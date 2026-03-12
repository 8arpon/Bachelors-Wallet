package com.example.myapplication

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*

@Composable
fun BudgetScreen() {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var expenses by remember { mutableStateOf(DataManager.getExpenses(context)) }
    var debts by remember { mutableStateOf(DataManager.getDebts(context)) }

    LaunchedEffect(Unit) {
        expenses = DataManager.getExpenses(context)
        debts = DataManager.getDebts(context)
    }

    var isUnlocked by remember { mutableStateOf(false) }
    var showPasswordAlert by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var wrongPassword by remember { mutableStateOf(false) }

    var isAutoBudget by remember { mutableStateOf(true) }

    val foodFocusReq = remember { FocusRequester() }
    val othersFocusReq = remember { FocusRequester() }
    var focusedField by remember { mutableStateOf<String?>(null) }

    // --- 🧠 LOGIC & CALCULATIONS (Monthly Only) ---
    val calendar = Calendar.getInstance()
    val daysRemaining = remember {
        val totalDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        maxOf(1, totalDays - currentDay + 1)
    }

    // 1. Total Income & Balance (Only This Month)
    val totalIncomeReceived = ExpenseCalculator.getThisMonthIncome(expenses)
    val currentBalance = ExpenseCalculator.getThisMonthBalance(expenses, debts)

    // 2. Category Spent Calculations (Only This Month)
    val thisMonthExpenses = expenses.filter { ExpenseCalculator.isThisMonth(it.date) }
    val totalFoodSpent = thisMonthExpenses.sumOf { it.breakfast + it.lunch + it.dinner }
    val totalOthersSpent = thisMonthExpenses.sumOf { it.others }

    val todayFoodSpent = expenses.filter { isTodayLocal(it.date.time) }.sumOf { it.breakfast + it.lunch + it.dinner }
    val todayOthersSpent = expenses.filter { isTodayLocal(it.date.time) }.sumOf { it.others }
    val totalTodaySpent = todayFoodSpent + todayOthersSpent

    // 3. Inputs for Plan
    var foodInput by remember { mutableStateOf(String.format(Locale.US, "%.0f", totalIncomeReceived * 0.80)) }
    var othersInput by remember { mutableStateOf(String.format(Locale.US, "%.0f", totalIncomeReceived * 0.20)) }

    LaunchedEffect(isAutoBudget, totalIncomeReceived) {
        if (isAutoBudget) {
            foodInput = String.format(Locale.US, "%.0f", totalIncomeReceived * 0.80)
            othersInput = String.format(Locale.US, "%.0f", totalIncomeReceived * 0.20)
        }
    }

    val totalFoodLimit = if (isAutoBudget) totalIncomeReceived * 0.80 else foodInput.toDoubleOrNull() ?: 0.0
    val totalOthersLimit = if (isAutoBudget) totalIncomeReceived * 0.20 else othersInput.toDoubleOrNull() ?: 0.0

    val remainingFoodBudget = totalFoodLimit - totalFoodSpent
    val remainingOthersBudget = totalOthersLimit - totalOthersSpent

    val safeDailyFood = (remainingFoodBudget + todayFoodSpent) / daysRemaining
    val safeDailyOthers = (remainingOthersBudget + todayOthersSpent) / daysRemaining
    val safeDailySpend = safeDailyFood + safeDailyOthers

    val nextDayBudget = if (daysRemaining > 1) (remainingFoodBudget + remainingOthersBudget) / (daysRemaining - 1) else 0.0

    fun evaluateSimpleMath(expr: String): String {
        return try {
            val parts = expr.split("+")
            if (parts.size > 1) {
                val sum = parts.sumOf { it.trim().toDoubleOrNull() ?: 0.0 }
                String.format(Locale.US, "%.0f", sum)
            } else if (expr.contains("-")) {
                val subParts = expr.split("-")
                val total = (subParts[0].trim().toDoubleOrNull() ?: 0.0) - (subParts.drop(1).sumOf { it.trim().toDoubleOrNull() ?: 0.0 })
                String.format(Locale.US, "%.0f", total)
            } else {
                expr
            }
        } catch (e: Exception) {
            expr
        }
    }

    fun saveAndLock() {
        foodInput = evaluateSimpleMath(foodInput)
        othersInput = evaluateSimpleMath(othersInput)
        isAutoBudget = false
        isUnlocked = false
        focusManager.clearFocus()
    }

    Box(modifier = Modifier.fillMaxSize().background(if (ThemeState.isDark.value) Color(0xFF121212) else Color(0xFFF2F2F7))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Text("Budget Planner", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (ThemeState.isDark.value) Color.White else Color.Black)
            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(25.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF009688), Color(0xFF2196F3))))
                    .padding(25.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Safe Daily Spend", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("৳${String.format("%.0f", safeDailySpend)}", fontSize = 45.sp, fontWeight = FontWeight.Bold, color = if (safeDailySpend < 0) Color.Red else Color.White)
                        if (totalTodaySpent > 0) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("(-${String.format("%.0f", totalTodaySpent)})", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (totalTodaySpent > safeDailySpend) Color.Red else Color.Green)
                        }
                    }

                    if (daysRemaining > 1) {
                        Text("Tomorrow: ৳${String.format("%.0f", nextDayBudget)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f))
                    } else {
                        Text("Last Day of Month", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 15.dp), color = Color.White.copy(alpha = 0.5f))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text("Wallet Balance", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                            Text("৳${String.format("%.0f", currentBalance)}", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Days Left", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                            Text("$daysRemaining", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Category Breakdown",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (ThemeState.isDark.value) Color.White else Color.Black,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        if (isUnlocked) {
                            Button(
                                onClick = { saveAndLock() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Save & Lock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        } else {
                            Button(
                                onClick = { showPasswordAlert = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Edit Plan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text("Total Income: ৳${String.format("%.0f", totalIncomeReceived)}", fontSize = 14.sp, color = Color.Gray)
                    HorizontalDivider()

                    BudgetRow(
                        title = "Food (80%)", color = Color.Magenta, spent = totalFoodSpent, limit = totalFoodLimit, remaining = remainingFoodBudget,
                        input = foodInput, isUnlocked = isUnlocked, focusRequester = foodFocusReq, onFocus = { focusedField = "food" }
                    ) { foodInput = it }

                    BudgetRow(
                        title = "Others (20%)", color = Color.Blue, spent = totalOthersSpent, limit = totalOthersLimit, remaining = remainingOthersBudget,
                        input = othersInput, isUnlocked = isUnlocked, focusRequester = othersFocusReq, onFocus = { focusedField = "others" }
                    ) { othersInput = it }

                    if (isUnlocked || !isAutoBudget) {
                        Row(modifier = Modifier.fillMaxWidth().clickable {
                            isAutoBudget = true
                            foodInput = String.format(Locale.US, "%.0f", totalIncomeReceived * 0.80)
                            othersInput = String.format(Locale.US, "%.0f", totalIncomeReceived * 0.20)
                            focusManager.clearFocus()
                        }.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🔄 Force Sync with Income (80/20 Rule)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007AFF))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Daily Target (Based on Remaining)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (ThemeState.isDark.value) Color.White else Color.Black)
                    Spacer(modifier = Modifier.height(15.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("Food/Day", fontSize = 12.sp, color = Color.Gray)
                            Text("৳${String.format("%.0f", safeDailyFood)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Magenta)
                        }
                        Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color.Gray.copy(alpha = 0.3f)))
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("Others/Day", fontSize = 12.sp, color = Color.Gray)
                            Text("৳${String.format("%.0f", safeDailyOthers)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Blue)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(150.dp))
        }

        AnimatedVisibility(
            visible = focusedField != null,
            modifier = Modifier.align(Alignment.BottomCenter).imePadding()
        ) {
            Surface(color = Color(0xFFE5E5EA), modifier = Modifier.fillMaxWidth().padding(bottom = 65.dp)) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { if(focusedField=="food") foodInput+="+" else othersInput+="+" }, colors = ButtonDefaults.buttonColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp), modifier = Modifier.size(50.dp, 40.dp), contentPadding = PaddingValues(0.dp)) { Text("+", color = Color.Black, fontSize = 20.sp) }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(onClick = { if(focusedField=="food") foodInput+="-" else othersInput+="-" }, colors = ButtonDefaults.buttonColors(containerColor = Color.White), shape = RoundedCornerShape(8.dp), modifier = Modifier.size(50.dp, 40.dp), contentPadding = PaddingValues(0.dp)) { Text("-", color = Color.Black, fontSize = 20.sp) }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = {
                            if (focusedField == "food") {
                                foodInput = evaluateSimpleMath(foodInput)
                                othersFocusReq.requestFocus()
                            } else {
                                othersInput = evaluateSimpleMath(othersInput)
                                saveAndLock()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)), shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (focusedField == "food") "Next" else "Done", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showPasswordAlert) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val savedPass = prefs.getString("app_password", "Arpon") ?: "Arpon"

        AlertDialog(
            onDismissRequest = { showPasswordAlert = false; wrongPassword = false; passwordInput = "" },
            title = { Text("Security Check") },
            text = {
                Column {
                    Text(if (wrongPassword) "Wrong Password!" else "Enter password to edit.", color = if (wrongPassword) Color.Red else Color.Gray)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (passwordInput == savedPass) {
                        isUnlocked = true
                        showPasswordAlert = false
                        wrongPassword = false
                        passwordInput = ""
                    } else {
                        wrongPassword = true
                    }
                }) { Text("Unlock") }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordAlert = false; wrongPassword = false; passwordInput = "" }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun BudgetRow(title: String, color: Color, spent: Double, limit: Double, remaining: Double, input: String, isUnlocked: Boolean, focusRequester: FocusRequester, onFocus: () -> Unit, onInputChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(if (ThemeState.isDark.value) Color(0xFF2C2C2E) else Color(0xFFF2F2F7), RoundedCornerShape(12.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontWeight = FontWeight.Bold, color = if (ThemeState.isDark.value) Color.White else Color.Black)
            Text("Available: ৳${String.format("%.0f", remaining)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (remaining < 0) Color.Red else color)
        }

        val spentRatio = (spent / if (limit > 0) limit else 1.0).toFloat().coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { spentRatio },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = if (spentRatio > 0.9f) Color.Red else color,
            trackColor = Color.Gray.copy(alpha = 0.3f),
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Spent: ৳${String.format("%.0f", spent)}", fontSize = 12.sp, color = Color.Gray)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Plan:", fontSize = 12.sp, color = Color.Gray)
                BasicTextFieldCustom(input = input, isUnlocked = isUnlocked, focusRequester = focusRequester, onFocus = onFocus, onInputChange = onInputChange)
            }
        }
    }
}

@Composable
fun BasicTextFieldCustom(input: String, isUnlocked: Boolean, focusRequester: FocusRequester, onFocus: () -> Unit, onInputChange: (String) -> Unit) {
    val borderColor = if (isUnlocked) Color(0xFF34C759) else Color.Transparent
    val bgColor = if (isUnlocked) Color.White else Color.Transparent
    val textColor = if (!isUnlocked && ThemeState.isDark.value) Color.LightGray else if (isUnlocked) Color.Black else Color.Gray

    BasicTextField(
        value = input,
        onValueChange = onInputChange,
        modifier = Modifier
            .width(80.dp)
            .background(bgColor, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        enabled = isUnlocked,
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
            textAlign = TextAlign.End,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        cursorBrush = SolidColor(Color(0xFF007AFF))
    )
}

fun isTodayLocal(timestamp: Long): Boolean {
    val cal1 = Calendar.getInstance()
    val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}