package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object HavenlyNotificationHelper {
    const val CHANNEL_CHAT_ALERTS = "havenly_chat_alerts"
    const val CHANNEL_PAYMENT_ALERTS = "havenly_payment_alerts"

    const val EXTRA_NAV_DESTINATION = "havenly_nav_destination"
    const val EXTRA_COMPANION_ID = "havenly_companion_id"

    const val DEST_CHAT = "chat"
    const val DEST_CIPHER = "cipher"
    const val DEST_LEDGER = "ledger"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val chatChannel = NotificationChannel(
            CHANNEL_CHAT_ALERTS,
            "Incoming Chat Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Real-time alerts for new incoming member and companion messages."
            enableVibration(true)
        }

        val paymentChannel = NotificationChannel(
            CHANNEL_PAYMENT_ALERTS,
            "Payment & Ledger Updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Real-time alerts for session bookings, settlement updates, and tax reserve changes."
            enableVibration(true)
        }

        manager.createNotificationChannel(chatChannel)
        manager.createNotificationChannel(paymentChannel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showChatAlert(
        context: Context,
        title: String,
        body: String,
        companionId: String = "",
        destination: String = DEST_CHAT
    ) {
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return

        val safeDest = if (destination == DEST_CIPHER) DEST_CIPHER else DEST_CHAT
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAV_DESTINATION, safeDest)
            if (companionId.isNotBlank()) {
                putExtra(EXTRA_COMPANION_ID, companionId)
            }
        }
        val requestCode = (companionId.hashCode() * 31 + safeDest.hashCode()).let { if (it == 0) 101 else it }
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CHAT_ALERTS)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title.ifBlank { "New Message" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = ((System.currentTimeMillis() and 0x7FFFFFFFL).toInt()).coerceAtLeast(1000)
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    fun showPaymentAlert(
        context: Context,
        title: String,
        body: String,
        referenceId: String = ""
    ) {
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAV_DESTINATION, DEST_LEDGER)
        }
        val requestCode = referenceId.hashCode().let { if (it == 0) 202 else it }
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_PAYMENT_ALERTS)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title.ifBlank { "Payment & Ledger Update" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = ((System.currentTimeMillis() and 0x7FFFFFFFL).toInt()).coerceAtLeast(2000)
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}
