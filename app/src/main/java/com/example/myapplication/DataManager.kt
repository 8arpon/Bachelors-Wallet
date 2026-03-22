package com.example.myapplication

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.*

enum class SaveOp { OVERWRITE, ADD, SUB }

object DataManager {

    // --- EXPENSES LOGIC ---
    fun getExpenses(context: Context): List<DailyExpense> {
        // Since getExpenses is often called synchronously, we might need to block or
        // ideally refactor. But for now, to keep your UI working without major changes:
        var expenses: List<DailyExpense> = emptyList()
        val dao = AppDatabase.getDatabase(context).expenseDao()
        // Note: In a real app, you shouldn't run DB queries on the main thread like this.
        // This is a quick fix to map your existing synchronous calls.
        Thread { expenses = dao.getAllExpensesSync() }.apply { start(); join() }
        return expenses
    }

    private fun saveExpenseToDb(context: Context, expense: DailyExpense) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(context).expenseDao().insertExpense(expense)

            // HIGHLIGHT: Data entry howar sathe sathe auto backup hobe
            if (CloudSyncManager.isUserLoggedIn()) {
                CloudSyncManager.backupToCloud(context) { _, _ -> }
            }
        }
    }

    fun addIncome(context: Context, date: Date, amount: Double, op: SaveOp = SaveOp.OVERWRITE) {
        val expenses = getExpenses(context).toMutableList()
        val index = expenses.indexOfFirst { isSameDay(it.date, date) }

        val newExpense = if (index != -1) {
            val old = expenses[index]
            val newAmount = when (op) {
                SaveOp.OVERWRITE -> amount
                SaveOp.ADD -> old.income + amount
                SaveOp.SUB -> maxOf(0.0, old.income - amount)
            }
            old.copy(income = newAmount)
        } else {
            val newAmount = if (op == SaveOp.SUB) 0.0 else amount
            DailyExpense(date = date, income = newAmount, breakfast = 0.0, lunch = 0.0, dinner = 0.0, others = 0.0)
        }
        saveExpenseToDb(context, newExpense)
    }

    fun addExpense(context: Context, date: Date, category: ExpenseCategory, amount: Double, op: SaveOp = SaveOp.OVERWRITE) {
        val expenses = getExpenses(context).toMutableList()
        val index = expenses.indexOfFirst { isSameDay(it.date, date) }

        var b = 0.0; var l = 0.0; var d = 0.0; var o = 0.0; var i = 0.0
        var id = UUID.randomUUID().toString()

        if (index != -1) {
            val old = expenses[index]
            b = old.breakfast; l = old.lunch; d = old.dinner; o = old.others; i = old.income; id = old.id
        }

        fun updateVal(oldVal: Double, amt: Double): Double {
            return when (op) {
                SaveOp.OVERWRITE -> amt
                SaveOp.ADD -> oldVal + amt
                SaveOp.SUB -> maxOf(0.0, oldVal - amt)
            }
        }

        when (category) {
            ExpenseCategory.BREAKFAST -> b = updateVal(b, amount)
            ExpenseCategory.LUNCH -> l = updateVal(l, amount)
            ExpenseCategory.DINNER -> d = updateVal(d, amount)
            ExpenseCategory.OTHERS -> o = updateVal(o, amount)
        }

        val newExpense = DailyExpense(id = id, date = date, income = i, breakfast = b, lunch = l, dinner = d, others = o)
        saveExpenseToDb(context, newExpense)
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    // --- DEBT MANAGER LOGIC ---
    fun getDebts(context: Context): List<DebtItem> {
        var debts: List<DebtItem> = emptyList()
        val dao = AppDatabase.getDatabase(context).debtDao()
        Thread { debts = dao.getAllDebtsSync() }.apply { start(); join() }
        return debts
    }

    fun addDebt(context: Context, debt: DebtItem) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(context).debtDao().insertDebt(debt)
            if (CloudSyncManager.isUserLoggedIn()) CloudSyncManager.backupToCloud(context) { _, _ -> }
        }
    }

    fun updateDebt(context: Context, updatedDebt: DebtItem) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(context).debtDao().updateDebt(updatedDebt)
        }
    }

    fun deleteDebt(context: Context, debtId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            AppDatabase.getDatabase(context).debtDao().deleteDebtById(debtId)
        }
    }

    fun clearAllData(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            db.expenseDao().deleteAll()
            db.debtDao().deleteAll()
            db.notificationDao().clearAll()
        }
    }

    // --- NOTIFICATION MANAGER LOGIC ---
    fun getNotifications(context: Context): List<AppNotification> {
        var notifs: List<AppNotification> = emptyList()
        val dao = AppDatabase.getDatabase(context).notificationDao()
        Thread { notifs = dao.getAllNotificationsSync() }.apply { start(); join() }
        return notifs
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

    // --- FLOWS FOR UI (REAL-TIME UPDATES) ---
    fun getExpensesFlow(context: Context): Flow<List<DailyExpense>> {
        return AppDatabase.getDatabase(context).expenseDao().getAllExpenses()
    }

    fun getDebtsFlow(context: Context): Flow<List<DebtItem>> {
        return AppDatabase.getDatabase(context).debtDao().getAllDebts()
    }

    fun getNotificationsFlow(context: Context): Flow<List<AppNotification>> {
        return AppDatabase.getDatabase(context).notificationDao().getAllNotifications()
    }
}