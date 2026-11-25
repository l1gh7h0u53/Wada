package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ClothingRepository
import com.example.ui.screens.MainScreen
import com.example.viewmodel.WardrobeViewModel
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var viewModel: WardrobeViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = ClothingRepository(db.clothingDao(), context)
        viewModel = WardrobeViewModel(repository)
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) {
            db.close()
        }
    }

    @Test
    fun testMainScreenNavigation() {
        composeTestRule.setContent {
            MainScreen(viewModel = viewModel)
        }
        composeTestRule.waitForIdle()

        // Click nav_closet to navigate to the Digital Closet panel
        composeTestRule.onNode(androidx.compose.ui.test.hasTestTag("nav_closet")).performClick()
        composeTestRule.waitForIdle()

        // Click digitize_fab to toggle the expandable Digitize menu
        composeTestRule.onNode(androidx.compose.ui.test.hasTestTag("digitize_fab")).performClick()
        composeTestRule.waitForIdle()

        // Click nav_settings to navigate to the Settings panel
        composeTestRule.onNode(androidx.compose.ui.test.hasTestTag("nav_settings")).performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testMainScreenRendersSuccessfully() {
        composeTestRule.setContent {
            MainScreen(viewModel = viewModel)
        }
        composeTestRule.waitForIdle()

        // Verify Looks brand icon on top-left of Match screen
        composeTestRule.onNodeWithTag("match_screen_looks_icon").assertExists()
    }

    @Test
    fun testClosetWadaColorSearchBar() {
        composeTestRule.setContent {
            com.example.ui.screens.wardrobe.WardrobeClosetScreen(
                viewModel = viewModel,
                onNavigateToDigitizer = {}
            )
        }
        composeTestRule.waitForIdle()

        // Verify Style Tags above search bar are displayed and clickable
        composeTestRule.onNodeWithTag("style_tag_Casual").assertExists()
        composeTestRule.onNodeWithTag("style_tag_Casual").performClick()
        composeTestRule.waitForIdle()

        // Verify search input is present and functional
        composeTestRule.onNodeWithTag("closet_color_search_input").assertExists()
        composeTestRule.onNodeWithTag("closet_color_search_input").performClick()
        composeTestRule.onNodeWithTag("closet_color_search_input").performTextInput("Kon")
        composeTestRule.waitForIdle()

        // Verify Color Lore toggle button exists and can be clicked
        composeTestRule.onNodeWithTag("color_lore_toggle_btn").assertExists()
        composeTestRule.onNodeWithTag("color_lore_toggle_btn").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testHiddenSimplePaletteModeUnlockAndSwipeBlock() {
        composeTestRule.setContent {
            MainScreen(viewModel = viewModel)
        }
        composeTestRule.waitForIdle()

        // 1. App Icon in top-left exists
        val looksIcon = composeTestRule.onNodeWithTag("match_screen_looks_icon")
        looksIcon.assertExists()

        // 2. Tap 6 times: hidden mode should NOT open yet
        repeat(6) {
            looksIcon.performClick()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hidden_simple_palette_screen").assertDoesNotExist()

        // 3. Tap the 7th time: unlocks hidden simple palette mode
        looksIcon.performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hidden_simple_palette_screen").assertExists()

        // 4. Verify initial state: starts at Palette 1
        composeTestRule.onNodeWithTag("hidden_palette_counter").assertExists()

        // 5. Verify swipe-left / previous block on Palette 1 (cannot wrap to Palette 346)
        composeTestRule.onNodeWithTag("hidden_palette_prev_button").assertIsNotEnabled()

        // 6. Next button works to advance palette
        composeTestRule.onNodeWithTag("hidden_palette_next_button").assertIsEnabled()
        composeTestRule.onNodeWithTag("hidden_palette_next_button").performClick()
        composeTestRule.waitForIdle()

        // Previous button is now enabled on Palette 2
        composeTestRule.onNodeWithTag("hidden_palette_prev_button").assertIsEnabled()

        // 7. Exit button returns smoothly to main app
        composeTestRule.onNodeWithTag("exit_hidden_mode_button").assertExists()
        composeTestRule.onNodeWithTag("exit_hidden_mode_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hidden_simple_palette_screen").assertDoesNotExist()
    }

    @Test
    fun testPaletteExportAndHarmonyLore() {
        val testCombo = com.example.data.WadaCombination(
            id = 1,
            name = "Classic Chic",
            colors = listOf("#1D2A44", "#E5BFB1", "#BA7A5E"),
            part = 1,
            description = "Deep Navy, Soft Peach, and Terracotta."
        )

        // 1. Verify Harmony Lore snippet generation
        val lore = com.example.util.PaletteExportHelper.getHarmonyLoreForPalette(testCombo)
        assertEquals("Palette #1: Classic Chic", lore.title)
        assertEquals("Triadic Resonance (3-Color Contrast & Anchor)", lore.harmonyType)
        assert(lore.principle.isNotBlank())
        assert(lore.description.isNotBlank())
        assert(lore.applicationAdvice.isNotBlank())

        // 2. Verify High-Res PNG Bitmap generation
        val bitmap = com.example.util.PaletteExportHelper.renderHighResPaletteBitmap(testCombo, emptyList())
        assertEquals(1200, bitmap.width)
        assertEquals(1600, bitmap.height)
    }

    @Test
    fun testSeedButtonsRemoved() {
        composeTestRule.setContent {
            com.example.ui.screens.settings.SettingsScreen(viewModel = viewModel)
        }
        composeTestRule.waitForIdle()

        // Verify Seed Sample Garments button is completely removed from Settings
        composeTestRule.onNodeWithTag("seed_sample_garments_btn").assertDoesNotExist()
    }

    @Test
    fun testCategoriesDialogDone() {
        var dismissed = false
        var updatedList: List<String>? = null
        val initialCategories = listOf("Tops", "Bottoms", "Shoes")

        composeTestRule.setContent {
            com.example.ui.screens.dialogs.CategoriesDialog(
                categories = initialCategories,
                onDismiss = { dismissed = true },
                onUpdateCategories = { updatedList = it }
            )
        }
        composeTestRule.waitForIdle()

        // 1. Verify old header close X button is not present
        composeTestRule.onNodeWithContentDescription("Close").assertDoesNotExist()

        // 2. Add a new category
        composeTestRule.onNodeWithTag("new_category_input").performTextInput("Accessories")
        composeTestRule.onNodeWithTag("add_category_button").performClick()
        composeTestRule.waitForIdle()

        // 3. Verify pill-shaped Cancel button exists
        composeTestRule.onNodeWithTag("cancel_categories_button").assertExists()

        // 4. Click Done button and verify categories are persisted
        composeTestRule.onNodeWithTag("done_categories_button").assertExists()
        composeTestRule.onNodeWithTag("done_categories_button").performClick()
        composeTestRule.waitForIdle()

        assert(dismissed)
        assertEquals(listOf("Tops", "Bottoms", "Shoes", "Accessories"), updatedList)
    }

    @Test
    fun testCategoriesDialogCancel() {
        var dismissed = false
        var updatedList: List<String>? = null
        val initialCategories = listOf("Tops", "Bottoms", "Shoes")

        composeTestRule.setContent {
            com.example.ui.screens.dialogs.CategoriesDialog(
                categories = initialCategories,
                onDismiss = { dismissed = true },
                onUpdateCategories = { updatedList = it }
            )
        }
        composeTestRule.waitForIdle()

        // Click Cancel button: should dismiss without updating categories
        composeTestRule.onNodeWithTag("cancel_categories_button").assertExists()
        composeTestRule.onNodeWithTag("cancel_categories_button").performClick()
        composeTestRule.waitForIdle()

        assert(dismissed)
        assert(updatedList == null)
    }

    @Test
    fun readStringFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Wada", appName)
    }

    @Test
    fun testDailyInspirationDeterministicPerDay() {
        val sampleList = (1..346).map { id ->
            com.example.data.WadaCombination(
                id = id,
                name = "Palette $id",
                colors = listOf("#1D2A44", "#E5BFB1", "#BA7A5E"),
                part = 1,
                description = "Sample $id"
            )
        }
        val dayOne = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.NOVEMBER, 25) }
        val dayOneAgain = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.NOVEMBER, 25) }
        val dayTwo = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.NOVEMBER, 26) }

        // Empty or 1-color closet must not produce daily inspiration
        assertEquals(null, viewModel.computeDailyInspiration(emptyList(), sampleList, dayOne))
        val oneGarment = listOf(
            com.example.data.ClothingItem(id = 1, filePath = "", category = "Tops", hexColor = "#1D2A44")
        )
        assertEquals(null, viewModel.computeDailyInspiration(oneGarment, sampleList, dayOne))

        // 2+ distinct colors logged in closet unlocks daily inspiration showing only logged colors
        val twoGarments = listOf(
            com.example.data.ClothingItem(id = 1, filePath = "", category = "Tops", hexColor = "#1D2A44"),
            com.example.data.ClothingItem(id = 2, filePath = "", category = "Bottoms", hexColor = "#E5BFB1")
        )
        val pick1 = viewModel.computeDailyInspiration(twoGarments, sampleList, dayOne)
        val pick1Repeat = viewModel.computeDailyInspiration(twoGarments, sampleList, dayOneAgain)
        val pick2 = viewModel.computeDailyInspiration(twoGarments, sampleList, dayTwo)

        assert(pick1 != null && pick2 != null)
        assertEquals(pick1?.combination?.id, pick1Repeat?.combination?.id)
        assertEquals(2, pick1?.displayedColors?.size)
        assert(pick1?.displayedColors?.containsAll(listOf("#1D2A44", "#E5BFB1")) == true)
        assert(pick1?.combination?.id != pick2?.combination?.id)
    }

    @Test
    fun testMatchScreenStreamlinedWithDailyInspiration() {
        composeTestRule.setContent {
            MainScreen(viewModel = viewModel)
        }
        composeTestRule.waitForIdle()

        // Top Shuffle and History buttons are hidden on Match
        composeTestRule.onNodeWithTag("palette_history_header_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("shuffle_palette_button").assertDoesNotExist()

        // Historic combinations filter chips and duplicate empty wardrobe card are removed from Match
        composeTestRule.onNodeWithTag("part_filter_chip_0").assertDoesNotExist()
        composeTestRule.onNode(androidx.compose.ui.test.hasText("Your Wardrobe is Empty")).assertDoesNotExist()

        // Top prompt to digitize first garment exists immediately
        composeTestRule.onNodeWithTag("minimalist_gallery_empty_state").assertExists()

        // With nothing logged in the closet, Daily Style Inspiration does not appear
        composeTestRule.onNodeWithTag("daily_style_inspiration_card").assertDoesNotExist()
    }
}

