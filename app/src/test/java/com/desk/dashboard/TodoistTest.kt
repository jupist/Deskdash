package com.desk.dashboard

import com.desk.dashboard.tasks.TaskError
import com.desk.dashboard.tasks.Todoist
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/**
 * Todoist's client against a stand-in server speaking API v1 as documented: paged results with
 * next_cursor, string ids, inbox_project, order_key and child_order, close/reopen with 204, and a
 * 401 body like the real one (checked against api.todoist.com with an invalid token).
 */
class TodoistTest {
    private lateinit var server: HttpServer
    private val seen = mutableListOf<String>()
    private var lastBody = ""
    private var lastAuth: String? = null

    private val projectsPage1 = """{"results":[
        {"id":"6Jf8VQXxpwv56VQ7","name":"Groceries","child_order":2,"is_archived":false,"is_deleted":false,"inbox_project":false},
        {"id":"6Jf8VQXxpwv56VQ8","name":"Old stuff","child_order":3,"is_archived":true,"is_deleted":false,"inbox_project":false}
      ],"next_cursor":"abc.def"}"""
    private val projectsPage2 = """{"results":[
        {"id":"6Jf8VQXxpwv56VQ1","name":"Inbox","child_order":0,"is_archived":false,"is_deleted":false,"inbox_project":true},
        {"id":"6Jf8VQXxpwv56VQ9","name":"Home","child_order":1,"is_archived":false,"is_deleted":false,"inbox_project":false}
      ],"next_cursor":null}"""
    private val tasks = """{"results":[
        {"id":"t3","content":"Buy milk","checked":false,"is_deleted":false,"parent_id":null,"child_order":2,"order_key":"a2","project_id":"p"},
        {"id":"t1","content":"Book train tickets","checked":false,"is_deleted":false,"parent_id":null,"child_order":1,"order_key":"a1","project_id":"p"},
        {"id":"t2","content":"Window seat","checked":false,"is_deleted":false,"parent_id":"t1","child_order":1,"order_key":"a1","project_id":"p"},
        {"id":"t4","content":"","checked":false,"is_deleted":false,"parent_id":null,"child_order":3,"order_key":"a3","project_id":"p"},
        {"id":"t5","content":"Gone","checked":false,"is_deleted":true,"parent_id":null,"child_order":4,"order_key":"a4","project_id":"p"}
      ],"next_cursor":null}"""

    @Before fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1") { ex ->
            val path = ex.requestURI.toString().removePrefix("/api/v1")
            seen += "${ex.requestMethod} $path"
            lastAuth = ex.requestHeaders.getFirst("Authorization")
            lastBody = ex.requestBody.readBytes().decodeToString()
            val (code, body) = when {
                lastAuth != "Bearer good" -> 401 to """{"error":"Unauthorized","error_code":477,"error_tag":"UNAUTHORIZED","http_code":401}"""
                path.startsWith("/projects") && "cursor=abc.def" in path -> 200 to projectsPage2
                path.startsWith("/projects") -> 200 to projectsPage1
                path.startsWith("/tasks?") -> 200 to tasks
                path.endsWith("/close") || path.endsWith("/reopen") -> 204 to ""
                path == "/tasks" -> 200 to """{"id":"t9","content":"New","checked":false}"""
                else -> 404 to """{"error":"Not found","http_code":404}"""
            }
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(code, if (code == 204) -1 else bytes.size.toLong())
            if (code != 204) ex.responseBody.use { it.write(bytes) } else ex.close()
        }
        server.start()
        Todoist.base = "http://127.0.0.1:${server.address.port}/api/v1"
    }

    @After fun stop() = server.stop(0)

    @Test fun projectsFollowEveryPageInboxFirstArchivedLeftOut() = runBlocking {
        val lists = Todoist.projects("good")
        assertEquals(listOf("Inbox", "Home", "Groceries"), lists.map { it.title })
        assertEquals("6Jf8VQXxpwv56VQ1", lists.first().id)
        assertEquals("Bearer good", lastAuth)
        assertTrue(seen.any { "cursor=abc.def" in it } && seen.all { "limit=200" in it })
    }

    @Test fun tasksInOrderWithSubtasksUnderTheirParent() = runBlocking {
        val items = Todoist.tasksWith("good", "p")
        assertEquals(listOf("Book train tickets", "Window seat", "Buy milk"), items.map { it.title })
        assertTrue(seen.last().startsWith("GET /tasks?project_id=p"))
    }

    @Test fun tickingClosesAndUntickingReopens() = runBlocking {
        Todoist.closeWith("good", "t1", true)
        Todoist.closeWith("good", "t1", false)
        assertEquals(listOf("POST /tasks/t1/close", "POST /tasks/t1/reopen"), seen)
    }

    @Test fun addingSendsContentAndProject() = runBlocking {
        Todoist.addWith("good", "p", "Call the bank")
        val body = Json.parseToJsonElement(lastBody).jsonObject
        assertEquals("Call the bank", body["content"]!!.jsonPrimitive.content)
        assertEquals("p", body["project_id"]!!.jsonPrimitive.content)
    }

    @Test fun aRejectedTokenSaysSo() = runBlocking {
        try {
            Todoist.projects("bad")
            fail("expected an error")
        } catch (e: TaskError) {
            assertEquals("Todoist didn't accept that token", e.message)
        }
    }
}
