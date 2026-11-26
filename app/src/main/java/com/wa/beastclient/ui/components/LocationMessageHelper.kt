package com.wa.beastclient.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.ui.theme.WaColors
import com.wa.beastclient.ui.theme.contrastingContentColor
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class ParsedLocationPayload(
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val address: String,
    val isLive: Boolean = false
)

private val LOCATION_BODY_REGEX = Regex(
    """^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*(?:\((.+)\))?\s*$"""
)

fun parseLocationPayload(body: String, mimeType: String = ""): ParsedLocationPayload? {
    val match = LOCATION_BODY_REGEX.matchEntire(body.trim()) ?: return null
    val lat = match.groupValues[1].toDoubleOrNull() ?: return null
    val lng = match.groupValues[2].toDoubleOrNull() ?: return null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null

    val isLive = mimeType.contains("live", ignoreCase = true)
    val rawLabel = match.groupValues.getOrNull(3)?.trim().orEmpty()
    val coordsFallback = String.format(Locale.US, "%.5f, %.5f", lat, lng)

    val (title, address) = when {
        rawLabel.isBlank() -> {
            (if (isLive) "Live location" else "Shared location") to coordsFallback
        }
        rawLabel.contains(" — ") -> {
            val part1 = rawLabel.substringBefore(" — ").trim()
            val part2 = rawLabel.substringAfter(" — ").trim()
            part1.ifBlank { "Shared location" } to part2.ifBlank { coordsFallback }
        }
        rawLabel.contains(", ") -> {
            val part1 = rawLabel.substringBefore(",").trim()
            part1.ifBlank { rawLabel } to rawLabel
        }
        else -> rawLabel to coordsFallback
    }

    return ParsedLocationPayload(
        latitude = lat,
        longitude = lng,
        title = title,
        address = address,
        isLive = isLive
    )
}

fun buildLocationBodyText(
    latitude: Double,
    longitude: Double,
    name: String = "",
    address: String = ""
): String {
    val cleanName = name.trim()
    val cleanAddress = address.trim()
    val label = when {
        cleanName.isNotEmpty() && cleanAddress.isNotEmpty() && cleanName != cleanAddress ->
            "$cleanName — $cleanAddress"
        cleanName.isNotEmpty() -> cleanName
        cleanAddress.isNotEmpty() -> cleanAddress
        else -> ""
    }
    val coords = String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
    return if (label.isEmpty()) coords else "$coords ($label)"
}

private val MAPS_COORDS_REGEX = Regex("""(-?\d{1,2}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)""")

fun parseLocationInput(raw: String): ParsedLocationPayload? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    parseLocationPayload(trimmed)?.let { return it }

    val match = MAPS_COORDS_REGEX.find(trimmed) ?: return null
    val lat = match.groupValues[1].toDoubleOrNull() ?: return null
    val lng = match.groupValues[2].toDoubleOrNull() ?: return null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null

    val coordsText = String.format(Locale.US, "%.5f, %.5f", lat, lng)
    return ParsedLocationPayload(
        latitude = lat,
        longitude = lng,
        title = "Shared location",
        address = coordsText
    )
}

fun openLocationInMaps(context: Context, payload: ParsedLocationPayload) {
    val rawLabel = payload.title.ifBlank { payload.address }.ifBlank {
        String.format(Locale.US, "%.5f,%.5f", payload.latitude, payload.longitude)
    }
    val encodedLabel = Uri.encode(rawLabel)
    val geoUri = Uri.parse(
        String.format(
            Locale.US,
            "geo:%.6f,%.6f?q=%.6f,%.6f(%s)",
            payload.latitude,
            payload.longitude,
            payload.latitude,
            payload.longitude,
            encodedLabel
        )
    )
    val geoIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val openedNative = runCatching {
        if (geoIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(geoIntent)
            true
        } else {
            false
        }
    }.getOrDefault(false)

    if (!openedNative) {
        val webUri = Uri.parse(
            String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", payload.latitude, payload.longitude)
        )
        val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(webIntent)
        }.onFailure {
            Toast.makeText(context, "No Maps app found on this device", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun LocationMapPreviewCanvas(
    latitude: Double,
    longitude: Double,
    isDark: Boolean,
    accentGreen: Color,
    isLive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val mapBg = if (isDark) Color(0xFF1F2C33) else Color(0xFFE7EFF2)
    val parkFill = if (isDark) Color(0xFF1A342B) else Color(0xFFD5EAD8)
    val waterFill = if (isDark) Color(0xFF163242) else Color(0xFFCCE3F0)
    val blockStroke = if (isDark) Color(0xFF2B3C45) else Color(0xFFD2DFE4)
    val arterialFill = if (isDark) Color(0xFF39505C) else Color(0xFFFFFFFF)
    val arterialEdge = if (isDark) Color(0xFF162127) else Color(0xFFBCCCD3)

    // Deterministic visual variation seeded lightly by coordinates so nearby pins still look unique
    val seedOffset = (((latitude * 1000.0) + (longitude * 1000.0)).toInt() and 0x1F) / 31f

    Box(modifier = modifier.background(mapBg)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Park & canal zones
            drawRoundRect(
                color = parkFill,
                topLeft = Offset(w * (0.06f + seedOffset * 0.08f), h * 0.12f),
                size = Size(w * 0.28f, h * 0.34f),
                cornerRadius = CornerRadius(12f, 12f)
            )
            drawRect(
                color = waterFill,
                topLeft = Offset(w * 0.68f, h * 0.56f),
                size = Size(w * 0.34f, h * 0.46f)
            )

            // City street grid blocks
            for (i in 1..4) {
                val y = h * (i / 5f)
                drawLine(
                    color = blockStroke,
                    start = Offset(0f, y),
                    end = Offset(w, y + (if (i % 2 == 0) 8f else -8f)),
                    strokeWidth = 2.6f
                )
            }
            for (i in 1..5) {
                val x = w * (i / 6f) + (seedOffset - 0.5f) * 16f
                drawLine(
                    color = blockStroke,
                    start = Offset(x, 0f),
                    end = Offset(x + (if (i % 2 == 0) -12f else 12f), h),
                    strokeWidth = 2.4f
                )
            }

            // Diagonal main arterial avenue passing near pin
            val avenuePath = Path().apply {
                moveTo(0f, h * 0.86f)
                lineTo(w * 0.42f, h * 0.56f)
                lineTo(w * 0.86f, 0f)
            }
            drawPath(
                path = avenuePath,
                color = arterialEdge,
                style = Stroke(width = 16f, cap = StrokeCap.Round)
            )
            drawPath(
                path = avenuePath,
                color = arterialFill,
                style = Stroke(width = 11f, cap = StrokeCap.Round)
            )

            // Secondary cross avenue
            drawLine(
                color = arterialEdge,
                start = Offset(w * 0.16f, 0f),
                end = Offset(w * 0.72f, h),
                strokeWidth = 10f
            )
            drawLine(
                color = arterialFill,
                start = Offset(w * 0.16f, 0f),
                end = Offset(w * 0.72f, h),
                strokeWidth = 6.5f
            )

            // Pin accuracy halo
            val center = Offset(w * 0.5f, h * 0.52f)
            drawCircle(
                color = (if (isLive) accentGreen else Color(0xFFE53935)).copy(alpha = 0.22f),
                radius = 22.dp.toPx(),
                center = center
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.25f),
                radius = 5.5.dp.toPx(),
                center = Offset(center.x, center.y + 4.dp.toPx())
            )
        }

        // Center crisp map pin icon
        Icon(
            imageVector = Icons.Rounded.LocationOn,
            contentDescription = "Location pin",
            tint = if (isLive) accentGreen else Color(0xFFE53935),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 12.dp)
                .size(38.dp)
        )

        // Subtle "Maps" external-open chip at bottom right of map image (replaces proprietary info icon)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .clip(CircleShape)
                .background(Color(0x99000000))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = "Open in Maps",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "Maps",
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun LocationMessageCard(
    msg: MessageEntity,
    payload: ParsedLocationPayload,
    colors: WaColors,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    metaCluster: @Composable () -> Unit
) {
    val isLive = payload.isLive
    Column(
        modifier = Modifier
            .widthIn(min = 232.dp, max = 272.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        LocationMapPreviewCanvas(
            latitude = payload.latitude,
            longitude = payload.longitude,
            isDark = colors.isDark,
            accentGreen = colors.brightGreen,
            isLive = isLive,
            modifier = Modifier
                .fillMaxWidth()
                .height(138.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            Text(
                text = payload.title,
                color = if (msg.isFromMe) colors.textPrimary else colors.brightGreen,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (payload.address.isNotBlank() && payload.address != payload.title) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = payload.address,
                    color = colors.textSecondary,
                    fontSize = 12.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.align(Alignment.End)) {
                metaCluster()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareLocationSheet(
    colors: WaColors,
    onDismiss: () -> Unit,
    onSendLocation: (latitude: Double, longitude: Double, name: String, address: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetBg = if (colors.isDark) Color(0xFF14181B) else colors.elevatedBar

    var isFetchingGps by remember { mutableStateOf(false) }
    var isSearchingPlace by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Tap \"Use current location\" or search a place / paste coordinates") }
    var searchQuery by remember { mutableStateOf("") }
    var labelOverride by remember { mutableStateOf("") }
    var selectedLocation by remember { mutableStateOf<ParsedLocationPayload?>(null) }

    val resolveAddressAndSelect: (Double, Double, String) -> Unit = { lat, lng, preferredTitle ->
        scope.launch {
            val (placeName, formattedAddress) = withContext(Dispatchers.IO) {
                reverseGeocodePlace(context, lat, lng)
            }
            val coordsText = String.format(Locale.US, "%.5f, %.5f", lat, lng)
            selectedLocation = ParsedLocationPayload(
                latitude = lat,
                longitude = lng,
                title = preferredTitle.ifBlank { placeName.ifBlank { "Current location" } },
                address = formattedAddress.ifBlank { coordsText }
            )
            statusText = formattedAddress.ifBlank { coordsText }
        }
    }

    val fetchCurrentDeviceLocation: () -> Unit = {
        scope.launch {
            isFetchingGps = true
            statusText = "Getting current location..."
            val loc = acquireBestLocation(context)
            isFetchingGps = false
            if (loc != null) {
                resolveAddressAndSelect(loc.latitude, loc.longitude, "")
            } else {
                statusText = "Couldn't read GPS fix right now. Make sure Location is turned on, or search/paste coordinates below."
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchCurrentDeviceLocation()
        } else {
            statusText = "Location permission denied. You can still search an address or paste coordinates."
        }
    }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            fetchCurrentDeviceLocation()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Send location",
                color = colors.textPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Search address / paste Google Maps URL / coordinates
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search place or paste lat, lng") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.brightGreen,
                        unfocusedBorderColor = colors.divider,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.brightGreen,
                    modifier = Modifier
                        .height(50.dp)
                        .clickable(enabled = searchQuery.isNotBlank() && !isSearchingPlace) {
                            val raw = searchQuery.trim()
                            val directParsed = parseLocationInput(raw)
                            if (directParsed != null) {
                                resolveAddressAndSelect(directParsed.latitude, directParsed.longitude, "")
                            } else {
                                scope.launch {
                                    isSearchingPlace = true
                                    val found = withContext(Dispatchers.IO) {
                                        forwardGeocodePlace(context, raw)
                                    }
                                    isSearchingPlace = false
                                    if (found != null) {
                                        selectedLocation = found
                                        statusText = found.address
                                    } else {
                                        statusText = "No matching place found — try coordinates like 31.49058, 74.29698"
                                    }
                                }
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSearchingPlace) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = colors.brightGreen.contrastingContentColor(),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Find location",
                                tint = colors.brightGreen.contrastingContentColor()
                            )
                        }
                    }
                }
            }

            // Current GPS quick button
            OutlinedButton(
                onClick = {
                    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (hasFine || hasCoarse) {
                        fetchCurrentDeviceLocation()
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isFetchingGps) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(17.dp),
                        color = colors.brightGreen,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = null,
                        tint = colors.brightGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isFetchingGps) "Finding current location..." else "Use my current location",
                    color = colors.brightGreen,
                    fontWeight = FontWeight.Medium
                )
            }

            // Preview card when a location is selected
            val loc = selectedLocation
            if (loc != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (colors.isDark) Color(0xFF1B2126) else colors.appSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(0.7.dp, colors.divider.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LocationMapPreviewCanvas(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            isDark = colors.isDark,
                            accentGreen = colors.brightGreen,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(132.dp)
                        )
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = labelOverride.ifBlank { loc.title },
                                color = colors.brightGreen,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = loc.address,
                                color = colors.textSecondary,
                                fontSize = 12.5.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            OutlinedTextField(
                                value = labelOverride,
                                onValueChange = { labelOverride = it },
                                placeholder = { Text("Optional place name (e.g. Home, Office)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val finalName = labelOverride.trim().ifBlank { loc.title }
                        onSendLocation(loc.latitude, loc.longitude, finalName, loc.address)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.brightGreen,
                        contentColor = colors.brightGreen.contrastingContentColor()
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Send this location", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            } else {
                Text(
                    text = statusText,
                    color = colors.textSecondary,
                    fontSize = 12.5.sp
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
private suspend fun acquireBestLocation(context: Context): Location? = withContext(Dispatchers.IO) {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null
    val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    ).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

    // Try fresh fix via LocationManagerCompat with short timeout; fall back cleanly to freshest lastKnown
    for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
        if (provider !in providers) continue
        val fresh = withTimeoutOrNull(5_500L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    LocationManagerCompat.getCurrentLocation(
                        lm,
                        provider,
                        signal,
                        ContextCompat.getMainExecutor(context)
                    ) { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                }.onFailure {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
        if (fresh != null) return@withContext fresh
    }

    providers
        .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
        .maxByOrNull { it.time }
}

private fun reverseGeocodePlace(context: Context, lat: Double, lng: Double): Pair<String, String> {
    if (!Geocoder.isPresent()) return "" to ""
    return runCatching {
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Synchronous fallback with short wait only when present
            geocoder.getFromLocation(lat, lng, 1)
        } else {
            geocoder.getFromLocation(lat, lng, 1)
        }
        val first = list?.firstOrNull() ?: return@runCatching "" to ""
        val name = first.featureName?.takeIf { !it.matches(Regex("^\\d+$")) }
            ?: first.thoroughfare
            ?: first.subLocality
            ?: first.locality
            ?: ""
        val lines = (0..first.maxAddressLineIndex)
            .mapNotNull { idx -> first.getAddressLine(idx)?.trim()?.takeIf { it.isNotEmpty() } }
            .joinToString(", ")
        name to lines
    }.getOrDefault("" to "")
}

private fun forwardGeocodePlace(context: Context, query: String): ParsedLocationPayload? {
    if (!Geocoder.isPresent()) return null
    return runCatching {
        val geocoder = Geocoder(context, Locale.getDefault())
        @Suppress("DEPRECATION")
        val list = geocoder.getFromLocationName(query, 1)
        val first = list?.firstOrNull() ?: return@runCatching null
        val name = first.featureName?.takeIf { !it.matches(Regex("^\\d+$")) }
            ?: first.thoroughfare
            ?: first.locality
            ?: query.trim()
        val addr = (0..first.maxAddressLineIndex)
            .mapNotNull { idx -> first.getAddressLine(idx)?.trim()?.takeIf { it.isNotEmpty() } }
            .joinToString(", ")
            .ifBlank { String.format(Locale.US, "%.5f, %.5f", first.latitude, first.longitude) }
        ParsedLocationPayload(
            latitude = first.latitude,
            longitude = first.longitude,
            title = name,
            address = addr
        )
    }.getOrNull()
}
