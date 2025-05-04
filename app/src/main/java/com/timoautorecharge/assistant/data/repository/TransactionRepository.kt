package com.timoautorecharge.assistant.data.repository

import com.timoautorecharge.assistant.data.database.TransactionDao
import com.timoautorecharge.assistant.data.model.Transaction
import com.timoautorecharge.assistant.data.model.TransactionStatus
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {
    
    fun getAllTransactions(): Flow<List<Transaction>> {
        return transactionDao.getAllTransactions()
    }
    
    fun getTransactionsByStatus(status: TransactionStatus): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByStatus(status)
    }
    
    suspend fun getTransactionById(id: Long): Transaction? {
        return transactionDao.getTransactionById(id)
    }
    
    suspend fun createTransaction(transaction: Transaction): Long {
        return transactionDao.insertTransaction(transaction)
    }
    
    suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.updateTransaction(transaction)
    }
    
    suspend fun getPendingPaymentTransactions(cutoffTime: Long): List<Transaction> {
        return transactionDao.getPendingPaymentTransactions(cutoffTime)
    }
    
    suspend fun getPendingRechargeTransactions(): List<Transaction> {
        return transactionDao.getPendingRechargeTransactions()
    }
    
    fun getRecentTransactions(limit: Int = 10): Flow<List<Transaction>> {
        return transactionDao.getRecentTransactions(limit)
    }
}
