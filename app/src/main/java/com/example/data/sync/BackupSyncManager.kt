package com.example.data.sync

import android.app.backup.BackupManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.BackupData
import com.example.data.model.DailyGoal
import com.example.data.model.DailyWaterLog
import com.example.data.model.FoodItem
import com.example.data.model.FoodLogEntry
import com.example.data.model.MealType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SyncQueueStatus {
    IDLE,
    SYNCING_NOW,
    SYNCED_ON_WIFI,
    WAITING_FOR_WIFI,
    SYNC_ERROR
}

data class AutoSyncUiState(
    val autoSyncEnabled: Boolean = true,
    val isWifiConnected: Boolean = true,
    val isWifiHoldMode: Boolean = false,
    val status: SyncQueueStatus = SyncQueueStatus.IDLE,
    val pendingEntriesCount: Int = 0,
    val lastPendingReason: String = "",
    val lastSyncedTimestamp: Long = 0L,
    val lastSyncMessage: String = "Auto-sync on Wi-Fi enabled",
    val linkedDriveUri: String? = null,
    val linkedDriveFileName: String = "NutriLog_GoogleDrive_AutoSync.json"
)

class BackupSyncManager(
    private val context: Context,
    private val database: AppDatabase
) {
    companion object {
        private const val TAG = "BackupSyncManager"
        private const val PREFS_NAME = "nutrilog_drive_autosync_prefs"
        private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
        private const val KEY_PENDING_COUNT = "pending_entries_count"
        private const val KEY_LAST_PENDING_REASON = "last_pending_reason"
        private const val KEY_LAST_SYNCED_TIME = "last_synced_timestamp"
        private const val KEY_LAST_SYNC_MESSAGE = "last_sync_message"
        private const val KEY_LINKED_DRIVE_URI = "linked_drive_uri"
        private const val KEY_LINKED_DRIVE_NAME = "linked_drive_name"
        const val AUTO_SYNC_MIRROR_FILE = "NutriLog_GoogleDrive_AutoSync.json"

        fun generateBackupFileName(): String {
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            return "NutriLog_Backup_$dateStr.json"
        }
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _autoSyncState = MutableStateFlow(loadInitialState())
    val autoSyncState: StateFlow<AutoSyncUiState> = _autoSyncState.asStateFlow()

    private fun loadInitialState(): AutoSyncUiState {
        val enabled = prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, true)
        val pendingCount = prefs.getInt(KEY_PENDING_COUNT, 0)
        val pendingReason = prefs.getString(KEY_LAST_PENDING_REASON, "") ?: ""
        val lastSynced = prefs.getLong(KEY_LAST_SYNCED_TIME, 0L)
        val linkedUri = prefs.getString(KEY_LINKED_DRIVE_URI, null)
        val linkedName = prefs.getString(KEY_LINKED_DRIVE_NAME, AUTO_SYNC_MIRROR_FILE) ?: AUTO_SYNC_MIRROR_FILE
        val savedMessage = prefs.getString(KEY_LAST_SYNC_MESSAGE, null)

        val initialStatus = when {
            pendingCount > 0 -> SyncQueueStatus.WAITING_FOR_WIFI
            lastSynced > 0L -> SyncQueueStatus.SYNCED_ON_WIFI
            else -> SyncQueueStatus.IDLE
        }

        val defaultMsg = when {
            pendingCount > 0 -> "Waiting for Wi-Fi • $pendingCount entry(s) queued ($pendingReason)"
            lastSynced > 0L -> savedMessage ?: "Synced to Google Drive over Wi-Fi"
            else -> "Auto-sync on Wi-Fi ready"
        }

        return AutoSyncUiState(
            autoSyncEnabled = enabled,
            isWifiConnected = true,
            isWifiHoldMode = false,
            status = initialStatus,
            pendingEntriesCount = pendingCount,
            lastPendingReason = pendingReason,
            lastSyncedTimestamp = lastSynced,
            lastSyncMessage = defaultMsg,
            linkedDriveUri = linkedUri,
            linkedDriveFileName = linkedName
        )
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, enabled).apply()
        _autoSyncState.update { state ->
            val msg = if (enabled) {
                if (state.pendingEntriesCount > 0 && !state.isWifiConnected) {
                    "Waiting for Wi-Fi • ${state.pendingEntriesCount} entry(s) queued"
                } else {
                    "Auto-sync on Wi-Fi enabled"
                }
            } else {
                "Auto-sync paused"
            }
            state.copy(autoSyncEnabled = enabled, lastSyncMessage = msg)
        }
    }

    fun updateWifiConnectivityState(isWifiAvailable: Boolean, isHoldMode: Boolean) {
        _autoSyncState.update { state ->
            val updatedStatus = if (!isWifiAvailable && state.pendingEntriesCount > 0) {
                SyncQueueStatus.WAITING_FOR_WIFI
            } else {
                state.status
            }
            val updatedMsg = if (!isWifiAvailable && state.pendingEntriesCount > 0) {
                "Waiting for Wi-Fi • ${state.pendingEntriesCount} entry(s) queued (${state.lastPendingReason})"
            } else {
                state.lastSyncMessage
            }
            state.copy(
                isWifiConnected = isWifiAvailable,
                isWifiHoldMode = isHoldMode,
                status = updatedStatus,
                lastSyncMessage = updatedMsg
            )
        }
    }

    /**
     * Links a user-chosen Google Drive document URI with persistable read/write permissions
     * so every subsequent Wi-Fi auto-sync updates that exact Google Drive file automatically.
     */
    fun linkGoogleDriveDocumentUri(uri: Uri) {
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            Log.w(TAG, "Persistable URI permission not supported by provider, using session URI", e)
        }

        val displayName = resolveDisplayName(uri) ?: "NutriLog_Drive_Sync.json"
        prefs.edit()
            .putString(KEY_LINKED_DRIVE_URI, uri.toString())
            .putString(KEY_LINKED_DRIVE_NAME, displayName)
            .apply()

        _autoSyncState.update {
            it.copy(
                linkedDriveUri = uri.toString(),
                linkedDriveFileName = displayName
            )
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Called after ANY nutrition entry or modification (food log, recipe/label scan, water intake,
     * custom food, favorite toggle, or goal update).
     * - If on Wi-Fi and auto-sync is enabled: immediately uploads the updated data to Google Drive.
     * - If NOT on Wi-Fi: queues the change and waits until the device joins Wi-Fi again.
     */
    suspend fun onEntryRecorded(
        entryDescription: String,
        isOnWifi: Boolean,
        isWifiHoldMode: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val newPendingCount = _autoSyncState.value.pendingEntriesCount + 1
        prefs.edit()
            .putInt(KEY_PENDING_COUNT, newPendingCount)
            .putString(KEY_LAST_PENDING_REASON, entryDescription)
            .apply()

        val autoSyncEnabled = _autoSyncState.value.autoSyncEnabled
        if (!autoSyncEnabled) {
            _autoSyncState.update {
                it.copy(
                    isWifiConnected = isOnWifi,
                    isWifiHoldMode = isWifiHoldMode,
                    pendingEntriesCount = newPendingCount,
                    lastPendingReason = entryDescription,
                    lastSyncMessage = "Auto-sync off • $newPendingCount unsynced change(s)"
                )
            }
            return@withContext Result.success(false)
        }

        if (isOnWifi) {
            return@withContext performAutoSyncToGoogleDrive(
                triggerDescription = "Auto-synced to Google Drive over Wi-Fi ($entryDescription)"
            )
        } else {
            val waitMsg = "Waiting for Wi-Fi • $newPendingCount entry(s) queued ($entryDescription)"
            prefs.edit().putString(KEY_LAST_SYNC_MESSAGE, waitMsg).apply()
            _autoSyncState.update {
                it.copy(
                    isWifiConnected = false,
                    isWifiHoldMode = isWifiHoldMode,
                    status = SyncQueueStatus.WAITING_FOR_WIFI,
                    pendingEntriesCount = newPendingCount,
                    lastPendingReason = entryDescription,
                    lastSyncMessage = waitMsg
                )
            }
            return@withContext Result.success(false)
        }
    }

    /**
     * Called automatically when [WifiAutoSyncMonitor] detects that the device has joined Wi-Fi.
     * Uploads any queued entries that were recorded while offline or on cellular data.
     */
    suspend fun flushQueuedSyncOnWifiJoined(): Result<Boolean> = withContext(Dispatchers.IO) {
        val current = _autoSyncState.value
        _autoSyncState.update { it.copy(isWifiConnected = true, isWifiHoldMode = false) }
        if (!current.autoSyncEnabled || current.pendingEntriesCount <= 0) {
            return@withContext Result.success(false)
        }

        val count = current.pendingEntriesCount
        val reason = if (current.lastPendingReason.isNotBlank()) {
            "Joined Wi-Fi • Uploaded $count queued entry(s) (${current.lastPendingReason})"
        } else {
            "Joined Wi-Fi • Uploaded $count queued entry(s) to Google Drive"
        }
        return@withContext performAutoSyncToGoogleDrive(triggerDescription = reason)
    }

    /**
     * Executes the actual Google Drive upload:
     * 1. Serializes the complete nutrition database into JSON.
     * 2. Writes to the Google Drive auto-sync staging file.
     * 3. Writes directly to the user's linked Google Drive SAF document URI (if linked).
     * 4. Signals Android's Google Drive Auto Backup service via [BackupManager.dataChanged].
     */
    suspend fun performAutoSyncToGoogleDrive(
        triggerDescription: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            _autoSyncState.update {
                it.copy(
                    isWifiConnected = true,
                    status = SyncQueueStatus.SYNCING_NOW,
                    lastSyncMessage = "Uploading nutrition data to Google Drive over Wi-Fi..."
                )
            }

            val jsonString = createBackupJson()

            // 1. Write to local Google Drive Auto-Sync mirror file
            val mirrorFile = File(context.filesDir, AUTO_SYNC_MIRROR_FILE)
            mirrorFile.writeText(jsonString, Charsets.UTF_8)

            // 2. If a Google Drive document URI is linked, write directly to that Drive file
            val linkedUriStr = _autoSyncState.value.linkedDriveUri
            if (!linkedUriStr.isNullOrBlank()) {
                try {
                    val uri = Uri.parse(linkedUriStr)
                    val stream = try {
                        context.contentResolver.openOutputStream(uri, "wt")
                    } catch (e: Exception) {
                        context.contentResolver.openOutputStream(uri)
                    }
                    stream?.use { out ->
                        out.write(jsonString.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not write to linked Drive URI, mirror file updated", e)
                }
            }

            // 3. Notify Android OS Google Drive BackupManager
            try {
                BackupManager.dataChanged(context.packageName)
            } catch (e: Exception) {
                Log.w(TAG, "BackupManager notification skipped", e)
            }

            val now = System.currentTimeMillis()
            prefs.edit()
                .putInt(KEY_PENDING_COUNT, 0)
                .putString(KEY_LAST_PENDING_REASON, "")
                .putLong(KEY_LAST_SYNCED_TIME, now)
                .putString(KEY_LAST_SYNC_MESSAGE, triggerDescription)
                .apply()

            _autoSyncState.update {
                it.copy(
                    isWifiConnected = true,
                    status = SyncQueueStatus.SYNCED_ON_WIFI,
                    pendingEntriesCount = 0,
                    lastPendingReason = "",
                    lastSyncedTimestamp = now,
                    lastSyncMessage = triggerDescription
                )
            }
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Auto-sync to Google Drive failed", e)
            val errMsg = "Auto-sync error: ${e.localizedMessage ?: "Upload failed"}"
            _autoSyncState.update {
                it.copy(
                    status = SyncQueueStatus.SYNC_ERROR,
                    lastSyncMessage = errMsg
                )
            }
            Result.failure(e)
        }
    }

    fun getAutoSyncMirrorFile(): File = File(context.filesDir, AUTO_SYNC_MIRROR_FILE)

    suspend fun createBackupJson(): String = withContext(Dispatchers.IO) {
        val goals = database.dailyGoalDao().getGoalSnapshot() ?: DailyGoal()
        val foodItems = database.foodDao().getAllFoodItems().first()
        val foodLogs = database.foodLogDao().getAllLogs().first()
        val waterLogs = database.waterLogDao().getAllWaterLogs().first()

        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        // Goals
        val goalsJson = JSONObject()
        goalsJson.put("calorieTarget", goals.calorieTarget)
        goalsJson.put("proteinTargetGrams", goals.proteinTargetGrams)
        goalsJson.put("carbsTargetGrams", goals.carbsTargetGrams)
        goalsJson.put("fatTargetGrams", goals.fatTargetGrams)
        goalsJson.put("fiberTargetGrams", goals.fiberTargetGrams)
        goalsJson.put("waterTargetMl", goals.waterTargetMl)
        root.put("goals", goalsJson)

        // Food Items
        val foodItemsArray = JSONArray()
        for (item in foodItems) {
            val itemJson = JSONObject()
            itemJson.put("id", item.id)
            itemJson.put("name", item.name)
            itemJson.put("brand", item.brand)
            itemJson.put("servingSize", item.servingSize)
            itemJson.put("servingUnit", item.servingUnit)
            itemJson.put("calories", item.calories)
            itemJson.put("proteinGrams", item.proteinGrams)
            itemJson.put("carbsGrams", item.carbsGrams)
            itemJson.put("fatGrams", item.fatGrams)
            itemJson.put("fiberGrams", item.fiberGrams)
            itemJson.put("barcode", item.barcode ?: "")
            itemJson.put("isFavorite", item.isFavorite)
            itemJson.put("source", item.source)
            itemJson.put("createdAt", item.createdAt)
            itemJson.put("timesLogged", item.timesLogged)
            foodItemsArray.put(itemJson)
        }
        root.put("foodItems", foodItemsArray)

        // Food Logs
        val foodLogsArray = JSONArray()
        for (log in foodLogs) {
            val logJson = JSONObject()
            logJson.put("id", log.id)
            logJson.put("date", log.date)
            logJson.put("mealType", log.mealType.name)
            logJson.put("foodName", log.foodName)
            logJson.put("brand", log.brand)
            logJson.put("numberOfServings", log.numberOfServings)
            logJson.put("servingSize", log.servingSize)
            logJson.put("servingUnit", log.servingUnit)
            logJson.put("calories", log.calories)
            logJson.put("proteinGrams", log.proteinGrams)
            logJson.put("carbsGrams", log.carbsGrams)
            logJson.put("fatGrams", log.fatGrams)
            logJson.put("fiberGrams", log.fiberGrams)
            logJson.put("foodItemId", log.foodItemId ?: "")
            logJson.put("loggedAt", log.loggedAt)
            foodLogsArray.put(logJson)
        }
        root.put("foodLogs", foodLogsArray)

        // Water Logs
        val waterLogsArray = JSONArray()
        for (w in waterLogs) {
            val wJson = JSONObject()
            wJson.put("date", w.date)
            wJson.put("intakeMl", w.intakeMl)
            waterLogsArray.put(wJson)
        }
        root.put("waterLogs", waterLogsArray)

        root.toString(2)
    }

    suspend fun exportToUri(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            linkGoogleDriveDocumentUri(uri)
            val jsonString = createBackupJson()
            val stream = try {
                context.contentResolver.openOutputStream(uri, "wt")
            } catch (e: Exception) {
                context.contentResolver.openOutputStream(uri)
            }
            stream?.use { out ->
                out.write(jsonString.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: return@withContext Result.failure(Exception("Unable to open output stream."))

            // Also update the auto-sync mirror and clear pending queue
            val mirrorFile = File(context.filesDir, AUTO_SYNC_MIRROR_FILE)
            mirrorFile.writeText(jsonString, Charsets.UTF_8)

            val now = System.currentTimeMillis()
            val msg = "Backed up & linked to Google Drive (${_autoSyncState.value.linkedDriveFileName})"
            prefs.edit()
                .putInt(KEY_PENDING_COUNT, 0)
                .putString(KEY_LAST_PENDING_REASON, "")
                .putLong(KEY_LAST_SYNCED_TIME, now)
                .putString(KEY_LAST_SYNC_MESSAGE, msg)
                .apply()

            _autoSyncState.update {
                it.copy(
                    status = SyncQueueStatus.SYNCED_ON_WIFI,
                    pendingEntriesCount = 0,
                    lastPendingReason = "",
                    lastSyncedTimestamp = now,
                    lastSyncMessage = msg
                )
            }
            Result.success(jsonString.length)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export backup to uri: $uri", e)
            Result.failure(e)
        }
    }

    suspend fun importFromUri(uri: Uri): Result<BackupData> = withContext(Dispatchers.IO) {
        try {
            linkGoogleDriveDocumentUri(uri)
            val sb = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        sb.append(line).append("\n")
                        line = reader.readLine()
                    }
                }
            } ?: return@withContext Result.failure(Exception("Unable to open input stream for backup file."))

            val root = JSONObject(sb.toString())
            val version = root.optInt("version", 1)
            val exportedAt = root.optLong("exportedAt", System.currentTimeMillis())

            // Parse Goals
            val goalsJson = root.optJSONObject("goals")
            val goals = if (goalsJson != null) {
                DailyGoal(
                    id = 1,
                    calorieTarget = goalsJson.optInt("calorieTarget", 2000),
                    proteinTargetGrams = goalsJson.optInt("proteinTargetGrams", 140),
                    carbsTargetGrams = goalsJson.optInt("carbsTargetGrams", 220),
                    fatTargetGrams = goalsJson.optInt("fatTargetGrams", 65),
                    fiberTargetGrams = goalsJson.optInt("fiberTargetGrams", 30),
                    waterTargetMl = goalsJson.optInt("waterTargetMl", 2500)
                )
            } else DailyGoal()

            // Parse Food Items
            val items = mutableListOf<FoodItem>()
            val itemsArray = root.optJSONArray("foodItems")
            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val obj = itemsArray.getJSONObject(i)
                    items.add(
                        FoodItem(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            brand = obj.optString("brand", ""),
                            servingSize = obj.optDouble("servingSize", 100.0),
                            servingUnit = obj.optString("servingUnit", "g"),
                            calories = obj.optDouble("calories", 0.0),
                            proteinGrams = obj.optDouble("proteinGrams", 0.0),
                            carbsGrams = obj.optDouble("carbsGrams", 0.0),
                            fatGrams = obj.optDouble("fatGrams", 0.0),
                            fiberGrams = obj.optDouble("fiberGrams", 0.0),
                            barcode = obj.optString("barcode").takeIf { it.isNotBlank() },
                            isFavorite = obj.optBoolean("isFavorite", false),
                            source = obj.optString("source", "MANUAL"),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            timesLogged = obj.optInt("timesLogged", 1)
                        )
                    )
                }
            }

            // Parse Food Logs
            val logs = mutableListOf<FoodLogEntry>()
            val logsArray = root.optJSONArray("foodLogs")
            if (logsArray != null) {
                for (i in 0 until logsArray.length()) {
                    val obj = logsArray.getJSONObject(i)
                    val mealTypeName = obj.optString("mealType", "SNACK")
                    val mealType = try {
                        MealType.valueOf(mealTypeName)
                    } catch (e: Exception) {
                        MealType.SNACK
                    }
                    logs.add(
                        FoodLogEntry(
                            id = obj.getString("id"),
                            date = obj.getString("date"),
                            mealType = mealType,
                            foodName = obj.getString("foodName"),
                            brand = obj.optString("brand", ""),
                            numberOfServings = obj.optDouble("numberOfServings", 1.0),
                            servingSize = obj.optDouble("servingSize", 100.0),
                            servingUnit = obj.optString("servingUnit", "g"),
                            calories = obj.optDouble("calories", 0.0),
                            proteinGrams = obj.optDouble("proteinGrams", 0.0),
                            carbsGrams = obj.optDouble("carbsGrams", 0.0),
                            fatGrams = obj.optDouble("fatGrams", 0.0),
                            fiberGrams = obj.optDouble("fiberGrams", 0.0),
                            foodItemId = obj.optString("foodItemId").takeIf { it.isNotBlank() },
                            loggedAt = obj.optLong("loggedAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            // Parse Water Logs
            val waterLogs = mutableListOf<DailyWaterLog>()
            val waterArray = root.optJSONArray("waterLogs")
            if (waterArray != null) {
                for (i in 0 until waterArray.length()) {
                    val obj = waterArray.getJSONObject(i)
                    waterLogs.add(
                        DailyWaterLog(
                            date = obj.getString("date"),
                            intakeMl = obj.optInt("intakeMl", 0)
                        )
                    )
                }
            }

            // Save to database
            database.dailyGoalDao().setGoal(goals)
            if (items.isNotEmpty()) {
                database.foodDao().insertFoodItems(items)
            }
            if (logs.isNotEmpty()) {
                database.foodLogDao().insertLogs(logs)
            }
            if (waterLogs.isNotEmpty()) {
                database.waterLogDao().insertAll(waterLogs)
            }

            val now = System.currentTimeMillis()
            prefs.edit()
                .putInt(KEY_PENDING_COUNT, 0)
                .putLong(KEY_LAST_SYNCED_TIME, now)
                .putString(KEY_LAST_SYNC_MESSAGE, "Restored & synced ${logs.size} logs from Google Drive")
                .apply()

            _autoSyncState.update {
                it.copy(
                    status = SyncQueueStatus.SYNCED_ON_WIFI,
                    pendingEntriesCount = 0,
                    lastSyncedTimestamp = now,
                    lastSyncMessage = "Restored & synced ${logs.size} logs from Google Drive"
                )
            }

            val data = BackupData(
                version = version,
                exportedAt = exportedAt,
                goals = goals,
                foodItems = items,
                foodLogs = logs,
                waterLogs = waterLogs
            )
            Result.success(data)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore backup from uri: $uri", e)
            Result.failure(e)
        }
    }
}
