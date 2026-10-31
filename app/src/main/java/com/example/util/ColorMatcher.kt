package com.example.util

import kotlin.math.pow
import kotlin.math.sqrt

object ColorMatcher {

    /**
     * Converts a hex color string (e.g. "#1D2A44" or "1D2A44") into RGB.
     */
    fun hexToRgb(hexColor: String): Triple<Int, Int, Int> {
        val cleanHex = hexColor.replace("#", "").trim()
        val colorInt = cleanHex.toLongOrNull(16)?.toInt() ?: 0
        val r = (colorInt shr 16) and 0xFF
        val g = (colorInt shr 8) and 0xFF
        val b = colorInt and 0xFF
        return Triple(r, g, b)
    }

    /**
     * Converts sRGB values (0-255) to CIELAB (L*, a*, b*) color space.
     */
    fun rgbToLab(r: Int, g: Int, b: Int): Lab {
        // 1. Convert RGB to normalized values (0..1)
        var varR = r / 255.0
        var varG = g / 255.0
        var varB = b / 255.0

        // 2. Linearize RGB (pivot values)
        varR = if (varR > 0.04045) ((varR + 0.055) / 1.055).pow(2.4) else varR / 12.92
        varG = if (varG > 0.04045) ((varG + 0.055) / 1.055).pow(2.4) else varG / 12.92
        varB = if (varB > 0.04045) ((varB + 0.055) / 1.055).pow(2.4) else varB / 12.92

        // Scale to percentage
        varR *= 100.0
        varG *= 100.0
        varB *= 100.0

        // 3. Convert list-linear RGB to XYZ using the standard D65 illuminant matrix
        val x = varR * 0.4124 + varG * 0.3576 + varB * 0.1805
        val y = varR * 0.2126 + varG * 0.7152 + varB * 0.0722
        val z = varR * 0.0193 + varG * 0.1192 + varB * 0.9505

        // 4. Convert XYZ to CIELAB using D65 reference white points
        // Reference white constants for 2° Standard Observer (D65)
        val refX = 95.047
        val refY = 100.000
        val refZ = 108.883

        var varX = x / refX
        var varY = y / refY
        var varZ = z / refZ

        fun pivot(v: Double): Double {
            return if (v > 0.008856) {
                v.pow(1.0 / 3.0)
            } else {
                (7.787 * v) + (16.0 / 116.0)
            }
        }

        varX = pivot(varX)
        varY = pivot(varY)
        varZ = pivot(varZ)

        val l = (116.0 * varY) - 16.0
        val a = 500.0 * (varX - varY)
        val bColor = 200.0 * (varY - varZ)

        return Lab(l, a, bColor)
    }

    /**
     * Calculates CIE76 Delta E color distance between two LAB colors.
     * Values < 2.3 represent a difference virtually imperceptible to the human eye.
     */
    fun calculateDeltaE(color1: Lab, color2: Lab): Double {
        return sqrt(
            (color1.l - color2.l).pow(2.0) +
            (color1.a - color2.a).pow(2.0) +
            (color1.b - color2.b).pow(2.0)
        )
    }

    /**
     * Convenient distance calculator starting from Hex color strings directly.
     */
    fun calculateDeltaEFromHex(hex1: String, hex2: String): Double {
        val rgb1 = hexToRgb(hex1)
        val rgb2 = hexToRgb(hex2)
        val lab1 = rgbToLab(rgb1.first, rgb1.second, rgb1.third)
        val lab2 = rgbToLab(rgb2.first, rgb2.second, rgb2.third)
        return calculateDeltaE(lab1, lab2)
    }

    /**
     * Finds the index of the closest Wada color in a list for a given garment color hex.
     */
    fun findClosestColorIndex(garmentHex: String, wadaColors: List<String>): Int {
        if (wadaColors.isEmpty()) return -1
        var minDistance = Double.MAX_VALUE
        var closestIndex = 0

        wadaColors.forEachIndexed { index, wadaHex ->
            val dist = calculateDeltaEFromHex(garmentHex, wadaHex)
            if (dist < minDistance) {
                minDistance = dist
                closestIndex = index
            }
        }
        return closestIndex
    }

    data class Lab(val l: Double, val a: Double, val b: Double)
}
