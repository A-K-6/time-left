package dev.calendarquiet.core

import org.junit.Assert.*
import org.junit.Test

class CountdownDisplayTest {
    @Test fun nativeClockBaseRoundsUpWithoutChangingTheRealDeadline() {
        assertEquals(10_000 + 1_500 + 999L, countdownChronometerBase(1_500, 10_000))
        assertEquals(10_000 + 1 + 999L, countdownChronometerBase(1, 10_000))
        assertEquals(10_000 + 999L, countdownChronometerBase(-1, 10_000))
    }
    @Test fun displayUpdatesAtTheNextRoundedSecondInsteadOfDrifting() {
        assertEquals(500L, nextCountdownUpdate(1_500))
        assertEquals(1000L, nextCountdownUpdate(2_000))
        assertEquals(1L, nextCountdownUpdate(1_001))
        assertEquals(0L, nextCountdownUpdate(0))
    }
    @Test fun android16AlignedTicksDoNotLagByASecond() {
        val deadline = 30_000L
        val base = countdownChronometerBase(deadline, 0, modernRounding = true)
        // Android 16 schedules these ticks from its base, then rounds (remaining - 499).
        for (tick in 1..5) {
            val now = base - tick * 1000 + 1
            val nativeSeconds = Math.round((base - now - 499) / 1000f).toLong()
            val expectedSeconds = (deadline - now + 999) / 1000
            assertEquals(expectedSeconds, nativeSeconds)
        }
    }

}
