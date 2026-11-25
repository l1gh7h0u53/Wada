package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.data.ClothingItem
import com.example.data.WadaCombination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.roundToInt

enum class WeatherCondition(val label: String) {
    CLEAR("Clear"),
    PARTLY_CLOUDY("Partly Cloudy"),
    CLOUDY("Cloudy"),
    FOG("Foggy"),
    RAIN("Rainy"),
    SNOW("Snowy"),
    STORM("Stormy")
}

data class WeatherInfo(
    val temperatureCelsius: Double,
    val condition: WeatherCondition,
    val windSpeedKmh: Double,
    val fetchedAtMillis: Long = System.currentTimeMillis()
) {
    val formattedTemperature: String
        get() {
            val c = temperatureCelsius.roundToInt()
            val f = ((temperatureCelsius * 9.0 / 5.0) + 32.0).roundToInt()
            return "$c°C / $f°F"
        }
}

sealed interface WeatherState {
    data object Disabled : WeatherState
    data object PermissionRequired : WeatherState
    data object Loading : WeatherState
    data class Ready(val info: WeatherInfo) : WeatherState
    data class Error(val message: String) : WeatherState
}

data class WeatherGarmentMatch(
    val item: ClothingItem,
    val reason: String
)

/**
 * Lightweight, privacy-conscious coarse-location & Open-Meteo weather fetcher.
 * Never accesses location or network unless explicitly enabled by the user.
 */
object WeatherService {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    fun hasCoarseLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun fetchLocalWeather(context: Context): WeatherState {
        if (!hasCoarseLocationPermission(context)) {
            return WeatherState.PermissionRequired
        }

        val location = getCoarseLocation(context)
            ?: return WeatherState.Error("Couldn't determine coarse location. Check device Location settings.")

        return fetchOpenMeteo(location.latitude, location.longitude)
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCoarseLocation(context: Context): Location? = withContext(Dispatchers.Main) {
        if (!hasCoarseLocationPermission(context)) return@withContext null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        val candidateProviders = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }

        // 1. Check for a reasonably fresh cached coarse location (<= 30 minutes)
        var bestCached: Location? = null
        val now = System.currentTimeMillis()
        for (provider in candidateProviders) {
            val cached = try {
                locationManager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
            if (cached != null && (bestCached == null || cached.time > bestCached.time)) {
                bestCached = cached
            }
        }

        if (bestCached != null && (now - bestCached.time) <= TimeUnit.MINUTES.toMillis(30)) {
            return@withContext bestCached
        }

        // 2. Try a lightweight single coarse location fix with a short timeout
        val activeProvider = candidateProviders.firstOrNull { provider ->
            try {
                locationManager.isProviderEnabled(provider)
            } catch (_: Exception) {
                false
            }
        }

        if (activeProvider != null) {
            val fresh = withTimeoutOrNull(5000L) {
                requestSingleLocation(context, locationManager, activeProvider)
            }
            if (fresh != null) return@withContext fresh
        }

        // 3. Fall back to older cached coarse location if available
        bestCached
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleLocation(
        context: Context,
        locationManager: LocationManager,
        provider: String
    ): Location? = suspendCancellableCoroutine { continuation ->
        if (!hasCoarseLocationPermission(context)) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try {
                locationManager.getCurrentLocation(
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(context)
                ) { loc ->
                    if (continuation.isActive) {
                        continuation.resume(loc)
                    }
                }
            } catch (_: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) continuation.resume(location)
                }

                @Deprecated("Deprecated in API 29")
                override fun onStatusChanged(p: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) {}
            }
            continuation.invokeOnCancellation {
                try {
                    locationManager.removeUpdates(listener)
                } catch (_: Exception) {
                }
            }
            try {
                @Suppress("DEPRECATION")
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (_: Exception) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    private suspend fun fetchOpenMeteo(lat: Double, lon: Double): WeatherState = withContext(Dispatchers.IO) {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,wind_speed_10m&temperature_unit=celsius",
            lat,
            lon
        )

        try {
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext WeatherState.Error("Weather service unavailable (${response.code}).")
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    return@withContext WeatherState.Error("Empty weather response.")
                }
                val info = parseOpenMeteoResponse(body)
                    ?: return@withContext WeatherState.Error("Could not read weather data.")
                WeatherState.Ready(info)
            }
        } catch (_: Exception) {
            WeatherState.Error("Unable to reach weather service. Check your connection.")
        }
    }

    fun parseOpenMeteoResponse(jsonString: String): WeatherInfo? {
        return try {
            val root = JSONObject(jsonString)
            val current = root.optJSONObject("current") ?: return null
            val temp = current.getDouble("temperature_2m")
            val code = current.optInt("weather_code", 0)
            val wind = current.optDouble("wind_speed_10m", 0.0)
            WeatherInfo(
                temperatureCelsius = temp,
                condition = mapWmoCodeToCondition(code),
                windSpeedKmh = wind
            )
        } catch (_: Exception) {
            null
        }
    }

    fun mapWmoCodeToCondition(code: Int): WeatherCondition {
        return when (code) {
            0 -> WeatherCondition.CLEAR
            1, 2 -> WeatherCondition.PARTLY_CLOUDY
            3 -> WeatherCondition.CLOUDY
            45, 48 -> WeatherCondition.FOG
            in 51..67, in 80..82 -> WeatherCondition.RAIN
            in 71..77, 85, 86 -> WeatherCondition.SNOW
            in 95..99 -> WeatherCondition.STORM
            else -> WeatherCondition.PARTLY_CLOUDY
        }
    }
}

/**
 * Pure-logic advisor pairing current temperature and weather conditions with the user's
 * closet garments aligned to the Daily Inspiration Wada combination.
 */
object WeatherOutfitAdvisor {

    fun buildAdvice(weather: WeatherInfo): String {
        val temp = weather.temperatureCelsius
        val base = when {
            temp < 10.0 -> "Chilly (${weather.formattedTemperature}) — reach for warm layers and cozy textures."
            temp < 15.0 -> "Cold (${weather.formattedTemperature}) — favor warm pieces and insulating layers."
            temp < 24.0 -> "Normal (${weather.formattedTemperature}) — ideal for light pieces in today's Wada palette."
            else -> "Warm (${weather.formattedTemperature}) — lean into light, breathable pieces."
        }
        val conditionAddOn = when (weather.condition) {
            WeatherCondition.RAIN -> " Rain ahead: anchor with darker bottoms and sturdy footwear."
            WeatherCondition.SNOW -> " Snowy conditions: pair deeper tones with warm outer accessories."
            WeatherCondition.STORM -> " Stormy weather: stick to practical, darker protective layers."
            WeatherCondition.CLEAR -> if (temp >= 22.0) " Bright sun favors airy, luminous tones." else ""
            else -> ""
        }
        val windAddOn = if (weather.windSpeedKmh >= 22.0 && conditionAddOn.isEmpty()) {
            " Breezy outside—consider a wind-ready layer."
        } else ""
        return (base + conditionAddOn + windAddOn).trim()
    }

    /**
     * Suggests up to 4 closet pieces (one per distinct garment category) that harmonize with
     * [combination] while matching WARM items to cold weather and LIGHT items to warm/normal weather.
     */
    fun suggestMatches(
        clothes: List<ClothingItem>,
        combination: WadaCombination,
        weather: WeatherInfo
    ): List<WeatherGarmentMatch> {
        if (clothes.isEmpty() || combination.colors.isEmpty()) return emptyList()

        val temp = weather.temperatureCelsius
        // Cold weather (< 15°C, snow, or cold storm) favors WARM items.
        // Normal (15°C–23.9°C) and warm (24°C+) weather favor LIGHT items.
        val isColdWeather = temp < 15.0 ||
            weather.condition == WeatherCondition.SNOW ||
            (weather.condition == WeatherCondition.STORM && temp < 18.0)

        val preferLightTone = temp >= 24.0 &&
            weather.condition != WeatherCondition.RAIN &&
            weather.condition != WeatherCondition.STORM
        val preferDeepTone = isColdWeather ||
            weather.condition == WeatherCondition.RAIN ||
            weather.condition == WeatherCondition.STORM

        val grouped = clothes.groupBy { it.category }
        val preferredOrder = listOf("Tops", "Bottoms", "Shoes", "Accessories")
        val orderedCategories = (preferredOrder.filter { it in grouped } +
            grouped.keys.filterNot { it in preferredOrder }).take(4)

        return orderedCategories.mapNotNull { category ->
            val candidates = grouped[category].orEmpty()
            val scored = candidates.minByOrNull { item ->
                val minDeltaE = combination.colors.minOf { paletteHex ->
                    ColorMatcher.calculateDeltaEFromHex(item.hexColor, paletteHex)
                }
                val thermalScore = when (item.thermalType.uppercase()) {
                    "WARM" -> if (isColdWeather) -40.0 else 40.0
                    "LIGHT" -> if (isColdWeather) 40.0 else -40.0
                    else -> {
                        val (_, _, v) = run {
                            val (r, g, b) = ColorMatcher.hexToRgb(item.hexColor)
                            ColorGroupClassifier.rgbToHsv(r, g, b)
                        }
                        when {
                            preferLightTone && v >= 0.68f -> -7.0
                            preferDeepTone && v <= 0.45f -> -7.0
                            else -> 0.0
                        }
                    }
                }
                minDeltaE + thermalScore
            } ?: return@mapNotNull null

            val groupLabel = scored.colorGroup.ifBlank {
                ColorGroupClassifier.classifyHex(scored.hexColor).label
            }
            val weatherTag = when (scored.thermalType.uppercase()) {
                "WARM" -> when {
                    weather.condition == WeatherCondition.SNOW -> "Warm • Snow-ready"
                    weather.condition == WeatherCondition.RAIN -> "Warm • Rain-ready"
                    else -> "Warm • Cold-ready"
                }
                "LIGHT" -> if (temp >= 24.0) "Light • Warm weather" else "Light • Normal weather"
                else -> when {
                    weather.condition == WeatherCondition.RAIN && (category == "Bottoms" || category == "Shoes") -> "Rain-ready"
                    weather.condition == WeatherCondition.SNOW -> "Winter-ready"
                    temp >= 24.0 -> "Breezy tone"
                    isColdWeather -> "Cool-day tone"
                    else -> "Palette match"
                }
            }
            WeatherGarmentMatch(
                item = scored,
                reason = "$groupLabel • $weatherTag"
            )
        }
    }
}
