package com.neighborly.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class FormatTest {
    private val now = Instant.parse("2026-11-15T12:00:00Z")

    @Test fun `timeAgo matches the web buckets`() {
        assertEquals("Just now", Format.timeAgo("2026-11-15T11:59:30+00:00", now))
        assertEquals("5m", Format.timeAgo("2026-11-15T11:55:00+00:00", now))
        assertEquals("3h", Format.timeAgo("2026-11-15T09:00:00Z", now))
        assertEquals("2d", Format.timeAgo("2026-11-13T11:59:00.123456+00:00", now))
        assertEquals("1d", Format.timeAgo("2026-11-13T12:00:00.123456+00:00", now))
        assertEquals("", Format.timeAgo(null, now))
    }

    @Test fun `price labels follow formatPrice`() {
        assertEquals("Free", Format.price(500.0, "free"))
        assertEquals("Exchange", Format.price(null, "exchange"))
        assertEquals("Wanted", Format.price(null, "buy"))
        assertEquals("Ask price", Format.price(null, "sell"))
        assertEquals("৳1,25,000", Format.price(125000.0, "sell"))
        assertEquals("৳4,500", Format.price(4500.0, "sell"))
        assertEquals("৳12,34,567.5", Format.price(1234567.5, "sell"))
        assertEquals("৳99.05", Format.price(99.05, "sell"))
    }

    @Test fun `compact numbers`() {
        assertEquals("999", Format.compact(999))
        assertEquals("1.5K", Format.compact(1500))
        assertEquals("12K", Format.compact(12000))
        assertEquals("2M", Format.compact(2_000_000))
    }

    @Test fun `initials and labelize`() {
        assertEquals("RU", Format.initials("Rahim Uddin Khan"))
        assertEquals("?", Format.initials(""))
        assertEquals("Like New", Format.labelize("like_new"))
        assertEquals("Rahim", Format.firstName("Rahim Uddin"))
    }

    @Test fun `cleanSearch strips PostgREST filter characters like clean() in api js`() {
        assertEquals("a  b  c", Format.cleanSearch("a,(b)%c"))
        assertEquals("x", Format.cleanSearch("  x*  "))
        assertEquals("", Format.cleanSearch(null))
    }
}
