package com.wa.beastclient.domain.mentions

import android.content.Context
import android.content.SharedPreferences
import com.wa.beastclient.domain.accounts.AccountManager
import com.wa.beastclient.domain.contacts.ParticipantDisplayResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UnreadMentionEntry(
    val messageId: String,
    val timestamp: Long
)

/**
 * Tracks unseen `@` mentions (direct or `@everyone` / group mentions) per group chat,
 * persisted per active account so badges and jump-to-mention targets survive app restarts.
 * Operates 100% locally on device with zero extra WhatsApp server traffic.
 */
object UnreadMentionManager {
    private const val PREFS_NAME = "wupp_unread_mentions"
    private const val MAX_MENTIONS_PER_CHAT = 50

    @Volatile
    private var prefs: SharedPreferences? = null

    private val _unreadMentionsByChat = MutableStateFlow<Map<String, List<UnreadMentionEntry>>>(emptyMap())
    val unreadMentionsByChat: StateFlow<Map<String, List<UnreadMentionEntry>>> = _unreadMentionsByChat.asStateFlow()

    /**
     * Pure check determining whether an incoming group message mentions the current user
     * directly (by phone JID or LID) or via an `@everyone` / group mention.
     */
    fun doesMessageMentionMe(
        chatJid: String,
        mentionsCsv: String,
        isFromMe: Boolean,
        ownJid: String,
        ownLid: String = "",
        lidToPn: Map<String, String> = emptyMap()
    ): Boolean {
        if (isFromMe) return false
        val cleanChat = chatJid.trim()
        if (!cleanChat.endsWith("@g.us", ignoreCase = true)) return false
        if (mentionsCsv.isBlank()) return false

        for (rawToken in mentionsCsv.split(',')) {
            val token = rawToken.trim()
            if (token.isEmpty()) continue
            if (token.equals(cleanChat, ignoreCase = true) ||
                token.endsWith("@g.us", ignoreCase = true) ||
                token.equals("everyone", ignoreCase = true) ||
                token.equals("all", ignoreCase = true)
            ) {
                return true
            }
            if (ParticipantDisplayResolver.isSelfParticipant(
                    rawJid = token,
                    altJid = "",
                    ownJid = ownJid,
                    ownLid = ownLid,
                    lidToPn = lidToPn
                )
            ) {
                return true
            }
        }
        return false
    }

    @Synchronized
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        reloadForActiveAccount()
    }

    @Synchronized
    fun reloadForActiveAccount() {
        val sp = prefs ?: return
        val prefix = accountPrefix()
        val loaded = LinkedHashMap<String, List<UnreadMentionEntry>>()
        for ((key, value) in sp.all) {
            if (key.startsWith(prefix) && value is String && value.isNotBlank()) {
                val chatJid = key.removePrefix(prefix)
                if (chatJid.isNotBlank()) {
                    val entries = decodeEntries(value)
                    if (entries.isNotEmpty()) {
                        loaded[chatJid] = entries
                    }
                }
            }
        }
        _unreadMentionsByChat.value = loaded
    }

    @Synchronized
    fun recordUnreadMention(chatJid: String, messageId: String, timestamp: Long) {
        val cleanChat = chatJid.trim()
        val cleanId = messageId.trim()
        if (!cleanChat.endsWith("@g.us", ignoreCase = true) || cleanId.isEmpty()) return

        val existing = _unreadMentionsByChat.value[cleanChat].orEmpty()
        if (existing.any { it.messageId == cleanId }) return

        val updated = (existing + UnreadMentionEntry(cleanId, timestamp))
            .sortedWith(compareBy<UnreadMentionEntry> { it.timestamp }.thenBy { it.messageId })
            .let { list ->
                if (list.size > MAX_MENTIONS_PER_CHAT) list.takeLast(MAX_MENTIONS_PER_CHAT) else list
            }
        commitChatEntries(cleanChat, updated)
    }

    @Synchronized
    fun markMentionSeen(chatJid: String, messageId: String) {
        val cleanChat = chatJid.trim()
        val cleanId = messageId.trim()
        if (cleanChat.isEmpty() || cleanId.isEmpty()) return
        val existing = _unreadMentionsByChat.value[cleanChat] ?: return
        val filtered = existing.filterNot { it.messageId == cleanId }
        if (filtered.size == existing.size) return
        commitChatEntries(cleanChat, filtered)
    }

    @Synchronized
    fun markMentionsSeen(chatJid: String, messageIds: Collection<String>) {
        if (messageIds.isEmpty()) return
        val cleanChat = chatJid.trim()
        if (cleanChat.isEmpty()) return
        val existing = _unreadMentionsByChat.value[cleanChat] ?: return
        val idSet = if (messageIds is Set<String>) messageIds else messageIds.toHashSet()
        val filtered = existing.filterNot { it.messageId in idSet }
        if (filtered.size == existing.size) return
        commitChatEntries(cleanChat, filtered)
    }

    @Synchronized
    fun markMentionsSeenUpTo(chatJid: String, upToTimestamp: Long) {
        val cleanChat = chatJid.trim()
        if (cleanChat.isEmpty() || upToTimestamp <= 0L) return
        val existing = _unreadMentionsByChat.value[cleanChat] ?: return
        val filtered = existing.filter { it.timestamp > upToTimestamp }
        if (filtered.size == existing.size) return
        commitChatEntries(cleanChat, filtered)
    }

    @Synchronized
    fun removeMention(chatJid: String, messageId: String) {
        markMentionSeen(chatJid, messageId)
    }

    @Synchronized
    fun clearChatMentions(chatJid: String) {
        val cleanChat = chatJid.trim()
        if (cleanChat.isEmpty()) return
        if (!_unreadMentionsByChat.value.containsKey(cleanChat)) return
        commitChatEntries(cleanChat, emptyList())
    }

    fun getUnreadMentions(chatJid: String): List<UnreadMentionEntry> =
        _unreadMentionsByChat.value[chatJid.trim()].orEmpty()

    @Synchronized
    internal fun resetForTest() {
        prefs = null
        _unreadMentionsByChat.value = emptyMap()
    }

    private fun commitChatEntries(chatJid: String, entries: List<UnreadMentionEntry>) {
        val next = _unreadMentionsByChat.value.toMutableMap()
        if (entries.isEmpty()) {
            next.remove(chatJid)
        } else {
            next[chatJid] = entries
        }
        _unreadMentionsByChat.value = next

        prefs?.edit()?.apply {
            if (entries.isEmpty()) {
                remove(storageKey(chatJid))
            } else {
                putString(storageKey(chatJid), encodeEntries(entries))
            }
            apply()
        }
    }

    internal fun encodeEntries(entries: List<UnreadMentionEntry>): String {
        return entries.joinToString("\n") { entry ->
            val safeId = entry.messageId.replace("\n", "").replace("\r", "").trim()
            "${entry.timestamp}|$safeId"
        }
    }

    internal fun decodeEntries(raw: String): List<UnreadMentionEntry> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence()
            .mapNotNull { line ->
                val sep = line.indexOf('|')
                if (sep <= 0) return@mapNotNull null
                val ts = line.substring(0, sep).trim().toLongOrNull() ?: 0L
                val id = line.substring(sep + 1).trim()
                if (id.isEmpty()) null else UnreadMentionEntry(id, ts)
            }
            .distinctBy { it.messageId }
            .sortedWith(compareBy<UnreadMentionEntry> { it.timestamp }.thenBy { it.messageId })
            .toList()
    }

    private fun accountPrefix(): String = "${AccountManager.activeAccountId.value}::"

    private fun storageKey(chatJid: String): String = "${accountPrefix()}$chatJid"
}
