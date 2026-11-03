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

    // Populate past days sample history if empty on those days so user can explore multi-day charts
    suspend fun seedPastDaysHistoryIfEmpty() {
        val existingLogs = foodLogDao.getAllLogs().first()
        val existingDates = existingLogs.map { it.date }.toSet()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        val entriesToInsert = mutableListOf<FoodLogEntry>()
        val waterToInsert = mutableListOf<DailyWaterLog>()

        // Daily profiles for past 6 days
        val dailyProfiles = listOf(
            // 1 day ago
            listOf(
                Triple(MealType.BREAKFAST, "Rolled Oats & Whey", listOf(380.0, 32.0, 48.0, 6.5, 6.0)),
                Triple(MealType.LUNCH, "Grilled Chicken & Brown Rice Bowl", listOf(560.0, 48.0, 58.0, 12.0, 5.5)),
                Triple(MealType.DINNER, "Baked Salmon & Roasted Asparagus", listOf(640.0, 44.0, 38.0, 28.0, 7.0)),
                Triple(MealType.SNACK, "Greek Yogurt & Berries", listOf(210.0, 20.0, 24.0, 2.5, 4.0))
            ) to 2400,
            // 2 days ago
            listOf(
                Triple(MealType.BREAKFAST, "3 Whole Eggs & Avocado Toast", listOf(490.0, 24.0, 34.0, 28.0, 8.0)),
                Triple(MealType.LUNCH, "Turkey Quinoa Salad", listOf(520.0, 42.0, 46.0, 16.0, 7.5)),
                Triple(MealType.DINNER, "Lean Beef Stir-Fry with Jasmine Rice", listOf(710.0, 52.0, 72.0, 21.0, 5.0)),
                Triple(MealType.SNACK, "Raw Almonds & Apple", listOf(255.0, 7.0, 29.0, 14.5, 6.5))
            ) to 2600,
            // 3 days ago
            listOf(
                Triple(MealType.BREAKFAST, "Protein Smoothie Bowl", listOf(420.0, 35.0, 52.0, 8.0, 7.0)),
                Triple(MealType.LUNCH, "Tuna Poke Bowl", listOf(590.0, 41.0, 68.0, 15.0, 6.0)),
                Triple(MealType.DINNER, "Herb Chicken Breast & Sweet Potato", listOf(610.0, 54.0, 56.0, 14.0, 8.0)),
                Triple(MealType.SNACK, "Cottage Cheese & Pineapple", listOf(190.0, 18.0, 21.0, 3.0, 1.5))
            ) to 2250,
            // 4 days ago
            listOf(
                Triple(MealType.BREAKFAST, "Overnight Oats with Chia Seeds", listOf(395.0, 22.0, 54.0, 11.0, 9.5)),
                Triple(MealType.LUNCH, "Mediterranean Chicken Wrap", listOf(630.0, 45.0, 59.0, 22.0, 6.0)),
                Triple(MealType.DINNER, "Shrimp Pasta Primavera", listOf(680.0, 43.0, 78.0, 18.0, 7.0)),
                Triple(MealType.SNACK, "Whey Protein Isolate Shake", listOf(160.0, 28.0, 6.0, 2.0, 1.0))
            ) to 2500,
            // 5 days ago
            listOf(
                Triple(MealType.BREAKFAST, "Greek Yogurt Parfait & Granola", listOf(360.0, 25.0, 46.0, 8.0, 4.5)),
                Triple(MealType.LUNCH, "Grilled Steak Fajita Bowl", listOf(650.0, 49.0, 54.0, 24.0, 8.5)),
                Triple(MealType.DINNER, "Baked Cod, Quinoa & Broccoli", listOf(510.0, 46.0, 48.0, 12.0, 7.0)),
                Triple(MealType.SNACK, "Dark Chocolate & Mixed Nuts", listOf(230.0, 6.0, 18.0, 16.0, 3.5))
            ) to 2100,
            // 6 days ago
            listOf(
                Triple(MealType.BREAKFAST, "Veggie Omelet & Whole Grain Toast", listOf(440.0, 29.0, 32.0, 21.0, 5.0)),
                Triple(MealType.LUNCH, "Lentil Soup & Grilled Chicken Salad", listOf(550.0, 47.0, 52.0, 14.0, 11.0)),
                Triple(MealType.DINNER, "Teriyaki Tofu & Edamame Rice Bowl", listOf(620.0, 34.0, 76.0, 18.0, 9.0)),
                Triple(MealType.SNACK, "Protein Bar", listOf(210.0, 20.0, 22.0, 7.0, 5.0))
            ) to 2750
        )

        dailyProfiles.forEachIndexed { idx, (meals, waterMl) ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -(idx + 1))
            val dateStr = dateFormat.format(cal.time)
            if (!existingDates.contains(dateStr)) {
                meals.forEach { (mealType, name, macros) ->
                    entriesToInsert.add(
                        FoodLogEntry(
                            id = "history_${dateStr}_${mealType.name}",
                            date = dateStr,
                            mealType = mealType,
                            foodName = name,
                            brand = "Logged Meal",
                            numberOfServings = 1.0,
                            servingSize = 1.0,
                            servingUnit = "serving",
                            calories = macros[0],
                            proteinGrams = macros[1],
                            carbsGrams = macros[2],
                            fatGrams = macros[3],
                            fiberGrams = macros[4],
                            loggedAt = cal.timeInMillis
                        )
                    )
                }
                waterToInsert.add(DailyWaterLog(date = dateStr, intakeMl = waterMl))
            }
        }

        if (entriesToInsert.isNotEmpty()) {
            foodLogDao.insertLogs(entriesToInsert)
        }
        if (waterToInsert.isNotEmpty()) {
            waterLogDao.insertAll(waterToInsert)
        }
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
