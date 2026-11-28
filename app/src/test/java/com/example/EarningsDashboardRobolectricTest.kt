package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.model.BookingStatus
import com.example.data.model.SessionBooking
import com.example.data.model.UserProfile
import com.example.ui.screens.EarningsDashboard
import com.example.ui.theme.HavenlyTheme
import com.example.ui.viewmodel.UiState
import com.google.firebase.Timestamp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EarningsDashboardRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `period filter dropdown switches between current week, month, and year`() {
        val nowCal = Calendar.getInstance()
        val currentYear = nowCal.get(Calendar.YEAR)
        val currentMonth = nowCal.get(Calendar.MONTH)
        val otherMonth = if (currentMonth == Calendar.JANUARY) Calendar.FEBRUARY else Calendar.JANUARY

        val thisMonthCal = (nowCal.clone() as Calendar)
        val sameYearOtherMonthCal = Calendar.getInstance().apply {
            set(currentYear, otherMonth, 10, 12, 0, 0)
        }

        val bookings = listOf(
            SessionBooking(
                id = "b_now",
                companionId = "elena_01",
                companionName = "Elena Vance",
                durationMinutes = 60L,
                grossCentsUsd = 10000L,
                taxWithheldCentsUsd = 2000L,
                status = BookingStatus.COMPLETED.name,
                createdAt = Timestamp(Date(thisMonthCal.timeInMillis))
            ),
            SessionBooking(
                id = "b_other_month",
                companionId = "sora_03",
                companionName = "Sora Takahashi",
                durationMinutes = 60L,
                grossCentsUsd = 20000L,
                taxWithheldCentsUsd = 4000L,
                status = BookingStatus.COMPLETED.name,
                createdAt = Timestamp(Date(sameYearOtherMonthCal.timeInMillis))
            )
        )

        composeTestRule.setContent {
            HavenlyTheme {
                EarningsDashboard(
                    profileState = UiState.Success(UserProfile(preferredCurrency = "USD")),
                    bookingsState = UiState.Success(bookings),
                    onSelectCurrency = {},
                    onOpenLounge = {},
                    onOpenLedger = {}
                )
            }
        }

        // Default is Current Month ($80.00 net)
        composeTestRule.onNodeWithTag("dashboard_total_net_earnings").assertTextContains("$80.00")

        // Open filter dropdown and select Current Year ($240.00 net)
        composeTestRule.onNodeWithTag("dashboard_period_dropdown_button").performClick()
        composeTestRule.onNodeWithTag("dashboard_period_option_current_year").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("dashboard_total_net_earnings").assertTextContains("$240.00")

        // Open filter dropdown and select Current Week ($80.00 net)
        composeTestRule.onNodeWithTag("dashboard_period_dropdown_button").performClick()
        composeTestRule.onNodeWithTag("dashboard_period_option_current_week").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("dashboard_total_net_earnings").assertTextContains("$80.00")
    }
}
