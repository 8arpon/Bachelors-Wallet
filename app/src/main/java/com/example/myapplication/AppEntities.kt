package com.example.myapplication

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(tableName = "daily_expenses")
data class DailyExpense(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: Date,
    val income: Double,
    val breakfast: Double,
    val lunch: Double,
    val dinner: Double,
    val others: Double
) {
    // HIGHLIGHT: Room কে বলে দিচ্ছি এটা ডাটাবেসে সেভ না করতে
    @get:Ignore
    val totalExpense: Double get() = breakfast + lunch + dinner + others
}

@Entity(tableName = "debt_items")
data class DebtItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    var amount: Double,
    var paidAmount: Double = 0.0,
    var isPaid: Boolean = false,
    val type: DebtType, // Ensure DebtType is properly defined in your project
    val date: Date,
    var deadline: Date? = null,
    var isArchived: Boolean = false,
    var archivedAmount: Double = 0.0,
    var archivedPaidAmount: Double = 0.0,
    var paymentHistory: MutableList<PaymentRecord> = mutableListOf()
) {
    // HIGHLIGHT: এগুলোতেও Ignore বসানো হলো
    @get:Ignore
    val displayAmount: Double get() = if (isArchived && amount == 0.0 && archivedAmount > 0.0) archivedAmount else amount
    @get:Ignore
    val displayPaidAmount: Double get() = if (isArchived && amount == 0.0 && archivedAmount > 0.0) archivedPaidAmount else paidAmount
    @get:Ignore
    val remainingAmount: Double get() = displayAmount - displayPaidAmount
}

@Entity(tableName = "app_notifications")
data class AppNotification(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val timestamp: Long,
    val type: String = "REMINDER",
    val isRead: Boolean = false
)