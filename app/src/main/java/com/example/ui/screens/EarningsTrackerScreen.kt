package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ActivityAuditLog
import com.example.data.model.BookingStatus
import com.example.data.model.CurrencyCode
import com.example.data.model.LiveChatMessage
import com.example.data.model.SessionBooking
import com.example.data.model.UserProfile
import com.example.ui.theme.ChampagneSecondary
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.TelemetryBlue
import com.example.ui.theme.TrustEmerald
import com.example.ui.viewmodel.DecryptedChatItem
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class ChartMetricMode(val label: String) {
    NET("Net Earnings"),
    GROSS("Gross Income"),
    ACTIVITY("Platform Activity")
}

private data class DailyPerformanceBucket(
    val dayKey: String,
    val shortLabel: String,
    val fullDateLabel: String,
    val grossCentsUsd: Long,
    val taxWithheldCentsUsd: Long,
    val netCentsUsd: Long,
    val sessionCount: Int,
    val sessionMinutes: Long,
    val messageCount: Int,
    val auditCount: Int
) {
    val totalActivityEvents: Int
        get() = sessionCount + messageCount + auditCount
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EarningsTrackerScreen(
    profileState: UiState<UserProfile?>,
    bookingsState: UiState<List<SessionBooking>>,
    messagesState: UiState<List<DecryptedChatItem>>,
    liveChatsState: UiState<List<LiveChatMessage>>,
    activityLogsState: UiState<List<ActivityAuditLog>>,
    onSelectCurrency: (CurrencyCode) -> Unit,
    onOpenLounge: () -> Unit,
    onOpenLedger: () -> Unit
) {
    val profile = (profileState as? UiState.Success)?.data
    val activeCurrency = CurrencyCode.fromRaw(profile?.preferredCurrency)
    val bookings = (bookingsState as? UiState.Success)?.data.orEmpty()
    val encryptedMessages = (messagesState as? UiState.Success)?.data.orEmpty()
    val liveChats = (liveChatsState as? UiState.Success)?.data.orEmpty()
    val activityLogs = (activityLogsState as? UiState.Success)?.data.orEmpty()

    var windowDays by rememberSaveable { mutableIntStateOf(7) }
    var metricMode by rememberSaveable { mutableStateOf(ChartMetricMode.NET) }

    val buckets = remember(windowDays, bookings, encryptedMessages, liveChats, activityLogs) {
        buildDailyPerformanceBuckets(
            days = windowDays,
            bookings = bookings,
            encryptedMessages = encryptedMessages,
            liveChats = liveChats,
            activityLogs = activityLogs
        )
    }

    var selectedIndex by rememberSaveable(windowDays) {
        mutableIntStateOf((windowDays - 1).coerceAtLeast(0))
    }
    LaunchedEffect(buckets.size) {
        if (selectedIndex !in buckets.indices && buckets.isNotEmpty()) {
            selectedIndex = buckets.lastIndex
        }
    }

    val totalGrossUsd = remember(buckets) { buckets.sumOf { it.grossCentsUsd } }
    val totalTaxUsd = remember(buckets) { buckets.sumOf { it.taxWithheldCentsUsd } }
    val totalNetUsd = remember(buckets) { buckets.sumOf { it.netCentsUsd } }
    val totalSessions = remember(buckets) { buckets.sumOf { it.sessionCount } }
    val totalMinutes = remember(buckets) { buckets.sumOf { it.sessionMinutes } }
    val totalMessages = remember(buckets) { buckets.sumOf { it.messageCount } }
    val totalAudits = remember(buckets) { buckets.sumOf { it.auditCount } }
    val totalActivity = remember(buckets) { buckets.sumOf { it.totalActivityEvents } }

    val completedOrSettledSessions = remember(bookings) {
        bookings.count {
            val st = BookingStatus.fromRaw(it.status)
            st == BookingStatus.COMPLETED || st == BookingStatus.PAYOUT_REQUESTED
        }
    }
    val peakDay = remember(buckets) {
        buckets.maxByOrNull { it.netCentsUsd }
    }
    val dailyAvgNetUsd = remember(totalNetUsd, windowDays) {
        if (windowDays > 0) totalNetUsd / windowDays else 0L
    }

    val selectedBucket = buckets.getOrNull(selectedIndex) ?: buckets.lastOrNull()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (bookingsState is UiState.Loading && bookings.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Syncing real-time earnings & activity streams…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (bookingsState is UiState.Error) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = bookingsState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            // 1. Live Earnings Hero Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.TrendingUp,
                                    contentDescription = null,
                                    tint = TrustEmerald,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Real-Time Earnings",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            Surface(
                                color = TrustEmerald.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(50)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(TrustEmerald)
                                    )
                                    Text(
                                        text = "Live Sync",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TrustEmerald
                                    )
                                }
                            }
                        }

                        Text(
                            text = activeCurrency.formatFromUsdCents(totalNetUsd),
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = TrustEmerald,
                            modifier = Modifier.testTag("earnings_total_net")
                        )
                        Text(
                            text = "Net ${windowDays}-day session income (${activeCurrency.formatFromUsdCents(totalGrossUsd)} gross • ${activeCurrency.formatFromUsdCents(totalTaxUsd)} 20% tax reserve)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            EarningsStatColumn(
                                label = "Daily Avg",
                                value = activeCurrency.formatFromUsdCents(dailyAvgNetUsd),
                                color = MaterialTheme.colorScheme.primary
                            )
                            EarningsStatColumn(
                                label = "Peak Day",
                                value = if ((peakDay?.netCentsUsd ?: 0L) > 0L) {
                                    "${peakDay?.shortLabel}: ${activeCurrency.formatFromUsdCents(peakDay?.netCentsUsd ?: 0L)}"
                                } else {
                                    "—"
                                },
                                color = ChampagneSecondary
                            )
                            EarningsStatColumn(
                                label = "Activity Events",
                                value = totalActivity.toString(),
                                color = TelemetryBlue
                            )
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(CurrencyCode.entries, key = { it.code }) { currency ->
                                FilterChip(
                                    selected = activeCurrency == currency,
                                    onClick = { onSelectCurrency(currency) },
                                    label = { Text("${currency.code} (${currency.symbol})") },
                                    modifier = Modifier.testTag("earnings_currency_${currency.code.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            // 2. Daily Performance Bar Chart Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.BarChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Daily Performance",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = windowDays == 7,
                                    onClick = { windowDays = 7 },
                                    label = { Text("7D") },
                                    modifier = Modifier.testTag("earnings_range_7d")
                                )
                                FilterChip(
                                    selected = windowDays == 14,
                                    onClick = { windowDays = 14 },
                                    label = { Text("14D") },
                                    modifier = Modifier.testTag("earnings_range_14d")
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = metricMode == ChartMetricMode.NET,
                                onClick = { metricMode = ChartMetricMode.NET },
                                label = { Text(ChartMetricMode.NET.label) },
                                modifier = Modifier.testTag("earnings_metric_net")
                            )
                            FilterChip(
                                selected = metricMode == ChartMetricMode.GROSS,
                                onClick = { metricMode = ChartMetricMode.GROSS },
                                label = { Text(ChartMetricMode.GROSS.label) },
                                modifier = Modifier.testTag("earnings_metric_gross")
                            )
                            FilterChip(
                                selected = metricMode == ChartMetricMode.ACTIVITY,
                                onClick = { metricMode = ChartMetricMode.ACTIVITY },
                                label = { Text(ChartMetricMode.ACTIVITY.label) },
                                modifier = Modifier.testTag("earnings_metric_activity")
                            )
                        }

                        DailyPerformanceBarChart(
                            buckets = buckets,
                            metricMode = metricMode,
                            currency = activeCurrency,
                            selectedIndex = selectedIndex,
                            onSelectBar = { index -> selectedIndex = index }
                        )

                        if (selectedBucket != null) {
                            SelectedDayDetailPanel(
                                bucket = selectedBucket,
                                currency = activeCurrency
                            )
                        }
                    }
                }
            }

            // 3. Income & Platform Activity Summary Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = ChampagneSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Sessions & Platform Activity Summary",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        SummaryMetricRow(
                            label = "Wellness Sessions ($windowDays days)",
                            value = "$totalSessions sessions • $totalMinutes min"
                        )
                        SummaryMetricRow(
                            label = "Completed / Settled Sessions",
                            value = "$completedOrSettledSessions of ${bookings.size} total"
                        )
                        SummaryMetricRow(
                            label = "Messaging Activity (Live + Cipher)",
                            value = "$totalMessages messages"
                        )
                        SummaryMetricRow(
                            label = "Compliance & EOA Audit Events",
                            value = "$totalAudits events"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Text(
                            text = "All earnings metrics update in real time from Firestore session and activity streams (Creator-Program-compliant estimator).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onOpenLounge,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("earnings_goto_lounge_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Spa,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Book Session")
                            }

                            OutlinedButton(
                                onClick = onOpenLedger,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("earnings_goto_ledger_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ReceiptLong,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Tax Ledger")
                            }
                        }
                    }
                }
            }

            // 4. Daily Ledger Breakdown Header
            item {
                Text(
                    text = "Daily Breakdown ($windowDays Days)",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            items(buckets.reversed(), key = { it.dayKey }) { day ->
                DailyBreakdownRowCard(
                    bucket = day,
                    currency = activeCurrency,
                    isSelected = day.dayKey == selectedBucket?.dayKey,
                    onClick = {
                        val idx = buckets.indexOfFirst { it.dayKey == day.dayKey }
                        if (idx >= 0) selectedIndex = idx
                    }
                )
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun DailyPerformanceBarChart(
    buckets: List<DailyPerformanceBucket>,
    metricMode: ChartMetricMode,
    currency: CurrencyCode,
    selectedIndex: Int,
    onSelectBar: (Int) -> Unit
) {
    val rawValues = remember(buckets, metricMode) {
        buckets.map { b ->
            when (metricMode) {
                ChartMetricMode.NET -> b.netCentsUsd.toFloat()
                ChartMetricMode.GROSS -> b.grossCentsUsd.toFloat()
                ChartMetricMode.ACTIVITY -> b.totalActivityEvents.toFloat()
            }
        }
    }
    val maxVal = remember(rawValues) {
        (rawValues.maxOrNull() ?: 0f).coerceAtLeast(1f)
    }
    val maxScaleLabel = remember(maxVal, metricMode, currency) {
        when (metricMode) {
            ChartMetricMode.NET, ChartMetricMode.GROSS -> {
                if (rawValues.all { it <= 0f }) currency.formatFromUsdCents(0L)
                else currency.formatFromUsdCents(maxVal.toLong())
            }
            ChartMetricMode.ACTIVITY -> "${maxVal.toInt()} events"
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val selectedColor = when (metricMode) {
        ChartMetricMode.NET -> TrustEmerald
        ChartMetricMode.GROSS -> ChampagneSecondary
        ChartMetricMode.ACTIVITY -> TelemetryBlue
    }
    val mutedBarColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("daily_performance_bar_chart"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Peak: $maxScaleLabel",
                style = MonoMetricStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Tap any bar for details",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
        ) {
            // Animated bar ratios
            val animatedRatios = rawValues.mapIndexed { idx, value ->
                val target = if (value > 0f) (value / maxVal).coerceIn(0.06f, 1f) else 0.03f
                val anim by animateFloatAsState(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                    label = "bar_anim_$idx"
                )
                anim
            }

            // Background Grid Canvas + Bars
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 26.dp, top = 6.dp)
            ) {
                val chartWidth = size.width
                val chartHeight = size.height
                if (chartWidth <= 0f || chartHeight <= 0f || buckets.isEmpty()) return@Canvas

                // 3 horizontal dashed grid lines (0%, 50%, 100%)
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                listOf(0f, 0.5f, 1f).forEach { fraction ->
                    val y = chartHeight * fraction
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1.5f,
                        pathEffect = if (fraction < 1f) dashEffect else null
                    )
                }

                val slotWidth = chartWidth / buckets.size
                val barWidth = (slotWidth * 0.56f).coerceIn(10f, 44f)

                buckets.forEachIndexed { idx, bucket ->
                    val ratio = animatedRatios[idx]
                    val hasValue = rawValues[idx] > 0f
                    val barHeight = (chartHeight * ratio).coerceAtLeast(6f)
                    val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                    val top = chartHeight - barHeight
                    val isSelected = idx == selectedIndex

                    val brush = if (!hasValue) {
                        Brush.verticalGradient(listOf(mutedBarColor, mutedBarColor))
                    } else if (isSelected) {
                        Brush.verticalGradient(listOf(selectedColor, selectedColor.copy(alpha = 0.72f)))
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                selectedColor.copy(alpha = 0.78f),
                                primaryColor.copy(alpha = 0.52f)
                            )
                        )
                    }

                    drawRoundRect(
                        brush = brush,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(10f, 10f)
                    )

                    if (isSelected) {
                        drawRoundRect(
                            color = selectedColor,
                            topLeft = Offset(left - 2f, top - 2f),
                            size = Size(barWidth + 4f, barHeight + 4f),
                            cornerRadius = CornerRadius(12f, 12f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                        )
                    }
                }
            }

            // Invisible touch columns + X-axis day labels aligned with bars
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Bottom
            ) {
                buckets.forEachIndexed { idx, bucket ->
                    val isSelected = idx == selectedIndex
                    val showLabel = buckets.size <= 7 || idx % 2 == 1 || isSelected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSelectBar(idx) }
                            .semantics {
                                contentDescription = "${bucket.fullDateLabel}, net ${currency.formatFromUsdCents(bucket.netCentsUsd)}, ${bucket.totalActivityEvents} platform activities"
                            }
                            .testTag("earnings_bar_${bucket.dayKey}"),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (showLabel) {
                            Text(
                                text = bucket.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedDayDetailPanel(
    bucket: DailyPerformanceBucket,
    currency: CurrencyCode
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("selected_day_detail_panel")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = bucket.fullDateLabel,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Text(
                    text = currency.formatFromUsdCents(bucket.netCentsUsd),
                    style = MonoMetricStyle.copy(fontWeight = FontWeight.Bold),
                    color = TrustEmerald
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                EarningsStatColumn(
                    label = "Gross",
                    value = currency.formatFromUsdCents(bucket.grossCentsUsd),
                    color = MaterialTheme.colorScheme.onSurface
                )
                EarningsStatColumn(
                    label = "Tax Reserve",
                    value = currency.formatFromUsdCents(bucket.taxWithheldCentsUsd),
                    color = ChampagneSecondary
                )
                EarningsStatColumn(
                    label = "Sessions",
                    value = "${bucket.sessionCount} (${bucket.sessionMinutes}m)",
                    color = MaterialTheme.colorScheme.primary
                )
                EarningsStatColumn(
                    label = "Activity",
                    value = "${bucket.totalActivityEvents} events",
                    color = TelemetryBlue
                )
            }
        }
    }
}

@Composable
private fun DailyBreakdownRowCard(
    bucket: DailyPerformanceBucket,
    currency: CurrencyCode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = bucket.fullDateLabel,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "${bucket.sessionCount} sessions • ${bucket.messageCount} chats • ${bucket.auditCount} audits",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = currency.formatFromUsdCents(bucket.netCentsUsd),
                    style = MonoMetricStyle.copy(fontWeight = FontWeight.Bold),
                    color = if (bucket.netCentsUsd > 0L) TrustEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Gross ${currency.formatFromUsdCents(bucket.grossCentsUsd)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EarningsStatColumn(
    label: String,
    value: String,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
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
private fun SummaryMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MonoMetricStyle.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun buildDailyPerformanceBuckets(
    days: Int,
    bookings: List<SessionBooking>,
    encryptedMessages: List<DecryptedChatItem>,
    liveChats: List<LiveChatMessage>,
    activityLogs: List<ActivityAuditLog>
): List<DailyPerformanceBucket> {
    val safeDays = days.coerceIn(1, 30)
    val keyFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val shortFormatter = SimpleDateFormat("EEE", Locale.US)
    val shortDateFormatter = SimpleDateFormat("M/d", Locale.US)
    val fullFormatter = SimpleDateFormat("EEE, MMM d", Locale.US)

    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)

    val todayKey = keyFormatter.format(cal.time)
    val dayEntries = mutableListOf<Triple<String, String, String>>()
    for (offset in (safeDays - 1) downTo 0) {
        val dayCal = cal.clone() as Calendar
        dayCal.add(Calendar.DAY_OF_YEAR, -offset)
        val date = dayCal.time
        val key = keyFormatter.format(date)
        val shortLabel = when {
            offset == 0 -> "Today"
            safeDays <= 7 -> shortFormatter.format(date)
            else -> shortDateFormatter.format(date)
        }
        val fullLabel = if (offset == 0) {
            "Today • ${fullFormatter.format(date)}"
        } else {
            fullFormatter.format(date)
        }
        dayEntries.add(Triple(key, shortLabel, fullLabel))
    }

    val bookingsByDay = bookings.groupBy { booking ->
        booking.createdAt?.toDate()?.let { keyFormatter.format(it) } ?: todayKey
    }
    val encryptedByDay = encryptedMessages.groupBy { msg ->
        if (msg.timestampSeconds > 0L) keyFormatter.format(Date(msg.timestampSeconds * 1000L)) else todayKey
    }
    val liveByDay = liveChats.groupBy { msg ->
        msg.createdAt?.toDate()?.let { keyFormatter.format(it) } ?: todayKey
    }
    val auditsByDay = activityLogs.groupBy { log ->
        log.createdAt?.toDate()?.let { keyFormatter.format(it) } ?: todayKey
    }

    return dayEntries.map { (key, shortLabel, fullLabel) ->
        val dayBookings = bookingsByDay[key].orEmpty()
        val gross = dayBookings.sumOf { it.grossCentsUsd }
        val tax = dayBookings.sumOf { it.taxWithheldCentsUsd }
        val net = (gross - tax).coerceAtLeast(0L)
        val minutes = dayBookings.sumOf { it.durationMinutes }
        val msgCount = encryptedByDay[key].orEmpty().size + liveByDay[key].orEmpty().size
        val auditCount = auditsByDay[key].orEmpty().size

        DailyPerformanceBucket(
            dayKey = key,
            shortLabel = shortLabel,
            fullDateLabel = fullLabel,
            grossCentsUsd = gross,
            taxWithheldCentsUsd = tax,
            netCentsUsd = net,
            sessionCount = dayBookings.size,
            sessionMinutes = minutes,
            messageCount = msgCount,
            auditCount = auditCount
        )
    }
}
