package com.gigabee.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.gigabee.app.R

object Bee {
    val Yellow = Color(0xFFFFD400)
    val YellowSoft = Color(0x1AFFD400) // 10% yellow, for highlighted rows
    val Black = Color(0xFF09090A)
    val Surface = Color(0xFF131315)
    val Surface2 = Color(0xFF1C1C1F)
    val Line = Color(0xFF26262A)
    val Muted = Color(0xFF8B8B92)
    val Text = Color(0xFFF6F6F3)
    val Red = Color(0xFFFF5A4E)
    val Paper = Color(0xFFFFFFFF) // wheel and cards
    val Ink = Color(0xFF0A0A0A)   // outlines on paper
}

/** Inter (OFL), static weights trimmed to Latin so bold renders as bold on every phone. */
val AppFont = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun Typography.withFont(family: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
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
        typography = Typography().withFont(AppFont),
        content = content,
    )
}
