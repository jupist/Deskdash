package com.desk.dashboard.calendar

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Google Tasks for the Checklist tile: one task list, read and ticked off over the Tasks REST
 * API. Uses the same Google sign-in as Google Calendar, asking for the Tasks permission
 * separately (the Google Cloud project also needs the Tasks API turned on; see
 * docs/GOOGLE_CALENDAR.md). Changes made anywhere else (the Tasks app, a phone) show up on the
 * next refresh.
 */
object GoogleTasks {
    const val SCOPE = "https://www.googleapis.com/auth/tasks"
    private const val API = "https://tasks.googleapis.com/tasks/v1"
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    data class TaskList(val id: String, val title: String)
    data class Task(val id: String, val title: String, val done: Boolean)
    data class ListState(val tasks: List<Task>? = null, val error: String? = null)

    private val _lists = MutableStateFlow<Map<String, ListState>>(emptyMap())
    /** Last known tasks per list id. */
    val lists: StateFlow<Map<String, ListState>> = _lists

    /** A token without showing anything; null with a reason when the user must connect first. */
    private suspend fun token(c: Context): Pair<String?, String?> = when (val a = GoogleCalendar.authorize(c, SCOPE)) {
        is GoogleCalendar.Auth.Token -> a.token to null
        is GoogleCalendar.Auth.NeedsConsent -> null to "Connect Google Tasks in this tile's settings"
        is GoogleCalendar.Auth.Failed -> null to a.message
    }

    private suspend fun call(token: String, url: String, method: String = "GET", body: JsonObject? = null): JsonObject? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url.toHttpUrl()).header("Authorization", "Bearer $token")
            .method(method, body?.toString()?.toRequestBody("application/json".toMediaType()) ?: if (method == "POST") ByteArray(0).toRequestBody() else null)
            .build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 401) error("Google sign-in expired; reconnect in the tile's settings")
            if (!r.isSuccessful) {
                val why = runCatching {
                    json.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                error("Google Tasks error ${r.code}" + (why?.let { ": $it" } ?: ""))
            }
            if (text.isBlank()) null else json.parseToJsonElement(text).jsonObject
        }
    }

    /** The account's task lists, for choosing one. Call with a token from [GoogleCalendar.authorize]. */
    suspend fun taskLists(token: String): List<TaskList> =
        call(token, "$API/users/@me/lists?maxResults=100")?.get("items")?.jsonArray.orEmpty().map {
            val o = it.jsonObject
            TaskList(o["id"]!!.jsonPrimitive.content, o["title"]?.jsonPrimitive?.contentOrNull ?: "Tasks")
        }

    suspend fun refresh(c: Context, listId: String) {
        val (token, why) = token(c)
        if (token == null) return set(listId) { it.copy(error = why) }
        runCatching {
            call(token, "$API/lists/$listId/tasks?showCompleted=true&showHidden=true&maxResults=100")?.get("items")?.jsonArray.orEmpty()
                .map { it.jsonObject }
                .filter { (it["title"]?.jsonPrimitive?.contentOrNull).orEmpty().isNotBlank() && it["deleted"]?.jsonPrimitive?.contentOrNull != "true" }
                // Top-level tasks in the list's order, each followed by its subtasks.
                .let { all ->
                    fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
                    val byPos = compareBy<JsonObject> { it.str("position") ?: "" }
                    all.filter { it.str("parent") == null }.sortedWith(byPos).flatMap { t ->
                        listOf(t) + all.filter { it.str("parent") == t.str("id") }.sortedWith(byPos)
                    }
                }
                .map { Task(it["id"]!!.jsonPrimitive.content, it["title"]!!.jsonPrimitive.content, it["status"]?.jsonPrimitive?.contentOrNull == "completed") }
        }.onSuccess { tasks -> set(listId) { ListState(tasks) } }
            .onFailure { e -> set(listId) { it.copy(error = e.message ?: "Couldn't load Google Tasks") } }
    }

    /** Ticks or unticks a task: shown straight away, then saved to Google. */
    suspend fun setDone(c: Context, listId: String, taskId: String, done: Boolean) {
        set(listId) { s -> s.copy(tasks = s.tasks?.map { if (it.id == taskId) it.copy(done = done) else it }) }
        mutate(c, listId) { token ->
            val body = JsonObject(
                if (done) mapOf("status" to JsonPrimitive("completed"))
                else mapOf("status" to JsonPrimitive("needsAction"), "completed" to JsonNull)
            )
            call(token, "$API/lists/$listId/tasks/$taskId", "PATCH", body)
        }
    }

    suspend fun add(c: Context, listId: String, title: String) = mutate(c, listId) { token ->
        call(token, "$API/lists/$listId/tasks", "POST", JsonObject(mapOf("title" to JsonPrimitive(title))))
    }

    /** Hides finished tasks from the list (Google's "clear completed"). */
    suspend fun clearDone(c: Context, listId: String) = mutate(c, listId) { token ->
        call(token, "$API/lists/$listId/clear", "POST")
    }

    private suspend fun mutate(c: Context, listId: String, block: suspend (String) -> Unit) {
        val (token, why) = token(c)
        if (token == null) return set(listId) { it.copy(error = why) }
        runCatching { block(token) }.onFailure { e -> set(listId) { it.copy(error = e.message) } }
        refresh(c, listId)
    }

    private fun set(listId: String, block: (ListState) -> ListState) =
        _lists.update { it + (listId to block(it[listId] ?: ListState())) }
}
