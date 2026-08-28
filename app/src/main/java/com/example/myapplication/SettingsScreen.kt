package com.example.myapplication

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val prefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }

    val isLoggedIn = CloudSyncManager.isUserLoggedIn()
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfoFound by remember { mutableStateOf<UpdateInfo?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val currentAppVersion = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.1.0"
        } catch (e: Exception) {
            "2.1.0"
        }
    }

    var includeDebtInBalance by remember { mutableStateOf(prefs.getBoolean("pref_include_debt_in_balance", true)) }
    var showDebtToggleDialog by remember { mutableStateOf(false) }
    var pendingDebtToggleState by remember { mutableStateOf(true) }

    val systemTheme = isSystemInDarkTheme()
    var selectedTheme by remember { mutableStateOf(prefs.getString("theme_mode", "System") ?: "System") }
    var showThemeSheet by remember { mutableStateOf(false) }

    var isFoodCentric by remember { mutableStateOf(prefs.getBoolean("pref_food_centric", true)) }
    var isMessCentric by remember { mutableStateOf(prefs.getBoolean("pref_mess_centric_mode", false)) }
    var linkMessToWallet by remember { mutableStateOf(prefs.getBoolean("pref_link_mess_to_wallet", true)) }
    var showMessInNavBar by remember { mutableStateOf(prefs.getBoolean("pref_show_mess_in_navbar", true)) }
    var autoCloudBackup by remember { mutableStateOf(prefs.getBoolean("pref_auto_cloud_backup", true)) }

    // 🌟 HIGHLIGHT: NEW AI REMINDER STATES
    var aiEmailReminder by remember { mutableStateOf(prefs.getBoolean("pref_ai_email", true)) }
    var aiPushReminder by remember { mutableStateOf(prefs.getBoolean("pref_ai_push", true)) }

    var isNotifEnabled by remember { mutableStateOf(prefs.getBoolean("notif_enabled", true)) }
    var notificationTime by remember { mutableStateOf(prefs.getString("notif_time", "9:00 PM") ?: "9:00 PM") }
    var smartReminder by remember { mutableStateOf(prefs.getBoolean("pref_smart_reminder", true)) }
    var budgetAlert by remember { mutableStateOf(prefs.getBoolean("pref_budget_alert", true)) }
    var debtAlert by remember { mutableStateOf(prefs.getBoolean("pref_debt_alert", true)) }
    var autoDownload by remember { mutableStateOf(prefs.getBoolean("pref_auto_download", false)) }
    var showNotificationPrefsDialog by remember { mutableStateOf(false) }

    var showAppInfoDialog by remember { mutableStateOf(false) }
    var showDevInfoDialog by remember { mutableStateOf(false) }

    var currentSavedPassword by remember { mutableStateOf(prefs.getString("app_password", "") ?: "") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var oldPassInput by remember { mutableStateOf("") }
    var newPassInput by remember { mutableStateOf("") }
    var passErrorMsg by remember { mutableStateOf("") }
    var isBiometricVerifiedForPassword by remember { mutableStateOf(false) }

    var showDangerDialog by remember { mutableStateOf(false) }
    var showResetMessDialog by remember { mutableStateOf(false) }
    var deletePasswordInput by remember { mutableStateOf("") }
    var deleteWrongPassword by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }

    val customCategories by DataManager.getCategoriesFlow(context).collectAsState(initial = emptyList())

    val isDark = ThemeState.isDark.value
    val bgColor = ThemeState.background.value
    val cardColor = ThemeState.cardBackground.value
    val textColor = if (isDark) Color.White else Color.Black
    val primaryColor = ThemeState.primaryAccent.value
    val successColor = Color(0xFF34C759)
    val warningColor = Color(0xFFFF9500)
    val dangerColor = Color(0xFFFF3B30)
    val purpleColor = Color(0xFFAF52DE)

    val calendar = Calendar.getInstance()
    val timePickerDialog = TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(context, "Please Allow 'Alarms & Reminders' for exact time notifications", Toast.LENGTH_LONG).show()
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply { data = Uri.parse("package:${context.packageName}") }
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
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
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
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
            })
        val promptInfo = BiometricPrompt.PromptInfo.Builder().setTitle("Verify Identity").setSubtitle("Confirm it's you to reset the password").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL).build()
        biometricPrompt.authenticate(promptInfo)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, fontSize = 28.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor, titleContentColor = textColor),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = bgColor
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(paddingValues).padding(horizontal = 20.dp)) {

            Spacer(modifier = Modifier.height(10.dp))

            // 👑 BACHELORS WALLET PRO BANNER
            val isPro = PremiumManager.isProUser.value
            val currentPlan = PremiumManager.currentPlanTitle.value

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isPro) Color(0xFF2A1B54) else Color(0xFF1C1A29),
                border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFF7B61FF)))),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate("subscription") },
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFD700).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.WorkspacePremium, contentDescription = "PRO", tint = Color(0xFFFFD700), modifier = Modifier.size(26.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isPro) "Bachelors Wallet PRO 👑" else "Upgrade to PRO & Coupons",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFFD700)
                            )
                            Text(
                                text = if (isPro) "Active: $currentPlan" else "Exclusive Themes, Auto Cloud & Mess Pro",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFFFD700))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ProfileAestheticCard(title = "App Preferences", cardColor = cardColor, isDark = isDark) {
                SyncOptionItem(icon = Icons.Outlined.Palette, title = "App Theme", subtitle = selectedTheme, iconColor = primaryColor, textColor = textColor) { showThemeSheet = true }
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = Color.Gray.copy(alpha = 0.15f))
                SyncOptionItem(
                    icon = Icons.Outlined.Category,
                    title = "Manage Categories",
                    subtitle = if (customCategories.isEmpty()) "Create custom categories with emojis" else "${customCategories.size} custom categories created",
                    iconColor = primaryColor,
                    textColor = textColor
                ) { showCategorySheet = true }
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = Color.Gray.copy(alpha = 0.15f))
                SwitchOptionItem(
                    icon = Icons.Outlined.RestaurantMenu,
                    title = "Food-Centric Budget",
                    subtitle = "Groups meals into Food budget vs separate categories",
                    iconColor = Color(0xFFFF9500),
                    textColor = textColor,
                    isChecked = isFoodCentric
                ) {
                    isFoodCentric = it; prefs.edit().putBoolean("pref_food_centric", it).apply()
                }
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = Color.Gray.copy(alpha = 0.15f))
                SwitchOptionItem(icon = Icons.Outlined.Download, title = "Auto-Download Report", subtitle = "Save daily summary as PDF", iconColor = primaryColor, textColor = textColor, isChecked = autoDownload) { autoDownload = it; prefs.edit().putBoolean("pref_auto_download", it).apply() }
            }
            Spacer(modifier = Modifier.height(20.dp))

            // 🏠 UNIFIED MESS MANAGER CONFIGURATION CARD
            ProfileAestheticCard(title = "Mess Manager 🏠", cardColor = cardColor, isDark = isDark) {
                SwitchOptionItem(
                    icon = Icons.Outlined.Groups,
                    title = "Show Mess in Bottom Bar",
                    subtitle = "Quick access to Mess Manager in bottom navigation",
                    iconColor = purpleColor,
                    textColor = textColor,
                    isChecked = showMessInNavBar
                ) {
                    showMessInNavBar = it
                    prefs.edit().putBoolean("pref_show_mess_in_navbar", it).apply()
                }
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = Color.Gray.copy(alpha = 0.15f))
                SwitchOptionItem(
                    icon = Icons.Outlined.HomeWork,
                    title = "Mess-Centric Lifestyle 🍲",
                    subtitle = "Hide separate meals (Breakfast/Lunch/Dinner/Food) and manage food under Mess",
                    iconColor = primaryColor,
                    textColor = textColor,
                    isChecked = isMessCentric
                ) {
                    isMessCentric = it
                    prefs.edit().putBoolean("pref_mess_centric_mode", it).apply()
                }
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = Color.Gray.copy(alpha = 0.15f))
                SwitchOptionItem(
                    icon = Icons.Outlined.Link,
                    title = "Sync Mess with Wallet Balance 💳",
                    subtitle = "Auto-deduct personal Mess Deposits & personal Bazaar from main wallet balance",
                    iconColor = successColor,
                    textColor = textColor,
                    isChecked = linkMessToWallet
                ) {
                    linkMessToWallet = it
                    prefs.edit().putBoolean("pref_link_mess_to_wallet", it).apply()
                }
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = Color.Gray.copy(alpha = 0.15f))
                SyncOptionItem(
                    icon = Icons.Outlined.RestartAlt,
                    title = "Reset Mess Data",
                    subtitle = "Clear all roommates, meals, bazaar & deposits",
                    iconColor = dangerColor,
                    textColor = dangerColor
                ) {
                    showResetMessDialog = true
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            // 🌟 HIGHLIGHT: NEW AI ROASTING & REMINDERS SECTION
            if (isLoggedIn) {
                ProfileAestheticCard(title = "AI Roasting & Reminders \uD83E\uDD16", cardColor = cardColor, isDark = isDark) {
                    SwitchOptionItem(icon = Icons.Outlined.Email, title = "Email Reminders", subtitle = "Receive funny daily reminders via Email", iconColor = warningColor, textColor = textColor, isChecked = aiEmailReminder) {
                        aiEmailReminder = it; prefs.edit().putBoolean("pref_ai_email", it).apply()
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = Color.Gray.copy(alpha = 0.15f))
                    SwitchOptionItem(icon = Icons.Outlined.NotificationsActive, title = "Push Notifications", subtitle = "Get AI roasting directly on your phone", iconColor = successColor, textColor = textColor, isChecked = aiPushReminder) {
                        aiPushReminder = it; prefs.edit().putBoolean("pref_ai_push", it).apply()
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            ProfileAestheticCard(title = "Finance & Alerts", cardColor = cardColor, isDark = isDark) {
                SwitchOptionItem(icon = Icons.Outlined.AccountBalanceWallet, title = "Link Debt to Balance", subtitle = "Include debts in total balance", iconColor = successColor, textColor = textColor, isChecked = includeDebtInBalance) { pendingDebtToggleState = it; showDebtToggleDialog = true }
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = Color.Gray.copy(alpha = 0.15f))
                SwitchOptionItem(icon = Icons.Outlined.Notifications, title = "Allow Notifications", subtitle = "Daily reminders and alerts", iconColor = warningColor, textColor = textColor, isChecked = isNotifEnabled) {
                    if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName) }
                        context.startActivity(intent); isNotifEnabled = false
                    } else { isNotifEnabled = it; prefs.edit().putBoolean("notif_enabled", it).apply() }
                }
                if (isNotifEnabled) {
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = Color.Gray.copy(alpha = 0.15f))
                    SyncOptionItem(icon = Icons.Outlined.Schedule, title = "Notification Time", subtitle = notificationTime, iconColor = Color.Gray, textColor = textColor) { timePickerDialog.show() }
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = Color.Gray.copy(alpha = 0.15f))
                    SyncOptionItem(icon = Icons.Outlined.Tune, title = "Manage Smart Alerts", subtitle = "Budget limits & Debt alerts", iconColor = purpleColor, textColor = textColor) { showNotificationPrefsDialog = true }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            if (isLoggedIn) {
                ProfileAestheticCard(title = "Cloud Synchronization", cardColor = cardColor, isDark = isDark) {
                    SwitchOptionItem(
                        icon = Icons.Outlined.CloudSync,
                        title = "Automatic Cloud Backup",
                        subtitle = "Auto-sync whenever expense is added",
                        iconColor = successColor,
                        textColor = textColor,
                        isChecked = autoCloudBackup
                    ) {
                        if (it && !PremiumManager.isFeatureAccessible("cloud_backup")) {
                            Toast.makeText(context, "Automatic Cloud Backup is a PRO feature 👑", Toast.LENGTH_SHORT).show()
                            navController.navigate("subscription")
                        } else {
                            autoCloudBackup = it
                            prefs.edit().putBoolean("pref_auto_cloud_backup", it).apply()
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = Color.Gray.copy(alpha = 0.15f))
                    SyncActionButton(icon = Icons.Outlined.CloudUpload, title = "Backup Now", subtitle = "Force save expenses to cloud", color = successColor, isLoading = isBackingUp) {
                        isBackingUp = true; CloudSyncManager.backupToCloud(context) { _, msg -> isBackingUp = false; Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = Color.Gray.copy(alpha = 0.15f))
                    SyncActionButton(icon = Icons.Outlined.CloudDownload, title = "Restore Data", subtitle = "Sync data back to this phone", color = primaryColor, isLoading = isRestoring) {
                        isRestoring = true; CloudSyncManager.restoreFromCloud(context) { _, msg -> isRestoring = false; Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            ProfileAestheticCard(title = "Security & Tools", cardColor = cardColor, isDark = isDark) {
                SyncOptionItem(icon = Icons.Outlined.Lock, title = if (currentSavedPassword.isEmpty()) "Set App Password" else "Change App Password", subtitle = "Secure your budget app", iconColor = successColor, textColor = textColor) { oldPassInput = ""; newPassInput = ""; passErrorMsg = ""; isBiometricVerifiedForPassword = false; showPasswordDialog = true }
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = Color.Gray.copy(alpha = 0.15f))
                SyncOptionItem(icon = Icons.Outlined.Delete, title = "Delete All History", subtitle = "Permanently delete history", iconColor = dangerColor, textColor = dangerColor) { showDangerDialog = true }
            }
            Spacer(modifier = Modifier.height(20.dp))

            ProfileAestheticCard(title = "About", cardColor = cardColor, isDark = isDark) {
                SyncActionButton(
                    icon = Icons.Outlined.SystemUpdate,
                    title = "Check for Updates",
                    subtitle = "Check GitHub for the latest version",
                    color = primaryColor,
                    isLoading = isCheckingUpdate
                ) {
                    isCheckingUpdate = true
                    coroutineScope.launch {
                        try {
                            val update = AppUpdateManager.checkForUpdates(context)
                            isCheckingUpdate = false
                            if (update != null && update.hasUpdate) {
                                updateInfoFound = update
                            } else {
                                Toast.makeText(context, "🎉 You're on the latest version (v$currentAppVersion)!", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            isCheckingUpdate = false
                            Toast.makeText(context, "Failed to check for updates", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = Color.Gray.copy(alpha = 0.15f))
                SyncOptionItem(icon = Icons.Outlined.Info, title = "About Application", subtitle = "Version $currentAppVersion", iconColor = primaryColor, textColor = textColor) { showAppInfoDialog = true }
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = Color.Gray.copy(alpha = 0.15f))
                SyncOptionItem(icon = Icons.Outlined.Code, title = "Developer Info", subtitle = "Built by Arpon Sarker", iconColor = primaryColor, textColor = textColor) { showDevInfoDialog = true }
            }

            Spacer(modifier = Modifier.height(150.dp))
        }
    }

    // Update Dialog if new release found from manual check
    updateInfoFound?.let { update ->
        InAppUpdateDialog(
            updateInfo = update,
            onDismiss = { updateInfoFound = null }
        )
    }

    // --- ALL DIALOGS FOR SETTINGS ---
    if (showCategorySheet) {
        CategoryManagerBottomSheet(onDismiss = { showCategorySheet = false })
    }

    if (showThemeSheet) {
        ModalBottomSheet(onDismissRequest = { showThemeSheet = false }, containerColor = cardColor, dragHandle = { BottomSheetDefaults.DragHandle() }) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp).padding(bottom = 40.dp).verticalScroll(rememberScrollState())) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Choose Theme", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textColor)
                    if (!PremiumManager.isFeatureAccessible("custom_themes")) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFD700).copy(alpha = 0.15f),
                            modifier = Modifier.clickable {
                                showThemeSheet = false
                                navController.navigate("subscription")
                            }
                        ) {
                            Text("Unlock All (PRO) 👑", color = Color(0xFFFFD700), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }

                ThemeState.ALL_THEMES.forEach { theme ->
                    val isSelected = selectedTheme == theme.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) theme.accentColor.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable {
                                if (theme.isProOnly && !PremiumManager.isFeatureAccessible("custom_themes")) {
                                    showThemeSheet = false
                                    Toast.makeText(context, "👑 '${theme.name}' is an Exclusive PRO Theme. Please Upgrade or Apply a Coupon!", Toast.LENGTH_LONG).show()
                                    navController.navigate("subscription")
                                } else {
                                    selectedTheme = theme.id
                                    val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                                    ThemeState.applyTheme(context, theme.id, isSystemDark)
                                }
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(theme.previewColor.copy(alpha = 0.2f))
                                .border(1.dp, theme.previewColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(theme.previewColor))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(theme.name, fontSize = 15.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = textColor)
                                if (theme.isProOnly) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFFFD700).copy(alpha = 0.18f)
                                    ) {
                                        Text("👑 PRO", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFD700), modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            Text(theme.description, fontSize = 12.sp, color = Color.Gray)
                        }
                        if (isSelected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = theme.accentColor, modifier = Modifier.size(22.dp))
                        } else if (theme.isProOnly && !PremiumManager.isFeatureAccessible("custom_themes")) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

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

    if (showDebtToggleDialog) {
        Dialog(onDismissRequest = { showDebtToggleDialog = false }, properties = DialogProperties(dismissOnClickOutside = true)) {
            Surface(shape = RoundedCornerShape(24.dp), color = cardColor, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(if (pendingDebtToggleState) Color(0xFF34C759).copy(alpha = 0.1f) else Color(0xFFFF9500).copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        Icon(if (pendingDebtToggleState) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = null, tint = if (pendingDebtToggleState) Color(0xFF34C759) else Color(0xFFFF9500), modifier = Modifier.size(30.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(if (pendingDebtToggleState) "Enable Integration?" else "Disable Integration?", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        if (pendingDebtToggleState) "Borrowed money will increase your main balance, and lent money will decrease it. Your wallet and debts will be fully synced."
                        else "Your total wallet balance will no longer be affected by debts. Debt tracking will be completely separate.",
                        fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center, lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { showDebtToggleDialog = false }, modifier = Modifier.weight(1f)) {
                            Text("Cancel", color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                includeDebtInBalance = pendingDebtToggleState
                                prefs.edit().putBoolean("pref_include_debt_in_balance", pendingDebtToggleState).apply()
                                showDebtToggleDialog = false
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (pendingDebtToggleState) Color(0xFF34C759) else Color(0xFFFF9500))
                        ) {
                            Text("Confirm", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }

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

    if (showResetMessDialog) {
        AlertDialog(
            onDismissRequest = { showResetMessDialog = false },
            containerColor = cardColor,
            icon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null, tint = dangerColor, modifier = Modifier.size(32.dp)) },
            title = { Text("Reset Mess Data?", fontWeight = FontWeight.Bold, color = textColor) },
            text = { Text("This will permanently clear all Mess Manager records (roommates, daily meals, bazaar logs, deposits, and fixed costs) both locally and from your cloud account. Wallet transactions and budget will remain safe.", color = Color.Gray, fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        MessManager.resetMessData(context)
                        showResetMessDialog = false
                        Toast.makeText(context, "Mess data has been reset.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = dangerColor)
                ) {
                    Text("Reset Mess", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetMessDialog = false }) {
                    Text("Cancel", color = textColor)
                }
            }
        )
    }

    if (showAppInfoDialog) {
        Dialog(onDismissRequest = { showAppInfoDialog = false }, properties = DialogProperties(dismissOnClickOutside = true)) {
            Surface(shape = RoundedCornerShape(24.dp), color = cardColor, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { showAppInfoDialog = false }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray) }
                    Column(modifier = Modifier.padding(24.dp).padding(top = 10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)).background(ThemeState.headerGradient.value), contentAlignment = Alignment.Center) { Icon(Icons.Default.ShoppingCart, contentDescription = "App Icon", tint = Color.White, modifier = Modifier.size(36.dp)) }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Bachelor's Wallet", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Text("Version $currentAppVersion", fontSize = 14.sp, color = primaryColor, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Your smart personal finance companion. Easily track your daily budget, manage debts, and save more with a clean and aesthetic interface.", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center, lineHeight = 20.sp)
                    }
                }
            }
        }
    }

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

// --- REUSABLE COMPONENTS ---
@Composable
fun ProfileAestheticCard(title: String, cardColor: Color, isDark: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Text(text = title, fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(start = 14.dp, bottom = 10.dp, top = 10.dp))
    Surface(shape = RoundedCornerShape(20.dp), color = cardColor, tonalElevation = 2.dp, shadowElevation = if (isDark) 0.dp else 2.dp, border = if (isDark) BorderStroke(1.dp, Color.Gray.copy(alpha = 0.1f)) else null, modifier = Modifier.fillMaxWidth()) { Column(content = content) }
}

@Composable
fun SyncOptionItem(icon: ImageVector, title: String, subtitle: String, iconColor: Color, textColor: Color, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(46.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp)) }
        Spacer(modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) { Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor); Text(subtitle, fontSize = 12.sp, color = Color.Gray) }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
    }
}

@Composable
fun SwitchOptionItem(icon: ImageVector, title: String, subtitle: String, iconColor: Color, textColor: Color, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(46.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp)) }
        Spacer(modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) { Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor); Text(subtitle, fontSize = 12.sp, color = Color.Gray) }
        Switch(checked = isChecked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.85f), colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = iconColor))
    }
}

@Composable
fun SyncActionButton(icon: ImageVector, title: String, subtitle: String, color: Color, isLoading: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(enabled = !isLoading) { onClick() }.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(46.dp).clip(CircleShape).background(color.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = color, strokeWidth = 2.dp) else Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) { Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (ThemeState.isDark.value) Color.White else Color.Black); Text(subtitle, fontSize = 12.sp, color = Color.Gray) }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
    }
}