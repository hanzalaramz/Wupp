package whatsbridge

import (
	"sync/atomic"
	"testing"
	"time"

	"go.mau.fi/whatsmeow/proto/waE2E"
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
