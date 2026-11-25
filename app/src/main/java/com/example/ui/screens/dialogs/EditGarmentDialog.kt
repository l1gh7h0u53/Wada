package com.example.ui.screens.dialogs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ClothingItem
import com.example.data.WadaColor
import com.example.ui.theme.WadaShapes
import com.example.util.ColorUtils
import com.example.util.WadaColorRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * EditGarmentDialog lets the user edit an item's exact color (via photo tap-to-sample
 * eyedropper, HSV gradient sliders, or exact Hex input) and rotate its picture in 90° steps.
 */
@Composable
fun EditGarmentDialog(
    item: ClothingItem,
    wadaColors: List<WadaColor>,
    onDismiss: () -> Unit,
    onSave: (newHexColor: String, rotationDegrees: Int) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Initial HSV state parsed from existing garment hex color
    val initialColor = remember(item.id, item.hexColor) {
        ColorUtils.parseSafeColor(item.hexColor)
    }
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        val argb = android.graphics.Color.rgb(
            (initialColor.red * 255f).roundToInt().coerceIn(0, 255),
            (initialColor.green * 255f).roundToInt().coerceIn(0, 255),
            (initialColor.blue * 255f).roundToInt().coerceIn(0, 255)
        )
        android.graphics.Color.colorToHSV(argb, hsv)
        hsv
    }

    var hue by remember(item.id) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(item.id) { mutableFloatStateOf(initialHsv[1]) }
    var value by remember(item.id) { mutableFloatStateOf(initialHsv[2]) }
    var hexInput by remember(item.id) { mutableStateOf(ColorUtils.toHexString(initialColor)) }
    var isHexError by remember(item.id) { mutableStateOf(false) }

    var rotationDegrees by remember(item.id) { mutableIntStateOf(0) }
    var isSaving by remember(item.id) { mutableStateOf(false) }

    // Decoded garment picture (null if the garment has no photo file on disk)
    var originalBitmap by remember(item.id, item.filePath) { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember(item.id, item.filePath) { mutableStateOf<Bitmap?>(null) }
    var imageBoxSize by remember { mutableStateOf(IntSize.Zero) }
    var sampleNormalizedPoint by remember(item.id) { mutableStateOf<Offset?>(null) }

    // Spring animation delta applied briefly during 90° rotation transitions
    val rotationSpringDelta = remember { Animatable(0f) }

    // Load and sub-sample the photo asynchronously to prevent main-thread hitching or OOM
    LaunchedEffect(item.id, item.filePath) {
        originalBitmap = withContext(Dispatchers.IO) {
            loadSubsampledBitmap(item.filePath, maxDimension = 900)
        }
    }

    // Generate cleanly rotated preview bitmap whenever rotationDegrees changes
    LaunchedEffect(originalBitmap, rotationDegrees) {
        val base = originalBitmap
        previewBitmap = if (base == null) {
            null
        } else if (rotationDegrees % 360 == 0) {
            base
        } else {
            withContext(Dispatchers.Default) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(base, 0, 0, base.width, base.height, matrix, true)
            }
        }
    }

    val currentColor = Color.hsv(
        hue = hue.coerceIn(0f, 360f),
        saturation = saturation.coerceIn(0f, 1f),
        value = value.coerceIn(0f, 1f)
    )
    val currentHex = ColorUtils.toHexString(currentColor)
    val closestWadaColor = remember(currentHex, wadaColors) {
        if (wadaColors.isEmpty()) null
        else WadaColorRegistry.findClosestTraditionalColor(currentHex, wadaColors)
    }

    // Helper to update HSV & Hex from an ARGB pixel integer
    fun applyPickedPixel(pixel: Int) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(pixel, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        hexInput = ColorUtils.toHexString(Color.hsv(hue, saturation, value))
        isHexError = false
    }

    // Helper to sample a pixel under a tap/drag point inside the ContentScale.Fit image box
    fun sampleColorAt(offset: Offset) {
        val bmp = previewBitmap ?: return
        if (imageBoxSize.width <= 0 || imageBoxSize.height <= 0) return

        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()
        val boxW = imageBoxSize.width.toFloat()
        val boxH = imageBoxSize.height.toFloat()
        val scale = minOf(boxW / bmpW, boxH / bmpH)
        val renderedW = bmpW * scale
        val renderedH = bmpH * scale
        val left = (boxW - renderedW) / 2f
        val top = (boxH - renderedH) / 2f

        val normX = ((offset.x - left) / renderedW).coerceIn(0f, 1f)
        val normY = ((offset.y - top) / renderedH).coerceIn(0f, 1f)
        val px = (normX * bmpW).toInt().coerceIn(0, bmp.width - 1)
        val py = (normY * bmpH).toInt().coerceIn(0, bmp.height - 1)

        val pixel = bmp.getPixel(px, py)
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha > 16) {
            sampleNormalizedPoint = Offset(normX, normY)
            applyPickedPixel(pixel)
        }
    }

    // Helper to rotate preview by -90 or +90 degrees with M3 Expressive spring physics
    fun rotateBy(deltaDegrees: Int) {
        rotationDegrees = (rotationDegrees + deltaDegrees + 360) % 360
        sampleNormalizedPoint = sampleNormalizedPoint?.let { pt ->
            if (deltaDegrees > 0) Offset(1f - pt.y, pt.x) else Offset(pt.y, 1f - pt.x)
        }
        coroutineScope.launch {
            rotationSpringDelta.snapTo(-deltaDegrees.toFloat())
            rotationSpringDelta.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = 520.dp)
                .testTag("edit_garment_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header: Title, Category Badge, and Circular Close Button
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
                            text = "Edit Garment",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = item.category.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { if (!isSaving) onDismiss() },
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                            .testTag("edit_garment_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Photo Preview & Picture Rotation Section (only when item has a picture file)
                val hasImageFile = remember(item.id, item.filePath) {
                    item.filePath.isNotBlank() && File(item.filePath).let { it.exists() && it.length() > 0L }
                }
                val activePreview = previewBitmap
                if (hasImageFile) {
                    if (activePreview == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .clip(WadaShapes.SwatchSquircle)
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    } else {
                        val imageBitmap = remember(activePreview) { activePreview.asImageBitmap() }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(WadaShapes.SwatchSquircle)
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                WadaShapes.SwatchSquircle
                            )
                            .onSizeChanged { imageBoxSize = it }
                            .pointerInput(activePreview, imageBoxSize) {
                                detectTapGestures { offset ->
                                    focusManager.clearFocus()
                                    sampleColorAt(offset)
                                }
                            }
                            .pointerInput(activePreview, imageBoxSize) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        focusManager.clearFocus()
                                        sampleColorAt(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        sampleColorAt(change.position)
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "Garment picture preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationZ = rotationSpringDelta.value
                                }
                        )

                        // Eyedropper Reticle on tapped garment spot
                        val norm = sampleNormalizedPoint
                        if (norm != null && imageBoxSize.width > 0 && imageBoxSize.height > 0) {
                            val bmpW = activePreview.width.toFloat()
                            val bmpH = activePreview.height.toFloat()
                            val boxW = imageBoxSize.width.toFloat()
                            val boxH = imageBoxSize.height.toFloat()
                            val scale = minOf(boxW / bmpW, boxH / bmpH)
                            val renderedW = bmpW * scale
                            val renderedH = bmpH * scale
                            val left = (boxW - renderedW) / 2f
                            val top = (boxH - renderedH) / 2f

                            val reticleRadiusPx = with(density) { 14.dp.toPx() }
                            val xPx = (left + norm.x * renderedW - reticleRadiusPx).roundToInt()
                            val yPx = (top + norm.y * renderedH - reticleRadiusPx).roundToInt()

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset { IntOffset(xPx, yPx) }
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(currentColor)
                                    .border(BorderStroke(2.5.dp, Color.White), CircleShape)
                                    .border(BorderStroke(1.dp, Color.Black.copy(alpha = 0.45f)), CircleShape)
                            )
                        }

                        // Subtle Eyedropper Pill Hint
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Colorize,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Tap or drag photo to sample color",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Picture Rotation Controls Row (all fully rounded pill buttons)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { rotateBy(-90) },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("edit_garment_rotate_left_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.RotateLeft,
                                contentDescription = "Rotate Left 90 degrees",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("90° Left", style = MaterialTheme.typography.labelMedium)
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.testTag("edit_garment_angle_badge")
                        ) {
                            Text(
                                text = "${rotationDegrees}°",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (rotationDegrees == 0) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        FilledTonalButton(
                            onClick = { rotateBy(90) },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .testTag("edit_garment_rotate_right_btn")
                        ) {
                            Text("90° Right", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.RotateRight,
                                contentDescription = "Rotate Right 90 degrees",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Exact Color Section
                Text(
                    text = "EXACT COLOR",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Original vs New Color Comparison Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(CircleShape)
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape)
                ) {
                    val origTextCol = ColorUtils.getContrastingTextColor(initialColor)
                    val newTextCol = ColorUtils.getContrastingTextColor(currentColor)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(initialColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "WAS ${item.hexColor.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = origTextCol.copy(alpha = 0.85f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1.35f)
                            .fillMaxHeight()
                            .background(currentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentHex,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = newTextCol
                        )
                    }
                }

                // Live Closest Traditional Wada Pigment Badge
                closestWadaColor?.let { trad ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(ColorUtils.parseSafeColor(trad.hex))
                                    .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.6f)), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Closest Wada: ${trad.wadaName} • ${trad.japaneseKanji}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Hue Gradient Slider (0° - 360°)
                HsvGradientSlider(
                    label = "HUE",
                    valueText = "${hue.roundToInt()}°",
                    fraction = (hue / 360f).coerceIn(0f, 1f),
                    gradientColors = listOf(
                        Color.Red,
                        Color.Yellow,
                        Color.Green,
                        Color.Cyan,
                        Color.Blue,
                        Color.Magenta,
                        Color.Red
                    ),
                    thumbColor = Color.hsv(hue.coerceIn(0f, 360f), 1f, 1f),
                    onFractionChange = { f ->
                        hue = (f * 360f).coerceIn(0f, 360f)
                        hexInput = ColorUtils.toHexString(Color.hsv(hue, saturation, value))
                        isHexError = false
                    }
                )

                // Saturation Gradient Slider (0% - 100%)
                HsvGradientSlider(
                    label = "SATURATION",
                    valueText = "${(saturation * 100f).roundToInt()}%",
                    fraction = saturation.coerceIn(0f, 1f),
                    gradientColors = listOf(
                        Color.hsv(hue.coerceIn(0f, 360f), 0f, value.coerceIn(0f, 1f)),
                        Color.hsv(hue.coerceIn(0f, 360f), 1f, value.coerceIn(0f, 1f))
                    ),
                    thumbColor = currentColor,
                    onFractionChange = { f ->
                        saturation = f.coerceIn(0f, 1f)
                        hexInput = ColorUtils.toHexString(Color.hsv(hue, saturation, value))
                        isHexError = false
                    }
                )

                // Brightness Gradient Slider (0% - 100%)
                HsvGradientSlider(
                    label = "BRIGHTNESS",
                    valueText = "${(value * 100f).roundToInt()}%",
                    fraction = value.coerceIn(0f, 1f),
                    gradientColors = listOf(
                        Color.Black,
                        Color.hsv(hue.coerceIn(0f, 360f), saturation.coerceIn(0f, 1f), 1f)
                    ),
                    thumbColor = currentColor,
                    onFractionChange = { f ->
                        value = f.coerceIn(0f, 1f)
                        hexInput = ColorUtils.toHexString(Color.hsv(hue, saturation, value))
                        isHexError = false
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Exact Hex Code Text Field
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { input ->
                        hexInput = input.uppercase()
                        val cleaned = input.trim().removePrefix("#")
                        if (cleaned.length == 3 || cleaned.length == 6) {
                            val parsed = ColorUtils.parseSafeColor(input, Color.Unspecified)
                            if (parsed != Color.Unspecified) {
                                val hsv = FloatArray(3)
                                val argb = android.graphics.Color.rgb(
                                    (parsed.red * 255f).roundToInt().coerceIn(0, 255),
                                    (parsed.green * 255f).roundToInt().coerceIn(0, 255),
                                    (parsed.blue * 255f).roundToInt().coerceIn(0, 255)
                                )
                                android.graphics.Color.colorToHSV(argb, hsv)
                                hue = hsv[0]
                                saturation = hsv[1]
                                value = hsv[2]
                                isHexError = false
                            } else {
                                isHexError = true
                            }
                        } else {
                            isHexError = true
                        }
                    },
                    label = { Text("Exact Hex Code (e.g. #4E6D53)") },
                    singleLine = true,
                    isError = isHexError,
                    supportingText = if (isHexError) {
                        { Text("Enter a valid 3 or 6 digit hex color") }
                    } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_garment_hex_input")
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Footer Action Buttons (fully rounded pill corners)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { if (!isSaving) onDismiss() },
                        shape = CircleShape,
                        modifier = Modifier.testTag("edit_garment_cancel_btn")
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            if (!isSaving && !isHexError) {
                                isSaving = true
                                onSave(currentHex, rotationDegrees)
                            }
                        },
                        enabled = !isSaving && !isHexError,
                        shape = CircleShape,
                        modifier = Modifier.testTag("edit_garment_save_btn")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving...")
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Changes")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive HSV gradient slider with a 48dp accessible touch target and smooth drag/tap response.
 */
@Composable
private fun HsvGradientSlider(
    label: String,
    valueText: String,
    fraction: Float,
    gradientColors: List<Color>,
    thumbColor: Color,
    onFractionChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        var trackWidthPx by remember { mutableIntStateOf(1) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .onSizeChanged { trackWidthPx = it.width.coerceAtLeast(1) }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onFractionChange((offset.x / trackWidthPx.toFloat()).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        onFractionChange((change.position.x / trackWidthPx.toFloat()).coerceIn(0f, 1f))
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Gradient Pill Track
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(CircleShape)
                    .border(
                        BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        CircleShape
                    )
            ) {
                drawRect(brush = Brush.horizontalGradient(colors = gradientColors))
            }

            // Draggable Thumb
            val thumbDiameter = 24.dp
            val thumbOffsetPx = with(LocalDensity.current) {
                val usableWidth = (trackWidthPx.toFloat() - thumbDiameter.toPx()).coerceAtLeast(0f)
                (fraction.coerceIn(0f, 1f) * usableWidth).roundToInt()
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbOffsetPx, 0) }
                    .size(thumbDiameter)
                    .clip(CircleShape)
                    .background(thumbColor)
                    .border(BorderStroke(2.5.dp, Color.White), CircleShape)
                    .border(BorderStroke(1.dp, Color.Black.copy(alpha = 0.35f)), CircleShape)
            )
        }
    }
}

/**
 * Safely decodes a garment image from disk, sub-sampling oversized camera images so preview
 * rendering and rotation stay fast and memory-safe.
 */
private fun loadSubsampledBitmap(filePath: String, maxDimension: Int): Bitmap? {
    if (filePath.isBlank()) return null
    val file = File(filePath)
    if (!file.exists() || file.length() <= 0L) return null

    return try {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        var inSampleSize = 1
        while (
            boundsOptions.outWidth / inSampleSize > maxDimension ||
            boundsOptions.outHeight / inSampleSize > maxDimension
        ) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inJustDecodeBounds = false
        }
        BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    } catch (_: Exception) {
        null
    }
}
