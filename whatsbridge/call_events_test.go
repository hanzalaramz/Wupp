package whatsbridge

import (
	"context"
	"testing"
	"time"

	"go.mau.fi/whatsmeow/types"
)

func TestResolveIncomingCallFields_DMAndGroup(t *testing.T) {
	callerPN := types.NewJID("15551234567", types.DefaultUserServer)
	callerLID := types.NewJID("998877665544", types.HiddenUserServer)

	t.Run("DM call keeps alternate LID and non-AD sender", func(t *testing.T) {
		ts := time.Unix(1_730_000_000, 0)
		chatJID, normChat, normSender, altSender, isGroup, tsMs := resolveIncomingCallFields(
			nil,
			context.Background(),
			types.BasicCallMeta{
				From:           callerPN,
				CallCreator:    types.JID{User: callerPN.User, Server: callerPN.Server, Device: 2},
				CallCreatorAlt: callerLID,
				CallID:         "call-dm-1",
				Timestamp:      ts,
			},
			false,
		)
		if chatJID != callerPN || normChat != callerPN.String() {
			t.Fatalf("unexpected DM chat resolution: chat=%v norm=%q", chatJID, normChat)
		}
		if normSender != callerPN.String() {
			t.Fatalf("got normSender=%q, want %q", normSender, callerPN.String())
		}
		if altSender != callerLID.String() {
			t.Fatalf("got altSender=%q, want %q", altSender, callerLID.String())
		}
		if isGroup {
			t.Fatal("expected DM call not to be marked isGroup")
		}
		if tsMs != ts.UnixMilli() {
			t.Fatalf("got tsMs=%d, want %d", tsMs, ts.UnixMilli())
		}
	})

	t.Run("Group call notice sets group flag and prefers group JID", func(t *testing.T) {
		grpJID := types.NewJID("120363011223344", types.GroupServer)
		_, normChat, normSender, _, isGroup, tsMs := resolveIncomingCallFields(
			nil,
			context.Background(),
			types.BasicCallMeta{
				From:        grpJID,
				GroupJID:    grpJID,
				CallCreator: callerPN,
				CallID:      "call-grp-1",
			},
			true,
		)
		if !isGroup {
			t.Fatal("expected group notice to set isGroup=true")
		}
		if normChat != grpJID.String() {
			t.Fatalf("got normChat=%q, want %q", normChat, grpJID.String())
		}
		if normSender != callerPN.String() {
			t.Fatalf("got normSender=%q, want %q", normSender, callerPN.String())
		}
		if tsMs <= 0 {
			t.Fatalf("expected non-zero fallback timestamp, got %d", tsMs)
		}
	})
}
