package whatsbridge

import (
	"testing"

	"go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/proto/waE2E"
)

func TestResolveOutboundUploadSpec_GifPlayback(t *testing.T) {
	spec := resolveOutboundUploadSpec("video/mp4", "gif", false)
	if spec.mediaType != whatsmeow.MediaVideo || !spec.isGifPlayback || spec.isSticker || spec.isViewOnce {
		t.Fatalf("unexpected spec for gif: %+v", spec)
	}

	// Even if :viewonce was accidentally appended or mime was image/gif, gif mode enforces MediaVideo + ViewOnce=false
	specMixed := resolveOutboundUploadSpec("image/gif", "standard:gif:viewonce", false)
	if specMixed.mediaType != whatsmeow.MediaVideo || !specMixed.isGifPlayback || specMixed.isViewOnce {
		t.Fatalf("unexpected spec for mixed gif mode: %+v", specMixed)
	}

	// Sticker and normal video must not set isGifPlayback
	stickerSpec := resolveOutboundUploadSpec("image/webp", "sticker", false)
	if stickerSpec.isGifPlayback || !stickerSpec.isSticker || stickerSpec.mediaType != whatsmeow.MediaImage {
		t.Fatalf("unexpected sticker spec: %+v", stickerSpec)
	}

	voVideoSpec := resolveOutboundUploadSpec("video/mp4", "hd:viewonce", false)
	if voVideoSpec.isGifPlayback || !voVideoSpec.isViewOnce || voVideoSpec.mediaType != whatsmeow.MediaVideo {
		t.Fatalf("unexpected view-once video spec: %+v", voVideoSpec)
	}
}

func TestBuildOutboundVideoMessage_GifPlayback(t *testing.T) {
	up := whatsmeow.UploadResponse{
		URL:           "https://mmg.whatsapp.net/v/t62.7161-24/test",
		DirectPath:    "/v/t62.7161-24/test",
		MediaKey:      []byte{1, 2, 3, 4},
		FileEncSHA256: []byte{5, 6, 7, 8},
		FileSHA256:    []byte{9, 10, 11, 12},
		FileLength:    2048,
	}

	gifMsg := buildOutboundVideoMessage(up, "", "hello", 3, true, true)
	if gifMsg.GetViewOnceMessageV2() != nil {
		t.Fatal("did not expect ViewOnceMessageV2 wrapper for GIF playback")
	}
	vm := gifMsg.GetVideoMessage()
	if vm == nil {
		t.Fatal("expected VideoMessage")
	}
	if !vm.GetGifPlayback() {
		t.Fatal("expected GifPlayback=true")
	}
	if vm.GetGifAttribution() != waE2E.VideoMessage_NONE {
		t.Fatalf("expected GifAttribution=NONE, got %v", vm.GetGifAttribution())
	}
	if vm.GetMimetype() != "video/mp4" {
		t.Fatalf("expected mimetype video/mp4 fallback, got %q", vm.GetMimetype())
	}
	if vm.GetViewOnce() {
		t.Fatal("expected ViewOnce=false for GIF playback")
	}
	if vm.GetSeconds() != 3 || vm.GetCaption() != "hello" {
		t.Fatalf("unexpected seconds=%d caption=%q", vm.GetSeconds(), vm.GetCaption())
	}

	voMsg := buildOutboundVideoMessage(up, "video/mp4", "secret", 5, true, false)
	wrapped := voMsg.GetViewOnceMessageV2().GetMessage().GetVideoMessage()
	if wrapped == nil {
		t.Fatal("expected ViewOnceMessageV2 wrapping VideoMessage for view-once video")
	}
	if wrapped.GetGifPlayback() {
		t.Fatal("did not expect GifPlayback=true for view-once video")
	}
	if !wrapped.GetViewOnce() {
		t.Fatal("expected ViewOnce=true inside ViewOnceMessageV2")
	}
}

func TestBuildOutboundAudioMessage(t *testing.T) {
	up := whatsmeow.UploadResponse{
		URL:           "https://mmg.whatsapp.net/v/t62.7117-24/ptt",
		DirectPath:    "/v/t62.7117-24/ptt",
		MediaKey:      []byte{1, 2, 3, 4},
		FileEncSHA256: []byte{5, 6, 7, 8},
		FileSHA256:    []byte{9, 10, 11, 12},
		FileLength:    4096,
	}

	pttMsg := buildOutboundAudioMessage(up, "audio/ogg; codecs=opus", true, 4, "10,20,30,40", 1730000100)
	am := pttMsg.GetAudioMessage()
	if am == nil {
		t.Fatal("expected AudioMessage")
	}
	if !am.GetPTT() || am.GetMimetype() != "audio/ogg; codecs=opus" {
		t.Fatalf("unexpected PTT=%v mime=%q", am.GetPTT(), am.GetMimetype())
	}
	if len(am.GetWaveform()) != 64 {
		t.Fatalf("expected 64-byte WhatsApp PTT waveform, got %d bytes", len(am.GetWaveform()))
	}
	if am.GetMediaKeyTimestamp() != 1730000100 || am.GetSeconds() != 4 {
		t.Fatalf("unexpected ts=%d sec=%d", am.GetMediaKeyTimestamp(), am.GetSeconds())
	}

	fallbackMsg := buildOutboundAudioMessage(up, "audio/mp4", false, 5, "10,20,30", 1730000200)
	amFallback := fallbackMsg.GetAudioMessage()
	if amFallback == nil || amFallback.GetPTT() {
		t.Fatal("expected non-PTT AudioMessage for M4A fallback")
	}
	if amFallback.GetMimetype() != "audio/mp4" {
		t.Fatalf("expected audio/mp4 mimetype, got %q", amFallback.GetMimetype())
	}
	if len(amFallback.GetWaveform()) != 0 {
		t.Fatalf("expected no PTT waveform on non-PTT audio, got %d bytes", len(amFallback.GetWaveform()))
	}
}
