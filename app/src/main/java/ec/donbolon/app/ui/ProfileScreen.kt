package ec.donbolon.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import ec.donbolon.app.data.Priorities
import ec.donbolon.app.data.Fasting
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.donbolon.app.AppModel
import ec.donbolon.app.data.Activity
import ec.donbolon.app.data.Mood
import ec.donbolon.app.data.Goal
import ec.donbolon.app.data.Nutrition
import ec.donbolon.app.data.Provider
import ec.donbolon.app.data.Sex

@Composable
fun ProfileScreen(model: AppModel, onboarding: Boolean, onClose: () -> Unit) {
    val state by model.store.state.collectAsState()
    val p0 = state.profile
    var name by remember { mutableStateOf(p0.name) }
    var sex by remember { mutableStateOf(p0.sex) }
    var age by remember { mutableStateOf(if (onboarding) "" else p0.age.toString()) }
    var height by remember { mutableStateOf(if (onboarding) "" else p0.heightCm.fmt()) }
    var weight by remember { mutableStateOf(if (onboarding) "" else p0.weightKg.fmt(1)) }
    var activity by remember { mutableStateOf(p0.activity) }
    var goal by remember { mutableStateOf(p0.goal) }
    var prio by remember { mutableStateOf(p0.priorities) }
    var rate by remember { mutableStateOf(p0.rateKgWeek) }
    var goalWeight by remember { mutableStateOf(p0.goalWeightKg?.fmt(1) ?: "") }
    var trainDays by remember { mutableStateOf(p0.trainDays) }
    var aiOn by remember { mutableStateOf(model.ai().available) }
    var changeKey by remember { mutableStateOf(false) }
    var wipe by remember { mutableStateOf(false) }
    var science by remember { mutableStateOf(false) }
    var fasting by remember { mutableStateOf(p0.fasting) }
    var windowStart by remember { mutableStateOf(p0.windowStart) }

    fun num(s: String) = s.replace(',', '.').toDoubleOrNull()
    val draft = p0.copy(
        name = name.trim(), sex = sex, age = num(age)?.toInt()?.coerceIn(14, 100) ?: p0.age,
        heightCm = num(height)?.coerceIn(120.0, 230.0) ?: p0.heightCm, weightKg = num(weight)?.coerceIn(30.0, 300.0) ?: p0.weightKg,
        activity = activity, goal = goal, priorities = prio, rateKgWeek = rate, goalWeightKg = num(goalWeight), trainDays = trainDays, fasting = fasting, windowStart = windowStart, onboarded = true,
    )
    val valid = num(age) != null && num(height) != null && num(weight) != null
    val t = Nutrition.targets(draft)

    fun save() {
        model.store.edit { s ->
            val weights = if (onboarding && s.weights.isEmpty()) listOf(ec.donbolon.app.data.WeightEntry(java.time.LocalDate.now(), draft.weightKg)) else s.weights
            s.copy(profile = draft, weights = weights)
        }
        onClose()
    }

    Column(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        if (!onboarding) Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClose) { Icon(Icons.Filled.Close, "Cerrar", tint = C.muted) }
            Text("Perfil", style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            if (onboarding) {
                Gap(20.dp)
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Bolon(Mood.GRUMPY, 150.dp, onPoke = {}) }
                Gap(12.dp)
                Text("Soy Don Bolón.", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Gruño, pero estoy de tu lado. Tú me enseñas lo que comes, yo te digo la verdad. Primero, unos datos para calcular lo que tu cuerpo gasta.",
                    color = C.muted, fontSize = 15.sp,
                )
                Gap(20.dp)
            }
            Field(name, { name = it }, "¿Cómo te llamo?")
            Gap(14.dp)
            Label("Sexo biológico (para la fórmula)")
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sex.entries.forEach { s -> Chip(s.name.lowercase().replaceFirstChar { it.uppercase() }, sex == s) { sex = s } }
            }
            Gap(14.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(age, { age = it }, "Edad", Modifier.weight(1f), number = true)
                Field(height, { height = it }, "Altura cm", Modifier.weight(1f), number = true)
                Field(weight, { weight = it }, "Peso kg", Modifier.weight(1f), number = true)
            }
            Gap(18.dp)
            Label("Actividad")
            Gap(8.dp)
            Activity.entries.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (a == activity) C.raised else C.bg)
                        .clickable { activity = a }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Dot(if (a == activity) C.accent else C.line, 10.dp)
                    HGap(12.dp)
                    Column {
                        Text(a.label, fontWeight = if (a == activity) FontWeight.SemiBold else FontWeight.Normal)
                        Text(a.detail, color = C.muted, fontSize = 12.sp)
                    }
                }
            }
            Gap(18.dp)
            Label("Tus prioridades")
            Gap(4.dp)
            Text("Arrastra las puntas del pentágono o usa las barras. Todo lo demás se calcula con evidencia científica.", color = C.muted, fontSize = 13.sp)
            Gap(10.dp)
            FlowChips(Goal.entries.map { it.label }, Goal.entries.firstOrNull { it.priorities == prio }?.label) { l ->
                val g = Goal.entries.first { it.label == l }
                goal = g; prio = g.priorities
            }
            Gap(8.dp)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Pentagon(prio.values, Priorities.short) { i, v -> prio = prio.with(i, v) }
            }
            Priorities.names.forEachIndexed { i, n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n, fontSize = 14.sp, modifier = Modifier.width(110.dp))
                    Slider(
                        prio.values[i].toFloat(), { prio = prio.with(i, it.toDouble()) }, Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent, inactiveTrackColor = C.raised),
                    )
                    Text("${(prio.values[i] * 100).toInt()}", color = C.muted, fontSize = 13.sp, modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
                }
            }
            if (prio.fat >= 0.6 && prio.muscle >= 0.6) {
                Gap(4.dp)
                Text(
                    "Recomposición: así se marcan los cuadritos. Aparecen con poca grasa (≈10–12% en hombres, 16–19% en mujeres) y músculo debajo; los abdominales solos no queman la grasa de la panza.",
                    color = C.faint, fontSize = 12.sp,
                )
            }
            Gap(14.dp)
            Text("Entrenos de fuerza por semana (tu plan)", color = C.muted, fontSize = 13.sp)
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (2..6).forEach { d -> Chip(if (d == t.strengthDays) "$d ★" else "$d", trainDays == d) { trainDays = d } }
            }
            Text("★ lo que recomienda la evidencia para tus prioridades", color = C.faint, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            Gap(12.dp)
            Gap(18.dp)
            Label("Ayuno intermitente")
            Gap(8.dp)
            FlowChips(Fasting.entries.map { it.label }, fasting.label) { l -> fasting = Fasting.entries.first { it.label == l } }
            Gap(6.dp)
            Text(fasting.detail, color = C.muted, fontSize = 13.sp)
            if (fasting != Fasting.NINGUNO) {
                Gap(10.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tu ventana abre a las", color = C.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Stepper({ windowStart = (windowStart + 23) % 24 }, { windowStart = (windowStart + 1) % 24 }) {
                        Text("%02d:00".format(windowStart), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp))
                    }
                }
                Text(
                    "Comes de %02d:00 a %02d:00. Te sugiero comidas solo dentro de esa ventana.".format(windowStart, (windowStart + fasting.windowHours) % 24),
                    color = C.faint, fontSize = 12.sp,
                )
            }
            Gap(12.dp)
            Field(goalWeight, { goalWeight = it }, "Peso meta kg (opcional)", number = true)
            Gap(18.dp)

            if (valid) Panel {
                Label("Tu prescripción")
                Gap(8.dp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${t.kcal}", style = MaterialTheme.typography.displaySmall, color = C.accent)
                    Text("  kcal / día", color = C.muted, modifier = Modifier.padding(bottom = 8.dp))
                }
                Text("Proteína ${t.protein} g · Carbos ${t.carbs} g · Grasa ${t.fat} g · Fibra ${t.fiber} g", fontSize = 14.sp)
                if (t.weeklyChangeKg < -0.02 || t.weeklyChangeKg > 0.02) {
                    Text(
                        "≈ ${if (t.weeklyChangeKg > 0) "+" else "−"}${kotlin.math.abs(t.weeklyChangeKg).fmt(2)} kg/semana al inicio (luego se frena un poco, es normal)",
                        color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (t.floored) Text("No bajo de ${t.kcal} kcal: menos que eso no es seguro sin supervisión médica.", color = C.accent, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                Gap(6.dp)
                QuietButton(if (science) "Ocultar la ciencia" else "¿Por qué estos números? ›", color = C.muted) { science = !science }
                if (science) t.why.forEach { w ->
                    Gap(10.dp)
                    Text(w.text, fontSize = 13.sp)
                    Text(w.source, color = C.faint, fontSize = 11.sp)
                }
            }

            Gap(22.dp)
            Label("Inteligencia artificial")
            Gap(8.dp)
            if (aiOn && !changeKey) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✓ Conectado a ${if (state.settings.provider == Provider.CLAUDE) "Claude" else "Gemini"}", color = C.good, modifier = Modifier.weight(1f))
                    QuietButton("Cambiar llave", color = C.muted) { changeKey = true }
                }
            } else {
                ConnectAi(model) { aiOn = true; changeKey = false }
            }

            if (!onboarding) {
                Gap(22.dp)
                Label("Lo que Don Bolón sabe de ti")
                Gap(6.dp)
                if (state.memories.isEmpty()) {
                    Text("Todavía nada. Cuéntale en el chat tus gustos, horarios o lo que te cuesta, y lo recordará.", color = C.faint, fontSize = 13.sp)
                }
                state.memories.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("· ${m.text}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        IconButton({ model.store.edit { s -> s.copy(memories = s.memories.filterNot { it.id == m.id }) } }, Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, "Olvidar", tint = C.faint, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                val learned = state.corrections.map { it.food.lowercase() }.distinct().size
                if (learned > 0) Text("Ha aprendido tus porciones reales de $learned alimentos.", color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                if (state.memories.isNotEmpty() || learned > 0) {
                    QuietButton("Olvidar todo", color = C.over) { model.store.edit { it.copy(memories = emptyList(), corrections = emptyList()) } }
                }
            }

            if (!onboarding) {
                Gap(22.dp)
                Label("Recordatorios")
                Gap(4.dp)
                SettingSwitch("Tomar agua", "Cada 2 horas de 9:00 a 21:00, solo si vas atrasado", state.settings.waterReminders) { on ->
                    model.store.edit { it.copy(settings = it.settings.copy(waterReminders = on)) }
                }
                SettingSwitch("Ayuno", "Cuando abre tu ventana y una hora antes de que cierre", state.settings.fastReminders) { on ->
                    model.store.edit { it.copy(settings = it.settings.copy(fastReminders = on)) }
                }
            }

            if (!onboarding) {
                Gap(22.dp)
                Label("Privacidad")
                Gap(4.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Bloquear con huella o PIN")
                        Text("El diario está cifrado y nunca va a la nube.", color = C.faint, fontSize = 12.sp)
                    }
                    Switch(
                        state.settings.lock, { on -> model.store.edit { it.copy(settings = it.settings.copy(lock = on)) } },
                        colors = SwitchDefaults.colors(checkedTrackColor = C.accent, checkedThumbColor = C.onAccent),
                    )
                }
                Gap(10.dp)
                QuietButton("Borrar todos mis datos", color = C.over) { wipe = true }
            }
            Gap(16.dp)
            Text(
                "Don Bolón es una guía educativa, no reemplaza a un médico o nutricionista. Si tienes una condición médica, estás embarazada o tienes historial de trastornos alimentarios, consulta a un profesional.",
                color = C.faint, fontSize = 11.sp,
            )
            Gap(20.dp)
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            PrimaryButton(if (onboarding) "Empezar" else "Guardar", enabled = valid) { save() }
        }
    }

    if (wipe) AlertDialog(
        onDismissRequest = { wipe = false },
        containerColor = C.card,
        title = { Text("¿Borrar todo?") },
        text = { Text("Se borran comidas, fotos, pesos, planes y chat. No se puede deshacer.") },
        confirmButton = { QuietButton("Borrar", color = C.over) { model.store.wipe(); wipe = false } },
        dismissButton = { QuietButton("Cancelar", color = C.muted) { wipe = false } },
    )
}

@Composable
private fun SettingSwitch(title: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(detail, color = C.faint, fontSize = 12.sp)
        }
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = C.accent, checkedThumbColor = C.onAccent))
    }
}
