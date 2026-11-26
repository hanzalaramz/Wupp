package com.wa.beastclient.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.wa.beastclient.core.logging.AppLogManager
import com.wa.beastclient.ui.theme.LocalWaColors
import com.wa.beastclient.ui.theme.LocalWaMonoFamily
import com.wa.beastclient.ui.theme.contrastingContentColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DiagnosticsLogsDialog(
    onDismiss: () -> Unit,
    onLogsCleared: () -> Unit = {}
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var refreshNonce by remember { mutableIntStateOf(0) }
    var previewText by remember { mutableStateOf("Loading logs...") }
    var hasCrash by remember { mutableStateOf(false) }

    LaunchedEffect(refreshNonce) {
        val (text, crashExists) = withContext(Dispatchers.IO) {
            AppLogManager.readLogPreview(context, maxLines = 180) to AppLogManager.hasCrashLog(context)
        }
        previewText = text
        hasCrash = crashExists
    }

    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        AppLogManager.writeReportToStream(context, stream).getOrThrow()
                    } ?: error("Unable to open destination file")
                }
            }
            Toast.makeText(
                context,
                result.fold(
                    onSuccess = { "Saved log file successfully" },
                    onFailure = { err -> err.message ?: "Save failed" }
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        containerColor = colors.elevatedBar,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.BugReport,
                    contentDescription = null,
                    tint = if (hasCrash) colors.antiDeleteRed else colors.brightGreen
                )
                Text(
                    text = "Diagnostics & Crash Logs",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasCrash) {
                    Surface(
                        color = colors.antiDeleteRed.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Crash log recorded from a recent session. Share or save this file when reporting bugs.",
                            color = colors.antiDeleteRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Rolling app, voice recorder, VoIP, and crash logs stored safely on your device.",
                        color = colors.textSecondary,
                        fontSize = 12.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 300.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (colors.isDark) Color(0xFF0C1013) else colors.appSurface)
                        .border(0.7.dp, colors.divider, RoundedCornerShape(12.dp))
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(
                        text = previewText,
                        color = colors.textPrimary,
                        fontFamily = mono,
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    AppLogManager.saveLogsToDownloads(context)
                                }
                                res.onSuccess { path ->
                                    AppLogManager.dismissCrashNotice(context)
                                    Toast.makeText(context, "Saved to $path", Toast.LENGTH_LONG).show()
                                }.onFailure { err ->
                                    Toast.makeText(context, err.message ?: "Unable to save", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            saveAsLauncher.launch(AppLogManager.suggestedLogFileName())
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.SaveAs, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save As", fontSize = 13.sp)
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            val shareIntent = withContext(Dispatchers.IO) {
                                AppLogManager.buildShareLogsIntent(context)
                            }
                            if (shareIntent != null) {
                                AppLogManager.dismissCrashNotice(context)
                                runCatching {
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Wupp logs"))
                                }.onFailure {
                                    Toast.makeText(context, "No app available to share file", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Unable to create shareable log file", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.brightGreen,
                        contentColor = colors.brightGreen.contrastingContentColor()
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Log File", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = colors.brightGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                AppLogManager.clearLogs(context)
                onLogsCleared()
                refreshNonce++
                Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = colors.antiDeleteRed, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear", color = colors.antiDeleteRed)
            }
        }
    )
}
