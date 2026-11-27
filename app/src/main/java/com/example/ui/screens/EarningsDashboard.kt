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
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

enum class DashboardRange(val label: String, val days: Int?) {
    DAYS_7("7D", 7),
    DAYS_14("14D", 14),
    DAYS_30("30D", 30),
    ALL("ALL", null)
}

enum class DashboardTrendMode(val label: String) {
    CUMULATIVE("Cumulative Growth"),
    DAILY("Daily Earnings")
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
    val points: List<EarningsTimePoint>,
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

    var selectedRange by rememberSaveable { mutableStateOf(DashboardRange.DAYS_14) }
    var trendMode by rememberSaveable { mutableStateOf(DashboardTrendMode.CUMULATIVE) }

    val aggregation = remember(bookings, selectedRange) {
        aggregateSessionEarningsTimeline(
            bookings = bookings,
            range = selectedRange
        )
    }

    var selectedPointIndex by rememberSaveable(selectedRange) {
        mutableIntStateOf((aggregation.points.lastIndex).coerceAtLeast(0))
    }
    LaunchedEffect(aggregation.points.size) {
        if (aggregation.points.isNotEmpty() && selectedPointIndex !in aggregation.points.indices) {
            selectedPointIndex = aggregation.points.lastIndex
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

            // 1. Hero Aggregated Earnings Summary Card
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
                                    imageVector = Icons.AutoMirrored.Rounded.ShowChart,
                                    contentDescription = null,
                                    tint = TrustEmerald,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Earnings Over Time",
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
                                        text = "Firestore Live",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TrustEmerald
                                    )
                                }
                            }
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
                            Column {
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

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                DashboardRange.entries.forEach { range ->
                                    FilterChip(
                                        selected = selectedRange == range,
                                        onClick = { selectedRange = range },
                                        label = { Text(range.label) },
                                        modifier = Modifier.testTag("dashboard_range_${range.label.lowercase()}")
                                    )
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DashboardTrendMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = trendMode == mode,
                                    onClick = { trendMode = mode },
                                    label = { Text(mode.label) },
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
                            DashboardEmptyChartState(onOpenLounge = onOpenLounge)
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
                                text = "Book a companion session from the Sanctuary Lounge to populate real-time companion earnings shares.",
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
                    contentDescription = "Earnings over time line chart across ${points.size} days"
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
                    label = if (trendMode == DashboardTrendMode.CUMULATIVE) "Cumulative Net" else "Daily Net",
                    value = currency.formatFromUsdCents(netValue),
                    color = TrustEmerald
                )
                DashboardStatColumn(
                    label = if (trendMode == DashboardTrendMode.CUMULATIVE) "Cumulative Gross" else "Daily Gross",
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
private fun DashboardEmptyChartState(onOpenLounge: () -> Unit) {
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
                text = "No Firestore sessions recorded yet",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Book a wellness companionship session in the Sanctuary Lounge to watch your earnings curve update live.",
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
    val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { this.timeZone = timeZone }
    val shortFmt = SimpleDateFormat("MMM d", Locale.US).apply { this.timeZone = timeZone }
    val fullFmt = SimpleDateFormat("EEE, MMM d", Locale.US).apply { this.timeZone = timeZone }

    val endCal = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val todayKey = keyFmt.format(endCal.time)

    val dayCount = when (val fixedDays = range.days) {
        null -> {
            val earliestMs = bookings.mapNotNull { it.createdAt?.toDate()?.time }.minOrNull()
            if (earliestMs == null) {
                7
            } else {
                val startCal = Calendar.getInstance(timeZone).apply {
                    timeInMillis = earliestMs
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val diffDays = ((endCal.timeInMillis - startCal.timeInMillis) / 86_400_000L).toInt() + 1
                diffDays.coerceIn(7, 90)
            }
        }
        else -> fixedDays.coerceIn(1, 90)
    }

    val startCal = (endCal.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, -(dayCount - 1))
    }
    val windowStartMs = startCal.timeInMillis

    val bookingsInRange = bookings.filter { booking ->
        val createdMs = booking.createdAt?.toDate()?.time ?: nowMillis
        range.days == null || createdMs >= windowStartMs
    }

    val bookingsByDay = bookingsInRange.groupBy { booking ->
        booking.createdAt?.toDate()?.let { keyFmt.format(it) } ?: todayKey
    }

    var runningGross = 0L
    var runningTax = 0L
    var runningNet = 0L

    val points = (0 until dayCount).map { offset ->
        val dayCal = (startCal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, offset)
        }
        val date = dayCal.time
        val key = keyFmt.format(date)
        val dayBookings = bookingsByDay[key].orEmpty()

        val dailyGross = dayBookings.sumOf { it.grossCentsUsd }
        val dailyTax = dayBookings.sumOf { it.taxWithheldCentsUsd }
        val dailyNet = (dailyGross - dailyTax).coerceAtLeast(0L)
        val dailyMinutes = dayBookings.sumOf { it.durationMinutes }

        runningGross += dailyGross
        runningTax += dailyTax
        runningNet += dailyNet

        val isToday = offset == dayCount - 1
        EarningsTimePoint(
            dayKey = key,
            shortLabel = if (isToday) "Today" else shortFmt.format(date),
            fullDateLabel = if (isToday) "Today • ${fullFmt.format(date)}" else fullFmt.format(date),
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
        points = points,
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
