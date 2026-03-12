package com.example.myapplication

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

enum class SaveOp { OVERWRITE, ADD, SUB }

object DataManager {
    private const val PREFS_NAME = "wallet_database"
    private const val EXPENSES_KEY = "all_expenses"
    private const val DEBTS_KEY = "all_debts"

    fun getExpenses(context: Context): List<DailyExpense> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(EXPENSES_KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<DailyExpense>>() {}.type
        return Gson().fromJson(json, type)
    }

    private fun saveExpenses(context: Context, expenses: List<DailyExpense>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(expenses)
        prefs.edit().putString(EXPENSES_KEY, json).apply()
    }

    fun addIncome(context: Context, date: Date, amount: Double, op: SaveOp = SaveOp.OVERWRITE) {
        val expenses = getExpenses(context).toMutableList()
        val index = expenses.indexOfFirst { isSameDay(it.date, date) }

        if (index != -1) {
            val old = expenses[index]
            val newAmount = when (op) {
                SaveOp.OVERWRITE -> amount
                SaveOp.ADD -> old.income + amount
                SaveOp.SUB -> maxOf(0.0, old.income - amount)
            }
            expenses[index] = old.copy(income = newAmount)
        } else {
            val newAmount = if (op == SaveOp.SUB) 0.0 else amount
            expenses.add(DailyExpense(date = date, income = newAmount, breakfast = 0.0, lunch = 0.0, dinner = 0.0, others = 0.0))
        }
        saveExpenses(context, expenses)
    }

    fun addExpense(context: Context, date: Date, category: ExpenseCategory, amount: Double, op: SaveOp = SaveOp.OVERWRITE) {
        val expenses = getExpenses(context).toMutableList()
        val index = expenses.indexOfFirst { isSameDay(it.date, date) }

        var b = 0.0; var l = 0.0; var d = 0.0; var o = 0.0; var i = 0.0
        var id = UUID.randomUUID().toString()

        if (index != -1) {
            val old = expenses[index]
            b = old.breakfast; l = old.lunch; d = old.dinner; o = old.others; i = old.income; id = old.id
            expenses.removeAt(index)
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

        expenses.add(DailyExpense(id = id, date = date, income = i, breakfast = b, lunch = l, dinner = d, others = o))
        saveExpenses(context, expenses)
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    // --- DEBT MANAGER DATABASE LOGIC ---
    fun getDebts(context: Context): List<DebtItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(DEBTS_KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<DebtItem>>() {}.type
        return Gson().fromJson(json, type)
    }

    private fun saveDebts(context: Context, debts: List<DebtItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(debts)
        prefs.edit().putString(DEBTS_KEY, json).apply()
    }

    fun addDebt(context: Context, debt: DebtItem) {
        val debts = getDebts(context).toMutableList()
        debts.add(debt)
        saveDebts(context, debts)
    }

    fun updateDebt(context: Context, updatedDebt: DebtItem) {
        val debts = getDebts(context).toMutableList()
        val index = debts.indexOfFirst { it.id == updatedDebt.id }
        if (index != -1) {
            debts[index] = updatedDebt
            saveDebts(context, debts)
        }
    }

    // HIGHLIGHT: MISSING FUNCTION 1 (For perfectly deleting a debt)
    fun deleteDebt(context: Context, debtId: String) {
        val debts = getDebts(context).toMutableList()
        debts.removeAll { it.id == debtId }
        saveDebts(context, debts)
    }

    // HIGHLIGHT: MISSING FUNCTION 2 (For Danger Zone in Settings)
    fun clearAllData(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}