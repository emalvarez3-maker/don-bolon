package ec.donbolon.app.data

import java.time.LocalDate
import java.time.LocalTime

/** Something Don Bolón says: a line, optionally the study behind it and a one-tap next step. */
data class Tip(val mood: Mood, val text: String, val source: String? = null, val action: Action? = null) {
    enum class Action(val label: String) { CAPTURE("Registrar"), SOS("3 minutos"), CHAT("¿Qué como?") }
}

/**
 * One finding from the literature, told the way Don Bolón talks. Every number was checked
 * against the paper's abstract; where a figure is shaky or the sample is narrow, the line says so.
 */
data class Principle(val title: String, val line: String, val source: String)

object Science {
    val principles = listOf(
        Principle(
            "Registrar",
            "No te pido ser contador. En 22 estudios, los que más registraban eran los que más bajaban, aunque anotaran mal. Una foto chueca vale más que una comida olvidada.",
            "Burke, Wang & Sevick (2011), J Am Diet Assoc",
        ),
        Principle(
            "La báscula",
            "En un ensayo con pesaje diario, el 43% bajó al menos 5% de su peso; en el grupo sin báscula, el 7%. Y no les dio más ansiedad. Pésate, pero cree en la línea, no en el número del día.",
            "Steinberg et al. (2013), Obesity; Steinberg et al. (2014), Am J Prev Med",
        ),
        Principle(
            "Dormir",
            "Dormir poco te hace comer unas 385 kcal más al día sin gastar más. Son como cuatro panes y medio que nadie te invitó. La almohada también hace dieta.",
            "Al Khatib et al. (2018), Eur J Clin Nutr (meta-análisis)",
        ),
        Principle(
            "Comer tarde",
            "Mismas calorías, pero más tarde: más hambre al día siguiente y menos gasto de energía. Por eso tu cocina cierra con tu ventana, no a medianoche.",
            "Vujović et al. (2022), Cell Metabolism",
        ),
        Principle(
            "Ultraprocesados",
            "Con comida ultraprocesada la gente comió 508 kcal más al día que con comida de verdad, aunque podían elegir igual. La diferencia no fue la fuerza de voluntad: fue la fábrica.",
            "Hall et al. (2019), Cell Metabolism",
        ),
        Principle(
            "La sopa primero",
            "Un plato de sopa de verduras antes del almuerzo y terminas comiendo 20% menos en total, sopa incluida. El locro de la abuela tenía razón (el chicharrón encima, no tanto).",
            "Flood & Rolls (2007), Appetite",
        ),
        Principle(
            "Agua antes de comer",
            "Medio litro de agua antes de cada comida: en 12 semanas bajaron 2 kg más que solo con dieta. Ojo, fue en adultos de 55 a 75; en jóvenes la evidencia es menor, pero tampoco te hace daño.",
            "Dennis et al. (2010), Obesity",
        ),
        Principle(
            "Moverse sin gimnasio",
            "Les dieron 1000 kcal de más por 8 semanas: los que se movían más sin 'hacer ejercicio' (caminar, pararse, moverse) guardaron mucho menos grasa. Ir a pie a la tienda sí cuenta.",
            "Levine, Eberhardt & Jensen (1999), Science",
        ),
        Principle(
            "Cuánta proteína",
            "Hacia 1,6 g por kilo ya ves casi todo el beneficio para el músculo; en déficit conviene acercarse a 2,2, porque el margen del estudio llega hasta ahí. Más no te hace más fuerte, te hace más lleno.",
            "Morton et al. (2018), Br J Sports Med (49 estudios)",
        ),
        Principle(
            "Músculo y grasa a la vez",
            "Con 2,4 g/kg de proteína, pesas fuertes y un déficit grande, jóvenes ganaron músculo mientras perdían grasa en solo 4 semanas. Los cuadritos se construyen y se destapan al mismo tiempo.",
            "Longland et al. (2016), Am J Clin Nutr",
        ),
        Principle(
            "Despacio no es flojo",
            "Atletas que bajaron 0,7% de su peso por semana ganaron músculo; los que bajaron 1,4% no. Ir lento es la estrategia, no la excusa.",
            "Garthe et al. (2011), Int J Sport Nutr Exerc Metab",
        ),
        Principle(
            "Descansos de dieta",
            "Hombres que alternaron 2 semanas de dieta con 2 de mantenimiento perdieron más grasa (12,3 vs 8,0 kg) que los que no pararon nunca. Descansar de la dieta también es parte del plan.",
            "Byrne et al. (2018), Int J Obesity (estudio MATADOR)",
        ),
        Principle(
            "66 días",
            "Un hábito tarda en promedio 66 días en volverse automático, aunque hubo gente que lo logró en 18 y otros en 254. Fallar un día no borró nada; lo que pesa es no volver.",
            "Lally et al. (2010), Eur J Soc Psychol",
        ),
        Principle(
            "Si pasa esto, hago esto",
            "Decidir antes qué harás en una situación concreta tiene un efecto entre medio y grande sobre cumplir metas, en 94 estudios. Tu cerebro sigue el plan sin pedirle permiso a la fuerza de voluntad.",
            "Gollwitzer & Sheeran (2006), Adv Exp Soc Psychol (d≈0,65)",
        ),
        Principle(
            "Nada prohibido",
            "Las dietas de todo o nada se asocian a más atracones; las flexibles, a mejor control con menos ansiedad. El pan no es el enemigo. El enemigo es el 'ya la regué, ahora todo'.",
            "Westenhoefer et al. (1999), Appetite",
        ),
        Principle(
            "Después de un desliz",
            "A gente a dieta le dieron una dona y luego le dijeron 'a todos nos pasa': comieron menos dulces después que los que se quedaron con la culpa. La culpa engorda más que la dona.",
            "Adams & Leary (2007), J Soc Clin Psychol",
        ),
        Principle(
            "Surfear el antojo",
            "Mirar el antojo como una ola que sube y baja, sin pelearle, redujo los antojos de cigarrillo y, en otro programa, los de comida. Tres minutos y la ola pasa sola.",
            "Bowen & Marlatt (2009), Psychol Addict Behav; Alberts et al. (2012), Appetite",
        ),
        Principle(
            "Proteína que llena",
            "La proteína es lo que más sacia por caloría y protege el músculo cuando comes menos. Si te da hambre a media tarde, revisa cuánta proteína hubo en el almuerzo.",
            "Leidy et al. (2015), Am J Clin Nutr",
        ),
        Principle(
            "Sin pantallas",
            "Comer distraído hace que comas más en esa comida y todavía más en la siguiente, según 24 estudios. Una comida al día sin celular. El TikTok no tiene calorías, pero las suma.",
            "Robinson et al. (2013), Am J Clin Nutr (meta-análisis)",
        ),
        Principle(
            "Despacio en la mesa",
            "Comiendo despacio, mujeres comieron unas 70 kcal menos en la misma comida y se sintieron igual de llenas. Suelta la cuchara entre bocado y bocado.",
            "Andrade, Greene & Melanson (2008), J Am Diet Assoc",
        ),
        Principle(
            "La cintura cuenta",
            "La cintura dice más de tu salud que el peso solo; un panel internacional pidió medirla como un signo vital. Si baja aunque la báscula no se mueva, estás ganando músculo.",
            "Ross et al. (2020), Nat Rev Endocrinol (consenso)",
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

        // Several ways to say the same thing, rotating by day, so he never sounds like a recording.
        fun pick(vararg lines: String) = lines[(today.toEpochDay() % lines.size).toInt()]

        if (now.hour >= 23 || now.hour < 5) out += Tip(
            Mood.SLEEPY, pick(
                "Grrr… ya es tarde, $name. Dormir poco te cobra unas 385 kcal de hambre mañana. La cama, ya.",
                "Hmpf. A esta hora el único bolón despierto soy yo. Apaga eso: dormir también te marca el abdomen.",
                "La cocina cerró y yo también. Si te da hambre, es sueño disfrazado. Agua y a dormir.",
            ),
            "Dormir poco suma ~385 kcal al día (Al Khatib et al., 2018, meta-análisis)",
        )
        val focus = state.focus?.takeIf { it.weekStart == Memory.monday(today) }
        if (focus != null && today.dayOfWeek == java.time.DayOfWeek.MONDAY) out += Tip(
            Mood.PROUD, "${focus.review} Esta semana vamos por una sola cosa: ${focus.title.lowercase()}. ${focus.plan}",
            "Una meta concreta rinde más que varias vagas (Locke & Latham, 2002)",
        )
        if (meals.isEmpty() && yesterday > targets.kcal * 1.15) out += Tip(
            Mood.CALM, pick(
                "Ayer te pasaste. ¿Y? Hoy se come normal. Si castigas con hambre, el rebote cobra con intereses.",
                "Lo de ayer ya es historia, no destino. Hoy, comida normal y a seguir. La culpa engorda más que el postre.",
            ),
            "Tras un desliz, la autocompasión reduce comer de más (Adams & Leary, 2007)",
        )
        val fast = Fast.state(state, now.atDate(today))
        if (fast != null && !fast.eating) out += Tip(
            Mood.CALM, "Vas ${"%.0f".format(fast.hoursFasted)} h de ayuno; tu ventana abre en ${Fast.format(fast.minutesToChange)}. " + pick(
                "Agua, café negro o té sin azúcar no lo rompen.",
                "Si te ruge la panza, medio litro de agua: muchas veces es sed.",
                "El ayuno no quema más que cualquier déficit; te sirve si te ayuda a comer menos sin sufrir.",
            ),
            "El ayuno rinde igual que contar calorías; su ventaja es la adherencia (Lowe et al., 2020; Liu et al., 2022)",
        )
        if (fast != null && fast.eating && meals.isEmpty()) out += Tip(
            Mood.HAPPY, pick(
                "¡Ventana abierta! Rompe el ayuno con proteína y verduras: llegas lleno hasta la siguiente.",
                "Se abrió la cocina. Un caldo o ensalada primero y luego la proteína: comerás menos sin pelear.",
            ),
            "La proteína sacia más (Leidy et al., 2015); una sopa antes reduce 20% lo que comes (Flood & Rolls, 2007)", Tip.Action.CHAT,
        )
        if (fast != null && fast.eating && fast.minutesToChange <= 90 && t.protein < targets.protein * 0.7) out += Tip(
            Mood.GRUMPY, "Tu ventana cierra en ${Fast.format(fast.minutesToChange)} y te faltan ${(targets.protein - t.protein).toInt()} g de proteína. Que la última comida sea de las que construyen músculo.",
            "Con menos horas para comer, la proteína es lo que primero se queda corta (Lowe et al., 2020)",
        )
        // While fasting there is nothing to log yet, so no nagging.
        if (meals.isEmpty() && (fast == null || fast.eating)) out += if (now.hour >= 11) Tip(
            Mood.GRUMPY, pick(
                "¿Nada registrado todavía? Grrr. No te pido perfección, te pido una foto.",
                "Plato que no se registra igual cuenta. Tómale foto.",
                "En 22 estudios, los que registraban bajaban más. Tú todavía vas en cero hoy. Hmpf.",
            ),
            "Registrar con constancia es lo que más predice el resultado (Burke et al., 2011)", Tip.Action.CAPTURE,
        ) else Tip(
            Mood.CALM,
            pick("Hmpf. Buenos días, $name. Cuando comas algo, me lo enseñas.", "Buen día. Yo vigilo; tú comes y me muestras.", "Arriba, $name. Hoy toca hacerlo simple: comer, foto, seguir."),
            null, Tip.Action.CAPTURE,
        )

        // Praise right after the behavior: immediate reward is what makes a habit stick (Wood & Neal, 2016).
        val last = meals.lastOrNull()
        if (last != null && last.totals.protein >= 30 && last.totals.kcal <= targets.kcal * 0.45) out += Tip(
            Mood.HAPPY, pick(
                "¡Eso! ${last.totals.protein.toInt()} g de proteína en esa comida y sin pasarte. Así se construyen los cuadritos.",
                "${last.totals.protein.toInt()} g de proteína. Hmpf… está bien, lo admito: esa comida estuvo de campeonato.",
            ),
            "Proteína repartida en cada comida favorece el músculo (Areta et al., 2013)",
        )
        val lastReason = meals.lastOrNull()?.reason
        if (lastReason != null && !lastReason.physical) out += Tip(
            Mood.CALM, "La última fue por ${lastReason.label.lowercase()}, no por hambre. La próxima vez, tres minutos conmigo antes: la ola sube y baja sola.",
            "Surfear el antojo lo reduce (Bowen & Marlatt, 2009; Alberts et al., 2012)", Tip.Action.SOS,
        )
        if (left < -targets.kcal * targets.band) out += Tip(
            Mood.GRUMPY, pick(
                "Te pasaste ${(-left).toInt()} kcal. Respira. Una comida no decide nada; lo que daña es el \"ya la regué, ahora todo\".",
                "${(-left).toInt()} kcal arriba. No es tragedia, es un dato. Mañana, normal; nada de compensar.",
            ),
            "El efecto \"qué más da\" en dietas (Polivy & Herman, 1985)",
        )
        if (state.profile.priorities.muscle >= 0.5 && today !in state.workouts && now.hour in 16..21 &&
            Nutrition.workoutsThisWeek(state) < state.profile.trainDays
        ) out += Tip(
            Mood.GRUMPY, pick(
                "¿Y el entreno? Los cuadritos se destapan en la cocina, pero se construyen con pesas. Llevas ${Nutrition.workoutsThisWeek(state)} de ${state.profile.trainDays}.",
                "En déficit, las pesas le dicen a tu cuerpo que el músculo no se toca. Llevas ${Nutrition.workoutsThisWeek(state)} de ${state.profile.trainDays} esta semana.",
            ),
            "Con proteína alta y pesas se gana músculo aun en déficit (Longland et al., 2016)",
        )
        if (meals.isNotEmpty() && Nutrition.onTarget(t.kcal, targets.kcal, targets.band) && t.protein >= targets.protein * 0.9) out += Tip(
            Mood.HAPPY, pick(
                "…Bien hecho. No te acostumbres a que lo diga. Calorías en rango y proteína cumplida.",
                "Día redondo. Hmpf. Hasta se me escurrió el queso de la emoción.",
            ),
        )
        if (meals.isNotEmpty() && t.protein < targets.protein * 0.5 && now.hour >= 14) out += Tip(
            Mood.GRUMPY, "Llevas ${t.protein.toInt()} g de proteína de ${targets.protein}. Ni un bolón vive solo de verde: pollo, huevo, atún o chochos en la siguiente.",
            "La proteína es lo que más sacia por caloría (Leidy et al., 2015)",
        )
        if (meals.size >= 2 && t.fiber < targets.fiber * 0.4 && now.hour >= 17) out += Tip(
            Mood.CALM, "Poca fibra hoy (${t.fiber.toInt()} g). Media porción de verduras o una fruta en la cena y listo.",
            "25–30 g de fibra al día se asocian a menos enfermedad (Reynolds et al., 2019, The Lancet)",
        )
        if (now.hour in 15..20 && water < targets.waterGlasses / 3) out += Tip(
            Mood.CALM, "Llevas $water vasos de agua. Medio litro antes de la próxima comida: en un ensayo, eso ayudó a bajar 2 kg más en 12 semanas.",
            "Dennis et al. (2010), Obesity (adultos de 55–75 años)",
        )
        if (streak in listOf(3, 7, 14, 21, 30, 45, 66) && meals.isNotEmpty()) out += Tip(
            Mood.HAPPY, "$streak días seguidos registrando. Hmpf. Así es como un hábito deja de costar.",
            "En promedio un hábito tarda 66 días, con un rango de 18 a 254 (Lally et al., 2010)",
        )
        if (meals.isNotEmpty() && meals.none { it.mindful } && now.hour in 11..15) out += Tip(
            Mood.CALM, pick(
                "Hoy come una vez sin celular. Comer distraído te hace comer más… y también en la siguiente comida.",
                "Suelta la cuchara entre bocado y bocado. Comiendo despacio se comen unas 70 kcal menos y llenas igual.",
            ),
            "Robinson et al. (2013), Am J Clin Nutr; Andrade et al. (2008), J Am Diet Assoc",
        )
        if (meals.isNotEmpty() && left > 0) out += Tip(
            Mood.CALM, pick("Te quedan ${left.toInt()} kcal. Úsalas con cabeza, no con antojo.", "Quedan ${left.toInt()} kcal en la cuenta. ¿Te armo algo con buena proteína?"),
            null, Tip.Action.CHAT,
        )
        if (focus != null && today.dayOfWeek != java.time.DayOfWeek.MONDAY) out += Tip(
            Mood.CALM, "Recuerda el enfoque de la semana: ${focus.title.lowercase()}. ${focus.plan}",
            "Planear \"si pasa esto, hago esto\" tiene un efecto medio-grande sobre cumplir metas (Gollwitzer & Sheeran, 2006)",
        )
        // Always end with one principle from the library, so there is something new each day.
        val p = tipOfDay()
        out += Tip(Mood.CALM, p.line, p.source)
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
Ayuno intermitente: ${if (!p.fasting) "no hace" else "${p.fastLabel}, ventana de ${Fast.opens(p)} a ${Fast.closes(p)}; sugiere comidas solo dentro de esa ventana"}
${Memory.forCoach(state, targets)}"""
    }
}

enum class Mood { GRUMPY, ANGRY, PROUD, SLEEPY, THINKING, EATING, HAPPY, CALM }
