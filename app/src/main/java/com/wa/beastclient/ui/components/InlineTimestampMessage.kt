package com.wa.beastclient.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.R
import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.ui.theme.LocalWaColors
import com.wa.beastclient.ui.theme.LocalWaMonoFamily
import com.wa.beastclient.ui.theme.LocalWuppChatFontScale
import com.wa.beastclient.ui.theme.LocalWuppShowSeconds
import com.wa.beastclient.ui.theme.LocalWuppUse24Hour
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

fun formatDeletedLabel(
    deletedAtMs: Long,
    fallbackTimestampMs: Long,
    includeSeconds: Boolean,
    use24Hour: Boolean
): String {
    val effectiveMs = deletedAtMs.takeIf { it > 0L } ?: fallbackTimestampMs
    val clock = formatMessageClock(effectiveMs, includeSeconds = includeSeconds, use24Hour = use24Hour)
    return if (clock.isNotBlank()) "Deleted · $clock" else "Deleted"
}

@Composable
fun DeletedTimestampBadge(
    deletedAtMs: Long,
    fallbackTimestampMs: Long,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    val showSeconds = LocalWuppShowSeconds.current
    val use24Hour = LocalWuppUse24Hour.current
    Row(
        modifier = modifier
            .background(colors.antiDeleteRed.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.vec_ic_delete_sweep),
            contentDescription = "Anti-Deleted",
            tint = colors.antiDeleteRed,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = formatDeletedLabel(
                deletedAtMs = deletedAtMs,
                fallbackTimestampMs = fallbackTimestampMs,
                includeSeconds = showSeconds,
                use24Hour = use24Hour
            ),
            color = colors.antiDeleteRed,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = mono
        )
    }
}

/**
 * WhatsApp-accurate message body layout that inspects TextLayoutResult.getLineRight(lastLine)
 * to tuck the timestamp, Edited label, and vector delivery ticks cleanly into the final line.
 * Preserved Anti-Delete messages render the original payload plus a crimson Deleted badge.
 */
@Composable
fun InlineTimestampMessage(
    msg: MessageEntity,
    modifier: Modifier = Modifier,
    showDeletedBadge: Boolean = true,
    mentionDisplayResolver: ((String) -> String?)? = null
) {
    val colors = LocalWaColors.current
    val showSeconds = LocalWuppShowSeconds.current
    val use24Hour = LocalWuppUse24Hour.current
    val fontScale = LocalWuppChatFontScale.current
    var lastLayout: TextLayoutResult? = null

    val displayText = if (msg.mentionsCsv.isNotBlank() && mentionDisplayResolver != null) {
        resolveMentionsInText(msg.body, msg.mentionsCsv, mentionDisplayResolver)
    } else {
        msg.body
    }
    val timeText = formatMessageClock(msg.timestamp, includeSeconds = showSeconds, use24Hour = use24Hour)
    val linkColor = colors.accentGreen
    val codeBg = colors.elevatedBar.copy(alpha = 0.65f)
    val styledText = androidx.compose.runtime.remember(displayText, msg.mentionsCsv, linkColor, codeBg) {
        formatWhatsAppBody(
            text = displayText,
            linkColor = linkColor,
            codeBg = codeBg,
            highlightMentions = msg.mentionsCsv.isNotBlank()
        )
    }

    Column(modifier = modifier) {
        if (showDeletedBadge && msg.isDeletedBySender) {
            DeletedTimestampBadge(
                deletedAtMs = msg.deletedAt,
                fallbackTimestampMs = msg.timestamp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        if (displayText.isNotEmpty()) {
            Layout(
                content = {
                    Text(
                        text = styledText,
                        color = colors.textPrimary,
                        fontSize = (15.5f * fontScale).sp,
                        lineHeight = (20.5f * fontScale).sp,
                        onTextLayout = { lastLayout = it }
                    )
                    MessageMetaCluster(
                        timeText = timeText,
                        isEdited = msg.isEdited,
                        isEphemeral = msg.isEphemeral,
                        isFromMe = msg.isFromMe,
                        deliveryStatus = msg.deliveryStatus,
                        isPinned = msg.isPinned
                    )
                }
            ) { measurables, constraints ->
                val gapPx = 8.dp.roundToPx()
                val verticalDropGap = 2.dp.roundToPx()
                val textPlaceable = measurables[0].measure(
                    Constraints(maxWidth = constraints.maxWidth)
                )
                val metaPlaceable = measurables[1].measure(
                    Constraints(maxWidth = constraints.maxWidth)
                )

                val layoutRes = lastLayout
                val lastLineIdx = (layoutRes?.lineCount ?: 1) - 1
                val lastLineRight = if (layoutRes != null && lastLineIdx >= 0) {
                    layoutRes.getLineRight(lastLineIdx).roundToInt()
                } else {
                    textPlaceable.width
                }

                val canTuckSameLine =
                    lastLineRight + gapPx + metaPlaceable.width <= constraints.maxWidth
                val totalWidth: Int
                val totalHeight: Int
                val metaX: Int
                val metaY: Int

                if (canTuckSameLine) {
                    totalWidth = max(textPlaceable.width, lastLineRight + gapPx + metaPlaceable.width)
                        .coerceAtMost(constraints.maxWidth)
                    totalHeight = max(textPlaceable.height, metaPlaceable.height)
                    metaX = totalWidth - metaPlaceable.width
                    metaY = (totalHeight - metaPlaceable.height).coerceAtLeast(0)
                } else {
                    totalWidth = max(textPlaceable.width, metaPlaceable.width)
                        .coerceAtMost(constraints.maxWidth)
                    totalHeight = textPlaceable.height + verticalDropGap + metaPlaceable.height
                    metaX = totalWidth - metaPlaceable.width
                    metaY = textPlaceable.height + verticalDropGap
                }

                layout(totalWidth, totalHeight) {
                    textPlaceable.placeRelative(0, 0)
                    metaPlaceable.placeRelative(metaX, metaY)
                }
            }
        } else {
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MessageMetaCluster(
                    timeText = timeText,
                    isEdited = msg.isEdited,
                    isEphemeral = msg.isEphemeral,
                    isFromMe = msg.isFromMe,
                    deliveryStatus = msg.deliveryStatus,
                    isPinned = msg.isPinned
                )
            }
        }
    }
}

@Composable
fun MessageMetaCluster(
    timeText: String,
    isEdited: Boolean,
    isEphemeral: Boolean,
    isFromMe: Boolean,
    deliveryStatus: String,
    tintOverride: Color? = null,
    isPinned: Boolean = false
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    val secondary = tintOverride ?: if (isFromMe && colors.isDark) {
        Color.White.copy(alpha = 0.78f)
    } else {
        colors.textSecondary
    }
    val tickMuted = tintOverride ?: if (isFromMe && colors.isDark) {
        Color.White.copy(alpha = 0.88f)
    } else {
        colors.textSecondary
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isPinned) {
            Icon(
                imageVector = Icons.Rounded.PushPin,
                contentDescription = "Pinned",
                modifier = Modifier.size(11.5.dp),
                tint = secondary
            )
        }
        if (isEphemeral) {
            Text(
                text = "⏳",
                fontSize = 10.5.sp,
                color = colors.accentGreen
            )
        }
        if (isEdited) {
            Text(
                text = "Edited",
                fontSize = 11.5.sp,
                color = secondary
            )
        }
        Text(
            text = timeText,
            fontSize = 12.sp,
            color = secondary
        )
        if (isFromMe) {
            Spacer(modifier = Modifier.width(1.dp))
            DeliveryTickIcon(status = deliveryStatus, mutedColor = tickMuted)
        }
    }
}

@Composable
fun DeliveryTickIcon(
    status: String,
    mutedColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalWaColors.current
    val (resId, tint) = when (status.uppercase()) {
        "READ", "PLAYED" -> R.drawable.msg_status_client_received to colors.blueTick
        "DELIVERED" -> R.drawable.msg_status_client_received to mutedColor
        "SENT", "LOCAL_SENT" -> R.drawable.msg_status_server_receive to mutedColor
        else -> R.drawable.msg_status_client to mutedColor
    }
    Image(
        painter = painterResource(resId),
        contentDescription = status,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(width = 17.dp, height = 11.5.dp)
    )
}

fun formatMessageClock(
    epochMs: Long,
    includeSeconds: Boolean = com.wa.beastclient.domain.stealth.StealthMatrixManager.config.value.showSecondsInTimestamps,
    use24Hour: Boolean = com.wa.beastclient.domain.stealth.StealthMatrixManager.defaultUse24Hour()
): String {
    if (epochMs <= 0L) return ""
    val pattern = when {
        use24Hour && includeSeconds -> "HH:mm:ss"
        use24Hour -> "HH:mm"
        includeSeconds -> "h:mm:ss a"
        else -> "h:mm a"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMs))
}

private val WA_URL_REGEX = Regex("""(?:https?://|www\.)[^\s<>\]\[()"']+""")

/**
 * Replaces raw `@<jid_user>` placeholders in message text using resolved display names from [mentionsCsv].
 */
internal fun resolveMentionsInText(
    text: String,
    mentionsCsv: String,
    resolveName: (String) -> String?
): String {
    if (text.isBlank() || mentionsCsv.isBlank() || !text.contains('@')) return text
    var result = text
    val seenUsers = HashSet<String>()
    for (rawJid in mentionsCsv.split(',')) {
        val cleanJid = rawJid.trim()
        if (cleanJid.isBlank()) continue
        val bareUser = cleanJid.substringBefore("@").substringBefore(":").trim()
        if (bareUser.isBlank() || !seenUsers.add(bareUser)) continue
        val resolved = if (cleanJid.endsWith("@g.us", ignoreCase = true)) {
            "everyone"
        } else {
            resolveName(cleanJid)?.trim().orEmpty()
        }
        if (resolved.isBlank() || resolved == bareUser) continue
        val pattern = Regex("""(?<![\w])@${Regex.escape(bareUser)}(?![\w])""")
        result = pattern.replace(result) { "@$resolved" }
    }
    return result
}

/**
 * Formats WhatsApp inline markdown (*bold*, _italic_, ~strikethrough~, `mono`, ```code```)
 * while highlighting clickable URLs and @mentions in WhatsApp accent green.
 */
fun formatWhatsAppBody(
    text: String,
    linkColor: Color,
    codeBg: Color,
    highlightMentions: Boolean = false
): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")
    return buildAnnotatedString {
        var pos = 0
        val urlMatches = WA_URL_REGEX.findAll(text).toList()
        for (match in urlMatches) {
            if (match.range.first > pos) {
                appendFormattedSegment(
                    this,
                    text.substring(pos, match.range.first),
                    codeBg,
                    mentionColor = if (highlightMentions) linkColor else null
                )
            }
            val rawUrl = match.value.trimEnd('.', ',', ';', ')', '!')
            val trailing = match.value.substring(rawUrl.length)
            val targetUrl = if (rawUrl.startsWith("http", ignoreCase = true)) rawUrl else "https://$rawUrl"
            withLink(
                LinkAnnotation.Url(
                    url = targetUrl,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                )
            ) {
                append(rawUrl)
            }
            if (trailing.isNotEmpty()) {
                append(trailing)
            }
            pos = match.range.last + 1
        }
        if (pos < text.length) {
            appendFormattedSegment(
                this,
                text.substring(pos),
                codeBg,
                mentionColor = if (highlightMentions) linkColor else null
            )
        }
    }
}

private fun appendFormattedSegment(
    builder: AnnotatedString.Builder,
    segment: String,
    codeBg: Color,
    mentionColor: Color? = null
) {
    var i = 0
    val n = segment.length
    while (i < n) {
        if (segment.startsWith("```", i)) {
            val end = segment.indexOf("```", i + 3)
            if (end > i + 3) {
                val content = segment.substring(i + 3, end)
                builder.withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) {
                    append(content)
                }
                i = end + 3
                continue
            }
        }
        val ch = segment[i]
        if (mentionColor != null && ch == '@') {
            val prevOk = i == 0 || segment[i - 1].isWhitespace() || segment[i - 1] in "([{\"'*_"
            if (prevOk && i + 1 < n && !segment[i + 1].isWhitespace()) {
                var end = i + 1
                if (segment[end] == '+') {
                    // Phone mention like `@+92 300 1234567`
                    while (end < n && (segment[end].isDigit() || segment[end] in "+-() " && (end + 1 < n && segment[end + 1].isDigit() || segment[end] != ' '))) {
                        if (segment[end] == ' ' && end + 1 < n && !segment[end + 1].isDigit()) break
                        end++
                    }
                } else {
                    while (end < n && !segment[end].isWhitespace() && segment[end] !in ",.!?;:)]}\"'") {
                        end++
                    }
                }
                if (end > i + 1) {
                    val token = segment.substring(i, end)
                    builder.withStyle(SpanStyle(color = mentionColor, fontWeight = FontWeight.SemiBold)) {
                        append(token)
                    }
                    i = end
                    continue
                }
            }
        }
        if (ch == '*' || ch == '_' || ch == '~' || ch == '`') {
            val prevOk = i == 0 || !segment[i - 1].isLetterOrDigit()
            val end = if (prevOk) segment.indexOf(ch, i + 1) else -1
            val validSpan = end > i + 1 &&
                segment[i + 1] != ' ' &&
                segment[end - 1] != ' ' &&
                (ch == '`' || !segment.substring(i + 1, end).contains('\n'))
            if (validSpan) {
                val inner = segment.substring(i + 1, end)
                val style = when (ch) {
                    '*' -> SpanStyle(fontWeight = FontWeight.Bold)
                    '_' -> SpanStyle(fontStyle = FontStyle.Italic)
                    '~' -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    else -> SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)
                }
                builder.withStyle(style) {
                    append(inner)
                }
                i = end + 1
                continue
            }
        }
        builder.append(ch)
        i++
    }
}
