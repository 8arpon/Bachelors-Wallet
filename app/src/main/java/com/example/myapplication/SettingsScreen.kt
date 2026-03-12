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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import java.util.*

object ThemeState {
    var isDark = mutableStateOf(false)
}

fun Context.getActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }

    // States
    var isDarkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    var notificationTime by remember { mutableStateOf(prefs.getString("notif_time", "Not Set")!!) }
    var showAppInfo by remember { mutableStateOf(false) }
    var showDevInfo by remember { mutableStateOf(false) }

    // HIGHLIGHT: Security States
    var currentSavedPassword by remember { mutableStateOf(prefs.getString("app_password", "") ?: "") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var oldPassInput by remember { mutableStateOf("") }
    var newPassInput by remember { mutableStateOf("") }
    var passErrorMsg by remember { mutableStateOf("") }

    // Danger Zone States
    var showDangerDialog by remember { mutableStateOf(false) }
    var deletePasswordInput by remember { mutableStateOf("") }
    var deleteWrongPassword by remember { mutableStateOf(false) }

    // Dynamic Colors
    val bgColor = if (ThemeState.isDark.value) Color(0xFF121212) else Color(0xFFF2F2F7)
    val cardColor = if (ThemeState.isDark.value) Color(0xFF1E1E1E) else Color.White
    val textColor = if (ThemeState.isDark.value) Color.White else Color.Black
    val iconColor = if (ThemeState.isDark.value) Color.White else Color.Black

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val calendar = Calendar.getInstance()
    val timePickerDialog = TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            val amPm = if (hourOfDay >= 12) "PM" else "AM"
            val hr = if (hourOfDay % 12 == 0) 12 else hourOfDay % 12
            val min = String.format("%02d", minute)
            val formattedTime = "$hr:$min $amPm"

            notificationTime = formattedTime
            prefs.edit().putString("notif_time", formattedTime).apply()

            val targetCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) { add(Calendar.DATE, 1) }
            }

            val intent = Intent(context, NotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetCal.timeInMillis, pendingIntent)
            } catch (e: SecurityException) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, targetCal.timeInMillis, pendingIntent)
            }
        },
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        false
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
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    Toast.makeText(context, "Auth Error: $errString", Toast.LENGTH_SHORT).show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verify Identity")
            .setSubtitle("Confirm it's you to delete all wallet data")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(bgColor).statusBarsPadding().padding(horizontal = 20.dp, vertical = 15.dp).verticalScroll(rememberScrollState())
    ) {
        // --- HEADER ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(45.dp).clip(CircleShape).background(cardColor).clickable { navController.popBackStack() },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = iconColor) }
            Spacer(modifier = Modifier.width(15.dp))
            Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = textColor)
        }

        Spacer(modifier = Modifier.height(30.dp))

        // --- PREFERENCES SECTION ---
        // SettingsScreen.kt er vitore state variable e eta add korbe (isDarkMode er pore)
        var isNotifEnabled by remember { mutableStateOf(prefs.getBoolean("notif_enabled", true)) }


        // --- PREFERENCES SECTION ---
        Text("PREFERENCES", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 8.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = cardColor, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (ThemeState.isDark.value) Icons.Default.Star else Icons.Default.ThumbUp, contentDescription = null, tint = Color(0xFF007AFF))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Dark Mode", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = ThemeState.isDark.value,
                        onCheckedChange = {
                            ThemeState.isDark.value = it; isDarkMode = it
                            prefs.edit().putBoolean("dark_mode", it).apply()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF34C759))
                    )
                }

                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.padding(start = 50.dp))

                // HIGHLIGHT: Notification ON/OFF Switch
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFFFF9500))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Enable Notifications", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = isNotifEnabled,
                        onCheckedChange = { isEnabled ->
                            isNotifEnabled = isEnabled
                            prefs.edit().putBoolean("notif_enabled", isEnabled).apply()

                            // Jodi OFF kore dey, alarm cancel hobe
                            if (!isEnabled) {
                                val intent = Intent(context, NotificationReceiver::class.java)
                                val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                                alarmManager.cancel(pendingIntent)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF34C759))
                    )
                }

                // Time picker row (Sudhu ON thaklei kaaj korbe)
                AnimatedVisibility(visible = isNotifEnabled) {
                    Column {
                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.padding(start = 50.dp))
                        Row(modifier = Modifier.fillMaxWidth().clickable { timePickerDialog.show() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color(0xFF007AFF))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Notification Time", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(notificationTime, fontSize = 16.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
                        }

                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.padding(start = 50.dp))
                        Row(modifier = Modifier.fillMaxWidth().clickable {
                            context.sendBroadcast(Intent(context, NotificationReceiver::class.java))
                        }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF34C759))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Test Notification Now", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // --- SECURITY SECTION (NEW) ---
        Text("SECURITY", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 8.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = cardColor, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    oldPassInput = ""
                    newPassInput = ""
                    passErrorMsg = ""
                    showPasswordDialog = true
                }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF34C759))
                Spacer(modifier = Modifier.width(16.dp))
                Text(if (currentSavedPassword.isEmpty()) "Set App Password" else "Change App Password", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // --- ABOUT SECTION ---
        Text("ABOUT", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 8.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = cardColor, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column {
                Column(modifier = Modifier.fillMaxWidth().clickable { showAppInfo = !showAppInfo }.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF007AFF))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("About Application", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(if (showAppInfo) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
                    }
                    AnimatedVisibility(visible = showAppInfo, enter = expandVertically(), exit = shrinkVertically()) {
                        Column(modifier = Modifier.padding(top = 10.dp, start = 40.dp)) {
                            Text("My Wallet App", fontWeight = FontWeight.Bold, color = textColor)
                            Text("Version: 1.0.0 (Beta)", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                }
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), modifier = Modifier.padding(start = 50.dp))
                Column(modifier = Modifier.fillMaxWidth().clickable { showDevInfo = !showDevInfo }.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFFAF52DE))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Developer Info", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(if (showDevInfo) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
                    }
                    AnimatedVisibility(visible = showDevInfo, enter = expandVertically(), exit = shrinkVertically()) {
                        Column(modifier = Modifier.padding(top = 10.dp, start = 40.dp)) {
                            Text("Arpon Sarker", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textColor)
                            Text("Daffodil International University", color = Color(0xFF007AFF), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // --- DANGER ZONE ---
        Text("DANGER ZONE", fontSize = 13.sp, color = Color.Red, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 8.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = cardColor, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().clickable { showDangerDialog = true }.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Delete All History", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.Red)
                }
            }
        }
        Spacer(modifier = Modifier.height(100.dp))
    }

    // --- AESTHETIC PASSWORD SET/CHANGE DIALOG ---
    if (showPasswordDialog) {
        Dialog(onDismissRequest = { showPasswordDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = cardColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Color(0xFF34C759).copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(30.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(if (currentSavedPassword.isEmpty()) "Set New Password" else "Change Password", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = textColor)
                    Text("Secure your budget and settings", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)

                    Spacer(modifier = Modifier.height(20.dp))

                    if (currentSavedPassword.isNotEmpty()) {
                        OutlinedTextField(
                            value = oldPassInput,
                            onValueChange = { oldPassInput = it; passErrorMsg = "" },
                            label = { Text("Current Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    OutlinedTextField(
                        value = newPassInput,
                        onValueChange = { newPassInput = it; passErrorMsg = "" },
                        label = { Text("New Password (Min 4 chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (passErrorMsg.isNotEmpty()) {
                        Text(passErrorMsg, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp).align(Alignment.Start))
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { showPasswordDialog = false }, modifier = Modifier.weight(1f)) {
                            Text("Cancel", color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                if (currentSavedPassword.isNotEmpty() && oldPassInput != currentSavedPassword) {
                                    passErrorMsg = "Current password is wrong"
                                } else if (newPassInput.length < 4) {
                                    passErrorMsg = "Password too short"
                                } else {
                                    prefs.edit().putString("app_password", newPassInput).apply()
                                    currentSavedPassword = newPassInput
                                    showPasswordDialog = false
                                    Toast.makeText(context, "Password Saved!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
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
                            OutlinedTextField(
                                value = deletePasswordInput,
                                onValueChange = { deletePasswordInput = it },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF007AFF).copy(alpha = 0.1f)).clickable { authenticateAndWipe() },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.Lock, contentDescription = "Biometric Unlock", tint = Color(0xFF007AFF)) }
                        }
                    } else {
                        // HIGHLIGHT: If no password is set, show a large Biometric Button
                        Text("Authentication required to continue.", color = Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { authenticateAndWipe() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verify with Fingerprint", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                if (currentSavedPassword.isNotEmpty()) {
                    Button(
                        onClick = {
                            if (deletePasswordInput == currentSavedPassword) {
                                DataManager.clearAllData(context)
                                Toast.makeText(context, "All data deleted", Toast.LENGTH_LONG).show()
                                showDangerDialog = false; deletePasswordInput = ""
                                navController.navigate("home") { popUpTo(0) }
                            } else { deleteWrongPassword = true }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) { Text("Wipe Data", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDangerDialog = false; deleteWrongPassword = false; deletePasswordInput = "" }) { Text("Cancel", color = textColor) }
            }
        )
    }
}