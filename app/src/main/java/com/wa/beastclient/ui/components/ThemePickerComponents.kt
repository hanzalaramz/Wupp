package com.wa.beastclient.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.ui.theme.LocalWaColors
import com.wa.beastclient.ui.theme.WuppThemeMode
import com.wa.beastclient.ui.theme.colorsForTheme

@Composable
fun ThemeSelectorGrid(
    selectedTheme: WuppThemeMode,
    onSelectTheme: (WuppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = WuppThemeMode.entries.chunked(2)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEach { rowModes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowModes.forEach { mode ->
                    ThemePreviewCard(
                        mode = mode,
                        selected = mode == selectedTheme,
                        onClick = { onSelectTheme(mode) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun ThemePickerDialog(
    selectedTheme: WuppThemeMode,
    onSelectTheme: (WuppThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalWaColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = {
            Text(
                text = "Choose Theme",
                fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp
            )
        },
        text = {
            ThemeSelectorGrid(
                selectedTheme = selectedTheme,
                onSelectTheme = { mode ->
                    onSelectTheme(mode)
                    onDismiss()
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = colors.brightGreen)
            }
        }
    )
}

@Composable
private fun ThemePreviewCard(
    mode: WuppThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalWaColors.current
    val preview = colorsForTheme(mode)
    val borderColor by animateColorAsState(
        targetValue = if (selected) appColors.brightGreen else appColors.divider,
        label = "themeBorder"
    )
    val cardBg by animateColorAsState(
        targetValue = if (selected) appColors.pillActiveBg.copy(alpha = 0.42f) else appColors.appSurface.copy(alpha = 0.45f),
        label = "themeCardBg"
    )

    val modeIcon = when (mode) {
        WuppThemeMode.LIGHT -> Icons.Rounded.LightMode
        WuppThemeMode.DARK -> Icons.Rounded.DarkMode
        WuppThemeMode.AMOLED -> Icons.Rounded.NightlightRound
        WuppThemeMode.AURORA -> Icons.Rounded.AutoAwesome
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = cardBg
    ) {
        Column(
            modifier = Modifier.padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Live miniature chat window preview in that theme's exact palette
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(preview.chatCanvas)
                    .border(
                        width = 0.6.dp,
                        color = preview.divider,
                        shape = RoundedCornerShape(11.dp)
                    )
            ) {
                if (mode == WuppThemeMode.AURORA) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0x3D6D28D9),
                                        Color.Transparent,
                                        Color(0x2800E5B8)
                                    )
                                )
                            )
                    )
                }

                Column(modifier = Modifier.matchParentSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(preview.elevatedBar)
                            .padding(horizontal = 7.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(preview.accentGreen)
                        )
                        Box(
                            modifier = Modifier
                                .width(34.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(preview.textPrimary.copy(alpha = 0.72f))
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(preview.brightGreen)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 7.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(56.dp)
                                .height(13.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(preview.incomingBubble)
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(34.dp)
                                    .height(3.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(preview.textSecondary.copy(alpha = 0.65f))
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Row(
                                modifier = Modifier
                                    .width(66.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(preview.outgoingBubble)
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(38.dp)
                                        .height(3.5.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(preview.textPrimary.copy(alpha = 0.85f))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(4.5.dp)
                                        .clip(CircleShape)
                                        .background(preview.blueTick)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = modeIcon,
                    contentDescription = null,
                    tint = if (selected) appColors.brightGreen else preview.accentGreen,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = mode.label,
                    color = appColors.textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Selected",
                        tint = appColors.brightGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = mode.tagline,
                color = appColors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
