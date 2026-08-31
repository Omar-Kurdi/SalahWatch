package com.salahwatch.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The area picked automatically is the one thing a user never sees us decide.
 *
 * Getting it wrong is not a visible failure: another emirate's timetable is off by a few minutes,
 * every value looks reasonable, and the app says nothing. So the boundary is tested from both
 * sides — a location that should match, and one that should fall through to calculation instead of
 * being handed the nearest table regardless of distance.
 */
class OfficialAreaTest {

    @Test
    fun `matches the area a location sits in`() {
        assertEquals(OfficialArea.AL_AIN, OfficialArea.nearestTo(24.2075, 55.7447))
        assertEquals(OfficialArea.DUBAI, OfficialArea.nearestTo(25.2048, 55.2708))
    }

    @Test
    fun `prefers the closer of two areas`() {
        // Sharjah is nearer Dubai than Al Ain, and inside the radius of one of them only.
        assertEquals(OfficialArea.DUBAI, OfficialArea.nearestTo(25.3463, 55.4209))
    }

    @Test
    fun `declines a location no bundled area covers`() {
        assertNull(OfficialArea.nearestTo(51.5074, -0.1278), "London")
        assertNull(OfficialArea.nearestTo(24.4539, 54.3773), "Abu Dhabi city")
    }

    @Test
    fun `every area names a distinct asset`() {
        val slugs = OfficialArea.entries.map { it.slug }
        assertEquals(slugs.size, slugs.distinct().size)
    }
}
