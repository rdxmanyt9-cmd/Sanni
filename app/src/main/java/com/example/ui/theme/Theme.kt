package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val MarcoDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = VoidObsidian,
    primaryContainer = GlassPanelSurface,
    onPrimaryContainer = TextPrimary,
    secondary = NeonPurple,
    onSecondary = TextPrimary,
    secondaryContainer = ElevatedPanelSurface,
    onSecondaryContainer = TextPrimary,
    tertiary = ElectricBlue,
    onTertiary = TextPrimary,
    background = VoidObsidian,
    onBackground = TextPrimary,
    surface = DeepSpaceSurface,
    onSurface = TextPrimary,
    surfaceVariant = GlassPanelSurface,
    onSurfaceVariant = TextSecondary,
    outline = HairlineBorder,
    error = AlertCrimson,
    onError = TextPrimary
)

private val MarcoShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MarcoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MarcoDarkColorScheme,
        typography = MarcoTypography,
        shapes = MarcoShapes,
        content = content
    )
}
