package com.desk.dashboard.tasks

import android.content.Context
import com.desk.dashboard.calendar.GoogleCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Google Tasks over its REST API. Uses the same Google sign-in as Google Calendar, asking for
 * the Tasks permission separately (the Google Cloud project also needs the Tasks API turned on;
 * see docs/GOOGLE_CALENDAR.md). Connected in Settings › Tasks.
 */
object GoogleTasks : TaskService {
    const val SCOPE = "https://www.googleapis.com/auth/tasks"
    private const val API = "https://tasks.googleapis.com/tasks/v1"

    override val id = "google"
    override val name = "Google Tasks"
    override val clearsDone = true

    override fun connected(c: Context) = Tasks.accounts(c).getBoolean("google", false)

    /** Called once access is granted (Settings › Tasks), or to disconnect. */
    fun setConnected(c: Context, on: Boolean) = Tasks.accounts(c).edit().putBoolean("google", on).apply()

    /** A token without showing anything; asks the user to reconnect when access was withdrawn. */
    private suspend fun token(c: Context): String = when (val a = GoogleCalendar.authorize(c, SCOPE)) {
        is GoogleCalendar.Auth.Token -> a.token
        is GoogleCalendar.Auth.NeedsConsent -> throw TaskError("Reconnect Google Tasks in Settings › Tasks")
        is GoogleCalendar.Auth.Failed -> throw TaskError(a.message)
    }

    private suspend fun call(token: String, url: String, method: String = "GET", body: JsonObject? = null): JsonObject? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url.toHttpUrl()).header("Authorization", "Bearer $token")
            .method(method, body?.toString()?.toRequestBody("application/json".toMediaType()) ?: if (method == "POST") ByteArray(0).toRequestBody() else null)
            .build()
        Tasks.http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 401) throw TaskError("Google sign-in expired; reconnect in Settings › Tasks")
            if (!r.isSuccessful) throw TaskError("Google Tasks: " + (Tasks.errorMessage(r, text) ?: "error ${r.code}"))
            if (text.isBlank()) null else Tasks.json.parseToJsonElement(text).jsonObject
        }
    }

    /** With a token straight from the consent screen (Settings › Tasks), or a silent one. */
    suspend fun listsWith(token: String): List<TaskList> =
        call(token, "$API/users/@me/lists?maxResults=100")?.get("items")?.jsonArray.orEmpty().map {
            val o = it.jsonObject
            TaskList(o.str("id")!!, o.str("title") ?: "Tasks")
        }

    override suspend fun lists(c: Context) = listsWith(token(c))

    override suspend fun tasks(c: Context, listId: String): List<TaskItem> {
        val all = call(token(c), "$API/lists/$listId/tasks?showCompleted=true&showHidden=true&maxResults=100")
            ?.get("items")?.jsonArray.orEmpty().map { it.jsonObject }
            .filter { it.str("title").orEmpty().isNotBlank() && it.str("deleted") != "true" }
        return ordered(all).map { TaskItem(it.str("id")!!, it.str("title")!!, it.str("status") == "completed") }
    }

    /** Top-level tasks in the list's order, each followed by its subtasks. */
    internal fun ordered(all: List<JsonObject>): List<JsonObject> {
        val byPos = compareBy<JsonObject> { it.str("position") ?: "" }
        return all.filter { it.str("parent") == null }.sortedWith(byPos).flatMap { t ->
            listOf(t) + all.filter { it.str("parent") == t.str("id") }.sortedWith(byPos)
        }
    }

    override suspend fun setDone(c: Context, listId: String, taskId: String, done: Boolean) {
        val body = JsonObject(
            if (done) mapOf("status" to JsonPrimitive("completed"))
            else mapOf("status" to JsonPrimitive("needsAction"), "completed" to JsonNull)
        )
        call(token(c), "$API/lists/$listId/tasks/$taskId", "PATCH", body)
    }

    override suspend fun add(c: Context, listId: String, title: String) {
        call(token(c), "$API/lists/$listId/tasks", "POST", JsonObject(mapOf("title" to JsonPrimitive(title))))
    }

    /** Hides finished tasks from the list (Google's "clear completed"). */
    override suspend fun clearDone(c: Context, listId: String) {
        call(token(c), "$API/lists/$listId/clear", "POST")
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
}
