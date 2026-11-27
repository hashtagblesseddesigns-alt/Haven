package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.notifications.HavenlyNotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationAlertRobolectricTest {

    @Test
    fun `ensureChannels registers chat and payment channels`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        HavenlyNotificationHelper.ensureChannels(context)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val chatChannel = manager.getNotificationChannel(HavenlyNotificationHelper.CHANNEL_CHAT_ALERTS)
        val paymentChannel = manager.getNotificationChannel(HavenlyNotificationHelper.CHANNEL_PAYMENT_ALERTS)

        assertNotNull(chatChannel)
        assertNotNull(paymentChannel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, chatChannel.importance)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, paymentChannel.importance)
    }

    @Test
    fun `showChatAlert and showPaymentAlert respect permission and post notifications`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowManager = Shadows.shadowOf(manager)

        assertFalse(HavenlyNotificationHelper.hasNotificationPermission(app))
        HavenlyNotificationHelper.showChatAlert(
            context = app,
            title = "Elena",
            body = "Ready when you are.",
            companionId = "elena"
        )
        assertEquals(0, shadowManager.allNotifications.size)

        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(HavenlyNotificationHelper.hasNotificationPermission(app))

        HavenlyNotificationHelper.showChatAlert(
            context = app,
            title = "Elena",
            body = "Ready when you are.",
            companionId = "elena",
            destination = HavenlyNotificationHelper.DEST_CHAT
        )
        HavenlyNotificationHelper.showPaymentAlert(
            context = app,
            title = "Payment Settled",
            body = "Booking b-42 settled to your ledger.",
            referenceId = "b-42"
        )

        val posted = shadowManager.allNotifications
        assertEquals(2, posted.size)
        assertTrue(posted.any { it.channelId == HavenlyNotificationHelper.CHANNEL_CHAT_ALERTS })
        assertTrue(posted.any { it.channelId == HavenlyNotificationHelper.CHANNEL_PAYMENT_ALERTS })
    }
}
