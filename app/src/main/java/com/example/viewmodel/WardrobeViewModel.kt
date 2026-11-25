package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ClothingItem
import com.example.data.ClothingRepository
import com.example.data.WadaColor
import com.example.data.WadaCombination
import com.example.util.ColorGroupClassifier
import com.example.util.ColorMatcher
import com.example.util.ColorUtils
import com.example.util.NetworkStatusObserver
import com.example.util.StyleTagHelper
import com.example.util.WadaColorRegistry
import com.example.util.WeatherGarmentMatch
import com.example.util.WeatherInfo
import com.example.util.WeatherOutfitAdvisor
import com.example.util.WeatherService
import com.example.util.WeatherState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class WardrobeViewModel(
    private val repository: ClothingRepository
) : ViewModel() {

    // All registered clothing items
    val allClothing: StateFlow<List<ClothingItem>> = repository.allClothing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Pre-packaged individual Sanzo Wada colors (143 dictionary colors)
    private val _wadaColors = MutableStateFlow<List<WadaColor>>(emptyList())
    val wadaColors: StateFlow<List<WadaColor>> = _wadaColors.asStateFlow()

    // Pre-packaged Sanzo Wada combinations
    private val _wadaCombinations = MutableStateFlow<List<WadaCombination>>(emptyList())
    val wadaCombinations = _wadaCombinations.asStateFlow()

    // Currently selected base garment for matching
    private val _selectedGarment = MutableStateFlow<ClothingItem?>(null)
    val selectedGarment = _selectedGarment.asStateFlow()

    // Currently selected Wada combination for styling
    private val _selectedCombination = MutableStateFlow<WadaCombination?>(null)
    val selectedCombination = _selectedCombination.asStateFlow()

    // Wada Palette exploration history (last 5 explored combinations)
    private val _paletteHistory = MutableStateFlow<List<WadaCombination>>(emptyList())
    val paletteHistory: StateFlow<List<WadaCombination>> = _paletteHistory.asStateFlow()

    // App Theme setting: "SYSTEM", "LIGHT", "DARK"
    private val _themeSetting = MutableStateFlow("SYSTEM")
    val themeSetting = _themeSetting.asStateFlow()

    // Monet dynamic color vs Standard historic palette: "MONET", "STANDARD"
    private val _monetMode = MutableStateFlow("MONET")
    val monetMode = _monetMode.asStateFlow()

    // Dynamic apparel categories list state flow
    private val _categories = MutableStateFlow<List<String>>(listOf("Tops", "Bottoms", "Shoes", "Accessories"))
    val categories = _categories.asStateFlow()

    // Opt-in local weather suggestions state (strictly disabled until user accepts)
    private val _weatherEnabled = MutableStateFlow(false)
    val weatherEnabled: StateFlow<Boolean> = _weatherEnabled.asStateFlow()

    private val _weatherState = MutableStateFlow<WeatherState>(WeatherState.Disabled)
    val weatherState: StateFlow<WeatherState> = _weatherState.asStateFlow()

    // Reactive offline state via ConnectivityManager (false = online, true = offline)
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()
    private var networkObserverJob: Job? = null

    // Persistent Closet filter state backed by SQLite Room queries
    private val _closetFilter = MutableStateFlow(ClosetFilterState())
    val closetFilter: StateFlow<ClosetFilterState> = _closetFilter.asStateFlow()

    /**
     * SQLite Room-backed primary color group counts for offline filter pills.
     */
    val colorGroupCounts: StateFlow<Map<String, Int>> = repository.colorGroupCounts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    /**
     * Offline-first Closet list: filters by category, favorites, and primary color group directly
     * inside SQLite Room (`clothing_items` indexes), then applies local Wada perceptual search
     * and style-tag filtering from cached data with zero network requirement.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredClosetClothing: StateFlow<List<ClothingItem>> = combine(
        _closetFilter,
        _wadaColors
    ) { filter, colors -> filter to colors }
        .flatMapLatest { (filter, colors) ->
            repository.observeFilteredWardrobe(
                category = filter.category,
                colorGroup = filter.colorGroup,
                query = ""
            ).combine(MutableStateFlow(filter to colors)) { dbItems, (activeFilter, wadaList) ->
                dbItems.filter { item ->
                    val matchesStyle = StyleTagHelper.matchesStyleTag(item, activeFilter.styleTag, wadaList)
                    val matchesColor = if (activeFilter.colorQuery.isBlank()) {
                        true
                    } else {
                        val directSqlTextMatch = item.wadaColorName.contains(activeFilter.colorQuery, ignoreCase = true) ||
                            item.colorGroup.contains(activeFilter.colorQuery, ignoreCase = true) ||
                            item.hexColor.contains(activeFilter.colorQuery, ignoreCase = true)
                        directSqlTextMatch || WadaColorRegistry.matchesWadaColorSearch(
                            item.hexColor,
                            activeFilter.colorQuery,
                            wadaList
                        )
                    }
                    matchesStyle && matchesColor
                }
            }
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Track garment IDs already checked during this session to avoid repeated image decoding
    private val autoTaggedGroupIds = mutableSetOf<Int>()
    private val autoTaggedWadaIds = mutableSetOf<Int>()

    /**
     * Daily Style Inspiration derived reactively from Sanzo Wada's dictionary, the user's
     * logged closet colors, and (when opted in) local weather conditions.
     * Remains null until at least 2 distinct colors are logged in the closet.
     */
    val dailyInspiration: StateFlow<DailyInspirationState?> = combine(
        allClothing,
        _wadaCombinations,
        _weatherState
    ) { clothes, combinations, weather ->
        val weatherInfo = (weather as? WeatherState.Ready)?.info
        computeDailyInspiration(clothes, combinations, weatherInfo = weatherInfo)
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val theme = repository.getThemeSetting()
            _themeSetting.value = theme
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val monet = repository.getMonetMode()
            _monetMode.value = monet
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _categories.value = repository.getCategories()
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val enabled = repository.getWeatherSuggestionsEnabled()
            _weatherEnabled.value = enabled
            if (enabled) {
                repository.getCachedWeatherInfo()?.let { cached ->
                    _weatherState.value = WeatherState.Ready(cached)
                }
            }
        }
        // Load Wada dictionary colors from Room cache / local JSON assets and backfill garment color metadata
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val colors = repository.loadWadaColors()
            _wadaColors.value = colors
            val list = repository.loadWadaCombinations()
            _wadaCombinations.value = list
            if (list.isNotEmpty()) {
                val first = list.first()
                _selectedCombination.value = first
                recordPaletteExploration(first)
            }
        }
        // Automatically tag any existing wardrobe items missing primary color group or cached Wada color label
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            combine(repository.allClothing, _wadaColors) { items, colors -> items to colors }
                .collect { (items, colors) ->
                    for (item in items) {
                        val shouldBackfillGroup = item.colorGroup.isBlank() && autoTaggedGroupIds.add(item.id)
                        val shouldBackfillWada = item.wadaColorName.isBlank() && colors.isNotEmpty() && autoTaggedWadaIds.add(item.id)
                        if (shouldBackfillGroup || shouldBackfillWada) {
                            val (resolvedHex, group) = if (item.colorGroup.isBlank()) {
                                ColorGroupClassifier.analyzeSavedImagery(item.filePath, item.hexColor)
                            } else {
                                item.hexColor to (com.example.util.PrimaryColorGroup.fromLabel(item.colorGroup)
                                    ?: ColorGroupClassifier.classifyHex(item.hexColor))
                            }
                            val wadaLabel = if (colors.isNotEmpty()) {
                                WadaColorRegistry.buildSearchableWadaLabel(resolvedHex, colors)
                            } else {
                                item.wadaColorName
                            }
                            repository.updateColorMetadata(item.id, group.label, wadaLabel)
                        }
                    }
                }
        }
    }

    fun startNetworkMonitoring(context: Context) {
        if (networkObserverJob != null) return
        val appContext = context.applicationContext
        _isOffline.value = !NetworkStatusObserver.isCurrentlyOnline(appContext)
        networkObserverJob = viewModelScope.launch {
            NetworkStatusObserver.observeIsOnline(appContext).collect { online ->
                _isOffline.value = !online
            }
        }
    }

    fun updateClosetCategoryFilter(category: String) {
        _closetFilter.value = _closetFilter.value.copy(category = category)
    }

    fun updateClosetColorGroupFilter(colorGroup: String) {
        _closetFilter.value = _closetFilter.value.copy(colorGroup = colorGroup)
    }

    fun updateClosetColorQuery(query: String) {
        _closetFilter.value = _closetFilter.value.copy(colorQuery = query)
    }

    fun updateClosetStyleTagFilter(styleTag: String) {
        _closetFilter.value = _closetFilter.value.copy(styleTag = styleTag)
    }

    fun resetClosetFilters() {
        _closetFilter.value = ClosetFilterState(category = _closetFilter.value.category)
    }

    /**
     * Deterministically selects today's Daily Style Inspiration from combinations that match
     * at least 2 distinct colors currently logged in the user's closet.
     * Only the matched closet colors are included in [DailyInspirationState.displayedColors].
     */
    fun computeDailyInspiration(
        clothes: List<ClothingItem>,
        combinations: List<WadaCombination>,
        calendar: java.util.Calendar = java.util.Calendar.getInstance(),
        weatherInfo: WeatherInfo? = null
    ): DailyInspirationState? {
        if (combinations.isEmpty()) return null

        val distinctClosetColors = clothes
            .map { it.hexColor.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()

        // Require at least 2 distinct colors logged in the closet before showing daily inspiration
        if (distinctClosetColors.size < 2) return null

        data class MatchCandidate(
            val combination: WadaCombination,
            val matchedClosetColors: List<String>,
            val totalDeltaE: Double
        )

        val allScored = combinations.map { combo ->
            // Build all (paletteColorIdx, closetColorIdx, deltaE) pairs and match greedily by lowest Delta-E
            val pairs = mutableListOf<Triple<Int, Int, Double>>()
            combo.colors.forEachIndexed { pIdx, pHex ->
                distinctClosetColors.forEachIndexed { cIdx, cHex ->
                    pairs.add(Triple(pIdx, cIdx, ColorMatcher.calculateDeltaEFromHex(pHex, cHex)))
                }
            }
            pairs.sortBy { it.third }

            val usedPalette = mutableSetOf<Int>()
            val usedCloset = mutableSetOf<Int>()
            val matched = mutableListOf<Pair<Int, Double>>() // closetIdx to deltaE

            for ((pIdx, cIdx, deltaE) in pairs) {
                if (pIdx !in usedPalette && cIdx !in usedCloset) {
                    usedPalette.add(pIdx)
                    usedCloset.add(cIdx)
                    matched.add(cIdx to deltaE)
                }
            }

            // Threshold-filtered matched closet colors (perceptually faithful matches)
            val closeMatches = matched.filter { it.second <= 30.0 }
            val resultColors = if (closeMatches.size >= 2) {
                closeMatches.map { distinctClosetColors[it.first] }
            } else {
                // Fallback to the closest 2 matched closet colors if no palette meets strict threshold
                matched.take(2).map { distinctClosetColors[it.first] }
            }
            val scoreSum = if (closeMatches.size >= 2) {
                closeMatches.sumOf { it.second } / closeMatches.size
            } else {
                matched.take(2).sumOf { it.second } / matched.take(2).size.coerceAtLeast(1)
            }

            MatchCandidate(
                combination = combo,
                matchedClosetColors = resultColors,
                totalDeltaE = scoreSum
            ) to (closeMatches.size >= 2)
        }

        val strictMatches = allScored.filter { it.second }.map { it.first }
        val eligiblePool = if (strictMatches.isNotEmpty()) {
            strictMatches
        } else {
            allScored.map { it.first }
                .sortedBy { it.totalDeltaE }
                .take(5)
        }

        if (eligiblePool.isEmpty()) return null

        val year = calendar.get(java.util.Calendar.YEAR)
        val dayOfYear = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val dailySeed = year * 1000L + dayOfYear
        val chosen = eligiblePool[kotlin.random.Random(dailySeed).nextInt(eligiblePool.size)]

        val weatherMatches = if (weatherInfo != null) {
            WeatherOutfitAdvisor.suggestMatches(clothes, chosen.combination, weatherInfo)
        } else {
            emptyList()
        }
        val weatherAdvice = weatherInfo?.let { WeatherOutfitAdvisor.buildAdvice(it) }

        return DailyInspirationState(
            combination = chosen.combination,
            displayedColors = chosen.matchedClosetColors,
            weatherMatches = weatherMatches,
            weatherAdvice = weatherAdvice
        )
    }

    /**
     * Opt in to coarse-location weather suggestions and fetch current weather.
     */
    fun enableWeatherSuggestions(context: Context) {
        _weatherEnabled.value = true
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.setWeatherSuggestionsEnabled(true)
        }
        fetchWeatherIfEnabled(context, forceRefresh = true)
    }

    /**
     * Opt out of location-based weather suggestions and clear cached weather state.
     */
    fun disableWeatherSuggestions() {
        _weatherEnabled.value = false
        _weatherState.value = WeatherState.Disabled
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.setWeatherSuggestionsEnabled(false)
            repository.clearWeatherCache()
        }
    }

    /**
     * Fetches current weather only if the user has explicitly opted in.
     * Falls back to the Room SQLite cached weather snapshot when offline or unreachable.
     */
    fun fetchWeatherIfEnabled(context: Context, forceRefresh: Boolean = false) {
        if (!_weatherEnabled.value) {
            _weatherState.value = WeatherState.Disabled
            return
        }
        if (!WeatherService.hasCoarseLocationPermission(context)) {
            _weatherState.value = WeatherState.PermissionRequired
            return
        }
        val currentReady = _weatherState.value as? WeatherState.Ready
        if (!forceRefresh && currentReady != null && !currentReady.info.isFromCache &&
            (System.currentTimeMillis() - currentReady.info.fetchedAtMillis) < 15 * 60 * 1000L
        ) {
            return
        }
        if (_weatherState.value is WeatherState.Loading) return

        // If offline, serve the SQLite cached weather immediately without waiting on network timeout
        if (!NetworkStatusObserver.isCurrentlyOnline(context.applicationContext)) {
            _isOffline.value = true
            viewModelScope.launch {
                val cached = repository.getCachedWeatherInfo()
                _weatherState.value = if (cached != null) {
                    WeatherState.Ready(cached)
                } else {
                    WeatherState.Error("Offline — no cached weather available yet.")
                }
            }
            return
        }

        _weatherState.value = WeatherState.Loading
        viewModelScope.launch {
            when (val result = WeatherService.fetchLocalWeather(context)) {
                is WeatherState.Ready -> {
                    repository.saveWeatherCache(result.info)
                    _weatherState.value = result
                }
                else -> {
                    val cached = repository.getCachedWeatherInfo()
                    _weatherState.value = if (cached != null) {
                        WeatherState.Ready(cached)
                    } else {
                        result
                    }
                }
            }
        }
    }

    fun updateCategories(newCats: List<String>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repository.saveCategories(newCats)
            _categories.value = newCats
        }
    }

    /**
     * Records an explored Wada combination into the last 5 palette history.
     */
    fun recordPaletteExploration(combo: WadaCombination) {
        val currentList = _paletteHistory.value.toMutableList()
        currentList.removeAll { it.id == combo.id }
        currentList.add(0, combo)
        _paletteHistory.value = currentList.take(5)
    }

    /**
     * Select a base garment to match. This will automatically:
     * 1. Find the nearest Wada combination representing this color.
     * 2. Populate recommended complementary pieces from other categories.
     */
    fun selectGarment(item: ClothingItem?) {
        _selectedGarment.value = item
        if (item != null) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                matchWadaAndRecommend(item)
            }
        }
    }

    fun selectCombination(combo: WadaCombination) {
        _selectedCombination.value = combo
        recordPaletteExploration(combo)
    }

    /**
     * Finds the closest Wada combination to the selected garment's color,
     * and sets it to drive the dynamic theme color-matches.
     */
    private suspend fun matchWadaAndRecommend(garment: ClothingItem) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val combinations = _wadaCombinations.value
        if (combinations.isEmpty()) return@withContext

        var bestCombo: WadaCombination? = null
        var minDistance = Double.MAX_VALUE

        // Find the Wada combination where ANY color is closest to the garment Hex
        for (combo in combinations) {
            for (colorHex in combo.colors) {
                val distance = ColorMatcher.calculateDeltaEFromHex(garment.hexColor, colorHex)
                if (distance < minDistance) {
                    minDistance = distance
                    bestCombo = combo
                }
            }
        }

        bestCombo?.let {
            _selectedCombination.value = it
            recordPaletteExploration(it)
        }
    }

    /**
     * Computes matching garments from the user's database based on the selected Wada palette.
     * For a selected combination [WadaColor0, WadaColor1, WadaColor2...], we:
     * - Categorize available clothing items.
     * - Find garments of secondary categories (other than the selected garment's category) that
     *   have the lowest Delta E to the other colors in the Wada combination.
     */
    val outfitRecommendations: StateFlow<OutfitRecommendation?> = combine(
        allClothing,
        _selectedGarment,
        _selectedCombination
    ) { clothes, selectedGar, combo ->
        if (combo == null || clothes.isEmpty()) return@combine null

        val baseGarment = selectedGar
        val baseColor = baseGarment?.hexColor ?: combo.colors.first()
        val baseCategory = baseGarment?.category ?: "Tops"

        // Exclude the selected item from candidates
        val eligibleCandidates = if (baseGarment != null) {
            clothes.filter { it.id != baseGarment.id }
        } else {
            clothes
        }

        // Divide candidates by category
        val tops = eligibleCandidates.filter { it.category == "Tops" }
        val bottoms = eligibleCandidates.filter { it.category == "Bottoms" }
        val shoes = eligibleCandidates.filter { it.category == "Shoes" }
        val accessories = eligibleCandidates.filter { it.category == "Accessories" }

        // We want to match colors relative to the combination
        val paletteColors = combo.colors

        // To assign coordinates, we pair colors
        // For example, if we have combo.colors [Color0, Color1, Color2]
        // Color0 -> Primary
        // Color1 -> Secondary
        // Color2 -> Tertiary
        val matchedTops = if (baseCategory == "Tops" && baseGarment != null) {
            baseGarment
        } else {
            findClosestInList(tops, paletteColors.getOrNull(0) ?: baseColor)
        }

        val matchedBottoms = if (baseCategory == "Bottoms" && baseGarment != null) {
            baseGarment
        } else {
            findClosestInList(bottoms, paletteColors.getOrNull(1) ?: baseColor)
        }

        val matchedShoes = if (baseCategory == "Shoes" && baseGarment != null) {
            baseGarment
        } else {
            findClosestInList(shoes, paletteColors.getOrNull(2) ?: baseColor)
        }

        val matchedAccessories = if (baseCategory == "Accessories" && baseGarment != null) {
            baseGarment
        } else {
            findClosestInList(accessories, paletteColors.getOrNull(3) ?: paletteColors.getOrNull(2) ?: baseColor)
        }

        OutfitRecommendation(
            combination = combo,
            baseGarment = baseGarment,
            top = matchedTops,
            bottom = matchedBottoms,
            shoes = matchedShoes,
            accessory = matchedAccessories
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun findClosestInList(items: List<ClothingItem>, targetHex: String): ClothingItem? {
        if (items.isEmpty()) return null
        var closest: ClothingItem? = null
        var minDistance = Double.MAX_VALUE

        for (item in items) {
            val dist = ColorMatcher.calculateDeltaEFromHex(item.hexColor, targetHex)
            if (dist < minDistance) {
                minDistance = dist
                closest = item
            }
        }
        return closest
    }

    /**
     * Digitize an actual photo from CameraX file path
     */
    fun digitizePhoto(context: Context, photoFile: File, category: String, onSuccess: () -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Avoid OOM by decoding with sub-sampling (inSampleSize) in the background thread!
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(photoFile.absolutePath, options)

                var inSampleSize = 1
                val reqWidth = 1024
                val reqHeight = 1024
                if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                        inSampleSize *= 2
                    }
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                    inJustDecodeBounds = false
                }

                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath, decodeOptions)
                if (bitmap == null) {
                    try {
                        if (photoFile.exists()) {
                            photoFile.delete()
                        }
                    } catch (e: Exception) {}
                    return@launch
                }

                // Safely delete intermediate cache file as bitmap is now loaded and sub-sampled
                try {
                    if (photoFile.exists()) {
                        photoFile.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Resize if it's too big to prevent excessive memory and storage usage
                val maxDimension = 1024
                val processedBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                    val (newW, newH) = if (bitmap.width > bitmap.height) {
                        Pair(maxDimension, (maxDimension / ratio).toInt())
                    } else {
                        Pair((maxDimension * ratio).toInt(), maxDimension)
                    }
                    Bitmap.createScaledBitmap(bitmap, newW, newH, true)
                } else {
                    bitmap
                }

                // 1. Extract dominant color and auto-tag primary color group from saved imagery
                val dominantHex = extractDominantColorFromBitmap(processedBitmap)

                // 2. Save file to private storage
                val path = saveBitmapToInternalStorage(context, processedBitmap)

                // Recycle intermediate bitmap if created
                if (processedBitmap != bitmap) {
                    processedBitmap.recycle()
                }
                bitmap.recycle() // Recycle original bitmap immediately

                val (analyzedHex, colorGroup) = ColorGroupClassifier.analyzeSavedImagery(path, dominantHex)
                val cachedColors = _wadaColors.value.ifEmpty { repository.loadWadaColors() }
                val wadaLabel = WadaColorRegistry.buildSearchableWadaLabel(analyzedHex, cachedColors)

                // 3. Save to Room database
                val item = ClothingItem(
                    filePath = path,
                    category = category,
                    hexColor = analyzedHex,
                    colorGroup = colorGroup.label,
                    wadaColorName = wadaLabel
                )
                repository.insertClothing(item)
                _selectedGarment.value = item
                matchWadaAndRecommend(item)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    if (photoFile.exists()) {
                        photoFile.delete()
                    }
                } catch (ex: Exception) {}
            }
        }
    }

    /**
     * Digitize an already processed bitmap (after background removal or rotation)
     */
    fun digitizeProcessedBitmap(context: Context, bitmap: Bitmap, category: String, onSuccess: () -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // 1. Extract dominant color based on center-weighted averaging
                val dominantHex = extractDominantColorFromBitmap(bitmap)

                // 2. Save file to private storage (save as PNG to retain transparency if background is removed)
                val path = saveBitmapToInternalStorage(context, bitmap, isPng = true)
                val (analyzedHex, colorGroup) = ColorGroupClassifier.analyzeSavedImagery(path, dominantHex)
                val cachedColors = _wadaColors.value.ifEmpty { repository.loadWadaColors() }
                val wadaLabel = WadaColorRegistry.buildSearchableWadaLabel(analyzedHex, cachedColors)

                // 3. Save to Room database
                val item = ClothingItem(
                    filePath = path,
                    category = category,
                    hexColor = analyzedHex,
                    colorGroup = colorGroup.label,
                    wadaColorName = wadaLabel
                )
                repository.insertClothing(item)
                _selectedGarment.value = item
                matchWadaAndRecommend(item)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Create virtual items (for quick exploration & beautiful fallback in browser environment)
     */
    fun createVirtualGarment(context: Context, category: String, hexColor: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Generate a beautifully stylized garment placeholder bitmap
                val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val colorPaint = Paint().apply {
                    color = try {
                        val formattedHex = if (hexColor.startsWith("#")) hexColor else "#$hexColor"
                        android.graphics.Color.parseColor(formattedHex)
                    } catch (e: Exception) {
                        android.graphics.Color.GRAY
                    }
                    style = Paint.Style.FILL
                }
                // Draw background fabric
                canvas.drawRect(0f, 0f, 400f, 400f, colorPaint)

                // Draw illustrative lines representing garment type
                val borderPaint = Paint().apply {
                    color = if (ColorMatcher.calculateDeltaEFromHex(hexColor, "#FFFFFF") < 20) {
                        android.graphics.Color.LTGRAY
                    } else {
                        android.graphics.Color.WHITE
                    }
                    style = Paint.Style.STROKE
                    strokeWidth = 8f
                    alpha = 150
                }

                when (category) {
                    "Tops" -> {
                        // Draw a t-shirt contour outline
                        canvas.drawCircle(200f, 20f, 40f, borderPaint) // Collar
                        canvas.drawLine(100f, 60f, 300f, 60f, borderPaint) // Shoulders
                        canvas.drawLine(100f, 60f, 70f, 150f, borderPaint) // Left Sleeve
                        canvas.drawLine(300f, 60f, 330f, 150f, borderPaint) // Right Sleeve
                    }
                    "Bottoms" -> {
                        // Draw trousers layout lines
                        canvas.drawLine(150f, 50f, 250f, 50f, borderPaint) // Waistband
                        canvas.drawLine(200f, 140f, 200f, 350f, borderPaint) // Divider
                    }
                    "Shoes" -> {
                        // Draw shoe silhouette outline
                        canvas.drawLine(100f, 250f, 300f, 250f, borderPaint)
                        canvas.drawCircle(180f, 200f, 30f, borderPaint)
                    }
                    "Accessories" -> {
                        // Draw bag layout outline
                        canvas.drawRect(120f, 140f, 280f, 300f, borderPaint)
                    }
                }

                val path = saveBitmapToInternalStorage(context, bitmap)
                bitmap.recycle() // Recycle virtual placeholder bitmap immediately

                val cachedColors = _wadaColors.value.ifEmpty { repository.loadWadaColors() }
                val wadaLabel = WadaColorRegistry.buildSearchableWadaLabel(hexColor, cachedColors)

                val item = ClothingItem(
                    filePath = path,
                    category = category,
                    hexColor = hexColor,
                    colorGroup = ColorGroupClassifier.classifyHex(hexColor).label,
                    wadaColorName = wadaLabel
                )
                repository.insertClothing(item)
                _selectedGarment.value = item
                matchWadaAndRecommend(item)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Delete a clothing item
     */
    fun deleteGarment(id: Int) {
        viewModelScope.launch {
            if (_selectedGarment.value?.id == id) {
                _selectedGarment.value = null
            }
            repository.deleteClothing(id)
        }
    }

    /**
     * Set App Theme
     */
    fun setThemeSetting(theme: String) {
        _themeSetting.value = theme
        repository.setThemeSetting(theme)
    }

    /**
     * Set Monet Mode ("MONET" or "STANDARD")
     */
    fun setMonetMode(mode: String) {
        _monetMode.value = mode
        repository.setMonetMode(mode)
    }

    /**
     * Seed a starter wardrobe of 8 sample Wada garments
     */
    fun seedSampleWardrobe(context: Context) {
        val sampleGarments = listOf(
            "Tops" to "#1E2B37",        // Indigo Navy tee
            "Tops" to "#C96868",        // Dusty Rose sweater
            "Bottoms" to "#3C3633",     // Charcoal trousers
            "Bottoms" to "#E8D8C8",     // Ecru linen pants
            "Shoes" to "#7A5C58",       // Terracotta leather oxfords
            "Shoes" to "#D49B35",       // Amber boots
            "Accessories" to "#D94E34", // Vermilion scarf
            "Accessories" to "#627254"  // Bamboo green tote
        )
        sampleGarments.forEach { (cat, hex) ->
            createVirtualGarment(context, cat, hex)
        }
    }



    /**
     * Toggle Favorite status of a clothing item
     */
    fun toggleFavorite(item: ClothingItem) {
        viewModelScope.launch {
            repository.toggleFavorite(item.id, !item.isFavorite)
        }
    }

    /**
     * Persist an edited exact hex color and optional photo rotation (in degrees, multiples of 90)
     * for a closet garment. When rotated, saves a newly rotated bitmap file so Coil refreshes
     * immediately and intrinsic aspect ratio updates across all cards.
     */
    fun updateGarmentColorAndRotation(
        context: Context,
        item: ClothingItem,
        newHexColor: String,
        rotationDegrees: Int,
        thermalType: String = item.thermalType,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val normalizedHex = ColorUtils.toHexString(ColorUtils.parseSafeColor(newHexColor))
                val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
                val normalizedThermal = when (thermalType.trim().uppercase()) {
                    "WARM" -> "WARM"
                    "LIGHT" -> "LIGHT"
                    else -> ""
                }
                var finalPath = item.filePath

                if (normalizedRotation != 0 && item.filePath.isNotBlank()) {
                    val sourceFile = File(item.filePath)
                    if (sourceFile.exists() && sourceFile.length() > 0L) {
                        val decoded = BitmapFactory.decodeFile(sourceFile.absolutePath)
                        if (decoded != null) {
                            val matrix = Matrix().apply {
                                postRotate(normalizedRotation.toFloat())
                            }
                            val rotatedBitmap = Bitmap.createBitmap(
                                decoded,
                                0,
                                0,
                                decoded.width,
                                decoded.height,
                                matrix,
                                true
                            )
                            val isPng = sourceFile.extension.equals("png", ignoreCase = true)
                            val newPath = saveBitmapToInternalStorage(context, rotatedBitmap, isPng = isPng)
                            if (rotatedBitmap != decoded) {
                                rotatedBitmap.recycle()
                            }
                            decoded.recycle()

                            if (newPath != sourceFile.absolutePath && File(newPath).exists()) {
                                finalPath = newPath
                                try {
                                    sourceFile.delete()
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                }

                val updatedColorGroup = ColorGroupClassifier.classifyHex(normalizedHex).label
                val cachedColors = _wadaColors.value.ifEmpty { repository.loadWadaColors() }
                val updatedWadaLabel = WadaColorRegistry.buildSearchableWadaLabel(normalizedHex, cachedColors)
                repository.updateClothingColorAndPath(
                    id = item.id,
                    hexColor = normalizedHex,
                    filePath = finalPath,
                    colorGroup = updatedColorGroup,
                    thermalType = normalizedThermal,
                    wadaColorName = updatedWadaLabel
                )

                val updatedItem = item.copy(
                    hexColor = normalizedHex,
                    filePath = finalPath,
                    colorGroup = updatedColorGroup,
                    thermalType = normalizedThermal,
                    wadaColorName = updatedWadaLabel
                )
                if (_selectedGarment.value?.id == item.id) {
                    _selectedGarment.value = updatedItem
                    matchWadaAndRecommend(updatedItem)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    // --- Helper math/file functions ---

    private fun extractDominantColorFromBitmap(bitmap: Bitmap): String {
        val startX = (bitmap.width * 0.25).toInt()
        val startY = (bitmap.height * 0.25).toInt()
        val width = (bitmap.width * 0.5).toInt()
        val height = (bitmap.height * 0.5).toInt()

        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0

        val step = if (width * height > 15000) 12 else 1
        for (y in startY until (startY + height) step step) {
            for (x in startX until (startX + width) step step) {
                val pixel = bitmap.getPixel(x, y)
                rSum += (pixel shr 16) and 0xFF
                gSum += (pixel shr 8) and 0xFF
                bSum += pixel and 0xFF
                count++
            }
        }

        val avgR = if (count > 0) (rSum / count).toInt() else 128
        val avgG = if (count > 0) (gSum / count).toInt() else 128
        val avgB = if (count > 0) (bSum / count).toInt() else 128

        return String.format("#%02X%02X%02X", avgR, avgG, avgB)
    }

    private fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap, isPng: Boolean = false): String {
        val extension = if (isPng) "png" else "jpg"
        val uniqueSuffix = "${System.currentTimeMillis()}_${System.nanoTime() % 100000}"
        val filename = "garment_$uniqueSuffix.$extension"
        val file = File(context.filesDir, filename)
        FileOutputStream(file).use { out ->
            if (isPng) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } else {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
        }
        return file.absolutePath
    }
}

data class OutfitRecommendation(
    val combination: WadaCombination,
    val baseGarment: ClothingItem?,
    val top: ClothingItem?,
    val bottom: ClothingItem?,
    val shoes: ClothingItem?,
    val accessory: ClothingItem?
)

data class ClosetFilterState(
    val category: String = "All",
    val colorGroup: String = "All",
    val colorQuery: String = "",
    val styleTag: String = "All Styles"
)

data class DailyInspirationState(
    val combination: WadaCombination,
    val displayedColors: List<String>,
    val weatherMatches: List<WeatherGarmentMatch> = emptyList(),
    val weatherAdvice: String? = null
)

class WardrobeViewModelFactory(
    private val repository: ClothingRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WardrobeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WardrobeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
