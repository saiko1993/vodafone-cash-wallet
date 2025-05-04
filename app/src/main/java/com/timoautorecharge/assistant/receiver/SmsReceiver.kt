package com.timoautorecharge.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.timoautorecharge.assistant.data.model.Log
import com.timoautorecharge.assistant.data.repository.LogRepository
import com.timoautorecharge.assistant.service.SmsService
import com.timoautorecharge.assistant.service.TransactionCoordinatorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

/**
 * BroadcastReceiver for SMS messages.
 * Listens for incoming SMS messages and processes them for payment verification.
 */
class SmsReceiver : BroadcastReceiver() {
    
    private val smsService: SmsService by inject(SmsService::class.java)
    private val logRepository: LogRepository by inject(LogRepository::class.java)
    private val transactionCoordinator: TransactionCoordinatorService by inject(TransactionCoordinatorService::class.java)
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            
            for (message in messages) {
                val sender = message.originatingAddress ?: continue
                val body = message.messageBody ?: continue
                
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Log the received SMS
                        logRepository.addLog(
                            Log(
                                message = "Received SMS from $sender",
                                timestamp = System.currentTimeMillis(),
                                level = Log.Level.INFO,
                                source = "SmsReceiver"
                            )
                        )
                        
                        // Parse the SMS message for payment information
                        val paymentInfo = smsService.parsePaymentInfo(sender, body)
                        
                        if (paymentInfo != null) {
                            // Log the payment information
                            logRepository.addLog(
                                Log(
                                    message = "Parsed payment: ${paymentInfo.amount} EGP from ${paymentInfo.senderNumber}",
                                    timestamp = System.currentTimeMillis(),
                                    level = Log.Level.INFO,
                                    source = "SmsReceiver"
                                )
                            )
                            
                            // Process the payment verification through the coordinator
                            val verified = transactionCoordinator.processPaymentVerification(
                                senderNumber = paymentInfo.senderNumber,
                                amount = paymentInfo.amount,
                                timestamp = paymentInfo.timestamp
                            )
                            
                            if (verified) {
                                logRepository.addLog(
                                    Log(
                                        message = "Payment verification successful for ${paymentInfo.amount} EGP from ${paymentInfo.senderNumber}",
                                        timestamp = System.currentTimeMillis(),
                                        level = Log.Level.INFO,
                                        source = "SmsReceiver"
                                    )
                                )
                            } else {
                                logRepository.addLog(
                                    Log(
                                        message = "No matching transaction found for payment: ${paymentInfo.amount} EGP from ${paymentInfo.senderNumber}",
                                        timestamp = System.currentTimeMillis(),
                                        level = Log.Level.WARNING,
                                        source = "SmsReceiver"
                                    )
                                )
                            }
                        } else {
                            // Not a payment SMS, just log it
                            logRepository.addLog(
                                Log(
                                    message = "SMS is not a payment notification",
                                    timestamp = System.currentTimeMillis(),
                                    level = Log.Level.DEBUG,
                                    source = "SmsReceiver"
                                )
                            )
                        }
                    } catch (e: Exception) {
                        logRepository.addLog(
                            Log(
                                message = "Error processing SMS: ${e.message}",
                                timestamp = System.currentTimeMillis(),
                                level = Log.Level.ERROR,
                                source = "SmsReceiver"
                            )
                        )
                    }
                }
            }
        }
    }
}
