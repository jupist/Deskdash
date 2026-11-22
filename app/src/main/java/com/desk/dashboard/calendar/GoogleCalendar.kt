package com.desk.dashboard.calendar

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

@Serializable
data class GoogleCal(val id: String, val name: String, val color: Int, val selected: Boolean, val primary: Boolean = false)

@Serializable
data class GoogleAccount(val email: String, val calendars: List<GoogleCal> = emptyList(), val lastSync: Long = 0, val error: String? = null)

/**
 * Google Calendar over its REST API with a read-only token from Google Play services. Needs an
 * OAuth client for this app in a Google Cloud project (see docs/GOOGLE_CALENDAR.md); without one,
 * sign-in fails with a "developer error", which we explain to the user.
 */
object GoogleCalendar {
    private const val SCOPE = "https://www.googleapis.com/auth/calendar.readonly"
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    private fun request() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    sealed interface Auth {
        data class Token(val token: String) : Auth
        /** The user has to approve access: launch this, then call [fromIntent] with the result. */
        data class NeedsConsent(val intent: PendingIntent) : Auth
        data class Failed(val message: String) : Auth
    }

    /** Asks for a token; shows no UI by itself (returns [Auth.NeedsConsent] when approval is needed). */
    suspend fun authorize(context: Context): Auth = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(context).authorize(request())
            .addOnSuccessListener { r -> cont.resume(result(r)) }
            .addOnFailureListener { e -> cont.resume(Auth.Failed(explain(e))) }
    }

    fun fromIntent(context: Context, data: Intent?): Auth = runCatching {
        result(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data))
    }.getOrElse { Auth.Failed(explain(it)) }

    private fun result(r: AuthorizationResult): Auth = when {
        r.hasResolution() -> r.pendingIntent?.let { Auth.NeedsConsent(it) } ?: Auth.Failed("Google didn't return a sign-in screen")
        r.accessToken != null -> Auth.Token(r.accessToken!!)
        else -> Auth.Failed("Google returned no access token")
    }

    private fun explain(e: Throwable): String {
        val code = (e as? ApiException)?.statusCode
        return when (code) {
            10 -> "This build isn't registered with Google yet (developer error). See docs/GOOGLE_CALENDAR.md, or use an iCal link instead."
            7 -> "No internet connection"
            12501, 16 -> "Sign-in was cancelled"
            else -> e.message ?: "Google sign-in failed"
        }
    }

    private suspend fun get(token: String, url: okhttp3.HttpUrl): JsonObject = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build()).execute().use { r ->
            if (r.code == 401) error("Google sign-in expired; reconnect in Settings")
            if (!r.isSuccessful) error("Google Calendar error ${r.code}")
            json.parseToJsonElement(r.body!!.string()).jsonObject
        }
    }

    /** The signed-in account's calendars (keeping earlier selections; primary on by default). */
    suspend fun account(token: String, previous: GoogleAccount?): GoogleAccount {
        val list = get(token, "https://www.googleapis.com/calendar/v3/users/me/calendarList".toHttpUrl())
        val items = list["items"]?.jsonArray.orEmpty().map { it.jsonObject }
        var email = previous?.email ?: ""
        val cals = items.map { o ->
            val id = o["id"]!!.jsonPrimitive.content
            val primary = o["primary"]?.jsonPrimitive?.contentOrNull == "true"
            if (primary) email = id
            val color = o["backgroundColor"]?.jsonPrimitive?.contentOrNull?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() }
                ?: 0xFF4E6187.toInt()
            val was = previous?.calendars?.firstOrNull { it.id == id }
            GoogleCal(id, o["summaryOverride"]?.jsonPrimitive?.contentOrNull ?: o["summary"]?.jsonPrimitive?.contentOrNull ?: id, color, was?.selected ?: primary, primary)
        }.sortedWith(compareBy({ !it.primary }, { it.name }))
        return GoogleAccount(email.ifBlank { "Google account" }, cals, System.currentTimeMillis())
    }

    /** Upcoming events from the selected calendars, through the end of [lastDay]. */
    suspend fun events(token: String, account: GoogleAccount, today: LocalDate, lastDay: LocalDate): List<Event> {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        val from = today.atStartOfDay(zone).toOffsetDateTime().toString()
        val to = lastDay.plusDays(1).atStartOfDay(zone).toOffsetDateTime().toString()
        val out = mutableListOf<Event>()
        for (cal in account.calendars.filter { it.selected }) {
            val url = "https://www.googleapis.com/calendar/v3/calendars/${java.net.URLEncoder.encode(cal.id, "UTF-8")}/events".toHttpUrl().newBuilder()
                .addQueryParameter("timeMin", from).addQueryParameter("timeMax", to)
                .addQueryParameter("singleEvents", "true").addQueryParameter("orderBy", "startTime")
                .addQueryParameter("maxResults", "100").build()
            val items = get(token, url)["items"]?.jsonArray.orEmpty().map { it.jsonObject }
            for (o in items) {
                if (o["status"]?.jsonPrimitive?.contentOrNull == "cancelled") continue
                val s = o["start"]?.jsonObject ?: continue
                val e = o["end"]?.jsonObject ?: continue
                val allDay = s["date"] != null
                val start = if (allDay) LocalDate.parse(s["date"]!!.jsonPrimitive.content).atStartOfDay()
                else OffsetDateTime.parse(s["dateTime"]!!.jsonPrimitive.content).atZoneSameInstant(zone).toLocalDateTime()
                val end = if (allDay) LocalDate.parse(e["date"]!!.jsonPrimitive.content).atStartOfDay()
                else OffsetDateTime.parse(e["dateTime"]!!.jsonPrimitive.content).atZoneSameInstant(zone).toLocalDateTime()
                if (!allDay && !end.isAfter(now)) continue
                out += Event(
                    key = "google:${cal.id}:${o["id"]?.jsonPrimitive?.content}",
                    title = o["summary"]?.jsonPrimitive?.contentOrNull?.ifBlank { null } ?: "(No title)",
                    start = start, end = end, allDay = allDay, color = cal.color, calendar = cal.name,
                    location = o["location"]?.jsonPrimitive?.contentOrNull,
                    open = o["htmlLink"]?.jsonPrimitive?.contentOrNull?.let { OpenTarget.Link(it) } ?: OpenTarget.None,
                )
            }
        }
        return out
    }
}
