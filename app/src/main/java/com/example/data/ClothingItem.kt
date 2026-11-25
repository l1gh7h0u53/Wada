package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "clothing_items",
    indices = [
        Index(value = ["colorGroup"]),
        Index(value = ["category"])
    ]
)
data class ClothingItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,
    val category: String, // e.g. "Tops", "Bottoms", "Shoes", "Accessories"
    val hexColor: String, // Normalized hex color (e.g. "#4E6D53")
    val dateAdded: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val colorGroup: String = "", // Auto-tagged primary color group (e.g. "Blue", "Green", "Black")
    val thermalType: String = "", // Weather thermal fit: "WARM", "LIGHT", or "" (auto)
    val wadaColorName: String = "" // Cached traditional Sanzo Wada color search text for offline SQLite filtering
)
