package com.timoautorecharge.assistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timoautorecharge.assistant.data.model.Log
import com.timoautorecharge.assistant.data.model.Transaction
import com.timoautorecharge.assistant.data.model.TransactionStatus
import com.timoautorecharge.assistant.data.repository.LogRepository
import com.timoautorecharge.assistant.data.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val transactionRepository: TransactionRepository,
    private val logRepository: LogRepository,
    private val settingsRepository: com.timoautorecharge.assistant.data.repository.SettingsRepository
) : ViewModel() {
    
    val pendingPayments: StateFlow<List<Transaction>> = transactionRepository
        .getTransactionsByStatus(TransactionStatus.PENDING_PAYMENT)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val pendingRecharges: StateFlow<List<Transaction>> = transactionRepository
        .getTransactionsByStatus(TransactionStatus.PAYMENT_VERIFIED)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val recentTransactions: StateFlow<List<Transaction>> = transactionRepository
        .getRecentTransactions(10)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val recentLogs: StateFlow<List<Log>> = logRepository
        .getRecentLogs(20)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    val transactionCounts: StateFlow<Map<TransactionStatus, Int>> = transactionRepository
        .getAllTransactions()
        .map { transactions ->
            transactions.groupBy { it.status }.mapValues { it.value.size }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())
    
    fun manualRecharge(transactionId: Long) {
        viewModelScope.launch {
            val transaction = transactionRepository.getTransactionById(transactionId)
            if (transaction != null && transaction.paymentVerified && !transaction.rechargeCompleted) {
                // Trigger recharge worker
                // This would normally be done through WorkManager, but for manual recharge
                // we can directly call the recharge executor
            }
        }
    }
}
