package com.wa.beastclient.ui.calling

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.core.calling.CallPhase
import com.wa.beastclient.core.calling.CallStateManager
import com.wa.beastclient.ui.theme.LocalWaMonoFamily

/**
 * Floating banner displayed across HomeScreen and ChatScreen while a call is running minimized,
 * offering one-tap return to CallActivity, quick mute, or hangup.
 */
@Composable
fun ActiveCallFloatingBar(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val callState by CallStateManager.uiState.collectAsState()
    val mono = LocalWaMonoFamily.current
    val visible = callState.phase != CallPhase.IDLE && callState.phase != CallPhase.FAILED

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0E4D3B))
                .border(1.dp, Color(0x5525D366), RoundedCornerShape(16.dp))
                .clickable { CallStateManager.launchCallActivity(context) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF25D366))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${callState.peerName.ifBlank { "Call" }} · ${callState.statusText.ifBlank { "Active" }}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = if (callState.phase == CallPhase.ACTIVE) mono else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { CallStateManager.toggleMute() },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (callState.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                        contentDescription = "Mute",
                        tint = if (callState.isMuted) Color(0xFFFFD54F) else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { CallStateManager.declineOrEndCall() },
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = "End call",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}
