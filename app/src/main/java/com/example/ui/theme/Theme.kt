package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.data.WadaCombination
import com.example.util.ColorUtils

/**
 * Baseline Light Color Scheme for Wada - Washi Editorial.
 */
private val BaselineLightColorScheme = lightColorScheme(
    primary = WadaKonIro,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3F2),
    onPrimaryContainer = Color(0xFF0F1E2E),

    secondary = WadaShuIro,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD4),
    onSecondaryContainer = Color(0xFF3B0903),

    tertiary = WadaMoegi,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE8CF),
    onTertiaryContainer = Color(0xFF1B2313),

    background = WashiBackgroundLight,
    onBackground = WashiOnSurfaceLight,

    surface = WashiSurfaceLight,
    onSurface = WashiOnSurfaceLight,
    surfaceVariant = WashiSurfaceVariantLight,
    onSurfaceVariant = WashiOnSurfaceVariantLight,

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF5ED),
    surfaceContainer = Color(0xFFF3ECE0),
    surfaceContainerHigh = Color(0xFFECE3D4),
    surfaceContainerHighest = Color(0xFFE4DAC9),

    outline = WashiOutlineLight,
    outlineVariant = Color(0xFFDBD3C3)
)

/**
 * Baseline Dark Color Scheme for Wada - Sumi Lacquer.
 */
private val BaselineDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA5C8E4),
    onPrimary = Color(0xFF0D2235),
    primaryContainer = Color(0xFF263D54),
    onPrimaryContainer = Color(0xFFCCE4F8),

    secondary = Color(0xFFFFB4A6),
    onSecondary = Color(0xFF5E170C),
    secondaryContainer = Color(0xFF7D2B1E),
    onSecondaryContainer = Color(0xFFFFDAD4),

    tertiary = Color(0xFFB5C9A4),
    onTertiary = Color(0xFF233516),
    tertiaryContainer = Color(0xFF384B2B),
    onTertiaryContainer = Color(0xFFD1E5C0),

    background = SumiBackgroundDark,
    onBackground = SumiOnSurfaceDark,

    surface = SumiSurfaceDark,
    onSurface = SumiOnSurfaceDark,
    surfaceVariant = SumiSurfaceVariantDark,
    onSurfaceVariant = SumiOnSurfaceVariantDark,

    surfaceContainerLowest = Color(0xFF0E0C0B),
    surfaceContainerLow = Color(0xFF171412),
    surfaceContainer = Color(0xFF1E1A17),
    surfaceContainerHigh = Color(0xFF28231F),
    surfaceContainerHighest = Color(0xFF332D28),

    outline = SumiOutlineDark,
    outlineVariant = Color(0xFF4C453C)
)

/**
 * WadaTheme applies Material 3 Expressive styling with dynamic Sanzo Wada
 * palette integration. When a user selects a historic color combination,
 * primary and secondary brand accents harmonize dynamically with the selected palette.
 */
@Composable
fun WadaTheme(
    wadaCombination: WadaCombination? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    useMonet: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // Monet dynamic wallpaper color scheme vs Standard curated baseline palette
    val colorScheme = remember(darkTheme, useMonet) {
        if (useMonet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (darkTheme) BaselineDarkColorScheme else BaselineLightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ExpressiveShapes,
        content = content
    )
}
