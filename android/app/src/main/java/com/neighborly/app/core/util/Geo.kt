package com.neighborly.app.core.util

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lng: Double)

data class BoundingBox(val minLat: Double, val maxLat: Double, val minLng: Double, val maxLng: Double)

/** Ports of src/lib/geo.js. */
object Geo {
    /** Daffodil Smart City / Ashulia, Dhaka — same default centre as the web map. */
    val DEFAULT_CENTER = LatLng(23.8768, 90.3209)
    val RADII_KM = listOf(1, 3, 5, 10)

    fun distanceKm(a: LatLng?, b: LatLng?): Double? {
        if (a == null || b == null) return null
        val r = 6371.0
        fun rad(x: Double) = Math.toRadians(x)
        val dLat = rad(b.lat - a.lat)
        val dLon = rad(b.lng - a.lng)
        val h = sin(dLat / 2).let { it * it } +
            cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * r * asin(sqrt(h))
    }

    fun formatDistance(km: Double?): String {
        if (km == null) return ""
        if (km < 1) return "${(km * 1000).roundToInt()} m away"
        return (if (km < 10) "%.1f km away" else "%.0f km away").format(Locale.US, km)
    }

    fun boundingBox(center: LatLng, km: Double): BoundingBox {
        val dLat = km / 111.0
        val dLng = km / (111.0 * cos(Math.toRadians(center.lat)))
        return BoundingBox(center.lat - dLat, center.lat + dLat, center.lng - dLng, center.lng + dLng)
    }
}
