package com.example.myapplication

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class DynamicPaymentMethod(
    val id: String = "",
    val name: String = "bKash",
    val number: String = "01613238009",
    val type: String = "Personal",
    val colorHex: Long = 0xFFE2136E
)

data class DynamicSubscriptionPlan(
    val id: String = "annual",
    val title: String = "Annual VIP Pass",
    val price: String = "৳৬৯৯",
    val period: String = "/ 1 Year (365 Days)",
    val subtitle: String = "৳৫৮ / month (Save 42%) • Most Popular for Students",
    val tag: String? = "POPULAR CHOICE ⭐",
    val durationDays: Int = 365,
    val isStudentOffer: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(navController: NavController) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val isPro = PremiumManager.isProUser.value
    val currentPlan = PremiumManager.currentPlanTitle.value
    val isLoggedIn = CloudSyncManager.isUserLoggedIn()

    var couponInput by remember { mutableStateOf("") }
    var couponMessage by remember { mutableStateOf<String?>(null) }
    var isSuccessCoupon by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }

    var selectedPlanIndex by remember { mutableIntStateOf(1) } // 0 = Monthly, 1 = Yearly, 2 = Lifetime

    // Pending payment request state
    var pendingTrxId by remember { mutableStateOf<String?>(null) }
    var pendingPlanName by remember { mutableStateOf<String?>(null) }

    // Student Verification State
    var studentVerificationStatus by remember { mutableStateOf<String?>(null) }
    var showStudentVerificationDialog by remember { mutableStateOf(false) }
    var isSubmittingStudentId by remember { mutableStateOf(false) }

    // Dynamic Subscription Plans list loaded from Admin Dashboard / Firestore
    var plansList by remember {
        mutableStateOf(
            listOf(
                DynamicSubscriptionPlan("monthly", "Monthly Pass", "৳৯৯", "/ Month", "Flexible 30-Day access for casual budgeters", null, 30),
                DynamicSubscriptionPlan("annual", "Annual VIP Pass", "৳৬৯৯", "/ 1 Year (365 Days)", "৳৫৮ / month (Save 42%) • Most Popular for Students", "POPULAR CHOICE ⭐", 365),
                DynamicSubscriptionPlan("lifetime", "Lifetime VIP Pass", "৳৯৯৯", "One-Time", "Pay once, enjoy Bachelors Wallet PRO forever!", "BEST VALUE 👑", -1)
            )
        )
    }

    // Dynamic Payment Methods list loaded from Firestore
    var paymentMethodsList by remember {
        mutableStateOf(
            listOf(
                DynamicPaymentMethod("bkash", "bKash (বিকাশ)", "01613238009", "Personal", 0xFFE2136E),
                DynamicPaymentMethod("nagad", "Nagad (নগদ)", "01613238009", "Personal", 0xFFF7941D),
                DynamicPaymentMethod("rocket", "Rocket (রকেট)", "016132380095", "Personal", 0xFF8C1D82)
            )
        )
    }

    val goldColor = Color(0xFFFFD700)
    val goldGradient = Brush.horizontalGradient(listOf(Color(0xFFFFB300), Color(0xFFFFD700), Color(0xFFFFE082)))
    val darkCardBg = if (ThemeState.isDark.value) Color(0xFF1E1E24) else Color.White
    val textColor = if (ThemeState.isDark.value) Color.White else Color(0xFF1C1C1E)

    // Real-time live sync for all users (Instant propagation of admin offers & numbers)
    LaunchedEffect(Unit) {
        PremiumManager.updateExpiryText(context)
        val db = FirebaseFirestore.getInstance()

        // 1. Fetch dynamic subscription plans from Firestore in real-time
        db.collection("subscription_plans").addSnapshotListener { snap, _ ->
            if (snap != null && !snap.isEmpty) {
                val list = mutableListOf<DynamicSubscriptionPlan>()
                for (doc in snap.documents) {
                    val title = doc.getString("title") ?: "VIP Plan"
                    val price = doc.getString("price") ?: "৳৯৯"
                    val period = doc.getString("period") ?: "/ Month"
                    val subtitle = doc.getString("subtitle") ?: ""
                    val tag = doc.getString("tag")
                    val durationDays = (doc.getLong("durationDays") ?: 30L).toInt()
                    val isStudent = doc.getBoolean("isStudentOffer") ?: doc.getBoolean("isStudent") ?: false
                    list.add(DynamicSubscriptionPlan(doc.id, title, price, period, subtitle, tag, durationDays, isStudent))
                }
                if (list.isNotEmpty()) {
                    plansList = list
                }
            }
        }

        // 2. Fetch dynamic payment numbers from Firestore 'payment_methods'
        db.collection("payment_methods").addSnapshotListener { snap, _ ->
            if (snap != null && !snap.isEmpty) {
                val list = mutableListOf<DynamicPaymentMethod>()
                for (doc in snap.documents) {
                    val name = doc.getString("name") ?: "bKash"
                    val number = doc.getString("number") ?: "01613238009"
                    val type = doc.getString("type") ?: "Personal"
                    val color = when {
                        name.contains("Nagad", ignoreCase = true) -> 0xFFF7941D
                        name.contains("Rocket", ignoreCase = true) -> 0xFF8C1D82
                        else -> 0xFFE2136E
                    }
                    list.add(DynamicPaymentMethod(doc.id, name, number, type, color))
                }
                if (list.isNotEmpty()) {
                    paymentMethodsList = list
                }
            }
        }
    }

    // Listen to user's personal pending requests
    LaunchedEffect(isLoggedIn) {
        val user = FirebaseAuth.getInstance().currentUser
        val db = FirebaseFirestore.getInstance()
        if (user != null && user.email != null) {
            try {
                db.collection("payment_requests")
                    .whereEqualTo("email", user.email!!.lowercase().trim())
                    .whereEqualTo("status", "PENDING")
                    .addSnapshotListener { snap, _ ->
                        if (snap != null && !snap.isEmpty) {
                            val doc = snap.documents.first()
                            pendingTrxId = doc.getString("trxId")
                            pendingPlanName = doc.getString("plan")
                        } else {
                            pendingTrxId = null
                            pendingPlanName = null
                        }
                    }

                // Listen to student verification status
                val cleanEmail = user.email!!.lowercase().trim()
                db.collection("student_verifications").document(user.uid)
                    .addSnapshotListener { doc, _ ->
                        if (doc != null && doc.exists()) {
                            studentVerificationStatus = doc.getString("status")
                        } else {
                            db.collection("student_verifications").document(cleanEmail)
                                .addSnapshotListener { emailDoc, _ ->
                                    if (emailDoc != null && emailDoc.exists()) {
                                        studentVerificationStatus = emailDoc.getString("status")
                                    }
                                }
                        }
                    }
            } catch (e: Exception) { }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(if (ThemeState.isDark.value) Color(0xFF101014) else Color(0xFFF7F7FA))) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(bottom = 60.dp)
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(if (ThemeState.isDark.value) Color.White.copy(0.1f) else Color.Black.copy(0.05f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textColor)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Bachelors Wallet PRO",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }

            // Hero PRO VIP Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF2A1B54), Color(0xFF180E33))
                        )
                    )
                    .border(
                        BorderStroke(1.5.dp, Brush.linearGradient(listOf(goldColor, Color(0xFF7B61FF)))),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(goldColor.copy(alpha = 0.2f))
                            .border(1.dp, goldColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = "PRO",
                            tint = goldColor,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (isPro) "YOU ARE A PRO MEMBER 👑" else "Upgrade to PRO",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = goldColor,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val expiryText = PremiumManager.proExpiryText.value
                    Text(
                        text = if (isPro) "Active Plan: $currentPlan\n$expiryText" else "Unlock Exclusive Themes, Auto Cloud Backup, Mess Pro & Unlimited Features",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    if (!isLoggedIn) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFF3B30).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFFFF3B30).copy(alpha = 0.5f)),
                            modifier = Modifier.clickable { navController.navigate("auth") }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Color(0xFFFF3B30), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Log In with Gmail to Save License 🔐", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Pending Verification Status Banner (If user submitted payment and waiting)
            if (pendingTrxId != null && !isPro) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFFF9500).copy(alpha = 0.12f),
                    border = BorderStroke(1.2.dp, Color(0xFFFF9500).copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF9500).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.HourglassTop, contentDescription = "Pending", tint = Color(0xFFFF9500), modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Payment Pending Verification ⏳", fontWeight = FontWeight.Bold, color = Color(0xFFFF9500), fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "TrxID: $pendingTrxId ($pendingPlanName)\nOur admin/moderator team is reviewing your payment. PRO will activate automatically!",
                                fontSize = 12.sp,
                                color = textColor.copy(alpha = 0.8f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Promo Code Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                color = darkCardBg,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocalOffer, contentDescription = null, tint = Color(0xFF7B61FF), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Have a Promo / Coupon Code?",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = couponInput,
                            onValueChange = {
                                couponInput = it.uppercase()
                                couponMessage = null
                            },
                            placeholder = { Text("Enter Promo Code", fontSize = 14.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF7B61FF),
                                unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor
                            )
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                val rawCode = couponInput.trim()
                                if (rawCode.isEmpty()) return@Button

                                PremiumManager.redeemCoupon(context, rawCode) { success, message ->
                                    isSuccessCoupon = success
                                    couponMessage = message
                                    if (success) {
                                        showConfetti = true
                                        couponInput = ""
                                    }
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B61FF)),
                            modifier = Modifier.height(54.dp)
                        ) {
                            Text("Apply", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    if (couponMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = couponMessage!!,
                            color = if (isSuccessCoupon) Color(0xFF34C759) else Color(0xFFFF3B30),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val publicPlans = plansList.filter { !it.isStudentOffer }
            val activeStudentOffer = plansList.firstOrNull { it.isStudentOffer }

            // Student Verification Special Banner Card (Shown only if admin created student offer or user applied)
            if (activeStudentOffer != null || studentVerificationStatus != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = if (ThemeState.isDark.value) Color(0xFF161F30) else Color(0xFFEFF6FF),
                    border = BorderStroke(1.2.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF38BDF8).copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.School, contentDescription = "Student", tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(activeStudentOffer?.title ?: "Student Verification Offer 🎓", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textColor)
                                Text(
                                    text = when (studentVerificationStatus) {
                                        "APPROVED" -> "✅ Verified Student Member (${activeStudentOffer?.title ?: "1-Year VIP"} Active)"
                                        "PENDING" -> "⏳ ID Card in Review by Admin"
                                        "REJECTED" -> "❌ Rejected. Tap to re-apply with clear photo"
                                        else -> if (activeStudentOffer != null) "${activeStudentOffer.price} ${activeStudentOffer.period} • ${activeStudentOffer.subtitle}" else "Verified students get 1-Year Free VIP PRO Access!"
                                    },
                                    fontSize = 12.sp,
                                    color = textColor.copy(alpha = 0.8f)
                                )
                            }
                        }

                        if (studentVerificationStatus != "APPROVED") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    if (!isLoggedIn) {
                                        Toast.makeText(context, "Please Log In with Gmail first 🔐", Toast.LENGTH_SHORT).show()
                                        navController.navigate("auth")
                                    } else {
                                        showStudentVerificationDialog = true
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (studentVerificationStatus == "PENDING") Color(0xFFFF9500) else Color(0xFF38BDF8)
                                ),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Text(
                                    text = if (studentVerificationStatus == "PENDING") "Under Review ⏳ Tap to Re-submit" else "Apply with Student ID Card 🎓",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Pricing Options Selector (Public Standard Plans)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Choose Your PRO Plan 💎",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )

                val displayPlans = if (publicPlans.isNotEmpty()) publicPlans else plansList
                displayPlans.forEachIndexed { index, plan ->
                    PlanCard(
                        title = plan.title,
                        price = plan.price,
                        period = plan.period,
                        subtitle = plan.subtitle,
                        isSelected = selectedPlanIndex == index,
                        tag = plan.tag,
                        tagColor = if (plan.tag?.contains("BEST", true) == true) goldColor else Color(0xFFFF9500),
                        textColor = textColor,
                        cardBg = darkCardBg,
                        onClick = { selectedPlanIndex = index }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Button
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                Button(
                    onClick = {
                        if (!isLoggedIn) {
                            Toast.makeText(context, "Please login first to connect your payment to your Gmail!", Toast.LENGTH_LONG).show()
                            navController.navigate("auth")
                        } else {
                            showPaymentDialog = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(12.dp, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPro) Color(0xFF34C759) else if (pendingTrxId != null) Color(0xFFFF9500) else Color(0xFF7B61FF)
                    )
                ) {
                    Icon(
                        imageVector = if (pendingTrxId != null && !isPro) Icons.Default.HourglassTop else Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPro) "PRO ACTIVATED (Tap to Manage / Upgrade)"
                               else if (pendingTrxId != null) "⏳ Verification Pending ($pendingTrxId)"
                               else "Subscribe via bKash / Nagad / Rocket 🚀",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // PRO Features Checklist
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                color = darkCardBg,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Everything Included in PRO ✨",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    ProFeatureRow(icon = Icons.Outlined.AutoAwesome, title = "Bachelors AI Financial Agent 🤖", desc = "Real-time intelligent spending analysis, saving tips & debt insights", textColor = textColor)
                    ProFeatureRow(icon = Icons.Outlined.Palette, title = "Exclusive Premium Themes 🎨", desc = "OLED Midnight, Royal Gold, Ocean Sapphire & Emerald", textColor = textColor)
                    ProFeatureRow(icon = Icons.Outlined.CloudSync, title = "Silent Automatic Cloud Backup ☁️", desc = "Real-time multi-device cloud sync on every transaction", textColor = textColor)
                    ProFeatureRow(icon = Icons.Outlined.Groups, title = "Mess & Roommate Pro 🏠", desc = "Live meal rate calculation, bazaar splitting & WhatsApp summary", textColor = textColor)
                    ProFeatureRow(icon = Icons.Outlined.QueryStats, title = "Advanced Analytics & Reports 📊", desc = "Deep category trends and custom period comparisons", textColor = textColor)
                    ProFeatureRow(icon = Icons.Outlined.Description, title = "Instant PDF & Excel Export 📑", desc = "Export monthly expense reports and receipts for accounting", textColor = textColor)
                    ProFeatureRow(icon = Icons.Outlined.Fingerprint, title = "Biometric & App PIN Lock 🛡️", desc = "Maximum security for your private personal finances", textColor = textColor)
                    ProFeatureRow(icon = Icons.Outlined.Block, title = "100% Ad-Free Experience 🚫", desc = "Distraction-free pure budgeting experience forever", textColor = textColor)
                }
            }
        }

        if (showConfetti) {
            ConfettiExplosion(
                trigger = showConfetti,
                onFinished = { showConfetti = false }
            )
        }

        // ================= 💳 BKASH, NAGAD & ROCKET PAYMENT & TRXID VERIFICATION MODAL =================
        if (showPaymentDialog) {
            var selectedMethodIndex by remember { mutableIntStateOf(0) }
            var senderPhoneInput by remember { mutableStateOf("") }
            var trxIdInput by remember { mutableStateOf("") }
            var isVerifying by remember { mutableStateOf(false) }
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            val currentMethod = paymentMethodsList.getOrNull(selectedMethodIndex) ?: paymentMethodsList.first()

            val activePublicPlans = plansList.filter { !it.isStudentOffer }.ifEmpty { plansList }
            val selectedPlan = activePublicPlans.getOrElse(selectedPlanIndex.coerceIn(0, activePublicPlans.size - 1)) { activePublicPlans[0] }
            val planTitle = selectedPlan.title
            val planAmount = selectedPlan.price
            val durationDays = selectedPlan.durationDays.toLong()

            Dialog(
                onDismissRequest = { showPaymentDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = darkCardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .heightIn(max = 720.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("bKash, Nagad & Rocket Payment 💳", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                                Text("Selected: $planTitle ($planAmount)", fontSize = 13.sp, color = Color(0xFF7B61FF), fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = { showPaymentDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Dynamic Payment Methods Selector (Loaded from Admin Dashboard / Firestore)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            paymentMethodsList.forEachIndexed { index, method ->
                                PaymentMethodChip(
                                    title = method.name,
                                    isSelected = selectedMethodIndex == index,
                                    activeColor = Color(method.colorHex),
                                    modifier = Modifier.weight(1f)
                                ) { selectedMethodIndex = index }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Instructions Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (ThemeState.isDark.value) Color(0xFF282832) else Color(0xFFF3F4F6),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Send Money Instructions (টাকা পাঠানোর নিয়ম):",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "১. আপনার ${currentMethod.name} অ্যাপ থেকে 'Send Money' অপশনে যান।\n২. নিচে দেওয়া ${currentMethod.type} নাম্বারে ঠিক $planAmount টাকা পাঠান।\n৩. এসএমএস বা অ্যাপ থেকে প্রাপ্ত Transaction ID (TrxID) নিচে লিখে 'Verify & Activate PRO' বাটনে চাপ দিন।",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (ThemeState.isDark.value) Color(0xFF1E1E24) else Color.White)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("${currentMethod.name} (${currentMethod.type}):", fontSize = 11.sp, color = Color.Gray)
                                        Text(currentMethod.number, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(currentMethod.colorHex))
                                    }
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setPrimaryClip(ClipData.newPlainText("Number", currentMethod.number))
                                            Toast.makeText(context, "Copied ${currentMethod.number} to clipboard!", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color(0xFF7B61FF), modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Form Inputs
                        OutlinedTextField(
                            value = senderPhoneInput,
                            onValueChange = { senderPhoneInput = it },
                            label = { Text("Your Phone (যে নাম্বার থেকে টাকা পাঠিয়েছেন)") },
                            placeholder = { Text("017XXXXXXXX") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = trxIdInput,
                            onValueChange = { trxIdInput = it.uppercase() },
                            label = { Text("Transaction ID / TrxID") },
                            placeholder = { Text("e.g. BL82X9A19P") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Submit & Auto-Verify Button
                        Button(
                            onClick = {
                                val senderPhone = senderPhoneInput.trim()
                                val trxId = trxIdInput.trim().uppercase()
                                val authUser = FirebaseAuth.getInstance().currentUser

                                if (authUser == null) {
                                    Toast.makeText(context, "Please login with your Gmail first!", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                if (senderPhone.length < 10 || trxId.length < 5) {
                                    Toast.makeText(context, "Please enter a valid Phone Number and Transaction ID.", Toast.LENGTH_LONG).show()
                                    return@Button
                                }

                                isVerifying = true
                                val db = FirebaseFirestore.getInstance()

                                // Step 1: Check TrxID Ledger in 'valid_transactions'
                                db.collection("valid_transactions").document(trxId).get()
                                    .addOnSuccessListener { doc ->
                                        if (doc != null && doc.exists()) {
                                            val isUsed = doc.getBoolean("used") ?: false
                                            if (isUsed) {
                                                isVerifying = false
                                                Toast.makeText(context, "⚠️ This Transaction ID has already been redeemed by another account.", Toast.LENGTH_LONG).show()
                                                return@addOnSuccessListener
                                            }

                                            // MATCH FOUND! Auto-Redeem & Instant Unlock
                                            val expiryTimestamp = if (durationDays > 0) {
                                                System.currentTimeMillis() + durationDays * 24L * 60L * 60L * 1000L
                                            } else {
                                                -1L
                                            }

                                            // Mark TrxID as used
                                            db.collection("valid_transactions").document(trxId).set(
                                                mapOf(
                                                    "used" to true,
                                                    "usedBy" to (authUser.email ?: ""),
                                                    "usedAt" to System.currentTimeMillis()
                                                ),
                                                SetOptions.merge()
                                            )

                                            // Activate PRO
                                            PremiumManager.activatePro(
                                                context = context,
                                                plan = planTitle,
                                                coupon = trxId,
                                                expiryTimestamp = expiryTimestamp
                                            )

                                            // Also log in payment_requests as APPROVED
                                            val reqId = "TRX_${System.currentTimeMillis()}"
                                            db.collection("payment_requests").document(reqId).set(
                                                hashMapOf(
                                                    "id" to reqId,
                                                    "email" to (authUser.email ?: ""),
                                                    "uid" to authUser.uid,
                                                    "plan" to planTitle,
                                                    "amount" to planAmount,
                                                    "durationDays" to durationDays,
                                                    "method" to currentMethod.name,
                                                    "senderPhone" to senderPhone,
                                                    "trxId" to trxId,
                                                    "status" to "APPROVED",
                                                    "timestamp" to System.currentTimeMillis()
                                                ),
                                                SetOptions.merge()
                                            )

                                            isVerifying = false
                                            showPaymentDialog = false
                                            showConfetti = true
                                            Toast.makeText(context, "🎉 Transaction Verified! Welcome to Bachelors Wallet PRO!", Toast.LENGTH_LONG).show()
                                        } else {
                                            // Step 2: TrxID not yet in admin ledger -> Save to 'payment_requests' for pending verification
                                            val reqId = "REQ_${System.currentTimeMillis()}"
                                            val requestData = hashMapOf(
                                                "id" to reqId,
                                                "email" to (authUser.email ?: ""),
                                                "uid" to authUser.uid,
                                                "plan" to planTitle,
                                                "amount" to planAmount,
                                                "durationDays" to durationDays,
                                                "method" to currentMethod.name,
                                                "senderPhone" to senderPhone,
                                                "trxId" to trxId,
                                                "status" to "PENDING",
                                                "timestamp" to System.currentTimeMillis()
                                            )

                                            db.collection("payment_requests").document(reqId).set(requestData, SetOptions.merge())
                                                .addOnSuccessListener {
                                                    isVerifying = false
                                                    showPaymentDialog = false
                                                    pendingTrxId = trxId
                                                    pendingPlanName = planTitle
                                                    Toast.makeText(context, "📩 Payment Submitted! TrxID ($trxId) is recorded as Pending.", Toast.LENGTH_LONG).show()
                                                }
                                                .addOnFailureListener { e ->
                                                    isVerifying = false
                                                    Toast.makeText(context, "Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                }
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        isVerifying = false
                                        Toast.makeText(context, "Verification error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                            },
                            enabled = !isVerifying,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B61FF))
                        ) {
                            if (isVerifying) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Verifying TrxID...", color = Color.White, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Verify & Activate PRO 🚀", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        if (showStudentVerificationDialog) {
            StudentVerificationDialog(
                onDismiss = { showStudentVerificationDialog = false },
                isSubmitting = isSubmittingStudentId,
                onSubmit = { institution, studentIdNum, imageBase64 ->
                    isSubmittingStudentId = true
                    val currentUser = FirebaseAuth.getInstance().currentUser
                    val cleanEmail = currentUser?.email?.lowercase()?.trim() ?: ""
                    val data = hashMapOf(
                        "userId" to (currentUser?.uid ?: ""),
                        "userEmail" to cleanEmail,
                        "institution" to institution,
                        "studentId" to studentIdNum,
                        "idCardImage" to imageBase64,
                        "status" to "PENDING",
                        "appliedAt" to System.currentTimeMillis()
                    )
                    val docKey = currentUser?.uid ?: cleanEmail
                    val db = FirebaseFirestore.getInstance()
                    db.collection("student_verifications").document(docKey)
                        .set(data, SetOptions.merge())
                        .addOnSuccessListener {
                            isSubmittingStudentId = false
                            showStudentVerificationDialog = false
                            studentVerificationStatus = "PENDING"
                            Toast.makeText(context, "🎉 Student Verification Submitted! Admin will review your ID card.", Toast.LENGTH_LONG).show()
                        }
                        .addOnFailureListener { e ->
                            isSubmittingStudentId = false
                            Toast.makeText(context, "Submission failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            )
        }
    }
}

@Composable
fun PaymentMethodChip(
    title: String,
    isSelected: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) activeColor.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.08f),
        border = BorderStroke(1.5.dp, if (isSelected) activeColor else Color.Transparent),
        modifier = modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) activeColor else Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PlanCard(
    title: String,
    price: String,
    period: String,
    subtitle: String,
    isSelected: Boolean,
    tag: String?,
    tagColor: Color = Color(0xFF7B61FF),
    textColor: Color,
    cardBg: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Color(0xFF7B61FF).copy(alpha = 0.08f) else cardBg,
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) Color(0xFF7B61FF) else if (ThemeState.isDark.value) Color.White.copy(0.08f) else Color.Black.copy(0.05f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Tag / Title & RadioButton
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    if (tag != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = tagColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = tag,
                                color = tagColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF7B61FF))
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle Row (Full Width)
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color.Gray,
                lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Price Row
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = price,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSelected) Color(0xFF7B61FF) else textColor
                )
                Text(
                    text = " $period",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
fun ProFeatureRow(icon: ImageVector, title: String, desc: String, textColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF7B61FF).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF7B61FF), modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
            Text(text = desc, fontSize = 12.sp, color = Color.Gray)
        }
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(20.dp))
    }
}

@Composable
fun StudentVerificationDialog(
    onDismiss: () -> Unit,
    onSubmit: (institution: String, studentId: String, imageBase64: String) -> Unit,
    isSubmitting: Boolean
) {
    var institution by remember { mutableStateOf("") }
    var studentIdNumber by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBase64String by remember { mutableStateOf("") }
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val maxDim = 800
                    val ratio = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height, 1.0f)
                    val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
                    val outputStream = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
                    val bytes = outputStream.toByteArray()
                    imageBase64String = "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Could not process image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (ThemeState.isDark.value) Color(0xFF181C28) else Color.White,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.School, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Student Verification 🎓", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Submit your College / University Student ID to unlock 1-Year Free Student VIP Access.",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = institution,
                    onValueChange = { institution = it },
                    label = { Text("College / University Name") },
                    placeholder = { Text("e.g. Dhaka University / BUET / NSU") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = studentIdNumber,
                    onValueChange = { studentIdNumber = it },
                    label = { Text("Student ID / Roll Number") },
                    placeholder = { Text("e.g. 2021-12345") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Image Upload Box
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF38BDF8).copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { imagePickerLauncher.launch("image/*") }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (selectedImageUri != null) "✅ Photo Selected (Tap to change)" else "📷 Tap to Select Student ID Photo",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF38BDF8)
                        )
                        Text("Clear photo of Student ID card or admit card", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (institution.trim().isEmpty() || studentIdNumber.trim().isEmpty()) {
                            Toast.makeText(context, "Please fill in all details", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (imageBase64String.isEmpty()) {
                            Toast.makeText(context, "Please select your student ID photo", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onSubmit(institution.trim(), studentIdNumber.trim(), imageBase64String)
                    },
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Submitting Verification...", color = Color.White)
                    } else {
                        Text("Submit Verification 🚀", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
