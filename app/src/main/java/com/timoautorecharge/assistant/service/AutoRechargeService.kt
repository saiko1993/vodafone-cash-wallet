package com.timoautorecharge.assistant.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.content.Context
import com.timoautorecharge.assistant.data.model.Log
import com.timoautorecharge.assistant.data.repository.LogRepository
import com.timoautorecharge.assistant.service.TimoAccessibilityService.ServiceStatus
import com.timoautorecharge.assistant.service.WhatsAppService.ServiceStatus as WhatsAppServiceStatus
import com.timoautorecharge.assistant.util.BatteryOptimizationManager
import com.timoautorecharge.assistant.util.NotificationService
import com.timoautorecharge.assistant.util.WorkManagerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Main foreground service that coordinates all other services and ensures they stay running.
 * Monitors service status and provides notifications for service state changes.
 * Optimized for battery efficiency and performance.
 */
class AutoRechargeService : Service() {
    
    companion object {
        private const val HEALTH_CHECK_NORMAL_INTERVAL_MINUTES = 30L
        private const val HEALTH_CHECK_PROBLEM_INTERVAL_MINUTES = 5L
        private const val MAX_CONSECUTIVE_ERRORS = 3
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes max
        private const val WAKE_LOCK_TAG = "TimoAutoRecharge:ServiceWakeLock"
    }
    
    private val notificationService: NotificationService by inject()
    private val logRepository: LogRepository by inject()
    private val whatsAppService: WhatsAppService by inject()
    private val timoAccessibilityService: TimoAccessibilityService by inject()
    private val transactionCoordinator: TransactionCoordinatorService by inject()
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isRunning = AtomicBoolean(false)
    private val consecutiveErrors = AtomicInteger(0)
    private var healthCheckJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private lateinit var batteryOptimizationManager: BatteryOptimizationManager
    private lateinit var workManagerHelper: WorkManagerHelper
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize utility managers
        batteryOptimizationManager = BatteryOptimizationManager(this)
        workManagerHelper = WorkManagerHelper(this)
        
        // Start as a foreground service with notification
        startForeground(
            NotificationService.NOTIFICATION_ID_SERVICE,
            notificationService.createServiceNotification()
        )
        
        // Log service start
        serviceScope.launch {
            logRepository.addLog(
                Log(
                    message = "AutoRechargeService started",
                    timestamp = System.currentTimeMillis(),
                    level = Log.Level.INFO,
                    source = "AutoRechargeService"
                )
            )
        }
        
        // Check battery optimization status
        serviceScope.launch {
            batteryOptimizationManager.checkBatteryOptimizationStatus()
        }
        
        // Set running flag
        isRunning.set(true)
        
        // Monitor service status
        monitorServiceStatus()
        
        // Start health check with adaptive interval
        startAdaptiveHealthCheck()
        
        // Prune completed work to free up resources
        serviceScope.launch {
            workManagerHelper.pruneCompletedWork()
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // If service is already running, just handle the intent
        if (isRunning.get()) {
            intent?.action?.let { action ->
                handleServiceAction(action, intent)
            }
        } else {
            // Service was restarted by the system, reinitialize
            isRunning.set(true)
            monitorServiceStatus()
            startAdaptiveHealthCheck()
        }
        
        // Return sticky to ensure service restarts if killed
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Release wake lock if held
        releaseWakeLock()
        
        // Cancel all coroutines
        serviceScope.cancel()
        
        // Set running flag to false
        isRunning.set(false)
        
        // Log service stop
        serviceScope.launch {
            logRepository.addLog(
                Log(
                    message = "AutoRechargeService stopped",
                    timestamp = System.currentTimeMillis(),
                    level = Log.Level.INFO,
                    source = "AutoRechargeService"
                )
            )
        }
    }
    
    /**
     * Handles service actions from intents.
     */
    private fun handleServiceAction(action: String, intent: Intent) {
        when (action) {
            "PERFORM_HEALTH_CHECK" -> {
                serviceScope.launch {
                    performHealthCheck(true)
                }
            }
            "RESTART_SERVICES" -> {
                serviceScope.launch {
                    restartServices()
                }
            }
        }
    }
    
    /**
     * Monitors the status of all services and provides notifications for state changes.
     * Optimized for battery efficiency by using collectLatest to cancel previous collections.
     */
    private fun monitorServiceStatus() {
        // Monitor WhatsApp service status
        serviceScope.launch {
            try {
                // Use collectLatest to cancel previous collection when a new value is emitted
                whatsAppService.serviceStatus.collectLatest { status ->
                    // Only log status changes to reduce database operations
                    val statusMessage = when (status) {
                        WhatsAppServiceStatus.ACTIVE -> "WhatsApp Service is active"
                        WhatsAppServiceStatus.INACTIVE -> "WhatsApp Service is inactive"
                        WhatsAppServiceStatus.CHECKING -> "Checking WhatsApp Service status"
                    }
                    
                    // Only show notification for inactive status to reduce battery usage
                    if (status == WhatsAppServiceStatus.INACTIVE) {
                        notificationService.showServiceStatusNotification(
                            "WhatsApp Service",
                            statusMessage
                        )
                    }
                    
                    // Log status change
                    logRepository.addLog(
                        Log(
                            message = statusMessage,
                            timestamp = System.currentTimeMillis(),
                            level = if (status == WhatsAppServiceStatus.ACTIVE) Log.Level.INFO else Log.Level.WARNING,
                            source = "AutoRechargeService"
                        )
                    )
                    
                    // If service is inactive, increase health check frequency
                    if (status != WhatsAppServiceStatus.ACTIVE) {
                        restartAdaptiveHealthCheck(true)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error monitoring WhatsApp service status")
                logRepository.addLog(
                    Log(
                        message = "Error monitoring WhatsApp service status: ${e.message}",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.ERROR,
                        source = "AutoRechargeService"
                    )
                )
                
                // Increment consecutive errors
                incrementConsecutiveErrors()
            }
        }
        
        // Monitor Timo service status
        serviceScope.launch {
            try {
                // Use collectLatest to cancel previous collection when a new value is emitted
                timoAccessibilityService.serviceStatus.collectLatest { status ->
                    // Only log status changes to reduce database operations
                    val statusMessage = when (status) {
                        ServiceStatus.ACTIVE -> "Timo Accessibility Service is active"
                        ServiceStatus.INACTIVE -> "Timo Accessibility Service is inactive"
                    }
                    
                    // Only show notification for inactive status to reduce battery usage
                    if (status == ServiceStatus.INACTIVE) {
                        notificationService.showServiceStatusNotification(
                            "Timo Service",
                            statusMessage
                        )
                    }
                    
                    // Log status change
                    logRepository.addLog(
                        Log(
                            message = statusMessage,
                            timestamp = System.currentTimeMillis(),
                            level = if (status == ServiceStatus.ACTIVE) Log.Level.INFO else Log.Level.WARNING,
                            source = "AutoRechargeService"
                        )
                    )
                    
                    // If service is inactive, increase health check frequency
                    if (status != ServiceStatus.ACTIVE) {
                        restartAdaptiveHealthCheck(true)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error monitoring Timo service status")
                logRepository.addLog(
                    Log(
                        message = "Error monitoring Timo service status: ${e.message}",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.ERROR,
                        source = "AutoRechargeService"
                    )
                )
                
                // Increment consecutive errors
                incrementConsecutiveErrors()
            }
        }
    }
    
    /**
     * Starts the adaptive health check process.
     * The interval adapts based on service status.
     */
    private fun startAdaptiveHealthCheck() {
        // Cancel existing job if any
        healthCheckJob?.cancel()
        
        // Start new health check job
        healthCheckJob = serviceScope.launch {
            while (isActive && isRunning.get()) {
                try {
                    // Perform health check
                    val hasProblems = performHealthCheck()
                    
                    // Reset consecutive errors if health check succeeds
                    if (!hasProblems) {
                        consecutiveErrors.set(0)
                    }
                    
                    // Adaptive delay based on service status
                    val delayMinutes = if (hasProblems) {
                        HEALTH_CHECK_PROBLEM_INTERVAL_MINUTES
                    } else {
                        HEALTH_CHECK_NORMAL_INTERVAL_MINUTES
                    }
                    
                    // Delay until next health check
                    delay(delayMinutes * 60 * 1000)
                } catch (e: Exception) {
                    Timber.e(e, "Error in service health check")
                    logRepository.addLog(
                        Log(
                            message = "Error in service health check: ${e.message}",
                            timestamp = System.currentTimeMillis(),
                            level = Log.Level.ERROR,
                            source = "AutoRechargeService"
                        )
                    )
                    
                    // Increment consecutive errors
                    incrementConsecutiveErrors()
                    
                    // Short delay before retry on error
                    delay(HEALTH_CHECK_PROBLEM_INTERVAL_MINUTES * 60 * 1000)
                }
            }
        }
    }
    
    /**
     * Restarts the adaptive health check with optional problem flag.
     * 
     * @param hasProblems Whether to use the shorter problem interval
     */
    private fun restartAdaptiveHealthCheck(hasProblems: Boolean = false) {
        // Cancel existing job
        healthCheckJob?.cancel()
        
        // Start new health check job with appropriate interval
        healthCheckJob = serviceScope.launch {
            // Short delay before first check
            delay(1000)
            
            while (isActive && isRunning.get()) {
                try {
                    // Perform health check
                    val currentProblems = performHealthCheck()
                    
                    // Determine next check interval
                    val delayMinutes = if (currentProblems) {
                        HEALTH_CHECK_PROBLEM_INTERVAL_MINUTES
                    } else {
                        HEALTH_CHECK_NORMAL_INTERVAL_MINUTES
                    }
                    
                    // Delay until next health check
                    delay(delayMinutes * 60 * 1000)
                } catch (e: Exception) {
                    Timber.e(e, "Error in restarted health check")
                    
                    // Short delay before retry on error
                    delay(HEALTH_CHECK_PROBLEM_INTERVAL_MINUTES * 60 * 1000)
                }
            }
        }
    }
    
    /**
     * Performs a health check on all services and logs their status.
     * Optimized for battery efficiency by reducing unnecessary operations.
     * 
     * @param forceCheck Whether to force a check regardless of the normal schedule
     * @return True if any service has problems, false otherwise
     */
    private suspend fun performHealthCheck(forceCheck: Boolean = false): Boolean {
        // Acquire wake lock for health check
        acquireWakeLock()
        
        try {
            // Check service status
            val whatsAppStatus = whatsAppService.getServiceStatus()
            val timoStatus = timoAccessibilityService.getServiceStatus()
            
            // Check if any service has problems
            val hasProblems = whatsAppStatus != WhatsAppServiceStatus.ACTIVE || 
                              timoStatus != ServiceStatus.ACTIVE
            
            // Only log detailed health check if there are problems or it's a forced check
            // This reduces database operations when everything is normal
            if (hasProblems || forceCheck) {
                logRepository.addLog(
                    Log(
                        message = "Service health check: WhatsApp=$whatsAppStatus, Timo=$timoStatus",
                        timestamp = System.currentTimeMillis(),
                        level = if (hasProblems) Log.Level.WARNING else Log.Level.INFO,
                        source = "AutoRechargeService"
                    )
                )
            }
            
            // Check if any service needs attention
            if (hasProblems) {
                // Show notification
                notificationService.showServiceStatusNotification(
                    "Service Health Check",
                    "Some services require attention: WhatsApp=$whatsAppStatus, Timo=$timoStatus"
                )
                
                // Try to recover services
                attemptServiceRecovery(whatsAppStatus, timoStatus)
            }
            
            // Check battery optimization status periodically
            batteryOptimizationManager.checkBatteryOptimizationStatus()
            
            return hasProblems
        } finally {
            // Release wake lock
            releaseWakeLock()
        }
    }
    
    /**
     * Attempts to recover services that are not active.
     * 
     * @param whatsAppStatus The current WhatsApp service status
     * @param timoStatus The current Timo service status
     */
    private suspend fun attemptServiceRecovery(
        whatsAppStatus: WhatsAppServiceStatus,
        timoStatus: ServiceStatus
    ) {
        // Only attempt recovery if consecutive errors are below threshold
        if (consecutiveErrors.get() < MAX_CONSECUTIVE_ERRORS) {
            // Try to recover WhatsApp service
            if (whatsAppStatus != WhatsAppServiceStatus.ACTIVE) {
                withContext(Dispatchers.IO) {
                    try {
                        whatsAppService.initialize()
                        logRepository.addLog(
                            Log(
                                message = "Attempted WhatsApp service recovery",
                                timestamp = System.currentTimeMillis(),
                                level = Log.Level.INFO,
                                source = "AutoRechargeService"
                            )
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Error recovering WhatsApp service")
                    }
                }
            }
            
            // Note: Timo accessibility service can't be directly recovered
            // as it requires user interaction to enable accessibility services
        }
    }
    
    /**
     * Restarts all services.
     * This is a more aggressive recovery method that can be triggered manually.
     */
    private suspend fun restartServices() {
        // Acquire wake lock for service restart
        acquireWakeLock()
        
        try {
            // Log restart attempt
            logRepository.addLog(
                Log(
                    message = "Attempting to restart all services",
                    timestamp = System.currentTimeMillis(),
                    level = Log.Level.WARNING,
                    source = "AutoRechargeService"
                )
            )
            
            // Restart WhatsApp service
            withContext(Dispatchers.IO) {
                try {
                    whatsAppService.initialize()
                } catch (e: Exception) {
                    Timber.e(e, "Error restarting WhatsApp service")
                }
            }
            
            // Reset consecutive errors
            consecutiveErrors.set(0)
            
            // Restart health check with normal interval
            restartAdaptiveHealthCheck(false)
            
            // Log completion
            logRepository.addLog(
                Log(
                    message = "Service restart completed",
                    timestamp = System.currentTimeMillis(),
                    level = Log.Level.INFO,
                    source = "AutoRechargeService"
                )
            )
        } finally {
            // Release wake lock
            releaseWakeLock()
        }
    }
    
    /**
     * Increments the consecutive error counter and takes action if threshold is exceeded.
     */
    private fun incrementConsecutiveErrors() {
        val errors = consecutiveErrors.incrementAndGet()
        
        if (errors >= MAX_CONSECUTIVE_ERRORS) {
            // Log critical error
            serviceScope.launch {
                logRepository.addLog(
                    Log(
                        message = "Critical: $MAX_CONSECUTIVE_ERRORS consecutive errors detected",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.ERROR,
                        source = "AutoRechargeService"
                    )
                )
                
                // Show critical notification
                notificationService.showServiceStatusNotification(
                    "Critical Service Error",
                    "Multiple consecutive errors detected. Manual intervention required."
                )
            }
        }
    }
    
    /**
     * Acquires a wake lock to ensure the service can complete critical operations.
     * Only used for short-duration critical tasks.
     */
    private fun acquireWakeLock() {
        // Release any existing wake lock
        releaseWakeLock()
        
        // Create and acquire a new wake lock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        ).apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS) // Auto-release after timeout
        }
    }
    
    /**
     * Releases the wake lock if it's held.
     */
    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            wakeLock = null
        }
    }
}
