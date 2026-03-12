package com.example.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.*

// Notification Data Model
data class AppNotification(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean = false
)

@Composable
fun NotificationScreen(navController: NavController) {
    // Dynamic Colors for Dark Mode Support
    val bgColor = if (ThemeState.isDark.value) Color(0xFF121212) else Color(0xFFF2F2F7)
    val cardColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White
    val textColor = if (ThemeState.isDark.value) Color.White else Color.Black
    val iconColor = if (ThemeState.isDark.value) Color.White else Color.Black

    // Generating Dummy Data for Last 7 Days
    val notifications = remember {
        val currentTime = System.currentTimeMillis()
        val oneDay = 86400000L

        listOf(
            // HIGHLIGHT: Fixed Argument Type Mismatch by specifying parameter names
            AppNotification(title = "Reminder", message = "Don't forget to log your breakfast expense!", timestamp = currentTime - (2 * 3600000L), isRead = false),
            AppNotification(title = "Daily Summary", message = "You spent ৳350 yesterday. Tap to see details.", timestamp = currentTime - oneDay, isRead = true),
            AppNotification(title = "Debt Update", message = "Arpon marked ৳1500 as Paid.", timestamp = currentTime - (oneDay * 2), isRead = true),
            AppNotification(title = "Reminder", message = "Don't forget to log your daily expenses!", timestamp = currentTime - (oneDay * 4), isRead = true),
            AppNotification(title = "Weekly Report", message = "Your weekly expense report is ready to download.", timestamp = currentTime - (oneDay * 6), isRead = true)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
    ) {
        // --- HEADER ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 15.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(45.dp)
                    .clip(CircleShape)
                    .background(cardColor)
                    .clickable { navController.popBackStack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = iconColor)
            }
            Spacer(modifier = Modifier.width(15.dp))
            Text("Notifications", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = textColor)
        }

        // --- NOTIFICATION LIST ---
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Grouping logic based on time
            val today = notifications.filter { isToday(it.timestamp) }
            val yesterday = notifications.filter { isYesterday(it.timestamp) }
            val older = notifications.filter { !isToday(it.timestamp) && !isYesterday(it.timestamp) }

            if (today.isNotEmpty()) {
                item { SectionHeader("Today", textColor) }
                items(today) { notif -> NotificationCard(notif, cardColor, textColor) }
            }

            if (yesterday.isNotEmpty()) {
                item { SectionHeader("Yesterday", textColor) }
                items(yesterday) { notif -> NotificationCard(notif, cardColor, textColor) }
            }

            if (older.isNotEmpty()) {
                item { SectionHeader("Last 7 Days", textColor) }
                items(older) { notif -> NotificationCard(notif, cardColor, textColor) }
            }

            item { Spacer(modifier = Modifier.height(50.dp)) } // Bottom Padding
        }
    }
}

@Composable
fun SectionHeader(title: String, textColor: Color) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Gray,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 5.dp)
    )
}

@Composable
fun NotificationCard(notification: AppNotification, cardColor: Color, textColor: Color) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardColor,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Icon (Aesthetic Blue Circle)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF007AFF).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFF007AFF))
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text Content
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(notification.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor)
                    // Unread Dot Indicator
                    if (!notification.isRead) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF3B30)))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(notification.message, fontSize = 14.sp, color = Color.Gray, lineHeight = 20.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(formatTimestamp(notification.timestamp), fontSize = 12.sp, color = Color.LightGray, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// Helper functions for Date formatting
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
    val format = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    return format.format(Date(timestamp))
}