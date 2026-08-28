package com.example.myapplication

import androidx.annotation.Keep
import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.material.icons.automirrored.filled.*
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
data class PaymentRecord(val amount: Double, val date: Date = Date(), val note: String? = "")

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
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var triggerConfetti by remember { mutableStateOf(false) }

    val debts by DataManager.getDebtsFlow(context).collectAsState(initial = DataManager.cachedDebts ?: emptyList())

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

    val bgColor = ThemeState.background.value
    val cardColor = ThemeState.cardBackground.value
    val textColor = if (ThemeState.isDark.value) Color.White else Color.Black

    val primaryColor = ThemeState.primaryAccent.value

    Scaffold(containerColor = bgColor) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // --- TOP HEADER ---
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            showArchive -> "Archived Debts"
                            showHistory -> "Debt History"
                            else -> "Active Debts"
                        },
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = primaryColor,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showHistory && historyDebts.isNotEmpty()) {
                            Box(modifier = Modifier.clip(CircleShape).background(Color(0xFFFF3B30).copy(alpha = 0.1f)).clickable {
                                showClearHistoryConfirm = true
                            }.padding(8.dp)) {
                                Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear All History", tint = Color(0xFFFF3B30), modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        // PDF Export Button
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(primaryColor.copy(alpha = 0.1f))
                                .clickable {
                                    val listToExport = when {
                                        showArchive -> archivedDebts
                                        showHistory -> historyDebts
                                        else -> (thisMonthDebts + pastMonthDebts)
                                    }
                                    val title = when {
                                        showArchive -> "Archived Debts"
                                        showHistory -> "Completed Debt History"
                                        else -> "Active Debts & Loans"
                                    }
                                    exportDebtStatementToPdf(context, listToExport, title)
                                }
                                .padding(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = primaryColor, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(modifier = Modifier.clip(CircleShape).background(if (showArchive) primaryColor.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f)).clickable {
                            showArchive = !showArchive; if (showArchive) showHistory = false
                        }.padding(8.dp)) {
                            Icon(imageVector = if (showArchive) Icons.Default.Close else Icons.Default.Inventory2, contentDescription = "Toggle Archive", tint = if (showArchive) primaryColor else textColor, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(modifier = Modifier.clip(CircleShape).background(if (showHistory) primaryColor.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f)).clickable {
                            showHistory = !showHistory; if (showHistory) showArchive = false
                        }.padding(8.dp)) {
                            Icon(imageVector = if (showHistory) Icons.Default.Close else Icons.Default.History, contentDescription = "Toggle History", tint = if (showHistory) primaryColor else textColor, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(modifier = Modifier.clip(CircleShape).background(primaryColor.copy(alpha = 0.1f)).clickable { showAddSheet = true }.padding(8.dp)) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Debt", tint = primaryColor, modifier = Modifier.size(24.dp))
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
                                DebtRowCard(debt = debt, cardColor = cardColor, textColor = textColor, isHistory = false, isArchive = true, modifier = Modifier.animateItem(), onCardClick = { selectedDebtForPayment = debt }, onMarkPaid = { })
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        } else if (showHistory) {
                            item {
                                DebtHistoryAnalyticsCard(completedDebts = historyDebts, cardColor = cardColor, textColor = textColor)
                            }
                            items(historyDebts, key = { it.id }) { debt ->
                                DebtRowCard(debt = debt, cardColor = cardColor, textColor = textColor, isHistory = true, isArchive = false, modifier = Modifier.animateItem(), onCardClick = { selectedDebtForPayment = debt }, onMarkPaid = { })
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        } else {
                            val activeDebtsList = thisMonthDebts + pastMonthDebts
                            if (activeDebtsList.isNotEmpty()) {
                                item {
                                    DebtActiveAnalyticsCard(activeDebts = activeDebtsList, cardColor = cardColor, textColor = textColor)
                                }
                            }
                            if (thisMonthDebts.isNotEmpty()) {
                                items(thisMonthDebts, key = { it.id }) { debt ->
                                    DebtRowCard(debt = debt, cardColor = cardColor, textColor = textColor, isHistory = false, isArchive = false, modifier = Modifier.animateItem(), onCardClick = { selectedDebtForPayment = debt }, onMarkPaid = { processMarkPaid(context, debt) { triggerConfetti = true } })
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
                                    DebtRowCard(debt = debt, cardColor = cardColor, textColor = textColor, isHistory = false, isArchive = false, modifier = Modifier.animateItem(), onCardClick = { selectedDebtForPayment = debt }, onMarkPaid = { processMarkPaid(context, debt) { triggerConfetti = true } })
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showClearHistoryConfirm) {
            AlertDialog(
                onDismissRequest = { showClearHistoryConfirm = false },
                title = { Text("Clear Debt History", fontWeight = FontWeight.Bold, color = textColor) },
                text = { Text("Are you sure you want to permanently delete all completed debt transactions? This action cannot be undone.", color = textColor) },
                containerColor = cardColor,
                confirmButton = {
                    TextButton(onClick = {
                        historyDebts.forEach { DataManager.deleteDebt(context, it.id) }
                        showClearHistoryConfirm = false
                    }) { Text("Clear All", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showClearHistoryConfirm = false }) { Text("Cancel", color = Color.Gray) } }
            )
        }

        if (showAddSheet) {
            Dialog(onDismissRequest = { showAddSheet = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .imePadding(),
                    color = bgColor
                ) {
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

        if (selectedDebtForPayment != null) {
            Dialog(onDismissRequest = { selectedDebtForPayment = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Box(modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp, vertical = 20.dp), contentAlignment = Alignment.Center) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        PaymentSheetContent(
                            debt = selectedDebtForPayment!!, textColor = textColor, isArchivedMode = showArchive, onDismiss = { selectedDebtForPayment = null },
                            onSavePayment = { amount, note ->
                                val current = selectedDebtForPayment!!
                                val safeAmount = if (current.paidAmount + amount > current.amount) current.remainingAmount else amount
                                val newPaidAmount = current.paidAmount + safeAmount
                                val updatedHistory = current.paymentHistory.toMutableList().apply { add(PaymentRecord(amount = safeAmount, date = Date(), note = note)) }
                                val updatedDebt = current.copy(paidAmount = newPaidAmount, isPaid = newPaidAmount >= current.amount, paymentHistory = updatedHistory)
                                DataManager.updateDebt(context, updatedDebt); selectedDebtForPayment = updatedDebt
                                triggerConfetti = true
                            },
                            onAddDebtAmount = { amount, note ->
                                val current = selectedDebtForPayment!!
                                val newAmount = current.amount + amount
                                val updatedHistory = current.paymentHistory.toMutableList().apply { add(PaymentRecord(amount = -amount, date = Date(), note = note)) }
                                val updatedDebt = current.copy(amount = newAmount, isPaid = current.paidAmount >= newAmount, paymentHistory = updatedHistory)
                                DataManager.updateDebt(context, updatedDebt); selectedDebtForPayment = updatedDebt
                            },
                            onEditPayment = { oldRecord, newAmount, newNote ->
                                val current = selectedDebtForPayment!!
                                val updatedHistory = current.paymentHistory.toMutableList()
                                val index = updatedHistory.indexOf(oldRecord)
                                if (index != -1) {
                                    if (oldRecord.amount < 0) {
                                        val amountWithoutRecord = current.amount - Math.abs(oldRecord.amount)
                                        val newTotalAmount = amountWithoutRecord + newAmount
                                        updatedHistory[index] = oldRecord.copy(amount = -newAmount, note = newNote)
                                        val updatedDebt = current.copy(amount = newTotalAmount, isPaid = current.paidAmount >= newTotalAmount, paymentHistory = updatedHistory)
                                        DataManager.updateDebt(context, updatedDebt); selectedDebtForPayment = updatedDebt
                                    } else {
                                        val paidWithoutRecord = current.paidAmount - oldRecord.amount
                                        val maxAllowed = current.amount - paidWithoutRecord
                                        val safeNewAmount = if (newAmount > maxAllowed) maxAllowed else newAmount
                                        updatedHistory[index] = oldRecord.copy(amount = safeNewAmount, note = newNote)
                                        val finalPaidAmount = paidWithoutRecord + safeNewAmount
                                        val updatedDebt = current.copy(paidAmount = finalPaidAmount, isPaid = finalPaidAmount >= current.amount, paymentHistory = updatedHistory)
                                        DataManager.updateDebt(context, updatedDebt); selectedDebtForPayment = updatedDebt
                                    }
                                }
                            },
                            onDeletePayment = { recordToRemove, revertBalance ->
                                val current = selectedDebtForPayment!!
                                val updatedHistory = current.paymentHistory.toMutableList().apply { remove(recordToRemove) }
                                val updatedDebt = if (recordToRemove.amount < 0) {
                                    val newAmount = current.amount - Math.abs(recordToRemove.amount)
                                    current.copy(amount = newAmount, isPaid = current.paidAmount >= newAmount, paymentHistory = updatedHistory)
                                } else {
                                    val safePaidAmount = maxOf(0.0, current.paidAmount - recordToRemove.amount)
                                    current.copy(paidAmount = safePaidAmount, isPaid = safePaidAmount >= current.amount, paymentHistory = updatedHistory)
                                }
                                DataManager.updateDebt(context, updatedDebt); selectedDebtForPayment = updatedDebt
                            },
                            onEditMainDebt = { newName, newTotalAmount, newDeadline, newLinked, newNote ->
                                val current = selectedDebtForPayment!!
                                val updatedDebt = current.copy(name = newName, amount = newTotalAmount, deadline = newDeadline, isLinkedWithBalance = newLinked, note = newNote, isPaid = current.paidAmount >= newTotalAmount)
                                DataManager.updateDebt(context, updatedDebt); selectedDebtForPayment = updatedDebt
                            },
                            onRestoreDebtFromComplete = {
                                val updatedDebt = selectedDebtForPayment!!.copy(isPaid = false, paidAmount = 0.0, paymentHistory = mutableListOf())
                                DataManager.updateDebt(context, updatedDebt); selectedDebtForPayment = null; showHistory = false; showArchive = false
                            },
                            onArchiveDebt = { revertBalance ->
                                val current = selectedDebtForPayment!!
                                val updated = if (revertBalance) {
                                    current.copy(
                                        isArchived = true, archivedAmount = current.amount, archivedPaidAmount = current.paidAmount,
                                        amount = 0.0, paidAmount = 0.0 // Instantly cuts connection from Main Balance
                                    )
                                } else { current.copy(isArchived = true) }
                                DataManager.updateDebt(context, updated); selectedDebtForPayment = null
                            },
                            onPermanentDelete = {
                                val current = selectedDebtForPayment!!
                                DataManager.deleteDebt(context, current.id); selectedDebtForPayment = null
                            },
                            onRestoreFromArchive = {
                                val current = selectedDebtForPayment!!
                                // Restore original values to reconnect with Main Balance
                                val updated = if (current.amount == 0.0 && current.archivedAmount > 0.0) {
                                    current.copy(isArchived = false, amount = current.archivedAmount, paidAmount = current.archivedPaidAmount)
                                } else { current.copy(isArchived = false) }
                                DataManager.updateDebt(context, updated); selectedDebtForPayment = null; showArchive = false
                            }
                        )
                    }
                }
            }
        }
        ConfettiExplosion(trigger = triggerConfetti, onFinished = { triggerConfetti = false })
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

    val primaryColor = ThemeState.primaryAccent.value
    val isDark = ThemeState.isDark.value

    val infiniteTransition = rememberInfiniteTransition(label = "debt_empty_anim")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_offset"
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

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
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

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Dynamic Animated Centerpiece
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(200.dp)
        ) {
            // Ambient particle background
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                if (w > 0 && h > 0) {
                    val p1X = w * 0.25f + (kotlin.math.sin(particlePhase * 2 * Math.PI).toFloat() * 25f)
                    val p1Y = h * 0.25f + (kotlin.math.cos(particlePhase * 2 * Math.PI).toFloat() * 20f)
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.12f * glowAlpha),
                        radius = 45f,
                        center = Offset(p1X, p1Y)
                    )

                    val p2X = w * 0.75f + (kotlin.math.cos(particlePhase * 2 * Math.PI).toFloat() * 25f)
                    val p2Y = h * 0.75f + (kotlin.math.sin(particlePhase * 2 * Math.PI).toFloat() * 20f)
                    drawCircle(
                        color = Color(0xFF34C759).copy(alpha = 0.12f * glowAlpha),
                        radius = 50f,
                        center = Offset(p2X, p2Y)
                    )
                }
            }

            // Outer continuous rotating glow sweep
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .scale(pulseScale)
                    .rotate(rotationAngle)
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            listOf(
                                primaryColor.copy(alpha = glowAlpha * 0.35f),
                                Color(0xFF34C759).copy(alpha = glowAlpha * 0.25f),
                                primaryColor.copy(alpha = glowAlpha * 0.35f)
                            )
                        )
                    )
            )

            // Orbit track line
            if (!isHistory && !isArchive) {
                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .clip(CircleShape)
                        .border(1.dp, primaryColor.copy(alpha = 0.2f), CircleShape)
                )
            }

            // Inner 3D circular container
            Surface(
                shape = CircleShape,
                color = if (isDark) Color(0xFF1E1E24) else Color(0xFFF7F8FA),
                border = BorderStroke(2.dp, primaryColor.copy(alpha = 0.4f)),
                shadowElevation = if (isDark) 0.dp else 8.dp,
                modifier = Modifier.size(90.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (!isHistory && !isArchive) {
                        Text(
                            text = "🤝",
                            fontSize = 38.sp,
                            modifier = Modifier.scale(pulseScale)
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .scale(pulseScale),
                            tint = if (isArchive) Color.Gray else Color(0xFF007AFF)
                        )
                    }
                }
            }

            // Orbiting real-life planets (Solar System)
            if (!isHistory && !isArchive) {
                val debtOrbitRadius = 66.0
                val angleRad = (rotationAngle * Math.PI / 180.0)

                val xScale = (kotlin.math.cos(angleRad) * debtOrbitRadius).toFloat()
                val yScale = (kotlin.math.sin(angleRad) * debtOrbitRadius).toFloat()

                val xShield = (kotlin.math.cos(angleRad + 2.0 * Math.PI / 3.0) * debtOrbitRadius).toFloat()
                val yShield = (kotlin.math.sin(angleRad + 2.0 * Math.PI / 3.0) * debtOrbitRadius).toFloat()

                val xPeace = (kotlin.math.cos(angleRad + 4.0 * Math.PI / 3.0) * debtOrbitRadius).toFloat()
                val yPeace = (kotlin.math.sin(angleRad + 4.0 * Math.PI / 3.0) * debtOrbitRadius).toFloat()

                // 1. Balanced Scale (⚖️)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset(x = xScale.dp, y = yScale.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF1E1E24) else Color.White)
                        .border(1.2.dp, Color(0xFF34C759).copy(alpha = 0.7f), CircleShape)
                ) {
                    Text("⚖️", fontSize = 16.sp)
                }

                // 2. Shield (🛡️)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset(x = xShield.dp, y = yShield.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF1E1E24) else Color.White)
                        .border(1.2.dp, Color(0xFFFFD700).copy(alpha = 0.7f), CircleShape)
                ) {
                    Text("🛡️", fontSize = 16.sp)
                }

                // 3. Peace / Sparkle (✨)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset(x = xPeace.dp, y = yPeace.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF1E1E24) else Color.White)
                        .border(1.2.dp, primaryColor.copy(alpha = 0.7f), CircleShape)
                ) {
                    Text("✨", fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (!isHistory && !isArchive) "All Debts Settled 🤝" else title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = textColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = when {
                isArchive -> "Your deleted debts will appear here."
                isHistory -> "You haven't completed any debt transactions yet."
                else -> "Clean accounts, stronger relationships! You have ৳0 pending borrows or lends with roommates & friends."
            },
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!isHistory && !isArchive) {
            Button(
                onClick = onAddClick,
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 13.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add First Entry", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DebtRowCard(
    debt: DebtItem, cardColor: Color, textColor: Color, isHistory: Boolean, isArchive: Boolean, modifier: Modifier = Modifier,
    onCardClick: () -> Unit, onMarkPaid: () -> Unit
) {
    val primaryColor = ThemeState.primaryAccent.value
    val isReceived = debt.type == DebtType.THEY_OWE
    val progress = if (debt.displayAmount > 0) (debt.displayPaidAmount / debt.displayAmount).toFloat() else 0f

    val infiniteTransition = rememberInfiniteTransition(label = "debt_row_flow")
    val flowOffset by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flow_offset"
    )
    val flowScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flow_scale"
    )

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (ThemeState.isDark.value) 0.dp else 1.5.dp),
        border = if (ThemeState.isDark.value) BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)) else null,
        modifier = modifier.fillMaxWidth().clickable { onCardClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Live Dynamic Animated Arrow Badge + RECEIVABLE/PAYABLE Tag underneath
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(62.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .scale(flowScale)
                        .clip(CircleShape)
                        .background(if (isReceived) Color(0xFF34C759).copy(alpha = 0.14f) else Color(0xFFFF3B30).copy(alpha = 0.14f))
                        .border(1.dp, if (isReceived) Color(0xFF34C759).copy(alpha = 0.35f) else Color(0xFFFF3B30).copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isReceived) Icons.Default.SouthWest else Icons.Default.NorthEast,
                        contentDescription = if (isReceived) "Receivable" else "Payable",
                        tint = if (isReceived) Color(0xFF34C759) else Color(0xFFFF3B30),
                        modifier = Modifier
                            .size(17.dp)
                            .offset(
                                x = if (isReceived) (-flowOffset).dp else flowOffset.dp,
                                y = if (isReceived) flowOffset.dp else (-flowOffset).dp
                            )
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = if (isReceived) Color(0xFF34C759).copy(alpha = 0.14f) else Color(0xFFFF3B30).copy(alpha = 0.14f)
                ) {
                    Text(
                        text = if (isReceived) "RECEIVABLE" else "PAYABLE",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isReceived) Color(0xFF34C759) else Color(0xFFFF3B30),
                        letterSpacing = 0.3.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Center Info: Name + UNLINKED badge, Progress Bar & Due/Note
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = debt.name,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = 2,
                        lineHeight = 18.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (!debt.isLinkedWithBalance) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Gray.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "UNLINKED",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                letterSpacing = 0.4.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(3.5.dp)
                            .clip(CircleShape),
                        color = if (isReceived) Color(0xFF34C759) else primaryColor,
                        trackColor = (if (ThemeState.isDark.value) Color.White else Color.Black).copy(alpha = 0.08f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Gray
                    )
                }

                if (debt.deadline != null && !isHistory && !isArchive) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Due: " + SimpleDateFormat("dd MMM", Locale.getDefault()).format(debt.deadline!!),
                        fontSize = 10.5.sp,
                        color = if (ThemeState.isDark.value) Color(0xFFFF453A) else Color(0xFFFF3B30),
                        fontWeight = FontWeight.Bold
                    )
                } else if (!debt.note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = debt.note ?: "",
                        fontSize = 10.5.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right Side: Amount and Action Button
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "৳${String.format(Locale.US, "%.0f", if (isHistory || isArchive) debt.displayAmount else debt.remainingAmount)}",
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isReceived) Color(0xFF34C759) else Color(0xFFFF3B30)
                )
                Spacer(modifier = Modifier.height(3.dp))
                if (isArchive) {
                    Surface(shape = RoundedCornerShape(6.dp), color = Color.Gray.copy(alpha = 0.1f)) {
                        Text("Archived", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                } else if (isHistory) {
                    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF34C759).copy(alpha = 0.1f)) {
                        Text("Completed", fontSize = 10.sp, color = Color(0xFF34C759), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                } else {
                    Surface(
                        onClick = onMarkPaid,
                        shape = RoundedCornerShape(6.dp),
                        color = primaryColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "Mark Paid",
                            fontSize = 10.5.sp,
                            color = primaryColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaymentSheetContent(
    debt: DebtItem, textColor: Color, isArchivedMode: Boolean, onDismiss: () -> Unit,
    onSavePayment: (Double, String) -> Unit, onAddDebtAmount: (Double, String) -> Unit,
    onEditPayment: (PaymentRecord, Double, String) -> Unit, onDeletePayment: (PaymentRecord, Boolean) -> Unit,
    onEditMainDebt: (String, Double, Date?, Boolean, String) -> Unit, onRestoreDebtFromComplete: () -> Unit, onArchiveDebt: (Boolean) -> Unit,
    onPermanentDelete: () -> Unit, onRestoreFromArchive: () -> Unit
) {
    var paymentInput by remember { mutableStateOf("") }
    var paymentNote by remember { mutableStateOf("") }
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
    var editIsLinkedWithBalance by remember { mutableStateOf(debt.isLinkedWithBalance) }
    var editNote by remember { mutableStateOf(debt.note ?: "") }

    val context = LocalContext.current
    val deadlineCalendar = Calendar.getInstance().apply { editDeadline?.let { time = it } }
    val editDeadlinePickerDialog = DatePickerDialog(context, { _, year, month, dayOfMonth -> val newCal = Calendar.getInstance(); newCal.set(year, month, dayOfMonth); editDeadline = newCal.time }, deadlineCalendar.get(Calendar.YEAR), deadlineCalendar.get(Calendar.MONTH), deadlineCalendar.get(Calendar.DAY_OF_MONTH))

    var recordToEdit by remember { mutableStateOf<PaymentRecord?>(null) }
    var editAmountInput by remember { mutableStateOf("") }
    var editNoteInput by remember { mutableStateOf("") }
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

    val primaryColor = ThemeState.primaryAccent.value

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false }, title = { Text("Restart Transaction") },
            text = { Text("Reset this debt and start from 0? All payment history will be cleared.", color = textColor) },
            containerColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White,
            confirmButton = { TextButton(onClick = { showRestoreConfirm = false; onRestoreDebtFromComplete() }) { Text("Restart", color = primaryColor, fontWeight = FontWeight.Bold) } },
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
                    OutlinedTextField(value = editNote, onValueChange = { editNote = it }, label = { Text("Note / Purpose (Optional)") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor))
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { editDeadlinePickerDialog.show() }.padding(vertical = 12.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Event, contentDescription = "Deadline", tint = if (editDeadline != null) Color(0xFFFF3B30) else Color.Gray, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        if (editDeadline == null) { Text("Set Deadline (Optional)", color = Color.Gray, fontSize = 14.sp) } else { Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(editDeadline!!), color = Color(0xFFFF3B30), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Box(modifier = Modifier.size(24.dp).clip(CircleShape).clickable { editDeadline = null }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp)) } }
                    }

                    // Link to Main Balance Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (ThemeState.isDark.value) Color(0xFF2C2C2E) else Color(0xFFF2F2F7))
                            .clickable { editIsLinkedWithBalance = !editIsLinkedWithBalance }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Link to Main Balance", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
                            Text(if (editIsLinkedWithBalance) "Affects total wallet balance" else "Standalone record only", fontSize = 11.sp, color = Color.Gray)
                        }
                        Switch(
                            checked = editIsLinkedWithBalance,
                            onCheckedChange = { editIsLinkedWithBalance = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF34C759))
                        )
                    }
                }
            },
            containerColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White,
            confirmButton = { TextButton(onClick = { val newAmt = editMainAmount.toDoubleOrNull() ?: debt.displayAmount; val newName = editMainName.takeIf { it.isNotBlank() } ?: debt.name; onEditMainDebt(newName, newAmt, editDeadline, editIsLinkedWithBalance, editNote); showEditMainDialog = false }) { Text("Save", color = primaryColor, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showEditMainDialog = false }) { Text("Cancel", color = Color.Gray) } }
        )
    }

    if (recordToEdit != null) {
        AlertDialog(
            onDismissRequest = { recordToEdit = null }, title = { Text("Edit Payment Record", fontWeight = FontWeight.Bold, color = textColor) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editAmountInput,
                        onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) editAmountInput = it },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Text("৳", fontWeight = FontWeight.Bold, color = textColor) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor)
                    )
                    OutlinedTextField(
                        value = editNoteInput,
                        onValueChange = { editNoteInput = it },
                        label = { Text("Note / বিবরণ (Optional)") },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor)
                    )
                }
            },
            containerColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White,
            confirmButton = {
                TextButton(onClick = {
                    val newAmt = editAmountInput.toDoubleOrNull() ?: 0.0
                    if (newAmt > 0) onEditPayment(recordToEdit!!, newAmt, editNoteInput.trim())
                    recordToEdit = null
                }) { Text("Save", color = primaryColor, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { recordToEdit = null }) { Text("Cancel", color = Color.Gray) } }
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(text = "Transaction Details", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = textColor, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 36.dp))
            Box(modifier = Modifier.align(Alignment.CenterEnd).clip(CircleShape).background(Color.Gray.copy(alpha = 0.1f)).clickable { onDismiss() }.padding(6.dp)) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(20.dp)) }
        }
        Spacer(modifier = Modifier.height(20.dp))

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
                    Box(modifier = Modifier.clip(CircleShape).background(primaryColor.copy(alpha = 0.1f)).clickable { editMainName = debt.name; editMainAmount = String.format(Locale.US, "%.0f", debt.displayAmount); editDeadline = debt.deadline; editNote = debt.note ?: ""; showEditMainDialog = true }.padding(8.dp)) { Icon(Icons.Default.Edit, contentDescription = "Edit Info", tint = primaryColor, modifier = Modifier.size(20.dp)) }
                    Box(modifier = Modifier.clip(CircleShape).background(Color(0xFFFF9500).copy(alpha = 0.1f)).clickable { showArchiveConfirm = true; revertMainBalance = true }.padding(8.dp)) { Icon(Icons.Default.Inventory2, contentDescription = "Archive", tint = Color(0xFFFF9500), modifier = Modifier.size(20.dp)) }
                }
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("Total Amount", fontSize = 12.sp, color = Color.Gray); Text("৳${String.format(Locale.US, "%.0f", debt.displayAmount)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor) }
            Column(horizontalAlignment = Alignment.End) { Text("Remaining", fontSize = 12.sp, color = Color.Gray); Text("৳${String.format(Locale.US, "%.0f", debt.remainingAmount)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isReceived) Color(0xFF34C759) else Color(0xFFFF3B30)) }
        }
        Spacer(modifier = Modifier.height(18.dp))

        if (!isArchivedMode) {
            var isRepaymentMode by remember { mutableStateOf(true) }

            Column(modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester)) {
                // --- PREMIUM SEGMENTED MODE SELECTOR ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (ThemeState.isDark.value) Color(0xFF242426) else Color(0xFFEBEBF0))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val activeColor = if (isReceived) Color(0xFF34C759) else primaryColor

                    // Tab 1: Return / Repayment
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isRepaymentMode) activeColor else Color.Transparent)
                            .clickable { isRepaymentMode = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (isRepaymentMode) Color.White else Color.Gray,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isReceived) "Receive" else "Pay Back",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRepaymentMode) Color.White else Color.Gray,
                                maxLines = 1
                            )
                        }
                    }

                    // Tab 2: Add more borrow/lend
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!isRepaymentMode) activeColor else Color.Transparent)
                            .clickable { isRepaymentMode = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = if (!isRepaymentMode) Color.White else Color.Gray,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isReceived) "Lend More" else "Borrow More",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isRepaymentMode) Color.White else Color.Gray,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (isRepaymentMode && debt.isPaid) {
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF34C759).copy(alpha = 0.1f)).padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("🎉 Fully Settled", color = Color(0xFF34C759), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                } else {
                    val inputPlaceholder = if (isRepaymentMode) {
                        if (isReceived) "Received return amount" else "Paid back amount"
                    } else {
                        if (isReceived) "Additional lent amount" else "Additional borrowed amount"
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = paymentInput,
                            onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) paymentInput = it },
                            placeholder = { Text(inputPlaceholder, fontSize = 13.5.sp, maxLines = 1) },
                            leadingIcon = { Text("৳", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp), color = textColor) },
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { isFocused = it.isFocused; if (it.isFocused) { coroutineScope.launch { delay(300); bringIntoViewRequester.bringIntoView() } } },
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = paymentNote,
                                onValueChange = { paymentNote = it },
                                placeholder = { Text("Note / বিবরণ (e.g. Bkash, Cash...)", fontSize = 13.sp, maxLines = 1) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor)
                            )

                            Button(
                                onClick = {
                                    val amount = paymentInput.toDoubleOrNull() ?: 0.0
                                    if (amount > 0) {
                                        if (isRepaymentMode) {
                                            onSavePayment(amount, paymentNote.trim())
                                        } else {
                                            onAddDebtAmount(amount, paymentNote.trim())
                                        }
                                        paymentInput = ""
                                        paymentNote = ""
                                    }
                                },
                                enabled = paymentInput.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = if (isReceived) Color(0xFF34C759) else primaryColor),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(52.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                Text("Save", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        if (debt.paymentHistory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(22.dp))
            Text("Payment & Adjustment History", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                debt.paymentHistory.asReversed().forEach { record ->
                    val isAddition = record.amount < 0
                    val absAmount = Math.abs(record.amount)
                    val labelText = if (isAddition) {
                        if (isReceived) "Lent More" else "Borrowed More"
                    } else {
                        if (isReceived) "Received Payment" else "Paid Settle"
                    }
                    val amountText = if (isAddition) {
                        "+৳${String.format(Locale.US, "%.0f", absAmount)}"
                    } else {
                        "-৳${String.format(Locale.US, "%.0f", record.amount)}"
                    }
                    val amountColor = if (isAddition) {
                        if (isReceived) Color(0xFF34C759) else Color(0xFFFF3B30)
                    } else {
                        if (isReceived) Color(0xFFFF9500) else Color(0xFF34C759)
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(labelText, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = textColor)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(record.date), color = Color.Gray, fontSize = 11.5.sp)
                            if (!record.note.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("📝 " + (record.note ?: ""), color = textColor.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(amountText, color = amountColor, fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
                            if (!isArchivedMode) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = primaryColor.copy(alpha = 0.8f), modifier = Modifier.size(18.dp).clickable { recordToEdit = record; editAmountInput = String.format(Locale.US, "%.0f", absAmount); editNoteInput = record.note ?: "" })
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF3B30).copy(alpha = 0.8f), modifier = Modifier.size(18.dp).clickable { paymentToDelete = record; revertPaymentBalance = true })
                            }
                        }
                    }
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))
                }
            }
        }
    }
}

@Composable
fun AddDebtDialogContent(textColor: Color, onDismiss: () -> Unit, onSave: (DebtItem) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var newName by remember { mutableStateOf("") }
    var newAmount by remember { mutableStateOf("") }
    var newNote by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf(DebtType.I_OWE) }

    var selectedDate by remember { mutableStateOf(Date()) }
    var selectedDeadline by remember { mutableStateOf<Date?>(null) }
    var isLinkedWithBalance by remember { mutableStateOf(prefs.getBoolean("pref_include_debt_in_balance", true)) }

    val isValid = newName.isNotBlank() && (newAmount.toDoubleOrNull() ?: 0.0) > 0.0

    val calendar = Calendar.getInstance().apply { time = selectedDate }
    val datePickerDialog = DatePickerDialog(
        context,
        if (ThemeState.isDark.value) android.R.style.Theme_DeviceDefault_Dialog else android.R.style.Theme_DeviceDefault_Light_Dialog,
        { _, year, month, dayOfMonth ->
            val newCal = Calendar.getInstance()
            newCal.set(year, month, dayOfMonth)
            selectedDate = newCal.time
            // Automatically unlink from current wallet if date belongs to a past month
            if (!ExpenseCalculator.isThisMonth(selectedDate)) {
                isLinkedWithBalance = false
            }
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val deadlineCalendar = Calendar.getInstance().apply { selectedDeadline?.let { time = it } }
    val deadlinePickerDialog = DatePickerDialog(
        context,
        if (ThemeState.isDark.value) android.R.style.Theme_DeviceDefault_Dialog else android.R.style.Theme_DeviceDefault_Light_Dialog,
        { _, year, month, dayOfMonth ->
            val newCal = Calendar.getInstance()
            newCal.set(year, month, dayOfMonth)
            selectedDeadline = newCal.time
        },
        deadlineCalendar.get(Calendar.YEAR),
        deadlineCalendar.get(Calendar.MONTH),
        deadlineCalendar.get(Calendar.DAY_OF_MONTH)
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Aesthetic Top Header Bar with Instant Save & Back
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (ThemeState.isDark.value) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "New Transaction",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = textColor
                    )
                    Text(
                        text = if (newType == DebtType.I_OWE) "I Borrowed (Payable ↗)" else "I Lent (Receivable ↙)",
                        fontSize = 11.5.sp,
                        color = if (newType == DebtType.I_OWE) Color(0xFFFF3B30) else Color(0xFF34C759),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Sleek Top-Bar Save Pill Button
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isValid) (if (newType == DebtType.I_OWE) Color(0xFFFF3B30) else Color(0xFF34C759)) else Color.Gray.copy(alpha = 0.2f),
                shadowElevation = if (isValid) 3.dp else 0.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = isValid) {
                        val amount = newAmount.toDoubleOrNull() ?: 0.0
                        if (amount > 0 && newName.isNotBlank()) {
                            onSave(
                                DebtItem(
                                    name = newName.trim(),
                                    amount = amount,
                                    type = newType,
                                    date = selectedDate,
                                    deadline = selectedDeadline,
                                    isLinkedWithBalance = isLinkedWithBalance,
                                    note = newNote.trim()
                                )
                            )
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Save",
                        tint = if (isValid) Color.White else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Save",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isValid) Color.White else Color.Gray
                    )
                }
            }
        }

        HorizontalDivider(color = if (ThemeState.isDark.value) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f))

        // Form Fields (Scrollable)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Segmented Direction Type Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DebtTypeButton(
                    title = "I Borrowed",
                    subtitle = "Payable ↗",
                    color = Color(0xFFFF3B30),
                    isSelected = newType == DebtType.I_OWE,
                    textColor = textColor,
                    modifier = Modifier.weight(1f)
                ) { newType = DebtType.I_OWE }

                DebtTypeButton(
                    title = "I Lent",
                    subtitle = "Receivable ↙",
                    color = Color(0xFF34C759),
                    isSelected = newType == DebtType.THEY_OWE,
                    textColor = textColor,
                    modifier = Modifier.weight(1f)
                ) { newType = DebtType.THEY_OWE }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Person Name & Date Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Person Name *", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedBorderColor = if (newType == DebtType.I_OWE) Color(0xFFFF3B30) else Color(0xFF34C759)
                    )
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (ThemeState.isDark.value) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                    border = BorderStroke(1.dp, if (ThemeState.isDark.value) Color.White.copy(0.08f) else Color.Black.copy(0.08f)),
                    modifier = Modifier
                        .height(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { datePickerDialog.show() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = "Date", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = SimpleDateFormat("dd MMM", Locale.getDefault()).format(selectedDate),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Amount Field
            OutlinedTextField(
                value = newAmount,
                onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) newAmount = it },
                placeholder = { Text("Amount ৳ *", fontSize = 16.sp) },
                leadingIcon = {
                    Text(
                        text = "৳",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = if (newType == DebtType.I_OWE) Color(0xFFFF3B30) else Color(0xFF34C759),
                        modifier = Modifier.padding(start = 14.dp, end = 4.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedBorderColor = if (newType == DebtType.I_OWE) Color(0xFFFF3B30) else Color(0xFF34C759)
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Note / Description Field
            OutlinedTextField(
                value = newNote,
                onValueChange = { newNote = it },
                placeholder = { Text("Note / Purpose (Optional, e.g. Lunch, Rent)", fontSize = 13.5.sp) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    focusedBorderColor = if (newType == DebtType.I_OWE) Color(0xFFFF3B30) else Color(0xFF34C759)
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Optional Deadline Chip Row
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (ThemeState.isDark.value) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                border = BorderStroke(1.dp, if (ThemeState.isDark.value) Color.White.copy(0.08f) else Color.Black.copy(0.08f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { deadlinePickerDialog.show() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = "Deadline",
                            tint = if (selectedDeadline != null) Color(0xFFFF3B30) else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        if (selectedDeadline == null) {
                            Text("Add Due Date (Optional)", color = Color.Gray, fontSize = 13.sp)
                        } else {
                            Text(
                                text = "Due: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(selectedDeadline!!)}",
                                color = if (ThemeState.isDark.value) Color(0xFFFF453A) else Color(0xFFFF3B30),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (selectedDeadline != null) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color.Gray.copy(0.2f))
                                .clickable { selectedDeadline = null },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Link with Main Balance Switch Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (ThemeState.isDark.value) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                border = BorderStroke(1.dp, if (ThemeState.isDark.value) Color.White.copy(0.08f) else Color.Black.copy(0.08f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { isLinkedWithBalance = !isLinkedWithBalance }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (isLinkedWithBalance) Icons.Default.Link else Icons.Default.LinkOff,
                            contentDescription = null,
                            tint = if (isLinkedWithBalance) Color(0xFF34C759) else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Link to Main Balance",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            Text(
                                text = if (!ExpenseCalculator.isThisMonth(selectedDate)) "Past month date (Auto-unlinked from current wallet)"
                                else if (isLinkedWithBalance) "Affects total wallet balance"
                                else "Standalone record only (won't affect wallet)",
                                fontSize = 11.sp,
                                color = if (!ExpenseCalculator.isThisMonth(selectedDate)) Color(0xFFFF9500) else Color.Gray
                            )
                        }
                    }
                    Switch(
                        checked = isLinkedWithBalance,
                        onCheckedChange = { isLinkedWithBalance = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF34C759)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun DebtTypeButton(
    title: String,
    subtitle: String,
    color: Color,
    isSelected: Boolean,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val unselectedBg = if (ThemeState.isDark.value) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) color.copy(alpha = 0.14f) else unselectedBg,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) color else (if (ThemeState.isDark.value) Color.White.copy(0.08f) else Color.Black.copy(0.08f))
        ),
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) color else Color.Gray.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) textColor else Color.Gray
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = if (isSelected) color else Color.Gray,
                    fontWeight = FontWeight.SemiBold
                )
            }
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

@Composable
fun DebtActiveAnalyticsCard(activeDebts: List<DebtItem>, cardColor: Color, textColor: Color) {
    val totalLentActive = remember(activeDebts) { activeDebts.filter { it.type == DebtType.THEY_OWE }.sumOf { it.remainingAmount } }
    val totalBorrowedActive = remember(activeDebts) { activeDebts.filter { it.type == DebtType.I_OWE }.sumOf { it.remainingAmount } }
    val netBalance = remember(totalLentActive, totalBorrowedActive) { totalLentActive - totalBorrowedActive }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "ACTIVE NET POSITION",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val formattedNet = String.format(Locale.US, "%,.0f", Math.abs(netBalance))
                    Text(
                        text = if (netBalance >= 0) "৳$formattedNet" else "-৳$formattedNet",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = if (netBalance >= 0) Color(0xFF34C759) else Color(0xFFFF3B30)
                    )
                    Text(
                        text = if (netBalance >= 0) "Net receivable (others owe you)" else "Net payable (you owe others)",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF34C759)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Receivable", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "৳${String.format(Locale.US, "%,.0f", totalLentActive)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF3B30)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Payable", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "৳${String.format(Locale.US, "%,.0f", totalBorrowedActive)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
fun DebtHistoryAnalyticsCard(completedDebts: List<DebtItem>, cardColor: Color, textColor: Color) {
    val totalCount = completedDebts.size
    val totalLent = remember(completedDebts) { completedDebts.filter { it.type == DebtType.THEY_OWE }.sumOf { it.displayAmount } }
    val totalBorrowed = remember(completedDebts) { completedDebts.filter { it.type == DebtType.I_OWE }.sumOf { it.displayAmount } }
    val totalAmount = remember(totalLent, totalBorrowed) { totalLent + totalBorrowed }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "SETTLED SUMMARY",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "৳${String.format(Locale.US, "%,.0f", totalAmount)}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = ThemeState.primaryAccent.value
                    )
                    Text(
                        text = "Total money settled ($totalCount transactions)",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.Gray.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF34C759))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Lent Settled", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "৳${String.format(Locale.US, "%,.0f", totalLent)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF3B30))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Borrowed Settled", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "৳${String.format(Locale.US, "%,.0f", totalBorrowed)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }
        }
    }
}

fun exportDebtStatementToPdf(context: Context, debts: List<DebtItem>, reportTitle: String) {
    if (debts.isEmpty()) {
        Toast.makeText(context, "No debt records found to export!", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val document = PdfDocument()
        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint()

        val totalReceivable = debts.filter { it.type == DebtType.THEY_OWE }.sumOf { if (it.isPaid || it.isArchived) it.displayAmount else it.remainingAmount }
        val totalPayable = debts.filter { it.type == DebtType.I_OWE }.sumOf { if (it.isPaid || it.isArchived) it.displayAmount else it.remainingAmount }
        val netBalance = totalReceivable - totalPayable

        fun drawHeader() {
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 22f
            paint.color = android.graphics.Color.parseColor("#1C1C1E")
            canvas.drawText("Bachelor's Wallet - $reportTitle", 40f, 60f, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 12f
            paint.color = android.graphics.Color.GRAY
            val dateStr = SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
            canvas.drawText("Generated on: $dateStr | Page $pageNum", 40f, 85f, paint)
        }

        drawHeader()

        // 3 Summary Cards
        // Card 1: Receivable (They Owe)
        paint.color = android.graphics.Color.parseColor("#E8F5E9")
        canvas.drawRoundRect(40f, 105f, 195f, 175f, 12f, 12f, paint)
        paint.color = android.graphics.Color.parseColor("#2E7D32")
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("RECEIVABLE (THEY OWE)", 52f, 128f, paint)
        paint.textSize = 18f
        canvas.drawText("৳${totalReceivable.toInt()}", 52f, 155f, paint)

        // Card 2: Payable (I Owe)
        paint.color = android.graphics.Color.parseColor("#FFEBEE")
        canvas.drawRoundRect(210f, 105f, 365f, 175f, 12f, 12f, paint)
        paint.color = android.graphics.Color.parseColor("#C62828")
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("PAYABLE (I OWE)", 222f, 128f, paint)
        paint.textSize = 18f
        canvas.drawText("৳${totalPayable.toInt()}", 222f, 155f, paint)

        // Card 3: Net Balance
        paint.color = android.graphics.Color.parseColor("#EDE7F6")
        canvas.drawRoundRect(380f, 105f, 555f, 175f, 12f, 12f, paint)
        paint.color = android.graphics.Color.parseColor("#5E35B1")
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("NET BALANCE", 392f, 128f, paint)
        paint.textSize = 18f
        val netPrefix = if (netBalance >= 0) "+৳" else "-৳"
        canvas.drawText("$netPrefix${Math.abs(netBalance).toInt()}", 392f, 155f, paint)

        var currentY = 210f

        fun drawTableHeader() {
            paint.color = android.graphics.Color.parseColor("#F5F5F7")
            canvas.drawRect(40f, currentY, 555f, currentY + 28f, paint)
            paint.color = android.graphics.Color.parseColor("#1C1C1E")
            paint.textSize = 11f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Person / Description", 50f, currentY + 18f, paint)
            canvas.drawText("Type", 210f, currentY + 18f, paint)
            canvas.drawText("Total", 320f, currentY + 18f, paint)
            canvas.drawText("Remaining", 410f, currentY + 18f, paint)
            canvas.drawText("Due / Status", 490f, currentY + 18f, paint)
            currentY += 45f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        drawTableHeader()

        val dateFormat = SimpleDateFormat("dd MMM yy", Locale.getDefault())

        debts.forEach { item ->
            if (currentY > 780f) {
                document.finishPage(page)
                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                drawHeader()
                currentY = 110f
                drawTableHeader()
            }

            val isReceived = item.type == DebtType.THEY_OWE
            paint.textSize = 11f
            paint.color = android.graphics.Color.BLACK
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val nameText = if (item.name.length > 20) item.name.substring(0, 18) + ".." else item.name
            canvas.drawText(nameText, 50f, currentY, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = if (isReceived) android.graphics.Color.parseColor("#2E7D32") else android.graphics.Color.parseColor("#C62828")
            val typeText = if (isReceived) "Receivable (They Owe)" else "Payable (I Owe)"
            canvas.drawText(typeText, 210f, currentY, paint)

            paint.color = android.graphics.Color.DKGRAY
            canvas.drawText("৳${item.displayAmount.toInt()}", 320f, currentY, paint)

            paint.color = if (isReceived) android.graphics.Color.parseColor("#2E7D32") else android.graphics.Color.parseColor("#C62828")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val remAmount = if (item.isPaid || item.isArchived) 0.0 else item.remainingAmount
            canvas.drawText("৳${remAmount.toInt()}", 410f, currentY, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = android.graphics.Color.GRAY
            val dueOrStatus = when {
                item.isArchived -> "Archived"
                item.isPaid -> "Settled"
                item.deadline != null -> dateFormat.format(item.deadline!!)
                else -> dateFormat.format(item.date)
            }
            canvas.drawText(dueOrStatus, 490f, currentY, paint)

            paint.color = android.graphics.Color.parseColor("#EEEEEE")
            canvas.drawLine(40f, currentY + 12f, 555f, currentY + 12f, paint)

            currentY += 28f
        }

        document.finishPage(page)

        // Save PDF file to cache for sharing
        val cleanTitle = reportTitle.replace(" ", "_").replace("&", "and")
        val monthYear = SimpleDateFormat("MMM_yyyy", Locale.US).format(Date())
        val filename = "${monthYear}_${cleanTitle}_BachelorsWallet.pdf"
        val pdfFile = File(context.cacheDir, filename)
        val fos = FileOutputStream(pdfFile)
        document.writeTo(fos)
        fos.close()
        document.close()

        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            pdfFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "Bachelor's Wallet - $reportTitle")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share Debt Statement PDF").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        Toast.makeText(context, "Debt Statement PDF ready to share!", Toast.LENGTH_SHORT).show()

    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to export PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}