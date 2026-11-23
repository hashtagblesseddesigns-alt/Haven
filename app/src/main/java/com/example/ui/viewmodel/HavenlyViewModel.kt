package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.HavenlyAiService
import com.example.data.model.ActivityAuditLog
import com.example.data.model.CompanionCatalog
import com.example.data.model.CompanionProfile
import com.example.data.model.CurrencyCode
import com.example.data.model.EncryptedMessage
import com.example.data.model.EoaAgentRun
import com.example.data.model.MatchScore
import com.example.data.model.SessionBooking
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.VerificationTier
import com.example.data.repository.HavenlyRepository
import com.example.security.MessageCrypto
import com.example.security.TwoFactorPreferences
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiTaskState {
    data object Idle : AiTaskState
    data object Running : AiTaskState
    data class Success(val message: String) : AiTaskState
    data class Error(val message: String) : AiTaskState
}

data class DecryptedChatItem(
    val id: String,
    val companionId: String,
    val companionName: String,
    val senderRole: String,
    val plaintext: String,
    val cipherText: String,
    val ivBase64: String,
    val keyFingerprint: String,
    val timestampSeconds: Long
)

class HavenlyViewModel(
    private val repository: HavenlyRepository,
    private val aiService: HavenlyAiService,
    private val twoFactorPrefs: TwoFactorPreferences,
    private val currentUserId: String,
    initialDisplayName: String
) : ViewModel() {

    val companions: List<CompanionProfile> = CompanionCatalog.companions

    val profileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { emit(unwrapFirestoreError("profile", it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val matchesState: StateFlow<UiState<List<MatchScore>>> = repository.observeMatches(currentUserId)
        .map<List<MatchScore>, UiState<List<MatchScore>>> { UiState.Success(it) }
        .catch { emit(unwrapFirestoreError("matches", it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val bookingsState: StateFlow<UiState<List<SessionBooking>>> = repository.observeBookings(currentUserId)
        .map<List<SessionBooking>, UiState<List<SessionBooking>>> { UiState.Success(it) }
        .catch { emit(unwrapFirestoreError("bookings", it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val messagesState: StateFlow<UiState<List<DecryptedChatItem>>> = repository.observeMessages(currentUserId)
        .map<List<EncryptedMessage>, UiState<List<DecryptedChatItem>>> { encryptedList ->
            UiState.Success(
                encryptedList.map { msg ->
                    DecryptedChatItem(
                        id = msg.id,
                        companionId = msg.companionId,
                        companionName = msg.companionName,
                        senderRole = msg.senderRole,
                        plaintext = MessageCrypto.decrypt(
                            msg.cipherText,
                            msg.ivBase64,
                            currentUserId,
                            msg.companionId
                        ),
                        cipherText = msg.cipherText,
                        ivBase64 = msg.ivBase64,
                        keyFingerprint = msg.keyFingerprint,
                        timestampSeconds = msg.createdAt?.seconds ?: 0L
                    )
                }
            )
        }
        .catch { emit(unwrapFirestoreError("messages", it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val eoaRunsState: StateFlow<UiState<List<EoaAgentRun>>> = repository.observeEoaRuns(currentUserId)
        .map<List<EoaAgentRun>, UiState<List<EoaAgentRun>>> { UiState.Success(it) }
        .catch { emit(unwrapFirestoreError("agentRuns", it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val activityLogsState: StateFlow<UiState<List<ActivityAuditLog>>> = repository.observeActivityLogs(currentUserId)
        .map<List<ActivityAuditLog>, UiState<List<ActivityAuditLog>>> { UiState.Success(it) }
        .catch { emit(unwrapFirestoreError("activityLogs", it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val hasConfiguredPin: StateFlow<Boolean> = twoFactorPrefs.hasConfiguredPin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val showCipherInspector: StateFlow<Boolean> = twoFactorPrefs.showCipherInspector
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    private val _selectedCompanionId = MutableStateFlow(companions.first().id)
    val selectedCompanionId: StateFlow<String> = _selectedCompanionId.asStateFlow()

    private val _matchmakingState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val matchmakingState: StateFlow<AiTaskState> = _matchmakingState.asStateFlow()

    private val _eoaCommandState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val eoaCommandState: StateFlow<AiTaskState> = _eoaCommandState.asStateFlow()

    private val _taxReportState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val taxReportState: StateFlow<AiTaskState> = _taxReportState.asStateFlow()

    private val _bannerFeedback = MutableStateFlow<String?>(null)
    val bannerFeedback: StateFlow<String?> = _bannerFeedback.asStateFlow()

    init {
        viewModelScope.launch {
            val pending = twoFactorPrefs.consumePendingRegistration()
            val resolvedName = pending?.displayName?.takeIf { it.isNotBlank() } ?: initialDisplayName
            repository.ensureUserProfile(
                displayName = resolvedName,
                role = pending?.role ?: UserRole.CLIENT,
                preferredCurrency = pending?.preferredCurrency ?: CurrencyCode.USD,
                wellnessFocus = pending?.wellnessFocus?.takeIf { it.isNotBlank() }
                    ?: "Calm platonic companionship, mindful tea talks, and stress decompression"
            ).onFailure { err ->
                _bannerFeedback.value = err.localizedMessage ?: "Failed to initialize profile."
            }
        }
    }

    private fun unwrapFirestoreError(streamName: String, error: Throwable): UiState.Error {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing $streamName", rootError)
        return UiState.Error(rootError.message ?: "Failed to observe $streamName")
    }

    fun clearBanner() {
        _bannerFeedback.value = null
    }

    fun selectCompanionForChat(companionId: String) {
        _selectedCompanionId.value = companionId
    }

    fun toggleCipherInspector(enabled: Boolean) {
        viewModelScope.launch {
            twoFactorPrefs.setShowCipherInspector(enabled)
        }
    }

    fun setRole(role: UserRole) {
        viewModelScope.launch {
            repository.updateUserProfile(role = role).onFailure {
                _bannerFeedback.value = it.localizedMessage ?: "Failed to switch workspace mode."
            }
        }
    }

    fun setPreferredCurrency(currency: CurrencyCode) {
        viewModelScope.launch {
            repository.updateUserProfile(preferredCurrency = currency).onFailure {
                _bannerFeedback.value = it.localizedMessage ?: "Failed to update currency."
            }
        }
    }

    fun setupOrVerifyTwoFactor(pin: String, isConfiguring: Boolean, onVerified: () -> Unit, onInvalidPin: () -> Unit) {
        viewModelScope.launch {
            if (pin.length != 6 || !pin.all { it.isDigit() }) {
                onInvalidPin()
                return@launch
            }
            if (isConfiguring) {
                twoFactorPrefs.saveTwoFactorPin(pin)
                repository.updateUserProfile(verificationTier = VerificationTier.VERIFIED_2FA)
                repository.logActivity(
                    category = "AUTH",
                    title = "2FA Shield Enrolled",
                    detail = "6-digit local TOTP passcode hash sealed and profile upgraded to VERIFIED_2FA.",
                    severity = "INFO"
                )
                _bannerFeedback.value = "2FA Shield activated for your sanctuary account."
                onVerified()
            } else {
                val ok = twoFactorPrefs.verifyPin(pin)
                if (ok) {
                    repository.updateUserProfile(verificationTier = VerificationTier.VERIFIED_2FA)
                    repository.logActivity(
                        category = "AUTH",
                        title = "2FA Challenge Passed",
                        detail = "Multi-factor identity challenge verified.",
                        severity = "INFO"
                    )
                    _bannerFeedback.value = "2FA identity challenge verified."
                    onVerified()
                } else {
                    repository.logActivity(
                        category = "FRAUD_SCAN",
                        title = "Failed 2FA Attempt Blocked",
                        detail = "Incorrect 6-digit challenge code entered.",
                        severity = "WARN"
                    )
                    onInvalidPin()
                }
            }
        }
    }

    fun toggleCompanionBookmark(companion: CompanionProfile) {
        viewModelScope.launch {
            val currentMatch = (matchesState.value as? UiState.Success)?.data?.firstOrNull { it.companionId == companion.id }
            repository.toggleBookmark(companion, currentMatch).onFailure {
                _bannerFeedback.value = it.localizedMessage ?: "Failed to update bookmark."
            }
        }
    }

    fun runAiMatchmaking(wellnessFocus: String) {
        val cleanFocus = wellnessFocus.trim().ifBlank { "Calm platonic cuddle therapy and peaceful conversation" }
        viewModelScope.launch {
            _matchmakingState.value = AiTaskState.Running
            try {
                repository.updateUserProfile(wellnessFocus = cleanFocus)
                val ranked = aiService.runAiMatchmaking(currentUserId, cleanFocus, companions)
                repository.saveMatchScores(ranked).getOrThrow()
                _matchmakingState.value = AiTaskState.Success(
                    "Matched ${ranked.firstOrNull()?.companionName ?: "companions"} (${ranked.firstOrNull()?.compatibilityScore ?: 95}% fit)"
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI matchmaking timed out", e)
                _matchmakingState.value = AiTaskState.Error("Matchmaking timed out after 60s. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI matchmaking failed", e)
                _matchmakingState.value = AiTaskState.Error(e.localizedMessage ?: "AI matchmaking failed.")
            }
        }
    }

    fun bookSession(
        companion: CompanionProfile,
        sessionTitle: String,
        durationMinutes: Int,
        scheduledLabel: String
    ) {
        val currency = CurrencyCode.fromRaw((profileState.value as? UiState.Success)?.data?.preferredCurrency)
        viewModelScope.launch {
            repository.createBooking(companion, sessionTitle, durationMinutes, currency, scheduledLabel)
                .onSuccess {
                    _bannerFeedback.value = "Booked $durationMinutes min with ${companion.name}."
                }
                .onFailure {
                    _bannerFeedback.value = it.localizedMessage ?: "Booking failed."
                }
        }
    }

    fun advanceBooking(booking: SessionBooking) {
        viewModelScope.launch {
            repository.advanceBookingStatus(booking)
                .onFailure {
                    _bannerFeedback.value = it.localizedMessage ?: "Failed to update booking."
                }
        }
    }

    fun sendEncryptedMessage(companion: CompanionProfile, messageText: String) {
        if (messageText.isBlank()) return
        viewModelScope.launch {
            repository.sendEncryptedMessage(companion, messageText, senderRole = "USER")
                .onFailure {
                    _bannerFeedback.value = it.localizedMessage ?: "Encryption dispatch failed."
                }
        }
    }

    fun simulateCompanionCheckIn(companion: CompanionProfile) {
        viewModelScope.launch {
            val checkInText = "Hi! I am verified and online in the ${companion.city} lounge. Looking forward to our calm ${companion.specialties.firstOrNull() ?: "wellness"} session."
            repository.sendEncryptedMessage(companion, checkInText, senderRole = "COMPANION")
                .onFailure {
                    _bannerFeedback.value = it.localizedMessage ?: "Check-in encryption failed."
                }
        }
    }

    fun runEoaOrchestration(directive: String) {
        val cleanDirective = directive.trim().ifBlank {
            "Autopilot full-fleet audit: latency balancing, fraud sentinel scan & tax readiness"
        }
        viewModelScope.launch {
            _eoaCommandState.value = AiTaskState.Running
            try {
                val profile = (profileState.value as? UiState.Success)?.data
                val is2fa = profile?.verificationStatus == VerificationTier.VERIFIED_2FA.name
                val bookings = (bookingsState.value as? UiState.Success)?.data.orEmpty()
                val msgCount = (messagesState.value as? UiState.Success)?.data?.size ?: 0

                val run = aiService.runEoaOrchestration(
                    userId = currentUserId,
                    directive = cleanDirective,
                    is2faVerified = is2fa,
                    bookings = bookings,
                    encryptedMessageCount = msgCount
                )
                repository.saveEoaAgentRun(run).getOrThrow()
                _eoaCommandState.value = AiTaskState.Success(
                    "EOA Health ${run.overallHealthScore}% • Fraud Risk: ${run.fraudRiskLevel} (${run.latencyMs}ms)"
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "EOA orchestration timed out", e)
                _eoaCommandState.value = AiTaskState.Error("EOA directive timed out after 60s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "EOA orchestration failed", e)
                _eoaCommandState.value = AiTaskState.Error(e.localizedMessage ?: "EOA orchestration failed.")
            }
        }
    }

    fun generateTaxComplianceReport() {
        viewModelScope.launch {
            _taxReportState.value = AiTaskState.Running
            try {
                val profile = (profileState.value as? UiState.Success)?.data
                val currency = CurrencyCode.fromRaw(profile?.preferredCurrency)
                val is2fa = profile?.verificationStatus == VerificationTier.VERIFIED_2FA.name
                val bookings = (bookingsState.value as? UiState.Success)?.data.orEmpty()
                val report = aiService.generateTaxComplianceBrief(bookings, currency, is2fa)
                repository.logActivity(
                    category = "TAX_REPORT",
                    title = "Multi-Currency Tax Brief Generated (${currency.code})",
                    detail = "Evaluated ${bookings.size} ledger entries with 20% withholding reserve.",
                    severity = "INFO"
                )
                _taxReportState.value = AiTaskState.Success(report)
            } catch (e: TimeoutCancellationException) {
                _taxReportState.value = AiTaskState.Error("Tax report timed out after 60s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Tax report failed", e)
                _taxReportState.value = AiTaskState.Error(e.localizedMessage ?: "Tax report generation failed.")
            }
        }
    }

    private companion object {
        const val TAG = "HavenlyVM"
    }
}
