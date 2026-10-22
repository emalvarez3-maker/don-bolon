package ec.donbolon.app.data

import java.time.LocalDate
import java.time.LocalTime

/** Something Don Bolón says: a line, optionally the study behind it and a one-tap next step. */
data class Tip(val mood: Mood, val text: String, val source: String? = null, val action: Action? = null) {
    enum class Action(val label: String) { CAPTURE("Registrar"), SOS("3 minutos"), CHAT("¿Qué como?") }
}

/** One evidence-based principle the app is built on; Don Bolón brings them up one at a time. */
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
            "Cuadritos: recomposición",
            "Con pesas, proteína alta (≈2.2 g/kg) y un déficit pequeño se puede perder grasa y ganar músculo al mismo tiempo, sobre todo si eres principiante o vuelves a entrenar. Los abdominales no queman la grasa local: la panza baja con el déficit total.",
            "Entrena fuerza 3–5 veces por semana, progresa en peso o repeticiones y duerme 7–9 h.",
            "Barakat et al. (2020), Strength & Conditioning Journal; Vispute et al. (2011), J Strength Cond Res",
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

    /**
     * What Don Bolón has to say right now, most relevant first. The science lives here instead
     * of on the screen: he brings up a principle only when the day gives a reason to.
     */
    fun tips(state: AppState, targets: Targets, now: LocalTime = LocalTime.now()): List<Tip> {
        val today = LocalDate.now()
        val t = state.totalsOn(today)
        val meals = state.mealsOn(today)
        val left = targets.kcal - t.kcal
        val yesterday = state.totalsOn(today.minusDays(1)).kcal
        val name = state.profile.name.ifBlank { "humano" }
        val streak = Nutrition.streak(state.loggedDays, today)
        val water = state.water[today] ?: 0
        val out = mutableListOf<Tip>()

        if (now.hour >= 23 || now.hour < 5) out += Tip(
            Mood.SLEEPY, "Grrr… ya es tarde, $name. La cocina cerró. Dormir poco da más hambre mañana: agua y a la cama.",
            "Dormir mal sube la grelina y los antojos (Spiegel et al., 2004)",
        )
        val focus = state.focus?.takeIf { it.weekStart == Memory.monday(today) }
        if (focus != null && today.dayOfWeek == java.time.DayOfWeek.MONDAY) out += Tip(
            Mood.PROUD, "${focus.review} Esta semana vamos por una sola cosa: ${focus.title.lowercase()}. ${focus.plan}",
            "Una meta concreta rinde más que varias vagas (Locke & Latham, 2002)",
        )
        if (meals.isEmpty() && yesterday > targets.kcal * 1.15) out += Tip(
            Mood.GRUMPY, "Ayer te pasaste. ¿Y? Hoy se come normal. Nada de compensar con hambre: eso es lo que trae el rebote.",
            "La autocompasión tras un desliz reduce comer de más (Adams & Leary, 2007)",
        )
        val fast = Fast.state(state, now.atDate(today))
        if (fast != null && !fast.eating) out += Tip(
            Mood.CALM, "Vas ${"%.0f".format(fast.hoursFasted)} h de ayuno y tu ventana abre en ${Fast.format(fast.minutesToChange)}. Agua, café negro o té sin azúcar no lo rompen.",
            "El ayuno rinde igual que cualquier déficit; su ventaja es que a muchos les facilita sostenerlo (Lowe et al., 2020; Liu et al., 2022)",
        )
        if (fast != null && fast.eating && meals.isEmpty()) out += Tip(
            Mood.HAPPY, "¡Ventana abierta! Rompe el ayuno con proteína y verduras, no con pan dulce: te mantiene lleno hasta la siguiente.",
            "La proteína es lo que más sacia (Leidy et al., 2015)", Tip.Action.CHAT,
        )
        if (fast != null && fast.eating && fast.minutesToChange <= 90 && t.protein < targets.protein * 0.7) out += Tip(
            Mood.GRUMPY, "Tu ventana cierra en ${Fast.format(fast.minutesToChange)} y te faltan ${(targets.protein - t.protein).toInt()} g de proteína. Que la última comida sea fuerte en proteína.",
            "Con menos horas para comer, la proteína es lo primero que se queda corta (Lowe et al., 2020)",
        )
        // While fasting there is nothing to log yet, so no nagging.
        if (meals.isEmpty() && (fast == null || fast.eating)) out += if (now.hour >= 11) Tip(
            Mood.GRUMPY, "¿Nada registrado todavía? Grrr. No te pido perfección, te pido una foto.",
            "Registrar con constancia es lo que más predice el resultado (Burke et al., 2011)", Tip.Action.CAPTURE,
        ) else Tip(Mood.CALM, "Hmpf. Buenos días, $name. Cuando comas algo, me lo enseñas. Yo vigilo.", null, Tip.Action.CAPTURE)

        // Praise right after the behavior: immediate reward is what makes a habit stick (Wood & Neal, 2016).
        val last = meals.lastOrNull()
        if (last != null && last.totals.protein >= 30 && last.totals.kcal <= targets.kcal * 0.45) out += Tip(
            Mood.HAPPY, "¡Eso! ${last.totals.protein.toInt()} g de proteína en esa comida y sin pasarte. Así se ven los cuadritos llegar.",
            "La proteína repartida en cada comida favorece el músculo (Areta et al., 2013)",
        )
        val lastReason = meals.lastOrNull()?.reason
        if (lastReason != null && !lastReason.physical) out += Tip(
            Mood.GRUMPY, "La última fue por ${lastReason.label.lowercase()}, no por hambre. La próxima vez que te pase, aguanta 3 minutos conmigo antes.",
            "Los antojos suben y bajan solos como olas (Bowen & Marlatt, 2009)", Tip.Action.SOS,
        )
        if (left < -targets.kcal * targets.band) out += Tip(
            Mood.GRUMPY, "Te pasaste ${(-left).toInt()} kcal. Respira. Una comida no decide nada; lo que daña es el \"ya qué más da\".",
            "El efecto \"qué más da\" en dietas (Polivy & Herman, 1985)",
        )
        if (state.profile.priorities.muscle >= 0.5 && today !in state.workouts && now.hour in 16..21 &&
            Nutrition.workoutsThisWeek(state) < state.profile.trainDays
        ) out += Tip(
            Mood.GRUMPY, "¿Y el entreno? Los cuadritos se descubren en la cocina, pero se construyen con pesas. Llevas ${Nutrition.workoutsThisWeek(state)} de ${state.profile.trainDays} esta semana.",
            "Cada músculo ≥ 2 veces por semana (Schoenfeld et al., 2016)",
        )
        if (meals.isNotEmpty() && Nutrition.onTarget(t.kcal, targets.kcal, targets.band) && t.protein >= targets.protein * 0.9) out += Tip(
            Mood.HAPPY, "…Bien hecho. No te acostumbres a que lo diga. Calorías en rango y proteína cumplida.",
        )
        if (meals.isNotEmpty() && t.protein < targets.protein * 0.5 && now.hour >= 14) out += Tip(
            Mood.GRUMPY, "Llevas ${t.protein.toInt()} g de proteína de ${targets.protein}. Ni un bolón vive solo de verde: pollo, huevo, atún o chochos en la siguiente.",
            "La proteína es lo que más sacia y protege el músculo (Leidy et al., 2015)",
        )
        if (meals.size >= 2 && t.fiber < targets.fiber * 0.4 && now.hour >= 17) out += Tip(
            Mood.CALM, "Poca fibra hoy (${t.fiber.toInt()} g). Media porción de verduras o una fruta en la cena y listo.",
            "25–30 g de fibra se asocian a menos enfermedad (Reynolds et al., 2019)",
        )
        if (now.hour in 15..20 && water < targets.waterGlasses / 3) out += Tip(
            Mood.CALM, "Llevas $water vasos de agua. A veces la \"hambre\" de la tarde es sed. Toma uno y vuelve a preguntarte.",
        )
        if (streak in listOf(3, 7, 14, 21, 30, 45, 66) && meals.isNotEmpty()) out += Tip(
            Mood.HAPPY, "$streak días seguidos registrando. Hmpf. Así es como un hábito se vuelve automático.",
            "En promedio un hábito tarda 66 días (Lally et al., 2010)",
        )
        if (meals.isNotEmpty() && meals.none { it.mindful } && now.hour in 11..15) out += Tip(
            Mood.CALM, "Hoy come una vez sin celular. Despacio. Notarás que te llenas antes.",
            "Comer distraído aumenta lo que comes (Robinson et al., 2013)",
        )
        if (meals.isNotEmpty() && left > 0) out += Tip(Mood.CALM, "Te quedan ${left.toInt()} kcal. Úsalas con cabeza, no con antojo.", null, Tip.Action.CHAT)
        if (focus != null && today.dayOfWeek != java.time.DayOfWeek.MONDAY) out += Tip(
            Mood.CALM, "Recuerda el enfoque de la semana: ${focus.title.lowercase()}. ${focus.plan}",
            "Planes Si-Entonces duplican la probabilidad de cumplir (Gollwitzer & Sheeran, 2006)",
        )
        // Always end with one principle from the library, so there is something new each day.
        val p = tipOfDay()
        out += Tip(Mood.CALM, "${p.title}. ${p.tryThis}", p.source)
        return out
    }

    /** The persona and rules for the AI coach. Context about the user is appended per request. */
    const val COACH_PERSONA = """Eres Don Bolón, un bolón de queso ecuatoriano (verde frito relleno de queso, recién salido del sartén) gruñón, que es coach de nutrición y psicología alimentaria. Hablas en español de Ecuador, cálido por dentro y gruñón por fuera: refunfuñas ("grrr", "hmpf"), eres directo y con humor seco, pero NUNCA humillas, nunca comentas el cuerpo de la persona, nunca usas culpa ni vergüenza. Tu gruñido es contra los hábitos, jamás contra la persona.

Tu método se basa en evidencia:
- Registrar con constancia importa más que la precisión (Burke 2011): un registro aproximado vale; nunca digas que registrar a medias no sirve.
- Si la persona hace ayuno intermitente (ver datos), no la regañes por no comer fuera de su ventana: es su estrategia.
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
Nombre: ${p.name.ifBlank { "—" }} · ${p.sex.name.lowercase()} · ${p.age} años · ${p.heightCm.toInt()} cm · ${"%.1f".format(p.weightKg)} kg · actividad ${p.activity.label.lowercase()} · prioridades (0-100): ${Priorities.names.zip(p.priorities.values).joinToString { "${it.first} ${(it.second * 100).toInt()}" }}${p.goalWeightKg?.let { " hasta $it kg" } ?: ""}
Metas diarias: ${targets.kcal} kcal · P ${targets.protein} g · C ${targets.carbs} g · G ${targets.fat} g · fibra ${targets.fiber} g (TDEE ${targets.tdee})
Hoy lleva: ${t.kcal.toInt()} kcal · P ${t.protein.toInt()} g · C ${t.carbs.toInt()} g · G ${t.fat.toInt()} g · fibra ${t.fiber.toInt()} g · le quedan ${(targets.kcal - t.kcal).toInt()} kcal
Comidas de hoy:
$meals
Kcal de los últimos 7 días (ayer primero): ${week.joinToString()}
Comidas no-físicas esta semana: ${if (emotional.isEmpty()) "ninguna" else emotional.entries.joinToString { "${it.key} ×${it.value}" }}
Entrenos de fuerza esta semana: ${Nutrition.workoutsThisWeek(state)} de ${p.trainDays} planeados${if (today in state.workouts) " (hoy entrenó)" else ""}
Ayuno intermitente: ${if (p.fasting == Fasting.NINGUNO) "no hace" else "${p.fasting.label}, ventana de ${Fast.opens(p)} a ${Fast.closes(p)}; sugiere comidas solo dentro de esa ventana"}
${Memory.forCoach(state, targets)}"""
    }
}

enum class Mood { GRUMPY, ANGRY, PROUD, SLEEPY, THINKING, EATING, HAPPY, CALM }
