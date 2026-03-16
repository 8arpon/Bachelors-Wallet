package com.example.myapplication

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.*

data class AppNotification(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val timestamp: Long,
    val type: String = "REMINDER",
    val isRead: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(navController: NavController) {
    val context = LocalContext.current

    // HIGHLIGHT: Fetching Auto Download Status
    val prefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    val isAutoDownloadOn = remember { prefs.getBoolean("pref_auto_download", false) }

    val bgColor = if (ThemeState.isDark.value) Color(0xFF121212) else Color(0xFFF8F9FA)
    val cardColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White
    val textColor = if (ThemeState.isDark.value) Color.White else Color.Black
    val iconColor = if (ThemeState.isDark.value) Color.White else Color.Black

    var notifications by remember { mutableStateOf(DataManager.getNotifications(context)) }

    var showClearDialog by remember { mutableStateOf(false) }
    var deleteDebtsAlso by remember { mutableStateOf(false) }

    var expandedFilter by remember { mutableStateOf(false) }
    var currentFilter by remember { mutableStateOf("ALL") }

    var selectedNotif by remember { mutableStateOf<AppNotification?>(null) }

    LaunchedEffect(Unit) {
        if (notifications.any { !it.isRead }) {
            kotlinx.coroutines.delay(1500)
            DataManager.markAllNotificationsAsRead(context)
            notifications = DataManager.getNotifications(context)
        }
    }

    val filteredList = if (currentFilter == "ALL") notifications else notifications.filter { it.type == currentFilter }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(bgColor).statusBarsPadding()) {
            // --- HEADER ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp)
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(cardColor).clickable { navController.popBackStack() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = iconColor, modifier = Modifier.size(20.dp)) }
                Spacer(modifier = Modifier.width(15.dp))
                Text(if (currentFilter == "ALL") "Notifications" else "$currentFilter", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textColor)

                Spacer(modifier = Modifier.weight(1f))

                Box {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.1f)).clickable { expandedFilter = true },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = iconColor, modifier = Modifier.size(20.dp)) }
                    DropdownMenu(expanded = expandedFilter, onDismissRequest = { expandedFilter = false }, modifier = Modifier.background(cardColor)) {
                        DropdownMenuItem(text = { Text("All Notifications", color = textColor) }, onClick = { currentFilter = "ALL"; expandedFilter = false })
                        DropdownMenuItem(text = { Text("Debts", color = textColor) }, onClick = { currentFilter = "DEBT"; expandedFilter = false })
                        DropdownMenuItem(text = { Text("Reports", color = textColor) }, onClick = { currentFilter = "REPORT"; expandedFilter = false })
                        DropdownMenuItem(text = { Text("Alerts", color = textColor) }, onClick = { currentFilter = "ALERT"; expandedFilter = false })
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                if (notifications.isNotEmpty()) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Red.copy(alpha = 0.1f)).clickable { showClearDialog = true },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = Color.Red, modifier = Modifier.size(20.dp)) }
                }
            }

            if (showClearDialog) {
                AlertDialog(
                    containerColor = cardColor,
                    onDismissRequest = { showClearDialog = false },
                    title = { Text("Clear Notifications", fontWeight = FontWeight.Bold, color = textColor) },
                    text = {
                        Column {
                            Text("Are you sure you want to delete all notifications?", color = textColor, fontSize = 15.sp)
                            Spacer(modifier = Modifier.height(15.dp))
                            Row(modifier = Modifier.fillMaxWidth().clickable { deleteDebtsAlso = !deleteDebtsAlso }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = deleteDebtsAlso, onCheckedChange = { deleteDebtsAlso = it }, colors = CheckboxDefaults.colors(checkedColor = Color.Red))
                                Text("Delete Debt Reminders too", fontSize = 14.sp, color = textColor)
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { DataManager.clearAllNotifications(context, keepDebts = !deleteDebtsAlso); notifications = DataManager.getNotifications(context); showClearDialog = false; deleteDebtsAlso = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Clear All", color = Color.White, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel", color = Color.Gray) } }
                )
            }

            if (filteredList.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize().padding(bottom = 50.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(Color(0xFF007AFF).copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(40.dp)) }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("All Caught Up!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor)
                    Text("No notifications found.", fontSize = 14.sp, color = Color.Gray)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    val today = filteredList.filter { isToday(it.timestamp) }
                    val yesterday = filteredList.filter { isYesterday(it.timestamp) }
                    val older = filteredList.filter { !isToday(it.timestamp) && !isYesterday(it.timestamp) }

                    // HIGHLIGHT: Passing isAutoDownloadOn to SwipeableNotificationCard
                    if (today.isNotEmpty()) {
                        item { SectionHeader("Today") }
                        items(today, key = { it.id }) { notif -> SwipeableNotificationCard(notif, cardColor, textColor, isAutoDownloadOn, { selectedNotif = it }) { d -> DataManager.deleteNotification(context, d.id); notifications = DataManager.getNotifications(context) } }
                    }
                    if (yesterday.isNotEmpty()) {
                        item { SectionHeader("Yesterday") }
                        items(yesterday, key = { it.id }) { notif -> SwipeableNotificationCard(notif, cardColor, textColor, isAutoDownloadOn, { selectedNotif = it }) { d -> DataManager.deleteNotification(context, d.id); notifications = DataManager.getNotifications(context) } }
                    }
                    if (older.isNotEmpty()) {
                        item { SectionHeader("Earlier") }
                        items(older, key = { it.id }) { notif -> SwipeableNotificationCard(notif, cardColor, textColor, isAutoDownloadOn, { selectedNotif = it }) { d -> DataManager.deleteNotification(context, d.id); notifications = DataManager.getNotifications(context) } }
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }

        // --- AESTHETIC POP-UP REDESIGN ---
        selectedNotif?.let { notif ->
            Dialog(
                onDismissRequest = { selectedNotif = null },
                properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = cardColor,
                    shadowElevation = 12.dp,
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        IconButton(
                            onClick = { selectedNotif = null },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        ) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray) }

                        Column(modifier = Modifier.padding(24.dp).padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {

                            val iconTint = when (notif.type) {
                                "ALERT" -> Color(0xFFFF3B30); "DEBT" -> Color(0xFFFF9500)
                                "REPORT" -> Color(0xFF8E2DE2); "SUCCESS" -> Color(0xFF34C759)
                                else -> Color(0xFF007AFF)
                            }
                            val iconImage = when (notif.type) {
                                "ALERT" -> Icons.Default.Warning; "DEBT" -> Icons.Default.MonetizationOn
                                "REPORT" -> Icons.Default.Assessment; "SUCCESS" -> Icons.Default.CheckCircle
                                else -> Icons.Default.Notifications
                            }

                            Box(modifier = Modifier.size(65.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Icon(iconImage, contentDescription = null, tint = iconTint, modifier = Modifier.size(32.dp))
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            Text(notif.title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = textColor, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(notif.message, fontSize = 15.sp, color = Color.Gray, textAlign = TextAlign.Center, lineHeight = 22.sp)
                            Spacer(modifier = Modifier.height(24.dp))

                            val buttonText = when (notif.type) {
                                "DEBT" -> "Go to Debt Manager"
                                "ALERT" -> "Check Budget"
                                else -> "Go to Home"
                            }

                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {

                                // HIGHLIGHT: Dynamic Pop-Up content for Reports based on Auto-Download
                                if (notif.type == "REPORT") {
                                    if (isAutoDownloadOn) {
                                        Surface(color = Color(0xFF34C759).copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
                                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Auto-downloaded at ${formatTimestamp(notif.timestamp)}", color = Color(0xFF34C759), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    } else {
                                        Surface(color = Color.Gray.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
                                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Auto-download is OFF", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Button(
                                        onClick = {
                                            exportAestheticPDF(context, notif.message, notif.timestamp)
                                            Toast.makeText(context, "Premium PDF Downloaded!", Toast.LENGTH_SHORT).show()
                                            selectedNotif = null
                                        },
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = iconTint),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (isAutoDownloadOn) "Download Again" else "Download PDF Report", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        selectedNotif = null
                                        when (notif.type) {
                                            "DEBT" -> navController.navigate("debt") { launchSingleTop = true }
                                            "ALERT" -> navController.navigate("budget") { launchSingleTop = true }
                                            else -> navController.navigate("home") { launchSingleTop = true }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    border = BorderStroke(1.5.dp, if (notif.type == "REPORT") Color.Gray.copy(alpha=0.3f) else iconTint),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text(buttonText, color = if (notif.type == "REPORT") textColor else iconTint, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableNotificationCard(
    notification: AppNotification,
    cardColor: Color,
    textColor: Color,
    isAutoDownloadOn: Boolean, // HIGHLIGHT: Received state
    onClick: (AppNotification) -> Unit,
    onDelete: (AppNotification) -> Unit
) {
    val context = LocalContext.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.StartToEnd || it == SwipeToDismissBoxValue.EndToStart) { onDelete(notification); true } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            Box(
                modifier = Modifier.fillMaxSize().padding(vertical = 1.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFF3B30)).padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(24.dp)) }
        },
        content = {
            val unreadHighlight = if (!notification.isRead) Color(0xFF007AFF).copy(alpha = 0.05f) else Color.Transparent
            Surface(
                shape = RoundedCornerShape(12.dp), color = cardColor, shadowElevation = 0.dp, border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth().clickable { onClick(notification) }
            ) {
                Row(modifier = Modifier.background(unreadHighlight).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {

                    val iconTint = when (notification.type) {
                        "ALERT" -> Color(0xFFFF3B30); "DEBT" -> Color(0xFFFF9500)
                        "REPORT" -> Color(0xFF8E2DE2); "SUCCESS" -> Color(0xFF34C759)
                        else -> Color(0xFF007AFF)
                    }
                    val iconImage = when (notification.type) {
                        "ALERT" -> Icons.Default.Warning; "DEBT" -> Icons.Default.MonetizationOn
                        "REPORT" -> Icons.Default.Assessment; "SUCCESS" -> Icons.Default.CheckCircle
                        else -> Icons.Default.Notifications
                    }

                    Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(iconTint.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        Icon(iconImage, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(notification.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = textColor, modifier = Modifier.weight(1f))
                            if (!notification.isRead) Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF007AFF)))
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(notification.message, fontSize = 12.sp, color = Color.Gray, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(formatTimestamp(notification.timestamp), fontSize = 10.sp, color = Color.LightGray, fontWeight = FontWeight.Medium)
                    }

                    // HIGHLIGHT: Hide Icon if Auto-Download is ON
                    if (notification.type == "REPORT" && !isAutoDownloadOn) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF8E2DE2).copy(alpha = 0.1f)).clickable {
                                exportAestheticPDF(context, notification.message, notification.timestamp)
                                Toast.makeText(context, "Premium PDF Downloaded!", Toast.LENGTH_SHORT).show()
                            },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Download, contentDescription = "Download Report", tint = Color(0xFF8E2DE2), modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        }
    )
}

// HIGHLIGHT: Bank Statement Style Professional PDF Generator
fun exportAestheticPDF(context: android.content.Context, content: String, timestamp: Long) {
    try {
        val dateStr = SimpleDateFormat("dd_MMM_yyyy", Locale.getDefault()).format(Date(timestamp))
        val fileName = "Wallet_Statement_$dateStr.pdf"

        val pdfDocument = android.graphics.pdf.PdfDocument()
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val width = pageInfo.pageWidth.toFloat()
        val height = pageInfo.pageHeight.toFloat()

        val headerColor = android.graphics.Color.rgb(44, 62, 80)
        val headerPaint = android.graphics.Paint().apply { color = headerColor }
        canvas.drawRect(0f, 0f, width, 150f, headerPaint)

        val titlePaint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 28f; isFakeBoldText = true; textAlign = android.graphics.Paint.Align.CENTER }
        val subtitlePaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(200, 200, 200); textSize = 14f; textAlign = android.graphics.Paint.Align.CENTER }

        canvas.drawText("BACHELOR'S WALLET", width / 2f, 65f, titlePaint)
        canvas.drawText("Daily Transaction Statement", width / 2f, 95f, subtitlePaint)
        val displayDate = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date(timestamp))
        canvas.drawText("Date: $displayDate", width / 2f, 120f, subtitlePaint)

        var yPos = 200f
        val tableHeaderPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(100, 100, 100); textSize = 14f; isFakeBoldText = true }
        canvas.drawText("CATEGORY", 70f, yPos, tableHeaderPaint)
        tableHeaderPaint.textAlign = android.graphics.Paint.Align.RIGHT
        canvas.drawText("AMOUNT (BDT)", width - 70f, yPos, tableHeaderPaint)

        val linePaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(200, 200, 200); strokeWidth = 2f }
        canvas.drawLine(60f, yPos + 15f, width - 60f, yPos + 15f, linePaint)
        yPos += 45f

        val textPaintLeft = android.graphics.Paint().apply { color = android.graphics.Color.rgb(40, 40, 40); textSize = 16f; textAlign = android.graphics.Paint.Align.LEFT }
        val textPaintRight = android.graphics.Paint().apply { color = android.graphics.Color.rgb(40, 40, 40); textSize = 16f; textAlign = android.graphics.Paint.Align.RIGHT; isFakeBoldText = true }
        val bgPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(245, 245, 245) }

        val lines = content.split("\n")
        var rowIndex = 0

        for (line in lines) {
            if (line.isBlank()) continue
            val parts = line.split(":")
            if (parts.size >= 2) {
                val label = parts[0].trim()
                val valueStr = parts.subList(1, parts.size).joinToString(":").trim()
                val cleanString = valueStr.replace(Regex("[^0-9.]"), "")
                val valueNum = cleanString.toDoubleOrNull() ?: 0.0

                if (valueNum == 0.0 && !label.contains("Total", ignoreCase = true)) continue

                if (rowIndex % 2 == 0 && !label.contains("Total", ignoreCase = true)) {
                    canvas.drawRect(60f, yPos - 25f, width - 60f, yPos + 15f, bgPaint)
                }

                if (label.contains("Total Spent", ignoreCase = true)) {
                    yPos += 20f
                    canvas.drawLine(60f, yPos - 35f, width - 60f, yPos - 35f, linePaint)
                    textPaintLeft.isFakeBoldText = true; textPaintLeft.textSize = 18f
                    textPaintRight.isFakeBoldText = true; textPaintRight.textSize = 20f
                    textPaintRight.color = android.graphics.Color.rgb(211, 47, 47) // Red
                } else if (label.contains("Income", ignoreCase = true)) {
                    textPaintRight.color = android.graphics.Color.rgb(56, 142, 60) // Green
                } else {
                    textPaintLeft.isFakeBoldText = false; textPaintLeft.textSize = 16f
                    textPaintRight.isFakeBoldText = false; textPaintRight.textSize = 16f
                    textPaintRight.color = android.graphics.Color.rgb(40, 40, 40)
                }

                canvas.drawText(label.uppercase(), 70f, yPos, textPaintLeft)
                canvas.drawText(valueStr, width - 70f, yPos, textPaintRight)
                yPos += 40f
                rowIndex++
            } else {
                canvas.drawText(line, 70f, yPos, textPaintLeft)
                yPos += 40f
            }
        }

        val footerPaint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(150, 150, 150); textSize = 12f; textAlign = android.graphics.Paint.Align.CENTER }
        canvas.drawText("App Generated Document - Bachelor's Wallet", width / 2f, height - 60f, footerPaint)

        pdfDocument.finishPage(page)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let { resolver.openOutputStream(it)?.use { os -> pdfDocument.writeTo(os) } }
        } else {
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(dir, fileName)
            java.io.FileOutputStream(file).use { pdfDocument.writeTo(it) }
        }
        pdfDocument.close()
    } catch (e: Exception) { e.printStackTrace() }
}

fun isToday(timestamp: Long): Boolean {
    val cal1 = Calendar.getInstance()
    val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

fun isYesterday(timestamp: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

fun formatTimestamp(timestamp: Long): String {
    val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return format.format(Date(timestamp))
}