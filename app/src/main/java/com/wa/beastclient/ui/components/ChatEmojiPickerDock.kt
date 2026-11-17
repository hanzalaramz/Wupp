package com.wa.beastclient.ui.components

import android.graphics.BitmapFactory
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import com.wa.beastclient.R
import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.ui.theme.WaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.BreakIterator

/**
 * Inserts [insert] into [current] at the active selection range (replacing any selected text)
 * and advances the cursor cleanly right past the inserted text.
 */
internal fun insertTextAtSelection(current: TextFieldValue, insert: String): TextFieldValue {
    val text = current.text
    val start = current.selection.min.coerceIn(0, text.length)
    val end = current.selection.max.coerceIn(0, text.length)
    val updated = buildString(text.length + insert.length) {
        append(text, 0, start)
        append(insert)
        append(text, end, text.length)
    }
    return TextFieldValue(
        text = updated,
        selection = TextRange(start + insert.length)
    )
}

/**
 * Deletes the selected range, or one full Unicode grapheme cluster before the cursor,
 * preventing split surrogate pairs or broken ZWJ emoji sequences.
 */
internal fun deleteBeforeSelection(current: TextFieldValue): TextFieldValue {
    val text = current.text
    if (text.isEmpty()) return current
    val start = current.selection.min.coerceIn(0, text.length)
    val end = current.selection.max.coerceIn(0, text.length)
    if (start != end) {
        return TextFieldValue(
            text = text.removeRange(start, end),
            selection = TextRange(start)
        )
    }
    if (start == 0) return current
    val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
    val prev = iterator.preceding(start).takeIf { it != BreakIterator.DONE } ?: (start - 1)
    val safePrev = prev.coerceIn(0, start)
    return TextFieldValue(
        text = text.removeRange(safePrev, start),
        selection = TextRange(safePrev)
    )
}

enum class DockTab { EMOJI, STICKER }

/**
 * Native AndroidX [EmojiPickerView] & Sticker panel docked inside the chat screen,
 * with bottom tab navigation, grapheme-aware repeat backspace, and WhatsApp dark/light styling.
 */
@Composable
fun ChatEmojiPickerDock(
    dockHeight: Dp,
    colors: WaColors,
    onEmojiSelected: (String) -> Unit,
    onBackspace: () -> Unit,
    recentStickers: List<MessageEntity> = emptyList(),
    onStickerSelected: (MessageEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(DockTab.EMOJI) }
    val latestOnEmojiSelected by rememberUpdatedState(onEmojiSelected)
    val latestOnBackspace by rememberUpdatedState(onBackspace)
    val latestOnStickerSelected by rememberUpdatedState(onStickerSelected)
    val dockBg = colors.chatCanvas
    val barBg = colors.elevatedBar
    val backspaceCd = stringResource(R.string.chat_emoji_backspace)
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(dockHeight)
            .background(dockBg)
    ) {
        HorizontalDivider(color = colors.divider, thickness = 0.6.dp)

        // Top bar: Title + drag handle + backspace (for emoji tab)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(barBg)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selectedTab == DockTab.EMOJI) {
                    stringResource(R.string.chat_emoji_panel_title)
                } else {
                    stringResource(R.string.chat_sticker_panel_title)
                },
                color = colors.textSecondary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.textSecondary.copy(alpha = 0.28f))
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (selectedTab == DockTab.EMOJI) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .semantics {
                                contentDescription = backspaceCd
                                role = Role.Button
                            }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    latestOnBackspace()
                                    val repeatJob = scope.launch {
                                        delay(380L)
                                        while (true) {
                                            latestOnBackspace()
                                            delay(65L)
                                        }
                                    }
                                    try {
                                        waitForUpOrCancellation()
                                    } finally {
                                        repeatJob.cancel()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Backspace,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }

        // Center Content Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedTab) {
                DockTab.EMOJI -> {
                    key(colors.isDark) {
                        val themeRes = if (colors.isDark) {
                            R.style.ThemeOverlay_Wupp_EmojiPicker_Dark
                        } else {
                            R.style.ThemeOverlay_Wupp_EmojiPicker_Light
                        }
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                EmojiPickerView(ContextThemeWrapper(ctx, themeRes)).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    setBackgroundColor(dockBg.toArgb())
                                    emojiGridColumns = 9
                                    setOnEmojiPickedListener { item ->
                                        latestOnEmojiSelected(item.emoji)
                                    }
                                }
                            },
                            update = { picker ->
                                picker.setBackgroundColor(dockBg.toArgb())
                                picker.setOnEmojiPickedListener { item ->
                                    latestOnEmojiSelected(item.emoji)
                                }
                            }
                        )
                    }
                }
                DockTab.STICKER -> {
                    if (recentStickers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp, vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_tab_sticker),
                                    contentDescription = null,
                                    tint = colors.textSecondary.copy(alpha = 0.45f),
                                    modifier = Modifier.size(46.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stringResource(R.string.chat_sticker_empty_title),
                                    color = colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.chat_sticker_empty_desc),
                                    color = colors.textSecondary,
                                    fontSize = 12.5.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(recentStickers, key = { it.id }) { stickerMsg ->
                                StickerPickerGridItem(
                                    msg = stickerMsg,
                                    colors = colors,
                                    onClick = { latestOnStickerSelected(stickerMsg) }
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = colors.divider, thickness = 0.5.dp)

        // Bottom Tab Switcher Bar: [😊 Emoji | 💟 Stickers]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(barBg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { selectedTab = DockTab.EMOJI },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SentimentSatisfiedAlt,
                        contentDescription = stringResource(R.string.chat_emoji_panel_title),
                        tint = if (selectedTab == DockTab.EMOJI) colors.accentGreen else colors.textSecondary,
                        modifier = Modifier.size(23.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (selectedTab == DockTab.EMOJI) colors.accentGreen else Color.Transparent)
                    )
                }
            }

            // Sticker Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { selectedTab = DockTab.STICKER },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tab_sticker),
                        contentDescription = stringResource(R.string.chat_sticker_panel_title),
                        tint = if (selectedTab == DockTab.STICKER) colors.accentGreen else colors.textSecondary,
                        modifier = Modifier.size(21.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (selectedTab == DockTab.STICKER) colors.accentGreen else Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
private fun StickerPickerGridItem(
    msg: MessageEntity,
    colors: WaColors,
    onClick: () -> Unit
) {
    val localFile = remember(msg.localMediaPath) {
        msg.localMediaPath?.takeIf { it.isNotBlank() }?.let { path ->
            File(path).takeIf { it.exists() && it.length() > 0L }
        }
    }
    val bmp by produceState<ImageBitmap?>(initialValue = null, key1 = localFile?.absolutePath) {
        value = if (localFile != null) {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(localFile.absolutePath)?.asImageBitmap() }.getOrNull()
            }
        } else null
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        val loaded = bmp
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = "Sticker",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.incomingBubble.copy(alpha = 0.35f))
            )
        }
    }
}
