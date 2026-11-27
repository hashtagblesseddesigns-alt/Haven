package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.BookingStatus
import com.example.data.model.CurrencyCode
import com.example.data.model.SessionBooking
import com.example.data.model.UserProfile
import com.example.ui.theme.ChampagneSecondary
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.TelemetryBlue
import com.example.ui.theme.TrustEmerald
import com.example.ui.viewmodel.AiTaskState
import com.example.ui.viewmodel.UiState

@Composable
fun LedgerScreen(
    profileState: UiState<UserProfile?>,
    bookingsState: UiState<List<SessionBooking>>,
    taxReportState: AiTaskState,
    onSelectCurrency: (CurrencyCode) -> Unit,
    onAdvanceBooking: (SessionBooking) -> Unit,
    onGenerateTaxReport: () -> Unit,
    onOpenEarningsChart: () -> Unit = {}
) {
    val profile = (profileState as? UiState.Success)?.data
    val activeCurrency = CurrencyCode.fromRaw(profile?.preferredCurrency)
    val bookings = (bookingsState as? UiState.Success)?.data.orEmpty()

    val totalGrossUsd = bookings.sumOf { it.grossCentsUsd }
    val totalTaxUsd = bookings.sumOf { it.taxWithheldCentsUsd }
    val totalNetUsd = (totalGrossUsd - totalTaxUsd).coerceAtLeast(0L)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Multi-Currency Selector
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Multi-Currency Settlement Display",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CurrencyCode.entries, key = { it.code }) { currency ->
                        FilterChip(
                            selected = activeCurrency == currency,
                            onClick = { onSelectCurrency(currency) },
                            label = { Text("${currency.code} (${currency.symbol})") }
                        )
                    }
                }
            }
        }

        // Earnings & Tax Reserve Summary Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AccountBalanceWallet,
                            contentDescription = null,
                            tint = ChampagneSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Session Earnings & Tax Ledger",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LedgerMetricCol(
                            label = "Gross Volume",
                            value = activeCurrency.formatFromUsdCents(totalGrossUsd),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LedgerMetricCol(
                            label = "Tax Reserve (20%)",
                            value = activeCurrency.formatFromUsdCents(totalTaxUsd),
                            color = ChampagneSecondary
                        )
                        LedgerMetricCol(
                            label = "Net Estimate",
                            value = activeCurrency.formatFromUsdCents(totalNetUsd),
                            color = TrustEmerald
                        )
                    }

                    Text(
                        text = "Creator-Program-compliant PG-13 wellness session & tax ledger (multi-currency estimator without live bank, card, PayPal/Cash App/Venmo, or Web3 transfers).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = onGenerateTaxReport,
                        enabled = taxReportState !is AiTaskState.Running,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("generate_tax_report_button")
                    ) {
                        if (taxReportState is AiTaskState.Running) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Compiling Tax & GDPR Brief...")
                        } else {
                            Icon(Icons.Rounded.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Generate AI Tax & Compliance Brief")
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenEarningsChart,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("ledger_open_earnings_chart_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ShowChart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open Earnings Dashboard & Charts")
                    }

                    AnimatedVisibility(visible = taxReportState is AiTaskState.Success || taxReportState is AiTaskState.Error) {
                        val isError = taxReportState is AiTaskState.Error
                        val text = when (taxReportState) {
                            is AiTaskState.Success -> taxReportState.message
                            is AiTaskState.Error -> taxReportState.message
                            else -> ""
                        }
                        Surface(
                            color = if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bookings List Header
        item {
            Text(
                text = "Verified Sessions (${bookings.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (bookingsState is UiState.Error) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = bookingsState.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        } else if (bookings.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "No Wellness Sessions Booked Yet",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Visit the Lounge tab to book a verified platonic session and track real-time multi-currency earnings & tax reserves.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(bookings, key = { it.id }) { booking ->
                BookingLedgerCard(
                    booking = booking,
                    displayCurrency = activeCurrency,
                    onAdvance = { onAdvanceBooking(booking) }
                )
            }
        }
    }
}

@Composable
private fun LedgerMetricCol(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MonoMetricStyle.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
private fun BookingLedgerCard(
    booking: SessionBooking,
    displayCurrency: CurrencyCode,
    onAdvance: () -> Unit
) {
    val status = BookingStatus.fromRaw(booking.status)
    val nextActionLabel = when (status) {
        BookingStatus.CONFIRMED -> "Start Session"
        BookingStatus.IN_SESSION -> "Complete"
        BookingStatus.COMPLETED -> "Mark Settled"
        BookingStatus.PAYOUT_REQUESTED -> null
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = booking.companionName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${booking.sessionTitle} • ${booking.durationMinutes} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = when (status) {
                        BookingStatus.PAYOUT_REQUESTED -> TrustEmerald.copy(alpha = 0.16f)
                        BookingStatus.COMPLETED -> TelemetryBlue.copy(alpha = 0.16f)
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = status.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (status) {
                            BookingStatus.PAYOUT_REQUESTED -> TrustEmerald
                            BookingStatus.COMPLETED -> TelemetryBlue
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = booking.scheduledLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${displayCurrency.formatFromUsdCents(booking.grossCentsUsd)} gross • ${displayCurrency.formatFromUsdCents(booking.taxWithheldCentsUsd)} tax",
                        style = MonoMetricStyle,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (nextActionLabel != null) {
                    FilledTonalButton(
                        onClick = onAdvance,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(nextActionLabel)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Verified,
                            contentDescription = "Settled",
                            tint = TrustEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Locked",
                            style = MaterialTheme.typography.labelMedium,
                            color = TrustEmerald
                        )
                    }
                }
            }
        }
    }
}
