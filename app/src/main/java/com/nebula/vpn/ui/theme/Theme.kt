package com.nebula.vpn.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Nebula dark color scheme
private val NebulaDarkColorScheme = darkColorScheme(
    primary = NebulaPrimary,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = NebulaPrimaryDark,
    onPrimaryContainer = NebulaPrimaryLight,
    secondary = NebulaAccent,
    onSecondary = NebulaBlack,
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF0A2E24),
    onSecondaryContainer = NebulaAccentLight,
    tertiary = NebulaAccent,
    onTertiary = NebulaBlack,
    background = NebulaBlack,
    onBackground = NebulaOnDark,
    surface = NebulaDark,
    onSurface = NebulaOnDark,
    surfaceVariant = NebulaSurface,
    onSurfaceVariant = NebulaOnDarkMuted,
    error = NebulaError,
    onError = androidx.compose.ui.graphics.Color.White,
    outline = NebulaCard,
    outlineVariant = NebulaOnDarkDim,
)

@Composable
fun NebulaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Use dynamic color on Android 12+ but override primary with our violet
        val dynamic = androidx.compose.material3.dynamicDarkColorScheme(context)
        dynamic.copy(
            primary = NebulaPrimary,
            secondary = NebulaAccent,
        )
    } else {
        NebulaDarkColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NebulaTypography,
        content = content,
    )
}
