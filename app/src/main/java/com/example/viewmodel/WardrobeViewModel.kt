package com.example.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ClothingItem
import com.example.data.ClothingRepository
import com.example.data.WadaColor
import com.example.data.WadaCombination
import com.example.util.ColorMatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
        // Load Wada colors and combinations from assets on IO thread to prevent ANR
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val colors = repository.loadWadaColors()
            _wadaColors.value = colors
            val list = repository.loadWadaCombinations()
            _wadaCombinations.value = list
            if (list.isNotEmpty()) {
                // Default to first combination to start the dynamic Monet theming
                val first = list.first()
                _selectedCombination.value = first
                recordPaletteExploration(first)
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

                // 1. Extract dominant color based on center-weighted averaging
                val dominantHex = extractDominantColorFromBitmap(processedBitmap)

                // 2. Save file to private storage
                val path = saveBitmapToInternalStorage(context, processedBitmap)

                // Recycle intermediate bitmap if created
                if (processedBitmap != bitmap) {
                    processedBitmap.recycle()
                }
                bitmap.recycle() // Recycle original bitmap immediately

                // 3. Save to Room database
                val item = ClothingItem(
                    filePath = path,
                    category = category,
                    hexColor = dominantHex
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

                // 3. Save to Room database
                val item = ClothingItem(
                    filePath = path,
                    category = category,
                    hexColor = dominantHex
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

                val item = ClothingItem(
                    filePath = path,
                    category = category,
                    hexColor = hexColor
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
        val filename = "garment_${System.currentTimeMillis()}.$extension"
        val file = File(context.filesDir, filename)
        FileOutputStream(file).use { out ->
            if (isPng) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } else {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
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
