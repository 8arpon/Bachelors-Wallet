package com.example.myapplication

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    val context = LocalContext.current
    var isLogin by remember { mutableStateOf(true) }

    // Form States
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Image State
    var profileImageUri by remember { mutableStateOf<Uri?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        profileImageUri = uri
    }

    var isLoading by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    val bgColor = if (ThemeState.isDark.value) Color(0xFF121212) else Color(0xFFF2F2F7)
    val textColor = if (ThemeState.isDark.value) Color.White else Color.Black

    // --- HIGHLIGHT: Google Sign-in Logic ---
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        // NOTE: Make sure your valid Web Client ID is placed here!
        .requestIdToken("465817919177-trpe5ogmori8e9t1eikv2e61lj145fi6.apps.googleusercontent.com")
        .requestEmail()
        .build()
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleAuthLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account?.idToken, null)
                isLoading = true
                CloudSyncManager.auth.signInWithCredential(credential)
                    .addOnSuccessListener {
                        CloudSyncManager.restoreFromCloud(context) { _, _ ->
                            isLoading = false
                            onAuthSuccess()
                        }
                    }
                    .addOnFailureListener {
                        isLoading = false
                        Toast.makeText(context, "Google Auth Failed: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
            } catch (e: ApiException) {
                isLoading = false
                Toast.makeText(context, "Google Sign in failed: ${e.statusCode}", Toast.LENGTH_SHORT).show()
            }
        } else {
            isLoading = false
        }
    }

    // --- HIGHLIGHT: Official Multi-Color Google Vector Logo ---
    val googleIcon = remember {
        ImageVector.Builder(name = "Google", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(fill = SolidColor(Color(0xFF4285F4))) {
                moveTo(22.56f, 12.25f); curveTo(22.56f, 11.47f, 22.49f, 10.72f, 22.36f, 10.0f); lineTo(12.0f, 10.0f); lineTo(12.0f, 14.26f); lineTo(17.92f, 14.26f); curveTo(17.66f, 15.63f, 16.88f, 16.79f, 15.71f, 17.57f); lineTo(15.71f, 20.34f); lineTo(19.28f, 20.34f); curveTo(21.37f, 18.42f, 22.56f, 15.6f, 22.56f, 12.25f); close()
            }
            path(fill = SolidColor(Color(0xFF34A853))) {
                moveTo(12.0f, 23.0f); curveTo(14.97f, 23.0f, 17.46f, 22.02f, 19.28f, 20.34f); lineTo(15.71f, 17.57f); curveTo(14.73f, 18.23f, 13.46f, 18.63f, 12.0f, 18.63f); curveTo(9.18f, 18.63f, 6.79f, 16.72f, 5.92f, 14.18f); lineTo(2.24f, 14.18f); lineTo(2.24f, 17.03f); curveTo(4.04f, 20.61f, 7.71f, 23.0f, 12.0f, 23.0f); close()
            }
            path(fill = SolidColor(Color(0xFFFBBC05))) {
                moveTo(5.92f, 14.18f); curveTo(5.7f, 13.52f, 5.57f, 12.78f, 5.57f, 12.0f); curveTo(5.57f, 11.22f, 5.7f, 10.48f, 5.92f, 9.82f); lineTo(5.92f, 6.97f); lineTo(2.24f, 6.97f); curveTo(1.48f, 8.48f, 1.0f, 10.18f, 1.0f, 12.0f); curveTo(1.0f, 13.82f, 1.48f, 15.52f, 2.24f, 17.03f); lineTo(5.92f, 14.18f); close()
            }
            path(fill = SolidColor(Color(0xFFEA4335))) {
                moveTo(12.0f, 5.38f); curveTo(13.62f, 5.38f, 15.06f, 5.94f, 16.2f, 7.02f); lineTo(19.36f, 3.86f); curveTo(17.46f, 2.09f, 14.97f, 1.0f, 12.0f, 1.0f); curveTo(7.71f, 1.0f, 4.04f, 3.39f, 2.24f, 6.97f); lineTo(5.92f, 9.82f); curveTo(6.79f, 7.28f, 9.18f, 5.38f, 12.0f, 5.38f); close()
            }
        }.build()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(bgColor).verticalScroll(rememberScrollState()).padding(24.dp).padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- HEADER ---
        Icon(
            imageVector = if (isLogin) Icons.Default.LockOpen else Icons.Default.PersonAdd,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = Color(0xFF007AFF)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = if (isLogin) "Welcome Back" else "Create Account", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = textColor)
        Text(text = if (isLogin) "Login to sync your data to cloud" else "Start your cloud backup journey", fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))

        // --- SIGN UP SPECIFIC FIELDS ---
        AnimatedVisibility(visible = !isLogin) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(Color.Gray.copy(alpha = 0.2f)).clickable { imagePickerLauncher.launch("image/*") }, contentAlignment = Alignment.Center) {
                    if (profileImageUri != null) {
                        Image(painter = rememberAsyncImagePainter(profileImageUri), contentDescription = "Profile Picture", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(30.dp))
                            Text("Add Photo", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text("First Name") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor))
                    OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text("Last Name") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // --- COMMON FIELDS (Email & Password) ---
        OutlinedTextField(
            value = email, onValueChange = { email = it }, label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor)
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password, onValueChange = { password = it }, label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null) } },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textColor, unfocusedTextColor = textColor)
        )
        Spacer(modifier = Modifier.height(24.dp))

        // --- MAIN ACTION BUTTON ---
        Button(
            onClick = {
                if (email.isEmpty() || password.isEmpty() || (!isLogin && (firstName.isEmpty() || lastName.isEmpty()))) {
                    Toast.makeText(context, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isLoading = true
                val auth = CloudSyncManager.auth
                if (isLogin) {
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener {
                            CloudSyncManager.restoreFromCloud(context) { _, _ -> isLoading = false; onAuthSuccess() }
                        }
                        .addOnFailureListener { isLoading = false; Toast.makeText(context, it.localizedMessage, Toast.LENGTH_LONG).show() }
                } else {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener {
                            CloudSyncManager.backupToCloud(context) { _, _ -> isLoading = false; onAuthSuccess() }
                        }
                        .addOnFailureListener { isLoading = false; Toast.makeText(context, it.localizedMessage, Toast.LENGTH_LONG).show() }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)), enabled = !isLoading
        ) {
            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text(if (isLogin) "Login" else "Create Account", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- GOOGLE SIGN IN BUTTON ---
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = Color.Gray.copy(alpha = 0.3f))
            Text("  OR  ", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            HorizontalDivider(modifier = Modifier.weight(1f), color = Color.Gray.copy(alpha = 0.3f))
        }
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = {
                isLoading = true
                // HIGHLIGHT: Force sign out first so the account picker ALWAYS shows
                googleSignInClient.signOut().addOnCompleteListener {
                    googleAuthLauncher.launch(googleSignInClient.signInIntent)
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
        ) {
            // HIGHLIGHT: Using the Official Colorful Google Logo
            Icon(googleIcon, contentDescription = "Google", tint = Color.Unspecified, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("Continue with Google", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = textColor)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- TOGGLE LOGIN/SIGNUP ---
        TextButton(onClick = { isLogin = !isLogin }) {
            Text(text = if (isLogin) "Don't have an account? Sign Up" else "Already have an account? Login", color = Color(0xFF007AFF), fontWeight = FontWeight.Medium)
        }
    }
}