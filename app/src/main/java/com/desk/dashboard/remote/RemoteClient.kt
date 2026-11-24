package com.desk.dashboard.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.desk.dashboard.board.GridLayout
import com.desk.dashboard.remote.Protocol.int
import com.desk.dashboard.remote.Protocol.obj
import com.desk.dashboard.remote.Protocol.str
import com.desk.dashboard.remote.Protocol.strings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.BufferedWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

/** A Deskdash found on the local network. [id] is null for builds that don't announce one. */
data class FoundTablet(val name: String, val id: String?, val address: InetAddress, val port: Int)

/** Finds tablets announcing themselves on the local network, while [start]ed. */
class Discovery(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val _found = MutableStateFlow<List<FoundTablet>>(emptyList())
    val found: StateFlow<List<FoundTablet>> = _found
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var listener: NsdManager.DiscoveryListener? = null

    fun start() {
        if (listener != null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) = synchronized(pending) { pending += info; next() }
            override fun onServiceLost(info: NsdServiceInfo) {
                _found.value = _found.value.filter { it.name != info.serviceName }
            }
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { Log.w("Discovery", "start failed $errorCode") }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        listener = l
        nsd.discoverServices(Protocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
    }

    fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        synchronized(pending) { pending.clear() }
    }

    // Older Android versions resolve one service at a time, so resolve in turn.
    @Suppress("DEPRECATION")
    private fun next() {
        if (resolving) return
        val info = pending.removeFirstOrNull() ?: return
        resolving = true
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(i: NsdServiceInfo, errorCode: Int) = done()
            override fun onServiceResolved(i: NsdServiceInfo) {
                val host = i.host
                if (host != null) {
                    val t = FoundTablet(i.serviceName, i.attributes["id"]?.decodeToString(), host, i.port)
                    _found.value = _found.value.filter { it.name != t.name } + t
                }
                done()
            }
            fun done() = synchronized(pending) { resolving = false; next() }
        })
    }
}

/** Tablets this phone has paired with: tablet id → certificate fingerprint and sign-in token. */
object Pairings {
    data class Pairing(val fingerprint: String, val token: String)

    private fun sp(c: Context) = c.getSharedPreferences("remote_client", Context.MODE_PRIVATE)

    fun get(c: Context, id: String): Pairing? = sp(c).getString(id, null)?.split('\t')?.takeIf { it.size == 2 }?.let { Pairing(it[0], it[1]) }
    fun save(c: Context, fingerprint: String, token: String) = sp(c).edit().putString(Protocol.hostId(fingerprint), "$fingerprint\t$token").apply()
    fun forget(c: Context, id: String) = sp(c).edit().remove(id).apply()
}

/**
 * One TLS connection to a tablet. Requests get replies matched by id; anything the tablet sends
 * unasked (a change notice) goes to [onEvent].
 */
class Connection private constructor(private val socket: SSLSocket, val fingerprint: String, private val onEvent: (JsonObject) -> Unit, private val onClosed: () -> Unit) {
    private val out: BufferedWriter = socket.outputStream.bufferedWriter()
    private val ids = AtomicInteger()
    private val waiting = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()

    init {
        thread(name = "remote-conn", isDaemon = true) {
            try {
                socket.inputStream.bufferedReader().lineSequence().forEach { line ->
                    val msg = runCatching { Protocol.json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return@forEach
                    val re = msg.int("re")
                    if (re != null) waiting.remove(re)?.complete(msg) else onEvent(msg)
                }
            } catch (_: Exception) {
            } finally {
                waiting.values.forEach { it.completeExceptionally(java.io.IOException("closed")) }
                onClosed()
            }
        }
    }

    suspend fun request(op: String, vararg fields: Pair<String, Any?>): JsonObject {
        val id = ids.incrementAndGet()
        val reply = CompletableDeferred<JsonObject>()
        waiting[id] = reply
        val msg = obj("op" to op, "id" to id, *fields)
        withContext(Dispatchers.IO) { synchronized(out) { out.write(msg.toString()); out.newLine(); out.flush() } }
        return withTimeout(10_000) { reply.await() }
    }

    fun close() = runCatching { socket.close() }

    companion object {
        /** Connects; with a [pin], refuses any certificate but that one. */
        suspend fun open(t: FoundTablet, pin: String?, onEvent: (JsonObject) -> Unit, onClosed: () -> Unit): Connection = withContext(Dispatchers.IO) {
            var seen: String? = null
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = throw CertificateException()
                override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String?) {
                    val fp = Protocol.fingerprint(chain[0])
                    if (pin != null && fp != pin) throw CertificateException("not the paired tablet")
                    seen = fp
                }
                override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
            }
            val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }
            val s = ctx.socketFactory.createSocket() as SSLSocket
            s.connect(InetSocketAddress(t.address, t.port), 5_000)
            s.startHandshake()
            Connection(s, seen!!, onEvent, onClosed)
        }
    }
}

/** What the phone shows of a tablet's dashboard. */
data class RemoteBoard(
    val presets: List<Pair<String, String>>,
    val active: String,
    val layout: GridLayout,
    val widthDp: Float,
    val heightDp: Float,
    val features: Set<String>,
)

/** A phone's session with one tablet: connect, pair if needed, then load and edit its board. */
class RemoteSession(private val context: Context, private val tablet: FoundTablet) {
    sealed interface State {
        data object Connecting : State
        data class NeedsCode(val error: String? = null) : State
        data class Ready(val board: RemoteBoard) : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<State>(State.Connecting)
    val state: StateFlow<State> = _state
    private var conn: Connection? = null
    private var features = emptySet<String>()
    private var closed = false
    /** Bumped per connection, so an old connection closing doesn't report on a newer one. */
    private var generation = 0

    fun start() = scope.launch { connect() }

    private suspend fun connect() {
        _state.value = State.Connecting
        val pairing = tablet.id?.let { Pairings.get(context, it) }
        val gen = ++generation
        val c = runCatching {
            Connection.open(tablet, pairing?.fingerprint, onEvent = { msg ->
                if (msg.str("op") == "changed") scope.launch { load() }
            }, onClosed = { scope.launch { if (!closed && gen == generation && _state.value !is State.Failed) _state.value = State.Failed("Lost the connection to ${tablet.name}") } })
        }.getOrElse {
            _state.value = State.Failed(
                if (it is javax.net.ssl.SSLHandshakeException && pairing != null) "${tablet.name} isn't the tablet this phone paired with. Forget it and pair again."
                else "Couldn't reach ${tablet.name}. Check both are on the same Wi-Fi."
            )
            return
        }
        conn = c
        val hello = runCatching { c.request("hello", "proto" to Protocol.PROTOCOL, "minProto" to Protocol.MIN_PROTOCOL, "features" to Protocol.FEATURES) }.getOrNull()
            ?: return fail("${tablet.name} didn't answer")
        val theirs = hello.int("proto") ?: 0
        if (!Protocol.compatible(theirs, hello.int("minProto") ?: theirs)) {
            val newer = theirs > Protocol.PROTOCOL
            return fail(
                if (newer) "${tablet.name} has a newer Deskdash (${hello.str("app")}). Update Deskdash on this phone to control it."
                else "${tablet.name} has an older Deskdash (${hello.str("app")}). Update Deskdash on the tablet to control it from this phone."
            )
        }
        features = hello.strings("features").toSet() intersect Protocol.FEATURES.toSet()
        if (pairing == null) { _state.value = State.NeedsCode(); return }
        signIn(pairing.token)
    }

    private suspend fun signIn(token: String) {
        val r = runCatching { conn!!.request("auth", "token" to token) }.getOrNull() ?: return fail("${tablet.name} didn't answer")
        if (r.str("error") == "unpaired") {
            tablet.id?.let { Pairings.forget(context, it) }
            _state.value = State.NeedsCode("${tablet.name} no longer knows this phone. Pair again.")
            return
        }
        load()
    }

    fun pair(code: String) = scope.launch {
        val c = conn ?: return@launch
        val r = runCatching { c.request("pair", "name" to phoneName(), "proof" to Protocol.proof(code.trim(), c.fingerprint)) }.getOrNull()
            ?: return@launch fail("${tablet.name} didn't answer")
        val token = r.str("token")
        if (token == null) {
            _state.value = State.NeedsCode(
                when (r.str("error")) {
                    "closed" -> "Pairing isn't open on the tablet. Tap Pair a phone there first."
                    else -> "That code didn't match. Check the code on the tablet."
                }
            )
            return@launch
        }
        Pairings.save(context, c.fingerprint, token)
        signIn(token)
    }

    private suspend fun load() {
        val r = runCatching { conn!!.request("get") }.getOrNull() ?: return
        when (r.str("error")) {
            null -> {}
            "noscreen" -> return fail("Open the dashboard on ${tablet.name} once, then try again.")
            else -> return
        }
        val layout = runCatching { Protocol.json.decodeFromJsonElement(GridLayout.serializer(), r["layout"]!!) }.getOrNull() ?: return
        val presets = r["presets"]!!.jsonArray.map { it.jsonObject.let { p -> p.str("id")!! to p.str("name")!! } }
        _state.value = State.Ready(
            RemoteBoard(
                presets, r.str("active")!!, layout,
                (r["width"] as? JsonPrimitive)?.content?.toFloatOrNull() ?: 800f,
                (r["height"] as? JsonPrimitive)?.content?.toFloatOrNull() ?: 480f,
                features,
            )
        )
    }

    /** Shows the change straight away, then sends it; the tablet's change notice confirms it. */
    fun setLayout(l: GridLayout) = scope.launch {
        val s = _state.value as? State.Ready ?: return@launch
        _state.value = State.Ready(s.board.copy(layout = l))
        val r = runCatching { conn!!.request("layout", "preset" to s.board.active, "layout" to Protocol.json.encodeToJsonElement(GridLayout.serializer(), l)) }.getOrNull()
        if (r?.str("error") != null) load()
    }

    fun setActive(id: String) = scope.launch { runCatching { conn!!.request("active", "preset" to id) } }

    fun retry() = scope.launch { conn?.close(); connect() }

    fun close() {
        closed = true
        conn?.close()
        scope.cancel()
    }

    private fun fail(message: String) { _state.value = State.Failed(message); conn?.close() }

    private fun phoneName() = RemoteHost.deviceName(context)
}
