package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val HavenlyDarkColorScheme = darkColorScheme(
    primary = RoseGoldPrimary,
    onPrimary = OnRoseGold,
    primaryContainer = RoseGoldContainer,
    onPrimaryContainer = OnRoseGoldContainer,
    secondary = ChampagneSecondary,
    onSecondary = OnChampagne,
    secondaryContainer = ChampagneContainer,
    onSecondaryContainer = OnChampagneContainer,
    tertiary = PlumTertiary,
    onTertiary = OnPlum,
    tertiaryContainer = PlumContainer,
    onTertiaryContainer = OnPlumContainer,
    background = ObsidianBg,
    onBackground = IvoryText,
    surface = ObsidianSurface,
    onSurface = IvoryText,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = MutedLilacText,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceHigh,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = ObsidianOutline,
    error = RiskCoral
)

private val HavenlyLightColorScheme = lightColorScheme(
    primary = RosePlumLight,
    secondary = ChampagneLight,
    tertiary = PlumLight,
    background = AlabasterBg,
    onBackground = CharcoalText,
    surface = AlabasterSurface,
    onSurface = CharcoalText,
    surfaceVariant = AlabasterSurfaceHigh
)

private val HavenlyShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun HavenlyTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) HavenlyDarkColorScheme else HavenlyLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = HavenlyTypography,
        shapes = HavenlyShapes,
        content = content
    )
}
