package com.example.myapplication

import android.content.Context
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.core.content.ContextCompat
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

    // --- 🧠 LOGIC & CALCULATIONS (Monthly Only) ---
    val calendar = Calendar.getInstance()
    val daysRemaining = remember {
        val totalDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        maxOf(1, totalDays - currentDay + 1)
    }

    // 1. Current Remaining Wallet Balance
    val currentBalance = ExpenseCalculator.getThisMonthBalance(context, expenses, debts)

    // 2. Category Spent Calculations (Only This Month)
    val thisMonthExpenses = expenses.filter { ExpenseCalculator.isThisMonth(it.date) }
    val totalFoodSpent = thisMonthExpenses.sumOf { it.breakfast + it.lunch + it.dinner }
    val totalOthersSpent = thisMonthExpenses.sumOf { it.others }
    val totalSpentThisMonth = totalFoodSpent + totalOthersSpent

    // HIGHLIGHT: PERFECT BUDGET MATH
    // Total Budget Base = What you have now + What you already spent.
    // This gives us the true 100% starting money for the month, which stays CONSTANT!
    val totalMonthlyBudgetBase = currentBalance + totalSpentThisMonth

    val todayFoodSpent = expenses.filter { isTodayLocal(it.date.time) }.sumOf { it.breakfast + it.lunch + it.dinner }
    val todayOthersSpent = expenses.filter { isTodayLocal(it.date.time) }.sumOf { it.others }
    val totalTodaySpent = todayFoodSpent + todayOthersSpent

    // 3. Inputs for Plan based on TOTAL BUDGET BASE (Not shrinking balance)
    var foodInput by remember { mutableStateOf(String.format(Locale.US, "%.0f", totalMonthlyBudgetBase * 0.80)) }
    var othersInput by remember { mutableStateOf(String.format(Locale.US, "%.0f", totalMonthlyBudgetBase * 0.20)) }

    LaunchedEffect(isAutoBudget, totalMonthlyBudgetBase) {
        if (isAutoBudget) {
            foodInput = String.format(Locale.US, "%.0f", totalMonthlyBudgetBase * 0.80)
            othersInput = String.format(Locale.US, "%.0f", totalMonthlyBudgetBase * 0.20)
        }
    }

    val totalFoodLimit = if (isAutoBudget) totalMonthlyBudgetBase * 0.80 else foodInput.toDoubleOrNull() ?: 0.0
    val totalOthersLimit = if (isAutoBudget) totalMonthlyBudgetBase * 0.20 else othersInput.toDoubleOrNull() ?: 0.0

    val remainingFoodBudget = totalFoodLimit - totalFoodSpent
    val remainingOthersBudget = totalOthersLimit - totalOthersSpent

    // HIGHLIGHT: Daily Targets strictly use the REMAINING WALLET BALANCE
    val safeDailySpend = (currentBalance + totalTodaySpent) / daysRemaining
    val nextDayBudget = if (daysRemaining > 1) currentBalance / (daysRemaining - 1) else 0.0

    // Sub-daily breakdown based on remaining category limits
    val safeDailyFood = (remainingFoodBudget + todayFoodSpent) / daysRemaining
    val safeDailyOthers = (remainingOthersBudget + todayOthersSpent) / daysRemaining

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

                    // HIGHLIGHT: Text changed from "Total Income" to "Total Monthly Balance"
                    Text("Total Monthly Budget Base: ৳${String.format("%.0f", totalMonthlyBudgetBase)}", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                    HorizontalDivider()

                    BudgetRow(
                        title = "Food (80%)", color = Color.Magenta, spent = totalFoodSpent, limit = totalFoodLimit, remaining = remainingFoodBudget,
                        input = foodInput, isUnlocked = isUnlocked, focusRequester = foodFocusReq
                    ) { foodInput = it }

                    BudgetRow(
                        title = "Others (20%)", color = Color.Blue, spent = totalOthersSpent, limit = totalOthersLimit, remaining = remainingOthersBudget,
                        input = othersInput, isUnlocked = isUnlocked, focusRequester = othersFocusReq
                    ) { othersInput = it }

                    if (isUnlocked || !isAutoBudget) {
                        Row(modifier = Modifier.fillMaxWidth().clickable {
                            isAutoBudget = true
                            foodInput = String.format(Locale.US, "%.0f", totalMonthlyBudgetBase * 0.80)
                            othersInput = String.format(Locale.US, "%.0f", totalMonthlyBudgetBase * 0.20)
                            focusManager.clearFocus()
                        }.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🔄 Sync with Total Income (80/20 Rule)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007AFF))
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
            Spacer(modifier = Modifier.height(100.dp))
        }
    }

    // --- PASSWORD & BIOMETRIC AUTHENTICATION DIALOG ---
    if (showPasswordAlert) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val savedPass = prefs.getString("app_password", "") ?: ""

        fun authenticateAndUnlock() {
            val activity = context.getActivity() ?: return Toast.makeText(context, "Error: Activity not found", Toast.LENGTH_SHORT).show()
            val executor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = BiometricPrompt(activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        isUnlocked = true
                        showPasswordAlert = false
                        wrongPassword = false
                        passwordInput = ""
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        Toast.makeText(context, "Auth Error: $errString", Toast.LENGTH_SHORT).show()
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Verify Identity")
                .setSubtitle("Confirm it's you to edit the budget plan")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
            biometricPrompt.authenticate(promptInfo)
        }

        AlertDialog(
            containerColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White,
            onDismissRequest = { showPasswordAlert = false; wrongPassword = false; passwordInput = "" },
            title = {
                Text(
                    text = if (savedPass.isEmpty()) "Set App Password" else "Security Check",
                    fontWeight = FontWeight.Bold,
                    color = if (ThemeState.isDark.value) Color.White else Color.Black
                )
            },
            text = {
                Column {
                    Text(
                        text = if (savedPass.isEmpty()) "No password is set. Set a new password or use fingerprint to unlock."
                        else if (wrongPassword) "Wrong Password!"
                        else "Enter password or use fingerprint.",
                        color = if (wrongPassword) Color.Red else Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(15.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            label = { Text(if (savedPass.isEmpty()) "New Password" else "Password") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = if (ThemeState.isDark.value) Color.White else Color.Black,
                                unfocusedTextColor = if (ThemeState.isDark.value) Color.White else Color.Black
                            )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF007AFF).copy(alpha = 0.1f))
                                .clickable { authenticateAndUnlock() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Biometric Unlock", tint = Color(0xFF007AFF))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (savedPass.isEmpty()) {
                            if (passwordInput.length >= 4) {
                                prefs.edit().putString("app_password", passwordInput).apply()
                                isUnlocked = true
                                showPasswordAlert = false
                                passwordInput = ""
                                Toast.makeText(context, "Password Saved!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Password must be at least 4 chars", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            if (passwordInput == savedPass) {
                                isUnlocked = true
                                showPasswordAlert = false
                                wrongPassword = false
                                passwordInput = ""
                            } else {
                                wrongPassword = true
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (savedPass.isEmpty()) "Save & Unlock" else "Unlock", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordAlert = false; wrongPassword = false; passwordInput = "" }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun BudgetRow(title: String, color: Color, spent: Double, limit: Double, remaining: Double, input: String, isUnlocked: Boolean, focusRequester: FocusRequester, onInputChange: (String) -> Unit) {
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
                BasicTextFieldCustom(input = input, isUnlocked = isUnlocked, focusRequester = focusRequester, onInputChange = onInputChange)
            }
        }
    }
}

@Composable
fun BasicTextFieldCustom(input: String, isUnlocked: Boolean, focusRequester: FocusRequester, onInputChange: (String) -> Unit) {
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
            // HIGHLIGHT: Removed the onFocusChanged logic that was triggering the floating bar
            .padding(horizontal = 8.dp, vertical = 6.dp),
        enabled = isUnlocked,
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
            textAlign = TextAlign.End,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        ),
        // Allows simple math characters from keyboard
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        cursorBrush = SolidColor(Color(0xFF007AFF))
    )
}

fun isTodayLocal(timestamp: Long): Boolean {
    val cal1 = Calendar.getInstance()
    val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}