package ec.donbolon.app.data

import ec.donbolon.app.Reminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class FastingTest {
    private val p168 = Profile(fasting = Fasting.F16, windowStart = 12)

    @Test fun sixteenEightWindowIsNoonToEight() {
        assertTrue(Fast.inWindow(p168, LocalTime.of(12, 0)))
        assertTrue(Fast.inWindow(p168, LocalTime.of(19, 59)))
        assertFalse(Fast.inWindow(p168, LocalTime.of(20, 0)))
        assertFalse(Fast.inWindow(p168, LocalTime.of(8, 0)))
    }

    @Test fun windowCanCrossMidnight() {
        val p = Profile(fasting = Fasting.F16, windowStart = 20) // 20:00 → 04:00
        assertTrue(Fast.inWindow(p, LocalTime.of(23, 0)))
        assertTrue(Fast.inWindow(p, LocalTime.of(2, 0)))
        assertFalse(Fast.inWindow(p, LocalTime.of(12, 0)))
    }

    @Test fun noFastingMeansAlwaysOpen() {
        assertTrue(Fast.inWindow(Profile(), LocalTime.of(3, 0)))
        assertNull(Fast.state(AppState()))
    }

    @Test fun mealsOutsideTheWindowAreNotSuggested() {
        assertEquals(listOf(Slot.ALMUERZO, Slot.SNACK), Fast.slots(p168).filter { it != Slot.CENA }.let { it })
        assertFalse(Slot.DESAYUNO in Fast.slots(p168))
        assertTrue(Slot.CENA !in Fast.slots(p168) || Fast.inWindow(p168, LocalTime.of(20, 0)))
    }

    @Test fun fastTimerCountsFromTheLastMeal() {
        val day = LocalDate.of(2026, 10, 22)
        val dinner = Meal("d", day.minusDays(1).atTime(19, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), Slot.CENA,
            listOf(FoodItem("Arroz", 100.0, 130.0, 3.0, 28.0, 0.3)))
        val f = Fast.state(AppState(profile = p168, meals = listOf(dinner)), day.atTime(10, 30))
        assertNotNull(f)
        assertFalse(f!!.eating)
        assertEquals(15.0, f.hoursFasted, 0.01)
        assertEquals(90, f.minutesToChange)
    }

    @Test fun eatingWindowCountsDownToClose() {
        val f = Fast.state(AppState(profile = p168), LocalDate.of(2026, 10, 22).atTime(18, 15))!!
        assertTrue(f.eating)
        assertEquals(105, f.minutesToChange)
    }
}

class ReminderTest {
    private val now = LocalDateTime.of(2026, 10, 22, 10, 5)

    @Test fun nextReminderIsTheEarliestUpcoming() {
        val s = AppState(profile = Profile(fasting = Fasting.F16, windowStart = 12))
        val (at, kind) = Reminders.next(s, now)!!
        assertEquals(LocalDateTime.of(2026, 10, 22, 11, 0), at)
        assertEquals(Reminders.Kind.WATER, kind)
    }

    @Test fun fastingRemindersFollowTheWindow() {
        val s = AppState(profile = Profile(fasting = Fasting.F16, windowStart = 12), settings = Settings(waterReminders = false))
        assertEquals(LocalDateTime.of(2026, 10, 22, 12, 0) to Reminders.Kind.FAST_OPEN, Reminders.next(s, now))
        assertEquals(LocalDateTime.of(2026, 10, 22, 19, 0) to Reminders.Kind.FAST_CLOSING, Reminders.next(s, LocalDateTime.of(2026, 10, 22, 12, 30)))
    }

    @Test fun nothingScheduledWhenAllOff() {
        assertNull(Reminders.next(AppState(settings = Settings(waterReminders = false, fastReminders = false)), now))
    }

    @Test fun waterNudgeOnlyWhenBehind() {
        val p = Profile(weightKg = 80.0)
        val behind = AppState(profile = p, water = mapOf(now.toLocalDate() to 0))
        val onPace = AppState(profile = p, water = mapOf(now.toLocalDate() to 5))
        assertNotNull(Reminders.waterBehind(behind, now.withHour(15)))
        assertNull(Reminders.waterBehind(onPace, now.withHour(13)))
    }
}
