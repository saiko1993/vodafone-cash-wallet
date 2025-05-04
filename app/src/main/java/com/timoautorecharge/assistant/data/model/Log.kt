package com.timoautorecharge.assistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "logs")
data class Log(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: LogType,
    val message: String,
    val transactionId: Long? = null
)

enum class LogType {
    INFO,
    WARNING,
    ERROR,
    WHATSAPP,
    SMS,
    RECHARGE
}
