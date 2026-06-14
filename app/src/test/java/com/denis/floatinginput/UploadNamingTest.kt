package com.denis.floatinginput

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class UploadNamingTest {

    private fun dateOf(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int): Date {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.clear()
        cal.set(y, mo - 1, d, h, mi, s)
        return cal.time
    }

    @Test
    fun sanitize_replaces_spaces_and_cyrillic() {
        assertEquals("IMG_2026_.jpg", UploadNaming.sanitize("IMG 2026 фото.jpg"))
    }

    @Test
    fun sanitize_keeps_safe_chars() {
        assertEquals("Report-01_v2.pdf", UploadNaming.sanitize("Report-01_v2.pdf"))
    }

    @Test
    fun sanitize_collapses_and_trims_underscores() {
        assertEquals("a_b", UploadNaming.sanitize("  a   b  "))
    }

    @Test
    fun sanitize_empty_becomes_file() {
        assertEquals("file", UploadNaming.sanitize("???"))
    }

    @Test
    fun uniqueName_has_timestamp_prefix() {
        val d = dateOf(2026, 6, 14, 9, 5, 3)
        assertEquals("20260614-090503-photo.jpg",
            UploadNaming.uniqueName("photo.jpg", d))
    }

    @Test
    fun remotePath_uses_home_prefix() {
        assertEquals(
            "/home/denis/truenas/workshop/claude-inbox/20260614-090503-photo.jpg",
            UploadNaming.remotePath("20260614-090503-photo.jpg")
        )
    }
}
