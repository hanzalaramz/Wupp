package com.wa.beastclient.domain.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.wa.beastclient.data.db.ContactEntity
import com.wa.beastclient.data.db.WaDao

internal class DeviceContactIndex private constructor(
    private val exactDigits: Map<String, String>,
    private val suffix10: Map<String, String?>,
    private val suffix9: Map<String, String?>
) {
    fun lookup(vararg candidates: String): String? {
        for (raw in candidates) {
            if (raw.isBlank()) continue
            val bare = raw.trim().substringBefore("@").substringBefore(":")
            if (bare.endsWith("lid", ignoreCase = true) || raw.contains("@lid")) continue
            val digits = bare.filter { it.isDigit() }
            if (digits.length < 6) continue
            exactDigits[digits]?.let { return it }
            val noZero = digits.trimStart('0')
            if (noZero.length >= 6) {
                exactDigits[noZero]?.let { return it }
            }
        }
        for (raw in candidates) {
            if (raw.isBlank() || raw.contains("@lid")) continue
            val digits = raw.trim().substringBefore("@").substringBefore(":").filter { it.isDigit() }
            if (digits.length >= 10) {
                suffix10[digits.takeLast(10)]?.let { return it }
            }
        }
        for (raw in candidates) {
            if (raw.isBlank() || raw.contains("@lid")) continue
            val digits = raw.trim().substringBefore("@").substringBefore(":").filter { it.isDigit() }
            if (digits.length >= 9) {
                suffix9[digits.takeLast(9)]?.let { return it }
            }
        }
        return null
    }

    val isEmpty: Boolean
        get() = exactDigits.isEmpty()

    companion object {
        val EMPTY = DeviceContactIndex(emptyMap(), emptyMap(), emptyMap())

        fun fromEntries(entries: List<Pair<String, String>>): DeviceContactIndex {
            if (entries.isEmpty()) return EMPTY
            val exact = HashMap<String, String>(entries.size * 2)
            val s10 = HashMap<String, String?>(entries.size)
            val s9 = HashMap<String, String?>(entries.size)

            fun putUnique(map: MutableMap<String, String?>, key: String, name: String) {
                if (!map.containsKey(key)) {
                    map[key] = name
                } else {
                    val prev = map[key]
                    if (prev != null && prev != name) {
                        map[key] = null
                    }
                }
            }

            for ((rawPhone, rawName) in entries) {
                val cleanName = rawName.trim()
                if (cleanName.isBlank()) continue
                val digits = rawPhone.filter { it.isDigit() }
                if (digits.length < 6) continue

                exact.putIfAbsent(digits, cleanName)
                val noZero = digits.trimStart('0')
                if (noZero.length >= 6) {
                    exact.putIfAbsent(noZero, cleanName)
                }
                if (digits.length >= 10) {
                    putUnique(s10, digits.takeLast(10), cleanName)
                }
                if (digits.length >= 9) {
                    putUnique(s9, digits.takeLast(9), cleanName)
                }
            }
            return DeviceContactIndex(exact, s10, s9)
        }
    }
}

/**
 * Reads local Android contacts ONLY as a privacy-preserving fallback when WhatsApp servers
 * have not supplied a name for a specific contact or group participant.
 */
object DeviceContactFallbackResolver {
    private const val CACHE_TTL_MS = 60_000L

    @Volatile
    private var cachedIndex: DeviceContactIndex = DeviceContactIndex.EMPTY

    @Volatile
    private var cachedAtMs: Long = 0L

    fun hasReadContactsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Returns true when [candidate] does not contain a real display name from WhatsApp servers
     * (e.g. blank, raw JID, numeric user part, or `+phone` string).
     */
    internal fun isServerNameMissing(
        candidate: String?,
        jid: String,
        altJid: String = ""
    ): Boolean {
        val clean = candidate?.trim().orEmpty()
        if (clean.isBlank()) return true
        // Punctuation/dot-only pushnames (e.g. ".", "...", "-", "~") carry no real name
        if (!clean.any { it.isLetterOrDigit() }) return true
        val cleanJid = jid.trim()
        val cleanAlt = altJid.trim()
        val bare = cleanJid.substringBefore("@").substringBefore(":")
        val altBare = cleanAlt.substringBefore("@").substringBefore(":")

        if (clean == cleanJid || (cleanAlt.isNotBlank() && clean == cleanAlt)) return true
        if (bare.isNotBlank() && (clean == bare || clean == "+$bare")) return true
        if (altBare.isNotBlank() && (clean == altBare || clean == "+$altBare")) return true
        if (clean == "Participant" || clean.startsWith("~ User")) return true
        if (clean.startsWith("+") && clean.any { it.isDigit() }) return true
        val looksLikePhoneOnly = clean.any { it.isDigit() } &&
            clean.all { it.isDigit() || it == '+' || it == '-' || it == ' ' || it == '(' || it == ')' }
        return looksLikePhoneOnly
    }

    internal fun isUsableDisplayName(
        candidate: String?,
        jid: String,
        altJid: String = ""
    ): Boolean = !isServerNameMissing(candidate, jid, altJid)

    /**
     * Returns true when [candidate] is actually a group/community JID or legacy `phone-epoch` ID
     * that must never be displayed as an individual message sender name.
     */
    internal fun isGroupJidLikeIdentifier(
        candidate: String?,
        chatJid: String = ""
    ): Boolean {
        val clean = candidate?.trim().orEmpty()
        if (clean.isBlank()) return false
        if (clean.endsWith("@g.us", ignoreCase = true)) return true
        val cleanChat = chatJid.trim()
        val chatBare = cleanChat.substringBefore("@").substringBefore(":")
        if (cleanChat.isNotBlank() && (clean == cleanChat || (chatBare.isNotBlank() && clean == chatBare))) {
            return true
        }
        val bare = clean.substringBefore("@").substringBefore(":")
        val dashIdx = bare.indexOf('-')
        if (dashIdx in 6 until bare.length - 5) {
            val left = bare.substring(0, dashIdx)
            val right = bare.substring(dashIdx + 1)
            if (left.all { it.isDigit() } && right.all { it.isDigit() }) {
                return true
            }
        }
        return false
    }

    internal fun formatIntlPhoneCompact(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 7) return ""
        return when {
            digits.startsWith("92") && digits.length == 12 ->
                "+92 ${digits.substring(2, 5)} ${digits.substring(5)}"
            digits.startsWith("1") && digits.length == 11 ->
                "+1 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7)}"
            digits.length >= 11 ->
                "+${digits.substring(0, 2)} ${digits.substring(2, 5)} ${digits.substring(5)}"
            else -> "+$digits"
        }
    }

    internal fun clearCacheForTest() {
        cachedIndex = DeviceContactIndex.EMPTY
        cachedAtMs = 0L
    }

    private fun getOrLoadIndex(context: Context, forceRefresh: Boolean = false): DeviceContactIndex {
        if (!hasReadContactsPermission(context)) {
            cachedIndex = DeviceContactIndex.EMPTY
            cachedAtMs = 0L
            return DeviceContactIndex.EMPTY
        }
        val now = SystemClock.elapsedRealtime()
        if (!forceRefresh && cachedAtMs > 0L && now - cachedAtMs < CACHE_TTL_MS) {
            return cachedIndex
        }
        val rows = ArrayList<Pair<String, String>>()
        runCatching {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY
            )
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                if (numIdx >= 0 && nameIdx >= 0) {
                    while (cursor.moveToNext()) {
                        val num = cursor.getString(numIdx).orEmpty()
                        val name = cursor.getString(nameIdx).orEmpty()
                        if (num.isNotBlank() && name.isNotBlank()) {
                            rows.add(num to name)
                        }
                    }
                }
            }
        }
        val built = DeviceContactIndex.fromEntries(rows)
        cachedIndex = built
        cachedAtMs = now
        return built
    }

    fun lookupForParticipant(
        context: Context,
        jid: String,
        altJid: String = "",
        phone: String = "",
        forceRefresh: Boolean = false
    ): String? {
        val index = getOrLoadIndex(context, forceRefresh = forceRefresh)
        if (index.isEmpty) return null
        return index.lookup(phone, jid, altJid)
    }

    suspend fun backfillMissingContactNames(
        context: Context,
        dao: WaDao,
        forceRefresh: Boolean = false
    ): Int {
        val index = getOrLoadIndex(context, forceRefresh = forceRefresh)
        if (index.isEmpty) return 0

        val chats = dao.getAllChatsOnce()
        val contactUpdates = ArrayList<ContactEntity>()
        var updatedCount = 0
        val now = System.currentTimeMillis()

        for (chat in chats) {
            if (chat.isGroup || chat.isCommunityParent || chat.jid.endsWith("@newsletter") || chat.jid.endsWith("@broadcast")) {
                continue
            }
            if (!isServerNameMissing(chat.name, chat.jid)) continue
            val existingContact = dao.getContact(chat.jid)
            if (existingContact != null && !isServerNameMissing(existingContact.name, chat.jid)) {
                dao.updateChatName(chat.jid, existingContact.name, overwrite = true)
                updatedCount++
                continue
            }
            val localName = index.lookup(chat.jid, chat.name) ?: continue
            dao.updateChatName(chat.jid, localName, overwrite = true)
            contactUpdates.add(
                ContactEntity(
                    jid = chat.jid,
                    name = localName,
                    aboutText = chat.aboutText,
                    avatarPath = chat.avatarPath,
                    isSaved = true,
                    updatedAt = maxOf(chat.lastMessageTimestamp, now)
                )
            )
            updatedCount++
        }
        if (contactUpdates.isNotEmpty()) {
            dao.upsertContacts(contactUpdates)
        }
        return updatedCount
    }
}
