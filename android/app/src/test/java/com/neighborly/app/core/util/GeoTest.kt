package com.neighborly.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    @Test fun `distance between two nearby points`() {
        val a = LatLng(23.8768, 90.3209)
        val b = LatLng(23.8858, 90.3209) // ~1 km north
        val d = Geo.distanceKm(a, b)!!
        assertEquals(1.0, d, 0.02)
        assertNull(Geo.distanceKm(a, null))
    }

    @Test fun `formatDistance matches the web`() {
        assertEquals("350 m away", Geo.formatDistance(0.35))
        assertEquals("2.5 km away", Geo.formatDistance(2.5))
        assertEquals("12 km away", Geo.formatDistance(12.2))
        assertEquals("", Geo.formatDistance(null))
    }

    @Test fun `bounding box contains the radius`() {
        val c = Geo.DEFAULT_CENTER
        val box = Geo.boundingBox(c, 3.0)
        assertTrue(box.minLat < c.lat && c.lat < box.maxLat)
        assertTrue(box.minLng < c.lng && c.lng < box.maxLng)
        assertEquals(3.0 / 111.0, box.maxLat - c.lat, 1e-9)
    }
}
