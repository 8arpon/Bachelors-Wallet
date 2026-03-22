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
    val auth = FirebaseAuth.getInstance()
    private val gson = Gson()

    private val db = FirebaseFirestore.getInstance().apply {
        firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(false)
            .build()
    }

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
        val expenses = DataManager.getExpenses(context)
        val debts = DataManager.getDebts(context)

        val backupData = hashMapOf("expenses_json" to gson.toJson(expenses), "debts_json" to gson.toJson(debts), "last_backup" to System.currentTimeMillis())

        db.collection("users").document(user.uid).set(backupData, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener { onComplete(true, "Cloud Backup Successful! ☁️") }
            .addOnFailureListener { e -> onComplete(false, e.localizedMessage ?: "Backup Failed") }
    }

    // --- 2. RESTORE (DATA LOGIC) ---
    fun restoreFromCloud(context: Context, onComplete: (Boolean, String) -> Unit) {
        val user = auth.currentUser ?: return onComplete(false, "Please login to restore data.")

        db.collection("users").document(user.uid).get(Source.SERVER).addOnSuccessListener { document ->
            if (document != null && document.exists()) {
                try {
                    val expensesJson = document.getString("expenses_json") ?: "[]"
                    val debtsJson = document.getString("debts_json") ?: "[]"
                    val expType = object : TypeToken<List<DailyExpense>>() {}.type
                    val debtType = object : TypeToken<List<DebtItem>>() {}.type

                    val cloudExpenses: List<DailyExpense> = gson.fromJson(expensesJson, expType) ?: emptyList()
                    val cloudDebts: List<DebtItem> = gson.fromJson(debtsJson, debtType) ?: emptyList()

                    // HIGHLIGHT: Room Database e notun kore insert kora hocche
                    CoroutineScope(Dispatchers.IO).launch {
                        val database = AppDatabase.getDatabase(context)
                        val expDao = database.expenseDao()
                        val debtDao = database.debtDao()

                        // Purono data clear kore cloud er data boshabo
                        expDao.deleteAll()
                        debtDao.deleteAll()

                        cloudExpenses.forEach { expDao.insertExpense(it) }
                        cloudDebts.forEach { debtDao.insertDebt(it) }

                        withContext(Dispatchers.Main) {
                            onComplete(true, "Data Synced Successfully! 🔄")
                        }
                    }
                } catch (e: Exception) { onComplete(false, "Error parsing cloud data.") }
            } else { onComplete(true, "No previous backup found. Clean slate! ✨") }
        }.addOnFailureListener { e -> onComplete(false, "Sync Failed: ${e.localizedMessage}") }
    }

    fun isUserLoggedIn(): Boolean = auth.currentUser != null
}