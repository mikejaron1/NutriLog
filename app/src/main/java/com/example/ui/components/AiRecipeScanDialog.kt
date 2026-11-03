package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.data.model.MealType
import com.example.data.remote.RecipeAnalysisResult
import com.example.ui.AiActionState
import com.example.ui.theme.CarbsColor
import com.example.ui.theme.FatColor
import com.example.ui.theme.FiberColor
import com.example.ui.theme.ProteinColor
import kotlin.math.roundToInt

@Composable
fun AiRecipeScanDialog(
    initialMealType: MealType,
    initialTab: Int = 0, // 0: Meal Photo, 1: Nutrition Label, 2: Recipe Text
    scanState: AiActionState<RecipeAnalysisResult>,
    onAnalyze: (String?, Bitmap?, Boolean) -> Unit,
    onLogRecipe: (RecipeAnalysisResult, MealType, Double) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var recipeText by remember { mutableStateOf("") }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedMealType by remember { mutableStateOf(initialMealType) }

    // Interactive Calculator State
    var calcByGrams by remember { mutableStateOf(false) }
    var servingsCount by remember { mutableDoubleStateOf(1.0) }
    var customGramsInput by remember { mutableStateOf("100") }

    // Sync default weight when scanState completes
    LaunchedEffect(scanState) {
        if (scanState is AiActionState.Success) {
            servingsCount = 1.0
            customGramsInput = scanState.data.servingWeightGrams.roundToInt().toString()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                val maxDim = 1024
                val scaled = if (bmp.width > maxDim || bmp.height > maxDim) {
                    val ratio = bmp.width.toFloat() / bmp.height.toFloat()
                    val targetW = if (ratio > 1) maxDim else (maxDim * ratio).toInt()
                    val targetH = if (ratio > 1) (maxDim / ratio).toInt() else maxDim
                    Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
                } else bmp
                selectedBitmap = scaled
            } catch (_: Exception) {
            }
        }
    }

    val cameraPreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) {
            selectedBitmap = bmp
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraPreviewLauncher.launch(null)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .testTag("ai_recipe_scan_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (selectedTab == 1) Icons.Default.ReceiptLong else Icons.Default.Fastfood,
                            contentDescription = "AI Intake",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Photo, Label & Recipe",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(
                    text = when (selectedTab) {
                        0 -> "Snap or upload a meal/ingredients photo for automatic macro estimation."
                        1 -> "Upload a Nutrition Facts label photo to extract exact per-serving values and calculate your portion."
                        else -> "Paste a recipe or ingredient list to calculate per-serving macros."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.testTag("tab_meal_photo"),
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Meal", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.testTag("tab_nutrition_label"),
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Nutrition Label", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        modifier = Modifier.testTag("tab_recipe_text"),
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Recipe", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedTab == 0 || selectedTab == 1) {
                    val isLabelMode = selectedTab == 1

                    if (selectedBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Image(
                                bitmap = selectedBitmap!!.asImageBitmap(),
                                contentDescription = if (isLabelMode) "Selected Nutrition Label" else "Selected Meal Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = if (isLabelMode) ContentScale.Fit else ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Change Image", style = MaterialTheme.typography.labelMedium)
                            }
                            Button(
                                onClick = {
                                    onAnalyze(
                                        recipeText.takeIf { it.isNotBlank() },
                                        selectedBitmap,
                                        isLabelMode
                                    )
                                },
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("analyze_photo_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (isLabelMode) "Scan Label" else "Analyze Meal",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    } else {
                        // Image Upload & Capture Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(92.dp)
                                    .testTag("pick_photo_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = "Pick Image",
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        if (isLabelMode) "Gallery Label" else "Gallery Photo",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        cameraPreviewLauncher.launch(null)
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(92.dp)
                                    .testTag("camera_capture_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.AddAPhoto,
                                        contentDescription = "Take Photo",
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        if (isLabelMode) "Snap Label" else "Snap Meal",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        if (isLabelMode) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                onClick = {
                                    val sampleBmp = generateSampleNutritionLabelBitmap()
                                    selectedBitmap = sampleBmp
                                    if (recipeText.isBlank()) {
                                        recipeText = "Organic Protein Granola Crunch"
                                    }
                                    onAnalyze(recipeText, sampleBmp, true)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sample_nutrition_label_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FactCheck,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Try Sample Nutrition Facts Label Image",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = recipeText,
                        onValueChange = { recipeText = it },
                        placeholder = {
                            Text(
                                if (isLabelMode) "Product name or notes (e.g. Protein Granola, ate 80g)"
                                else "Optional notes (e.g. cooked with 1 tbsp olive oil)"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                } else {
                    // Recipe Text Mode
                    OutlinedTextField(
                        value = recipeText,
                        onValueChange = { recipeText = it },
                        placeholder = {
                            Text("Paste recipe ingredients or nutrition label text...\n\nExample:\n- 200g chicken breast\n- 1 cup cooked brown rice\n- 1 tbsp olive oil\n- 100g steamed broccoli")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 130.dp)
                            .testTag("recipe_text_input"),
                        singleLine = false
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (recipeText.isNotBlank()) {
                                onAnalyze(recipeText, null, false)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("analyze_recipe_button"),
                        enabled = recipeText.isNotBlank()
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Calculate Recipe Macros")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action States
                when (scanState) {
                    is AiActionState.Idle -> {}
                    is AiActionState.Loading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = scanState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is AiActionState.Error -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = scanState.message,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    is AiActionState.Success -> {
                        val result = scanState.data
                        val baseWeightGrams = result.servingWeightGrams.coerceAtLeast(1.0)
                        val effectiveMultiplier = if (calcByGrams) {
                            val enteredGrams = customGramsInput.toDoubleOrNull() ?: baseWeightGrams
                            (enteredGrams / baseWeightGrams).coerceAtLeast(0.05)
                        } else {
                            servingsCount.coerceAtLeast(0.1)
                        }

                        val calcCalories = (result.perServingCalories * effectiveMultiplier).roundToInt()
                        val calcProtein = result.perServingProteinGrams * effectiveMultiplier
                        val calcCarbs = result.perServingCarbsGrams * effectiveMultiplier
                        val calcFat = result.perServingFatGrams * effectiveMultiplier
                        val calcFiber = result.perServingFiberGrams * effectiveMultiplier
                        val calcSugar = result.perServingSugarGrams * effectiveMultiplier
                        val calcSodium = (result.perServingSodiumMg * effectiveMultiplier).roundToInt()
                        val calcWeightGrams = (baseWeightGrams * effectiveMultiplier).roundToInt()

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("nutrition_analysis_result_card"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = result.dishName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "1 Serving = ${result.servingSizeDescription} (${baseWeightGrams.roundToInt()}g)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (result.isNutritionLabel) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "Label Verified",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Interactive Portion & Gram Calculator Box
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Calculate,
                                                    contentDescription = "Calculator",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Portion Calculator",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                FilterChip(
                                                    selected = !calcByGrams,
                                                    onClick = { calcByGrams = false },
                                                    label = { Text("Servings", fontSize = 11.sp) }
                                                )
                                                FilterChip(
                                                    selected = calcByGrams,
                                                    onClick = {
                                                        calcByGrams = true
                                                        customGramsInput = (baseWeightGrams * servingsCount).roundToInt().toString()
                                                    },
                                                    label = { Text("Grams (g)", fontSize = 11.sp) },
                                                    modifier = Modifier.testTag("calc_by_grams_chip")
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        if (!calcByGrams) {
                                            // By Servings Stepper + Quick Multipliers
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "${String.format("%.2f", servingsCount)}x (${calcWeightGrams}g)",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(
                                                        onClick = { if (servingsCount > 0.25) servingsCount -= 0.25 },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(Icons.Default.Remove, contentDescription = "Decrease")
                                                    }
                                                    Text(
                                                        text = String.format("%.2f", servingsCount),
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp)
                                                    )
                                                    IconButton(
                                                        onClick = { servingsCount += 0.25 },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(Icons.Default.Add, contentDescription = "Increase")
                                                    }
                                                }
                                            }
                                        } else {
                                            // By Exact Weight in Grams
                                            OutlinedTextField(
                                                value = customGramsInput,
                                                onValueChange = { customGramsInput = it },
                                                label = { Text("Enter amount eaten in grams (1 serving = ${baseWeightGrams.roundToInt()}g)") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("custom_grams_calculator_input")
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Calculated Live Totals
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Calculated Total ($calcWeightGrams g):",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "$calcCalories kcal",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                "Protein: ${String.format("%.1f", calcProtein)}g",
                                                color = ProteinColor,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "Carbs: ${String.format("%.1f", calcCarbs)}g",
                                                color = CarbsColor,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "Fat: ${String.format("%.1f", calcFat)}g",
                                                color = FatColor,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "Fiber: ${String.format("%.1f", calcFiber)}g",
                                                color = FiberColor,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        if (calcSugar > 0.0 || calcSodium > 0) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Sugars: ${String.format("%.1f", calcSugar)}g  •  Sodium: ${calcSodium}mg",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (result.detectedIngredients.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (result.isNutritionLabel) "Label Highlights:" else "Detected Ingredients:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = result.detectedIngredients.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Meal Selector
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    MealType.values().forEach { mt ->
                                        val selected = selectedMealType == mt
                                        Surface(
                                            onClick = { selectedMealType = mt },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = mt.displayName.take(5),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        onLogRecipe(result, selectedMealType, effectiveMultiplier)
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("log_recipe_button")
                                ) {
                                    Text("Log $calcCalories kcal to ${selectedMealType.displayName}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Generates a realistic, high-contrast FDA-style Nutrition Facts Label Bitmap
 * so users can test scanning a real Nutrition Facts label image even on an emulator.
 */
private fun generateSampleNutritionLabelBitmap(): Bitmap {
    val width = 600
    val height = 780
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // White background
    canvas.drawColor(AndroidColor.WHITE)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    canvas.drawRect(20f, 20f, width - 20f, height - 20f, borderPaint)

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        textSize = 54f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        textSize = 28f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val regularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        textSize = 26f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val calPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        textSize = 62f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val thickLinePaint = Paint().apply {
        color = AndroidColor.BLACK
        strokeWidth = 18f
    }
    val thinLinePaint = Paint().apply {
        color = AndroidColor.BLACK
        strokeWidth = 3f
    }

    var y = 80f
    canvas.drawText("Nutrition Facts", 40f, y, titlePaint)
    y += 18f
    canvas.drawLine(40f, y, width - 40f, y, thinLinePaint)

    y += 38f
    canvas.drawText("8 servings per container", 40f, y, regularPaint)
    y += 38f
    canvas.drawText("Serving size", 40f, y, boldPaint)
    canvas.drawText("2/3 cup (55g)", 340f, y, boldPaint)

    y += 24f
    canvas.drawLine(40f, y, width - 40f, y, thickLinePaint)

    y += 42f
    canvas.drawText("Amount per serving", 40f, y, boldPaint)
    y += 52f
    canvas.drawText("Calories", 40f, y, titlePaint)
    canvas.drawText("240", 430f, y, calPaint)

    y += 22f
    canvas.drawLine(40f, y, width - 40f, y, thickLinePaint)

    y += 36f
    canvas.drawText("% Daily Value*", 390f, y, boldPaint)
    y += 14f
    canvas.drawLine(40f, y, width - 40f, y, thinLinePaint)

    val rows = listOf(
        Triple("Total Fat 8g", "10%", true),
        Triple("  Saturated Fat 1.5g", "8%", false),
        Triple("Sodium 140mg", "6%", true),
        Triple("Total Carbohydrate 32g", "12%", true),
        Triple("  Dietary Fiber 5g", "18%", false),
        Triple("  Total Sugars 6g", "", false),
        Triple("Protein 12g", "24%", true)
    )

    for ((label, dv, isBold) in rows) {
        y += 42f
        canvas.drawText(label, 40f, y, if (isBold) boldPaint else regularPaint)
        if (dv.isNotEmpty()) {
            canvas.drawText(dv, 480f, y, boldPaint)
        }
        y += 14f
        canvas.drawLine(40f, y, width - 40f, y, thinLinePaint)
    }

    return bitmap
}
