package com.example.myapplication

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transaction_entries ORDER BY date DESC")
    fun getAllTransactions(): kotlinx.coroutines.flow.Flow<List<TransactionEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntry)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntry)

    @Query("DELETE FROM transaction_entries WHERE id = :id")
    suspend fun deleteTransactionById(id: String)

    @Query("DELETE FROM transaction_entries")
    suspend fun deleteAll()

    @Query("SELECT * FROM transaction_entries ORDER BY date DESC")
    fun getAllTransactionsSync(): List<TransactionEntry>
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debt_items ORDER BY date DESC")
    fun getAllDebts(): Flow<List<DebtItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: DebtItem)

    @Update
    suspend fun updateDebt(debt: DebtItem)

    @Query("DELETE FROM debt_items WHERE id = :debtId")
    suspend fun deleteDebtById(debtId: String)

    @Query("DELETE FROM debt_items")
    suspend fun deleteAll()

    @Query("SELECT * FROM debt_items ORDER BY date DESC")
    fun getAllDebtsSync(): List<DebtItem>
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM app_notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<AppNotification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: AppNotification)

    @Query("UPDATE app_notifications SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM app_notifications WHERE id = :notificationId")
    suspend fun deleteNotificationById(notificationId: String)

    @Query("DELETE FROM app_notifications WHERE type != 'DEBT'")
    suspend fun clearAllExceptDebts()

    @Query("DELETE FROM app_notifications")
    suspend fun clearAll()

    @Query("SELECT * FROM app_notifications ORDER BY timestamp DESC")
    fun getAllNotificationsSync(): List<AppNotification>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM custom_categories ORDER BY addedOn ASC")
    fun getAllCategories(): Flow<List<CustomCategory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CustomCategory)

    @Query("DELETE FROM custom_categories WHERE name = :categoryName")
    suspend fun deleteCategory(categoryName: String)

    @Query("DELETE FROM custom_categories")
    suspend fun deleteAll()

    @Query("SELECT * FROM custom_categories")
    fun getAllCategoriesSync(): List<CustomCategory>
}

@Dao
interface ScheduledTransactionDao {
    @Query("SELECT * FROM scheduled_transactions ORDER BY nextExecutionDate ASC")
    fun getAllScheduled(): Flow<List<ScheduledTransaction>>

    @Query("SELECT * FROM scheduled_transactions WHERE isActive = 1")
    fun getActiveScheduledSync(): List<ScheduledTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertScheduled(item: ScheduledTransaction)

    @Update
    fun updateScheduled(item: ScheduledTransaction)

    @Delete
    fun deleteScheduled(item: ScheduledTransaction)

    @Query("DELETE FROM scheduled_transactions")
    fun deleteAll()
}