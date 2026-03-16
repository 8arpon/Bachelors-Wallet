package com.example.myapplication

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import java.util.*

object ThemeState {
    var isDark = mutableStateOf(false)
}
//Testing Notification Mode
const val IS_DEVELOPMENT_MODE = false

fun Context.getActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }

    // Theme States
    val systemTheme = isSystemInDarkTheme()
    var selectedTheme by remember { mutableStateOf(prefs.getString("theme_mode", "System") ?: "System") }
    var showThemeSheet by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTheme, systemTheme) {
        val newIsDark = when (selectedTheme) {
            "Light" -> false
            "Dark" -> true
            else -> systemTheme
        }
        ThemeState.isDark.value = newIsDark
        prefs.edit().putBoolean("dark_mode", newIsDark).apply()
        prefs.edit().putString("theme_mode", selectedTheme).apply()
    }

    // Notification States
    var isNotifEnabled by remember { mutableStateOf(prefs.getBoolean("notif_enabled", true)) }
    var notificationTime by remember { mutableStateOf(prefs.getString("notif_time", "9:00 PM") ?: "9:00 PM") }

    var smartReminder by remember { mutableStateOf(prefs.getBoolean("pref_smart_reminder", true)) }
    var budgetAlert by remember { mutableStateOf(prefs.getBoolean("pref_budget_alert", true)) }
    var debtAlert by remember { mutableStateOf(prefs.getBoolean("pref_debt_alert", true)) }
    var autoDownload by remember { mutableStateOf(prefs.getBoolean("pref_auto_download", false)) }
    var showNotificationPrefsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val isFirstTimeNotif = prefs.getBoolean("first_time_notif_setup", true)
        if (isFirstTimeNotif && isNotifEnabled) {
            val targetCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 21)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) { add(Calendar.DATE, 1) }
            }
            val intent = Intent(context, NotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            try { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetCal.timeInMillis, pendingIntent)
            } catch (e: SecurityException) { alarmManager.set(AlarmManager.RTC_WAKEUP, targetCal.timeInMillis, pendingIntent) }
            prefs.edit().putBoolean("first_time_notif_setup", false).apply()
            prefs.edit().putString("notif_time", "9:00 PM").apply()
        }
    }

    // Dialog States
    var showAppInfoDialog by remember { mutableStateOf(false) }
    var showDevInfoDialog by remember { mutableStateOf(false) }

    // Security States
    var currentSavedPassword by remember { mutableStateOf(prefs.getString("app_password", "") ?: "") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var oldPassInput by remember { mutableStateOf("") }
    var newPassInput by remember { mutableStateOf("") }
    var passErrorMsg by remember { mutableStateOf("") }
    var isBiometricVerifiedForPassword by remember { mutableStateOf(false) }

    // Danger Zone States
    var showDangerDialog by remember { mutableStateOf(false) }
    var deletePasswordInput by remember { mutableStateOf("") }
    var deleteWrongPassword by remember { mutableStateOf(false) }

    val bgColor = if (ThemeState.isDark.value) Color(0xFF121212) else Color(0xFFF2F2F7)
    val cardColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White
    val textColor = if (ThemeState.isDark.value) Color.White else Color.Black
    val iconColor = if (ThemeState.isDark.value) Color.White else Color.Black

    val calendar = Calendar.getInstance()
    val timePickerDialog = TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(context, "Please Allow 'Alarms & Reminders' for exact time notifications", Toast.LENGTH_LONG).show()
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply { data = android.net.Uri.parse("package:${context.packageName}") }
                context.startActivity(intent)
                return@TimePickerDialog
            }
            val amPm = if (hourOfDay >= 12) "PM" else "AM"
            val hr = if (hourOfDay % 12 == 0) 12 else hourOfDay % 12
            val min = String.format("%02d", minute)
            val formattedTime = "$hr:$min $amPm"

            notificationTime = formattedTime
            prefs.edit().putString("notif_time", formattedTime).apply()

            val targetCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hourOfDay); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) { add(Calendar.DATE, 1) }
            }
            val intent = Intent(context, NotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetCal.timeInMillis, pendingIntent)
                Toast.makeText(context, "Notification set for $formattedTime", Toast.LENGTH_SHORT).show()
            } catch (e: SecurityException) { alarmManager.set(AlarmManager.RTC_WAKEUP, targetCal.timeInMillis, pendingIntent) }
        },
        calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false
    )

    fun authenticateAndWipe() {
        val activity = context.getActivity() ?: return Toast.makeText(context, "Error: Activity not found", Toast.LENGTH_SHORT).show()
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    DataManager.clearAllData(context)
                    Toast.makeText(context, "Data wiped via Biometrics", Toast.LENGTH_LONG).show()
                    showDangerDialog = false
                    navController.navigate("home") { popUpTo(0) }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { Toast.makeText(context, "Auth Error: $errString", Toast.LENGTH_SHORT).show() }
            })
        val promptInfo = BiometricPrompt.PromptInfo.Builder().setTitle("Verify Identity").setSubtitle("Confirm it's you to delete all wallet data").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL).build()
        biometricPrompt.authenticate(promptInfo)
    }

    fun authenticateForPasswordReset() {
        val activity = context.getActivity() ?: return Toast.makeText(context, "Error: Activity not found", Toast.LENGTH_SHORT).show()
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { isBiometricVerifiedForPassword = true; passErrorMsg = "Verified via Biometrics! Enter new password." }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { Toast.makeText(context, "Auth Error: $errString", Toast.LENGTH_SHORT).show() }
            })
        val promptInfo = BiometricPrompt.PromptInfo.Builder().setTitle("Verify Identity").setSubtitle("Confirm it's you to reset the password").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL).build()
        biometricPrompt.authenticate(promptInfo)
    }

    Column(modifier = Modifier.fillMaxSize().background(bgColor).statusBarsPadding().padding(horizontal = 20.dp, vertical = 15.dp).verticalScroll(rememberScrollState())) {

        // --- HEADER ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(45.dp).clip(CircleShape).background(cardColor).clickable { navController.popBackStack() }, contentAlignment = Alignment.Center) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = iconColor) }
            Spacer(modifier = Modifier.width(15.dp))
            Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = textColor)
        }

        Spacer(modifier = Modifier.height(30.dp))

        // --- 1. PREFERENCES SECTION ---
        Text("PREFERENCES", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 8.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = cardColor, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(modifier = Modifier.fillMaxWidth().clickable { showThemeSheet = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF007AFF).copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(20.dp)) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("App Theme", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(selectedTheme, fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
                }

                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.padding(start = 68.dp))

                // HIGHLIGHT: Auto-Download is logically placed in Preferences
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF007AFF).copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Download Report", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
                        Text("Save daily summary as PDF", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(checked = autoDownload, onCheckedChange = { autoDownload = it; prefs.edit().putBoolean("pref_auto_download", it).apply() }, modifier = Modifier.scale(0.8f))
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // --- 2. NOTIFICATIONS SECTION ---
        Text("NOTIFICATIONS", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 8.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = cardColor, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFFF9500).copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFFFF9500), modifier = Modifier.size(20.dp)) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Allow Notifications", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = isNotifEnabled,
                        onCheckedChange = { isEnabled ->
                            if (isEnabled) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    Toast.makeText(context, "Please Allow Notifications from Settings", Toast.LENGTH_LONG).show()
                                    try { val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName) }; context.startActivity(intent)
                                    } catch (e: Exception) { val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = android.net.Uri.parse("package:${context.packageName}") }; context.startActivity(intent) }
                                    isNotifEnabled = false
                                } else { isNotifEnabled = true; prefs.edit().putBoolean("notif_enabled", true).apply() }
                            } else {
                                isNotifEnabled = false; prefs.edit().putBoolean("notif_enabled", false).apply()
                                val intent = Intent(context, NotificationReceiver::class.java)
                                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                                alarmManager.cancel(pendingIntent)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF34C759))
                    )
                }

                AnimatedVisibility(visible = isNotifEnabled) {
                    Column {
                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.padding(start = 68.dp))
                        Row(modifier = Modifier.fillMaxWidth().clickable { timePickerDialog.show() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp)) }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Notification Time", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(notificationTime, fontSize = 16.sp, color = Color.Gray)
                        }

                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.padding(start = 68.dp))
                        Row(modifier = Modifier.fillMaxWidth().clickable { showNotificationPrefsDialog = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF8E2DE2).copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.List, contentDescription = null, tint = Color(0xFF8E2DE2), modifier = Modifier.size(20.dp)) }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Manage Alerts", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
                        }

                        // HIGHLIGHT: Developer Test Button (সহজেই হাইড করার জন্য)
                        // অ্যাপ পাবলিশ করার আগে এটাকে false করে দিবেন!

                        // HIGHLIGHT: Developer Test Button
                        if (IS_DEVELOPMENT_MODE) {
                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.padding(start = 68.dp))
                            Row(modifier = Modifier.fillMaxWidth().clickable {
                                context.sendBroadcast(android.content.Intent(context, NotificationReceiver::class.java))
                                android.widget.Toast.makeText(context, "Checking Smart Notifications...", android.widget.Toast.LENGTH_SHORT).show()
                            }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF34C759).copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(20.dp)) }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Test Smart Notifications (Dev Only)", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // --- 3. SECURITY SECTION ---
        Text("SECURITY", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 8.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = cardColor, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().clickable { oldPassInput = ""; newPassInput = ""; passErrorMsg = ""; isBiometricVerifiedForPassword = false; showPasswordDialog = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF34C759).copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(20.dp)) }
                Spacer(modifier = Modifier.width(16.dp))
                Text(if (currentSavedPassword.isEmpty()) "Set App Password" else "Change App Password", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // --- 4. ABOUT SECTION ---
        Text("ABOUT", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 8.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = cardColor, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(modifier = Modifier.fillMaxWidth().clickable { showAppInfoDialog = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF007AFF).copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(20.dp)) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("About Application", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
                }
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.padding(start = 68.dp))
                Row(modifier = Modifier.fillMaxWidth().clickable { showDevInfoDialog = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFAF52DE).copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFFAF52DE), modifier = Modifier.size(20.dp)) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Developer Info", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // --- 5. DANGER ZONE ---
        Text("DANGER ZONE", fontSize = 13.sp, color = Color.Red, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 8.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = cardColor, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().clickable { showDangerDialog = true }.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Red.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(20.dp)) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Delete All History", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.Red)
                }
            }
        }
        Spacer(modifier = Modifier.height(100.dp))
    }

    // --- ALERTS PREFERENCE DIALOG ---
    if (showNotificationPrefsDialog) {
        Dialog(onDismissRequest = { showNotificationPrefsDialog = false }, properties = DialogProperties(dismissOnClickOutside = true)) {
            Surface(shape = RoundedCornerShape(24.dp), color = cardColor, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Allowed Alerts", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = textColor)
                    Text("Choose what you want to be notified about", fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(20.dp))

                    @Composable
                    fun PrefRow(title: String, desc: String, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
                        Row(modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!isChecked) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                                Text(desc, fontSize = 13.sp, color = Color.Gray)
                            }
                            Checkbox(checked = isChecked, onCheckedChange = onCheckedChange, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF007AFF)))
                        }
                    }

                    PrefRow("Smart Daily Reminder", "Notifies only if you forgot to log today", smartReminder) { smartReminder = it; prefs.edit().putBoolean("pref_smart_reminder", it).apply() }
                    PrefRow("Budget Warning", "Alerts when you cross 80% of income", budgetAlert) { budgetAlert = it; prefs.edit().putBoolean("pref_budget_alert", it).apply() }
                    PrefRow("Debt Reminders", "Reminds about debts older than 15 days", debtAlert) { debtAlert = it; prefs.edit().putBoolean("pref_debt_alert", it).apply() }

                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = { showNotificationPrefsDialog = false }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))) { Text("Done", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    // --- APP THEME BOTTOM SHEET ---
    if (showThemeSheet) {
        ModalBottomSheet(onDismissRequest = { showThemeSheet = false }, containerColor = cardColor, dragHandle = { BottomSheetDefaults.DragHandle() }) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp).padding(bottom = 40.dp)) {
                Text("Appearance", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.padding(bottom = 20.dp))
                val themes = listOf(Triple("System", "Follow device settings", Color.Gray), Triple("Light", "Clean and bright", Color(0xFFFF9500)), Triple("Dark", "Easy on the eyes", Color(0xFF5E5CE6)))
                themes.forEach { (themeName, desc, iconColor) ->
                    val isSelected = selectedTheme == themeName
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(if (isSelected) Color(0xFF007AFF).copy(alpha = 0.1f) else Color.Transparent).clickable { selectedTheme = themeName; showThemeSheet = false }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(iconColor)) }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) { Text(themeName, fontSize = 16.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = textColor); Text(desc, fontSize = 13.sp, color = Color.Gray) }
                        if (isSelected) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(24.dp)) }
                    }
                }
            }
        }
    }

    // --- APP INFO AESTHETIC DIALOG ---
    if (showAppInfoDialog) {
        Dialog(onDismissRequest = { showAppInfoDialog = false }, properties = DialogProperties(dismissOnClickOutside = true)) {
            Surface(shape = RoundedCornerShape(24.dp), color = cardColor, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { showAppInfoDialog = false }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray) }
                    Column(modifier = Modifier.padding(24.dp).padding(top = 10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Color(0xFF007AFF), Color(0xFF00C6FF)))), contentAlignment = Alignment.Center) { Icon(Icons.Default.ShoppingCart, contentDescription = "App Icon", tint = Color.White, modifier = Modifier.size(36.dp)) }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Bachelor's Wallet", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Text("Version 1.0.1", fontSize = 14.sp, color = Color(0xFF007AFF), fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Your smart personal finance companion. Easily track your daily budget, manage debts, and save more with a clean and aesthetic interface.", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center, lineHeight = 20.sp)
                    }
                }
            }
        }
    }

    // --- PASSWORD SET/CHANGE DIALOG ---
    if (showPasswordDialog) {
        Dialog(onDismissRequest = { showPasswordDialog = false; isBiometricVerifiedForPassword = false }, properties = DialogProperties(dismissOnClickOutside = true)) {
            Surface(shape = RoundedCornerShape(24.dp), color = cardColor, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Color(0xFF34C759).copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(30.dp)) }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(if (currentSavedPassword.isEmpty()) "Set New Password" else "Change Password", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor)
                    Text("Secure your budget and settings", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(20.dp))

                    if (currentSavedPassword.isNotEmpty() && !isBiometricVerifiedForPassword) {
                        OutlinedTextField(value = oldPassInput, onValueChange = { oldPassInput = it; passErrorMsg = "" }, label = { Text("Current Password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Forgot Password? Verify with Biometric", color = Color(0xFF007AFF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.End).clickable { authenticateForPasswordReset() }.padding(vertical = 4.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    OutlinedTextField(value = newPassInput, onValueChange = { newPassInput = it; passErrorMsg = "" }, label = { Text("New Password (Min 4 chars)") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor))
                    if (passErrorMsg.isNotEmpty()) { Text(passErrorMsg, color = if (isBiometricVerifiedForPassword) Color(0xFF34C759) else Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp).align(Alignment.Start)) }
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { showPasswordDialog = false; isBiometricVerifiedForPassword = false }, modifier = Modifier.weight(1f)) { Text("Cancel", color = Color.Gray, fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = {
                                if (currentSavedPassword.isNotEmpty() && !isBiometricVerifiedForPassword && oldPassInput != currentSavedPassword) { passErrorMsg = "Current password is wrong"
                                } else if (newPassInput.length < 4) { passErrorMsg = "Password too short"
                                } else { prefs.edit().putString("app_password", newPassInput).apply(); currentSavedPassword = newPassInput; showPasswordDialog = false; isBiometricVerifiedForPassword = false; Toast.makeText(context, "Password Saved!", Toast.LENGTH_SHORT).show() }
                            }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                        ) { Text("Save", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    // --- DANGER ZONE SECURITY DIALOG ---
    if (showDangerDialog) {
        AlertDialog(
            containerColor = cardColor,
            onDismissRequest = { showDangerDialog = false; deleteWrongPassword = false; deletePasswordInput = "" },
            title = { Text("Delete All Data?", color = Color.Red, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("This action cannot be undone. All your expenses, incomes, and debts will be permanently wiped out.", color = textColor, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(15.dp))
                    if (currentSavedPassword.isNotEmpty()) {
                        Text(if (deleteWrongPassword) "Wrong Password!" else "Enter password or use fingerprint.", color = if (deleteWrongPassword) Color.Red else Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(5.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = deletePasswordInput, onValueChange = { deletePasswordInput = it }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true, shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor), modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF007AFF).copy(alpha = 0.1f)).clickable { authenticateAndWipe() }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Lock, contentDescription = "Biometric Unlock", tint = Color(0xFF007AFF)) }
                        }
                    } else {
                        Text("Authentication required to continue.", color = Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(onClick = { authenticateAndWipe() }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White); Spacer(modifier = Modifier.width(8.dp)); Text("Verify with Fingerprint", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Or", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(onClick = { showDangerDialog = false; showPasswordDialog = true }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White); Spacer(modifier = Modifier.width(8.dp)); Text("Set Password to Verify", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                if (currentSavedPassword.isNotEmpty()) {
                    Button(onClick = {
                        if (deletePasswordInput == currentSavedPassword) { DataManager.clearAllData(context); Toast.makeText(context, "All data deleted", Toast.LENGTH_LONG).show(); showDangerDialog = false; deletePasswordInput = ""; navController.navigate("home") { popUpTo(0) }
                        } else { deleteWrongPassword = true }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Wipe Data", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            },
            dismissButton = { TextButton(onClick = { showDangerDialog = false; deleteWrongPassword = false; deletePasswordInput = "" }) { Text("Cancel", color = textColor) } }
        )
    }

    // --- DEVELOPER INFO AESTHETIC DIALOG ---
    if (showDevInfoDialog) {
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        val githubIcon = remember {
            androidx.compose.ui.graphics.vector.ImageVector.Builder(name = "github", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
                path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
                    moveTo(12f, 1.27f); curveTo(5.37f, 1.27f, 0f, 6.64f, 0f, 13.27f); curveTo(0f, 18.58f, 3.44f, 23.08f, 8.21f, 24.67f)
                    curveTo(8.81f, 24.78f, 9.03f, 24.41f, 9.03f, 24.09f); curveTo(9.03f, 23.81f, 9.02f, 23.07f, 9.01f, 22.07f)
                    curveTo(5.67f, 22.79f, 4.97f, 20.46f, 4.97f, 20.46f); curveTo(4.42f, 19.07f, 3.63f, 18.7f, 3.63f, 18.7f)
                    curveTo(2.54f, 17.96f, 3.71f, 17.97f, 3.71f, 17.97f); curveTo(4.91f, 18.06f, 5.54f, 19.21f, 5.54f, 19.21f)
                    curveTo(6.61f, 21.05f, 8.35f, 20.52f, 9.04f, 20.21f); curveTo(9.15f, 19.44f, 9.46f, 18.91f, 9.8f, 18.61f)
                    curveTo(7.14f, 18.31f, 4.34f, 17.28f, 4.34f, 12.68f); curveTo(4.34f, 11.37f, 4.81f, 10.3f, 5.57f, 9.46f)
                    curveTo(5.44f, 9.16f, 5.03f, 7.94f, 5.67f, 6.29f); curveTo(5.67f, 6.29f, 6.68f, 5.97f, 8.97f, 7.52f)
                    curveTo(9.93f, 7.25f, 10.95f, 7.12f, 11.97f, 7.11f); curveTo(12.99f, 7.12f, 14.01f, 7.25f, 14.97f, 7.52f)
                    curveTo(17.26f, 5.97f, 18.26f, 6.29f, 18.26f, 6.29f); curveTo(18.91f, 7.94f, 18.5f, 9.16f, 18.38f, 9.46f)
                    curveTo(19.14f, 10.3f, 19.61f, 11.37f, 19.61f, 12.68f); curveTo(19.61f, 17.29f, 16.8f, 18.31f, 14.13f, 18.6f)
                    curveTo(14.55f, 18.96f, 14.94f, 19.7f, 14.94f, 20.82f); curveTo(14.94f, 22.43f, 14.93f, 23.72f, 14.93f, 24.11f)
                    curveTo(14.93f, 24.43f, 15.14f, 24.81f, 15.75f, 24.69f); curveTo(20.53f, 23.09f, 24f, 18.59f, 24f, 13.27f)
                    curveTo(24f, 6.64f, 18.63f, 1.27f, 12f, 1.27f); close()
                }
            }.build()
        }

        Dialog(onDismissRequest = { showDevInfoDialog = false }, properties = DialogProperties(dismissOnClickOutside = true)) {
            Surface(shape = RoundedCornerShape(30.dp), color = cardColor, shadowElevation = 15.dp, modifier = Modifier.widthIn(max = 300.dp)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { showDevInfoDialog = false }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray) }
                    Column(modifier = Modifier.padding(24.dp).padding(top = 10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(85.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF007AFF), Color(0xFFAF52DE)))).padding(3.dp).clip(CircleShape).background(cardColor).padding(4.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF007AFF), Color(0xFFAF52DE)))), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(45.dp)) }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Arpon Sarker", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = textColor)
                        Surface(color = Color(0xFFAF52DE).copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(top = 4.dp)) { Text("Student", fontSize = 12.sp, color = Color(0xFFAF52DE), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) }
                        Text("Daffodil International University", fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
                        Surface(color = Color(0xFF34C759).copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 12.dp)) { Text(text = "Learning & Building Apps", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp, color = Color(0xFF34C759), fontWeight = FontWeight.Bold) }
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(onClick = { uriHandler.openUri("https://github.com/8arpon") }, shape = CircleShape, color = if (ThemeState.isDark.value) Color.White.copy(0.1f) else Color.Black.copy(0.05f), modifier = Modifier.size(52.dp)) { Box(contentAlignment = Alignment.Center) { Icon(githubIcon, contentDescription = "GitHub", tint = textColor, modifier = Modifier.size(32.dp)) } }
                            Surface(onClick = { uriHandler.openUri("https://www.linkedin.com/in/arpon-sarker/") }, shape = CircleShape, color = Color(0xFF0A66C2).copy(alpha = 0.12f), modifier = Modifier.size(52.dp)) { Box(contentAlignment = Alignment.Center) { Text("in", color = Color(0xFF0A66C2), fontWeight = FontWeight.Black, fontSize = 26.sp, modifier = Modifier.offset(y = (-1).dp)) } }
                        }
                    }
                }
            }
        }
    }
}