package com.example.util

/**
 * Data model for the historical and cultural lore of a Sanzo Wada color.
 */
data class ColorLoreInfo(
    val title: String,
    val kanji: String,
    val romaji: String,
    val englishMeaning: String,
    val era: String, // e.g. "Heian Court & 1930s Modernism", "Edo Period (1603–1867)"
    val history: String,
    val wadaStylingNote: String,
    val swatchHex: String
)

/**
 * Repository of historical significance, cultural lore, and Sanzo Wada 1930s dictionary insights.
 */
object WadaColorLore {

    private val LORE_MAP = mapOf(
        "kon" to ColorLoreInfo(
            title = "Kon",
            kanji = "紺",
            romaji = "Kon",
            englishMeaning = "Deep Indigo Navy",
            era = "Edo Period & 1930s Modernism",
            history = "In Edo Japan, indigo was the prized color of everyday resilience and artisan craft. Celebrated internationally as 'Japan Blue', indigo was believed to repel insects and protect cotton fabrics. Sanzo Wada positioned Kon as the quintessential grounding anchor in his 1930s dictionary for high-contrast three-color palettes.",
            wadaStylingNote = "Harmonizes magnificently with Yamabuki (Golden Kerria) and Shironeri (Silk White) for an authoritative, timeless silhouette.",
            swatchHex = "#0d2b52"
        ),
        "ai" to ColorLoreInfo(
            title = "Ai",
            kanji = "藍",
            romaji = "Ai",
            englishMeaning = "Pure Indigo Blue",
            era = "Heian Period to Showa Era",
            history = "Dating back to ancient Japanese court robes, natural indigo dyeing requires weeks of fermenting Polygonum leaves in clay vats. Wada celebrated its pure optical depth, using it across over 40 distinct combinations in his 1930s color dictionary.",
            wadaStylingNote = "Pair with warm persimmon or terracotta tones to evoke classical Japanese lacquerware and woodblock prints.",
            swatchHex = "#1a3d6d"
        ),
        "moegi" to ColorLoreInfo(
            title = "Moegi",
            kanji = "萌黄",
            romaji = "Moegi",
            englishMeaning = "Sprouting Spring Green",
            era = "Heian Court (794–1185)",
            history = "Named for the fresh, tender shoots of young plants breaking through early spring earth. In medieval Japan, it was worn by youthful warriors and aristocrats as an emblem of vitality, renewal, and auspicious growth.",
            wadaStylingNote = "Wada paired fresh sprout greens with burnt sienna and deep plums to create rich seasonal transitions.",
            swatchHex = "#5c8a73"
        ),
        "yamabuki" to ColorLoreInfo(
            title = "Yamabuki",
            kanji = "山吹",
            romaji = "Yamabuki",
            englishMeaning = "Golden Japanese Kerria",
            era = "Manyoshu Era (8th Century)",
            history = "Celebrated in ancient poetry for the radiant yellow flowers that dance along mountain streams. During the Edo period, 'Yamabuki color' was affectionate slang for pure gold coins (*koban*). Wada utilized its golden luminescence to introduce warmth and joyful focus to darker ensembles.",
            wadaStylingNote = "Functions best as an expressive focal piece against charcoal or midnight navy bases.",
            swatchHex = "#ffb852"
        ),
        "beni" to ColorLoreInfo(
            title = "Beni",
            kanji = "紅",
            romaji = "Beni",
            englishMeaning = "Safflower Crimson",
            era = "Nara & Heian Periods",
            history = "Extracted from delicate safflower petals (*benibana*), traditional Beni was so precious that a small tub of dye was valued equal to a merchant house. Reserved for imperial royalty and celebratory silks, Wada celebrated its electrifying vitality in modern wardrobe compositions.",
            wadaStylingNote = "Use as a high-impact statement layer alongside sumi black or camel browns.",
            swatchHex = "#d60036"
        ),
        "sumi" to ColorLoreInfo(
            title = "Sumi",
            kanji = "墨",
            romaji = "Sumi",
            englishMeaning = "Pine Soot Ink Black",
            era = "Classical Calligraphy & Suibokuga",
            history = "Crafted from compressed pine soot, natural resins, and fragrance, Sumi is the soul of Japanese brush calligraphy and monochrome wash painting. Far from being flat black, Sumi possesses subtle warm or cool undertones that breathe with the surrounding canvas.",
            wadaStylingNote = "An architectural neutral that provides negative space (*ma*), allowing vibrant accent tones to sing.",
            swatchHex = "#241829"
        ),
        "sakura" to ColorLoreInfo(
            title = "Sakura",
            kanji = "桜",
            romaji = "Sakura",
            englishMeaning = "Cherry Blossom Pink",
            era = "Traditional Hanami Culture",
            history = "Embodying *mono no aware*—the poignant appreciation of impermanence and natural beauty. This ethereal blush was crafted from mountain cherry bark and madder, capturing the brief window when cherry blossoms carpet ancient temple gardens.",
            wadaStylingNote = "Pairs exquisitely with sage greens and soft silvers to evoke spring morning light.",
            swatchHex = "#ffb3f0"
        ),
        "asagi" to ColorLoreInfo(
            title = "Asagi",
            kanji = "浅葱",
            romaji = "Asagi",
            englishMeaning = "Spring Scallion Aqua",
            era = "Bakumatsu & Edo Periods",
            history = "Evoking the translucent blue-green of crisp spring leeks. During the 19th-century Bakumatsu period, Asagi became famous as the ceremonial coat color of the legendary Shinsengumi samurai force. Wada celebrated its clean, architectural clarity.",
            wadaStylingNote = "Injects fresh, cool energy when juxtaposed with warm ochres and linen textures.",
            swatchHex = "#417777"
        ),
        "sienna" to ColorLoreInfo(
            title = "Koganecha",
            kanji = "焦茶",
            romaji = "Koganecha",
            englishMeaning = "Raw & Burnt Sienna",
            era = "Edo Sumptuary Laws (Edo-cha)",
            history = "When Edo shogunate decrees banned commoners from wearing ostentatious purples and reds, urban artisans rebelled through subtle sophistication, inventing forty-eight shades of tea brown (*Shiju-hachi-cha*). Earthy browns became the ultimate mark of understated chic (*iki*).",
            wadaStylingNote = "Grounds tailored outfits with rustic warmth, pairing naturally with cream and indigo denim.",
            swatchHex = "#b85e00"
        ),
        "fuji" to ColorLoreInfo(
            title = "Fuji",
            kanji = "藤",
            romaji = "Fuji",
            englishMeaning = "Wisteria Blossom Violet",
            era = "Fujiwara Clan & Tale of Genji",
            history = "Hanging in dramatic cascades during May, wisteria has symbolized grace, refined lineage, and poetic romance since the 9th century. Lady Murasaki frequently referenced Fuji-colored layering in *The Tale of Genji*.",
            wadaStylingNote = "Offers a poetic, tranquil accent when paired with misty greys and forest pine greens.",
            swatchHex = "#805999"
        ),
        "akane" to ColorLoreInfo(
            title = "Akane",
            kanji = "茜",
            romaji = "Akane",
            englishMeaning = "Madder Root Twilight Red",
            era = "Asuka & Nara Periods (6th Century)",
            history = "Extracted from the wild madder plant, Akane is one of the most ancient dyes in Japanese archaeology. It captures the exact amber-red glow of sunset over Mount Yoshino described in the ancient *Manyoshu* anthology.",
            wadaStylingNote = "Ideal for statement knitwear or footwear, anchoring earthy autumn layer combinations.",
            swatchHex = "#c9303e"
        ),
        "rikyu" to ColorLoreInfo(
            title = "Rikyū",
            kanji = "利休",
            romaji = "Rikyū",
            englishMeaning = "Tea Master Muted Olive",
            era = "Azuchi-Momoyama Period (16th Century)",
            history = "Named after Sen no Rikyū, the legendary founder of the Japanese Way of Tea (*Chado*). This restrained mossy green embodies *wabi-sabi*—the profound aesthetic of rustic simplicity, quietude, and harmony with nature.",
            wadaStylingNote = "The quintessential everyday versatile neutral for trousers, chore jackets, and trench coats.",
            swatchHex = "#4a5d4e"
        )
    )

    /**
     * Resolves the most relevant color lore for a given search query or traditional color name.
     */
    fun getLoreForQuery(query: String): ColorLoreInfo {
        val q = query.trim().lowercase()

        // Exact match in keys
        for ((key, lore) in LORE_MAP) {
            if (q.contains(key) || key.contains(q)) {
                return lore
            }
        }

        // Match against English terms or family
        return when {
            q.contains("blue") || q.contains("indigo") || q.contains("navy") || q.contains("tyrian") ->
                LORE_MAP["kon"]!!
            q.contains("green") || q.contains("olive") || q.contains("sprout") || q.contains("andover") ->
                LORE_MAP["moegi"]!!
            q.contains("yellow") || q.contains("gold") || q.contains("kerria") || q.contains("cream") ->
                LORE_MAP["yamabuki"]!!
            q.contains("red") || q.contains("crimson") || q.contains("carmine") || q.contains("scarlet") ->
                LORE_MAP["beni"]!!
            q.contains("pink") || q.contains("rose") || q.contains("coral") || q.contains("hermosa") ->
                LORE_MAP["sakura"]!!
            q.contains("black") || q.contains("charcoal") || q.contains("dark") || q.contains("ink") ->
                LORE_MAP["sumi"]!!
            q.contains("cyan") || q.contains("turquoise") || q.contains("aqua") || q.contains("medici") ->
                LORE_MAP["asagi"]!!
            q.contains("brown") || q.contains("sienna") || q.contains("earth") || q.contains("tan") || q.contains("sepia") ->
                LORE_MAP["sienna"]!!
            q.contains("purple") || q.contains("violet") || q.contains("lavender") || q.contains("lilac") ->
                LORE_MAP["fuji"]!!
            else ->
                ColorLoreInfo(
                    title = "Sanzo Wada Color Philosophy",
                    kanji = "配色総鑑",
                    romaji = "Haishoku Sōkan",
                    englishMeaning = "A Dictionary of Color Combinations (1930s)",
                    era = "1930s Showa Modernism & Avant-Garde",
                    history = "Sanzo Wada (1883–1967), an Academy Award-winning designer and artist, revolutionized color theory by combining traditional Japanese aesthetic sensibilities (*Dento-iro*) with Western color science. His 346 palette harmonies explored bold two-, three-, and four-color combinations that transformed fashion, kimono, and industrial design.",
                    wadaStylingNote = "Wada believed that colors do not exist in isolation—harmony arises from the dynamic tension between saturation, area ratio, and seasonal context.",
                    swatchHex = "#8D5B4C"
                )
        }
    }
}
