package com.example.util

import com.example.data.WadaColor

/**
 * Traditional Sanzo Wada Color representation.
 * Bridges Sanzo Wada's 1930s dictionary names with Japan's historic traditional colors (Dento-iro),
 * providing kanji, romaji, English meaning, and color families.
 */
data class TraditionalWadaColor(
    val index: Int,
    val wadaName: String,
    val hex: String,
    val japaneseRomaji: String,
    val japaneseKanji: String,
    val englishMeaning: String,
    val colorFamily: String
) {
    val displayName: String
        get() = "$wadaName • $japaneseKanji ($japaneseRomaji)"

    val shortBadge: String
        get() = "$japaneseKanji $japaneseRomaji"
}

/**
 * Filter pill item for quick one-tap color filter selection in the Closet search bar.
 */
data class WadaColorFilterPill(
    val label: String,
    val query: String?,
    val swatchHex: String?
)

/**
 * Sanzo Wada Color Registry and Search Matcher.
 * Maps 1930s Wada catalog colors to traditional Japanese aesthetic names and provides
 * Delta-E perceptual color filtering for digital wardrobe curation.
 */
object WadaColorRegistry {

    // Classic curated quick-filter pills for the Closet search bar
    val POPULAR_FILTER_PILLS = listOf(
        WadaColorFilterPill("All Colors", null, null),
        WadaColorFilterPill("Kon • 紺", "Kon", "#0d2b52"),
        WadaColorFilterPill("Moegi • 萌黄", "Moegi", "#5c8a73"),
        WadaColorFilterPill("Yamabuki • 山吹", "Yamabuki", "#ffb852"),
        WadaColorFilterPill("Beni • 紅", "Beni", "#d60036"),
        WadaColorFilterPill("Sumi • 墨", "Sumi", "#241829"),
        WadaColorFilterPill("Sakura • 桜", "Sakura", "#ffb3f0"),
        WadaColorFilterPill("Asagi • 浅葱", "Asagi", "#417777"),
        WadaColorFilterPill("Sienna • 焦茶", "Sienna", "#b85e00"),
        WadaColorFilterPill("Fuji • 藤", "Fuji", "#805999"),
        WadaColorFilterPill("Akane • 茜", "Akane", "#c9303e"),
        WadaColorFilterPill("Rikyū • 利休", "Rikyu", "#4a5d4e")
    )

    /**
     * Resolves traditional Japanese details (Kanji, Romaji, translation, family)
     * for any Sanzo Wada color based on its dictionary name and hue.
     */
    fun resolveTraditionalDetails(color: WadaColor): TraditionalWadaColor {
        val name = color.name
        val hex = color.hex

        val (romaji, kanji, meaning, family) = when {
            // Indigo / Blue tones
            name.contains("Indigo", ignoreCase = true) || name.contains("Tyrian Blue", ignoreCase = true) ->
                Quadruple("Kon", "紺", "Deep Indigo Navy", "Blue")
            name.contains("Lyons Blue", ignoreCase = true) || name.contains("Antwarp Blue", ignoreCase = true) ->
                Quadruple("Ai", "藍", "Pure Indigo Blue", "Blue")
            name.contains("Cerulian", ignoreCase = true) || name.contains("Peacock Blue", ignoreCase = true) || name.contains("Olympic Blue", ignoreCase = true) ->
                Quadruple("Shinbashi", "新橋", "Geisha Bridge Cyan", "Blue")
            name.contains("Nile Blue", ignoreCase = true) || name.contains("King's Blue", ignoreCase = true) || name.contains("Glaucous Blue", ignoreCase = true) ->
                Quadruple("Mizu", "水色", "Clear Spring Water Blue", "Blue")
            name.contains("Blue", ignoreCase = true) ->
                Quadruple("Ruri", "瑠璃", "Lapis Lazuli Blue", "Blue")

            // Green / Olive / Sprout tones
            name.contains("Yellow Green", ignoreCase = true) || name.contains("Pistachio", ignoreCase = true) || name.contains("Rainette", ignoreCase = true) ->
                Quadruple("Moegi", "萌黄", "Sprouting Young Green", "Green")
            name.contains("Olive", ignoreCase = true) || name.contains("Artemesia", ignoreCase = true) || name.contains("Citrine", ignoreCase = true) ->
                Quadruple("Rikyū", "利休", "Tea Master Olive Green", "Green")
            name.contains("Andover Green", ignoreCase = true) || name.contains("Dull Viridian", ignoreCase = true) || name.contains("Oil Green", ignoreCase = true) ->
                Quadruple("Matsuba", "松葉", "Pine Needle Forest Green", "Green")
            name.contains("Turquoise Green", ignoreCase = true) || name.contains("Venice Green", ignoreCase = true) || name.contains("Calamine", ignoreCase = true) || name.contains("Medici", ignoreCase = true) ->
                Quadruple("Asagi", "浅葱", "Spring Scallion Aqua", "Green")
            name.contains("Green", ignoreCase = true) ->
                Quadruple("Midori", "緑", "Traditional Vibrant Green", "Green")

            // Yellow / Gold tones
            name.contains("Golden", ignoreCase = true) || name.contains("Cream Yellow", ignoreCase = true) || name.contains("Pyrite Yellow", ignoreCase = true) ->
                Quadruple("Yamabuki", "山吹", "Golden Japanese Kerria", "Yellow")
            name.contains("Lemon", ignoreCase = true) || name.contains("Sulpher", ignoreCase = true) ->
                Quadruple("Kihada", "黄蘗", "Amur Cork Bright Yellow", "Yellow")
            name.contains("Khaki", ignoreCase = true) || name.contains("Ocher", ignoreCase = true) || name.contains("Citron", ignoreCase = true) ->
                Quadruple("Karashi", "芥子", "Spicy Mustard Ochre", "Yellow")
            name.contains("Yellow", ignoreCase = true) ->
                Quadruple("Ki-iro", "黄色", "Traditional Imperial Yellow", "Yellow")

            // Red / Crimson / Scarlet tones
            name.contains("Carmine", ignoreCase = true) || name.contains("Scarlet", ignoreCase = true) || name.contains("Spectrum Red", ignoreCase = true) ->
                Quadruple("Beni", "紅", "Safflower Crimson", "Red")
            name.contains("Etruscan Red", ignoreCase = true) || name.contains("Jasper Red", ignoreCase = true) || name.contains("Ochre Red", ignoreCase = true) ->
                Quadruple("Akane", "茜", "Madder Root Vermilion", "Red")
            name.contains("Brick Red", ignoreCase = true) || name.contains("Pompeian Red", ignoreCase = true) || name.contains("English Red", ignoreCase = true) ->
                Quadruple("Benga", "弁柄", "Oxide Red Ocher", "Red")
            name.contains("Coral Red", ignoreCase = true) || name.contains("Peach Red", ignoreCase = true) || name.contains("Fresh Color", ignoreCase = true) ->
                Quadruple("Sango", "珊瑚", "Precious Living Coral", "Red")
            name.contains("Red", ignoreCase = true) ->
                Quadruple("Aka", "赤", "Classic Wada Red", "Red")

            // Pink / Rose tones
            name.contains("Hermosa Pink", ignoreCase = true) || name.contains("Corinthian Pink", ignoreCase = true) || name.contains("Cameo Pink", ignoreCase = true) ->
                Quadruple("Sakura", "桜", "Spring Cherry Blossom Pink", "Pink")
            name.contains("Grenadine Pink", ignoreCase = true) || name.contains("Eosine Pink", ignoreCase = true) || name.contains("Spinel Red", ignoreCase = true) ->
                Quadruple("Toki", "鴇", "Japanese Crested Ibis Pink", "Pink")
            name.contains("Old Rose", ignoreCase = true) || name.contains("Seashell", ignoreCase = true) ->
                Quadruple("Nadeshiko", "撫子", "Wild Diad Pink", "Pink")
            name.contains("Pink", ignoreCase = true) ->
                Quadruple("Momo", "桃色", "Ripe Peach Pink", "Pink")

            // Brown / Earth / Sienna tones
            name.contains("Sienna", ignoreCase = true) || name.contains("Burnt Sienna", ignoreCase = true) || name.contains("Rufous", ignoreCase = true) ->
                Quadruple("Koganecha", "焦茶", "Deep Earthy Roasted Tea", "Brown")
            name.contains("Cinnamon", ignoreCase = true) || name.contains("Fawn", ignoreCase = true) || name.contains("Drab", ignoreCase = true) ->
                Quadruple("Kuchiba", "朽葉", "Autumn Fallen Russet Leaf", "Brown")
            name.contains("Sepia", ignoreCase = true) || name.contains("Umber", ignoreCase = true) || name.contains("Tobacco", ignoreCase = true) || name.contains("Brown", ignoreCase = true) ->
                Quadruple("Kurumi", "胡桃", "Historic Walnut Brown", "Brown")

            // Purple / Violet / Lavender tones
            name.contains("Pansy", ignoreCase = true) || name.contains("Violet", ignoreCase = true) || name.contains("Plumbeous", ignoreCase = true) ->
                Quadruple("Murasaki", "紫", "Imperial Royal Purple", "Purple")
            name.contains("Lavender", ignoreCase = true) || name.contains("Lilac", ignoreCase = true) || name.contains("Mauve", ignoreCase = true) ->
                Quadruple("Fuji", "藤", "Wisteria Blossom Lavender", "Purple")
            name.contains("Eupatorium", ignoreCase = true) || name.contains("Rosolanc", ignoreCase = true) || name.contains("Pomegranite", ignoreCase = true) ->
                Quadruple("Botan", "牡丹", "Vibrant Peony Violet", "Purple")
            name.contains("Purple", ignoreCase = true) ->
                Quadruple("Shion", "紫苑", "Tatarian Aster Purple", "Purple")

            // Black / Dark / Neutral
            name.contains("Black", ignoreCase = true) || name.contains("Slate", ignoreCase = true) || name.contains("Umber", ignoreCase = true) ->
                Quadruple("Sumi", "墨", "Traditional Pine Soot Ink", "Black")
            name.contains("Ivory", ignoreCase = true) || name.contains("Ecru", ignoreCase = true) || name.contains("Buff", ignoreCase = true) ->
                Quadruple("Shironeri", "白練", "Pure Bleached White Silk", "White")

            else ->
                Quadruple("Wada", "和", "Sanzo Wada Harmonic Tone", "Neutral")
        }

        return TraditionalWadaColor(
            index = color.index,
            wadaName = name,
            hex = hex,
            japaneseRomaji = romaji,
            japaneseKanji = kanji,
            englishMeaning = meaning,
            colorFamily = family
        )
    }

    /**
     * Finds the closest Traditional Wada Color for any garment hex color string.
     */
    fun findClosestTraditionalColor(garmentHex: String, wadaColors: List<WadaColor>): TraditionalWadaColor {
        if (wadaColors.isEmpty()) {
            return TraditionalWadaColor(
                index = 1,
                wadaName = "Wada Neutral",
                hex = garmentHex,
                japaneseRomaji = "Sumi",
                japaneseKanji = "墨",
                englishMeaning = "Charcoal Tone",
                colorFamily = "Neutral"
            )
        }

        var closest = wadaColors.first()
        var minDistance = Double.MAX_VALUE

        for (wadaColor in wadaColors) {
            val dist = ColorMatcher.calculateDeltaEFromHex(garmentHex, wadaColor.hex)
            if (dist < minDistance) {
                minDistance = dist
                closest = wadaColor
            }
        }

        return resolveTraditionalDetails(closest)
    }

    /**
     * Determines whether a garment's color matches a user's Wada color search query.
     * Matches across:
     * - Wada dictionary name (e.g. "Sienna", "Tyrian", "Hermosa", "Carmine", "Andover")
     * - Traditional Japanese romaji name (e.g. "Kon", "Ai", "Moegi", "Yamabuki", "Sumi", "Sakura", "Beni", "Asagi", "Fuji")
     * - Traditional Japanese kanji (e.g. "紺", "藍", "萌黄", "山吹", "墨", "桜", "紅", "浅葱", "藤")
     * - English translated meaning (e.g. "Indigo", "Kerria", "Sprout", "Cherry", "Wisteria")
     * - Color family (e.g. "Blue", "Green", "Yellow", "Red", "Brown", "Purple", "Pink", "Black")
     * - Perceptual proximity (Delta-E < 26) to any specific Wada color matching the query
     */
    fun matchesWadaColorSearch(
        garmentHex: String,
        searchQuery: String,
        wadaColors: List<WadaColor>
    ): Boolean {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) return true

        val closest = findClosestTraditionalColor(garmentHex, wadaColors)

        // 1. Direct textual match on the garment's closest Wada color
        if (closest.wadaName.lowercase().contains(query) ||
            closest.japaneseRomaji.lowercase().contains(query) ||
            closest.japaneseKanji.contains(query) ||
            closest.englishMeaning.lowercase().contains(query) ||
            closest.colorFamily.lowercase().contains(query)
        ) {
            return true
        }

        // 2. Perceptual match: if the query targets a specific Wada color,
        // match garments that are perceptually close (Delta-E < 26) to that target Wada color
        val matchingTargetColors = wadaColors.filter { wada ->
            val trad = resolveTraditionalDetails(wada)
            trad.wadaName.lowercase().contains(query) ||
            trad.japaneseRomaji.lowercase().contains(query) ||
            trad.japaneseKanji.contains(query) ||
            trad.englishMeaning.lowercase().contains(query) ||
            trad.colorFamily.lowercase().contains(query)
        }

        for (target in matchingTargetColors) {
            val deltaE = ColorMatcher.calculateDeltaEFromHex(garmentHex, target.hex)
            if (deltaE < 26.0) {
                return true
            }
        }

        return false
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
