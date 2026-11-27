package com.wa.beastclient.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import com.wa.beastclient.ui.components.ExpressiveIconBadge
import com.wa.beastclient.ui.components.WuppGlassCircleButton
import com.wa.beastclient.ui.components.WuppGlassTopBar
import com.wa.beastclient.ui.components.rememberWuppBackdropState
import com.wa.beastclient.ui.components.wuppBackdropSource
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PhotoSizeSelectActual
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.wa.beastclient.ui.components.InAppPdfViewerDialog
import com.wa.beastclient.ui.components.MessageHistorySheet
import com.wa.beastclient.ui.components.PerChatQuickControlsSheet
import com.wa.beastclient.ui.components.ProfilePhotoLightbox
import com.wa.beastclient.ui.components.WallpaperPickerDialog
import com.wa.beastclient.ui.components.formatBytesCompact
import com.wa.beastclient.ui.components.formatMessageClock
import com.wa.beastclient.ui.components.isDocumentMessage
import com.wa.beastclient.ui.components.openDocumentNatively
import com.wa.beastclient.ui.components.resolveDocumentTitleLine
import com.wa.beastclient.ui.theme.LocalWaColors
import com.wa.beastclient.ui.theme.LocalWuppUse24Hour
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

internal data class ChatWallLinkItem(
    val messageId: String,
    val rawUrl: String,
    val openUrl: String,
    val host: String,
    val snippet: String,
    val timestampMs: Long
)

private val CHAT_WALL_URL_REGEX = Regex("""(?i)\b(?:https?://|www\.)[^\s<>\]"']+""")

/** Extracts deduplicated clickable web links and message context snippets for the chat Links wall. */
internal fun extractChatWallLinks(messages: List<MessageEntity>): List<ChatWallLinkItem> {
    if (messages.isEmpty()) return emptyList()
    val result = ArrayList<ChatWallLinkItem>(messages.size)
    val seen = HashSet<String>(messages.size)
    for (msg in messages) {
        val body = msg.body
        if (body.isBlank()) continue
        for (match in CHAT_WALL_URL_REGEX.findAll(body)) {
            val cleaned = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '"', '\'')
            if (cleaned.length < 6) continue
            val normalized = if (cleaned.startsWith("http://", ignoreCase = true) ||
                cleaned.startsWith("https://", ignoreCase = true)
            ) {
                cleaned
            } else {
                "https://$cleaned"
            }
            val dedupKey = "${msg.id}|${normalized.lowercase(Locale.US)}"
            if (!seen.add(dedupKey)) continue
            val host = normalized
                .substringAfter("://")
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
                .removePrefix("www.")
                .ifBlank { cleaned }
            result += ChatWallLinkItem(
                messageId = msg.id,
                rawUrl = cleaned,
                openUrl = normalized,
                host = host,
                snippet = body.replace('\n', ' ').trim(),
                timestampMs = msg.timestamp
            )
        }
    }
    return result
}

private suspend fun loadChatWallThumbnail(path: String?, isVideo: Boolean): ImageBitmap? =
    withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext null
        val file = File(path)
        if (!file.exists() || file.length() <= 0L) return@withContext null
        runCatching {
            val raw: Bitmap? = if (isVideo) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    runCatching { retriever.release() }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                val maxDim = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
                var sample = 1
                while (maxDim / sample > 360) sample *= 2
                BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                )
            }
            raw?.asImageBitmap()
        }.getOrNull()
    }

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
    val allChats by viewModel.allChats.collectAsState()
    val allContacts by viewModel.allContacts.collectAsState()
    val contact = remember(chatJid, allContacts) { allContacts.firstOrNull { it.jid == chatJid } }
    val stealthConfig by viewModel.stealthConfig.collectAsState()
    val deletedHistory by viewModel.deletedMessagesHistory.collectAsState()
    val editedHistory by viewModel.editedMessagesHistory.collectAsState()
    val allEditsHistory by viewModel.allMessageEdits.collectAsState()
    var showChatHistorySheet by remember { mutableStateOf(false) }
    val galleryMedia by viewModel.observeChatGalleryMedia(chatJid).collectAsState(initial = emptyList())
    val wallMediaItems by viewModel.observeChatMediaWallItems(chatJid).collectAsState(initial = emptyList())
    val rawDocMessages by viewModel.observeChatDocuments(chatJid).collectAsState(initial = emptyList())
    val rawLinkMessages by viewModel.observeChatLinkMessages(chatJid).collectAsState(initial = emptyList())
    val downloadingMediaIds by viewModel.downloadingMediaIds.collectAsState()
    val docItems = remember(rawDocMessages) { rawDocMessages.filter { isDocumentMessage(it) } }
    val linkItems = remember(rawLinkMessages) { extractChatWallLinks(rawLinkMessages) }
    val use24Hour = LocalWuppUse24Hour.current

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
    var mediaWallSubTab by remember { mutableIntStateOf(0) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showProfilePhotoLightbox by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showGhostPrivacySheet by remember { mutableStateOf(false) }
    var showMediaVisibilityDialog by remember { mutableStateOf(false) }
    var showAutoDownloadSheet by remember { mutableStateOf(false) }
    var mediaViewerTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var fallbackPdfTarget by remember { mutableStateOf<Pair<File, String>?>(null) }
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

    val infoBackdropState = rememberWuppBackdropState()
    val classicMode = stealthConfig.classicWhatsAppLook

    Scaffold(
        topBar = {
            WuppGlassTopBar(
                backdropState = infoBackdropState,
                classicMode = classicMode,
                leading = {
                    WuppGlassCircleButton(
                        onClick = onBack,
                        contentDescription = "Back",
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        size = 36.dp,
                        iconSize = 19.dp,
                        backdropState = infoBackdropState,
                        classicMode = classicMode
                    )
                },
                center = {
                    Text(
                        text = if (isGroup) "Group Info" else if (isChannel) "Channel Info" else "Contact Info",
                        color = colors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailing = {
                    Box {
                        WuppGlassCircleButton(
                            onClick = { showOverflowMenu = true },
                            contentDescription = "More",
                            imageVector = Icons.Rounded.MoreHoriz,
                            size = 36.dp,
                            iconSize = 19.dp,
                            backdropState = infoBackdropState,
                            classicMode = classicMode
                        )
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
                }
            )
        },
        containerColor = colors.appSurface
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!classicMode) Modifier.wuppBackdropSource(infoBackdropState) else Modifier)
                .padding(innerPadding)
        ) {
            // Hero section: Centered large avatar, Name, Subtitle, MB/iOS-style vertical quick action tiles
            item(key = "hero_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.appSurface)
                        .padding(top = 18.dp, bottom = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(124.dp)
                            .clip(CircleShape)
                            .border(2.dp, colors.accentGreen.copy(alpha = 0.35f), CircleShape)
                            .padding(3.dp)
                            .clickable { showProfilePhotoLightbox = true },
                        contentAlignment = Alignment.Center
                    ) {
                        WhatsAppAvatar(
                            jid = chatJid,
                            avatarPath = chat?.avatarPath,
                            isGroup = isGroup,
                            size = 118.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = chatTitle,
                        color = colors.textPrimary,
                        fontSize = 23.sp,
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
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Equal-width vertical quick-action tiles: [Message] [Audio/Voice chat] [Video]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InfoActionPill(
                            icon = Icons.AutoMirrored.Rounded.Chat,
                            label = "Message",
                            onClick = { onOpenChat(chatJid) },
                            modifier = Modifier.weight(1f)
                        )

                        if (isGroup) {
                            InfoActionPill(
                                icon = Icons.Rounded.GraphicEq,
                                label = "Voice chat",
                                onClick = {
                                    Toast.makeText(context, "Voice chat requires official WhatsApp app", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            InfoActionPill(
                                icon = Icons.Rounded.Call,
                                label = "Audio",
                                onClick = {
                                    Toast.makeText(context, "Calls require official WhatsApp app", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            )

                            InfoActionPill(
                                icon = Icons.Rounded.Videocam,
                                label = "Video",
                                onClick = {
                                    Toast.makeText(context, "Calls require official WhatsApp app", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Segmented Pill Tabs Header: Members / Overview, Media, Settings
            item(key = "tab_row") {
                val tabTitles = if (isGroup) listOf("Members", "Media", "Settings") else listOf("Overview", "Media", "Settings")
                val trackBorder = if (colors.isDark) Color.White.copy(alpha = 0.08f) else colors.divider
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.elevatedBar)
                        .border(0.6.dp, trackBorder, RoundedCornerShape(14.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        val active = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    if (active) colors.accentGreen.copy(alpha = if (colors.isDark) 0.22f else 0.16f)
                                    else Color.Transparent
                                )
                                .clickable { selectedTab = index }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 13.5.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) colors.accentGreen else colors.textSecondary
                            )
                        }
                    }
                }
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

                        item(key = "dm_overview_media_shortcut") {
                            SettingsCard(
                                title = "Media, links, and docs",
                                subtitle = "${wallMediaItems.size} media · ${docItems.size} docs · ${linkItems.size} links",
                                icon = Icons.Rounded.PhotoLibrary,
                                colors = colors,
                                onClick = { selectedTab = 1 }
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
                    // Full Media, Docs & Links Wall
                    item(key = "media_wall_subtabs") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.appSurface)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val subTabs = listOf(
                                "Media (${wallMediaItems.size})",
                                "Docs (${docItems.size})",
                                "Links (${linkItems.size})"
                            )
                            subTabs.forEachIndexed { idx, label ->
                                val selected = mediaWallSubTab == idx
                                FilterChip(
                                    selected = selected,
                                    onClick = { mediaWallSubTab = idx },
                                    label = {
                                        Text(
                                            text = label,
                                            fontSize = 12.5.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.pillActiveBg,
                                        selectedLabelColor = colors.pillActiveText,
                                        containerColor = colors.pillInactiveBg,
                                        labelColor = colors.textSecondary
                                    )
                                )
                            }
                        }
                    }

                    when (mediaWallSubTab) {
                        0 -> {
                            if (wallMediaItems.isEmpty()) {
                                item(key = "media_wall_empty") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(colors.appSurface)
                                            .padding(horizontal = 16.dp, vertical = 22.dp)
                                    ) {
                                        Text(
                                            text = "No photos or videos shared in this chat yet.",
                                            color = colors.textSecondary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            } else {
                                val rows = wallMediaItems.chunked(3)
                                items(rows, key = { row -> row.joinToString("_") { it.id } }) { rowItems ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(colors.appSurface)
                                            .padding(horizontal = 14.dp, vertical = 3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        for (col in 0 until 3) {
                                            val itemMsg = rowItems.getOrNull(col)
                                            if (itemMsg == null) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            } else {
                                                val localFile = itemMsg.localMediaPath?.let { p ->
                                                    File(p).takeIf { it.exists() && it.length() > 0L }
                                                }
                                                val isVideo = itemMsg.msgType.equals("VIDEO", ignoreCase = true)
                                                val isDownloading = itemMsg.id in downloadingMediaIds
                                                val thumb by produceState<ImageBitmap?>(
                                                    initialValue = null,
                                                    key1 = localFile?.absolutePath,
                                                    key2 = isVideo
                                                ) {
                                                    value = loadChatWallThumbnail(localFile?.absolutePath, isVideo)
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .aspectRatio(1f)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(colors.pillInactiveBg)
                                                        .clickable {
                                                            if (localFile != null) {
                                                                mediaViewerTarget = itemMsg
                                                            } else if (!isDownloading) {
                                                                viewModel.requestMediaDownload(itemMsg, userInitiated = true)
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (thumb != null) {
                                                        Image(
                                                            bitmap = thumb!!,
                                                            contentDescription = if (isVideo) "Video thumbnail" else "Photo thumbnail",
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = if (isVideo) Icons.Rounded.Videocam else Icons.Rounded.PhotoSizeSelectActual,
                                                            contentDescription = null,
                                                            tint = colors.textSecondary
                                                        )
                                                    }

                                                    if (localFile == null) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .background(Color.Black.copy(alpha = 0.42f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (isDownloading) {
                                                                CircularProgressIndicator(
                                                                    color = Color.White,
                                                                    strokeWidth = 2.dp,
                                                                    modifier = Modifier.size(22.dp)
                                                                )
                                                            } else {
                                                                Icon(
                                                                    imageVector = Icons.Rounded.Download,
                                                                    contentDescription = "Download media",
                                                                    tint = Color.White,
                                                                    modifier = Modifier.size(24.dp)
                                                                )
                                                            }
                                                        }
                                                    } else if (isVideo) {
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.BottomStart)
                                                                .padding(6.dp)
                                                                .clip(CircleShape)
                                                                .background(Color.Black.copy(alpha = 0.62f))
                                                                .padding(4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Rounded.PlayArrow,
                                                                contentDescription = "Video",
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
                            }
                        }

                        1 -> {
                            if (docItems.isEmpty()) {
                                item(key = "docs_wall_empty") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(colors.appSurface)
                                            .padding(horizontal = 16.dp, vertical = 22.dp)
                                    ) {
                                        Text(
                                            text = "No documents shared in this chat yet.",
                                            color = colors.textSecondary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            } else {
                                items(docItems, key = { "wall_doc_${it.id}" }) { docMsg ->
                                    val title = remember(docMsg.id, docMsg.body) {
                                        resolveDocumentTitleLine(docMsg.body).ifBlank { "Document" }
                                    }
                                    val extBadge = remember(title) {
                                        title.substringAfterLast('.', "")
                                            .uppercase(Locale.US)
                                            .take(4)
                                            .ifBlank { "DOC" }
                                    }
                                    val localFile = docMsg.localMediaPath?.let { p ->
                                        File(p).takeIf { it.exists() && it.length() > 0L }
                                    }
                                    val isDownloading = docMsg.id in downloadingMediaIds
                                    val sizeLabel = remember(docMsg.fileSize, localFile?.length()) {
                                        val bytes = localFile?.length() ?: docMsg.fileSize
                                        if (bytes > 0L) formatBytesCompact(bytes) else ""
                                    }
                                    val dateLabel = formatMessageClock(
                                        docMsg.timestamp,
                                        includeSeconds = false,
                                        use24Hour = use24Hour
                                    )
                                    val metaLine = listOfNotNull(
                                        sizeLabel.takeIf { it.isNotBlank() },
                                        dateLabel,
                                        if (localFile != null) "Tap to open" else if (isDownloading) "Downloading..." else "Tap to download"
                                    ).joinToString(" · ")

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(colors.appSurface)
                                            .clickable {
                                                if (localFile != null) {
                                                    openDocumentNatively(context, docMsg, localFile) { pdfFile ->
                                                        fallbackPdfTarget = pdfFile to title
                                                    }
                                                } else if (!isDownloading) {
                                                    viewModel.requestMediaDownload(docMsg, userInitiated = true)
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(colors.pillActiveBg),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isDownloading) {
                                                CircularProgressIndicator(
                                                    color = colors.pillActiveText,
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = extBadge,
                                                    color = colors.pillActiveText,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = title,
                                                color = colors.textPrimary,
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = metaLine,
                                                color = colors.textSecondary,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (localFile == null && !isDownloading) {
                                            Icon(
                                                imageVector = Icons.Rounded.Download,
                                                contentDescription = "Download document",
                                                tint = colors.accentGreen,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Description,
                                                contentDescription = null,
                                                tint = colors.textSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            if (linkItems.isEmpty()) {
                                item(key = "links_wall_empty") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(colors.appSurface)
                                            .padding(horizontal = 16.dp, vertical = 22.dp)
                                    ) {
                                        Text(
                                            text = "No links shared in this chat yet.",
                                            color = colors.textSecondary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            } else {
                                items(linkItems, key = { "${it.messageId}_${it.openUrl}" }) { link ->
                                    val dateLabel = formatMessageClock(
                                        link.timestampMs,
                                        includeSeconds = false,
                                        use24Hour = use24Hour
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(colors.appSurface)
                                            .clickable {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, Uri.parse(link.openUrl))
                                                    )
                                                }.onFailure {
                                                    Toast.makeText(context, "No app available to open link", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(colors.pillActiveBg),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Link,
                                                contentDescription = null,
                                                tint = colors.pillActiveText,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = link.rawUrl,
                                                color = colors.brightGreen,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${link.host} · $dateLabel",
                                                color = colors.textSecondary,
                                                fontSize = 11.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (link.snippet.isNotBlank() && link.snippet != link.rawUrl) {
                                                Text(
                                                    text = link.snippet,
                                                    color = colors.textSecondary.copy(alpha = 0.8f),
                                                    fontSize = 11.5.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "media_manage_storage") {
                        val totalBytes = remember(galleryMedia, docItems) {
                            (galleryMedia + docItems).distinctBy { it.id }.sumOf { m ->
                                m.localMediaPath?.let { p -> File(p).takeIf { it.exists() }?.length() ?: 0L } ?: 0L
                            }
                        }
                        val formattedSize = remember(totalBytes) {
                            formatBytesCompact(totalBytes)
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

                    // Per-chat media auto-download
                    item(key = "settings_auto_download") {
                        val perChatMask = remember(stealthConfig, chatJid) {
                            StealthMatrixManager.getPerChatAutoDownloadMask(chatJid)
                        }
                        SettingsCard(
                            title = "Media auto-download",
                            subtitle = if (perChatMask != null)
                                "Custom: ${StealthMatrixManager.formatAutoDownloadSummary(perChatMask)}"
                            else
                                "Following global settings",
                            icon = Icons.Rounded.Download,
                            colors = colors,
                            onClick = { showAutoDownloadSheet = true }
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

    fallbackPdfTarget?.let { (pdfFile, pdfTitle) ->
        InAppPdfViewerDialog(
            file = pdfFile,
            title = pdfTitle,
            colors = colors,
            onDismiss = { fallbackPdfTarget = null }
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

    // Per-chat media auto-download bottom sheet
    if (showAutoDownloadSheet) {
        val currentOverride = remember(stealthConfig, chatJid) {
            StealthMatrixManager.getPerChatAutoDownloadMask(chatJid)
        }
        val activeGlobalMask = remember(stealthConfig) {
            StealthMatrixManager.activeAutoDownloadMask(context, stealthConfig)
        }
        // Start with the override mask or the current active global mask so toggles make sense
        var photoOn by remember(currentOverride, activeGlobalMask) {
            mutableStateOf(
                currentOverride?.let { (it and StealthMatrixManager.MEDIA_PHOTO) != 0 }
                    ?: ((activeGlobalMask and StealthMatrixManager.MEDIA_PHOTO) != 0)
            )
        }
        var videoOn by remember(currentOverride, activeGlobalMask) {
            mutableStateOf(
                currentOverride?.let { (it and StealthMatrixManager.MEDIA_VIDEO) != 0 }
                    ?: ((activeGlobalMask and StealthMatrixManager.MEDIA_VIDEO) != 0)
            )
        }
        var audioOn by remember(currentOverride, activeGlobalMask) {
            mutableStateOf(
                currentOverride?.let { (it and StealthMatrixManager.MEDIA_AUDIO) != 0 }
                    ?: ((activeGlobalMask and StealthMatrixManager.MEDIA_AUDIO) != 0)
            )
        }
        var docOn by remember(currentOverride, activeGlobalMask) {
            mutableStateOf(
                currentOverride?.let { (it and StealthMatrixManager.MEDIA_DOCUMENT) != 0 }
                    ?: ((activeGlobalMask and StealthMatrixManager.MEDIA_DOCUMENT) != 0)
            )
        }

        ModalBottomSheet(onDismissRequest = { showAutoDownloadSheet = false },
            containerColor = colors.elevatedBar
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Media auto-download",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "Override global auto-download settings for this chat.",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Photos toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text("Photos", color = colors.textPrimary, modifier = Modifier.weight(1f))
                    Switch(
                        checked = photoOn,
                        onCheckedChange = { photoOn = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.accentGreen, checkedTrackColor = colors.accentGreen.copy(alpha = 0.4f))
                    )
                }
                // Videos toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text("Videos", color = colors.textPrimary, modifier = Modifier.weight(1f))
                    Switch(
                        checked = videoOn,
                        onCheckedChange = { videoOn = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.accentGreen, checkedTrackColor = colors.accentGreen.copy(alpha = 0.4f))
                    )
                }
                // Audio toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text("Audio", color = colors.textPrimary, modifier = Modifier.weight(1f))
                    Switch(
                        checked = audioOn,
                        onCheckedChange = { audioOn = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.accentGreen, checkedTrackColor = colors.accentGreen.copy(alpha = 0.4f))
                    )
                }
                // Documents toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text("Documents", color = colors.textPrimary, modifier = Modifier.weight(1f))
                    Switch(
                        checked = docOn,
                        onCheckedChange = { docOn = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.accentGreen, checkedTrackColor = colors.accentGreen.copy(alpha = 0.4f))
                    )
                }

                Row(modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentOverride != null) {
                        TextButton(
                            onClick = {
                                viewModel.setPerChatAutoDownload(chatJid, null)
                                showAutoDownloadSheet = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset to global", color = colors.textSecondary)
                        }
                    }
                    TextButton(
                        onClick = {
                            var mask = 0
                            if (photoOn) mask = mask or StealthMatrixManager.MEDIA_PHOTO
                            if (videoOn) mask = mask or StealthMatrixManager.MEDIA_VIDEO
                            if (audioOn) mask = mask or StealthMatrixManager.MEDIA_AUDIO
                            if (docOn) mask = mask or StealthMatrixManager.MEDIA_DOCUMENT
                            viewModel.setPerChatAutoDownload(chatJid, mask)
                            showAutoDownloadSheet = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save", color = colors.accentGreen, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    // Per-Chat Quick Controls & Privacy Overrides Sheet
    if (showGhostPrivacySheet) {
        val chatSavedCount = remember(deletedHistory, editedHistory, chatJid) {
            (deletedHistory.asSequence().filter { it.chatJid == chatJid }.map { it.id } +
                editedHistory.asSequence().filter { it.chatJid == chatJid }.map { it.id }).toSet().size
        }
        PerChatQuickControlsSheet(
            chat = chat,
            chatJid = chatJid,
            effectivePrivacy = effectivePrivacy,
            stealthConfig = stealthConfig,
            savedHistoryCount = chatSavedCount,
            onSavePrivacyOverride = { r, p, t -> viewModel.saveChatPrivacyOverride(chatJid, r, p, t) },
            onClearPrivacyOverride = { viewModel.clearChatPrivacyOverride(chatJid) },
            onSetGalleryVisibility = { viewModel.setChatMediaVisibility(chatJid, it) },
            onTogglePin = { chat?.let { viewModel.togglePinChat(it) } },
            onToggleMute = { chat?.let { viewModel.muteChats(listOf(it.jid), !it.isMuted) } },
            onToggleFavorite = { chat?.let { viewModel.toggleFavoriteChat(it) } },
            onToggleLock = { chat?.let { viewModel.setChatLock(it.jid, !it.isLocked) } },
            onOpenWallpaper = { showWallpaperDialog = true },
            onOpenChatHistory = { showChatHistorySheet = true },
            onSetHideChatNames = { enabled ->
                viewModel.updateGlobalStealth { c -> c.copy(hideChatContactNames = enabled) }
            },
            onSetHideChatDps = { enabled ->
                viewModel.updateGlobalStealth { c -> c.copy(hideChatProfilePictures = enabled) }
            },
            onSetHideChatNamesAndDps = { enabled ->
                viewModel.updateGlobalStealth { c ->
                    c.copy(hideChatContactNames = enabled, hideChatProfilePictures = enabled)
                }
            },
            onDismiss = { showGhostPrivacySheet = false }
        )
    }

    if (showChatHistorySheet) {
        MessageHistorySheet(
            deletedMessages = deletedHistory,
            editedMessages = editedHistory,
            allEdits = allEditsHistory,
            chats = allChats,
            initialChatJid = chatJid,
            onJumpToChat = { targetJid -> onOpenChat(targetJid) },
            onDeleteEntry = { viewModel.deleteMessageHistoryItem(it) },
            onClearAll = { scoped -> viewModel.clearAllMessageHistory(scoped) },
            onDismiss = { showChatHistorySheet = false }
        )
    }
}

@Composable
private fun InfoActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaColors.current
    val cardBorder = if (colors.isDark) Color.White.copy(alpha = 0.08f) else colors.divider
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = colors.elevatedBar,
        tonalElevation = 0.dp,
        modifier = modifier.border(0.65.dp, cardBorder, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = colors.accentGreen,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                color = colors.textPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AdminBadge(bgColor: Color, textColor: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
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
    iconTint: Color = colors.accentGreen,
    titleColor: Color = colors.textPrimary,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val cardBorder = if (colors.isDark) Color.White.copy(alpha = 0.06f) else colors.divider
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.elevatedBar,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.5.dp)
            .border(0.6.dp, cardBorder, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveIconBadge(
                icon = icon,
                contentDescription = null,
                active = true,
                tintOverride = iconTint
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = titleColor,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.textSecondary.copy(alpha = 0.45f),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}
