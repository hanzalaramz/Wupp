use std::collections::{HashMap, HashSet};
use std::sync::atomic::{AtomicBool, AtomicI64, Ordering};
use std::sync::{Arc, Mutex, OnceLock, RwLock};
use std::time::{SystemTime, UNIX_EPOCH};

use jni::objects::{GlobalRef, JObject, JValue};
use jni::JavaVM;
use tokio::runtime::Runtime;
use tokio::sync::{mpsc, Semaphore};
use whatsapp_rust::client::Client;
use whatsapp_rust_sqlite_storage::SharedSqlite;

use crate::voip::VoipRegistry;

static BRIDGE: OnceLock<Arc<BridgeRuntime>> = OnceLock::new();

/// Process-wide singleton managing Tokio tasks, SQLite shared handles, VoIP calls, and JNI callbacks.
pub struct BridgeRuntime {
    pub rt: Runtime,
    jvm: JavaVM,
    callback: RwLock<Option<GlobalRef>>,
    event_tx: mpsc::Sender<(String, String)>,

    pub files_dir: RwLock<String>,
    pub client: RwLock<Option<Arc<Client>>>,
    pub shared_db: RwLock<Option<SharedSqlite>>,
    pub bot_abort: RwLock<Option<tokio::task::AbortHandle>>,

    pub shutting_down: AtomicBool,
    pub connected: AtomicBool,
    pub logged_in: AtomicBool,
    pub terminal_stop: AtomicBool,
    pub rate_limit_until_unix: AtomicI64,

    pub ghost_presence: AtomicBool,
    pub allow_photo: AtomicBool,
    pub allow_audio: AtomicBool,
    pub allow_video: AtomicBool,
    pub allow_doc: AtomicBool,

    pub auto_dl_sem: Arc<Semaphore>,
    pub download_inflight: Mutex<HashSet<String>>,
    pub chat_ephemeral: RwLock<HashMap<String, u32>>,
    pub contact_sync_active: AtomicBool,
    pub last_sync_unix: AtomicI64,
    pub last_verify_unix: AtomicI64,

    pub voip: Arc<VoipRegistry>,
}

impl BridgeRuntime {
    /// Initializes or returns the singleton bridge runtime and updates the JNI callback target.
    pub fn init_global(jvm: JavaVM, callback: GlobalRef) -> Result<Arc<Self>, String> {
        if let Some(existing) = BRIDGE.get() {
            *existing
                .callback
                .write()
                .unwrap_or_else(|e| e.into_inner()) = Some(callback);
            existing.shutting_down.store(false, Ordering::SeqCst);
            return Ok(Arc::clone(existing));
        }

        let rt = tokio::runtime::Builder::new_multi_thread()
            .worker_threads(4)
            .thread_name("wupp-wa-rs")
            .enable_all()
            .build()
            .map_err(|e| format!("failed to build tokio runtime: {e}"))?;

        let (event_tx, event_rx) = mpsc::channel::<(String, String)>(2048);

        let runtime = Arc::new(Self {
            rt,
            jvm,
            callback: RwLock::new(Some(callback)),
            event_tx,
            files_dir: RwLock::new(String::new()),
            client: RwLock::new(None),
            shared_db: RwLock::new(None),
            bot_abort: RwLock::new(None),
            shutting_down: AtomicBool::new(false),
            connected: AtomicBool::new(false),
            logged_in: AtomicBool::new(false),
            terminal_stop: AtomicBool::new(false),
            rate_limit_until_unix: AtomicI64::new(0),
            ghost_presence: AtomicBool::new(false),
            allow_photo: AtomicBool::new(true),
            allow_audio: AtomicBool::new(true),
            allow_video: AtomicBool::new(false),
            allow_doc: AtomicBool::new(false),
            auto_dl_sem: Arc::new(Semaphore::new(4)),
            download_inflight: Mutex::new(HashSet::new()),
            chat_ephemeral: RwLock::new(HashMap::new()),
            contact_sync_active: AtomicBool::new(false),
            last_sync_unix: AtomicI64::new(0),
            last_verify_unix: AtomicI64::new(0),
            voip: Arc::new(VoipRegistry::new()),
        });

        // Dedicated JVM-attached event pump avoids per-event attach/detach overhead.
        let pump_ref = Arc::clone(&runtime);
        std::thread::Builder::new()
            .name("wupp-jni-events".to_string())
            .spawn(move || pump_events(pump_ref, event_rx))
            .map_err(|e| format!("failed to spawn JNI event pump: {e}"))?;

        let _ = BRIDGE.set(Arc::clone(&runtime));
        Ok(Arc::clone(BRIDGE.get().expect("BRIDGE initialized")))
    }

    /// Returns the initialized global runtime if `initClient` has already run.
    pub fn get() -> Option<Arc<Self>> {
        BRIDGE.get().cloned()
    }

    pub fn client_arc(&self) -> Option<Arc<Client>> {
        self.client
            .read()
            .unwrap_or_else(|e| e.into_inner())
            .clone()
    }

    pub fn shared_db_arc(&self) -> Option<SharedSqlite> {
        self.shared_db
            .read()
            .unwrap_or_else(|e| e.into_inner())
            .clone()
    }

    pub fn files_dir_string(&self) -> String {
        self.files_dir
            .read()
            .unwrap_or_else(|e| e.into_inner())
            .clone()
    }

    pub fn is_auto_download_enabled(&self, msg_type: &str) -> bool {
        match msg_type.to_ascii_lowercase().as_str() {
            "image" | "sticker" => self.allow_photo.load(Ordering::Relaxed),
            "audio" | "ptt" => self.allow_audio.load(Ordering::Relaxed),
            "video" | "gif" | "ptv" => self.allow_video.load(Ordering::Relaxed),
            "document" => self.allow_doc.load(Ordering::Relaxed),
            _ => false,
        }
    }

    pub fn set_chat_ephemeral(&self, chat_jid: &str, secs: u32) {
        if chat_jid.is_empty() {
            return;
        }
        let mut guard = self.chat_ephemeral.write().unwrap_or_else(|e| e.into_inner());
        if secs == 0 {
            guard.remove(chat_jid);
        } else {
            guard.insert(chat_jid.to_string(), secs);
        }
    }

    pub fn chat_ephemeral_secs(&self, chat_jid: &str) -> u32 {
        self.chat_ephemeral
            .read()
            .unwrap_or_else(|e| e.into_inner())
            .get(chat_jid)
            .copied()
            .unwrap_or(0)
    }

    /// Enqueues a JSON event onto the JVM callback thread without blocking async tasks.
    pub fn emit_json(&self, event_type: &str, payload: serde_json::Value) {
        if let Ok(json_str) = serde_json::to_string(&payload) {
            if let Err(err) = self.event_tx.try_send((event_type.to_string(), json_str)) {
                log::warn!("Dropping event {event_type} (queue full/closed): {err}");
            }
        }
    }

    /// Sends a pre-serialized JSON string to the Kotlin service callback.
    #[allow(dead_code)]
    pub fn emit_raw_json(&self, event_type: &str, json_str: String) {
        if let Err(err) = self.event_tx.try_send((event_type.to_string(), json_str)) {
            log::warn!("Dropping event {event_type} (queue full/closed): {err}");
        }
    }

    /// Emits a bridge diagnostic log line both to logcat and the JVM UI console.
    pub fn emit_log(&self, message: impl AsRef<str>) {
        let msg = message.as_ref();
        log::info!("[WuppRust] {msg}");
        self.emit_json(
            "BRIDGE_LOG",
            serde_json::json!({
                "level": "INFO",
                "module": "Rust",
                "msg": msg,
            }),
        );
    }

    pub fn try_begin_download(&self, message_id: &str) -> bool {
        if message_id.is_empty() {
            return false;
        }
        let mut guard = self
            .download_inflight
            .lock()
            .unwrap_or_else(|e| e.into_inner());
        guard.insert(message_id.to_string())
    }

    pub fn finish_download(&self, message_id: &str) {
        let mut guard = self
            .download_inflight
            .lock()
            .unwrap_or_else(|e| e.into_inner());
        guard.remove(message_id);
    }
}

pub fn now_secs() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

pub fn now_millis() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

fn pump_events(runtime: Arc<BridgeRuntime>, mut rx: mpsc::Receiver<(String, String)>) {
    let mut env = match runtime.jvm.attach_current_thread_permanently() {
        Ok(env) => env,
        Err(e) => {
            log::error!("Failed to permanently attach JNI event pump thread: {e}");
            return;
        }
    };

    while let Some((event_type, json_payload)) = rx.blocking_recv() {
        let cb_opt = runtime
            .callback
            .read()
            .unwrap_or_else(|e| e.into_inner())
            .clone();
        let Some(cb_ref) = cb_opt else {
            continue;
        };

        let res: jni::errors::Result<()> = env.with_local_frame(16, |env| {
            let j_type = env.new_string(&event_type)?;
            let j_payload = env.new_string(&json_payload)?;
            let obj: &JObject = cb_ref.as_obj();
            env.call_method(
                obj,
                "onJsonEvent",
                "(Ljava/lang/String;Ljava/lang/String;)V",
                &[JValue::Object(&j_type), JValue::Object(&j_payload)],
            )?;
            Ok(())
        });

        if let Err(err) = res {
            let _ = env.exception_clear();
            log::error!("JNI callback error on {event_type}: {err}");
        }
    }
}
