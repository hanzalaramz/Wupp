use std::collections::HashMap;
use std::sync::atomic::Ordering;
use std::sync::Arc;

use serde_json::{json, Value};
use whatsapp_rust::client::Client;
use whatsapp_rust::types::events::{Event, LazyHistorySync, UnavailableType};
use whatsapp_rust::wacore::types::presence::{ChatActivity, PresenceStatus, ReceiptType};
use whatsapp_rust::wacore_binary::{Jid, JidExt};
use whatsapp_rust::waproto::whatsapp as wa;

use crate::contacts_groups::{fetch_and_cache_avatar_if_needed, resolve_dm_chat_pair_local};
use crate::jid_helpers::canonical_jid_str;
use crate::meta_db::{clear_avatar_miss, get_contact_meta, list_contact_lid_pn_pairs, upsert_contact_meta};
use crate::messaging::{format_call_record_body, normalize_message, NormalizedItem};
use crate::runtime::{now_millis, now_secs, BridgeRuntime};

async fn resolve_chat_alt_jid(client: &Client, jid: &Jid) -> String {
    let Ok(Some(entry)) = client.get_lid_pn_entry(jid).await else {
        return String::new();
    };
    if jid.is_lid() && !entry.phone_number.is_empty() {
        format!("{}@s.whatsapp.net", entry.phone_number)
    } else if jid.is_pn() && !entry.lid.is_empty() {
        format!("{}@lid", entry.lid)
    } else {
        String::new()
    }
}

/// Translates `whatsapp-rust` SDK events into Wupp's JSON event stream.
pub async fn handle_sdk_event(runtime: Arc<BridgeRuntime>, event: Arc<Event>, client: Arc<Client>) {
    match &*event {
        Event::PairingQrCode(qr) => {
            runtime.emit_json(
                "QR",
                json!({
                    "code": qr.code,
                }),
            );
        }
        Event::PairingCode(pc) => {
            runtime.emit_json(
                "PAIR_CODE",
                json!({
                    "code": pc.code,
                }),
            );
        }
        Event::PairingCodeError(err) => {
            runtime.emit_log(format!("Pairing code error: {}", err.error));
        }
        Event::PairSuccess(ok) => {
            runtime.logged_in.store(true, Ordering::SeqCst);
            let own_jid = canonical_jid_str(&ok.id.to_string());
            runtime.emit_json(
                "CONNECTION_STATE",
                json!({
                    "state": "CONNECTED",
                    "ownJid": own_jid,
                    "isLoggedIn": true,
                }),
            );
        }
        Event::Connected(_) => {
            runtime.connected.store(true, Ordering::SeqCst);
            runtime.terminal_stop.store(false, Ordering::SeqCst);
            runtime.rate_limit_until_unix.store(0, Ordering::SeqCst);
            let snap = client.persistence_manager().get_device_snapshot();
            let logged = snap.pn.is_some() || snap.lid.is_some();
            runtime.logged_in.store(logged, Ordering::SeqCst);
            let own_jid = snap
                .pn
                .as_ref()
                .map(|j| canonical_jid_str(&j.to_string()))
                .or_else(|| snap.lid.as_ref().map(|j| canonical_jid_str(&j.to_string())))
                .unwrap_or_default();

            runtime.emit_json(
                "CONNECTION_STATE",
                json!({
                    "state": "CONNECTED",
                    "ownJid": own_jid,
                    "isLoggedIn": logged,
                }),
            );

            if let Some(shared) = runtime.shared_db_arc() {
                let pairs = list_contact_lid_pn_pairs(&shared).await;
                if !pairs.is_empty() {
                    let mappings: Vec<Value> = pairs
                        .into_iter()
                        .map(|(pn, lid)| json!({ "pn": pn, "lid": lid }))
                        .collect();
                    runtime.emit_json("LID_PN_MAPPINGS", json!({ "mappings": mappings }));
                }
            }

            if runtime.ghost_presence.load(Ordering::Relaxed) {
                let _ = client.presence().set(PresenceStatus::Unavailable).await;
            }
        }
        Event::Disconnected(disc) => {
            runtime.connected.store(false, Ordering::SeqCst);
            runtime.emit_json(
                "CONNECTION_STATE",
                json!({
                    "state": "DISCONNECTED",
                    "reason": format!("{:?}", disc.reason),
                    "isLoggedIn": runtime.logged_in.load(Ordering::Relaxed),
                }),
            );
        }
        Event::LoggedOut(out) => {
            runtime.connected.store(false, Ordering::SeqCst);
            runtime.logged_in.store(false, Ordering::SeqCst);
            runtime.terminal_stop.store(true, Ordering::SeqCst);
            runtime.emit_json(
                "CONNECTION_STATE",
                json!({
                    "state": "LOGGED_OUT",
                    "reason": format!("{:?}", out.reason),
                    "isLoggedIn": false,
                }),
            );
        }
        Event::StreamReplaced(_) => {
            runtime.connected.store(false, Ordering::SeqCst);
            runtime.emit_json(
                "CONNECTION_STATE",
                json!({
                    "state": "STREAM_REPLACED",
                    "isLoggedIn": runtime.logged_in.load(Ordering::Relaxed),
                }),
            );
        }
        Event::TemporaryBan(ban) => {
            runtime.connected.store(false, Ordering::SeqCst);
            runtime.terminal_stop.store(true, Ordering::SeqCst);
            let expire_secs = ban.expire.as_secs();
            runtime.emit_log(format!(
                "WhatsApp TemporaryBan ({}, expire={}s): {}",
                ban.code,
                expire_secs,
                ban.message.as_deref().unwrap_or("")
            ));
            runtime.emit_json(
                "CONNECTION_STATE",
                json!({
                    "state": "TEMPORARY_BAN",
                    "reason": format!("{}", ban.code),
                    "expireSecs": expire_secs,
                    "isLoggedIn": runtime.logged_in.load(Ordering::Relaxed),
                }),
            );
        }
        Event::ClientOutdated(_) => {
            runtime.connected.store(false, Ordering::SeqCst);
            runtime.terminal_stop.store(true, Ordering::SeqCst);
            runtime.emit_log("WhatsApp returned 405 ClientOutdated — update whatsapp-rust");
            runtime.emit_json(
                "CONNECTION_STATE",
                json!({
                    "state": "CLIENT_OUTDATED",
                    "reason": "405",
                    "isLoggedIn": runtime.logged_in.load(Ordering::Relaxed),
                }),
            );
        }
        Event::ConnectFailure(cf) => {
            runtime.connected.store(false, Ordering::SeqCst);
            if cf.reason.is_logged_out() {
                runtime.logged_in.store(false, Ordering::SeqCst);
            }
            if !cf.reason.should_reconnect() {
                runtime.terminal_stop.store(true, Ordering::SeqCst);
            }
            let st = if cf.reason.is_logged_out() {
                "LOGGED_OUT"
            } else {
                "CONNECT_FAILURE"
            };
            runtime.emit_json(
                "CONNECTION_STATE",
                json!({
                    "state": st,
                    "reason": format!("{:?}", cf.reason),
                    "message": cf.message.as_deref().unwrap_or(""),
                    "shouldReconnect": cf.reason.should_reconnect(),
                    "isLoggedIn": runtime.logged_in.load(Ordering::Relaxed),
                }),
            );
        }
        Event::StreamError(se) => {
            if se.code == "429" || se.code == "503" {
                let wait_secs = if se.code == "429" { 180 } else { 60 };
                runtime
                    .rate_limit_until_unix
                    .store(now_secs() + wait_secs, Ordering::SeqCst);
                runtime.connected.store(false, Ordering::SeqCst);
                runtime.emit_log(format!(
                    "WhatsApp stream error code={} — backing off {}s",
                    se.code, wait_secs
                ));
                runtime.emit_json(
                    "CONNECTION_STATE",
                    json!({
                        "state": "RATE_LIMITED",
                        "reason": se.code,
                        "backoffSecs": wait_secs,
                        "isLoggedIn": runtime.logged_in.load(Ordering::Relaxed),
                    }),
                );
            }
        }
        Event::Messages(batch) => {
            for inbound in batch.iter() {
                let _ = normalize_message(&runtime, &inbound.info, &inbound.message, false).await;
            }
        }
        Event::HistorySync(lazy) => {
            process_history_sync(runtime, (**lazy).clone()).await;
        }
        Event::Receipt(receipt) => {
            let rtype = match receipt.r#type {
                ReceiptType::Delivered => "DELIVERED",
                ReceiptType::Read => "READ",
                ReceiptType::ReadSelf => "READ_SELF",
                ReceiptType::Played => "PLAYED",
                ReceiptType::PlayedSelf => "PLAYED_SELF",
                ReceiptType::Sender => "SENDER",
                ReceiptType::Retry => "RETRY",
                _ => receipt.r#type.as_wire_str(),
            };
            let ids: Vec<String> = receipt.message_ids.iter().map(ToString::to_string).collect();
            let is_primary_active = matches!(receipt.r#type, ReceiptType::ReadSelf | ReceiptType::PlayedSelf);
            let raw_chat = canonical_jid_str(&receipt.source.chat.to_string());
            let alt_hint = receipt
                .source
                .recipient_alt
                .as_ref()
                .map(|j| canonical_jid_str(&j.to_string()))
                .unwrap_or_default();
            let sender_hint = receipt
                .source
                .sender_alt
                .as_ref()
                .map(|j| canonical_jid_str(&j.to_string()))
                .unwrap_or_default();
            let (chat_jid, alt_chat_jid) = if receipt.source.chat.is_group() || receipt.source.chat.is_status_broadcast() {
                (raw_chat, alt_hint)
            } else {
                resolve_dm_chat_pair_local(&runtime, &raw_chat, &[&alt_hint, &sender_hint]).await
            };
            runtime.emit_json(
                "RECEIPT",
                json!({
                    "chatJid": chat_jid,
                    "altChatJid": alt_chat_jid,
                    "senderJid": canonical_jid_str(&receipt.source.sender.to_string()),
                    "receiptType": rtype,
                    "timestamp": receipt.timestamp.as_millis(),
                    "isPrimaryActive": is_primary_active,
                    "messageIds": ids,
                }),
            );
        }
        Event::Presence(p) => {
            let raw_jid = canonical_jid_str(&p.from.to_string());
            let (jid, alt_jid) = resolve_dm_chat_pair_local(&runtime, &raw_jid, &[]).await;
            runtime.emit_json(
                "PRESENCE",
                json!({
                    "jid": jid,
                    "altJid": alt_jid,
                    "isOnline": p.status == PresenceStatus::Available,
                    "lastSeen": p.last_seen.map(|t| t.as_millis()).unwrap_or(0),
                    "timestamp": now_millis(),
                }),
            );
        }
        Event::ChatPresence(cp) => {
            let (state, media) = match cp.state {
                ChatActivity::Typing => ("composing", ""),
                ChatActivity::RecordingAudio => ("composing", "audio"),
                ChatActivity::Idle | _ => ("paused", ""),
            };
            let raw_chat = canonical_jid_str(&cp.source.chat.to_string());
            let alt_hint = cp
                .source
                .recipient_alt
                .as_ref()
                .map(|j| canonical_jid_str(&j.to_string()))
                .unwrap_or_default();
            let (chat_jid, alt_chat_jid) = if cp.source.chat.is_group() {
                (raw_chat, alt_hint)
            } else {
                resolve_dm_chat_pair_local(&runtime, &raw_chat, &[&alt_hint]).await
            };
            runtime.emit_json(
                "CHAT_PRESENCE",
                json!({
                    "chatJid": chat_jid,
                    "altChatJid": alt_chat_jid,
                    "senderJid": canonical_jid_str(&cp.source.sender.to_string()),
                    "state": state,
                    "media": media,
                }),
            );
        }
        Event::PictureUpdate(pic) => {
            let raw_jid = canonical_jid_str(&pic.jid.to_string());
            let (jid_str, alt_jid) = resolve_dm_chat_pair_local(&runtime, &raw_jid, &[]).await;
            if let Some(shared) = runtime.shared_db_arc() {
                clear_avatar_miss(&shared, &jid_str).await;
            }
            if pic.removed {
                runtime.emit_json(
                    "PROFILE_UPDATE",
                    json!({
                        "jid": jid_str,
                        "altJid": alt_jid,
                        "pictureId": "",
                        "avatarPath": "",
                        "aboutText": "",
                        "removed": true,
                        "timestamp": pic.timestamp.as_millis(),
                    }),
                );
            } else {
                let rt_c = Arc::clone(&runtime);
                let jid_c = pic.jid.clone();
                runtime.rt.spawn(async move {
                    let _ = fetch_and_cache_avatar_if_needed(&rt_c, &jid_c, true).await;
                });
            }
        }
        Event::UserAboutUpdate(about) => {
            let raw_jid = canonical_jid_str(&about.jid.to_string());
            let (jid_str, alt_jid) = resolve_dm_chat_pair_local(&runtime, &raw_jid, &[]).await;
            runtime.emit_json(
                "PROFILE_UPDATE",
                json!({
                    "jid": jid_str,
                    "altJid": alt_jid,
                    "avatarPath": "",
                    "aboutText": about.status,
                    "removed": false,
                    "timestamp": about.timestamp.as_millis(),
                }),
            );
        }
        Event::GroupUpdate(gp) => {
            let g_jid = canonical_jid_str(&gp.group_jid.to_string());
            let ts = gp.timestamp.as_millis();
            match &*gp.action {
                whatsapp_rust::wacore::stanza::groups::GroupNotificationAction::Subject {
                    subject,
                    ..
                } => {
                    runtime.emit_json(
                        "CHAT_META_UPDATE",
                        json!({
                            "kind": "GROUP_NAME",
                            "jid": g_jid,
                            "altJid": "",
                            "name": subject,
                            "timestamp": ts,
                        }),
                    );
                }
                whatsapp_rust::wacore::stanza::groups::GroupNotificationAction::Announce => {
                    runtime.emit_json(
                        "CHAT_META_UPDATE",
                        json!({ "kind": "GROUP_ANNOUNCE", "jid": g_jid, "locked": true, "timestamp": ts }),
                    );
                }
                whatsapp_rust::wacore::stanza::groups::GroupNotificationAction::NotAnnounce => {
                    runtime.emit_json(
                        "CHAT_META_UPDATE",
                        json!({ "kind": "GROUP_ANNOUNCE", "jid": g_jid, "locked": false, "timestamp": ts }),
                    );
                }
                _ => {}
            }
        }
        Event::ContactUpdate(cu) => {
            let mut jid_str = canonical_jid_str(&cu.jid.to_string());
            let full_name = cu.action.full_name.as_deref().unwrap_or("");
            let first_name = cu.action.first_name.as_deref().unwrap_or("");
            let name = if !full_name.is_empty() { full_name } else { first_name };
            let lid_from_action = cu
                .action
                .lid_jid
                .as_deref()
                .map(canonical_jid_str)
                .unwrap_or_default();
            let pn_from_action = cu
                .action
                .pn_jid
                .as_deref()
                .map(canonical_jid_str)
                .unwrap_or_default();
            let mut alt_jid = if jid_str.ends_with("@lid") {
                if !pn_from_action.is_empty() && pn_from_action != jid_str {
                    pn_from_action
                } else if let Ok(Some(entry)) = client.get_lid_pn_entry(&cu.jid).await {
                    if !entry.phone_number.is_empty() {
                        format!("{}@s.whatsapp.net", entry.phone_number)
                    } else {
                        String::new()
                    }
                } else {
                    String::new()
                }
            } else if !lid_from_action.is_empty() && lid_from_action != jid_str {
                lid_from_action
            } else if let Ok(Some(entry)) = client.get_lid_pn_entry(&cu.jid).await {
                if !entry.lid.is_empty() {
                    format!("{}@lid", entry.lid)
                } else {
                    String::new()
                }
            } else {
                String::new()
            };
            if jid_str.ends_with("@lid") && alt_jid.ends_with("@s.whatsapp.net") {
                std::mem::swap(&mut jid_str, &mut alt_jid);
            }
            if let Some(shared) = runtime.shared_db_arc() {
                let lid_col = if alt_jid.ends_with("@lid") {
                    alt_jid.as_str()
                } else if jid_str.ends_with("@lid") {
                    jid_str.as_str()
                } else {
                    ""
                };
                let _ = upsert_contact_meta(&shared, &jid_str, lid_col, name, "", "").await;
            }
            runtime.emit_json(
                "CHAT_META_UPDATE",
                json!({
                    "kind": "CONTACT",
                    "jid": jid_str,
                    "altJid": alt_jid,
                    "name": name,
                    "timestamp": cu.timestamp.as_millis(),
                }),
            );
        }
        Event::PinUpdate(pin) => {
            let alt_jid = resolve_chat_alt_jid(&client, &pin.jid).await;
            runtime.emit_json(
                "CHAT_META_UPDATE",
                json!({
                    "kind": "PIN",
                    "jid": canonical_jid_str(&pin.jid.to_string()),
                    "altJid": alt_jid,
                    "pinned": pin.action.pinned.unwrap_or(false),
                    "timestamp": pin.timestamp.as_millis(),
                }),
            );
        }
        Event::MuteUpdate(mute) => {
            let alt_jid = resolve_chat_alt_jid(&client, &mute.jid).await;
            let muted = mute.action.muted.unwrap_or(false);
            runtime.emit_json(
                "CHAT_META_UPDATE",
                json!({
                    "kind": "MUTE",
                    "jid": canonical_jid_str(&mute.jid.to_string()),
                    "altJid": alt_jid,
                    "muted": muted,
                    "muteEnd": mute.action.mute_end_timestamp.unwrap_or(0) * 1000,
                    "timestamp": mute.timestamp.as_millis(),
                }),
            );
        }
        Event::ArchiveUpdate(arch) => {
            let alt_jid = resolve_chat_alt_jid(&client, &arch.jid).await;
            runtime.emit_json(
                "CHAT_META_UPDATE",
                json!({
                    "kind": "ARCHIVE",
                    "jid": canonical_jid_str(&arch.jid.to_string()),
                    "altJid": alt_jid,
                    "archived": arch.action.archived.unwrap_or(false),
                    "timestamp": arch.timestamp.as_millis(),
                }),
            );
        }
        Event::MarkChatAsReadUpdate(read) => {
            let alt_jid = resolve_chat_alt_jid(&client, &read.jid).await;
            runtime.emit_json(
                "CHAT_META_UPDATE",
                json!({
                    "kind": "MARK_READ",
                    "jid": canonical_jid_str(&read.jid.to_string()),
                    "altJid": alt_jid,
                    "read": read.action.read.unwrap_or(true),
                    "timestamp": read.timestamp.as_millis(),
                }),
            );
        }
        Event::DeleteChatUpdate(del) => {
            let alt_jid = resolve_chat_alt_jid(&client, &del.jid).await;
            runtime.emit_json(
                "CHAT_META_UPDATE",
                json!({
                    "kind": "DELETE_CHAT",
                    "jid": canonical_jid_str(&del.jid.to_string()),
                    "altJid": alt_jid,
                    "timestamp": del.timestamp.as_millis(),
                }),
            );
        }
        Event::ClearChatUpdate(clr) => {
            let alt_jid = resolve_chat_alt_jid(&client, &clr.jid).await;
            runtime.emit_json(
                "CHAT_META_UPDATE",
                json!({
                    "kind": "CLEAR_CHAT",
                    "jid": canonical_jid_str(&clr.jid.to_string()),
                    "altJid": alt_jid,
                    "timestamp": clr.timestamp.as_millis(),
                }),
            );
        }
        Event::FavoriteStickerUpdate(fav) => {
            let fhash = fav
                .action
                .file_enc_sha256
                .as_deref()
                .map(|b| b.iter().map(|x| format!("{x:02x}")).collect::<String>())
                .unwrap_or_else(|| fav.filehash.clone());
            runtime.emit_json(
                "STICKER_ACTION",
                json!({
                    "fileEncSha256": fhash,
                    "isFavorite": fav.action.is_favorite.unwrap_or(false),
                }),
            );
        }
        Event::UndecryptableMessage(und) => {
            let raw_chat = canonical_jid_str(&und.info.source.chat.to_string());
            let alt_hint = und
                .info
                .source
                .recipient_alt
                .as_ref()
                .map(|j| canonical_jid_str(&j.to_string()))
                .unwrap_or_default();
            let sender_hint = und
                .info
                .source
                .sender_alt
                .as_ref()
                .map(|j| canonical_jid_str(&j.to_string()))
                .unwrap_or_default();
            let (chat_jid, alt_chat_jid) = if und.info.source.chat.is_group() || und.info.source.chat.is_status_broadcast() {
                (raw_chat, alt_hint)
            } else {
                resolve_dm_chat_pair_local(&runtime, &raw_chat, &[&alt_hint, &sender_hint]).await
            };
            let sender_jid = canonical_jid_str(&und.info.source.sender.to_string());
            if und.unavailable_type == UnavailableType::ViewOnce {
                runtime.emit_json(
                    "VIEW_ONCE_STRIPPED",
                    json!({
                        "id": und.info.id.to_string(),
                        "chatJid": chat_jid,
                        "altChatJid": alt_chat_jid,
                        "senderJid": sender_jid,
                        "pushName": und.info.push_name.as_str(),
                        "isFromMe": und.info.source.is_from_me,
                        "timestamp": und.info.timestamp.as_millis(),
                        "mediaType": "VIEW_ONCE_STUB",
                        "isUnavailableStub": true,
                        "viewOnceState": "AVAILABLE",
                        "reason": "",
                    }),
                );
            } else if !und.is_unavailable {
                runtime.emit_log(format!(
                    "Undecryptable message {} in {chat_jid} (type={:?})",
                    und.info.id, und.unavailable_type
                ));
                runtime.emit_json(
                    "UNDECRYPTABLE_STUB",
                    json!({
                        "id": und.info.id.to_string(),
                        "chatJid": chat_jid,
                        "altChatJid": alt_chat_jid,
                        "senderJid": sender_jid,
                        "pushName": und.info.push_name.as_str(),
                        "isFromMe": und.info.source.is_from_me,
                        "timestamp": und.info.timestamp.as_millis(),
                    }),
                );
            }
        }
        Event::SelfPushNameUpdated(_) => {
            if runtime.ghost_presence.load(Ordering::Relaxed) {
                let _ = client.presence().set(PresenceStatus::Unavailable).await;
            }
        }
        Event::DeleteMessageForMeUpdate(del) => {
            let target_id = del.message_id.clone();
            if !target_id.is_empty() {
                let raw_chat = canonical_jid_str(&del.chat_jid.to_string());
                let (chat_jid, alt_chat_jid) = if del.chat_jid.is_group() || del.chat_jid.is_status_broadcast() {
                    (raw_chat, String::new())
                } else {
                    resolve_dm_chat_pair_local(&runtime, &raw_chat, &[]).await
                };
                runtime.emit_json(
                    "MESSAGE_REVOKE",
                    json!({
                        "chatJid": chat_jid,
                        "altChatJid": alt_chat_jid,
                        "targetMessageId": target_id,
                        "senderJid": del.participant_jid.as_ref().map(|j| canonical_jid_str(&j.to_string())).unwrap_or_default(),
                        "revokedAt": del.timestamp.as_millis(),
                        "isStatus": false,
                        "isDeleteForMe": true,
                    }),
                );
            }
        }
        Event::DisappearingModeChanged(dm) => {
            let raw_jid = canonical_jid_str(&dm.from.to_string());
            let (jid_s, alt_jid) = resolve_dm_chat_pair_local(&runtime, &raw_jid, &[]).await;
            runtime.set_chat_ephemeral(&jid_s, dm.duration);
            runtime.emit_json(
                "CHAT_META_UPDATE",
                json!({
                    "kind": "EPHEMERAL",
                    "jid": jid_s,
                    "altJid": alt_jid,
                    "ephemeralSec": dm.duration,
                    "timestamp": dm.setting_timestamp.as_millis(),
                }),
            );
        }
        Event::IncomingCall(call) => {
            runtime.voip.on_incoming_call_event(call, &runtime);
        }
        Event::MissedCall(missed) => {
            runtime.voip.on_missed_call(missed, &runtime);
        }
        Event::CallEndedElsewhere(ended) => {
            runtime.voip.on_call_ended_elsewhere(ended, &runtime);
        }
        Event::CallLogSync(sync) => {
            emit_call_log_record(
                &runtime,
                &client,
                &sync.record,
                Some(sync.from_me),
                Some(&sync.call_creator_jid),
                Some(sync.call_id.as_str()),
                sync.timestamp.as_millis(),
                sync.from_full_sync,
            )
            .await;
        }
        Event::CallLogHistory(hist) => {
            let fallback_ms = hist
                .timestamp
                .map(|t| t.as_millis())
                .unwrap_or_else(now_millis);
            emit_call_log_record(
                &runtime,
                &client,
                &hist.record,
                hist.from_me,
                hist.call_creator_jid.as_ref(),
                hist.record.call_id.as_deref(),
                fallback_ms,
                true,
            )
            .await;
        }
        _ => {}
    }
}

async fn emit_call_log_record(
    runtime: &Arc<BridgeRuntime>,
    client: &Arc<Client>,
    record: &wa::CallLogRecord,
    from_me_hint: Option<bool>,
    creator_hint: Option<&Jid>,
    call_id_hint: Option<&str>,
    fallback_ts_ms: i64,
    is_sync: bool,
) {
    let snap = client.persistence_manager().get_device_snapshot();
    let own_pn = snap
        .pn
        .as_ref()
        .map(|j| canonical_jid_str(&j.to_string()))
        .unwrap_or_default();
    let own_lid = snap
        .lid
        .as_ref()
        .map(|j| canonical_jid_str(&j.to_string()))
        .unwrap_or_default();
    let is_self_jid = |j: &str| {
        !j.is_empty()
            && ((!own_pn.is_empty() && j == own_pn) || (!own_lid.is_empty() && j == own_lid))
    };

    let creator_canon = creator_hint
        .map(|j| canonical_jid_str(&j.to_string()))
        .filter(|s| !s.is_empty())
        .or_else(|| {
            record
                .call_creator_jid
                .as_deref()
                .map(canonical_jid_str)
                .filter(|s| !s.is_empty())
        })
        .unwrap_or_default();

    let from_me = from_me_hint.unwrap_or_else(|| is_self_jid(&creator_canon));
    let group_canon = record
        .group_jid
        .as_deref()
        .map(canonical_jid_str)
        .filter(|s| !s.is_empty())
        .unwrap_or_default();

    let peer_from_participants = record
        .participants
        .iter()
        .filter_map(|p| p.user_jid.as_deref())
        .map(canonical_jid_str)
        .find(|s| !s.is_empty() && !is_self_jid(s))
        .unwrap_or_default();

    let chat_jid = if !group_canon.is_empty() {
        group_canon.clone()
    } else if !from_me && !creator_canon.is_empty() && !is_self_jid(&creator_canon) {
        creator_canon.clone()
    } else if !peer_from_participants.is_empty() {
        peer_from_participants
    } else if !creator_canon.is_empty() && !is_self_jid(&creator_canon) {
        creator_canon.clone()
    } else {
        return;
    };

    let alt_chat_jid = if let Ok(parsed) = chat_jid.parse::<Jid>() {
        if parsed.is_lid() || parsed.is_pn() {
            if let Ok(Some(entry)) = client.get_lid_pn_entry(&parsed).await {
                if parsed.is_lid() && !entry.phone_number.is_empty() {
                    format!("{}@s.whatsapp.net", entry.phone_number)
                } else if parsed.is_pn() && !entry.lid.is_empty() {
                    format!("{}@lid", entry.lid)
                } else {
                    String::new()
                }
            } else {
                String::new()
            }
        } else {
            String::new()
        }
    } else {
        String::new()
    };

    let ts_ms = record
        .start_time
        .filter(|&s| s > 0)
        .map(|s| s * 1000)
        .unwrap_or(fallback_ts_ms);
    let call_id_raw = call_id_hint
        .filter(|s| !s.is_empty())
        .or(record.call_id.as_deref())
        .unwrap_or("");
    let msg_id = if !call_id_raw.is_empty() {
        format!("call_{call_id_raw}")
    } else {
        format!("call_{}_{}", chat_jid.split('@').next().unwrap_or("peer"), ts_ms / 1000)
    };
    let sender_jid = if !creator_canon.is_empty() {
        creator_canon
    } else if from_me {
        own_pn.clone()
    } else {
        chat_jid.clone()
    };
    let (body, duration_sec) = format_call_record_body(record, from_me);

    runtime.emit_json(
        "CALL_LOG_UPSERT",
        json!({
            "id": msg_id,
            "chatJid": chat_jid,
            "altChatJid": alt_chat_jid,
            "senderJid": sender_jid,
            "isFromMe": from_me,
            "isGroup": !group_canon.is_empty(),
            "timestamp": ts_ms,
            "body": body,
            "durationSec": duration_sec,
            "msgType": "CALL",
            "isSync": is_sync,
        }),
    );
}

fn pick_individual_sender<'a>(candidates: &[Option<&'a str>]) -> Option<&'a str> {
    candidates.iter().flatten().map(|s| s.trim()).find(|s| {
        !s.is_empty()
            && !s.ends_with("@g.us")
            && !s.ends_with("@broadcast")
            && *s != "status@broadcast"
    })
}

async fn normalize_history_status_item(
    runtime: &Arc<BridgeRuntime>,
    web_msg: &wa::WebMessageInfo,
    lid_to_pn: &HashMap<String, String>,
    pn_to_lid: &HashMap<String, String>,
    cutoff_ms: i64,
    statuses_batch: &mut Vec<Value>,
    revokes_batch: &mut Vec<Value>,
) {
    let Some(key) = web_msg.key.as_option() else { return };
    let msg_id = key.id.as_deref().unwrap_or("");
    if msg_id.is_empty() {
        return;
    }
    let ts_ms = web_msg
        .message_timestamp
        .map(|s| (s as i64) * 1000)
        .unwrap_or(0);
    if ts_ms > 0 && ts_ms < cutoff_ms {
        return;
    }

    let from_me = key.from_me.unwrap_or(false);
    let sender_raw = pick_individual_sender(&[
        key.participant.as_deref(),
        web_msg.participant.as_deref(),
        web_msg.original_self_author_user_jid_string.as_deref(),
    ])
    .unwrap_or(if from_me { "me@s.whatsapp.net" } else { "" });
    if sender_raw.is_empty() {
        return;
    }

    let stub_is_revoke = web_msg.message_stub_type
        == Some(wa::web_message_info::StubType::REVOKE.into())
        || web_msg.message_stub_type
            == Some(wa::web_message_info::StubType::ADMIN_REVOKE.into());
    if stub_is_revoke {
        revokes_batch.push(json!({
            "chatJid": "status@broadcast",
            "altChatJid": "",
            "targetMessageId": msg_id,
            "senderJid": canonical_jid_str(sender_raw),
            "revokedAt": if ts_ms > 0 { ts_ms } else { now_millis() },
            "isStatus": true,
            "isDeleteForMe": false,
        }));
        return;
    }

    let Some(msg_body) = web_msg.message.as_option() else { return };
    let Ok(sender_jid) = canonical_jid_str(sender_raw).parse::<Jid>() else {
        return;
    };
    let Ok(status_chat) = "status@broadcast".parse::<Jid>() else {
        return;
    };

    let sender_canon = canonical_jid_str(&sender_jid.to_string());
    let mut alt_jid_opt: Option<Jid> = if sender_jid.is_lid() {
        lid_to_pn.get(&sender_canon).and_then(|s| s.parse().ok())
    } else if sender_jid.is_pn() {
        pn_to_lid.get(&sender_canon).and_then(|s| s.parse().ok())
    } else {
        None
    };
    if alt_jid_opt.is_none() && (sender_jid.is_lid() || sender_jid.is_pn()) {
        if let Some(client) = runtime.client_arc() {
            if let Ok(Some(entry)) = client.get_lid_pn_entry(&sender_jid).await {
                let alt_s = if sender_jid.is_lid() && !entry.phone_number.is_empty() {
                    format!("{}@s.whatsapp.net", entry.phone_number)
                } else if sender_jid.is_pn() && !entry.lid.is_empty() {
                    format!("{}@lid", entry.lid)
                } else {
                    String::new()
                };
                if !alt_s.is_empty() {
                    alt_jid_opt = alt_s.parse().ok();
                }
            }
        }
    }

    let mut info = whatsapp_rust::types::message::MessageInfo::default();
    info.id = msg_id.to_string().into();
    info.source.chat = status_chat;
    info.source.sender = sender_jid;
    info.source.sender_alt = alt_jid_opt;
    info.source.is_from_me = from_me;
    info.source.is_group = false;
    info.is_status_stanza = true;
    info.push_name = web_msg.push_name.as_deref().unwrap_or("").into();
    if let Some(secs) = web_msg.message_timestamp {
        if let Some(ts) = whatsapp_rust::wacore::time::Timestamp::from_secs(secs as i64) {
            info.timestamp = ts;
        }
    }

    if let NormalizedItem::Message(mut payload) =
        normalize_message(runtime, &info, msg_body, true).await
    {
        let already_viewed = !from_me
            && matches!(
                web_msg.status,
                Some(wa::web_message_info::Status::READ | wa::web_message_info::Status::PLAYED)
            );
        payload["isViewed"] = json!(already_viewed);
        statuses_batch.push(payload);
    }
}

async fn process_history_sync(runtime: Arc<BridgeRuntime>, lazy: LazyHistorySync) {
    let sync_type = format!("{}", lazy.sync_type());
    let progress = lazy.progress().unwrap_or(0);
    let shared = runtime.shared_db_arc();

    let parsed_res = tokio::task::spawn_blocking(move || {
        let mut conversations = Vec::new();
        let mut stream = lazy.stream();
        while let Ok(Some(conv)) = stream.next_conversation() {
            conversations.push(conv);
        }
        let remainder = stream.remainder().ok();
        (conversations, remainder)
    })
    .await;

    let Ok((conversations, remainder)) = parsed_res else {
        return;
    };

    let mut pushnames: Vec<Value> = Vec::new();
    let mut history_pushnames: HashMap<String, String> = HashMap::new();
    let mut rem_status_messages: Vec<wa::WebMessageInfo> = Vec::new();
    let mut lid_to_pn: HashMap<String, String> = HashMap::new();
    let mut pn_to_lid: HashMap<String, String> = HashMap::new();
    if let Some(rem) = remainder {
        for mapping in &rem.phone_number_to_lid_mappings {
            if let (Some(pn), Some(lid)) = (mapping.pn_jid.as_deref(), mapping.lid_jid.as_deref()) {
                let c_pn = canonical_jid_str(pn);
                let c_lid = canonical_jid_str(lid);
                if !c_pn.is_empty() && !c_lid.is_empty() {
                    lid_to_pn.insert(c_lid.clone(), c_pn.clone());
                    pn_to_lid.insert(c_pn, c_lid);
                }
            }
        }
        for p in &rem.pushnames {
            let Some(jid_s) = p.id.as_deref() else { continue };
            let name = p.pushname.as_deref().unwrap_or("").trim();
            if name.is_empty() || !name.chars().any(|c| c.is_alphanumeric()) {
                continue;
            }
            let norm_jid = canonical_jid_str(jid_s);
            let alt_jid = lid_to_pn
                .get(&norm_jid)
                .or_else(|| pn_to_lid.get(&norm_jid))
                .cloned()
                .unwrap_or_default();
            history_pushnames.insert(norm_jid.clone(), name.to_string());
            if !alt_jid.is_empty() {
                history_pushnames.insert(alt_jid.clone(), name.to_string());
            }
            if let Some(ref sdb) = shared {
                let (row_jid, row_lid) = if norm_jid.ends_with("@lid") {
                    (
                        if alt_jid.ends_with("@s.whatsapp.net") { alt_jid.as_str() } else { norm_jid.as_str() },
                        norm_jid.as_str(),
                    )
                } else {
                    (
                        norm_jid.as_str(),
                        if alt_jid.ends_with("@lid") { alt_jid.as_str() } else { "" },
                    )
                };
                let _ = upsert_contact_meta(sdb, row_jid, row_lid, "", name, "").await;
            }
            pushnames.push(json!({
                "jid": norm_jid,
                "altJid": alt_jid,
                "name": name,
            }));
        }
        rem_status_messages = rem.status_v3_messages;
    }

    const BATCH_LIMIT: usize = 250;
    let status_cutoff_ms = now_millis() - 86_400_000;
    let mut chats_batch: Vec<Value> = Vec::new();
    let mut msgs_batch: Vec<Value> = Vec::new();
    let mut statuses_batch: Vec<Value> = Vec::new();
    let mut rx_batch: Vec<Value> = Vec::new();
    let mut pins_batch: Vec<Value> = Vec::new();
    let mut revokes_batch: Vec<Value> = Vec::new();

    for conv in conversations {
        let chat_jid = canonical_jid_str(&conv.id);
        let Ok(parsed_chat_jid) = chat_jid.parse::<Jid>() else {
            continue;
        };
        if parsed_chat_jid.is_status_broadcast() {
            for hmsg in conv.messages {
                let Some(web_msg) = hmsg.message.into_option() else { continue };
                normalize_history_status_item(
                    &runtime,
                    &web_msg,
                    &lid_to_pn,
                    &pn_to_lid,
                    status_cutoff_ms,
                    &mut statuses_batch,
                    &mut revokes_batch,
                )
                .await;
            }
            continue;
        }
        if parsed_chat_jid.is_broadcast_list() {
            continue;
        }
        let conv_eph = conv.ephemeral_expiration.unwrap_or(0);
        if conv_eph > 0 {
            runtime.set_chat_ephemeral(&chat_jid, conv_eph);
        }
        let name = conv
            .name
            .as_deref()
            .or(conv.display_name.as_deref())
            .unwrap_or("")
            .to_string();

        let conv_pn = conv
            .pn_jid
            .as_deref()
            .map(canonical_jid_str)
            .filter(|s| s.ends_with("@s.whatsapp.net"))
            .unwrap_or_default();
        let conv_lid = conv
            .lid_jid
            .as_deref()
            .map(canonical_jid_str)
            .filter(|s| s.ends_with("@lid"))
            .unwrap_or_default();
        if !conv_pn.is_empty() && !conv_lid.is_empty() {
            lid_to_pn.entry(conv_lid.clone()).or_insert_with(|| conv_pn.clone());
            pn_to_lid.entry(conv_pn.clone()).or_insert_with(|| conv_lid.clone());
        }

        let alt_chat_jid = if parsed_chat_jid.is_lid() {
            if !conv_pn.is_empty() {
                conv_pn.clone()
            } else if let Some(pn) = lid_to_pn.get(&chat_jid) {
                pn.clone()
            } else if let Some(client) = runtime.client_arc() {
                client
                    .get_lid_pn_entry(&parsed_chat_jid)
                    .await
                    .ok()
                    .flatten()
                    .filter(|e| !e.phone_number.is_empty())
                    .map(|e| format!("{}@s.whatsapp.net", e.phone_number))
                    .unwrap_or_default()
            } else {
                String::new()
            }
        } else if parsed_chat_jid.is_pn() {
            if !conv_lid.is_empty() {
                conv_lid.clone()
            } else if let Some(lid) = pn_to_lid.get(&chat_jid) {
                lid.clone()
            } else if let Some(client) = runtime.client_arc() {
                client
                    .get_lid_pn_entry(&parsed_chat_jid)
                    .await
                    .ok()
                    .flatten()
                    .filter(|e| !e.lid.is_empty())
                    .map(|e| format!("{}@lid", e.lid))
                    .unwrap_or_default()
            } else {
                String::new()
            }
        } else {
            String::new()
        };

        chats_batch.push(json!({
            "jid": chat_jid,
            "altJid": alt_chat_jid,
            "name": name,
            "isGroup": parsed_chat_jid.is_group(),
            "isCommunityParent": conv.is_parent_group.unwrap_or(false),
            "parentGroupJid": conv.parent_group_id.as_deref().unwrap_or(""),
            "isDefaultSubgroup": conv.is_default_subgroup.unwrap_or(false),
            "unreadCount": conv.unread_count,
            "archived": conv.archived.unwrap_or(false),
            "locked": conv.locked.unwrap_or(false),
            "pinned": conv.pinned.unwrap_or(0),
            "muteEndTime": conv.mute_end_time.unwrap_or(0),
            "lastTimestampMs": conv.conversation_timestamp.map(|t| (t as i64) * 1000).unwrap_or(0),
            "ephemeralSec": conv_eph,
        }));

        for hmsg in conv.messages {
            let Some(web_msg) = hmsg.message.into_option() else { continue };
            let Some(key) = web_msg.key.as_option() else { continue };
            let msg_id = key.id.as_deref().unwrap_or("").to_string();
            if msg_id.is_empty() {
                continue;
            }
            let stub_is_revoke = web_msg.message_stub_type
                == Some(wa::web_message_info::StubType::REVOKE.into())
                || web_msg.message_stub_type
                    == Some(wa::web_message_info::StubType::ADMIN_REVOKE.into());
            if stub_is_revoke {
                let ts_ms = web_msg
                    .message_timestamp
                    .map(|s| (s as i64) * 1000)
                    .unwrap_or_else(now_millis);
                revokes_batch.push(json!({
                    "chatJid": chat_jid,
                    "altChatJid": alt_chat_jid,
                    "targetMessageId": msg_id,
                    "senderJid": key.participant.as_deref().map(canonical_jid_str).unwrap_or_default(),
                    "revokedAt": ts_ms,
                    "isStatus": false,
                    "isDeleteForMe": false,
                }));
                continue;
            }
            if let Some(call_stub_body) = match web_msg.message_stub_type {
                Some(t) if t == wa::web_message_info::StubType::CALL_MISSED_VOICE.into() => {
                    Some("Missed voice call|Tap to call back")
                }
                Some(t) if t == wa::web_message_info::StubType::CALL_MISSED_VIDEO.into() => {
                    Some("Missed video call|Tap to call back")
                }
                Some(t) if t == wa::web_message_info::StubType::CALL_MISSED_GROUP_VOICE.into() => {
                    Some("Missed group voice call|Tap to join")
                }
                Some(t) if t == wa::web_message_info::StubType::CALL_MISSED_GROUP_VIDEO.into() => {
                    Some("Missed group video call|Tap to join")
                }
                Some(t)
                    if t == wa::web_message_info::StubType::SILENCED_UNKNOWN_CALLER_AUDIO.into() =>
                {
                    Some("Silenced voice call|Unknown caller")
                }
                Some(t)
                    if t == wa::web_message_info::StubType::SILENCED_UNKNOWN_CALLER_VIDEO.into() =>
                {
                    Some("Silenced video call|Unknown caller")
                }
                _ => None,
            } {
                let ts_ms = web_msg
                    .message_timestamp
                    .map(|s| (s as i64) * 1000)
                    .unwrap_or_else(now_millis);
                let from_me = key.from_me.unwrap_or(false);
                let call_sender = pick_individual_sender(&[
                    key.participant.as_deref(),
                    web_msg.participant.as_deref(),
                    web_msg.original_self_author_user_jid_string.as_deref(),
                ])
                .map(canonical_jid_str)
                .unwrap_or_else(|| if parsed_chat_jid.is_group() { String::new() } else { chat_jid.clone() });
                msgs_batch.push(json!({
                    "id": msg_id,
                    "chatJid": chat_jid,
                    "altChatJid": alt_chat_jid,
                    "senderJid": call_sender,
                    "altSenderJid": "",
                    "senderName": web_msg.push_name.as_deref().unwrap_or(""),
                    "isFromMe": from_me,
                    "isGroup": parsed_chat_jid.is_group(),
                    "timestamp": ts_ms,
                    "body": call_stub_body,
                    "caption": "",
                    "msgType": "CALL",
                    "mimeType": "",
                    "fileName": "",
                    "mediaPath": "",
                    "durationSec": 0,
                    "fileSize": 0,
                    "waveformCsv": "",
                    "deliveryStatus": if from_me { "SENT" } else { "READ" },
                    "isViewOnce": false,
                    "viewOnceState": "NONE",
                    "isEphemeral": false,
                    "ephemeralDuration": 0,
                    "quotedId": "",
                    "quotedSender": "",
                    "quotedBody": "",
                    "mentionsCsv": "",
                    "reactionSummary": "",
                    "isSync": true,
                }));
                continue;
            }
            let Some(msg_body) = web_msg.message.as_option() else { continue };
            let from_me = key.from_me.unwrap_or(false);
            let sender_raw = pick_individual_sender(&[
                key.participant.as_deref(),
                web_msg.participant.as_deref(),
                web_msg.original_self_author_user_jid_string.as_deref(),
            ])
            .or_else(|| if !from_me && !parsed_chat_jid.is_group() { Some(chat_jid.as_str()) } else { None })
            .unwrap_or("");
            let sender_jid = sender_raw
                .parse::<Jid>()
                .unwrap_or_default();
            let sender_canon = if sender_raw.is_empty() {
                String::new()
            } else {
                canonical_jid_str(&sender_jid.to_string())
            };
            let mut sender_alt_opt: Option<Jid> = if sender_canon.is_empty() {
                None
            } else if sender_jid.is_lid() {
                lid_to_pn.get(&sender_canon).and_then(|s| s.parse().ok())
            } else if sender_jid.is_pn() {
                pn_to_lid.get(&sender_canon).and_then(|s| s.parse().ok())
            } else {
                None
            };
            if sender_alt_opt.is_none() && (sender_jid.is_lid() || sender_jid.is_pn()) {
                if let Some(client) = runtime.client_arc() {
                    if let Ok(Some(entry)) = client.get_lid_pn_entry(&sender_jid).await {
                        let alt_s = if sender_jid.is_lid() && !entry.phone_number.is_empty() {
                            format!("{}@s.whatsapp.net", entry.phone_number)
                        } else if sender_jid.is_pn() && !entry.lid.is_empty() {
                            format!("{}@lid", entry.lid)
                        } else {
                            String::new()
                        };
                        if !alt_s.is_empty() {
                            sender_alt_opt = alt_s.parse().ok();
                        }
                    }
                }
            }
            let alt_sender_canon = sender_alt_opt
                .as_ref()
                .map(|j| canonical_jid_str(&j.to_string()))
                .unwrap_or_default();
            let raw_push = web_msg
                .push_name
                .as_deref()
                .map(str::trim)
                .filter(|s| !s.is_empty())
                .or_else(|| web_msg.verified_biz_name.as_deref().map(str::trim))
                .unwrap_or("");
            let usable_web_push = (!raw_push.is_empty() && raw_push.chars().any(|c| c.is_alphanumeric()))
                .then_some(raw_push);
            if let (Some(p), false) = (usable_web_push, sender_canon.is_empty()) {
                history_pushnames.entry(sender_canon.clone()).or_insert_with(|| p.to_string());
                if !alt_sender_canon.is_empty() {
                    history_pushnames.entry(alt_sender_canon.clone()).or_insert_with(|| p.to_string());
                }
            }
            let resolved_push: String = if let Some(p) = usable_web_push {
                p.to_string()
            } else if !from_me && !sender_canon.is_empty() {
                let cached_hist = history_pushnames
                    .get(&sender_canon)
                    .or_else(|| history_pushnames.get(&alt_sender_canon))
                    .cloned();
                if let Some(name) = cached_hist {
                    name
                } else if let Some(ref sdb) = shared {
                    let meta = match get_contact_meta(sdb, &sender_canon).await {
                        Some(m) => Some(m),
                        None if !alt_sender_canon.is_empty() => get_contact_meta(sdb, &alt_sender_canon).await,
                        None => None,
                    };
                    meta.map(|m| {
                        if !m.full_name.is_empty() {
                            m.full_name
                        } else if !m.business_name.is_empty() {
                            m.business_name
                        } else {
                            m.push_name
                        }
                    })
                    .unwrap_or_default()
                } else {
                    String::new()
                }
            } else {
                String::new()
            };

            let mut info = whatsapp_rust::types::message::MessageInfo::default();
            info.id = msg_id.into();
            info.source.chat = parsed_chat_jid.clone();
            if !alt_chat_jid.is_empty() {
                info.source.recipient_alt = alt_chat_jid.parse().ok();
            }
            info.source.sender = sender_jid;
            info.source.sender_alt = sender_alt_opt;
            info.source.is_from_me = from_me;
            info.source.is_group = parsed_chat_jid.is_group();
            info.push_name = resolved_push.into();
            if let Some(secs) = web_msg.message_timestamp {
                if let Some(ts) = whatsapp_rust::wacore::time::Timestamp::from_secs(secs as i64) {
                    info.timestamp = ts;
                }
            }

            match normalize_message(&runtime, &info, msg_body, true).await {
                NormalizedItem::Message(m) => msgs_batch.push(m),
                NormalizedItem::Reaction(r) => rx_batch.push(r),
                NormalizedItem::Pin(p) => pins_batch.push(p),
                NormalizedItem::Revoke(rev) => revokes_batch.push(rev),
                NormalizedItem::None => {}
            }

            if msgs_batch.len() + statuses_batch.len() >= BATCH_LIMIT {
                runtime.emit_json(
                    "HISTORY_SYNC_BATCH",
                    json!({
                        "syncType": sync_type,
                        "progress": progress,
                        "pushnames": std::mem::take(&mut pushnames),
                        "chats": std::mem::take(&mut chats_batch),
                        "messages": std::mem::take(&mut msgs_batch),
                        "statuses": std::mem::take(&mut statuses_batch),
                        "reactions": std::mem::take(&mut rx_batch),
                        "pins": std::mem::take(&mut pins_batch),
                        "revokes": std::mem::take(&mut revokes_batch),
                        "isFinal": false,
                    }),
                );
            }
        }
    }

    for web_msg in rem_status_messages {
        normalize_history_status_item(
            &runtime,
            &web_msg,
            &lid_to_pn,
            &pn_to_lid,
            status_cutoff_ms,
            &mut statuses_batch,
            &mut revokes_batch,
        )
        .await;
    }

    runtime.emit_json(
        "HISTORY_SYNC_BATCH",
        json!({
            "syncType": sync_type,
            "progress": progress,
            "pushnames": pushnames,
            "chats": chats_batch,
            "messages": msgs_batch,
            "statuses": statuses_batch,
            "reactions": rx_batch,
            "pins": pins_batch,
            "revokes": revokes_batch,
            "isFinal": true,
        }),
    );
}
