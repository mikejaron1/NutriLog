package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DailyGoal
import com.example.data.model.DailyWaterLog
import com.example.data.model.FoodItem
import com.example.data.model.FoodLogEntry
import com.example.data.model.MacroTotals
import com.example.data.model.MealType
import com.example.data.remote.AiNutritionalInsight
import com.example.data.remote.RecipeAnalysisResult
import com.example.data.repository.NutriRepository
import com.example.data.sync.AutoSyncUiState
import com.example.data.sync.WifiAutoSyncMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed interface AiActionState<out T> {
    data object Idle : AiActionState<Nothing>
    data class Loading(val message: String) : AiActionState<Nothing>
    data class Success<T>(val data: T) : AiActionState<T>
    data class Error(val message: String) : AiActionState<Nothing>
}

class NutriViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "NutriViewModel"
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        private val DISPLAY_DATE_FORMAT = SimpleDateFormat("EEE, MMM d", Locale.US)
    }

    private val repository = NutriRepository(application.applicationContext)

    private val wifiMonitor = WifiAutoSyncMonitor(
        context = application.applicationContext,
        onWifiJoined = {
            onWifiNetworkJoined()
        }
    )

    // Google Drive Wi-Fi Auto-Sync State
    val autoSyncState: StateFlow<AutoSyncUiState> = repository.autoSyncState

    init {
        // Observe real-time Wi-Fi state & hold mode, and flush any pending queue if on Wi-Fi
        viewModelScope.launch {
            combine(
                wifiMonitor.isEffectiveWifiAvailable,
                wifiMonitor.isWifiHoldEnabled
            ) { isWifiAvailable, isHold ->
                isWifiAvailable to isHold
            }.collect { (isWifiAvailable, isHold) ->
                repository.updateWifiConnectivityState(isWifiAvailable, isHold)
                if (isWifiAvailable && autoSyncState.value.autoSyncEnabled && autoSyncState.value.pendingEntriesCount > 0) {
                    repository.flushQueuedSyncOnWifiJoined()
                }
            }
        }

        // Purge any legacy sample/seed data from prior versions automatically
        viewModelScope.launch {
            try {
                repository.purgeLegacySampleData()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to purge legacy data", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        wifiMonitor.unregisterNetworkCallback()
    }

    // Current selected date
    private val _selectedDate = MutableStateFlow(getTodayDateString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // Formatted readable date
    val displayDate: StateFlow<String> = MutableStateFlow(getTodayDisplayString()).apply {
        viewModelScope.launch {
            _selectedDate.collect { dateStr ->
                try {
                    val parsed = DATE_FORMAT.parse(dateStr)
                    value = if (parsed != null) DISPLAY_DATE_FORMAT.format(parsed) else dateStr
                } catch (e: Exception) {
                    value = dateStr
                }
            }
        }
    }

    // Daily Goals
    val dailyGoal: StateFlow<DailyGoal> = repository.dailyGoal
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DailyGoal())

    // Logs for selected date
    val currentLogs: StateFlow<List<FoodLogEntry>> = _selectedDate
        .flatMapLatest { date -> repository.getLogsForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All logs & water for History charts
    val allFoodLogs: StateFlow<List<FoodLogEntry>> = repository.allFoodLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWaterLogs: StateFlow<List<DailyWaterLog>> = repository.allWaterLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Macro Totals for selected date
    val macroTotals: StateFlow<MacroTotals> = _selectedDate
        .flatMapLatest { date -> repository.getMacroTotalsForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MacroTotals())

    // Water Log for selected date
    val currentWaterLog: StateFlow<DailyWaterLog> = _selectedDate
        .flatMapLatest { date -> repository.getWaterLog(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DailyWaterLog(date = getTodayDateString(), intakeMl = 0))

    // Foods Library
    val allFoodItems: StateFlow<List<FoodItem>> = repository.allFoodItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteFoodItems: StateFlow<List<FoodItem>> = repository.favoriteFoodItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentFoodItems: StateFlow<List<FoodItem>> = repository.recentFoodItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // AI States
    private val _foodSearchState = MutableStateFlow<AiActionState<FoodItem>>(AiActionState.Idle)
    val foodSearchState: StateFlow<AiActionState<FoodItem>> = _foodSearchState.asStateFlow()

    private val _recipeScanState = MutableStateFlow<AiActionState<RecipeAnalysisResult>>(AiActionState.Idle)
    val recipeScanState: StateFlow<AiActionState<RecipeAnalysisResult>> = _recipeScanState.asStateFlow()

    private val _barcodeState = MutableStateFlow<AiActionState<FoodItem>>(AiActionState.Idle)
    val barcodeState: StateFlow<AiActionState<FoodItem>> = _barcodeState.asStateFlow()

    private val _insightsState = MutableStateFlow<AiActionState<AiNutritionalInsight>>(AiActionState.Idle)
    val insightsState: StateFlow<AiActionState<AiNutritionalInsight>> = _insightsState.asStateFlow()

    // Backup / Sync manual action state
    private val _syncState = MutableStateFlow<AiActionState<String>>(AiActionState.Idle)
    val syncState: StateFlow<AiActionState<String>> = _syncState.asStateFlow()

    // Dark Mode Setting: "SYSTEM", "DARK", "LIGHT"
    private val _themePreference = MutableStateFlow("SYSTEM")
    val themePreference: StateFlow<String> = _themePreference.asStateFlow()

    fun setThemePreference(pref: String) {
        _themePreference.value = pref
    }

    // Date Navigation
    fun changeDateByDays(offset: Int) {
        try {
            val cal = Calendar.getInstance()
            val current = DATE_FORMAT.parse(_selectedDate.value)
            if (current != null) {
                cal.time = current
                cal.add(Calendar.DAY_OF_YEAR, offset)
                _selectedDate.value = DATE_FORMAT.format(cal.time)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error changing date", e)
        }
    }

    fun setDate(date: String) {
        _selectedDate.value = date
    }

    fun setToday() {
        _selectedDate.value = getTodayDateString()
    }


    // Automatic Wi-Fi Google Drive Sync Trigger after ANY entry
    private suspend fun triggerAutoSyncAfterEntry(entryDescription: String) {
        val isOnWifi = wifiMonitor.refreshWifiState()
        val isHold = wifiMonitor.isWifiHoldEnabled.value
        repository.onEntryRecorded(
            entryDescription = entryDescription,
            isOnWifi = isOnWifi,
            isWifiHoldMode = isHold
        )
    }

    private fun onWifiNetworkJoined() {
        viewModelScope.launch {
            try {
                repository.flushQueuedSyncOnWifiJoined()
            } catch (e: Exception) {
                Log.e(TAG, "Error flushing queued Google Drive sync on Wi-Fi join", e)
            }
        }
    }

    fun setAutoSyncOnWifiEnabled(enabled: Boolean) {
        repository.setAutoSyncEnabled(enabled)
        if (enabled) {
            viewModelScope.launch {
                val isOnWifi = wifiMonitor.refreshWifiState()
                if (isOnWifi && autoSyncState.value.pendingEntriesCount > 0) {
                    repository.flushQueuedSyncOnWifiJoined()
                }
            }
        }
    }

    fun setWifiHoldMode(holdOffline: Boolean) {
        wifiMonitor.setWifiHoldMode(holdOffline)
    }

    fun syncNowOverWifi() {
        viewModelScope.launch {
            val isOnWifi = wifiMonitor.refreshWifiState()
            if (isOnWifi) {
                repository.performManualOrAutoDriveSync("Synced to Google Drive over Wi-Fi")
            } else {
                repository.updateWifiConnectivityState(
                    isWifiAvailable = false,
                    isHoldMode = wifiMonitor.isWifiHoldEnabled.value
                )
            }
        }
    }

    // Food Logging
    fun logFoodItem(
        foodItem: FoodItem,
        mealType: MealType,
        numberOfServings: Double
    ) {
        viewModelScope.launch {
            try {
                repository.logFood(_selectedDate.value, mealType, foodItem, numberOfServings)
                triggerAutoSyncAfterEntry("Logged ${foodItem.name} (${mealType.displayName})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log food", e)
            }
        }
    }

    fun deleteLogEntry(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteLogEntry(id)
                triggerAutoSyncAfterEntry("Deleted food log entry")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete log entry", e)
            }
        }
    }

    fun toggleFavorite(foodItem: FoodItem) {
        viewModelScope.launch {
            try {
                repository.updateFavorite(foodItem.id, !foodItem.isFavorite)
                triggerAutoSyncAfterEntry("Updated favorite: ${foodItem.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle favorite", e)
            }
        }
    }

    fun deleteFoodItem(foodItem: FoodItem) {
        viewModelScope.launch {
            try {
                repository.deleteFoodItem(foodItem)
                triggerAutoSyncAfterEntry("Deleted food item: ${foodItem.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete food item", e)
            }
        }
    }

    fun clearAllHistoryLogs() {
        viewModelScope.launch {
            try {
                repository.clearAllHistoryLogs()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear all logs", e)
            }
        }
    }

    fun saveCustomFood(foodItem: FoodItem) {
        viewModelScope.launch {
            try {
                repository.saveFoodItem(foodItem)
                triggerAutoSyncAfterEntry("Added custom food: ${foodItem.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save food", e)
            }
        }
    }

    // Water Logging
    fun addWater(ml: Int) {
        viewModelScope.launch {
            try {
                val current = currentWaterLog.value.intakeMl
                val newTotal = (current + ml).coerceAtLeast(0)
                repository.setWater(_selectedDate.value, newTotal)
                val sign = if (ml >= 0) "+${ml}ml" else "${ml}ml"
                triggerAutoSyncAfterEntry("Logged water ($sign)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update water", e)
            }
        }
    }

    fun setWaterAmount(ml: Int) {
        viewModelScope.launch {
            try {
                repository.setWater(_selectedDate.value, ml.coerceAtLeast(0))
                triggerAutoSyncAfterEntry("Set water intake (${ml}ml)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set water", e)
            }
        }
    }

    // Daily Goals
    fun updateGoals(
        calorieTarget: Int,
        proteinGrams: Int,
        carbsGrams: Int,
        fatGrams: Int,
        fiberGrams: Int,
        waterMl: Int
    ) {
        viewModelScope.launch {
            try {
                val newGoal = DailyGoal(
                    id = 1,
                    calorieTarget = calorieTarget,
                    proteinTargetGrams = proteinGrams,
                    carbsTargetGrams = carbsGrams,
                    fatTargetGrams = fatGrams,
                    fiberTargetGrams = fiberGrams,
                    waterTargetMl = waterMl
                )
                repository.updateDailyGoal(newGoal)
                triggerAutoSyncAfterEntry("Updated daily macro goals")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update goals", e)
            }
        }
    }

    // AI Food Search
    fun searchFoodWithGemini(description: String) {
        if (description.isBlank()) return
        viewModelScope.launch {
            _foodSearchState.value = AiActionState.Loading("Searching & calculating nutritional values with Gemini...")
            try {
                val result = repository.searchFoodWithAi(description)
                result.fold(
                    onSuccess = { item ->
                        _foodSearchState.value = AiActionState.Success(item)
                    },
                    onFailure = { error ->
                        _foodSearchState.value = AiActionState.Error(error.localizedMessage ?: "Failed to estimate food macros.")
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Food search error", e)
                _foodSearchState.value = AiActionState.Error(e.localizedMessage ?: "An error occurred during search.")
            }
        }
    }

    fun resetFoodSearchState() {
        _foodSearchState.value = AiActionState.Idle
    }

    // Recipe, Meal Photo & Nutrition Facts Label Scan
    fun analyzeRecipe(recipeText: String?, photoBitmap: Bitmap?, isLabelModeHint: Boolean = false) {
        viewModelScope.launch {
            val loadingMsg = if (isLabelModeHint) {
                "Reading Nutrition Facts label & calculating values with Gemini..."
            } else {
                "Analyzing photo / recipe & calculating macros with Gemini..."
            }
            _recipeScanState.value = AiActionState.Loading(loadingMsg)
            try {
                val result = repository.analyzeRecipeOrPhoto(recipeText, photoBitmap, isLabelModeHint)
                result.fold(
                    onSuccess = { analysis ->
                        _recipeScanState.value = AiActionState.Success(analysis)
                    },
                    onFailure = { error ->
                        _recipeScanState.value = AiActionState.Error(error.localizedMessage ?: "Analysis failed.")
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Analysis error", e)
                _recipeScanState.value = AiActionState.Error(e.localizedMessage ?: "Failed to analyze.")
            }
        }
    }

    fun logAnalyzedRecipe(
        analysis: RecipeAnalysisResult,
        mealType: MealType,
        servingsMultiplier: Double = 1.0
    ) {
        viewModelScope.launch {
            try {
                val brandInfo = if (analysis.isNutritionLabel) {
                    "Nutrition Label (${analysis.servingSizeDescription})"
                } else {
                    "Recipe: ${analysis.detectedIngredients.take(3).joinToString(", ")}"
                }
                val foodItem = FoodItem(
                    name = analysis.dishName,
                    brand = brandInfo,
                    servingSize = analysis.servingWeightGrams,
                    servingUnit = "g",
                    calories = analysis.perServingCalories,
                    proteinGrams = analysis.perServingProteinGrams,
                    carbsGrams = analysis.perServingCarbsGrams,
                    fatGrams = analysis.perServingFatGrams,
                    fiberGrams = analysis.perServingFiberGrams,
                    source = if (analysis.isNutritionLabel) "NUTRITION_LABEL" else "RECIPE_SCAN"
                )
                repository.saveFoodItem(foodItem)
                repository.logFood(_selectedDate.value, mealType, foodItem, servingsMultiplier)
                triggerAutoSyncAfterEntry("Logged ${analysis.dishName} (${mealType.displayName})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log analyzed item", e)
            }
        }
    }

    fun resetRecipeScanState() {
        _recipeScanState.value = AiActionState.Idle
    }

    // Barcode Lookup
    fun lookupBarcode(barcode: String) {
        if (barcode.isBlank()) return
        viewModelScope.launch {
            _barcodeState.value = AiActionState.Loading("Looking up barcode $barcode...")
            try {
                val result = repository.lookupBarcode(barcode)
                result.fold(
                    onSuccess = { item ->
                        _barcodeState.value = AiActionState.Success(item)
                    },
                    onFailure = { err ->
                        _barcodeState.value = AiActionState.Error(err.localizedMessage ?: "Product not found for barcode.")
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Barcode lookup error", e)
                _barcodeState.value = AiActionState.Error(e.localizedMessage ?: "Barcode lookup failed.")
            }
        }
    }

    fun resetBarcodeState() {
        _barcodeState.value = AiActionState.Idle
    }

    // AI Personalized Insights
    fun generateNutritionalInsights() {
        viewModelScope.launch {
            _insightsState.value = AiActionState.Loading("Generating personalized nutrition insights with Gemini...")
            try {
                val logs = currentLogs.value
                val water = currentWaterLog.value.intakeMl
                val result = repository.getPersonalizedInsights(_selectedDate.value, logs, water)
                result.fold(
                    onSuccess = { insight ->
                        _insightsState.value = AiActionState.Success(insight)
                    },
                    onFailure = { err ->
                        _insightsState.value = AiActionState.Error(err.localizedMessage ?: "Failed to generate nutritional insights.")
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Insights error", e)
                _insightsState.value = AiActionState.Error(e.localizedMessage ?: "Failed to generate insights.")
            }
        }
    }

    // Google Drive Backup & Sync
    fun getBackupFileName(): String = repository.getBackupFileName()

    fun exportBackupToUri(uri: Uri) {
        viewModelScope.launch {
            _syncState.value = AiActionState.Loading("Backing up & linking Google Drive file for Wi-Fi Auto-Sync...")
            try {
                val result = repository.exportBackup(uri)
                result.fold(
                    onSuccess = {
                        _syncState.value = AiActionState.Success("Backed up & linked to Google Drive! Future entries will auto-sync to this file whenever on Wi-Fi.")
                    },
                    onFailure = { err ->
                        _syncState.value = AiActionState.Error(err.localizedMessage ?: "Backup failed.")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Export error", e)
                _syncState.value = AiActionState.Error(e.localizedMessage ?: "Export failed.")
            }
        }
    }

    fun importBackupFromUri(uri: Uri) {
        viewModelScope.launch {
            _syncState.value = AiActionState.Loading("Restoring and syncing nutrition data...")
            try {
                val result = repository.importBackup(uri)
                result.fold(
                    onSuccess = { data ->
                        _syncState.value = AiActionState.Success("Synced ${data.foodLogs.size} logs, ${data.foodItems.size} food items, and linked file for Wi-Fi Auto-Sync!")
                    },
                    onFailure = { err ->
                        _syncState.value = AiActionState.Error(err.localizedMessage ?: "Import / sync failed.")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Import error", e)
                _syncState.value = AiActionState.Error(e.localizedMessage ?: "Import failed.")
            }
        }
    }

    fun resetSyncState() {
        _syncState.value = AiActionState.Idle
    }

    private fun getTodayDateString(): String = DATE_FORMAT.format(Date())
    private fun getTodayDisplayString(): String = DISPLAY_DATE_FORMAT.format(Date())
}
