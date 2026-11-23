package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CompanionProfile
import com.example.data.model.CurrencyCode
import com.example.data.model.MatchScore
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.VerificationTier
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.ChampagneSecondary
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.TrustEmerald
import com.example.ui.viewmodel.AiTaskState
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoungeScreen(
    profileState: UiState<UserProfile?>,
    matchesState: UiState<List<MatchScore>>,
    matchmakingState: AiTaskState,
    companions: List<CompanionProfile>,
    onOpenTwoFactor: () -> Unit,
    onSwitchRole: (UserRole) -> Unit,
    onRunMatchmaking: (String) -> Unit,
    onToggleBookmark: (CompanionProfile) -> Unit,
    onOpenEncryptedChat: (CompanionProfile) -> Unit,
    onBookCompanion: (CompanionProfile) -> Unit
) {
    val profile = (profileState as? UiState.Success)?.data
    val currency = CurrencyCode.fromRaw(profile?.preferredCurrency)
    val is2fa = profile?.verificationStatus == VerificationTier.VERIFIED_2FA.name
    val currentRole = UserRole.fromRaw(profile?.role)
    val matches = (matchesState as? UiState.Success)?.data.orEmpty()
    val matchMap = remember(matches) { matches.associateBy { it.companionId } }

    val sortedCompanions = remember(companions, matchMap) {
        companions.sortedByDescending { matchMap[it.id]?.compatibilityScore ?: 0L }
    }

    var wellnessPrompt by rememberSaveable {
        mutableStateOf("Quiet evening breathwork, cozy platonic cuddle therapy, and warm tea conversation")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Editorial Hero Lounge Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(178.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_lounge),
                    contentDescription = "Havenly sanctuary lounge",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.45f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (is2fa) TrustEmerald.copy(alpha = 0.18f) else AlertAmber.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(50),
                        onClick = onOpenTwoFactor,
                        modifier = Modifier.testTag("two_factor_badge_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = "2FA Verification Status",
                                tint = if (is2fa) TrustEmerald else AlertAmber,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (is2fa) "2FA SHIELD ACTIVE" else "TAP TO ENABLE 2FA SHIELD",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (is2fa) TrustEmerald else AlertAmber
                            )
                        }
                    }

                    Text(
                        text = "Platinum Wellness Lounge",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Verified platonic cuddle & mindfulness companions • PG-13 Trust Protocol",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Role Switcher (Client / Verified Companion Mode)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserRole.entries.forEach { role ->
                    FilterChip(
                        selected = currentRole == role,
                        onClick = { onSwitchRole(role) },
                        label = { Text(role.label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. Gemini AI Matchmaking Engine Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = ChampagneSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "AI Matchmaking Sub-Agent",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    OutlinedTextField(
                        value = wellnessPrompt,
                        onValueChange = { if (it.length <= 280) wellnessPrompt = it },
                        label = { Text("Your calm session mood & preferences") },
                        maxLines = 2,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("matchmaking_prompt_input")
                    )
                    Button(
                        onClick = { onRunMatchmaking(wellnessPrompt) },
                        enabled = matchmakingState !is AiTaskState.Running,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("matchmake_button")
                    ) {
                        if (matchmakingState is AiTaskState.Running) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Scoring Verified Roster...")
                        } else {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Rank Best Companion Matches")
                        }
                    }

                    AnimatedVisibility(visible = matchmakingState is AiTaskState.Success || matchmakingState is AiTaskState.Error) {
                        val isError = matchmakingState is AiTaskState.Error
                        val msg = when (matchmakingState) {
                            is AiTaskState.Success -> matchmakingState.message
                            is AiTaskState.Error -> matchmakingState.message
                            else -> ""
                        }
                        Surface(
                            color = if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                            else TrustEmerald.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isError) MaterialTheme.colorScheme.error else TrustEmerald,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. Verified Companion Roster
        items(sortedCompanions, key = { it.id }) { companion ->
            val match = matchMap[companion.id]
            CompanionCard(
                companion = companion,
                matchScore = match,
                currency = currency,
                onToggleBookmark = { onToggleBookmark(companion) },
                onChat = { onOpenEncryptedChat(companion) },
                onBook = { onBookCompanion(companion) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompanionCard(
    companion: CompanionProfile,
    matchScore: MatchScore?,
    currency: CurrencyCode,
    onToggleBookmark: () -> Unit,
    onChat: () -> Unit,
    onBook: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if ((matchScore?.compatibilityScore ?: 0L) >= 90L) ChampagneSecondary.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                RoundedCornerShape(22.dp)
            )
            .testTag("companion_card_${companion.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (companion.portraitRes != null) {
                    Image(
                        painter = painterResource(id = companion.portraitRes),
                        contentDescription = "${companion.name} portrait",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = companion.name.split(" ").mapNotNull { it.firstOrNull() }.joinToString(""),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = companion.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.Verified,
                            contentDescription = "Verified Companion",
                            tint = TrustEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "${companion.city} • ${companion.verifiedBadge}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = ChampagneSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${companion.rating} (${companion.completedSessions} sessions)",
                            style = MaterialTheme.typography.labelMedium,
                            color = ChampagneSecondary
                        )
                    }
                }
                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier.testTag("bookmark_${companion.id}")
                ) {
                    Icon(
                        imageVector = if (matchScore?.bookmarked == true) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Bookmark companion",
                        tint = if (matchScore?.bookmarked == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = companion.tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (matchScore != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AI FIT SCORE",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChampagneSecondary
                            )
                            Text(
                                text = "${matchScore.compatibilityScore}% • ${matchScore.recommendedFormat}",
                                style = MonoMetricStyle,
                                color = TrustEmerald
                            )
                        }
                        Text(
                            text = matchScore.rationale,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                companion.specialties.forEach { tag ->
                    AssistChip(
                        onClick = onBook,
                        label = { Text(tag, style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hourly Rate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${currency.formatFromUsdCents(companion.hourlyRateCentsUsd)}/hr",
                        style = MonoMetricStyle.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onChat,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("chat_${companion.id}")
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cipher")
                    }
                    Button(
                        onClick = onBook,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("book_${companion.id}")
                    ) {
                        Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Book")
                    }
                }
            }
        }
    }
}
