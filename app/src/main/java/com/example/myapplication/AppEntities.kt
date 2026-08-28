package com.example.myapplication

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(
    tableName = "transaction_entries",
    indices = [
        Index("date"),
        Index("type"),
        Index("category")
    ]
)
data class TransactionEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: Date,
    val type: TransactionType, // INCOME or EXPENSE
    val category: String, // "Breakfast", "Salary", "Transport", etc.
    val amount: Double
)

enum class TransactionType {
    INCOME, EXPENSE
}

@Entity(tableName = "custom_categories")
data class CustomCategory(
    @PrimaryKey val name: String,
    val addedOn: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "debt_items",
    indices = [
        Index("isPaid"),
        Index("isArchived"),
        Index("date")
    ]
)
data class DebtItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    var amount: Double,
    var paidAmount: Double = 0.0,
    var isPaid: Boolean = false,
    val type: DebtType,
    val date: Date,
    var deadline: Date? = null,
    var isArchived: Boolean = false,
    var archivedAmount: Double = 0.0,
    var archivedPaidAmount: Double = 0.0,
    var paymentHistory: MutableList<PaymentRecord> = mutableListOf(),
    var isLinkedWithBalance: Boolean = true,
    var note: String = ""
) {
    @get:Ignore
    val safeNote: String get() = note ?: ""
    @get:Ignore
    val displayAmount: Double get() = if (isArchived && amount == 0.0 && archivedAmount > 0.0) archivedAmount else amount
    @get:Ignore
    val displayPaidAmount: Double get() = if (isArchived && amount == 0.0 && archivedAmount > 0.0) archivedPaidAmount else paidAmount
    @get:Ignore
    val remainingAmount: Double get() = displayAmount - displayPaidAmount
}

@Entity(
    tableName = "app_notifications",
    indices = [
        Index("timestamp"),
        Index("isRead")
    ]
)
data class AppNotification(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val timestamp: Long,
    val type: String = "REMINDER",
    val isRead: Boolean = false
)

@Entity(
    tableName = "scheduled_transactions",
    indices = [
        Index("isActive"),
        Index("nextExecutionDate")
    ]
)
data class ScheduledTransaction(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val amount: Double,
    val category: String,
    val type: TransactionType,
    val frequency: String, // "Daily", "Weekly", "Monthly"
    val nextExecutionDate: Date,
    val isActive: Boolean = true
)