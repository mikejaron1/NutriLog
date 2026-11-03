package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DailyGoal
import com.example.data.model.DailyWaterLog
import com.example.data.model.FoodItem
import com.example.data.model.FoodLogEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_items ORDER BY timesLogged DESC, createdAt DESC")
    fun getAllFoodItems(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items WHERE isFavorite = 1 ORDER BY timesLogged DESC")
    fun getFavoriteFoodItems(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items ORDER BY createdAt DESC LIMIT 20")
    fun getRecentFoodItems(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items WHERE barcode = :barcode LIMIT 1")
    suspend fun getFoodByBarcode(barcode: String): FoodItem?

    @Query("SELECT * FROM food_items WHERE name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%'")
    fun searchFoodItems(query: String): Flow<List<FoodItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodItem(item: FoodItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoodItems(items: List<FoodItem>)

    @Update
    suspend fun updateFoodItem(item: FoodItem)

    @Delete
    suspend fun deleteFoodItem(item: FoodItem)

    @Query("UPDATE food_items SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE food_items SET timesLogged = timesLogged + 1 WHERE id = :id")
    suspend fun incrementTimesLogged(id: String)
}

@Dao
interface FoodLogDao {
    @Query("SELECT * FROM food_log_entries WHERE date = :date ORDER BY loggedAt ASC")
    fun getLogsForDate(date: String): Flow<List<FoodLogEntry>>

    @Query("SELECT * FROM food_log_entries ORDER BY loggedAt DESC")
    fun getAllLogs(): Flow<List<FoodLogEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(entry: FoodLogEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(entries: List<FoodLogEntry>)

    @Update
    suspend fun updateLog(entry: FoodLogEntry)

    @Delete
    suspend fun deleteLog(entry: FoodLogEntry)

    @Query("DELETE FROM food_log_entries WHERE id = :id")
    suspend fun deleteLogById(id: String)

    @Query("SELECT * FROM food_log_entries WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getLogsInRange(startDate: String, endDate: String): Flow<List<FoodLogEntry>>

    @Query("DELETE FROM food_log_entries")
    suspend fun clearAllLogs()

    @Query("DELETE FROM food_log_entries WHERE id LIKE 'sample_%' OR id LIKE 'seed_%' OR id LIKE 'demo_%' OR id LIKE 'test_%'")
    suspend fun deleteLegacySampleLogs()
}

@Dao
interface WaterLogDao {
    @Query("SELECT * FROM daily_water_logs WHERE date = :date LIMIT 1")
    fun getWaterLogForDate(date: String): Flow<DailyWaterLog?>

    @Query("SELECT * FROM daily_water_logs")
    fun getAllWaterLogs(): Flow<List<DailyWaterLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(log: DailyWaterLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<DailyWaterLog>)

    @Query("DELETE FROM daily_water_logs")
    suspend fun clearAllWaterLogs()

    @Query("DELETE FROM daily_water_logs WHERE date LIKE 'sample_%'")
    suspend fun deleteLegacySampleWaterLogs()
}

@Dao
interface DailyGoalDao {
    @Query("SELECT * FROM daily_goals WHERE id = 1 LIMIT 1")
    fun getGoal(): Flow<DailyGoal?>

    @Query("SELECT * FROM daily_goals WHERE id = 1 LIMIT 1")
    suspend fun getGoalSnapshot(): DailyGoal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setGoal(goal: DailyGoal)
}
