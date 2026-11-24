package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.MarcoConfig
import com.example.domain.model.MarcoUiState
import com.example.ui.theme.GlassPanelSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LiveEmerald
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StandbyAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidObsidian

@Composable
fun SettingsScreen(
    uiState: MarcoUiState,
    onSelectVoice: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onWakeSensitivityChanged: (Float) -> Unit,
    onToggleBackgroundService: (Boolean) -> Unit,
    onToggleNotificationConsent: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Intent,
    onOpenBatterySettings: () -> Intent,
    onPermissionsUpdated: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onPermissionsUpdated()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .background(VoidObsidian)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Marco",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Marco Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Voice Persona Section
            SettingsSectionCard(
                title = "Male Voice Profile",
                icon = Icons.Filled.RecordVoiceOver
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarcoConfig.MALE_VOICES.forEach { voice ->
                        val selected = uiState.settings.selectedVoice == voice.id
                        Surface(
                            color = if (selected) NeonCyan.copy(alpha = 0.14f) else VoidObsidian.copy(alpha = 0.55f),
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(
                                1.dp,
                                if (selected) NeonCyan else HairlineBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clickable { onSelectVoice(voice.id) }
                                .testTag("voice_option_${voice.id.lowercase()}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = voice.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = voice.vibe,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Selected voice",
                                        tint = NeonCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Live Model Section
            SettingsSectionCard(
                title = "Gemini Live Model",
                icon = Icons.Filled.GraphicEq
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarcoConfig.LIVE_MODELS.forEach { model ->
                        val selected = uiState.settings.selectedModel == model.id
                        Surface(
                            color = if (selected) NeonPurple.copy(alpha = 0.16f) else VoidObsidian.copy(alpha = 0.55f),
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(
                                1.dp,
                                if (selected) NeonPurple else HairlineBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clickable { onSelectModel(model.id) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = model.label,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = model.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Selected model",
                                        tint = NeonPurple
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Wake-Word & Background Service
            SettingsSectionCard(
                title = "Wake Word & Background Service",
                icon = Icons.Filled.NotificationsActive
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsToggleRow(
                        title = "\"Hey Marco\" Wake-Word",
                        subtitle = "Listen locally for Hey Marco / Marco in standby",
                        checked = uiState.settings.wakeWordEnabled,
                        onCheckedChange = onToggleWakeWord,
                        testTag = "wake_word_switch"
                    )

                    Column {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Wake Sensitivity",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = "${(uiState.settings.wakeSensitivity * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = NeonCyan
                            )
                        }
                        Slider(
                            value = uiState.settings.wakeSensitivity,
                            onValueChange = onWakeSensitivityChanged,
                            valueRange = 0.25f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = NeonCyan,
                                activeTrackColor = NeonCyan
                            )
                        )
                    }

                    SettingsToggleRow(
                        title = "Background Foreground Service",
                        subtitle = "Keep Marco available with persistent status notification",
                        checked = uiState.settings.backgroundServiceEnabled,
                        onCheckedChange = onToggleBackgroundService,
                        testTag = "background_service_switch"
                    )
                }
            }

            // Notification Reader Consent
            SettingsSectionCard(
                title = "Notification Reader (Consent Gated)",
                icon = Icons.Filled.NotificationsActive
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsToggleRow(
                        title = "Allow Marco to Read Notifications",
                        subtitle = if (uiState.isNotificationAccessGranted)
                            "Android Notification Access is granted"
                        else "Requires Android Notification Access below",
                        checked = uiState.settings.notificationReaderConsent,
                        onCheckedChange = onToggleNotificationConsent,
                        testTag = "notification_consent_switch"
                    )

                    OutlinedButton(
                        onClick = { context.startActivity(onOpenNotificationSettings()) },
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                    ) {
                        Text(
                            if (uiState.isNotificationAccessGranted)
                                "Notification Access Enabled • Manage"
                            else "Enable Android Notification Access"
                        )
                    }
                }
            }

            // Battery Optimization
            SettingsSectionCard(
                title = "Battery & Reliability",
                icon = Icons.Filled.BatteryChargingFull
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (uiState.isBatteryOptimizationIgnored)
                            "Battery optimization is set to Unrestricted for Marco."
                        else "Set Marco to Unrestricted battery mode so Android doesn't pause background wake-word listening.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.isBatteryOptimizationIgnored) LiveEmerald else TextSecondary
                    )
                    OutlinedButton(
                        onClick = { context.startActivity(onOpenBatterySettings()) },
                        border = BorderStroke(1.dp, StandbyAmber.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StandbyAmber),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                    ) {
                        Text("Open Battery Optimization Settings")
                    }
                }
            }

            // Permissions Checklist
            SettingsSectionCard(
                title = "Runtime Permissions",
                icon = Icons.Filled.Security
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    uiState.permissions.forEach { perm ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = perm.type.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = perm.type.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            if (perm.isGranted) {
                                Surface(
                                    color = LiveEmerald.copy(alpha = 0.16f),
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "GRANTED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LiveEmerald,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { permissionLauncher.launch(perm.type.manifestPermission) },
                                    border = BorderStroke(1.dp, NeonCyan),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("Grant")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        color = GlassPanelSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = VoidObsidian,
                checkedTrackColor = NeonCyan,
                uncheckedTrackColor = HairlineBorder
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
