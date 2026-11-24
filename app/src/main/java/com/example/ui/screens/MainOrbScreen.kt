package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.ConnectionState
import com.example.domain.model.MarcoUiState
import com.example.domain.model.OrbState
import com.example.domain.model.PermissionType
import com.example.ui.components.ConfirmationOverlay
import com.example.ui.components.MarcoOrb
import com.example.ui.theme.AlertCrimson
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.GlassPanelSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LiveEmerald
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StandbyAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidObsidian

private val VOICE_QUICK_PROMPTS = listOf(
    "Marco, kya kar raha hai?",
    "Marco, YouTube kholo.",
    "Marco, ek joke suna.",
    "Marco, mujhe motivate kar.",
    "Marco, good morning!"
)

@Composable
fun MainOrbScreen(
    uiState: MarcoUiState,
    waveformBands: List<Float>,
    onOrbTapped: () -> Unit,
    onToggleAssistant: (Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onPauseToStandby: () -> Unit,
    onTriggerPrompt: (String) -> Unit,
    onConfirmAction: () -> Unit,
    onCancelAction: () -> Unit,
    onDismissBanner: () -> Unit,
    onPermissionsUpdated: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onPermissionsUpdated()
        if (granted) {
            onOrbTapped()
        }
    }

    val micGranted = uiState.permissions
        .firstOrNull { it.type == PermissionType.RECORD_AUDIO }
        ?.isGranted == true

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoidObsidian)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ElectricBlue.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.45f),
                        radius = size.minDimension * 0.8f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NeonPurple.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.15f),
                        radius = size.minDimension * 0.55f
                    )
                )
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .widthIn(max = 640.dp)
                .align(Alignment.Center)
        ) {
            // Top Telemetry HUD
            TopHudBar(
                uiState = uiState,
                onOpenSettings = onOpenSettings,
                onOpenAbout = onOpenAbout
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Transient alert / missing permission banner
            val bannerText = uiState.errorMessage ?: uiState.statusBanner
            AnimatedVisibility(visible = !bannerText.isNullOrBlank()) {
                Surface(
                    color = GlassPanelSurface.copy(alpha = 0.92f),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(
                        1.dp,
                        if (uiState.errorMessage != null) AlertCrimson.copy(alpha = 0.65f)
                        else NeonCyan.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            if (!micGranted) {
                                micPermissionLauncher.launch(PermissionType.RECORD_AUDIO.manifestPermission)
                            } else {
                                onDismissBanner()
                            }
                        }
                ) {
                    Text(
                        text = bannerText.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            // Center Stage: Animated Marco AI Orb
            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val targetOrbSize = (minOf(maxWidth, maxHeight) * 0.68f)
                    .coerceIn(210.dp, 330.dp)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    MarcoOrb(
                        orbState = uiState.orbState,
                        micLevel = uiState.micLevel,
                        speakerLevel = uiState.speakerLevel,
                        waveformBands = waveformBands,
                        orbSize = targetOrbSize,
                        onClick = {
                            if (!micGranted) {
                                micPermissionLauncher.launch(PermissionType.RECORD_AUDIO.manifestPermission)
                            } else {
                                onOrbTapped()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = resolveHeadlineForState(uiState),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val liveSubtitle = when {
                        uiState.activeToolName != null -> "Executing ${uiState.activeToolName}..."
                        uiState.liveMarcoCaption.isNotBlank() -> uiState.liveMarcoCaption
                        uiState.liveInputCaption.isNotBlank() -> "Sanni: ${uiState.liveInputCaption}"
                        uiState.connectionState == ConnectionState.STANDBY_WAKEWORD ->
                            "Say \"Hey Marco\" or tap the orb"
                        uiState.connectionState == ConnectionState.LIVE ->
                            "Speak naturally in Hinglish or English"
                        else -> "Hey Sanni! Tap the orb to connect"
                    }

                    Text(
                        text = liveSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (uiState.activeToolName != null) NeonCyan else TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .height(40.dp)
                    )
                }
            }

            // Quick Hinglish Voice Trigger Pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                VOICE_QUICK_PROMPTS.forEachIndexed { idx, prompt ->
                    Surface(
                        color = GlassPanelSurface.copy(alpha = 0.78f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable {
                                if (!micGranted) {
                                    micPermissionLauncher.launch(PermissionType.RECORD_AUDIO.manifestPermission)
                                } else {
                                    onTriggerPrompt(prompt)
                                }
                            }
                            .testTag("quick_prompt_chip_$idx")
                    ) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Bottom Futuristic Control Dock
            BottomControlDock(
                uiState = uiState,
                onToggleAssistant = onToggleAssistant,
                onToggleMute = onToggleMute,
                onOrbAction = {
                    if (!micGranted) {
                        micPermissionLauncher.launch(PermissionType.RECORD_AUDIO.manifestPermission)
                    } else if (uiState.connectionState == ConnectionState.LIVE) {
                        onPauseToStandby()
                    } else {
                        onOrbTapped()
                    }
                }
            )
        }

        // Confirmation Overlay for sensitive actions (Call, WhatsApp, Gmail)
        AnimatedVisibility(
            visible = uiState.pendingConfirmation != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp)
        ) {
            uiState.pendingConfirmation?.let { pending ->
                ConfirmationOverlay(
                    confirmation = pending,
                    onConfirm = onConfirmAction,
                    onCancel = onCancelAction
                )
            }
        }
    }
}

@Composable
private fun TopHudBar(
    uiState: MarcoUiState,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val (connLabel, connColor) = when (uiState.connectionState) {
        ConnectionState.LIVE -> "LIVE" to LiveEmerald
        ConnectionState.CONNECTING -> "CONNECTING" to NeonCyan
        ConnectionState.RECOVERING -> "RECOVERING" to StandbyAmber
        ConnectionState.STANDBY_WAKEWORD -> "WAKE STANDBY" to StandbyAmber
        ConnectionState.ERROR -> "ERROR" to AlertCrimson
        ConnectionState.OFFLINE -> "OFFLINE" to TextMuted
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Connection Status Pill
        Surface(
            color = GlassPanelSurface,
            shape = CircleShape,
            border = BorderStroke(1.dp, connColor.copy(alpha = 0.55f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(connColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = connLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = connColor
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Microphone Active Indicator
        val micColor = when {
            uiState.settings.isMuted -> AlertCrimson
            uiState.isMicActive -> NeonCyan
            else -> TextMuted
        }
        val micLabel = when {
            uiState.settings.isMuted -> "MUTED"
            uiState.connectionState == ConnectionState.LIVE -> "MIC LIVE"
            uiState.isWakeWordListening -> "WAKE MIC"
            else -> "MIC OFF"
        }

        Surface(
            color = GlassPanelSurface,
            shape = CircleShape,
            border = BorderStroke(1.dp, micColor.copy(alpha = 0.45f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Icon(
                    imageVector = if (uiState.settings.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = "Microphone status: $micLabel",
                    tint = micColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = micLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = micColor
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        IconButton(
            onClick = onOpenAbout,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("open_about_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "About Marco",
                tint = TextSecondary
            )
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("open_settings_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Marco Settings",
                tint = TextPrimary
            )
        }
    }
}

@Composable
private fun BottomControlDock(
    uiState: MarcoUiState,
    onToggleAssistant: (Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onOrbAction: () -> Unit
) {
    Surface(
        color = GlassPanelSurface.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Assistant Master Power Toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = "Assistant power",
                    tint = if (uiState.settings.assistantEnabled) NeonCyan else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = uiState.settings.assistantEnabled,
                    onCheckedChange = onToggleAssistant,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = VoidObsidian,
                        checkedTrackColor = NeonCyan,
                        uncheckedTrackColor = HairlineBorder
                    ),
                    modifier = Modifier.testTag("assistant_master_switch")
                )
            }

            // Central Live Session Action Button
            val isLive = uiState.connectionState == ConnectionState.LIVE
            Surface(
                color = if (isLive) NeonPurple else NeonCyan,
                shape = CircleShape,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onOrbAction)
                    .testTag("live_toggle_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = if (isLive) Icons.Filled.Pause else Icons.Filled.GraphicEq,
                        contentDescription = if (isLive) "Pause Live Session" else "Talk to Marco",
                        tint = if (isLive) TextPrimary else VoidObsidian,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isLive) "Standby" else "Talk Now",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isLive) TextPrimary else VoidObsidian
                    )
                }
            }

            // Mic Mute Toggle
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("mic_mute_button")
            ) {
                Icon(
                    imageVector = if (uiState.settings.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = if (uiState.settings.isMuted) "Unmute Microphone" else "Mute Microphone",
                    tint = if (uiState.settings.isMuted) AlertCrimson else NeonCyan
                )
            }
        }
    }
}

private fun resolveHeadlineForState(uiState: MarcoUiState): String {
    if (!uiState.settings.assistantEnabled) return "Marco Paused"
    return when (uiState.orbState) {
        OrbState.IDLE -> "Marco Offline"
        OrbState.STANDBY -> "Listening for \"Hey Marco\""
        OrbState.LISTENING -> if (uiState.settings.isMuted) "Mic Muted" else "Bol bhai, sun raha hoon"
        OrbState.THINKING -> "Soch raha hoon..."
        OrbState.SPEAKING -> "Marco"
    }
}
