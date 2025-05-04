package com.timoautorecharge.assistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey val phoneNumber: String,
    val timoId: String,
    val lastTransactionDate: Long,
    val totalTransactions: Int = 0
)
