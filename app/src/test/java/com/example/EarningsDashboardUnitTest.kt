package com.example

import com.example.data.model.BookingStatus
import com.example.data.model.SessionBooking
import com.example.ui.screens.DashboardRange
import com.example.ui.screens.aggregateSessionEarningsTimeline
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.TimeZone

class EarningsDashboardUnitTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private val baseDayMillis = 1_764_000_000_000L // fixed UTC timestamp

    @Test
    fun `aggregateSessionEarningsTimeline computes cumulative and daily series accurately`() {
        val twoDaysAgo = Timestamp(Date(baseDayMillis - 2 * 86_400_000L))
        val today = Timestamp(Date(baseDayMillis))

        val bookings = listOf(
            SessionBooking(
                id = "b1",
                companionId = "elena_01",
                companionName = "Elena Vance",
                durationMinutes = 60L,
                grossCentsUsd = 12000L,
                taxWithheldCentsUsd = 2400L,
                status = BookingStatus.COMPLETED.name,
                createdAt = twoDaysAgo
            ),
            SessionBooking(
                id = "b2",
                companionId = "sora_03",
                companionName = "Sora Takahashi",
                durationMinutes = 90L,
                grossCentsUsd = 20000L,
                taxWithheldCentsUsd = 4000L,
                status = BookingStatus.CONFIRMED.name,
                createdAt = today
            ),
            SessionBooking(
                id = "b3",
                companionId = "elena_01",
                companionName = "Elena Vance",
                durationMinutes = 30L,
                grossCentsUsd = 6000L,
                taxWithheldCentsUsd = 1200L,
                status = BookingStatus.PAYOUT_REQUESTED.name,
                createdAt = today
            )
        )

        val result = aggregateSessionEarningsTimeline(
            bookings = bookings,
            range = DashboardRange.DAYS_7,
            nowMillis = baseDayMillis,
            timeZone = utc
        )

        assertEquals(7, result.points.size)
        assertEquals(38000L, result.totalGrossCentsUsd)
        assertEquals(7600L, result.totalTaxCentsUsd)
        assertEquals(30400L, result.totalNetCentsUsd)
        assertEquals(14400L, result.settledNetCentsUsd) // b1 (9600) + b3 (4800)
        assertEquals(16000L, result.activeEscrowNetCentsUsd) // b2 (16000)
        assertEquals(3, result.totalSessions)
        assertEquals(180L, result.totalMinutes)
        assertEquals(10133L, result.avgNetPerSessionUsd)

        // Two days ago is index 4 in a 7-day window ending today (index 6)
        assertEquals(9600L, result.points[4].dailyNetCentsUsd)
        assertEquals(9600L, result.points[4].cumulativeNetCentsUsd)
        assertEquals(0L, result.points[5].dailyNetCentsUsd)
        assertEquals(9600L, result.points[5].cumulativeNetCentsUsd)
        assertEquals(20800L, result.points[6].dailyNetCentsUsd)
        assertEquals(30400L, result.points[6].cumulativeNetCentsUsd)

        // Companion breakdown sorted by highest net revenue
        assertEquals(2, result.companionBreakdown.size)
        assertEquals("sora_03", result.companionBreakdown[0].companionId)
        assertEquals(16000L, result.companionBreakdown[0].netCentsUsd)
        assertEquals("elena_01", result.companionBreakdown[1].companionId)
        assertEquals(14400L, result.companionBreakdown[1].netCentsUsd)
        assertEquals(2, result.companionBreakdown[1].sessionCount)
    }

    @Test
    fun `aggregateSessionEarningsTimeline handles empty bookings cleanly`() {
        val result = aggregateSessionEarningsTimeline(
            bookings = emptyList(),
            range = DashboardRange.DAYS_14,
            nowMillis = baseDayMillis,
            timeZone = utc
        )

        assertEquals(14, result.points.size)
        assertEquals(0L, result.totalNetCentsUsd)
        assertEquals(0, result.totalSessions)
        assertTrue(result.companionBreakdown.isEmpty())
    }
}
