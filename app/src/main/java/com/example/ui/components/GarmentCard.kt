package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.ClothingItem
import com.example.ui.theme.WadaShapes
import com.example.util.ColorUtils
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

import androidx.compose.ui.text.style.TextOverflow
import com.example.util.ColorGroupClassifier
import com.example.util.PrimaryColorGroup
import com.example.util.TraditionalWadaColor

/**
 * GarmentCard renders a clothing item with spring-based physics animations.
 *
 * Spring Physics Features:
 * - Tactile press-scale: squishes dynamically on tap/press with M3 bouncy spring damping.
 * - Interactive horizontal swipe feedback with elastic spring resistance.
 *   - Swiping right triggers favorite toggle.
 *   - Swiping left prompts item deletion.
 *   - Rebounds smoothly to center using [Spring.DampingRatioMediumBouncy].
 * - Selected state elevation and highlight border transitions.
 * - Traditional Sanzo Wada color taxonomy pill.
 *
 * @param item The clothing item data entity
 * @param isSelected Whether this garment is currently selected as the styling base anchor
 * @param onClick Callback when the card is clicked
 * @param onToggleFavorite Callback to favorite/unfavorite the item
 * @param onDeleteClick Optional callback to delete the item
 * @param traditionalColor Resolved traditional Sanzo Wada color identity
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GarmentCard(
    item: ClothingItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: (ClothingItem) -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    traditionalColor: TraditionalWadaColor? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val garmentColor = ColorUtils.parseSafeColor(item.hexColor)
    val textColor = ColorUtils.getContrastingTextColor(garmentColor)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Spring physics for press-scale
    val targetScale = when {
        isPressed -> 0.94f
        isSelected -> 1.02f
        else -> 1.0f
    }

    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "garment_spring_scale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        },
        label = "garment_border_color"
    )

    // Swipe physics with elastic drag and spring rebound
    val coroutineScope = rememberCoroutineScope()
    val dragOffsetX = remember { Animatable(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("garment_card_${item.id}"),
        contentAlignment = Alignment.Center
    ) {
        // Background swipe affordance indicator
        val currentOffset = dragOffsetX.value
        if (currentOffset != 0f) {
            val isSwipingRight = currentOffset > 0f
            val swipeProgress = (kotlin.math.abs(currentOffset) / 160f).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(WadaShapes.OutfitPieceShape)
                    .background(
                        if (isSwipingRight) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f * swipeProgress)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f * swipeProgress)
                        }
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = if (isSwipingRight) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                if (isSwipingRight) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Outlined.FavoriteBorder else Icons.Filled.Favorite,
                        contentDescription = "Swipe to Favorite",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .size(24.dp)
                            .scale(0.8f + 0.4f * swipeProgress)
                    )
                } else if (onDeleteClick != null) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Swipe to Delete",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .size(24.dp)
                            .scale(0.8f + 0.4f * swipeProgress)
                    )
                }
            }
        }

        // Foreground Card with spring gesture and press state
        Card(
            modifier = Modifier
                .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
                .scale(scale)
                .fillMaxWidth()
                .pointerInput(item.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                val offset = dragOffsetX.value
                                if (offset > 140f) {
                                    onToggleFavorite(item)
                                } else if (offset < -140f && onDeleteClick != null) {
                                    onDeleteClick()
                                }
                                dragOffsetX.animateTo(
                                    0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                dragOffsetX.animateTo(
                                    0f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                )
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                // Add elastic drag resistance
                                val newOffset = dragOffsetX.value + dragAmount * 0.65f
                                dragOffsetX.snapTo(newOffset.coerceIn(-200f, 200f))
                            }
                        }
                    )
                }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true),
                    onClick = onClick,
                    onLongClick = if (onLongClick != null) {
                        {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick()
                        }
                    } else null,
                    onLongClickLabel = if (onLongClick != null) "Edit color and rotation" else null
                ),
            shape = WadaShapes.OutfitPieceShape,
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                }
            ),
            border = BorderStroke(if (isSelected) 2.5.dp else 1.dp, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else 1.5.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Garment Image / Color Swatch Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(WadaShapes.SwatchSquircle)
                        .background(garmentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    // Physical photo or stylized vector silhouette
                    val imageFile = File(item.filePath)
                    if (imageFile.exists() && imageFile.length() > 0) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageFile)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Garment ${item.category}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(garmentColor)
                                    .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.5f)), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Checkroom,
                                    contentDescription = null,
                                    tint = textColor,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }

                    // Favorite button in top-right corner
                    IconButton(
                        onClick = { onToggleFavorite(item) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                            .testTag("favorite_button_${item.id}")
                    ) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (item.isFavorite) "Favorited" else "Favorite",
                            tint = if (item.isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Delete button in top-left corner if deletion provided
                    if (onDeleteClick != null) {
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                                .testTag("delete_button_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete Garment",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Weather Fit Badge (Warm / Light) in bottom-start corner if tagged
                    if (item.thermalType.equals("WARM", ignoreCase = true) ||
                        item.thermalType.equals("LIGHT", ignoreCase = true)
                    ) {
                        val isWarm = item.thermalType.equals("WARM", ignoreCase = true)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            border = BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .testTag("garment_thermal_badge_${item.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = if (isWarm) Icons.Filled.Whatshot else Icons.Outlined.WbSunny,
                                    contentDescription = null,
                                    tint = if (isWarm) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (isWarm) "Warm" else "Light",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Info Row: Category pill and Hex Swatch pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category Chip
                    Surface(
                        shape = WadaShapes.FilterPill,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = item.category.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    // Color Swatch Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(garmentColor)
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = item.hexColor.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Traditional Wada Color Name + Auto-Tagged Primary Color Group Row
                val resolvedGroupName = item.colorGroup.ifBlank {
                    ColorGroupClassifier.classifyHex(item.hexColor).label
                }
                val resolvedGroupSwatch = PrimaryColorGroup.fromLabel(resolvedGroupName)?.swatchHex ?: item.hexColor

                Spacer(modifier = Modifier.height(7.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    traditionalColor?.let { trad ->
                        Surface(
                            shape = WadaShapes.FilterPill,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(ColorUtils.parseSafeColor(trad.hex))
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "${trad.wadaName} • ${trad.japaneseKanji}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Primary Color Group Auto-Tag Pill
                    Surface(
                        shape = WadaShapes.FilterPill,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.testTag("garment_color_group_tag_${item.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(ColorUtils.parseSafeColor(resolvedGroupSwatch))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = resolvedGroupName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
