package com.example.util

import com.example.data.ClothingItem
import com.example.data.WadaColor

data class StyleTag(
    val name: String,
    val iconName: String,
    val description: String
)

object StyleTagHelper {

    val POPULAR_STYLE_TAGS = listOf(
        StyleTag("All Styles", "Dashboard", "Browse all garments across wardrobe"),
        StyleTag("Formal", "WorkspacePremium", "Sophisticated tailored pieces and structured silhouettes"),
        StyleTag("Casual", "Checkroom", "Relaxed everyday t-shirts, casual pants, and sneakers"),
        StyleTag("Monochromatic", "Contrast", "Clean single-hue, charcoal, slate, and ink neutral palettes"),
        StyleTag("Minimalist", "AutoAwesome", "Understated tones and unembellished archival essentials"),
        StyleTag("Traditional Japanese", "Brush", "Garments pairing with classic Sanzo Wada 1930s Dento-iro archetypes"),
        StyleTag("Workwear", "Construction", "Durable denim, chore jackets, and utilitarian earth tones"),
        StyleTag("Evening", "DarkMode", "Deep jewel tones, midnight indigos, and luxurious dark accents")
    )

    /**
     * Determines whether a clothing item qualifies for a selected style tag.
     */
    fun matchesStyleTag(
        item: ClothingItem,
        selectedTag: String,
        wadaColors: List<WadaColor>
    ): Boolean {
        if (selectedTag.isBlank() || selectedTag.equals("All Styles", ignoreCase = true)) {
            return true
        }

        val category = item.category.lowercase()
        val closestColor = WadaColorRegistry.findClosestTraditionalColor(item.hexColor, wadaColors)
        val family = closestColor.colorFamily.lowercase()
        val romaji = closestColor.japaneseRomaji.lowercase()

        return when (selectedTag.lowercase()) {
            "formal" -> {
                category.contains("top") || category.contains("shoe") || category.contains("accessory") ||
                family in listOf("black", "blue", "brown", "white")
            }
            "casual" -> {
                category.contains("top") || category.contains("bottom") ||
                family in listOf("blue", "green", "pink", "yellow", "brown")
            }
            "monochromatic" -> {
                // Sumi, black, white, charcoal, slate, or very dark/neutral tones
                family in listOf("black", "white", "neutral") ||
                romaji in listOf("sumi", "shironeri") ||
                ColorMatcher.calculateDeltaEFromHex(item.hexColor, "#000000") < 35.0 ||
                ColorMatcher.calculateDeltaEFromHex(item.hexColor, "#FFFFFF") < 25.0 ||
                ColorMatcher.calculateDeltaEFromHex(item.hexColor, "#555555") < 30.0
            }
            "minimalist" -> {
                family in listOf("black", "white", "brown", "green", "neutral") ||
                romaji in listOf("sumi", "rikyu", "shironeri", "kuchiba")
            }
            "traditional japanese" -> {
                romaji in listOf("kon", "ai", "moegi", "yamabuki", "beni", "sakura", "asagi", "fuji", "sumi", "akane", "rikyu")
            }
            "workwear" -> {
                category.contains("bottom") || category.contains("top") ||
                family in listOf("blue", "brown", "green") ||
                romaji in listOf("kon", "sienna", "rikyu")
            }
            "evening" -> {
                family in listOf("black", "purple", "blue", "red") ||
                romaji in listOf("sumi", "kon", "beni", "fuji")
            }
            else -> true
        }
    }
}
