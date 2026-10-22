package ec.donbolon.app.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A vintage kitchen at night, chosen with color psychology for eating:
 * - Deep greens and sage as the base and the main action: green reads as fresh, natural and
 *   healthy, and food labelled in green is judged healthier (Schuldt, 2013, Health Comm.).
 * - Mustard gold only for Don Bolón and rewards. Warm yellows and oranges stimulate appetite
 *   (Spence, 2015, Flavour), so they mark wins instead of filling the screen.
 * - Terracotta only when going over: red works as an avoidance cue that reduces snacking
 *   (Genschow et al., 2012, Appetite), softened so it informs instead of scolds.
 * - Dusty blue for water and carbs: calm and appetite-neutral.
 * Old-paper cream text keeps the vintage feel, and nothing is purple, so it never looks like Dexter.
 */
object C {
    val bg = Color(0xFF111A17)
    val card = Color(0xFF1A2521)
    val raised = Color(0xFF24312C)
    val line = Color(0xFF2E3C36)
    val text = Color(0xFFF1E8D6)
    val muted = Color(0xFFA2AE9F)
    val faint = Color(0xFF6C7A72)
    val accent = Color(0xFF9CC285)
    val onAccent = Color(0xFF10201A)
    val gold = Color(0xFFE0AA45)
    val over = Color(0xFFD9775A)
    val good = Color(0xFF9CC285)

    val protein = Color(0xFFE0AA45)
    val carbs = Color(0xFF86AFC0)
    val fat = Color(0xFFCF9286)
    val fiber = Color(0xFF9CC285)
    val water = Color(0xFF86AFC0)
}

@Composable
fun BolonTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = C.accent,
        onPrimary = C.onAccent,
        secondary = C.gold,
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
