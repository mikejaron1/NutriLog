package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DailyGoal
import com.example.data.model.FoodLogEntry
import com.example.data.model.MacroTotals
import com.example.data.model.MealType
import com.example.data.sync.AutoSyncUiState
import com.example.data.sync.SyncQueueStatus
import com.example.ui.components.MacroSummaryCard
import com.example.ui.components.MealSectionCard

@Composable
fun DiaryScreen(
    selectedDate: String,
    displayDate: String,
    dailyGoal: DailyGoal,
    macroTotals: MacroTotals,
    logs: List<FoodLogEntry>,
    waterIntakeMl: Int,
    autoSyncState: AutoSyncUiState,
    onDateChange: (Int) -> Unit,
    onSetToday: () -> Unit,
    onAddWater: (Int) -> Unit,
    onDeleteLog: (String) -> Unit,
    onOpenBarcodeScanner: (MealType) -> Unit,
    onOpenAiFoodSearch: (MealType) -> Unit,
    onOpenAiRecipeScan: (MealType) -> Unit,
    onOpenNutritionLabelScan: (MealType) -> Unit,
    onOpenAddCustomFood: () -> Unit,
    onOpenQuickLogFood: (MealType) -> Unit,
    onOpenSyncSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var fabExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Date Navigator Header
            item {
                Spacer(modifier = Modifier.height(8.dp))
                DateNavigatorHeader(
                    displayDate = displayDate,
                    onPrevDay = { onDateChange(-1) },
                    onNextDay = { onDateChange(1) },
                    onToday = onSetToday
                )
            }

            // Google Drive Wi-Fi Auto-Sync Status Pill
            item {
                DiaryWifiAutoSyncBanner(
                    autoSyncState = autoSyncState,
                    onClick = onOpenSyncSettings
                )
            }

            // Macro & Calorie Ring Summary Card
            item {
                MacroSummaryCard(
                    totals = macroTotals,
                    goal = dailyGoal,
                    waterIntakeMl = waterIntakeMl,
                    onAddWater = onAddWater
                )
            }

            // Quick AI & Camera Intake Action Strip
            item {
                QuickIntakeActionStrip(
                    onScanBarcode = { onOpenBarcodeScanner(MealType.SNACK) },
                    onScanNutritionLabel = { onOpenNutritionLabelScan(MealType.SNACK) },
                    onMealPhotoOrRecipe = { onOpenAiRecipeScan(MealType.SNACK) },
                    onAiSearch = { onOpenAiFoodSearch(MealType.SNACK) }
                )
            }

            // 4 Meal Sections: Breakfast, Lunch, Dinner, Snacks
            item {
                val breakfastEntries = logs.filter { it.mealType == MealType.BREAKFAST }
                MealSectionCard(
                    mealType = MealType.BREAKFAST,
                    entries = breakfastEntries,
                    onAddFoodClick = { onOpenQuickLogFood(MealType.BREAKFAST) },
                    onDeleteEntry = onDeleteLog
                )
            }

            item {
                val lunchEntries = logs.filter { it.mealType == MealType.LUNCH }
                MealSectionCard(
                    mealType = MealType.LUNCH,
                    entries = lunchEntries,
                    onAddFoodClick = { onOpenQuickLogFood(MealType.LUNCH) },
                    onDeleteEntry = onDeleteLog
                )
            }

            item {
                val dinnerEntries = logs.filter { it.mealType == MealType.DINNER }
                MealSectionCard(
                    mealType = MealType.DINNER,
                    entries = dinnerEntries,
                    onAddFoodClick = { onOpenQuickLogFood(MealType.DINNER) },
                    onDeleteEntry = onDeleteLog
                )
            }

            item {
                val snackEntries = logs.filter { it.mealType == MealType.SNACK }
                MealSectionCard(
                    mealType = MealType.SNACK,
                    entries = snackEntries,
                    onAddFoodClick = { onOpenQuickLogFood(MealType.SNACK) },
                    onDeleteEntry = onDeleteLog
                )
            }

            item {
                Spacer(modifier = Modifier.height(90.dp))
            }
        }

        // Multi-Action FAB
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (fabExpanded) {
                // Barcode Action
                FabSubAction(
                    label = "Scan Barcode",
                    icon = Icons.Default.QrCodeScanner,
                    onClick = {
                        fabExpanded = false
                        onOpenBarcodeScanner(MealType.SNACK)
                    },
                    testTag = "fab_scan_barcode"
                )

                // Nutrition Label Action
                FabSubAction(
                    label = "Scan Nutrition Label",
                    icon = Icons.Default.ReceiptLong,
                    onClick = {
                        fabExpanded = false
                        onOpenNutritionLabelScan(MealType.SNACK)
                    },
                    testTag = "fab_scan_nutrition_label"
                )

                // Recipe & Photo Action
                FabSubAction(
                    label = "AI Recipe / Photo",
                    icon = Icons.Default.Fastfood,
                    onClick = {
                        fabExpanded = false
                        onOpenAiRecipeScan(MealType.SNACK)
                    },
                    testTag = "fab_ai_recipe_photo"
                )

                // Gemini Food Search Action
                FabSubAction(
                    label = "AI Food Search",
                    icon = Icons.Default.AutoAwesome,
                    onClick = {
                        fabExpanded = false
                        onOpenAiFoodSearch(MealType.SNACK)
                    },
                    testTag = "fab_ai_food_search"
                )

                // Custom Food
                FabSubAction(
                    label = "Custom Food",
                    icon = Icons.Default.Edit,
                    onClick = {
                        fabExpanded = false
                        onOpenAddCustomFood()
                    },
                    testTag = "fab_add_custom_food"
                )
            }

            FloatingActionButton(
                onClick = { fabExpanded = !fabExpanded },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .size(58.dp)
                    .testTag("main_add_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Food Intake",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun DiaryWifiAutoSyncBanner(
    autoSyncState: AutoSyncUiState,
    onClick: () -> Unit
) {
    val isWaitingForWifi = autoSyncState.status == SyncQueueStatus.WAITING_FOR_WIFI ||
        (!autoSyncState.isWifiConnected && autoSyncState.pendingEntriesCount > 0)

    val containerColor = when {
        !autoSyncState.autoSyncEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        isWaitingForWifi -> MaterialTheme.colorScheme.tertiaryContainer
        autoSyncState.status == SyncQueueStatus.SYNCING_NOW -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    }

    val contentColor = when {
        !autoSyncState.autoSyncEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
        isWaitingForWifi -> MaterialTheme.colorScheme.onTertiaryContainer
        autoSyncState.status == SyncQueueStatus.SYNCING_NOW -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diary_wifi_sync_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                when {
                    autoSyncState.status == SyncQueueStatus.SYNCING_NOW -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = contentColor
                        )
                    }
                    isWaitingForWifi || !autoSyncState.isWifiConnected -> {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = "Waiting for Wi-Fi",
                            tint = contentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "On Wi-Fi",
                            tint = contentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    val headline = when {
                        !autoSyncState.autoSyncEnabled -> "Google Drive Auto-Sync Paused"
                        isWaitingForWifi -> "Waiting for Wi-Fi • ${autoSyncState.pendingEntriesCount} Queued for Drive"
                        !autoSyncState.isWifiConnected -> "Off Wi-Fi • New entries will queue until Wi-Fi joins"
                        autoSyncState.status == SyncQueueStatus.SYNCING_NOW -> "Syncing to Google Drive over Wi-Fi..."
                        else -> "Google Drive Auto-Sync Active (Wi-Fi)"
                    }
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = autoSyncState.lastSyncMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = if (isWaitingForWifi) Icons.Default.CloudQueue else Icons.Default.CloudDone,
                contentDescription = "Drive Sync Status",
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun QuickIntakeActionStrip(
    onScanBarcode: () -> Unit,
    onScanNutritionLabel: () -> Unit,
    onMealPhotoOrRecipe: () -> Unit,
    onAiSearch: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickStripChip(
            label = "Barcode",
            icon = Icons.Default.QrCodeScanner,
            onClick = onScanBarcode,
            testTag = "quick_strip_barcode",
            modifier = Modifier.weight(1f)
        )
        QuickStripChip(
            label = "Label Scan",
            icon = Icons.Default.ReceiptLong,
            onClick = onScanNutritionLabel,
            testTag = "quick_strip_nutrition_label",
            modifier = Modifier.weight(1f)
        )
        QuickStripChip(
            label = "Meal Photo",
            icon = Icons.Default.Fastfood,
            onClick = onMealPhotoOrRecipe,
            testTag = "quick_strip_meal_photo",
            modifier = Modifier.weight(1f)
        )
        QuickStripChip(
            label = "AI Search",
            icon = Icons.Default.AutoAwesome,
            onClick = onAiSearch,
            testTag = "quick_strip_ai_search",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickStripChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier.testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DateNavigatorHeader(
    displayDate: String,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onPrevDay,
                modifier = Modifier.testTag("prev_date_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous Day"
                )
            }

            Surface(
                onClick = onToday,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.testTag("date_header_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Today,
                        contentDescription = "Today",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = displayDate,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(
                onClick = onNextDay,
                modifier = Modifier.testTag("next_date_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next Day"
                )
            }
        }
    }
}

@Composable
private fun FabSubAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.testTag(testTag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
