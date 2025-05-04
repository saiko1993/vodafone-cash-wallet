package com.timoautorecharge.assistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.timoautorecharge.assistant.R
import com.timoautorecharge.assistant.data.model.Transaction
import com.timoautorecharge.assistant.data.model.TransactionStatus
import java.text.SimpleDateFormat
import java.util.*

/**
 * A component that displays transaction information with optional action buttons.
 * 
 * @param transaction The transaction to display
 * @param onRechargeClick Called when the recharge button is clicked
 * @param onVerifyClick Called when the verify button is clicked
 * @param onCancelClick Called when the cancel button is clicked
 * @param expanded Whether to show the expanded view with more details
 */
@Composable
fun TransactionItem(
    transaction: Transaction,
    onRechargeClick: () -> Unit,
    onVerifyClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
    expanded: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(expanded) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header Row with ID, Status, and Expand/Collapse Icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ID: ${transaction.timoId}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "${transaction.requestAmount} EGP → ${transaction.coinQuantity} coins",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    StatusChip(status = transaction.status)
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Expanded Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // Transaction Details
                    TransactionDetailItem(
                        label = "From",
                        value = transaction.senderNumber
                    )
                    
                    TransactionDetailItem(
                        label = "Requested",
                        value = formatDate(transaction.requestTimestamp)
                    )
                    
                    if (transaction.paymentVerified && transaction.paymentTimestamp != null) {
                        TransactionDetailItem(
                            label = "Payment Verified",
                            value = formatDate(transaction.paymentTimestamp)
                        )
                    }
                    
                    if (transaction.rechargeCompleted && transaction.rechargeTimestamp != null) {
                        TransactionDetailItem(
                            label = "Recharged",
                            value = formatDate(transaction.rechargeTimestamp)
                        )
                    }
                    
                    if (transaction.notes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Notes:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = transaction.notes,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    // Action Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        // Show different buttons based on transaction status
                        when (transaction.status) {
                            TransactionStatus.PENDING_PAYMENT -> {
                                OutlinedButton(
                                    onClick = onVerifyClick,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(stringResource(R.string.verify_payment))
                                }
                                
                                OutlinedButton(
                                    onClick = onCancelClick,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                            
                            TransactionStatus.PAYMENT_VERIFIED -> {
                                Button(
                                    onClick = onRechargeClick
                                ) {
                                    Text(stringResource(R.string.recharge_now))
                                }
                            }
                            
                            TransactionStatus.PAYMENT_TIMEOUT, 
                            TransactionStatus.MANUAL_INTERVENTION_REQUIRED -> {
                                OutlinedButton(
                                    onClick = onVerifyClick,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(stringResource(R.string.verify_payment))
                                }
                                
                                OutlinedButton(
                                    onClick = onCancelClick,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text(stringResource(R.string.cancel))
                                }
                            }
                            
                            TransactionStatus.RECHARGE_FAILED -> {
                                Button(
                                    onClick = onRechargeClick
                                ) {
                                    Text(stringResource(R.string.retry_recharge))
                                }
                            }
                            
                            else -> { /* No buttons for other statuses */ }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionDetailItem(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(100.dp)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun StatusChip(status: TransactionStatus) {
    val (color, text) = when (status) {
        TransactionStatus.PENDING_PAYMENT -> Pair(MaterialTheme.colorScheme.primary, "Pending Payment")
        TransactionStatus.PAYMENT_VERIFIED -> Pair(MaterialTheme.colorScheme.secondary, "Payment Verified")
        TransactionStatus.PAYMENT_TIMEOUT -> Pair(MaterialTheme.colorScheme.error, "Payment Timeout")
        TransactionStatus.RECHARGE_IN_PROGRESS -> Pair(MaterialTheme.colorScheme.tertiary, "Recharging")
        TransactionStatus.RECHARGE_COMPLETED -> Pair(MaterialTheme.colorScheme.primary, "Completed")
        TransactionStatus.RECHARGE_FAILED -> Pair(MaterialTheme.colorScheme.error, "Failed")
        TransactionStatus.MANUAL_INTERVENTION_REQUIRED -> Pair(MaterialTheme.colorScheme.error, "Manual Required")
    }
    
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun formatDate(timestamp: Long?): String {
    if (timestamp == null) return ""
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
