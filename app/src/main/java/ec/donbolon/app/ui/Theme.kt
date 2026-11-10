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
 * - Dusty blue only for water: calm and appetite-neutral.
 * - Every color that carries text or data keeps at least 4.5:1 against bg and card (WCAG AA);
 *   [contrast] computes it and a unit test holds the line.
 * Old-paper cream text keeps the vintage feel, and nothing is purple, so it never looks like Dexter.
 */
object C {
    val bg = Color(0xFF111A17)
    val card = Color(0xFF1A2521)
    val raised = Color(0xFF24312C)
    val line = Color(0xFF2E3C36)
    val text = Color(0xFFF1E8D6)
    val muted = Color(0xFFA2AE9F)
    // Lightened from #6C7A72 (3.5:1 on card) so small labels and targets stay readable: 6.0:1 on bg, 5.4:1 on card.
    val faint = Color(0xFF8C9A92)
    val accent = Color(0xFF9CC285)
    val onAccent = Color(0xFF10201A)
    val gold = Color(0xFFE0AA45)
    val over = Color(0xFFD9775A)
    val good = Color(0xFF9CC285)

    // Protein is a nutrient, not a reward: a warm sand, so gold keeps meaning "you earned this".
    val protein = Color(0xFFD9C3A0)
    // A eucalyptus green-grey, greener than water's blue, so dusty blue only ever means water.
    val carbs = Color(0xFF7FB8A4)
    // A muted dusty rose, far less saturated than terracotta, so a fat bar never reads as "over".
    val fat = Color(0xFFC49A9E)
    val fiber = Color(0xFF9CC285)
    val water = Color(0xFF86AFC0)
}

/** WCAG contrast ratio between two colors (1..21), from their relative luminance. */
fun contrast(a: Color, b: Color): Double {
    fun lin(c: Float): Double = if (c <= 0.03928f) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    fun lum(c: Color) = 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    val (hi, lo) = lum(a).let { la -> lum(b).let { lb -> if (la > lb) la to lb else lb to la } }
    return (hi + 0.05) / (lo + 0.05)
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
