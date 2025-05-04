package com.timoautorecharge.assistant.service

import android.content.Context
import android.content.Intent
import com.timoautorecharge.assistant.R
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
import com.timoautorecharge.assistant.util.WhatsAppTemplateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject
import java.util.regex.Pattern

/**
 * Service responsible for processing WhatsApp messages and sending responses.
 * Uses WhatsAppAccessibilityService for UI automation.
 */
class WhatsAppService(
    private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val settingsRepository: SettingsRepository,
    private val customerRepository: CustomerRepository,
    private val logRepository: LogRepository,
    private val coinCalculator: CoinCalculator,
    private val notificationService: NotificationService,
    private val templateManager: WhatsAppTemplateManager
) {
    private val whatsAppAccessibilityService: WhatsAppAccessibilityService by inject(WhatsAppAccessibilityService::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Service status
    private val _serviceStatus = MutableStateFlow(ServiceStatus.CHECKING)
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()
    
    // Initialize service
    init {
        // Monitor accessibility service status
        scope.launch {
            try {
                whatsAppAccessibilityService.serviceStatus.collect { accessibilityStatus ->
                    _serviceStatus.value = when (accessibilityStatus) {
                        WhatsAppAccessibilityService.ServiceStatus.ACTIVE -> ServiceStatus.ACTIVE
                        WhatsAppAccessibilityService.ServiceStatus.INACTIVE -> ServiceStatus.INACTIVE
                    }
                    
                    // Log status changes
                    logRepository.logInfo(
                        LogType.WHATSAPP,
                        "WhatsApp service status changed to ${_serviceStatus.value}"
                    )
                    
                    // Notify status changes
                    notificationService.notifyServiceStatusChange(
                        "WhatsApp Service",
                        if (_serviceStatus.value == ServiceStatus.ACTIVE) 
                            context.getString(R.string.whatsapp_service_active)
                        else 
                            context.getString(R.string.whatsapp_service_inactive)
                    )
                }
            } catch (e: Exception) {
                logRepository.logError(
                    LogType.WHATSAPP,
                    "Error monitoring WhatsApp service status: ${e.message}"
                )
                _serviceStatus.value = ServiceStatus.INACTIVE
            }
        }
    }
    
    /**
     * Process a WhatsApp message from a customer.
     * Parses the message for recharge request details and creates a transaction.
     *
     * @param message The message content
     * @param senderNumber The sender's phone number
     */
    suspend fun processWhatsAppMessage(message: String, senderNumber: String) {
        try {
            // Get settings for message parsing
            val settings = settingsRepository.getSettingsSync()
            
            // Use template manager to get the appropriate regex pattern
            val parseResult = templateManager.parseRechargeRequest(message)
            
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
                sendPaymentInstructions(senderNumber, requestAmount, settings.vodafoneWalletNumber)
                
                // Notify admin about new transaction
                notificationService.notifyNewTransaction(transaction)
                
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
    
    /**
     * Save or update customer information in the database.
     *
     * @param phoneNumber The customer's phone number
     * @param timoId The customer's Timo ID
     */
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
    
    /**
     * Send payment instructions to a customer via WhatsApp.
     *
     * @param phoneNumber The customer's phone number
     * @param amount The payment amount
     * @param walletNumber The Vodafone wallet number
     */
    private fun sendPaymentInstructions(
        phoneNumber: String, 
        amount: Double,
        walletNumber: String
    ) {
        // Format the message using the template
        val message = context.getString(
            R.string.payment_instructions,
            amount.toString(),
            walletNumber
        )
        
        // Send the message using the accessibility service
        if (_serviceStatus.value == ServiceStatus.ACTIVE) {
            whatsAppAccessibilityService.sendWhatsAppMessage(
                phoneNumber,
                message
            ) { success ->
                if (success) {
                    logRepository.logInfo(
                        LogType.WHATSAPP,
                        "Sent payment instructions to $phoneNumber: $message"
                    )
                } else {
                    logRepository.logError(
                        LogType.WHATSAPP,
                        "Failed to send payment instructions to $phoneNumber"
                    )
                    
                    // Try alternative method
                    whatsAppAccessibilityService.sendWhatsAppMessageViaDeepLink(
                        phoneNumber,
                        message
                    )
                }
            }
        } else {
            logRepository.logWarning(
                LogType.WHATSAPP,
                "WhatsApp service is not active. Cannot send payment instructions to $phoneNumber"
            )
            
            // Notify admin that service is not active
            notificationService.notifyServiceInactive("WhatsApp Service")
        }
    }
    
    /**
     * Send payment confirmation to a customer via WhatsApp.
     *
     * @param phoneNumber The customer's phone number
     * @param transaction The transaction details
     */
    fun sendPaymentConfirmation(phoneNumber: String, transaction: Transaction) {
        val message = context.getString(
            R.string.payment_confirmed,
            transaction.requestAmount.toString()
        )
        
        if (_serviceStatus.value == ServiceStatus.ACTIVE) {
            whatsAppAccessibilityService.sendWhatsAppMessage(
                phoneNumber,
                message
            ) { success ->
                if (success) {
                    logRepository.logInfo(
                        LogType.WHATSAPP,
                        "Sent payment confirmation to $phoneNumber for transaction ${transaction.id}"
                    )
                } else {
                    logRepository.logError(
                        LogType.WHATSAPP,
                        "Failed to send payment confirmation to $phoneNumber"
                    )
                    
                    // Try alternative method
                    whatsAppAccessibilityService.sendWhatsAppMessageViaDeepLink(
                        phoneNumber,
                        message
                    )
                }
            }
        } else {
            logRepository.logWarning(
                LogType.WHATSAPP,
                "WhatsApp service is not active. Cannot send payment confirmation to $phoneNumber"
            )
        }
    }
    
    /**
     * Send recharge confirmation to a customer via WhatsApp.
     *
     * @param phoneNumber The customer's phone number
     * @param transaction The transaction details
     */
    fun sendRechargeConfirmation(phoneNumber: String, transaction: Transaction) {
        val message = context.getString(
            R.string.recharge_completed,
            transaction.coinQuantity,
            transaction.timoId
        )
        
        if (_serviceStatus.value == ServiceStatus.ACTIVE) {
            whatsAppAccessibilityService.sendWhatsAppMessage(
                phoneNumber,
                message
            ) { success ->
                if (success) {
                    logRepository.logInfo(
                        LogType.WHATSAPP,
                        "Sent recharge confirmation to $phoneNumber for transaction ${transaction.id}"
                    )
                } else {
                    logRepository.logError(
                        LogType.WHATSAPP,
                        "Failed to send recharge confirmation to $phoneNumber"
                    )
                    
                    // Try alternative method
                    whatsAppAccessibilityService.sendWhatsAppMessageViaDeepLink(
                        phoneNumber,
                        message
                    )
                }
            }
        } else {
            logRepository.logWarning(
                LogType.WHATSAPP,
                "WhatsApp service is not active. Cannot send recharge confirmation to $phoneNumber"
            )
        }
    }
    
    /**
     * Send recharge failure notification to a customer via WhatsApp.
     *
     * @param phoneNumber The customer's phone number
     * @param transaction The transaction details
     */
    fun sendRechargeFailure(phoneNumber: String, transaction: Transaction) {
        val message = context.getString(
            R.string.recharge_failed,
            transaction.timoId
        )
        
        if (_serviceStatus.value == ServiceStatus.ACTIVE) {
            whatsAppAccessibilityService.sendWhatsAppMessage(
                phoneNumber,
                message
            ) { success ->
                if (success) {
                    logRepository.logInfo(
                        LogType.WHATSAPP,
                        "Sent recharge failure notification to $phoneNumber for transaction ${transaction.id}"
                    )
                } else {
                    logRepository.logError(
                        LogType.WHATSAPP,
                        "Failed to send recharge failure notification to $phoneNumber"
                    )
                    
                    // Try alternative method
                    whatsAppAccessibilityService.sendWhatsAppMessageViaDeepLink(
                        phoneNumber,
                        message
                    )
                }
            }
        } else {
            logRepository.logWarning(
                LogType.WHATSAPP,
                "WhatsApp service is not active. Cannot send recharge failure notification to $phoneNumber"
            )
        }
    }
    
    /**
     * Enum representing the status of the WhatsApp service.
     */
    enum class ServiceStatus {
        ACTIVE,
        INACTIVE,
        CHECKING
    }
}
