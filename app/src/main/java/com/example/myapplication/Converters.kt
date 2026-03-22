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

    // PaymentRecord List Converters (For DebtItem)
    @TypeConverter
    fun fromPaymentRecordList(value: String): MutableList<PaymentRecord> {
        val listType = object : TypeToken<MutableList<PaymentRecord>>() {}.type
        return gson.fromJson(value, listType) ?: mutableListOf()
    }

    @TypeConverter
    fun paymentRecordListToString(list: MutableList<PaymentRecord>): String {
        return gson.toJson(list)
    }
}