package com.wa.beastclient.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import com.wa.beastclient.BuildConfig
import com.wa.beastclient.R
import com.wa.beastclient.core.update.UpdateUiState
import com.wa.beastclient.data.db.ChatEntity
import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.data.db.StatusEntity
import com.wa.beastclient.domain.stealth.AutoDownloadNetwork
import com.wa.beastclient.domain.stealth.StealthMatrixManager
import com.wa.beastclient.ui.HomeFilterChip
import com.wa.beastclient.ui.components.AutoDownloadNetworkDialog
import com.wa.beastclient.ui.components.FullScreenMediaViewer
import com.wa.beastclient.ui.components.FullScreenStatusViewer
import com.wa.beastclient.ui.components.GalleryVisibilityContactsDialog
import com.wa.beastclient.ui.components.StatusGroup
import com.wa.beastclient.ui.components.GitHubFeedbackDialog
import com.wa.beastclient.ui.components.GitHubFeedbackType
import com.wa.beastclient.ui.MainViewModel
import com.wa.beastclient.ui.components.DeliveryTickIcon
import com.wa.beastclient.ui.components.QuickContactDialog
import com.wa.beastclient.ui.components.ThemePickerDialog
import com.wa.beastclient.ui.components.ThemeSelectorGrid
import com.wa.beastclient.ui.components.formatMessageClock
import com.wa.beastclient.ui.theme.LocalWaColors
import com.wa.beastclient.ui.theme.LocalWaMonoFamily
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenChat: (String) -> Unit,
    currentTab: Int = 0,
    onSelectTab: (Int) -> Unit = {},
    chatsListState: LazyListState = rememberLazyListState(),
    updatesListState: LazyListState = rememberLazyListState(),
    communitiesListState: LazyListState = rememberLazyListState(),
    toolsListState: LazyListState = rememberLazyListState()
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current

    val chats by viewModel.filteredChats.collectAsState()
    val allChats by viewModel.allChats.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val selectedChip by viewModel.selectedFilter.collectAsState()
    val searchQuery by viewModel.homeSearchQuery.collectAsState()
    val ftsHits by viewModel.ftsSearchResults.collectAsState()
    val stealth by viewModel.stealthConfig.collectAsState()
    val bridgeStatus by viewModel.bridgeStatus.collectAsState()
    val heartbeat by viewModel.heartbeatStatus.collectAsState()
    val statuses by viewModel.statuses.collectAsState()
    val voVault by viewModel.viewOnceVault.collectAsState()
    val presenceLogs by viewModel.presenceLogs.collectAsState()
    val profileHistory by viewModel.profileHistory.collectAsState()
    val bulkResult by viewModel.bulkVerifyOutput.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val context = LocalContext.current

    val unreadChatsCount = remember(allChats) {
        allChats.count {
            !it.isCommunityParent &&
                !it.jid.endsWith("@newsletter") &&
                !(it.name.startsWith("@") && it.lastMessagePreview.isBlank() && (it.lastMessageType == "UNKNOWN" || it.lastMessageType.isBlank())) &&
                it.unreadCount > 0
        }
    }

    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var showPairingDialog by remember { mutableStateOf(false) }
    var showQuickContactPicker by remember { mutableStateOf(false) }
    var quickContactTarget by remember { mutableStateOf<ChatEntity?>(null) }
    val quickContactChat = remember(quickContactTarget, allChats) {
        quickContactTarget?.let { t -> allChats.firstOrNull { it.jid == t.jid } ?: t }
    }
    var activeStatusViewer by remember { mutableStateOf<StatusEntity?>(null) }
    var activeVaultViewer by remember { mutableStateOf<MessageEntity?>(null) }
    var activeFeedbackType by remember { mutableStateOf<GitHubFeedbackType?>(null) }
    var showThemePickerDialog by remember { mutableStateOf(false) }
    var selectedChatJids by remember { mutableStateOf(setOf<String>()) }
    var selectionOverflowExpanded by remember { mutableStateOf(false) }
    var pendingDeleteJids by remember { mutableStateOf<List<String>>(emptyList()) }

    val selectedChats = remember(selectedChatJids, allChats, chats) {
        val merged = (allChats + chats).associateBy { it.jid }
        selectedChatJids.mapNotNull { merged[it] }
    }

    LaunchedEffect(currentTab) {
        if (currentTab != 0 && selectedChatJids.isNotEmpty()) {
            selectedChatJids = emptySet()
            selectionOverflowExpanded = false
        }
    }

    BackHandler(enabled = selectedChatJids.isNotEmpty()) {
        selectedChatJids = emptySet()
        selectionOverflowExpanded = false
    }

    Scaffold(
        containerColor = colors.appSurface,
        topBar = {
            Column(modifier = Modifier.background(colors.appSurface)) {
                if (currentTab == 0 && selectedChatJids.isNotEmpty()) {
                    val allPinned = selectedChats.isNotEmpty() && selectedChats.all { it.isPinned }
                    val allMuted = selectedChats.isNotEmpty() && selectedChats.all { it.isMuted }
                    val allArchived = selectedChats.isNotEmpty() && selectedChats.all { it.isArchived }
                    val allFavorite = selectedChats.isNotEmpty() && selectedChats.all { it.isFavorite }
                    val anyUnread = selectedChats.any { it.unreadCount > 0 }
                    val targetJids = selectedChatJids.toList()

                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = colors.elevatedBar,
                            titleContentColor = colors.textPrimary
                        ),
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    selectedChatJids = emptySet()
                                    selectionOverflowExpanded = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Clear selection",
                                    tint = colors.textPrimary
                                )
                            }
                        },
                        title = {
                            Text(
                                text = "${selectedChatJids.size}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    viewModel.pinChats(targetJids, !allPinned)
                                    selectedChatJids = emptySet()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = if (allPinned) "Unpin" else "Pin",
                                    tint = if (allPinned) colors.brightGreen else colors.textPrimary
                                )
                            }
                            IconButton(
                                onClick = { pendingDeleteJids = targetJids }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Delete locally",
                                    tint = colors.textPrimary
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.muteChats(targetJids, !allMuted)
                                    selectedChatJids = emptySet()
                                }
                            ) {
                                Icon(
                                    imageVector = if (allMuted) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff,
                                    contentDescription = if (allMuted) "Unmute notifications" else "Mute notifications",
                                    tint = colors.textPrimary
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.archiveChats(targetJids, !allArchived)
                                    selectedChatJids = emptySet()
                                }
                            ) {
                                Icon(
                                    imageVector = if (allArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                                    contentDescription = if (allArchived) "Unarchive" else "Archive",
                                    tint = colors.textPrimary
                                )
                            }
                            IconButton(onClick = { selectionOverflowExpanded = true }) {
                                Icon(Icons.Rounded.MoreVert, "More actions", tint = colors.textPrimary)
                            }
                            DropdownMenu(
                                expanded = selectionOverflowExpanded,
                                onDismissRequest = { selectionOverflowExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (anyUnread) "Mark as read" else "Mark as unread") },
                                    onClick = {
                                        selectionOverflowExpanded = false
                                        viewModel.setChatsUnreadLocal(targetJids, !anyUnread)
                                        selectedChatJids = emptySet()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (allFavorite) "Remove from Favorites" else "Add to Favorites") },
                                    onClick = {
                                        selectionOverflowExpanded = false
                                        viewModel.favoriteChats(targetJids, !allFavorite)
                                        selectedChatJids = emptySet()
                                    }
                                )
                                if (selectedChats.size == 1) {
                                    val only = selectedChats.first()
                                    DropdownMenuItem(
                                        text = { Text("View contact") },
                                        onClick = {
                                            selectionOverflowExpanded = false
                                            selectedChatJids = emptySet()
                                            quickContactTarget = only
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Select all") },
                                    onClick = {
                                        selectionOverflowExpanded = false
                                        selectedChatJids = chats.map { it.jid }.toSet()
                                    }
                                )
                            }
                        }
                    )
                } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.appSurface,
                        titleContentColor = if (colors.isDark) colors.textPrimary else colors.accentGreen
                    ),
                    title = {
                        if (currentTab == 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.wupp_logo),
                                    contentDescription = "Wupp",
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "Wupp",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                text = when (currentTab) {
                                    1 -> "Updates"
                                    2 -> "Communities"
                                    else -> "Settings"
                                },
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    actions = {
                        if (currentTab == 0) {
                            IconButton(onClick = { showPairingDialog = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.vec_ic_camera_reply),
                                    contentDescription = "Camera",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        } else if (currentTab == 1 || currentTab == 2) {
                            IconButton(onClick = {
                                searchExpanded = !searchExpanded
                                if (!searchExpanded) viewModel.updateHomeSearch("")
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.vec_ic_search),
                                    contentDescription = "Search",
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        IconButton(onClick = { overflowExpanded = true }) {
                            Icon(Icons.Rounded.MoreVert, "Menu", tint = colors.textPrimary)
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("New chat") },
                                onClick = {
                                    overflowExpanded = false
                                    showQuickContactPicker = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Linked devices") },
                                leadingIcon = { Icon(Icons.Rounded.QrCode2, null) },
                                onClick = {
                                    overflowExpanded = false
                                    showPairingDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Starred messages") },
                                onClick = {
                                    overflowExpanded = false
                                    viewModel.selectFilter(HomeFilterChip.FAVORITES)
                                    onSelectTab(0)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Archived chats") },
                                leadingIcon = { Icon(Icons.Rounded.Archive, null) },
                                onClick = {
                                    overflowExpanded = false
                                    viewModel.toggleArchivedIncluded()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    overflowExpanded = false
                                    onSelectTab(3)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Check for updates") },
                                leadingIcon = { Icon(Icons.Rounded.SystemUpdate, null) },
                                onClick = {
                                    overflowExpanded = false
                                    viewModel.checkForUpdates(silent = false)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Report bug / Feedback") },
                                leadingIcon = { Icon(Icons.Rounded.BugReport, null) },
                                onClick = {
                                    overflowExpanded = false
                                    activeFeedbackType = GitHubFeedbackType.BUG
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Theme · ${stealth.themeMode.label}") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Palette, null)
                                },
                                onClick = {
                                    overflowExpanded = false
                                    showThemePickerDialog = true
                                }
                            )
                        }
                    }
                )
                }

                AnimatedVisibility(visible = searchExpanded && currentTab != 0) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateHomeSearch(it) },
                        placeholder = { Text("Search...") },
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateHomeSearch("") }) {
                                    Icon(Icons.Rounded.Close, "Clear")
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = colors.appSurface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { onSelectTab(0) },
                    icon = {
                        Box(
                            modifier = Modifier.size(width = 40.dp, height = 26.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.vec_ic_chat_filled),
                                contentDescription = "Chats",
                                modifier = Modifier.size(22.dp)
                            )
                            if (unreadChatsCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .clip(CircleShape)
                                        .background(colors.brightGreen)
                                        .padding(horizontal = 4.5.dp, vertical = 0.5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (unreadChatsCount > 99) "99+" else unreadChatsCount.toString(),
                                        color = Color(0xFF0B141A),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 11.sp
                                    )
                                }
                            }
                        }
                    },
                    label = { Text("Chats", fontSize = 12.sp) },
                    colors = navItemColors()
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { onSelectTab(1) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_status),
                            contentDescription = "Updates",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = { Text("Updates", fontSize = 12.sp) },
                    colors = navItemColors()
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { onSelectTab(2) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_avatar_community),
                            contentDescription = "Communities",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = { Text("Communities", fontSize = 12.sp) },
                    colors = navItemColors()
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { onSelectTab(3) },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = "Settings",
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = { Text("Settings", fontSize = 12.sp) },
                    colors = navItemColors()
                )
            }
        },
        floatingActionButton = {
            when (currentTab) {
                0 -> {
                    FloatingActionButton(
                        onClick = { showQuickContactPicker = true },
                        containerColor = colors.brightGreen,
                        contentColor = Color(0xFF0B141A),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_chat_filled),
                            contentDescription = "New Chat",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                1 -> {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FloatingActionButton(
                            onClick = { statuses.firstOrNull()?.let { activeStatusViewer = it } },
                            containerColor = colors.elevatedBar,
                            contentColor = colors.textPrimary,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Text status",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        FloatingActionButton(
                            onClick = { statuses.firstOrNull()?.let { activeStatusViewer = it } },
                            containerColor = colors.brightGreen,
                            contentColor = Color(0xFF0B141A),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.vec_ic_camera_reply),
                                contentDescription = "Camera status",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> ChatsTabContent(
                    chats = chats,
                    allChats = allChats,
                    selectedChip = selectedChip,
                    ftsHits = ftsHits,
                    searchQuery = searchQuery,
                    listState = chatsListState,
                    selectedChatJids = selectedChatJids,
                    onSearchChange = { viewModel.updateHomeSearch(it) },
                    heartbeat = heartbeat,
                    bridgeStatus = bridgeStatus,
                    onSelectChip = { viewModel.selectFilter(it) },
                    onResetHeartbeat = { viewModel.recordPrimaryHeartbeatNow() },
                    onSeedShowcase = { viewModel.seedShowcaseData() },
                    onAvatarClick = { chat ->
                        if (selectedChatJids.isNotEmpty()) {
                            selectedChatJids = if (chat.jid in selectedChatJids) {
                                selectedChatJids - chat.jid
                            } else {
                                selectedChatJids + chat.jid
                            }
                        } else {
                            quickContactTarget = chat
                        }
                    },
                    onChatClick = { chat ->
                        if (selectedChatJids.isNotEmpty()) {
                            selectedChatJids = if (chat.jid in selectedChatJids) {
                                selectedChatJids - chat.jid
                            } else {
                                selectedChatJids + chat.jid
                            }
                        } else {
                            onOpenChat(chat.jid)
                        }
                    },
                    onChatLongClick = { chat ->
                        selectedChatJids = if (chat.jid in selectedChatJids) {
                            selectedChatJids - chat.jid
                        } else {
                            selectedChatJids + chat.jid
                        }
                    }
                )

                1 -> UpdatesStatusTab(
                    statuses = statuses,
                    channels = channels,
                    allChats = allChats,
                    searchQuery = searchQuery,
                    listState = updatesListState,
                    onOpenStatus = { activeStatusViewer = it },
                    onOpenChannel = { onOpenChat(it) }
                )

                2 -> CommunitiesTab(
                    allChats = allChats,
                    listState = communitiesListState,
                    onOpenGroup = onOpenChat
                )

                3 -> ToolsVaultTab(
                    viewModel = viewModel,
                    listState = toolsListState,
                    onOpenFeedback = { activeFeedbackType = it }
                )
            }
        }
    }

    quickContactChat?.let { target ->
        QuickContactDialog(
            chat = target,
            onDismiss = { quickContactTarget = null },
            onOpenChat = { onOpenChat(target.jid) },
            onOpenGhostInfo = { onSelectTab(3) },
            onOpenPresenceLog = { onSelectTab(3) },
            onOpenAvatarHistory = { onSelectTab(3) },
            onRequestFullAvatar = { viewModel.requestFullProfilePicture(target.jid) },
            onSaveAvatar = {
                val safeName = target.name.ifBlank { target.jid.substringBefore("@") }
                viewModel.exportMediaToGallery(target.avatarPath, "${safeName}_avatar.jpg", "image/jpeg")
            }
        )
    }

    if (showPairingDialog) {
        LinkCompanionDialog(
            bridgeStatus = bridgeStatus,
            onDismiss = { showPairingDialog = false },
            onPairPhone = { digits, cb -> viewModel.pairWithPhoneNumber(digits, cb) },
            onReconnect = { viewModel.reconnectSocket() }
        )
    }

    if (showQuickContactPicker) {
        NewChatPickerDialog(
            chats = allChats,
            onDismiss = { showQuickContactPicker = false },
            onSelectChat = { jid ->
                showQuickContactPicker = false
                onOpenChat(jid)
            },
            onOpenLinkedDevices = {
                showQuickContactPicker = false
                showPairingDialog = true
            }
        )
    }

    if (showThemePickerDialog) {
        ThemePickerDialog(
            selectedTheme = stealth.themeMode,
            onSelectTheme = { viewModel.setThemeMode(it) },
            onDismiss = { showThemePickerDialog = false }
        )
    }

    activeStatusViewer?.let { st ->
        val statusGroups = remember(statuses, allChats, st.id) {
            val chatsByJid = allChats.associateBy { it.jid }
            val bySender = statuses.groupBy { it.senderJid }
            val groupsList = bySender.entries.map { (senderJid, items) ->
                val sortedItems = items.sortedBy { it.timestamp }
                val senderChat = chatsByJid[senderJid]
                StatusGroup(
                    senderJid = senderJid,
                    senderName = senderChat?.name?.takeIf { it.isNotBlank() }
                        ?: sortedItems.firstOrNull()?.senderName
                        ?: senderJid.substringBefore("@"),
                    senderAvatarPath = senderChat?.avatarPath,
                    items = sortedItems
                )
            }
            groupsList.ifEmpty {
                val senderChat = chatsByJid[st.senderJid]
                listOf(
                    StatusGroup(
                        senderJid = st.senderJid,
                        senderName = senderChat?.name?.takeIf { it.isNotBlank() } ?: st.senderName,
                        senderAvatarPath = senderChat?.avatarPath,
                        items = listOf(st)
                    )
                )
            }
        }
        val initialGroupIdx = remember(statusGroups, st.senderJid) {
            statusGroups.indexOfFirst { it.senderJid == st.senderJid }.coerceAtLeast(0)
        }
        val initialStoryIdx = remember(statusGroups, initialGroupIdx, st.id) {
            statusGroups.getOrNull(initialGroupIdx)?.items?.indexOfFirst { it.id == st.id }?.coerceAtLeast(0) ?: 0
        }
        FullScreenStatusViewer(
            groups = statusGroups,
            initialGroupIndex = initialGroupIdx,
            initialStoryIndex = initialStoryIdx,
            onDismiss = { activeStatusViewer = null },
            onSaveToGallery = { target ->
                val ext = if (target.mimeType.contains("mp4", ignoreCase = true)) "mp4" else "png"
                viewModel.exportMediaToGallery(
                    target.localMediaPath,
                    "WA_Status_${target.id}.$ext",
                    target.mimeType.ifBlank { "image/png" }
                )
            },
            onReply = { target ->
                if (target.senderJid.isNotBlank()) {
                    onOpenChat(target.senderJid)
                }
            }
        )
    }

    activeVaultViewer?.let { msg ->
        val chatName = remember(allChats, msg.chatJid, msg.senderName) {
            allChats.firstOrNull { it.jid == msg.chatJid }?.name ?: msg.senderName
        }
        FullScreenMediaViewer(
            msg = msg,
            senderDisplayName = chatName,
            onDismiss = { activeVaultViewer = null },
            onSaveToGallery = {
                val ext = if (msg.mimeType.contains("mp4", ignoreCase = true)) "mp4" else "png"
                viewModel.exportMediaToGallery(
                    msg.localMediaPath,
                    "WA_ViewOnce_${msg.id}.$ext",
                    msg.mimeType.ifBlank { "image/png" }
                )
            },
            onReply = {
                if (msg.chatJid.isNotBlank()) {
                    onOpenChat(msg.chatJid)
                }
            }
        )
    }

    activeFeedbackType?.let { fbType ->
        GitHubFeedbackDialog(
            initialType = fbType,
            onDismiss = { activeFeedbackType = null }
        )
    }

    if (pendingDeleteJids.isNotEmpty()) {
        val count = pendingDeleteJids.size
        AlertDialog(
            onDismissRequest = { pendingDeleteJids = emptyList() },
            title = { Text(if (count == 1) "Delete this chat?" else "Delete $count chats?") },
            text = {
                Text(
                    "Messages for ${if (count == 1) "this conversation" else "these conversations"} will be removed from Wupp's local cache only."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteChatsLocally(pendingDeleteJids)
                        pendingDeleteJids = emptyList()
                        selectedChatJids = emptySet()
                    }
                ) {
                    Text("Delete", color = colors.antiDeleteRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteJids = emptyList() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = LocalWaColors.current.pillActiveText,
    selectedTextColor = LocalWaColors.current.textPrimary,
    indicatorColor = LocalWaColors.current.pillActiveBg,
    unselectedIconColor = LocalWaColors.current.textSecondary,
    unselectedTextColor = LocalWaColors.current.textSecondary
)

@Composable
private fun rememberAvatarBitmap(jid: String, avatarPath: String?): ImageBitmap? {
    val context = LocalContext.current
    return remember(jid, avatarPath) {
        val explicitFile = avatarPath?.takeIf { it.isNotBlank() }?.let { path ->
            File(path).takeIf { it.exists() && it.length() > 0 }
        }
        val resolvedFile = explicitFile ?: run {
            val rawUser = jid.substringBefore("@").substringBefore(":")
            val dir = File(context.filesDir, "vault/avatars")
            if (rawUser.isNotBlank() && dir.isDirectory) {
                val prefix = "${rawUser}_"
                dir.listFiles()?.firstOrNull {
                    it.isFile && it.name.startsWith(prefix) && it.length() > 0L
                }
            } else {
                null
            }
        }
        resolvedFile?.let { file ->
            runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
        }
    }
}

@Composable
internal fun WhatsAppAvatar(
    jid: String,
    avatarPath: String?,
    isGroup: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    initialChar: String? = null
) {
    val avatarBmp = rememberAvatarBitmap(jid = jid, avatarPath = avatarPath)
    val palette = remember(jid, isGroup) {
        val pairs = listOf(
            Color(0xFF4A2E2B) to Color(0xFFF2998E), // Warm terracotta brown / peach (e.g. Mom)
            Color(0xFF1E3A5F) to Color(0xFF78A9FF), // Deep slate blue / sky blue (e.g. Talha)
            Color(0xFF3E2742) to Color(0xFFD7AEFB), // Plum / lavender
            Color(0xFF1B3B36) to Color(0xFF81C995), // Deep teal / sage
            Color(0xFF423824) to Color(0xFFFDD663)  // Charcoal olive / warm amber
        )
        pairs[abs(jid.substringBefore("@").hashCode()) % pairs.size]
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isGroup) Color(0xFF1F353D) else palette.first),
        contentAlignment = Alignment.Center
    ) {
        if (avatarBmp != null) {
            Image(
                bitmap = avatarBmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isGroup) {
            Icon(
                painter = painterResource(R.drawable.vec_ic_avatar_community),
                contentDescription = null,
                tint = Color(0xFF99B2BD),
                modifier = Modifier.size(size * 0.54f)
            )
        } else if (!initialChar.isNullOrBlank()) {
            Text(
                text = initialChar,
                color = palette.second,
                fontSize = (size.value * 0.44f).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = palette.second,
                modifier = Modifier.size(size * 0.58f)
            )
        }
    }
}

@Composable
private fun ChatsTabContent(
    chats: List<ChatEntity>,
    allChats: List<ChatEntity>,
    selectedChip: HomeFilterChip,
    ftsHits: List<MessageEntity>,
    searchQuery: String,
    listState: LazyListState,
    selectedChatJids: Set<String> = emptySet(),
    onSearchChange: (String) -> Unit,
    heartbeat: com.wa.beastclient.domain.stealth.PrimaryHeartbeatStatus,
    bridgeStatus: com.wa.beastclient.core.service.BridgeStatus,
    onSelectChip: (HomeFilterChip) -> Unit,
    onResetHeartbeat: () -> Unit,
    onSeedShowcase: () -> Unit,
    onAvatarClick: (ChatEntity) -> Unit,
    onChatClick: (ChatEntity) -> Unit,
    onChatLongClick: (ChatEntity) -> Unit
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    var showExtraFilters by remember { mutableStateOf(false) }

    val nonCommunityChats = remember(allChats) {
        allChats.filter {
            !it.isCommunityParent &&
                !it.jid.endsWith("@newsletter") &&
                !(it.name.startsWith("@") && it.lastMessagePreview.isBlank() && (it.lastMessageType == "UNKNOWN" || it.lastMessageType.isBlank()))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        // Inline "Ask Meta AI or Search" pill bar (matching Original WhatsApp)
        item(key = "inline_search_bar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.elevatedBar)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.vec_ic_search),
                    contentDescription = "Search",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    singleLine = true,
                    textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.5.sp),
                    cursorBrush = SolidColor(colors.brightGreen),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Ask Meta AI or Search",
                                color = colors.textSecondary,
                                fontSize = 15.5.sp
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier.weight(1f)
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchChange("") },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 14-Day Primary Heartbeat countdown warning banner (>= 10 days elapsed)
        if (heartbeat.isWarningActive) {
            item(key = "heartbeat_warning") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.warningAmber.copy(alpha = 0.18f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Warning, null, tint = colors.warningAmber)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "14-Day Primary Heartbeat Alert",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                            Text(
                                text = "${heartbeat.elapsedDays}d elapsed · ${heartbeat.remainingDays}d (${heartbeat.remainingHours}h) before Meta unlinks companions. Open official WhatsApp on primary device.",
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        TextButton(onClick = onResetHeartbeat) {
                            Text("Reset", color = colors.accentGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Filter Chips Row (All / Unread N / Favorites / Groups N / +)
        item(key = "filter_chips") {
            val visibleChips = remember(showExtraFilters) {
                if (showExtraFilters) {
                    listOf(
                        HomeFilterChip.ALL,
                        HomeFilterChip.UNREAD,
                        HomeFilterChip.FAVORITES,
                        HomeFilterChip.GROUPS,
                        HomeFilterChip.DELETED,
                        HomeFilterChip.EDITED
                    )
                } else {
                    listOf(
                        HomeFilterChip.ALL,
                        HomeFilterChip.UNREAD,
                        HomeFilterChip.FAVORITES,
                        HomeFilterChip.GROUPS
                    )
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleChips) { chip ->
                    val count = when (chip) {
                        HomeFilterChip.ALL -> nonCommunityChats.size
                        HomeFilterChip.UNREAD -> nonCommunityChats.count { it.unreadCount > 0 }
                        HomeFilterChip.FAVORITES -> nonCommunityChats.count { it.isFavorite }
                        HomeFilterChip.GROUPS -> nonCommunityChats.count { it.isGroup }
                        HomeFilterChip.DELETED -> nonCommunityChats.count { it.hasDeletedMessages || it.lastMessageIsDeleted }
                        HomeFilterChip.EDITED -> nonCommunityChats.count { it.hasEditedMessages }
                    }
                    val active = chip == selectedChip
                    val borderColor = if (active) {
                        colors.pillActiveText.copy(alpha = 0.35f)
                    } else {
                        colors.textSecondary.copy(alpha = 0.28f)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (active) colors.pillActiveBg else Color.Transparent)
                            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                            .clickable { onSelectChip(chip) }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        val chipLabel = when (chip) {
                            HomeFilterChip.UNREAD -> if (count > 0) "Unread $count" else "Unread"
                            HomeFilterChip.GROUPS -> if (count > 0) "Groups $count" else "Groups"
                            else -> chip.label
                        }
                        Text(
                            text = chipLabel,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (active) colors.pillActiveText else colors.textSecondary
                        )
                    }
                }
                item(key = "filter_plus_chip") {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (showExtraFilters) colors.pillActiveBg else Color.Transparent)
                            .border(
                                1.dp,
                                if (showExtraFilters) colors.pillActiveText.copy(alpha = 0.35f) else colors.textSecondary.copy(alpha = 0.28f),
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { showExtraFilters = !showExtraFilters }
                            .padding(horizontal = 13.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (showExtraFilters) Icons.Rounded.Close else Icons.Rounded.Add,
                            contentDescription = "More filters",
                            tint = if (showExtraFilters) colors.pillActiveText else colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // FTS4 Search Matches when typing search query
        if (searchQuery.trim().length >= 2 && ftsHits.isNotEmpty()) {
            item(key = "fts_header") {
                Text(
                    text = "FTS4 Message Matches (${ftsHits.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accentGreen,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            items(ftsHits.take(6), key = { "fts_${it.id}" }) { hit ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChatClick(ChatEntity(jid = hit.chatJid, name = hit.senderName)) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = hit.senderName.ifBlank { hit.chatJid.substringBefore("@") },
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        )
                        Text(
                            text = hit.body.ifBlank { hit.audioTranscript.orEmpty() },
                            color = colors.textSecondary,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = formatMessageClock(hit.timestamp),
                        fontFamily = mono,
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }
            item { HorizontalDivider(color = colors.divider) }
        }

        // Empty state
        if (chats.isEmpty()) {
            item(key = "empty_state") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (bridgeStatus.connectionState == "CONNECTED") "No chats yet" else "Start chatting on Wupp",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your synced conversations will appear here automatically.",
                        color = colors.textSecondary,
                        fontSize = 13.5.sp
                    )
                }
            }
        } else {
            items(chats, key = { it.jid }) { chat ->
                ChatListRow(
                    chat = chat,
                    selected = chat.jid in selectedChatJids,
                    onAvatarClick = { onAvatarClick(chat) },
                    onRowClick = { onChatClick(chat) },
                    onRowLongClick = { onChatLongClick(chat) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListRow(
    chat: ChatEntity,
    selected: Boolean = false,
    onAvatarClick: () -> Unit,
    onRowClick: () -> Unit,
    onRowLongClick: () -> Unit = {}
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    val rowBg = if (selected) colors.brightGreen.copy(alpha = 0.16f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .background(rowBg)
            .combinedClickable(
                onClick = onRowClick,
                onLongClick = onRowLongClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .combinedClickable(
                    onClick = onAvatarClick,
                    onLongClick = onRowLongClick
                )
        ) {
            WhatsAppAvatar(
                jid = chat.jid,
                avatarPath = chat.avatarPath,
                isGroup = chat.isGroup,
                size = 52.dp
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(21.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(colors.brightGreen)
                        .border(2.dp, colors.appSurface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = Color(0xFF0B141A),
                        modifier = Modifier.size(13.dp)
                    )
                }
            } else if (chat.isOnline) {
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(colors.brightGreen)
                        .border(2.dp, colors.appSurface, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.name.ifBlank { chat.jid.substringBefore("@") },
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatMessageClock(chat.lastMessageTimestamp),
                    fontFamily = mono,
                    fontSize = 11.5.sp,
                    color = if (chat.unreadCount > 0) colors.brightGreen else colors.textSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isViewOncePreview = chat.lastMessageType.startsWith("VIEW_ONCE") ||
                    chat.lastMessagePreview.startsWith("View once")

                if (chat.chatPresence.isBlank()) {
                    if (chat.lastMessageIsDeleted) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_delete_sweep),
                            contentDescription = null,
                            tint = colors.antiDeleteRed,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(14.dp)
                        )
                    } else if (chat.lastMessageFromMe) {
                        DeliveryTickIcon(
                            status = chat.lastMessageStatus,
                            mutedColor = colors.textSecondary,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    if (!chat.lastMessageIsDeleted && isViewOncePreview) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_view_once),
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier
                                .padding(end = 5.dp)
                                .size(15.dp)
                        )
                    }
                }

                val fallbackPreview = when (chat.lastMessageType) {
                    "PTT" -> "🎤 Voice message"
                    "AUDIO" -> "🎵 Audio"
                    "IMAGE" -> "📷 Photo"
                    "VIDEO" -> "🎥 Video"
                    "STICKER" -> "💟 Sticker"
                    "DOCUMENT" -> "📄 Document"
                    else -> ""
                }
                val subtitle = when {
                    chat.chatPresence.isNotBlank() -> chat.chatPresence
                    isViewOncePreview && !chat.lastMessageIsDeleted -> when {
                        chat.lastMessagePreview.startsWith("Opened") -> chat.lastMessagePreview
                        chat.lastMessageType.endsWith("VIDEO") -> "Video"
                        chat.lastMessageType.endsWith("AUDIO") || chat.lastMessageType.endsWith("PTT") -> "Voice message"
                        else -> "Photo"
                    }
                    chat.lastMessagePreview.isNotBlank() -> chat.lastMessagePreview
                    else -> fallbackPreview
                }
                Text(
                    text = subtitle,
                    color = when {
                        chat.chatPresence.isNotBlank() -> colors.brightGreen
                        chat.lastMessageIsDeleted -> colors.antiDeleteRed
                        else -> colors.textSecondary
                    },
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (chat.isMuted) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_mute_list),
                            contentDescription = "Muted",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (chat.isPinned) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_pin),
                            contentDescription = "Pinned",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (chat.unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.brightGreen)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (chat.unreadCount > 999) "999+" else chat.unreadCount.toString(),
                                color = if (colors.isDark) Color(0xFF0B141A) else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = mono
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatesStatusTab(
    statuses: List<StatusEntity>,
    channels: List<ChatEntity>,
    allChats: List<ChatEntity>,
    searchQuery: String,
    listState: LazyListState,
    onOpenStatus: (StatusEntity) -> Unit,
    onOpenChannel: (String) -> Unit
) {
    val colors = LocalWaColors.current
    val chatsByJid = remember(allChats) { allChats.associateBy { it.jid } }
    val groupedStatuses = remember(statuses, searchQuery) {
        val q = searchQuery.trim().lowercase()
        val grouped = statuses.groupBy { it.senderJid }.values.map { it.first() }
        if (q.isEmpty()) grouped
        else grouped.filter { st ->
            val name = chatsByJid[st.senderJid]?.name?.ifBlank { st.senderName } ?: st.senderName
            name.lowercase().contains(q) || st.caption.lowercase().contains(q)
        }
    }
    val filteredChannels = remember(channels, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) channels
        else channels.filter {
            it.name.lowercase().contains(q) ||
                it.lastMessagePreview.lowercase().contains(q) ||
                it.aboutText.lowercase().contains(q)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 112.dp)
    ) {
        item(key = "updates_status_header") {
            Text(
                text = "Status",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = colors.textPrimary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 10.dp)
            )
        }

        item(key = "updates_status_cards_row") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "add_status_card") {
                    AddStatusStoryCard()
                }
                items(groupedStatuses, key = { "status_card_${it.id}" }) { st ->
                    val senderChat = chatsByJid[st.senderJid]
                    StatusStoryCard(
                        status = st,
                        senderChat = senderChat,
                        onClick = { onOpenStatus(st) }
                    )
                }
            }
        }

        item(key = "updates_channels_header") {
            Text(
                text = "Channels",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = colors.textPrimary,
                modifier = Modifier.padding(start = 16.dp, top = 22.dp, end = 16.dp, bottom = 6.dp)
            )
        }

        if (filteredChannels.isEmpty()) {
            item(key = "updates_channels_empty") {
                Text(
                    text = "Subscribed channels will appear here once synced.",
                    color = colors.textSecondary,
                    fontSize = 13.5.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
        } else {
            items(filteredChannels, key = { "channel_${it.jid}" }) { ch ->
                ChannelListRow(
                    channel = ch,
                    onClick = { onOpenChannel(ch.jid) }
                )
            }
        }
    }
}

@Composable
private fun AddStatusStoryCard() {
    val colors = LocalWaColors.current
    Box(
        modifier = Modifier
            .width(100.dp)
            .height(162.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.elevatedBar)
            .border(0.5.dp, colors.textSecondary.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4A2531)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = Color(0xFFF48FB1),
                    modifier = Modifier.size(32.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(colors.brightGreen)
                    .border(2.dp, colors.elevatedBar, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add status",
                    tint = Color(0xFF0B141A),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Text(
            text = "Add status",
            color = colors.textPrimary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun StatusStoryCard(
    status: StatusEntity,
    senderChat: ChatEntity?,
    onClick: () -> Unit
) {
    val colors = LocalWaColors.current
    val mediaBmp = remember(status.localMediaPath) {
        status.localMediaPath?.let { path ->
            File(path).takeIf { it.exists() && it.length() > 0 }
                ?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }.getOrNull() }
        }
    }
    val displayName = senderChat?.name?.takeIf { it.isNotBlank() } ?: status.senderName.ifBlank {
        status.senderJid.substringBefore("@")
    }

    Box(
        modifier = Modifier
            .width(100.dp)
            .height(162.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1A3438))
            .clickable(onClick = onClick)
    ) {
        if (mediaBmp != null) {
            Image(
                bitmap = mediaBmp,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (status.caption.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 34.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = status.caption,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 11.5.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.28f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.72f)
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .padding(8.dp)
                .size(40.dp)
                .align(Alignment.TopStart)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (status.isRevoked) colors.antiDeleteRed else colors.brightGreen,
                    shape = CircleShape
                )
                .padding(3.dp)
        ) {
            WhatsAppAvatar(
                jid = status.senderJid,
                avatarPath = senderChat?.avatarPath,
                isGroup = false,
                size = 34.dp
            )
        }

        Text(
            text = displayName,
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ChannelListRow(
    channel: ChatEntity,
    onClick: () -> Unit
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WhatsAppAvatar(
            jid = channel.jid,
            avatarPath = channel.avatarPath,
            isGroup = false,
            size = 52.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = channel.name.ifBlank { channel.jid.substringBefore("@") },
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (channel.lastMessageTimestamp > 0L) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatMessageClock(channel.lastMessageTimestamp),
                        fontFamily = mono,
                        fontSize = 11.5.sp,
                        color = if (channel.unreadCount > 0) colors.brightGreen else colors.textSecondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = channel.lastMessagePreview.ifBlank {
                        channel.aboutText.ifBlank { "Channel updates" }
                    },
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (channel.isPinned) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_pin),
                            contentDescription = "Pinned",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (channel.unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.brightGreen)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (channel.unreadCount > 999) "999+" else channel.unreadCount.toString(),
                                color = if (colors.isDark) Color(0xFF0B141A) else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = mono
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunitiesTab(
    allChats: List<ChatEntity>,
    listState: LazyListState,
    onOpenGroup: (String) -> Unit
) {
    val colors = LocalWaColors.current
    var expandedCommunities by remember { mutableStateOf(setOf<String>()) }

    val groups = remember(allChats) { allChats.filter { it.isGroup } }
    val parentByJid = remember(groups) {
        groups.filter { it.isCommunityParent }.associateBy { it.jid }
    }
    val childrenByParent = remember(groups) {
        groups.filter { !it.isCommunityParent && it.parentGroupJid.isNotBlank() }
            .groupBy { it.parentGroupJid }
            .mapValues { (_, list) ->
                list.sortedWith(
                    compareByDescending<ChatEntity> { it.isDefaultSubgroup }
                        .thenByDescending { it.lastMessageTimestamp }
                        .thenBy { it.name.lowercase() }
                )
            }
    }
    val communityJids = remember(parentByJid, childrenByParent) {
        (parentByJid.keys + childrenByParent.keys).distinct().sortedByDescending { jid ->
            val parentTs = parentByJid[jid]?.lastMessageTimestamp ?: 0L
            val childTs = childrenByParent[jid]?.maxOfOrNull { it.lastMessageTimestamp } ?: 0L
            maxOf(parentTs, childTs)
        }
    }
    val standaloneGroups = remember(groups) {
        groups.filter { !it.isCommunityParent && it.parentGroupJid.isBlank() }
            .sortedWith(
                compareByDescending<ChatEntity> { it.lastMessageTimestamp }
                    .thenBy { it.name.lowercase() }
            )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 84.dp)
    ) {
        item(key = "new_community_row") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(48.dp)) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.textSecondary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_avatar_community),
                            contentDescription = "New community",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(colors.accentGreen)
                            .border(1.5.dp, colors.appSurface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "New community",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(colors.elevatedBar.copy(alpha = 0.45f))
            )
        }

        if (communityJids.isEmpty()) {
            item(key = "communities_empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.accentGreen.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.vec_ic_avatar_community),
                            contentDescription = null,
                            tint = colors.accentGreen,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Stay connected with a community",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Communities bring members together in topic-based groups, and make it easy to get admin announcements.",
                        color = colors.textSecondary,
                        fontSize = 13.5.sp
                    )
                }
            }
        }

        communityJids.forEach { parentJid ->
            val parentChat = parentByJid[parentJid]
            val children = childrenByParent[parentJid].orEmpty()
            val isExpanded = parentJid in expandedCommunities
            val visibleChildren = if (isExpanded || children.size <= 3) children else children.take(3)
            val communityTitle = parentChat?.name?.takeIf {
                it.isNotBlank() && it != parentJid && it != parentJid.substringBefore("@")
            } ?: "Community"

            item(key = "comm_header_$parentJid") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val announcement = children.firstOrNull { it.isDefaultSubgroup } ?: children.firstOrNull()
                            if (announcement != null) {
                                onOpenGroup(announcement.jid)
                            } else if (parentChat != null) {
                                onOpenGroup(parentChat.jid)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CommunityAvatarBox(
                        avatarPath = parentChat?.avatarPath,
                        isRoundedSquare = true,
                        isAnnouncement = false,
                        sizeDp = 46
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = communityTitle,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val topic = parentChat?.aboutText.orEmpty()
                        if (topic.isNotBlank()) {
                            Text(
                                text = topic,
                                color = colors.textSecondary,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(start = 76.dp, end = 16.dp),
                    thickness = 0.5.dp,
                    color = colors.textSecondary.copy(alpha = 0.14f)
                )
            }

            items(visibleChildren, key = { "comm_${parentJid}_sub_${it.jid}" }) { sub ->
                CommunityGroupRow(
                    chat = sub,
                    onClick = { onOpenGroup(sub.jid) }
                )
            }

            if (children.size > 3) {
                item(key = "comm_more_$parentJid") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedCommunities = if (isExpanded) {
                                    expandedCommunities - parentJid
                                } else {
                                    expandedCommunities + parentJid
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = colors.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = if (isExpanded) "Show less" else "View all (${children.size})",
                            color = colors.textSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.5.sp
                        )
                    }
                }
            }

            item(key = "comm_sep_$parentJid") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(colors.elevatedBar.copy(alpha = 0.45f))
                )
            }
        }

        if (standaloneGroups.isNotEmpty()) {
            item(key = "standalone_groups_header") {
                Text(
                    text = "Groups",
                    color = colors.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 6.dp)
                )
            }
            items(standaloneGroups, key = { "standalone_${it.jid}" }) { g ->
                CommunityGroupRow(
                    chat = g,
                    onClick = { onOpenGroup(g.jid) }
                )
            }
        }
    }
}

@Composable
private fun CommunityGroupRow(
    chat: ChatEntity,
    onClick: () -> Unit
) {
    val colors = LocalWaColors.current
    val title = if (chat.isDefaultSubgroup && (chat.name.isBlank() || chat.name == chat.jid.substringBefore("@"))) {
        "Announcements"
    } else {
        chat.name.ifBlank { chat.jid.substringBefore("@") }
    }
    val subtitle = chat.lastMessagePreview.ifBlank {
        chat.aboutText.ifBlank { if (chat.isDefaultSubgroup) "Announcements" else "" }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CommunityAvatarBox(
            avatarPath = chat.avatarPath,
            isRoundedSquare = chat.isDefaultSubgroup,
            isAnnouncement = chat.isDefaultSubgroup,
            sizeDp = 44
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (chat.lastMessageTimestamp > 0L) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatMessageClock(chat.lastMessageTimestamp),
                        color = if (chat.unreadCount > 0) colors.accentGreen else colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            if (subtitle.isNotBlank() || chat.unreadCount > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = subtitle,
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (chat.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(colors.accentGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = chat.unreadCount.toString(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityAvatarBox(
    avatarPath: String?,
    isRoundedSquare: Boolean,
    isAnnouncement: Boolean,
    sizeDp: Int
) {
    val colors = LocalWaColors.current
    val shape = if (isRoundedSquare) RoundedCornerShape(12.dp) else CircleShape
    val avatarBmp = remember(avatarPath) {
        avatarPath?.takeIf { it.isNotBlank() }?.let { path ->
            File(path).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
        }
    }

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(shape)
            .background(colors.accentGreen.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        if (avatarBmp != null && !isAnnouncement) {
            Image(
                bitmap = avatarBmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (isAnnouncement) {
            Icon(
                imageVector = Icons.Rounded.Campaign,
                contentDescription = null,
                tint = colors.accentGreen,
                modifier = Modifier.size((sizeDp * 0.52f).dp)
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.vec_ic_avatar_community),
                contentDescription = null,
                tint = colors.accentGreen,
                modifier = Modifier.size((sizeDp * 0.54f).dp)
            )
        }
    }
}

@Composable
private fun ToolsVaultTab(
    viewModel: MainViewModel,
    listState: LazyListState,
    onOpenFeedback: (GitHubFeedbackType) -> Unit
) {
    val context = LocalContext.current
    val stealth by viewModel.stealthConfig.collectAsState()
    val allChats by viewModel.allChats.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val autoUpdateEnabled by viewModel.autoUpdateEnabled.collectAsState()
    var editingAutoDlNetwork by remember { mutableStateOf<AutoDownloadNetwork?>(null) }
    var showGalleryContactsDialog by remember { mutableStateOf(false) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "patch_section_privacy") {
            PatchSectionCard(title = "Privacy & Ghost Mode") {
                PatchToggleRow(
                    icon = Icons.Rounded.VisibilityOff,
                    title = "Ghost Mode (Freeze Last Seen)",
                    subtitle = "Hide online status and freeze your last seen timestamp",
                    checked = stealth.ghostPresence,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(ghostPresence = it) }
                    }
                )
                PatchRowDivider()
                PatchToggleRow(
                    icon = Icons.Rounded.CheckCircle,
                    title = "Hide Read Receipts",
                    subtitle = "Read messages privately without sending blue ticks",
                    checked = stealth.hideReadReceipts,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(hideReadReceipts = it) }
                    }
                )
                PatchRowDivider()
                PatchToggleRow(
                    icon = Icons.Rounded.Campaign,
                    title = "Hide Played Receipts",
                    subtitle = "Play voice notes and audio without marking them played",
                    checked = stealth.hidePlayedReceipts,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(hidePlayedReceipts = it) }
                    }
                )
                PatchRowDivider()
                PatchToggleRow(
                    icon = Icons.Rounded.Shield,
                    title = "Hide Typing & Recording",
                    subtitle = "Suppress typing and recording audio indicators in chats",
                    checked = stealth.hideTypingRecording,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(hideTypingRecording = it) }
                    }
                )
            }
        }

        item(key = "patch_section_media") {
            PatchSectionCard(title = "Message & Media Patches") {
                PatchToggleRow(
                    icon = Icons.Rounded.DeleteSweep,
                    title = "Anti-Delete Messages & Statuses",
                    subtitle = "Keep messages, media, and statuses deleted by everyone",
                    checked = stealth.antiDeleteEnabled,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(antiDeleteEnabled = it) }
                    }
                )
                PatchRowDivider()
                PatchToggleRow(
                    icon = Icons.Rounded.Lock,
                    title = "Disappearing Message Immunity",
                    subtitle = "Prevent ephemeral timers from expiring messages in chats",
                    checked = stealth.disappearingImmunity,
                    onCheckedChange = {
                        viewModel.updateGlobalStealth { c -> c.copy(disappearingImmunity = it) }
                    }
                )
            }
        }

        item(key = "patch_section_storage_media") {
            PatchSectionCard(title = "Storage & Media Auto-Download") {
                PatchToggleRow(
                    icon = Icons.Rounded.CloudDownload,
                    title = "Media auto-download",
                    subtitle = if (stealth.autoDownloadEnabled) {
                        "Automatically download incoming photos, audio, videos, and files"
                    } else {
                        "Off — tap media items in chats to download manually"
                    },
                    checked = stealth.autoDownloadEnabled,
                    onCheckedChange = { viewModel.setAutoDownloadEnabled(it) }
                )
                PatchRowDivider()
                PatchActionRow(
                    icon = Icons.Rounded.SignalCellularAlt,
                    title = "When using mobile data",
                    subtitle = if (stealth.autoDownloadEnabled) {
                        StealthMatrixManager.formatAutoDownloadSummary(stealth.autoDownloadMobileMask)
                    } else {
                        "Disabled (auto-download is off)"
                    },
                    onClick = { editingAutoDlNetwork = AutoDownloadNetwork.MOBILE }
                )
                PatchRowDivider()
                PatchActionRow(
                    icon = Icons.Rounded.Wifi,
                    title = "When connected on Wi-Fi",
                    subtitle = if (stealth.autoDownloadEnabled) {
                        StealthMatrixManager.formatAutoDownloadSummary(stealth.autoDownloadWifiMask)
                    } else {
                        "Disabled (auto-download is off)"
                    },
                    onClick = { editingAutoDlNetwork = AutoDownloadNetwork.WIFI }
                )
                PatchRowDivider()
                PatchActionRow(
                    icon = Icons.Rounded.Flight,
                    title = "When roaming",
                    subtitle = if (stealth.autoDownloadEnabled) {
                        StealthMatrixManager.formatAutoDownloadSummary(stealth.autoDownloadRoamingMask)
                    } else {
                        "Disabled (auto-download is off)"
                    },
                    onClick = { editingAutoDlNetwork = AutoDownloadNetwork.ROAMING }
                )
                PatchRowDivider()
                PatchToggleRow(
                    icon = Icons.Rounded.Collections,
                    title = "Media visibility",
                    subtitle = "Show newly downloaded media in your device's gallery",
                    checked = stealth.mediaVisibilityInGallery,
                    onCheckedChange = { viewModel.setMediaVisibilityInGallery(it) }
                )
                PatchRowDivider()
                PatchActionRow(
                    icon = Icons.Rounded.VisibilityOff,
                    title = "Hide contact media from gallery",
                    subtitle = if (stealth.hiddenGalleryJids.isEmpty()) {
                        "Choose specific contacts or groups to keep out of gallery"
                    } else {
                        "${stealth.hiddenGalleryJids.size} contact(s)/group(s) hidden from gallery"
                    },
                    onClick = { showGalleryContactsDialog = true }
                )
            }
        }

        item(key = "patch_section_appearance") {
            PatchSectionCard(title = "Appearance") {
                val colors = LocalWaColors.current
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = colors.accentGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App Theme",
                                color = colors.textPrimary,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${stealth.themeMode.label} — ${stealth.themeMode.tagline}",
                                color = colors.textSecondary,
                                fontSize = 12.5.sp
                            )
                        }
                    }
                    ThemeSelectorGrid(
                        selectedTheme = stealth.themeMode,
                        onSelectTheme = { viewModel.setThemeMode(it) }
                    )
                }
            }
        }

        item(key = "patch_section_updates_support") {
            val updateSubtitle = when (val st = updateState) {
                is UpdateUiState.Checking -> "Checking GitHub Releases..."
                is UpdateUiState.UpToDate -> "Up to date (v${st.currentVersion})"
                is UpdateUiState.UpdateAvailable -> "New update available: ${st.release.tagName}"
                is UpdateUiState.Downloading -> {
                    if (st.progressPercent >= 0) "Downloading ${st.release.tagName} (${st.progressPercent}%)"
                    else "Downloading ${st.release.tagName}..."
                }
                is UpdateUiState.ReadyToInstall -> "${st.release.tagName} ready — tap to install"
                is UpdateUiState.Error -> if (!st.silent) st.message else "Installed v${BuildConfig.VERSION_NAME}"
                UpdateUiState.Idle -> "Installed v${BuildConfig.VERSION_NAME}"
            }
            val updateBadge = when (updateState) {
                is UpdateUiState.UpdateAvailable -> "NEW"
                is UpdateUiState.ReadyToInstall -> "INSTALL"
                else -> null
            }

            PatchSectionCard(title = "Updates & GitHub Support") {
                if (BuildConfig.ENABLE_SELF_UPDATE) {
                    PatchActionRow(
                        icon = Icons.Rounded.SystemUpdate,
                        title = "Check for Updates",
                        subtitle = updateSubtitle,
                        badgeText = updateBadge,
                        onClick = {
                            when (updateState) {
                                is UpdateUiState.UpdateAvailable,
                                is UpdateUiState.Downloading,
                                is UpdateUiState.ReadyToInstall -> viewModel.openUpdatePrompt()
                                else -> viewModel.checkForUpdates(silent = false)
                            }
                        }
                    )
                    PatchRowDivider()
                    PatchToggleRow(
                        icon = Icons.Rounded.CheckCircle,
                        title = "Auto-Check for Updates",
                        subtitle = "Notify and prompt to download when a new GitHub release ships",
                        checked = autoUpdateEnabled,
                        onCheckedChange = { viewModel.setAutoUpdateEnabled(it) }
                    )
                    PatchRowDivider()
                } else {
                    PatchActionRow(
                        icon = Icons.Rounded.SystemUpdate,
                        title = "App Updates",
                        subtitle = "Installed v${BuildConfig.VERSION_NAME} • Updates delivered via F-Droid",
                        onClick = { }
                    )
                    PatchRowDivider()
                }
                PatchActionRow(
                    icon = Icons.Rounded.BugReport,
                    title = "Report a Bug / Issue",
                    subtitle = "Open a pre-filled bug report on GitHub Issues",
                    onClick = { onOpenFeedback(GitHubFeedbackType.BUG) }
                )
                PatchRowDivider()
                PatchActionRow(
                    icon = Icons.Rounded.Lightbulb,
                    title = "Request a Feature",
                    subtitle = "Suggest improvements or new capabilities",
                    onClick = { onOpenFeedback(GitHubFeedbackType.FEATURE) }
                )
                PatchRowDivider()
                PatchActionRow(
                    icon = Icons.Rounded.ChatBubbleOutline,
                    title = "Give Feedback",
                    subtitle = "Share general thoughts directly to GitHub",
                    onClick = { onOpenFeedback(GitHubFeedbackType.FEEDBACK) }
                )
                PatchRowDivider()
                PatchActionRow(
                    icon = Icons.Rounded.Code,
                    title = "GitHub Repository",
                    subtitle = "${BuildConfig.GITHUB_REPO} • v${BuildConfig.VERSION_NAME}",
                    onClick = {
                        val repo = BuildConfig.GITHUB_REPO.trim().trim('/')
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$repo"))
                            )
                        }
                    }
                )
            }
        }

        item(key = "patch_bottom_spacer") {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    editingAutoDlNetwork?.let { net ->
        val mask = when (net) {
            AutoDownloadNetwork.MOBILE -> stealth.autoDownloadMobileMask
            AutoDownloadNetwork.WIFI -> stealth.autoDownloadWifiMask
            AutoDownloadNetwork.ROAMING -> stealth.autoDownloadRoamingMask
        }
        AutoDownloadNetworkDialog(
            network = net,
            initialMask = mask,
            onDismiss = { editingAutoDlNetwork = null },
            onConfirmMask = { updatedMask ->
                viewModel.setAutoDownloadMask(net, updatedMask)
            }
        )
    }

    if (showGalleryContactsDialog) {
        GalleryVisibilityContactsDialog(
            chats = allChats,
            hiddenJids = stealth.hiddenGalleryJids,
            onToggleHideContact = { jid, hide ->
                viewModel.setChatMediaVisibility(jid, if (hide) false else null)
            },
            onDismiss = { showGalleryContactsDialog = false }
        )
    }
}

@Composable
private fun PatchSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val colors = LocalWaColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = colors.accentGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.elevatedBar),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun PatchRowDivider() {
    val colors = LocalWaColors.current
    HorizontalDivider(
        modifier = Modifier.padding(start = 66.dp, end = 16.dp),
        thickness = 0.5.dp,
        color = colors.textSecondary.copy(alpha = 0.14f)
    )
}

@Composable
private fun PatchToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalWaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    if (checked) colors.accentGreen.copy(alpha = 0.16f)
                    else colors.textSecondary.copy(alpha = 0.10f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (checked) colors.accentGreen else colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = colors.textSecondary,
                fontSize = 12.5.sp,
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accentGreen,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.pillInactiveBg,
                uncheckedBorderColor = colors.textSecondary.copy(alpha = 0.35f)
            )
        )
    }
}

@Composable
private fun PatchActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    val colors = LocalWaColors.current
    val highlighted = !badgeText.isNullOrBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    if (highlighted) colors.accentGreen.copy(alpha = 0.16f)
                    else colors.textSecondary.copy(alpha = 0.10f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (highlighted) colors.accentGreen else colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = colors.textSecondary,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!badgeText.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.accentGreen)
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badgeText,
                    color = Color(0xFF0B141A),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun NewChatPickerDialog(
    chats: List<ChatEntity>,
    onDismiss: () -> Unit,
    onSelectChat: (String) -> Unit,
    onOpenLinkedDevices: () -> Unit
) {
    val colors = LocalWaColors.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(chats, query) {
        val q = query.trim().lowercase()
        val base = chats.filter { !it.isGroup && !it.isCommunityParent && !it.jid.endsWith("@newsletter") }
        if (q.isEmpty()) base.take(30)
        else base.filter { it.name.lowercase().contains(q) || it.jid.lowercase().contains(q) }.take(30)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        title = { Text("Select contact", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search name or number...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(modifier = Modifier.height(260.dp)) {
                    items(filtered, key = { it.jid }) { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectChat(c.jid) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WhatsAppAvatar(
                                jid = c.jid,
                                avatarPath = c.avatarPath,
                                isGroup = false,
                                size = 36.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = c.name.ifBlank { c.jid.substringBefore("@") },
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (c.aboutText.isNotBlank()) {
                                    Text(
                                        text = c.aboutText,
                                        color = colors.textSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenLinkedDevices) {
                Text("Linked devices", color = colors.accentGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun LinkCompanionDialog(
    bridgeStatus: com.wa.beastclient.core.service.BridgeStatus,
    onDismiss: () -> Unit,
    onPairPhone: (String, (String) -> Unit) -> Unit,
    onReconnect: () -> Unit
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    var phoneInput by remember { mutableStateOf("") }
    var generatedCode by remember(bridgeStatus.latestPairCode) { mutableStateOf(bridgeStatus.latestPairCode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.wupp_logo),
                    contentDescription = "Wupp",
                    modifier = Modifier.size(28.dp)
                )
                Text("Link Companion Device", color = colors.textPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Engine: ${bridgeStatus.connectionState}" +
                        if (bridgeStatus.ownJid.isNotBlank()) " (${bridgeStatus.ownJid})" else "",
                    color = colors.accentGreen,
                    fontFamily = mono,
                    fontSize = 12.sp
                )
                Text(
                    text = "Enter your full phone number with country code (digits only, e.g. 15551234567) to request an 8-character Wupp companion link code:",
                    color = colors.textSecondary,
                    fontSize = 12.5.sp
                )
                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it },
                    label = { Text("Phone number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!generatedCode.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.pillActiveBg, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Pair Code: $generatedCode",
                            fontFamily = mono,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.pillActiveText
                        )
                    }
                }
                if (!bridgeStatus.latestQrCode.isNullOrBlank()) {
                    Text(
                        text = "QR Token ready (${bridgeStatus.latestQrCode.take(28)}...)",
                        fontFamily = mono,
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPairPhone(phoneInput) { generatedCode = it } },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen)
            ) {
                Text("Get Pair Code", color = Color.White)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReconnect) { Text("Reconnect") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
private fun VaultMediaPreviewDialog(
    title: String,
    subtitle: String,
    localPath: String?,
    onDismiss: () -> Unit,
    onExport: () -> Unit
) {
    val colors = LocalWaColors.current
    val bmp = remember(localPath) {
        localPath?.let { path ->
            File(path).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        title = { Text(title, color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = colors.textSecondary, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onExport,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen)
            ) {
                Text("Save to Gallery", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

fun formatFullDateTime(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    return SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(epochMs))
}
