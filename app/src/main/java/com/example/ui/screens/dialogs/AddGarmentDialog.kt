package com.example.ui.screens.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.WadaShapes
import com.example.util.ColorUtils

/**
 * Curated preset swatches from Sanzo Wada's foundational palette.
 */
private val WadaPresetColors = listOf(
    "#1E2C3D" to "Kon-iro (Indigo)",
    "#D94E34" to "Shu-iro (Vermilion)",
    "#568F8B" to "Asagi (Celadon)",
    "#627254" to "Moegi (Bamboo)",
    "#D49B35" to "Yamabuki (Amber)",
    "#7A3E48" to "Azuki (Russet)",
    "#2C4A6F" to "Ruri (Lapis)",
    "#1C1917" to "Kuro (Charcoal)",
    "#F3ECE0" to "Kinari (Ecru)",
    "#8B5A2B" to "Kitsune (Fawn)"
)

/**
 * AddGarmentDialog provides an expressive modal interface for adding
 * a garment to the wardrobe, with category and Wada pigment selection.
 *
 * @param categories Available clothing categories (e.g. Tops, Bottoms, Shoes, Accessories)
 * @param onDismiss Callback when dismissed
 * @param onConfirm Callback with selected category and hex color
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGarmentDialog(
    categories: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (category: String, hexColor: String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: "Tops") }
    var selectedHex by remember { mutableStateOf(WadaPresetColors.first().first) }
    var customHexInput by remember { mutableStateOf("") }
    var isCustomInputError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = WadaShapes.AsymmetricHeroCard,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("add_garment_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Header with title and close icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add to Wardrobe",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 1: Apparel Category Selection
                Text(
                    text = "CATEGORY",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        val isSelected = selectedCategory == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = category },
                            label = { Text(category) },
                            shape = WadaShapes.FilterPill,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("chip_category_$category")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 2: Color Preview & Wada Pigments
                Text(
                    text = "SANZO WADA PIGMENT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Active Color Banner Preview
                val activeColor = ColorUtils.parseSafeColor(selectedHex)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(WadaShapes.SwatchSquircle)
                        .background(activeColor)
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), WadaShapes.SwatchSquircle),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedHex.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = ColorUtils.getContrastingTextColor(activeColor)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Palette Swatch Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(WadaPresetColors) { (hex, name) ->
                        val swatchColor = ColorUtils.parseSafeColor(hex)
                        val isSelected = selectedHex.equals(hex, ignoreCase = true)

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    BorderStroke(
                                        if (isSelected) 2.5.dp else 1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    CircleShape
                                )
                                .clickable { selectedHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = name,
                                    tint = ColorUtils.getContrastingTextColor(swatchColor),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Custom Hex Input Option
                OutlinedTextField(
                    value = customHexInput,
                    onValueChange = { input ->
                        customHexInput = input
                        if (input.isNotBlank()) {
                            val parsed = ColorUtils.parseSafeColor(input, Color.Unspecified)
                            if (parsed != Color.Unspecified) {
                                selectedHex = if (input.startsWith("#")) input else "#$input"
                                isCustomInputError = false
                            } else {
                                isCustomInputError = true
                            }
                        } else {
                            isCustomInputError = false
                        }
                    },
                    label = { Text("Custom Hex (e.g., #2A5A4A)") },
                    singleLine = true,
                    isError = isCustomInputError,
                    shape = WadaShapes.SwatchSquircle,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = WadaShapes.FilterPill
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirm(selectedCategory, selectedHex)
                            onDismiss()
                        },
                        shape = WadaShapes.FilterPill,
                        modifier = Modifier.testTag("confirm_add_garment_button")
                    ) {
                        Text("Save Garment")
                    }
                }
            }
        }
    }
}
