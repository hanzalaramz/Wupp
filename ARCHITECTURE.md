# Connection, Rate-Limit & Protocol Architecture

This page breaks down every safeguard Wupp uses across the Kotlin app (`WaRealtimeService`, `StealthMatrixManager`) and Go bridge (`whatsbridge.go`) to maintain connection stability and avoid rate-limiting on WhatsApp's multidevice protocol.

## 1. Companion Identity & HTTP Headers
- **Browser fingerprint (`applyDeviceFingerprint`, `PairWithPhoneNumber`):** Wupp links as **Chrome (Mac OS)** (`DeviceProps_CHROME`, OS `Mac OS 14.4.1`) with full-sync capability enabled (`RequireFullSync = true`).
- **24-hour version caching (`refreshWAVersionIfNeeded`):** The WhatsApp Web protocol version is cached in `wa_version_cache.json` for `86,400s` (24 hours) so reconnects do not hammer WhatsApp version endpoints.
- **Browser-matched CDN headers (`setWhatsAppCDNHeaders`):** Avatar and CDN HTTP fetches attach Chrome Mac OS `User-Agent`, `Origin: https://web.whatsapp.com`, and `Referer: https://web.whatsapp.com/`.

## 2. Preventing `429: rate-overlimit` Sync Storms
- **Serialized 90-second global cooldown (`SyncContactsAndAvatars`):** Full group (`GetJoinedGroups`), channel (`GetSubscribedNewsletters`), and contact sweeps are protected by `contactsSyncRunning` and `lastContactsSyncUnix`, allowing at most one global sweep every `90` seconds.
- **Targeted JID lookups instead of global re-syncs:** When a chat or status lacks an avatar, passing specific JIDs triggers only `enqueueAvatarFetch` + name resolution for those JIDs and returns immediately without re-running group or newsletter sweeps.
- **Dead-channel circuit breaker (`deadNewsletters`):** Channels returning HTTP/protocol `404` are cached in memory so subsequent syncs skip them.
- **Queue separation (`realtimeChannel` vs. `syncChannel`):** Incoming `HISTORY_SYNC_BATCH` and `CONTACTS_SYNC_BATCH` bursts sit on their own unlimited channel, and bridge logging drops `INFO`/`DEBUG` noise (`WARN`/`ERROR` only), keeping live events flowing smoothly without queue drops or reconnect churn.

## 3. Throttled Worker Pools, Jitter & Miss Caching
- **Bounded concurrency + random jitter:** Downloads run inside fixed pools (`mediaWorkers = 3`, `avatarWorkers = 3`) with randomized pauses (`70-180ms` spacing) rather than firing dozens of simultaneous requests after launch or reconnect.
- **6-hour SQLite miss cache (`wa_avatar_miss`):** Accounts with no profile photo or restricted privacy (`ErrProfilePictureNotSet`, `ErrProfilePictureUnauthorized`, HTTP `404`/`410`) are stored in SQLite (`avatarMissTTLSec = 6 hours`) so Wupp does not re-query them on every restart.
- **Smart `@lid` retry window:** Unmapped hidden user IDs (`@lid` with no phone number mapping yet) pause in memory for `60s` (`avatarUnmappedRetrySec`) and clear cleanly once `PutLIDMapping` resolves.

## 4. Per-Request Cooldowns & Retry Caps
- **Peer & history deduplication:** Peer unavailable-message requests (`peerMediaReqMu`, `10s` cooldown) and chat history-sync requests (`historyReqMu`, `15s` cooldown) cannot spam WhatsApp or your primary phone for the same chat/message.
- **Contact & decrypt caps:** Bulk contact checks are throttled via `verifyBulkLastMs`, and corrupted/undecryptable packets stop retrying after `maxDecryptRetries = 2`.

## 5. Outbound Error 463 Companion Guard
- **Unknown-number send block (`StealthMatrixManager.canInitiateOutboundMessage`):** Before sending any text or media in a 1-on-1 chat (`WaRealtimeService.kt`), Wupp checks Room for prior message history.
- If a contact has **0 prior messages**, outbound sending is blocked locally (`ERROR_463_GUARD`) so you start new unknown-number conversations from your official WhatsApp app first, eliminating one of the most frequent companion Error 463 protocol issues. Groups, statuses, and newsletters remain unaffected.

## 6. Balanced Privacy Defaults & 14-Day Heartbeat
- **Non-extreme default receipts (`GlobalStealthConfig`):** Blue read ticks stay enabled by default (`hideReadReceipts = false`) unless you opt in to Ghost Read globally or per chat, keeping normal conversation timing balanced while `ghostPresence`, `hidePlayedReceipts`, and `hideTypingRecording` stay configurable.
- **14-day primary phone tracker (`computeHeartbeatStatus`):** Wupp monitors WhatsApp's 14-day linked-device window and flags you starting at day `10` (`WARNING_THRESHOLD_DAYS`) so your primary phone checks in before your session is invalidated.

## Protocol Boundaries
Wupp connects via `whatsmeow` as an independent companion client. Standard protocol limits apply if an account sends bulk or automated unsolicited messages or triggers spam reports.
