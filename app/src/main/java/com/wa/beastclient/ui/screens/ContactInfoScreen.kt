package com.wa.beastclient.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PhotoSizeSelectActual
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.data.db.WaUiDatabase
import com.wa.beastclient.domain.stealth.StealthMatrixManager
import com.wa.beastclient.ui.MainViewModel
import com.wa.beastclient.ui.ResolvedParticipantUi
import com.wa.beastclient.ui.components.ChatMediaVisibilitySelector
import com.wa.beastclient.ui.components.FullScreenMediaViewer
import com.wa.beastclient.ui.components.ProfilePhotoLightbox
import com.wa.beastclient.ui.components.WallpaperPickerDialog
import com.wa.beastclient.ui.theme.LocalWaColors
import java.io.File
import java.util.Locale

private data class MemberActionTarget(
    val participantJid: String,
    val chatJid: String,
    val displayName: String,
    val phone: String,
    val avatarPath: String?
)

private fun stripMemberJidDevice(rawJid: String): String {
    val trimmed = rawJid.trim()
    val atIdx = trimmed.indexOf('@')
    if (atIdx <= 0) return trimmed
    val user = trimmed.substring(0, atIdx).substringBefore(':')
    val server = trimmed.substring(atIdx + 1)
    return if (user.isNotBlank() && server.isNotBlank()) "$user@$server" else trimmed
}

internal fun resolveMemberChatJid(
    participantJid: String,
    meta: ResolvedParticipantUi?
): String {
    val cleanPrimary = stripMemberJidDevice(participantJid)
    if (cleanPrimary.endsWith("@s.whatsapp.net", ignoreCase = true)) {
        return cleanPrimary
    }
    val cleanAlt = stripMemberJidDevice(meta?.altJid.orEmpty())
    if (cleanAlt.endsWith("@s.whatsapp.net", ignoreCase = true)) {
        return cleanAlt
    }
    val digits = meta?.phone.orEmpty().filter(Char::isDigit)
    if (digits.length >= 7) {
        return "$digits@s.whatsapp.net"
    }
    return cleanPrimary.ifBlank { participantJid.trim() }
}

internal fun resolveMemberDisplayPhone(
    participantJid: String,
    meta: ResolvedParticipantUi?
): String {
    val explicitPhone = meta?.phone?.trim().orEmpty()
    if (explicitPhone.isNotBlank()) {
        return explicitPhone
    }
    val cleanPrimary = stripMemberJidDevice(participantJid)
    if (cleanPrimary.endsWith("@s.whatsapp.net", ignoreCase = true)) {
        val user = cleanPrimary.substringBefore("@")
        if (user.isNotBlank()) return "+$user"
    }
    val cleanAlt = stripMemberJidDevice(meta?.altJid.orEmpty())
    if (cleanAlt.endsWith("@s.whatsapp.net", ignoreCase = true)) {
        val user = cleanAlt.substringBefore("@")
        if (user.isNotBlank()) return "+$user"
    }
    return ""
}

internal fun isGroupMemberAdmin(
    participantJid: String,
    resolvedChatJid: String,
    meta: ResolvedParticipantUi?,
    groupAdmins: Set<String>
): Boolean {
    if (groupAdmins.isEmpty()) return false
    val candidates = buildList {
        add(participantJid)
        add(resolvedChatJid)
        meta?.altJid?.takeIf { it.isNotBlank() }?.let(::add)
    }.flatMap { jid ->
        val clean = stripMemberJidDevice(jid)
        val user = clean.substringBefore("@")
        listOfNotNull(jid.takeIf { it.isNotBlank() }, clean.takeIf { it.isNotBlank() }, user.takeIf { it.isNotBlank() })
    }
    return candidates.any { it in groupAdmins }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactInfoScreen(
    chatJid: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val colors = LocalWaColors.current
    val dao = remember { WaUiDatabase.getInstance(context).waDao() }

    val chat by viewModel.observeChat(chatJid).collectAsState(initial = null)
    val allContacts by viewModel.allContacts.collectAsState()
    val contact = remember(chatJid, allContacts) { allContacts.firstOrNull { it.jid == chatJid } }
    val stealthConfig by viewModel.stealthConfig.collectAsState()
    val galleryMedia by viewModel.observeChatGalleryMedia(chatJid).collectAsState(initial = emptyList())

    val isGroup = remember(chatJid, chat) { chat?.isGroup == true || chatJid.endsWith("@g.us") }
    val isChannel = remember(chatJid) { chatJid.endsWith("@newsletter") }

    val chatTitle = remember(chat?.name, contact?.name, chatJid) {
        chat?.name?.ifBlank { contact?.name }.orEmpty().ifBlank { chatJid.substringBefore("@") }
    }
    val effectiveAbout = remember(chat?.aboutText, contact?.aboutText) {
        chat?.aboutText?.takeIf { it.isNotBlank() }
            ?: contact?.aboutText?.takeIf { it.isNotBlank() }.orEmpty()
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showProfilePhotoLightbox by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showGhostPrivacySheet by remember { mutableStateOf(false) }
    var showMediaVisibilityDialog by remember { mutableStateOf(false) }
    var mediaViewerTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var activeMemberActionTarget by remember { mutableStateOf<MemberActionTarget?>(null) }

    // Group participants & admin resolution
    var groupParticipants by remember { mutableStateOf<List<String>>(emptyList()) }
    var groupAdmins by remember { mutableStateOf<Set<String>>(emptySet()) }
    var memberSearchQuery by remember { mutableStateOf("") }
    var isMemberSearchActive by remember { mutableStateOf(false) }
    val participantMeta by viewModel.participantMeta.collectAsState()

    val otherParticipants = remember(groupParticipants, participantMeta) {
        groupParticipants
            .filter { participantMeta[it]?.isSelf != true }
            .distinctBy { resolveMemberChatJid(it, participantMeta[it]) }
    }
    val totalMembersCount = remember(otherParticipants) {
        (1 + otherParticipants.size).coerceAtLeast(1)
    }

    val filteredMembers = remember(otherParticipants, memberSearchQuery, participantMeta) {
        if (memberSearchQuery.isBlank()) {
            otherParticipants
        } else {
            val q = memberSearchQuery.lowercase().trim()
            otherParticipants.filter { jid ->
                val meta = participantMeta[jid]
                val resolvedChatJid = resolveMemberChatJid(jid, meta)
                val name = meta?.savedName?.ifBlank { meta.pushName }.orEmpty().lowercase()
                val phone = resolveMemberDisplayPhone(jid, meta).lowercase()
                jid.lowercase().contains(q) ||
                    resolvedChatJid.lowercase().contains(q) ||
                    name.contains(q) ||
                    phone.contains(q)
            }
        }
    }

    val mediaVisibilityOverride = remember(stealthConfig, chatJid) {
        StealthMatrixManager.getChatGalleryOverride(chatJid, stealthConfig)
    }
    val mediaVisibilityLabel = when (mediaVisibilityOverride) {
        true -> "Yes"
        false -> "No"
        else -> "Default (${if (stealthConfig.mediaVisibilityInGallery) "Yes" else "No"})"
    }

    val effectiveWallpaper = remember(stealthConfig, chatJid) {
        StealthMatrixManager.resolveChatWallpaper(chatJid, cfg = stealthConfig)
    }
    val hasWallpaperOverride = remember(stealthConfig, chatJid) {
        StealthMatrixManager.hasChatWallpaperOverride(chatJid, cfg = stealthConfig)
    }

    val effectivePrivacy by viewModel.observeEffectivePrivacy(chatJid)
        .collectAsState(
            initial = com.wa.beastclient.domain.stealth.EffectiveChatPrivacy(
                sendReadReceipts = !stealthConfig.hideReadReceipts,
                sendPlayedReceipts = !stealthConfig.hidePlayedReceipts,
                sendChatPresence = !stealthConfig.hideTypingRecording,
                hasCustomOverride = false
            )
        )

    LaunchedEffect(chatJid) {
        if (isGroup) {
            val list = viewModel.fetchGroupParticipants(chatJid)
            groupParticipants = list
            groupAdmins = viewModel.fetchGroupAdmins(chatJid)
        }
        viewModel.requestFullProfilePicture(chatJid)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.appSurface,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textPrimary,
                    actionIconContentColor = colors.textPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WhatsAppAvatar(
                            jid = chatJid,
                            avatarPath = chat?.avatarPath,
                            isGroup = isGroup,
                            size = 34.dp
                        )
                        Text(
                            text = chatTitle,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Rounded.Share, null) },
                            text = { Text("Share") },
                            onClick = {
                                showOverflowMenu = false
                                viewModel.exportChatToText(chatJid, context)
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Rounded.Wallpaper, null) },
                            text = { Text("Wallpaper & sound") },
                            onClick = {
                                showOverflowMenu = false
                                showWallpaperDialog = true
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Rounded.Security, null) },
                            text = { Text("Privacy overrides") },
                            onClick = {
                                showOverflowMenu = false
                                showGhostPrivacySheet = true
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Rounded.Clear, null, tint = colors.antiDeleteRed) },
                            text = { Text("Clear chat", color = colors.antiDeleteRed) },
                            onClick = {
                                showOverflowMenu = false
                                showClearChatDialog = true
                            }
                        )
                    }
                }
            )
        },
        containerColor = colors.chatCanvas
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Hero section: Large DP, Name, Subtitle, Quick action pills
            item(key = "hero_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.appSurface)
                        .padding(top = 16.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(116.dp)
                            .clip(CircleShape)
                            .clickable { showProfilePhotoLightbox = true }
                    ) {
                        WhatsAppAvatar(
                            jid = chatJid,
                            avatarPath = chat?.avatarPath,
                            isGroup = isGroup,
                            size = 116.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = chatTitle,
                        color = colors.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val subtitleText = when {
                        isGroup -> "Group · ${if (groupParticipants.isNotEmpty()) groupParticipants.size else 1} members"
                        isChannel -> "Public Channel"
                        chat?.isOnline == true -> "Online"
                        effectiveAbout.isNotBlank() -> effectiveAbout
                        else -> chatJid.substringBefore("@")
                    }
                    Text(
                        text = subtitleText,
                        color = if (chat?.isOnline == true && !isGroup && !isChannel) colors.brightGreen else colors.textSecondary,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Action buttons: [Message] [Audio/Voice chat] [Video]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InfoActionPill(
                            icon = Icons.AutoMirrored.Rounded.Chat,
                            label = "Message",
                            onClick = { onOpenChat(chatJid) }
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        if (isGroup) {
                            InfoActionPill(
                                icon = Icons.Rounded.GraphicEq,
                                label = "Voice chat",
                                onClick = {
                                    Toast.makeText(context, "Voice chat requires official WhatsApp app", Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            InfoActionPill(
                                icon = Icons.Rounded.Call,
                                label = "Audio",
                                onClick = {
                                    Toast.makeText(context, "Calls require official WhatsApp app", Toast.LENGTH_SHORT).show()
                                }
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            InfoActionPill(
                                icon = Icons.Rounded.Videocam,
                                label = "Video",
                                onClick = {
                                    Toast.makeText(context, "Calls require official WhatsApp app", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }

            // Tabs Header: Members / Overview, Media, Settings
            item(key = "tab_row") {
                val tabTitles = if (isGroup) listOf("Members", "Media", "Settings") else listOf("Overview", "Media", "Settings")
                SecondaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = colors.appSurface,
                    contentColor = colors.accentGreen,
                    indicator = {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(selectedTab),
                            color = colors.accentGreen,
                            height = 3.dp
                        )
                    }
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 14.5.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTab == index) colors.accentGreen else colors.textSecondary
                                )
                            }
                        )
                    }
                }
                HorizontalDivider(color = colors.divider, thickness = 0.8.dp)
            }

            // Tab Content
            when (selectedTab) {
                0 -> {
                    if (isGroup) {
                        // Group Members Tab (Image 2)
                        item(key = "members_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.appSurface)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$totalMembersCount members",
                                    color = colors.textSecondary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                IconButton(
                                    onClick = { isMemberSearchActive = !isMemberSearchActive },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isMemberSearchActive) Icons.Rounded.Clear else Icons.Rounded.Search,
                                        contentDescription = "Search members",
                                        tint = colors.textSecondary
                                    )
                                }
                            }
                            if (isMemberSearchActive) {
                                OutlinedTextField(
                                    value = memberSearchQuery,
                                    onValueChange = { memberSearchQuery = it },
                                    placeholder = { Text("Search members...", fontSize = 13.5.sp) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(colors.appSurface)
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.accentGreen,
                                        unfocusedBorderColor = colors.divider,
                                        focusedTextColor = colors.textPrimary,
                                        unfocusedTextColor = colors.textPrimary
                                    )
                                )
                            }
                        }

                        // "You" row
                        item(key = "member_you") {
                            val ownPhone = remember(chatJid) {
                                runCatching { com.wa.beastclient.core.service.WaRealtimeService.bridgeStatus.value.ownJid.substringBefore("@") }.getOrDefault("")
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.appSurface)
                                    .clickable {
                                        Toast.makeText(context, "This is your account", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(colors.accentGreen.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "You",
                                        color = colors.accentGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "You",
                                        color = colors.textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (ownPhone.isNotBlank()) "+$ownPhone" else "Add member tag",
                                        color = colors.brightGreen,
                                        fontSize = 12.5.sp
                                    )
                                }
                                if (groupAdmins.contains("me") || groupAdmins.any { it.contains("me@") }) {
                                    AdminBadge(colors.accentGreen, colors.brightGreen)
                                }
                            }
                        }

                        // List of other group participants
                        items(filteredMembers, key = { it }) { participantJid ->
                            val meta = participantMeta[participantJid]
                            val resolvedChatJid = resolveMemberChatJid(participantJid, meta)
                            val pPhone = resolveMemberDisplayPhone(participantJid, meta)
                            val pName = meta?.savedName?.ifBlank { meta.pushName }?.takeIf { it.isNotBlank() }
                                ?: pPhone.ifBlank { participantJid.substringBefore("@") }
                            val isAdmin = isGroupMemberAdmin(participantJid, resolvedChatJid, meta, groupAdmins)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.appSurface)
                                    .clickable {
                                        activeMemberActionTarget = MemberActionTarget(
                                            participantJid = participantJid,
                                            chatJid = resolvedChatJid,
                                            displayName = pName,
                                            phone = pPhone,
                                            avatarPath = meta?.avatarPath
                                        )
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                WhatsAppAvatar(
                                    jid = resolvedChatJid,
                                    avatarPath = meta?.avatarPath,
                                    isGroup = false,
                                    size = 44.dp
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pName,
                                        color = colors.textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = pPhone.ifBlank { "Private WhatsApp ID" },
                                        color = colors.textSecondary,
                                        fontSize = 12.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isAdmin) {
                                    AdminBadge(colors.accentGreen, colors.brightGreen)
                                }
                            }
                        }

                        item(key = "members_footer") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.appSurface)
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Text(
                                    text = "See all ($totalMembersCount members)",
                                    color = colors.brightGreen,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clickable {
                                            isMemberSearchActive = true
                                        }
                                        .padding(vertical = 6.dp)
                                )
                                Text(
                                    text = "View member changes",
                                    color = colors.brightGreen,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clickable {
                                            Toast.makeText(context, "No recent participant changes", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(vertical = 6.dp)
                                )
                            }
                        }
                    } else {
                        // 1:1 Contact Overview Tab
                        item(key = "dm_overview_about") {
                            SettingsCard(
                                title = "About",
                                subtitle = effectiveAbout.ifBlank { "Hey there! I am using WhatsApp." },
                                icon = Icons.AutoMirrored.Rounded.Chat,
                                colors = colors,
                                onClick = {}
                            )
                        }

                        item(key = "dm_overview_phone") {
                            val phone = chatJid.substringBefore("@")
                            SettingsCard(
                                title = "Phone number",
                                subtitle = "+$phone",
                                icon = Icons.Rounded.Call,
                                colors = colors,
                                trailing = {
                                    IconButton(onClick = {
                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cb.setPrimaryClip(ClipData.newPlainText("phone", "+$phone"))
                                        Toast.makeText(context, "Phone number copied", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Rounded.ContentCopy, "Copy", tint = colors.accentGreen)
                                    }
                                },
                                onClick = {}
                            )
                        }

                        item(key = "dm_overview_stealth") {
                            SettingsCard(
                                title = "Stealth & Ghost privacy",
                                subtitle = "Read Receipts: ${if (effectivePrivacy.sendReadReceipts) "On" else "Off (Ghost)"} · Audio Played: ${if (effectivePrivacy.sendPlayedReceipts) "On" else "Off"} · Live Typing: ${if (effectivePrivacy.sendChatPresence) "On" else "Off"}",
                                icon = Icons.Rounded.Shield,
                                colors = colors,
                                onClick = { showGhostPrivacySheet = true }
                            )
                        }
                    }
                }

                1 -> {
                    // Media Tab (Image 4)
                    item(key = "media_section_card") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.appSurface)
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Media, links, and docs",
                                    color = colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${galleryMedia.size} >",
                                    color = colors.textSecondary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clickable {
                                        if (galleryMedia.isNotEmpty()) {
                                            mediaViewerTarget = galleryMedia.last()
                                        } else {
                                            Toast.makeText(context, "No media in this chat", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (galleryMedia.isEmpty()) {
                                Text(
                                    text = "No media, links, or documents shared in this chat yet.",
                                    color = colors.textSecondary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    galleryMedia.takeLast(16).reversed().forEach { msg ->
                                        val path = msg.localMediaPath
                                        val bmp = remember(path) {
                                            path?.let { p ->
                                                val f = File(p)
                                                if (f.exists() && f.length() > 0) {
                                                    BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
                                                } else null
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(colors.pillInactiveBg)
                                                .clickable { mediaViewerTarget = msg },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (bmp != null) {
                                                Image(
                                                    bitmap = bmp,
                                                    contentDescription = "Media thumbnail",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = if (msg.msgType == "VIDEO") Icons.Rounded.Videocam else Icons.Rounded.PhotoSizeSelectActual,
                                                    contentDescription = null,
                                                    tint = colors.textSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "media_manage_storage") {
                        val totalBytes = remember(galleryMedia) {
                            galleryMedia.sumOf { m ->
                                m.localMediaPath?.let { p -> File(p).takeIf { it.exists() }?.length() ?: 0L } ?: 0L
                            }
                        }
                        val formattedSize = remember(totalBytes) {
                            val mb = totalBytes / (1024.0 * 1024.0)
                            if (mb >= 0.1) String.format(Locale.US, "%.1f MB", mb) else "${totalBytes / 1024} KB"
                        }

                        SettingsCard(
                            title = "Manage storage",
                            subtitle = formattedSize,
                            icon = Icons.Rounded.Folder,
                            colors = colors,
                            onClick = {
                                Toast.makeText(context, "Storage: $formattedSize used by this chat", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    item(key = "media_visibility_card") {
                        SettingsCard(
                            title = "Media visibility",
                            subtitle = mediaVisibilityLabel,
                            icon = Icons.Rounded.PhotoLibrary,
                            colors = colors,
                            onClick = { showMediaVisibilityDialog = true }
                        )
                    }
                }

                2 -> {
                    // Settings Tab (Image 1)
                    item(key = "settings_header_label") {
                        Text(
                            text = "Settings",
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }

                    // Notifications / Mute
                    item(key = "settings_notifications") {
                        val isMuted = chat?.isMuted == true
                        SettingsCard(
                            title = "Notifications",
                            subtitle = if (isMuted) "Muted" else "All",
                            icon = if (isMuted) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                            colors = colors,
                            onClick = {
                                viewModel.muteChats(listOf(chatJid), !isMuted)
                            }
                        )
                    }

                    // Media visibility
                    item(key = "settings_media_visibility") {
                        SettingsCard(
                            title = "Media visibility",
                            subtitle = mediaVisibilityLabel,
                            icon = Icons.Rounded.PhotoSizeSelectActual,
                            colors = colors,
                            onClick = { showMediaVisibilityDialog = true }
                        )
                    }

                    // Encryption
                    item(key = "settings_encryption") {
                        SettingsCard(
                            title = "Encryption",
                            subtitle = "Messages and calls are end-to-end encrypted. Learn more",
                            icon = Icons.Rounded.Lock,
                            colors = colors,
                            onClick = {
                                Toast.makeText(context, "Signals end-to-end encrypted protocol active", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    // Chat lock
                    item(key = "settings_chat_lock") {
                        val isLocked = chat?.isLocked == true
                        SettingsCard(
                            title = "Chat lock",
                            subtitle = "Lock and hide this chat on this device.",
                            icon = Icons.Rounded.Security,
                            colors = colors,
                            trailing = {
                                Switch(
                                    checked = isLocked,
                                    onCheckedChange = { locked ->
                                        viewModel.setChatLock(chatJid, locked)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = colors.accentGreen
                                    )
                                )
                            },
                            onClick = {
                                viewModel.setChatLock(chatJid, !isLocked)
                            }
                        )
                    }

                    // Restricted chat
                    item(key = "settings_restricted_chat") {
                        SettingsCard(
                            title = "Restricted chat",
                            subtitle = "Off",
                            icon = Icons.Rounded.Shield,
                            colors = colors,
                            onClick = {
                                Toast.makeText(context, "Restricted chat is off", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    // Translate messages
                    item(key = "settings_translate") {
                        var translateEnabled by remember { mutableStateOf(false) }
                        SettingsCard(
                            title = "Translate messages",
                            subtitle = if (translateEnabled) "On" else "Off",
                            icon = Icons.Rounded.Translate,
                            colors = colors,
                            trailing = {
                                Switch(
                                    checked = translateEnabled,
                                    onCheckedChange = { translateEnabled = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = colors.accentGreen
                                    )
                                )
                            },
                            onClick = { translateEnabled = !translateEnabled }
                        )
                    }

                    // Add to Favorites
                    item(key = "settings_favorites") {
                        val isFav = chat?.isFavorite == true
                        SettingsCard(
                            title = if (isFav) "Remove from Favorites" else "Add to Favorites",
                            subtitle = if (isFav) "In favorites list" else "Not in favorites",
                            icon = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            colors = colors,
                            onClick = {
                                chat?.let { viewModel.toggleFavoriteChat(it) }
                            }
                        )
                    }

                    // Add to list
                    item(key = "settings_add_to_list") {
                        SettingsCard(
                            title = "Add to list",
                            subtitle = "Custom chat lists",
                            icon = Icons.Rounded.PlaylistAdd,
                            colors = colors,
                            onClick = {
                                Toast.makeText(context, "Custom chat lists coming soon", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    // Wallpaper & sound
                    item(key = "settings_wallpaper") {
                        SettingsCard(
                            title = "Wallpaper & sound",
                            subtitle = "Customize background wallpaper for this chat",
                            icon = Icons.Rounded.Wallpaper,
                            colors = colors,
                            onClick = { showWallpaperDialog = true }
                        )
                    }

                    // Privacy overrides
                    item(key = "settings_ghost_privacy") {
                        SettingsCard(
                            title = "Ghost Privacy Overrides",
                            subtitle = "Blue ticks, audio played receipts & typing visibility",
                            icon = Icons.Rounded.VisibilityOff,
                            colors = colors,
                            onClick = { showGhostPrivacySheet = true }
                        )
                    }

                    // Export chat
                    item(key = "settings_export_chat") {
                        SettingsCard(
                            title = "Export chat",
                            subtitle = "Export messages as plain text document",
                            icon = Icons.Rounded.Share,
                            colors = colors,
                            onClick = {
                                viewModel.exportChatToText(chatJid, context)
                            }
                        )
                    }

                    // Clear chat (red)
                    item(key = "settings_clear_chat") {
                        SettingsCard(
                            title = "Clear chat",
                            subtitle = "Permanently delete messages in this chat",
                            icon = Icons.Rounded.Block,
                            iconTint = colors.antiDeleteRed,
                            titleColor = colors.antiDeleteRed,
                            colors = colors,
                            onClick = { showClearChatDialog = true }
                        )
                    }
                }
            }
        }
    }

    // Lightbox for avatar photo
    if (showProfilePhotoLightbox) {
        ProfilePhotoLightbox(
            jid = chatJid,
            title = chatTitle,
            avatarPath = chat?.avatarPath,
            onDismiss = { showProfilePhotoLightbox = false },
            onRequestFullAvatar = { viewModel.requestFullProfilePicture(chatJid) },
            onSaveAvatar = {
                val safeName = chatTitle.filter { it.isLetterOrDigit() || it == '_' }
                viewModel.exportMediaToGallery(chat?.avatarPath, "${safeName}_avatar.jpg", "image/jpeg")
            }
        )
    }

    // Full screen media viewer
    mediaViewerTarget?.let { target ->
        FullScreenMediaViewer(
            msg = target,
            senderDisplayName = target.senderName.ifBlank { target.senderJid.substringBefore("@") },
            gallery = galleryMedia,
            onDismiss = { mediaViewerTarget = null },
            onSaveToGallery = { item ->
                val ext = if (item.msgType == "VIDEO") "mp4" else "png"
                val mime = item.mimeType.ifBlank { if (item.msgType == "VIDEO") "video/mp4" else "image/png" }
                viewModel.exportMediaToGallery(
                    item.localMediaPath,
                    "WA_Vault_${item.id}.$ext",
                    mime
                )
            }
        )
    }

    // Member action modal sheet
    activeMemberActionTarget?.let { target ->
        val liveMeta = participantMeta[target.participantJid] ?: participantMeta[target.chatJid]
        val pChatJid = resolveMemberChatJid(target.participantJid, liveMeta).ifBlank { target.chatJid }
        val pPhone = resolveMemberDisplayPhone(target.participantJid, liveMeta).ifBlank { target.phone }
        val pName = liveMeta?.savedName?.ifBlank { liveMeta.pushName }?.takeIf { it.isNotBlank() }
            ?: target.displayName
        val pAvatar = liveMeta?.avatarPath?.takeIf { it.isNotBlank() } ?: target.avatarPath

        ModalBottomSheet(
            onDismissRequest = { activeMemberActionTarget = null },
            containerColor = colors.elevatedBar
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WhatsAppAvatar(
                        jid = pChatJid,
                        avatarPath = pAvatar,
                        isGroup = false,
                        size = 46.dp
                    )
                    Column {
                        Text(
                            text = pName,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = pPhone.ifBlank { "Phone number hidden by privacy settings" },
                            color = colors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                HorizontalDivider(color = colors.divider)

                TextButton(
                    onClick = {
                        activeMemberActionTarget = null
                        onOpenChat(pChatJid)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Chat, null, tint = colors.accentGreen)
                        Text("Message $pName", color = colors.textPrimary, fontSize = 15.sp)
                    }
                }

                if (pPhone.isNotBlank()) {
                    TextButton(
                        onClick = {
                            activeMemberActionTarget = null
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("phone", pPhone))
                            Toast.makeText(context, "Phone number copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.ContentCopy, null, tint = colors.accentGreen)
                            Text("Copy phone number", color = colors.textPrimary, fontSize = 15.sp)
                        }
                    }
                }

                TextButton(
                    onClick = {
                        activeMemberActionTarget = null
                        Toast.makeText(context, "Calls require official WhatsApp app", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Call, null, tint = colors.accentGreen)
                        Text("Voice call $pName", color = colors.textPrimary, fontSize = 15.sp)
                    }
                }
            }
        }
    }

    // Clear chat dialog
    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = {
                Text(
                    text = "Clear this chat?",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will permanently delete all messages and media stored locally in this chat.",
                    color = colors.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearChatMessages(chatJid)
                        showClearChatDialog = false
                        Toast.makeText(context, "Chat cleared", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Clear chat", color = colors.antiDeleteRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) {
                    Text("Cancel", color = colors.textPrimary)
                }
            },
            containerColor = colors.elevatedBar
        )
    }

    // Media visibility dialog
    if (showMediaVisibilityDialog) {
        AlertDialog(
            onDismissRequest = { showMediaVisibilityDialog = false },
            title = {
                Text(
                    text = "Media visibility",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Show newly downloaded media from this chat in your device's gallery?",
                        color = colors.textSecondary,
                        fontSize = 13.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ChatMediaVisibilitySelector(
                        selectedOverride = mediaVisibilityOverride,
                        globalDefaultVisible = stealthConfig.mediaVisibilityInGallery,
                        onSelectOverride = { chosen ->
                            viewModel.setChatMediaVisibility(chatJid, chosen)
                            showMediaVisibilityDialog = false
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMediaVisibilityDialog = false }) {
                    Text("Close", color = colors.textPrimary)
                }
            },
            containerColor = colors.elevatedBar
        )
    }

    // Per-Chat Wallpaper picker
    if (showWallpaperDialog) {
        WallpaperPickerDialog(
            currentUri = stealthConfig.chatWallpaperUri,
            currentDim = stealthConfig.chatWallpaperDim,
            showDoodleOnPresets = stealthConfig.chatWallpaperShowDoodle,
            currentScale = stealthConfig.chatWallpaperScale,
            currentOffsetX = stealthConfig.chatWallpaperOffsetX,
            currentOffsetY = stealthConfig.chatWallpaperOffsetY,
            onSelectUri = { viewModel.setChatWallpaper(it) },
            onPickCustomImage = { viewModel.importChatWallpaperFromUri(it) },
            onDimChange = { viewModel.setChatWallpaperDim(it) },
            onToggleDoodle = { viewModel.setChatWallpaperShowDoodle(it) },
            onPositionChange = { scale, ox, oy -> viewModel.setChatWallpaperPosition(scale, ox, oy) },
            onDismiss = { showWallpaperDialog = false },
            chatTitle = chatTitle,
            hasChatOverride = hasWallpaperOverride,
            chatUri = effectiveWallpaper.uri,
            chatDim = effectiveWallpaper.dim,
            chatShowDoodle = effectiveWallpaper.showDoodle,
            chatScale = effectiveWallpaper.scale,
            chatOffsetX = effectiveWallpaper.offsetXFraction,
            chatOffsetY = effectiveWallpaper.offsetYFraction,
            onSelectChatUri = { viewModel.setPerChatWallpaper(chatJid, it) },
            onPickChatCustomImage = { viewModel.importPerChatWallpaperFromUri(chatJid, it) },
            onChatDimChange = { viewModel.setPerChatWallpaperDim(chatJid, it) },
            onToggleChatDoodle = { viewModel.setPerChatWallpaperShowDoodle(chatJid, it) },
            onChatPositionChange = { scale, ox, oy -> viewModel.setPerChatWallpaperPosition(chatJid, scale, ox, oy) },
            onResetChatToGlobal = { viewModel.clearPerChatWallpaper(chatJid) }
        )
    }

    // Ghost Privacy Overrides Sheet
    if (showGhostPrivacySheet) {
        var sendRead by remember(effectivePrivacy) { mutableStateOf(effectivePrivacy.sendReadReceipts) }
        var sendPlayed by remember(effectivePrivacy) { mutableStateOf(effectivePrivacy.sendPlayedReceipts) }
        var sendTyping by remember(effectivePrivacy) { mutableStateOf(effectivePrivacy.sendChatPresence) }

        ModalBottomSheet(
            onDismissRequest = { showGhostPrivacySheet = false },
            containerColor = colors.elevatedBar
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Ghost Privacy Overrides",
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    fontSize = 17.sp
                )
                Text(
                    text = "Override global stealth policies exclusively for this thread:",
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Send Read Receipts (Blue Ticks)", color = colors.textPrimary, fontSize = 14.sp)
                    Switch(checked = sendRead, onCheckedChange = { sendRead = it })
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Send Audio Played Receipts", color = colors.textPrimary, fontSize = 14.sp)
                    Switch(checked = sendPlayed, onCheckedChange = { sendPlayed = it })
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Send Live Typing & Recording", color = colors.textPrimary, fontSize = 14.sp)
                    Switch(checked = sendTyping, onCheckedChange = { sendTyping = it })
                }

                HorizontalDivider(color = colors.divider)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = {
                            viewModel.clearChatPrivacyOverride(chatJid)
                            showGhostPrivacySheet = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Use Global Default", color = colors.textSecondary)
                    }
                    TextButton(
                        onClick = {
                            viewModel.saveChatPrivacyOverride(chatJid, sendRead, sendPlayed, sendTyping)
                            showGhostPrivacySheet = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Override", color = colors.accentGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val colors = LocalWaColors.current
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.elevatedBar,
        tonalElevation = 2.dp,
        modifier = Modifier.border(0.8.dp, colors.divider, CircleShape)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = colors.accentGreen,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                color = colors.textPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AdminBadge(bgColor: Color, textColor: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bgColor.copy(alpha = 0.18f),
        modifier = Modifier.padding(start = 8.dp)
    ) {
        Text(
            text = "Group Admin",
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    colors: com.wa.beastclient.ui.theme.WaColors,
    iconTint: Color = colors.textSecondary,
    titleColor: Color = colors.textPrimary,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.appSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = colors.textSecondary,
                    fontSize = 12.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}
