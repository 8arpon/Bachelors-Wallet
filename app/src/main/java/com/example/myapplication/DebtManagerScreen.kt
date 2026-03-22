package com.example.myapplication

import androidx.annotation.Keep
import android.app.DatePickerDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextOverflow
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

//@Keep
//data class DebtItem(
//    val id: String = UUID.randomUUID().toString(),
//    val name: String,
//    var amount: Double,
//    var paidAmount: Double = 0.0,
//    var isPaid: Boolean = false,
//    val type: DebtType,
//    val date: Date,
//    var deadline: Date? = null,
//    var isArchived: Boolean = false,
//    // HIGHLIGHT: Hidden fields to keep amounts safe when disconnected from Main Balance
//    var archivedAmount: Double = 0.0,
//    var archivedPaidAmount: Double = 0.0,
//    var paymentHistory: MutableList<PaymentRecord> = mutableListOf()
//) {
//    // Smartly handle UI rendering even if underlying amount is 0 (cut off from main balance)
//    val displayAmount: Double get() = if (isArchived && amount == 0.0 && archivedAmount > 0.0) archivedAmount else amount
//    val displayPaidAmount: Double get() = if (isArchived && amount == 0.0 && archivedAmount > 0.0) archivedPaidAmount else paidAmount
//    val remainingAmount: Double get() = displayAmount - displayPaidAmount
//}

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
    var showAddSheet by remember { mutableStateOf(false) }

    var showHistory by remember { mutableStateOf(false) }
    var showArchive by remember { mutableStateOf(false) }

    var selectedDebtForPayment by remember { mutableStateOf<DebtItem?>(null) }

    var debtToArchiveBySwipe by remember { mutableStateOf<DebtItem?>(null) }
    var revertSwipeDeleteBalance by remember { mutableStateOf(true) }

    val debts by DataManager.getDebtsFlow(context).collectAsState(initial = emptyList())

    val (thisMonthDebts, pastMonthDebts, historyDebts, archivedDebts) = remember(debts, showHistory, showArchive) {
        val archived = debts.filter { it.isArchived }.sortedByDescending { it.date }
        val history = debts.filter { it.isPaid && !it.isArchived }.sortedByDescending { it.date }
        val active = debts.filter { !it.isPaid && !it.isArchived }.sortedByDescending { it.date }

        val thisMonth = active.filter { isThisMonth(it.date) }
        val pastMonths = active.filter { !isThisMonth(it.date) }

        listOf(thisMonth, pastMonths, history, archived)
    }

    val isListEmpty = when {
        showArchive -> archivedDebts.isEmpty()
        showHistory -> historyDebts.isEmpty()
        else -> thisMonthDebts.isEmpty() && pastMonthDebts.isEmpty()
    }

    val bgColor = if (ThemeState.isDark.value) Color(0xFF121212) else Color(0xFFF8F9FA)
    val cardColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White
    val textColor = if (ThemeState.isDark.value) Color.White else Color.Black

    Scaffold(containerColor = bgColor) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // --- TOP HEADER ---
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                // HIGHLIGHT: Ellipsis সরানো হয়েছে এবং Font Size 20.sp করা হয়েছে
                Text(
                    text = when {
                        showArchive -> "Archived Debts"
                        showHistory -> "Debt History"
                        else -> "Active Debts"
                    },
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007AFF),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.clip(CircleShape).background(if (showArchive) Color(0xFF007AFF).copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f)).clickable {
                        showArchive = !showArchive; if (showArchive) showHistory = false
                    }.padding(8.dp)) {
                        Icon(imageVector = if (showArchive) Icons.Default.Close else Icons.Default.Inventory2, contentDescription = "Toggle Archive", tint = if (showArchive) Color(0xFF007AFF) else textColor, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.clip(CircleShape).background(if (showHistory) Color(0xFF007AFF).copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f)).clickable {
                        showHistory = !showHistory; if (showHistory) showArchive = false
                    }.padding(8.dp)) {
                        Icon(imageVector = if (showHistory) Icons.Default.Close else Icons.Default.History, contentDescription = "Toggle History", tint = if (showHistory) Color(0xFF007AFF) else textColor, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.clip(CircleShape).background(Color(0xFF007AFF).copy(alpha = 0.1f)).clickable { showAddSheet = true }.padding(8.dp)) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Debt", tint = Color(0xFF007AFF), modifier = Modifier.size(24.dp))
                    }
                }
            }

            AnimatedContent(targetState = isListEmpty, label = "DebtViewTransition", modifier = Modifier.weight(1f)) { empty ->
                if (empty) {
                    EmptyStateView(textColor = textColor, isHistory = showHistory, isArchive = showArchive, onAddClick = { showAddSheet = true })
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                        if (showArchive) {
                            items(archivedDebts, key = { it.id }) { debt ->
                                DebtRowCard(debt = debt, cardColor = cardColor, textColor = textColor, isHistory = false, isArchive = true, modifier = Modifier.animateItem(), onCardClick = { selectedDebtForPayment = debt }, onMarkPaid = { }, onSwipeAction = { debtToArchiveBySwipe = debt; revertSwipeDeleteBalance = true })
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        } else if (showHistory) {
                            items(historyDebts, key = { it.id }) { debt ->
                                DebtRowCard(debt = debt, cardColor = cardColor, textColor = textColor, isHistory = true, isArchive = false, modifier = Modifier.animateItem(), onCardClick = { selectedDebtForPayment = debt }, onMarkPaid = { }, onSwipeAction = { debtToArchiveBySwipe = debt; revertSwipeDeleteBalance = true })
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        } else {
                            if (thisMonthDebts.isNotEmpty()) {
                                items(thisMonthDebts, key = { it.id }) { debt ->
                                    DebtRowCard(debt = debt, cardColor = cardColor, textColor = textColor, isHistory = false, isArchive = false, modifier = Modifier.animateItem(), onCardClick = { selectedDebtForPayment = debt }, onMarkPaid = { processMarkPaid(context, debt) {  } }, onSwipeAction = { debtToArchiveBySwipe = debt; revertSwipeDeleteBalance = true })
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
                                    DebtRowCard(debt = debt, cardColor = cardColor, textColor = textColor, isHistory = false, isArchive = false, modifier = Modifier.animateItem(), onCardClick = { selectedDebtForPayment = debt }, onMarkPaid = { processMarkPaid(context, debt) {  } }, onSwipeAction = { debtToArchiveBySwipe = debt; revertSwipeDeleteBalance = true })
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- SWIPE ARCHIVE / DELETE CONFIRMATION DIALOG ---
        if (debtToArchiveBySwipe != null) {
            AlertDialog(
                onDismissRequest = { debtToArchiveBySwipe = null },
                title = { Text(if (showArchive) "Permanent Delete" else "Move to Archive", fontWeight = FontWeight.Bold, color = textColor) },
                text = {
                    Column {
                        Text(if (showArchive) "Are you sure you want to permanently delete this? You cannot recover it." else "Move this transaction to the archive? You can restore it later.", color = textColor)
                        if (!showArchive) {
                            Spacer(modifier = Modifier.height(15.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = revertSwipeDeleteBalance, onCheckedChange = { revertSwipeDeleteBalance = it }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFF9500)))
                                Text("Revert from Main Balance", fontSize = 14.sp, color = textColor)
                            }
                        }
                    }
                },
                containerColor = cardColor,
                confirmButton = {
                    TextButton(onClick = {
                        val current = debtToArchiveBySwipe!!
                        if (showArchive) {
                            // HIGHLIGHT: Smart logic to handle permanent delete
                            if (current.amount > 0.0) { // Keep Impact if revert was FALSE
                                val impact = if (current.type == DebtType.I_OWE) current.amount - current.paidAmount else current.paidAmount - current.amount
                                if (impact > 0) DataManager.addIncome(context, Date(), impact, SaveOp.ADD)
                                else if (impact < 0) DataManager.addExpense(context, Date(), ExpenseCategory.OTHERS, -impact, SaveOp.ADD)
                            }
                            DataManager.deleteDebt(context, current.id)
                        } else {
                            // HIGHLIGHT: SMART Connection Cut Logic
                            val updated = if (revertSwipeDeleteBalance) {
                                current.copy(
                                    isArchived = true, archivedAmount = current.amount, archivedPaidAmount = current.paidAmount,
                                    amount = 0.0, paidAmount = 0.0 // Instantly cuts connection from Main Balance
                                )
                            } else {
                                current.copy(isArchived = true)
                            }
                            DataManager.updateDebt(context, updated)
                        }
                        
                        debtToArchiveBySwipe = null
                    }) { Text(if (showArchive) "Delete" else "Archive", color = if (showArchive) Color(0xFFFF3B30) else Color(0xFFFF9500), fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { debtToArchiveBySwipe = null }) { Text("Cancel", color = Color.Gray) } }
            )
        }

        if (showAddSheet) {
            Dialog(onDismissRequest = { showAddSheet = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = cardColor), elevation = CardDefaults.cardElevation(defaultElevation = 10.dp), modifier = Modifier.fillMaxWidth()) {
                        AddDebtDialogContent(
                            textColor = textColor,
                            onDismiss = { showAddSheet = false },
                            onSave = { newDebt ->
                                DataManager.addDebt(context, newDebt)
                                
                                showAddSheet = false
                                showHistory = false
                                showArchive = false
                            }
                        )
                    }
                }
            }
        }

        if (selectedDebtForPayment != null) {
            Dialog(onDismissRequest = { selectedDebtForPayment = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = cardColor), elevation = CardDefaults.cardElevation(defaultElevation = 10.dp), modifier = Modifier.fillMaxWidth()) {
                        PaymentSheetContent(
                            debt = selectedDebtForPayment!!, textColor = textColor, isArchivedMode = showArchive, onDismiss = { selectedDebtForPayment = null },
                            onSavePayment = { amount ->
                                val current = selectedDebtForPayment!!
                                val safeAmount = if (current.paidAmount + amount > current.amount) current.remainingAmount else amount
                                val newPaidAmount = current.paidAmount + safeAmount
                                val updatedHistory = current.paymentHistory.toMutableList().apply { add(PaymentRecord(amount = safeAmount, date = Date())) }
                                val updatedDebt = current.copy(paidAmount = newPaidAmount, isPaid = newPaidAmount >= current.amount, paymentHistory = updatedHistory)
                                DataManager.updateDebt(context, updatedDebt); ; selectedDebtForPayment = updatedDebt
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
                                    DataManager.updateDebt(context, updatedDebt); ; selectedDebtForPayment = updatedDebt
                                }
                            },
                            onDeletePayment = { recordToRemove, revertBalance ->
                                val current = selectedDebtForPayment!!
                                if (!revertBalance) {
                                    val impact = if (current.type == DebtType.I_OWE) -recordToRemove.amount else recordToRemove.amount
                                    if (impact > 0) DataManager.addIncome(context, Date(), impact, SaveOp.ADD)
                                    else if (impact < 0) DataManager.addExpense(context, Date(), ExpenseCategory.OTHERS, -impact, SaveOp.ADD)
                                }
                                val updatedHistory = current.paymentHistory.toMutableList().apply { remove(recordToRemove) }
                                val safePaidAmount = maxOf(0.0, current.paidAmount - recordToRemove.amount)
                                val updatedDebt = current.copy(paidAmount = safePaidAmount, isPaid = safePaidAmount >= current.amount, paymentHistory = updatedHistory)
                                DataManager.updateDebt(context, updatedDebt); ; selectedDebtForPayment = updatedDebt
                            },
                            onEditMainDebt = { newName, newTotalAmount, newDeadline ->
                                val current = selectedDebtForPayment!!
                                val updatedDebt = current.copy(name = newName, amount = newTotalAmount, deadline = newDeadline, isPaid = current.paidAmount >= newTotalAmount)
                                DataManager.updateDebt(context, updatedDebt); ; selectedDebtForPayment = updatedDebt
                            },
                            onRestoreDebtFromComplete = {
                                val updatedDebt = selectedDebtForPayment!!.copy(isPaid = false, paidAmount = 0.0, paymentHistory = mutableListOf())
                                DataManager.updateDebt(context, updatedDebt); ; selectedDebtForPayment = null; showHistory = false; showArchive = false
                            },
                            onArchiveDebt = { revertBalance ->
                                val current = selectedDebtForPayment!!
                                // HIGHLIGHT: Smart Connection Cut Logic
                                val updated = if (revertBalance) {
                                    current.copy(
                                        isArchived = true, archivedAmount = current.amount, archivedPaidAmount = current.paidAmount,
                                        amount = 0.0, paidAmount = 0.0 // Instantly cuts connection from Main Balance
                                    )
                                } else { current.copy(isArchived = true) }
                                DataManager.updateDebt(context, updated); ; selectedDebtForPayment = null
                            },
                            onPermanentDelete = {
                                val current = selectedDebtForPayment!!
                                if (current.amount > 0.0) { // Keep Impact if revert was FALSE
                                    val impact = if (current.type == DebtType.I_OWE) current.amount - current.paidAmount else current.paidAmount - current.amount
                                    if (impact > 0) DataManager.addIncome(context, Date(), impact, SaveOp.ADD)
                                    else if (impact < 0) DataManager.addExpense(context, Date(), ExpenseCategory.OTHERS, -impact, SaveOp.ADD)
                                }
                                DataManager.deleteDebt(context, current.id); ; selectedDebtForPayment = null
                            },
                            onRestoreFromArchive = {
                                val current = selectedDebtForPayment!!
                                // Restore original values to reconnect with Main Balance
                                val updated = if (current.amount == 0.0 && current.archivedAmount > 0.0) {
                                    current.copy(isArchived = false, amount = current.archivedAmount, paidAmount = current.archivedPaidAmount)
                                } else { current.copy(isArchived = false) }
                                DataManager.updateDebt(context, updated); ; selectedDebtForPayment = null; showArchive = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(textColor: Color, isHistory: Boolean, isArchive: Boolean, onAddClick: () -> Unit) {
    val title = when {
        isArchive -> "Archive Empty"
        isHistory -> "No History Yet"
        else -> "Debt Free!"
    }
    val icon = when {
        isArchive -> Icons.Default.Inventory2
        isHistory -> Icons.Default.History
        else -> Icons.Default.CheckCircle
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(200.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF007AFF).copy(alpha=0.1f), Color.Magenta.copy(alpha=0.1f)))))
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(90.dp), tint = Color(0xFF007AFF))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(title, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = textColor)
        Text(
            text = when {
                isArchive -> "Your deleted debts will appear here."
                isHistory -> "You haven't completed any debt transactions yet."
                else -> "You have no pending payables or receivables.\nEnjoy your financial freedom!"
            },
            fontSize = 16.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp, vertical = 10.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        if (!isHistory && !isArchive) {
            Button(onClick = onAddClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)), shape = CircleShape, contentPadding = PaddingValues(horizontal = 30.dp, vertical = 14.dp)) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add First Entry", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtRowCard(
    debt: DebtItem, cardColor: Color, textColor: Color, isHistory: Boolean, isArchive: Boolean, modifier: Modifier = Modifier,
    onCardClick: () -> Unit, onMarkPaid: () -> Unit, onSwipeAction: () -> Unit
) {
    val isReceived = debt.type == DebtType.THEY_OWE
    val progress = if (debt.displayAmount > 0) (debt.displayPaidAmount / debt.displayAmount).toFloat() else 0f

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.StartToEnd || it == SwipeToDismissBoxValue.EndToStart) {
                onSwipeAction()
                false // Snap back, let dialog handle it
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            Box(
                modifier = Modifier.fillMaxSize().padding(vertical = 2.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (isArchive) Color(0xFFFF3B30) else Color(0xFFFF9500)).padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) { Icon(if (isArchive) Icons.Default.Delete else Icons.Default.Inventory2, contentDescription = "Action", tint = Color.White, modifier = Modifier.size(28.dp)) }
        },
        content = {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = cardColor), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), modifier = modifier.fillMaxWidth().clickable { onCardClick() }) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(if (isReceived) Color(0xFF34C759).copy(alpha=0.1f) else Color(0xFFFF3B30).copy(alpha=0.1f)), contentAlignment = Alignment.Center) {
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
                        Text("৳${String.format(Locale.US, "%.0f", if (isHistory || isArchive) debt.displayAmount else debt.remainingAmount)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isReceived) Color(0xFF34C759) else Color(0xFFFF3B30))
                        Spacer(modifier = Modifier.height(5.dp))
                        if (isArchive) {
                            Box(modifier = Modifier.clip(CircleShape).background(Color.Gray.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 6.dp)) { Text("Archived", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold) }
                        } else if (isHistory) {
                            Box(modifier = Modifier.clip(CircleShape).background(Color.Gray.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 6.dp)) { Text("Completed", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold) }
                        } else {
                            Box(modifier = Modifier.clip(CircleShape).background(Color(0xFF007AFF).copy(alpha = 0.1f)).clickable { onMarkPaid() }.padding(horizontal = 12.dp, vertical = 6.dp)) { Text("Mark Paid", fontSize = 12.sp, color = Color(0xFF007AFF), fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaymentSheetContent(
    debt: DebtItem, textColor: Color, isArchivedMode: Boolean, onDismiss: () -> Unit, onSavePayment: (Double) -> Unit,
    onEditPayment: (PaymentRecord, Double) -> Unit, onDeletePayment: (PaymentRecord, Boolean) -> Unit,
    onEditMainDebt: (String, Double, Date?) -> Unit, onRestoreDebtFromComplete: () -> Unit, onArchiveDebt: (Boolean) -> Unit,
    onPermanentDelete: () -> Unit, onRestoreFromArchive: () -> Unit
) {
    var paymentInput by remember { mutableStateOf("") }
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    var showArchiveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var revertMainBalance by remember { mutableStateOf(true) }

    var paymentToDelete by remember { mutableStateOf<PaymentRecord?>(null) }
    var revertPaymentBalance by remember { mutableStateOf(true) }

    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showEditMainDialog by remember { mutableStateOf(false) }

    var editMainName by remember { mutableStateOf(debt.name) }
    var editMainAmount by remember { mutableStateOf(String.format(Locale.US, "%.0f", debt.displayAmount)) }
    var editDeadline by remember { mutableStateOf(debt.deadline) }

    val context = LocalContext.current
    val deadlineCalendar = Calendar.getInstance().apply { editDeadline?.let { time = it } }
    val editDeadlinePickerDialog = DatePickerDialog(context, { _, year, month, dayOfMonth -> val newCal = Calendar.getInstance(); newCal.set(year, month, dayOfMonth); editDeadline = newCal.time }, deadlineCalendar.get(Calendar.YEAR), deadlineCalendar.get(Calendar.MONTH), deadlineCalendar.get(Calendar.DAY_OF_MONTH))

    var recordToEdit by remember { mutableStateOf<PaymentRecord?>(null) }
    var editAmountInput by remember { mutableStateOf("") }
    val isReceived = debt.type == DebtType.THEY_OWE

    // Archive Dialog (Soft Delete)
    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false }, title = { Text("Move to Archive", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Are you sure you want to archive this transaction?", color = textColor)
                    Spacer(modifier = Modifier.height(15.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = revertMainBalance, onCheckedChange = { revertMainBalance = it }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFF9500)))
                        Text("Revert from Main Balance", fontSize = 14.sp, color = textColor)
                    }
                }
            },
            containerColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White,
            confirmButton = { TextButton(onClick = { showArchiveConfirm = false; onArchiveDebt(revertMainBalance) }) { Text("Archive", color = Color(0xFFFF9500), fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showArchiveConfirm = false }) { Text("Cancel", color = Color.Gray) } }
        )
    }

    // Permanent Delete Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false }, title = { Text("Permanent Delete", fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete this record. You cannot recover it.", color = textColor) },
            containerColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White,
            confirmButton = { TextButton(onClick = { showDeleteConfirm = false; onPermanentDelete() }) { Text("Delete", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = Color.Gray) } }
        )
    }

    if (paymentToDelete != null) {
        AlertDialog(
            onDismissRequest = { paymentToDelete = null }, title = { Text("Delete Payment", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Are you sure you want to delete this payment record?", color = textColor)
                    Spacer(modifier = Modifier.height(15.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = revertPaymentBalance, onCheckedChange = { revertPaymentBalance = it }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFF3B30)))
                        Text("Revert from Main Balance", fontSize = 14.sp, color = textColor)
                    }
                }
            },
            containerColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White,
            confirmButton = { TextButton(onClick = { onDeletePayment(paymentToDelete!!, revertPaymentBalance); paymentToDelete = null }) { Text("Delete", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { paymentToDelete = null }) { Text("Cancel", color = Color.Gray) } }
        )
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false }, title = { Text("Restart Transaction") },
            text = { Text("Reset this debt and start from 0? All payment history will be cleared.", color = textColor) },
            containerColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White,
            confirmButton = { TextButton(onClick = { showRestoreConfirm = false; onRestoreDebtFromComplete() }) { Text("Restart", color = Color(0xFF007AFF), fontWeight = FontWeight.Bold) } },
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
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { editDeadlinePickerDialog.show() }.padding(vertical = 12.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Event, contentDescription = "Deadline", tint = if (editDeadline != null) Color(0xFFFF3B30) else Color.Gray, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        if (editDeadline == null) { Text("Set Deadline (Optional)", color = Color.Gray, fontSize = 14.sp) } else { Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(editDeadline!!), color = Color(0xFFFF3B30), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Box(modifier = Modifier.size(24.dp).clip(CircleShape).clickable { editDeadline = null }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp)) } }
                    }
                }
            },
            containerColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White,
            confirmButton = { TextButton(onClick = { val newAmt = editMainAmount.toDoubleOrNull() ?: debt.displayAmount; val newName = editMainName.takeIf { it.isNotBlank() } ?: debt.name; onEditMainDebt(newName, newAmt, editDeadline); showEditMainDialog = false }) { Text("Save", color = Color(0xFF007AFF), fontWeight = FontWeight.Bold) } },
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
        // HIGHLIGHT: Overlap Fix for Title
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(text = "Transaction Details", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = textColor, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 36.dp))
            Box(modifier = Modifier.align(Alignment.CenterEnd).clip(CircleShape).background(Color.Gray.copy(alpha = 0.1f)).clickable { onDismiss() }.padding(6.dp)) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(20.dp)) }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(debt.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Created: ${SimpleDateFormat("dd MMM yy", Locale.getDefault()).format(debt.date)}", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Event, contentDescription = null, tint = if (debt.deadline != null) Color(0xFFFF3B30) else Color.Gray, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = if (debt.deadline != null) "Deadline: ${SimpleDateFormat("dd MMM yy", Locale.getDefault()).format(debt.deadline!!)}" else "No deadline", fontSize = 12.sp, color = if (debt.deadline != null) Color(0xFFFF3B30) else Color.Gray, fontWeight = FontWeight.Medium)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isArchivedMode) {
                    Box(modifier = Modifier.clip(CircleShape).background(Color(0xFF34C759).copy(alpha = 0.1f)).clickable { onRestoreFromArchive() }.padding(8.dp)) { Icon(Icons.Default.Restore, contentDescription = "Restore", tint = Color(0xFF34C759), modifier = Modifier.size(20.dp)) }
                    Box(modifier = Modifier.clip(CircleShape).background(Color(0xFFFF3B30).copy(alpha = 0.1f)).clickable { showDeleteConfirm = true }.padding(8.dp)) { Icon(Icons.Default.DeleteForever, contentDescription = "Delete Permanent", tint = Color(0xFFFF3B30), modifier = Modifier.size(20.dp)) }
                } else {
                    if (debt.isPaid) Box(modifier = Modifier.clip(CircleShape).background(Color(0xFF34C759).copy(alpha = 0.1f)).clickable { showRestoreConfirm = true }.padding(8.dp)) { Icon(Icons.Default.Refresh, contentDescription = "Restart", tint = Color(0xFF34C759), modifier = Modifier.size(20.dp)) }
                    Box(modifier = Modifier.clip(CircleShape).background(Color(0xFF007AFF).copy(alpha = 0.1f)).clickable { editMainName = debt.name; editMainAmount = String.format(Locale.US, "%.0f", debt.displayAmount); editDeadline = debt.deadline; showEditMainDialog = true }.padding(8.dp)) { Icon(Icons.Default.Edit, contentDescription = "Edit Info", tint = Color(0xFF007AFF), modifier = Modifier.size(20.dp)) }
                    Box(modifier = Modifier.clip(CircleShape).background(Color(0xFFFF9500).copy(alpha = 0.1f)).clickable { showArchiveConfirm = true; revertMainBalance = true }.padding(8.dp)) { Icon(Icons.Default.Inventory2, contentDescription = "Archive", tint = Color(0xFFFF9500), modifier = Modifier.size(20.dp)) }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("Total Amount", fontSize = 12.sp, color = Color.Gray); Text("৳${String.format(Locale.US, "%.0f", debt.displayAmount)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor) }
            Column(horizontalAlignment = Alignment.End) { Text("Remaining", fontSize = 12.sp, color = Color.Gray); Text("৳${String.format(Locale.US, "%.0f", debt.remainingAmount)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isReceived) Color(0xFF34C759) else Color(0xFFFF3B30)) }
        }
        Spacer(modifier = Modifier.height(20.dp))

        if (debt.isPaid && !isArchivedMode) {
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF34C759).copy(alpha = 0.1f)).padding(16.dp), contentAlignment = Alignment.Center) { Text("🎉 Fully Settled", color = Color(0xFF34C759), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        } else if (!isArchivedMode) {
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
                            Text("৳${String.format(Locale.US, "%.0f", record.amount)}", color = textColor, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                            if (!isArchivedMode) {
                                Spacer(modifier = Modifier.width(15.dp))
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF007AFF).copy(alpha = 0.8f), modifier = Modifier.size(20.dp).clickable { recordToEdit = record; editAmountInput = String.format(Locale.US, "%.0f", record.amount) })
                                Spacer(modifier = Modifier.width(10.dp))
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF3B30).copy(alpha = 0.8f), modifier = Modifier.size(20.dp).clickable { paymentToDelete = record; revertPaymentBalance = true })
                            }
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
    var selectedDeadline by remember { mutableStateOf<Date?>(null) }

    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val isValid = newName.isNotEmpty() && newAmount.isNotEmpty()

    val calendar = Calendar.getInstance().apply { time = selectedDate }
    val datePickerDialog = DatePickerDialog(context, { _, year, month, dayOfMonth -> val newCal = Calendar.getInstance(); newCal.set(year, month, dayOfMonth); selectedDate = newCal.time }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))

    val deadlineCalendar = Calendar.getInstance().apply { selectedDeadline?.let { time = it } }
    val deadlinePickerDialog = DatePickerDialog(context, { _, year, month, dayOfMonth -> val newCal = Calendar.getInstance(); newCal.set(year, month, dayOfMonth); selectedDeadline = newCal.time }, deadlineCalendar.get(Calendar.YEAR), deadlineCalendar.get(Calendar.MONTH), deadlineCalendar.get(Calendar.DAY_OF_MONTH))

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        // HIGHLIGHT: Overlap Fix for Title
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(text = "New Transaction", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = textColor, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 36.dp))
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
                Box(modifier = Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(if (isValid) Color(0xFF4CAF50) else Color.Gray.copy(alpha = 0.5f)).clickable(enabled = isValid) { val amount = newAmount.toDoubleOrNull() ?: 0.0; if (amount > 0) onSave(DebtItem(name = newName, amount = amount, type = newType, date = selectedDate, deadline = selectedDeadline)) }, contentAlignment = Alignment.Center) { Text("✓", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { deadlinePickerDialog.show() }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Event, contentDescription = "Deadline", tint = if (selectedDeadline != null) Color(0xFFFF3B30) else Color.Gray, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            if (selectedDeadline == null) { Text("Add Deadline (Optional)", color = Color.Gray, fontSize = 15.sp, fontWeight = FontWeight.Medium) } else { Text("Target: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(selectedDeadline!!)}", color = Color(0xFFFF3B30), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.15f)).clickable { selectedDeadline = null }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, contentDescription = "Clear Deadline", tint = Color.Gray, modifier = Modifier.size(14.dp)) } }
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

fun processMarkPaid(context: android.content.Context, debt: DebtItem, onComplete: () -> Unit) {
    val remaining = debt.remainingAmount
    val updatedHistory = debt.paymentHistory.toMutableList()
    if (remaining > 0) updatedHistory.add(PaymentRecord(amount = remaining, date = Date()))
    DataManager.updateDebt(context, debt.copy(paidAmount = debt.displayAmount, isPaid = true, paymentHistory = updatedHistory))
    onComplete()
}

fun Color.opacity(alpha: Float): Color = this.copy(alpha = alpha)
