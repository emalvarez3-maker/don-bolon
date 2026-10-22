package ec.donbolon.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class Sex { HOMBRE, MUJER }

/**
 * What the user wants, each from 0 to 1, drawn as a pentagon. Every daily target is derived
 * from these five numbers (see [Nutrition.targets]); presets are just starting points.
 */
data class Priorities(
    val fat: Double = 0.9,
    val muscle: Double = 0.8,
    val performance: Double = 0.4,
    val health: Double = 0.6,
    val flexibility: Double = 0.5,
) {
    val values get() = listOf(fat, muscle, performance, health, flexibility)

    fun with(i: Int, v: Double): Priorities {
        val x = v.coerceIn(0.0, 1.0)
        return when (i) { 0 -> copy(fat = x); 1 -> copy(muscle = x); 2 -> copy(performance = x); 3 -> copy(health = x); else -> copy(flexibility = x) }
    }

    fun toJson(): JSONObject = JSONObject().put("fat", fat).put("muscle", muscle).put("performance", performance)
        .put("health", health).put("flexibility", flexibility)

    companion object {
        val names = listOf("Perder grasa", "Ganar músculo", "Rendimiento", "Salud", "Flexibilidad")
        val short = listOf("Grasa ↓", "Músculo ↑", "Rendir", "Salud", "Flexible")

        fun from(o: JSONObject) = Priorities(
            o.optDouble("fat", 0.9), o.optDouble("muscle", 0.8), o.optDouble("performance", 0.4),
            o.optDouble("health", 0.6), o.optDouble("flexibility", 0.5),
        )
    }
}

/** Presets that set the pentagon; the sliders can then fine-tune it. */
enum class Goal(val label: String, val detail: String, val priorities: Priorities) {
    DEFINIR("Marcar abdomen", "Bajar grasa y ganar músculo a la vez: déficit pequeño, mucha proteína y pesas", Priorities(0.9, 0.8, 0.4, 0.6, 0.5)),
    BAJAR("Bajar grasa", "Déficit más grande; prioridad: perder grasa", Priorities(1.0, 0.3, 0.2, 0.6, 0.5)),
    SUBIR("Ganar músculo", "Superávit pequeño para crecer con poca grasa", Priorities(0.1, 1.0, 0.5, 0.5, 0.5)),
    RENDIR("Rendir", "Energía para entrenar fuerte; el peso queda estable", Priorities(0.2, 0.5, 1.0, 0.6, 0.4)),
    MANTENER("Salud", "Comer lo que gastas, con calidad", Priorities(0.0, 0.2, 0.3, 1.0, 0.7)),
}

enum class Activity(val label: String, val detail: String, val factor: Double) {
    SEDENTARIO("Sedentario", "Oficina, casi sin ejercicio", 1.2),
    LIGERO("Ligero", "1–3 entrenos o caminatas por semana", 1.375),
    MODERADO("Moderado", "3–5 entrenos por semana", 1.55),
    ALTO("Alto", "6–7 entrenos o trabajo físico", 1.725),
    ATLETA("Atleta", "Doble sesión o trabajo muy pesado", 1.9),
}

enum class Slot(val label: String, val emoji: String) {
    DESAYUNO("Desayuno", "🌅"), ALMUERZO("Almuerzo", "☀️"), CENA("Cena", "🌙"), SNACK("Snack", "🍎");

    companion object {
        fun forHour(h: Int) = when (h) {
            in 5..10 -> DESAYUNO
            in 11..15 -> ALMUERZO
            in 18..22 -> CENA
            else -> SNACK
        }
    }
}

/** Why the user is about to eat: the core of the hunger-awareness check. */
enum class Reason(val label: String, val physical: Boolean) {
    HAMBRE("Hambre de verdad", true),
    HORA("Es la hora", true),
    ANTOJO("Antojo", false),
    ESTRES("Estrés / ansiedad", false),
    ABURRIMIENTO("Aburrimiento", false),
    SOCIAL("Social / me invitaron", false),
    TRISTEZA("Tristeza / cansancio", false),
}

data class Profile(
    val name: String = "",
    val sex: Sex = Sex.HOMBRE,
    val age: Int = 25,
    val heightCm: Double = 170.0,
    val weightKg: Double = 75.0,
    val activity: Activity = Activity.LIGERO,
    val goal: Goal = Goal.BAJAR,
    val rateKgWeek: Double = 0.5,
    val goalWeightKg: Double? = null,
    val trainDays: Int = 4,
    val priorities: Priorities = goal.priorities,
    val onboarded: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("sex", sex.name).put("age", age).put("heightCm", heightCm)
        .put("weightKg", weightKg).put("activity", activity.name).put("goal", goal.name)
        .put("rateKgWeek", rateKgWeek).put("goalWeightKg", goalWeightKg ?: JSONObject.NULL)
        .put("trainDays", trainDays).put("priorities", priorities.toJson()).put("onboarded", onboarded)

    companion object {
        fun from(o: JSONObject) = Profile(
            name = o.optString("name"),
            sex = enumOr(o.optString("sex"), Sex.HOMBRE),
            age = o.optInt("age", 25),
            heightCm = o.optDouble("heightCm", 170.0),
            weightKg = o.optDouble("weightKg", 75.0),
            activity = enumOr(o.optString("activity"), Activity.LIGERO),
            goal = enumOr(o.optString("goal"), Goal.BAJAR),
            rateKgWeek = o.optDouble("rateKgWeek", 0.5),
            goalWeightKg = if (o.isNull("goalWeightKg")) null else o.optDouble("goalWeightKg"),
            trainDays = o.optInt("trainDays", 4),
            priorities = o.optJSONObject("priorities")?.let(Priorities::from) ?: enumOr(o.optString("goal"), Goal.BAJAR).priorities,
            onboarded = o.optBoolean("onboarded"),
        )
    }
}

data class FoodItem(
    val name: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double = 0.0,
) {
    /** The same food at a different portion: every nutrient scales with the grams. */
    fun withGrams(g: Double): FoodItem {
        if (grams <= 0.0) return copy(grams = g)
        val k = g / grams
        return copy(grams = g, kcal = kcal * k, protein = protein * k, carbs = carbs * k, fat = fat * k, fiber = fiber * k)
    }

    fun toJson(): JSONObject = JSONObject().put("name", name).put("grams", grams).put("kcal", kcal)
        .put("protein", protein).put("carbs", carbs).put("fat", fat).put("fiber", fiber)

    companion object {
        fun from(o: JSONObject) = FoodItem(
            o.optString("name", "Comida"), o.optDouble("grams", 100.0).orZero(), o.optDouble("kcal", 0.0).orZero(),
            o.optDouble("protein", 0.0).orZero(), o.optDouble("carbs", 0.0).orZero(), o.optDouble("fat", 0.0).orZero(),
            o.optDouble("fiber", 0.0).orZero(),
        )
    }
}

data class Totals(val kcal: Double = 0.0, val protein: Double = 0.0, val carbs: Double = 0.0, val fat: Double = 0.0, val fiber: Double = 0.0) {
    operator fun plus(o: Totals) = Totals(kcal + o.kcal, protein + o.protein, carbs + o.carbs, fat + o.fat, fiber + o.fiber)
}

fun List<FoodItem>.totals() = fold(Totals()) { t, f -> t + Totals(f.kcal, f.protein, f.carbs, f.fat, f.fiber) }

data class Meal(
    val id: String,
    val time: Long,
    val slot: Slot,
    val items: List<FoodItem>,
    val photo: String? = null,
    val note: String = "",
    val hunger: Int? = null,
    val reason: Reason? = null,
    val mindful: Boolean = false,
    val source: String = "texto",
) {
    val totals get() = items.totals()
    val date: LocalDate get() = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()

    fun toJson(): JSONObject = JSONObject().put("id", id).put("time", time).put("slot", slot.name)
        .put("items", JSONArray(items.map { it.toJson() })).put("photo", photo ?: JSONObject.NULL)
        .put("note", note).put("hunger", hunger ?: JSONObject.NULL).put("reason", reason?.name ?: JSONObject.NULL)
        .put("mindful", mindful).put("source", source)

    companion object {
        fun from(o: JSONObject) = Meal(
            id = o.getString("id"),
            time = o.getLong("time"),
            slot = enumOr(o.optString("slot"), Slot.SNACK),
            items = o.optJSONArray("items").objects().map(FoodItem::from),
            photo = if (o.isNull("photo")) null else o.optString("photo"),
            note = o.optString("note"),
            hunger = if (o.isNull("hunger")) null else o.optInt("hunger"),
            reason = if (o.isNull("reason")) null else enumOrNull<Reason>(o.optString("reason")),
            mindful = o.optBoolean("mindful"),
            source = o.optString("source", "texto"),
        )
    }
}

data class WeightEntry(val date: LocalDate, val kg: Double) {
    fun toJson(): JSONObject = JSONObject().put("date", date.toString()).put("kg", kg)

    companion object {
        fun from(o: JSONObject) = WeightEntry(LocalDate.parse(o.getString("date")), o.getDouble("kg"))
    }
}

/** An implementation intention: "Si <cuando>, entonces <haré>" (Gollwitzer, 1999). */
data class IfThenPlan(val id: String, val cue: String, val action: String, val hits: Int = 0) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("cue", cue).put("action", action).put("hits", hits)

    companion object {
        fun from(o: JSONObject) = IfThenPlan(o.getString("id"), o.optString("cue"), o.optString("action"), o.optInt("hits"))
    }
}

data class ChatMessage(val fromUser: Boolean, val text: String, val time: Long = System.currentTimeMillis()) {
    fun toJson(): JSONObject = JSONObject().put("user", fromUser).put("text", text).put("time", time)

    companion object {
        fun from(o: JSONObject) = ChatMessage(o.optBoolean("user"), o.optString("text"), o.optLong("time"))
    }
}

enum class Provider(val label: String) { GEMINI("Google Gemini"), CLAUDE("Anthropic Claude"), NINGUNO("Sin IA (solo libreta)") }

data class Settings(val provider: Provider = Provider.GEMINI, val lock: Boolean = false) {
    fun toJson(): JSONObject = JSONObject().put("provider", provider.name).put("lock", lock)

    companion object {
        fun from(o: JSONObject) = Settings(enumOr(o.optString("provider"), Provider.GEMINI), o.optBoolean("lock"))
    }
}

data class AppState(
    val profile: Profile = Profile(),
    val meals: List<Meal> = emptyList(),
    val weights: List<WeightEntry> = emptyList(),
    val water: Map<LocalDate, Int> = emptyMap(),
    val plans: List<IfThenPlan> = emptyList(),
    val chat: List<ChatMessage> = emptyList(),
    val settings: Settings = Settings(),
    val urgesSurfed: Int = 0,
    val menu: Map<LocalDate, List<PlannedMeal>> = emptyMap(),
    val workouts: Set<LocalDate> = emptySet(),
    val memories: List<MemoryNote> = emptyList(),
    val corrections: List<Correction> = emptyList(),
    val focus: Focus? = null,
) {
    fun mealsOn(day: LocalDate) = meals.filter { it.date == day }.sortedBy { it.time }
    fun totalsOn(day: LocalDate) = mealsOn(day).fold(Totals()) { t, m -> t + m.totals }
    val loggedDays: Set<LocalDate> get() = meals.map { it.date }.toSet()

    fun toJson(): JSONObject = JSONObject()
        .put("v", 1)
        .put("profile", profile.toJson())
        .put("meals", JSONArray(meals.map { it.toJson() }))
        .put("weights", JSONArray(weights.map { it.toJson() }))
        .put("water", JSONObject(water.mapKeys { it.key.toString() }))
        .put("plans", JSONArray(plans.map { it.toJson() }))
        .put("chat", JSONArray(chat.takeLast(80).map { it.toJson() }))
        .put("settings", settings.toJson())
        .put("urgesSurfed", urgesSurfed)
        .put("workouts", JSONArray(workouts.map { it.toString() }))
        .put("memories", JSONArray(memories.map { it.toJson() }))
        .put("corrections", JSONArray(corrections.takeLast(200).map { it.toJson() }))
        .put("focus", focus?.toJson() ?: JSONObject.NULL)
        .put("menu", JSONObject(menu.filterKeys { it >= LocalDate.now().minusDays(1) }.map { (d, ms) -> d.toString() to JSONArray(ms.map { it.toJson() }) }.toMap()))

    companion object {
        fun from(o: JSONObject): AppState {
            val water = o.optJSONObject("water")
            return AppState(
                profile = o.optJSONObject("profile")?.let(Profile::from) ?: Profile(),
                meals = o.optJSONArray("meals").objects().map(Meal::from),
                weights = o.optJSONArray("weights").objects().map(WeightEntry::from).sortedBy { it.date },
                water = water?.keys()?.asSequence()?.associate { LocalDate.parse(it) to water.optInt(it) } ?: emptyMap(),
                plans = o.optJSONArray("plans").objects().map(IfThenPlan::from),
                chat = o.optJSONArray("chat").objects().map(ChatMessage::from),
                settings = o.optJSONObject("settings")?.let(Settings::from) ?: Settings(),
                urgesSurfed = o.optInt("urgesSurfed"),
                workouts = o.optJSONArray("workouts")?.let { a -> (0 until a.length()).map { LocalDate.parse(a.getString(it)) }.toSet() } ?: emptySet(),
                menu = o.optJSONObject("menu")?.let { m -> m.keys().asSequence().associate { LocalDate.parse(it) to m.optJSONArray(it).objects().map(PlannedMeal::from) } } ?: emptyMap(),
                memories = o.optJSONArray("memories").objects().map(MemoryNote::from),
                corrections = o.optJSONArray("corrections").objects().map(Correction::from),
                focus = o.optJSONObject("focus")?.let(Focus::from),
            )
        }
    }
}

fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun Double.orZero() = if (isNaN() || isInfinite() || this < 0) 0.0 else this

inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E = enumOrNull<E>(name) ?: fallback

inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? = enumValues<E>().firstOrNull { it.name == name }

/** One meal of the suggested daily menu (Fitia-style): already sized to the user's targets. */
data class PlannedMeal(val slot: Slot, val title: String, val items: List<FoodItem>, val eaten: Boolean = false) {
    val totals get() = items.totals()

    fun toJson(): JSONObject = JSONObject().put("slot", slot.name).put("title", title)
        .put("items", JSONArray(items.map { it.toJson() })).put("eaten", eaten)

    companion object {
        fun from(o: JSONObject) = PlannedMeal(
            enumOr(o.optString("slot"), Slot.SNACK), o.optString("title"),
            o.optJSONArray("items").objects().map(FoodItem::from), o.optBoolean("eaten"),
        )
    }
}
