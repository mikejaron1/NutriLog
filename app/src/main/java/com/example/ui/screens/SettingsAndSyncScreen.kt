package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.DailyGoal
import com.example.data.sync.AutoSyncUiState
import com.example.data.sync.SyncQueueStatus
import com.example.ui.AiActionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsAndSyncScreen(
    currentGoal: DailyGoal,
    themePreference: String,
    syncState: AiActionState<String>,
    autoSyncState: AutoSyncUiState,
    backupFileName: String,
    onExportBackup: (Uri) -> Unit,
    onImportBackup: (Uri) -> Unit,
    onSetAutoSyncEnabled: (Boolean) -> Unit,
    onSetWifiHoldMode: (Boolean) -> Unit,
    onSyncNowOverWifi: () -> Unit,
    onUpdateGoals: (Int, Int, Int, Int, Int, Int) -> Unit,
    onThemeChange: (String) -> Unit,
    onResetSyncState: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Goals state
    var caloriesText by remember(currentGoal) { mutableStateOf(currentGoal.calorieTarget.toString()) }
    var proteinText by remember(currentGoal) { mutableStateOf(currentGoal.proteinTargetGrams.toString()) }
    var carbsText by remember(currentGoal) { mutableStateOf(currentGoal.carbsTargetGrams.toString()) }
    var fatText by remember(currentGoal) { mutableStateOf(currentGoal.fatTargetGrams.toString()) }
    var fiberText by remember(currentGoal) { mutableStateOf(currentGoal.fiberTargetGrams.toString()) }
    var waterText by remember(currentGoal) { mutableStateOf(currentGoal.waterTargetMl.toString()) }
    var goalsSavedMessage by remember { mutableStateOf(false) }

    // Google Drive SAF launchers
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            onExportBackup(uri)
        }
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportBackup(uri)
        }
    }

    val formattedLastSynced = remember(autoSyncState.lastSyncedTimestamp) {
        if (autoSyncState.lastSyncedTimestamp <= 0L) {
            "Waiting for first entry sync"
        } else {
            val fmt = SimpleDateFormat("MMM d, h:mm:ss a", Locale.US)
            fmt.format(Date(autoSyncState.lastSyncedTimestamp))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            text = "Settings & Cloud Sync",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // 1. Google Drive Wi-Fi Auto-Sync & Backup Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("google_drive_sync_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Cloud",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Google Drive Wi-Fi Auto-Sync",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Auto-uploads after every entry on Wi-Fi, or waits until Wi-Fi reconnects",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle: Auto-Sync on Wi-Fi
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Sync After Every Entry (Wi-Fi Only)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "If not on Wi-Fi, entries queue locally and upload automatically as soon as Wi-Fi connects.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = autoSyncState.autoSyncEnabled,
                            onCheckedChange = onSetAutoSyncEnabled,
                            modifier = Modifier.testTag("auto_sync_wifi_switch")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Live Network & Queue Status Panel
                val isWaiting = autoSyncState.status == SyncQueueStatus.WAITING_FOR_WIFI ||
                    (!autoSyncState.isWifiConnected && autoSyncState.pendingEntriesCount > 0)

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isWaiting || !autoSyncState.isWifiConnected) {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wifi_sync_status_panel")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (autoSyncState.isWifiConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                                    contentDescription = "Wi-Fi Status",
                                    tint = if (autoSyncState.isWifiConnected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.tertiary
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (autoSyncState.isWifiConnected) {
                                        "Wi-Fi Connected • Ready to Auto-Upload"
                                    } else {
                                        "Not on Wi-Fi • Waiting for Wi-Fi Connection"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (autoSyncState.pendingEntriesCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.tertiary
                                ) {
                                    Text(
                                        text = "${autoSyncState.pendingEntriesCount} Queued",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Status: ${autoSyncState.lastSyncMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Last Drive Upload: $formattedLastSynced",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Target Drive File: ${autoSyncState.linkedDriveFileName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Wi-Fi State Simulation / Hold Toggle + Manual Sync Now
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = autoSyncState.isWifiHoldMode,
                                onClick = { onSetWifiHoldMode(!autoSyncState.isWifiHoldMode) },
                                label = {
                                    Text(
                                        if (autoSyncState.isWifiHoldMode) {
                                            "Reconnect Wi-Fi (Upload Queue)"
                                        } else {
                                            "Simulate Off-Wi-Fi (Queue Mode)"
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (autoSyncState.isWifiHoldMode) Icons.Default.Wifi else Icons.Default.CloudQueue,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("wifi_hold_mode_chip")
                            )

                            if (autoSyncState.isWifiConnected) {
                                OutlinedButton(
                                    onClick = onSyncNowOverWifi,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("sync_now_wifi_button"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sync Now", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Link a file in your Google Drive below so automatic Wi-Fi uploads overwrite that exact Drive file after every logged meal or water entry, or restore data on another device:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            createDocLauncher.launch(backupFileName)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("backup_drive_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Link & Backup to Drive")
                    }

                    OutlinedButton(
                        onClick = {
                            openDocLauncher.launch(arrayOf("application/json"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("restore_drive_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore / Sync")
                    }
                }

                // Sync manual status indicator
                when (syncState) {
                    is AiActionState.Loading -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(syncState.message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is AiActionState.Success -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "✓ ${syncState.data}",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    is AiActionState.Error -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "✕ ${syncState.message}",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    else -> {}
                }
            }
        }

        // 2. Daily Macro Goals Customizer
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("daily_goals_editor_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrackChanges,
                            contentDescription = "Goals",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Daily Nutritional Goals",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Set your calorie, macronutrient, and water targets",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = caloriesText,
                        onValueChange = { caloriesText = it; goalsSavedMessage = false },
                        label = { Text("Calories (kcal)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = waterText,
                        onValueChange = { waterText = it; goalsSavedMessage = false },
                        label = { Text("Water (ml)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = proteinText,
                        onValueChange = { proteinText = it; goalsSavedMessage = false },
                        label = { Text("Protein (g)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = carbsText,
                        onValueChange = { carbsText = it; goalsSavedMessage = false },
                        label = { Text("Carbs (g)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = fatText,
                        onValueChange = { fatText = it; goalsSavedMessage = false },
                        label = { Text("Fat (g)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = fiberText,
                        onValueChange = { fiberText = it; goalsSavedMessage = false },
                        label = { Text("Fiber (g)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val c = caloriesText.toIntOrNull() ?: currentGoal.calorieTarget
                        val p = proteinText.toIntOrNull() ?: currentGoal.proteinTargetGrams
                        val crb = carbsText.toIntOrNull() ?: currentGoal.carbsTargetGrams
                        val f = fatText.toIntOrNull() ?: currentGoal.fatTargetGrams
                        val fib = fiberText.toIntOrNull() ?: currentGoal.fiberTargetGrams
                        val w = waterText.toIntOrNull() ?: currentGoal.waterTargetMl
                        onUpdateGoals(c, p, crb, f, fib, w)
                        goalsSavedMessage = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_goals_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save Nutritional Goals")
                }

                if (goalsSavedMessage) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "✓ Daily goals saved & queued for Wi-Fi Drive sync!",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 3. Theme & Appearance
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Theme & Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = themePreference == "SYSTEM",
                        onClick = { onThemeChange("SYSTEM") },
                        label = { Text("System") },
                        leadingIcon = {
                            Icon(Icons.Default.SettingsBrightness, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.testTag("theme_system_chip")
                    )

                    FilterChip(
                        selected = themePreference == "DARK",
                        onClick = { onThemeChange("DARK") },
                        label = { Text("Dark Mode") },
                        leadingIcon = {
                            Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.testTag("theme_dark_chip")
                    )

                    FilterChip(
                        selected = themePreference == "LIGHT",
                        onClick = { onThemeChange("LIGHT") },
                        label = { Text("Light") },
                        leadingIcon = {
                            Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.testTag("theme_light_chip")
                    )
                }
            }
        }

        // About
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "NutriLog v2.0",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "Powered by Google Gemini & Firebase AI Logic. Nutrition information is provided for tracking and reference purposes.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}
