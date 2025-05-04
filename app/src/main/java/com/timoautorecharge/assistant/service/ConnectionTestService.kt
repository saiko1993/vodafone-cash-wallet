package com.timoautorecharge.assistant.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.timoautorecharge.assistant.data.model.Log
import com.timoautorecharge.assistant.data.repository.LogRepository
import com.timoautorecharge.assistant.data.repository.SettingsRepository
import com.timoautorecharge.assistant.util.NotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Service for testing connections to various services.
 */
class ConnectionTestService(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val logRepository: LogRepository,
    private val notificationService: NotificationService
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Connection status flows
    private val _internetStatus = MutableStateFlow(ConnectionStatus.UNKNOWN)
    val internetStatus: StateFlow<ConnectionStatus> = _internetStatus.asStateFlow()
    
    private val _whatsAppStatus = MutableStateFlow(ConnectionStatus.UNKNOWN)
    val whatsAppStatus: StateFlow<ConnectionStatus> = _whatsAppStatus.asStateFlow()
    
    private val _timoStatus = MutableStateFlow(ConnectionStatus.UNKNOWN)
    val timoStatus: StateFlow<ConnectionStatus> = _timoStatus.asStateFlow()
    
    private val _smsStatus = MutableStateFlow(ConnectionStatus.UNKNOWN)
    val smsStatus: StateFlow<ConnectionStatus> = _smsStatus.asStateFlow()
    
    init {
        // Start periodic connection tests
        scope.launch {
            while (true) {
                testAllConnections()
                delay(60000) // Test every minute
            }
        }
    }
    
    /**
     * Test all connections.
     */
    suspend fun testAllConnections() {
        testInternetConnection()
        testWhatsAppConnection()
        testTimoConnection()
        testSmsConnection()
    }
    
    /**
     * Test internet connection.
     */
    private suspend fun testInternetConnection() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val isConnected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities != null && (
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            )
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo != null && networkInfo.isConnected
        }
        
        if (isConnected) {
            // Test actual internet connectivity by pinging a reliable server
            try {
                val url = URL("https://www.google.com")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.connect()
                
                if (connection.responseCode == 200) {
                    updateConnectionStatus(_internetStatus, ConnectionStatus.CONNECTED)
                } else {
                    updateConnectionStatus(_internetStatus, ConnectionStatus.LIMITED)
                }
                
                connection.disconnect()
            } catch (e: IOException) {
                updateConnectionStatus(_internetStatus, ConnectionStatus.ERROR)
                logRepository.addLog(
                    Log(
                        message = "Internet connection test failed: ${e.message}",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.ERROR,
                        source = "ConnectionTestService"
                    )
                )
            }
        } else {
            updateConnectionStatus(_internetStatus, ConnectionStatus.DISCONNECTED)
        }
    }
    
    /**
     * Test WhatsApp connection.
     */
    private suspend fun testWhatsAppConnection() {
        // Check if WhatsApp is installed
        val whatsAppPackageName = "com.whatsapp"
        val isWhatsAppInstalled = try {
            context.packageManager.getPackageInfo(whatsAppPackageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        
        if (!isWhatsAppInstalled) {
            updateConnectionStatus(_whatsAppStatus, ConnectionStatus.NOT_AVAILABLE)
            return
        }
        
        // Check if WhatsApp accessibility service is active
        val whatsAppAccessibilityService: WhatsAppAccessibilityService? = try {
            WhatsAppAccessibilityService()
        } catch (e: Exception) {
            null
        }
        
        val accessibilityStatus = whatsAppAccessibilityService?.serviceStatus?.value
        
        if (accessibilityStatus == WhatsAppAccessibilityService.ServiceStatus.ACTIVE) {
            updateConnectionStatus(_whatsAppStatus, ConnectionStatus.CONNECTED)
        } else {
            updateConnectionStatus(_whatsAppStatus, ConnectionStatus.DISCONNECTED)
        }
    }
    
    /**
     * Test Timo connection.
     */
    private suspend fun testTimoConnection() {
        val settings = settingsRepository.getSettings().first()
        val timoPackageName = settings.timoPackageName
        
        if (timoPackageName.isBlank()) {
            updateConnectionStatus(_timoStatus, ConnectionStatus.NOT_CONFIGURED)
            return
        }
        
        // Check if Timo is installed
        val isTimoInstalled = try {
            context.packageManager.getPackageInfo(timoPackageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        
        if (!isTimoInstalled) {
            updateConnectionStatus(_timoStatus, ConnectionStatus.NOT_AVAILABLE)
            return
        }
        
        // Check if Timo accessibility service is active
        val timoAccessibilityService: TimoAccessibilityService? = try {
            TimoAccessibilityService()
        } catch (e: Exception) {
            null
        }
        
        val accessibilityStatus = timoAccessibilityService?.serviceStatus?.value
        
        if (accessibilityStatus == TimoAccessibilityService.ServiceStatus.ACTIVE) {
            updateConnectionStatus(_timoStatus, ConnectionStatus.CONNECTED)
        } else {
            updateConnectionStatus(_timoStatus, ConnectionStatus.DISCONNECTED)
        }
    }
    
    /**
     * Test SMS connection.
     */
    private suspend fun testSmsConnection() {
        // Check if app has SMS permissions
        val hasSmsPermission = context.checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) == 
            PackageManager.PERMISSION_GRANTED
        
        if (!hasSmsPermission) {
            updateConnectionStatus(_smsStatus, ConnectionStatus.NOT_CONFIGURED)
            return
        }
        
        // SMS is a system service, so if we have permission, we should be able to use it
        updateConnectionStatus(_smsStatus, ConnectionStatus.CONNECTED)
    }
    
    /**
     * Update connection status and notify if changed.
     */
    private suspend fun updateConnectionStatus(
        statusFlow: MutableStateFlow<ConnectionStatus>,
        newStatus: ConnectionStatus
    ) {
        val oldStatus = statusFlow.value
        statusFlow.value = newStatus
        
        if (oldStatus != newStatus) {
            val serviceName = when (statusFlow) {
                _internetStatus -> "Internet"
                _whatsAppStatus -> "WhatsApp"
                _timoStatus -> "Timo"
                _smsStatus -> "SMS"
                else -> "Unknown"
            }
            
            logRepository.addLog(
                Log(
                    message = "$serviceName connection status changed from $oldStatus to $newStatus",
                    timestamp = System.currentTimeMillis(),
                    level = Log.Level.INFO,
                    source = "ConnectionTestService"
                )
            )
            
            // Notify about status change
            notificationService.showSmartNotification(
                title = "$serviceName Connection",
                message = "Connection status changed to $newStatus",
                category = NotificationService.Companion.NotificationCategory.CONNECTION,
                priority = when (newStatus) {
                    ConnectionStatus.ERROR, 
                    ConnectionStatus.DISCONNECTED -> NotificationService.Companion.NotificationPriority.HIGH
                    else -> NotificationService.Companion.NotificationPriority.NORMAL
                },
                key = "connection_$serviceName"
            )
        }
    }
    
    /**
     * Enum representing connection status.
     */
    enum class ConnectionStatus {
        CONNECTED,
        DISCONNECTED,
        LIMITED,
        ERROR,
        NOT_AVAILABLE,
        NOT_CONFIGURED,
        UNKNOWN
    }
}
