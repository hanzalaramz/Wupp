use std::path::Path;
use std::sync::atomic::Ordering;
use std::sync::Arc;

use whatsapp_rust::bot::Bot;
use whatsapp_rust::features::{PresencePolicy, PresenceStatus};
use whatsapp_rust::pair_code::PairCodeOptions;
use whatsapp_rust::store::{SqliteDatabase, SqliteDatabaseConfig};
use whatsapp_rust::wacore::client_profile::ClientProfile;
use whatsapp_rust::wacore::store::DevicePropsOverride;
use whatsapp_rust::waproto::whatsapp::device_props::{AppVersion, PlatformType};

use crate::events::handle_sdk_event;
use crate::jid_helpers::canonical_jid_str;
use crate::meta_db::init_storage;
use crate::runtime::{now_secs, BridgeRuntime};

/// Opens `filesDir/wa_rust.db`, initializes bridge metadata tables, builds the WhatsApp Web
/// `Bot`, and spawns the background event/socket loop.
pub async fn init_client(runtime: Arc<BridgeRuntime>, files_dir: String) -> Result<(), String> {
    if runtime.client_arc().is_some() {
        return Ok(());
    }

    *runtime.files_dir.write().unwrap_or_else(|e| e.into_inner()) = files_dir.clone();

    let db_path = Path::new(&files_dir).join("wa_rust.db");
    let db_url = db_path.to_string_lossy().into_owned();
    let db = SqliteDatabase::open(&db_url, SqliteDatabaseConfig::default().with_read_pool_size(1))
        .await
        .map_err(|e| format!("failed to open SQLite store {db_url}: {e}"))?;

    let backend = db.store(1);
    let shared = backend.shared();

    init_storage(&files_dir, &shared).await?;
    *runtime.shared_db.write().unwrap_or_else(|e| e.into_inner()) = Some(shared);

    let rt_events = Arc::clone(&runtime);
    let mut device_os_ver = AppVersion::default();
    device_os_ver.primary = Some(10);
    device_os_ver.secondary = Some(0);
    device_os_ver.tertiary = Some(0);
    let bot = Bot::builder()
        .with_backend(backend)
        .with_presence_policy(PresencePolicy::Manual)
        .with_device_props(
            DevicePropsOverride::new()
                .with_os("Windows")
                .with_version(device_os_ver)
                .with_platform_type(PlatformType::CHROME),
        )
        .on_event(move |event, client| {
            let rt_clone = Arc::clone(&rt_events);
            async move {
                handle_sdk_event(rt_clone, event, client).await;
            }
        })
        .build()
        .await
        .map_err(|e| format!("failed to build whatsapp bot: {e}"))?;

    let client = bot.client();
    let mut profile = ClientProfile::web();
    profile.os_version = "10.0.0".to_string();
    client.set_client_profile(profile).await;
    let snap = client.persistence_manager().get_device_snapshot();
    runtime
        .logged_in
        .store(snap.pn.is_some() || snap.lid.is_some(), Ordering::SeqCst);

    *runtime.client.write().unwrap_or_else(|e| e.into_inner()) = Some(Arc::clone(&client));

    let handle = bot.spawn();

    let rt_watch = Arc::clone(&runtime);
    let join_handle = runtime.rt.spawn(async move {
        let reason = handle.await;
        rt_watch.connected.store(false, Ordering::SeqCst);
        rt_watch.emit_log(&format!("Bot runner exited: {reason:?}"));
    });

    *runtime.bot_abort.write().unwrap_or_else(|e| e.into_inner()) = Some(join_handle.abort_handle());
    runtime.emit_json(
        "ENGINE_READY",
        serde_json::json!({
            "ready": true,
        }),
    );
    runtime.emit_log("Rust whatsapp-rust client initialized");
    Ok(())
}

pub async fn connect_client(runtime: &BridgeRuntime) -> Result<(), String> {
    if runtime.terminal_stop.load(Ordering::Acquire) {
        return Err("session halted by server verdict — re-link or update client".into());
    }
    let until = runtime.rate_limit_until_unix.load(Ordering::Acquire);
    if until > 0 && now_secs() < until {
        return Err("rate limited — waiting out server backoff".into());
    }
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not initialized".to_string())?;
    client.resume();
    if !client.is_socket_connected() {
        client.reconnect_immediately().await;
    }
    Ok(())
}

pub async fn disconnect_client(runtime: &BridgeRuntime) {
    if let Some(client) = runtime.client_arc() {
        client.pause().await;
    }
    runtime.connected.store(false, Ordering::SeqCst);
}

pub async fn reset_socket(runtime: &BridgeRuntime) {
    if runtime.terminal_stop.load(Ordering::Acquire) {
        return;
    }
    let until = runtime.rate_limit_until_unix.load(Ordering::Acquire);
    if until > 0 && now_secs() < until {
        return;
    }
    if let Some(client) = runtime.client_arc() {
        if client.is_socket_connected() {
            return;
        }
        client.resume();
        client.reconnect_immediately().await;
    }
}

pub async fn set_ghost_presence(runtime: &BridgeRuntime, enabled: bool) {
    runtime.ghost_presence.store(enabled, Ordering::SeqCst);
    if let Some(client) = runtime.client_arc() {
        let status = if enabled {
            PresenceStatus::Unavailable
        } else {
            PresenceStatus::Available
        };
        let _ = client.presence().set(status).await;
    }
}

pub fn is_logged_in(runtime: &BridgeRuntime) -> bool {
    if let Some(client) = runtime.client_arc() {
        let snap = client.persistence_manager().get_device_snapshot();
        snap.pn.is_some() || snap.lid.is_some()
    } else {
        false
    }
}

pub fn is_connected(runtime: &BridgeRuntime) -> bool {
    runtime
        .client_arc()
        .is_some_and(|c| c.is_socket_connected() || runtime.connected.load(Ordering::Relaxed))
}

pub fn get_own_jid(runtime: &BridgeRuntime) -> String {
    let Some(client) = runtime.client_arc() else {
        return String::new();
    };
    let snap = client.persistence_manager().get_device_snapshot();
    snap.pn
        .as_ref()
        .map(|j| canonical_jid_str(&j.to_string()))
        .or_else(|| snap.lid.as_ref().map(|j| canonical_jid_str(&j.to_string())))
        .unwrap_or_default()
}

pub async fn pair_with_phone_number(runtime: &BridgeRuntime, phone: &str) -> Result<String, String> {
    runtime.terminal_stop.store(false, Ordering::SeqCst);
    runtime.rate_limit_until_unix.store(0, Ordering::SeqCst);
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not initialized".to_string())?;
    let digits: String = phone.chars().filter(|c| c.is_ascii_digit()).collect();
    if digits.len() < 7 {
        return Err("invalid phone number".to_string());
    }
    let opts = PairCodeOptions {
        phone_number: digits,
        show_push_notification: true,
        custom_code: None,
        ..Default::default()
    };
    client
        .pair_with_code(opts)
        .await
        .map_err(|e| format!("pair_with_code failed: {e}"))
}
