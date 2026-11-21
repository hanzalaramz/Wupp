use std::path::Path;
use std::sync::Arc;

use serde_json::{json, Value};
use whatsapp_rust::download::DownloadLimits;
use whatsapp_rust::features::{ChatActivity, MediaReuploadRequest, MediaRetryResult};
use whatsapp_rust::proto_helpers::MessageExt;
use whatsapp_rust::send::{EditRequest, PinDuration, RevokeType, SendOptions, SendRequest};
use whatsapp_rust::types::message::MessageInfo;
use whatsapp_rust::upload::UploadOptions;
use whatsapp_rust::wacore::download::MediaType;
use whatsapp_rust::wacore::proto_helpers::build_reaction_message;
use whatsapp_rust::wacore::types::message_ref::{MessageId, MessageRef};
use whatsapp_rust::wacore_binary::JidExt;
use whatsapp_rust::waproto::whatsapp as wa;

use crate::jid_helpers::{
    canonical_jid_str, media_subdir_and_ext, parse_jid_flexible, safe_file_component, split_csv,
};
use crate::meta_db::{
    get_contact_meta, get_media_meta, save_media_bytes, upsert_contact_meta, upsert_media_meta,
    MediaMetaRow,
};
use crate::runtime::{now_millis, BridgeRuntime};

pub enum NormalizedItem {
    Message(Value),
    Reaction(Value),
    Pin(Value),
    Revoke(Value),
    None,
}

/// Normalizes an inbound or history message into Wupp's exact JSON shape, caching contact and media metadata.
pub async fn normalize_message(
    runtime: &Arc<BridgeRuntime>,
    info: &MessageInfo,
    raw_msg: &wa::Message,
    from_history: bool,
) -> NormalizedItem {
    let base = raw_msg.get_base_message();
    let chat_jid = canonical_jid_str(&info.source.chat.to_string());
    let raw_sender = canonical_jid_str(&info.source.sender.to_string());
    let sender_jid = if (info.source.is_group || info.source.chat.is_group())
        && (raw_sender == chat_jid || info.source.sender.is_group())
    {
        String::new()
    } else {
        raw_sender
    };
    let alt_chat_jid = info
        .source
        .recipient_alt
        .as_ref()
        .map(|j| canonical_jid_str(&j.to_string()))
        .unwrap_or_default();
    let alt_sender_jid = info
        .source
        .sender_alt
        .as_ref()
        .map(|j| canonical_jid_str(&j.to_string()))
        .unwrap_or_default();

    let ts_ms = info.timestamp.as_millis();
    let msg_id = info.id.to_string();
    let is_status = info.is_status_stanza || info.source.chat.is_status_broadcast();

    let mut effective_sender_name = if info.push_name.chars().any(|c| c.is_alphanumeric()) {
        info.push_name.trim().to_string()
    } else {
        String::new()
    };

    if let Some(shared) = runtime.shared_db_arc() {
        if !sender_jid.is_empty() && !effective_sender_name.is_empty() {
            let lid = if info.source.sender.is_lid() {
                sender_jid.as_str()
            } else if info.source.sender_alt.as_ref().is_some_and(|j| j.is_lid()) {
                alt_sender_jid.as_str()
            } else {
                ""
            };
            let pn = if info.source.sender.is_pn() {
                sender_jid.as_str()
            } else if info.source.sender_alt.as_ref().is_some_and(|j| j.is_pn()) {
                alt_sender_jid.as_str()
            } else {
                ""
            };
            if !pn.is_empty() || !lid.is_empty() {
                let key = if !pn.is_empty() { pn } else { lid };
                let _ = upsert_contact_meta(&shared, key, lid, "", &effective_sender_name, "").await;
            }
        } else if !info.source.is_from_me && !sender_jid.is_empty() && effective_sender_name.is_empty() {
            let row_opt = match get_contact_meta(&shared, &sender_jid).await {
                Some(r) => Some(r),
                None if !alt_sender_jid.is_empty() => get_contact_meta(&shared, &alt_sender_jid).await,
                None => None,
            };
            if let Some(row) = row_opt {
                effective_sender_name = if !row.full_name.is_empty() {
                    row.full_name
                } else if !row.business_name.is_empty() {
                    row.business_name
                } else {
                    row.push_name
                };
            }
        }
    }

    if let Some(proto_msg) = base.protocol_message.as_option() {
        let target_id = proto_msg
            .key
            .as_option()
            .and_then(|k| k.id.clone())
            .unwrap_or_default();

        if let Some(edited) = proto_msg.edited_message.as_option() {
            let new_body = extract_text_or_caption(edited);
            if !from_history {
                runtime.emit_json(
                    "MESSAGE_EDIT",
                    json!({
                        "chatJid": chat_jid,
                        "altChatJid": alt_chat_jid,
                        "targetMessageId": target_id,
                        "newBody": new_body,
                        "editedAt": ts_ms,
                    }),
                );
            }
            return NormalizedItem::None;
        }

        if proto_msg.r#type == Some(wa::message::protocol_message::Type::REVOKE.into())
            && !target_id.is_empty()
        {
            let rev = json!({
                "chatJid": chat_jid,
                "altChatJid": alt_chat_jid,
                "targetMessageId": target_id,
                "senderJid": sender_jid,
                "revokedAt": ts_ms,
                "isStatus": is_status,
                "isDeleteForMe": false,
            });
            if !from_history {
                runtime.emit_json("MESSAGE_REVOKE", rev.clone());
            }
            return NormalizedItem::Revoke(rev);
        }

        if proto_msg.r#type == Some(wa::message::protocol_message::Type::EPHEMERAL_SETTING.into()) {
            let exp_sec = proto_msg.ephemeral_expiration.unwrap_or(0);
            runtime.set_chat_ephemeral(&chat_jid, exp_sec);
            if !from_history {
                runtime.emit_json(
                    "CHAT_META_UPDATE",
                    json!({
                        "kind": "EPHEMERAL",
                        "jid": chat_jid,
                        "altJid": alt_chat_jid,
                        "ephemeralSec": exp_sec,
                        "timestamp": ts_ms,
                    }),
                );
            }
            return NormalizedItem::None;
        }
    }

    if let Some(react) = base.reaction_message.as_option() {
        let emoji = react.text.clone().unwrap_or_default();
        let target_id = react
            .key
            .as_option()
            .and_then(|k| k.id.clone())
            .unwrap_or_default();
        let rx = json!({
            "chatJid": chat_jid,
            "altChatJid": alt_chat_jid,
            "targetMessageId": target_id,
            "senderJid": sender_jid,
            "senderName": effective_sender_name,
            "isFromMe": info.source.is_from_me,
            "emoji": emoji,
            "timestamp": ts_ms,
        });
        if !from_history {
            runtime.emit_json("MESSAGE_REACTION", rx.clone());
        }
        return NormalizedItem::Reaction(rx);
    }

    if let Some(pin) = base.pin_in_chat_message.as_option() {
        let target_id = pin
            .key
            .as_option()
            .and_then(|k| k.id.clone())
            .unwrap_or_default();
        let is_unpin = pin
            .r#type
            .as_ref()
            .map(|t| format!("{t:?}").to_ascii_lowercase().contains("unpin"))
            .unwrap_or(false);
        let dur_secs = raw_msg
            .message_context_info
            .as_option()
            .and_then(|m| m.message_add_on_duration_in_secs)
            .unwrap_or(0);
        let pin_json = json!({
            "chatJid": chat_jid,
            "altChatJid": alt_chat_jid,
            "targetMessageId": target_id,
            "senderJid": sender_jid,
            "isFromMe": info.source.is_from_me,
            "isUnpin": is_unpin,
            "durationSec": dur_secs,
            "timestamp": ts_ms,
        });
        if !from_history {
            runtime.emit_json("MESSAGE_PIN_IN_CHAT", pin_json.clone());
        }
        return NormalizedItem::Pin(pin_json);
    }

    let (quoted_id, quoted_sender, quoted_body, mentions_csv, eph_sec) =
        extract_context_fields(raw_msg, None);
    let is_ephemeral = raw_msg.is_ephemeral() || eph_sec > 0;
    let is_view_once = raw_msg.is_view_once();
    if !from_history && eph_sec > 0 && !is_status {
        runtime.set_chat_ephemeral(&chat_jid, eph_sec as u32);
    }

    let (msg_type, body, caption, mime, file_name, duration_sec, file_size, waveform_csv, meta_opt) =
        classify_message(base, &msg_id, &chat_jid, &sender_jid, info.source.is_from_me);

    let mut local_media_path = String::new();
    if let Some(meta) = meta_opt {
        if !is_view_once {
            if let Some(shared) = runtime.shared_db_arc() {
                let _ = upsert_media_meta(&shared, meta.clone()).await;
            }
            let files_dir = runtime.files_dir_string();
            if !files_dir.is_empty() {
                let (subdir, default_ext) = media_subdir_and_ext(&meta.msg_type, &meta.mime_type);
                let candidate = Path::new(&files_dir)
                    .join("media")
                    .join(subdir)
                    .join(format!("{}{}", safe_file_component(&msg_id), default_ext));
                if candidate.exists() {
                    local_media_path = candidate.to_string_lossy().into_owned();
                } else if !from_history && runtime.is_auto_download_enabled(&meta.msg_type) {
                    spawn_auto_download(Arc::clone(runtime), meta, false, is_status, false);
                }
            }
        }
    }

    let payload = json!({
        "id": msg_id,
        "chatJid": chat_jid,
        "altChatJid": alt_chat_jid,
        "senderJid": sender_jid,
        "altSenderJid": alt_sender_jid,
        "senderName": effective_sender_name,
        "isFromMe": info.source.is_from_me,
        "isGroup": info.source.is_group || info.source.chat.is_group(),
        "timestamp": ts_ms,
        "body": body,
        "caption": caption,
        "msgType": msg_type,
        "mimeType": mime,
        "fileName": file_name,
        "mediaPath": local_media_path,
        "durationSec": duration_sec,
        "fileSize": file_size,
        "waveformCsv": waveform_csv,
        "deliveryStatus": if info.source.is_from_me { "SENT" } else { "DELIVERED" },
        "isViewOnce": is_view_once,
        "viewOnceState": if is_view_once { "PENDING" } else { "NONE" },
        "isEphemeral": is_ephemeral,
        "ephemeralDuration": eph_sec,
        "quotedId": quoted_id,
        "quotedSender": quoted_sender,
        "quotedBody": quoted_body,
        "mentionsCsv": mentions_csv,
        "reactionSummary": "",
        "isSync": from_history,
    });

    if !from_history {
        if is_status {
            runtime.emit_json("STATUS_UPSERT", payload.clone());
        } else {
            runtime.emit_json("MESSAGE_UPSERT", payload.clone());
        }
    }
    NormalizedItem::Message(payload)
}

fn extract_text_or_caption(msg: &wa::Message) -> String {
    if let Some(t) = msg.text_content() {
        return t.to_string();
    }
    if let Some(c) = msg.get_caption() {
        return c.to_string();
    }
    String::new()
}

fn extract_context_fields(
    msg: &wa::Message,
    ephemeral_opt: Option<u32>,
) -> (String, String, String, String, i64) {
    let Some(ctx) = msg.context_info() else {
        return (
            String::new(),
            String::new(),
            String::new(),
            String::new(),
            ephemeral_opt.unwrap_or(0) as i64,
        );
    };
    let q_id = ctx.stanza_id.clone().unwrap_or_default();
    let q_sender = ctx
        .participant
        .as_deref()
        .map(canonical_jid_str)
        .unwrap_or_default();
    let q_body = ctx
        .quoted_message
        .as_option()
        .map(|qm| {
            let s = extract_text_or_caption(qm);
            if !s.is_empty() {
                s
            } else if qm.image_message.is_set() {
                "Photo".to_string()
            } else if qm.video_message.is_set() {
                "Video".to_string()
            } else if qm.audio_message.is_set() {
                "Audio".to_string()
            } else if qm.document_message.is_set() {
                "Document".to_string()
            } else if qm.sticker_message.is_set() {
                "Sticker".to_string()
            } else {
                String::new()
            }
        })
        .unwrap_or_default();
    let mentions = ctx
        .mentioned_jid
        .iter()
        .map(|j| canonical_jid_str(j))
        .collect::<Vec<_>>()
        .join(",");
    let exp = ephemeral_opt.or(ctx.expiration).unwrap_or(0) as i64;
    (q_id, q_sender, q_body, mentions, exp)
}

fn classify_message(
    base: &wa::Message,
    msg_id: &str,
    chat_jid: &str,
    sender_jid: &str,
    is_from_me: bool,
) -> (
    String,
    String,
    String,
    String,
    String,
    i32,
    u64,
    String,
    Option<MediaMetaRow>,
) {
    if let Some(txt) = base.conversation.as_ref() {
        return (
            "TEXT".into(),
            txt.clone(),
            String::new(),
            String::new(),
            String::new(),
            0,
            0,
            String::new(),
            None,
        );
    }
    if let Some(ext) = base.extended_text_message.as_option() {
        return (
            "TEXT".into(),
            ext.text.clone().unwrap_or_default(),
            String::new(),
            String::new(),
            String::new(),
            0,
            0,
            String::new(),
            None,
        );
    }
    if let Some(img) = base.image_message.as_option() {
        let cap = img.caption.clone().unwrap_or_default();
        let mime = img.mimetype.clone().unwrap_or_else(|| "image/jpeg".into());
        let len = img.file_length.unwrap_or(0);
        let meta = MediaMetaRow {
            message_id: msg_id.into(),
            chat_jid: chat_jid.into(),
            sender_jid: sender_jid.into(),
            msg_type: "IMAGE".into(),
            direct_path: img.direct_path.clone().unwrap_or_default(),
            media_key: img.media_key.clone().unwrap_or_default(),
            file_enc_sha256: img.file_enc_sha256.clone().unwrap_or_default(),
            file_sha256: img.file_sha256.clone().unwrap_or_default(),
            file_length: len as i64,
            mime_type: mime.clone(),
            file_name: String::new(),
            is_from_me: i32::from(is_from_me),
        };
        return (
            "IMAGE".into(),
            cap.clone(),
            cap,
            mime,
            String::new(),
            0,
            len,
            String::new(),
            Some(meta),
        );
    }
    if let Some(vid) = base.video_message.as_option().or_else(|| base.ptv_message.as_option()) {
        let cap = vid.caption.clone().unwrap_or_default();
        let mime = vid.mimetype.clone().unwrap_or_else(|| "video/mp4".into());
        let len = vid.file_length.unwrap_or(0);
        let is_gif = vid.gif_playback.unwrap_or(false);
        let msg_type = if is_gif { "GIF" } else { "VIDEO" };
        let meta = MediaMetaRow {
            message_id: msg_id.into(),
            chat_jid: chat_jid.into(),
            sender_jid: sender_jid.into(),
            msg_type: msg_type.into(),
            direct_path: vid.direct_path.clone().unwrap_or_default(),
            media_key: vid.media_key.clone().unwrap_or_default(),
            file_enc_sha256: vid.file_enc_sha256.clone().unwrap_or_default(),
            file_sha256: vid.file_sha256.clone().unwrap_or_default(),
            file_length: len as i64,
            mime_type: mime.clone(),
            file_name: String::new(),
            is_from_me: i32::from(is_from_me),
        };
        return (
            msg_type.into(),
            cap.clone(),
            cap,
            mime,
            String::new(),
            vid.seconds.unwrap_or(0) as i32,
            len,
            String::new(),
            Some(meta),
        );
    }
    if let Some(aud) = base.audio_message.as_option() {
        let is_ptt = aud.ptt.unwrap_or(false);
        let msg_type = if is_ptt { "PTT" } else { "AUDIO" };
        let mime = aud
            .mimetype
            .clone()
            .unwrap_or_else(|| "audio/ogg; codecs=opus".into());
        let len = aud.file_length.unwrap_or(0);
        let wf = aud
            .waveform
            .as_deref()
            .map(|bytes| bytes.iter().map(|b| b.to_string()).collect::<Vec<_>>().join(","))
            .unwrap_or_default();
        let meta = MediaMetaRow {
            message_id: msg_id.into(),
            chat_jid: chat_jid.into(),
            sender_jid: sender_jid.into(),
            msg_type: msg_type.into(),
            direct_path: aud.direct_path.clone().unwrap_or_default(),
            media_key: aud.media_key.clone().unwrap_or_default(),
            file_enc_sha256: aud.file_enc_sha256.clone().unwrap_or_default(),
            file_sha256: aud.file_sha256.clone().unwrap_or_default(),
            file_length: len as i64,
            mime_type: mime.clone(),
            file_name: String::new(),
            is_from_me: i32::from(is_from_me),
        };
        return (
            msg_type.into(),
            String::new(),
            String::new(),
            mime,
            String::new(),
            aud.seconds.unwrap_or(0) as i32,
            len,
            wf,
            Some(meta),
        );
    }
    if let Some(doc) = base.document_message.as_option() {
        let cap = doc.caption.clone().unwrap_or_default();
        let mime = doc
            .mimetype
            .clone()
            .unwrap_or_else(|| "application/octet-stream".into());
        let fname = doc
            .file_name
            .clone()
            .or_else(|| doc.title.clone())
            .unwrap_or_else(|| "Document".into());
        let len = doc.file_length.unwrap_or(0);
        let meta = MediaMetaRow {
            message_id: msg_id.into(),
            chat_jid: chat_jid.into(),
            sender_jid: sender_jid.into(),
            msg_type: "DOCUMENT".into(),
            direct_path: doc.direct_path.clone().unwrap_or_default(),
            media_key: doc.media_key.clone().unwrap_or_default(),
            file_enc_sha256: doc.file_enc_sha256.clone().unwrap_or_default(),
            file_sha256: doc.file_sha256.clone().unwrap_or_default(),
            file_length: len as i64,
            mime_type: mime.clone(),
            file_name: fname.clone(),
            is_from_me: i32::from(is_from_me),
        };
        return (
            "DOCUMENT".into(),
            if cap.is_empty() { fname.clone() } else { cap.clone() },
            cap,
            mime,
            fname,
            0,
            len,
            String::new(),
            Some(meta),
        );
    }
    if let Some(stk) = base.sticker_message.as_option() {
        let mime = stk.mimetype.clone().unwrap_or_else(|| "image/webp".into());
        let len = stk.file_length.unwrap_or(0);
        let meta = MediaMetaRow {
            message_id: msg_id.into(),
            chat_jid: chat_jid.into(),
            sender_jid: sender_jid.into(),
            msg_type: "STICKER".into(),
            direct_path: stk.direct_path.clone().unwrap_or_default(),
            media_key: stk.media_key.clone().unwrap_or_default(),
            file_enc_sha256: stk.file_enc_sha256.clone().unwrap_or_default(),
            file_sha256: stk.file_sha256.clone().unwrap_or_default(),
            file_length: len as i64,
            mime_type: mime.clone(),
            file_name: String::new(),
            is_from_me: i32::from(is_from_me),
        };
        return (
            "STICKER".into(),
            String::new(),
            String::new(),
            mime,
            String::new(),
            0,
            len,
            String::new(),
            Some(meta),
        );
    }
    if let Some(loc) = base.location_message.as_option() {
        let label = loc.name.clone().or_else(|| loc.address.clone()).unwrap_or_default();
        let coords = format!(
            "{:.5}, {:.5}{}",
            loc.degrees_latitude.unwrap_or(0.0),
            loc.degrees_longitude.unwrap_or(0.0),
            if label.is_empty() {
                String::new()
            } else {
                format!(" ({label})")
            }
        );
        return (
            "LOCATION".into(),
            coords,
            String::new(),
            String::new(),
            String::new(),
            0,
            0,
            String::new(),
            None,
        );
    }
    if let Some(contact) = base.contact_message.as_option() {
        return (
            "CONTACT".into(),
            contact.display_name.clone().unwrap_or_else(|| "Contact".into()),
            String::new(),
            "text/vcard".into(),
            String::new(),
            0,
            0,
            String::new(),
            None,
        );
    }
    if let Some(poll) = base
        .poll_creation_message_v3
        .as_option()
        .or_else(|| base.poll_creation_message.as_option())
    {
        return (
            "POLL".into(),
            poll.name.clone().unwrap_or_else(|| "Poll".into()),
            String::new(),
            String::new(),
            String::new(),
            0,
            0,
            String::new(),
            None,
        );
    }
    if let Some(call_log) = base.call_log_messsage.as_option() {
        let is_video = call_log.is_video.unwrap_or(false);
        let dur = call_log.duration_secs.unwrap_or(0).max(0);
        let outcome = call_log.call_outcome;
        use wa::message::call_log_message::CallOutcome;
        let (title, sub) = match outcome {
            Some(CallOutcome::SILENCED_BY_DND) => (
                if is_video { "Silenced video call" } else { "Silenced voice call" },
                "While in \"Do Not Disturb\"".to_string(),
            ),
            Some(CallOutcome::SILENCED_UNKNOWN_CALLER) => (
                if is_video { "Silenced video call" } else { "Silenced voice call" },
                "Unknown caller".to_string(),
            ),
            Some(CallOutcome::MISSED) => (
                if is_video { "Missed video call" } else { "Missed voice call" },
                "Tap to call back".to_string(),
            ),
            Some(CallOutcome::REJECTED) => (
                if is_video { "Video call" } else { "Voice call" },
                if is_from_me { "No answer".to_string() } else { "Declined".to_string() },
            ),
            Some(CallOutcome::FAILED) => (
                if is_video { "Video call" } else { "Voice call" },
                "No answer".to_string(),
            ),
            Some(CallOutcome::ACCEPTED_ELSEWHERE) => (
                if is_video { "Video call" } else { "Voice call" },
                if dur > 0 { format_call_duration_text(dur) } else { "Answered elsewhere".to_string() },
            ),
            _ => (
                if is_video { "Video call" } else { "Voice call" },
                if dur > 0 {
                    format_call_duration_text(dur)
                } else if is_from_me {
                    "No answer".to_string()
                } else {
                    "Answered".to_string()
                },
            ),
        };
        return (
            "CALL".into(),
            format!("{title}|{sub}"),
            String::new(),
            String::new(),
            String::new(),
            dur as i32,
            0,
            String::new(),
            None,
        );
    }

    (
        "UNKNOWN".into(),
        String::new(),
        String::new(),
        String::new(),
        String::new(),
        0,
        0,
        String::new(),
        None,
    )
}

pub fn format_call_duration_text(secs: i64) -> String {
    let s = secs.max(0);
    if s < 60 {
        format!("{s} sec")
    } else if s < 3600 {
        let m = s / 60;
        let rem = s % 60;
        if rem == 0 {
            format!("{m} min")
        } else {
            format!("{m} min {rem} sec")
        }
    } else {
        let h = s / 3600;
        let m = (s % 3600) / 60;
        if m == 0 {
            format!("{h} hr")
        } else {
            format!("{h} hr {m} min")
        }
    }
}

pub fn format_call_record_body(record: &wa::CallLogRecord, from_me: bool) -> (String, i32) {
    use wa::call_log_record::{CallResult, SilenceReason};
    let is_video = record.is_video.unwrap_or(false);
    let dur = record.duration.unwrap_or(0).max(0);
    let is_dnd = record.is_dnd_mode.unwrap_or(false)
        || matches!(record.silence_reason, Some(SilenceReason::SCHEDULED));
    let is_unknown_silenced = matches!(record.silence_reason, Some(SilenceReason::PRIVACY));
    let call_result = record.call_result;

    let (title, sub) = if is_dnd {
        (
            if is_video { "Silenced video call" } else { "Silenced voice call" },
            "While in \"Do Not Disturb\"".to_string(),
        )
    } else if is_unknown_silenced {
        (
            if is_video { "Silenced video call" } else { "Silenced voice call" },
            "Unknown caller".to_string(),
        )
    } else {
        match call_result {
            Some(CallResult::MISSED) => (
                if is_video { "Missed video call" } else { "Missed voice call" },
                "Tap to call back".to_string(),
            ),
            Some(CallResult::REJECTED) => (
                if is_video { "Video call" } else { "Voice call" },
                if from_me { "No answer".to_string() } else { "Declined".to_string() },
            ),
            Some(CallResult::CANCELLED | CallResult::UNAVAILABLE | CallResult::FAILED | CallResult::ABANDONED) => (
                if is_video { "Video call" } else { "Voice call" },
                "No answer".to_string(),
            ),
            Some(CallResult::ACCEPTEDELSEWHERE) => (
                if is_video { "Video call" } else { "Voice call" },
                if dur > 0 { format_call_duration_text(dur) } else { "Answered elsewhere".to_string() },
            ),
            _ => (
                if is_video { "Video call" } else { "Voice call" },
                if dur > 0 {
                    format_call_duration_text(dur)
                } else if from_me {
                    "No answer".to_string()
                } else {
                    "Missed".to_string()
                },
            ),
        }
    };
    (format!("{title}|{sub}"), dur as i32)
}

fn spawn_auto_download(
    runtime: Arc<BridgeRuntime>,
    meta: MediaMetaRow,
    is_view_once: bool,
    is_status: bool,
    is_historical: bool,
) {
    let msg_id = meta.message_id.clone();
    if !runtime.try_begin_download(&msg_id) {
        return;
    }
    let rt_dl = Arc::clone(&runtime);
    runtime.rt.spawn(async move {
        let _permit = rt_dl.auto_dl_sem.acquire().await;
        let res = download_and_save_media(&rt_dl, &meta).await;
        rt_dl.finish_download(&meta.message_id);
        match res {
            Ok((path, file_size, wf)) => {
                rt_dl.emit_json(
                    "MEDIA_DOWNLOADED",
                    json!({
                        "messageId": meta.message_id,
                        "chatJid": meta.chat_jid,
                        "localPath": path,
                        "sha256": "",
                        "fileSize": file_size,
                        "isViewOnce": is_view_once,
                        "isStatus": is_status,
                        "isHistorical": is_historical,
                        "isFromMe": meta.is_from_me != 0,
                        "mediaType": meta.msg_type,
                        "mimeType": meta.mime_type,
                        "waveformCsv": wf,
                    }),
                );
            }
            Err(e) => {
                if is_view_once {
                    rt_dl.emit_json(
                        "VIEW_ONCE_EXPIRED",
                        json!({
                            "messageId": meta.message_id,
                            "mediaType": meta.msg_type,
                        }),
                    );
                }
                rt_dl.emit_json(
                    "MEDIA_DOWNLOAD_STATUS",
                    json!({
                        "messageId": meta.message_id,
                        "status": "FAILED",
                        "reason": e,
                    }),
                );
            }
        }
    });
}

async fn download_and_save_media(
    runtime: &BridgeRuntime,
    meta: &MediaMetaRow,
) -> Result<(String, usize, String), String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let files_dir = runtime.files_dir_string();
    if files_dir.is_empty() {
        return Err("filesDir not set".to_string());
    }

    let first_try = client.download(meta, DownloadLimits::default()).await;
    let bytes = match first_try {
        Ok(b) => b,
        Err(_err) if !meta.chat_jid.is_empty() && !meta.media_key.is_empty() => {
            if let (Ok(chat), Ok(mid)) = (
                parse_jid_flexible(&meta.chat_jid),
                MessageId::new(&meta.message_id),
            ) {
                let sender_opt = (!meta.sender_jid.is_empty())
                    .then(|| parse_jid_flexible(&meta.sender_jid).ok())
                    .flatten();
                let key_arr: [u8; 32] = meta
                    .media_key
                    .as_slice()
                    .try_into()
                    .map_err(|_| "invalid 32-byte media_key".to_string())?;
                let target = MessageRef::new(&chat, mid, sender_opt.as_ref(), meta.is_from_me != 0)
                    .map_err(|e| format!("reupload ref error: {e}"))?;
                let reupload_req = MediaReuploadRequest {
                    target,
                    media_key: &key_arr,
                };
                if let Ok(MediaRetryResult::Success {
                    direct_path: new_path,
                }) = client.media_reupload().request(&reupload_req).await
                {
                    let mut updated = meta.clone();
                    updated.direct_path = new_path;
                    client
                        .download(&updated, DownloadLimits::default())
                        .await
                        .map_err(|e2| format!("download failed after reupload: {e2}"))?
                } else {
                    return Err("media download & reupload failed".to_string());
                }
            } else {
                return Err("media download failed (invalid jid/id)".to_string());
            }
        }
        Err(e) => return Err(format!("media download failed: {e}")),
    };

    let size = bytes.len();
    let wf = if meta.msg_type.eq_ignore_ascii_case("PTT") || meta.msg_type.eq_ignore_ascii_case("AUDIO") {
        compute_40_bar_waveform(&bytes)
    } else {
        String::new()
    };
    let path = save_media_bytes(
        &files_dir,
        &meta.message_id,
        &meta.msg_type,
        &meta.mime_type,
        &meta.file_name,
        &bytes,
    )?;
    Ok((path, size, wf))
}

fn compute_40_bar_waveform(data: &[u8]) -> String {
    const BARS: usize = 40;
    if data.len() < BARS {
        return "25,40,65,50,80,60,45,70,85,55,40,75,90,65,50,35,60,80,70,45,35,55,75,65,50,85,70,45,60,75,55,40,65,80,60,45,70,50,35,25".into();
    }
    let chunk = data.len() / BARS;
    (0..BARS)
        .map(|i| {
            let start = i * chunk;
            let end = (start + chunk).min(data.len());
            let sum: u32 = data[start..end]
                .iter()
                .map(|&b| u32::from(b.abs_diff(128)))
                .sum();
            let avg = sum / ((end - start).max(1) as u32);
            (15 + (avg * 85) / 128).min(100).to_string()
        })
        .collect::<Vec<_>>()
        .join(",")
}

pub async fn request_media_download(
    runtime: &Arc<BridgeRuntime>,
    chat_jid: &str,
    sender_jid: &str,
    message_id: &str,
) -> Result<(), String> {
    let shared = runtime
        .shared_db_arc()
        .ok_or_else(|| "storage not initialized".to_string())?;
    let mut meta = get_media_meta(&shared, message_id)
        .await
        .ok_or_else(|| format!("no cached media metadata for {message_id}"))?;
    if meta.chat_jid.is_empty() && !chat_jid.is_empty() {
        meta.chat_jid = canonical_jid_str(chat_jid);
    }
    if meta.sender_jid.is_empty() && !sender_jid.is_empty() {
        meta.sender_jid = canonical_jid_str(sender_jid);
    }
    let is_status = meta.chat_jid == "status@broadcast";
    spawn_auto_download(Arc::clone(runtime), meta, false, is_status, false);
    Ok(())
}

pub async fn send_text_message(
    runtime: &BridgeRuntime,
    chat_jid_str: &str,
    text: &str,
    quoted_id: &str,
    quoted_sender: &str,
    quoted_body: &str,
    mentions_csv: &str,
    ghost_typing: bool,
) -> Result<String, String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let to = parse_jid_flexible(chat_jid_str)?;
    let mentions = split_csv(mentions_csv)
        .into_iter()
        .map(|s| canonical_jid_str(&s))
        .collect::<Vec<_>>();

    if !ghost_typing && !runtime.ghost_presence.load(std::sync::atomic::Ordering::Relaxed) {
        let _ = client.chatstate().send_paused(&to).await;
    }

    let message = build_text_or_extended_message(
        text,
        quoted_id,
        quoted_sender,
        quoted_body,
        mentions,
    );

    let canonical_to = canonical_jid_str(&to.to_string());
    let eph_secs = runtime.chat_ephemeral_secs(&canonical_to);
    let mut opts = SendOptions::default();
    if eph_secs > 0 {
        opts = opts.with_ephemeral_expiration(eph_secs);
    }

    let result = client
        .send(SendRequest::new(&to, message).with_options(opts))
        .await
        .map_err(|e| format!("send text failed: {e}"))?;
    Ok(result.message_id.to_string())
}

fn build_text_or_extended_message(
    text: &str,
    quoted_id: &str,
    quoted_sender: &str,
    quoted_body: &str,
    mentions: Vec<String>,
) -> wa::Message {
    if quoted_id.is_empty() && mentions.is_empty() {
        let mut m = wa::Message::default();
        m.conversation = Some(text.to_string());
        return m;
    }

    let mut ctx = wa::ContextInfo::default();
    if !mentions.is_empty() {
        ctx.mentioned_jid = mentions;
    }
    if !quoted_id.is_empty() {
        ctx.stanza_id = Some(quoted_id.to_string());
        if !quoted_sender.is_empty() {
            ctx.participant = Some(canonical_jid_str(quoted_sender));
        }
        let mut qm = wa::Message::default();
        qm.conversation = Some(if quoted_body.is_empty() {
            " ".to_string()
        } else {
            quoted_body.to_string()
        });
        ctx.quoted_message = buffa::MessageField::from_box(Box::new(qm));
    }

    let mut ext = wa::message::ExtendedTextMessage::default();
    ext.text = Some(text.to_string());
    ext.context_info = buffa::MessageField::some(ctx);

    let mut msg = wa::Message::default();
    msg.extended_text_message = buffa::MessageField::some(ext);
    msg
}

pub async fn send_media_message(
    runtime: &BridgeRuntime,
    chat_jid_str: &str,
    file_path: &str,
    mime_type: &str,
    caption: &str,
    quality_mode: &str,
    is_voice_note: bool,
    duration_sec: i32,
    waveform_csv: &str,
    is_gif: bool,
) -> Result<String, String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let to = parse_jid_flexible(chat_jid_str)?;
    let path_owned = file_path.to_string();
    let data = tokio::task::spawn_blocking(move || std::fs::read(&path_owned))
        .await
        .map_err(|e| format!("read task failed for {file_path}: {e}"))?
        .map_err(|e| format!("failed to read upload file {file_path}: {e}"))?;

    let mime_lower = mime_type.to_ascii_lowercase();
    let raw_mode = quality_mode.trim().to_ascii_lowercase();
    let is_view_once_flag = raw_mode.contains("viewonce") || raw_mode.contains("view_once");
    let mode_clean = raw_mode
        .trim_end_matches(":viewonce")
        .trim_end_matches("_viewonce")
        .trim();
    let is_sticker_out = !is_voice_note && (mode_clean == "sticker" || raw_mode.contains("sticker"));
    let is_gif_out = is_gif
        || (!is_voice_note
            && !is_sticker_out
            && (mode_clean == "gif" || raw_mode.contains("gif")));
    let is_view_once_out = is_view_once_flag && !is_gif_out && !is_sticker_out;

    let media_type = if is_voice_note {
        MediaType::Ptt
    } else if is_gif_out {
        MediaType::Gif
    } else if is_sticker_out
        || (mime_lower.starts_with("image/webp") && !is_view_once_out && caption.is_empty())
    {
        MediaType::Sticker
    } else if !is_view_once_out && mode_clean == "original" && !mime_lower.starts_with("video/") {
        MediaType::Document
    } else if mime_lower.starts_with("image/") {
        MediaType::Image
    } else if mime_lower.starts_with("video/") {
        MediaType::Video
    } else if mime_lower.starts_with("audio/") {
        MediaType::Audio
    } else {
        MediaType::Document
    };

    let upload = client
        .upload(data, media_type, UploadOptions::default())
        .await
        .map_err(|e| format!("media upload failed: {e}"))?;

    let file_name = Path::new(file_path)
        .file_name()
        .and_then(|s| s.to_str())
        .unwrap_or("file")
        .to_string();

    let mut message = wa::Message::default();
    match media_type {
        MediaType::Image => {
            let mut img = wa::message::ImageMessage::default();
            img.url = Some(upload.url);
            img.direct_path = Some(upload.direct_path);
            img.media_key = Some(upload.media_key.to_vec());
            img.file_enc_sha256 = Some(upload.file_enc_sha256.to_vec());
            img.file_sha256 = Some(upload.file_sha256.to_vec());
            img.file_length = Some(upload.file_length);
            img.media_key_timestamp = Some(upload.media_key_timestamp);
            img.mimetype = Some(if mime_type.is_empty() {
                "image/jpeg".into()
            } else {
                mime_type.into()
            });
            if !caption.is_empty() {
                img.caption = Some(caption.into());
            }
            if is_view_once_out {
                img.view_once = Some(true);
            }
            message.image_message = buffa::MessageField::some(img);
        }
        MediaType::Sticker => {
            let mut stk = wa::message::StickerMessage::default();
            stk.url = Some(upload.url);
            stk.direct_path = Some(upload.direct_path);
            stk.media_key = Some(upload.media_key.to_vec());
            stk.file_enc_sha256 = Some(upload.file_enc_sha256.to_vec());
            stk.file_sha256 = Some(upload.file_sha256.to_vec());
            stk.file_length = Some(upload.file_length);
            stk.media_key_timestamp = Some(upload.media_key_timestamp);
            stk.mimetype = Some("image/webp".into());
            message.sticker_message = buffa::MessageField::some(stk);
        }
        MediaType::Video | MediaType::Gif => {
            let mut vid = wa::message::VideoMessage::default();
            vid.url = Some(upload.url);
            vid.direct_path = Some(upload.direct_path);
            vid.media_key = Some(upload.media_key.to_vec());
            vid.file_enc_sha256 = Some(upload.file_enc_sha256.to_vec());
            vid.file_sha256 = Some(upload.file_sha256.to_vec());
            vid.file_length = Some(upload.file_length);
            vid.media_key_timestamp = Some(upload.media_key_timestamp);
            vid.mimetype = Some(if mime_type.is_empty() {
                "video/mp4".into()
            } else {
                mime_type.into()
            });
            if duration_sec > 0 {
                vid.seconds = Some(duration_sec as u32);
            }
            if is_gif_out {
                vid.gif_playback = Some(true);
            }
            if is_view_once_out {
                vid.view_once = Some(true);
            }
            if !caption.is_empty() {
                vid.caption = Some(caption.into());
            }
            vid.streaming_sidecar = upload.streaming_sidecar;
            message.video_message = buffa::MessageField::some(vid);
        }
        MediaType::Audio | MediaType::Ptt => {
            let mut aud = wa::message::AudioMessage::default();
            aud.url = Some(upload.url);
            aud.direct_path = Some(upload.direct_path);
            aud.media_key = Some(upload.media_key.to_vec());
            aud.file_enc_sha256 = Some(upload.file_enc_sha256.to_vec());
            aud.file_sha256 = Some(upload.file_sha256.to_vec());
            aud.file_length = Some(upload.file_length);
            aud.media_key_timestamp = Some(upload.media_key_timestamp);
            aud.mimetype = Some(if mime_type.is_empty() {
                "audio/ogg; codecs=opus".into()
            } else {
                mime_type.into()
            });
            aud.ptt = Some(is_voice_note);
            if duration_sec > 0 {
                aud.seconds = Some(duration_sec as u32);
            }
            let wf: Vec<u8> = waveform_csv
                .split(',')
                .filter_map(|s| s.trim().parse::<u8>().ok())
                .collect();
            if !wf.is_empty() {
                aud.waveform = Some(wf);
            }
            aud.streaming_sidecar = upload.streaming_sidecar;
            message.audio_message = buffa::MessageField::some(aud);
        }
        _ => {
            let mut doc = wa::message::DocumentMessage::default();
            doc.url = Some(upload.url);
            doc.direct_path = Some(upload.direct_path);
            doc.media_key = Some(upload.media_key.to_vec());
            doc.file_enc_sha256 = Some(upload.file_enc_sha256.to_vec());
            doc.file_sha256 = Some(upload.file_sha256.to_vec());
            doc.file_length = Some(upload.file_length);
            doc.media_key_timestamp = Some(upload.media_key_timestamp);
            doc.mimetype = Some(if mime_type.is_empty() {
                "application/octet-stream".into()
            } else {
                mime_type.into()
            });
            doc.file_name = Some(file_name.clone());
            doc.title = Some(file_name);
            if !caption.is_empty() {
                doc.caption = Some(caption.into());
            }
            message.document_message = buffa::MessageField::some(doc);
        }
    }

    let final_message = if is_view_once_out && matches!(media_type, MediaType::Image | MediaType::Video) {
        let mut fpm = wa::message::FutureProofMessage::default();
        fpm.message = buffa::MessageField::from_box(Box::new(message));
        let mut wrapper = wa::Message::default();
        wrapper.view_once_message_v2 = buffa::MessageField::some(fpm);
        wrapper
    } else {
        message
    };

    let canonical_to = canonical_jid_str(&to.to_string());
    let eph_secs = runtime.chat_ephemeral_secs(&canonical_to);
    let mut opts = SendOptions::default();
    if eph_secs > 0 {
        opts = opts.with_ephemeral_expiration(eph_secs);
    }

    let result = client
        .send(SendRequest::new(&to, final_message).with_options(opts))
        .await
        .map_err(|e| format!("send media failed: {e}"))?;
    Ok(result.message_id.to_string())
}

pub async fn edit_message(
    runtime: &BridgeRuntime,
    chat_jid_str: &str,
    message_id: &str,
    new_text: &str,
) -> Result<(), String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let chat = parse_jid_flexible(chat_jid_str)?;
    let mid = MessageId::new(message_id).map_err(|e| format!("invalid message id: {e}"))?;
    let target = MessageRef::new(&chat, mid, None, true).map_err(|e| format!("{e}"))?;
    let mut msg = wa::Message::default();
    msg.conversation = Some(new_text.to_string());
    client
        .edit_message(EditRequest::new(target, msg))
        .await
        .map_err(|e| format!("edit failed: {e}"))?;
    Ok(())
}

pub async fn revoke_message(
    runtime: &BridgeRuntime,
    chat_jid_str: &str,
    message_id: &str,
) -> Result<(), String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let chat = parse_jid_flexible(chat_jid_str)?;
    client
        .revoke_message_raw(chat, message_id.to_string(), RevokeType::Sender)
        .await
        .map_err(|e| format!("revoke failed: {e}"))?;
    Ok(())
}

pub async fn delete_chat_remote(
    runtime: &BridgeRuntime,
    chat_jid_str: &str,
    last_msg_id: &str,
    last_from_me: bool,
    last_timestamp_ms: i64,
    delete_media: bool,
) -> Result<(), String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let chat = parse_jid_flexible(chat_jid_str)?;
    let ts_secs = if last_timestamp_ms > 0 {
        last_timestamp_ms / 1000
    } else {
        now_millis() / 1000
    };
    let mut items = Vec::new();
    if let Ok(mid) = MessageId::new(last_msg_id) {
        if let Ok(mref) = MessageRef::new(&chat, mid, None, last_from_me) {
            items.push((mref, ts_secs));
        }
    }
    let range = whatsapp_rust::features::message_range(ts_secs, None, items);
    client
        .chat_actions()
        .delete_chat(&chat, delete_media, Some(range))
        .await
        .map_err(|e| format!("delete_chat failed: {e}"))
}

pub async fn set_chat_pin_remote(
    runtime: &BridgeRuntime,
    chat_jid_str: &str,
    pinned: bool,
) -> Result<(), String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let chat = parse_jid_flexible(chat_jid_str)?;
    if pinned {
        client
            .chat_actions()
            .pin_chat(&chat)
            .await
            .map_err(|e| format!("pin_chat failed: {e}"))
    } else {
        client
            .chat_actions()
            .unpin_chat(&chat)
            .await
            .map_err(|e| format!("unpin_chat failed: {e}"))
    }
}

pub async fn set_chat_mute_remote(
    runtime: &BridgeRuntime,
    chat_jid_str: &str,
    muted: bool,
) -> Result<(), String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let chat = parse_jid_flexible(chat_jid_str)?;
    if chat.is_newsletter() {
        return client
            .newsletter()
            .set_follower_mute(&chat, muted)
            .await
            .map_err(|e| format!("toggle newsletter mute failed: {e}"));
    }
    if muted {
        client
            .chat_actions()
            .mute_chat(&chat)
            .await
            .map_err(|e| format!("mute_chat failed: {e}"))
    } else {
        client
            .chat_actions()
            .unmute_chat(&chat)
            .await
            .map_err(|e| format!("unmute_chat failed: {e}"))
    }
}

pub async fn set_chat_archive_remote(
    runtime: &BridgeRuntime,
    chat_jid_str: &str,
    archived: bool,
) -> Result<(), String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let chat = parse_jid_flexible(chat_jid_str)?;
    if archived {
        client
            .chat_actions()
            .archive_chat(&chat, None)
            .await
            .map_err(|e| format!("archive_chat failed: {e}"))
    } else {
        client
            .chat_actions()
            .unarchive_chat(&chat, None)
            .await
            .map_err(|e| format!("unarchive_chat failed: {e}"))
    }
}

pub async fn mark_read_manual(
    runtime: &BridgeRuntime,
    chat_jid_str: &str,
    sender_jid_str: &str,
    message_ids_csv: &str,
) -> Result<(), String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let chat = parse_jid_flexible(chat_jid_str)?;
    let sender = (!sender_jid_str.trim().is_empty())
        .then(|| parse_jid_flexible(sender_jid_str).ok())
        .flatten();
    let ids = split_csv(message_ids_csv);
    let id_refs: Vec<&str> = ids.iter().map(String::as_str).collect();
    if id_refs.is_empty() {
        return Ok(());
    }
    client
        .mark_as_read(&chat, sender.as_ref(), &id_refs)
        .await
        .map_err(|e| format!("mark_as_read failed: {e}"))
}

pub async fn send_played_receipt_manual(
    runtime: &BridgeRuntime,
    chat_jid_str: &str,
    sender_jid_str: &str,
    message_id: &str,
) -> Result<(), String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let chat = parse_jid_flexible(chat_jid_str)?;
    let sender = (!sender_jid_str.trim().is_empty())
        .then(|| parse_jid_flexible(sender_jid_str).ok())
        .flatten();
    if message_id.trim().is_empty() {
        return Ok(());
    }
    client
        .mark_as_played(&chat, sender.as_ref(), &[message_id.trim()])
        .await
        .map_err(|e| format!("mark_as_played failed: {e}"))
}

pub async fn send_chat_presence_manual(
    runtime: &BridgeRuntime,
    chat_jid_str: &str,
    state: &str,
    media: &str,
    blocked_by_ghost: bool,
) -> Result<(), String> {
    if blocked_by_ghost || runtime.ghost_presence.load(std::sync::atomic::Ordering::Relaxed) {
        return Ok(());
    }
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let chat = parse_jid_flexible(chat_jid_str)?;
    let activity = match state {
        "composing" if media == "audio" => ChatActivity::RecordingAudio,
        "composing" => ChatActivity::Typing,
        _ => ChatActivity::Idle,
    };
    client
        .chatstate()
        .send(&chat, activity)
        .await
        .map_err(|e| format!("send chatstate failed: {e}"))
}

pub async fn send_reaction(
    runtime: &BridgeRuntime,
    chat_jid_str: &str,
    target_id: &str,
    sender_jid_str: &str,
    from_me: bool,
    emoji: &str,
) -> Result<(), String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let chat = parse_jid_flexible(chat_jid_str)?;
    if chat.is_newsletter() {
        let sid: u64 = target_id
            .parse()
            .map_err(|_| "newsletter reaction target_id must be server_id integer".to_string())?;
        let nref = whatsapp_rust::wacore::types::message_ref::NewsletterMessageRef::new(
            &chat,
            None,
            Some(sid.into()),
        )
        .map_err(|e| format!("{e}"))?;
        client
            .newsletter()
            .send_reaction(&nref, emoji)
            .await
            .map_err(|e| format!("newsletter reaction failed: {e}"))?;
        return Ok(());
    }

    let mut key = wa::MessageKey::default();
    key.remote_jid = Some(chat.to_string());
    key.id = Some(target_id.to_string());
    key.from_me = Some(from_me);
    if (chat.is_group() || chat.is_status_broadcast()) && !sender_jid_str.trim().is_empty() {
        key.participant = Some(canonical_jid_str(sender_jid_str));
    }
    client
        .send_reaction_raw(chat, key, emoji)
        .await
        .map_err(|e| format!("send_reaction failed: {e}"))?;
    Ok(())
}

pub async fn send_status_reply(
    runtime: &BridgeRuntime,
    author_jid_str: &str,
    status_id: &str,
    reply_text: &str,
    orig_body: &str,
) -> Result<(), String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let author = parse_jid_flexible(author_jid_str)?;
    let mut ctx = wa::ContextInfo::default();
    ctx.stanza_id = Some(status_id.to_string());
    ctx.participant = Some(author.to_string());
    ctx.remote_jid = Some("status@broadcast".to_string());
    if !orig_body.trim().is_empty() {
        let mut qm = wa::Message::default();
        qm.conversation = Some(orig_body.to_string());
        ctx.quoted_message = buffa::MessageField::from_box(Box::new(qm));
    }

    let mut ext = wa::message::ExtendedTextMessage::default();
    ext.text = Some(reply_text.to_string());
    ext.context_info = buffa::MessageField::some(ctx);

    let mut msg = wa::Message::default();
    msg.extended_text_message = buffa::MessageField::some(ext);
    client
        .send_message(author, msg)
        .await
        .map_err(|e| format!("status reply failed: {e}"))?;
    Ok(())
}

pub async fn send_status_reaction(
    runtime: &BridgeRuntime,
    author_jid_str: &str,
    status_id: &str,
    emoji: &str,
) -> Result<(), String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let status_chat = parse_jid_flexible("status@broadcast")?;
    let mut key = wa::MessageKey::default();
    key.remote_jid = Some(status_chat.to_string());
    key.id = Some(status_id.to_string());
    key.from_me = Some(false);
    key.participant = Some(canonical_jid_str(author_jid_str));

    let msg = build_reaction_message(key, emoji, now_millis());
    client
        .send(SendRequest::new(&status_chat, msg).with_options(SendOptions::default()))
        .await
        .map_err(|e| format!("status reaction failed: {e}"))?;
    Ok(())
}

pub async fn pin_chat_message(
    runtime: &BridgeRuntime,
    chat_jid_str: &str,
    message_id: &str,
    from_me: bool,
    sender_jid_str: &str,
    duration_secs: i64,
    is_unpin: bool,
) -> Result<(), String> {
    let client = runtime
        .client_arc()
        .ok_or_else(|| "client not connected".to_string())?;
    let chat = parse_jid_flexible(chat_jid_str)?;
    if message_id.trim().is_empty() {
        if is_unpin {
            client.chat_actions().unpin_chat(&chat).await
        } else {
            client.chat_actions().pin_chat(&chat).await
        }
        .map_err(|e| format!("pin chat failed: {e}"))?;
        return Ok(());
    }

    let mut key = wa::MessageKey::default();
    key.remote_jid = Some(chat.to_string());
    key.id = Some(message_id.to_string());
    key.from_me = Some(from_me);
    if chat.is_group() && !sender_jid_str.trim().is_empty() {
        key.participant = Some(canonical_jid_str(sender_jid_str));
    }
    if is_unpin {
        client
            .unpin_message_raw(chat, key)
            .await
            .map_err(|e| format!("unpin_message failed: {e}"))?;
    } else {
        let dur = match duration_secs {
            d if d <= 86400 => PinDuration::Hours24,
            d if d <= 604800 => PinDuration::Days7,
            _ => PinDuration::Days30,
        };
        client
            .pin_message_raw(chat, key, dur)
            .await
            .map_err(|e| format!("pin_message failed: {e}"))?;
    }
    Ok(())
}
