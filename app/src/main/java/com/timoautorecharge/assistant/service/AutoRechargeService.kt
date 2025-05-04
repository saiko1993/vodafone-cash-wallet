package com.timoautorecharge.assistant.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.timoautorecharge.assistant.data.model.TransactionStatus
import com.timoautorecharge.assistant.data.repository.LogRepository
import com.timoautorecharge.assistant.data.repository.SettingsRepository
import com.timoautorecharge.assistant.data.repository.TransactionRepository
import com.timoautorecharge.assistant.util.NotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber

class AutoRechargeService : Service() {
    
    private val notificationService: NotificationService by inject()
    private val transactionRepository: TransactionRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val logRepository: LogRepository by inject()
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        startForeground(
            NotificationService.NOTIFICATION_ID_SERVICE,
            notificationService.createServiceNotification()
        )
        
        // Start monitoring for payment timeouts
        startPaymentTimeoutMonitoring()
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
    }
    
    private fun startPaymentTimeoutMonitoring() {
        serviceScope.launch {
            while (isActive) {
                try {
                    checkForPaymentTimeouts()
                    delay(5 * 60 * 1000) // Check every 5 minutes
                } catch (e: Exception) {
                    Timber.e(e, "Error in payment timeout monitoring")
                }
            }
        }
    }
    
    private suspend fun checkForPaymentTimeouts() {
        val settings = settingsRepository.getSettingsSync()
        val timeoutMinutes = settings.paymentTimeoutMinutes
        
        // Calculate cutoff time
        val cutoffTime = System.currentTimeMillis() - (timeoutMinutes * 60 * 1000)
        
        // Get transactions that have timed out
        val pendingTransactions = transactionRepository.getPendingPaymentTransactions(cutoffTime)
        
        // Update status and notify for each timed out transaction
        for (transaction in pendingTransactions) {
            if (transaction.requestTimestamp < cutoffTime) {
                // Update transaction status
                val updatedTransaction = transaction.copy(
                    status = TransactionStatus.PAYMENT_TIMEOUT
                )
                
                transactionRepository.updateTransaction(updatedTransaction)
                
                // Notify admin
                notificationService.notifyPaymentTimeout(updatedTransaction)
                
                // Log the timeout
                logRepository.logWarning(
                    com.timoautorecharge.assistant.data.model.LogType.SMS,
                    "Payment timeout for transaction ${transaction.id}",
                    transaction.id
                )
            }
        }
    }
}
