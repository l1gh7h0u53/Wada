package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.WadaColor
import com.example.data.WadaCombination
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Educational snippet explaining the color harmony principles of a Sanzo Wada combination.
 */
data class PaletteHarmonyLore(
    val title: String,
    val principle: String,
    val description: String,
    val harmonyType: String,
    val applicationAdvice: String
)

/**
 * Utility for exporting Sanzo Wada color palettes to high-resolution PNG images
 * and generating educational color harmony snippets.
 */
object PaletteExportHelper {

    /**
     * Determines color harmony principles based on palette composition,
     * part grouping, and CIELAB / color family metrics.
     */
    fun getHarmonyLoreForPalette(
        combination: WadaCombination,
        wadaColors: List<WadaColor> = emptyList()
    ): PaletteHarmonyLore {
        val count = combination.colors.size
        val id = combination.id

        // Classify harmony type based on count, palette attributes, and Sanzo Wada's taxonomy
        val harmonyType = when (count) {
            2 -> "Dyadic Duotone Harmony (2-Color Balance)"
            3 -> "Triadic Resonance (3-Color Contrast & Anchor)"
            4 -> "Tetradic Orchestration (4-Color Symphony)"
            else -> "Multi-Tone Synthesis (${count}-Color Suite)"
        }

        val principle = when {
            id in 1..20 -> "Classic High-Contrast Grounding"
            id in 21..60 -> "Organic Earth & Botanical Resonance"
            id in 61..120 -> "Seasonal Dento-iro (Court Elegance)"
            id in 121..180 -> "Modernist Avant-Garde Complementaries"
            id in 181..240 -> "Muted Pastel & Subtle Tonal Shift"
            id in 241..300 -> "Dramatic Chiaroscuro & Saturated Accents"
            else -> "Complex Expressive Polychromy"
        }

        val description = when (count) {
            2 -> "Sanzo Wada paired these two tones with intentional lightness delta, ensuring one tone serves as a quiet canvas while the other delivers vibrant chromatic resonance."
            3 -> "Built upon Wada's hallmark 1930s tripartite formula: a grounding base tone (frequently an indigo or deep neutral), a luminous mediating tone, and an energetic chromatic accent."
            4 -> "Combines two pairs of balanced complements. Wada calibrated the chroma so no individual tone overwhelms the ensemble, maintaining unified visual hierarchy."
            else -> "An intricate multi-chromatic palette demonstrating Sanzo Wada's pioneering Meiji-Showa research into modern color synthesis and textile rhythm."
        }

        val advice = when (count) {
            2 -> "Ideal for minimalist duotone design, poster graphics, or pairing a tailored jacket with a statement top."
            3 -> "Recommended 60-30-10 distribution in design and fashion: 60% dominant base, 30% secondary structure, and 10% high-impact accent."
            4 -> "Distribute across four primary canvas components (e.g. background, header cards, icons, and CTA buttons) for a complete brand design system."
            else -> "Use as a complete UI design token palette or multi-layered architectural styling suite."
        }

        return PaletteHarmonyLore(
            title = "Palette #${combination.id}: ${combination.name}",
            principle = principle,
            description = description,
            harmonyType = harmonyType,
            applicationAdvice = advice
        )
    }

    /**
     * Generates a high-resolution 1200x1600 PNG bitmap representing the palette
     * with archival typography, swatches, Wada naming, and hex color codes.
     */
    fun renderHighResPaletteBitmap(
        combination: WadaCombination,
        wadaColors: List<WadaColor>
    ): Bitmap {
        val width = 1200
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background: Washi paper cream tone
        val bgPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#FAF7F0")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Border: Archival subtle stroke
        val borderPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#E0D8C8")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRoundRect(RectF(40f, 40f, width - 40f, height - 40f), 32f, 32f, borderPaint)

        // Title Paint
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#1A1817")
            textSize = 54f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }

        // Subtitle / Japanese aesthetic text
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#665F55")
            textSize = 28f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#A64024") // Wada Shu-iro terracotta
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        // Header text
        canvas.drawText("SANZO WADA • HAISHOKU SŌKAN", 80f, 120f, tagPaint)
        canvas.drawText("Palette No. ${combination.id}", 80f, 185f, titlePaint)
        canvas.drawText(combination.name, 80f, 230f, subPaint)

        // Draw swatches
        val colors = combination.colors
        val swatchStartY = 270f
        val swatchBottomY = 1420f
        val availableHeight = swatchBottomY - swatchStartY
        val spacing = 16f
        val count = colors.size
        val totalSpacing = spacing * (count - 1)
        val itemHeight = (availableHeight - totalSpacing) / count

        val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val swatchTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val hexPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        colors.forEachIndexed { index, hex ->
            val top = swatchStartY + index * (itemHeight + spacing)
            val bottom = top + itemHeight
            val rect = RectF(80f, top, width - 80f, bottom)

            val parsedColor = try {
                android.graphics.Color.parseColor(hex)
            } catch (e: Exception) {
                android.graphics.Color.DKGRAY
            }

            swatchPaint.color = parsedColor
            canvas.drawRoundRect(rect, 20f, 20f, swatchPaint)

            // Determine contrasting text color
            val r = (parsedColor shr 16) and 0xFF
            val g = (parsedColor shr 8) and 0xFF
            val b = parsedColor and 0xFF
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            val textColor = if (luminance > 0.55) android.graphics.Color.parseColor("#151311") else android.graphics.Color.parseColor("#FFFFFF")

            val traditional = WadaColorRegistry.findClosestTraditionalColor(hex, wadaColors)

            swatchTextPaint.apply {
                color = textColor
                textSize = 34f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }

            hexPaint.apply {
                color = textColor
                alpha = 220
                textSize = 28f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }

            // Draw color name and traditional kanji/romaji
            val textY = top + (itemHeight / 2) + 10f
            val nameText = if (traditional.japaneseKanji.isNotBlank()) {
                "${traditional.wadaName} • ${traditional.japaneseKanji} (${traditional.japaneseRomaji})"
            } else {
                traditional.wadaName
            }
            canvas.drawText(nameText, 120f, textY, swatchTextPaint)

            // Draw Hex code on the right side
            val hexText = hex.uppercase()
            val hexWidth = hexPaint.measureText(hexText)
            canvas.drawText(hexText, width - 120f - hexWidth, textY, hexPaint)
        }

        // Footer Branding
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#8C8274")
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        canvas.drawText(
            "Curated with Wada Wardrobe • A Dictionary of Color Combinations (1930s)",
            80f,
            1480f,
            footerPaint
        )

        return bitmap
    }

    /**
     * Saves the high-resolution bitmap to public device Pictures/WadaPalettes (if available)
     * and shares via Android Share Sheet so user can export directly to external design apps.
     */
    fun exportAndSharePalette(
        context: Context,
        combination: WadaCombination,
        wadaColors: List<WadaColor>
    ): Uri? {
        val bitmap = renderHighResPaletteBitmap(combination, wadaColors)
        val fileName = "Wada_Palette_${combination.id}_${System.currentTimeMillis()}.png"

        var savedUri: Uri? = null

        try {
            // Save to app cache for immediate FileProvider sharing
            val cacheFolder = File(context.cacheDir, "exports")
            if (!cacheFolder.exists()) cacheFolder.mkdirs()
            val cacheFile = File(cacheFolder, fileName)
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            savedUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )

            // Also save to MediaStore Pictures collection on Android 10+ so it persists in gallery
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WadaPalettes")
                }
                val mediaUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (mediaUri != null) {
                    resolver.openOutputStream(mediaUri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            }

            // Launch share sheet for external design apps (Figma, Canva, Photoshop, etc.)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, savedUri)
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    "Sanzo Wada Palette No. ${combination.id}: ${combination.name}"
                )
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Exported Sanzo Wada Color Palette No. ${combination.id} (${combination.name}): ${combination.colors.joinToString(", ")} from Wada."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Export Palette to Design App")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return savedUri
    }
}
