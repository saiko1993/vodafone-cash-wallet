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

class NotificationService(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        const val CHANNEL_ID_SERVICE = "auto_recharge_service"
        const val CHANNEL_ID_TRANSACTIONS = "transactions"
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
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannels(listOf(serviceChannel, transactionsChannel))
        }
    }
    
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
    
    fun notifyPaymentVerified(transaction: Transaction) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TRANSACTIONS)
            .setContentTitle("Payment Verified")
            .setContentText("Payment of ${transaction.requestAmount} EGP for Timo ID ${transaction.timoId} verified")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
    }
    
    fun notifyRechargeCompleted(transaction: Transaction) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TRANSACTIONS)
            .setContentTitle("Recharge Completed")
            .setContentText("${transaction.coinQuantity} coins recharged for Timo ID ${transaction.timoId}")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
    }
    
    fun notifyRechargeFailed(transaction: Transaction) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TRANSACTIONS)
            .setContentTitle("Recharge Failed")
            .setContentText("Failed to recharge ${transaction.coinQuantity} coins for Timo ID ${transaction.timoId}")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
    }
    
    fun notifyPaymentTimeout(transaction: Transaction) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TRANSACTIONS)
            .setContentTitle("Payment Timeout")
            .setContentText("Payment for Timo ID ${transaction.timoId} timed out")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
    }
    
    fun notifyUnmatchedPayment(amount: Double, senderNumber: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TRANSACTIONS)
            .setContentTitle("Unmatched Payment")
            .setContentText("Received payment of $amount EGP from $senderNumber with no matching request")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
    }
    
    fun notifyManualInterventionRequired(transaction: Transaction) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TRANSACTIONS)
            .setContentTitle("Manual Intervention Required")
            .setContentText("Transaction for Timo ID ${transaction.timoId} requires manual intervention")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        NotificationManagerCompat.from(context).notify(getNextNotificationId(), notification)
    }
    
    private fun getNextNotificationId(): Int {
        return notificationId++
    }
}
