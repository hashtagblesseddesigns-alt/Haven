package com.example.notifications

import android.util.Log
import com.example.R
import com.example.data.repository.HavenlyRepository
import com.example.security.TwoFactorPreferences
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HavenlyFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        HavenlyNotificationHelper.ensureChannels(applicationContext)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM registration token refreshed")
        val appContext = applicationContext
        serviceScope.launch {
            TwoFactorPreferences(appContext).saveFcmToken(token)
            if (Firebase.auth.currentUser != null) {
                runCatching {
                    val db = FirebaseFirestore.getInstance(appContext.getString(R.string.firestore_database_id))
                    HavenlyRepository(db).updateUserProfile(fcmToken = token)
                }.onFailure { err ->
                    Log.w(TAG, "Failed to sync refreshed FCM token to Firestore", err)
                }
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        val alertType = data["type"]?.lowercase().orEmpty()
        val title = data["title"]
            ?: remoteMessage.notification?.title
            ?: if (alertType == "payment" || alertType == "booking") {
                "Session Payment & Ledger Update"
            } else {
                "New Incoming Message"
            }
        val body = data["body"]
            ?: remoteMessage.notification?.body
            ?: "Open Havenly to view your latest update."

        when (alertType) {
            "payment", "booking", "ledger", "payout" -> {
                HavenlyNotificationHelper.showPaymentAlert(
                    context = applicationContext,
                    title = title,
                    body = body,
                    referenceId = data["bookingId"].orEmpty()
                )
            }

            "cipher" -> {
                HavenlyNotificationHelper.showChatAlert(
                    context = applicationContext,
                    title = title,
                    body = body,
                    companionId = data["companionId"].orEmpty(),
                    destination = HavenlyNotificationHelper.DEST_CIPHER
                )
            }

            else -> {
                HavenlyNotificationHelper.showChatAlert(
                    context = applicationContext,
                    title = title,
                    body = body,
                    companionId = data["companionId"].orEmpty(),
                    destination = HavenlyNotificationHelper.DEST_CHAT
                )
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "HavenlyFCM"
    }
}
