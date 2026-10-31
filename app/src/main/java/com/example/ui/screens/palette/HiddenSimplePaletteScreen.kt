package com.example.ui.screens.palette

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.util.PaletteExportHelper
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.WadaColor
import com.example.data.WadaCombination
import com.example.ui.theme.WadaShapes
import com.example.util.ColorUtils
import com.example.util.TraditionalWadaColor
import com.example.util.WadaColorRegistry
import kotlinx.coroutines.launch

/**
 * HiddenSimplePaletteScreen: A zen, minimalist hidden mode showcasing Sanzo Wada's
 * 1930s dictionary color combinations one-by-one.
 *
 * Easter Egg Features:
 * - Unlocked by tapping the Looks App Icon on the Match Screen 7 times.
 * - Swipe through all 346 historical palettes starting from Palette 1.
 * - Swipe left moves forward, swipe right moves backward.
 * - Non-circular navigation: page 0 (Palette 1) has a strict boundary block preventing
 *   wrap-around to Palette 346.
 * - Allows quick one-tap application of any palette to the active wardrobe theme.
 */
@Composable
fun HiddenSimplePaletteScreen(
    combinations: List<WadaCombination>,
    wadaColors: List<WadaColor>,
    selectedCombination: WadaCombination?,
    onSelectPalette: (WadaCombination) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Intercept hardware/system back gesture to smoothly exit hidden mode
    BackHandler(onBack = onExit)

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val totalCount = combinations.size.coerceAtLeast(1)

    // Initial page starts strictly at 0 (Palette 1).
    // The pager does NOT wrap around, enforcing the swipe block on Palette 1.
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { totalCount }
    )

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("hidden_simple_palette_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                .padding(top = 16.dp, bottom = 24.dp)
        ) {
            // Top App Bar: Exit button & Palette Counter Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Exit Easter Egg mode
                IconButton(
                    onClick = onExit,
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            CircleShape
                        )
                        .testTag("exit_hidden_mode_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit Simple Palette Mode",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Palette Number Indicator (e.g. "Palette 1 of 346")
                Surface(
                    shape = WadaShapes.FilterPill,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.testTag("hidden_palette_counter")
                ) {
                    val currentNum = (pagerState.currentPage + 1).coerceAtMost(totalCount)
                    Text(
                        text = "PALETTE $currentNum OF $totalCount",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                // Current Part Tag (e.g. "Part 1")
                val activeCombo = combinations.getOrNull(pagerState.currentPage)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "P${activeCombo?.part ?: 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (combinations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                // Pager swiping through all palettes sequentially
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("hidden_palette_pager"),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    pageSpacing = 16.dp
                ) { page ->
                    val combo = combinations[page]
                    PaletteCardItem(
                        combination = combo,
                        paletteIndex = page + 1,
                        wadaColors = wadaColors,
                        isSelected = selectedCombination?.id == combo.id,
                        onApply = { onSelectPalette(combo) },
                        onExportPng = {
                            PaletteExportHelper.exportAndSharePalette(context, combo, wadaColors)
                            Toast.makeText(context, "Exporting Palette No. ${combo.id} as PNG...", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Navigation Footer: Previous, Apply, Next
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous button: Strictly disabled on page 0 so user cannot wrap to Palette 346
                FilledTonalIconButton(
                    onClick = {
                        if (pagerState.currentPage > 0) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                    },
                    enabled = pagerState.currentPage > 0,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("hidden_palette_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Palette"
                    )
                }

                // Quick Apply Button
                val currentCombo = combinations.getOrNull(pagerState.currentPage)
                val isCurrentSelected = currentCombo != null && selectedCombination?.id == currentCombo.id
                Button(
                    onClick = {
                        currentCombo?.let { onSelectPalette(it) }
                    },
                    shape = WadaShapes.FilterPill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCurrentSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        contentColor = if (isCurrentSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .height(48.dp)
                        .testTag("hidden_palette_apply_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCurrentSelected) "Selected Palette" else "Apply Palette",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Next button: Disabled on last page
                FilledTonalIconButton(
                    onClick = {
                        if (pagerState.currentPage < combinations.size - 1) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    enabled = pagerState.currentPage < combinations.size - 1,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("hidden_palette_next_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Palette"
                    )
                }
            }
        }
    }
}

/**
 * PaletteCardItem: Minimalist card displaying the palette colors stacked elegantly.
 */
@Composable
private fun PaletteCardItem(
    combination: WadaCombination,
    paletteIndex: Int,
    wadaColors: List<WadaColor>,
    isSelected: Boolean,
    onApply: () -> Unit,
    onExportPng: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("hidden_palette_card_$paletteIndex")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header: Title + Action Buttons (Export)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Palette #$paletteIndex",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (combination.name.isNotBlank()) {
                        Text(
                            text = combination.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // High-Res PNG Export button for external design apps
                IconButton(
                    onClick = onExportPng,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            CircleShape
                        )
                        .testTag("hidden_palette_export_btn_$paletteIndex")
                ) {
                    Icon(
                        imageVector = Icons.Default.IosShare,
                        contentDescription = "Export High-Res PNG",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Swatches stack filling available height
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                combination.colors.forEach { hex ->
                    val parsedColor = ColorUtils.parseSafeColor(hex)
                    val textColor = ColorUtils.getContrastingTextColor(parsedColor)
                    val traditional = WadaColorRegistry.findClosestTraditionalColor(hex, wadaColors)

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = parsedColor,
                        border = BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.12f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = traditional.wadaName,
                                    color = textColor,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (traditional.japaneseRomaji.isNotBlank() || traditional.japaneseKanji.isNotBlank()) {
                                    Text(
                                        text = "${traditional.japaneseRomaji} • ${traditional.japaneseKanji}",
                                        color = textColor.copy(alpha = 0.85f),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Hex Chip badge
                            Surface(
                                shape = WadaShapes.FilterPill,
                                color = textColor.copy(alpha = 0.18f),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    text = hex.uppercase(),
                                    color = textColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
