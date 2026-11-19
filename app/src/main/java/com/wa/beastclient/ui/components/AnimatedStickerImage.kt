package com.wa.beastclient.ui.components

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

internal enum class StickerContainerFormat {
    WEBP_OR_RASTER,
    LOTTIE_JSON,
    LOTTIE_WAS_ZIP
}

internal sealed interface DecodedStickerAsset {
    data class StaticBitmap(val bitmap: ImageBitmap) : DecodedStickerAsset
    data class AnimatedDrawable(val drawable: Drawable) : DecodedStickerAsset
    data class LottieJson(val json: String) : DecodedStickerAsset
}

internal fun detectStickerContainerFormat(header: ByteArray): StickerContainerFormat {
    if (header.size >= 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()) {
        return StickerContainerFormat.LOTTIE_WAS_ZIP
    }
    for (b in header) {
        when (b.toInt().toChar()) {
            ' ', '\t', '\r', '\n' -> continue
            '{' -> return StickerContainerFormat.LOTTIE_JSON
            else -> return StickerContainerFormat.WEBP_OR_RASTER
        }
    }
    return StickerContainerFormat.WEBP_OR_RASTER
}

internal fun extractLottieJsonFromZipStream(zipBytes: ByteArray): String? {
    if (zipBytes.isEmpty()) return null
    return runCatching {
        ZipInputStream(zipBytes.inputStream()).use { zis ->
            var fallbackJson: String? = null
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                    val buf = ByteArrayOutputStream()
                    val chunk = ByteArray(4096)
                    var total = 0
                    while (true) {
                        val n = zis.read(chunk)
                        if (n <= 0) break
                        total += n
                        if (total > 4 * 1024 * 1024) break
                        buf.write(chunk, 0, n)
                    }
                    if (total <= 4 * 1024 * 1024) {
                        val content = buf.toString(Charsets.UTF_8.name()).trim()
                        if (content.startsWith("{")) {
                            if (entry.name.contains("anim", ignoreCase = true)) {
                                return@use content
                            }
                            if (fallbackJson == null) {
                                fallbackJson = content
                            }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
            fallbackJson
        }
    }.getOrNull()
}

private fun decodeStickerAssetFromFile(file: File, maxDecodeDimPx: Int): DecodedStickerAsset? {
    if (!file.exists() || file.length() <= 0L) return null
    val header = ByteArray(32)
    val headerLen = runCatching {
        file.inputStream().use { input -> input.read(header).coerceAtLeast(0) }
    }.getOrDefault(0)
    if (headerLen <= 0) return null

    when (detectStickerContainerFormat(header.copyOf(headerLen))) {
        StickerContainerFormat.LOTTIE_WAS_ZIP -> {
            if (file.length() <= 8L * 1024L * 1024L) {
                val json = extractLottieJsonFromZipStream(file.readBytes())
                if (!json.isNullOrBlank()) {
                    return DecodedStickerAsset.LottieJson(json)
                }
            }
            return null
        }

        StickerContainerFormat.LOTTIE_JSON -> {
            if (file.length() <= 4L * 1024L * 1024L) {
                val json = runCatching { file.readText(Charsets.UTF_8).trim() }.getOrNull()
                if (!json.isNullOrBlank() && json.startsWith("{")) {
                    return DecodedStickerAsset.LottieJson(json)
                }
            }
            return null
        }

        StickerContainerFormat.WEBP_OR_RASTER -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val decoded = decodeWithImageDecoderApi28(file, maxDecodeDimPx)
                if (decoded != null) return decoded
            }
            return runCatching {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()?.let {
                    DecodedStickerAsset.StaticBitmap(it)
                }
            }.getOrNull()
        }
    }
}

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeWithImageDecoderApi28(file: File, maxDecodeDimPx: Int): DecodedStickerAsset? {
    return runCatching {
        val source = ImageDecoder.createSource(file)
        val drawable = ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
            if (maxDecodeDimPx > 0) {
                val w = info.size.width
                val h = info.size.height
                val largest = maxOf(w, h)
                if (largest > maxDecodeDimPx && w > 0 && h > 0) {
                    val scale = maxDecodeDimPx.toFloat() / largest.toFloat()
                    decoder.setTargetSize(
                        (w * scale).toInt().coerceAtLeast(1),
                        (h * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
        }
        when (drawable) {
            is AnimatedImageDrawable -> {
                drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                DecodedStickerAsset.AnimatedDrawable(drawable)
            }
            is BitmapDrawable -> {
                drawable.bitmap?.asImageBitmap()?.let { DecodedStickerAsset.StaticBitmap(it) }
            }
            else -> {
                DecodedStickerAsset.AnimatedDrawable(drawable)
            }
        }
    }.getOrNull()
}

/**
 * Renders both static and animated WhatsApp stickers (Animated WebP via Android 9+ ImageDecoder
 * and Lottie `.was` / JSON stickers via lottie-compose).
 */
@Composable
fun AnimatedStickerImage(
    file: File?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    maxDecodeDimPx: Int = 512,
    replayNonce: Int = 0,
    placeholder: @Composable () -> Unit = { Box(modifier = Modifier.fillMaxSize()) }
) {
    val decoded by produceState<DecodedStickerAsset?>(
        initialValue = null,
        key1 = file?.absolutePath,
        key2 = file?.length() ?: 0L,
        key3 = maxDecodeDimPx
    ) {
        value = if (file != null) {
            withContext(Dispatchers.IO) {
                decodeStickerAssetFromFile(file, maxDecodeDimPx)
            }
        } else {
            null
        }
    }

    when (val asset = decoded) {
        is DecodedStickerAsset.StaticBitmap -> {
            Image(
                bitmap = asset.bitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = modifier
            )
        }

        is DecodedStickerAsset.AnimatedDrawable -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && asset.drawable is AnimatedImageDrawable) {
                val animDrawable = asset.drawable
                DisposableEffect(animDrawable) {
                    animDrawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    animDrawable.start()
                    onDispose {
                        runCatching { animDrawable.stop() }
                    }
                }
                LaunchedEffect(animDrawable, replayNonce) {
                    if (replayNonce > 0) {
                        runCatching {
                            animDrawable.stop()
                            animDrawable.start()
                        }
                    }
                }
            }
            AndroidView(
                modifier = modifier,
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        adjustViewBounds = false
                        this.contentDescription = contentDescription
                    }
                },
                update = { iv ->
                    if (iv.drawable !== asset.drawable) {
                        iv.setImageDrawable(asset.drawable)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && asset.drawable is AnimatedImageDrawable) {
                        if (!asset.drawable.isRunning) {
                            asset.drawable.start()
                        }
                    }
                }
            )
        }

        is DecodedStickerAsset.LottieJson -> {
            val composition by rememberLottieComposition(
                LottieCompositionSpec.JsonString(asset.json)
            )
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                modifier = modifier
            )
        }

        null -> placeholder()
    }
}
