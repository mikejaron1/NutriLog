package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.data.model.BackupData
import com.example.data.model.DailyGoal
import com.example.data.model.DailyWaterLog
import com.example.data.model.FoodItem
import com.example.data.model.FoodLogEntry
import com.example.data.model.MacroTotals
import com.example.data.model.MealType
import com.example.data.remote.AiNutritionalInsight
import com.example.data.remote.GeminiNutritionService
import com.example.data.remote.OpenFoodFactsService
import com.example.data.remote.RecipeAnalysisResult
import com.example.data.sync.AutoSyncUiState
import com.example.data.sync.BackupSyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class NutriRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context),
    private val openFoodFactsService: OpenFoodFactsService = OpenFoodFactsService(),
    private val geminiService: GeminiNutritionService = GeminiNutritionService(),
    private val backupSyncManager: BackupSyncManager = BackupSyncManager(context, database)
) {
    private val foodDao = database.foodDao()
    private val foodLogDao = database.foodLogDao()
    private val waterLogDao = database.waterLogDao()
    private val dailyGoalDao = database.dailyGoalDao()

    // Foods
    val allFoodItems: Flow<List<FoodItem>> = foodDao.getAllFoodItems()
    val favoriteFoodItems: Flow<List<FoodItem>> = foodDao.getFavoriteFoodItems()
    val recentFoodItems: Flow<List<FoodItem>> = foodDao.getRecentFoodItems()

    // All logs & water for History charts
    val allFoodLogs: Flow<List<FoodLogEntry>> = foodLogDao.getAllLogs()
    val allWaterLogs: Flow<List<DailyWaterLog>> = waterLogDao.getAllWaterLogs()

    // Google Drive Wi-Fi Auto-Sync State
    val autoSyncState: StateFlow<AutoSyncUiState> = backupSyncManager.autoSyncState

    fun searchFoods(query: String): Flow<List<FoodItem>> = foodDao.searchFoodItems(query)

    suspend fun saveFoodItem(item: FoodItem) = foodDao.insertFoodItem(item)
    suspend fun updateFavorite(id: String, isFavorite: Boolean) = foodDao.updateFavorite(id, isFavorite)
    suspend fun deleteFoodItem(item: FoodItem) = foodDao.deleteFoodItem(item)

    // Daily Goals
    val dailyGoal: Flow<DailyGoal> = dailyGoalDao.getGoal().map { it ?: DailyGoal() }
    suspend fun updateDailyGoal(goal: DailyGoal) = dailyGoalDao.setGoal(goal)

    // Logs
    fun getLogsForDate(date: String): Flow<List<FoodLogEntry>> = foodLogDao.getLogsForDate(date)

    fun getMacroTotalsForDate(date: String): Flow<MacroTotals> = getLogsForDate(date).map { list ->
        MacroTotals(
            calories = list.sumOf { it.calories },
            protein = list.sumOf { it.proteinGrams },
            carbs = list.sumOf { it.carbsGrams },
            fat = list.sumOf { it.fatGrams },
            fiber = list.sumOf { it.fiberGrams }
        )
    }

    suspend fun logFood(
        date: String,
        mealType: MealType,
        foodItem: FoodItem,
        numberOfServings: Double
    ) {
        val entry = FoodLogEntry(
            date = date,
            mealType = mealType,
            foodName = foodItem.name,
            brand = foodItem.brand,
            numberOfServings = numberOfServings,
            servingSize = foodItem.servingSize,
            servingUnit = foodItem.servingUnit,
            calories = foodItem.calories * numberOfServings,
            proteinGrams = foodItem.proteinGrams * numberOfServings,
            carbsGrams = foodItem.carbsGrams * numberOfServings,
            fatGrams = foodItem.fatGrams * numberOfServings,
            fiberGrams = foodItem.fiberGrams * numberOfServings,
            foodItemId = foodItem.id
        )
        foodLogDao.insertLog(entry)
        foodDao.incrementTimesLogged(foodItem.id)
    }

    suspend fun logDirectEntry(entry: FoodLogEntry) {
        foodLogDao.insertLog(entry)
    }

    suspend fun deleteLogEntry(id: String) {
        foodLogDao.deleteLogById(id)
    }

    // Water
    fun getWaterLog(date: String): Flow<DailyWaterLog> = waterLogDao.getWaterLogForDate(date).map {
        it ?: DailyWaterLog(date = date, intakeMl = 0)
    }

    suspend fun setWater(date: String, totalMl: Int) {
        waterLogDao.insertOrUpdate(DailyWaterLog(date = date, intakeMl = totalMl))
    }

    // Barcode Lookup: first local DB, then OpenFoodFacts, then Gemini fallback
    suspend fun lookupBarcode(barcode: String): Result<FoodItem> {
        val local = foodDao.getFoodByBarcode(barcode)
        if (local != null) {
            return Result.success(local)
        }

        val remoteProduct = openFoodFactsService.getProductByBarcode(barcode)
        if (remoteProduct != null) {
            foodDao.insertFoodItem(remoteProduct)
            return Result.success(remoteProduct)
        }

        val geminiResult = geminiService.estimateFoodFromDescription("Product with barcode $barcode. Identify common food for this UPC and provide nutrition.")
        return if (geminiResult.isSuccess) {
            val est = geminiResult.getOrThrow()
            val item = FoodItem(
                name = est.foodName,
                brand = "Scanned Item",
                servingSize = est.estimatedGrams,
                servingUnit = "g",
                calories = est.calories,
                proteinGrams = est.proteinGrams,
                carbsGrams = est.carbsGrams,
                fatGrams = est.fatGrams,
                fiberGrams = est.fiberGrams,
                barcode = barcode,
                source = "BARCODE"
            )
            foodDao.insertFoodItem(item)
            Result.success(item)
        } else {
            Result.failure(Exception("Product not found for barcode $barcode."))
        }
    }

    // Gemini Food Description Search
    suspend fun searchFoodWithAi(description: String): Result<FoodItem> {
        val result = geminiService.estimateFoodFromDescription(description)
        return result.map { est ->
            val item = FoodItem(
                name = est.foodName,
                brand = est.servingDescription,
                servingSize = est.estimatedGrams,
                servingUnit = "g",
                calories = est.calories,
                proteinGrams = est.proteinGrams,
                carbsGrams = est.carbsGrams,
                fatGrams = est.fatGrams,
                fiberGrams = est.fiberGrams,
                source = "GEMINI_SEARCH"
            )
            foodDao.insertFoodItem(item)
            item
        }
    }

    // Gemini Recipe / Ingredient Photo / Nutrition Label Analysis
    suspend fun analyzeRecipeOrPhoto(
        recipeText: String?,
        bitmap: Bitmap?,
        isLabelModeHint: Boolean = false
    ): Result<RecipeAnalysisResult> {
        return geminiService.analyzeRecipeOrIngredients(recipeText, bitmap, isLabelModeHint)
    }

    // Gemini Personalized Insights
    suspend fun getPersonalizedInsights(
        date: String,
        currentLogs: List<FoodLogEntry>,
        currentWaterMl: Int
    ): Result<AiNutritionalInsight> {
        val goal = dailyGoalDao.getGoalSnapshot() ?: DailyGoal()
        return geminiService.generateNutritionalInsights(goal, currentLogs, currentWaterMl)
    }



    // Google Drive Backup & Wi-Fi Auto-Sync
    fun getBackupFileName(): String = BackupSyncManager.generateBackupFileName()
    suspend fun exportBackup(uri: Uri): Result<Int> = backupSyncManager.exportToUri(uri)
    suspend fun importBackup(uri: Uri): Result<BackupData> = backupSyncManager.importFromUri(uri)
    fun setAutoSyncEnabled(enabled: Boolean) = backupSyncManager.setAutoSyncEnabled(enabled)
    fun updateWifiConnectivityState(isWifiAvailable: Boolean, isHoldMode: Boolean) =
        backupSyncManager.updateWifiConnectivityState(isWifiAvailable, isHoldMode)
    fun linkGoogleDriveDocumentUri(uri: Uri) = backupSyncManager.linkGoogleDriveDocumentUri(uri)
    suspend fun onEntryRecorded(
        entryDescription: String,
        isOnWifi: Boolean,
        isWifiHoldMode: Boolean = false
    ): Result<Boolean> = backupSyncManager.onEntryRecorded(entryDescription, isOnWifi, isWifiHoldMode)
    suspend fun flushQueuedSyncOnWifiJoined(): Result<Boolean> = backupSyncManager.flushQueuedSyncOnWifiJoined()
    suspend fun performManualOrAutoDriveSync(reason: String): Result<Boolean> =
        backupSyncManager.performAutoSyncToGoogleDrive(reason)
    fun getAutoSyncMirrorFile(): File = backupSyncManager.getAutoSyncMirrorFile()
}
