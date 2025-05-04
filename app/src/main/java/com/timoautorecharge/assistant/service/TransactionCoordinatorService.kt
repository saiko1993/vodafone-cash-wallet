package com.timoautorecharge.assistant.service

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.timoautorecharge.assistant.data.model.Log
import com.timoautorecharge.assistant.data.model.Transaction
import com.timoautorecharge.assistant.data.model.TransactionStatus
import com.timoautorecharge.assistant.data.repository.LogRepository
import com.timoautorecharge.assistant.data.repository.SettingsRepository
import com.timoautorecharge.assistant.data.repository.TransactionRepository
import com.timoautorecharge.assistant.util.CoinCalculator
import com.timoautorecharge.assistant.util.NotificationService
import com.timoautorecharge.assistant.worker.RechargeWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Central service that coordinates the flow between WhatsApp, SMS, and Timo modules.
 * Manages the complete transaction lifecycle from request to recharge.
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
    
    /**
     * Processes a new recharge request from WhatsApp.
     * 
     * @param timoId The Timo ID to recharge
     * @param amount The amount in EGP
     * @param senderNumber The sender's phone number
     * @return The ID of the created transaction
     */
    suspend fun processRechargeRequest(timoId: String, amount: Double, senderNumber: String): Long {
        // Calculate coins based on amount
        val coinQuantity = coinCalculator.calculateCoins(amount)
        
        // Create transaction
        val transaction = Transaction(
            timoId = timoId,
            senderNumber = senderNumber,
            requestAmount = amount,
            coinQuantity = coinQuantity,
            requestTimestamp = System.currentTimeMillis(),
            status = TransactionStatus.PENDING_PAYMENT
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
        
        // Schedule payment timeout check
        schedulePaymentTimeoutCheck(transactionId, settings.paymentTimeoutMinutes)
        
        return transactionId
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
        // Find matching transaction
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
            
            // Schedule recharge
            scheduleRecharge(transaction.id)
            
            return true
        }
        
        return false
    }
    
    /**
     * Processes a recharge completion.
     * 
     * @param transactionId The ID of the transaction
     * @param success Whether the recharge was successful
     * @param errorMessage Optional error message if the recharge failed
     */
    suspend fun processRechargeCompletion(transactionId: Long, success: Boolean, errorMessage: String? = null) {
        val transaction = transactionRepository.getTransactionById(transactionId) ?: return
        
        // Update transaction
        val updatedTransaction = if (success) {
            transaction.copy(
                rechargeCompleted = true,
                rechargeTimestamp = System.currentTimeMillis(),
                status = TransactionStatus.RECHARGE_COMPLETED
            )
        } else {
            transaction.copy(
                status = TransactionStatus.RECHARGE_FAILED,
                notes = transaction.notes + "\nRecharge failed: $errorMessage"
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
        }
    }
    
    /**
     * Finds a matching transaction for payment verification.
     * 
     * @param senderNumber The sender's phone number
     * @param amount The amount in EGP
     * @param timestamp The timestamp of the payment
     * @return The matching transaction or null if not found
     */
    private suspend fun findMatchingTransaction(senderNumber: String, amount: Double, timestamp: Long): Transaction? {
        val settings = settingsRepository.getSettings().first()
        val pendingTransactions = transactionRepository.getTransactionsByStatus(TransactionStatus.PENDING_PAYMENT).first()
        
        // Check for exact match first
        val exactMatch = pendingTransactions.find { 
            it.senderNumber == senderNumber && 
            Math.abs(it.requestAmount - amount) < 0.01 &&
            Math.abs(timestamp - it.requestTimestamp) < settings.paymentTimeoutMinutes * 60 * 1000
        }
        
        if (exactMatch != null) {
            return exactMatch
        }
        
        // Check for partial match (last 5 digits of phone number)
        val last5DigitsMatch = pendingTransactions.find {
            val last5Digits = it.senderNumber.takeLast(5)
            val senderLast5Digits = senderNumber.takeLast(5)
            
            last5Digits == senderLast5Digits &&
            Math.abs(it.requestAmount - amount) < 0.01 &&
            Math.abs(timestamp - it.requestTimestamp) < settings.paymentTimeoutMinutes * 60 * 1000
        }
        
        return last5DigitsMatch
    }
    
    /**
     * Schedules a payment timeout check.
     * 
     * @param transactionId The ID of the transaction
     * @param timeoutMinutes The timeout in minutes
     */
    private fun schedulePaymentTimeoutCheck(transactionId: Long, timeoutMinutes: Int) {
        serviceScope.launch {
            // Wait for timeout
            kotlinx.coroutines.delay(timeoutMinutes * 60 * 1000L)
            
            // Check if payment was verified
            val transaction = transactionRepository.getTransactionById(transactionId) ?: return@launch
            
            if (!transaction.paymentVerified) {
                // Update transaction status
                val updatedTransaction = transaction.copy(
                    status = TransactionStatus.PAYMENT_TIMEOUT
                )
                
                transactionRepository.updateTransaction(updatedTransaction)
                
                // Log the timeout
                logRepository.addLog(
                    Log(
                        message = "Payment timeout: Timo ID ${transaction.timoId}, Amount ${transaction.requestAmount} EGP",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.WARNING,
                        source = "TransactionCoordinatorService"
                    )
                )
                
                // Send notification
                notificationService.showPaymentTimeoutNotification(updatedTransaction)
                
                // Notify customer
                whatsAppService.sendMessage(
                    transaction.senderNumber,
                    "Your payment for Timo ID ${transaction.timoId} has timed out. Please try again or contact support."
                )
            }
        }
    }
    
    /**
     * Schedules a recharge.
     * 
     * @param transactionId The ID of the transaction
     */
    private fun scheduleRecharge(transactionId: Long) {
        // Create work request
        val rechargeWorkRequest = OneTimeWorkRequestBuilder<RechargeWorker>()
            .setInputData(
                workDataOf(
                    RechargeWorker.KEY_TRANSACTION_ID to transactionId
                )
            )
            .build()
        
        // Enqueue work request
        WorkManager.getInstance(context).enqueue(rechargeWorkRequest)
    }
}
