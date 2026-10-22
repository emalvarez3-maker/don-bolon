package ec.donbolon.app.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Warm night palette: a cave lit by honey. One accent, everything else quiet. */
object C {
    val night = Color(0xFF120E0B)
    val card = Color(0xFF1C1713)
    val raised = Color(0xFF27201A)
    val line = Color(0xFF332A22)
    val text = Color(0xFFF3EADF)
    val muted = Color(0xFF9C8F80)
    val faint = Color(0xFF6B6056)
    val honey = Color(0xFFF2A93B)
    val over = Color(0xFFE8775C)
    val good = Color(0xFF8FBF7A)

    val protein = Color(0xFFF2A93B)
    val carbs = Color(0xFF7FA7C9)
    val fat = Color(0xFFC98BB0)
    val fiber = Color(0xFF8FBF7A)
}

@Composable
fun BolonTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = C.honey,
        onPrimary = C.night,
        secondary = C.good,
        background = C.night,
        onBackground = C.text,
        surface = C.card,
        onSurface = C.text,
        surfaceVariant = C.raised,
        onSurfaceVariant = C.muted,
        surfaceContainer = C.card,
        surfaceContainerHigh = C.raised,
        surfaceContainerHighest = C.raised,
        outline = C.line,
        error = C.over,
    )
    val base = Typography()
    val type = Typography(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
    )
    MaterialTheme(colorScheme = scheme, typography = type) {
        CompositionLocalProvider(LocalContentColor provides C.text, content = content)
    }
}
