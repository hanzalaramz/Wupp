use std::fs;
use std::path::{Path, PathBuf};

use diesel::prelude::*;
use diesel::sql_types::{BigInt, Binary, Integer, Text};
use whatsapp_rust::wacore::download::{Downloadable, MediaType};
use whatsapp_rust::wacore::store::error::StoreError;
use whatsapp_rust_sqlite_storage::SharedSqlite;

use crate::jid_helpers::{media_subdir_and_ext, safe_file_component};
use crate::runtime::now_secs;

#[derive(QueryableByName, Clone)]
pub struct MediaMetaRow {
    #[diesel(sql_type = Text)]
    pub message_id: String,
    #[diesel(sql_type = Text)]
    pub chat_jid: String,
    #[diesel(sql_type = Text)]
    pub sender_jid: String,
    #[diesel(sql_type = Text)]
    pub msg_type: String,
    #[diesel(sql_type = Text)]
    pub direct_path: String,
    #[diesel(sql_type = Binary)]
    pub media_key: Vec<u8>,
    #[diesel(sql_type = Binary)]
    pub file_enc_sha256: Vec<u8>,
    #[diesel(sql_type = Binary)]
    pub file_sha256: Vec<u8>,
    #[diesel(sql_type = BigInt)]
    pub file_length: i64,
    #[diesel(sql_type = Text)]
    pub mime_type: String,
    #[diesel(sql_type = Text)]
    pub file_name: String,
    #[diesel(sql_type = Integer)]
    pub is_from_me: i32,
}

#[derive(QueryableByName, Clone)]
pub struct ContactMetaRow {
    #[diesel(sql_type = Text)]
    pub jid: String,
    #[diesel(sql_type = Text)]
    pub lid: String,
    #[diesel(sql_type = Text)]
    pub full_name: String,
    #[diesel(sql_type = Text)]
    pub push_name: String,
    #[diesel(sql_type = Text)]
    pub business_name: String,
}

#[derive(QueryableByName, Clone)]
pub struct AvatarMissRow {
    #[diesel(sql_type = Text)]
    pub status: String,
    #[diesel(sql_type = BigInt)]
    pub checked_at: i64,
}

impl MediaMetaRow {
    pub fn media_type(&self) -> MediaType {
        match self.msg_type.to_ascii_lowercase().as_str() {
            "image" => MediaType::Image,
            "video" => MediaType::Video,
            "gif" => MediaType::Gif,
            "ptv" => MediaType::Video,
            "audio" => MediaType::Audio,
            "ptt" => MediaType::Ptt,
            "sticker" => MediaType::Sticker,
            "document" => MediaType::Document,
            _ => MediaType::Document,
        }
    }
}

impl Downloadable for MediaMetaRow {
    fn direct_path(&self) -> Option<&str> {
        (!self.direct_path.is_empty()).then_some(self.direct_path.as_str())
    }

    fn media_key(&self) -> Option<&[u8]> {
        (!self.media_key.is_empty()).then_some(self.media_key.as_slice())
    }

    fn file_enc_sha256(&self) -> Option<&[u8]> {
        (!self.file_enc_sha256.is_empty()).then_some(self.file_enc_sha256.as_slice())
    }

    fn file_sha256(&self) -> Option<&[u8]> {
        (!self.file_sha256.is_empty()).then_some(self.file_sha256.as_slice())
    }

    fn file_length(&self) -> Option<u64> {
        u64::try_from(self.file_length).ok()
    }

    fn app_info(&self) -> MediaType {
        self.media_type()
    }
}

/// Ensures local storage directories and bridge metadata tables exist inside `SharedSqlite`.
pub async fn init_storage(files_dir: &str, shared: &SharedSqlite) -> Result<(), String> {
    let root = Path::new(files_dir);
    for sub in [
        "media/images",
        "media/videos",
        "media/audio",
        "media/stickers",
        "media/documents",
        "media/avatars",
        "media/files",
        "vault/images",
        "vault/videos",
        "vault/audio",
        "vault/stickers",
        "vault/documents",
    ] {
        fs::create_dir_all(root.join(sub))
            .map_err(|e| format!("failed to create {sub} dir: {e}"))?;
    }

    shared
        .run(|conn| {
            diesel::sql_query(
                "CREATE TABLE IF NOT EXISTS wa_media_meta (
                    message_id TEXT PRIMARY KEY,
                    chat_jid TEXT NOT NULL,
                    sender_jid TEXT NOT NULL,
                    msg_type TEXT NOT NULL,
                    direct_path TEXT NOT NULL,
                    media_key BLOB NOT NULL,
                    file_enc_sha256 BLOB NOT NULL,
                    file_sha256 BLOB NOT NULL,
                    file_length INTEGER NOT NULL,
                    mime_type TEXT NOT NULL,
                    file_name TEXT NOT NULL,
                    is_from_me INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                )",
            )
            .execute(conn)
            .map_err(|e| StoreError::Database(Box::new(e)))?;

            diesel::sql_query(
                "CREATE TABLE IF NOT EXISTS wa_contacts (
                    jid TEXT PRIMARY KEY,
                    lid TEXT NOT NULL DEFAULT '',
                    full_name TEXT NOT NULL DEFAULT '',
                    push_name TEXT NOT NULL DEFAULT '',
                    business_name TEXT NOT NULL DEFAULT '',
                    updated_at INTEGER NOT NULL
                )",
            )
            .execute(conn)
            .map_err(|e| StoreError::Database(Box::new(e)))?;

            diesel::sql_query(
                "CREATE TABLE IF NOT EXISTS wa_avatar_miss (
                    jid TEXT PRIMARY KEY,
                    status TEXT NOT NULL,
                    checked_at INTEGER NOT NULL
                )",
            )
            .execute(conn)
            .map_err(|e| StoreError::Database(Box::new(e)))?;

            Ok(())
        })
        .await
        .map_err(|e| format!("failed to initialize bridge metadata tables: {e}"))
}

pub async fn upsert_media_meta(shared: &SharedSqlite, row: MediaMetaRow) -> Result<(), String> {
    let ts = now_secs();
    shared
        .run(move |conn| {
            diesel::sql_query(
                "INSERT INTO wa_media_meta (
                    message_id, chat_jid, sender_jid, msg_type, direct_path,
                    media_key, file_enc_sha256, file_sha256, file_length,
                    mime_type, file_name, is_from_me, updated_at
                ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13)
                ON CONFLICT(message_id) DO UPDATE SET
                    chat_jid = CASE WHEN excluded.chat_jid != '' THEN excluded.chat_jid ELSE wa_media_meta.chat_jid END,
                    sender_jid = CASE WHEN excluded.sender_jid != '' THEN excluded.sender_jid ELSE wa_media_meta.sender_jid END,
                    msg_type = excluded.msg_type,
                    direct_path = CASE WHEN excluded.direct_path != '' THEN excluded.direct_path ELSE wa_media_meta.direct_path END,
                    media_key = CASE WHEN length(excluded.media_key) > 0 THEN excluded.media_key ELSE wa_media_meta.media_key END,
                    file_enc_sha256 = CASE WHEN length(excluded.file_enc_sha256) > 0 THEN excluded.file_enc_sha256 ELSE wa_media_meta.file_enc_sha256 END,
                    file_sha256 = CASE WHEN length(excluded.file_sha256) > 0 THEN excluded.file_sha256 ELSE wa_media_meta.file_sha256 END,
                    file_length = CASE WHEN excluded.file_length > 0 THEN excluded.file_length ELSE wa_media_meta.file_length END,
                    mime_type = CASE WHEN excluded.mime_type != '' THEN excluded.mime_type ELSE wa_media_meta.mime_type END,
                    file_name = CASE WHEN excluded.file_name != '' THEN excluded.file_name ELSE wa_media_meta.file_name END,
                    is_from_me = excluded.is_from_me,
                    updated_at = excluded.updated_at",
            )
            .bind::<Text, _>(&row.message_id)
            .bind::<Text, _>(&row.chat_jid)
            .bind::<Text, _>(&row.sender_jid)
            .bind::<Text, _>(&row.msg_type)
            .bind::<Text, _>(&row.direct_path)
            .bind::<Binary, _>(&row.media_key)
            .bind::<Binary, _>(&row.file_enc_sha256)
            .bind::<Binary, _>(&row.file_sha256)
            .bind::<BigInt, _>(row.file_length)
            .bind::<Text, _>(&row.mime_type)
            .bind::<Text, _>(&row.file_name)
            .bind::<Integer, _>(row.is_from_me)
            .bind::<BigInt, _>(ts)
            .execute(conn)
            .map_err(|e| StoreError::Database(Box::new(e)))?;
            Ok(())
        })
        .await
        .map_err(|e| e.to_string())
}

pub async fn get_media_meta(shared: &SharedSqlite, message_id: &str) -> Option<MediaMetaRow> {
    let id = message_id.to_string();
    shared
        .read(move |conn| {
            diesel::sql_query(
                "SELECT message_id, chat_jid, sender_jid, msg_type, direct_path,
                        media_key, file_enc_sha256, file_sha256, file_length,
                        mime_type, file_name, is_from_me
                 FROM wa_media_meta WHERE message_id = ?1 LIMIT 1",
            )
            .bind::<Text, _>(id)
            .get_result::<MediaMetaRow>(conn)
            .optional()
            .map_err(|e| StoreError::Database(Box::new(e)))
        })
        .await
        .ok()
        .flatten()
}

pub async fn has_media_meta(shared: &SharedSqlite, message_id: &str) -> bool {
    get_media_meta(shared, message_id).await.is_some()
}

pub async fn upsert_contact_meta(
    shared: &SharedSqlite,
    jid: &str,
    lid: &str,
    full_name: &str,
    push_name: &str,
    business_name: &str,
) -> Result<(), String> {
    if jid.is_empty() {
        return Ok(());
    }
    let jid_s = jid.to_string();
    let lid_s = lid.to_string();
    let full_s = full_name.to_string();
    let push_s = push_name.to_string();
    let biz_s = business_name.to_string();
    let ts = now_secs();
    shared
        .run(move |conn| {
            diesel::sql_query(
                "INSERT INTO wa_contacts (jid, lid, full_name, push_name, business_name, updated_at)
                 VALUES (?1, ?2, ?3, ?4, ?5, ?6)
                 ON CONFLICT(jid) DO UPDATE SET
                    lid = CASE WHEN excluded.lid != '' THEN excluded.lid ELSE wa_contacts.lid END,
                    full_name = CASE WHEN excluded.full_name != '' THEN excluded.full_name ELSE wa_contacts.full_name END,
                    push_name = CASE WHEN excluded.push_name != '' THEN excluded.push_name ELSE wa_contacts.push_name END,
                    business_name = CASE WHEN excluded.business_name != '' THEN excluded.business_name ELSE wa_contacts.business_name END,
                    updated_at = excluded.updated_at",
            )
            .bind::<Text, _>(jid_s)
            .bind::<Text, _>(lid_s)
            .bind::<Text, _>(full_s)
            .bind::<Text, _>(push_s)
            .bind::<Text, _>(biz_s)
            .bind::<BigInt, _>(ts)
            .execute(conn)
            .map_err(|e| StoreError::Database(Box::new(e)))?;
            Ok(())
        })
        .await
        .map_err(|e| e.to_string())
}

pub async fn get_contact_meta(shared: &SharedSqlite, jid_or_lid: &str) -> Option<ContactMetaRow> {
    let key = jid_or_lid.to_string();
    shared
        .read(move |conn| {
            diesel::sql_query(
                "SELECT jid, lid, full_name, push_name, business_name
                 FROM wa_contacts WHERE jid = ?1 OR lid = ?1 LIMIT 1",
            )
            .bind::<Text, _>(key)
            .get_result::<ContactMetaRow>(conn)
            .optional()
            .map_err(|e| StoreError::Database(Box::new(e)))
        })
        .await
        .ok()
        .flatten()
}

pub async fn should_skip_avatar_fetch(shared: &SharedSqlite, jid: &str) -> bool {
    let jid_s = jid.to_string();
    let row = shared
        .read(move |conn| {
            diesel::sql_query("SELECT status, checked_at FROM wa_avatar_miss WHERE jid = ?1 LIMIT 1")
                .bind::<Text, _>(jid_s)
                .get_result::<AvatarMissRow>(conn)
                .optional()
                .map_err(|e| StoreError::Database(Box::new(e)))
        })
        .await
        .ok()
        .flatten();

    let Some(miss) = row else {
        return false;
    };
    let age = now_secs().saturating_sub(miss.checked_at);
    match miss.status.as_str() {
        "none" | "hidden" => age < 24 * 3600,
        _ => age < 3600,
    }
}

pub async fn mark_avatar_miss(shared: &SharedSqlite, jid: &str, status: &str) {
    let jid_s = jid.to_string();
    let status_s = status.to_string();
    let ts = now_secs();
    let _ = shared
        .run(move |conn| {
            diesel::sql_query(
                "INSERT INTO wa_avatar_miss (jid, status, checked_at) VALUES (?1, ?2, ?3)
                 ON CONFLICT(jid) DO UPDATE SET status = excluded.status, checked_at = excluded.checked_at",
            )
            .bind::<Text, _>(jid_s)
            .bind::<Text, _>(status_s)
            .bind::<BigInt, _>(ts)
            .execute(conn)
            .map_err(|e| StoreError::Database(Box::new(e)))?;
            Ok(())
        })
        .await;
}

pub async fn clear_avatar_miss(shared: &SharedSqlite, jid: &str) {
    let jid_s = jid.to_string();
    let _ = shared
        .run(move |conn| {
            diesel::sql_query("DELETE FROM wa_avatar_miss WHERE jid = ?1")
                .bind::<Text, _>(jid_s)
                .execute(conn)
                .map_err(|e| StoreError::Database(Box::new(e)))?;
            Ok(())
        })
        .await;
}

/// Computes and ensures the destination media path inside `files_dir`, writing atomically.
pub fn save_media_bytes(
    files_dir: &str,
    message_id: &str,
    msg_type: &str,
    mime_type: &str,
    file_name: &str,
    bytes: &[u8],
) -> Result<String, String> {
    let (subdir, default_ext) = media_subdir_and_ext(msg_type, mime_type);
    let dir = Path::new(files_dir).join("media").join(subdir);
    fs::create_dir_all(&dir).map_err(|e| format!("mkdir failed: {e}"))?;

    let target = if msg_type.eq_ignore_ascii_case("document") && !file_name.trim().is_empty() {
        let base = Path::new(file_name)
            .file_name()
            .and_then(|s| s.to_str())
            .unwrap_or("file");
        let ext = Path::new(base)
            .extension()
            .and_then(|e| e.to_str())
            .map(|e| format!(".{}", safe_file_component(e)))
            .unwrap_or_else(|| default_ext.to_string());
        dir.join(format!("{}{}", safe_file_component(message_id), ext))
    } else {
        dir.join(format!("{}{}", safe_file_component(message_id), default_ext))
    };

    write_atomic(&target, bytes)?;
    Ok(target.to_string_lossy().into_owned())
}

pub fn avatar_file_path(files_dir: &str, jid: &str) -> PathBuf {
    let hash = format!("{:x}", md5_like_or_safe(jid));
    Path::new(files_dir)
        .join("media")
        .join("avatars")
        .join(format!("{hash}_{}.jpg", safe_file_component(jid)))
}

fn md5_like_or_safe(input: &str) -> u64 {
    // Stable FNV-1a 64-bit hash keeps avatar filenames compact across restarts.
    let mut hash: u64 = 0xcbf29ce484222325;
    for b in input.as_bytes() {
        hash ^= u64::from(*b);
        hash = hash.wrapping_mul(0x100000001b3);
    }
    hash
}

pub fn write_atomic(target: &Path, bytes: &[u8]) -> Result<(), String> {
    if let Some(parent) = target.parent() {
        fs::create_dir_all(parent).map_err(|e| format!("mkdir failed: {e}"))?;
    }
    let tmp = target.with_extension("tmp");
    fs::write(&tmp, bytes).map_err(|e| format!("write temp file failed: {e}"))?;
    fs::rename(&tmp, target).map_err(|e| format!("rename media file failed: {e}"))?;
    Ok(())
}
