package com.wa.beastclient.ui.components

import android.graphics.BitmapFactory
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.text.TextStyle
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
import com.wa.beastclient.data.media.BuiltinSticker
import com.wa.beastclient.data.media.CuppyPackManager
import com.wa.beastclient.data.media.CustomStickerItem
import com.wa.beastclient.data.media.isStickerMatchedByFavoriteKeys
import com.wa.beastclient.data.media.CustomStickerStore
import com.wa.beastclient.ui.theme.WaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

private val emojiGrapheme = Regex("""\X""")

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
    val cluster = emojiGrapheme.findAll(text).first { it.range.last + 1 >= start }
    return TextFieldValue(
        text = text.removeRange(cluster.range.first, cluster.range.last + 1),
        selection = TextRange(cluster.range.first)
    )
}

enum class DockTab { EMOJI, GIF, STICKER }

enum class StickerSubTab { RECENT, FAVORITES, CUPPY }

/**
 * Native WhatsApp-style Emoji, GIF, and Sticker Dock.
 * Matches official WhatsApp pixel-for-pixel:
 * - Expandable / stretchable height with top drag handle (drag up to stretch to ~74% screen height)
 * - Centered stadium pill segmented switcher [ 😊 | GIF | 📑 ]
 * - Left search icon that opens an inline search query bar
 * - Right action icon:
 *   - Emoji: Backspace with repeating grapheme cluster deletion
 *   - Sticker: Pencil/Edit button to create/edit stickers
 *   - GIF: Search / clear
 * - Single continuous scrolling sticker grid with 5 columns:
 *   - Item 0: Green circular "Create sticker" card
 *   - Recent stickers
 *   - "Favorites" section header + favorite stickers
 *   - "Coffee" section header + built-in coffee stickers
 * - Bottom sticker pack switcher bar [ 🕒 Recent | ⭐ Favorites | ☕ Coffee | ➕ Add ] with active circular pill highlight
 */
@Composable
fun ChatEmojiPickerDock(
    dockHeight: Dp,
    colors: WaColors,
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onExpand: () -> Unit = {},
    onCollapse: () -> Unit = {},
    onDismiss: () -> Unit = {},
    onEmojiSelected: (String) -> Unit = {},
    onBackspace: () -> Unit = {},
    recentStickers: List<MessageEntity> = emptyList(),
    favoriteStickers: List<MessageEntity> = emptyList(),
    favoriteStickerKeys: Set<String> = emptySet(),
    cuppyStickers: List<BuiltinSticker> = emptyList(),
    customStickers: List<CustomStickerItem> = emptyList(),
    onStickerSelected: (MessageEntity) -> Unit = {},
    onSendBuiltinSticker: (BuiltinSticker) -> Unit = {},
    onSendCustomSticker: (CustomStickerItem) -> Unit = {},
    onDeleteCustomSticker: (CustomStickerItem) -> Unit = {},
    onToggleFavorite: (MessageEntity) -> Unit = {},
    onCreateSticker: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(DockTab.STICKER) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var contextStickerItem by remember { mutableStateOf<Any?>(null) } // MessageEntity or BuiltinSticker

    val latestOnEmojiSelected by rememberUpdatedState(onEmojiSelected)
    val latestOnBackspace by rememberUpdatedState(onBackspace)
    val latestOnStickerSelected by rememberUpdatedState(onStickerSelected)
    val latestOnSendBuiltin by rememberUpdatedState(onSendBuiltinSticker)
    val latestOnSendCustom by rememberUpdatedState(onSendCustomSticker)
    val latestOnDeleteCustom by rememberUpdatedState(onDeleteCustomSticker)
    val latestOnToggleFav by rememberUpdatedState(onToggleFavorite)
    val latestOnCreateSticker by rememberUpdatedState(onCreateSticker)

    val dockBg = if (colors.isDark) Color(0xFF0B141B) else Color(0xFFFFFFFF)
    val barBg = if (colors.isDark) Color(0xFF121B22) else Color(0xFFF0F2F5)
    val pillBg = if (colors.isDark) Color(0xFF1F2C34) else Color(0xFFE9EDEF)
    val pillActiveBg = if (colors.isDark) Color(0xFF2A3942) else Color.White
    val greenAccent = colors.accentGreen
    val scope = rememberCoroutineScope()

    // Continuous sticker list state
    val stickerGridState = rememberLazyGridState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(dockHeight)
            .background(dockBg)
    ) {
        HorizontalDivider(color = colors.divider.copy(alpha = 0.5f), thickness = 0.5.dp)

        // 1. Top Drag Handle (Tap to toggle stretch, drag vertically to expand/collapse)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(barBg)
                .pointerInput(isExpanded) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -18f) {
                            onExpand()
                        } else if (dragAmount > 18f) {
                            if (isExpanded) onCollapse() else onDismiss()
                        }
                    }
                }
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleExpand
                )
                .padding(top = 7.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.textSecondary.copy(alpha = 0.38f))
            )
        }

        // 2. Unified Top Action Bar: Search | [ 😊 | GIF | 📑 ] Segmented Pill | Backspace / Edit / Close
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(barBg)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSearchActive) {
                // Inline Search Mode
                IconButton(
                    onClick = {
                        isSearchActive = false
                        searchQuery = ""
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(greenAccent),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = when (selectedTab) {
                                        DockTab.EMOJI -> stringResource(R.string.chat_emoji_search_hint)
                                        DockTab.GIF -> "Search GIFs…"
                                        DockTab.STICKER -> stringResource(R.string.chat_sticker_search_hint)
                                    },
                                    color = colors.textSecondary.copy(alpha = 0.6f),
                                    fontSize = 14.5.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            } else {
                // Left: Search Button
                IconButton(
                    onClick = { isSearchActive = true },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(21.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Center: WhatsApp 3-Segment Stadium Pill [ 😊 | GIF | 📑 ]
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = pillBg,
                    border = BorderStroke(0.7.dp, Color(0x1F8696A0)),
                    modifier = Modifier.height(35.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Emoji Segment
                        val isEmojiSelected = selectedTab == DockTab.EMOJI
                        Box(
                            modifier = Modifier
                                .height(31.dp)
                                .width(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isEmojiSelected) pillActiveBg else Color.Transparent)
                                .clickable { selectedTab = DockTab.EMOJI },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SentimentSatisfiedAlt,
                                contentDescription = "Emoji",
                                tint = if (isEmojiSelected) {
                                    if (colors.isDark) Color.White else greenAccent
                                } else colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // 2. GIF Segment
                        val isGifSelected = selectedTab == DockTab.GIF
                        Box(
                            modifier = Modifier
                                .height(31.dp)
                                .width(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isGifSelected) pillActiveBg else Color.Transparent)
                                .clickable { selectedTab = DockTab.GIF },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "GIF",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                color = if (isGifSelected) {
                                    if (colors.isDark) Color.White else greenAccent
                                } else colors.textSecondary
                            )
                        }

                        // 3. Sticker Segment
                        val isStickerSelected = selectedTab == DockTab.STICKER
                        Box(
                            modifier = Modifier
                                .height(31.dp)
                                .width(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isStickerSelected) pillActiveBg else Color.Transparent)
                                .clickable { selectedTab = DockTab.STICKER },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_tab_sticker),
                                contentDescription = "Stickers",
                                tint = if (isStickerSelected) {
                                    if (colors.isDark) Color.White else greenAccent
                                } else colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Right Action Icon
                when (selectedTab) {
                    DockTab.EMOJI -> {
                        // Backspace with repeat hold
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .semantics {
                                    contentDescription = "Backspace"
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
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    DockTab.STICKER -> {
                        // Create / Edit Sticker Button (Pencil Icon)
                        IconButton(
                            onClick = latestOnCreateSticker,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.chat_sticker_create_title),
                                tint = colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    DockTab.GIF -> {
                        IconButton(
                            onClick = { isSearchActive = true },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search GIFs",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = colors.divider.copy(alpha = 0.35f), thickness = 0.5.dp)

        // 3. Main Content Area (Emoji Picker vs GIF Grid vs Continuous Sticker Grid)
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
                                    emojiGridColumns = 8
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

                DockTab.GIF -> {
                    // GIF Reaction View with reaction tag chips and quick search
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        val reactionTags = remember {
                            listOf("Trending", "Love", "Haha", "Sad", "Applause", "Dance", "Shock", "Cheer", "Facepalm", "Fire", "Party")
                        }
                        var activeTag by rememberSaveable { mutableStateOf("Trending") }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(reactionTags) { tag ->
                                val isTagActive = activeTag == tag
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isTagActive) greenAccent else pillBg,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { activeTag = tag }
                                ) {
                                    Text(
                                        text = tag,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isTagActive) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isTagActive) Color.White else colors.textPrimary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = pillBg,
                                    modifier = Modifier.size(54.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "GIF",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = greenAccent
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "GIFs for \"$activeTag\"",
                                    color = colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Tap search to find animated GIFs from the web",
                                    color = colors.textSecondary,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                DockTab.STICKER -> {
                    // Continuous Single-Grid Sticker Tab (matching native WhatsApp layout)
                    val filteredRecent = remember(recentStickers, searchQuery) {
                        if (searchQuery.isBlank()) recentStickers else {
                            recentStickers.filter {
                                it.body.contains(searchQuery, ignoreCase = true) ||
                                    it.senderName.contains(searchQuery, ignoreCase = true)
                            }
                        }
                    }

                    val filteredFavorites = remember(favoriteStickers, searchQuery) {
                        if (searchQuery.isBlank()) favoriteStickers else {
                            favoriteStickers.filter {
                                it.body.contains(searchQuery, ignoreCase = true) ||
                                    it.senderName.contains(searchQuery, ignoreCase = true)
                            }
                        }
                    }

                    val filteredCuppy = remember(cuppyStickers, searchQuery) {
                        if (searchQuery.isBlank()) cuppyStickers else {
                            cuppyStickers.filter {
                                it.name.contains(searchQuery, ignoreCase = true) ||
                                    it.id.contains(searchQuery, ignoreCase = true)
                            }
                        }
                    }

                    val filteredCustom = remember(customStickers, searchQuery) {
                        if (searchQuery.isBlank()) customStickers else {
                            customStickers.filter { it.label.contains(searchQuery, ignoreCase = true) }
                        }
                    }

                    // Section index anchors for jumping and bottom bar highlights
                    // Item 0 is always Create Sticker action
                    val customSectionCount = if (filteredCustom.isNotEmpty()) 1 + filteredCustom.size else 0
                    val favHeaderIndex = remember(customSectionCount, filteredRecent.size, filteredFavorites.size) {
                        if (filteredFavorites.isNotEmpty()) 1 + customSectionCount + filteredRecent.size else -1
                    }
                    val cuppyHeaderIndex = remember(customSectionCount, filteredRecent.size, filteredFavorites.size, filteredCuppy.size) {
                        if (filteredCuppy.isNotEmpty()) {
                            1 + customSectionCount + filteredRecent.size + (if (filteredFavorites.isNotEmpty()) 1 + filteredFavorites.size else 0)
                        } else -1
                    }

                    // Derived active section based on scroll offset
                    val activeStickerSection by remember {
                        derivedStateOf {
                            val firstVisible = stickerGridState.firstVisibleItemIndex
                            when {
                                cuppyHeaderIndex != -1 && firstVisible >= cuppyHeaderIndex -> StickerSubTab.CUPPY
                                favHeaderIndex != -1 && firstVisible >= favHeaderIndex -> StickerSubTab.FAVORITES
                                else -> StickerSubTab.RECENT
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            LazyVerticalGrid(
                                state = stickerGridState,
                                columns = GridCells.Fixed(5),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // 1. Tile 0: "Create sticker" green circular action button (Image 1)
                                item(span = { GridItemSpan(1) }) {
                                    CreateStickerActionTile(
                                        colors = colors,
                                        onClick = latestOnCreateSticker
                                    )
                                }

                                // 1b. "My Stickers" from Sticker Studio
                                if (filteredCustom.isNotEmpty()) {
                                    item(span = { GridItemSpan(5) }) {
                                        Text(
                                            text = CustomStickerStore.PACK_NAME,
                                            color = colors.accentGreen,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                                        )
                                    }
                                    items(filteredCustom, key = { it.id }) { custom ->
                                        StickerPickerGridItem(
                                            filePath = custom.filePath,
                                            colors = colors,
                                            isFavorite = false,
                                            onClick = { latestOnSendCustom(custom) },
                                            onLongClick = { contextStickerItem = custom }
                                        )
                                    }
                                }

                                // 2. Recent Stickers
                                items(filteredRecent, key = { it.id }) { stickerMsg ->
                                    val isFav = isStickerMatchedByFavoriteKeys(
                                        sha256 = stickerMsg.mediaSha256,
                                        localPath = stickerMsg.localMediaPath,
                                        messageId = stickerMsg.id,
                                        favoriteKeys = favoriteStickerKeys
                                    )
                                    StickerPickerGridItem(
                                        filePath = stickerMsg.localMediaPath,
                                        colors = colors,
                                        isFavorite = isFav,
                                        onClick = { latestOnStickerSelected(stickerMsg) },
                                        onLongClick = { contextStickerItem = stickerMsg }
                                    )
                                }

                                // 3. "Favorites" Section Header + Items (Image 1 & 3)
                                if (filteredFavorites.isNotEmpty()) {
                                    item(span = { GridItemSpan(5) }) {
                                        Text(
                                            text = stringResource(R.string.chat_sticker_favorites_title),
                                            color = colors.textSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 4.dp)
                                        )
                                    }

                                    items(filteredFavorites, key = { "fav_${it.id}" }) { stickerMsg ->
                                        StickerPickerGridItem(
                                            filePath = stickerMsg.localMediaPath,
                                            colors = colors,
                                            isFavorite = true,
                                            onClick = { latestOnStickerSelected(stickerMsg) },
                                            onLongClick = { contextStickerItem = stickerMsg }
                                        )
                                    }
                                }

                                // 4. "Cuppy" Built-in Section Header + Items (Image 3 & 5)
                                if (filteredCuppy.isNotEmpty()) {
                                    item(span = { GridItemSpan(5) }) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 4.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = CuppyPackManager.PACK_NAME,
                                                color = colors.textSecondary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = Icons.Rounded.MoreVert,
                                                contentDescription = null,
                                                tint = colors.textSecondary.copy(alpha = 0.6f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    items(filteredCuppy, key = { it.id }) { sticker ->
                                        StickerPickerGridItem(
                                            filePath = sticker.file.absolutePath,
                                            colors = colors,
                                            isFavorite = false,
                                            onClick = { latestOnSendBuiltin(sticker) },
                                            onLongClick = { contextStickerItem = sticker }
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = colors.divider.copy(alpha = 0.4f), thickness = 0.5.dp)

                        // 4. WhatsApp Bottom Sticker Pack Switcher Bar [ 🕒 | ⭐ | ☕ | ➕ ] (Image 1, 3, 5)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .background(barBg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Recent Tab (Clock icon)
                            val isRecentActive = activeStickerSection == StickerSubTab.RECENT
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        scope.launch { stickerGridState.animateScrollToItem(0) }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isRecentActive) (if (colors.isDark) Color(0xFF2A3942) else Color(0xFFD9DCE0)) else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Schedule,
                                        contentDescription = stringResource(R.string.chat_sticker_recent_title),
                                        tint = if (isRecentActive) {
                                            if (colors.isDark) Color.White else greenAccent
                                        } else colors.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Favorites Tab (Star icon)
                            val isFavActive = activeStickerSection == StickerSubTab.FAVORITES
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        scope.launch {
                                            if (favHeaderIndex != -1) {
                                                stickerGridState.animateScrollToItem(favHeaderIndex)
                                            } else {
                                                stickerGridState.animateScrollToItem(0)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isFavActive) (if (colors.isDark) Color(0xFF2A3942) else Color(0xFFD9DCE0)) else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isFavActive) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        contentDescription = stringResource(R.string.chat_sticker_favorites_title),
                                        tint = if (isFavActive) {
                                            if (colors.isDark) Color.White else greenAccent
                                        } else colors.textSecondary,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }

                            // Cuppy Pack Tab (Coffee Cup icon)
                            val isCuppyActive = activeStickerSection == StickerSubTab.CUPPY
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        scope.launch {
                                            if (cuppyHeaderIndex != -1) {
                                                stickerGridState.animateScrollToItem(cuppyHeaderIndex)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isCuppyActive) (if (colors.isDark) Color(0xFF2A3942) else Color(0xFFD9DCE0)) else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LocalCafe,
                                        contentDescription = CuppyPackManager.PACK_NAME,
                                        tint = if (isCuppyActive) {
                                            if (colors.isDark) Color.White else greenAccent
                                        } else colors.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Add / Create Sticker Action (Plus icon)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(onClick = latestOnCreateSticker),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.chat_sticker_create_title),
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Context Menu Dialog on Sticker Long-Press
    contextStickerItem?.let { item ->
        AlertDialog(
            onDismissRequest = { contextStickerItem = null },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { contextStickerItem = null }) {
                    Text("Close", color = greenAccent)
                }
            },
            title = {
                Text(
                    text = "Sticker Options",
                    color = colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (item is MessageEntity) {
                        val isFav = isStickerMatchedByFavoriteKeys(
                            sha256 = item.mediaSha256,
                            localPath = item.localMediaPath,
                            messageId = item.id,
                            favoriteKeys = favoriteStickerKeys
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    latestOnToggleFav(item)
                                    contextStickerItem = null
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isFav) Icons.Rounded.StarBorder else Icons.Rounded.Star,
                                contentDescription = null,
                                tint = if (isFav) colors.textSecondary else Color(0xFFFFB300),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = if (isFav) {
                                    stringResource(R.string.chat_sticker_remove_favorite)
                                } else {
                                    stringResource(R.string.chat_sticker_add_favorite)
                                },
                                color = colors.textPrimary,
                                fontSize = 15.sp
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    latestOnStickerSelected(item)
                                    contextStickerItem = null
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = null,
                                tint = greenAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = stringResource(R.string.chat_sticker_send),
                                color = colors.textPrimary,
                                fontSize = 15.sp
                            )
                        }
                    } else if (item is CustomStickerItem) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    latestOnSendCustom(item)
                                    contextStickerItem = null
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = null,
                                tint = greenAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = stringResource(R.string.chat_sticker_send),
                                color = colors.textPrimary,
                                fontSize = 15.sp
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    latestOnDeleteCustom(item)
                                    contextStickerItem = null
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = colors.antiDeleteRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Delete custom sticker",
                                color = colors.antiDeleteRed,
                                fontSize = 15.sp
                            )
                        }
                    } else if (item is BuiltinSticker) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    latestOnSendBuiltin(item)
                                    contextStickerItem = null
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = null,
                                tint = greenAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = stringResource(R.string.chat_sticker_send),
                                color = colors.textPrimary,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            },
            containerColor = colors.elevatedBar
        )
    }
}

/**
 * Green circular button tile for "Create sticker" matching native WhatsApp (Image 1).
 */
@Composable
private fun CreateStickerActionTile(
    colors: WaColors,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (colors.isDark) Color(0xFF141D22) else Color(0xFFF2F5F7))
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.accentGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.chat_sticker_create_title),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.chat_sticker_create_title),
                color = colors.accentGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                maxLines = 2
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun StickerPickerGridItem(
    filePath: String?,
    colors: WaColors,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val localFile = remember(filePath) {
        filePath?.takeIf { it.isNotBlank() }?.let { path ->
            File(path).takeIf { it.exists() && it.length() > 0L }
        }
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        if (localFile != null) {
            AnimatedStickerImage(
                file = localFile,
                contentDescription = "Sticker",
                maxDecodeDimPx = 220,
                modifier = Modifier.fillMaxSize(),
                placeholder = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.incomingBubble.copy(alpha = 0.35f))
                    )
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.incomingBubble.copy(alpha = 0.35f))
            )
        }

        if (isFavorite) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = "Favorite",
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}
