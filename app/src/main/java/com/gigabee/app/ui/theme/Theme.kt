package com.gigabee.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Bee {
    val Yellow = Color(0xFFFFD400)
    val Black = Color(0xFF0A0A0A)
    val Surface = Color(0xFF151515)
    val Surface2 = Color(0xFF1F1F1F)
    val Line = Color(0xFF2A2A2A)
    val Muted = Color(0xFF8E8E8E)
    val Text = Color(0xFFF7F7F2)
    val Red = Color(0xFFFF5A4E)
}

@Composable
fun GigaBeeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Bee.Yellow,
            onPrimary = Bee.Black,
            secondary = Bee.Yellow,
            onSecondary = Bee.Black,
            background = Bee.Black,
            onBackground = Bee.Text,
            surface = Bee.Surface,
            onSurface = Bee.Text,
            surfaceVariant = Bee.Surface2,
            onSurfaceVariant = Bee.Muted,
            outline = Bee.Line,
            error = Bee.Red,
        ),
        content = content,
    )
}
