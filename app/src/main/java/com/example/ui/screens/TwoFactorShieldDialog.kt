package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.CompanionProfile
import com.example.data.model.CurrencyCode
import com.example.ui.theme.TrustEmerald

@Composable
fun TwoFactorShieldDialog(
    hasConfiguredPin: Boolean,
    onDismiss: () -> Unit,
    onSubmitPin: (pin: String, isConfiguring: Boolean, onError: () -> Unit) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Security,
                contentDescription = "Two-factor security shield",
                tint = TrustEmerald,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = if (hasConfiguredPin) "Verify 2FA Shield" else "Activate 2FA Shield",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (hasConfiguredPin) {
                        "Enter your 6-digit sanctuary passcode to confirm elevated identity trust."
                    } else {
                        "Set a 6-digit passcode to upgrade your profile to VERIFIED_2FA and harden E2E sessions."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { input ->
                        if (input.length <= 6 && input.all { it.isDigit() }) {
                            pin = input
                            errorText = null
                        }
                    },
                    label = { Text("6-digit 2FA passcode") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = errorText != null,
                    supportingText = errorText?.let { msg -> { Text(msg) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("two_factor_pin_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin.length != 6) {
                        errorText = "Enter all 6 digits."
                    } else {
                        onSubmitPin(pin, !hasConfiguredPin) {
                            errorText = "Passcode did not match. Try again."
                        }
                    }
                },
                modifier = Modifier.testTag("two_factor_confirm_button")
            ) {
                Text(if (hasConfiguredPin) "Verify" else "Activate 2FA")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BookSessionDialog(
    companion: CompanionProfile,
    preferredCurrency: CurrencyCode,
    onDismiss: () -> Unit,
    onConfirmBooking: (sessionTitle: String, durationMinutes: Int, scheduledLabel: String) -> Unit
) {
    var selectedFormat by remember {
        mutableStateOf(companion.specialties.firstOrNull() ?: "Platonic Cuddle Therapy")
    }
    var durationMinutes by remember { mutableIntStateOf(60) }
    var scheduledSlot by remember { mutableStateOf("Tonight • 7:30 PM") }

    val estimatedCentsUsd = (companion.hourlyRateCentsUsd * durationMinutes) / 60L

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = "Schedule wellness session",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Book ${companion.name}",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Platonic PG-13 Wellness Session • ${companion.city}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Session Format", style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    companion.specialties.forEach { specialty ->
                        FilterChip(
                            selected = selectedFormat == specialty,
                            onClick = { selectedFormat = specialty },
                            label = { Text(specialty, style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Text("Duration", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(30, 60, 90, 120).forEach { mins ->
                        FilterChip(
                            selected = durationMinutes == mins,
                            onClick = { durationMinutes = mins },
                            label = { Text("${mins}m") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = scheduledSlot,
                    onValueChange = { if (it.length <= 60) scheduledSlot = it },
                    label = { Text("Preferred Window") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ledger Estimate (${preferredCurrency.code})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = preferredCurrency.formatFromUsdCents(estimatedCentsUsd),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmBooking(selectedFormat, durationMinutes, scheduledSlot) },
                modifier = Modifier.testTag("confirm_booking_button")
            ) {
                Text("Confirm Session")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
