package com.desk.dashboard.tasks

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Todoist over its API (v1), signed in with the user's own API token (Todoist › Settings ›
 * Integrations › Developer), so there's no app registration or sign-in screen. A Checklist tile
 * shows one project's open tasks; ticking one completes it in Todoist.
 */
object Todoist : TaskService {
    internal var base = "https://api.todoist.com/api/v1"

    override val id = "todoist"
    override val name = "Todoist"
    override val clearsDone = false

    private fun token(c: Context) = Tasks.accounts(c).getString("todoist_token", null)
    override fun connected(c: Context) = token(c) != null

    /** Checks the token against Todoist, and keeps it if it works. Returns its projects. */
    suspend fun connect(c: Context, token: String): List<TaskList> {
        val t = token.trim()
        if (t.isEmpty()) throw TaskError("Paste your API token first")
        val projects = projects(t)
        Tasks.accounts(c).edit().putString("todoist_token", t).apply()
        return projects
    }

    fun disconnect(c: Context) = Tasks.accounts(c).edit().remove("todoist_token").apply()

    private fun need(c: Context) = token(c) ?: throw TaskError("Connect Todoist in Settings › Tasks")

    private suspend fun call(token: String, path: String, method: String = "GET", body: JsonObject? = null): JsonObject? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$base$path".toHttpUrl()).header("Authorization", "Bearer $token")
            .method(method, body?.toString()?.toRequestBody("application/json".toMediaType()) ?: if (method == "POST") ByteArray(0).toRequestBody() else null)
            .build()
        Tasks.http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            when {
                r.code == 401 || r.code == 403 -> throw TaskError("Todoist didn't accept that token")
                r.code == 429 -> throw TaskError("Todoist asked to slow down; trying again shortly")
                !r.isSuccessful -> throw TaskError("Todoist: " + (Tasks.errorMessage(r, text) ?: "error ${r.code}"))
            }
            if (text.isBlank()) null else Tasks.json.parseToJsonElement(text).jsonObject
        }
    }

    /** Every page of a list endpoint ({ results, next_cursor }). */
    private suspend fun all(token: String, path: String): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        var cursor: String? = null
        do {
            val sep = if ('?' in path) '&' else '?'
            val page = call(token, path + "${sep}limit=200" + (cursor?.let { "&cursor=$it" } ?: "")) ?: break
            out += page["results"]?.jsonArray.orEmpty().map { it.jsonObject }
            cursor = page.str("next_cursor")
        } while (cursor != null && out.size < 2000)
        return out
    }

    internal suspend fun projects(token: String): List<TaskList> = parseProjects(all(token, "/projects"))
    internal suspend fun tasksWith(token: String, projectId: String) = parseTasks(all(token, "/tasks?project_id=$projectId"))
    internal suspend fun closeWith(token: String, taskId: String, done: Boolean) { call(token, "/tasks/$taskId/" + if (done) "close" else "reopen", "POST") }
    internal suspend fun addWith(token: String, projectId: String, title: String) {
        call(token, "/tasks", "POST", JsonObject(mapOf("content" to JsonPrimitive(title), "project_id" to JsonPrimitive(projectId))))
    }

    override suspend fun lists(c: Context) = projects(need(c))

    override suspend fun tasks(c: Context, listId: String) = tasksWith(need(c), listId)
    override suspend fun setDone(c: Context, listId: String, taskId: String, done: Boolean) = closeWith(need(c), taskId, done)
    override suspend fun add(c: Context, listId: String, title: String) = addWith(need(c), listId, title)

    /** The Inbox first, then the rest in Todoist's order. */
    internal fun parseProjects(results: List<JsonObject>): List<TaskList> = results
        .filter { it.bool("is_deleted") != true && it.bool("is_archived") != true }
        .sortedWith(compareBy({ it.bool("inbox_project") != true }, { it.int("child_order") ?: 0 }))
        .map { TaskList(it.str("id")!!, if (it.bool("inbox_project") == true) "Inbox" else it.str("name") ?: "Project") }

    /** Open tasks in Todoist's order, each followed by its subtasks. */
    internal fun parseTasks(results: List<JsonObject>): List<TaskItem> {
        val live = results.filter { it.bool("is_deleted") != true && it.str("content").orEmpty().isNotBlank() }
        // order_key sorts siblings when present; older tasks only have child_order.
        val order = compareBy<JsonObject>({ it.str("order_key") ?: "" }, { it.int("child_order") ?: 0 })
        fun children(parent: String?): List<JsonObject> = live.filter { it.str("parent_id") == parent }.sortedWith(order)
            .flatMap { listOf(it) + children(it.str("id")) }
        val tree = children(null)
        // Subtasks whose parent isn't in this project still show, at the end.
        val rest = live.filter { t -> tree.none { it === t } }
        return (tree + rest).map { TaskItem(it.str("id")!!, it.str("content")!!, it.bool("checked") == true) }
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.intOrNull
}
