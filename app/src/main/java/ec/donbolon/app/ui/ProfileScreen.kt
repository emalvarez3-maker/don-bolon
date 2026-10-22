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
    var rate by remember { mutableStateOf(p0.rateKgWeek) }
    var goalWeight by remember { mutableStateOf(p0.goalWeightKg?.fmt(1) ?: "") }
    var provider by remember { mutableStateOf(state.settings.provider) }
    var key by remember { mutableStateOf(model.secrets.get(state.settings.provider)) }
    var wipe by remember { mutableStateOf(false) }

    fun num(s: String) = s.replace(',', '.').toDoubleOrNull()
    val draft = p0.copy(
        name = name.trim(), sex = sex, age = num(age)?.toInt()?.coerceIn(14, 100) ?: p0.age,
        heightCm = num(height)?.coerceIn(120.0, 230.0) ?: p0.heightCm, weightKg = num(weight)?.coerceIn(30.0, 300.0) ?: p0.weightKg,
        activity = activity, goal = goal, rateKgWeek = rate, goalWeightKg = num(goalWeight), onboarded = true,
    )
    val valid = num(age) != null && num(height) != null && num(weight) != null
    val t = Nutrition.targets(draft)

    fun save() {
        model.secrets.set(provider, key)
        model.store.edit { s ->
            val weights = if (onboarding && s.weights.isEmpty()) listOf(ec.donbolon.app.data.WeightEntry(java.time.LocalDate.now(), draft.weightKg)) else s.weights
            s.copy(profile = draft, settings = s.settings.copy(provider = provider), weights = weights)
        }
        onClose()
    }

    Column(Modifier.fillMaxSize().background(C.night).statusBarsPadding().navigationBarsPadding().imePadding()) {
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
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (a == activity) C.raised else C.night)
                        .clickable { activity = a }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Dot(if (a == activity) C.honey else C.line, 10.dp)
                    HGap(12.dp)
                    Column {
                        Text(a.label, fontWeight = if (a == activity) FontWeight.SemiBold else FontWeight.Normal)
                        Text(a.detail, color = C.muted, fontSize = 12.sp)
                    }
                }
            }
            Gap(18.dp)
            Label("Objetivo")
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Goal.entries.forEach { g -> Chip(g.label, goal == g) { goal = g } } }
            if (goal != Goal.MANTENER) {
                Gap(12.dp)
                Text("Ritmo por semana", color = C.muted, fontSize = 13.sp)
                Gap(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.25, 0.5, 0.75, 1.0).forEach { r -> Chip("${r} kg", rate == r) { rate = r } }
                }
                Gap(12.dp)
                Field(goalWeight, { goalWeight = it }, "Peso meta kg (opcional)", number = true)
            }
            Gap(18.dp)

            if (valid) Panel {
                Label("Tu prescripción")
                Gap(8.dp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${t.kcal}", style = MaterialTheme.typography.displaySmall, color = C.honey)
                    Text("  kcal / día", color = C.muted, modifier = Modifier.padding(bottom = 8.dp))
                }
                Text("Proteína ${t.protein} g · Carbos ${t.carbs} g · Grasa ${t.fat} g · Fibra ${t.fiber} g", fontSize = 14.sp)
                Gap(8.dp)
                Text(
                    "Metabolismo en reposo ${t.bmr} kcal (Mifflin–St Jeor) × actividad = gasto ${t.tdee} kcal. " +
                        when {
                            goal == Goal.MANTENER -> "Comes lo que gastas."
                            t.weeklyChangeKg < 0 -> "Déficit de ${t.tdee - t.kcal} kcal ≈ ${(-t.weeklyChangeKg).fmt(2)} kg/semana al inicio; luego se frena un poco, es normal."
                            else -> "Superávit de ${t.kcal - t.tdee} kcal para ganar músculo con poca grasa."
                        },
                    color = C.muted, fontSize = 13.sp,
                )
                if (t.capped) Text("Recorté el ritmo: más rápido cuesta músculo y suele rebotar.", color = C.honey, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                if (t.floored) Text("No bajo de ${t.kcal} kcal: menos que eso no es seguro sin supervisión médica.", color = C.honey, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }

            Gap(22.dp)
            Label("Inteligencia artificial")
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Provider.entries.forEach { pr -> Chip(when (pr) { Provider.GEMINI -> "Gemini"; Provider.CLAUDE -> "Claude"; Provider.NINGUNO -> "Sin IA" }, provider == pr) { provider = pr; key = model.secrets.get(pr) } }
            }
            Gap(8.dp)
            if (provider != Provider.NINGUNO) {
                OutlinedTextField(
                    key, { key = it.trim() }, Modifier.fillMaxWidth(),
                    label = { Text(if (provider == Provider.GEMINI) "Llave de Google AI Studio" else "Llave de Anthropic (sk-ant-…)") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.honey, unfocusedBorderColor = C.line, focusedLabelColor = C.honey, cursorColor = C.honey),
                )
                Text(
                    "Se guarda cifrada con el Keystore del teléfono. Solo se envía la foto y tu descripción al proveedor, por HTTPS, cuando tú pides un análisis.",
                    color = C.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                Text("Sin IA uso la libreta: escribes lo que comiste y busco los valores.", color = C.faint, fontSize = 12.sp)
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
                        colors = SwitchDefaults.colors(checkedTrackColor = C.honey, checkedThumbColor = C.night),
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
