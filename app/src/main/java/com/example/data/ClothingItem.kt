package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clothing_items")
data class ClothingItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,
    val category: String, // e.g. "Tops", "Bottoms", "Shoes", "Accessories"
    val hexColor: String, // Normalized hex color (e.g. "#4E6D53")
    val dateAdded: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)
