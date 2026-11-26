package com.wa.beastclient.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.ContactsContract
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.wa.beastclient.core.calling.extractDialablePhoneDigits
import com.wa.beastclient.ui.screens.WhatsAppAvatar
import com.wa.beastclient.ui.theme.WaColors
import com.wa.beastclient.ui.theme.contrastingContentColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ParsedContactQr(
    val phoneDigits: String,
    val displayPhone: String,
    val name: String,
    val rawPayload: String,
    val jid: String
)

fun buildMyContactQrContent(ownJid: String, phone: String = ""): String {
    val digits = extractDialablePhoneDigits(phone, ownJid)
    return if (digits.isNotEmpty()) "https://wa.me/$digits" else ""
}

private val WA_ME_REGEX = Regex("""(?:https?://)?(?:www\.)?wa\.me/(\+?\d{6,16})(?:[/?#].*)?$""", RegexOption.IGNORE_CASE)
private val WA_API_PHONE_REGEX = Regex("""[?&]phone=(\+?\d{6,16})""", RegexOption.IGNORE_CASE)
private val TEL_URI_REGEX = Regex("""^(?:tel:|whatsapp://send\?phone=)(\+?\d{6,16})$""", RegexOption.IGNORE_CASE)

fun parseContactQrPayload(raw: String): ParsedContactQr? {
    val text = raw.trim()
    if (text.isEmpty()) return null

    // 1. WhatsApp me links: https://wa.me/923001234567 or api.whatsapp.com/send?phone=...
    WA_ME_REGEX.find(text)?.let { m ->
        val digits = m.groupValues[1].filter { it.isDigit() }
        if (digits.length >= 6) {
            return ParsedContactQr(
                phoneDigits = digits,
                displayPhone = "+$digits",
                name = "",
                rawPayload = text,
                jid = "$digits@s.whatsapp.net"
            )
        }
    }
    if (text.contains("whatsapp", ignoreCase = true)) {
        WA_API_PHONE_REGEX.find(text)?.let { m ->
            val digits = m.groupValues[1].filter { it.isDigit() }
            if (digits.length >= 6) {
                return ParsedContactQr(
                    phoneDigits = digits,
                    displayPhone = "+$digits",
                    name = "",
                    rawPayload = text,
                    jid = "$digits@s.whatsapp.net"
                )
            }
        }
    }

    // 2. vCard payload (BEGIN:VCARD)
    if (text.contains("BEGIN:VCARD", ignoreCase = true)) {
        var name = ""
        var digits = ""
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("FN:", ignoreCase = true) && name.isEmpty()) {
                name = trimmed.substringAfter(":").trim()
            } else if (trimmed.startsWith("TEL", ignoreCase = true) && digits.isEmpty()) {
                digits = trimmed.substringAfter(":").filter { it.isDigit() }
            }
        }
        if (digits.length >= 6) {
            return ParsedContactQr(
                phoneDigits = digits,
                displayPhone = "+$digits",
                name = name,
                rawPayload = text,
                jid = "$digits@s.whatsapp.net"
            )
        }
    }

    // 3. MECARD payload (MECARD:N:Alice;TEL:+923001234567;;)
    if (text.startsWith("MECARD:", ignoreCase = true)) {
        val body = text.substringAfter(":")
        var name = ""
        var digits = ""
        for (part in body.split(";")) {
            when {
                part.startsWith("N:", ignoreCase = true) -> name = part.substringAfter(":").replace(",", " ").trim()
                part.startsWith("TEL:", ignoreCase = true) -> digits = part.substringAfter(":").filter { it.isDigit() }
            }
        }
        if (digits.length >= 6) {
            return ParsedContactQr(
                phoneDigits = digits,
                displayPhone = "+$digits",
                name = name,
                rawPayload = text,
                jid = "$digits@s.whatsapp.net"
            )
        }
    }

    // 4. tel: URI or raw phone number string (7-16 digits, optional +, spaces, dashes, parens)
    TEL_URI_REGEX.find(text)?.let { m ->
        val digits = m.groupValues[1].filter { it.isDigit() }
        if (digits.length >= 6) {
            return ParsedContactQr(
                phoneDigits = digits,
                displayPhone = "+$digits",
                name = "",
                rawPayload = text,
                jid = "$digits@s.whatsapp.net"
            )
        }
    }
    if (text.matches(Regex("""^\+?[\d\s\-().]{7,22}$"""))) {
        val digits = text.filter { it.isDigit() }
        if (digits.length in 7..16) {
            return ParsedContactQr(
                phoneDigits = digits,
                displayPhone = "+$digits",
                name = "",
                rawPayload = text,
                jid = "$digits@s.whatsapp.net"
            )
        }
    }

    return null
}

fun generateContactQrBitmap(content: String, sizePx: Int = 560): ImageBitmap? {
    if (content.isBlank()) return null
    return runCatching {
        val hints = mapOf(
            EncodeHintType.MARGIN to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        val darkColor = 0xFF111B21.toInt()
        val whiteColor = 0xFFFFFFFF.toInt()
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (matrix.get(x, y)) darkColor else whiteColor
            }
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        bmp.asImageBitmap()
    }.getOrNull()
}

fun decodeQrFromBitmap(bitmap: Bitmap): String? {
    val maxSide = maxOf(bitmap.width, bitmap.height)
    val scaled = if (maxSide > 1024) {
        val ratio = 1024f / maxSide.toFloat()
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(64),
            (bitmap.height * ratio).toInt().coerceAtLeast(64),
            true
        )
    } else {
        bitmap
    }
    val w = scaled.width
    val h = scaled.height
    val pixels = IntArray(w * h)
    scaled.getPixels(pixels, 0, w, 0, 0, w, h)
    if (scaled !== bitmap) scaled.recycle()

    val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true
    )
    return runCatching {
        val source = RGBLuminanceSource(w, h, pixels)
        val result = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), hints)
        result.text?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactQrCodeSheet(
    ownJid: String,
    ownName: String,
    ownPhone: String,
    ownAvatarPath: String?,
    colors: WaColors,
    initialTab: Int = 0,
    onDismiss: () -> Unit,
    onStartChat: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetBg = if (colors.isDark) Color(0xFF14181B) else colors.elevatedBar

    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var scannedContact by remember { mutableStateOf<ParsedContactQr?>(null) }
    var scanErrorText by remember { mutableStateOf<String?>(null) }
    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val camPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPerm = granted
        if (!granted) {
            scanErrorText = "Camera permission needed to scan QR codes, or pick a QR image from Photos."
        }
    }

    val handleDecodedText: (String) -> Unit = { rawText ->
        val parsed = parseContactQrPayload(rawText)
        if (parsed != null) {
            scanErrorText = null
            scannedContact = parsed
        } else {
            scanErrorText = "That QR code doesn't contain a WhatsApp/Wupp contact link or phone number."
        }
    }

    val pickQrImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val decoded = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.let { bmp ->
                            try {
                                decodeQrFromBitmap(bmp)
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                }.getOrNull()
            }
            if (decoded != null) {
                handleDecodedText(decoded)
            } else {
                scanErrorText = "No readable QR code found in that image."
            }
        }
    }

    val myQrLink = remember(ownJid, ownPhone) { buildMyContactQrContent(ownJid, ownPhone) }
    val myQrBitmap = remember(myQrLink) { generateContactQrBitmap(myQrLink, 560) }
    val displayTitle = ownName.trim().ifBlank { "My Wupp Contact" }
    val displayDigits = remember(ownPhone, ownJid) { extractDialablePhoneDigits(ownPhone, ownJid) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "QR Code",
                color = colors.textPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Segmented My Code / Scan Code pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (colors.isDark) Color(0xFF20262B) else colors.appSurface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("My code" to Icons.Rounded.QrCode2, "Scan code" to Icons.Rounded.QrCodeScanner).forEachIndexed { idx, (label, icon) ->
                    val active = selectedTab == idx
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) colors.brightGreen else Color.Transparent)
                            .clickable {
                                selectedTab = idx
                                scanErrorText = null
                            }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val fg = if (active) colors.brightGreen.contrastingContentColor() else colors.textSecondary
                        Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }

            if (selectedTab == 0) {
                // MY QR CODE CARD
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = if (colors.isDark) Color(0xFF1D2429) else Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.7.dp, colors.divider.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WhatsAppAvatar(
                            jid = ownJid,
                            avatarPath = ownAvatarPath,
                            isGroup = false,
                            size = 56.dp,
                            initialChar = displayTitle.firstOrNull()?.uppercase()
                        )
                        Text(
                            text = displayTitle,
                            color = colors.textPrimary,
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (displayDigits.isNotEmpty()) {
                            Text(
                                text = "+$displayDigits",
                                color = colors.textSecondary,
                                fontSize = 13.sp
                            )
                        }
                        if (myQrBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .size(216.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color.White)
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = myQrBitmap,
                                    contentDescription = "My Contact QR Code",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            Text(
                                text = "Link an account first so your phone number QR code can be created.",
                                color = colors.textSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                if (myQrLink.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("Wupp QR Link", myQrLink))
                                Toast.makeText(context, "Copied contact link", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy link")
                        }
                        Button(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Message me on WhatsApp / Wupp: $myQrLink")
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(sendIntent, "Share contact link"))
                                }.onFailure {
                                    Toast.makeText(context, "Unable to share link", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.brightGreen,
                                contentColor = colors.brightGreen.contrastingContentColor()
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                // SCAN CODE TAB
                val found = scannedContact
                if (found != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (colors.isDark) Color(0xFF1D2429) else Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.8.dp, colors.brightGreen.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WhatsAppAvatar(
                                jid = found.jid,
                                avatarPath = null,
                                isGroup = false,
                                size = 56.dp,
                                initialChar = (found.name.firstOrNull() ?: '+').uppercase()
                            )
                            Text(
                                text = found.name.ifBlank { found.displayPhone },
                                color = colors.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (found.name.isNotBlank()) {
                                Text(text = found.displayPhone, color = colors.textSecondary, fontSize = 13.5.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val insert = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                                            type = ContactsContract.RawContacts.CONTENT_TYPE
                                            putExtra(ContactsContract.Intents.Insert.PHONE, found.displayPhone)
                                            if (found.name.isNotBlank()) {
                                                putExtra(ContactsContract.Intents.Insert.NAME, found.name)
                                            }
                                        }
                                        runCatching { context.startActivity(insert) }.onFailure {
                                            Toast.makeText(context, "No Contacts app found", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save")
                                }
                                Button(
                                    onClick = { onStartChat(found.jid) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.brightGreen,
                                        contentColor = colors.brightGreen.contrastingContentColor()
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Chat", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            TextButton(onClick = {
                                scannedContact = null
                                scanErrorText = null
                            }) {
                                Text("Scan another code", color = colors.brightGreen)
                            }
                        }
                    }
                } else {
                    if (hasCameraPerm) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.Black)
                        ) {
                            QrCamera2Preview(
                                onQrDecoded = handleDecodedText,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Clean scan frame reticle overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(190.dp)
                                    .border(2.5.dp, colors.brightGreen, RoundedCornerShape(18.dp))
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (colors.isDark) Color(0xFF1D2429) else colors.appSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CameraAlt,
                                    contentDescription = null,
                                    tint = colors.brightGreen,
                                    modifier = Modifier.size(34.dp)
                                )
                                Text(
                                    text = "Allow camera access to scan a contact QR code, or pick one from your gallery.",
                                    color = colors.textSecondary,
                                    fontSize = 13.5.sp,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { camPermLauncher.launch(Manifest.permission.CAMERA) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.brightGreen,
                                        contentColor = colors.brightGreen.contrastingContentColor()
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Allow Camera", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    scanErrorText?.let { err ->
                        Text(
                            text = err,
                            color = colors.antiDeleteRed,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            pickQrImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan QR from Photos")
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun QrCamera2Preview(
    onQrDecoded: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reader = remember { MultiFormatReader() }
    val decodeHints = remember {
        mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to false
        )
    }

    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }

    DisposableEffect(context, textureViewRef) {
        val tv = textureViewRef ?: return@DisposableEffect onDispose {}
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return@DisposableEffect onDispose {}

        val bgThread = HandlerThread("wupp-qr-cam").apply { start() }
        val bgHandler = Handler(bgThread.looper)
        val mainHandler = Handler(context.mainLooper)

        var cameraDevice: CameraDevice? = null
        var captureSession: CameraCaptureSession? = null
        var imageReader: ImageReader? = null
        var lastScanMs = 0L
        var delivered = false
        var disposed = false

        fun closeCamera() {
            disposed = true
            runCatching { captureSession?.close() }
            captureSession = null
            runCatching { cameraDevice?.close() }
            cameraDevice = null
            runCatching { imageReader?.close() }
            imageReader = null
            runCatching { bgThread.quitSafely() }
        }

        fun pickBackCameraId(): String? {
            val ids = runCatching { manager.cameraIdList }.getOrNull() ?: return null
            return ids.firstOrNull { id ->
                runCatching {
                    manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_BACK
                }.getOrDefault(false)
            } ?: ids.firstOrNull()
        }

        fun applyTextureTransform(viewWidth: Int, viewHeight: Int, previewSize: Size) {
            if (viewWidth == 0 || viewHeight == 0) return
            val matrix = Matrix()
            val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
            val bufRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
            val centerX = viewRect.centerX()
            val centerY = viewRect.centerY()
            bufRect.offset(centerX - bufRect.centerX(), centerY - bufRect.centerY())
            matrix.setRectToRect(viewRect, bufRect, Matrix.ScaleToFit.FILL)
            val scale = maxOf(
                viewHeight.toFloat() / previewSize.height.toFloat(),
                viewWidth.toFloat() / previewSize.width.toFloat()
            )
            matrix.postScale(scale, scale, centerX, centerY)
            mainHandler.post { if (!disposed) tv.setTransform(matrix) }
        }

        fun openAndStart(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
            val camId = pickBackCameraId() ?: return
            val map = runCatching {
                manager.getCameraCharacteristics(camId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            }.getOrNull() ?: return
            val choices = map.getOutputSizes(SurfaceTexture::class.java).orEmpty()
            val previewSize = choices
                .filter { it.width in 640..1920 && it.height in 480..1080 }
                .minByOrNull { kotlin.math.abs(it.width * it.height - 921_600) }
                ?: choices.firstOrNull()
                ?: Size(1280, 720)

            surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
            applyTextureTransform(width, height, previewSize)
            val previewSurface = Surface(surfaceTexture)

            val ir = ImageReader.newInstance(
                previewSize.width.coerceAtMost(960),
                previewSize.height.coerceAtMost(720),
                ImageFormat.YUV_420_888,
                2
            )
            imageReader = ir

            ir.setOnImageAvailableListener({ r ->
                val img = runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
                try {
                    val now = System.currentTimeMillis()
                    if (delivered || disposed || now - lastScanMs < 240L) return@setOnImageAvailableListener
                    lastScanMs = now

                    val yPlane = img.planes.firstOrNull() ?: return@setOnImageAvailableListener
                    val yBuf = yPlane.buffer
                    val rowStride = yPlane.rowStride
                    val yBytes = ByteArray(yBuf.remaining())
                    yBuf.get(yBytes)

                    val imgW = img.width
                    val imgH = img.height
                    // Trim row padding if rowStride > imgW
                    val packedY = if (rowStride == imgW) {
                        yBytes
                    } else {
                        ByteArray(imgW * imgH).also { dst ->
                            for (row in 0 until imgH) {
                                val srcOff = row * rowStride
                                if (srcOff + imgW <= yBytes.size) {
                                    System.arraycopy(yBytes, srcOff, dst, row * imgW, imgW)
                                }
                            }
                        }
                    }

                    val source = PlanarYUVLuminanceSource(
                        packedY, imgW, imgH, 0, 0, imgW, imgH, false
                    )
                    val decoded = runCatching {
                        reader.decode(BinaryBitmap(HybridBinarizer(source)), decodeHints).text
                    }.getOrNull()
                    if (!decoded.isNullOrBlank()) {
                        delivered = true
                        mainHandler.post {
                            if (!disposed) onQrDecoded(decoded)
                        }
                    }
                } finally {
                    runCatching { img.close() }
                }
            }, bgHandler)

            runCatching {
                manager.openCamera(camId, object : CameraDevice.StateCallback() {
                    override fun onOpened(cam: CameraDevice) {
                        if (disposed) {
                            cam.close()
                            return
                        }
                        cameraDevice = cam
                        runCatching {
                            val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                addTarget(previewSurface)
                                addTarget(ir.surface)
                                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            }
                            @Suppress("DEPRECATION")
                            cam.createCaptureSession(
                                listOf(previewSurface, ir.surface),
                                object : CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(session: CameraCaptureSession) {
                                        if (disposed) {
                                            session.close()
                                            return
                                        }
                                        captureSession = session
                                        runCatching { session.setRepeatingRequest(req.build(), null, bgHandler) }
                                    }

                                    override fun onConfigureFailed(session: CameraCaptureSession) = Unit
                                },
                                bgHandler
                            )
                        }
                    }

                    override fun onDisconnected(cam: CameraDevice) {
                        cam.close()
                    }

                    override fun onError(cam: CameraDevice, error: Int) {
                        cam.close()
                    }
                }, bgHandler)
            }
        }

        if (tv.isAvailable && tv.surfaceTexture != null) {
            tv.surfaceTexture?.let { st -> openAndStart(st, tv.width, tv.height) }
        } else {
            tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                    openAndStart(st, w, h)
                }

                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
            }
        }

        onDispose {
            closeCamera()
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).also { textureViewRef = it }
        },
        modifier = modifier
    )
}
