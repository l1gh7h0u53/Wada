package com.example.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.screens.dialogs.CategoriesDialog
import com.example.ui.theme.WadaShapes
import com.example.ui.theme.buildWadaText
import com.example.viewmodel.WardrobeViewModel

/**
 * SettingsScreen provides configuration for themes, Sanzo Wada historical context,
 * category customization, and sample wardrobe initialization.
 */
@Composable
fun SettingsScreen(
    viewModel: WardrobeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val themeSetting by viewModel.themeSetting.collectAsState()
    val categories by viewModel.categories.collectAsState()

    var showCategoriesDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .padding(bottom = 100.dp)
            .testTag("settings_screen")
    ) {
        // Title Header
        Text(
            text = buildWadaText("Settings & History"),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Personalize aesthetic preferences and explore Sanzo Wada's heritage",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 1. Theme Configuration Section
        Text(
            text = "APPEARANCE & THEME",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = WadaShapes.OutfitPieceShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "DISPLAY MODE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = themeSetting == "SYSTEM",
                        onClick = { viewModel.setThemeSetting("SYSTEM") },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        icon = { Icon(Icons.Filled.SettingsBrightness, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("theme_system_btn")
                    ) {
                        Text("System")
                    }
                    SegmentedButton(
                        selected = themeSetting == "LIGHT",
                        onClick = { viewModel.setThemeSetting("LIGHT") },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        icon = { Icon(Icons.Filled.LightMode, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("theme_light_btn")
                    ) {
                        Text("Light")
                    }
                    SegmentedButton(
                        selected = themeSetting == "DARK",
                        onClick = { viewModel.setThemeSetting("DARK") },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        icon = { Icon(Icons.Filled.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("theme_dark_btn")
                    ) {
                        Text("Dark")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "COLOR ENGINE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Connected button group for Monet and Standard
                val monetMode by viewModel.monetMode.collectAsState()
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = monetMode == "MONET",
                        onClick = { viewModel.setMonetMode("MONET") },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Filled.Palette, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("theme_monet_btn")
                    ) {
                        Text("Monet")
                    }
                    SegmentedButton(
                        selected = monetMode == "STANDARD",
                        onClick = { viewModel.setMonetMode("STANDARD") },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("theme_standard_btn")
                    ) {
                        Text("Standard")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (monetMode == "MONET")
                        "Monet adapts dynamic Android system tones harmonized with Sanzo Wada palettes."
                        else "Standard applies the authentic 1930s Sanzo Wada Washi & Sumi editorial palette.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2. Category Management
        Text(
            text = "WARDROBE ORGANIZATION",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showCategoriesDialog = true },
            shape = WadaShapes.OutfitPieceShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Apparel Categories",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${categories.size} categories: ${categories.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Sanzo Wada Literature & Philosophy
        Text(
            text = "ABOUT SANZO WADA (1883–1967)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = WadaShapes.AsymmetricHeroCard,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "A Dictionary of Color Combinations",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Sanzo Wada (和田三造) was a pioneer of Japanese color research, a renowned Western-style painter, and an Academy Award winner for Best Costume Design (Gate of Hell, 1954). In the early 1930s, he published Haishoku Sōkan (配配色総鑑), establishing 346 color combinations that merged traditional Japanese aesthetic sensibilities with modern avant-garde Western palettes.\n\nWada brings this seminal work to your personal wardrobe, using CIELAB Delta-E color science to orchestrate balanced, expressive outfits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // App Version Footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = WadaShapes.FilterPill,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "v1.6.0",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Wada • Haishoku Sōkan Wardrobe Matcher",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }

    if (showCategoriesDialog) {
        CategoriesDialog(
            categories = categories,
            onDismiss = { showCategoriesDialog = false },
            onUpdateCategories = { viewModel.updateCategories(it) }
        )
    }
}
