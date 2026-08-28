package com.example.myapplication

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// --- COLORS ---
val PremiumSlate = Color(0xFF0F172A)
val PremiumSlateLight = Color(0xFF1E293B)
val SoftRose = Color(0xFFF43F5E)
val EmeraldGreen = Color(0xFF10B981)
val AppBgLight = Color(0xFFF8F9FA)
val AppBgDark = Color(0xFF121212)

enum class ExpenseEntryMode { ADD, SUBTRACT, SET_TOTAL }

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        PremiumManager.initialize(this)
        MessManager.initialize(this)
        GeminiAiClient.initConfig()

        val savedTheme = prefs.getString("theme_mode", "System") ?: "System"
        val systemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        ThemeState.applyTheme(this, savedTheme, systemDark)

        setContent {
            MaterialTheme(colorScheme = if (ThemeState.isDark.value) darkColorScheme() else lightColorScheme()) {
                MainApp()
            }
        }
    }
}

@TargetApi(Build.VERSION_CODES.CUPCAKE)
@Composable
fun MainApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val globalBgColor = if (ThemeState.isDark.value) AppBgDark else AppBgLight
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val view = LocalView.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    if (!view.isInEditMode) {
        LaunchedEffect(ThemeState.isDark.value, drawerState.isOpen, currentRoute) {
            val activity = view.context as? Activity
            activity?.window?.let { window ->
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                if (drawerState.isOpen) {
                    // Force white status bar icons on the drawer's purple gradient header
                    insetsController.isAppearanceLightStatusBars = false
                } else {
                    if (currentRoute == "home") {
                        // Force white status bar icons on the home screen's purple header
                        insetsController.isAppearanceLightStatusBars = false
                    } else {
                        // Standard theme-based status bar icons for other screens
                        insetsController.isAppearanceLightStatusBars = !ThemeState.isDark.value
                    }
                }
                insetsController.isAppearanceLightNavigationBars = !ThemeState.isDark.value
            }
        }
    }

    val activity = context as? Activity
    LaunchedEffect(activity?.intent) {
        val openTab = activity?.intent?.getStringExtra("OPEN_TAB")
        if (openTab == "NOTIFICATIONS") {
            navController.navigate("notifications") { launchSingleTop = true }
            activity.intent?.removeExtra("OPEN_TAB")
        }
    }

    LaunchedEffect(Unit) {
        DataManager.processScheduledTransactions(context)
    }

    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        // Auto-check GitHub Releases for newer version on launch
        delay(1200)
        try {
            val update = AppUpdateManager.checkForUpdates(context)
            if (update != null && update.hasUpdate) {
                availableUpdate = update
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("first_time_permission_asked", true) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                delay(1500)
                (context as? Activity)?.let { ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101) }
                prefs.edit().putBoolean("first_time_permission_asked", false).apply()
            }
        }
    }

    // 🌟 HIGHLIGHT: 3 Lines Menu (Drawer) with Profile and Settings
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = if (ThemeState.isDark.value) ThemeState.cardBackground.value else Color.White,
                modifier = Modifier.width(300.dp),
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
            ) {
                val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val isPro = PremiumManager.isProUser.value
                val primaryColor = ThemeState.primaryAccent.value
                val isDark = ThemeState.isDark.value
                val itemTextColor = if (isDark) Color.White else Color(0xFF1E1E24)

                // --- STUNNING GRADIENT DRAWER HEADER ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ThemeState.headerGradient.value)
                        .statusBarsPadding()
                        .padding(horizontal = 22.dp, vertical = 26.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (authUser?.photoUrl != null) {
                                    androidx.compose.foundation.Image(
                                        painter = coil.compose.rememberAsyncImagePainter(authUser.photoUrl),
                                        contentDescription = "Avatar",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            if (isPro) {
                                Surface(
                                    color = Color(0xFFFFD700).copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(50.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFFD700))
                                ) {
                                    Text(
                                        text = "👑 PRO",
                                        color = Color(0xFFFFD700),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = authUser?.displayName ?: "Bachelors Wallet",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = authUser?.email ?: "Smart Student Finances",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null, tint = primaryColor) },
                    label = { Text("Home", fontWeight = FontWeight.SemiBold) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedIconColor = primaryColor,
                        unselectedTextColor = itemTextColor
                    )
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = null, tint = primaryColor) },
                    label = { Text("Profile", fontWeight = FontWeight.SemiBold) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        navController.navigate("profile") { launchSingleTop = true }
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedIconColor = primaryColor,
                        unselectedTextColor = itemTextColor
                    )
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = primaryColor) },
                    label = { Text("Repeating Bills", fontWeight = FontWeight.SemiBold) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        navController.navigate("scheduled") { launchSingleTop = true }
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedIconColor = primaryColor,
                        unselectedTextColor = itemTextColor
                    )
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color(0xFFFFD700)) },
                    label = { Text("Bachelors PRO 👑", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        navController.navigate("subscription") { launchSingleTop = true }
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedIconColor = Color(0xFFFFD700),
                        unselectedTextColor = Color(0xFFFFD700)
                    )
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = primaryColor) },
                    label = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        navController.navigate("settings") { launchSingleTop = true }
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedIconColor = primaryColor,
                        unselectedTextColor = itemTextColor
                    )
                )

                Spacer(modifier = Modifier.weight(1f))

                val currentAppVersion = remember(context) {
                    try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.1.0"
                    } catch (e: Exception) {
                        "2.1.0"
                    }
                }

                // Footer version
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "Version $currentAppVersion • Bachelors Wallet",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize().background(ThemeState.background.value)) {
            NavHost(
                navController = navController, startDestination = "home", modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing)) },
                exitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing)) },
                popEnterTransition = { fadeIn(animationSpec = androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing)) },
                popExitTransition = { fadeOut(animationSpec = androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
            ) {
                composable("home") { BudgetPlannerScreen(navController, drawerState) }
                composable("budget") { BudgetScreen() }
                composable("debt") { DebtManagerScreen() }
                composable("reports") { ReportsScreen() }
                composable("mess") { MessManagerScreen(navController) }
                composable("mess_analysis") { MessAnalysisScreen(navController) }
                composable("ai_assistant") { AiAssistantScreen(navController) }
                composable("subscription") { SubscriptionScreen(navController) }
                composable("auth") { AuthScreen(onAuthSuccess = { navController.navigate("home") { popUpTo("home") { inclusive = true } } }) }
                composable("notifications") { NotificationScreen(navController) }
                composable("profile") { ProfileScreen(navController) }
                composable("scheduled") { ScheduledTransactionsScreen() }
                composable("settings") { SettingsScreen(navController) }
            }
            val isMainTab = currentRoute in listOf("home", "budget", "debt", "reports", "mess")
            if (isMainTab) {
                FloatingNavBar(navController = navController, modifier = Modifier.align(Alignment.BottomCenter))
            }

            availableUpdate?.let { update ->
                InAppUpdateDialog(
                    updateInfo = update,
                    onDismiss = { availableUpdate = null }
                )
            }
        }
    }
}

@Composable
fun FloatingNavBar(navController: NavController, modifier: Modifier = Modifier) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isDark = ThemeState.isDark.value
    val navBarColor = ThemeState.cardBackground.value
    val borderColor = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f)

    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    val showMess = prefs.getBoolean("pref_show_mess_in_navbar", true)

    Surface(
        color = navBarColor,
        shadowElevation = 16.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = borderColor, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 🌟 REQUESTED ORDER: 1. Home, 2. Budget, 3. Debt, 4. Reports, 5. Mess
                NavItem(icon = Icons.Default.Home, title = "Home", isSelected = currentRoute == "home") { if (currentRoute != "home") navController.navigate("home") { launchSingleTop = true } }
                NavItem(icon = Icons.Default.ShoppingCart, title = "Budget", isSelected = currentRoute == "budget") { if (currentRoute != "budget") navController.navigate("budget") { launchSingleTop = true } }
                NavItem(icon = Icons.Default.AccountBalanceWallet, title = "Debt", isSelected = currentRoute == "debt") { if (currentRoute != "debt") navController.navigate("debt") { launchSingleTop = true } }
                NavItem(icon = Icons.Default.PieChart, title = "Reports", isSelected = currentRoute == "reports") { if (currentRoute != "reports") navController.navigate("reports") { launchSingleTop = true } }
                if (showMess) {
                    NavItem(icon = Icons.Default.Groups, title = "Mess", isSelected = currentRoute == "mess") {
                        if (currentRoute != "mess") {
                            if (!PremiumManager.isProUser.value) {
                                Toast.makeText(context, "👑 Mess Manager is a PRO Feature!", Toast.LENGTH_SHORT).show()
                                navController.navigate("subscription") { launchSingleTop = true }
                            } else {
                                navController.navigate("mess") { launchSingleTop = true }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.NavItem(icon: ImageVector, title: String, isSelected: Boolean, onClick: () -> Unit) {
    val highlightColor = ThemeState.primaryAccent.value
    val contentColor by animateColorAsState(if (isSelected) highlightColor else Color.Gray, tween(300), label = "")
    val capsuleAlpha by animateFloatAsState(if (isSelected) 0.12f else 0f, tween(300), label = "")

    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .width(54.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(highlightColor.copy(alpha = capsuleAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = contentColor, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = title, color = contentColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 10.sp, maxLines = 1)
    }
}

enum class MetricDetailType {
    INCOME,
    SPENT,
    I_OWE,
    THEY_OWE
}

@Composable
fun SummaryMetricBlock(
    icon: ImageVector,
    label: String,
    value: Double,
    color: Color,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDark = ThemeState.isDark.value
    val textColor = if (isDark) Color.White else PremiumSlate
    val bgColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF1F3F5)

    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = modifier.height(65.dp),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = if (!isDark) BorderStroke(1.dp, Color.LightGray.copy(0.2f)) else null
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(color.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                    if (onClick != null) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                    }
                }
                Spacer(modifier = Modifier.height(1.dp))
                Text("৳${String.format("%,.0f", value)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@TargetApi(Build.VERSION_CODES.CUPCAKE)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetPlannerScreen(navController: NavController, drawerState: DrawerState) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    val isDark = ThemeState.isDark.value
    val view = LocalView.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val isCurrentScreen = navBackStackEntry?.destination?.route == "home"



    val allTransactions by DataManager.getTransactionsFlow(context).collectAsState(initial = DataManager.cachedTransactions ?: emptyList())
    val allDebts by DataManager.getDebtsFlow(context).collectAsState(initial = DataManager.cachedDebts ?: emptyList())
    val notifications by DataManager.getNotificationsFlow(context).collectAsState(initial = emptyList())
    val hasUnreadNotifs = notifications.any { !it.isRead }

    val thisMonthTransactions = remember(allTransactions) {
        val cal = Calendar.getInstance()
        val curMonth = cal.get(Calendar.MONTH)
        val curYear = cal.get(Calendar.YEAR)
        allTransactions.filter {
            val tCal = Calendar.getInstance().apply { time = it.date }
            tCal.get(Calendar.MONTH) == curMonth && tCal.get(Calendar.YEAR) == curYear
        }
    }

    val totalReceived = remember(thisMonthTransactions) { thisMonthTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount } }
    val totalSpent = remember(thisMonthTransactions) { thisMonthTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount } }

    val includeDebt = prefs.getBoolean("pref_include_debt_in_balance", true)
    val currentBalance = remember(allTransactions, allDebts, includeDebt) {
        ExpenseCalculator.getThisMonthBalance(context, allTransactions, allDebts)
    }

    val totalIOwe = remember(allDebts) { allDebts.filter { it.type == DebtType.I_OWE && !it.isPaid && !it.isArchived }.sumOf { it.remainingAmount } }
    val totalTheyOwe = remember(allDebts) { allDebts.filter { it.type == DebtType.THEY_OWE && !it.isPaid && !it.isArchived }.sumOf { it.remainingAmount } }

    val dayKey = System.currentTimeMillis() / (24 * 60 * 60 * 1000L)
    val todayTransactions = remember(allTransactions, dayKey) {
        val todayCal = Calendar.getInstance()
        allTransactions.filter {
            val expCal = Calendar.getInstance().apply { time = it.date }
            todayCal.get(Calendar.YEAR) == expCal.get(Calendar.YEAR) && todayCal.get(Calendar.DAY_OF_YEAR) == expCal.get(Calendar.DAY_OF_YEAR)
        }
    }

    val todaySpent = remember(todayTransactions) {
        todayTransactions.filter { it.type == TransactionType.EXPENSE && !it.category.contains("Mess", ignoreCase = true) && !it.id.startsWith("mess_") }.sumOf { it.amount }
    }
    val daysRemaining = remember(dayKey) {
        val cal = Calendar.getInstance()
        maxOf(1, cal.getActualMaximum(Calendar.DAY_OF_MONTH) - cal.get(Calendar.DAY_OF_MONTH) + 1)
    }
    val safeDailySpend = remember(currentBalance, todaySpent, daysRemaining) { if (currentBalance > 0) (currentBalance + todaySpent) / daysRemaining else 0.0 }
    val dailyProgress = remember(safeDailySpend, todaySpent) { if (safeDailySpend > 0) (todaySpent / safeDailySpend).toFloat().coerceIn(0f, 1f) else 0f }

    var selectedMetricDetail by remember { mutableStateOf<MetricDetailType?>(null) }

    val homeInfiniteAnim = rememberInfiniteTransition(label = "home_ambient")
    val homePulseScale by homeInfiniteAnim.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val homeGlowAlpha by homeInfiniteAnim.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val homeParticlePhase by homeInfiniteAnim.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6500, easing = LinearEasing), RepeatMode.Restart),
        label = "particle"
    )
    val homeWavePhase by homeInfiniteAnim.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3800, easing = LinearEasing), RepeatMode.Restart),
        label = "wave"
    )

    val criticalDebts = remember(allDebts) {
        val now = System.currentTimeMillis()
        val twoDaysInMillis = 2L * 24 * 60 * 60 * 1000
        allDebts.filter { !it.isPaid && !it.isArchived && it.deadline != null && (it.deadline!!.time - now) <= twoDaysInMillis }
            .sortedBy { it.deadline!!.time }
    }

    var showAddScreen by remember { mutableStateOf(false) }
    var isExpenseForm by remember { mutableStateOf(true) }

    val transactionFocusRequester = remember { FocusRequester() }
    var transactionInput by remember { mutableStateOf(TextFieldValue("")) }
    var transactionDate by remember { mutableStateOf(Date()) }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAllCategoriesPicker by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }

    val customCategories by DataManager.getCategoriesFlow(context).collectAsState(initial = emptyList())

    val availableCategories = remember(customCategories, showAddScreen) {
        DataManager.getActiveCategories(context, customCategories)
    }

    var selectedCategoryName by remember { mutableStateOf("") }

    fun isSameCategory(cat1: String, cat2: String): Boolean {
        val clean1 = cat1.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
        val clean2 = cat2.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
        return clean1 == clean2 || cat1.equals(cat2, ignoreCase = true)
    }

    // 🌟 Date-Specific Dynamic Categories (Persists any extra categories used on this specific date)
    val dateSpecificCategories = remember(transactionDate, allTransactions, availableCategories, selectedCategoryName) {
        val cal1 = Calendar.getInstance().apply { time = transactionDate }
        val txCatsOnDate = allTransactions.filter {
            val cal2 = Calendar.getInstance().apply { time = it.date }
            cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR) &&
                    it.type == TransactionType.EXPENSE &&
                    it.category.isNotBlank() && it.category != "Income"
        }.map { it.category }

        val list = mutableListOf<String>()
        // 1. Add active categories from settings
        availableCategories.forEach { if (!list.any { existing -> isSameCategory(existing, it) }) list.add(it) }
        // 2. Add categories that have transactions on this date
        txCatsOnDate.forEach { if (!list.any { existing -> isSameCategory(existing, it) }) list.add(it) }
        // 3. If user just selected a category (e.g. from More...), keep it in the list!
        if (selectedCategoryName.isNotBlank() && !list.any { existing -> isSameCategory(existing, selectedCategoryName) }) {
            list.add(selectedCategoryName)
        }
        list
    }

    LaunchedEffect(dateSpecificCategories, isExpenseForm, showAddScreen) {
        if (showAddScreen && dateSpecificCategories.isNotEmpty()) {
            if (selectedCategoryName.isBlank() || !dateSpecificCategories.any { isSameCategory(it, selectedCategoryName) }) {
                selectedCategoryName = dateSpecificCategories[0]
            }
        }
    }

    var entryMode by remember { mutableStateOf(ExpenseEntryMode.ADD) }

    fun getCategoryDisplay(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return ""
        if (!DataManager.isShowEmojisEnabled(context)) {
            return DataManager.stripEmoji(trimmed)
        }
        val hasEmoji = trimmed.any { Character.getType(it) == Character.SURROGATE.toInt() || it.code > 0x2000 }
        if (hasEmoji) return trimmed
        val icon = when (trimmed.lowercase()) {
            "breakfast" -> "☕"
            "lunch" -> "🍱"
            "dinner" -> "🍽️"
            "snacks", "food" -> "🍔"
            "transport", "bus" -> "🚌"
            "shopping", "clothes" -> "🛍️"
            "bills", "utility" -> "🧾"
            "health", "medicine" -> "💊"
            "education", "books" -> "📚"
            "rent", "house" -> "🏠"
            "others" -> "🏷️"
            else -> "🏷️"
        }
        return "$icon $trimmed"
    }

    val textColor = if (ThemeState.isDark.value) Color.White else Color(0xFF1C1C1E)
    val purpleBg = ThemeState.headerGradient.value
    val cardColor = ThemeState.cardBackground.value
    val primaryAccent = ThemeState.primaryAccent.value

    var existingTxId by remember { mutableStateOf<String?>(null) }
    var existingAmount by remember { mutableStateOf(0.0) }
    var isExplicitEditMode by remember { mutableStateOf(false) }

    LaunchedEffect(isExpenseForm, selectedCategoryName, transactionDate, showAddScreen, allTransactions) {
        if (showAddScreen && !isExplicitEditMode) {
            val cal1 = Calendar.getInstance().apply { time = transactionDate }
            val targetType = if (isExpenseForm) TransactionType.EXPENSE else TransactionType.INCOME
            val targetCat = if (isExpenseForm) selectedCategoryName else "Income"

            // Find existing transaction to show Add / Edit mode
            val targetTx = allTransactions.find {
                val cal2 = Calendar.getInstance().apply { time = it.date }
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR) &&
                        it.type == targetType && isSameCategory(it.category, targetCat)
            }

            if (targetTx != null) {
                existingTxId = targetTx.id
                existingAmount = targetTx.amount
                entryMode = ExpenseEntryMode.ADD
                transactionInput = TextFieldValue("")
            } else {
                existingTxId = null
                existingAmount = 0.0
                entryMode = ExpenseEntryMode.SET_TOTAL
                transactionInput = TextFieldValue("")
            }
            delay(100)
        } else if (!showAddScreen) {
            isExplicitEditMode = false
        }
    }

    LaunchedEffect(successMessage) { if (successMessage.isNotEmpty()) { delay(2500); successMessage = "" } }

    fun parseTerm(expr: String): Double {
        val parts = expr.split('*')
        var prod = 1.0
        for (i in parts.indices) {
            val divs = parts[i].split('/')
            var res = divs[0].trim().toDoubleOrNull() ?: 0.0
            for (j in 1 until divs.size) { res /= (divs[j].trim().toDoubleOrNull() ?: 1.0).let { if (it == 0.0) 1.0 else it } }
            if (i == 0) prod = res else prod *= res
        }
        return prod
    }

    fun evaluateExpression(expr: String): Double {
        var str = expr.replace(" ", "")
        if (str.isEmpty()) return 0.0
        if (str.startsWith("-")) str = "0$str"
        return try {
            val adds = str.split('+')
            var sum = 0.0
            for (add in adds) {
                if (add.isEmpty()) continue
                val subs = add.split('-')
                var subSum = parseTerm(subs[0])
                for (i in 1 until subs.size) subSum -= parseTerm(subs[i])
                sum += subSum
            }
            sum
        } catch (e: Exception) { str.toDoubleOrNull() ?: 0.0 }
    }

    fun handleSave(closeSheet: Boolean = true) {
        if (transactionInput.text.trim().isEmpty()) return
        val rawInput = evaluateExpression(transactionInput.text)
        if (rawInput < 0) return

        val type = if (!isExpenseForm) TransactionType.INCOME else TransactionType.EXPENSE
        val category = if (!isExpenseForm) "Income" else selectedCategoryName

        val finalAmount = if (existingAmount > 0) {
            when (entryMode) {
                ExpenseEntryMode.ADD -> existingAmount + rawInput
                ExpenseEntryMode.SUBTRACT -> maxOf(0.0, existingAmount - rawInput)
                ExpenseEntryMode.SET_TOTAL -> rawInput
            }
        } else {
            rawInput
        }

        if (finalAmount == 0.0 && existingTxId != null) {
            // Delete entry
            val dummyEntry = TransactionEntry(id = existingTxId!!, date = transactionDate, type = type, category = category, amount = existingAmount)
            DataManager.deleteTransaction(context, dummyEntry)
            if (closeSheet) successMessage = "Removed $category entry"
            existingTxId = null
            existingAmount = 0.0
            transactionInput = TextFieldValue("")
        } else if (finalAmount > 0) {
            val newEntry = TransactionEntry(
                id = existingTxId ?: UUID.randomUUID().toString(),
                date = transactionDate,
                type = type,
                category = category,
                amount = finalAmount
            )
            DataManager.saveTransaction(context, newEntry)
            existingAmount = finalAmount
            if (closeSheet) {
                successMessage = if (existingAmount > 0 && entryMode != ExpenseEntryMode.SET_TOTAL) {
                    if (entryMode == ExpenseEntryMode.ADD) "+৳${rawInput.toInt()} added! (Total: ৳${finalAmount.toInt()})"
                    else "-৳${rawInput.toInt()} deducted! (Total: ৳${finalAmount.toInt()})"
                } else {
                    "৳${finalAmount.toInt()} saved in $category!"
                }
            }
            transactionInput = TextFieldValue("")
        }

        if (closeSheet) {
            focusManager.clearFocus()
            showAddScreen = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(ThemeState.background.value)) {

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

            Box(modifier = Modifier.fillMaxWidth()) {
                // Dynamic Animated Dual-Layer Wave Background (Extended Height)
                Canvas(modifier = Modifier.fillMaxWidth().height(345.dp)) {
                    val w = size.width
                    val h = size.height
                    val baseWaveY = h - 35f
                    val waveAmp = 18f

                    // Layer 1: Lighter secondary wave for depth
                    val path1 = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(0f, baseWaveY)
                        var x = 0f
                        val step = 8f
                        while (x <= w) {
                            val y = baseWaveY + waveAmp * kotlin.math.sin((x / w * 2.0 * Math.PI + homeWavePhase + 1.2)).toFloat()
                            lineTo(x, y)
                            x += step
                        }
                        lineTo(w, 0f)
                        close()
                    }
                    drawPath(path1, brush = purpleBg, alpha = 0.45f)

                    // Layer 2: Main foreground solid wave
                    val path2 = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(0f, baseWaveY)
                        var x = 0f
                        val step = 8f
                        while (x <= w) {
                            val y = baseWaveY + waveAmp * kotlin.math.sin((x / w * 2.0 * Math.PI + homeWavePhase)).toFloat()
                            lineTo(x, y)
                            x += step
                        }
                        lineTo(w, 0f)
                        close()
                    }
                    drawPath(path2, brush = purpleBg)
                }

                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, "Menu", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Dashboard", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                Text(SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault()).format(Date()), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // AI Financial Agent Quick Button
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFF7B61FF))))
                                    .clickable {
                                        if (!PremiumManager.isProUser.value) {
                                            Toast.makeText(context, "👑 AI Financial Agent is a PRO Feature!", Toast.LENGTH_SHORT).show()
                                            navController.navigate("subscription")
                                        } else {
                                            navController.navigate("ai_assistant")
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AutoAwesome, "AI Financial Agent", tint = Color.White, modifier = Modifier.size(20.dp))
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).clickable { navController.navigate("notifications") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Notifications, "Notifications", tint = Color.White, modifier = Modifier.size(20.dp))
                                if (hasUnreadNotifs) {
                                    Box(
                                        modifier = Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = 8.dp).size(10.dp).clip(CircleShape).background(Color.Red).border(2.dp, primaryAccent, CircleShape)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = cardColor,
                        shadowElevation = 12.dp,
                        border = if (!ThemeState.isDark.value) BorderStroke(1.dp, Color.Black.copy(0.04f)) else BorderStroke(1.dp, Color.White.copy(0.06f))
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            // Ambient canvas background for Hero card
                            Canvas(modifier = Modifier.matchParentSize().clip(RoundedCornerShape(24.dp))) {
                                val w = size.width
                                val h = size.height
                                if (w > 0 && h > 0) {
                                    val p1X = w * 0.15f + (kotlin.math.sin(homeParticlePhase * 2 * Math.PI).toFloat() * 20f)
                                    val p1Y = h * 0.25f + (kotlin.math.cos(homeParticlePhase * 2 * Math.PI).toFloat() * 15f)
                                    drawCircle(
                                        color = primaryAccent.copy(alpha = 0.07f * homeGlowAlpha),
                                        radius = 65f,
                                        center = Offset(p1X, p1Y)
                                    )

                                    val p2X = w * 0.85f + (kotlin.math.cos(homeParticlePhase * 2 * Math.PI).toFloat() * 20f)
                                    val p2Y = h * 0.35f + (kotlin.math.sin(homeParticlePhase * 2 * Math.PI).toFloat() * 15f)
                                    drawCircle(
                                        color = EmeraldGreen.copy(alpha = 0.06f * homeGlowAlpha),
                                        radius = 70f,
                                        center = Offset(p2X, p2Y)
                                    )
                                }
                            }

                            Column(modifier = Modifier.padding(20.dp)) {
                                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Live pulsating radar dot
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(14.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .scale(homePulseScale)
                                                    .clip(CircleShape)
                                                    .background(EmeraldGreen.copy(alpha = 0.3f))
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(EmeraldGreen)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "TOTAL BALANCE",
                                            fontSize = 12.sp,
                                            color = if(ThemeState.isDark.value) Color.Gray else PremiumSlateLight.copy(0.7f),
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("৳ ${String.format("%,.2f", currentBalance)}", fontSize = 38.sp, fontWeight = FontWeight.Black, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                                HorizontalDivider(color = Color.Gray.copy(0.1f))
                                Spacer(modifier = Modifier.height(20.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SummaryMetricBlock(
                                        icon = Icons.Default.ArrowDownward,
                                        label = "Income",
                                        value = totalReceived,
                                        color = EmeraldGreen,
                                        onClick = { selectedMetricDetail = MetricDetailType.INCOME },
                                        modifier = Modifier.weight(1f)
                                    )
                                    SummaryMetricBlock(
                                        icon = Icons.Default.ArrowUpward,
                                        label = "Spent",
                                        value = totalSpent,
                                        color = SoftRose,
                                        onClick = { selectedMetricDetail = MetricDetailType.SPENT },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SummaryMetricBlock(
                                        icon = Icons.Default.AccountBalanceWallet,
                                        label = "I Owe",
                                        value = totalIOwe,
                                        color = SoftRose,
                                        onClick = { selectedMetricDetail = MetricDetailType.I_OWE },
                                        modifier = Modifier.weight(1f)
                                    )
                                    SummaryMetricBlock(
                                        icon = Icons.Default.AccountBalance,
                                        label = "They Owe",
                                        value = totalTheyOwe,
                                        color = EmeraldGreen,
                                        onClick = { selectedMetricDetail = MetricDetailType.THEY_OWE },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            val actualHorizontalCount = if (criticalDebts.isNotEmpty()) 2 else 1
            val pagerState = rememberPagerState(
                initialPage = if (actualHorizontalCount > 1) actualHorizontalCount * 500 else 0,
                pageCount = { if (actualHorizontalCount > 1) Int.MAX_VALUE else 1 }
            )

            LaunchedEffect(actualHorizontalCount) {
                if (actualHorizontalCount > 1 && pagerState.currentPage < 100) {
                    pagerState.scrollToPage(5000)
                }
            }

            val currentActualPage = if (actualHorizontalCount > 1) {
                (pagerState.currentPage % actualHorizontalCount + actualHorizontalCount) % actualHorizontalCount
            } else 0

            val targetTitle = if (currentActualPage == 0) "Today's Limit" else "Debts"
            AnimatedContent(
                targetState = targetTitle,
                transitionSpec = { fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300)) }, label = "TitleAnimation"
            ) { title ->
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 2.dp))
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 8.dp
            ) { page ->
                val actualPage = if (actualHorizontalCount > 1) (page % actualHorizontalCount + actualHorizontalCount) % actualHorizontalCount else 0

                if (actualPage == 0) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 26.dp).height(115.dp),
                        shape = RoundedCornerShape(20.dp), color = cardColor, shadowElevation = 10.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().clickable { navController.navigate("budget") }.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Spent: ৳${todaySpent.toInt()}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SoftRose)
                                Surface(color = primaryAccent.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                                    Text("Limit: ৳${safeDailySpend.toInt()}", fontSize = 11.sp, color = primaryAccent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                            LinearProgressIndicator(
                                progress = { dailyProgress },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                color = if (dailyProgress > 0.9f) SoftRose else primaryAccent, trackColor = Color.Gray.copy(alpha = 0.15f)
                            )
                            if (dailyProgress >= 1f) {
                                Text("You've reached your daily limit!", fontSize = 12.sp, color = SoftRose, fontWeight = FontWeight.Medium)
                            } else {
                                Text("৳${(safeDailySpend - todaySpent).toInt()} remaining for today", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                } else if (actualPage == 1 && criticalDebts.isNotEmpty()) {
                    val actualVerticalCount = criticalDebts.size
                    val verticalPagerState = rememberPagerState(
                        initialPage = if (actualVerticalCount > 1) actualVerticalCount * 500 else 0,
                        pageCount = { if (actualVerticalCount > 1) Int.MAX_VALUE else 1 }
                    )

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        VerticalPager(
                            state = verticalPagerState,
                            modifier = Modifier.weight(1f).height(145.dp)
                        ) { vPage ->
                            val actualVPage = if (actualVerticalCount > 1) (vPage % actualVerticalCount + actualVerticalCount) % actualVerticalCount else 0
                            val debt = criticalDebts[actualVPage]
                            val isOwe = debt.type == DebtType.I_OWE
                            val debtColor = if (isOwe) SoftRose else EmeraldGreen

                            val diff = debt.deadline!!.time - System.currentTimeMillis()
                            val days = diff / (1000 * 60 * 60 * 24)
                            val dayText = when {
                                days < 0 -> "Overdue by ${-days} days!"
                                days == 0L -> "Due Today!"
                                days == 1L -> "Due Tomorrow!"
                                else -> "Due in $days days"
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 26.dp).height(115.dp),
                                shape = RoundedCornerShape(20.dp), color = cardColor, shadowElevation = 10.dp
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().clickable { navController.navigate("debt") }.padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(debtColor.copy(alpha=0.1f)), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Person, contentDescription = null, tint = debtColor, modifier = Modifier.size(14.dp))
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(if (isOwe) "Pay ${debt.name}" else "Collect from ${debt.name}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }

                                        Surface(color = debtColor.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                                            Text(dayText, fontSize = 10.sp, color = debtColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }
                                    Text("৳${String.format("%,.0f", debt.remainingAmount)}", fontSize = 24.sp, fontWeight = FontWeight.Black, color = debtColor)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Date: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(debt.date)}", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                        Icon(Icons.Default.ChevronRight, contentDescription = "Go", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        if (actualVerticalCount > 1) {
                            AnimatedVisibility(
                                visible = pagerState.currentPage == page, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))
                            ) {
                                val currentActualVPage = (verticalPagerState.currentPage % actualVerticalCount + actualVerticalCount) % actualVerticalCount
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 12.dp)) {
                                    repeat(actualVerticalCount) { iteration ->
                                        val color = if (currentActualVPage == iteration) Color(0xFF7B61FF) else Color.Gray.copy(alpha = 0.3f)
                                        val heightSize = if (currentActualVPage == iteration) 12.dp else 6.dp
                                        Box(modifier = Modifier.padding(vertical = 3.dp).clip(RoundedCornerShape(3.dp)).background(color).width(6.dp).height(heightSize))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (actualHorizontalCount > 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    repeat(actualHorizontalCount) { iteration ->
                        val color = if (currentActualPage == iteration) primaryAccent else Color.Gray.copy(alpha = 0.3f)
                        val widthSize = if (currentActualPage == iteration) 12.dp else 6.dp
                        Box(modifier = Modifier.padding(horizontal = 3.dp).clip(RoundedCornerShape(3.dp)).background(color).height(6.dp).width(widthSize))
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        // FLOATING ACTION BUTTON
        FloatingActionButton(
            onClick = { isExpenseForm = true; showAddScreen = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 100.dp),
            containerColor = primaryAccent, contentColor = Color.White, shape = RoundedCornerShape(16.dp), elevation = FloatingActionButtonDefaults.elevation(6.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Transaction", modifier = Modifier.size(28.dp))
        }

        // Toast Message
        AnimatedVisibility(visible = successMessage.isNotEmpty(), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp)) {
            Surface(color = EmeraldGreen.copy(alpha = 0.1f), shape = RoundedCornerShape(50.dp), border = borderStroke(1.dp, EmeraldGreen.copy(alpha = 0.3f))) {
                Text(successMessage, color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            }
        }

        if (showAddScreen) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showAddScreen = false; focusManager.clearFocus() },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
            ) {
                val dialogBg = if (ThemeState.isDark.value) Color(0xFF1E1E20) else Color.White
                val inputBg = if (ThemeState.isDark.value) Color(0xFF2C2C2E) else Color(0xFFF3F4F6)
                val highlightColor = if (isExpenseForm) SoftRose else EmeraldGreen

                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).imePadding().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        showAddScreen = false; focusManager.clearFocus()
                    }, contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                        shape = RoundedCornerShape(24.dp),
                        color = dialogBg,
                        shadowElevation = 24.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {

                            Box(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = if (isExpenseForm) "Add Expense" else "Add Income",
                                    fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = textColor, modifier = Modifier.align(Alignment.Center)
                                )
                                Box(
                                    modifier = Modifier.size(32.dp).align(Alignment.CenterEnd).clip(CircleShape).background(inputBg).clickable { showAddScreen = false; focusManager.clearFocus() },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(16.dp)) }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(modifier = Modifier.fillMaxWidth().background(inputBg, RoundedCornerShape(14.dp)).padding(4.dp)) {
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (isExpenseForm) SoftRose else Color.Transparent).clickable { isExpenseForm = true }.padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text("Expense", color = if (isExpenseForm) Color.White else textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                                Box(
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (!isExpenseForm) EmeraldGreen else Color.Transparent).clickable { isExpenseForm = false }.padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text("Income", color = if (!isExpenseForm) Color.White else textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                val calDate = Calendar.getInstance().apply { time = transactionDate }
                                val today = Calendar.getInstance()
                                val isToday = calDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) && calDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                                val dateText = if (isToday) "Today" else SimpleDateFormat("dd MMM", Locale.getDefault()).format(transactionDate)

                                Surface(
                                    shape = RoundedCornerShape(50.dp), color = highlightColor.copy(alpha = 0.1f),
                                    modifier = Modifier.height(34.dp).clickable { focusManager.clearFocus(); showDatePicker(context) { transactionDate = it } }
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
                                        Icon(Icons.Default.Event, contentDescription = "Date", tint = highlightColor, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(dateText, color = highlightColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                AnimatedVisibility(visible = !isToday) {
                                    Box(
                                        modifier = Modifier.padding(start = 8.dp).size(34.dp).clip(CircleShape).background(highlightColor.copy(alpha = 0.1f)).clickable { transactionDate = Date() },
                                        contentAlignment = Alignment.Center
                                    ) { Icon(Icons.Default.Restore, contentDescription = "Back to Today", tint = highlightColor, modifier = Modifier.size(16.dp)) }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // 🌟 ULTRA-CLEAN 3-WAY EXPENSE/INCOME ADJUSTER: [+ Add] [− Deduct] [Set Total]
                            if (existingAmount > 0) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 2.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isExpenseForm) "Spent Today in $selectedCategoryName:" else "Earned Today:",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "৳${if (existingAmount % 1.0 == 0.0) existingAmount.toLong().toString() else existingAmount.toString()}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = highlightColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Full-Width Segmented Bar: Equal 1/3 widths, Never wraps
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (ThemeState.isDark.value) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf(
                                            ExpenseEntryMode.ADD to "+ Add",
                                            ExpenseEntryMode.SUBTRACT to "− Deduct",
                                            ExpenseEntryMode.SET_TOTAL to "Set Total"
                                        ).forEach { (mode, label) ->
                                            val isSelected = entryMode == mode
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(34.dp)
                                                    .clip(RoundedCornerShape(9.dp))
                                                    .background(if (isSelected) highlightColor else Color.Transparent)
                                                    .clickable { entryMode = mode },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = if (isSelected) Color.White else textColor,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            // Clean Hero Input Box
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = inputBg,
                                border = BorderStroke(1.dp, highlightColor.copy(alpha = 0.25f))
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val prefix = if (existingAmount > 0) {
                                            when (entryMode) {
                                                ExpenseEntryMode.ADD -> "+৳"
                                                ExpenseEntryMode.SUBTRACT -> "−৳"
                                                ExpenseEntryMode.SET_TOTAL -> "৳"
                                            }
                                        } else "৳"

                                        Text(
                                            text = prefix,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = highlightColor.copy(alpha = 0.7f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))

                                        BasicTextField(
                                            value = transactionInput,
                                            onValueChange = { if (it.text.all { c -> c.isDigit() || c == '.' || c == '+' || c == '-' || c == '*' || c == '/' }) transactionInput = it },
                                            modifier = Modifier.weight(1f).focusRequester(transactionFocusRequester),
                                            textStyle = LocalTextStyle.current.copy(fontSize = 28.sp, fontWeight = FontWeight.Black, color = highlightColor),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                                            keyboardActions = KeyboardActions(onDone = {
                                                handleSave(closeSheet = false)
                                                transactionInput = TextFieldValue("")
                                            }),
                                            cursorBrush = SolidColor(highlightColor),
                                            decorationBox = { innerTextField ->
                                                if (transactionInput.text.isEmpty()) {
                                                    Text(
                                                        text = "0",
                                                        fontSize = 28.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = highlightColor.copy(alpha = 0.35f)
                                                    )
                                                } else {
                                                    innerTextField()
                                                }
                                            }
                                        )

                                        AnimatedVisibility(
                                            visible = transactionInput.text.isNotEmpty(),
                                            enter = fadeIn(tween(200)) + slideInHorizontally(initialOffsetX = { it / 2 }),
                                            exit = fadeOut(tween(200)) + slideOutHorizontally(targetOffsetX = { it / 2 })
                                        ) {
                                            val buttonLabel = if (existingAmount > 0) {
                                                when (entryMode) {
                                                    ExpenseEntryMode.ADD -> "+ Add"
                                                    ExpenseEntryMode.SUBTRACT -> "− Deduct"
                                                    ExpenseEntryMode.SET_TOTAL -> "Save"
                                                }
                                            } else "Save"

                                            Box(
                                                modifier = Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(highlightColor).clickable {
                                                    handleSave(closeSheet = false)
                                                    transactionInput = TextFieldValue("")
                                                }.padding(horizontal = 16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = buttonLabel,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    // Live Preview of New Total
                                    if (existingAmount > 0 && transactionInput.text.isNotEmpty()) {
                                        val inputVal = evaluateExpression(transactionInput.text)
                                        if (inputVal > 0) {
                                            val previewTotal = when (entryMode) {
                                                ExpenseEntryMode.ADD -> existingAmount + inputVal
                                                ExpenseEntryMode.SUBTRACT -> maxOf(0.0, existingAmount - inputVal)
                                                ExpenseEntryMode.SET_TOTAL -> inputVal
                                            }
                                            val previewColor = when (entryMode) {
                                                ExpenseEntryMode.ADD -> Color(0xFF34C759)
                                                ExpenseEntryMode.SUBTRACT -> Color(0xFFFF9500)
                                                ExpenseEntryMode.SET_TOTAL -> highlightColor
                                            }

                                            Text(
                                                text = "New Total will be: ৳${if (previewTotal % 1.0 == 0.0) previewTotal.toLong().toString() else previewTotal.toString()}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = previewColor,
                                                modifier = Modifier.padding(top = 4.dp, start = 28.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            if (isExpenseForm) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(dateSpecificCategories) { categoryName ->
                                        val isSelected = isSameCategory(selectedCategoryName, categoryName)
                                        val chipBg = if (isSelected) highlightColor else inputBg
                                        val chipTextColor = if (isSelected) Color.White else textColor

                                        Surface(
                                            modifier = Modifier.height(38.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            color = chipBg,
                                            border = if (!isSelected && !ThemeState.isDark.value) BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)) else null,
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxHeight().clickable {
                                                    if (selectedCategoryName != categoryName) {
                                                        if (transactionInput.text.isNotEmpty()) {
                                                            handleSave(closeSheet = false)
                                                            transactionInput = TextFieldValue("")
                                                        }
                                                        focusManager.clearFocus()
                                                        selectedCategoryName = categoryName
                                                    }
                                                }.padding(horizontal = 14.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = getCategoryDisplay(categoryName),
                                                    color = chipTextColor,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }

                                    // Button 1: "✨ More..." (Occasional Categories Sheet Picker)
                                    item {
                                        Surface(
                                            modifier = Modifier.height(38.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (ThemeState.isDark.value) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
                                            border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.3f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .clickable { showAllCategoriesPicker = true }
                                                    .padding(horizontal = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("✨ More...", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = highlightColor)
                                            }
                                        }
                                    }

                                    // Button 2: "+" (Create New Custom Category Dialog)
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(highlightColor.copy(alpha = 0.12f))
                                                .clickable { showAddCategoryDialog = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Add, "New Category", tint = highlightColor, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAllCategoriesPicker) {
            AllCategoriesPickerSheet(
                onDismiss = { showAllCategoriesPicker = false },
                onCategorySelected = { cat ->
                    selectedCategoryName = cat
                    showAllCategoriesPicker = false
                },
                onCreateNewClicked = {
                    showAllCategoriesPicker = false
                    showAddCategoryDialog = true
                }
            )
        }

        if (showAddCategoryDialog) {
            CreateCategoryDialog(
                onDismiss = { showAddCategoryDialog = false },
                onCategoryCreated = { newCat ->
                    selectedCategoryName = newCat
                    showAddCategoryDialog = false
                }
            )
        }

        if (selectedMetricDetail != null) {
            MetricBreakdownDialog(
                type = selectedMetricDetail!!,
                thisMonthTransactions = thisMonthTransactions,
                allDebts = allDebts,
                totalIncome = totalReceived,
                totalSpent = totalSpent,
                totalIOwe = totalIOwe,
                totalTheyOwe = totalTheyOwe,
                cardColor = cardColor,
                textColor = textColor,
                primaryAccent = primaryAccent,
                isDark = ThemeState.isDark.value,
                onDismiss = { selectedMetricDetail = null },
                onEditTransaction = { tx ->
                    selectedMetricDetail = null
                    transactionDate = tx.date
                    isExpenseForm = tx.type == TransactionType.EXPENSE
                    selectedCategoryName = tx.category
                    existingTxId = tx.id
                    existingAmount = tx.amount
                    entryMode = ExpenseEntryMode.SET_TOTAL
                    val formattedAmt = if (tx.amount % 1.0 == 0.0) tx.amount.toLong().toString() else tx.amount.toString()
                    transactionInput = TextFieldValue(text = formattedAmt, selection = TextRange(formattedAmt.length))
                    isExplicitEditMode = true
                    showAddScreen = true
                },
                onDeleteTransaction = { tx ->
                    DataManager.deleteTransaction(context, tx)
                    Toast.makeText(context, "Deleted ${tx.category} entry (৳${tx.amount.toInt()})", Toast.LENGTH_SHORT).show()
                },
                onAddNewClicked = {
                    val isExp = selectedMetricDetail == MetricDetailType.SPENT
                    selectedMetricDetail = null
                    isExpenseForm = isExp
                    transactionDate = Date()
                    selectedCategoryName = if (isExp) (availableCategories.firstOrNull() ?: "Others") else "Income"
                    existingTxId = null
                    existingAmount = 0.0
                    entryMode = ExpenseEntryMode.SET_TOTAL
                    transactionInput = TextFieldValue("")
                    isExplicitEditMode = false
                    showAddScreen = true
                },
                onNavigateToDebts = {
                    selectedMetricDetail = null
                    navController.navigate("debt")
                }
            )
        }
    }
}

@Composable
fun MetricBreakdownDialog(
    type: MetricDetailType,
    thisMonthTransactions: List<TransactionEntry>,
    allDebts: List<DebtItem>,
    totalIncome: Double,
    totalSpent: Double,
    totalIOwe: Double,
    totalTheyOwe: Double,
    cardColor: Color,
    textColor: Color,
    primaryAccent: Color,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onEditTransaction: (TransactionEntry) -> Unit,
    onDeleteTransaction: (TransactionEntry) -> Unit,
    onAddNewClicked: () -> Unit,
    onNavigateToDebts: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("d MMM yyyy, hh:mm a", Locale.getDefault()) }
    val themeBg = if (isDark) Color(0xFF1E1E24) else Color(0xFFF7F8FA)

    val title = when (type) {
        MetricDetailType.INCOME -> "Income History 💰"
        MetricDetailType.SPENT -> "Expense History 💸"
        MetricDetailType.I_OWE -> "Debts You Owe 💳"
        MetricDetailType.THEY_OWE -> "Money Owed to You 🤝"
    }

    val subtitle = when (type) {
        MetricDetailType.INCOME -> "Total Income: +৳${String.format("%,.0f", totalIncome)}"
        MetricDetailType.SPENT -> "Total Spent: ৳${String.format("%,.0f", totalSpent)}"
        MetricDetailType.I_OWE -> "Total Payable: ৳${String.format("%,.0f", totalIOwe)}"
        MetricDetailType.THEY_OWE -> "Total Receivable: ৳${String.format("%,.0f", totalTheyOwe)}"
    }

    val headerColor = when (type) {
        MetricDetailType.INCOME, MetricDetailType.THEY_OWE -> Color(0xFF34C759)
        MetricDetailType.SPENT, MetricDetailType.I_OWE -> Color(0xFFFF3B30)
    }

    val transactions = remember(type, thisMonthTransactions) {
        when (type) {
            MetricDetailType.INCOME -> thisMonthTransactions.filter { it.type == TransactionType.INCOME }.sortedByDescending { it.date.time }
            MetricDetailType.SPENT -> thisMonthTransactions.filter { it.type == TransactionType.EXPENSE }.sortedByDescending { it.date.time }
            else -> emptyList()
        }
    }

    val debts = remember(type, allDebts) {
        when (type) {
            MetricDetailType.I_OWE -> allDebts.filter { it.type == DebtType.I_OWE && !it.isPaid && !it.isArchived }.sortedByDescending { it.date.time }
            MetricDetailType.THEY_OWE -> allDebts.filter { it.type == DebtType.THEY_OWE && !it.isPaid && !it.isArchived }.sortedByDescending { it.date.time }
            else -> emptyList()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = cardColor,
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(subtitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = headerColor)
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // List content
                if (type == MetricDetailType.INCOME || type == MetricDetailType.SPENT) {
                    if (transactions.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (type == MetricDetailType.INCOME) "No income records found for this month." else "No expense records found for this month.",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                        ) {
                            items(transactions, key = { it.id }) { tx ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = themeBg,
                                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.06f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tx.category.ifBlank { if (tx.type == TransactionType.INCOME) "Income" else "Expense" },
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = textColor
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = sdf.format(tx.date),
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (tx.type == TransactionType.INCOME) "+৳${String.format("%,.0f", tx.amount)}" else "-৳${String.format("%,.0f", tx.amount)}",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (tx.type == TransactionType.INCOME) Color(0xFF34C759) else Color(0xFFFF3B30)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            IconButton(
                                                onClick = { onEditTransaction(tx) },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF007AFF), modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = { onDeleteTransaction(tx) },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF3B30).copy(0.8f), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onAddNewClicked,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = headerColor)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (type == MetricDetailType.INCOME) "Add New Income" else "Add New Expense", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else {
                    // Debts list
                    if (debts.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (type == MetricDetailType.I_OWE) "No active debts you owe." else "No one currently owes you money.",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                        ) {
                            items(debts, key = { it.id }) { d ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = themeBg,
                                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.06f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = d.name,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = textColor
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Created: ${sdf.format(d.date)}",
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "৳${String.format("%,.0f", d.remainingAmount)}",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = headerColor
                                            )
                                            if (d.deadline != null) {
                                                val dStr = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(d.deadline!!)
                                                Text("Due: $dStr", fontSize = 10.sp, color = Color.Gray)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onNavigateToDebts,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Debt Tracker 📊", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

fun borderStroke(width: androidx.compose.ui.unit.Dp, color: Color) = androidx.compose.foundation.BorderStroke(width, color)

fun showDatePicker(context: Context, onDateSelected: (Date) -> Unit) {
    val cal = Calendar.getInstance()
    DatePickerDialog(context, if (ThemeState.isDark.value) android.R.style.Theme_DeviceDefault_Dialog else android.R.style.Theme_DeviceDefault_Light_Dialog, { _, y, m, d ->
        onDateSelected(Calendar.getInstance().apply { set(y, m, d) }.time)
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
}