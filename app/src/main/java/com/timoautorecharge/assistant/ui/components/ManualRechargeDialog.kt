package com.timoautorecharge.assistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.timoautorecharge.assistant.R
import com.timoautorecharge.assistant.ui.viewmodel.MainViewModel

/**
 * Dialog for manually creating a recharge transaction.
 * 
 * @param viewModel The MainViewModel instance
 * @param onDismiss Called when the dialog is dismissed
 */
@Composable
fun ManualRechargeDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.manual_recharge_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Timo ID Input
                OutlinedTextField(
                    value = viewModel.manualTimoId.value,
                    onValueChange = { viewModel.manualTimoId.value = it },
                    label = { Text(stringResource(R.string.timo_id)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Amount Input
                OutlinedTextField(
                    value = viewModel.manualAmount.value,
                    onValueChange = { viewModel.manualAmount.value = it },
                    label = { Text(stringResource(R.string.amount)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Sender Number Input
                OutlinedTextField(
                    value = viewModel.manualSenderNumber.value,
                    onValueChange = { viewModel.manualSenderNumber.value = it },
                    label = { Text(stringResource(R.string.sender_number)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            viewModel.createManualTransaction()
                        },
                        enabled = viewModel.manualTimoId.value.isNotEmpty() &&
                                viewModel.manualAmount.value.isNotEmpty() &&
                                viewModel.manualSenderNumber.value.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            }
        }
    }
}
