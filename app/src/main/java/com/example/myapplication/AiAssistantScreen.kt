package com.example.myapplication

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "user" or "ai"
    val message: String,
    val thinkingProcess: String? = null,
    val budgetActionMap: Map<String, Double>? = null,
    var isActionApplied: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

object AiBudgetActionManager {
    /**
     * Updates the app's official budget preferences with the AI-suggested category limits
     */
    fun applyBudget(context: Context, budgetMap: Map<String, Double>): Boolean {
        return try {
            val prefs = context.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)
            val gson = Gson()

            val curatedListType = object : TypeToken<MutableSet<String>>() {}.type
            val curatedList: MutableSet<String> = gson.fromJson(prefs.getString("curated_budget_list", "[]"), curatedListType) ?: mutableSetOf()

            val limitsType = object : TypeToken<MutableMap<String, Double>>() {}.type
            val categoryLimits: MutableMap<String, Double> = gson.fromJson(prefs.getString("category_limits", "{}"), limitsType) ?: mutableMapOf()

            for ((cat, amount) in budgetMap) {
                if (amount > 0) {
                    curatedList.add(cat)
                    categoryLimits[cat] = amount
                }
            }

            prefs.edit()
                .putString("curated_budget_list", gson.toJson(curatedList))
                .putString("category_limits", gson.toJson(categoryLimits))
                .apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Parses [ACTION:SET_BUDGET:{"Category": Amount, ...}] from AI response
     */
    fun extractBudgetAction(rawAnswer: String): Pair<String, Map<String, Double>?> {
        val regex = Regex("\\[ACTION:SET_BUDGET:(\\{.*?\\})\\]", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(rawAnswer) ?: return Pair(rawAnswer, null)

        val jsonStr = match.groupValues[1]
        val cleanAnswer = rawAnswer.replace(match.value, "").trim()
        val textToShow = if (cleanAnswer.isBlank()) {
            "এখানে আপনার দেওয়া তথ্য অনুযায়ী বাজেট পরিকল্পনা তৈরি করা হয়েছে। নিচের বাটনে ট্যাপ করে এটি অ্যাপের বাজেটে সেট করুন 👇"
        } else cleanAnswer

        return try {
            val type = object : TypeToken<Map<String, Double>>() {}.type
            val map: Map<String, Double> = Gson().fromJson(jsonStr, type)
            Pair(textToShow, map)
        } catch (e: Exception) {
            Pair(cleanAnswer, null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(navController: NavController) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val isPro = PremiumManager.isProUser.value
    val isDark = ThemeState.isDark.value
    val textColor = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val cardBg = if (isDark) Color(0xFF1E1E24) else Color.White
    val bgColor = if (isDark) Color(0xFF101014) else Color(0xFFF7F7FA)
    val primaryColor = Color(0xFF7B61FF)

    // Financial Data Context for AI
    val transactions by DataManager.getTransactionsFlow(context).collectAsState(initial = emptyList())
    val debts by DataManager.getDebtsFlow(context).collectAsState(initial = emptyList())

    val thisMonthExpenses = transactions.filter { ExpenseCalculator.isThisMonth(it.date) && it.type == TransactionType.EXPENSE }
    val thisMonthIncome = transactions.filter { ExpenseCalculator.isThisMonth(it.date) && it.type == TransactionType.INCOME }
    val totalExpense = thisMonthExpenses.sumOf { it.amount }
    val totalIncome = thisMonthIncome.sumOf { it.amount }
    val netBalance = ExpenseCalculator.getThisMonthBalance(context, transactions, debts)

    val totalLent = debts.filter { it.type == DebtType.THEY_OWE && !it.isPaid }.sumOf { it.remainingAmount }
    val totalBorrowed = debts.filter { it.type == DebtType.I_OWE && !it.isPaid }.sumOf { it.remainingAmount }

    // Top categories
    val topCategories = thisMonthExpenses.groupBy { it.category }
        .mapValues { it.value.sumOf { tx -> tx.amount } }
        .toList()
        .sortedByDescending { it.second }
        .take(5)

    val recentTxList = transactions.take(15)

    // Current Active Conversation Session (Multi-Conversation Support up to 50)
    var currentSession by remember {
        mutableStateOf(AiChatHistoryManager.getOrCreateLatestSession(context))
    }

    var allSessions by remember {
        mutableStateOf(AiChatHistoryManager.getAllSessions(context))
    }

    var showHistorySheet by remember { mutableStateOf(false) }
    var userInput by remember { mutableStateOf("") }
    var isAiThinking by remember { mutableStateOf(false) }
    var streamingMessageId by remember { mutableStateOf<String?>(null) }

    val quickQuestions = listOf(
        "💡 Plan my budget to save ৳2,000 this month",
        "🎯 Set my budget: Food 3000, Transport 1000, Mess 2000",
        "🏠 How is our Mess running this month?",
        "🍔 Analyze my Food & Mess expenses",
        "💸 Who owes me money in debts & mess?"
    )

    fun buildSystemPrompt(): String {
        val calendar = Calendar.getInstance()
        val totalDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val daysLeft = maxOf(1, totalDays - currentDay + 1)
        val dailySafe = if (netBalance > 0) netBalance / daysLeft else 0.0

        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
        val todayStr = dateFormat.format(Date())
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

        val txSummary = if (recentTxList.isNotEmpty()) {
            recentTxList.joinToString("\n") { tx ->
                "• ${dateFormat.format(tx.date)} | ${tx.type} | ${tx.category}: ৳${tx.amount.toInt()}"
            }
        } else "No recent transactions."

        val topCatStr = if (topCategories.isNotEmpty()) {
            topCategories.joinToString(", ") { "${it.first}: ৳${it.second.toInt()}" }
        } else "None"

        val debtsStr = if (debts.filter { !it.isPaid }.isNotEmpty()) {
            debts.filter { !it.isPaid }.joinToString("\n") { d ->
                val typeStr = if (d.type == DebtType.THEY_OWE) "They owe me (পাবো)" else "I owe them (দিতে হবে)"
                val deadlineStr = d.deadline?.let { " [Deadline: ${dateFormat.format(it)}]" } ?: ""
                "• ${d.name}: ৳${d.remainingAmount.toInt()} ($typeStr)$deadlineStr"
            }
        } else "No active debts."

        // Live Mess Manager Data
        val messRate = MessManager.getMealRate(monthKey)
        val messBazaar = MessManager.getTotalBazaar(monthKey)
        val messDeposits = MessManager.getTotalDeposits(monthKey)
        val messFund = MessManager.getFundInHand(monthKey)
        val messFixed = MessManager.getTotalFixedCosts()
        val messMeals = MessManager.getTotalMeals(monthKey)
        val messSummaries = MessManager.getMemberSummaries(monthKey)

        val messMembersStr = if (messSummaries.isNotEmpty()) {
            messSummaries.joinToString("\n") { s ->
                val balStr = if (s.netBalance >= 0) "Refund: +৳${s.netBalance.toInt()}" else "Due: -৳${(-s.netBalance).toInt()}"
                "• ${s.member.displayName} (${s.member.displayRole}): ${String.format("%.1f", s.totalMeals)} meals | Cost: ৳${s.totalCost.toInt()} | Deposit: ৳${s.totalDeposits.toInt()} | $balStr"
            }
        } else "No roommates added in Mess Manager yet."

        val appUserName = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName ?: "User"

        return """
            You are the "Bachelors AI Financial Agent" (স্মার্ট ব্যাচেলর ফাইন্যান্সিয়াল এআই অ্যাসিস্ট্যান্ট), an omniscient, intelligent, and autonomous personal finance advisor built inside the "Bachelor's Wallet" Android app. You have full knowledge of all app features and real-time live access to user's finances and mess manager.

            CRITICAL CORE RULES:
            1. STRICT DIRECT ANSWER: Start directly with your answer. NEVER explain internal reasoning steps or draft notes.
            2. LANGUAGE MATCHING MANDATE:
               • Respond in the EXACT SAME LANGUAGE and SCRIPT of user's query:
                 - English query -> 100% English.
                 - Bengali script query (বাংলা) -> 100% Bengali script.
                 - Banglish query (e.g. "amar budget set kore dao") -> 100% Banglish.
            3. MATHEMATICAL ACCURACY:
               • Daily Safe Spend = (Available Net ৳${netBalance.toInt()} - Target Savings) ÷ $daysLeft days.
               • Mess Meal Rate Formula = Total Bazaar (৳${messBazaar.toInt()}) ÷ Total Meals (${String.format("%.1f", messMeals)}) = ৳${String.format("%.2f", messRate)}/meal.
            4. ⚡ AI AUTONOMOUS BUDGET ACTION CAPABILITY:
               • Whenever user asks to set, allocate, plan, or update their budget (e.g. "set budget: Food 3000, Transport 1000", "save 2000 tk budget set kore dao"):
                 - FIRST, write a rich, detailed financial breakdown in user's language:
                   • 📌 **সারসংক্ষেপ / Overview**: Confirmation of the budget.
                   • 📊 **ক্যাটাগরি হিসাব / Category Breakdown**: Allocations with rationale.
                   • 💡 **সাশ্রয়ী টিপস / Money Saving Tips**: 2-3 specific student/bachelor money-saving hacks.
                   • 🎯 **দৈনিক সীমা / Daily Target**: Recommended safe daily spending limit.
                 - THEN on the very last line, output the action payload tag:
                   [ACTION:SET_BUDGET:{"Food":3000,"Transport":1000,"Shopping":500}]

            APP CAPABILITIES & ARCHITECTURE KNOWLEDGE:
            • 📱 **Wallet & Expense Tracker (হোম স্ক্রিন)**:
              - Tracks Incomes & Expenses with category, dynamic date-specific tags, voice/text notes.
              - Live "Daily Safe Spend" indicator dynamically guides student spending so money lasts all month.
            • 🎯 **Smart Budget Planner (বাজেট স্ক্রিন)**:
              - Category limits, Monthly Savings Goal deduction, Food-Centric budget toggle.
            • 🏠 **Mess Manager Pro (মেস ম্যানেজার ও রুমমেটস)**:
              - Live meal rate calculation, daily meal tracker (breakfast, lunch, dinner with 0.5/1 increments).
              - Multi-roommate bazaar shopping records (personal pocket vs mess fund split).
              - Member advance deposits, fixed utility costs (Rent, Cook, WiFi, Gas, Electricity).
              - Recovery Bin (রিসাইকেল বিন): Safely restore accidentally removed roommates.
              - Full Analytics & Export: Visual charts (Meal Share, Paid vs Cost matrix, Expense mix), A4 PDF Statement, CSV/Excel export, and WhatsApp report generator.
            • 🤝 **Debt & Loan Manager (ধার ও দেনা)**:
              - Tracks "They Owe Me" (পাওনা) and "I Owe Others" (দেনা), partial payments, and deadline alerts.
            • 📊 **Reports & Analytics (রিপোর্টস)**:
              - Monthly trend charts, category distributions, PDF statement export.

            CURRENT LIVE FINANCIAL DATA OF USER:
            • ইউজার এর নাম: $appUserName
            • আজকের তারিখ: $todayStr (মাসের বাকি দিন: $daysLeft দিন)
            • চলতি মাসের মোট আয় (Total Income): ৳${totalIncome.toInt()} BDT
            • চলতি মাসের মোট খরচ (Total Expenses): ৳${totalExpense.toInt()} BDT (${thisMonthExpenses.size} টি লেনদেন)
            • বর্তমান নিট ব্যালেন্স (Available Net Balance): ৳${netBalance.toInt()} BDT
            • দৈনিক নিরাপদ খরচের সীমা (Safe Daily Limit): ৳${dailySafe.toInt()} BDT/দিন
            • শীর্ষ খরচের খাতসমূহ: $topCatStr
            • মানুষের কাছে পাওনা টাকা (They Owe Me): ৳${totalLent.toInt()} BDT
            • মানুষকে দেনা টাকা (I Owe Others): ৳${totalBorrowed.toInt()} BDT

            সক্রিয় ধার ও দেনার তালিকা:
            $debtsStr

            🏠 রিয়েলটাইম মেস ম্যানেজার ডাটা (Current Month):
            • লাইভ মিল রেট: ৳${String.format("%.2f", messRate)} BDT
            • মোট মিল সংখ্যা: ${String.format("%.1f", messMeals)}
            • মোট বাজার খরচ: ৳${messBazaar.toInt()} BDT
            • মোট মেম্বার জমা (Deposits): ৳${messDeposits.toInt()} BDT
            • মেস ফিক্সড খরচ (Fixed Bills): ৳${messFixed.toInt()} BDT
            • মেসের অবশিষ্ট ফান্ড (Fund in Hand): ৳${messFund.toInt()} BDT
            • রুমমেটদের বর্তমান হিসাব ও ব্যালেন্স:
            $messMembersStr

            সর্বশেষ ট্রানজেকশন হিস্ট্রি:
            $txSummary
        """.trimIndent()
    }

    fun sendQuestion(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty() || isAiThinking) return

        val userMsg = ChatMessage(sender = "user", message = cleanQuery)
        val updatedMessages = currentSession.messages.toMutableList().apply { add(userMsg) }
        val updatedSession = currentSession.copy(messages = updatedMessages)
        currentSession = updatedSession
        AiChatHistoryManager.saveSession(context, updatedSession)
        allSessions = AiChatHistoryManager.getAllSessions(context)

        userInput = ""
        focusManager.clearFocus()
        isAiThinking = true

        coroutineScope.launch {
            var fullCleanAnswer = ""
            var budgetAction: Map<String, Double>? = null
            var generatedAiId = UUID.randomUUID().toString()

            try {
                delay(100)
                try {
                    listState.animateScrollToItem(currentSession.messages.size - 1)
                } catch (_: Exception) {}

                val systemInstruction = buildSystemPrompt()
                val aiResponse = GeminiAiClient.generateResponse(
                    context = context,
                    systemInstruction = systemInstruction,
                    conversationHistory = currentSession.messages,
                    userMessage = cleanQuery
                )

                isAiThinking = false

                // Extract any [ACTION:SET_BUDGET:{...}]
                val (cleanText, extractedMap) = AiBudgetActionManager.extractBudgetAction(aiResponse.answer)
                fullCleanAnswer = cleanText
                budgetAction = extractedMap
                streamingMessageId = generatedAiId

                // ⚡ 100% AUTONOMOUS EXECUTION: Auto-apply budget directly into app preferences!
                var isAutoApplied = false
                if (extractedMap != null && extractedMap.isNotEmpty()) {
                    isAutoApplied = AiBudgetActionManager.applyBudget(context, extractedMap)
                }

                val initialAiMsg = ChatMessage(
                    id = generatedAiId,
                    sender = "ai",
                    message = "",
                    thinkingProcess = aiResponse.thinkingProcess,
                    budgetActionMap = budgetAction,
                    isActionApplied = isAutoApplied
                )

                val initialList = (currentSession.messages + initialAiMsg).toMutableList()
                currentSession = currentSession.copy(messages = initialList)

                val tokens = fullCleanAnswer.split(Regex("(?<=\\s)|(?=\\n)"))
                val builder = StringBuilder()

                for ((idx, token) in tokens.withIndex()) {
                    builder.append(token)
                    val currentText = builder.toString()

                    val updatedList = currentSession.messages.map { msg ->
                        if (msg.id == generatedAiId) msg.copy(message = currentText) else msg
                    }.toMutableList()
                    currentSession = currentSession.copy(messages = updatedList)

                    if (idx % 2 == 0 || idx == tokens.size - 1) {
                        try {
                            if (!listState.isScrollInProgress) {
                                listState.scrollToItem(currentSession.messages.size - 1)
                            }
                        } catch (_: Exception) {}
                    }
                    delay(14)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                streamingMessageId = null
                isAiThinking = false

                if (fullCleanAnswer.isNotBlank()) {
                    val finalCompletedSession = currentSession.copy(
                        messages = currentSession.messages.map { msg ->
                            if (msg.id == generatedAiId) {
                                msg.copy(
                                    message = fullCleanAnswer,
                                    budgetActionMap = budgetAction
                                )
                            } else msg
                        }.toMutableList()
                    )
                    currentSession = finalCompletedSession
                    AiChatHistoryManager.saveSession(context, finalCompletedSession)
                    allSessions = AiChatHistoryManager.getAllSessions(context)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = cardBg,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Button
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textColor, modifier = Modifier.size(20.dp))
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Glowing AI Sparkle Orb
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF7B61FF), Color(0xFFFFD700)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Title & Online Status
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSession.title.ifBlank { "Bachelors AI" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isAiThinking) primaryColor else Color(0xFF34C759))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isAiThinking) "Analyzing finances..." else "Live Financial Advisor",
                                fontSize = 11.sp,
                                color = if (isAiThinking) primaryColor else Color.Gray,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Action Buttons Row (Clear separation, zero overlapping)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // New Chat Button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = primaryColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val newSess = AiChatHistoryManager.createNewSession()
                                    AiChatHistoryManager.saveSession(context, newSess)
                                    currentSession = newSess
                                    allSessions = AiChatHistoryManager.getAllSessions(context)
                                    Toast.makeText(context, "Started New Chat 💬", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AddComment, contentDescription = "New Chat", tint = primaryColor, modifier = Modifier.size(19.dp))
                            }
                        }

                        // Conversation History Button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)),
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showHistorySheet = true }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.History, contentDescription = "Chat History", tint = textColor, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        },
        containerColor = bgColor
    ) { paddingValues ->

        if (!isPro) {
            // Paywall Lock Screen for Non-PRO Users
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = cardBg,
                    border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color(0xFFFFD700), primaryColor))),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFD700).copy(alpha = 0.15f))
                                .border(1.5.dp, Color(0xFFFFD700), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(36.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Unlock Dynamic AI Financial Agent 🤖", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = textColor, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Powered by Gemini & OpenRouter LLM models with real-time reasoning over your transactions, mess expenses, category limits, and debt reminders in Bangla & English.",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { navController.navigate("subscription") },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                        ) {
                            Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color(0xFFFFD700))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upgrade to PRO to Access AI 👑", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        } else {
            // Full AI Chat UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Messages List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(currentSession.messages) { msg ->
                        val isStreaming = msg.id == streamingMessageId
                        ChatBubble(
                            message = msg,
                            isStreaming = isStreaming,
                            isDark = isDark,
                            primaryColor = primaryColor,
                            textColor = textColor,
                            onViewBudget = { navController.navigate("budget") },
                            onApplyBudget = { budgetMap ->
                                val success = AiBudgetActionManager.applyBudget(context, budgetMap)
                                if (success) {
                                    val updatedMessages = currentSession.messages.map { m ->
                                        if (m.id == msg.id) m.copy(isActionApplied = true) else m
                                    }.toMutableList()
                                    val updatedSession = currentSession.copy(messages = updatedMessages)
                                    currentSession = updatedSession
                                    AiChatHistoryManager.saveSession(context, updatedSession)
                                    Toast.makeText(context, "🎉 Budget successfully applied to your app!", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    }

                    if (isAiThinking) {
                        item {
                            ThinkingBubble(primaryColor = primaryColor, isDark = isDark)
                        }
                    }
                }

                // Quick Prompt Chips
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickQuestions) { q ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.08f)),
                            modifier = Modifier.clickable { sendQuestion(q) }
                        ) {
                            Text(
                                text = q,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = textColor,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // Input Bar
                Surface(
                    color = cardBg,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = userInput,
                            onValueChange = { userInput = it },
                            placeholder = { Text("Ask AI to analyze or set your budget...", fontSize = 14.sp, color = if (isDark) Color.Gray else Color.DarkGray) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { sendQuestion(userInput) }),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = if (isDark) Color.White.copy(0.15f) else Color.Black.copy(0.1f),
                                focusedTextColor = textColor,
                                unfocusedTextColor = textColor
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { sendQuestion(userInput) },
                            enabled = userInput.isNotBlank() && !isAiThinking,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (userInput.isNotBlank()) primaryColor else Color.Gray.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        // ================= 🕒 CONVERSATION SESSIONS HISTORY MODAL =================
        if (showHistorySheet) {
            ModalBottomSheet(
                onDismissRequest = { showHistorySheet = false },
                containerColor = cardBg,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.History, contentDescription = null, tint = primaryColor, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Conversation History", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                        }

                        Button(
                            onClick = {
                                val newSess = AiChatHistoryManager.createNewSession()
                                AiChatHistoryManager.saveSession(context, newSess)
                                currentSession = newSess
                                allSessions = AiChatHistoryManager.getAllSessions(context)
                                showHistorySheet = false
                                Toast.makeText(context, "Started New Chat 💬", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Chat", fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f))
                    Spacer(modifier = Modifier.height(10.dp))

                    if (allSessions.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No saved conversations yet.", color = Color.Gray, fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 420.dp)
                        ) {
                            items(allSessions) { sess ->
                                val isSelected = sess.id == currentSession.id
                                val timeFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                                val dateStr = timeFormat.format(Date(sess.updatedAt))

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) primaryColor.copy(alpha = 0.15f) else if (isDark) Color(0xFF262630) else Color(0xFFF2F2F7),
                                    border = if (isSelected) BorderStroke(1.dp, primaryColor) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentSession = sess
                                            showHistorySheet = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = sess.title,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 14.sp,
                                                color = if (isSelected) primaryColor else textColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "$dateStr • ${sess.messages.size} messages",
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                AiChatHistoryManager.deleteSession(context, sess.id)
                                                allSessions = AiChatHistoryManager.getAllSessions(context)
                                                if (currentSession.id == sess.id) {
                                                    currentSession = AiChatHistoryManager.getOrCreateLatestSession(context)
                                                }
                                                Toast.makeText(context, "Chat deleted", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color.Gray.copy(0.7f), modifier = Modifier.size(18.dp))
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
fun ChatBubble(
    message: ChatMessage,
    isStreaming: Boolean = false,
    isDark: Boolean,
    primaryColor: Color,
    textColor: Color,
    onViewBudget: () -> Unit = {},
    onApplyBudget: (Map<String, Double>) -> Unit = {}
) {
    val isUser = message.sender == "user"
    val clipboardManager = LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val context = LocalContext.current
    var isThinkingExpanded by remember { mutableStateOf(false) }

    val bubbleBg = if (isUser) primaryColor else if (isDark) Color(0xFF1E1E28) else Color(0xFFF0F0F5)
    val contentTextColor = if (isUser) Color.White else if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)

    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(primaryColor, Color(0xFFFFD700)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = bubbleBg,
            border = if (!isUser && isDark) BorderStroke(1.dp, Color.White.copy(0.12f)) else null,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {

                // Optional Collapsible Thinking Process Accordion
                if (!isUser && !message.thinkingProcess.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDark) Color(0xFF282836) else Color(0xFFE2E2EC),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isThinkingExpanded = !isThinkingExpanded }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("💭 Thought Process", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = primaryColor)
                            Icon(
                                imageVector = if (isThinkingExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = isThinkingExpanded) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDark) Color(0xFF161620) else Color(0xFFDCDCE6),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)
                        ) {
                            Text(
                                text = message.thinkingProcess,
                                fontSize = 11.sp,
                                color = if (isDark) Color(0xFFB0B0C0) else Color(0xFF4A4A58),
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Main Message Content with High-Contrast Rich Markdown
                if (isUser) {
                    Text(
                        text = message.message,
                        fontSize = 14.sp,
                        color = Color.White,
                        lineHeight = 20.sp
                    )
                } else {
                    FormattedMarkdownText(
                        text = message.message,
                        color = contentTextColor,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        primaryColor = primaryColor
                    )

                    // Blinking luxury cursor while live streaming
                    if (isStreaming) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "●",
                            color = primaryColor.copy(alpha = cursorAlpha),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // ⚡ 100% AUTONOMOUS AI BUDGET ACTION CARD
                if (!isUser && message.budgetActionMap != null && message.budgetActionMap.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDark) Color(0xFF1C261F) else Color(0xFFEBF8EE),
                        border = BorderStroke(1.dp, Color(0xFF34C759).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "AI Auto-Applied Budget",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color.White else Color(0xFF1C1C1E)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF34C759).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "LIVE ACTIVE ✅",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF34C759),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            message.budgetActionMap.forEach { (category, amount) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("• $category", fontSize = 12.sp, color = contentTextColor)
                                    Text("৳${amount.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34C759))
                                }
                            }

                            val totalSuggested = message.budgetActionMap.values.sum()
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.08f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Monthly Limit:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = contentTextColor)
                                Text("৳${totalSuggested.toInt()} BDT", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF34C759))
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = { onViewBudget() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.PieChart, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Budget Screen ➜", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }

                if (!isUser && !isStreaming) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = {
                                clipboardManager.setPrimaryClip(ClipData.newPlainText("AI Advice", message.message))
                                Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Gray, modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingBubble(primaryColor: Color, isDark: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(primaryColor, Color(0xFFFFD700)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isDark) Color(0xFF1E1E28) else Color(0xFFEBEBF5),
            modifier = Modifier.padding(4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = primaryColor, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text("AI Agent is analyzing your finances...", fontSize = 12.sp, color = if (isDark) Color(0xFFE0E0EC) else Color.Gray)
            }
        }
    }
}
