package com.wa.beastclient.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.data.db.ChatEntity
import com.wa.beastclient.data.db.MessageEditEntity
import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.ui.theme.LocalWaColors
import com.wa.beastclient.ui.theme.LocalWaMonoFamily
import com.wa.beastclient.ui.theme.LocalWuppShowSeconds
import com.wa.beastclient.ui.theme.LocalWuppUse24Hour
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MessageHistoryFilter(val label: String) {
    ALL("All"),
    DELETED("Deleted"),
    EDITED("Edited")
}

internal data class HistoryEventEntry(
    val message: MessageEntity,
    val edits: List<MessageEditEntity>,
    val eventTimestampMs: Long
)

internal fun buildMessageHistoryEntries(
    deletedMessages: List<MessageEntity>,
    editedMessages: List<MessageEntity>,
    editsByMessageId: Map<String, List<MessageEditEntity>>,
    filter: MessageHistoryFilter,
    query: String,
    chatNamesByJid: Map<String, String>,
    chatJidFilter: String? = null
): List<HistoryEventEntry> {
    val combinedById = LinkedHashMap<String, MessageEntity>()
    if (filter != MessageHistoryFilter.EDITED) {
        deletedMessages.forEach { combinedById[it.id] = it }
    }
    if (filter != MessageHistoryFilter.DELETED) {
        editedMessages.forEach { combinedById.putIfAbsent(it.id, it) }
    }
    val needle = query.trim().lowercase()
    return combinedById.values
        .asSequence()
        .filter { msg -> chatJidFilter.isNullOrBlank() || msg.chatJid == chatJidFilter }
        .map { msg ->
            val edits = editsByMessageId[msg.id].orEmpty().sortedByDescending { it.editedAt }
            val eventTs = maxOf(msg.deletedAt, msg.editedAt, msg.timestamp)
            HistoryEventEntry(msg, edits, eventTs)
        }
        .filter { entry ->
            if (needle.isEmpty()) return@filter true
            val msg = entry.message
            val chatTitle = chatNamesByJid[msg.chatJid].orEmpty()
            msg.body.contains(needle, ignoreCase = true) ||
                msg.senderName.contains(needle, ignoreCase = true) ||
                chatTitle.contains(needle, ignoreCase = true) ||
                entry.edits.any { it.oldText.contains(needle, ignoreCase = true) }
        }
        .sortedByDescending { it.eventTimestampMs }
        .toList()
}

private fun formatHistoryTimestamp(epochMs: Long, includeSeconds: Boolean, use24Hour: Boolean): String {
    if (epochMs <= 0L) return ""
    val pattern = when {
        use24Hour && includeSeconds -> "MMM d, HH:mm:ss"
        use24Hour -> "MMM d, HH:mm"
        includeSeconds -> "MMM d, h:mm:ss a"
        else -> "MMM d, h:mm a"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMs))
}

/**
 * Searchable timeline of locally recovered deleted messages and multi-revision edit histories.
 * Runs 100% on local Room data with zero WhatsApp server traffic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageHistorySheet(
    deletedMessages: List<MessageEntity>,
    editedMessages: List<MessageEntity>,
    allEdits: List<MessageEditEntity>,
    chats: List<ChatEntity>,
    initialChatJid: String? = null,
    initialFilter: MessageHistoryFilter = MessageHistoryFilter.ALL,
    onJumpToChat: (String) -> Unit,
    onDeleteEntry: (MessageEntity) -> Unit = {},
    onClearAll: (scopedChatJid: String?) -> Unit = {},
    onDismiss: () -> Unit
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    val showSeconds = LocalWuppShowSeconds.current
    val use24Hour = LocalWuppUse24Hour.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var filter by remember(initialFilter) { mutableStateOf(initialFilter) }
    var searchQuery by remember { mutableStateOf("") }
    var scopedChatJid by remember(initialChatJid) { mutableStateOf(initialChatJid) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val expandedEditCardIds = remember { mutableStateMapOf<String, Boolean>() }

    val chatNamesByJid = remember(chats) {
        chats.associate { it.jid to it.name.ifBlank { it.jid.substringBefore("@") } }
    }
    val editsByMessageId = remember(allEdits) {
        allEdits.groupBy { it.messageId }
    }
    val entries = remember(deletedMessages, editedMessages, editsByMessageId, filter, searchQuery, chatNamesByJid, scopedChatJid) {
        buildMessageHistoryEntries(
            deletedMessages = deletedMessages,
            editedMessages = editedMessages,
            editsByMessageId = editsByMessageId,
            filter = filter,
            query = searchQuery,
            chatNamesByJid = chatNamesByJid,
            chatJidFilter = scopedChatJid
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.elevatedBar
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.History, null, tint = colors.accentGreen, modifier = Modifier.size(22.dp))
                    Column {
                        Text(
                            text = "Message History",
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Deleted & edited messages saved on your phone",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entries.isNotEmpty()) {
                        TextButton(onClick = { showClearConfirm = true }) {
                            Icon(
                                Icons.Rounded.DeleteSweep,
                                contentDescription = "Clear history",
                                tint = colors.antiDeleteRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Clear",
                                color = colors.antiDeleteRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, "Close", tint = colors.textSecondary)
                    }
                }
            }

            if (!scopedChatJid.isNullOrBlank()) {
                val scopedTitle = chatNamesByJid[scopedChatJid] ?: scopedChatJid!!.substringBefore("@")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.pillActiveBg)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Showing only: $scopedTitle",
                        color = colors.pillActiveText,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { scopedChatJid = null }) {
                        Text("Show All Chats", color = colors.accentGreen, fontSize = 12.sp)
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = colors.textSecondary) },
                placeholder = { Text("Search deleted, edited, or sender…", fontSize = 13.5.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentGreen,
                    unfocusedBorderColor = colors.divider,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MessageHistoryFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = {
                            val count = when (option) {
                                MessageHistoryFilter.ALL -> entries.size
                                MessageHistoryFilter.DELETED -> deletedMessages.count { scopedChatJid == null || it.chatJid == scopedChatJid }
                                MessageHistoryFilter.EDITED -> editedMessages.count { scopedChatJid == null || it.chatJid == scopedChatJid }
                            }
                            Text("${option.label} ($count)", fontSize = 12.sp)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.pillActiveBg,
                            selectedLabelColor = colors.pillActiveText
                        )
                    )
                }
            }

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.History, null, tint = colors.textSecondary.copy(alpha = 0.45f), modifier = Modifier.size(44.dp))
                        Text("No recovered deleted or edited messages", color = colors.textSecondary, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(entries, key = { it.message.id }) { entry ->
                        val msg = entry.message
                        val chatTitle = chatNamesByJid[msg.chatJid] ?: msg.chatJid.substringBefore("@")
                        val senderLabel = when {
                            msg.isFromMe -> "You"
                            msg.senderName.isNotBlank() -> msg.senderName
                            else -> msg.senderJid.substringBefore("@")
                        }
                        val isExpanded = expandedEditCardIds[msg.id] ?: false
                        val thumb by produceState<ImageBitmap?>(initialValue = null, msg.localMediaPath) {
                            value = withContext(Dispatchers.IO) {
                                val path = msg.localMediaPath ?: return@withContext null
                                val f = File(path)
                                if (!f.exists() || f.length() <= 0L) return@withContext null
                                BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = colors.appSurface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = chatTitle,
                                            color = colors.textPrimary,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "$senderLabel · ${formatHistoryTimestamp(entry.eventTimestampMs, showSeconds, use24Hour)}",
                                            color = colors.textSecondary,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (msg.isDeletedBySender) {
                                            HistoryStatusPill("Deleted", colors.antiDeleteRed)
                                        }
                                        if (msg.isEdited) {
                                            HistoryStatusPill("Edited", colors.accentGreen)
                                        }
                                    }
                                }

                                if (thumb != null) {
                                    Image(
                                        bitmap = thumb!!,
                                        contentDescription = "Preserved media",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                }

                                if (msg.body.isNotBlank()) {
                                    Text(
                                        text = msg.body,
                                        color = colors.textPrimary,
                                        fontSize = 14.sp,
                                        lineHeight = 19.sp
                                    )
                                } else if (thumb == null) {
                                    Text(
                                        text = "[${msg.msgType} media preserved]",
                                        color = colors.textSecondary,
                                        fontSize = 13.sp
                                    )
                                }

                                if (entry.edits.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(colors.pillInactiveBg)
                                            .clickable { expandedEditCardIds[msg.id] = !isExpanded }
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Rounded.Edit, null, tint = colors.accentGreen, modifier = Modifier.size(15.dp))
                                            Text(
                                                text = "${entry.edits.size} earlier version(s)",
                                                color = colors.textPrimary,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                            contentDescription = null,
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(colors.elevatedBar)
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            entry.edits.forEachIndexed { idx, rev ->
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(
                                                        text = "Revision #${entry.edits.size - idx} · ${formatHistoryTimestamp(rev.editedAt, showSeconds, use24Hour)}",
                                                        color = colors.textSecondary,
                                                        fontSize = 11.sp,
                                                        fontFamily = mono
                                                    )
                                                    Text(
                                                        text = rev.oldText,
                                                        color = colors.textPrimary,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                                if (idx < entry.edits.lastIndex) {
                                                    HorizontalDivider(color = colors.divider.copy(alpha = 0.5f))
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { onDeleteEntry(msg) }) {
                                        Icon(
                                            Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete history entry",
                                            modifier = Modifier.size(16.dp),
                                            tint = colors.antiDeleteRed
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Delete",
                                            color = colors.antiDeleteRed,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            onDismiss()
                                            onJumpToChat(msg.chatJid)
                                        }
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp), tint = colors.accentGreen)
                                        Spacer(Modifier.width(5.dp))
                                        Text("Show in Chat", color = colors.accentGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = colors.elevatedBar,
            title = {
                Text(
                    text = if (scopedChatJid.isNullOrBlank()) "Clear Message History?" else "Clear Chat History?",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "This removes saved deleted and edited message records from this device to free storage.",
                    color = colors.textSecondary,
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        onClearAll(scopedChatJid)
                    }
                ) {
                    Text("Clear All", color = colors.antiDeleteRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun HistoryStatusPill(label: String, tint: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.16f)
    ) {
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
