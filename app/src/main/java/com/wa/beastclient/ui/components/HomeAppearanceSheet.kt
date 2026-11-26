package com.wa.beastclient.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.domain.appearance.HomeAccentStyle
import com.wa.beastclient.domain.appearance.HomeAppearanceConfig
import com.wa.beastclient.domain.appearance.HomeAppearanceManager
import com.wa.beastclient.domain.appearance.HomeBadgeStyle
import com.wa.beastclient.domain.appearance.HomeLayoutPreset
import com.wa.beastclient.domain.appearance.HomeNavStyle
import com.wa.beastclient.domain.appearance.HomeNewChatStyle
import com.wa.beastclient.domain.appearance.HomeRowDensity
import com.wa.beastclient.domain.appearance.HomeSectionKind
import com.wa.beastclient.domain.appearance.HomeSurfaceTone
import com.wa.beastclient.domain.appearance.moveSectionInOrder
import com.wa.beastclient.ui.theme.LocalWaColors
import com.wa.beastclient.ui.theme.contrastingContentColor

/**
 * Original geometric Wupp rounded emblem (squircle + twin rounded wave stroke).
 */
@Composable
fun WuppBrandMark(
    accent: Color,
    size: Dp = 26.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val corner = w * 0.30f
        drawRoundRect(
            color = accent.copy(alpha = 0.16f),
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = CornerRadius(corner, corner)
        )
        drawRoundRect(
            color = accent.copy(alpha = 0.50f),
            topLeft = Offset(0.75f, 0.75f),
            size = Size(w - 1.5f, h - 1.5f),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = w * 0.06f)
        )
        val path = Path().apply {
            moveTo(w * 0.24f, h * 0.36f)
            lineTo(w * 0.37f, h * 0.66f)
            lineTo(w * 0.50f, h * 0.42f)
            lineTo(w * 0.63f, h * 0.66f)
            lineTo(w * 0.76f, h * 0.36f)
        }
        drawPath(
            path = path,
            color = accent,
            style = Stroke(
                width = w * 0.105f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeAppearanceSheet(
    onDismiss: () -> Unit
) {
    val colors = LocalWaColors.current
    val cfg by HomeAppearanceManager.config.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCategory by remember { mutableIntStateOf(0) }
    val categories = listOf("Layout", "Colors", "Type", "Rows", "Order", "Nav & +", "Motion")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.elevatedBar
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WuppBrandMark(
                        accent = cfg.accentStyle.resolve(colors.isDark),
                        size = 28.dp
                    )
                    Column {
                        Text(
                            text = "Home appearance",
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${cfg.layoutPreset.label} preset · live preview",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                OutlinedButton(
                    onClick = { HomeAppearanceManager.resetCurrentLayout() },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Reset", fontSize = 12.5.sp)
                }
            }

            // Live Mini Preview
            HomeAppearanceLivePreview(cfg = cfg)

            // Category Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEachIndexed { idx, label ->
                    val active = selectedCategory == idx
                    val accent = cfg.accentStyle.resolve(colors.isDark)
                    val bg by animateColorAsState(
                        targetValue = if (active) accent.copy(alpha = 0.20f) else colors.appSurface,
                        animationSpec = tween(if (cfg.reducedMotion) 0 else 150),
                        label = "cat_bg_$idx"
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(bg)
                            .border(
                                1.dp,
                                if (active) accent else colors.divider,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { selectedCategory = idx }
                            .padding(horizontal = 13.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (active) accent else colors.textSecondary,
                            fontSize = 12.5.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }

            // Control Panel
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colors.appSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedCategory) {
                        0 -> LayoutPresetControls(cfg = cfg)
                        1 -> ColorControls(cfg = cfg)
                        2 -> TypographyControls(cfg = cfg)
                        3 -> ChatRowControls(cfg = cfg)
                        4 -> SectionOrderControls(cfg = cfg)
                        5 -> NavAndNewChatControls(cfg = cfg)
                        else -> MotionControls(cfg = cfg)
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = cfg.accentStyle.resolve(colors.isDark),
                    contentColor = cfg.accentStyle.resolve(colors.isDark).contrastingContentColor()
                )
            ) {
                Text("Done", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HomeAppearanceLivePreview(cfg: HomeAppearanceConfig) {
    val colors = LocalWaColors.current
    val accent = cfg.accentStyle.resolve(colors.isDark)
    val previewSurface = when (cfg.surfaceTone) {
        HomeSurfaceTone.OLED_PURE -> if (colors.isDark) Color(0xFF000000) else colors.appSurface
        HomeSurfaceTone.ELEVATED_CARDS -> if (colors.isDark) Color(0xFF14181F) else Color(0xFFF6F7FA)
        HomeSurfaceTone.ADAPTIVE -> colors.appSurface
    }
    val rowBg = if (cfg.surfaceTone == HomeSurfaceTone.ELEVATED_CARDS) {
        if (colors.isDark) Color(0xFF1C222B) else Color.White
    } else {
        Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(previewSurface)
            .border(1.dp, colors.divider, RoundedCornerShape(22.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Mini Top Bar matching selected preset
            when (cfg.layoutPreset) {
                HomeLayoutPreset.IOS_INSPIRED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Chats",
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (cfg.newChatButtonStyle == HomeNewChatStyle.TOP_BAR_ONLY ||
                            cfg.newChatButtonStyle == HomeNewChatStyle.BOTH
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = accent.contrastingContentColor(),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
                HomeLayoutPreset.CLASSIC -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            WuppBrandMark(accent = accent, size = 20.dp)
                            Text(
                                text = "Wupp",
                                color = colors.textPrimary,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (cfg.newChatButtonStyle == HomeNewChatStyle.TOP_BAR_ONLY ||
                            cfg.newChatButtonStyle == HomeNewChatStyle.BOTH
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                HomeLayoutPreset.COMPACT -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                            )
                            Text(
                                text = "Chats",
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (cfg.newChatButtonStyle == HomeNewChatStyle.TOP_BAR_ONLY ||
                            cfg.newChatButtonStyle == HomeNewChatStyle.BOTH
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            // Dynamic Section Order preview
            cfg.sectionOrder.forEach { sec ->
                when (sec) {
                    HomeSectionKind.SEARCH_BAR -> if (cfg.showSearchBar) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.elevatedBar)
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text("Search", color = colors.textSecondary, fontSize = 10.5.sp)
                        }
                    }
                    HomeSectionKind.FILTER_CHIPS -> if (cfg.showFilterChips) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("All" to true, "Unread 3" to false, "Groups" to false).forEach { (lbl, act) ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (act) accent.copy(alpha = 0.18f) else Color.Transparent)
                                        .border(
                                            0.8.dp,
                                            if (act) accent else colors.divider,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = lbl,
                                        color = if (act) accent else colors.textSecondary,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    HomeSectionKind.ARCHIVED_BANNER -> {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Archive,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Archived",
                                color = colors.textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Sample Chat Rows
            PreviewSampleRow(
                name = "Amina Noor",
                preview = "Draft: Let's meet at 5pm near the park",
                time = "14:22",
                unread = 2,
                cfg = cfg,
                accent = accent,
                rowBg = rowBg,
                isDraft = true
            )
            if (cfg.showRowDividers) {
                HorizontalDivider(color = colors.divider)
            }
            PreviewSampleRow(
                name = "Design Crew",
                preview = "Sara: Uploaded the new rounded icons",
                time = "13:40",
                unread = 0,
                cfg = cfg,
                accent = accent,
                rowBg = rowBg,
                isDraft = false
            )

            // Mini Bottom Bar
            val barShape = when (cfg.navBarStyle) {
                HomeNavStyle.FLOATING_PILL -> RoundedCornerShape(16.dp)
                HomeNavStyle.DOCKED_BAR -> RoundedCornerShape(8.dp)
                HomeNavStyle.COMPACT_ICONS -> RoundedCornerShape(12.dp)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(barShape)
                    .background(colors.elevatedBar)
                    .border(0.7.dp, colors.divider, barShape)
                    .padding(vertical = if (cfg.navBarStyle == HomeNavStyle.COMPACT_ICONS) 5.dp else 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val navItems = listOf(
                    Icons.Rounded.ChatBubbleOutline to "Chats",
                    Icons.Rounded.Explore to "Updates",
                    Icons.Rounded.Groups to "Groups",
                    Icons.Rounded.Call to "Calls",
                    Icons.Rounded.Person to "You"
                )
                navItems.forEachIndexed { idx, (icon, label) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (idx == 0) accent else colors.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        if (cfg.showNavLabels && cfg.navBarStyle != HomeNavStyle.COMPACT_ICONS) {
                            Text(
                                text = label,
                                color = if (idx == 0) accent else colors.textSecondary,
                                fontSize = 8.5.sp
                            )
                        }
                    }
                }
            }
        }

        // Floating New-Chat preview badge
        if (cfg.newChatButtonStyle == HomeNewChatStyle.FLOATING_CIRCLE ||
            cfg.newChatButtonStyle == HomeNewChatStyle.EXTENDED_PILL ||
            cfg.newChatButtonStyle == HomeNewChatStyle.BOTH
        ) {
            val isExtended = cfg.newChatButtonStyle == HomeNewChatStyle.EXTENDED_PILL
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 36.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent)
                    .padding(horizontal = if (isExtended) 9.dp else 7.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = accent.contrastingContentColor(),
                    modifier = Modifier.size(12.dp)
                )
                if (isExtended) {
                    Text(
                        text = "New chat",
                        color = accent.contrastingContentColor(),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewSampleRow(
    name: String,
    preview: String,
    time: String,
    unread: Int,
    cfg: HomeAppearanceConfig,
    accent: Color,
    rowBg: Color,
    isDraft: Boolean
) {
    val colors = LocalWaColors.current
    val avatarScale = (cfg.avatarSizeDp * 0.62f).dp
    val vPad = (cfg.rowDensity.verticalPadDp * 0.42f).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(rowBg)
            .padding(horizontal = 6.dp, vertical = vPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(avatarScale)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1),
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = name,
                    color = colors.textPrimary,
                    fontSize = (12.5f * cfg.titleScale).sp,
                    fontWeight = if (unread > 0 && cfg.boldUnreadTitles) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = time,
                    color = if (unread > 0) accent else colors.textSecondary,
                    fontSize = 9.5.sp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = preview,
                    color = if (isDraft) accent else colors.textSecondary,
                    fontSize = (10.5f * cfg.subtitleScale).sp,
                    maxLines = cfg.previewMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (unread > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    when (cfg.unreadBadgeStyle) {
                        HomeBadgeStyle.SOLID_PILL -> Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(accent)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = unread.toString(),
                                color = accent.contrastingContentColor(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HomeBadgeStyle.SOFT_TINT -> Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(accent.copy(alpha = 0.18f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = unread.toString(),
                                color = accent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HomeBadgeStyle.MINIMAL_DOT -> Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LayoutPresetControls(cfg: HomeAppearanceConfig) {
    val colors = LocalWaColors.current
    val accent = cfg.accentStyle.resolve(colors.isDark)
    Text(
        text = "Choose a Home layout",
        color = colors.textPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
    )
    HomeLayoutPreset.entries.forEach { preset ->
        val selected = cfg.layoutPreset == preset
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) accent.copy(alpha = 0.14f) else colors.elevatedBar)
                .border(
                    1.2.dp,
                    if (selected) accent else colors.divider,
                    RoundedCornerShape(14.dp)
                )
                .clickable { HomeAppearanceManager.applyLayoutPreset(preset) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.label,
                    color = if (selected) accent else colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = preset.subtitle,
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ColorControls(cfg: HomeAppearanceConfig) {
    val colors = LocalWaColors.current
    val activeAccent = cfg.accentStyle.resolve(colors.isDark)
    Text("Accent tint", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeAccentStyle.entries.forEach { style ->
            val swatch = style.resolve(colors.isDark)
            val selected = cfg.accentStyle == style
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(swatch)
                    .border(
                        if (selected) 2.5.dp else 1.dp,
                        if (selected) colors.textPrimary else Color.Transparent,
                        CircleShape
                    )
                    .clickable { HomeAppearanceManager.update { it.copy(accentStyle = style) } },
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = style.label,
                        tint = swatch.contrastingContentColor(),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    Text("Unread badge", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    ChoiceChipRow(
        options = HomeBadgeStyle.entries.map { it to it.label },
        selected = cfg.unreadBadgeStyle,
        accent = activeAccent,
        onSelect = { style -> HomeAppearanceManager.update { it.copy(unreadBadgeStyle = style) } }
    )

    Text("Surface tone", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    ChoiceChipRow(
        options = HomeSurfaceTone.entries.map { it to it.label },
        selected = cfg.surfaceTone,
        accent = activeAccent,
        onSelect = { tone -> HomeAppearanceManager.update { it.copy(surfaceTone = tone) } }
    )
}

@Composable
private fun TypographyControls(cfg: HomeAppearanceConfig) {
    val colors = LocalWaColors.current
    val accent = cfg.accentStyle.resolve(colors.isDark)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Chat title size", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        Text("${(cfg.titleScale * 100).toInt()}%", color = accent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
    Slider(
        value = cfg.titleScale,
        onValueChange = { v -> HomeAppearanceManager.update { it.copy(titleScale = v) } },
        valueRange = 0.85f..1.20f,
        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Message preview size", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        Text("${(cfg.subtitleScale * 100).toInt()}%", color = accent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
    Slider(
        value = cfg.subtitleScale,
        onValueChange = { v -> HomeAppearanceManager.update { it.copy(subtitleScale = v) } },
        valueRange = 0.85f..1.15f,
        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
    )

    ToggleRow(
        title = "Bold unread chat names",
        subtitle = "Makes unread chats stand out faster",
        checked = cfg.boldUnreadTitles,
        accent = accent,
        onCheckedChange = { checked -> HomeAppearanceManager.update { it.copy(boldUnreadTitles = checked) } }
    )
}

@Composable
private fun ChatRowControls(cfg: HomeAppearanceConfig) {
    val colors = LocalWaColors.current
    val accent = cfg.accentStyle.resolve(colors.isDark)

    Text("Row spacing", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    ChoiceChipRow(
        options = HomeRowDensity.entries.map { it to it.label },
        selected = cfg.rowDensity,
        accent = accent,
        onSelect = { density -> HomeAppearanceManager.update { it.copy(rowDensity = density) } }
    )

    Text("Avatar size", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    ChoiceChipRow(
        options = listOf(42 to "Small", 48 to "Medium", 54 to "Large"),
        selected = cfg.avatarSizeDp,
        accent = accent,
        onSelect = { size -> HomeAppearanceManager.update { it.copy(avatarSizeDp = size) } }
    )

    Text("Preview lines", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    ChoiceChipRow(
        options = listOf(1 to "1 line", 2 to "2 lines"),
        selected = cfg.previewMaxLines,
        accent = accent,
        onSelect = { lines -> HomeAppearanceManager.update { it.copy(previewMaxLines = lines) } }
    )

    ToggleRow(
        title = "Subtle row dividers",
        subtitle = "Show thin separators between chats",
        checked = cfg.showRowDividers,
        accent = accent,
        onCheckedChange = { checked -> HomeAppearanceManager.update { it.copy(showRowDividers = checked) } }
    )
}

@Composable
private fun SectionOrderControls(cfg: HomeAppearanceConfig) {
    val colors = LocalWaColors.current
    val accent = cfg.accentStyle.resolve(colors.isDark)

    Text("Section order", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    cfg.sectionOrder.forEachIndexed { idx, section ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.elevatedBar)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${idx + 1}. ${section.label}",
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Row {
                IconButton(
                    enabled = idx > 0,
                    onClick = {
                        HomeAppearanceManager.update {
                            it.copy(sectionOrder = moveSectionInOrder(it.sectionOrder, section, -1))
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = "Move up",
                        tint = if (idx > 0) accent else colors.textSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    enabled = idx < cfg.sectionOrder.lastIndex,
                    onClick = {
                        HomeAppearanceManager.update {
                            it.copy(sectionOrder = moveSectionInOrder(it.sectionOrder, section, 1))
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDownward,
                        contentDescription = "Move down",
                        tint = if (idx < cfg.sectionOrder.lastIndex) accent else colors.textSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    ToggleRow(
        title = "Show search bar",
        subtitle = "Keep inline search visible at the top",
        checked = cfg.showSearchBar,
        accent = accent,
        onCheckedChange = { checked -> HomeAppearanceManager.update { it.copy(showSearchBar = checked) } }
    )
    ToggleRow(
        title = "Show filter chips",
        subtitle = "All, Unread, Favorites, and Groups",
        checked = cfg.showFilterChips,
        accent = accent,
        onCheckedChange = { checked -> HomeAppearanceManager.update { it.copy(showFilterChips = checked) } }
    )
}

@Composable
private fun NavAndNewChatControls(cfg: HomeAppearanceConfig) {
    val colors = LocalWaColors.current
    val accent = cfg.accentStyle.resolve(colors.isDark)

    Text("Navigation bar", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    ChoiceChipRow(
        options = HomeNavStyle.entries.map { it to it.label },
        selected = cfg.navBarStyle,
        accent = accent,
        onSelect = { style -> HomeAppearanceManager.update { it.copy(navBarStyle = style) } }
    )

    ToggleRow(
        title = "Show tab labels",
        subtitle = "Display text under navigation icons",
        checked = cfg.showNavLabels,
        accent = accent,
        onCheckedChange = { checked -> HomeAppearanceManager.update { it.copy(showNavLabels = checked) } }
    )

    Text("New-chat button", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    ChoiceChipRow(
        options = HomeNewChatStyle.entries.map { it to it.label },
        selected = cfg.newChatButtonStyle,
        accent = accent,
        onSelect = { style -> HomeAppearanceManager.update { it.copy(newChatButtonStyle = style) } }
    )
}

@Composable
private fun MotionControls(cfg: HomeAppearanceConfig) {
    val accent = cfg.accentStyle.resolve(LocalWaColors.current.isDark)
    ToggleRow(
        title = "Reduced motion",
        subtitle = "Minimize tab, badge, and row transitions (also follows Android system setting)",
        checked = cfg.reducedMotion,
        accent = accent,
        onCheckedChange = { checked -> HomeAppearanceManager.update { it.copy(reducedMotion = checked) } }
    )
}

@Composable
private fun <T> ChoiceChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    accent: Color,
    onSelect: (T) -> Unit
) {
    val colors = LocalWaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) accent.copy(alpha = 0.18f) else colors.elevatedBar)
                    .border(
                        1.dp,
                        if (isSelected) accent else colors.divider,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = label,
                    color = if (isSelected) accent else colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalWaColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = colors.textSecondary, fontSize = 11.5.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = accent.contrastingContentColor(), checkedTrackColor = accent)
        )
    }
}
