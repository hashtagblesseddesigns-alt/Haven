package com.example

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            HavenlyTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
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
                onSignedOut = { currentUser = Firebase.auth.currentUser }
            )
        }
    }
}
