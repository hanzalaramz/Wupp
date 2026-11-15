# Changelog

## [Unreleased]

## [0.2.0] - 2026-11-15

Lots of UI fixes, media controls, and day-to-day polish in this update.

### What's new
- Added an auto-download media toggle with separate rules for mobile data, Wi-Fi, and roaming (#12).
- Added global and per-chat toggles to keep downloaded media out of your phone's gallery (#12).
- Added long-press chat selection on the home screen so you can pin, mute, archive, favorite, mark read/unread, or clear chats locally.
- Switched the theme toggle to a four-theme picker with Light, Dark, AMOLED, and Velvet Aurora.
- Reworked the update prompt into a bottom sheet with clean release notes and APK info every time you open the app.

### Fixes & polish
- Fixed chats opening blank in some DMs, groups, and channels.
- Resolved raw `@lid` numbers on highlighted replies and group headers to show actual names and formatted phone numbers instead.
- Added group sender profile pictures beside incoming group messages and fixed tapping profile photos so it opens the full HD viewer instead of jumping into the chat.
- Tightened chat bubble corners and media card framing so photos keep their proportions without cropping.
- You can now swipe horizontally between different people's status stories, and backing out of a chat keeps your place in the home list.
- Clamped channel unread badges at `999+` and cleaned up View-Once pill labels.

## [0.1.0] - 2026-11-15

First public release of Wupp (`com.wupp.app`).

- 🕵️ **Ghost Mode & Privacy:** freeze last seen, hide blue read ticks, hide voice played receipts, hide typing/recording, and customize rules per chat.
- 🧹 **Anti-Delete & Immunity:** keep deleted messages and statuses locally, preserve disappearing messages past timer expiry, and view edit history on edited messages.
- 👁️ **View-Once & Status Saver:** open and save View-Once media and contact statuses straight to your gallery.
- 📣 **Group & Chat Extras:** `@everyone` silent Ghost Mention, swipe-to-reply, emoji reaction bar, live delivery/read ticks, admin-locked group handling, `@newsletter` channels, communities, and dark/light themes.
- 🛡️ **Anti-Ban & Sync Safety:** `Chrome (Mac OS)` fingerprint, `90s` sync cooldowns, jittered media/avatar workers, dead-channel skipping, Error 463 unknown-number outbound guard, and 14-day primary heartbeat tracker.
- 🔗 **Setup & Real-Time Fixes:** mandatory QR or phone link-code onboarding, dual event queues so history sync never drops live messages, plus fixed channel images, profile pictures, and initial statuses.
- 📦 **Multi-ABI Signed Releases & Updater:** `arm64-v8a` and multi-ABI `universal` APKs that update in place without logging out, plus in-app GitHub updates and bug/feedback forms.
