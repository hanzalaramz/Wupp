package com.wa.beastclient.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddReaction
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallMissed
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.wa.beastclient.R
import com.wa.beastclient.core.calling.CallStateManager
import com.wa.beastclient.core.calling.extractDialablePhoneDigits
import com.wa.beastclient.core.calling.isColdCallBlocked
import com.wa.beastclient.core.service.WaRealtimeService
import com.wa.beastclient.data.db.ChatEntity
import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.data.db.MessageReactionEntity
import com.wa.beastclient.data.media.OutboundQualityMode
import com.wa.beastclient.domain.contacts.DeviceContactFallbackResolver
import com.wa.beastclient.domain.security.AppLockManager
import com.wa.beastclient.domain.stealth.CallBackendMode
import com.wa.beastclient.domain.stealth.StealthMatrixManager
import com.wa.beastclient.ui.MainViewModel
import com.wa.beastclient.ui.QuotedSenderMeta
import com.wa.beastclient.ui.ResolvedParticipantUi
import com.wa.beastclient.ui.theme.contrastingContentColor
import com.wa.beastclient.ui.calling.ActiveCallFloatingBar
import com.wa.beastclient.ui.calling.CallingSetupDialog
import com.wa.beastclient.ui.components.AnimatedStickerImage
import com.wa.beastclient.ui.components.ChatEmojiPickerDock
import com.wa.beastclient.ui.components.ChatMediaVisibilitySelector
import com.wa.beastclient.ui.components.DeletedTimestampBadge
import com.wa.beastclient.ui.components.DocumentMessageCard
import com.wa.beastclient.ui.components.DoodleBackground
import com.wa.beastclient.ui.components.ForwardMessageDialog
import com.wa.beastclient.ui.components.FullScreenMediaViewer
import com.wa.beastclient.ui.components.InAppPdfViewerDialog
import com.wa.beastclient.ui.components.InlineTimestampMessage
import com.wa.beastclient.ui.components.LocationMessageCard
import com.wa.beastclient.ui.components.MessageHistorySheet
import com.wa.beastclient.ui.components.ShareLocationSheet
import com.wa.beastclient.ui.components.openLocationInMaps
import com.wa.beastclient.ui.components.parseLocationPayload
import com.wa.beastclient.ui.components.MessageMetaCluster
import com.wa.beastclient.ui.components.OutboundMediaPreviewDialog
import com.wa.beastclient.ui.components.PerChatQuickControlsSheet
import com.wa.beastclient.ui.components.PinDurationDialog
import com.wa.beastclient.ui.components.PinnedMessageBanner
import com.wa.beastclient.ui.components.ProfilePhotoLightbox
import com.wa.beastclient.ui.components.StagedOutboundMedia
import com.wa.beastclient.ui.components.StickerStudioDialog
import com.wa.beastclient.ui.components.SwipeToReplyBox
import com.wa.beastclient.ui.components.VoiceNoteBubble
import com.wa.beastclient.ui.components.WallpaperPickerDialog
import com.wa.beastclient.ui.components.WhatsAppBubbleShape
import com.wa.beastclient.ui.components.WuppGlassBottomBar
import com.wa.beastclient.ui.components.WuppGlassCircleButton
import com.wa.beastclient.ui.components.WuppGlassContainer
import com.wa.beastclient.ui.components.WuppGlassTopBar
import com.wa.beastclient.ui.components.wuppBackdropSource
import com.wa.beastclient.ui.components.whatsAppClusterInnerShape
import com.wa.beastclient.ui.components.buildDocumentExportFileName
import com.wa.beastclient.ui.components.deleteBeforeSelection
import com.wa.beastclient.ui.components.expressivePressScale
import com.wa.beastclient.ui.components.resolveMentionsInText
import com.wa.beastclient.domain.scheduling.ScheduledMessage
import com.wa.beastclient.ui.components.ScheduleMessageDialog
import com.wa.beastclient.ui.components.ScheduledMessagesSheet
import com.wa.beastclient.ui.components.formatMessageClock
import com.wa.beastclient.ui.components.insertTextAtSelection
import com.wa.beastclient.ui.components.isDocumentMessage
import com.wa.beastclient.ui.components.openDocumentNatively
import com.wa.beastclient.ui.components.rememberWuppBackdropState
import com.wa.beastclient.ui.components.resolveDocumentDisplayName
import com.wa.beastclient.ui.components.resolveDocumentMimeType
import com.wa.beastclient.ui.components.ContactMessageCard
import com.wa.beastclient.ui.components.ContactSharingHelper
import com.wa.beastclient.ui.components.InAppContactPickerSheet
import com.wa.beastclient.ui.components.MessageInfoSheet
import com.wa.beastclient.ui.components.SendContactPreviewDialog
import com.wa.beastclient.ui.components.SharedContactPreview
import com.wa.beastclient.ui.components.isContactMessage
import com.wa.beastclient.ui.theme.LocalWaColors
import com.wa.beastclient.ui.theme.LocalWaMonoFamily
import com.wa.beastclient.ui.theme.LocalWuppBubbleRadiusDp
import com.wa.beastclient.ui.theme.LocalWuppShowSeconds
import com.wa.beastclient.ui.theme.LocalWuppUse24Hour
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun ChatScreen(
    chatJid: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenContactInfo: (String) -> Unit = {},
    onNavigateToChat: (String) -> Unit = {}
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    val showSeconds = LocalWuppShowSeconds.current
    val use24Hour = LocalWuppUse24Hour.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val isAppLocked by viewModel.isAppLocked.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(chatJid, lifecycleOwner, isAppLocked) {
        fun syncActiveVisibility() {
            val isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (isResumed && !isAppLocked) {
                viewModel.setActiveChat(chatJid)
            } else {
                viewModel.setActiveChat(null)
            }
        }
        syncActiveVisibility()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (!isAppLocked) viewModel.setActiveChat(chatJid)
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> viewModel.setActiveChat(null)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setActiveChat(null)
        }
    }

    val chat by remember(chatJid) { viewModel.observeChat(chatJid) }.collectAsState(initial = null)
    val contact by remember(chatJid) { viewModel.observeContact(chatJid) }.collectAsState(initial = null)
    val pinnedMessages by remember(chatJid) { viewModel.observePinnedMessages(chatJid) }.collectAsState(initial = emptyList())
    var pinDurationDialogTarget by remember(chatJid) { mutableStateOf<MessageEntity?>(null) }
    val isChannel = chatJid.endsWith("@newsletter")
    val isGroupAnnounceLocked by remember(chatJid) { viewModel.isGroupAnnounceLocked(chatJid) }.collectAsState(initial = false)
    val effectivePrivacy by remember(chatJid) { viewModel.observeEffectivePrivacy(chatJid) }.collectAsState(initial = null)
    val stealthConfig by viewModel.stealthConfig.collectAsState()
    val effectiveWallpaper = remember(chatJid, stealthConfig) {
        StealthMatrixManager.resolveChatWallpaper(chatJid, cfg = stealthConfig)
    }
    val hasChatWallpaperOverride = remember(chatJid, stealthConfig) {
        StealthMatrixManager.hasChatWallpaperOverride(chatJid, cfg = stealthConfig)
    }
    val isKnownChatOrContact by remember(chatJid) { viewModel.canInitiateOutbound(chatJid) }.collectAsState(initial = true)
    var coldStartConfirmed by remember(chatJid) { mutableStateOf(false) }
    val coldStartWarningActive = !isChannel && !isGroupAnnounceLocked && !isKnownChatOrContact && !coldStartConfirmed
    val canSendOutbound = true
    val allowUnsavedColdStartFlag = !isKnownChatOrContact || coldStartConfirmed
    var pendingGhostGuardAction by remember(chatJid) { mutableStateOf<(() -> Unit)?>(null) }
    var pendingColdStartAction by remember(chatJid) { mutableStateOf<(() -> Unit)?>(null) }
    val runWithGhostGuardOnly: (() -> Unit) -> Unit = { action ->
        if (stealthConfig.ghostPresence && stealthConfig.ghostOutboundGuardPrompt) {
            pendingGhostGuardAction = action
        } else {
            action()
        }
    }
    val runWithOutboundGuard: (() -> Unit) -> Unit = { action ->
        val afterGhost: () -> Unit = {
            if (coldStartWarningActive) {
                pendingColdStartAction = action
            } else {
                action()
            }
        }
        runWithGhostGuardOnly(afterGhost)
    }
    val isRecordingVoice by viewModel.isRecordingVoice.collectAsState()
    val isVoiceRecordingPaused by viewModel.isVoiceRecordingPaused.collectAsState()
    val bridgeStatus by viewModel.bridgeStatus.collectAsState()
    val downloadingMediaIds by viewModel.downloadingMediaIds.collectAsState()
    val failedMediaIds by viewModel.failedMediaIds.collectAsState()
    val chatReactionCounts by remember(chatJid) { viewModel.observeChatReactionCounts(chatJid) }
        .collectAsState(initial = emptyMap())
    val pagedItems = remember(chatJid) { viewModel.pagedMessages(chatJid) }.collectAsLazyPagingItems()
    var showCallingSetupDialog by remember(chatJid) { mutableStateOf(false) }
    var pendingSetupOfficialFallbackDigits by remember(chatJid) { mutableStateOf("") }
    var pendingColdCallVideo by remember(chatJid) { mutableStateOf<Boolean?>(null) }
    var pendingPermissionCallIsVideo by remember(chatJid) { mutableStateOf<Boolean?>(null) }
    var pendingParticipantCall by remember(chatJid) { mutableStateOf<Pair<ParticipantPopupTarget, Boolean>?>(null) }

    val resolvePhoneDigitsForJid: suspend (String) -> String = { targetJid ->
        withContext(Dispatchers.IO) {
            val direct = extractDialablePhoneDigits(targetJid)
            if (direct.isNotEmpty()) {
                direct
            } else {
                val rawJson = WaRealtimeService.resolveParticipantNamesJson(targetJid)
                val entry = runCatching { JSONObject(rawJson).optJSONObject(targetJid) }.getOrNull()
                val altJid = entry?.optString("altJid").orEmpty()
                val resolvedPhone = entry?.optString("phone").orEmpty()
                extractDialablePhoneDigits(targetJid, altJid, resolvedPhone)
            }
        }
    }

    val resolvePhoneDigitsForChat: suspend () -> String = { resolvePhoneDigitsForJid(chatJid) }

    val launchPeerCallFlow: (String, String, String?, Boolean, Boolean) -> Unit =
        { peerJid, peerName, peerAvatarPath, isGroupPeer, isVideo ->
            scope.launch {
                val digits = if (isGroupPeer) "" else resolvePhoneDigitsForJid(peerJid)
                if (stealthConfig.offlineMode) {
                    Toast.makeText(context, "Disable Offline Mode to place calls", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                if (stealthConfig.callBackendMode == CallBackendMode.OFF) {
                    pendingSetupOfficialFallbackDigits = digits
                    showCallingSetupDialog = true
                    return@launch
                }
                if (!isGroupPeer && digits.isEmpty()) {
                    Toast.makeText(context, "Unable to resolve contact phone number for calling", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                CallStateManager.startOutgoingCall(
                    context = context,
                    peerJid = peerJid,
                    peerName = peerName,
                    peerAvatarPath = peerAvatarPath,
                    phoneDigits = digits,
                    isVideo = isVideo,
                    isGroup = isGroupPeer
                )
            }
        }

    val launchOutgoingCallFlow: (Boolean) -> Unit = { isVideo ->
        val displayTitle = chat?.name?.ifBlank { chatJid.substringBefore("@") }
            ?: chatJid.substringBefore("@")
        val isGroup = chatJid.endsWith("@g.us")
        launchPeerCallFlow(chatJid, displayTitle, chat?.avatarPath, isGroup, isVideo)
    }

    val proceedWithGuardsAndCall: (Boolean) -> Unit = { isVideo ->
        val participantCall = pendingParticipantCall
        if (participantCall != null) {
            pendingParticipantCall = null
            runWithGhostGuardOnly {
                launchPeerCallFlow(
                    participantCall.first.jid,
                    participantCall.first.name,
                    participantCall.first.avatarPath,
                    false,
                    participantCall.second
                )
            }
        } else {
            runWithGhostGuardOnly {
                if (isColdCallBlocked(contact?.isSaved == true, pagedItems.itemCount, chatJid.endsWith("@g.us"))) {
                    pendingColdCallVideo = isVideo
                } else {
                    launchOutgoingCallFlow(isVideo)
                }
            }
        }
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val requestedVideo = pendingPermissionCallIsVideo ?: pendingParticipantCall?.second ?: false
        pendingPermissionCallIsVideo = null
        val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camGranted = !requestedVideo || grants[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (micGranted && camGranted) {
            proceedWithGuardsAndCall(requestedVideo)
        } else {
            pendingParticipantCall = null
            Toast.makeText(
                context,
                if (requestedVideo) "Microphone & camera permissions are required for video calls"
                else "Microphone permission is required for voice calls",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val initiateCallFor: (ParticipantPopupTarget?, Boolean) -> Unit = { participantTarget, isVideo ->
        if (stealthConfig.offlineMode) {
            Toast.makeText(context, "Disable Offline Mode to place calls", Toast.LENGTH_SHORT).show()
        } else if (stealthConfig.callBackendMode == CallBackendMode.OFF) {
            scope.launch {
                val targetJid = participantTarget?.jid ?: chatJid
                pendingSetupOfficialFallbackDigits = if (targetJid.endsWith("@g.us")) "" else resolvePhoneDigitsForJid(targetJid)
                showCallingSetupDialog = true
            }
        } else {
            pendingParticipantCall = participantTarget?.let { it to isVideo }
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
            if (missing.isEmpty()) {
                proceedWithGuardsAndCall(isVideo)
            } else {
                pendingPermissionCallIsVideo = isVideo
                callPermissionLauncher.launch(missing.toTypedArray())
            }
        }
    }

    val initiateCall: (Boolean) -> Unit = { isVideo ->
        initiateCallFor(null, isVideo)
    }
    val listState = remember(chatJid) { LazyListState() }
    var scrollToBottomPending by remember(chatJid) { mutableStateOf(false) }
    var highlightedMessageId by remember(chatJid) { mutableStateOf<String?>(null) }
    var activeJumpJob by remember(chatJid) { mutableStateOf<Job?>(null) }
    val quotedNotFoundLabel = stringResource(R.string.chat_quoted_not_found)

    LaunchedEffect(highlightedMessageId) {
        if (highlightedMessageId != null) {
            delay(1500L)
            highlightedMessageId = null
        }
    }

    val jumpToQuotedMessage: (String) -> Unit = { rawQuotedId ->
        val qid = rawQuotedId.trim()
        if (qid.isNotEmpty()) {
            activeJumpJob?.cancel()
            activeJumpJob = scope.launch {
                val immediateIdx = findLoadedQuotedItemIndex(pagedItems.itemSnapshotList, qid)
                if (immediateIdx >= 0) {
                    if (abs(immediateIdx - listState.firstVisibleItemIndex) <= 35) {
                        listState.animateScrollToItem(immediateIdx)
                    } else {
                        listState.scrollToItem(immediateIdx)
                    }
                    highlightedMessageId = qid
                    return@launch
                }

                val existsInChat = viewModel.findMessageExistsInChat(chatJid, qid)
                if (!existsInChat) {
                    Toast.makeText(context, quotedNotFoundLabel, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                var foundIdx = -1
                var step = 0
                while (step < 40) {
                    val prevCount = pagedItems.itemCount
                    if (prevCount > 0) {
                        pagedItems[prevCount - 1]
                    }
                    withTimeoutOrNull(2000L) {
                        snapshotFlow { pagedItems.itemSnapshotList to pagedItems.loadState.append }
                            .first { (snap, appendState) ->
                                findLoadedQuotedItemIndex(snap, qid) >= 0 ||
                                    snap.size > prevCount ||
                                    (appendState is LoadState.NotLoading && appendState.endOfPaginationReached)
                            }
                    }
                    foundIdx = findLoadedQuotedItemIndex(pagedItems.itemSnapshotList, qid)
                    if (foundIdx >= 0) break
                    val appendState = pagedItems.loadState.append
                    if (appendState is LoadState.NotLoading &&
                        appendState.endOfPaginationReached &&
                        pagedItems.itemCount == prevCount
                    ) {
                        break
                    }
                    step++
                }

                if (foundIdx >= 0) {
                    if (abs(foundIdx - listState.firstVisibleItemIndex) <= 35) {
                        listState.animateScrollToItem(foundIdx)
                    } else {
                        listState.scrollToItem(foundIdx)
                    }
                    highlightedMessageId = qid
                } else {
                    Toast.makeText(context, quotedNotFoundLabel, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    var initialUnreadCount by remember(chatJid) { mutableStateOf<Int?>(null) }
    LaunchedEffect(chatJid, chat?.unreadCount) {
        if (initialUnreadCount == null && chat != null) {
            initialUnreadCount = chat?.unreadCount ?: 0
        }
    }
    var lastLatestMsgId by remember(chatJid) { mutableStateOf<String?>(null) }
    var latestVisibleMsg: MessageEntity? = null
    val latestPeekCount = minOf(pagedItems.itemCount, 8)
    for (idx in 0 until latestPeekCount) {
        val candidate = pagedItems.peek(idx)
        if (candidate != null && !isHiddenPlaceholderMessage(candidate)) {
            latestVisibleMsg = candidate
            break
        }
    }
    LaunchedEffect(chatJid, latestVisibleMsg?.id, pagedItems.itemCount) {
        val newest = latestVisibleMsg ?: return@LaunchedEffect
        val prevId = lastLatestMsgId
        lastLatestMsgId = newest.id
        if (prevId == null) {
            val unread = initialUnreadCount ?: chat?.unreadCount ?: 0
            if (unread > 1 && pagedItems.itemCount > 0) {
                val targetIndex = (unread - 1).coerceIn(0, pagedItems.itemCount - 1)
                listState.scrollToItem(targetIndex)
            } else if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                listState.scrollToItem(0)
            }
            scrollToBottomPending = false
        } else if (newest.id != prevId) {
            val nearBottom = listState.firstVisibleItemIndex <= 3
            if (scrollToBottomPending || newest.isFromMe || nearBottom) {
                scrollToBottomPending = false
                listState.animateScrollToItem(0)
            }
        }
    }
    val allChats by viewModel.allChats.collectAsState()
    val unreadChatsCount = remember(allChats, stealthConfig.separateGroupsAndChats, chatJid) {
        allChats.count {
            !it.isCommunityParent &&
                !it.jid.endsWith("@newsletter") &&
                it.jid != chatJid &&
                (!stealthConfig.separateGroupsAndChats || !it.isGroup) &&
                !it.isLocked &&
                it.unreadCount > 0
        }
    }
    val participantMeta by viewModel.participantMeta.collectAsState()
    val quotedMsgMeta by viewModel.quotedMsgMeta.collectAsState()
    val chatsByJid = remember(allChats) { allChats.associateBy { it.jid } }
    val loadedSnapshot = pagedItems.itemSnapshotList.items
    val loadedMessagesById = remember(loadedSnapshot) { loadedSnapshot.associateBy { it.id } }
    val prevOutgoingStatusById = remember(chatJid) { mutableStateMapOf<String, String>() }
    var statusTrackerSeeded by remember(chatJid) { mutableStateOf(false) }
    var lastReadToastAtMs by remember(chatJid) { mutableLongStateOf(0L) }
    var readReceiptBannerTitle by remember(chatJid) { mutableStateOf<String?>(null) }

    LaunchedEffect(chatJid, loadedSnapshot) {
        if (isChannel || loadedSnapshot.isEmpty()) return@LaunchedEffect
        val outgoing = loadedSnapshot.filter { it.isFromMe }
        if (!statusTrackerSeeded) {
            outgoing.forEach { m -> prevOutgoingStatusById[m.id] = m.deliveryStatus }
            statusTrackerSeeded = true
            return@LaunchedEffect
        }
        val nowMs = System.currentTimeMillis()
        var newlyReadMsg: MessageEntity? = null
        outgoing.forEach { m ->
            val prev = prevOutgoingStatusById[m.id]
            val curr = m.deliveryStatus
            if (prev != null &&
                !prev.equals("READ", ignoreCase = true) &&
                !prev.equals("PLAYED", ignoreCase = true) &&
                curr.equals("READ", ignoreCase = true) &&
                (nowMs - m.timestamp) <= 7_200_000L
            ) {
                newlyReadMsg = m
            }
            prevOutgoingStatusById[m.id] = curr
        }
        if (newlyReadMsg != null && (nowMs - lastReadToastAtMs) >= 12_000L) {
            lastReadToastAtMs = nowMs
            val title = chat?.name?.takeIf { it.isNotBlank() } ?: chatJid.substringBefore("@")
            readReceiptBannerTitle = title
        }
    }

    LaunchedEffect(readReceiptBannerTitle) {
        if (readReceiptBannerTitle != null) {
            delay(2600L)
            readReceiptBannerTitle = null
        }
    }

    val senderPushByJid = remember(loadedSnapshot) {
        buildMap {
            for (m in loadedSnapshot) {
                val sJid = m.senderJid.trim()
                val sName = m.senderName.trim()
                if (sJid.isNotBlank() && isMeaningfulDisplayName(sName, sJid) && !containsKey(sJid)) {
                    put(sJid, sName)
                }
            }
        }
    }
    LaunchedEffect(chatJid, pagedItems.itemCount) {
        val snap = pagedItems.itemSnapshotList.items
        val jids = linkedSetOf<String>()
        val quotedIds = linkedSetOf<String>()
        for (m in snap) {
            if (m.senderJid.contains("@")) jids += m.senderJid
            val q = m.quotedSender?.trim().orEmpty()
            if (q.contains("@")) jids += q
            if (m.mentionsCsv.isNotBlank()) {
                m.mentionsCsv.split(',').forEach { mj ->
                    val cleanM = mj.trim()
                    if (cleanM.contains("@")) jids += cleanM
                }
            }
            val qid = m.quotedId?.trim().orEmpty()
            if (qid.isNotBlank() && !loadedMessagesById.containsKey(qid)) {
                quotedIds += qid
            }
        }
        if (jids.isNotEmpty()) viewModel.resolveParticipantsLocally(jids)
        if (quotedIds.isNotEmpty()) viewModel.resolveQuotedSendersLocally(quotedIds)
    }

    val initialDraft = remember(chatJid) { viewModel.getChatDraft(chatJid) }
    var draftValue by rememberSaveable(chatJid, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(
            TextFieldValue(
                text = initialDraft,
                selection = TextRange(initialDraft.length)
            )
        )
    }
    val latestDraftText by androidx.compose.runtime.rememberUpdatedState(draftValue.text)
    LaunchedEffect(chatJid, draftValue.text) {
        viewModel.saveChatDraft(chatJid, draftValue.text)
    }
    DisposableEffect(chatJid, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.saveChatDraft(chatJid, latestDraftText)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.saveChatDraft(chatJid, latestDraftText)
        }
    }
    var replyTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var ghostMentionEveryone by remember { mutableStateOf(false) }
    var qualityMode by remember { mutableStateOf(OutboundQualityMode.HD) }
    var showAttachTray by remember { mutableStateOf(false) }
    var showEmojiPicker by remember(chatJid) { mutableStateOf(false) }
    var holdDockForIme by remember(chatJid) { mutableStateOf(false) }
    val recentStickers by viewModel.recentStickers.collectAsState()
    val favoriteStickers by viewModel.favoriteStickers.collectAsState()
    val favoriteStickerKeys by viewModel.favoriteStickerKeys.collectAsState()
    val cuppyStickers by viewModel.cuppyStickers.collectAsState()
    val customStickers by viewModel.customStickers.collectAsState()
    val deletedHistory by viewModel.deletedMessagesHistory.collectAsState()
    val editedHistory by viewModel.editedMessagesHistory.collectAsState()
    val allEditsHistory by viewModel.allMessageEdits.collectAsState()
    var isDockExpanded by rememberSaveable(chatJid) { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val draftFocusRequester = remember { FocusRequester() }
    val draftInteractionSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val maxLandscapeDockDp = (LocalConfiguration.current.screenHeightDp * 0.48f).dp
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val navBarBottomPx = WindowInsets.navigationBars.getBottom(density)
    val imeExclusivePx = (imeBottomPx - navBarBottomPx).coerceAtLeast(0)
    val imeExclusiveDp = with(density) { imeExclusivePx.toDp() }
    val uiPrefs = remember(context) {
        context.getSharedPreferences("wupp_ui_prefs", Context.MODE_PRIVATE)
    }
    var rememberedKeyboardDp by remember {
        mutableStateOf(uiPrefs.getFloat("cached_ime_height_dp", 296f).coerceIn(220f, 420f).dp)
    }
    LaunchedEffect(showEmojiPicker) {
        if (!showEmojiPicker) {
            isDockExpanded = false
        }
    }

    LaunchedEffect(imeExclusivePx) {
        val minImePx = with(density) { 120.dp.toPx() }
        if (imeExclusivePx > minImePx) {
            val measured = with(density) { imeExclusivePx.toDp().value }.coerceIn(220f, 420f)
            if (abs(measured - rememberedKeyboardDp.value) > 4f) {
                rememberedKeyboardDp = measured.dp
                uiPrefs.edit().putFloat("cached_ime_height_dp", measured).apply()
            }
        }
    }
    LaunchedEffect(holdDockForIme, imeExclusivePx) {
        if (holdDockForIme) {
            if (imeExclusivePx > 0) {
                holdDockForIme = false
            } else {
                delay(320L)
                holdDockForIme = false
            }
        }
    }
    LaunchedEffect(draftInteractionSource) {
        draftInteractionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                if (showAttachTray) {
                    showAttachTray = false
                }
                if (showEmojiPicker) {
                    holdDockForIme = true
                    showEmojiPicker = false
                    keyboardController?.show()
                }
            }
        }
    }
    LaunchedEffect(isRecordingVoice, isGroupAnnounceLocked, canSendOutbound) {
        if (isRecordingVoice || isGroupAnnounceLocked || !canSendOutbound) {
            showAttachTray = false
            showEmojiPicker = false
            holdDockForIme = false
        }
    }
    var lastComposingSentMs by remember(chatJid) { mutableStateOf(0L) }
    LaunchedEffect(
        chatJid,
        draftValue.text,
        isRecordingVoice,
        isVoiceRecordingPaused,
        stealthConfig.offlineMode,
        stealthConfig.ghostPresence,
        stealthConfig.hideTypingRecording,
        effectivePrivacy?.sendChatPresence
    ) {
        val canSendPresence = !stealthConfig.offlineMode &&
            !stealthConfig.ghostPresence &&
            (effectivePrivacy?.sendChatPresence ?: !stealthConfig.hideTypingRecording)
        if (!canSendPresence) {
            lastComposingSentMs = 0L
            return@LaunchedEffect
        }
        if (isRecordingVoice && !isVoiceRecordingPaused) {
            viewModel.sendChatPresence(chatJid, isComposing = true, isAudio = true)
            lastComposingSentMs = System.currentTimeMillis()
        } else if (isRecordingVoice && isVoiceRecordingPaused && lastComposingSentMs > 0L) {
            viewModel.sendChatPresence(chatJid, isComposing = false, isAudio = false)
            lastComposingSentMs = 0L
        } else if (draftValue.text.isNotEmpty()) {
            val now = System.currentTimeMillis()
            if (now - lastComposingSentMs >= 2_500L) {
                viewModel.sendChatPresence(chatJid, isComposing = true, isAudio = false)
                lastComposingSentMs = now
            }
            delay(4_000L)
            viewModel.sendChatPresence(chatJid, isComposing = false, isAudio = false)
            lastComposingSentMs = 0L
        } else if (lastComposingSentMs > 0L) {
            viewModel.sendChatPresence(chatJid, isComposing = false, isAudio = false)
            lastComposingSentMs = 0L
        }
    }

    var showSearchInChat by remember { mutableStateOf(false) }
    var inChatQuery by remember { mutableStateOf("") }
    var inChatMatches by remember { mutableStateOf<List<MessageEntity>>(emptyList()) }
    var showOverflow by remember { mutableStateOf(false) }
    var showSelectionOverflow by remember { mutableStateOf(false) }

    var showGhostOverrideSheet by remember { mutableStateOf(false) }
    var showStickerStudioDialog by remember { mutableStateOf(false) }
    var showChatHistorySheet by remember { mutableStateOf(false) }
    var editHistoryTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var reactionDetailsTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var groupReceiptsTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var actionMenuTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var deleteConfirmTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var forwardTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var showMoreReactionEmojis by remember { mutableStateOf(false) }
    var editMessageDialogTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var mediaLightboxTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var activeStickerActionMsg by remember { mutableStateOf<MessageEntity?>(null) }
    var pdfViewerTarget by remember { mutableStateOf<Pair<File, String>?>(null) }
    var pendingOutboundPreview by remember { mutableStateOf<StagedOutboundMedia?>(null) }
    var showProfilePhotoLightbox by remember { mutableStateOf(false) }
    var participantAvatarTarget by remember { mutableStateOf<Triple<String, String, String?>?>(null) }
    var participantPopupTarget by remember { mutableStateOf<ParticipantPopupTarget?>(null) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showShareLocationSheet by remember { mutableStateOf(false) }
    var callMessageActionTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var callInfoDialogTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showScheduledSheet by remember { mutableStateOf(false) }
    var editingScheduledMessage by remember { mutableStateOf<ScheduledMessage?>(null) }
    // Multi-selection: keyed by message id so toggling is O(1).
    var selectedMessages by remember(chatJid) { mutableStateOf<Map<String, MessageEntity>>(emptyMap()) }
    // Bulk delete/forward targets (populated when the user triggers the action from multi-select bar).
    var deleteConfirmTargets by remember { mutableStateOf<List<MessageEntity>>(emptyList()) }
    var forwardTargets by remember { mutableStateOf<List<MessageEntity>>(emptyList()) }

    BackHandler(enabled = showAttachTray) {
        showAttachTray = false
    }

    BackHandler(enabled = showEmojiPicker) {
        if (isDockExpanded) {
            isDockExpanded = false
        } else {
            showEmojiPicker = false
            holdDockForIme = false
        }
    }

    BackHandler(enabled = selectedMessages.isNotEmpty()) {
        selectedMessages = emptyMap()
    }

    BackHandler(enabled = actionMenuTarget != null) {
        actionMenuTarget = null
        showMoreReactionEmojis = false
        showSelectionOverflow = false
    }

    val toggleMessageSelection: (MessageEntity) -> Unit = { tapped ->
        showMoreReactionEmojis = false
        showSelectionOverflow = false
        val single = actionMenuTarget
        when {
            selectedMessages.isNotEmpty() -> {
                val next = if (selectedMessages.containsKey(tapped.id)) {
                    selectedMessages - tapped.id
                } else {
                    selectedMessages + (tapped.id to tapped)
                }
                if (next.size == 1) {
                    actionMenuTarget = next.values.first()
                    selectedMessages = emptyMap()
                } else {
                    selectedMessages = next
                }
            }
            single != null -> {
                if (single.id == tapped.id) {
                    actionMenuTarget = null
                } else {
                    selectedMessages = linkedMapOf(single.id to single, tapped.id to tapped)
                    actionMenuTarget = null
                }
            }
            else -> {
                actionMenuTarget = tapped
            }
        }
    }

    val typingComposition by rememberLottieComposition(
        LottieCompositionSpec.Asset("lottie/typing_indicator_bounce_v3.json")
    )

    fun handlePickedAttachment(uri: Uri, fallbackMime: String) {
        val mime = context.contentResolver.getType(uri) ?: fallbackMime
        showAttachTray = false
        if (mime.startsWith("image/", ignoreCase = true) || mime.startsWith("video/", ignoreCase = true)) {
            scope.launch {
                pendingOutboundPreview = viewModel.stageMediaForPreview(uri, mime)
            }
        } else {
            runWithOutboundGuard {
                scrollToBottomPending = true
                viewModel.sendPickedMedia(
                    chatJid = chatJid,
                    uri = uri,
                    mimeType = mime,
                    caption = draftValue.text.trim(),
                    qualityMode = qualityMode,
                    allowUnsavedColdStart = allowUnsavedColdStartFlag
                )
                draftValue = TextFieldValue("")
                viewModel.saveChatDraft(chatJid, "")
                scope.launch { listState.animateScrollToItem(0) }
            }
        }
    }

    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            handlePickedAttachment(uri, "image/jpeg")
        }
    }

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            handlePickedAttachment(uri, "application/octet-stream")
        }
    }

    val pickAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            handlePickedAttachment(uri, "audio/mpeg")
        }
    }

    var pendingCameraCaptureUri by remember { mutableStateOf<Uri?>(null) }
    val takeCameraPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val capUri = pendingCameraCaptureUri
        pendingCameraCaptureUri = null
        if (saved && capUri != null) {
            handlePickedAttachment(capUri, "image/jpeg")
        }
    }

    fun launchCameraPhotoCapture() {
        runCatching {
            val captureDir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
            val photoFile = File(captureDir, "wupp_cam_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            pendingCameraCaptureUri = uri
            showAttachTray = false
            AppLockManager.suspendNextBackgroundLock()
            takeCameraPhotoLauncher.launch(uri)
        }.onFailure {
            pendingCameraCaptureUri = null
            Toast.makeText(context, "Unable to open camera on this device", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraCapturePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraPhotoCapture()
        } else {
            Toast.makeText(context, "Camera permission required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    fun openCameraOrRequestPermission() {
        val hasCam = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCam) {
            launchCameraPhotoCapture()
        } else {
            cameraCapturePermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var showInAppContactPicker by remember { mutableStateOf(false) }
    var pendingShareContact by remember { mutableStateOf<SharedContactPreview?>(null) }

    val contactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Permission state automatically updates InAppContactPickerSheet
    }

    val pickStickerVisualLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runWithOutboundGuard {
                viewModel.sendStickerFromUri(chatJid, uri, allowUnsavedColdStart = allowUnsavedColdStartFlag)
            }
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            runWithOutboundGuard {
                viewModel.toggleVoiceRecording(chatJid, true, allowUnsavedColdStart = allowUnsavedColdStartFlag)
            }
        } else {
            Toast.makeText(context, "Microphone permission required to record voice notes", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(inChatQuery, showSearchInChat) {
        inChatMatches = if (showSearchInChat && inChatQuery.trim().length >= 2) {
            viewModel.searchInChat(chatJid, inChatQuery)
        } else emptyList()
    }

    val wallpaperBackdropState = rememberWuppBackdropState()
    val overlayDensity = LocalDensity.current
    var bottomOverlayPx by remember { mutableIntStateOf(0) }
    val bottomOverlayInsetDp = if (bottomOverlayPx > 0) with(overlayDensity) { bottomOverlayPx.toDp() } else 58.dp
    val messageBoundsInRoot = remember(chatJid) { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
    var overlayOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var overlaySizePx by remember { mutableStateOf(IntSize.Zero) }

    val triggerSingleMessageAction: (MessageEntity, Boolean, SelectionBarAction) -> Unit = { selectedMsg, isSelectedDoc, action ->
        showSelectionOverflow = false
        when (action) {
            SelectionBarAction.REPLY -> {
                replyTarget = selectedMsg
                actionMenuTarget = null
                showMoreReactionEmojis = false
            }
            SelectionBarAction.FORWARD -> {
                val upper = selectedMsg.msgType.uppercase()
                val localReady = upper == "TEXT" || selectedMsg.localMediaPath
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::File)
                    ?.let { it.exists() && it.length() > 0L } == true
                if (localReady) {
                    forwardTarget = selectedMsg
                } else {
                    Toast.makeText(context, "Downloading media before forwarding…", Toast.LENGTH_SHORT).show()
                    viewModel.requestMediaDownload(selectedMsg, userInitiated = true)
                }
                actionMenuTarget = null
                showMoreReactionEmojis = false
            }
            SelectionBarAction.PIN_UNPIN -> {
                actionMenuTarget = null
                showMoreReactionEmojis = false
                if (selectedMsg.isPinned) {
                    viewModel.unpinMessage(chatJid, selectedMsg)
                } else {
                    pinDurationDialogTarget = selectedMsg
                }
            }
            SelectionBarAction.EDIT -> {
                editMessageDialogTarget = selectedMsg
                actionMenuTarget = null
                showMoreReactionEmojis = false
            }
            SelectionBarAction.COPY -> {
                clipboard.setText(AnnotatedString(selectedMsg.body))
                Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                actionMenuTarget = null
                showMoreReactionEmojis = false
            }
            SelectionBarAction.SAVE_MEDIA -> {
                val mediaPath = selectedMsg.localMediaPath
                if (!mediaPath.isNullOrBlank()) {
                    val targetFile = File(mediaPath)
                    val exportName = if (isSelectedDoc) {
                        buildDocumentExportFileName(selectedMsg, targetFile)
                    } else {
                        "WA_${selectedMsg.msgType}_${selectedMsg.id}.${targetFile.extension.ifBlank { "bin" }}"
                    }
                    val exportMime = if (isSelectedDoc) {
                        resolveDocumentMimeType(selectedMsg, targetFile)
                    } else {
                        selectedMsg.mimeType
                    }
                    viewModel.exportMediaToGallery(mediaPath, exportName, exportMime)
                }
                actionMenuTarget = null
                showMoreReactionEmojis = false
            }
            SelectionBarAction.MESSAGE_INFO -> {
                groupReceiptsTarget = selectedMsg
                actionMenuTarget = null
                showMoreReactionEmojis = false
            }
            SelectionBarAction.EDIT_HISTORY -> {
                editHistoryTarget = selectedMsg
                actionMenuTarget = null
                showMoreReactionEmojis = false
            }
            SelectionBarAction.OPEN_DOCUMENT -> {
                val mediaPath = selectedMsg.localMediaPath
                if (!mediaPath.isNullOrBlank()) {
                    val docFile = File(mediaPath)
                    if (docFile.exists()) {
                        openDocumentNatively(context, selectedMsg, docFile) { pdfFile ->
                            pdfViewerTarget = pdfFile to resolveDocumentDisplayName(
                                selectedMsg,
                                resolveDocumentMimeType(selectedMsg, pdfFile),
                                pdfFile
                            )
                        }
                    } else {
                        viewModel.requestMediaDownload(selectedMsg, userInitiated = true)
                    }
                }
                actionMenuTarget = null
                showMoreReactionEmojis = false
            }
            SelectionBarAction.DELETE -> {
                deleteConfirmTarget = selectedMsg
                actionMenuTarget = null
                showMoreReactionEmojis = false
            }
        }
    }

    val iconForSingleAction: (SelectionBarAction) -> androidx.compose.ui.graphics.vector.ImageVector = { action ->
        when (action) {
            SelectionBarAction.REPLY -> Icons.AutoMirrored.Rounded.Reply
            SelectionBarAction.FORWARD -> Icons.AutoMirrored.Rounded.Forward
            SelectionBarAction.PIN_UNPIN -> Icons.Rounded.PushPin
            SelectionBarAction.EDIT -> Icons.Rounded.Edit
            SelectionBarAction.COPY -> Icons.Rounded.ContentCopy
            SelectionBarAction.SAVE_MEDIA -> Icons.Rounded.Download
            SelectionBarAction.MESSAGE_INFO -> Icons.Rounded.Info
            SelectionBarAction.EDIT_HISTORY -> Icons.Rounded.History
            SelectionBarAction.OPEN_DOCUMENT -> Icons.Rounded.Description
            SelectionBarAction.DELETE -> Icons.Rounded.Delete
        }
    }

    val labelForSingleAction: (MessageEntity, Boolean, SelectionBarAction) -> String = { selectedMsg, isSelectedDoc, action ->
        when (action) {
            SelectionBarAction.REPLY -> "Reply"
            SelectionBarAction.FORWARD -> "Forward"
            SelectionBarAction.PIN_UNPIN -> if (selectedMsg.isPinned) {
                context.getString(R.string.unpin_message)
            } else {
                context.getString(R.string.pin_message)
            }
            SelectionBarAction.EDIT -> "Edit"
            SelectionBarAction.COPY -> "Copy"
            SelectionBarAction.SAVE_MEDIA -> if (isSelectedDoc) {
                context.getString(R.string.chat_doc_save_downloads)
            } else {
                "Save to Gallery"
            }
            SelectionBarAction.MESSAGE_INFO -> "Message info"
            SelectionBarAction.EDIT_HISTORY -> "Edit history"
            SelectionBarAction.OPEN_DOCUMENT -> context.getString(R.string.chat_doc_open_with)
            SelectionBarAction.DELETE -> "Delete"
        }
    }

    Scaffold(
        containerColor = colors.chatCanvas,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            val topMotion = MaterialTheme.motionScheme
            Column {
                // Determine which top-bar state we're in:
                // 0 = normal, 1 = single-message action, 2 = multi-select
                val topBarState = when {
                    selectedMessages.isNotEmpty() -> 2
                    actionMenuTarget != null -> 1
                    else -> 0
                }
                AnimatedContent(
                    targetState = topBarState,
                    transitionSpec = {
                        (fadeIn(topMotion.fastEffectsSpec()) + slideInVertically(topMotion.fastSpatialSpec()) { -it / 4 }) togetherWith
                            (fadeOut(topMotion.fastEffectsSpec()) + slideOutVertically(topMotion.fastSpatialSpec()) { -it / 4 })
                    },
                    contentKey = { it },
                    label = "chat_topbar_transition"
                ) { barState ->
                    when (barState) {
                    2 -> {
                        // Multi-select bar (borderless icons directly on frosted glass top bar)
                        val selCount = selectedMessages.size
                        WuppGlassTopBar(
                            backdropState = wallpaperBackdropState,
                            classicMode = stealthConfig.classicWhatsAppLook,
                            showBottomDivider = false,
                            height = 56.dp,
                            leading = {
                                IconButton(
                                    onClick = { selectedMessages = emptyMap() },
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
                                    color = colors.textPrimary
                                )
                            },
                            trailing = {
                                val msgs = selectedMessages.values.toList()
                                val hasText = msgs.any { it.body.isNotBlank() }
                                val canFwd = msgs.all { com.wa.beastclient.core.service.WaRealtimeService.isForwardableMessageType(it) }
                                if (hasText) {
                                    IconButton(
                                        onClick = {
                                            val text = msgs.sortedBy { it.timestamp }
                                                .joinToString("\n") { it.body }
                                            clipboard.setText(AnnotatedString(text))
                                            Toast.makeText(context, "${msgs.size} message(s) copied", Toast.LENGTH_SHORT).show()
                                            selectedMessages = emptyMap()
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = "Copy",
                                            tint = colors.textPrimary,
                                            modifier = Modifier.size(21.dp)
                                        )
                                    }
                                }
                                if (canFwd) {
                                    IconButton(
                                        onClick = {
                                            forwardTargets = msgs.sortedBy { it.timestamp }
                                            selectedMessages = emptyMap()
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Forward,
                                            contentDescription = "Forward",
                                            tint = colors.textPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        deleteConfirmTargets = msgs.sortedBy { it.timestamp }
                                        selectedMessages = emptyMap()
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Delete",
                                        tint = colors.antiDeleteRed,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        )
                    }
                    1 -> {
                    WuppGlassTopBar(
                        backdropState = wallpaperBackdropState,
                        classicMode = stealthConfig.classicWhatsAppLook,
                        showBottomDivider = false,
                        height = 56.dp,
                        leading = {
                            IconButton(
                                onClick = {
                                    actionMenuTarget = null
                                    showMoreReactionEmojis = false
                                    showSelectionOverflow = false
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
                    0 -> {
                    WuppGlassTopBar(
                        backdropState = wallpaperBackdropState,
                        classicMode = stealthConfig.classicWhatsAppLook,
                        showBottomDivider = false,
                        height = 56.dp,
                        leading = {
                            val backInteraction = remember { MutableInteractionSource() }
                            Row(
                                modifier = Modifier
                                    .expressivePressScale(backInteraction, pressedScale = 0.90f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(
                                        interactionSource = backInteraction,
                                        indication = null,
                                        onClick = onBack
                                    )
                                    .padding(vertical = 6.dp, horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                    contentDescription = "Back",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                                if (unreadChatsCount > 0) {
                                    Text(
                                        text = if (unreadChatsCount > 99) "99+" else unreadChatsCount.toString(),
                                        color = colors.textPrimary,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(
                                    start = 6.dp,
                                    end = if (isChannel) 82.dp else 118.dp
                                )
                            ) {
                                Box(modifier = Modifier.clickable { showProfilePhotoLightbox = true }) {
                                    WhatsAppAvatar(
                                        jid = chatJid,
                                        avatarPath = chat?.avatarPath,
                                        isGroup = chat?.isGroup == true,
                                        size = 38.dp
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .clickable { onOpenContactInfo(chatJid) }
                                ) {
                                    val isSelfChat = remember(chatJid, bridgeStatus.ownJid) {
                                        StealthMatrixManager.isSelfChatJid(chatJid, bridgeStatus.ownJid)
                                    }
                                    val displayChatTitle = remember(chat?.name, chatJid, isSelfChat) {
                                        val base = chat?.name?.ifBlank { chatJid.substringBefore("@") }
                                            ?: chatJid.substringBefore("@")
                                        if (isSelfChat && !base.contains("(You)", ignoreCase = true)) "$base (You)" else base
                                    }
                                    Text(
                                        text = displayChatTitle,
                                        color = colors.textPrimary,
                                        fontSize = 16.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val effectiveAbout = remember(chat?.aboutText, contact?.aboutText) {
                                        chat?.aboutText?.takeIf { it.isNotBlank() } ?: contact?.aboutText.orEmpty()
                                    }
                                    val groupMembersSubtitle = remember(
                                        chat?.isGroup,
                                        isChannel,
                                        participantMeta,
                                        chatsByJid,
                                        senderPushByJid
                                    ) {
                                        if (chat?.isGroup != true || isChannel || participantMeta.isEmpty()) {
                                            ""
                                        } else {
                                            val others = participantMeta.values
                                                .asSequence()
                                                .filter { !it.isSelf }
                                                .map { p ->
                                                    val resolved = resolveSenderDisplayInfo(
                                                        rawJidOrText = p.jid,
                                                        explicitName = p.savedName.ifBlank { p.pushName },
                                                        isSelf = false,
                                                        participantMeta = participantMeta,
                                                        chatsByJid = chatsByJid,
                                                        senderPushByJid = senderPushByJid
                                                    ).primary.removePrefix("~ ").trim()
                                                    resolved.substringBefore(" ")
                                                        .ifBlank { resolved }
                                                }
                                                .filter { it.isNotBlank() && !it.startsWith("User ·") }
                                                .distinct()
                                                .sortedBy { it.lowercase(Locale.getDefault()) }
                                                .take(8)
                                                .toList()
                                            val hasSelf = participantMeta.values.any { it.isSelf }
                                            val allNames = if (hasSelf) others + "You" else others
                                            allNames.joinToString(", ")
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val presence = chat?.chatPresence.orEmpty()
                                        if (presence.isNotBlank()) {
                                            if (typingComposition != null) {
                                                LottieAnimation(
                                                    composition = typingComposition,
                                                    iterations = LottieConstants.IterateForever,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = presence,
                                                fontSize = 12.5.sp,
                                                color = colors.brightGreen
                                            )
                                        } else {
                                            val sub = when {
                                                isChannel -> "Public channel"
                                                chat?.isGroup == true && groupMembersSubtitle.isNotBlank() -> groupMembersSubtitle
                                                chat?.isOnline == true -> stringResource(R.string.chat_status_online)
                                                (chat?.lastSeen ?: 0L) > 0L -> "last seen ${formatMessageClock(chat!!.lastSeen, includeSeconds = showSeconds, use24Hour = use24Hour)}"
                                                effectiveAbout.isNotBlank() -> effectiveAbout
                                                else -> ""
                                            }
                                            if (sub.isNotBlank()) {
                                                Text(
                                                    text = sub,
                                                    fontSize = 12.5.sp,
                                                    color = if (chat?.isOnline == true && !isChannel && chat?.isGroup != true) colors.brightGreen else colors.textSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    if (stealthConfig.alwaysShowContactBio &&
                                        effectiveAbout.isNotBlank() &&
                                        !isChannel &&
                                        !chatJid.endsWith("@g.us") &&
                                        (chat?.isOnline == true || (chat?.lastSeen ?: 0L) > 0L || !chat?.chatPresence.isNullOrBlank())
                                    ) {
                                        Text(
                                            text = effectiveAbout,
                                            fontSize = 11.sp,
                                            color = colors.textSecondary.copy(alpha = 0.85f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        },
                        trailing = {
                            if (isChannel) {
                                val muteInteraction = remember { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .expressivePressScale(muteInteraction, pressedScale = 0.88f)
                                        .clip(CircleShape)
                                        .clickable(
                                            interactionSource = muteInteraction,
                                            indication = null,
                                            onClick = { viewModel.toggleChannelMute(chatJid, chat?.isMuted == true) }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (chat?.isMuted == true) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                                        contentDescription = if (chat?.isMuted == true) "Unmute channel" else "Mute channel",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            } else {
                                val videoInteraction = remember { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .expressivePressScale(videoInteraction, pressedScale = 0.88f)
                                        .clip(CircleShape)
                                        .clickable(
                                            interactionSource = videoInteraction,
                                            indication = null,
                                            onClick = { initiateCall(true) }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Videocam,
                                        contentDescription = "Video call",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                val voiceInteraction = remember { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .expressivePressScale(voiceInteraction, pressedScale = 0.88f)
                                        .clip(CircleShape)
                                        .clickable(
                                            interactionSource = voiceInteraction,
                                            indication = null,
                                            onClick = { initiateCall(false) }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Call,
                                        contentDescription = "Voice call",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Box {
                                val moreInteraction = remember { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .expressivePressScale(moreInteraction, pressedScale = 0.88f)
                                        .clip(CircleShape)
                                        .clickable(
                                            interactionSource = moreInteraction,
                                            indication = null,
                                            onClick = { showOverflow = true }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = "More",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Quick controls & privacy") },
                                        leadingIcon = { Icon(Icons.Rounded.Shield, null) },
                                        onClick = {
                                            showOverflow = false
                                            showGhostOverrideSheet = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("View contact info") },
                                        leadingIcon = { Icon(Icons.Rounded.Person, null) },
                                        onClick = {
                                            showOverflow = false
                                            onOpenContactInfo(chatJid)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Search") },
                                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                                        onClick = {
                                            showOverflow = false
                                            showSearchInChat = !showSearchInChat
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Mark as read") },
                                        leadingIcon = { Icon(Icons.Rounded.Visibility, null) },
                                        onClick = {
                                            showOverflow = false
                                            viewModel.markChatReadExplicitly(chatJid)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (chat?.isPinned == true) stringResource(R.string.chat_action_unpin)
                                                else stringResource(R.string.chat_action_pin)
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.PushPin, null) },
                                        onClick = {
                                            showOverflow = false
                                            chat?.let { viewModel.togglePinChat(it) }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (chat?.isMuted == true) stringResource(R.string.chat_action_unmute)
                                                else stringResource(R.string.chat_action_mute)
                                            )
                                        },
                                        leadingIcon = { Icon(if (chat?.isMuted == true) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff, null) },
                                        onClick = {
                                            showOverflow = false
                                            viewModel.muteChats(listOf(chatJid), chat?.isMuted != true)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (chat?.isFavorite == true) "Remove from Favorites" else "Add to Favorites") },
                                        leadingIcon = { Icon(if (chat?.isFavorite == true) Icons.Rounded.Star else Icons.Rounded.StarBorder, null) },
                                        onClick = {
                                            showOverflow = false
                                            chat?.let { viewModel.toggleFavoriteChat(it) }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Chat wallpaper") },
                                        leadingIcon = { Icon(Icons.Rounded.Image, null) },
                                        onClick = {
                                            showOverflow = false
                                            showWallpaperDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Scheduled messages") },
                                        leadingIcon = { Icon(Icons.Rounded.Schedule, null) },
                                        onClick = {
                                            showOverflow = false
                                            showScheduledSheet = true
                                        }
                                    )
                                }
                            }
                        }
                    )
                    } // end 0 ->
                } // end when(barState)
                } // end AnimatedContent

                AnimatedVisibility(visible = showSearchInChat && actionMenuTarget == null) {
                    var inChatMatchIndex by remember(inChatMatches) { mutableStateOf(0) }
                    LaunchedEffect(inChatMatches, inChatMatchIndex) {
                        inChatMatches.getOrNull(inChatMatchIndex)?.let { match ->
                            jumpToQuotedMessage(match.id)
                        }
                    }
                    Column(modifier = Modifier.background(colors.elevatedBar).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        OutlinedTextField(
                            value = inChatQuery,
                            onValueChange = { inChatQuery = it },
                            placeholder = { Text("Search messages or voice transcripts...") },
                            singleLine = true,
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (inChatMatches.isNotEmpty()) {
                                        IconButton(
                                            enabled = inChatMatchIndex < inChatMatches.lastIndex,
                                            onClick = {
                                                if (inChatMatchIndex < inChatMatches.lastIndex) {
                                                    inChatMatchIndex += 1
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Rounded.KeyboardArrowUp, "Older match")
                                        }
                                        IconButton(
                                            enabled = inChatMatchIndex > 0,
                                            onClick = {
                                                if (inChatMatchIndex > 0) {
                                                    inChatMatchIndex -= 1
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Rounded.KeyboardArrowDown, "Newer match")
                                        }
                                    }
                                    IconButton(onClick = { showSearchInChat = false; inChatQuery = "" }) {
                                        Icon(Icons.Rounded.Close, "Close")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (inChatMatches.isNotEmpty()) {
                            val currentMatch = inChatMatches.getOrElse(inChatMatchIndex) { inChatMatches.first() }
                            val snippet = currentMatch.body.ifBlank { currentMatch.audioTranscript.orEmpty() }
                            Text(
                                text = "${inChatMatchIndex + 1} / ${inChatMatches.size} · \"$snippet\"",
                                fontSize = 11.5.sp,
                                color = colors.brightGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        } else if (inChatQuery.trim().length >= 2) {
                            Text(
                                text = "No matches found",
                                fontSize = 11.5.sp,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = pinnedMessages.isNotEmpty() && actionMenuTarget == null,
                    enter = fadeIn(topMotion.fastEffectsSpec()) + expandVertically(topMotion.fastSpatialSpec()),
                    exit = fadeOut(topMotion.fastEffectsSpec()) + shrinkVertically(topMotion.fastSpatialSpec())
                ) {
                    PinnedMessageBanner(
                        pinnedMessages = pinnedMessages,
                        colors = colors,
                        onMessageClick = { targetMsg ->
                            jumpToQuotedMessage(targetMsg.id)
                        },
                        onUnpinClick = { targetMsg ->
                            viewModel.unpinMessage(chatJid, targetMsg)
                        }
                    )
                }

                val isMetaAiChat = remember(chatJid, chat?.name) {
                    chatJid.contains("13135550002") || chat?.name.equals("Meta AI", ignoreCase = true)
                }
                if (isMetaAiChat) {
                    Surface(
                        color = colors.pillActiveBg.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = colors.accentGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Meta AI chats are managed directly in official WhatsApp. Companion devices cannot message Meta AI directly.",
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val topBarInsetDp = innerPadding.calculateTopPadding()
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (!stealthConfig.classicWhatsAppLook) {
                            Modifier.wuppBackdropSource(wallpaperBackdropState)
                        } else {
                            Modifier
                        }
                    )
            ) {
                DoodleBackground(
                    wallpaperUriOverride = effectiveWallpaper.uri,
                    wallpaperDimOverride = effectiveWallpaper.dim,
                    showDoodleOverride = effectiveWallpaper.showDoodle,
                    wallpaperScaleOverride = effectiveWallpaper.scale,
                    wallpaperOffsetXOverride = effectiveWallpaper.offsetXFraction,
                    wallpaperOffsetYOverride = effectiveWallpaper.offsetYFraction,
                    backdropState = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (actionMenuTarget != null && selectedMessages.isEmpty()) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    actionMenuTarget = null
                                    showMoreReactionEmojis = false
                                    showSelectionOverflow = false
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                            .navigationBarsPadding(),
                        reverseLayout = true,
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = topBarInsetDp + 6.dp,
                            bottom = bottomOverlayInsetDp + 8.dp
                        )
                    ) {
                    items(
                        count = pagedItems.itemCount,
                        key = pagedItems.itemKey { it.id }
                    ) { index ->
                        val msg = pagedItems[index] ?: return@items
                        if (isHiddenPlaceholderMessage(msg)) {
                            return@items
                        }
                        // Remember: reverseLayout=true -> index + 1 is visually ABOVE index
                        var nextVisibleIdx = index + 1
                        var above: MessageEntity? = null
                        while (nextVisibleIdx < pagedItems.itemCount) {
                            val candidate = pagedItems.peek(nextVisibleIdx)
                            if (candidate != null && !isHiddenPlaceholderMessage(candidate)) {
                                above = candidate
                                break
                            }
                            nextVisibleIdx++
                        }
                        val showDateHeader = above == null || !isSameCalendarDay(msg.timestamp, above.timestamp)
                        val msgSenderKey = msg.senderJid.takeIf {
                            it.isNotBlank() && !DeviceContactFallbackResolver.isGroupJidLikeIdentifier(it, msg.chatJid)
                        } ?: msg.senderName.trim()
                        val aboveSenderKey = above?.let { a ->
                            a.senderJid.takeIf {
                                it.isNotBlank() && !DeviceContactFallbackResolver.isGroupJidLikeIdentifier(it, a.chatJid)
                            } ?: a.senderName.trim()
                        }.orEmpty()
                        val isFirstInCluster = above == null ||
                            showDateHeader ||
                            above.isFromMe != msg.isFromMe ||
                            (msgSenderKey.isBlank() && aboveSenderKey.isNotBlank()) ||
                            aboveSenderKey != msgSenderKey
                        var prevVisibleIdx = index - 1
                        var below: MessageEntity? = null
                        while (prevVisibleIdx >= 0) {
                            val candidate = pagedItems.peek(prevVisibleIdx)
                            if (candidate != null && !isHiddenPlaceholderMessage(candidate)) {
                                below = candidate
                                break
                            }
                            prevVisibleIdx--
                        }
                        val belowSenderKey = below?.let { b ->
                            b.senderJid.takeIf {
                                it.isNotBlank() && !DeviceContactFallbackResolver.isGroupJidLikeIdentifier(it, b.chatJid)
                            } ?: b.senderName.trim()
                        }.orEmpty()
                        val isLastInCluster = below == null ||
                            !isSameCalendarDay(msg.timestamp, below.timestamp) ||
                            below.msgType == "SYSTEM" ||
                            below.isFromMe != msg.isFromMe ||
                            (msgSenderKey.isBlank() && belowSenderKey.isNotBlank()) ||
                            belowSenderKey != msgSenderKey
                        val isSelected = actionMenuTarget?.id == msg.id || selectedMessages.containsKey(msg.id)
                        val isHighlighted = highlightedMessageId == msg.id
                        val rowBgColor by animateColorAsState(
                            targetValue = when {
                                isHighlighted -> colors.brightGreen.copy(alpha = 0.24f)
                                isSelected -> colors.brightGreen.copy(alpha = 0.16f)
                                else -> Color.Transparent
                            },
                            animationSpec = tween(durationMillis = 360),
                            label = "msg_row_highlight_bg"
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowBgColor)
                        ) {
                            if (showDateHeader && msg.timestamp > 0L) {
                                DateHeaderPill(timestamp = msg.timestamp)
                            }
                            Spacer(modifier = Modifier.height(if (isFirstInCluster || showDateHeader) 5.dp else 1.5.dp))

                            if (msg.msgType == "SYSTEM") {
                                SystemNoticePill(text = msg.body)
                            } else {
                                val channelDisplayName = chat?.name?.takeIf { it.isNotBlank() } ?: chatJid.substringBefore("@")
                                val isGroupChat = chat?.isGroup == true && !isChannel
                                val senderMeta = participantMeta[msg.senderJid]
                                val altJid = senderMeta?.altJid.orEmpty()
                                val senderAvatarPath = senderMeta?.avatarPath?.takeIf { it.isNotBlank() }
                                    ?: chatsByJid[msg.senderJid]?.avatarPath?.takeIf { it.isNotBlank() }
                                    ?: altJid.takeIf { it.isNotBlank() }?.let { chatsByJid[it]?.avatarPath?.takeIf { p -> p.isNotBlank() } }
                                    ?: if (!isGroupChat && !msg.isFromMe) chat?.avatarPath?.takeIf { it.isNotBlank() } else null
                                val senderDisplay = resolveSenderDisplayInfo(
                                    rawJidOrText = msg.senderJid,
                                    explicitName = msg.senderName,
                                    isSelf = msg.isFromMe || senderMeta?.isSelf == true,
                                    participantMeta = participantMeta,
                                    chatsByJid = chatsByJid,
                                    senderPushByJid = senderPushByJid,
                                    isGroupAnnounceLocked = isGroupAnnounceLocked
                                )
                                val quotedDisplay = if (!msg.quotedId.isNullOrBlank()) {
                                    resolveQuotedBannerSender(
                                        msg = msg,
                                        isChannel = isChannel,
                                        channelDisplayName = channelDisplayName,
                                        loadedMessagesById = loadedMessagesById,
                                        quotedMsgMeta = quotedMsgMeta,
                                        participantMeta = participantMeta,
                                        chatsByJid = chatsByJid,
                                        senderPushByJid = senderPushByJid
                                    )
                                } else null
                                val quotedEntity = msg.quotedId?.let { loadedMessagesById[it] }
                                val quotedMentionsCsv = quotedEntity?.mentionsCsv.orEmpty()
                                val quotedMediaPath = quotedEntity?.localMediaPath
                                val quotedMsgType = quotedEntity?.msgType
                                val quotedIsViewOnce = quotedEntity?.isViewOnce == true
                                val quotedDurationSec = quotedEntity?.durationSec ?: 0
                                val isVoiceOrMediaMsg = msg.msgType == "PTT" || msg.msgType == "AUDIO" ||
                                    msg.msgType == "IMAGE" || msg.msgType == "VIDEO" || msg.msgType == "GIF"
                                val showSenderForRow = isFirstInCluster || (
                                    isGroupChat &&
                                        !msg.isFromMe &&
                                        isVoiceOrMediaMsg &&
                                        above?.msgType != msg.msgType
                                    )
                                val mentionDisplayResolver: ((String) -> String?)? =
                                    if (isGroupChat) {
                                        { mentionJid: String ->
                                            val cleanMention = mentionJid.trim()
                                            val bareDigits = cleanMention.substringBefore("@").substringBefore(":")
                                            val directMeta = participantMeta[cleanMention]
                                                ?: participantMeta["$bareDigits@s.whatsapp.net"]
                                                ?: participantMeta["$bareDigits@lid"]
                                                ?: participantMeta.values.firstOrNull { meta ->
                                                    meta.altJid.substringBefore("@").substringBefore(":") == bareDigits
                                                }
                                            val effectiveJid = directMeta?.jid ?: cleanMention
                                            if (directMeta?.isSelf == true) {
                                                "You"
                                            } else {
                                                val info = resolveSenderDisplayInfo(
                                                    rawJidOrText = effectiveJid,
                                                    explicitName = "",
                                                    isSelf = false,
                                                    participantMeta = participantMeta,
                                                    chatsByJid = chatsByJid,
                                                    senderPushByJid = senderPushByJid
                                                )
                                                val candidate = info.primary.removePrefix("~ ").trim()
                                                candidate.takeIf {
                                                    it.isNotBlank() &&
                                                        !it.startsWith("User ·") &&
                                                        it != bareDigits &&
                                                        !DeviceContactFallbackResolver.isGroupJidLikeIdentifier(it)
                                                }
                                            }
                                        }
                                    } else {
                                        null
                                    }
                                val isAdminSender = (isGroupChat && isGroupAnnounceLocked && !msg.isFromMe) ||
                                    senderDisplay.keyForColor == "admin"
                                val clickAvatar: () -> Unit = {
                                    if (isGroupChat && !msg.isFromMe) {
                                        val cleanName = senderDisplay.primary.removePrefix("~ ").trim()
                                            .ifBlank { msg.senderJid.substringBefore("@") }
                                        val resolvedAltJid = altJid.takeIf { it.isNotBlank() }
                                            ?: senderDisplay.secondaryPhone.takeIf { it.isNotBlank() }
                                        participantPopupTarget = ParticipantPopupTarget(
                                            jid = msg.senderJid,
                                            name = cleanName,
                                            avatarPath = senderAvatarPath,
                                            phoneDisplay = senderDisplay.secondaryPhone,
                                            altJid = resolvedAltJid.orEmpty(),
                                            isAdmin = isAdminSender
                                        )
                                    } else {
                                        participantAvatarTarget = Triple(
                                            msg.senderJid,
                                            senderDisplay.primary,
                                            senderAvatarPath
                                        )
                                    }
                                }
                                val badgeText = remember(msg.reactionSummary, chatReactionCounts[msg.id]) {
                                    formatReactionBadgeSummary(
                                        legacySummary = msg.reactionSummary,
                                        counts = chatReactionCounts[msg.id].orEmpty()
                                    )
                                }
                                val handleOpenDocument: () -> Unit = {
                                    val docFile = msg.localMediaPath?.takeIf { it.isNotBlank() }?.let(::File)
                                    if (docFile != null && docFile.exists() && docFile.length() > 0L) {
                                        openDocumentNatively(context, msg, docFile) { pdfFile ->
                                            pdfViewerTarget = pdfFile to resolveDocumentDisplayName(
                                                msg,
                                                resolveDocumentMimeType(msg, pdfFile),
                                                pdfFile
                                            )
                                        }
                                    } else {
                                        viewModel.requestMediaDownload(msg, userInitiated = true)
                                    }
                                }
                                val handleSaveDocument: () -> Unit = {
                                    val docFile = msg.localMediaPath?.takeIf { it.isNotBlank() }?.let(::File)
                                    if (docFile != null && docFile.exists() && docFile.length() > 0L) {
                                        viewModel.exportMediaToGallery(
                                            localPath = docFile.absolutePath,
                                            displayName = buildDocumentExportFileName(msg, docFile),
                                            mimeType = resolveDocumentMimeType(msg, docFile)
                                        )
                                    } else {
                                        viewModel.requestMediaDownload(msg, userInitiated = true)
                                    }
                                }
                                val isInSelectionMode = actionMenuTarget != null || selectedMessages.isNotEmpty()
                                val isMsgSelected = actionMenuTarget?.id == msg.id || selectedMessages.containsKey(msg.id)
                                val reportBubbleBounds: (androidx.compose.ui.geometry.Rect) -> Unit = { rect ->
                                    messageBoundsInRoot[msg.id] = rect
                                }
                                val handleQuickForward: (() -> Unit)? = if (
                                    com.wa.beastclient.core.service.WaRealtimeService.isForwardableMessageType(msg)
                                ) {
                                    { forwardTarget = msg }
                                } else null
                                if (isChannel || isGroupAnnounceLocked) {
                                    MessageBubbleRow(
                                        msg = msg,
                                        isGroup = isGroupChat,
                                        isChannel = isChannel,
                                        channelDisplayName = channelDisplayName,
                                        showTail = if (isChannel) false else isLastInCluster,
                                        showSenderHeader = if (isChannel) false else showSenderForRow,
                                        isSenderAdmin = isGroupChat && isGroupAnnounceLocked && !msg.isFromMe,
                                        senderDisplay = senderDisplay,
                                        senderAvatarPath = senderAvatarPath,
                                        quotedDisplay = quotedDisplay,
                                        quotedMentionsCsv = quotedMentionsCsv,
                                        quotedMediaPath = quotedMediaPath,
                                        quotedMsgType = quotedMsgType,
                                        quotedIsViewOnce = quotedIsViewOnce,
                                        quotedDurationSec = quotedDurationSec,
                                        mentionDisplayResolver = mentionDisplayResolver,
                                        onClickSenderAvatar = clickAvatar,
                                        ghostPlayedActive = effectivePrivacy?.sendPlayedReceipts == false,
                                        isDownloadingMedia = downloadingMediaIds.contains(msg.id),
                                        mediaDownloadError = failedMediaIds[msg.id],
                                        isSelectionMode = isInSelectionMode,
                                        isSelected = isMsgSelected,
                                        onToggleSelect = { toggleMessageSelection(msg) },
                                        onBubbleBoundsChanged = reportBubbleBounds,
                                        onQuickForward = handleQuickForward,
                                        onClickReaction = { reactionDetailsTarget = msg },
                                        reactionBadgeText = badgeText,
                                        onClickEditBadge = { editHistoryTarget = msg },
                                        onClickQuoted = jumpToQuotedMessage,
                                        onManualSendPlayedReceipt = { viewModel.markAudioPlayedExplicitly(msg) },
                                        onAutoRequestDownload = { viewModel.requestMediaDownload(msg, userInitiated = false) },
                                        onRequestDownload = { viewModel.requestMediaDownload(msg, userInitiated = true) },
                                        onOpenMedia = { mediaLightboxTarget = msg },
                                        onOpenSticker = { activeStickerActionMsg = msg },
                                        onOpenDocument = handleOpenDocument,
                                        onSaveDocument = handleSaveDocument,
                                        onCallBackMessage = { isVideoCall -> initiateCall(isVideoCall) },
                                        onLongClickCallMessage = {
                                            if (isInSelectionMode) toggleMessageSelection(msg)
                                            else callMessageActionTarget = msg
                                        },
                                        onLongPress = { toggleMessageSelection(msg) },
                                        onNavigateToChat = onNavigateToChat
                                    )
                                } else {
                                    SwipeToReplyBox(onReply = { replyTarget = msg }) {
                                        MessageBubbleRow(
                                            msg = msg,
                                            isGroup = chat?.isGroup == true,
                                            isChannel = false,
                                            channelDisplayName = channelDisplayName,
                                            showTail = isLastInCluster,
                                            showSenderHeader = showSenderForRow,
                                            senderDisplay = senderDisplay,
                                            senderAvatarPath = senderAvatarPath,
                                            quotedDisplay = quotedDisplay,
                                            quotedMentionsCsv = quotedMentionsCsv,
                                            quotedMediaPath = quotedMediaPath,
                                            quotedMsgType = quotedMsgType,
                                            quotedIsViewOnce = quotedIsViewOnce,
                                            quotedDurationSec = quotedDurationSec,
                                            mentionDisplayResolver = mentionDisplayResolver,
                                            onClickSenderAvatar = clickAvatar,
                                            ghostPlayedActive = effectivePrivacy?.sendPlayedReceipts == false,
                                            isDownloadingMedia = downloadingMediaIds.contains(msg.id),
                                            mediaDownloadError = failedMediaIds[msg.id],
                                            isSelectionMode = isInSelectionMode,
                                            isSelected = isMsgSelected,
                                            onToggleSelect = { toggleMessageSelection(msg) },
                                            onBubbleBoundsChanged = reportBubbleBounds,
                                            onQuickForward = handleQuickForward,
                                            onClickReaction = { reactionDetailsTarget = msg },
                                            reactionBadgeText = badgeText,
                                            onClickEditBadge = { editHistoryTarget = msg },
                                            onClickQuoted = jumpToQuotedMessage,
                                            onManualSendPlayedReceipt = { viewModel.markAudioPlayedExplicitly(msg) },
                                            onAutoRequestDownload = { viewModel.requestMediaDownload(msg, userInitiated = false) },
                                            onRequestDownload = { viewModel.requestMediaDownload(msg, userInitiated = true) },
                                            onOpenMedia = { mediaLightboxTarget = msg },
                                            onOpenSticker = { activeStickerActionMsg = msg },
                                            onOpenDocument = handleOpenDocument,
                                            onSaveDocument = handleSaveDocument,
                                            onCallBackMessage = { isVideoCall -> initiateCall(isVideoCall) },
                                            onLongClickCallMessage = {
                                                if (isInSelectionMode) toggleMessageSelection(msg)
                                                else callMessageActionTarget = msg
                                            },
                                            onLongPress = { toggleMessageSelection(msg) },
                                            onNavigateToChat = onNavigateToChat
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .navigationBarsPadding()
                ) {
                    Spacer(modifier = Modifier.height(topBarInsetDp))
                    ActiveCallFloatingBar()
                    // Error 463 Ban Guard Warning Banner on genuinely unsaved zero-history chats (non-blocking)
                    if (coldStartWarningActive) {
                        val warnAmber = Color(0xFFFFB020)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(warnAmber.copy(alpha = 0.14f))
                                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Warning, null, tint = warnAmber, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Unsaved number · 0 prior messages. First message carries Error 463 risk.",
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { coldStartConfirmed = true }
                            ) {
                                Text(
                                    text = "Allow",
                                    color = warnAmber,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    val showScrollToBottom by remember(listState) {
                        derivedStateOf { listState.firstVisibleItemIndex > 2 }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                overlayOriginInRoot = coords.positionInRoot()
                                overlaySizePx = coords.size
                            }
                    ) {

                // Bubble-Anchored Emoji Reaction Pill + Floating Liquid Glass Context Menu Column
                var lastReactionTarget by remember { mutableStateOf<MessageEntity?>(null) }
                LaunchedEffect(actionMenuTarget) {
                    if (actionMenuTarget != null) lastReactionTarget = actionMenuTarget
                }
                val reactionMotion = MaterialTheme.motionScheme
                androidx.compose.animation.AnimatedVisibility(
                    visible = actionMenuTarget != null && selectedMessages.isEmpty(),
                    modifier = Modifier.fillMaxSize(),
                    enter = fadeIn(reactionMotion.fastEffectsSpec()),
                    exit = fadeOut(reactionMotion.fastEffectsSpec())
                ) {
                    val target = actionMenuTarget ?: lastReactionTarget
                    var pillSizePx by remember(target?.id) { mutableStateOf(IntSize.Zero) }
                    var menuSizePx by remember(target?.id) { mutableStateOf(IntSize.Zero) }

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Whisper-light tint scrim (passes taps and scrolls through so users can directly select more messages)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = if (colors.isDark) 0.04f else 0.03f))
                        )

                        if (target != null) {
                            val overlayW = overlaySizePx.width
                            val overlayH = overlaySizePx.height
                            val marginPx = with(overlayDensity) { 12.dp.roundToPx() }
                            val gapPx = with(overlayDensity) { 8.dp.roundToPx() }
                            val topSafePx = with(overlayDensity) { 6.dp.roundToPx() }
                            val bottomSafePx = with(overlayDensity) { 10.dp.roundToPx() }

                            val bubbleRect = messageBoundsInRoot[target.id]
                            val bTop = if (bubbleRect != null && overlayH > 0) {
                                (bubbleRect.top - overlayOriginInRoot.y).toInt()
                            } else {
                                (overlayH * 0.28f).toInt()
                            }
                            val bBottom = if (bubbleRect != null && overlayH > 0) {
                                (bubbleRect.bottom - overlayOriginInRoot.y).toInt()
                            } else {
                                (overlayH * 0.42f).toInt()
                            }
                            val bLeft = if (bubbleRect != null && overlayW > 0) {
                                (bubbleRect.left - overlayOriginInRoot.x).toInt()
                            } else {
                                marginPx
                            }
                            val bRight = if (bubbleRect != null && overlayW > 0) {
                                (bubbleRect.right - overlayOriginInRoot.x).toInt()
                            } else {
                                (overlayW - marginPx).coerceAtLeast(marginPx)
                            }

                            val estPillW = if (pillSizePx.width > 0) pillSizePx.width else with(overlayDensity) { 332.dp.roundToPx() }
                            val estPillH = if (pillSizePx.height > 0) pillSizePx.height else with(overlayDensity) { 54.dp.roundToPx() }
                            val estMenuW = if (menuSizePx.width > 0) menuSizePx.width else with(overlayDensity) { 196.dp.roundToPx() }
                            val estMenuH = if (menuSizePx.height > 0) menuSizePx.height else with(overlayDensity) { 356.dp.roundToPx() }

                            val maxPillY = (overlayH - bottomSafePx - estPillH).coerceAtLeast(topSafePx)
                            val maxMenuY = (overlayH - bottomSafePx - estMenuH).coerceAtLeast(topSafePx)

                            val roomBelowBubble = overlayH - bottomSafePx - (bBottom + gapPx)
                            val roomAboveBubble = bTop - topSafePx - gapPx

                            val menuY: Int
                            val pillY: Int
                            if (roomBelowBubble >= estMenuH) {
                                // Room below bubble: pill sits directly above bubble, glass menu right below bubble
                                menuY = (bBottom + gapPx).coerceIn(topSafePx, maxMenuY)
                                pillY = (bTop - gapPx - estPillH).coerceIn(topSafePx, (menuY - gapPx - estPillH).coerceAtLeast(topSafePx))
                            } else if (roomAboveBubble >= estMenuH + gapPx + estPillH) {
                                // Bubble near bottom: stack glass menu right above bubble and pill above menu
                                menuY = (bTop - gapPx - estMenuH).coerceIn(topSafePx, maxMenuY)
                                pillY = (menuY - gapPx - estPillH).coerceIn(topSafePx, maxPillY)
                            } else {
                                // Very tall media bubble or tight viewport: anchor near top of bubble without clipping
                                pillY = bTop.coerceIn(topSafePx, (overlayH - bottomSafePx - estPillH - gapPx - 120).coerceAtLeast(topSafePx))
                                menuY = (pillY + estPillH + gapPx).coerceIn(topSafePx, maxMenuY)
                            }

                            val isOutgoingTarget = target.isFromMe && !isChannel
                            val maxPillX = (overlayW - marginPx - estPillW).coerceAtLeast(marginPx)
                            val pillX = if (isOutgoingTarget) {
                                (bRight - estPillW).coerceIn(marginPx, maxPillX)
                            } else {
                                bLeft.coerceIn(marginPx, maxPillX)
                            }
                            val maxMenuX = (overlayW - marginPx - estMenuW).coerceAtLeast(marginPx)
                            val menuX = if (isOutgoingTarget) {
                                (bRight - estMenuW).coerceIn(marginPx, maxMenuX)
                            } else {
                                bLeft.coerceIn(marginPx, maxMenuX)
                            }

                            val hasLiveGlass = !stealthConfig.classicWhatsAppLook && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            val popupGlassTint = if (colors.isDark) Color(0xFF2B2E33) else Color(0xFFF8FAFC)
                            val popupGlassAlpha = if (hasLiveGlass) {
                                if (colors.isDark) 0.42f else 0.62f
                            } else {
                                if (colors.isDark) 0.82f else 0.94f
                            }

                            // 1. Anchored Floating Frosted Emoji Reaction Pill
                            Column(
                                horizontalAlignment = if (isOutgoingTarget) Alignment.End else Alignment.Start,
                                modifier = Modifier
                                    .offset { IntOffset(pillX, pillY) }
                                    .onSizeChanged { pillSizePx = it }
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {}
                                    .animateEnterExit(
                                        enter = scaleIn(
                                            animationSpec = reactionMotion.fastSpatialSpec(),
                                            initialScale = 0.86f
                                        ),
                                        exit = scaleOut(
                                            animationSpec = reactionMotion.fastEffectsSpec(),
                                            targetScale = 0.92f
                                        )
                                    )
                            ) {
                                WuppGlassContainer(
                                    shape = CircleShape,
                                    colors = colors,
                                    elevation = 14.dp,
                                    backdropState = wallpaperBackdropState,
                                    blurRadius = 16.dp,
                                    refractionHeight = 10.dp,
                                    refractionAmount = 18.dp,
                                    chromaticAberration = false,
                                    surfaceTintOverride = popupGlassTint,
                                    surfaceAlphaOverride = popupGlassAlpha,
                                    showHighlight = false,
                                    classicMode = stealthConfig.classicWhatsAppLook
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .border(
                                                width = 0.55.dp,
                                                color = if (colors.isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.45f),
                                                shape = CircleShape
                                            )
                                            .padding(horizontal = 11.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        val activeReactions = stealthConfig.quickReactionEmojis.ifEmpty {
                                            StealthMatrixManager.DEFAULT_QUICK_REACTIONS
                                        }
                                        val myReaction by remember(target.id) {
                                            viewModel.observeMyReaction(target.id)
                                        }.collectAsState(initial = null)
                                        activeReactions.forEach { emoji ->
                                            val isCurrent = myReaction == emoji
                                            val emojiInteraction = remember(emoji) { MutableInteractionSource() }
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .expressivePressScale(emojiInteraction, pressedScale = 0.80f)
                                                    .clip(CircleShape)
                                                    .background(if (isCurrent) colors.brightGreen.copy(alpha = 0.24f) else Color.Transparent)
                                                    .clickable(
                                                        interactionSource = emojiInteraction,
                                                        indication = null
                                                    ) {
                                                        runWithOutboundGuard {
                                                            viewModel.sendReaction(chatJid, target, emoji)
                                                        }
                                                        actionMenuTarget = null
                                                        showMoreReactionEmojis = false
                                                        showSelectionOverflow = false
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = emoji, fontSize = 27.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(2.dp))
                                        val moreInteraction = remember { MutableInteractionSource() }
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .expressivePressScale(moreInteraction, pressedScale = 0.84f)
                                                .clip(CircleShape)
                                                .background(
                                                    if (colors.isDark) Color.White.copy(alpha = 0.34f)
                                                    else Color.Black.copy(alpha = 0.14f)
                                                )
                                                .clickable(
                                                    interactionSource = moreInteraction,
                                                    indication = null
                                                ) { showMoreReactionEmojis = !showMoreReactionEmojis },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (showMoreReactionEmojis) Icons.Rounded.Close else Icons.Rounded.Add,
                                                contentDescription = "More reactions",
                                                tint = if (colors.isDark) Color(0xFF14171A) else colors.textPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                androidx.compose.animation.AnimatedVisibility(
                                    visible = showMoreReactionEmojis,
                                    enter = fadeIn(reactionMotion.fastEffectsSpec()) + scaleIn(
                                        animationSpec = reactionMotion.fastSpatialSpec(),
                                        initialScale = 0.88f
                                    ),
                                    exit = fadeOut(reactionMotion.fastEffectsSpec()) + scaleOut(
                                        animationSpec = reactionMotion.fastEffectsSpec(),
                                        targetScale = 0.92f
                                    )
                                ) {
                                    WuppGlassContainer(
                                        shape = RoundedCornerShape(24.dp),
                                        colors = colors,
                                        elevation = 14.dp,
                                        backdropState = wallpaperBackdropState,
                                        blurRadius = 16.dp,
                                        refractionHeight = 10.dp,
                                        refractionAmount = 18.dp,
                                        surfaceTintOverride = popupGlassTint,
                                        surfaceAlphaOverride = if (hasLiveGlass) {
                                            if (colors.isDark) 0.56f else 0.72f
                                        } else {
                                            if (colors.isDark) 0.88f else 0.95f
                                        },
                                        showHighlight = false,
                                        classicMode = stealthConfig.classicWhatsAppLook,
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .border(
                                                    width = 0.55.dp,
                                                    color = if (colors.isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.45f),
                                                    shape = RoundedCornerShape(24.dp)
                                                )
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val extraRows = listOf(
                                                listOf("😍", "🔥", "👏", "🎉", "💯", "🤣"),
                                                listOf("🥳", "🤝", "🙌", "✨", "👀", "🫡"),
                                                listOf("😭", "😡", "👌", "✅", "💔", "🤔")
                                            )
                                            extraRows.forEach { rowEmojis ->
                                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    rowEmojis.forEach { emoji ->
                                                        val extraInteraction = remember(emoji) { MutableInteractionSource() }
                                                        Text(
                                                            text = emoji,
                                                            fontSize = 25.sp,
                                                            modifier = Modifier
                                                                .expressivePressScale(extraInteraction, pressedScale = 0.80f)
                                                                .clip(CircleShape)
                                                                .clickable(
                                                                    interactionSource = extraInteraction,
                                                                    indication = null
                                                                ) {
                                                                    runWithOutboundGuard {
                                                                        viewModel.sendReaction(chatJid, target, emoji)
                                                                    }
                                                                    actionMenuTarget = null
                                                                    showMoreReactionEmojis = false
                                                                    showSelectionOverflow = false
                                                                }
                                                                .padding(4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. Anchored Floating Translucent Liquid Glass Message Options Column
                            if (!showMoreReactionEmojis) {
                                val isTargetDoc = remember(
                                    target.id,
                                    target.msgType,
                                    target.mimeType,
                                    target.body,
                                    target.isViewOnce,
                                    target.isDeletedBySender
                                ) {
                                    !target.localMediaPath.isNullOrBlank() && isDocumentMessage(target)
                                }
                                val (primaryActions, moreActions) = remember(
                                    target.id,
                                    target.isFromMe,
                                    target.msgType,
                                    target.body,
                                    target.localMediaPath,
                                    target.isPinned,
                                    target.isEdited,
                                    isChannel,
                                    isGroupAnnounceLocked,
                                    isTargetDoc
                                ) {
                                    resolveFloatingContextActions(
                                        msg = target,
                                        isChannel = isChannel,
                                        isGroupAnnounceLocked = isGroupAnnounceLocked,
                                        isDocument = isTargetDoc
                                    )
                                }
                                val maxMenuHeightDp = with(overlayDensity) {
                                    (overlayH - topSafePx - bottomSafePx - estPillH - gapPx * 2)
                                        .coerceAtLeast(200.dp.roundToPx())
                                        .toDp()
                                }
                                val menuTextColor = if (colors.isDark) Color(0xFFF3F5F7) else colors.textPrimary
                                val menuDeleteRed = if (colors.isDark) Color(0xFFD92626) else Color(0xFFD32F2F)
                                val menuShape = RoundedCornerShape(28.dp)

                                Box(
                                    modifier = Modifier
                                        .offset { IntOffset(menuX, menuY) }
                                        .onSizeChanged { menuSizePx = it }
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {}
                                        .animateEnterExit(
                                            enter = scaleIn(
                                                animationSpec = reactionMotion.fastSpatialSpec(),
                                                initialScale = 0.88f
                                            ),
                                            exit = scaleOut(
                                                animationSpec = reactionMotion.fastEffectsSpec(),
                                                targetScale = 0.92f
                                            )
                                        )
                                ) {
                                    WuppGlassContainer(
                                        modifier = Modifier.width(196.dp),
                                        shape = menuShape,
                                        colors = colors,
                                        elevation = 16.dp,
                                        backdropState = wallpaperBackdropState,
                                        blurRadius = 18.dp,
                                        refractionHeight = 10.dp,
                                        refractionAmount = 20.dp,
                                        surfaceTintOverride = popupGlassTint,
                                        surfaceAlphaOverride = popupGlassAlpha,
                                        showHighlight = false,
                                        classicMode = stealthConfig.classicWhatsAppLook
                                    ) {
                                        val menuScrollState = rememberScrollState()
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = maxMenuHeightDp)
                                                .border(
                                                    width = 0.55.dp,
                                                    color = if (colors.isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.45f),
                                                    shape = menuShape
                                                )
                                                .verticalScroll(menuScrollState)
                                                .padding(vertical = 10.dp)
                                        ) {
                                            primaryActions.forEach { action ->
                                                val isDelete = action == SelectionBarAction.DELETE
                                                val itemTint = if (isDelete) menuDeleteRed else menuTextColor
                                                val itemLabel = if (action == SelectionBarAction.MESSAGE_INFO) {
                                                    "Info"
                                                } else {
                                                    labelForSingleAction(target, isTargetDoc, action)
                                                }
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            triggerSingleMessageAction(target, isTargetDoc, action)
                                                        }
                                                        .padding(start = 18.dp, end = 20.dp, top = 12.5.dp, bottom = 12.5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                                ) {
                                                    ContextMenuActionGlyph(
                                                        action = action,
                                                        tint = itemTint,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                    Text(
                                                        text = itemLabel,
                                                        color = itemTint,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Normal,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            // Expandable "More" row (Pin/Unpin, Save to Gallery, Open Doc, Edit History, Select)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { showSelectionOverflow = !showSelectionOverflow }
                                                    .padding(start = 18.dp, end = 20.dp, top = 12.5.dp, bottom = 12.5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                ContextMenuActionGlyph(
                                                    action = null,
                                                    tint = menuTextColor,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Text(
                                                    text = if (showSelectionOverflow) "Less" else "More",
                                                    color = menuTextColor,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Normal
                                                )
                                            }

                                            androidx.compose.animation.AnimatedVisibility(visible = showSelectionOverflow) {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    HorizontalDivider(
                                                        thickness = 0.5.dp,
                                                        color = if (colors.isDark) Color.White.copy(alpha = 0.12f) else colors.divider,
                                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                                    )
                                                    moreActions.forEach { action ->
                                                        val itemTint = if (action == SelectionBarAction.PIN_UNPIN && target.isPinned) {
                                                            colors.accentGreen
                                                        } else {
                                                            menuTextColor
                                                        }
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    triggerSingleMessageAction(target, isTargetDoc, action)
                                                                }
                                                                .padding(start = 18.dp, end = 20.dp, top = 11.5.dp, bottom = 11.5.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = iconForSingleAction(action),
                                                                contentDescription = null,
                                                                tint = itemTint,
                                                                modifier = Modifier.size(21.dp)
                                                            )
                                                            Text(
                                                                text = labelForSingleAction(target, isTargetDoc, action),
                                                                color = itemTint,
                                                                fontSize = 15.5.sp,
                                                                fontWeight = FontWeight.Normal,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
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
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = readReceiptBannerTitle != null && actionMenuTarget == null,
                        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 2 },
                        exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 2 }
                    ) {
                        val toastBg = if (colors.isDark) Color(0xED15181C) else Color(0xF5FFFFFF)
                        val toastBorder = if (colors.isDark) Color.White.copy(alpha = 0.11f) else Color.Black.copy(alpha = 0.08f)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(toastBg)
                                .border(0.65.dp, toastBorder, RoundedCornerShape(24.dp))
                                .padding(start = 10.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.wupp_logo),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                            )
                            Column {
                                Text(
                                    text = readReceiptBannerTitle.orEmpty(),
                                    color = colors.textPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Has read your message",
                                    color = colors.textSecondary,
                                    fontSize = 11.5.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                if (showScrollToBottom && actionMenuTarget == null) {
                    val scrollOrbInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 12.dp)
                            .size(38.dp)
                            .expressivePressScale(scrollOrbInteraction, pressedScale = 0.88f)
                            .clip(CircleShape)
                            .background(
                                if (colors.isDark) Color(0xD9202529) else Color(0xEBFFFFFF)
                            )
                            .clickable(
                                interactionSource = scrollOrbInteraction,
                                indication = null
                            ) { scope.launch { listState.animateScrollToItem(0) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardDoubleArrowDown,
                            contentDescription = "Scroll to latest message",
                            tint = colors.textPrimary.copy(alpha = 0.90f),
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { bottomOverlayPx = it.height }
                ) {
                if (isGroupAnnounceLocked && !isChannel) {
                    Surface(
                        color = colors.appSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Only admins can send messages",
                                color = colors.textSecondary,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                } else if (!isChannel) {

                // Live Voice Recording Elapsed Timer (ticks cleanly while active & not paused)
                var recordingElapsedSec by remember(chatJid) { mutableIntStateOf(0) }
                var voiceViewOnceEnabled by remember(chatJid) { mutableStateOf(false) }
                LaunchedEffect(isRecordingVoice) {
                    if (!isRecordingVoice) {
                        recordingElapsedSec = 0
                        voiceViewOnceEnabled = false
                    }
                }
                LaunchedEffect(isRecordingVoice, isVoiceRecordingPaused) {
                    if (isRecordingVoice && !isVoiceRecordingPaused) {
                        while (true) {
                            delay(1_000L)
                            recordingElapsedSec += 1
                        }
                    }
                }

                // Chat Composer Area:
                // - When recording: Full 2-row Voice Note Recorder card (Row 1: timer + tall live scrolling waveform + View Once toggle; Row 2: Red Trash, Pause/Resume pill, Green Send)
                // - Normal: (+) Attach left + Unified Capsule (Embedded Quote Preview w/ thumbnail + Message left + Emoji/Sticker inside right) + Camera & Mic (or Send) right
                AnimatedContent(
                    targetState = isRecordingVoice,
                    transitionSpec = { fadeIn(tween(170)) togetherWith fadeOut(tween(150)) },
                    label = "composer_voice_mode"
                ) { recording ->
                    if (recording) {
                        val infiniteWave = rememberInfiniteTransition(label = "voice_rec_wave")
                        val animatedWavePhase by infiniteWave.animateFloat(
                            initialValue = 0f,
                            targetValue = 6.2831855f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 1150, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "voice_rec_phase"
                        )
                        val animatedDotPulse by infiniteWave.animateFloat(
                            initialValue = 0.35f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 620, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "voice_rec_dot"
                        )
                        val wavePhase = if (isVoiceRecordingPaused) 0.8f else animatedWavePhase
                        val dotPulse = if (isVoiceRecordingPaused) 0.45f else animatedDotPulse
                        WuppGlassContainer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            colors = colors,
                            shape = RoundedCornerShape(24.dp),
                            elevation = 14.dp,
                            backdropState = wallpaperBackdropState,
                            classicMode = stealthConfig.classicWhatsAppLook
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Row 1: Pulsing dot + 18sp timer + tall 40dp live scrolling waveform + View Once toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(colors.antiDeleteRed.copy(alpha = dotPulse))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "%d:%02d".format(recordingElapsedSec / 60, recordingElapsedSec % 60),
                                        color = colors.textPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = LocalWaMonoFamily.current
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Canvas(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                    ) {
                                        val bars = 34
                                        val slotW = size.width / bars
                                        val barW = (slotW * 0.54f).coerceIn(2.2.dp.toPx(), 3.2.dp.toPx())
                                        val centerY = size.height / 2f
                                        val maxH = size.height * 0.86f
                                        for (i in 0 until bars) {
                                            val w1 = sin(wavePhase + i * 0.48f)
                                            val w2 = sin(wavePhase * 1.7f - i * 0.31f)
                                            val w3 = sin(wavePhase * 0.7f + i * 0.85f)
                                            val norm = ((w1 * 0.45f + w2 * 0.35f + w3 * 0.20f) + 1f) / 2f
                                            val barH = if (isVoiceRecordingPaused) {
                                                (maxH * (0.16f + norm * 0.28f)).coerceAtLeast(3.dp.toPx())
                                            } else {
                                                (maxH * (0.18f + norm * 0.82f)).coerceAtLeast(3.dp.toPx())
                                            }
                                            val x = i * slotW + (slotW - barW) / 2f
                                            drawRoundRect(
                                                color = if (isVoiceRecordingPaused) {
                                                    colors.textSecondary.copy(alpha = 0.42f)
                                                } else {
                                                    colors.textSecondary.copy(alpha = 0.45f + norm * 0.50f)
                                                },
                                                topLeft = Offset(x, centerY - barH / 2f),
                                                size = Size(barW, barH),
                                                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    val voVoiceInteraction = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .expressivePressScale(voVoiceInteraction, pressedScale = 0.88f)
                                            .clip(CircleShape)
                                            .background(
                                                if (voiceViewOnceEnabled) colors.brightGreen
                                                else Color.Transparent
                                            )
                                            .border(
                                                1.dp,
                                                if (voiceViewOnceEnabled) colors.brightGreen else colors.textSecondary.copy(alpha = 0.45f),
                                                CircleShape
                                            )
                                            .clickable(
                                                interactionSource = voVoiceInteraction,
                                                indication = null
                                            ) {
                                                voiceViewOnceEnabled = !voiceViewOnceEnabled
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.vec_ic_view_once),
                                            contentDescription = if (voiceViewOnceEnabled) "View once enabled" else "Enable view once",
                                            tint = if (voiceViewOnceEnabled) colors.brightGreen.contrastingContentColor() else colors.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Row 2: Red Trash (Discard) | Center Pause/Resume Pill | Green Send Orb
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val trashInteraction = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .expressivePressScale(trashInteraction, pressedScale = 0.88f)
                                            .clip(CircleShape)
                                            .background(colors.antiDeleteRed.copy(alpha = 0.14f))
                                            .clickable(
                                                interactionSource = trashInteraction,
                                                indication = null
                                            ) { viewModel.cancelVoiceRecording() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "Discard voice note",
                                            tint = colors.antiDeleteRed,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    val pauseInteraction = remember { MutableInteractionSource() }
                                    val pauseTint = if (isVoiceRecordingPaused) colors.brightGreen else colors.textPrimary
                                    val pauseBg = if (isVoiceRecordingPaused) {
                                        colors.brightGreen.copy(alpha = 0.15f)
                                    } else {
                                        colors.pillInactiveBg
                                    }
                                    Row(
                                        modifier = Modifier
                                            .height(42.dp)
                                            .expressivePressScale(pauseInteraction, pressedScale = 0.92f)
                                            .clip(CircleShape)
                                            .background(pauseBg)
                                            .border(
                                                1.dp,
                                                if (isVoiceRecordingPaused) colors.brightGreen.copy(alpha = 0.55f)
                                                else colors.textSecondary.copy(alpha = 0.25f),
                                                CircleShape
                                            )
                                            .clickable(
                                                interactionSource = pauseInteraction,
                                                indication = null
                                            ) { viewModel.pauseOrResumeVoiceRecording() }
                                            .padding(horizontal = 20.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isVoiceRecordingPaused) Icons.Rounded.Mic else Icons.Rounded.Pause,
                                            contentDescription = if (isVoiceRecordingPaused) "Resume recording" else "Pause recording",
                                            tint = if (isVoiceRecordingPaused) colors.brightGreen else colors.antiDeleteRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = if (isVoiceRecordingPaused) "Resume" else "Pause",
                                            color = pauseTint,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    val sendVoiceInteraction = remember { MutableInteractionSource() }
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .expressivePressScale(sendVoiceInteraction, pressedScale = 0.88f)
                                            .clip(CircleShape)
                                            .background(colors.brightGreen)
                                            .clickable(
                                                interactionSource = sendVoiceInteraction,
                                                indication = null
                                            ) {
                                                scrollToBottomPending = true
                                                viewModel.toggleVoiceRecording(
                                                    chatJid = chatJid,
                                                    hasRecordPermission = true,
                                                    allowUnsavedColdStart = allowUnsavedColdStartFlag,
                                                    isViewOnce = voiceViewOnceEnabled
                                                )
                                                scope.launch { listState.animateScrollToItem(0) }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Send,
                                            contentDescription = "Send voice note",
                                            tint = colors.brightGreen.contrastingContentColor(),
                                            modifier = Modifier.size(21.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val hasDraftText = draftValue.text.isNotBlank()
                        WuppGlassBottomBar(
                            backdropState = wallpaperBackdropState,
                            classicMode = stealthConfig.classicWhatsAppLook,
                            fadeBottom = !showAttachTray && !showEmojiPicker
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Far Left: Borderless (+) / Keyboard toggle directly on frosted bar
                                val attachInteraction = remember { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .padding(bottom = 1.dp)
                                        .size(38.dp)
                                        .expressivePressScale(attachInteraction, pressedScale = 0.88f)
                                        .clip(CircleShape)
                                        .clickable(
                                            interactionSource = attachInteraction,
                                            indication = null
                                        ) {
                                            if (showAttachTray) {
                                                showAttachTray = false
                                                draftFocusRequester.requestFocus()
                                                keyboardController?.show()
                                            } else {
                                                showEmojiPicker = false
                                                holdDockForIme = false
                                                keyboardController?.hide()
                                                showAttachTray = true
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (showAttachTray) Icons.Rounded.Keyboard else Icons.Rounded.Add,
                                        contentDescription = if (showAttachTray) "Show keyboard" else "Attach",
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(if (showAttachTray) 24.dp else 28.dp)
                                    )
                                }

                                // Middle: Smooth Borderless Pill Input (Embedded Quoted Reply + Message + Folded Sticker Icon)
                                val pillShape = RoundedCornerShape(if (replyTarget != null) 20.dp else 22.dp)
                                val pillBg = if (colors.isDark) {
                                    Color(0xFF262B30).copy(alpha = 0.80f)
                                } else {
                                    Color(0xFFE9EDF0)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(pillShape)
                                        .background(pillBg)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // Embedded Quoted Reply Preview inside top of capsule
                                        androidx.compose.animation.AnimatedVisibility(visible = replyTarget != null) {
                                            replyTarget?.let { rep ->
                                                val repTypeUpper = rep.msgType.uppercase()
                                                val repHasThumb = !rep.localMediaPath.isNullOrBlank() &&
                                                    (repTypeUpper == "IMAGE" || repTypeUpper == "VIDEO" || repTypeUpper == "GIF" || repTypeUpper == "STICKER")
                                                val repThumbBmp by produceState<ImageBitmap?>(
                                                    initialValue = null,
                                                    key1 = rep.localMediaPath,
                                                    key2 = repTypeUpper
                                                ) {
                                                    value = if (repHasThumb) {
                                                        withContext(Dispatchers.IO) {
                                                            decodeQuotedThumbnailBitmap(rep.localMediaPath!!, repTypeUpper == "VIDEO")
                                                        }
                                                    } else null
                                                }
                                                val replyPreviewText = rep.body.ifBlank {
                                                    when (repTypeUpper) {
                                                        "IMAGE" -> "Photo"
                                                        "VIDEO" -> "Video"
                                                        "PTT", "AUDIO" -> "Voice message"
                                                        "DOCUMENT" -> "Document"
                                                        "STICKER" -> "Sticker"
                                                        "GIF" -> "GIF"
                                                        else -> rep.msgType
                                                    }
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 2.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(IntrinsicSize.Min)
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(colors.quotedBg),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .width(3.5.dp)
                                                                .fillMaxHeight()
                                                                .background(colors.brightGreen)
                                                        )
                                                        Column(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .padding(start = 9.dp, end = 28.dp, top = 6.dp, bottom = 6.dp),
                                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                                        ) {
                                                            Text(
                                                                text = if (rep.isFromMe) "You" else rep.senderName.ifBlank { rep.senderJid.substringBefore("@") },
                                                                color = colors.brightGreen,
                                                                fontWeight = FontWeight.SemiBold,
                                                                fontSize = 12.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = replyPreviewText,
                                                                color = colors.textSecondary,
                                                                fontSize = 12.5.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        if (repThumbBmp != null) {
                                                            Image(
                                                                bitmap = repThumbBmp!!,
                                                                contentDescription = replyPreviewText,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier
                                                                    .width(50.dp)
                                                                    .fillMaxHeight()
                                                                    .sizeIn(minHeight = 50.dp)
                                                            )
                                                        }
                                                    }
                                                    // Top-right translucent circular close badge
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(4.dp)
                                                            .size(21.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.Black.copy(alpha = 0.56f))
                                                            .clickable { replyTarget = null },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Close,
                                                            contentDescription = "Cancel Reply",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Text Entry Row: Message input LEFT + Sticker/Emoji icon INSIDE RIGHT
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 40.dp)
                                                .padding(start = 14.dp, end = 5.dp, top = 2.dp, bottom = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (ghostMentionEveryone && chat?.isGroup == true) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(colors.pillActiveBg)
                                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                                ) {
                                                    Text("@all", color = colors.pillActiveText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Box(modifier = Modifier.weight(1f).padding(vertical = 5.dp)) {
                                                if (draftValue.text.isEmpty()) {
                                                    Text(
                                                        text = stringResource(R.string.chat_input_placeholder),
                                                        color = colors.textSecondary,
                                                        fontSize = 16.sp
                                                    )
                                                }
                                                val sendCurrentDraft: () -> Unit = {
                                                    if (draftValue.text.isNotBlank()) {
                                                        runWithOutboundGuard {
                                                            scrollToBottomPending = true
                                                            viewModel.sendText(
                                                                chatJid = chatJid,
                                                                text = draftValue.text,
                                                                quoted = replyTarget,
                                                                ghostMentionEveryone = ghostMentionEveryone,
                                                                allowUnsavedColdStart = allowUnsavedColdStartFlag
                                                            )
                                                            draftValue = TextFieldValue("")
                                                            viewModel.saveChatDraft(chatJid, "")
                                                            replyTarget = null
                                                            ghostMentionEveryone = false
                                                            scope.launch { listState.animateScrollToItem(0) }
                                                        }
                                                    }
                                                }
                                                BasicTextField(
                                                    value = draftValue,
                                                    onValueChange = { draftValue = it },
                                                    enabled = true,
                                                    interactionSource = draftInteractionSource,
                                                    textStyle = TextStyle(
                                                        color = colors.textPrimary,
                                                        fontSize = 16.sp,
                                                        lineHeight = 20.5.sp
                                                    ),
                                                    cursorBrush = SolidColor(colors.brightGreen),
                                                    maxLines = 6,
                                                    keyboardOptions = KeyboardOptions(
                                                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                                                        imeAction = if (stealthConfig.enterIsSend) ImeAction.Send else ImeAction.Default
                                                    ),
                                                    keyboardActions = KeyboardActions(
                                                        onSend = { sendCurrentDraft() }
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .focusRequester(draftFocusRequester)
                                                )
                                            }
                                            IconButton(
                                                enabled = canSendOutbound,
                                                onClick = {
                                                    if (showEmojiPicker) {
                                                        holdDockForIme = true
                                                        showEmojiPicker = false
                                                        draftFocusRequester.requestFocus()
                                                        keyboardController?.show()
                                                    } else {
                                                        showAttachTray = false
                                                        holdDockForIme = false
                                                        showEmojiPicker = true
                                                        keyboardController?.hide()
                                                    }
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                if (showEmojiPicker) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Keyboard,
                                                        contentDescription = stringResource(R.string.chat_switch_to_keyboard),
                                                        tint = colors.brightGreen,
                                                        modifier = Modifier.size(21.dp)
                                                    )
                                                } else {
                                                    ComposerStickerIcon(
                                                        tint = colors.textPrimary.copy(alpha = 0.86f),
                                                        modifier = Modifier.size(21.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Far Right: Borderless Camera + Mic when empty, Green Send circle when typing
                                val fabMotion = MaterialTheme.motionScheme
                                AnimatedContent(
                                    targetState = hasDraftText,
                                    transitionSpec = {
                                        (scaleIn(
                                            animationSpec = fabMotion.fastSpatialSpec(),
                                            initialScale = 0.78f
                                        ) + fadeIn(fabMotion.fastEffectsSpec())) togetherWith
                                            (scaleOut(
                                                animationSpec = fabMotion.fastEffectsSpec(),
                                                targetScale = 0.78f
                                            ) + fadeOut(fabMotion.fastEffectsSpec()))
                                    },
                                    label = "composer_right_actions"
                                ) { typing ->
                                    if (typing) {
                                        val sendInteraction = remember { MutableInteractionSource() }
                                        Box(
                                            modifier = Modifier
                                                .padding(bottom = 1.dp)
                                                .size(38.dp)
                                                .expressivePressScale(sendInteraction, pressedScale = 0.88f)
                                                .clip(CircleShape)
                                                .background(if (canSendOutbound) colors.brightGreen else colors.textSecondary)
                                                .combinedClickable(
                                                    enabled = canSendOutbound,
                                                    interactionSource = sendInteraction,
                                                    indication = null,
                                                    onClick = {
                                                        runWithOutboundGuard {
                                                            scrollToBottomPending = true
                                                            viewModel.sendText(
                                                                chatJid = chatJid,
                                                                text = draftValue.text,
                                                                quoted = replyTarget,
                                                                ghostMentionEveryone = ghostMentionEveryone,
                                                                allowUnsavedColdStart = allowUnsavedColdStartFlag
                                                            )
                                                            draftValue = TextFieldValue("")
                                                            viewModel.saveChatDraft(chatJid, "")
                                                            replyTarget = null
                                                            ghostMentionEveryone = false
                                                            scope.launch { listState.animateScrollToItem(0) }
                                                        }
                                                    },
                                                    onLongClick = {
                                                        editingScheduledMessage = null
                                                        showScheduleDialog = true
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                                contentDescription = "Send",
                                                tint = if (canSendOutbound) colors.brightGreen.contrastingContentColor() else Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            modifier = Modifier.padding(bottom = 1.dp)
                                        ) {
                                            val camInteraction = remember { MutableInteractionSource() }
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .expressivePressScale(camInteraction, pressedScale = 0.88f)
                                                    .clip(CircleShape)
                                                    .clickable(
                                                        interactionSource = camInteraction,
                                                        indication = null,
                                                        onClick = { openCameraOrRequestPermission() }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.PhotoCamera,
                                                    contentDescription = "Take photo",
                                                    tint = colors.textPrimary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            val micInteraction = remember { MutableInteractionSource() }
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .expressivePressScale(micInteraction, pressedScale = 0.88f)
                                                    .clip(CircleShape)
                                                    .combinedClickable(
                                                        enabled = canSendOutbound,
                                                        interactionSource = micInteraction,
                                                        indication = null,
                                                        onClick = {
                                                            val hasMic = ContextCompat.checkSelfPermission(
                                                                context,
                                                                Manifest.permission.RECORD_AUDIO
                                                            ) == PackageManager.PERMISSION_GRANTED
                                                            if (hasMic) {
                                                                runWithOutboundGuard {
                                                                    viewModel.toggleVoiceRecording(
                                                                        chatJid,
                                                                        true,
                                                                        allowUnsavedColdStart = allowUnsavedColdStartFlag
                                                                    )
                                                                }
                                                            } else {
                                                                micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                            }
                                                        },
                                                        onLongClick = {
                                                            editingScheduledMessage = null
                                                            showScheduleDialog = true
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Mic,
                                                    contentDescription = "Record Voice Note",
                                                    tint = if (canSendOutbound) colors.textPrimary else colors.textSecondary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Sleek Dark Attachment Sheet docked directly beneath the composer
                val trayMotion = MaterialTheme.motionScheme
                androidx.compose.animation.AnimatedVisibility(
                    visible = showAttachTray && !isRecordingVoice,
                    enter = fadeIn(trayMotion.fastEffectsSpec()) + expandVertically(trayMotion.fastSpatialSpec()),
                    exit = fadeOut(trayMotion.fastEffectsSpec()) + shrinkVertically(trayMotion.fastSpatialSpec())
                ) {
                    val sheetBg = if (colors.isDark) Color(0xFF0A0C0E) else colors.appSurface
                    val tileBg = if (colors.isDark) Color(0xFF161A1D) else Color(0xFFF0F3F6)
                    val tileBorder = if (colors.isDark) Color(0xFF23282D) else Color(0xFFE1E6EB)
                    val labelColor = if (colors.isDark) Color(0xFFB8C0C7) else colors.textSecondary

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(sheetBg)
                            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 22.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        // Top Row: 3 sleek rounded capsule quick-tool pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Pill 1: Schedule message
                            ComposerQuickPill(
                                icon = Icons.Rounded.Schedule,
                                iconTint = Color(0xFF38BDF8),
                                label = "Schedule\nmessage",
                                pillBg = tileBg,
                                pillBorder = tileBorder,
                                labelColor = labelColor,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    showAttachTray = false
                                    editingScheduledMessage = null
                                    showScheduleDialog = true
                                }
                            )
                            // Pill 2: Media Quality toggle (HD / Original / Standard)
                            val qualityShort = when (qualityMode) {
                                OutboundQualityMode.HD -> "HD Quality"
                                OutboundQualityMode.ORIGINAL -> "Original\nLossless"
                                OutboundQualityMode.STANDARD -> "Standard\nFast"
                                else -> qualityMode.label
                            }
                            val qualityTint = when (qualityMode) {
                                OutboundQualityMode.HD -> Color(0xFF22C55E)
                                OutboundQualityMode.ORIGINAL -> Color(0xFFFACC15)
                                else -> Color(0xFF94A3B8)
                            }
                            ComposerQuickPill(
                                icon = Icons.Rounded.HighQuality,
                                iconTint = qualityTint,
                                label = qualityShort,
                                pillBg = tileBg,
                                pillBorder = if (qualityMode != OutboundQualityMode.STANDARD) qualityTint.copy(alpha = 0.45f) else tileBorder,
                                labelColor = labelColor,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    qualityMode = when (qualityMode) {
                                        OutboundQualityMode.HD -> OutboundQualityMode.ORIGINAL
                                        OutboundQualityMode.ORIGINAL -> OutboundQualityMode.STANDARD
                                        else -> OutboundQualityMode.HD
                                    }
                                    Toast.makeText(context, "Media quality: ${qualityMode.label}", Toast.LENGTH_SHORT).show()
                                }
                            )
                            // Pill 3: Group @everyone silent mention OR Scheduled list
                            if (chat?.isGroup == true) {
                                ComposerQuickPill(
                                    icon = Icons.Rounded.AlternateEmail,
                                    iconTint = if (ghostMentionEveryone) Color(0xFF22C55E) else Color(0xFFEC4899),
                                    label = if (ghostMentionEveryone) "@everyone\nActive" else "@everyone\nGhost",
                                    pillBg = tileBg,
                                    pillBorder = if (ghostMentionEveryone) Color(0xFF22C55E).copy(alpha = 0.50f) else tileBorder,
                                    labelColor = labelColor,
                                    modifier = Modifier.weight(1f),
                                    onClick = { ghostMentionEveryone = !ghostMentionEveryone }
                                )
                            } else {
                                ComposerQuickPill(
                                    icon = Icons.Rounded.EventNote,
                                    iconTint = Color(0xFFEC4899),
                                    label = "Scheduled\nqueue",
                                    pillBg = tileBg,
                                    pillBorder = tileBorder,
                                    labelColor = labelColor,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        showAttachTray = false
                                        showScheduledSheet = true
                                    }
                                )
                            }
                        }

                        // Main 3x3 Rounded-Square Attachment Grid (balanced symmetry + Location sharing)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ComposerAttachTile(
                                icon = Icons.Rounded.Collections,
                                iconTint = Color(0xFF22C55E),
                                label = "Gallery",
                                tileBg = tileBg,
                                tileBorder = tileBorder,
                                labelColor = labelColor,
                                onClick = {
                                    showAttachTray = false
                                    AppLockManager.suspendNextBackgroundLock()
                                    pickVisualMediaLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                }
                            )
                            ComposerAttachTile(
                                icon = Icons.Rounded.PhotoCamera,
                                iconTint = Color(0xFFF43F5E),
                                label = "Camera",
                                tileBg = tileBg,
                                tileBorder = tileBorder,
                                labelColor = labelColor,
                                onClick = { openCameraOrRequestPermission() }
                            )
                            ComposerAttachTile(
                                icon = Icons.Rounded.Description,
                                iconTint = Color(0xFF7C5CFF),
                                label = "Document",
                                tileBg = tileBg,
                                tileBorder = tileBorder,
                                labelColor = labelColor,
                                onClick = {
                                    showAttachTray = false
                                    AppLockManager.suspendNextBackgroundLock()
                                    pickMediaLauncher.launch("*/*")
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ComposerAttachTile(
                                icon = Icons.Rounded.Person,
                                iconTint = Color(0xFF14B8A6),
                                label = "Contact",
                                tileBg = tileBg,
                                tileBorder = tileBorder,
                                labelColor = labelColor,
                                onClick = {
                                    showAttachTray = false
                                    showInAppContactPicker = true
                                }
                            )
                            ComposerAttachTile(
                                icon = Icons.Rounded.Headphones,
                                iconTint = Color(0xFFF97316),
                                label = "Audio",
                                tileBg = tileBg,
                                tileBorder = tileBorder,
                                labelColor = labelColor,
                                onClick = {
                                    showAttachTray = false
                                    AppLockManager.suspendNextBackgroundLock()
                                    pickAudioLauncher.launch("audio/*")
                                }
                            )
                            ComposerAttachTile(
                                icon = Icons.Rounded.LocationOn,
                                iconTint = Color(0xFF10B981),
                                label = "Location",
                                tileBg = tileBg,
                                tileBorder = tileBorder,
                                labelColor = labelColor,
                                onClick = {
                                    showAttachTray = false
                                    showShareLocationSheet = true
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ComposerAttachTile(
                                icon = Icons.Rounded.EmojiEmotions,
                                iconTint = Color(0xFFFACC15),
                                label = "Stickers",
                                tileBg = tileBg,
                                tileBorder = tileBorder,
                                labelColor = labelColor,
                                onClick = {
                                    showAttachTray = false
                                    holdDockForIme = false
                                    keyboardController?.hide()
                                    showEmojiPicker = true
                                }
                            )
                            ComposerAttachTile(
                                icon = Icons.Rounded.Wallpaper,
                                iconTint = Color(0xFF38BDF8),
                                label = "Wallpaper",
                                tileBg = tileBg,
                                tileBorder = tileBorder,
                                labelColor = labelColor,
                                onClick = {
                                    showAttachTray = false
                                    showWallpaperDialog = true
                                }
                            )
                            ComposerAttachTile(
                                icon = Icons.Rounded.Shield,
                                iconTint = Color(0xFFEC4899),
                                label = "Privacy",
                                tileBg = tileBg,
                                tileBorder = tileBorder,
                                labelColor = labelColor,
                                onClick = {
                                    showAttachTray = false
                                    showGhostOverrideSheet = true
                                }
                            )
                        }
                    }
                }

                // Native Keyboard-Height Emoji & Sticker Dock (with WhatsApp vertical stretch expansion)
                val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
                val standardDockDp = minOf(rememberedKeyboardDp, maxLandscapeDockDp)
                val expandedDockDp = (screenHeightDp * 0.72f).coerceIn(460.dp, 640.dp)
                val desiredDockDp = if (isDockExpanded) expandedDockDp else standardDockDp
                val targetDockDp = if (showEmojiPicker || holdDockForIme) {
                    (desiredDockDp - imeExclusiveDp).coerceAtLeast(0.dp)
                } else {
                    0.dp
                }
                val animatedDockDp by animateDpAsState(
                    targetValue = targetDockDp,
                    animationSpec = tween(
                        durationMillis = if (imeExclusivePx > 0 || holdDockForIme) 0 else 200
                    ),
                    label = "emoji_dock_height"
                )
                if (showEmojiPicker || animatedDockDp > 0.dp) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(animatedDockDp)
                            .clipToBounds()
                    ) {
                        if (showEmojiPicker) {
                            ChatEmojiPickerDock(
                                dockHeight = desiredDockDp,
                                colors = colors,
                                isExpanded = isDockExpanded,
                                onToggleExpand = { isDockExpanded = !isDockExpanded },
                                onExpand = { isDockExpanded = true },
                                onCollapse = { isDockExpanded = false },
                                onDismiss = {
                                    showEmojiPicker = false
                                    isDockExpanded = false
                                },
                                onEmojiSelected = { emoji ->
                                    draftValue = insertTextAtSelection(draftValue, emoji)
                                },
                                onBackspace = {
                                    draftValue = deleteBeforeSelection(draftValue)
                                },
                                recentStickers = recentStickers,
                                favoriteStickers = favoriteStickers,
                                favoriteStickerKeys = favoriteStickerKeys,
                                cuppyStickers = cuppyStickers,
                                customStickers = customStickers,
                                onStickerSelected = { stickerMsg ->
                                    val path = stickerMsg.localMediaPath
                                    if (!path.isNullOrBlank()) {
                                        val file = File(path)
                                        if (file.exists() && file.length() > 0L) {
                                            runWithOutboundGuard {
                                                viewModel.sendSticker(chatJid, file, allowUnsavedColdStart = allowUnsavedColdStartFlag)
                                            }
                                        }
                                    }
                                },
                                onSendBuiltinSticker = { builtinSticker ->
                                    runWithOutboundGuard {
                                        viewModel.sendSticker(chatJid, builtinSticker.file, allowUnsavedColdStart = allowUnsavedColdStartFlag)
                                    }
                                },
                                onSendCustomSticker = { customSticker ->
                                    val file = File(customSticker.filePath)
                                    if (file.exists() && file.length() > 0L) {
                                        runWithOutboundGuard {
                                            viewModel.sendSticker(chatJid, file, allowUnsavedColdStart = allowUnsavedColdStartFlag)
                                        }
                                    }
                                },
                                onDeleteCustomSticker = { customSticker ->
                                    viewModel.deleteCustomSticker(customSticker.id)
                                },
                                onToggleFavorite = { stickerMsg ->
                                    viewModel.toggleFavoriteSticker(stickerMsg)
                                },
                                onCreateSticker = {
                                    showStickerStudioDialog = true
                                }
                            )
                        }
                    }
                }
                }
            }
            }
        }
    }

    // Per-Chat Quick Controls & Privacy Sheet
    if (showGhostOverrideSheet) {
        val chatSavedCount = remember(deletedHistory, editedHistory, chatJid) {
            (deletedHistory.asSequence().filter { it.chatJid == chatJid }.map { it.id } +
                editedHistory.asSequence().filter { it.chatJid == chatJid }.map { it.id }).toSet().size
        }
        PerChatQuickControlsSheet(
            chat = chat,
            chatJid = chatJid,
            effectivePrivacy = effectivePrivacy,
            stealthConfig = stealthConfig,
            savedHistoryCount = chatSavedCount,
            onSavePrivacyOverride = { sendRead, sendPlayed, sendTyping ->
                viewModel.saveChatPrivacyOverride(chatJid, sendRead, sendPlayed, sendTyping)
            },
            onClearPrivacyOverride = { viewModel.clearChatPrivacyOverride(chatJid) },
            onSetGalleryVisibility = { overrideVisible ->
                viewModel.setChatMediaVisibility(chatJid, overrideVisible)
            },
            onTogglePin = { chat?.let { viewModel.togglePinChat(it) } },
            onToggleMute = { chat?.let { viewModel.muteChats(listOf(it.jid), !it.isMuted) } },
            onToggleFavorite = { chat?.let { viewModel.toggleFavoriteChat(it) } },
            onToggleLock = { chat?.let { viewModel.setChatLock(it.jid, !it.isLocked) } },
            onOpenWallpaper = { showWallpaperDialog = true },
            onOpenChatHistory = { showChatHistorySheet = true },
            onDismiss = { showGhostOverrideSheet = false }
        )
    }

    if (showStickerStudioDialog) {
        StickerStudioDialog(
            canSendNow = canSendOutbound,
            onSaveToPack = { bmp, label -> viewModel.saveCustomSticker(bmp, label) },
            onSaveAndSend = { bmp, label ->
                runWithOutboundGuard {
                    viewModel.saveCustomStickerAndSend(
                        chatJid = chatJid,
                        bitmap = bmp,
                        label = label,
                        allowUnsavedColdStart = allowUnsavedColdStartFlag
                    )
                }
            },
            onDismiss = { showStickerStudioDialog = false }
        )
    }

    activeStickerActionMsg?.let { stickerMsg ->
        val file = stickerMsg.localMediaPath?.let { File(it) }?.takeIf { it.exists() }
        val isFav = (stickerMsg.mediaSha256 != null && favoriteStickerKeys.contains(stickerMsg.mediaSha256)) ||
            (stickerMsg.localMediaPath != null && favoriteStickerKeys.contains(stickerMsg.localMediaPath)) ||
            favoriteStickerKeys.contains(stickerMsg.id)

        AlertDialog(
            onDismissRequest = { activeStickerActionMsg = null },
            title = {
                Text(
                    text = "Sticker",
                    color = colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (file != null) {
                        AnimatedStickerImage(
                            file = file,
                            contentDescription = "Sticker Preview",
                            maxDecodeDimPx = 512,
                            modifier = Modifier.size(160.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            viewModel.toggleFavoriteSticker(stickerMsg)
                            activeStickerActionMsg = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.pillActiveBg,
                            contentColor = colors.brightGreen
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = null,
                            tint = if (isFav) colors.brightGreen else colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isFav) "Remove from Favorites" else "Add to Favorites",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeStickerActionMsg = null }) {
                    Text("Close", color = colors.accentGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    if (showChatHistorySheet) {
        MessageHistorySheet(
            deletedMessages = deletedHistory,
            editedMessages = editedHistory,
            allEdits = allEditsHistory,
            chats = allChats,
            initialChatJid = chatJid,
            onJumpToChat = { targetJid ->
                if (targetJid != chatJid) onOpenContactInfo(targetJid)
            },
            onDismiss = { showChatHistorySheet = false }
        )
    }

    if (showWallpaperDialog) {
        val chatTitle = chat?.name?.takeIf { it.isNotBlank() } ?: chatJid.substringBefore("@")
        WallpaperPickerDialog(
            currentUri = stealthConfig.chatWallpaperUri,
            currentDim = stealthConfig.chatWallpaperDim,
            showDoodleOnPresets = stealthConfig.chatWallpaperShowDoodle,
            currentScale = stealthConfig.chatWallpaperScale,
            currentOffsetX = stealthConfig.chatWallpaperOffsetX,
            currentOffsetY = stealthConfig.chatWallpaperOffsetY,
            onSelectUri = { viewModel.setChatWallpaper(it) },
            onPickCustomImage = { viewModel.importChatWallpaperFromUri(it) },
            onDimChange = { viewModel.setChatWallpaperDim(it) },
            onToggleDoodle = { viewModel.setChatWallpaperShowDoodle(it) },
            onPositionChange = { scale, ox, oy -> viewModel.setChatWallpaperPosition(scale, ox, oy) },
            onDismiss = { showWallpaperDialog = false },
            chatTitle = chatTitle,
            hasChatOverride = hasChatWallpaperOverride,
            chatUri = effectiveWallpaper.uri,
            chatDim = effectiveWallpaper.dim,
            chatShowDoodle = effectiveWallpaper.showDoodle,
            chatScale = effectiveWallpaper.scale,
            chatOffsetX = effectiveWallpaper.offsetXFraction,
            chatOffsetY = effectiveWallpaper.offsetYFraction,
            onSelectChatUri = { viewModel.setPerChatWallpaper(chatJid, it) },
            onPickChatCustomImage = { viewModel.importPerChatWallpaperFromUri(chatJid, it) },
            onChatDimChange = { viewModel.setPerChatWallpaperDim(chatJid, it) },
            onToggleChatDoodle = { viewModel.setPerChatWallpaperShowDoodle(chatJid, it) },
            onChatPositionChange = { scale, ox, oy -> viewModel.setPerChatWallpaperPosition(chatJid, scale, ox, oy) },
            onResetChatToGlobal = { viewModel.clearPerChatWallpaper(chatJid) }
        )
    }

    if (showScheduleDialog) {
        val resolvedTitle = chat?.name?.takeIf { it.isNotBlank() } ?: chatJid.substringBefore("@")
        ScheduleMessageDialog(
            chatJid = chatJid,
            chatTitle = resolvedTitle,
            initialMessage = editingScheduledMessage,
            initialText = if (editingScheduledMessage == null) draftValue.text else "",
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
                    if (draftValue.text.isNotBlank() && draftValue.text == msg.text) {
                        draftValue = androidx.compose.ui.text.input.TextFieldValue("")
                    }
                }
                showScheduleDialog = false
                editingScheduledMessage = null
            }
        )
    }

    if (showScheduledSheet) {
        val resolvedTitle = chat?.name?.takeIf { it.isNotBlank() } ?: chatJid.substringBefore("@")
        ScheduledMessagesSheet(
            filterChatJid = chatJid,
            filterChatTitle = resolvedTitle,
            colors = colors,
            onDismiss = { showScheduledSheet = false },
            onOpenComposer = { _, _ ->
                editingScheduledMessage = null
                showScheduleDialog = true
            },
            onEditMessage = { msg ->
                editingScheduledMessage = msg
                showScheduleDialog = true
            },
            onPauseMessage = { viewModel.pauseScheduledMessage(it) },
            onResumeMessage = { viewModel.resumeScheduledMessage(it) },
            onDeleteMessage = { viewModel.deleteScheduledMessage(it) },
            onRetryMessage = { viewModel.retryScheduledMessage(it) }
        )
    }

    if (showInAppContactPicker) {
        val allContactsList by viewModel.allContacts.collectAsState(initial = emptyList())
        val allChatsList by viewModel.allChats.collectAsState(initial = emptyList())
        val bridgeStatus by viewModel.bridgeStatus.collectAsState()
        InAppContactPickerSheet(
            colors = colors,
            contacts = allContactsList,
            chats = allChatsList,
            ownJid = bridgeStatus.ownJid,
            onRequestPermission = {
                contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            },
            onDismiss = { showInAppContactPicker = false },
            onContactSelected = { preview ->
                showInAppContactPicker = false
                pendingShareContact = preview
            }
        )
    }

    pendingShareContact?.let { contact ->
        SendContactPreviewDialog(
            contact = contact,
            colors = colors,
            onDismiss = { pendingShareContact = null },
            onSend = { confirmedContact ->
                pendingShareContact = null
                runWithOutboundGuard {
                    scrollToBottomPending = true
                    val phones = if (confirmedContact.allPhones.isNotEmpty()) {
                        val chosen = confirmedContact.allPhones.firstOrNull { it.first == confirmedContact.phoneNumber }
                        val rest = confirmedContact.allPhones.filter { it.first != confirmedContact.phoneNumber }
                        if (chosen != null) listOf(chosen) + rest else confirmedContact.allPhones
                    } else if (confirmedContact.phoneNumber.isNotBlank()) {
                        listOf(confirmedContact.phoneNumber to confirmedContact.phoneTypeLabel)
                    } else {
                        emptyList()
                    }
                    val vcard = ContactSharingHelper.buildVCard(confirmedContact.displayName, phones)
                    viewModel.sendContact(
                        chatJid = chatJid,
                        displayName = confirmedContact.displayName,
                        phoneNumber = confirmedContact.phoneNumber,
                        vcard = vcard,
                        quoted = replyTarget,
                        allowUnsavedColdStart = allowUnsavedColdStartFlag
                    )
                    replyTarget = null
                    draftValue = androidx.compose.ui.text.input.TextFieldValue("")
                    scope.launch { listState.animateScrollToItem(0) }
                }
            }
        )
    }

    // Message Reactions Details Sheet ("Who reacted" — 100% local Room DB, zero extra server calls)
    reactionDetailsTarget?.let { target ->
        val storedReactions by remember(target.id) {
            viewModel.observeReactionsFor(target.id)
        }.collectAsState(initial = emptyList())
        val effectiveReactions = remember(storedReactions, target.reactionSummary) {
            if (storedReactions.isNotEmpty()) {
                storedReactions
            } else {
                target.reactionSummary
                    .split(Regex("\\s+"))
                    .filter { it.isNotBlank() }
                    .mapIndexed { idx, emoji ->
                        MessageReactionEntity(
                            messageId = target.id,
                            actor = "${MessageReactionEntity.LEGACY_PREFIX}$idx:$emoji",
                            emoji = emoji,
                            updatedAt = 0L
                        )
                    }
            }
        }
        val groupedCounts = remember(effectiveReactions) {
            effectiveReactions.groupingBy { it.emoji }.eachCount().entries.toList()
        }
        var selectedEmojiFilter by remember(target.id) { mutableStateOf<String?>(null) }
        var showSheetQuickReact by remember(target.id) { mutableStateOf(false) }
        val visibleReactions = remember(effectiveReactions, selectedEmojiFilter) {
            val f = selectedEmojiFilter
            if (f == null) effectiveReactions else effectiveReactions.filter { it.emoji == f }
        }
        LaunchedEffect(effectiveReactions.size) {
            if (selectedEmojiFilter != null && effectiveReactions.none { it.emoji == selectedEmojiFilter }) {
                selectedEmojiFilter = null
            }
        }
        val sheetBg = if (colors.isDark) Color(0xFF121518) else colors.elevatedBar
        val chipBg = if (colors.isDark) Color(0xFF252A2E) else colors.pillInactiveBg
        val chipSelectedBg = if (colors.isDark) Color(0xFF353C42) else colors.pillActiveBg

        ModalBottomSheet(
            onDismissRequest = { reactionDetailsTarget = null },
            containerColor = sheetBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val totalCount = effectiveReactions.size
                Text(
                    text = if (totalCount == 1) "1 reaction" else "$totalCount reactions",
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    fontSize = 18.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick-react toggle chip (add or switch your own reaction)
                    if (!isChannel && !isGroupAnnounceLocked) {
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .widthIn(min = 58.dp)
                                .clip(CircleShape)
                                .background(if (showSheetQuickReact) chipSelectedBg else chipBg)
                                .clickable { showSheetQuickReact = !showSheetQuickReact }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddReaction,
                                contentDescription = "Add reaction",
                                tint = colors.textPrimary.copy(alpha = 0.88f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (groupedCounts.size > 1) {
                        val isAll = selectedEmojiFilter == null
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(CircleShape)
                                .background(if (isAll) chipSelectedBg else chipBg)
                                .clickable { selectedEmojiFilter = null }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "All $totalCount",
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (isAll) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }

                    groupedCounts.forEach { (emoji, count) ->
                        val selected = selectedEmojiFilter == emoji || (groupedCounts.size == 1 && selectedEmojiFilter == null)
                        Row(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(CircleShape)
                                .background(if (selected) chipSelectedBg else chipBg)
                                .clickable {
                                    selectedEmojiFilter = if (selectedEmojiFilter == emoji) null else emoji
                                }
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = emoji, fontSize = 17.sp)
                            Text(
                                text = count.toString(),
                                color = colors.textSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(visible = showSheetQuickReact) {
                    val quickList = stealthConfig.quickReactionEmojis.ifEmpty {
                        StealthMatrixManager.DEFAULT_QUICK_REACTIONS
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(chipBg)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        quickList.forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 24.sp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        runWithOutboundGuard {
                                            viewModel.sendReaction(chatJid, target, emoji)
                                        }
                                        showSheetQuickReact = false
                                    }
                                    .padding(6.dp)
                            )
                        }
                    }
                }

                if (visibleReactions.isEmpty()) {
                    Text(
                        text = "No reactions yet",
                        color = colors.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        visibleReactions.forEach { item ->
                            val isMe = item.actor == MessageReactionEntity.ME_ACTOR ||
                                (bridgeStatus.ownJid.isNotBlank() && item.actor == bridgeStatus.ownJid)
                            val isLegacy = item.actor.startsWith(MessageReactionEntity.LEGACY_PREFIX)
                            val meta = if (!isMe && !isLegacy) participantMeta[item.actor] else null
                            val altJid = meta?.altJid.orEmpty()
                            val reactorAvatar = when {
                                isMe -> bridgeStatus.ownJid.takeIf { it.isNotBlank() }?.let { chatsByJid[it]?.avatarPath }
                                isLegacy -> null
                                else -> meta?.avatarPath?.takeIf { it.isNotBlank() }
                                    ?: chatsByJid[item.actor]?.avatarPath?.takeIf { it.isNotBlank() }
                                    ?: altJid.takeIf { it.isNotBlank() }?.let { chatsByJid[it]?.avatarPath?.takeIf { p -> p.isNotBlank() } }
                                    ?: if (chat?.isGroup != true) chat?.avatarPath?.takeIf { it.isNotBlank() } else null
                            }
                            val reactorDisplay = if (!isMe && !isLegacy) {
                                resolveSenderDisplayInfo(
                                    rawJidOrText = item.actor,
                                    explicitName = "",
                                    isSelf = false,
                                    participantMeta = participantMeta,
                                    chatsByJid = chatsByJid,
                                    senderPushByJid = senderPushByJid
                                )
                            } else null
                            val reactorName = when {
                                isMe -> "You"
                                isLegacy -> if (chat?.isGroup != true) {
                                    chat?.name?.takeIf { it.isNotBlank() } ?: "Contact"
                                } else {
                                    "Participant"
                                }
                                else -> reactorDisplay?.primary?.removePrefix("~ ")?.trim()
                                    ?.ifBlank { item.actor.substringBefore("@") }
                                    ?: item.actor.substringBefore("@")
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .then(
                                        when {
                                            isMe && !isChannel && !isGroupAnnounceLocked -> Modifier.clickable {
                                                runWithOutboundGuard {
                                                    viewModel.sendReaction(chatJid, target, "")
                                                }
                                                if (effectiveReactions.size <= 1) {
                                                    reactionDetailsTarget = null
                                                }
                                            }
                                            !isMe && !isLegacy && chat?.isGroup == true && !isChannel -> Modifier.clickable {
                                                reactionDetailsTarget = null
                                                participantPopupTarget = ParticipantPopupTarget(
                                                    jid = item.actor,
                                                    name = reactorName,
                                                    avatarPath = reactorAvatar,
                                                    phoneDisplay = reactorDisplay?.secondaryPhone.orEmpty(),
                                                    altJid = altJid,
                                                    isAdmin = reactorDisplay?.keyForColor == "admin"
                                                )
                                            }
                                            else -> Modifier
                                        }
                                    )
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                WhatsAppAvatar(
                                    jid = if (isMe) bridgeStatus.ownJid else if (isLegacy) "" else item.actor,
                                    avatarPath = reactorAvatar,
                                    isGroup = false,
                                    size = 44.dp,
                                    initialChar = extractAvatarInitialChar(reactorName)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = reactorName,
                                        color = colors.textPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isMe && !isChannel && !isGroupAnnounceLocked) {
                                        Text(
                                            text = "Tap to remove",
                                            color = colors.textSecondary,
                                            fontSize = 12.5.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = item.emoji,
                                    fontSize = 24.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Unlimited Edit History Bottom Sheet
    editHistoryTarget?.let { target ->
        val edits by viewModel.observeEdits(target.id).collectAsState(initial = emptyList())
        ModalBottomSheet(
            onDismissRequest = { editHistoryTarget = null },
            containerColor = colors.elevatedBar
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Unlimited Edit History",
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    fontSize = 17.sp
                )
                Card(colors = CardDefaults.cardColors(containerColor = colors.pillActiveBg)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Current Version · ${formatMessageClock(target.editedAt, includeSeconds = showSeconds, use24Hour = use24Hour)}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.pillActiveText)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(target.body, color = colors.textPrimary, fontSize = 14.sp)
                    }
                }
                if (edits.isEmpty()) {
                    Text("No prior revisions recorded yet.", color = colors.textSecondary, fontSize = 13.sp)
                } else {
                    edits.forEachIndexed { idx, rev ->
                        Card(colors = CardDefaults.cardColors(containerColor = colors.pillInactiveBg)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Revision #${edits.size - idx} · ${formatMessageClock(rev.editedAt, includeSeconds = showSeconds, use24Hour = use24Hour)}",
                                    fontSize = 11.sp,
                                    fontFamily = mono,
                                    color = colors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(rev.oldText, color = colors.textPrimary, fontSize = 13.5.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Message Info Bottom Sheet (DMs & Groups)
    groupReceiptsTarget?.let { target ->
        val receipts by viewModel.observeGroupReceipts(target.id).collectAsState(initial = emptyList())
        MessageInfoSheet(
            msg = target,
            isGroup = chat?.isGroup == true,
            groupReceipts = receipts,
            colors = colors,
            mono = mono,
            showSeconds = showSeconds,
            use24Hour = use24Hour,
            onDismiss = { groupReceiptsTarget = null }
        )
    }

    // Edit Message Text Dialog
    editMessageDialogTarget?.let { target ->
        var newBody by remember(target.id) { mutableStateOf(target.body) }
        AlertDialog(
            onDismissRequest = { editMessageDialogTarget = null },
            containerColor = colors.elevatedBar,
            title = { Text("Edit Message", color = colors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = newBody,
                    onValueChange = { newBody = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.editMessage(chatJid, target.id, newBody)
                        editMessageDialogTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen)
                ) {
                    Text("Save Edit", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { editMessageDialogTarget = null }) { Text("Cancel") }
            }
        )
    }

    if (showProfilePhotoLightbox) {
        val avatarTitle = chat?.name?.takeIf { it.isNotBlank() } ?: chatJid.substringBefore("@")
        ProfilePhotoLightbox(
            jid = chatJid,
            title = avatarTitle,
            avatarPath = chat?.avatarPath,
            onDismiss = { showProfilePhotoLightbox = false },
            onRequestFullAvatar = { viewModel.requestFullProfilePicture(chatJid) },
            onSaveAvatar = {
                viewModel.exportMediaToGallery(chat?.avatarPath, "${avatarTitle}_avatar.jpg", "image/jpeg")
            }
        )
    }

    if (showShareLocationSheet) {
        ShareLocationSheet(
            colors = colors,
            onDismiss = { showShareLocationSheet = false },
            onSendLocation = { lat, lng, name, address ->
                showShareLocationSheet = false
                runWithOutboundGuard {
                    val allowUnsaved = !isKnownChatOrContact && coldStartConfirmed
                    scrollToBottomPending = true
                    viewModel.sendLocationMessage(
                        chatJid = chatJid,
                        latitude = lat,
                        longitude = lng,
                        name = name,
                        address = address,
                        quoted = replyTarget,
                        allowUnsavedColdStart = allowUnsaved
                    )
                    replyTarget = null
                    scope.launch { listState.animateScrollToItem(0) }
                }
            }
        )
    }

    callMessageActionTarget?.let { callMsg ->
        val defaultVideo = resolveCallMessageIsVideo(callMsg.body)
        ModalBottomSheet(
            onDismissRequest = { callMessageActionTarget = null },
            containerColor = if (colors.isDark) Color(0xFF14181B) else colors.elevatedBar
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = callMsg.body.substringBefore("|").ifBlank { "Call" },
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                ActionSheetRow(
                    title = if (defaultVideo) "Video call back" else "Voice call back",
                    icon = if (defaultVideo) Icons.Rounded.Videocam else Icons.Rounded.Call
                ) {
                    callMessageActionTarget = null
                    initiateCall(defaultVideo)
                }
                ActionSheetRow(
                    title = if (defaultVideo) "Voice call instead" else "Video call instead",
                    icon = if (defaultVideo) Icons.Rounded.Call else Icons.Rounded.Videocam
                ) {
                    callMessageActionTarget = null
                    initiateCall(!defaultVideo)
                }
                ActionSheetRow(
                    title = "View call info",
                    icon = Icons.Rounded.Info
                ) {
                    callMessageActionTarget = null
                    callInfoDialogTarget = callMsg
                }
                ActionSheetRow(
                    title = "Delete from chat",
                    icon = Icons.Rounded.Delete
                ) {
                    callMessageActionTarget = null
                    deleteConfirmTarget = callMsg
                }
            }
        }
    }

    callInfoDialogTarget?.let { callMsg ->
        val parts = callMsg.body.split("|", limit = 2)
        val title = parts.getOrNull(0)?.ifBlank { "Voice call" } ?: "Voice call"
        val subtitle = parts.getOrNull(1)?.ifBlank {
            when {
                callMsg.durationSec in 1..59 -> "${callMsg.durationSec} sec"
                callMsg.durationSec >= 60 -> "${(callMsg.durationSec + 30) / 60} min"
                else -> "No answer"
            }
        } ?: "No answer"
        val formattedTime = remember(callMsg.timestamp) {
            SimpleDateFormat("MMM d, yyyy · h:mm:ss a", Locale.getDefault()).format(Date(callMsg.timestamp))
        }
        AlertDialog(
            onDismissRequest = { callInfoDialogTarget = null },
            containerColor = colors.elevatedBar,
            title = { Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (callMsg.isFromMe) "Direction: Outgoing" else "Direction: Incoming",
                        color = colors.textPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Status: $subtitle",
                        color = colors.textSecondary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Time: $formattedTime",
                        color = colors.textSecondary,
                        fontSize = 13.5.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val isVideo = resolveCallMessageIsVideo(callMsg.body)
                        callInfoDialogTarget = null
                        initiateCall(isVideo)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.brightGreen,
                        contentColor = colors.brightGreen.contrastingContentColor()
                    )
                ) {
                    Text("Call Back")
                }
            },
            dismissButton = {
                TextButton(onClick = { callInfoDialogTarget = null }) {
                    Text("Close")
                }
            }
        )
    }

    participantPopupTarget?.let { popup ->
        var resolvedDigits by remember(popup.jid, popup.altJid, popup.phoneDisplay) {
            mutableStateOf(
                extractDialablePhoneDigits(popup.jid, popup.altJid, popup.phoneDisplay)
            )
        }
        LaunchedEffect(popup.jid) {
            if (resolvedDigits.isEmpty()) {
                resolvedDigits = resolvePhoneDigitsForJid(popup.jid)
            }
        }
        val formattedPhone = remember(resolvedDigits, popup.phoneDisplay) {
            when {
                resolvedDigits.isNotEmpty() -> "+$resolvedDigits"
                popup.phoneDisplay.isNotBlank() -> popup.phoneDisplay
                else -> popup.jid.substringBefore("@")
            }
        }
        val resolveDirectChatJid: () -> String = {
            when {
                popup.altJid.endsWith("@s.whatsapp.net", ignoreCase = true) -> popup.altJid
                resolvedDigits.isNotEmpty() -> "$resolvedDigits@s.whatsapp.net"
                else -> popup.jid
            }
        }
        GroupParticipantActionSheet(
            target = popup,
            formattedPhone = formattedPhone,
            onDismiss = { participantPopupTarget = null },
            onCall = {
                participantPopupTarget = null
                initiateCallFor(popup, false)
            },
            onVideoCall = {
                participantPopupTarget = null
                initiateCallFor(popup, true)
            },
            onMessage = {
                val dmJid = resolveDirectChatJid()
                participantPopupTarget = null
                onNavigateToChat(dmJid)
            },
            onSaveContact = {
                participantPopupTarget = null
                val insertIntent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                    type = ContactsContract.RawContacts.CONTENT_TYPE
                    putExtra(ContactsContract.Intents.Insert.NAME, popup.name)
                    if (resolvedDigits.isNotEmpty()) {
                        putExtra(ContactsContract.Intents.Insert.PHONE, "+$resolvedDigits")
                    }
                }
                runCatching { context.startActivity(insertIntent) }.onFailure {
                    Toast.makeText(context, "No Contacts app available", Toast.LENGTH_SHORT).show()
                }
            },
            onViewProfilePhoto = {
                participantPopupTarget = null
                participantAvatarTarget = Triple(popup.jid, popup.name, popup.avatarPath)
            },
            onOpenContactInfo = {
                val infoJid = resolveDirectChatJid()
                participantPopupTarget = null
                onOpenContactInfo(infoJid)
            }
        )
    }

    participantAvatarTarget?.let { (pJid, pName, pPath) ->
        ProfilePhotoLightbox(
            jid = pJid,
            title = pName.ifBlank { pJid.substringBefore("@") },
            avatarPath = pPath,
            onDismiss = { participantAvatarTarget = null },
            onRequestFullAvatar = { viewModel.requestFullProfilePicture(pJid) },
            onSaveAvatar = {
                viewModel.exportMediaToGallery(pPath, "${pName.ifBlank { pJid.substringBefore("@") }}_avatar.jpg", "image/jpeg")
            }
        )
    }

    // Full-screen Media / View Once Lightbox
    mediaLightboxTarget?.let { target ->
        val galleryItems by remember(chatJid, target.isViewOnce) {
            if (target.isViewOnce) kotlinx.coroutines.flow.flowOf(emptyList())
            else viewModel.observeChatGalleryMedia(chatJid)
        }.collectAsState(initial = emptyList())
        val resolveSenderLabel: (MessageEntity) -> String = { item ->
            if (item.isFromMe) {
                "You"
            } else if (chat?.isGroup == true) {
                resolveSenderDisplayInfo(
                    rawJidOrText = item.senderJid,
                    explicitName = item.senderName,
                    isSelf = false,
                    participantMeta = participantMeta,
                    chatsByJid = chatsByJid,
                    senderPushByJid = senderPushByJid
                ).primary.takeIf {
                    it.isNotBlank() && !DeviceContactFallbackResolver.isGroupJidLikeIdentifier(it, chatJid)
                } ?: chat?.name?.takeIf { it.isNotBlank() }.orEmpty().ifBlank { "Participant" }
            } else {
                chat?.name?.takeIf { it.isNotBlank() }
                    ?: item.senderName.takeIf {
                        it.isNotBlank() && !DeviceContactFallbackResolver.isGroupJidLikeIdentifier(it, chatJid)
                    }
                    ?: item.senderJid.substringBefore("@")
            }
        }
        FullScreenMediaViewer(
            msg = target,
            senderDisplayName = resolveSenderLabel(target),
            onDismiss = { mediaLightboxTarget = null },
            onSaveToGallery = { item ->
                val (ext, fallbackMime) = when {
                    item.mimeType.contains("mp4", ignoreCase = true) || item.msgType == "VIDEO" ->
                        "mp4" to "video/mp4"
                    item.msgType == "PTT" || item.msgType == "AUDIO" || item.mimeType.startsWith("audio/", ignoreCase = true) ->
                        (if (item.mimeType.contains("m4a", ignoreCase = true) || item.mimeType.contains("mp4", ignoreCase = true)) "m4a" else "ogg") to "audio/ogg"
                    else -> "png" to "image/png"
                }
                val mime = item.mimeType.ifBlank { fallbackMime }
                viewModel.exportMediaToGallery(
                    item.localMediaPath,
                    "WA_Vault_${item.id}.$ext",
                    mime
                )
            },
            onReply = if (isChannel) null else {
                { item -> replyTarget = item }
            },
            onQuickReaction = if (isChannel) null else {
                { item, emoji ->
                    runWithOutboundGuard {
                        viewModel.sendReaction(chatJid, item, emoji)
                    }
                }
            },
            gallery = galleryItems,
            senderDisplayNameFor = resolveSenderLabel
        )
    }

    pdfViewerTarget?.let { (file, title) ->
        InAppPdfViewerDialog(
            file = file,
            title = title,
            colors = colors,
            onDismiss = { pdfViewerTarget = null }
        )
    }

    pendingOutboundPreview?.let { staged ->
        val recipientLabel = chat?.name?.takeIf { it.isNotBlank() } ?: chatJid.substringBefore("@")
        OutboundMediaPreviewDialog(
            staged = staged,
            recipientLabel = recipientLabel,
            initialCaption = draftValue.text.trim(),
            initialQualityMode = qualityMode,
            onDismiss = { pendingOutboundPreview = null },
            onSend = { finalFile, captionText, chosenQuality, isViewOnce ->
                pendingOutboundPreview = null
                qualityMode = chosenQuality
                runWithOutboundGuard {
                    scrollToBottomPending = true
                    val targetStaged = if (finalFile != staged.file) {
                        staged.copy(file = finalFile, fileSize = finalFile.length())
                    } else {
                        staged
                    }
                    viewModel.sendStagedMedia(
                        chatJid = chatJid,
                        staged = targetStaged,
                        caption = captionText,
                        qualityMode = chosenQuality,
                        isViewOnce = isViewOnce,
                        allowUnsavedColdStart = allowUnsavedColdStartFlag
                    )
                    draftValue = TextFieldValue("")
                    scope.launch { listState.animateScrollToItem(0) }
                }
            }
        )
    }

    pendingGhostGuardAction?.let { pendingAction ->
        AlertDialog(
            onDismissRequest = { pendingGhostGuardAction = null },
            containerColor = colors.elevatedBar,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text("Unfreeze Last Seen?", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Ghost Mode (Frozen Last Seen) is active. Sending will temporarily unfreeze your last seen timestamp so WhatsApp accepts the action, and then safely re-freeze it after 1.5 seconds.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val act = pendingGhostGuardAction
                        pendingGhostGuardAction = null
                        act?.invoke()
                    }
                ) {
                    Text("Unfreeze & Send", color = colors.brightGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingGhostGuardAction = null }
                ) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    pendingColdStartAction?.let {
        AlertDialog(
            onDismissRequest = { pendingColdStartAction = null },
            containerColor = colors.elevatedBar,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text("Unsaved Number Warning", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "This number isn't saved in your contacts and has no synced chat history. Starting a chat with an unsaved number from a companion device can trigger WhatsApp Error 463 anti-spam checks.\n\nSend anyway or message from your primary phone first?",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val act = pendingColdStartAction
                        pendingColdStartAction = null
                        coldStartConfirmed = true
                        act?.invoke()
                    }
                ) {
                    Text("Send Anyway", color = colors.brightGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingColdStartAction = null }
                ) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    pendingColdCallVideo?.let { targetIsVideo ->
        AlertDialog(
            onDismissRequest = { pendingColdCallVideo = null },
            containerColor = colors.elevatedBar,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text("Cold-Call Safety Warning", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "This number isn't saved in your contacts and has zero prior chat history. Placing unsolicited calls to unsaved accounts increases WhatsApp spam/ban risk.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingColdCallVideo = null
                        launchOutgoingCallFlow(targetIsVideo)
                    }
                ) {
                    Text("Place Call Anyway", color = colors.antiDeleteRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingColdCallVideo = null }
                ) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    if (showCallingSetupDialog) {
        CallingSetupDialog(
            ownJid = bridgeStatus.ownJid,
            stealth = stealthConfig,
            onDismiss = { showCallingSetupDialog = false },
            onCallViaOfficialApp = {
                showCallingSetupDialog = false
                CallStateManager.openOfficialWhatsApp(context, pendingSetupOfficialFallbackDigits)
            }
        )
    }

    deleteConfirmTarget?.let { target ->
        val mode = resolveMessageDeleteDialogMode(target)
        val dialogTitle = when (mode) {
            MessageDeleteDialogMode.PERMANENT_DELETE_CAPTURED -> stringResource(R.string.chat_delete_captured_title)
            MessageDeleteDialogMode.DELETE_FOR_ME_INCOMING,
            MessageDeleteDialogMode.DELETE_OUTGOING_OPTIONS -> stringResource(R.string.chat_delete_dialog_title)
        }
        val dialogBody = when (mode) {
            MessageDeleteDialogMode.PERMANENT_DELETE_CAPTURED -> stringResource(R.string.chat_delete_captured_body)
            MessageDeleteDialogMode.DELETE_FOR_ME_INCOMING -> stringResource(R.string.chat_delete_incoming_body)
            MessageDeleteDialogMode.DELETE_OUTGOING_OPTIONS -> stringResource(R.string.chat_delete_outgoing_body)
        }
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            containerColor = colors.elevatedBar,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text(dialogTitle, fontWeight = FontWeight.SemiBold) },
            text = { Text(dialogBody, fontSize = 14.sp) },
            confirmButton = {
                if (mode == MessageDeleteDialogMode.DELETE_OUTGOING_OPTIONS) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.revokeMessageForEveryone(chatJid, target.id)
                                deleteConfirmTarget = null
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.chat_delete_for_everyone),
                                color = colors.antiDeleteRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TextButton(
                            onClick = {
                                viewModel.deleteMessageFromDevice(target.id)
                                deleteConfirmTarget = null
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.chat_delete_for_me),
                                color = colors.accentGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TextButton(onClick = { deleteConfirmTarget = null }) {
                            Text("Cancel", color = colors.textSecondary)
                        }
                    }
                } else {
                    TextButton(
                        onClick = {
                            viewModel.deleteMessageFromDevice(target.id)
                            deleteConfirmTarget = null
                        }
                    ) {
                        Text(
                            text = if (mode == MessageDeleteDialogMode.PERMANENT_DELETE_CAPTURED) {
                                stringResource(R.string.chat_delete_from_device)
                            } else {
                                stringResource(R.string.chat_delete_for_me)
                            },
                            color = colors.antiDeleteRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            dismissButton = if (mode == MessageDeleteDialogMode.DELETE_OUTGOING_OPTIONS) {
                null
            } else {
                {
                    TextButton(onClick = { deleteConfirmTarget = null }) {
                        Text("Cancel", color = colors.textSecondary)
                    }
                }
            }
        )
    }

    pinDurationDialogTarget?.let { target ->
        PinDurationDialog(
            onDismiss = { pinDurationDialogTarget = null },
            onConfirm = { durationSec ->
                viewModel.pinMessage(chatJid, target, durationSec)
                pinDurationDialogTarget = null
            },
            colors = colors
        )
    }

    forwardTarget?.let { target ->
        val lockedGroups by com.wa.beastclient.core.service.WaRealtimeService.announceLockedGroups.collectAsState()
        ForwardMessageDialog(
            allChats = allChats,
            announceLockedGroups = lockedGroups,
            currentChatJid = chatJid,
            colors = colors,
            onDismiss = { forwardTarget = null },
            onConfirmForward = { targetJids ->
                viewModel.forwardMessage(target, targetJids)
                forwardTarget = null
            }
        )
    }

    // Bulk-forward dialog (from multi-select)
    if (forwardTargets.isNotEmpty()) {
        val lockedGroups by com.wa.beastclient.core.service.WaRealtimeService.announceLockedGroups.collectAsState()
        ForwardMessageDialog(
            allChats = allChats,
            announceLockedGroups = lockedGroups,
            currentChatJid = chatJid,
            colors = colors,
            onDismiss = { forwardTargets = emptyList() },
            onConfirmForward = { targetJids ->
                viewModel.forwardMessages(forwardTargets, targetJids)
                forwardTargets = emptyList()
            }
        )
    }

    // Bulk-delete dialog (from multi-select)
    if (deleteConfirmTargets.isNotEmpty()) {
        val hasOutgoing = deleteConfirmTargets.any { it.isFromMe && !it.isDeletedBySender }
        AlertDialog(
            onDismissRequest = { deleteConfirmTargets = emptyList() },
            containerColor = colors.elevatedBar,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text("Delete ${deleteConfirmTargets.size} message(s)?", fontWeight = FontWeight.SemiBold) },
            text = { Text("Choose how to delete the selected messages.", fontSize = 14.sp) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                    if (hasOutgoing) {
                        TextButton(onClick = {
                            val outgoing = deleteConfirmTargets.filter { it.isFromMe && !it.isDeletedBySender }
                            viewModel.revokeMessagesForEveryone(chatJid, outgoing.map { it.id })
                            val others = deleteConfirmTargets.filter { !it.isFromMe || it.isDeletedBySender }
                            if (others.isNotEmpty()) viewModel.deleteMessagesFromDevice(others.map { it.id })
                            deleteConfirmTargets = emptyList()
                        }) {
                            Text(text = stringResource(R.string.chat_delete_for_everyone), color = colors.antiDeleteRed, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    TextButton(onClick = {
                        viewModel.deleteMessagesFromDevice(deleteConfirmTargets.map { it.id })
                        deleteConfirmTargets = emptyList()
                    }) {
                        Text(text = stringResource(R.string.chat_delete_for_me), color = colors.accentGreen, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = { deleteConfirmTargets = emptyList() }) {
                        Text("Cancel", color = colors.textSecondary)
                    }
                }
            },
            dismissButton = null
        )
    }
}

internal fun findLoadedQuotedItemIndex(
    items: List<MessageEntity?>,
    quotedId: String
): Int {
    val cleanId = quotedId.trim()
    if (cleanId.isEmpty()) return -1
    return items.indexOfFirst { it != null && it.id == cleanId }
}

internal enum class MessageDeleteDialogMode {
    PERMANENT_DELETE_CAPTURED,
    DELETE_FOR_ME_INCOMING,
    DELETE_OUTGOING_OPTIONS
}

internal fun resolveMessageDeleteDialogMode(msg: MessageEntity): MessageDeleteDialogMode = when {
    msg.isDeletedBySender -> MessageDeleteDialogMode.PERMANENT_DELETE_CAPTURED
    msg.isFromMe -> MessageDeleteDialogMode.DELETE_OUTGOING_OPTIONS
    else -> MessageDeleteDialogMode.DELETE_FOR_ME_INCOMING
}

internal enum class SelectionBarAction {
    REPLY,
    FORWARD,
    PIN_UNPIN,
    EDIT,
    SAVE_MEDIA,
    COPY,
    MESSAGE_INFO,
    EDIT_HISTORY,
    OPEN_DOCUMENT,
    DELETE
}

internal data class SelectionBarLayout(
    val directActions: List<SelectionBarAction>,
    val overflowActions: List<SelectionBarAction>
)

internal fun resolveSelectionBarLayout(
    msg: MessageEntity,
    isChannel: Boolean,
    isGroupAnnounceLocked: Boolean,
    isGroup: Boolean,
    isDocument: Boolean,
    maxDirectWithoutOverflow: Int = 6
): SelectionBarLayout {
    val available = buildList {
        if (!isChannel && !isGroupAnnounceLocked) add(SelectionBarAction.REPLY)
        if (com.wa.beastclient.core.service.WaRealtimeService.isForwardableMessageType(msg)) {
            add(SelectionBarAction.FORWARD)
        }
        add(SelectionBarAction.MESSAGE_INFO)
        add(SelectionBarAction.PIN_UNPIN)
        if (msg.isFromMe && msg.msgType == "TEXT") add(SelectionBarAction.EDIT)
        if (!msg.localMediaPath.isNullOrBlank()) {
            add(SelectionBarAction.SAVE_MEDIA)
        }
        if (msg.body.isNotBlank()) add(SelectionBarAction.COPY)
        if (!msg.localMediaPath.isNullOrBlank() && isDocument) {
            add(SelectionBarAction.OPEN_DOCUMENT)
        }
        if (msg.isEdited) add(SelectionBarAction.EDIT_HISTORY)
        add(SelectionBarAction.DELETE)
    }

    if (available.size <= maxDirectWithoutOverflow) {
        return SelectionBarLayout(directActions = available, overflowActions = emptyList())
    }

    // Reserve one slot for the overflow button while keeping Delete always directly reachable.
    val nonDelete = available.filter { it != SelectionBarAction.DELETE }
    val directNonDeleteCount = (maxDirectWithoutOverflow - 2).coerceAtLeast(1)
    val direct = nonDelete.take(directNonDeleteCount) + SelectionBarAction.DELETE
    val overflow = nonDelete.drop(directNonDeleteCount)
    return SelectionBarLayout(directActions = direct, overflowActions = overflow)
}

internal fun resolveFloatingContextActions(
    msg: MessageEntity,
    isChannel: Boolean,
    isGroupAnnounceLocked: Boolean,
    isDocument: Boolean
): Pair<List<SelectionBarAction>, List<SelectionBarAction>> {
    val primary = buildList {
        if (!isChannel && !isGroupAnnounceLocked) add(SelectionBarAction.REPLY)
        if (com.wa.beastclient.core.service.WaRealtimeService.isForwardableMessageType(msg)) {
            add(SelectionBarAction.FORWARD)
        }
        if (msg.body.isNotBlank()) add(SelectionBarAction.COPY)
        if (msg.isFromMe && msg.msgType == "TEXT") add(SelectionBarAction.EDIT)
        add(SelectionBarAction.DELETE)
        add(SelectionBarAction.MESSAGE_INFO)
    }
    val secondary = buildList {
        add(SelectionBarAction.PIN_UNPIN)
        if (!msg.localMediaPath.isNullOrBlank()) {
            add(SelectionBarAction.SAVE_MEDIA)
        }
        if (!msg.localMediaPath.isNullOrBlank() && isDocument) {
            add(SelectionBarAction.OPEN_DOCUMENT)
        }
        if (msg.isEdited) add(SelectionBarAction.EDIT_HISTORY)
    }
    return primary to secondary
}

@Composable
private fun ActionSheetRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val colors = LocalWaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = colors.accentGreen, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(title, color = colors.textPrimary, fontSize = 14.5.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageBubbleRow(
    msg: MessageEntity,
    isGroup: Boolean,
    isChannel: Boolean = false,
    channelDisplayName: String = "",
    showTail: Boolean,
    showSenderHeader: Boolean = showTail,
    isSenderAdmin: Boolean = false,
    senderDisplay: SenderDisplayInfo? = null,
    senderAvatarPath: String? = null,
    quotedDisplay: SenderDisplayInfo? = null,
    quotedMentionsCsv: String = "",
    quotedMediaPath: String? = null,
    quotedMsgType: String? = null,
    quotedIsViewOnce: Boolean = false,
    quotedDurationSec: Int = 0,
    mentionDisplayResolver: ((String) -> String?)? = null,
    onClickSenderAvatar: () -> Unit = {},
    ghostPlayedActive: Boolean,
    isDownloadingMedia: Boolean = false,
    mediaDownloadError: String? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onBubbleBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    onQuickForward: (() -> Unit)? = null,
    onClickReaction: (() -> Unit)? = null,
    reactionBadgeText: String = msg.reactionSummary,
    onClickEditBadge: () -> Unit,
    onClickQuoted: (String) -> Unit = {},
    onManualSendPlayedReceipt: () -> Unit,
    onAutoRequestDownload: () -> Unit = {},
    onRequestDownload: () -> Unit = {},
    onOpenMedia: () -> Unit,
    onOpenSticker: () -> Unit = {},
    onOpenDocument: () -> Unit = {},
    onSaveDocument: () -> Unit = {},
    onCallBackMessage: (Boolean) -> Unit = {},
    onLongClickCallMessage: (() -> Unit)? = null,
    onLongPress: () -> Unit,
    onNavigateToChat: (String) -> Unit = {}
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    val showSeconds = LocalWuppShowSeconds.current
    val use24Hour = LocalWuppUse24Hour.current
    val context = LocalContext.current
    val effectiveOutgoing = msg.isFromMe && !isChannel
    val isSticker = msg.msgType == "STICKER" && !msg.isDeletedBySender
    val bubbleColor = when {
        isSticker -> Color.Transparent
        effectiveOutgoing -> colors.outgoingBubble
        else -> colors.incomingBubble
    }
    val shape = when {
        isSticker -> RoundedCornerShape(0.dp)
        isChannel -> RoundedCornerShape(12.dp)
        showTail -> WhatsAppBubbleShape(effectiveOutgoing, true, LocalWuppBubbleRadiusDp.current)
        else -> whatsAppClusterInnerShape(LocalWuppBubbleRadiusDp.current)
    }

    val hasVaultFile = remember(msg.localMediaPath) {
        !msg.localMediaPath.isNullOrBlank() &&
            File(msg.localMediaPath).let { it.exists() && it.length() > 0L }
    }
    val isDocCard = remember(msg.id, msg.msgType, msg.mimeType, msg.body, msg.isViewOnce, msg.isDeletedBySender) {
        isDocumentMessage(msg)
    }
    val isContactCard = remember(msg.id, msg.msgType, msg.isDeletedBySender) {
        isContactMessage(msg)
    }
    val parsedLocation = remember(msg.id, msg.msgType, msg.body, msg.mimeType, msg.isDeletedBySender) {
        if (msg.msgType == "LOCATION" && !msg.isDeletedBySender) {
            parseLocationPayload(msg.body, msg.mimeType)
        } else {
            null
        }
    }
    val isLocationCard = parsedLocation != null
    val isMediaCard = (msg.msgType == "IMAGE" || msg.msgType == "VIDEO" || msg.msgType == "GIF") &&
        !msg.isViewOnce &&
        msg.msgType != "VIEW_ONCE_STRIPPED" &&
        !msg.isDeletedBySender

    val tailOffsetDp = 5.5.dp
    val sideStartPad = when {
        isSticker -> 0.dp
        isMediaCard || isLocationCard -> if (!isChannel && showTail && !effectiveOutgoing) 8.5.dp else 3.dp
        isDocCard || isContactCard -> if (!isChannel && showTail && !effectiveOutgoing) 11.5.dp else 6.dp
        else -> if (!isChannel && showTail && !effectiveOutgoing) 15.5.dp else 10.dp
    }
    val sideEndPad = when {
        isSticker -> 0.dp
        isMediaCard || isLocationCard -> if (!isChannel && showTail && effectiveOutgoing) 8.5.dp else 3.dp
        isDocCard || isContactCard -> if (!isChannel && showTail && effectiveOutgoing) 11.5.dp else 6.dp
        else -> if (!isChannel && showTail && effectiveOutgoing) 15.5.dp else 10.dp
    }
    val topBottomPad = when {
        isSticker -> 0.dp
        isMediaCard || isLocationCard -> 3.dp
        isDocCard || isContactCard -> 5.dp
        else -> 6.dp
    }
    val bubbleMaxW = when {
        isSticker -> 160.dp
        isMediaCard -> 282.dp
        isLocationCard -> 276.dp
        isContactCard -> 270.dp
        isDocCard -> 285.dp
        else -> 305.dp
    }
    val showGroupGutter = isGroup && !effectiveOutgoing
    val nonTailStartAlign = if (!isChannel && !isSticker && !effectiveOutgoing && !showTail) tailOffsetDp else 0.dp
    val nonTailEndAlign = if (!isChannel && !isSticker && effectiveOutgoing && !showTail) tailOffsetDp else 0.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelectionMode) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleSelect
                    )
                } else Modifier
            )
            .padding(
                start = when {
                    isChannel -> 4.dp
                    effectiveOutgoing -> 52.dp
                    else -> nonTailStartAlign
                },
                end = when {
                    isChannel -> 4.dp
                    effectiveOutgoing -> nonTailEndAlign
                    showGroupGutter -> 34.dp
                    else -> 48.dp
                }
            ),
        horizontalArrangement = when {
            isChannel -> Arrangement.Center
            effectiveOutgoing -> Arrangement.End
            else -> Arrangement.Start
        },
        verticalAlignment = Alignment.Top
    ) {
        if (showGroupGutter) {
            if (showSenderHeader) {
                val resolvedPrimary = senderDisplay?.primary ?: msg.senderName
                val showInitials = resolvedPrimary != "~ Member" && resolvedPrimary != "Group Admin"
                Box(
                    modifier = Modifier
                        .padding(top = 1.dp, end = 6.dp)
                        .clickable {
                            if (isSelectionMode) onToggleSelect() else onClickSenderAvatar()
                        }
                ) {
                    WhatsAppAvatar(
                        jid = msg.senderJid,
                        avatarPath = senderAvatarPath,
                        isGroup = false,
                        size = 28.dp,
                        initialChar = if (showInitials) extractAvatarInitialChar(resolvedPrimary) else null
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(34.dp))
            }
        }
        if (effectiveOutgoing && isMediaCard && hasVaultFile && onQuickForward != null && !isSelectionMode && !isChannel) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(end = 10.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (colors.isDark) Color(0xD41F2428) else Color(0xE0FFFFFF))
                    .clickable(onClick = onQuickForward),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Forward,
                    contentDescription = "Forward media",
                    tint = if (colors.isDark) Color.White else colors.textPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        Column(
            horizontalAlignment = if (effectiveOutgoing) Alignment.End else Alignment.Start
        ) {
        Box(
            modifier = Modifier
                .then(if (isChannel) Modifier.fillMaxWidth().widthIn(max = 390.dp) else Modifier.widthIn(max = bubbleMaxW))
                .onGloballyPositioned { coords ->
                    onBubbleBoundsChanged?.invoke(coords.boundsInRoot())
                }
                .clip(shape)
                .background(bubbleColor)
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) {
                            onToggleSelect()
                            return@combinedClickable
                        }
                        when {
                            msg.isEdited -> onClickEditBadge()
                            msg.isViewOnce || msg.msgType == "VIEW_ONCE_STRIPPED" || msg.viewOnceState == "STRIPPED" -> {
                                Toast.makeText(
                                    context,
                                    "For added privacy, you can only open this view once message on your phone",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        isDocCard -> if (hasVaultFile) onOpenDocument() else onRequestDownload()
                        !msg.localMediaPath.isNullOrBlank() && msg.msgType != "PTT" && msg.msgType != "AUDIO" -> onOpenMedia()
                        msg.msgType == "IMAGE" || msg.msgType == "VIDEO" || msg.msgType == "GIF" -> onRequestDownload()
                    }
                },
                onLongClick = onLongPress
            )
            .padding(start = sideStartPad, end = sideEndPad, top = topBottomPad, bottom = topBottomPad)
        ) {
            val useTightIntrinsicWidth = !msg.quotedId.isNullOrBlank() &&
                !isChannel && !isMediaCard && !isDocCard && !isContactCard &&
                msg.msgType != "PTT" && msg.msgType != "AUDIO"
            Column(
                modifier = if (useTightIntrinsicWidth) Modifier.width(IntrinsicSize.Max) else Modifier
            ) {
                // Sender color label in Groups
                val cleanSenderJid = msg.senderJid.takeIf {
                    it.isNotBlank() && !DeviceContactFallbackResolver.isGroupJidLikeIdentifier(it, msg.chatJid)
                }.orEmpty()
                val senderPrimary = senderDisplay?.primary
                    ?.takeIf { it.isNotBlank() && !DeviceContactFallbackResolver.isGroupJidLikeIdentifier(it, msg.chatJid) }
                    ?: msg.senderName.takeIf {
                        it.isNotBlank() && !DeviceContactFallbackResolver.isGroupJidLikeIdentifier(it, msg.chatJid)
                    }
                    ?: cleanSenderJid.substringBefore("@").takeIf { it.isNotBlank() }
                    ?: "~ Member"
                if (isGroup && !effectiveOutgoing && showSenderHeader && senderPrimary.isNotBlank()) {
                    val palette = listOf(Color(0xFF53BDEB), Color(0xFFFFB74D), Color(0xFF7CE8A5), Color(0xFFF48FB1))
                    val colorKey = senderDisplay?.keyForColor?.takeIf {
                        it.isNotBlank() && it != "participant" && it != "group"
                    } ?: cleanSenderJid.takeIf { it.isNotBlank() } ?: msg.senderName.ifBlank { msg.id }
                    val c = palette[abs(colorKey.hashCode()) % palette.size]
                    val resolvedPrimary = senderPrimary
                    val resolvedPhone = senderDisplay?.secondaryPhone.orEmpty()
                    Row(
                        modifier = Modifier
                            .then(
                                if (!isSelectionMode) {
                                    Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onClickSenderAvatar
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .then(
                                when {
                                    isMediaCard -> Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    isDocCard -> Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
                                    else -> Modifier.padding(bottom = 2.dp)
                                }
                            ),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = resolvedPrimary,
                            color = c,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isSenderAdmin || senderDisplay?.keyForColor == "admin") {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = "Group admin",
                                tint = colors.accentGreen,
                                modifier = Modifier.size(12.5.dp)
                            )
                        }
                        if (resolvedPhone.isNotBlank()) {
                            Text(
                                text = resolvedPhone,
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Quoted Context Banner with full-height accent bar + media glyph + 50dp square thumbnail
                if (!msg.quotedId.isNullOrBlank()) {
                    val rawQuoted = msg.quotedSender.orEmpty()
                    val qPrimary = quotedDisplay?.primary?.takeIf { it.isNotBlank() } ?: when {
                        isChannel && (rawQuoted.isBlank() || rawQuoted == msg.chatJid || rawQuoted.endsWith("@newsletter")) ->
                            channelDisplayName.ifBlank { "Channel" }
                        rawQuoted.endsWith("@newsletter") ->
                            channelDisplayName.ifBlank { rawQuoted.substringBefore("@") }
                        rawQuoted.contains("@") -> rawQuoted.substringBefore("@")
                        rawQuoted.isNotBlank() -> rawQuoted
                        else -> "Quoted message"
                    }
                    val qPhone = quotedDisplay?.secondaryPhone.orEmpty()
                    val qAccent = when {
                        isChannel || qPrimary == "You" -> colors.brightGreen
                        else -> listOf(
                            Color(0xFF53BDEB),
                            Color(0xFFFFB74D),
                            Color(0xFF7CE8A5),
                            Color(0xFFF48FB1)
                        )[abs((quotedDisplay?.keyForColor ?: rawQuoted).hashCode()) % 4]
                    }
                    val qTypeUpper = quotedMsgType.orEmpty().uppercase()
                    val hasQuotedThumb = !quotedIsViewOnce && !quotedMediaPath.isNullOrBlank() &&
                        (qTypeUpper == "IMAGE" || qTypeUpper == "VIDEO" || qTypeUpper == "GIF" || qTypeUpper == "STICKER")
                    val quotedThumbBmp by produceState<ImageBitmap?>(
                        initialValue = null,
                        key1 = quotedMediaPath,
                        key2 = qTypeUpper,
                        key3 = quotedIsViewOnce
                    ) {
                        value = if (hasQuotedThumb) {
                            withContext(Dispatchers.IO) {
                                decodeQuotedThumbnailBitmap(quotedMediaPath!!, qTypeUpper == "VIDEO")
                            }
                        } else null
                    }
                    val durationSuffix = if (quotedDurationSec > 0) " (${formatVideoDuration(quotedDurationSec)})" else ""
                    val mediaTypeFallback = when (qTypeUpper) {
                        "IMAGE" -> "Photo"
                        "VIDEO" -> "Video$durationSuffix"
                        "PTT", "AUDIO" -> "Voice message$durationSuffix"
                        "DOCUMENT" -> "Document"
                        "STICKER" -> "Sticker"
                        "GIF" -> "GIF"
                        else -> ""
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(min = 148.dp)
                            .padding(
                                start = if (isMediaCard) 3.dp else 0.dp,
                                end = if (isMediaCard) 3.dp else 0.dp,
                                bottom = 5.dp
                            )
                            .height(IntrinsicSize.Min)
                            .heightIn(min = 46.dp, max = 56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.quotedBg)
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        onToggleSelect()
                                    } else {
                                        val targetId = msg.quotedId?.trim().orEmpty()
                                        if (targetId.isNotEmpty()) {
                                            onClickQuoted(targetId)
                                        }
                                    }
                                },
                                onLongClick = onLongPress
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(qAccent)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.5.dp, vertical = 5.5.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = qPrimary,
                                    color = qAccent,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (qPhone.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = qPhone,
                                        color = colors.textSecondary,
                                        fontSize = 10.5.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                            val resolvedQuotedBody = if (quotedMentionsCsv.isNotBlank() && mentionDisplayResolver != null) {
                                resolveMentionsInText(msg.quotedBody.orEmpty(), quotedMentionsCsv, mentionDisplayResolver)
                            } else {
                                msg.quotedBody.orEmpty()
                            }
                            val displayQuotedText = resolvedQuotedBody.ifBlank {
                                mediaTypeFallback.ifBlank { "Message" }
                            }
                            val quotedTint = if (colors.isDark) Color(0xFFBDC5CC) else colors.textSecondary
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                when {
                                    quotedIsViewOnce -> {
                                        Icon(
                                            painter = painterResource(R.drawable.vec_ic_view_once),
                                            contentDescription = null,
                                            tint = quotedTint,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    qTypeUpper == "VIDEO" -> {
                                        Icon(
                                            imageVector = Icons.Rounded.Videocam,
                                            contentDescription = null,
                                            tint = quotedTint,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    qTypeUpper == "PTT" || qTypeUpper == "AUDIO" -> {
                                        Icon(
                                            imageVector = Icons.Rounded.Mic,
                                            contentDescription = null,
                                            tint = quotedTint,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    qTypeUpper == "IMAGE" -> {
                                        Icon(
                                            imageVector = Icons.Rounded.PhotoCamera,
                                            contentDescription = null,
                                            tint = quotedTint,
                                            modifier = Modifier.size(13.5.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = displayQuotedText,
                                    color = quotedTint,
                                    fontSize = 13.sp,
                                    lineHeight = 16.5.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (quotedThumbBmp != null) {
                            Box(
                                modifier = Modifier.size(50.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = quotedThumbBmp!!,
                                    contentDescription = mediaTypeFallback.ifBlank { "Quoted media" },
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (qTypeUpper == "VIDEO") {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x9912171B)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (msg.isDeletedBySender) {
                    DeletedTimestampBadge(
                        deletedAtMs = msg.deletedAt,
                        fallbackTimestampMs = msg.timestamp,
                        modifier = Modifier.padding(
                            start = if (isMediaCard) 4.dp else 0.dp,
                            end = if (isMediaCard) 4.dp else 0.dp,
                            bottom = 4.dp
                        )
                    )
                }

                when {
                    // Already opened / stripped View-Once
                    msg.viewOnceState == "STRIPPED" || msg.msgType == "VIEW_ONCE_STRIPPED" -> {
                        val voSubType = when (msg.msgType.uppercase()) {
                            "VIDEO" -> " · Video"
                            "PTT", "AUDIO" -> " · Voice message"
                            "IMAGE" -> " · Photo"
                            else -> ""
                        }
                        Row(
                            modifier = Modifier
                                .widthIn(min = 175.dp)
                                .padding(horizontal = 2.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(colors.textSecondary.copy(alpha = 0.14f))
                                    .border(0.65.dp, colors.textSecondary.copy(alpha = 0.22f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.vec_ic_view_once),
                                    contentDescription = "View once opened",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    text = "Opened$voSubType",
                                    color = colors.textSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Opened on phone",
                                    color = colors.textSecondary.copy(alpha = 0.78f),
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(modifier = Modifier.align(Alignment.End)) {
                            MessageMetaCluster(
                                timeText = formatMessageClock(msg.timestamp, includeSeconds = showSeconds, use24Hour = use24Hour),
                                isEdited = false,
                                isEphemeral = msg.isEphemeral,
                                isFromMe = msg.isFromMe,
                                deliveryStatus = msg.deliveryStatus,
                                isPinned = msg.isPinned
                            )
                        }
                    }

                    // Active View-Once (Photo, Video, Voice message -> concise companion notice)
                    msg.isViewOnce -> {
                        val voLabel = when (msg.msgType.uppercase()) {
                            "VIDEO" -> "Video"
                            "PTT", "AUDIO" -> "Voice message"
                            else -> "Photo"
                        }
                        val tintColor = if (effectiveOutgoing) colors.textPrimary else colors.brightGreen
                        val subLabel = if (msg.durationSec > 0) "${formatVideoDuration(msg.durationSec)} · Open on phone" else "Open on phone"
                        Row(
                            modifier = Modifier
                                .widthIn(min = 185.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            onToggleSelect()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "For added privacy, you can only open this view once message on your phone",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    onLongClick = onLongPress
                                )
                                .padding(horizontal = 2.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(tintColor.copy(alpha = 0.16f))
                                    .border(0.65.dp, tintColor.copy(alpha = 0.34f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.vec_ic_view_once),
                                    contentDescription = "View once $voLabel",
                                    tint = tintColor,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    text = voLabel,
                                    color = tintColor,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.5.sp
                                )
                                Text(
                                    text = subLabel,
                                    color = colors.textSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(modifier = Modifier.align(Alignment.End)) {
                            MessageMetaCluster(
                                timeText = formatMessageClock(msg.timestamp, includeSeconds = showSeconds, use24Hour = use24Hour),
                                isEdited = msg.isEdited,
                                isEphemeral = msg.isEphemeral,
                                isFromMe = msg.isFromMe,
                                deliveryStatus = msg.deliveryStatus,
                                isPinned = msg.isPinned
                            )
                        }
                    }

                    // Voice Note (PTT) / Audio
                    msg.msgType == "PTT" || msg.msgType == "AUDIO" -> {
                        val hasAudioFile = remember(msg.localMediaPath) {
                            !msg.localMediaPath.isNullOrBlank() &&
                                File(msg.localMediaPath).let { it.exists() && it.length() > 0L }
                        }
                        LaunchedEffect(msg.id, hasAudioFile) {
                            if (!hasAudioFile && !isDownloadingMedia && mediaDownloadError == null) {
                                onAutoRequestDownload()
                            }
                        }
                        Box {
                            VoiceNoteBubble(
                                msg = msg,
                                ghostPlayedActive = ghostPlayedActive,
                                onManualSendPlayedReceipt = onManualSendPlayedReceipt,
                                onRequestDownload = onRequestDownload,
                                isDownloadingMedia = isDownloadingMedia,
                                mediaDownloadError = mediaDownloadError,
                                avatarPath = if (msg.isFromMe) null else senderAvatarPath,
                                onClickAvatar = if (msg.isFromMe) null else onClickSenderAvatar
                            )
                            if (isSelectionMode) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = onToggleSelect,
                                            onLongClick = onLongPress
                                        )
                                )
                            }
                        }
                    }

                    // Call Log Bubble ("Voice call · No answer / 1 min / 39 sec")
                    msg.msgType == "CALL" -> {
                        val parts = msg.body.split("|", limit = 2)
                        val callTitle = parts.getOrNull(0)?.ifBlank { "Voice call" } ?: "Voice call"
                        val callSubtitle = parts.getOrNull(1)?.ifBlank {
                            when {
                                msg.durationSec in 1..59 -> "${msg.durationSec} sec"
                                msg.durationSec >= 60 -> "${(msg.durationSec + 30) / 60} min"
                                else -> "No answer"
                            }
                        } ?: "No answer"
                        val isVideoCall = resolveCallMessageIsVideo(msg.body)
                        val isMissed = callTitle.contains("Missed", ignoreCase = true) ||
                            callTitle.contains("Silenced", ignoreCase = true) ||
                            callSubtitle.contains("No answer", ignoreCase = true) ||
                            callSubtitle.contains("Missed", ignoreCase = true) ||
                            callSubtitle.contains("Declined", ignoreCase = true) ||
                            callSubtitle.contains("Do Not Disturb", ignoreCase = true) ||
                            callSubtitle.contains("Unknown caller", ignoreCase = true)
                        val callCardBg = if (msg.isFromMe) {
                            if (colors.isDark) Color(0xFF0A4D3E) else Color(0xFFC5F2C0)
                        } else {
                            if (colors.isDark) Color(0xFF182229) else Color(0xFFF3F5F7)
                        }
                        val badgeBg = if (msg.isFromMe) {
                            if (colors.isDark) Color(0xFF1B6151) else Color(0xFFB3E6AE)
                        } else {
                            if (colors.isDark) Color(0xFF28353D) else Color(0xFFE1E6EA)
                        }
                        val iconVec = when {
                            isVideoCall -> Icons.Rounded.Videocam
                            isMissed && !msg.isFromMe -> Icons.Rounded.CallMissed
                            msg.isFromMe -> Icons.Rounded.CallMade
                            else -> Icons.Rounded.CallReceived
                        }
                        val iconTint = if (isMissed && !msg.isFromMe) colors.antiDeleteRed else colors.textPrimary

                        Column(modifier = Modifier.widthIn(min = 195.dp, max = 250.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(callCardBg)
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) onToggleSelect()
                                            else onCallBackMessage(isVideoCall)
                                        },
                                        onLongClick = {
                                            if (isSelectionMode) onLongPress()
                                            else (onLongClickCallMessage ?: onLongPress).invoke()
                                        }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(badgeBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = iconVec,
                                        contentDescription = callTitle,
                                        tint = iconTint,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = callTitle,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.5.sp
                                    )
                                    Text(
                                        text = callSubtitle,
                                        color = colors.textSecondary,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(modifier = Modifier.align(Alignment.End).padding(horizontal = 2.dp)) {
                                MessageMetaCluster(
                                    timeText = formatMessageClock(msg.timestamp, includeSeconds = showSeconds, use24Hour = use24Hour),
                                    isEdited = false,
                                    isEphemeral = false,
                                    isFromMe = msg.isFromMe,
                                    deliveryStatus = msg.deliveryStatus
                                )
                            }
                        }
                    }

                    // Photo / Video / GIF (auto-download images/GIFs; videos stay tap-to-download)
                    msg.msgType == "IMAGE" || msg.msgType == "VIDEO" || msg.msgType == "GIF" -> {
                        val localFile = remember(msg.localMediaPath) {
                            msg.localMediaPath?.takeIf { it.isNotBlank() }?.let { p ->
                                File(p).takeIf { it.exists() && it.length() > 0L }
                            }
                        }
                        val isImageGif = msg.mimeType.equals("image/gif", ignoreCase = true) ||
                            localFile?.extension.equals("gif", ignoreCase = true) == true
                        val isVideo = !isImageGif && (
                            msg.msgType == "VIDEO" ||
                                msg.msgType == "GIF" ||
                                msg.mimeType.startsWith("video/", ignoreCase = true)
                            )
                        val isPureVideo = msg.msgType == "VIDEO" ||
                            (msg.mimeType.startsWith("video/", ignoreCase = true) && msg.msgType != "GIF")
                        val previewMeta by produceState<MediaPreviewMeta?>(
                            initialValue = null,
                            key1 = localFile?.absolutePath,
                            key2 = isVideo,
                            key3 = isImageGif
                        ) {
                            value = if (localFile == null || isImageGif) {
                                null
                            } else if (!isVideo) {
                                withContext(Dispatchers.IO) {
                                    decodeImagePreviewMeta(localFile.absolutePath)
                                }
                            } else {
                                withContext(Dispatchers.IO) {
                                    extractVideoPreviewMeta(localFile.absolutePath)
                                }
                            }
                        }
                        LaunchedEffect(msg.id, localFile != null) {
                            // Never auto-request full VIDEO on scroll so large videos never auto-download
                            if (localFile == null && !isPureVideo && !isDownloadingMedia && mediaDownloadError == null) {
                                onAutoRequestDownload()
                            }
                        }
                        val loadedBmp = previewMeta?.bitmap
                        val origW = previewMeta?.origWidth?.takeIf { it > 0 } ?: (loadedBmp?.width ?: 0)
                        val origH = previewMeta?.origHeight?.takeIf { it > 0 } ?: (loadedBmp?.height ?: 0)
                        val mediaRatio = if (origW > 0 && origH > 0) {
                            (origW.toFloat() / origH.toFloat()).coerceIn(0.45f, 2.2f)
                        } else {
                            null
                        }
                        // Official WhatsApp HD thresholds: videos >= 720p, photos >= 2000px longest side
                        val isHdMedia = if (origW > 0 && origH > 0) {
                            val minSide = minOf(origW, origH)
                            val maxSide = maxOf(origW, origH)
                            if (isPureVideo) (minSide >= 720 || maxSide >= 1280)
                            else if (!isVideo && !isImageGif) maxSide >= 2000
                            else false
                        } else {
                            false
                        }
                        // Auto-adjust card width & height cleanly according to portrait / square / landscape aspect ratio
                        val (mediaWidthDp, mediaHeightDp) = when {
                            isChannel -> null to (mediaRatio?.let { (340f / it).coerceIn(170f, 340f).dp } ?: 220.dp)
                            localFile == null -> 248.dp to 206.dp
                            isImageGif -> 248.dp to 210.dp
                            mediaRatio == null -> 248.dp to 215.dp
                            mediaRatio < 0.90f -> {
                                val w = 248f
                                w.dp to (w / mediaRatio).coerceIn(235f, 325f).dp
                            }
                            mediaRatio <= 1.15f -> {
                                val w = 256f
                                w.dp to (w / mediaRatio).coerceIn(220f, 275f).dp
                            }
                            else -> {
                                val w = 272f
                                w.dp to (w / mediaRatio).coerceIn(148f, 232f).dp
                            }
                        }
                        val mediaBoxSizeMod = if (mediaWidthDp != null) {
                            Modifier.width(mediaWidthDp).height(mediaHeightDp)
                        } else {
                            Modifier.fillMaxWidth().height(mediaHeightDp)
                        }
                        Box(
                            modifier = mediaBoxSizeMod
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            onToggleSelect()
                                        } else if (localFile != null) {
                                            onOpenMedia()
                                        } else {
                                            onRequestDownload()
                                        }
                                    },
                                    onLongClick = onLongPress
                                )
                        ) {
                            if (localFile != null) {
                                if (isImageGif) {
                                    coil.compose.AsyncImage(
                                        model = localFile,
                                        contentDescription = msg.body.ifBlank { "GIF" },
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (loadedBmp != null) {
                                    Image(
                                        bitmap = loadedBmp,
                                        contentDescription = msg.body,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color(0xFF1A2228), Color(0xFF0E1418))
                                                )
                                            )
                                    )
                                }
                                // Soft bottom gradient scrim for legible HD badge, duration, and timestamp
                                if (isVideo || isHdMedia || msg.body.isBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f))
                                                )
                                            )
                                    )
                                }
                                if (isVideo) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(52.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.50f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = "Play video",
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                if (isHdMedia || (isVideo && msg.durationSec > 0)) {
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(start = 8.dp, bottom = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        if (isHdMedia) {
                                            HdMediaBadge()
                                        }
                                        if (isVideo && msg.durationSec > 0) {
                                            Text(
                                                text = formatVideoDuration(msg.durationSec),
                                                color = Color.White,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(colors.quotedBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(CircleShape)
                                                .background(Color(0x73000000)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isDownloadingMedia) {
                                                CircularProgressIndicator(
                                                    color = colors.accentGreen,
                                                    modifier = Modifier.size(28.dp),
                                                    strokeWidth = 2.5.dp
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = if (isVideo || msg.fileSize > 0) Icons.Rounded.Download else Icons.Rounded.Image,
                                                    contentDescription = "Download ${msg.msgType}",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        val sizeLabel = formatMediaSizeLabel(msg.fileSize)
                                        val stateLabel = when {
                                            isDownloadingMedia -> if (sizeLabel.isNotBlank()) "Downloading$sizeLabel..." else "Downloading..."
                                            mediaDownloadError != null -> "Tap to retry$sizeLabel"
                                            else -> (if (isVideo) "Video" else "Photo") + sizeLabel
                                        }
                                        Text(
                                            text = stateLabel,
                                            color = if (mediaDownloadError != null && !isDownloadingMedia) Color(0xFFFFB74D) else colors.textSecondary,
                                            fontSize = 12.5.sp
                                        )
                                    }
                                    if (isVideo && msg.durationSec > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(start = 8.dp, bottom = 7.dp)
                                        ) {
                                            Text(
                                                text = formatVideoDuration(msg.durationSec),
                                                color = Color.White,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                            if (msg.body.isBlank()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 8.dp, bottom = 6.dp)
                                ) {
                                    MessageMetaCluster(
                                        timeText = formatMessageClock(msg.timestamp, includeSeconds = showSeconds, use24Hour = use24Hour),
                                        isEdited = msg.isEdited,
                                        isEphemeral = msg.isEphemeral,
                                        isFromMe = msg.isFromMe,
                                        deliveryStatus = msg.deliveryStatus,
                                        tintOverride = Color.White.copy(alpha = 0.94f),
                                        isPinned = msg.isPinned
                                    )
                                }
                            }
                        }
                        if (msg.body.isNotBlank()) {
                            Box(modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)) {
                                InlineTimestampMessage(
                                    msg = msg,
                                    showDeletedBadge = false,
                                    mentionDisplayResolver = mentionDisplayResolver
                                )
                            }
                        }
                    }

                    // Location card (WhatsApp-style map preview + place name + address + tap to open Maps)
                    parsedLocation != null -> {
                        LocationMessageCard(
                            msg = msg,
                            payload = parsedLocation,
                            colors = colors,
                            onClick = {
                                if (isSelectionMode) onToggleSelect()
                                else openLocationInMaps(context, parsedLocation)
                            },
                            onLongClick = onLongPress,
                            metaCluster = {
                                MessageMetaCluster(
                                    timeText = formatMessageClock(msg.timestamp, includeSeconds = showSeconds, use24Hour = use24Hour),
                                    isEdited = msg.isEdited,
                                    isEphemeral = msg.isEphemeral,
                                    isFromMe = msg.isFromMe,
                                    deliveryStatus = msg.deliveryStatus,
                                    isPinned = msg.isPinned
                                )
                            }
                        )
                    }

                    // Contact card (vCard attachment)
                    isContactCard -> {
                        Box {
                            ContactMessageCard(
                                msg = msg,
                                colors = colors,
                                onLongClick = onLongPress,
                                onMessageClick = { phone ->
                                    if (isSelectionMode) {
                                        onToggleSelect()
                                    } else {
                                        val digits = phone.filter { it.isDigit() }
                                        if (digits.isNotBlank()) {
                                            val targetJid = "$digits@s.whatsapp.net"
                                            onNavigateToChat(targetJid)
                                        }
                                    }
                                }
                            )
                            if (isSelectionMode) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = onToggleSelect,
                                            onLongClick = onLongPress
                                        )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(horizontal = 4.dp)
                        ) {
                            MessageMetaCluster(
                                timeText = formatMessageClock(msg.timestamp, includeSeconds = showSeconds, use24Hour = use24Hour),
                                isEdited = msg.isEdited,
                                isEphemeral = msg.isEphemeral,
                                isFromMe = msg.isFromMe,
                                deliveryStatus = msg.deliveryStatus,
                                isPinned = msg.isPinned
                            )
                        }
                    }

                    // Documents (PDFs, Office files, archives, etc.)
                    isDocCard -> {
                        val localDocFile = remember(msg.localMediaPath) {
                            msg.localMediaPath?.takeIf { it.isNotBlank() }?.let { p ->
                                File(p).takeIf { it.exists() && it.length() > 0L }
                            }
                        }
                        LaunchedEffect(msg.id, localDocFile != null) {
                            if (localDocFile == null && !isDownloadingMedia && mediaDownloadError == null) {
                                onAutoRequestDownload()
                            }
                        }
                        DocumentMessageCard(
                            msg = msg,
                            localFile = localDocFile,
                            colors = colors,
                            isDownloading = isDownloadingMedia,
                            errorMsg = mediaDownloadError,
                            onClick = {
                                if (isSelectionMode) onToggleSelect()
                                else if (localDocFile != null) onOpenDocument()
                                else onRequestDownload()
                            },
                            onLongClick = onLongPress,
                            onSaveToDownloads = if (localDocFile != null && !isSelectionMode) onSaveDocument else null
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(horizontal = 4.dp)
                        ) {
                            MessageMetaCluster(
                                timeText = formatMessageClock(msg.timestamp, includeSeconds = showSeconds, use24Hour = use24Hour),
                                isEdited = msg.isEdited,
                                isEphemeral = msg.isEphemeral,
                                isFromMe = msg.isFromMe,
                                deliveryStatus = msg.deliveryStatus,
                                isPinned = msg.isPinned
                            )
                        }
                    }

                    // Sticker message (transparent borderless, WebP render, floating timestamp pill)
                    msg.msgType == "STICKER" -> {
                        StickerMessageBubbleItem(
                            msg = msg,
                            showSeconds = showSeconds,
                            isDownloadingMedia = isDownloadingMedia,
                            mediaDownloadError = mediaDownloadError,
                            isSelectionMode = isSelectionMode,
                            onToggleSelect = onToggleSelect,
                            onAutoRequestDownload = onAutoRequestDownload,
                            onRequestDownload = onRequestDownload,
                            onOpenSticker = onOpenSticker,
                            onLongPress = onLongPress
                        )
                    }

                    // Undecryptable placeholder (only shown when sender retry & phone peer-sync timed out)
                    msg.msgType == "UNDECRYPTABLE" && !msg.isDeletedBySender -> {
                        Row(
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = msg.body.ifBlank { "Waiting for this message. This may take a while." },
                                color = colors.textSecondary,
                                fontStyle = FontStyle.Italic,
                                fontSize = 13.5.sp,
                                lineHeight = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(modifier = Modifier.align(Alignment.End)) {
                            MessageMetaCluster(
                                timeText = formatMessageClock(msg.timestamp, includeSeconds = showSeconds, use24Hour = use24Hour),
                                isEdited = false,
                                isEphemeral = msg.isEphemeral,
                                isFromMe = msg.isFromMe,
                                deliveryStatus = msg.deliveryStatus,
                                isPinned = msg.isPinned
                            )
                        }
                    }

                    // Standard Text, Sticker, or Anti-Deleted text bubble
                    else -> {
                        InlineTimestampMessage(
                            msg = msg,
                            showDeletedBadge = false,
                            mentionDisplayResolver = mentionDisplayResolver
                        )
                    }
                }
            }
        }
        // Clickable overlapping Reaction pill underneath the bubble/card
        val resolvedReactionText = reactionBadgeText.ifBlank { msg.reactionSummary }
        if (resolvedReactionText.isNotBlank()) {
            val reactInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .offset(y = (-6).dp)
                    .padding(horizontal = 8.dp)
                    .expressivePressScale(reactInteraction, pressedScale = 0.88f)
                    .clip(CircleShape)
                    .background(if (colors.isDark) Color(0xFF23282C) else colors.elevatedBar)
                    .border(1.4.dp, colors.chatCanvas, CircleShape)
                    .then(
                        if (onClickReaction != null && !isSelectionMode) {
                            Modifier.clickable(
                                interactionSource = reactInteraction,
                                indication = null,
                                onClick = onClickReaction
                            )
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = resolvedReactionText,
                    color = colors.textPrimary,
                    fontSize = 14.5.sp
                )
            }
        }
        }
        if (!effectiveOutgoing && isMediaCard && hasVaultFile && onQuickForward != null && !isSelectionMode && !isChannel) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(start = 10.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (colors.isDark) Color(0xD41F2428) else Color(0xE0FFFFFF))
                    .clickable(onClick = onQuickForward),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Forward,
                    contentDescription = "Forward media",
                    tint = if (colors.isDark) Color.White else colors.textPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

private fun isHiddenPlaceholderMessage(msg: MessageEntity): Boolean {
    return msg.msgType == "UNKNOWN" &&
        msg.body.isBlank() &&
        msg.localMediaPath.isNullOrBlank() &&
        !msg.isViewOnce &&
        !msg.isDeletedBySender
}

private fun isSameCalendarDay(ts1: Long, ts2: Long): Boolean {
    if (ts1 <= 0L || ts2 <= 0L) return false
    val c1 = Calendar.getInstance().apply { timeInMillis = ts1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = ts2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
        c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}

private fun formatChatDateHeader(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, -1)
    }
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    return when {
        target.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            target.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"
        target.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
            target.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Yesterday"
        else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
private fun DateHeaderPill(timestamp: Long) {
    val colors = LocalWaColors.current
    val pillBg = if (colors.isDark) Color(0xE60F1318) else Color(0xF0ECF2F6)
    val pillBorder = if (colors.isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.06f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(pillBg)
                .border(0.65.dp, pillBorder, CircleShape)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = formatChatDateHeader(timestamp),
                color = colors.textSecondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ComposerStickerIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pad = w * 0.12f
        val left = pad
        val top = pad
        val right = w - pad
        val bottom = h - pad
        val r = (right - left) * 0.26f
        val fold = (right - left) * 0.34f
        val stroke = Stroke(
            width = (w * 0.085f).coerceAtLeast(1.5f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val outline = Path().apply {
            moveTo(left + r, top)
            lineTo(right - r, top)
            quadraticTo(right, top, right, top + r)
            lineTo(right, bottom - fold)
            lineTo(right - fold, bottom)
            lineTo(left + r, bottom)
            quadraticTo(left, bottom, left, bottom - r)
            lineTo(left, top + r)
            quadraticTo(left, top, left + r, top)
            close()
        }
        drawPath(path = outline, color = tint, style = stroke)
        val curl = Path().apply {
            moveTo(right - fold, bottom)
            lineTo(right - fold, bottom - fold * 0.72f)
            quadraticTo(right - fold, bottom - fold, right - fold * 0.72f, bottom - fold)
            lineTo(right, bottom - fold)
        }
        drawPath(path = curl, color = tint, style = stroke)
    }
}

@Composable
private fun ContextMenuActionGlyph(
    action: SelectionBarAction?,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = (w * 0.075f).coerceAtLeast(1.45f)
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (action) {
            SelectionBarAction.REPLY -> {
                // Hollow outlined curved reply arrow pointing left
                val p = Path().apply {
                    moveTo(w * 0.42f, h * 0.20f)
                    lineTo(w * 0.14f, h * 0.45f)
                    lineTo(w * 0.42f, h * 0.70f)
                    lineTo(w * 0.42f, h * 0.54f)
                    cubicTo(w * 0.64f, h * 0.54f, w * 0.78f, h * 0.62f, w * 0.87f, h * 0.79f)
                    cubicTo(w * 0.86f, h * 0.50f, w * 0.68f, h * 0.36f, w * 0.42f, h * 0.36f)
                    close()
                }
                drawPath(path = p, color = tint, style = stroke)
            }
            SelectionBarAction.FORWARD -> {
                // Hollow outlined curved forward arrow pointing right
                val p = Path().apply {
                    moveTo(w * 0.58f, h * 0.20f)
                    lineTo(w * 0.86f, h * 0.45f)
                    lineTo(w * 0.58f, h * 0.70f)
                    lineTo(w * 0.58f, h * 0.54f)
                    cubicTo(w * 0.36f, h * 0.54f, w * 0.22f, h * 0.62f, w * 0.13f, h * 0.79f)
                    cubicTo(w * 0.14f, h * 0.50f, w * 0.32f, h * 0.36f, w * 0.58f, h * 0.36f)
                    close()
                }
                drawPath(path = p, color = tint, style = stroke)
            }
            SelectionBarAction.COPY -> {
                // Two crisp overlapping outlined pages
                val cr = CornerRadius(w * 0.10f, h * 0.10f)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.18f, h * 0.28f),
                    size = Size(w * 0.52f, h * 0.58f),
                    cornerRadius = cr,
                    style = stroke
                )
                val back = Path().apply {
                    moveTo(w * 0.32f, h * 0.28f)
                    lineTo(w * 0.32f, h * 0.20f)
                    quadraticTo(w * 0.32f, h * 0.13f, w * 0.39f, h * 0.13f)
                    lineTo(w * 0.75f, h * 0.13f)
                    quadraticTo(w * 0.82f, h * 0.13f, w * 0.82f, h * 0.20f)
                    lineTo(w * 0.82f, h * 0.64f)
                    quadraticTo(w * 0.82f, h * 0.71f, w * 0.75f, h * 0.71f)
                    lineTo(w * 0.70f, h * 0.71f)
                }
                drawPath(path = back, color = tint, style = stroke)
            }
            SelectionBarAction.EDIT -> {
                // Slim hollow diagonal pencil
                val body = Path().apply {
                    moveTo(w * 0.18f, h * 0.82f)
                    lineTo(w * 0.24f, h * 0.62f)
                    lineTo(w * 0.68f, h * 0.18f)
                    quadraticTo(w * 0.74f, h * 0.12f, w * 0.80f, h * 0.18f)
                    lineTo(w * 0.82f, h * 0.20f)
                    quadraticTo(w * 0.88f, h * 0.26f, w * 0.82f, h * 0.32f)
                    lineTo(w * 0.38f, h * 0.76f)
                    close()
                }
                drawPath(path = body, color = tint, style = stroke)
                drawLine(
                    color = tint,
                    start = Offset(w * 0.59f, h * 0.27f),
                    end = Offset(w * 0.73f, h * 0.41f),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
            }
            SelectionBarAction.DELETE -> {
                // Crisp hollow trash bin with top handle, lid bar, tapered body, and 2 vertical ribs
                drawLine(
                    color = tint,
                    start = Offset(w * 0.18f, h * 0.26f),
                    end = Offset(w * 0.82f, h * 0.26f),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
                val handle = Path().apply {
                    moveTo(w * 0.38f, h * 0.26f)
                    lineTo(w * 0.38f, h * 0.18f)
                    quadraticTo(w * 0.38f, h * 0.14f, w * 0.43f, h * 0.14f)
                    lineTo(w * 0.57f, h * 0.14f)
                    quadraticTo(w * 0.62f, h * 0.14f, w * 0.62f, h * 0.18f)
                    lineTo(w * 0.62f, h * 0.26f)
                }
                drawPath(path = handle, color = tint, style = stroke)
                val bin = Path().apply {
                    moveTo(w * 0.26f, h * 0.26f)
                    lineTo(w * 0.30f, h * 0.80f)
                    quadraticTo(w * 0.31f, h * 0.86f, w * 0.37f, h * 0.86f)
                    lineTo(w * 0.63f, h * 0.86f)
                    quadraticTo(w * 0.69f, h * 0.86f, w * 0.70f, h * 0.80f)
                    lineTo(w * 0.74f, h * 0.26f)
                }
                drawPath(path = bin, color = tint, style = stroke)
                drawLine(
                    color = tint,
                    start = Offset(w * 0.43f, h * 0.40f),
                    end = Offset(w * 0.44f, h * 0.73f),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.57f, h * 0.40f),
                    end = Offset(w * 0.56f, h * 0.73f),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
            }
            SelectionBarAction.MESSAGE_INFO -> {
                // Crisp outlined info circle with dot + vertical stem
                drawCircle(
                    color = tint,
                    radius = w * 0.38f,
                    center = Offset(w * 0.50f, h * 0.50f),
                    style = stroke
                )
                drawCircle(
                    color = tint,
                    radius = sw * 0.58f,
                    center = Offset(w * 0.50f, h * 0.34f)
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.50f, h * 0.46f),
                    end = Offset(w * 0.50f, h * 0.69f),
                    strokeWidth = sw,
                    cap = StrokeCap.Round
                )
            }
            else -> {
                // Outlined circle with 3 crisp centered dots ("More")
                drawCircle(
                    color = tint,
                    radius = w * 0.38f,
                    center = Offset(w * 0.50f, h * 0.50f),
                    style = stroke
                )
                val dotR = sw * 0.62f
                drawCircle(color = tint, radius = dotR, center = Offset(w * 0.34f, h * 0.50f))
                drawCircle(color = tint, radius = dotR, center = Offset(w * 0.50f, h * 0.50f))
                drawCircle(color = tint, radius = dotR, center = Offset(w * 0.66f, h * 0.50f))
            }
        }
    }
}

@Composable
private fun SystemNoticePill(text: String) {
    if (text.isBlank()) return
    val colors = LocalWaColors.current
    val pillBg = if (colors.isDark) Color(0xE60F1318) else Color(0xF0ECF2F6)
    val pillBorder = if (colors.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(pillBg)
                .border(0.65.dp, pillBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 13.dp, vertical = 6.dp)
        ) {
            Text(
                text = text,
                color = colors.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun HdMediaBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.94f))
            .padding(horizontal = 3.5.dp, vertical = 0.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "HD",
            color = Color(0xFF121619),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 11.sp
        )
    }
}

@Composable
private fun ComposerQuickPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    pillBg: Color,
    pillBorder: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .expressivePressScale(interaction, pressedScale = 0.94f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(CircleShape)
                .background(pillBg)
                .border(0.8.dp, pillBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(21.dp)
            )
        }
        Text(
            text = label,
            color = labelColor,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun ComposerAttachTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    tileBg: Color,
    tileBorder: Color,
    labelColor: Color,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .width(68.dp)
            .expressivePressScale(interaction, pressedScale = 0.90f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(tileBg)
                .border(0.8.dp, tileBorder, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            text = label,
            color = labelColor,
            fontSize = 12.5.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class MediaPreviewMeta(
    val bitmap: ImageBitmap?,
    val origWidth: Int,
    val origHeight: Int
)

private fun ensureSoftwareBitmap(bmp: Bitmap): Bitmap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bmp.config == Bitmap.Config.HARDWARE) {
        val copy = runCatching { bmp.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
        if (copy != null) {
            bmp.recycle()
            return copy
        }
    }
    return bmp
}

private fun isMostlyBlackFrame(bmp: Bitmap): Boolean {
    val w = bmp.width
    val h = bmp.height
    if (w <= 0 || h <= 0) return true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bmp.config == Bitmap.Config.HARDWARE) {
        return false
    }
    var totalLum = 0L
    var maxLum = 0L
    var samples = 0
    for (gx in 1..4) {
        val x = ((w * gx) / 5).coerceIn(0, w - 1)
        for (gy in 1..4) {
            val y = ((h * gy) / 5).coerceIn(0, h - 1)
            val px = bmp.getPixel(x, y)
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            val lum = (r * 3L + g * 6L + b) / 10L
            totalLum += lum
            if (lum > maxLum) maxLum = lum
            samples++
        }
    }
    return samples == 0 || ((totalLum / samples) < 12L && maxLum < 26L)
}

private fun decodeVideoFrameSafe(
    retriever: MediaMetadataRetriever,
    timeUs: Long,
    option: Int
): Bitmap? {
    val raw = runCatching {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                val params = MediaMetadataRetriever.BitmapParams().apply {
                    preferredConfig = Bitmap.Config.ARGB_8888
                }
                retriever.getScaledFrameAtTime(timeUs, option, 720, 720, params)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 -> {
                retriever.getScaledFrameAtTime(timeUs, option, 720, 720)
            }
            else -> {
                retriever.getFrameAtTime(timeUs, option)
            }
        }
    }.getOrNull() ?: runCatching {
        retriever.getFrameAtTime(timeUs, option)
    }.getOrNull() ?: return null
    return ensureSoftwareBitmap(raw)
}

private fun extractVideoPreviewMeta(path: String): MediaPreviewMeta {
    val retriever = MediaMetadataRetriever()
    var origW = 0
    var origH = 0
    return try {
        retriever.setDataSource(path)
        val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val swapped = (rot % 180) != 0
        origW = if (swapped) rawH else rawW
        origH = if (swapped) rawW else rawH
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val maxUs = ((durationMs - 120L).coerceAtLeast(0L)) * 1_000L

        var fallback: Bitmap? = null
        var chosen: Bitmap? = null

        // 1. Fast sync check first (instant on normal videos whose keyframe isn't black)
        val fastFrame = decodeVideoFrameSafe(
            retriever,
            minOf(500_000L, maxUs),
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        )
        if (fastFrame != null) {
            if (!isMostlyBlackFrame(fastFrame)) {
                chosen = fastFrame
            } else {
                fallback = fastFrame
            }
        }

        // 2. If keyframe was black/null (common on short phone videos with a single black GOP at t=0),
        //    probe exact non-keyframes with OPTION_CLOSEST across the video timeline.
        if (chosen == null) {
            val probeUs = linkedSetOf<Long>()
            probeUs.add(minOf(400_000L, maxUs))
            probeUs.add(minOf(1_100_000L, maxUs))
            if (durationMs > 1_500L) {
                probeUs.add((durationMs * 330L).coerceAtMost(maxUs))
                probeUs.add((durationMs * 500L).coerceAtMost(maxUs))
                probeUs.add((durationMs * 700L).coerceAtMost(maxUs))
            }
            probeUs.add(0L)
            for (tUs in probeUs) {
                val frame = decodeVideoFrameSafe(retriever, tUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
                if (!isMostlyBlackFrame(frame)) {
                    chosen = frame
                    break
                }
                if (fallback == null) {
                    fallback = frame
                } else {
                    frame.recycle()
                }
            }
        }

        // 3. Fallback to Android ThumbnailUtils on API 29+ if retriever couldn't decode a frame
        if (chosen == null && fallback == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val thumb = runCatching {
                ThumbnailUtils.createVideoThumbnail(File(path), android.util.Size(720, 720), null)
            }.getOrNull()?.let(::ensureSoftwareBitmap)
            if (thumb != null) {
                chosen = thumb
            }
        }

        val raw = chosen ?: fallback ?: retriever.frameAtTime?.let(::ensureSoftwareBitmap)
        if (chosen != null && fallback != null && fallback !== chosen) {
            fallback.recycle()
        }
        val finalBmp = raw?.let { bmp ->
            val maxSide = maxOf(bmp.width, bmp.height)
            if (maxSide <= 720) bmp
            else {
                val scale = 720f / maxSide.toFloat()
                val w = (bmp.width * scale).toInt().coerceAtLeast(1)
                val h = (bmp.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
                if (scaled !== bmp) bmp.recycle()
                scaled
            }
        }
        val resolvedW = origW.takeIf { it > 0 } ?: (finalBmp?.width ?: 0)
        val resolvedH = origH.takeIf { it > 0 } ?: (finalBmp?.height ?: 0)
        MediaPreviewMeta(finalBmp?.asImageBitmap(), resolvedW, resolvedH)
    } catch (_: Throwable) {
        MediaPreviewMeta(null, origW, origH)
    } finally {
        runCatching { retriever.release() }
    }
}

private fun decodeImagePreviewMeta(path: String): MediaPreviewMeta {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val rawW = bounds.outWidth.coerceAtLeast(0)
        val rawH = bounds.outHeight.coerceAtLeast(0)
        val exifOrientation = runCatching {
            android.media.ExifInterface(path).getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (exifOrientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val swapped = degrees == 90f || degrees == 270f
        val origW = if (swapped) rawH else rawW
        val origH = if (swapped) rawW else rawH
        var sample = 1
        while (rawW / sample > 1080 || rawH / sample > 1080) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = BitmapFactory.decodeFile(path, opts)
        if (bmp != null && degrees != 0f) {
            val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            if (rotated !== bmp) bmp.recycle()
            bmp = rotated
        }
        MediaPreviewMeta(
            bitmap = bmp?.asImageBitmap(),
            origWidth = origW.takeIf { it > 0 } ?: (bmp?.width ?: 0),
            origHeight = origH.takeIf { it > 0 } ?: (bmp?.height ?: 0)
        )
    }.getOrElse { MediaPreviewMeta(null, 0, 0) }
}

private fun extractVideoThumbnailBitmap(path: String): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val maxUs = ((durationMs - 120L).coerceAtLeast(0L)) * 1_000L
        val fast = decodeVideoFrameSafe(retriever, minOf(500_000L, maxUs), MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        if (fast != null && !isMostlyBlackFrame(fast)) return fast
        var fallback: Bitmap? = fast
        val probes = longArrayOf(
            minOf(400_000L, maxUs),
            minOf(1_100_000L, maxUs),
            (durationMs * 500L).coerceAtMost(maxUs),
            0L
        )
        for (tUs in probes) {
            val frame = decodeVideoFrameSafe(retriever, tUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
            if (!isMostlyBlackFrame(frame)) {
                fallback?.recycle()
                return frame
            }
            if (fallback == null) fallback = frame else frame.recycle()
        }
        fallback
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun decodeQuotedThumbnailBitmap(path: String, isVideo: Boolean): ImageBitmap? {
    val file = File(path)
    if (!file.exists() || file.length() <= 0L) return null
    return runCatching {
        if (isVideo) {
            extractVideoPreviewMeta(path).bitmap
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 180 || bounds.outHeight / sample > 180) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
        }
    }.getOrNull()
}

private fun formatMediaSizeLabel(bytes: Long): String {
    if (bytes <= 0L) return ""
    return if (bytes >= 1024L * 1024L) {
        String.format(Locale.US, " · %.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
    } else {
        " · ${maxOf(1L, bytes / 1024L)} KB"
    }
}

private fun formatVideoDuration(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    val mins = s / 60
    val rem = s % 60
    return String.format(Locale.US, "%d:%02d", mins, rem)
}

private data class SenderDisplayInfo(
    val primary: String,
    val secondaryPhone: String = "",
    val keyForColor: String = primary
)

private fun isMeaningfulDisplayName(candidate: String?, jid: String = ""): Boolean {
    val t = candidate?.trim().orEmpty()
    if (t.isBlank() || t.contains("@")) return false
    val bare = t.removePrefix("+").replace(" ", "").replace("-", "")
    if (bare.isNotEmpty() && bare.all { it.isDigit() }) {
        val jidUser = jid.substringBefore("@")
        if (bare == jidUser || bare.length >= 7) return false
    }
    return true
}

private fun formatIntlPhone(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    if (digits.isBlank()) return ""
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

private fun extractAvatarInitialChar(displayName: String): String? {
    val cleaned = displayName.removePrefix("~").trim()
    if (cleaned.isBlank() || cleaned.startsWith("+")) return null
    val cp = cleaned.codePointAt(0)
    if (Character.isDigit(cp)) return null
    return String(Character.toChars(cp)).uppercase(Locale.getDefault())
}

private fun resolveSenderDisplayInfo(
    rawJidOrText: String,
    explicitName: String,
    isSelf: Boolean,
    participantMeta: Map<String, ResolvedParticipantUi>,
    chatsByJid: Map<String, ChatEntity>,
    senderPushByJid: Map<String, String>,
    isGroupAnnounceLocked: Boolean = false
): SenderDisplayInfo {
    if (isSelf) return SenderDisplayInfo(primary = "You", keyForColor = rawJidOrText)
    val jid = rawJidOrText.trim()
    if (jid.endsWith("@g.us", ignoreCase = true) ||
        DeviceContactFallbackResolver.isGroupJidLikeIdentifier(jid)
    ) {
        return SenderDisplayInfo(primary = "", keyForColor = jid.ifBlank { "group" })
    }
    val meta = participantMeta[jid]
    val altJid = meta?.altJid.orEmpty()
    val chatMatch = chatsByJid[jid] ?: altJid.takeIf { it.isNotBlank() }?.let { chatsByJid[it] }

    val savedName = meta?.savedName?.takeIf { isMeaningfulDisplayName(it, jid) }.orEmpty()
    val pushName = sequenceOf(
        explicitName.takeIf { isMeaningfulDisplayName(it, jid) },
        meta?.pushName?.takeIf { isMeaningfulDisplayName(it, jid) },
        senderPushByJid[jid]?.takeIf { isMeaningfulDisplayName(it, jid) },
        altJid.takeIf { it.isNotBlank() }?.let { senderPushByJid[it]?.takeIf { n -> isMeaningfulDisplayName(n, it) } },
        chatMatch?.name?.takeIf { isMeaningfulDisplayName(it, jid) }
    ).firstOrNull().orEmpty()

    val rawPhone = when {
        !meta?.phone.isNullOrBlank() -> meta!!.phone
        jid.endsWith("@s.whatsapp.net") -> jid.substringBefore("@")
        altJid.endsWith("@s.whatsapp.net") -> altJid.substringBefore("@")
        else -> {
            val bare = jid.removePrefix("+")
            if (jid.all { it.isDigit() || it == '+' } && bare.length in 7..13 && !jid.endsWith("@lid")) bare else ""
        }
    }
    val formattedPhone = formatIntlPhone(rawPhone)

    return when {
        savedName.isNotBlank() ->
            SenderDisplayInfo(primary = savedName, keyForColor = jid.ifBlank { savedName })
        pushName.isNotBlank() ->
            SenderDisplayInfo(primary = "~ $pushName", secondaryPhone = formattedPhone, keyForColor = jid.ifBlank { pushName })
        formattedPhone.isNotBlank() ->
            SenderDisplayInfo(primary = formattedPhone, keyForColor = jid.ifBlank { formattedPhone })
        jid.endsWith("@lid") -> {
            val tail = jid.substringBefore("@").takeLast(4)
            SenderDisplayInfo(primary = if (tail.isNotBlank()) "~ User · …$tail" else "~ User", keyForColor = jid)
        }
        jid.contains("@") ->
            SenderDisplayInfo(primary = jid.substringBefore("@"), keyForColor = jid)
        jid.isNotBlank() ->
            SenderDisplayInfo(primary = jid, keyForColor = jid)
        else ->
            SenderDisplayInfo(
                primary = if (isGroupAnnounceLocked) "Group Admin" else "~ Member",
                keyForColor = if (isGroupAnnounceLocked) "admin" else "participant"
            )
    }
}

private fun resolveQuotedBannerSender(
    msg: MessageEntity,
    isChannel: Boolean,
    channelDisplayName: String,
    loadedMessagesById: Map<String, MessageEntity>,
    quotedMsgMeta: Map<String, QuotedSenderMeta>,
    participantMeta: Map<String, ResolvedParticipantUi>,
    chatsByJid: Map<String, ChatEntity>,
    senderPushByJid: Map<String, String>
): SenderDisplayInfo {
    val rawQuoted = msg.quotedSender.orEmpty().trim()
    if (isChannel && (rawQuoted.isBlank() || rawQuoted == msg.chatJid || rawQuoted.endsWith("@newsletter"))) {
        return SenderDisplayInfo(primary = channelDisplayName.ifBlank { "Channel" }, keyForColor = msg.chatJid)
    }
    if (rawQuoted.endsWith("@newsletter")) {
        return SenderDisplayInfo(primary = channelDisplayName.ifBlank { rawQuoted.substringBefore("@") }, keyForColor = rawQuoted)
    }

    val quotedId = msg.quotedId.orEmpty()
    val loadedQ = loadedMessagesById[quotedId]
    val cachedQ = quotedMsgMeta[quotedId]
    val qFromMe = loadedQ?.isFromMe ?: cachedQ?.isFromMe ?: false
    val qSenderJidFromMsg = loadedQ?.senderJid ?: cachedQ?.senderJid.orEmpty()
    val qSenderNameFromMsg = loadedQ?.senderName ?: cachedQ?.senderName.orEmpty()

    val chosenJid = when {
        qSenderJidFromMsg.endsWith("@s.whatsapp.net") -> qSenderJidFromMsg
        rawQuoted.endsWith("@s.whatsapp.net") -> rawQuoted
        qSenderJidFromMsg.contains("@") -> qSenderJidFromMsg
        rawQuoted.isNotBlank() -> rawQuoted
        else -> qSenderJidFromMsg
    }
    val explicitName = if (isMeaningfulDisplayName(qSenderNameFromMsg, chosenJid)) {
        qSenderNameFromMsg
    } else if (!rawQuoted.contains("@") && isMeaningfulDisplayName(rawQuoted, chosenJid)) {
        rawQuoted
    } else ""

    val isSelf = qFromMe || participantMeta[chosenJid]?.isSelf == true || participantMeta[rawQuoted]?.isSelf == true
    return resolveSenderDisplayInfo(
        rawJidOrText = chosenJid,
        explicitName = explicitName,
        isSelf = isSelf,
        participantMeta = participantMeta,
        chatsByJid = chatsByJid,
        senderPushByJid = senderPushByJid
    )
}

@Composable
private fun StickerMessageBubbleItem(
    msg: MessageEntity,
    showSeconds: Boolean,
    isDownloadingMedia: Boolean,
    mediaDownloadError: String?,
    isSelectionMode: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onAutoRequestDownload: () -> Unit,
    onRequestDownload: () -> Unit,
    onOpenSticker: () -> Unit = {},
    onLongPress: () -> Unit
) {
    val localFile = remember(msg.localMediaPath) {
        msg.localMediaPath?.takeIf { it.isNotBlank() }?.let { p ->
            File(p).takeIf { it.exists() && it.length() > 0L }
        }
    }
    var replayNonce by remember(msg.id) { mutableStateOf(0) }

    LaunchedEffect(msg.id, localFile != null) {
        if (localFile == null && !isDownloadingMedia && mediaDownloadError == null) {
            onAutoRequestDownload()
        }
    }

    Box(
        modifier = Modifier
            .sizeIn(minWidth = 120.dp, minHeight = 120.dp, maxWidth = 160.dp, maxHeight = 160.dp)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelect()
                    } else if (localFile == null) {
                        onRequestDownload()
                    } else {
                        replayNonce++
                        onOpenSticker()
                    }
                },
                onLongClick = onLongPress
            )
    ) {
        if (localFile != null) {
            AnimatedStickerImage(
                file = localFile,
                contentDescription = "Sticker",
                maxDecodeDimPx = 512,
                replayNonce = replayNonce,
                modifier = Modifier
                    .size(150.dp)
                    .align(Alignment.Center),
                placeholder = {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.08f))
                    )
                }
            )
        } else if (isDownloadingMedia) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color.White.copy(alpha = 0.8f),
                    strokeWidth = 2.5.dp
                )
            }
        } else {
            // Placeholder / tap to download
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.15f))
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tab_sticker),
                        contentDescription = "Download sticker",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (mediaDownloadError != null) "Retry" else "Tap to load",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // WhatsApp-style floating dark timestamp pill
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 4.dp, bottom = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x66000000))
                .padding(horizontal = 5.dp, vertical = 2.dp)
        ) {
            MessageMetaCluster(
                timeText = formatMessageClock(msg.timestamp, includeSeconds = showSeconds, use24Hour = LocalWuppUse24Hour.current),
                isEdited = msg.isEdited,
                isEphemeral = msg.isEphemeral,
                isFromMe = msg.isFromMe,
                deliveryStatus = msg.deliveryStatus,
                isPinned = msg.isPinned,
                tintOverride = Color.White.copy(alpha = 0.92f)
            )
        }
    }
}

private data class ParticipantPopupTarget(
    val jid: String,
    val name: String,
    val avatarPath: String?,
    val phoneDisplay: String = "",
    val altJid: String = "",
    val isAdmin: Boolean = false
)

internal fun resolveCallMessageIsVideo(callBody: String): Boolean =
    callBody.substringBefore("|").contains("Video", ignoreCase = true)

internal fun formatReactionBadgeSummary(
    legacySummary: String,
    counts: List<Pair<String, Int>>
): String {
    if (counts.isEmpty()) return legacySummary
    val emojis = counts.map { it.first }.filter { it.isNotBlank() }.distinct()
    if (emojis.isEmpty()) return legacySummary
    val total = counts.sumOf { it.second }
    return if (total > 1) "${emojis.joinToString("")} $total" else emojis.joinToString(" ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupParticipantActionSheet(
    target: ParticipantPopupTarget,
    formattedPhone: String,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onVideoCall: () -> Unit,
    onMessage: () -> Unit,
    onSaveContact: () -> Unit,
    onViewProfilePhoto: () -> Unit,
    onOpenContactInfo: () -> Unit
) {
    val colors = LocalWaColors.current
    val sheetBg = if (colors.isDark) Color(0xFF14181B) else colors.elevatedBar
    val cardBg = if (colors.isDark) Color(0xFF1F2428) else colors.appSurface
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onViewProfilePhoto)
            ) {
                WhatsAppAvatar(
                    jid = target.jid,
                    avatarPath = target.avatarPath,
                    isGroup = false,
                    size = 76.dp,
                    initialChar = extractAvatarInitialChar(target.name)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = target.name.ifBlank { target.jid.substringBefore("@") },
                    color = colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (target.isAdmin) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = "Group admin",
                        tint = colors.accentGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (formattedPhone.isNotBlank() && formattedPhone != target.name) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formattedPhone,
                    color = colors.textSecondary,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top
            ) {
                ParticipantQuickActionButton(
                    icon = Icons.Rounded.Call,
                    label = "Call",
                    onClick = onCall
                )
                ParticipantQuickActionButton(
                    icon = Icons.Rounded.Videocam,
                    label = "Video",
                    onClick = onVideoCall
                )
                ParticipantQuickActionButton(
                    icon = Icons.AutoMirrored.Rounded.Chat,
                    label = "Message",
                    onClick = onMessage
                )
                ParticipantQuickActionButton(
                    icon = Icons.Rounded.PersonAdd,
                    label = "Save",
                    onClick = onSaveContact
                )
                ParticipantQuickActionButton(
                    icon = Icons.Rounded.Person,
                    label = "Photo",
                    onClick = onViewProfilePhoto
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBg)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenContactInfo)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(21.dp)
                    )
                    Text(
                        text = "Contact info",
                        color = colors.textPrimary,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                HorizontalDivider(color = colors.divider.copy(alpha = 0.5f), thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(21.dp)
                    )
                    Text(
                        text = "Close",
                        color = colors.textPrimary,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ParticipantQuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val colors = LocalWaColors.current
    Column(
        modifier = Modifier
            .widthIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(colors.brightGreen.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = colors.brightGreen,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = label,
            color = colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
