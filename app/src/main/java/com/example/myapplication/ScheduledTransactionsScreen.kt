package com.example.myapplication

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledTransactionsScreen() {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var scheduledToDelete by remember { mutableStateOf<ScheduledTransaction?>(null) }
    var triggerConfetti by remember { mutableStateOf(false) }

    val scheduledList by DataManager.getScheduledTransactionsFlow(context).collectAsState(initial = emptyList())

    val isDark = ThemeState.isDark.value
    val bgColor = ThemeState.background.value
    val cardColor = ThemeState.cardBackground.value
    val textColor = if (isDark) Color.White else Color.Black
    val primaryColor = ThemeState.primaryAccent.value

    // Calculate monthly commitment total
    val monthlyCommitmentTotal = remember(scheduledList) {
        scheduledList.filter { it.isActive && it.type == TransactionType.EXPENSE }.sumOf {
            when (it.frequency) {
                "Daily" -> it.amount * 30.0
                "Weekly" -> it.amount * 4.0
                "Monthly" -> it.amount
                else -> it.amount
            }
        }
    }

    Scaffold(
        containerColor = bgColor
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // --- TOP HEADER ---
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Repeating Bills",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.12f))
                            .clickable { showAddDialog = true }
                            .padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Repeating Bill",
                            tint = primaryColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    // --- COMMITMENTS ANALYTICS CARD ---
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = cardColor),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                primaryColor.copy(alpha = 0.12f),
                                                primaryColor.copy(alpha = 0.04f)
                                            )
                                        )
                                    )
                                    .padding(20.dp)
                            ) {
                                Text(
                                    text = "COMMITMENTS SUMMARY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "৳${String.format(Locale.US, "%,.0f", monthlyCommitmentTotal)}",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = primaryColor
                                )
                                Text(
                                    text = "Estimated repeating monthly commitments",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Active Cycles", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${scheduledList.size} commitments",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Auto-Log Status", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF34C759)))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Active", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (scheduledList.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier.size(80.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = primaryColor, modifier = Modifier.size(40.dp))
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No commitments yet",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Schedule recurring bills (like rent, utilities) or regular income here.",
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = { showAddDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Schedule First Bill", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        items(scheduledList, key = { it.id }) { item ->
                            ScheduledCommitmentCard(
                                item = item,
                                cardColor = cardColor,
                                textColor = textColor,
                                onDelete = { scheduledToDelete = item }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }

            if (showAddDialog) {
                Dialog(onDismissRequest = { showAddDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                    Box(modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                        Card(
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = cardColor),
                            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AddScheduledDialogContent(
                                textColor = textColor,
                                onDismiss = { showAddDialog = false },
                                onSave = { newItem ->
                                    DataManager.addScheduledTransaction(context, newItem)
                                    showAddDialog = false
                                    triggerConfetti = true
                                }
                            )
                        }
                    }
                }
            }

            if (scheduledToDelete != null) {
                AlertDialog(
                    onDismissRequest = { scheduledToDelete = null },
                    title = { Text("Delete Repeating Bill", fontWeight = FontWeight.Bold, color = textColor) },
                    text = { Text("Are you sure you want to stop this repeating cycle? Future payments will no longer be logged automatically.", color = textColor) },
                    containerColor = cardColor,
                    confirmButton = {
                        TextButton(onClick = {
                            DataManager.deleteScheduledTransaction(context, scheduledToDelete!!)
                            scheduledToDelete = null
                        }) { Text("Stop Auto-Log", color = Color(0xFFFF3B30), fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = { TextButton(onClick = { scheduledToDelete = null }) { Text("Cancel", color = Color.Gray) } }
                )
            }

            ConfettiExplosion(trigger = triggerConfetti, onFinished = { triggerConfetti = false })
        }
    }
}

@Composable
fun ScheduledCommitmentCard(item: ScheduledTransaction, cardColor: Color, textColor: Color, onDelete: () -> Unit) {
    val primaryColor = ThemeState.primaryAccent.value
    val isExpense = item.type == TransactionType.EXPENSE
    val accentColor = if (isExpense) Color(0xFFFF3B30) else Color(0xFF34C759)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isExpense) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.category,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(Color.Gray))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.frequency,
                        fontSize = 12.sp,
                        color = primaryColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Next: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(item.nextExecutionDate)}",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "৳${String.format(Locale.US, "%,.0f", item.amount)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = accentColor
                )
                Spacer(modifier = Modifier.height(6.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun AddScheduledDialogContent(textColor: Color, onDismiss: () -> Unit, onSave: (ScheduledTransaction) -> Unit) {
    var newTitle by remember { mutableStateOf("") }
    var newAmount by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var selectedCategory by remember { mutableStateOf("Rent") }
    var selectedFrequency by remember { mutableStateOf("Monthly") }
    var selectedDate by remember { mutableStateOf(Date()) }

    val context = LocalContext.current
    val customCategories by DataManager.getCategoriesFlow(context).collectAsState(initial = emptyList())
    val defaultCategories = listOf("Rent", "Utilities", "Salary", "Food", "Transport", "Shopping", "Entertainment", "Others")
    val allCategories = remember(customCategories) { (defaultCategories + customCategories.map { it.name }).distinct() }

    val datePickerDialog = remember {
        val cal = Calendar.getInstance().apply { time = selectedDate }
        DatePickerDialog(context, { _, y, m, d ->
            val selected = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
            selectedDate = selected.time
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
    }

    val isValid = newTitle.isNotBlank() && (newAmount.toDoubleOrNull() ?: 0.0) > 0.0
    val primaryColor = ThemeState.primaryAccent.value

    Column(modifier = Modifier.fillMaxWidth().padding(22.dp)) {
        // --- DIALOG HEADER ---
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "Schedule Repeating Bill",
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textColor,
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.12f))
                    .clickable { onDismiss() }
                    .padding(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- TYPE SELECTOR CARDS (HIGH CONTRAST & BEAUTIFUL) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ScheduledTypeCard(
                title = "Expense",
                subtitle = "Rent, Bills, Mess",
                icon = Icons.AutoMirrored.Filled.TrendingDown,
                color = Color(0xFFFF3B30),
                isSelected = newType == TransactionType.EXPENSE,
                textColor = textColor,
                modifier = Modifier.weight(1f)
            ) {
                newType = TransactionType.EXPENSE
            }

            ScheduledTypeCard(
                title = "Income",
                subtitle = "Salary, Pocket Money",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                color = Color(0xFF34C759),
                isSelected = newType == TransactionType.INCOME,
                textColor = textColor,
                modifier = Modifier.weight(1f)
            ) {
                newType = TransactionType.INCOME
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // --- INPUT FIELDS ---
        OutlinedTextField(
            value = newTitle,
            onValueChange = { newTitle = it },
            placeholder = { Text("Title (e.g. Mess Rent, WiFi Bill)", color = Color.Gray) },
            leadingIcon = {
                Icon(Icons.Default.EditNote, contentDescription = null, tint = Color.Gray)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                focusedBorderColor = primaryColor,
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = newAmount,
            onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) newAmount = it },
            placeholder = { Text("Amount", color = Color.Gray) },
            leadingIcon = {
                Text(
                    text = "৳",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (newType == TransactionType.EXPENSE) Color(0xFFFF3B30) else Color(0xFF34C759),
                    modifier = Modifier.padding(start = 12.dp)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                focusedBorderColor = primaryColor,
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // --- CATEGORY & FREQUENCY SELECTORS (2 COLUMNS) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Category Dropdown
            var showCategoryDrop by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                Surface(
                    onClick = { showCategoryDrop = true },
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Gray.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Category", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            Text(selectedCategory, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }
                DropdownMenu(expanded = showCategoryDrop, onDismissRequest = { showCategoryDrop = false }) {
                    allCategories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat, fontWeight = if (cat == selectedCategory) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { selectedCategory = cat; showCategoryDrop = false }
                        )
                    }
                }
            }

            // Frequency Cycle Dropdown
            var showFreqDrop by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                Surface(
                    onClick = { showFreqDrop = true },
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Gray.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Repeat Cycle", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            Text(selectedFrequency, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                        }
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }
                DropdownMenu(expanded = showFreqDrop, onDismissRequest = { showFreqDrop = false }) {
                    listOf("Daily", "Weekly", "Monthly").forEach { freq ->
                        DropdownMenuItem(
                            text = { Text(freq, fontWeight = if (freq == selectedFrequency) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { selectedFrequency = freq; showFreqDrop = false }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- START / NEXT EXECUTION DATE PICKER ---
        Surface(
            onClick = { datePickerDialog.show() },
            shape = RoundedCornerShape(14.dp),
            color = Color.Gray.copy(alpha = 0.08f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CalendarToday, contentDescription = "Pick Date", tint = primaryColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-log execution starts", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                    Text(
                        text = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(selectedDate),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
                Text("Change", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = primaryColor)
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // --- ACTION BUTTONS ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(48.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
            ) {
                Text("Cancel", color = textColor.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = {
                    val amount = newAmount.toDoubleOrNull() ?: 0.0
                    onSave(
                        ScheduledTransaction(
                            title = newTitle.trim(),
                            amount = amount,
                            category = selectedCategory,
                            type = newType,
                            frequency = selectedFrequency,
                            nextExecutionDate = selectedDate
                        )
                    )
                },
                enabled = isValid,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    disabledContainerColor = primaryColor.copy(alpha = 0.3f)
                ),
                modifier = Modifier.weight(1.5f).height(48.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Bill", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun ScheduledTypeCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    isSelected: Boolean,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgBrush = if (isSelected) {
        Brush.verticalGradient(
            colors = listOf(
                color.copy(alpha = 0.18f),
                color.copy(alpha = 0.08f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.Gray.copy(alpha = 0.06f),
                Color.Gray.copy(alpha = 0.03f)
            )
        )
    }

    val borderColor = if (isSelected) color else Color.Gray.copy(alpha = 0.2f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgBrush)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) color else Color.Gray.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) color else textColor
            )

            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
