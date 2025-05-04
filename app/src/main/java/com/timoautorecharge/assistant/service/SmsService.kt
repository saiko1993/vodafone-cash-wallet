package com.timoautorecharge.assistant.service

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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Service responsible for processing SMS messages, particularly Vodafone Cash payment notifications.
 * Uses configurable templates to parse SMS content and match payments with pending transactions.
 */
class SmsService(
    private val context: android.content.Context,
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val logRepository: LogRepository,
    private val notificationService: NotificationService
) {
    
    /**
     * Process an incoming SMS message to check if it's a Vodafone Cash payment notification
     * and match it with a pending transaction.
     *
     * @param sender The phone number or identifier of the SMS sender
     * @param message The content of the SMS message
     */
    suspend fun processSmsMessage(sender: String, message: String) {
        val settings = settingsRepository.getSettingsSync()
        val currentTime = System.currentTimeMillis()
        
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
            val (amount, senderNumber, timestamp) = paymentInfo
            
            // Log the parsed payment information
            logRepository.logInfo(
                LogType.SMS,
                "Parsed payment: Amount=$amount EGP, Sender=$senderNumber, Time=${formatTimestamp(timestamp)}"
            )
            
            // Find matching pending transaction
            val cutoffTime = currentTime - (settings.paymentTimeoutMinutes * 60 * 1000)
            val pendingTransactions = transactionRepository.getPendingPaymentTransactions(cutoffTime)
            
            val matchingTransaction = findMatchingTransaction(
                pendingTransactions,
                amount,
                senderNumber,
                settings.smsMatchByLastDigits
            )
            
            if (matchingTransaction != null) {
                // Calculate time difference for logging
                val timeDiffMinutes = (currentTime - matchingTransaction.requestTimestamp) / (60 * 1000)
                
                // Update transaction status
                val updatedTransaction = matchingTransaction.copy(
                    paymentVerified = true,
                    paymentTimestamp = timestamp ?: currentTime,
                    status = TransactionStatus.PAYMENT_VERIFIED
                )
                
                transactionRepository.updateTransaction(updatedTransaction)
                
                // Notify admin
                notificationService.notifyPaymentVerified(updatedTransaction)
                
                // Schedule recharge
                scheduleRecharge(updatedTransaction.id)
                
                logRepository.logInfo(
                    LogType.SMS,
                    "Payment verified for transaction ${updatedTransaction.id}. " +
                    "Time since request: $timeDiffMinutes minutes",
                    updatedTransaction.id
                )
            } else {
                logRepository.logWarning(
                    LogType.SMS,
                    "No matching transaction found for payment: $amount EGP from $senderNumber"
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
    
    /**
     * Parse a Vodafone Cash SMS message using the provided regex template.
     * Extracts amount, sender number, and timestamp if available.
     *
     * @param message The SMS message content
     * @param template The regex template to use for parsing
     * @return Triple containing amount, sender number, and timestamp (or null if not available)
     */
    private fun parseVodafoneCashSms(message: String, template: String): Triple<Double, String, Long?>? {
        try {
            val pattern = Pattern.compile(template, Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
            val matcher = pattern.matcher(message)
            
            if (matcher.matches()) {
                // Extract amount (required)
                val amount = matcher.group(1)?.toDoubleOrNull() ?: return null
                
                // Extract sender number (required)
                val senderNumber = matcher.group(2) ?: return null
                
                // Extract timestamp if available (optional)
                val timestamp = if (matcher.groupCount() >= 3 && matcher.group(3) != null) {
                    parseTimestamp(matcher.group(3))
                } else {
                    null
                }
                
                return Triple(amount, senderNumber, timestamp)
            }
        } catch (e: Exception) {
            logRepository.logError(
                LogType.SMS,
                "Error parsing SMS: ${e.message}"
            )
        }
        
        return null
    }
    
    /**
     * Attempt to parse a timestamp string from the SMS.
     * Handles various date formats commonly found in SMS messages.
     *
     * @param dateStr The date string from the SMS
     * @return Timestamp in milliseconds or null if parsing failed
     */
    private fun parseTimestamp(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        
        // Try different date formats commonly found in SMS messages
        val dateFormats = listOf(
            "dd/MM/yyyy HH:mm:ss",
            "dd-MM-yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "dd-MM-yyyy HH:mm",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )
        
        for (format in dateFormats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault())
                val date = sdf.parse(dateStr.trim())
                if (date != null) {
                    return date.time
                }
            } catch (e: Exception) {
                // Try next format
            }
        }
        
        return null
    }
    
    /**
     * Format a timestamp for logging purposes.
     *
     * @param timestamp The timestamp in milliseconds
     * @return Formatted date string
     */
    private fun formatTimestamp(timestamp: Long?): String {
        if (timestamp == null) return "Unknown"
        
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    /**
     * Find a matching transaction based on amount and sender number.
     * Uses configurable settings for matching by last N digits.
     *
     * @param transactions List of pending transactions to check
     * @param amount The payment amount to match
     * @param senderNumber The sender's phone number
     * @param matchByLastDigits Number of last digits to use for matching
     * @return Matching transaction or null if none found
     */
    private fun findMatchingTransaction(
        transactions: List<Transaction>,
        amount: Double,
        senderNumber: String,
        matchByLastDigits: Int
    ): Transaction? {
        // Clean the sender number (remove non-digit characters)
        val cleanSenderNumber = senderNumber.replace(Regex("\\D"), "")
        
        // Match by exact amount and either full phone number or last N digits
        return transactions.find { transaction ->
            val cleanTransactionSender = transaction.senderNumber.replace(Regex("\\D"), "")
            
            // Check if amount matches exactly
            val amountMatches = transaction.requestAmount == amount
            
            // Check if sender matches (either full number or last N digits)
            val senderMatches = if (cleanSenderNumber.length >= matchByLastDigits && 
                                   cleanTransactionSender.length >= matchByLastDigits) {
                // Match by last N digits
                cleanSenderNumber.takeLast(matchByLastDigits) == 
                cleanTransactionSender.takeLast(matchByLastDigits)
            } else {
                // Fall back to full number match if numbers are too short
                cleanSenderNumber == cleanTransactionSender
            }
            
            amountMatches && senderMatches
        }
    }
    
    /**
     * Schedule a recharge operation for a verified transaction.
     *
     * @param transactionId The ID of the transaction to recharge
     */
    private fun scheduleRecharge(transactionId: Long) {
        val workManager = WorkManager.getInstance(context)
        val rechargeWorkRequest = OneTimeWorkRequestBuilder<RechargeWorker>()
            .setInputData(workDataOf("transactionId" to transactionId))
            .build()
        
        workManager.enqueue(rechargeWorkRequest)
        
        logRepository.logInfo(
            LogType.SMS,
            "Scheduled recharge for transaction $transactionId",
            transactionId
        )
    }
}
