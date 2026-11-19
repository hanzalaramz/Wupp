package com.wa.beastclient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.core.service.WaRealtimeService
import com.wa.beastclient.data.db.ChatEntity
import com.wa.beastclient.ui.screens.WhatsAppAvatar
import com.wa.beastclient.ui.theme.WaColors

internal fun filterForwardTargetChats(
    allChats: List<ChatEntity>,
    announceLockedGroups: Set<String>,
    query: String
): List<ChatEntity> {
    val cleanQuery = query.trim().lowercase()
    return allChats
        .asSequence()
        .filter { chat ->
            !chat.isCommunityParent &&
                chat.jid != "status@broadcast" &&
                !chat.jid.endsWith("@newsletter") &&
                !chat.jid.endsWith("@broadcast") &&
                !(chat.isGroup && announceLockedGroups.contains(chat.jid))
        }
        .filter { chat ->
            if (cleanQuery.isBlank()) {
                true
            } else {
                chat.name.lowercase().contains(cleanQuery) ||
                    chat.jid.substringBefore("@").lowercase().contains(cleanQuery)
            }
        }
        .sortedByDescending { it.lastMessageTimestamp }
        .toList()
}

@Composable
fun ForwardMessageDialog(
    allChats: List<ChatEntity>,
    announceLockedGroups: Set<String>,
    currentChatJid: String,
    colors: WaColors,
    onDismiss: () -> Unit,
    onConfirmForward: (List<String>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val selectedJids = remember { mutableStateListOf<String>() }
    val filteredChats = remember(allChats, announceLockedGroups, searchQuery) {
        filterForwardTargetChats(allChats, announceLockedGroups, searchQuery)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Forward to…",
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${selectedJids.size}/${WaRealtimeService.MAX_FORWARD_RECIPIENTS}",
                    color = if (selectedJids.isNotEmpty()) colors.accentGreen else colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search chats or groups", fontSize = 13.5.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (filteredChats.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching chats",
                            color = colors.textSecondary,
                            fontSize = 13.5.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filteredChats, key = { it.jid }) { chat ->
                            val isSelected = selectedJids.contains(chat.jid)
                            val displayTitle = chat.name.ifBlank { chat.jid.substringBefore("@") }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) colors.accentGreen.copy(alpha = 0.12f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        if (isSelected) {
                                            selectedJids.remove(chat.jid)
                                        } else if (selectedJids.size < WaRealtimeService.MAX_FORWARD_RECIPIENTS) {
                                            selectedJids.add(chat.jid)
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                WhatsAppAvatar(
                                    jid = chat.jid,
                                    avatarPath = chat.avatarPath,
                                    isGroup = chat.isGroup,
                                    size = 38.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayTitle,
                                        color = colors.textPrimary,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sub = when {
                                        chat.jid == currentChatJid -> "Current chat"
                                        chat.isGroup -> "Group"
                                        else -> "Chat"
                                    }
                                    Text(
                                        text = sub,
                                        color = colors.textSecondary,
                                        fontSize = 11.5.sp,
                                        maxLines = 1
                                    )
                                }
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(colors.accentGreen),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmForward(selectedJids.toList()) },
                enabled = selectedJids.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen)
            ) {
                Text(
                    text = if (selectedJids.isEmpty()) "Send" else "Send (${selectedJids.size})",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        }
    )
}
