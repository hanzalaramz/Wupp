package whatsbridge

import (
	"testing"

	"go.mau.fi/whatsmeow/proto/waE2E"
	"google.golang.org/protobuf/proto"
)

func TestExtractMessageContent_PreservesApkFilenameWithCaption(t *testing.T) {
	msg := &waE2E.Message{
		DocumentMessage: &waE2E.DocumentMessage{
			FileName: proto.String("Wupp-v0.3.3.apk"),
			Caption:  proto.String("Install this update"),
			Mimetype: proto.String("application/vnd.android.package-archive"),
			FileLength: proto.Uint64(42_000_000),
		},
	}
	body, msgType, mimeType, _, fileSize, _ := extractMessageContent(msg)
	if msgType != "DOCUMENT" {
		t.Fatalf("expected DOCUMENT, got %s", msgType)
	}
	if mimeType != "application/vnd.android.package-archive" {
		t.Fatalf("unexpected mimeType: %s", mimeType)
	}
	if fileSize != 42_000_000 {
		t.Fatalf("unexpected fileSize: %d", fileSize)
	}
	if body != "Wupp-v0.3.3.apk\nInstall this update" {
		t.Fatalf("expected filename + caption preserved, got %q", body)
	}
}

func TestExtensionForMime_CommonDocumentsAndApk(t *testing.T) {
	cases := []struct {
		mime string
		want string
	}{
		{"application/vnd.android.package-archive", ".apk"},
		{"application/pdf", ".pdf"},
		{"application/zip", ".zip"},
		{"application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"},
		{"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"},
		{"text/plain", ".txt"},
		{"application/octet-stream", ".bin"},
	}
	for _, tc := range cases {
		if got := extensionForMime(tc.mime, "DOCUMENT"); got != tc.want {
			t.Fatalf("extensionForMime(%q) = %q, want %q", tc.mime, got, tc.want)
		}
	}
	if maxManualMediaBytes < 512*1024*1024 {
		t.Fatalf("expected maxManualMediaBytes >= 512 MiB, got %d", maxManualMediaBytes)
	}
}
