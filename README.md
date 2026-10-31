# 🎨 Wada (v1.6.0)

> A modern Android color-matching & wardrobe canvas powered by Sanzo Wada’s pioneering 1930s color combinations (*Haishoku Sōkan* • 配色総鑑).

[![Version](https://img.shields.io/badge/version-1.6.0-blue.svg)](https://github.com/your-username/wada/releases/tag/v1.6.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF.svg)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material%20Design-M3%20Expressive-7B1FA2.svg)](https://m3.material.io/)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

---

## 📖 Overview

**Wada** bridges historical color theory and modern sartorial styling. Built on top of **Sanzo Wada’s 346 avant-garde color combinations** from 1930s Japan, Wada analyzes your real wardrobe pieces using perceptual **CIELAB $\Delta E$ color science** to build dynamically harmonized outfits.

Whether you're pairing daily outfits or exporting print-ready palettes for external design tools (Figma, Canva, Procreate), Wada delivers a zen, modern Material 3 experience rooted in traditional Japanese color aesthetics.

---

## ✨ Key Features

### 🎨 1930s Sanzo Wada Color Engine
* **Authentic 346 Palette Catalog**: Complete catalog of combinations from *Haishoku Sōkan* (配配色総鑑), spanning 2-color, 3-color, and 4-color palettes (Parts 1–3).
* **Traditional Color Registry**: Every swatch is mapped with its English name, Japanese Romaji, Kanji, and Hex code (e.g., *Uguisucha* • 鶯茶 `#6B6B47`, *Shinshu* • 眞朱 `#AB4B38`, *Asagi* • 浅葱 `#33A3A1`).
* **CIELAB $\Delta E$ Color Science**: Accurate garment color matching using perceptual Delta-E distance calculation to pair physical clothing with Wada’s historical swatches.

### 👔 Match & Outfit Canvas
* **Dynamic Outfit Harmonization**: Automatically suggests top-matching Wada palettes based on selected wardrobe garments.
* **Interactive Stacking Canvas**: Drag, swap, or reassign garments to individual color slots within a selected palette.
* **Score & Harmony Breakdown**: Live calculations of overall outfit harmony percentages and visual $\Delta E$ accuracy metrics.
* **Theme Application**: Apply any Wada palette directly to your app workspace theme in one tap.

### 🧥 Wardrobe & Closet Management
* **Zero-Permission Photo Capture**: Native integration with Android Photo Picker (`ActivityResultContracts.PickVisualMedia`) and Camera—no intrusive storage permissions required.
* **Intelligent Color Extraction**: Automatically extracts dominant hex values using Android Palette, complete with manual color picker overrides.
* **Material 3 Expressive Category Manager**: Custom apparel organization (Tops, Bottoms, Outerwear, Shoes, Accessories) featuring pill-shaped tonal chips and inline state persistence.
* **100% User-Authored Storage**: Clean experience focused purely on your real clothing items.

### 📐 High-Resolution Palette Export
* **Design App Export**: Export any Wada palette as a high-DPI, print-ready PNG image formatted for external tools (Figma, Canva, Procreate, Adobe Illustrator).
* **Native Share Sheet**: Integrated Android Share Sheet (`Intent.ACTION_SEND` + `FileProvider`) sharing formatted bitmaps complete with palette ID, color swatches, hex codes, and traditional names.

### 📚 Educational Color Harmony Principles
* **Historical & Theory Lore**: Educational dialogs on the Outfit Canvas highlighting color harmony principles (Dyad, Complementary, Triadic, Split-Complementary, Nuanced Earth Tones, and Heian Court aesthetic influences).
* **Contextual Harmony Snippets**: Insights into contrast ratios, temperature balance, and Wada's pioneering 1930s avant-garde color theories.

### 🧘 Secret "Simple Palette" Mode (Easter Egg)
* **Secret Access Flow**: Tap the **Looks App Icon** 7 times in the top-left corner to unlock a distraction-free, zen palette browser.
* **Minimalist Swatch Cards**: Browse sequential palettes (1 to 346) with strict boundary control, native back handling (`BackHandler`), and direct high-res PNG export buttons.

---

## 🎨 Material 3 Expressive Theming

* **Authentic Washi & Sumi Theme**: Inspired by traditional Japanese Washi paper (`#F7F4EB`) and Sumi ink (`#1A1715`).
* **Monet Dynamic Color**: Adapts natively to Android Material You wallpaper palettes.
* **Edge-to-Edge System UI**: Seamless handling of `WindowInsets.statusBars.union(WindowInsets.displayCutout)` to prevent clipping with status bars or camera cutouts.
* **Adaptive App Bar**: Dynamic app icon rendering (`#000000` on Light Mode / `#FFFFFF` on Dark Mode).

---

## 🛠️ Tech Stack & Architecture

* **Language**: 100% Kotlin
* **UI Framework**: Jetpack Compose & Material 3 Expressive Components
* **Architecture**: Reactive MVVM with Kotlin `StateFlow` and Coroutines
* **Database**: Local Jetpack Room SQLite persistence with automatic schema migrations
* **Color Math**: Custom CIELAB $\Delta E$ calculation engine & Android Palette API
* **Security**: Clean pipeline with zero hardcoded API keys or external runtime trackers

---

## 🚀 Recent Release Notes (v1.6.0)

* **Build Bump**: Updated to `versionName` **1.6.0** (`versionCode` 6).
* **Version Badge**: Added Material 3 version indicator badge at the footer of the Settings screen.
* **Palette Standardization**: Standardized all app copy and metrics across Match, Closet, and Settings to accurately reference the full **346 Wada Palettes**.
* **Clean Storage**: Streamlined seed data to ensure a pure, user-owned wardrobe environment.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
