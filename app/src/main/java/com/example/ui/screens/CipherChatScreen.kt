package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.CompanionCatalog
import com.example.data.model.CompanionProfile
import com.example.security.MessageCrypto
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.TrustEmerald
import com.example.ui.viewmodel.DecryptedChatItem
import com.example.ui.viewmodel.UiState

@Composable
fun CipherChatScreen(
    currentUserId: String,
    companions: List<CompanionProfile>,
    selectedCompanionId: String,
    messagesState: UiState<List<DecryptedChatItem>>,
    showCipherInspector: Boolean,
    onSelectCompanion: (String) -> Unit,
    onToggleCipherInspector: (Boolean) -> Unit,
    onSendMessage: (CompanionProfile, String) -> Unit,
    onSimulateCompanionPing: (CompanionProfile) -> Unit
) {
    val activeCompanion = remember(selectedCompanionId, companions) {
        companions.firstOrNull { it.id == selectedCompanionId } ?: CompanionCatalog.companions.first()
    }
    val allMessages = (messagesState as? UiState.Success)?.data.orEmpty()
    val threadMessages = remember(allMessages, activeCompanion.id) {
        allMessages.filter { it.companionId == activeCompanion.id }
    }
    val sessionFingerprint = remember(currentUserId, activeCompanion.id) {
        MessageCrypto.fingerprint(currentUserId, activeCompanion.id)
    }

    var draftText by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Companion thread switcher
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(companions, key = { it.id }) { companion ->
                FilterChip(
                    selected = companion.id == activeCompanion.id,
                    onClick = { onSelectCompanion(companion.id) },
                    label = { Text(companion.name) }
                )
            }
        }

        // AES-256-GCM Security Banner + Cipher Inspector Toggle
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.EnhancedEncryption,
                            contentDescription = null,
                            tint = TrustEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "AES-256-GCM E2E Channel",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Text(
                        text = "Key: $sessionFingerprint",
                        style = MonoMetricStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Cipher",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = showCipherInspector,
                        onCheckedChange = onToggleCipherInspector,
                        modifier = Modifier.testTag("cipher_inspector_switch")
                    )
                }
            }
        }

        // Quick companion check-in trigger for testing round-trip encryption
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Thread with ${activeCompanion.name}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            AssistChip(
                onClick = { onSimulateCompanionPing(activeCompanion) },
                leadingIcon = {
                    Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = { Text("Request Check-In") },
                modifier = Modifier.testTag("request_checkin_chip")
            )
        }

        // Messages Feed
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (messagesState is UiState.Error) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = messagesState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else if (threadMessages.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "Zero-Plaintext Sanctuary Thread",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Messages are sealed on-device with AES-256-GCM before syncing to Firestore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(threadMessages, key = { it.id }) { msg ->
                        val isUser = msg.senderRole == "USER"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.widthIn(max = 320.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = msg.plaintext,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (showCipherInspector) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text(
                                                    text = "IV: ${msg.ivBase64}",
                                                    style = MonoMetricStyle,
                                                    color = TrustEmerald
                                                )
                                                Text(
                                                    text = "CIPHER: ${msg.cipherText.take(44)}...",
                                                    style = MonoMetricStyle,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Composer
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draftText,
                onValueChange = { if (it.length <= 1000) draftText = it },
                placeholder = { Text("Encrypted note to ${activeCompanion.name}...") },
                shape = RoundedCornerShape(16.dp),
                maxLines = 3,
                modifier = Modifier
                    .weight(1f)
                    .testTag("encrypted_message_input")
            )
            IconButton(
                onClick = {
                    val text = draftText.trim()
                    if (text.isNotEmpty()) {
                        onSendMessage(activeCompanion, text)
                        draftText = ""
                    }
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .size(52.dp)
                    .testTag("send_encrypted_message_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send encrypted message"
                )
            }
        }
    }
}
