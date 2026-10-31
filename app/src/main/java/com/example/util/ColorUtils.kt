package com.example.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ColorUtils provides robust color science conversions, safe string parsing,
 * contrast calculation, and Sanzo Wada harmony scoring.
 *
 * All color parsing handles multiple formats (#RGB, #RRGGBB, #AARRGGBB, or raw hex)
 * safely without crashing the application.
 */
object ColorUtils {

    /**
     * Safely parse a hex color string into a Jetpack Compose Color.
     * Falls back to [fallback] if parsing encounters malformed input.
     *
     * @param hex The color string, with or without leading '#' (e.g., "#D94E34", "1E2C3D", "#FFF")
     * @param fallback The fallback color to return on error (defaults to a neutral warm gray)
     */
    fun parseSafeColor(hex: String?, fallback: Color = Color(0xFF8E8880)): Color {
        if (hex.isNullOrBlank()) return fallback
        val clean = hex.trim().removePrefix("#")
        return try {
            when (clean.length) {
                3 -> {
                    // Shorthand #RGB -> #RRGGBB
                    val r = clean[0].toString().repeat(2).toInt(16)
                    val g = clean[1].toString().repeat(2).toInt(16)
                    val b = clean[2].toString().repeat(2).toInt(16)
                    Color(r, g, b)
                }
                6 -> {
                    // Standard #RRGGBB
                    val colorInt = clean.toLong(16).toInt() or 0xFF000000.toInt()
                    Color(colorInt)
                }
                8 -> {
                    // #AARRGGBB
                    Color(clean.toLong(16).toInt())
                }
                else -> fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }

    /**
     * Converts a Compose Color to an uppercase 6-character hex string (e.g., "#1E2C3D").
     */
    fun toHexString(color: Color): String {
        val r = (color.red * 255f).roundToInt().coerceIn(0, 255)
        val g = (color.green * 255f).roundToInt().coerceIn(0, 255)
        val b = (color.blue * 255f).roundToInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", r, g, b)
    }

    /**
     * Returns true if the color is perceptually light based on W3C relative luminance.
     */
    fun isLightColor(color: Color): Boolean {
        return color.luminance() > 0.55f
    }

    /**
     * Returns a high-contrast text/icon color (deep charcoal or pure white)
     * suitable for rendering over the provided [background] color.
     */
    fun getContrastingTextColor(background: Color): Color {
        return if (isLightColor(background)) {
            Color(0xFF191614) // Deep Sumi charcoal
        } else {
            Color(0xFFFBF8F2) // Soft Washi white
        }
    }

    /**
     * Calculates an Outfit Harmony Score (0–100%) and descriptive qualitative grade
     * based on the perceptual CIELAB Delta-E distance between clothing pieces and
     * Sanzo Wada's target combination colors.
     *
     * Lower Delta-E values indicate closer fidelity to Sanzo Wada's historical color balance.
     */
    data class HarmonyAssessment(
        val scorePercent: Int,
        val grade: String,
        val description: String,
        val badgeColor: Color
    )

    fun assessOutfitHarmony(distances: List<Double>): HarmonyAssessment {
        if (distances.isEmpty()) {
            return HarmonyAssessment(
                scorePercent = 88,
                grade = "Curated",
                description = "Sanzo Wada Color Foundation",
                badgeColor = Color(0xFF627254)
            )
        }

        // Average perceptual distance
        val avgDist = distances.average()

        // Delta-E mapping to 0-100%:
        // Delta-E < 12 is near-exact perceptual match (~95-100%)
        // Delta-E 12-25 is harmonious adjacent match (~80-94%)
        // Delta-E 25-45 is complementary contrast (~65-79%)
        // Delta-E > 45 is eclectic contrast (<65%)
        val score = (100.0 - (avgDist * 1.3)).roundToInt().coerceIn(40, 100)

        return when {
            score >= 90 -> HarmonyAssessment(
                scorePercent = score,
                grade = "Master Harmony",
                description = "Exceptional fidelity to Wada's 1930s dictionary pairing",
                badgeColor = Color(0xFF2E6F40)
            )
            score >= 78 -> HarmonyAssessment(
                scorePercent = score,
                grade = "Harmonious",
                description = "Natural tonal balance across your wardrobe pieces",
                badgeColor = Color(0xFF3B6E8C)
            )
            score >= 65 -> HarmonyAssessment(
                scorePercent = score,
                grade = "Balanced Contrast",
                description = "Dynamic complementary pairing with strong visual character",
                badgeColor = Color(0xFFD49B35)
            )
            else -> HarmonyAssessment(
                scorePercent = score,
                grade = "Eclectic Accent",
                description = "Expressive high-contrast combination with distinctive accents",
                badgeColor = Color(0xFFD94E34)
            )
        }
    }
}
