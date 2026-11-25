package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalCacheDao {
    @Query("SELECT * FROM cached_wada_colors ORDER BY colorIndex ASC")
    fun observeCachedWadaColors(): Flow<List<CachedWadaColor>>

    @Query("SELECT * FROM cached_wada_colors ORDER BY colorIndex ASC")
    suspend fun getCachedWadaColorsOnce(): List<CachedWadaColor>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWadaColors(colors: List<CachedWadaColor>)

    @Query("SELECT * FROM cached_weather WHERE id = 1")
    fun observeCachedWeather(): Flow<CachedWeather?>

    @Query("SELECT * FROM cached_weather WHERE id = 1")
    suspend fun getCachedWeatherOnce(): CachedWeather?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedWeather(weather: CachedWeather)

    @Query("DELETE FROM cached_weather")
    suspend fun clearCachedWeather()
}
