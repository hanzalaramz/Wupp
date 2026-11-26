package com.wa.beastclient.domain.drafts

import android.content.Context
import android.content.SharedPreferences
import com.wa.beastclient.domain.accounts.AccountManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun normalizeDraftText(raw: String): String =
    if (raw.isBlank()) "" else raw

/**
 * Persists unsent per-chat message drafts across screen exits and app restarts,
 * scoped per active account.
 */
object ChatDraftManager {
    private const val PREFS_NAME = "wupp_chat_drafts"

    @Volatile
    private var prefs: SharedPreferences? = null

    private val _drafts = MutableStateFlow<Map<String, String>>(emptyMap())
    val drafts: StateFlow<Map<String, String>> = _drafts.asStateFlow()

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
        val loaded = linkedMapOf<String, String>()
        for ((key, value) in sp.all) {
            if (key.startsWith(prefix) && value is String && value.isNotBlank()) {
                val chatJid = key.removePrefix(prefix)
                if (chatJid.isNotBlank()) {
                    loaded[chatJid] = value
                }
            }
        }
        _drafts.value = loaded
    }

    fun getDraft(chatJid: String): String {
        val cleanJid = chatJid.trim()
        if (cleanJid.isEmpty()) return ""
        return _drafts.value[cleanJid]
            ?: prefs?.getString(storageKey(cleanJid), null)?.takeIf { it.isNotBlank() }.orEmpty()
    }

    fun saveDraft(chatJid: String, rawText: String) {
        val cleanJid = chatJid.trim()
        if (cleanJid.isEmpty()) return
        val normalized = normalizeDraftText(rawText)
        val current = _drafts.value[cleanJid].orEmpty()
        if (current == normalized) return

        val next = _drafts.value.toMutableMap()
        if (normalized.isEmpty()) {
            next.remove(cleanJid)
        } else {
            next[cleanJid] = normalized
        }
        _drafts.value = next

        prefs?.edit()?.apply {
            if (normalized.isEmpty()) {
                remove(storageKey(cleanJid))
            } else {
                putString(storageKey(cleanJid), normalized)
            }
            apply()
        }
    }

    private fun accountPrefix(): String = "${AccountManager.activeAccountId.value}::"

    private fun storageKey(chatJid: String): String = "${accountPrefix()}$chatJid"
}
