package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CosmicPrimaryDark,
    onPrimary = CosmicOnPrimaryDark,
    primaryContainer = CosmicPrimaryContainerDark,
    onPrimaryContainer = CosmicOnPrimaryContainerDark,
    secondary = CosmicSecondaryDark,
    onSecondary = CosmicOnSecondaryDark,
    secondaryContainer = CosmicSecondaryContainerDark,
    onSecondaryContainer = CosmicOnSecondaryContainerDark,
    background = CosmicBackgroundDark,
    onBackground = CosmicOnBackgroundDark,
    surface = CosmicSurfaceDark,
    onSurface = CosmicOnSurfaceDark,
    surfaceVariant = CosmicSurfaceVariantDark,
    onSurfaceVariant = CosmicOnBackgroundDark.copy(alpha = 0.7f)
)

private val LightColorScheme = lightColorScheme(
    primary = CosmicPrimary,
    onPrimary = CosmicOnPrimary,
    primaryContainer = CosmicPrimaryContainer,
    onPrimaryContainer = CosmicOnPrimaryContainer,
    secondary = CosmicSecondary,
    onSecondary = CosmicOnSecondary,
    secondaryContainer = CosmicSecondaryContainer,
    onSecondaryContainer = CosmicOnSecondaryContainer,
    background = CosmicBackground,
    onBackground = CosmicOnBackground,
    surface = CosmicSurface,
    onSurface = CosmicOnSurface,
    surfaceVariant = CosmicSurfaceVariant,
    onSurfaceVariant = CosmicOnSurfaceVariant
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep dynamicColor false to enforce our custom themed design identity by default, 
    // unless the user specifically wants system wallpaper color sync.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
