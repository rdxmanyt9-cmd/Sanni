package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.core.configureAppCheck
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.MainOrbScreen
import com.example.ui.screens.OnboardingPermissionsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MarcoTheme
import com.example.ui.theme.VoidObsidian
import com.example.ui.viewmodel.MarcoViewModel

private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_MAIN = "main"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_ABOUT = "about"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before UI or ViewModel creation
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            MarcoTheme {
                Surface(
                    color = VoidObsidian,
                    modifier = Modifier.fillMaxSize()
                ) {
                    MarcoRootNavigation()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
    }
}

@Composable
private fun MarcoRootNavigation(
    viewModel: MarcoViewModel = viewModel()
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val waveformBands by viewModel.waveformBands.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSystemStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!uiState.isInitialized) {
        return
    }

    val startRoute = if (uiState.settings.onboardingCompleted) ROUTE_MAIN else ROUTE_ONBOARDING

    NavHost(
        navController = navController,
        startDestination = startRoute
    ) {
        composable(ROUTE_ONBOARDING) {
            OnboardingPermissionsScreen(
                permissions = uiState.permissions,
                onPermissionsChanged = viewModel::refreshSystemStatus,
                onContinue = {
                    viewModel.completeOnboarding()
                    navController.navigate(ROUTE_MAIN) {
                        popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(ROUTE_MAIN) {
            MainOrbScreen(
                uiState = uiState,
                waveformBands = waveformBands,
                onOrbTapped = viewModel::onOrbTapped,
                onToggleAssistant = viewModel::toggleAssistantEnabled,
                onToggleMute = viewModel::toggleMute,
                onPauseToStandby = viewModel::endLiveSessionToStandby,
                onTriggerPrompt = viewModel::triggerVoiceSuggestion,
                onConfirmAction = viewModel::confirmPendingAction,
                onCancelAction = viewModel::cancelPendingAction,
                onDismissBanner = viewModel::dismissBanner,
                onPermissionsUpdated = viewModel::refreshSystemStatus,
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onOpenAbout = { navController.navigate(ROUTE_ABOUT) }
            )
        }

        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                uiState = uiState,
                onSelectVoice = viewModel::setSelectedVoice,
                onSelectModel = viewModel::setSelectedModel,
                onToggleWakeWord = viewModel::setWakeWordEnabled,
                onWakeSensitivityChanged = viewModel::setWakeSensitivity,
                onToggleBackgroundService = viewModel::setBackgroundServiceEnabled,
                onToggleNotificationConsent = viewModel::setNotificationReaderConsent,
                onOpenNotificationSettings = viewModel::buildNotificationAccessIntent,
                onOpenBatterySettings = viewModel::buildBatteryOptimizationIntent,
                onPermissionsUpdated = viewModel::refreshSystemStatus,
                onBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
