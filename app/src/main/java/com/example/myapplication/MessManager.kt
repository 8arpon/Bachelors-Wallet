package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

object MessManager {
    private const val PREFS_NAME = "mess_manager_prefs"
    private const val KEY_MEMBERS = "mess_members"
    private const val KEY_ARCHIVED_MEMBERS = "mess_archived_members"
    private const val KEY_MEALS = "mess_daily_meals"
    private const val KEY_BAZAAR = "mess_bazaar_records"
    private const val KEY_DEPOSITS = "mess_deposits"
    private const val KEY_FIXED_COSTS = "mess_fixed_costs"
    private const val KEY_MESS_NAME = "mess_name"

    private val gson = Gson()

    // Reactive Compose Lists for instantaneous UI updates
    val members = mutableStateListOf<MessMember>()
    val archivedMembers = mutableStateListOf<MessMember>()
    val dailyMeals = mutableStateListOf<DailyMealRecord>()
    val bazaarRecords = mutableStateListOf<MessBazaarRecord>()
    val depositRecords = mutableStateListOf<MessDepositRecord>()
    val fixedCosts = mutableStateListOf<MessFixedExpense>()

    fun initialize(context: Context) {
        val prefs = getPrefs(context)
        members.clear()
        archivedMembers.clear()
        dailyMeals.clear()
        bazaarRecords.clear()
        depositRecords.clear()
        fixedCosts.clear()

        val membersJson = prefs.getString(KEY_MEMBERS, "[]")
        val archivedJson = prefs.getString(KEY_ARCHIVED_MEMBERS, "[]")
        val mealsJson = prefs.getString(KEY_MEALS, "[]")
        val bazaarJson = prefs.getString(KEY_BAZAAR, "[]")
        val depositsJson = prefs.getString(KEY_DEPOSITS, "[]")
        val fixedJson = prefs.getString(KEY_FIXED_COSTS, "[]")

        val memberType = object : TypeToken<List<MessMember>>() {}.type
        val mealType = object : TypeToken<List<DailyMealRecord>>() {}.type
        val bazaarType = object : TypeToken<List<MessBazaarRecord>>() {}.type
        val depositType = object : TypeToken<List<MessDepositRecord>>() {}.type
        val fixedType = object : TypeToken<List<MessFixedExpense>>() {}.type

        val loadedMembers: List<MessMember> = (gson.fromJson(membersJson, memberType) ?: emptyList<MessMember>())
            .map { m -> m.copy(name = m.name ?: "", phone = m.phone ?: "", roomNo = m.roomNo ?: "", role = m.role ?: "Member") }

        val loadedArchived: List<MessMember> = (gson.fromJson(archivedJson, memberType) ?: emptyList<MessMember>())
            .map { m -> m.copy(name = m.name ?: "", phone = m.phone ?: "", roomNo = m.roomNo ?: "", role = m.role ?: "Member") }

        val loadedMeals: List<DailyMealRecord> = (gson.fromJson(mealsJson, mealType) ?: emptyList<DailyMealRecord>())
            .map { d -> d.copy(dateString = d.dateString ?: "", memberId = d.memberId ?: "") }

        val loadedBazaar: List<MessBazaarRecord> = (gson.fromJson(bazaarJson, bazaarType) ?: emptyList<MessBazaarRecord>())
            .map { b ->
                val bIds = b.buyerMemberIds ?: (if (!b.buyerMemberId.isNullOrEmpty()) listOf(b.buyerMemberId) else emptyList())
                b.copy(
                    buyerMemberIds = bIds,
                    buyerMemberId = b.buyerMemberId ?: (bIds.firstOrNull() ?: ""),
                    buyerName = b.buyerName ?: "Roommate",
                    items = b.items ?: "Bazaar / Groceries"
                )
            }

        val loadedDeposits: List<MessDepositRecord> = (gson.fromJson(depositsJson, depositType) ?: emptyList<MessDepositRecord>())
            .map { dep -> dep.copy(memberId = dep.memberId ?: "", memberName = dep.memberName ?: "Roommate", note = dep.note ?: "") }

        val loadedFixed: List<MessFixedExpense> = (gson.fromJson(fixedJson, fixedType) ?: emptyList<MessFixedExpense>())
            .map { f -> f.copy(title = f.title ?: "Fixed Cost") }

        members.addAll(loadedMembers)
        archivedMembers.addAll(loadedArchived)
        dailyMeals.addAll(loadedMeals)
        bazaarRecords.addAll(loadedBazaar)
        depositRecords.addAll(loadedDeposits)
        fixedCosts.addAll(loadedFixed)

        if (members.isEmpty()) {
            val appUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val defaultName = appUser?.displayName?.takeIf { it.isNotBlank() } ?: "Manager"
            members.add(MessMember(name = defaultName, role = "Manager", isPrimaryManager = true))
            saveData(context, triggerCloudBackup = false)
        }
    }

    private fun saveData(context: Context, triggerCloudBackup: Boolean = true) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putString(KEY_MEMBERS, gson.toJson(members.toList()))
            .putString(KEY_ARCHIVED_MEMBERS, gson.toJson(archivedMembers.toList()))
            .putString(KEY_MEALS, gson.toJson(dailyMeals.toList()))
            .putString(KEY_BAZAAR, gson.toJson(bazaarRecords.toList()))
            .putString(KEY_DEPOSITS, gson.toJson(depositRecords.toList()))
            .putString(KEY_FIXED_COSTS, gson.toJson(fixedCosts.toList()))
            .apply()

        if (triggerCloudBackup && CloudSyncManager.isUserLoggedIn()) {
            CloudSyncManager.backupToCloud(context) { _, _ -> }
        }
    }

    fun loadFromCloud(
        context: Context,
        membersJson: String,
        archivedJson: String,
        mealsJson: String,
        bazaarJson: String,
        depositsJson: String,
        fixedJson: String
    ) {
        val memberType = object : TypeToken<List<MessMember>>() {}.type
        val mealType = object : TypeToken<List<DailyMealRecord>>() {}.type
        val bazaarType = object : TypeToken<List<MessBazaarRecord>>() {}.type
        val depositType = object : TypeToken<List<MessDepositRecord>>() {}.type
        val fixedType = object : TypeToken<List<MessFixedExpense>>() {}.type

        val cloudMembers: List<MessMember> = gson.fromJson(membersJson, memberType) ?: emptyList()
        val cloudArchived: List<MessMember> = gson.fromJson(archivedJson, memberType) ?: emptyList()
        val cloudMeals: List<DailyMealRecord> = gson.fromJson(mealsJson, mealType) ?: emptyList()
        val cloudBazaar: List<MessBazaarRecord> = gson.fromJson(bazaarJson, bazaarType) ?: emptyList()
        val cloudDeposits: List<MessDepositRecord> = gson.fromJson(depositsJson, depositType) ?: emptyList()
        val cloudFixed: List<MessFixedExpense> = gson.fromJson(fixedJson, fixedType) ?: emptyList()

        members.clear()
        members.addAll(cloudMembers)

        archivedMembers.clear()
        archivedMembers.addAll(cloudArchived)

        dailyMeals.clear()
        dailyMeals.addAll(cloudMeals)

        bazaarRecords.clear()
        bazaarRecords.addAll(cloudBazaar)

        depositRecords.clear()
        depositRecords.addAll(cloudDeposits)

        fixedCosts.clear()
        fixedCosts.addAll(cloudFixed)

        saveData(context, triggerCloudBackup = false)
    }

    fun resetMessData(context: Context) {
        DataManager.deleteMessTransactions(context)
        members.clear()
        archivedMembers.clear()
        dailyMeals.clear()
        bazaarRecords.clear()
        depositRecords.clear()
        fixedCosts.clear()
        saveData(context, triggerCloudBackup = true)
    }

    // --- MEMBERS ---
    fun addMember(context: Context, member: MessMember) {
        members.add(member)
        saveData(context)
    }

    fun updateMember(context: Context, updated: MessMember) {
        val idx = members.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            members[idx] = updated
            saveData(context)
        }
    }

    fun removeMember(context: Context, memberId: String) {
        val target = members.find { it.id == memberId }
        if (target != null) {
            members.remove(target)
            archivedMembers.add(0, target)
            saveData(context)
        }
    }

    fun restoreMember(context: Context, memberId: String) {
        val target = archivedMembers.find { it.id == memberId }
        if (target != null) {
            archivedMembers.remove(target)
            members.add(target)
            saveData(context)
        }
    }

    fun permanentlyDeleteMember(context: Context, memberId: String) {
        archivedMembers.removeAll { it.id == memberId }
        members.removeAll { it.id == memberId }
        dailyMeals.removeAll { it.memberId == memberId }
        bazaarRecords.removeAll { it.involvesMember(memberId) }
        depositRecords.removeAll { it.memberId == memberId }
        saveData(context)
    }

    // --- DAILY MEALS ---
    fun getMealForDateAndMember(dateStr: String, memberId: String): DailyMealRecord {
        val existing = dailyMeals.find { it.dateString == dateStr && it.memberId == memberId }
        if (existing != null) return existing
        return DailyMealRecord(dateString = dateStr, memberId = memberId, breakfast = 0.0, lunch = 0.0, dinner = 0.0)
    }

    fun updateMeal(context: Context, dateStr: String, memberId: String, breakfast: Double, lunch: Double, dinner: Double) {
        val idx = dailyMeals.indexOfFirst { it.dateString == dateStr && it.memberId == memberId }
        if (idx >= 0) {
            dailyMeals[idx] = dailyMeals[idx].copy(breakfast = breakfast, lunch = lunch, dinner = dinner)
        } else {
            dailyMeals.add(DailyMealRecord(dateString = dateStr, memberId = memberId, breakfast = breakfast, lunch = lunch, dinner = dinner))
        }
        saveData(context)
    }

    // --- HELPER TO CHECK IF MEMBER IS CURRENT LOGGED IN USER / MANAGER ---
    fun isMeOrManager(memberId: String): Boolean {
        val member = members.find { it.id == memberId } ?: return false
        val appName = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName
        return member.isPrimaryManager || 
               member.role.equals("Manager", ignoreCase = true) || 
               member.name.equals("Me", ignoreCase = true) || 
               member.name.equals("Me (Manager)", ignoreCase = true) ||
               (!appName.isNullOrBlank() && member.name.equals(appName, ignoreCase = true))
    }

    // --- BAZAAR ---
    fun addBazaar(context: Context, record: MessBazaarRecord) {
        bazaarRecords.add(0, record)
        saveData(context)

        // Automatically reflect in Main Wallet Balance if paid from personal pocket
        if (DataManager.isLinkMessToWallet(context) && record.isPaidFromPersonalPocket) {
            val involvesMe = (record.buyerMemberIds ?: emptyList()).any { isMeOrManager(it) } || isMeOrManager(record.buyerMemberId)
            if (involvesMe) {
                DataManager.saveTransaction(
                    context,
                    TransactionEntry(
                        id = "mess_bzr_${record.id}",
                        date = java.util.Date(record.timestamp),
                        type = TransactionType.EXPENSE,
                        category = "🏠 Mess",
                        amount = record.amount
                    )
                )
            }
        }
    }

    fun updateBazaar(context: Context, updated: MessBazaarRecord) {
        val idx = bazaarRecords.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            bazaarRecords[idx] = updated
            saveData(context)
            if (DataManager.isLinkMessToWallet(context)) {
                if (updated.isPaidFromPersonalPocket) {
                    val involvesMe = (updated.buyerMemberIds ?: emptyList()).any { isMeOrManager(it) } || isMeOrManager(updated.buyerMemberId)
                    if (involvesMe) {
                        DataManager.saveTransaction(
                            context,
                            TransactionEntry(
                                id = "mess_bzr_${updated.id}",
                                date = java.util.Date(updated.timestamp),
                                type = TransactionType.EXPENSE,
                                category = "🏠 Mess",
                                amount = updated.amount
                            )
                        )
                    } else {
                        DataManager.deleteTransactionById(context, "mess_bzr_${updated.id}")
                    }
                } else {
                    DataManager.deleteTransactionById(context, "mess_bzr_${updated.id}")
                }
            }
        }
    }

    fun deleteBazaar(context: Context, recordId: String) {
        bazaarRecords.removeAll { it.id == recordId }
        saveData(context)
        DataManager.deleteTransactionById(context, "mess_bzr_${recordId}")
    }

    // --- DEPOSITS ---
    fun addDeposit(context: Context, record: MessDepositRecord) {
        depositRecords.add(0, record)
        saveData(context)

        // Automatically deduct from Main Wallet Balance if deposit was made by me/manager
        if (DataManager.isLinkMessToWallet(context) && isMeOrManager(record.memberId)) {
            DataManager.saveTransaction(
                context,
                TransactionEntry(
                    id = "mess_dep_${record.id}",
                    date = java.util.Date(record.timestamp),
                    type = TransactionType.EXPENSE,
                    category = "🏠 Mess",
                    amount = record.amount
                )
            )
        }
    }

    fun updateDeposit(context: Context, updated: MessDepositRecord) {
        val idx = depositRecords.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            depositRecords[idx] = updated
            saveData(context)
            if (DataManager.isLinkMessToWallet(context)) {
                if (isMeOrManager(updated.memberId)) {
                    DataManager.saveTransaction(
                        context,
                        TransactionEntry(
                            id = "mess_dep_${updated.id}",
                            date = java.util.Date(updated.timestamp),
                            type = TransactionType.EXPENSE,
                            category = "🏠 Mess",
                            amount = updated.amount
                        )
                    )
                } else {
                    DataManager.deleteTransactionById(context, "mess_dep_${updated.id}")
                }
            }
        }
    }

    fun deleteDeposit(context: Context, recordId: String) {
        depositRecords.removeAll { it.id == recordId }
        saveData(context)
        DataManager.deleteTransactionById(context, "mess_dep_${recordId}")
    }

    // --- 🔄 BI-DIRECTIONAL WALLET SYNC METHODS ---
    fun getOrCreatePrimaryManager(context: Context): MessMember {
        var mgr = members.find { it.isPrimaryManager || it.role.equals("Manager", ignoreCase = true) || isMeOrManager(it.id) }
        if (mgr == null) {
            val appName = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName ?: "Manager"
            mgr = MessMember(name = appName, role = "Manager", isPrimaryManager = true)
            members.add(0, mgr)
            saveData(context)
        }
        return mgr
    }

    fun syncDepositFromWallet(context: Context, depositId: String, newAmount: Double, date: java.util.Date) {
        val idx = depositRecords.indexOfFirst { it.id == depositId }
        if (idx >= 0) {
            depositRecords[idx] = depositRecords[idx].copy(amount = newAmount, timestamp = date.time)
            saveData(context)
        }
    }

    fun syncBazaarFromWallet(context: Context, bazaarId: String, newAmount: Double, date: java.util.Date) {
        val idx = bazaarRecords.indexOfFirst { it.id == bazaarId }
        if (idx >= 0) {
            bazaarRecords[idx] = bazaarRecords[idx].copy(amount = newAmount, timestamp = date.time)
            saveData(context)
        }
    }

    fun syncWalletMessExpense(context: Context, txId: String, amount: Double, date: java.util.Date) {
        val manager = getOrCreatePrimaryManager(context)
        val depositId = "wallet_$txId"
        val idx = depositRecords.indexOfFirst { it.id == depositId }
        if (idx >= 0) {
            depositRecords[idx] = depositRecords[idx].copy(amount = amount, timestamp = date.time)
        } else {
            depositRecords.add(0, MessDepositRecord(
                id = depositId,
                memberId = manager.id,
                memberName = manager.displayName,
                amount = amount,
                note = "Daily Expense Entry",
                timestamp = date.time
            ))
        }
        saveData(context)
    }

    fun deleteWalletSyncedRecord(context: Context, txId: String) {
        val depId = txId.removePrefix("mess_dep_")
        val bzrId = txId.removePrefix("mess_bzr_")
        val walletDepId = "wallet_$txId"
        depositRecords.removeAll { it.id == depId || it.id == walletDepId || it.id == txId }
        bazaarRecords.removeAll { it.id == bzrId || it.id == txId }
        saveData(context)
    }

    // --- FIXED COSTS ---
    fun addOrUpdateFixedCost(context: Context, cost: MessFixedExpense) {
        val idx = fixedCosts.indexOfFirst { it.id == cost.id }
        if (idx >= 0) {
            fixedCosts[idx] = cost
        } else {
            fixedCosts.add(cost)
        }
        saveData(context)
    }

    fun deleteFixedCost(context: Context, costId: String) {
        fixedCosts.removeAll { it.id == costId }
        saveData(context)
    }

    // --- MONTH UTILITIES ---
    fun getMonthKey(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        return sdf.format(Date(timestamp))
    }

    fun getCurrentMonthKey(): String {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        return sdf.format(Date())
    }

    // --- CALCULATIONS (Month-Aware) ---
    fun getTotalBazaar(monthKey: String = getCurrentMonthKey()): Double {
        return bazaarRecords.filter { getMonthKey(it.timestamp) == monthKey }.sumOf { it.amount }
    }

    fun getTotalDeposits(monthKey: String = getCurrentMonthKey()): Double {
        return depositRecords.filter { getMonthKey(it.timestamp) == monthKey }.sumOf { it.amount }
    }

    fun getFundInHand(monthKey: String = getCurrentMonthKey()): Double {
        val deposits = getTotalDeposits(monthKey)
        val fundBazaar = bazaarRecords.filter { getMonthKey(it.timestamp) == monthKey && !it.isPaidFromPersonalPocket }.sumOf { it.amount }
        return deposits - fundBazaar
    }

    fun getTotalFixedCosts(): Double = fixedCosts.sumOf { it.amount }

    fun getTotalMeals(monthKey: String = getCurrentMonthKey()): Double {
        return dailyMeals.filter { it.dateString.startsWith(monthKey) }.sumOf { it.totalDayMeals }
    }

    fun getMealRate(monthKey: String = getCurrentMonthKey()): Double {
        val totalMeals = getTotalMeals(monthKey)
        val totalBazaar = getTotalBazaar(monthKey)
        return if (totalMeals > 0) totalBazaar / totalMeals else 0.0
    }

    fun getMemberSummaries(monthKey: String = getCurrentMonthKey()): List<MemberSummary> {
        val mealRate = getMealRate(monthKey)
        val fixedShare = if (members.isNotEmpty()) getTotalFixedCosts() / members.size else 0.0

        return members.map { member ->
            val memMeals = dailyMeals.filter { it.dateString.startsWith(monthKey) && it.memberId == member.id }.sumOf { it.totalDayMeals }
            val memMealCost = memMeals * mealRate
            val memDeposits = depositRecords.filter { getMonthKey(it.timestamp) == monthKey && it.memberId == member.id }.sumOf { it.amount }
            val memPersonalBazaar = bazaarRecords.filter { getMonthKey(it.timestamp) == monthKey && it.involvesMember(member.id) && it.isPaidFromPersonalPocket }.sumOf { it.amount }
            val memTotalBazaarShopped = bazaarRecords.filter { getMonthKey(it.timestamp) == monthKey && it.involvesMember(member.id) }.sumOf { it.amount }
            val totalPaid = memDeposits + memPersonalBazaar
            val totalCost = memMealCost + fixedShare
            val balance = totalPaid - totalCost

            MemberSummary(
                member = member,
                totalMeals = memMeals,
                mealCost = memMealCost,
                fixedCostShare = fixedShare,
                totalCost = totalCost,
                totalDeposits = memDeposits,
                totalBazaarSpent = memTotalBazaarShopped,
                personalBazaarPaid = memPersonalBazaar,
                netBalance = balance
            )
        }
    }

    fun getMemberSummary(memberId: String, monthKey: String = getCurrentMonthKey()): MemberSummary? {
        return getMemberSummaries(monthKey).find { it.member.id == memberId }
    }

    fun getMemberBazaars(memberId: String, monthKey: String = getCurrentMonthKey()): List<MessBazaarRecord> {
        return bazaarRecords.filter { getMonthKey(it.timestamp) == monthKey && it.involvesMember(memberId) }
    }

    fun generateWhatsAppReport(monthKey: String = getCurrentMonthKey(), messName: String = "Bachelors Mess"): String {
        val displayMonthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val parseFormat = SimpleDateFormat("yyyy-MM", Locale.US)
        val monthDisplay = try {
            val date = parseFormat.parse(monthKey)
            if (date != null) displayMonthFormat.format(date) else monthKey
        } catch (e: Exception) {
            monthKey
        }

        val mealRate = getMealRate(monthKey)
        val totalBazaar = getTotalBazaar(monthKey)
        val totalFixed = getTotalFixedCosts()
        val totalMeals = getTotalMeals(monthKey)
        val summaries = getMemberSummaries(monthKey)

        val sb = StringBuilder()
        sb.append("📋 *${messName.uppercase()} STATEMENT ($monthDisplay)* 📋\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("🛒 Total Bazaar: ৳${String.format("%,.0f", totalBazaar)}\n")
        sb.append("🏠 Fixed Costs (Rent/Cook/WiFi): ৳${String.format("%,.0f", totalFixed)}\n")
        sb.append("🍽️ Total Meals: ${String.format("%.1f", totalMeals)}\n")
        sb.append("🔥 *Meal Rate: ৳${String.format("%.2f", mealRate)} / meal*\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━\n\n")
        sb.append("👥 *INDIVIDUAL SUMMARY:*\n")

        summaries.forEach { s ->
            sb.append("\n👤 *${s.member.name}* (${s.member.role})\n")
            sb.append("  • Meals: ${String.format("%.1f", s.totalMeals)} × ৳${String.format("%.2f", mealRate)} = ৳${String.format("%,.0f", s.mealCost)}\n")
            if (s.fixedCostShare > 0) {
                sb.append("  • Fixed Share: ৳${String.format("%,.0f", s.fixedCostShare)}\n")
            }
            sb.append("  • Total Cost: ৳${String.format("%,.0f", s.totalCost)}\n")
            sb.append("  • Total Paid: ৳${String.format("%,.0f", s.totalDeposits + s.totalBazaarSpent)}\n")
            if (s.netBalance >= 0) {
                sb.append("  • *Status: ✅ Gets Refund ৳${String.format("%,.0f", s.netBalance)}*\n")
            } else {
                sb.append("  • *Status: ⚠️ DUE ৳${String.format("%,.0f", -s.netBalance)}*\n")
            }
        }

        sb.append("\n━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("Generated by *Bachelors-Wallet App* 📱✨")
        return sb.toString()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
