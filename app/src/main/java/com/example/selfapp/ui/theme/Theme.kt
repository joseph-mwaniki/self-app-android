package com.example.selfapp.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

private val DarkColorScheme = darkColorScheme(
    primary = NeonBlue,
    secondary = ElectricCyan,
    background = Black,
    surface = DarkGray,
    error = NeonRed,
    onPrimary = Black,
    onSecondary = Black,
    onBackground = White,
    onSurface = White
)

@Composable
fun SelfAppTheme(content: @Composable () -> Unit) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.statusBarColor = Black.toArgb()
            window?.navigationBarColor = Black.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
