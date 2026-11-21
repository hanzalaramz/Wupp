package com.wa.beastclient.ui

import android.app.Application
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.wa.beastclient.core.service.BridgeStatus
import com.wa.beastclient.core.service.WaRealtimeService
import com.wa.beastclient.core.update.AppReleaseInfo
import com.wa.beastclient.core.update.UpdateUiState
import com.wa.beastclient.core.update.WuppUpdater
import com.wa.beastclient.data.db.ChatEntity
import com.wa.beastclient.data.db.ContactEntity
import com.wa.beastclient.data.db.GroupReceiptEntity
import com.wa.beastclient.data.db.MessageEditEntity
import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.data.db.PresenceLogEntity
import com.wa.beastclient.data.db.ProfileHistoryEntity
import com.wa.beastclient.data.db.StatusEntity
import com.wa.beastclient.data.db.WaUiDatabase
import com.wa.beastclient.data.media.BuiltinSticker
import com.wa.beastclient.data.media.CuppyPackManager
import com.wa.beastclient.data.media.EagerMediaVault
import com.wa.beastclient.data.media.OpusOggVoiceRecorder
import com.wa.beastclient.data.media.OutboundQualityMode
import com.wa.beastclient.data.media.hasExtractableAudioTrack
import com.wa.beastclient.data.media.SoundPoolManager
import com.wa.beastclient.data.media.StickerFavoritesStore
import com.wa.beastclient.domain.contacts.DeviceContactFallbackResolver
import com.wa.beastclient.domain.security.AppLockConfig
import com.wa.beastclient.domain.security.AppLockManager
import com.wa.beastclient.domain.stealth.AutoDownloadNetwork
import com.wa.beastclient.domain.stealth.EffectiveChatPrivacy
import com.wa.beastclient.domain.stealth.GlobalStealthConfig
import com.wa.beastclient.domain.stealth.PrimaryHeartbeatStatus
import com.wa.beastclient.domain.stealth.StealthMatrixManager
import com.wa.beastclient.ui.components.StagedOutboundMedia
import com.wa.beastclient.ui.components.probeVideoMetadata
import com.wa.beastclient.ui.theme.LocaleHelper
import com.wa.beastclient.ui.theme.WuppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

enum class HomeFilterChip(val label: String) {
    ALL("All"),
    UNREAD("Unread"),
    FAVORITES("Favorites"),
    GROUPS("Groups"),
    LOCKED("Locked"),
    DELETED("Deleted"),
    EDITED("Edited")
}

internal const val MAX_PINNED_CHATS = 3

internal fun canPinMoreChats(
    chats: List<ChatEntity>,
    targetJids: Collection<String>
): Boolean {
    val alreadyPinnedSet = chats
        .filter {
            it.isPinned &&
                !it.isCommunityParent &&
                !it.jid.endsWith("@newsletter") &&
                it.jid != "status@broadcast" &&
                !it.jid.endsWith("@broadcast")
        }
        .mapTo(HashSet()) { it.jid }
    val additionalPins = targetJids
        .asSequence()
        .filter {
            it.isNotBlank() &&
                !it.endsWith("@newsletter") &&
                it != "status@broadcast" &&
                !it.endsWith("@broadcast") &&
                it !in alreadyPinnedSet
        }
        .distinct()
        .count()
    return alreadyPinnedSet.size + additionalPins <= MAX_PINNED_CHATS
}

internal fun filterHomeThreads(
    chats: List<ChatEntity>,
    chip: HomeFilterChip,
    query: String,
    hideLocked: Boolean,
    groupFilter: Boolean? = null
): List<ChatEntity> {
    val chatThreads = chats.filter {
        !it.isCommunityParent &&
            !it.jid.endsWith("@newsletter") &&
            it.jid != "status@broadcast" &&
            !it.jid.endsWith("@broadcast") &&
            !it.isPhantomEmptyDm() &&
            !(it.name.startsWith("@") && it.lastMessagePreview.isBlank()) &&
            (groupFilter == null || it.isGroup == groupFilter)
    }
    val effectiveChip = if (groupFilter != null && chip == HomeFilterChip.GROUPS) {
        HomeFilterChip.ALL
    } else {
        chip
    }
    val byChip = when (effectiveChip) {
        HomeFilterChip.ALL -> chatThreads.filter { !hideLocked || !it.isLocked }
        HomeFilterChip.UNREAD -> chatThreads.filter { (!hideLocked || !it.isLocked) && it.unreadCount > 0 }
        HomeFilterChip.FAVORITES -> chatThreads.filter { (!hideLocked || !it.isLocked) && it.isFavorite }
        HomeFilterChip.GROUPS -> chatThreads.filter { (!hideLocked || !it.isLocked) && it.isGroup }
        HomeFilterChip.LOCKED -> if (hideLocked) emptyList() else chatThreads.filter { it.isLocked }
        HomeFilterChip.DELETED -> chatThreads.filter { (!hideLocked || !it.isLocked) && (it.hasDeletedMessages || it.lastMessageIsDeleted) }
        HomeFilterChip.EDITED -> chatThreads.filter { (!hideLocked || !it.isLocked) && it.hasEditedMessages }
    }
    val q = query.trim().lowercase()
    val filtered = if (q.isEmpty()) byChip
    else byChip.filter {
        it.name.lowercase().contains(q) ||
            it.jid.lowercase().contains(q) ||
            it.lastMessagePreview.lowercase().contains(q) ||
            (it.isGroup && it.lastMessageSenderName.lowercase().contains(q))
    }
    return filtered.sortedWith(
        compareByDescending<ChatEntity> { it.isPinned }
            .thenByDescending { if (it.isPinned) it.pinOrder else 0L }
            .thenByDescending { it.lastMessageTimestamp }
    )
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val dao = WaUiDatabase.getInstance(appContext).waDao()

    val stealthConfig: StateFlow<GlobalStealthConfig> = StealthMatrixManager.config
    val appLockConfig: StateFlow<AppLockConfig> = AppLockManager.config
    val isAppLocked: StateFlow<Boolean> = AppLockManager.isLocked
    val bridgeStatus: StateFlow<BridgeStatus> = WaRealtimeService.bridgeStatus
    val updateState: StateFlow<UpdateUiState> = WuppUpdater.state
    val autoUpdateEnabled: StateFlow<Boolean> = WuppUpdater.autoCheckEnabled
    val includePreReleases: StateFlow<Boolean> = WuppUpdater.includePreReleases
    val showUpdatePrompt: StateFlow<Boolean> = WuppUpdater.showPromptDialog

    private val _lockedChatsRevealed = MutableStateFlow(false)
    val lockedChatsRevealed: StateFlow<Boolean> = _lockedChatsRevealed.asStateFlow()

    init {
        WuppUpdater.init(appContext)
        viewModelScope.launch {
            WuppUpdater.checkForUpdates(appContext, silent = true)
        }
        viewModelScope.launch {
            AppLockManager.isLocked.collect { locked ->
                if (locked) {
                    _lockedChatsRevealed.value = false
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            purgeExpiredStatuses()
        }
    }

    val heartbeatStatus: StateFlow<PrimaryHeartbeatStatus> = stealthConfig
        .map { StealthMatrixManager.computeHeartbeatStatus(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StealthMatrixManager.computeHeartbeatStatus())

    private val _includeArchived = MutableStateFlow(false)
    val includeArchived: StateFlow<Boolean> = _includeArchived.asStateFlow()

    private val _selectedFilter = MutableStateFlow(HomeFilterChip.ALL)
    val selectedFilter: StateFlow<HomeFilterChip> = _selectedFilter.asStateFlow()

    private val _selectedGroupsFilter = MutableStateFlow(HomeFilterChip.ALL)
    val selectedGroupsFilter: StateFlow<HomeFilterChip> = _selectedGroupsFilter.asStateFlow()

    private val _homeSearchQuery = MutableStateFlow("")
    val homeSearchQuery: StateFlow<String> = _homeSearchQuery.asStateFlow()

    private val _ftsSearchResults = MutableStateFlow<List<MessageEntity>>(emptyList())
    val ftsSearchResults: StateFlow<List<MessageEntity>> = _ftsSearchResults.asStateFlow()

    private val _bulkVerifyOutput = MutableStateFlow<String?>(null)
    val bulkVerifyOutput: StateFlow<String?> = _bulkVerifyOutput.asStateFlow()

    private val _isRecordingVoice = MutableStateFlow(false)
    val isRecordingVoice: StateFlow<Boolean> = _isRecordingVoice.asStateFlow()
    private var activeRecorder: MediaRecorder? = null
    private var activeOpusRecorder: OpusOggVoiceRecorder? = null
    private var activeRecordingFile: File? = null
    private var recordingStartMs: Long = 0L

    @OptIn(ExperimentalCoroutinesApi::class)
    val allChats: StateFlow<List<ChatEntity>> = _includeArchived
        .flatMapLatest { dao.observeChats(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allContacts: StateFlow<List<ContactEntity>> = dao.observeContacts()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val callLogs: StateFlow<List<MessageEntity>> = dao.observeCallLogs()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recentStickers: StateFlow<List<MessageEntity>> = dao.observeRecentStickers()
        .map { list ->
            list.filter { !it.localMediaPath.isNullOrBlank() && File(it.localMediaPath).exists() }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val stickerFavoritesStore = StickerFavoritesStore.getInstance(appContext)

    val favoriteStickerKeys: StateFlow<Set<String>> = stickerFavoritesStore.favorites

    val favoriteStickers: StateFlow<List<MessageEntity>> = combine(
        recentStickers,
        favoriteStickerKeys
    ) { stickers, favKeys ->
        stickers.filter { msg ->
            val sha = msg.mediaSha256
            val path = msg.localMediaPath
            val id = msg.id
            (sha != null && favKeys.contains(sha)) ||
                (path != null && favKeys.contains(path)) ||
                favKeys.contains(id)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val cuppyStickers: StateFlow<List<BuiltinSticker>> = flow {
        emit(CuppyPackManager.getCuppyStickers(appContext))
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val archivedCount: StateFlow<Int> = dao.observeArchivedCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val hiddenLockedChatsCount: StateFlow<Int> = combine(
        allChats,
        appLockConfig,
        lockedChatsRevealed
    ) { chats, lockCfg, revealed ->
        if (!lockCfg.hideLockedChatsBehindLock || revealed) 0
        else chats.count {
            it.isLocked &&
                !it.isCommunityParent &&
                !it.jid.endsWith("@newsletter") &&
                it.jid != "status@broadcast" &&
                !it.jid.endsWith("@broadcast") &&
                !it.isPhantomEmptyDm() &&
                !(it.name.startsWith("@") && it.lastMessagePreview.isBlank())
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private val homeFilterCriteria = combine(
        allChats,
        _homeSearchQuery,
        appLockConfig,
        lockedChatsRevealed
    ) { chats, query, lockCfg, revealed ->
        QuadFilterState(chats, query, lockCfg.hideLockedChatsBehindLock && !revealed)
    }

    private data class QuadFilterState(
        val chats: List<ChatEntity>,
        val query: String,
        val hideLocked: Boolean
    )

    val filteredChats: StateFlow<List<ChatEntity>> = combine(
        homeFilterCriteria,
        _selectedFilter,
        stealthConfig
    ) { state, chip, stealth ->
        val groupFilter = if (stealth.separateGroupsAndChats) false else null
        filterHomeThreads(state.chats, chip, state.query, state.hideLocked, groupFilter = groupFilter)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredGroups: StateFlow<List<ChatEntity>> = combine(
        homeFilterCriteria,
        _selectedGroupsFilter
    ) { state, chip ->
        filterHomeThreads(state.chats, chip, state.query, state.hideLocked, groupFilter = true)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val channels: StateFlow<List<ChatEntity>> = allChats
        .map { chats ->
            chats.filter { it.jid.endsWith("@newsletter") }
                .sortedWith(
                    compareByDescending<ChatEntity> { it.isPinned }
                        .thenByDescending { if (it.isPinned) it.pinOrder else 0L }
                        .thenByDescending { it.lastMessageTimestamp }
                        .thenBy { it.name.lowercase() }
                )
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val statuses: StateFlow<List<StatusEntity>> = dao.observeStatuses()
        .map { list ->
            val cutoff = System.currentTimeMillis() - 86_400_000L
            list.filter { it.timestamp >= cutoff }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun purgeExpiredStatuses() {
        runCatching {
            val cutoff = System.currentTimeMillis() - 86_400_000L
            val expired = dao.getExpiredStatuses(cutoff)
            if (expired.isNotEmpty()) {
                expired.forEach { old ->
                    old.localMediaPath?.let { path ->
                        val f = File(path)
                        if (f.exists() && f.absolutePath.contains("status")) {
                            f.delete()
                        }
                    }
                }
                dao.purgeExpiredStatuses(cutoff)
            }
        }
    }

    val viewOnceVault: StateFlow<List<MessageEntity>> = dao.observeViewOnceVault()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val presenceLogs: StateFlow<List<PresenceLogEntity>> = dao.observeAllPresenceLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profileHistory: StateFlow<List<ProfileHistoryEntity>> = dao.observeAllProfileHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectFilter(chip: HomeFilterChip) {
        _selectedFilter.value = chip
    }

    fun selectGroupsFilter(chip: HomeFilterChip) {
        _selectedGroupsFilter.value = chip
    }

    fun toggleArchivedIncluded() {
        _includeArchived.value = !_includeArchived.value
    }

    fun setIncludeArchived(include: Boolean) {
        _includeArchived.value = include
    }

    fun setLockedChatsRevealed(revealed: Boolean) {
        _lockedChatsRevealed.value = revealed
    }

    fun updateHomeSearch(query: String) {
        _homeSearchQuery.value = query
        viewModelScope.launch(Dispatchers.IO) {
            val cleaned = query.trim().replace("\"", "")
            if (cleaned.length < 2) {
                _ftsSearchResults.value = emptyList()
            } else {
                val results = runCatching {
                    dao.searchMessagesFts("$cleaned*")
                }.getOrDefault(emptyList())
                _ftsSearchResults.value = results
            }
        }
    }

    fun setActiveChat(chatJid: String?) {
        WaRealtimeService.activeChatJid.value = chatJid
        if (!chatJid.isNullOrBlank()) {
            WaRealtimeService.onChatOpened(appContext, chatJid)
        }
    }

    val downloadingMediaIds: StateFlow<Set<String>> = WaRealtimeService.downloadingMediaIds
    val failedMediaIds: StateFlow<Map<String, String>> = WaRealtimeService.failedMediaIds

    fun requestMediaDownload(msg: MessageEntity, userInitiated: Boolean = true) {
        WaRealtimeService.requestMediaDownload(appContext, msg, userInitiated)
    }

    fun requestStatusMediaDownload(status: StatusEntity, userInitiated: Boolean = true) {
        WaRealtimeService.requestStatusMediaDownload(appContext, status, userInitiated)
    }

    fun markStatusViewed(status: StatusEntity) {
        if (status.isViewed) return
        viewModelScope.launch {
            WaRealtimeService.markStatusViewed(appContext, status)
        }
    }

    fun observeChat(chatJid: String): Flow<ChatEntity?> = dao.observeChat(chatJid)

    fun observeRecentMessages(chatJid: String, limit: Int = 20): Flow<List<MessageEntity>> =
        dao.observeRecentMessages(chatJid, limit)

    fun observeMyReaction(messageId: String): Flow<String?> = dao.observeMyReaction(messageId)

    fun observeChatGalleryMedia(chatJid: String): Flow<List<MessageEntity>> =
        dao.observeChatGalleryMedia(chatJid)

    fun observeContact(jid: String): Flow<ContactEntity?> = dao.observeContact(jid)

    fun observeEffectivePrivacy(chatJid: String): Flow<EffectiveChatPrivacy> =
        StealthMatrixManager.observeEffectivePrivacy(dao, chatJid)

    fun canInitiateOutbound(chatJid: String): Flow<Boolean> {
        return if (chatJid.endsWith("@g.us")) {
            MutableStateFlow(true)
        } else {
            dao.observeRecentMessages(chatJid, 1).map { it.isNotEmpty() }
        }
    }

    private val pagerFlowCache = object : LinkedHashMap<String, Flow<PagingData<MessageEntity>>>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Flow<PagingData<MessageEntity>>>?
        ): Boolean = size > 12
    }

    fun pagedMessages(chatJid: String): Flow<PagingData<MessageEntity>> = synchronized(pagerFlowCache) {
        pagerFlowCache.getOrPut(chatJid) {
            Pager(
                config = PagingConfig(
                    pageSize = 60,
                    initialLoadSize = 120,
                    prefetchDistance = 20,
                    enablePlaceholders = false
                ),
                pagingSourceFactory = { dao.pagingMessages(chatJid) }
            ).flow.cachedIn(viewModelScope)
        }
    }

    suspend fun searchInChat(chatJid: String, query: String): List<MessageEntity> =
        withContext(Dispatchers.IO) {
            if (query.trim().length < 2) emptyList()
            else dao.searchInChat(chatJid, query.trim())
        }

    suspend fun findMessageExistsInChat(chatJid: String, messageId: String): Boolean =
        withContext(Dispatchers.IO) {
            val id = messageId.trim()
            id.isNotEmpty() && dao.getMessage(id)?.chatJid == chatJid
        }

    fun observeEdits(messageId: String): Flow<List<MessageEditEntity>> =
        dao.observeMessageEdits(messageId)

    fun observeGroupReceipts(messageId: String): Flow<List<GroupReceiptEntity>> =
        dao.observeGroupReceipts(messageId)

    fun toggleGhostPresence() {
        val next = !stealthConfig.value.ghostPresence
        viewModelScope.launch { WaRealtimeService.setGhostPresence(next) }
    }

    fun setThemeMode(themeMode: WuppThemeMode) {
        StealthMatrixManager.updateConfig { it.copy(themeMode = themeMode) }
    }

    fun setAccentColor(accentColor: com.wa.beastclient.ui.theme.WuppAccentColor) {
        StealthMatrixManager.updateConfig { it.copy(accentColor = accentColor) }
    }

    fun setUseSystemFont(enabled: Boolean) {
        StealthMatrixManager.updateConfig { it.copy(useSystemFont = enabled) }
    }

    fun setQuickReactionEmojis(emojis: List<String>) {
        StealthMatrixManager.updateConfig {
            it.copy(quickReactionEmojis = StealthMatrixManager.sanitizeQuickReactions(emojis))
        }
    }

    fun resetQuickReactionEmojis() {
        StealthMatrixManager.updateConfig {
            it.copy(quickReactionEmojis = StealthMatrixManager.DEFAULT_QUICK_REACTIONS)
        }
    }

    fun setAppLanguage(languageCode: String) {
        val normalized = when (languageCode) {
            LocaleHelper.LANG_EN, LocaleHelper.LANG_TR -> languageCode
            else -> LocaleHelper.LANG_SYSTEM
        }
        StealthMatrixManager.updateConfig { it.copy(appLanguage = normalized) }
    }

    fun setChatWallpaper(uri: String) {
        StealthMatrixManager.updateConfig { it.copy(chatWallpaperUri = uri) }
    }

    fun setChatWallpaperDim(dim: Float) {
        StealthMatrixManager.updateConfig { it.copy(chatWallpaperDim = dim.coerceIn(0f, 0.75f)) }
    }

    fun setChatWallpaperShowDoodle(show: Boolean) {
        StealthMatrixManager.updateConfig { it.copy(chatWallpaperShowDoodle = show) }
    }

    fun setChatWallpaperPosition(scale: Float, offsetX: Float, offsetY: Float) {
        StealthMatrixManager.updateConfig {
            it.copy(
                chatWallpaperScale = scale.coerceIn(1f, 3f),
                chatWallpaperOffsetX = offsetX.coerceIn(-1f, 1f),
                chatWallpaperOffsetY = offsetY.coerceIn(-1f, 1f)
            )
        }
    }

    fun importChatWallpaperFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = com.wa.beastclient.ui.components.copyCustomWallpaperImage(
                appContext,
                uri,
                keepFilePaths = StealthMatrixManager.referencedWallpaperFilePaths()
            )
            if (!saved.isNullOrBlank()) {
                StealthMatrixManager.updateConfig { it.copy(chatWallpaperUri = saved) }
            }
        }
    }

    fun setPerChatWallpaper(chatJid: String, uri: String) {
        StealthMatrixManager.setChatWallpaperOverride(chatJid) { prev ->
            prev.copy(uri = uri)
        }
    }

    fun setPerChatWallpaperDim(chatJid: String, dim: Float) {
        StealthMatrixManager.setChatWallpaperOverride(chatJid) { prev ->
            prev.copy(dim = dim.coerceIn(0f, 0.75f))
        }
    }

    fun setPerChatWallpaperShowDoodle(chatJid: String, show: Boolean) {
        StealthMatrixManager.setChatWallpaperOverride(chatJid) { prev ->
            prev.copy(showDoodle = show)
        }
    }

    fun setPerChatWallpaperPosition(chatJid: String, scale: Float, offsetX: Float, offsetY: Float) {
        StealthMatrixManager.setChatWallpaperOverride(chatJid) { prev ->
            prev.copy(
                scale = scale.coerceIn(1f, 3f),
                offsetXFraction = offsetX.coerceIn(-1f, 1f),
                offsetYFraction = offsetY.coerceIn(-1f, 1f)
            )
        }
    }

    fun importPerChatWallpaperFromUri(chatJid: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = com.wa.beastclient.ui.components.copyCustomWallpaperImage(
                appContext,
                uri,
                keepFilePaths = StealthMatrixManager.referencedWallpaperFilePaths()
            )
            if (!saved.isNullOrBlank()) {
                StealthMatrixManager.setChatWallpaperOverride(chatJid) { prev ->
                    prev.copy(uri = saved)
                }
            }
        }
    }

    fun clearPerChatWallpaper(chatJid: String, altChatJid: String = "") {
        StealthMatrixManager.clearChatWallpaperOverride(chatJid, altChatJid)
    }

    fun setAutoDownloadEnabled(enabled: Boolean) {
        StealthMatrixManager.updateConfig { it.copy(autoDownloadEnabled = enabled) }
        WaRealtimeService.syncAutoDownloadPolicy(appContext)
    }

    fun setAutoDownloadMask(network: AutoDownloadNetwork, mask: Int) {
        StealthMatrixManager.updateConfig { cfg ->
            when (network) {
                AutoDownloadNetwork.MOBILE -> cfg.copy(autoDownloadMobileMask = mask)
                AutoDownloadNetwork.WIFI -> cfg.copy(autoDownloadWifiMask = mask)
                AutoDownloadNetwork.ROAMING -> cfg.copy(autoDownloadRoamingMask = mask)
            }
        }
        WaRealtimeService.syncAutoDownloadPolicy(appContext)
    }

    fun setMediaVisibilityInGallery(visible: Boolean) {
        StealthMatrixManager.updateConfig { it.copy(mediaVisibilityInGallery = visible) }
    }

    fun setChatMediaVisibility(chatJid: String, overrideVisible: Boolean?) {
        StealthMatrixManager.setChatGalleryVisibility(chatJid, overrideVisible)
    }

    fun updateGlobalStealth(transform: (GlobalStealthConfig) -> GlobalStealthConfig) {
        val prevGhost = stealthConfig.value.ghostPresence
        val prevImmunity = stealthConfig.value.disappearingImmunity
        StealthMatrixManager.updateConfig(transform)
        val newGhost = stealthConfig.value.ghostPresence
        val newImmunity = stealthConfig.value.disappearingImmunity
        if (prevGhost != newGhost) {
            viewModelScope.launch { WaRealtimeService.setGhostPresence(newGhost) }
        }
        if (prevImmunity && !newImmunity) {
            WaRealtimeService.sweepExpiredEphemeralNow(appContext)
        }
    }

    fun sendChatPresence(chatJid: String, isComposing: Boolean, isAudio: Boolean = false) {
        viewModelScope.launch {
            WaRealtimeService.sendChatPresence(appContext, chatJid, isComposing, isAudio)
        }
    }

    fun saveChatPrivacyOverride(
        chatJid: String,
        sendRead: Boolean,
        sendPlayed: Boolean,
        sendTyping: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            StealthMatrixManager.setChatPrivacyOverride(dao, chatJid, sendRead, sendPlayed, sendTyping)
        }
    }

    fun clearChatPrivacyOverride(chatJid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            StealthMatrixManager.clearChatPrivacyOverride(dao, chatJid)
        }
    }

    fun toggleFavoriteChat(chat: ChatEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateFavorite(chat.jid, !chat.isFavorite)
        }
    }

    fun setChatLock(chatJid: String, locked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateChatLock(chatJid, locked)
        }
    }

    fun clearChatMessages(chatJid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteMessagesForChat(chatJid)
            dao.updateChatPreviewAfterClear(chatJid, "", "TEXT", System.currentTimeMillis(), 0)
        }
    }

    suspend fun fetchGroupParticipants(groupJid: String): List<String> = withContext(Dispatchers.IO) {
        if (!groupJid.endsWith("@g.us")) return@withContext emptyList()
        val fromBridge = WaRealtimeService.getGroupParticipants(groupJid)
        if (fromBridge.isNotEmpty()) {
            resolveParticipantsLocally(fromBridge)
            return@withContext fromBridge
        }
        val senders = dao.getDistinctSenders(groupJid)
        if (senders.isNotEmpty()) {
            resolveParticipantsLocally(senders)
            return@withContext senders
        }
        emptyList()
    }

    suspend fun fetchGroupAdmins(groupJid: String): Set<String> = withContext(Dispatchers.IO) {
        val admins = WaRealtimeService.getGroupAdminJids(groupJid)
        if (admins.isNotEmpty()) return@withContext admins
        if (groupJid == "120363044556677889@g.us") {
            return@withContext setOf("15550192834@s.whatsapp.net", "15550198811@s.whatsapp.net")
        }
        emptySet()
    }

    fun exportChatToText(chatJid: String, context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val msgs = dao.getMessagesForChat(chatJid)
            val chat = dao.getChat(chatJid)
            val title = chat?.name?.ifBlank { chatJid.substringBefore("@") } ?: chatJid.substringBefore("@")
            val sb = StringBuilder()
            sb.append("WhatsApp Chat with $title\n\n")
            msgs.reversed().forEach { m ->
                val time = java.text.SimpleDateFormat("dd/MM/yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(m.timestamp))
                val sender = if (m.isFromMe) "You" else m.senderName.ifBlank { m.senderJid.substringBefore("@") }
                sb.append("[$time] $sender: ${m.body}\n")
            }
            val file = java.io.File(context.cacheDir, "chat_${chatJid.filter { it.isLetterOrDigit() }}.txt")
            file.writeText(sb.toString())
            withContext(Dispatchers.Main) {
                runCatching {
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "Export chat"))
                }.onFailure {
                    showToast("Export failed: ${it.message}")
                }
            }
        }
    }

    fun togglePinChat(chat: ChatEntity) {
        val targetPinned = !chat.isPinned
        viewModelScope.launch(Dispatchers.IO) {
            if (targetPinned &&
                !chat.jid.endsWith("@newsletter") &&
                dao.getChat(chat.jid)?.isPinned != true &&
                dao.countPinnedChats() >= MAX_PINNED_CHATS
            ) {
                showToast("You can only pin up to $MAX_PINNED_CHATS chats")
                return@launch
            }
            dao.updatePinned(chat.jid, targetPinned, if (targetPinned) System.currentTimeMillis() else 0L)
            WaRealtimeService.syncChatPin(chat.jid, targetPinned)
        }
    }

    fun pinChats(jids: Collection<String>, pinned: Boolean) {
        if (jids.isEmpty()) return
        val distinctJids = jids.filter { it.isNotBlank() }.distinct()
        if (distinctJids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            if (pinned) {
                val currentlyPinned = dao.countPinnedChats()
                val newNonNewsletterPins = distinctJids.count { jid ->
                    !jid.endsWith("@newsletter") &&
                        jid != "status@broadcast" &&
                        !jid.endsWith("@broadcast") &&
                        dao.getChat(jid)?.isPinned != true
                }
                if (currentlyPinned + newNonNewsletterPins > MAX_PINNED_CHATS) {
                    showToast("You can only pin up to $MAX_PINNED_CHATS chats")
                    return@launch
                }
            }
            val order = if (pinned) System.currentTimeMillis() else 0L
            distinctJids.forEach { jid ->
                dao.updatePinned(jid, pinned, order)
                WaRealtimeService.syncChatPin(jid, pinned)
            }
        }
    }

    fun muteChats(jids: Collection<String>, muted: Boolean) {
        if (jids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            jids.forEach { jid ->
                dao.updateMuted(jid, muted, 0L)
                WaRealtimeService.syncChatMute(jid, muted)
            }
        }
    }

    fun archiveChats(jids: Collection<String>, archived: Boolean) {
        if (jids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            jids.forEach { jid ->
                dao.updateArchived(jid, archived)
                WaRealtimeService.syncChatArchive(jid, archived)
            }
        }
    }

    fun favoriteChats(jids: Collection<String>, favorite: Boolean) {
        if (jids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            jids.forEach { dao.updateFavorite(it, favorite) }
        }
    }

    fun setChatsUnreadLocal(jids: Collection<String>, unread: Boolean) {
        if (jids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val count = if (unread) 1 else 0
            jids.forEach { dao.updateUnreadCount(it, count) }
        }
    }

    fun deleteChatsLocally(jids: Collection<String>) {
        deleteChats(jids, syncToWhatsApp = false)
    }

    fun deleteChats(jids: Collection<String>, syncToWhatsApp: Boolean) {
        if (jids.isEmpty()) return
        viewModelScope.launch {
            WaRealtimeService.deleteChats(appContext, jids, syncToWhatsApp)
                .onSuccess {
                    if (syncToWhatsApp) {
                        showToast(if (jids.size == 1) "Chat deleted on WhatsApp" else "${jids.size} chats deleted on WhatsApp")
                    }
                }
                .onFailure { err ->
                    showToast("Deleted locally; WhatsApp sync failed: ${err.message ?: "not connected"}")
                }
        }
    }

    fun toggleChannelMute(chatJid: String, currentlyMuted: Boolean) {
        viewModelScope.launch {
            val targetMuted = !currentlyMuted
            WaRealtimeService.toggleChannelMute(appContext, chatJid, targetMuted)
                .onSuccess {
                    showToast(if (targetMuted) "Channel muted" else "Channel notifications on")
                }
                .onFailure {
                    showToast(if (targetMuted) "Channel muted locally" else "Channel unmuted locally")
                }
        }
    }

    fun isGroupAnnounceLocked(chatJid: String): Flow<Boolean> =
        WaRealtimeService.announceLockedGroups.map { it.contains(chatJid) }

    fun sendReaction(chatJid: String, target: MessageEntity, emoji: String) {
        viewModelScope.launch {
            WaRealtimeService.sendReaction(appContext, chatJid, target, emoji)
                .onFailure { err ->
                    showToast("Reaction saved locally (${err.message ?: "offline"})")
                }
        }
    }

    fun observePinnedMessages(chatJid: String): Flow<List<MessageEntity>> =
        dao.observePinnedMessages(chatJid)

    fun pinMessage(chatJid: String, target: MessageEntity, durationSec: Long) {
        viewModelScope.launch {
            WaRealtimeService.pinInChatMessage(
                context = appContext,
                chatJid = chatJid,
                targetMsgId = target.id,
                fromMe = target.isFromMe,
                senderJid = target.senderJid,
                durationSec = durationSec,
                isUnpin = false
            ).onFailure { err ->
                showToast("Pin saved locally (${err.message ?: "offline"})")
            }
        }
    }

    fun unpinMessage(chatJid: String, target: MessageEntity) {
        viewModelScope.launch {
            WaRealtimeService.pinInChatMessage(
                context = appContext,
                chatJid = chatJid,
                targetMsgId = target.id,
                fromMe = target.isFromMe,
                senderJid = target.senderJid,
                durationSec = 0L,
                isUnpin = true
            ).onFailure { err ->
                showToast("Unpin saved locally (${err.message ?: "offline"})")
            }
        }
    }

    fun pairWithPhoneNumber(phone: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val digits = phone.filter { it.isDigit() }
            if (digits.length < 7) {
                showToast("Enter country code + phone digits")
                return@launch
            }
            WaRealtimeService.pairWithPhoneNumber(digits)
                .onSuccess { code ->
                    showToast("Pairing Code: $code")
                    onResult(code)
                }
                .onFailure { err ->
                    showToast("Pair error: ${err.message}")
                    onResult("")
                }
        }
    }

    private var lastOfflineToggleMs: Long = 0L

    fun setOfflineMode(enabled: Boolean) {
        val current = StealthMatrixManager.config.value.offlineMode
        if (current == enabled) return
        val now = System.currentTimeMillis()
        if (now - lastOfflineToggleMs < 2_000L) {
            showToast("Hold on — switching connection state")
            return
        }
        lastOfflineToggleMs = now
        WaRealtimeService.setOfflineMode(appContext, enabled)
        showToast(
            if (enabled) "Wupp is now Offline — sync paused"
            else "Connecting Wupp back Live…"
        )
    }

    fun toggleOfflineMode() {
        setOfflineMode(!StealthMatrixManager.config.value.offlineMode)
    }

    fun reconnectSocket() {
        if (StealthMatrixManager.config.value.offlineMode) {
            setOfflineMode(false)
            return
        }
        val intent = Intent(appContext, WaRealtimeService::class.java).apply {
            action = WaRealtimeService.ACTION_RECONNECT
        }
        appContext.startService(intent)
    }

    fun sendText(
        chatJid: String,
        text: String,
        quoted: MessageEntity?,
        ghostMentionEveryone: Boolean
    ) {
        if (text.isBlank()) return
        viewModelScope.launch {
            WaRealtimeService.sendTextMessage(appContext, chatJid, text.trim(), quoted, ghostMentionEveryone)
                .onFailure { err ->
                    showToast(err.message ?: "Send failed")
                }
        }
    }

    fun forwardMessage(msg: MessageEntity, targetChatJids: List<String>) {
        if (targetChatJids.isEmpty()) return
        viewModelScope.launch {
            WaRealtimeService.forwardMessageToChats(appContext, msg, targetChatJids)
                .onSuccess { count ->
                    showToast("Forwarded to $count ${if (count == 1) "chat" else "chats"}")
                }
                .onFailure { err ->
                    showToast(err.message ?: "Forward failed")
                }
        }
    }

    fun sendStatusReply(
        status: StatusEntity,
        text: String,
        onDone: ((Boolean) -> Unit)? = null
    ) {
        if (text.isBlank()) return
        viewModelScope.launch {
            WaRealtimeService.sendStatusReply(appContext, status, text.trim())
                .onSuccess {
                    showToast("Reply sent")
                    onDone?.invoke(true)
                }
                .onFailure { err ->
                    showToast(err.message ?: "Failed to send status reply")
                    onDone?.invoke(false)
                }
        }
    }

    fun sendStatusReaction(
        status: StatusEntity,
        emoji: String
    ) {
        viewModelScope.launch {
            WaRealtimeService.sendStatusReaction(appContext, status, emoji)
                .onFailure { err ->
                    showToast("Reaction failed: ${err.message}")
                }
        }
    }

    fun editMessage(chatJid: String, messageId: String, newText: String) {
        if (newText.isBlank()) return
        viewModelScope.launch {
            WaRealtimeService.editOwnMessage(appContext, chatJid, messageId, newText.trim())
                .onFailure { showToast("Edit error: ${it.message}") }
        }
    }

    fun revokeMessageForEveryone(chatJid: String, messageId: String) {
        val antiDeleteOn = stealthConfig.value.antiDeleteEnabled
        viewModelScope.launch {
            WaRealtimeService.revokeMessageForEveryone(appContext, chatJid, messageId)
                .onSuccess {
                    showToast(
                        if (antiDeleteOn) "Deleted for everyone · Local copy kept (Anti-Delete)"
                        else "Message deleted for everyone"
                    )
                }
                .onFailure { err ->
                    showToast(err.message ?: "Delete for everyone failed")
                }
        }
    }

    fun deleteMessageFromDevice(messageId: String) {
        viewModelScope.launch {
            WaRealtimeService.deleteMessagePermanently(appContext, messageId)
                .onSuccess {
                    showToast("Deleted from device")
                }
                .onFailure { err ->
                    showToast(err.message ?: "Delete failed")
                }
        }
    }

    fun markChatReadExplicitly(chatJid: String) {
        viewModelScope.launch {
            WaRealtimeService.markChatReadExplicitly(appContext, chatJid)
                .onSuccess { count ->
                    showToast("Sent manual Read receipts ($count msgs)")
                }
        }
    }

    fun markAudioPlayedExplicitly(msg: MessageEntity) {
        viewModelScope.launch {
            WaRealtimeService.sendManualPlayedReceipt(appContext, msg)
                .onSuccess { showToast("Sent manual Played receipt") }
        }
    }

    suspend fun stageMediaForPreview(uri: Uri, rawMimeType: String?): StagedOutboundMedia? {
        val resolvedMime = rawMimeType?.takeIf {
            it.isNotBlank() && !it.equals("application/octet-stream", ignoreCase = true)
        } ?: "application/octet-stream"
        val ext = when {
            resolvedMime.contains("png", ignoreCase = true) -> "png"
            resolvedMime.contains("webp", ignoreCase = true) -> "webp"
            resolvedMime.contains("gif", ignoreCase = true) -> "gif"
            resolvedMime.contains("mp4", ignoreCase = true) -> "mp4"
            resolvedMime.contains("quicktime", ignoreCase = true) -> "mov"
            resolvedMime.contains("x-matroska", ignoreCase = true) -> "mkv"
            resolvedMime.contains("webm", ignoreCase = true) -> "webm"
            resolvedMime.contains("3gpp", ignoreCase = true) -> "3gp"
            resolvedMime.contains("pdf", ignoreCase = true) -> "pdf"
            resolvedMime.startsWith("video/", ignoreCase = true) -> "mp4"
            resolvedMime.startsWith("image/", ignoreCase = true) -> "jpg"
            else -> "bin"
        }
        val copied = EagerMediaVault.copyUriToCacheFile(appContext, uri, ext)
        if (copied == null) {
            showToast("Unable to read selected file")
            return null
        }
        val isVideo = resolvedMime.startsWith("video/", ignoreCase = true)
        val durationSec = if (isVideo) {
            withContext(Dispatchers.IO) { probeVideoMetadata(copied).durationSec }
        } else {
            0
        }
        return StagedOutboundMedia(
            file = copied,
            mimeType = resolvedMime,
            isVideo = isVideo,
            durationSec = durationSec,
            fileSize = copied.length()
        )
    }

    fun sendStagedMedia(
        chatJid: String,
        staged: StagedOutboundMedia,
        caption: String,
        qualityMode: OutboundQualityMode,
        isViewOnce: Boolean = false
    ) {
        viewModelScope.launch {
            WaRealtimeService.sendMediaMessage(
                context = appContext,
                chatJid = chatJid,
                sourceFile = staged.file,
                mimeType = staged.mimeType,
                caption = caption,
                qualityMode = qualityMode,
                isVoiceNote = false,
                durationSec = staged.durationSec,
                isViewOnce = isViewOnce
            ).onFailure { err ->
                showToast(err.message ?: "Media send failed")
            }
        }
    }

    fun sendPickedMedia(
        chatJid: String,
        uri: Uri,
        mimeType: String,
        caption: String,
        qualityMode: OutboundQualityMode
    ) {
        viewModelScope.launch {
            val staged = stageMediaForPreview(uri, mimeType) ?: return@launch
            WaRealtimeService.sendMediaMessage(
                context = appContext,
                chatJid = chatJid,
                sourceFile = staged.file,
                mimeType = staged.mimeType,
                caption = caption,
                qualityMode = qualityMode,
                isVoiceNote = false,
                durationSec = staged.durationSec
            ).onFailure { err ->
                showToast(err.message ?: "Media send failed")
            }
        }
    }

    fun sendSticker(chatJid: String, stickerFile: File) {
        viewModelScope.launch {
            if (!stickerFile.exists() || stickerFile.length() == 0L) {
                showToast("Sticker file not found")
                return@launch
            }
            WaRealtimeService.sendMediaMessage(
                context = appContext,
                chatJid = chatJid,
                sourceFile = stickerFile,
                mimeType = "image/webp",
                caption = "",
                qualityMode = OutboundQualityMode.STICKER,
                isVoiceNote = false,
                durationSec = 0
            ).onFailure { err ->
                showToast(err.message ?: "Failed to send sticker")
            }
        }
    }

    fun sendGifMessage(chatJid: String, gifMp4File: File, caption: String = "", durationSec: Int = 0) {
        viewModelScope.launch {
            if (!gifMp4File.exists() || gifMp4File.length() == 0L) {
                showToast("GIF file not found")
                return@launch
            }
            WaRealtimeService.sendMediaMessage(
                context = appContext,
                chatJid = chatJid,
                sourceFile = gifMp4File,
                mimeType = "video/mp4",
                caption = caption,
                qualityMode = OutboundQualityMode.GIF,
                isVoiceNote = false,
                durationSec = durationSec
            ).onFailure { err ->
                showToast(err.message ?: "Failed to send GIF")
            }
        }
    }

    fun toggleFavoriteSticker(msg: MessageEntity): Boolean {
        val key = msg.mediaSha256?.takeIf { it.isNotBlank() }
            ?: msg.localMediaPath?.takeIf { it.isNotBlank() }
            ?: msg.id
        return stickerFavoritesStore.toggleFavorite(key)
    }

    fun isStickerFavorite(msg: MessageEntity): Boolean {
        val key = msg.mediaSha256?.takeIf { it.isNotBlank() }
            ?: msg.localMediaPath?.takeIf { it.isNotBlank() }
            ?: msg.id
        return stickerFavoritesStore.isFavorite(key)
    }

    fun sendStickerFromUri(chatJid: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val cachedFile = EagerMediaVault.copyUriToCacheFile(appContext, uri, "webp") ?: return@launch
            val prepared = EagerMediaVault.prepareOutboundMedia(
                context = appContext,
                sourceFile = cachedFile,
                mimeType = "image/webp",
                mode = OutboundQualityMode.STICKER
            )
            sendSticker(chatJid, prepared.file)
        }
    }

    fun toggleVoiceRecording(chatJid: String, hasRecordPermission: Boolean) {
        if (_isRecordingVoice.value) {
            stopAndSendVoiceRecording(chatJid)
        } else {
            startVoiceRecording(hasRecordPermission)
        }
    }

    private fun startVoiceRecording(hasRecordPermission: Boolean) {
        if (!hasRecordPermission) {
            showToast("Microphone permission required to record voice notes")
            return
        }
        val mediaDir = EagerMediaVault.mediaDir(appContext)
        val ts = System.currentTimeMillis()

        // Prefer deterministic AudioRecord + MediaCodec(audio/opus) + RFC 7845 OggOpus muxing on Android 10+
        // so Official WhatsApp always receives spec-compliant Ogg/Opus PTT frames.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val opusFile = File(mediaDir, "ptt_rec_${ts}_opus.ogg")
            val opusRec = OpusOggVoiceRecorder(opusFile)
            if (opusRec.start()) {
                SoundPoolManager.playPttStart()
                activeOpusRecorder = opusRec
                activeRecorder = null
                activeRecordingFile = opusFile
                recordingStartMs = System.currentTimeMillis()
                _isRecordingVoice.value = true
                return
            } else {
                runCatching { opusFile.delete() }
            }
        }

        var startedRecorder: MediaRecorder? = null
        var startedFile: File? = null

        for ((idx, profile) in buildVoiceRecorderProfiles(Build.VERSION.SDK_INT).withIndex()) {
            val candidate = File(mediaDir, "ptt_rec_${ts}_$idx.${profile.ext}")
            val rec = runCatching {
                val instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(appContext)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                instance.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    if (profile.isOggOpus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        setOutputFormat(MediaRecorder.OutputFormat.OGG)
                        setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                    } else {
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    }
                    setAudioChannels(1)
                    setAudioSamplingRate(profile.sampleRateHz)
                    setAudioEncodingBitRate(profile.bitRate)
                    setOutputFile(candidate.absolutePath)
                    prepare()
                    start()
                }
                instance
            }.onFailure {
                runCatching { candidate.delete() }
            }.getOrNull()

            if (rec != null) {
                startedRecorder = rec
                startedFile = candidate
                break
            }
        }

        if (startedRecorder == null || startedFile == null) {
            activeRecorder = null
            activeOpusRecorder = null
            activeRecordingFile = null
            _isRecordingVoice.value = false
            showToast("Unable to access microphone to record audio")
            return
        }

        SoundPoolManager.playPttStart()
        activeOpusRecorder = null
        activeRecorder = startedRecorder
        activeRecordingFile = startedFile
        recordingStartMs = System.currentTimeMillis()
        _isRecordingVoice.value = true
    }

    fun cancelVoiceRecording() {
        SoundPoolManager.playPttCancel()
        activeOpusRecorder?.cancelAndDelete()
        activeOpusRecorder = null
        runCatching { activeRecorder?.stop() }
        runCatching { activeRecorder?.release() }
        activeRecorder = null
        activeRecordingFile?.delete()
        activeRecordingFile = null
        _isRecordingVoice.value = false
    }

    private fun stopAndSendVoiceRecording(chatJid: String) {
        val opusRec = activeOpusRecorder
        activeOpusRecorder = null
        val rec = activeRecorder
        activeRecorder = null
        val file = activeRecordingFile
        activeRecordingFile = null
        _isRecordingVoice.value = false

        val elapsedMs = (System.currentTimeMillis() - recordingStartMs).coerceAtLeast(0L)
        val stopSucceeded = when {
            opusRec != null -> opusRec.stopAndFinalize()
            rec != null -> {
                val ok = runCatching { rec.stop() }.isSuccess
                runCatching { rec.release() }
                ok
            }
            else -> false
        }

        if (!stopSucceeded || elapsedMs < 600L || file == null || !file.exists() || file.length() <= 1024L) {
            runCatching { file?.delete() }
            showToast(
                if (elapsedMs < 600L) "Hold longer to record a voice message"
                else "Voice recording failed — please try again"
            )
            return
        }

        SoundPoolManager.playPttEnd()
        val elapsedSec = ((elapsedMs + 500L) / 1000L).toInt().coerceIn(1, 300)
        val recordedWaveformCsv = opusRec?.waveformCsv().orEmpty()

        viewModelScope.launch {
            val validatedDurationSec = kotlinx.coroutines.withContext(Dispatchers.IO) {
                probeValidRecordedAudioDurationSec(file, elapsedSec)
            }
            if (validatedDurationSec <= 0) {
                runCatching { file.delete() }
                showToast("Recorded voice file was unreadable — please try again")
                return@launch
            }

            val dispatch = resolveOutboundAudioDispatch(file.extension)
            WaRealtimeService.sendMediaMessage(
                context = appContext,
                chatJid = chatJid,
                sourceFile = file,
                mimeType = dispatch.mimeType,
                caption = "",
                qualityMode = OutboundQualityMode.STANDARD,
                isVoiceNote = dispatch.isVoiceNote,
                durationSec = validatedDurationSec,
                waveformCsvOverride = if (dispatch.isVoiceNote) recordedWaveformCsv else ""
            ).onFailure { err ->
                showToast(err.message ?: "Voice send failed")
            }
        }
    }

    fun exportMediaToGallery(localPath: String?, displayName: String, mimeType: String) {
        if (localPath.isNullOrBlank()) {
            showToast("No decrypted file on disk for this item")
            return
        }
        viewModelScope.launch {
            val resolvedMime = EagerMediaVault.resolveDownloadExportSpec(displayName, mimeType).second
            val isGalleryMedia = resolvedMime.startsWith("image/") || resolvedMime.startsWith("video/")
            EagerMediaVault.exportToGallery(appContext, File(localPath), displayName, resolvedMime)
                .onSuccess {
                    val where = if (isGalleryMedia) "Gallery" else "Downloads/Wupp"
                    showToast("Saved to $where ($displayName)")
                }
                .onFailure { showToast("Export failed: ${it.message}") }
        }
    }

    fun seedShowcaseData() {
        viewModelScope.launch {
            WaRealtimeService.seedBenchmarkAndShowcase(appContext)
                .onSuccess { showToast("Seeded 1,220-msg thread + all Mod showcases") }
                .onFailure { showToast("Seed failed: ${it.message}") }
        }
    }

    fun recordPrimaryHeartbeatNow() {
        StealthMatrixManager.recordPrimaryHeartbeat()
        showToast("Primary phone heartbeat reset (14d window refreshed)")
    }

    fun simulate11DayHeartbeatGap() {
        StealthMatrixManager.simulateElapsedDays(11)
        showToast("Simulated 11 days elapsed -> 3 days until companion unlink warning active")
    }

    fun verifyBulkNumbers(phonesCsv: String) {
        if (phonesCsv.isBlank()) return
        viewModelScope.launch {
            WaRealtimeService.verifyBulkNumbers(phonesCsv)
                .onSuccess { _bulkVerifyOutput.value = it }
                .onFailure { _bulkVerifyOutput.value = "Error: ${it.message}" }
        }
    }

    fun checkForUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            val result = WuppUpdater.checkForUpdates(appContext, silent = silent)
            if (!silent) {
                when (result) {
                    is UpdateUiState.UpToDate -> showToast("You're on the latest Wupp (v${result.currentVersion})")
                    is UpdateUiState.Error -> showToast(result.message)
                    else -> Unit
                }
            }
        }
    }

    fun downloadUpdate(release: AppReleaseInfo) {
        viewModelScope.launch {
            WuppUpdater.downloadUpdate(appContext, release)
            val current = WuppUpdater.state.value
            if (current is UpdateUiState.Error) {
                showToast(current.message)
            }
        }
    }

    fun installDownloadedApk(apkFile: File) {
        WuppUpdater.installApk(appContext, apkFile).onFailure { err ->
            showToast(err.message ?: "Unable to launch APK installer")
        }
    }

    fun installExternalApkFallback(apkFile: File) {
        WuppUpdater.installViaExternalChooser(appContext, apkFile).onFailure { err ->
            showToast(err.message ?: "Unable to open external APK installer")
        }
    }

    fun openUpdatePrompt() {
        WuppUpdater.openPromptDialog()
    }

    fun dismissUpdatePrompt(skipVersion: Boolean = false) {
        WuppUpdater.dismissPromptDialog(appContext, skipVersion = skipVersion)
    }

    fun setAutoUpdateEnabled(enabled: Boolean) {
        WuppUpdater.setAutoCheckEnabled(appContext, enabled)
    }

    fun setIncludePreReleases(enabled: Boolean) {
        WuppUpdater.setIncludePreReleases(appContext, enabled)
        viewModelScope.launch {
            WuppUpdater.checkForUpdates(appContext, silent = !enabled)
        }
    }

    private val _participantMeta = MutableStateFlow<Map<String, ResolvedParticipantUi>>(emptyMap())
    val participantMeta: StateFlow<Map<String, ResolvedParticipantUi>> = _participantMeta.asStateFlow()

    private val _quotedMsgMeta = MutableStateFlow<Map<String, QuotedSenderMeta>>(emptyMap())
    val quotedMsgMeta: StateFlow<Map<String, QuotedSenderMeta>> = _quotedMsgMeta.asStateFlow()

    fun syncDeviceContactFallbacks(forceRefresh: Boolean = true, notifyUser: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = DeviceContactFallbackResolver.backfillMissingContactNames(
                context = appContext,
                dao = dao,
                forceRefresh = forceRefresh
            )
            // Drop cached participant entries that had neither saved nor push names from the server so open screens re-resolve.
            val currentMeta = _participantMeta.value
            val staleKeys = currentMeta.entries
                .filter { (_, v) ->
                    DeviceContactFallbackResolver.isServerNameMissing(v.savedName, v.jid, v.altJid) &&
                        DeviceContactFallbackResolver.isServerNameMissing(v.pushName, v.jid, v.altJid)
                }
                .map { it.key }
            if (staleKeys.isNotEmpty()) {
                _participantMeta.value = currentMeta - staleKeys.toSet()
            }
            if (notifyUser) {
                val msg = if (updated > 0) {
                    "Matched $updated missing contact name(s) from device"
                } else {
                    "Device contact fallback active · server names prioritized"
                }
                showToast(msg)
            }
        }
    }

    fun resolveParticipantsLocally(jids: Collection<String>) {
        val current = _participantMeta.value
        val missing = jids
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains("@") && !current.containsKey(it) }
            .distinct()
            .take(80)
            .toList()
        if (missing.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val raw = WaRealtimeService.resolveParticipantNamesJson(missing.joinToString(","))
            val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return@launch
            val parsed = HashMap<String, ResolvedParticipantUi>(missing.size)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val item = obj.optJSONObject(key) ?: continue
                val altJid = item.optString("altJid")
                val serverSaved = item.optString("savedName")
                val fallbackName = item.optString("name")
                val serverPush = item.optString("pushName").ifBlank { fallbackName }
                val phone = item.optString("phone")
                val dbContactName = listOfNotNull(
                    dao.getContact(key)?.name,
                    altJid.takeIf { it.isNotBlank() }?.let { dao.getContact(it)?.name },
                    dao.getChat(key)?.name,
                    altJid.takeIf { it.isNotBlank() }?.let { dao.getChat(it)?.name }
                ).firstOrNull {
                    DeviceContactFallbackResolver.isUsableDisplayName(it, key, altJid)
                }.orEmpty()
                val bestServerSaved = serverSaved.takeIf {
                    DeviceContactFallbackResolver.isUsableDisplayName(it, key, altJid)
                }.orEmpty()
                val effectiveSaved = when {
                    bestServerSaved.isNotBlank() -> bestServerSaved
                    dbContactName.isNotBlank() -> dbContactName
                    DeviceContactFallbackResolver.isServerNameMissing(serverPush, key, altJid) -> {
                        DeviceContactFallbackResolver.lookupForParticipant(
                            context = appContext,
                            jid = key,
                            altJid = altJid,
                            phone = phone
                        ).orEmpty()
                    }
                    else -> ""
                }
                val cleanPush = serverPush.takeIf {
                    DeviceContactFallbackResolver.isUsableDisplayName(it, key, altJid)
                }.orEmpty()
                parsed[key] = ResolvedParticipantUi(
                    jid = key,
                    altJid = altJid,
                    savedName = effectiveSaved,
                    pushName = cleanPush,
                    phone = phone,
                    avatarPath = item.optString("avatarPath"),
                    isSelf = item.optBoolean("isSelf", false)
                )
            }
            // Mark any queried JID not returned as checked so we don't re-query every recomposition.
            missing.forEach { k ->
                if (!parsed.containsKey(k)) {
                    parsed[k] = ResolvedParticipantUi(jid = k)
                }
            }
            if (parsed.isNotEmpty()) {
                _participantMeta.value = _participantMeta.value + parsed
            }
        }
    }

    fun resolveQuotedSendersLocally(quotedIds: Collection<String>) {
        val current = _quotedMsgMeta.value
        val missing = quotedIds
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !current.containsKey(it) }
            .distinct()
            .take(50)
            .toList()
        if (missing.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val resolved = HashMap<String, QuotedSenderMeta>(missing.size)
            val discoveredJids = mutableSetOf<String>()
            for (qid in missing) {
                val msg = dao.getMessage(qid)
                if (msg != null) {
                    resolved[qid] = QuotedSenderMeta(
                        senderJid = msg.senderJid,
                        senderName = msg.senderName,
                        isFromMe = msg.isFromMe
                    )
                    if (msg.senderJid.contains("@")) {
                        discoveredJids += msg.senderJid
                    }
                } else {
                    resolved[qid] = QuotedSenderMeta()
                }
            }
            if (resolved.isNotEmpty()) {
                _quotedMsgMeta.value = _quotedMsgMeta.value + resolved
            }
            if (discoveredJids.isNotEmpty()) {
                resolveParticipantsLocally(discoveredJids)
            }
        }
    }

    fun requestFullProfilePicture(jid: String) {
        if (jid.isBlank()) return
        WaRealtimeService.requestFullProfilePicture(jid)
    }

    suspend fun fetchOwnProfile() = WaRealtimeService.fetchOwnProfile()

    suspend fun saveOwnPushName(name: String) = WaRealtimeService.updateOwnPushName(name)
        .onSuccess { showToast("Display name updated") }
        .onFailure { showToast(it.message ?: "Failed to update display name") }

    suspend fun saveOwnAbout(about: String) = WaRealtimeService.updateOwnAbout(about)
        .onSuccess { showToast("About updated") }
        .onFailure { showToast(it.message ?: "Failed to update About") }

    suspend fun fetchAccountPrivacy() = WaRealtimeService.fetchAccountPrivacySettings()

    suspend fun setAccountPrivacy(category: String, value: String) =
        WaRealtimeService.updateAccountPrivacySetting(category, value)
            .onFailure { showToast(it.message ?: "Failed to update privacy setting") }

    suspend fun setDefaultDisappearingTimer(seconds: Int) =
        WaRealtimeService.setDefaultDisappearingTimerSec(seconds)
            .onSuccess { showToast("Default timer updated") }
            .onFailure { showToast(it.message ?: "Failed to update timer") }

    suspend fun fetchBlockedContacts() = WaRealtimeService.fetchBlockedContacts()

    suspend fun setContactBlocked(jid: String, blocked: Boolean) =
        WaRealtimeService.setContactBlocked(jid, blocked)
            .onSuccess { showToast(if (blocked) "Contact blocked" else "Contact unblocked") }
            .onFailure { showToast(it.message ?: "Blocklist update failed") }

    suspend fun loadStorageStats() = kotlinx.coroutines.withContext(Dispatchers.IO) {
        WaRealtimeService.computeStorageStats(appContext)
    }

    suspend fun clearTempCache(): Long = kotlinx.coroutines.withContext(Dispatchers.IO) {
        WaRealtimeService.clearSafeTempCache(appContext)
    }

    private fun showToast(text: String) {
        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show()
        }
    }
}

data class ResolvedParticipantUi(
    val jid: String,
    val altJid: String = "",
    val savedName: String = "",
    val pushName: String = "",
    val phone: String = "",
    val avatarPath: String = "",
    val isSelf: Boolean = false
)

data class QuotedSenderMeta(
    val senderJid: String = "",
    val senderName: String = "",
    val isFromMe: Boolean = false
)

internal data class VoiceRecorderProfile(
    val isOggOpus: Boolean,
    val sampleRateHz: Int,
    val bitRate: Int,
    val ext: String
)

internal data class OutboundAudioDispatch(
    val mimeType: String,
    val isVoiceNote: Boolean
)

internal fun buildVoiceRecorderProfiles(sdkInt: Int): List<VoiceRecorderProfile> =
    if (sdkInt >= Build.VERSION_CODES.Q) {
        listOf(
            VoiceRecorderProfile(isOggOpus = true, sampleRateHz = 48_000, bitRate = 32_000, ext = "ogg"),
            VoiceRecorderProfile(isOggOpus = true, sampleRateHz = 16_000, bitRate = 24_000, ext = "ogg"),
            VoiceRecorderProfile(isOggOpus = false, sampleRateHz = 44_100, bitRate = 64_000, ext = "m4a")
        )
    } else {
        listOf(
            VoiceRecorderProfile(isOggOpus = false, sampleRateHz = 44_100, bitRate = 64_000, ext = "m4a")
        )
    }

internal fun resolveOutboundAudioDispatch(extension: String): OutboundAudioDispatch {
    val cleanExt = extension.trim().lowercase()
    return if (cleanExt == "ogg" || cleanExt == "opus") {
        OutboundAudioDispatch(mimeType = "audio/ogg; codecs=opus", isVoiceNote = true)
    } else {
        // Send AAC/M4A as standard audio instead of PTT=true so WhatsApp's Opus PTT player never fails to open it.
        OutboundAudioDispatch(mimeType = "audio/mp4", isVoiceNote = false)
    }
}

internal fun hasValidAudioContainerHeader(extension: String, headerBytes: ByteArray): Boolean {
    val cleanExt = extension.trim().lowercase()
    return when (cleanExt) {
        "ogg", "opus" -> {
            headerBytes.size >= 4 &&
                headerBytes[0] == 'O'.code.toByte() &&
                headerBytes[1] == 'g'.code.toByte() &&
                headerBytes[2] == 'g'.code.toByte() &&
                headerBytes[3] == 'S'.code.toByte()
        }
        "m4a", "mp4" -> {
            headerBytes.size >= 8 &&
                headerBytes[4] == 'f'.code.toByte() &&
                headerBytes[5] == 't'.code.toByte() &&
                headerBytes[6] == 'y'.code.toByte() &&
                headerBytes[7] == 'p'.code.toByte()
        }
        else -> headerBytes.size >= 8
    }
}

private fun probeValidRecordedAudioDurationSec(file: File, fallbackElapsedSec: Int): Int {
    if (!file.exists() || file.length() <= 1024L) return 0
    val header = ByteArray(16)
    val readLen = runCatching {
        file.inputStream().use { input -> input.read(header).coerceAtLeast(0) }
    }.getOrDefault(0)
    if (!hasValidAudioContainerHeader(file.extension, header.copyOf(readLen))) return 0
    if (!hasExtractableAudioTrack(file)) return 0

    val retriever = MediaMetadataRetriever()
    val durationMs = try {
        retriever.setDataSource(file.absolutePath)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: Throwable) {
        -1L
    } finally {
        runCatching { retriever.release() }
    }
    if (durationMs < 0L) return 0
    if (durationMs == 0L) return fallbackElapsedSec.coerceIn(1, 300)
    return ((durationMs + 500L) / 1000L).toInt().coerceIn(1, 300)
}
