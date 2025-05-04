package com.timoautorecharge.assistant.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.timoautorecharge.assistant.R
import com.timoautorecharge.assistant.data.model.Transaction
import com.timoautorecharge.assistant.data.repository.SettingsRepository
import com.timoautorecharge.assistant.ui.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Service responsible for creating and showing notifications.
 * Handles service status notifications and transaction event notifications.
 * Supports smart notifications with categorization, priority levels, and grouping.
 */
class NotificationService(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        const val CHANNEL_ID_SERVICE = "auto_recharge_service"
        const val CHANNEL_ID_TRANSACTIONS = "transactions"
        const val CHANNEL_ID_SERVICE_STATUS = "service_status"
        const val CHANNEL_ID_CRITICAL = "critical_alerts"
        const val CHANNEL_ID_CONNECTION = "connection_status"
        const val NOTIFICATION_ID_SERVICE = 1
        
        private var notificationId = 100
        
        // Enum for notification priority
        enum class NotificationPriority {
            LOW,
            NORMAL,
            HIGH,
            CRITICAL
        }
        
        // Enum for notification categories
        enum class NotificationCategory {
            SERVICE_STATUS,
            TRANSACTION,
            PAYMENT,
            RECHARGE,
            CONNECTION,
            SYSTEM
        }
    }
    
    // Add these maps for notification tracking
    private val activeNotifications = mutableMapOf<String, Int>() // key -> notificationId
    private val notificationGroups = mutableMapOf<NotificationCategory, MutableList<Int>>() // category -> list of notificationIds
    
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_description)
            }
            
            val transactionsChannel = NotificationChannel(
                CHANNEL_ID_TRANSACTIONS,
                "Transactions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for transaction events"
            }
            
            val serviceStatusChannel = NotificationChannel(
                CHANNEL_ID_SERVICE_STATUS,
                "Service Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for service status changes"
            }
            
            val criticalChannel = NotificationChannel(
                CHANNEL_ID_CRITICAL,
                "Critical Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority alerts requiring immediate attention"
                enableVibration(true)
                enableLights(true)
            }
            
            val connectionChannel = NotificationChannel(
                CHANNEL_ID_CONNECTION,
                "Connection Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for connection status"
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannels(
                listOf(
                    serviceChannel, 
                    transactionsChannel, 
                    serviceStatusChannel,
                    criticalChannel,
                    connectionChannel
                )
            )
        }
    }
    
    /**
     * Creates a foreground service notification.
     * 
     * @return The notification for the foreground service
     */
    fun createServiceNotification(): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .build()
    }
    
    /**
     * Shows a notification for a service status change.
     * 
     * @param serviceName The name of the service
     * @param statusMessage The status message to display
     */
    fun showServiceStatusNotification(serviceName: String, statusMessage: String) {
        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID_SERVICE_STATUS)
                .setContentTitle("$serviceName Status")
                .setContentText(statusMessage)
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            
            NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
        } catch (e: Exception) {
            // Log error but don't crash if notification permission is missing
        }
    }
    
    /**
     * Notifies about a service status change.
     * 
     * @param serviceName The name of the service
     * @param statusMessage The status message to display
     */
    suspend fun notifyServiceStatusChange(serviceName: String, statusMessage: String) {
        showServiceStatusNotification(serviceName, statusMessage)
    }
    
    /**
     * Shows a notification for a payment verification.
     * 
     * @param transaction The transaction that was verified
     */
    fun showPaymentVerifiedNotification(transaction: Transaction) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TRANSACTIONS)
            .setContentTitle(context.getString(R.string.payment_verified_notification))
            .setContentText(context.getString(
                R.string.payment_verified_message,
                transaction.requestAmount.toString(),
                transaction.senderNumber
            ))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
    }
    
    /**
     * Shows a notification for a payment timeout.
     * 
     * @param transaction The transaction that timed out
     */
    fun showPaymentTimeoutNotification(transaction: Transaction) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TRANSACTIONS)
            .setContentTitle(context.getString(R.string.payment_timeout_notification))
            .setContentText(context.getString(
                R.string.payment_timeout_message,
                transaction.timoId
            ))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
    }
    
    /**
     * Shows a notification for an unmatched payment.
     * 
     * @param amount The payment amount
     * @param senderNumber The sender's phone number
     */
    fun showUnmatchedPaymentNotification(amount: Double, senderNumber: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TRANSACTIONS)
            .setContentTitle("Unmatched Payment")
            .setContentText("Received payment of $amount EGP from $senderNumber with no matching request")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
    }
    
    /**
     * Notifies about an unmatched payment.
     * 
     * @param amount The payment amount
     * @param senderNumber The sender's phone number
     */
    fun notifyUnmatchedPayment(amount: Double, senderNumber: String) {
        showUnmatchedPaymentNotification(amount, senderNumber)
    }
    
    /**
     * Notifies about a payment verification.
     * 
     * @param transaction The transaction that was verified
     */
    fun notifyPaymentVerified(transaction: Transaction) {
        showPaymentVerifiedNotification(transaction)
    }
    
    /**
     * Shows a notification for manual intervention required.
     * 
     * @param transaction The transaction requiring intervention
     * @param reason The reason for manual intervention
     */
    fun showManualInterventionRequiredNotification(transaction: Transaction, reason: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TRANSACTIONS)
            .setContentTitle("Manual Intervention Required")
            .setContentText("Transaction for Timo ID ${transaction.timoId} requires manual intervention: $reason")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
    }
    
    /**
     * Shows a notification for a recharge start.
     * 
     * @param transaction The transaction being recharged
     */
    fun showRechargeStartedNotification(transaction: Transaction) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TRANSACTIONS)
            .setContentTitle("Recharge Started")
            .setContentText(context.getString(
                R.string.recharge_started,
                transaction.coinQuantity,
                transaction.timoId
            ))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
    }
    
    /**
     * Notifies about a recharge start.
     * 
     * @param timoId The Timo ID being recharged
     * @param coinQuantity The quantity of coins being recharged
     */
    fun notifyRechargeStarted(timoId: String, coinQuantity: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TRANSACTIONS)
            .setContentTitle("Recharge Started")
            .setContentText("Starting recharge of $coinQuantity coins for Timo ID $timoId")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
    }
    
    /**
     * Shows a notification for a successful recharge.
     * 
     * @param transaction The transaction that was recharged
     */
    fun showRechargeSuccessNotification(transaction: Transaction) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TRANSACTIONS)
            .setContentTitle(context.getString(R.string.recharge_success_notification))
            .setContentText(context.getString(
                R.string.recharge_success_message,
                transaction.coinQuantity,
                transaction.timoId
            ))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
    }
    
    /**
     * Notifies about a completed recharge.
     * 
     * @param timoId The Timo ID that was recharged
     * @param coinQuantity The quantity of coins that was recharged
     */
    fun notifyRechargeCompleted(timoId: String, coinQuantity: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TRANSACTIONS)
            .setContentTitle("Recharge Completed")
            .setContentText("Successfully recharged $coinQuantity coins for Timo ID $timoId")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
    }
    
    /**
     * Shows a notification for a failed recharge.
     * 
     * @param transaction The transaction that failed
     * @param errorMessage The error message
     */
    fun showRechargeFailedNotification(transaction: Transaction, errorMessage: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TRANSACTIONS)
            .setContentTitle(context.getString(R.string.recharge_failed_notification))
            .setContentText(context.getString(
                R.string.recharge_failed_message,
                transaction.timoId,
                errorMessage
            ))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
    }
    
    /**
     * Notifies about a failed recharge.
     * 
     * @param timoId The Timo ID that failed to recharge
     * @param coinQuantity The quantity of coins that failed to recharge
     * @param errorMessage The error message
     */
    fun notifyRechargeFailed(timoId: String, coinQuantity: Int, errorMessage: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TRANSACTIONS)
            .setContentTitle("Recharge Failed")
            .setContentText("Failed to recharge $coinQuantity coins for Timo ID $timoId: $errorMessage")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
    }
    
    /**
     * Shows a notification for a transaction requiring manual intervention.
     * 
     * @param transaction The transaction requiring intervention
     */
    fun showManualInterventionNotification(transaction: Transaction) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TRANSACTIONS)
            .setContentTitle(context.getString(R.string.manual_intervention_notification))
            .setContentText(context.getString(
                R.string.manual_intervention_message,
                transaction.timoId
            ))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
    }
    
    /**
     * Gets the next notification ID.
     * 
     * @return The next notification ID
     */
    private fun getNextNotificationId(): Int {
        return notificationId++
    }
    
    /**
     * Shows a smart notification with proper categorization and priority.
     * 
     * @param title The notification title
     * @param message The notification message
     * @param category The notification category
     * @param priority The notification priority
     * @param key A unique key for this notification type (for updating)
     * @param data Additional data for the notification
     */
    fun showSmartNotification(
        title: String,
        message: String,
        category: NotificationCategory,
        priority: NotificationPriority = NotificationPriority.NORMAL,
        key: String? = null,
        data: Map<String, String> = emptyMap()
    ) {
        val channelId = when {
            priority == NotificationPriority.CRITICAL -> CHANNEL_ID_CRITICAL
            category == NotificationCategory.CONNECTION -> CHANNEL_ID_CONNECTION
            category == NotificationCategory.SERVICE_STATUS -> CHANNEL_ID_SERVICE_STATUS
            category == NotificationCategory.TRANSACTION || 
            category == NotificationCategory.PAYMENT || 
            category == NotificationCategory.RECHARGE -> CHANNEL_ID_TRANSACTIONS
            else -> CHANNEL_ID_SERVICE
        }
        
        val notificationPriority = when (priority) {
            NotificationPriority.LOW -> NotificationCompat.PRIORITY_LOW
            NotificationPriority.NORMAL -> NotificationCompat.PRIORITY_DEFAULT
            NotificationPriority.HIGH -> NotificationCompat.PRIORITY_HIGH
            NotificationPriority.CRITICAL -> NotificationCompat.PRIORITY_MAX
        }
        
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(notificationPriority)
            .setAutoCancel(true)
        
        // Add actions based on category
        when (category) {
            NotificationCategory.TRANSACTION -> {
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra("SCREEN", "transactions")
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, intent, PendingIntent.FLAG_IMMUTABLE
                )
                builder.setContentIntent(pendingIntent)
            }
            NotificationCategory.CONNECTION -> {
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra("SCREEN", "settings")
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, intent, PendingIntent.FLAG_IMMUTABLE
                )
                builder.setContentIntent(pendingIntent)
            }
            else -> {
                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, intent, PendingIntent.FLAG_IMMUTABLE
                )
                builder.setContentIntent(pendingIntent)
            }
        }
        
        // Use notification ID from key or get a new one
        val notificationId = if (key != null && activeNotifications.containsKey(key)) {
            activeNotifications[key]!!
        } else {
            val newId = getNextNotificationId()
            if (key != null) {
                activeNotifications[key] = newId
            }
            
            // Add to category group
            notificationGroups.getOrPut(category) { mutableListOf() }.add(newId)
            
            newId
        }
        
        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
    
    /**
     * Clears notifications by category.
     * 
     * @param category The category of notifications to clear
     */
    fun clearNotificationsByCategory(category: NotificationCategory) {
        val notificationManager = NotificationManagerCompat.from(context)
        notificationGroups[category]?.forEach { id ->
            notificationManager.cancel(id)
        }
        notificationGroups[category]?.clear()
    }
    
    /**
     * Updates an existing notification.
     * 
     * @param key The key of the notification to update
     * @param title The new title
     * @param message The new message
     */
    fun updateNotification(key: String, title: String, message: String) {
        if (activeNotifications.containsKey(key)) {
            val notificationId = activeNotifications[key]!!
            val builder = NotificationCompat.Builder(context, CHANNEL_ID_TRANSACTIONS)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
            
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }
}
