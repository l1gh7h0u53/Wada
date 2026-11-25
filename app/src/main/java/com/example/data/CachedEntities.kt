package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SQLite Room cache of the 143 Sanzo Wada dictionary colors so traditional color
 * lookups and perceptual filters run offline without re-parsing JSON assets.
 */
@Entity(tableName = "cached_wada_colors")
data class CachedWadaColor(
    @PrimaryKey val colorIndex: Int,
    val name: String,
    val hex: String,
    val combinationsCsv: String,
    val colorGroup: String
)

/**
 * Single-row SQLite Room cache for the last fetched local weather snapshot so
 * Daily Inspiration weather-fit picks remain available offline.
 */
@Entity(tableName = "cached_weather")
data class CachedWeather(
    @PrimaryKey val id: Int = 1,
    val temperatureCelsius: Double,
    val conditionName: String,
    val windSpeedKmh: Double,
    val fetchedAtMillis: Long
)

/**
 * SQLite projection for pre-aggregated garment counts per primary color group.
 */
data class ColorGroupCount(
    val colorGroup: String,
    val itemCount: Int
)
