package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Broad primary color groups used to auto-tag wardrobe garments from their imagery
 * and enable fast one-tap Closet color-group filtering.
 */
enum class PrimaryColorGroup(val label: String, val swatchHex: String) {
    RED("Red", "#C9303E"),
    ORANGE("Orange", "#D96B27"),
    YELLOW("Yellow", "#E0B038"),
    GREEN("Green", "#4E6D53"),
    BLUE("Blue", "#284B70"),
    PURPLE("Purple", "#7B5EA7"),
    PINK("Pink", "#E38CA7"),
    BROWN("Brown", "#7A5238"),
    BLACK("Black", "#1E1B1A"),
    WHITE("White", "#F4F0E8"),
    GRAY("Gray", "#858585");

    companion object {
        fun fromLabel(label: String?): PrimaryColorGroup? {
            if (label.isNullOrBlank()) return null
            return entries.firstOrNull { it.label.equals(label.trim(), ignoreCase = true) }
        }
    }
}

/**
 * Classifies RGB/Hex colors into PrimaryColorGroup buckets and analyzes saved garment
 * imagery to extract and auto-tag a piece's dominant primary color.
 */
object ColorGroupClassifier {

    /**
     * Pure-Kotlin RGB-to-HSV conversion (H in 0..360, S in 0..1, V in 0..1).
     */
    fun rgbToHsv(r: Int, g: Int, b: Int): Triple<Float, Float, Float> {
        val rf = r.coerceIn(0, 255) / 255f
        val gf = g.coerceIn(0, 255) / 255f
        val bf = b.coerceIn(0, 255) / 255f

        val cMax = max(rf, max(gf, bf))
        val cMin = min(rf, min(gf, bf))
        val delta = cMax - cMin

        val hue = when {
            delta < 1e-5f -> 0f
            cMax == rf -> 60f * (((gf - bf) / delta) % 6f)
            cMax == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }

        val sat = if (cMax < 1e-5f) 0f else delta / cMax
        return Triple(hue, sat, cMax)
    }

    /**
     * Maps any hex color string into its canonical [PrimaryColorGroup].
     */
    fun classifyHex(hexColor: String): PrimaryColorGroup {
        val (r, g, b) = ColorMatcher.hexToRgb(hexColor)
        return classifyRgb(r, g, b)
    }

    /**
     * Maps RGB values (0..255) into a [PrimaryColorGroup].
     */
    fun classifyRgb(r: Int, g: Int, b: Int): PrimaryColorGroup {
        val (hue, sat, value) = rgbToHsv(r, g, b)

        // 1. Achromatic / Neutral tones first
        if (value <= 0.16f) return PrimaryColorGroup.BLACK
        if (sat <= 0.13f && value >= 0.82f) return PrimaryColorGroup.WHITE
        if (sat <= 0.14f) {
            return when {
                value < 0.23f -> PrimaryColorGroup.BLACK
                value > 0.80f -> PrimaryColorGroup.WHITE
                else -> PrimaryColorGroup.GRAY
            }
        }
        // Very dark muted tones read as Black in apparel unless distinctly warm brown
        if (value <= 0.24f && sat <= 0.35f) return PrimaryColorGroup.BLACK

        // 2. Warm earthy browns / tans / khaki / camel / chocolate
        val isEarthBrownHue = hue in 12f..46f
        if (isEarthBrownHue && (value < 0.66f || (sat < 0.45f && value < 0.80f))) {
            return PrimaryColorGroup.BROWN
        }
        if (hue in 0f..12f && value < 0.42f && sat < 0.62f) {
            return PrimaryColorGroup.BROWN
        }

        // 3. Soft ivory / cream / ecru off-whites
        if (sat <= 0.22f && value >= 0.88f) return PrimaryColorGroup.WHITE

        // 4. Chromatic hue sectors
        return when {
            hue < 14f || hue >= 344f -> {
                if (value >= 0.72f && sat <= 0.62f) PrimaryColorGroup.PINK
                else PrimaryColorGroup.RED
            }
            hue < 42f -> PrimaryColorGroup.ORANGE
            hue < 68f -> PrimaryColorGroup.YELLOW
            hue < 168f -> PrimaryColorGroup.GREEN
            hue < 252f -> PrimaryColorGroup.BLUE
            hue < 296f -> PrimaryColorGroup.PURPLE
            else -> {
                // 296..344: magenta / rose / plum
                if (value >= 0.56f || sat < 0.65f) PrimaryColorGroup.PINK
                else PrimaryColorGroup.PURPLE
            }
        }
    }

    /**
     * Analyzes a saved garment image file to determine its dominant primary color hex and
     * [PrimaryColorGroup]. Uses center-weighted color histogram clustering so folded fabrics,
     * studio shadows, or light neutral backgrounds do not distort chromatic garments.
     *
     * Falls back cleanly to [fallbackHex] if the file does not exist or cannot be decoded.
     */
    fun analyzeSavedImagery(filePath: String, fallbackHex: String): Pair<String, PrimaryColorGroup> {
        if (filePath.isBlank()) {
            val clean = fallbackHex.ifBlank { "#8E8880" }
            return clean to classifyHex(clean)
        }
        val file = File(filePath)
        if (!file.exists() || file.length() <= 0L) {
            val clean = fallbackHex.ifBlank { "#8E8880" }
            return clean to classifyHex(clean)
        }

        var bitmap: Bitmap? = null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return fallbackHex to classifyHex(fallbackHex)
            }

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > 160 || bounds.outHeight / sampleSize > 160) {
                sampleSize *= 2
            }

            bitmap = BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            ) ?: return fallbackHex to classifyHex(fallbackHex)

            val resultHex = extractDominantSubjectHex(bitmap) ?: fallbackHex
            resultHex to classifyHex(resultHex)
        } catch (_: Exception) {
            fallbackHex to classifyHex(fallbackHex)
        } finally {
            bitmap?.recycle()
        }
    }

    /**
     * Clusters pixels inside the central 70% bounding region into quantized 5-bit color buckets,
     * boosting center-weighted subject pixels and prioritizing chromatic garments over plain
     * studio backgrounds when significant chroma is present.
     */
    private fun extractDominantSubjectHex(bitmap: Bitmap): String? {
        val startX = (bitmap.width * 0.15f).toInt().coerceIn(0, bitmap.width - 1)
        val endX = (bitmap.width * 0.85f).toInt().coerceIn(startX + 1, bitmap.width)
        val startY = (bitmap.height * 0.15f).toInt().coerceIn(0, bitmap.height - 1)
        val endY = (bitmap.height * 0.85f).toInt().coerceIn(startY + 1, bitmap.height)

        data class Bucket(
            var weight: Double = 0.0,
            var rSum: Double = 0.0,
            var gSum: Double = 0.0,
            var bSum: Double = 0.0
        )

        val buckets = HashMap<Int, Bucket>()
        val centerX = bitmap.width / 2.0
        val centerY = bitmap.height / 2.0
        val maxDist = kotlin.math.hypot(bitmap.width * 0.35, bitmap.height * 0.35).coerceAtLeast(1.0)

        var totalOpaque = 0
        var chromaticPixels = 0

        val step = max(1, min((endX - startX) / 48, (endY - startY) / 48))

        for (y in startY until endY step step) {
            for (x in startX until endX step step) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 128) continue

                totalOpaque++
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val (_, sat, value) = rgbToHsv(r, g, b)
                if (sat >= 0.18f && value >= 0.15f) {
                    chromaticPixels++
                }

                // Distance-from-center Gaussian-like weight (center garments weigh more than corners)
                val distNorm = (kotlin.math.hypot(x - centerX, y - centerY) / maxDist).coerceIn(0.0, 1.0)
                val centerWeight = 1.4 - (0.7 * distNorm)
                val chromaBonus = if (sat >= 0.20f) 1.35 else 1.0
                val w = centerWeight * chromaBonus

                // Quantize into 5-bit per channel key (32x32x32 color cube)
                val key = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
                val bucket = buckets.getOrPut(key) { Bucket() }
                bucket.weight += w
                bucket.rSum += r * w
                bucket.gSum += g * w
                bucket.bSum += b * w
            }
        }

        if (totalOpaque == 0 || buckets.isEmpty()) return null

        // If at least 22% of sampled pixels are meaningfully chromatic, ignore near-white backdrop buckets
        val preferChromatic = chromaticPixels.toFloat() / totalOpaque.toFloat() >= 0.22f
        val bestBucket = buckets.values
            .filter { bucket ->
                if (!preferChromatic) true
                else {
                    val avgR = (bucket.rSum / bucket.weight).roundToInt()
                    val avgG = (bucket.gSum / bucket.weight).roundToInt()
                    val avgB = (bucket.bSum / bucket.weight).roundToInt()
                    val (_, s, v) = rgbToHsv(avgR, avgG, avgB)
                    !(s < 0.10f && v > 0.88f)
                }
            }
            .maxByOrNull { it.weight }
            ?: buckets.values.maxByOrNull { it.weight }
            ?: return null

        val finalR = (bestBucket.rSum / bestBucket.weight).roundToInt().coerceIn(0, 255)
        val finalG = (bestBucket.gSum / bestBucket.weight).roundToInt().coerceIn(0, 255)
        val finalB = (bestBucket.bSum / bestBucket.weight).roundToInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", finalR, finalG, finalB)
    }
}
