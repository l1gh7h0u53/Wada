package com.example.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException

class ClothingRepository(
    private val clothingDao: ClothingDao,
    private val context: Context
) {
    val allClothing: Flow<List<ClothingItem>> = clothingDao.getAllClothing()

    fun getClothingByCategory(category: String): Flow<List<ClothingItem>> {
        return clothingDao.getClothingByCategory(category)
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



    private var cachedColors: List<WadaColor>? = null

    /**
     * Parse and load all 143 Sanzo Wada dictionary colors from assets.
     */
    suspend fun loadWadaColors(): List<WadaColor> = withContext(Dispatchers.IO) {
        cachedColors?.let { return@withContext it }
        try {
            val jsonString = context.assets.open("wada_colors.json").bufferedReader().use { it.readText() }
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
            val listType = Types.newParameterizedType(List::class.java, WadaColor::class.java)
            val adapter = moshi.adapter<List<WadaColor>>(listType)
            val colors = adapter.fromJson(jsonString) ?: emptyList()
            cachedColors = colors
            colors
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Parse and load pre-packaged Sanzo Wada combinations from local Assets folder.
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
            // Fallback to static list if error
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
