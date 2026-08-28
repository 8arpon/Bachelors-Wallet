package com.example.myapplication

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

object DataManager {

    var cachedDebts: List<DebtItem>? = null
    var cachedTransactions: List<TransactionEntry>? = null
    var cachedCategories: List<CustomCategory>? = null

    // --- TRANSACTIONS LOGIC (NEW) ---
    fun getTransactionsFlow(context: Context): Flow<List<TransactionEntry>> {
        return AppDatabase.getDatabase(context).transactionDao().getAllTransactions().onEach { cachedTransactions = it }
    }

    suspend fun getTransactionsSync(context: Context): List<TransactionEntry> = withContext(Dispatchers.IO) {
        AppDatabase.getDatabase(context).transactionDao().getAllTransactionsSync()
    }

    private fun autoBackupIfEnabled(context: Context) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val autoEnabled = prefs.getBoolean("pref_auto_cloud_backup", true)
        if (autoEnabled && CloudSyncManager.isUserLoggedIn() && PremiumManager.isPremium(context)) {
            CloudSyncManager.backupToCloud(context) { _, _ -> }
        }
    }

    fun saveTransaction(context: Context, transaction: TransactionEntry) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(context).transactionDao().insertTransaction(transaction)
            autoBackupIfEnabled(context)

            // Two-way sync to MessManager if this is a Mess transaction
            if (isLinkMessToWallet(context) && transaction.category.contains("Mess", ignoreCase = true)) {
                if (transaction.id.startsWith("mess_dep_")) {
                    MessManager.syncDepositFromWallet(context, transaction.id.removePrefix("mess_dep_"), transaction.amount, transaction.date)
                } else if (transaction.id.startsWith("mess_bzr_")) {
                    MessManager.syncBazaarFromWallet(context, transaction.id.removePrefix("mess_bzr_"), transaction.amount, transaction.date)
                } else {
                    MessManager.syncWalletMessExpense(context, transaction.id, transaction.amount, transaction.date)
                }
            }
        }
    }

    fun deleteTransaction(context: Context, transaction: TransactionEntry) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(context).transactionDao().deleteTransaction(transaction)
            autoBackupIfEnabled(context)

            if (isLinkMessToWallet(context) && (transaction.category.contains("Mess", ignoreCase = true) || transaction.id.startsWith("mess_"))) {
                MessManager.deleteWalletSyncedRecord(context, transaction.id)
            }
        }
    }

    fun deleteTransactionById(context: Context, id: String) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(context).transactionDao().deleteTransactionById(id)
            autoBackupIfEnabled(context)

            if (isLinkMessToWallet(context) && (id.startsWith("mess_") || id.startsWith("wallet_"))) {
                MessManager.deleteWalletSyncedRecord(context, id)
            }
        }
    }

    fun deleteMessTransactions(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val list = AppDatabase.getDatabase(context).transactionDao().getAllTransactionsSync()
            val messTxs = list.filter { it.id.startsWith("mess_") || it.category.contains("Mess", ignoreCase = true) }
            messTxs.forEach {
                AppDatabase.getDatabase(context).transactionDao().deleteTransaction(it)
            }
            autoBackupIfEnabled(context)
        }
    }

    // --- CATEGORY MANAGER LOGIC ---
    val DEFAULT_CATEGORIES = listOf(
        "☕ Breakfast",
        "🍱 Lunch",
        "🍽️ Dinner",
        "🍔 Snacks",
        "🏠 Mess",
        "🚌 Transport",
        "🛍️ Shopping",
        "🧾 Bills",
        "💊 Health",
        "📚 Education",
        "🎬 Entertainment",
        "🏷️ Others"
    )

    fun getCategoriesFlow(context: Context): Flow<List<CustomCategory>> {
        return AppDatabase.getDatabase(context).categoryDao().getAllCategories().onEach { cachedCategories = it }
    }

    fun addCategory(context: Context, categoryName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(context).categoryDao().insertCategory(CustomCategory(name = categoryName))
            autoBackupIfEnabled(context)
        }
    }

    fun deleteCategory(context: Context, categoryName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(context).categoryDao().deleteCategory(categoryName)
            autoBackupIfEnabled(context)
        }
    }

    fun getDisabledCategories(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return prefs.getStringSet("disabled_categories_set", emptySet()) ?: emptySet()
    }

    fun setCategoryEnabled(context: Context, categoryName: String, enabled: Boolean) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val disabled = (prefs.getStringSet("disabled_categories_set", emptySet()) ?: emptySet()).toMutableSet()
        if (enabled) {
            disabled.remove(categoryName)
        } else {
            disabled.add(categoryName)
        }
        prefs.edit().putStringSet("disabled_categories_set", disabled).apply()
    }

    fun isMessCentricMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("pref_mess_centric_mode", false)
    }

    fun isLinkMessToWallet(context: Context): Boolean {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("pref_link_mess_to_wallet", true)
    }

    fun getActiveCategories(context: Context, customCategories: List<CustomCategory>): List<String> {
        val disabled = getDisabledCategories(context)
        val isMessCentric = isMessCentricMode(context)

        val all = (DEFAULT_CATEGORIES + customCategories.map { it.name }).distinct()
        var active = all.filter { !disabled.contains(it) }

        if (isMessCentric) {
            val foodNames = listOf("breakfast", "lunch", "dinner", "food")
            active = active.filter { cat ->
                val clean = cat.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
                clean !in foodNames
            }
            if (!active.any { it.contains("Mess", ignoreCase = true) }) {
                active = listOf("🏠 Mess") + active
            }
        } else {
            // When Mess Centric is OFF, do NOT show Mess category in personal wallet categories
            active = active.filter { !it.contains("Mess", ignoreCase = true) }
        }

        return if (active.isEmpty()) listOf("🏷️ Others") else active
    }

    fun stripEmoji(name: String): String {
        return name.replace(Regex("^[\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}\\p{P}\\s]+"), "").trim().ifEmpty { name.trim() }
    }

    fun isShowEmojisEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("pref_show_category_emojis", true)
    }

    fun setShowEmojisEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("pref_show_category_emojis", enabled).apply()
    }

    fun formatCategoryDisplay(context: Context, categoryName: String): String {
        return if (isShowEmojisEnabled(context)) categoryName else stripEmoji(categoryName)
    }

    fun isDuplicateCategoryName(allCategories: List<String>, newName: String): Boolean {
        val cleanNew = newName.replace(Regex("[^a-zA-Z0-9]"), "").lowercase().trim()
        if (cleanNew.isEmpty()) return false
        return allCategories.any { existing ->
            val cleanExisting = existing.replace(Regex("[^a-zA-Z0-9]"), "").lowercase().trim()
            cleanExisting == cleanNew
        }
    }

    // --- DEBT MANAGER LOGIC ---
    suspend fun getDebts(context: Context): List<DebtItem> = withContext(Dispatchers.IO) {
        AppDatabase.getDatabase(context).debtDao().getAllDebtsSync()
    }

    fun addDebt(context: Context, debt: DebtItem) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(context).debtDao().insertDebt(debt)
            autoBackupIfEnabled(context)
        }
    }

    fun updateDebt(context: Context, updatedDebt: DebtItem) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(context).debtDao().updateDebt(updatedDebt)
            autoBackupIfEnabled(context)
        }
    }

    fun deleteDebt(context: Context, debtId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(context).debtDao().deleteDebtById(debtId)
            autoBackupIfEnabled(context)
        }
    }

    fun getDebtsFlow(context: Context): Flow<List<DebtItem>> {
        return AppDatabase.getDatabase(context).debtDao().getAllDebts().onEach { cachedDebts = it }
    }

    // --- NOTIFICATION MANAGER LOGIC ---
    suspend fun getNotifications(context: Context): List<AppNotification> = withContext(Dispatchers.IO) {
        AppDatabase.getDatabase(context).notificationDao().getAllNotificationsSync()
    }

    fun saveNotification(context: Context, notification: AppNotification) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(context).notificationDao().insertNotification(notification)
        }
    }

    fun markAllNotificationsAsRead(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(context).notificationDao().markAllAsRead()
        }
    }

    fun deleteNotification(context: Context, notificationId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(context).notificationDao().deleteNotificationById(notificationId)
        }
    }

    fun clearAllNotifications(context: Context, keepDebts: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = AppDatabase.getDatabase(context).notificationDao()
            if (keepDebts) {
                dao.clearAllExceptDebts()
            } else {
                dao.clearAll()
            }
        }
    }

    fun getNotificationsFlow(context: Context): Flow<List<AppNotification>> {
        return AppDatabase.getDatabase(context).notificationDao().getAllNotifications()
    }

    // --- SCHEDULED TRANSACTIONS LOGIC ---
    fun getScheduledTransactionsFlow(context: Context): Flow<List<ScheduledTransaction>> {
        return AppDatabase.getDatabase(context).scheduledTransactionDao().getAllScheduled()
    }

    fun addScheduledTransaction(context: Context, item: ScheduledTransaction) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(context).scheduledTransactionDao().insertScheduled(item)
        }
    }

    fun deleteScheduledTransaction(context: Context, item: ScheduledTransaction) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(context).scheduledTransactionDao().deleteScheduled(item)
        }
    }

    fun processScheduledTransactions(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val activeScheduled = db.scheduledTransactionDao().getActiveScheduledSync()
            val now = Date()

            activeScheduled.forEach { scheduled ->
                var nextDate = scheduled.nextExecutionDate
                var hasRun = false

                // Process all overdue occurrences
                while (nextDate.before(now) || nextDate == now) {
                    val entry = TransactionEntry(
                        date = nextDate,
                        type = scheduled.type,
                        category = scheduled.category,
                        amount = scheduled.amount
                    )
                    db.transactionDao().insertTransaction(entry)

                    val notification = AppNotification(
                        title = "Repeating Bill Logged",
                        message = "Logged automatic ${scheduled.type.name.lowercase()} for ${scheduled.title}: ৳${String.format(Locale.US, "%,.0f", scheduled.amount)}",
                        timestamp = System.currentTimeMillis(),
                        type = "SYSTEM"
                    )
                    db.notificationDao().insertNotification(notification)

                    nextDate = calculateNextOccurrence(nextDate, scheduled.frequency)
                    hasRun = true
                }

                if (hasRun) {
                    db.scheduledTransactionDao().updateScheduled(scheduled.copy(nextExecutionDate = nextDate))
                }
            }
        }
    }

    private fun calculateNextOccurrence(current: Date, frequency: String): Date {
        val cal = Calendar.getInstance().apply { time = current }
        when (frequency) {
            "Daily" -> cal.add(Calendar.DAY_OF_YEAR, 1)
            "Weekly" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            "Monthly" -> cal.add(Calendar.MONTH, 1)
            else -> cal.add(Calendar.MONTH, 1)
        }
        return cal.time
    }

    // --- SYSTEM LOGIC ---
    fun clearAllData(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            db.transactionDao().deleteAll()
            db.debtDao().deleteAll()
            db.categoryDao().deleteAll()
            db.notificationDao().clearAll()
            db.scheduledTransactionDao().deleteAll()
        }
    }
}