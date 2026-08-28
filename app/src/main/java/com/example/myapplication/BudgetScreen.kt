package com.example.myapplication

import android.content.Context
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen() {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val prefs = remember { context.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE) }
    val appSettings = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    val gson = remember { com.google.gson.Gson() }

    val transactions by DataManager.getTransactionsFlow(context).collectAsState(initial = DataManager.cachedTransactions ?: emptyList())
    val debts by DataManager.getDebtsFlow(context).collectAsState(initial = DataManager.cachedDebts ?: emptyList())
    val customCategories by DataManager.getCategoriesFlow(context).collectAsState(initial = DataManager.cachedCategories ?: emptyList())

    val isDark = ThemeState.isDark.value
    val textColor = if (isDark) Color.White else Color.Black
    val cardColor = ThemeState.cardBackground.value
    val bgColor = ThemeState.background.value
    val primaryColor = ThemeState.primaryAccent.value

    // ✨ HIGHLIGHT: Auto-Refresh Logic (Triggered when screen becomes visible)
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTrigger by remember { mutableStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++ // Force UI to reload data from SharedPreferences
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var isUnlocked by remember { mutableStateOf(false) }
    var showCategorySelectorDialog by remember { mutableStateOf(false) }
    var showBudgetSettingsSheet by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var categoryToRemove by remember { mutableStateOf<String?>(null) }
    var selectedCategoryDetails by remember { mutableStateOf<String?>(null) }
    var showSafeDailySpendDetailSheet by remember { mutableStateOf(false) }

    // --- PASSWORD STATES ---
    var showInitialPasswordPrompt by remember { mutableStateOf(false) }
    var showSetupPasswordDialog by remember { mutableStateOf(false) }
    var showLoginPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var wrongPassword by remember { mutableStateOf(false) }
    var isFoodCentric by remember { mutableStateOf(appSettings.getBoolean("pref_food_centric", true)) }
    var isMessCentric by remember { mutableStateOf(appSettings.getBoolean("pref_mess_centric_mode", false)) }
    var askedBudgetPassword by remember { mutableStateOf(appSettings.getBoolean("asked_budget_password", false)) }
    var isBudgetPasswordEnabled by remember { mutableStateOf(appSettings.getBoolean("is_budget_password_enabled", false)) }
    var savedPass by remember { mutableStateOf(appSettings.getString("app_password", "") ?: "") }

    // --- 🧠 EXPERT LOGIC & CALCULATIONS (PACING ALGORITHM) ---
    val calendar = Calendar.getInstance()
    val totalDaysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val currentDayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
    val daysRemaining = maxOf(1, totalDaysInMonth - currentDayOfMonth + 1)

    val monthProgress = currentDayOfMonth.toFloat() / totalDaysInMonth.toFloat()

    var savingsGoal by remember { mutableDoubleStateOf(prefs.getFloat("monthly_savings_goal", 0f).toDouble()) }
    var showSavingsGoalDialog by remember { mutableStateOf(false) }

    val currentBalance = ExpenseCalculator.getThisMonthBalance(context, transactions, debts)
    val thisMonthTransactions = transactions.filter { ExpenseCalculator.isThisMonth(it.date) && it.type == TransactionType.EXPENSE }
    val todayTransactions = transactions.filter { isTodayLocal(it.date.time) && it.type == TransactionType.EXPENSE }

    val totalTodaySpent = todayTransactions.filter { !it.category.contains("Mess", ignoreCase = true) && !it.id.startsWith("mess_") }.sumOf { it.amount }
    val totalSpentThisMonth = thisMonthTransactions.sumOf { it.amount }

    val totalBudgetBase = currentBalance + totalSpentThisMonth

    // Spendable budget after setting aside the monthly savings goal
    val spendableBalance = maxOf(0.0, currentBalance - savingsGoal)
    val spendableBudgetBase = maxOf(0.0, totalBudgetBase - savingsGoal)

    val overallBudgetProgress = if (spendableBudgetBase > 0) (totalSpentThisMonth / spendableBudgetBase).toFloat() else 0f
    val isOverPacing = overallBudgetProgress > monthProgress

    val safeDailySpend = if (spendableBalance > 0) (spendableBalance + totalTodaySpent) / daysRemaining else 0.0
    val leftForToday = safeDailySpend - totalTodaySpent
    val nextDayBudget = if (daysRemaining > 1) spendableBalance / (daysRemaining - 1) else 0.0

    fun isSameCategory(cat1: String, cat2: String): Boolean {
        val clean1 = cat1.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
        val clean2 = cat2.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
        return clean1 == clean2 || cat1.equals(cat2, ignoreCase = true)
    }

    fun isFoodCategory(name: String): Boolean {
        val clean = name.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
        return clean in listOf("breakfast", "lunch", "dinner", "snacks", "food", "meal", "meals")
    }

    val builtInFoodCategories = listOf("Breakfast", "Lunch", "Dinner", "Snacks")
    val allPossibleCategoriesSet = remember(customCategories, transactions, isFoodCentric, isMessCentric) {
        val set = mutableSetOf<String>()
        val active = DataManager.getActiveCategories(context, customCategories).filter { !it.contains("Mess", ignoreCase = true) }
        if (isMessCentric) {
            val nonMessFood = listOf("breakfast", "lunch", "dinner", "food")
            active.filter { !nonMessFood.contains(it.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()) }.forEach { set.add(it) }
            transactions.map { it.category }
                .filter { it.isNotBlank() && it != "Income" && !it.contains("Mess", ignoreCase = true) && !nonMessFood.contains(it.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()) }
                .forEach { set.add(it) }
        } else if (isFoodCentric) {
            set.add("🍲 Food")
            active.filter { !isFoodCategory(it) }.forEach { set.add(it) }
            transactions.map { it.category }
                .filter { it.isNotBlank() && it != "Income" && !isFoodCategory(it) && !it.contains("Mess", ignoreCase = true) }
                .forEach { set.add(it) }
        } else {
            set.addAll(active)
            if (!set.any { isSameCategory(it, "Food") }) {
                set.add("🍲 Food")
            }
            transactions.map { it.category }.filter { it.isNotBlank() && it != "Income" && !it.contains("Mess", ignoreCase = true) }.forEach { set.add(it) }
        }
        set
    }

    val curatedListType = object : com.google.gson.reflect.TypeToken<MutableSet<String>>() {}.type
    var curatedBudgetList by remember {
        val rawSet = gson.fromJson<MutableSet<String>>(prefs.getString("curated_budget_list", "[]"), curatedListType) ?: mutableSetOf()
        val cleaned = rawSet.filter { !it.contains("Mess", ignoreCase = true) }.toMutableSet()
        mutableStateOf(cleaned)
    }

    val limitsType = object : com.google.gson.reflect.TypeToken<MutableMap<String, Double>>() {}.type
    var categoryLimits by remember { mutableStateOf(gson.fromJson<MutableMap<String, Double>>(prefs.getString("category_limits", "{}"), limitsType) ?: mutableMapOf()) }

    var limitInputs by remember(curatedBudgetList, isUnlocked, isFoodCentric, isMessCentric) {
        val map = mutableMapOf<String, String>()
        if (isFoodCentric && !isMessCentric && curatedBudgetList.any { isFoodCategory(it) }) {
            val foodLimit = categoryLimits["🍲 Food"] ?: categoryLimits["Food"] ?: curatedBudgetList.filter { isFoodCategory(it) }.sumOf { categoryLimits[it] ?: 0.0 }
            if (foodLimit > 0.0) map["🍲 Food"] = String.format(Locale.US, "%.0f", foodLimit)
        }
        curatedBudgetList.filter { 
            if (isMessCentric) !listOf("breakfast", "lunch", "dinner", "food", "mess").contains(it.replace(Regex("[^a-zA-Z0-9]"), "").lowercase())
            else !it.contains("Mess", ignoreCase = true) && (!isFoodCentric || !isFoodCategory(it)) 
        }.forEach { cat ->
            val limit = categoryLimits[cat] ?: 0.0
            if (limit > 0.0) map[cat] = String.format(Locale.US, "%.0f", limit)
        }
        mutableStateOf(map)
    }

    val totalAllocated = limitInputs.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
    val unallocatedBalance = maxOf(0.0, spendableBudgetBase - totalAllocated)

    // ✨ HIGHLIGHT: ICON & COLOR METADATA FETCHING WITH AUTO-REFRESH ✨
    val categoryIconMap = mapOf(
        "Wallet" to Icons.Outlined.AccountBalanceWallet, "Food" to Icons.Outlined.Restaurant,
        "Shopping" to Icons.Outlined.ShoppingCart, "Transport" to Icons.Outlined.DirectionsCar,
        "Health" to Icons.Outlined.LocalHospital, "Home" to Icons.Outlined.Home,
        "Education" to Icons.Outlined.School, "Entertainment" to Icons.Outlined.Movie,
        "Pets" to Icons.Outlined.Pets, "Travel" to Icons.Outlined.Flight,
        "Tech" to Icons.Outlined.Smartphone, "Gifts" to Icons.Outlined.CardGiftcard,
        "Label" to Icons.AutoMirrored.Outlined.Label
    )
    val categoryColorList = listOf(
        Color(0xFF007AFF), Color(0xFF34C759), Color(0xFFFF9500), Color(0xFFAF52DE),
        Color(0xFFFF3B30), Color(0xFF00C6FF), Color(0xFFE91E63), Color(0xFF8E2DE2),
        Color(0xFF009688), Color(0xFFFFC107)
    )

    val metaType = object : com.google.gson.reflect.TypeToken<MutableMap<String, Map<String, String>>>() {}.type

    // Load metadata every time screen is resumed
    val categoryMetaStr = remember(refreshTrigger) { appSettings.getString("category_meta", "{}") ?: "{}" }
    val categoryMeta = remember(categoryMetaStr) {
        try { gson.fromJson<MutableMap<String, Map<String, String>>>(categoryMetaStr, metaType) ?: mutableMapOf() }
        catch (e: Exception) { mutableMapOf() }
    }

    // Refresh configurations dynamically
    LaunchedEffect(refreshTrigger) {
        isFoodCentric = appSettings.getBoolean("pref_food_centric", true)
        isMessCentric = appSettings.getBoolean("pref_mess_centric_mode", false)
        askedBudgetPassword = appSettings.getBoolean("asked_budget_password", false)
        isBudgetPasswordEnabled = appSettings.getBoolean("is_budget_password_enabled", false)
        savedPass = appSettings.getString("app_password", "") ?: ""

        val currentListStr = prefs.getString("curated_budget_list", "[]")
        curatedBudgetList = gson.fromJson<MutableSet<String>>(currentListStr, curatedListType) ?: mutableSetOf()

        val currentLimitsStr = prefs.getString("category_limits", "{}")
        categoryLimits = gson.fromJson<MutableMap<String, Double>>(currentLimitsStr, limitsType) ?: mutableMapOf()
    }

    fun getIconForCat(name: String): ImageVector {
        val savedIcon = categoryMeta[name]?.get("icon")
        if (savedIcon != null && categoryIconMap.containsKey(savedIcon)) {
            return categoryIconMap[savedIcon]!!
        }

        return when (name.lowercase(Locale.ROOT)) {
            "food", "breakfast", "lunch", "dinner", "snacks", "groceries" -> Icons.Outlined.Restaurant
            "transport", "bus", "uber", "ride", "fuel" -> Icons.Outlined.DirectionsCar
            "shopping", "clothes", "accessories" -> Icons.Outlined.ShoppingCart
            "health", "medical", "doctor", "pharmacy" -> Icons.Outlined.LocalHospital
            "home", "rent", "bills", "utilities", "electricity" -> Icons.Outlined.Home
            "education", "school", "books", "tuition" -> Icons.Outlined.School
            "entertainment", "movie", "fun", "games", "netflix" -> Icons.Outlined.Movie
            "pets", "dog", "cat" -> Icons.Outlined.Pets
            "travel", "tour", "flight", "hotel" -> Icons.Outlined.Flight
            "tech", "gadgets", "mobile", "internet" -> Icons.Outlined.Smartphone
            "gifts", "gift", "donation", "charity" -> Icons.Outlined.CardGiftcard
            "wallet", "salary", "income", "savings" -> Icons.Outlined.AccountBalanceWallet
            else -> Icons.AutoMirrored.Outlined.Label
        }
    }

    fun getColorForCat(name: String): Color {
        val hexStr = categoryMeta[name]?.get("color")
        if (hexStr != null) {
            try {
                return Color(hexStr.toULong())
            } catch (e: Exception) { } // Fallback if parse fails
        }
        return categoryColorList[(name.hashCode() and 0x7FFFFFFF) % categoryColorList.size]
    }

    fun saveAndLock() {
        val newLimits = mutableMapOf<String, Double>()
        limitInputs.forEach { (cat, inputStr) -> newLimits[cat] = inputStr.toDoubleOrNull() ?: 0.0 }
        categoryLimits = newLimits
        prefs.edit().putString("category_limits", gson.toJson(newLimits)).apply()

        if (CloudSyncManager.isUserLoggedIn()) {
            CloudSyncManager.backupToCloud(context) { _, _ -> }
        }

        isUnlocked = false
        focusManager.clearFocus()
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    fun removeBudgetCategory(catName: String) {
        val newSet = curatedBudgetList.toMutableSet()
        if (isFoodCentric && isFoodCategory(catName)) {
            newSet.removeAll { isFoodCategory(it) }
        } else {
            newSet.remove(catName)
        }
        curatedBudgetList = newSet
        prefs.edit().putString("curated_budget_list", gson.toJson(newSet)).apply()

        val newLimits = categoryLimits.toMutableMap()
        if (isFoodCentric && isFoodCategory(catName)) {
            newLimits.remove("🍲 Food")
            newLimits.remove("Food")
            builtInFoodCategories.forEach { newLimits.remove(it) }
        } else {
            newLimits.remove(catName)
        }
        categoryLimits = newLimits
        prefs.edit().putString("category_limits", gson.toJson(newLimits)).apply()

        val newInputs = limitInputs.toMutableMap()
        if (isFoodCentric && isFoodCategory(catName)) {
            newInputs.remove("🍲 Food")
            newInputs.remove("Food")
            builtInFoodCategories.forEach { newInputs.remove(it) }
        } else {
            newInputs.remove(catName)
        }
        limitInputs = newInputs

        if (CloudSyncManager.isUserLoggedIn()) {
            CloudSyncManager.backupToCloud(context) { _, _ -> }
        }
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    fun resetAllBudgets() {
        curatedBudgetList = mutableSetOf()
        prefs.edit().putString("curated_budget_list", "[]").apply()
        categoryLimits = mutableMapOf()
        prefs.edit().putString("category_limits", "{}").apply()
        limitInputs = mutableMapOf()

        if (CloudSyncManager.isUserLoggedIn()) {
            CloudSyncManager.backupToCloud(context) { _, _ -> }
        }
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        // --- MAIN SCREEN CONTENT ---
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {

            Spacer(modifier = Modifier.height(10.dp))
            Text("Budget Planner", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = textColor)
            Spacer(modifier = Modifier.height(14.dp))

            // --- 🌟 ULTRA-SLIM EXPERT MAIN CARD (DYNAMIC SIGNATURE GRADIENT) ---
            val headerGradient = ThemeState.headerGradient.value
            val cardShape = RoundedCornerShape(20.dp)

            val infiniteTransition = rememberInfiniteTransition(label = "glow_anim")
            val glowAlpha by infiniteTransition.animateFloat(
                initialValue = if(isDark) 0.3f else 0.45f,
                targetValue = if(isDark) 0.75f else 0.9f,
                animationSpec = infiniteRepeatable(animation = tween(2000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
                label = "alpha"
            )
            val glowScale by infiniteTransition.animateFloat(
                initialValue = 1.01f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(animation = tween(2000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
                label = "scale"
            )
            val glowColor = ThemeState.primaryAccent.value

            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.matchParentSize().scale(glowScale)
                        .background(Brush.radialGradient(listOf(glowColor.copy(alpha = glowAlpha), Color.Transparent)), shape = cardShape)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(headerGradient)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showSafeDailySpendDetailSheet = true
                        }
                        .padding(20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("SAFE DAILY SPEND", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.75f))
                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (totalSpentThisMonth > 0) {
                                        Surface(color = if (isOverPacing) Color(0xFFFF3B30).copy(0.2f) else Color(0xFF34C759).copy(0.2f), shape = RoundedCornerShape(6.dp)) {
                                            Text(if (isOverPacing) "Overpacing" else "On Track", color = if (isOverPacing) Color(0xFFFFCDD2) else Color(0xFFD1E8D2), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text("৳${String.format("%.0f", safeDailySpend)}", fontSize = 36.sp, fontWeight = FontWeight.Black, color = if (safeDailySpend < 0) Color(0xFFFFCDD2) else Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("/ day", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.65f), modifier = Modifier.padding(bottom = 5.dp))
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(color = if (leftForToday < 0) Color(0xFFFF3B30).copy(0.2f) else Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            text = if (leftForToday < 0) "Overspent: ৳${String.format("%.0f", Math.abs(leftForToday))}" else "Left Today: ৳${String.format("%.0f", leftForToday)}",
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                            color = if (leftForToday < 0) Color(0xFFFFCDD2) else Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }

                                    Surface(
                                        onClick = { showSavingsGoalDialog = true },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (savingsGoal > 0) Color(0xFFFFD700).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f)
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (savingsGoal > 0) "🎯 Goal: ৳${savingsGoal.toInt()}" else "🎯 + Save",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (savingsGoal > 0) Color(0xFFFFE082) else Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            Surface(color = Color.Black.copy(alpha = 0.25f), shape = RoundedCornerShape(8.dp)) {
                                Text("$daysRemaining Days Left", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text("WALLET", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp, color = Color.White.copy(alpha = 0.65f))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("৳${String.format("%.0f", currentBalance)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("SPENDABLE", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp, color = if (savingsGoal > 0) Color(0xFFFFE082) else Color.White.copy(alpha = 0.65f))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("৳${String.format("%.0f", spendableBalance)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (savingsGoal > 0) Color(0xFFFFE082) else Color.White)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("MONTH SPENT", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp, color = Color.White.copy(alpha = 0.65f))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("৳${String.format("%.0f", totalSpentThisMonth)}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("TOMORROW", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp, color = Color.White.copy(alpha = 0.65f))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(if (daysRemaining > 1) "৳${String.format("%.0f", nextDayBudget)}" else "Last Day", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // CATEGORY BUDGETS SECTION
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("My Budgets", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)

                if (isUnlocked) {
                    Button(onClick = { saveAndLock() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp), modifier = Modifier.height(34.dp)) {
                        Text("Save & Lock", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(onClick = { showCategorySelectorDialog = true }, shape = CircleShape, color = primaryColor.copy(alpha = 0.1f), modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = primaryColor, modifier = Modifier.padding(6.dp))
                        }
                        Surface(onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            showBudgetSettingsSheet = true
                        }, shape = CircleShape, color = cardColor, border = BorderStroke(1.dp, Color.Gray.copy(alpha=0.15f)), modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "Budget Settings", tint = Color.Gray, modifier = Modifier.padding(7.dp))
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isUnlocked) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Total Base: ৳${totalBudgetBase.toInt()}", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                    Surface(color = if (unallocatedBalance <= 0) Color.Red.copy(0.1f) else Color(0xFF34C759).copy(0.1f), shape = RoundedCornerShape(8.dp)) {
                        Text("Unallocated: ৳${unallocatedBalance.toInt()}", color = if (unallocatedBalance <= 0) Color.Red else Color(0xFF34C759), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val foodBudgets = if (isMessCentric) {
                emptyList()
            } else if (isFoodCentric) {
                if (curatedBudgetList.any { isFoodCategory(it) } || curatedBudgetList.contains("🍲 Food")) listOf("🍲 Food") else emptyList()
            } else {
                emptyList()
            }
            val otherBudgets = if (isMessCentric) {
                curatedBudgetList.filter { !listOf("breakfast", "lunch", "dinner", "food", "mess").contains(it.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()) && !it.contains("Mess", ignoreCase = true) }.sorted()
            } else if (isFoodCentric) {
                curatedBudgetList.filter { !isFoodCategory(it) && !it.contains("Mess", ignoreCase = true) }.sorted()
            } else {
                curatedBudgetList.filter { !it.contains("Mess", ignoreCase = true) }.sorted()
            }

            if (curatedBudgetList.isEmpty()) {
                BudgetEmptyAnimationCard(
                    spendableBalance = spendableBalance,
                    isMessCentric = isMessCentric,
                    isFoodCentric = isFoodCentric,
                    cardColor = cardColor,
                    textColor = textColor,
                    primaryColor = primaryColor,
                    isDark = isDark,
                    onAddClick = { showCategorySelectorDialog = true },
                    onQuickAddCategory = { cat ->
                        val newSet = curatedBudgetList.toMutableSet()
                        newSet.add(cat)
                        curatedBudgetList = newSet
                        prefs.edit().putString("curated_budget_list", gson.toJson(newSet)).apply()
                        if (!isUnlocked) isUnlocked = true
                        Toast.makeText(context, "Added $cat to budget! Set limit below.", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Section 1: Food & Meals (When Food Centric is ON and Mess Centric is OFF)
                    if (!isMessCentric && isFoodCentric && foodBudgets.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🍲 FOOD BUDGET (ALL MEALS)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor,
                                letterSpacing = 1.sp
                            )
                            val totalFoodLimit = categoryLimits["🍲 Food"] ?: categoryLimits["Food"] ?: curatedBudgetList.filter { isFoodCategory(it) }.sumOf { categoryLimits[it] ?: 0.0 }
                            if (totalFoodLimit > 0) {
                                Surface(shape = RoundedCornerShape(6.dp), color = primaryColor.copy(alpha = 0.12f)) {
                                    Text(
                                        text = "Limit: ৳${totalFoodLimit.toInt()}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        foodBudgets.forEach { catName ->
                            val spent = thisMonthTransactions.filter { isFoodCategory(it.category) }.sumOf { it.amount }
                            val catTodaySpent = todayTransactions.filter { isFoodCategory(it.category) }.sumOf { it.amount }
                            val limit = categoryLimits["🍲 Food"] ?: categoryLimits["Food"] ?: curatedBudgetList.filter { isFoodCategory(it) }.sumOf { categoryLimits[it] ?: 0.0 }

                            val totalOtherLimits = otherBudgets.sumOf { limitInputs[it]?.toDoubleOrNull() ?: 0.0 }
                            val maxAllocatable = maxOf(0.0, totalBudgetBase - totalOtherLimits)

                            val catColor = getColorForCat("Food")
                            val catIcon = getIconForCat("Food")

                            PremiumSlimBudgetRow(
                                title = DataManager.formatCategoryDisplay(context, catName), color = catColor, icon = catIcon, spent = spent, limit = limit,
                                input = limitInputs["🍲 Food"] ?: limitInputs["Food"] ?: "", maxAllocatable = maxAllocatable, totalBudgetBase = totalBudgetBase, monthProgress = monthProgress,
                                daysRemaining = daysRemaining,
                                todaySpent = catTodaySpent,
                                isUnlocked = isUnlocked, isDark = isDark, cardColor = cardColor, textColor = textColor,
                                onCardClick = { if(!isUnlocked) { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); selectedCategoryDetails = catName } },
                                onRemoveClick = { categoryToRemove = catName }
                            ) { newValue ->
                                val parsedVal = newValue.toDoubleOrNull() ?: 0.0
                                val safeVal = if (parsedVal > maxAllocatable) maxAllocatable.toInt().toString() else newValue
                                val newMap = limitInputs.toMutableMap()
                                newMap["🍲 Food"] = safeVal
                                limitInputs = newMap
                            }
                        }
                    }

                    // Section 2: General Budgets
                    if (otherBudgets.isNotEmpty()) {
                        if (isFoodCentric && foodBudgets.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "🏷️ GENERAL & LIVING EXPENSES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }

                        otherBudgets.forEach { catName ->
                            val spent = thisMonthTransactions.filter { isSameCategory(it.category, catName) }.sumOf { it.amount }
                            val catTodaySpent = todayTransactions.filter { isSameCategory(it.category, catName) }.sumOf { it.amount }
                            val limit = categoryLimits[catName] ?: 0.0

                            val foodAllocated = if (isFoodCentric && foodBudgets.isNotEmpty()) (limitInputs["🍲 Food"] ?: limitInputs["Food"])?.toDoubleOrNull() ?: 0.0 else 0.0
                            val totalOtherLimits = otherBudgets.filter { it != catName }.sumOf { limitInputs[it]?.toDoubleOrNull() ?: 0.0 } + foodAllocated
                            val maxAllocatable = maxOf(0.0, totalBudgetBase - totalOtherLimits)

                            val catColor = getColorForCat(catName)
                            val catIcon = getIconForCat(catName)

                            PremiumSlimBudgetRow(
                                title = DataManager.formatCategoryDisplay(context, catName), color = catColor, icon = catIcon, spent = spent, limit = limit,
                                input = limitInputs[catName] ?: "", maxAllocatable = maxAllocatable, totalBudgetBase = totalBudgetBase, monthProgress = monthProgress,
                                daysRemaining = daysRemaining,
                                todaySpent = catTodaySpent,
                                isUnlocked = isUnlocked, isDark = isDark, cardColor = cardColor, textColor = textColor,
                                onCardClick = { if(!isUnlocked) { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); selectedCategoryDetails = catName } },
                                onRemoveClick = { categoryToRemove = catName }
                            ) { newValue ->
                                val parsedVal = newValue.toDoubleOrNull() ?: 0.0
                                val safeVal = if (parsedVal > maxAllocatable) maxAllocatable.toInt().toString() else newValue
                                val newMap = limitInputs.toMutableMap()
                                newMap[catName] = safeVal
                                limitInputs = newMap
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        // --- 📊 HIGHLIGHT: BUTTER SMOOTH EXPERT INSIGHTS OVERLAY ---
        AnimatedVisibility(
            visible = selectedCategoryDetails != null,
            enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = spring(dampingRatio = 0.8f, stiffness = 350f)) + fadeIn(tween(200)),
            exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = spring(dampingRatio = 0.8f, stiffness = 350f)) + fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize().zIndex(10f)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
                BackHandler(enabled = selectedCategoryDetails != null) { selectedCategoryDetails = null }

                selectedCategoryDetails?.let { catName ->
                    val isGroupedFood = isFoodCentric && isSameCategory(catName, "Food")
                    val catTransactions = if (!isGroupedFood) thisMonthTransactions.filter { isSameCategory(it.category, catName) } else thisMonthTransactions.filter { isFoodCategory(it.category) }
                    val spent = catTransactions.sumOf { it.amount }
                    val limit = categoryLimits[catName] ?: 0.0
                    val remaining = limit - spent
                    val catColor = getColorForCat(catName)
                    val catIcon = getIconForCat(catName)
                    val dailyCatSafe = maxOf(0.0, remaining / daysRemaining)
                    val spentRatio = if (limit > 0) (spent / limit).toFloat().coerceIn(0f, 1f) else 0f

                    val isCatOverPacing = limit > 0 && spentRatio > monthProgress

                    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp)) {
                            IconButton(onClick = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); selectedCategoryDetails = null }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textColor, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Budget Insights", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textColor)
                        }

                        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                            Spacer(modifier = Modifier.height(10.dp))

                            Surface(shape = RoundedCornerShape(24.dp), color = cardColor, shadowElevation = if(isDark) 0.dp else 4.dp, border = if(isDark) BorderStroke(1.dp, Color.White.copy(0.05f)) else null, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(catColor.copy(0.15f)), contentAlignment = Alignment.Center) {
                                                Icon(catIcon, contentDescription = null, tint = catColor, modifier = Modifier.size(18.dp))
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(catName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor)
                                        }

                                        if (limit > 0 && spent > 0) {
                                            Surface(color = if (isCatOverPacing) Color(0xFFFF3B30).copy(0.1f) else Color(0xFF34C759).copy(0.1f), shape = RoundedCornerShape(8.dp)) {
                                                Text(if (isCatOverPacing) "Overpacing 🔥" else "On Track ✅", color = if (isCatOverPacing) Color(0xFFFF3B30) else Color(0xFF34C759), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(20.dp))

                                    Text(if(limit > 0) "AVAILABLE BUDGET" else "TOTAL SPENT", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (limit > 0) "৳${String.format("%.0f", remaining)}" else "৳${String.format("%.0f", spent)}",
                                        fontSize = 48.sp, fontWeight = FontWeight.Black,
                                        color = if (limit > 0 && remaining < 0) Color(0xFFFF3B30) else textColor
                                    )

                                    if (limit > 0) {
                                        Spacer(modifier = Modifier.height(24.dp))
                                        LinearProgressIndicator(progress = { spentRatio }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = if (spentRatio >= 1f) Color(0xFFFF3B30) else catColor, trackColor = Color.Gray.copy(0.15f))
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Column(horizontalAlignment = Alignment.Start) {
                                                Text("Spent", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                                Text("৳${String.format("%.0f", spent)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("Monthly Limit", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                                Text("৳${String.format("%.0f", limit)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            if (limit > 0) {
                                Surface(shape = RoundedCornerShape(16.dp), color = catColor.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column {
                                            Text("Safe Daily Spend", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                                            Text("Based on $daysRemaining days left", fontSize = 12.sp, color = Color.Gray)
                                        }
                                        Text("৳${String.format("%.0f", dailyCatSafe)}", fontSize = 24.sp, fontWeight = FontWeight.Black, color = catColor)
                                    }
                                }

                                if (isCatOverPacing && remaining > 0) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("⚠️ You are spending faster than the month is passing. Slow down to avoid running out of budget before the month ends.", color = Color(0xFFFF9500), fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 4.dp))
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        selectedCategoryDetails = null
                                        categoryToRemove = catName
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30).copy(alpha = 0.1f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF3B30), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Remove $catName From Budgets", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                            }

                            Text("Recent Transactions", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                            Spacer(modifier = Modifier.height(12.dp))

                            if (catTransactions.isEmpty()) {
                                Text("No expenses recorded yet for $catName.", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 20.dp))
                            } else {
                                val format = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                                catTransactions.sortedByDescending { it.date }.forEach { t ->
                                    Surface(shape = RoundedCornerShape(12.dp), color = cardColor, shadowElevation = if(isDark) 0.dp else 1.dp, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Column {
                                                Text(t.category, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                                                Text(format.format(t.date), fontSize = 12.sp, color = Color.Gray)
                                            }
                                            Text("-৳${String.format("%.0f", t.amount)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF3B30))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                        }
                    }
                }
            }
        }

        // --- 🔐 DYNAMIC PASSWORD DIALOGS ---

        if (showInitialPasswordPrompt) {
            AlertDialog(
                containerColor = cardColor,
                onDismissRequest = { showInitialPasswordPrompt = false },
                title = { Text("Secure Budget?", fontWeight = FontWeight.Bold, color = textColor) },
                text = { Text("Do you want to require a password to edit your budget limits? You can change this later in Settings.", color = Color.Gray, fontSize = 14.sp) },
                confirmButton = {
                    Button(onClick = {
                        showInitialPasswordPrompt = false
                        appSettings.edit().putBoolean("asked_budget_password", true).apply()
                        askedBudgetPassword = true
                        showSetupPasswordDialog = true
                    }, colors = ButtonDefaults.buttonColors(containerColor = primaryColor), shape = RoundedCornerShape(10.dp)) { Text("Set Password", color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showInitialPasswordPrompt = false
                        appSettings.edit().putBoolean("asked_budget_password", true).apply()
                        appSettings.edit().putBoolean("is_budget_password_enabled", false).apply()
                        askedBudgetPassword = true
                        isBudgetPasswordEnabled = false
                        isUnlocked = true
                    }) { Text("Not Now", color = Color.Gray) }
                }
            )
        }

        if (showSetupPasswordDialog) {
            AlertDialog(
                containerColor = cardColor,
                onDismissRequest = { showSetupPasswordDialog = false; passwordInput = "" },
                title = { Text("Create Password", fontWeight = FontWeight.Bold, color = textColor) },
                text = {
                    Column {
                        Text("Enter a minimum 4-digit password.", color = Color.Gray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(15.dp))
                        OutlinedTextField(value = passwordInput, onValueChange = { passwordInput = it }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), label = { Text("New Password") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor))
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (passwordInput.length >= 4) {
                            appSettings.edit().putString("app_password", passwordInput).putBoolean("is_budget_password_enabled", true).apply()
                            isBudgetPasswordEnabled = true
                            isUnlocked = true
                            showSetupPasswordDialog = false
                            passwordInput = ""
                            Toast.makeText(context, "Password Saved!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Must be at least 4 chars", Toast.LENGTH_SHORT).show()
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = primaryColor), shape = RoundedCornerShape(10.dp)) { Text("Save & Unlock", color = Color.White) }
                },
                dismissButton = { TextButton(onClick = { showSetupPasswordDialog = false; passwordInput = "" }) { Text("Cancel", color = Color.Gray) } }
            )
        }

        if (showLoginPasswordDialog) {
            fun authenticateAndUnlock() {
                val activity = context.getActivity() ?: return
                val executor = ContextCompat.getMainExecutor(activity)
                val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { isUnlocked = true; showLoginPasswordDialog = false; wrongPassword = false; passwordInput = "" }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
                })
                biometricPrompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("Verify Identity").setSubtitle("Unlock to edit limits").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL).build())
            }

            AlertDialog(
                containerColor = cardColor,
                onDismissRequest = { showLoginPasswordDialog = false; wrongPassword = false; passwordInput = "" },
                title = { Text("Security Check", fontWeight = FontWeight.Bold, color = textColor) },
                text = {
                    Column {
                        Text(if (wrongPassword) "Wrong Password!" else "Enter your password or use fingerprint to unlock.", color = if (wrongPassword) Color.Red else Color.Gray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(15.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = passwordInput, onValueChange = { passwordInput = it }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f), label = { Text("Password") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor))
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(modifier = Modifier.size(56.dp).padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(primaryColor.copy(0.1f)).clickable { authenticateAndUnlock() }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Lock, null, tint = primaryColor) }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (passwordInput == savedPass) {
                            isUnlocked = true
                            showLoginPasswordDialog = false
                            wrongPassword = false
                            passwordInput = ""
                        } else {
                            wrongPassword = true
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = primaryColor), shape = RoundedCornerShape(10.dp)) { Text("Unlock", color = Color.White) }
                },
                dismissButton = { TextButton(onClick = { showLoginPasswordDialog = false; wrongPassword = false; passwordInput = "" }) { Text("Cancel", color = Color.Gray) } }
            )
        }

        if (showCategorySelectorDialog) {
            var tempCuratedList by remember { mutableStateOf(curatedBudgetList.toSet()) }

            val allCats = allPossibleCategoriesSet.filter { !it.contains("Mess", ignoreCase = true) }.toList()
            val generalCats = if (isMessCentric) {
                allCats.filter { !listOf("breakfast", "lunch", "dinner", "food", "mess").contains(it.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()) && !it.contains("Mess", ignoreCase = true) }.sorted()
            } else if (isFoodCentric) {
                allCats.filter { !isFoodCategory(it) && !it.contains("Mess", ignoreCase = true) }.sorted()
            } else {
                allCats.filter { !it.contains("Mess", ignoreCase = true) }.sorted()
            }

            AlertDialog(
                containerColor = cardColor,
                onDismissRequest = { showCategorySelectorDialog = false },
                title = {
                    Column {
                        Text("Select Budgets", fontWeight = FontWeight.Bold, color = textColor, fontSize = 20.sp)
                        if (isMessCentric) {
                            Text("Mess-Centric mode active 🏠", color = primaryColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        } else if (isFoodCentric) {
                            Text("Food-Centric mode active 🍲", color = primaryColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                text = {
                    Column(modifier = Modifier.height(340.dp)) {
                        Text("Choose which categories appear in your budget plan.", color = Color.Gray, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
                            // Section 1: Food & Meals (When Food Centric is ON and Mess Centric is OFF)
                            if (!isMessCentric && isFoodCentric) {
                                item {
                                    Text(
                                        text = "🍲 FOOD BUDGET (ALL MEALS)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryColor,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                    )
                                }

                                item {
                                    val isFoodChecked = tempCuratedList.any { isFoodCategory(it) } || tempCuratedList.contains("🍲 Food")
                                    val catColor = getColorForCat("Food")
                                    val catIcon = getIconForCat("Food")

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isFoodChecked) primaryColor.copy(alpha = 0.08f) else Color.Transparent,
                                        border = if (isFoodChecked) BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f)) else null,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                val newSet = tempCuratedList.toMutableSet()
                                                if (isFoodChecked) {
                                                    newSet.removeAll { isFoodCategory(it) }
                                                    newSet.remove("🍲 Food")
                                                } else {
                                                    newSet.add("🍲 Food")
                                                }
                                                tempCuratedList = newSet
                                            }.padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Checkbox(checked = isFoodChecked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = primaryColor))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(catColor.copy(0.15f)), contentAlignment = Alignment.Center) {
                                                Icon(catIcon, contentDescription = null, tint = catColor, modifier = Modifier.size(16.dp))
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(DataManager.formatCategoryDisplay(context, "🍲 Food"), color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                                Text("Includes Breakfast, Lunch, Dinner, Snacks", color = Color.Gray, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            // Section 2: General & Living Expenses
                            if (generalCats.isNotEmpty()) {
                                item {
                                    if (isFoodCentric) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    Text(
                                        text = if (isFoodCentric) "🏷️ GENERAL & LIVING EXPENSES" else "ALL CATEGORIES",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                    )
                                }

                                items(generalCats) { catName ->
                                    val isChecked = tempCuratedList.contains(catName)
                                    val catColor = getColorForCat(catName)
                                    val catIcon = getIconForCat(catName)

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isChecked) primaryColor.copy(alpha = 0.08f) else Color.Transparent,
                                        border = if (isChecked) BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f)) else null,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                val newSet = tempCuratedList.toMutableSet()
                                                if (isChecked) newSet.remove(catName) else newSet.add(catName)
                                                tempCuratedList = newSet
                                            }.padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Checkbox(checked = isChecked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = primaryColor))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(catColor.copy(0.15f)), contentAlignment = Alignment.Center) {
                                                Icon(catIcon, contentDescription = null, tint = catColor, modifier = Modifier.size(16.dp))
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(DataManager.formatCategoryDisplay(context, catName), color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        curatedBudgetList = tempCuratedList.toMutableSet()
                        prefs.edit().putString("curated_budget_list", gson.toJson(curatedBudgetList)).apply()
                        showCategorySelectorDialog = false
                        if (isUnlocked) saveAndLock()
                    }, colors = ButtonDefaults.buttonColors(containerColor = primaryColor), shape = RoundedCornerShape(10.dp)) { Text("Save", color = Color.White, fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showCategorySelectorDialog = false }) { Text("Cancel", color = Color.Gray) } }
            )
        }

        // 🌟 BUDGET SETTINGS BOTTOM SHEET
        if (showBudgetSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBudgetSettingsSheet = false },
                containerColor = cardColor,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 28.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Budget Settings",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = textColor
                            )
                            Text(
                                text = "Manage budget plans, limits & security",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        IconButton(onClick = { showBudgetSettingsSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }

                    // Option 0: Set Monthly Savings Goal
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                        modifier = Modifier.fillMaxWidth().clickable {
                            showBudgetSettingsSheet = false
                            showSavingsGoalDialog = true
                        }
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFFFD700).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Savings, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Monthly Savings Goal", fontWeight = FontWeight.Bold, color = textColor, fontSize = 15.sp)
                                Text(if (savingsGoal > 0) "Target: ৳${savingsGoal.toInt()} (Spendable: ৳${spendableBalance.toInt()})" else "Reserve money from total balance as savings", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 1: Edit Limits
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                        modifier = Modifier.fillMaxWidth().clickable {
                            showBudgetSettingsSheet = false
                            if (!isUnlocked) {
                                if (!askedBudgetPassword) {
                                    showInitialPasswordPrompt = true
                                } else if (isBudgetPasswordEnabled && savedPass.isNotEmpty()) {
                                    showLoginPasswordDialog = true
                                } else {
                                    isUnlocked = true
                                }
                            }
                        }
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(if (isUnlocked) "Editing Limits (Active)" else "Edit Budget Limits", fontWeight = FontWeight.Bold, color = textColor, fontSize = 15.sp)
                                Text("Adjust limits and sliders for each category", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 2: Add / Manage Categories
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                        modifier = Modifier.fillMaxWidth().clickable {
                            showBudgetSettingsSheet = false
                            showCategorySelectorDialog = true
                        }
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFFF9500).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFFFF9500), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Add / Select Categories", fontWeight = FontWeight.Bold, color = textColor, fontSize = 15.sp)
                                Text("Choose categories for your budget (${curatedBudgetList.size} active)", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 3: Reset / Clear All Budgets
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                        modifier = Modifier.fillMaxWidth().clickable {
                            showResetConfirmDialog = true
                        }
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFFF3B30).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF3B30), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Clear All Budgets", fontWeight = FontWeight.Bold, color = Color(0xFFFF3B30), fontSize = 15.sp)
                                Text("Remove all active budget categories and limits", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option 4: Food-Centric Budget Toggle
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF34C759).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.RestaurantMenu, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text("Food-Centric Budget", fontWeight = FontWeight.Bold, color = textColor, fontSize = 15.sp)
                                    Text("Groups meals under Food budget", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                            Switch(
                                checked = isFoodCentric,
                                onCheckedChange = { checked ->
                                    isFoodCentric = checked
                                    appSettings.edit().putBoolean("pref_food_centric", checked).apply()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = primaryColor)
                            )
                        }
                    }

                    // Section 5: Active Budgets list with single remove buttons
                    if (curatedBudgetList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "ACTIVE BUDGETS (${curatedBudgetList.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val activeList = if (isMessCentric) {
                            curatedBudgetList.filter { !listOf("breakfast", "lunch", "dinner", "food", "mess").contains(it.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()) && !it.contains("Mess", ignoreCase = true) }.sorted()
                        } else if (isFoodCentric) {
                            (if (curatedBudgetList.any { isFoodCategory(it) } || curatedBudgetList.contains("🍲 Food")) listOf("🍲 Food") else emptyList()) + curatedBudgetList.filter { !isFoodCategory(it) && !it.contains("Mess", ignoreCase = true) }.sorted()
                        } else {
                            curatedBudgetList.filter { !it.contains("Mess", ignoreCase = true) }.sorted()
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)
                        ) {
                            items(activeList) { cat ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isDark) Color(0xFF242426) else Color(0xFFF7F7F9),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = DataManager.formatCategoryDisplay(context, cat),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = textColor
                                        )
                                        IconButton(
                                            onClick = {
                                                categoryToRemove = cat
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color(0xFFFF3B30).copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Reset All Budgets Confirmation Dialog
        if (showResetConfirmDialog) {
            AlertDialog(
                containerColor = cardColor,
                onDismissRequest = { showResetConfirmDialog = false },
                title = { Text("Clear All Budgets?", fontWeight = FontWeight.Bold, color = textColor) },
                text = { Text("Are you sure you want to remove all categories from your budget plan? Your transaction history will remain safe.", color = Color.Gray, fontSize = 14.sp) },
                confirmButton = {
                    Button(
                        onClick = {
                            resetAllBudgets()
                            showResetConfirmDialog = false
                            showBudgetSettingsSheet = false
                            Toast.makeText(context, "All budgets cleared", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Clear All", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirmDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }

        // Delete Individual Category Confirmation Dialog
        if (categoryToRemove != null) {
            AlertDialog(
                containerColor = cardColor,
                onDismissRequest = { categoryToRemove = null },
                title = { Text("Remove Budget?", fontWeight = FontWeight.Bold, color = textColor) },
                text = { Text("Are you sure you want to remove '${categoryToRemove}' from your budget plan? Existing transactions will remain safe.", color = Color.Gray, fontSize = 14.sp) },
                confirmButton = {
                    Button(
                        onClick = {
                            categoryToRemove?.let { removeBudgetCategory(it) }
                            categoryToRemove = null
                            Toast.makeText(context, "Budget removed", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { categoryToRemove = null }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }

        // Monthly Savings Goal Dialog
        if (showSavingsGoalDialog) {
            var goalInput by remember { mutableStateOf(if (savingsGoal > 0) String.format(Locale.US, "%.0f", savingsGoal) else "") }
            val enteredGoal = goalInput.toDoubleOrNull() ?: 0.0
            val previewSpendable = maxOf(0.0, currentBalance - enteredGoal)

            AlertDialog(
                containerColor = cardColor,
                onDismissRequest = { showSavingsGoalDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFFFFD700).copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                            Text("🎯", fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Monthly Savings Goal", fontWeight = FontWeight.Bold, color = textColor, fontSize = 18.sp)
                    }
                },
                text = {
                    Column {
                        Text(
                            "Reserve funds from your total balance. Your safe daily spend and category budgets will calculate from spendable balance only.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Current Balance:", fontSize = 12.sp, color = Color.Gray)
                                    Text("৳${String.format("%,.0f", currentBalance)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Spendable Pool:", fontSize = 12.sp, color = Color.Gray)
                                    Text("৳${String.format("%,.0f", previewSpendable)}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = primaryColor)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = goalInput,
                            onValueChange = { goalInput = it },
                            label = { Text("Savings Target (৳)") },
                            placeholder = { Text("e.g. 1000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor,
                                focusedBorderColor = primaryColor
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Presets Chips
                        Text("Quick Presets:", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf(500.0, 1000.0, 2000.0).forEach { preset ->
                                Surface(
                                    onClick = { goalInput = preset.toInt().toString() },
                                    shape = RoundedCornerShape(8.dp),
                                    color = primaryColor.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        text = "৳${preset.toInt()}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            if (currentBalance > 0) {
                                val tenPercent = (currentBalance * 0.10).toInt()
                                val twentyPercent = (currentBalance * 0.20).toInt()
                                Surface(
                                    onClick = { goalInput = tenPercent.toString() },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF34C759).copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "10% (৳$tenPercent)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF34C759),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val parsed = goalInput.toDoubleOrNull() ?: 0.0
                            val clamped = minOf(parsed, currentBalance)
                            savingsGoal = clamped
                            prefs.edit().putFloat("monthly_savings_goal", clamped.toFloat()).apply()
                            showSavingsGoalDialog = false
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            Toast.makeText(context, if (clamped > 0) "Savings Goal set to ৳${clamped.toInt()} 🎯" else "Savings Goal cleared", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save Target", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    if (savingsGoal > 0) {
                        TextButton(onClick = {
                            savingsGoal = 0.0
                            prefs.edit().putFloat("monthly_savings_goal", 0f).apply()
                            showSavingsGoalDialog = false
                            Toast.makeText(context, "Savings Goal cleared", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("Clear Goal", color = Color(0xFFFF3B30))
                        }
                    } else {
                        TextButton(onClick = { showSavingsGoalDialog = false }) {
                            Text("Cancel", color = Color.Gray)
                        }
                    }
                }
            )
        }

        // 💡 SAFE DAILY SPEND DETAIL MODAL BOTTOM SHEET
        if (showSafeDailySpendDetailSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSafeDailySpendDetailSheet = false },
                containerColor = cardColor,
                dragHandle = { BottomSheetDefaults.DragHandle() },
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(primaryColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("💡", fontSize = 18.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Safe Daily Spend Analysis", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = textColor)
                                Text("Real-time pacing & today's status", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
                                .clickable { showSafeDailySpendDetailSheet = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Primary Hero Card: Today's Target vs Spent
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isDark) Color(0xFF202026) else Color(0xFFF4F6F9),
                        border = BorderStroke(1.dp, if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("TODAY'S TARGET", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
                                Surface(
                                    color = if (leftForToday < 0) Color(0xFFFF3B30).copy(0.15f) else Color(0xFF34C759).copy(0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (leftForToday < 0) "Overspent Today 🔥" else "Within Limit ✅",
                                        color = if (leftForToday < 0) Color(0xFFFF3B30) else Color(0xFF34C759),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("৳${String.format("%.0f", safeDailySpend)}", fontSize = 34.sp, fontWeight = FontWeight.Black, color = textColor)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("assigned for today", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 4.dp))
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Today's Progress Bar
                            val todaySpentRatio = if (safeDailySpend > 0) (totalTodaySpent / safeDailySpend).toFloat().coerceIn(0f, 1f) else 0f
                            LinearProgressIndicator(
                                progress = { todaySpentRatio },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                color = if (leftForToday < 0) Color(0xFFFF3B30) else primaryColor,
                                trackColor = Color.Gray.copy(alpha = 0.18f)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Spent Today", fontSize = 11.5.sp, color = Color.Gray)
                                    Text("৳${String.format("%.0f", totalTodaySpent)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(if (leftForToday < 0) "Overspent By" else "Remaining for Today", fontSize = 11.5.sp, color = Color.Gray)
                                    Text(
                                        text = if (leftForToday < 0) "৳${String.format("%.0f", Math.abs(leftForToday))}" else "৳${String.format("%.0f", leftForToday)}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (leftForToday < 0) Color(0xFFFF3B30) else Color(0xFF34C759)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Pacing & Tomorrow's Recalculation Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = primaryColor.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Outlined.TrendingUp, contentDescription = null, tint = primaryColor, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text("Tomorrow's Projected Budget", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = textColor)
                                Text(
                                    text = if (daysRemaining > 1) "If you spend no more today, tomorrow's daily safe budget will adjust to ৳${String.format("%.0f", nextDayBudget)} / day based on $daysRemaining days left."
                                    else "Today is the last day of the month. Spend carefully!",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4-Quadrant Monthly Breakdown
                    Text("MONTHLY POOL OVERVIEW", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isDark) Color(0xFF2C2C34) else Color(0xFFF2F2F7),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Total Balance", fontSize = 11.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("৳${String.format("%,.0f", currentBalance)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isDark) Color(0xFF2C2C34) else Color(0xFFF2F2F7),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Spendable Pool", fontSize = 11.sp, color = if (savingsGoal > 0) Color(0xFFFF9500) else Color.Gray)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("৳${String.format("%,.0f", spendableBalance)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (savingsGoal > 0) Color(0xFFFF9500) else textColor)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isDark) Color(0xFF2C2C34) else Color(0xFFF2F2F7),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Total Month Spent", fontSize = 11.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("৳${String.format("%,.0f", totalSpentThisMonth)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isDark) Color(0xFF2C2C34) else Color(0xFFF2F2F7),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Days Left in Month", fontSize = 11.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("$daysRemaining Days", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    Button(
                        onClick = { showSafeDailySpendDetailSheet = false },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Understood 👍", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

// --- ✨ EXPERT LEVEL MAGNETIC BUDGET ROW ---
@Composable
fun PremiumSlimBudgetRow(
    title: String, color: Color, icon: ImageVector, spent: Double, limit: Double, input: String,
    maxAllocatable: Double, totalBudgetBase: Double, monthProgress: Float,
    daysRemaining: Int = 1,
    todaySpent: Double = 0.0,
    isUnlocked: Boolean, isDark: Boolean, cardColor: Color, textColor: Color,
    onCardClick: () -> Unit, onRemoveClick: (() -> Unit)? = null, onInputChange: (String) -> Unit
) {
    val view = LocalView.current

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardColor,
        shadowElevation = if(isDark) 0.dp else 2.dp,
        border = if(isDark) BorderStroke(1.dp, Color.White.copy(0.05f)) else null,
        modifier = Modifier.fillMaxWidth().clickable { onCardClick() }
    ) {
        AnimatedContent(targetState = isUnlocked, label = "edit_mode_anim") { editMode ->
            if (editMode) {
                val currentVal = input.toDoubleOrNull() ?: 0.0
                val percentage = if (totalBudgetBase > 0) ((currentVal / totalBudgetBase) * 100).roundToInt() else 0

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (onRemoveClick != null) {
                                IconButton(onClick = onRemoveClick, modifier = Modifier.size(26.dp).padding(end = 2.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color(0xFFFF3B30).copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                                }
                            }
                            Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(color.copy(0.15f)), contentAlignment = Alignment.Center) {
                                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("৳", color = Color.Gray, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            BasicTextField(
                                value = input, onValueChange = onInputChange, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.End, fontSize = 16.sp, fontWeight = FontWeight.Black, color = color),
                                modifier = Modifier.width(75.dp).background(if(isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                                decorationBox = { inner -> if (input.isEmpty()) Text("0", color = Color.Gray, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) else inner() }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val maxSliderValue = maxOf(1f, maxAllocatable.toFloat())
                    val stepSize = if (maxSliderValue >= 1000f) 100f else 50f

                    Slider(
                        value = currentVal.toFloat().coerceIn(0f, maxSliderValue),
                        onValueChange = { newVal ->
                            val snappedValue = (Math.round(newVal / stepSize) * stepSize).toInt()
                            if (snappedValue != currentVal.toInt()) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onInputChange(snappedValue.toString())
                            }
                        },
                        valueRange = 0f..maxSliderValue,
                        colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = color.copy(0.2f)),
                        modifier = Modifier.fillMaxWidth().height(24.dp)
                    )

                    Spacer(modifier = Modifier.height(2.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("$percentage% of total", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                        Text(if(maxAllocatable <= 0) "Maxed out!" else "Max: ৳${maxAllocatable.toInt()}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if(maxAllocatable <= 0) Color.Red else Color.Gray)
                    }
                }
            } else {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    val remaining = limit - spent
                    val spentRatio = if (limit > 0) (spent / limit).toFloat().coerceIn(0f, 1f) else 0f
                    val isCatOverpacing = limit > 0 && spentRatio > monthProgress
                    val dailyCatSafe = if (daysRemaining > 0) maxOf(0.0, remaining / daysRemaining) else 0.0

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(color.copy(0.15f)), contentAlignment = Alignment.Center) {
                                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor)

                            if (isCatOverpacing && remaining > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("🔥", fontSize = 14.sp)
                            }
                        }

                        if (limit > 0) {
                            val badgeColor = if (remaining < 0) Color(0xFFFF3B30) else Color(0xFF34C759)
                            val badgeText = if (remaining < 0) "Overspent" else "Left: ৳${String.format("%.0f", remaining)}"

                            Surface(color = badgeColor.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                                Text(badgeText, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { spentRatio },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                        color = if (spentRatio >= 1f) Color(0xFFFF3B30) else color,
                        trackColor = Color.Gray.copy(alpha = 0.15f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Spent: ৳${String.format("%.0f", spent)}", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
                            if (limit > 0) {
                                Text(" • ", fontSize = 12.sp, color = Color.Gray.copy(alpha = 0.5f))
                                Text("Safe: ৳${String.format("%.0f", dailyCatSafe)}/d", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = if (dailyCatSafe <= 0) Color(0xFFFF3B30) else Color(0xFF34C759))
                            }
                        }
                        Text(if (limit > 0) "Limit: ৳${String.format("%.0f", limit)}" else "No Limit", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }

                    if (limit > 0 && (todaySpent > 0 || dailyCatSafe > 0)) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Today's Spent: ৳${String.format("%.0f", todaySpent)}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (todaySpent > dailyCatSafe && dailyCatSafe > 0) Color(0xFFFF3B30) else Color.Gray
                            )
                            val catLeftToday = dailyCatSafe - todaySpent
                            Text(
                                text = if (catLeftToday >= 0) "Today Left: ৳${String.format("%.0f", catLeftToday)}" else "Over Today: ৳${String.format("%.0f", Math.abs(catLeftToday))}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (catLeftToday < 0) Color(0xFFFF3B30) else Color(0xFF34C759)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BudgetEmptyAnimationCard(
    spendableBalance: Double,
    isMessCentric: Boolean,
    isFoodCentric: Boolean,
    cardColor: Color,
    textColor: Color,
    primaryColor: Color,
    isDark: Boolean,
    onAddClick: () -> Unit,
    onQuickAddCategory: (String) -> Unit
) {
    val view = LocalView.current
    val infiniteTransition = rememberInfiniteTransition(label = "budget_empty_anim")

    // Pulse & floating animations
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val particlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_phase"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_angle"
    )

    val coinDropY by infiniteTransition.animateFloat(
        initialValue = -22f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "coin_drop"
    )
    val coinAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "coin_alpha"
    )

    val tips = remember {
        listOf(
            "✉️ The Envelope Method: Divide money into category envelopes to never run out before month-end!",
            "💡 Safe Daily Spend automatically recalculates based on what you spend!",
            "🎯 Set a monthly savings goal to protect your funds from impulsive expenses.",
            "📊 Track limits in real-time with smart pacing color alerts.",
            "🏠 Mess expenses are neatly isolated to keep your personal wallet organized."
        )
    }
    var currentTipIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(4000)
            currentTipIndex = (currentTipIndex + 1) % tips.size
        }
    }

    val suggestedPresets = remember(isMessCentric, isFoodCentric) {
        if (isMessCentric) {
            listOf("🍔 Snacks", "🚗 Transport", "🛍️ Shopping", "💡 Bills", "☕ Tea & Coffee")
        } else if (isFoodCentric) {
            listOf("🍲 Food", "🚗 Transport", "🛍️ Shopping", "💡 Bills", "🍿 Entertainment")
        } else {
            listOf("🍳 Breakfast", "🍱 Lunch", "🍽️ Dinner", "🚗 Transport", "🛍️ Shopping")
        }
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = cardColor,
        border = BorderStroke(1.dp, if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f)),
        shadowElevation = if (isDark) 0.dp else 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Background Canvas with floating glowing particle orbs
            Canvas(modifier = Modifier.matchParentSize().clip(RoundedCornerShape(22.dp))) {
                val w = size.width
                val h = size.height
                if (w > 0 && h > 0) {
                    val p1X = w * 0.2f + (kotlin.math.sin(particlePhase * 2 * Math.PI).toFloat() * 30f)
                    val p1Y = h * 0.3f + (kotlin.math.cos(particlePhase * 2 * Math.PI).toFloat() * 20f)
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.08f * glowAlpha),
                        radius = 80f,
                        center = Offset(p1X, p1Y)
                    )

                    val p2X = w * 0.8f + (kotlin.math.cos(particlePhase * 2 * Math.PI).toFloat() * 35f)
                    val p2Y = h * 0.7f + (kotlin.math.sin(particlePhase * 2 * Math.PI).toFloat() * 25f)
                    drawCircle(
                        color = Color(0xFFFF9500).copy(alpha = 0.07f * glowAlpha),
                        radius = 90f,
                        center = Offset(p2X, p2Y)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(22.dp)
            ) {
                // Real-life Envelope & Vault Animation Centerpiece with Solar System Orbit
                val orbitRadius = 54.0
                val angleRad = (rotationAngle * Math.PI / 180.0)

                val xFood = (kotlin.math.cos(angleRad) * orbitRadius).toFloat()
                val yFood = (kotlin.math.sin(angleRad) * orbitRadius).toFloat()

                val xTransport = (kotlin.math.cos(angleRad + Math.PI / 2.0) * orbitRadius).toFloat()
                val yTransport = (kotlin.math.sin(angleRad + Math.PI / 2.0) * orbitRadius).toFloat()

                val xShopping = (kotlin.math.cos(angleRad + Math.PI) * orbitRadius).toFloat()
                val yShopping = (kotlin.math.sin(angleRad + Math.PI) * orbitRadius).toFloat()

                val xBills = (kotlin.math.cos(angleRad + 3.0 * Math.PI / 2.0) * orbitRadius).toFloat()
                val yBills = (kotlin.math.sin(angleRad + 3.0 * Math.PI / 2.0) * orbitRadius).toFloat()

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(150.dp).padding(vertical = 4.dp)
                ) {
                    // Orbital track ring
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(CircleShape)
                            .border(1.dp, primaryColor.copy(alpha = 0.2f), CircleShape)
                    )

                    // Outer continuous rotating & pulsating glow aura
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .scale(pulseScale)
                            .rotate(rotationAngle)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        primaryColor.copy(alpha = glowAlpha * 0.35f),
                                        Color(0xFFFF9500).copy(alpha = glowAlpha * 0.25f),
                                        primaryColor.copy(alpha = glowAlpha * 0.35f)
                                    )
                                )
                            )
                    )

                    // Inner 3D Money Vault
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        if (isDark) Color(0xFF2C2C35) else Color(0xFFF3F4F8),
                                        if (isDark) Color(0xFF1E1E24) else Color.White
                                    )
                                )
                            )
                            .border(1.5.dp, primaryColor.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Text(
                            text = "🏦",
                            fontSize = 28.sp,
                            modifier = Modifier.scale(pulseScale)
                        )
                    }

                    // Dropping coin animation into vault
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .offset(y = (coinDropY - 2).dp)
                            .size(20.dp)
                            .clip(CircleShape)
                    ) {
                        Text(
                            text = "🪙",
                            fontSize = 13.sp
                        )
                    }

                    // Orbiting Category Planets (Solar System)
                    // 1. Food 🍔
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .offset(x = xFood.dp, y = yFood.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF2C2C35) else Color.White)
                            .border(1.2.dp, Color(0xFFFF9500).copy(alpha = 0.7f), CircleShape)
                    ) {
                        Text("🍔", fontSize = 14.sp)
                    }

                    // 2. Transport 🚗
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .offset(x = xTransport.dp, y = yTransport.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF2C2C35) else Color.White)
                            .border(1.2.dp, Color(0xFF007AFF).copy(alpha = 0.7f), CircleShape)
                    ) {
                        Text("🚗", fontSize = 14.sp)
                    }

                    // 3. Shopping 🛍️
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .offset(x = xShopping.dp, y = yShopping.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF2C2C35) else Color.White)
                            .border(1.2.dp, Color(0xFFFF2D55).copy(alpha = 0.7f), CircleShape)
                    ) {
                        Text("🛍️", fontSize = 14.sp)
                    }

                    // 4. Bills 💡
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .offset(x = xBills.dp, y = yBills.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF2C2C35) else Color.White)
                            .border(1.2.dp, Color(0xFF34C759).copy(alpha = 0.7f), CircleShape)
                    ) {
                        Text("💡", fontSize = 14.sp)
                    }
                }

                Text(
                    text = "Envelope Budgeting ✉️",
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (spendableBalance > 0) {
                        "You have ৳${String.format("%,.0f", spendableBalance)} spendable balance. Divide your funds into category envelopes below (Food, Transport, Shopping) to stay on track all month!"
                    } else {
                        "Divide your monthly income into designated category envelopes (Food, Transport, Bills) so you never run out of money before month-end!"
                    },
                    color = if (isDark) Color(0xFFB0B0B8) else Color(0xFF6B7280),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Presets
                Text(
                    text = "QUICK ADD PRESETS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(suggestedPresets) { preset ->
                        Surface(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onQuickAddCategory(preset)
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isDark) Color(0xFF282830) else Color(0xFFF0F1F5),
                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.15f))
                        ) {
                            Text(
                                text = "+ $preset",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Main CTA Button
                Button(
                    onClick = onAddClick,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Curate Budget Categories", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Animated Tip Carousel at bottom
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color(0xFF1E1E24) else Color(0xFFF7F8FA),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = currentTipIndex,
                        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) },
                        label = "tip_anim"
                    ) { idx ->
                        Text(
                            text = tips[idx],
                            fontSize = 11.sp,
                            color = if (isDark) Color(0xFF9E9EA8) else Color(0xFF6B7280),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

fun isTodayLocal(timestamp: Long): Boolean {
    val cal1 = Calendar.getInstance()
    val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}