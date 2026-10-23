package ec.donbolon.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import ec.donbolon.app.data.Mood
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val CrustLight = Color(0xFFE9B158)
private val Crust = Color(0xFFC9832F)
private val CrustDark = Color(0xFF8A4F1C)
private val Crisp = Color(0xFF7A4316)
private val Cheese = Color(0xFFFFEDB0)
private val CheeseShade = Color(0xFFF2CF72)
private val Ink = Color(0xFF2A160A)
private val Blush = Color(0xFFE8775C)

// Fixed crispy spots on the crust (x, y, size), so the texture doesn't flicker between frames.
private val crumbs = listOf(
    Triple(58f, 70f, 5f), Triple(140f, 66f, 4f), Triple(46f, 120f, 4.5f), Triple(156f, 118f, 5f),
    Triple(70f, 160f, 4f), Triple(132f, 162f, 4.5f), Triple(100f, 56f, 3.5f), Triple(84f, 82f, 2.5f),
    Triple(118f, 84f, 3f), Triple(150f, 92f, 3f), Triple(52f, 94f, 3f), Triple(100f, 172f, 3.5f),
)

/** What Don Bolón does when touched. */
enum class Reaction(val line: String) {
    GROWL("¡Grrr! No me apachurres, que se me sale el queso."),
    HOP("¡Hop! Así de liviano te quiero ver."),
    LAUGH("Jajaja… digo, hmpf. Sigue registrando."),
    CHEW("Ñam. ¿Tú ya comiste proteína hoy?"),
    PETTED("…Ok, eso estuvo bonito. No le cuentes a nadie."),
}

/**
 * Don Bolón: a bolón de queso just out of the pan, drawn by hand on a 200×200 grid.
 * He breathes (the dough squashes), blinks, wobbles a cheese drip and frowns or smiles with
 * his mood. When [onPoke] is set he is interactive: a tap gets a reaction (growl, hop, laugh,
 * chew), stroking him makes him happy, dragging squishes the dough while his eyes follow the
 * finger, and a long press calls [onLongPress]. [talking] animates the mouth.
 */
@Composable
fun Bolon(
    mood: Mood,
    size: Dp,
    modifier: Modifier = Modifier,
    talking: Boolean = false,
    onPoke: (() -> Unit)? = null,
    onReact: ((Reaction) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val loop = rememberInfiniteTransition(label = "bolon")
    val breath by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2800), RepeatMode.Reverse), label = "breath")
    val blink by loop.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes { durationMillis = 4200; 1f at 3900; 0.05f at 3990; 1f at 4100 }),
        label = "blink",
    )
    val drip by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(3400), RepeatMode.Reverse), label = "drip")
    val wobble by loop.animateFloat(
        0f, 0f,
        infiniteRepeatable(keyframes { durationMillis = 5300; 0f at 4600; -3f at 4700; 2f at 4800; -1f at 4900; 0f at 5050 }),
        label = "wobble",
    )
    val drift by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "drift")
    val chew by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(260), RepeatMode.Reverse), label = "chew")
    val look by loop.animateFloat(
        0f, 0f,
        infiniteRepeatable(keyframes { durationMillis = 9000; 0f at 2000; -3f at 2300; -3f at 3600; 2.5f at 3900; 2.5f at 5200; 0f at 5500 }),
        label = "look",
    )

    var reaction by remember { mutableStateOf<Reaction?>(null) }
    var lastTap by remember { mutableStateOf<Reaction?>(null) }
    val shake = remember { Animatable(0f) }
    val hop = remember { Animatable(0f) }
    val squishX = remember { Animatable(0f) }
    val squishY = remember { Animatable(0f) }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var stroke by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    val shown = when (reaction) {
        Reaction.GROWL -> Mood.ANGRY
        Reaction.HOP, Reaction.LAUGH, Reaction.PETTED -> Mood.HAPPY
        Reaction.CHEW -> Mood.EATING
        null -> mood
    }

    fun play(r: Reaction) {
        reaction = r
        onReact?.invoke(r)
        scope.launch {
            when (r) {
                Reaction.GROWL -> { repeat(6) { i -> shake.animateTo(if (i % 2 == 0) 6f else -6f, tween(55)) }; shake.animateTo(0f, tween(80)) }
                Reaction.HOP -> { hop.animateTo(1f, tween(170)); hop.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 300f)) }
                Reaction.LAUGH -> repeat(4) { i -> shake.animateTo(if (i % 2 == 0) 3f else -3f, tween(90)) }.also { shake.animateTo(0f, tween(90)) }
                Reaction.CHEW, Reaction.PETTED -> {}
            }
            delay(if (r == Reaction.CHEW) 1400 else 1100)
            reaction = null
        }
    }

    // Mood → face parameters, eased so expressions morph instead of snapping.
    val browTilt by animateFloatAsState(
        when (shown) { Mood.ANGRY -> 1.5f; Mood.GRUMPY -> 0.9f; Mood.PROUD -> 0.15f; Mood.SLEEPY -> 0.25f; Mood.THINKING -> 0.6f; Mood.EATING -> 0.4f; Mood.HAPPY -> -0.35f; Mood.CALM -> -0.1f },
        tween(450), label = "brow",
    )
    val rightBrowLift by animateFloatAsState(if (shown == Mood.THINKING || shown == Mood.PROUD) 1f else 0f, tween(450), label = "lift")
    val smile by animateFloatAsState(
        when (shown) { Mood.ANGRY -> -1f; Mood.GRUMPY -> -0.7f; Mood.PROUD -> 0.55f; Mood.SLEEPY -> -0.2f; Mood.THINKING -> -0.35f; Mood.EATING -> 0f; Mood.HAPPY -> 1f; Mood.CALM -> 0.45f },
        tween(450), label = "smile",
    )
    val eyeOpenBase by animateFloatAsState(if (shown == Mood.SLEEPY) 0.12f else if (shown == Mood.ANGRY) 0.75f else 1f, tween(500), label = "eye")
    val growl by animateFloatAsState(if (shown == Mood.ANGRY) 1f else 0f, tween(300), label = "growl")
    val blush by animateFloatAsState(if (shown == Mood.PROUD || shown == Mood.HAPPY) 1f else 0f, tween(600), label = "blush")

    val touch = if (onPoke == null && onReact == null && onLongPress == null) Modifier else Modifier
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = {
                    onPoke?.invoke()
                    // Never the same reaction twice in a row, so poking him stays fun.
                    val next = listOf(Reaction.GROWL, Reaction.HOP, Reaction.LAUGH, Reaction.CHEW).filter { it != lastTap }.random()
                    lastTap = next
                    play(next)
                },
                onLongPress = { onLongPress?.invoke() },
            )
        }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { finger = it; stroke = 0f },
                onDragEnd = {
                    finger = null
                    scope.launch { squishX.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 260f)) }
                    scope.launch { squishY.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 260f)) }
                    // A real stroke (not a nudge) counts as petting.
                    if (stroke > this.size.width * 0.9f) play(Reaction.PETTED)
                },
                onDragCancel = { finger = null; scope.launch { squishX.snapTo(0f); squishY.snapTo(0f) } },
            ) { change, drag ->
                finger = change.position
                stroke += drag.getDistance()
                val w = this.size.width.toFloat()
                scope.launch { squishX.snapTo((squishX.value + drag.x / w).coerceIn(-0.25f, 0.25f)) }
                scope.launch { squishY.snapTo((squishY.value + drag.y / w).coerceIn(-0.2f, 0.2f)) }
            }
        }

    Canvas(modifier.size(size).then(touch)) {
        val u = this.size.minDimension / 200f
        val mouthOpen = if (shown == Mood.EATING || talking) 0.25f + 0.75f * chew else 0f
        // The eyes follow the finger while it is on him.
        val eyes = finger?.let { ((it.x / this.size.width - 0.5f) * 9f).coerceIn(-4f, 4f) } ?: look
        withTransform({
            translate(squishX.value * 40 * u, -26 * u * hop.value + squishY.value * 20 * u)
            rotate(shake.value + wobble + squishX.value * 18f, Offset(100 * u, 186 * u))
            // Dough squash: breathing, plus the stretch from being dragged.
            scale(
                1f + 0.015f * breath + abs(squishX.value) * 0.5f - abs(squishY.value) * 0.3f,
                1f - 0.02f * breath - abs(squishX.value) * 0.35f + squishY.value * 0.5f,
                Offset(100 * u, 186 * u),
            )
        }) {
            drawBolon(u, browTilt, rightBrowLift, smile, (eyeOpenBase * blink).coerceAtLeast(0.05f), eyes, drip, growl, blush, mouthOpen, shown, drift)
        }
    }
}

private fun DrawScope.drawBolon(
    u: Float, browTilt: Float, rightBrowLift: Float, smile: Float, eyeOpen: Float, look: Float, drip: Float,
    growl: Float, blush: Float, mouthOpen: Float, mood: Mood, drift: Float,
) {
    fun p(x: Float, y: Float) = Offset(x * u, y * u)

    // Shadow on the plate.
    drawOval(Color.Black.copy(alpha = 0.35f), p(42f, 178f), Size(116 * u, 16 * u))

    // Stubby arms on the hips, behind the ball.
    rotate(-28f, p(36f, 128f)) { drawRoundRect(CrustDark, p(18f, 118f), Size(30 * u, 18 * u), CornerRadius(9 * u)) }
    rotate(28f, p(164f, 128f)) { drawRoundRect(CrustDark, p(152f, 118f), Size(30 * u, 18 * u), CornerRadius(9 * u)) }

    // The ball: hand-shaped, never a perfect circle.
    val ball = Path()
    val steps = 72
    for (i in 0..steps) {
        val a = (i / steps.toFloat()) * 2 * PI.toFloat()
        val r = 72f + 3.2f * sin(7 * a) + 2f * sin(11 * a + 1.3f)
        val x = 100f + r * cos(a)
        val y = 112f + r * 0.94f * sin(a)
        if (i == 0) ball.moveTo(x * u, y * u) else ball.lineTo(x * u, y * u)
    }
    ball.close()
    drawPath(ball, Brush.radialGradient(listOf(CrustLight, Crust, CrustDark), center = p(88f, 82f), radius = 105 * u))
    drawPath(ball, CrustDark.copy(alpha = 0.5f), style = Stroke(2.2f * u))

    // Crispy fried spots.
    crumbs.forEach { (x, y, s) ->
        drawOval(Crisp.copy(alpha = 0.55f), p(x - s, y - s * 0.7f), Size(2 * s * u, 1.4f * s * u))
        drawCircle(CrustLight.copy(alpha = 0.7f), s * 0.35f * u, p(x + s * 0.6f, y - s * 0.5f))
    }

    // Cheese bursting out: top-left chunk, a right-side pocket with a slow drip, a bit at the bottom.
    val topChunk = Path().apply {
        moveTo(40 * u, 74 * u); quadraticTo(36 * u, 54 * u, 56 * u, 50 * u); quadraticTo(70 * u, 46 * u, 72 * u, 60 * u)
        quadraticTo(62 * u, 62 * u, 58 * u, 74 * u); quadraticTo(50 * u, 82 * u, 40 * u, 74 * u); close()
    }
    drawPath(topChunk, Cheese)
    drawOval(CheeseShade, p(47f, 64f), Size(12 * u, 6 * u))
    val dripLen = 8f + 9f * drip
    val side = Path().apply {
        moveTo(160 * u, 92 * u); quadraticTo(178 * u, 96 * u, 172 * u, 112 * u)
        lineTo(172 * u, (112 + dripLen) * u)
        quadraticTo(168 * u, (120 + dripLen) * u, 164 * u, (112 + dripLen) * u)
        lineTo(164 * u, 114 * u); quadraticTo(156 * u, 110 * u, 160 * u, 92 * u); close()
    }
    drawPath(side, Cheese)
    drawCircle(Cheese, 4.2f * u, p(168f, 113f + dripLen))
    drawOval(CheeseShade, p(162f, 98f), Size(8 * u, 5 * u))
    val bottom = Path().apply {
        moveTo(118 * u, 176 * u); quadraticTo(126 * u, 166 * u, 140 * u, 170 * u); quadraticTo(138 * u, 182 * u, 118 * u, 176 * u); close()
    }
    drawPath(bottom, Cheese)

    // Anger reddens the dough.
    if (growl > 0f) drawOval(Blush.copy(alpha = 0.2f * growl), p(52f, 92f), Size(96 * u, 50 * u))

    // Cheeks (only when secretly pleased).
    if (blush > 0f) {
        drawOval(Blush.copy(alpha = 0.4f * blush), p(52f, 120f), Size(20 * u, 11 * u))
        drawOval(Blush.copy(alpha = 0.4f * blush), p(128f, 120f), Size(20 * u, 11 * u))
    }

    // Eyes: blink by squashing the oval; pupils drift left and right.
    for (x in listOf(80f, 120f)) {
        val h = 16f * eyeOpen
        if (mood == Mood.HAPPY && eyeOpen > 0.5f) {
            // Happy eyes: closed upward arcs, the universal "smiling eyes".
            drawArc(Ink, 200f, 140f, false, p(x - 9, 102f), Size(18 * u, 14 * u), style = Stroke(3.6f * u, cap = StrokeCap.Round))
        } else if (eyeOpen < 0.2f) {
            drawArc(Ink, 10f, 160f, false, p(x - 8, 104f), Size(16 * u, 8 * u), style = Stroke(3.2f * u, cap = StrokeCap.Round))
        } else {
            drawOval(Color.White, p(x - 9f, 108f - h * 0.62f), Size(18 * u, h * 1.24f * u))
            drawOval(Ink, p(x - 5.5f + look, 108f - h / 2), Size(11 * u, h * u))
            drawCircle(Color.White.copy(alpha = 0.9f), 2.2f * u, p(x - 2f + look, 108f - h / 4))
        }
    }

    // Brows: the inner ends drop when he's grumpy; the right one lifts when skeptical.
    val lift = 9f * rightBrowLift
    drawLine(Ink, p(64f, 92f - 4 * browTilt), p(92f, 94f + 7 * browTilt), 6.5f * u, StrokeCap.Round)
    drawLine(Ink, p(136f, 92f - 4 * browTilt - lift), p(108f, 94f + 7 * browTilt - lift * 0.6f), 6.5f * u, StrokeCap.Round)

    // Mouth: a curve that frowns or smiles, a growl with teeth, or chewing.
    if (growl > 0.5f) {
        drawRoundRect(Ink, p(86f, 134f), Size(28 * u, 13 * u * growl), CornerRadius(6 * u))
        drawRoundRect(Color.White, p(89f, 134f), Size(22 * u, 4 * u), CornerRadius(2 * u))
    } else if (mouthOpen > 0f) {
        drawOval(Ink, p(91f, 133f), Size(18 * u, (3 + 9 * mouthOpen) * u))
    } else if (smile > 0.8f) {
        // A real grin: open mouth with a little tongue.
        val grin = Path().apply {
            moveTo(84 * u, 136 * u)
            quadraticTo(100 * u, 136 * u, 116 * u, 136 * u)
            quadraticTo(100 * u, (136 + 22 * smile) * u, 84 * u, 136 * u)
            close()
        }
        drawPath(grin, Ink)
        drawOval(Blush.copy(alpha = 0.9f), p(93f, 144f), Size(14 * u, 6 * u))
    } else {
        val m = Path().apply {
            moveTo(86 * u, 138 * u)
            quadraticTo(100 * u, (138 + 12 * smile) * u, 114 * u, 138 * u)
        }
        drawPath(m, Ink, style = Stroke(3.2f * u, cap = StrokeCap.Round))
    }

    // Mood props.
    when (mood) {
        Mood.ANGRY -> for (i in 0..2) {
            val t = (drift + i / 3f) % 1f
            val x = 70f + i * 30f
            drawCircle(Color(0xFFE9DED3).copy(alpha = 0.45f * (1 - t)), (5 + 8 * t) * u, p(x + 6 * sin(t * 2 * PI.toFloat()), 36f - 30 * t))
        }
        Mood.SLEEPY -> {
            val paint = Paint().apply { color = Color(0xFFD8CBBE).toArgb(); isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD }
            for (i in 0..1) {
                val t = (drift + i / 2f) % 1f
                paint.alpha = ((1 - t) * 220).toInt()
                paint.textSize = (12 + 10 * t) * u
                drawContext.canvas.nativeCanvas.drawText("z", (150 + 22 * t) * u, (40 - 30 * t) * u, paint)
            }
        }
        Mood.THINKING -> for (i in 0..2) {
            val on = ((drift * 3).toInt() % 3) >= i
            drawCircle(Color(0xFFD8CBBE).copy(alpha = if (on) 0.9f else 0.3f), 4.5f * u, p(150f + i * 13, 26f))
        }
        Mood.PROUD -> for (i in 0..1) {
            // A wisp of fresh-out-of-the-pan steam.
            val t = (drift + i / 2f) % 1f
            drawCircle(Color(0xFFE9DED3).copy(alpha = 0.3f * (1 - t)), (4 + 6 * t) * u, p(92f + i * 16 + 4 * sin(t * 6f), 38f - 24 * t))
        }
        else -> Unit
    }
}
