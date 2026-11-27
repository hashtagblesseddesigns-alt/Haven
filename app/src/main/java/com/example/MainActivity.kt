package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ai.HavenlyAiService
import com.example.data.repository.HavenlyRepository
import com.example.notifications.HavenlyNotificationHelper
import com.example.security.TwoFactorPreferences
import com.example.security.configureAppCheck
import com.example.ui.auth.HavenlyAuthScreen
import com.example.ui.screens.HavenlyAppShell
import com.example.ui.theme.HavenlyTheme
import com.example.ui.viewmodel.HavenlyViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    private val pendingNavDestination = mutableStateOf<String?>(null)
    private val pendingCompanionId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)
        HavenlyNotificationHelper.ensureChannels(this)
        handleNotificationIntent(intent)

        enableEdgeToEdge()
        setContent {
            HavenlyTheme {
                AppContent(
                    pendingNavDestination = pendingNavDestination.value,
                    pendingCompanionId = pendingCompanionId.value,
                    onConsumeNavExtras = {
                        pendingNavDestination.value = null
                        pendingCompanionId.value = null
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val dest = intent?.getStringExtra(HavenlyNotificationHelper.EXTRA_NAV_DESTINATION)
        val companionId = intent?.getStringExtra(HavenlyNotificationHelper.EXTRA_COMPANION_ID)
        if (!dest.isNullOrBlank()) {
            pendingNavDestination.value = dest
        }
        if (!companionId.isNullOrBlank()) {
            pendingCompanionId.value = companionId
        }
    }
}

@Composable
fun AppContent(
    pendingNavDestination: String? = null,
    pendingCompanionId: String? = null,
    onConsumeNavExtras: () -> Unit = {}
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        HavenlyAuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        val uid = user.uid
        val displayName = user.displayName ?: user.email?.substringBefore("@") ?: "Havenly Member"
        key(uid) {
            val havenlyViewModel: HavenlyViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        HavenlyViewModel(
                            repository = HavenlyRepository(db),
                            aiService = HavenlyAiService(),
                            twoFactorPrefs = TwoFactorPreferences(app.applicationContext),
                            currentUserId = uid,
                            initialDisplayName = displayName
                        )
                    }
                }
            )
            HavenlyAppShell(
                currentUserId = uid,
                viewModel = havenlyViewModel,
                pendingNavDestination = pendingNavDestination,
                pendingCompanionId = pendingCompanionId,
                onConsumeNavExtras = onConsumeNavExtras,
                onSignedOut = { currentUser = Firebase.auth.currentUser }
            )
        }
    }
}
