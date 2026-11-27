package com.wa.beastclient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.data.db.ChatEntity
import com.wa.beastclient.domain.stealth.EffectiveChatPrivacy
import com.wa.beastclient.domain.stealth.GlobalStealthConfig
import com.wa.beastclient.domain.stealth.StealthMatrixManager
import com.wa.beastclient.ui.screens.WhatsAppAvatar
import com.wa.beastclient.ui.theme.LocalWaColors

/**
 * Unified Watusi-style Per-Chat Quick Controls sheet organizing receipts, typing visibility,
 * pin/mute/favorite/lock toggles, gallery visibility, wallpaper, and saved chat history in one place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerChatQuickControlsSheet(
    chat: ChatEntity?,
    chatJid: String,
    effectivePrivacy: EffectiveChatPrivacy?,
    stealthConfig: GlobalStealthConfig,
    savedHistoryCount: Int = 0,
    onSavePrivacyOverride: (sendRead: Boolean, sendPlayed: Boolean, sendTyping: Boolean) -> Unit,
    onClearPrivacyOverride: () -> Unit,
    onSetGalleryVisibility: (Boolean?) -> Unit,
    onTogglePin: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleLock: () -> Unit,
    onOpenWallpaper: (() -> Unit)? = null,
    onOpenChatHistory: (() -> Unit)? = null,
    onSetHideChatNames: ((Boolean) -> Unit)? = null,
    onSetHideChatDps: ((Boolean) -> Unit)? = null,
    onSetHideChatNamesAndDps: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val colors = LocalWaColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sendRead by remember(effectivePrivacy) { mutableStateOf(effectivePrivacy?.sendReadReceipts ?: false) }
    var sendPlayed by remember(effectivePrivacy) { mutableStateOf(effectivePrivacy?.sendPlayedReceipts ?: false) }
    var sendTyping by remember(effectivePrivacy) { mutableStateOf(effectivePrivacy?.sendChatPresence ?: false) }

    val displayName = chat?.name?.ifBlank { chatJid.substringBefore("@") } ?: chatJid.substringBefore("@")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.elevatedBar
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Contact Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                WhatsAppAvatar(
                    jid = chatJid,
                    avatarPath = chat?.avatarPath,
                    isGroup = chat?.isGroup == true,
                    size = 44.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        fontSize = 16.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (effectivePrivacy?.hasCustomOverride == true) {
                            "Quick Controls · Custom privacy active"
                        } else {
                            "Quick Controls · Using global stealth defaults"
                        },
                        color = if (effectivePrivacy?.hasCustomOverride == true) colors.accentGreen else colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            // One-tap Thread Action Chips
            if (chat != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickControlTile(
                        icon = Icons.Rounded.PushPin,
                        label = if (chat.isPinned) "Pinned" else "Pin",
                        active = chat.isPinned,
                        onClick = onTogglePin,
                        modifier = Modifier.weight(1f)
                    )
                    QuickControlTile(
                        icon = if (chat.isMuted) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                        label = if (chat.isMuted) "Muted" else "Mute",
                        active = chat.isMuted,
                        onClick = onToggleMute,
                        modifier = Modifier.weight(1f)
                    )
                    QuickControlTile(
                        icon = if (chat.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        label = if (chat.isFavorite) "Starred" else "Fav",
                        active = chat.isFavorite,
                        onClick = onToggleFavorite,
                        modifier = Modifier.weight(1f)
                    )
                    QuickControlTile(
                        icon = if (chat.isLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        label = if (chat.isLocked) "Locked" else "Lock",
                        active = chat.isLocked,
                        onClick = onToggleLock,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(color = colors.divider)

            // Per-Chat Privacy Toggles
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Shield, null, tint = colors.accentGreen, modifier = Modifier.size(16.dp))
                Text(
                    text = "Privacy for This Chat",
                    color = colors.textSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Send Read Receipts (Blue Ticks)", color = colors.textPrimary, fontSize = 14.sp)
                Switch(
                    checked = sendRead,
                    onCheckedChange = { sendRead = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.accentGreen, checkedThumbColor = Color.White)
                )
            }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Send Audio Played Receipts", color = colors.textPrimary, fontSize = 14.sp)
                Switch(
                    checked = sendPlayed,
                    onCheckedChange = { sendPlayed = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.accentGreen, checkedThumbColor = Color.White)
                )
            }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Send Live Typing & Recording", color = colors.textPrimary, fontSize = 14.sp)
                Switch(
                    checked = sendTyping,
                    onCheckedChange = { sendTyping = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = colors.accentGreen, checkedThumbColor = Color.White)
                )
            }

            if (onSetHideChatNamesAndDps != null || onSetHideChatNames != null || onSetHideChatDps != null) {
                HorizontalDivider(color = colors.divider)
                Text(
                    text = "Chat Screen Privacy",
                    color = colors.textSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (onSetHideChatNamesAndDps != null) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Hide Names & DPs in Chats", color = colors.textPrimary, fontSize = 14.sp)
                        Switch(
                            checked = stealthConfig.hideChatContactNames && stealthConfig.hideChatProfilePictures,
                            onCheckedChange = onSetHideChatNamesAndDps,
                            colors = SwitchDefaults.colors(checkedTrackColor = colors.accentGreen, checkedThumbColor = Color.White)
                        )
                    }
                }
                if (onSetHideChatNames != null) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Hide Contact Names", color = colors.textPrimary, fontSize = 14.sp)
                        Switch(
                            checked = stealthConfig.hideChatContactNames,
                            onCheckedChange = onSetHideChatNames,
                            colors = SwitchDefaults.colors(checkedTrackColor = colors.accentGreen, checkedThumbColor = Color.White)
                        )
                    }
                }
                if (onSetHideChatDps != null) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Hide Profile Pictures (DPs)", color = colors.textPrimary, fontSize = 14.sp)
                        Switch(
                            checked = stealthConfig.hideChatProfilePictures,
                            onCheckedChange = onSetHideChatDps,
                            colors = SwitchDefaults.colors(checkedTrackColor = colors.accentGreen, checkedThumbColor = Color.White)
                        )
                    }
                }
            }

            HorizontalDivider(color = colors.divider)

            ChatMediaVisibilitySelector(
                selectedOverride = StealthMatrixManager.getChatGalleryOverride(chatJid, stealthConfig),
                globalDefaultVisible = stealthConfig.mediaVisibilityInGallery,
                onSelectOverride = onSetGalleryVisibility
            )

            // Shortcuts to Saved Chat History & Per-Chat Wallpaper
            if (onOpenWallpaper != null || onOpenChatHistory != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onOpenWallpaper != null) {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onOpenWallpaper()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Wallpaper, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Wallpaper", fontSize = 12.5.sp)
                        }
                    }
                    if (onOpenChatHistory != null) {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onOpenChatHistory()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.History, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Saved ($savedHistoryCount)", fontSize = 12.5.sp)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onClearPrivacyOverride()
                        onSetGalleryVisibility(null)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Use Global Default", fontSize = 13.sp)
                }
                Button(
                    onClick = {
                        onSavePrivacyOverride(sendRead, sendPlayed, sendTyping)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Controls", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun QuickControlTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaColors.current
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (active) colors.pillActiveBg else colors.appSurface
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) colors.accentGreen else colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                color = if (active) colors.pillActiveText else colors.textPrimary,
                fontSize = 11.5.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
