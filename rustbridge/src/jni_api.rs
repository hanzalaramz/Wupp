use std::sync::atomic::Ordering;
use std::sync::Arc;

use jni::objects::{JByteArray, JClass, JObject, JShortArray, JString};
use jni::sys::{jboolean, jbyteArray, jdouble, jint, jlong, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;

use crate::client;
use crate::contacts_groups;
use crate::jid_helpers::parse_jid_flexible;
use crate::messaging;
use crate::meta_db::has_media_meta;
use crate::runtime::BridgeRuntime;

fn jstr(env: &mut JNIEnv, s: &JString) -> String {
    env.get_string(s)
        .map(|js| js.into())
        .unwrap_or_default()
}

fn new_jstr(env: &mut JNIEnv, s: &str) -> jstring {
    env.new_string(s)
        .map(|js| js.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

fn throw_rt(env: &mut JNIEnv, msg: &str) {
    let _ = env.throw_new("java/lang/RuntimeException", msg);
}

fn get_rt(env: &mut JNIEnv) -> Option<Arc<BridgeRuntime>> {
    match BridgeRuntime::get() {
        Some(rt) => Some(rt),
        None => {
            throw_rt(env, "Native rustbridge not initialized (call initClient first)");
            None
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeInitClient<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    files_dir: JString<'a>,
    callback: JObject<'a>,
) {
    crate::init_android_logger();
    let dir = jstr(&mut env, &files_dir);
    let Ok(jvm) = env.get_java_vm() else {
        throw_rt(&mut env, "failed to get JavaVM");
        return;
    };
    let Ok(cb_ref) = env.new_global_ref(callback) else {
        throw_rt(&mut env, "failed to retain callback GlobalRef");
        return;
    };

    let rt = match BridgeRuntime::init_global(jvm, cb_ref) {
        Ok(rt) => rt,
        Err(e) => {
            throw_rt(&mut env, &e);
            return;
        }
    };

    if let Err(e) = rt.rt.block_on(client::init_client(Arc::clone(&rt), dir)) {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSetGhostPresence<'a>(
    _env: JNIEnv<'a>,
    _cls: JClass<'a>,
    enabled: jboolean,
) {
    let Some(rt) = BridgeRuntime::get() else { return };
    rt.rt
        .block_on(client::set_ghost_presence(&rt, enabled != JNI_FALSE));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeConnectClient<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
) {
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt.rt.block_on(client::connect_client(&rt)) {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeDisconnectClient<'a>(
    _env: JNIEnv<'a>,
    _cls: JClass<'a>,
) {
    let Some(rt) = BridgeRuntime::get() else { return };
    rt.rt.block_on(client::disconnect_client(&rt));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeShutdownClient<'a>(
    _env: JNIEnv<'a>,
    _cls: JClass<'a>,
) {
    let Some(rt) = BridgeRuntime::get() else { return };
    rt.rt.block_on(client::shutdown_client(&rt));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeResetSocket<'a>(
    _env: JNIEnv<'a>,
    _cls: JClass<'a>,
) {
    let Some(rt) = BridgeRuntime::get() else { return };
    rt.rt.block_on(client::reset_socket(&rt));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeIsLoggedIn<'a>(
    _env: JNIEnv<'a>,
    _cls: JClass<'a>,
) -> jboolean {
    let Some(rt) = BridgeRuntime::get() else { return JNI_FALSE };
    if client::is_logged_in(&rt) { JNI_TRUE } else { JNI_FALSE }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeIsConnected<'a>(
    _env: JNIEnv<'a>,
    _cls: JClass<'a>,
) -> jboolean {
    let Some(rt) = BridgeRuntime::get() else { return JNI_FALSE };
    if client::is_connected(&rt) { JNI_TRUE } else { JNI_FALSE }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeGetOwnJID<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
) -> jstring {
    let s = BridgeRuntime::get()
        .map(|rt| client::get_own_jid(&rt))
        .unwrap_or_default();
    new_jstr(&mut env, &s)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativePairWithPhoneNumber<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    phone: JString<'a>,
) -> jstring {
    let p = jstr(&mut env, &phone);
    let Some(rt) = get_rt(&mut env) else {
        return std::ptr::null_mut();
    };
    match rt.rt.block_on(client::pair_with_phone_number(&rt, &p)) {
        Ok(code) => new_jstr(&mut env, &code),
        Err(e) => {
            throw_rt(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSendTextMessage<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    chat_jid: JString<'a>,
    text: JString<'a>,
    quoted_id: JString<'a>,
    quoted_sender: JString<'a>,
    quoted_body: JString<'a>,
    mentions_csv: JString<'a>,
    ghost_typing: jboolean,
) -> jstring {
    let c = jstr(&mut env, &chat_jid);
    let t = jstr(&mut env, &text);
    let qid = jstr(&mut env, &quoted_id);
    let qs = jstr(&mut env, &quoted_sender);
    let qb = jstr(&mut env, &quoted_body);
    let mc = jstr(&mut env, &mentions_csv);
    let Some(rt) = get_rt(&mut env) else {
        return std::ptr::null_mut();
    };
    match rt.rt.block_on(messaging::send_text_message(
        &rt,
        &c,
        &t,
        &qid,
        &qs,
        &qb,
        &mc,
        ghost_typing != JNI_FALSE,
    )) {
        Ok(id) => new_jstr(&mut env, &id),
        Err(e) => {
            throw_rt(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSendContactMessage<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    chat_jid: JString<'a>,
    display_name: JString<'a>,
    vcard: JString<'a>,
    quoted_id: JString<'a>,
    quoted_sender: JString<'a>,
    quoted_body: JString<'a>,
) -> jstring {
    let c = jstr(&mut env, &chat_jid);
    let dn = jstr(&mut env, &display_name);
    let vc = jstr(&mut env, &vcard);
    let qid = jstr(&mut env, &quoted_id);
    let qs = jstr(&mut env, &quoted_sender);
    let qb = jstr(&mut env, &quoted_body);
    let Some(rt) = get_rt(&mut env) else {
        return std::ptr::null_mut();
    };
    match rt.rt.block_on(messaging::send_contact_message(
        &rt,
        &c,
        &dn,
        &vc,
        &qid,
        &qs,
        &qb,
    )) {
        Ok(id) => new_jstr(&mut env, &id),
        Err(e) => {
            throw_rt(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSendLocationMessage<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    chat_jid: JString<'a>,
    latitude: jdouble,
    longitude: jdouble,
    name: JString<'a>,
    address: JString<'a>,
    url: JString<'a>,
    quoted_id: JString<'a>,
    quoted_sender: JString<'a>,
    quoted_body: JString<'a>,
) -> jstring {
    let c = jstr(&mut env, &chat_jid);
    let nm = jstr(&mut env, &name);
    let addr = jstr(&mut env, &address);
    let u = jstr(&mut env, &url);
    let qid = jstr(&mut env, &quoted_id);
    let qs = jstr(&mut env, &quoted_sender);
    let qb = jstr(&mut env, &quoted_body);
    let Some(rt) = get_rt(&mut env) else {
        return std::ptr::null_mut();
    };
    match rt.rt.block_on(messaging::send_location_message(
        &rt,
        &c,
        latitude as f64,
        longitude as f64,
        &nm,
        &addr,
        &u,
        &qid,
        &qs,
        &qb,
    )) {
        Ok(id) => new_jstr(&mut env, &id),
        Err(e) => {
            throw_rt(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSendMediaMessage<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    chat_jid: JString<'a>,
    file_path: JString<'a>,
    mime_type: JString<'a>,
    caption: JString<'a>,
    quality_mode: JString<'a>,
    is_voice_note: jboolean,
    duration_sec: jint,
    waveform_csv: JString<'a>,
) -> jstring {
    let c = jstr(&mut env, &chat_jid);
    let fp = jstr(&mut env, &file_path);
    let mime = jstr(&mut env, &mime_type);
    let cap = jstr(&mut env, &caption);
    let qmode = jstr(&mut env, &quality_mode);
    let wf = jstr(&mut env, &waveform_csv);
    let Some(rt) = get_rt(&mut env) else {
        return std::ptr::null_mut();
    };
    match rt.rt.block_on(messaging::send_media_message(
        &rt,
        &c,
        &fp,
        &mime,
        &cap,
        &qmode,
        is_voice_note != JNI_FALSE,
        duration_sec,
        &wf,
        false,
    )) {
        Ok(id) => new_jstr(&mut env, &id),
        Err(e) => {
            throw_rt(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSendGifMessage<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    chat_jid: JString<'a>,
    file_path: JString<'a>,
    caption: JString<'a>,
    duration_sec: jint,
) -> jstring {
    let c = jstr(&mut env, &chat_jid);
    let fp = jstr(&mut env, &file_path);
    let cap = jstr(&mut env, &caption);
    let Some(rt) = get_rt(&mut env) else {
        return std::ptr::null_mut();
    };
    match rt.rt.block_on(messaging::send_media_message(
        &rt,
        &c,
        &fp,
        "video/mp4",
        &cap,
        "gif",
        false,
        duration_sec,
        "",
        true,
    )) {
        Ok(id) => new_jstr(&mut env, &id),
        Err(e) => {
            throw_rt(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeEditMessage<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    chat_jid: JString<'a>,
    msg_id: JString<'a>,
    new_text: JString<'a>,
) {
    let c = jstr(&mut env, &chat_jid);
    let mid = jstr(&mut env, &msg_id);
    let nt = jstr(&mut env, &new_text);
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt.rt.block_on(messaging::edit_message(&rt, &c, &mid, &nt)) {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeRevokeMessage<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    chat_jid: JString<'a>,
    msg_id: JString<'a>,
) {
    let c = jstr(&mut env, &chat_jid);
    let mid = jstr(&mut env, &msg_id);
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt.rt.block_on(messaging::revoke_message(&rt, &c, &mid)) {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeDeleteChatRemote<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    chat_jid: JString<'a>,
    last_msg_id: JString<'a>,
    last_from_me: jboolean,
    _last_participant: JString<'a>,
    last_ts_ms: jlong,
    delete_media: jboolean,
) {
    let c = jstr(&mut env, &chat_jid);
    let mid = jstr(&mut env, &last_msg_id);
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt.rt.block_on(messaging::delete_chat_remote(
        &rt,
        &c,
        &mid,
        last_from_me != JNI_FALSE,
        last_ts_ms,
        delete_media != JNI_FALSE,
    )) {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativePinChat<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    chat_jid: JString<'a>,
    pinned: jboolean,
) {
    let c = jstr(&mut env, &chat_jid);
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt
        .rt
        .block_on(messaging::set_chat_pin_remote(&rt, &c, pinned != JNI_FALSE))
    {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeMuteChat<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    chat_jid: JString<'a>,
    muted: jboolean,
) {
    let c = jstr(&mut env, &chat_jid);
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt
        .rt
        .block_on(messaging::set_chat_mute_remote(&rt, &c, muted != JNI_FALSE))
    {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeArchiveChat<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    chat_jid: JString<'a>,
    archived: jboolean,
) {
    let c = jstr(&mut env, &chat_jid);
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt
        .rt
        .block_on(messaging::set_chat_archive_remote(&rt, &c, archived != JNI_FALSE))
    {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeMarkReadManual<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    chat_jid: JString<'a>,
    sender_jid: JString<'a>,
    msg_ids_csv: JString<'a>,
) {
    let c = jstr(&mut env, &chat_jid);
    let s = jstr(&mut env, &sender_jid);
    let m = jstr(&mut env, &msg_ids_csv);
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt.rt.block_on(messaging::mark_read_manual(&rt, &c, &s, &m)) {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSendPlayedReceiptManual<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    chat_jid: JString<'a>,
    sender_jid: JString<'a>,
    msg_id: JString<'a>,
) {
    let c = jstr(&mut env, &chat_jid);
    let s = jstr(&mut env, &sender_jid);
    let m = jstr(&mut env, &msg_id);
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt.rt.block_on(messaging::send_played_receipt_manual(&rt, &c, &s, &m)) {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSendChatPresenceManual<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    chat_jid: JString<'a>,
    state: JString<'a>,
    media: JString<'a>,
    blocked_by_ghost: jboolean,
) {
    let c = jstr(&mut env, &chat_jid);
    let st = jstr(&mut env, &state);
    let md = jstr(&mut env, &media);
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt.rt.block_on(messaging::send_chat_presence_manual(
        &rt,
        &c,
        &st,
        &md,
        blocked_by_ghost != JNI_FALSE,
    )) {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSubscribeContactPresence<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    jid_str: JString<'a>,
) {
    let js = jstr(&mut env, &jid_str);
    let Some(rt) = get_rt(&mut env) else { return };
    let res = rt.rt.block_on(async {
        let client = rt
            .client_arc()
            .ok_or_else(|| "client not connected".to_string())?;
        let jid = parse_jid_flexible(&js)?;
        client
            .presence()
            .subscribe(jid)
            .await
            .map_err(|e| format!("subscribe presence failed: {e}"))
    });
    if let Err(e) = res {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeResolveParticipantNamesJson<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    jids_csv: JString<'a>,
) -> jstring {
    let csv = jstr(&mut env, &jids_csv);
    let out = match BridgeRuntime::get() {
        Some(rt) => rt.rt.block_on(contacts_groups::resolve_participant_names_json(&rt, &csv)),
        None => "{}".to_string(),
    };
    new_jstr(&mut env, &out)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeResolveLocalDmAliasesJson<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    jids_csv: JString<'a>,
) -> jstring {
    let csv = jstr(&mut env, &jids_csv);
    let out = match BridgeRuntime::get() {
        Some(rt) => rt.rt.block_on(contacts_groups::resolve_local_dm_aliases_json(&rt, &csv)),
        None => "{}".to_string(),
    };
    new_jstr(&mut env, &out)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeGetGroupParticipantsJson<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    group_jid: JString<'a>,
) -> jstring {
    let gj = jstr(&mut env, &group_jid);
    let out = match BridgeRuntime::get() {
        Some(rt) => rt.rt.block_on(contacts_groups::get_group_participants_json(&rt, &gj)),
        None => "[]".to_string(),
    };
    new_jstr(&mut env, &out)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeHasCachedMediaMeta<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    msg_id: JString<'a>,
) -> jboolean {
    let mid = jstr(&mut env, &msg_id);
    let Some(rt) = BridgeRuntime::get() else { return JNI_FALSE };
    let Some(shared) = rt.shared_db_arc() else { return JNI_FALSE };
    if rt.rt.block_on(has_media_meta(&shared, &mid)) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeRequestMediaDownload<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    chat_jid: JString<'a>,
    sender_jid: JString<'a>,
    msg_id: JString<'a>,
    ts_ms: jlong,
) {
    let c = jstr(&mut env, &chat_jid);
    let s = jstr(&mut env, &sender_jid);
    let mid = jstr(&mut env, &msg_id);
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt
        .rt
        .block_on(messaging::request_media_download(&rt, &c, &s, &mid, ts_ms as i64))
    {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeRequestGroupHistoryBackfill<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    chat_jid: JString<'a>,
    anchor_id: JString<'a>,
    anchor_from_me: jboolean,
    anchor_ts_ms: jlong,
) {
    let c = jstr(&mut env, &chat_jid);
    let aid = jstr(&mut env, &anchor_id);
    let Some(rt) = get_rt(&mut env) else { return };
    let rt_clone = Arc::clone(&rt);
    rt.rt.spawn(messaging::request_group_history_backfill(
        rt_clone,
        c,
        aid,
        anchor_from_me != JNI_FALSE,
        anchor_ts_ms as i64,
    ));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSetAutoDownloadPolicy<'a>(
    _env: JNIEnv<'a>,
    _cls: JClass<'a>,
    photo: jboolean,
    audio: jboolean,
    video: jboolean,
    doc: jboolean,
) {
    let Some(rt) = BridgeRuntime::get() else { return };
    rt.allow_photo.store(photo != JNI_FALSE, Ordering::Relaxed);
    rt.allow_audio.store(audio != JNI_FALSE, Ordering::Relaxed);
    rt.allow_video.store(video != JNI_FALSE, Ordering::Relaxed);
    rt.allow_doc.store(doc != JNI_FALSE, Ordering::Relaxed);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSetChatAutoDownload<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    chat_jid: JString<'a>,
    mask: jint,
) {
    let jid = jstr(&mut env, &chat_jid);
    let Some(rt) = BridgeRuntime::get() else { return };
    rt.set_chat_auto_download(&jid, mask);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSetPerChatAutoDownloadJson<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    json_str: JString<'a>,
) {
    let s = jstr(&mut env, &json_str);
    let Some(rt) = BridgeRuntime::get() else { return };
    rt.set_per_chat_auto_download_json(&s);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeVerifyNumbersOnWhatsApp<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    phones_csv: JString<'a>,
) -> jstring {
    let csv = jstr(&mut env, &phones_csv);
    let Some(rt) = get_rt(&mut env) else {
        return std::ptr::null_mut();
    };
    match rt.rt.block_on(contacts_groups::verify_numbers_on_whatsapp(&rt, &csv)) {
        Ok(s) => new_jstr(&mut env, &s),
        Err(e) => {
            throw_rt(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSyncContactsAndAvatars<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    filter_csv: JString<'a>,
) {
    let csv = jstr(&mut env, &filter_csv);
    let Some(rt) = BridgeRuntime::get() else { return };
    let rt_sync = Arc::clone(&rt);
    rt.rt.spawn(async move {
        contacts_groups::sync_contacts_and_avatars(rt_sync, &csv).await;
    });
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeFetchNewsletterMessages<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    nl_jid: JString<'a>,
) {
    let nj = jstr(&mut env, &nl_jid);
    let Some(rt) = BridgeRuntime::get() else { return };
    let rt_nl = Arc::clone(&rt);
    rt.rt.spawn(async move {
        contacts_groups::fetch_newsletter_messages(rt_nl, &nj).await;
    });
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeFetchGroupLockState<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    group_jid: JString<'a>,
) -> jboolean {
    let gj = jstr(&mut env, &group_jid);
    let Some(rt) = get_rt(&mut env) else { return JNI_FALSE };
    match rt.rt.block_on(contacts_groups::fetch_group_lock_state(&rt, &gj)) {
        Ok(locked) => if locked { JNI_TRUE } else { JNI_FALSE },
        Err(e) => {
            throw_rt(&mut env, &e);
            JNI_FALSE
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeGetGroupInfoJson<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    group_jid: JString<'a>,
) -> jstring {
    let gj = jstr(&mut env, &group_jid);
    let Some(rt) = get_rt(&mut env) else {
        return std::ptr::null_mut();
    };
    match rt.rt.block_on(contacts_groups::get_group_info_json(&rt, &gj)) {
        Ok(s) => new_jstr(&mut env, &s),
        Err(e) => {
            throw_rt(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeFetchFullProfilePicture<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    jid_str: JString<'a>,
) -> jstring {
    let js = jstr(&mut env, &jid_str);
    let Some(rt) = get_rt(&mut env) else {
        return std::ptr::null_mut();
    };
    let res = rt.rt.block_on(async {
        let jid = parse_jid_flexible(&js)?;
        contacts_groups::fetch_and_cache_avatar_if_needed(&rt, &jid, true).await
    });
    match res {
        Ok(path) => new_jstr(&mut env, &path),
        Err(e) => {
            throw_rt(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeToggleNewsletterMute<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    nl_jid: JString<'a>,
    muted: jboolean,
) {
    let nj = jstr(&mut env, &nl_jid);
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt
        .rt
        .block_on(contacts_groups::toggle_newsletter_mute(&rt, &nj, muted != JNI_FALSE))
    {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSendReaction<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    chat_jid: JString<'a>,
    target_id: JString<'a>,
    sender_jid: JString<'a>,
    from_me: jboolean,
    emoji: JString<'a>,
) {
    let c = jstr(&mut env, &chat_jid);
    let tid = jstr(&mut env, &target_id);
    let s = jstr(&mut env, &sender_jid);
    let em = jstr(&mut env, &emoji);
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt.rt.block_on(messaging::send_reaction(
        &rt,
        &c,
        &tid,
        &s,
        from_me != JNI_FALSE,
        &em,
    )) {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSendStatusReply<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    author_jid: JString<'a>,
    status_id: JString<'a>,
    reply_text: JString<'a>,
    _orig_type: JString<'a>,
    orig_body: JString<'a>,
    _is_from_me: jboolean,
) {
    let a = jstr(&mut env, &author_jid);
    let sid = jstr(&mut env, &status_id);
    let txt = jstr(&mut env, &reply_text);
    let ob = jstr(&mut env, &orig_body);
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt
        .rt
        .block_on(messaging::send_status_reply(&rt, &a, &sid, &txt, &ob))
    {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSendStatusReaction<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    author_jid: JString<'a>,
    status_id: JString<'a>,
    emoji: JString<'a>,
) {
    let a = jstr(&mut env, &author_jid);
    let sid = jstr(&mut env, &status_id);
    let em = jstr(&mut env, &emoji);
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt
        .rt
        .block_on(messaging::send_status_reaction(&rt, &a, &sid, &em))
    {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativePinChatMessage<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    chat_jid: JString<'a>,
    msg_id: JString<'a>,
    from_me: jboolean,
    sender_jid: JString<'a>,
    duration_secs: jlong,
    is_unpin: jboolean,
) {
    let c = jstr(&mut env, &chat_jid);
    let mid = jstr(&mut env, &msg_id);
    let s = jstr(&mut env, &sender_jid);
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt.rt.block_on(messaging::pin_chat_message(
        &rt,
        &c,
        &mid,
        from_me != JNI_FALSE,
        &s,
        duration_secs,
        is_unpin != JNI_FALSE,
    )) {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeGetOwnProfileJson<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
) -> jstring {
    let Some(rt) = get_rt(&mut env) else {
        return std::ptr::null_mut();
    };
    match rt.rt.block_on(contacts_groups::get_own_profile_json(&rt)) {
        Ok(s) => new_jstr(&mut env, &s),
        Err(e) => {
            throw_rt(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSetOwnPushName<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    name: JString<'a>,
) {
    let n = jstr(&mut env, &name);
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt.rt.block_on(contacts_groups::set_own_push_name(&rt, &n)) {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSetOwnAbout<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    about: JString<'a>,
) {
    let ab = jstr(&mut env, &about);
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt.rt.block_on(contacts_groups::set_own_about(&rt, &ab)) {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeGetAccountPrivacySettingsJson<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
) -> jstring {
    let Some(rt) = get_rt(&mut env) else {
        return std::ptr::null_mut();
    };
    match rt
        .rt
        .block_on(contacts_groups::get_account_privacy_settings_json(&rt))
    {
        Ok(s) => new_jstr(&mut env, &s),
        Err(e) => {
            throw_rt(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSetAccountPrivacySetting<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    category: JString<'a>,
    value: JString<'a>,
) -> jstring {
    let cat = jstr(&mut env, &category);
    let val = jstr(&mut env, &value);
    let Some(rt) = get_rt(&mut env) else {
        return std::ptr::null_mut();
    };
    match rt
        .rt
        .block_on(contacts_groups::set_account_privacy_setting(&rt, &cat, &val))
    {
        Ok(s) => new_jstr(&mut env, &s),
        Err(e) => {
            throw_rt(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSetDefaultDisappearingTimerSec<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    seconds: jlong,
) {
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt
        .rt
        .block_on(contacts_groups::set_default_disappearing_timer_sec(&rt, seconds))
    {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeGetBlocklistJson<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
) -> jstring {
    let Some(rt) = get_rt(&mut env) else {
        return std::ptr::null_mut();
    };
    match rt.rt.block_on(contacts_groups::get_blocklist_json(&rt)) {
        Ok(s) => new_jstr(&mut env, &s),
        Err(e) => {
            throw_rt(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSetContactBlocked<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    jid_str: JString<'a>,
    blocked: jboolean,
) {
    let js = jstr(&mut env, &jid_str);
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt
        .rt
        .block_on(contacts_groups::set_contact_blocked(&rt, &js, blocked != JNI_FALSE))
    {
        throw_rt(&mut env, &e);
    }
}

// ── Native VoIP Calls & Media JNI ────────────────────────────────────────

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeRejectCall<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    from_jid: JString<'a>,
    call_id: JString<'a>,
) {
    let fj = jstr(&mut env, &from_jid);
    let cid = jstr(&mut env, &call_id);
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt.rt.block_on(rt.voip.reject_call(&rt, &fj, &cid)) {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeStartOutgoingCall<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    peer_jid: JString<'a>,
    is_video: jboolean,
    is_group: jboolean,
) -> jstring {
    let pj = jstr(&mut env, &peer_jid);
    let Some(rt) = get_rt(&mut env) else {
        return std::ptr::null_mut();
    };
    match rt.rt.block_on(rt.voip.start_outgoing_call(
        &rt,
        &pj,
        is_video != JNI_FALSE,
        is_group != JNI_FALSE,
    )) {
        Ok(cid) => new_jstr(&mut env, &cid),
        Err(e) => {
            throw_rt(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeAcceptIncomingCall<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    call_id: JString<'a>,
    with_video: jboolean,
) -> jstring {
    let cid = jstr(&mut env, &call_id);
    let Some(rt) = get_rt(&mut env) else {
        return std::ptr::null_mut();
    };
    match rt
        .rt
        .block_on(rt.voip.accept_incoming_call(&rt, &cid, with_video != JNI_FALSE))
    {
        Ok(id) => new_jstr(&mut env, &id),
        Err(e) => {
            throw_rt(&mut env, &e);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeTerminateCall<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    call_id: JString<'a>,
) {
    let cid = jstr(&mut env, &call_id);
    let Some(rt) = BridgeRuntime::get() else { return };
    rt.rt.block_on(rt.voip.terminate_call(&cid));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSetCallMuted<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    call_id: JString<'a>,
    muted: jboolean,
) {
    let cid = jstr(&mut env, &call_id);
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt.rt.block_on(rt.voip.set_muted(&cid, muted != JNI_FALSE)) {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSetCallVideoEnabled<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    call_id: JString<'a>,
    enabled: jboolean,
) {
    let cid = jstr(&mut env, &call_id);
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt
        .rt
        .block_on(rt.voip.set_video_enabled(&cid, enabled != JNI_FALSE))
    {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeSetCallVideoOrientation<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    call_id: JString<'a>,
    orientation: jint,
) {
    let cid = jstr(&mut env, &call_id);
    let Some(rt) = get_rt(&mut env) else { return };
    if let Err(e) = rt
        .rt
        .block_on(rt.voip.set_video_orientation(&cid, orientation.clamp(0, 3) as u8))
    {
        throw_rt(&mut env, &e);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativeRequestPeerKeyframe<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    call_id: JString<'a>,
) {
    let cid = jstr(&mut env, &call_id);
    let Some(rt) = BridgeRuntime::get() else { return };
    rt.voip.request_peer_keyframe(&cid);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativePushVoipAudioCapture<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    call_id: JString<'a>,
    samples: JShortArray<'a>,
) -> jint {
    let cid = jstr(&mut env, &call_id);
    let Some(rt) = BridgeRuntime::get() else { return -1 };
    let Ok(len) = env.get_array_length(&samples) else {
        return -1;
    };
    let mut buf = vec![0i16; len as usize];
    if env.get_short_array_region(&samples, 0, &mut buf).is_err() {
        return -1;
    }
    rt.voip.push_audio_capture(&cid, &buf)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativePullVoipAudioPlayout<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    call_id: JString<'a>,
    out_samples: JShortArray<'a>,
) -> jint {
    let cid = jstr(&mut env, &call_id);
    let Some(rt) = BridgeRuntime::get() else { return -1 };
    let Ok(len) = env.get_array_length(&out_samples) else {
        return -1;
    };
    let mut buf = vec![0i16; len as usize];
    let n = rt.voip.pull_audio_playout(&rt, &cid, &mut buf);
    if n > 0 {
        let _ = env.set_short_array_region(&out_samples, 0, &buf[..(n as usize)]);
    }
    n
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativePushVoipVideoAu<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    call_id: JString<'a>,
    data: JByteArray<'a>,
    timestamp_90k: jlong,
) -> jint {
    let cid = jstr(&mut env, &call_id);
    let Some(rt) = BridgeRuntime::get() else { return -1 };
    let Ok(bytes) = env.convert_byte_array(&data) else {
        return -1;
    };
    rt.voip.push_video_au(&cid, bytes, timestamp_90k as u32)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_wa_bridge_whatsbridge_Whatsbridge_nativePullVoipVideoFrame<'a>(
    mut env: JNIEnv<'a>,
    _cls: JClass<'a>,
    call_id: JString<'a>,
) -> jbyteArray {
    let cid = jstr(&mut env, &call_id);
    let Some(rt) = BridgeRuntime::get() else {
        return std::ptr::null_mut();
    };
    let Some(packet) = rt.voip.pull_video_frame(&rt, &cid) else {
        return std::ptr::null_mut();
    };
    env.byte_array_from_slice(&packet)
        .map(|arr| arr.into_raw())
        .unwrap_or(std::ptr::null_mut())
}
