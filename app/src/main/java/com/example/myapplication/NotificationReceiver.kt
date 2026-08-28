package com.example.myapplication

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val channelId = "smart_wallet_alerts"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Smart Alerts", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        }

        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isSmartReminder = prefs.getBoolean("pref_smart_reminder", true)
        val isBudgetAlert = prefs.getBoolean("pref_budget_alert", true)
        val isDebtAlert = prefs.getBoolean("pref_debt_alert", true)
        val isAutoDownload = prefs.getBoolean("pref_auto_download", false)

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tapIntent = Intent(context, MainActivity::class.java).apply {
                    putExtra("OPEN_TAB", "NOTIFICATIONS")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val pendingIntent = PendingIntent.getActivity(context, 100, tapIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

                val transactions = DataManager.getTransactionsSync(context)
                val debts = DataManager.getDebts(context)

                val todayCal = Calendar.getInstance()
                val todayTxs = transactions.filter {
                    val cal = Calendar.getInstance().apply { time = it.date }
                    cal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
                }
                val hasLoggedToday = todayTxs.isNotEmpty()

                // 1. Smart Reminder
                if (isSmartReminder && !hasLoggedToday) {
                    val addExpenseIntent = Intent(context, MainActivity::class.java).apply { putExtra("OPEN_TAB", "ADD_EXPENSE"); flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
                    val addExpensePendingIntent = PendingIntent.getActivity(context, 1, addExpenseIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    sendNotification(context, manager, channelId, 1001, "Wallet Reminder", "You haven't logged any expenses today!", "REMINDER", pendingIntent, addExpensePendingIntent, true)
                    delay(1000L)
                }

                // 2. Daily Detailed Report
                if (hasLoggedToday) {
                    val todayInc = todayTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                    val todayExp = todayTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

                    val foodCats = listOf("Breakfast", "Lunch", "Dinner", "Food", "Snacks", "Groceries")
                    val todayFood = todayTxs.filter { it.type == TransactionType.EXPENSE && it.category in foodCats }.sumOf { it.amount }
                    val todayOthers = todayExp - todayFood

                    val fullReport = "Income: ৳${todayInc.toInt()}\nFood & Groceries: ৳${todayFood.toInt()}\nOthers: ৳${todayOthers.toInt()}\nTotal Spent: ৳${todayExp.toInt()}"

                    sendNotification(context, manager, channelId, 1004, "Daily Summary Report", fullReport, "REPORT", pendingIntent, null, true)

                    if (isAutoDownload) {
                        exportAestheticPDF(context, fullReport, System.currentTimeMillis())
                    }
                    delay(1000L)
                }

                // 3. Budget Alert
                if (isBudgetAlert) {
                    val curMonth = todayCal.get(Calendar.MONTH)
                    val curYear = todayCal.get(Calendar.YEAR)
                    val monthTxs = transactions.filter {
                        val c = Calendar.getInstance().apply { time = it.date }
                        c.get(Calendar.MONTH) == curMonth && c.get(Calendar.YEAR) == curYear
                    }
                    val mInc = monthTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                    val mExp = monthTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

                    if (mInc > 0 && mExp >= (mInc * 0.8)) {
                        sendNotification(context, manager, channelId, 1002, "Budget Alert! ⚠️", "You have spent over 80% of your monthly income.", "ALERT", pendingIntent, null, true)
                        delay(1000L)
                    }
                }

                // 4. Debt Reminder
                if (isDebtAlert) {
                    val currentTime = System.currentTimeMillis()
                    val oldDebts = debts.filter { !it.isPaid && !it.isArchived && ((currentTime - it.date.time) / 86400000L) >= 15L }
                    oldDebts.forEachIndexed { index, debt ->
                        val daysOverdue = ((currentTime - debt.date.time) / 86400000L).toInt()
                        val typeStr = if (debt.type == DebtType.I_OWE) "You owe" else "Owes you"
                        sendNotification(context, manager, channelId, 1003 + index, "Pending Debt: ${debt.name}", "$typeStr ৳${debt.remainingAmount.toInt()} for $daysOverdue days!", "DEBT", pendingIntent, null, true)
                        delay(1000L)
                    }
                }

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val nextIntent = Intent(context, NotificationReceiver::class.java)
                val nextPendingIntent = PendingIntent.getBroadcast(context, 0, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

                val targetCal = Calendar.getInstance().apply {
                    val notifTimeStr = prefs.getString("notif_time", "9:00 PM") ?: "9:00 PM"
                    var hour = 21
                    var min = 0
                    try {
                        val parts = notifTimeStr.split(":", " ")
                        if (parts.size >= 3) {
                            val h = parts[0].toInt()
                            val m = parts[1].toInt()
                            val amPm = parts[2].uppercase()
                            hour = if (amPm == "PM" && h < 12) h + 12 else if (amPm == "AM" && h == 12) 0 else h
                            min = m
                        }
                    } catch (e: Exception) { }
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, min)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (before(Calendar.getInstance())) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }

                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetCal.timeInMillis, nextPendingIntent)
                } catch (e: SecurityException) {
                    try {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, targetCal.timeInMillis, nextPendingIntent)
                    } catch (ex: Exception) { }
                } catch (e: Exception) { }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun sendNotification(context: Context, manager: NotificationManager, channelId: String, notifId: Int, title: String, message: String, type: String, pendingIntent: PendingIntent, actionIntent: PendingIntent?, saveToDb: Boolean) {
        if (saveToDb) {
            val appNotif = AppNotification(title = title, message = message, timestamp = System.currentTimeMillis(), type = type, isRead = false)
            DataManager.saveNotification(context, appNotif)
            val updateIntent = Intent("ACTION_UPDATE_RED_DOT")
            updateIntent.setPackage(context.packageName)
            context.sendBroadcast(updateIntent)
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (actionIntent != null) builder.addAction(android.R.drawable.ic_input_add, "Add Transaction", actionIntent)
        manager.notify(notifId, builder.build())
    }
}