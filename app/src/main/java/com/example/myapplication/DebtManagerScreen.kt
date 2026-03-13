package com.example.myapplication


import androidx.annotation.Keep // <-- এই লাইনটি ইম্পোর্ট করো
import android.app.DatePickerDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class DebtType { I_OWE, THEY_OWE }
@Keep
data class PaymentRecord(val amount: Double, val date: Date = Date())
@Keep // <-- HIGHLIGHT
data class DebtItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    var amount: Double,
    var paidAmount: Double = 0.0,
    var isPaid: Boolean = false,
    val type: DebtType,
    val date: Date,
    var paymentHistory: MutableList<PaymentRecord> = mutableListOf()
) {
    val remainingAmount: Double get() = amount - paidAmount
}

fun isThisMonth(date: Date): Boolean {
    val currentCal = Calendar.getInstance()
    val targetCal = Calendar.getInstance().apply { time = date }
    return currentCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
            currentCal.get(Calendar.MONTH) == targetCal.get(Calendar.MONTH)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DebtManagerScreen() {
    val context = LocalContext.current
    var debts by remember { mutableStateOf(DataManager.getDebts(context)) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var selectedDebtForPayment by remember { mutableStateOf<DebtItem?>(null) }

    LaunchedEffect(Unit) { debts = DataManager.getDebts(context) }

    val (thisMonthDebts, pastMonthDebts, historyDebts) = remember(debts, showHistory) {
        if (showHistory) {
            val sixtyDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -60) }.time
            val history = debts.filter { it.isPaid }.filter { debt ->
                val lastActivityDate = debt.paymentHistory.maxByOrNull { it.date }?.date ?: debt.date
                lastActivityDate.after(sixtyDaysAgo)
            }.sortedByDescending { debt -> debt.paymentHistory.maxByOrNull { it.date }?.date ?: debt.date }
            Triple(emptyList<DebtItem>(), emptyList<DebtItem>(), history)
        } else {
            val active = debts.filter { !it.isPaid }.sortedByDescending { it.date }
            val thisMonth = active.filter { isThisMonth(it.date) }
            val pastMonths = active.filter { !isThisMonth(it.date) }
            Triple(thisMonth, pastMonths, emptyList<DebtItem>())
        }
    }

    val isListEmpty = if (showHistory) historyDebts.isEmpty() else (thisMonthDebts.isEmpty() && pastMonthDebts.isEmpty())
    val bgColor = if (ThemeState.isDark.value) Color(0xFF121212) else Color(0xFFF8F9FA)
    val cardColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White
    val textColor = if (ThemeState.isDark.value) Color.White else Color.Black

    Scaffold(containerColor = bgColor) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // --- TOP HEADER ---
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = if (showHistory) "Debt History" else "Active Debts", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007AFF))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.clip(CircleShape).background(if (showHistory) Color(0xFF007AFF).copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f)).clickable { showHistory = !showHistory }.padding(8.dp)) {
                        Icon(imageVector = if (showHistory) Icons.Default.Close else Icons.Default.List, contentDescription = "Toggle History", tint = if (showHistory) Color(0xFF007AFF) else textColor, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.clip(CircleShape).background(Color(0xFF007AFF).copy(alpha = 0.1f)).clickable { showAddSheet = true }.padding(8.dp)) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Debt", tint = Color(0xFF007AFF), modifier = Modifier.size(24.dp))
                    }
                }
            }

            AnimatedContent(targetState = isListEmpty, label = "DebtViewTransition", modifier = Modifier.weight(1f)) { empty ->
                if (empty) {
                    EmptyStateView(textColor = textColor, isHistory = showHistory, onAddClick = { showAddSheet = true })
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                        if (showHistory) {
                            item {
                                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                val today = Date()
                                val sixtyDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -60) }.time
                                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.Center) {
                                    Surface(color = Color(0xFF007AFF).copy(alpha = 0.1f), shape = RoundedCornerShape(16.dp)) {
                                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(imageVector = Icons.Default.DateRange, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = "${dateFormat.format(sixtyDaysAgo)}  —  ${dateFormat.format(today)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007AFF))
                                        }
                                    }
                                }
                            }
                            items(historyDebts, key = { it.id }) { debt ->
                                DebtRowCard(debt = debt, cardColor = cardColor, textColor = textColor, isHistory = true, modifier = Modifier.animateItem(), onCardClick = { selectedDebtForPayment = debt }, onMarkPaid = { })
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        } else {
                            if (thisMonthDebts.isNotEmpty()) {
                                items(thisMonthDebts, key = { it.id }) { debt ->
                                    DebtRowCard(debt = debt, cardColor = cardColor, textColor = textColor, isHistory = false, modifier = Modifier.animateItem(), onCardClick = { selectedDebtForPayment = debt }, onMarkPaid = { processMarkPaid(context, debt) { debts = DataManager.getDebts(context) } })
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                            if (pastMonthDebts.isNotEmpty()) {
                                item {
                                    Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.Gray.copy(alpha = 0.2f)))
                                        Text(text = "PAST MONTHS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(horizontal = 12.dp))
                                        Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.Gray.copy(alpha = 0.2f)))
                                    }
                                }
                                items(pastMonthDebts, key = { it.id }) { debt ->
                                    DebtRowCard(debt = debt, cardColor = cardColor, textColor = textColor, isHistory = false, modifier = Modifier.animateItem(), onCardClick = { selectedDebtForPayment = debt }, onMarkPaid = { processMarkPaid(context, debt) { debts = DataManager.getDebts(context) } })
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddSheet) {
            Dialog(onDismissRequest = { showAddSheet = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).imePadding()) {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(100.dp))
                        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = cardColor), elevation = CardDefaults.cardElevation(defaultElevation = 10.dp), modifier = Modifier.fillMaxWidth()) {
                            AddDebtDialogContent(
                                textColor = textColor,
                                onDismiss = { showAddSheet = false },
                                onSave = { newDebt ->
                                    DataManager.addDebt(context, newDebt)
                                    debts = DataManager.getDebts(context)
                                    showAddSheet = false
                                    showHistory = false
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
        }

        if (selectedDebtForPayment != null) {
            Dialog(onDismissRequest = { selectedDebtForPayment = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).imePadding()) {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(100.dp))
                        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = cardColor), elevation = CardDefaults.cardElevation(defaultElevation = 10.dp), modifier = Modifier.fillMaxWidth()) {
                            PaymentSheetContent(
                                debt = selectedDebtForPayment!!, textColor = textColor, onDismiss = { selectedDebtForPayment = null },
                                onSavePayment = { amount ->
                                    val current = selectedDebtForPayment!!
                                    val safeAmount = if (current.paidAmount + amount > current.amount) current.remainingAmount else amount
                                    val newPaidAmount = current.paidAmount + safeAmount
                                    val updatedHistory = current.paymentHistory.toMutableList().apply { add(PaymentRecord(amount = safeAmount, date = Date())) }
                                    val updatedDebt = current.copy(paidAmount = newPaidAmount, isPaid = newPaidAmount >= current.amount, paymentHistory = updatedHistory)
                                    DataManager.updateDebt(context, updatedDebt)
                                    debts = DataManager.getDebts(context)
                                    selectedDebtForPayment = updatedDebt
                                },
                                onEditPayment = { oldRecord, newAmount ->
                                    val current = selectedDebtForPayment!!
                                    val updatedHistory = current.paymentHistory.toMutableList()
                                    val index = updatedHistory.indexOf(oldRecord)
                                    if (index != -1) {
                                        val paidWithoutRecord = current.paidAmount - oldRecord.amount
                                        val maxAllowed = current.amount - paidWithoutRecord
                                        val safeNewAmount = if (newAmount > maxAllowed) maxAllowed else newAmount
                                        updatedHistory[index] = oldRecord.copy(amount = safeNewAmount)
                                        val finalPaidAmount = paidWithoutRecord + safeNewAmount
                                        val updatedDebt = current.copy(paidAmount = finalPaidAmount, isPaid = finalPaidAmount >= current.amount, paymentHistory = updatedHistory)
                                        DataManager.updateDebt(context, updatedDebt)
                                        debts = DataManager.getDebts(context)
                                        selectedDebtForPayment = updatedDebt
                                    }
                                },
                                onDeletePayment = { recordToRemove, revertBalance ->
                                    val current = selectedDebtForPayment!!

                                    // HIGHLIGHT: If not reverting, convert the payment's impact into manual Income/Expense
                                    if (!revertBalance) {
                                        val impact = if (current.type == DebtType.I_OWE) -recordToRemove.amount else recordToRemove.amount
                                        if (impact > 0) DataManager.addIncome(context, Date(), impact, SaveOp.ADD)
                                        else if (impact < 0) DataManager.addExpense(context, Date(), ExpenseCategory.OTHERS, -impact, SaveOp.ADD)
                                    }

                                    val updatedHistory = current.paymentHistory.toMutableList().apply { remove(recordToRemove) }
                                    val safePaidAmount = maxOf(0.0, current.paidAmount - recordToRemove.amount)
                                    val updatedDebt = current.copy(paidAmount = safePaidAmount, isPaid = safePaidAmount >= current.amount, paymentHistory = updatedHistory)
                                    DataManager.updateDebt(context, updatedDebt)
                                    debts = DataManager.getDebts(context)
                                    selectedDebtForPayment = updatedDebt
                                },
                                onEditMainDebt = { newName, newTotalAmount ->
                                    val current = selectedDebtForPayment!!
                                    val updatedDebt = current.copy(name = newName, amount = newTotalAmount, isPaid = current.paidAmount >= newTotalAmount)
                                    DataManager.updateDebt(context, updatedDebt)
                                    debts = DataManager.getDebts(context)
                                    selectedDebtForPayment = updatedDebt
                                },
                                onRestoreDebt = {
                                    val updatedDebt = selectedDebtForPayment!!.copy(isPaid = false, paidAmount = 0.0, paymentHistory = mutableListOf())
                                    DataManager.updateDebt(context, updatedDebt)
                                    debts = DataManager.getDebts(context)
                                    selectedDebtForPayment = null
                                    showHistory = false
                                },
                                onDeleteDebt = { revertBalance ->
                                    val current = selectedDebtForPayment!!

                                    // HIGHLIGHT: If not reverting, convert the debt's whole impact into manual Income/Expense
                                    if (!revertBalance) {
                                        val impact = if (current.type == DebtType.I_OWE) current.amount - current.paidAmount else current.paidAmount - current.amount
                                        if (impact > 0) DataManager.addIncome(context, Date(), impact, SaveOp.ADD)
                                        else if (impact < 0) DataManager.addExpense(context, Date(), ExpenseCategory.OTHERS, -impact, SaveOp.ADD)
                                    }

                                    DataManager.deleteDebt(context, current.id)
                                    debts = DataManager.getDebts(context)
                                    selectedDebtForPayment = null
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
        }
    }
}

fun processMarkPaid(context: android.content.Context, debt: DebtItem, onComplete: () -> Unit) {
    val remaining = debt.remainingAmount
    val updatedHistory = debt.paymentHistory.toMutableList()
    if (remaining > 0) updatedHistory.add(PaymentRecord(amount = remaining, date = Date()))
    DataManager.updateDebt(context, debt.copy(paidAmount = debt.amount, isPaid = true, paymentHistory = updatedHistory))
    onComplete()
}

@Composable
fun EmptyStateView(textColor: Color, isHistory: Boolean, onAddClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(200.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF007AFF).opacity(0.1f), Color.Magenta.opacity(0.1f)))))
            Icon(imageVector = if (isHistory) Icons.Default.List else Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(90.dp), tint = Color(0xFF007AFF))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(if (isHistory) "No History Yet" else "Debt Free!", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = textColor)
        Text(text = if (isHistory) "You haven't completed any debt transactions yet." else "You have no pending payables or receivables.\nEnjoy your financial freedom!", fontSize = 16.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp, vertical = 10.dp))
        Spacer(modifier = Modifier.height(20.dp))
        if (!isHistory) {
            Button(onClick = onAddClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)), shape = CircleShape, contentPadding = PaddingValues(horizontal = 30.dp, vertical = 14.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add First Entry", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun DebtRowCard(debt: DebtItem, cardColor: Color, textColor: Color, isHistory: Boolean, modifier: Modifier = Modifier, onCardClick: () -> Unit, onMarkPaid: () -> Unit) {
    val isReceived = debt.type == DebtType.THEY_OWE
    val progress = if (debt.amount > 0) (debt.paidAmount / debt.amount).toFloat() else 0f
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cardColor), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), modifier = modifier.fillMaxWidth().clickable { onCardClick() }) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(if (isReceived) Color(0xFF34C759).opacity(0.1f) else Color(0xFFFF3B30).opacity(0.1f)), contentAlignment = Alignment.Center) {
                Text(if (isReceived) "↗" else "↙", color = if (isReceived) Color(0xFF34C759) else Color(0xFFFF3B30), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(15.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(debt.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                Spacer(modifier = Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.width(60.dp).height(6.dp).clip(RoundedCornerShape(3.dp)), color = if (isReceived) Color(0xFF34C759) else Color(0xFF007AFF), trackColor = Color.LightGray.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${(progress * 100).toInt()}%", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("৳${String.format("%.0f", if (isHistory) debt.amount else debt.remainingAmount)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isReceived) Color(0xFF34C759) else Color(0xFFFF3B30))
                Spacer(modifier = Modifier.height(5.dp))
                if (isHistory) {
                    Box(modifier = Modifier.clip(CircleShape).background(Color.Gray.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 6.dp)) { Text("Completed", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold) }
                } else {
                    Box(modifier = Modifier.clip(CircleShape).background(Color(0xFF007AFF).copy(alpha = 0.1f)).clickable { onMarkPaid() }.padding(horizontal = 12.dp, vertical = 6.dp)) { Text("Mark Paid", fontSize = 12.sp, color = Color(0xFF007AFF), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaymentSheetContent(
    debt: DebtItem, textColor: Color, onDismiss: () -> Unit, onSavePayment: (Double) -> Unit,
    onEditPayment: (PaymentRecord, Double) -> Unit, onDeletePayment: (PaymentRecord, Boolean) -> Unit,
    onEditMainDebt: (String, Double) -> Unit, onRestoreDebt: () -> Unit, onDeleteDebt: (Boolean) -> Unit
) {
    var paymentInput by remember { mutableStateOf("") }
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    // HIGHLIGHT: Checkbox States added back
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var revertMainBalance by remember { mutableStateOf(true) }

    var paymentToDelete by remember { mutableStateOf<PaymentRecord?>(null) }
    var revertPaymentBalance by remember { mutableStateOf(true) }

    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showEditMainDialog by remember { mutableStateOf(false) }

    var editMainName by remember { mutableStateOf(debt.name) }
    var editMainAmount by remember { mutableStateOf(String.format(Locale.US, "%.0f", debt.amount)) }
    var recordToEdit by remember { mutableStateOf<PaymentRecord?>(null) }
    var editAmountInput by remember { mutableStateOf("") }
    val isReceived = debt.type == DebtType.THEY_OWE

    // HIGHLIGHT: Dialog for Deleting Entire Debt
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Transaction", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Are you sure you want to delete this completely?", color = textColor)
                    Spacer(modifier = Modifier.height(15.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = revertMainBalance,
                            onCheckedChange = { revertMainBalance = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFF3B30))
                        )
                        Text("Revert from Main Balance", fontSize = 14.sp, color = textColor)
                    }
                }
            },
            containerColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White,
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteDebt(revertMainBalance)
                }) { Text("Delete", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = Color.Gray) } }
        )
    }

    // HIGHLIGHT: Dialog for Deleting Single Payment
    if (paymentToDelete != null) {
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            title = { Text("Delete Payment", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Are you sure you want to delete this payment record?", color = textColor)
                    Spacer(modifier = Modifier.height(15.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = revertPaymentBalance,
                            onCheckedChange = { revertPaymentBalance = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFF3B30))
                        )
                        Text("Revert from Main Balance", fontSize = 14.sp, color = textColor)
                    }
                }
            },
            containerColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White,
            confirmButton = {
                TextButton(onClick = {
                    onDeletePayment(paymentToDelete!!, revertPaymentBalance)
                    paymentToDelete = null
                }) { Text("Delete", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { paymentToDelete = null }) { Text("Cancel", color = Color.Gray) } }
        )
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false }, title = { Text("Restart Transaction") },
            text = { Text("Reset this debt and start from 0? All payment history will be cleared.", color = textColor) },
            containerColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White,
            confirmButton = { TextButton(onClick = { showRestoreConfirm = false; onRestoreDebt() }) { Text("Restart", color = Color(0xFF007AFF), fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text("Cancel", color = Color.Gray) } }
        )
    }

    if (showEditMainDialog) {
        AlertDialog(
            onDismissRequest = { showEditMainDialog = false }, title = { Text("Edit Info", fontWeight = FontWeight.Bold, color = textColor) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = editMainName, onValueChange = { editMainName = it }, label = { Text("Person Name") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor))
                    OutlinedTextField(value = editMainAmount, onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) editMainAmount = it }, label = { Text("Total Amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor))
                }
            },
            containerColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White,
            confirmButton = { TextButton(onClick = { val newAmt = editMainAmount.toDoubleOrNull() ?: debt.amount; val newName = editMainName.takeIf { it.isNotBlank() } ?: debt.name; onEditMainDebt(newName, newAmt); showEditMainDialog = false }) { Text("Save", color = Color(0xFF007AFF), fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showEditMainDialog = false }) { Text("Cancel", color = Color.Gray) } }
        )
    }

    if (recordToEdit != null) {
        AlertDialog(
            onDismissRequest = { recordToEdit = null }, title = { Text("Edit Payment", fontWeight = FontWeight.Bold, color = textColor) },
            text = { OutlinedTextField(value = editAmountInput, onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) editAmountInput = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(12.dp), leadingIcon = { Text("৳", fontWeight = FontWeight.Bold, color = textColor) }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor)) },
            containerColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White,
            confirmButton = { TextButton(onClick = { val newAmt = editAmountInput.toDoubleOrNull() ?: 0.0; if (newAmt > 0) onEditPayment(recordToEdit!!, newAmt); recordToEdit = null }) { Text("Save", color = Color(0xFF007AFF), fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { recordToEdit = null }) { Text("Cancel", color = Color.Gray) } }
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Debt Details", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = textColor, modifier = Modifier.align(Alignment.Center))
            Box(modifier = Modifier.align(Alignment.CenterEnd).clip(CircleShape).background(Color.Gray.copy(alpha = 0.1f)).clickable { onDismiss() }.padding(6.dp)) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(20.dp)) }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(debt.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor)
                Spacer(modifier = Modifier.height(2.dp))
                Text("Created: ${SimpleDateFormat("dd MMM yy", Locale.getDefault()).format(debt.date)}", fontSize = 12.sp, color = Color.Gray)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (debt.isPaid) Box(modifier = Modifier.clip(CircleShape).background(Color(0xFF34C759).copy(alpha = 0.1f)).clickable { showRestoreConfirm = true }.padding(8.dp)) { Icon(Icons.Default.Refresh, contentDescription = "Restart", tint = Color(0xFF34C759), modifier = Modifier.size(20.dp)) }
                Box(modifier = Modifier.clip(CircleShape).background(Color(0xFF007AFF).copy(alpha = 0.1f)).clickable { showEditMainDialog = true }.padding(8.dp)) { Icon(Icons.Default.Edit, contentDescription = "Edit Info", tint = Color(0xFF007AFF), modifier = Modifier.size(20.dp)) }
                Box(modifier = Modifier.clip(CircleShape).background(Color(0xFFFF3B30).copy(alpha = 0.1f)).clickable {
                    showDeleteConfirm = true
                    revertMainBalance = true // Reset checkbox state when opened
                }.padding(8.dp)) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF3B30), modifier = Modifier.size(20.dp)) }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("Total Amount", fontSize = 12.sp, color = Color.Gray); Text("৳${String.format("%.0f", debt.amount)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor) }
            Column(horizontalAlignment = Alignment.End) { Text("Remaining", fontSize = 12.sp, color = Color.Gray); Text("৳${String.format("%.0f", debt.remainingAmount)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isReceived) Color(0xFF34C759) else Color(0xFFFF3B30)) }
        }
        Spacer(modifier = Modifier.height(20.dp))
        if (debt.isPaid) {
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF34C759).copy(alpha = 0.1f)).padding(16.dp), contentAlignment = Alignment.Center) { Text("🎉 Fully Settled", color = Color(0xFF34C759), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        } else {
            Column(modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = paymentInput, onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) paymentInput = it }, placeholder = { Text("Enter Partial Payment", fontSize = 14.sp, maxLines = 1) }, leadingIcon = { Text("৳", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp), color = textColor) }, modifier = Modifier.weight(1f).focusRequester(focusRequester).onFocusChanged { isFocused = it.isFocused; if (it.isFocused) { coroutineScope.launch { delay(300); bringIntoViewRequester.bringIntoView() } } }, shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor))
                    AnimatedVisibility(visible = isFocused || paymentInput.isNotEmpty(), enter = fadeIn(animationSpec = tween(300)) + expandHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)), exit = fadeOut(animationSpec = tween(300)) + shrinkHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing))) {
                        Box(modifier = Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF4CAF50)).clickable(enabled = paymentInput.isNotEmpty()) { val amount = paymentInput.toDoubleOrNull() ?: 0.0; if (amount > 0) { onSavePayment(amount); paymentInput = "" } }, contentAlignment = Alignment.Center) { Text("✓", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        if (debt.paymentHistory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(25.dp))
            Text("Payment History", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
            Spacer(modifier = Modifier.height(10.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                debt.paymentHistory.asReversed().forEach { record ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(record.date), color = Color.Gray, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("৳${String.format("%.0f", record.amount)}", color = textColor, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(15.dp))
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF007AFF).copy(alpha = 0.8f), modifier = Modifier.size(20.dp).clickable { recordToEdit = record; editAmountInput = String.format(Locale.US, "%.0f", record.amount) })
                            Spacer(modifier = Modifier.width(10.dp))
                            // HIGHLIGHT: Show Payment Delete Dialog on icon click
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF3B30).copy(alpha = 0.8f), modifier = Modifier.size(20.dp).clickable {
                                paymentToDelete = record
                                revertPaymentBalance = true // Reset checkbox state when opened
                            })
                        }
                    }
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                }
            }
        }
    }
}

@Composable
fun AddDebtDialogContent(textColor: Color, onDismiss: () -> Unit, onSave: (DebtItem) -> Unit) {
    val context = LocalContext.current
    var newName by remember { mutableStateOf("") }
    var newAmount by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf(DebtType.I_OWE) }
    var selectedDate by remember { mutableStateOf(Date()) }
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val isValid = newName.isNotEmpty() && newAmount.isNotEmpty()
    val calendar = Calendar.getInstance().apply { time = selectedDate }
    val datePickerDialog = DatePickerDialog(context, { _, year, month, dayOfMonth -> val newCal = Calendar.getInstance(); newCal.set(year, month, dayOfMonth); selectedDate = newCal.time }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(text = "New Transaction", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = textColor, modifier = Modifier.align(Alignment.Center))
            Box(modifier = Modifier.align(Alignment.CenterEnd).clip(CircleShape).background(Color.Gray.copy(alpha = 0.1f)).clickable { onDismiss() }.padding(6.dp)) { Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(20.dp)) }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DebtTypeButton(title = "I Borrowed", subtitle = "Payable", color = Color(0xFFFF3B30), isSelected = newType == DebtType.I_OWE, textColor = textColor, modifier = Modifier.weight(1f)) { newType = DebtType.I_OWE }
            DebtTypeButton(title = "I Lent", subtitle = "Receivable", color = Color(0xFF34C759), isSelected = newType == DebtType.THEY_OWE, textColor = textColor, modifier = Modifier.weight(1f)) { newType = DebtType.THEY_OWE }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = newName, onValueChange = { newName = it }, placeholder = { Text("Person Name") }, leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor))
            Box(modifier = Modifier.height(56.dp).clip(RoundedCornerShape(16.dp)).background(Color.Gray.copy(alpha = 0.1f)).clickable { datePickerDialog.show() }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.DateRange, contentDescription = "Date", tint = Color.Gray, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.height(2.dp)); Text(text = SimpleDateFormat("dd MMM", Locale.getDefault()).format(selectedDate), fontSize = 10.sp, color = textColor, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = newAmount, onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) newAmount = it }, placeholder = { Text("Amount") }, leadingIcon = { Text("৳", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp), color = textColor) }, modifier = Modifier.weight(1f).focusRequester(focusRequester).onFocusChanged { isFocused = it.isFocused }, shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor))
            AnimatedVisibility(visible = isFocused || newAmount.isNotEmpty(), enter = fadeIn(animationSpec = tween(300)) + expandHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)), exit = fadeOut(animationSpec = tween(300)) + shrinkHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing))) {
                Box(modifier = Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(if (isValid) Color(0xFF4CAF50) else Color.Gray.copy(alpha = 0.5f)).clickable(enabled = isValid) { val amount = newAmount.toDoubleOrNull() ?: 0.0; if (amount > 0) onSave(DebtItem(name = newName, amount = amount, type = newType, date = selectedDate)) }, contentAlignment = Alignment.Center) { Text("✓", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun DebtTypeButton(title: String, subtitle: String, color: Color, isSelected: Boolean, textColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val unselectedBg = if (ThemeState.isDark.value) Color(0xFF2C2C2E) else Color.LightGray.copy(alpha = 0.2f)
    Box(modifier = modifier.height(90.dp).clip(RoundedCornerShape(16.dp)).background(if (isSelected) color.copy(alpha = 0.1f) else unselectedBg).border(2.dp, if (isSelected) color else Color.Transparent, RoundedCornerShape(16.dp)).clickable { onClick() }.padding(12.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(if (isSelected) color else Color.Gray.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            Spacer(modifier = Modifier.weight(1f))
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (isSelected) textColor else Color.Gray)
            Text(subtitle, fontSize = 12.sp, color = if (isSelected) color else Color.Gray, fontWeight = FontWeight.Medium)
        }
    }
}

fun Color.opacity(alpha: Float): Color = this.copy(alpha = alpha)