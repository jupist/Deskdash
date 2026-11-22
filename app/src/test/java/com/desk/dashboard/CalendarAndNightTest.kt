package com.desk.dashboard

import biweekly.Biweekly
import com.desk.dashboard.calendar.IcsFeed
import com.desk.dashboard.calendar.IcsFeeds
import com.desk.dashboard.data.DisplayConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class CalendarAndNightTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val feed = IcsFeed("f", "Test")

    private val ics = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//test//EN
        BEGIN:VEVENT
        UID:weekly
        SUMMARY:Lecture
        DTSTART;TZID=Asia/Kolkata:20261102T100000
        DTEND;TZID=Asia/Kolkata:20261102T113000
        RRULE:FREQ=WEEKLY;BYDAY=MO,WE
        EXDATE;TZID=Asia/Kolkata:20261125T100000
        END:VEVENT
        BEGIN:VEVENT
        UID:allday
        SUMMARY:Exam week
        DTSTART;VALUE=DATE:20261123
        DTEND;VALUE=DATE:20261126
        END:VEVENT
        BEGIN:VEVENT
        UID:past
        SUMMARY:Old
        DTSTART:20250101T090000Z
        DTEND:20250101T100000Z
        END:VEVENT
        END:VCALENDAR
    """.trimIndent().replace("\n", "\r\n")

    private fun events(today: LocalDate, days: Int, now: LocalDateTime) =
        IcsFeeds.occurrences(Biweekly.parse(ics).all(), feed, today, today.plusDays(days - 1L), now, zone)

    @Test fun weeklyRuleExpandsInEventTimeZoneAndHonoursExdate() {
        val today = LocalDate.of(2026, 11, 22) // a Sunday
        val ev = events(today, 10, today.atTime(8, 0)).filter { it.title == "Lecture" }
        // Mondays 23 and 30 at 10:00; Wednesday 25 is excluded by EXDATE; Wednesday 2 Dec is past the window.
        assertEquals(listOf(LocalDateTime.of(2026, 11, 23, 10, 0), LocalDateTime.of(2026, 11, 30, 10, 0)), ev.map { it.start }.sorted())
        assertTrue(ev.none { it.start.dayOfMonth == 25 })
        assertEquals(LocalTime.of(11, 30), ev.first().end.toLocalTime())
    }

    @Test fun allDaySpanShowsWhileItLastsAndPastEventsDont() {
        val midSpan = LocalDate.of(2026, 11, 24)
        val ev = events(midSpan, 1, midSpan.atTime(12, 0))
        assertTrue(ev.any { it.title == "Exam week" && it.allDay })
        assertFalse(ev.any { it.title == "Old" })
        val after = LocalDate.of(2026, 11, 26)
        assertFalse(events(after, 1, after.atTime(9, 0)).any { it.title == "Exam week" })
    }

    @Test fun finishedTimedOccurrenceIsHidden() {
        val mon = LocalDate.of(2026, 11, 23)
        assertTrue(events(mon, 1, mon.atTime(9, 0)).any { it.title == "Lecture" })
        assertFalse(events(mon, 1, mon.atTime(12, 0)).any { it.title == "Lecture" })
    }

    @Test fun nightWindowWrapsPastMidnight() {
        val c = DisplayConfig(nightEnabled = true, nightStart = LocalTime.of(23, 0), nightEnd = LocalTime.of(7, 0))
        assertTrue(c.isNight(LocalTime.of(23, 30)))
        assertTrue(c.isNight(LocalTime.of(3, 0)))
        assertFalse(c.isNight(LocalTime.of(7, 0)))
        assertFalse(c.isNight(LocalTime.of(12, 0)))
        assertFalse(c.copy(nightEnabled = false).isNight(LocalTime.of(3, 0)))
        val day = DisplayConfig(nightStart = LocalTime.of(13, 0), nightEnd = LocalTime.of(14, 0))
        assertTrue(day.isNight(LocalTime.of(13, 30)))
        assertFalse(day.isNight(LocalTime.of(14, 30)))
    }
}
