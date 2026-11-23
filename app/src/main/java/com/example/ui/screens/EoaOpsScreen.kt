package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ActivityAuditLog
import com.example.data.model.EoaAgentRun
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.ChampagneSecondary
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.RiskCoral
import com.example.ui.theme.TelemetryBlue
import com.example.ui.theme.TrustEmerald
import com.example.ui.viewmodel.AiTaskState
import com.example.ui.viewmodel.UiState

@Composable
fun EoaOpsScreen(
    eoaRunsState: UiState<List<EoaAgentRun>>,
    activityLogsState: UiState<List<ActivityAuditLog>>,
    eoaCommandState: AiTaskState,
    onRunEoaDirective: (String) -> Unit
) {
    val runs = (eoaRunsState as? UiState.Success)?.data.orEmpty()
    val latestRun = runs.firstOrNull()
    val logs = (activityLogsState as? UiState.Success)?.data.orEmpty()

    var directiveText by rememberSaveable {
        mutableStateOf("Autopilot fleet audit: check latency spikes, fraud anomalies, and tax reserve parity")
    }
    var showArchitectureDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. EOA Executive Command Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Hub,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "EOA Agent Command",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Executive Orchestrator overseeing 5 sub-agents",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { showArchitectureDialog = true },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("architecture_specs_button")
                        ) {
                            Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Specs")
                        }
                    }

                    OutlinedTextField(
                        value = directiveText,
                        onValueChange = { if (it.length <= 450) directiveText = it },
                        label = { Text("Orchestrator Autopilot Directive") },
                        maxLines = 2,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("eoa_directive_input")
                    )

                    Button(
                        onClick = { onRunEoaDirective(directiveText) },
                        enabled = eoaCommandState !is AiTaskState.Running,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("run_eoa_audit_button")
                    ) {
                        if (eoaCommandState is AiTaskState.Running) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("EOA Auditing 5 Sub-Agents...")
                        } else {
                            Icon(Icons.Rounded.AutoMode, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Execute EOA Fleet Audit")
                        }
                    }

                    AnimatedVisibility(visible = eoaCommandState is AiTaskState.Success || eoaCommandState is AiTaskState.Error) {
                        val isError = eoaCommandState is AiTaskState.Error
                        val msg = when (eoaCommandState) {
                            is AiTaskState.Success -> eoaCommandState.message
                            is AiTaskState.Error -> eoaCommandState.message
                            else -> ""
                        }
                        Surface(
                            color = if (isError) RiskCoral.copy(alpha = 0.14f) else TrustEmerald.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isError) RiskCoral else TrustEmerald,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Sub-Agent Performance Analytics & Fleet Health Canvas Gauge
        item {
            val health = latestRun?.overallHealthScore?.toInt() ?: 98
            val fraudRisk = latestRun?.fraudRiskLevel ?: "LOW"
            val matchmaker = latestRun?.matchmakerScore?.toInt() ?: 96
            val fraudSentinel = latestRun?.fraudSentinelScore?.toInt() ?: 99
            val taxAgent = latestRun?.taxAgentScore?.toInt() ?: 97
            val cryptoGuardian = latestRun?.cryptoGuardianScore?.toInt() ?: 98
            val infraBalancer = latestRun?.infraBalancerScore?.toInt() ?: 97

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        FleetHealthGauge(score = health)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Sub-Agent Telemetry",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Fraud Risk: $fraudRisk • Alerts: ${latestRun?.activeAlertsCount ?: 0}",
                                style = MonoMetricStyle,
                                color = when (fraudRisk) {
                                    "HIGH" -> RiskCoral
                                    "MEDIUM" -> AlertAmber
                                    else -> TrustEmerald
                                }
                            )
                            Text(
                                text = latestRun?.eoaSummary
                                    ?: "Execute an EOA audit to inspect live Gemini sub-agent diagnostics.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    SubAgentBar("1. Matchmaker AI Agent", matchmaker, MaterialTheme.colorScheme.primary)
                    SubAgentBar("2. Fraud & Trust Sentinel", fraudSentinel, TrustEmerald)
                    SubAgentBar("3. Multi-Currency Tax Agent", taxAgent, ChampagneSecondary)
                    SubAgentBar("4. AES-256 & 2FA Guardian", cryptoGuardian, TelemetryBlue)
                    SubAgentBar("5. Predictive Mesh Balancer", infraBalancer, MaterialTheme.colorScheme.tertiary)

                    if (latestRun != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "PREDICTIVE MAINTENANCE ADVISORY (${latestRun.latencyMs}ms)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ChampagneSecondary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = latestRun.recommendedAction,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Real-Time Admin Activity & Transaction Audit Logs
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Real-Time Activity & Audit Stream",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${logs.size} events",
                    style = MonoMetricStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (logs.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Activity telemetry will stream here as you verify 2FA, dispatch encrypted messages, book sessions, or trigger EOA audits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(logs.take(25), key = { it.id }) { log ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = when (log.severity) {
                                "CRITICAL" -> RiskCoral
                                "WARN" -> AlertAmber
                                else -> TrustEmerald
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = log.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = log.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = log.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showArchitectureDialog) {
        ArchitectureSpecsDialog(onDismiss = { showArchitectureDialog = false })
    }
}

@Composable
private fun FleetHealthGauge(score: Int) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val arcColor = when {
        score >= 90 -> TrustEmerald
        score >= 75 -> AlertAmber
        else -> RiskCoral
    }
    Box(
        modifier = Modifier.size(84.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 9.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = arcColor,
                startAngle = 135f,
                sweepAngle = 270f * (score.coerceIn(0, 100) / 100f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score%",
                style = MonoMetricStyle.copy(fontWeight = FontWeight.Bold),
                color = arcColor
            )
            Text(
                text = "SLA",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SubAgentBar(
    label: String,
    score: Int,
    accent: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall)
            Text(text = "$score/100", style = MonoMetricStyle, color = accent)
        }
        val track = MaterialTheme.colorScheme.surfaceContainerHigh
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            drawRoundRect(
                color = track,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )
            drawRoundRect(
                color = accent,
                size = Size(size.width * (score.coerceIn(0, 100) / 100f), size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )
        }
    }
}

@Composable
private fun ArchitectureSpecsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Havenly Platform & API Specs", style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Android Client Architecture",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "• Auth & Integrity: Google Credential Manager + Firebase Auth + App Check (reCAPTCHA Enterprise in release) + local 2FA PIN shield.\n" +
                        "• Data Layer: Firebase Firestore Enterprise Edition with zero-trust owner isolation & immutable activity audit logs.\n" +
                        "• E2E Crypto: On-device AES-256-GCM authenticated encryption (12-byte IV, 128-bit GCM tag) before Firestore write.\n" +
                        "• AI Mesh: Firebase AI Logic (Gemini 3.8 Flash) driving Matchmaker AI, EOA Fleet Audits & Tax Briefs.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Reference Cloud & Compliance Blueprint",
                    style = MaterialTheme.typography.titleSmall,
                    color = ChampagneSecondary
                )
                Text(
                    text = "• GDPR Data Minimization: Split user scope, zero plaintext chat persistence, verifiable audit trail.\n" +
                        "• Creator Program Compliance: Strictly PG-13 platonic companionship & multi-currency session/tax ledger without live ACH/bank transfers.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
