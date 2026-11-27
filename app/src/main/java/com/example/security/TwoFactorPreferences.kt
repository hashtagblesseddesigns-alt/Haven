package com.example.security

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.CurrencyCode
import com.example.data.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.havenlyDataStore by preferencesDataStore(name = "havenly_security_prefs")

data class PendingRegistration(
    val displayName: String,
    val role: UserRole,
    val preferredCurrency: CurrencyCode,
    val wellnessFocus: String
)

class TwoFactorPreferences(private val context: Context) {
    private val pinHashKey = stringPreferencesKey("two_factor_pin_hash")
    private val cipherInspectorKey = booleanPreferencesKey("show_cipher_inspector")
    private val regNameKey = stringPreferencesKey("pending_reg_name")
    private val regRoleKey = stringPreferencesKey("pending_reg_role")
    private val regCurrencyKey = stringPreferencesKey("pending_reg_currency")
    private val regFocusKey = stringPreferencesKey("pending_reg_focus")
    private val fcmTokenKey = stringPreferencesKey("fcm_registration_token")

    val hasConfiguredPin: Flow<Boolean> = context.havenlyDataStore.data.map { prefs ->
        !prefs[pinHashKey].isNullOrBlank()
    }

    val showCipherInspector: Flow<Boolean> = context.havenlyDataStore.data.map { prefs ->
        prefs[cipherInspectorKey] ?: false
    }

    val cachedFcmToken: Flow<String> = context.havenlyDataStore.data.map { prefs ->
        prefs[fcmTokenKey].orEmpty()
    }

    suspend fun saveTwoFactorPin(pin: String) {
        val hash = sha256(pin)
        context.havenlyDataStore.edit { prefs ->
            prefs[pinHashKey] = hash
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        var matched = false
        context.havenlyDataStore.edit { prefs ->
            val saved = prefs[pinHashKey]
            matched = saved != null && saved == sha256(pin)
        }
        return matched
    }

    suspend fun setShowCipherInspector(enabled: Boolean) {
        context.havenlyDataStore.edit { prefs ->
            prefs[cipherInspectorKey] = enabled
        }
    }

    suspend fun saveFcmToken(token: String) {
        val clean = token.trim().take(512)
        context.havenlyDataStore.edit { prefs ->
            prefs[fcmTokenKey] = clean
        }
    }

    suspend fun savePendingRegistration(registration: PendingRegistration) {
        context.havenlyDataStore.edit { prefs ->
            prefs[regNameKey] = registration.displayName.trim().take(80)
            prefs[regRoleKey] = registration.role.name
            prefs[regCurrencyKey] = registration.preferredCurrency.code
            prefs[regFocusKey] = registration.wellnessFocus.trim().take(300)
        }
    }

    suspend fun consumePendingRegistration(): PendingRegistration? {
        val prefs = context.havenlyDataStore.data.first()
        val name = prefs[regNameKey]
        val roleRaw = prefs[regRoleKey]
        val currencyRaw = prefs[regCurrencyKey]
        val focus = prefs[regFocusKey]
        if (name == null && roleRaw == null && currencyRaw == null && focus == null) {
            return null
        }
        context.havenlyDataStore.edit { mutable ->
            mutable.remove(regNameKey)
            mutable.remove(regRoleKey)
            mutable.remove(regCurrencyKey)
            mutable.remove(regFocusKey)
        }
        return PendingRegistration(
            displayName = name.orEmpty(),
            role = UserRole.fromRaw(roleRaw),
            preferredCurrency = CurrencyCode.fromRaw(currencyRaw),
            wellnessFocus = focus.orEmpty()
        )
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("havenly-2fa:$input".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
