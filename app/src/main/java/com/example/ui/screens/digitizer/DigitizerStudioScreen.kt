package com.example.ui.screens.digitizer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.WadaShapes
import com.example.ui.theme.buildWadaText
import com.example.util.ColorUtils
import com.example.viewmodel.WardrobeViewModel
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * DigitizerStudioScreen allows digitizing real garments via CameraX
 * or generating virtual swatches using Wada's pigment laboratory.
 */
@Composable
fun DigitizerStudioScreen(
    viewModel: WardrobeViewModel,
    onDigitizeSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val categories by viewModel.categories.collectAsState()

    var activeStudioMode by remember { mutableStateOf(0) } // 0 = Camera Lens, 1 = Swatch Lab
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: "Tops") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("digitizer_studio_screen")
    ) {
        // Mode Switcher (Hero control)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
        ) {

            // Studio Mode Tabs (Camera Lens vs Swatch Lab)
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = activeStudioMode == 0,
                    onClick = { activeStudioMode = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp)) }
                ) {
                    Text("Camera Lens")
                }
                SegmentedButton(
                    selected = activeStudioMode == 1,
                    onClick = { activeStudioMode = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Filled.ColorLens, contentDescription = null, modifier = Modifier.size(16.dp)) }
                ) {
                    Text("Swatch Canvas")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Apparel Category Selector Pill Row
            Text(
                text = "ASSIGN CATEGORY",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategory == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) },
                        shape = WadaShapes.FilterPill,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("digitize_cat_chip_$cat")
                    )
                }
            }
        }

        // 2. Active Mode Workspace
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            if (activeStudioMode == 0) {
                CameraLensSection(
                    selectedCategory = selectedCategory,
                    onDigitizePhoto = { file ->
                        viewModel.digitizePhoto(context, file, selectedCategory) {
                            Toast.makeText(context, "Digitized & matched in wardrobe!", Toast.LENGTH_SHORT).show()
                            onDigitizeSuccess()
                        }
                    }
                )
            } else {
                SwatchCanvasSection(
                    selectedCategory = selectedCategory,
                    onDigitizeColor = { hex ->
                        viewModel.createVirtualGarment(context, selectedCategory, hex)
                        Toast.makeText(context, "Saved $hex to wardrobe!", Toast.LENGTH_SHORT).show()
                        onDigitizeSuccess()
                    }
                )
            }
        }
    }
}

/**
 * CameraLensSection provides live CameraX viewfinder with center-weighted reticle
 * and high-resolution photo capture for dominant color extraction.
 */
@Composable
private fun CameraLensSection(
    selectedCategory: String,
    onDigitizePhoto: (File) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var isCapturing by remember { mutableStateOf(false) }

    if (!hasCameraPermission) {
        // Fallback UI when permission not yet granted
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp),
            shape = WadaShapes.AsymmetricHeroCard,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera Permission Required",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Grant camera access to point your lens at clothing and sample natural dyes and fabric pigments.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    shape = WadaShapes.FilterPill,
                    modifier = Modifier.testTag("request_camera_perm_button")
                ) {
                    Text("Grant Permission")
                }
            }
        }
    } else {
        // Live Camera Viewfinder with Capture Button
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 90.dp)
                .clip(WadaShapes.AsymmetricHeroCard)
                .background(Color.Black)
        ) {
            // CameraX Viewfinder
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    val executor: ExecutorService = Executors.newSingleThreadExecutor()

                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                            imageCapture = capture

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Center Target Crosshair for color sampling
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .align(Alignment.Center)
                    .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.8f)), CircleShape)
            )

            // Capture Trigger Pill Button
            FloatingActionButton(
                onClick = {
                    if (isCapturing) return@FloatingActionButton
                    isCapturing = true
                    val capture = imageCapture
                    if (capture != null) {
                        val photoFile = File(context.cacheDir, "sample_${System.currentTimeMillis()}.jpg")
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                        capture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    isCapturing = false
                                    onDigitizePhoto(photoFile)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    isCapturing = false
                                    Toast.makeText(context, "Capture error: ${exception.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    } else {
                        isCapturing = false
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .size(68.dp)
                    .testTag("camera_capture_button")
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Capture", modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

/**
 * SwatchCanvasSection allows interactive color exploration from Sanzo Wada's
 * primary pigments and custom hex inputs with real-time preview.
 */
@Composable
private fun SwatchCanvasSection(
    selectedCategory: String,
    onDigitizeColor: (String) -> Unit
) {
    val curatedSwatches = remember {
        listOf(
            "#1E2C3D" to "Kon-iro (Indigo)",
            "#D94E34" to "Shu-iro (Vermilion)",
            "#568F8B" to "Asagi (Celadon)",
            "#627254" to "Moegi (Bamboo)",
            "#D49B35" to "Yamabuki (Amber)",
            "#7A3E48" to "Azuki (Russet)",
            "#2C4A6F" to "Ruri (Lapis)",
            "#1C1917" to "Kuro (Charcoal)",
            "#A84432" to "Beni (Carmine)",
            "#E0C9A6" to "Torinoko (Eggshell)",
            "#847C6F" to "Rikyu (Tea Gray)",
            "#4D5840" to "Sensai (Deep Moss)"
        )
    }

    var selectedHex by remember { mutableStateOf(curatedSwatches.first().first) }
    var customHexInput by remember { mutableStateOf("") }
    val activeColor = ColorUtils.parseSafeColor(selectedHex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 100.dp)
    ) {
        // Active Swatch Canvas Preview
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            shape = WadaShapes.AsymmetricHeroCard,
            colors = CardDefaults.cardColors(containerColor = activeColor),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = selectedHex.uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = ColorUtils.getContrastingTextColor(activeColor)
                    )
                    Text(
                        text = "READY TO DIGITIZE FOR $selectedCategory".uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorUtils.getContrastingTextColor(activeColor).copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Grid of Curated Wada Swatches
        Text(
            text = "SANZO WADA CURATED PIGMENTS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            items(curatedSwatches) { (hex, name) ->
                val col = ColorUtils.parseSafeColor(hex)
                val isSelected = selectedHex.equals(hex, ignoreCase = true)

                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(WadaShapes.SwatchSquircle)
                        .background(col)
                        .border(
                            BorderStroke(
                                if (isSelected) 3.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.15f)
                            ),
                            WadaShapes.SwatchSquircle
                        )
                        .clickable { selectedHex = hex },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = name,
                            tint = ColorUtils.getContrastingTextColor(col),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Custom Hex Input
        OutlinedTextField(
            value = customHexInput,
            onValueChange = { input ->
                customHexInput = input
                if (input.isNotBlank()) {
                    val parsed = ColorUtils.parseSafeColor(input, Color.Unspecified)
                    if (parsed != Color.Unspecified) {
                        selectedHex = if (input.startsWith("#")) input else "#$input"
                    }
                }
            },
            label = { Text("Custom Color Hex (e.g. #3D5A80)") },
            singleLine = true,
            shape = WadaShapes.SwatchSquircle,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Save & Digitize Button
        Button(
            onClick = { onDigitizeColor(selectedHex) },
            shape = WadaShapes.FilterPill,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("digitize_swatch_button")
        ) {
            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Digitize & Save to $selectedCategory", style = MaterialTheme.typography.labelLarge)
        }
    }
}
