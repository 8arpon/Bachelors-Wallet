package com.example.myapplication

import java.util.*

object ExpenseCalculator {

    // HIGHLIGHT: মাস চেক করার লজিক
    fun isThisMonth(date: Date): Boolean {
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance().apply { time = date }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH)
    }

    fun getTotalFood(expenses: List<DailyExpense>): Double = expenses.sumOf { it.breakfast + it.lunch + it.dinner }
    fun getTotalOthers(expenses: List<DailyExpense>): Double = expenses.sumOf { it.others }
    fun getTotalExpense(expenses: List<DailyExpense>): Double = getTotalFood(expenses) + getTotalOthers(expenses)
    fun getTotalIncome(expenses: List<DailyExpense>): Double = expenses.sumOf { it.income }

    // HIGHLIGHT: এই মাসের মোট আয় (Home Screen এর জন্য)
    fun getThisMonthIncome(expenses: List<DailyExpense>): Double {
        return expenses.filter { isThisMonth(it.date) }.sumOf { it.income }
    }

    // HIGHLIGHT: এই মাসের মোট ব্যয় (Home Screen এর জন্য)
    fun getThisMonthExpense(expenses: List<DailyExpense>): Double {
        return expenses.filter { isThisMonth(it.date) }.sumOf { it.totalExpense }
    }

    // HIGHLIGHT: ডাইনামিক মান্থলি ব্যালেন্স (গত মাসের ধার এই মাসে শোধ হলেই কেবল কাউন্ট হবে)
    fun getThisMonthBalance(context: android.content.Context, expenses: List<DailyExpense>, debts: List<DebtItem>): Double {
        val baseBalance = getThisMonthIncome(expenses) - getThisMonthExpense(expenses)

        // Read user preference
        val prefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        val includeDebt = prefs.getBoolean("pref_include_debt_in_balance", true)

        // If the user disabled integration, just return the base balance
        if (!includeDebt) {
            return baseBalance
        }

        var debtImpact = 0.0
        for (debt in debts) {
            // ১. যদি ধার "এই মাসে" নেওয়া বা দেওয়া হয়
            if (isThisMonth(debt.date)) {
                if (debt.type == DebtType.I_OWE) debtImpact += debt.amount       // আমি ধার নিলে টাকা আসে (+)
                else debtImpact -= debt.amount                                   // অন্যকে ধার দিলে টাকা যায় (-)
            }

            // ২. যদি আগের বা এই মাসের ধারের টাকা "এই মাসে" শোধ করা হয়
            for (payment in debt.paymentHistory) {
                if (isThisMonth(payment.date)) {
                    if (debt.type == DebtType.I_OWE) debtImpact -= payment.amount // আমি শোধ করলে টাকা যায় (-)
                    else debtImpact += payment.amount                             // কেউ আমাকে শোধ করলে টাকা আসে (+)
                }
            }
        }
        return baseBalance + debtImpact
    }

    fun filterExpenses(expenses: List<DailyExpense>, filterType: String): List<DailyExpense> {
        return expenses.filter {
            when (filterType) {
                "In" -> it.income > 0
                "Out" -> it.totalExpense > 0
                else -> true
            }
        }
    }
}