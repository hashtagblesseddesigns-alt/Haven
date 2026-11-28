package com.example

import com.example.data.model.BookingStatus
import com.example.data.model.SessionBooking
import com.example.ui.screens.DashboardRange
import com.example.ui.screens.aggregateSessionEarningsTimeline
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class EarningsDashboardUnitTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    // Fixed anchor: Wednesday, Nov 25, 2026 at 12:00 UTC
    private fun anchorMillis(): Long = Calendar.getInstance(utc).apply {
        set(2026, Calendar.NOVEMBER, 25, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun utcTimestamp(year: Int, month: Int, day: Int): Timestamp {
        val cal = Calendar.getInstance(utc).apply {
            set(year, month, day, 14, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return Timestamp(Date(cal.timeInMillis))
    }

    @Test
    fun `aggregateSessionEarningsTimeline filters current week, month, and year accurately`() {
        val bookings = listOf(
            // Current week (Nov 23 & Nov 25, 2026)
            SessionBooking(
                id = "b_week_1",
                companionId = "elena_01",
                companionName = "Elena Vance",
                durationMinutes = 60L,
                grossCentsUsd = 12000L,
                taxWithheldCentsUsd = 2400L,
                status = BookingStatus.COMPLETED.name,
                createdAt = utcTimestamp(2026, Calendar.NOVEMBER, 23)
            ),
            SessionBooking(
                id = "b_week_2",
                companionId = "sora_03",
                companionName = "Sora Takahashi",
                durationMinutes = 90L,
                grossCentsUsd = 20000L,
                taxWithheldCentsUsd = 4000L,
                status = BookingStatus.CONFIRMED.name,
                createdAt = utcTimestamp(2026, Calendar.NOVEMBER, 25)
            ),
            // Earlier in current month (Nov 4, 2026)
            SessionBooking(
                id = "b_month_earlier",
                companionId = "elena_01",
                companionName = "Elena Vance",
                durationMinutes = 30L,
                grossCentsUsd = 6000L,
                taxWithheldCentsUsd = 1200L,
                status = BookingStatus.PAYOUT_REQUESTED.name,
                createdAt = utcTimestamp(2026, Calendar.NOVEMBER, 4)
            ),
            // Earlier in current year (Aug 12, 2026)
            SessionBooking(
                id = "b_year_aug",
                companionId = "marcus_02",
                companionName = "Marcus Sterling",
                durationMinutes = 60L,
                grossCentsUsd = 10000L,
                taxWithheldCentsUsd = 2000L,
                status = BookingStatus.COMPLETED.name,
                createdAt = utcTimestamp(2026, Calendar.AUGUST, 12)
            ),
            // Prior year (Dec 20, 2025) - excluded from all 2026 windows
            SessionBooking(
                id = "b_last_year",
                companionId = "elena_01",
                companionName = "Elena Vance",
                durationMinutes = 60L,
                grossCentsUsd = 50000L,
                taxWithheldCentsUsd = 10000L,
                status = BookingStatus.COMPLETED.name,
                createdAt = utcTimestamp(2025, Calendar.DECEMBER, 20)
            )
        )

        val now = anchorMillis()

        // 1. Current Week
        val weekResult = aggregateSessionEarningsTimeline(
            bookings = bookings,
            range = DashboardRange.CURRENT_WEEK,
            nowMillis = now,
            timeZone = utc
        )
        assertEquals(7, weekResult.points.size)
        assertEquals(2, weekResult.totalSessions)
        assertEquals(25600L, weekResult.totalNetCentsUsd)
        assertEquals(9600L, weekResult.settledNetCentsUsd)
        assertEquals(16000L, weekResult.activeEscrowNetCentsUsd)
        assertEquals(150L, weekResult.totalMinutes)
        assertEquals(12800L, weekResult.avgNetPerSessionUsd)

        // 2. Current Month (November 2026 has 30 days)
        val monthResult = aggregateSessionEarningsTimeline(
            bookings = bookings,
            range = DashboardRange.CURRENT_MONTH,
            nowMillis = now,
            timeZone = utc
        )
        assertEquals(30, monthResult.points.size)
        assertEquals(3, monthResult.totalSessions)
        assertEquals(30400L, monthResult.totalNetCentsUsd)
        assertEquals(24, monthResult.defaultSelectedIndex) // Nov 25 -> index 24
        assertEquals(4800L, monthResult.points[3].cumulativeNetCentsUsd) // Nov 4
        assertEquals(30400L, monthResult.points[24].cumulativeNetCentsUsd) // Nov 25

        // 3. Current Year (2026: 12 monthly points)
        val yearResult = aggregateSessionEarningsTimeline(
            bookings = bookings,
            range = DashboardRange.CURRENT_YEAR,
            nowMillis = now,
            timeZone = utc
        )
        assertEquals(12, yearResult.points.size)
        assertEquals(4, yearResult.totalSessions)
        assertEquals(38400L, yearResult.totalNetCentsUsd)
        assertEquals(Calendar.NOVEMBER, yearResult.defaultSelectedIndex)
        assertEquals(8000L, yearResult.points[Calendar.AUGUST].dailyNetCentsUsd)
        assertEquals(8000L, yearResult.points[Calendar.AUGUST].cumulativeNetCentsUsd)
        assertEquals(30400L, yearResult.points[Calendar.NOVEMBER].dailyNetCentsUsd)
        assertEquals(38400L, yearResult.points[Calendar.NOVEMBER].cumulativeNetCentsUsd)

        // Companion breakdown sorted by highest net revenue in Current Year
        assertEquals(3, yearResult.companionBreakdown.size)
        assertEquals("sora_03", yearResult.companionBreakdown[0].companionId)
        assertEquals(16000L, yearResult.companionBreakdown[0].netCentsUsd)
        assertEquals("elena_01", yearResult.companionBreakdown[1].companionId)
        assertEquals(14400L, yearResult.companionBreakdown[1].netCentsUsd)
        assertEquals("marcus_02", yearResult.companionBreakdown[2].companionId)
        assertEquals(8000L, yearResult.companionBreakdown[2].netCentsUsd)
    }

    @Test
    fun `aggregateSessionEarningsTimeline handles empty bookings cleanly across all periods`() {
        val now = anchorMillis()

        val week = aggregateSessionEarningsTimeline(emptyList(), DashboardRange.CURRENT_WEEK, now, utc)
        val month = aggregateSessionEarningsTimeline(emptyList(), DashboardRange.CURRENT_MONTH, now, utc)
        val year = aggregateSessionEarningsTimeline(emptyList(), DashboardRange.CURRENT_YEAR, now, utc)

        assertEquals(7, week.points.size)
        assertEquals(30, month.points.size)
        assertEquals(12, year.points.size)
        assertEquals(0L, week.totalNetCentsUsd)
        assertEquals(0L, month.totalNetCentsUsd)
        assertEquals(0L, year.totalNetCentsUsd)
        assertTrue(year.companionBreakdown.isEmpty())
    }
}
