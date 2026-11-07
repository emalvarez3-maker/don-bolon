package ec.donbolon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyTest {
    @Test fun losingWeightCountsProgressAndMilestones() {
        val j = journey(80.0, 77.6, 74.0)!!
        assertEquals(0.4, j.fraction, 1e-9)
        assertEquals(2.4, j.lostKg, 1e-9)
        assertEquals(3.6, j.leftKg, 1e-9)
        assertFalse(j.gaining)
        assertEquals(listOf(79.0, 78.0, 77.0, 76.0, 75.0, 74.0), j.milestones.map { it.kg })
        assertEquals(listOf(true, true, false, false, false, false), j.milestones.map { it.reached })
    }

    @Test fun gainingWeightGoesUp() {
        val j = journey(60.0, 61.5, 64.0)!!
        assertTrue(j.gaining)
        assertEquals(0.375, j.fraction, 1e-9)
        assertEquals(1.5, j.lostKg, 1e-9)
        assertEquals(2.5, j.leftKg, 1e-9)
        assertEquals(listOf(61.0, 62.0, 63.0, 64.0), j.milestones.map { it.kg })
        assertEquals(listOf(true, false, false, false), j.milestones.map { it.reached })
    }

    @Test fun overshootingClampsToOne() {
        val j = journey(80.0, 72.0, 74.0)!!
        assertEquals(1.0, j.fraction, 1e-9)
        assertEquals(0.0, j.leftKg, 1e-9)
        assertEquals(8.0, j.lostKg, 1e-9)
        assertTrue(j.milestones.all { it.reached })
    }

    @Test fun wrongWayClampsToZero() {
        val j = journey(80.0, 81.0, 74.0)!!
        assertEquals(0.0, j.fraction, 1e-9)
        assertEquals(0.0, j.lostKg, 1e-9)
        assertEquals(7.0, j.leftKg, 1e-9)
        assertTrue(j.milestones.none { it.reached })
    }

    @Test fun unevenDistanceEndsOnTheGoal() {
        val j = journey(80.0, 80.0, 77.5)!!
        assertEquals(listOf(79.0, 78.0, 77.5), j.milestones.map { it.kg })
    }

    @Test fun longRoadsWidenTheStep() {
        val j = journey(120.0, 110.0, 80.0)!! // 40 kg: 1 kg steps would be 40 stops
        assertTrue(j.milestones.size <= MAX_MILESTONES)
        assertEquals(80.0, j.milestones.last().kg, 1e-9)
        val gap = j.milestones[0].kg - j.milestones[1].kg
        assertEquals(3.5, gap, 1e-9) // 40 / 12 = 3.33, rounded up to 3.5
        assertEquals(listOf(116.5, 113.0), j.milestones.filter { it.reached }.map { it.kg })
    }

    @Test fun exactlyTwelveStepsFits() {
        val j = journey(80.0, 80.0, 68.0)!!
        assertEquals(12, j.milestones.size)
    }

    @Test fun sameStartAndGoalIsNull() {
        assertNull(journey(70.0, 72.0, 70.0))
    }
}
