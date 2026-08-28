package com.example.myapplication

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TransactionEntry::class, DebtItem::class, AppNotification::class, CustomCategory::class, ScheduledTransaction::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao // HIGHLIGHT: নতুন DAO
    abstract fun debtDao(): DebtDao
    abstract fun notificationDao(): NotificationDao
    abstract fun categoryDao(): CategoryDao
    abstract fun scheduledTransactionDao(): ScheduledTransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "budget_wallet_database"
                )
                    .fallbackToDestructiveMigration() // যেহেতু ডাটাবেস আর্কিটেকচার পুরো পাল্টে গেছে, পুরনো ডাটা মুছে যাবে
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}