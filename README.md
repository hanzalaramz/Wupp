<p align="center">
  <img src="app/src/main/res/drawable/wupp_logo.png" alt="Wupp logo" width="96">
</p>

<h1 align="center">Wupp</h1>

<p align="center">
  <b>A linked WhatsApp companion client for Android, built with Jetpack Compose and Go.</b>
</p>

<p align="center">
  <a href="https://github.com/hanzalaramz/Wupp/releases/latest"><img src="https://img.shields.io/github/v/release/hanzalaramz/Wupp?label=release&color=25D366" alt="Wupp release"></a>
  <a href="https://github.com/hanzalaramz/Wupp/actions/workflows/ci.yml"><img src="https://img.shields.io/github/actions/workflow/status/hanzalaramz/Wupp/ci.yml?branch=main&label=CI" alt="CI status"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/hanzalaramz/Wupp?color=blue" alt="MIT License"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84" alt="Android 8.0+">
</p>

<p align="center">
  <a href="https://github.com/hanzalaramz/Wupp/releases"><b>Download latest APK from Releases</b></a>
</p>

Use your existing WhatsApp account, chats, groups, status updates, and channels in a native Jetpack Compose client, built with **anti-ban rate-limit protection**, per-feature privacy switches, and built-in GitHub updates.

## Why Wupp

### **Privacy & Ghost Mode**

- **Ghost Mode:** Freeze your last seen and stay hidden while online.
- **Hide Read, Played & Typing:** Read chats without sending blue ticks, play voice notes without played receipts, and hide typing or recording indicators.
- **Per-Chat Ghost Overrides:** Override read, played, and typing switches individually for any single chat or group.

### **Anti-Ban & Account Safety**

- **Chrome Web Identity:** Connects as **Chrome (Mac OS)** and caches WhatsApp Web version lookups locally.
- **No `429` Sync Storms:** Global contact, group, and channel syncs run under a **90-second cooldown**, dead channels are skipped, and media/avatars load through **3 jittered workers** (`70-160ms` spacing) with miss caching.
- **Error 463 Outbound Guard:** Blocks sending a first companion message to an unknown number with **zero prior local chat history**, guarding against one of WhatsApp's biggest companion triggers.
- **Safe Defaults:** Keeps normal read ticks on unless you enable **Ghost Read**, and tracks Meta's **14-day primary phone heartbeat** in Settings.

[![View Full Anti-Ban Explanation](https://img.shields.io/badge/View_Full_Anti--Ban_Explanation-0969DA?style=for-the-badge)](ANTIBAN.md)

### **Mod Features & Local Vault**

- **Anti-Delete Messages & Statuses:** Keeps text, media, and statuses on your phone even after someone deletes them for everyone.
- **View-Once & Status Saver:** View and **save View-Once photos/videos and contact statuses directly to your gallery**.
- **Disappearing Immunity & Edit History:** Keeps disappearing messages from vanishing locally, and lets you tap **Edited** messages to inspect their previous text.
- **Silent `@everyone` Mentions:** Tag everyone in a group cleanly using **Ghost Mention** without cluttering the message.

### **Native Client & In-App Updates**

Chats, statuses, `@newsletter` channels, communities, swipe-to-reply, floating emoji reactions, live delivery/read ticks, and admin-locked groups all run natively in Jetpack Compose + Room (`com.wupp.app`). Built-in **GitHub Releases** updates download signed builds (`arm64-v8a` or `universal`) and install in place **without logging you out**, alongside pre-filled bug, feature, and feedback forms.

## What it is

Wupp runs as a linked WhatsApp Web companion device rather than replacing your primary WhatsApp install. Your existing phone remains the primary account holder, and Wupp connects to it through WhatsApp's multidevice protocol.

Under the hood, a Go library (`whatsbridge`, built on `whatsmeow` and compiled to `whatsbridge.aar`) runs inside a foreground Android service (`WaRealtimeService`). It handles encrypted WebSocket traffic, history sync, receipt updates, and media decryption directly on your phone, then stores threads and downloaded files locally in Room.

## Get started

1. Download an APK from [Releases](https://github.com/hanzalaramz/Wupp/releases) (`arm64-v8a` for modern 64-bit phones, or `universal` for older 32-bit, x86, or mixed devices) and open it on an Android 8.0 or newer phone. Android will ask once to allow app installs from your browser or file manager.
2. Walk through the first-launch setup screen so notification, media, and background-running permissions are granted.
3. Link your WhatsApp account on the setup login screen using either a QR code scan or a phone number link code from **Linked devices** in your primary WhatsApp app.

Future versions prompt inside the app, automatically match your phone's CPU architecture, and update in place without logging you out of WhatsApp. If you track releases with [Obtainium](https://github.com/ImranR98/Obtainium), point it at `https://github.com/hanzalaramz/Wupp` and filter assets with `^Wupp-.*-arm64-v8a\.apk$` (or `-universal`; turn on "Include prereleases" for alpha/beta builds).

## What talks to the network

Wupp sends no telemetry, analytics, or crash dumps to any developer server.

- WhatsApp protocol messages, avatars, status updates, channel posts, and media travel directly between your device and WhatsApp servers over `whatsmeow`.
- Update checks query `https://api.github.com/repos/hanzalaramz/Wupp/releases/latest` (and `/releases?per_page=1` if only pre-releases exist) to read version tags and APK URLs without needing a GitHub account.
- Bug reports, feature requests, and feedback open GitHub's new-issue page in your browser only when you tap **Open GitHub** (GitHub asks you to sign in before posting; tap **Copy** to save the text locally without logging in).

Chats, decrypted media, session keys, and preferences remain on your device.

## Bugs, features, and feedback

Inside Wupp, open **Settings > Updates & GitHub Support** or tap the top-right overflow menu and choose **Report bug / Feedback**. Submitting on GitHub requires a GitHub account in your browser, or you can copy the formatted report text directly from the dialog. You can also file an issue directly on [GitHub Issues](https://github.com/hanzalaramz/Wupp/issues).

## Disclaimer

Wupp is an independent client and is not affiliated with, sponsored by, or endorsed by WhatsApp LLC or Meta Platforms, Inc. Using unofficial WhatsApp companion clients or privacy modifications may violate WhatsApp's Terms of Service and could result in temporary or permanent account restrictions. You use this software at your own risk.

## For developers

### Layout

`app/` contains the Kotlin and Jetpack Compose Android client (`applicationId = "com.wupp.app"`, `minSdk = 26`, `targetSdk = 34`, outputting `arm64-v8a` and multi-ABI `universal` release APKs). `whatsbridge/` contains the Go bridge wrapping `go.mau.fi/whatsmeow`, built into `app/libs/whatsbridge.aar` (`arm64-v8a`, `armeabi-v7a`, `x86_64`).

### Requirements and local build

Install Go 1.26 or newer, `gomobile`, JDK 17, Gradle 8.9 or newer, and Android SDK 34 with NDK.

Build the Go AAR first:

```bash
gomobile init
cd whatsbridge
gomobile bind -javapkg=com.wa.bridge -target=android/arm64,android/arm,android/amd64 -androidapi 26 -o ../app/libs/whatsbridge.aar .
```

`app/build.gradle.kts` reads `BuildConfig.GITHUB_REPO` from `git remote origin`, falling back to `WUPP_GITHUB_REPO` in `gradle.properties`. Once `whatsbridge.aar` is in `app/libs/`, build the debug APKs:

```bash
gradle assembleDebug
```

### CI and releases

- Every push and pull request on `main` runs `.github/workflows/ci.yml`, which vets `whatsbridge`, binds `whatsbridge.aar`, and compiles a debug APK.
- Pushing a `v*` tag triggers `.github/workflows/release.yml`, which builds `whatsbridge.aar`, compiles and signs `Wupp-<tag>-arm64-v8a.apk` and `Wupp-<tag>-universal.apk`, generates `SHA256SUMS.txt`, and publishes the GitHub Release.
- Tags with a hyphen (such as `v0.1.0-beta.1`) publish as pre-releases. Tags without a hyphen (such as `v1.0.0`) publish as the latest stable release.
- When `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` are set in Actions secrets, CI signs release APKs with that permanent key so in-app updates install over older versions cleanly.

## Credits

- [whatsmeow](https://github.com/tulir/whatsmeow) by tulir and contributors for the Go WhatsApp Web multidevice library.
- Jetpack Compose, Material 3, Room, Media3, and Coil for the Android UI, local storage, and media playback stack.

## License

Released under the [MIT License](LICENSE).
