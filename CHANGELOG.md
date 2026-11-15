# Changelog

## [Unreleased]

## [0.2.3-pre.1] - 2026-11-15

✨ Liquid Glass UI polish, per-chat wallpapers, synced chat deletes, and status/settings fixes.

### ✨ What's new
- 🪟 **Liquid Glass UI:** Floating frosted-glass navigation bar, glass search & chat composer chrome, edge-to-edge scrolling, and a cleaner single-line `Communities` tab.
- 🎨 **Per-Chat Wallpapers:** Set a custom background for any specific chat while keeping your global wallpaper everywhere else.
- 🗑️ **Two-Way Chat Delete:** Deleting a chat in Wupp syncs with your primary WhatsApp (and deletes from your phone sync back).

### 🛠️ Fixes
- 👤 **Status Contact Names:** Fixed status cards and full-screen stories showing raw phone numbers instead of saved contact names.
- 👻 **Phantom Number Chats:** Cleaned up empty unmapped `@lid` chats that didn't appear on official WhatsApp.
- 🔋 **Settings & Battery Fix:** Removed the extra *Incoming Message Alerts* row and fixed *Unrestricted Battery (24/7 Sync)* opening the wrong settings page.

## [0.2.2] - 2026-11-15

Chat auto-scroll fix, clearer background notification hiding UX, and battery/installer polish.

### What's new
- Added a **1-tap Hide Persistent Sync Banner** guide dialog and an **Unrestricted Battery (24/7 Sync)** status row in Settings.
- Added a floating scroll-to-bottom button when scrolled up inside a chat.
- Added a reassurance hint for Google Play Protect's scan prompt on the in-app updater sheet.

### Fixes & polish
- Fixed chat timelines staying static when sending or receiving new messages so they now auto-scroll to the latest message like WhatsApp.
- Lowered the persistent sync notification channel priority (`IMPORTANCE_MIN`) and rewrote its description and status copy in plain English without affecting 24/7 sync or incoming message alerts.

## [0.2.1] - 2026-11-15

Bug fixes for chat deduplication, message ordering, reactions, background reliability, custom chat backgrounds, and pre-release update testing.

### What's new
- Added **Custom Chat Background** picker with built-in wallpapers, gallery image import, dimness slider, and doodle toggle.
- Added **Notifications & Background Sync** controls in Settings (hide the persistent status notification channel and request unrestricted battery optimization for reliable real-time sync).
- Added background message notifications, boot restart, and companion app notices (#13).
- Added opt-in **Receive Pre-release Updates** toggle in Settings so testers can preview GitHub pre-releases in-app before public rollout.

### Fixes & polish
- Fixed duplicate DM chat threads caused by separate `@lid` and phone JIDs by canonicalizing and merging chat aliases.
- Fixed same-second message ordering swaps across history sync and live messages.
- Fixed missing or out-of-order emoji reactions during history sync and live arrival.

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
