package com.minimal.digitalclock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings store the digit style by its position, so existing choices must keep their meaning. */
class ClockStyleTest {
    @Test
    fun storedStylesKeepTheirMeaning() {
        assertEquals(ClockStyle.MODERN, ClockStyle.entries[0])   // saved by 1.0 – 1.4: Modern
        assertEquals(ClockStyle.SEGMENT, ClockStyle.entries[1])  // saved by 1.2 – 1.4: 7-segment (thin)
        assertEquals(SegmentLook.THIN, ClockStyle.SEGMENT.segmentLook)
    }

    @Test
    fun segmentStyles() {
        assertFalse(ClockStyle.MODERN.isSegment)
        assertTrue(ClockStyle.SEGMENT_THICK.isSegment)
        assertTrue(ClockStyle.SEGMENT_LED.segmentLook!!.led)
        assertTrue(SegmentLook.THICK.thickness > SegmentLook.THIN.thickness)
    }
}
