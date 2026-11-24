package com.desk.dashboard.tasks

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

data class TaskList(val id: String, val title: String)
data class TaskItem(val id: String, val title: String, val done: Boolean)

/** A to-do service a Checklist tile can show one list from. */
interface TaskService {
    val id: String
    val name: String
    /** Connected on this device (signed in, or a token saved). */
    fun connected(c: Context): Boolean
    suspend fun lists(c: Context): List<TaskList>
    suspend fun tasks(c: Context, listId: String): List<TaskItem>
    suspend fun setDone(c: Context, listId: String, taskId: String, done: Boolean)
    suspend fun add(c: Context, listId: String, title: String)
    /** Finished items vanish from the service by themselves (Todoist), so there's nothing to clear. */
    val clearsDone: Boolean
    suspend fun clearDone(c: Context, listId: String) {}
}

/** A problem worth showing as it is, in the tile or the settings (no stack traces). */
class TaskError(message: String) : Exception(message)

/**
 * The task lists shown by Checklist tiles, from Google Tasks or Todoist. Holds the last known
 * items per list; changes show at once and are then sent to the service.
 */
object Tasks {
    val services: List<TaskService> get() = listOf(GoogleTasks, Todoist)
    fun service(id: String?): TaskService? = services.firstOrNull { it.id == id }

    data class ListState(val items: List<TaskItem>? = null, val error: String? = null)

    private val _lists = MutableStateFlow<Map<String, ListState>>(emptyMap())
    val lists: StateFlow<Map<String, ListState>> = _lists

    fun key(service: TaskService, listId: String) = "${service.id}:$listId"

    suspend fun refresh(c: Context, s: TaskService, listId: String) {
        runCatching { s.tasks(c, listId) }
            .onSuccess { items -> set(s, listId) { ListState(items) } }
            .onFailure { e -> set(s, listId) { it.copy(error = message(e)) } }
    }

    suspend fun setDone(c: Context, s: TaskService, listId: String, taskId: String, done: Boolean) {
        set(s, listId) { st -> st.copy(items = st.items?.map { if (it.id == taskId) it.copy(done = done) else it }) }
        runCatching { s.setDone(c, listId, taskId, done) }
            .onFailure { e -> set(s, listId) { it.copy(error = message(e)) }; refresh(c, s, listId) }
        // Google keeps finished tasks in the list, so refresh shows the truth. Todoist drops them;
        // they stay ticked here until the next refresh, so a mistaken tick can be undone.
        if (s.clearsDone) refresh(c, s, listId)
    }

    suspend fun add(c: Context, s: TaskService, listId: String, title: String) {
        runCatching { s.add(c, listId, title) }.onFailure { e -> set(s, listId) { it.copy(error = message(e)) } }
        refresh(c, s, listId)
    }

    suspend fun clearDone(c: Context, s: TaskService, listId: String) {
        runCatching { s.clearDone(c, listId) }.onFailure { e -> set(s, listId) { it.copy(error = message(e)) } }
        refresh(c, s, listId)
    }

    private fun set(s: TaskService, listId: String, block: (ListState) -> ListState) =
        _lists.update { it + (key(s, listId) to block(it[key(s, listId)] ?: ListState())) }

    fun message(e: Throwable): String = when (e) {
        is TaskError -> e.message ?: "Something went wrong"
        is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException -> "Offline"
        else -> "Couldn't reach the service"
    }

    // --- Shared plumbing for the services.

    internal val json = Json { ignoreUnknownKeys = true }
    internal val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    /** The service's own explanation from an error response, if it gave one. */
    internal fun errorMessage(r: Response, text: String): String? = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        (o["error"] as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull // Google
            ?: o["error"]?.jsonPrimitive?.contentOrNull // Todoist
    }.getOrNull()

    /** Where a service keeps its sign-in on this device (never backed up or sent to a tablet). */
    internal fun accounts(c: Context) = c.applicationContext.getSharedPreferences("task_accounts", Context.MODE_PRIVATE)
}
