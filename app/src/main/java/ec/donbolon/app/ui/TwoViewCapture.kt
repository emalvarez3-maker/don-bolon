package ec.donbolon.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.min

/**
 * Guided capture of a meal from two angles, with the user's open hand beside the plate as a
 * size reference: from above (required) and from the side at about 45° (optional). Each empty
 * slot shows a drawn guide of where the plate and the hand go; a filled slot shows the photo.
 * It launches nothing itself: the caller wires the camera and gallery.
 */
@Composable
fun TwoViewCapture(
    top: Bitmap?,
    side: Bitmap?,
    handReady: Boolean,
    onShootTop: () -> Unit,
    onShootSide: () -> Unit,
    onPickTop: () -> Unit,
    onClearSide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ViewSlot(
                title = "De arriba",
                tag = "Necesaria",
                tagColor = C.accent,
                photo = top,
                guide = { drawTopGuide() },
                spoken = "Foto de arriba, necesaria",
                onShoot = onShootTop,
                onGallery = onPickTop,
                onClear = null,
                modifier = Modifier.weight(1f),
            )
            ViewSlot(
                title = "De lado",
                tag = "Opcional · 45°",
                tagColor = C.muted,
                photo = side,
                guide = { drawSideGuide() },
                spoken = "Foto de lado, a unos 45 grados, opcional",
                onShoot = onShootSide,
                onGallery = null,
                onClear = onClearSide,
                modifier = Modifier.weight(1f),
            )
        }
        if (!handReady) {
            Gap(10.dp)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.gold.copy(alpha = 0.09f))
                    .border(1.dp, C.gold.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(26.dp)) {
                    drawPath(flatHand(Offset(size.width * 0.5f, size.height * 0.98f), size.height * 0.95f), C.gold, style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round))
                }
                HGap(10.dp)
                Text("Mide tu mano una vez para que la use como regla", color = C.text.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun ViewSlot(
    title: String,
    tag: String,
    tagColor: Color,
    photo: Bitmap?,
    guide: DrawScope.() -> Unit,
    spoken: String,
    onShoot: () -> Unit,
    onGallery: (() -> Unit)?,
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val empty = photo == null
    Box(
        modifier.aspectRatio(0.76f).clip(shape).background(C.card)
            .border(1.dp, if (empty) C.line else C.accent.copy(alpha = 0.35f), shape)
            .clickable(onClickLabel = if (empty) "Tomar foto" else "Repetir foto", role = Role.Button, onClick = onShoot)
            .semantics {
                contentDescription = if (empty) "$spoken. Vacía. Mano abierta y plana junto al plato, toda a la vista"
                else "$spoken. Lista"
            },
    ) {
        if (photo != null) {
            Image(photo.asImageBitmap(), "Tu plato, ${title.lowercase()}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.45f), 0.3f to Color.Transparent, 0.62f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f)),
                ),
            )
        } else {
            // A faint warm glow behind the guide, like a lamp over the table.
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(C.raised, C.card), radius = 420f)))
        }

        Corners(Modifier.fillMaxSize().padding(10.dp), if (empty) C.accent.copy(alpha = 0.55f) else C.accent.copy(alpha = 0.9f))

        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = C.text)
                    Text(tag, color = if (empty) tagColor else C.text.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                if (!empty && onClear != null) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                            .clickable(onClickLabel = "Quitar foto de lado", role = Role.Button, onClick = onClear),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Close, "Quitar foto de lado", Modifier.size(16.dp), tint = C.text) }
                }
            }
            if (empty) {
                Canvas(Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp), onDraw = guide)
                Text(
                    "Mano abierta y plana junto al plato, toda a la vista",
                    color = C.muted, fontSize = 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Gap(8.dp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                    SlotAction("📷 Cámara", primary = true, label = "Tomar foto ${title.lowercase()}", onClick = onShoot)
                    if (onGallery != null) SlotAction("🖼️", primary = false, label = "Elegir de la galería", onClick = onGallery)
                }
            } else {
                Box(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    SlotAction("↻ Repetir", primary = false, label = "Repetir foto ${title.lowercase()}", onClick = onShoot)
                }
            }
        }
    }
}

@Composable
private fun SlotAction(text: String, primary: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape)
            .background(if (primary) C.accent else Color.Black.copy(alpha = 0.35f))
            .border(1.dp, if (primary) Color.Transparent else C.text.copy(alpha = 0.18f), CircleShape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Text(text, color = if (primary) C.onAccent else C.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

/** Viewfinder corner brackets, like the main capture screen. */
@Composable
private fun Corners(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val l = 20.dp.toPx()
        val w = 2.5.dp.toPx()
        listOf(
            Offset(0f, 0f) to Offset(1f, 1f), Offset(size.width, 0f) to Offset(-1f, 1f),
            Offset(0f, size.height) to Offset(1f, -1f), Offset(size.width, size.height) to Offset(-1f, -1f),
        ).forEach { (o, d) ->
            drawLine(color, o, Offset(o.x + d.x * l, o.y), w, StrokeCap.Round)
            drawLine(color, o, Offset(o.x, o.y + d.y * l), w, StrokeCap.Round)
        }
    }
}

// ---- Drawn guides -------------------------------------------------------------------------

private val dashes = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))

/** From above: a round plate with a little food on it, and an open flat hand to its right. */
private fun DrawScope.drawTopGuide() {
    val w = size.width
    val h = size.height
    val r = min(w * 0.30f, h * 0.36f)
    val plate = Offset(w * 0.40f, h * 0.50f)
    val thin = 1.6.dp.toPx()

    // Table shadow, rim, well and a couple of food mounds.
    drawCircle(Color.Black.copy(alpha = 0.18f), r * 1.04f, plate + Offset(0f, r * 0.06f))
    drawCircle(C.text.copy(alpha = 0.05f), r, plate)
    drawCircle(C.muted.copy(alpha = 0.75f), r, plate, style = Stroke(thin))
    drawCircle(C.muted.copy(alpha = 0.45f), r * 0.72f, plate, style = Stroke(thin, pathEffect = dashes))
    drawCircle(C.gold.copy(alpha = 0.16f), r * 0.26f, plate + Offset(-r * 0.20f, -r * 0.12f))
    drawCircle(C.accent.copy(alpha = 0.14f), r * 0.20f, plate + Offset(r * 0.24f, r * 0.18f))

    // The hand, fingers pointing away from the camera, beside the plate.
    val handH = r * 1.55f
    val wrist = Offset(min(w * 0.86f, plate.x + r + handH * 0.30f), plate.y + handH * 0.52f)
    val hand = flatHand(wrist, handH)
    drawPath(hand, C.accent.copy(alpha = 0.10f))
    drawPath(hand, C.accent, style = Stroke(thin * 1.2f, join = StrokeJoin.Round))
}

/** From the side at ~45°: the plate as an ellipse with its food, the hand standing on its edge. */
private fun DrawScope.drawSideGuide() {
    val w = size.width
    val h = size.height
    val thin = 1.6.dp.toPx()
    val rx = min(w * 0.31f, h * 0.40f)
    val ry = rx * 0.42f
    val c = Offset(w * 0.40f, h * 0.62f)
    val depth = ry * 0.32f

    // Shadow, the plate's lower edge, its top rim and a mound of food rising from it.
    drawOval(Color.Black.copy(alpha = 0.2f), Offset(c.x - rx * 1.04f, c.y - ry + depth * 1.6f), Size(rx * 2.08f, ry * 2f))
    drawOval(C.muted.copy(alpha = 0.45f), Offset(c.x - rx * 0.92f, c.y - ry * 0.92f + depth), Size(rx * 1.84f, ry * 1.84f), style = Stroke(thin))
    drawOval(C.card, Offset(c.x - rx, c.y - ry), Size(rx * 2f, ry * 2f))
    drawOval(C.text.copy(alpha = 0.05f), Offset(c.x - rx, c.y - ry), Size(rx * 2f, ry * 2f))
    drawOval(C.muted.copy(alpha = 0.75f), Offset(c.x - rx, c.y - ry), Size(rx * 2f, ry * 2f), style = Stroke(thin))
    val mound = Path().apply {
        moveTo(c.x - rx * 0.58f, c.y + ry * 0.05f)
        cubicTo(c.x - rx * 0.45f, c.y - ry * 1.9f, c.x + rx * 0.40f, c.y - ry * 2.0f, c.x + rx * 0.58f, c.y + ry * 0.05f)
        quadraticTo(c.x, c.y + ry * 0.45f, c.x - rx * 0.58f, c.y + ry * 0.05f)
        close()
    }
    drawPath(mound, C.gold.copy(alpha = 0.14f))
    drawPath(mound, C.gold.copy(alpha = 0.5f), style = Stroke(thin, pathEffect = dashes))

    // The hand on its edge, little finger on the table, palm facing the plate.
    val base = Offset(min(w * 0.86f, c.x + rx + w * 0.10f), c.y + ry * 0.55f)
    val handH = rx * 1.55f
    drawOval(Color.Black.copy(alpha = 0.22f), Offset(base.x - handH * 0.16f, base.y - handH * 0.035f), Size(handH * 0.32f, handH * 0.07f))
    val hand = edgeHand(base, handH)
    drawPath(hand, C.accent.copy(alpha = 0.10f))
    drawPath(hand, C.accent, style = Stroke(thin * 1.2f, join = StrokeJoin.Round))
    // Knuckle creases so the blade of the hand reads as fingers.
    val fx = base.x - handH * 0.03f
    for (i in 0..2) {
        val y = base.y - handH * (0.62f + i * 0.09f)
        drawLine(C.accent.copy(alpha = 0.5f), Offset(fx - handH * 0.035f, y), Offset(fx + handH * 0.035f, y), thin * 0.8f, StrokeCap.Round)
    }
}

/**
 * Outline of an open right hand seen from above, wrist at [wrist] and [height] from wrist to the
 * middle fingertip: a palm with four fingers slightly spread and the thumb out to the left.
 */
private fun flatHand(wrist: Offset, height: Float): Path {
    val u = height
    val palmW = u * 0.40f
    val palmH = u * 0.44f
    val palmTop = wrist.y - palmH
    var hand = Path().apply {
        addRoundRect(RoundRect(Rect(wrist.x - palmW / 2, palmTop, wrist.x + palmW / 2, wrist.y), CornerRadius(u * 0.12f)))
    }
    val fingerR = palmW * 0.105f
    // Index to little finger: base x across the palm, length, outward lean.
    val fingers = listOf(Triple(-0.36f, 0.50f, -0.05f), Triple(-0.12f, 0.56f, -0.015f), Triple(0.12f, 0.52f, 0.02f), Triple(0.36f, 0.40f, 0.06f))
    fingers.forEach { (fx, len, lean) ->
        val b = Offset(wrist.x + fx * palmW, palmTop + fingerR)
        val t = Offset(b.x + lean * u, palmTop - (len * u - fingerR * 2))
        hand = Path.combine(PathOperation.Union, hand, capsule(b, t, fingerR))
    }
    val thumbBase = Offset(wrist.x - palmW * 0.42f, wrist.y - palmH * 0.30f)
    val thumbTip = Offset(thumbBase.x - u * 0.22f, thumbBase.y - u * 0.20f)
    return Path.combine(PathOperation.Union, hand, capsule(thumbBase, thumbTip, fingerR * 1.15f))
}

/** A hand standing on its little-finger edge, seen from the side: a tall blade with the thumb out. */
private fun edgeHand(base: Offset, height: Float): Path {
    val u = height
    val bladeW = u * 0.13f
    var hand = capsule(Offset(base.x, base.y - bladeW / 2), Offset(base.x - u * 0.06f, base.y - u + bladeW / 2), bladeW / 2)
    // Thicker palm near the base.
    hand = Path.combine(PathOperation.Union, hand, capsule(Offset(base.x + u * 0.01f, base.y - u * 0.10f), Offset(base.x - u * 0.01f, base.y - u * 0.45f), bladeW * 0.65f))
    val thumbBase = Offset(base.x - u * 0.05f, base.y - u * 0.40f)
    val thumbTip = Offset(thumbBase.x - u * 0.18f, thumbBase.y - u * 0.16f)
    return Path.combine(PathOperation.Union, hand, capsule(thumbBase, thumbTip, bladeW * 0.32f))
}

/** A stadium shape from [a] to [b] with radius [r]. */
private fun capsule(a: Offset, b: Offset, r: Float): Path {
    val len = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(0.001f)
    val nx = -(b.y - a.y) / len * r
    val ny = (b.x - a.x) / len * r
    val body = Path().apply {
        moveTo(a.x + nx, a.y + ny)
        lineTo(b.x + nx, b.y + ny)
        lineTo(b.x - nx, b.y - ny)
        lineTo(a.x - nx, a.y - ny)
        close()
    }
    val ends = Path().apply {
        addOval(Rect(a, r))
        addOval(Rect(b, r))
    }
    return Path.combine(PathOperation.Union, body, ends)
}
