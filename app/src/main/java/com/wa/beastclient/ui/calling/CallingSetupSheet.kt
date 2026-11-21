package com.wa.beastclient.ui.calling

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.wa.beastclient.core.calling.CallStateManager
import com.wa.beastclient.core.calling.OfficialWhatsAppHelper
import com.wa.beastclient.domain.stealth.CallBackendMode
import com.wa.beastclient.domain.stealth.GlobalStealthConfig
import com.wa.beastclient.domain.stealth.StealthMatrixManager
import com.wa.beastclient.ui.theme.LocalWaColors

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
        CallingSetupCard(
            ownJid = ownJid,
            stealth = stealth,
            showCloseButton = true,
            onDismiss = onDismiss,
            onCallViaOfficialApp = onCallViaOfficialApp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}

@Composable
fun CallingSetupCard(
    @Suppress("UNUSED_PARAMETER") ownJid: String,
    stealth: GlobalStealthConfig,
    showCloseButton: Boolean,
    onDismiss: () -> Unit = {},
    onCallViaOfficialApp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaColors.current
    val context = LocalContext.current

    var permRevision by remember { mutableIntStateOf(0) }
    val hasMic = remember(permRevision) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }
    val hasCam = remember(permRevision) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permRevision += 1
    }

    val nativeEnabled = stealth.callBackendMode != CallBackendMode.OFF
    val orderedModes = remember {
        listOf(CallBackendMode.ALWAYS_READY, CallBackendMode.SMART_WARM, CallBackendMode.OFF)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.elevatedBar,
        border = BorderStroke(1.dp, colors.divider),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = (if (nativeEnabled) colors.brightGreen else colors.textSecondary)
                            .copy(alpha = 0.14f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = null,
                            tint = if (nativeEnabled) colors.brightGreen else colors.textSecondary,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Voice & Video Calling",
                            color = colors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (nativeEnabled) {
                                "Native WhatsApp VoIP Active • Single Linked Device"
                            } else {
                                "Handing off calls to Official WhatsApp"
                            },
                            color = if (nativeEnabled) colors.brightGreen else colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (showCloseButton) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = colors.textSecondary
                        )
                    }
                }
            }

            Text(
                text = "Wupp places and answers end-to-end encrypted voice and video calls natively over Rust (`whatsapp-rust`) using your existing linked session—no second browser link required.",
                color = colors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            orderedModes.forEach { mode ->
                val selected = stealth.callBackendMode == mode
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) colors.brightGreen.copy(alpha = 0.12f) else colors.appSurface.copy(alpha = 0.6f),
                    border = BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) colors.brightGreen else colors.divider
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            StealthMatrixManager.updateConfig {
                                it.copy(callBackendMode = mode)
                            }
                            CallStateManager.onConfigChanged(StealthMatrixManager.config.value)
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = if (selected) colors.brightGreen else colors.textSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.title,
                                color = colors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = mode.subtitle,
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.appSurface.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, colors.divider),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (hasCam) Icons.Rounded.Videocam else Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = if (hasMic && hasCam) colors.brightGreen else colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Mic & Camera Access",
                                color = colors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Mic: ${if (hasMic) "Granted" else "Needed"} • Camera: ${if (hasCam) "Granted" else "Needed"}",
                                color = colors.textSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                    if (!hasMic || !hasCam) {
                        Button(
                            onClick = {
                                permLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.RECORD_AUDIO,
                                        Manifest.permission.CAMERA
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.brightGreen)
                        ) {
                            Text("Allow", fontSize = 12.sp, color = Color.Black)
                        }
                    }
                }
            }

            val officialPkg = remember { OfficialWhatsAppHelper.findInstalledPackage(context) }
            if (officialPkg != null) {
                OfficialWhatsAppCallCoexistenceSection(
                    officialPkg = officialPkg,
                    stealth = stealth
                )
            }

            if (onCallViaOfficialApp != null) {
                OutlinedButton(
                    onClick = onCallViaOfficialApp,
                    border = BorderStroke(1.dp, colors.divider),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Open Official WhatsApp App",
                        color = colors.textPrimary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/**
 * Shared setup card helping users avoid double ringing when Official WhatsApp and Wupp coexist
 * on the same Android phone. Since Android forbids reading another app's NotificationChannel state,
 * the user opens WhatsApp's notification screen directly and confirms or chooses Official-only ring.
 */
@Composable
fun OfficialWhatsAppCallCoexistenceSection(
    officialPkg: String,
    stealth: GlobalStealthConfig,
    modifier: Modifier = Modifier,
    onChoiceSelected: (() -> Unit)? = null
) {
    val colors = LocalWaColors.current
    val context = LocalContext.current
    val appLabel = if (officialPkg == "com.whatsapp.w4b") "WhatsApp Business" else "Official WhatsApp"
    val configured = stealth.officialCallMuteConfirmed || stealth.suppressIncomingRingWhenOfficialInstalled

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.appSurface.copy(alpha = 0.72f),
        border = BorderStroke(
            width = if (configured) 1.5.dp else 1.dp,
            color = if (configured) colors.brightGreen else colors.divider
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (configured) Icons.Rounded.CheckCircle else Icons.Rounded.NotificationsOff,
                    contentDescription = null,
                    tint = if (configured) colors.brightGreen else colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Avoid Double Call Ringing",
                        color = colors.textPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$appLabel is also installed, so incoming calls alert both apps.",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Text(
                text = "To let Wupp ring alone: tap below → Call notifications → set Sound to Silent and turn Pop on screen Off. (Android doesn't allow Wupp to check WhatsApp's channel automatically.)",
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.5.sp
            )

            OutlinedButton(
                onClick = { OfficialWhatsAppHelper.openNotificationSettings(context, officialPkg) },
                border = BorderStroke(1.dp, colors.brightGreen.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = colors.brightGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Open $appLabel Notifications",
                    color = colors.brightGreen,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val mutedOfficialSelected = stealth.officialCallMuteConfirmed && !stealth.suppressIncomingRingWhenOfficialInstalled
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (mutedOfficialSelected) colors.brightGreen.copy(alpha = 0.16f) else colors.elevatedBar,
                    border = BorderStroke(1.dp, if (mutedOfficialSelected) colors.brightGreen else colors.divider),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            StealthMatrixManager.updateConfig {
                                it.copy(
                                    officialCallMuteConfirmed = true,
                                    suppressIncomingRingWhenOfficialInstalled = false
                                )
                            }
                            CallStateManager.onConfigChanged(StealthMatrixManager.config.value)
                            onChoiceSelected?.invoke()
                        }
                ) {
                    Text(
                        text = if (mutedOfficialSelected) "✓ Official Muted" else "I Muted Official",
                        color = if (mutedOfficialSelected) colors.brightGreen else colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                    )
                }

                val officialRingsSelected = stealth.suppressIncomingRingWhenOfficialInstalled
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (officialRingsSelected) colors.brightGreen.copy(alpha = 0.16f) else colors.elevatedBar,
                    border = BorderStroke(1.dp, if (officialRingsSelected) colors.brightGreen else colors.divider),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            StealthMatrixManager.updateConfig {
                                it.copy(
                                    suppressIncomingRingWhenOfficialInstalled = true,
                                    officialCallMuteConfirmed = false
                                )
                            }
                            CallStateManager.onConfigChanged(StealthMatrixManager.config.value)
                            onChoiceSelected?.invoke()
                        }
                ) {
                    Text(
                        text = if (officialRingsSelected) "✓ Official Rings" else "Let Official Ring",
                        color = if (officialRingsSelected) colors.brightGreen else colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun OfficialWhatsAppCoexistenceDialog(
    officialPkg: String,
    stealth: GlobalStealthConfig,
    onDismiss: () -> Unit
) {
    val colors = LocalWaColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.elevatedBar,
            border = BorderStroke(1.dp, colors.divider),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Call Ringing Setup",
                        color = colors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = colors.textSecondary
                        )
                    }
                }
                OfficialWhatsAppCallCoexistenceSection(
                    officialPkg = officialPkg,
                    stealth = stealth,
                    onChoiceSelected = onDismiss
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Later", color = colors.textSecondary)
                    }
                }
            }
        }
    }
}
