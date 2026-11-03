package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.StackedBarChart
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DailyGoal
import com.example.data.model.DailyWaterLog
import com.example.data.model.FoodLogEntry
import com.example.ui.theme.CaloriesColor
import com.example.ui.theme.CarbsColor
import com.example.ui.theme.FatColor
import com.example.ui.theme.FiberColor
import com.example.ui.theme.ProteinColor
import com.example.ui.theme.TertiaryAmber
import com.example.ui.theme.WaterColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

data class DailyHistorySummary(
    val dateString: String,     // "yyyy-MM-dd"
    val shortLabel: String,     // "Mon" or "11/02"
    val fullLabel: String,      // "Mon, Nov 2"
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double,
    val waterMl: Int,
    val mealCount: Int
)

@Composable
fun HistoryScreen(
    allLogs: List<FoodLogEntry>,
    allWaterLogs: List<DailyWaterLog>,
    dailyGoal: DailyGoal,
    onSelectDateInDiary: (String) -> Unit,
    onSeedSampleHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedDaysRange by remember { mutableIntStateOf(7) } // 7, 14, 30
    var calorieChartIsLine by remember { mutableStateOf(false) } // false = Bar, true = Area Line
    var macroChartIsStacked by remember { mutableStateOf(false) } // false = Multi-Line, true = Stacked Bar
    var selectedMacroFilter by remember { mutableStateOf("ALL") } // ALL, PROTEIN, CARBS, FAT, FIBER

    // Build chronological daily summaries for the selected range ending today
    val dailyHistoryList = remember(allLogs, allWaterLogs, selectedDaysRange) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayNameFormat = SimpleDateFormat("EEE", Locale.US)
        val shortDateFormat = SimpleDateFormat("M/d", Locale.US)
        val fullDateFormat = SimpleDateFormat("EEE, MMM d", Locale.US)

        val logsByDate = allLogs.groupBy { it.date }
        val waterByDate = allWaterLogs.associateBy { it.date }

        val list = mutableListOf<DailyHistorySummary>()
        for (i in (selectedDaysRange - 1) downTo 0) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dateStr = dateFormat.format(cal.time)
            val dayLogs = logsByDate[dateStr].orEmpty()
            val water = waterByDate[dateStr]?.intakeMl ?: 0

            val shortLbl = if (selectedDaysRange <= 7) {
                if (i == 0) "Today" else dayNameFormat.format(cal.time)
            } else {
                shortDateFormat.format(cal.time)
            }

            list.add(
                DailyHistorySummary(
                    dateString = dateStr,
                    shortLabel = shortLbl,
                    fullLabel = if (i == 0) "Today (${shortDateFormat.format(cal.time)})" else fullDateFormat.format(cal.time),
                    calories = dayLogs.sumOf { it.calories },
                    protein = dayLogs.sumOf { it.proteinGrams },
                    carbs = dayLogs.sumOf { it.carbsGrams },
                    fat = dayLogs.sumOf { it.fatGrams },
                    fiber = dayLogs.sumOf { it.fiberGrams },
                    waterMl = water,
                    mealCount = dayLogs.size
                )
            )
        }
        list
    }

    var selectedDayIndex by remember(dailyHistoryList) {
        mutableIntStateOf((dailyHistoryList.size - 1).coerceAtLeast(0))
    }

    val activeDays = dailyHistoryList.filter { it.calories > 0 }
    val avgCalories = if (activeDays.isNotEmpty()) activeDays.map { it.calories }.average().roundToInt() else 0
    val avgProtein = if (activeDays.isNotEmpty()) activeDays.map { it.protein }.average().roundToInt() else 0
    val avgCarbs = if (activeDays.isNotEmpty()) activeDays.map { it.carbs }.average().roundToInt() else 0
    val avgFat = if (activeDays.isNotEmpty()) activeDays.map { it.fat }.average().roundToInt() else 0
    val avgFiber = if (activeDays.isNotEmpty()) activeDays.map { it.fiber }.average().roundToInt() else 0
    val avgWater = if (activeDays.isNotEmpty()) activeDays.map { it.waterMl }.average().roundToInt() else 0
    val daysWithinGoal = activeDays.count {
        it.calories in (dailyGoal.calorieTarget * 0.8)..(dailyGoal.calorieTarget * 1.12)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .testTag("history_screen_container"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header & Time Range Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Nutrition History",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Calorie & macro trends over time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(7 to "7D", 14 to "14D", 30 to "30D").forEach { (days, label) ->
                    FilterChip(
                        selected = selectedDaysRange == days,
                        onClick = { selectedDaysRange = days },
                        label = { Text(label, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("range_chip_${days}d")
                    )
                }
            }
        }

        // Overview KPI Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HistoryKpiCard(
                title = "Avg Calories",
                value = "$avgCalories",
                subtitle = "Goal: ${dailyGoal.calorieTarget} kcal",
                accentColor = CaloriesColor,
                modifier = Modifier.weight(1f)
            )
            HistoryKpiCard(
                title = "Avg Protein",
                value = "${avgProtein}g",
                subtitle = "Goal: ${dailyGoal.proteinTargetGrams}g",
                accentColor = ProteinColor,
                modifier = Modifier.weight(1f)
            )
            HistoryKpiCard(
                title = "On-Target",
                value = "$daysWithinGoal/${activeDays.size.coerceAtLeast(1)}d",
                subtitle = "Avg H2O: ${avgWater}ml",
                accentColor = WaterColor,
                modifier = Modifier.weight(1f)
            )
        }

        // If fewer than 2 days logged, offer quick sample seed
        if (activeDays.size < 2) {
            OutlinedButton(
                onClick = onSeedSampleHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("seed_sample_history_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Timeline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Load Sample 7-Day Nutrition History")
            }
        }

        // CHART 1: Calories Over Time (Bar Chart / Line Graph + Target Goal Line)
        CalorieHistoryChartCard(
            history = dailyHistoryList,
            calorieGoal = dailyGoal.calorieTarget,
            selectedIndex = selectedDayIndex.coerceIn(0, (dailyHistoryList.size - 1).coerceAtLeast(0)),
            isLineChart = calorieChartIsLine,
            onToggleChartType = { calorieChartIsLine = !calorieChartIsLine },
            onSelectDay = { selectedDayIndex = it }
        )

        // CHART 2: Macronutrients Over Time (Multi-Line Trend / Stacked Bar Chart)
        MacroTrendsChartCard(
            history = dailyHistoryList,
            dailyGoal = dailyGoal,
            isStackedBar = macroChartIsStacked,
            selectedMacroFilter = selectedMacroFilter,
            onToggleChartMode = { macroChartIsStacked = !macroChartIsStacked },
            onSelectMacroFilter = { selectedMacroFilter = it }
        )

        // CHART 3: Average Macro Caloric Distribution (Donut Chart & Target Comparison)
        MacroDistributionDonutCard(
            avgProtein = avgProtein,
            avgCarbs = avgCarbs,
            avgFat = avgFat,
            avgFiber = avgFiber,
            goal = dailyGoal
        )

        // Daily Log Breakdown List
        Text(
            text = "Daily Log Breakdown",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
        )

        dailyHistoryList.reversed().forEach { day ->
            DailyHistoryRowCard(
                day = day,
                goal = dailyGoal,
                onClick = { onSelectDateInDiary(day.dateString) }
            )
        }

        Spacer(modifier = Modifier.height(84.dp))
    }
}

@Composable
private fun HistoryKpiCard(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun CalorieHistoryChartCard(
    history: List<DailyHistorySummary>,
    calorieGoal: Int,
    selectedIndex: Int,
    isLineChart: Boolean,
    onToggleChartType: () -> Unit,
    onSelectDay: (Int) -> Unit
) {
    val selectedDay = history.getOrNull(selectedIndex)
    val maxCalories = maxOf(
        calorieGoal * 1.25,
        (history.maxOfOrNull { it.calories } ?: 2200.0) * 1.15,
        2400.0
    ).toFloat()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("calorie_history_chart_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = CaloriesColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Calorie Intake Over Time",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Dashed line = ${calorieGoal} kcal daily target",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    onClick = onToggleChartType,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.testTag("toggle_calorie_chart_mode")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isLineChart) Icons.Default.BarChart else Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = "Switch Chart Type",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isLineChart) "Bars" else "Line",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Selected Day Inspector Banner
            if (selectedDay != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val diff = selectedDay.calories.roundToInt() - calorieGoal
                val diffLabel = when {
                    selectedDay.calories == 0.0 -> "No logs recorded"
                    diff > 0 -> "+$diff kcal over target"
                    diff < 0 -> "${-diff} kcal under target"
                    else -> "Exact target hit!"
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = selectedDay.fullLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = diffLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${selectedDay.calories.roundToInt()} kcal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = CaloriesColor
                            )
                            Text(
                                text = "P:${selectedDay.protein.roundToInt()}g • C:${selectedDay.carbs.roundToInt()}g • F:${selectedDay.fat.roundToInt()}g",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Canvas Chart Area
            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            val goalLineColor = TertiaryAmber
            val highlightColor = MaterialTheme.colorScheme.primary

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(185.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(history) {
                            detectTapGestures { offset ->
                                if (history.isNotEmpty() && size.width > 0) {
                                    val slotWidth = size.width.toFloat() / history.size
                                    val tappedIdx = (offset.x / slotWidth).toInt().coerceIn(0, history.size - 1)
                                    onSelectDay(tappedIdx)
                                }
                            }
                        }
                ) {
                    val chartHeight = size.height
                    val chartWidth = size.width
                    val count = history.size.coerceAtLeast(1)
                    val slotWidth = chartWidth / count

                    // Horizontal Grid lines (25%, 50%, 75%, 100%)
                    listOf(0.25f, 0.5f, 0.75f, 1.0f).forEach { frac ->
                        val y = chartHeight * (1f - frac)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(chartWidth, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Target Calorie Dashed Line
                    val goalY = (chartHeight * (1f - (calorieGoal / maxCalories))).coerceIn(0f, chartHeight)
                    drawLine(
                        color = goalLineColor,
                        start = Offset(0f, goalY),
                        end = Offset(chartWidth, goalY),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                    )

                    if (!isLineChart) {
                        // Bar Chart Mode
                        val barWidth = (slotWidth * 0.58f).coerceAtMost(28.dp.toPx())
                        history.forEachIndexed { idx, day ->
                            val ratio = (day.calories.toFloat() / maxCalories).coerceIn(0f, 1f)
                            val barHeight = (chartHeight * ratio).coerceAtLeast(if (day.calories > 0) 4.dp.toPx() else 2.dp.toPx())
                            val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                            val top = chartHeight - barHeight

                            val isOverGoal = day.calories > calorieGoal * 1.12
                            val barColor = when {
                                idx == selectedIndex -> if (isOverGoal) FatColor else CaloriesColor
                                isOverGoal -> FatColor.copy(alpha = 0.65f)
                                day.calories == 0.0 -> gridColor
                                else -> CaloriesColor.copy(alpha = 0.65f)
                            }

                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(left, top),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )
                        }
                    } else {
                        // Area Line Chart Mode
                        val points = history.mapIndexed { idx, day ->
                            val x = idx * slotWidth + slotWidth / 2f
                            val ratio = (day.calories.toFloat() / maxCalories).coerceIn(0f, 1f)
                            val y = chartHeight - (chartHeight * ratio)
                            Offset(x, y)
                        }

                        if (points.size >= 2) {
                            val linePath = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                for (i in 1 until points.size) {
                                    lineTo(points[i].x, points[i].y)
                                }
                            }
                            val areaPath = Path().apply {
                                addPath(linePath)
                                lineTo(points.last().x, chartHeight)
                                lineTo(points.first().x, chartHeight)
                                close()
                            }

                            drawPath(
                                path = areaPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        CaloriesColor.copy(alpha = 0.35f),
                                        CaloriesColor.copy(alpha = 0.02f)
                                    )
                                )
                            )
                            drawPath(
                                path = linePath,
                                color = CaloriesColor,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        points.forEachIndexed { idx, pt ->
                            val isSelected = idx == selectedIndex
                            drawCircle(
                                color = if (isSelected) highlightColor else CaloriesColor,
                                radius = if (isSelected) 6.dp.toPx() else 4.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // X-axis Labels
            val step = when {
                history.size <= 7 -> 1
                history.size <= 14 -> 2
                else -> 5
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                history.forEachIndexed { index, item ->
                    if (index % step == 0 || index == history.lastIndex) {
                        Text(
                            text = item.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                            color = if (index == selectedIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroTrendsChartCard(
    history: List<DailyHistorySummary>,
    dailyGoal: DailyGoal,
    isStackedBar: Boolean,
    selectedMacroFilter: String,
    onToggleChartMode: () -> Unit,
    onSelectMacroFilter: (String) -> Unit
) {
    val maxGramValue = remember(history, isStackedBar, selectedMacroFilter) {
        if (isStackedBar) {
            (history.maxOfOrNull { it.protein + it.carbs + it.fat } ?: 400.0).toFloat().coerceAtLeast(300f) * 1.15f
        } else {
            val maxSingle = history.maxOfOrNull {
                maxOf(it.protein, it.carbs, it.fat, it.fiber)
            } ?: 250.0
            maxSingle.toFloat().coerceAtLeast(200f) * 1.15f
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("macro_trends_chart_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.StackedBarChart,
                        contentDescription = null,
                        tint = ProteinColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Macros Over Time (g)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isStackedBar) "Stacked daily Protein, Carbs & Fat" else "Multi-line gram trend comparison",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    onClick = onToggleChartMode,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.testTag("toggle_macro_chart_mode")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isStackedBar) Icons.AutoMirrored.Filled.ShowChart else Icons.Default.StackedBarChart,
                            contentDescription = "Switch Mode",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isStackedBar) "Lines" else "Stacked",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter pills for Macros
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    Triple("ALL", "All", MaterialTheme.colorScheme.primary),
                    Triple("PROTEIN", "Protein", ProteinColor),
                    Triple("CARBS", "Carbs", CarbsColor),
                    Triple("FAT", "Fat", FatColor),
                    Triple("FIBER", "Fiber", FiberColor)
                ).forEach { (key, label, color) ->
                    val selected = selectedMacroFilter == key
                    Surface(
                        onClick = { onSelectMacroFilter(key) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val chartHeight = size.height
                    val chartWidth = size.width
                    val count = history.size.coerceAtLeast(1)
                    val slotWidth = chartWidth / count

                    // Grid lines
                    listOf(0.25f, 0.5f, 0.75f, 1.0f).forEach { frac ->
                        val y = chartHeight * (1f - frac)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(chartWidth, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    if (isStackedBar) {
                        // Stacked Bar Chart
                        val barWidth = (slotWidth * 0.56f).coerceAtMost(26.dp.toPx())
                        history.forEachIndexed { idx, day ->
                            val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                            var currentBottom = chartHeight

                            val segments = buildList {
                                if (selectedMacroFilter == "ALL" || selectedMacroFilter == "PROTEIN") {
                                    add(day.protein.toFloat() to ProteinColor)
                                }
                                if (selectedMacroFilter == "ALL" || selectedMacroFilter == "CARBS") {
                                    add(day.carbs.toFloat() to CarbsColor)
                                }
                                if (selectedMacroFilter == "ALL" || selectedMacroFilter == "FAT") {
                                    add(day.fat.toFloat() to FatColor)
                                }
                                if (selectedMacroFilter == "FIBER") {
                                    add(day.fiber.toFloat() to FiberColor)
                                }
                            }

                            segments.forEach { (grams, segColor) ->
                                val segH = (grams / maxGramValue) * chartHeight
                                if (segH > 0.5f) {
                                    val top = (currentBottom - segH).coerceAtLeast(0f)
                                    drawRect(
                                        color = segColor,
                                        topLeft = Offset(left, top),
                                        size = Size(barWidth, currentBottom - top)
                                    )
                                    currentBottom = top
                                }
                            }
                        }
                    } else {
                        // Multi-Line Graph
                        fun drawMacroLine(selector: (DailyHistorySummary) -> Double, lineColor: Color) {
                            val pts = history.mapIndexed { idx, day ->
                                val x = idx * slotWidth + slotWidth / 2f
                                val ratio = (selector(day).toFloat() / maxGramValue).coerceIn(0f, 1f)
                                val y = chartHeight - (chartHeight * ratio)
                                Offset(x, y)
                            }
                            if (pts.size >= 2) {
                                val path = Path().apply {
                                    moveTo(pts.first().x, pts.first().y)
                                    for (i in 1 until pts.size) {
                                        lineTo(pts[i].x, pts[i].y)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = lineColor,
                                    style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                            pts.forEach { pt ->
                                drawCircle(
                                    color = lineColor,
                                    radius = 3.5.dp.toPx(),
                                    center = pt
                                )
                            }
                        }

                        if (selectedMacroFilter == "ALL" || selectedMacroFilter == "CARBS") {
                            drawMacroLine({ it.carbs }, CarbsColor)
                        }
                        if (selectedMacroFilter == "ALL" || selectedMacroFilter == "PROTEIN") {
                            drawMacroLine({ it.protein }, ProteinColor)
                        }
                        if (selectedMacroFilter == "ALL" || selectedMacroFilter == "FAT") {
                            drawMacroLine({ it.fat }, FatColor)
                        }
                        if (selectedMacroFilter == "ALL" || selectedMacroFilter == "FIBER") {
                            drawMacroLine({ it.fiber }, FiberColor)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Legend Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MacroLegendDot("Protein (${dailyGoal.proteinTargetGrams}g)", ProteinColor)
                MacroLegendDot("Carbs (${dailyGoal.carbsTargetGrams}g)", CarbsColor)
                MacroLegendDot("Fat (${dailyGoal.fatTargetGrams}g)", FatColor)
                MacroLegendDot("Fiber (${dailyGoal.fiberTargetGrams}g)", FiberColor)
            }
        }
    }
}

@Composable
private fun MacroLegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun MacroDistributionDonutCard(
    avgProtein: Int,
    avgCarbs: Int,
    avgFat: Int,
    avgFiber: Int,
    goal: DailyGoal
) {
    val proteinCal = avgProtein * 4f
    val carbsCal = avgCarbs * 4f
    val fatCal = avgFat * 9f
    val totalMacroCal = (proteinCal + carbsCal + fatCal).coerceAtLeast(1f)

    val proteinPct = ((proteinCal / totalMacroCal) * 100).roundToInt()
    val carbsPct = ((carbsCal / totalMacroCal) * 100).roundToInt()
    val fatPct = (100 - proteinPct - carbsPct).coerceAtLeast(0)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("macro_distribution_donut_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DonutLarge,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Average Caloric Macro Split",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Proportion of energy from Protein, Carbs, and Fat",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Donut Chart
                Box(
                    modifier = Modifier.size(124.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(112.dp)) {
                        val stroke = 16.dp.toPx()
                        val pSweep = 360f * (proteinCal / totalMacroCal)
                        val cSweep = 360f * (carbsCal / totalMacroCal)
                        val fSweep = 360f - pSweep - cSweep

                        var startAngle = -90f
                        drawArc(
                            color = ProteinColor,
                            startAngle = startAngle,
                            sweepAngle = pSweep,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Butt)
                        )
                        startAngle += pSweep
                        drawArc(
                            color = CarbsColor,
                            startAngle = startAngle,
                            sweepAngle = cSweep,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Butt)
                        )
                        startAngle += cSweep
                        drawArc(
                            color = FatColor,
                            startAngle = startAngle,
                            sweepAngle = fSweep,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Butt)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${totalMacroCal.roundToInt()}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "avg kcal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Breakdown Table
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MacroSplitRow(
                        name = "Protein",
                        grams = "${avgProtein}g / ${goal.proteinTargetGrams}g",
                        pct = "$proteinPct%",
                        color = ProteinColor
                    )
                    MacroSplitRow(
                        name = "Carbs",
                        grams = "${avgCarbs}g / ${goal.carbsTargetGrams}g",
                        pct = "$carbsPct%",
                        color = CarbsColor
                    )
                    MacroSplitRow(
                        name = "Fat",
                        grams = "${avgFat}g / ${goal.fatTargetGrams}g",
                        pct = "$fatPct%",
                        color = FatColor
                    )
                    MacroSplitRow(
                        name = "Fiber",
                        grams = "${avgFiber}g / ${goal.fiberTargetGrams}g",
                        pct = "Daily avg",
                        color = FiberColor
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroSplitRow(
    name: String,
    grams: String,
    pct: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = "$grams ($pct)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DailyHistoryRowCard(
    day: DailyHistorySummary,
    goal: DailyGoal,
    onClick: () -> Unit
) {
    val totalGrams = (day.protein + day.carbs + day.fat).coerceAtLeast(1.0)
    val pWeight = (day.protein / totalGrams).toFloat().coerceAtLeast(0.05f)
    val cWeight = (day.carbs / totalGrams).toFloat().coerceAtLeast(0.05f)
    val fWeight = (day.fat / totalGrams).toFloat().coerceAtLeast(0.05f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("history_day_card_${day.dateString}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = day.fullLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${day.calories.roundToInt()} / ${goal.calorieTarget} kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open in Diary",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Proportional Macro Bar
            if (day.calories > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(pWeight)
                            .fillMaxHeight()
                            .background(ProteinColor)
                    )
                    Box(
                        modifier = Modifier
                            .weight(cWeight)
                            .fillMaxHeight()
                            .background(CarbsColor)
                    )
                    Box(
                        modifier = Modifier
                            .weight(fWeight)
                            .fillMaxHeight()
                            .background(FatColor)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("P: ${day.protein.roundToInt()}g", color = ProteinColor, style = MaterialTheme.typography.labelSmall)
                    Text("C: ${day.carbs.roundToInt()}g", color = CarbsColor, style = MaterialTheme.typography.labelSmall)
                    Text("F: ${day.fat.roundToInt()}g", color = FatColor, style = MaterialTheme.typography.labelSmall)
                    Text("Fib: ${day.fiber.roundToInt()}g", color = FiberColor, style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    text = "${day.waterMl} ml H2O",
                    style = MaterialTheme.typography.labelSmall,
                    color = WaterColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
