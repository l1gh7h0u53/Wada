package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WadaCombination(
    val id: Int,
    val name: String,
    val colors: List<String>, // List of hex colors, e.g. ["#1D2A44", "#E5BFB1", "#BA7A5E"]
    val part: Int,
    val description: String
)
