package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.MarcoConfig
import com.example.ui.theme.GlassPanelSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidObsidian

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .background(VoidObsidian)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
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
                    .testTag("about_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Marco",
                    tint = TextPrimary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "About Marco",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_marco_orb_art_1795493754862),
                contentDescription = "Marco Emblem",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
            )

            Text(
                text = "Marco AI Assistant",
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Male Personal Edition • Sanni Kumar",
                style = MaterialTheme.typography.labelMedium,
                color = NeonCyan
            )

            Surface(
                color = GlassPanelSurface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, NeonPurple.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    AboutMetaRow(label = "AI Assistant", value = MarcoConfig.ASSISTANT_NAME)
                    HorizontalDivider(color = HairlineBorder, modifier = Modifier.padding(vertical = 12.dp))
                    AboutMetaRow(label = "Assistant Gender", value = MarcoConfig.ASSISTANT_GENDER)
                    HorizontalDivider(color = HairlineBorder, modifier = Modifier.padding(vertical = 12.dp))
                    AboutMetaRow(label = "Developed by", value = MarcoConfig.DEVELOPER_NAME)
                    HorizontalDivider(color = HairlineBorder, modifier = Modifier.padding(vertical = 12.dp))
                    AboutMetaRow(label = "Version", value = MarcoConfig.APP_VERSION)
                }
            }

            Surface(
                color = GlassPanelSurface.copy(alpha = 0.72f),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(18.dp)
                ) {
                    Text(
                        text = "CORE ARCHITECTURE",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonCyan
                    )
                    Text(
                        text = "• Bidirectional Gemini Live Native Audio via Firebase AI Logic & App Check\n" +
                            "• 16kHz PCM16 Voice Capture with Hardware Echo Cancellation\n" +
                            "• 24kHz PCM16 Streaming Playback with Instant Barge-In Interruption\n" +
                            "• Local \"Hey Marco\" Wake-Word Foreground Service\n" +
                            "• Confirmation-Gated Android Device Control Tools",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutMetaRow(
    label: String,
    value: String
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
    }
}
