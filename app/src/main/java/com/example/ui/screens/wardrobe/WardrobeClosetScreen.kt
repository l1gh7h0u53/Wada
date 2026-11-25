package com.example.ui.screens.wardrobe

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.example.data.ClothingItem
import com.example.ui.components.GarmentCard
import com.example.ui.components.MinimalistGalleryEmptyState
import com.example.ui.screens.dialogs.AddGarmentDialog
import com.example.ui.screens.dialogs.EditGarmentDialog
import com.example.ui.theme.WadaShapes
import com.example.util.ColorGroupClassifier
import com.example.util.ColorLoreInfo
import com.example.util.ColorUtils
import com.example.util.PrimaryColorGroup
import com.example.util.StyleTagHelper
import com.example.util.WadaColorFilterPill
import com.example.util.WadaColorLore
import com.example.util.WadaColorRegistry
import com.example.viewmodel.WardrobeViewModel
import kotlinx.coroutines.delay

/**
 * WardrobeClosetScreen manages the user's digital wardrobe inventory with Material 3 Expressive motion.
 *
 * Features:
 * - Horizontal scrollable 'Style Tags' (e.g. Formal, Casual, Monochromatic, Minimalist, Traditional Japanese)
 *   positioned above the search bar for one-tap aesthetic filtering.
 * - Traditional Sanzo Wada Color Search Bar with an educational 'Color Lore' tooltip/info panel explaining
 *   the historical and cultural significance of the filtered color.
 * - Quick-tap Wada color suggestion filter chips with live color swatches.
 * - Staggered bouncy entrance animations for clothing cards during filtering and loading.
 * - Minimalist Gallery empty state aesthetic with contextual recommendations.
 * - Transparent container-less layout: no redundant nested scaffolds, allowing the bottom navigation
 *   bar to float naturally above the app content.
 *
 * @param viewModel The shared wardrobe state coordinator
 * @param onNavigateToDigitizer Callback to launch the camera color digitizer studio
 */
@Composable
fun WardrobeClosetScreen(
    viewModel: WardrobeViewModel,
    onNavigateToDigitizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val allClothing by viewModel.allClothing.collectAsState()
    val filteredList by viewModel.filteredClosetClothing.collectAsState()
    val closetFilter by viewModel.closetFilter.collectAsState()
    val sqlColorGroupCounts by viewModel.colorGroupCounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val wadaColors by viewModel.wadaColors.collectAsState()
    val selectedGarment by viewModel.selectedGarment.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()

    val selectedFilter = closetFilter.category
    val selectedStyleTag = closetFilter.styleTag
    val selectedColorGroup = closetFilter.colorGroup
    val colorSearchQuery = closetFilter.colorQuery

    var showColorLore by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var itemPendingDelete by remember { mutableStateOf<ClothingItem?>(null) }
    var itemPendingEdit by remember { mutableStateOf<ClothingItem?>(null) }

    LaunchedEffect(context) {
        viewModel.startNetworkMonitoring(context)
    }

    // LazyGrid scroll state
    val gridState = rememberLazyGridState()

    // Precompute closest traditional Wada color for all garments
    val itemWadaColors = remember(allClothing, wadaColors) {
        allClothing.associate { it.id to WadaColorRegistry.findClosestTraditionalColor(it.hexColor, wadaColors) }
    }

    // Resolve color lore for the current color query
    val activeColorLore = remember(colorSearchQuery) {
        if (colorSearchQuery.isNotBlank()) {
            WadaColorLore.getLoreForQuery(colorSearchQuery)
        } else {
            WadaColorLore.getLoreForQuery("wada")
        }
    }

    // Prefer Room SQLite pre-aggregated counts, falling back to live classification before backfill completes
    val colorGroupCounts = remember(sqlColorGroupCounts, allClothing) {
        if (sqlColorGroupCounts.isNotEmpty() || allClothing.isEmpty()) {
            sqlColorGroupCounts
        } else {
            allClothing.groupingBy { item ->
                item.colorGroup.ifBlank { ColorGroupClassifier.classifyHex(item.hexColor).label }
            }.eachCount()
        }
    }

    // Container-less layout: direct Column hosted inside MainScreen with status bar clearance
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("wardrobe_closet_screen")
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        if (isOffline) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .testTag("closet_offline_cache_badge")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Offline • Local SQLite wardrobe cache active",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // 1. Horizontal Scrollable 'Style Tags' (Positioned ABOVE the closet search bar)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("style_tags_row")
        ) {
            items(StyleTagHelper.POPULAR_STYLE_TAGS) { tag ->
                val isSelected = selectedStyleTag.equals(tag.name, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        viewModel.updateClosetStyleTagFilter(
                            if (isSelected && tag.name != "All Styles") "All Styles" else tag.name
                        )
                    },
                    label = {
                        Text(
                            text = tag.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    shape = WadaShapes.FilterPill,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        selectedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("style_tag_${tag.name}")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Material 3 Expressive Wada Color Search Bar with Color Lore Trigger
        WadaColorSearchBar(
            query = colorSearchQuery,
            onQueryChange = {
                viewModel.updateClosetColorQuery(it)
                if (it.isNotBlank()) showColorLore = true
            },
            onClearQuery = {
                viewModel.updateClosetColorQuery("")
                showColorLore = false
            },
            onToggleLore = { showColorLore = !showColorLore },
            isLoreActive = showColorLore,
            modifier = Modifier.fillMaxWidth()
        )

        // 3. 'Color Lore' Info Panel / Educational Tooltip (Collapsible)
        AnimatedVisibility(
            visible = showColorLore,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            ColorLoreInfoPanel(
                lore = activeColorLore,
                onDismiss = { showColorLore = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .testTag("wada_color_lore_card")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Traditional Wada Color Suggestion Pills (Quick One-Tap Swatch Filters)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wada_color_quick_filters_row")
        ) {
            items(WadaColorRegistry.POPULAR_FILTER_PILLS) { pill ->
                val isSelected = if (pill.query == null) {
                    colorSearchQuery.isBlank()
                } else {
                    colorSearchQuery.equals(pill.query, ignoreCase = true)
                }

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected && pill.query != null) {
                            viewModel.updateClosetColorQuery("") // toggle off if tapped again
                            showColorLore = false
                        } else {
                            viewModel.updateClosetColorQuery(pill.query ?: "")
                            if (pill.query != null) showColorLore = true
                        }
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (pill.swatchHex != null) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(ColorUtils.parseSafeColor(pill.swatchHex))
                                        .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.6f)), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Palette,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = pill.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    },
                    shape = WadaShapes.FilterPill,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        selectedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("wada_filter_chip_${pill.label}")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 5. Primary Color Group Auto-Tag Filter Chips Row
        ColorGroupFilterRow(
            selectedGroup = selectedColorGroup,
            groupCounts = colorGroupCounts,
            onGroupSelected = { viewModel.updateClosetColorGroupFilter(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 6. Category Filter Chips Row (Hero navigation: All, Favorites, Tops, Bottoms...)
        val filterOptions = listOf("All", "Favorites") + categories
        LazyGridFilterChips(
            options = filterOptions,
            selected = selectedFilter,
            onSelectedChange = { viewModel.updateClosetCategoryFilter(it) }
        )

        // 7. Active Filter Indicators feedback bar
        if (colorSearchQuery.isNotBlank() || selectedStyleTag != "All Styles" || selectedColorGroup != "All") {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = WadaShapes.FilterPill,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        val activeFilterLabel = buildList {
                            if (selectedColorGroup != "All") add("Group: $selectedColorGroup")
                            if (colorSearchQuery.isNotBlank()) add("Tone: \"$colorSearchQuery\"")
                            if (selectedStyleTag != "All Styles") add("Style: \"$selectedStyleTag\"")
                        }.joinToString(" • ")

                        Text(
                            text = activeFilterLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${filteredList.size} found",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Text(
                        text = "Reset All",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(WadaShapes.FilterPill)
                            .clickable {
                                viewModel.resetClosetFilters()
                                showColorLore = false
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("clear_filter_badge_btn")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 7. Grid of Clothing Items or Minimalist Gallery Empty State
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 110.dp, top = 8.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                if (allClothing.isEmpty()) {
                    MinimalistGalleryEmptyState(
                        title = "Your Wardrobe Gallery is Empty",
                        subtitle = "Curate your garments into digital color archetypes. Capture via the Camera Lab or digitize tones from photos to unlock Sanzo Wada 1930s color harmony matching.",
                        primaryButtonText = "Digitize First Garment",
                        onPrimaryClick = onNavigateToDigitizer,
                        secondaryButtonText = null,
                        onSecondaryClick = null
                    )
                } else if (selectedColorGroup != "All") {
                    MinimalistGalleryEmptyState(
                        title = "No $selectedColorGroup Garments",
                        subtitle = "No garments in your closet are tagged in the $selectedColorGroup color group under the current filters. Try another color group or show all colors.",
                        primaryButtonText = "Show All Color Groups",
                        onPrimaryClick = { viewModel.updateClosetColorGroupFilter("All") },
                        secondaryButtonText = "Digitize New Piece",
                        onSecondaryClick = onNavigateToDigitizer
                    )
                } else if (colorSearchQuery.isNotBlank()) {
                    MinimalistGalleryEmptyState(
                        title = "No Garments Matching \"$colorSearchQuery\"",
                        subtitle = "None of your current wardrobe garments align with the traditional Wada color \"$colorSearchQuery\". Try searching for another classical tone like Kon (Indigo), Moegi (Green), Yamabuki (Gold), or clear the color filter.",
                        primaryButtonText = "Clear Color Filter",
                        onPrimaryClick = {
                            viewModel.updateClosetColorQuery("")
                            showColorLore = false
                        },
                        secondaryButtonText = "Digitize New Piece",
                        onSecondaryClick = onNavigateToDigitizer
                    )
                } else if (selectedStyleTag != "All Styles") {
                    MinimalistGalleryEmptyState(
                        title = "No $selectedStyleTag Pieces",
                        subtitle = "You don't have any garments tagged as $selectedStyleTag yet. Try switching to 'All Styles' or digitize a new piece.",
                        primaryButtonText = "Show All Styles",
                        onPrimaryClick = { viewModel.updateClosetStyleTagFilter("All Styles") },
                        secondaryButtonText = "Digitize New Piece",
                        onSecondaryClick = onNavigateToDigitizer
                    )
                } else {
                    MinimalistGalleryEmptyState(
                        title = if (selectedFilter == "Favorites") "No Favorite Pieces Yet" else "No $selectedFilter Pieces Found",
                        subtitle = if (selectedFilter == "Favorites")
                            "Tap the heart icon on any garment in your wardrobe to pin it to your curated favorites collection."
                        else
                            "No garments matching '$selectedFilter' are currently in your collection. Try switching filter tabs or digitize a new piece.",
                        primaryButtonText = "Digitize New Piece",
                        onPrimaryClick = onNavigateToDigitizer,
                        secondaryButtonText = "Show All Garments",
                        onSecondaryClick = { viewModel.updateClosetCategoryFilter("All") }
                    )
                }
            }
        } else {
            Text(
                text = "Hold any garment to edit exact color & photo rotation",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .testTag("closet_long_press_hint")
            )

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 120.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = filteredList,
                    key = { _, it -> it.id }
                ) { index, item ->
                    // Staggered Bouncy Entrance with Spring Physics
                    val enterAnim = remember(item.id, selectedFilter, selectedStyleTag, colorSearchQuery) { Animatable(0f) }
                    LaunchedEffect(item.id, selectedFilter, selectedStyleTag, colorSearchQuery) {
                        delay((index * 30L).coerceAtMost(240L))
                        enterAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    }

                    GarmentCard(
                        item = item,
                        isSelected = selectedGarment?.id == item.id,
                        onClick = { viewModel.selectGarment(item) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onDeleteClick = { itemPendingDelete = item },
                        onLongClick = { itemPendingEdit = item },
                        traditionalColor = itemWadaColors[item.id],
                        modifier = Modifier
                            .animateItem()
                            .graphicsLayer {
                                alpha = enterAnim.value
                                scaleX = 0.88f + 0.12f * enterAnim.value
                                scaleY = 0.88f + 0.12f * enterAnim.value
                                translationY = (1f - enterAnim.value) * 44f
                            }
                    )
                }
            }
        }
    }

    // Modal Add Garment Dialog
    if (showAddDialog) {
        AddGarmentDialog(
            categories = categories,
            onDismiss = { showAddDialog = false },
            onConfirm = { cat, hex ->
                viewModel.createVirtualGarment(context, cat, hex)
            }
        )
    }

    // Deletion Confirmation Dialog
    itemPendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemPendingDelete = null },
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Delete Garment?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove this ${item.category} item (${item.hexColor}) from your digital wardrobe? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        viewModel.deleteGarment(item.id)
                        itemPendingDelete = null
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { itemPendingDelete = null },
                    shape = CircleShape
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Long-press Edit Garment Dialog (exact color & picture rotation)
    itemPendingEdit?.let { item ->
        EditGarmentDialog(
            item = item,
            wadaColors = wadaColors,
            onDismiss = { itemPendingEdit = null },
            onSave = { newHex, rotationDegrees, thermalType ->
                viewModel.updateGarmentColorAndRotation(
                    context = context,
                    item = item,
                    newHexColor = newHex,
                    rotationDegrees = rotationDegrees,
                    thermalType = thermalType
                ) {
                    itemPendingEdit = null
                    Toast.makeText(context, "Updated garment details", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

/**
 * Material 3 Expressive Search Bar for Traditional Wada Color Lookup with Color Lore toggle.
 */
@Composable
private fun WadaColorSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onToggleLore: () -> Unit,
    isLoreActive: Boolean,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = "Filter by Wada color (Kon, Moegi, Yamabuki)...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search Wada Colors",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Color Lore Educational Tooltip / Toggle Button
                IconButton(
                    onClick = onToggleLore,
                    modifier = Modifier.testTag("color_lore_toggle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Color Lore Info",
                        tint = if (isLoreActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = onClearQuery,
                        modifier = Modifier.testTag("clear_color_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("closet_color_search_input")
    )
}

/**
 * Educational Color Lore Info Panel.
 * Explains the historical significance, era, and Wada 1930s styling tips for the filtered color.
 */
@Composable
private fun ColorLoreInfoPanel(
    lore: ColorLoreInfo,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Swatch + Title + Era Pill + Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(ColorUtils.parseSafeColor(lore.swatchHex))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${lore.title} • ${lore.kanji}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (lore.englishMeaning.isNotBlank() &&
                        !lore.englishMeaning.startsWith("A Dictionary of Color Combinations", ignoreCase = true)
                    ) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = WadaShapes.FilterPill,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = lore.englishMeaning,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("color_lore_close_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close Lore",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Historical Era
            Text(
                text = "Era: ${lore.era}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Historical lore body text
            Text(
                text = lore.history,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Sanzo Wada 1930s Styling Guidance Note
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(14.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Wada Styling: ${lore.wadaStylingNote}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * Filter chips row supporting scrollable category pills following M3 Chips guidelines.
 */
@Composable
private fun LazyGridFilterChips(
    options: List<String>,
    selected: String,
    onSelectedChange: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(options) { option ->
            val isSelected = selected == option
            FilterChip(
                selected = isSelected,
                onClick = { onSelectedChange(option) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (option == "Favorites") {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = option,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                },
                shape = WadaShapes.FilterPill,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("filter_chip_$option")
            )
        }
    }
}

/**
 * Scrollable primary color group filter row powered by imagery-based auto-tagging.
 */
@Composable
private fun ColorGroupFilterRow(
    selectedGroup: String,
    groupCounts: Map<String, Int>,
    onGroupSelected: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("color_group_filter_row")
    ) {
        item {
            val isAllSelected = selectedGroup == "All"
            FilterChip(
                selected = isAllSelected,
                onClick = { onGroupSelected("All") },
                label = {
                    Text(
                        text = "All Groups",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                shape = WadaShapes.FilterPill,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isAllSelected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    selectedBorderColor = MaterialTheme.colorScheme.tertiary
                ),
                modifier = Modifier.testTag("color_group_chip_All")
            )
        }

        items(PrimaryColorGroup.entries) { group ->
            val count = groupCounts[group.label] ?: 0
            val isSelected = selectedGroup.equals(group.label, ignoreCase = true)
            val labelText = if (count > 0) "${group.label} ($count)" else group.label

            FilterChip(
                selected = isSelected,
                onClick = {
                    onGroupSelected(if (isSelected) "All" else group.label)
                },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(ColorUtils.parseSafeColor(group.swatchHex))
                                .border(
                                    BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = labelText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                },
                shape = WadaShapes.FilterPill,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (count > 0) 0.55f else 0.3f),
                    selectedBorderColor = MaterialTheme.colorScheme.tertiary
                ),
                modifier = Modifier.testTag("color_group_chip_${group.label}")
            )
        }
    }
}
