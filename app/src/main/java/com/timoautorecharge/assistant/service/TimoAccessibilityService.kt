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
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class TimoAccessibilityService : AccessibilityService() {
    
    private val settingsRepository: SettingsRepository by inject(SettingsRepository::class.java)
    private val logRepository: LogRepository by inject(LogRepository::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    
    // Callback for recharge operations
    private var rechargeCallback: ((Boolean) -> Unit)? = null
    
    // Current recharge operation state
    private var currentTimoId: String? = null
    private var currentCoinQuantity: Int? = null
    private var currentStep = RechargeStep.IDLE
    private var retryCount = 0
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        logRepository.logInfo(
            LogType.ACCESSIBILITY,
            "Timo Accessibility Service connected"
        )
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (currentStep == RechargeStep.IDLE) return
        
        val packageName = event.packageName?.toString() ?: return
        
        scope.launch {
            val settings = settingsRepository.getSettingsSync()
            
            if (packageName == settings.timoPackageName) {
                when (currentStep) {
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
        logRepository.logWarning(
            LogType.ACCESSIBILITY,
            "Timo Accessibility Service interrupted"
        )
    }
    
    fun performRecharge(timoId: String, coinQuantity: Int, callback: (Boolean) -> Unit) {
        scope.launch {
            try {
                val settings = settingsRepository.getSettingsSync()
                
                // Reset state
                currentTimoId = timoId
                currentCoinQuantity = coinQuantity
                currentStep = RechargeStep.LAUNCH_APP
                retryCount = 0
                rechargeCallback = callback
                
                // Launch Timo app
                val intent = packageManager.getLaunchIntentForPackage(settings.timoPackageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    
                    // Move to next step after delay
                    handler.postDelayed({
                        currentStep = RechargeStep.FIND_RECHARGE_BUTTON
                    }, settings.navigationDelayMs)
                    
                    logRepository.logInfo(
                        LogType.ACCESSIBILITY,
                        "Launched Timo app for recharge: ID=$timoId, Coins=$coinQuantity"
                    )
                } else {
                    logRepository.logError(
                        LogType.ACCESSIBILITY,
                        "Failed to launch Timo app: Package ${settings.timoPackageName} not found"
                    )
                    rechargeCallback?.invoke(false)
                    resetState()
                }
            } catch (e: Exception) {
                logRepository.logError(
                    LogType.ACCESSIBILITY,
                    "Error starting recharge: ${e.message}"
                )
                rechargeCallback?.invoke(false)
                resetState()
            }
        }
    }
    
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
            
            logRepository.logInfo(
                LogType.ACCESSIBILITY,
                "Clicked recharge button"
            )
        } else {
            handleNavigationFailure("Could not find or click recharge button")
        }
    }
    
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
            
            logRepository.logInfo(
                LogType.ACCESSIBILITY,
                "Entered Timo ID: $timoId"
            )
        } else {
            handleNavigationFailure("Could not find Timo ID input field")
        }
    }
    
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
            
            logRepository.logInfo(
                LogType.ACCESSIBILITY,
                "Entered coin quantity: $coinQuantity"
            )
        } else {
            handleNavigationFailure("Could not find coin quantity input field")
        }
    }
    
    private fun clickConfirmButton(buttonId: String) {
        val rootNode = rootInActiveWindow ?: return
        val button = findNodeById(rootNode, buttonId)
        
        if (button != null && button.isClickable) {
            button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            
            logRepository.logInfo(
                LogType.ACCESSIBILITY,
                "Clicked confirm button, recharge completed"
            )
            
            // Recharge completed successfully
            rechargeCallback?.invoke(true)
            resetState()
        } else {
            handleNavigationFailure("Could not find or click confirm button")
        }
    }
    
    private fun findNodeById(root: AccessibilityNodeInfo, viewId: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        return if (nodes.isNotEmpty()) nodes[0] else null
    }
    
    private fun handleNavigationFailure(errorMessage: String) {
        scope.launch {
            val settings = settingsRepository.getSettingsSync()
            
            if (retryCount < settings.maxNavigationRetries) {
                retryCount++
                
                logRepository.logWarning(
                    LogType.ACCESSIBILITY,
                    "$errorMessage, retrying (attempt $retryCount of ${settings.maxNavigationRetries})"
                )
                
                // Retry after delay
                handler.postDelayed({
                    // Keep the same step for retry
                }, settings.navigationDelayMs)
            } else {
                logRepository.logError(
                    LogType.ACCESSIBILITY,
                    "$errorMessage, max retries exceeded"
                )
                
                // Recharge failed
                rechargeCallback?.invoke(false)
                resetState()
            }
        }
    }
    
    private fun resetState() {
        currentTimoId = null
        currentCoinQuantity = null
        currentStep = RechargeStep.IDLE
        retryCount = 0
        rechargeCallback = null
    }
    
    enum class RechargeStep {
        IDLE,
        LAUNCH_APP,
        FIND_RECHARGE_BUTTON,
        ENTER_TIMO_ID,
        ENTER_COIN_QUANTITY,
        CLICK_CONFIRM
    }
}
