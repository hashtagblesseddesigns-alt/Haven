package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ActivityAuditLog
import com.example.data.model.BookingStatus
import com.example.data.model.CompanionProfile
import com.example.data.model.CurrencyCode
import com.example.data.model.EncryptedMessage
import com.example.data.model.EoaAgentRun
import com.example.data.model.MatchScore
import com.example.data.model.SessionBooking
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.VerificationTier
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.security.MessageCrypto
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class HavenlyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    rethrowOrLog(error, OperationType.GET, path, userId)
                }
        )
    }

    fun observeMatches(userId: String): Flow<List<MatchScore>> = flow {
        val path = "users/$userId/matches"
        emitAll(
            db.collection("users").document(userId).collection("matches")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(MatchScore::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.compatibilityScore }
                }
                .catch { error ->
                    rethrowOrLog(error, OperationType.LIST, path, userId)
                }
        )
    }

    fun observeBookings(userId: String): Flow<List<SessionBooking>> = flow {
        val path = "users/$userId/bookings"
        emitAll(
            db.collection("users").document(userId).collection("bookings")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(SessionBooking::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    rethrowOrLog(error, OperationType.LIST, path, userId)
                }
        )
    }

    fun observeMessages(userId: String): Flow<List<EncryptedMessage>> = flow {
        val path = "users/$userId/messages"
        emitAll(
            db.collection("users").document(userId).collection("messages")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(EncryptedMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    rethrowOrLog(error, OperationType.LIST, path, userId)
                }
        )
    }

    fun observeEoaRuns(userId: String): Flow<List<EoaAgentRun>> = flow {
        val path = "users/$userId/agentRuns"
        emitAll(
            db.collection("users").document(userId).collection("agentRuns")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(EoaAgentRun::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    rethrowOrLog(error, OperationType.LIST, path, userId)
                }
        )
    }

    fun observeActivityLogs(userId: String): Flow<List<ActivityAuditLog>> = flow {
        val path = "users/$userId/activityLogs"
        emitAll(
            db.collection("users").document(userId).collection("activityLogs")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(ActivityAuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    rethrowOrLog(error, OperationType.LIST, path, userId)
                }
        )
    }

    private fun rethrowOrLog(error: Throwable, op: OperationType, path: String, expectedUid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    suspend fun ensureUserProfile(
        displayName: String,
        role: UserRole = UserRole.CLIENT,
        preferredCurrency: CurrencyCode = CurrencyCode.USD,
        wellnessFocus: String = "Calm platonic companionship, mindful tea talks, and stress decompression"
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val safeName = displayName.trim().ifBlank { "Havenly Member" }.take(80)
                val safeFocus = wellnessFocus.trim()
                    .ifBlank { "Calm platonic companionship, mindful tea talks, and stress decompression" }
                    .take(300)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "role" to role.name,
                    "verificationStatus" to VerificationTier.STANDARD.name,
                    "preferredCurrency" to preferredCurrency.code,
                    "wellnessFocus" to safeFocus,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                logActivityInternal(
                    uid = uid,
                    category = "AUTH",
                    title = "Verified Sanctuary Account Registered",
                    detail = "Google Credential Manager identity bound to $uid (${role.label}).",
                    severity = "INFO"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(
        role: UserRole? = null,
        verificationTier: VerificationTier? = null,
        preferredCurrency: CurrencyCode? = null,
        wellnessFocus: String? = null
    ): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            path = docRef.path
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (role != null) updates["role"] = role.name
            if (verificationTier != null) updates["verificationStatus"] = verificationTier.name
            if (preferredCurrency != null) updates["preferredCurrency"] = preferredCurrency.code
            if (wellnessFocus != null) {
                val clean = wellnessFocus.trim().take(300)
                if (clean.isNotEmpty()) updates["wellnessFocus"] = clean
            }
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveMatchScores(matches: List<MatchScore>): Result<Unit> {
        var path = "users/matches"
        return try {
            val uid = requireUserId()
            val batch = db.batch()
            val collection = db.collection("users").document(uid).collection("matches")
            path = collection.path
            matches.forEach { m ->
                val docRef = collection.document(m.companionId)
                val existing = docRef.get().await()
                if (existing.exists()) {
                    batch.update(
                        docRef,
                        mapOf(
                            "companionName" to m.companionName.take(80),
                            "compatibilityScore" to m.compatibilityScore.coerceIn(0L, 100L),
                            "rationale" to m.rationale.take(600).ifBlank { "Aligned wellness profile." },
                            "recommendedFormat" to m.recommendedFormat.take(120).ifBlank { "60m Lounge Session" },
                            "bookmarked" to (existing.getBoolean("bookmarked") ?: m.bookmarked),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    batch.set(
                        docRef,
                        mapOf(
                            "userId" to uid,
                            "companionId" to m.companionId,
                            "companionName" to m.companionName.take(80),
                            "compatibilityScore" to m.compatibilityScore.coerceIn(0L, 100L),
                            "rationale" to m.rationale.take(600).ifBlank { "Aligned wellness profile." },
                            "recommendedFormat" to m.recommendedFormat.take(120).ifBlank { "60m Lounge Session" },
                            "bookmarked" to m.bookmarked,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
            logActivityInternal(
                uid = uid,
                category = "EOA_AUDIT",
                title = "AI Matchmaking Sub-Agent Completed",
                detail = "Ranked ${matches.size} verified companions using Gemini 3.8 Flash.",
                severity = "INFO"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleBookmark(companion: CompanionProfile, currentMatch: MatchScore?): Result<Unit> {
        var path = "users/matches"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("matches").document(companion.id)
            path = docRef.path
            val snapshot = docRef.get().await()
            val nextBookmark = !(currentMatch?.bookmarked ?: (snapshot.getBoolean("bookmarked") ?: false))
            if (snapshot.exists()) {
                docRef.update(
                    mapOf(
                        "bookmarked" to nextBookmark,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "companionId" to companion.id,
                        "companionName" to companion.name.take(80),
                        "compatibilityScore" to 92L,
                        "rationale" to companion.tagline.take(600),
                        "recommendedFormat" to (companion.specialties.firstOrNull() ?: "60m Wellness Session").take(120),
                        "bookmarked" to nextBookmark,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun createBooking(
        companion: CompanionProfile,
        sessionTitle: String,
        durationMinutes: Int,
        currency: CurrencyCode,
        scheduledLabel: String
    ): Result<Unit> {
        var path = "users/bookings"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("bookings").document()
            path = docRef.path
            val clampedMinutes = durationMinutes.coerceIn(15, 480)
            val grossCentsUsd = (companion.hourlyRateCentsUsd * clampedMinutes) / 60L
            val taxWithheldCentsUsd = (grossCentsUsd * 20L) / 100L // 20% automated tax reserve estimate
            val cleanTitle = sessionTitle.trim().ifBlank { companion.specialties.firstOrNull() ?: "Wellness Session" }.take(140)
            val cleanSchedule = scheduledLabel.trim().ifBlank { "Today • 7:00 PM" }.take(80)

            val payload = mapOf(
                "userId" to uid,
                "companionId" to companion.id,
                "companionName" to companion.name.take(80),
                "sessionTitle" to cleanTitle,
                "durationMinutes" to clampedMinutes.toLong(),
                "currency" to currency.code,
                "grossCentsUsd" to grossCentsUsd,
                "taxWithheldCentsUsd" to taxWithheldCentsUsd,
                "status" to BookingStatus.CONFIRMED.name,
                "scheduledLabel" to cleanSchedule,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            logActivityInternal(
                uid = uid,
                category = "BOOKING",
                title = "Session Booked with ${companion.name}",
                detail = "$cleanTitle ($clampedMinutes min, ${currency.formatFromUsdCents(grossCentsUsd)}) logged.",
                severity = "INFO"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun advanceBookingStatus(booking: SessionBooking): Result<Unit> {
        var path = "users/bookings"
        return try {
            val uid = requireUserId()
            require(booking.id.isNotBlank()) { "Booking ID cannot be blank" }
            val current = BookingStatus.fromRaw(booking.status)
            val next = when (current) {
                BookingStatus.CONFIRMED -> BookingStatus.IN_SESSION
                BookingStatus.IN_SESSION -> BookingStatus.COMPLETED
                BookingStatus.COMPLETED -> BookingStatus.PAYOUT_REQUESTED
                BookingStatus.PAYOUT_REQUESTED -> return Result.success(Unit)
            }
            val docRef = db.collection("users").document(uid).collection("bookings").document(booking.id)
            path = docRef.path
            docRef.update(
                mapOf(
                    "status" to next.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logActivityInternal(
                uid = uid,
                category = if (next == BookingStatus.PAYOUT_REQUESTED) "TAX_REPORT" else "BOOKING",
                title = "Booking -> ${next.label}",
                detail = "${booking.companionName}: ${booking.sessionTitle}",
                severity = "INFO"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendEncryptedMessage(
        companion: CompanionProfile,
        plaintext: String,
        senderRole: String = "USER"
    ): Result<Unit> {
        var path = "users/messages"
        return try {
            val uid = requireUserId()
            val cleanText = plaintext.trim()
            require(cleanText.isNotEmpty()) { "Message cannot be empty" }
            val encrypted = MessageCrypto.encrypt(cleanText.take(1500), uid, companion.id)
            val docRef = db.collection("users").document(uid).collection("messages").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "companionId" to companion.id,
                "companionName" to companion.name.take(80),
                "senderRole" to if (senderRole == "COMPANION") "COMPANION" else "USER",
                "cipherText" to encrypted.cipherTextBase64.take(4000),
                "ivBase64" to encrypted.ivBase64.take(128),
                "keyFingerprint" to encrypted.keyFingerprint.take(128),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            logActivityInternal(
                uid = uid,
                category = "CIPHER",
                title = "AES-256-GCM Packet Dispatched",
                detail = "Encrypted to ${companion.name} (${encrypted.keyFingerprint}).",
                severity = "INFO"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveEoaAgentRun(run: EoaAgentRun): Result<Unit> {
        var path = "users/agentRuns"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("agentRuns").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "directive" to run.directive.take(500).ifBlank { "Full fleet audit" },
                "eoaSummary" to run.eoaSummary.take(2000).ifBlank { "Audit complete." },
                "overallHealthScore" to run.overallHealthScore.coerceIn(0L, 100L),
                "fraudRiskLevel" to run.fraudRiskLevel,
                "activeAlertsCount" to run.activeAlertsCount.coerceIn(0L, 500L),
                "matchmakerScore" to run.matchmakerScore.coerceIn(0L, 100L),
                "fraudSentinelScore" to run.fraudSentinelScore.coerceIn(0L, 100L),
                "taxAgentScore" to run.taxAgentScore.coerceIn(0L, 100L),
                "cryptoGuardianScore" to run.cryptoGuardianScore.coerceIn(0L, 100L),
                "infraBalancerScore" to run.infraBalancerScore.coerceIn(0L, 100L),
                "recommendedAction" to run.recommendedAction.take(400).ifBlank { "Maintain nominal pod replicas." },
                "latencyMs" to run.latencyMs.coerceIn(0L, 120_000L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            logActivityInternal(
                uid = uid,
                category = if (run.fraudRiskLevel == "HIGH") "FRAUD_SCAN" else "EOA_AUDIT",
                title = "EOA Fleet Directive Executed (${run.overallHealthScore}%)",
                detail = run.recommendedAction.take(500),
                severity = if (run.fraudRiskLevel == "HIGH") "WARN" else "INFO"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun logActivity(
        category: String,
        title: String,
        detail: String,
        severity: String = "INFO"
    ): Result<Unit> {
        var path = "users/activityLogs"
        return try {
            val uid = requireUserId()
            path = "users/$uid/activityLogs"
            logActivityInternal(uid, category, title, detail, severity)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private suspend fun logActivityInternal(
        uid: String,
        category: String,
        title: String,
        detail: String,
        severity: String
    ) {
        val allowedCategories = setOf("AUTH", "BOOKING", "CIPHER", "EOA_AUDIT", "TAX_REPORT", "FRAUD_SCAN")
        val allowedSeverities = setOf("INFO", "WARN", "CRITICAL")
        val docRef = db.collection("users").document(uid).collection("activityLogs").document()
        docRef.set(
            mapOf(
                "userId" to uid,
                "category" to if (category in allowedCategories) category else "AUTH",
                "title" to title.take(160).ifBlank { "System Event" },
                "detail" to detail.take(500).ifBlank { "Completed." },
                "severity" to if (severity in allowedSeverities) severity else "INFO",
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }
}
