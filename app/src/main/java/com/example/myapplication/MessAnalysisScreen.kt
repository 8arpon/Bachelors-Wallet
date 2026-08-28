package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessAnalysisScreen(navController: NavController) {
    val context = LocalContext.current
    val isDark = ThemeState.isDark.value
    val bgColor = ThemeState.background.value
    val cardColor = ThemeState.cardBackground.value
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val primaryColor = ThemeState.primaryAccent.value

    var selectedMonthCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val sdfMonthKey = remember { SimpleDateFormat("yyyy-MM", Locale.US) }
    val displayMonthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val selectedMonthKey = remember(selectedMonthCalendar) { sdfMonthKey.format(selectedMonthCalendar.time) }
    val selectedMonthDisplay = remember(selectedMonthCalendar) { displayMonthFormat.format(selectedMonthCalendar.time) }

    val members = MessManager.members
    val mealRate = MessManager.getMealRate(selectedMonthKey)
    val totalBazaar = MessManager.getTotalBazaar(selectedMonthKey)
    val totalDeposits = MessManager.getTotalDeposits(selectedMonthKey)
    val fundInHand = MessManager.getFundInHand(selectedMonthKey)
    val totalFixed = MessManager.getTotalFixedCosts()
    val totalMeals = MessManager.getTotalMeals(selectedMonthKey)
    val totalMessExpense = totalBazaar + totalFixed
    val summaries = MessManager.getMemberSummaries(selectedMonthKey)

    var selectedMemberIdForChart by remember { mutableStateOf<String?>(null) }
    var activeChartTab by remember { mutableIntStateOf(0) } // 0: Meal Share, 1: Paid vs Cost, 2: Expense Mix

    Scaffold(
        containerColor = bgColor,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mess Analytics & Reports", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Text("$selectedMonthDisplay • Financial Overview", fontSize = 12.sp, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cardColor)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Month Selector Bar
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = cardColor,
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            val c = selectedMonthCalendar.clone() as Calendar
                            c.add(Calendar.MONTH, -1)
                            selectedMonthCalendar = c
                        }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Prev Month", tint = textColor)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedMonthDisplay,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }

                        IconButton(onClick = {
                            val c = selectedMonthCalendar.clone() as Calendar
                            c.add(Calendar.MONTH, 1)
                            selectedMonthCalendar = c
                        }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Month", tint = textColor)
                        }
                    }
                }
            }

            // Executive Financial KPI Summary Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardColor,
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("MONTHLY FINANCIAL OVERVIEW", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = primaryColor, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Live Meal Rate", fontSize = 11.sp, color = Color.Gray)
                                Text("৳${String.format("%.2f", mealRate)}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = textColor)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Total Meals", fontSize = 11.sp, color = Color.Gray)
                                Text(String.format("%.1f", totalMeals), fontSize = 18.sp, fontWeight = FontWeight.Black, color = textColor)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Bazaar Spent", fontSize = 11.sp, color = Color.Gray)
                                Text("৳${String.format("%,.0f", totalBazaar)}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF9500))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Total Deposits", fontSize = 11.sp, color = Color.Gray)
                                Text("৳${String.format("%,.0f", totalDeposits)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34C759))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Fixed Costs", fontSize = 11.sp, color = Color.Gray)
                                Text("৳${String.format("%,.0f", totalFixed)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Fund in Hand", fontSize = 11.sp, color = Color.Gray)
                                Text("৳${String.format("%,.0f", fundInHand)}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = if (fundInHand >= 0) Color(0xFF34C759) else Color(0xFFFF3B30))
                            }
                        }
                    }
                }
            }

            // Export & Share Actions (Export PDF + Share Text + CSV)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Export PDF Button (Primary Action)
                        Button(
                            onClick = {
                                exportMessPdf(context, selectedMonthDisplay, summaries, mealRate, totalBazaar, totalDeposits, fundInHand, totalFixed, totalMeals)
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export PDF", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        // Share Text Button
                        Button(
                            onClick = {
                                exportMessTextStatement(context, selectedMonthKey)
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share Text", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Secondary CSV / Excel button
                    OutlinedButton(
                        onClick = {
                            exportMessCsv(context, selectedMonthDisplay, summaries, mealRate, totalBazaar, totalDeposits, fundInHand, totalFixed)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        border = BorderStroke(1.dp, if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.12f))
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null, tint = Color(0xFF107C41), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export CSV / Excel Format", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textColor)
                    }
                }
            }

            // Interactive Chart Selector Tabs
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("🍽️ Meal Share", "⚖️ Paid vs Cost", "📊 Expense Mix").forEachIndexed { idx, title ->
                        val isSelected = activeChartTab == idx
                        Surface(
                            onClick = { activeChartTab = idx },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) primaryColor else if (isDark) Color(0xFF24242C) else Color(0xFFE8E9F0),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else textColor,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Interactive Visual Graph Section
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardColor,
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        AnimatedContent(
                            targetState = activeChartTab,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(200, easing = LinearOutSlowInEasing)) + scaleIn(initialScale = 0.97f, animationSpec = tween(200, easing = LinearOutSlowInEasing)))
                                    .togetherWith(fadeOut(animationSpec = tween(160, easing = FastOutLinearInEasing)))
                                    .using(
                                        SizeTransform(
                                            clip = false,
                                            sizeAnimationSpec = { _, _ ->
                                                spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            }
                                        )
                                    )
                            },
                            label = "chart_tab_anim"
                        ) { tab ->
                            when (tab) {
                                0 -> {
                                    // Chart 1: Interactive Meal Consumption Share
                                    Column {
                                        Text("MEAL CONSUMPTION BREAKDOWN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = primaryColor, letterSpacing = 1.sp)
                                        Text("Tap on any roommate to view meal details", fontSize = 11.sp, color = Color.Gray)
                                        Spacer(modifier = Modifier.height(14.dp))

                                        if (summaries.isEmpty() || totalMeals == 0.0) {
                                            Text("No meal data recorded for this month.", fontSize = 12.sp, color = Color.Gray)
                                        } else {
                                            summaries.forEach { s ->
                                                val pct = (s.totalMeals / totalMeals).toFloat()
                                                val animPct by animateFloatAsState(targetValue = pct, animationSpec = tween(600), label = "meal_pct")
                                                val isHighlighted = selectedMemberIdForChart == s.member.id

                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            selectedMemberIdForChart = if (isHighlighted) null else s.member.id
                                                        }
                                                        .background(if (isHighlighted) primaryColor.copy(alpha = 0.12f) else Color.Transparent)
                                                        .padding(horizontal = 6.dp, vertical = 6.dp)
                                                ) {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text(s.member.displayName, fontSize = 13.sp, fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.SemiBold, color = textColor)
                                                        Text("${String.format("%.1f", s.totalMeals)} meals (${(pct * 100).toInt()}%)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isHighlighted) primaryColor else textColor.copy(alpha = 0.8f))
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    LinearProgressIndicator(
                                                        progress = { animPct.coerceIn(0f, 1f) },
                                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                                        color = if (isHighlighted) primaryColor else Color(0xFF7B61FF).copy(alpha = 0.75f),
                                                        trackColor = if (isDark) Color(0xFF2C2C34) else Color(0xFFE5E7EB)
                                                    )
                                                    if (isHighlighted) {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                            Text("Meal Cost: ৳${String.format("%,.0f", s.mealCost)}", fontSize = 11.sp, color = primaryColor, fontWeight = FontWeight.Bold)
                                                            Text("Avg ৳${String.format("%.2f", mealRate)}/meal", fontSize = 11.sp, color = Color.Gray)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                1 -> {
                                    // Chart 2: Paid vs Cost Balance Matrix
                                    Column {
                                        Text("FINANCIAL BALANCE MATRIX (PAID vs COST)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = primaryColor, letterSpacing = 1.sp)
                                        Text("Comparing total paid (Deposits + Pocket) vs Total Cost", fontSize = 11.sp, color = Color.Gray)
                                        Spacer(modifier = Modifier.height(14.dp))

                                        if (summaries.isEmpty()) {
                                            Text("No roommates added yet.", fontSize = 12.sp, color = Color.Gray)
                                        } else {
                                            val maxVal = summaries.maxOfOrNull { maxOf(it.totalDeposits + it.personalBazaarPaid, it.totalCost) }?.takeIf { it > 0 } ?: 1.0

                                            summaries.forEach { s ->
                                                val totalPaid = s.totalDeposits + s.personalBazaarPaid
                                                val paidPct = (totalPaid / maxVal).toFloat()
                                                val costPct = (s.totalCost / maxVal).toFloat()
                                                val animPaidPct by animateFloatAsState(targetValue = paidPct, animationSpec = tween(600), label = "paid_pct")
                                                val animCostPct by animateFloatAsState(targetValue = costPct, animationSpec = tween(600), label = "cost_pct")
                                                val isRefund = s.netBalance >= 0

                                                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                        Text(s.member.displayName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
                                                        Text(
                                                            text = if (isRefund) "+৳${s.netBalance.toInt()} (Refund)" else "-৳${(-s.netBalance).toInt()} (Due)",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = if (isRefund) Color(0xFF34C759) else Color(0xFFFF453A)
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    // Paid Bar (Green)
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text("Paid: ৳${totalPaid.toInt()}", fontSize = 10.sp, color = Color(0xFF34C759), modifier = Modifier.width(90.dp))
                                                        LinearProgressIndicator(
                                                            progress = { animPaidPct.coerceIn(0f, 1f) },
                                                            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                                            color = Color(0xFF34C759),
                                                            trackColor = if (isDark) Color(0xFF2C2C34) else Color(0xFFE5E7EB)
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    // Cost Bar (Orange/Red)
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text("Cost: ৳${s.totalCost.toInt()}", fontSize = 10.sp, color = Color(0xFFFF9500), modifier = Modifier.width(90.dp))
                                                        LinearProgressIndicator(
                                                            progress = { animCostPct.coerceIn(0f, 1f) },
                                                            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                                            color = Color(0xFFFF9500),
                                                            trackColor = if (isDark) Color(0xFF2C2C34) else Color(0xFFE5E7EB)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                2 -> {
                                    // Chart 3: Expense Mix (Bazaar Grocery vs Fixed Rent/Cook/Bills)
                                    Column {
                                        Text("MONTHLY EXPENSE DISTRIBUTION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = primaryColor, letterSpacing = 1.sp)
                                        Spacer(modifier = Modifier.height(14.dp))

                                        if (totalMessExpense == 0.0) {
                                            Text("No expenses recorded yet for this month.", fontSize = 12.sp, color = Color.Gray)
                                        } else {
                                            val bzrPct = (totalBazaar / totalMessExpense).toFloat()
                                            val fixedPct = (totalFixed / totalMessExpense).toFloat()

                                            // Multi-segment progress bar
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(14.dp)
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .background(if (isDark) Color(0xFF2C2C34) else Color(0xFFE5E7EB))
                                            ) {
                                                if (bzrPct > 0) {
                                                    Box(modifier = Modifier.fillMaxHeight().weight(bzrPct.coerceAtLeast(0.01f)).background(Color(0xFFFF9500)))
                                                }
                                                if (fixedPct > 0) {
                                                    Box(modifier = Modifier.fillMaxHeight().weight(fixedPct.coerceAtLeast(0.01f)).background(Color(0xFF7B61FF)))
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(14.dp))

                                            // Legend Items
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF9500)))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Bazaar & Groceries (${(bzrPct * 100).toInt()}%)", fontSize = 12.sp, color = textColor)
                                                }
                                                Text("৳${String.format("%,.0f", totalBazaar)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9500))
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF7B61FF)))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Fixed Costs & Bills (${(fixedPct * 100).toInt()}%)", fontSize = 12.sp, color = textColor)
                                                }
                                                Text("৳${String.format("%,.0f", totalFixed)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7B61FF))
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))
                                            HorizontalDivider(color = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.04f))
                                            Spacer(modifier = Modifier.height(10.dp))

                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Total Mess Outflow:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
                                                Text("৳${String.format("%,.0f", totalMessExpense)}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = textColor)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Detailed Tabular Breakdown
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardColor,
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("ROOMMATE-WISE DETAILED LEDGER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = primaryColor, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        if (summaries.isEmpty()) {
                            Text("No roommates added yet.", fontSize = 12.sp, color = Color.Gray)
                        } else {
                            summaries.forEachIndexed { index, s ->
                                if (index > 0) {
                                    HorizontalDivider(color = if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.04f), modifier = Modifier.padding(vertical = 10.dp))
                                }

                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(s.member.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
                                        val isRefund = s.netBalance >= 0
                                        Text(
                                            text = if (isRefund) "Refund: +৳${String.format("%,.0f", s.netBalance)}" else "Due: -৳${String.format("%,.0f", -s.netBalance)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isRefund) Color(0xFF34C759) else Color(0xFFFF453A)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Meals: ${String.format("%.1f", s.totalMeals)} (৳${String.format("%,.0f", s.mealCost)})", fontSize = 11.sp, color = Color.Gray)
                                        Text("Fixed: ৳${String.format("%,.0f", s.fixedCostShare)}", fontSize = 11.sp, color = Color.Gray)
                                        Text("Deposit: ৳${String.format("%,.0f", s.totalDeposits)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF34C759))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- PDF EXPORT FUNCTION (NATIVE ANDROID PDF GENERATOR) ---
private fun exportMessPdf(
    context: Context,
    monthDisplay: String,
    summaries: List<MemberSummary>,
    mealRate: Double,
    totalBazaar: Double,
    totalDeposits: Double,
    fundInHand: Double,
    totalFixed: Double,
    totalMeals: Double
) {
    try {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Standard
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint().apply { isAntiAlias = true }

        // 1. Header Banner
        paint.color = android.graphics.Color.parseColor("#7B61FF")
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        paint.color = android.graphics.Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 20f
        canvas.drawText("Bachelors Mess Statement", 40f, 45f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 12f
        canvas.drawText("Period: $monthDisplay • Generated by Bachelors Wallet", 40f, 70f, paint)

        // 2. Executive Summary Cards (Row 1)
        var curY = 120f
        paint.color = android.graphics.Color.parseColor("#F3F4F6")
        canvas.drawRoundRect(40f, curY, 195f, curY + 60f, 10f, 10f, paint)
        canvas.drawRoundRect(210f, curY, 365f, curY + 60f, 10f, 10f, paint)
        canvas.drawRoundRect(380f, curY, 555f, curY + 60f, 10f, 10f, paint)

        paint.color = android.graphics.Color.DKGRAY
        paint.textSize = 10f
        canvas.drawText("LIVE MEAL RATE", 52f, curY + 22f, paint)
        canvas.drawText("TOTAL MEALS", 222f, curY + 22f, paint)
        canvas.drawText("BAZAAR SPENT", 392f, curY + 22f, paint)

        paint.color = android.graphics.Color.BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 16f
        canvas.drawText("৳${String.format("%.2f", mealRate)}", 52f, curY + 48f, paint)
        canvas.drawText(String.format("%.1f", totalMeals), 222f, curY + 48f, paint)
        paint.color = android.graphics.Color.parseColor("#E65100")
        canvas.drawText("৳${String.format("%,.0f", totalBazaar)}", 392f, curY + 48f, paint)

        // Executive Summary Cards (Row 2)
        curY += 70f
        paint.color = android.graphics.Color.parseColor("#F3F4F6")
        canvas.drawRoundRect(40f, curY, 195f, curY + 60f, 10f, 10f, paint)
        canvas.drawRoundRect(210f, curY, 365f, curY + 60f, 10f, 10f, paint)
        canvas.drawRoundRect(380f, curY, 555f, curY + 60f, 10f, 10f, paint)

        paint.color = android.graphics.Color.DKGRAY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 10f
        canvas.drawText("TOTAL DEPOSITS", 52f, curY + 22f, paint)
        canvas.drawText("FIXED COSTS", 222f, curY + 22f, paint)
        canvas.drawText("FUND IN HAND", 392f, curY + 22f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 16f
        paint.color = android.graphics.Color.parseColor("#2E7D32")
        canvas.drawText("৳${String.format("%,.0f", totalDeposits)}", 52f, curY + 48f, paint)
        paint.color = android.graphics.Color.BLACK
        canvas.drawText("৳${String.format("%,.0f", totalFixed)}", 222f, curY + 48f, paint)
        paint.color = if (fundInHand >= 0) android.graphics.Color.parseColor("#2E7D32") else android.graphics.Color.parseColor("#C62828")
        canvas.drawText("৳${String.format("%,.0f", fundInHand)}", 392f, curY + 48f, paint)

        // 3. Table Header
        curY += 85f
        paint.color = android.graphics.Color.parseColor("#2C2C34")
        canvas.drawRect(40f, curY, 555f, curY + 26f, paint)

        paint.color = android.graphics.Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 10f
        canvas.drawText("ROOMMATE", 48f, curY + 17f, paint)
        canvas.drawText("MEALS", 185f, curY + 17f, paint)
        canvas.drawText("MEAL COST", 245f, curY + 17f, paint)
        canvas.drawText("FIXED", 325f, curY + 17f, paint)
        canvas.drawText("TOTAL COST", 380f, curY + 17f, paint)
        canvas.drawText("DEPOSIT", 450f, curY + 17f, paint)
        canvas.drawText("STATUS", 505f, curY + 17f, paint)

        // 4. Table Rows
        curY += 26f
        summaries.forEachIndexed { i, s ->
            paint.color = if (i % 2 == 0) android.graphics.Color.parseColor("#FAFAFA") else android.graphics.Color.WHITE
            canvas.drawRect(40f, curY, 555f, curY + 24f, paint)

            paint.color = android.graphics.Color.BLACK
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 9.5f

            canvas.drawText(s.member.displayName.take(18), 48f, curY + 16f, paint)
            canvas.drawText(String.format("%.1f", s.totalMeals), 185f, curY + 16f, paint)
            canvas.drawText("৳${s.mealCost.toInt()}", 245f, curY + 16f, paint)
            canvas.drawText("৳${s.fixedCostShare.toInt()}", 325f, curY + 16f, paint)
            canvas.drawText("৳${s.totalCost.toInt()}", 380f, curY + 16f, paint)
            canvas.drawText("৳${s.totalDeposits.toInt()}", 450f, curY + 16f, paint)

            val isRefund = s.netBalance >= 0
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = if (isRefund) android.graphics.Color.parseColor("#2E7D32") else android.graphics.Color.parseColor("#C62828")
            val statusText = if (isRefund) "+৳${s.netBalance.toInt()}" else "-৳${(-s.netBalance).toInt()}"
            canvas.drawText(statusText, 505f, curY + 16f, paint)

            curY += 24f
        }

        // Table Bottom Border
        paint.color = android.graphics.Color.LTGRAY
        canvas.drawLine(40f, curY, 555f, curY, paint)

        // Footer Note
        curY = 800f
        paint.color = android.graphics.Color.GRAY
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        paint.textSize = 9f
        canvas.drawText("Generated on ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())} via Bachelors Wallet", 40f, curY, paint)

        document.finishPage(page)

        // Save PDF in cache/pdfs directory
        val pdfsDir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val cleanMonth = monthDisplay.replace(" ", "_")
        val fileName = "${cleanMonth}_Mess_Statement_BachelorsWallet.pdf"
        val file = File(pdfsDir, fileName)
        FileOutputStream(file).use { os -> document.writeTo(os) }

        // Also save a copy to public Downloads if possible
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let { context.contentResolver.openOutputStream(it)?.use { os -> document.writeTo(os) } }
            }
        } catch (_: Exception) {}

        document.close()

        val authority = "${context.packageName}.provider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, "Mess Statement - $monthDisplay")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "Open / Share Mess Statement PDF via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        Toast.makeText(context, "PDF Statement generated successfully! 📄", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "PDF Generation error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

// --- CSV EXPORT FUNCTION ---
private fun exportMessCsv(
    context: Context,
    monthDisplay: String,
    summaries: List<MemberSummary>,
    mealRate: Double,
    totalBazaar: Double,
    totalDeposits: Double,
    fundInHand: Double,
    totalFixed: Double
) {
    try {
        val sb = StringBuilder()
        sb.append("Bachelors Wallet - Mess Statement for $monthDisplay\n")
        sb.append("Live Meal Rate,৳${String.format("%.2f", mealRate)}\n")
        sb.append("Total Bazaar Spent,৳${String.format("%.0f", totalBazaar)}\n")
        sb.append("Total Deposits Collected,৳${String.format("%.0f", totalDeposits)}\n")
        sb.append("Fixed Costs,৳${String.format("%.0f", totalFixed)}\n")
        sb.append("Fund in Hand,৳${String.format("%.0f", fundInHand)}\n\n")

        sb.append("Roommate,Role,Meals,Meal Cost (৳),Fixed Share (৳),Total Cost (৳),Deposit (৳),Net Balance (৳),Status\n")
        summaries.forEach { s ->
            val status = if (s.netBalance >= 0) "REFUND (+)" else "DUE (-)"
            sb.append("\"${s.member.displayName}\",\"${s.member.displayRole}\",${s.totalMeals},${s.mealCost.toInt()},${s.fixedCostShare.toInt()},${s.totalCost.toInt()},${s.totalDeposits.toInt()},${s.netBalance.toInt()},$status\n")
        }

        val pdfsDir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val fileName = "Mess_Statement_${monthDisplay.replace(" ", "_")}.csv"
        val file = File(pdfsDir, fileName)
        file.writeText(sb.toString())

        val authority = "${context.packageName}.provider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Mess Statement - $monthDisplay")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "Export Mess CSV via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// --- TEXT STATEMENT SHARE FUNCTION ---
private fun exportMessTextStatement(context: Context, monthKey: String) {
    val report = MessManager.generateWhatsAppReport(monthKey)
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, report)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Share Statement via")
    shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(shareIntent)
}
