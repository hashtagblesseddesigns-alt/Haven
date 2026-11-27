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
import com.example.data.model.LiveChatMessage
import com.example.data.model.MatchScore
import com.example.data.model.SessionBooking
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.VerificationTier
import com.example.data.repository.HavenlyRepository
import com.example.data.model.BookingStatus
import com.example.security.MessageCrypto
import com.example.security.TwoFactorPreferences
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

sealed interface RealtimeAlertEvent {
    data class ChatAlert(
        val title: String,
        val body: String,
        val companionId: String,
        val isEncrypted: Boolean
    ) : RealtimeAlertEvent

    data class PaymentAlert(
        val title: String,
        val body: String,
        val bookingId: String
    ) : RealtimeAlertEvent
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

    val liveChatsState: StateFlow<UiState<List<LiveChatMessage>>> = repository.observeLiveChats(currentUserId)
        .map<List<LiveChatMessage>, UiState<List<LiveChatMessage>>> { UiState.Success(it) }
        .catch { emit(unwrapFirestoreError("liveChats", it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val hasConfiguredPin: StateFlow<Boolean> = twoFactorPrefs.hasConfiguredPin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val showCipherInspector: StateFlow<Boolean> = twoFactorPrefs.showCipherInspector
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val cachedFcmToken: StateFlow<String> = twoFactorPrefs.cachedFcmToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), "")

    private val _selectedCompanionId = MutableStateFlow(companions.first().id)
    val selectedCompanionId: StateFlow<String> = _selectedCompanionId.asStateFlow()

    private val _matchmakingState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val matchmakingState: StateFlow<AiTaskState> = _matchmakingState.asStateFlow()

    private val _eoaCommandState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val eoaCommandState: StateFlow<AiTaskState> = _eoaCommandState.asStateFlow()

    private val _taxReportState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val taxReportState: StateFlow<AiTaskState> = _taxReportState.asStateFlow()

    private val _profileSaveState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val profileSaveState: StateFlow<AiTaskState> = _profileSaveState.asStateFlow()

    private val _autoReplyEnabled = MutableStateFlow(true)
    val autoReplyEnabled: StateFlow<Boolean> = _autoReplyEnabled.asStateFlow()

    private val _liveReplyState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val liveReplyState: StateFlow<AiTaskState> = _liveReplyState.asStateFlow()

    private val _bannerFeedback = MutableStateFlow<String?>(null)
    val bannerFeedback: StateFlow<String?> = _bannerFeedback.asStateFlow()

    private val _alertEvents = MutableSharedFlow<RealtimeAlertEvent>(extraBufferCapacity = 16)
    val alertEvents: SharedFlow<RealtimeAlertEvent> = _alertEvents.asSharedFlow()

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
            ).onSuccess {
                syncFcmToken(showToastOnSuccess = false)
            }.onFailure { err ->
                _bannerFeedback.value = err.localizedMessage ?: "Failed to initialize profile."
            }
        }

        observeIncomingChatAlerts()
        observeIncomingCipherAlerts()
        observePaymentAndBookingAlerts()
    }

    private fun alertsEnabled(): Boolean =
        (profileState.value as? UiState.Success)?.data?.notificationsEnabled ?: true

    private fun observeIncomingChatAlerts() {
        viewModelScope.launch {
            var knownIds: MutableSet<String>? = null
            liveChatsState.collect { state ->
                if (state !is UiState.Success) return@collect
                val items = state.data
                val baseline = knownIds
                if (baseline == null) {
                    knownIds = items.mapNotNull { it.id.takeIf(String::isNotBlank) }.toMutableSet()
                    return@collect
                }
                items.forEach { msg ->
                    if (msg.id.isNotBlank() && baseline.add(msg.id)) {
                        if (msg.senderRole == "COMPANION" && alertsEnabled()) {
                            _alertEvents.tryEmit(
                                RealtimeAlertEvent.ChatAlert(
                                    title = "New message from ${msg.senderName.ifBlank { msg.companionName }}",
                                    body = msg.text,
                                    companionId = msg.companionId,
                                    isEncrypted = false
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun observeIncomingCipherAlerts() {
        viewModelScope.launch {
            var knownIds: MutableSet<String>? = null
            messagesState.collect { state ->
                if (state !is UiState.Success) return@collect
                val items = state.data
                val baseline = knownIds
                if (baseline == null) {
                    knownIds = items.mapNotNull { it.id.takeIf(String::isNotBlank) }.toMutableSet()
                    return@collect
                }
                items.forEach { msg ->
                    if (msg.id.isNotBlank() && baseline.add(msg.id)) {
                        if (msg.senderRole == "COMPANION" && alertsEnabled()) {
                            _alertEvents.tryEmit(
                                RealtimeAlertEvent.ChatAlert(
                                    title = "Encrypted message from ${msg.companionName}",
                                    body = msg.plaintext,
                                    companionId = msg.companionId,
                                    isEncrypted = true
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun observePaymentAndBookingAlerts() {
        viewModelScope.launch {
            var knownStatuses: MutableMap<String, String>? = null
            bookingsState.collect { state ->
                if (state !is UiState.Success) return@collect
                val bookings = state.data
                val baseline = knownStatuses
                if (baseline == null) {
                    knownStatuses = bookings.associate { it.id to it.status }.toMutableMap()
                    return@collect
                }
                val currency = CurrencyCode.fromRaw(
                    (profileState.value as? UiState.Success)?.data?.preferredCurrency
                )
                bookings.forEach { booking ->
                    if (booking.id.isBlank()) return@forEach
                    val previousStatus = baseline[booking.id]
                    if (previousStatus == null) {
                        baseline[booking.id] = booking.status
                        if (alertsEnabled()) {
                            _alertEvents.tryEmit(
                                RealtimeAlertEvent.PaymentAlert(
                                    title = "Session Booking Confirmed",
                                    body = "${booking.companionName} • ${currency.formatFromUsdCents(booking.grossCentsUsd)} (${booking.scheduledLabel})",
                                    bookingId = booking.id
                                )
                            )
                        }
                    } else if (previousStatus != booking.status) {
                        baseline[booking.id] = booking.status
                        if (alertsEnabled()) {
                            val nextLabel = BookingStatus.fromRaw(booking.status).label
                            _alertEvents.tryEmit(
                                RealtimeAlertEvent.PaymentAlert(
                                    title = "Payment Ledger: $nextLabel",
                                    body = "${booking.companionName} (${booking.sessionTitle}) • Net ${currency.formatFromUsdCents((booking.grossCentsUsd - booking.taxWithheldCentsUsd).coerceAtLeast(0L))}",
                                    bookingId = booking.id
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun syncFcmToken(showToastOnSuccess: Boolean = true) {
        viewModelScope.launch {
            runCatching {
                FirebaseMessaging.getInstance().token.await()
            }.onSuccess { token ->
                if (token.isNotBlank()) {
                    twoFactorPrefs.saveFcmToken(token)
                    repository.updateUserProfile(fcmToken = token)
                        .onSuccess {
                            if (showToastOnSuccess) {
                                _bannerFeedback.value = "FCM device token synced for real-time alerts."
                            }
                        }
                }
            }.onFailure { err ->
                Log.w(TAG, "Unable to fetch FCM token", err)
                if (showToastOnSuccess) {
                    _bannerFeedback.value = err.localizedMessage ?: "FCM token unavailable on this device."
                }
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateUserProfile(notificationsEnabled = enabled)
                .onSuccess {
                    if (enabled) syncFcmToken(showToastOnSuccess = false)
                    _bannerFeedback.value = if (enabled) {
                        "Real-time chat & payment alerts enabled."
                    } else {
                        "Real-time alerts paused."
                    }
                }
                .onFailure { err ->
                    _bannerFeedback.value = err.localizedMessage ?: "Failed to update alert preferences."
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

    fun showBannerMessage(message: String) {
        _bannerFeedback.value = message
    }

    fun updateProfileDetails(displayName: String, wellnessFocus: String) {
        val cleanName = displayName.trim().take(80)
        if (cleanName.isEmpty()) {
            _profileSaveState.value = AiTaskState.Error("Please enter a display name.")
            return
        }
        val cleanFocus = wellnessFocus.trim().take(300)
        viewModelScope.launch {
            _profileSaveState.value = AiTaskState.Running
            repository.updateUserProfile(
                displayName = cleanName,
                wellnessFocus = cleanFocus.ifEmpty { null }
            ).onSuccess {
                _profileSaveState.value = AiTaskState.Success("Profile saved for $cleanName.")
                _bannerFeedback.value = "Display name & profile saved."
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Failed to save profile."
                _profileSaveState.value = AiTaskState.Error(msg)
                _bannerFeedback.value = msg
            }
        }
    }

    fun updateProfileAvatar(avatarBase64: String) {
        viewModelScope.launch {
            _profileSaveState.value = AiTaskState.Running
            repository.updateUserProfile(avatarBase64 = avatarBase64)
                .onSuccess {
                    _profileSaveState.value = AiTaskState.Success("Profile picture updated.")
                    _bannerFeedback.value = "Profile picture updated."
                }
                .onFailure { err ->
                    val msg = err.localizedMessage ?: "Failed to upload profile picture."
                    _profileSaveState.value = AiTaskState.Error(msg)
                    _bannerFeedback.value = msg
                }
        }
    }

    fun removeProfileAvatar() {
        viewModelScope.launch {
            _profileSaveState.value = AiTaskState.Running
            repository.updateUserProfile(avatarBase64 = "")
                .onSuccess {
                    _profileSaveState.value = AiTaskState.Success("Profile picture removed.")
                    _bannerFeedback.value = "Profile picture removed."
                }
                .onFailure { err ->
                    val msg = err.localizedMessage ?: "Failed to remove profile picture."
                    _profileSaveState.value = AiTaskState.Error(msg)
                    _bannerFeedback.value = msg
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

    fun setLiveAutoReplyEnabled(enabled: Boolean) {
        _autoReplyEnabled.value = enabled
    }

    fun sendLiveChatMessage(companion: CompanionProfile, messageText: String) {
        val cleanText = messageText.trim()
        if (cleanText.isEmpty()) return
        val memberName = (profileState.value as? UiState.Success)?.data?.displayName?.ifBlank { "Havenly Member" }
            ?: "Havenly Member"
        val currentThread = (liveChatsState.value as? UiState.Success)?.data.orEmpty()
            .filter { it.companionId == companion.id }

        viewModelScope.launch {
            val sendResult = repository.sendLiveChatMessage(
                companion = companion,
                text = cleanText,
                senderRole = "USER",
                senderName = memberName
            )
            if (sendResult.isFailure) {
                _bannerFeedback.value = sendResult.exceptionOrNull()?.localizedMessage ?: "Failed to send message."
                return@launch
            }
            if (_autoReplyEnabled.value) {
                generateAndSyncCompanionReply(companion, memberName, currentThread, cleanText)
            }
        }
    }

    fun requestCompanionLiveReply(companion: CompanionProfile) {
        val memberName = (profileState.value as? UiState.Success)?.data?.displayName?.ifBlank { "Havenly Member" }
            ?: "Havenly Member"
        val currentThread = (liveChatsState.value as? UiState.Success)?.data.orEmpty()
            .filter { it.companionId == companion.id }
        val latestUserPrompt = currentThread.lastOrNull { it.senderRole == "USER" }?.text
            ?: "Hi ${companion.name}, checking in for our wellness session."

        viewModelScope.launch {
            generateAndSyncCompanionReply(companion, memberName, currentThread, latestUserPrompt)
        }
    }

    fun deleteLiveChatMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteLiveChatMessage(messageId)
                .onFailure { err ->
                    _bannerFeedback.value = err.localizedMessage ?: "Could not delete message."
                }
        }
    }

    private suspend fun generateAndSyncCompanionReply(
        companion: CompanionProfile,
        memberName: String,
        recentThread: List<LiveChatMessage>,
        latestMessage: String
    ) {
        _liveReplyState.value = AiTaskState.Running
        try {
            val replyText = aiService.generateCompanionChatReply(
                companion = companion,
                memberName = memberName,
                recentMessages = recentThread,
                latestUserMessage = latestMessage
            )
            repository.sendLiveChatMessage(
                companion = companion,
                text = replyText,
                senderRole = "COMPANION",
                senderName = companion.name
            ).getOrThrow()
            _liveReplyState.value = AiTaskState.Idle
        } catch (e: TimeoutCancellationException) {
            _liveReplyState.value = AiTaskState.Error("Companion reply timed out.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Companion live reply failed", e)
            _liveReplyState.value = AiTaskState.Error(e.localizedMessage ?: "Could not receive companion reply.")
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
