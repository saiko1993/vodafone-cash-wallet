package com.timoautorecharge.assistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timoId: String,
    val senderNumber: String,
    val requestAmount: Double,
    val coinQuantity: Int,
    val requestTimestamp: Long,
    val paymentVerified: Boolean = false,
    val paymentTimestamp: Long? = null,
    val rechargeCompleted: Boolean = false,
    val rechargeTimestamp: Long? = null,
    val status: TransactionStatus,
    val notes: String? = null
)

enum class TransactionStatus {
    PENDING_PAYMENT,
    PAYMENT_VERIFIED,
    PAYMENT_TIMEOUT,
    RECHARGE_IN_PROGRESS,
    RECHARGE_COMPLETED,
    RECHARGE_FAILED,
    MANUAL_INTERVENTION_REQUIRED
}
