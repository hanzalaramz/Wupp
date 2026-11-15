package com.wa.beastclient.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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

@Composable
fun WallpaperPickerDialog(
    currentUri: String,
    currentDim: Float,
    showDoodleOnPresets: Boolean,
    onSelectUri: (String) -> Unit,
    onPickCustomImage: (Uri) -> Unit,
    onDimChange: (Float) -> Unit,
    onToggleDoodle: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalWaColors.current
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPickCustomImage(uri)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        title = {
            Text(
                text = "Chat Wallpaper",
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Live chat preview banner
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.chatCanvas,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(124.dp)
                        .border(1.dp, colors.divider, RoundedCornerShape(14.dp))
                ) {
                    DoodleBackground(
                        wallpaperUriOverride = currentUri,
                        wallpaperDimOverride = currentDim,
                        showDoodleOverride = showDoodleOnPresets,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.incomingBubble
                            ) {
                                Text(
                                    text = "Custom background preview",
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = colors.outgoingBubble
                                ) {
                                    Text(
                                        text = describeChatWallpaper(currentUri),
                                        color = colors.textPrimary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Gallery + Reset action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("From gallery", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(
                        onClick = { onSelectUri("") }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Default", color = colors.textSecondary, fontSize = 13.sp)
                    }
                }

                // Curated color/gradient presets
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Presets",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ChatWallpaperPresets.forEach { preset ->
                            val isSelected = currentUri == preset.storageUri
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.clickable { onSelectUri(preset.storageUri) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Brush.verticalGradient(listOf(preset.topColor, preset.bottomColor)))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) colors.brightGreen else colors.divider,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = colors.brightGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = preset.label,
                                    color = if (isSelected) colors.textPrimary else colors.textSecondary,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }
                }

                if (currentUri.startsWith("file:")) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Overlay dimness (${(currentDim * 100).toInt()}%)",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                        Slider(
                            value = currentDim.coerceIn(0f, 0.75f),
                            onValueChange = onDimChange,
                            valueRange = 0f..0.75f,
                            colors = SliderDefaults.colors(
                                thumbColor = colors.brightGreen,
                                activeTrackColor = colors.accentGreen,
                                inactiveTrackColor = colors.divider
                            )
                        )
                    }
                } else if (currentUri.startsWith("preset:")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Show doodle overlay",
                            color = colors.textPrimary,
                            fontSize = 13.5.sp
                        )
                        Switch(
                            checked = showDoodleOnPresets,
                            onCheckedChange = onToggleDoodle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.accentGreen
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = colors.brightGreen, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
