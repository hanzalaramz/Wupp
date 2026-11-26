use std::collections::HashMap;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, RwLock};
use std::time::Duration;

use async_channel::{Receiver, Sender};
use whatsapp_rust::voip::{
    AudioSink, AudioSource, CallEvent, CallHandle, KeyframeUrgency, TimedVideoFrame, VideoFrame,
    VideoSink, VideoSource, VideoState, VideoUpgradeToken,
};
use whatsapp_rust::wacore::types::call::{CallAction, ElsewhereOutcome, IncomingCall, MissedCall, CallEndedElsewhere};
use whatsapp_rust::wacore_binary::JidExt;

use crate::jid_helpers::{canonical_jid_str, parse_jid_flexible};
use crate::runtime::{now_secs, BridgeRuntime};

#[allow(dead_code)]
pub const VOIP_SAMPLE_RATE: usize = 16_000;
#[allow(dead_code)]
pub const VOIP_FRAME_SAMPLES: usize = 960; // 60 ms @ 16 kHz mono

struct NativeAudioSource {
    rx: Receiver<Vec<i16>>,
}

impl AudioSource for NativeAudioSource {
    fn frames(&self) -> Receiver<Vec<i16>> {
        self.rx.clone()
    }
}

struct NativeAudioSink {
    tx: Sender<Vec<i16>>,
}

impl AudioSink for NativeAudioSink {
    fn playout(&self) -> Sender<Vec<i16>> {
        self.tx.clone()
    }
}

struct NativeVideoSource {
    legacy_rx: Receiver<Vec<u8>>,
    timed_rx: Receiver<TimedVideoFrame>,
}

impl VideoSource for NativeVideoSource {
    fn frames(&self) -> Receiver<Vec<u8>> {
        self.legacy_rx.clone()
    }

    fn timed_frames(&self) -> Option<Receiver<TimedVideoFrame>> {
        Some(self.timed_rx.clone())
    }

    fn rtp_timestamp_stride(&self) -> u32 {
        3000 // 30 fps @ 90 kHz default fallback
    }
}

struct NativeVideoSink {
    tx: Sender<VideoFrame>,
}

impl VideoSink for NativeVideoSink {
    fn playout(&self) -> Sender<VideoFrame> {
        self.tx.clone()
    }
}

#[derive(Clone)]
struct PendingIncomingOffer {
    offer: Arc<IncomingCall>,
    peer_jid: String,
    is_video: bool,
    is_group: bool,
}

struct ActiveCall {
    call_id: String,
    peer_jid: String,
    is_video: AtomicBool,
    is_group: AtomicBool,
    ended: AtomicBool,
    handle: Arc<CallHandle>,
    audio_capture_tx: Sender<Vec<i16>>,
    audio_playout_rx: Receiver<Vec<i16>>,
    video_capture_tx: RwLock<Option<Sender<TimedVideoFrame>>>,
    video_playout_rx: RwLock<Option<Receiver<VideoFrame>>>,
    video_op_lock: tokio::sync::Mutex<()>,
    upgrade_token: Mutex<Option<VideoUpgradeToken>>,
}

pub struct VoipRegistry {
    pending_offers: Mutex<HashMap<String, PendingIncomingOffer>>,
    active_calls: Mutex<HashMap<String, Arc<ActiveCall>>>,
}

impl VoipRegistry {
    pub fn new() -> Self {
        Self {
            pending_offers: Mutex::new(HashMap::new()),
            active_calls: Mutex::new(HashMap::new()),
        }
    }

    pub fn clear_all(&self) {
        self.pending_offers
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .clear();
        self.active_calls
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .clear();
    }

    pub fn on_incoming_call_event(&self, call: &IncomingCall, runtime: &BridgeRuntime) {
        let from_jid = canonical_jid_str(&call.from.to_string());
        match &call.action {
            CallAction::Offer {
                call_id,
                call_creator,
                caller_pn,
                is_video,
                group_jid,
                ..
            } => {
                let effective_from = caller_pn
                    .as_ref()
                    .map(|j| canonical_jid_str(&j.to_string()))
                    .filter(|s| !s.is_empty())
                    .unwrap_or_else(|| from_jid.clone());
                let is_group = group_jid.is_some();
                let target_chat = group_jid
                    .as_ref()
                    .map(|g| canonical_jid_str(&g.to_string()))
                    .unwrap_or_else(|| effective_from.clone());

                {
                    let mut guard = self.pending_offers.lock().unwrap_or_else(|e| e.into_inner());
                    guard.insert(
                        call_id.clone(),
                        PendingIncomingOffer {
                            offer: Arc::new(call.clone()),
                            peer_jid: target_chat.clone(),
                            is_video: *is_video,
                            is_group,
                        },
                    );
                }

                let ts_secs = call
                    .timestamp
                    .map(|t| t.as_millis() / 1000)
                    .unwrap_or_else(now_secs);

                runtime.emit_json(
                    "CALL_INCOMING",
                    serde_json::json!({
                        "callId": call_id,
                        "fromJid": effective_from,
                        "callerJid": effective_from,
                        "callerAltJid": from_jid,
                        "chatJid": target_chat,
                        "callCreator": canonical_jid_str(&call_creator.to_string()),
                        "timestamp": ts_secs,
                        "isVideo": is_video,
                        "isGroup": is_group,
                        "isNotice": false,
                    }),
                );
            }
            CallAction::OfferNotice {
                call_id,
                call_creator,
                is_video,
                is_group,
            } => {
                runtime.emit_json(
                    "CALL_INCOMING",
                    serde_json::json!({
                        "callId": call_id,
                        "fromJid": from_jid,
                        "callerJid": from_jid,
                        "callerAltJid": "",
                        "chatJid": from_jid,
                        "callCreator": canonical_jid_str(&call_creator.to_string()),
                        "timestamp": now_secs(),
                        "isVideo": is_video,
                        "isGroup": is_group,
                        "isNotice": true,
                    }),
                );
            }
            CallAction::Terminate { call_id, reason, .. } => {
                self.pending_offers
                    .lock()
                    .unwrap_or_else(|e| e.into_inner())
                    .remove(call_id);
                let r = reason.clone().unwrap_or_else(|| "terminated".to_string());
                if r == "accepted_elsewhere" {
                    runtime.emit_json(
                        "CALL_ACCEPTED_ELSEWHERE",
                        serde_json::json!({
                            "fromJid": from_jid,
                            "callId": call_id,
                            "reason": r,
                        }),
                    );
                } else {
                    runtime.emit_json(
                        "CALL_TERMINATED",
                        serde_json::json!({
                            "fromJid": from_jid,
                            "callId": call_id,
                            "reason": r,
                        }),
                    );
                }
            }
            CallAction::Reject { call_id, reason, .. } => {
                self.pending_offers
                    .lock()
                    .unwrap_or_else(|e| e.into_inner())
                    .remove(call_id);
                runtime.emit_json(
                    "CALL_TERMINATED",
                    serde_json::json!({
                        "fromJid": from_jid,
                        "callId": call_id,
                        "reason": reason.clone().unwrap_or_else(|| "rejected".to_string()),
                    }),
                );
            }
            CallAction::Accept { call_id, .. } => {
                runtime.emit_json(
                    "CALL_STATE_UPDATE",
                    serde_json::json!({
                        "callId": call_id,
                        "peerJid": from_jid,
                        "state": "accepted",
                    }),
                );
            }
            CallAction::PreAccept { call_id, .. } => {
                runtime.emit_json(
                    "CALL_STATE_UPDATE",
                    serde_json::json!({
                        "callId": call_id,
                        "peerJid": from_jid,
                        "state": "ringing",
                    }),
                );
            }
            _ => {}
        }
    }

    pub fn on_missed_call(&self, missed: &MissedCall, runtime: &BridgeRuntime) {
        self.pending_offers
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .remove(&missed.call_id);
        runtime.emit_json(
            "CALL_TERMINATED",
            serde_json::json!({
                "fromJid": canonical_jid_str(&missed.from.to_string()),
                "callId": missed.call_id,
                "reason": "missed",
            }),
        );
    }

    pub fn on_call_ended_elsewhere(&self, ev: &CallEndedElsewhere, runtime: &BridgeRuntime) {
        self.pending_offers
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .remove(&ev.call_id);
        let from_s = canonical_jid_str(&ev.from.to_string());
        match ev.outcome {
            ElsewhereOutcome::Accepted => {
                runtime.emit_json(
                    "CALL_ACCEPTED_ELSEWHERE",
                    serde_json::json!({
                        "fromJid": from_s,
                        "callId": ev.call_id,
                        "reason": "accepted_elsewhere",
                    }),
                );
            }
            ElsewhereOutcome::Rejected => {
                runtime.emit_json(
                    "CALL_TERMINATED",
                    serde_json::json!({
                        "fromJid": from_s,
                        "callId": ev.call_id,
                        "reason": "rejected_elsewhere",
                    }),
                );
            }
            _ => {
                runtime.emit_json(
                    "CALL_TERMINATED",
                    serde_json::json!({
                        "fromJid": from_s,
                        "callId": ev.call_id,
                        "reason": "ended_elsewhere",
                    }),
                );
            }
        }
    }

    pub async fn start_outgoing_call(
        &self,
        runtime: &Arc<BridgeRuntime>,
        peer_jid_str: &str,
        is_video: bool,
        is_group: bool,
    ) -> Result<String, String> {
        let client = runtime
            .client_arc()
            .ok_or_else(|| "client not initialized".to_string())?;
        let target = parse_jid_flexible(peer_jid_str)?;

        let (cap_tx, cap_rx) = async_channel::bounded::<Vec<i16>>(8);
        let (play_tx, play_rx) = async_channel::bounded::<Vec<i16>>(16);
        let audio_src = NativeAudioSource { rx: cap_rx };
        let audio_sink = NativeAudioSink { tx: play_tx };

        let (vtx_opt, vrx_opt, vsrc_opt, vsink_opt) = if is_video {
            let (vtx, vrx, vsrc, vsink) = new_video_channels();
            (Some(vtx), Some(vrx), Some(vsrc), Some(vsink))
        } else {
            (None, None, None, None)
        };

        let voip = client.voip();
        let handle = if is_group || target.is_group() {
            let mut builder = voip
                .group_call_by_id(&target)
                .audio(audio_src, audio_sink);
            if let (Some(vsrc), Some(vsink)) = (vsrc_opt, vsink_opt) {
                builder = builder.video(vsrc, vsink);
            }
            builder
                .start()
                .await
                .map_err(|e| format!("group call start failed: {e}"))?
        } else {
            let mut builder = voip.call(&target).audio(audio_src, audio_sink);
            if let (Some(vsrc), Some(vsink)) = (vsrc_opt, vsink_opt) {
                builder = builder.video(vsrc, vsink);
            }
            builder
                .start()
                .await
                .map_err(|e| format!("call start failed: {e}"))?
        };

        let call_id = handle.call_id().to_string();
        let entry = Arc::new(ActiveCall {
            call_id: call_id.clone(),
            peer_jid: canonical_jid_str(&target.to_string()),
            is_video: AtomicBool::new(is_video),
            is_group: AtomicBool::new(is_group || target.is_group()),
            ended: AtomicBool::new(false),
            handle: Arc::new(handle),
            audio_capture_tx: cap_tx,
            audio_playout_rx: play_rx,
            video_capture_tx: RwLock::new(vtx_opt),
            video_playout_rx: RwLock::new(vrx_opt),
            video_op_lock: tokio::sync::Mutex::new(()),
            upgrade_token: Mutex::new(None),
        });

        self.active_calls
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .insert(call_id.clone(), Arc::clone(&entry));

        spawn_call_monitor(Arc::clone(runtime), Arc::clone(&entry));
        Ok(call_id)
    }

    pub async fn accept_incoming_call(
        &self,
        runtime: &Arc<BridgeRuntime>,
        call_id: &str,
        with_video: bool,
    ) -> Result<String, String> {
        let client = runtime
            .client_arc()
            .ok_or_else(|| "client not initialized".to_string())?;
        let pending = {
            self.pending_offers
                .lock()
                .unwrap_or_else(|e| e.into_inner())
                .remove(call_id)
        }
        .ok_or_else(|| format!("no pending call offer found for {call_id}"))?;

        let (cap_tx, cap_rx) = async_channel::bounded::<Vec<i16>>(8);
        let (play_tx, play_rx) = async_channel::bounded::<Vec<i16>>(16);
        let audio_src = NativeAudioSource { rx: cap_rx };
        let audio_sink = NativeAudioSink { tx: play_tx };

        let attach_video = with_video && pending.is_video;
        let (vtx_opt, vrx_opt, vsrc_opt, vsink_opt) = if attach_video {
            let (vtx, vrx, vsrc, vsink) = new_video_channels();
            (Some(vtx), Some(vrx), Some(vsrc), Some(vsink))
        } else {
            (None, None, None, None)
        };

        let voip = client.voip();
        let mut builder = voip.accept(&pending.offer).audio(audio_src, audio_sink);
        if let (Some(vsrc), Some(vsink)) = (vsrc_opt, vsink_opt) {
            builder = builder.video(vsrc, vsink);
        }

        let handle = builder
            .start()
            .await
            .map_err(|e| format!("accept call failed: {e}"))?;

        let call_id_owned = handle.call_id().to_string();
        let entry = Arc::new(ActiveCall {
            call_id: call_id_owned.clone(),
            peer_jid: pending.peer_jid,
            is_video: AtomicBool::new(pending.is_video),
            is_group: AtomicBool::new(pending.is_group),
            ended: AtomicBool::new(false),
            handle: Arc::new(handle),
            audio_capture_tx: cap_tx,
            audio_playout_rx: play_rx,
            video_capture_tx: RwLock::new(vtx_opt),
            video_playout_rx: RwLock::new(vrx_opt),
            video_op_lock: tokio::sync::Mutex::new(()),
            upgrade_token: Mutex::new(None),
        });

        self.active_calls
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .insert(call_id_owned.clone(), Arc::clone(&entry));

        spawn_call_monitor(Arc::clone(runtime), Arc::clone(&entry));
        Ok(call_id_owned)
    }

    pub async fn reject_call(
        &self,
        runtime: &BridgeRuntime,
        from_jid_str: &str,
        call_id: &str,
    ) -> Result<(), String> {
        let client = runtime
            .client_arc()
            .ok_or_else(|| "client not initialized".to_string())?;
        let pending = self
            .pending_offers
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .remove(call_id);

        if let Some(offer) = pending {
            client
                .voip()
                .reject(&offer.offer)
                .await
                .map_err(|e| format!("reject failed: {e}"))
        } else {
            let from_jid = parse_jid_flexible(from_jid_str)?;
            client
                .voip()
                .reject_call(call_id, &from_jid, &from_jid)
                .await
                .map_err(|e| format!("reject_call failed: {e}"))
        }
    }

    pub async fn terminate_call(&self, call_id: &str) {
        self.pending_offers
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .remove(call_id);

        let entry = self
            .active_calls
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .remove(call_id);

        if let Some(call) = entry {
            call.ended.store(true, Ordering::SeqCst);
            call.audio_capture_tx.close();
            call.audio_playout_rx.close();
            if let Some(tx) = call
                .video_capture_tx
                .write()
                .unwrap_or_else(|e| e.into_inner())
                .take()
            {
                tx.close();
            }
            if let Some(rx) = call
                .video_playout_rx
                .write()
                .unwrap_or_else(|e| e.into_inner())
                .take()
            {
                rx.close();
            }
            let _ = call.handle.terminate().await;
        }
    }

    pub async fn set_muted(&self, call_id: &str, muted: bool) -> Result<(), String> {
        let call = self
            .get_active_call(call_id)
            .ok_or_else(|| format!("unknown call {call_id}"))?;
        call.handle
            .set_muted(muted)
            .await
            .map_err(|e| format!("set_muted failed: {e}"))
    }

    pub async fn set_video_enabled(&self, call_id: &str, enabled: bool) -> Result<(), String> {
        let call = self
            .get_active_call(call_id)
            .ok_or_else(|| format!("unknown call {call_id}"))?;
        if call.ended.load(Ordering::Relaxed) {
            return Err("call already ended".to_string());
        }
        let _video_op_guard = call.video_op_lock.lock().await;
        if call.ended.load(Ordering::Relaxed) {
            return Err("call already ended".to_string());
        }

        if !enabled {
            if let Some(tx) = call
                .video_capture_tx
                .write()
                .unwrap_or_else(|e| e.into_inner())
                .take()
            {
                tx.close();
            }
            call.handle
                .stop_video()
                .await
                .map_err(|e| format!("stop_video failed: {e}"))?;
            call.is_video.store(false, Ordering::SeqCst);
            return Ok(());
        }

        let (vtx, vrx, vsrc, vsink) = new_video_channels();
        let prev_tx = call
            .video_capture_tx
            .write()
            .unwrap_or_else(|e| e.into_inner())
            .replace(vtx);
        let prev_rx = call
            .video_playout_rx
            .write()
            .unwrap_or_else(|e| e.into_inner())
            .replace(vrx);

        let token = call
            .upgrade_token
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .take();
        let res = if let Some(tok) = token {
            call.handle.accept_video(tok, vsrc, vsink).await
        } else if call.handle.video_states().is_some() {
            call.handle.resume_video(vsrc, vsink).await
        } else {
            call.handle.start_video(vsrc, vsink).await
        };

        if let Err(e) = res {
            // Restore previous video channels so a failed upgrade/resume does not break active playout.
            *call
                .video_capture_tx
                .write()
                .unwrap_or_else(|e| e.into_inner()) = prev_tx;
            *call
                .video_playout_rx
                .write()
                .unwrap_or_else(|e| e.into_inner()) = prev_rx;
            return Err(format!("enable_video failed: {e}"));
        }

        if let Some(old_tx) = prev_tx {
            old_tx.close();
        }
        call.is_video.store(true, Ordering::SeqCst);
        Ok(())
    }

    pub async fn set_video_orientation(&self, call_id: &str, orientation: u8) -> Result<(), String> {
        let call = self
            .get_active_call(call_id)
            .ok_or_else(|| format!("unknown call {call_id}"))?;
        call.handle
            .set_video_orientation(orientation.min(3))
            .await
            .map_err(|e| format!("set_video_orientation failed: {e}"))
    }

    pub fn request_peer_keyframe(&self, call_id: &str) {
        if let Some(call) = self.get_active_call(call_id) {
            call.handle.request_peer_keyframe(KeyframeUrgency::Immediate);
        }
    }

    pub fn push_audio_capture(&self, call_id: &str, pcm: &[i16]) -> i32 {
        let Some(call) = self.get_active_call(call_id) else {
            return -1;
        };
        if call.ended.load(Ordering::Relaxed) {
            return -1;
        }
        match call.audio_capture_tx.try_send(pcm.to_vec()) {
            Ok(()) => pcm.len() as i32,
            Err(_) => 0,
        }
    }

    pub fn pull_audio_playout(&self, runtime: &BridgeRuntime, call_id: &str, out: &mut [i16]) -> i32 {
        let Some(call) = self.get_active_call(call_id) else {
            return -1;
        };
        if call.ended.load(Ordering::Relaxed) {
            return -1;
        }

        // Drain stale queue backlog beyond 4 frames (~80 ms) to prevent multi-second speech drift
        while call.audio_playout_rx.len() > 4 {
            let _ = call.audio_playout_rx.try_recv();
        }

        if let Ok(frame) = call.audio_playout_rx.try_recv() {
            let n = frame.len().min(out.len());
            out[..n].copy_from_slice(&frame[..n]);
            return n as i32;
        }

        let rx = call.audio_playout_rx.clone();
        match runtime.rt.block_on(async move {
            tokio::time::timeout(Duration::from_millis(25), rx.recv()).await
        }) {
            Ok(Ok(frame)) => {
                let n = frame.len().min(out.len());
                out[..n].copy_from_slice(&frame[..n]);
                n as i32
            }
            Ok(Err(_)) => -1, // Channel closed
            Err(_) => 0,      // Timeout — caller plays silence cleanly
        }
    }

    pub fn push_video_au(&self, call_id: &str, data: Vec<u8>, ts_90k: u32) -> i32 {
        let Some(call) = self.get_active_call(call_id) else {
            return -1;
        };
        if call.ended.load(Ordering::Relaxed) {
            return -1;
        }
        let tx_opt = call
            .video_capture_tx
            .read()
            .unwrap_or_else(|e| e.into_inner())
            .clone();
        let Some(tx) = tx_opt else {
            return 0;
        };
        let frame = TimedVideoFrame::builder()
            .data(data)
            .timestamp(ts_90k)
            .build();
        match tx.try_send(frame) {
            Ok(()) => 1,
            Err(_) => 0,
        }
    }

    /// Returns encoded header (8B) + Annex-B H.264 payload or `None` when timed out/unavailable.
    pub fn pull_video_frame(&self, runtime: &BridgeRuntime, call_id: &str) -> Option<Vec<u8>> {
        let call = self.get_active_call(call_id)?;
        if call.ended.load(Ordering::Relaxed) {
            return None;
        }
        let rx_opt = call
            .video_playout_rx
            .read()
            .unwrap_or_else(|e| e.into_inner())
            .clone();
        let Some(rx) = rx_opt else {
            // Sleep briefly when video playout isn't attached yet so JVM decoder threads don't busy-spin.
            runtime
                .rt
                .block_on(tokio::time::sleep(Duration::from_millis(25)));
            return None;
        };

        let frame = if let Ok(f) = rx.try_recv() {
            f
        } else {
            match runtime.rt.block_on(async move {
                tokio::time::timeout(Duration::from_millis(30), rx.recv()).await
            }) {
                Ok(Ok(f)) => f,
                _ => return None,
            }
        };

        let mut packet = Vec::with_capacity(8 + frame.data.len());
        let flags: u8 = if frame.keyframe { 1 } else { 0 };
        let orientation: u8 = frame.orientation;
        packet.push(flags);
        packet.push(orientation);
        packet.push(0);
        packet.push(0);
        packet.extend_from_slice(&frame.timestamp.to_le_bytes());
        packet.extend_from_slice(&frame.data);
        Some(packet)
    }

    fn get_active_call(&self, call_id: &str) -> Option<Arc<ActiveCall>> {
        self.active_calls
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .get(call_id)
            .cloned()
    }
}

fn new_video_channels() -> (
    Sender<TimedVideoFrame>,
    Receiver<VideoFrame>,
    NativeVideoSource,
    NativeVideoSink,
) {
    let (_legacy_tx, legacy_rx) = async_channel::bounded::<Vec<u8>>(4);
    let (timed_tx, timed_rx) = async_channel::bounded::<TimedVideoFrame>(4);
    let (play_tx, play_rx) = async_channel::bounded::<VideoFrame>(16);
    (
        timed_tx,
        play_rx,
        NativeVideoSource { legacy_rx, timed_rx },
        NativeVideoSink { tx: play_tx },
    )
}

fn spawn_call_monitor(runtime: Arc<BridgeRuntime>, call: Arc<ActiveCall>) {
    let mut events_opt = call.handle.take_events();
    let rt_clone = Arc::clone(&runtime);
    let call_clone = Arc::clone(&call);

    runtime.rt.spawn(async move {
        if let Some(ref mut events) = events_opt {
            while let Ok(ev) = events.recv().await {
                match ev {
                    CallEvent::MediaConnected => {
                        rt_clone.emit_json(
                            "CALL_STATE_UPDATE",
                            serde_json::json!({
                                "callId": call_clone.call_id,
                                "peerJid": call_clone.peer_jid,
                                "state": "connected",
                                "isVideo": call_clone.is_video.load(Ordering::Relaxed),
                                "isGroup": call_clone.is_group.load(Ordering::Relaxed),
                            }),
                        );
                    }
                    CallEvent::PeerVideoStateChanged {
                        state,
                        orientation,
                        upgrade_token,
                        ..
                    } => {
                        let has_token = upgrade_token.is_some();
                        if let Some(tok) = upgrade_token {
                            *call_clone
                                .upgrade_token
                                .lock()
                                .unwrap_or_else(|e| e.into_inner()) = Some(tok);
                        }
                        if matches!(
                            state,
                            VideoState::Enabled | VideoState::UpgradeAccept
                        ) {
                            call_clone.is_video.store(true, Ordering::SeqCst);
                        } else if state.is_inactive_for_call_mode() {
                            call_clone.is_video.store(false, Ordering::SeqCst);
                        }
                        rt_clone.emit_json(
                            "CALL_VIDEO_STATE",
                            serde_json::json!({
                                "callId": call_clone.call_id,
                                "state": format!("{state:?}"),
                                "orientation": orientation,
                                "hasToken": has_token,
                                "isVideo": call_clone.is_video.load(Ordering::Relaxed),
                            }),
                        );
                    }
                    CallEvent::VideoKeyframeNeeded => {
                        rt_clone.emit_json(
                            "CALL_KEYFRAME_NEEDED",
                            serde_json::json!({
                                "callId": call_clone.call_id,
                            }),
                        );
                    }
                    CallEvent::MediaSetupFailed(reason) => {
                        rt_clone.emit_log(format!("Call setup failed ({}): {reason}", call_clone.call_id));
                    }
                    CallEvent::Closed(_) => break,
                    _ => {}
                }
            }
        }

        call_clone.handle.wait_ended().await;
        call_clone.ended.store(true, Ordering::SeqCst);
        rt_clone
            .voip
            .active_calls
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .remove(&call_clone.call_id);

        rt_clone.emit_json(
            "CALL_TERMINATED",
            serde_json::json!({
                "fromJid": call_clone.peer_jid,
                "callId": call_clone.call_id,
                "reason": "ended",
            }),
        );
    });
}
