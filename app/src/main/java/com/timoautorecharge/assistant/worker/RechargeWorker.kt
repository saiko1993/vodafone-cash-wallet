package com.timoautorecharge.assistant.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.timoautorecharge.assistant.data.model.Log
import com.timoautorecharge.assistant.data.model.TransactionStatus
import com.timoautorecharge.assistant.data.repository.LogRepository
import com.timoautorecharge.assistant.data.repository.TransactionRepository
import com.timoautorecharge.assistant.service.TimoAccessibilityService
import com.timoautorecharge.assistant.service.TransactionCoordinatorService
import org.koin.java.KoinJavaComponent.inject

/**
 * Worker that executes the recharge process in the Timo app.
 * Triggered after payment verification or manual recharge request.
 */
class RechargeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        const val KEY_TRANSACTION_ID = "transactionId"
    }
    
    private val transactionRepository: TransactionRepository by inject(TransactionRepository::class.java)
    private val logRepository: LogRepository by inject(LogRepository::class.java)
    private val timoAccessibilityService: TimoAccessibilityService by inject(TimoAccessibilityService::class.java)
    private val transactionCoordinator: TransactionCoordinatorService by inject(TransactionCoordinatorService::class.java)
    
    override suspend fun doWork(): Result {
        val transactionId = inputData.getLong(KEY_TRANSACTION_ID, -1)
        if (transactionId == -1L) {
            logRepository.addLog(
                Log(
                    message = "Invalid transaction ID in RechargeWorker",
                    timestamp = System.currentTimeMillis(),
                    level = Log.Level.ERROR,
                    source = "RechargeWorker"
                )
            )
            return Result.failure()
        }
        
        val transaction = transactionRepository.getTransactionById(transactionId)
        if (transaction == null) {
            logRepository.addLog(
                Log(
                    message = "Transaction not found: $transactionId",
                    timestamp = System.currentTimeMillis(),
                    level = Log.Level.ERROR,
                    source = "RechargeWorker"
                )
            )
            return Result.failure()
        }
        
        if (!transaction.paymentVerified) {
            logRepository.addLog(
                Log(
                    message = "Attempted to recharge unverified transaction: $transactionId",
                    timestamp = System.currentTimeMillis(),
                    level = Log.Level.WARNING,
                    source = "RechargeWorker"
                )
            )
            return Result.failure()
        }
        
        try {
            // Update transaction status
            transactionRepository.updateTransaction(
                transaction.copy(status = TransactionStatus.RECHARGE_IN_PROGRESS)
            )
            
            // Log the start of recharge
            logRepository.addLog(
                Log(
                    message = "Starting recharge for Timo ID ${transaction.timoId} with ${transaction.coinQuantity} coins",
                    timestamp = System.currentTimeMillis(),
                    level = Log.Level.INFO,
                    source = "RechargeWorker"
                )
            )
            
            // Execute recharge
            val result = executeRecharge(transaction.timoId, transaction.coinQuantity)
            
            // Process the recharge result through the coordinator
            transactionCoordinator.processRechargeCompletion(
                transactionId = transactionId,
                success = result.success,
                errorMessage = result.errorMessage
            )
            
            return if (result.success) {
                Result.success()
            } else if (result.retryable) {
                Result.retry()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            // Log the error
            logRepository.addLog(
                Log(
                    message = "Error during recharge: ${e.message}",
                    timestamp = System.currentTimeMillis(),
                    level = Log.Level.ERROR,
                    source = "RechargeWorker"
                )
            )
            
            // Process the recharge failure through the coordinator
            transactionCoordinator.processRechargeCompletion(
                transactionId = transactionId,
                success = false,
                errorMessage = "Exception: ${e.message}"
            )
            
            return Result.failure()
        }
    }
    
    /**
     * Executes the recharge process using the TimoAccessibilityService.
     * 
     * @param timoId The Timo ID to recharge
     * @param coinQuantity The quantity of coins to recharge
     * @return RechargeResult containing success status and error message if applicable
     */
    private suspend fun executeRecharge(timoId: String, coinQuantity: Int): RechargeResult {
        // Check if the accessibility service is active
        if (timoAccessibilityService.getServiceStatus() != TimoAccessibilityService.ServiceStatus.ACTIVE) {
            return RechargeResult(
                success = false,
                retryable = true,
                errorMessage = "Timo Accessibility Service is not active"
            )
        }
        
        // Perform the recharge using the accessibility service
        return try {
            val result = timoAccessibilityService.performRecharge(timoId, coinQuantity)
            
            if (result.success) {
                logRepository.addLog(
                    Log(
                        message = "Recharge successful for Timo ID $timoId with $coinQuantity coins",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.INFO,
                        source = "RechargeWorker"
                    )
                )
            } else {
                logRepository.addLog(
                    Log(
                        message = "Recharge failed for Timo ID $timoId: ${result.errorMessage}",
                        timestamp = System.currentTimeMillis(),
                        level = Log.Level.ERROR,
                        source = "RechargeWorker"
                    )
                )
            }
            
            result
        } catch (e: Exception) {
            RechargeResult(
                success = false,
                retryable = false,
                errorMessage = "Exception during recharge: ${e.message}"
            )
        }
    }
    
    /**
     * Data class representing the result of a recharge operation.
     */
    data class RechargeResult(
        val success: Boolean,
        val retryable: Boolean = false,
        val errorMessage: String? = null
    )
}
