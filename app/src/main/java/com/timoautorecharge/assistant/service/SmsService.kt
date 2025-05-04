package com.timoautorecharge.assistant.service

import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.timoautorecharge.assistant.data.model.LogType
import com.timoautorecharge.assistant.data.model.Transaction
import com.timoautorecharge.assistant.data.model.TransactionStatus
import com.timoautorecharge.assistant.data.repository.LogRepository
import com.timoautorecharge.assistant.data.repository.SettingsRepository
import com.timoautorecharge.assistant.data.repository.TransactionRepository
import com.timoautorecharge.assistant.util.NotificationService
import com.timoautorecharge.assistant.worker.RechargeWorker
import kotlinx.coroutines.flow.first
import java.util.regex.Pattern

class SmsService(
    private val context: android.content.Context,
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val logRepository: LogRepository,
    private val notificationService: NotificationService
) {
    
    suspend fun processSmsMessage(sender: String, message: String) {
        val settings = settingsRepository.getSettingsSync()
        
        // Check if this is from a Vodafone Cash sender
        if (!settings.vodafoneSenderNumbers.any { sender.contains(it, ignoreCase = true) }) {
            logRepository.logInfo(
                LogType.SMS,
                "Ignored SMS from non-Vodafone sender: $sender"
            )
            return
        }
        
        // Parse the SMS using the configurable template
        val paymentInfo = parseVodafoneCashSms(message, settings.vodafoneSmsTemplate)
        if (paymentInfo != null) {
            val (amount, senderNumber) = paymentInfo
            
            // Find matching pending transaction
            val cutoffTime = System.currentTimeMillis() - (settings.paymentTimeoutMinutes * 60 * 1000)
            val pendingTransactions = transactionRepository.getPendingPaymentTransactions(cutoffTime)
            
            val matchingTransaction = findMatchingTransaction(
                pendingTransactions,
                amount,
                senderNumber
            )
            
            if (matchingTransaction != null) {
                // Update transaction status
                val updatedTransaction = matchingTransaction.copy(
                    paymentVerified = true,
                    paymentTimestamp = System.currentTimeMillis(),
                    status = TransactionStatus.PAYMENT_VERIFIED
                )
                
                transactionRepository.updateTransaction(updatedTransaction)
                
                // Notify admin
                notificationService.notifyPaymentVerified(updatedTransaction)
                
                // Schedule recharge
                scheduleRecharge(updatedTransaction.id)
                
                logRepository.logInfo(
                    LogType.SMS,
                    "Payment verified for transaction ${updatedTransaction.id}",
                    updatedTransaction.id
                )
            } else {
                logRepository.logWarning(
                    LogType.SMS,
                    "No matching transaction found for payment: $amount from $senderNumber"
                )
                notificationService.notifyUnmatchedPayment(amount, senderNumber)
            }
        } else {
            logRepository.logInfo(
                LogType.SMS,
                "SMS does not match Vodafone Cash pattern: $message"
            )
        }
    }
    
    private fun parseVodafoneCashSms(message: String, template: String): Pair<Double, String>? {
        try {
            val pattern = Pattern.compile(template)
            val matcher = pattern.matcher(message)
            
            if (matcher.matches() && matcher.groupCount() >= 2) {
                val amount = matcher.group(1)?.toDoubleOrNull() ?: return null
                val senderNumber = matcher.group(2) ?: return null
                return Pair(amount, senderNumber)
            }
        } catch (e: Exception) {
            logRepository.logError(
                LogType.SMS,
                "Error parsing SMS: ${e.message}"
            )
        }
        
        return null
    }
    
    private fun findMatchingTransaction(
        transactions: List<Transaction>,
        amount: Double,
        senderNumber: String
    ): Transaction? {
        // Match by exact amount and either full phone number or last 5 digits
        return transactions.find { transaction ->
            transaction.requestAmount == amount &&
            (transaction.senderNumber == senderNumber ||
             transaction.senderNumber.takeLast(5) == senderNumber.takeLast(5))
        }
    }
    
    private fun scheduleRecharge(transactionId: Long) {
        val workManager = WorkManager.getInstance(context)
        val rechargeWorkRequest = OneTimeWorkRequestBuilder<RechargeWorker>()
            .setInputData(workDataOf("transactionId" to transactionId))
            .build()
        
        workManager.enqueue(rechargeWorkRequest)
    }
}
