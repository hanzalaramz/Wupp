use std::fs;
use std::sync::atomic::Ordering;
use std::sync::Arc;
use std::time::Duration;

use serde_json::{json, Value};
use whatsapp_rust::features::{
    ProfilePictureLookup, ProfilePictureRequest, ProfilePictureTarget, ProfilePictureType,
};
use whatsapp_rust::http::{HttpClient, HttpRequest, UreqHttpClient};
use whatsapp_rust::proto_helpers::MessageExt;
use whatsapp_rust::wacore::iq::privacy::{PrivacyCategory, PrivacyValue};
use whatsapp_rust::wacore_binary::{Jid, JidExt};

use crate::jid_helpers::{canonical_jid_str, parse_jid_flexible, phone_digits_from_jid, split_csv};
use crate::meta_db::{
    avatar_file_path, clear_avatar_miss, get_contact_meta, list_contact_lid_pn_pairs, mark_avatar_miss,
    mark_newsletter_synced, should_skip_avatar_fetch, should_sync_newsletter, upsert_contact_meta,
};
use crate::messaging::normalize_message;
use crate::runtime::{jitter_ms, looks_like_rate_limit_error, now_millis, now_secs, BridgeRuntime};

/// Normalizes a relative WhatsApp CDN path (such as `/v/t61...`) into an HTTPS URL.
pub fn normalize_whatsapp_cdn_url(raw: &str, default_host: &str) -> String {
    let trimmed = raw.trim();
    if trimmed.is_empty() {
        return String::new();
    }
    if trimmed.starts_with("https://") || trimmed.starts_with("http://") {
        return trimmed.to_string();
    }
    if trimmed.starts_with('/') {
        format!("https://{default_host}{trimmed}")
    } else {
        format!("https://{default_host}/{trimmed}")
    }
}

/// Downloads public/signed WhatsApp CDN bytes (avatars, channel icons, unencrypted channel media)
/// using WhatsApp Web headers and host fallbacks when given a relative CDN path.
pub async fn download_cdn_bytes(http: &UreqHttpClient, url_or_path: &str) -> Result<Vec<u8>, String> {
    let trimmed = url_or_path.trim();
    if trimmed.is_empty() {
        return Err("empty CDN URL".to_string());
    }
    let candidates = if trimmed.starts_with("https://") || trimmed.starts_with("http://") {
        vec![trimmed.to_string()]
    } else {
        vec![
            normalize_whatsapp_cdn_url(trimmed, "pps.whatsapp.net"),
            normalize_whatsapp_cdn_url(trimmed, "mmg.whatsapp.net"),
        ]
    };

    let mut last_err = String::from("CDN download failed");
    for url in candidates {
        let req = HttpRequest::get(&url)
            .with_header(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            )
            .with_header(
                "Accept",
                "image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
            )
            .with_header("Origin", "https://web.whatsapp.com")
            .with_header("Referer", "https://web.whatsapp.com/");
        match http.execute(req).await {
            Ok(resp) if resp.status_code < 400 && !resp.body.is_empty() => {
                return Ok(resp.body);
            }
            Ok(resp) => {
                last_err = format!("CDN HTTP status {}", resp.status_code);
            }
            Err(e) => {
                last_err = format!("CDN HTTP error: {e}");
            }
        }
    }
    Err(last_err)
}

enum PicLookupOutcome {
    Found { url: String, id: String },
    Miss(&'static str),
    Unchanged,
    RateLimited(String),
    Err(String),
}

async fn lookup_picture_with_type(
    client: &whatsapp_rust::client::Client,
    target: ProfilePictureTarget<'_>,
    full_res: bool,
) -> PicLookupOutcome {
    let pic_types: &[ProfilePictureType] = if full_res {
        &[ProfilePictureType::Full, ProfilePictureType::Preview]
    } else {
        &[ProfilePictureType::Preview]
    };
    for &pic_type in pic_types {
        let req = ProfilePictureRequest::new(target, pic_type);
        match client.pictures().lookup(req).await {
            Ok(ProfilePictureLookup::Found(p)) if !p.url.is_empty() => {
                return PicLookupOutcome::Found {
                    url: p.url,
                    id: p.id,
                };
            }
            Ok(ProfilePictureLookup::Found(_)) => {
                if pic_type == ProfilePictureType::Preview {
                    return PicLookupOutcome::Miss("none");
                }
            }
            Ok(ProfilePictureLookup::NotFound) => return PicLookupOutcome::Miss("none"),
            Ok(ProfilePictureLookup::NotAuthorized) => return PicLookupOutcome::Miss("hidden"),
            Ok(ProfilePictureLookup::Unchanged) => return PicLookupOutcome::Unchanged,
            Ok(_) => {}
            Err(e) => {
                let msg = format!("picture lookup failed: {e}");
                if looks_like_rate_limit_error(&msg) {
                    return PicLookupOutcome::RateLimited(msg);
                }
                return PicLookupOutcome::Err(msg);
            }
        }
    }
    PicLookupOutcome::Miss("none")
}

pub async fn fetch_and_cache_avatar_if_needed(
    runtime: &BridgeRuntime,
    jid: &Jid,
    full_res: bool,
) -> Result<String, String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let files_dir = runtime.files_dir_string();
    if files_dir.is_empty() {
        return Err("filesDir not set".to_string());
    }

    let jid_canon = canonical_jid_str(&jid.to_string());
    let shared = runtime.shared_db_arc();

    let mut alt_canon = String::new();
    if jid.is_pn() || jid.is_lid() {
        if let Ok(Some(entry)) = client.get_lid_pn_entry(jid).await {
            if jid.is_lid() && !entry.phone_number.is_empty() {
                alt_canon = format!("{}@s.whatsapp.net", entry.phone_number);
            } else if jid.is_pn() && !entry.lid.is_empty() {
                alt_canon = format!("{}@lid", entry.lid);
            }
        }
        if alt_canon.is_empty() {
            if let Some(ref sdb) = shared {
                if let Some(meta) = get_contact_meta(sdb, &jid_canon).await {
                    if meta.jid == jid_canon && !meta.lid.is_empty() {
                        alt_canon = meta.lid;
                    } else if meta.lid == jid_canon && !meta.jid.is_empty() {
                        alt_canon = meta.jid;
                    }
                }
            }
        }
    }

    let out_path = avatar_file_path(&files_dir, &jid_canon);
    let cached_path = if out_path.exists() && fs::metadata(&out_path).map(|m| m.len() > 0).unwrap_or(false) {
        Some(out_path.clone())
    } else if !alt_canon.is_empty() {
        let alt_path = avatar_file_path(&files_dir, &alt_canon);
        (alt_path.exists() && fs::metadata(&alt_path).map(|m| m.len() > 0).unwrap_or(false))
            .then_some(alt_path)
    } else {
        None
    };

    if !full_res {
        if let Some(existing) = cached_path {
            let local_path = existing.to_string_lossy().into_owned();
            runtime.emit_json(
                "PROFILE_UPDATE",
                json!({
                    "jid": jid_canon,
                    "altJid": alt_canon,
                    "avatarPath": local_path,
                    "aboutText": "",
                    "pictureId": "",
                    "removed": false,
                    "timestamp": now_millis(),
                }),
            );
            return Ok(local_path);
        }
        if let Some(ref sdb) = shared {
            if should_skip_avatar_fetch(sdb, &jid_canon).await {
                return Ok(String::new());
            }
        }
    }

    if !full_res && runtime.is_rate_limited() {
        return Ok(String::new());
    }

    let http = UreqHttpClient::new();
    if jid.is_newsletter() {
        let meta = match client.newsletter().get_metadata(jid).await {
            Ok(m) => m,
            Err(e) => {
                let msg = format!("newsletter metadata failed: {e}");
                if looks_like_rate_limit_error(&msg) {
                    runtime.trip_rate_limit(60);
                }
                return Err(msg);
            }
        };
        let urls: Vec<&str> = if full_res {
            [meta.picture_url.as_deref(), meta.preview_url.as_deref()]
                .into_iter()
                .flatten()
                .filter(|s| !s.trim().is_empty())
                .collect()
        } else {
            [meta.preview_url.as_deref(), meta.picture_url.as_deref()]
                .into_iter()
                .flatten()
                .filter(|s| !s.trim().is_empty())
                .collect()
        };
        if urls.is_empty() {
            if let Some(ref sdb) = shared {
                mark_avatar_miss(sdb, &jid_canon, "none").await;
            }
            return Ok(String::new());
        }
        for url in urls {
            if let Ok(bytes) = download_cdn_bytes(&http, url).await {
                if let Some(parent) = out_path.parent() {
                    fs::create_dir_all(parent).map_err(|e| format!("mkdir avatar failed: {e}"))?;
                }
                fs::write(&out_path, &bytes).map_err(|e| format!("write avatar failed: {e}"))?;
                if let Some(ref sdb) = shared {
                    clear_avatar_miss(sdb, &jid_canon).await;
                }
                let local_path = out_path.to_string_lossy().into_owned();
                runtime.emit_json(
                    "PROFILE_UPDATE",
                    json!({
                        "jid": jid_canon,
                        "altJid": "",
                        "avatarPath": local_path,
                        "aboutText": "",
                        "pictureId": "",
                        "removed": false,
                        "timestamp": now_millis(),
                    }),
                );
                return Ok(local_path);
            }
        }
        return Err("channel avatar CDN download failed".to_string());
    }

    let primary_target = if jid.is_group() {
        ProfilePictureTarget::Group(jid)
    } else {
        ProfilePictureTarget::Contact(jid)
    };
    let mut resolved: Option<(String, String)> = None;
    let mut miss_reason: Option<&'static str> = None;

    match lookup_picture_with_type(&client, primary_target, full_res).await {
        PicLookupOutcome::Found { url, id } => resolved = Some((url, id)),
        PicLookupOutcome::Miss(r) => miss_reason = Some(r),
        PicLookupOutcome::Unchanged => return Ok(String::new()),
        PicLookupOutcome::RateLimited(msg) => {
            runtime.trip_rate_limit(60);
            return Err(msg);
        }
        PicLookupOutcome::Err(msg) => {
            log::debug!("Avatar lookup transient error for {jid_canon}: {msg}");
        }
    }

    if resolved.is_none() && jid.is_group() && miss_reason == Some("none") {
        match lookup_picture_with_type(&client, ProfilePictureTarget::Community(jid), full_res).await {
            PicLookupOutcome::Found { url, id } => resolved = Some((url, id)),
            PicLookupOutcome::Miss(r) => miss_reason = miss_reason.or(Some(r)),
            PicLookupOutcome::Unchanged => return Ok(String::new()),
            PicLookupOutcome::RateLimited(msg) => {
                runtime.trip_rate_limit(60);
                return Err(msg);
            }
            PicLookupOutcome::Err(msg) => {
                log::debug!("Community avatar lookup transient error for {jid_canon}: {msg}");
            }
        }
    }

    if resolved.is_none() && jid.is_lid() && alt_canon.ends_with("@s.whatsapp.net") {
        if let Ok(alt_jid) = parse_jid_flexible(&alt_canon) {
            match lookup_picture_with_type(&client, ProfilePictureTarget::Contact(&alt_jid), full_res).await {
                PicLookupOutcome::Found { url, id } => resolved = Some((url, id)),
                PicLookupOutcome::Miss(r) => miss_reason = Some(r),
                PicLookupOutcome::Unchanged => return Ok(String::new()),
                PicLookupOutcome::RateLimited(msg) => {
                    runtime.trip_rate_limit(60);
                    return Err(msg);
                }
                PicLookupOutcome::Err(msg) => {
                    log::debug!("Alt PN avatar lookup transient error for {jid_canon}: {msg}");
                }
            }
        }
    }

    let Some((pic_url, pic_id)) = resolved else {
        let should_mark = !jid.is_lid() || !alt_canon.is_empty();
        if should_mark {
            if let (Some(sdb), Some(reason)) = (shared.as_ref(), miss_reason) {
                mark_avatar_miss(sdb, &jid_canon, reason).await;
            }
        }
        return Ok(String::new());
    };

    let bytes = download_cdn_bytes(&http, &pic_url).await?;
    if let Some(parent) = out_path.parent() {
        fs::create_dir_all(parent).map_err(|e| format!("mkdir avatar failed: {e}"))?;
    }
    fs::write(&out_path, &bytes).map_err(|e| format!("write avatar failed: {e}"))?;
    if let Some(ref sdb) = shared {
        clear_avatar_miss(sdb, &jid_canon).await;
        if !alt_canon.is_empty() {
            clear_avatar_miss(sdb, &alt_canon).await;
        }
    }

    let local_path = out_path.to_string_lossy().into_owned();
    runtime.emit_json(
        "PROFILE_UPDATE",
        json!({
            "jid": jid_canon,
            "altJid": alt_canon,
            "avatarPath": local_path,
            "aboutText": "",
            "pictureId": pic_id,
            "removed": false,
            "timestamp": now_millis(),
        }),
    );
    Ok(local_path)
}

pub async fn sync_contacts_and_avatars(runtime: Arc<BridgeRuntime>, filter_csv: &str) {
    if runtime.is_rate_limited() {
        return;
    }
    let trimmed = filter_csv.trim();
    let raw_csv = trimmed.strip_prefix("avatars:").unwrap_or(trimmed).trim();
    let is_explicit = !raw_csv.is_empty();
    if trimmed.starts_with("avatars:") && !is_explicit {
        return;
    }
    let now = now_secs();
    if !is_explicit {
        let last = runtime.last_sync_unix.load(Ordering::Relaxed);
        // Global 10-minute cooldown prevents usync + avatar IQ storms on every socket reconnect.
        if last > 0 && (now - last) < 600 {
            return;
        }
    }

    let mut waited_ms = 0u64;
    while runtime
        .contact_sync_active
        .compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst)
        .is_err()
    {
        if !is_explicit || waited_ms >= 12_000 {
            return;
        }
        tokio::time::sleep(Duration::from_millis(500)).await;
        waited_ms += 500;
    }

    if let Err(e) = sync_inner(&runtime, filter_csv).await {
        runtime.emit_log(format!("Contact/group sync warning: {e}"));
    }
    if !is_explicit {
        runtime.last_sync_unix.store(now_secs(), Ordering::SeqCst);
    }
    runtime.contact_sync_active.store(false, Ordering::SeqCst);
}

async fn sync_inner(runtime: &Arc<BridgeRuntime>, filter_csv: &str) -> Result<(), String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let shared = runtime.shared_db_arc();

    let trimmed = filter_csv.trim();
    let (avatars_only, raw_csv) = if let Some(rest) = trimmed.strip_prefix("avatars:") {
        (true, rest)
    } else {
        (false, trimmed)
    };
    let explicit_jids = split_csv(raw_csv);
    if avatars_only && explicit_jids.is_empty() {
        return Ok(());
    }
    let mut avatar_targets: Vec<Jid> = Vec::new();

    if explicit_jids.is_empty() {
        if let Ok(groups) = client.groups().list_participating().await {
            let mut group_batch = Vec::with_capacity(groups.len());
            for g in groups {
                let g_jid = canonical_jid_str(&g.id.to_string());
                let name = g.subject.clone().unwrap_or_default();
                group_batch.push(json!({
                    "jid": g_jid,
                    "name": name,
                    "topic": "",
                    "isCommunityParent": g.is_parent_group(),
                    "parentGroupJid": g.parent_group_jid().map(|j| canonical_jid_str(&j.to_string())).unwrap_or_default(),
                    "isDefaultSubgroup": g.is_default_sub_group(),
                    "onlyAdminsPost": false,
                }));
                avatar_targets.push(g.id);
            }
            if !group_batch.is_empty() {
                runtime.emit_json("GROUPS_SYNC_BATCH", json!({ "groups": group_batch }));
            }
        }

        if let Ok(newsletters) = client.newsletter().list_subscribed().await {
            let files_dir = runtime.files_dir_string();
            let mut ch_batch = Vec::with_capacity(newsletters.len());
            let mut nl_tasks: Vec<(String, Option<String>, Option<String>)> = Vec::with_capacity(newsletters.len());
            for nl in newsletters {
                let nl_jid = canonical_jid_str(&nl.jid.to_string());
                let mut local_avatar = String::new();
                if !files_dir.is_empty() {
                    let out_path = avatar_file_path(&files_dir, &nl_jid);
                    if out_path.exists() && fs::metadata(&out_path).map(|m| m.len() > 0).unwrap_or(false) {
                        local_avatar = out_path.to_string_lossy().into_owned();
                    }
                }
                ch_batch.push(json!({
                    "jid": nl_jid,
                    "name": nl.name,
                    "description": nl.description.unwrap_or_default(),
                    "isMuted": nl.muted.unwrap_or(false),
                    "avatarPath": local_avatar,
                }));
                nl_tasks.push((nl_jid, nl.preview_url, nl.picture_url));
            }
            if !ch_batch.is_empty() {
                runtime.emit_json("NEWSLETTERS_SYNC_BATCH", json!({ "channels": ch_batch }));
            }
            if !nl_tasks.is_empty() {
                let rt_nl = Arc::clone(runtime);
                runtime.rt.spawn(async move {
                    let http = UreqHttpClient::new();
                    let dir = rt_nl.files_dir_string();
                    let mut synced_count = 0usize;
                    for (nl_jid, prev_url, pic_url) in nl_tasks {
                        if !dir.is_empty() {
                            let out_path = avatar_file_path(&dir, &nl_jid);
                            let already_cached = out_path.exists()
                                && fs::metadata(&out_path).map(|m| m.len() > 0).unwrap_or(false);
                            if !already_cached {
                                let urls: Vec<String> = [prev_url, pic_url]
                                    .into_iter()
                                    .flatten()
                                    .filter(|u| !u.trim().is_empty())
                                    .collect();
                                for u in urls {
                                    if let Ok(bytes) = download_cdn_bytes(&http, &u).await {
                                        if let Some(parent) = out_path.parent() {
                                            let _ = fs::create_dir_all(parent);
                                        }
                                        if fs::write(&out_path, &bytes).is_ok() {
                                            let local = out_path.to_string_lossy().into_owned();
                                            rt_nl.emit_json(
                                                "PROFILE_UPDATE",
                                                json!({
                                                    "jid": nl_jid,
                                                    "altJid": "",
                                                    "avatarPath": local,
                                                    "aboutText": "",
                                                    "pictureId": "",
                                                    "removed": false,
                                                    "timestamp": now_millis(),
                                                }),
                                            );
                                        }
                                        break;
                                    }
                                }
                                tokio::time::sleep(jitter_ms(150, 300)).await;
                            }
                        }
                        if rt_nl.is_rate_limited() {
                            break;
                        }
                        let should_sync = match rt_nl.shared_db_arc() {
                            Some(ref sdb) => should_sync_newsletter(sdb, &nl_jid, 15 * 60).await,
                            None => true,
                        };
                        if !should_sync {
                            continue;
                        }
                        if synced_count > 0 {
                            tokio::time::sleep(jitter_ms(500, 900)).await;
                        }
                        if rt_nl.is_rate_limited() {
                            break;
                        }
                        fetch_newsletter_messages_inner(Arc::clone(&rt_nl), &nl_jid, 15, true, 3).await;
                        synced_count += 1;
                    }
                });
            }
        }
    } else {
        for raw in &explicit_jids {
            if let Ok(jid) = parse_jid_flexible(raw) {
                avatar_targets.push(jid);
            }
        }
    }

    if !avatars_only {
        let contact_jids: Vec<Jid> = avatar_targets
            .iter()
            .filter(|j| j.is_pn() || j.is_lid())
            .cloned()
            .collect();
        if !contact_jids.is_empty() {
            let mut contact_batch = Vec::new();
            for (idx, chunk) in contact_jids.chunks(50).enumerate() {
                if runtime.is_rate_limited() {
                    break;
                }
                if idx > 0 {
                    tokio::time::sleep(jitter_ms(250, 450)).await;
                }
                match client.contacts().get_user_info(chunk).await {
                    Ok(info_map) => {
                        for (jid, ui) in info_map {
                            let jid_s = canonical_jid_str(&jid.to_string());
                            let mut alt_s = ui
                                .lid
                                .as_ref()
                                .map(|l| canonical_jid_str(&l.to_string()))
                                .filter(|s| !s.is_empty() && s != &jid_s)
                                .or_else(|| {
                                    ui.pn_jid
                                        .as_ref()
                                        .map(|p| canonical_jid_str(&p.to_string()))
                                        .filter(|s| !s.is_empty() && s != &jid_s)
                                })
                                .unwrap_or_default();
                            let biz = ui
                                .verified_name
                                .as_ref()
                                .and_then(|v| v.name.clone())
                                .unwrap_or_default();
                            let mut saved_full_name = String::new();
                            if let Some(ref sdb) = shared {
                                if let Some(existing) = get_contact_meta(sdb, &jid_s).await {
                                    if !existing.full_name.is_empty() {
                                        saved_full_name = existing.full_name;
                                    }
                                    if alt_s.is_empty() {
                                        if existing.jid == jid_s && !existing.lid.is_empty() {
                                            alt_s = existing.lid;
                                        } else if existing.lid == jid_s && !existing.jid.is_empty() {
                                            alt_s = existing.jid;
                                        }
                                    }
                                }
                                let (row_jid, row_lid) = if jid_s.ends_with("@lid") {
                                    (
                                        if alt_s.ends_with("@s.whatsapp.net") { alt_s.as_str() } else { jid_s.as_str() },
                                        jid_s.as_str(),
                                    )
                                } else {
                                    (
                                        jid_s.as_str(),
                                        if alt_s.ends_with("@lid") { alt_s.as_str() } else { "" },
                                    )
                                };
                                let _ = upsert_contact_meta(sdb, row_jid, row_lid, "", "", &biz).await;
                            }
                            let emit_name = if !saved_full_name.is_empty() {
                                &saved_full_name
                            } else {
                                &biz
                            };
                            let is_custom = !saved_full_name.is_empty();
                            if !emit_name.is_empty() || !alt_s.is_empty() {
                                contact_batch.push(json!({
                                    "jid": jid_s,
                                    "altJid": alt_s,
                                    "name": emit_name,
                                    "isCustom": is_custom,
                                }));
                            }
                            if let Some(about) = ui.status.filter(|s| !s.is_empty()) {
                                runtime.emit_json(
                                    "PROFILE_UPDATE",
                                    json!({
                                        "jid": jid_s,
                                        "altJid": alt_s,
                                        "avatarPath": "",
                                        "aboutText": about,
                                        "removed": false,
                                        "timestamp": now_millis(),
                                    }),
                                );
                            }
                        }
                    }
                    Err(e) => {
                        if looks_like_rate_limit_error(&e.to_string()) {
                            runtime.trip_rate_limit(60);
                            break;
                        }
                    }
                }
            }
            if !contact_batch.is_empty() {
                runtime.emit_json("CONTACTS_SYNC_BATCH", json!({ "contacts": contact_batch }));
            }
        }
    }

    let files_dir = runtime.files_dir_string();
    let mut fetched_uncached = 0usize;
    for target in avatar_targets {
        if runtime.is_rate_limited() {
            break;
        }
        if target.is_newsletter() {
            if !explicit_jids.is_empty() {
                let target_canon = canonical_jid_str(&target.to_string());
                let cached = !files_dir.is_empty() && {
                    let out_path = avatar_file_path(&files_dir, &target_canon);
                    out_path.exists() && fs::metadata(&out_path).map(|m| m.len() > 0).unwrap_or(false)
                };
                if !cached {
                    if let Some(ref sdb) = shared {
                        if should_skip_avatar_fetch(sdb, &target_canon).await {
                            continue;
                        }
                    }
                    if fetched_uncached >= 35 {
                        break;
                    }
                    if fetched_uncached > 0 {
                        tokio::time::sleep(jitter_ms(350, 650)).await;
                    }
                    fetched_uncached += 1;
                }
                let _ = fetch_and_cache_avatar_if_needed(runtime, &target, false).await;
            }
            continue;
        }
        let target_canon = canonical_jid_str(&target.to_string());
        if !files_dir.is_empty() {
            let out_path = avatar_file_path(&files_dir, &target_canon);
            if out_path.exists() && fs::metadata(&out_path).map(|m| m.len() > 0).unwrap_or(false) {
                if !explicit_jids.is_empty() {
                    let _ = fetch_and_cache_avatar_if_needed(runtime, &target, false).await;
                }
                continue;
            }
        }
        if let Some(ref sdb) = shared {
            if should_skip_avatar_fetch(sdb, &target_canon).await {
                continue;
            }
        }
        if fetched_uncached >= 35 {
            break;
        }
        if fetched_uncached > 0 {
            tokio::time::sleep(jitter_ms(350, 650)).await;
        }
        let _ = fetch_and_cache_avatar_if_needed(runtime, &target, false).await;
        fetched_uncached += 1;
    }
    Ok(())
}

pub async fn resolve_participant_names_json(runtime: &BridgeRuntime, jids_csv: &str) -> String {
    let inputs = split_csv(jids_csv);
    if inputs.is_empty() {
        return "{}".to_string();
    }
    let shared = runtime.shared_db_arc();
    let client = runtime.client_arc();
    let files_dir = runtime.files_dir_string();
    let (own_pn, own_lid) = client
        .as_ref()
        .map(|c| {
            let snap = c.persistence_manager().get_device_snapshot();
            (
                snap.pn.as_ref().map(|j| canonical_jid_str(&j.to_string())).unwrap_or_default(),
                snap.lid.as_ref().map(|j| canonical_jid_str(&j.to_string())).unwrap_or_default(),
            )
        })
        .unwrap_or_default();
    let mut map = serde_json::Map::new();
    let mut unresolved: Vec<Jid> = Vec::new();

    for raw in inputs {
        let key = canonical_jid_str(&raw);
        let mut saved_name = String::new();
        let mut push_name = String::new();
        let mut alt_jid = String::new();
        let mut phone = String::new();

        let mut parsed_opt: Option<Jid> = None;
        if let Ok(parsed) = parse_jid_flexible(&key) {
            if parsed.is_pn() {
                phone = phone_digits_from_jid(&parsed);
            }
            if let Some(ref c) = client {
                if let Ok(Some(entry)) = c.get_lid_pn_entry(&parsed).await {
                    if parsed.is_lid() && !entry.phone_number.is_empty() {
                        phone = entry.phone_number.to_string();
                        alt_jid = format!("{}@s.whatsapp.net", entry.phone_number);
                    } else if parsed.is_pn() && !entry.lid.is_empty() {
                        alt_jid = format!("{}@lid", entry.lid);
                    }
                }
            }
            parsed_opt = Some(parsed);
        }

        if let Some(ref sdb) = shared {
            let meta_opt = match get_contact_meta(sdb, &key).await {
                Some(r) => Some(r),
                None if !alt_jid.is_empty() => get_contact_meta(sdb, &alt_jid).await,
                None => None,
            };
            if let Some(row) = meta_opt {
                if !row.full_name.is_empty() {
                    saved_name = row.full_name;
                }
                if !row.business_name.is_empty() {
                    push_name = row.business_name;
                } else if !row.push_name.is_empty() {
                    push_name = row.push_name;
                }
                if phone.is_empty() && row.jid.ends_with("@s.whatsapp.net") {
                    phone = row.jid.split('@').next().unwrap_or("").to_string();
                }
                if alt_jid.is_empty() {
                    if row.jid == key && !row.lid.is_empty() {
                        alt_jid = row.lid;
                    } else if row.lid == key && !row.jid.is_empty() {
                        alt_jid = row.jid;
                    }
                }
            }
        }

        let is_self = (!own_pn.is_empty() && (key == own_pn || alt_jid == own_pn))
            || (!own_lid.is_empty() && (key == own_lid || alt_jid == own_lid));
        if !is_self && saved_name.is_empty() && push_name.is_empty() && phone.is_empty() {
            if let Some(parsed) = parsed_opt {
                unresolved.push(parsed);
            }
        }
        let avatar_path = if !files_dir.is_empty() {
            let p1 = avatar_file_path(&files_dir, &key);
            if p1.exists() && fs::metadata(&p1).map(|m| m.len() > 0).unwrap_or(false) {
                p1.to_string_lossy().into_owned()
            } else if !alt_jid.is_empty() {
                let p2 = avatar_file_path(&files_dir, &alt_jid);
                if p2.exists() && fs::metadata(&p2).map(|m| m.len() > 0).unwrap_or(false) {
                    p2.to_string_lossy().into_owned()
                } else {
                    String::new()
                }
            } else {
                String::new()
            }
        } else {
            String::new()
        };
        let display = if !saved_name.is_empty() {
            saved_name.clone()
        } else {
            push_name.clone()
        };

        map.insert(
            key,
            json!({
                "name": display,
                "savedName": saved_name,
                "pushName": push_name,
                "altJid": alt_jid,
                "phone": phone,
                "avatarPath": avatar_path,
                "isSelf": is_self,
            }),
        );
    }

    if let (Some(c), false, false) = (client, unresolved.is_empty(), runtime.is_rate_limited()) {
        let batch: Vec<Jid> = unresolved.into_iter().take(40).collect();
        let mut discovered_mappings: Vec<Value> = Vec::new();
        if let Ok(infos) = c.contacts().get_user_info(&batch).await {
            for (jid, ui) in infos {
                let k = canonical_jid_str(&jid.to_string());
                let biz = ui.verified_name.as_ref().and_then(|v| v.name.clone()).unwrap_or_default();
                let alt_str = ui.pn_jid.as_ref().map(|p| canonical_jid_str(&p.to_string()))
                    .or_else(|| ui.lid.as_ref().map(|l| canonical_jid_str(&l.to_string())))
                    .unwrap_or_default();
                let lid_col = if k.ends_with("@lid") {
                    k.as_str()
                } else if alt_str.ends_with("@lid") {
                    alt_str.as_str()
                } else {
                    ""
                };
                let pn_col = if k.ends_with("@s.whatsapp.net") {
                    k.as_str()
                } else if alt_str.ends_with("@s.whatsapp.net") {
                    alt_str.as_str()
                } else {
                    k.as_str()
                };
                if pn_col.ends_with("@s.whatsapp.net") && lid_col.ends_with("@lid") {
                    discovered_mappings.push(json!({ "pn": pn_col, "lid": lid_col }));
                }
                if let Some(ref sdb) = shared {
                    if !biz.is_empty() || !alt_str.is_empty() {
                        let _ = upsert_contact_meta(sdb, pn_col, lid_col, "", "", &biz).await;
                    }
                }
                if let Some(Value::Object(obj)) = map.get_mut(&k) {
                    if let Some(ref pn) = ui.pn_jid {
                        obj.insert("altJid".into(), json!(canonical_jid_str(&pn.to_string())));
                        obj.insert("phone".into(), json!(phone_digits_from_jid(pn)));
                    } else if let Some(ref lid) = ui.lid {
                        obj.insert("altJid".into(), json!(canonical_jid_str(&lid.to_string())));
                    }
                    if !biz.is_empty() && obj.get("pushName").and_then(|v| v.as_str()).unwrap_or("").is_empty() {
                        obj.insert("pushName".into(), json!(biz.clone()));
                        if obj.get("name").and_then(|v| v.as_str()).unwrap_or("").is_empty() {
                            obj.insert("name".into(), json!(biz));
                        }
                    }
                }
            }
        }
        if !discovered_mappings.is_empty() {
            runtime.emit_json("LID_PN_MAPPINGS", json!({ "mappings": discovered_mappings }));
        }
    }

    Value::Object(map).to_string()
}

/// Resolves a DM JID into `(canonical_pn_or_original, alt_jid)` using strictly local SQLite state
/// (explicit hints -> `whatsapp-rust` `get_lid_pn_entry` -> `wa_contacts`) with zero network calls.
pub async fn resolve_dm_chat_pair_local(
    runtime: &BridgeRuntime,
    raw_chat: &str,
    alt_hints: &[&str],
) -> (String, String) {
    let clean_chat = canonical_jid_str(raw_chat);
    if clean_chat.is_empty()
        || clean_chat.ends_with("@g.us")
        || clean_chat.ends_with("@newsletter")
        || clean_chat.ends_with("@broadcast")
        || clean_chat == "status@broadcast"
    {
        return (clean_chat, String::new());
    }

    let is_lid = clean_chat.ends_with("@lid");
    let is_pn = clean_chat.ends_with("@s.whatsapp.net");
    if !is_lid && !is_pn {
        return (clean_chat, String::new());
    }

    let want_suffix = if is_lid { "@s.whatsapp.net" } else { "@lid" };
    let mut alt = alt_hints
        .iter()
        .map(|h| canonical_jid_str(h))
        .find(|h| !h.is_empty() && h.ends_with(want_suffix))
        .unwrap_or_default();

    if alt.is_empty() {
        if let Some(client) = runtime.client_arc() {
            if let Ok(parsed) = parse_jid_flexible(&clean_chat) {
                if let Ok(Some(entry)) = client.get_lid_pn_entry(&parsed).await {
                    if is_lid && !entry.phone_number.is_empty() {
                        alt = format!("{}@s.whatsapp.net", entry.phone_number);
                    } else if is_pn && !entry.lid.is_empty() {
                        alt = format!("{}@lid", entry.lid);
                    }
                }
            }
        }
    }

    if alt.is_empty() {
        if let Some(shared) = runtime.shared_db_arc() {
            if let Some(row) = get_contact_meta(&shared, &clean_chat).await {
                if is_lid && row.jid.ends_with("@s.whatsapp.net") {
                    alt = row.jid;
                } else if is_pn && row.lid.ends_with("@lid") {
                    alt = row.lid;
                }
            }
        }
    }

    if is_lid && alt.ends_with("@s.whatsapp.net") {
        if let Some(shared) = runtime.shared_db_arc() {
            let _ = upsert_contact_meta(&shared, &alt, &clean_chat, "", "", "").await;
        }
        return (alt, clean_chat);
    }

    if is_pn && alt.ends_with("@lid") {
        if let Some(shared) = runtime.shared_db_arc() {
            let _ = upsert_contact_meta(&shared, &clean_chat, &alt, "", "", "").await;
        }
    }

    (clean_chat, alt)
}

/// Returns local `{ "<pn>@s.whatsapp.net": "<id>@lid", ... }` pairs from cached SQLite tables for
/// given `jids_csv` (and all stored `wa_contacts` LID↔PN rows) with zero network requests.
pub async fn resolve_local_dm_aliases_json(runtime: &BridgeRuntime, jids_csv: &str) -> String {
    let mut pairs = serde_json::Map::new();
    if let Some(shared) = runtime.shared_db_arc() {
        for (pn, lid) in list_contact_lid_pn_pairs(&shared).await {
            pairs.insert(lid, json!(pn));
        }
    }
    for raw in split_csv(jids_csv) {
        let key = canonical_jid_str(&raw);
        if key.is_empty() || key.ends_with("@g.us") || key.ends_with("@newsletter") {
            continue;
        }
        let (canonical, alt) = resolve_dm_chat_pair_local(runtime, &key, &[]).await;
        if canonical.ends_with("@s.whatsapp.net") && alt.ends_with("@lid") {
            pairs.insert(alt, json!(canonical));
        }
    }
    Value::Object(pairs).to_string()
}

async fn persist_and_emit_group_participant_mappings(
    runtime: &BridgeRuntime,
    pairs: &[(String, String)],
) {
    let shared = runtime.shared_db_arc();
    let mut mappings: Vec<Value> = Vec::new();
    for (jid_s, pn_s) in pairs {
        let (pn, lid) = if jid_s.ends_with("@lid") && pn_s.ends_with("@s.whatsapp.net") {
            (pn_s.as_str(), jid_s.as_str())
        } else if jid_s.ends_with("@s.whatsapp.net") && pn_s.ends_with("@lid") {
            (jid_s.as_str(), pn_s.as_str())
        } else {
            continue;
        };
        if let Some(ref sdb) = shared {
            let _ = upsert_contact_meta(sdb, pn, lid, "", "", "").await;
        }
        mappings.push(json!({ "pn": pn, "lid": lid }));
    }
    if !mappings.is_empty() {
        runtime.emit_json("LID_PN_MAPPINGS", json!({ "mappings": mappings }));
    }
}

pub async fn get_group_participants_json(runtime: &BridgeRuntime, group_jid_str: &str) -> String {
    let Some(client) = runtime.client_arc() else {
        return "[]".to_string();
    };
    let Ok(gjid) = parse_jid_flexible(group_jid_str) else {
        return "[]".to_string();
    };
    let Ok(mut meta) = client.groups().fetch_metadata(&gjid).await else {
        return "[]".to_string();
    };
    client.groups().resolve_participant_addresses(&mut meta).await;
    let raw_pairs: Vec<(String, String)> = meta
        .participants
        .iter()
        .map(|p| {
            (
                canonical_jid_str(&p.jid.to_string()),
                p.phone_number
                    .as_ref()
                    .map(|j| canonical_jid_str(&j.to_string()))
                    .unwrap_or_default(),
            )
        })
        .collect();
    persist_and_emit_group_participant_mappings(runtime, &raw_pairs).await;

    // WaRealtimeService.getGroupParticipants reads a JSON array of plain participant JID strings.
    // Prefer canonical PN JID when resolved while persisting LID->PN mappings above.
    let arr: Vec<Value> = meta
        .participants
        .into_iter()
        .map(|p| {
            let pn = p
                .phone_number
                .as_ref()
                .map(|j| canonical_jid_str(&j.to_string()))
                .filter(|s| s.ends_with("@s.whatsapp.net"));
            json!(pn.unwrap_or_else(|| canonical_jid_str(&p.jid.to_string())))
        })
        .collect();
    Value::Array(arr).to_string()
}

pub async fn get_group_info_json(runtime: &BridgeRuntime, group_jid_str: &str) -> Result<String, String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let gjid = parse_jid_flexible(group_jid_str)?;
    let mut meta = client
        .groups()
        .fetch_metadata(&gjid)
        .await
        .map_err(|e| format!("fetch group metadata failed: {e}"))?;
    client.groups().resolve_participant_addresses(&mut meta).await;
    let raw_pairs: Vec<(String, String)> = meta
        .participants
        .iter()
        .map(|p| {
            (
                canonical_jid_str(&p.jid.to_string()),
                p.phone_number
                    .as_ref()
                    .map(|j| canonical_jid_str(&j.to_string()))
                    .unwrap_or_default(),
            )
        })
        .collect();
    persist_and_emit_group_participant_mappings(runtime, &raw_pairs).await;

    let ephem_secs = meta.ephemeral.as_ref().and_then(|e| e.expiration).unwrap_or(0);
    let invite_link = client
        .groups()
        .get_invite_link(&gjid, false)
        .await
        .ok()
        .unwrap_or_default();

    let participants: Vec<Value> = meta
        .participants
        .iter()
        .map(|p| {
            json!({
                "jid": canonical_jid_str(&p.jid.to_string()),
                "phoneJid": p.phone_number.as_ref().map(|j| canonical_jid_str(&j.to_string())).unwrap_or_default(),
                "isAdmin": p.is_admin(),
                "isSuperAdmin": p.is_super_admin(),
            })
        })
        .collect();

    Ok(json!({
        "jid": canonical_jid_str(&meta.id.to_string()),
        "subject": meta.subject.unwrap_or_default(),
        "description": meta.description.unwrap_or_default(),
        "isLocked": meta.is_locked,
        "isAnnounce": meta.is_announcement,
        "isParent": meta.is_parent_group,
        "parentJid": meta.parent_group_jid.map(|j| canonical_jid_str(&j.to_string())).unwrap_or_default(),
        "participantCount": meta.participant_count.unwrap_or(participants.len() as u32),
        "ephemeralSeconds": ephem_secs,
        "inviteLink": invite_link,
        "participants": participants,
    })
    .to_string())
}

pub async fn fetch_group_lock_state(runtime: &BridgeRuntime, group_jid_str: &str) -> Result<bool, String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let gjid = parse_jid_flexible(group_jid_str)?;
    let meta = client
        .groups()
        .fetch_metadata(&gjid)
        .await
        .map_err(|e| format!("fetch group metadata failed: {e}"))?;
    let locked = meta.is_announcement;
    runtime.emit_json(
        "CHAT_META_UPDATE",
        json!({
            "kind": "GROUP_ANNOUNCE",
            "jid": canonical_jid_str(&meta.id.to_string()),
            "locked": locked,
        }),
    );
    Ok(locked)
}

pub async fn fetch_newsletter_messages(runtime: Arc<BridgeRuntime>, nl_jid_str: &str) {
    fetch_newsletter_messages_inner(runtime, nl_jid_str, 40, false, 6).await;
}

pub async fn fetch_newsletter_messages_inner(
    runtime: Arc<BridgeRuntime>,
    nl_jid_str: &str,
    count: u32,
    is_sync: bool,
    max_auto_dl: usize,
) {
    if is_sync && runtime.is_rate_limited() {
        return;
    }
    let Some(client) = runtime.client_arc() else {
        return;
    };
    let Ok(njid) = parse_jid_flexible(nl_jid_str) else {
        return;
    };
    let msgs = match client.newsletter().get_messages(&njid, count, None).await {
        Ok(m) => m,
        Err(e) => {
            if looks_like_rate_limit_error(&e.to_string()) {
                runtime.trip_rate_limit(60);
            }
            return;
        }
    };
    if let Some(ref sdb) = runtime.shared_db_arc() {
        mark_newsletter_synced(sdb, &canonical_jid_str(nl_jid_str)).await;
    }
    let nl_user = njid.user.clone();
    let mut auto_dl_used = 0usize;

    for nm in msgs {
        let Some(body) = nm.message else { continue };
        let msg_id_str = nm
            .message_id
            .map(|m| m.into_string())
            .filter(|s| !s.is_empty())
            .unwrap_or_else(|| format!("nl_{nl_user}_{}", nm.server_id));
        if auto_dl_used < max_auto_dl && runtime.is_auto_download_enabled("IMAGE") {
            let base = body.get_base_message();
            let has_media = base.image_message.as_option().is_some()
                || base.sticker_message.as_option().is_some();
            if has_media {
                runtime.mark_wanted_media(&msg_id_str);
                auto_dl_used += 1;
            }
        }
        let mut info = whatsapp_rust::types::message::MessageInfo::default();
        info.id = msg_id_str.into();
        info.newsletter_server_id = Some(nm.server_id);
        info.source.chat = njid.clone();
        info.source.sender = njid.clone();
        info.source.is_from_me = nm.is_sender;
        if let Some(ts) = whatsapp_rust::wacore::time::Timestamp::from_secs(nm.timestamp as i64) {
            info.timestamp = ts;
        }
        let mut parts: Vec<String> = nm
            .reactions
            .iter()
            .filter(|r| !r.code.is_empty() && r.count > 0)
            .take(4)
            .map(|r| format!("{} {}", r.code, r.count))
            .collect();
        if let Some(fwd) = nm.forwards_count.filter(|&c| c > 0) {
            parts.push(format!("{fwd} forwards"));
        }
        let reaction_summary = parts.join("  ·  ");

        if let crate::messaging::NormalizedItem::Message(mut payload) =
            normalize_message(&runtime, &info, &body, true).await
        {
            if !reaction_summary.is_empty() {
                payload["reactionSummary"] = json!(reaction_summary);
            }
            payload["isSync"] = json!(is_sync);
            runtime.emit_json("MESSAGE_UPSERT", payload);
        }
    }
}

pub async fn toggle_newsletter_mute(
    runtime: &BridgeRuntime,
    nl_jid_str: &str,
    muted: bool,
) -> Result<(), String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let njid = parse_jid_flexible(nl_jid_str)?;
    client
        .newsletter()
        .set_follower_mute(&njid, muted)
        .await
        .map_err(|e| format!("toggle newsletter mute failed: {e}"))
}

pub async fn verify_numbers_on_whatsapp(
    runtime: &BridgeRuntime,
    phones_csv: &str,
) -> Result<String, String> {
    let now = now_secs();
    let prev = runtime.last_verify_unix.load(Ordering::Acquire);
    if prev > 0 && (now - prev) < 30 {
        return Err("Please wait 30s between number lookup batches".to_string());
    }
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let inputs = split_csv(phones_csv);
    let mut jids = Vec::with_capacity(inputs.len().min(25));
    for p in inputs {
        if let Ok(jid) = parse_jid_flexible(&p) {
            if !jids.contains(&jid) {
                jids.push(jid);
                if jids.len() >= 25 {
                    break;
                }
            }
        }
    }
    if jids.is_empty() {
        return Ok("[]".to_string());
    }
    runtime.last_verify_unix.store(now, Ordering::SeqCst);
    let res = client
        .contacts()
        .is_on_whatsapp(&jids)
        .await
        .map_err(|e| format!("verify numbers failed: {e}"))?;

    let arr: Vec<Value> = res
        .into_iter()
        .map(|item| {
            let jid_s = canonical_jid_str(&item.jid.to_string());
            let lid_s = item
                .lid
                .as_ref()
                .map(|l| canonical_jid_str(&l.to_string()))
                .unwrap_or_default();
            let biz = item
                .verified_name
                .as_ref()
                .and_then(|v| v.name.clone())
                .unwrap_or_default();
            json!({
                "query": item.jid.user,
                "jid": jid_s,
                "lid": lid_s,
                "isIn": item.is_registered,
                "businessName": biz,
            })
        })
        .collect();
    Ok(Value::Array(arr).to_string())
}

pub async fn get_own_profile_json(runtime: &BridgeRuntime) -> Result<String, String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let snap = client.persistence_manager().get_device_snapshot();
    let own_pn = snap.pn.clone();
    let own_jid = own_pn
        .as_ref()
        .map(|j| canonical_jid_str(&j.to_string()))
        .unwrap_or_default();
    let phone = own_pn
        .as_ref()
        .map(|j| format!("+{}", phone_digits_from_jid(j)))
        .unwrap_or_default();
    let mut about = String::new();
    let mut avatar_path = String::new();

    if let Some(ref pn) = own_pn {
        if let Ok(infos) = client.contacts().get_user_info(std::slice::from_ref(pn)).await {
            if let Some(ui) = infos.values().next() {
                about = ui.status.clone().unwrap_or_default();
            }
        }
        avatar_path = fetch_and_cache_avatar_if_needed(runtime, pn, true)
            .await
            .unwrap_or_default();
    }

    Ok(json!({
        "ownJid": own_jid,
        "phone": phone,
        "pushName": snap.push_name,
        "about": about,
        "avatarPath": avatar_path,
    })
    .to_string())
}

pub async fn set_own_push_name(runtime: &BridgeRuntime, name: &str) -> Result<(), String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let _ = client
        .profile()
        .set_push_name(name)
        .await
        .map_err(|e| format!("set_push_name failed: {e}"))?;
    Ok(())
}

pub async fn set_own_about(runtime: &BridgeRuntime, about: &str) -> Result<(), String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let trimmed = about.trim();
    if trimmed.is_empty() {
        client
            .profile()
            .clear_text_status()
            .await
            .map_err(|e| format!("clear_text_status failed: {e}"))
    } else {
        client
            .profile()
            .set_status_text(trimmed)
            .await
            .map_err(|e| format!("set_status_text failed: {e}"))
    }
}

pub async fn get_account_privacy_settings_json(runtime: &BridgeRuntime) -> Result<String, String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let resp = client
        .fetch_privacy_settings()
        .await
        .map_err(|e| format!("fetch privacy failed: {e}"))?;

    let mut out = serde_json::Map::new();
    for s in resp.settings {
        let camel_key = match s.category.as_str() {
            "last" => "lastSeen",
            "groupadd" => "groupAdd",
            "readreceipts" => "readReceipts",
            "calladd" => "callAdd",
            other => other,
        };
        out.insert(camel_key.to_string(), json!(s.value.as_str()));
    }
    Ok(Value::Object(out).to_string())
}

pub async fn set_account_privacy_setting(
    runtime: &BridgeRuntime,
    category_str: &str,
    value_str: &str,
) -> Result<String, String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let wire_cat = match category_str {
        "lastSeen" => "last",
        "groupAdd" => "groupadd",
        "readReceipts" => "readreceipts",
        "callAdd" => "calladd",
        other => other,
    };
    let cat = PrivacyCategory::from(wire_cat);
    let val = PrivacyValue::from(value_str);
    client
        .set_privacy_setting(cat, val)
        .await
        .map_err(|e| format!("set_privacy_setting failed: {e}"))?;
    get_account_privacy_settings_json(runtime).await
}

pub async fn set_default_disappearing_timer_sec(
    runtime: &BridgeRuntime,
    seconds: i64,
) -> Result<(), String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    client
        .set_default_disappearing_mode(seconds.max(0) as u32)
        .await
        .map_err(|e| format!("set default disappearing failed: {e}"))
}

pub async fn get_blocklist_json(runtime: &BridgeRuntime) -> Result<String, String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let entries = client
        .blocking()
        .get_blocklist()
        .await
        .map_err(|e| format!("get_blocklist failed: {e}"))?;
    let shared = runtime.shared_db_arc();
    let mut arr = Vec::with_capacity(entries.len());
    for entry in entries {
        let jid_s = canonical_jid_str(&entry.jid.to_string());
        let mut name = String::new();
        let mut phone = phone_digits_from_jid(&entry.jid);
        if let Some(ref sdb) = shared {
            if let Some(row) = get_contact_meta(sdb, &jid_s).await {
                name = if !row.full_name.is_empty() {
                    row.full_name
                } else {
                    row.push_name
                };
                if phone.is_empty() && row.jid.ends_with("@s.whatsapp.net") {
                    phone = row.jid.split('@').next().unwrap_or("").to_string();
                }
            }
        }
        arr.push(json!({
            "jid": jid_s,
            "name": name,
            "phone": phone,
            "timestamp": entry.timestamp.unwrap_or(0),
        }));
    }
    Ok(Value::Array(arr).to_string())
}

pub async fn set_contact_blocked(
    runtime: &BridgeRuntime,
    jid_str: &str,
    blocked: bool,
) -> Result<(), String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let target = parse_jid_flexible(jid_str)?;
    if blocked {
        client
            .blocking()
            .block(&target)
            .await
            .map_err(|e| format!("block failed: {e}"))
    } else {
        client
            .blocking()
            .unblock(&target)
            .await
            .map_err(|e| format!("unblock failed: {e}"))
    }
}
