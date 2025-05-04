package com.timoautorecharge.assistant.service

import android.content.Context
import android.content.Intent
import com.timoautorecharge.assistant.data.model.Customer
import com.timoautorecharge.assistant.data.model.LogType
import com.timoautorecharge.assistant.data.model.Transaction
import com.timoautorecharge.assistant.data.model.TransactionStatus
import com.timoautorecharge.assistant.data.repository.CustomerRepository
import com.timoautorecharge.assistant.data.repository.LogRepository
import com.timoautorecharge.assistant.data.repository.SettingsRepository
import com.timoautorecharge.assistant.data.repository.TransactionRepository
import com.timoautorecharge.assistant.util.CoinCalculator
import com.timoautorecharge.assistant.util.NotificationService
import kotlinx.coroutines.flow.first
import java.util.regex.Pattern

class WhatsAppService(
    private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val customerRepository: CustomerRepository,
    private val logRepository: LogRepository,
    private val coinCalculator: CoinCalculator,
    private val notificationService: NotificationService
) {
    
    // Regex patterns for extracting information from messages
    private val rechargeRequestPattern = 
        Pattern.compile("(?i).*recharge.*?(\\d+).*?ID[:\\s]*(\\d+).*")
    
    suspend fun processWhatsAppMessage(message: String, senderNumber: String) {
        try {
            val parseResult = parseRechargeRequest(message)
            if (parseResult != null) {
                val (timoId, requestAmount) = parseResult
                
                // Calculate coin quantity based on amount
                val coinQuantity = coinCalculator.calculateCoins(requestAmount)
                
                // Create transaction
                val transaction = Transaction(
                    timoId = timoId,
                    senderNumber = senderNumber,
                    requestAmount = requestAmount,
                    coinQuantity = coinQuantity,
                    requestTimestamp = System.currentTimeMillis(),
                    status = TransactionStatus.PENDING_PAYMENT
                )
                
                val transactionId = transactionRepository.createTransaction(transaction)
                
                // Save or update customer information
                saveCustomerInfo(senderNumber, timoId)
                
                // Send payment instructions
                val settings = settingsRepository.getSettingsSync()
                sendPaymentInstructions(senderNumber, requestAmount, settings.vodafoneWalletNumber)
                
                logRepository.logInfo(
                    LogType.WHATSAPP,
                    "Created transaction for $timoId, amount: $requestAmount, coins: $coinQuantity",
                    transactionId
                )
            } else {
                logRepository.logInfo(
                    LogType.WHATSAPP,
                    "Message from $senderNumber does not match recharge request pattern: $message"
                )
            }
        } catch (e: Exception) {
            logRepository.logError(
                LogType.WHATSAPP,
                "Error processing WhatsApp message: ${e.message}"
            )
        }
    }
    
    private fun parseRechargeRequest(message: String): Pair<String, Double>? {
        val matcher = rechargeRequestPattern.matcher(message)
        if (matcher.matches()) {
            val amount = matcher.group(1)?.toDoubleOrNull() ?: return null
            val timoId = matcher.group(2) ?: return null
            return Pair(timoId, amount)
        }
        return null
    }
    
    private suspend fun saveCustomerInfo(phoneNumber: String, timoId: String) {
        val existingCustomer = customerRepository.getCustomerByPhoneNumber(phoneNumber)
        
        if (existingCustomer != null) {
            customerRepository.updateCustomer(
                existingCustomer.copy(
                    timoId = timoId,
                    lastTransactionDate = System.currentTimeMillis(),
                    totalTransactions = existingCustomer.totalTransactions + 1
                )
            )
        } else {
            customerRepository.saveCustomer(
                Customer(
                    phoneNumber = phoneNumber,
                    timoId = timoId,
                    lastTransactionDate = System.currentTimeMillis(),
                    totalTransactions = 1
                )
            )
        }
    }
    
    private fun sendPaymentInstructions(
        phoneNumber: String, 
        amount: Double,
        walletNumber: String
    ) {
        // In a real implementation, this would use Accessibility Service to navigate WhatsApp UI
        // and send a message. For now, we'll just log it.
        logRepository.logInfo(
            LogType.WHATSAPP,
            "Would send payment instructions to $phoneNumber: " +
            "Please send $amount EGP to Vodafone Cash wallet $walletNumber"
        )
        
        // This is a placeholder for the actual implementation
        // In a real app, we would use Accessibility Service to:
        // 1. Open WhatsApp
        // 2. Navigate to the chat with the customer
        // 3. Type and send the payment instructions
    }
    
    fun sendPaymentConfirmation(phoneNumber: String, transaction: Transaction) {
        logRepository.logInfo(
            LogType.WHATSAPP,
            "Would send payment confirmation to $phoneNumber for transaction ${transaction.id}"
        )
        
        // Placeholder for actual implementation
    }
    
    fun sendRechargeConfirmation(phoneNumber: String, transaction: Transaction) {
        logRepository.logInfo(
            LogType.WHATSAPP,
            "Would send recharge confirmation to $phoneNumber for transaction ${transaction.id}: " +
            "${transaction.coinQuantity} coins have been recharged to Timo ID ${transaction.timoId}"
        )
        
        // Placeholder for actual implementation
    }
}
