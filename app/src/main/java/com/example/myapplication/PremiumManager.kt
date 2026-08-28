package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PremiumManager {
    private const val PREFS_NAME = "premium_prefs"
    private const val KEY_IS_PRO = "is_pro_member"
    private const val KEY_PRO_PLAN = "pro_plan_name"
    private const val KEY_PRO_UNLOCKED_AT = "pro_unlocked_at"
    private const val KEY_PRO_EXPIRY = "pro_expiry_timestamp"
    private const val KEY_PRO_COUPON = "pro_applied_coupon"

    // Default Fallback Codes (Universal codes are loaded from Firestore collection 'promo_codes')
    val DEFAULT_PROMO_CODES = mapOf(
        "PROFREE" to "PRO Lifetime (VIP Gift)",
        "BACHELOR2026" to "PRO Lifetime (Bachelor Special)"
    )

    // Reactive Compose State for instantaneous UI updates
    var isProUser = mutableStateOf(false)
        private set

    var currentPlanTitle = mutableStateOf("Free Plan")
        private set

    var proExpiryText = mutableStateOf("Free User")
        private set

    private var firestoreListener: ListenerRegistration? = null

    fun initialize(context: Context) {
        val prefs = getPrefs(context)
        val isPro = prefs.getBoolean(KEY_IS_PRO, false)
        val expiry = prefs.getLong(KEY_PRO_EXPIRY, -1L)

        // Check if expired (if expiry is set)
        if (isPro && expiry > 0 && System.currentTimeMillis() > expiry) {
            revokePro(context)
        } else {
            isProUser.value = isPro
            currentPlanTitle.value = prefs.getString(KEY_PRO_PLAN, if (isPro) "PRO Member" else "Free Plan") ?: "Free Plan"
            updateExpiryText(context)
        }

        // Attach Realtime Firestore Snapshot Listener if user is logged in
        syncWithFirestoreIfLoggedIn(context)
    }

    fun isPremium(context: Context): Boolean {
        val prefs = getPrefs(context)
        val isPro = prefs.getBoolean(KEY_IS_PRO, false)
        val expiry = prefs.getLong(KEY_PRO_EXPIRY, -1L)
        if (isPro && expiry > 0 && System.currentTimeMillis() > expiry) {
            revokePro(context)
            return false
        }
        return isPro
    }

    /**
     * Redeem Promo Code:
     * - Checks Firestore collection 'promo_codes' or DEFAULT_PROMO_CODES
     * - Binds PRO permanently to the logged in Gmail account (Firebase Auth)
     */
    fun redeemCoupon(
        context: Context,
        rawCode: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val code = rawCode.trim().uppercase()
        if (code.isEmpty()) {
            onComplete(false, "Please enter a promo code.")
            return
        }

        val authUser = FirebaseAuth.getInstance().currentUser
        if (authUser == null) {
            onComplete(false, "⚠️ Please login with your Google/Gmail account first so your PRO License is permanently attached to your email!")
            return
        }

        // 1. Check local default codes first
        val defaultPlan = DEFAULT_PROMO_CODES[code]
        if (defaultPlan != null) {
            activateProForUser(
                context = context,
                user = authUser,
                plan = defaultPlan,
                coupon = code,
                expiryTimestamp = -1L
            )
            onComplete(true, "🎉 Awesome! '$code' unlocked $defaultPlan and is now bound to ${authUser.email}!")
            return
        }

        // 2. Check universal Firestore collection 'promo_codes' created by Web Admin Dashboard
        try {
            val db = FirebaseFirestore.getInstance()
            db.collection("promo_codes").document(code).get()
                .addOnSuccessListener { doc ->
                    if (doc != null && doc.exists()) {
                        val planName = doc.getString("plan") ?: "PRO Lifetime VIP"
                        val durationDaysDoc = doc.getLong("durationDays") ?: doc.getLong("duration") ?: 0L
                        val explicitExpiry = doc.getLong("expiry") ?: -1L

                        val calculatedExpiry: Long = when {
                            explicitExpiry > System.currentTimeMillis() -> explicitExpiry
                            durationDaysDoc > 0L -> System.currentTimeMillis() + (durationDaysDoc * 24L * 60L * 60L * 1000L)
                            planName.contains("1-Year", ignoreCase = true) || planName.contains("1 Year", ignoreCase = true) || planName.contains("Annual", ignoreCase = true) || planName.contains("Yearly", ignoreCase = true) -> {
                                System.currentTimeMillis() + (365L * 24L * 60L * 60L * 1000L)
                            }
                            planName.contains("6-Month", ignoreCase = true) || planName.contains("6 Month", ignoreCase = true) || planName.contains("Half-Year", ignoreCase = true) -> {
                                System.currentTimeMillis() + (180L * 24L * 60L * 60L * 1000L)
                            }
                            planName.contains("3-Month", ignoreCase = true) || planName.contains("3 Month", ignoreCase = true) || planName.contains("Quarter", ignoreCase = true) -> {
                                System.currentTimeMillis() + (90L * 24L * 60L * 60L * 1000L)
                            }
                            planName.contains("1-Month", ignoreCase = true) || planName.contains("Monthly", ignoreCase = true) || planName.contains("1 Month", ignoreCase = true) || planName.contains("30-Day", ignoreCase = true) -> {
                                System.currentTimeMillis() + (30L * 24L * 60L * 60L * 1000L)
                            }
                            planName.contains("7-Day", ignoreCase = true) || planName.contains("Weekly", ignoreCase = true) || planName.contains("1 Week", ignoreCase = true) -> {
                                System.currentTimeMillis() + (7L * 24L * 60L * 60L * 1000L)
                            }
                            else -> -1L
                        }

                        activateProForUser(
                            context = context,
                            user = authUser,
                            plan = planName,
                            coupon = code,
                            expiryTimestamp = calculatedExpiry
                        )

                        // Increment usage count in Firestore
                        val currentCount = doc.getLong("usageCount") ?: 0
                        db.collection("promo_codes").document(code)
                            .set(mapOf("usageCount" to (currentCount + 1)), SetOptions.merge())

                        onComplete(true, "🎉 Promo code '$code' activated $planName successfully for ${authUser.email}!")
                    } else {
                        onComplete(false, "Invalid promo code. Please check and try again.")
                    }
                }
                .addOnFailureListener { e ->
                    onComplete(false, "Network error: ${e.localizedMessage}")
                }
        } catch (e: Exception) {
            onComplete(false, "Error verifying promo code. Please try again.")
        }
    }

    /**
     * Activates PRO and binds it to user account if logged in
     */
    fun activatePro(
        context: Context,
        plan: String = "PRO Lifetime VIP",
        coupon: String? = null,
        expiryTimestamp: Long = -1L
    ) {
        val authUser = FirebaseAuth.getInstance().currentUser
        if (authUser != null) {
            activateProForUser(context, authUser, plan, coupon, expiryTimestamp)
        } else {
            val prefs = getPrefs(context)
            prefs.edit()
                .putBoolean(KEY_IS_PRO, true)
                .putString(KEY_PRO_PLAN, plan)
                .putString(KEY_PRO_COUPON, coupon ?: "")
                .putLong(KEY_PRO_UNLOCKED_AT, System.currentTimeMillis())
                .putLong(KEY_PRO_EXPIRY, expiryTimestamp)
                .apply()

            isProUser.value = true
            currentPlanTitle.value = plan
            updateExpiryText(context)
        }
    }

    /**
     * Activates PRO and binds it permanently to the Gmail account
     */
    fun activateProForUser(
        context: Context,
        user: com.google.firebase.auth.FirebaseUser,
        plan: String,
        coupon: String? = null,
        expiryTimestamp: Long = -1L
    ) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putBoolean(KEY_IS_PRO, true)
            .putString(KEY_PRO_PLAN, plan)
            .putString(KEY_PRO_COUPON, coupon ?: "")
            .putLong(KEY_PRO_UNLOCKED_AT, System.currentTimeMillis())
            .putLong(KEY_PRO_EXPIRY, expiryTimestamp)
            .apply()

        isProUser.value = true
        currentPlanTitle.value = plan
        updateExpiryText(context)

        // Bind directly to Firestore document users/{uid}
        try {
            val db = FirebaseFirestore.getInstance()
            val proData = hashMapOf(
                "is_premium" to true,
                "pro_plan" to plan,
                "pro_coupon" to (coupon ?: ""),
                "email" to (user.email ?: ""),
                "pro_unlocked_at" to System.currentTimeMillis(),
                "pro_expiry" to expiryTimestamp
            )
            db.collection("users").document(user.uid).set(proData, SetOptions.merge())
        } catch (e: Exception) {
            // Offline fallback
        }
    }

    fun revokePro(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putBoolean(KEY_IS_PRO, false)
            .putString(KEY_PRO_PLAN, "Free Plan")
            .remove(KEY_PRO_EXPIRY)
            .remove(KEY_PRO_COUPON)
            .apply()

        isProUser.value = false
        currentPlanTitle.value = "Free Plan"
        proExpiryText.value = "Free User"

        // 1. Reset theme if current theme is a PRO-exclusive theme
        val appPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val currentThemeId = appPrefs.getString("theme_mode", "System") ?: "System"
        val activeTheme = ThemeState.ALL_THEMES.find { it.id == currentThemeId }
        if (activeTheme?.isProOnly == true) {
            val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            ThemeState.applyTheme(context, "System", isSystemDark)
        }

        // 2. Disable Auto Cloud Backup
        appPrefs.edit().putBoolean("pref_auto_cloud_backup", false).apply()
    }

    /**
     * Realtime Snapshot Listener on Firestore users/{uid}:
     * Instant Push Notifications when Super Admin Grants or Revokes PRO from Web Dashboard!
     */
    fun syncWithFirestoreIfLoggedIn(context: Context) {
        try {
            val user = FirebaseAuth.getInstance().currentUser ?: return
            val db = FirebaseFirestore.getInstance()

            // Remove existing listener if any
            firestoreListener?.remove()

            // Real-time Live Listener
            firestoreListener = db.collection("users").document(user.uid)
                .addSnapshotListener { doc, error ->
                    if (error != null || doc == null || !doc.exists()) return@addSnapshotListener

                    val isCloudPro = doc.getBoolean("is_premium") ?: false
                    val plan = doc.getString("pro_plan") ?: "PRO Lifetime VIP"
                    val coupon = doc.getString("pro_coupon")
                    var expiry = doc.getLong("pro_expiry") ?: -1L

                    if (isCloudPro) {
                        // Auto-correct any timed plan that was previously saved with -1L (Lifetime)
                        if (expiry <= 0L) {
                            val unlockedAt = doc.getLong("pro_unlocked_at") ?: System.currentTimeMillis()
                            val fixedExpiry: Long? = when {
                                plan.contains("1-Year", ignoreCase = true) || plan.contains("1 Year", ignoreCase = true) || plan.contains("Annual", ignoreCase = true) || plan.contains("Yearly", ignoreCase = true) -> {
                                    unlockedAt + (365L * 24L * 60L * 60L * 1000L)
                                }
                                plan.contains("6-Month", ignoreCase = true) || plan.contains("6 Month", ignoreCase = true) || plan.contains("Half-Year", ignoreCase = true) -> {
                                    unlockedAt + (180L * 24L * 60L * 60L * 1000L)
                                }
                                plan.contains("3-Month", ignoreCase = true) || plan.contains("3 Month", ignoreCase = true) || plan.contains("Quarter", ignoreCase = true) -> {
                                    unlockedAt + (90L * 24L * 60L * 60L * 1000L)
                                }
                                plan.contains("1-Month", ignoreCase = true) || plan.contains("Monthly", ignoreCase = true) || plan.contains("1 Month", ignoreCase = true) || plan.contains("30-Day", ignoreCase = true) -> {
                                    unlockedAt + (30L * 24L * 60L * 60L * 1000L)
                                }
                                plan.contains("7-Day", ignoreCase = true) || plan.contains("Weekly", ignoreCase = true) || plan.contains("1 Week", ignoreCase = true) -> {
                                    unlockedAt + (7L * 24L * 60L * 60L * 1000L)
                                }
                                else -> null
                            }

                            if (fixedExpiry != null) {
                                expiry = fixedExpiry
                                try {
                                    db.collection("users").document(user.uid).set(mapOf("pro_expiry" to expiry), SetOptions.merge())
                                } catch (e: Exception) {}
                            }
                        }

                        // Check if expired
                        if (expiry > 0 && System.currentTimeMillis() > expiry) {
                            if (isProUser.value) {
                                revokePro(context)
                                Toast.makeText(context, "⚠️ Your PRO Subscription has expired.", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            val wasNotPro = !isProUser.value
                            val prefs = getPrefs(context)
                            prefs.edit()
                                .putBoolean(KEY_IS_PRO, true)
                                .putString(KEY_PRO_PLAN, plan)
                                .putString(KEY_PRO_COUPON, coupon ?: "")
                                .putLong(KEY_PRO_EXPIRY, expiry)
                                .apply()

                            isProUser.value = true
                            currentPlanTitle.value = plan
                            updateExpiryText(context)

                            if (wasNotPro) {
                                Toast.makeText(context, "🎉 Awesome! PRO VIP status is now ACTIVE on your account!", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        // Admin Revoked PRO in Web Dashboard
                        if (isProUser.value) {
                            revokePro(context)
                            Toast.makeText(context, "⚠️ Your PRO Membership status was updated by Administrator.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    fun updateExpiryText(context: Context) {
        val prefs = getPrefs(context)
        if (!isProUser.value) {
            proExpiryText.value = "Free User"
            return
        }

        val plan = prefs.getString(KEY_PRO_PLAN, currentPlanTitle.value) ?: currentPlanTitle.value
        var expiry = prefs.getLong(KEY_PRO_EXPIRY, -1L)

        // 🛡️ CRITICAL FIX: If plan is a timed plan (e.g. 1-Year) but expiry was saved as <= 0 (Lifetime),
        // recalculate and heal the expiry timestamp right now!
        if (expiry <= 0L) {
            val unlockedAt = prefs.getLong(KEY_PRO_UNLOCKED_AT, System.currentTimeMillis())
            val fixedExpiry: Long? = when {
                plan.contains("1-Year", ignoreCase = true) || plan.contains("1 Year", ignoreCase = true) || plan.contains("Annual", ignoreCase = true) || plan.contains("Yearly", ignoreCase = true) -> {
                    unlockedAt + (365L * 24L * 60L * 60L * 1000L)
                }
                plan.contains("6-Month", ignoreCase = true) || plan.contains("6 Month", ignoreCase = true) || plan.contains("Half-Year", ignoreCase = true) -> {
                    unlockedAt + (180L * 24L * 60L * 60L * 1000L)
                }
                plan.contains("3-Month", ignoreCase = true) || plan.contains("3 Month", ignoreCase = true) || plan.contains("Quarter", ignoreCase = true) -> {
                    unlockedAt + (90L * 24L * 60L * 60L * 1000L)
                }
                plan.contains("1-Month", ignoreCase = true) || plan.contains("Monthly", ignoreCase = true) || plan.contains("1 Month", ignoreCase = true) || plan.contains("30-Day", ignoreCase = true) -> {
                    unlockedAt + (30L * 24L * 60L * 60L * 1000L)
                }
                plan.contains("7-Day", ignoreCase = true) || plan.contains("Weekly", ignoreCase = true) || plan.contains("1 Week", ignoreCase = true) -> {
                    unlockedAt + (7L * 24L * 60L * 60L * 1000L)
                }
                else -> null
            }

            if (fixedExpiry != null) {
                expiry = fixedExpiry
                prefs.edit().putLong(KEY_PRO_EXPIRY, expiry).apply()
            }
        }

        if (expiry <= 0) {
            proExpiryText.value = "Lifetime VIP 👑 (No Expiry)"
        } else {
            val remainingMillis = expiry - System.currentTimeMillis()
            if (remainingMillis <= 0) {
                proExpiryText.value = "Expired"
            } else {
                val days = maxOf(1, (remainingMillis / (1000 * 60 * 60 * 24)).toInt())
                val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(expiry))
                proExpiryText.value = "Valid till: $dateStr ($days days remaining)"
            }
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
