package com.wa.beastclient.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.wa.beastclient.ui.theme.contrastingContentColor
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.wa.beastclient.ui.theme.LocalWaColors
import com.wa.beastclient.ui.theme.LocalWuppClassicUi
import com.wa.beastclient.ui.theme.WaColors
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

internal enum class ChromeSurfaceStyle {
    LIQUID_GLASS,
    CLASSIC_FLAT
}

internal fun resolveChromeSurfaceStyle(classicMode: Boolean): ChromeSurfaceStyle =
    if (classicMode) ChromeSurfaceStyle.CLASSIC_FLAT else ChromeSurfaceStyle.LIQUID_GLASS

internal fun resolveLiquidGlassSurfaceAlpha(
    isDark: Boolean,
    renderEffectSupported: Boolean
): Float = if (renderEffectSupported) {
    if (isDark) 0.44f else 0.56f
} else {
    if (isDark) 0.88f else 0.94f
}

/**
 * Shared live backdrop state powered by Kyant0/AndroidLiquidGlass ([LayerBackdrop]) so floating
 * glass surfaces capture, blur, and refract scrolled content directly beneath them.
 */
@Stable
class WuppBackdropState internal constructor(
    val backdrop: LayerBackdrop
)

@Composable
fun rememberWuppBackdropState(): WuppBackdropState {
    val layerBackdrop = rememberLayerBackdrop()
    return remember(layerBackdrop) { WuppBackdropState(layerBackdrop) }
}

fun Modifier.wuppBackdropSource(state: WuppBackdropState): Modifier =
    this.layerBackdrop(state.backdrop)

/**
 * Apple-inspired Liquid Glass container backed by Kyant0/AndroidLiquidGlass (`drawBackdrop`)
 * with vibrancy, RenderEffect blur, AGSL edge refraction lens + specular highlight on Android 12+/13+,
 * clean high-contrast frosted fallback on Android 8–11, and flat Classic mode when enabled.
 */
@Composable
fun WuppGlassContainer(
    modifier: Modifier = Modifier,
    colors: WaColors = LocalWaColors.current,
    shape: Shape = CircleShape,
    elevation: Dp = 16.dp,
    backdropState: WuppBackdropState? = null,
    blurRadius: Dp = 9.dp,
    refractionHeight: Dp = 12.dp,
    refractionAmount: Dp = 22.dp,
    chromaticAberration: Boolean = false,
    classicMode: Boolean = LocalWuppClassicUi.current,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    if (resolveChromeSurfaceStyle(classicMode) == ChromeSurfaceStyle.CLASSIC_FLAT) {
        Box(
            modifier = modifier
                .shadow(
                    elevation = 1.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.08f)
                )
                .clip(shape)
                .background(colors.elevatedBar)
                .border(
                    width = 0.75.dp,
                    color = colors.divider,
                    shape = shape
                ),
            contentAlignment = contentAlignment
        ) {
            content()
        }
        return
    }

    val backdrop: Backdrop = backdropState?.backdrop ?: emptyBackdrop()
    val hasLiveBackdrop = backdropState != null && isRenderEffectSupported()
    val surfaceAlpha = resolveLiquidGlassSurfaceAlpha(
        isDark = colors.isDark,
        renderEffectSupported = hasLiveBackdrop
    )
    val surfaceTint = if (colors.isDark) Color(0xFF121519) else Color(0xFFF7F9FC)

    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    if (hasLiveBackdrop) {
                        vibrancy()
                        blur(blurRadius.toPx())
                        lens(
                            refractionHeight = refractionHeight.toPx(),
                            refractionAmount = refractionAmount.toPx(),
                            depthEffect = true,
                            chromaticAberration = chromaticAberration
                        )
                    }
                },
                highlight = {
                    Highlight(
                        width = 0.65.dp,
                        alpha = if (colors.isDark) 0.52f else 0.94f,
                        style = HighlightStyle.Default
                    )
                },
                shadow = {
                    Shadow(
                        radius = elevation,
                        color = Color.Black.copy(alpha = if (colors.isDark) 0.28f else 0.14f)
                    )
                },
                innerShadow = {
                    InnerShadow(
                        radius = 8.dp,
                        alpha = if (colors.isDark) 0.18f else 0.08f
                    )
                },
                onDrawSurface = {
                    drawRect(surfaceTint.copy(alpha = surfaceAlpha))
                }
            )
            .border(
                width = 0.6.dp,
                color = if (colors.isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.42f),
                shape = shape
            ),
        contentAlignment = contentAlignment
    ) {
        content()
    }
}

/**
 * Circular Liquid Glass icon button with spring press feedback and subtle specular rim,
 * used in floating headers, chat actions, scroll-to-bottom orb, and companion nav buttons.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WuppGlassCircleButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    imageVector: ImageVector? = null,
    drawableRes: Int? = null,
    size: Dp = 38.dp,
    iconSize: Dp = 20.dp,
    tint: Color? = null,
    accentFill: Boolean = false,
    backdropState: WuppBackdropState? = null,
    classicMode: Boolean = LocalWuppClassicUi.current,
    enabled: Boolean = true
) {
    val colors = LocalWaColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val resolvedTint = tint ?: if (accentFill) {
        colors.brightGreen.contrastingContentColor()
    } else {
        colors.textPrimary
    }

    if (accentFill) {
        Box(
            modifier = modifier
                .size(size)
                .expressivePressScale(interactionSource, pressedScale = 0.90f)
                .shadow(
                    elevation = if (classicMode) 3.dp else 10.dp,
                    shape = CircleShape,
                    ambientColor = colors.brightGreen.copy(alpha = 0.32f),
                    spotColor = colors.brightGreen.copy(alpha = 0.36f)
                )
                .clip(CircleShape)
                .background(colors.brightGreen)
                .border(
                    width = 0.65.dp,
                    color = Color.White.copy(alpha = 0.28f),
                    shape = CircleShape
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            if (drawableRes != null) {
                Icon(
                    painter = painterResource(drawableRes),
                    contentDescription = contentDescription,
                    tint = resolvedTint,
                    modifier = Modifier.size(iconSize)
                )
            } else if (imageVector != null) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = contentDescription,
                    tint = resolvedTint,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        return
    }

    WuppGlassContainer(
        modifier = modifier
            .size(size)
            .expressivePressScale(interactionSource, pressedScale = 0.90f)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        colors = colors,
        shape = CircleShape,
        elevation = if (size >= 48.dp) 10.dp else 4.dp,
        backdropState = backdropState,
        blurRadius = if (size >= 48.dp) 9.dp else 7.dp,
        refractionHeight = if (size >= 48.dp) 12.dp else 8.dp,
        refractionAmount = if (size >= 48.dp) 22.dp else 14.dp,
        classicMode = classicMode,
        contentAlignment = Alignment.Center
    ) {
        if (drawableRes != null) {
            Icon(
                painter = painterResource(drawableRes),
                contentDescription = contentDescription,
                tint = resolvedTint,
                modifier = Modifier.size(iconSize)
            )
        } else if (imageVector != null) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = resolvedTint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * Floating translucent Liquid Glass top bar with live backdrop blur and a razor-thin bottom hairline.
 * Falls back cleanly to a flat elevated bar in Classic mode.
 */
@Composable
fun WuppGlassTopBar(
    modifier: Modifier = Modifier,
    backdropState: WuppBackdropState? = null,
    classicMode: Boolean = LocalWuppClassicUi.current,
    showBottomDivider: Boolean = true,
    height: Dp = 54.dp,
    leading: @Composable RowScope.() -> Unit = {},
    center: @Composable BoxScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val colors = LocalWaColors.current
    val hasLiveBackdrop = !classicMode && backdropState != null && isRenderEffectSupported()
    val backdrop: Backdrop = backdropState?.backdrop ?: emptyBackdrop()
    val barTint = if (colors.isDark) Color(0xFF06080A) else Color(0xFFF6F8FA)
    val barAlpha = when {
        classicMode -> 1f
        hasLiveBackdrop -> if (colors.isDark) 0.78f else 0.76f
        else -> if (colors.isDark) 0.94f else 0.96f
    }

    val barModifier = if (classicMode) {
        modifier
            .fillMaxWidth()
            .background(colors.appSurface)
    } else {
        modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {
                    if (hasLiveBackdrop) {
                        vibrancy()
                        blur(12.dp.toPx())
                    }
                },
                onDrawSurface = {
                    drawRect(barTint.copy(alpha = barAlpha))
                }
            )
    }

    Column(modifier = barModifier.statusBarsPadding()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                content = leading
            )
            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center,
                content = center
            )
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = trailing
            )
        }
        if (showBottomDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(
                        if (colors.isDark) Color.White.copy(alpha = 0.08f)
                        else colors.divider
                    )
            )
        }
    }
}

private data class GlassNavTab(
    val index: Int,
    val label: String,
    val drawableRes: Int? = null,
    val imageVector: ImageVector? = null,
    val badgeCount: Int = 0
)

internal fun computeLiquidNavSpan(left: Float, right: Float): Float {
    return (right - left).coerceIn(0.85f, 2.5f)
}

internal fun computeLiquidNavSquash(span: Float): Float {
    val stretchFraction = (span - 1f).coerceIn(0f, 0.7f)
    return 1f - (stretchFraction * 0.16f)
}

internal fun computeLiquidNavBounds(tabWidth: Dp, left: Float, right: Float, maxWidth: Dp): Pair<Dp, Dp> {
    val indicatorOffset = (tabWidth * left).coerceAtLeast(0.dp)
    val span = computeLiquidNavSpan(left, right)
    val indicatorWidth = (tabWidth * span).coerceAtMost(maxWidth - indicatorOffset)
    return Pair(indicatorOffset, indicatorWidth)
}

/**
 * Floating Liquid Glass bottom navigation pill inspired by Kyant0's LiquidBottomTabs:
 * - Outer refractive Capsule bar over live scrolled content
 * - Optical sliding active capsule with spring dynamics, velocity squeeze, and interactive scrub drag
 * - Automatic flat Classic mode when requested
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WuppGlassNavBar(
    currentTab: Int,
    unreadChatsCount: Int,
    onSelectTab: (Int) -> Unit,
    backdropState: WuppBackdropState? = null,
    classicMode: Boolean = false,
    separateGroupsAndChats: Boolean = false,
    unreadGroupsCount: Int = 0,
    separateChannelsTab: Boolean = false,
    chatsLabel: String = "Chats",
    groupsLabel: String = "Groups",
    updatesLabel: String = "Updates",
    channelsLabel: String = "Channels",
    communitiesLabel: String = "Community",
    callsLabel: String = "Calls",
    youLabel: String = "You",
    trailingActionIcon: ImageVector? = null,
    trailingActionDrawableRes: Int? = null,
    trailingActionDescription: String = "",
    trailingActionAccentFill: Boolean = false,
    onTrailingActionClick: (() -> Unit)? = null,
    showLabels: Boolean = true,
    compactIcons: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaColors.current
    val tabs = remember(
        separateGroupsAndChats,
        separateChannelsTab,
        unreadChatsCount,
        unreadGroupsCount,
        chatsLabel,
        groupsLabel,
        updatesLabel,
        channelsLabel,
        communitiesLabel,
        callsLabel,
        youLabel
    ) {
        buildList {
            add(GlassNavTab(0, chatsLabel, imageVector = Icons.Rounded.ChatBubble, badgeCount = unreadChatsCount))
            if (separateGroupsAndChats) {
                add(GlassNavTab(4, groupsLabel, imageVector = Icons.Rounded.Groups, badgeCount = unreadGroupsCount))
            }
            add(GlassNavTab(1, updatesLabel, imageVector = Icons.Rounded.DataUsage))
            if (separateChannelsTab) {
                add(GlassNavTab(6, channelsLabel, imageVector = Icons.Rounded.Campaign))
            }
            add(GlassNavTab(2, communitiesLabel, imageVector = Icons.Rounded.Hub))
            add(GlassNavTab(5, callsLabel, imageVector = Icons.Rounded.Call))
            add(GlassNavTab(3, youLabel, imageVector = Icons.Rounded.AccountCircle))
        }
    }
    val fiveTabs = tabs.size >= 5
    val sixTabs = tabs.size >= 6
    val sevenTabs = tabs.size >= 7
    val labelFontSize = when {
        sevenTabs -> 9.sp
        sixTabs -> 9.5.sp
        fiveTabs -> 10.sp
        else -> 11.sp
    }
    val classicLabelFontSize = when {
        sevenTabs -> 9.sp
        sixTabs -> 9.5.sp
        fiveTabs -> 10.sp
        else -> 11.sp
    }

    if (classicMode) {
        androidx.compose.material3.Surface(
            color = colors.elevatedBar,
            tonalElevation = 0.dp,
            modifier = modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                androidx.compose.material3.HorizontalDivider(color = colors.divider, thickness = 0.6.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (compactIcons) 54.dp else 64.dp)
                        .padding(horizontal = if (sevenTabs) 0.dp else if (sixTabs) 1.dp else if (fiveTabs) 2.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        val selected = tab.index == currentTab
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .semantics { contentDescription = tab.label }
                                .clickable { onSelectTab(tab.index) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) colors.pillActiveBg else Color.Transparent)
                                    .padding(horizontal = if (sixTabs) 10.dp else if (fiveTabs) 12.dp else 16.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (tab.drawableRes != null) {
                                    Icon(
                                        painter = painterResource(tab.drawableRes),
                                        contentDescription = null,
                                        tint = if (selected) colors.pillActiveText else colors.textSecondary,
                                        modifier = Modifier.size(21.dp)
                                    )
                                } else if (tab.imageVector != null) {
                                    Icon(
                                        imageVector = tab.imageVector,
                                        contentDescription = null,
                                        tint = if (selected) colors.pillActiveText else colors.textSecondary,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                                if (tab.badgeCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 8.dp, y = (-3).dp)
                                            .clip(CircleShape)
                                            .background(colors.brightGreen)
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = if (tab.badgeCount > 99) "99+" else tab.badgeCount.toString(),
                                            color = colors.brightGreen.contrastingContentColor(),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            if (showLabels) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = tab.label,
                                    color = if (selected) colors.textPrimary else colors.textSecondary,
                                    fontSize = classicLabelFontSize,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }

    val outerBackdrop: Backdrop = backdropState?.backdrop ?: emptyBackdrop()
    val tabsBackdrop = rememberLayerBackdrop()
    val hasLiveBackdrop = backdropState != null && isRenderEffectSupported()
    val barSurfaceTint = if (colors.isDark) Color(0xFF141E24) else Color(0xFFF6F9F7)
    val barSurfaceAlpha = resolveLiquidGlassSurfaceAlpha(
        isDark = colors.isDark,
        renderEffectSupported = hasLiveBackdrop
    )

    val activePosition = tabs.indexOfFirst { it.index == currentTab }.coerceAtLeast(0)
    val scope = rememberCoroutineScope()
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val density = LocalDensity.current

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(activePosition.toFloat()) }
    val leftEdge = remember { Animatable(activePosition.toFloat()) }
    val rightEdge = remember { Animatable((activePosition + 1).toFloat()) }

    val pressProgress by animateFloatAsState(
        targetValue = if (isScrubbing) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 500f),
        label = "liquid_nav_press_progress"
    )
    val lensScale by animateFloatAsState(
        targetValue = if (isScrubbing) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "liquid_nav_lens_scale"
    )

    // Liquid stretch-and-settle animation (#67):
    // When moving forward, the leading right edge springs ahead quickly while the trailing left edge follows with a softer spring.
    // When moving backward, the leading left edge springs ahead first while the trailing right edge follows.
    // This creates an organic jelly stretch in transit that cleanly contracts and settles back at the destination.
    LaunchedEffect(activePosition, isScrubbing) {
        if (!isScrubbing) {
            scrubFraction = activePosition.toFloat()
            val targetLeft = activePosition.toFloat()
            val targetRight = (activePosition + 1).toFloat()
            val currentLeft = leftEdge.value

            if (targetLeft > currentLeft + 0.001f) {
                // Moving forward: right edge leads with brisk spring, left edge follows with softer spring
                launch {
                    rightEdge.animateTo(
                        targetValue = targetRight,
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 440f)
                    )
                }
                launch {
                    leftEdge.animateTo(
                        targetValue = targetLeft,
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 300f)
                    )
                }
            } else if (targetLeft < currentLeft - 0.001f) {
                // Moving backward: left edge leads with brisk spring, right edge follows with softer spring
                launch {
                    leftEdge.animateTo(
                        targetValue = targetLeft,
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 440f)
                    )
                }
                launch {
                    rightEdge.animateTo(
                        targetValue = targetRight,
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 300f)
                    )
                }
            } else {
                // Settle / rest at exact target
                launch {
                    leftEdge.animateTo(
                        targetValue = targetLeft,
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f)
                    )
                }
                launch {
                    rightEdge.animateTo(
                        targetValue = targetRight,
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f)
                    )
                }
            }
        }
    }

    val panelOffsetPx by remember(density, isScrubbing) {
        derivedStateOf {
            if (!isScrubbing) 0f
            else {
                val maxIdx = (tabs.size - 1).coerceAtLeast(1).toFloat()
                val delta = ((leftEdge.value - activePosition.toFloat()) / maxIdx).coerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() * delta.sign * EaseOut.transform(abs(delta)) }
            }
        }
    }

    val hasTrailingOrb = !classicMode && onTrailingActionClick != null && (trailingActionIcon != null || trailingActionDrawableRes != null)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.65f to Color.Transparent,
                    1.0f to colors.appSurface.copy(alpha = if (colors.isDark) 0.24f else 0.32f)
                )
            )
            .navigationBarsPadding()
            .padding(
                horizontal = when {
                    sevenTabs -> 4.dp
                    sixTabs -> 6.dp
                    fiveTabs -> 10.dp
                    else -> 12.dp
                },
                vertical = 10.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(if (compactIcons) 52.dp else 62.dp)
                .graphicsLayer { translationX = panelOffsetPx }
                .drawBackdrop(
                    backdrop = outerBackdrop,
                    shape = { Capsule() },
                    effects = {
                        if (hasLiveBackdrop) {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(
                                refractionHeight = 16.dp.toPx(),
                                refractionAmount = 28.dp.toPx(),
                                depthEffect = true
                            )
                        }
                    },
                    highlight = {
                        Highlight(
                            width = 0.65.dp,
                            alpha = if (colors.isDark) 0.72f else 0.94f,
                            style = HighlightStyle.Default
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 20.dp,
                            color = Color.Black.copy(alpha = if (colors.isDark) 0.42f else 0.16f)
                        )
                    },
                    onDrawSurface = {
                        drawRect(barSurfaceTint.copy(alpha = barSurfaceAlpha))
                    }
                )
                .pointerInput(tabs.size, isLtr) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isScrubbing = true
                            scrubFraction = leftEdge.value
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val stepWidth = (size.width.toFloat() - 8.dp.toPx()) / tabs.size.toFloat()
                            if (stepWidth > 0f) {
                                val signedDelta = if (isLtr) dragAmount else -dragAmount
                                scrubFraction = (scrubFraction + signedDelta / stepWidth)
                                    .coerceIn(0f, (tabs.size - 1).toFloat())
                                scope.launch {
                                    leftEdge.snapTo(scrubFraction)
                                    rightEdge.snapTo(scrubFraction + 1f)
                                }
                            }
                        },
                        onDragEnd = {
                            val snapped = scrubFraction.roundToInt().coerceIn(0, tabs.lastIndex)
                            isScrubbing = false
                            tabs.getOrNull(snapped)?.let { onSelectTab(it.index) }
                        },
                        onDragCancel = {
                            isScrubbing = false
                        }
                    )
                }
                .padding(4.dp)
        ) {
            val tabWidth = maxWidth / tabs.size
            val currentLeft = leftEdge.value
            val currentRight = rightEdge.value
            val (indicatorOffset, indicatorWidth) = computeLiquidNavBounds(
                tabWidth = tabWidth,
                left = currentLeft,
                right = currentRight,
                maxWidth = maxWidth
            )
            val span = computeLiquidNavSpan(currentLeft, currentRight)
            val verticalSquash = computeLiquidNavSquash(span)

            val lensBackdrop = rememberCombinedBackdrop(outerBackdrop, tabsBackdrop)
            val pillSurfaceTint = if (colors.isDark) {
                Color.White.copy(alpha = (0.14f - 0.08f * pressProgress).coerceAtLeast(0.05f))
            } else {
                colors.accentGreen.copy(alpha = (0.12f - 0.05f * pressProgress).coerceAtLeast(0.05f))
            }

            // Tab labels/icons row recorded into tabsBackdrop for optical magnification by active pill
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(tabsBackdrop),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val selected = tab.index == currentTab
                    val tintColor by animateColorAsState(
                        targetValue = when {
                            selected && colors.isDark -> Color.White
                            selected -> colors.accentGreen
                            colors.isDark -> Color.White.copy(alpha = 0.86f)
                            else -> Color(0xFF1F2C34).copy(alpha = 0.78f)
                        },
                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        label = "glass_nav_tint_${tab.index}"
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.09f else 1f,
                        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                        label = "glass_nav_scale_${tab.index}"
                    )
                    val interactionSource = remember { MutableInteractionSource() }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .semantics { contentDescription = tab.label }
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onSelectTab(tab.index) }
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier.size(width = 40.dp, height = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (tab.drawableRes != null) {
                                Icon(
                                    painter = painterResource(tab.drawableRes),
                                    contentDescription = null,
                                    tint = tintColor,
                                    modifier = Modifier
                                        .size(21.dp)
                                        .graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                                )
                            } else if (tab.imageVector != null) {
                                Icon(
                                    imageVector = tab.imageVector,
                                    contentDescription = null,
                                    tint = tintColor,
                                    modifier = Modifier
                                        .size(21.dp)
                                        .graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                                )
                            }

                            if (tab.badgeCount > 0) {
                                val badgeRing = if (colors.isDark) Color(0xFF1A2226) else Color.White
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 8.dp, y = (-3).dp)
                                        .defaultMinSize(minWidth = 17.dp, minHeight = 17.dp)
                                        .clip(CircleShape)
                                        .background(colors.brightGreen)
                                        .border(1.5.dp, badgeRing, CircleShape)
                                        .padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (tab.badgeCount > 99) "99+" else tab.badgeCount.toString(),
                                        color = colors.brightGreen.contrastingContentColor(),
                                        fontSize = 9.5.sp,
                                        lineHeight = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }

                        if (showLabels) {
                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = tab.label,
                                color = tintColor,
                                fontSize = labelFontSize,
                                lineHeight = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Floating refractive Liquid Glass active tab pill with lens + chromatic aberration on scrub
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(indicatorWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleX = lensScale
                        scaleY = lensScale * verticalSquash
                    }
                    .drawBackdrop(
                        backdrop = lensBackdrop,
                        shape = { Capsule() },
                        effects = {
                            if (hasLiveBackdrop) {
                                lens(
                                    refractionHeight = (6.dp + 4.dp * pressProgress).toPx(),
                                    refractionAmount = (10.dp + 6.dp * pressProgress).toPx(),
                                    depthEffect = true,
                                    chromaticAberration = isScrubbing
                                )
                            }
                        },
                        highlight = {
                            Highlight(
                                width = 0.6.dp,
                                alpha = (0.35f + 0.55f * pressProgress).coerceIn(0f, 1f),
                                style = HighlightStyle.Default
                            )
                        },
                        innerShadow = {
                            InnerShadow(
                                radius = (4.dp + 4.dp * pressProgress),
                                alpha = if (colors.isDark) 0.14f + 0.16f * pressProgress else 0.06f + 0.10f * pressProgress
                            )
                        },
                        onDrawSurface = {
                            drawRect(pillSurfaceTint)
                        }
                    )
            )
        }
        if (hasTrailingOrb) {
            WuppGlassCircleButton(
                onClick = { onTrailingActionClick?.invoke() },
                contentDescription = trailingActionDescription.ifBlank { "Quick action" },
                imageVector = trailingActionIcon,
                drawableRes = trailingActionDrawableRes,
                size = 56.dp,
                iconSize = 23.dp,
                accentFill = trailingActionAccentFill,
                backdropState = backdropState,
                classicMode = false
            )
        }
        }
    }
}
