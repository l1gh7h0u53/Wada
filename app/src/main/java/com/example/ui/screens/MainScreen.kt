package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ui.components.ExpressiveBottomBar
import com.example.ui.screens.digitizer.DigitizerStudioScreen
import com.example.ui.screens.outfit.OutfitCanvasScreen
import com.example.ui.screens.palette.HiddenSimplePaletteScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.wardrobe.WardrobeClosetScreen
import com.example.ui.theme.WadaTheme
import com.example.viewmodel.WardrobeViewModel

/**
 * MainScreen is the primary top-level coordinator for Wada.
 *
 * It hosts the canonical Material 3 Scaffold, provides animated screen transitions
 * across the 4 primary tabs, manages the dynamic Sanzo Wada Monet theming,
 * and anchors the floating [ExpressiveBottomBar] above system navigation bar insets.
 *
 * Tab Indices:
 * - 0: Outfit Canvas & Styling Workspace ([OutfitCanvasScreen])
 * - 1: Digital Wardrobe Inventory ([WardrobeClosetScreen])
 * - 2: Color Digitizer & Camera Lab ([DigitizerStudioScreen])
 * - 3: Settings & Sanzo Wada Heritage ([SettingsScreen])
 */
@Composable
fun MainScreen(viewModel: WardrobeViewModel) {
    val themeSetting by viewModel.themeSetting.collectAsState()
    val monetMode by viewModel.monetMode.collectAsState()
    val selectedCombination by viewModel.selectedCombination.collectAsState()
    val wadaCombinations by viewModel.wadaCombinations.collectAsState()
    val wadaColors by viewModel.wadaColors.collectAsState()

    // Screen tab state: 0 -> Outfit, 1 -> Closet, 2 -> Digitize, 3 -> Settings
    var activeTab by remember { mutableIntStateOf(0) }

    // Easter Egg: Hidden Simple Palette Mode state (unlocked via 7 taps on Looks icon)
    var showHiddenSimplePaletteMode by remember { mutableStateOf(false) }

    // Resolve system vs manual theme preference
    val isSystemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeSetting) {
        "LIGHT" -> false
        "DARK" -> true
        else -> isSystemDark
    }

    // Android back button navigation: return to normal mode if hidden mode is open, else return to tab 0
    BackHandler(enabled = showHiddenSimplePaletteMode) {
        showHiddenSimplePaletteMode = false
    }

    BackHandler(enabled = !showHiddenSimplePaletteMode && activeTab != 0) {
        activeTab = 0
    }

    WadaTheme(
        wadaCombination = selectedCombination,
        darkTheme = useDarkTheme,
        useMonet = monetMode == "MONET"
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Animated crossfade between the 4 modular screens, padded below system status bar
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                },
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                    .padding(top = 24.dp),
                label = "screen_crossfade"
            ) { tab ->
                when (tab) {
                    0 -> OutfitCanvasScreen(
                        viewModel = viewModel,
                        onNavigateToCloset = { activeTab = 1 },
                        onNavigateToDigitizer = { activeTab = 2 },
                        onOpenHiddenPaletteMode = { showHiddenSimplePaletteMode = true }
                    )
                    1 -> WardrobeClosetScreen(
                        viewModel = viewModel,
                        onNavigateToDigitizer = { activeTab = 2 }
                    )
                    2 -> DigitizerStudioScreen(
                        viewModel = viewModel,
                        onDigitizeSuccess = { activeTab = 0 }
                    )
                    3 -> SettingsScreen(
                        viewModel = viewModel
                    )
                }
            }

            // Truly floating bottom navigation bar: floats naturally above app content with zero white container
            ExpressiveBottomBar(
                selectedTab = activeTab,
                onTabSelected = { activeTab = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // Hidden Simple Palette Mode: Zen full-screen mode showing ONLY palettes
            AnimatedVisibility(
                visible = showHiddenSimplePaletteMode,
                enter = fadeIn(tween(260)) + slideInVertically(tween(300)) { it / 6 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(240)) { it / 6 }
            ) {
                HiddenSimplePaletteScreen(
                    combinations = wadaCombinations,
                    wadaColors = wadaColors,
                    selectedCombination = selectedCombination,
                    onSelectPalette = { combo ->
                        viewModel.selectCombination(combo)
                    },
                    onExit = { showHiddenSimplePaletteMode = false }
                )
            }
        }
    }
}
