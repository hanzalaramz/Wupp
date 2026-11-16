# Changelog

## [Unreleased]

## [0.3.1] - 2026-11-16

### ✨ What's New
- 😄 **Emoji Picker Dock (#26):** In-app emoji search & keyboard toggle
- 📄 **PDF & Docs (#42, #38):** Page preview cards & native PDF viewer
- 🎬 **Outbound Video (#40):** Video sending, preview dialog & HD mode
- ⭕ **Status Fixes (#39):** Swipe down to close, My Status & composer
- 🗂️ **Groups & Channels (#41):** Separate Groups tab & Hide Channels toggle
- 🗑️ **Anti-Delete Removal (#37):** Confirm Delete for me & device wipe
- 💬 **Quote Jump (#34):** Tap a quoted reply to scroll to the original
- ⚙️ **Ported Settings (#36):** Account privacy, profile, blocklist, alerts & cache
- 🎨 **Classic Theme (#30):** Flat WhatsApp look across bars & chat box

## [0.3.1-pre.1] - 2026-11-16

✨ Outbound video & pre-send preview, native PDF/document viewer, in-app emoji dock, WhatsApp Settings ports, and major status, quote-jump, and delete fixes.

### ✨ Key Changes
- 😄 **Emoji Picker Dock (#26):** Native Android emoji picker with live search and smooth keyboard switching in chat.
- 📄 **PDF & Document Viewer (#42, #38):** First-page PDF previews on document cards, Save to Downloads, and native external/in-app PDF opening.
- 🎬 **Video Send & Aspect-Fit Playback (#40):** Send gallery videos in Original/HD/Compressed modes with pre-send media preview, captions, and aspect-ratio-preserving video playback.
- ⭕ **Status Viewer & Composer (#39):** Fixed status loading states, swipe-down to close, dedicated My Status viewing, and Add Status composer actions.
- 🗂️ **Separate Groups Tab & Hide Channels (#41):** Optional dedicated Groups tab and Hide Channels toggle under Appearance & Looks.
- 🗑️ **Permanent Anti-Delete Removal (#37):** Confirmation prompts for Delete for everyone, Delete for me, and permanently deleting Anti-Delete messages from device.
- 💬 **Tap Quote to Jump (#34):** Tap any quoted reply banner to scroll to and highlight the original message.
- ⚙️ **Ported WhatsApp Settings (#36):** Manage Account Privacy, Display Name & About, Blocked Contacts, Default Timer, Enter-to-send, Chat Font Size, Notification controls, and Storage cache.
- 🎨 **Unified Classic Theme (#30):** Enabling Classic mode now applies the flat WhatsApp look to top search bars, bottom navigation, and chat composer boxes.

## [0.3.0] - 2026-11-16

✨ Material 3 Expressive & Liquid Glass redesign, Local App Lock, Customize Looks, Turkish support, custom reactions, and sync & stability fixes.

### ✨ What's new
- 🎨 **Material 3 Expressive & Liquid Glass UI:** Frosted-glass bottom bar and chat composer, expressive spring motion, Android 16-style segmented Settings cards, and morphing loading/filter controls (Android 8.0+ compatible).
- 🔒 **Local App Lock (#18):** Lock Wupp with biometrics/screen lock or a custom PIN, auto-lock timers, and optional Recents/screenshot hiding.
- 🔐 **Archived & Locked Chats (#20):** Preserves local archive state, supports synced locked chats behind App Lock, and adds an Archived banner row on Home.
- 🎛️ **Customize Looks (#16):** Toggles for Vertical Status Layout, Always Show Cached Bio, Classic Navigation, Compact Chat List, Seconds in Timestamps, Hide Camera Shortcut, and Hide Stealth Badges.
- 🖼️ **Per-Chat Wallpapers & Framing (#21):** Set per-chat backgrounds and pinch, drag, zoom, and reset fit for wallpapers.
- 😄 **Custom Quick Reactions (#24):** Personalize all 7 quick-reaction emojis in Settings.
- 🌍 **Turkish Language & System Font (#17, #14):** Added Turkish (`Türkçe`) translations, an in-app language picker, and a Use System Font option.
- 🗑️ **Two-Way Chat Delete:** Deleting a chat syncs with your primary WhatsApp, and phone deletes sync back.

### 🛠️ Fixes
- ⚡ **24/7 Sync Reliability (#22):** Improved timeout reconnect, boot restore, and background sync watchdog for reliable real-time updates.
- 📦 **In-App Updater (#23):** Switched APK installation to Android PackageInstaller with cleaner fallback handling.
- 👻 **Empty `@lid` & Phantom Chats:** Cleaned up unmapped random-number chats stuck in the chat list and unread count.
- 👤 **Status Names & Battery Settings:** Fixed contact names on status cards/stories, removed extra alert clutter, and fixed the Unrestricted Battery settings shortcut.

## [0.3.0-pre.2] - 2026-11-16

✨ App Lock, archived & locked chat fixes, Turkish language support, Customize Looks, and sync/updater improvements.

### ✨ What's new
- 🔒 **Local App Lock (#18):** Lock Wupp with Device Lock (biometrics or screen lock) or a custom PIN, auto-lock timers, and optional Recents/screenshot hiding.
- 🔐 **Archived & Locked Chats (#20):** Keeps local archive state synced properly, supports locked chats behind App Lock, and adds an Archived banner row on Home.
- 🎛️ **Customize Looks (#16):** Added toggles for Vertical Status Layout, Always Show Cached Bio, Classic Navigation, Compact Chat List, Seconds in Timestamps, Hide Camera Shortcut, and Hide Stealth Badges.
- 🌍 **Turkish Language (#17):** Added Turkish (`Türkçe`) translations alongside an in-app language picker.
- 🔤 **Use System Font (#14):** Switch between Wupp's font and your Android system font in Settings.
- 🖼️ **Wallpaper Zoom & Framing (#21):** Pinch, drag, zoom, and reset fit for global and per-chat wallpapers.
- 😄 **Custom Quick Reactions (#24):** Personalize all 7 quick-reaction emojis in Settings.
- 🧩 **Settings & UI Polish:** Segmented Settings layout with refreshed controls and motion.

### 🛠️ Fixes
- ⚡ **Background Sync Recovery (#22):** Improved timeout reconnect, boot restore, and background sync watchdog for reliable real-time updates.
- 📦 **In-App Updater (#23):** Improved direct APK installation reliability with cleaner fallback handling.

## [0.3.0-pre.1] - 2026-11-16

✨ Material 3 Expressive upgrade across Wupp — with full Android 8.0+ compatibility intact.

### ✨ What's new
- 🎨 **Material 3 Expressive Theme & Motion:** Upgraded Wupp to the latest Material 3 Expressive (`1.5.0-rc01` / Compose BOM `2026.09.00`) with expressive spring physics (`MotionScheme.expressive()`) and rounded shapes across all themes.
- 🌀 **Expressive Loading & Controls:** Added morphing M3 Expressive loading indicators (`ContainedLoadingIndicator` & `LoadingIndicator`), connected toggle groups on device linking, shape-morphing chat filter chips, and an expandable Updates status FAB menu.
- 🫧 **Springy Liquid Glass Nav:** Upgraded the YouTube-style frosted Liquid Glass bottom bar with smooth expressive spring transitions and subtle active icon bounce.
- 📦 **Compatibility Preserved:** Devices running older Android versions (**Android 8.0 / API 26+** and up) continue working with zero dropped support.

## [0.2.3-pre.2] - 2026-11-15

✨ True frosted Liquid Glass blur and complete cleanup of empty random-number chats.

### ✨ What's new
- 🫧 **True Liquid Glass Blur:** Real-time frosted blur behind the floating nav bar and chat composer on Android 12+, with a cleaner borderless active pill and sharper icons.

### 🛠️ Fixes
- 👻 **Empty Random-Number Chats:** Fixed the default type bug that kept old empty `@lid` chats stuck in your chat list and unread counter.

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
