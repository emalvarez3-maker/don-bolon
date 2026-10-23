package ec.donbolon.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

val Shape = RoundedCornerShape(20.dp)

@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(Shape).background(C.card).padding(padding),
        content = content,
    )
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelSmall, color = C.muted)
}

@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(CircleShape)
            .background(if (selected) C.accent else C.raised)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, color = if (selected) C.onAccent else C.text, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick, modifier.fillMaxWidth().height(54.dp), enabled = enabled, shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = C.accent, contentColor = C.onAccent, disabledContainerColor = C.raised),
    ) { Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
}

@Composable
fun QuietButton(text: String, modifier: Modifier = Modifier, color: Color = C.accent, onClick: () -> Unit) {
    TextButton(onClick, modifier, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
        Text(text, color = color, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, number: Boolean = false, singleLine: Boolean = true, minLines: Int = 1) {
    OutlinedTextField(
        value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine, minLines = minLines,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.accent, unfocusedBorderColor = C.line, focusedLabelColor = C.accent, cursorColor = C.accent,
            focusedContainerColor = C.card, unfocusedContainerColor = C.card,
        ),
    )
}

/** The day's calorie ring: honey while under, coral once over. */
@Composable
fun CalorieRing(eaten: Double, target: Int, size: Dp = 180.dp) {
    val frac by animateFloatAsState((eaten / max(target, 1)).toFloat().coerceIn(0f, 1.5f), tween(700), label = "ring")
    val over = eaten > target * 1.1
    val left = target - eaten
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = 12.dp.toPx()
            val inset = w / 2
            val arc = Size(this.size.width - w, this.size.height - w)
            drawArc(C.raised, 135f, 270f, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
            drawArc(if (over) C.over else C.accent, 135f, 270f * min(frac, 1f), false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${kotlin.math.abs(left).roundToInt()}",
                style = MaterialTheme.typography.displaySmall, color = if (over) C.over else if (left < 0) C.good else C.text,
            )
            Text(if (left >= 0) "kcal restantes" else if (over) "kcal de más" else "sobre la meta, en rango ✓", color = C.muted, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text("${eaten.roundToInt()} / $target", color = C.faint, fontSize = 12.sp)
        }
    }
}

@Composable
fun MacroBar(name: String, value: Double, target: Int, color: Color, modifier: Modifier = Modifier) {
    val frac by animateFloatAsState((value / max(target, 1)).toFloat().coerceIn(0f, 1f), tween(700), label = name)
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${value.roundToInt()}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(" / ${target}g", color = C.faint, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(C.raised)) {
            Box(Modifier.fillMaxWidth(frac).height(5.dp).clip(CircleShape).background(color))
        }
        Spacer(Modifier.height(5.dp))
        Text(name, color = C.muted, fontSize = 11.sp)
    }
}

@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
            .background(C.raised).padding(horizontal = 14.dp, vertical = 11.dp),
    ) { Text(text, fontSize = 14.sp, lineHeight = 20.sp) }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

@Composable
fun Stepper(onMinus: () -> Unit, onPlus: () -> Unit, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        RoundTap("−", onMinus)
        content()
        RoundTap("+", onPlus)
    }
}

@Composable
fun RoundTap(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).border(1.dp, C.line, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = C.text, fontSize = 16.sp) }
}

@Composable
fun Gap(h: Dp = 12.dp) = Spacer(Modifier.height(h))

@Composable
fun HGap(w: Dp = 12.dp) = Spacer(Modifier.width(w))

/** Photo plumbing: decode at a size the vision models can read well, upright, without OOM. */
object Images {
    const val ANALYSIS_SIDE = 1568

    fun load(context: Context, uri: Uri, maxSide: Int = ANALYSIS_SIDE): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val orientation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
        }
        val scale = min(1f, maxSide.toFloat() / max(raw.width, raw.height))
        m.postScale(scale, scale)
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    }.getOrNull()

    fun saveThumb(b: Bitmap, file: File) {
        val s = min(1f, 640f / max(b.width, b.height))
        val t = Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
        FileOutputStream(file).use { t.compress(Bitmap.CompressFormat.JPEG, 82, it) }
    }
}

fun Double.fmt(decimals: Int = 0) = if (decimals == 0) roundToInt().toString() else "%.${decimals}f".format(this)
