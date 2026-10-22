package ec.donbolon.app.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** A sunny kitchen: cream, white plates, fried-plantain gold. One accent, everything else calm. */
object C {
    val bg = Color(0xFFFBF5EC)
    val card = Color(0xFFFFFFFF)
    val raised = Color(0xFFF4EBDD)
    val line = Color(0xFFEADFCF)
    val text = Color(0xFF2E2117)
    val muted = Color(0xFF7D6C5C)
    val faint = Color(0xFFADA090)
    val honey = Color(0xFFEE9A1F)
    val onAccent = Color(0xFFFFFFFF)
    val over = Color(0xFFDD5B45)
    val good = Color(0xFF5E9E4A)

    val protein = Color(0xFFEE9A1F)
    val carbs = Color(0xFF5B9BD5)
    val fat = Color(0xFFC77DB0)
    val fiber = Color(0xFF5E9E4A)
}

@Composable
fun BolonTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = C.honey,
        onPrimary = C.onAccent,
        secondary = C.good,
        background = C.bg,
        onBackground = C.text,
        surface = C.card,
        onSurface = C.text,
        surfaceVariant = C.raised,
        onSurfaceVariant = C.muted,
        surfaceContainer = C.card,
        surfaceContainerHigh = C.card,
        surfaceContainerHighest = C.raised,
        outline = C.line,
        error = C.over,
    )
    val base = Typography()
    val type = Typography(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
    )
    MaterialTheme(colorScheme = scheme, typography = type) {
        CompositionLocalProvider(LocalContentColor provides C.text, content = content)
    }
}
