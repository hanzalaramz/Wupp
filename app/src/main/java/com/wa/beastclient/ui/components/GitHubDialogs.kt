package com.wa.beastclient.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.BuildConfig
import com.wa.beastclient.R
import com.wa.beastclient.core.update.AppReleaseInfo
import com.wa.beastclient.core.update.UpdateUiState
import com.wa.beastclient.core.update.WuppUpdater
import com.wa.beastclient.ui.theme.LocalWaColors
import java.io.File
import java.net.URLEncoder

enum class GitHubFeedbackType(
    val label: String,
    val templateFile: String,
    val githubLabel: String,
    val defaultTitlePrefix: String,
    val placeholder: String
) {
    BUG(
        label = "Bug",
        templateFile = "bug_report.yml",
        githubLabel = "bug",
        defaultTitlePrefix = "[Bug] ",
        placeholder = "What broke, and what did you expect to happen? Steps to reproduce help a lot."
    ),
    FEATURE(
        label = "Feature",
        templateFile = "feature_request.yml",
        githubLabel = "enhancement",
        defaultTitlePrefix = "[Feature] ",
        placeholder = "Describe the feature, how it should behave, or which WhatsApp feature it improves."
    ),
    FEEDBACK(
        label = "Feedback",
        templateFile = "feedback.yml",
        githubLabel = "feedback",
        defaultTitlePrefix = "[Feedback] ",
        placeholder = "Share UI/UX thoughts, performance feedback, or suggestions for Wupp."
    )
}

@Composable
fun GitHubFeedbackDialog(
    initialType: GitHubFeedbackType,
    onDismiss: () -> Unit
) {
    val colors = LocalWaColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var selectedType by remember(initialType) { mutableStateOf(initialType) }
    var titleText by remember { mutableStateOf("") }
    var detailsText by remember { mutableStateOf("") }
    var includeDiagnostics by remember { mutableStateOf(true) }

    val diagnosticsBlock = remember {
        buildString {
            appendLine("---")
            appendLine("**Environment**")
            appendLine("- App: Wupp v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
            appendLine("- Repo: `${BuildConfig.GITHUB_REPO}`")
            appendLine("- Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            append("- Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        }
    }

    fun buildFullBody(): String {
        val core = detailsText.trim().ifBlank { "_Describe details here..._" }
        return if (includeDiagnostics) "$core\n\n$diagnosticsBlock" else core
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (selectedType) {
                        GitHubFeedbackType.BUG -> Icons.Rounded.BugReport
                        GitHubFeedbackType.FEATURE -> Icons.Rounded.Lightbulb
                        GitHubFeedbackType.FEEDBACK -> Icons.Rounded.ChatBubbleOutline
                    },
                    contentDescription = null,
                    tint = colors.accentGreen,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Send to GitHub",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GitHubFeedbackType.entries.forEach { type ->
                        val selected = selectedType == type
                        FilterChip(
                            selected = selected,
                            onClick = { selectedType = type },
                            label = { Text(type.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.accentGreen.copy(alpha = 0.22f),
                                selectedLabelColor = colors.accentGreen
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Summary") },
                    placeholder = { Text("Short title for ${selectedType.label.lowercase()}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = detailsText,
                    onValueChange = { detailsText = it },
                    label = { Text("Details") },
                    placeholder = { Text(selectedType.placeholder, fontSize = 12.sp) },
                    minLines = 4,
                    maxLines = 7,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { includeDiagnostics = !includeDiagnostics }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeDiagnostics,
                        onCheckedChange = { includeDiagnostics = it },
                        colors = CheckboxDefaults.colors(checkedColor = colors.accentGreen)
                    )
                    Column {
                        Text(
                            text = "Attach Wupp & device info",
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} • Android ${Build.VERSION.RELEASE} • ${Build.MODEL}",
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanTitle = if (titleText.isBlank()) {
                        selectedType.defaultTitlePrefix.trim()
                    } else if (titleText.trim().startsWith("[")) {
                        titleText.trim()
                    } else {
                        "${selectedType.defaultTitlePrefix}${titleText.trim()}"
                    }
                    val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")
                    val encodedBody = URLEncoder.encode(buildFullBody(), "UTF-8")
                    val repo = BuildConfig.GITHUB_REPO.trim().trim('/')
                    val issueUrl = "https://github.com/$repo/issues/new" +
                        "?template=${selectedType.templateFile}" +
                        "&labels=${selectedType.githubLabel}" +
                        "&title=$encodedTitle" +
                        "&body=$encodedBody"

                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(issueUrl)))
                        onDismiss()
                    }.onFailure {
                        Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentGreen,
                    contentColor = Color(0xFF0B141A)
                )
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open GitHub", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        val markdown = buildString {
                            appendLine("## ${selectedType.defaultTitlePrefix}${titleText.trim()}")
                            appendLine()
                            appendLine(buildFullBody())
                        }
                        clipboard.setText(AnnotatedString(markdown))
                        Toast.makeText(context, "Copied report to clipboard", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateDialog(
    state: UpdateUiState,
    onDownload: (AppReleaseInfo) -> Unit,
    onDownloadToDevice: ((AppReleaseInfo, String?) -> Unit)? = null,
    onInstall: (File) -> Unit,
    onInstallExternal: (File) -> Unit = {},
    onSkipVersion: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val colors = LocalWaColors.current
    val context = LocalContext.current

    val release: AppReleaseInfo = when (state) {
        is UpdateUiState.UpdateAvailable -> state.release
        is UpdateUiState.Downloading -> state.release
        is UpdateUiState.ReadyToInstall -> state.release
        is UpdateUiState.Error -> state.release ?: return
        else -> return
    }

    val isDownloading = state is UpdateUiState.Downloading
    val isReady = state is UpdateUiState.ReadyToInstall || (state is UpdateUiState.Error && !state.apkPath.isNullOrBlank())
    val apkSizeLabel = remember(release.apkSizeBytes) {
        WuppUpdater.formatByteSize(release.apkSizeBytes)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val parsedChangelog = remember(release.changelog) { parseReleaseNotesLines(release.changelog) }
    val showReleaseTitle = remember(release.title, release.tagName, release.versionName) {
        val t = release.title.trim()
        t.isNotBlank() &&
            !t.equals(release.tagName, ignoreCase = true) &&
            !t.equals(release.versionName, ignoreCase = true) &&
            !t.equals("v${release.versionName}", ignoreCase = true)
    }
    val apkDisplayName = release.apkFileName?.takeIf { it.isNotBlank() } ?: "Wupp-${release.versionName}.apk"
    val apkMetaSubtitle = buildString {
        append(context.packageName)
        if (apkSizeLabel.isNotBlank()) {
            append(" · ")
            append(apkSizeLabel)
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        sheetState = sheetState,
        containerColor = colors.appSurface,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = colors.textSecondary.copy(alpha = 0.45f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 22.dp)
        ) {
            // Circular Wupp app badge matching reference sheet
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(colors.elevatedBar)
                    .border(0.75.dp, colors.textSecondary.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.wupp_logo),
                    contentDescription = "Wupp",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isReady) "Update ready to install!" else "Update available!",
                color = colors.textPrimary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Rounded changelog card: "What changed on X.Y.Z"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.elevatedBar)
                    .border(0.6.dp, colors.textSecondary.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                Text(
                    text = "What changed on ${release.versionName}",
                    color = colors.textPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                if (showReleaseTitle) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = release.title,
                        color = colors.textPrimary.copy(alpha = 0.9f),
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = colors.textSecondary.copy(alpha = 0.22f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "What's new",
                    color = colors.textPrimary,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp, max = 210.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    parsedChangelog.forEach { line ->
                        when (line.kind) {
                            ChangelogLineKind.HEADING -> {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = line.text,
                                    color = colors.textPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            ChangelogLineKind.BULLET -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "•",
                                        color = colors.textSecondary,
                                        fontSize = 13.5.sp,
                                        modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                                    )
                                    Text(
                                        text = line.text,
                                        color = colors.textSecondary,
                                        fontSize = 13.5.sp,
                                        lineHeight = 19.sp
                                    )
                                }
                            }
                            ChangelogLineKind.TEXT -> {
                                Text(
                                    text = line.text,
                                    color = colors.textSecondary,
                                    fontSize = 13.5.sp,
                                    lineHeight = 19.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "To Be Installed -",
                color = colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
            )

            // Package asset preview card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.elevatedBar)
                    .border(0.6.dp, colors.textSecondary.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(colors.chatCanvas),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.wupp_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(7.dp))
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = apkDisplayName,
                        color = colors.textPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = apkMetaSubtitle,
                        color = colors.textSecondary,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (state is UpdateUiState.Downloading) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.progressPercent >= 0) {
                        LinearProgressIndicator(
                            progress = { state.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = colors.accentGreen,
                            trackColor = colors.elevatedBar
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = colors.accentGreen,
                            trackColor = colors.elevatedBar
                        )
                    }
                    val dlText = buildString {
                        append("Downloading update")
                        if (state.progressPercent >= 0) append(" ${state.progressPercent}%")
                        val done = WuppUpdater.formatByteSize(state.downloadedBytes)
                        val tot = WuppUpdater.formatByteSize(state.totalBytes)
                        if (done.isNotBlank() && tot.isNotBlank()) append(" · $done / $tot")
                    }
                    Text(
                        text = dlText,
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Primary CTA pill ("Install here" / "Install now" / "Retry download" / "Download in background")
            when (state) {
                is UpdateUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.antiDeleteRed.copy(alpha = 0.14f))
                            .border(0.8.dp, colors.antiDeleteRed.copy(alpha = 0.38f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = state.message,
                            color = colors.antiDeleteRed,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    val readyFile = state.apkPath?.let { File(it) }?.takeIf { it.exists() }
                    if (readyFile != null) {
                        Button(
                            onClick = { onInstall(readyFile) },
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentGreen,
                                contentColor = Color(0xFF0B141A)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                text = "Try system installer",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onInstallExternal(readyFile) },
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.elevatedBar,
                                contentColor = colors.textPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                text = "Open with external installer",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else if (!release.apkDownloadUrl.isNullOrBlank()) {
                        Button(
                            onClick = { onDownload(release) },
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentGreen,
                                contentColor = Color(0xFF0B141A)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                text = "Retry download",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                is UpdateUiState.ReadyToInstall -> {
                    Button(
                        onClick = { onInstall(File(state.apkPath)) },
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentGreen,
                            contentColor = Color(0xFF0B141A)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = "Install now",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                is UpdateUiState.Downloading -> {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.elevatedBar,
                            contentColor = colors.textPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = "Download in background",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            if (!release.apkDownloadUrl.isNullOrBlank()) {
                                onDownload(release)
                            } else if (release.htmlUrl.isNotBlank()) {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)))
                                }
                                onDismiss()
                            }
                        },
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentGreen,
                            contentColor = Color(0xFF0B141A)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = if (!release.apkDownloadUrl.isNullOrBlank()) "Install here" else "View release",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (!isDownloading && (isReady || !release.apkDownloadUrl.isNullOrBlank())) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "If Google Play Protect shows \"App scan recommended\", tap \"Scan app\" — normal for GitHub APKs outside Play Store.",
                    color = colors.textSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp
                )
            }

            if (onDownloadToDevice != null && !isDownloading && !release.apkDownloadUrl.isNullOrBlank()) {
                val cachedPath = when (state) {
                    is UpdateUiState.ReadyToInstall -> state.apkPath
                    is UpdateUiState.Error -> state.apkPath
                    else -> null
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { onDownloadToDevice(release, cachedPath) },
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "Download to Device",
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (release.htmlUrl.isNotBlank() && !isDownloading) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)))
                        }
                        onDismiss()
                    },
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.elevatedBar,
                        contentColor = colors.textPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "Open the web",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private enum class ChangelogLineKind { HEADING, BULLET, TEXT }

private data class ChangelogLine(val kind: ChangelogLineKind, val text: String)

private fun parseReleaseNotesLines(raw: String): List<ChangelogLine> {
    val lines = raw.lines()
    val out = ArrayList<ChangelogLine>(lines.size)
    for (original in lines) {
        val trimmed = original.trim()
        if (trimmed.isBlank() || trimmed == "---" || trimmed == "***") continue
        val cleanMarkdown = trimmed
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("`([^`]+)`"), "$1")
            .trim()
        when {
            trimmed.startsWith("#") -> {
                val headingText = cleanMarkdown.trimStart('#').trim()
                if (headingText.isNotBlank() && !headingText.equals("What's new", ignoreCase = true)) {
                    out += ChangelogLine(ChangelogLineKind.HEADING, headingText)
                }
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                val itemText = cleanMarkdown.drop(1).trim()
                if (itemText.isNotBlank()) {
                    out += ChangelogLine(ChangelogLineKind.BULLET, itemText)
                }
            }
            else -> {
                out += ChangelogLine(ChangelogLineKind.TEXT, cleanMarkdown)
            }
        }
    }
    return if (out.isEmpty()) listOf(ChangelogLine(ChangelogLineKind.TEXT, raw.trim())) else out
}
