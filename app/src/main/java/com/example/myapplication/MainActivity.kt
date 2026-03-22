package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// --- COLORS ---
val PremiumSlate = Color(0xFF0F172A)
val PremiumSlateLight = Color(0xFF1E293B)
val SoftRose = Color(0xFFF43F5E)
val EmeraldGreen = Color(0xFF10B981)
val AppBgLight = Color(0xFFF8F9FA)
val AppBgDark = Color(0xFF121212)

enum class ExpenseCategory(val label: String, val emoji: String) {
    BREAKFAST("Breakfast", "☕"),
    LUNCH("Lunch", "🍱"),
    DINNER("Dinner", "🌙"),
    OTHERS("Others", "🛍️");

    companion object {
        fun fromLabel(label: String): ExpenseCategory = entries.find { it.label == label } ?: OTHERS
    }
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        ThemeState.isDark.value = prefs.getBoolean("dark_mode", false)

        setContent {
            MaterialTheme(colorScheme = if (ThemeState.isDark.value) darkColorScheme() else lightColorScheme()) {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val globalBgColor = if (ThemeState.isDark.value) AppBgDark else AppBgLight

    val view = LocalView.current
    if (!view.isInEditMode) {
        LaunchedEffect(ThemeState.isDark.value) {
            val activity = view.context as? Activity
            activity?.window?.let { window ->
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !ThemeState.isDark.value
                insetsController.isAppearanceLightNavigationBars = !ThemeState.isDark.value
            }
        }
    }

    val activity = context as? Activity
    LaunchedEffect(activity?.intent) {
        val openTab = activity?.intent?.getStringExtra("OPEN_TAB")
        if (openTab == "NOTIFICATIONS") {
            navController.navigate("notifications") { launchSingleTop = true }
            activity.intent?.removeExtra("OPEN_TAB")
        }
    }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("first_time_permission_asked", true) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                delay(1500)
                (context as? Activity)?.let { ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101) }
                prefs.edit().putBoolean("first_time_permission_asked", false).apply()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(globalBgColor)) {
        NavHost(
            navController = navController, startDestination = "home", modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(300)) }, exitTransition = { fadeOut(tween(300)) }
        ) {
            composable("auth") { AuthScreen(onAuthSuccess = { navController.navigate("home") { popUpTo("home") { inclusive = true } } }) }
            composable("home") { BudgetPlannerScreen(navController) }
            composable("budget") { BudgetScreen() }
            composable("debt") { DebtManagerScreen() }
            composable("history") { ExpenseHistoryScreen() }
            composable("notifications") { NotificationScreen(navController) }
            composable("profile") { ProfileScreen(navController) }
        }
        FloatingNavBar(navController = navController, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun FloatingNavBar(navController: NavController, modifier: Modifier = Modifier) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isDark = ThemeState.isDark.value
    val navBarColor = if (isDark) Color(0xFF1E1E20) else Color.White
    val borderColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f)

    Surface(
        color = navBarColor,
        shadowElevation = 16.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = borderColor, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavItem(icon = Icons.Default.ShoppingCart, title = "Budget", isSelected = currentRoute == "budget") { if (currentRoute != "budget") navController.navigate("budget") { launchSingleTop = true } }
                NavItem(icon = Icons.Default.AccountBalanceWallet, title = "Debt", isSelected = currentRoute == "debt") { if (currentRoute != "debt") navController.navigate("debt") { launchSingleTop = true } }
                NavItem(icon = Icons.Default.Home, title = "Home", isSelected = currentRoute == "home") { if (currentRoute != "home") navController.navigate("home") { launchSingleTop = true } }
                NavItem(icon = Icons.Default.History, title = "History", isSelected = currentRoute == "history") { if (currentRoute != "history") navController.navigate("history") { launchSingleTop = true } }
                NavItem(icon = Icons.Default.Person, title = "Account", isSelected = currentRoute == "profile") { if (currentRoute != "profile") navController.navigate("profile") { launchSingleTop = true } }
            }
        }
    }
}

@Composable
fun RowScope.NavItem(icon: ImageVector, title: String, isSelected: Boolean, onClick: () -> Unit) {
    val highlightColor = Color(0xFF7B61FF)
    val contentColor by animateColorAsState(if (isSelected) highlightColor else Color.Gray, tween(300), label = "")
    val capsuleAlpha by animateFloatAsState(if (isSelected) 0.12f else 0f, tween(300), label = "")

    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .width(54.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(highlightColor.copy(alpha = capsuleAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = contentColor, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            color = contentColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

@Composable
fun SummaryMetricBlock(
    icon: ImageVector,
    label: String,
    value: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    val isDark = ThemeState.isDark.value
    val textColor = if (isDark) Color.White else PremiumSlate
    val bgColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF1F3F5)

    Surface(
        modifier = modifier.height(65.dp),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = if(!isDark) BorderStroke(1.dp, Color.LightGray.copy(0.2f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(label, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = "৳${String.format("%,.0f", value)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetPlannerScreen(navController: NavController) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val isDark = ThemeState.isDark.value
    val view = LocalView.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val isCurrentScreen = navBackStackEntry?.destination?.route == "home"

    if (!view.isInEditMode) {
        LaunchedEffect(isDark, isCurrentScreen) {
            val window = (view.context as? Activity)?.window
            window?.let {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(it, view)
                if (isCurrentScreen) {
                    insetsController.isAppearanceLightStatusBars = false
                } else {
                    insetsController.isAppearanceLightStatusBars = !isDark
                }
            }
        }
    }

    val allExpenses by DataManager.getExpensesFlow(context).collectAsState(initial = emptyList())
    val allDebts by DataManager.getDebtsFlow(context).collectAsState(initial = emptyList())
    val notifications by DataManager.getNotificationsFlow(context).collectAsState(initial = emptyList())
    val hasUnreadNotifs = notifications.any { !it.isRead }

    val totalReceived = ExpenseCalculator.getThisMonthIncome(allExpenses)
    val totalSpent = ExpenseCalculator.getThisMonthExpense(allExpenses)
    val currentBalance = ExpenseCalculator.getThisMonthBalance(context, allExpenses, allDebts)

    val totalIOwe = allDebts.filter { it.type == DebtType.I_OWE && !it.isPaid }.sumOf { it.remainingAmount }
    val totalTheyOwe = allDebts.filter { it.type == DebtType.THEY_OWE && !it.isPaid }.sumOf { it.remainingAmount }

    val todayExpenses = remember(allExpenses) {
        val todayCal = Calendar.getInstance()
        allExpenses.filter {
            val expCal = Calendar.getInstance().apply { time = it.date }
            todayCal.get(Calendar.YEAR) == expCal.get(Calendar.YEAR) && todayCal.get(Calendar.DAY_OF_YEAR) == expCal.get(Calendar.DAY_OF_YEAR)
        }
    }
    val todaySpent = todayExpenses.sumOf { it.breakfast + it.lunch + it.dinner + it.others }
    val daysRemaining = remember {
        val cal = Calendar.getInstance()
        maxOf(1, cal.getActualMaximum(Calendar.DAY_OF_MONTH) - cal.get(Calendar.DAY_OF_MONTH) + 1)
    }
    val safeDailySpend = if (currentBalance > 0) (currentBalance + todaySpent) / daysRemaining else 0.0
    val dailyProgress = if (safeDailySpend > 0) (todaySpent / safeDailySpend).toFloat().coerceIn(0f, 1f) else 0f

    val criticalDebts = remember(allDebts) {
        val now = System.currentTimeMillis()
        val twoDaysInMillis = 2L * 24 * 60 * 60 * 1000
        allDebts.filter { !it.isPaid && !it.isArchived && it.deadline != null && (it.deadline!!.time - now) <= twoDaysInMillis }
            .sortedBy { it.deadline!!.time }
    }

    var showAddScreen by remember { mutableStateOf(false) }
    var isExpenseForm by remember { mutableStateOf(true) }

    val transactionFocusRequester = remember { FocusRequester() }
    var transactionInput by remember { mutableStateOf(TextFieldValue("")) }
    var transactionDate by remember { mutableStateOf(Date()) }

    val builtInCategories = remember { listOf("Breakfast", "Lunch", "Dinner", "Others") }
    var userCreatedCategories = remember { mutableStateListOf<String>() }
    val availableCategories = remember(userCreatedCategories.size) { builtInCategories + userCreatedCategories }
    var selectedCategoryName by remember { mutableStateOf(builtInCategories[0]) }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }

    val textColor = if (ThemeState.isDark.value) Color.White else Color(0xFF1C1C1E)
    val purpleBg = Brush.verticalGradient(listOf(Color(0xFF5E45DA), Color(0xFF7B61FF)))
    val cardColor = if (ThemeState.isDark.value) Color(0xFF1E1E20) else Color.White

    LaunchedEffect(showAddScreen) {
        if (showAddScreen) {
            transactionInput = TextFieldValue("")
            transactionDate = Date()
            if (isExpenseForm) selectedCategoryName = builtInCategories[0]
            delay(100)
        }
    }

    LaunchedEffect(successMessage) { if (successMessage.isNotEmpty()) { delay(2500); successMessage = "" } }

    fun parseTerm(expr: String): Double {
        val parts = expr.split('*')
        var prod = 1.0
        for (i in parts.indices) {
            val divs = parts[i].split('/')
            var res = divs[0].trim().toDoubleOrNull() ?: 0.0
            for (j in 1 until divs.size) { res /= (divs[j].trim().toDoubleOrNull() ?: 1.0).let { if (it == 0.0) 1.0 else it } }
            if (i == 0) prod = res else prod *= res
        }
        return prod
    }

    fun evaluateExpression(expr: String): Double {
        var str = expr.replace(" ", "")
        if (str.isEmpty()) return 0.0
        if (str.startsWith("-")) str = "0$str"
        return try {
            val adds = str.split('+')
            var sum = 0.0
            for (add in adds) {
                if (add.isEmpty()) continue
                val subs = add.split('-')
                var subSum = parseTerm(subs[0])
                for (i in 1 until subs.size) subSum -= parseTerm(subs[i])
                sum += subSum
            }
            sum
        } catch (e: Exception) { str.toDoubleOrNull() ?: 0.0 }
    }

    fun handleSave(op: SaveOp, closeSheet: Boolean = true) {
        if (transactionInput.text.trim().isEmpty()) return
        val amt = evaluateExpression(transactionInput.text)
        if (amt < 0) return

        if (!isExpenseForm) {
            DataManager.addIncome(context, transactionDate, amt, op)
            if (closeSheet) successMessage = "Income of ৳${amt.toInt()} Saved!"
        } else {
            val mapping = ExpenseCategory.fromLabel(selectedCategoryName)
            DataManager.addExpense(context, transactionDate, mapping, amt, op)
            if (closeSheet) successMessage = "৳${amt.toInt()} saved in $selectedCategoryName!"
        }

        if (closeSheet) {
            focusManager.clearFocus()
            showAddScreen = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(if (ThemeState.isDark.value) Color(0xFF121212) else Color(0xFFF8F9FA))) {

        Column(modifier = Modifier.fillMaxSize()) {

            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                        .background(purpleBg)
                )

                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Dashboard", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text(SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault()).format(Date()), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        }
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).clickable { navController.navigate("notifications") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Notifications, "Notifications", tint = Color.White, modifier = Modifier.size(20.dp))
                            if (hasUnreadNotifs) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-8).dp, y = 8.dp)
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                        .border(2.dp, Color(0xFF7B61FF), CircleShape)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = cardColor,
                        shadowElevation = 12.dp
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("TOTAL BALANCE", fontSize = 12.sp, color = if(ThemeState.isDark.value) Color.Gray else PremiumSlateLight.copy(0.7f), fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("৳ ${String.format("%,.2f", currentBalance)}", fontSize = 38.sp, fontWeight = FontWeight.Black, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                            HorizontalDivider(color = Color.Gray.copy(0.1f))
                            Spacer(modifier = Modifier.height(20.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SummaryMetricBlock(icon = Icons.Default.ArrowDownward, label = "Income", value = totalReceived, color = EmeraldGreen, modifier = Modifier.weight(1f))
                                SummaryMetricBlock(icon = Icons.Default.ArrowUpward, label = "Spent", value = totalSpent, color = SoftRose, modifier = Modifier.weight(1f))
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SummaryMetricBlock(icon = Icons.Default.AccountBalanceWallet, label = "I Owe", value = totalIOwe, color = SoftRose, modifier = Modifier.weight(1f))
                                SummaryMetricBlock(icon = Icons.Default.AccountBalance, label = "They Owe", value = totalTheyOwe, color = EmeraldGreen, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            val actualHorizontalCount = if (criticalDebts.isNotEmpty()) 2 else 1
            val pagerState = rememberPagerState(
                initialPage = if (actualHorizontalCount > 1) actualHorizontalCount * 500 else 0,
                pageCount = { if (actualHorizontalCount > 1) Int.MAX_VALUE else 1 }
            )

            LaunchedEffect(actualHorizontalCount) {
                if (actualHorizontalCount > 1 && pagerState.currentPage < 100) {
                    pagerState.scrollToPage(5000)
                }
            }

            val currentActualPage = if (actualHorizontalCount > 1) {
                (pagerState.currentPage % actualHorizontalCount + actualHorizontalCount) % actualHorizontalCount
            } else 0

            val targetTitle = if (currentActualPage == 0) "Today's Limit" else "Debts"
            AnimatedContent(
                targetState = targetTitle,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                }, label = "TitleAnimation"
            ) { title ->
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 2.dp))
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 8.dp
            ) { page ->
                val actualPage = if (actualHorizontalCount > 1) (page % actualHorizontalCount + actualHorizontalCount) % actualHorizontalCount else 0

                if (actualPage == 0) {
                    // 🚀 STANDARD SHADOW: No custom spotColor that breaks on certain devices.
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 26.dp)
                            .height(115.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = cardColor,
                        shadowElevation = 10.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().clickable { navController.navigate("budget") }.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Spent: ৳${todaySpent.toInt()}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SoftRose)

                                // Clean limit badge
                                Surface(color = Color(0xFF7B61FF).copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                                    Text("Limit: ৳${safeDailySpend.toInt()}", fontSize = 11.sp, color = Color(0xFF7B61FF), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }

                            LinearProgressIndicator(
                                progress = { dailyProgress },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                color = if (dailyProgress > 0.9f) SoftRose else Color(0xFF7B61FF),
                                trackColor = Color.Gray.copy(alpha = 0.15f)
                            )

                            if (dailyProgress >= 1f) {
                                Text("You've reached your daily limit!", fontSize = 12.sp, color = SoftRose, fontWeight = FontWeight.Medium)
                            } else {
                                Text("৳${(safeDailySpend - todaySpent).toInt()} remaining for today", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                } else if (actualPage == 1 && criticalDebts.isNotEmpty()) {
                    val actualVerticalCount = criticalDebts.size
                    val verticalPagerState = rememberPagerState(
                        initialPage = if (actualVerticalCount > 1) actualVerticalCount * 500 else 0,
                        pageCount = { if (actualVerticalCount > 1) Int.MAX_VALUE else 1 }
                    )

                    LaunchedEffect(actualVerticalCount) {
                        if (actualVerticalCount > 1 && verticalPagerState.currentPage < 100) {
                            verticalPagerState.scrollToPage(5000)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        VerticalPager(
                            state = verticalPagerState,
                            modifier = Modifier.weight(1f).height(145.dp) // 115 height + padding = 145 exactly
                        ) { vPage ->
                            val actualVPage = if (actualVerticalCount > 1) (vPage % actualVerticalCount + actualVerticalCount) % actualVerticalCount else 0
                            val debt = criticalDebts[actualVPage]
                            val isOwe = debt.type == DebtType.I_OWE
                            val debtColor = if (isOwe) SoftRose else EmeraldGreen

                            val diff = debt.deadline!!.time - System.currentTimeMillis()
                            val days = diff / (1000 * 60 * 60 * 24)
                            val dayText = when {
                                days < 0 -> "Overdue by ${-days} days!"
                                days == 0L -> "Due Today!"
                                days == 1L -> "Due Tomorrow!"
                                else -> "Due in $days days"
                            }

                            // 🚀 DEBT CARD - 100% Matching "Today's Limit" Size & Layout & Standard Shadow
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 26.dp)
                                    .height(115.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = cardColor,
                                shadowElevation = 10.dp
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().clickable { navController.navigate("debt") }.padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Top Row: Name + Due Badge
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(debtColor.copy(alpha=0.1f)), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Person, contentDescription = null, tint = debtColor, modifier = Modifier.size(14.dp))
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(if (isOwe) "Pay ${debt.name}" else "Collect from ${debt.name}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }

                                        // Premium Deadline Badge (same as Limit badge)
                                        Surface(color = debtColor.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                                            Text(dayText, fontSize = 10.sp, color = debtColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }

                                    // Middle Row: Amount
                                    Text("৳${String.format("%,.0f", debt.remainingAmount)}", fontSize = 24.sp, fontWeight = FontWeight.Black, color = debtColor)

                                    // Bottom Row: Origination Date (Issued date)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Date: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(debt.date)}", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                        Icon(Icons.Default.ChevronRight, contentDescription = "Go", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        if (actualVerticalCount > 1) {
                            AnimatedVisibility(
                                visible = pagerState.currentPage == page,
                                enter = fadeIn(tween(200)),
                                exit = fadeOut(tween(200))
                            ) {
                                val currentActualVPage = (verticalPagerState.currentPage % actualVerticalCount + actualVerticalCount) % actualVerticalCount
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(start = 12.dp)
                                ) {
                                    repeat(actualVerticalCount) { iteration ->
                                        val color = if (currentActualVPage == iteration) Color(0xFF7B61FF) else Color.Gray.copy(alpha = 0.3f)
                                        val heightSize = if (currentActualVPage == iteration) 12.dp else 6.dp
                                        Box(
                                            modifier = Modifier
                                                .padding(vertical = 3.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(color)
                                                .width(6.dp)
                                                .height(heightSize)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (actualHorizontalCount > 1) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(actualHorizontalCount) { iteration ->
                        val color = if (currentActualPage == iteration) Color(0xFF7B61FF) else Color.Gray.copy(alpha = 0.3f)
                        val widthSize = if (currentActualPage == iteration) 12.dp else 6.dp
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(color)
                                .height(6.dp)
                                .width(widthSize)
                        )
                    }
                }
            }
        }

        // FLOATING ACTION BUTTON (FAB)
        FloatingActionButton(
            onClick = { isExpenseForm = true; showAddScreen = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 100.dp),
            containerColor = Color(0xFF7B61FF),
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            elevation = FloatingActionButtonDefaults.elevation(6.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Transaction", modifier = Modifier.size(28.dp))
        }

        // Toast Message
        AnimatedVisibility(visible = successMessage.isNotEmpty(), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp)) {
            Surface(color = EmeraldGreen.copy(alpha = 0.1f), shape = RoundedCornerShape(50.dp), border = borderStroke(1.dp, EmeraldGreen.copy(alpha = 0.3f))) {
                Text(successMessage, color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            }
        }

        // Add Transaction Dialog
        if (showAddScreen) {
            var existingAmount by remember { mutableStateOf(0.0) }
            var isEditMode by remember { mutableStateOf(false) }

            LaunchedEffect(isExpenseForm, selectedCategoryName, transactionDate, showAddScreen, allExpenses) {
                if (showAddScreen) {
                    val cal1 = Calendar.getInstance().apply { time = transactionDate }
                    val targetExp = allExpenses.find {
                        val cal2 = Calendar.getInstance().apply { time = it.date }
                        cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
                    }

                    val amt = if (targetExp != null) {
                        if (!isExpenseForm) targetExp.income
                        else {
                            when (ExpenseCategory.fromLabel(selectedCategoryName)) {
                                ExpenseCategory.BREAKFAST -> targetExp.breakfast
                                ExpenseCategory.LUNCH -> targetExp.lunch
                                ExpenseCategory.DINNER -> targetExp.dinner
                                ExpenseCategory.OTHERS -> targetExp.others
                            }
                        }
                    } else 0.0

                    existingAmount = amt
                    if (amt > 0) {
                        isEditMode = false
                        val formatted = if (amt % 1.0 == 0.0) amt.toLong().toString() else amt.toString()
                        transactionInput = TextFieldValue(text = formatted, selection = TextRange(formatted.length))
                    } else {
                        isEditMode = true
                        transactionInput = TextFieldValue("")
                    }
                }
            }

            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showAddScreen = false; focusManager.clearFocus() },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
            ) {
                val dialogBg = if (ThemeState.isDark.value) Color(0xFF1E1E20) else Color.White
                val inputBg = if (ThemeState.isDark.value) Color(0xFF2C2C2E) else Color(0xFFF3F4F6)
                val highlightColor = if (isExpenseForm) SoftRose else EmeraldGreen

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .imePadding()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            showAddScreen = false; focusManager.clearFocus()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                        shape = RoundedCornerShape(24.dp),
                        color = dialogBg,
                        shadowElevation = 24.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {

                            Box(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = if (isExpenseForm) "Add Expense" else "Add Income",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = textColor,
                                    modifier = Modifier.align(Alignment.Center)
                                )

                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .align(Alignment.CenterEnd)
                                        .clip(CircleShape)
                                        .background(inputBg)
                                        .clickable { showAddScreen = false; focusManager.clearFocus() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(inputBg, RoundedCornerShape(14.dp))
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isExpenseForm) SoftRose else Color.Transparent)
                                        .clickable { isExpenseForm = true }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Expense", color = if (isExpenseForm) Color.White else textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (!isExpenseForm) EmeraldGreen else Color.Transparent)
                                        .clickable { isExpenseForm = false }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Income", color = if (!isExpenseForm) Color.White else textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val calDate = Calendar.getInstance().apply { time = transactionDate }
                                val today = Calendar.getInstance()
                                val isToday = calDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) && calDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                                val dateText = if (isToday) "Today" else SimpleDateFormat("dd MMM", Locale.getDefault()).format(transactionDate)

                                Surface(
                                    shape = RoundedCornerShape(50.dp),
                                    color = highlightColor.copy(alpha = 0.1f),
                                    modifier = Modifier.height(36.dp).clickable { focusManager.clearFocus(); showDatePicker(context) { transactionDate = it } }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 14.dp)
                                    ) {
                                        Icon(Icons.Default.Event, contentDescription = "Date", tint = highlightColor, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(dateText, color = highlightColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                AnimatedVisibility(visible = !isToday) {
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(highlightColor.copy(alpha = 0.1f))
                                            .clickable { transactionDate = Date() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Restore, contentDescription = "Back to Today", tint = highlightColor, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = if (isEditMode) inputBg else highlightColor.copy(alpha = 0.05f),
                                border = BorderStroke(1.dp, if (isEditMode) Color.Transparent else highlightColor.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("৳", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = highlightColor.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (!isEditMode && existingAmount > 0) {
                                        Text(
                                            text = if (existingAmount % 1.0 == 0.0) existingAmount.toLong().toString() else existingAmount.toString(),
                                            fontSize = 32.sp,
                                            fontWeight = FontWeight.Black,
                                            color = highlightColor,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Box(
                                            modifier = Modifier.size(36.dp).clip(CircleShape).background(highlightColor.copy(alpha = 0.1f)).clickable { isEditMode = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = highlightColor, modifier = Modifier.size(16.dp))
                                        }
                                    } else {
                                        BasicTextField(
                                            value = transactionInput,
                                            onValueChange = { if (it.text.all { c -> c.isDigit() || c == '.' || c == '+' || c == '-' || c == '*' || c == '/' }) transactionInput = it },
                                            modifier = Modifier.weight(1f).focusRequester(transactionFocusRequester),
                                            textStyle = LocalTextStyle.current.copy(
                                                fontSize = 32.sp,
                                                fontWeight = FontWeight.Black,
                                                color = highlightColor
                                            ),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                                            keyboardActions = KeyboardActions(onDone = {
                                                handleSave(SaveOp.OVERWRITE, closeSheet = false)
                                                focusManager.clearFocus()
                                                isEditMode = false
                                            }),
                                            cursorBrush = SolidColor(highlightColor),
                                            decorationBox = { innerTextField ->
                                                if (transactionInput.text.isEmpty()) {
                                                    Text("0", fontSize = 32.sp, fontWeight = FontWeight.Black, color = highlightColor.copy(alpha = 0.3f))
                                                } else {
                                                    innerTextField()
                                                }
                                            }
                                        )

                                        AnimatedVisibility(
                                            visible = transactionInput.text.isNotEmpty(),
                                            enter = fadeIn(tween(300)) + slideInHorizontally(initialOffsetX = { it / 2 }, animationSpec = tween(300)),
                                            exit = fadeOut(tween(300)) + slideOutHorizontally(targetOffsetX = { it / 2 }, animationSpec = tween(300))
                                        ) {
                                            Box(
                                                modifier = Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(highlightColor).clickable {
                                                    handleSave(SaveOp.OVERWRITE, closeSheet = false)
                                                    focusManager.clearFocus()
                                                    isEditMode = false
                                                }.padding(horizontal = 16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("Save", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            if (isExpenseForm) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(availableCategories) { categoryName ->
                                        val isSelected = selectedCategoryName == categoryName
                                        val chipBg = if (isSelected) highlightColor else inputBg
                                        val chipTextColor = if (isSelected) Color.White else textColor

                                        Surface(
                                            modifier = Modifier.height(36.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            color = chipBg,
                                            border = if (!isSelected && !ThemeState.isDark.value) BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)) else null,
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxHeight().clickable {
                                                    if (selectedCategoryName != categoryName) {
                                                        if (isEditMode && transactionInput.text.isNotEmpty()) {
                                                            handleSave(SaveOp.OVERWRITE, closeSheet = false)
                                                        }
                                                        focusManager.clearFocus()
                                                        selectedCategoryName = categoryName
                                                    }
                                                }.padding(horizontal = 14.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = categoryName,
                                                    color = chipTextColor,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                    item {
                                        Box(
                                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF007AFF).copy(alpha = 0.1f)).clickable { showAddCategoryDialog = true },
                                            contentAlignment = Alignment.Center
                                        ) { Icon(Icons.Default.Add, "Add", tint = Color(0xFF007AFF), modifier = Modifier.size(20.dp)) }
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

// --- UTILITIES ---
fun borderStroke(width: androidx.compose.ui.unit.Dp, color: Color) = androidx.compose.foundation.BorderStroke(width, color)

fun showDatePicker(context: Context, onDateSelected: (Date) -> Unit) {
    val cal = Calendar.getInstance()
    DatePickerDialog(context, if (ThemeState.isDark.value) android.R.style.Theme_DeviceDefault_Dialog else android.R.style.Theme_DeviceDefault_Light_Dialog, { _, y, m, d ->
        onDateSelected(Calendar.getInstance().apply { set(y, m, d) }.time)
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
}