package whatsbridge

import (
	"errors"
	"sync/atomic"
	"testing"
	"time"

	"go.mau.fi/whatsmeow"
	"go.mau.fi/whatsmeow/proto/waCommon"
	"go.mau.fi/whatsmeow/proto/waE2E"
	"go.mau.fi/whatsmeow/proto/waWeb"
	"go.mau.fi/whatsmeow/store"
	"go.mau.fi/whatsmeow/types"
	"go.mau.fi/whatsmeow/types/events"
	"google.golang.org/protobuf/proto"
)

func TestShouldIgnoreUndecryptable(t *testing.T) {
	dmJID := types.NewJID("15551234567", types.DefaultUserServer)
	groupJID := types.NewJID("120363012345678901", types.GroupServer)
	newsletterJID := types.NewJID("120363099999999999", types.NewsletterServer)

	cases := []struct {
		name   string
		evt    *events.UndecryptableMessage
		ignore bool
	}{
		{
			name:   "nil event",
			evt:    nil,
			ignore: true,
		},
		{
			name: "decrypt fail hide (silent reaction/poll/edit)",
			evt: &events.UndecryptableMessage{
				Info:            types.MessageInfo{MessageSource: types.MessageSource{Chat: groupJID}},
				DecryptFailMode: events.DecryptFailHide,
			},
			ignore: true,
		},
		{
			name: "peer sync category",
			evt: &events.UndecryptableMessage{
				Info: types.MessageInfo{
					MessageSource: types.MessageSource{Chat: dmJID},
					Category:      "peer",
				},
			},
			ignore: true,
		},
		{
			name: "status broadcast",
			evt: &events.UndecryptableMessage{
				Info: types.MessageInfo{MessageSource: types.MessageSource{Chat: types.StatusBroadcastJID}},
			},
			ignore: true,
		},
		{
			name: "newsletter channel",
			evt: &events.UndecryptableMessage{
				Info: types.MessageInfo{MessageSource: types.MessageSource{Chat: newsletterJID}},
			},
			ignore: true,
		},
		{
			name: "edit attribute",
			evt: &events.UndecryptableMessage{
				Info: types.MessageInfo{
					MessageSource: types.MessageSource{Chat: groupJID},
					Edit:          types.EditAttributeMessageEdit,
				},
			},
			ignore: true,
		},
		{
			name: "reaction type",
			evt: &events.UndecryptableMessage{
				Info: types.MessageInfo{
					MessageSource: types.MessageSource{Chat: dmJID},
					Type:          "reaction",
				},
			},
			ignore: true,
		},
		{
			name: "poll type",
			evt: &events.UndecryptableMessage{
				Info: types.MessageInfo{
					MessageSource: types.MessageSource{Chat: groupJID},
					Type:          "poll",
				},
			},
			ignore: true,
		},
		{
			name: "medianotify type",
			evt: &events.UndecryptableMessage{
				Info: types.MessageInfo{
					MessageSource: types.MessageSource{Chat: groupJID},
					Type:          "medianotify",
				},
			},
			ignore: true,
		},
		{
			name: "normal DM message",
			evt: &events.UndecryptableMessage{
				Info: types.MessageInfo{
					MessageSource: types.MessageSource{Chat: dmJID},
					Type:          "text",
				},
				IsUnavailable: false,
			},
			ignore: false,
		},
		{
			name: "unavailable business DM message",
			evt: &events.UndecryptableMessage{
				Info: types.MessageInfo{
					MessageSource: types.MessageSource{Chat: dmJID},
					Type:          "text",
				},
				IsUnavailable: true,
			},
			ignore: false,
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := shouldIgnoreUndecryptable(tc.evt); got != tc.ignore {
				t.Fatalf("shouldIgnoreUndecryptable() = %v, want %v", got, tc.ignore)
			}
		})
	}
}

func TestExtractMessageContentBusinessTypes(t *testing.T) {
	t.Run("hydrated template text", func(t *testing.T) {
		msg := &waE2E.Message{
			TemplateMessage: &waE2E.TemplateMessage{
				HydratedTemplate: &waE2E.TemplateMessage_HydratedFourRowTemplate{
					Title: &waE2E.TemplateMessage_HydratedFourRowTemplate_HydratedTitleText{
						HydratedTitleText: "Order Update",
					},
					HydratedContentText: proto.String("Your package is out for delivery."),
					HydratedFooterText:  proto.String("Acme Express"),
				},
			},
		}
		body, msgType, mime, _, _, dl := extractMessageContent(msg)
		want := "Order Update\nYour package is out for delivery.\nAcme Express"
		if body != want || msgType != "TEXT" || mime != "text/plain" || dl != nil {
			t.Fatalf("got (%q, %q, %q, %v), want (%q, TEXT, text/plain, nil)", body, msgType, mime, dl, want)
		}
	})

	t.Run("hydrated template with header image", func(t *testing.T) {
		img := &waE2E.ImageMessage{
			Caption:    proto.String("Promo"),
			Mimetype:   proto.String("image/jpeg"),
			FileLength: proto.Uint64(12345),
		}
		msg := &waE2E.Message{
			TemplateMessage: &waE2E.TemplateMessage{
				Format: &waE2E.TemplateMessage_HydratedFourRowTemplate_{
					HydratedFourRowTemplate: &waE2E.TemplateMessage_HydratedFourRowTemplate{
						Title: &waE2E.TemplateMessage_HydratedFourRowTemplate_ImageMessage{
							ImageMessage: img,
						},
						HydratedContentText: proto.String("Weekend Offer"),
					},
				},
			},
		}
		body, msgType, mime, _, fileLen, dl := extractMessageContent(msg)
		if body != "Weekend Offer\nPromo" || msgType != "IMAGE" || mime != "image/jpeg" || fileLen != 12345 || dl != img {
			t.Fatalf("unexpected result: (%q, %q, %q, %d, %v)", body, msgType, mime, fileLen, dl)
		}
	})

	t.Run("interactive message", func(t *testing.T) {
		msg := &waE2E.Message{
			InteractiveMessage: &waE2E.InteractiveMessage{
				Header: &waE2E.InteractiveMessage_Header{
					Title:    proto.String("Appointment"),
					Subtitle: proto.String("Confirmed"),
				},
				Body:   &waE2E.InteractiveMessage_Body{Text: proto.String("Tomorrow at 10:00 AM")},
				Footer: &waE2E.InteractiveMessage_Footer{Text: proto.String("Reply STOP to opt out")},
			},
		}
		body, msgType, _, _, _, _ := extractMessageContent(msg)
		want := "Appointment\nConfirmed\nTomorrow at 10:00 AM\nReply STOP to opt out"
		if body != want || msgType != "TEXT" {
			t.Fatalf("got (%q, %q), want (%q, TEXT)", body, msgType, want)
		}
	})

	t.Run("buttons message", func(t *testing.T) {
		msg := &waE2E.Message{
			ButtonsMessage: &waE2E.ButtonsMessage{
				Header:      &waE2E.ButtonsMessage_Text{Text: "Verification"},
				ContentText: proto.String("Tap confirm below."),
				FooterText:  proto.String("Support"),
			},
		}
		body, msgType, _, _, _, _ := extractMessageContent(msg)
		want := "Verification\nTap confirm below.\nSupport"
		if body != want || msgType != "TEXT" {
			t.Fatalf("got (%q, %q), want (%q, TEXT)", body, msgType, want)
		}
	})

	t.Run("list message", func(t *testing.T) {
		msg := &waE2E.Message{
			ListMessage: &waE2E.ListMessage{
				Title:       proto.String("Menu"),
				Description: proto.String("Choose an option"),
				FooterText:  proto.String("Thank you"),
			},
		}
		body, msgType, _, _, _, _ := extractMessageContent(msg)
		want := "Menu\nChoose an option\nThank you"
		if body != want || msgType != "TEXT" {
			t.Fatalf("got (%q, %q), want (%q, TEXT)", body, msgType, want)
		}
	})
}

func TestCancelPendingUndecryptableStopsTimer(t *testing.T) {
	const testID = "test-cancel-timer-id"
	t.Cleanup(func() {
		pendingUndecryptable.Delete(testID)
		retryCounts.Delete(testID)
		emittedStubIDs.Delete(testID)
	})

	var fired atomic.Bool
	timer := time.AfterFunc(80*time.Millisecond, func() {
		fired.Store(true)
	})
	pendingUndecryptable.Store(testID, timer)
	retryCounts.Store(testID, 1)

	cancelPendingUndecryptable(testID)
	time.Sleep(130 * time.Millisecond)

	if fired.Load() {
		t.Fatal("expected pending undecryptable timer to be stopped before firing")
	}
	if _, ok := pendingUndecryptable.Load(testID); ok {
		t.Fatal("expected pendingUndecryptable entry to be deleted")
	}
	if _, ok := retryCounts.Load(testID); ok {
		t.Fatal("expected retryCounts entry to be deleted")
	}
}

func TestIsStatusMessageInfo(t *testing.T) {
	if isStatusMessageInfo(nil) {
		t.Fatal("expected nil MessageInfo to return false")
	}
	if !isStatusMessageInfo(&types.MessageInfo{MessageSource: types.MessageSource{Chat: types.StatusBroadcastJID}}) {
		t.Fatal("expected status@broadcast to be treated as status")
	}
	nlJID := types.NewJID("120363012345678901", types.NewsletterServer)
	if !isStatusMessageInfo(&types.MessageInfo{MessageSource: types.MessageSource{Chat: nlJID}, IsNewsletterStatus: true}) {
		t.Fatal("expected IsNewsletterStatus to be treated as status")
	}
	dmJID := types.NewJID("15551234567", types.DefaultUserServer)
	if isStatusMessageInfo(&types.MessageInfo{MessageSource: types.MessageSource{Chat: dmJID}}) {
		t.Fatal("expected normal DM not to be treated as status")
	}
}

func TestUnwrapAndExtractStatusWrappers(t *testing.T) {
	img := &waE2E.ImageMessage{Caption: proto.String("Sunset")}
	cases := []struct {
		name string
		msg  *waE2E.Message
	}{
		{
			name: "status mention wrapper",
			msg: &waE2E.Message{
				StatusMentionMessage: &waE2E.FutureProofMessage{
					Message: &waE2E.Message{ImageMessage: img},
				},
			},
		},
		{
			name: "group status wrapper v2",
			msg: &waE2E.Message{
				GroupStatusMessageV2: &waE2E.FutureProofMessage{
					Message: &waE2E.Message{ImageMessage: img},
				},
			},
		},
		{
			name: "status add yours wrapper",
			msg: &waE2E.Message{
				StatusAddYours: &waE2E.FutureProofMessage{
					Message: &waE2E.Message{ImageMessage: img},
				},
			},
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			inner, _, _, _, _ := unwrapMessageContainer(tc.msg)
			if inner == nil || inner.GetImageMessage() != img {
				t.Fatalf("expected unwrapped ImageMessage, got %v", inner)
			}
		})
	}

	body, msgType, _, _, _, _ := extractMessageContent(&waE2E.Message{
		StatusQuotedMessage: &waE2E.StatusQuotedMessage{
			Text: proto.String("Nice view!"),
		},
	})
	if msgType != "TEXT" || body != "Nice view!" {
		t.Fatalf("got (%q, %q), want (Nice view!, TEXT)", body, msgType)
	}
}

func TestParseStatusWebMessageFallbacks(t *testing.T) {
	cli := &whatsmeow.Client{
		Store: &store.Device{
			ID:  &types.JID{User: "15550001111", Server: types.DefaultUserServer},
			LID: types.NewJID("999000111", types.HiddenUserServer),
		},
	}
	senderStr := "15559876543@s.whatsapp.net"
	webMsg := &waWeb.WebMessageInfo{
		Key: &waCommon.MessageKey{
			ID:        proto.String("st-msg-1"),
			FromMe:    proto.Bool(false),
			RemoteJID: proto.String(senderStr),
		},
		MessageTimestamp: proto.Uint64(1730000000),
		Message: &waE2E.Message{
			ExtendedTextMessage: &waE2E.ExtendedTextMessage{Text: proto.String("Hello status")},
		},
	}

	parsed, err := parseStatusWebMessage(cli, types.StatusBroadcastJID, webMsg)
	if err != nil || parsed == nil {
		t.Fatalf("parseStatusWebMessage failed: parsed=%v err=%v", parsed, err)
	}
	if parsed.Info.Chat != types.StatusBroadcastJID {
		t.Fatalf("got chat=%v, want %v", parsed.Info.Chat, types.StatusBroadcastJID)
	}
	if parsed.Info.Sender.String() != senderStr {
		t.Fatalf("got sender=%v, want %s", parsed.Info.Sender, senderStr)
	}
}

func TestScheduleAndCancelStatusRecovery(t *testing.T) {
	const msgID = "status-recovery-test-1"
	t.Cleanup(func() {
		clearPendingStatusRecoveries(true)
	})

	sender := types.NewJID("15557654321", types.DefaultUserServer)
	scheduleStatusRecovery(&events.UndecryptableMessage{
		Info: types.MessageInfo{
			MessageSource: types.MessageSource{
				Chat:   types.StatusBroadcastJID,
				Sender: sender,
			},
			ID:        msgID,
			Timestamp: time.Now(),
		},
		IsUnavailable: true,
	})

	if _, ok := pendingStatusRecovery.Load(msgID); !ok {
		t.Fatal("expected undecryptable status@broadcast event to queue background recovery")
	}

	cancelPendingUndecryptable(msgID)
	if _, ok := pendingStatusRecovery.Load(msgID); ok {
		t.Fatal("expected cancelPendingUndecryptable to clear pending status recovery")
	}
}

func TestStatusRecoveryAntiBurstGuards(t *testing.T) {
	t.Cleanup(func() {
		clearPendingStatusRecoveries(true)
	})

	clearPendingStatusRecoveries(true)
	baseNow := time.Now().UnixMilli()

	// 1. Reconnect settle guard blocks peer resends within 15s of connect.
	statusRecoveryMu.Lock()
	statusLastConnectMs = baseNow - 5_000
	statusRecoveryMu.Unlock()
	if ok, _, reason := reserveStatusRecoveryBudget(baseNow); ok || reason != "reconnect_settling" {
		t.Fatalf("expected reconnect_settling block, got ok=%v reason=%q", ok, reason)
	}

	// Once settled (>= 15s), sliding-window budget allows up to statusRecoveryWindowMax requests.
	statusRecoveryMu.Lock()
	statusLastConnectMs = baseNow - 30_000
	statusRecoveryMu.Unlock()
	for i := 0; i < statusRecoveryWindowMax; i++ {
		ok, _, reason := reserveStatusRecoveryBudget(baseNow + int64(i)*4_000)
		if !ok {
			t.Fatalf("expected request #%d to succeed, blocked by %q", i+1, reason)
		}
	}
	if ok, _, reason := reserveStatusRecoveryBudget(baseNow + 30_000); ok || reason != "window_budget_exhausted" {
		t.Fatalf("expected window_budget_exhausted after cap, got ok=%v reason=%q", ok, reason)
	}

	// 2. Reconnect flush cooldown prevents repeated reconnect bursts within 10m.
	clearPendingStatusRecoveries(true)
	if !shouldFlushStatusOnReconnect(baseNow) {
		t.Fatal("expected initial reconnect flush to be allowed")
	}
	if shouldFlushStatusOnReconnect(baseNow + 60_000) {
		t.Fatal("expected reconnect flush within 10m cooldown to be blocked")
	}
	if !shouldFlushStatusOnReconnect(baseNow + int64(statusReconnectFlushCooldown/time.Millisecond) + 1) {
		t.Fatal("expected reconnect flush after 10m cooldown to be allowed")
	}

	// 3. Consecutive error backoff locks recovery after 2 send errors until 10m passes.
	clearPendingStatusRecoveries(true)
	recordStatusRecoveryOutcome(baseNow, errors.New("offline"))
	recordStatusRecoveryOutcome(baseNow+100, errors.New("timeout"))
	if ok, _, reason := reserveStatusRecoveryBudget(baseNow + 5_000); ok || reason != "error_backoff" {
		t.Fatalf("expected error_backoff after 2 consecutive failures, got ok=%v reason=%q", ok, reason)
	}
	recordStatusRecoveryOutcome(baseNow+10_000, nil)
	if ok, _, reason := reserveStatusRecoveryBudget(baseNow + 15_000); !ok {
		t.Fatalf("expected success to reset error backoff, got reason=%q", reason)
	}

	// 4. Resolved status IDs are never re-queued for peer recovery.
	clearPendingStatusRecoveries(true)
	const resolvedID = "already-resolved-status-1"
	resolvedStatusIDs.Store(resolvedID, baseNow)
	scheduleStatusRecovery(&events.UndecryptableMessage{
		Info: types.MessageInfo{
			MessageSource: types.MessageSource{Chat: types.StatusBroadcastJID, Sender: types.NewJID("15550009999", types.DefaultUserServer)},
			ID:            resolvedID,
			Timestamp:     time.UnixMilli(baseNow),
		},
	})
	if _, ok := pendingStatusRecovery.Load(resolvedID); ok {
		t.Fatal("expected resolved status ID not to schedule duplicate recovery")
	}
}

func TestResolveGroupParticipantJID(t *testing.T) {
	lid := types.NewJID("249182736451234", types.HiddenUserServer)
	pn := types.NewJID("15551234567", types.DefaultUserServer)

	t.Run("prefers PhoneNumber when primary JID is LID", func(t *testing.T) {
		got := resolveGroupParticipantJID(nil, nil, types.GroupParticipant{
			JID:         lid,
			LID:         lid,
			PhoneNumber: pn,
		})
		if got != pn {
			t.Fatalf("got %v, want %v", got, pn)
		}
	})

	t.Run("strips device suffix from PN primary JID", func(t *testing.T) {
		pnWithDevice := types.JID{User: "15551234567", Server: types.DefaultUserServer, Device: 4}
		got := resolveGroupParticipantJID(nil, nil, types.GroupParticipant{
			JID: pnWithDevice,
		})
		if got != pn {
			t.Fatalf("got %v, want %v", got, pn)
		}
	})

	t.Run("falls back to non-AD LID when PhoneNumber absent", func(t *testing.T) {
		lidWithDevice := types.JID{User: "249182736451234", Server: types.HiddenUserServer, Device: 2}
		got := resolveGroupParticipantJID(nil, nil, types.GroupParticipant{
			JID: lidWithDevice,
		})
		if got != lid {
			t.Fatalf("got %v, want %v", got, lid)
		}
	})
}

func TestKnownStatusOriginPromotionAndFallback(t *testing.T) {
	t.Cleanup(func() {
		clearPendingStatusRecoveries(true)
	})
	clearPendingStatusRecoveries(true)

	const msgID = "status-origin-msg-1"
	senderPN := types.NewJID("15551230000", types.DefaultUserServer)
	senderLID := types.NewJID("991122334455", types.HiddenUserServer)

	recordKnownStatusOrigin(&types.MessageInfo{
		MessageSource: types.MessageSource{
			Chat:      types.StatusBroadcastJID,
			Sender:    senderPN,
			SenderAlt: senderLID,
			IsGroup:   true,
		},
		ID:        msgID,
		Timestamp: time.Now(),
	})

	// Resend payload arrives keyed to the sender's DM JID instead of status@broadcast
	resentDMInfo := types.MessageInfo{
		MessageSource: types.MessageSource{
			Chat:   senderPN,
			Sender: types.EmptyJID,
		},
		ID: msgID,
	}
	applyKnownStatusOriginHint(&resentDMInfo, true)
	if resentDMInfo.Chat != types.StatusBroadcastJID {
		t.Fatalf("got chat=%v, want %v", resentDMInfo.Chat, types.StatusBroadcastJID)
	}
	if !resentDMInfo.IsGroup {
		t.Fatal("expected promoted status@broadcast MessageInfo to have IsGroup=true")
	}
	if resentDMInfo.Sender != senderPN {
		t.Fatalf("got sender=%v, want %v", resentDMInfo.Sender, senderPN)
	}
	if resentDMInfo.SenderAlt != senderLID {
		t.Fatalf("got senderAlt=%v, want %v", resentDMInfo.SenderAlt, senderLID)
	}
}

func TestPickStatusRecoverySenderPrefersPNFromAlt(t *testing.T) {
	lid := types.NewJID("991122334455", types.HiddenUserServer)
	pn := types.NewJID("15551230000", types.DefaultUserServer)

	if got := pickStatusRecoverySender(nil, nil, lid, pn); got != pn {
		t.Fatalf("got %v, want %v", got, pn)
	}
	if got := pickStatusRecoverySender(nil, nil, lid, types.EmptyJID); got != lid {
		t.Fatalf("got %v, want %v", got, lid)
	}
}

func TestNextStatusRecoveryRetryDelayAndReschedule(t *testing.T) {
	t.Cleanup(func() {
		clearPendingStatusRecoveries(true)
	})
	clearPendingStatusRecoveries(true)

	baseNow := time.Now().UnixMilli()
	statusRecoveryMu.Lock()
	statusLastConnectMs = baseNow - 4_000
	statusSentWindowMs = []int64{baseNow - int64((9*time.Minute)/time.Millisecond)}
	statusRecoveryMu.Unlock()

	if d := nextStatusRecoveryRetryDelay(baseNow, "reconnect_settling"); d < 10*time.Second || d > 20*time.Second {
		t.Fatalf("unexpected reconnect_settling retry delay: %v", d)
	}
	if d := nextStatusRecoveryRetryDelay(baseNow, "window_budget_exhausted"); d < 50*time.Second || d > 75*time.Second {
		t.Fatalf("unexpected window_budget_exhausted retry delay: %v", d)
	}

	const msgID = "reschedule-status-1"
	entry := &statusRecoveryEntry{
		msgID: msgID,
		chat:  types.StatusBroadcastJID,
		tsMs:  baseNow,
	}
	pendingStatusRecovery.Store(msgID, entry)
	reschedulePendingStatusRecovery(entry, 5*time.Second)

	entry.timerMu.Lock()
	hasTimer := entry.timer != nil
	entry.timerMu.Unlock()
	if !hasTimer {
		t.Fatal("expected reschedulePendingStatusRecovery to arm a retry timer")
	}
}

func TestExtractModernStatusTypes(t *testing.T) {
	innerQ, _, _, _, _ := unwrapMessageContainer(&waE2E.Message{
		QuestionMessage: &waE2E.FutureProofMessage{
			Message: &waE2E.Message{Conversation: proto.String("Ask me anything")},
		},
	})
	if innerQ == nil || innerQ.GetConversation() != "Ask me anything" {
		t.Fatalf("failed to unwrap QuestionMessage: %v", innerQ)
	}

	body, mt, _, _, _, _ := extractMessageContent(&waE2E.Message{
		MusicMessage: &waE2E.MusicMessage{
			EmbeddedMusic: &waE2E.EmbeddedMusic{
				Title:  proto.String("Midnight City"),
				Author: proto.String("M83"),
			},
		},
	})
	if mt != "TEXT" || body != "🎵 Midnight City — M83" {
		t.Fatalf("got MusicMessage (%q, %q)", body, mt)
	}

	ptv := &waE2E.VideoMessage{Caption: proto.String("Quick clip"), Seconds: proto.Uint32(4), DirectPath: proto.String("/v/ptv")}
	body, mt, _, dur, _, dl := extractMessageContent(&waE2E.Message{PtvMessage: ptv})
	if mt != "VIDEO" || body != "Quick clip" || dur != 4 || dl != ptv {
		t.Fatalf("got PtvMessage (%q, %q, dur=%d, dl=%v)", body, mt, dur, dl)
	}

	body, mt, _, _, _, _ = extractMessageContent(&waE2E.Message{
		ExtendedTextMessage: &waE2E.ExtendedTextMessage{
			Title:       proto.String("Article Title"),
			MatchedText: proto.String("https://example.com"),
		},
	})
	if mt != "TEXT" || body == "" {
		t.Fatalf("expected link-preview ExtendedTextMessage fallback, got (%q, %q)", body, mt)
	}
}
