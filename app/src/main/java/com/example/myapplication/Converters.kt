package com.example.myapplication

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

class Converters {
    private val gson = Gson()

    // Date Converters
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    // DebtType Enum Converters
    @TypeConverter
    fun fromDebtType(value: String): DebtType {
        return DebtType.valueOf(value)
    }

    @TypeConverter
    fun debtTypeToString(debtType: DebtType): String {
        return debtType.name
    }

    // TransactionType Enum Converters
    @TypeConverter
    fun fromTransactionType(value: String): TransactionType {
        return TransactionType.valueOf(value)
    }

    @TypeConverter
    fun transactionTypeToString(type: TransactionType): String {
        return type.name
    }

    // PaymentRecord List Converters (For DebtItem)
    @TypeConverter
    fun fromPaymentRecordList(value: String?): MutableList<PaymentRecord> {
        if (value.isNullOrBlank()) return mutableListOf()
        return try {
            val listType = object : TypeToken<MutableList<PaymentRecord>>() {}.type
            val list: MutableList<PaymentRecord>? = gson.fromJson(value, listType)
            list?.map { it.copy(note = it.note ?: "") }?.toMutableList() ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    @TypeConverter
    fun paymentRecordListToString(list: MutableList<PaymentRecord>?): String {
        return gson.toJson(list ?: emptyList<PaymentRecord>())
    }
}