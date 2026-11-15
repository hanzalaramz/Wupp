package com.wa.beastclient.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.R
import com.wa.beastclient.ui.theme.LocalWaColors
import com.wa.beastclient.ui.theme.WaColors

/**
 * Applies a translucent frosted-glass surface with a subtle specular highlight rim
 * tuned for Light, Dark, AMOLED, and Aurora themes.
 */
fun Modifier.wuppGlassSurface(
    colors: WaColors,
    shape: Shape = CircleShape,
    elevation: Dp = 14.dp,
    alpha: Float = if (colors.isDark) 0.78f else 0.86f
): Modifier {
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
    val rimTopLeft = if (colors.isDark) {
        Color.White.copy(alpha = 0.32f)
    } else {
        Color.White.copy(alpha = 0.92f)
    }
    val rimBottomRight = if (colors.isDark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        colors.textSecondary.copy(alpha = 0.18f)
    }

    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = if (colors.isDark) 0.55f else 0.18f),
            spotColor = Color.Black.copy(alpha = if (colors.isDark) 0.65f else 0.22f)
        )
        .clip(shape)
        .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
        .background(
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = if (colors.isDark) 0.08f else 0.28f),
                    Color.Transparent
                )
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(listOf(rimTopLeft, rimBottomRight)),
            shape = shape
        )
}

private data class GlassNavTab(
    val index: Int,
    val label: String,
    val drawableRes: Int? = null,
    val imageVector: ImageVector? = null,
    val badgeCount: Int = 0
)

/**
 * Floating translucent Liquid-Glass bottom navigation bar inspired by modern pill nav bars,
 * featuring an animated sliding active capsule and single-line non-wrapping labels.
 */
@Composable
fun WuppGlassNavBar(
    currentTab: Int,
    unreadChatsCount: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaColors.current
    val tabs = remember(unreadChatsCount) {
        listOf(
            GlassNavTab(0, "Chats", drawableRes = R.drawable.vec_ic_chat_filled, badgeCount = unreadChatsCount),
            GlassNavTab(1, "Updates", drawableRes = R.drawable.vec_ic_status),
            GlassNavTab(2, "Communities", drawableRes = R.drawable.vec_ic_avatar_community),
            GlassNavTab(3, "Settings", imageVector = Icons.Rounded.Shield)
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        colors.appSurface.copy(alpha = if (colors.isDark) 0.55f else 0.65f)
                    )
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .wuppGlassSurface(colors = colors, shape = CircleShape, elevation = 18.dp, alpha = if (colors.isDark) 0.72f else 0.84f)
                .padding(4.dp)
        ) {
            val tabWidth = maxWidth / tabs.size
            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * currentTab.coerceIn(0, tabs.lastIndex),
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "glass_nav_indicator_offset"
            )

            // Sliding frosted active capsule
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(
                        if (colors.isDark) {
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.19f),
                                    Color.White.copy(alpha = 0.11f)
                                )
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(
                                    colors.accentGreen.copy(alpha = 0.18f),
                                    colors.accentGreen.copy(alpha = 0.11f)
                                )
                            )
                        }
                    )
                    .border(
                        width = 0.8.dp,
                        color = if (colors.isDark) {
                            Color.White.copy(alpha = 0.24f)
                        } else {
                            colors.accentGreen.copy(alpha = 0.28f)
                        },
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
                            else -> colors.textSecondary
                        },
                        animationSpec = tween(durationMillis = 180),
                        label = "glass_nav_tint_${tab.index}"
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.07f else 1f,
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
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
                                val badgeRing = if (colors.isDark) Color(0xFF141E24) else Color.White
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 3.dp, y = (-3).dp)
                                        .defaultMinSize(minWidth = 17.dp, minHeight = 16.dp)
                                        .clip(CircleShape)
                                        .background(colors.brightGreen)
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
                            fontSize = 10.5.sp,
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
