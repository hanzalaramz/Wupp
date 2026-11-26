# Changelog

## [0.3.10-pre.8] - 2026-11-26

### Improved
- Updated Home, Groups, Channels, and Communities to use the same translucent selection menu and top bar as chat messages.
- Enabled direct multi-select by tapping additional messages, chats, or channels instead of tapping a separate select-multiple button.

## [0.3.10-pre.7] - 2026-11-26

### Fixed
- Fixed a crash during video calls when the local camera preview updated or reopened while a capture session was closing.

## [0.3.10-pre.6] - 2026-11-26

### ✨ Features
- Added a setting to choose what tapping the self-preview window does during a video call (`Swap Video Layout` like WhatsApp by default, or `Flip Camera`), plus quick controls to swap views or rotate peer video in the call menu (#188).
- Added automatic crash log capture and a **Diagnostics & Crash Logs** viewer in Settings to save log files to Downloads or share them with developers (#163).

### 🛠️ Fixes
- Fixed video calls showing rotated, stretched, or black-barred feeds by stabilizing RTP keyframe orientation, encoding upright 9:16 video with center-crop fill, and rendering full-screen edge-to-edge video (#188).

## [0.3.10-pre.5] - 2026-11-26

### ✨ Features
- Fixed location messages so they render as rich map preview cards with place names and addresses, and open directly in Maps when tapped (#184).
- Added location sharing to the attachment menu using device GPS or address search / pasted coordinates (#184).
- Added WhatsApp-style QR code contact sharing (`My code`) and live camera or photo scanning (`Scan code`) plus a `New contact` shortcut in New Chat and Settings (#166).
- Made call log bubbles interactive so tapping starts a matching voice or video callback right away, and long-pressing offers Call back or View call info (#186).

## [0.3.10-pre.4] - 2026-11-26

### ✨ Features
- Added a Pause/Resume button and a View Once toggle directly inside the voice note recorder.
- Added a quick profile action sheet when tapping any group member's name or photo, with one-tap Call, Video, Message, Save contact, and View photo shortcuts.
- Added reaction counts directly on group message reaction badges so multiple reactions show cleanly at a glance.

## [0.3.10-pre.3] - 2026-11-26

### ✨ Features
- Redesigned chat headers, composer bar, and buttons as smooth borderless frosted glass with group member names under the group title.
- Rebuilt the `+` attachment menu as a clean dark grid with Gallery, Camera capture, Document, Contact, Audio, Stickers, Wallpaper, Privacy, Schedule message, and HD quality controls.
- Added tap-to-view reaction details showing who reacted to a message, with quick emoji filtering and tap-to-remove for your own reaction.
- Added bubble-anchored floating emoji reactions and frosted message options popups, an `HD` badge on high-res videos and photos, and quick-forward buttons beside media messages.

### 🛠️ Fixes
- Fixed video previews showing solid black frames by probing non-black keyframes and software bitmaps cleanly.
- Auto-adjusted photo and video bubble widths and heights to fit their real aspect ratio, and fixed stretched quoted video thumbnails.
- Upgraded voice note waveforms to show natural speech peaks from audio packet energy instead of flat bars, and made message timestamps and durations larger and easier to read.
- Improved dark and AMOLED chat bubble contrast, bottom bubble tails, and quoted media/view-once icons.

## [0.3.10-pre.2] - 2026-11-26

### ✨ Features
- Added Classic, iOS-inspired, and Compact Home layouts with an original rounded Wupp emblem and per-layout presets.
- Added a live-preview Home appearance menu to customize accent colors, unread badges, typography, chat row density, section order, navigation bar, New Chat button, and reduced motion.

## [0.3.10-pre.1] - 2026-11-26

### ✨ Features
- Added an unread chats count pill on the chat back button, a green admin badge in announcement-only groups, and a subtle read-receipt toast above the composer.
- Replaced the composer smiley button with a clean sticker icon.

### 🛠️ Fixes
- Fixed quoted replies stretching across the screen so short quotes wrap neatly around their text with darker inset contrast.
- Slimmed chat bubble tails, padding, and cluster spacing, and darkened AMOLED doodles and header glass buttons for sharper contrast.

## [0.3.9] - 2026-11-26

### ✨ Features
- Saved unsent message drafts per chat across app restarts and screen exits, with a green "Draft:" preview in the chat list (#156).
- Added multi-message selection in chat with bulk Copy, Forward, and Delete actions, plus a direct Message info (ⓘ) button and real sent, delivered, read, and played timestamps (#164).
- Added manual "Mark viewed" for statuses so contacts only see you viewed their status after you tap it (#161).
- Added scheduled and recurring messages with repeat options and dedicated management (#144).
- Added per-chat media auto-download overrides in Chat Info (#145).
- Added in-app contact picker and native WhatsApp contact cards with direct message and add-contact shortcuts (#146).
- Set pure black AMOLED as the default theme and redesigned Home tabs, chat headers, composer, voice waveforms, media sending, Settings, Contact Info, and linking screens with rounded glass controls.

### 🛠️ Fixes
- Fixed incoming voice calls having no audio and dropping on the first attempt, plus video-upgrade pipeline binding and call UI cleanups (#155).
- Fixed voice notes cutting off when scrolling or leaving a chat, and added background playback with notification controls (#159).
- Fixed Error 463 Guard blocking saved contacts, phonebook contacts, and alias-linked chats, and replaced the composer lock with an unsaved-number confirmation warning (#143, #154, #157).
- Fixed contact sharing sending raw `.vcf` files instead of native WhatsApp contact cards.
- Fixed per-chat media auto-download rules, removed duplicate scheduler menu items, and improved scheduled message pacing.

## [0.3.9-pre.10] - 2026-11-26

### 🛠️ Fixes
- Fixed incoming voice calls having no audio and dropping on the first attempt by making audio focus/mode setup synchronous, retrying AudioRecord/AudioTrack initialization, and not blocking call answer on Bluetooth permission (#155).
- Fixed video-upgrade pipeline binding, immediate redialing after a failed call, and cleaned up duplicate audio-route items in the call controls menu.

## [0.3.9-pre.9] - 2026-11-26

### ✨ Features
- Added multi-message selection in chat — long-press any message, then tap more to select them all at once, with bulk Copy, Forward, and Delete actions in the top bar (#164).
- Moved the Message info (ⓘ) button directly onto the top bar for every selected message so you no longer have to dig into the overflow menu to see it.
- Message info now shows the real time a message was delivered, read, and played — not just a status label.

## [0.3.9-pre.8] - 2026-11-26

### ✨ Features
- Enabled the Message info option for all 1-on-1 and group messages with delivery, read, and played details.

### 🛠️ Fixes
- Fixed Error 463 Guard blocking saved contacts, phonebook contacts, and alias-linked chats (#154).
- Replaced the Error 463 composer lock with an unsaved-number confirmation warning so users can message new numbers directly (#143, #157).

## [0.3.9-pre.7] - 2026-11-26

### 🛠️ Fixes
- Fixed voice notes cutting off when scrolling or leaving a chat, and added background playback with notification controls (#159).

## [0.3.9-pre.6] - 2026-11-26

### ✨ Features
- Added a manual "Mark viewed" button to the status viewer so contacts only see you viewed their status after you tap it (#161).

## [0.3.9-pre.5] - 2026-11-25

### ✨ Features
- Set pure black AMOLED as the default theme across launch, chats, and settings.
- Redesigned chat headers, grouped settings cards, Contact Info, and linking screens with rounded glass controls.
- Rebuilt the chat composer with left attach, inside emoji button, embedded reply thumbnails, and a live 2-row voice recorder.
- Added drag-scrubbing voice note waveforms with contact avatars and automatic speed pill switching during playback.
- Updated photo and video sending with circular top tools, integrated view-once toggle inside the caption bar, and updated lightbox viewers.

## [0.3.9-pre.4] - 2026-11-25

### ✨ Features
- 🎨 Redesigned Home tabs (**Chats**, **Updates**, **Calls**, and **You**) with a native iOS-style header, inline search bars, circular quick actions, and a centered **You** profile hero while keeping Wupp's tab order.
- ⚙️ Kept Settings accessible from both the **You** tab and the top-left `⋯` overflow menu.

## [0.3.9-pre.3] - 2026-11-24

### ✨ Features
- Added in-app contact picker allowing contact selection without leaving the chat.
- Added native WhatsApp contact cards with direct message and add-contact shortcuts.

### 🛠️ Fixes
- Fixed contact sharing sending raw .vcf document files instead of native WhatsApp contact cards.

## [0.3.9-pre.2] - 2026-11-24

### ✨ Features
- Added contact sharing from the attachment menu with full contact details and card preview (#146).

### 🛠️ Fixes
- Fixed per-chat media auto-download to respect custom chat rules and download all media types (#145).
- Removed duplicate scheduler option in the chat overflow menu.
- Improved scheduled message reliability by requiring an active connection and pacing outbound messages.

## [0.3.9-pre.1] - 2026-11-24

### ✨ Features
- Added scheduled and recurring messages with repeat options and dedicated management (#144).
- Added per-chat media auto-download overrides in Chat Info (#145).
- Added contact sharing from the attachment menu and contact card preview (#146).
- Moved the message scheduler to the chat menu and long-press on Send.

### 🛠️ Fixes
- Fixed per-chat media auto-download to respect custom chat rules and download all media types (#145).

## [0.3.8] - 2026-11-23

> **Note:** Video calls are still **Beta / Unstable**.

### ✨ Features
- 👥 Added a multi-account switcher.
- ⚙️ Redesigned Settings with search, category pills, and a font-size slider.
- 🔒 Added a Secret Code to hide chats and silence their alerts (#128).
- 📱 Added an optional iOS Style light theme (#99).
- 📤 Added Android share target support (#80).
- ⭐ Tap any sticker to add or remove it from favorites (#84).
- 🔊 Added a toggle to mute chat sound effects (#92).
- 🎞️ Added animated GIF playback (#83).
- 🎨 Refreshed the call screen layout.

### 🛠️ Fixes
- 💥 Fixed an immediate launch crash and added a 32-bit device guard (#100).
- 🔄 Fixed saved logins resetting after updates.
- 📞 Fixed group calls showing as 1:1 chats and improved call audio routing.
- 📹 **Video Calls (Beta / Unstable):** Improved codec fallbacks, latency, and camera rotation (#127, #130).
- 💬 Fixed unread chats not scrolling to the oldest unread message (#90).
- 📇 Fixed contact name fallback and added phonebook name priority (#124, #126).
- 🖼️ Fixed media viewer reactions, background pause, and retries (#118).
- 🔔 Hid notification previews for locked chats and App Lock.
- 🎨 Fixed theme button contrast and Meta AI's display name (#97).

## [0.3.8-pre.6] - 2026-11-23

### 🛠️ Fixes
- 📞 Fixed incoming group calls showing as 1:1 chats instead of linking to the group.
- 📹 Fixed outgoing video calls appearing rotated sideways when the camera already handles sensor orientation.

### ✨ Features
- 🎨 Redesigned the call screen with a cleaner top bar, rounded self-view, and single-row floating control dock.

## [0.3.8-pre.5] - 2026-11-23

### 🛠️ Fixes
- 💥 Fixed an immediate crash on launch caused by a startup initialization order bug.
- 🛡️ Prevented background sync services from starting on unsupported 32-bit devices.
- 📤 Improved external app sharing to detect mixed file types and rich text reliably.
- 🔄 Fixed saved logins resetting or prompting to re-link after app updates.
- 📹 Added H.264 encoder/decoder fallbacks for budget devices and lowered voice call latency.
- 💬 Fixed opening unread chats to scroll straight to the oldest unread message.
- 🎨 Fixed button and badge contrast across all themes and enlarged small chat buttons.
- 📇 Added toggles to disable phonebook fallback or prioritize your saved contact names.

### ✨ Features
- 🔒 Added a Secret Code for hidden chats with silenced notifications and calls until unlocked (#128).
- 📱 Added an optional iOS Style light theme under Settings → Theme (#99).
- 📤 Added Android share target support to share media, files, and text into Wupp (#80).
- ⭐ Tapping a sticker now lets you add or remove it from favorites (#84).
- 🔊 Added a Conversation Sounds toggle under Alerts settings to mute chat sound effects (#92).
- 🎞️ Added animated GIF playback in chats and the full-screen media viewer (#83).

## [0.3.8-pre.4] - 2026-11-23 (pulled)

### ✨ Features
- 🔒 Added a Secret Code for hidden chats so they stay invisible in Chats, Updates, search, badges, and Calls with notifications and incoming calls silenced until unlocked (#128).
- 📱 Added an optional iOS Style light theme under Settings → Theme (#99).
- 📤 Added Android share target support so you can share photos, videos, audio, documents, and text from other apps into Wupp (#80).
- ⭐ Tapping a received sticker now opens a preview dialog to add or remove it from favorites (#84).
- 🔊 Added a Conversation Sounds switch under Alerts settings to mute incoming and outgoing message effects (#92).
- 🎞️ Added animated GIF decoding via Coil in chat bubbles and the full-screen media viewer (#83).

### 🛠️ Fixes
- 🔄 Fixed saved logins incorrectly resetting or prompting to re-link after app updates when sessions are already paired.
- 📇 Added a toggle to disable Local Contact Names Fallback and optionally prioritize your phonebook names across chats and Updates (#124, #126).
- 📹 Added H.264 encoder/decoder fallbacks and error recovery for budget devices, plus reduced voice call playout queue latency (#130, #127).
- 💬 Fixed opening unread chats to jump to the oldest unread message (#90).
- 🎨 Fixed button and badge text contrast across Dark, AMOLED, Synthwave, iOS, and custom themes and enlarged cramped chat buttons.
- 🛡️ Prevented startup crashes on unsupported 32-bit devices with a clear alert and normalized Meta AI's display name (#100, #97).

## [0.3.8-pre.3] - 2026-11-22

### ✨ Features
- ⚙️ Redesigned Settings with a search bar, quick category pills, info popups, and an inline chat font-size slider.

## [0.3.8-pre.2] - 2026-11-23

### ✨ Features
- 👥 Added a WhatsApp account switcher so you can link and switch between multiple accounts with separate chats, media, and settings.

## [0.3.8-pre.1] - 2026-11-22

### 🛠️ Fixes
- 📞 Fixed video call controls, camera preview rotation, audio route switching, and ongoing call notification updates.
- 🖼️ Fixed reaction emojis, date separator text, background pause, and retry handling in the full-screen media viewer (#118).
- 🔒 Hid message notification previews and quick actions for locked chats and when App Lock is active, and required a linked session for notification quick replies.

## [0.3.7] - 2026-11-22

### ✨ Features
- 🎨 Added a Material You Dynamic Colors toggle plus 6 new themes, Pure Black OLED mode, custom fonts, and bubble style options (#70, #95).
- 📢 Added an optional Separate Channels Tab and a pinned Message Yourself entry in New Chat (#69).
- 🛡️ Added Anti-Call Protection modes to silence or auto-decline all or unknown callers (#73).
- 🖼️ Upgraded the Chat Info screen with a full Media, Docs, and Links wall with video thumbnails and tap-to-download (#76).
- ✨ Added Sticker Studio for creating custom transparent stickers with background cutout, text, and drawing tools (#64).
- 🎙️ Added in-app call recording and organized locked folders inside the Media Vault (#64).
- 🕘 Added a searchable Deleted and Edited Message History viewer and a Per-Chat Quick Controls sheet (#64).
- 💬 Updated default chat bubble corner roundness to `17dp`.

### 🛠️ Fixes
- 👥 Fixed duplicate 1-on-1 chats appearing for the same contact and merged existing split threads automatically (#116).
- 🔔 Fixed incoming messages being marked as read while Wupp is in the background or locked (#117).
- ⌨️ Removed the extra blank space below the chat input bar (#69).
- ⚡ Added a 4-second post-connect settle delay before background contact and avatar syncing starts.

## [0.3.6] - 2026-11-22

### ✨ Features
- 🦀 Replaced the old background bridge and browser calling with a unified native Rust core.
- 📞 Added native in-app voice and video calls using a single linked session.
- 🛡️ Added a setup guide and setting to block double ringing when Official WhatsApp is installed.
- 📢 Added channel post syncing, channel updates, mute controls, and reactions.
- 📦 Added Universal APK builds alongside the `arm64-v8a` APK.

### 🛠️ Fixes
- 🖼️ Loaded profile pictures automatically across chats, statuses, channels, and group messages.
- 👥 Fixed missing group sender names, `Participant` placeholders, and wrong contact numbers in Group Info (#82).
- 💬 Replaced raw IDs in group `@mentions` with real contact names (#104).
- 📱 Fixed contact statuses not appearing, expired stories lingering, and cross-device seen sync (#91, #94, #101).
- 🕒 Added exact deletion timestamps on deleted messages and stories (#62).
- 🎙️ Fixed recorded voice notes failing to play in official WhatsApp.
- 📥 Fixed media, GIF, and status downloads not starting or saving properly.
- 📞 Fixed call-log contact names, call timers, and rotated or stretched video calls.
- 👁️ Removed automatic saving of View-Once media to stay compliant with companion behavior.
- 🔝 Fixed chat list sorting and capped pinned chats at 3 to stay in sync.
- ⚡ Reduced background network bursts while keeping avatars, channels, and media syncing smoothly.

## [0.3.5-pre.20] - 2026-11-22

### 🛠️ Fixes & Improvements
- Fixed missing profile pictures across chats, statuses, and channels.
- Fixed channel posts and channel updates not showing up.
- Fixed media downloads failing in chats and statuses.
- Fixed missing sender names and colors on group messages.
- Reduced background network bursts while keeping avatars, channels, and media syncing smoothly.

## [0.3.5-pre.19] - 2026-11-22

### 🛠️ Fixes & Improvements
- Loaded profile pictures automatically across chats, statuses, and channels.
- Fixed group and community message previews showing random numbers instead of sender names.
- Replaced raw phone numbers with real contact names in group `@mentions`.
- Added universal APK downloads alongside the `arm64-v8a` APK.

## [0.3.5-pre.18] - 2026-11-22

### ✨ Features & Fixes
- Replaced the background bridge and browser calling stack with a unified native core.
- Added native in-app voice and video calling using a single linked device.
- Fixed contact statuses not appearing and marked viewed statuses as seen across devices.
- Removed automatic saving of View-Once media to restore standard behavior.
- Fixed incoming call log names, missed or silenced call labels, and punctuation-only contact names.
- Fixed rotated and stretched video call streams on both local and remote screens.
- Fixed outgoing calls showing "Calling" and "Ringing" before starting the call timer.
- Fixed chat list sorting so latest replies move to the top and reactions do not replace chat timestamps.
- Limited pinned chats to 3 to stay synced with WhatsApp.
- Added a setup guide and setting to prevent double ringing when Official WhatsApp is installed.

## [0.3.5-pre.17] - 2026-11-21

### 🛠️ Fixes
- 📞 **Direct Calling Router & Status:** Fixed calls dialing the wrong chat by routing directly via target chat IDs, and fixed `Calling…` → `Ringing…` → `Connecting…` states plus full-screen video switching.
- 🔗 **Calling Setup Sync Transition:** Cleared stale pairing codes as soon as WhatsApp Web starts loading chats after phone-code linking.
- 🔋 **Calling Efficiency & Smaller APK:** Put background calling to sleep cleanly between calls with event-driven listeners and compressed native libraries to shrink APK size.

## [0.3.5-pre.16] - 2026-11-21

### 🛠️ Fixes
- 📞 **Calls Engine Upgrade:** Switched internal calling to Mozilla GeckoView so native `SharedArrayBuffer` threads and voice/video calling connect cleanly on device.

## [0.3.5-pre.15] - 2026-11-20

### 🛠️ Fixes
- 📞 **Calls Cross-Origin Isolation & SharedArrayBuffer:** Fixed greyed-out voice and video call buttons by enabling `SharedArrayBuffer` and `Cross-Origin-Opener-Policy: same-origin` isolation required by WhatsApp Web's VoIP engine.

## [0.3.5-pre.14] - 2026-11-20

### 🛠️ Fixes
- 📞 **Calls Diagnostics & Cache Reset:** Automatically clears stale Service Worker cache once after update, removes legacy `ontouchstart` detection where allowed, and reports detailed browser/Worker/gating capability status in Linked Session view.

## [0.3.5-pre.13] - 2026-11-20

### 🛠️ Fixes
- 📞 **Calls PC Mode:** Added early PC browser environment alignment (`Win32`, non-touch, 1024px desktop viewport, permission status sync, and `/call/popout` call window support) plus a live Reload button in Linked Session view.

## [0.3.5-pre.12] - 2026-11-20

### 🛠️ Fixes
- 📞 **Calls Compatibility:** Aligned Chromium Client Hints (`User-AgentMetadata`) to Windows Desktop so WhatsApp Web treats the session like a laptop browser instead of greying out call buttons, and prevented opening group chats before search filters finish.

## [0.3.5-pre.11] - 2026-11-20

### 🛠️ Fixes
- 📞 **Calls Warmup & Dialing:** Increased cold-start wait for WhatsApp Web, fixed the Activity focus race on dial start, and used trusted Android taps and keys so chat search and chat selection open cleanly.

## [0.3.5-pre.10] - 2026-11-20

### 🛠️ Fixes
- 📞 **Calls Dialing:** Fixed WhatsApp Web call button detection (supports `voice-call` / `video-call` icons, unified Call menu dropdowns, and reliable chat selection) so outgoing calls start cleanly.

## [0.3.5-pre.9] - 2026-11-20

### 🛠️ Fixes
- 📞 **Calls Pairing:** Fixed phone-linking button matching so it never clicks `"Terms & Privacy Policy"`, and fixed the blank white Live Pairing Screen by opening it in an interactive dialog without losing the WebView.

## [0.3.5-pre.8] - 2026-11-20

### 🛠️ Fixes
- 📞 **Calls Pairing:** Blocked unintended WhatsApp Desktop app downloads during pairing, added a 35s timeout guard, and added a Live Pairing Screen toggle.

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
