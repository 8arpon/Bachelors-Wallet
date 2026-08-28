package com.example.myapplication

import java.util.UUID

data class MessMember(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val phone: String = "",
    val roomNo: String = "",
    val role: String = "Member", // "Manager", "Member"
    val isPrimaryManager: Boolean = false
) {
    val displayName: String get() {
        val raw = if (name.isBlank()) "Roommate" else name
        if (raw.equals("Me", ignoreCase = true) || raw.equals("Me (Manager)", ignoreCase = true) || (isPrimaryManager && raw.equals("Manager", ignoreCase = true))) {
            val appUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName
            if (!appUser.isNullOrBlank()) return appUser
        }
        return raw
    }
    val displayRole: String get() = if (role.isBlank()) "Member" else role
}

data class DailyMealRecord(
    val id: String = UUID.randomUUID().toString(),
    val dateString: String = "", // "yyyy-MM-dd"
    val memberId: String = "",
    var breakfast: Double = 0.0,
    var lunch: Double = 0.0,
    var dinner: Double = 0.0
) {
    val totalDayMeals: Double get() = breakfast + lunch + dinner
}

data class MessBazaarRecord(
    val id: String = UUID.randomUUID().toString(),
    val buyerMemberIds: List<String> = emptyList(),
    val buyerMemberId: String = "",
    val buyerName: String = "",
    val amount: Double = 0.0,
    val items: String = "Bazaar / Groceries",
    val isPaidFromPersonalPocket: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun involvesMember(mId: String): Boolean {
        if (buyerMemberId == mId) return true
        val ids = buyerMemberIds ?: emptyList()
        return ids.contains(mId)
    }

    val displayBuyerName: String get() {
        val raw = if (buyerName.isNullOrBlank()) "Roommate" else buyerName
        val appUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName
        if (!appUser.isNullOrBlank()) {
            return raw.replace("Me (Manager)", appUser).replace("Me", appUser)
        }
        return raw
    }
    val displayItems: String get() = if (items.isNullOrBlank()) "Bazaar / Groceries" else items
}

data class MessDepositRecord(
    val id: String = UUID.randomUUID().toString(),
    val memberId: String = "",
    val memberName: String = "",
    val amount: Double = 0.0,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    val displayMemberName: String get() = if (memberName.isNullOrBlank()) "Roommate" else memberName
}

data class MessFixedExpense(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "", // "House Rent", "Cook Bill", "WiFi", "Electricity", "Gas"
    val amount: Double = 0.0
)

data class MemberSummary(
    val member: MessMember,
    val totalMeals: Double,
    val mealCost: Double,
    val fixedCostShare: Double,
    val totalCost: Double,
    val totalDeposits: Double,
    val totalBazaarSpent: Double,
    val personalBazaarPaid: Double = 0.0,
    val netBalance: Double // Positive = Refund (+), Negative = Due (-)
)
