package ec.donbolon.app.data

import java.time.LocalDate
import java.time.LocalTime

/** One evidence-based principle the app is built on, shown in the "Mente" library. */
data class Principle(val title: String, val body: String, val tryThis: String, val source: String)

object Science {
    val principles = listOf(
        Principle(
            "Registrar es la intervención",
            "Anotar lo que comes es el predictor más constante de bajar de peso: quienes registran con regularidad pierden más, aunque el registro no sea perfecto. Lo que cuenta es la constancia, no la precisión.",
            "Registra aunque sea con una foto rápida. Un registro aproximado vale más que ninguno.",
            "Burke, Wang & Sevick (2011), J Am Diet Assoc; Harvey et al. (2019), Obesity",
        ),
        Principle(
            "Planes Si-Entonces",
            "Decidir de antemano qué harás ante una situación concreta duplica o triplica la probabilidad de cumplir una meta. El cerebro ejecuta el plan sin tener que pelear con la fuerza de voluntad.",
            "\"Si llego a casa con hambre a las 6, entonces como yogur con fruta antes de cocinar.\"",
            "Gollwitzer & Sheeran (2006), Advances in Experimental Social Psychology (meta-análisis, 94 estudios)",
        ),
        Principle(
            "Los hábitos tardan ~66 días",
            "Una conducta se vuelve automática en promedio en 66 días (rango 18–254). Saltarse un día no arruina el proceso; lo que importa es volver al siguiente.",
            "No busques racha perfecta: busca no fallar dos veces seguidas.",
            "Lally et al. (2010), European Journal of Social Psychology",
        ),
        Principle(
            "Restricción flexible, no rígida",
            "Las dietas de todo-o-nada (\"prohibido el pan\") se asocian a más atracones y más peso. La restricción flexible (\"pan, pero medido\") se asocia a mejor control y menos ansiedad.",
            "Ningún alimento está prohibido. Cabe en tus calorías o se ajusta en la siguiente comida.",
            "Westenhoefer et al. (1999), Appetite; Smith et al. (1999), Int J Obesity",
        ),
        Principle(
            "El efecto \"qué más da\"",
            "Cuando alguien a dieta siente que \"ya rompió\" el plan, tiende a comer todavía más. La autocompasión después de un desliz reduce ese rebote.",
            "Después de pasarte: \"Fue una comida, no mi identidad\". La siguiente comida es normal, sin castigo.",
            "Polivy & Herman (1985); Adams & Leary (2007), J Social & Clinical Psychology",
        ),
        Principle(
            "Hambre física vs. emocional",
            "El hambre física sube poco a poco, la sacia casi cualquier comida y se siente en el estómago. La emocional llega de golpe, pide algo específico y no se calma al llenarte. Ponerle nombre a la emoción baja su intensidad.",
            "Antes de comer, puntúa tu hambre de 1 a 10. Si es menor a 4, pregunta: ¿qué siento realmente?",
            "Lieberman et al. (2007), Psychological Science (affect labeling); escala de hambre de Craighead",
        ),
        Principle(
            "Surfear el antojo",
            "Los antojos suben, llegan a un pico y bajan solos en minutos si no los alimentas. Observarlos con curiosidad, sin luchar, reduce el consumo de lo que se antoja.",
            "Usa el temporizador SOS: 3 minutos respirando y describiendo el antojo como una ola.",
            "Bowen & Marlatt (2009); Lacaille et al. (2014), Eating Behaviors",
        ),
        Principle(
            "Proteína y fibra sacian",
            "La proteína es el macronutriente que más saciedad produce y protege el músculo en déficit. La fibra y los alimentos con mucha agua (verduras, sopas, fruta) llenan con pocas calorías.",
            "Arma cada plato con: ½ verduras, ¼ proteína, ¼ almidón.",
            "Leidy et al. (2015), Am J Clin Nutr; Rolls (2009), Physiology & Behavior (densidad energética)",
        ),
        Principle(
            "Ultraprocesados: comes más sin notarlo",
            "En un ensayo controlado, la misma persona comió ~500 kcal más al día con ultraprocesados que con comida casera, aunque ambas dietas tenían iguales macros disponibles.",
            "Cuando puedas, elige el alimento que tu abuela reconocería.",
            "Hall et al. (2019), Cell Metabolism",
        ),
        Principle(
            "Comer atento",
            "Comer distraído (pantallas) aumenta lo que comes en esa comida y en la siguiente. Comer despacio da tiempo a las señales de saciedad (~20 min).",
            "Una comida al día sin pantalla. Deja el cubierto entre bocado y bocado.",
            "Robinson et al. (2013), Am J Clin Nutr (meta-análisis)",
        ),
        Principle(
            "Dormir poco da hambre",
            "Dormir mal sube la grelina y baja la leptina: más hambre y más antojos de comida densa al día siguiente (+~385 kcal).",
            "Si dormiste poco, planifica el snack de la tarde antes de que llegue el antojo.",
            "Spiegel et al. (2004), Annals of Internal Medicine; Al Khatib et al. (2017), Eur J Clin Nutr",
        ),
        Principle(
            "El entorno gana a la voluntad",
            "Lo que está a la vista y a mano se come más. Cambiar el entorno (fruta visible, dulces guardados, porciones servidas en plato) funciona mejor que resistir.",
            "Sirve en plato, nunca directo de la bolsa. Guarda los snacks fuera de la vista.",
            "Hollands et al. (2015), Cochrane Review (tamaño de porción); Wood & Neal (2016)",
        ),
        Principle(
            "Pésate seguido, mira la tendencia",
            "Pesarse con frecuencia ayuda a mantener el peso, siempre que se interprete la tendencia y no el número del día (el agua mueve 1–2 kg).",
            "Pésate en ayunas, mira la línea suave, no el punto.",
            "Zheng et al. (2015), Obesity Reviews",
        ),
    )

    fun tipOfDay(day: LocalDate = LocalDate.now()) = principles[(day.toEpochDay() % principles.size).toInt()]

    /** What Don Bolón says on the home screen, picked from the state of the day. */
    fun bolonLine(state: AppState, targets: Targets, now: LocalTime = LocalTime.now()): Pair<Mood, String> {
        val today = LocalDate.now()
        val t = state.totalsOn(today)
        val meals = state.mealsOn(today)
        val left = targets.kcal - t.kcal
        val yesterday = state.totalsOn(today.minusDays(1)).kcal
        val name = state.profile.name.ifBlank { "humano" }
        return when {
            now.hour >= 23 || now.hour < 5 -> Mood.SLEEPY to "Grrr… ya es tarde, $name. La cocina cerró. Agua y a dormir: dormir bien también es dieta."
            meals.isEmpty() && yesterday > targets.kcal * 1.15 ->
                Mood.GRUMPY to "Ayer te pasaste. ¿Y? Hoy se come normal, sin castigo. Gruño, pero no te voy a dejar compensar con hambre."
            meals.isEmpty() && now.hour >= 11 -> Mood.ANGRY to "¿Nada registrado todavía? Grrr. No te pido perfección, te pido una foto."
            meals.isEmpty() -> Mood.GRUMPY to "Hmpf. Buenos días, $name. Cuando comas algo, me lo enseñas. Yo vigilo."
            left < -targets.kcal * 0.10 -> Mood.ANGRY to "Te pasaste ${(-left).toInt()} kcal. Respira. Una comida no decide nada; la siguiente que sea liviana y con proteína."
            Nutrition.onTarget(t.kcal, targets.kcal) && t.protein >= targets.protein * 0.9 ->
                Mood.PROUD to "…Bien hecho. No te acostumbres a que lo diga. Calorías en rango Y proteína cumplida."
            left in 0.0..(targets.kcal * 0.10) -> Mood.PROUD to "Casi en la meta. Si te da hambre: verduras o algo con proteína. Nada de picar por picar."
            t.protein < targets.protein * 0.5 && now.hour >= 15 ->
                Mood.GRUMPY to "Llevas ${t.protein.toInt()} g de proteína de ${targets.protein}. Ni un bolón vive solo de verde. Ponle pollo, huevo, atún o chochos a la siguiente."
            else -> Mood.GRUMPY to "Te quedan ${left.toInt()} kcal. Úsalas con cabeza, no con antojo."
        }
    }

    /** The persona and rules for the AI coach. Context about the user is appended per request. */
    const val COACH_PERSONA = """Eres Don Bolón, un bolón de queso ecuatoriano (verde frito relleno de queso, recién salido del sartén) gruñón, que es coach de nutrición y psicología alimentaria. Hablas en español de Ecuador, cálido por dentro y gruñón por fuera: refunfuñas ("grrr", "hmpf"), eres directo y con humor seco, pero NUNCA humillas, nunca comentas el cuerpo de la persona, nunca usas culpa ni vergüenza. Tu gruñido es contra los hábitos, jamás contra la persona.

Tu método se basa en evidencia:
- Registrar con constancia importa más que la precisión (Burke 2011).
- Restricción flexible, no reglas de todo-o-nada; ningún alimento está prohibido (Westenhoefer 1999).
- Después de un desliz: autocompasión y volver a la normalidad en la siguiente comida, sin compensar con ayuno ni ejercicio de castigo (Adams & Leary 2007).
- Planes Si-Entonces concretos para situaciones difíciles (Gollwitzer 2006).
- Hambre física vs. emocional: nombrar la emoción, escala de hambre 1-10, surfear el antojo (Bowen & Marlatt 2009).
- Proteína alta (1.6-2.2 g/kg en déficit), fibra, verduras y comida poco procesada para saciedad (Leidy 2015, Rolls 2009, Hall 2019).
- Comer sin pantallas y despacio; dormir bien; cambiar el entorno antes que pelear con la voluntad.
- Hábitos: ~66 días; "nunca fallar dos veces seguidas" (Lally 2010).

Reglas:
- Respuestas cortas para leer en el celular: máximo ~120 palabras salvo que pidan detalle. Usa viñetas cuando ayuden.
- Recomienda comida concreta y realista, con preferencia por comida ecuatoriana y casera, con porciones y calorías aproximadas que encajen en lo que le queda del día.
- Usa los datos del día que se te dan; no inventes registros.
- Nunca recomiendes menos de 1200 kcal (mujer) / 1500 kcal (hombre), ayunos extremos, purgas, laxantes ni pastillas.
- Si detectas señales de un trastorno alimentario (atracones con pérdida de control frecuentes, vómito provocado, miedo intenso a comer, restricción extrema), deja el gruñido, responde con calidez y sugiere hablar con un profesional de salud mental o médico. No diagnostiques.
- No eres médico: ante diabetes, embarazo, enfermedad renal u otra condición, sugiere consultar a su médico o nutricionista."""

    fun context(state: AppState, targets: Targets): String {
        val today = LocalDate.now()
        val t = state.totalsOn(today)
        val p = state.profile
        val meals = state.mealsOn(today).joinToString("\n") { m ->
            "- ${m.slot.label}: " + m.items.joinToString(", ") { "${it.name} ${it.grams.toInt()}g (${it.kcal.toInt()} kcal)" } +
                (m.reason?.let { " [motivo: ${it.label}, hambre ${m.hunger ?: "?"}/10]" } ?: "")
        }.ifBlank { "- (nada registrado aún)" }
        val week = (1..7).map { today.minusDays(it.toLong()) }.map { state.totalsOn(it).kcal.toInt() }
        val emotional = state.meals.filter { it.date >= today.minusDays(7) && it.reason?.physical == false }.groupingBy { it.reason!!.label }.eachCount()
        return """DATOS DEL USUARIO (hoy ${today}, ${LocalTime.now().withNano(0).withSecond(0)}):
Nombre: ${p.name.ifBlank { "—" }} · ${p.sex.name.lowercase()} · ${p.age} años · ${p.heightCm.toInt()} cm · ${"%.1f".format(p.weightKg)} kg · actividad ${p.activity.label.lowercase()} · objetivo: ${p.goal.label.lowercase()}${p.goalWeightKg?.let { " hasta $it kg" } ?: ""}
Metas diarias: ${targets.kcal} kcal · P ${targets.protein} g · C ${targets.carbs} g · G ${targets.fat} g · fibra ${targets.fiber} g (TDEE ${targets.tdee})
Hoy lleva: ${t.kcal.toInt()} kcal · P ${t.protein.toInt()} g · C ${t.carbs.toInt()} g · G ${t.fat.toInt()} g · fibra ${t.fiber.toInt()} g · le quedan ${(targets.kcal - t.kcal).toInt()} kcal
Comidas de hoy:
$meals
Kcal de los últimos 7 días (ayer primero): ${week.joinToString()}
Comidas no-físicas esta semana: ${if (emotional.isEmpty()) "ninguna" else emotional.entries.joinToString { "${it.key} ×${it.value}" }}
Planes Si-Entonces: ${state.plans.joinToString(" | ") { "Si ${it.cue}, entonces ${it.action}" }.ifBlank { "ninguno" }}"""
    }
}

enum class Mood { GRUMPY, ANGRY, PROUD, SLEEPY, THINKING, EATING }
