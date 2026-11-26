package com.wa.beastclient.data.media

import android.content.Context
import android.content.SharedPreferences
import com.wa.beastclient.domain.accounts.AccountManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class FavoriteStickerRecord(
    val key: String,
    val sha256: String,
    val filePath: String,
    val originalPath: String,
    val messageId: String,
    val addedAt: Long
)

/**
 * Collects all non-blank lookup keys for a sticker so blank SHA-256 values never match falsely.
 */
internal fun collectStickerLookupKeys(
    sha256: String?,
    localPath: String?,
    messageId: String?,
    extraPaths: Collection<String> = emptyList()
): Set<String> = buildSet {
    sha256?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
    localPath?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
    messageId?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
    extraPaths.forEach { raw ->
        raw.trim().takeIf { it.isNotEmpty() }?.let(::add)
    }
}

internal fun isStickerMatchedByFavoriteKeys(
    sha256: String?,
    localPath: String?,
    messageId: String?,
    favoriteKeys: Set<String>
): Boolean {
    if (favoriteKeys.isEmpty()) return false
    val keys = collectStickerLookupKeys(sha256, localPath, messageId)
    return keys.any { it in favoriteKeys }
}

/**
 * Manages locally favorited stickers with persistent SharedPreferences keys plus
 * account-scoped local copies in `stickers/favorites/` so favorited stickers remain
 * available even if chat history exceeds recent limits or original messages are cleared.
 */
class StickerFavoritesStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences
        get() = AccountManager.getScopedPreferences(appContext, "wupp_sticker_favorites")
    private val favoritesDir: File
        get() = EagerMediaVault.favoriteStickersDir(appContext)
    private val manifestFile: File
        get() = File(favoritesDir, "manifest.json")

    private val _records = MutableStateFlow<List<FavoriteStickerRecord>>(emptyList())
    val records: StateFlow<List<FavoriteStickerRecord>> = _records.asStateFlow()

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    init {
        reloadForActiveAccount()
    }

    fun reloadForActiveAccount() {
        val loadedRecords = loadManifest()
        val loadedKeys = loadFavoriteKeys()
        _records.value = loadedRecords
        _favorites.value = mergeKeys(loadedKeys, loadedRecords)
    }

    private fun loadFavoriteKeys(): Set<String> {
        val raw = prefs.getStringSet(KEY_FAVORITES, null) ?: emptySet()
        return raw.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toSet()
    }

    private fun mergeKeys(
        rawKeys: Set<String>,
        records: List<FavoriteStickerRecord>
    ): Set<String> = buildSet {
        rawKeys.forEach { k ->
            k.trim().takeIf { it.isNotEmpty() }?.let(::add)
        }
        records.forEach { rec ->
            addAll(
                collectStickerLookupKeys(
                    sha256 = rec.sha256,
                    localPath = rec.filePath,
                    messageId = rec.messageId,
                    extraPaths = listOf(rec.key, rec.originalPath)
                )
            )
        }
    }

    fun isFavorite(key: String): Boolean {
        val clean = key.trim()
        if (clean.isEmpty()) return false
        return _favorites.value.contains(clean)
    }

    fun isFavoriteAny(
        sha256: String?,
        localPath: String?,
        messageId: String?
    ): Boolean = isStickerMatchedByFavoriteKeys(
        sha256 = sha256,
        localPath = localPath,
        messageId = messageId,
        favoriteKeys = _favorites.value
    )

    fun toggleFavoriteSticker(
        sha256: String?,
        localPath: String?,
        messageId: String?
    ): Boolean {
        val cleanSha = sha256?.trim().orEmpty()
        val cleanPath = localPath?.trim().orEmpty()
        val cleanId = messageId?.trim().orEmpty()
        val candidateKeys = collectStickerLookupKeys(cleanSha, cleanPath, cleanId)
        if (candidateKeys.isEmpty()) return false

        val currentRecords = _records.value
        val matchingRecords = currentRecords.filter { rec ->
            val recKeys = collectStickerLookupKeys(
                sha256 = rec.sha256,
                localPath = rec.filePath,
                messageId = rec.messageId,
                extraPaths = listOf(rec.key, rec.originalPath)
            )
            recKeys.any { it in candidateKeys }
        }
        val alreadyFav = matchingRecords.isNotEmpty() || candidateKeys.any { it in _favorites.value }

        return if (alreadyFav) {
            val keysToRemove = candidateKeys.toMutableSet()
            matchingRecords.forEach { rec ->
                keysToRemove.addAll(
                    collectStickerLookupKeys(
                        sha256 = rec.sha256,
                        localPath = rec.filePath,
                        messageId = rec.messageId,
                        extraPaths = listOf(rec.key, rec.originalPath)
                    )
                )
                deleteManagedCopyIfOwned(rec.filePath)
            }
            val nextRecords = currentRecords.filterNot { it in matchingRecords }
            val nextPrefsKeys = loadFavoriteKeys().filterNot { it in keysToRemove }.toSet()
            saveManifest(nextRecords)
            prefs.edit().putStringSet(KEY_FAVORITES, nextPrefsKeys).apply()
            _records.value = nextRecords
            _favorites.value = mergeKeys(nextPrefsKeys, nextRecords)
            false
        } else {
            val primaryKey = cleanSha.ifBlank { cleanPath.ifBlank { cleanId } }
            val persistedPath = persistStickerCopy(primaryKey, cleanPath)
            val resolvedSha = cleanSha.ifBlank {
                persistedPath.takeIf { it.isNotBlank() }?.let { path ->
                    runCatching { EagerMediaVault.computeSha256(File(path)) }.getOrDefault("")
                }.orEmpty()
            }
            val newRecord = FavoriteStickerRecord(
                key = primaryKey,
                sha256 = resolvedSha,
                filePath = persistedPath.ifBlank { cleanPath },
                originalPath = cleanPath,
                messageId = cleanId,
                addedAt = System.currentTimeMillis()
            )
            val dedupedExisting = currentRecords.filterNot { rec ->
                (resolvedSha.isNotBlank() && rec.sha256 == resolvedSha) ||
                    (newRecord.filePath.isNotBlank() && rec.filePath == newRecord.filePath) ||
                    rec.key == primaryKey
            }
            val nextRecords = listOf(newRecord) + dedupedExisting
            val nextPrefsKeys = loadFavoriteKeys().toMutableSet().apply {
                add(primaryKey)
                if (resolvedSha.isNotBlank()) add(resolvedSha)
                if (cleanPath.isNotBlank()) add(cleanPath)
            }
            saveManifest(nextRecords)
            prefs.edit().putStringSet(KEY_FAVORITES, nextPrefsKeys).apply()
            _records.value = nextRecords
            _favorites.value = mergeKeys(nextPrefsKeys, nextRecords)
            true
        }
    }

    fun toggleFavorite(key: String): Boolean {
        val clean = key.trim()
        if (clean.isEmpty()) return false
        return if (isFavorite(clean)) {
            removeFavorite(clean)
            false
        } else {
            addFavorite(clean)
            true
        }
    }

    fun addFavorite(key: String) {
        val clean = key.trim()
        if (clean.isEmpty()) return
        val nextPrefsKeys = loadFavoriteKeys().toMutableSet()
        if (nextPrefsKeys.add(clean)) {
            prefs.edit().putStringSet(KEY_FAVORITES, nextPrefsKeys).apply()
        }
        _favorites.value = mergeKeys(nextPrefsKeys, _records.value)
    }

    fun removeFavorite(key: String) {
        val clean = key.trim()
        if (clean.isEmpty()) return
        val currentRecords = _records.value
        val matchingRecords = currentRecords.filter { rec ->
            clean == rec.key || clean == rec.sha256 || clean == rec.filePath ||
                clean == rec.originalPath || clean == rec.messageId
        }
        val keysToRemove = mutableSetOf(clean)
        matchingRecords.forEach { rec ->
            keysToRemove.addAll(
                collectStickerLookupKeys(
                    sha256 = rec.sha256,
                    localPath = rec.filePath,
                    messageId = rec.messageId,
                    extraPaths = listOf(rec.key, rec.originalPath)
                )
            )
            deleteManagedCopyIfOwned(rec.filePath)
        }
        val nextRecords = currentRecords.filterNot { it in matchingRecords }
        if (matchingRecords.isNotEmpty()) {
            saveManifest(nextRecords)
            _records.value = nextRecords
        }
        val nextPrefsKeys = loadFavoriteKeys().filterNot { it in keysToRemove }.toSet()
        prefs.edit().putStringSet(KEY_FAVORITES, nextPrefsKeys).apply()
        _favorites.value = mergeKeys(nextPrefsKeys, nextRecords)
    }

    private fun persistStickerCopy(primaryKey: String, sourcePath: String): String {
        if (sourcePath.isBlank()) return ""
        return runCatching {
            val src = File(sourcePath)
            if (!src.exists() || src.length() <= 0L) return ""
            val dir = favoritesDir
            if (!dir.exists()) dir.mkdirs()
            if (src.parentFile?.canonicalPath == dir.canonicalPath) {
                return src.absolutePath
            }
            val ext = src.extension.lowercase().takeIf { it.isNotBlank() && it.length <= 5 } ?: "webp"
            val stableName = "fav_${shortStableHash(primaryKey)}.$ext"
            val dest = File(dir, stableName)
            if (!dest.exists() || dest.length() != src.length()) {
                src.copyTo(dest, overwrite = true)
            }
            dest.absolutePath
        }.getOrDefault(sourcePath)
    }

    private fun deleteManagedCopyIfOwned(filePath: String) {
        if (filePath.isBlank()) return
        runCatching {
            val file = File(filePath)
            val dir = favoritesDir
            if (file.exists() && file.parentFile?.canonicalPath == dir.canonicalPath) {
                file.delete()
            }
        }
    }

    private fun shortStableHash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun loadManifest(): List<FavoriteStickerRecord> {
        if (!manifestFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(manifestFile.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val key = obj.optString("key").trim()
                    val filePath = obj.optString("filePath").trim()
                    if (key.isNotBlank() || filePath.isNotBlank()) {
                        add(
                            FavoriteStickerRecord(
                                key = key.ifBlank { filePath },
                                sha256 = obj.optString("sha256").trim(),
                                filePath = filePath,
                                originalPath = obj.optString("originalPath").trim(),
                                messageId = obj.optString("messageId").trim(),
                                addedAt = obj.optLong("addedAt", 0L)
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveManifest(items: List<FavoriteStickerRecord>) {
        runCatching {
            val dir = favoritesDir
            if (!dir.exists()) dir.mkdirs()
            val arr = JSONArray()
            items.forEach { item ->
                arr.put(
                    JSONObject().apply {
                        put("key", item.key)
                        put("sha256", item.sha256)
                        put("filePath", item.filePath)
                        put("originalPath", item.originalPath)
                        put("messageId", item.messageId)
                        put("addedAt", item.addedAt)
                    }
                )
            }
            manifestFile.writeText(arr.toString())
        }
    }

    companion object {
        private const val KEY_FAVORITES = "favorite_sticker_keys"

        @Volatile
        private var instance: StickerFavoritesStore? = null

        fun getInstance(context: Context): StickerFavoritesStore {
            return instance ?: synchronized(this) {
                instance ?: StickerFavoritesStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
