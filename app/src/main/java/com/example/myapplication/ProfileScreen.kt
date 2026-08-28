package com.example.myapplication

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter

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

    val auth = CloudSyncManager.auth
    var isLoggedIn by remember { mutableStateOf(CloudSyncManager.isUserLoggedIn()) }
    var isLoading by remember { mutableStateOf(false) }
    var isSavingProfile by remember { mutableStateOf(false) }

    var displayName by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(auth.currentUser?.email ?: "") }
    var isEditing by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf("") }

    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var photoActionByUser by remember { mutableStateOf("none") }

    var showLogoutDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            tempPhotoUri = uri
            photoActionByUser = "selected"
        }
    }

    val isDark = ThemeState.isDark.value
    val bgColor = ThemeState.background.value
    val cardColor = ThemeState.cardBackground.value
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val primaryColor = ThemeState.primaryAccent.value
    val successColor = Color(0xFF10B981)
    val dangerColor = Color(0xFFF43F5E)
    val purpleBg = ThemeState.headerGradient.value

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
                if (isRemoving) photoUrl = ""

                CloudSyncManager.getUserProfile { profile, _ ->
                    if (profile != null) photoUrl = profile["photoUrl"] ?: ""
                }
                isEditing = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        // Aesthetic Top Header Background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                .background(purpleBg)
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Text("Profile", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)

                if (isLoggedIn) {
                    if (!isEditing) {
                        Row {
                            IconButton(
                                onClick = { isEditing = true; tempName = displayName; photoActionByUser = "none" },
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { showLogoutDialog = true },
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(dangerColor.copy(alpha = 0.8f))
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "Logout", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    } else {
                        IconButton(
                            onClick = { isEditing = false; tempPhotoUri = null; photoActionByUser = "none" },
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }

            // Main Scrollable Content
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = primaryColor)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(20.dp))

                    // Floating Avatar Box
                    Box(modifier = Modifier.size(130.dp), contentAlignment = Alignment.BottomEnd) {
                        Surface(
                            modifier = Modifier.size(130.dp),
                            shape = CircleShape,
                            color = cardColor,
                            shadowElevation = 12.dp,
                            border = BorderStroke(4.dp, Color.White.copy(alpha = 0.8f))
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (photoActionByUser == "selected" && tempPhotoUri != null && isEditing) {
                                    Image(painter = rememberAsyncImagePainter(tempPhotoUri), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else if (photoActionByUser == "removed" && isEditing) {
                                    InitialAvatar(name = tempName.ifBlank { "U" }, size = 130.dp)
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
                                    } else { InitialAvatar(name = displayName, size = 130.dp) }
                                } else if (photoUrl.isNotEmpty() && photoUrl != "null") {
                                    Image(painter = rememberAsyncImagePainter(photoUrl), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    InitialAvatar(name = if(isEditing) tempName else displayName, size = 130.dp)
                                }
                            }
                        }

                        if (isEditing) {
                            Box(
                                modifier = Modifier.size(40.dp).offset(x = (-4).dp, y = (-4).dp).clip(CircleShape).background(primaryColor).border(3.dp, cardColor, CircleShape).clickable { imagePickerLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    AnimatedVisibility(visible = isEditing && (photoUrl.isNotEmpty() || photoActionByUser == "selected") && photoActionByUser != "removed") {
                        TextButton(onClick = { photoActionByUser = "removed"; tempPhotoUri = null }, modifier = Modifier.padding(top = 12.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = dangerColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Remove Photo", color = dangerColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // User Info Card
                    AnimatedVisibility(visible = !isEditing) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            color = cardColor,
                            shadowElevation = 8.dp
                        ) {
                            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (displayName.isNotEmpty()) displayName else if (isLoggedIn) "User" else "Guest Mode",
                                    fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = textColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (isLoggedIn) {
                                    Text(text = email, fontSize = 15.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                                HorizontalDivider(color = Color.Gray.copy(alpha = 0.1f))
                                Spacer(modifier = Modifier.height(20.dp))

                                Surface(
                                    color = if (isLoggedIn) successColor.copy(alpha = 0.1f) else dangerColor.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(if (isLoggedIn) Icons.Outlined.CheckCircle else Icons.Default.CloudOff, contentDescription = null, tint = if (isLoggedIn) successColor else dangerColor, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(if (isLoggedIn) "Cloud Sync is Active" else "Local Storage Only", color = if (isLoggedIn) successColor else dangerColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                val isPro = PremiumManager.isProUser.value
                                val planTitle = PremiumManager.currentPlanTitle.value
                                val expiryText = PremiumManager.proExpiryText.value

                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = if (isPro) Color(0xFFFFD700).copy(alpha = 0.12f) else Color.Gray.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(16.dp),
                                    border = if (isPro) BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.3f)) else null,
                                    modifier = Modifier.fillMaxWidth().clickable { navController.navigate("subscription") }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.WorkspacePremium,
                                                contentDescription = null,
                                                tint = if (isPro) Color(0xFFFFD700) else Color.Gray,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (isPro) "$planTitle 👑" else "Free Plan",
                                                color = if (isPro) Color(0xFFFFD700) else textColor,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (isPro) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = expiryText,
                                                color = Color.Gray,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Edit Mode Fields
                    AnimatedVisibility(visible = isEditing) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            color = cardColor,
                            shadowElevation = 8.dp
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text("Edit Information", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.padding(bottom = 16.dp))

                                OutlinedTextField(
                                    value = tempName, onValueChange = { tempName = it }, label = { Text("Display Name") },
                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = primaryColor) },
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor, focusedBorderColor = primaryColor, focusedLabelColor = primaryColor)
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = { saveProfile() }, enabled = !isSavingProfile,
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    if (isSavingProfile) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                                    else Text("Save Changes", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    if (!isLoggedIn) {
                        Button(
                            onClick = { navController.navigate("auth") },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Login to Auto-Sync Data", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            containerColor = cardColor,
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out?", color = dangerColor, fontWeight = FontWeight.Bold) },
            text = { Text("Logging out will clear all local data from this device for your security.\n\nPlease make sure you have tapped 'Backup Now' in Settings to save your data to the cloud before leaving.", color = textColor, fontSize = 15.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        DataManager.clearAllData(context)
                        CloudSyncManager.auth.signOut()
                        PremiumManager.revokePro(context)
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
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel", color = Color.Gray) } }
        )
    }
}

@Composable
fun InitialAvatar(name: String, size: androidx.compose.ui.unit.Dp) {
    val initial = if (name.isNotBlank()) name.trim().first().uppercase() else "U"
    val avatarColors = listOf(Color(0xFF5E45DA), Color(0xFF10B981), Color(0xFFFF9500), Color(0xFFAF52DE), Color(0xFFF43F5E))
    val charIndex = if (name.isNotBlank()) name.first().code % avatarColors.size else 0

    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(avatarColors[charIndex]),
        contentAlignment = Alignment.Center
    ) {
        Text(text = initial, fontSize = (size.value / 2.2).sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}