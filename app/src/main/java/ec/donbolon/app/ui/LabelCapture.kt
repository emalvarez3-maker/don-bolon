package ec.donbolon.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.donbolon.app.ai.Label
import ec.donbolon.app.data.FoodItem
import ec.donbolon.app.data.Per100

/** A package's nutrition table turned into what the person ate, in code: the AI only reads the numbers. */
internal object LabelMath {
    /** True when the label gives a column the grams can be applied to. */
    fun usable(label: Label): Boolean = label.per100 != null || (label.perServing != null && (label.servingG ?: 0.0) > 0)

    /**
     * [grams] of the product: per 100 g when printed, otherwise the per-serving column divided by the
     * serving's grams. Null when neither column can be used or [grams] is not positive.
     */
    fun item(label: Label, grams: Double): FoodItem? {
        if (grams.isNaN() || grams <= 0) return null
        val name = label.name.trim().ifBlank { "Producto" }
        label.per100?.let { return scaled(name, it, grams / 100.0, grams) }
        val serving = label.servingG?.takeIf { it > 0 } ?: return null
        val per = label.perServing ?: return null
        return scaled(name, per, grams / serving, grams)
    }

    /** "por 100 g: 120 kcal · P 3 · C 20 · G 2" or "por porción de 30 g: …". */
    fun describe(label: Label): String {
        val col = label.per100 ?: label.perServing ?: return ""
        val per = if (label.per100 != null) "por 100 g" else label.servingG?.let { "por porción de ${typed(it)} g" } ?: "por porción"
        return "$per: ${col.kcal.fmt()} kcal · P ${col.protein.fmt()} · C ${col.carbs.fmt()} · G ${col.fat.fmt()}"
    }

    private fun scaled(name: String, p: Per100, k: Double, grams: Double) =
        FoodItem(name, grams, p.kcal * k, p.protein * k, p.carbs * k, p.fat * k, p.fiber * k)
}

/** Grams as typed by a person: "30", or "12,5" when the label has a decimal. */
private fun typed(x: Double): String = if (x == Math.rint(x)) x.toLong().toString() else x.fmt(1)

/** What the label said, the name to keep and how much was eaten; [onAdd] gets the computed item. */
@Composable
internal fun LabelSheet(label: Label, onAdd: (FoodItem) -> Unit, onDismiss: () -> Unit) {
    var name by remember(label) { mutableStateOf(label.name) }
    var grams by remember(label) { mutableStateOf(label.servingG?.let(::typed) ?: "") }
    val usable = LabelMath.usable(label)
    val g = grams.replace(',', '.').toDoubleOrNull()
    val item = if (usable && g != null) LabelMath.item(label.copy(name = name), g) else null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.card,
        title = { Text("🏷️ Etiqueta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!usable) {
                    Text("No pude leer la tabla. Prueba con más luz o escríbela", color = C.text, fontSize = 14.sp)
                } else {
                    Field(name, { name = it }, "Producto")
                    Text(LabelMath.describe(label), color = C.muted, fontSize = 13.sp)
                    Field(grams, { grams = it }, "¿Cuánto comiste? (g)", number = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        label.servingG?.let { s -> Chip("1 porción", g == typed(s).replace(',', '.').toDoubleOrNull()) { grams = typed(s) } }
                        Chip("100 g", g == 100.0) { grams = "100" }
                    }
                    item?.let { Text("= ${it.kcal.fmt()} kcal · P ${it.protein.fmt()} g", color = C.faint, fontSize = 12.sp) }
                }
            }
        },
        confirmButton = {
            if (usable) QuietButton("Añadir", color = if (item != null) C.accent else C.faint) { item?.let(onAdd) }
            else QuietButton("Cerrar", color = C.muted, onClick = onDismiss)
        },
        dismissButton = { if (usable) QuietButton("Cancelar", color = C.muted, onClick = onDismiss) },
    )
}
