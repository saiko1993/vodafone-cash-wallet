package com.timoautorecharge.assistant.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.timoautorecharge.assistant.data.model.Log
import com.timoautorecharge.assistant.data.repository.LogRepository
import com.timoautorecharge.assistant.service.TimoAccessibilityService.ServiceStatus
import com.timoautorecharge.assistant.service.WhatsAppService.ServiceStatus as WhatsAppServiceStatus
import com.timoautorecharge.assistant.util.NotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * Main foreground service that coordinates all other services and ensures they stay running.
 * Monitors service status and provides notifications for service state changes.
 */
class AutoRechargeService : Service() {
    
    private val notificationService: NotificationService by inject()
    private val logRepository: LogRepository by inject()
    private val whatsAppService: WhatsAppService by inject()
    private val timoAccessibilityService: TimoAccessibilityService by inject()
    private val transactionCoordinator: TransactionCoordinatorService by inject()
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
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
        
        // Monitor service status
        monitorServiceStatus()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle commands if needed
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        
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
     * Monitors the status of all services and provides notifications for state changes.
     */
    private fun monitorServiceStatus() {
        // Monitor WhatsApp service status
        serviceScope.launch {
            try {
                whatsAppService.serviceStatus.collect { status ->
                    val statusMessage = when (status) {
                        WhatsAppServiceStatus.ACTIVE -> "WhatsApp Service is active"
                        WhatsAppServiceStatus.INACTIVE -> "WhatsApp Service is inactive"
                        WhatsAppServiceStatus.CHECKING -> "Checking WhatsApp Service status"
                    }
                    
                    notificationService.showServiceStatusNotification(
                        "WhatsApp Service",
                        statusMessage
                    )
                    
                    logRepository.addLog(
                        Log(
                            message = statusMessage,
                            timestamp = System.currentTimeMillis(),
                            level = Log.Level.INFO,
                            source = "AutoRechargeService"
                        )
                    )
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
            }
        }
        
        // Monitor Timo service status
        serviceScope.launch {
            try {
                timoAccessibilityService.serviceStatus.collect { status ->
                    val statusMessage = when (status) {
                        ServiceStatus.ACTIVE -> "Timo Accessibility Service is active"
                        ServiceStatus.INACTIVE -> "Timo Accessibility Service is inactive"
                    }
                    
                    notificationService.showServiceStatusNotification(
                        "Timo Service",
                        statusMessage
                    )
                    
                    logRepository.addLog(
                        Log(
                            message = statusMessage,
                            timestamp = System.currentTimeMillis(),
                            level = Log.Level.INFO,
                            source = "AutoRechargeService"
                        )
                    )
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
            }
        }
        
        // Periodic health check
        serviceScope.launch {
            while (isActive) {
                try {
                    performHealthCheck()
                    delay(15 * 60 * 1000) // Check every 15 minutes
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
                }
            }
        }
    }
    
    /**
     * Performs a health check on all services and logs their status.
     */
    private suspend fun performHealthCheck() {
        val whatsAppStatus = whatsAppService.getServiceStatus()
        val timoStatus = timoAccessibilityService.getServiceStatus()
        
        logRepository.addLog(
            Log(
                message = "Service health check: WhatsApp=$whatsAppStatus, Timo=$timoStatus",
                timestamp = System.currentTimeMillis(),
                level = Log.Level.INFO,
                source = "AutoRechargeService"
            )
        )
        
        // Check if any service needs attention
        if (whatsAppStatus != WhatsAppServiceStatus.ACTIVE || timoStatus != ServiceStatus.ACTIVE) {
            notificationService.showServiceStatusNotification(
                "Service Health Check",
                "Some services require attention: WhatsApp=$whatsAppStatus, Timo=$timoStatus"
            )
        }
    }
}
