package com.example.ui.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.MarcoConfig
import com.example.domain.model.PermissionStatus
import com.example.domain.model.PermissionType
import com.example.ui.theme.GlassPanelSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LiveEmerald
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidObsidian

@Composable
fun OnboardingPermissionsScreen(
    permissions: List<PermissionStatus>,
    onPermissionsChanged: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMissingWarning by remember { mutableStateOf(false) }

    val singlePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onPermissionsChanged()
    }

    val multiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onPermissionsChanged()
    }

    val requiredPermissionsToRequest = remember {
        buildList {
            add(PermissionType.RECORD_AUDIO.manifestPermission)
            add(PermissionType.READ_CONTACTS.manifestPermission)
            add(PermissionType.CALL_PHONE.manifestPermission)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(PermissionType.POST_NOTIFICATIONS.manifestPermission)
            }
        }.toTypedArray()
    }

    val micGranted = permissions.firstOrNull { it.type == PermissionType.RECORD_AUDIO }?.isGranted == true

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoidObsidian)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NeonPurple.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.2f, size.height * 0.12f),
                        radius = size.minDimension * 0.75f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NeonCyan.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.82f),
                        radius = size.minDimension * 0.7f
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
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .widthIn(max = 580.dp)
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_marco_orb_art_1795493754862),
                contentDescription = "Marco AI Emblem",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "MARCO AI • SANNI EDITION",
                style = MaterialTheme.typography.labelMedium,
                color = NeonCyan
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Permissions & Setup",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = GlassPanelSurface.copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "\"${MarcoConfig.INTRO_GREETING}\"",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(14.dp)
                )
            }

            if (showMissingWarning && !micGranted) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = NeonPurple.copy(alpha = 0.18f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = MarcoConfig.MISSING_PERMISSION_LINE,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            permissions.forEach { status ->
                PermissionOnboardingCard(
                    status = status,
                    onGrantClick = {
                        singlePermissionLauncher.launch(status.type.manifestPermission)
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    multiPermissionLauncher.launch(requiredPermissionsToRequest)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = VoidObsidian
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("grant_all_permissions_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Grant All Permissions")
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    if (!micGranted) {
                        showMissingWarning = true
                    }
                    onContinue()
                },
                border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("continue_to_marco_button")
            ) {
                Text(if (micGranted) "Launch Marco Core" else "Continue Anyway")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PermissionOnboardingCard(
    status: PermissionStatus,
    onGrantClick: () -> Unit
) {
    val icon: ImageVector = when (status.type) {
        PermissionType.RECORD_AUDIO -> Icons.Filled.Mic
        PermissionType.READ_CONTACTS -> Icons.Filled.Contacts
        PermissionType.CALL_PHONE -> Icons.Filled.Call
        PermissionType.POST_NOTIFICATIONS -> Icons.Filled.Notifications
    }

    Surface(
        color = GlassPanelSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = 1.dp,
            color = if (status.isGranted) LiveEmerald.copy(alpha = 0.45f) else HairlineBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Surface(
                color = (if (status.isGranted) LiveEmerald else NeonCyan).copy(alpha = 0.14f),
                shape = CircleShape,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = status.type.title,
                        tint = if (status.isGranted) LiveEmerald else NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.type.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = status.type.marcoPitch,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            if (status.isGranted) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "${status.type.title} granted",
                    tint = LiveEmerald,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                OutlinedButton(
                    onClick = onGrantClick,
                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.65f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("grant_${status.type.name.lowercase()}_button")
                ) {
                    Text("Allow")
                }
            }
        }
    }
}
