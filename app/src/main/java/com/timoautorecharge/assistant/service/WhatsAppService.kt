package com.timoautorecharge.assistant.service

import android.content.Context
import android.content.Intent
import com.timoautorecharge.assistant.R
import com.timoautorecharge.assistant.data.model.Customer
import com.timoautorecharge.assistant.data.model.Log
import com.timoautorecharge.assistant.data.model.Transaction
import com.timoautorecharge.assistant.data.repository.CustomerRepository
import com.timoautorecharge.assistant.data.repository.LogRepository
import com.timoautorecharge.assistant.data.repository.SettingsRepository
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

/**
 * Service responsible for processing WhatsApp messages and sending responses.
 * Uses WhatsAppAccessibilityService for UI automation and TransactionCoordinatorService
 * for transaction management.
 */
class WhatsAppService(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val customerRepository: CustomerRepository,
    private val logRepository: LogRepository,
    private val templateManager: WhatsAppTemplateManager
) {
    private val whatsAppAccessibilityService: WhatsAppAccessibilityService by inject(WhatsAppAccessibilityService::class.java)
    private val transactionCoordinator: TransactionCoordinatorService by inject(TransactionCoordinatorService::class.java)
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
                    logRepository.addLog(
                        Log(
                            message = "WhatsApp service status changed to ${_serviceStatus.value}",
                            timestamp = System.currentTimeMillis(),
                            level = Log.Level.INFO,
                            source = "WhatsAppService"
                        )
                    )
                }
            } catch (e: Exception) {
                logRepository.addLog(
                    Log(
                        message = "Error monitoring WhatsApp service status: ${e.message}",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.ERROR,
                        source = "WhatsAppService"
                    )
                )
                _serviceStatus.value = ServiceStatus.INACTIVE
            }
        }
    }
    
    /**
     * Process a WhatsApp message from a customer.
     * Parses the message for recharge request details and creates a transaction
     * through the TransactionCoordinatorService.
     *
     * @param message The message content
     * @param senderNumber The sender's phone number
     */
    suspend fun processWhatsAppMessage(message: String, senderNumber: String) {
        try {
            // Log the received message
            logRepository.addLog(
                Log(
                    message = "Processing WhatsApp message from $senderNumber",
                    timestamp = System.currentTimeMillis(),
                    level = Log.Level.INFO,
                    source = "WhatsAppService"
                )
            )
            
            // Use template manager to get the appropriate regex pattern
            val parseResult = templateManager.parseRechargeRequest(message)
            
            if (parseResult != null) {
                val (timoId, requestAmount) = parseResult
                
                // Log the parsed request
                logRepository.addLog(
                    Log(
                        message = "Parsed recharge request: Timo ID $timoId, Amount $requestAmount EGP",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.INFO,
                        source = "WhatsAppService"
                    )
                )
                
                // Save or update customer information
                saveCustomerInfo(senderNumber, timoId)
                
                // Process the recharge request through the coordinator
                val transactionId = transactionCoordinator.processRechargeRequest(
                    timoId = timoId,
                    amount = requestAmount,
                    senderNumber = senderNumber
                )
                
                logRepository.addLog(
                    Log(
                        message = "Created transaction $transactionId for Timo ID $timoId, Amount $requestAmount EGP",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.INFO,
                        source = "WhatsAppService"
                    )
                )
            } else {
                logRepository.addLog(
                    Log(
                        message = "Message from $senderNumber does not match recharge request pattern",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.INFO,
                        source = "WhatsAppService"
                    )
                )
            }
        } catch (e: Exception) {
            logRepository.addLog(
                Log(
                    message = "Error processing WhatsApp message: ${e.message}",
                    timestamp = System.currentTimeMillis(),
                    level = Log.Level.ERROR,
                    source = "WhatsAppService"
                )
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
     * Send a message to a customer via WhatsApp.
     * 
     * @param phoneNumber The customer's phone number
     * @param message The message to send
     * @return True if the message was sent successfully, false otherwise
     */
    suspend fun sendMessage(phoneNumber: String, message: String): Boolean {
        if (_serviceStatus.value != ServiceStatus.ACTIVE) {
            logRepository.addLog(
                Log(
                    message = "WhatsApp service is not active. Cannot send message to $phoneNumber",
                    timestamp = System.currentTimeMillis(),
                    level = Log.Level.WARNING,
                    source = "WhatsAppService"
                )
            )
            return false
        }
        
        var success = false
        
        try {
            // Send the message using the accessibility service
            success = whatsAppAccessibilityService.sendWhatsAppMessage(phoneNumber, message)
            
            if (success) {
                logRepository.addLog(
                    Log(
                        message = "Sent message to $phoneNumber",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.INFO,
                        source = "WhatsAppService"
                    )
                )
            } else {
                logRepository.addLog(
                    Log(
                        message = "Failed to send message to $phoneNumber, trying alternative method",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.WARNING,
                        source = "WhatsAppService"
                    )
                )
                
                // Try alternative method
                success = whatsAppAccessibilityService.sendWhatsAppMessageViaDeepLink(phoneNumber, message)
                
                if (success) {
                    logRepository.addLog(
                        Log(
                            message = "Sent message to $phoneNumber using deep link",
                            timestamp = System.currentTimeMillis(),
                            level = Log.Level.INFO,
                            source = "WhatsAppService"
                        )
                    )
                } else {
                    logRepository.addLog(
                        Log(
                            message = "Failed to send message to $phoneNumber using deep link",
                            timestamp = System.currentTimeMillis(),
                            level = Log.Level.ERROR,
                            source = "WhatsAppService"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logRepository.addLog(
                Log(
                    message = "Error sending message to $phoneNumber: ${e.message}",
                    timestamp = System.currentTimeMillis(),
                    level = Log.Level.ERROR,
                    source = "WhatsAppService"
                )
            )
            success = false
        }
        
        return success
    }
    
    /**
     * Get the current service status.
     * 
     * @return The current service status
     */
    fun getServiceStatus(): ServiceStatus {
        return _serviceStatus.value
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
