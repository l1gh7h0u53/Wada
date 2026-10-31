package com.example.ui.screens.outfit

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.material.icons.filled.IosShare
import com.example.data.ClothingItem
import com.example.data.WadaColor
import com.example.data.WadaCombination
import com.example.ui.components.MinimalistGalleryEmptyState
import com.example.ui.screens.dialogs.PaletteHarmonyInfoDialog
import com.example.ui.screens.dialogs.WadaPaletteHistoryDialog
import com.example.ui.theme.WadaShapes
import com.example.ui.theme.buildWadaText
import com.example.util.ColorMatcher
import com.example.util.ColorUtils
import com.example.util.PaletteExportHelper
import com.example.viewmodel.OutfitRecommendation
import com.example.viewmodel.WardrobeViewModel

/**
 * OutfitCanvasScreen provides the streamlined, minimalist styling workspace for Wada.
 *
 * Material 3 Expressive Refinement:
 * - Minimalist Hero Card: Unifies active palette swatches and harmony score in a clean, compact footprint.
 * - Compact Layer Chips: Shows Tops, Bottoms, Shoes, and Accessories as interactive M3 Chips.
 * - Expandable Details: Offers deeper CIELAB perceptual science insights without visual clutter.
 * - Wada Palette History: Instant access via dedicated history icon buttons in the header & palette bar.
 * - M3 Buttons & FilterChips for combination browsing adhering to official Material 3 specs.
 *
 * @param viewModel The shared wardrobe state coordinator
 * @param onNavigateToCloset Callback to view the full wardrobe closet
 * @param onNavigateToDigitizer Callback to launch the camera color digitizer
 */
@Composable
fun OutfitCanvasScreen(
    viewModel: WardrobeViewModel,
    onNavigateToCloset: () -> Unit,
    onNavigateToDigitizer: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenHiddenPaletteMode: () -> Unit = {}
) {
    val context = LocalContext.current
    val allClothing by viewModel.allClothing.collectAsState()
    val wadaCombinations by viewModel.wadaCombinations.collectAsState()
    val selectedCombination by viewModel.selectedCombination.collectAsState()
    val selectedGarment by viewModel.selectedGarment.collectAsState()
    val recommendation by viewModel.outfitRecommendations.collectAsState()
    val paletteHistory by viewModel.paletteHistory.collectAsState()
    val themeSetting by viewModel.themeSetting.collectAsState()

    var showHistoryModal by remember { mutableStateOf(false) }
    var showHarmonyInfoDialog by remember { mutableStateOf(false) }
    val wadaColors by viewModel.wadaColors.collectAsState()
    var isDetailsExpanded by remember { mutableStateOf(false) }
    var selectedPartFilter by remember { mutableIntStateOf(0) } // 0 = All, 1, 2, etc.

    // Calculate CIELAB Delta-E distances between outfit garments and palette colors
    val harmonyAssessment = remember(recommendation, selectedCombination) {
        val distances = mutableListOf<Double>()
        val combo = selectedCombination
        if (combo != null && recommendation != null) {
            val pieces = listOfNotNull(
                recommendation?.top,
                recommendation?.bottom,
                recommendation?.shoes,
                recommendation?.accessory
            )
            pieces.forEachIndexed { index, garment ->
                val targetHex = combo.colors.getOrNull(index % combo.colors.size)
                if (targetHex != null) {
                    val deltaE = ColorMatcher.calculateDeltaEFromHex(garment.hexColor, targetHex)
                    distances.add(deltaE)
                }
            }
        }
        ColorUtils.assessOutfitHarmony(distances)
    }

    // Filter combinations if user selects specific part
    val displayedCombinations = remember(wadaCombinations, selectedPartFilter) {
        if (selectedPartFilter == 0) wadaCombinations
        else wadaCombinations.filter { it.part == selectedPartFilter }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("outfit_canvas_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Action Bar with Looks Brand Icon, History and Quick Shuffle
        item {
            val isLightMode = when (themeSetting) {
                "LIGHT" -> true
                "DARK" -> false
                else -> ColorUtils.isLightColor(MaterialTheme.colorScheme.background)
            }
            val looksIconColor = if (isLightMode) Color(0xFF000000) else Color(0xFFFFFFFF)

            var secretTapCount by remember { mutableIntStateOf(0) }
            var lastTapTimestamp by remember { mutableLongStateOf(0L) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Top-Left: Looks Brand Icon with secret 7-tap Easter Egg unlock
                Icon(
                    painter = painterResource(id = R.drawable.ic_looks),
                    contentDescription = "Looks Brand Icon",
                    tint = looksIconColor,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("match_screen_looks_icon")
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null // Silent, secret interaction without revealing hints or toasts
                        ) {
                            val now = System.currentTimeMillis()
                            // If user pauses more than 2 seconds, reset tap counter
                            if (now - lastTapTimestamp < 2000L) {
                                secretTapCount++
                            } else {
                                secretTapCount = 1
                            }
                            lastTapTimestamp = now

                            // Exactly 7 taps unlocks the hidden simple palette mode silently
                            if (secretTapCount >= 7) {
                                secretTapCount = 0
                                onOpenHiddenPaletteMode()
                            }
                        }
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Wada Palette History Button (Dedicated access)
                    IconButton(
                        onClick = { showHistoryModal = true },
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                CircleShape
                            )
                            .testTag("palette_history_header_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (paletteHistory.isNotEmpty()) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text("${paletteHistory.size}")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Palette History",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Quick Shuffle Button
                    IconButton(
                        onClick = {
                            if (wadaCombinations.isNotEmpty()) {
                                viewModel.selectCombination(wadaCombinations.random())
                            }
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                CircleShape
                            )
                            .testTag("shuffle_palette_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = "Shuffle Palette",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 2. Minimalist Hero Card or Minimalist Gallery Empty State
        item {
        if (allClothing.isEmpty()) {
            MinimalistGalleryEmptyState(
                title = "Gallery Awaiting Garments",
                subtitle = "Sanzo Wada's 1930s color harmony engine pairs your wardrobe pieces with 346 historic multi-tone combinations. Add your first top, bottoms, or accessories to generate tailored outfit ensembles.",
                primaryButtonText = "Digitize First Garment",
                onPrimaryClick = onNavigateToDigitizer,
                secondaryButtonText = null,
                onSecondaryClick = null
            )
        } else {
            selectedCombination?.let { combo ->
                MinimalistMatchHeroCard(
                    combo = combo,
                    assessment = harmonyAssessment,
                    recommendation = recommendation,
                    selectedGarment = selectedGarment,
                    isExpanded = isDetailsExpanded,
                    onToggleExpand = { isDetailsExpanded = !isDetailsExpanded },
                    onNavigateToCloset = onNavigateToCloset,
                    onSelectGarment = { viewModel.selectGarment(it) },
                    onShowInfo = { showHarmonyInfoDialog = true },
                    onExportPng = {
                        PaletteExportHelper.exportAndSharePalette(context, combo, wadaColors)
                        Toast.makeText(context, "Exporting Palette No. ${combo.id} as PNG...", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

        // 3. Historic Wada Combinations with M3 Buttons and FilterChips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "HISTORIC COMBINATIONS",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Wada Palette History pill button in palette bar
                AssistChip(
                    onClick = { showHistoryModal = true },
                    label = {
                        Text(
                            text = "History (${paletteHistory.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    shape = CircleShape,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.testTag("palette_history_bar_button")
                )
            }
        }

        // 4. Combination Selector Chips (M3 FilterChips)
        item {
            val availableParts = listOf(0, 1, 2, 3)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(availableParts) { part ->
                    val isSelected = selectedPartFilter == part
                    val label = if (part == 0) "All Palettes (${wadaCombinations.size})" else "Part $part"

                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedPartFilter = part },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        shape = WadaShapes.FilterPill,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("part_filter_chip_$part")
                    )
                }
            }
        }

        // 5. Combinations Horizontal Streamlined Carousel (M3 Cards & Chips)
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(displayedCombinations, key = { it.id }) { combo ->
                    val isSelected = selectedCombination?.id == combo.id

                    CompactCombinationCard(
                        combo = combo,
                        isSelected = isSelected,
                        onClick = { viewModel.selectCombination(combo) }
                    )
                }
            }
        }

        // 6. Wardrobe Empty Callout if user hasn't added pieces yet
        if (allClothing.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Checkroom,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Your Wardrobe is Empty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Scan pieces with camera or add colors to see live outfit matches.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        FilledTonalButton(
                            onClick = onNavigateToDigitizer,
                            shape = WadaShapes.FilterPill
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Digitize Item", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }

    // Modal Sheet for Wada Palette History
    if (showHistoryModal) {
        WadaPaletteHistoryDialog(
            history = paletteHistory,
            currentCombination = selectedCombination,
            onSelectCombination = { combo ->
                viewModel.selectCombination(combo)
            },
            onDismiss = { showHistoryModal = false }
        )
    }

    // Modal Sheet for Educational Palette Harmony Lore and High-Res PNG Export
    if (showHarmonyInfoDialog && selectedCombination != null) {
        val combo = selectedCombination!!
        val lore = PaletteExportHelper.getHarmonyLoreForPalette(combo, wadaColors)

        PaletteHarmonyInfoDialog(
            combination = combo,
            wadaColors = wadaColors,
            lore = lore,
            onExportPng = {
                PaletteExportHelper.exportAndSharePalette(context, combo, wadaColors)
                Toast.makeText(context, "Exporting Palette No. ${combo.id} as PNG...", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showHarmonyInfoDialog = false }
        )
    }
}

/**
 * Minimalist Hero Card unifying active palette swatches, harmony gauge,
 * compact layer chips, and expandable color science details.
 */
@Composable
private fun MinimalistMatchHeroCard(
    combo: WadaCombination,
    assessment: ColorUtils.HarmonyAssessment,
    recommendation: OutfitRecommendation?,
    selectedGarment: ClothingItem?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onNavigateToCloset: () -> Unit,
    onSelectGarment: (ClothingItem?) -> Unit,
    onShowInfo: () -> Unit,
    onExportPng: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = assessment.scorePercent / 100f,
        animationSpec = tween(durationMillis = 600),
        label = "hero_progress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("minimalist_hero_match_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Row: Active Palette Name + Circular Score Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = WadaShapes.FilterPill,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "NO. ${combo.id}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = combo.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = assessment.badgeColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${assessment.grade} Harmony",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = assessment.badgeColor
                        )
                    }
                }

                // Compact Circular Score Gauge + Action Buttons (Info & Export)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Small 'Info' button for educational color harmony principles
                    IconButton(
                        onClick = onShowInfo,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                CircleShape
                            )
                            .testTag("palette_harmony_info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Palette Harmony Info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
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
                            .testTag("palette_export_png_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.IosShare,
                            contentDescription = "Export Palette as PNG",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Circular Score Gauge
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(50.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxSize(),
                            color = assessment.badgeColor,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            strokeWidth = 4.5.dp,
                            strokeCap = StrokeCap.Round
                        )
                        Text(
                            text = "${assessment.scorePercent}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Swatch Bar: Crisp, modern palette representation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp)),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                combo.colors.forEach { hex ->
                    val color = ColorUtils.parseSafeColor(hex)
                    val textCol = ColorUtils.getContrastingTextColor(color)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(color)
                            .border(BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.08f))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = hex.removePrefix("#"),
                            style = MaterialTheme.typography.labelSmall,
                            color = textCol.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Compact Layer Chips Row
            Text(
                text = "ENSEMBLE LAYERS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            val layers = listOf(
                "Top" to recommendation?.top,
                "Bottom" to recommendation?.bottom,
                "Shoes" to recommendation?.shoes,
                "Accessory" to recommendation?.accessory
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                layers.forEach { (category, garment) ->
                    val isBase = selectedGarment?.id == garment?.id && garment != null

                    CompactLayerChip(
                        category = category,
                        garment = garment,
                        isBase = isBase,
                        onClick = {
                            if (garment != null) {
                                onSelectGarment(garment)
                            } else {
                                onNavigateToCloset()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Expandable Details Section Toggle Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggleExpand)
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isExpanded) "Hide Color Science Details" else "View Color Science Details",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Expandable Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Wada CIELAB Assessment",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = assessment.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Historical Context (1930s)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Sanzo Wada pioneered empirical Japanese color harmonies, blending Meiji traditional dyes with Western chromatic balance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Compact interactive layer chip representing Top, Bottom, Shoes, or Accessory.
 */
@Composable
private fun CompactLayerChip(
    category: String,
    garment: ClothingItem?,
    isBase: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val garmentColor = garment?.let { ColorUtils.parseSafeColor(it.hexColor) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isBase) {
            MaterialTheme.colorScheme.primaryContainer
        } else if (garment != null) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(
            width = if (isBase) 1.5.dp else 1.dp,
            color = if (isBase) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
        ),
        modifier = modifier.testTag("layer_chip_${category.lowercase()}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Swatch dot or empty indicator
            if (garmentColor != null) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(garmentColor)
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBase) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Anchor",
                            tint = ColorUtils.getContrastingTextColor(garmentColor),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = category,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isBase) FontWeight.Bold else FontWeight.Medium,
                color = if (isBase) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = garment?.hexColor?.takeLast(3)?.uppercase() ?: "NONE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * Compact M3 Combination Card for browsing the Wada Dictionary in Buttons/Chips format.
 */
@Composable
private fun CompactCombinationCard(
    combo: WadaCombination,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            }
        ),
        modifier = modifier
            .width(180.dp)
            .testTag("compact_combo_card_${combo.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NO. ${combo.id}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Active",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Text(
                text = combo.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Swatch Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(6.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                combo.colors.forEach { hex ->
                    val color = ColorUtils.parseSafeColor(hex)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(color)
                    )
                }
            }
        }
    }
}
