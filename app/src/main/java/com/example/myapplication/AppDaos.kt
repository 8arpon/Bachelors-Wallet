package com.example.myapplication

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM daily_expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<DailyExpense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: DailyExpense)

    @Delete
    suspend fun deleteExpense(expense: DailyExpense)

    @Query("DELETE FROM daily_expenses")
    suspend fun deleteAll()

    @Query("SELECT * FROM daily_expenses ORDER BY date DESC")
    fun getAllExpensesSync(): List<DailyExpense>
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