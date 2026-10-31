package com.example.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Shapes for Wada.
 *
 * M3 Expressive embraces intentional asymmetry, organic rounded contours,
 * squircle containers, and tactile pill shapes inspired by Japanese ceramics
 * and modern editorial design.
 */

val ExpressiveShapes = Shapes(
    // Extra Small: Badges, tiny tags, inner indicators
    extraSmall = RoundedCornerShape(8.dp),

    // Small: Filter chips, buttons, small swatches
    small = RoundedCornerShape(12.dp),

    // Medium: Interactive clothing cards, dialog surfaces
    medium = RoundedCornerShape(20.dp),

    // Large: Featured outfit hero cards, bottom sheet containers
    large = RoundedCornerShape(28.dp),

    // Extra Large: Modal dialogs, full-width hero banners
    extraLarge = RoundedCornerShape(32.dp)
)

// Dedicated Expressive Specialty Shapes
object WadaShapes {
    // Asymmetric editorial hero card (curved top-left and bottom-right corners)
    val AsymmetricHeroCard = RoundedCornerShape(
        topStart = 32.dp,
        topEnd = 14.dp,
        bottomEnd = 32.dp,
        bottomStart = 14.dp
    )

    // Swatch container with soft organic corners
    val SwatchSquircle = RoundedCornerShape(18.dp)

    // Floating navigation pill
    val NavPill = CircleShape

    // Outfit piece container
    val OutfitPieceShape = RoundedCornerShape(24.dp)

    // Category filter chip
    val FilterPill = RoundedCornerShape(16.dp)
}
