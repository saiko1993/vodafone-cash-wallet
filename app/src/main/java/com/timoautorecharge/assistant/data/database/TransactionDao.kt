package com.timoautorecharge.assistant.data.database

import androidx.room.*
import com.timoautorecharge.assistant.data.model.Transaction
import com.timoautorecharge.assistant.data.model.TransactionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY requestTimestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>
    
    @Query("SELECT * FROM transactions WHERE status = :status ORDER BY requestTimestamp DESC")
    fun getTransactionsByStatus(status: TransactionStatus): Flow<List<Transaction>>
    
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): Transaction?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long
    
    @Update
    suspend fun updateTransaction(transaction: Transaction)
    
    @Query("SELECT * FROM transactions WHERE paymentVerified = 0 AND requestTimestamp > :cutoffTime")
    suspend fun getPendingPaymentTransactions(cutoffTime: Long): List<Transaction>
    
    @Query("SELECT * FROM transactions WHERE paymentVerified = 1 AND rechargeCompleted = 0")
    suspend fun getPendingRechargeTransactions(): List<Transaction>
    
    @Query("SELECT * FROM transactions ORDER BY requestTimestamp DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int): Flow<List<Transaction>>
}
