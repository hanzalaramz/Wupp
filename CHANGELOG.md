# Changelog

## [Unreleased]

## [0.3.5-pre.7] - 2026-11-20

### 🛠️ Fixes
- 📞 **Calls Pairing:** Fixed clicking `"Link with phone number instead."` using native touchscreen events and stabilized pairing status updates.

## [0.3.5-pre.6] - 2026-11-19

### 🛠️ Fixes
- 📞 **Calls Pairing:** Fixed phone number input detection and button clicking during 8-digit WhatsApp Web pairing.

## [0.3.5-pre.5] - 2026-11-19

### 🛠️ Fixes
- 📞 **Calls Pairing:** Fixed the 8-digit pairing code setup getting stuck while initializing the calling engine.

## [0.3.5-pre.4] - 2026-11-19

### ✨ Features
- 📞 **Calls Tab & Calling (Beta):** Added a Calls tab with one-time 8-digit device linking, Always Ready mode, and native-style voice and video calls.

## [0.3.5-pre.3] - 2026-11-19

### 🛠️ Fixes
- 📱 **Contact Statuses:** Fixed new contact statuses failing to appear after recovery or arriving in newer status formats.
- 🕒 **Deletion Time (#62):** Added the exact deletion time to deleted text, images, videos, voice notes, stickers, and statuses.

## [0.3.5-pre.2] - 2026-11-19

### 🛠️ Fixes
- 🎙️ **Voice Notes:** Fixed recorded voice messages failing to play in WhatsApp with standard Ogg/Opus voice encoding.
- 📱 **Own Status Sync:** Added recovery for your own status uploads and fixed missing timestamps hiding stories.

## [0.3.5-pre.1] - 2026-11-19

### 🛠️ Fixes
- 🎙️ **Voice Notes:** Fixed recorded voice messages failing to open in WhatsApp by enforcing valid `OGG/OPUS` mono audio and container checks.
- 📱 **Status & Story Sync:** Unwrapped newer status formats, surfaced Channel stories in Status, fixed history/resend sender parsing, and added low-risk capped recovery for undecryptable statuses.
- 👥 **Group Info Contacts (#82):** Resolved participant `PhoneNumber ↔ LID` mappings so selecting a group member shows and messages their real WhatsApp number instead of an `@lid`.

## [0.3.4] - 2026-11-19

### ✨ Features
- ↪️ **Forward Messages (#81):** Forward texts, media, documents, and stickers to up to 5 chats or groups from the long-press bar.
- 💬 **Notification Quick Reply (#75):** Reply and mark messages as read directly from incoming notifications.
- 👤 **Local Contact Name Fallback (#71):** Show saved phonebook names when no name comes from the server, plus cleaner Group Info member counts.
- 📸 **Allow Screenshots Toggle (#60):** Toggle app screenshots on or off in Security Settings while keeping Android 13+ Recents privacy intact.
- 🎯 **More Long-Press Actions (#59):** Show Pin, Edit, Save, Open, and Info icons directly in the selection bar.
- ✈️ **Live / Offline Mode (#58):** Tap the header status pill or Settings switch to pause WhatsApp sync without disabling internet.
- 🕒 **Clock Format Option (#57):** Choose between device default, 24-hour, or 12-hour message timestamps.
- 🎞️ **Native GIF Playback (#55):** Send MP4 GIF clips with native looping WhatsApp playback.

### 🛠️ Fixes
- 🎞️ **Animated Stickers (#85):** Play animated WebP and Lottie `.was` stickers in chats and the sticker picker.
- 📦 **APK & File Downloads (#61):** Preserve filenames with captions, fix `.apk` exports to `Downloads/Wupp`, raise download size limit, and surface clear errors.
- 🔐 **Undecryptable & Business Message Recovery (#86):** Auto-retry failed decrypts cleanly, filter silent protocol noise, and decode interactive/template business messages.

## [0.3.3] - 2026-11-17

### ✨ Features
- 📑 **Native WhatsApp-Style Emoji, GIF & Sticker Dock:** Full visual and functional parity with official WhatsApp featuring a centered 3-segment stadium pill switcher `[ 😊 | GIF | 📑 ]`, reaction tag chips, search, and edit controls.
- 📜 **Continuous 5-Column Sticker Feed:** Unified scrolling sticker grid with green circular "Create sticker" action tile, recent stickers, favorites section, and Cuppy built-in pack.
- 🧭 **Sticker Pack Bar & Smooth Section Jump:** Bottom switcher `[ 🕒 Recent | ⭐ Favorites | ☕ Cuppy | ➕ Add ]` with active circular pill highlight and smooth section scrolling.
- ↕️ **Vertical Stretch Gesture:** Drag top dock handle smoothly up to ~74% screen height over chat messages while keeping the composer bar and keyboard visible.
- ⭐ **Sticker Favorites & 1-Tap Creation:** Star/unstar stickers via long-press and convert any photo into a custom transparent sticker in 1 tap.
- ☕ **Built-in Cuppy Sticker Pack:** High-resolution transparent Cuppy coffee cup sticker pack rendered programmatically at runtime.
- 📌 **Pinned Messages (#53):** Two-way sync for pinned messages with sticky top banner, 24h / 7d / 30d durations, tap-to-scroll, and unpin actions.
- 🎨 **Photo & Media Editor (#32):** Native WhatsApp-style editor before sending with freehand doodle pen, text overlays, stickers, rotation, and color filters.
- 🧭 **Fluid Navigation Animation (#67):** Liquid stretch-and-settle jelly physics for floating bottom navigation capsule.
- 💬 **Status Replies & Realtime Reactions (#25):** Reply directly to contact stories/statuses with inline composer and send genuine emoji reactions.
- 👤 **Dedicated Contact & Group Info Screen (#72):** Tapping avatar/profile popup navigates to dedicated Contact & Group Info screen with member roster, admin badges, search, and shared media/docs/links browser.
- ⚙️ **Granular Thread Settings:** Toggle chat lock, notification mute, custom wallpaper, per-thread privacy, and clear chat.

### 🛡️ Security & Privacy Patches
- 👻 **Ghost Mode Outbound Activity Guard:** Implemented dynamic presence unfreezing (`PresenceAvailable`) before any outbound activity (messages, media uploads, reactions, status replies, pinned messages) and automated refreeze (`PresenceUnavailable` after 1.5s delay). Protects against server heuristics detecting messages from offline clients. Added user confirmation prompt toggle in Settings under Privacy & Ghost Mode.
- 👁️ **Native View-Once Compliance:** Removed all anti-view-once bypass and vaulting logic. Incoming View-Once messages now strictly mirror native WhatsApp companion clients with `"View once · Open on phone"` and `"For added privacy, you can only open this view once message on your phone"` toast notification.

### 🛠️ Fixes
- 🎙️ **Native Voice Notes / PTT (#79, #66):** Fixed voice recordings being delivered as generic audio or PDF-like document attachments on recipient devices. Enforced outbound `whatsmeow.MediaAudio` dispatch, native Android Ogg Opus (`audio/ogg; codecs=opus`) encoding on API 29+ (with M4A fallback on Android 8/9), `PTT: true`, and 64-bar speech waveform so recipient WhatsApp clients display the native green voice note waveform player bubble.
- ⌛ **24-Hour Status Expiration (#65):** Filter expired status updates (`timestamp < System.currentTimeMillis() - 86400000L`) from the Updates feed, story viewer, and ViewModel state flow. Automatically sweep and purge expired status database records and their orphaned media cache files on service launch and status updates.
- 👥 **Group Chat Previews (#56):** Display sender name prefix (`<Sender>: <message>`) for incoming messages in group chats and community subgroups, while preserving delivery status indicators on user-sent messages and enabling group sender search matching.
- 🖼️ **Sticker Message Rendering:** Fixed sent and received stickers rendering as blank or wrapped in chat bubbles; now rendered with transparent aspect-fit background directly on chat canvas.

## [0.3.3-pre.5] - 2026-11-17

### ✨ Features
- 📑 **Native WhatsApp-Style Emoji, GIF & Sticker Dock:** Full visual parity with official WhatsApp featuring a centered 3-segment stadium pill switcher `[ 😊 | GIF | 📑 ]`, reaction tag chips, search, and edit controls.
- 📜 **Continuous 5-Column Sticker Feed:** Unified scrolling sticker grid with green circular "Create sticker" action tile, recent stickers, favorites section, and Cuppy built-in pack.
- 🧭 **Sticker Pack Bar & Smooth Jump:** Bottom switcher `[ 🕒 Recent | ⭐ Favorites | ☕ Cuppy | ➕ Add ]` with active circular pill highlight and smooth section scrolling.
- ↕️ **Vertical Stretch Gesture:** Drag top handle to expand dock smoothly up to ~74% screen height over chat messages while keeping composer bar visible.
- ⭐ **Sticker Favorites & 1-Tap Creation:** Star/unstar stickers via long-press and convert any photo into a custom transparent sticker in 1 tap.
- ☕ **Built-in Cuppy Pack:** High-resolution transparent Cuppy coffee cup sticker pack rendered programmatically at runtime.

### 🛠️ Fixes
- 🎙️ **Native Voice Notes / PTT (#79, #66):** Fixed voice recordings being delivered as generic audio or PDF-like document attachments on recipient devices. Enforced outbound `whatsmeow.MediaAudio` dispatch, native Android Ogg Opus (`audio/ogg; codecs=opus`) encoding on API 29+, `PTT: true`, and 64-bar speech waveform so recipient WhatsApp clients display the native green voice note waveform player bubble.
- 🖼️ **Sticker Message Rendering:** Fixed sent and received stickers rendering as blank or wrapped in chat bubbles; now rendered with transparent aspect-fit background directly on chat canvas.

## [0.3.3-pre.4] - 2026-11-17

### ✨ Features
- 📑 **Expandable Sticker & Emoji Dock:** Bottom sheet dock with drag-to-stretch handle, smooth keyboard switching, and favorites management.
- ☕ **Cuppy Sticker Pack:** Built-in programmatic Cuppy coffee cup stickers with instant send and preview.
- ⭐ **Favorite Stickers Store:** Persistent storage for favorite stickers with long-press context options.

## [0.3.3-pre.3] - 2026-11-17

### ✨ Features & Fixes
- 📑 **Sticker Tab & Picker:** Added dedicated sticker picker dock in chat.
- 🖼️ **Sticker Chat Bubbles:** Fixed received stickers showing as blank messages; enabled aspect-fit sticker rendering.

## [0.3.3-pre.2] - 2026-11-17

### ✨ Features
- 📌 **Pinned Messages (#53):** Two-way sync for pinned messages with sticky top banner, 24h/7d/30d durations, tap-to-scroll, and unpin actions
- 🎨 **Photo Editor (#32):** Native WhatsApp-style editor before sending with freehand doodle pen, text overlays, stickers, rotation, and color filters
- 🧭 **Fluid Nav Animation (#67):** Liquid stretch-and-settle jelly physics for floating bottom navigation pill
- 💬 **Status Replies & Reactions (#25):** Reply directly to contact statuses with inline composer and send real emoji reactions

### 🛠️ Fixes
- 👥 **Group Chat Previews (#56):** Display sender name prefix (`<Sender>: <message>`) for incoming messages in group chats and community subgroups, while preserving delivery status indicators on user-sent messages and enabling group sender search matching.

## [0.3.3-pre.1] - 2026-11-17

### 🛠️ Fixes & Features
- 👤 **Contact & Group Info (#72):** Fixed DP popup `(i)` button opening own settings; now opens dedicated Contact & Group Info screen
- 👥 **Group Members & Admins:** Group Info displays full participant list with search filter and green Group Admin badges
- 🖼️ **Media, Links & Docs:** Dedicated Media tab with item count, storage usage calculator, and horizontal media carousel
- ⚙️ **Chat Settings:** Toggle chat lock, notifications mute, custom wallpaper, per-thread privacy, and clear chat

## [0.3.2] - 2026-11-16

### ✨ Features
- 🫧 **Kyant0 Liquid Glass:** Optical lens refraction, vibrancy & specular rim across UI
- 🧭 **Scrubbable Nav Capsule:** Spring-animated glass pill with horizontal scrub gesture
- 💬 **Chat Peek Popup (#49):** Long-press chats for a blurred live preview & quick actions
- 👉 **Swipe Navigation (#48):** Swipe horizontally across home bottom-bar categories
- 🎨 **Custom Accent Colors (#33):** Pick custom accent swatches across all themes
- 👁️ **Send View-Once Media (#52):** Toggle View-Once (`1`) before sending photos & videos
- 🖼️ **Swipe Chat Gallery (#28):** Swipe between chat photos & videos in full-screen viewer
- 🔎 **In-Chat Search Stepper (#29):** Jump between older & newer search matches in chat
- 🔕 **Two-Way Mute Sync (#45):** Sync mute changes with WhatsApp & stored chat settings
- 🔄 **Always-On Update Check:** Automatic GitHub update checks enabled by default

### 🛠️ Fixes
- 🖼️ **Media Viewer Crash:** Fixed `IndexOutOfBoundsException` when opening photos/videos
- 😀 **Reactions (#27):** Per-user toggle/replace so reactions can be deselected cleanly
- 📸 **Media Long-Press (#51):** Long-pressing photos, videos & quotes opens action menu
- 🔢 **Unread Sync (#43):** Clear unread count when replying from primary WhatsApp
- 🔡 **Sentence Caps (#47):** Enabled sentence capitalization by default in chat composer
- 🖌️ **Wallpaper Drag (#31):** Smooth drag & pinch-to-zoom without gesture resets
- 🧹 **Legacy Glass Removed:** Replaced old frosted helpers while preserving Classic mode

## [0.3.2-pre.4] - 2026-11-16

### 🛠️ Bug Fixes
- 🖼️ **Media Viewer Crash Fix:** Fixed `IndexOutOfBoundsException` when opening photos or videos and synced initial gallery page

## [0.3.2-pre.3] - 2026-11-16

### ✨ What's New
- 🎨 **Custom Accent Colors (#33):** Pick custom accent swatches across all themes
- 👁️ **Send View-Once Media (#52):** Toggle View-Once (`1`) before sending photos & videos
- 🖼️ **Swipe Chat Gallery (#28):** Swipe through chat photos & videos in full-screen viewer
- 🔎 **In-Chat Search Stepper (#29):** Jump between older & newer search matches in chat
- 🔕 **Mute & Read Sync (#43, #45):** Two-way mute sync & unread reset when replying on phone
- 🛠️ **Reactions & Media Fixes (#27, #31, #47, #51):** Reaction toggle/replace, media long-press, sentence caps & smooth wallpaper drag
- 🔄 **Always-On Update Check:** Automatic GitHub update checks enabled by default

## [0.3.2-pre.2] - 2026-11-16

### ✨ What's New
- 💬 **Chat Peek Popup (#49):** Long-press a chat for a live preview & Liquid Glass actions
- 👉 **Swipe Navigation (#48):** Swipe horizontally between Chats, Groups, Updates, Communities & Settings
- 🫧 **Blurred Peek Backdrop:** Smooth iOS-style background blur & spring animation while peeking

## [0.3.2-pre.1] - 2026-11-16

### ✨ What's New
- 🫧 **Kyant0 Liquid Glass:** Optical lens refraction, vibrancy & specular rim across glass UI
- 🧭 **Scrubbable Nav Capsule:** Spring-animated optical lens pill with horizontal scrub gesture
- 💬 **Refractive Chat Overlays:** Liquid Glass composer, reply banner, reactions, tray & scroll button
- 🧹 **Legacy Glass Removed:** Replaced all custom frosted glass helpers while preserving Classic mode

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
- 🧹 **Local Archive & Revision History:** keep messages and statuses locally, preserve disappearing messages past timer expiry, and view edit history on edited messages.
- 👁️ **Media Vault & Status Saver:** open and save incoming media and contact statuses straight to your gallery.
- 📣 **Group & Chat Extras:** `@everyone` silent Ghost Mention, swipe-to-reply, emoji reaction bar, live delivery/read ticks, admin-locked group handling, `@newsletter` channels, communities, and dark/light themes.
- 🛡️ **Connection & Sync Safeguards:** `Chrome (Mac OS)` fingerprint, `90s` sync cooldowns, jittered media/avatar workers, dead-channel skipping, Error 463 unknown-number outbound guard, and 14-day primary heartbeat tracker.
- 🔗 **Setup & Real-Time Fixes:** mandatory QR or phone link-code onboarding, dual event queues so history sync never drops live messages, plus fixed channel images, profile pictures, and initial statuses.
- 📦 **Multi-ABI Signed Releases & Updater:** `arm64-v8a` and multi-ABI `universal` APKs that update in place without logging out, plus in-app GitHub updates and bug/feedback forms.
