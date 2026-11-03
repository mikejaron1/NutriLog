package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.DailyGoal
import com.example.data.model.DailyWaterLog
import com.example.data.model.FoodItem
import com.example.data.model.FoodLogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        FoodItem::class,
        FoodLogEntry::class,
        DailyWaterLog::class,
        DailyGoal::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun foodLogDao(): FoodLogDao
    abstract fun waterLogDao(): WaterLogDao
    abstract fun dailyGoalDao(): DailyGoalDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nutrilog_database"
                )
                    .addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    INSTANCE?.let { database ->
                        // Pre-populate default goals
                        database.dailyGoalDao().setGoal(DailyGoal())

                        // Pre-populate popular staple foods
                        database.foodDao().insertFoodItems(defaultFoodItems)
                    }
                }
            }
        }

        val defaultFoodItems = listOf(
            FoodItem(
                id = "staple_rolled_oats",
                name = "Rolled Oats (Raw)",
                brand = "Staple Foods",
                servingSize = 40.0,
                servingUnit = "g",
                calories = 150.0,
                proteinGrams = 5.0,
                carbsGrams = 27.0,
                fatGrams = 3.0,
                fiberGrams = 4.0,
                isFavorite = true,
                source = "MANUAL"
            ),
            FoodItem(
                id = "staple_chicken_breast",
                name = "Grilled Chicken Breast",
                brand = "Staple Foods",
                servingSize = 120.0,
                servingUnit = "g",
                calories = 198.0,
                proteinGrams = 37.0,
                carbsGrams = 0.0,
                fatGrams = 4.3,
                fiberGrams = 0.0,
                isFavorite = true,
                source = "MANUAL"
            ),
            FoodItem(
                id = "staple_greek_yogurt",
                name = "Greek Yogurt 0%",
                brand = "Plain",
                servingSize = 170.0,
                servingUnit = "g",
                calories = 100.0,
                proteinGrams = 17.0,
                carbsGrams = 6.0,
                fatGrams = 0.7,
                fiberGrams = 0.0,
                isFavorite = true,
                source = "MANUAL"
            ),
            FoodItem(
                id = "staple_banana",
                name = "Medium Banana",
                brand = "Fresh Produce",
                servingSize = 118.0,
                servingUnit = "g",
                calories = 105.0,
                proteinGrams = 1.3,
                carbsGrams = 27.0,
                fatGrams = 0.4,
                fiberGrams = 3.1,
                isFavorite = true,
                source = "MANUAL"
            ),
            FoodItem(
                id = "staple_whole_egg",
                name = "Large Whole Egg",
                brand = "Farm Fresh",
                servingSize = 50.0,
                servingUnit = "g",
                calories = 72.0,
                proteinGrams = 6.3,
                carbsGrams = 0.4,
                fatGrams = 4.8,
                fiberGrams = 0.0,
                isFavorite = true,
                source = "MANUAL"
            ),
            FoodItem(
                id = "staple_brown_rice",
                name = "Cooked Brown Rice",
                brand = "Grain Select",
                servingSize = 150.0,
                servingUnit = "g",
                calories = 168.0,
                proteinGrams = 3.8,
                carbsGrams = 35.0,
                fatGrams = 1.4,
                fiberGrams = 2.4,
                isFavorite = false,
                source = "MANUAL"
            ),
            FoodItem(
                id = "staple_avocado",
                name = "Avocado Hass",
                brand = "Fresh Produce",
                servingSize = 100.0,
                servingUnit = "g",
                calories = 160.0,
                proteinGrams = 2.0,
                carbsGrams = 8.5,
                fatGrams = 14.7,
                fiberGrams = 6.7,
                isFavorite = true,
                source = "MANUAL"
            ),
            FoodItem(
                id = "staple_salmon",
                name = "Baked Salmon Fillet",
                brand = "Wild Caught",
                servingSize = 150.0,
                servingUnit = "g",
                calories = 312.0,
                proteinGrams = 34.0,
                carbsGrams = 0.0,
                fatGrams = 18.5,
                fiberGrams = 0.0,
                isFavorite = false,
                source = "MANUAL"
            ),
            FoodItem(
                id = "staple_whey_protein",
                name = "Whey Protein Isolate",
                brand = "NutriLog Fit",
                servingSize = 30.0,
                servingUnit = "g",
                calories = 120.0,
                proteinGrams = 25.0,
                carbsGrams = 2.0,
                fatGrams = 1.0,
                fiberGrams = 0.5,
                isFavorite = true,
                source = "MANUAL"
            ),
            FoodItem(
                id = "staple_almonds",
                name = "Raw Almonds",
                brand = "Whole Nuts",
                servingSize = 28.0,
                servingUnit = "g",
                calories = 164.0,
                proteinGrams = 6.0,
                carbsGrams = 6.1,
                fatGrams = 14.2,
                fiberGrams = 3.5,
                isFavorite = false,
                source = "MANUAL"
            )
        )
    }
}
