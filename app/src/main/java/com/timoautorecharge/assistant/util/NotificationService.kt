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
 */
class NotificationService(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        const val CHANNEL_ID_SERVICE = "auto_recharge_service"
        const val CHANNEL_ID_TRANSACTIONS = "transactions"
        const val CHANNEL_ID_SERVICE_STATUS = "service_status"
        const val NOTIFICATION_ID_SERVICE = 1
        private var notificationId = 100
    }
    
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
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannels(
                listOf(serviceChannel, transactionsChannel, serviceStatusChannel)
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
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SERVICE_STATUS)
            .setContentTitle("$serviceName Status")
            .setContentText(statusMessage)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
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
}
