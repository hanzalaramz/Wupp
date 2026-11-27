package com.wa.beastclient.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.wa.beastclient.domain.drafts.ChatDraftManager
import com.wa.beastclient.domain.appearance.HomeAppearanceConfig
import com.wa.beastclient.domain.appearance.HomeAppearanceManager
import com.wa.beastclient.domain.appearance.HomeBadgeStyle
import com.wa.beastclient.domain.appearance.HomeLayoutPreset
import com.wa.beastclient.domain.appearance.HomeNavStyle
import com.wa.beastclient.domain.appearance.HomeNewChatStyle
import com.wa.beastclient.domain.appearance.HomeSectionKind
import com.wa.beastclient.domain.appearance.HomeSurfaceTone
import com.wa.beastclient.ui.components.HomeAppearanceSheet
import com.wa.beastclient.ui.components.WuppBrandMark
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import com.wa.beastclient.BuildConfig
import com.wa.beastclient.R
import com.wa.beastclient.core.calling.CallStateManager
import com.wa.beastclient.core.calling.extractDialablePhoneDigits
import com.wa.beastclient.core.logging.AppLogManager
import com.wa.beastclient.core.service.OwnProfileUi
import com.wa.beastclient.core.service.WaRealtimeService
import com.wa.beastclient.core.update.UpdateUiState
import com.wa.beastclient.data.db.ChatEntity
import com.wa.beastclient.data.db.ContactEntity
import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.data.db.StatusEntity
import com.wa.beastclient.domain.contacts.DeviceContactFallbackResolver
import com.wa.beastclient.domain.security.AppLockManager
import com.wa.beastclient.domain.security.AppLockMode
import com.wa.beastclient.domain.stealth.AntiCallMode
import com.wa.beastclient.domain.stealth.AutoDownloadNetwork
import com.wa.beastclient.domain.stealth.CallBackendMode
import com.wa.beastclient.domain.stealth.CallPipTapAction
import com.wa.beastclient.domain.stealth.GlobalStealthConfig
import com.wa.beastclient.domain.stealth.StealthMatrixManager
import com.wa.beastclient.ui.HomeFilterChip
import com.wa.beastclient.ui.calling.ActiveCallFloatingBar
import com.wa.beastclient.ui.calling.CallingSetupCard
import com.wa.beastclient.ui.calling.CallingSetupDialog
import com.wa.beastclient.ui.components.AppLanguageDialog
import com.wa.beastclient.ui.components.AppLockConfigDialog
import com.wa.beastclient.ui.components.AutoDownloadNetworkDialog
import com.wa.beastclient.ui.components.BlockedContactsDialog
import com.wa.beastclient.ui.components.ChatFontSizeDialog
import com.wa.beastclient.ui.components.CompanionScopeInfoDialog
import com.wa.beastclient.ui.components.ContactQrCodeSheet
import com.wa.beastclient.ui.components.DiagnosticsLogsDialog
import com.wa.beastclient.ui.components.ExpressiveIconBadge
import com.wa.beastclient.ui.components.ExpressiveSegmentPosition
import com.wa.beastclient.ui.components.ExpressiveSegmentedActionRow
import com.wa.beastclient.ui.components.ExpressiveSegmentedContainer
import com.wa.beastclient.ui.components.ExpressiveSegmentedGroup
import com.wa.beastclient.ui.components.ExpressiveSegmentedSliderRow
import com.wa.beastclient.ui.components.ExpressiveSegmentedSwitchRow
import com.wa.beastclient.ui.components.FullScreenMediaViewer
import com.wa.beastclient.ui.components.FullScreenStatusViewer
import com.wa.beastclient.ui.components.GalleryVisibilityContactsDialog
import com.wa.beastclient.ui.components.ProfileEditorDialog
import com.wa.beastclient.ui.components.SettingsProfileHeaderCard
import com.wa.beastclient.ui.components.StatusComposerMode
import com.wa.beastclient.ui.components.StatusComposerSheet
import com.wa.beastclient.ui.components.StatusGroup
import com.wa.beastclient.ui.components.StorageUsageDialog
import com.wa.beastclient.ui.components.WhatsAppAccountPrivacyDialog
import com.wa.beastclient.ui.components.buildContactStatusSummaries
import com.wa.beastclient.ui.components.formatFontScaleLabel
import com.wa.beastclient.ui.components.isOwnStatusSender
import com.wa.beastclient.ui.components.partitionOwnAndContactStatuses
import androidx.compose.material.icons.rounded.ManageAccounts
import com.wa.beastclient.ui.components.AccountSwitcherSheet
import com.wa.beastclient.ui.components.GitHubFeedbackDialog
import com.wa.beastclient.ui.components.GitHubFeedbackType
import com.wa.beastclient.ui.MainViewModel
import com.wa.beastclient.ui.components.ChatSelectionState
import com.wa.beastclient.ui.components.DeliveryTickIcon
import com.wa.beastclient.ui.components.FloatingChatSelectionMenuOverlay
import com.wa.beastclient.ui.components.MediaVaultSheet
import com.wa.beastclient.ui.components.applyChatLongPress
import com.wa.beastclient.ui.components.applyChatTapInSelection
import com.wa.beastclient.ui.components.MessageHistorySheet
import com.wa.beastclient.ui.components.PerChatQuickControlsSheet
import com.wa.beastclient.ui.components.QuickContactDialog
import com.wa.beastclient.ui.components.ReactionEmojiCustomizerDialog
import com.wa.beastclient.ui.components.StickerStudioDialog
import com.wa.beastclient.ui.components.ThemePickerDialog
import com.wa.beastclient.ui.components.ThemeSelectorGrid
import com.wa.beastclient.ui.components.WallpaperPickerDialog
import com.wa.beastclient.ui.components.WuppGlassCircleButton
import com.wa.beastclient.ui.components.WuppGlassContainer
import com.wa.beastclient.ui.components.WuppGlassNavBar
import com.wa.beastclient.ui.components.WuppGlassTopBar
import com.wa.beastclient.ui.components.describeChatWallpaper
import com.wa.beastclient.ui.components.expressivePressScale
import com.wa.beastclient.ui.components.findFragmentActivity
import com.wa.beastclient.ui.components.formatDeletedLabel
import com.wa.beastclient.ui.components.formatMessageClock
import com.wa.beastclient.ui.components.launchDeviceAuthPrompt
import com.wa.beastclient.ui.components.rememberWuppBackdropState
import com.wa.beastclient.ui.components.wuppBackdropSource
import com.wa.beastclient.ui.theme.LocalWaColors
import com.wa.beastclient.ui.theme.LocalWaMonoFamily
import com.wa.beastclient.ui.theme.LocalWuppShowSeconds
import com.wa.beastclient.ui.theme.LocalWuppUse24Hour
import com.wa.beastclient.ui.theme.LocaleHelper
import com.wa.beastclient.ui.theme.contrastingContentColor
import com.wa.beastclient.ui.theme.WuppClockFormat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Returns the bottom-bar tab IDs in swipeable left-to-right Wupp order, omitting [hiddenNavTabs]. */
internal fun resolveHomeTabOrder(
    separateGroupsAndChats: Boolean,
    separateChannelsTab: Boolean = false,
    hiddenNavTabs: Set<Int> = emptySet()
): List<Int> {
    val full = buildList {
        add(0)
        if (separateGroupsAndChats) add(4)
        add(1)
        if (separateChannelsTab) add(6)
        add(2)
        add(5)
        add(3)
    }
    val filtered = full.filterNot { it in hiddenNavTabs }
    return filtered.ifEmpty { listOf(0) }
}

internal fun filterChannelsByQuery(
    channels: List<ChatEntity>,
    searchQuery: String
): List<ChatEntity> {
    val q = searchQuery.trim().lowercase()
    return if (q.isEmpty()) {
        channels
    } else {
        channels.filter {
            it.name.lowercase().contains(q) ||
                it.lastMessagePreview.lowercase().contains(q) ||
                it.aboutText.lowercase().contains(q)
        }
    }
}

internal fun resolvePagerIndexForTab(tabOrder: List<Int>, tabId: Int): Int =
    tabOrder.indexOf(tabId).let { if (it >= 0) it else 0 }

internal fun resolveTabIdForPagerIndex(tabOrder: List<Int>, pageIndex: Int): Int =
    tabOrder.getOrElse(pageIndex) { tabOrder.firstOrNull() ?: 0 }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenChat: (String) -> Unit,
    onOpenContactInfo: (String) -> Unit = {},
    currentTab: Int = 0,
    onSelectTab: (Int) -> Unit = {},
    chatsListState: LazyListState = rememberLazyListState(),
    groupsListState: LazyListState = rememberLazyListState(),
    updatesListState: LazyListState = rememberLazyListState(),
    channelsListState: LazyListState = rememberLazyListState(),
    communitiesListState: LazyListState = rememberLazyListState(),
    callsListState: LazyListState = rememberLazyListState(),
    toolsListState: LazyListState = rememberLazyListState()
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current

    val chats by viewModel.filteredChats.collectAsState()
    val groupsChats by viewModel.filteredGroups.collectAsState()
    val allChats by viewModel.allChats.collectAsState()
    val allContacts by viewModel.allContacts.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val selectedChip by viewModel.selectedFilter.collectAsState()
    val selectedGroupsChip by viewModel.selectedGroupsFilter.collectAsState()
    val searchQuery by viewModel.homeSearchQuery.collectAsState()
    val ftsHits by viewModel.ftsSearchResults.collectAsState()
    val stealth by viewModel.stealthConfig.collectAsState()
    val appLockConfig by viewModel.appLockConfig.collectAsState()
    val includeArchived by viewModel.includeArchived.collectAsState()
    val archivedCount by viewModel.archivedCount.collectAsState()
    val lockedChatsRevealed by viewModel.lockedChatsRevealed.collectAsState()
    val hiddenLockedCount by viewModel.hiddenLockedChatsCount.collectAsState()
    val hiddenLockedJids by viewModel.hiddenLockedJids.collectAsState()
    val hideLockedCurrently = AppLockManager.shouldHideLockedChats(appLockConfig, lockedChatsRevealed)
    val showSeconds = LocalWuppShowSeconds.current
    val bridgeStatus by viewModel.bridgeStatus.collectAsState()
    val heartbeat by viewModel.heartbeatStatus.collectAsState()
    val statuses by viewModel.statuses.collectAsState()
    val callLogs by viewModel.callLogs.collectAsState()
    val downloadingMediaIds by viewModel.downloadingMediaIds.collectAsState()
    val failedMediaIds by viewModel.failedMediaIds.collectAsState()
    val voVault by viewModel.viewOnceVault.collectAsState()
    val deletedHistory by viewModel.deletedMessagesHistory.collectAsState()
    val editedHistory by viewModel.editedMessagesHistory.collectAsState()
    val allEditsHistory by viewModel.allMessageEdits.collectAsState()
    val customStickers by viewModel.customStickers.collectAsState()
    val presenceLogs by viewModel.presenceLogs.collectAsState()
    val profileHistory by viewModel.profileHistory.collectAsState()
    val bulkResult by viewModel.bulkVerifyOutput.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val context = LocalContext.current

    val unreadChatsCount = remember(allChats, stealth.separateGroupsAndChats, hideLockedCurrently) {
        allChats.count {
            !it.isCommunityParent &&
                !it.jid.endsWith("@newsletter") &&
                !it.isPhantomEmptyDm() &&
                !(it.name.startsWith("@") && it.lastMessagePreview.isBlank()) &&
                (!stealth.separateGroupsAndChats || !it.isGroup) &&
                (!hideLockedCurrently || !it.isLocked) &&
                it.unreadCount > 0
        }
    }
    val unreadGroupsCount = remember(allChats, stealth.separateGroupsAndChats, hideLockedCurrently) {
        if (!stealth.separateGroupsAndChats) 0
        else allChats.count {
            !it.isCommunityParent &&
                !it.jid.endsWith("@newsletter") &&
                it.isGroup &&
                (!hideLockedCurrently || !it.isLocked) &&
                it.unreadCount > 0
        }
    }

    val showChannelsTab = stealth.separateChannelsTab && !stealth.hideChannels

    var editProfileRequestNonce by remember { mutableIntStateOf(0) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var showPairingDialog by remember { mutableStateOf(false) }
    var showQuickContactPicker by remember { mutableStateOf(false) }
    var showContactQrSheet by remember { mutableStateOf(false) }
    var contactQrInitialTab by remember { mutableIntStateOf(0) }
    var showHomeCrashDialog by remember { mutableStateOf(AppLogManager.hasPendingCrashNotice(context)) }
    var showHomeLogsViewer by remember { mutableStateOf(false) }
    var quickContactTarget by remember { mutableStateOf<ChatEntity?>(null) }
    val quickContactChat = remember(quickContactTarget, allChats) {
        quickContactTarget?.let { t -> allChats.firstOrNull { it.jid == t.jid } ?: t }
    }
    var activeStatusViewer by remember { mutableStateOf<StatusEntity?>(null) }
    var statusComposerInitialMode by remember { mutableStateOf<StatusComposerMode?>(null) }
    var activeVaultViewer by remember { mutableStateOf<MessageEntity?>(null) }
    var activeFeedbackType by remember { mutableStateOf<GitHubFeedbackType?>(null) }
    var showThemePickerDialog by remember { mutableStateOf(false) }
    var showAccountSwitcherSheet by remember { mutableStateOf(false) }
    var showMediaVaultSheet by remember { mutableStateOf(false) }
    var showScheduledSheet by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var editingScheduledMessage by remember { mutableStateOf<com.wa.beastclient.domain.scheduling.ScheduledMessage?>(null) }
    var scheduleDialogChatJid by remember { mutableStateOf("") }
    var scheduleDialogChatTitle by remember { mutableStateOf("") }
    var showMessageHistorySheet by remember { mutableStateOf(false) }
    var messageHistoryChatFilter by remember { mutableStateOf<String?>(null) }
    var showStickerStudioDialog by remember { mutableStateOf(false) }
    var quickControlsChatTarget by remember { mutableStateOf<ChatEntity?>(null) }
    var quickControlsWallpaperChat by remember { mutableStateOf<ChatEntity?>(null) }
    var showLockedChatsPinPrompt by remember { mutableStateOf(false) }
    var chatSelectionState by remember { mutableStateOf(ChatSelectionState()) }
    val liveSingleSelectedChat = remember(chatSelectionState.singleTarget, allChats) {
        chatSelectionState.singleTarget?.let { target -> allChats.firstOrNull { it.jid == target.jid } ?: target }
    }
    var selectionOverflowExpanded by remember { mutableStateOf(false) }
    var pendingDeleteJids by remember { mutableStateOf<List<String>>(emptyList()) }
    val rowBoundsInRoot = remember { mutableStateMapOf<String, Rect>() }
    var overlayOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var overlaySizePx by remember { mutableStateOf(IntSize.Zero) }
    val haptics = LocalHapticFeedback.current
    val homeScope = rememberCoroutineScope()
    var showHomeCallingSetupDialog by remember { mutableStateOf(false) }
    var pendingHomeCall by remember { mutableStateOf<PendingHomeCallRequest?>(null) }

    val startResolvedHomeCall: (PendingHomeCallRequest) -> Unit = { req ->
        val isGroup = req.peerJid.endsWith("@g.us")
        homeScope.launch {
            val digits = if (isGroup) "" else withContext(Dispatchers.IO) {
                val direct = extractDialablePhoneDigits(req.peerJid)
                if (direct.isNotEmpty()) {
                    direct
                } else {
                    val rawJson = WaRealtimeService.resolveParticipantNamesJson(req.peerJid)
                    val entry = runCatching { JSONObject(rawJson).optJSONObject(req.peerJid) }.getOrNull()
                    val altJid = entry?.optString("altJid").orEmpty()
                    val resolvedPhone = entry?.optString("phone").orEmpty()
                    extractDialablePhoneDigits(req.peerJid, altJid, resolvedPhone)
                }
            }
            if (stealth.offlineMode) {
                Toast.makeText(context, "Disable Offline Mode to place calls", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (stealth.callBackendMode == CallBackendMode.OFF) {
                showHomeCallingSetupDialog = true
                return@launch
            }
            if (!isGroup && digits.isEmpty()) {
                Toast.makeText(context, "Unable to resolve contact phone number for calling", Toast.LENGTH_SHORT).show()
                return@launch
            }
            CallStateManager.startOutgoingCall(
                context = context,
                peerJid = req.peerJid,
                peerName = req.peerName,
                peerAvatarPath = req.avatarPath,
                phoneDigits = digits,
                isVideo = req.isVideo,
                isGroup = isGroup
            )
        }
    }

    val homeCallPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val req = pendingHomeCall ?: return@rememberLauncherForActivityResult
        pendingHomeCall = null
        val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camGranted = !req.isVideo || grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (micGranted && camGranted) {
            startResolvedHomeCall(req)
        } else {
            Toast.makeText(
                context,
                if (req.isVideo) "Microphone & camera permissions are required for video calls"
                else "Microphone permission is required for voice calls",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val launchHomeCall: (String, String, String?, Boolean) -> Unit = { peerJid, peerName, avatarPath, isVideo ->
        if (com.wa.beastclient.core.calling.isMetaAiCallBlocked(peerJid)) {
            Toast.makeText(context, "This chat doesn't support WhatsApp calls", Toast.LENGTH_SHORT).show()
        } else if (stealth.offlineMode) {
            Toast.makeText(context, "Disable Offline Mode to place calls", Toast.LENGTH_SHORT).show()
        } else if (stealth.callBackendMode == CallBackendMode.OFF) {
            showHomeCallingSetupDialog = true
        } else {
            val missing = buildList {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    add(Manifest.permission.RECORD_AUDIO)
                }
                if (isVideo && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    add(Manifest.permission.CAMERA)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                ) {
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
            val request = PendingHomeCallRequest(peerJid, peerName, avatarPath, isVideo)
            if (missing.isEmpty()) {
                startResolvedHomeCall(request)
            } else {
                pendingHomeCall = request
                homeCallPermissionLauncher.launch(missing.toTypedArray())
            }
        }
    }

    val hostActivity = remember(context) { context.findFragmentActivity() }
    val lockedDeviceAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.setLockedChatsRevealed(true)
        }
    }
    val unlockPromptTitle = stringResource(R.string.applock_prompt_title)
    val unlockPromptSubtitle = stringResource(R.string.applock_prompt_subtitle)
    val unlockNoDeviceLock = stringResource(R.string.applock_no_device_lock)
    fun requestRevealLockedChats() {
        if (!appLockConfig.hideLockedChatsBehindLock || lockedChatsRevealed) {
            viewModel.setLockedChatsRevealed(true)
            return
        }
        when (appLockConfig.mode) {
            AppLockMode.DEVICE -> {
                val host = hostActivity
                if (host != null) {
                    launchDeviceAuthPrompt(
                        activity = host,
                        title = unlockPromptTitle,
                        subtitle = unlockPromptSubtitle,
                        cancelText = unlockNoDeviceLock,
                        keyguardFallbackLauncher = { intent -> lockedDeviceAuthLauncher.launch(intent) },
                        onAuthenticated = { viewModel.setLockedChatsRevealed(true) },
                        onErrorMessage = { msg ->
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    viewModel.setLockedChatsRevealed(true)
                }
            }
            AppLockMode.CUSTOM_PIN -> {
                showLockedChatsPinPrompt = true
            }
            AppLockMode.OFF -> {
                viewModel.setLockedChatsRevealed(true)
            }
        }
    }

    val selectedChats = remember(chatSelectionState, allChats, chats, groupsChats) {
        val merged = (allChats + chats + groupsChats).associateBy { it.jid }
        val jids = if (chatSelectionState.multiSelectedJids.isNotEmpty()) {
            chatSelectionState.multiSelectedJids
        } else {
            chatSelectionState.singleTarget?.let { setOf(it.jid) }.orEmpty()
        }
        jids.mapNotNull { merged[it] }
    }

    LaunchedEffect(currentTab) {
        if (chatSelectionState.hasAnySelection) {
            chatSelectionState = ChatSelectionState()
            selectionOverflowExpanded = false
        }
    }

    BackHandler(enabled = chatSelectionState.hasAnySelection) {
        chatSelectionState = ChatSelectionState()
        selectionOverflowExpanded = false
    }

    val backdropState = rememberWuppBackdropState()
    val homeAppearance by HomeAppearanceManager.config.collectAsState()
    val activeTabOrder = remember(stealth.separateGroupsAndChats, showChannelsTab, homeAppearance.hiddenNavTabs) {
        resolveHomeTabOrder(
            separateGroupsAndChats = stealth.separateGroupsAndChats,
            separateChannelsTab = showChannelsTab,
            hiddenNavTabs = homeAppearance.hiddenNavTabs
        )
    }
    LaunchedEffect(activeTabOrder, currentTab) {
        if (currentTab !in activeTabOrder) {
            onSelectTab(activeTabOrder.first())
        }
    }
    val isReducedMotion = remember(homeAppearance.reducedMotion, context) {
        HomeAppearanceManager.isReducedMotionEffective(context, homeAppearance)
    }
    val homeAccent = homeAppearance.accentStyle.resolve(colors.isDark)
    val effectiveAppSurface = when (homeAppearance.surfaceTone) {
        HomeSurfaceTone.OLED_PURE -> if (colors.isDark) Color(0xFF000000) else colors.appSurface
        HomeSurfaceTone.ELEVATED_CARDS -> if (colors.isDark) Color(0xFF101318) else Color(0xFFF6F7FA)
        HomeSurfaceTone.ADAPTIVE -> colors.appSurface
    }
    var showHomeAppearanceSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = effectiveAppSurface,
        topBar = {
            val motionScheme = MaterialTheme.motionScheme
            Column(modifier = Modifier.background(colors.appSurface)) {
                val selectionTabActive = currentTab in listOf(0, 1, 2, 4, 6)
                val topBarState = when {
                    selectionTabActive && chatSelectionState.multiSelectedJids.isNotEmpty() -> 2
                    selectionTabActive && liveSingleSelectedChat != null -> 1
                    else -> 0
                }
                AnimatedContent(
                    targetState = topBarState,
                    transitionSpec = {
                        (fadeIn(motionScheme.fastEffectsSpec()) + slideInVertically(motionScheme.fastSpatialSpec()) { -it / 4 }) togetherWith
                            (fadeOut(motionScheme.fastEffectsSpec()) + slideOutVertically(motionScheme.fastSpatialSpec()) { -it / 4 })
                    },
                    contentKey = { it },
                    label = "home_topbar_mode"
                ) { barState ->
                when (barState) {
                2 -> {
                    val selCount = chatSelectionState.multiSelectedJids.size
                    val allPinned = selectedChats.isNotEmpty() && selectedChats.all { it.isPinned }
                    val allMuted = selectedChats.isNotEmpty() && selectedChats.all { it.isMuted }
                    val allArchived = selectedChats.isNotEmpty() && selectedChats.all { it.isArchived }
                    val allFavorite = selectedChats.isNotEmpty() && selectedChats.all { it.isFavorite }
                    val anyUnread = selectedChats.any { it.unreadCount > 0 }
                    val targetJids = chatSelectionState.multiSelectedJids.toList()

                    WuppGlassTopBar(
                        backdropState = backdropState,
                        classicMode = stealth.classicWhatsAppLook,
                        showBottomDivider = false,
                        height = 56.dp,
                        leading = {
                            IconButton(
                                onClick = {
                                    chatSelectionState = ChatSelectionState()
                                    selectionOverflowExpanded = false
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Clear selection",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(23.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$selCount selected",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary,
                                maxLines = 1
                            )
                        },
                        trailing = {
                            IconButton(
                                onClick = {
                                    viewModel.pinChats(targetJids, !allPinned)
                                    chatSelectionState = ChatSelectionState()
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = if (allPinned) "Unpin" else "Pin",
                                    tint = if (allPinned) colors.brightGreen else colors.textPrimary,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.muteChats(targetJids, !allMuted)
                                    chatSelectionState = ChatSelectionState()
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (allMuted) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff,
                                    contentDescription = if (allMuted) "Unmute notifications" else "Mute notifications",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.archiveChats(targetJids, !allArchived)
                                    chatSelectionState = ChatSelectionState()
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (allArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                                    contentDescription = if (allArchived) "Unarchive" else "Archive",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            IconButton(
                                onClick = { pendingDeleteJids = targetJids },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Delete locally",
                                    tint = colors.antiDeleteRed,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = { selectionOverflowExpanded = true },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = "More actions",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = selectionOverflowExpanded,
                                    onDismissRequest = { selectionOverflowExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (anyUnread) "Mark as read" else "Mark as unread") },
                                        onClick = {
                                            selectionOverflowExpanded = false
                                            viewModel.setChatsUnreadLocal(targetJids, !anyUnread)
                                            chatSelectionState = ChatSelectionState()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (allFavorite) "Remove from Favorites" else "Add to Favorites") },
                                        onClick = {
                                            selectionOverflowExpanded = false
                                            viewModel.favoriteChats(targetJids, !allFavorite)
                                            chatSelectionState = ChatSelectionState()
                                        }
                                    )
                                    if (selectedChats.size == 1) {
                                        val only = selectedChats.first()
                                        DropdownMenuItem(
                                            text = { Text(if (only.jid.endsWith("@newsletter", ignoreCase = true)) "Channel info" else "View contact") },
                                            onClick = {
                                                selectionOverflowExpanded = false
                                                chatSelectionState = ChatSelectionState()
                                                if (only.jid.endsWith("@newsletter", ignoreCase = true)) {
                                                    onOpenContactInfo(only.jid)
                                                } else {
                                                    quickContactTarget = only
                                                }
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Select all") },
                                        onClick = {
                                            selectionOverflowExpanded = false
                                            val allVisible = when (currentTab) {
                                                1, 6 -> channels
                                                4 -> groupsChats
                                                2 -> allChats.filter { it.isGroup && !it.isCommunityParent }
                                                else -> chats
                                            }
                                            chatSelectionState = when {
                                                allVisible.size > 1 -> ChatSelectionState(
                                                    singleTarget = null,
                                                    multiSelectedJids = allVisible.map { it.jid }.toSet()
                                                )
                                                allVisible.size == 1 -> ChatSelectionState(singleTarget = allVisible.first())
                                                else -> ChatSelectionState()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
                1 -> {
                    WuppGlassTopBar(
                        backdropState = backdropState,
                        classicMode = stealth.classicWhatsAppLook,
                        showBottomDivider = false,
                        height = 56.dp,
                        leading = {
                            IconButton(
                                onClick = {
                                    chatSelectionState = ChatSelectionState()
                                    selectionOverflowExpanded = false
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Clear selection",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "1 selected",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Normal,
                                color = colors.textPrimary,
                                maxLines = 1
                            )
                        }
                    )
                }
                else -> {
                val iosCircleBg = if (colors.isDark) Color(0xFF1F2024) else Color(0xFFEBEBF0)
                val showCollapsedSettingsTitle = currentTab == 3 &&
                    (toolsListState.firstVisibleItemIndex > 0 || toolsListState.firstVisibleItemScrollOffset > 120)
                val showTopBarNewChat = homeAppearance.newChatButtonStyle == HomeNewChatStyle.TOP_BAR_ONLY ||
                    homeAppearance.newChatButtonStyle == HomeNewChatStyle.BOTH
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(effectiveAppSurface)
                        .statusBarsPadding()
                        .padding(
                            horizontal = 16.dp,
                            vertical = if (homeAppearance.layoutPreset == HomeLayoutPreset.COMPACT) 4.dp else 6.dp
                        )
                ) {
                    // Row 1: iOS circular utility bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        // Left circular control
                        Box(modifier = Modifier.align(Alignment.CenterStart)) {
                            if (currentTab == 3) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(iosCircleBg)
                                        .clickable { showThemePickerDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Palette,
                                        contentDescription = "Theme",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(iosCircleBg)
                                        .clickable { overflowExpanded = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreHoriz,
                                        contentDescription = "Menu",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = overflowExpanded,
                                    onDismissRequest = { overflowExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("New chat") },
                                        leadingIcon = { Icon(Icons.Rounded.ChatBubbleOutline, null) },
                                        onClick = {
                                            overflowExpanded = false
                                            showQuickContactPicker = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Linked devices") },
                                        leadingIcon = { Icon(Icons.Rounded.QrCode2, null) },
                                        onClick = {
                                            overflowExpanded = false
                                            showPairingDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Switch account") },
                                        leadingIcon = { Icon(Icons.Rounded.ManageAccounts, null) },
                                        onClick = {
                                            overflowExpanded = false
                                            showAccountSwitcherSheet = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Starred messages") },
                                        leadingIcon = { Icon(Icons.Rounded.StarOutline, null) },
                                        onClick = {
                                            overflowExpanded = false
                                            viewModel.selectFilter(HomeFilterChip.FAVORITES)
                                            onSelectTab(0)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (includeArchived) stringResource(R.string.home_back_to_active_chats)
                                                else stringResource(R.string.home_archived_chats)
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.Archive, null) },
                                        onClick = {
                                            overflowExpanded = false
                                            viewModel.toggleArchivedIncluded()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.nav_settings)) },
                                        leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                                        onClick = {
                                            overflowExpanded = false
                                            onSelectTab(3)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Check for updates") },
                                        leadingIcon = { Icon(Icons.Rounded.SystemUpdate, null) },
                                        onClick = {
                                            overflowExpanded = false
                                            viewModel.checkForUpdates(silent = false)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Report bug / Feedback") },
                                        leadingIcon = { Icon(Icons.Rounded.BugReport, null) },
                                        onClick = {
                                            overflowExpanded = false
                                            activeFeedbackType = GitHubFeedbackType.BUG
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Home appearance · ${homeAppearance.layoutPreset.label}") },
                                        leadingIcon = { Icon(Icons.Rounded.Tune, null) },
                                        onClick = {
                                            overflowExpanded = false
                                            showHomeAppearanceSheet = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Settings") },
                                        leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                                        onClick = {
                                            overflowExpanded = false
                                            if (3 in activeTabOrder) {
                                                onSelectTab(3)
                                            } else {
                                                HomeAppearanceManager.update { cfg ->
                                                    cfg.copy(hiddenNavTabs = cfg.hiddenNavTabs - 3)
                                                }
                                                onSelectTab(3)
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Theme · ${stealth.themeMode.label}") },
                                        leadingIcon = { Icon(Icons.Rounded.Palette, null) },
                                        onClick = {
                                            overflowExpanded = false
                                            showThemePickerDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Message history") },
                                        leadingIcon = { Icon(Icons.Rounded.History, null) },
                                        onClick = {
                                            overflowExpanded = false
                                            messageHistoryChatFilter = null
                                            showMessageHistorySheet = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Scheduled messages") },
                                        leadingIcon = { Icon(Icons.Rounded.Schedule, null) },
                                        onClick = {
                                            overflowExpanded = false
                                            showScheduledSheet = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Media vault") },
                                        leadingIcon = { Icon(Icons.Rounded.Shield, null) },
                                        onClick = {
                                            overflowExpanded = false
                                            showMediaVaultSheet = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sticker studio") },
                                        leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, null) },
                                        onClick = {
                                            overflowExpanded = false
                                            showStickerStudioDialog = true
                                        }
                                    )
                                }
                            }
                        }

                        // Centered scrolled title on You / Settings tab
                        if (showCollapsedSettingsTitle) {
                            Text(
                                text = stringResource(R.string.nav_settings),
                                color = colors.textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        // Right circular controls
                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (currentTab) {
                                0, 4 -> {
                                    if (!stealth.hideHomeCameraShortcut) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(iosCircleBg)
                                                .clickable { showPairingDialog = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.vec_ic_camera_reply),
                                                contentDescription = "Camera",
                                                tint = colors.textPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    if (showTopBarNewChat) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(homeAccent)
                                                .clickable { showQuickContactPicker = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Add,
                                                contentDescription = "New Chat",
                                                tint = homeAccent.contrastingContentColor(),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                                5 -> {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(iosCircleBg)
                                            .clickable { showQuickContactPicker = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = "New Call",
                                            tint = colors.textPrimary,
                                            modifier = Modifier.size(21.dp)
                                        )
                                    }
                                }
                                3 -> {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(iosCircleBg)
                                            .clickable { showAccountSwitcherSheet = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = "Switch account",
                                            tint = colors.textPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(iosCircleBg)
                                            .clickable { editProfileRequestNonce++ },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = "Edit profile",
                                            tint = colors.textPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Row 2: Section title — large editorial for iOS-inspired, sleek branded bar for Classic, slim for Compact
                    if (currentTab != 3) {
                        val titleText = when (currentTab) {
                            0 -> if (homeAppearance.layoutPreset == HomeLayoutPreset.CLASSIC) "Wupp" else stringResource(R.string.nav_chats)
                            4 -> stringResource(R.string.nav_groups)
                            1 -> stringResource(R.string.nav_updates)
                            6 -> stringResource(R.string.nav_channels)
                            2 -> stringResource(R.string.nav_communities)
                            else -> stringResource(R.string.nav_calls)
                        }
                        Spacer(modifier = Modifier.height(if (homeAppearance.layoutPreset == HomeLayoutPreset.COMPACT) 2.dp else 6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (homeAppearance.layoutPreset == HomeLayoutPreset.CLASSIC) {
                                WuppBrandMark(accent = homeAccent, size = 26.dp)
                            }
                            Text(
                                text = titleText,
                                color = colors.textPrimary,
                                fontSize = when (homeAppearance.layoutPreset) {
                                    HomeLayoutPreset.IOS_INSPIRED -> 32.sp
                                    HomeLayoutPreset.CLASSIC -> 24.sp
                                    HomeLayoutPreset.COMPACT -> 21.sp
                                },
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            )
                            if (currentTab == 0) {
                                val badge = resolveTopBarConnectionBadge(
                                    connectionState = bridgeStatus.connectionState,
                                    offlineMode = stealth.offlineMode
                                )
                                val statusColor by animateColorAsState(
                                    targetValue = when (badge.tone) {
                                        TopBarStatusTone.LIVE -> colors.brightGreen
                                        TopBarStatusTone.SYNCING -> colors.warningAmber
                                        TopBarStatusTone.OFFLINE -> Color(0xFFE53935)
                                        TopBarStatusTone.IDLE -> colors.textSecondary.copy(alpha = 0.55f)
                                    },
                                    animationSpec = motionScheme.fastEffectsSpec(),
                                    label = "top_bar_status_dot"
                                )
                                val showBadge = !stealth.hideStealthStatusBadges ||
                                    badge.tone == TopBarStatusTone.OFFLINE
                                if (showBadge) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(statusColor.copy(alpha = 0.14f))
                                            .then(
                                                if (bridgeStatus.isLoggedIn) {
                                                    Modifier.clickable { viewModel.toggleOfflineMode() }
                                                } else {
                                                    Modifier
                                                }
                                            )
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        if (badge.tone == TopBarStatusTone.OFFLINE) {
                                            Icon(
                                                imageVector = Icons.Rounded.Flight,
                                                contentDescription = "Offline mode",
                                                tint = statusColor,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.5.dp)
                                                    .clip(CircleShape)
                                                    .background(statusColor)
                                            )
                                        }
                                        Text(
                                            text = badge.label,
                                            color = statusColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            lineHeight = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
                }
                }
            }
        },
        floatingActionButton = {
            val showFab = (currentTab == 0 || currentTab == 4) &&
                !chatSelectionState.hasAnySelection &&
                (homeAppearance.newChatButtonStyle == HomeNewChatStyle.FLOATING_CIRCLE ||
                    homeAppearance.newChatButtonStyle == HomeNewChatStyle.EXTENDED_PILL ||
                    homeAppearance.newChatButtonStyle == HomeNewChatStyle.BOTH)
            if (showFab) {
                if (homeAppearance.newChatButtonStyle == HomeNewChatStyle.EXTENDED_PILL) {
                    ExtendedFloatingActionButton(
                        onClick = { showQuickContactPicker = true },
                        containerColor = homeAccent,
                        contentColor = homeAccent.contrastingContentColor(),
                        shape = RoundedCornerShape(18.dp),
                        icon = { Icon(Icons.Rounded.Edit, contentDescription = "New chat") },
                        text = { Text("New chat", fontWeight = FontWeight.SemiBold) }
                    )
                } else {
                    FloatingActionButton(
                        onClick = { showQuickContactPicker = true },
                        containerColor = homeAccent,
                        contentColor = homeAccent.contrastingContentColor(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "New chat")
                    }
                }
            }
        },
        bottomBar = {
            WuppGlassNavBar(
                currentTab = currentTab,
                unreadChatsCount = unreadChatsCount,
                onSelectTab = onSelectTab,
                backdropState = backdropState,
                classicMode = stealth.classicWhatsAppLook || homeAppearance.navBarStyle == HomeNavStyle.DOCKED_BAR,
                showLabels = homeAppearance.showNavLabels && homeAppearance.navBarStyle != HomeNavStyle.COMPACT_ICONS,
                compactIcons = homeAppearance.navBarStyle == HomeNavStyle.COMPACT_ICONS,
                hiddenNavTabs = homeAppearance.hiddenNavTabs,
                separateGroupsAndChats = stealth.separateGroupsAndChats,
                unreadGroupsCount = unreadGroupsCount,
                separateChannelsTab = showChannelsTab,
                chatsLabel = stringResource(R.string.nav_chats),
                groupsLabel = stringResource(R.string.nav_groups),
                updatesLabel = stringResource(R.string.nav_updates),
                channelsLabel = stringResource(R.string.nav_channels),
                communitiesLabel = stringResource(R.string.nav_communities),
                callsLabel = stringResource(R.string.nav_calls),
                youLabel = "You"
            )
        }
    ) { innerPadding ->
        val contentBottomPad = innerPadding.calculateBottomPadding() + 14.dp
        val mergedChatsByJid = remember(allChats, chats, groupsChats) {
            (allChats + chats + groupsChats).associateBy { it.jid }
        }
        val toggleSelection: (ChatEntity) -> Unit = { chat ->
            chatSelectionState = applyChatTapInSelection(
                state = chatSelectionState,
                target = chat,
                allChatsByJid = mergedChatsByJid
            )
            selectionOverflowExpanded = false
        }
        val handleLongClick: (ChatEntity) -> Unit = { chat ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            chatSelectionState = applyChatLongPress(
                state = chatSelectionState,
                target = chat,
                allChatsByJid = mergedChatsByJid
            )
            selectionOverflowExpanded = false
        }
        val handleAvatarClick: (ChatEntity) -> Unit = { chat ->
            if (chatSelectionState.hasAnySelection) toggleSelection(chat) else quickContactTarget = chat
        }
        val handleChatClick: (ChatEntity) -> Unit = { chat ->
            if (chatSelectionState.hasAnySelection) toggleSelection(chat) else onOpenChat(chat.jid)
        }
        val revealIfLockedFilter: (HomeFilterChip) -> Unit = { chip ->
            if (chip == HomeFilterChip.LOCKED &&
                appLockConfig.hideLockedChatsBehindLock &&
                !lockedChatsRevealed
            ) {
                requestRevealLockedChats()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (!stealth.classicWhatsAppLook) Modifier.wuppBackdropSource(backdropState) else Modifier)
                    .background(effectiveAppSurface)
            ) {
                val tabOrder = activeTabOrder
                val pagerState = rememberPagerState(
                    initialPage = resolvePagerIndexForTab(tabOrder, currentTab),
                    pageCount = { tabOrder.size }
                )
                val latestTabOrder by rememberUpdatedState(tabOrder)
                val latestCurrentTab by rememberUpdatedState(currentTab)
                val latestOnSelectTab by rememberUpdatedState(onSelectTab)

                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.settledPage }
                        .distinctUntilChanged()
                        .drop(1)
                        .collect { settled ->
                            val resolvedTab = resolveTabIdForPagerIndex(latestTabOrder, settled)
                            if (resolvedTab != latestCurrentTab) {
                                latestOnSelectTab(resolvedTab)
                            }
                        }
                }

                LaunchedEffect(currentTab, tabOrder) {
                    val targetPage = resolvePagerIndexForTab(tabOrder, currentTab)
                    if (pagerState.currentPage != targetPage && pagerState.targetPage != targetPage) {
                        pagerState.animateScrollToPage(targetPage)
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    ActiveCallFloatingBar()
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        userScrollEnabled = !chatSelectionState.hasAnySelection,
                        key = { pageIndex -> resolveTabIdForPagerIndex(tabOrder, pageIndex) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) { pageIndex ->
                        when (resolveTabIdForPagerIndex(tabOrder, pageIndex)) {
                            0, 4 -> {
                                val tabIndex = resolveTabIdForPagerIndex(tabOrder, pageIndex)
                                val isGroupsTab = tabIndex == 4 && stealth.separateGroupsAndChats
                                val tabGroupFilterMode: Boolean? = when {
                                    isGroupsTab -> true
                                    stealth.separateGroupsAndChats -> false
                                    else -> null
                                }
                                ChatsTabContent(
                                    chats = if (isGroupsTab) groupsChats else chats,
                                    allChats = allChats,
                                    selectedChip = if (isGroupsTab) selectedGroupsChip else selectedChip,
                                    ftsHits = ftsHits,
                                    searchQuery = searchQuery,
                                    listState = if (isGroupsTab) groupsListState else chatsListState,
                                    selectedChatJids = chatSelectionState.multiSelectedJids,
                                    singleSelectedChatJid = liveSingleSelectedChat?.jid,
                                    onRowBoundsChanged = { jid, rect -> rowBoundsInRoot[jid] = rect },
                                    onDismissSelection = { chatSelectionState = ChatSelectionState() },
                                    bottomPadding = contentBottomPad,
                                    archivedCount = archivedCount,
                                    includeArchived = includeArchived,
                                    hiddenLockedCount = hiddenLockedCount,
                                    hideLockedBehindLock = appLockConfig.hideLockedChatsBehindLock,
                                    hasSecretCode = appLockConfig.hasSecretCode,
                                    lockedChatsRevealed = lockedChatsRevealed,
                                    compactRows = stealth.compactChatRows,
                                    homeAppearance = homeAppearance,
                                    reducedMotion = isReducedMotion,
                                    showSeconds = showSeconds,
                                    hideStealthBadges = stealth.hideStealthStatusBadges,
                                    groupFilterMode = tabGroupFilterMode,
                                    onSearchChange = { viewModel.updateHomeSearch(it) },
                                    heartbeat = heartbeat,
                                    bridgeStatus = bridgeStatus,
                                    onSelectChip = { chip ->
                                        revealIfLockedFilter(chip)
                                        if (isGroupsTab) viewModel.selectGroupsFilter(chip) else viewModel.selectFilter(chip)
                                    },
                                    onToggleIncludeArchived = { viewModel.setIncludeArchived(it) },
                                    onRequestRevealLockedChats = { requestRevealLockedChats() },
                                    onHideRevealedLockedChats = { viewModel.setLockedChatsRevealed(false) },
                                    onResetHeartbeat = { viewModel.recordPrimaryHeartbeatNow() },
                                    onSeedShowcase = { viewModel.seedShowcaseData() },
                                    onAvatarClick = handleAvatarClick,
                                    onChatClick = handleChatClick,
                                    onChatLongClick = handleLongClick
                                )
                            }

                            1 -> UpdatesStatusTab(
                                statuses = statuses,
                                channels = channels,
                                allChats = allChats,
                                allContacts = allContacts,
                                hiddenLockedJids = hiddenLockedJids,
                                preferPhonebookNames = stealth.preferPhonebookContactNames,
                                ownJid = bridgeStatus.ownJid,
                                searchQuery = searchQuery,
                                listState = updatesListState,
                                bottomPadding = contentBottomPad,
                                verticalStatusLayout = stealth.statusVerticalLayout,
                                hideChannels = stealth.hideChannels || showChannelsTab,
                                showSeconds = showSeconds,
                                selectedChatJids = chatSelectionState.multiSelectedJids,
                                singleSelectedChatJid = liveSingleSelectedChat?.jid,
                                onRowBoundsChanged = { jid, rect -> rowBoundsInRoot[jid] = rect },
                                onChannelLongClick = handleLongClick,
                                onDismissSelection = { chatSelectionState = ChatSelectionState() },
                                onSearchChange = { viewModel.updateHomeSearch(it) },
                                onOpenStatus = { activeStatusViewer = it },
                                onAddStatus = { mode -> statusComposerInitialMode = mode },
                                onOpenChannel = handleChatClick
                            )

                            6 -> ChannelsTabContent(
                                channels = channels,
                                searchQuery = searchQuery,
                                listState = channelsListState,
                                bottomPadding = contentBottomPad,
                                selectedChatJids = chatSelectionState.multiSelectedJids,
                                singleSelectedChatJid = liveSingleSelectedChat?.jid,
                                onRowBoundsChanged = { jid, rect -> rowBoundsInRoot[jid] = rect },
                                onChannelLongClick = handleLongClick,
                                onDismissSelection = { chatSelectionState = ChatSelectionState() },
                                onOpenChannel = handleChatClick
                            )

                            2 -> CommunitiesTab(
                                allChats = allChats,
                                listState = communitiesListState,
                                bottomPadding = contentBottomPad,
                                selectedChatJids = chatSelectionState.multiSelectedJids,
                                singleSelectedChatJid = liveSingleSelectedChat?.jid,
                                onRowBoundsChanged = { jid, rect -> rowBoundsInRoot[jid] = rect },
                                onGroupLongClick = handleLongClick,
                                onDismissSelection = { chatSelectionState = ChatSelectionState() },
                                onOpenGroup = handleChatClick
                            )

                            5 -> CallsTab(
                                ownJid = bridgeStatus.ownJid,
                                stealth = stealth,
                                callLogs = callLogs,
                                allChats = allChats,
                                allContacts = allContacts,
                                hiddenLockedJids = hiddenLockedJids,
                                searchQuery = searchQuery,
                                onSearchChange = { viewModel.updateHomeSearch(it) },
                                listState = callsListState,
                                bottomPadding = contentBottomPad,
                                showSeconds = showSeconds,
                                onOpenChat = onOpenChat,
                                onStartCall = launchHomeCall,
                                onNewCallClick = { showQuickContactPicker = true },
                                onOpenCallingSettings = { showHomeCallingSetupDialog = true }
                            )

                            else -> ToolsVaultTab(
                                viewModel = viewModel,
                                listState = toolsListState,
                                bottomPadding = contentBottomPad,
                                savedHistoryCount = (deletedHistory.asSequence().map { it.id } + editedHistory.asSequence().map { it.id }).toSet().size,
                                customStickerCount = customStickers.size,
                                editProfileRequestNonce = editProfileRequestNonce,
                                onOpenStarredChats = {
                                    viewModel.selectFilter(HomeFilterChip.FAVORITES)
                                    onSelectTab(0)
                                },
                                onOpenMessageHistory = {
                                    messageHistoryChatFilter = null
                                    showMessageHistorySheet = true
                                },
                                onOpenMediaVault = { showMediaVaultSheet = true },
                                onOpenStickerStudio = { showStickerStudioDialog = true },
                                onOpenContactQr = {
                                    contactQrInitialTab = 0
                                    showContactQrSheet = true
                                },
                                onOpenFeedback = { activeFeedbackType = it }
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        overlayOriginInRoot = coords.positionInRoot()
                        overlaySizePx = coords.size
                    }
            ) {
                val activeSingle = if (currentTab in listOf(0, 1, 2, 4, 6) && chatSelectionState.multiSelectedJids.isEmpty()) {
                    liveSingleSelectedChat
                } else {
                    null
                }
                FloatingChatSelectionMenuOverlay(
                    targetChat = activeSingle,
                    rowBoundsInRoot = activeSingle?.let { rowBoundsInRoot[it.jid] },
                    overlayOriginInRoot = overlayOriginInRoot,
                    overlaySizePx = overlaySizePx,
                    backdropState = backdropState,
                    classicMode = stealth.classicWhatsAppLook,
                    onDismiss = { chatSelectionState = ChatSelectionState() },
                    onTogglePin = { viewModel.togglePinChat(it) },
                    onToggleMute = { viewModel.muteChats(listOf(it.jid), !it.isMuted) },
                    onToggleUnread = { viewModel.setChatsUnreadLocal(listOf(it.jid), it.unreadCount == 0) },
                    onToggleArchive = { viewModel.archiveChats(listOf(it.jid), !it.isArchived) },
                    onToggleFavorite = { viewModel.toggleFavoriteChat(it) },
                    onQuickControls = { quickControlsChatTarget = it },
                    onOpenInfo = {
                        if (it.jid.endsWith("@newsletter", ignoreCase = true)) {
                            onOpenContactInfo(it.jid)
                        } else {
                            quickContactTarget = it
                        }
                    },
                    onDeleteChat = { pendingDeleteJids = listOf(it.jid) }
                )
            }
        }
    }

    quickContactChat?.let { target ->
        val cachedBio = remember(target.jid, target.aboutText, allContacts) {
            target.aboutText.takeIf { it.isNotBlank() }
                ?: allContacts.firstOrNull { it.jid == target.jid }?.aboutText.orEmpty()
        }
        QuickContactDialog(
            chat = target,
            aboutOverride = if (stealth.alwaysShowContactBio) cachedBio else "",
            onDismiss = { quickContactTarget = null },
            onOpenChat = { onOpenChat(target.jid) },
            onOpenInfo = {
                val jid = target.jid
                quickContactTarget = null
                onOpenContactInfo(jid)
            },
            onAudioCall = {
                val safeTitle = target.name.ifBlank { target.jid.substringBefore("@") }
                quickContactTarget = null
                launchHomeCall(target.jid, safeTitle, target.avatarPath, false)
            },
            onVideoCall = {
                val safeTitle = target.name.ifBlank { target.jid.substringBefore("@") }
                quickContactTarget = null
                launchHomeCall(target.jid, safeTitle, target.avatarPath, true)
            },
            onRequestFullAvatar = { viewModel.requestFullProfilePicture(target.jid) },
            onSaveAvatar = {
                val safeName = target.name.ifBlank { target.jid.substringBefore("@") }
                viewModel.exportMediaToGallery(target.avatarPath, "${safeName}_avatar.jpg", "image/jpeg")
            }
        )
    }

    if (showHomeAppearanceSheet) {
        HomeAppearanceSheet(onDismiss = { showHomeAppearanceSheet = false })
    }

    if (showLockedChatsPinPrompt) {
        var pinAttempt by remember { mutableStateOf("") }
        var pinErr by remember { mutableStateOf<String?>(null) }
        val wrongMsg = stringResource(R.string.applock_wrong_pin)
        val colors = LocalWaColors.current
        AlertDialog(
            onDismissRequest = { showLockedChatsPinPrompt = false },
            containerColor = colors.elevatedBar,
            title = {
                Text(
                    text = stringResource(R.string.applock_prompt_title),
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pinAttempt,
                        onValueChange = { raw ->
                            pinErr = null
                            val clean = raw.filter { it.isDigit() }.take(8)
                            pinAttempt = clean
                            if (clean.length >= 4 && AppLockManager.verifyCustomPin(clean)) {
                                viewModel.setLockedChatsRevealed(true)
                                showLockedChatsPinPrompt = false
                            }
                        },
                        label = { Text(stringResource(R.string.applock_pin_enter_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!pinErr.isNullOrBlank()) {
                        Text(pinErr.orEmpty(), color = colors.antiDeleteRed, fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showLockedChatsPinPrompt = false }) {
                    Text(stringResource(R.string.common_cancel), color = colors.textSecondary)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (AppLockManager.verifyCustomPin(pinAttempt)) {
                            viewModel.setLockedChatsRevealed(true)
                            showLockedChatsPinPrompt = false
                        } else {
                            pinErr = wrongMsg
                            pinAttempt = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen)
                ) {
                    Text(stringResource(R.string.applock_unlock_button), color = Color.White)
                }
            }
        )
    }

    if (showPairingDialog) {
        LinkCompanionDialog(
            bridgeStatus = bridgeStatus,
            onDismiss = { showPairingDialog = false },
            onPairPhone = { digits, cb -> viewModel.pairWithPhoneNumber(digits, cb) },
            onReconnect = { viewModel.reconnectSocket() }
        )
    }

    if (showQuickContactPicker) {
        NewChatPickerDialog(
            ownJid = bridgeStatus.ownJid,
            chats = allChats,
            contacts = allContacts,
            onDismiss = { showQuickContactPicker = false },
            onSelectChat = { jid ->
                showQuickContactPicker = false
                onOpenChat(jid)
            },
            onOpenLinkedDevices = {
                showQuickContactPicker = false
                showPairingDialog = true
            },
            onOpenQrScanner = {
                showQuickContactPicker = false
                contactQrInitialTab = 1
                showContactQrSheet = true
            }
        )
    }

    if (showHomeLogsViewer) {
        DiagnosticsLogsDialog(
            onDismiss = {
                AppLogManager.dismissCrashNotice(context)
                showHomeLogsViewer = false
            }
        )
    }

    if (showHomeCrashDialog) {
        AlertDialog(
            onDismissRequest = {
                AppLogManager.dismissCrashNotice(context)
                showHomeCrashDialog = false
            },
            containerColor = colors.elevatedBar,
            title = {
                Text(
                    text = "Crash log captured",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Wupp saved a crash report from your previous session so you can save or share it with developers.",
                    color = colors.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        AppLogManager.dismissCrashNotice(context)
                        showHomeCrashDialog = false
                        showHomeLogsViewer = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.brightGreen,
                        contentColor = colors.brightGreen.contrastingContentColor()
                    )
                ) {
                    Text("View / Share Logs")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        AppLogManager.dismissCrashNotice(context)
                        showHomeCrashDialog = false
                    }
                ) {
                    Text("Dismiss")
                }
            }
        )
    }

    if (showContactQrSheet) {
        val ownAvatarPath = remember(bridgeStatus.ownJid, allChats) {
            val ownUser = bridgeStatus.ownJid.substringBefore("@").substringBefore(":")
            allChats.firstOrNull { ch ->
                !ch.avatarPath.isNullOrBlank() &&
                    ownUser.isNotBlank() &&
                    ch.jid.substringBefore("@").substringBefore(":") == ownUser
            }?.avatarPath
        }
        val ownDisplayChat = remember(bridgeStatus.ownJid, allChats) {
            val ownUser = bridgeStatus.ownJid.substringBefore("@").substringBefore(":")
            allChats.firstOrNull { ch ->
                ownUser.isNotBlank() && ch.jid.substringBefore("@").substringBefore(":") == ownUser
            }
        }
        ContactQrCodeSheet(
            ownJid = bridgeStatus.ownJid,
            ownName = ownDisplayChat?.name.orEmpty(),
            ownPhone = "",
            ownAvatarPath = ownAvatarPath,
            colors = colors,
            initialTab = contactQrInitialTab,
            onDismiss = { showContactQrSheet = false },
            onStartChat = { jid ->
                showContactQrSheet = false
                onOpenChat(jid)
            }
        )
    }

    if (showThemePickerDialog) {
        ThemePickerDialog(
            selectedTheme = stealth.themeMode,
            onSelectTheme = { viewModel.setThemeMode(it) },
            onDismiss = { showThemePickerDialog = false },
            selectedAccent = stealth.accentColor,
            onSelectAccent = { viewModel.setAccentColor(it) },
            pureBlackOled = stealth.pureBlackOled,
            onTogglePureBlackOled = { viewModel.setPureBlackOled(it) },
            dynamicMaterialYou = stealth.dynamicMaterialYou,
            onToggleDynamicMaterialYou = { viewModel.setDynamicMaterialYou(it) },
            bubbleCornerRadiusDp = stealth.bubbleCornerRadiusDp,
            onBubbleCornerRadiusChange = { viewModel.setBubbleCornerRadiusDp(it) },
            customOutgoingBubbleArgb = stealth.customOutgoingBubbleArgb,
            customIncomingBubbleArgb = stealth.customIncomingBubbleArgb,
            onCustomBubbleColorsChange = { outArgb, inArgb -> viewModel.setCustomBubbleColors(outArgb, inArgb) },
            selectedFontStyle = stealth.fontStyle,
            hasImportedFont = stealth.customFontPath.isNotBlank(),
            onSelectFontStyle = { viewModel.setFontStyle(it) },
            onImportCustomFontUri = { viewModel.importCustomFontFromUri(it) }
        )
    }

    if (showMessageHistorySheet) {
        MessageHistorySheet(
            deletedMessages = deletedHistory,
            editedMessages = editedHistory,
            allEdits = allEditsHistory,
            chats = allChats,
            initialChatJid = messageHistoryChatFilter,
            onJumpToChat = { targetJid -> onOpenChat(targetJid) },
            onDeleteEntry = { viewModel.deleteMessageHistoryItem(it) },
            onClearAll = { scoped -> viewModel.clearAllMessageHistory(scoped) },
            onDismiss = {
                showMessageHistorySheet = false
                messageHistoryChatFilter = null
            }
        )
    }

    if (showMediaVaultSheet) {
        MediaVaultSheet(
            viewOnceMessages = voVault,
            statuses = statuses,
            vaultLockEnabled = stealth.vaultLockEnabled,
            appLockConfig = appLockConfig,
            onToggleVaultLock = { viewModel.setVaultLockEnabled(it) },
            onExportFileToGallery = { file, name, mime ->
                viewModel.exportMediaToGallery(file.absolutePath, name, mime)
            },
            onDismiss = { showMediaVaultSheet = false }
        )
    }

    if (showScheduledSheet) {
        com.wa.beastclient.ui.components.ScheduledMessagesSheet(
            colors = colors,
            onDismiss = { showScheduledSheet = false },
            onOpenComposer = { jid, title ->
                editingScheduledMessage = null
                scheduleDialogChatJid = jid
                scheduleDialogChatTitle = title
                showScheduleDialog = true
            },
            onEditMessage = { msg ->
                editingScheduledMessage = msg
                scheduleDialogChatJid = msg.chatJid
                scheduleDialogChatTitle = msg.chatTitle
                showScheduleDialog = true
            },
            onPauseMessage = { viewModel.pauseScheduledMessage(it) },
            onResumeMessage = { viewModel.resumeScheduledMessage(it) },
            onDeleteMessage = { viewModel.deleteScheduledMessage(it) },
            onRetryMessage = { viewModel.retryScheduledMessage(it) }
        )
    }

    if (showScheduleDialog) {
        com.wa.beastclient.ui.components.ScheduleMessageDialog(
            chatJid = scheduleDialogChatJid,
            chatTitle = scheduleDialogChatTitle,
            initialMessage = editingScheduledMessage,
            colors = colors,
            onDismiss = {
                showScheduleDialog = false
                editingScheduledMessage = null
            },
            onSave = { msg ->
                if (editingScheduledMessage != null) {
                    viewModel.updateScheduledMessage(msg)
                } else {
                    viewModel.scheduleMessage(msg)
                }
                showScheduleDialog = false
                editingScheduledMessage = null
            }
        )
    }

    if (showStickerStudioDialog) {
        StickerStudioDialog(
            canSendNow = false,
            onSaveToPack = { bmp, label -> viewModel.saveCustomSticker(bmp, label) },
            onDismiss = { showStickerStudioDialog = false }
        )
    }

    if (showAccountSwitcherSheet) {
        val accounts by viewModel.accounts.collectAsState()
        val activeAccountId by viewModel.activeAccountId.collectAsState()
        val isSwitchingAccount by viewModel.isSwitchingAccount.collectAsState()
        AccountSwitcherSheet(
            accounts = accounts,
            activeAccountId = activeAccountId,
            isSwitching = isSwitchingAccount,
            onSelectAccount = { viewModel.switchAccount(it) },
            onAddAccount = { viewModel.addAndSwitchAccount(it) },
            onRenameAccount = { id, label -> viewModel.renameAccount(id, label) },
            onRemoveAccount = { viewModel.removeInactiveAccount(it) },
            onDismiss = { showAccountSwitcherSheet = false }
        )
    }

    quickControlsChatTarget?.let { target ->
        val liveTarget = remember(target.jid, allChats) { allChats.firstOrNull { it.jid == target.jid } ?: target }
        val effectivePrivacy by remember(liveTarget.jid) {
            viewModel.observeEffectivePrivacy(liveTarget.jid)
        }.collectAsState(initial = null)
        val chatSavedCount = remember(deletedHistory, editedHistory, liveTarget.jid) {
            (deletedHistory.asSequence().filter { it.chatJid == liveTarget.jid }.map { it.id } +
                editedHistory.asSequence().filter { it.chatJid == liveTarget.jid }.map { it.id }).toSet().size
        }
        PerChatQuickControlsSheet(
            chat = liveTarget,
            chatJid = liveTarget.jid,
            effectivePrivacy = effectivePrivacy,
            stealthConfig = stealth,
            savedHistoryCount = chatSavedCount,
            onSavePrivacyOverride = { r, p, t -> viewModel.saveChatPrivacyOverride(liveTarget.jid, r, p, t) },
            onClearPrivacyOverride = { viewModel.clearChatPrivacyOverride(liveTarget.jid) },
            onSetGalleryVisibility = { viewModel.setChatMediaVisibility(liveTarget.jid, it) },
            onTogglePin = { viewModel.togglePinChat(liveTarget) },
            onToggleMute = { viewModel.muteChats(listOf(liveTarget.jid), !liveTarget.isMuted) },
            onToggleFavorite = { viewModel.toggleFavoriteChat(liveTarget) },
            onToggleLock = { viewModel.setChatLock(liveTarget.jid, !liveTarget.isLocked) },
            onOpenWallpaper = { quickControlsWallpaperChat = liveTarget },
            onOpenChatHistory = {
                messageHistoryChatFilter = liveTarget.jid
                showMessageHistorySheet = true
            },
            onSetHideChatNames = { enabled ->
                viewModel.updateGlobalStealth { c -> c.copy(hideChatContactNames = enabled) }
            },
            onSetHideChatDps = { enabled ->
                viewModel.updateGlobalStealth { c -> c.copy(hideChatProfilePictures = enabled) }
            },
            onSetHideChatNamesAndDps = { enabled ->
                viewModel.updateGlobalStealth { c ->
                    c.copy(hideChatContactNames = enabled, hideChatProfilePictures = enabled)
                }
            },
            onDismiss = { quickControlsChatTarget = null }
        )
    }

    quickControlsWallpaperChat?.let { target ->
        val effectiveWp = remember(target.jid, stealth) {
            StealthMatrixManager.resolveChatWallpaper(target.jid, cfg = stealth)
        }
        val hasWpOverride = remember(target.jid, stealth) {
            StealthMatrixManager.hasChatWallpaperOverride(target.jid, cfg = stealth)
        }
        WallpaperPickerDialog(
            currentUri = stealth.chatWallpaperUri,
            currentDim = stealth.chatWallpaperDim,
            showDoodleOnPresets = stealth.chatWallpaperShowDoodle,
            currentScale = stealth.chatWallpaperScale,
            currentOffsetX = stealth.chatWallpaperOffsetX,
            currentOffsetY = stealth.chatWallpaperOffsetY,
            onSelectUri = { viewModel.setChatWallpaper(it) },
            onPickCustomImage = { viewModel.importChatWallpaperFromUri(it) },
            onDimChange = { viewModel.setChatWallpaperDim(it) },
            onToggleDoodle = { viewModel.setChatWallpaperShowDoodle(it) },
            onPositionChange = { s, ox, oy -> viewModel.setChatWallpaperPosition(s, ox, oy) },
            onDismiss = { quickControlsWallpaperChat = null },
            chatTitle = target.name.ifBlank { target.jid.substringBefore("@") },
            hasChatOverride = hasWpOverride,
            chatUri = effectiveWp.uri,
            chatDim = effectiveWp.dim,
            chatShowDoodle = effectiveWp.showDoodle,
            chatScale = effectiveWp.scale,
            chatOffsetX = effectiveWp.offsetXFraction,
            chatOffsetY = effectiveWp.offsetYFraction,
            onSelectChatUri = { viewModel.setPerChatWallpaper(target.jid, it) },
            onPickChatCustomImage = { viewModel.importPerChatWallpaperFromUri(target.jid, it) },
            onChatDimChange = { viewModel.setPerChatWallpaperDim(target.jid, it) },
            onToggleChatDoodle = { viewModel.setPerChatWallpaperShowDoodle(target.jid, it) },
            onChatPositionChange = { s, ox, oy -> viewModel.setPerChatWallpaperPosition(target.jid, s, ox, oy) },
            onResetChatToGlobal = { viewModel.clearPerChatWallpaper(target.jid) }
        )
    }

    statusComposerInitialMode?.let { mode ->
        StatusComposerSheet(
            initialMode = mode,
            onDismiss = { statusComposerInitialMode = null }
        )
    }

    activeStatusViewer?.let { st ->
        val myStatusLabel = stringResource(R.string.status_my_status)
        val lockedSenderOrder = remember(st.id) {
            val (_, initialContacts) = partitionOwnAndContactStatuses(statuses, bridgeStatus.ownJid, filterExpired = true)
            buildContactStatusSummaries(initialContacts).map { it.senderKey }
        }
        val statusGroups = remember(statuses, allChats, bridgeStatus.ownJid, myStatusLabel, st.id, lockedSenderOrder) {
            val (chatsByJid, chatsByUser) = buildStatusChatLookup(allChats)
            val (ownStatuses, contactStatuses) = partitionOwnAndContactStatuses(statuses, bridgeStatus.ownJid, filterExpired = true)
            val viewedOwn = isOwnStatusSender(st.senderJid, bridgeStatus.ownJid)

            fun toStatusGroup(senderJid: String, items: List<StatusEntity>, isOwn: Boolean): StatusGroup {
                val sortedItems = items.sortedBy { it.timestamp }
                val senderChat = resolveStatusSenderChat(senderJid, chatsByJid, chatsByUser)
                val firstItem = sortedItems.firstOrNull()
                return StatusGroup(
                    senderJid = senderJid,
                    senderName = if (isOwn) {
                        myStatusLabel
                    } else {
                        resolveStatusDisplayName(
                            senderJid = firstItem?.senderJid ?: senderJid,
                            storedName = firstItem?.senderName.orEmpty(),
                            senderChat = senderChat
                        )
                    },
                    senderAvatarPath = senderChat?.avatarPath,
                    items = sortedItems,
                    isOwn = isOwn
                )
            }

            if (viewedOwn) {
                val ownItems = ownStatuses.ifEmpty { listOf(st) }
                listOf(toStatusGroup(normalizeStatusSenderJid(st.senderJid), ownItems, isOwn = true))
            } else {
                val bySender = contactStatuses.groupBy { normalizeStatusSenderJid(it.senderJid) }
                val rankBySender = lockedSenderOrder.withIndex().associate { it.value to it.index }
                val groupsList = bySender.entries
                    .sortedBy { (senderJid, _) -> rankBySender[senderJid] ?: Int.MAX_VALUE }
                    .map { (senderJid, items) ->
                        toStatusGroup(senderJid, items, isOwn = false)
                    }
                groupsList.ifEmpty {
                    val normSender = normalizeStatusSenderJid(st.senderJid)
                    listOf(toStatusGroup(normSender, listOf(st), isOwn = false))
                }
            }
        }
        val initialGroupIdx = remember(statusGroups, st.senderJid) {
            val targetSender = normalizeStatusSenderJid(st.senderJid)
            statusGroups.indexOfFirst { it.senderJid == targetSender || it.senderJid == st.senderJid }.coerceAtLeast(0)
        }
        val initialStoryIdx = remember(statusGroups, initialGroupIdx, st.id) {
            statusGroups.getOrNull(initialGroupIdx)?.items?.indexOfFirst { it.id == st.id }?.coerceAtLeast(0) ?: 0
        }
        FullScreenStatusViewer(
            groups = statusGroups,
            initialGroupIndex = initialGroupIdx,
            initialStoryIndex = initialStoryIdx,
            downloadingStatusIds = downloadingMediaIds,
            failedStatusIds = failedMediaIds,
            onRequestDownload = { target ->
                viewModel.requestStatusMediaDownload(target, userInitiated = true)
            },
            onStatusViewed = { viewed ->
                viewModel.markStatusViewed(viewed)
            },
            onDismiss = { activeStatusViewer = null },
            onSaveToGallery = { target ->
                val ext = if (target.mimeType.contains("mp4", ignoreCase = true)) "mp4" else "png"
                viewModel.exportMediaToGallery(
                    target.localMediaPath,
                    "WA_Status_${target.id}.$ext",
                    target.mimeType.ifBlank { "image/png" }
                )
            },
            onReply = { target ->
                if (target.senderJid.isNotBlank() && !isOwnStatusSender(target.senderJid, bridgeStatus.ownJid)) {
                    onOpenChat(target.senderJid)
                }
            },
            onSendReply = { target, text ->
                if (target.senderJid.isNotBlank() && !isOwnStatusSender(target.senderJid, bridgeStatus.ownJid)) {
                    viewModel.sendStatusReply(target, text)
                }
            },
            onSendReaction = { target, emoji ->
                if (target.senderJid.isNotBlank() && !isOwnStatusSender(target.senderJid, bridgeStatus.ownJid)) {
                    viewModel.sendStatusReaction(target, emoji)
                }
            },
            onAddStatus = { statusComposerInitialMode = StatusComposerMode.MEDIA }
        )
    }

    activeVaultViewer?.let { msg ->
        val chatName = remember(allChats, msg.chatJid, msg.senderName) {
            allChats.firstOrNull { it.jid == msg.chatJid }?.name ?: msg.senderName
        }
        FullScreenMediaViewer(
            msg = msg,
            senderDisplayName = chatName,
            onDismiss = { activeVaultViewer = null },
            onSaveToGallery = { item ->
                val ext = if (item.mimeType.contains("mp4", ignoreCase = true)) "mp4" else "png"
                viewModel.exportMediaToGallery(
                    item.localMediaPath,
                    "WA_ViewOnce_${item.id}.$ext",
                    item.mimeType.ifBlank { "image/png" }
                )
            },
            onReply = { item ->
                if (item.chatJid.isNotBlank()) {
                    onOpenChat(item.chatJid)
                }
            }
        )
    }

    activeFeedbackType?.let { fbType ->
        GitHubFeedbackDialog(
            initialType = fbType,
            onDismiss = { activeFeedbackType = null }
        )
    }

    if (pendingDeleteJids.isNotEmpty()) {
        val count = pendingDeleteJids.size
        val hasSyncableChats = pendingDeleteJids.any { !it.endsWith("@newsletter") && !it.endsWith("@broadcast") }
        var alsoDeleteOnWhatsApp by remember(pendingDeleteJids) { mutableStateOf(hasSyncableChats) }
        AlertDialog(
            onDismissRequest = { pendingDeleteJids = emptyList() },
            title = { Text(if (count == 1) "Delete this chat?" else "Delete $count chats?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (alsoDeleteOnWhatsApp && hasSyncableChats) {
                            "Deletes ${if (count == 1) "this chat" else "these chats"} from Wupp and syncs deletion to WhatsApp on your primary phone."
                        } else {
                            "Messages for ${if (count == 1) "this conversation" else "these conversations"} will be removed from Wupp's local cache only."
                        }
                    )
                    if (hasSyncableChats) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { alsoDeleteOnWhatsApp = !alsoDeleteOnWhatsApp },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = alsoDeleteOnWhatsApp,
                                onCheckedChange = { alsoDeleteOnWhatsApp = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Also delete on WhatsApp",
                                fontSize = 13.5.sp,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteChats(pendingDeleteJids, syncToWhatsApp = alsoDeleteOnWhatsApp && hasSyncableChats)
                        pendingDeleteJids = emptyList()
                        chatSelectionState = ChatSelectionState()
                    }
                ) {
                    Text("Delete", color = colors.antiDeleteRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteJids = emptyList() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showHomeCallingSetupDialog) {
        CallingSetupDialog(
            ownJid = bridgeStatus.ownJid,
            stealth = stealth,
            onDismiss = { showHomeCallingSetupDialog = false }
        )
    }
}

private data class PendingHomeCallRequest(
    val peerJid: String,
    val peerName: String,
    val avatarPath: String?,
    val isVideo: Boolean
)

@Composable
private fun IosInlineSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search",
    modifier: Modifier = Modifier
) {
    val colors = LocalWaColors.current
    val fill = if (colors.isDark) Color(0xFF1C1C1E) else Color(0xFFEBEBF0)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.vec_ic_search),
            contentDescription = "Search",
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.5.sp),
            cursorBrush = SolidColor(colors.brightGreen),
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = colors.textSecondary,
                        fontSize = 15.5.sp
                    )
                }
                innerTextField()
            },
            modifier = Modifier.weight(1f)
        )
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(colors.textSecondary.copy(alpha = 0.28f))
                    .clickable { onQueryChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Clear search",
                    tint = colors.appSurface,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun IosQuickActionCircle(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalWaColors.current
    val circleBg = when {
        active -> colors.brightGreen.copy(alpha = 0.22f)
        colors.isDark -> Color(0xFF1F272B)
        else -> Color(0xFFEBEBF0)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(circleBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) colors.brightGreen else colors.textPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = if (active) colors.brightGreen else colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun CallsTab(
    ownJid: String,
    stealth: GlobalStealthConfig,
    callLogs: List<MessageEntity>,
    allChats: List<ChatEntity>,
    allContacts: List<ContactEntity>,
    hiddenLockedJids: Set<String> = emptySet(),
    searchQuery: String,
    onSearchChange: (String) -> Unit = {},
    listState: LazyListState,
    bottomPadding: Dp,
    showSeconds: Boolean,
    onOpenChat: (String) -> Unit,
    onStartCall: (peerJid: String, peerName: String, avatarPath: String?, isVideo: Boolean) -> Unit,
    onNewCallClick: () -> Unit = {},
    onOpenCallingSettings: () -> Unit
) {
    val colors = LocalWaColors.current
    val use24Hour = LocalWuppUse24Hour.current
    val isCallingReady = stealth.callBackendMode != CallBackendMode.OFF
    var favoritesOnly by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = isCallingReady,
        transitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = 220)) togetherWith
                fadeOut(animationSpec = tween(durationMillis = 160))
        },
        label = "calls_tab_setup_or_list"
    ) { ready ->
        if (!ready) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 16.dp,
                        top = 14.dp,
                        end = 16.dp,
                        bottom = bottomPadding + 18.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CallingSetupCard(
                    ownJid = ownJid,
                    stealth = stealth,
                    showCloseButton = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            return@AnimatedContent
        }

        val chatLookup = remember(allChats) {
            val map = HashMap<String, ChatEntity>(allChats.size * 2)
            for (ch in allChats) {
                map[ch.jid] = ch
                val bare = ch.jid.substringBefore("@").substringBefore(":")
                if (bare.isNotBlank() && bare !in map) {
                    map[bare] = ch
                }
            }
            map
        }
        val contactLookup = remember(allContacts) {
            val map = HashMap<String, ContactEntity>(allContacts.size * 2)
            for (c in allContacts) {
                map[c.jid] = c
                val bare = c.jid.substringBefore("@").substringBefore(":")
                if (bare.isNotBlank() && bare !in map) {
                    map[bare] = c
                }
            }
            map
        }

        val normalizedQuery = searchQuery.trim().lowercase()
        val queryDigits = remember(searchQuery) { searchQuery.filter { it.isDigit() } }

        val visibleLogs = remember(callLogs, hiddenLockedJids) {
            if (hiddenLockedJids.isEmpty()) callLogs
            else callLogs.filter { log ->
                val bare = log.chatJid.substringBefore("@").substringBefore(":")
                log.chatJid !in hiddenLockedJids && bare !in hiddenLockedJids
            }
        }

        val filteredLogs = remember(visibleLogs, chatLookup, contactLookup, normalizedQuery, queryDigits) {
            if (normalizedQuery.isEmpty()) visibleLogs
            else visibleLogs.filter { log ->
                val bare = log.chatJid.substringBefore("@").substringBefore(":")
                val peerName = chatLookup[log.chatJid]?.name?.ifBlank { null }
                    ?: chatLookup[bare]?.name?.ifBlank { null }
                    ?: contactLookup[log.chatJid]?.name?.ifBlank { null }
                    ?: contactLookup[bare]?.name?.ifBlank { null }
                    ?: log.senderName.ifBlank { bare }
                peerName.lowercase().contains(normalizedQuery) ||
                    log.chatJid.lowercase().contains(normalizedQuery) ||
                    (queryDigits.length >= 3 && bare.contains(queryDigits))
            }
        }

        val callableContacts = remember(allChats, allContacts, hiddenLockedJids, normalizedQuery, queryDigits) {
            val byJid = LinkedHashMap<String, ChatEntity>(allContacts.size + allChats.size)
            for (c in allContacts) {
                if (c.jid.endsWith("@g.us") || c.jid.endsWith("@newsletter") ||
                    c.jid == "status@broadcast" || c.jid.endsWith("@broadcast") ||
                    c.jid in hiddenLockedJids || c.jid.substringBefore("@").substringBefore(":") in hiddenLockedJids
                ) continue
                byJid[c.jid] = ChatEntity(
                    jid = c.jid,
                    name = c.name,
                    avatarPath = c.avatarPath,
                    aboutText = c.aboutText,
                    isFavorite = c.isSaved
                )
            }
            for (ch in allChats) {
                if (ch.isGroup || ch.isCommunityParent || ch.jid.endsWith("@newsletter") ||
                    ch.jid == "status@broadcast" || ch.jid.endsWith("@broadcast") ||
                    ch.isPhantomEmptyDm() ||
                    ch.jid in hiddenLockedJids || ch.jid.substringBefore("@").substringBefore(":") in hiddenLockedJids
                ) continue
                val existing = byJid[ch.jid]
                if (existing == null) {
                    byJid[ch.jid] = ch
                } else {
                    val bare = ch.jid.substringBefore("@")
                    val existingReal = existing.name.isNotBlank() && existing.name != bare && existing.name != ch.jid && !existing.name.startsWith("+")
                    val chReal = ch.name.isNotBlank() && ch.name != bare && ch.name != ch.jid && !ch.name.startsWith("+")
                    byJid[ch.jid] = existing.copy(
                        name = when {
                            existingReal && !chReal -> existing.name
                            chReal -> ch.name
                            else -> ch.name.ifBlank { existing.name }
                        },
                        avatarPath = ch.avatarPath?.takeIf { it.isNotBlank() } ?: existing.avatarPath,
                        aboutText = ch.aboutText.ifBlank { existing.aboutText },
                        lastMessageTimestamp = maxOf(existing.lastMessageTimestamp, ch.lastMessageTimestamp)
                    )
                }
            }
            val sorted = byJid.values.sortedWith(
                compareByDescending<ChatEntity> { it.isFavorite }
                    .thenByDescending { it.lastMessageTimestamp > 0L }
                    .thenBy { it.name.lowercase() }
            )
            val narrowed = if (normalizedQuery.isEmpty()) sorted else sorted.filter { peer ->
                peer.name.lowercase().contains(normalizedQuery) ||
                    peer.jid.lowercase().contains(normalizedQuery) ||
                    (queryDigits.length >= 3 && peer.jid.substringBefore("@").contains(queryDigits))
            }
            narrowed.take(80)
        }

        val displayedLogs = remember(filteredLogs, favoritesOnly, chatLookup) {
            if (!favoritesOnly) filteredLogs
            else filteredLogs.filter { log ->
                val bare = log.chatJid.substringBefore("@").substringBefore(":")
                chatLookup[log.chatJid]?.isFavorite == true || chatLookup[bare]?.isFavorite == true
            }
        }
        val callsScope = rememberCoroutineScope()

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 2.dp, bottom = bottomPadding + 18.dp)
        ) {
            item(key = "calls_ios_search") {
                IosInlineSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchChange,
                    placeholder = "Search"
                )
            }

            item(key = "calls_ios_quick_actions") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IosQuickActionCircle(
                        icon = Icons.Rounded.Call,
                        label = "Call",
                        onClick = onNewCallClick
                    )
                    IosQuickActionCircle(
                        icon = Icons.Rounded.Tune,
                        label = "Setup",
                        onClick = onOpenCallingSettings
                    )
                    IosQuickActionCircle(
                        icon = Icons.Rounded.Dialpad,
                        label = "Contacts",
                        onClick = {
                            callsScope.launch {
                                val targetIdx = (displayedLogs.size + 4).coerceAtLeast(3)
                                runCatching { listState.animateScrollToItem(targetIdx) }
                            }
                        }
                    )
                    IosQuickActionCircle(
                        icon = if (favoritesOnly) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        label = "Favorites",
                        active = favoritesOnly,
                        onClick = { favoritesOnly = !favoritesOnly }
                    )
                }
            }

            if (stealth.offlineMode) {
                item(key = "calls_mode_banner") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.warningAmber.copy(alpha = 0.14f))
                            .border(0.8.dp, colors.warningAmber.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            .clickable { onOpenCallingSettings() }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Flight,
                            contentDescription = null,
                            tint = colors.warningAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Offline Mode active — calls paused",
                            color = colors.textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Mode",
                            color = colors.brightGreen,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item(key = "calls_recent_header") {
                Text(
                    text = if (favoritesOnly) "Recent Favorites" else "Recent",
                    color = colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 6.dp)
                )
            }

            if (displayedLogs.isEmpty()) {
                item(key = "calls_recent_empty") {
                    Text(
                        text = if (normalizedQuery.isNotEmpty() || favoritesOnly) {
                            "No matching call history"
                        } else {
                            "No recent calls yet — tap any contact below to start a call."
                        },
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            } else {
                items(items = displayedLogs, key = { "call_log_${it.id}" }) { log ->
                    val bare = log.chatJid.substringBefore("@").substringBefore(":")
                    val chatMatch = chatLookup[log.chatJid] ?: chatLookup[bare]
                    val contactMatch = contactLookup[log.chatJid] ?: contactLookup[bare]
                    val displayName = chatMatch?.name?.ifBlank { null }
                        ?: contactMatch?.name?.ifBlank { null }
                        ?: log.senderName.ifBlank { bare }
                    val avatarPath = chatMatch?.avatarPath?.takeIf { it.isNotBlank() } ?: contactMatch?.avatarPath
                    val isGroup = log.chatJid.endsWith("@g.us")

                    val parts = log.body.split("|", limit = 2)
                    val callTitle = parts.getOrNull(0)?.ifBlank { "Voice call" } ?: "Voice call"
                    val callSubtitle = parts.getOrNull(1)?.ifBlank {
                        when {
                            log.durationSec in 1..59 -> "${log.durationSec} sec"
                            log.durationSec >= 60 -> "${(log.durationSec + 30) / 60} min"
                            else -> "No answer"
                        }
                    } ?: "No answer"
                    val isVideoCall = callTitle.contains("Video", ignoreCase = true)
                    val isMissed = !log.isFromMe && (
                        callTitle.contains("Missed", ignoreCase = true) ||
                            callTitle.contains("Silenced", ignoreCase = true) ||
                            callSubtitle.contains("No answer", ignoreCase = true) ||
                            callSubtitle.contains("Missed", ignoreCase = true) ||
                            callSubtitle.contains("Declined", ignoreCase = true) ||
                            callSubtitle.contains("Do Not Disturb", ignoreCase = true) ||
                            callSubtitle.contains("Unknown caller", ignoreCase = true)
                        )
                    val directionIcon = when {
                        isMissed -> Icons.AutoMirrored.Rounded.CallMissed
                        log.isFromMe -> Icons.AutoMirrored.Rounded.CallMade
                        else -> Icons.AutoMirrored.Rounded.CallReceived
                    }
                    val statusTint = if (isMissed) colors.antiDeleteRed else colors.brightGreen
                    val formattedTime = formatMessageClock(
                        log.timestamp,
                        includeSeconds = showSeconds,
                        use24Hour = use24Hour
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenChat(log.chatJid) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WhatsAppAvatar(
                            jid = log.chatJid,
                            avatarPath = avatarPath,
                            isGroup = isGroup,
                            size = 46.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                color = if (isMissed) colors.antiDeleteRed else colors.textPrimary,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = directionIcon,
                                    contentDescription = null,
                                    tint = statusTint,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "$callSubtitle · $formattedTime",
                                    color = colors.textSecondary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = { onStartCall(log.chatJid, displayName, avatarPath, false) }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Call,
                                contentDescription = "Voice call",
                                tint = if (isVideoCall) colors.textSecondary else colors.brightGreen
                            )
                        }
                        IconButton(
                            onClick = { onStartCall(log.chatJid, displayName, avatarPath, true) }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Videocam,
                                contentDescription = "Video call",
                                tint = if (isVideoCall) colors.brightGreen else colors.textSecondary
                            )
                        }
                    }
                }
            }

            if (callableContacts.isNotEmpty()) {
                item(key = "calls_contacts_header") {
                    Text(
                        text = "Call a Contact",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 8.dp
                        )
                    )
                }

                items(items = callableContacts, key = { "call_contact_${it.jid}" }) { peer ->
                    val displayName = peer.name.ifBlank { peer.jid.substringBefore("@") }
                    val subtitle = peer.aboutText.ifBlank {
                        peer.jid.substringBefore("@").substringBefore(":")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStartCall(peer.jid, displayName, peer.avatarPath, false) }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WhatsAppAvatar(
                            jid = peer.jid,
                            avatarPath = peer.avatarPath,
                            isGroup = false,
                            size = 44.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                color = colors.textPrimary,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (subtitle.isNotBlank()) {
                                Text(
                                    text = subtitle,
                                    color = colors.textSecondary,
                                    fontSize = 12.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = { onStartCall(peer.jid, displayName, peer.avatarPath, false) }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Call,
                                contentDescription = "Voice call",
                                tint = colors.brightGreen
                            )
                        }
                        IconButton(
                            onClick = { onStartCall(peer.jid, displayName, peer.avatarPath, true) }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Videocam,
                                contentDescription = "Video call",
                                tint = colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = LocalWaColors.current.pillActiveText,
    selectedTextColor = LocalWaColors.current.textPrimary,
    indicatorColor = LocalWaColors.current.pillActiveBg,
    unselectedIconColor = LocalWaColors.current.textSecondary,
    unselectedTextColor = LocalWaColors.current.textSecondary
)

@Composable
private fun rememberAvatarBitmap(jid: String, avatarPath: String?): ImageBitmap? {
    val context = LocalContext.current
    return remember(jid, avatarPath) {
        val explicitFile = avatarPath?.takeIf { it.isNotBlank() }?.let { path ->
            File(path).takeIf { it.exists() && it.length() > 0 }
        }
        val resolvedFile = explicitFile ?: run {
            val rawUser = jid.substringBefore("@").substringBefore(":")
            val dirs = listOf(
                File(context.filesDir, "media/avatars"),
                File(context.filesDir, "vault/avatars")
            )
            var found: File? = null
            if (rawUser.isNotBlank()) {
                for (dir in dirs) {
                    if (!dir.isDirectory) continue
                    found = dir.listFiles()?.firstOrNull {
                        it.isFile && it.length() > 0L && (
                            it.name.startsWith("${rawUser}_") ||
                                it.name.contains("_${rawUser}_") ||
                                it.name.contains("_${rawUser}.")
                            )
                    }
                    if (found != null) break
                }
            }
            found
        }
        resolvedFile?.let { file ->
            runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
        }
    }
}

@Composable
internal fun WhatsAppAvatar(
    jid: String,
    avatarPath: String?,
    isGroup: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    initialChar: String? = null,
    hideAvatar: Boolean = false
) {
    val avatarBmp = if (hideAvatar) null else rememberAvatarBitmap(jid = jid, avatarPath = avatarPath)
    LaunchedEffect(jid, avatarBmp == null, hideAvatar) {
        if (!hideAvatar && avatarBmp == null && jid.isNotBlank() && jid != "status@broadcast" && !jid.endsWith("@broadcast")) {
            WaRealtimeService.requestAvatarIfNeeded(jid)
        }
    }
    val palette = remember(jid, isGroup) {
        val pairs = listOf(
            Color(0xFF4A2E2B) to Color(0xFFF2998E), // Warm terracotta brown / peach (e.g. Mom)
            Color(0xFF1E3A5F) to Color(0xFF78A9FF), // Deep slate blue / sky blue (e.g. Talha)
            Color(0xFF3E2742) to Color(0xFFD7AEFB), // Plum / lavender
            Color(0xFF1B3B36) to Color(0xFF81C995), // Deep teal / sage
            Color(0xFF423824) to Color(0xFFFDD663)  // Charcoal olive / warm amber
        )
        pairs[abs(jid.substringBefore("@").hashCode()) % pairs.size]
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isGroup) Color(0xFF1F353D) else palette.first),
        contentAlignment = Alignment.Center
    ) {
        if (avatarBmp != null) {
            Image(
                bitmap = avatarBmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isGroup) {
            Icon(
                painter = painterResource(R.drawable.vec_ic_avatar_community),
                contentDescription = null,
                tint = Color(0xFF99B2BD),
                modifier = Modifier.size(size * 0.54f)
            )
        } else if (!hideAvatar && !initialChar.isNullOrBlank()) {
            Text(
                text = initialChar,
                color = palette.second,
                fontSize = (size.value * 0.44f).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = palette.second,
                modifier = Modifier.size(size * 0.58f)
            )
        }
    }
}

@Composable
private fun ChatsTabContent(
    chats: List<ChatEntity>,
    allChats: List<ChatEntity>,
    selectedChip: HomeFilterChip,
    ftsHits: List<MessageEntity>,
    searchQuery: String,
    listState: LazyListState,
    selectedChatJids: Set<String> = emptySet(),
    singleSelectedChatJid: String? = null,
    onRowBoundsChanged: (String, Rect) -> Unit = { _, _ -> },
    onDismissSelection: () -> Unit = {},
    bottomPadding: Dp = 96.dp,
    archivedCount: Int = 0,
    includeArchived: Boolean = false,
    hiddenLockedCount: Int = 0,
    hideLockedBehindLock: Boolean = false,
    hasSecretCode: Boolean = false,
    lockedChatsRevealed: Boolean = false,
    compactRows: Boolean = false,
    homeAppearance: HomeAppearanceConfig = HomeAppearanceConfig(),
    reducedMotion: Boolean = false,
    showSeconds: Boolean = false,
    hideStealthBadges: Boolean = false,
    groupFilterMode: Boolean? = null,
    onSearchChange: (String) -> Unit,
    heartbeat: com.wa.beastclient.domain.stealth.PrimaryHeartbeatStatus,
    bridgeStatus: com.wa.beastclient.core.service.BridgeStatus,
    onSelectChip: (HomeFilterChip) -> Unit,
    onToggleIncludeArchived: (Boolean) -> Unit = {},
    onRequestRevealLockedChats: () -> Unit = {},
    onHideRevealedLockedChats: () -> Unit = {},
    onResetHeartbeat: () -> Unit,
    onSeedShowcase: () -> Unit,
    onAvatarClick: (ChatEntity) -> Unit,
    onChatClick: (ChatEntity) -> Unit,
    onChatLongClick: (ChatEntity) -> Unit
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    val scope = rememberCoroutineScope()
    var showExtraFilters by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    val nonCommunityChats = remember(allChats, groupFilterMode) {
        allChats.filter {
            !it.isCommunityParent &&
                !it.jid.endsWith("@newsletter") &&
                !it.isPhantomEmptyDm() &&
                !(it.name.startsWith("@") && it.lastMessagePreview.isBlank()) &&
                (groupFilterMode == null || it.isGroup == groupFilterMode)
        }
    }
    val hideLockedCurrently = (hideLockedBehindLock || hasSecretCode) && !lockedChatsRevealed
    val visibleChatsForCounts = remember(nonCommunityChats, hideLockedCurrently) {
        if (!hideLockedCurrently) nonCommunityChats
        else nonCommunityChats.filter { !it.isLocked }
    }
    val tabHiddenLockedCount = remember(nonCommunityChats, hideLockedCurrently, hiddenLockedCount) {
        if (!hideLockedCurrently) 0
        else nonCommunityChats.count { it.isLocked }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (!isRefreshing) {
                isRefreshing = true
                scope.launch {
                    delay(700L)
                    isRefreshing = false
                }
            }
        },
        state = pullRefreshState,
        indicator = {
            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
            PullToRefreshDefaults.LoadingIndicator(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                containerColor = colors.pillActiveBg,
                color = colors.brightGreen,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        },
        modifier = Modifier.fillMaxSize()
    ) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (singleSelectedChatJid != null && selectedChatJids.isEmpty()) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissSelection
                    )
                } else Modifier
            ),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
        // 14-Day Primary Heartbeat countdown warning banner (>= 10 days elapsed)
        if (heartbeat.isWarningActive) {
            item(key = "heartbeat_warning") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.warningAmber.copy(alpha = 0.18f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Warning, null, tint = colors.warningAmber)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "14-Day Primary Heartbeat Alert",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                            Text(
                                text = "${heartbeat.elapsedDays}d elapsed · ${heartbeat.remainingDays}d (${heartbeat.remainingHours}h) before Meta unlinks companions. Open official WhatsApp on primary device.",
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        TextButton(onClick = onResetHeartbeat) {
                            Text("Reset", color = colors.accentGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Customizable Section Order: Search bar, Filter chips, Archived row
        homeAppearance.sectionOrder.forEach { sectionKind ->
            when (sectionKind) {
                HomeSectionKind.SEARCH_BAR -> {
                    if (homeAppearance.showSearchBar) {
                        item(key = "inline_search_bar") {
                            IosInlineSearchBar(
                                query = searchQuery,
                                onQueryChange = onSearchChange,
                                placeholder = stringResource(R.string.home_search_placeholder)
                            )
                        }
                    }
                }
                HomeSectionKind.FILTER_CHIPS -> {
                    if (homeAppearance.showFilterChips) {
                        item(key = "filter_chips") {
                            val hasLockedChats = remember(nonCommunityChats, hideLockedCurrently) {
                                !hideLockedCurrently && nonCommunityChats.any { it.isLocked }
                            }
                            val visibleChips = remember(showExtraFilters, hasLockedChats, groupFilterMode) {
                                buildList {
                                    add(HomeFilterChip.ALL)
                                    add(HomeFilterChip.UNREAD)
                                    add(HomeFilterChip.FAVORITES)
                                    if (groupFilterMode == null) add(HomeFilterChip.GROUPS)
                                    if (hasLockedChats) add(HomeFilterChip.LOCKED)
                                    if (showExtraFilters) {
                                        add(HomeFilterChip.DELETED)
                                        add(HomeFilterChip.EDITED)
                                    }
                                }
                            }
                            val chipAccent = homeAppearance.accentStyle.resolve(colors.isDark)
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(visibleChips) { chip ->
                                    val count = when (chip) {
                                        HomeFilterChip.ALL -> visibleChatsForCounts.size
                                        HomeFilterChip.UNREAD -> visibleChatsForCounts.count { it.unreadCount > 0 }
                                        HomeFilterChip.FAVORITES -> visibleChatsForCounts.count { it.isFavorite }
                                        HomeFilterChip.GROUPS -> visibleChatsForCounts.count { it.isGroup }
                                        HomeFilterChip.LOCKED -> nonCommunityChats.count { it.isLocked }
                                        HomeFilterChip.DELETED -> visibleChatsForCounts.count { it.hasDeletedMessages || it.lastMessageIsDeleted }
                                        HomeFilterChip.EDITED -> visibleChatsForCounts.count { it.hasEditedMessages }
                                    }
                                    val active = chip == selectedChip
                                    val isClassicUi = com.wa.beastclient.ui.theme.LocalWuppClassicUi.current
                                    val chipRadius by animateDpAsState(
                                        targetValue = if (isClassicUi) 20.dp else if (active) 12.dp else 20.dp,
                                        animationSpec = if (reducedMotion) tween(0) else MaterialTheme.motionScheme.fastSpatialSpec(),
                                        label = "filter_chip_radius_${chip.name}"
                                    )
                                    val chipBg by animateColorAsState(
                                        targetValue = if (active) chipAccent.copy(alpha = 0.18f) else Color.Transparent,
                                        animationSpec = if (reducedMotion) tween(0) else MaterialTheme.motionScheme.fastEffectsSpec(),
                                        label = "filter_chip_bg_${chip.name}"
                                    )
                                    val borderColor by animateColorAsState(
                                        targetValue = if (active) {
                                            chipAccent.copy(alpha = 0.55f)
                                        } else {
                                            colors.textSecondary.copy(alpha = 0.28f)
                                        },
                                        animationSpec = if (reducedMotion) tween(0) else MaterialTheme.motionScheme.fastEffectsSpec(),
                                        label = "filter_chip_border_${chip.name}"
                                    )
                                    val chipShape = RoundedCornerShape(chipRadius)
                                    Box(
                                        modifier = Modifier
                                            .clip(chipShape)
                                            .background(chipBg)
                                            .border(1.dp, borderColor, chipShape)
                                            .clickable { onSelectChip(chip) }
                                            .padding(horizontal = 14.dp, vertical = 7.dp)
                                    ) {
                                        val baseLabel = when (chip) {
                                            HomeFilterChip.ALL -> stringResource(R.string.home_filter_all)
                                            HomeFilterChip.UNREAD -> stringResource(R.string.home_filter_unread)
                                            HomeFilterChip.FAVORITES -> stringResource(R.string.home_filter_pinned)
                                            HomeFilterChip.GROUPS -> stringResource(R.string.home_filter_groups)
                                            HomeFilterChip.LOCKED -> stringResource(R.string.home_filter_locked)
                                            HomeFilterChip.DELETED -> chip.label
                                            HomeFilterChip.EDITED -> chip.label
                                        }
                                        val chipLabel = when (chip) {
                                            HomeFilterChip.UNREAD -> if (count > 0) "$baseLabel $count" else baseLabel
                                            HomeFilterChip.GROUPS -> if (count > 0) "$baseLabel $count" else baseLabel
                                            HomeFilterChip.LOCKED -> if (count > 0) "$baseLabel $count" else baseLabel
                                            else -> baseLabel
                                        }
                                        Text(
                                            text = chipLabel,
                                            fontSize = 13.sp,
                                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (active) chipAccent else colors.textSecondary
                                        )
                                    }
                                }
                                item(key = "filter_plus_chip") {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(if (showExtraFilters) chipAccent.copy(alpha = 0.18f) else Color.Transparent)
                                            .border(
                                                1.dp,
                                                if (showExtraFilters) chipAccent.copy(alpha = 0.45f) else colors.textSecondary.copy(alpha = 0.28f),
                                                RoundedCornerShape(20.dp)
                                            )
                                            .clickable { showExtraFilters = !showExtraFilters }
                                            .padding(horizontal = 13.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (showExtraFilters) Icons.Rounded.Close else Icons.Rounded.Add,
                                            contentDescription = "More filters",
                                            tint = if (showExtraFilters) chipAccent else colors.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                HomeSectionKind.ARCHIVED_BANNER -> {
                    if (archivedCount > 0 || includeArchived) {
                        item(key = "archived_banner_row") {
                            val archAccent = homeAppearance.accentStyle.resolve(colors.isDark)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleIncludeArchived(!includeArchived) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (includeArchived) archAccent.copy(alpha = 0.18f)
                                            else colors.elevatedBar
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (includeArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                                        contentDescription = null,
                                        tint = archAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = if (includeArchived) {
                                        stringResource(R.string.home_back_to_active_chats)
                                    } else {
                                        stringResource(R.string.home_archived_chats)
                                    },
                                    color = colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                if (archivedCount > 0) {
                                    Text(
                                        text = archivedCount.toString(),
                                        color = archAccent,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = mono
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (tabHiddenLockedCount > 0 && !hasSecretCode) {
            item(key = "locked_chats_hidden_banner") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onRequestRevealLockedChats)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.accentGreen.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = colors.accentGreen,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.home_locked_chats_banner, tabHiddenLockedCount),
                            color = colors.textPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.home_locked_chats_unlock_action),
                            color = colors.accentGreen,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else if ((hideLockedBehindLock || hasSecretCode) && lockedChatsRevealed && nonCommunityChats.any { it.isLocked }) {
            item(key = "locked_chats_revealed_banner") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onHideRevealedLockedChats)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LockOpen,
                        contentDescription = null,
                        tint = colors.accentGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(
                            if (hasSecretCode) R.string.home_secret_chats_unlocked_banner
                            else R.string.home_locked_chats_unlocked_banner
                        ),
                        color = colors.accentGreen,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // FTS4 Search Matches when typing search query
        if (searchQuery.trim().length >= 2 && ftsHits.isNotEmpty()) {
            item(key = "fts_header") {
                Text(
                    text = "Message matches (${ftsHits.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accentGreen,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            items(ftsHits.take(6), key = { "fts_${it.id}" }) { hit ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChatClick(ChatEntity(jid = hit.chatJid, name = hit.senderName)) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = hit.senderName.ifBlank { hit.chatJid.substringBefore("@") },
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        )
                        Text(
                            text = hit.body.ifBlank { hit.audioTranscript.orEmpty() },
                            color = colors.textSecondary,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = formatMessageClock(hit.timestamp, includeSeconds = showSeconds, use24Hour = LocalWuppUse24Hour.current),
                        fontFamily = mono,
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }
            item { HorizontalDivider(color = colors.divider) }
        }

        // Empty state
        if (chats.isEmpty()) {
            item(key = "empty_state") {
                val syncingNow = bridgeStatus.connectionState == "SYNCING" || bridgeStatus.connectionState == "CONNECTING"
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = colors.elevatedBar.copy(alpha = if (colors.isDark) 0.72f else 0.94f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 28.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 34.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (syncingNow) {
                            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                            ContainedLoadingIndicator(
                                containerColor = colors.pillActiveBg,
                                indicatorColor = colors.brightGreen,
                                modifier = Modifier.size(52.dp)
                            )
                        } else {
                            ExpressiveIconBadge(
                                icon = Icons.Rounded.ChatBubbleOutline,
                                contentDescription = null,
                                active = true,
                                modifier = Modifier.size(54.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = when {
                                syncingNow -> "Syncing your conversations..."
                                bridgeStatus.connectionState == "CONNECTED" -> "No chats yet"
                                else -> "Your chats will appear here"
                            },
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Your synced conversations will appear here automatically.",
                            color = colors.textSecondary,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }
        } else {
            items(chats, key = { it.jid }) { chat ->
                ChatListRow(
                    chat = chat,
                    selected = chat.jid == singleSelectedChatJid || chat.jid in selectedChatJids,
                    compactRows = compactRows,
                    homeAppearance = homeAppearance,
                    reducedMotion = reducedMotion,
                    showSeconds = showSeconds,
                    hideStealthBadges = hideStealthBadges,
                    onAvatarClick = { onAvatarClick(chat) },
                    onRowClick = { onChatClick(chat) },
                    onRowLongClick = { onChatLongClick(chat) },
                    onBoundsChanged = { rect -> onRowBoundsChanged(chat.jid, rect) }
                )
                if (homeAppearance.showRowDividers) {
                    HorizontalDivider(
                        color = colors.divider.copy(alpha = 0.65f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = (homeAppearance.avatarSizeDp + 26).dp, end = 16.dp)
                    )
                }
            }
        }
    }
    }
}

internal fun resolveChatSubtitlePreview(chat: ChatEntity): String {
    val isViewOncePreview = chat.lastMessageType.startsWith("VIEW_ONCE") ||
        chat.lastMessagePreview.startsWith("View once")

    val fallbackPreview = when (chat.lastMessageType) {
        "PTT" -> "🎤 Voice message"
        "AUDIO" -> "🎵 Audio"
        "IMAGE" -> "📷 Photo"
        "VIDEO" -> "🎥 Video"
        "STICKER" -> "💟 Sticker"
        "DOCUMENT" -> "📄 Document"
        else -> ""
    }
    val baseSubtitle = when {
        chat.chatPresence.isNotBlank() -> chat.chatPresence
        isViewOncePreview && !chat.lastMessageIsDeleted -> when {
            chat.lastMessagePreview.startsWith("Opened") -> chat.lastMessagePreview
            chat.lastMessageType.endsWith("VIDEO") -> "Video"
            chat.lastMessageType.endsWith("AUDIO") || chat.lastMessageType.endsWith("PTT") -> "Voice message"
            else -> "Photo"
        }
        chat.lastMessagePreview.isNotBlank() -> chat.lastMessagePreview
        else -> fallbackPreview
    }
    val rawSender = chat.lastMessageSenderName.trim()
    val cleanSender = when {
        rawSender.isBlank() -> ""
        com.wa.beastclient.domain.contacts.DeviceContactFallbackResolver.isGroupJidLikeIdentifier(rawSender, chat.jid) -> ""
        rawSender.endsWith("@lid", ignoreCase = true) -> ""
        rawSender.endsWith("@s.whatsapp.net", ignoreCase = true) ->
            com.wa.beastclient.domain.contacts.DeviceContactFallbackResolver
                .formatIntlPhoneCompact(rawSender.substringBefore("@"))
                .ifBlank { "" }
        else -> rawSender
    }
    return if (
        chat.isGroup &&
        !chat.lastMessageFromMe &&
        chat.chatPresence.isBlank() &&
        cleanSender.isNotBlank() &&
        baseSubtitle.isNotBlank() &&
        !baseSubtitle.startsWith("$cleanSender:") &&
        !baseSubtitle.contains(" reacted ")
    ) {
        "$cleanSender: $baseSubtitle"
    } else {
        baseSubtitle
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListRow(
    chat: ChatEntity,
    selected: Boolean = false,
    compactRows: Boolean = false,
    homeAppearance: HomeAppearanceConfig = HomeAppearanceConfig(),
    reducedMotion: Boolean = false,
    showSeconds: Boolean = false,
    hideStealthBadges: Boolean = false,
    onAvatarClick: () -> Unit,
    onRowClick: () -> Unit,
    onRowLongClick: () -> Unit = {},
    onBoundsChanged: (Rect) -> Unit = {}
) {
    val chatDrafts by ChatDraftManager.drafts.collectAsState()
    val draftText = chatDrafts[chat.jid].orEmpty()
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    val homeAccent = homeAppearance.accentStyle.resolve(colors.isDark)
    val rowInteraction = remember { MutableInteractionSource() }
    val cardBaseBg = if (homeAppearance.surfaceTone == HomeSurfaceTone.ELEVATED_CARDS) {
        if (colors.isDark) Color(0xFF191E26) else Color.White
    } else {
        Color.Transparent
    }
    val rowBg by animateColorAsState(
        targetValue = if (selected) homeAccent.copy(alpha = 0.16f) else cardBaseBg,
        animationSpec = if (reducedMotion) tween(0) else MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "chat_row_selection_bg"
    )
    val rowRadius by animateDpAsState(
        targetValue = if (selected) 20.dp else if (homeAppearance.surfaceTone == HomeSurfaceTone.ELEVATED_CARDS) 16.dp else 14.dp,
        animationSpec = if (reducedMotion) tween(0) else MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "chat_row_radius"
    )
    val avatarSize = if (compactRows) minOf(46, homeAppearance.avatarSizeDp).dp else homeAppearance.avatarSizeDp.dp
    val rowVerticalPad = if (compactRows) minOf(6f, homeAppearance.rowDensity.verticalPadDp).dp else homeAppearance.rowDensity.verticalPadDp.dp
    val rowHeight = (avatarSize.value + rowVerticalPad.value * 2f + if (homeAppearance.previewMaxLines > 1) 14f else 4f).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords -> onBoundsChanged(coords.boundsInRoot()) }
            .then(if (reducedMotion) Modifier else Modifier.expressivePressScale(rowInteraction, pressedScale = 0.985f))
            .padding(
                horizontal = if (selected || homeAppearance.surfaceTone == HomeSurfaceTone.ELEVATED_CARDS) 8.dp else 0.dp,
                vertical = if (homeAppearance.surfaceTone == HomeSurfaceTone.ELEVATED_CARDS) 3.dp else 1.dp
            )
            .clip(RoundedCornerShape(rowRadius))
            .height(rowHeight)
            .background(rowBg)
            .then(
                if (selected) {
                    Modifier.border(
                        1.dp,
                        homeAccent.copy(alpha = 0.35f),
                        RoundedCornerShape(rowRadius)
                    )
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                interactionSource = rowInteraction,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onRowClick,
                onLongClick = onRowLongClick
            )
            .padding(horizontal = if (selected) 10.dp else 14.dp, vertical = rowVerticalPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .combinedClickable(
                    onClick = onAvatarClick,
                    onLongClick = onRowLongClick
                )
        ) {
            WhatsAppAvatar(
                jid = chat.jid,
                avatarPath = chat.avatarPath,
                isGroup = chat.isGroup,
                size = avatarSize
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(21.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(colors.brightGreen)
                        .border(2.dp, colors.appSurface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = colors.brightGreen.contrastingContentColor(),
                        modifier = Modifier.size(13.dp)
                    )
                }
            } else if (chat.isOnline && !hideStealthBadges) {
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(colors.brightGreen)
                        .border(2.dp, colors.appSurface, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(if (compactRows) 1.5.dp else 3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.name.ifBlank { chat.jid.substringBefore("@") },
                    color = colors.textPrimary,
                    fontWeight = if (chat.unreadCount > 0 && homeAppearance.boldUnreadTitles) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = ((if (compactRows) 15f else 16f) * homeAppearance.titleScale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatMessageClock(chat.lastMessageTimestamp, includeSeconds = showSeconds, use24Hour = LocalWuppUse24Hour.current),
                    fontFamily = mono,
                    fontSize = 11.5.sp,
                    color = if (chat.unreadCount > 0) homeAccent else colors.textSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isViewOncePreview = chat.lastMessageType.startsWith("VIEW_ONCE") ||
                    chat.lastMessagePreview.startsWith("View once")

                val showDraft = draftText.isNotBlank() && chat.chatPresence.isBlank()
                if (chat.chatPresence.isBlank() && !showDraft) {
                    if (chat.lastMessageIsDeleted) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_delete_sweep),
                            contentDescription = null,
                            tint = colors.antiDeleteRed,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(14.dp)
                        )
                    } else if (chat.lastMessageFromMe) {
                        DeliveryTickIcon(
                            status = chat.lastMessageStatus,
                            mutedColor = colors.textSecondary,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    if (!chat.lastMessageIsDeleted && isViewOncePreview) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_view_once),
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier
                                .padding(end = 5.dp)
                                .size(15.dp)
                        )
                    }
                }

                if (showDraft) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = homeAccent, fontWeight = FontWeight.Medium)) {
                                append("Draft: ")
                            }
                            append(draftText.replace('\n', ' ').trim())
                        },
                        color = colors.textSecondary,
                        fontSize = (13.5f * homeAppearance.subtitleScale).sp,
                        maxLines = homeAppearance.previewMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    val subtitle = resolveChatSubtitlePreview(chat)
                    Text(
                        text = subtitle,
                        color = when {
                            chat.chatPresence.isNotBlank() -> homeAccent
                            chat.lastMessageIsDeleted -> colors.antiDeleteRed
                            else -> colors.textSecondary
                        },
                        fontSize = (13.5f * homeAppearance.subtitleScale).sp,
                        maxLines = homeAppearance.previewMaxLines,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (chat.isLocked) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Locked",
                            tint = colors.accentGreen,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (chat.isArchived) {
                        Icon(
                            imageVector = Icons.Rounded.Archive,
                            contentDescription = "Archived",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (chat.isMuted) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_mute_list),
                            contentDescription = "Muted",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (chat.isPinned) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_pin),
                            contentDescription = "Pinned",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (chat.unreadCount > 0) {
                        when (homeAppearance.unreadBadgeStyle) {
                            HomeBadgeStyle.SOLID_PILL -> {
                                Box(
                                    modifier = Modifier
                                        .animateContentSize()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(homeAccent)
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (chat.unreadCount > 999) "999+" else chat.unreadCount.toString(),
                                        color = homeAccent.contrastingContentColor(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = mono
                                    )
                                }
                            }
                            HomeBadgeStyle.SOFT_TINT -> {
                                Box(
                                    modifier = Modifier
                                        .animateContentSize()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(homeAccent.copy(alpha = 0.18f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (chat.unreadCount > 999) "999+" else chat.unreadCount.toString(),
                                        color = homeAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = mono
                                    )
                                }
                            }
                            HomeBadgeStyle.MINIMAL_DOT -> {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(homeAccent)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelsTabContent(
    channels: List<ChatEntity>,
    searchQuery: String,
    listState: LazyListState,
    bottomPadding: Dp = 96.dp,
    selectedChatJids: Set<String> = emptySet(),
    singleSelectedChatJid: String? = null,
    onRowBoundsChanged: (String, Rect) -> Unit = { _, _ -> },
    onChannelLongClick: (ChatEntity) -> Unit = {},
    onDismissSelection: () -> Unit = {},
    onOpenChannel: (ChatEntity) -> Unit
) {
    val colors = LocalWaColors.current
    val filteredChannels = remember(channels, searchQuery) {
        filterChannelsByQuery(channels, searchQuery)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (singleSelectedChatJid != null && selectedChatJids.isEmpty()) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissSelection
                    )
                } else Modifier
            ),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding + 16.dp)
    ) {
        item(key = "channels_tab_header") {
            Text(
                text = stringResource(R.string.nav_channels),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        if (filteredChannels.isEmpty()) {
            item(key = "channels_tab_empty") {
                Text(
                    text = if (searchQuery.isBlank()) {
                        "Subscribed channels will appear here once synced."
                    } else {
                        "No channels matching \"${searchQuery.trim()}\"."
                    },
                    color = colors.textSecondary,
                    fontSize = 13.5.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
        } else {
            items(filteredChannels, key = { "channel_tab_${it.jid}" }) { ch ->
                val isSelected = ch.jid == singleSelectedChatJid || ch.jid in selectedChatJids
                ChannelListRow(
                    channel = ch,
                    selected = isSelected,
                    onClick = { onOpenChannel(ch) },
                    onLongClick = { onChannelLongClick(ch) },
                    onBoundsChanged = { rect -> onRowBoundsChanged(ch.jid, rect) }
                )
            }
        }
    }
}

@Composable
private fun UpdatesStatusTab(
    statuses: List<StatusEntity>,
    channels: List<ChatEntity>,
    allChats: List<ChatEntity>,
    allContacts: List<ContactEntity> = emptyList(),
    hiddenLockedJids: Set<String> = emptySet(),
    preferPhonebookNames: Boolean = false,
    ownJid: String = "",
    searchQuery: String,
    listState: LazyListState,
    bottomPadding: Dp = 96.dp,
    verticalStatusLayout: Boolean = false,
    hideChannels: Boolean = false,
    showSeconds: Boolean = false,
    selectedChatJids: Set<String> = emptySet(),
    singleSelectedChatJid: String? = null,
    onRowBoundsChanged: (String, Rect) -> Unit = { _, _ -> },
    onChannelLongClick: (ChatEntity) -> Unit = {},
    onDismissSelection: () -> Unit = {},
    onSearchChange: (String) -> Unit = {},
    onOpenStatus: (StatusEntity) -> Unit,
    onAddStatus: (StatusComposerMode) -> Unit,
    onOpenChannel: (ChatEntity) -> Unit
) {
    val colors = LocalWaColors.current
    val (chatsByJid, chatsByUser) = remember(allChats) { buildStatusChatLookup(allChats) }
    val (contactsByJid, contactsByUser) = remember(allContacts) {
        val byJid = HashMap<String, ContactEntity>(allContacts.size * 2)
        val byUser = HashMap<String, ContactEntity>(allContacts.size * 2)
        for (c in allContacts) {
            val norm = normalizeStatusSenderJid(c.jid)
            byJid[c.jid] = c
            byJid[norm] = c
            val user = norm.substringBefore("@").trim()
            if (user.isNotBlank()) byUser[user] = c
        }
        byJid to byUser
    }
    val (ownStatuses, contactStatuses) = remember(statuses, ownJid) {
        partitionOwnAndContactStatuses(statuses, ownJid, filterExpired = true)
    }
    val latestOwnStatus = remember(ownStatuses) { ownStatuses.maxByOrNull { it.timestamp } }
    val ownAvatarPath = remember(ownJid, chatsByJid, chatsByUser) {
        if (ownJid.isBlank()) null else resolveStatusSenderChat(ownJid, chatsByJid, chatsByUser)?.avatarPath
    }
    val groupedStatuses = remember(contactStatuses, allChats, allContacts, hiddenLockedJids, preferPhonebookNames, searchQuery) {
        val visibleStatuses = if (hiddenLockedJids.isEmpty()) contactStatuses else contactStatuses.filter { st ->
            val norm = normalizeStatusSenderJid(st.senderJid)
            val bare = norm.substringBefore("@").trim()
            st.senderJid !in hiddenLockedJids && norm !in hiddenLockedJids && bare !in hiddenLockedJids
        }
        val q = searchQuery.trim().lowercase()
        val summaries = buildContactStatusSummaries(visibleStatuses)
        if (q.isEmpty()) summaries
        else summaries.filter { summary ->
            val st = summary.representativeStatus
            val senderChat = resolveStatusSenderChat(st.senderJid, chatsByJid, chatsByUser)
            val norm = normalizeStatusSenderJid(st.senderJid)
            val senderContact = contactsByJid[st.senderJid] ?: contactsByJid[norm] ?: contactsByUser[norm.substringBefore("@").trim()]
            val name = resolveStatusDisplayName(st.senderJid, st.senderName, senderChat, senderContact, preferPhonebookNames)
            name.lowercase().contains(q) || summary.items.any { it.caption.lowercase().contains(q) }
        }
    }
    val filteredChannels = remember(channels, searchQuery) {
        filterChannelsByQuery(channels, searchQuery)
    }
    val iosCircleBg = if (colors.isDark) Color(0xFF1F2024) else Color(0xFFEBEBF0)

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (singleSelectedChatJid != null && selectedChatJids.isEmpty()) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissSelection
                    )
                } else Modifier
            ),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
    ) {
        item(key = "updates_ios_search") {
            IosInlineSearchBar(
                query = searchQuery,
                onQueryChange = onSearchChange,
                placeholder = "Search"
            )
        }

        item(key = "updates_status_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(iosCircleBg)
                            .clickable { onAddStatus(StatusComposerMode.MEDIA) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_camera_reply),
                            contentDescription = "Camera status",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(iosCircleBg)
                            .clickable { onAddStatus(StatusComposerMode.TEXT) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Text status",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        if (verticalStatusLayout) {
            item(key = "updates_my_status_vertical") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (latestOwnStatus != null) {
                                onOpenStatus(latestOwnStatus)
                            } else {
                                onAddStatus(StatusComposerMode.MEDIA)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(50.dp)) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .then(
                                    if (latestOwnStatus != null) {
                                        Modifier
                                            .border(2.dp, if (latestOwnStatus.isRevoked) colors.antiDeleteRed else colors.brightGreen, CircleShape)
                                            .padding(2.dp)
                                    } else {
                                        Modifier
                                    }
                                )
                                .background(Color(0xFF4A2531)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (ownJid.isNotBlank() && !ownAvatarPath.isNullOrBlank()) {
                                WhatsAppAvatar(
                                    jid = ownJid,
                                    avatarPath = ownAvatarPath,
                                    isGroup = false,
                                    size = 44.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = Color(0xFFF48FB1),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(19.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(colors.brightGreen)
                                .border(2.dp, colors.appSurface, CircleShape)
                                .clickable { onAddStatus(StatusComposerMode.MEDIA) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add status",
                                tint = colors.brightGreen.contrastingContentColor(),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.status_my_status),
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.5.sp
                        )
                        Text(
                            text = if (latestOwnStatus != null) {
                                buildString {
                                    append(formatMessageClock(latestOwnStatus.timestamp, includeSeconds = showSeconds, use24Hour = LocalWuppUse24Hour.current))
                                    if (latestOwnStatus.isRevoked) {
                                        append(" · ")
                                        append(
                                            formatDeletedLabel(
                                                deletedAtMs = latestOwnStatus.revokedAt,
                                                fallbackTimestampMs = latestOwnStatus.timestamp,
                                                includeSeconds = showSeconds,
                                                use24Hour = LocalWuppUse24Hour.current
                                            )
                                        )
                                    }
                                    if (ownStatuses.size > 1) {
                                        append(" · ${ownStatuses.size}")
                                    }
                                }
                            } else {
                                stringResource(R.string.status_add_update)
                            },
                            color = if (latestOwnStatus?.isRevoked == true) colors.antiDeleteRed else colors.textSecondary,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }
            if (groupedStatuses.isEmpty()) {
                item(key = "updates_status_empty_vertical") {
                    Text(
                        text = stringResource(R.string.home_empty_updates),
                        color = colors.textSecondary,
                        fontSize = 13.5.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            } else {
                items(groupedStatuses, key = { "status_vert_${it.senderKey}" }) { summary ->
                    val st = summary.representativeStatus
                    val senderChat = resolveStatusSenderChat(st.senderJid, chatsByJid, chatsByUser)
                    val norm = normalizeStatusSenderJid(st.senderJid)
                    val senderContact = contactsByJid[st.senderJid] ?: contactsByJid[norm] ?: contactsByUser[norm.substringBefore("@").trim()]
                    val displayName = resolveStatusDisplayName(st.senderJid, st.senderName, senderChat, senderContact, preferPhonebookNames)
                    val ringColor = when {
                        summary.hasRevoked -> colors.antiDeleteRed
                        summary.hasUnviewed -> colors.brightGreen
                        else -> colors.textSecondary.copy(alpha = 0.55f)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenStatus(st) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .border(2.dp, ringColor, CircleShape)
                                .padding(3.dp)
                        ) {
                            WhatsAppAvatar(
                                jid = senderChat?.jid ?: st.senderJid,
                                avatarPath = senderChat?.avatarPath,
                                isGroup = false,
                                size = 46.dp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append(formatMessageClock(st.timestamp, includeSeconds = showSeconds, use24Hour = LocalWuppUse24Hour.current))
                                    if (st.isRevoked) {
                                        append(" · ")
                                        append(
                                            formatDeletedLabel(
                                                deletedAtMs = st.revokedAt,
                                                fallbackTimestampMs = st.timestamp,
                                                includeSeconds = showSeconds,
                                                use24Hour = LocalWuppUse24Hour.current
                                            )
                                        )
                                    }
                                    if (st.caption.isNotBlank()) {
                                        append(" · ")
                                        append(st.caption)
                                    }
                                },
                                color = if (st.isRevoked) colors.antiDeleteRed else colors.textSecondary,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        } else {
            item(key = "updates_status_cards_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "add_status_card") {
                        AddStatusStoryCard(
                            latestOwnStatus = latestOwnStatus,
                            ownJid = ownJid,
                            ownAvatarPath = ownAvatarPath,
                            onOpenOwnStatus = { latestOwnStatus?.let(onOpenStatus) },
                            onAddStatus = { onAddStatus(StatusComposerMode.MEDIA) }
                        )
                    }
                    items(groupedStatuses, key = { "status_card_${it.senderKey}" }) { summary ->
                        val st = summary.representativeStatus
                        val senderChat = resolveStatusSenderChat(st.senderJid, chatsByJid, chatsByUser)
                        val norm = normalizeStatusSenderJid(st.senderJid)
                        val senderContact = contactsByJid[st.senderJid] ?: contactsByJid[norm] ?: contactsByUser[norm.substringBefore("@").trim()]
                        StatusStoryCard(
                            status = st,
                            senderChat = senderChat,
                            senderContact = senderContact,
                            preferPhonebookNames = preferPhonebookNames,
                            hasUnviewed = summary.hasUnviewed,
                            hasRevoked = summary.hasRevoked,
                            onClick = { onOpenStatus(st) }
                        )
                    }
                }
            }
        }

        if (!hideChannels) {
            item(key = "updates_channels_header") {
                Text(
                    text = "Channels",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(start = 16.dp, top = 22.dp, end = 16.dp, bottom = 6.dp)
                )
            }

            if (filteredChannels.isEmpty()) {
                item(key = "updates_channels_empty") {
                    Text(
                        text = "Subscribed channels will appear here once synced.",
                        color = colors.textSecondary,
                        fontSize = 13.5.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }
            } else {
                items(filteredChannels, key = { "channel_${it.jid}" }) { ch ->
                    val isSelected = ch.jid == singleSelectedChatJid || ch.jid in selectedChatJids
                    ChannelListRow(
                        channel = ch,
                        selected = isSelected,
                        onClick = { onOpenChannel(ch) },
                        onLongClick = { onChannelLongClick(ch) },
                        onBoundsChanged = { rect -> onRowBoundsChanged(ch.jid, rect) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddStatusStoryCard(
    latestOwnStatus: StatusEntity?,
    ownJid: String,
    ownAvatarPath: String?,
    onOpenOwnStatus: () -> Unit,
    onAddStatus: () -> Unit
) {
    val colors = LocalWaColors.current
    val ownMediaBmp = remember(latestOwnStatus?.localMediaPath) {
        latestOwnStatus?.localMediaPath?.let { path ->
            File(path).takeIf { it.exists() && it.length() > 0 }
                ?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }.getOrNull() }
        }
    }
    Box(
        modifier = Modifier
            .width(100.dp)
            .height(162.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (latestOwnStatus != null) Color(0xFF1A3438) else colors.elevatedBar)
            .border(
                width = if (latestOwnStatus != null) 1.dp else 0.5.dp,
                color = if (latestOwnStatus != null) colors.brightGreen.copy(alpha = 0.65f) else colors.textSecondary.copy(alpha = 0.16f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable {
                if (latestOwnStatus != null) onOpenOwnStatus() else onAddStatus()
            }
    ) {
        if (ownMediaBmp != null) {
            Image(
                bitmap = ownMediaBmp,
                contentDescription = stringResource(R.string.status_my_status),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.25f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.72f)
                            )
                        )
                    )
            )
        } else if (latestOwnStatus != null && latestOwnStatus.caption.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 34.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = latestOwnStatus.caption,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 11.5.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = if (latestOwnStatus != null) {
                Modifier
                    .padding(8.dp)
                    .size(40.dp)
                    .align(Alignment.TopStart)
            } else {
                Modifier
                    .size(56.dp)
                    .align(Alignment.Center)
            }
        ) {
            val ringSize = if (latestOwnStatus != null) 38.dp else 54.dp
            Box(
                modifier = Modifier
                    .size(ringSize)
                    .clip(CircleShape)
                    .then(
                        if (latestOwnStatus != null) {
                            Modifier
                                .border(2.dp, if (latestOwnStatus.isRevoked) colors.antiDeleteRed else colors.brightGreen, CircleShape)
                                .padding(2.dp)
                        } else {
                            Modifier
                        }
                    )
                    .background(Color(0xFF4A2531)),
                contentAlignment = Alignment.Center
            ) {
                if (ownJid.isNotBlank() && !ownAvatarPath.isNullOrBlank()) {
                    WhatsAppAvatar(
                        jid = ownJid,
                        avatarPath = ownAvatarPath,
                        isGroup = false,
                        size = ringSize - 4.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = Color(0xFFF48FB1),
                        modifier = Modifier.size(if (latestOwnStatus != null) 22.dp else 32.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(if (latestOwnStatus != null) 18.dp else 22.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(colors.brightGreen)
                    .border(2.dp, colors.elevatedBar, CircleShape)
                    .clickable(onClick = onAddStatus),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add status",
                    tint = colors.brightGreen.contrastingContentColor(),
                    modifier = Modifier.size(if (latestOwnStatus != null) 11.dp else 14.dp)
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp, start = 8.dp, end = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (latestOwnStatus?.isRevoked == true) {
                Text(
                    text = formatDeletedLabel(
                        deletedAtMs = latestOwnStatus.revokedAt,
                        fallbackTimestampMs = latestOwnStatus.timestamp,
                        includeSeconds = LocalWuppShowSeconds.current,
                        use24Hour = LocalWuppUse24Hour.current
                    ),
                    color = Color(0xFFFF6B6B),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(Color(0xB344181A), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            Text(
                text = if (latestOwnStatus != null) stringResource(R.string.status_my_status) else "Add status",
                color = if (latestOwnStatus != null) Color.White else colors.textPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

internal fun normalizeStatusSenderJid(rawJid: String): String {
    val trimmed = rawJid.trim()
    val atIdx = trimmed.indexOf('@')
    if (atIdx <= 0) return trimmed
    val user = trimmed.substring(0, atIdx).substringBefore(':')
    val server = trimmed.substring(atIdx + 1)
    return if (user.isNotBlank() && server.isNotBlank()) "$user@$server" else trimmed
}

private fun isPhoneLikeDisplayName(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return false
    var hasDigit = false
    for (ch in trimmed) {
        when {
            ch.isDigit() -> hasDigit = true
            ch == '+' || ch == '-' || ch == ' ' || ch == '(' || ch == ')' -> Unit
            else -> return false
        }
    }
    return hasDigit
}

private fun buildStatusChatLookup(
    allChats: List<ChatEntity>
): Pair<Map<String, ChatEntity>, Map<String, ChatEntity>> {
    val byJid = LinkedHashMap<String, ChatEntity>(allChats.size * 2)
    val byUser = LinkedHashMap<String, ChatEntity>(allChats.size)
    for (chat in allChats) {
        byJid.putIfAbsent(chat.jid, chat)
        val norm = normalizeStatusSenderJid(chat.jid)
        if (norm.isNotBlank()) byJid.putIfAbsent(norm, chat)
        val user = norm.substringBefore("@").trim()
        if (user.isNotBlank() && !chat.isGroup && !chat.jid.endsWith("@newsletter")) {
            val prev = byUser[user]
            if (prev == null || (isPhoneLikeDisplayName(prev.name) && !isPhoneLikeDisplayName(chat.name))) {
                byUser[user] = chat
            }
        }
    }
    return byJid to byUser
}

private fun resolveStatusSenderChat(
    senderJid: String,
    chatsByJid: Map<String, ChatEntity>,
    chatsByUser: Map<String, ChatEntity>
): ChatEntity? {
    val norm = normalizeStatusSenderJid(senderJid)
    return chatsByJid[senderJid]
        ?: chatsByJid[norm]
        ?: chatsByUser[norm.substringBefore("@").trim()]
}

internal fun resolveStatusDisplayName(
    senderJid: String,
    storedName: String,
    senderChat: ChatEntity?,
    senderContact: ContactEntity? = null,
    preferPhonebookNames: Boolean = false
): String {
    val chatName = senderChat?.name?.trim().orEmpty()
    val contactName = senderContact?.name?.trim().orEmpty()
    val norm = normalizeStatusSenderJid(senderJid)
    val user = norm.substringBefore("@")
    val chatUseful = chatName.isNotBlank() && chatName != senderJid && chatName != norm && chatName != user
    val contactUseful = contactName.isNotBlank() && contactName != senderJid && contactName != norm && contactName != user
    val storedClean = storedName.trim()
    val storedUseful = storedClean.isNotBlank() &&
        storedClean != "Status" &&
        storedClean != senderJid &&
        storedClean != norm &&
        storedClean != user
    return when {
        preferPhonebookNames && contactUseful && !isPhoneLikeDisplayName(contactName) -> contactName
        chatUseful && !isPhoneLikeDisplayName(chatName) -> chatName
        contactUseful && !isPhoneLikeDisplayName(contactName) -> contactName
        storedUseful && !isPhoneLikeDisplayName(storedClean) -> storedClean
        chatUseful -> chatName
        contactUseful -> contactName
        storedUseful -> storedClean
        else -> user.ifBlank { "Status" }
    }
}

@Composable
private fun StatusStoryCard(
    status: StatusEntity,
    senderChat: ChatEntity?,
    senderContact: ContactEntity? = null,
    preferPhonebookNames: Boolean = false,
    hasUnviewed: Boolean,
    hasRevoked: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalWaColors.current
    val context = LocalContext.current
    val mediaBmp = remember(status.localMediaPath) {
        status.localMediaPath?.let { path ->
            File(path).takeIf { it.exists() && it.length() > 0 }
                ?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }.getOrNull() }
        }
    }
    LaunchedEffect(status.id, mediaBmp == null) {
        if (mediaBmp == null && status.msgType.equals("IMAGE", ignoreCase = true)) {
            WaRealtimeService.requestStatusMediaDownload(context, status, userInitiated = false)
        }
    }
    val displayName = resolveStatusDisplayName(
        senderJid = status.senderJid,
        storedName = status.senderName,
        senderChat = senderChat,
        senderContact = senderContact,
        preferPhonebookNames = preferPhonebookNames
    )

    Box(
        modifier = Modifier
            .width(100.dp)
            .height(162.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1A3438))
            .clickable(onClick = onClick)
    ) {
        if (mediaBmp != null) {
            Image(
                bitmap = mediaBmp,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (status.caption.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 34.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = status.caption,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 11.5.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.28f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.72f)
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .padding(8.dp)
                .size(40.dp)
                .align(Alignment.TopStart)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = when {
                        hasRevoked || status.isRevoked -> colors.antiDeleteRed
                        hasUnviewed -> colors.brightGreen
                        else -> colors.textSecondary.copy(alpha = 0.55f)
                    },
                    shape = CircleShape
                )
                .padding(3.dp)
        ) {
            WhatsAppAvatar(
                jid = senderChat?.jid ?: status.senderJid,
                avatarPath = senderChat?.avatarPath,
                isGroup = false,
                size = 34.dp
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (status.isRevoked) {
                Text(
                    text = formatDeletedLabel(
                        deletedAtMs = status.revokedAt,
                        fallbackTimestampMs = status.timestamp,
                        includeSeconds = LocalWuppShowSeconds.current,
                        use24Hour = LocalWuppUse24Hour.current
                    ),
                    color = Color(0xFFFF6B6B),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(Color(0xB344181A), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            Text(
                text = displayName,
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelListRow(
    channel: ChatEntity,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onBoundsChanged: (Rect) -> Unit = {}
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    val homeAppearance by HomeAppearanceManager.config.collectAsState()
    val homeAccent = homeAppearance.accentStyle.resolve(colors.isDark)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords -> onBoundsChanged(coords.boundsInRoot()) }
            .padding(horizontal = if (selected) 8.dp else 0.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(if (selected) 20.dp else 14.dp))
            .background(if (selected) homeAccent.copy(alpha = 0.16f) else Color.Transparent)
            .then(
                if (selected) {
                    Modifier.border(
                        1.dp,
                        homeAccent.copy(alpha = 0.35f),
                        RoundedCornerShape(20.dp)
                    )
                } else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = if (selected) 10.dp else 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(52.dp)) {
            WhatsAppAvatar(
                jid = channel.jid,
                avatarPath = channel.avatarPath,
                isGroup = false,
                size = 52.dp
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(21.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(colors.brightGreen)
                        .border(2.dp, colors.appSurface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = colors.brightGreen.contrastingContentColor(),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = channel.name.ifBlank { channel.jid.substringBefore("@") },
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (channel.lastMessageTimestamp > 0L) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatMessageClock(channel.lastMessageTimestamp, includeSeconds = LocalWuppShowSeconds.current, use24Hour = LocalWuppUse24Hour.current),
                        fontFamily = mono,
                        fontSize = 11.5.sp,
                        color = if (channel.unreadCount > 0) colors.brightGreen else colors.textSecondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = channel.lastMessagePreview.ifBlank {
                        channel.aboutText.ifBlank { "Channel updates" }
                    },
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (channel.isPinned) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_pin),
                            contentDescription = "Pinned",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (channel.unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.brightGreen)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (channel.unreadCount > 999) "999+" else channel.unreadCount.toString(),
                                color = colors.brightGreen.contrastingContentColor(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = mono
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunitiesTab(
    allChats: List<ChatEntity>,
    listState: LazyListState,
    bottomPadding: Dp = 96.dp,
    selectedChatJids: Set<String> = emptySet(),
    singleSelectedChatJid: String? = null,
    onRowBoundsChanged: (String, Rect) -> Unit = { _, _ -> },
    onGroupLongClick: (ChatEntity) -> Unit = {},
    onDismissSelection: () -> Unit = {},
    onOpenGroup: (ChatEntity) -> Unit
) {
    val colors = LocalWaColors.current
    var expandedCommunities by remember { mutableStateOf(setOf<String>()) }

    val groups = remember(allChats) { allChats.filter { it.isGroup } }
    val parentByJid = remember(groups) {
        groups.filter { it.isCommunityParent }.associateBy { it.jid }
    }
    val childrenByParent = remember(groups) {
        groups.filter { !it.isCommunityParent && it.parentGroupJid.isNotBlank() }
            .groupBy { it.parentGroupJid }
            .mapValues { (_, list) ->
                list.sortedWith(
                    compareByDescending<ChatEntity> { it.isDefaultSubgroup }
                        .thenByDescending { it.lastMessageTimestamp }
                        .thenBy { it.name.lowercase() }
                )
            }
    }
    val communityJids = remember(parentByJid, childrenByParent) {
        (parentByJid.keys + childrenByParent.keys).distinct().sortedByDescending { jid ->
            val parentTs = parentByJid[jid]?.lastMessageTimestamp ?: 0L
            val childTs = childrenByParent[jid]?.maxOfOrNull { it.lastMessageTimestamp } ?: 0L
            maxOf(parentTs, childTs)
        }
    }
    val standaloneGroups = remember(groups) {
        groups.filter { !it.isCommunityParent && it.parentGroupJid.isBlank() }
            .sortedWith(
                compareByDescending<ChatEntity> { it.lastMessageTimestamp }
                    .thenBy { it.name.lowercase() }
            )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (singleSelectedChatJid != null && selectedChatJids.isEmpty()) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissSelection
                    )
                } else Modifier
            ),
        contentPadding = PaddingValues(bottom = bottomPadding + 8.dp)
    ) {
        item(key = "new_community_row") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(48.dp)) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.textSecondary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_avatar_community),
                            contentDescription = "New community",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(colors.accentGreen)
                            .border(1.5.dp, colors.appSurface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "New community",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(colors.elevatedBar.copy(alpha = 0.45f))
            )
        }

        if (communityJids.isEmpty()) {
            item(key = "communities_empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.accentGreen.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_avatar_community),
                            contentDescription = null,
                            tint = colors.accentGreen,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Stay connected with a community",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Communities bring members together in topic-based groups, and make it easy to get admin announcements.",
                        color = colors.textSecondary,
                        fontSize = 13.5.sp
                    )
                }
            }
        }

        communityJids.forEach { parentJid ->
            val parentChat = parentByJid[parentJid]
            val children = childrenByParent[parentJid].orEmpty()
            val isExpanded = parentJid in expandedCommunities
            val visibleChildren = if (isExpanded || children.size <= 3) children else children.take(3)
            val communityTitle = parentChat?.name?.takeIf {
                it.isNotBlank() && it != parentJid && it != parentJid.substringBefore("@")
            } ?: "Community"

            item(key = "comm_header_$parentJid") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val announcement = children.firstOrNull { it.isDefaultSubgroup } ?: children.firstOrNull()
                            if (announcement != null) {
                                onOpenGroup(announcement)
                            } else if (parentChat != null) {
                                onOpenGroup(parentChat)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CommunityAvatarBox(
                        avatarPath = parentChat?.avatarPath,
                        isRoundedSquare = true,
                        isAnnouncement = false,
                        sizeDp = 46
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = communityTitle,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val topic = parentChat?.aboutText.orEmpty()
                        if (topic.isNotBlank()) {
                            Text(
                                text = topic,
                                color = colors.textSecondary,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 76.dp, end = 16.dp),
                    thickness = 0.5.dp,
                    color = colors.textSecondary.copy(alpha = 0.14f)
                )
            }

            items(visibleChildren, key = { "comm_${parentJid}_sub_${it.jid}" }) { sub ->
                val isSelected = sub.jid == singleSelectedChatJid || sub.jid in selectedChatJids
                CommunityGroupRow(
                    chat = sub,
                    selected = isSelected,
                    onClick = { onOpenGroup(sub) },
                    onLongClick = { onGroupLongClick(sub) },
                    onBoundsChanged = { rect -> onRowBoundsChanged(sub.jid, rect) }
                )
            }

            if (children.size > 3) {
                item(key = "comm_more_$parentJid") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedCommunities = if (isExpanded) {
                                    expandedCommunities - parentJid
                                } else {
                                    expandedCommunities + parentJid
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = colors.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = if (isExpanded) "Show less" else "View all (${children.size})",
                            color = colors.textSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.5.sp
                        )
                    }
                }
            }

            item(key = "comm_sep_$parentJid") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(colors.elevatedBar.copy(alpha = 0.45f))
                )
            }
        }

        if (standaloneGroups.isNotEmpty()) {
            item(key = "standalone_groups_header") {
                Text(
                    text = "Groups",
                    color = colors.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 6.dp)
                )
            }
            items(standaloneGroups, key = { "standalone_${it.jid}" }) { g ->
                val isSelected = g.jid == singleSelectedChatJid || g.jid in selectedChatJids
                CommunityGroupRow(
                    chat = g,
                    selected = isSelected,
                    onClick = { onOpenGroup(g) },
                    onLongClick = { onGroupLongClick(g) },
                    onBoundsChanged = { rect -> onRowBoundsChanged(g.jid, rect) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommunityGroupRow(
    chat: ChatEntity,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onBoundsChanged: (Rect) -> Unit = {}
) {
    val colors = LocalWaColors.current
    val homeAppearance by HomeAppearanceManager.config.collectAsState()
    val homeAccent = homeAppearance.accentStyle.resolve(colors.isDark)
    val title = if (chat.isDefaultSubgroup && (chat.name.isBlank() || chat.name == chat.jid.substringBefore("@"))) {
        "Announcements"
    } else {
        chat.name.ifBlank { chat.jid.substringBefore("@") }
    }
    val subtitle = resolveChatSubtitlePreview(chat).ifBlank {
        chat.aboutText.ifBlank { if (chat.isDefaultSubgroup) "Announcements" else "" }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords -> onBoundsChanged(coords.boundsInRoot()) }
            .padding(horizontal = if (selected) 8.dp else 0.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(if (selected) 20.dp else 14.dp))
            .background(if (selected) homeAccent.copy(alpha = 0.16f) else Color.Transparent)
            .then(
                if (selected) {
                    Modifier.border(
                        1.dp,
                        homeAccent.copy(alpha = 0.35f),
                        RoundedCornerShape(20.dp)
                    )
                } else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = if (selected) 10.dp else 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp)) {
            CommunityAvatarBox(
                avatarPath = chat.avatarPath,
                isRoundedSquare = chat.isDefaultSubgroup,
                isAnnouncement = chat.isDefaultSubgroup,
                sizeDp = 44
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(colors.brightGreen)
                        .border(2.dp, colors.appSurface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = colors.brightGreen.contrastingContentColor(),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (chat.lastMessageTimestamp > 0L) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatMessageClock(chat.lastMessageTimestamp, includeSeconds = LocalWuppShowSeconds.current, use24Hour = LocalWuppUse24Hour.current),
                        color = if (chat.unreadCount > 0) colors.accentGreen else colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            if (subtitle.isNotBlank() || chat.unreadCount > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = subtitle,
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (chat.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(colors.accentGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = chat.unreadCount.toString(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityAvatarBox(
    avatarPath: String?,
    isRoundedSquare: Boolean,
    isAnnouncement: Boolean,
    sizeDp: Int
) {
    val colors = LocalWaColors.current
    val shape = if (isRoundedSquare) RoundedCornerShape(12.dp) else CircleShape
    val avatarBmp = remember(avatarPath) {
        avatarPath?.takeIf { it.isNotBlank() }?.let { path ->
            File(path).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
        }
    }

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(shape)
            .background(colors.accentGreen.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        if (avatarBmp != null && !isAnnouncement) {
            Image(
                bitmap = avatarBmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isAnnouncement) {
            Icon(
                imageVector = Icons.Rounded.Campaign,
                contentDescription = null,
                tint = colors.accentGreen,
                modifier = Modifier.size((sizeDp * 0.52f).dp)
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.vec_ic_avatar_community),
                contentDescription = null,
                tint = colors.accentGreen,
                modifier = Modifier.size((sizeDp * 0.54f).dp)
            )
        }
    }
}

private enum class SettingsCategory(
    val label: String,
    val icon: ImageVector
) {
    ALL("All settings", Icons.Rounded.Tune),
    ACCOUNT("Account", Icons.Rounded.ManageAccounts),
    PRIVACY("Privacy", Icons.Rounded.VisibilityOff),
    CALLS("Calls", Icons.Rounded.Call),
    MEDIA("Media & storage", Icons.Rounded.CloudDownload),
    APPEARANCE("Appearance", Icons.Rounded.Palette),
    ALERTS("Notifications", Icons.Rounded.Notifications),
    UPDATES("Help & updates", Icons.Rounded.SystemUpdate)
}

@Composable
private fun ToolsVaultTab(
    viewModel: MainViewModel,
    listState: LazyListState,
    bottomPadding: Dp = 96.dp,
    savedHistoryCount: Int = 0,
    customStickerCount: Int = 0,
    editProfileRequestNonce: Int = 0,
    onOpenStarredChats: () -> Unit = {},
    onOpenMessageHistory: () -> Unit = {},
    onOpenMediaVault: () -> Unit = {},
    onOpenStickerStudio: () -> Unit = {},
    onOpenContactQr: () -> Unit = {},
    onOpenFeedback: (GitHubFeedbackType) -> Unit
) {
    val context = LocalContext.current
    val tabScope = rememberCoroutineScope()
    val colors = LocalWaColors.current
    val stealth by viewModel.stealthConfig.collectAsState()
    val appLockConfig by viewModel.appLockConfig.collectAsState()
    val bridgeStatus by viewModel.bridgeStatus.collectAsState()
    val allChats by viewModel.allChats.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val activeAccountId by viewModel.activeAccountId.collectAsState()
    val isSwitchingAccount by viewModel.isSwitchingAccount.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val includePreReleases by viewModel.includePreReleases.collectAsState()
    var ownProfile by remember { mutableStateOf(OwnProfileUi()) }
    var selectedCategory by rememberSaveable { mutableStateOf(SettingsCategory.ALL) }
    var settingsSearchQuery by rememberSaveable { mutableStateOf("") }
    var infoDialogContent by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showAccountSwitcherSheet by remember { mutableStateOf(false) }
    var showProfileEditorDialog by remember { mutableStateOf(false) }
    var showDiagnosticsLogsDialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(editProfileRequestNonce) {
        if (editProfileRequestNonce > 0) {
            showProfileEditorDialog = true
        }
    }
    var showAccountPrivacyDialog by remember { mutableStateOf(false) }
    var showBlockedContactsDialog by remember { mutableStateOf(false) }
    var showCompanionScopeDialog by remember { mutableStateOf(false) }
    var showStorageUsageDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var editingAutoDlNetwork by remember { mutableStateOf<AutoDownloadNetwork?>(null) }
    var showGalleryContactsDialog by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showReactionDialog by remember { mutableStateOf(false) }
    var showAppLockDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showClockFormatDialog by remember { mutableStateOf(false) }
    var showCustomizeNavBarDialog by remember { mutableStateOf(false) }
    var showHideNotifGuide by remember { mutableStateOf(false) }
    var showCallingSetupDialog by remember { mutableStateOf(false) }
    var showAntiCallDialog by remember { mutableStateOf(false) }
    var isSyncNotifHidden by remember {
        mutableStateOf(WaRealtimeService.isRealtimeStatusNotificationHidden(context))
    }
    var isBatteryUnrestricted by remember {
        mutableStateOf(WaRealtimeService.isIgnoringBatteryOptimizations(context))
    }
    var hasContactsPermission by remember {
        mutableStateOf(DeviceContactFallbackResolver.hasReadContactsPermission(context))
    }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasContactsPermission = granted
        if (granted) {
            viewModel.setLocalContactFallbackEnabled(true)
        } else {
            viewModel.setLocalContactFallbackEnabled(false)
            Toast.makeText(
                context,
                "Contacts permission not granted — showing server names and numbers only",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(bridgeStatus.isLoggedIn, bridgeStatus.connectionState) {
        viewModel.fetchOwnProfile().onSuccess { ownProfile = it }
    }

    DisposableEffect(context) {
        val lifecycleOwner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isSyncNotifHidden = WaRealtimeService.isRealtimeStatusNotificationHidden(context)
                isBatteryUnrestricted = WaRealtimeService.isIgnoringBatteryOptimizations(context)
                hasContactsPermission = DeviceContactFallbackResolver.hasReadContactsPermission(context)
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    val normalizedQuery = settingsSearchQuery.trim().lowercase()
    val isSearching = normalizedQuery.isNotEmpty()
    fun matchesSearch(vararg fields: String): Boolean {
        if (!isSearching) return true
        return fields.any { it.lowercase().contains(normalizedQuery) }
    }
    fun showCategory(cat: SettingsCategory): Boolean =
        isSearching || selectedCategory == SettingsCategory.ALL || selectedCategory == cat

    val privacyDotActive = stealth.offlineMode || stealth.ghostPresence || stealth.hideReadReceipts ||
        stealth.hideChatContactNames || stealth.hideChatProfilePictures
    val updatesDotActive = updateState is UpdateUiState.UpdateAvailable || updateState is UpdateUiState.ReadyToInstall

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.nav_settings),
                color = colors.textPrimary,
                fontSize = 31.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp
            )
            val searchShape = CircleShape
            Surface(
                shape = searchShape,
                color = colors.elevatedBar.copy(alpha = if (colors.isDark) 0.94f else 0.98f),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 0.65.dp,
                        color = if (isSearching) colors.accentGreen.copy(alpha = 0.6f)
                        else if (colors.isDark) Color.White.copy(alpha = 0.08f) else colors.divider,
                        shape = searchShape
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search settings",
                        tint = if (isSearching) colors.accentGreen else colors.textSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (settingsSearchQuery.isEmpty()) {
                            Text(
                                text = "Search settings",
                                color = colors.textSecondary.copy(alpha = 0.75f),
                                fontSize = 14.5.sp
                            )
                        }
                        BasicTextField(
                            value = settingsSearchQuery,
                            onValueChange = { settingsSearchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = colors.textPrimary,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(colors.accentGreen),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (isSearching) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(colors.textSecondary.copy(alpha = 0.16f))
                                .clickable { settingsSearchQuery = "" },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            if (!isSearching) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 4.dp)
                ) {
                    items(SettingsCategory.entries) { category ->
                        val selected = selectedCategory == category
                        val showDot = when (category) {
                            SettingsCategory.PRIVACY -> privacyDotActive
                            SettingsCategory.UPDATES -> updatesDotActive
                            else -> false
                        }
                        val pillShape = CircleShape
                        Row(
                            modifier = Modifier
                                .clip(pillShape)
                                .height(36.dp)
                                .background(
                                    if (selected) colors.accentGreen.copy(alpha = 0.18f)
                                    else colors.elevatedBar.copy(alpha = if (colors.isDark) 0.90f else 0.97f)
                                )
                                .border(
                                    width = if (selected) 1.dp else 0.6.dp,
                                    color = if (selected) colors.accentGreen.copy(alpha = 0.65f)
                                    else if (colors.isDark) Color.White.copy(alpha = 0.08f) else colors.divider,
                                    shape = pillShape
                                )
                                .clickable {
                                    selectedCategory = category
                                    tabScope.launch { listState.scrollToItem(0) }
                                }
                                .padding(horizontal = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                tint = if (selected) colors.accentGreen else colors.textSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = category.label,
                                color = if (selected) colors.textPrimary else colors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                            if (showDot) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(colors.accentGreen)
                                )
                            }
                        }
                    }
                }
            }
        }

        val showAccountSection = showCategory(SettingsCategory.ACCOUNT) && (
            matchesSearch("switch", "add", "whatsapp", "account", "multi") ||
                matchesSearch(stringResource(R.string.settings_wa_privacy_title), "privacy", "last seen", "profile photo") ||
                matchesSearch(stringResource(R.string.settings_blocked_contacts_title), "blocked", "contacts") ||
                matchesSearch(stringResource(R.string.settings_account_limits_title), "companion", "limits", "linked")
            )
        val showPrivacySection = showCategory(SettingsCategory.PRIVACY) && (
            matchesSearch("offline mode", "pause sync", "disconnect") ||
                matchesSearch("ghost mode", "freeze last seen", "online", "outbound") ||
                matchesSearch("hide read receipts", "blue ticks", "seen") ||
                matchesSearch("hide played receipts", "voice notes", "audio") ||
                matchesSearch("hide typing", "recording") ||
                matchesSearch("anti-delete", "deleted messages", "statuses") ||
                matchesSearch("disappearing", "ephemeral", "immunity") ||
                matchesSearch(stringResource(R.string.settings_applock_row_title), "app lock", "pin", "biometric") ||
                matchesSearch(stringResource(R.string.settings_allow_screenshots_title), "screenshots", "screen") ||
                matchesSearch("privacy mode", "hide contact names", "hide profile pictures", "dp", "avatar", "names")
            )
        val showCallingSection = showCategory(SettingsCategory.CALLS) && (
            matchesSearch("calling", "voip", "voice", "video", "always ready", "smart warm", "official", "ring", "anti-call", "record", "pip", "swap", "flip", "preview", "layout")
            )
        val showVaultsSection = showCategory(SettingsCategory.MEDIA) && (
            matchesSearch("deleted", "edited", "history", "vault", "call recordings", "view once", "sticker studio")
            )
        val showMediaSection = showCategory(SettingsCategory.MEDIA) && (
            matchesSearch(stringResource(R.string.settings_manage_storage_title), "storage", "cache", "auto-download", "mobile data", "wifi", "roaming", "media visibility", "gallery", "hide contact media")
            )
        val showAppearanceSection = showCategory(SettingsCategory.APPEARANCE) && (
            matchesSearch("theme", "oled", "material you", "accent", "bubble", "font", "wallpaper", "reactions", "emoji", "language", "enter sends")
            )
        val showLooksSection = showCategory(SettingsCategory.APPEARANCE) && (
            matchesSearch("status", "vertical", "bio", "classic", "compact", "seconds", "time format", "clock", "camera", "badges", "separate groups", "channels", "local contact names", "fallback", "phonebook", "prefer", "navigation bar", "bottom bar", "tabs", "customize")
            )
        val showAlertsSection = showCategory(SettingsCategory.ALERTS) && (
            matchesSearch("notifications", "dm", "group", "preview", "vibrate", "conversation sounds", "chat sounds", "sound", "sync banner", "battery", "unrestricted", "24/7")
            )
        val showUpdatesSection = showCategory(SettingsCategory.UPDATES) && (
            matchesSearch("update", "pre-release", "bug", "feature", "feedback", "github", "version", "about", "log", "logs", "crash", "diagnostic", "share", "save")
            )
        val hasAnyVisibleSection = showAccountSection || showPrivacySection || showCallingSection ||
            showVaultsSection || showMediaSection || showAppearanceSection ||
            showLooksSection || showAlertsSection || showUpdatesSection

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 8.dp,
                end = 16.dp,
                bottom = bottomPadding + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isSearching && (selectedCategory == SettingsCategory.ALL || selectedCategory == SettingsCategory.ACCOUNT)) {
                item(key = "patch_section_profile_card") {
                    val ownAvatarPath = remember(bridgeStatus.ownJid, allChats) {
                        val ownUser = bridgeStatus.ownJid.substringBefore("@").substringBefore(":")
                        allChats.firstOrNull { ch ->
                            !ch.avatarPath.isNullOrBlank() &&
                                ownUser.isNotBlank() &&
                                ch.jid.substringBefore("@").substringBefore(":") == ownUser
                        }?.avatarPath
                    }
                    SettingsProfileHeaderCard(
                        ownJid = bridgeStatus.ownJid,
                        profile = ownProfile,
                        avatarPath = ownAvatarPath,
                        onClick = { showProfileEditorDialog = true }
                    )
                }

                item(key = "patch_ios_quick_group_card") {
                    val cardBg = if (colors.isDark) Color(0xFF1A1B1E) else colors.elevatedBar
                    val dividerColor = if (colors.isDark) Color.White.copy(alpha = 0.08f) else colors.divider
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = cardBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val quickRows = listOf(
                                Triple(Icons.Rounded.StarOutline, "Starred messages", onOpenStarredChats),
                                Triple(Icons.Rounded.History, if (savedHistoryCount > 0) "Message history ($savedHistoryCount)" else "Message history", onOpenMessageHistory),
                                Triple(Icons.Rounded.Shield, "Media vault", onOpenMediaVault),
                                Triple(Icons.Rounded.AutoFixHigh, if (customStickerCount > 0) "Sticker studio ($customStickerCount)" else "Sticker studio", onOpenStickerStudio),
                                Triple(Icons.Rounded.QrCode2, "My QR code & scan", onOpenContactQr)
                            )
                            quickRows.forEachIndexed { idx, (icon, label, action) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = action)
                                        .padding(horizontal = 16.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(21.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = label,
                                        color = colors.textPrimary,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = colors.textSecondary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                if (idx < quickRows.lastIndex) {
                                    HorizontalDivider(
                                        color = dividerColor,
                                        thickness = 0.6.dp,
                                        modifier = Modifier.padding(start = 51.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isSearching && !hasAnyVisibleSection) {
                item(key = "patch_search_empty") {
                    ExpressiveSegmentedContainer(position = ExpressiveSegmentPosition.SINGLE) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "No settings match \"$settingsSearchQuery\"",
                                color = colors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Clear search",
                                color = colors.accentGreen,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { settingsSearchQuery = "" }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            if (showAccountSection) {
                item(key = "patch_section_account_privacy") {
                    val activeAccount = accounts.firstOrNull { it.id == activeAccountId } ?: accounts.firstOrNull()
                    val acctSubtitle = buildString {
                        if (activeAccount != null) {
                            append(activeAccount.displayTitle)
                            val phone = activeAccount.displayPhone
                            if (phone.isNotBlank()) {
                                append(" (")
                                append(phone)
                                append(")")
                            }
                            append(" · ")
                        }
                        append(if (accounts.size == 1) "1 account · Tap to add" else "${accounts.size} accounts")
                    }
                    ExpressiveSegmentedGroup(title = stringResource(R.string.settings_section_account_privacy)) {
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.ManageAccounts,
                            title = "Switch or add WhatsApp account",
                            subtitle = acctSubtitle,
                            position = ExpressiveSegmentPosition.TOP,
                            onClick = { showAccountSwitcherSheet = true }
                        )
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.AdminPanelSettings,
                            title = stringResource(R.string.settings_wa_privacy_title),
                            subtitle = stringResource(R.string.settings_wa_privacy_subtitle),
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onClick = { showAccountPrivacyDialog = true }
                        )
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.Block,
                            title = stringResource(R.string.settings_blocked_contacts_title),
                            subtitle = stringResource(R.string.settings_blocked_contacts_subtitle),
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onClick = { showBlockedContactsDialog = true }
                        )
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.Info,
                            title = stringResource(R.string.settings_account_limits_title),
                            subtitle = stringResource(R.string.settings_account_limits_subtitle),
                            position = ExpressiveSegmentPosition.BOTTOM,
                            onClick = { showCompanionScopeDialog = true }
                        )
                    }
                }
            }

            if (showPrivacySection) {
                item(key = "patch_section_privacy") {
                    val lockSubtitle = when {
                        appLockConfig.mode == AppLockMode.DEVICE -> stringResource(R.string.settings_applock_row_subtitle_device)
                        appLockConfig.mode == AppLockMode.CUSTOM_PIN -> stringResource(R.string.settings_applock_row_subtitle_pin)
                        appLockConfig.hasSecretCode -> stringResource(R.string.settings_applock_row_subtitle_secret)
                        else -> stringResource(R.string.settings_applock_row_subtitle_off)
                    }
                    ExpressiveSegmentedGroup(title = "Privacy, Stealth & Security") {
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.Flight,
                            title = "Offline Mode (Pause Sync)",
                            checked = stealth.offlineMode,
                            position = ExpressiveSegmentPosition.TOP,
                            onInfoClick = {
                                infoDialogContent = "Offline Mode (Pause Sync)" to
                                    "Disconnects WhatsApp sync while keeping internet active for other apps on your phone."
                            },
                            onCheckedChange = { viewModel.setOfflineMode(it) }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.VisibilityOff,
                            title = "Ghost Mode (Freeze Last Seen)",
                            checked = stealth.ghostPresence,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = "Ghost Mode (Freeze Last Seen)" to
                                    "Hides your online status and freezes your last seen. Automatically unfreezes safely when sending."
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(ghostPresence = it) }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.Shield,
                            title = "Outbound Activity Confirmation",
                            checked = stealth.ghostOutboundGuardPrompt,
                            enabled = stealth.ghostPresence,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = "Outbound Activity Confirmation" to
                                    "Prompts before unfreezing last seen when sending messages, media, or reactions while Ghost Mode is active."
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(ghostOutboundGuardPrompt = it) }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.CheckCircle,
                            title = "Hide Read Receipts",
                            checked = stealth.hideReadReceipts,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = "Hide Read Receipts" to
                                    "Read incoming messages privately without sending blue ticks to the sender."
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(hideReadReceipts = it) }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.Campaign,
                            title = "Hide Played Receipts",
                            checked = stealth.hidePlayedReceipts,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = "Hide Played Receipts" to
                                    "Play voice notes and audio messages without marking them as played."
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(hidePlayedReceipts = it) }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.Shield,
                            title = "Hide Typing & Recording",
                            checked = stealth.hideTypingRecording,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = "Hide Typing & Recording" to
                                    "Suppresses typing and recording audio indicators in chats."
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(hideTypingRecording = it) }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.DeleteSweep,
                            title = "Anti-Delete Messages & Statuses",
                            checked = stealth.antiDeleteEnabled,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = "Anti-Delete Messages & Statuses" to
                                    "Keeps messages, media, and statuses even after the sender deletes them for everyone."
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(antiDeleteEnabled = it) }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.Lock,
                            title = "Disappearing Message Immunity",
                            checked = stealth.disappearingImmunity,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = "Disappearing Message Immunity" to
                                    "Prevents ephemeral timers from expiring messages in your local chats."
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(disappearingImmunity = it) }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.VisibilityOff,
                            title = "Hide Names & DPs in Chats",
                            checked = stealth.hideChatContactNames && stealth.hideChatProfilePictures,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = "Hide Names & DPs in Chats" to
                                    "Turns on both contact name masking and profile picture hiding inside chat conversations."
                            },
                            onCheckedChange = { enabled ->
                                viewModel.updateGlobalStealth { c ->
                                    c.copy(
                                        hideChatContactNames = enabled,
                                        hideChatProfilePictures = enabled
                                    )
                                }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.Person,
                            title = "Hide Contact Names in Chats",
                            checked = stealth.hideChatContactNames,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = "Hide Contact Names in Chats" to
                                    "Replaces contact, group, and sender names with neutral labels inside open chat screens."
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(hideChatContactNames = it) }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.Image,
                            title = "Hide Profile Pictures (DPs) in Chats",
                            checked = stealth.hideChatProfilePictures,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = "Hide Profile Pictures (DPs) in Chats" to
                                    "Hides contact and participant profile photos in the chat header and group message bubbles."
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(hideChatProfilePictures = it) }
                            }
                        )
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.Lock,
                            title = stringResource(R.string.settings_applock_row_title),
                            subtitle = lockSubtitle,
                            badgeText = if (appLockConfig.mode != AppLockMode.OFF || appLockConfig.hasSecretCode) {
                                stringResource(R.string.common_enabled)
                            } else null,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onClick = { showAppLockDialog = true }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.Image,
                            title = stringResource(R.string.settings_allow_screenshots_title),
                            checked = appLockConfig.allowScreenshots,
                            position = if (appLockConfig.mode != AppLockMode.OFF) {
                                ExpressiveSegmentPosition.MIDDLE
                            } else {
                                ExpressiveSegmentPosition.BOTTOM
                            },
                            onInfoClick = {
                                infoDialogContent = context.getString(R.string.settings_allow_screenshots_title) to
                                    context.getString(R.string.settings_allow_screenshots_subtitle)
                            },
                            onCheckedChange = { AppLockManager.setAllowScreenshots(it) }
                        )
                        if (appLockConfig.mode != AppLockMode.OFF) {
                            ExpressiveSegmentedActionRow(
                                icon = Icons.Rounded.Shield,
                                title = stringResource(R.string.settings_lock_now_title),
                                subtitle = stringResource(R.string.settings_lock_now_subtitle),
                                position = ExpressiveSegmentPosition.BOTTOM,
                                onClick = { AppLockManager.lockNow() }
                            )
                        }
                    }
                }
            }

            if (showCallingSection) {
                item(key = "patch_section_calling") {
                    ExpressiveSegmentedGroup(title = "Calls") {
                        val nativeActive = stealth.callBackendMode != CallBackendMode.OFF
                        val linkBadge = if (nativeActive) "Native VoIP" else "Official App"
                        val statusSummary = when {
                            stealth.offlineMode -> "Paused while Offline Mode is enabled"
                            !nativeActive -> "Handing off calls to Official WhatsApp"
                            else -> "Active backend: ${stealth.callBackendMode.title}"
                        }
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.Call,
                            title = "In-App Calling Setup & Mode",
                            subtitle = statusSummary,
                            badgeText = linkBadge,
                            position = ExpressiveSegmentPosition.TOP,
                            onClick = { showCallingSetupDialog = true }
                        )
                        val orderedModes = listOf(CallBackendMode.ALWAYS_READY, CallBackendMode.SMART_WARM, CallBackendMode.OFF)
                        orderedModes.forEach { mode ->
                            val selected = stealth.callBackendMode == mode
                            ExpressiveSegmentedSwitchRow(
                                icon = Icons.Rounded.Call,
                                title = mode.title,
                                subtitle = mode.subtitle,
                                checked = selected,
                                position = ExpressiveSegmentPosition.MIDDLE,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        viewModel.updateGlobalStealth { c -> c.copy(callBackendMode = mode) }
                                    } else if (selected && mode != CallBackendMode.OFF) {
                                        viewModel.updateGlobalStealth { c -> c.copy(callBackendMode = CallBackendMode.OFF) }
                                    }
                                }
                            )
                        }
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.NotificationsOff,
                            title = "Let Official WhatsApp Ring Instead",
                            checked = stealth.suppressIncomingRingWhenOfficialInstalled,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = "Let Official WhatsApp Ring Instead" to
                                    "Silences Wupp's incoming ringtone and popup when Official WhatsApp is installed on this phone."
                            },
                            onCheckedChange = { enabled ->
                                viewModel.updateGlobalStealth { c ->
                                    c.copy(
                                        suppressIncomingRingWhenOfficialInstalled = enabled,
                                        officialCallMuteConfirmed = if (enabled) false else c.officialCallMuteConfirmed
                                    )
                                }
                                com.wa.beastclient.core.calling.CallStateManager.onConfigChanged(
                                    com.wa.beastclient.domain.stealth.StealthMatrixManager.config.value
                                )
                            }
                        )
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.Block,
                            title = "Anti-Call Protection",
                            subtitle = stealth.antiCallMode.subtitle,
                            badgeText = stealth.antiCallMode.badgeLabel.ifBlank { null },
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onClick = { showAntiCallDialog = true }
                        )
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.Videocam,
                            title = "Video Preview Tap Action",
                            subtitle = stealth.callPipTapAction.title,
                            badgeText = if (stealth.callPipTapAction == CallPipTapAction.SWAP_LAYOUT) "Swap" else "Flip",
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onClick = {
                                val next = if (stealth.callPipTapAction == CallPipTapAction.SWAP_LAYOUT) {
                                    CallPipTapAction.FLIP_CAMERA
                                } else {
                                    CallPipTapAction.SWAP_LAYOUT
                                }
                                viewModel.updateGlobalStealth { c -> c.copy(callPipTapAction = next) }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.FiberManualRecord,
                            title = "Auto-Record In-App Calls",
                            checked = stealth.autoRecordCalls,
                            position = ExpressiveSegmentPosition.BOTTOM,
                            onInfoClick = {
                                infoDialogContent = "Auto-Record In-App Calls" to
                                    "Automatically saves dual-sided call audio locally into your Media Vault."
                            },
                            onCheckedChange = { viewModel.setAutoRecordCalls(it) }
                        )
                    }
                }
            }

            if (showVaultsSection) {
                item(key = "patch_section_vaults_studios") {
                    ExpressiveSegmentedGroup(title = "Saved items") {
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.History,
                            title = "Deleted & Edited Message History",
                            subtitle = "Recovered deleted messages and edit timelines",
                            badgeText = if (savedHistoryCount > 0) "$savedHistoryCount" else null,
                            position = ExpressiveSegmentPosition.TOP,
                            onClick = onOpenMessageHistory
                        )
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.Shield,
                            title = "Media Vault & Call Recordings",
                            subtitle = "Private folders for View Once, Statuses & Calls",
                            badgeText = if (stealth.vaultLockEnabled) "LOCKED" else "OPEN",
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onClick = onOpenMediaVault
                        )
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.AutoFixHigh,
                            title = "Sticker Studio",
                            subtitle = "Create transparent WebP stickers",
                            badgeText = if (customStickerCount > 0) "$customStickerCount" else null,
                            position = ExpressiveSegmentPosition.BOTTOM,
                            onClick = onOpenStickerStudio
                        )
                    }
                }
            }

            if (showMediaSection) {
                item(key = "patch_section_storage_media") {
                    ExpressiveSegmentedGroup(title = "Storage & media") {
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.CleaningServices,
                            title = stringResource(R.string.settings_manage_storage_title),
                            subtitle = stringResource(R.string.settings_manage_storage_subtitle),
                            position = ExpressiveSegmentPosition.TOP,
                            onClick = { showStorageUsageDialog = true }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.CloudDownload,
                            title = "Media auto-download",
                            checked = stealth.autoDownloadEnabled,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = "Media auto-download" to
                                    "Automatically downloads incoming photos, audio, videos, and files according to the network rules below."
                            },
                            onCheckedChange = { viewModel.setAutoDownloadEnabled(it) }
                        )
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.SignalCellularAlt,
                            title = "When using mobile data",
                            subtitle = if (stealth.autoDownloadEnabled) {
                                StealthMatrixManager.formatAutoDownloadSummary(stealth.autoDownloadMobileMask)
                            } else {
                                "Turn on Media auto-download to configure"
                            },
                            enabled = stealth.autoDownloadEnabled,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onClick = { editingAutoDlNetwork = AutoDownloadNetwork.MOBILE }
                        )
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.Wifi,
                            title = "When connected on Wi-Fi",
                            subtitle = if (stealth.autoDownloadEnabled) {
                                StealthMatrixManager.formatAutoDownloadSummary(stealth.autoDownloadWifiMask)
                            } else {
                                "Turn on Media auto-download to configure"
                            },
                            enabled = stealth.autoDownloadEnabled,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onClick = { editingAutoDlNetwork = AutoDownloadNetwork.WIFI }
                        )
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.Flight,
                            title = "When roaming",
                            subtitle = if (stealth.autoDownloadEnabled) {
                                StealthMatrixManager.formatAutoDownloadSummary(stealth.autoDownloadRoamingMask)
                            } else {
                                "Turn on Media auto-download to configure"
                            },
                            enabled = stealth.autoDownloadEnabled,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onClick = { editingAutoDlNetwork = AutoDownloadNetwork.ROAMING }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.Collections,
                            title = "Media visibility",
                            checked = stealth.mediaVisibilityInGallery,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = "Media visibility" to
                                    "Shows newly downloaded media in your device's gallery app."
                            },
                            onCheckedChange = { viewModel.setMediaVisibilityInGallery(it) }
                        )
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.VisibilityOff,
                            title = "Hide contact media from gallery",
                            subtitle = if (!stealth.mediaVisibilityInGallery) {
                                "All media is already hidden from gallery"
                            } else if (stealth.hiddenGalleryJids.isEmpty()) {
                                "Keep specific contacts or groups out of gallery"
                            } else {
                                "${stealth.hiddenGalleryJids.size} contact(s)/group(s) hidden"
                            },
                            enabled = stealth.mediaVisibilityInGallery,
                            position = ExpressiveSegmentPosition.BOTTOM,
                            onClick = { showGalleryContactsDialog = true }
                        )
                    }
                }
            }

            if (showAppearanceSection) {
                item(key = "patch_section_appearance") {
                    ExpressiveSegmentedGroup(title = stringResource(R.string.settings_section_appearance)) {
                        val langSubtitle = when (stealth.appLanguage) {
                            LocaleHelper.LANG_TR -> stringResource(R.string.language_turkish)
                            LocaleHelper.LANG_EN -> stringResource(R.string.language_english)
                            else -> stringResource(R.string.language_system)
                        }
                        val reactionPreview = stealth.quickReactionEmojis
                            .ifEmpty { StealthMatrixManager.DEFAULT_QUICK_REACTIONS }
                            .joinToString("  ")

                        ExpressiveSegmentedContainer(position = ExpressiveSegmentPosition.TOP) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 15.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ExpressiveIconBadge(
                                        icon = Icons.Rounded.Palette,
                                        contentDescription = stringResource(R.string.settings_theme_title),
                                        active = true
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.settings_theme_title),
                                            color = colors.textPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = buildString {
                                                append(stealth.themeMode.label)
                                                append(" — ")
                                                append(stealth.themeMode.tagline)
                                                if (stealth.dynamicMaterialYou) append(" · Material You")
                                            },
                                            color = colors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                ThemeSelectorGrid(
                                    selectedTheme = stealth.themeMode,
                                    onSelectTheme = { viewModel.setThemeMode(it) },
                                    selectedAccent = stealth.accentColor,
                                    onSelectAccent = { viewModel.setAccentColor(it) },
                                    pureBlackOled = stealth.pureBlackOled,
                                    onTogglePureBlackOled = { viewModel.setPureBlackOled(it) },
                                    dynamicMaterialYou = stealth.dynamicMaterialYou,
                                    onToggleDynamicMaterialYou = { viewModel.setDynamicMaterialYou(it) },
                                    bubbleCornerRadiusDp = stealth.bubbleCornerRadiusDp,
                                    onBubbleCornerRadiusChange = { viewModel.setBubbleCornerRadiusDp(it) },
                                    customOutgoingBubbleArgb = stealth.customOutgoingBubbleArgb,
                                    customIncomingBubbleArgb = stealth.customIncomingBubbleArgb,
                                    onCustomBubbleColorsChange = { outArgb, inArgb -> viewModel.setCustomBubbleColors(outArgb, inArgb) },
                                    selectedFontStyle = stealth.fontStyle,
                                    hasImportedFont = stealth.customFontPath.isNotBlank(),
                                    onSelectFontStyle = { viewModel.setFontStyle(it) },
                                    onImportCustomFontUri = { viewModel.importCustomFontFromUri(it) }
                                )
                            }
                        }
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.Image,
                            title = stringResource(R.string.settings_wallpaper_title),
                            subtitle = describeChatWallpaper(stealth.chatWallpaperUri),
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onClick = { showWallpaperDialog = true }
                        )
                        ExpressiveSegmentedSliderRow(
                            icon = Icons.Rounded.FormatSize,
                            title = stringResource(R.string.settings_font_size_title),
                            valueLabel = formatFontScaleLabel(stealth.chatFontScale),
                            value = stealth.chatFontScale.coerceIn(0.80f, 1.45f),
                            valueRange = 0.80f..1.45f,
                            steps = 12,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onValueChange = { scale ->
                                viewModel.updateGlobalStealth { c -> c.copy(chatFontScale = scale) }
                            },
                            onClick = { showFontSizeDialog = true }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.TextFields,
                            title = stringResource(R.string.settings_system_font_title),
                            checked = stealth.useSystemFont,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = context.getString(R.string.settings_system_font_title) to
                                    context.getString(R.string.settings_system_font_subtitle)
                            },
                            onCheckedChange = { viewModel.setUseSystemFont(it) }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.KeyboardReturn,
                            title = stringResource(R.string.settings_enter_sends_title),
                            checked = stealth.enterIsSend,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = context.getString(R.string.settings_enter_sends_title) to
                                    context.getString(R.string.settings_enter_sends_subtitle)
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(enterIsSend = it) }
                            }
                        )
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.EmojiEmotions,
                            title = stringResource(R.string.settings_reactions_title),
                            subtitle = reactionPreview,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onClick = { showReactionDialog = true }
                        )
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.Translate,
                            title = stringResource(R.string.settings_language_title),
                            subtitle = langSubtitle,
                            position = ExpressiveSegmentPosition.BOTTOM,
                            onClick = { showLanguageDialog = true }
                        )
                    }
                }
            }

            if (showLooksSection) {
                item(key = "patch_section_looks") {
                    ExpressiveSegmentedGroup(title = stringResource(R.string.settings_section_looks)) {
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.ViewAgenda,
                            title = stringResource(R.string.settings_look_vertical_status_title),
                            checked = stealth.statusVerticalLayout,
                            position = ExpressiveSegmentPosition.TOP,
                            onInfoClick = {
                                infoDialogContent = context.getString(R.string.settings_look_vertical_status_title) to
                                    context.getString(R.string.settings_look_vertical_status_subtitle)
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(statusVerticalLayout = it) }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.Person,
                            title = stringResource(R.string.settings_look_always_bio_title),
                            checked = stealth.alwaysShowContactBio,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = context.getString(R.string.settings_look_always_bio_title) to
                                    context.getString(R.string.settings_look_always_bio_subtitle)
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(alwaysShowContactBio = it) }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.ViewAgenda,
                            title = stringResource(R.string.settings_look_classic_ui_title),
                            checked = stealth.classicWhatsAppLook,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = context.getString(R.string.settings_look_classic_ui_title) to
                                    context.getString(R.string.settings_look_classic_ui_subtitle)
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(classicWhatsAppLook = it) }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.ViewAgenda,
                            title = stringResource(R.string.settings_look_compact_rows_title),
                            checked = stealth.compactChatRows,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = context.getString(R.string.settings_look_compact_rows_title) to
                                    context.getString(R.string.settings_look_compact_rows_subtitle)
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(compactChatRows = it) }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.Schedule,
                            title = stringResource(R.string.settings_look_seconds_title),
                            checked = stealth.showSecondsInTimestamps,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = context.getString(R.string.settings_look_seconds_title) to
                                    context.getString(R.string.settings_look_seconds_subtitle)
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(showSecondsInTimestamps = it) }
                            }
                        )
                        val clockFormatSubtitle = when (stealth.clockFormat) {
                            WuppClockFormat.SYSTEM -> "Follow device (${if (LocalWuppUse24Hour.current) "24-hour" else "12-hour"})"
                            WuppClockFormat.HOUR_24 -> "24-hour (14:30)"
                            WuppClockFormat.HOUR_12 -> "12-hour (2:30 PM)"
                        }
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.Schedule,
                            title = "Time format",
                            subtitle = clockFormatSubtitle,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onClick = { showClockFormatDialog = true }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.VisibilityOff,
                            title = stringResource(R.string.settings_look_hide_camera_title),
                            checked = stealth.hideHomeCameraShortcut,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = context.getString(R.string.settings_look_hide_camera_title) to
                                    context.getString(R.string.settings_look_hide_camera_subtitle)
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(hideHomeCameraShortcut = it) }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.VisibilityOff,
                            title = stringResource(R.string.settings_look_hide_badges_title),
                            checked = stealth.hideStealthStatusBadges,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = context.getString(R.string.settings_look_hide_badges_title) to
                                    context.getString(R.string.settings_look_hide_badges_subtitle)
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(hideStealthStatusBadges = it) }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.Groups,
                            title = stringResource(R.string.settings_look_separate_groups_title),
                            checked = stealth.separateGroupsAndChats,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = context.getString(R.string.settings_look_separate_groups_title) to
                                    context.getString(R.string.settings_look_separate_groups_subtitle)
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(separateGroupsAndChats = it) }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.Campaign,
                            title = stringResource(R.string.settings_look_separate_channels_title),
                            checked = stealth.separateChannelsTab,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = context.getString(R.string.settings_look_separate_channels_title) to
                                    context.getString(R.string.settings_look_separate_channels_subtitle)
                            },
                            onCheckedChange = { enabled ->
                                viewModel.updateGlobalStealth { c ->
                                    c.copy(
                                        separateChannelsTab = enabled,
                                        hideChannels = if (enabled) false else c.hideChannels
                                    )
                                }
                            }
                        )
                        val fallbackActive = stealth.localContactFallbackEnabled && hasContactsPermission
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.Person,
                            title = "Local Contact Names Fallback",
                            checked = fallbackActive,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = "Local Contact Names Fallback" to
                                    "Reads your phone's Contacts locally (never uploaded) to show names when WhatsApp only provides numbers. Turn off anytime to use server names only."
                            },
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    if (DeviceContactFallbackResolver.hasReadContactsPermission(context)) {
                                        hasContactsPermission = true
                                        viewModel.setLocalContactFallbackEnabled(true)
                                    } else {
                                        contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                    }
                                } else {
                                    viewModel.setLocalContactFallbackEnabled(false)
                                }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.Person,
                            title = "Prefer Phonebook Contact Names",
                            checked = stealth.preferPhonebookContactNames,
                            enabled = fallbackActive,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = "Prefer Phonebook Contact Names" to
                                    "Shows the saved name from your phone's Contacts instead of WhatsApp's server push or contact name."
                            },
                            onCheckedChange = { prefer ->
                                viewModel.setPreferPhonebookContactNames(prefer)
                            }
                        )
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.Person,
                            title = "Refresh Phonebook Contacts",
                            subtitle = if (fallbackActive) {
                                "Re-scan your device contacts and update chat names"
                            } else {
                                "Turn on Local Contact Names Fallback to sync phonebook"
                            },
                            enabled = fallbackActive,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onClick = {
                                viewModel.syncDeviceContactFallbacks(forceRefresh = true, notifyUser = true)
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.VisibilityOff,
                            title = stringResource(R.string.settings_look_hide_channels_title),
                            checked = stealth.hideChannels,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = context.getString(R.string.settings_look_hide_channels_title) to
                                    context.getString(R.string.settings_look_hide_channels_subtitle)
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(hideChannels = it) }
                            }
                        )
                        val navAppearance by HomeAppearanceManager.config.collectAsState()
                        val navOrderAll = resolveHomeTabOrder(
                            separateGroupsAndChats = stealth.separateGroupsAndChats,
                            separateChannelsTab = stealth.separateChannelsTab && !stealth.hideChannels,
                            hiddenNavTabs = emptySet()
                        )
                        val navOrderVisible = resolveHomeTabOrder(
                            separateGroupsAndChats = stealth.separateGroupsAndChats,
                            separateChannelsTab = stealth.separateChannelsTab && !stealth.hideChannels,
                            hiddenNavTabs = navAppearance.hiddenNavTabs
                        )
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.Tune,
                            title = "Customize Navigation Bar",
                            subtitle = "${navOrderVisible.size} of ${navOrderAll.size} tabs visible",
                            position = ExpressiveSegmentPosition.BOTTOM,
                            onClick = { showCustomizeNavBarDialog = true }
                        )
                    }
                }
            }

            if (showAlertsSection) {
                item(key = "patch_section_notifications_sync") {
                    ExpressiveSegmentedGroup(title = "Notifications & Background Sync") {
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.Notifications,
                            title = stringResource(R.string.settings_notify_dm_title),
                            checked = stealth.notifyDmEnabled,
                            position = ExpressiveSegmentPosition.TOP,
                            onInfoClick = {
                                infoDialogContent = context.getString(R.string.settings_notify_dm_title) to
                                    context.getString(R.string.settings_notify_dm_subtitle)
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(notifyDmEnabled = it) }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.Groups,
                            title = stringResource(R.string.settings_notify_group_title),
                            checked = stealth.notifyGroupEnabled,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = context.getString(R.string.settings_notify_group_title) to
                                    context.getString(R.string.settings_notify_group_subtitle)
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(notifyGroupEnabled = it) }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.VisibilityOff,
                            title = stringResource(R.string.settings_notify_preview_title),
                            checked = stealth.notifyShowPreview,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = context.getString(R.string.settings_notify_preview_title) to
                                    context.getString(R.string.settings_notify_preview_subtitle)
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(notifyShowPreview = it) }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.Vibration,
                            title = stringResource(R.string.settings_notify_vibrate_title),
                            checked = stealth.notifyVibrateEnabled,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = context.getString(R.string.settings_notify_vibrate_title) to
                                    context.getString(R.string.settings_notify_vibrate_subtitle)
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(notifyVibrateEnabled = it) }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.VolumeUp,
                            title = "Conversation Sounds",
                            subtitle = "Play audio effects for incoming and outgoing chat messages",
                            checked = stealth.chatSoundsEnabled,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = "Conversation Sounds" to
                                    "Play audio effects for incoming and outgoing chat messages."
                            },
                            onCheckedChange = {
                                viewModel.updateGlobalStealth { c -> c.copy(chatSoundsEnabled = it) }
                            }
                        )
                        ExpressiveSegmentedActionRow(
                            icon = if (isSyncNotifHidden) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                            title = if (isSyncNotifHidden) "Persistent Sync Banner" else "Hide Persistent Sync Banner",
                            subtitle = if (isSyncNotifHidden) {
                                "Hidden · 24/7 sync & alerts active"
                            } else {
                                "Hide constant notification without breaking sync"
                            },
                            badgeText = if (isSyncNotifHidden) "HIDDEN" else "1-TAP GUIDE",
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onClick = {
                                if (isSyncNotifHidden) {
                                    WaRealtimeService.openNotificationChannelSettings(
                                        context = context,
                                        channelId = WaRealtimeService.CHANNEL_ID
                                    )
                                } else {
                                    showHideNotifGuide = true
                                }
                            }
                        )
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.BatteryChargingFull,
                            title = "Unrestricted Battery (24/7 Sync)",
                            subtitle = if (isBatteryUnrestricted) {
                                "Allowed · Android won't kill background sync"
                            } else {
                                "Allow unrestricted background use"
                            },
                            badgeText = if (isBatteryUnrestricted) "ACTIVE" else "ALLOW",
                            position = ExpressiveSegmentPosition.BOTTOM,
                            onClick = {
                                WaRealtimeService.openBatteryOptimizationSettings(context)
                            }
                        )
                    }
                }
            }

            if (showUpdatesSection) {
                item(key = "patch_section_updates_support") {
                    val updateSubtitle = when (val st = updateState) {
                        is UpdateUiState.Checking -> "Checking GitHub Releases..."
                        is UpdateUiState.UpToDate -> "Up to date (v${st.currentVersion})"
                        is UpdateUiState.UpdateAvailable -> "New update available: ${st.release.tagName}"
                        is UpdateUiState.Downloading -> {
                            if (st.progressPercent >= 0) "Downloading ${st.release.tagName} (${st.progressPercent}%)"
                            else "Downloading ${st.release.tagName}..."
                        }
                        is UpdateUiState.ReadyToInstall -> "${st.release.tagName} ready — tap to install"
                        is UpdateUiState.Error -> if (!st.silent) st.message else "Installed v${BuildConfig.VERSION_NAME}"
                        UpdateUiState.Idle -> "Installed v${BuildConfig.VERSION_NAME}"
                    }
                    val updateBadge = when (updateState) {
                        is UpdateUiState.UpdateAvailable -> "NEW"
                        is UpdateUiState.ReadyToInstall -> "INSTALL"
                        else -> null
                    }

                    ExpressiveSegmentedGroup(title = "Updates & GitHub Support") {
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.SystemUpdate,
                            title = "Check for Updates",
                            subtitle = updateSubtitle,
                            badgeText = updateBadge,
                            position = ExpressiveSegmentPosition.TOP,
                            onClick = {
                                when (updateState) {
                                    is UpdateUiState.UpdateAvailable,
                                    is UpdateUiState.Downloading,
                                    is UpdateUiState.ReadyToInstall -> viewModel.openUpdatePrompt()
                                    else -> viewModel.checkForUpdates(silent = false)
                                }
                            }
                        )
                        ExpressiveSegmentedSwitchRow(
                            icon = Icons.Rounded.SystemUpdate,
                            title = "Receive Pre-release Updates",
                            checked = includePreReleases,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onInfoClick = {
                                infoDialogContent = "Receive Pre-release Updates" to
                                    "Tester channel — fetches GitHub pre-releases before public rollout."
                            },
                            onCheckedChange = { viewModel.setIncludePreReleases(it) }
                        )
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.BugReport,
                            title = "Report a Bug / Issue",
                            subtitle = "Open a pre-filled bug report on GitHub",
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onClick = { onOpenFeedback(GitHubFeedbackType.BUG) }
                        )
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.Lightbulb,
                            title = "Request a Feature",
                            subtitle = "Suggest improvements on GitHub",
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onClick = { onOpenFeedback(GitHubFeedbackType.FEATURE) }
                        )
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.ChatBubbleOutline,
                            title = "Give Feedback",
                            subtitle = "Share thoughts on GitHub",
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onClick = { onOpenFeedback(GitHubFeedbackType.FEEDBACK) }
                        )
                        val hasPendingCrash = AppLogManager.hasCrashLog(context)
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.BugReport,
                            title = "Diagnostics & Crash Logs",
                            subtitle = if (hasPendingCrash) "Crash report available — save or share with developers" else "Save or share app and runtime log files",
                            badgeText = if (hasPendingCrash) "Crash Log" else null,
                            position = ExpressiveSegmentPosition.MIDDLE,
                            onClick = { showDiagnosticsLogsDialog = true }
                        )
                        ExpressiveSegmentedActionRow(
                            icon = Icons.Rounded.Code,
                            title = "GitHub Repository",
                            subtitle = "${BuildConfig.GITHUB_REPO} • v${BuildConfig.VERSION_NAME}",
                            position = ExpressiveSegmentPosition.BOTTOM,
                            onClick = {
                                val repo = BuildConfig.GITHUB_REPO.trim().trim('/')
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$repo"))
                                    )
                                }
                            }
                        )
                    }
                }
            }

            item(key = "patch_bottom_spacer") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Wupp · v${BuildConfig.VERSION_NAME}",
                        color = colors.textSecondary.copy(alpha = 0.6f),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    infoDialogContent?.let { (infoTitle, infoBody) ->
        AlertDialog(
            onDismissRequest = { infoDialogContent = null },
            containerColor = colors.elevatedBar,
            title = {
                Text(
                    text = infoTitle,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = infoBody,
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { infoDialogContent = null }) {
                    Text("Got it", color = colors.accentGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    editingAutoDlNetwork?.let { net ->
        val mask = when (net) {
            AutoDownloadNetwork.MOBILE -> stealth.autoDownloadMobileMask
            AutoDownloadNetwork.WIFI -> stealth.autoDownloadWifiMask
            AutoDownloadNetwork.ROAMING -> stealth.autoDownloadRoamingMask
        }
        AutoDownloadNetworkDialog(
            network = net,
            initialMask = mask,
            onDismiss = { editingAutoDlNetwork = null },
            onConfirmMask = { updatedMask ->
                viewModel.setAutoDownloadMask(net, updatedMask)
            }
        )
    }

    if (showGalleryContactsDialog) {
        GalleryVisibilityContactsDialog(
            chats = allChats,
            hiddenJids = stealth.hiddenGalleryJids,
            onToggleHideContact = { jid, hide ->
                viewModel.setChatMediaVisibility(jid, if (hide) false else null)
            },
            onDismiss = { showGalleryContactsDialog = false }
        )
    }

    if (showProfileEditorDialog) {
        ProfileEditorDialog(
            initialProfile = ownProfile,
            onSaveName = { viewModel.saveOwnPushName(it) },
            onSaveAbout = { viewModel.saveOwnAbout(it) },
            onDismiss = {
                showProfileEditorDialog = false
                tabScope.launch { viewModel.fetchOwnProfile().onSuccess { ownProfile = it } }
            }
        )
    }

    if (showAccountPrivacyDialog) {
        WhatsAppAccountPrivacyDialog(
            initialTimerSec = stealth.defaultDisappearingSec,
            onLoadPrivacy = { viewModel.fetchAccountPrivacy() },
            onUpdateCategory = { cat, valStr -> viewModel.setAccountPrivacy(cat, valStr) },
            onUpdateDefaultTimer = { sec -> viewModel.setDefaultDisappearingTimer(sec) },
            onOpenBlockedContacts = { showBlockedContactsDialog = true },
            onDismiss = { showAccountPrivacyDialog = false }
        )
    }

    if (showBlockedContactsDialog) {
        BlockedContactsDialog(
            dmCandidates = allChats,
            onLoadBlocked = { viewModel.fetchBlockedContacts() },
            onSetBlocked = { jid, blk -> viewModel.setContactBlocked(jid, blk) },
            onDismiss = { showBlockedContactsDialog = false }
        )
    }

    if (showStorageUsageDialog) {
        StorageUsageDialog(
            onLoadStats = { viewModel.loadStorageStats() },
            onClearTempCache = { viewModel.clearTempCache() },
            onDismiss = { showStorageUsageDialog = false }
        )
    }

    if (showFontSizeDialog) {
        ChatFontSizeDialog(
            currentScale = stealth.chatFontScale,
            onSelectScale = { scale -> viewModel.updateGlobalStealth { c -> c.copy(chatFontScale = scale) } },
            onDismiss = { showFontSizeDialog = false }
        )
    }

    if (showAntiCallDialog) {
        val colors = LocalWaColors.current
        AlertDialog(
            onDismissRequest = { showAntiCallDialog = false },
            containerColor = colors.elevatedBar,
            title = {
                Text(
                    text = "Anti-Call Protection",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AntiCallMode.entries.forEach { mode ->
                        val selected = stealth.antiCallMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setAntiCallMode(mode)
                                    showAntiCallDialog = false
                                }
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    viewModel.setAntiCallMode(mode)
                                    showAntiCallDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.brightGreen)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.title,
                                    color = colors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text(
                                    text = mode.subtitle,
                                    color = colors.textSecondary,
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAntiCallDialog = false }) {
                    Text("Close", color = colors.brightGreen)
                }
            }
        )
    }

    if (showCompanionScopeDialog) {
        CompanionScopeInfoDialog(onDismiss = { showCompanionScopeDialog = false })
    }

    if (showDiagnosticsLogsDialog) {
        DiagnosticsLogsDialog(
            onDismiss = { showDiagnosticsLogsDialog = false }
        )
    }

    if (showAccountSwitcherSheet) {
        AccountSwitcherSheet(
            accounts = accounts,
            activeAccountId = activeAccountId,
            isSwitching = isSwitchingAccount,
            onSelectAccount = { viewModel.switchAccount(it) },
            onAddAccount = { viewModel.addAndSwitchAccount(it) },
            onRenameAccount = { id, label -> viewModel.renameAccount(id, label) },
            onRemoveAccount = { viewModel.removeInactiveAccount(it) },
            onDismiss = { showAccountSwitcherSheet = false }
        )
    }

    if (showWallpaperDialog) {
        WallpaperPickerDialog(
            currentUri = stealth.chatWallpaperUri,
            currentDim = stealth.chatWallpaperDim,
            showDoodleOnPresets = stealth.chatWallpaperShowDoodle,
            currentScale = stealth.chatWallpaperScale,
            currentOffsetX = stealth.chatWallpaperOffsetX,
            currentOffsetY = stealth.chatWallpaperOffsetY,
            onSelectUri = { viewModel.setChatWallpaper(it) },
            onPickCustomImage = { viewModel.importChatWallpaperFromUri(it) },
            onDimChange = { viewModel.setChatWallpaperDim(it) },
            onToggleDoodle = { viewModel.setChatWallpaperShowDoodle(it) },
            onPositionChange = { scale, ox, oy -> viewModel.setChatWallpaperPosition(scale, ox, oy) },
            onDismiss = { showWallpaperDialog = false }
        )
    }

    if (showReactionDialog) {
        ReactionEmojiCustomizerDialog(
            currentEmojis = stealth.quickReactionEmojis,
            onSave = { viewModel.setQuickReactionEmojis(it) },
            onReset = { viewModel.resetQuickReactionEmojis() },
            onDismiss = { showReactionDialog = false }
        )
    }

    if (showAppLockDialog) {
        AppLockConfigDialog(
            config = appLockConfig,
            onDismiss = { showAppLockDialog = false }
        )
    }

    if (showLanguageDialog) {
        AppLanguageDialog(
            currentLanguage = stealth.appLanguage,
            onSelectLanguage = { chosen ->
                val changed = chosen != stealth.appLanguage
                viewModel.setAppLanguage(chosen)
                if (changed) {
                    context.findFragmentActivity()?.recreate()
                }
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showClockFormatDialog) {
        val colors = LocalWaColors.current
        AlertDialog(
            onDismissRequest = { showClockFormatDialog = false },
            containerColor = colors.elevatedBar,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textPrimary,
            title = {
                Text(
                    text = "Time format",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WuppClockFormat.entries.forEach { option ->
                        val selected = option == stealth.clockFormat
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) colors.pillActiveBg else Color.Transparent)
                                .clickable {
                                    viewModel.updateGlobalStealth { c -> c.copy(clockFormat = option) }
                                    showClockFormatDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.label,
                                    color = if (selected) colors.pillActiveText else colors.textPrimary,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 14.5.sp
                                )
                                Text(
                                    text = option.subtitle,
                                    color = colors.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = colors.accentGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showClockFormatDialog = false }) {
                    Text("Close", color = colors.accentGreen)
                }
            }
        )
    }

    if (showCustomizeNavBarDialog) {
        val colors = LocalWaColors.current
        val navAppearance by HomeAppearanceManager.config.collectAsState()
        val navItems = buildList {
            add(0 to stringResource(R.string.nav_chats))
            add(4 to stringResource(R.string.nav_groups))
            add(1 to stringResource(R.string.nav_updates))
            if (stealth.separateChannelsTab && !stealth.hideChannels) {
                add(6 to stringResource(R.string.nav_channels))
            }
            add(2 to stringResource(R.string.nav_communities))
            add(5 to stringResource(R.string.nav_calls))
            add(3 to "You")
        }
        val visibleOrder = resolveHomeTabOrder(
            separateGroupsAndChats = stealth.separateGroupsAndChats,
            separateChannelsTab = stealth.separateChannelsTab && !stealth.hideChannels,
            hiddenNavTabs = navAppearance.hiddenNavTabs
        )
        AlertDialog(
            onDismissRequest = { showCustomizeNavBarDialog = false },
            containerColor = colors.elevatedBar,
            title = { Text("Customize Navigation Bar", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Choose which tabs appear on the bottom navigation bar. At least one tab must stay visible.",
                        color = colors.textSecondary,
                        fontSize = 12.5.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    navItems.forEach { (tabId, label) ->
                        val isVisible = if (tabId == 4) {
                            stealth.separateGroupsAndChats && (4 !in navAppearance.hiddenNavTabs)
                        } else {
                            tabId !in navAppearance.hiddenNavTabs
                        }
                        val isLastVisible = isVisible && visibleOrder.size <= 1
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, color = colors.textPrimary, fontSize = 14.5.sp)
                            Switch(
                                checked = isVisible,
                                enabled = !isLastVisible,
                                onCheckedChange = { checked ->
                                    if (tabId == 4) {
                                        viewModel.updateGlobalStealth { c -> c.copy(separateGroupsAndChats = checked) }
                                        HomeAppearanceManager.update { cfg ->
                                            cfg.copy(
                                                hiddenNavTabs = if (checked) cfg.hiddenNavTabs - 4 else cfg.hiddenNavTabs + 4
                                            )
                                        }
                                    } else {
                                        HomeAppearanceManager.update { cfg ->
                                            cfg.copy(
                                                hiddenNavTabs = if (checked) cfg.hiddenNavTabs - tabId else cfg.hiddenNavTabs + tabId
                                            )
                                        }
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = colors.accentGreen,
                                    checkedThumbColor = Color.White
                                )
                            )
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        HomeAppearanceManager.update { cfg -> cfg.copy(hiddenNavTabs = emptySet()) }
                    }
                ) {
                    Text("Restore Default", color = colors.textSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomizeNavBarDialog = false }) {
                    Text("Done", color = colors.accentGreen)
                }
            }
        )
    }

    if (showHideNotifGuide) {
        val colors = LocalWaColors.current
        AlertDialog(
            onDismissRequest = { showHideNotifGuide = false },
            containerColor = colors.elevatedBar,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsOff,
                        contentDescription = null,
                        tint = colors.accentGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hide Constant Banner",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Android requires a background service for 24/7 WhatsApp sync and doesn't let apps mute that status notification automatically.",
                        color = colors.textSecondary,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.appSurface)
                            .border(0.8.dp, colors.accentGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "On the next screen (1 tap):",
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Switch \"Show notifications\" OFF at the top of \"Wupp Realtime Sync\".",
                            color = colors.accentGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 18.sp
                        )
                    }
                    Text(
                        text = "• Real-time sync stays 100% active\n• Chat message alerts & sounds keep working",
                        color = colors.textSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showHideNotifGuide = false
                        WaRealtimeService.openNotificationChannelSettings(
                            context = context,
                            channelId = WaRealtimeService.CHANNEL_ID
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentGreen,
                        contentColor = colors.accentGreen.contrastingContentColor()
                    )
                ) {
                    Text("Open Setting", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHideNotifGuide = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    if (showCallingSetupDialog) {
        CallingSetupDialog(
            ownJid = bridgeStatus.ownJid,
            stealth = stealth,
            onDismiss = { showCallingSetupDialog = false }
        )
    }
}

@Composable
private fun NewChatPickerDialog(
    ownJid: String = "",
    chats: List<ChatEntity>,
    contacts: List<ContactEntity>,
    onDismiss: () -> Unit,
    onSelectChat: (String) -> Unit,
    onOpenLinkedDevices: () -> Unit,
    onOpenQrScanner: () -> Unit = {}
) {
    val colors = LocalWaColors.current
    val pickerContext = LocalContext.current
    var query by remember { mutableStateOf("") }
    val selfChatJid = remember(ownJid) { StealthMatrixManager.canonicalSelfChatJid(ownJid) }
    val selfChatEntry = remember(selfChatJid, ownJid, chats, contacts) {
        if (selfChatJid.isBlank()) null
        else {
            val existingChat = chats.firstOrNull { StealthMatrixManager.isSelfChatJid(it.jid, ownJid) }
            val existingContact = contacts.firstOrNull { StealthMatrixManager.isSelfChatJid(it.jid, ownJid) }
            val resolvedName = existingChat?.name?.takeIf { it.isNotBlank() }
                ?: existingContact?.name?.takeIf { it.isNotBlank() }
                ?: "+${selfChatJid.substringBefore("@")}"
            val formattedTitle = if (resolvedName.contains("(You)", ignoreCase = true)) {
                resolvedName
            } else {
                "$resolvedName (You)"
            }
            ChatEntity(
                jid = existingChat?.jid?.takeIf { it.isNotBlank() } ?: selfChatJid,
                name = formattedTitle,
                avatarPath = existingChat?.avatarPath?.takeIf { it.isNotBlank() } ?: existingContact?.avatarPath,
                aboutText = "Message yourself"
            )
        }
    }
    val mergedContacts = remember(chats, contacts, ownJid) {
        val byJid = LinkedHashMap<String, ChatEntity>(contacts.size + chats.size)
        for (c in contacts) {
            if (c.jid.endsWith("@g.us") || c.jid.endsWith("@newsletter") || c.jid == "status@broadcast" || c.jid.endsWith("@broadcast")) continue
            if (StealthMatrixManager.isSelfChatJid(c.jid, ownJid)) continue
            byJid[c.jid] = ChatEntity(
                jid = c.jid,
                name = c.name,
                avatarPath = c.avatarPath,
                aboutText = c.aboutText,
                isFavorite = c.isSaved
            )
        }
        for (ch in chats) {
            if (ch.isGroup || ch.isCommunityParent || ch.jid.endsWith("@newsletter") || ch.jid == "status@broadcast" || ch.jid.endsWith("@broadcast")) continue
            if (StealthMatrixManager.isSelfChatJid(ch.jid, ownJid)) continue
            val existing = byJid[ch.jid]
            if (existing == null) {
                byJid[ch.jid] = ch
            } else {
                val bare = ch.jid.substringBefore("@")
                val existingReal = existing.name.isNotBlank() && existing.name != bare && existing.name != ch.jid && !existing.name.startsWith("+")
                val chReal = ch.name.isNotBlank() && ch.name != bare && ch.name != ch.jid && !ch.name.startsWith("+")
                byJid[ch.jid] = existing.copy(
                    name = when {
                        existingReal && !chReal -> existing.name
                        chReal -> ch.name
                        else -> ch.name.ifBlank { existing.name }
                    },
                    avatarPath = ch.avatarPath?.takeIf { it.isNotBlank() } ?: existing.avatarPath,
                    aboutText = ch.aboutText.ifBlank { existing.aboutText },
                    lastMessageTimestamp = maxOf(existing.lastMessageTimestamp, ch.lastMessageTimestamp)
                )
            }
        }
        byJid.values.sortedWith(
            compareByDescending<ChatEntity> { it.isFavorite }
                .thenByDescending { it.lastMessageTimestamp > 0L }
                .thenBy { it.name.lowercase() }
        )
    }
    val digitsQuery = remember(query) { query.filter { it.isDigit() } }
    val filtered = remember(mergedContacts, query) {
        val q = query.trim().lowercase()
        val qDigits = q.filter { it.isDigit() }
        if (q.isEmpty()) mergedContacts.take(60)
        else mergedContacts.filter {
            it.name.lowercase().contains(q) ||
                it.jid.lowercase().contains(q) ||
                (qDigits.length >= 3 && it.jid.substringBefore("@").contains(qDigits))
        }.take(60)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        title = { Text("Select contact", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search name or number...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val showSelfEntry = remember(selfChatEntry, query) {
                    val entry = selfChatEntry ?: return@remember false
                    val q = query.trim().lowercase()
                    q.isEmpty() ||
                        "message yourself".contains(q) ||
                        "you".contains(q) ||
                        entry.name.lowercase().contains(q) ||
                        entry.jid.lowercase().contains(q)
                }
                LazyColumn(modifier = Modifier.height(260.dp)) {
                    item(key = "new_contact_qr_row") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val insertIntent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                                        type = ContactsContract.RawContacts.CONTENT_TYPE
                                    }
                                    runCatching { pickerContext.startActivity(insertIntent) }.onFailure {
                                        Toast.makeText(pickerContext, "No Contacts app available", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.accentGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PersonAdd,
                                    contentDescription = "New contact",
                                    tint = colors.accentGreen.contrastingContentColor(),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "New contact",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable { onOpenQrScanner() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QrCode2,
                                    contentDescription = "Scan or show QR code",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                    if (showSelfEntry && selfChatEntry != null) {
                        item(key = "self_${selfChatEntry.jid}") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectChat(selfChatEntry.jid) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                WhatsAppAvatar(
                                    jid = selfChatEntry.jid,
                                    avatarPath = selfChatEntry.avatarPath,
                                    isGroup = false,
                                    size = 36.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selfChatEntry.name,
                                        color = colors.accentGreen,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = selfChatEntry.aboutText,
                                        color = colors.textSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    if (digitsQuery.length >= 6 &&
                        !StealthMatrixManager.isSelfChatJid("$digitsQuery@s.whatsapp.net", ownJid) &&
                        filtered.none { it.jid.substringBefore("@") == digitsQuery }
                    ) {
                        val directJid = "$digitsQuery@s.whatsapp.net"
                        item(key = "direct_$directJid") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectChat(directJid) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(colors.accentGreen.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = colors.accentGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Start chat with +$digitsQuery",
                                    color = colors.accentGreen,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    items(filtered, key = { it.jid }) { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectChat(c.jid) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WhatsAppAvatar(
                                jid = c.jid,
                                avatarPath = c.avatarPath,
                                isGroup = false,
                                size = 36.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = c.name.ifBlank { c.jid.substringBefore("@") },
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (c.aboutText.isNotBlank()) {
                                    Text(
                                        text = c.aboutText,
                                        color = colors.textSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenLinkedDevices) {
                Text("Linked devices", color = colors.accentGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun LinkCompanionDialog(
    bridgeStatus: com.wa.beastclient.core.service.BridgeStatus,
    onDismiss: () -> Unit,
    onPairPhone: (String, (String) -> Unit) -> Unit,
    onReconnect: () -> Unit
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    var phoneInput by remember { mutableStateOf("") }
    var generatedCode by remember(bridgeStatus.latestPairCode) { mutableStateOf(bridgeStatus.latestPairCode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.wupp_logo),
                    contentDescription = "Wupp",
                    modifier = Modifier.size(28.dp)
                )
                Text("Link Companion Device", color = colors.textPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Engine: ${bridgeStatus.connectionState}" +
                        if (bridgeStatus.ownJid.isNotBlank()) " (${bridgeStatus.ownJid})" else "",
                    color = colors.accentGreen,
                    fontFamily = mono,
                    fontSize = 12.sp
                )
                Text(
                    text = "Enter your full phone number with country code (digits only, e.g. 15551234567) to request an 8-character Wupp companion link code:",
                    color = colors.textSecondary,
                    fontSize = 12.5.sp
                )
                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it },
                    label = { Text("Phone number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!generatedCode.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.pillActiveBg, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Pair Code: $generatedCode",
                            fontFamily = mono,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.pillActiveText
                        )
                    }
                }
                if (!bridgeStatus.latestQrCode.isNullOrBlank()) {
                    Text(
                        text = "QR Token ready (${bridgeStatus.latestQrCode.take(28)}...)",
                        fontFamily = mono,
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPairPhone(phoneInput) { generatedCode = it } },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen)
            ) {
                Text("Get Pair Code", color = Color.White)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReconnect) { Text("Reconnect") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
private fun VaultMediaPreviewDialog(
    title: String,
    subtitle: String,
    localPath: String?,
    onDismiss: () -> Unit,
    onExport: () -> Unit
) {
    val colors = LocalWaColors.current
    val bmp = remember(localPath) {
        localPath?.let { path ->
            File(path).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        title = { Text(title, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = colors.textSecondary, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onExport,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen)
            ) {
                Text("Save to Gallery", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

fun formatFullDateTime(
    epochMs: Long,
    use24Hour: Boolean = StealthMatrixManager.defaultUse24Hour()
): String {
    if (epochMs <= 0L) return ""
    val pattern = if (use24Hour) "MMM d · HH:mm" else "MMM d · h:mm a"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMs))
}

internal enum class TopBarStatusTone {
    LIVE,
    SYNCING,
    OFFLINE,
    IDLE
}

internal data class TopBarConnectionBadge(
    val label: String,
    val tone: TopBarStatusTone
)

internal fun resolveTopBarConnectionBadge(
    connectionState: String,
    offlineMode: Boolean
): TopBarConnectionBadge {
    if (offlineMode || connectionState == "OFFLINE_PAUSED") {
        return TopBarConnectionBadge(label = "Offline", tone = TopBarStatusTone.OFFLINE)
    }
    return when (connectionState) {
        "CONNECTED" -> TopBarConnectionBadge(label = "Live", tone = TopBarStatusTone.LIVE)
        "SYNCING" -> TopBarConnectionBadge(label = "Syncing", tone = TopBarStatusTone.SYNCING)
        "CONNECTING", "PAIRING" -> TopBarConnectionBadge(label = "Linking", tone = TopBarStatusTone.SYNCING)
        else -> TopBarConnectionBadge(label = "Idle", tone = TopBarStatusTone.IDLE)
    }
}
