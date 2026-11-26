package com.example.data.ai

import com.example.data.model.CompanionProfile
import com.example.data.model.CurrencyCode
import com.example.data.model.EoaAgentRun
import com.example.data.model.FraudRiskLevel
import com.example.data.model.MatchScore
import com.example.data.model.SessionBooking
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class HavenlyAiService {
    private val matchmakingSchema = Schema.obj(
        properties = mapOf(
            "matches" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "companionId" to Schema.string(description = "Exact companion ID from input list"),
                        "companionName" to Schema.string(description = "Companion full name"),
                        "compatibilityScore" to Schema.integer(description = "Compatibility percentage 0 to 100"),
                        "rationale" to Schema.string(description = "Concise 1-2 sentence explanation of match fit"),
                        "recommendedFormat" to Schema.string(description = "Suggested platonic wellness session title")
                    )
                ),
                description = "Ranked list of companion matches"
            )
        )
    )

    private val eoaSchema = Schema.obj(
        properties = mapOf(
            "eoaSummary" to Schema.string(description = "Executive summary from the Head Orchestrator Agent"),
            "overallHealthScore" to Schema.integer(description = "Overall platform health 0 to 100"),
            "fraudRiskLevel" to Schema.enumeration(
                values = listOf("LOW", "MEDIUM", "HIGH"),
                description = "Real-time fraud risk level"
            ),
            "activeAlertsCount" to Schema.integer(description = "Number of active predictive maintenance or risk alerts"),
            "matchmakerScore" to Schema.integer(description = "Matchmaker AI Sub-Agent KPI 0-100"),
            "fraudSentinelScore" to Schema.integer(description = "Fraud & Trust Sentinel Sub-Agent KPI 0-100"),
            "taxAgentScore" to Schema.integer(description = "Multi-Currency Tax Compliance Sub-Agent KPI 0-100"),
            "cryptoGuardianScore" to Schema.integer(description = "AES-256-GCM & 2FA Auth Guardian Sub-Agent KPI 0-100"),
            "infraBalancerScore" to Schema.integer(description = "Predictive Maintenance & Mesh Balancer Sub-Agent KPI 0-100"),
            "recommendedAction" to Schema.string(description = "Single concrete remediation or scaling action")
        )
    )

    private val matchmakingModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = matchmakingSchema
            }
        )
    }

    private val eoaModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = eoaSchema
            }
        )
    }

    private val taxReportModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    suspend fun runAiMatchmaking(
        userId: String,
        wellnessPrompt: String,
        companions: List<CompanionProfile>
    ): List<MatchScore> {
        val rosterPrompt = companions.joinToString("\n") { c ->
            "- id=${c.id}, name=${c.name}, city=${c.city}, tagline=${c.tagline}, specialties=${c.specialties.joinToString("/")}, languages=${c.languages.joinToString("/")}"
        }
        val prompt = """
            You are Havenly's PG-13 Platinum Wellness & Platonic Cuddle Companionship Matchmaker AI.
            Member preference: "$wellnessPrompt"
            Score every companion below from 60 to 99 and explain why they fit the member's calm, platonic wellness goal.
            Companions:
            $rosterPrompt
        """.trimIndent()

        val startMs = System.currentTimeMillis()
        val response = withTimeout(60_000L) {
            matchmakingModel.generateContent(prompt)
        }
        val raw = response.text ?: throw IllegalStateException("Matchmaking model returned an empty response.")
        val json = JSONObject(raw)
        val array = json.getJSONArray("matches")
        val results = mutableListOf<MatchScore>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val compId = item.optString("companionId", "")
            val validCompanion = companions.firstOrNull { it.id == compId } ?: continue
            results.add(
                MatchScore(
                    userId = userId,
                    companionId = validCompanion.id,
                    companionName = validCompanion.name,
                    compatibilityScore = item.optInt("compatibilityScore", 88).toLong().coerceIn(0L, 100L),
                    rationale = item.optString("rationale", validCompanion.tagline),
                    recommendedFormat = item.optString("recommendedFormat", validCompanion.specialties.firstOrNull() ?: "60m Session"),
                    bookmarked = false
                )
            )
        }
        if (results.isEmpty()) {
            throw IllegalStateException("Matchmaking returned no matching companion IDs (${System.currentTimeMillis() - startMs}ms).")
        }
        return results.sortedByDescending { it.compatibilityScore }
    }

    suspend fun runEoaOrchestration(
        userId: String,
        directive: String,
        is2faVerified: Boolean,
        bookings: List<SessionBooking>,
        encryptedMessageCount: Int
    ): EoaAgentRun {
        val totalGrossCents = bookings.sumOf { it.grossCentsUsd }
        val totalTaxCents = bookings.sumOf { it.taxWithheldCentsUsd }
        val prompt = """
            You are the Executive Orchestrator Agent (EOA) overseeing 5 autonomous sub-agents for Havenly (a verified platonic wellness companionship platform):
            1. Matchmaker AI Agent
            2. Fraud & Trust Sentinel
            3. Multi-Currency Tax & Compliance Agent
            4. E2E Crypto (AES-256-GCM) & 2FA Auth Guardian
            5. Predictive Maintenance & Mesh Load Balancer

            Live platform telemetry for this user scope:
            - Directive from operator: "$directive"
            - 2FA Shield Enabled: $is2faVerified
            - Session Bookings Count: ${bookings.size}
            - Gross Ledger Volume (USD cents): $totalGrossCents
            - Tax Withholding Reserve (USD cents): $totalTaxCents
            - AES-256-GCM Encrypted Packets Sent: $encryptedMessageCount

            Evaluate every sub-agent (0-100), determine fraudRiskLevel (LOW, MEDIUM, HIGH), activeAlertsCount, overallHealthScore, and give one actionable predictive maintenance or compliance recommendation.
        """.trimIndent()

        val startMs = System.currentTimeMillis()
        val response = withTimeout(60_000L) {
            eoaModel.generateContent(prompt)
        }
        val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        val raw = response.text ?: throw IllegalStateException("EOA returned an empty response.")
        val json = JSONObject(raw)

        return EoaAgentRun(
            userId = userId,
            directive = directive,
            eoaSummary = json.optString("eoaSummary", "Fleet telemetry audited."),
            overallHealthScore = json.optInt("overallHealthScore", 96).toLong().coerceIn(0L, 100L),
            fraudRiskLevel = FraudRiskLevel.fromRaw(json.optString("fraudRiskLevel", "LOW")).name,
            activeAlertsCount = json.optInt("activeAlertsCount", 0).toLong().coerceIn(0L, 500L),
            matchmakerScore = json.optInt("matchmakerScore", 95).toLong().coerceIn(0L, 100L),
            fraudSentinelScore = json.optInt("fraudSentinelScore", 98).toLong().coerceIn(0L, 100L),
            taxAgentScore = json.optInt("taxAgentScore", 96).toLong().coerceIn(0L, 100L),
            cryptoGuardianScore = json.optInt("cryptoGuardianScore", if (is2faVerified) 99 else 82).toLong().coerceIn(0L, 100L),
            infraBalancerScore = json.optInt("infraBalancerScore", 97).toLong().coerceIn(0L, 100L),
            recommendedAction = json.optString("recommendedAction", "Maintain nominal cluster autoscaling."),
            latencyMs = elapsedMs
        )
    }

    suspend fun generateTaxComplianceBrief(
        bookings: List<SessionBooking>,
        currency: CurrencyCode,
        is2faVerified: Boolean
    ): String {
        val grossUsd = bookings.sumOf { it.grossCentsUsd }
        val taxUsd = bookings.sumOf { it.taxWithheldCentsUsd }
        val prompt = """
            Produce a concise 4-bullet multi-currency tax & compliance brief (under 110 words total) for a wellness companion session ledger:
            - Display Currency: ${currency.code} (${currency.label})
            - Sessions Logged: ${bookings.size}
            - Gross Volume Equivalent: ${currency.formatFromUsdCents(grossUsd)}
            - Estimated Tax Withholding Reserve (20%): ${currency.formatFromUsdCents(taxUsd)}
            - 2FA Shield Verified: $is2faVerified
            Include GDPR data-minimization status, quarterly 1099/VAT ledger readiness, and payout-ledger audit note. Do not use markdown code fences.
        """.trimIndent()

        val response = withTimeout(60_000L) {
            taxReportModel.generateContent(prompt)
        }
        return response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Tax compliance model returned an empty report.")
    }

    suspend fun generateCompanionChatReply(
        companion: CompanionProfile,
        memberName: String,
        recentMessages: List<com.example.data.model.LiveChatMessage>,
        latestUserMessage: String
    ): String {
        val transcript = recentMessages.takeLast(6).joinToString("\n") { msg ->
            "${msg.senderName}: ${msg.text}"
        }
        val prompt = """
            You are ${companion.name}, a verified PG-13 platonic wellness & cuddle therapy companion based in ${companion.city} (${companion.tagline}).
            Specialties: ${companion.specialties.joinToString(", ")}.
            You are chatting in real time with member "$memberName".
            Keep the reply warm, grounded, supportive, strictly platonic/PG-13, and under 65 words. Do not use markdown code fences.

            Recent conversation:
            $transcript
            $memberName: $latestUserMessage

            Reply directly as ${companion.name}:
        """.trimIndent()

        val response = withTimeout(60_000L) {
            taxReportModel.generateContent(prompt)
        }
        return response.text?.trim()?.take(1400)?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Companion reply model returned empty text.")
    }
}
