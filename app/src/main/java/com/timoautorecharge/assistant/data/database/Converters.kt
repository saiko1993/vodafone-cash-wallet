package com.timoautorecharge.assistant.data.database

import androidx.room.TypeConverter
import com.timoautorecharge.assistant.data.model.LogType
import com.timoautorecharge.assistant.data.model.TransactionStatus

class Converters {
    @TypeConverter
    fun fromTransactionStatus(status: TransactionStatus): String {
        return status.name
    }
    
    @TypeConverter
    fun toTransactionStatus(status: String): TransactionStatus {
        return TransactionStatus.valueOf(status)
    }
    
    @TypeConverter
    fun fromLogType(type: LogType): String {
        return type.name
    }
    
    @TypeConverter
    fun toLogType(type: String): LogType {
        return LogType.valueOf(type)
    }
    
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return value.joinToString(",")
    }
    
    @TypeConverter
    fun toStringList(value: String): List<String> {
        return value.split(",").map { it.trim() }
    }
}
