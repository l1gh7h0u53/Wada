package com.example.data

import android.content.Context
import com.example.util.ColorGroupClassifier
import com.example.util.WeatherCondition
import com.example.util.WeatherInfo
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ClothingRepository(
    private val clothingDao: ClothingDao,
    private val context: Context,
    private val localCacheDao: LocalCacheDao = AppDatabase.getDatabase(context).localCacheDao()
) {
    val allClothing: Flow<List<ClothingItem>> = clothingDao.getAllClothing()

    val colorGroupCounts: Flow<Map<String, Int>> = clothingDao.getColorGroupCounts()
        .map { rows -> rows.associate { it.colorGroup to it.itemCount } }

    fun getClothingByCategory(category: String): Flow<List<ClothingItem>> {
        return clothingDao.getClothingByCategory(category)
    }

    fun getClothingByColorGroup(colorGroup: String): Flow<List<ClothingItem>> {
        return clothingDao.getClothingByColorGroup(colorGroup)
    }

    /**
     * Reactive Room SQLite query for offline wardrobe viewing and color filtering.
     */
    fun observeFilteredWardrobe(
        category: String,
        colorGroup: String,
        query: String
    ): Flow<List<ClothingItem>> {
        return clothingDao.filterClothingOffline(
            category = category,
            colorGroup = colorGroup,
            query = query.trim()
        )
    }

    suspend fun insertClothing(item: ClothingItem) {
        withContext(Dispatchers.IO) {
            clothingDao.insertClothing(item)
        }
    }

    suspend fun deleteClothing(id: Int) {
        withContext(Dispatchers.IO) {
            clothingDao.deleteClothingById(id)
        }
    }

    suspend fun toggleFavorite(id: Int, isFavorite: Boolean) {
        withContext(Dispatchers.IO) {
            clothingDao.updateFavorite(id, isFavorite)
        }
    }

    suspend fun updateClothingColorAndPath(
        id: Int,
        hexColor: String,
        filePath: String,
        colorGroup: String,
        thermalType: String,
        wadaColorName: String
    ) {
        withContext(Dispatchers.IO) {
            clothingDao.updateColorAndFilePath(
                id = id,
                hexColor = hexColor,
                filePath = filePath,
                colorGroup = colorGroup,
                thermalType = thermalType,
                wadaColorName = wadaColorName
            )
        }
    }

    suspend fun updateColorMetadata(id: Int, colorGroup: String, wadaColorName: String) {
        withContext(Dispatchers.IO) {
            clothingDao.updateColorMetadata(id, colorGroup, wadaColorName)
        }
    }

    suspend fun saveWeatherCache(info: WeatherInfo) {
        withContext(Dispatchers.IO) {
            localCacheDao.upsertCachedWeather(
                CachedWeather(
                    id = 1,
                    temperatureCelsius = info.temperatureCelsius,
                    conditionName = info.condition.name,
                    windSpeedKmh = info.windSpeedKmh,
                    fetchedAtMillis = info.fetchedAtMillis
                )
            )
        }
    }

    suspend fun getCachedWeatherInfo(): WeatherInfo? = withContext(Dispatchers.IO) {
        val cached = localCacheDao.getCachedWeatherOnce() ?: return@withContext null
        val condition = WeatherCondition.entries.firstOrNull { it.name == cached.conditionName }
            ?: WeatherCondition.PARTLY_CLOUDY
        WeatherInfo(
            temperatureCelsius = cached.temperatureCelsius,
            condition = condition,
            windSpeedKmh = cached.windSpeedKmh,
            fetchedAtMillis = cached.fetchedAtMillis,
            isFromCache = true
        )
    }

    suspend fun clearWeatherCache() {
        withContext(Dispatchers.IO) {
            localCacheDao.clearCachedWeather()
        }
    }

    fun getThemeSetting(): String {
        val prefs = context.getSharedPreferences("wada_settings", Context.MODE_PRIVATE)
        return prefs.getString("theme_preference", "SYSTEM") ?: "SYSTEM"
    }

    fun setThemeSetting(theme: String) {
        val prefs = context.getSharedPreferences("wada_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("theme_preference", theme).apply()
    }

    fun getMonetMode(): String {
        val prefs = context.getSharedPreferences("wada_settings", Context.MODE_PRIVATE)
        return prefs.getString("monet_mode", "MONET") ?: "MONET"
    }

    fun setMonetMode(mode: String) {
        val prefs = context.getSharedPreferences("wada_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("monet_mode", mode).apply()
    }

    fun getCategories(): List<String> {
        val prefs = context.getSharedPreferences("wada_settings", Context.MODE_PRIVATE)
        val catsString = prefs.getString("custom_categories", "Tops,Bottoms,Shoes,Accessories") ?: "Tops,Bottoms,Shoes,Accessories"
        return catsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun saveCategories(categories: List<String>) {
        val prefs = context.getSharedPreferences("wada_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("custom_categories", categories.joinToString(",")).apply()
    }

    fun getWeatherSuggestionsEnabled(): Boolean {
        val prefs = context.getSharedPreferences("wada_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("weather_suggestions_enabled", false)
    }

    fun setWeatherSuggestionsEnabled(enabled: Boolean) {
        val prefs = context.getSharedPreferences("wada_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("weather_suggestions_enabled", enabled).apply()
    }

    private var cachedColors: List<WadaColor>? = null

    /**
     * Cache-aside loader for all 143 Sanzo Wada dictionary colors:
     * 1. Returns in-memory cache if already hydrated.
     * 2. Reads from Room SQLite (`cached_wada_colors`) if populated.
     * 3. Parses bundled JSON assets and persists them into Room SQLite for future offline use.
     */
    suspend fun loadWadaColors(): List<WadaColor> = withContext(Dispatchers.IO) {
        cachedColors?.let { return@withContext it }

        try {
            val dbCached = localCacheDao.getCachedWadaColorsOnce()
            if (dbCached.isNotEmpty()) {
                val mapped = dbCached.map { entity ->
                    WadaColor(
                        index = entity.colorIndex,
                        name = entity.name,
                        hex = entity.hex,
                        combinations = entity.combinationsCsv
                            .split(",")
                            .mapNotNull { it.trim().toIntOrNull() }
                    )
                }
                cachedColors = mapped
                return@withContext mapped
            }
        } catch (_: Exception) {
            // Fall through to asset load if DB cache read fails
        }

        try {
            val jsonString = context.assets.open("wada_colors.json").bufferedReader().use { it.readText() }
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
            val listType = Types.newParameterizedType(List::class.java, WadaColor::class.java)
            val adapter = moshi.adapter<List<WadaColor>>(listType)
            val colors = adapter.fromJson(jsonString) ?: emptyList()

            if (colors.isNotEmpty()) {
                try {
                    localCacheDao.insertWadaColors(
                        colors.map { color ->
                            CachedWadaColor(
                                colorIndex = color.index,
                                name = color.name,
                                hex = color.hex,
                                combinationsCsv = color.combinations.joinToString(","),
                                colorGroup = ColorGroupClassifier.classifyHex(color.hex).label
                            )
                        }
                    )
                } catch (_: Exception) {
                }
            }

            cachedColors = colors
            colors
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Parse and load pre-packaged Sanzo Wada combinations from local Assets folder / Room-cached colors.
     */
    suspend fun loadWadaCombinations(): List<WadaCombination> = withContext(Dispatchers.IO) {
        try {
            val colors = loadWadaColors()

            val comboMap = mutableMapOf<Int, MutableList<WadaColor>>()
            for (color in colors) {
                for (comboId in color.combinations) {
                    comboMap.getOrPut(comboId) { mutableListOf() }.add(color)
                }
            }

            comboMap.map { (id, comboColors) ->
                val hexColors = comboColors.map { it.hex }
                val part = when {
                    id <= 120 -> 1
                    id <= 240 -> 2
                    else -> 3
                }
                val name = comboColors.joinToString(" • ") { it.name }
                val description = "A historical ${comboColors.size}-color palette compiled from Sanzo Wada's 1930s color dictionary."
                WadaCombination(
                    id = id,
                    name = name,
                    colors = hexColors,
                    part = part,
                    description = description
                )
            }.sortedBy { it.id }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val jsonString = context.assets.open("wada_combinations.json").bufferedReader().use { it.readText() }
                val moshi = Moshi.Builder()
                    .add(KotlinJsonAdapterFactory())
                    .build()
                val listType = Types.newParameterizedType(List::class.java, WadaCombination::class.java)
                val adapter = moshi.adapter<List<WadaCombination>>(listType)
                adapter.fromJson(jsonString) ?: emptyList()
            } catch (ex: Exception) {
                ex.printStackTrace()
                emptyList()
            }
        }
    }
}
