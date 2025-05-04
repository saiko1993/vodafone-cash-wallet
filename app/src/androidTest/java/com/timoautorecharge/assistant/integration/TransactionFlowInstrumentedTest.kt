package com.timoautorecharge.assistant.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.testing.WorkManagerTestInitHelper
import com.timoautorecharge.assistant.data.model.Transaction
import com.timoautorecharge.assistant.data.model.TransactionStatus
import com.timoautorecharge.assistant.data.repository.LogRepository
import com.timoautorecharge.assistant.data.repository.SettingsRepository
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
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumented test for the complete transaction flow.
 * Tests the integration of all modules in a real Android environment.
 */
@RunWith(AndroidJUnit4::class)
class TransactionFlowInstrumentedTest : KoinTest {
    
    // Services
    private val transactionCoordinator: TransactionCoordinatorService by inject()
    private val whatsAppService: WhatsAppService by inject()
    private val smsService: SmsService by inject()
    private val timoAccessibilityService: TimoAccessibilityService by inject()
    
    // Repositories
    private val transactionRepository: TransactionRepository by inject()
    private val logRepository: LogRepository by inject()
    private val settingsRepository: SettingsRepository by inject()
    
    // Test data
    private val testTimoId = "12345"
    private val testAmount = 100.0
    private val testSenderNumber = "01234567890"
    
    @Before
    fun setup() {
        // Stop any existing Koin instance
        stopKoin()
        
        // Initialize WorkManager for testing
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        
        // Start Koin with test modules
        startKoin {
            androidContext(context)
            modules(testModule)
        }
    }
    
    /**
     * Tests the creation of a transaction through the TransactionCoordinatorService.
     */
    @Test
    fun testTransactionCreation() = runBlocking {
        // Process a recharge request
        val transactionId = transactionCoordinator.processRechargeRequest(
            timoId = testTimoId,
            amount = testAmount,
            senderNumber = testSenderNumber
        )
        
        // Verify transaction was created
        val transaction = transactionRepository.getTransactionById(transactionId)
        assert(transaction != null) { "Transaction was not created" }
        assert(transaction?.timoId == testTimoId) { "Transaction has incorrect Timo ID" }
        assert(transaction?.requestAmount == testAmount) { "Transaction has incorrect amount" }
        assert(transaction?.senderNumber == testSenderNumber) { "Transaction has incorrect sender number" }
        assert(transaction?.status == TransactionStatus.PAYMENT_PENDING) { "Transaction has incorrect status" }
    }
    
    /**
     * Tests the payment verification process.
     */
    @Test
    fun testPaymentVerification() = runBlocking {
        // Create a transaction
        val transactionId = transactionCoordinator.processRechargeRequest(
            timoId = testTimoId,
            amount = testAmount,
            senderNumber = testSenderNumber
        )
        
        // Verify payment
        val verified = transactionCoordinator.processPaymentVerification(
            senderNumber = testSenderNumber,
            amount = testAmount,
            timestamp = System.currentTimeMillis()
        )
        
        // Check verification result
        assert(verified) { "Payment verification failed" }
        
        // Check transaction status
        val transaction = transactionRepository.getTransactionById(transactionId)
        assert(transaction?.status == TransactionStatus.PAYMENT_VERIFIED) { "Transaction status not updated to PAYMENT_VERIFIED" }
        assert(transaction?.paymentVerified == true) { "Transaction paymentVerified flag not set to true" }
    }
    
    /**
     * Tests the recharge completion process.
     */
    @Test
    fun testRechargeCompletion() = runBlocking {
        // Create and verify a transaction
        val transactionId = transactionCoordinator.processRechargeRequest(
            timoId = testTimoId,
            amount = testAmount,
            senderNumber = testSenderNumber
        )
        
        transactionCoordinator.processPaymentVerification(
            senderNumber = testSenderNumber,
            amount = testAmount,
            timestamp = System.currentTimeMillis()
        )
        
        // Complete the recharge
        transactionCoordinator.processRechargeCompletion(
            transactionId = transactionId,
            success = true,
            errorMessage = null
        )
        
        // Check transaction status
        val transaction = transactionRepository.getTransactionById(transactionId)
        assert(transaction?.status == TransactionStatus.RECHARGE_COMPLETED) { "Transaction status not updated to RECHARGE_COMPLETED" }
    }
    
    /**
     * Tests the recharge failure handling.
     */
    @Test
    fun testRechargeFailure() = runBlocking {
        // Create and verify a transaction
        val transactionId = transactionCoordinator.processRechargeRequest(
            timoId = testTimoId,
            amount = testAmount,
            senderNumber = testSenderNumber
        )
        
        transactionCoordinator.processPaymentVerification(
            senderNumber = testSenderNumber,
            amount = testAmount,
            timestamp = System.currentTimeMillis()
        )
        
        // Fail the recharge
        transactionCoordinator.processRechargeCompletion(
            transactionId = transactionId,
            success = false,
            errorMessage = "Test failure"
        )
        
        // Check transaction status
        val transaction = transactionRepository.getTransactionById(transactionId)
        assert(transaction?.status == TransactionStatus.RECHARGE_FAILED) { "Transaction status not updated to RECHARGE_FAILED" }
    }
    
    /**
     * Test module for dependency injection.
     */
    private val testModule = module {
        // Use real implementations for testing
        single { ApplicationProvider.getApplicationContext<Context>() }
        single { TransactionRepository(get()) }
        single { SettingsRepository(get()) }
        single { LogRepository(get()) }
        single { CoinCalculator(get()) }
        single { NotificationService(get(), get()) }
        single { WhatsAppService(get(), get(), get(), get(), get()) }
        single { SmsService(get(), get(), get(), get()) }
        single { TimoAccessibilityService() }
        single { 
            TransactionCoordinatorService(
                context = get(),
                transactionRepository = get(),
                logRepository = get(),
                settingsRepository = get(),
                whatsAppService = get(),
                smsService = get(),
                notificationService = get(),
                coinCalculator = get()
            ) 
        }
    }
}
