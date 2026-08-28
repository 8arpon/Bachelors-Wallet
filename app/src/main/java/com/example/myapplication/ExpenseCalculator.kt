package com.example.myapplication

import android.content.Context
import java.util.*

object ExpenseCalculator {

    fun isThisMonth(date: Date): Boolean {
        val cal1 = Calendar.getInstance()
        val cal2 = Calendar.getInstance().apply { time = date }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH)
    }

    fun isSameMonth(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH)
    }

    // --- TRANSACTION CALCULATIONS ---
    fun getTotalIncome(transactions: List<TransactionEntry>): Double {
        return transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    }

    fun getTotalExpense(transactions: List<TransactionEntry>): Double {
        return transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    }

    fun getThisMonthIncome(transactions: List<TransactionEntry>): Double {
        return transactions.filter { isThisMonth(it.date) && it.type == TransactionType.INCOME }.sumOf { it.amount }
    }

    fun getThisMonthExpense(transactions: List<TransactionEntry>): Double {
        return transactions.filter { isThisMonth(it.date) && it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    }

    /**
     * Total Cumulative Wallet Balance:
     * Reflects the actual real-time funds in the user's wallet with full past-month rollover.
     * All past and current incomes, expenses, debts (borrowed/lent), and repayments are accounted for.
     */
    fun getTotalWalletBalance(context: Context, transactions: List<TransactionEntry>, debts: List<DebtItem>): Double {
        val baseBalance = getTotalIncome(transactions) - getTotalExpense(transactions)
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val includeDebt = prefs.getBoolean("pref_include_debt_in_balance", true)

        if (!includeDebt) return baseBalance

        val debtImpact = debts.filter { !it.isArchived && it.isLinkedWithBalance }.sumOf { debt ->
            if (debt.type == DebtType.I_OWE) debt.remainingAmount
            else -debt.remainingAmount
        }
        return baseBalance + debtImpact
    }

    /**
     * Current Available Wallet Balance (synonymous with getTotalWalletBalance to ensure past month funds roll over).
     */
    fun getThisMonthBalance(context: Context, transactions: List<TransactionEntry>, debts: List<DebtItem>): Double {
        return getTotalWalletBalance(context, transactions, debts)
    }

    /**
     * Calculates the exact debt cashflow that occurred strictly in THIS month:
     * - New loans created in this month (I_OWE: +amount, THEY_OWE: -amount)
     * - Repayments made in this month (I_OWE: -paid, THEY_OWE: +paid)
     */
    fun getThisMonthDebtFlow(context: Context, debts: List<DebtItem>): Double {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val includeDebt = prefs.getBoolean("pref_include_debt_in_balance", true)
        if (!includeDebt) return 0.0

        var flow = 0.0
        val linkedDebts = debts.filter { !it.isArchived && it.isLinkedWithBalance }

        for (debt in linkedDebts) {
            // Did the debt originate this month?
            if (isThisMonth(debt.date)) {
                if (debt.type == DebtType.I_OWE) flow += debt.displayAmount
                else flow -= debt.displayAmount
            }
            // Repayments that took place this month
            for (payment in debt.paymentHistory) {
                if (isThisMonth(payment.date)) {
                    if (debt.type == DebtType.I_OWE) flow -= payment.amount
                    else flow += payment.amount
                }
            }
        }
        return flow
    }

    fun filterTransactions(transactions: List<TransactionEntry>, filterType: String): List<TransactionEntry> {
        return transactions.filter {
            when (filterType) {
                "In" -> it.type == TransactionType.INCOME
                "Out" -> it.type == TransactionType.EXPENSE
                else -> true
            }
        }
    }
}