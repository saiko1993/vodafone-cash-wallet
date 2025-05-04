package com.timoautorecharge.assistant.data.database

import android.graphics.Rect
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.timoautorecharge.assistant.data.model.LogType
import com.timoautorecharge.assistant.data.model.TransactionStatus
import com.timoautorecharge.assistant.data.model.UiElementInfo

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
    
    @TypeConverter
    fun fromUiElementInfo(elementInfo: UiElementInfo): String {
        val gson = Gson()
        return gson.toJson(elementInfo)
    }
    
    @TypeConverter
    fun toUiElementInfo(elementInfoString: String): UiElementInfo {
        val gson = Gson()
        return gson.fromJson(elementInfoString, UiElementInfo::class.java)
    }
    
    @TypeConverter
    fun fromRect(rect: Rect): String {
        return "${rect.left},${rect.top},${rect.right},${rect.bottom}"
    }
    
    @TypeConverter
    fun toRect(rectString: String): Rect {
        val parts = rectString.split(",")
        if (parts.size == 4) {
            return Rect(
                parts[0].toInt(),
                parts[1].toInt(),
                parts[2].toInt(),
                parts[3].toInt()
            )
        }
        return Rect()
    }
}
