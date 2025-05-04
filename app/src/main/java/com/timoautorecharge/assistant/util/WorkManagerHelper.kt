package com.timoautorecharge.assistant.util

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.timoautorecharge.assistant.worker.RechargeWorker
import java.util.concurrent.TimeUnit

/**
 * Helper class for configuring and scheduling WorkManager tasks.
 * Optimizes work requests for battery efficiency and reliability.
 */
class WorkManagerHelper(private val context: Context) {
    
    companion object {
        private const val RECHARGE_WORK_NAME_PREFIX = "recharge_work_"
        private const val BACKOFF_DELAY_SECONDS = 30L
        private const val MAX_RETRIES = 3
    }
    
    /**
     * Schedules a recharge work request with battery-efficient constraints.
     * 
     * @param transactionId The ID of the transaction to recharge
     * @return The ID of the scheduled work request
     */
    fun scheduleRechargeWork(transactionId: Long): String {
        val workName = "$RECHARGE_WORK_NAME_PREFIX$transactionId"
        
        // Create input data
        val inputData = Data.Builder()
            .putLong(RechargeWorker.KEY_TRANSACTION_ID, transactionId)
            .build()
        
        // Define constraints for battery efficiency
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true) // Don't run when battery is low
            .setRequiresDeviceIdle(false) // Can run when device is not idle
            .setRequiresCharging(false) // Don't require charging
            .setRequiredNetworkType(NetworkType.CONNECTED) // Require network connection
            .build()
        
        // Build work request with exponential backoff for retries
        val rechargeWorkRequest = OneTimeWorkRequestBuilder<RechargeWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_DELAY_SECONDS,
                TimeUnit.SECONDS
            )
            .build()
        
        // Enqueue unique work to prevent duplicate recharge attempts
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE, // Replace any existing work with the same name
            rechargeWorkRequest
        )
        
        return rechargeWorkRequest.id.toString()
    }
    
    /**
     * Cancels a scheduled recharge work request.
     * 
     * @param transactionId The ID of the transaction whose recharge work should be canceled
     */
    fun cancelRechargeWork(transactionId: Long) {
        val workName = "$RECHARGE_WORK_NAME_PREFIX$transactionId"
        WorkManager.getInstance(context).cancelUniqueWork(workName)
    }
    
    /**
     * Schedules a recharge work request with a delay for retry.
     * Used when a recharge attempt fails but should be retried later.
     * 
     * @param transactionId The ID of the transaction to recharge
     * @param delayMinutes The delay in minutes before the retry
     * @param attemptCount The current attempt count (for exponential backoff)
     * @return The ID of the scheduled work request
     */
    fun scheduleDelayedRechargeWork(
        transactionId: Long,
        delayMinutes: Long = 5,
        attemptCount: Int = 1
    ): String {
        val workName = "${RECHARGE_WORK_NAME_PREFIX}${transactionId}_retry_$attemptCount"
        
        // Create input data
        val inputData = Data.Builder()
            .putLong(RechargeWorker.KEY_TRANSACTION_ID, transactionId)
            .build()
        
        // Define constraints for battery efficiency
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(false)
            .setRequiresCharging(false)
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        // Calculate exponential backoff delay
        val exponentialDelay = delayMinutes * (1 shl (attemptCount - 1))
        val cappedDelay = exponentialDelay.coerceAtMost(60) // Cap at 60 minutes
        
        // Build work request with delay
        val rechargeWorkRequest = OneTimeWorkRequestBuilder<RechargeWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setInitialDelay(cappedDelay, TimeUnit.MINUTES)
            .build()
        
        // Enqueue unique work
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            rechargeWorkRequest
        )
        
        return rechargeWorkRequest.id.toString()
    }
    
    /**
     * Prunes completed work to free up resources.
     * Should be called periodically to clean up WorkManager database.
     */
    fun pruneCompletedWork() {
        WorkManager.getInstance(context).pruneWork()
    }
}
