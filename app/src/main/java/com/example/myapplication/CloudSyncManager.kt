package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Source
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object CloudSyncManager {
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val gson = Gson()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    fun saveOrUpdateUserProfile(context: Context, name: String?, photoUri: Uri?, isRemovingPhoto: Boolean, onComplete: (Boolean, String) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onComplete(false, "User not logged in.")
            return
        }

        val userRef = db.collection("users").document(user.uid)

        userRef.get().addOnSuccessListener { document ->
            val updates = hashMapOf<String, Any>()
            updates["email"] = user.email ?: ""
            updates["displayName"] = if (!name.isNullOrBlank()) name else (user.displayName ?: "User")

            if (isRemovingPhoto) {
                updates["photoUrl"] = ""
            } else if (photoUri != null && photoUri.toString().startsWith("content://")) {
                val base64Image = encodeImageToBase64(context, photoUri)
                if (base64Image != null) updates["photoUrl"] = base64Image
                else updates["photoUrl"] = document?.getString("photoUrl") ?: (user.photoUrl.toString() ?: "")
            } else {
                updates["photoUrl"] = document?.getString("photoUrl") ?: (user.photoUrl.toString() ?: "")
            }

            userRef.set(updates, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener { onComplete(true, "Profile updated successfully! ✅") }
                .addOnFailureListener { e -> onComplete(false, e.localizedMessage ?: "Failed to save profile.") }
        }
    }

    private fun encodeImageToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)

            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 200, 200, true)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val byteArray = outputStream.toByteArray()

            "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }

    fun getUserProfile(onComplete: (Map<String, String>?, String) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onComplete(null, "Not logged in")
            return
        }

        db.collection("users").document(user.uid).get(Source.SERVER)
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val name = document.getString("displayName") ?: (user.displayName ?: "User")
                    val photoUrl = document.getString("photoUrl") ?: (user.photoUrl.toString() ?: "")
                    val email = document.getString("email") ?: (user.email ?: "")

                    onComplete(mapOf("name" to name, "photoUrl" to photoUrl, "email" to email), "Success")
                } else {
                    onComplete(null, "No profile found")
                }
            }
            .addOnFailureListener { e -> onComplete(null, e.localizedMessage ?: "Fetch failed") }
    }

    // --- 1. BACKUP (DATA LOGIC) ---
    fun backupToCloud(context: Context, onComplete: (Boolean, String) -> Unit) {
        val user = auth.currentUser ?: return onComplete(false, "Please login to backup data.")

        CoroutineScope(Dispatchers.IO).launch {
            val database = AppDatabase.getDatabase(context)
            val transactions = database.transactionDao().getAllTransactionsSync()
            val debts = database.debtDao().getAllDebtsSync()
            val categories = database.categoryDao().getAllCategoriesSync()

            val appPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val budgetPrefs = context.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)
            val messPrefs = context.getSharedPreferences("mess_manager_prefs", Context.MODE_PRIVATE)

            // Budget & Preferences
            val categoryMetaJson = appPrefs.getString("category_meta", "{}")
            val categoryLimitsJson = budgetPrefs.getString("category_limits", "{}")
            val curatedBudgetListJson = budgetPrefs.getString("curated_budget_list", "[]")
            val savingsGoal = budgetPrefs.getFloat("monthly_savings_goal", 0f)

            // Settings & App Configuration
            val appTheme = appPrefs.getString("theme_mode", appPrefs.getString("app_theme", "System")) ?: "System"
            val isDarkTheme = appPrefs.getBoolean("dark_mode", appPrefs.getBoolean("is_dark_theme", false))
            val customAccentColor = appPrefs.getLong("pref_custom_accent_color", 0L)
            val includeDebtInBalance = appPrefs.getBoolean("pref_include_debt_in_balance", true)
            val autoDownload = appPrefs.getBoolean("pref_auto_download", false)
            val showCategoryEmojis = appPrefs.getBoolean("pref_show_category_emojis", true)
            val disabledCategoriesJson = gson.toJson(appPrefs.getStringSet("disabled_categories_set", emptySet()))
            val userMonthlyIncome = appPrefs.getFloat("user_monthly_income", 0f)

            val notifEnabled = appPrefs.getBoolean("notif_enabled", true)
            val notifTime = appPrefs.getString("notif_time", "09:00 PM") ?: "09:00 PM"
            val aiEmail = appPrefs.getBoolean("pref_ai_email", true)
            val aiPush = appPrefs.getBoolean("pref_ai_push", true)
            val autoCloudBackup = appPrefs.getBoolean("pref_auto_cloud_backup", true)

            val isMessCentric = appPrefs.getBoolean("pref_mess_centric_mode", false)
            val linkMessToWallet = appPrefs.getBoolean("pref_link_mess_to_wallet", true)
            val isFoodCentric = appPrefs.getBoolean("pref_food_centric", true)
            val showMessInNavBar = appPrefs.getBoolean("pref_show_mess_in_navbar", true)

            // Mess Manager Complete Records
            val messMembersJson = messPrefs.getString("mess_members", "[]")
            val messArchivedJson = messPrefs.getString("mess_archived_members", "[]")
            val messMealsJson = messPrefs.getString("mess_daily_meals", "[]")
            val messBazaarJson = messPrefs.getString("mess_bazaar_records", "[]")
            val messDepositsJson = messPrefs.getString("mess_deposits", "[]")
            val messFixedJson = messPrefs.getString("mess_fixed_costs", "[]")

            val backupData = hashMapOf(
                "transactions_json" to gson.toJson(transactions),
                "debts_json" to gson.toJson(debts),
                "categories_json" to gson.toJson(categories),
                "disabled_categories_json" to disabledCategoriesJson,
                "category_meta_json" to categoryMetaJson,
                "category_limits_json" to categoryLimitsJson,
                "curated_budget_list_json" to curatedBudgetListJson,
                "monthly_savings_goal" to savingsGoal,
                "user_monthly_income" to userMonthlyIncome,
                "app_theme" to appTheme,
                "is_dark_theme" to isDarkTheme,
                "pref_custom_accent_color" to customAccentColor,
                "pref_include_debt_in_balance" to includeDebtInBalance,
                "pref_auto_download" to autoDownload,
                "pref_show_category_emojis" to showCategoryEmojis,
                "pref_mess_centric_mode" to isMessCentric,
                "pref_link_mess_to_wallet" to linkMessToWallet,
                "pref_food_centric" to isFoodCentric,
                "pref_show_mess_in_navbar" to showMessInNavBar,
                "notif_enabled" to notifEnabled,
                "notif_time" to notifTime,
                "pref_ai_email" to aiEmail,
                "pref_ai_push" to aiPush,
                "pref_auto_cloud_backup" to autoCloudBackup,
                "mess_members_json" to messMembersJson,
                "mess_archived_members_json" to messArchivedJson,
                "mess_daily_meals_json" to messMealsJson,
                "mess_bazaar_records_json" to messBazaarJson,
                "mess_deposits_json" to messDepositsJson,
                "mess_fixed_costs_json" to messFixedJson,
                "last_backup" to System.currentTimeMillis()
            )

            withContext(Dispatchers.Main) {
                db.collection("users").document(user.uid).set(backupData, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener { onComplete(true, "Cloud Backup Successful! ☁️") }
                    .addOnFailureListener { e -> onComplete(false, e.localizedMessage ?: "Backup Failed") }
            }
        }
    }

    // --- 2. RESTORE (DATA LOGIC) ---
    fun restoreFromCloud(context: Context, onComplete: (Boolean, String) -> Unit) {
        val user = auth.currentUser ?: return onComplete(false, "Please login to restore data.")

        db.collection("users").document(user.uid).get(Source.SERVER).addOnSuccessListener { document ->
            if (document != null && document.exists()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val transactionsJson = document.getString("transactions_json") ?: "[]"
                        val debtsJson = document.getString("debts_json") ?: "[]"
                        val categoriesJson = document.getString("categories_json") ?: "[]"

                        val categoryMetaJson = document.getString("category_meta_json") ?: "{}"
                        val categoryLimitsJson = document.getString("category_limits_json") ?: "{}"
                        val curatedBudgetListJson = document.getString("curated_budget_list_json") ?: "[]"
                        val savingsGoal = (document.getDouble("monthly_savings_goal") ?: 0.0).toFloat()

                        val isMessCentric = document.getBoolean("pref_mess_centric_mode") ?: false
                        val linkMessToWallet = document.getBoolean("pref_link_mess_to_wallet") ?: true
                        val isFoodCentric = document.getBoolean("pref_food_centric") ?: true
                        val showMessInNavBar = document.getBoolean("pref_show_mess_in_navbar") ?: true

                        // Mess Manager Data from Cloud
                        val messMembersJson = document.getString("mess_members_json") ?: "[]"
                        val messArchivedJson = document.getString("mess_archived_members_json") ?: "[]"
                        val messMealsJson = document.getString("mess_daily_meals_json") ?: "[]"
                        val messBazaarJson = document.getString("mess_bazaar_records_json") ?: "[]"
                        val messDepositsJson = document.getString("mess_deposits_json") ?: "[]"
                        val messFixedJson = document.getString("mess_fixed_costs_json") ?: "[]"

                        val appTheme = document.getString("app_theme") ?: "System"
                        val isDarkTheme = document.getBoolean("is_dark_theme") ?: false
                        val customAccentColor = document.getLong("pref_custom_accent_color") ?: 0L
                        val includeDebtInBalance = document.getBoolean("pref_include_debt_in_balance") ?: true
                        val autoDownload = document.getBoolean("pref_auto_download") ?: false
                        val showCategoryEmojis = document.getBoolean("pref_show_category_emojis") ?: true
                        val disabledCategoriesJson = document.getString("disabled_categories_json") ?: "[]"
                        val userMonthlyIncome = (document.getDouble("user_monthly_income") ?: 0.0).toFloat()
                        val notifEnabled = document.getBoolean("notif_enabled") ?: true
                        val notifTime = document.getString("notif_time") ?: "09:00 PM"
                        val aiEmail = document.getBoolean("pref_ai_email") ?: true
                        val aiPush = document.getBoolean("pref_ai_push") ?: true
                        val autoCloudBackup = document.getBoolean("pref_auto_cloud_backup") ?: true

                        val stringSetType = object : TypeToken<Set<String>>() {}.type
                        val disabledSet: Set<String> = try {
                            gson.fromJson(disabledCategoriesJson, stringSetType) ?: emptySet()
                        } catch (e: Exception) { emptySet() }

                        val appPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                        appPrefs.edit()
                            .putString("category_meta", categoryMetaJson)
                            .putString("theme_mode", appTheme)
                            .putString("app_theme", appTheme)
                            .putBoolean("dark_mode", isDarkTheme)
                            .putBoolean("is_dark_theme", isDarkTheme)
                            .putLong("pref_custom_accent_color", customAccentColor)
                            .putBoolean("pref_include_debt_in_balance", includeDebtInBalance)
                            .putBoolean("pref_auto_download", autoDownload)
                            .putBoolean("pref_show_category_emojis", showCategoryEmojis)
                            .putStringSet("disabled_categories_set", disabledSet)
                            .putFloat("user_monthly_income", userMonthlyIncome)
                            .putBoolean("notif_enabled", notifEnabled)
                            .putString("notif_time", notifTime)
                            .putBoolean("pref_ai_email", aiEmail)
                            .putBoolean("pref_ai_push", aiPush)
                            .putBoolean("pref_auto_cloud_backup", autoCloudBackup)
                            .putBoolean("pref_mess_centric_mode", isMessCentric)
                            .putBoolean("pref_link_mess_to_wallet", linkMessToWallet)
                            .putBoolean("pref_food_centric", isFoodCentric)
                            .putBoolean("pref_show_mess_in_navbar", showMessInNavBar)
                            .apply()

                        val budgetPrefs = context.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)
                        budgetPrefs.edit()
                            .putString("category_limits", categoryLimitsJson)
                            .putString("curated_budget_list", curatedBudgetListJson)
                            .putFloat("monthly_savings_goal", savingsGoal)
                            .apply()

                        // Restore Mess Manager
                        withContext(Dispatchers.Main) {
                            ThemeState.applyTheme(context, appTheme, isDarkTheme)
                            MessManager.loadFromCloud(
                                context = context,
                                membersJson = messMembersJson,
                                archivedJson = messArchivedJson,
                                mealsJson = messMealsJson,
                                bazaarJson = messBazaarJson,
                                depositsJson = messDepositsJson,
                                fixedJson = messFixedJson
                            )
                        }

                        val transType = object : TypeToken<List<TransactionEntry>>() {}.type
                        val debtType = object : TypeToken<List<DebtItem>>() {}.type
                        val catType = object : TypeToken<List<CustomCategory>>() {}.type

                        val cloudTransactions: List<TransactionEntry> = gson.fromJson(transactionsJson, transType) ?: emptyList()
                        val cloudDebts: List<DebtItem> = gson.fromJson(debtsJson, debtType) ?: emptyList()
                        val cloudCategories: List<CustomCategory> = gson.fromJson(categoriesJson, catType) ?: emptyList()

                        val database = AppDatabase.getDatabase(context)
                        val transDao = database.transactionDao()
                        val debtDao = database.debtDao()
                        val catDao = database.categoryDao()

                        transDao.deleteAll()
                        debtDao.deleteAll()
                        catDao.deleteAll()

                        cloudTransactions.forEach { transDao.insertTransaction(it) }
                        cloudDebts.forEach { debtDao.insertDebt(it) }
                        cloudCategories.forEach { catDao.insertCategory(it) }

                        withContext(Dispatchers.Main) {
                            onComplete(true, "All Settings, Categories & Data Synced with Gmail! 🔄")
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            onComplete(false, "Error parsing cloud data: ${e.localizedMessage}")
                        }
                    }
                }
            } else { onComplete(true, "No previous backup found. Clean slate! ✨") }
        }.addOnFailureListener { e -> onComplete(false, "Sync Failed: ${e.localizedMessage}") }
    }

    fun isUserLoggedIn(): Boolean = auth.currentUser != null
}