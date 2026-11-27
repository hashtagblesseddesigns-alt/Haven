package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CompanionProfile
import com.example.data.model.CurrencyCode
import com.example.data.model.VerificationTier
import com.example.notifications.HavenlyNotificationHelper
import com.example.ui.auth.signOut
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.TrustEmerald
import com.example.ui.viewmodel.HavenlyViewModel
import com.example.ui.viewmodel.RealtimeAlertEvent
import com.example.ui.viewmodel.UiState

enum class HavenlyTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    LOUNGE("lounge", "Lounge", Icons.Rounded.Spa, Icons.Outlined.Spa),
    CHAT("chat", "Chat", Icons.AutoMirrored.Rounded.Chat, Icons.AutoMirrored.Outlined.Chat),
    CIPHER("cipher", "Cipher", Icons.Rounded.Lock, Icons.Outlined.Lock),
    LEDGER("ledger", "Ledger", Icons.Rounded.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    EARNINGS("earnings", "Earnings", Icons.AutoMirrored.Rounded.ShowChart, Icons.AutoMirrored.Rounded.ShowChart),
    PROFILE("profile", "Profile", Icons.Rounded.Person, Icons.Outlined.Person),
    EOA_OPS("eoa_ops", "EOA Ops", Icons.Rounded.Hub, Icons.Outlined.Hub)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HavenlyAppShell(
    currentUserId: String,
    viewModel: HavenlyViewModel,
    pendingNavDestination: String? = null,
    pendingCompanionId: String? = null,
    onConsumeNavExtras: () -> Unit = {},
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val matchesState by viewModel.matchesState.collectAsStateWithLifecycle()
    val bookingsState by viewModel.bookingsState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val eoaRunsState by viewModel.eoaRunsState.collectAsStateWithLifecycle()
    val activityLogsState by viewModel.activityLogsState.collectAsStateWithLifecycle()
    val liveChatsState by viewModel.liveChatsState.collectAsStateWithLifecycle()
    val hasConfiguredPin by viewModel.hasConfiguredPin.collectAsStateWithLifecycle()
    val showCipherInspector by viewModel.showCipherInspector.collectAsStateWithLifecycle()
    val cachedFcmToken by viewModel.cachedFcmToken.collectAsStateWithLifecycle()
    val selectedCompanionId by viewModel.selectedCompanionId.collectAsStateWithLifecycle()
    val matchmakingState by viewModel.matchmakingState.collectAsStateWithLifecycle()
    val eoaCommandState by viewModel.eoaCommandState.collectAsStateWithLifecycle()
    val taxReportState by viewModel.taxReportState.collectAsStateWithLifecycle()
    val profileSaveState by viewModel.profileSaveState.collectAsStateWithLifecycle()
    val autoReplyEnabled by viewModel.autoReplyEnabled.collectAsStateWithLifecycle()
    val liveReplyState by viewModel.liveReplyState.collectAsStateWithLifecycle()
    val bannerFeedback by viewModel.bannerFeedback.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(HavenlyTab.LOUNGE) }
    var showTwoFactorDialog by remember { mutableStateOf(false) }
    var companionToBook by remember { mutableStateOf<CompanionProfile?>(null) }
    var hasPromptedNotifications by rememberSaveable { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.syncFcmToken(showToastOnSuccess = false)
            viewModel.showBannerMessage("Real-time notifications enabled for chat & payments.")
        } else {
            viewModel.showBannerMessage("Notifications permission denied. Enable anytime in Profile.")
        }
    }

    LaunchedEffect(pendingNavDestination, pendingCompanionId) {
        val dest = pendingNavDestination
        val compId = pendingCompanionId
        if (!compId.isNullOrBlank()) {
            viewModel.selectCompanionForChat(compId)
        }
        if (!dest.isNullOrBlank()) {
            currentTab = when (dest) {
                HavenlyNotificationHelper.DEST_CHAT -> HavenlyTab.CHAT
                HavenlyNotificationHelper.DEST_CIPHER -> HavenlyTab.CIPHER
                HavenlyNotificationHelper.DEST_LEDGER -> HavenlyTab.LEDGER
                else -> currentTab
            }
        }
        if (!dest.isNullOrBlank() || !compId.isNullOrBlank()) {
            onConsumeNavExtras()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.alertEvents.collect { event ->
            when (event) {
                is RealtimeAlertEvent.ChatAlert -> {
                    HavenlyNotificationHelper.showChatAlert(
                        context = context,
                        title = event.title,
                        body = event.body,
                        companionId = event.companionId,
                        destination = if (event.isEncrypted) {
                            HavenlyNotificationHelper.DEST_CIPHER
                        } else {
                            HavenlyNotificationHelper.DEST_CHAT
                        }
                    )
                }

                is RealtimeAlertEvent.PaymentAlert -> {
                    HavenlyNotificationHelper.showPaymentAlert(
                        context = context,
                        title = event.title,
                        body = event.body,
                        referenceId = event.bookingId
                    )
                }
            }
        }
    }

    BackHandler(enabled = currentTab != HavenlyTab.LOUNGE) {
        currentTab = HavenlyTab.LOUNGE
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(bannerFeedback) {
        val msg = bannerFeedback
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    val profile = (profileState as? UiState.Success)?.data
    val is2fa = profile?.verificationStatus == VerificationTier.VERIFIED_2FA.name
    val activeCurrency = CurrencyCode.fromRaw(profile?.preferredCurrency)

    LaunchedEffect(currentTab, profile?.notificationsEnabled) {
        val isAlertTab = currentTab == HavenlyTab.CHAT || currentTab == HavenlyTab.CIPHER || currentTab == HavenlyTab.LEDGER
        val alertsWanted = profile?.notificationsEnabled ?: true
        if (isAlertTab && alertsWanted && !hasPromptedNotifications &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !HavenlyNotificationHelper.hasNotificationPermission(context)
        ) {
            hasPromptedNotifications = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (currentTab) {
                                HavenlyTab.LOUNGE -> "Havenly Sanctuary"
                                HavenlyTab.CHAT -> "Live Firestore Chat"
                                HavenlyTab.CIPHER -> "AES-256-GCM Cipher"
                                HavenlyTab.LEDGER -> "Earnings & Tax Ledger"
                                HavenlyTab.EARNINGS -> "Earnings Dashboard"
                                HavenlyTab.EOA_OPS -> "EOA & Admin Telemetry"
                                HavenlyTab.PROFILE -> "Member Profile"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    actions = {
                        IconButton(
                            onClick = { currentTab = HavenlyTab.EARNINGS },
                            modifier = Modifier.testTag("topbar_earnings_chart_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ShowChart,
                                contentDescription = "Earnings Dashboard",
                                tint = if (currentTab == HavenlyTab.EARNINGS) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        IconButton(
                            onClick = { currentTab = HavenlyTab.EOA_OPS },
                            modifier = Modifier.testTag("topbar_eoa_ops_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Hub,
                                contentDescription = "EOA & Admin Telemetry",
                                tint = if (currentTab == HavenlyTab.EOA_OPS) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        IconButton(
                            onClick = { showTwoFactorDialog = true },
                            modifier = Modifier.testTag("topbar_2fa_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = "2FA Security Shield",
                                tint = if (is2fa) TrustEmerald else AlertAmber
                            )
                        }
                        IconButton(
                            onClick = {
                                signOut(
                                    credentialManager = credentialManager,
                                    scope = scope,
                                    onSignOutComplete = onSignedOut
                                )
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = "Sign out"
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    val primaryTabs = listOf(
                        HavenlyTab.LOUNGE,
                        HavenlyTab.CHAT,
                        HavenlyTab.CIPHER,
                        HavenlyTab.LEDGER,
                        HavenlyTab.PROFILE
                    )
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        primaryTabs.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1, softWrap = false) },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                        Spacer(Modifier.width(4.dp))
                        HavenlyTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_${tab.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (currentTab) {
                        HavenlyTab.LOUNGE -> LoungeScreen(
                            profileState = profileState,
                            matchesState = matchesState,
                            matchmakingState = matchmakingState,
                            companions = viewModel.companions,
                            onOpenTwoFactor = { showTwoFactorDialog = true },
                            onSwitchRole = viewModel::setRole,
                            onRunMatchmaking = viewModel::runAiMatchmaking,
                            onToggleBookmark = viewModel::toggleCompanionBookmark,
                            onOpenEncryptedChat = { companion ->
                                viewModel.selectCompanionForChat(companion.id)
                                currentTab = HavenlyTab.CIPHER
                            },
                            onBookCompanion = { companion ->
                                companionToBook = companion
                            }
                        )

                        HavenlyTab.CHAT -> LiveChatScreen(
                            companions = viewModel.companions,
                            selectedCompanionId = selectedCompanionId,
                            liveChatsState = liveChatsState,
                            autoReplyEnabled = autoReplyEnabled,
                            liveReplyState = liveReplyState,
                            onSelectCompanion = viewModel::selectCompanionForChat,
                            onToggleAutoReply = viewModel::setLiveAutoReplyEnabled,
                            onSendMessage = viewModel::sendLiveChatMessage,
                            onRequestCompanionReply = viewModel::requestCompanionLiveReply,
                            onDeleteMessage = viewModel::deleteLiveChatMessage,
                            onOpenEncryptedChat = { currentTab = HavenlyTab.CIPHER }
                        )

                        HavenlyTab.CIPHER -> CipherChatScreen(
                            currentUserId = currentUserId,
                            companions = viewModel.companions,
                            selectedCompanionId = selectedCompanionId,
                            messagesState = messagesState,
                            showCipherInspector = showCipherInspector,
                            onSelectCompanion = viewModel::selectCompanionForChat,
                            onToggleCipherInspector = viewModel::toggleCipherInspector,
                            onSendMessage = viewModel::sendEncryptedMessage,
                            onSimulateCompanionPing = viewModel::simulateCompanionCheckIn
                        )

                        HavenlyTab.LEDGER -> LedgerScreen(
                            profileState = profileState,
                            bookingsState = bookingsState,
                            taxReportState = taxReportState,
                            onSelectCurrency = viewModel::setPreferredCurrency,
                            onAdvanceBooking = viewModel::advanceBooking,
                            onGenerateTaxReport = viewModel::generateTaxComplianceReport,
                            onOpenEarningsChart = { currentTab = HavenlyTab.EARNINGS }
                        )

                        HavenlyTab.EARNINGS -> {
                            var chartViewIndex by rememberSaveable { mutableIntStateOf(0) }
                            Column(modifier = Modifier.fillMaxSize()) {
                                SingleChoiceSegmentedButtonRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    SegmentedButton(
                                        selected = chartViewIndex == 0,
                                        onClick = { chartViewIndex = 0 },
                                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                        modifier = Modifier.testTag("earnings_view_line_dashboard")
                                    ) {
                                        Text("Line Trend")
                                    }
                                    SegmentedButton(
                                        selected = chartViewIndex == 1,
                                        onClick = { chartViewIndex = 1 },
                                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                        modifier = Modifier.testTag("earnings_view_daily_bars")
                                    ) {
                                        Text("Daily Activity")
                                    }
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    if (chartViewIndex == 0) {
                                        EarningsDashboard(
                                            profileState = profileState,
                                            bookingsState = bookingsState,
                                            onSelectCurrency = viewModel::setPreferredCurrency,
                                            onOpenLounge = { currentTab = HavenlyTab.LOUNGE },
                                            onOpenLedger = { currentTab = HavenlyTab.LEDGER }
                                        )
                                    } else {
                                        EarningsTrackerScreen(
                                            profileState = profileState,
                                            bookingsState = bookingsState,
                                            messagesState = messagesState,
                                            liveChatsState = liveChatsState,
                                            activityLogsState = activityLogsState,
                                            onSelectCurrency = viewModel::setPreferredCurrency,
                                            onOpenLounge = { currentTab = HavenlyTab.LOUNGE },
                                            onOpenLedger = { currentTab = HavenlyTab.LEDGER }
                                        )
                                    }
                                }
                            }
                        }

                        HavenlyTab.EOA_OPS -> EoaOpsScreen(
                            eoaRunsState = eoaRunsState,
                            activityLogsState = activityLogsState,
                            eoaCommandState = eoaCommandState,
                            onRunEoaDirective = viewModel::runEoaOrchestration
                        )

                        HavenlyTab.PROFILE -> ProfileScreen(
                            currentUserId = currentUserId,
                            profileState = profileState,
                            profileSaveState = profileSaveState,
                            cachedFcmToken = cachedFcmToken,
                            onSaveProfileDetails = viewModel::updateProfileDetails,
                            onUploadAvatarBase64 = viewModel::updateProfileAvatar,
                            onRemoveAvatar = viewModel::removeProfileAvatar,
                            onSelectRole = viewModel::setRole,
                            onSelectCurrency = viewModel::setPreferredCurrency,
                            onToggleNotifications = viewModel::setNotificationsEnabled,
                            onRefreshFcmToken = { viewModel.syncFcmToken(showToastOnSuccess = true) },
                            onOpenTwoFactor = { showTwoFactorDialog = true },
                            onSignOutClick = {
                                signOut(
                                    credentialManager = credentialManager,
                                    scope = scope,
                                    onSignOutComplete = onSignedOut
                                )
                            },
                            onShowMessage = viewModel::showBannerMessage
                        )
                    }
                }
            }
        }
    }

    if (showTwoFactorDialog) {
        TwoFactorShieldDialog(
            hasConfiguredPin = hasConfiguredPin,
            onDismiss = { showTwoFactorDialog = false },
            onSubmitPin = { pin, isConfiguring, onError ->
                viewModel.setupOrVerifyTwoFactor(
                    pin = pin,
                    isConfiguring = isConfiguring,
                    onVerified = { showTwoFactorDialog = false },
                    onInvalidPin = onError
                )
            }
        )
    }

    val bookingTarget = companionToBook
    if (bookingTarget != null) {
        BookSessionDialog(
            companion = bookingTarget,
            preferredCurrency = activeCurrency,
            onDismiss = { companionToBook = null },
            onConfirmBooking = { title, minutes, slot ->
                viewModel.bookSession(bookingTarget, title, minutes, slot)
                companionToBook = null
            }
        )
    }
}
