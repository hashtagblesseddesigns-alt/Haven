package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.CurrencyCode
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.VerificationTier
import com.example.notifications.HavenlyNotificationHelper
import com.example.security.AvatarImageUtils
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.TrustEmerald
import com.example.ui.viewmodel.AiTaskState
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    currentUserId: String,
    profileState: UiState<UserProfile?>,
    profileSaveState: AiTaskState,
    cachedFcmToken: String = "",
    onSaveProfileDetails: (displayName: String, wellnessFocus: String) -> Unit,
    onUploadAvatarBase64: (String) -> Unit,
    onRemoveAvatar: () -> Unit,
    onSelectRole: (UserRole) -> Unit,
    onSelectCurrency: (CurrencyCode) -> Unit,
    onToggleNotifications: (Boolean) -> Unit = {},
    onRefreshFcmToken: () -> Unit = {},
    onOpenTwoFactor: () -> Unit,
    onSignOutClick: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val profile = (profileState as? UiState.Success)?.data
    val currentEmail = remember(currentUserId) { Firebase.auth.currentUser?.email.orEmpty() }
    val currentRole = UserRole.fromRaw(profile?.role)
    val currentCurrency = CurrencyCode.fromRaw(profile?.preferredCurrency)
    val is2fa = profile?.verificationStatus == VerificationTier.VERIFIED_2FA.name

    var displayNameInput by rememberSaveable { mutableStateOf("") }
    var wellnessFocusInput by rememberSaveable { mutableStateOf("") }
    var lastSyncedKey by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(profile?.displayName, profile?.wellnessFocus) {
        if (profile != null) {
            val syncKey = "${profile.displayName}|${profile.wellnessFocus}"
            if (lastSyncedKey.isEmpty()) {
                displayNameInput = profile.displayName
                wellnessFocusInput = profile.wellnessFocus
                lastSyncedKey = syncKey
            } else if (lastSyncedKey != syncKey) {
                if (displayNameInput.isBlank()) displayNameInput = profile.displayName
                if (wellnessFocusInput.isBlank()) wellnessFocusInput = profile.wellnessFocus
                lastSyncedKey = syncKey
            }
        }
    }

    var pendingCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var isProcessingPhoto by remember { mutableStateOf(false) }
    var cameraPermissionDenied by remember { mutableStateOf(false) }

    fun handleCapturedUri(uri: Uri) {
        scope.launch {
            isProcessingPhoto = true
            AvatarImageUtils.encodeAvatarFromUri(context, uri)
                .onSuccess { encoded ->
                    onUploadAvatarBase64(encoded)
                }
                .onFailure { err ->
                    onShowMessage(err.localizedMessage ?: "Could not process selected image.")
                }
            isProcessingPhoto = false
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uriStr = pendingCameraUriString
        pendingCameraUriString = null
        if (success && !uriStr.isNullOrBlank()) {
            handleCapturedUri(Uri.parse(uriStr))
        }
    }

    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            handleCapturedUri(uri)
        }
    }

    fun launchCameraCapture() {
        runCatching {
            AvatarImageUtils.createTempCameraUri(context)
        }.onSuccess { uri ->
            pendingCameraUriString = uri.toString()
            takePictureLauncher.launch(uri)
        }.onFailure { err ->
            onShowMessage(err.localizedMessage ?: "Unable to initialize camera storage.")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraPermissionDenied = false
            launchCameraCapture()
        } else {
            cameraPermissionDenied = true
            onShowMessage("Camera permission is required to capture a profile photo.")
        }
    }

    fun onTakeCameraPhotoClick() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            cameraPermissionDenied = false
            launchCameraCapture()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val avatarBitmap = remember(profile?.avatarBase64) {
        AvatarImageUtils.decodeAvatarBitmap(profile?.avatarBase64.orEmpty())
    }
    val effectiveName = profile?.displayName?.ifBlank { "Havenly Member" } ?: "Havenly Member"
    val initials = remember(effectiveName) {
        effectiveName.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.take(1).uppercase() }
            .ifBlank { "HM" }
    }

    val isSaving = profileSaveState is AiTaskState.Running || isProcessingPhoto

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (profileState is UiState.Loading) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Text("Syncing member profile…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (profileState is UiState.Error) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = profileState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // Hero Avatar & Camera Upload Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                MaterialTheme.colorScheme.tertiaryContainer
                                            )
                                        )
                                    )
                                    .border(
                                        width = 3.dp,
                                        color = if (is2fa) TrustEmerald else MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                                    .testTag("profile_avatar_preview"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (avatarBitmap != null) {
                                    Image(
                                        bitmap = avatarBitmap,
                                        contentDescription = "Profile picture for $effectiveName",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = initials,
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (isProcessingPhoto) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 3.dp
                                        )
                                    }
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                tonalElevation = 4.dp,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.PhotoCamera,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = effectiveName,
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("profile_header_name")
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text(currentRole.label) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Badge,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                                AssistChip(
                                    onClick = onOpenTwoFactor,
                                    label = {
                                        Text(if (is2fa) "2FA Verified" else "Activate 2FA")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (is2fa) Icons.Rounded.Verified else Icons.Rounded.Shield,
                                            contentDescription = null,
                                            tint = if (is2fa) TrustEmerald else AlertAmber,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = ::onTakeCameraPhotoClick,
                                enabled = !isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("take_camera_photo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhotoCamera,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Take Photo")
                            }

                            OutlinedButton(
                                onClick = {
                                    pickVisualMediaLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("pick_gallery_photo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Choose Photo")
                            }
                        }

                        if (!profile?.avatarBase64.isNullOrBlank()) {
                            TextButton(
                                onClick = onRemoveAvatar,
                                enabled = !isSaving,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("remove_photo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Remove Profile Picture")
                            }
                        }

                        AnimatedVisibility(visible = cameraPermissionDenied) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Camera access was denied. Grant Camera permission to snap a live portrait, or use Choose Photo to select an existing picture without extra permissions.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    TextButton(
                                        onClick = ::onTakeCameraPhotoClick,
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("retry_camera_permission_button")
                                    ) {
                                        Text("Retry Camera Permission")
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Photos are cropped and optimized on-device before syncing securely to your cloud profile.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Edit Display Name & Wellness Bio Card
            item {
                val trimmedName = displayNameInput.trim()
                val isNameBlank = trimmedName.isEmpty()

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Profile Identity",
                            style = MaterialTheme.typography.titleMedium
                        )

                        OutlinedTextField(
                            value = displayNameInput,
                            onValueChange = { if (it.length <= 80) displayNameInput = it },
                            label = { Text("Display Name") },
                            placeholder = { Text("How verified members see you") },
                            singleLine = true,
                            isError = isNameBlank && lastSyncedKey.isNotEmpty(),
                            supportingText = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        if (isNameBlank && lastSyncedKey.isNotEmpty()) "Display name is required" else "Visible across lounge & encrypted sessions"
                                    )
                                    Text("${displayNameInput.length}/80")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_display_name_input")
                        )

                        OutlinedTextField(
                            value = wellnessFocusInput,
                            onValueChange = { if (it.length <= 300) wellnessFocusInput = it },
                            label = { Text("Wellness Focus") },
                            placeholder = { Text("E.g., Mindful tea talks, quiet reading, decompression") },
                            minLines = 2,
                            maxLines = 4,
                            supportingText = {
                                Text("${wellnessFocusInput.length}/300 • Guides AI matchmaking compatibility")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_wellness_focus_input")
                        )

                        Button(
                            onClick = { onSaveProfileDetails(displayNameInput, wellnessFocusInput) },
                            enabled = !isNameBlank && !isSaving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("save_profile_button")
                        ) {
                            if (profileSaveState is AiTaskState.Running) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Saving…")
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Save Profile Changes")
                            }
                        }

                        if (profileSaveState is AiTaskState.Error) {
                            Text(
                                text = profileSaveState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Workspace Preferences & Security Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Workspace & Settlement Preferences",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = "Member Role",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UserRole.entries.forEach { role ->
                                FilterChip(
                                    selected = currentRole == role,
                                    onClick = { onSelectRole(role) },
                                    label = { Text(role.label) },
                                    modifier = Modifier.testTag("profile_role_${role.name.lowercase()}")
                                )
                            }
                        }

                        Text(
                            text = "Preferred Settlement Currency",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CurrencyCode.entries.forEach { code ->
                                FilterChip(
                                    selected = currentCurrency == code,
                                    onClick = { onSelectCurrency(code) },
                                    label = { Text("${code.code} (${code.symbol})") },
                                    modifier = Modifier.testTag("profile_currency_${code.code.lowercase()}")
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Google Account",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = currentEmail.ifBlank { "Google Credential Linked" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "UID: ${currentUserId.take(12)}…",
                                    style = MonoMetricStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            OutlinedButton(
                                onClick = onSignOutClick,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("profile_sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Sign Out")
                            }
                        }
                    }
                }
            }

            // Real-Time Alerts & Firebase Cloud Messaging Card
            item {
                var hasNotifPerm by remember {
                    mutableStateOf(HavenlyNotificationHelper.hasNotificationPermission(context))
                }
                val notifPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    hasNotifPerm = granted
                    if (granted) {
                        onToggleNotifications(true)
                        onRefreshFcmToken()
                    } else {
                        onShowMessage("Notification permission denied. Grant permission in Android settings to receive push banners.")
                    }
                }
                val alertsEnabled = profile?.notificationsEnabled ?: true
                val activeFcmToken = profile?.fcmToken?.ifBlank { cachedFcmToken } ?: cachedFcmToken

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    tint = if (alertsEnabled && hasNotifPerm) TrustEmerald else AlertAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Real-Time Push Alerts (FCM)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            Switch(
                                checked = alertsEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPerm) {
                                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        onToggleNotifications(enabled)
                                    }
                                },
                                modifier = Modifier.testTag("profile_notifications_switch")
                            )
                        }

                        Text(
                            text = "Receive instant alerts for incoming companion messages and booking/payment ledger updates via Firebase Cloud Messaging.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Button(
                                onClick = { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("grant_notifications_permission_button")
                            ) {
                                Text("Grant Notification Permission")
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "FCM Device Registration Token",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (activeFcmToken.isNotBlank()) {
                                        "${activeFcmToken.take(28)}…${activeFcmToken.takeLast(12)}"
                                    } else {
                                        "Pending Google Play Services / FCM registration"
                                    },
                                    style = MonoMetricStyle,
                                    color = if (activeFcmToken.isNotBlank()) TrustEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onRefreshFcmToken,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("refresh_fcm_token_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Sync Token")
                            }

                            OutlinedButton(
                                onClick = {
                                    if (activeFcmToken.isNotBlank()) {
                                        clipboardManager.setText(AnnotatedString(activeFcmToken))
                                        onShowMessage("FCM token copied to clipboard.")
                                    } else {
                                        onShowMessage("FCM token not generated yet.")
                                    }
                                },
                                enabled = activeFcmToken.isNotBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("copy_fcm_token_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Copy Token")
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
