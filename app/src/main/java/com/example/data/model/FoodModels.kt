package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "food_items")
data class FoodItem(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val brand: String = "",
    val servingSize: Double = 100.0,
    val servingUnit: String = "g",
    val calories: Double,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val fiberGrams: Double = 0.0,
    val barcode: String? = null,
    val isFavorite: Boolean = false,
    val source: String = "MANUAL", // MANUAL, BARCODE, GEMINI_SEARCH, RECIPE_SCAN
    val createdAt: Long = System.currentTimeMillis(),
    val timesLogged: Int = 1
)

@Entity(tableName = "food_log_entries")
data class FoodLogEntry(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val date: String, // "YYYY-MM-DD"
    val mealType: MealType,
    val foodName: String,
    val brand: String = "",
    val numberOfServings: Double = 1.0,
    val servingSize: Double = 100.0,
    val servingUnit: String = "g",
    val calories: Double,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val fiberGrams: Double = 0.0,
    val foodItemId: String? = null,
    val loggedAt: Long = System.currentTimeMillis()
)

enum class MealType(val displayName: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snacks")
}

@Entity(tableName = "daily_water_logs")
data class DailyWaterLog(
    @PrimaryKey
    val date: String, // "YYYY-MM-DD"
    val intakeMl: Int = 0
)

@Entity(tableName = "daily_goals")
data class DailyGoal(
    @PrimaryKey
    val id: Int = 1,
    val calorieTarget: Int = 2000,
    val proteinTargetGrams: Int = 140,
    val carbsTargetGrams: Int = 220,
    val fatTargetGrams: Int = 65,
    val fiberTargetGrams: Int = 30,
    val waterTargetMl: Int = 2500
)

data class MacroTotals(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double = 0.0
)

data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val goals: DailyGoal,
    val foodItems: List<FoodItem>,
    val foodLogs: List<FoodLogEntry>,
    val waterLogs: List<DailyWaterLog>
)
