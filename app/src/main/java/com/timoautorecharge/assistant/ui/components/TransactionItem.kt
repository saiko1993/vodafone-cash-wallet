package com.timoautorecharge.assistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.timoautorecharge.assistant.R
import com.timoautorecharge.assistant.data.model.Transaction
import com.timoautorecharge.assistant.data.model.TransactionStatus
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TransactionItem(
    transaction: Transaction,
    onRechargeClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ID: ${transaction.timoId}",
                    style = MaterialTheme.typography.titleMedium
                )
                StatusChip(status = transaction.status)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Amount: ${transaction.requestAmount} EGP",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Text(
                text = "Coins: ${transaction.coinQuantity}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Text(
                text = "From: ${transaction.senderNumber}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Text(
                text = "Requested: ${formatDate(transaction.requestTimestamp)}",
                style = MaterialTheme.typography.bodySmall
            )
            
            if (transaction.paymentVerified && transaction.paymentTimestamp != null) {
                Text(
                    text = "Payment verified: ${formatDate(transaction.paymentTimestamp)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            if (transaction.rechargeCompleted && transaction.rechargeTimestamp != null) {
                Text(
                    text = "Recharged: ${formatDate(transaction.rechargeTimestamp)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            if (!transaction.rechargeCompleted && transaction.status == TransactionStatus.PAYMENT_VERIFIED) {
                Button(
                    onClick = onRechargeClick,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.recharge_now))
                }
            }
        }
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
        shape = MaterialTheme.shapes.small
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
