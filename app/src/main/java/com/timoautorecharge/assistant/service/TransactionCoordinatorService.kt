package com.timoautorecharge.assistant.service

import android.content.Context
import com.timoautorecharge.assistant.data.model.Log
import com.timoautorecharge.assistant.data.model.Transaction
import com.timoautorecharge.assistant.data.model.TransactionStatus
import com.timoautorecharge.assistant.data.repository.LogRepository
import com.timoautorecharge.assistant.data.repository.SettingsRepository
import com.timoautorecharge.assistant.data.repository.TransactionRepository
import com.timoautorecharge.assistant.util.CoinCalculator
import com.timoautorecharge.assistant.util.NotificationService
import com.timoautorecharge.assistant.util.WorkManagerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Central service that coordinates the flow between WhatsApp, SMS, and Timo modules.
 * Manages the complete transaction lifecycle from request to recharge.
 * Optimized for battery efficiency and performance.
 */
class TransactionCoordinatorService(
    private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val logRepository: LogRepository,
    private val settingsRepository: SettingsRepository,
    private val whatsAppService: WhatsAppService,
    private val smsService: SmsService,
    private val notificationService: NotificationService,
    private val coinCalculator: CoinCalculator
) {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val workManagerHelper = WorkManagerHelper(context)
    
    /**
     * Processes a new recharge request from WhatsApp.
     * 
     * @param timoId The Timo ID to recharge
     * @param amount The amount in EGP
     * @param senderNumber The sender's phone number
     * @return The ID of the created transaction
     */
    suspend fun processRechargeRequest(timoId: String, amount: Double, senderNumber: String): Long {
        return withContext(Dispatchers.IO) {
            // Calculate coins based on amount
            val coinQuantity = coinCalculator.calculateCoins(amount)
            
            // Create transaction
            val transaction = Transaction(
                timoId = timoId,
                senderNumber = senderNumber,
                requestAmount = amount,
                coinQuantity = coinQuantity,
                requestTimestamp = System.currentTimeMillis(),
                status = TransactionStatus.PAYMENT_PENDING
            )
            
            val transactionId = transactionRepository.addTransaction(transaction)
            
            // Log the request
            logRepository.addLog(
                Log(
                    message = "New recharge request: Timo ID $timoId, Amount $amount EGP, Coins $coinQuantity",
                    timestamp = System.currentTimeMillis(),
                    level = Log.Level.INFO,
                    source = "TransactionCoordinatorService"
                )
            )
            
            // Send payment instructions via WhatsApp
            val settings = settingsRepository.getSettings().first()
            whatsAppService.sendMessage(
                senderNumber,
                "Please send $amount EGP to Vodafone Cash wallet ${settings.vodafoneCashNumber}"
            )
            
            // Schedule payment timeout check using WorkManager for battery efficiency
            schedulePaymentTimeoutCheck(transactionId, settings.paymentTimeoutMinutes)
            
            transactionId
        }
    }
    
    /**
     * Processes a payment verification from SMS.
     * 
     * @param senderNumber The sender's phone number
     * @param amount The amount in EGP
     * @param timestamp The timestamp of the payment
     * @return True if a matching transaction was found and verified
     */
    suspend fun processPaymentVerification(senderNumber: String, amount: Double, timestamp: Long): Boolean {
        return withContext(Dispatchers.IO) {
            // Find matching transaction - optimized query using indexed fields
            val transaction = findMatchingTransaction(senderNumber, amount, timestamp)
            
            if (transaction != null) {
                // Update transaction
                val updatedTransaction = transaction.copy(
                    paymentVerified = true,
                    paymentTimestamp = timestamp,
                    status = TransactionStatus.PAYMENT_VERIFIED
                )
                
                transactionRepository.updateTransaction(updatedTransaction)
                
                // Log the verification
                logRepository.addLog(
                    Log(
                        message = "Payment verified: Timo ID ${transaction.timoId}, Amount $amount EGP",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.INFO,
                        source = "TransactionCoordinatorService"
                    )
                )
                
                // Send confirmation via WhatsApp
                whatsAppService.sendMessage(
                    senderNumber,
                    "Your payment of $amount EGP has been confirmed. Processing your recharge now."
                )
                
                // Show notification
                notificationService.showPaymentVerifiedNotification(updatedTransaction)
                
                // Schedule recharge with battery-efficient constraints
                scheduleRecharge(transaction.id)
                
                true
            } else {
                false
            }
        }
    }
    
    /**
     * Processes a recharge completion.
     * 
     * @param transactionId The ID of the transaction
     * @param success Whether the recharge was successful
     * @param errorMessage Optional error message if the recharge failed
     */
    suspend fun processRechargeCompletion(transactionId: Long, success: Boolean, errorMessage: String? = null) {
        withContext(Dispatchers.IO) {
            val transaction = transactionRepository.getTransactionById(transactionId) ?: return@withContext
            
            // Update transaction
            val updatedTransaction = if (success) {
                transaction.copy(
                    rechargeCompleted = true,
                    rechargeTimestamp = System.currentTimeMillis(),
                    status = TransactionStatus.RECHARGE_COMPLETED
                )
            } else {
                val notes = if (transaction.notes.isNullOrEmpty()) {
                    "Recharge failed: $errorMessage"
                } else {
                    "${transaction.notes}\nRecharge failed: $errorMessage"
                }
                
                transaction.copy(
                    status = TransactionStatus.RECHARGE_FAILED,
                    notes = notes
                )
            }
            
            transactionRepository.updateTransaction(updatedTransaction)
            
            // Log the completion
            val logLevel = if (success) Log.Level.INFO else Log.Level.ERROR
            logRepository.addLog(
                Log(
                    message = if (success) 
                        "Recharge completed: Timo ID ${transaction.timoId}, Coins ${transaction.coinQuantity}" 
                    else 
                        "Recharge failed: Timo ID ${transaction.timoId}, Error: $errorMessage",
                    timestamp = System.currentTimeMillis(),
                    level = logLevel,
                    source = "TransactionCoordinatorService"
                )
            )
            
            // Send notification to customer
            if (success) {
                whatsAppService.sendMessage(
                    transaction.senderNumber,
                    "Your recharge of ${transaction.coinQuantity} coins to Timo ID ${transaction.timoId} has been completed successfully."
                )
                notificationService.showRechargeSuccessNotification(updatedTransaction)
            } else {
                whatsAppService.sendMessage(
                    transaction.senderNumber,
                    "Your recharge to Timo ID ${transaction.timoId} has failed. Please contact support."
                )
                notificationService.showRechargeFailedNotification(updatedTransaction, errorMessage ?: "Unknown error")
                
                // If the error is retryable, schedule a retry
                if (errorMessage?.contains("retryable", ignoreCase = true) == true) {
                    scheduleRetryRecharge(transactionId)
                }
            }
        }
    }
    
    /**
     * Finds a matching transaction for payment verification.
     * Optimized for performance using indexed fields.
     * 
     * @param senderNumber The sender's phone number
     * @param amount The amount in EGP
     * @param timestamp The timestamp of the payment
     * @return The matching transaction or null if not found
     */
    private suspend fun findMatchingTransaction(senderNumber: String, amount: Double, timestamp: Long): Transaction? {
        val settings = settingsRepository.getSettings().first()
        
        // Get only pending payment transactions within the timeout window
        // This is more efficient than getting all pending transactions
        val cutoffTime = timestamp - (settings.paymentTimeoutMinutes * 60 * 1000L)
        val pendingTransactions = transactionRepository.getPendingPaymentTransactions(cutoffTime)
        
        // Check for exact match first
        val exactMatch = pendingTransactions.find { 
            it.senderNumber == senderNumber && 
            abs(it.requestAmount - amount) < 0.01
        }
        
        if (exactMatch != null) {
            return exactMatch
        }
        
        // Check for partial match (last 5 digits of phone number)
        val senderLast5Digits = senderNumber.takeLast(5)
        val last5DigitsMatch = pendingTransactions.find {
            val last5Digits = it.senderNumber.takeLast(5)
            last5Digits == senderLast5Digits &&
            abs(it.requestAmount - amount) < 0.01
        }
        
        return last5DigitsMatch
    }
    
    /**
     * Schedules a payment timeout check using WorkManager for battery efficiency.
     * 
     * @param transactionId The ID of the transaction
     * @param timeoutMinutes The timeout in minutes
     */
    private fun schedulePaymentTimeoutCheck(transactionId: Long, timeoutMinutes: Int) {
        // Use a coroutine for the initial scheduling to avoid blocking
        serviceScope.launch {
            // Create a worker to check for payment timeout
            val workRequest = workManagerHelper.scheduleDelayedRechargeWork(
                transactionId = transactionId,
                delayMinutes = timeoutMinutes.toLong(),
                attemptCount = 1
            )
            
            // Log the scheduled timeout check
            logRepository.addLog(
                Log(
                    message = "Scheduled payment timeout check for transaction $transactionId in $timeoutMinutes minutes",
                    timestamp = System.currentTimeMillis(),
                    level = Log.Level.DEBUG,
                    source = "TransactionCoordinatorService"
                )
            )
        }
    }
    
    /**
     * Schedules a recharge using WorkManager with battery-efficient constraints.
     * 
     * @param transactionId The ID of the transaction
     */
    private fun scheduleRecharge(transactionId: Long) {
        // Use WorkManagerHelper to schedule the recharge with battery-efficient constraints
        val workId = workManagerHelper.scheduleRechargeWork(transactionId)
        
        // Log the scheduled recharge
        serviceScope.launch {
            logRepository.addLog(
                Log(
                    message = "Scheduled recharge for transaction $transactionId with work ID $workId",
                    timestamp = System.currentTimeMillis(),
                    level = Log.Level.INFO,
                    source = "TransactionCoordinatorService"
                )
            )
        }
    }
    
    /**
     * Schedules a retry for a failed recharge.
     * 
     * @param transactionId The ID of the transaction
     */
    private fun scheduleRetryRecharge(transactionId: Long) {
        serviceScope.launch {
            // Get the transaction to check retry count
            val transaction = transactionRepository.getTransactionById(transactionId) ?: return@launch
            
            // Extract retry count from notes if available
            val retryCount = extractRetryCount(transaction.notes)
            
            if (retryCount < 3) { // Limit to 3 retries
                // Update transaction with retry count
                val updatedNotes = updateRetryCount(transaction.notes, retryCount + 1)
                transactionRepository.updateTransaction(
                    transaction.copy(notes = updatedNotes)
                )
                
                // Schedule retry with exponential backoff
                val delayMinutes = (5 * (1 shl retryCount)).toLong() // 5, 10, 20 minutes
                workManagerHelper.scheduleDelayedRechargeWork(
                    transactionId = transactionId,
                    delayMinutes = delayMinutes,
                    attemptCount = retryCount + 1
                )
                
                // Log the retry
                logRepository.addLog(
                    Log(
                        message = "Scheduled retry #${retryCount + 1} for transaction $transactionId in $delayMinutes minutes",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.INFO,
                        source = "TransactionCoordinatorService"
                    )
                )
            } else {
                // Max retries reached, update status to require manual intervention
                transactionRepository.updateTransaction(
                    transaction.copy(
                        status = TransactionStatus.MANUAL_INTERVENTION_REQUIRED,
                        notes = transaction.notes + "\nMax retries reached, manual intervention required."
                    )
                )
                
                // Log the max retries
                logRepository.addLog(
                    Log(
                        message = "Max retries reached for transaction $transactionId, manual intervention required",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.WARNING,
                        source = "TransactionCoordinatorService"
                    )
                )
                
                // Notify admin
                notificationService.showManualInterventionRequiredNotification(transaction)
            }
        }
    }
    
    /**
     * Extracts the retry count from transaction notes.
     * 
     * @param notes The transaction notes
     * @return The retry count, or 0 if not found
     */
    private fun extractRetryCount(notes: String?): Int {
        if (notes.isNullOrEmpty()) return 0
        
        val retryPattern = "Retry count: (\\d+)".toRegex()
        val matchResult = retryPattern.find(notes)
        
        return matchResult?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }
    
    /**
     * Updates the retry count in transaction notes.
     * 
     * @param notes The transaction notes
     * @param count The new retry count
     * @return The updated notes
     */
    private fun updateRetryCount(notes: String?, count: Int): String {
        if (notes.isNullOrEmpty()) {
            return "Retry count: $count"
        }
        
        val retryPattern = "Retry count: (\\d+)".toRegex()
        return if (retryPattern.containsMatchIn(notes)) {
            notes.replace(retryPattern, "Retry count: $count")
        } else {
            "$notes\nRetry count: $count"
        }
    }
    
    /**
     * Calculates the coins for a given amount.
     * Wrapper method for the CoinCalculator.
     * 
     * @param amount The amount in EGP
     * @return The calculated coin quantity
     */
    fun calculateCoins(amount: Double): Int {
        return coinCalculator.calculateCoins(amount)
    }
}
