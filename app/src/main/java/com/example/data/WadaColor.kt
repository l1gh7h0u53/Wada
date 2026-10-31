package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WadaColor(
    val index: Int,
    val name: String,
    val hex: String,
    val combinations: List<Int>
)
