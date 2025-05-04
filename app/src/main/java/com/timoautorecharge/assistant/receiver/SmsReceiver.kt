package com.timoautorecharge.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.timoautorecharge.assistant.data.model.LogType
import com.timoautorecharge.assistant.data.repository.LogRepository
import com.timoautorecharge.assistant.service.SmsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class SmsReceiver : BroadcastReceiver() {
    
    private val smsService: SmsService by inject(SmsService::class.java)
    private val logRepository: LogRepository by inject(LogRepository::class.java)
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            
            for (message in messages) {
                val sender = message.originatingAddress ?: continue
                val body = message.messageBody ?: continue
                
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        logRepository.logInfo(
                            LogType.SMS,
                            "Received SMS from $sender: $body"
                        )
                        
                        smsService.processSmsMessage(sender, body)
                    } catch (e: Exception) {
                        logRepository.logError(
                            LogType.SMS,
                            "Error processing SMS: ${e.message}"
                        )
                    }
                }
            }
        }
    }
}
