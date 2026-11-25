package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClothingDao {
    @Query("SELECT * FROM clothing_items ORDER BY dateAdded DESC")
    fun getAllClothing(): Flow<List<ClothingItem>>

    @Query("SELECT * FROM clothing_items WHERE category = :category ORDER BY dateAdded DESC")
    fun getClothingByCategory(category: String): Flow<List<ClothingItem>>

    @Query("SELECT * FROM clothing_items WHERE LOWER(colorGroup) = LOWER(:colorGroup) ORDER BY dateAdded DESC")
    fun getClothingByColorGroup(colorGroup: String): Flow<List<ClothingItem>>

    /**
     * Indexed SQLite Room query that supports offline wardrobe viewing and filtering
     * by category/favorites, primary color group, and hex/color-name query.
     */
    @Query(
        """
        SELECT * FROM clothing_items
        WHERE (:category = 'All' OR (:category = 'Favorites' AND isFavorite = 1) OR LOWER(category) = LOWER(:category))
          AND (:colorGroup = 'All' OR LOWER(colorGroup) = LOWER(:colorGroup))
          AND (
                :query = ''
                OR LOWER(hexColor) LIKE '%' || LOWER(:query) || '%'
                OR LOWER(colorGroup) LIKE '%' || LOWER(:query) || '%'
                OR LOWER(wadaColorName) LIKE '%' || LOWER(:query) || '%'
              )
        ORDER BY dateAdded DESC
        """
    )
    fun filterClothingOffline(category: String, colorGroup: String, query: String): Flow<List<ClothingItem>>

    @Query("SELECT colorGroup, COUNT(*) AS itemCount FROM clothing_items WHERE colorGroup != '' GROUP BY colorGroup")
    fun getColorGroupCounts(): Flow<List<ColorGroupCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClothing(item: ClothingItem)

    @Query("DELETE FROM clothing_items WHERE id = :id")
    suspend fun deleteClothingById(id: Int)

    @Query("UPDATE clothing_items SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Int, isFavorite: Boolean)

    @Query(
        "UPDATE clothing_items SET hexColor = :hexColor, filePath = :filePath, colorGroup = :colorGroup, thermalType = :thermalType, wadaColorName = :wadaColorName WHERE id = :id"
    )
    suspend fun updateColorAndFilePath(
        id: Int,
        hexColor: String,
        filePath: String,
        colorGroup: String,
        thermalType: String,
        wadaColorName: String
    )

    @Query("UPDATE clothing_items SET colorGroup = :colorGroup, wadaColorName = :wadaColorName WHERE id = :id")
    suspend fun updateColorMetadata(id: Int, colorGroup: String, wadaColorName: String)
}
