package com.timoautorecharge.assistant.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.timoautorecharge.assistant.data.model.Transaction
import com.timoautorecharge.assistant.data.model.TransactionStatus
import com.timoautorecharge.assistant.data.repository.TransactionRepository
import com.timoautorecharge.assistant.service.SmsService
import com.timoautorecharge.assistant.service.TimoAccessibilityService
import com.timoautorecharge.assistant.service.TransactionCoordinatorService
import com.timoautorecharge.assistant.service.WhatsAppService
import com.timoautorecharge.assistant.util.CoinCalculator
import com.timoautorecharge.assistant.util.NotificationService
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.test.KoinTest
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Integration test for the complete transaction flow.
 * Tests the flow from WhatsApp message reception to Timo recharge execution.
 */
@RunWith(AndroidJUnit4::class)
class TransactionFlowTest : KoinTest {
    
    // Services
    private lateinit var transactionCoordinator: TransactionCoordinatorService
    private lateinit var whatsAppService: WhatsAppService
    private lateinit var smsService: SmsService
    private lateinit var timoAccessibilityService: TimoAccessibilityService
    
    // Repositories
    private val transactionRepository: TransactionRepository by inject()
    
    // Test data
    private val testTimoId = "12345"
    private val testAmount = 100.0
    private val testSenderNumber = "01234567890"
    private val testCoinQuantity = 13500 // Based on the rate of 0.74/100 coins
    
    @Before
    fun setup() {
        // Mock services
        whatsAppService = mock(WhatsAppService::class.java)
        smsService = mock(SmsService::class.java)
        timoAccessibilityService = mock(TimoAccessibilityService::class.java)
        
        // Set up transaction coordinator with mocked services
        transactionCoordinator = TransactionCoordinatorService(
            context = ApplicationProvider.getApplicationContext(),
            transactionRepository = transactionRepository,
            logRepository = mock(),
            settingsRepository = mock(),
            whatsAppService = whatsAppService,
            smsService = smsService,
            notificationService = mock(NotificationService::class.java),
            coinCalculator = mock(CoinCalculator::class.java)
        )
        
        // Set up coin calculator mock
        `when`(transactionCoordinator.calculateCoins(testAmount)).thenReturn(testCoinQuantity)
    }
    
    /**
     * Tests the complete transaction flow:
     * 1. WhatsApp message reception and parsing
     * 2. SMS payment verification
     * 3. Timo app recharge execution
     */
    @Test
    fun testCompleteTransactionFlow() = runBlocking {
        // Step 1: Process WhatsApp message
        val transactionId = transactionCoordinator.processRechargeRequest(
            timoId = testTimoId,
            amount = testAmount,
            senderNumber = testSenderNumber
        )
        
        // Verify transaction was created with correct status
        val transaction = transactionRepository.getTransactionById(transactionId)
        assert(transaction != null)
        assert(transaction?.status == TransactionStatus.PAYMENT_PENDING)
        assert(transaction?.timoId == testTimoId)
        assert(transaction?.requestAmount == testAmount)
        assert(transaction?.senderNumber == testSenderNumber)
        
        // Verify WhatsApp message was sent with payment instructions
        verify(whatsAppService).sendMessage(
            phoneNumber = testSenderNumber,
            message = any() // Payment instructions message
        )
        
        // Step 2: Process SMS payment verification
        val verified = transactionCoordinator.processPaymentVerification(
            senderNumber = testSenderNumber,
            amount = testAmount,
            timestamp = System.currentTimeMillis()
        )
        
        // Verify payment was verified
        assert(verified)
        
        // Get updated transaction
        val updatedTransaction = transactionRepository.getTransactionById(transactionId)
        assert(updatedTransaction?.status == TransactionStatus.PAYMENT_VERIFIED)
        assert(updatedTransaction?.paymentVerified == true)
        
        // Verify WhatsApp confirmation message was sent
        verify(whatsAppService).sendMessage(
            phoneNumber = testSenderNumber,
            message = any() // Payment confirmation message
        )
        
        // Step 3: Process recharge execution
        val latch = CountDownLatch(1)
        
        // Set up Timo service mock to return success
        `when`(timoAccessibilityService.performRecharge(testTimoId, testCoinQuantity))
            .thenReturn(TimoAccessibilityService.RechargeResult(success = true))
        
        // Process recharge completion
        transactionCoordinator.processRechargeCompletion(
            transactionId = transactionId,
            success = true,
            errorMessage = null
        )
        
        // Get final transaction state
        val finalTransaction = transactionRepository.getTransactionById(transactionId)
        assert(finalTransaction?.status == TransactionStatus.RECHARGE_COMPLETED)
        
        // Verify WhatsApp completion message was sent
        verify(whatsAppService).sendMessage(
            phoneNumber = testSenderNumber,
            message = any() // Recharge completion message
        )
    }
    
    /**
     * Tests the transaction flow with payment verification failure.
     */
    @Test
    fun testPaymentVerificationFailure() = runBlocking {
        // Step 1: Process WhatsApp message
        val transactionId = transactionCoordinator.processRechargeRequest(
            timoId = testTimoId,
            amount = testAmount,
            senderNumber = testSenderNumber
        )
        
        // Step 2: Process SMS payment verification with wrong amount
        val verified = transactionCoordinator.processPaymentVerification(
            senderNumber = testSenderNumber,
            amount = testAmount - 10, // Wrong amount
            timestamp = System.currentTimeMillis()
        )
        
        // Verify payment was not verified
        assert(!verified)
        
        // Get updated transaction
        val updatedTransaction = transactionRepository.getTransactionById(transactionId)
        assert(updatedTransaction?.status == TransactionStatus.PAYMENT_PENDING)
        assert(updatedTransaction?.paymentVerified == false)
    }
    
    /**
     * Tests the transaction flow with recharge failure.
     */
    @Test
    fun testRechargeFailure() = runBlocking {
        // Step 1: Process WhatsApp message
        val transactionId = transactionCoordinator.processRechargeRequest(
            timoId = testTimoId,
            amount = testAmount,
            senderNumber = testSenderNumber
        )
        
        // Step 2: Process SMS payment verification
        val verified = transactionCoordinator.processPaymentVerification(
            senderNumber = testSenderNumber,
            amount = testAmount,
            timestamp = System.currentTimeMillis()
        )
        
        // Verify payment was verified
        assert(verified)
        
        // Set up Timo service mock to return failure
        `when`(timoAccessibilityService.performRecharge(testTimoId, testCoinQuantity))
            .thenReturn(TimoAccessibilityService.RechargeResult(
                success = false,
                errorMessage = "Failed to navigate to recharge screen"
            ))
        
        // Process recharge completion with failure
        transactionCoordinator.processRechargeCompletion(
            transactionId = transactionId,
            success = false,
            errorMessage = "Failed to navigate to recharge screen"
        )
        
        // Get final transaction state
        val finalTransaction = transactionRepository.getTransactionById(transactionId)
        assert(finalTransaction?.status == TransactionStatus.RECHARGE_FAILED)
        
        // Verify WhatsApp failure message was sent
        verify(whatsAppService).sendMessage(
            phoneNumber = testSenderNumber,
            message = any() // Recharge failure message
        )
    }
    
    // Helper function for Mockito's any() matcher
    private fun <T> any(): T {
        org.mockito.Mockito.any<T>()
        return null as T
    }
}
