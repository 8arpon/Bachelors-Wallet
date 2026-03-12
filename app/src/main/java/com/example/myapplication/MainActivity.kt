package com.example.myapplication

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class ExpenseCategory(val label: String, val emoji: String, val color: Color) {
    BREAKFAST("Breakfast", "☕", Color(0xFFFFA500)),
    LUNCH("Lunch", "🍱", Color(0xFFFF4500)),
    DINNER("Dinner", "🌙", Color(0xFF4B0082)),
    OTHERS("Others", "🛍️", Color(0xFF800080))
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        ThemeState.isDark.value = prefs.getBoolean("dark_mode", false)

        setContent {
            MaterialTheme(
                colorScheme = if (ThemeState.isDark.value) darkColorScheme() else lightColorScheme()
            ) {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .navigationBarsPadding()
    ) {

        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            composable("home") { BudgetPlannerScreen(navController) }
            composable("budget") { BudgetScreen() }
            composable("debt") { DebtManagerScreen() }
            composable("history") { ExpenseHistoryScreen() }
            composable("settings") { SettingsScreen(navController) }
            composable("notifications") { NotificationScreen(navController) }
        }

        FloatingNavBar(
            navController = navController,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
        )
    }
}

@Composable
fun FloatingNavBar(navController: NavController, modifier: Modifier = Modifier) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Surface(
        modifier = modifier
            .fillMaxWidth(0.95f)
            .shadow(12.dp, CircleShape),
        shape = CircleShape,
        color = Color.White
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(icon = Icons.Default.Home, title = "Home", isSelected = currentRoute == "home") {
                if (currentRoute != "home") navController.navigate("home") { launchSingleTop = true }
            }
            NavItem(icon = Icons.Default.ShoppingCart, title = "Budget", isSelected = currentRoute == "budget") {
                if (currentRoute != "budget") navController.navigate("budget") { launchSingleTop = true }
            }
            NavItem(icon = Icons.Default.Person, title = "Debt", isSelected = currentRoute == "debt") {
                if (currentRoute != "debt") navController.navigate("debt") { launchSingleTop = true }
            }
            NavItem(icon = Icons.Default.List, title = "History", isSelected = currentRoute == "history") {
                if (currentRoute != "history") navController.navigate("history") { launchSingleTop = true }
            }
        }
    }
}

@Composable
fun RowScope.NavItem(icon: ImageVector, title: String, isSelected: Boolean, onClick: () -> Unit) {
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF0088CC) else Color.Gray,
        animationSpec = tween(300), label = ""
    )

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF0088CC).copy(alpha = 0.1f) else Color.Transparent,
        animationSpec = tween(300), label = ""
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )

        Text(
            text = title,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BudgetPlannerScreen(navController: NavController) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val expenseFocusRequester = remember { FocusRequester() }
    val incomeFocusRequester = remember { FocusRequester() }

    var allExpenses by remember { mutableStateOf(DataManager.getExpenses(context)) }
    var allDebts by remember { mutableStateOf(DataManager.getDebts(context)) }

    LaunchedEffect(Unit) {
        allExpenses = DataManager.getExpenses(context)
        allDebts = DataManager.getDebts(context)
    }

    // HIGHLIGHT: Accurate Monthly Calculation
    val totalReceived = ExpenseCalculator.getThisMonthIncome(allExpenses)
    val totalSpent = ExpenseCalculator.getThisMonthExpense(allExpenses)
    val currentBalance = ExpenseCalculator.getThisMonthBalance(allExpenses, allDebts)

    var incomeInput by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    var expenseInput by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }

    var selectedCategory by remember { mutableStateOf(ExpenseCategory.BREAKFAST) }
    var successMessage by remember { mutableStateOf("") }

    var isIncomeFocused by remember { mutableStateOf(false) }
    var isExpenseFocused by remember { mutableStateOf(false) }

    var incomeHint by remember { mutableStateOf("Amount") }
    var expenseHint by remember { mutableStateOf("Enter Cost") }

    var incomeDate by remember { mutableStateOf(Date()) }
    var expenseDate by remember { mutableStateOf(Date()) }

    var currentSavedIncome by remember { mutableStateOf(0.0) }
    var currentSavedExpense by remember { mutableStateOf(0.0) }

    val displayFormatter = SimpleDateFormat("d MMM", Locale.getDefault())
    val dateCheckFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun updateHints() {
        val currentExpenses = DataManager.getExpenses(context)

        val incDayStr = dateCheckFormatter.format(incomeDate)
        val incDay = currentExpenses.find { dateCheckFormatter.format(it.date) == incDayStr }
        currentSavedIncome = incDay?.income ?: 0.0
        incomeHint = if (currentSavedIncome > 0) "Saved: ৳${currentSavedIncome.toInt()}" else "Amount"

        val expDayStr = dateCheckFormatter.format(expenseDate)
        val expDay = currentExpenses.find { dateCheckFormatter.format(it.date) == expDayStr }
        currentSavedExpense = when (selectedCategory) {
            ExpenseCategory.BREAKFAST -> expDay?.breakfast ?: 0.0
            ExpenseCategory.LUNCH -> expDay?.lunch ?: 0.0
            ExpenseCategory.DINNER -> expDay?.dinner ?: 0.0
            ExpenseCategory.OTHERS -> expDay?.others ?: 0.0
        }
        expenseHint = if (currentSavedExpense > 0) "Saved: ৳${currentSavedExpense.toInt()}" else "Enter ${selectedCategory.label} Cost"
    }

    LaunchedEffect(incomeDate, expenseDate, selectedCategory, allExpenses) { updateHints() }
    LaunchedEffect(successMessage) { if (successMessage.isNotEmpty()) { kotlinx.coroutines.delay(2500); successMessage = "" } }

    fun handleSave(isIncome: Boolean, amount: Double, op: SaveOp) {
        if (isIncome) {
            DataManager.addIncome(context, incomeDate, amount, op)
            successMessage = "Income Saved!"
            incomeInput = androidx.compose.ui.text.input.TextFieldValue("")
        } else {
            DataManager.addExpense(context, expenseDate, selectedCategory, amount, op)
            successMessage = "Expense Saved!"
            expenseInput = androidx.compose.ui.text.input.TextFieldValue("")
        }
        allExpenses = DataManager.getExpenses(context)
        focusManager.clearFocus()
    }

    fun parseTerm(expr: String): Double {
        val multParts = expr.split('*')
        var prod = 1.0
        for (i in multParts.indices) {
            val divParts = multParts[i].split('/')
            var divRes = divParts[0].trim().toDoubleOrNull() ?: 0.0
            for (j in 1 until divParts.size) {
                val divisor = divParts[j].trim().toDoubleOrNull() ?: 1.0
                divRes /= if (divisor == 0.0) 1.0 else divisor
            }
            if (i == 0) prod = divRes else prod *= divRes
        }
        return prod
    }

    fun evaluateExpression(expr: String): Double {
        var str = expr.replace(" ", "")
        if (str.isEmpty()) return 0.0
        if (str.startsWith("-")) str = "0$str"
        return try {
            val addParts = str.split('+')
            var sum = 0.0
            for (addPart in addParts) {
                if (addPart.isEmpty()) continue
                val subParts = addPart.split('-')
                var subSum = parseTerm(subParts[0])
                for (i in 1 until subParts.size) {
                    subSum -= parseTerm(subParts[i])
                }
                sum += subSum
            }
            sum
        } catch (e: Exception) {
            str.toDoubleOrNull() ?: 0.0
        }
    }

    val bgColor = if (ThemeState.isDark.value) Color(0xFF121212) else Color(0xFFF8F9FA)
    val cardColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White
    val textColor = if (ThemeState.isDark.value) Color.White else Color.Black
    val iconBgColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("My Wallet", fontSize = 32.sp, fontWeight = FontWeight.Black, color = textColor)
                Text(SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(Date()), color = Color.Gray)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(iconBgColor).clickable {
                    navController.navigate("notifications")
                }, contentAlignment = Alignment.Center) {
                    Text("🔔", fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(iconBgColor).clickable {
                    navController.navigate("settings")
                }, contentAlignment = Alignment.Center) {
                    Text("≡", fontSize = 32.sp, fontWeight = FontWeight.Light, color = textColor)
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF4400E0), Color(0xFF8E2DE2))))
                .padding(25.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Current Balance", color = Color.White.copy(alpha = 0.8f))
                Text("৳${String.format("%.2f", currentBalance)}", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp), color = Color.White.copy(alpha = 0.2f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatItem("Received", "৳${totalReceived.toInt()}", Color.Green)
                    StatItem("Spent", "৳${totalSpent.toInt()}", Color.Red)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // INCOME CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Income Entry", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor)
                    DatePill(dateText = displayFormatter.format(incomeDate)) {
                        showDatePicker(context) { selected -> incomeDate = selected }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = incomeInput,
                        onValueChange = { if (it.text.all { c -> c.isDigit() || c == '.' || c == '+' || c == '-' || c == '*' || c == '/' }) incomeInput = it },
                        placeholder = { Text(incomeHint) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(incomeFocusRequester)
                            .onFocusChanged { focusState -> isIncomeFocused = focusState.isFocused },
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor)
                    )

                    AnimatedVisibility(
                        visible = isIncomeFocused || incomeInput.text.isNotEmpty(),
                        enter = fadeIn() + expandHorizontally(animationSpec = tween(300)),
                        exit = fadeOut() + shrinkHorizontally(animationSpec = tween(300))
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AnimatedVisibility(visible = currentSavedIncome > 0 && incomeInput.text.isEmpty()) {
                                Box(modifier = Modifier.height(56.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF007AFF)).clickable {
                                    val str = if (currentSavedIncome % 1.0 == 0.0) currentSavedIncome.toInt().toString() else currentSavedIncome.toString()
                                    incomeInput = androidx.compose.ui.text.input.TextFieldValue(text = str, selection = androidx.compose.ui.text.TextRange(str.length))
                                    incomeFocusRequester.requestFocus()
                                }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                                    Text("Edit", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            AnimatedVisibility(visible = incomeInput.text.isNotEmpty()) {
                                Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF4CAF50)).clickable {
                                    val amt = evaluateExpression(incomeInput.text)
                                    if (amt >= 0) handleSave(true, amt, SaveOp.OVERWRITE)
                                }, contentAlignment = Alignment.Center) {
                                    Text("✓", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // EXPENSE CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Expense Entry", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textColor)
                    DatePill(dateText = displayFormatter.format(expenseDate)) {
                        showDatePicker(context) { selected -> expenseDate = selected }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    ExpenseCategory.entries.forEach { category ->
                        CategoryCircle(category = category, isSelected = selectedCategory == category, onClick = {
                            selectedCategory = category
                            expenseInput = androidx.compose.ui.text.input.TextFieldValue("")
                        })
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = expenseInput,
                        onValueChange = { if (it.text.all { c -> c.isDigit() || c == '.' || c == '+' || c == '-' || c == '*' || c == '/' }) expenseInput = it },
                        placeholder = { Text(expenseHint) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(expenseFocusRequester)
                            .onFocusChanged { focusState -> isExpenseFocused = focusState.isFocused },
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor)
                    )

                    AnimatedVisibility(
                        visible = isExpenseFocused || expenseInput.text.isNotEmpty(),
                        enter = fadeIn() + expandHorizontally(animationSpec = tween(300)),
                        exit = fadeOut() + shrinkHorizontally(animationSpec = tween(300))
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            AnimatedVisibility(visible = currentSavedExpense > 0 && expenseInput.text.isEmpty()) {
                                Box(modifier = Modifier.height(56.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF007AFF)).clickable {
                                    val str = if (currentSavedExpense % 1.0 == 0.0) currentSavedExpense.toInt().toString() else currentSavedExpense.toString()
                                    expenseInput = androidx.compose.ui.text.input.TextFieldValue(text = str, selection = androidx.compose.ui.text.TextRange(str.length))
                                    expenseFocusRequester.requestFocus()
                                }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                                    Text("Edit", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            AnimatedVisibility(visible = expenseInput.text.isNotEmpty()) {
                                Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF4CAF50)).clickable {
                                    val amt = evaluateExpression(expenseInput.text)
                                    if (amt >= 0) handleSave(false, amt, SaveOp.OVERWRITE)
                                }, contentAlignment = Alignment.Center) {
                                    Text("✓", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = successMessage.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
                Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(8.dp)) {
                    Text(successMessage, color = Color(0xFF2E7D32), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }
}

@Composable
fun DatePill(dateText: String, onClick: () -> Unit) {
    Surface(color = Color(0xFFE3F2FD), shape = RoundedCornerShape(8.dp), modifier = Modifier.clickable { onClick() }) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📅 ", fontSize = 12.sp)
            Text(text = dateText, color = Color(0xFF1976D2), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

fun showDatePicker(context: android.content.Context, onDateSelected: (Date) -> Unit) {
    val calendar = Calendar.getInstance()
    DatePickerDialog(context, { _, year, month, day ->
        val selected = Calendar.getInstance().apply { set(year, month, day) }
        onDateSelected(selected.time)
    }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
}

@Composable
fun CategoryCircle(category: ExpenseCategory, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(if (isSelected) category.color else Color(0xFFF1F3F5), label = "")
    Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(bgColor).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(category.emoji, fontSize = 24.sp)
    }
}

@Composable
fun StatItem(label: String, amount: String, color: Color) {
    Column {
        Text(label.uppercase(), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(amount, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}