package com.wa.beastclient.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.MarkChatRead
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.R
import com.wa.beastclient.data.db.ChatEntity
import com.wa.beastclient.ui.theme.LocalWaColors
import com.wa.beastclient.ui.theme.LocalWuppClassicUi

internal data class ChatSelectionState(
    val singleTarget: ChatEntity? = null,
    val multiSelectedJids: Set<String> = emptySet()
) {
    val hasAnySelection: Boolean
        get() = singleTarget != null || multiSelectedJids.isNotEmpty()

    val selectedCount: Int
        get() = when {
            multiSelectedJids.isNotEmpty() -> multiSelectedJids.size
            singleTarget != null -> 1
            else -> 0
        }

    fun isSelected(jid: String): Boolean =
        singleTarget?.jid == jid || jid in multiSelectedJids
}

internal fun applyChatLongPress(
    state: ChatSelectionState,
    target: ChatEntity,
    allChatsByJid: Map<String, ChatEntity> = emptyMap()
): ChatSelectionState {
    if (!state.hasAnySelection) {
        return ChatSelectionState(singleTarget = target, multiSelectedJids = emptySet())
    }
    return applyChatTapInSelection(state = state, target = target, allChatsByJid = allChatsByJid)
}

internal fun applyChatTapInSelection(
    state: ChatSelectionState,
    target: ChatEntity,
    allChatsByJid: Map<String, ChatEntity> = emptyMap()
): ChatSelectionState {
    if (state.multiSelectedJids.isNotEmpty()) {
        val next = if (target.jid in state.multiSelectedJids) {
            state.multiSelectedJids - target.jid
        } else {
            state.multiSelectedJids + target.jid
        }
        return when {
            next.isEmpty() -> ChatSelectionState()
            next.size == 1 -> {
                val remaining = allChatsByJid[next.first()]
                if (remaining != null) {
                    ChatSelectionState(singleTarget = remaining, multiSelectedJids = emptySet())
                } else {
                    ChatSelectionState(singleTarget = null, multiSelectedJids = next)
                }
            }
            else -> ChatSelectionState(singleTarget = null, multiSelectedJids = next)
        }
    }

    val single = state.singleTarget
    if (single != null) {
        return if (single.jid == target.jid) {
            ChatSelectionState()
        } else {
            ChatSelectionState(
                singleTarget = null,
                multiSelectedJids = linkedSetOf(single.jid, target.jid)
            )
        }
    }

    return state
}

internal enum class ChatContextMenuAction {
    PIN_UNPIN,
    MUTE_UNMUTE,
    MARK_UNREAD_READ,
    ARCHIVE_UNARCHIVE,
    FAVORITE_UNFAVORITE,
    QUICK_CONTROLS,
    INFO,
    DELETE
}

internal data class ChatContextMenuLayout(
    val primaryActions: List<ChatContextMenuAction>,
    val moreActions: List<ChatContextMenuAction>
)

internal fun resolveChatContextMenuLayout(chat: ChatEntity): ChatContextMenuLayout {
    val isChannel = chat.jid.endsWith("@newsletter", ignoreCase = true)
    val primary = listOf(
        ChatContextMenuAction.PIN_UNPIN,
        ChatContextMenuAction.MUTE_UNMUTE,
        ChatContextMenuAction.MARK_UNREAD_READ,
        ChatContextMenuAction.DELETE,
        ChatContextMenuAction.INFO
    )
    val more = buildList {
        add(ChatContextMenuAction.ARCHIVE_UNARCHIVE)
        add(ChatContextMenuAction.FAVORITE_UNFAVORITE)
        if (!isChannel) {
            add(ChatContextMenuAction.QUICK_CONTROLS)
        }
    }
    return ChatContextMenuLayout(primaryActions = primary, moreActions = more)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FloatingChatSelectionMenuOverlay(
    targetChat: ChatEntity?,
    rowBoundsInRoot: Rect?,
    overlayOriginInRoot: Offset,
    overlaySizePx: IntSize,
    backdropState: WuppBackdropState?,
    classicMode: Boolean = LocalWuppClassicUi.current,
    onDismiss: () -> Unit,
    onTogglePin: (ChatEntity) -> Unit,
    onToggleMute: (ChatEntity) -> Unit,
    onToggleUnread: (ChatEntity) -> Unit,
    onToggleArchive: (ChatEntity) -> Unit,
    onToggleFavorite: (ChatEntity) -> Unit,
    onQuickControls: (ChatEntity) -> Unit,
    onOpenInfo: (ChatEntity) -> Unit,
    onDeleteChat: (ChatEntity) -> Unit
) {
    val colors = LocalWaColors.current
    val density = LocalDensity.current
    var lastTarget by remember { mutableStateOf<ChatEntity?>(null) }
    var showOverflow by remember(targetChat?.jid) { mutableStateOf(false) }

    LaunchedEffect(targetChat) {
        if (targetChat != null) {
            lastTarget = targetChat
        } else {
            showOverflow = false
        }
    }

    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = targetChat != null,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(motion.fastEffectsSpec()),
        exit = fadeOut(motion.fastEffectsSpec())
    ) {
        val target = targetChat ?: lastTarget
        var menuSizePx by remember(target?.jid) { mutableStateOf(IntSize.Zero) }

        // Keep background whisper-light and pass pointer events through so users can
        // directly tap or scroll to multi-select more rows without dismissing first.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (colors.isDark) 0.04f else 0.03f))
        ) {
            if (target != null) {
                val overlayW = overlaySizePx.width
                val overlayH = overlaySizePx.height
                val marginPx = with(density) { 14.dp.roundToPx() }
                val gapPx = with(density) { 8.dp.roundToPx() }
                val topSafePx = with(density) { 8.dp.roundToPx() }
                val bottomSafePx = with(density) { 14.dp.roundToPx() }

                val rTop = if (rowBoundsInRoot != null && overlayH > 0) {
                    (rowBoundsInRoot.top - overlayOriginInRoot.y).toInt()
                } else {
                    (overlayH * 0.22f).toInt()
                }
                val rBottom = if (rowBoundsInRoot != null && overlayH > 0) {
                    (rowBoundsInRoot.bottom - overlayOriginInRoot.y).toInt()
                } else {
                    (overlayH * 0.34f).toInt()
                }

                val estMenuW = if (menuSizePx.width > 0) menuSizePx.width else with(density) { 222.dp.roundToPx() }
                val estMenuH = if (menuSizePx.height > 0) menuSizePx.height else with(density) { 336.dp.roundToPx() }
                val maxMenuY = (overlayH - bottomSafePx - estMenuH).coerceAtLeast(topSafePx)
                val maxMenuX = (overlayW - marginPx - estMenuW).coerceAtLeast(marginPx)

                val roomBelow = overlayH - bottomSafePx - (rBottom + gapPx)
                val roomAbove = rTop - topSafePx - gapPx
                val menuY = when {
                    roomBelow >= estMenuH -> (rBottom + gapPx).coerceIn(topSafePx, maxMenuY)
                    roomAbove >= estMenuH -> (rTop - gapPx - estMenuH).coerceIn(topSafePx, maxMenuY)
                    else -> (rBottom + gapPx).coerceIn(topSafePx, maxMenuY)
                }
                val menuX = with(density) { 16.dp.roundToPx() }.coerceIn(marginPx, maxMenuX)

                val hasLiveGlass = !classicMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                val popupGlassTint = if (colors.isDark) Color(0xFF2B2E33) else Color(0xFFF8FAFC)
                val popupGlassAlpha = if (hasLiveGlass) {
                    if (colors.isDark) 0.42f else 0.62f
                } else {
                    if (colors.isDark) 0.82f else 0.94f
                }

                val maxMenuHeightDp = with(density) {
                    (overlayH - topSafePx - bottomSafePx)
                        .coerceAtLeast(220.dp.roundToPx())
                        .toDp()
                }
                val menuTextColor = if (colors.isDark) Color(0xFFF3F5F7) else colors.textPrimary
                val menuDeleteRed = if (colors.isDark) Color(0xFFD92626) else Color(0xFFD32F2F)
                val menuShape = RoundedCornerShape(28.dp)
                val layout = remember(target.jid, target.isPinned, target.isMuted, target.isArchived, target.isFavorite, target.unreadCount) {
                    resolveChatContextMenuLayout(target)
                }

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
                                animationSpec = motion.fastSpatialSpec(),
                                initialScale = 0.88f
                            ),
                            exit = scaleOut(
                                animationSpec = motion.fastEffectsSpec(),
                                targetScale = 0.92f
                            )
                        )
                ) {
                    WuppGlassContainer(
                        modifier = Modifier.width(222.dp),
                        shape = menuShape,
                        colors = colors,
                        elevation = 16.dp,
                        backdropState = backdropState,
                        blurRadius = 18.dp,
                        refractionHeight = 10.dp,
                        refractionAmount = 20.dp,
                        surfaceTintOverride = popupGlassTint,
                        surfaceAlphaOverride = popupGlassAlpha,
                        showHighlight = false,
                        classicMode = classicMode
                    ) {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = maxMenuHeightDp)
                                .border(
                                    width = 0.55.dp,
                                    color = if (colors.isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.45f),
                                    shape = menuShape
                                )
                                .verticalScroll(scrollState)
                                .padding(vertical = 10.dp)
                        ) {
                            layout.primaryActions.forEach { action ->
                                val isDelete = action == ChatContextMenuAction.DELETE
                                val itemTint = if (isDelete) menuDeleteRed else menuTextColor
                                val itemLabel = chatActionLabel(target, action)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onDismiss()
                                            dispatchChatContextAction(
                                                action = action,
                                                chat = target,
                                                onTogglePin = onTogglePin,
                                                onToggleMute = onToggleMute,
                                                onToggleUnread = onToggleUnread,
                                                onToggleArchive = onToggleArchive,
                                                onToggleFavorite = onToggleFavorite,
                                                onQuickControls = onQuickControls,
                                                onOpenInfo = onOpenInfo,
                                                onDeleteChat = onDeleteChat
                                            )
                                        }
                                        .padding(start = 18.dp, end = 20.dp, top = 12.5.dp, bottom = 12.5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    ChatContextActionGlyph(
                                        action = action,
                                        chat = target,
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

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showOverflow = !showOverflow }
                                    .padding(start = 18.dp, end = 20.dp, top = 12.5.dp, bottom = 12.5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                ChatContextActionGlyph(
                                    action = null,
                                    chat = target,
                                    tint = menuTextColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = if (showOverflow) "Less" else "More",
                                    color = menuTextColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }

                            AnimatedVisibility(visible = showOverflow) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = if (colors.isDark) Color.White.copy(alpha = 0.12f) else colors.divider,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                    layout.moreActions.forEach { action ->
                                        val itemTint = if (action == ChatContextMenuAction.QUICK_CONTROLS) {
                                            colors.accentGreen
                                        } else {
                                            menuTextColor
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onDismiss()
                                                    dispatchChatContextAction(
                                                        action = action,
                                                        chat = target,
                                                        onTogglePin = onTogglePin,
                                                        onToggleMute = onToggleMute,
                                                        onToggleUnread = onToggleUnread,
                                                        onToggleArchive = onToggleArchive,
                                                        onToggleFavorite = onToggleFavorite,
                                                        onQuickControls = onQuickControls,
                                                        onOpenInfo = onOpenInfo,
                                                        onDeleteChat = onDeleteChat
                                                    )
                                                }
                                                .padding(start = 18.dp, end = 20.dp, top = 11.5.dp, bottom = 11.5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            ChatContextActionGlyph(
                                                action = action,
                                                chat = target,
                                                tint = itemTint,
                                                modifier = Modifier.size(21.dp)
                                            )
                                            Text(
                                                text = chatActionLabel(target, action),
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

private fun dispatchChatContextAction(
    action: ChatContextMenuAction,
    chat: ChatEntity,
    onTogglePin: (ChatEntity) -> Unit,
    onToggleMute: (ChatEntity) -> Unit,
    onToggleUnread: (ChatEntity) -> Unit,
    onToggleArchive: (ChatEntity) -> Unit,
    onToggleFavorite: (ChatEntity) -> Unit,
    onQuickControls: (ChatEntity) -> Unit,
    onOpenInfo: (ChatEntity) -> Unit,
    onDeleteChat: (ChatEntity) -> Unit
) {
    when (action) {
        ChatContextMenuAction.PIN_UNPIN -> onTogglePin(chat)
        ChatContextMenuAction.MUTE_UNMUTE -> onToggleMute(chat)
        ChatContextMenuAction.MARK_UNREAD_READ -> onToggleUnread(chat)
        ChatContextMenuAction.ARCHIVE_UNARCHIVE -> onToggleArchive(chat)
        ChatContextMenuAction.FAVORITE_UNFAVORITE -> onToggleFavorite(chat)
        ChatContextMenuAction.QUICK_CONTROLS -> onQuickControls(chat)
        ChatContextMenuAction.INFO -> onOpenInfo(chat)
        ChatContextMenuAction.DELETE -> onDeleteChat(chat)
    }
}

@Composable
private fun chatActionLabel(chat: ChatEntity, action: ChatContextMenuAction): String = when (action) {
    ChatContextMenuAction.PIN_UNPIN -> stringResource(
        if (chat.isPinned) R.string.chat_action_unpin else R.string.chat_action_pin
    )
    ChatContextMenuAction.MUTE_UNMUTE -> stringResource(
        if (chat.isMuted) R.string.chat_action_unmute else R.string.chat_action_mute
    )
    ChatContextMenuAction.MARK_UNREAD_READ -> stringResource(
        if (chat.unreadCount > 0) R.string.chat_peek_mark_read else R.string.chat_peek_mark_unread
    )
    ChatContextMenuAction.ARCHIVE_UNARCHIVE -> stringResource(
        if (chat.isArchived) R.string.chat_action_unarchive else R.string.chat_action_archive
    )
    ChatContextMenuAction.FAVORITE_UNFAVORITE -> stringResource(
        if (chat.isFavorite) R.string.chat_peek_remove_favorite else R.string.chat_peek_add_favorite
    )
    ChatContextMenuAction.QUICK_CONTROLS -> "Quick controls"
    ChatContextMenuAction.INFO -> "Info"
    ChatContextMenuAction.DELETE -> stringResource(R.string.chat_peek_delete)
}

@Composable
private fun ChatContextActionGlyph(
    action: ChatContextMenuAction?,
    chat: ChatEntity,
    tint: Color,
    modifier: Modifier = Modifier
) {
    when (action) {
        ChatContextMenuAction.DELETE -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                val sw = (w * 0.075f).coerceAtLeast(1.45f)
                val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
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
        }
        ChatContextMenuAction.INFO -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                val sw = (w * 0.075f).coerceAtLeast(1.45f)
                val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
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
        }
        null -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height
                val sw = (w * 0.075f).coerceAtLeast(1.45f)
                val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawCircle(
                    color = tint,
                    radius = w * 0.38f,
                    center = Offset(w * 0.50f, h * 0.50f),
                    style = stroke
                )
                val dotR = sw * 0.65f
                drawCircle(color = tint, radius = dotR, center = Offset(w * 0.34f, h * 0.50f))
                drawCircle(color = tint, radius = dotR, center = Offset(w * 0.50f, h * 0.50f))
                drawCircle(color = tint, radius = dotR, center = Offset(w * 0.66f, h * 0.50f))
            }
        }
        ChatContextMenuAction.PIN_UNPIN -> Icon(
            imageVector = Icons.Rounded.PushPin,
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
        ChatContextMenuAction.MUTE_UNMUTE -> Icon(
            imageVector = if (chat.isMuted) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff,
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
        ChatContextMenuAction.MARK_UNREAD_READ -> Icon(
            imageVector = if (chat.unreadCount > 0) Icons.Rounded.MarkChatRead else Icons.Rounded.MarkChatUnread,
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
        ChatContextMenuAction.ARCHIVE_UNARCHIVE -> Icon(
            imageVector = if (chat.isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
        ChatContextMenuAction.FAVORITE_UNFAVORITE -> Icon(
            imageVector = if (chat.isFavorite) Icons.Rounded.StarOutline else Icons.Rounded.Star,
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
        ChatContextMenuAction.QUICK_CONTROLS -> Icon(
            imageVector = Icons.Rounded.Shield,
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
    }
}
