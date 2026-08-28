package com.example.myapplication

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessManagerScreen(navController: NavController) {
    val context = LocalContext.current
    val isDark = ThemeState.isDark.value
    val bgColor = ThemeState.background.value
    val cardColor = ThemeState.cardBackground.value
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)
    val primaryColor = ThemeState.primaryAccent.value

    LaunchedEffect(Unit) {
        MessManager.initialize(context)
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Overview, 1: Daily Meals, 2: Bazaar & Deposits, 3: Roommates

    // Month state for historical records & archive
    var selectedMonthCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val sdfMonthKey = remember { SimpleDateFormat("yyyy-MM", Locale.US) }
    val displayMonthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val selectedMonthKey = remember(selectedMonthCalendar) { sdfMonthKey.format(selectedMonthCalendar.time) }
    val selectedMonthDisplay = remember(selectedMonthCalendar) { displayMonthFormat.format(selectedMonthCalendar.time) }

    // Dialog States
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var memberToEdit by remember { mutableStateOf<MessMember?>(null) }
    var memberToDelete by remember { mutableStateOf<MessMember?>(null) }
    var selectedMemberForDetail by remember { mutableStateOf<MemberSummary?>(null) }
    var showAddBazaarDialog by remember { mutableStateOf(false) }
    var bazaarToEdit by remember { mutableStateOf<MessBazaarRecord?>(null) }
    var showAddDepositDialog by remember { mutableStateOf(false) }
    var depositToEdit by remember { mutableStateOf<MessDepositRecord?>(null) }
    var targetMemberIdForDeposit by remember { mutableStateOf<String?>(null) }
    var showFixedCostDialog by remember { mutableStateOf(false) }

    // Date state for daily meal tracking
    val sdfDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val displaySdf = remember { SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()) }
    var selectedMealCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val selectedDateStr = remember(selectedMealCalendar) { sdfDate.format(selectedMealCalendar.time) }

    val members = MessManager.members
    val mealRate = MessManager.getMealRate(selectedMonthKey)
    val totalBazaar = MessManager.getTotalBazaar(selectedMonthKey)
    val totalDeposits = MessManager.getTotalDeposits(selectedMonthKey)
    val fundInHand = MessManager.getFundInHand(selectedMonthKey)
    val totalFixed = MessManager.getTotalFixedCosts()
    val totalMeals = MessManager.getTotalMeals(selectedMonthKey)
    val summaries = MessManager.getMemberSummaries(selectedMonthKey)

    var showRestoreDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = bgColor,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Mess Manager", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = textColor)
                        Text("${members.size} Roommates • Live Rate: ৳${String.format("%.2f", mealRate)}", fontSize = 12.sp, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textColor)
                    }
                },
                actions = {
                    // Analytics & Reports Screen Button
                    IconButton(onClick = { navController.navigate("mess_analysis") }) {
                        Icon(Icons.Default.Insights, contentDescription = "Analytics & Reports", tint = primaryColor)
                    }

                    // Fixed Costs (Rent/Cook/WiFi) Button
                    IconButton(onClick = { showFixedCostDialog = true }) {
                        Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = "Fixed Costs", tint = textColor.copy(alpha = 0.8f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cardColor)
            )
        }
    ) { paddingValues ->
        if (!PremiumManager.isProUser.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = cardColor,
                    shadowElevation = 12.dp
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFD700).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.WorkspacePremium,
                                contentDescription = "PRO",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            "Mess & Roommates Pro 👑",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Live meal rate calculation, daily meal counting, bazaar cost splitting, deposit tracking, fixed cost breakdown, monthly archive and WhatsApp statement generator are exclusive PRO features.",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(26.dp))
                        Button(
                            onClick = { navController.navigate("subscription") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) {
                            Text(
                                "Unlock Mess Manager (PRO)",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Month Selector Bar (Historical Records Support)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = cardColor,
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.05f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            val c = selectedMonthCalendar.clone() as Calendar
                            c.add(Calendar.MONTH, -1)
                            selectedMonthCalendar = c
                        }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Prev Month", tint = textColor)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedMonthDisplay,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                        }

                        IconButton(onClick = {
                            val c = selectedMonthCalendar.clone() as Calendar
                            c.add(Calendar.MONTH, 1)
                            selectedMonthCalendar = c
                        }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Month", tint = textColor)
                        }
                    }
                }

                // Live Stats Hero Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(ThemeState.headerGradient.value)
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("LIVE MEAL RATE ($selectedMonthDisplay)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f), letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "৳${String.format("%.2f", mealRate)}",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "${String.format("%.1f", totalMeals)} Meals Total",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MessHeroSubMetric(label = "Deposits (জমা)", value = "৳${String.format("%,.0f", totalDeposits)}")
                            MessHeroSubMetric(label = "Bazaar Spent", value = "৳${String.format("%,.0f", totalBazaar)}")
                            MessHeroSubMetric(label = "Fund in Hand", value = "৳${String.format("%,.0f", fundInHand)}")
                        }
                    }
                }

                // 🌟 HORIZONTALLY SCROLLABLE TAB BAR (Never wraps text!)
                val tabs = listOf(
                    "📊 Overview",
                    "🍽️ Meals",
                    "🛒 Bazaar & Fund",
                    "👥 Roommates (${members.size})"
                )

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = cardColor,
                    contentColor = primaryColor,
                    edgePadding = 16.dp,
                    divider = {},
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (selectedTab == index) primaryColor else if (isDark) Color(0xFFB0B0B8) else Color(0xFF6B7280),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        )
                    }
                }

                // Tab Contents with Ultra-Smooth Animation
                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(240)) + slideInHorizontally(animationSpec = tween(240)) { width -> if (targetState > initialState) width / 4 else -width / 4 })
                                .togetherWith(fadeOut(animationSpec = tween(180)))
                        },
                        label = "mess_tab_content_anim"
                    ) { tab ->
                        when (tab) {
                            0 -> OverviewTabContent(
                                summaries = summaries,
                                cardColor = cardColor,
                                textColor = textColor,
                                isDark = isDark,
                                primaryColor = primaryColor,
                                onMemberClick = { selectedMemberForDetail = it },
                                onAddDeposit = { showAddDepositDialog = true },
                                onAddBazaar = { showAddBazaarDialog = true },
                                onAddRoommate = { showAddMemberDialog = true }
                            )
                            1 -> DailyMealsTabContent(
                                selectedCalendar = selectedMealCalendar,
                                onPrevDay = {
                                    val c = selectedMealCalendar.clone() as Calendar
                                    c.add(Calendar.DAY_OF_YEAR, -1)
                                    selectedMealCalendar = c
                                },
                                onNextDay = {
                                    val c = selectedMealCalendar.clone() as Calendar
                                    c.add(Calendar.DAY_OF_YEAR, 1)
                                    selectedMealCalendar = c
                                },
                                dateDisplay = displaySdf.format(selectedMealCalendar.time),
                                dateStr = selectedDateStr,
                                members = members,
                                cardColor = cardColor,
                                textColor = textColor,
                                primaryColor = primaryColor,
                                isDark = isDark,
                                context = context,
                                onMemberClick = { m ->
                                    selectedMemberForDetail = MessManager.getMemberSummary(m.id, selectedMonthKey)
                                },
                                onAddRoommate = { showAddMemberDialog = true }
                            )
                            2 -> BazaarAndDepositsTabContent(
                                monthKey = selectedMonthKey,
                                onAddBazaar = { showAddBazaarDialog = true },
                                onAddDeposit = { showAddDepositDialog = true },
                                onAddDepositForMember = { targetMemberIdForDeposit = it },
                                onEditBazaar = { bazaarToEdit = it },
                                onEditDeposit = { depositToEdit = it },
                                cardColor = cardColor,
                                textColor = textColor,
                                isDark = isDark,
                                primaryColor = primaryColor,
                                context = context
                            )
                            3 -> MembersTabContent(
                                members = members,
                                onAddMember = { showAddMemberDialog = true },
                                onRestoreClick = { showRestoreDialog = true },
                                onMemberClick = { m ->
                                    selectedMemberForDetail = MessManager.getMemberSummary(m.id, selectedMonthKey)
                                },
                                onEditMember = { memberToEdit = it },
                                onDeleteMember = { memberToDelete = it },
                                cardColor = cardColor,
                                textColor = textColor,
                                isDark = isDark,
                                context = context
                            )
                        }
                    }
                }
            }
        }
    }

    // --- ALL MODALS & DIALOGS ---

    // Member Details Dialog (Bazaars participated in & breakdown)
    if (selectedMemberForDetail != null) {
        val s = selectedMemberForDetail!!
        val memberBazaars = remember(s.member.id, selectedMonthKey) {
            MessManager.getMemberBazaars(s.member.id, selectedMonthKey)
        }
        val sdf = remember { SimpleDateFormat("d MMM, hh:mm a", Locale.getDefault()) }

        Dialog(onDismissRequest = { selectedMemberForDetail = null }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = cardColor,
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(42.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(s.member.displayName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = primaryColor, fontSize = 17.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.member.displayName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                                    if (s.member.role.equals("Manager", ignoreCase = true) || s.member.isPrimaryManager) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(color = primaryColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                            Text("Manager 🔒", color = primaryColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                                Text("$selectedMonthDisplay • 🍽️ ${String.format("%.1f", s.totalMeals)} Meals", fontSize = 12.sp, color = if (isDark) Color(0xFFB0B0B8) else Color(0xFF6B7280))
                            }
                        }

                        IconButton(onClick = { selectedMemberForDetail = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Summary Stats Grid
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDark) Color(0xFF26262C) else Color(0xFFF2F3F7),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("🍲 Meal Cost (${String.format("%.1f", s.totalMeals)} meals):", fontSize = 12.sp, color = if (isDark) Color(0xFFA0A0A8) else Color(0xFF6B7280))
                                Text("৳${String.format("%,.0f", s.mealCost)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("🏠 Fixed Costs Share:", fontSize = 12.sp, color = if (isDark) Color(0xFFA0A0A8) else Color(0xFF6B7280))
                                Text("৳${String.format("%,.0f", s.fixedCostShare)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("📊 Total Cost (মোট খরচ):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9500))
                                Text("৳${String.format("%,.0f", s.totalCost)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9500))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("💰 Total Deposit (জমা):", fontSize = 12.sp, color = if (isDark) Color(0xFFA0A0A8) else Color(0xFF6B7280))
                                Text("৳${String.format("%,.0f", s.totalDeposits)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34C759))
                            }
                            if (s.personalBazaarPaid > 0) {
                                Spacer(modifier = Modifier.height(5.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("🛒 Own Pocket Bazaar:", fontSize = 12.sp, color = if (isDark) Color(0xFFA0A0A8) else Color(0xFF6B7280))
                                    Text("৳${String.format("%,.0f", s.personalBazaarPaid)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF007AFF))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Net Balance (হিসাব):", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
                                Text(
                                    text = if (s.netBalance >= 0) "Refund: +৳${String.format("%,.0f", s.netBalance)}" else "Due: -৳${String.format("%,.0f", -s.netBalance)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (s.netBalance >= 0) Color(0xFF34C759) else Color(0xFFFF3B30)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("🛒 Bazaars Participated In (${memberBazaars.size} Trips)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (memberBazaars.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                            Text("No bazaar trips recorded with ${s.member.displayName} in $selectedMonthDisplay.", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                        ) {
                            items(memberBazaars, key = { it.id }) { b ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isDark) Color(0xFF202026) else Color(0xFFF7F8FA),
                                    border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.1f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(b.displayItems, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
                                            Text("Went: ${b.displayBuyerName} • ${sdf.format(Date(b.timestamp))}", fontSize = 10.sp, color = Color.Gray)
                                        }
                                        Text("৳${String.format("%,.0f", b.amount)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9500))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Member Dialog
    if (showAddMemberDialog) {
        AddOrEditMemberDialog(
            member = null,
            cardColor = cardColor,
            textColor = textColor,
            primaryColor = primaryColor,
            onDismiss = { showAddMemberDialog = false },
            onSave = { name, phone, room, role ->
                MessManager.addMember(context, MessMember(name = name, phone = phone, roomNo = room, role = role))
                showAddMemberDialog = false
                Toast.makeText(context, "Added $name to Mess! 👤", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Edit Member Dialog
    if (memberToEdit != null) {
        AddOrEditMemberDialog(
            member = memberToEdit,
            cardColor = cardColor,
            textColor = textColor,
            primaryColor = primaryColor,
            onDismiss = { memberToEdit = null },
            onSave = { name, phone, room, role ->
                memberToEdit?.let { existing ->
                    MessManager.updateMember(context, existing.copy(name = name, phone = phone, roomNo = room, role = role))
                    Toast.makeText(context, "Updated $name's info! ✏️", Toast.LENGTH_SHORT).show()
                }
                memberToEdit = null
            }
        )
    }

    // Delete Member Confirmation Dialog
    if (memberToDelete != null) {
        val m = memberToDelete!!
        val isManager = m.role.equals("Manager", ignoreCase = true) || m.isPrimaryManager

        if (isManager) {
            AlertDialog(
                containerColor = cardColor,
                onDismissRequest = { memberToDelete = null },
                title = { Text("Manager Account Locked 🔒", fontWeight = FontWeight.Bold, color = textColor) },
                text = { Text("${m.name} is the Mess Manager. Manager accounts cannot be deleted to preserve mess administration.", color = Color.Gray, fontSize = 14.sp) },
                confirmButton = {
                    Button(onClick = { memberToDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) {
                        Text("Understood", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            )
        } else {
            AlertDialog(
                containerColor = cardColor,
                onDismissRequest = { memberToDelete = null },
                title = { Text("Remove Roommate?", fontWeight = FontWeight.Bold, color = textColor) },
                text = { Text("Are you sure you want to remove '${m.displayName}'? Their past meal and bazaar history will be safely preserved in the Recovery Bin.", color = Color.Gray, fontSize = 14.sp) },
                confirmButton = {
                    Button(
                        onClick = {
                            MessManager.removeMember(context, m.id)
                            memberToDelete = null
                            Toast.makeText(context, "${m.displayName} moved to Recovery Bin", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { memberToDelete = null }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }
    }

    // Restore Member Dialog (Recovery Bin)
    if (showRestoreDialog) {
        val archived = MessManager.archivedMembers
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Restore, contentDescription = null, tint = primaryColor)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Recovery Bin (${archived.size})", fontWeight = FontWeight.Bold, color = textColor, fontSize = 17.sp)
                }
            },
            text = {
                if (archived.isEmpty()) {
                    Text("No removed roommates in the Recovery Bin.", color = Color.Gray, fontSize = 13.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(archived, key = { it.id }) { rm ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDark) Color(0xFF222228) else Color(0xFFF3F4F6),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(rm.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor)
                                        Text("${rm.displayRole}${if (rm.roomNo.isNotEmpty()) " • Bed ${rm.roomNo}" else ""}", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = {
                                            MessManager.restoreMember(context, rm.id)
                                            Toast.makeText(context, "${rm.displayName} Restored! ✅", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Text("Restore", color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        IconButton(onClick = {
                                            MessManager.permanentlyDeleteMember(context, rm.id)
                                            Toast.makeText(context, "${rm.displayName} permanently deleted", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Default.DeleteForever, contentDescription = "Permanent Delete", tint = Color(0xFFFF3B30), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Close", color = primaryColor, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = cardColor,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Add / Edit Bazaar Dialog
    if (showAddBazaarDialog || bazaarToEdit != null) {
        AddOrEditBazaarDialog(
            bazaar = bazaarToEdit,
            members = members,
            cardColor = cardColor,
            textColor = textColor,
            primaryColor = primaryColor,
            isDark = isDark,
            onDismiss = {
                showAddBazaarDialog = false
                bazaarToEdit = null
            },
            onAddRoommate = { showAddMemberDialog = true },
            onSave = { memberIds, buyerNames, amount, items, isPaidFromPersonal ->
                if (bazaarToEdit != null) {
                    val updated = bazaarToEdit!!.copy(
                        buyerMemberIds = memberIds,
                        buyerMemberId = memberIds.firstOrNull() ?: "",
                        buyerName = buyerNames,
                        amount = amount,
                        items = items,
                        isPaidFromPersonalPocket = isPaidFromPersonal
                    )
                    MessManager.updateBazaar(context, updated)
                    Toast.makeText(context, "Bazaar entry updated! 🛒", Toast.LENGTH_SHORT).show()
                } else {
                    MessManager.addBazaar(
                        context,
                        MessBazaarRecord(
                            buyerMemberIds = memberIds,
                            buyerMemberId = memberIds.firstOrNull() ?: "",
                            buyerName = buyerNames,
                            amount = amount,
                            items = items,
                            isPaidFromPersonalPocket = isPaidFromPersonal
                        )
                    )
                    Toast.makeText(context, "Logged Bazaar ৳${amount.toInt()}! 🛒", Toast.LENGTH_SHORT).show()
                }
                showAddBazaarDialog = false
                bazaarToEdit = null
            },
            onDelete = { bzr ->
                MessManager.deleteBazaar(context, bzr.id)
                showAddBazaarDialog = false
                bazaarToEdit = null
                Toast.makeText(context, "Bazaar entry deleted", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Add / Edit Deposit Dialog
    if (showAddDepositDialog || depositToEdit != null || targetMemberIdForDeposit != null) {
        AddOrEditDepositDialog(
            deposit = depositToEdit,
            targetMemberId = targetMemberIdForDeposit,
            members = members,
            cardColor = cardColor,
            textColor = textColor,
            primaryColor = primaryColor,
            isDark = isDark,
            onDismiss = {
                showAddDepositDialog = false
                depositToEdit = null
                targetMemberIdForDeposit = null
            },
            onAddRoommate = { showAddMemberDialog = true },
            onSave = { memberId, memberName, amount, note ->
                if (depositToEdit != null) {
                    val updated = depositToEdit!!.copy(
                        memberId = memberId,
                        memberName = memberName,
                        amount = amount,
                        note = note
                    )
                    MessManager.updateDeposit(context, updated)
                    Toast.makeText(context, "Deposit updated! 💵", Toast.LENGTH_SHORT).show()
                } else {
                    MessManager.addDeposit(
                        context,
                        MessDepositRecord(
                            memberId = memberId,
                            memberName = memberName,
                            amount = amount,
                            note = note
                        )
                    )
                    Toast.makeText(context, "Deposit ৳${amount.toInt()} recorded! 💵", Toast.LENGTH_SHORT).show()
                }
                showAddDepositDialog = false
                depositToEdit = null
                targetMemberIdForDeposit = null
            },
            onDelete = { dep ->
                MessManager.deleteDeposit(context, dep.id)
                showAddDepositDialog = false
                depositToEdit = null
                targetMemberIdForDeposit = null
                Toast.makeText(context, "Deposit deleted", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Fixed Cost Dialog
    if (showFixedCostDialog) {
        FixedCostDialog(
            fixedCosts = MessManager.fixedCosts,
            cardColor = cardColor,
            textColor = textColor,
            primaryColor = primaryColor,
            onDismiss = { showFixedCostDialog = false },
            onAdd = { title, amount ->
                MessManager.addOrUpdateFixedCost(context, MessFixedExpense(title = title, amount = amount))
            },
            onDelete = { id ->
                MessManager.deleteFixedCost(context, id)
            }
        )
    }
}

@Composable
fun MessHeroSubMetric(label: String, value: String) {
    Column {
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.75f))
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

// --- TAB 1: OVERVIEW ---
@Composable
fun OverviewTabContent(
    summaries: List<MemberSummary>,
    cardColor: Color,
    textColor: Color,
    isDark: Boolean,
    primaryColor: Color = Color(0xFF7B61FF),
    onMemberClick: (MemberSummary) -> Unit,
    onAddDeposit: () -> Unit = {},
    onAddBazaar: () -> Unit = {},
    onAddRoommate: () -> Unit = {}
) {
    if (summaries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.GroupAdd, contentDescription = null, tint = primaryColor, modifier = Modifier.size(30.dp))
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text("No Roommates Added Yet", fontWeight = FontWeight.Bold, color = textColor, fontSize = 16.sp)
                Text("Add your mess roommates to start calculating meal rates & expense shares.", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAddRoommate,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text("+ Add First Roommate", fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 6.dp),
        contentPadding = PaddingValues(bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Quick Action Bar
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    onClick = onAddDeposit,
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF34C759).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFF34C759).copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Deposit (জমা)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34C759))
                    }
                }

                Surface(
                    onClick = onAddBazaar,
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFF9500).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFFFF9500).copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color(0xFFFF9500), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Bazaar (বাজার)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9500))
                    }
                }
            }
        }

        items(summaries, key = { it.member.id }) { s ->
            Surface(
                onClick = { onMemberClick(s) },
                shape = RoundedCornerShape(18.dp),
                color = cardColor,
                border = BorderStroke(1.dp, if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f)),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header Row: Avatar, Name & Net Status Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(primaryColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = s.member.name.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = primaryColor
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(s.member.displayName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                                Text(
                                    text = "${if (s.member.role.equals("Manager", ignoreCase = true) || s.member.isPrimaryManager) "Manager (Host)" else s.member.displayRole}${if (s.member.roomNo.isNotEmpty()) " • Bed: ${s.member.roomNo}" else ""} • 🍽️ ${String.format("%.1f", s.totalMeals)} Meals",
                                    fontSize = 12.sp,
                                    color = if (isDark) Color(0xFFB0B0B8) else Color(0xFF6B7280)
                                )
                            }
                        }

                        // Balance Tag
                        val isRefund = s.netBalance >= 0
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isRefund) Color(0xFF34C759).copy(alpha = 0.15f) else Color(0xFFFF3B30).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (isRefund) "Refund: +৳${String.format("%,.0f", s.netBalance)}" else "Due: -৳${String.format("%,.0f", -s.netBalance)}",
                                color = if (isRefund) Color(0xFF34C759) else Color(0xFFFF453A),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.05f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Metrics Row: Costs & Deposits (Clean, minimal, no clutter)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🍲 Meal Cost", fontSize = 11.sp, color = if (isDark) Color(0xFFA0A0A8) else Color(0xFF6B7280), fontWeight = FontWeight.Medium)
                            Text("৳${String.format("%,.0f", s.mealCost)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🏠 Fixed Share", fontSize = 11.sp, color = if (isDark) Color(0xFFA0A0A8) else Color(0xFF6B7280), fontWeight = FontWeight.Medium)
                            Text("৳${String.format("%,.0f", s.fixedCostShare)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("💰 Deposit (জমা)", fontSize = 11.sp, color = if (isDark) Color(0xFFA0A0A8) else Color(0xFF6B7280), fontWeight = FontWeight.Medium)
                            Text("৳${String.format("%,.0f", s.totalDeposits)}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF34C759))
                        }
                    }
                }
            }
        }
    }
}

// --- TAB 2: DAILY MEALS ---
@Composable
fun DailyMealsTabContent(
    selectedCalendar: Calendar,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    dateDisplay: String,
    dateStr: String,
    members: List<MessMember>,
    cardColor: Color,
    textColor: Color,
    primaryColor: Color,
    isDark: Boolean,
    context: android.content.Context,
    onMemberClick: (MessMember) -> Unit,
    onAddRoommate: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 6.dp)) {
        // Date Selector Row
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = cardColor,
            border = BorderStroke(1.dp, if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevDay) { Icon(Icons.Default.ChevronLeft, contentDescription = "Prev Day", tint = textColor) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(dateDisplay, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                }
                IconButton(onClick = onNextDay) { Icon(Icons.Default.ChevronRight, contentDescription = "Next Day", tint = textColor) }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (members.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No Roommates Added Yet", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onAddRoommate, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = primaryColor)) {
                        Text("+ Add Roommates")
                    }
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(members, key = { it.id }) { member ->
                val mealRecord = MessManager.getMealForDateAndMember(dateStr, member.id)

                var bCount by remember(dateStr, member.id, mealRecord.breakfast) { mutableDoubleStateOf(mealRecord.breakfast) }
                var lCount by remember(dateStr, member.id, mealRecord.lunch) { mutableDoubleStateOf(mealRecord.lunch) }
                var dCount by remember(dateStr, member.id, mealRecord.dinner) { mutableDoubleStateOf(mealRecord.dinner) }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardColor,
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.08f)),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f).clickable { onMemberClick(member) }
                            ) {
                                Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                    Text(member.displayName.take(1).uppercase(), color = primaryColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(member.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
                                    Text("${member.displayRole}${if (member.roomNo.isNotEmpty()) " • Bed ${member.roomNo}" else ""}", fontSize = 11.sp, color = if (isDark) Color(0xFFB0B0B8) else Color(0xFF6B7280))
                                }
                            }

                            Text(
                                text = "Total: ${String.format("%.1f", bCount + lCount + dCount)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryColor
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MealCounterPill(
                                modifier = Modifier.weight(1f),
                                label = "☕ Breakfast", count = bCount, isDark = isDark, textColor = textColor, primaryColor = primaryColor,
                                onInc = {
                                    bCount = (bCount + 0.5).coerceAtMost(5.0)
                                    MessManager.updateMeal(context, dateStr, member.id, bCount, lCount, dCount)
                                }, onDec = {
                                    bCount = (bCount - 0.5).coerceAtLeast(0.0)
                                    MessManager.updateMeal(context, dateStr, member.id, bCount, lCount, dCount)
                                })

                            MealCounterPill(
                                modifier = Modifier.weight(1f),
                                label = "🍱 Lunch", count = lCount, isDark = isDark, textColor = textColor, primaryColor = primaryColor,
                                onInc = {
                                    lCount = (lCount + 1.0).coerceAtMost(6.0)
                                    MessManager.updateMeal(context, dateStr, member.id, bCount, lCount, dCount)
                                }, onDec = {
                                    lCount = (lCount - 1.0).coerceAtLeast(0.0)
                                    MessManager.updateMeal(context, dateStr, member.id, bCount, lCount, dCount)
                                })

                            MealCounterPill(
                                modifier = Modifier.weight(1f),
                                label = "🍽️ Dinner", count = dCount, isDark = isDark, textColor = textColor, primaryColor = primaryColor,
                                onInc = {
                                    dCount = (dCount + 1.0).coerceAtMost(6.0)
                                    MessManager.updateMeal(context, dateStr, member.id, bCount, lCount, dCount)
                                }, onDec = {
                                    dCount = (dCount - 1.0).coerceAtLeast(0.0)
                                    MessManager.updateMeal(context, dateStr, member.id, bCount, lCount, dCount)
                                })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MealCounterPill(
    label: String,
    count: Double,
    isDark: Boolean,
    textColor: Color,
    primaryColor: Color,
    onInc: () -> Unit,
    onDec: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) Color(0xFF26262C) else Color(0xFFF2F3F7),
        border = BorderStroke(1.dp, if (isDark) Color.White.copy(0.05f) else Color.Black.copy(0.05f)),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Text(label, fontSize = 11.sp, color = if (isDark) Color(0xFFD1D5DB) else Color(0xFF4B5563), fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isDark) Color(0xFF383842) else Color(0xFFE2E4EB),
                    modifier = Modifier.size(26.dp).clickable { onDec() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = textColor)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (count % 1.0 == 0.0) count.toInt().toString() else count.toString(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = textColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = primaryColor.copy(alpha = 0.2f),
                    modifier = Modifier.size(26.dp).clickable { onInc() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = primaryColor)
                    }
                }
            }
        }
    }
}

// Data class for grouped deposits by member
data class MemberDepositGroup(
    val memberId: String,
    val memberName: String,
    val totalAmount: Double,
    val records: List<MessDepositRecord>
)

// --- TAB 3: BAZAAR & DEPOSITS (Full height, spacious & clear) ---
@Composable
fun BazaarAndDepositsTabContent(
    monthKey: String,
    onAddBazaar: () -> Unit,
    onAddDeposit: () -> Unit,
    onAddDepositForMember: (String) -> Unit,
    onEditBazaar: (MessBazaarRecord) -> Unit,
    onEditDeposit: (MessDepositRecord) -> Unit,
    cardColor: Color,
    textColor: Color,
    isDark: Boolean,
    primaryColor: Color,
    context: android.content.Context
) {
    var filterType by remember { mutableIntStateOf(0) } // 0: All, 1: Bazaar Only, 2: Deposits Only
    var selectedDepositGroupForDetail by remember { mutableStateOf<MemberDepositGroup?>(null) }

    val bzr = remember(monthKey, MessManager.bazaarRecords.size) {
        MessManager.bazaarRecords.filter { MessManager.getMonthKey(it.timestamp) == monthKey }
    }
    val dep = remember(monthKey, MessManager.depositRecords.size) {
        MessManager.depositRecords.filter { MessManager.getMonthKey(it.timestamp) == monthKey }
    }

    val groupedDeposits = remember(dep, MessManager.members.size) {
        dep.groupBy { it.memberId.ifBlank { it.memberName } }
            .map { (key, list) ->
                val first = list.first()
                val member = MessManager.members.find { it.id == first.memberId }
                    ?: MessManager.members.find { it.name.equals(first.memberName, ignoreCase = true) }
                val displayName = member?.displayName ?: first.memberName
                MemberDepositGroup(
                    memberId = member?.id ?: first.memberId,
                    memberName = displayName,
                    totalAmount = list.sumOf { it.amount },
                    records = list.sortedByDescending { it.timestamp }
                )
            }
            .sortedByDescending { it.totalAmount }
    }

    val sdf = remember { SimpleDateFormat("d MMM, hh:mm a", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 6.dp)) {
        // Prominent Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onAddBazaar,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500))
            ) {
                Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Bazaar", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onAddDeposit,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
            ) {
                Icon(Icons.Default.AccountBalanceWallet, null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Deposit", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Pills
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All (${bzr.size + groupedDeposits.size})", "🛒 Bazaar (${bzr.size})", "💵 Deposits (${groupedDeposits.size})").forEachIndexed { index, title ->
                Surface(
                    onClick = { filterType = index },
                    shape = RoundedCornerShape(10.dp),
                    color = if (filterType == index) primaryColor else Color.Gray.copy(alpha = 0.12f),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (filterType == index) Color.White else textColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Full Screen Spacious LazyColumn
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filterType == 0 || filterType == 1) {
                if (filterType == 0 && bzr.isNotEmpty()) {
                    item {
                        Text("Bazaar Purchases (বাজার খরচ)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }
                }

                if (bzr.isEmpty() && filterType == 1) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                            Text("No bazaar entries for this month.", color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                } else {
                    items(bzr, key = { "bzr_${it.id}" }) { b ->
                        Surface(
                            onClick = { onEditBazaar(b) },
                            shape = RoundedCornerShape(14.dp),
                            color = cardColor,
                            border = BorderStroke(1.dp, if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(b.items, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Went: ${b.buyerName} ${if (b.isPaidFromPersonalPocket) "• (Own Pocket)" else "• (From Fund)"} • ${sdf.format(Date(b.timestamp))}", fontSize = 11.sp, color = Color.Gray)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("৳${String.format("%,.0f", b.amount)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9500))
                                    IconButton(onClick = { onEditBazaar(b) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF007AFF), modifier = Modifier.size(17.dp))
                                    }
                                    IconButton(onClick = { MessManager.deleteBazaar(context, b.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(17.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (filterType == 0 || filterType == 2) {
                if (filterType == 0 && groupedDeposits.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Member Deposits (টাকা জমা / Advance)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }
                }

                if (groupedDeposits.isEmpty() && filterType == 2) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                            Text("No deposits recorded for this month.", color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                } else {
                    items(groupedDeposits, key = { "group_${it.memberId}_${it.memberName}" }) { g ->
                        Surface(
                            onClick = { selectedDepositGroupForDetail = g },
                            shape = RoundedCornerShape(16.dp),
                            color = cardColor,
                            border = BorderStroke(1.dp, if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF34C759).copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = g.memberName.take(1).uppercase(),
                                            color = Color(0xFF34C759),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(g.memberName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val entryCountText = if (g.records.size == 1) "1 deposit" else "${g.records.size} deposits"
                                        val latestTime = sdf.format(Date(g.records.first().timestamp))
                                        Text("$entryCountText • Last: $latestTime", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("+৳${String.format("%,.0f", g.totalAmount)}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF34C759))
                                        Text("Details 👁️", fontSize = 11.sp, color = primaryColor, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.ChevronRight, contentDescription = "View Details", tint = Color.Gray, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Member Deposit History Details Dialog
    if (selectedDepositGroupForDetail != null) {
        val currentGroup = groupedDeposits.find { it.memberId == selectedDepositGroupForDetail!!.memberId || it.memberName == selectedDepositGroupForDetail!!.memberName }
            ?: selectedDepositGroupForDetail!!

        MemberDepositHistoryDialog(
            group = currentGroup,
            cardColor = cardColor,
            textColor = textColor,
            primaryColor = primaryColor,
            isDark = isDark,
            onDismiss = { selectedDepositGroupForDetail = null },
            onAddMoreDeposit = {
                val mId = currentGroup.memberId
                selectedDepositGroupForDetail = null
                onAddDepositForMember(mId)
            },
            onEditDeposit = { d ->
                selectedDepositGroupForDetail = null
                onEditDeposit(d)
            },
            onDeleteDeposit = { d ->
                MessManager.deleteDeposit(context, d.id)
                Toast.makeText(context, "Deposit entry deleted", Toast.LENGTH_SHORT).show()
                if (currentGroup.records.size <= 1) {
                    selectedDepositGroupForDetail = null
                }
            }
        )
    }
}

@Composable
fun MemberDepositHistoryDialog(
    group: MemberDepositGroup,
    cardColor: Color,
    textColor: Color,
    primaryColor: Color,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onAddMoreDeposit: () -> Unit,
    onEditDeposit: (MessDepositRecord) -> Unit,
    onDeleteDeposit: (MessDepositRecord) -> Unit
) {
    val sdf = remember { SimpleDateFormat("d MMM, hh:mm a", Locale.getDefault()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = cardColor,
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF34C759).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(group.memberName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Color(0xFF34C759), fontSize = 17.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("${group.memberName}'s Deposits 💵", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                            Text("Total: +৳${String.format("%,.0f", group.totalAmount)} (${group.records.size} installment${if (group.records.size > 1) "s" else ""})", fontSize = 12.sp, color = Color(0xFF34C759), fontWeight = FontWeight.SemiBold)
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action to add more deposits for this member
                Button(
                    onClick = onAddMoreDeposit,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759).copy(alpha = 0.15f))
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ Add Deposit for ${group.memberName}", color = Color(0xFF34C759), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Deposit History / জমা বিবরণী:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                if (group.records.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("No deposit records found.", color = Color.Gray, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                    ) {
                        items(group.records, key = { it.id }) { d ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isDark) Color(0xFF222228) else Color(0xFFF6F7F9),
                                border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "+৳${String.format("%,.0f", d.amount)}",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF34C759)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${sdf.format(Date(d.timestamp))}${if (d.note.isNotBlank()) " • ${d.note}" else ""}",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onEditDeposit(d) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF007AFF), modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = { onDeleteDeposit(d) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF3B30).copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MembersTabContent(
    members: List<MessMember>,
    onAddMember: () -> Unit,
    onRestoreClick: () -> Unit,
    onMemberClick: (MessMember) -> Unit,
    onEditMember: (MessMember) -> Unit,
    onDeleteMember: (MessMember) -> Unit,
    cardColor: Color,
    textColor: Color,
    isDark: Boolean,
    context: android.content.Context
) {
    val archivedCount = MessManager.archivedMembers.size

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onAddMember,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B61FF))
            ) {
                Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Roommate", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            if (archivedCount > 0) {
                OutlinedButton(
                    onClick = onRestoreClick,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF34C759).copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF34C759))
                ) {
                    Icon(Icons.Default.Restore, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Recover ($archivedCount)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (members.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No roommates added yet. Tap above to add roomies!", color = Color.Gray, fontSize = 13.sp)
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(members, key = { it.id }) { m ->
                val isManager = m.role.equals("Manager", ignoreCase = true) || m.isPrimaryManager

                Surface(
                    onClick = { onMemberClick(m) },
                    shape = RoundedCornerShape(16.dp),
                    color = cardColor,
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF7B61FF).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(m.displayName.take(1).uppercase(), color = Color(0xFF7B61FF), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(m.displayName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
                                Text(
                                    text = "${if (isManager) "Manager (Host) 🔒" else m.displayRole}${if (m.roomNo.isNotEmpty()) " • Bed ${m.roomNo}" else ""}${if (m.phone.isNotEmpty()) " • 📞 ${m.phone}" else ""}",
                                    fontSize = 12.sp,
                                    color = if (isDark) Color(0xFFB0B0B8) else Color(0xFF6B7280)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onEditMember(m) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF007AFF), modifier = Modifier.size(18.dp))
                            }
                            if (isManager) {
                                IconButton(onClick = {
                                    Toast.makeText(context, "Manager account is locked and cannot be deleted.", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.Lock, contentDescription = "Locked", tint = Color.Gray.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                                }
                            } else {
                                IconButton(onClick = { onDeleteMember(m) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF3B30).copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- DIALOGS ---

@Composable
fun AddOrEditMemberDialog(
    member: MessMember?,
    cardColor: Color,
    textColor: Color,
    primaryColor: Color,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(member?.displayName ?: "") }
    var phone by remember { mutableStateOf(member?.phone ?: "") }
    var room by remember { mutableStateOf(member?.roomNo ?: "") }
    var role by remember { mutableStateOf(member?.role ?: "Member") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = cardColor, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(if (member == null) "Add Roommate" else "Edit Roommate", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone (Optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("Room / Bed No (Optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))

                // Role Selector
                Text("Role in Mess:", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Member", "Manager").forEach { r ->
                        Surface(
                            onClick = { role = r },
                            shape = RoundedCornerShape(10.dp),
                            color = if (role == r) primaryColor else Color.Gray.copy(alpha = 0.12f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = r,
                                color = if (role == r) Color.White else textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = { if (name.isNotBlank()) onSave(name, phone, room, role) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text(if (member == null) "Add Roommate" else "Save Changes", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AddOrEditBazaarDialog(
    bazaar: MessBazaarRecord? = null,
    members: List<MessMember>,
    cardColor: Color,
    textColor: Color,
    primaryColor: Color,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onAddRoommate: () -> Unit,
    onSave: (List<String>, String, Double, String, Boolean) -> Unit,
    onDelete: ((MessBazaarRecord) -> Unit)? = null
) {
    val initialSelected = remember(bazaar, members) {
        if (bazaar != null) {
            val ids = bazaar.buyerMemberIds ?: (if (bazaar.buyerMemberId.isNotEmpty()) listOf(bazaar.buyerMemberId) else emptyList())
            ids.toSet().ifEmpty { setOfNotNull(members.firstOrNull()?.id) }
        } else {
            setOfNotNull(members.firstOrNull()?.id)
        }
    }
    var selectedMemberIds by remember { mutableStateOf(initialSelected) }
    var amount by remember { mutableStateOf(if (bazaar != null) String.format(Locale.US, "%.0f", bazaar.amount) else "") }
    var items by remember { mutableStateOf(bazaar?.items ?: "") }
    var isPaidFromPersonal by remember { mutableStateOf(bazaar?.isPaidFromPersonalPocket ?: false) }
    var errorMessage by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = cardColor, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (bazaar != null) "Edit Bazaar Expense ✏️" else "Add Bazaar Expense 🛒",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    if (bazaar != null && onDelete != null) {
                        IconButton(onClick = { onDelete(bazaar) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF3B30))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))

                Text("Who went / shopped? (Select roomies):", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                if (members.isEmpty()) {
                    Text("No roommates added. Please add roommates first.", color = Color.Red, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = {
                        onDismiss()
                        onAddRoommate()
                    }) {
                        Text("+ Add Roommate", color = primaryColor, fontWeight = FontWeight.Bold)
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(members) { m ->
                            val isSelected = selectedMemberIds.contains(m.id)
                            Surface(
                                onClick = {
                                    selectedMemberIds = if (isSelected) {
                                        if (selectedMemberIds.size > 1) selectedMemberIds - m.id else selectedMemberIds
                                    } else {
                                        selectedMemberIds + m.id
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFFFF9500) else if (isDark) Color(0xFF2C2C34) else Color(0xFFF0F1F5)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    val isMgr = m.role.equals("Manager", ignoreCase = true) || m.isPrimaryManager
                                    val label = if (isMgr) "${m.displayName} (Manager)" else m.displayName
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.White else textColor,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Payment Source (Mess Fund vs Personal Pocket)
                Text("Payment Source:", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = { isPaidFromPersonal = false },
                        shape = RoundedCornerShape(10.dp),
                        color = if (!isPaidFromPersonal) Color(0xFF007AFF) else if (isDark) Color(0xFF2C2C34) else Color(0xFFF0F1F5),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "💼 From Mess Fund",
                            color = if (!isPaidFromPersonal) Color.White else textColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    Surface(
                        onClick = { isPaidFromPersonal = true },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isPaidFromPersonal) Color(0xFFFF9500) else if (isDark) Color(0xFF2C2C34) else Color(0xFFF0F1F5),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "💳 Own Pocket",
                            color = if (isPaidFromPersonal) Color.White else textColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        errorMessage = ""
                    },
                    label = { Text("Amount (৳) *") },
                    isError = errorMessage.isNotEmpty(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage.isNotEmpty()) {
                    Text(errorMessage, color = Color.Red, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Quick Adjustment Chips (+/-)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(-500, -100, -50, 50, 100, 500).forEach { delta ->
                        Surface(
                            onClick = {
                                val cur = amount.toDoubleOrNull() ?: 0.0
                                val next = maxOf(0.0, cur + delta)
                                amount = if (next == 0.0) "" else String.format(Locale.US, "%.0f", next)
                                errorMessage = ""
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (delta < 0) Color(0xFFFF3B30).copy(alpha = 0.12f) else Color(0xFF34C759).copy(alpha = 0.12f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (delta > 0) "+$delta" else "$delta",
                                color = if (delta < 0) Color(0xFFFF3B30) else Color(0xFF34C759),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = items,
                    onValueChange = { items = it },
                    label = { Text("Items / Description (Optional)") },
                    placeholder = { Text("e.g. Chicken, Rice, Oil") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        val amt = amount.toDoubleOrNull()
                        if (amt == null || amt <= 0) {
                            errorMessage = "Please enter a valid amount (৳)"
                            return@Button
                        }
                        val selectedList = members.filter { selectedMemberIds.contains(it.id) }
                        if (selectedList.isEmpty()) {
                            errorMessage = "Please select at least one roommate"
                            return@Button
                        }
                        val buyerNames = selectedList.joinToString(", ") { it.displayName }
                        val finalItems = if (items.isBlank()) "Bazaar / Groceries" else items.trim()
                        onSave(selectedList.map { it.id }, buyerNames, amt, finalItems, isPaidFromPersonal)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500))
                ) {
                    Text(if (bazaar != null) "Update Bazaar Entry" else "Save Bazaar Record", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AddOrEditDepositDialog(
    deposit: MessDepositRecord? = null,
    targetMemberId: String? = null,
    members: List<MessMember>,
    cardColor: Color,
    textColor: Color,
    primaryColor: Color,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onAddRoommate: () -> Unit,
    onSave: (String, String, Double, String) -> Unit,
    onDelete: ((MessDepositRecord) -> Unit)? = null
) {
    val initialMemberId = remember(deposit, targetMemberId, members) {
        deposit?.memberId ?: targetMemberId ?: (members.firstOrNull()?.id ?: "")
    }
    var selectedMemberId by remember { mutableStateOf(initialMemberId) }
    var amount by remember { mutableStateOf(if (deposit != null) String.format(Locale.US, "%.0f", deposit.amount) else "") }
    var note by remember { mutableStateOf(deposit?.note ?: "") }
    var errorMessage by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = cardColor, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (deposit != null) "Edit Member Deposit ✏️" else "Add Member Deposit 💵",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text("Advance money given to the mess fund (জমা)", fontSize = 12.sp, color = Color.Gray)
                    }
                    if (deposit != null && onDelete != null) {
                        IconButton(onClick = { onDelete(deposit) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF3B30))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))

                Text("Deposited by:", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))

                if (members.isEmpty()) {
                    Text("No roommates added. Please add roommates first.", color = Color.Red, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = {
                        onDismiss()
                        onAddRoommate()
                    }) {
                        Text("+ Add Roommate", color = primaryColor, fontWeight = FontWeight.Bold)
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(members) { m ->
                            val isSelected = m.id == selectedMemberId
                            Surface(
                                onClick = { selectedMemberId = m.id },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF34C759) else if (isDark) Color(0xFF2C2C34) else Color(0xFFF0F1F5)
                            ) {
                                val isMgr = m.role.equals("Manager", ignoreCase = true) || m.isPrimaryManager
                                val label = if (isMgr) "${m.displayName} (Manager)" else m.displayName
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else textColor,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        errorMessage = ""
                    },
                    label = { Text("Deposit Amount (৳) *") },
                    isError = errorMessage.isNotEmpty(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage.isNotEmpty()) {
                    Text(errorMessage, color = Color.Red, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Quick Adjustment Chips (+/-)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(-500, -100, -50, 50, 100, 500).forEach { delta ->
                        Surface(
                            onClick = {
                                val cur = amount.toDoubleOrNull() ?: 0.0
                                val next = maxOf(0.0, cur + delta)
                                amount = if (next == 0.0) "" else String.format(Locale.US, "%.0f", next)
                                errorMessage = ""
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (delta < 0) Color(0xFFFF3B30).copy(alpha = 0.12f) else Color(0xFF34C759).copy(alpha = 0.12f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (delta > 0) "+$delta" else "$delta",
                                color = if (delta < 0) Color(0xFFFF3B30) else Color(0xFF34C759),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (Optional, e.g. 1st installment)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        val amt = amount.toDoubleOrNull()
                        if (amt == null || amt <= 0) {
                            errorMessage = "Please enter deposit amount (৳)"
                            return@Button
                        }
                        val selectedMember = members.find { it.id == selectedMemberId } ?: members.firstOrNull()
                        if (selectedMember == null) {
                            errorMessage = "Please select a roommate"
                            return@Button
                        }
                        onSave(selectedMember.id, selectedMember.displayName, amt, note.trim())
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759))
                ) {
                    Text(if (deposit != null) "Update Deposit" else "Save Deposit", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun FixedCostDialog(
    fixedCosts: List<MessFixedExpense>,
    cardColor: Color,
    textColor: Color,
    primaryColor: Color,
    onDismiss: () -> Unit,
    onAdd: (String, Double) -> Unit,
    onDelete: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = cardColor, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text("Fixed Mess Costs 🏠", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                Text("House rent, cook bill, wifi, gas (Split equally among roomies)", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, placeholder = { Text("e.g. WiFi Bill") }, singleLine = true, modifier = Modifier.weight(1.2f))
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, placeholder = { Text("৳") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(0.8f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val amt = amount.toDoubleOrNull()
                        if (title.isNotBlank() && amt != null && amt > 0) {
                            onAdd(title, amt)
                            title = ""
                            amount = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text("Add Fixed Cost")
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(fixedCosts, key = { it.id }) { fc ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(fc.title, fontSize = 14.sp, color = textColor)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("৳${String.format("%,.0f", fc.amount)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor)
                                IconButton(onClick = { onDelete(fc.id) }) {
                                    Icon(Icons.Default.Delete, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
