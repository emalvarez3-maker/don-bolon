package ec.donbolon.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ec.donbolon.app.data.AppState
import ec.donbolon.app.data.Fast
import ec.donbolon.app.data.fasting
import ec.donbolon.app.data.Nutrition
import ec.donbolon.app.data.Store
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Water and fasting nudges. One inexact alarm at a time (no exact-alarm permission needed):
 * when it fires, the receiver decides whether a nudge is actually useful and books the next.
 * Water is only nudged when behind pace, because reminders that fire when not needed train
 * people to ignore them.
 */
object Reminders {
    private const val CHANNEL = "recordatorios"
    private const val WATER_ID = 41
    private const val FAST_ID = 42
    private val waterHours = listOf(9, 11, 13, 15, 17, 19, 21)

    enum class Kind { WATER, FAST_OPEN, FAST_CLOSING }

    /** The next moment worth checking, across water and fasting, after [now]. */
    fun next(s: AppState, now: LocalDateTime): Pair<LocalDateTime, Kind>? {
        val out = mutableListOf<Pair<LocalDateTime, Kind>>()
        for (day in listOf(now.toLocalDate(), now.toLocalDate().plusDays(1))) {
            if (s.settings.waterReminders) waterHours.forEach { out += day.atTime(it, 0) to Kind.WATER }
            if (s.settings.fastReminders && s.profile.fasting) {
                out += day.atTime(Fast.opens(s.profile)) to Kind.FAST_OPEN
                out += day.atTime(Fast.closes(s.profile)).minusHours(1) to Kind.FAST_CLOSING
            }
        }
        return out.filter { it.first.isAfter(now) }.minByOrNull { it.first }
    }

    fun schedule(context: Context) {
        val s = Store.get(context).state.value
        val am = context.getSystemService(AlarmManager::class.java)
        val (at, kind) = next(s, LocalDateTime.now()) ?: run {
            am.cancel(tick(context, Kind.WATER)); return
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), tick(context, kind))
    }

    private fun tick(context: Context, kind: Kind) = PendingIntent.getBroadcast(
        context, 1, Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.TICK).putExtra("kind", kind.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Water drunk by now should follow the waking day; nudge only when clearly behind. */
    fun waterBehind(s: AppState, now: LocalDateTime): Pair<Int, Int>? {
        val goal = Nutrition.targets(s.profile).waterGlasses
        val drunk = s.water[now.toLocalDate()] ?: 0
        val expected = (goal * ((now.hour - 8).coerceIn(0, 13) / 13.0)).toInt()
        return if (drunk < expected && drunk < goal) drunk to goal else null
    }

    fun fire(context: Context, kind: Kind) {
        val s = Store.get(context).state.value
        val now = LocalDateTime.now()
        when (kind) {
            Kind.WATER -> waterBehind(s, now)?.let { (drunk, goal) ->
                val lines = listOf(
                    "Grrr, llevas $drunk de $goal vasos. Uno ahora, sin pensarlo.",
                    "A veces el \"hambre\" es sed. Toma un vaso y vuelve a preguntarte.",
                    "Hmpf. Agua. Ahora. Vas $drunk de $goal.",
                )
                notify(context, WATER_ID, "💧 Hora de un vaso de agua", lines[now.hour % lines.size], drinkAction = true)
            }
            Kind.FAST_OPEN -> notify(
                context, FAST_ID, "🍽️ Tu ventana está abierta",
                "Rompe el ayuno con proteína y verduras. Tómale foto a tu plato.",
            )
            Kind.FAST_CLOSING -> {
                val t = Nutrition.targets(s.profile)
                val missing = (t.protein - s.totalsOn(LocalDate.now()).protein).toInt()
                notify(
                    context, FAST_ID, "⏳ Tu ventana cierra en 1 hora",
                    if (missing > 20) "Te faltan $missing g de proteína: que la última comida sea fuerte en proteína." else "Vas bien. Si comes algo más, que sea con calma.",
                )
            }
        }
    }

    private fun notify(context: Context, id: Int, title: String, text: String, drinkAction: Boolean = false) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Recordatorios", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Agua y ventana de ayuno"
        })
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bolon)
            .setColor(0xFF9CC285.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
        if (drinkAction) {
            val drink = PendingIntent.getBroadcast(
                context, 2, Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.DRINK),
                PendingIntent.FLAG_IMMUTABLE,
            )
            n.addAction(0, "Tomé uno", drink)
        }
        NotificationManagerCompat.from(context).notify(id, n.build())
    }

    fun dismissWater(context: Context) = NotificationManagerCompat.from(context).cancel(WATER_ID)
}

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        const val TICK = "ec.donbolon.app.TICK"
        const val DRINK = "ec.donbolon.app.DRINK"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TICK -> {
                val kind = runCatching { Reminders.Kind.valueOf(intent.getStringExtra("kind").orEmpty()) }.getOrNull()
                if (kind != null) Reminders.fire(context, kind)
                Reminders.schedule(context)
            }
            DRINK -> {
                val done = goAsync()
                Thread {
                    val store = Store.get(context)
                    store.addWater(LocalDate.now(), 1)
                    store.saveNow()
                    Reminders.dismissWater(context)
                    done.finish()
                }.start()
            }
            // After a reboot or an update the alarm is gone: book it again.
            else -> Reminders.schedule(context)
        }
    }
}
