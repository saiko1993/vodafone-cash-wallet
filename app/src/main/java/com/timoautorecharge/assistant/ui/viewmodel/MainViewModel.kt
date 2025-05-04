package com.timoautorecharge.assistant.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.timoautorecharge.assistant.data.model.Log
import com.timoautorecharge.assistant.data.model.Transaction
import com.timoautorecharge.assistant.data.model.TransactionStatus
import com.timoautorecharge.assistant.data.repository.LogRepository
import com.timoautorecharge.assistant.data.repository.SettingsRepository
import com.timoautorecharge.assistant.data.repository.TransactionRepository
import com.timoautorecharge.assistant.service.TimoAccessibilityService
import com.timoautorecharge.assistant.service.WhatsAppService
import com.timoautorecharge.assistant.util.CoinCalculator
import com.timoautorecharge.assistant.util.NotificationService
import com.timoautorecharge.assistant.worker.RechargeWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

/**
 * ViewModel for the main application screens.
 * Manages transaction data, service status, and user interactions.
 */
class MainViewModel(
    private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val logRepository: LogRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationService: NotificationService,
    private val coinCalculator: CoinCalculator
) : ViewModel() {
    
    // Transaction data flows
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
    
    // Service status flows
    val whatsAppServiceStatus: StateFlow<WhatsAppService.ServiceStatus> = WhatsAppService.serviceStatus
        .stateIn(viewModelScope, SharingStarted.Eagerly, WhatsAppService.ServiceStatus.INACTIVE)
    
    val timoServiceStatus: StateFlow<TimoAccessibilityService.ServiceStatus> = TimoAccessibilityService.serviceStatus
        .stateIn(viewModelScope, SharingStarted.Eagerly, TimoAccessibilityService.ServiceStatus.INACTIVE)
    
    val smsServiceStatus: StateFlow<Boolean> = MutableStateFlow(true)
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    
    // UI state
    private val _showManualRechargeDialog = MutableStateFlow(false)
    val showManualRechargeDialog: StateFlow<Boolean> = _showManualRechargeDialog
    
    private val _selectedTransactionType = MutableStateFlow<TransactionStatus?>(null)
    val selectedTransactionType: StateFlow<TransactionStatus?> = _selectedTransactionType
    
    private val _navigateToTransactions = MutableStateFlow(false)
    val navigateToTransactions: StateFlow<Boolean> = _navigateToTransactions
    
    // Manual recharge form state
    val manualTimoId = mutableStateOf("")
    val manualAmount = mutableStateOf("")
    val manualSenderNumber = mutableStateOf("")
    
    /**
     * Refreshes the dashboard data by re-fetching transactions and logs.
     */
    fun refreshDashboard() {
        viewModelScope.launch {
            transactionRepository.refreshTransactions()
            logRepository.refreshLogs()
        }
    }
    
    /**
     * Shows pending payments in the transactions screen.
     */
    fun showPendingPayments() {
        _selectedTransactionType.value = TransactionStatus.PENDING_PAYMENT
        _navigateToTransactions.value = true
    }
    
    /**
     * Shows pending recharges in the transactions screen.
     */
    fun showPendingRecharges() {
        _selectedTransactionType.value = TransactionStatus.PAYMENT_VERIFIED
        _navigateToTransactions.value = true
    }
    
    /**
     * Navigates to the transactions screen.
     */
    fun navigateToTransactions() {
        _selectedTransactionType.value = null
        _navigateToTransactions.value = true
    }
    
    /**
     * Clears the navigation flag after navigation is complete.
     */
    fun onNavigationComplete() {
        _navigateToTransactions.value = false
    }
    
    /**
     * Manually initiates a recharge for a transaction.
     * 
     * @param transactionId The ID of the transaction to recharge
     */
    fun manualRecharge(transactionId: Long) {
        viewModelScope.launch {
            val transaction = transactionRepository.getTransactionById(transactionId)
            if (transaction != null && transaction.paymentVerified && !transaction.rechargeCompleted) {
                // Update transaction status
                transactionRepository.updateTransaction(
                    transaction.copy(
                        status = TransactionStatus.RECHARGE_IN_PROGRESS,
                        notes = transaction.notes + "\nManual recharge initiated at ${Date()}"
                    )
                )
                
                // Log the manual recharge
                logRepository.addLog(
                    Log(
                        message = "Manual recharge initiated for Timo ID ${transaction.timoId}",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.INFO,
                        source = "MainViewModel"
                    )
                )
                
                // Trigger recharge worker
                val rechargeWorkRequest = OneTimeWorkRequestBuilder<RechargeWorker>()
                    .setInputData(
                        workDataOf(
                            RechargeWorker.KEY_TRANSACTION_ID to transactionId
                        )
                    )
                    .build()
                
                WorkManager.getInstance(context).enqueue(rechargeWorkRequest)
                
                // Show notification
                notificationService.showRechargeStartedNotification(transaction)
            }
        }
    }
    
    /**
     * Manually verifies a payment for a transaction.
     * 
     * @param transactionId The ID of the transaction to verify
     */
    fun manualVerifyPayment(transactionId: Long) {
        viewModelScope.launch {
            val transaction = transactionRepository.getTransactionById(transactionId)
            if (transaction != null && !transaction.paymentVerified) {
                // Update transaction
                val updatedTransaction = transaction.copy(
                    paymentVerified = true,
                    paymentTimestamp = System.currentTimeMillis(),
                    status = TransactionStatus.PAYMENT_VERIFIED,
                    notes = transaction.notes + "\nPayment manually verified at ${Date()}"
                )
                
                transactionRepository.updateTransaction(updatedTransaction)
                
                // Log the manual verification
                logRepository.addLog(
                    Log(
                        message = "Payment manually verified for Timo ID ${transaction.timoId}",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.INFO,
                        source = "MainViewModel"
                    )
                )
                
                // Show notification
                notificationService.showPaymentVerifiedNotification(updatedTransaction)
            }
        }
    }
    
    /**
     * Cancels a transaction.
     * 
     * @param transactionId The ID of the transaction to cancel
     */
    fun cancelTransaction(transactionId: Long) {
        viewModelScope.launch {
            val transaction = transactionRepository.getTransactionById(transactionId)
            if (transaction != null && !transaction.rechargeCompleted) {
                // Update transaction
                val updatedTransaction = transaction.copy(
                    status = TransactionStatus.MANUAL_INTERVENTION_REQUIRED,
                    notes = transaction.notes + "\nTransaction cancelled at ${Date()}"
                )
                
                transactionRepository.updateTransaction(updatedTransaction)
                
                // Log the cancellation
                logRepository.addLog(
                    Log(
                        message = "Transaction cancelled for Timo ID ${transaction.timoId}",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.WARNING,
                        source = "MainViewModel"
                    )
                )
            }
        }
    }
    
    /**
     * Shows the manual recharge dialog.
     */
    fun showManualRechargeDialog() {
        _showManualRechargeDialog.value = true
    }
    
    /**
     * Dismisses the manual recharge dialog.
     */
    fun dismissManualRechargeDialog() {
        _showManualRechargeDialog.value = false
        manualTimoId.value = ""
        manualAmount.value = ""
        manualSenderNumber.value = ""
    }
    
    /**
     * Creates a manual transaction from user input.
     */
    fun createManualTransaction() {
        viewModelScope.launch {
            try {
                val timoId = manualTimoId.value
                val amount = manualAmount.value.toDoubleOrNull() ?: 0.0
                val senderNumber = manualSenderNumber.value
                
                if (timoId.isNotEmpty() && amount > 0 && senderNumber.isNotEmpty()) {
                    val coinQuantity = coinCalculator.calculateCoins(amount)
                    
                    // Create transaction
                    val transaction = Transaction(
                        timoId = timoId,
                        senderNumber = senderNumber,
                        requestAmount = amount,
                        coinQuantity = coinQuantity,
                        requestTimestamp = System.currentTimeMillis(),
                        paymentVerified = true,
                        paymentTimestamp = System.currentTimeMillis(),
                        status = TransactionStatus.PAYMENT_VERIFIED,
                        notes = "Manually created transaction at ${Date()}"
                    )
                    
                    val id = transactionRepository.addTransaction(transaction)
                    
                    // Log the manual transaction creation
                    logRepository.addLog(
                        Log(
                            message = "Manual transaction created for Timo ID $timoId",
                            timestamp = System.currentTimeMillis(),
                            level = Log.Level.INFO,
                            source = "MainViewModel"
                        )
                    )
                    
                    // Dismiss dialog
                    dismissManualRechargeDialog()
                    
                    // Trigger recharge
                    manualRecharge(id)
                }
            } catch (e: Exception) {
                logRepository.addLog(
                    Log(
                        message = "Error creating manual transaction: ${e.message}",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.ERROR,
                        source = "MainViewModel"
                    )
                )
            }
        }
    }
}
