package com.wa.beastclient.ui.calling

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wa.beastclient.core.calling.CallStateManager
import com.wa.beastclient.core.calling.WebLinkState
import com.wa.beastclient.domain.stealth.CallBackendMode
import com.wa.beastclient.domain.stealth.GlobalStealthConfig
import com.wa.beastclient.domain.stealth.StealthMatrixManager
import com.wa.beastclient.ui.theme.LocalWaColors
import com.wa.beastclient.ui.theme.LocalWaMonoFamily

/**
 * Native onboarding and lifecycle dialog for linking Wupp's hidden WebRTC calling companion
 * via WhatsApp's 8-character phone linking code and selecting the RAM/battery backend mode.
 */
@Composable
fun CallingSetupDialog(
    ownJid: String,
    stealth: GlobalStealthConfig,
    onDismiss: () -> Unit,
    onCallViaOfficialApp: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxWidth(0.92f)) {
            CallingSetupCard(
                ownJid = ownJid,
                stealth = stealth,
                showCloseButton = true,
                onDismiss = onDismiss,
                onCallViaOfficialApp = onCallViaOfficialApp,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
fun CallingSetupCard(
    ownJid: String,
    stealth: GlobalStealthConfig,
    showCloseButton: Boolean,
    onDismiss: () -> Unit = {},
    onCallViaOfficialApp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    val context = LocalContext.current
    val linkState by CallStateManager.linkState.collectAsState()
    val pairingCode by CallStateManager.pairingCode.collectAsState()
    val pairingStatus by CallStateManager.pairingStatus.collectAsState()

    val initialPhone = remember(ownJid) {
        ownJid.substringBefore("@").substringBefore(":").filter { it.isDigit() }
    }
    var phoneInput by remember(initialPhone) { mutableStateOf(initialPhone) }

    val isLinked = stealth.callingLinked || linkState == WebLinkState.AUTHENTICATED
    var selectedUnlinkedMode by remember {
        mutableStateOf(
            if (!isLinked && stealth.callBackendMode == CallBackendMode.OFF) {
                CallBackendMode.ALWAYS_READY
            } else {
                stealth.callBackendMode
            }
        )
    }
    val displaySelectedMode = if (isLinked) stealth.callBackendMode else selectedUnlinkedMode
    val orderedModes = remember {
        listOf(CallBackendMode.ALWAYS_READY, CallBackendMode.SMART_WARM, CallBackendMode.OFF)
    }
    var showLivePreview by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        CallStateManager.init(context)
    }

    LaunchedEffect(isLinked) {
        if (isLinked) {
            showLivePreview = false
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, colors.textSecondary.copy(alpha = 0.18f), RoundedCornerShape(24.dp)),
        color = colors.elevatedBar
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = null,
                        tint = colors.brightGreen
                    )
                    Text(
                        text = "In-App Calling (Beta)",
                        color = colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (showCloseButton) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.textSecondary)
                    }
                }
            }

            // Linked badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isLinked) colors.brightGreen.copy(alpha = 0.14f)
                        else colors.appSurface.copy(alpha = 0.7f)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isLinked) Icons.Rounded.CheckCircle else Icons.Rounded.Call,
                    contentDescription = null,
                    tint = if (isLinked) colors.brightGreen else colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = if (isLinked) "Calling Companion Linked" else "One-Time Companion Pairing Needed",
                        color = colors.textPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Runs a separate WebRTC link (uses 2 of your 4 WhatsApp Linked Devices slots).",
                        color = colors.textSecondary,
                        fontSize = 11.5.sp
                    )
                }
            }

            // Lifecycle Mode selector
            Text(
                text = "Engine Lifecycle Mode",
                color = colors.textPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                orderedModes.forEach { mode ->
                    val selected = displaySelectedMode == mode
                    val selectMode: () -> Unit = {
                        selectedUnlinkedMode = mode
                        StealthMatrixManager.updateConfig { cfg -> cfg.copy(callBackendMode = mode) }
                        CallStateManager.onConfigChanged(StealthMatrixManager.config.value)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) colors.brightGreen.copy(alpha = 0.12f)
                                else colors.appSurface.copy(alpha = 0.55f)
                            )
                            .clickable(onClick = selectMode)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = selectMode
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.title,
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = mode.subtitle,
                                color = colors.textSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }
            Text(
                text = "You can change your calling mode anytime in Settings → Voice & Video Calling.",
                color = colors.textSecondary,
                fontSize = 11.5.sp
            )

            if (!isLinked) {
                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Phone number with country code") },
                    prefix = { Text("+") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )

                if (pairingCode.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.appSurface)
                            .border(1.dp, colors.brightGreen.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pairingCode,
                                color = colors.brightGreen,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = mono,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = "Open WhatsApp → Linked devices → Link with phone number",
                                color = colors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Pairing Code", pairingCode.replace("-", "")))
                                Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = colors.textPrimary)
                        }
                    }
                } else if (linkState == WebLinkState.ERROR) {
                    Text(
                        text = pairingStatus.ifBlank { "Unable to generate pairing code — please try again." },
                        color = colors.antiDeleteRed,
                        fontSize = 12.5.sp
                    )
                } else if (linkState == WebLinkState.LOADING || pairingStatus.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.brightGreen)
                        Text(
                            text = pairingStatus.ifBlank { "Initializing WhatsApp Web engine…" },
                            color = colors.textSecondary,
                            fontSize = 12.5.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        val modeToApply = if (selectedUnlinkedMode == CallBackendMode.OFF) {
                            CallBackendMode.ALWAYS_READY
                        } else {
                            selectedUnlinkedMode
                        }
                        selectedUnlinkedMode = modeToApply
                        CallStateManager.requestPhonePairingCode(phoneInput)
                        StealthMatrixManager.updateConfig { it.copy(callBackendMode = modeToApply) }
                    },
                    enabled = phoneInput.length >= 7,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.brightGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (pairingCode.isNotBlank()) "Refresh Pairing Code" else "Generate 8-Digit Pairing Code",
                        color = Color(0xFF0A1418),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (linkState != WebLinkState.UNINITIALIZED || pairingStatus.isNotBlank()) {
                    OutlinedButton(
                        onClick = { showLivePreview = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Open Live Pairing Screen (Manual / Interactive)",
                            color = colors.textPrimary
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { showLivePreview = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Linked WhatsApp Web", color = colors.textPrimary)
                }

                OutlinedButton(
                    onClick = {
                        CallStateManager.unlinkCallingEngine()
                        Toast.makeText(context, "Calling companion session unlinked", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LinkOff,
                        contentDescription = null,
                        tint = colors.antiDeleteRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlink & Clear Calling Session", color = colors.antiDeleteRed)
                }
            }

            if (showLivePreview) {
                val previewContainer = remember(context) { FrameLayout(context) }
                DisposableEffect(previewContainer) {
                    CallStateManager.attachPairingPreviewHost(previewContainer)
                    onDispose {
                        CallStateManager.detachPairingPreviewHost(previewContainer)
                    }
                }
                Dialog(
                    onDismissRequest = { showLivePreview = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = colors.elevatedBar,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 16.dp)
                            .border(1.dp, colors.textSecondary.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isLinked) "Linked WhatsApp Web Session" else "Live WhatsApp Web Pairing",
                                        color = colors.textPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isLinked) {
                                            "Inspect the live companion browser session below."
                                        } else {
                                            "If auto-click pauses, tap 'Link with phone number instead.' directly on the page below."
                                        },
                                        color = colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                IconButton(onClick = { showLivePreview = false }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Close live preview",
                                        tint = colors.textSecondary
                                    )
                                }
                            }

                            if (!isLinked && pairingCode.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.brightGreen.copy(alpha = 0.14f))
                                        .clickable {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("WhatsApp Pairing Code", pairingCode))
                                            Toast.makeText(context, "Pairing code copied", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Code: $pairingCode",
                                        color = colors.brightGreen,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Tap to copy",
                                        color = colors.textSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            } else if (!isLinked && pairingStatus.isNotBlank()) {
                                Text(
                                    text = pairingStatus,
                                    color = if (linkState == WebLinkState.ERROR) colors.antiDeleteRed else colors.textSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(500.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, colors.textSecondary.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                                    .background(Color.White)
                            ) {
                                AndroidView(
                                    factory = { previewContainer },
                                    update = { container ->
                                        CallStateManager.attachPairingPreviewHost(container)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }

            if (onCallViaOfficialApp != null) {
                OutlinedButton(
                    onClick = {
                        if (showCloseButton) onDismiss()
                        onCallViaOfficialApp()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Call in Official WhatsApp Instead", color = colors.textPrimary)
                }
            }
        }
    }
}
