package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BookingStatus
import com.example.data.model.CurrencyCode
import com.example.data.model.SessionBooking
import com.example.data.model.UserProfile
import com.example.ui.theme.ChampagneSecondary
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.TelemetryBlue
import com.example.ui.theme.TrustEmerald
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

enum class DashboardRange(val label: String, val subtitlePrefix: String) {
    CURRENT_WEEK("Current Week", "This week"),
    CURRENT_MONTH("Current Month", "This month"),
    CURRENT_YEAR("Current Year", "This year")
}

enum class DashboardTrendMode(val label: String) {
    CUMULATIVE("Cumulative Growth"),
    DAILY("Period Breakdown")
}

data class EarningsTimePoint(
    val dayKey: String,
    val shortLabel: String,
    val fullDateLabel: String,
    val dailyGrossCentsUsd: Long,
    val dailyTaxCentsUsd: Long,
    val dailyNetCentsUsd: Long,
    val cumulativeGrossCentsUsd: Long,
    val cumulativeTaxCentsUsd: Long,
    val cumulativeNetCentsUsd: Long,
    val sessionCount: Int,
    val sessionMinutes: Long
)

data class CompanionEarningsSummary(
    val companionId: String,
    val companionName: String,
    val sessionCount: Int,
    val totalMinutes: Long,
    val grossCentsUsd: Long,
    val netCentsUsd: Long,
    val shareFraction: Float
)

data class EarningsDashboardAggregation(
    val periodSubtitle: String,
    val points: List<EarningsTimePoint>,
    val defaultSelectedIndex: Int,
    val totalGrossCentsUsd: Long,
    val totalTaxCentsUsd: Long,
    val totalNetCentsUsd: Long,
    val settledNetCentsUsd: Long,
    val activeEscrowNetCentsUsd: Long,
    val totalSessions: Int,
    val totalMinutes: Long,
    val avgNetPerSessionUsd: Long,
    val companionBreakdown: List<CompanionEarningsSummary>
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EarningsDashboard(
    profileState: UiState<UserProfile?>,
    bookingsState: UiState<List<SessionBooking>>,
    onSelectCurrency: (CurrencyCode) -> Unit,
    onOpenLounge: () -> Unit,
    onOpenLedger: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = (profileState as? UiState.Success)?.data
    val activeCurrency = CurrencyCode.fromRaw(profile?.preferredCurrency)
    val bookings = (bookingsState as? UiState.Success)?.data.orEmpty()

    var selectedRange by rememberSaveable { mutableStateOf(DashboardRange.CURRENT_MONTH) }
    var trendMode by rememberSaveable { mutableStateOf(DashboardTrendMode.CUMULATIVE) }

    val aggregation = remember(bookings, selectedRange) {
        aggregateSessionEarningsTimeline(
            bookings = bookings,
            range = selectedRange
        )
    }

    var selectedPointIndex by rememberSaveable(selectedRange) {
        mutableIntStateOf(aggregation.defaultSelectedIndex)
    }
    LaunchedEffect(aggregation.points.size) {
        if (aggregation.points.isNotEmpty() && selectedPointIndex !in aggregation.points.indices) {
            selectedPointIndex = aggregation.defaultSelectedIndex.coerceIn(0, aggregation.points.lastIndex)
        }
    }

    val selectedPoint = aggregation.points.getOrNull(selectedPointIndex) ?: aggregation.points.lastOrNull()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .testTag("earnings_dashboard_list"),
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
                            Text(
                                text = "Aggregating Firestore session earnings…",
                                style = MaterialTheme.typography.bodyMedium
                            )
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

            // 1. Hero Aggregated Earnings Summary Card with Period Filter Dropdown
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ShowChart,
                                    contentDescription = null,
                                    tint = TrustEmerald,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = "Earnings Over Time",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = aggregation.periodSubtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.testTag("dashboard_period_subtitle")
                                    )
                                }
                            }

                            Spacer(Modifier.width(8.dp))

                            EarningsPeriodFilterDropdown(
                                selectedRange = selectedRange,
                                onSelectRange = { range -> selectedRange = range }
                            )
                        }

                        Text(
                            text = activeCurrency.formatFromUsdCents(aggregation.totalNetCentsUsd),
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = TrustEmerald,
                            modifier = Modifier.testTag("dashboard_total_net_earnings")
                        )

                        Text(
                            text = "${selectedRange.label} net earnings • ${activeCurrency.formatFromUsdCents(aggregation.totalGrossCentsUsd)} gross • ${activeCurrency.formatFromUsdCents(aggregation.totalTaxCentsUsd)} 20% tax reserve",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            DashboardStatColumn(
                                label = "Settled Net",
                                value = activeCurrency.formatFromUsdCents(aggregation.settledNetCentsUsd),
                                color = TrustEmerald
                            )
                            DashboardStatColumn(
                                label = "Active Escrow",
                                value = activeCurrency.formatFromUsdCents(aggregation.activeEscrowNetCentsUsd),
                                color = ChampagneSecondary
                            )
                            DashboardStatColumn(
                                label = "Avg / Session",
                                value = activeCurrency.formatFromUsdCents(aggregation.avgNetPerSessionUsd),
                                color = TelemetryBlue
                            )
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(CurrencyCode.entries, key = { it.code }) { currency ->
                                FilterChip(
                                    selected = activeCurrency == currency,
                                    onClick = { onSelectCurrency(currency) },
                                    label = { Text("${currency.code} (${currency.symbol})") },
                                    modifier = Modifier.testTag("dashboard_currency_${currency.code.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            // 2. Interactive Line Chart Card
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Revenue Trajectory",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${aggregation.totalSessions} sessions • ${aggregation.totalMinutes} total min",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                color = TrustEmerald.copy(alpha = 0.14f),
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
                                        text = "Firestore Live",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TrustEmerald
                                    )
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DashboardTrendMode.entries.forEach { mode ->
                                val label = if (mode == DashboardTrendMode.DAILY) {
                                    if (selectedRange == DashboardRange.CURRENT_YEAR) "Monthly Earnings" else "Daily Earnings"
                                } else {
                                    mode.label
                                }
                                FilterChip(
                                    selected = trendMode == mode,
                                    onClick = { trendMode = mode },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("dashboard_mode_${mode.name.lowercase()}")
                                )
                            }
                        }

                        // Chart Legend
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LegendDot(color = TrustEmerald, label = "Net Earnings")
                            LegendDot(color = ChampagneSecondary, label = "Gross Volume (dashed)")
                        }

                        if (aggregation.totalSessions == 0) {
                            DashboardEmptyChartState(
                                periodLabel = selectedRange.label,
                                onOpenLounge = onOpenLounge
                            )
                        } else {
                            EarningsOverTimeLineChart(
                                points = aggregation.points,
                                trendMode = trendMode,
                                currency = activeCurrency,
                                selectedIndex = selectedPointIndex,
                                onSelectPoint = { idx -> selectedPointIndex = idx }
                            )

                            if (selectedPoint != null) {
                                SelectedTimePointCard(
                                    point = selectedPoint,
                                    trendMode = trendMode,
                                    isYearly = selectedRange == DashboardRange.CURRENT_YEAR,
                                    currency = activeCurrency
                                )
                            }
                        }
                    }
                }
            }

            // 3. Companion Session Aggregation Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(24.dp),
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
                                imageVector = Icons.Rounded.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Session Revenue by Companion",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        if (aggregation.companionBreakdown.isEmpty()) {
                            Text(
                                text = "Book a companion session from the Sanctuary Lounge to populate companion revenue shares for ${selectedRange.subtitlePrefix.lowercase()}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            aggregation.companionBreakdown.forEach { item ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${item.companionName} (${item.sessionCount}x • ${item.totalMinutes}m)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = activeCurrency.formatFromUsdCents(item.netCentsUsd),
                                            style = MonoMetricStyle.copy(fontWeight = FontWeight.Bold),
                                            color = TrustEmerald
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { item.shareFraction.coerceIn(0.05f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = TrustEmerald,
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onOpenLounge,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("dashboard_book_session_button")
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
                                    .testTag("dashboard_open_ledger_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
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

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun EarningsPeriodFilterDropdown(
    selectedRange: DashboardRange,
    onSelectRange: (DashboardRange) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "dropdown_arrow_rotation"
    )

    Box {
        FilledTonalButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics {
                    contentDescription = "Filter earnings timeframe: ${selectedRange.label}"
                }
                .testTag("dashboard_period_dropdown_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = selectedRange.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(arrowRotation)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.testTag("dashboard_period_dropdown_menu")
        ) {
            DashboardRange.entries.forEach { range ->
                val isSelected = range == selectedRange
                DropdownMenuItem(
                    text = {
                        Text(
                            text = range.label,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isSelected) Icons.Rounded.Check else Icons.Rounded.CalendarToday,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelectRange(range)
                    },
                    modifier = Modifier.testTag("dashboard_period_option_${range.name.lowercase()}")
                )
            }
        }
    }
}

@Composable
private fun EarningsOverTimeLineChart(
    points: List<EarningsTimePoint>,
    trendMode: DashboardTrendMode,
    currency: CurrencyCode,
    selectedIndex: Int,
    onSelectPoint: (Int) -> Unit
) {
    val netSeries = remember(points, trendMode) {
        points.map { pt ->
            if (trendMode == DashboardTrendMode.CUMULATIVE) {
                pt.cumulativeNetCentsUsd.toFloat()
            } else {
                pt.dailyNetCentsUsd.toFloat()
            }
        }
    }
    val grossSeries = remember(points, trendMode) {
        points.map { pt ->
            if (trendMode == DashboardTrendMode.CUMULATIVE) {
                pt.cumulativeGrossCentsUsd.toFloat()
            } else {
                pt.dailyGrossCentsUsd.toFloat()
            }
        }
    }

    val maxScaleCents = remember(netSeries, grossSeries) {
        maxOf(
            netSeries.maxOrNull() ?: 0f,
            grossSeries.maxOrNull() ?: 0f,
            100f
        )
    }

    val revealProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "line_chart_reveal"
    )

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val surfaceDotBorder = MaterialTheme.colorScheme.surfaceContainer

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("earnings_line_chart"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Scale Max: ${currency.formatFromUsdCents(maxScaleCents.toLong())}",
                style = MonoMetricStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Tap or drag to inspect",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .semantics {
                    contentDescription = "Earnings over time line chart across ${points.size} intervals"
                }
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        if (points.isNotEmpty() && size.width > 0) {
                            val idx = ((offset.x / size.width.toFloat()) * (points.size - 1))
                                .roundToInt()
                                .coerceIn(0, points.lastIndex)
                            onSelectPoint(idx)
                        }
                    }
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        if (points.isNotEmpty() && size.width > 0) {
                            val idx = ((change.position.x / size.width.toFloat()) * (points.size - 1))
                                .roundToInt()
                                .coerceIn(0, points.lastIndex)
                            onSelectPoint(idx)
                        }
                    }
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp, bottom = 26.dp, start = 6.dp, end = 6.dp)
            ) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f || points.isEmpty()) return@Canvas

                // Dashed horizontal grid lines (0%, 50%, 100%)
                val gridDash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                listOf(0f, 0.5f, 1f).forEach { frac ->
                    val y = h * frac
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.5f,
                        pathEffect = if (frac < 1f) gridDash else null
                    )
                }

                fun buildCoords(values: List<Float>): List<Offset> {
                    if (values.size == 1) {
                        val y = h - (values[0] / maxScaleCents).coerceIn(0f, 1f) * h
                        return listOf(Offset(w / 2f, y))
                    }
                    val stepX = w / (values.size - 1).toFloat()
                    return values.mapIndexed { idx, v ->
                        val x = idx * stepX
                        val ratio = (v / maxScaleCents).coerceIn(0f, 1f)
                        val y = h - (ratio * h)
                        Offset(x, y)
                    }
                }

                fun buildSmoothPath(coords: List<Offset>): Path {
                    val path = Path()
                    if (coords.isEmpty()) return path
                    path.moveTo(coords[0].x, coords[0].y)
                    for (i in 0 until coords.lastIndex) {
                        val p0 = coords[i]
                        val p1 = coords[i + 1]
                        val ctrlX = (p0.x + p1.x) / 2f
                        path.cubicTo(ctrlX, p0.y, ctrlX, p1.y, p1.x, p1.y)
                    }
                    return path
                }

                val netCoords = buildCoords(netSeries)
                val grossCoords = buildCoords(grossSeries)
                val netPath = buildSmoothPath(netCoords)
                val grossPath = buildSmoothPath(grossCoords)

                val areaPath = Path().apply {
                    addPath(netPath)
                    lineTo(netCoords.last().x, h)
                    lineTo(netCoords.first().x, h)
                    close()
                }

                clipRect(right = w * revealProgress) {
                    // Area gradient beneath Net Earnings curve
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                TrustEmerald.copy(alpha = 0.28f),
                                TrustEmerald.copy(alpha = 0.02f)
                            ),
                            startY = 0f,
                            endY = h
                        )
                    )

                    // Gross comparison line (dashed champagne)
                    drawPath(
                        path = grossPath,
                        color = ChampagneSecondary.copy(alpha = 0.85f),
                        style = Stroke(
                            width = 4f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                        )
                    )

                    // Primary Net Earnings line
                    drawPath(
                        path = netPath,
                        color = TrustEmerald,
                        style = Stroke(
                            width = 6f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Subtle data points along Net curve
                    netCoords.forEachIndexed { idx, pt ->
                        if (idx == selectedIndex || points.size <= 14) {
                            drawCircle(
                                color = surfaceDotBorder,
                                radius = if (idx == selectedIndex) 9f else 5f,
                                center = pt
                            )
                            drawCircle(
                                color = TrustEmerald,
                                radius = if (idx == selectedIndex) 6f else 3.5f,
                                center = pt
                            )
                        }
                    }
                }

                // Active inspection crosshair
                val safeIdx = selectedIndex.coerceIn(0, netCoords.lastIndex)
                val activeNet = netCoords[safeIdx]
                val activeGross = grossCoords[safeIdx]
                drawLine(
                    color = TelemetryBlue.copy(alpha = 0.65f),
                    start = Offset(activeNet.x, 0f),
                    end = Offset(activeNet.x, h),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
                drawCircle(color = surfaceDotBorder, radius = 10f, center = activeGross)
                drawCircle(color = ChampagneSecondary, radius = 6.5f, center = activeGross)
                drawCircle(color = surfaceDotBorder, radius = 12f, center = activeNet)
                drawCircle(color = TrustEmerald, radius = 8f, center = activeNet)
            }

            // X-Axis Labels aligned along bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val first = points.firstOrNull()
                val mid = points.getOrNull(points.size / 2)
                val last = points.lastOrNull()
                if (first != null) {
                    Text(
                        text = first.shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (mid != null && points.size >= 3) {
                    Text(
                        text = mid.shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (last != null && points.size >= 2) {
                    Text(
                        text = last.shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedTimePointCard(
    point: EarningsTimePoint,
    trendMode: DashboardTrendMode,
    isYearly: Boolean,
    currency: CurrencyCode
) {
    val netValue = if (trendMode == DashboardTrendMode.CUMULATIVE) {
        point.cumulativeNetCentsUsd
    } else {
        point.dailyNetCentsUsd
    }
    val grossValue = if (trendMode == DashboardTrendMode.CUMULATIVE) {
        point.cumulativeGrossCentsUsd
    } else {
        point.dailyGrossCentsUsd
    }
    val taxValue = if (trendMode == DashboardTrendMode.CUMULATIVE) {
        point.cumulativeTaxCentsUsd
    } else {
        point.dailyTaxCentsUsd
    }

    val periodNoun = if (isYearly) "Monthly" else "Daily"

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_tooltip_card")
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
                        text = point.fullDateLabel,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                Text(
                    text = "${point.sessionCount} sessions (${point.sessionMinutes}m)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TelemetryBlue
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DashboardStatColumn(
                    label = if (trendMode == DashboardTrendMode.CUMULATIVE) "Cumulative Net" else "$periodNoun Net",
                    value = currency.formatFromUsdCents(netValue),
                    color = TrustEmerald
                )
                DashboardStatColumn(
                    label = if (trendMode == DashboardTrendMode.CUMULATIVE) "Cumulative Gross" else "$periodNoun Gross",
                    value = currency.formatFromUsdCents(grossValue),
                    color = ChampagneSecondary
                )
                DashboardStatColumn(
                    label = "Tax Reserve",
                    value = currency.formatFromUsdCents(taxValue),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DashboardEmptyChartState(
    periodLabel: String,
    onOpenLounge: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_empty_state")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ShowChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
            Text(
                text = "No sessions recorded for $periodLabel",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Book a wellness companionship session in the Sanctuary Lounge or select another timeframe to inspect your earnings curve.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onOpenLounge,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Explore Sanctuary Lounge")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DashboardStatColumn(
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

internal fun aggregateSessionEarningsTimeline(
    bookings: List<SessionBooking>,
    range: DashboardRange,
    nowMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault()
): EarningsDashboardAggregation {
    val nowCal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }

    val dayKeyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { this.timeZone = timeZone }
    val monthKeyFmt = SimpleDateFormat("yyyy-MM", Locale.US).apply { this.timeZone = timeZone }
    val shortDayFmt = SimpleDateFormat("EEE", Locale.US).apply { this.timeZone = timeZone }
    val shortDateFmt = SimpleDateFormat("MMM d", Locale.US).apply { this.timeZone = timeZone }
    val fullDayFmt = SimpleDateFormat("EEE, MMM d", Locale.US).apply { this.timeZone = timeZone }
    val monthShortFmt = SimpleDateFormat("MMM", Locale.US).apply { this.timeZone = timeZone }
    val monthFullFmt = SimpleDateFormat("MMMM yyyy", Locale.US).apply { this.timeZone = timeZone }
    val yearFmt = SimpleDateFormat("yyyy", Locale.US).apply { this.timeZone = timeZone }

    val todayKey = dayKeyFmt.format(nowCal.time)
    val currentMonthKey = monthKeyFmt.format(nowCal.time)

    val startCal = (nowCal.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val endExclusiveCal: Calendar
    val periodSubtitle: String
    val defaultSelectedIndex: Int
    val isYearly = range == DashboardRange.CURRENT_YEAR

    when (range) {
        DashboardRange.CURRENT_WEEK -> {
            val dow = startCal.get(Calendar.DAY_OF_WEEK)
            val firstDow = startCal.firstDayOfWeek
            val daysSinceWeekStart = (dow - firstDow + 7) % 7
            startCal.add(Calendar.DAY_OF_YEAR, -daysSinceWeekStart)
            endExclusiveCal = (startCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 7) }
            val lastDayCal = (startCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 6) }
            periodSubtitle = "${shortDateFmt.format(startCal.time)} – ${shortDateFmt.format(lastDayCal.time)}"
            defaultSelectedIndex = daysSinceWeekStart.coerceIn(0, 6)
        }

        DashboardRange.CURRENT_MONTH -> {
            startCal.set(Calendar.DAY_OF_MONTH, 1)
            val daysInMonth = startCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            endExclusiveCal = (startCal.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
            periodSubtitle = monthFullFmt.format(startCal.time)
            defaultSelectedIndex = (nowCal.get(Calendar.DAY_OF_MONTH) - 1).coerceIn(0, daysInMonth - 1)
        }

        DashboardRange.CURRENT_YEAR -> {
            startCal.set(Calendar.MONTH, Calendar.JANUARY)
            startCal.set(Calendar.DAY_OF_MONTH, 1)
            endExclusiveCal = (startCal.clone() as Calendar).apply { add(Calendar.YEAR, 1) }
            periodSubtitle = "${yearFmt.format(startCal.time)} Year-to-Date"
            defaultSelectedIndex = nowCal.get(Calendar.MONTH).coerceIn(0, 11)
        }
    }

    val windowStartMs = startCal.timeInMillis
    val windowEndExclusiveMs = endExclusiveCal.timeInMillis

    val bookingsInRange = bookings.filter { booking ->
        val createdMs = booking.createdAt?.toDate()?.time ?: nowMillis
        createdMs >= windowStartMs && createdMs < windowEndExclusiveMs
    }

    var runningGross = 0L
    var runningTax = 0L
    var runningNet = 0L

    val points = if (isYearly) {
        val bookingsByMonth = bookingsInRange.groupBy { booking ->
            booking.createdAt?.toDate()?.let { monthKeyFmt.format(it) } ?: currentMonthKey
        }
        (0 until 12).map { monthOffset ->
            val monthCal = (startCal.clone() as Calendar).apply {
                add(Calendar.MONTH, monthOffset)
            }
            val date = monthCal.time
            val key = monthKeyFmt.format(date)
            val bucketBookings = bookingsByMonth[key].orEmpty()

            val bucketGross = bucketBookings.sumOf { it.grossCentsUsd }
            val bucketTax = bucketBookings.sumOf { it.taxWithheldCentsUsd }
            val bucketNet = (bucketGross - bucketTax).coerceAtLeast(0L)
            val bucketMinutes = bucketBookings.sumOf { it.durationMinutes }

            runningGross += bucketGross
            runningTax += bucketTax
            runningNet += bucketNet

            val isCurrentMonth = key == currentMonthKey
            EarningsTimePoint(
                dayKey = key,
                shortLabel = monthShortFmt.format(date),
                fullDateLabel = if (isCurrentMonth) {
                    "${monthFullFmt.format(date)} (Current)"
                } else {
                    monthFullFmt.format(date)
                },
                dailyGrossCentsUsd = bucketGross,
                dailyTaxCentsUsd = bucketTax,
                dailyNetCentsUsd = bucketNet,
                cumulativeGrossCentsUsd = runningGross,
                cumulativeTaxCentsUsd = runningTax,
                cumulativeNetCentsUsd = runningNet,
                sessionCount = bucketBookings.size,
                sessionMinutes = bucketMinutes
            )
        }
    } else {
        val dayCount = if (range == DashboardRange.CURRENT_WEEK) {
            7
        } else {
            startCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
        val bookingsByDay = bookingsInRange.groupBy { booking ->
            booking.createdAt?.toDate()?.let { dayKeyFmt.format(it) } ?: todayKey
        }
        (0 until dayCount).map { offset ->
            val dayCal = (startCal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val date = dayCal.time
            val key = dayKeyFmt.format(date)
            val dayBookings = bookingsByDay[key].orEmpty()

            val dailyGross = dayBookings.sumOf { it.grossCentsUsd }
            val dailyTax = dayBookings.sumOf { it.taxWithheldCentsUsd }
            val dailyNet = (dailyGross - dailyTax).coerceAtLeast(0L)
            val dailyMinutes = dayBookings.sumOf { it.durationMinutes }

            runningGross += dailyGross
            runningTax += dailyTax
            runningNet += dailyNet

            val isToday = key == todayKey
            val shortLabel = when {
                isToday -> "Today"
                range == DashboardRange.CURRENT_WEEK -> shortDayFmt.format(date)
                else -> shortDateFmt.format(date)
            }
            EarningsTimePoint(
                dayKey = key,
                shortLabel = shortLabel,
                fullDateLabel = if (isToday) "Today • ${fullDayFmt.format(date)}" else fullDayFmt.format(date),
                dailyGrossCentsUsd = dailyGross,
                dailyTaxCentsUsd = dailyTax,
                dailyNetCentsUsd = dailyNet,
                cumulativeGrossCentsUsd = runningGross,
                cumulativeTaxCentsUsd = runningTax,
                cumulativeNetCentsUsd = runningNet,
                sessionCount = dayBookings.size,
                sessionMinutes = dailyMinutes
            )
        }
    }

    val totalGross = bookingsInRange.sumOf { it.grossCentsUsd }
    val totalTax = bookingsInRange.sumOf { it.taxWithheldCentsUsd }
    val totalNet = (totalGross - totalTax).coerceAtLeast(0L)

    val settledNet = bookingsInRange.filter {
        val status = BookingStatus.fromRaw(it.status)
        status == BookingStatus.COMPLETED || status == BookingStatus.PAYOUT_REQUESTED
    }.sumOf { (it.grossCentsUsd - it.taxWithheldCentsUsd).coerceAtLeast(0L) }

    val activeEscrowNet = (totalNet - settledNet).coerceAtLeast(0L)
    val totalSessions = bookingsInRange.size
    val totalMinutes = bookingsInRange.sumOf { it.durationMinutes }
    val avgNet = if (totalSessions > 0) totalNet / totalSessions else 0L

    val companionBreakdown = bookingsInRange
        .groupBy { it.companionId.ifBlank { it.companionName } }
        .map { (compKey, list) ->
            val compGross = list.sumOf { it.grossCentsUsd }
            val compTax = list.sumOf { it.taxWithheldCentsUsd }
            val compNet = (compGross - compTax).coerceAtLeast(0L)
            val share = if (totalNet > 0L) compNet.toFloat() / totalNet.toFloat() else 0f
            CompanionEarningsSummary(
                companionId = compKey,
                companionName = list.firstOrNull()?.companionName?.ifBlank { "Companion" } ?: "Companion",
                sessionCount = list.size,
                totalMinutes = list.sumOf { it.durationMinutes },
                grossCentsUsd = compGross,
                netCentsUsd = compNet,
                shareFraction = share
            )
        }
        .sortedByDescending { it.netCentsUsd }

    return EarningsDashboardAggregation(
        periodSubtitle = periodSubtitle,
        points = points,
        defaultSelectedIndex = defaultSelectedIndex,
        totalGrossCentsUsd = totalGross,
        totalTaxCentsUsd = totalTax,
        totalNetCentsUsd = totalNet,
        settledNetCentsUsd = settledNet,
        activeEscrowNetCentsUsd = activeEscrowNet,
        totalSessions = totalSessions,
        totalMinutes = totalMinutes,
        avgNetPerSessionUsd = avgNet,
        companionBreakdown = companionBreakdown
    )
}
