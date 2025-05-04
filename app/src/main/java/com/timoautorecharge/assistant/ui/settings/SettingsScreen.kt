package com.timoautorecharge.assistant.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.timoautorecharge.assistant.R
import com.timoautorecharge.assistant.data.model.UiElementInfo
import com.timoautorecharge.assistant.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()
    
    // General Settings
    var vodafoneWalletNumber by remember { mutableStateOf(settings.vodafoneWalletNumber) }
    var adminPhoneNumber by remember { mutableStateOf(settings.adminPhoneNumber) }
    
    // Pricing Settings
    var tier1Rate by remember { mutableStateOf(settings.tier1Rate.toString()) }
    var tier2Rate by remember { mutableStateOf(settings.tier2Rate.toString()) }
    var tier3Rate by remember { mutableStateOf(settings.tier3Rate.toString()) }
    var tier1Threshold by remember { mutableStateOf(settings.tier1Threshold.toString()) }
    var tier2Threshold by remember { mutableStateOf(settings.tier2Threshold.toString()) }
    
    // SMS Settings
    var vodafoneSmsTemplate by remember { mutableStateOf(settings.vodafoneSmsTemplate) }
    var vodafoneSenderNumbers by remember { mutableStateOf(settings.vodafoneSenderNumbers.joinToString(",")) }
    var smsMatchByLastDigits by remember { mutableStateOf(settings.smsMatchByLastDigits.toString()) }
    var paymentTimeoutMinutes by remember { mutableStateOf(settings.paymentTimeoutMinutes.toString()) }
    
    // WhatsApp Settings
    var whatsappRechargeRequestPattern by remember { mutableStateOf(settings.whatsappRechargeRequestPattern) }
    var whatsappPaymentInstructionsTemplate by remember { mutableStateOf(settings.whatsappPaymentInstructionsTemplate) }
    var whatsappPaymentConfirmationTemplate by remember { mutableStateOf(settings.whatsappPaymentConfirmationTemplate) }
    var whatsappRechargeConfirmationTemplate by remember { mutableStateOf(settings.whatsappRechargeConfirmationTemplate) }
    
    // Timo App Settings
    var timoPackageName by remember { mutableStateOf(settings.timoPackageName) }
    var rechargeButtonId by remember { mutableStateOf(settings.rechargeButtonId) }
    var timoIdInputId by remember { mutableStateOf(settings.timoIdInputId) }
    var coinQuantityInputId by remember { mutableStateOf(settings.coinQuantityInputId) }
    var confirmButtonId by remember { mutableStateOf(settings.confirmButtonId) }
    
    // Navigation Settings
    var navigationDelayMs by remember { mutableStateOf(settings.navigationDelayMs.toString()) }
    var maxNavigationRetries by remember { mutableStateOf(settings.maxNavigationRetries.toString()) }
    var navigationTimeoutMs by remember { mutableStateOf(settings.navigationTimeoutMs.toString()) }
    
    LaunchedEffect(settings) {
        // General Settings
        vodafoneWalletNumber = settings.vodafoneWalletNumber
        adminPhoneNumber = settings.adminPhoneNumber
        
        // Pricing Settings
        tier1Rate = settings.tier1Rate.toString()
        tier2Rate = settings.tier2Rate.toString()
        tier3Rate = settings.tier3Rate.toString()
        tier1Threshold = settings.tier1Threshold.toString()
        tier2Threshold = settings.tier2Threshold.toString()
        
        // SMS Settings
        vodafoneSmsTemplate = settings.vodafoneSmsTemplate
        vodafoneSenderNumbers = settings.vodafoneSenderNumbers.joinToString(",")
        smsMatchByLastDigits = settings.smsMatchByLastDigits.toString()
        paymentTimeoutMinutes = settings.paymentTimeoutMinutes.toString()
        
        // WhatsApp Settings
        whatsappRechargeRequestPattern = settings.whatsappRechargeRequestPattern
        whatsappPaymentInstructionsTemplate = settings.whatsappPaymentInstructionsTemplate
        whatsappPaymentConfirmationTemplate = settings.whatsappPaymentConfirmationTemplate
        whatsappRechargeConfirmationTemplate = settings.whatsappRechargeConfirmationTemplate
        
        // Timo App Settings
        timoPackageName = settings.timoPackageName
        rechargeButtonId = settings.rechargeButtonId
        timoIdInputId = settings.timoIdInputId
        coinQuantityInputId = settings.coinQuantityInputId
        confirmButtonId = settings.confirmButtonId
        
        // Navigation Settings
        navigationDelayMs = settings.navigationDelayMs.toString()
        maxNavigationRetries = settings.maxNavigationRetries.toString()
        navigationTimeoutMs = settings.navigationTimeoutMs.toString()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // General Settings Section
        SettingsSection(title = stringResource(R.string.general_settings)) {
            OutlinedTextField(
                value = vodafoneWalletNumber,
                onValueChange = { vodafoneWalletNumber = it },
                label = { Text("Vodafone Wallet Number") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = adminPhoneNumber,
                onValueChange = { adminPhoneNumber = it },
                label = { Text("Admin Phone Number") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
        }
        
        // Pricing Settings Section
        SettingsSection(title = stringResource(R.string.pricing_settings)) {
            OutlinedTextField(
                value = tier1Rate,
                onValueChange = { tier1Rate = it },
                label = { Text("Tier 1 Rate (EGP per 100 coins)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = tier1Threshold,
                onValueChange = { tier1Threshold = it },
                label = { Text("Tier 1 Upper Limit (coins)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = tier2Rate,
                onValueChange = { tier2Rate = it },
                label = { Text("Tier 2 Rate (EGP per 100 coins)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = tier2Threshold,
                onValueChange = { tier2Threshold = it },
                label = { Text("Tier 2 Upper Limit (coins)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = tier3Rate,
                onValueChange = { tier3Rate = it },
                label = { Text("Tier 3 Rate (EGP per 100 coins)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
        
        // SMS Settings Section
        SettingsSection(title = stringResource(R.string.sms_settings)) {
            OutlinedTextField(
                value = vodafoneSmsTemplate,
                onValueChange = { vodafoneSmsTemplate = it },
                label = { Text("Vodafone SMS Template (Regex)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = vodafoneSenderNumbers,
                onValueChange = { vodafoneSenderNumbers = it },
                label = { Text("Vodafone Sender Numbers (comma separated)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = smsMatchByLastDigits,
                onValueChange = { smsMatchByLastDigits = it },
                label = { Text("Match by Last N Digits") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = paymentTimeoutMinutes,
                onValueChange = { paymentTimeoutMinutes = it },
                label = { Text("Payment Timeout (minutes)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        
        // WhatsApp Settings Section
        SettingsSection(title = stringResource(R.string.whatsapp_settings)) {
            OutlinedTextField(
                value = whatsappRechargeRequestPattern,
                onValueChange = { whatsappRechargeRequestPattern = it },
                label = { Text("Recharge Request Pattern (Regex)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = whatsappPaymentInstructionsTemplate,
                onValueChange = { whatsappPaymentInstructionsTemplate = it },
                label = { Text("Payment Instructions Template") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = whatsappPaymentConfirmationTemplate,
                onValueChange = { whatsappPaymentConfirmationTemplate = it },
                label = { Text("Payment Confirmation Template") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = whatsappRechargeConfirmationTemplate,
                onValueChange = { whatsappRechargeConfirmationTemplate = it },
                label = { Text("Recharge Confirmation Template") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // Timo App Settings Section
        SettingsSection(title = stringResource(R.string.timo_app_settings)) {
            OutlinedTextField(
                value = timoPackageName,
                onValueChange = { timoPackageName = it },
                label = { Text("Timo Package Name") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = rechargeButtonId,
                    onValueChange = { rechargeButtonId = it },
                    label = { Text("Recharge Button ID") },
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = { viewModel.launchElementSelector(
                        UiElementInfo.ElementType.BUTTON,
                        timoPackageName,
                        "rechargeButtonId"
                    ) },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.select_button))
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = timoIdInputId,
                    onValueChange = { timoIdInputId = it },
                    label = { Text("Timo ID Input Field ID") },
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = { viewModel.launchElementSelector(
                        UiElementInfo.ElementType.INPUT_FIELD,
                        timoPackageName,
                        "timoIdInputId"
                    ) },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.select_input_field))
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = coinQuantityInputId,
                    onValueChange = { coinQuantityInputId = it },
                    label = { Text("Coin Quantity Input Field ID") },
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = { viewModel.launchElementSelector(
                        UiElementInfo.ElementType.INPUT_FIELD,
                        timoPackageName,
                        "coinQuantityInputId"
                    ) },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.select_input_field))
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = confirmButtonId,
                    onValueChange = { confirmButtonId = it },
                    label = { Text("Confirm Button ID") },
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = { viewModel.launchElementSelector(
                        UiElementInfo.ElementType.BUTTON,
                        timoPackageName,
                        "confirmButtonId"
                    ) },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.select_button))
                }
            }
        }
        
        // Navigation Settings Section
        SettingsSection(title = stringResource(R.string.navigation_settings)) {
            OutlinedTextField(
                value = navigationDelayMs,
                onValueChange = { navigationDelayMs = it },
                label = { Text("Navigation Delay (ms)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = maxNavigationRetries,
                onValueChange = { maxNavigationRetries = it },
                label = { Text("Max Navigation Retries") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = navigationTimeoutMs,
                onValueChange = { navigationTimeoutMs = it },
                label = { Text("Navigation Timeout (ms)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                viewModel.updateSettings(
                    settings.copy(
                        // General Settings
                        vodafoneWalletNumber = vodafoneWalletNumber,
                        adminPhoneNumber = adminPhoneNumber,
                        
                        // Pricing Settings
                        tier1Rate = tier1Rate.toDoubleOrNull() ?: settings.tier1Rate,
                        tier2Rate = tier2Rate.toDoubleOrNull() ?: settings.tier2Rate,
                        tier3Rate = tier3Rate.toDoubleOrNull() ?: settings.tier3Rate,
                        tier1Threshold = tier1Threshold.toIntOrNull() ?: settings.tier1Threshold,
                        tier2Threshold = tier2Threshold.toIntOrNull() ?: settings.tier2Threshold,
                        
                        // SMS Settings
                        vodafoneSmsTemplate = vodafoneSmsTemplate,
                        vodafoneSenderNumbers = vodafoneSenderNumbers.split(",").map { it.trim() },
                        smsMatchByLastDigits = smsMatchByLastDigits.toIntOrNull() ?: settings.smsMatchByLastDigits,
                        paymentTimeoutMinutes = paymentTimeoutMinutes.toIntOrNull() ?: settings.paymentTimeoutMinutes,
                        
                        // WhatsApp Settings
                        whatsappRechargeRequestPattern = whatsappRechargeRequestPattern,
                        whatsappPaymentInstructionsTemplate = whatsappPaymentInstructionsTemplate,
                        whatsappPaymentConfirmationTemplate = whatsappPaymentConfirmationTemplate,
                        whatsappRechargeConfirmationTemplate = whatsappRechargeConfirmationTemplate,
                        
                        // Timo App Settings
                        timoPackageName = timoPackageName,
                        rechargeButtonId = rechargeButtonId,
                        timoIdInputId = timoIdInputId,
                        coinQuantityInputId = coinQuantityInputId,
                        confirmButtonId = confirmButtonId,
                        
                        // Navigation Settings
                        navigationDelayMs = navigationDelayMs.toLongOrNull() ?: settings.navigationDelayMs,
                        maxNavigationRetries = maxNavigationRetries.toIntOrNull() ?: settings.maxNavigationRetries,
                        navigationTimeoutMs = navigationTimeoutMs.toLongOrNull() ?: settings.navigationTimeoutMs
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.save_settings))
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(vertical = 8.dp)
    )
    
    content()
    
    Spacer(modifier = Modifier.height(16.dp))
}
