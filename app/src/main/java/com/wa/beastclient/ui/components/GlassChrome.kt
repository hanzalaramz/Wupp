package com.wa.beastclient.ui.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.R
import com.wa.beastclient.ui.theme.LocalWaColors
import com.wa.beastclient.ui.theme.LocalWuppClassicUi
import com.wa.beastclient.ui.theme.WaColors

internal enum class ChromeSurfaceStyle {
    LIQUID_GLASS,
    CLASSIC_FLAT
}

internal fun resolveChromeSurfaceStyle(classicMode: Boolean): ChromeSurfaceStyle =
    if (classicMode) ChromeSurfaceStyle.CLASSIC_FLAT else ChromeSurfaceStyle.LIQUID_GLASS

/**
 * Shared live backdrop state captured via a GPU GraphicsLayer so floating glass surfaces
 * can blur and refract the content scrolling directly beneath them (API 31+ RenderEffect).
 */
@Stable
class WuppBackdropState internal constructor(
    val layer: GraphicsLayer
) {
    var sourceOriginInWindow by mutableStateOf(Offset.Zero)
        internal set

    @Volatile
    internal var hasRecorded: Boolean = false

    private val targets = mutableListOf<WuppBackdropTargetNode>()

    internal fun register(node: WuppBackdropTargetNode) {
        if (node !in targets) targets.add(node)
    }

    internal fun unregister(node: WuppBackdropTargetNode) {
        targets.remove(node)
    }

    internal fun notifyTargets() {
        for (i in 0 until targets.size) {
            targets[i].invalidateDraw()
        }
    }
}

@Composable
fun rememberWuppBackdropState(): WuppBackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { WuppBackdropState(layer) }
}

fun Modifier.wuppBackdropSource(state: WuppBackdropState): Modifier =
    this
        .onGloballyPositioned { coords ->
            state.sourceOriginInWindow = coords.positionInWindow()
        }
        .drawWithContent {
            state.layer.record {
                this@drawWithContent.drawContent()
            }
            state.hasRecorded = true
            drawLayer(state.layer)
            state.notifyTargets()
        }

internal class WuppBackdropTargetNode(
    var state: WuppBackdropState,
    var onDraw: DrawScope.() -> Unit
) : Modifier.Node(), DrawModifierNode {
    override fun onAttach() {
        state.register(this)
    }

    override fun onDetach() {
        state.unregister(this)
    }

    override fun ContentDrawScope.draw() {
        onDraw()
        drawContent()
    }
}

private data class WuppBackdropTargetElement(
    val state: WuppBackdropState,
    val onDraw: DrawScope.() -> Unit
) : ModifierNodeElement<WuppBackdropTargetNode>() {
    override fun create(): WuppBackdropTargetNode = WuppBackdropTargetNode(state, onDraw)

    override fun update(node: WuppBackdropTargetNode) {
        if (node.state != state) {
            node.state.unregister(node)
            node.state = state
            state.register(node)
        }
        node.onDraw = onDraw
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "wuppBackdropTarget"
    }
}

private fun Modifier.wuppBackdropTarget(
    state: WuppBackdropState,
    onDraw: DrawScope.() -> Unit
): Modifier = this then WuppBackdropTargetElement(state, onDraw)

private fun glassRimBrush(colors: WaColors): Brush = if (colors.isDark) {
    Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = 0.54f),
        0.28f to Color.White.copy(alpha = 0.20f),
        0.72f to Color.White.copy(alpha = 0.11f),
        1.0f to Color.White.copy(alpha = 0.30f)
    )
} else {
    Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = 0.96f),
        0.55f to colors.textSecondary.copy(alpha = 0.14f),
        1.0f to Color.White.copy(alpha = 0.72f)
    )
}

/**
 * Applies a frosted-glass surface with specular lens sheen and hairline rim for inline cards/bars
 * where a full GraphicsLayer backdrop capture isn't attached.
 */
fun Modifier.wuppGlassSurface(
    colors: WaColors,
    shape: Shape = CircleShape,
    elevation: Dp = 14.dp,
    alpha: Float = if (colors.isDark) 0.76f else 0.88f,
    classicMode: Boolean = false
): Modifier {
    if (resolveChromeSurfaceStyle(classicMode) == ChromeSurfaceStyle.CLASSIC_FLAT) {
        return this
            .clip(shape)
            .background(colors.elevatedBar)
            .border(
                width = 0.75.dp,
                color = colors.divider,
                shape = shape
            )
    }
    val topColor = if (colors.isDark) {
        colors.elevatedBar.copy(alpha = alpha)
    } else {
        Color.White.copy(alpha = alpha)
    }
    val bottomColor = if (colors.isDark) {
        colors.appSurface.copy(alpha = (alpha + 0.08f).coerceAtMost(0.95f))
    } else {
        colors.elevatedBar.copy(alpha = (alpha + 0.06f).coerceAtMost(0.96f))
    }

    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = if (colors.isDark) 0.50f else 0.16f),
            spotColor = Color.Black.copy(alpha = if (colors.isDark) 0.60f else 0.20f)
        )
        .clip(shape)
        .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
        .background(
            Brush.verticalGradient(
                0.0f to Color.White.copy(alpha = if (colors.isDark) 0.13f else 0.36f),
                0.42f to Color.Transparent,
                0.80f to Color.Transparent,
                1.0f to Color.White.copy(alpha = if (colors.isDark) 0.04f else 0.10f)
            )
        )
        .border(
            width = 0.85.dp,
            brush = glassRimBrush(colors),
            shape = shape
        )
}

/**
 * Real-time GPU-blurred Liquid Glass container inspired by YouTube's frosted floating pill bar.
 * Blurs live content recorded into [backdropState] on Android 12+ and blends it with optical
 * tinting, specular lens sheen, and a curved glass rim.
 */
@Composable
fun WuppGlassContainer(
    modifier: Modifier = Modifier,
    colors: WaColors = LocalWaColors.current,
    shape: Shape = CircleShape,
    elevation: Dp = 18.dp,
    backdropState: WuppBackdropState? = null,
    blurRadius: Dp = 30.dp,
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
    val hasHardwareBlur = backdropState != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val tintTop = when {
        hasHardwareBlur && colors.isDark -> Color(0xFF1A2226).copy(alpha = 0.36f)
        hasHardwareBlur -> Color.White.copy(alpha = 0.54f)
        colors.isDark -> colors.elevatedBar.copy(alpha = 0.82f)
        else -> Color.White.copy(alpha = 0.90f)
    }
    val tintBottom = when {
        hasHardwareBlur && colors.isDark -> Color(0xFF0E1418).copy(alpha = 0.44f)
        hasHardwareBlur -> Color(0xFFEFF4F1).copy(alpha = 0.62f)
        colors.isDark -> colors.appSurface.copy(alpha = 0.88f)
        else -> colors.elevatedBar.copy(alpha = 0.94f)
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = if (colors.isDark) 0.50f else 0.16f),
                spotColor = Color.Black.copy(alpha = if (colors.isDark) 0.60f else 0.22f)
            )
            .clip(shape),
        contentAlignment = contentAlignment
    ) {
        if (hasHardwareBlur) {
            var targetOrigin by remember { mutableStateOf(Offset.Zero) }
            val bleedPx = with(LocalDensity.current) { 36.dp.roundToPx() }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .layout { measurable, constraints ->
                        val w = if (constraints.hasBoundedWidth) {
                            constraints.maxWidth + bleedPx * 2
                        } else {
                            constraints.minWidth
                        }
                        val h = if (constraints.hasBoundedHeight) {
                            constraints.maxHeight + bleedPx * 2
                        } else {
                            constraints.minHeight
                        }
                        val placeable = measurable.measure(
                            Constraints.fixed(w.coerceAtLeast(1), h.coerceAtLeast(1))
                        )
                        layout(constraints.maxWidth.coerceAtLeast(0), constraints.maxHeight.coerceAtLeast(0)) {
                            placeable.place(-bleedPx, -bleedPx)
                        }
                    }
                    .onGloballyPositioned { coords ->
                        targetOrigin = coords.positionInWindow()
                    }
                    .blur(radius = blurRadius, edgeTreatment = BlurredEdgeTreatment.Rectangle)
                    .wuppBackdropTarget(backdropState) {
                        if (backdropState.hasRecorded &&
                            backdropState.layer.size.width > 0 &&
                            backdropState.layer.size.height > 0
                        ) {
                            val dx = backdropState.sourceOriginInWindow.x - targetOrigin.x
                            val dy = backdropState.sourceOriginInWindow.y - targetOrigin.y
                            withTransform({
                                translate(left = dx, top = dy)
                            }) {
                                drawLayer(backdropState.layer)
                            }
                        }
                    }
            )
        }

        // Translucent frosted tint + curved optical specular sheen + hairline glass border
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(tintTop, tintBottom)))
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.White.copy(alpha = if (colors.isDark) 0.15f else 0.40f),
                        0.38f to Color.White.copy(alpha = if (colors.isDark) 0.02f else 0.06f),
                        0.72f to Color.Transparent,
                        1.0f to Color.White.copy(alpha = if (colors.isDark) 0.06f else 0.14f)
                    )
                )
                .border(
                    width = 0.85.dp,
                    brush = glassRimBrush(colors),
                    shape = shape
                )
        )

        content()
    }
}

private data class GlassNavTab(
    val index: Int,
    val label: String,
    val drawableRes: Int? = null,
    val imageVector: ImageVector? = null,
    val badgeCount: Int = 0
)

/**
 * Floating translucent Liquid-Glass bottom navigation bar inspired by YouTube's frosted pill bar,
 * blending live blurred content behind the glass with a borderless sliding frosted active capsule
 * and single-line non-wrapping labels.
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
    chatsLabel: String = "Chats",
    groupsLabel: String = "Groups",
    updatesLabel: String = "Updates",
    communitiesLabel: String = "Communities",
    settingsLabel: String = "Settings",
    modifier: Modifier = Modifier
) {
    val colors = LocalWaColors.current
    val tabs = remember(
        separateGroupsAndChats,
        unreadChatsCount,
        unreadGroupsCount,
        chatsLabel,
        groupsLabel,
        updatesLabel,
        communitiesLabel,
        settingsLabel
    ) {
        buildList {
            add(GlassNavTab(0, chatsLabel, drawableRes = R.drawable.vec_ic_chat_filled, badgeCount = unreadChatsCount))
            if (separateGroupsAndChats) {
                add(GlassNavTab(4, groupsLabel, imageVector = Icons.Rounded.Groups, badgeCount = unreadGroupsCount))
            }
            add(GlassNavTab(1, updatesLabel, drawableRes = R.drawable.vec_ic_status))
            add(GlassNavTab(2, communitiesLabel, drawableRes = R.drawable.vec_ic_avatar_community))
            add(GlassNavTab(3, settingsLabel, imageVector = Icons.Rounded.Shield))
        }
    }
    val fiveTabs = tabs.size >= 5
    val labelFontSize = if (fiveTabs) 9.5.sp else 10.5.sp
    val classicLabelFontSize = if (fiveTabs) 9.5.sp else 11.sp

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
                        .height(64.dp)
                        .padding(horizontal = if (fiveTabs) 2.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        val selected = tab.index == currentTab
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onSelectTab(tab.index) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) colors.pillActiveBg else Color.Transparent)
                                    .padding(horizontal = if (fiveTabs) 12.dp else 16.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (tab.drawableRes != null) {
                                    androidx.compose.material3.Icon(
                                        painter = androidx.compose.ui.res.painterResource(tab.drawableRes),
                                        contentDescription = tab.label,
                                        tint = if (selected) colors.pillActiveText else colors.textSecondary,
                                        modifier = Modifier.size(21.dp)
                                    )
                                } else if (tab.imageVector != null) {
                                    androidx.compose.material3.Icon(
                                        imageVector = tab.imageVector,
                                        contentDescription = tab.label,
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
                                            color = if (colors.isDark) Color(0xFF0B141A) else Color.White,
                                            fontSize = 9.5.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = tab.label,
                                color = if (selected) colors.textPrimary else colors.textSecondary,
                                fontSize = classicLabelFontSize,
                                fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.65f to Color.Transparent,
                    1.0f to colors.appSurface.copy(alpha = if (colors.isDark) 0.28f else 0.36f)
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = if (fiveTabs) 10.dp else 14.dp, vertical = 10.dp)
    ) {
        WuppGlassContainer(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = colors,
            shape = CircleShape,
            elevation = 18.dp,
            backdropState = backdropState,
            blurRadius = 30.dp
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                val tabWidth = maxWidth / tabs.size
                val activePosition = tabs.indexOfFirst { it.index == currentTab }.coerceAtLeast(0)
                val indicatorOffset by animateDpAsState(
                    targetValue = tabWidth * activePosition,
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    label = "glass_nav_indicator_offset"
                )

                // Soft borderless frosted active lens (YouTube-style pill)
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            if (colors.isDark) {
                                Brush.verticalGradient(
                                    0.0f to Color.White.copy(alpha = 0.24f),
                                    0.55f to Color.White.copy(alpha = 0.16f),
                                    1.0f to Color.White.copy(alpha = 0.11f)
                                )
                            } else {
                                Brush.verticalGradient(
                                    0.0f to Color.White.copy(alpha = 0.88f),
                                    1.0f to colors.accentGreen.copy(alpha = 0.14f)
                                )
                            }
                        )
                        .border(
                            width = 0.5.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = if (colors.isDark) 0.26f else 0.90f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
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
                                        contentDescription = tab.label,
                                        tint = tintColor,
                                        modifier = Modifier
                                            .size(21.dp)
                                            .graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                                    )
                                } else if (tab.imageVector != null) {
                                    Icon(
                                        imageVector = tab.imageVector,
                                        contentDescription = tab.label,
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
                                            .offset(x = 4.dp, y = (-3).dp)
                                            .defaultMinSize(minWidth = 17.dp, minHeight = 16.dp)
                                            .clip(CircleShape)
                                            .background(if (colors.isDark) colors.brightGreen else colors.accentGreen)
                                            .border(1.5.dp, badgeRing, CircleShape)
                                            .padding(horizontal = 4.dp, vertical = 0.5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (tab.badgeCount > 99) "99+" else tab.badgeCount.toString(),
                                            color = if (colors.isDark) Color(0xFF0B141A) else Color.White,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 10.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = tab.label,
                                color = tintColor,
                                fontSize = labelFontSize,
                                lineHeight = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
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
}
