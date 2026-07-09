package com.example.ta33

import com.example.ta33.core.TimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeFormatterTest {

    @Test
    fun zero_isMmSs() {
        assertEquals("00:00", TimeFormatter.format(0L))
    }

    @Test
    fun underAnHour_isMmSs() {
        assertEquals("01:05", TimeFormatter.format(65_000L))
    }

    @Test
    fun exactlyOneHour_isHMmSs() {
        assertEquals("1:00:00", TimeFormatter.format(3_600_000L))
    }

    @Test
    fun overAnHour_isHMmSs() {
        assertEquals("1:01:01", TimeFormatter.format(3_661_000L))
    }

    @Test
    fun negative_coercedToZero() {
        assertEquals("00:00", TimeFormatter.format(-5_000L))
    }
}
