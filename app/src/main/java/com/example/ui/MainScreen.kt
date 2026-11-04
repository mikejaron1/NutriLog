package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.example.data.model.FoodItem
import com.example.data.model.MealType
import com.example.ui.components.AddCustomFoodDialog
import com.example.ui.components.AiFoodSearchDialog
import com.example.ui.components.AiRecipeScanDialog
import com.example.ui.components.BarcodeResultDialog
import com.example.ui.components.BarcodeScannerSheet
import com.example.ui.components.FoodItemQuickLogDialog
import com.example.ui.screens.DiaryScreen
import com.example.ui.screens.FavoritesAndRecentsScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.SettingsAndSyncScreen
import com.example.ui.theme.NutriLogTheme

@Composable
fun MainScreen(viewModel: NutriViewModel) {
    val themePref by viewModel.themePreference.collectAsState()
    val isSystemDark = isSystemInDarkTheme()
    val isDarkTheme = when (themePref) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemDark
    }

    NutriLogTheme(darkTheme = isDarkTheme) {
        // 0: Diary, 1: History, 2: AI Advisor, 3: Favorites, 4: Sync & Goals
        var currentTab by remember { mutableIntStateOf(0) }

        BackHandler(enabled = currentTab != 0) {
            currentTab = 0
        }

        // Dialog & Sheet States
        var showBarcodeScanner by remember { mutableStateOf(false) }
        var activeMealTypeForBarcode by remember { mutableStateOf(MealType.SNACK) }

        var showAiFoodSearch by remember { mutableStateOf(false) }
        var activeMealTypeForAiSearch by remember { mutableStateOf(MealType.SNACK) }

        var showAiRecipeScan by remember { mutableStateOf(false) }
        var recipeScanInitialTab by remember { mutableIntStateOf(0) } // 0: Meal Photo, 1: Nutrition Label, 2: Recipe Text
        var activeMealTypeForRecipeScan by remember { mutableStateOf(MealType.SNACK) }

        var showAddCustomFood by remember { mutableStateOf(false) }

        var quickLogItem by remember { mutableStateOf<FoodItem?>(null) }
        var quickLogMealType by remember { mutableStateOf(MealType.SNACK) }

        // Data flows
        val selectedDate by viewModel.selectedDate.collectAsState()
        val displayDate by viewModel.displayDate.collectAsState()
        val dailyGoal by viewModel.dailyGoal.collectAsState()
        val currentLogs by viewModel.currentLogs.collectAsState()
        val allFoodLogs by viewModel.allFoodLogs.collectAsState()
        val allWaterLogs by viewModel.allWaterLogs.collectAsState()
        val macroTotals by viewModel.macroTotals.collectAsState()
        val waterLog by viewModel.currentWaterLog.collectAsState()

        val allFoods by viewModel.allFoodItems.collectAsState()
        val favoriteFoods by viewModel.favoriteFoodItems.collectAsState()
        val recentFoods by viewModel.recentFoodItems.collectAsState()

        val foodSearchState by viewModel.foodSearchState.collectAsState()
        val recipeScanState by viewModel.recipeScanState.collectAsState()
        val barcodeState by viewModel.barcodeState.collectAsState()
        val insightsState by viewModel.insightsState.collectAsState()
        val syncState by viewModel.syncState.collectAsState()
        val autoSyncState by viewModel.autoSyncState.collectAsState()

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Diary") },
                        label = { Text("Diary", fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("nav_item_diary")
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Default.BarChart, contentDescription = "History") },
                        label = { Text("History", fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("nav_item_history")
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = {
                            currentTab = 2
                            if (insightsState is AiActionState.Idle) {
                                viewModel.generateNutritionalInsights()
                            }
                        },
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI Advisor") },
                        label = { Text("Advisor", fontWeight = if (currentTab == 2) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("nav_item_insights")
                    )
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 },
                        icon = { Icon(Icons.Default.Star, contentDescription = "Favorites") },
                        label = { Text("Favorites", fontWeight = if (currentTab == 3) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("nav_item_favorites")
                    )
                    NavigationBarItem(
                        selected = currentTab == 4,
                        onClick = { currentTab = 4 },
                        icon = { Icon(Icons.Default.CloudDone, contentDescription = "Sync & Goals") },
                        label = {
                            val labelText = if (autoSyncState.pendingEntriesCount > 0 && !autoSyncState.isWifiConnected) {
                                "Sync (${autoSyncState.pendingEntriesCount})"
                            } else {
                                "Sync"
                            }
                            Text(labelText, fontWeight = if (currentTab == 4) FontWeight.Bold else FontWeight.Normal)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("nav_item_settings")
                    )
                }
            }
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                color = MaterialTheme.colorScheme.background
            ) {
                when (currentTab) {
                    0 -> DiaryScreen(
                        selectedDate = selectedDate,
                        displayDate = displayDate,
                        dailyGoal = dailyGoal,
                        macroTotals = macroTotals,
                        logs = currentLogs,
                        waterIntakeMl = waterLog.intakeMl,
                        autoSyncState = autoSyncState,
                        onDateChange = { viewModel.changeDateByDays(it) },
                        onSetToday = { viewModel.setToday() },
                        onAddWater = { viewModel.addWater(it) },
                        onDeleteLog = { viewModel.deleteLogEntry(it) },
                        onOpenBarcodeScanner = { mealType ->
                            activeMealTypeForBarcode = mealType
                            showBarcodeScanner = true
                        },
                        onOpenAiFoodSearch = { mealType ->
                            activeMealTypeForAiSearch = mealType
                            viewModel.resetFoodSearchState()
                            showAiFoodSearch = true
                        },
                        onOpenAiRecipeScan = { mealType ->
                            activeMealTypeForRecipeScan = mealType
                            recipeScanInitialTab = 0
                            viewModel.resetRecipeScanState()
                            showAiRecipeScan = true
                        },
                        onOpenNutritionLabelScan = { mealType ->
                            activeMealTypeForRecipeScan = mealType
                            recipeScanInitialTab = 1
                            viewModel.resetRecipeScanState()
                            showAiRecipeScan = true
                        },
                        onOpenAddCustomFood = { showAddCustomFood = true },
                        onOpenQuickLogFood = { mealType ->
                            quickLogMealType = mealType
                            currentTab = 3 // Switch to Favorites & Past Foods for quick selection
                        },
                        onOpenSyncSettings = {
                            currentTab = 4
                        }
                    )
                    1 -> HistoryScreen(
                        allLogs = allFoodLogs,
                        allWaterLogs = allWaterLogs,
                        dailyGoal = dailyGoal,
                        onSelectDateInDiary = { dateStr ->
                            viewModel.setDate(dateStr)
                            currentTab = 0
                        }
                    )
                    2 -> InsightsScreen(
                        displayDate = displayDate,
                        goal = dailyGoal,
                        macroTotals = macroTotals,
                        logs = currentLogs,
                        waterIntakeMl = waterLog.intakeMl,
                        insightsState = insightsState,
                        onGenerateInsights = { viewModel.generateNutritionalInsights() }
                    )
                    3 -> FavoritesAndRecentsScreen(
                        favoriteFoods = favoriteFoods,
                        recentFoods = recentFoods,
                        allFoods = allFoods,
                        onFoodSelected = { item ->
                            quickLogItem = item
                        },
                        onToggleFavorite = { item ->
                            viewModel.toggleFavorite(item)
                        },
                        onDeleteFood = { item ->
                            viewModel.deleteFoodItem(item)
                        },
                        onAddNewCustomFood = { showAddCustomFood = true }
                    )
                    4 -> SettingsAndSyncScreen(
                        currentGoal = dailyGoal,
                        themePreference = themePref,
                        syncState = syncState,
                        autoSyncState = autoSyncState,
                        backupFileName = viewModel.getBackupFileName(),
                        onExportBackup = { uri -> viewModel.exportBackupToUri(uri) },
                        onImportBackup = { uri -> viewModel.importBackupFromUri(uri) },
                        onSetAutoSyncEnabled = { viewModel.setAutoSyncOnWifiEnabled(it) },
                        onSetWifiHoldMode = { viewModel.setWifiHoldMode(it) },
                        onSyncNowOverWifi = { viewModel.syncNowOverWifi() },
                        onUpdateGoals = { c, p, crb, f, fib, w ->
                            viewModel.updateGoals(c, p, crb, f, fib, w)
                        },
                        onThemeChange = { viewModel.setThemePreference(it) },
                        onResetSyncState = { viewModel.resetSyncState() },
                        onClearAllHistory = { viewModel.clearAllHistoryLogs() }
                    )
                }
            }

            // Barcode Scanner Modal Sheet
            if (showBarcodeScanner) {
                BarcodeScannerSheet(
                    onBarcodeScanned = { barcode ->
                        showBarcodeScanner = false
                        viewModel.lookupBarcode(barcode)
                    },
                    onClose = { showBarcodeScanner = false }
                )
            }

            // Scanned Barcode Result Dialog
            if (barcodeState is AiActionState.Success) {
                val scannedItem = (barcodeState as AiActionState.Success<FoodItem>).data
                BarcodeResultDialog(
                    foodItem = scannedItem,
                    initialMealType = activeMealTypeForBarcode,
                    onLog = { item, mealType, servings ->
                        viewModel.logFoodItem(item, mealType, servings)
                        viewModel.resetBarcodeState()
                    },
                    onDismiss = { viewModel.resetBarcodeState() }
                )
            }

            // AI Food Description Search Dialog
            if (showAiFoodSearch) {
                AiFoodSearchDialog(
                    initialMealType = activeMealTypeForAiSearch,
                    searchState = foodSearchState,
                    onSearch = { desc -> viewModel.searchFoodWithGemini(desc) },
                    onLogFood = { item, mealType, servings ->
                        viewModel.logFoodItem(item, mealType, servings)
                        showAiFoodSearch = false
                        viewModel.resetFoodSearchState()
                    },
                    onDismiss = {
                        showAiFoodSearch = false
                        viewModel.resetFoodSearchState()
                    }
                )
            }

            // AI Photo, Nutrition Label & Recipe Scan Dialog
            if (showAiRecipeScan) {
                AiRecipeScanDialog(
                    initialMealType = activeMealTypeForRecipeScan,
                    initialTab = recipeScanInitialTab,
                    scanState = recipeScanState,
                    onAnalyze = { text, bmp, isLabel ->
                        viewModel.analyzeRecipe(text, bmp, isLabel)
                    },
                    onLogRecipe = { result, mealType, servingsMultiplier ->
                        viewModel.logAnalyzedRecipe(result, mealType, servingsMultiplier)
                        showAiRecipeScan = false
                        viewModel.resetRecipeScanState()
                    },
                    onDismiss = {
                        showAiRecipeScan = false
                        viewModel.resetRecipeScanState()
                    }
                )
            }

            // Quick Log Food Item Dialog
            if (quickLogItem != null) {
                FoodItemQuickLogDialog(
                    foodItem = quickLogItem!!,
                    initialMealType = quickLogMealType,
                    onLog = { item, mealType, servings ->
                        viewModel.logFoodItem(item, mealType, servings)
                        quickLogItem = null
                    },
                    onToggleFavorite = { item ->
                        viewModel.toggleFavorite(item)
                    },
                    onDismiss = { quickLogItem = null }
                )
            }

            // Custom Food Creator Dialog
            if (showAddCustomFood) {
                AddCustomFoodDialog(
                    onSave = { food ->
                        viewModel.saveCustomFood(food)
                        showAddCustomFood = false
                    },
                    onDismiss = { showAddCustomFood = false }
                )
            }
        }
    }
}
