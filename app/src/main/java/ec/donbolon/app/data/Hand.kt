package ec.donbolon.app.data

import org.json.JSONObject
import java.util.Locale

/**
 * The person's own hand, measured once on the screen ruler. A hand next to the food is a scale
 * that is always at the table, so the AI can size portions from a photo.
 */
data class Hand(val palmWidthCm: Double, val middleFingerCm: Double, val thumbWidthCm: Double) {

    /** How the AI should read it, in Spanish like the rest of the prompt. */
    fun describe(): String =
        "La mano de la persona mide: palma ${palmWidthCm.cm()} cm de ancho, " +
            "dedo medio ${middleFingerCm.cm()} cm de largo, pulgar ${thumbWidthCm.cm()} cm de ancho."

    fun toJson(): JSONObject = JSONObject().put("palmWidthCm", palmWidthCm)
        .put("middleFingerCm", middleFingerCm).put("thumbWidthCm", thumbWidthCm)

    companion object {
        /**
         * Adult averages, men and women together, used until the person measures their own.
         * - Palm width (across the knuckles, metacarpale II–V): ANSUR II (US Army Anthropometric
         *   Survey 2012, Gordon et al., NSRDEC) medians are about 8.8 cm for men and 7.8 cm for
         *   women, so 8.3 cm.
         * - Middle finger length and thumb width: ANSUR II public data was not checked for these,
         *   so they are approximate textbook figures (about 8 cm and 2.1 cm, in line with
         *   Pheasant, "Bodyspace"); treat them as rough until verified.
         */
        val typical = Hand(palmWidthCm = 8.3, middleFingerCm = 8.0, thumbWidthCm = 2.1)

        fun from(o: JSONObject) = Hand(
            o.optDouble("palmWidthCm", typical.palmWidthCm).sane(typical.palmWidthCm),
            o.optDouble("middleFingerCm", typical.middleFingerCm).sane(typical.middleFingerCm),
            o.optDouble("thumbWidthCm", typical.thumbWidthCm).sane(typical.thumbWidthCm),
        )

        private fun Double.sane(fallback: Double) = if (isNaN() || isInfinite() || this <= 0.0) fallback else this
    }
}

private fun Double.cm() = String.format(Locale.US, "%.1f", this)

/** Screen pixels to real length: the display reports its density in pixels per inch. */
object Ruler {
    const val CM_PER_INCH = 2.54

    fun pxToCm(px: Float, dpi: Float): Double = if (dpi <= 0f) 0.0 else px / dpi.toDouble() * CM_PER_INCH

    fun cmToPx(cm: Double, dpi: Float): Float = (cm / CM_PER_INCH * dpi).toFloat()
}
