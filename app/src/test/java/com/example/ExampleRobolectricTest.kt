package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.FoodLogEntry
import com.example.data.model.MealType
import com.example.data.remote.RecipeAnalysisResult
import com.example.data.sync.BackupSyncManager
import com.example.data.sync.SyncQueueStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("NutriLog", appName)
    }

    @Test
    fun `test macro and calorie summation`() {
        val entry1 = FoodLogEntry(
            date = "2026-11-02",
            mealType = MealType.BREAKFAST,
            foodName = "Rolled Oats",
            calories = 150.0,
            proteinGrams = 5.0,
            carbsGrams = 27.0,
            fatGrams = 3.0,
            fiberGrams = 4.0
        )
        val entry2 = FoodLogEntry(
            date = "2026-11-02",
            mealType = MealType.BREAKFAST,
            foodName = "Whey Protein",
            calories = 120.0,
            proteinGrams = 25.0,
            carbsGrams = 2.0,
            fatGrams = 1.0,
            fiberGrams = 0.5
        )

        val totalCalories = entry1.calories + entry2.calories
        val totalProtein = entry1.proteinGrams + entry2.proteinGrams
        val totalCarbs = entry1.carbsGrams + entry2.carbsGrams
        val totalFat = entry1.fatGrams + entry2.fatGrams
        val totalFiber = entry1.fiberGrams + entry2.fiberGrams

        assertEquals(270.0, totalCalories, 0.01)
        assertEquals(30.0, totalProtein, 0.01)
        assertEquals(29.0, totalCarbs, 0.01)
        assertEquals(4.0, totalFat, 0.01)
        assertEquals(4.5, totalFiber, 0.01)
    }

    @Test
    fun `test nutrition label gram portion calculation`() {
        val labelResult = RecipeAnalysisResult(
            dishName = "Organic Protein Granola",
            totalServings = 8.0,
            servingSizeDescription = "2/3 cup (55g)",
            servingWeightGrams = 55.0,
            detectedIngredients = listOf("Whole grain oats", "Whey protein", "Honey"),
            perServingCalories = 240.0,
            perServingProteinGrams = 12.0,
            perServingCarbsGrams = 32.0,
            perServingFatGrams = 8.0,
            perServingFiberGrams = 5.0,
            perServingSugarGrams = 6.0,
            perServingSodiumMg = 140.0,
            isNutritionLabel = true,
            macroSummary = "240 kcal per 55g serving",
            healthRatingNotes = "High fiber and protein"
        )

        val customEatenGrams = 110.0 // 2x serving weight
        val multiplier = customEatenGrams / labelResult.servingWeightGrams

        assertTrue(labelResult.isNutritionLabel)
        assertEquals(2.0, multiplier, 0.01)
        assertEquals(480.0, labelResult.perServingCalories * multiplier, 0.01)
        assertEquals(24.0, labelResult.perServingProteinGrams * multiplier, 0.01)
        assertEquals(64.0, labelResult.perServingCarbsGrams * multiplier, 0.01)
        assertEquals(16.0, labelResult.perServingFatGrams * multiplier, 0.01)
    }

    @Test
    fun `test wifi auto sync queues when offline and uploads when wifi joins`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val syncManager = BackupSyncManager(context, db)
        syncManager.setAutoSyncEnabled(true)

        // 1. Log an entry while ON Wi-Fi -> should immediately upload
        db.foodLogDao().insertLog(
            FoodLogEntry(
                id = "log_wifi_1",
                date = "2026-11-03",
                mealType = MealType.BREAKFAST,
                foodName = "Avocado Toast",
                calories = 320.0,
                proteinGrams = 10.0,
                carbsGrams = 28.0,
                fatGrams = 18.0,
                fiberGrams = 7.0
            )
        )
        val wifiResult = syncManager.onEntryRecorded("Logged Avocado Toast", isOnWifi = true)
        assertTrue(wifiResult.getOrDefault(false))
        assertEquals(0, syncManager.autoSyncState.value.pendingEntriesCount)
        assertEquals(SyncQueueStatus.SYNCED_ON_WIFI, syncManager.autoSyncState.value.status)
        assertTrue(syncManager.getAutoSyncMirrorFile().exists())
        assertTrue(syncManager.getAutoSyncMirrorFile().readText().contains("Avocado Toast"))

        // 2. Log two entries while NOT on Wi-Fi -> should queue and wait for Wi-Fi
        db.foodLogDao().insertLog(
            FoodLogEntry(
                id = "log_offline_2",
                date = "2026-11-03",
                mealType = MealType.LUNCH,
                foodName = "Grilled Salmon Salad",
                calories = 540.0,
                proteinGrams = 42.0,
                carbsGrams = 18.0,
                fatGrams = 32.0,
                fiberGrams = 6.0
            )
        )
        val offlineResult1 = syncManager.onEntryRecorded("Logged Grilled Salmon Salad", isOnWifi = false)
        assertFalse(offlineResult1.getOrDefault(true))
        assertEquals(1, syncManager.autoSyncState.value.pendingEntriesCount)
        assertEquals(SyncQueueStatus.WAITING_FOR_WIFI, syncManager.autoSyncState.value.status)

        val offlineResult2 = syncManager.onEntryRecorded("Logged water (+250ml)", isOnWifi = false)
        assertFalse(offlineResult2.getOrDefault(true))
        assertEquals(2, syncManager.autoSyncState.value.pendingEntriesCount)
        assertEquals(SyncQueueStatus.WAITING_FOR_WIFI, syncManager.autoSyncState.value.status)

        // 3. Rejoin Wi-Fi -> queued entries automatically upload to Google Drive
        val flushResult = syncManager.flushQueuedSyncOnWifiJoined()
        assertTrue(flushResult.getOrDefault(false))
        assertEquals(0, syncManager.autoSyncState.value.pendingEntriesCount)
        assertEquals(SyncQueueStatus.SYNCED_ON_WIFI, syncManager.autoSyncState.value.status)
        assertTrue(syncManager.getAutoSyncMirrorFile().readText().contains("Grilled Salmon Salad"))

        db.close()
    }
}
