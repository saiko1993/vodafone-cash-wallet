package com.timoautorecharge.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.timoautorecharge.assistant.data.model.LogType
import com.timoautorecharge.assistant.data.repository.LogRepository
import com.timoautorecharge.assistant.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

/**
 * Accessibility service for interacting with WhatsApp UI.
 * Provides functionality to navigate WhatsApp, find chats, and send messages.
 */
class WhatsAppAccessibilityService : AccessibilityService() {
    
    private val settingsRepository: SettingsRepository by inject(SettingsRepository::class.java)
    private val logRepository: LogRepository by inject(LogRepository::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    
    // WhatsApp package name
    private val whatsAppPackageName = "com.whatsapp"
    
    // Current operation state
    private var currentPhoneNumber: String? = null
    private var currentMessage: String? = null
    private var currentStep = WhatsAppStep.IDLE
    private var retryCount = 0
    private var operationCallback: ((Boolean) -> Unit)? = null
    
    // Service status
    private val _serviceStatus = MutableStateFlow(ServiceStatus.INACTIVE)
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        _serviceStatus.value = ServiceStatus.ACTIVE
        logRepository.logInfo(
            LogType.ACCESSIBILITY,
            "WhatsApp Accessibility Service connected"
        )
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (currentStep == WhatsAppStep.IDLE) return
        
        val packageName = event.packageName?.toString() ?: return
        
        if (packageName == whatsAppPackageName) {
            when (currentStep) {
                WhatsAppStep.FIND_CHAT -> findChat(currentPhoneNumber ?: return)
                WhatsAppStep.ENTER_MESSAGE -> enterMessage(currentMessage ?: return)
                WhatsAppStep.SEND_MESSAGE -> sendMessage()
                else -> {}
            }
        }
    }
    
    override fun onInterrupt() {
        _serviceStatus.value = ServiceStatus.INACTIVE
        logRepository.logWarning(
            LogType.ACCESSIBILITY,
            "WhatsApp Accessibility Service interrupted"
        )
    }
    
    override fun onDestroy() {
        super.onDestroy()
        _serviceStatus.value = ServiceStatus.INACTIVE
    }
    
    /**
     * Send a message to a WhatsApp contact.
     * 
     * @param phoneNumber The phone number to send the message to
     * @param message The message to send
     * @param callback Callback to be called when the operation completes
     */
    fun sendWhatsAppMessage(phoneNumber: String, message: String, callback: (Boolean) -> Unit) {
        scope.launch {
            try {
                // Reset state
                currentPhoneNumber = phoneNumber
                currentMessage = message
                currentStep = WhatsAppStep.LAUNCH_APP
                retryCount = 0
                operationCallback = callback
                
                // Launch WhatsApp
                val intent = packageManager.getLaunchIntentForPackage(whatsAppPackageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    
                    // Move to next step after delay
                    handler.postDelayed({
                        currentStep = WhatsAppStep.FIND_CHAT
                    }, 1000)
                    
                    logRepository.logInfo(
                        LogType.ACCESSIBILITY,
                        "Launched WhatsApp to send message to $phoneNumber"
                    )
                } else {
                    logRepository.logError(
                        LogType.ACCESSIBILITY,
                        "Failed to launch WhatsApp: Package $whatsAppPackageName not found"
                    )
                    operationCallback?.invoke(false)
                    resetState()
                }
            } catch (e: Exception) {
                logRepository.logError(
                    LogType.ACCESSIBILITY,
                    "Error starting WhatsApp message: ${e.message}"
                )
                operationCallback?.invoke(false)
                resetState()
            }
        }
    }
    
    /**
     * Alternative method to send WhatsApp message using deep linking.
     * This is a fallback method if accessibility navigation fails.
     * 
     * @param phoneNumber The phone number to send the message to
     * @param message The message to send
     */
    fun sendWhatsAppMessageViaDeepLink(phoneNumber: String, message: String) {
        try {
            // Format phone number (remove any non-digit characters and ensure it has country code)
            val formattedNumber = phoneNumber.replace(Regex("\\D"), "")
            
            // Create deep link intent
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setPackage(whatsAppPackageName)
            
            // WhatsApp deep link format: https://api.whatsapp.com/send?phone=PHONE_NUMBER&text=MESSAGE
            val url = "https://api.whatsapp.com/send?phone=$formattedNumber&text=${message.replace(" ", "%20")}"
            intent.data = android.net.Uri.parse(url)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            startActivity(intent)
            
            logRepository.logInfo(
                LogType.ACCESSIBILITY,
                "Sent WhatsApp message via deep link to $phoneNumber"
            )
        } catch (e: Exception) {
            logRepository.logError(
                LogType.ACCESSIBILITY,
                "Error sending WhatsApp message via deep link: ${e.message}"
            )
        }
    }
    
    /**
     * Find and open a chat with the specified phone number.
     * 
     * @param phoneNumber The phone number to find
     */
    private fun findChat(phoneNumber: String) {
        val rootNode = rootInActiveWindow ?: return
        
        // Try to find the search button first
        val searchButton = findNodeByContentDescription(rootNode, "Search")
        if (searchButton != null && searchButton.isClickable) {
            searchButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            
            // Wait a moment and then enter the phone number
            handler.postDelayed({
                val searchEditText = findEditTextNode(rootInActiveWindow ?: return@postDelayed)
                if (searchEditText != null) {
                    val arguments = Bundle()
                    arguments.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, 
                        phoneNumber
                    )
                    searchEditText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                    
                    // Wait for search results and click on the contact
                    handler.postDelayed({
                        val contactNode = findNodeContainingText(
                            rootInActiveWindow ?: return@postDelayed,
                            phoneNumber
                        )
                        if (contactNode != null) {
                            contactNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            
                            // Move to next step
                            handler.postDelayed({
                                currentStep = WhatsAppStep.ENTER_MESSAGE
                            }, 1000)
                        } else {
                            handleNavigationFailure("Could not find contact with number $phoneNumber")
                        }
                    }, 1000)
                } else {
                    handleNavigationFailure("Could not find search input field")
                }
            }, 1000)
        } else {
            // If we can't find the search button, try the alternative method
            sendWhatsAppMessageViaDeepLink(phoneNumber, currentMessage ?: "")
            resetState()
        }
    }
    
    /**
     * Enter a message in the message input field.
     * 
     * @param message The message to enter
     */
    private fun enterMessage(message: String) {
        val rootNode = rootInActiveWindow ?: return
        
        // Find the message input field
        val messageInput = findEditTextNode(rootNode)
        
        if (messageInput != null) {
            // Set the message text
            val arguments = Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, 
                message
            )
            messageInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            
            // Move to next step
            handler.postDelayed({
                currentStep = WhatsAppStep.SEND_MESSAGE
            }, 500)
        } else {
            handleNavigationFailure("Could not find message input field")
        }
    }
    
    /**
     * Send the message by clicking the send button.
     */
    private fun sendMessage() {
        val rootNode = rootInActiveWindow ?: return
        
        // Find the send button
        val sendButton = findNodeByContentDescription(rootNode, "Send")
        
        if (sendButton != null && sendButton.isClickable) {
            sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            
            logRepository.logInfo(
                LogType.ACCESSIBILITY,
                "Message sent to $currentPhoneNumber"
            )
            
            // Operation completed successfully
            operationCallback?.invoke(true)
            resetState()
        } else {
            handleNavigationFailure("Could not find or click send button")
        }
    }
    
    /**
     * Find a node by its content description.
     * 
     * @param root The root node to search from
     * @param description The content description to search for
     * @return The node with the specified content description, or null if not found
     */
    private fun findNodeByContentDescription(root: AccessibilityNodeInfo, description: String): AccessibilityNodeInfo? {
        // Check if this node matches
        if (root.contentDescription?.contains(description) == true) {
            return root
        }
        
        // Check child nodes
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findNodeByContentDescription(child, description)
            if (result != null) {
                return result
            }
        }
        
        return null
    }
    
    /**
     * Find a node containing the specified text.
     * 
     * @param root The root node to search from
     * @param text The text to search for
     * @return The node containing the specified text, or null if not found
     */
    private fun findNodeContainingText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        // Check if this node matches
        if (root.text?.contains(text) == true) {
            return root
        }
        
        // Check child nodes
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findNodeContainingText(child, text)
            if (result != null) {
                return result
            }
        }
        
        return null
    }
    
    /**
     * Find an edit text node.
     * 
     * @param root The root node to search from
     * @return The first edit text node found, or null if not found
     */
    private fun findEditTextNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Check if this node is an edit text
        if (root.className?.contains("EditText") == true) {
            return root
        }
        
        // Check child nodes
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findEditTextNode(child)
            if (result != null) {
                return result
            }
        }
        
        return null
    }
    
    /**
     * Handle navigation failures by retrying or reporting failure.
     * 
     * @param errorMessage The error message to log
     */
    private fun handleNavigationFailure(errorMessage: String) {
        if (retryCount < 3) {
            retryCount++
            
            logRepository.logWarning(
                LogType.ACCESSIBILITY,
                "$errorMessage, retrying (attempt $retryCount of 3)"
            )
            
            // Retry after delay
            handler.postDelayed({
                // Keep the same step for retry
            }, 1000)
        } else {
            logRepository.logError(
                LogType.ACCESSIBILITY,
                "$errorMessage, max retries exceeded"
            )
            
            // Try alternative method
            if (currentPhoneNumber != null && currentMessage != null) {
                sendWhatsAppMessageViaDeepLink(currentPhoneNumber!!, currentMessage!!)
            }
            
            // Operation failed
            operationCallback?.invoke(false)
            resetState()
        }
    }
    
    /**
     * Reset the state of the service.
     */
    private fun resetState() {
        currentPhoneNumber = null
        currentMessage = null
        currentStep = WhatsAppStep.IDLE
        retryCount = 0
        operationCallback = null
    }
    
    /**
     * Enum representing the steps in the WhatsApp message sending process.
     */
    enum class WhatsAppStep {
        IDLE,
        LAUNCH_APP,
        FIND_CHAT,
        ENTER_MESSAGE,
        SEND_MESSAGE
    }
    
    /**
     * Enum representing the status of the service.
     */
    enum class ServiceStatus {
        ACTIVE,
        INACTIVE
    }
}
