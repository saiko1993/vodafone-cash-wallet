package com.timoautorecharge.assistant.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = 1, // Only one settings record
    val vodafoneWalletNumber: String,
    val tier1Rate: Double = 0.74, // Up to 49,000 coins
    val tier2Rate: Double = 0.80, // 50,000-749,000 coins
    val tier3Rate: Double = 0.86, // 750,000+ coins
    val tier1Threshold: Int = 49000, // Upper limit for tier 1
    val tier2Threshold: Int = 749000, // Upper limit for tier 2
    val paymentTimeoutMinutes: Int = 15,
    val adminPhoneNumber: String,
    val notifyCustomerOnSuccess: Boolean = true,
    
    // SMS Template settings
    val vodafoneSmsTemplate: String = ".*vodafone cash.*received.*EGP\\s*(\\d+\\.?\\d*).*from\\s*(\\d+).*",
    val vodafoneSenderNumbers: List<String> = listOf("VODAFONE", "VF-CASH"),
    val smsMatchByLastDigits: Int = 5, // Number of last digits to match sender number
    
    // WhatsApp settings
    val whatsappRechargeRequestPattern: String = "(?i).*recharge.*?(\\d+).*?ID[:\\s]*(\\d+).*",
    val whatsappPaymentInstructionsTemplate: String = "Please send %s EGP to Vodafone Cash wallet %s",
    val whatsappPaymentConfirmationTemplate: String = "Your payment of %s EGP has been confirmed. Recharging your Timo account now.",
    val whatsappRechargeConfirmationTemplate: String = "%s coins have been successfully recharged to your Timo ID %s.",
    val whatsAppApiKey: String? = null,
    
    // Timo app navigation settings
    val timoPackageName: String = "com.timo.app",
    val rechargeButtonId: String = "recharge_button",
    val timoIdInputId: String = "timo_id_input",
    val coinQuantityInputId: String = "coin_quantity_input",
    val confirmButtonId: String = "confirm_button",
    
    // Navigation sequence settings
    val navigationDelayMs: Long = 500, // Delay between navigation actions
    val maxNavigationRetries: Int = 3, // Maximum number of retries for navigation actions
    val navigationTimeoutMs: Long = 10000 // Timeout for navigation actions
)
