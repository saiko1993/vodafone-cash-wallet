package com.timoautorecharge.assistant.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.timoautorecharge.assistant.R
import com.timoautorecharge.assistant.data.model.Log
import com.timoautorecharge.assistant.data.model.LogType
import com.timoautorecharge.assistant.data.model.UiElementInfo
import com.timoautorecharge.assistant.data.repository.LogRepository
import com.timoautorecharge.assistant.data.repository.SettingsRepository
import com.timoautorecharge.assistant.util.NotificationService
import com.timoautorecharge.assistant.util.TimoNavigationTemplateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.java.KoinJavaComponent.inject
import kotlin.coroutines.resume

/**
 * Accessibility service for interacting with the Timo app.
 * Provides functionality to automate the recharge process using configurable navigation templates.
 */
class TimoAccessibilityService : AccessibilityService() {
    
    private val settingsRepository: SettingsRepository by inject(SettingsRepository::class.java)
    private val logRepository: LogRepository by inject(LogRepository::class.java)
    private val notificationService: NotificationService by inject(NotificationService::class.java)
    private val navigationTemplateManager: TimoNavigationTemplateManager by inject(TimoNavigationTemplateManager::class.java)
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val context = this
    private val handler = Handler(Looper.getMainLooper())
    
    // Callback for recharge operations
    private var rechargeCallback: ((Boolean) -> Unit)? = null
    
    // Current recharge operation state
    private var currentTimoId: String? = null
    private var currentCoinQuantity: Int? = null
    private var currentStep = RechargeStep.IDLE
    private var retryCount = 0
    private var currentAdditionalStepIndex = -1
    
    // Service status
    private val _serviceStatus = MutableStateFlow(ServiceStatus.INACTIVE)
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()
    
    companion object {
        private var captureCallback: ((UiElementInfo?) -> Unit)? = null
        private var captureRect: Rect? = null
        private var captureElementType: UiElementInfo.ElementType? = null
        
        /**
         * Captures an element at the specified screen location.
         *
         * @param rect The screen coordinates of the element to capture
         * @param elementType The type of element to capture (button or input field)
         * @return The captured element info or null if no element was found
         */
        suspend fun captureElementAtLocation(
            rect: Rect,
            elementType: UiElementInfo.ElementType
        ): UiElementInfo? {
            return suspendCancellableCoroutine { continuation ->
                captureCallback = { result ->
                    captureCallback = null
                    captureRect = null
                    captureElementType = null
                    continuation.resume(result)
                }
                captureRect = rect
                captureElementType = elementType
                
                // Set a timeout
                Handler(Looper.getMainLooper()).postDelayed({
                    if (captureCallback != null) {
                        captureCallback?.invoke(null)
                    }
                }, 5000)
            }
        }
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        _serviceStatus.value = ServiceStatus.ACTIVE
        
        scope.launch {
            logRepository.addLog(
                Log(
                    message = "Timo Accessibility Service connected",
                    timestamp = System.currentTimeMillis(),
                    level = Log.Level.INFO,
                    source = "TimoAccessibilityService"
                )
            )
            
            // Notify service status change
            notificationService.notifyServiceStatusChange(
                "Timo Service",
                context.getString(R.string.timo_service_active)
            )
        }
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Check if we're in element capture mode
        if (captureRect != null && captureElementType != null) {
            processElementCapture()
            return
        }
        
        if (currentStep == RechargeStep.IDLE) return
        
        val packageName = event.packageName?.toString() ?: return
        
        scope.launch {
            val settings = settingsRepository.getSettingsSync()
            
            if (packageName == settings.timoPackageName) {
                when (currentStep) {
                    RechargeStep.ADDITIONAL_STEPS -> processAdditionalStep()
                    RechargeStep.FIND_RECHARGE_BUTTON -> findAndClickRechargeButton(settings.rechargeButtonId)
                    RechargeStep.ENTER_TIMO_ID -> enterTimoId(settings.timoIdInputId, currentTimoId ?: return@launch)
                    RechargeStep.ENTER_COIN_QUANTITY -> enterCoinQuantity(settings.coinQuantityInputId, currentCoinQuantity ?: return@launch)
                    RechargeStep.CLICK_CONFIRM -> clickConfirmButton(settings.confirmButtonId)
                    else -> {}
                }
            }
        }
    }
    
    override fun onInterrupt() {
        _serviceStatus.value = ServiceStatus.INACTIVE
        
        scope.launch {
            logRepository.addLog(
                Log(
                    message = "Timo Accessibility Service interrupted",
                    timestamp = System.currentTimeMillis(),
                    level = Log.Level.WARNING,
                    source = "TimoAccessibilityService",
                    type = LogType.ACCESSIBILITY
                )
            )
            
            // Notify service status change
            notificationService.notifyServiceStatusChange(
                "Timo Service",
                context.getString(R.string.timo_service_inactive)
            )
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        _serviceStatus.value = ServiceStatus.INACTIVE
        
        scope.launch {
            // Notify service status change
            notificationService.notifyServiceStatusChange(
                "Timo Service",
                context.getString(R.string.timo_service_inactive)
            )
        }
    }
    
    /**
     * Perform a recharge operation in the Timo app.
     * 
     * @param timoId The Timo ID to recharge
     * @param coinQuantity The quantity of coins to recharge
     * @param callback Callback to be called when the operation completes
     */
    fun performRecharge(timoId: String, coinQuantity: Int, callback: (Boolean) -> Unit) {
        scope.launch {
            try {
                if (_serviceStatus.value != ServiceStatus.ACTIVE) {
                    logRepository.addLog(
                        Log(
                            message = "Cannot perform recharge: Timo Accessibility Service is not active",
                            timestamp = System.currentTimeMillis(),
                            level = Log.Level.ERROR,
                            source = "TimoAccessibilityService",
                            type = LogType.ACCESSIBILITY
                        )
                    )
                    callback(false)
                    return@launch
                }
                
                val settings = settingsRepository.getSettingsSync()
                val template = navigationTemplateManager.getCurrentNavigationSettings()
                
                // Reset state
                currentTimoId = timoId
                currentCoinQuantity = coinQuantity
                retryCount = 0
                rechargeCallback = callback
                
                // Check if there are additional steps to perform
                if (template.additionalSteps.isNotEmpty()) {
                    currentAdditionalStepIndex = 0
                    currentStep = RechargeStep.ADDITIONAL_STEPS
                } else {
                    currentStep = RechargeStep.FIND_RECHARGE_BUTTON
                }
                
                // Launch Timo app
                val intent = packageManager.getLaunchIntentForPackage(settings.timoPackageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    
                    // Notify admin about recharge start
                    notificationService.notifyRechargeStarted(timoId, coinQuantity)
                    
                    logRepository.addLog(
                        Log(
                            message = "Launched Timo app for recharge: ID=$timoId, Coins=$coinQuantity",
                            timestamp = System.currentTimeMillis(),
                            level = Log.Level.INFO,
                            source = "TimoAccessibilityService"
                        )
                    )
                } else {
                    logRepository.addLog(
                        Log(
                            message = "Failed to launch Timo app: Package ${settings.timoPackageName} not found",
                            timestamp = System.currentTimeMillis(),
                            level = Log.Level.ERROR,
                            source = "TimoAccessibilityService"
                        )
                    )
                    rechargeCallback?.invoke(false)
                    resetState()
                }
            } catch (e: Exception) {
                logRepository.addLog(
                    Log(
                        message = "Error starting recharge: ${e.message}",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.ERROR,
                        source = "TimoAccessibilityService"
                    )
                )
                rechargeCallback?.invoke(false)
                resetState()
            }
        }
    }
    
    /**
     * Process an additional navigation step from the template.
     */
    private fun processAdditionalStep() {
        scope.launch {
            val template = navigationTemplateManager.getCurrentNavigationSettings()
            
            if (currentAdditionalStepIndex >= 0 && currentAdditionalStepIndex < template.additionalSteps.size) {
                val step = template.additionalSteps[currentAdditionalStepIndex]
                
                when (step.type) {
                    "click" -> {
                        val rootNode = rootInActiveWindow ?: return@launch
                        val node = findNodeById(rootNode, step.targetId)
                        
                        if (node != null && node.isClickable) {
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            
                            logRepository.addLog(
                                Log(
                                    message = "Performed additional step: ${step.name}",
                                    timestamp = System.currentTimeMillis(),
                                    level = Log.Level.INFO,
                                    source = "TimoAccessibilityService"
                                )
                            )
                            
                            // Move to next step after delay
                            handler.postDelayed({
                                currentAdditionalStepIndex++
                                if (currentAdditionalStepIndex >= template.additionalSteps.size) {
                                    currentStep = RechargeStep.FIND_RECHARGE_BUTTON
                                }
                            }, step.delayAfterMs)
                        } else {
                            handleNavigationFailure("Could not find or click element for step: ${step.name}")
                        }
                    }
                    "input" -> {
                        val rootNode = rootInActiveWindow ?: return@launch
                        val input = findNodeById(rootNode, step.targetId)
                        
                        if (input != null) {
                            // Clear existing text
                            input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, null)
                            
                            // Set new text
                            val arguments = Bundle()
                            arguments.putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, 
                                step.value ?: ""
                            )
                            input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                            
                            logRepository.addLog(
                                Log(
                                    message = "Performed additional step: ${step.name}",
                                    timestamp = System.currentTimeMillis(),
                                    level = Log.Level.INFO,
                                    source = "TimoAccessibilityService"
                                )
                            )
                            
                            // Move to next step after delay
                            handler.postDelayed({
                                currentAdditionalStepIndex++
                                if (currentAdditionalStepIndex >= template.additionalSteps.size) {
                                    currentStep = RechargeStep.FIND_RECHARGE_BUTTON
                                }
                            }, step.delayAfterMs)
                        } else {
                            handleNavigationFailure("Could not find input field for step: ${step.name}")
                        }
                    }
                    "wait" -> {
                        logRepository.addLog(
                            Log(
                                message = "Performing wait step: ${step.name}",
                                timestamp = System.currentTimeMillis(),
                                level = Log.Level.INFO,
                                source = "TimoAccessibilityService"
                            )
                        )
                        
                        // Just wait for the specified delay
                        handler.postDelayed({
                            currentAdditionalStepIndex++
                            if (currentAdditionalStepIndex >= template.additionalSteps.size) {
                                currentStep = RechargeStep.FIND_RECHARGE_BUTTON
                            }
                        }, step.delayAfterMs)
                    }
                    else -> {
                        logRepository.addLog(
                            Log(
                                message = "Unknown step type: ${step.type} for step: ${step.name}",
                                timestamp = System.currentTimeMillis(),
                                level = Log.Level.WARNING,
                                source = "TimoAccessibilityService"
                            )
                        )
                        
                        // Skip this step
                        currentAdditionalStepIndex++
                        if (currentAdditionalStepIndex >= template.additionalSteps.size) {
                            currentStep = RechargeStep.FIND_RECHARGE_BUTTON
                        }
                    }
                }
            } else {
                // Move to main recharge flow
                currentStep = RechargeStep.FIND_RECHARGE_BUTTON
            }
        }
    }
    
    /**
     * Find and click the recharge button in the Timo app.
     * 
     * @param buttonId The ID of the recharge button
     */
    private fun findAndClickRechargeButton(buttonId: String) {
        val rootNode = rootInActiveWindow ?: return
        val button = findNodeById(rootNode, buttonId)
        
        if (button != null && button.isClickable) {
            button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            
            scope.launch {
                val settings = settingsRepository.getSettingsSync()
                handler.postDelayed({
                    currentStep = RechargeStep.ENTER_TIMO_ID
                }, settings.navigationDelayMs)
            }
            
            scope.launch {
                logRepository.addLog(
                    Log(
                        message = "Clicked recharge button",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.INFO,
                        source = "TimoAccessibilityService"
                    )
                )
            }
        } else {
            handleNavigationFailure("Could not find or click recharge button")
        }
    }
    
    /**
     * Enter the Timo ID in the input field.
     * 
     * @param inputId The ID of the Timo ID input field
     * @param timoId The Timo ID to enter
     */
    private fun enterTimoId(inputId: String, timoId: String) {
        val rootNode = rootInActiveWindow ?: return
        val input = findNodeById(rootNode, inputId)
        
        if (input != null) {
            // Clear existing text
            input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, null)
            
            // Set new text
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, timoId)
            input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            
            scope.launch {
                val settings = settingsRepository.getSettingsSync()
                handler.postDelayed({
                    currentStep = RechargeStep.ENTER_COIN_QUANTITY
                }, settings.navigationDelayMs)
            }
            
            scope.launch {
                logRepository.addLog(
                    Log(
                        message = "Entered Timo ID: $timoId",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.INFO,
                        source = "TimoAccessibilityService"
                    )
                )
            }
        } else {
            handleNavigationFailure("Could not find Timo ID input field")
        }
    }
    
    /**
     * Enter the coin quantity in the input field.
     * 
     * @param inputId The ID of the coin quantity input field
     * @param coinQuantity The coin quantity to enter
     */
    private fun enterCoinQuantity(inputId: String, coinQuantity: Int) {
        val rootNode = rootInActiveWindow ?: return
        val input = findNodeById(rootNode, inputId)
        
        if (input != null) {
            // Clear existing text
            input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, null)
            
            // Set new text
            val arguments = Bundle()
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, coinQuantity.toString())
            input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            
            scope.launch {
                val settings = settingsRepository.getSettingsSync()
                handler.postDelayed({
                    currentStep = RechargeStep.CLICK_CONFIRM
                }, settings.navigationDelayMs)
            }
            
            scope.launch {
                logRepository.addLog(
                    Log(
                        message = "Entered coin quantity: $coinQuantity",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.INFO,
                        source = "TimoAccessibilityService"
                    )
                )
            }
        } else {
            handleNavigationFailure("Could not find coin quantity input field")
        }
    }
    
    /**
     * Click the confirm button to complete the recharge.
     * 
     * @param buttonId The ID of the confirm button
     */
    private fun clickConfirmButton(buttonId: String) {
        val rootNode = rootInActiveWindow ?: return
        val button = findNodeById(rootNode, buttonId)
        
        if (button != null && button.isClickable) {
            button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            
            scope.launch {
                logRepository.addLog(
                    Log(
                        message = "Clicked confirm button, recharge completed",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.INFO,
                        source = "TimoAccessibilityService"
                    )
                )
            }
            
            // Notify admin about successful recharge
            val timoId = currentTimoId
            val coinQuantity = currentCoinQuantity
            if (timoId != null && coinQuantity != null) {
                notificationService.notifyRechargeCompleted(timoId, coinQuantity)
            }
            
            // Recharge completed successfully
            rechargeCallback?.invoke(true)
            resetState()
        } else {
            handleNavigationFailure("Could not find or click confirm button")
        }
    }
    
    /**
     * Find a node by its ID.
     * 
     * @param root The root node to search from
     * @param viewId The ID of the node to find
     * @return The node with the specified ID, or null if not found
     */
    private fun findNodeById(root: AccessibilityNodeInfo, viewId: String): AccessibilityNodeInfo? {
        try {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            return if (nodes.isNotEmpty()) nodes[0] else null
        } catch (e: Exception) {
            scope.launch {
                logRepository.addLog(
                    Log(
                        message = "Error finding node by ID: ${e.message}",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.ERROR,
                        source = "TimoAccessibilityService"
                    )
                )
            }
            return null
        }
    }
    
    /**
     * Find a node by its text content.
     * 
     * @param root The root node to search from
     * @param text The text content to search for
     * @return The node with the specified text content, or null if not found
     */
    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (root.text?.toString()?.contains(text) == true) {
            return root
        }
        
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findNodeByText(child, text)
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
        scope.launch {
            val settings = settingsRepository.getSettingsSync()
            
            if (retryCount < settings.maxNavigationRetries) {
                retryCount++
                
                logRepository.addLog(
                    Log(
                        message = "$errorMessage, retrying (attempt $retryCount of ${settings.maxNavigationRetries})",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.WARNING,
                        source = "TimoAccessibilityService"
                    )
                )
                
                // Retry after delay
                handler.postDelayed({
                    // Keep the same step for retry
                }, settings.navigationDelayMs)
            } else {
                logRepository.addLog(
                    Log(
                        message = "$errorMessage, max retries exceeded",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.ERROR,
                        source = "TimoAccessibilityService"
                    )
                )
                
                // Notify admin about recharge failure
                val timoId = currentTimoId
                val coinQuantity = currentCoinQuantity
                if (timoId != null && coinQuantity != null) {
                    notificationService.notifyRechargeFailed(timoId, coinQuantity, errorMessage)
                }
                
                // Recharge failed
                rechargeCallback?.invoke(false)
                resetState()
            }
        }
    }
    
    /**
     * Reset the state of the service.
     */
    private fun resetState() {
        currentTimoId = null
        currentCoinQuantity = null
        currentStep = RechargeStep.IDLE
        currentAdditionalStepIndex = -1
        retryCount = 0
        rechargeCallback = null
    }
    
    /**
     * Enum representing the steps in the recharge process.
     */
    enum class RechargeStep {
        IDLE,
        LAUNCH_APP,
        ADDITIONAL_STEPS,
        FIND_RECHARGE_BUTTON,
        ENTER_TIMO_ID,
        ENTER_COIN_QUANTITY,
        CLICK_CONFIRM
    }
    
    /**
     * Enum representing the status of the service.
     */
    enum class ServiceStatus {
        ACTIVE,
        INACTIVE
    }
    
    /**
     * Process element capture request.
     * Searches for UI elements at the specified screen location.
     */
    private fun processElementCapture() {
        val rect = captureRect ?: return
        val elementType = captureElementType ?: return
        val callback = captureCallback ?: return
        
        val rootNode = rootInActiveWindow ?: return
        val node = findNodeAtLocation(rootNode, rect)
        
        if (node != null) {
            val elementInfo = createUiElementInfo(node, elementType)
            callback(elementInfo)
            
            scope.launch {
                logRepository.addLog(
                    Log(
                        message = "Captured UI element: ${elementInfo.elementId} (${elementInfo.elementType})",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.INFO,
                        source = "TimoAccessibilityService"
                    )
                )
            }
        }
    }
    
    /**
     * Find a node at the specified screen location.
     *
     * @param root The root node to search from
     * @param rect The screen coordinates to search within
     * @return The node at the specified location, or null if not found
     */
    private fun findNodeAtLocation(root: AccessibilityNodeInfo, rect: Rect): AccessibilityNodeInfo? {
        try {
            val nodeRect = Rect()
            root.getBoundsInScreen(nodeRect)
            
            // Check if this node is within the selection rectangle
            if (rect.contains(nodeRect) || nodeRect.contains(rect)) {
                return root
            }
            
            // Check child nodes
            for (i in 0 until root.childCount) {
                val child = root.getChild(i) ?: continue
                val result = findNodeAtLocation(child, rect)
                if (result != null) {
                    return result
                }
            }
            
            return null
        } catch (e: Exception) {
            scope.launch {
                logRepository.addLog(
                    Log(
                        message = "Error finding node at location: ${e.message}",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.ERROR,
                        source = "TimoAccessibilityService"
                    )
                )
            }
            return null
        }
    }
    
    /**
     * Create a UiElementInfo object from an AccessibilityNodeInfo.
     *
     * @param node The node to create the element info from
     * @param elementType The type of element (button or input field)
     * @return The created UiElementInfo object
     */
    private fun createUiElementInfo(node: AccessibilityNodeInfo, elementType: UiElementInfo.ElementType): UiElementInfo {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        return UiElementInfo(
            elementId = node.viewIdResourceName ?: "",
            packageName = node.packageName?.toString() ?: "",
            elementType = elementType,
            contentDescription = node.contentDescription?.toString() ?: "",
            text = node.text?.toString() ?: "",
            bounds = bounds,
            className = node.className?.toString() ?: ""
        )
    }
}
