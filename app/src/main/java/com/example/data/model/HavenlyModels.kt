package com.example.data.model

import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Locale

enum class CurrencyCode(
    val code: String,
    val symbol: String,
    val usdMultiplier: Double,
    val label: String
) {
    USD("USD", "$", 1.0, "US Dollar"),
    EUR("EUR", "€", 0.92, "Euro"),
    GBP("GBP", "£", 0.79, "British Pound"),
    JPY("JPY", "¥", 154.0, "Japanese Yen"),
    CAD("CAD", "CA$", 1.39, "Canadian Dollar");

    fun formatFromUsdCents(centsUsd: Long): String {
        val converted = (centsUsd / 100.0) * usdMultiplier
        return if (this == JPY) {
            String.format(Locale.US, "%s%,.0f", symbol, converted)
        } else {
            String.format(Locale.US, "%s%,.2f", symbol, converted)
        }
    }

    companion object {
        fun fromRaw(raw: String?): CurrencyCode =
            entries.firstOrNull { it.code == raw } ?: USD
    }
}

enum class BookingStatus(val label: String) {
    CONFIRMED("Confirmed"),
    IN_SESSION("In Session"),
    COMPLETED("Completed"),
    PAYOUT_REQUESTED("Ledger Settled");

    companion object {
        fun fromRaw(raw: String?): BookingStatus =
            entries.firstOrNull { it.name == raw } ?: CONFIRMED
    }
}

enum class UserRole(val label: String) {
    CLIENT("Client Member"),
    COMPANION("Verified Companion");

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.name == raw } ?: CLIENT
    }
}

enum class VerificationTier(val label: String) {
    STANDARD("Google Verified"),
    VERIFIED_2FA("2FA Shield Verified");

    companion object {
        fun fromRaw(raw: String?): VerificationTier =
            entries.firstOrNull { it.name == raw } ?: STANDARD
    }
}

enum class FraudRiskLevel {
    LOW, MEDIUM, HIGH;

    companion object {
        fun fromRaw(raw: String?): FraudRiskLevel =
            entries.firstOrNull { it.name == raw } ?: LOW
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val role: String = UserRole.CLIENT.name,
    val verificationStatus: String = VerificationTier.STANDARD.name,
    val preferredCurrency: String = CurrencyCode.USD.code,
    val wellnessFocus: String = "Mindful platonic companionship, stress relief, and calm conversation",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MatchScore(
    @DocumentId val id: String = "",
    val userId: String = "",
    val companionId: String = "",
    val companionName: String = "",
    val compatibilityScore: Long = 0L,
    val rationale: String = "",
    val recommendedFormat: String = "",
    val bookmarked: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SessionBooking(
    @DocumentId val id: String = "",
    val userId: String = "",
    val companionId: String = "",
    val companionName: String = "",
    val sessionTitle: String = "",
    val durationMinutes: Long = 60L,
    val currency: String = CurrencyCode.USD.code,
    val grossCentsUsd: Long = 0L,
    val taxWithheldCentsUsd: Long = 0L,
    val status: String = BookingStatus.CONFIRMED.name,
    val scheduledLabel: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class EncryptedMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val companionId: String = "",
    val companionName: String = "",
    val senderRole: String = "USER",
    val cipherText: String = "",
    val ivBase64: String = "",
    val keyFingerprint: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class EoaAgentRun(
    @DocumentId val id: String = "",
    val userId: String = "",
    val directive: String = "",
    val eoaSummary: String = "",
    val overallHealthScore: Long = 98L,
    val fraudRiskLevel: String = FraudRiskLevel.LOW.name,
    val activeAlertsCount: Long = 0L,
    val matchmakerScore: Long = 96L,
    val fraudSentinelScore: Long = 99L,
    val taxAgentScore: Long = 97L,
    val cryptoGuardianScore: Long = 100L,
    val infraBalancerScore: Long = 98L,
    val recommendedAction: String = "",
    val latencyMs: Long = 120L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ActivityAuditLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "AUTH",
    val title: String = "",
    val detail: String = "",
    val severity: String = "INFO",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class CompanionProfile(
    val id: String,
    val name: String,
    val city: String,
    val tagline: String,
    val specialties: List<String>,
    val languages: List<String>,
    val hourlyRateCentsUsd: Long,
    val rating: Double,
    val completedSessions: Int,
    val verifiedBadge: String,
    @DrawableRes val portraitRes: Int?
)

object CompanionCatalog {
    val companions = listOf(
        CompanionProfile(
            id = "elena_01",
            name = "Elena Vance",
            city = "San Francisco, CA",
            tagline = "Certified Cuddle Therapist & Calm Breathwork Guide",
            specialties = listOf("Platonic Cuddle Therapy", "Acoustic Tea Lounge", "Grief & Burnout Reset"),
            languages = listOf("English", "French"),
            hourlyRateCentsUsd = 12000L,
            rating = 4.98,
            completedSessions = 342,
            verifiedBadge = "2FA + ID Verified",
            portraitRes = R.drawable.img_companion_elena
        ),
        CompanionProfile(
            id = "marcus_02",
            name = "Marcus Sterling",
            city = "Seattle, WA",
            tagline = "Mindfulness Mentor & Somatic Relaxation Companion",
            specialties = listOf("Cozy Reading & Cinema", "Stress Decompression", "Guided Walking Outings"),
            languages = listOf("English", "Spanish"),
            hourlyRateCentsUsd = 11000L,
            rating = 4.95,
            completedSessions = 219,
            verifiedBadge = "2FA + Background Checked",
            portraitRes = R.drawable.img_companion_marcus
        ),
        CompanionProfile(
            id = "sora_03",
            name = "Sora Takahashi",
            city = "Los Angeles, CA",
            tagline = "Artisan Tea Host & Warm Conversationalist",
            specialties = listOf("Matcha & Reflection", "Museum Companionship", "Evening Calm Talks"),
            languages = listOf("English", "Japanese"),
            hourlyRateCentsUsd = 13500L,
            rating = 4.99,
            completedSessions = 418,
            verifiedBadge = "Platinum Verified",
            portraitRes = R.drawable.img_companion_sora
        ),
        CompanionProfile(
            id = "julian_04",
            name = "Julian Mercer",
            city = "New York, NY",
            tagline = "Executive Decompression & Botanical Walk Partner",
            specialties = listOf("Executive Burnout Reset", "Gallery Walks", "Quiet Presence"),
            languages = listOf("English", "German"),
            hourlyRateCentsUsd = 14000L,
            rating = 4.93,
            completedSessions = 176,
            verifiedBadge = "2FA + ID Verified",
            portraitRes = null
        ),
        CompanionProfile(
            id = "amara_05",
            name = "Amara Osei",
            city = "Austin, TX",
            tagline = "Restorative Soundscapes & Cozy Sanctuary Host",
            specialties = listOf("Platonic Cuddle Therapy", "Ambient Vinyl Listening", "Anxiety Grounding"),
            languages = listOf("English"),
            hourlyRateCentsUsd = 11500L,
            rating = 4.97,
            completedSessions = 289,
            verifiedBadge = "Platinum Verified",
            portraitRes = null
        )
    )

    fun findById(id: String): CompanionProfile =
        companions.firstOrNull { it.id == id } ?: companions.first()
}
