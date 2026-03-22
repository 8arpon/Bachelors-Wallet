package com.example.myapplication

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.rememberAsyncImagePainter
import java.util.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.asImageBitmap

object ThemeState {
    var isDark = mutableStateOf(false)
}

const val IS_DEVELOPMENT_MODE = false

fun Context.getActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(navController: NavController) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val prefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }

    // --- CLOUD AUTH STATES ---
    val auth = CloudSyncManager.auth
    var isLoggedIn by remember { mutableStateOf(CloudSyncManager.isUserLoggedIn()) }
    var isLoading by remember { mutableStateOf(false) }
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var isSavingProfile by remember { mutableStateOf(false) }

    var displayName by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(auth.currentUser?.email ?: "") }
    var isEditing by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf("") }

    // HIGHLIGHT: Photo Action States
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var photoActionByUser by remember { mutableStateOf("none") } // "none", "selected", "removed"

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            tempPhotoUri = uri
            photoActionByUser = "selected"
        }
    }

    // --- SETTINGS STATES ---
    var includeDebtInBalance by remember { mutableStateOf(prefs.getBoolean("pref_include_debt_in_balance", true)) }
    var showDebtToggleDialog by remember { mutableStateOf(false) }
    var pendingDebtToggleState by remember { mutableStateOf(true) }

    val systemTheme = isSystemInDarkTheme()
    var selectedTheme by remember { mutableStateOf(prefs.getString("theme_mode", "System") ?: "System") }
    var showThemeSheet by remember { mutableStateOf(false) }

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
    var deletePasswordInput by remember { mutableStateOf("") }
    var deleteWrongPassword by remember { mutableStateOf(false) }

    var showLogoutDialog by remember { mutableStateOf(false) }

    // THEME COLORS
    val isDark = ThemeState.isDark.value
    val bgColor = if (isDark) Color(0xFF121212) else Color(0xFFF2F2F7)
    val cardColor = if (isDark) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDark) Color.White else Color.Black
    val primaryColor = Color(0xFF007AFF)
    val successColor = Color(0xFF34C759)
    val warningColor = Color(0xFFFF9500)
    val dangerColor = Color(0xFFFF3B30)
    val purpleColor = Color(0xFFAF52DE)

    // --- EFFECTS & HELPERS ---
    LaunchedEffect(selectedTheme, systemTheme) {
        val newIsDark = when (selectedTheme) { "Light" -> false; "Dark" -> true; else -> systemTheme }
        ThemeState.isDark.value = newIsDark
        prefs.edit().putBoolean("dark_mode", newIsDark).apply()
        prefs.edit().putString("theme_mode", selectedTheme).apply()
    }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            isLoading = true
            CloudSyncManager.getUserProfile { profile, msg ->
                isLoading = false
                if (profile != null) { displayName = profile["name"] ?: ""; photoUrl = profile["photoUrl"] ?: "" }
                else { displayName = auth.currentUser?.displayName ?: "User"; photoUrl = auth.currentUser?.photoUrl.toString() }
            }
        }
    }

    LaunchedEffect(Unit) {
        val isFirstTimeNotif = prefs.getBoolean("first_time_notif_setup", true)
        if (isFirstTimeNotif && isNotifEnabled) {
            val targetCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 21); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) { add(Calendar.DATE, 1) }
            }
            val intent = Intent(context, NotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            try { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetCal.timeInMillis, pendingIntent) }
            catch (e: SecurityException) { alarmManager.set(AlarmManager.RTC_WAKEUP, targetCal.timeInMillis, pendingIntent) }
            prefs.edit().putBoolean("first_time_notif_setup", false).apply()
            prefs.edit().putString("notif_time", "9:00 PM").apply()
        }
    }

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

    fun saveProfile() {
        if (tempName.isBlank()) {
            Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }
        isSavingProfile = true
        val isRemoving = photoActionByUser == "removed"

        CloudSyncManager.saveOrUpdateUserProfile(context, tempName, tempPhotoUri, isRemoving) { success, msg ->
            isSavingProfile = false
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            if (success) {
                displayName = tempName
                // HIGHLIGHT: ইনস্ট্যান্ট লোকাল আপডেট
                if (isRemoving) photoUrl = ""

                CloudSyncManager.getUserProfile { profile, _ ->
                    if (profile != null) photoUrl = profile["photoUrl"] ?: ""
                }
                isEditing = false
            }
        }
    }

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

    // --- MAIN UI ---
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account", fontWeight = FontWeight.Bold, fontSize = 28.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor, titleContentColor = textColor),
                actions = {
                    if (isLoggedIn) {
                        AnimatedVisibility(visible = !isEditing) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = {
                                    isEditing = true
                                    tempName = displayName
                                    photoActionByUser = "none"
                                }) {
                                    Text("Edit", color = primaryColor, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                }
                                IconButton(onClick = {
                                    CloudSyncManager.auth.signOut()
                                    isLoggedIn = false
                                    displayName = ""
                                    photoUrl = ""
                                    email = ""
                                    Toast.makeText(context, "Logged Out Successfully", Toast.LENGTH_SHORT).show()
                                }) {
                                    // HIGHLIGHT: ডায়ালগ ওপেন করবে
                                    IconButton(onClick = { showLogoutDialog = true }) {
                                        Icon(Icons.Outlined.Logout, contentDescription = "Logout", tint = dangerColor)
                                    }                                }
                            }
                        }
                        AnimatedVisibility(visible = isEditing) {
                            Row {
                                TextButton(onClick = { isEditing = false; tempPhotoUri = null; photoActionByUser = "none" }) { Text("Cancel", color = dangerColor, fontSize = 16.sp) }
                                TextButton(onClick = { saveProfile() }, enabled = !isSavingProfile) {
                                    if (isSavingProfile) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = primaryColor) else Text("Done", color = primaryColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            )
        },
        containerColor = bgColor
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = primaryColor)
            } else {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {

                    // --- 1. PROFILE HEADER ---
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.BottomEnd) {
                        Box(modifier = Modifier.size(110.dp).clip(CircleShape).border(2.dp, primaryColor.copy(alpha = 0.3f), CircleShape).background(Color.Gray.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {

                            // HIGHLIGHT: Smart Image Logic
                            if (photoActionByUser == "selected" && tempPhotoUri != null && isEditing) {
                                Image(painter = rememberAsyncImagePainter(tempPhotoUri), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else if (photoActionByUser == "removed" && isEditing) {
                                InitialAvatar(name = tempName.ifBlank { "U" }, size = 110.dp)
                            } else if (photoUrl.startsWith("data:image")) {
                                val decodedBitmap = remember(photoUrl) {
                                    try {
                                        val base64String = photoUrl.substringAfter(",")
                                        val imageBytes = android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)
                                        android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                    } catch (e: Exception) { null }
                                }
                                if (decodedBitmap != null) {
                                    Image(bitmap = decodedBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else { InitialAvatar(name = displayName, size = 110.dp) }
                            } else if (photoUrl.isNotEmpty() && photoUrl != "null") {
                                Image(painter = rememberAsyncImagePainter(photoUrl), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                InitialAvatar(name = if(isEditing) tempName else displayName, size = 110.dp)
                            }
                        }

                        if (isEditing) {
                            Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(primaryColor).border(3.dp, bgColor, CircleShape).clickable { imagePickerLauncher.launch("image/*") }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // HIGHLIGHT: Remove Photo Button
                    AnimatedVisibility(visible = isEditing && (photoUrl.isNotEmpty() || photoActionByUser == "selected") && photoActionByUser != "removed") {
                        TextButton(onClick = { photoActionByUser = "removed"; tempPhotoUri = null }, modifier = Modifier.padding(top = 8.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = dangerColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove Photo", color = dangerColor, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    AnimatedVisibility(visible = !isEditing) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = if (displayName.isNotEmpty()) displayName else if (isLoggedIn) "User" else "Guest Mode", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = textColor)
                            if (isLoggedIn) Text(text = email, fontSize = 14.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(color = if (isLoggedIn) successColor.copy(alpha = 0.1f) else dangerColor.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (isLoggedIn) successColor.copy(alpha = 0.5f) else dangerColor.copy(alpha = 0.5f))) {
                                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (isLoggedIn) Icons.Outlined.CheckCircle else Icons.Default.CloudOff, contentDescription = null, tint = if (isLoggedIn) successColor else dangerColor, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isLoggedIn) "Cloud Sync Active" else "Local Storage Only", color = if (isLoggedIn) successColor else dangerColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    AnimatedVisibility(visible = isEditing) {
                        OutlinedTextField(value = tempName, onValueChange = { tempName = it }, label = { Text("Display Name") }, modifier = Modifier.fillMaxWidth(0.9f), shape = RoundedCornerShape(12.dp), singleLine = true, leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = primaryColor) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor, focusedBorderColor = primaryColor))
                    }
                    Spacer(modifier = Modifier.height(30.dp))

                    if (!isLoggedIn) {
                        Button(onClick = { navController.navigate("auth") }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = primaryColor), shape = RoundedCornerShape(14.dp)) { Text("Login to Auto-Sync Data", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                        Spacer(modifier = Modifier.height(30.dp))
                    }

                    // --- 2. APP PREFERENCES ---
                    ProfileAestheticCard(title = "App Preferences", cardColor = cardColor, isDark = isDark) {
                        SyncOptionItem(icon = Icons.Outlined.Palette, title = "App Theme", subtitle = selectedTheme, iconColor = primaryColor, textColor = textColor) { showThemeSheet = true }
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = Color.Gray.copy(alpha = 0.15f))
                        SwitchOptionItem(icon = Icons.Outlined.Download, title = "Auto-Download Report", subtitle = "Save daily summary as PDF", iconColor = primaryColor, textColor = textColor, isChecked = autoDownload) { autoDownload = it; prefs.edit().putBoolean("pref_auto_download", it).apply() }
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    // --- 3. FINANCE & ALERTS ---
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

                    // --- 4. CLOUD SYNCHRONIZATION ---
                    if (isLoggedIn) {
                        ProfileAestheticCard(title = "Cloud Synchronization", cardColor = cardColor, isDark = isDark) {
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

                    // --- 5. SECURITY & DANGER ---
                    ProfileAestheticCard(title = "Security & Tools", cardColor = cardColor, isDark = isDark) {
                        SyncOptionItem(icon = Icons.Outlined.Lock, title = if (currentSavedPassword.isEmpty()) "Set App Password" else "Change App Password", subtitle = "Secure your budget app", iconColor = successColor, textColor = textColor) { oldPassInput = ""; newPassInput = ""; passErrorMsg = ""; isBiometricVerifiedForPassword = false; showPasswordDialog = true }
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = Color.Gray.copy(alpha = 0.15f))
                        SyncOptionItem(icon = Icons.Outlined.Delete, title = "Delete All History", subtitle = "Permanently delete history", iconColor = dangerColor, textColor = dangerColor) { showDangerDialog = true }
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    // --- 6. ABOUT ---
                    ProfileAestheticCard(title = "About", cardColor = cardColor, isDark = isDark) {
                        SyncOptionItem(icon = Icons.Outlined.Info, title = "About Application", subtitle = "Version 2.0.0", iconColor = primaryColor, textColor = textColor) { showAppInfoDialog = true }
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = Color.Gray.copy(alpha = 0.15f))
                        SyncOptionItem(icon = Icons.Outlined.Code, title = "Developer Info", subtitle = "Built by Arpon Sarker", iconColor = purpleColor, textColor = textColor) { showDevInfoDialog = true }
                    }

                    Spacer(modifier = Modifier.height(150.dp)) // Extra padding for bottom nav
                }
            }
        }
    }

    // =========================================================================
    //  ALL DIALOGS EXACTLY RESTORED FROM ORIGINAL CODE
    // =========================================================================

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

    // --- DEBT INTEGRATION TOGGLE DIALOG ---
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


    // --- LOGOUT WARNING DIALOG ---
    if (showLogoutDialog) {
        AlertDialog(
            containerColor = cardColor,
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out?", color = dangerColor, fontWeight = FontWeight.Bold) },
            text = {
                Text("Logging out will clear all local data from this device for your security.\n\nPlease make sure you have tapped 'Backup Now' to save your data to the cloud before leaving.", color = textColor, fontSize = 14.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        // ১. সব লোকাল ডাটা মুছে দেওয়া হলো!
                        DataManager.clearAllData(context)
                        // ২. ফায়ারবেস থেকে সাইন আউট
                        CloudSyncManager.auth.signOut()

                        isLoggedIn = false
                        displayName = ""
                        photoUrl = ""
                        email = ""
                        showLogoutDialog = false
                        Toast.makeText(context, "Logged Out & Device Data Cleared!", Toast.LENGTH_LONG).show()
                        navController.navigate("auth") { popUpTo(0) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = dangerColor)
                ) { Text("Log Out & Clear", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel", color = textColor) }
            }
        )
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
                        Text("Version 2.0.0", fontSize = 14.sp, color = Color(0xFF007AFF), fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Your smart personal finance companion. Easily track your daily budget, manage debts, and save more with a clean and aesthetic interface.", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center, lineHeight = 20.sp)
                    }
                }
            }
        }
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

// --- HELPER COMPONENT: INITIAL AVATAR ---
@Composable
fun InitialAvatar(name: String, size: androidx.compose.ui.unit.Dp) {
    val initial = if (name.isNotBlank()) name.trim().first().uppercase() else "U"
    val avatarColors = listOf(Color(0xFF007AFF), Color(0xFF34C759), Color(0xFFFF9500), Color(0xFFAF52DE), Color(0xFFFF3B30))
    val charIndex = if (name.isNotBlank()) name.first().code % avatarColors.size else 0
    Box(modifier = Modifier.size(size).clip(CircleShape).background(avatarColors[charIndex]), contentAlignment = Alignment.Center) {
        Text(text = initial, fontSize = (size.value / 2.2).sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

// --- REUSABLE AESTHETIC COMPONENTS ---
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