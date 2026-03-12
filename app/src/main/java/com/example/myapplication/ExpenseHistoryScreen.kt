package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class DailyExpense(
    val id: String = UUID.randomUUID().toString(),
    val date: Date,
    val income: Double,
    val breakfast: Double,
    val lunch: Double,
    val dinner: Double,
    val others: Double
) {
    val totalExpense: Double get() = breakfast + lunch + dinner + others
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseHistoryScreen() {
    val context = LocalContext.current
    var selectedDate by remember { mutableStateOf(Date()) }
    var viewMode by remember { mutableStateOf("Daily") }
    var listFilter by remember { mutableStateOf("All") }
    var selectedEntry by remember { mutableStateOf<DailyExpense?>(null) }

    var allExpenses by remember { mutableStateOf(DataManager.getExpenses(context)) }
    var expenses by remember { mutableStateOf(listOf<DailyExpense>()) }

    LaunchedEffect(selectedDate, allExpenses) {
        allExpenses = DataManager.getExpenses(context)
        val cal = Calendar.getInstance()
        cal.time = selectedDate
        val targetMonth = cal.get(Calendar.MONTH)
        val targetYear = cal.get(Calendar.YEAR)

        expenses = allExpenses.filter {
            val expenseCal = Calendar.getInstance()
            expenseCal.time = it.date
            expenseCal.get(Calendar.MONTH) == targetMonth && expenseCal.get(Calendar.YEAR) == targetYear
        }
    }

    val bgColor = if (ThemeState.isDark.value) Color(0xFF121212) else Color(0xFFF2F2F7)
    val cardColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White
    val textColor = if (ThemeState.isDark.value) Color.White else Color.Black

    // File Picker Launcher for saving PDF in any folder chosen by the user
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            val success = PDFManager.writePDFToUri(context, uri, expenses, selectedDate)
            if (success) {
                Toast.makeText(context, "PDF Saved Successfully!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Failed to save PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(bgColor)) {
        Surface(
            color = cardColor,
            shadowElevation = 4.dp,
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
        ) {
            Column(modifier = Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("History", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = textColor)
                    Spacer(modifier = Modifier.weight(1f))

                    // HIGHLIGHT: Replaced ambiguous Icon Box with a clear "Download PDF" Button
                    Surface(
                        onClick = {
                            val fileName = "MyWallet_Report_${SimpleDateFormat("MMM_yyyy", Locale.getDefault()).format(selectedDate)}.pdf"
                            createDocumentLauncher.launch(fileName)
                        },
                        color = Color(0xFF007AFF).copy(alpha = 0.1f), // Standard secondary tonal background color
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Download PDF",
                            color = Color(0xFF007AFF), // The blue accent color
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(15.dp))
                CompactMonthPicker(date = selectedDate, onDateSelected = { selectedDate = it }, cardColor = cardColor, textColor = textColor)

                Spacer(modifier = Modifier.height(15.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = viewMode == "Daily",
                        onClick = { viewMode = "Daily" },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("Daily List") }
                    SegmentedButton(
                        selected = viewMode == "Category",
                        onClick = { viewMode = "Category" },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("Category Wise") }
                }
            }
        }

        AnimatedContent(targetState = viewMode, label = "ViewMode") { mode ->
            if (mode == "Daily") {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        FilterChip(selected = listFilter == "All", onClick = { listFilter = "All" }, label = { Text("All") })
                        FilterChip(selected = listFilter == "In", onClick = { listFilter = "In" }, label = { Text("Income") })
                        FilterChip(selected = listFilter == "Out", onClick = { listFilter = "Out" }, label = { Text("Expense") })
                    }

                    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 100.dp)) {
                        val filtered = ExpenseCalculator.filterExpenses(expenses.sortedByDescending { it.date }, listFilter)

                        items(filtered) { item ->
                            HistoryRowCard(item, listFilter, cardColor, textColor) { selectedEntry = item }
                        }
                    }
                }
            } else {
                val totalFood = ExpenseCalculator.getTotalFood(expenses)
                val totalOthers = ExpenseCalculator.getTotalOthers(expenses)
                val totalExp = ExpenseCalculator.getTotalExpense(expenses)
                val totalInc = ExpenseCalculator.getTotalIncome(expenses)

                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("TOTAL SPENT (THIS MONTH)", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text("৳${String.format("%.0f", totalExp)}", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = textColor)

                    Spacer(modifier = Modifier.height(20.dp))

                    CategoryCard(icon = Icons.Default.ShoppingCart, color = Color(0xFFFF9500), title = "Food Total", subtitle = "Breakfast + Lunch + Dinner", amount = totalFood, cardColor = cardColor, textColor = textColor)
                    Spacer(modifier = Modifier.height(12.dp))
                    CategoryCard(icon = Icons.Default.ShoppingCart, color = Color(0xFFAF52DE), title = "Others Total", subtitle = "Transport, Shopping, etc.", amount = totalOthers, cardColor = cardColor, textColor = textColor)
                    Spacer(modifier = Modifier.height(12.dp))
                    CategoryCard(icon = Icons.Default.KeyboardArrowDown, color = Color(0xFF34C759), title = "Total Received", subtitle = "Income from Home", amount = totalInc, cardColor = cardColor, textColor = textColor)
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }

    if (selectedEntry != null) {
        ModalBottomSheet(onDismissRequest = { selectedEntry = null }, containerColor = cardColor) {
            ExpenseDetailSheet(item = selectedEntry!!, cardColor = cardColor, textColor = textColor)
        }
    }
}

@Composable
fun HistoryRowCard(item: DailyExpense, currentFilter: String, cardColor: Color, textColor: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(item.date), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor)
                Text(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(item.date), color = Color.Gray, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                if (currentFilter != "Out" && item.income > 0) {
                    Text("+৳${item.income.toInt()}", color = Color(0xFF34C759), fontWeight = FontWeight.Bold)
                }
                if (currentFilter != "In" && item.totalExpense > 0) {
                    Text("-৳${item.totalExpense.toInt()}", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CategoryCard(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, title: String, subtitle: String, amount: Double, cardColor: Color, textColor: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = cardColor), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textColor)
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text("৳${amount.toInt()}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
        }
    }
}

@Composable
fun ExpenseDetailSheet(item: DailyExpense, cardColor: Color, textColor: Color) {
    val innerBgColor = if (ThemeState.isDark.value) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 20.dp)) {
        Text(SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(item.date), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textColor)
        Text(SimpleDateFormat("EEEE", Locale.getDefault()).format(item.date), fontSize = 16.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(20.dp))

        if (item.income > 0) {
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0xFF34C759), Color(0xFF30D158)))).padding(20.dp)) {
                Row {
                    Column {
                        Text("Income Received", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        Text("+৳${item.income.toInt()}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(innerBgColor).padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Text("Expense Breakdown", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textColor)
                DetailRow("Breakfast", item.breakfast, textColor)
                DetailRow("Lunch", item.lunch, textColor)
                DetailRow("Dinner", item.dinner, textColor)
                DetailRow("Others", item.others, textColor)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Box(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Red.copy(alpha = 0.3f), RoundedCornerShape(16.dp)).padding(20.dp)) {
            Row {
                Text("Total Spent", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textColor)
                Spacer(modifier = Modifier.weight(1f))
                Text("-৳${item.totalExpense.toInt()}", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun DetailRow(name: String, amount: Double, textColor: Color) {
    Row {
        Text(name, fontSize = 16.sp, color = textColor)
        Spacer(modifier = Modifier.weight(1f))
        if (amount > 0) Text("৳${amount.toInt()}", fontWeight = FontWeight.SemiBold, color = textColor)
        else Text("-", color = Color.Gray)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactMonthPicker(date: Date, onDateSelected: (Date) -> Unit, cardColor: Color, textColor: Color) {
    var showSheet by remember { mutableStateOf(false) }
    val calendar = Calendar.getInstance().apply { time = date }
    var currentYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    Box(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF007AFF).copy(alpha = 0.1f)).clickable { showSheet = true }.padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.DateRange, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(16.dp))
            Text(SimpleDateFormat("MMM ''yy", Locale.getDefault()).format(date), color = Color(0xFF007AFF), fontWeight = FontWeight.Bold)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(16.dp))
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, containerColor = cardColor) {
            Column(modifier = Modifier.padding(20.dp).padding(bottom = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { currentYear -= 1 }) { Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = textColor) }
                    Text(currentYear.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.padding(horizontal = 20.dp))
                    IconButton(onClick = { currentYear += 1 }) { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = textColor) }
                }
                Spacer(modifier = Modifier.height(20.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(12) { index ->
                        val isSelected = calendar.get(Calendar.MONTH) == index && calendar.get(Calendar.YEAR) == currentYear

                        val itemBgColor = if (isSelected) Color(0xFF007AFF) else if (ThemeState.isDark.value) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
                        val itemTextColor = if (isSelected) Color.White else textColor

                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(itemBgColor).clickable {
                                calendar.set(Calendar.YEAR, currentYear)
                                calendar.set(Calendar.MONTH, index)
                                onDateSelected(calendar.time)
                                showSheet = false
                            }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(months[index], color = itemTextColor, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

object PDFManager {
    // New function to safely write PDF using Uri from CreateDocument API
    fun writePDFToUri(context: Context, uri: Uri, expenses: List<DailyExpense>, selectedDate: Date): Boolean {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(612, 792, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        val totalFood = ExpenseCalculator.getTotalFood(expenses)
        val totalOthers = ExpenseCalculator.getTotalOthers(expenses)
        val totalExpense = ExpenseCalculator.getTotalExpense(expenses)
        val totalIncome = ExpenseCalculator.getTotalIncome(expenses)
        val balance = totalIncome - totalExpense

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 24f
        paint.color = android.graphics.Color.BLACK
        canvas.drawText("Monthly Expense Report", 50f, 74f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 14f
        paint.color = android.graphics.Color.GRAY
        canvas.drawText("Period: ${SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(selectedDate)}", 50f, 100f, paint)

        drawCard(canvas, paint, "Total Income", totalIncome, android.graphics.Color.parseColor("#334CAF50"), android.graphics.Color.parseColor("#4CAF50"), 50f, 130f)
        drawCard(canvas, paint, "Total Expenses", totalExpense, android.graphics.Color.parseColor("#33F44336"), android.graphics.Color.parseColor("#F44336"), 226f, 130f)
        drawCard(canvas, paint, "Net Balance", balance, android.graphics.Color.parseColor("#332196F3"), android.graphics.Color.parseColor("#2196F3"), 402f, 130f)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 16f
        paint.color = android.graphics.Color.BLACK
        canvas.drawText("Category Breakdown", 50f, 226f, paint)

        val barWidth = 512f
        if (totalExpense > 0) {
            val foodRatio = (totalFood / totalExpense).toFloat() * barWidth
            paint.color = android.graphics.Color.parseColor("#FF9800")
            canvas.drawRect(50f, 240f, 50f + foodRatio, 260f, paint)

            paint.color = android.graphics.Color.parseColor("#9C27B0")
            canvas.drawRect(50f + foodRatio, 240f, 50f + barWidth, 260f, paint)
        }

        val tableY = 320f
        paint.color = android.graphics.Color.parseColor("#1A000000")
        canvas.drawRect(50f, tableY, 562f, tableY + 30f, paint)

        paint.color = android.graphics.Color.BLACK
        paint.textSize = 12f
        canvas.drawText("Date", 60f, tableY + 20f, paint)
        canvas.drawText("Income", 160f, tableY + 20f, paint)
        canvas.drawText("Food", 260f, tableY + 20f, paint)
        canvas.drawText("Others", 360f, tableY + 20f, paint)
        canvas.drawText("Total", 460f, tableY + 20f, paint)

        var rowY = tableY + 55f
        val sorted = expenses.sortedBy { it.date }
        val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        for (expense in sorted) {
            val fCost = expense.breakfast + expense.lunch + expense.dinner

            paint.color = android.graphics.Color.DKGRAY
            canvas.drawText(dateFormat.format(expense.date), 60f, rowY, paint)

            paint.color = android.graphics.Color.parseColor("#4CAF50")
            canvas.drawText(if (expense.income > 0) "+${expense.income.toInt()}" else "-", 160f, rowY, paint)

            paint.color = android.graphics.Color.parseColor("#FF9800")
            canvas.drawText(if (fCost > 0) "${fCost.toInt()}" else "-", 260f, rowY, paint)

            paint.color = android.graphics.Color.parseColor("#9C27B0")
            canvas.drawText(if (expense.others > 0) "${expense.others.toInt()}" else "-", 360f, rowY, paint)

            paint.color = android.graphics.Color.BLACK
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("${expense.totalExpense.toInt()}", 460f, rowY, paint)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

            paint.color = android.graphics.Color.parseColor("#1A000000")
            canvas.drawLine(50f, rowY + 15f, 562f, rowY + 15f, paint)

            rowY += 25f
        }

        document.finishPage(page)

        return try {
            // Write PDF safely via ContentResolver
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                document.writeTo(stream)
            }
            document.close()
            true
        } catch (e: Exception) {
            document.close()
            false
        }
    }

    private fun drawCard(canvas: Canvas, paint: Paint, title: String, value: Double, bgColor: Int, txtColor: Int, x: Float, y: Float) {
        paint.color = bgColor
        canvas.drawRoundRect(x, y, x + 160f, y + 60f, 10f, 10f, paint)
        paint.color = txtColor
        paint.textSize = 12f
        canvas.drawText(title, x + 15f, y + 22f, paint)
        paint.textSize = 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(String.format("%.0f", value), x + 15f, y + 50f, paint)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
}