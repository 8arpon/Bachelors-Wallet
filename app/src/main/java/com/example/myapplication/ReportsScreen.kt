package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

// --- DATA CLASSES ---
data class ChartData(val category: String, val amount: Double, val percentage: Float, val color: Color)
data class DailySummary(val date: Date, val income: Double, val expense: Double, val entries: List<TransactionEntry>)

val ChartColors = listOf(
    Color(0xFF5E5CE6), Color(0xFFFF9500), Color(0xFF34C759), Color(0xFFFF3B30),
    Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFFCC00), Color(0xFFE58606)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val allTransactions by DataManager.getTransactionsFlow(context).collectAsState(initial = emptyList())

    var selectedMonth by remember { mutableStateOf(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }) }
    var currentTab by remember { mutableStateOf(0) } // 0 = Analytics, 1 = History

    // Analytics States
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var chartType by remember { mutableStateOf("Pie") } // "Pie", "Bar", "Line"

    // History States
    var listFilter by remember { mutableStateOf("All") }
    var selectedDailySummary by remember { mutableStateOf<DailySummary?>(null) }
    var historyViewMode by remember { mutableStateOf("Daily") } // "Daily" or "Category"

    // Unified Export Menu State
    var showExportSheet by remember { mutableStateOf(false) }
    var exportSelectedMonth by remember { mutableStateOf(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }) }

    val isDark = ThemeState.isDark.value
    val bgColor = ThemeState.background.value
    val cardColor = ThemeState.cardBackground.value
    val primaryColor = ThemeState.primaryAccent.value
    val textColor = if (isDark) Color.White else Color.Black

    // --- DATA PROCESSING ---
    val monthTransactions = remember(allTransactions, selectedMonth) {
        val currentM = selectedMonth.get(Calendar.MONTH)
        val currentY = selectedMonth.get(Calendar.YEAR)
        allTransactions.filter {
            val cal = Calendar.getInstance().apply { time = it.date }
            cal.get(Calendar.MONTH) == currentM && cal.get(Calendar.YEAR) == currentY
        }
    }

    val totalMonthIncome = monthTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val totalMonthExpense = monthTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val netSavings = totalMonthIncome - totalMonthExpense

    val chartPalette = remember(primaryColor) {
        listOf(
            primaryColor, Color(0xFFFF9500), Color(0xFF34C759), Color(0xFFFF3B30),
            Color(0xFF00C6FF), Color(0xFFAF52DE), Color(0xFFFFCC00), Color(0xFFE58606)
        )
    }

    val (chartDataList, totalChartAmount) = remember(monthTransactions, selectedType, chartPalette) {
        val filtered = monthTransactions.filter { it.type == selectedType }
        val total = filtered.sumOf { it.amount }
        val grouped = filtered.groupBy { it.category }.map { (cat, txs) -> Pair(cat, txs.sumOf { it.amount }) }.sortedByDescending { it.second }
        val dataList = grouped.mapIndexed { index, pair ->
            ChartData(pair.first, pair.second, if (total > 0) (pair.second / total).toFloat() else 0f, chartPalette[index % chartPalette.size])
        }
        Pair(dataList, total)
    }

    val dailySummaries = remember(monthTransactions) {
        monthTransactions.groupBy {
            val c = Calendar.getInstance().apply { time = it.date }
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            c.timeInMillis
        }.map { (time, txs) ->
            val inc = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val exp = txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            DailySummary(Date(time), inc, exp, txs)
        }.sortedByDescending { it.date }
    }

    Column(modifier = Modifier.fillMaxSize().background(bgColor).statusBarsPadding()) {

        // --- TOP HEADER ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Insights", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = textColor)
                Spacer(modifier = Modifier.height(4.dp))
                CompactMonthPicker(date = selectedMonth.time, onDateSelected = { selectedMonth = Calendar.getInstance().apply { time = it } }, cardColor = cardColor, textColor = textColor, isDark = isDark)
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = primaryColor.copy(alpha = 0.15f),
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                    exportSelectedMonth = selectedMonth
                    showExportSheet = true
                }
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Export", tint = primaryColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }

        // --- NET SAVINGS CARD ---
        val savingsGradient = if (netSavings >= 0) {
            ThemeState.headerGradient.value
        } else {
            Brush.linearGradient(listOf(Color(0xFFF43F5E), Color(0xFFE11D48)))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(savingsGradient)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(54.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon(if (netSavings >= 0) Icons.Default.Savings else Icons.AutoMirrored.Filled.TrendingDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Net Savings This Month", fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("৳${String.format(Locale.US, "%,.0f", netSavings)}", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- PREMIUM TAB SWITCHER ---
        PremiumTabSwitch(
            currentTab = currentTab,
            onTabChange = { currentTab = it },
            cardColor = cardColor,
            textColor = textColor
        )

        // --- TAB CONTENT ---
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp, top = 16.dp)) {

            if (currentTab == 0) {
                // ==========================================
                // 📊 ANALYTICS TAB CONTENT
                // ==========================================
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                        // Income/Expense Toggle
                        Surface(modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(14.dp), color = cardColor, shadowElevation = 2.dp) {
                            Row(modifier = Modifier.padding(4.dp)) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(if (selectedType == TransactionType.EXPENSE) Color(0xFFFF3B30) else Color.Transparent).clickable { selectedType = TransactionType.EXPENSE }, contentAlignment = Alignment.Center) {
                                    Text("Expense", color = if (selectedType == TransactionType.EXPENSE) Color.White else textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(if (selectedType == TransactionType.INCOME) Color(0xFF34C759) else Color.Transparent).clickable { selectedType = TransactionType.INCOME }, contentAlignment = Alignment.Center) {
                                    Text("Income", color = if (selectedType == TransactionType.INCOME) Color.White else textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Chart Type Toggle (3 Options)
                        Surface(modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(14.dp), color = cardColor, shadowElevation = 2.dp) {
                            Row(modifier = Modifier.padding(4.dp)) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(if (chartType == "Pie") primaryColor.copy(alpha = 0.15f) else Color.Transparent).clickable { chartType = "Pie" }, contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PieChart, contentDescription = "Pie", tint = if (chartType == "Pie") primaryColor else Color.Gray, modifier = Modifier.size(20.dp))
                                }
                                Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(if (chartType == "Bar") primaryColor.copy(alpha = 0.15f) else Color.Transparent).clickable { chartType = "Bar" }, contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.BarChart, contentDescription = "Bar", tint = if (chartType == "Bar") primaryColor else Color.Gray, modifier = Modifier.size(20.dp))
                                }
                                Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(if (chartType == "Line") primaryColor.copy(alpha = 0.15f) else Color.Transparent).clickable { chartType = "Line" }, contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Line", tint = if (chartType == "Line") primaryColor else Color.Gray, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }

                item {
                    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(320.dp), shape = RoundedCornerShape(24.dp), color = cardColor, shadowElevation = 4.dp) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (chartDataList.isEmpty() && chartType != "Line") {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.DataUsage, contentDescription = null, tint = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.size(70.dp)); Spacer(modifier = Modifier.height(12.dp)); Text("No Data Available", color = Color.Gray, fontWeight = FontWeight.Medium, fontSize = 16.sp) }
                            } else if (dailySummaries.isEmpty() && chartType == "Line") {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.DataUsage, contentDescription = null, tint = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.size(70.dp)); Spacer(modifier = Modifier.height(12.dp)); Text("No Flow Data Available", color = Color.Gray, fontWeight = FontWeight.Medium, fontSize = 16.sp) }
                            } else {
                                AnimatedContent(targetState = chartType, label = "ChartAnim") { type ->
                                    when (type) {
                                        "Pie" -> BeautifulDonutChart(chartDataList, totalChartAmount, textColor)
                                        "Bar" -> BeautifulBarChart(chartDataList, textColor, isDark)
                                        "Line" -> BeautifulLineChart(dailySummaries, selectedType, textColor, isDark)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Category Breakdown", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = textColor, modifier = Modifier.padding(horizontal = 20.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (chartDataList.isEmpty()) {
                    item { Text("Log some transactions to see breakdown.", color = Color.Gray, modifier = Modifier.padding(horizontal = 20.dp)) }
                } else {
                    items(chartDataList) { data -> CategoryListRow(data, cardColor, textColor); Spacer(modifier = Modifier.height(12.dp)) }
                }

            } else {
                // ==========================================
                // 📜 HISTORY LOGS TAB CONTENT
                // ==========================================
                item {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                        SegmentedButton(selected = historyViewMode == "Daily", onClick = { historyViewMode = "Daily" }, shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)) { Text("Daily List") }
                        SegmentedButton(selected = historyViewMode == "Category", onClick = { historyViewMode = "Category" }, shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)) { Text("Category Wise") }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (historyViewMode == "Daily") {
                    item {
                        Surface(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp).height(44.dp), shape = RoundedCornerShape(14.dp), color = cardColor, shadowElevation = 2.dp) {
                            Row(modifier = Modifier.padding(4.dp)) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(if (listFilter == "All") primaryColor.copy(alpha = 0.15f) else Color.Transparent).clickable { listFilter = "All" }, contentAlignment = Alignment.Center) {
                                    Text("All", color = if (listFilter == "All") primaryColor else textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(if (listFilter == "In") Color(0xFF34C759).copy(alpha = 0.15f) else Color.Transparent).clickable { listFilter = "In" }, contentAlignment = Alignment.Center) {
                                    Text("Income", color = if (listFilter == "In") Color(0xFF34C759) else textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(if (listFilter == "Out") Color(0xFFFF3B30).copy(alpha = 0.15f) else Color.Transparent).clickable { listFilter = "Out" }, contentAlignment = Alignment.Center) {
                                    Text("Expense", color = if (listFilter == "Out") Color(0xFFFF3B30) else textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    val filteredHistory = when (listFilter) {
                        "In" -> dailySummaries.filter { it.income > 0 }
                        "Out" -> dailySummaries.filter { it.expense > 0 }
                        else -> dailySummaries
                    }

                    if (filteredHistory.isEmpty()) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.size(90.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("No history matching filter.", color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    } else {
                        items(filteredHistory) { summary ->
                            HistoryRowCard(summary, listFilter, cardColor, textColor) { selectedDailySummary = summary }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                } else {
                    item {
                        val foodCats = listOf("Breakfast", "Lunch", "Dinner", "Food", "Snacks", "Groceries")
                        val totalFood = monthTransactions.filter { it.type == TransactionType.EXPENSE && it.category in foodCats }.sumOf { it.amount }
                        val totalOthers = totalMonthExpense - totalFood

                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("TOTAL SPENT (THIS MONTH)", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text("৳${String.format(Locale.US, "%.0f", totalMonthExpense)}", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = textColor)

                            Spacer(modifier = Modifier.height(24.dp))

                            CategoryCard(icon = Icons.Default.Restaurant, color = Color(0xFFFF9500), title = "Food Total", subtitle = "Breakfast + Lunch + Dinner", amount = totalFood, cardColor = cardColor, textColor = textColor)
                            Spacer(modifier = Modifier.height(12.dp))
                            CategoryCard(icon = Icons.Default.ShoppingCart, color = Color(0xFFAF52DE), title = "Others Total", subtitle = "Transport, Shopping, etc.", amount = totalOthers, cardColor = cardColor, textColor = textColor)
                            Spacer(modifier = Modifier.height(12.dp))
                            CategoryCard(icon = Icons.AutoMirrored.Filled.TrendingUp, color = Color(0xFF34C759), title = "Total Received", subtitle = "Income from Sources", amount = totalMonthIncome, cardColor = cardColor, textColor = textColor)
                        }
                    }
                }
            }
        }
    }

    // 🌟 HIGHLIGHT: Centralized Export Bottom Sheet
    if (showExportSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showExportSheet = false }, sheetState = sheetState, containerColor = cardColor) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
                Text("Export Reports", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = textColor)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Select a month to download its report", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Medium)

                Spacer(modifier = Modifier.height(24.dp))

                val currentY = exportSelectedMonth.get(Calendar.YEAR)
                val currentMonthIndex = exportSelectedMonth.get(Calendar.MONTH)
                val monthsListState = rememberLazyListState()
                LaunchedEffect(showExportSheet) {
                    if (showExportSheet) {
                        val scrollIndex = maxOf(0, currentMonthIndex - 2)
                        monthsListState.scrollToItem(scrollIndex)
                    }
                }
                LazyRow(
                    state = monthsListState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                    items(12) { index ->
                        val isSelected = exportSelectedMonth.get(Calendar.MONTH) == index
                        val itemBgColor = if (isSelected) primaryColor else if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
                        val itemTextColor = if (isSelected) Color.White else textColor

                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(itemBgColor).clickable {
                                exportSelectedMonth = Calendar.getInstance().apply { set(Calendar.YEAR, currentY); set(Calendar.MONTH, index) }
                            }.padding(horizontal = 20.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(text = months[index], color = itemTextColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp) }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFF8F9FA)).padding(8.dp)) {
                    PremiumExportOptionRow(icon = Icons.Default.PieChart, title = "Analytics Report", subtitle = "Detailed PDF with charts", iconColor = primaryColor, textColor = textColor) {
                        showExportSheet = false
                        coroutineScope.launch {
                            val success = AdvancedExportManager.exportAnalyticsPDF(context, chartDataList, totalChartAmount, selectedType.name, exportSelectedMonth.time)
                            if (success) Toast.makeText(context, "Analytics PDF Saved!", Toast.LENGTH_LONG).show() else Toast.makeText(context, "Failed to save PDF", Toast.LENGTH_SHORT).show()
                        }
                    }
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 56.dp))

                    PremiumExportOptionRow(icon = Icons.AutoMirrored.Filled.ListAlt, title = "Transaction History (PDF)", subtitle = "Daily logs in PDF format", iconColor = Color(0xFFFF9500), textColor = textColor) {
                        showExportSheet = false
                        coroutineScope.launch {
                            val success = AdvancedExportManager.exportHistoryPDF(context, dailySummaries, totalMonthIncome, totalMonthExpense, exportSelectedMonth.time)
                            if (success) Toast.makeText(context, "History PDF Saved!", Toast.LENGTH_LONG).show() else Toast.makeText(context, "Failed to save PDF", Toast.LENGTH_SHORT).show()
                        }
                    }
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 56.dp))

                    PremiumExportOptionRow(icon = Icons.Default.TableChart, title = "Export to Excel (.CSV)", subtitle = "Clean spreadsheet data", iconColor = Color(0xFF34C759), textColor = textColor) {
                        showExportSheet = false
                        coroutineScope.launch {
                            val success = AdvancedExportManager.exportHistoryCSV(context, dailySummaries, exportSelectedMonth.time)
                            if (success) Toast.makeText(context, "CSV Saved to Downloads!", Toast.LENGTH_LONG).show() else Toast.makeText(context, "Failed to save CSV", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (selectedDailySummary != null) {
        ModalBottomSheet(onDismissRequest = { selectedDailySummary = null }, containerColor = cardColor) {
            ExpenseDetailSheetContent(item = selectedDailySummary!!, textColor = textColor)
        }
    }
}

// --- PREMIUM UI COMPONENTS ---

@Composable
fun PremiumExportOptionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, iconColor: Color, textColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
    }
}

@Composable
fun PremiumTabSwitch(currentTab: Int, onTabChange: (Int) -> Unit, cardColor: Color, textColor: Color) {
    val primaryColor = ThemeState.primaryAccent.value
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(50.dp), shape = RoundedCornerShape(25.dp), color = cardColor, shadowElevation = 2.dp) {
        Row(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(21.dp)).background(if (currentTab == 0) primaryColor.copy(alpha = 0.1f) else Color.Transparent).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTabChange(0) }, contentAlignment = Alignment.Center) {
                Text("Analytics", color = if (currentTab == 0) primaryColor else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(21.dp)).background(if (currentTab == 1) primaryColor.copy(alpha = 0.1f) else Color.Transparent).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTabChange(1) }, contentAlignment = Alignment.Center) {
                Text("History Logs", color = if (currentTab == 1) primaryColor else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactMonthPicker(date: Date, onDateSelected: (Date) -> Unit, cardColor: Color, textColor: Color, isDark: Boolean) {
    val primaryColor = ThemeState.primaryAccent.value
    var showSheet by remember { mutableStateOf(false) }
    val calendar = Calendar.getInstance().apply { time = date }
    var currentYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    Surface(
        shape = RoundedCornerShape(50), color = cardColor, shadowElevation = if (isDark) 0.dp else 3.dp,
        border = if (isDark) BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f)) else null,
        modifier = Modifier.clickable { showSheet = true }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Icon(Icons.Default.DateRange, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(SimpleDateFormat("MMM ''yy", Locale.getDefault()).format(date), color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState, containerColor = cardColor) {
            Column(modifier = Modifier.padding(24.dp).padding(bottom = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { currentYear -= 1 }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = textColor, modifier = Modifier.size(28.dp)) }
                    Text(currentYear.toString(), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = textColor)
                    IconButton(onClick = { currentYear += 1 }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = textColor, modifier = Modifier.size(28.dp)) }
                }
                Spacer(modifier = Modifier.height(24.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(12) { index ->
                        val isSelected = calendar.get(Calendar.MONTH) == index && calendar.get(Calendar.YEAR) == currentYear
                        val itemBgColor = if (isSelected) primaryColor else if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
                        val itemTextColor = if (isSelected) Color.White else textColor

                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(itemBgColor).clickable {
                                calendar.set(Calendar.YEAR, currentYear); calendar.set(Calendar.MONTH, index); onDateSelected(calendar.time); showSheet = false
                            }.padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) { Text(months[index], color = itemTextColor, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                    }
                }
            }
        }
    }
}

// --- CATEGORY CARDS ---
@Composable
fun CategoryCard(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, title: String, subtitle: String, amount: Double, cardColor: Color, textColor: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = cardColor), elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(46.dp).clip(CircleShape).background(color.copy(0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor)
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
            Text("৳${amount.toInt()}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
        }
    }
}

@Composable
fun CategoryListRow(data: ChartData, cardColor: Color, textColor: Color) {
    val progress by animateFloatAsState(targetValue = data.percentage, animationSpec = tween(1000), label = "")
    Surface(shape = RoundedCornerShape(20.dp), color = cardColor, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(data.color))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(data.category, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("৳${String.format(Locale.US, "%,.0f", data.amount)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape), color = data.color, trackColor = Color.Gray.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("${(data.percentage * 100).toInt()}%", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun HistoryRowCard(item: DailySummary, currentFilter: String, cardColor: Color, textColor: Color, onClick: () -> Unit) {
    val primaryColor = ThemeState.primaryAccent.value
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Text(SimpleDateFormat("dd", Locale.getDefault()).format(item.date), color = primaryColor, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(item.date), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor)
                Text(SimpleDateFormat("EEEE", Locale.getDefault()).format(item.date), color = Color.Gray, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                if (currentFilter != "Out" && item.income > 0) {
                    Text("+৳${String.format(Locale.US, "%,.0f", item.income)}", color = Color(0xFF34C759), fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
                if (currentFilter != "In" && item.expense > 0) {
                    Text("-৳${String.format(Locale.US, "%,.0f", item.expense)}", color = Color(0xFFFF3B30), fontWeight = FontWeight.Black, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun ExpenseDetailSheetContent(item: DailySummary, textColor: Color) {
    val innerBgColor = if (ThemeState.isDark.value) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 20.dp)) {
        Text(SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(item.date), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = textColor)
        Text(SimpleDateFormat("EEEE", Locale.getDefault()).format(item.date), fontSize = 16.sp, color = Color.Gray, fontWeight = FontWeight.Medium)

        Spacer(modifier = Modifier.height(24.dp))

        if (item.income > 0) {
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Color(0xFF34C759), Color(0xFF30D158)))).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Income Received", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("+৳${String.format(Locale.US, "%,.0f", item.income)}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(innerBgColor).padding(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Expense Breakdown", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textColor)

                val expensesOnly = item.entries.filter { it.type == TransactionType.EXPENSE }
                val groupedExpenses = expensesOnly.groupBy { it.category }.mapValues { it.value.sumOf { tx -> tx.amount } }

                if (groupedExpenses.isEmpty()) {
                    Text("No expenses logged for this day.", color = Color.Gray)
                } else {
                    groupedExpenses.forEach { (catName, amount) ->
                        Row {
                            Text(catName, fontSize = 16.sp, color = textColor, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.weight(1f))
                            Text("৳${String.format(Locale.US, "%,.0f", amount)}", fontWeight = FontWeight.Bold, color = textColor)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Box(modifier = Modifier.fillMaxWidth().border(2.dp, Color(0xFFFF3B30).copy(alpha = 0.3f), RoundedCornerShape(20.dp)).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Total Spent", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textColor)
                Spacer(modifier = Modifier.weight(1f))
                Text("-৳${String.format(Locale.US, "%,.0f", item.expense)}", color = Color(0xFFFF3B30), fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
        }
    }
}

// --- NEW ANNOTATED CHARTS (With Native Canvas Texts) ---
@Composable
fun BeautifulDonutChart(data: List<ChartData>, totalAmount: Double, textColor: Color) {
    var animationPlayed by remember { mutableStateOf(false) }
    LaunchedEffect(data) { animationPlayed = true }

    val animateSweep by animateFloatAsState(targetValue = if (animationPlayed) 360f else 0f, animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing), label = "")

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(220.dp)) {
            var startAngle = -90f
            data.forEach { chartData ->
                val sweepAngle = (chartData.percentage * animateSweep)
                drawArc(color = chartData.color, startAngle = startAngle, sweepAngle = sweepAngle, useCenter = false, style = Stroke(width = 65f, cap = StrokeCap.Butt))
                startAngle += sweepAngle
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Total", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("৳${String.format(Locale.US, "%,.0f", totalAmount)}", color = textColor, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun BeautifulBarChart(data: List<ChartData>, textColor: Color, isDark: Boolean) {
    var animationPlayed by remember { mutableStateOf(false) }
    LaunchedEffect(data) { animationPlayed = true }

    val maxAmount = data.maxOfOrNull { it.amount } ?: 1.0

    // 🌟 HIGHLIGHT: Labels added under and above bars using NativeCanvas
    Canvas(modifier = Modifier.fillMaxSize().padding(top = 40.dp, bottom = 40.dp, start = 20.dp, end = 20.dp)) {
        val barWidth = 45f
        val spacing = (size.width - (data.size * barWidth)) / (data.size + 1)
        var xOffset = spacing

        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 28f
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valuePaint = android.graphics.Paint().apply {
            color = if(isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            textSize = 24f
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        data.forEach { chartData ->
            val targetHeight = (chartData.amount / maxAmount).toFloat() * size.height
            val animatedHeight = if (animationPlayed) targetHeight else 0f

            drawRoundRect(color = Color.Gray.copy(alpha = 0.1f), topLeft = Offset(xOffset, 0f), size = Size(barWidth, size.height), cornerRadius = CornerRadius(20f, 20f))
            drawRoundRect(color = chartData.color, topLeft = Offset(xOffset, size.height - animatedHeight), size = Size(barWidth, animatedHeight), cornerRadius = CornerRadius(20f, 20f))

            val shortName = if (chartData.category.length > 3) chartData.category.take(3) else chartData.category
            drawContext.canvas.nativeCanvas.drawText(shortName, xOffset + (barWidth / 2), size.height + 40f, textPaint)

            if (animatedHeight > 20f) {
                drawContext.canvas.nativeCanvas.drawText(chartData.amount.toInt().toString(), xOffset + (barWidth / 2), size.height - animatedHeight - 15f, valuePaint)
            }

            xOffset += barWidth + spacing
        }
    }
}

// 🌟 HIGHLIGHT: New Line/Trend Chart
@Composable
fun BeautifulLineChart(dailySummaries: List<DailySummary>, selectedType: TransactionType, textColor: Color, isDark: Boolean) {
    if (dailySummaries.isEmpty()) return
    var animationPlayed by remember { mutableStateOf(false) }
    LaunchedEffect(dailySummaries) { animationPlayed = true }
    val animProgress by animateFloatAsState(if (animationPlayed) 1f else 0f, tween(1200), label = "")

    val sortedData = dailySummaries.sortedBy { it.date }
    val maxAmount = sortedData.maxOfOrNull { if(selectedType == TransactionType.EXPENSE) it.expense else it.income } ?: 1.0

    Canvas(modifier = Modifier.fillMaxSize().padding(top = 40.dp, bottom = 40.dp, start = 20.dp, end = 20.dp)) {
        val width = size.width
        val height = size.height
        val xStep = if (sortedData.size > 1) width / (sortedData.size - 1) else width / 2

        val path = Path()
        val fillPath = Path()

        var currentX = if (sortedData.size == 1) width / 2 else 0f
        val points = mutableListOf<Offset>()

        sortedData.forEachIndexed { index, summary ->
            val amt = if(selectedType == TransactionType.EXPENSE) summary.expense else summary.income
            val y = height - ((amt / maxAmount) * height).toFloat() * animProgress
            points.add(Offset(currentX, y))

            if (index == 0) {
                path.moveTo(currentX, y)
                fillPath.moveTo(currentX, height)
                fillPath.lineTo(currentX, y)
            } else {
                path.lineTo(currentX, y)
                fillPath.lineTo(currentX, y)
            }
            if (sortedData.size > 1) currentX += xStep
        }

        val chartLineColor = ThemeState.primaryAccent.value
        if (points.isNotEmpty()) {
            if (sortedData.size > 1) {
                fillPath.lineTo(points.last().x, height)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(listOf(chartLineColor.copy(alpha = 0.3f), Color.Transparent))
                )

                drawPath(
                    path = path,
                    color = chartLineColor,
                    style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            val textPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.GRAY
                textSize = 26f
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val dateFormat = SimpleDateFormat("dd", Locale.getDefault())

            points.forEachIndexed { index, point ->
                drawCircle(color = if(isDark) Color(0xFF1E1E1E) else Color.White, radius = 10f, center = point)
                drawCircle(color = chartLineColor, radius = 6f, center = point)
                drawContext.canvas.nativeCanvas.drawText(dateFormat.format(sortedData[index].date), point.x, height + 40f, textPaint)
            }
        }
    }
}

// ====================================================================
// ADVANCED EXPORT MANAGER
// ====================================================================
object AdvancedExportManager {

    suspend fun exportChartImage(context: Context, data: List<ChartData>, total: Double, type: String, date: Date): Boolean = withContext(Dispatchers.IO) {
        if (data.isEmpty()) return@withContext false
        try {
            val bitmap = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = Paint().apply { isAntiAlias = true }

            canvas.drawColor(android.graphics.Color.WHITE)
            paint.color = android.graphics.Color.BLACK
            paint.textSize = 45f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val title = "$type Analysis - ${SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date)}"
            canvas.drawText(title, 40f, 80f, paint)

            val rectF = android.graphics.RectF(200f, 150f, 600f, 550f)
            var startAngle = -90f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 80f

            data.forEach { item ->
                val sweepAngle = item.percentage * 360f
                paint.color = item.color.toArgb()
                canvas.drawArc(rectF, startAngle, sweepAngle, false, paint)
                startAngle += sweepAngle
            }

            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 24f
            paint.color = android.graphics.Color.GRAY
            canvas.drawText("TOTAL", 400f, 340f, paint)
            paint.textSize = 40f
            paint.color = android.graphics.Color.BLACK
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("৳${total.toInt()}", 400f, 390f, paint)

            paint.textAlign = Paint.Align.LEFT
            var legendY = 650f
            data.forEach { item ->
                paint.color = item.color.toArgb()
                canvas.drawCircle(60f, legendY - 10f, 15f, paint)
                paint.color = android.graphics.Color.BLACK
                paint.textSize = 28f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                canvas.drawText("${item.category} (${(item.percentage*100).toInt()}%)", 100f, legendY, paint)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("৳${item.amount.toInt()}", 600f, legendY, paint)
                legendY += 50f
            }

            val filename = "Analysis_${type}_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { os -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, os) }
                    values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0); context.contentResolver.update(it, values, null, null)
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                if (!dir.exists()) dir.mkdirs()
                FileOutputStream(File(dir, filename)).use { os -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, os) }
            }
            true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    suspend fun exportHistoryCSV(context: Context, dailySummaries: List<DailySummary>, date: Date): Boolean = withContext(Dispatchers.IO) {
        if (dailySummaries.isEmpty()) return@withContext false
        try {
            val csvBuilder = StringBuilder()
            csvBuilder.append("Date,Income (tk),Expense (tk),Transactions Count\n")
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            dailySummaries.forEach { summary ->
                csvBuilder.append("${dateFormat.format(summary.date)},${summary.income},${summary.expense},${summary.entries.size}\n")
            }

            val filename = "History_${SimpleDateFormat("MMM_yyyy", Locale.US).format(date)}_${System.currentTimeMillis()}.csv"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        os.write(csvBuilder.toString().toByteArray(Charsets.UTF_8))
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    context.contentResolver.update(it, values, null, null)
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                FileOutputStream(File(dir, filename)).use { os ->
                    os.write(csvBuilder.toString().toByteArray(Charsets.UTF_8))
                }
            }
            true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    suspend fun exportAnalyticsPDF(context: Context, data: List<ChartData>, total: Double, type: String, date: Date): Boolean = withContext(Dispatchers.IO) {
        if (data.isEmpty()) return@withContext false
        try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            val paint = Paint().apply { isAntiAlias = true }

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 24f
            paint.color = android.graphics.Color.BLACK
            canvas.drawText("Monthly Analysis Report ($type)", 50f, 70f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 14f
            paint.color = android.graphics.Color.GRAY
            canvas.drawText("Period: ${SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date)}", 50f, 100f, paint)

            paint.color = android.graphics.Color.parseColor("#E3F2FD")
            canvas.drawRoundRect(50f, 130f, 250f, 210f, 15f, 15f, paint)
            paint.color = android.graphics.Color.parseColor("#1976D2")
            paint.textSize = 12f
            canvas.drawText("TOTAL $type", 70f, 160f, paint)
            paint.textSize = 24f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("৳${total.toInt()}", 70f, 190f, paint)

            val tableY = 260f
            paint.color = android.graphics.Color.parseColor("#F5F5F5")
            canvas.drawRect(50f, tableY, 545f, tableY + 30f, paint)
            paint.color = android.graphics.Color.BLACK
            paint.textSize = 14f
            canvas.drawText("Category", 60f, tableY + 20f, paint)
            canvas.drawText("Percentage", 300f, tableY + 20f, paint)
            canvas.drawText("Amount", 450f, tableY + 20f, paint)

            var rowY = tableY + 55f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            data.forEach { item ->
                paint.color = android.graphics.Color.DKGRAY
                canvas.drawText(item.category, 60f, rowY, paint)
                canvas.drawText("${(item.percentage * 100).toInt()}%", 300f, rowY, paint)
                paint.color = android.graphics.Color.BLACK
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("৳${item.amount.toInt()}", 450f, rowY, paint)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.color = android.graphics.Color.parseColor("#E0E0E0")
                canvas.drawLine(50f, rowY + 15f, 545f, rowY + 15f, paint)
                rowY += 35f
            }
            document.finishPage(page)
            val monthPeriod = SimpleDateFormat("MMM_yyyy", Locale.US).format(date)
            savePdfDocument(context, document, "${monthPeriod}_${type}_Analysis_BachelorsWallet.pdf")
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    suspend fun exportHistoryPDF(context: Context, history: List<DailySummary>, totalInc: Double, totalExp: Double, date: Date): Boolean = withContext(Dispatchers.IO) {
        if (history.isEmpty()) return@withContext false
        try {
            val document = PdfDocument()
            var pageNum = 1
            var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas
            val paint = Paint().apply { isAntiAlias = true }

            fun drawHeader() {
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 24f
                paint.color = android.graphics.Color.BLACK
                canvas.drawText("Monthly History Log", 50f, 70f, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textSize = 14f
                paint.color = android.graphics.Color.GRAY
                canvas.drawText("Period: ${SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date)} (Page $pageNum)", 50f, 100f, paint)
            }

            drawHeader()

            paint.color = android.graphics.Color.parseColor("#E8F5E9")
            canvas.drawRoundRect(50f, 130f, 250f, 210f, 15f, 15f, paint)
            paint.color = android.graphics.Color.parseColor("#4CAF50")
            paint.textSize = 12f
            canvas.drawText("TOTAL INCOME", 70f, 160f, paint)
            paint.textSize = 24f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("৳${totalInc.toInt()}", 70f, 190f, paint)

            paint.color = android.graphics.Color.parseColor("#FFEBEE")
            canvas.drawRoundRect(270f, 130f, 470f, 210f, 15f, 15f, paint)
            paint.color = android.graphics.Color.parseColor("#F44336")
            paint.textSize = 12f
            canvas.drawText("TOTAL EXPENSE", 290f, 160f, paint)
            paint.textSize = 24f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("৳${totalExp.toInt()}", 290f, 190f, paint)

            var currentY = 260f

            fun drawTableHeader() {
                paint.color = android.graphics.Color.parseColor("#F5F5F5")
                canvas.drawRect(50f, currentY, 545f, currentY + 30f, paint)
                paint.color = android.graphics.Color.BLACK
                paint.textSize = 14f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("Date", 60f, currentY + 20f, paint)
                canvas.drawText("Income", 250f, currentY + 20f, paint)
                canvas.drawText("Expense", 400f, currentY + 20f, paint)
                currentY += 55f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            drawTableHeader()

            val dateFormat = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())

            history.forEach { item ->
                if (currentY > 780f) {
                    document.finishPage(page)
                    pageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    drawHeader()
                    currentY = 140f
                    drawTableHeader()
                }

                paint.color = android.graphics.Color.DKGRAY
                canvas.drawText(dateFormat.format(item.date), 60f, currentY, paint)
                paint.color = android.graphics.Color.parseColor("#4CAF50")
                canvas.drawText(if(item.income > 0) "+৳${item.income.toInt()}" else "-", 250f, currentY, paint)
                paint.color = android.graphics.Color.parseColor("#F44336")
                canvas.drawText(if(item.expense > 0) "-৳${item.expense.toInt()}" else "-", 400f, currentY, paint)
                paint.color = android.graphics.Color.parseColor("#E0E0E0")
                canvas.drawLine(50f, currentY + 15f, 545f, currentY + 15f, paint)
                currentY += 35f
            }

            document.finishPage(page)
            val monthPeriod = SimpleDateFormat("MMM_yyyy", Locale.US).format(date)
            savePdfDocument(context, document, "${monthPeriod}_Transaction_History_BachelorsWallet.pdf")
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    private fun savePdfDocument(context: Context, document: PdfDocument, filename: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let { context.contentResolver.openOutputStream(it)?.use { os -> document.writeTo(os) } }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                FileOutputStream(File(dir, filename)).use { os -> document.writeTo(os) }
            }
            document.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            document.close()
            false
        }
    }
}