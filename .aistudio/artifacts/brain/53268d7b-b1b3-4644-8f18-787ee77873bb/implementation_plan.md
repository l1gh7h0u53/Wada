# Implementation Plan - Floating Pill Menu Bar, Monet Theming Fix & Looks App Icon

## User Requirements
1. **Floating Menu Bar Pill Container**: Give the Floating Menu Bar a pill-shaped container with fully rounded corners (`RoundedCornerShape(32.dp)`), Material 3 tonal surface styling, and subtle border so it floats above content cleanly without being an edge-to-edge block.
2. **Monet Theming Fix**: Disconnect global app UI theming from the chosen Wada color combination.
   - When **Monet** mode is active on Android 12+, use pure Android system dynamic wallpaper color schemes (`dynamicLightColorScheme` / `dynamicDarkColorScheme`) without Wada palette tinting.
   - When **Standard** mode is active, use the app's clean baseline Material 3 palette.
   - Restrict Sanzo Wada palette combinations strictly to outfit recommendations, match assessment, and palette exploration.
3. **Looks App Icon & Themed/Minimalist Icon Support**:
   - Update adaptive launcher icon with the official Material Symbols `looks` double-arc icon.
   - **Standard Icon**: Solid background `#FFDBFE`, foreground icon `#000000`.
   - **Themed / Minimalist Icon (Android 13+)**: Configure `<monochrome>` layer in adaptive icon XML for dynamic theme-tinting on Pixel and modern launchers.
   - Generate valid density PNG assets (`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`) for both standard and round launcher icons.
4. **Match Screen Looks Icon**:
   - Display the new `looks` icon at the top-left of the Match screen header.
   - Styled in `#000000` in Light Mode and `#FFFFFF` in Dark Mode.

---

## Proposed Changes

### 1. Floating Menu Bar Pill Container
- File: `app/src/main/java/com/example/ui/components/ExpressiveBottomBar.kt`
- Wrap the navigation tabs row in a Surface styled with:
  - `shape = RoundedCornerShape(32.dp)`
  - `color = MaterialTheme.colorScheme.surfaceContainer`
  - `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))`
  - `shadow(elevation = 8.dp, shape = RoundedCornerShape(32.dp))`
  - `widthIn(max = 380.dp)` centered with clearance above system navigation insets.

### 2. Monet & Standard Theming Architecture
- File: `app/src/main/java/com/example/ui/theme/Theme.kt`
  - Update `WadaTheme` to decouple `colorScheme` from `wadaCombination`.
  - When `useMonet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`: return `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)`.
  - When `!useMonet`: return `BaselineLightColorScheme` / `BaselineDarkColorScheme`.
  - Ensure Wada color palettes drive outfit canvas calculations rather than the whole app's UI elements.

### 3. App Icon Implementation (`looks` Double-Arc Symbol)
- Resource Files:
  - `app/src/main/res/values/colors.xml`: Add `<color name="ic_launcher_background">#FFDBFE</color>`.
  - `app/src/main/res/drawable/ic_looks.xml`: Vector drawable for the Looks double-arc icon.
  - `app/src/main/res/drawable/ic_launcher_foreground.xml`: Layer-list centering `ic_looks` inside 66dp safe zone in `#000000`.
  - `app/src/main/res/drawable/ic_launcher_monochrome.xml`: Monochrome vector layer for Android 13+ Themed Icons.
  - `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`: Include `<background>`, `<foreground>`, and `<monochrome>`.
  - Generate PNGs for all mipmap density buckets (`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`) for standard and round launcher bitmaps using ImageMagick.

### 4. Looks Icon on Match Screen Header
- File: `app/src/main/java/com/example/ui/screens/outfit/OutfitCanvasScreen.kt`
- Add the `looks` icon to the top-left of the header in Match screen:
  - Color: `#000000` in Light Mode, `#FFFFFF` in Dark Mode.
  - Size: 26.dp with smooth alignment next to or replacing generic icon.

---

## Verification Plan
1. **Compilation Check**: Run `compile_applet` to verify XML drawables, vector paths, and Compose layouts compile without error.
2. **Local Unit Tests**: Run `gradle :app:testDebugUnitTest` to ensure all existing and updated Robolectric navigation and theme tests pass.
3. **Visual & Behavior Verification**:
   - Verify floating bottom bar appears as a pill with fully rounded corners and surfaceContainer background.
   - Verify switching between Monet and Standard in Settings applies wallpaper colors vs clean baseline colors.
   - Verify Match screen displays the Looks icon in top-left with correct light/dark tinting.
   - Verify launcher icon in mipmap/adaptive resources.
