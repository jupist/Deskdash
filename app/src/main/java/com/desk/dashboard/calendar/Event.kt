package com.desk.dashboard.calendar

import java.time.LocalDateTime

/** Where tapping an event goes. */
sealed interface OpenTarget {
    data class Device(val eventId: Long, val begin: Long, val end: Long) : OpenTarget
    data class Link(val url: String) : OpenTarget
    data object None : OpenTarget
}

/** One occurrence of an event, from any source, in local time. */
data class Event(
    val key: String,
    val title: String,
    /** For all-day events: midnight of the first day. */
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
    /** ARGB colour of its calendar, if known. */
    val color: Int?,
    val calendar: String,
    val location: String? = null,
    val open: OpenTarget = OpenTarget.None,
)
