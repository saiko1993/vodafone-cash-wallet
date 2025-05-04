package com.timoautorecharge.assistant.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.timoautorecharge.assistant.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()
    
    var vodafoneWalletNumber by remember { mutableStateOf(settings.vodafoneWalletNumber) }
    var adminPhoneNumber by remember { mutableStateOf(settings.adminPhoneNumber) }
    var tier1Rate by remember { mutableStateOf(settings.tier1Rate.toString()) }
    var tier2Rate by remember { mutableStateOf(settings.tier2Rate.toString()) }
    var tier3Rate by remember { mutableStateOf(settings.tier3Rate.toString()) }
    var vodafoneSmsTemplate by remember { mutableStateOf(settings.vodafoneSmsTemplate) }
    var vodafoneSenderNumbers by remember { mutableStateOf(settings.vodafoneSenderNumbers.joinToString(",")) }
    var timoPackageName by remember { mutableStateOf(settings.timoPackageName) }
    var rechargeButtonId by remember { mutableStateOf(settings.rechargeButtonId) }
    var timoIdInputId by remember { mutableStateOf(settings.timoIdInputId) }
    var coinQuantityInputId by remember { mutableStateOf(settings.coinQuantityInputId) }
    var confirmButtonId by remember { mutableStateOf(settings.confirmButtonId) }
    
    LaunchedEffect(settings) {
        vodafoneWalletNumber = settings.vodafoneWalletNumber
        adminPhoneNumber = settings.adminPhoneNumber
        tier1Rate = settings.tier1Rate.toString()
        tier2Rate = settings.tier2Rate.toString()
        tier3Rate = settings.tier3Rate.toString()
        vodafoneSmsTemplate = settings.vodafoneSmsTemplate
        vodafoneSenderNumbers = settings.vodafoneSenderNumbers.joinToString(",")
        timoPackageName = settings.timoPackageName
        rechargeButtonId = settings.rechargeButtonId
        timoIdInputId = settings.timoIdInputId
        coinQuantityInputId = settings.coinQuantityInputId
        confirmButtonId = settings.confirmButtonId
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // General Settings Section
        Text(
            text = "General Settings",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
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
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Pricing Settings Section
        Text(
            text = "Pricing Settings",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        OutlinedTextField(
            value = tier1Rate,
            onValueChange = { tier1Rate = it },
            label = { Text("Tier 1 Rate (Up to 49,000 coins)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = tier2Rate,
            onValueChange = { tier2Rate = it },
            label = { Text("Tier 2 Rate (50,000-749,000 coins)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = tier3Rate,
            onValueChange = { tier3Rate = it },
            label = { Text("Tier 3 Rate (750,000+ coins)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // SMS Settings Section
        Text(
            text = "SMS Settings",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
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
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Timo App Settings Section
        Text(
            text = "Timo App Settings",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        OutlinedTextField(
            value = timoPackageName,
            onValueChange = { timoPackageName = it },
            label = { Text("Timo Package Name") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = rechargeButtonId,
            onValueChange = { rechargeButtonId = it },
            label = { Text("Recharge Button ID") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = timoIdInputId,
            onValueChange = { timoIdInputId = it },
            label = { Text("Timo ID Input Field ID") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = coinQuantityInputId,
            onValueChange = { coinQuantityInputId = it },
            label = { Text("Coin Quantity Input Field ID") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = confirmButtonId,
            onValueChange = { confirmButtonId = it },
            label = { Text("Confirm Button ID") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                viewModel.updateSettings(
                    settings.copy(
                        vodafoneWalletNumber = vodafoneWalletNumber,
                        adminPhoneNumber = adminPhoneNumber,
                        tier1Rate = tier1Rate.toDoubleOrNull() ?: settings.tier1Rate,
                        tier2Rate = tier2Rate.toDoubleOrNull() ?: settings.tier2Rate,
                        tier3Rate = tier3Rate.toDoubleOrNull() ?: settings.tier3Rate,
                        vodafoneSmsTemplate = vodafoneSmsTemplate,
                        vodafoneSenderNumbers = vodafoneSenderNumbers.split(",").map { it.trim() },
                        timoPackageName = timoPackageName,
                        rechargeButtonId = rechargeButtonId,
                        timoIdInputId = timoIdInputId,
                        coinQuantityInputId = coinQuantityInputId,
                        confirmButtonId = confirmButtonId
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Settings")
        }
    }
}
