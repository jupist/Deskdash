package com.desk.dashboard.remote

import android.content.Context
import android.content.SharedPreferences
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import com.desk.dashboard.BuildConfig
import com.desk.dashboard.board.BoardStore
import com.desk.dashboard.board.Grid
import com.desk.dashboard.board.GridLayout
import com.desk.dashboard.board.TileSpec
import com.desk.dashboard.alarm.Alarms
import com.desk.dashboard.data.Category
import com.desk.dashboard.data.DashDb
import com.desk.dashboard.data.UpcomingDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.json.JsonPrimitive
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import com.desk.dashboard.remote.Protocol.obj
import com.desk.dashboard.remote.Protocol.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Calendar
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.security.auth.x500.X500Principal
import kotlin.concurrent.thread

/** The dashboard's grid and its size on screen, so a phone can draw it to scale. */
@Serializable
data class ScreenInfo(val cols: Int, val rows: Int, val widthDp: Float, val heightDp: Float)

/**
 * The tablet side: while "Edit from another device" is on, listens on the local network for
 * paired phones and applies their edits. Off by default; nothing listens while it's off.
 */
object RemoteHost {
    private const val TAG = "RemoteHost"
    private const val ALIAS = "deskdash_remote"
    private const val PAIR_WINDOW_MS = 5 * 60_000L
    private const val MAX_ATTEMPTS = 5

    private lateinit var app: Context
    private lateinit var sp: SharedPreferences
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled
    /** The pairing code on show, or null when pairing is closed. */
    private val _code = MutableStateFlow<String?>(null)
    val code: StateFlow<String?> = _code
    private var codeExpires = 0L
    private var attempts = 0
    /** Paired phones: token hash → phone name. */
    private val _paired = MutableStateFlow<Map<String, String>>(emptyMap())
    val paired: StateFlow<Map<String, String>> = _paired
    /** Phones connected right now, by name. */
    private val _connected = MutableStateFlow<List<String>>(emptyList())
    val connected: StateFlow<List<String>> = _connected

    @Volatile private var server: SSLServerSocket? = null
    private val clients = mutableSetOf<Socket>()
    private var nsdListener: NsdManager.RegistrationListener? = null

    @Volatile var screen: ScreenInfo? = null
        private set

    fun init(context: Context) {
        app = context.applicationContext
        sp = app.getSharedPreferences("remote_host", Context.MODE_PRIVATE)
        _paired.value = sp.getStringSet("paired", emptySet()).orEmpty()
            .mapNotNull { e -> e.split('\t', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
        screen = sp.getString("screen", null)?.let { runCatching { Protocol.json.decodeFromString<ScreenInfo>(it) }.getOrNull() }
        if (sp.getBoolean("enabled", false)) start()
    }

    /** Called by the dashboard whenever its grid or size changes. */
    fun reportScreen(info: ScreenInfo) {
        if (info == screen || !::sp.isInitialized) return
        screen = info
        sp.edit().putString("screen", Protocol.json.encodeToString(ScreenInfo.serializer(), info)).apply()
    }

    fun setEnabled(on: Boolean) {
        sp.edit().putBoolean("enabled", on).apply()
        if (on) start() else stop()
    }

    /** Shows a fresh code for five minutes. */
    fun openPairing(): String {
        val c = "%06d".format(SecureRandom().nextInt(1_000_000))
        attempts = 0
        codeExpires = System.currentTimeMillis() + PAIR_WINDOW_MS
        _code.value = c
        return c
    }

    fun closePairing() { _code.value = null }

    fun forget(tokenHash: String) {
        savePaired(_paired.value - tokenHash)
        // Drop its live connection too: it signed in with the token just revoked.
        synchronized(clients) { clients.toList() }.forEach { runCatching { it.close() } }
    }

    private fun savePaired(m: Map<String, String>) {
        _paired.value = m
        sp.edit().putStringSet("paired", m.map { (k, v) -> "$k\t$v" }.toSet()).apply()
    }

    fun deviceName(c: Context = app): String =
        android.provider.Settings.Global.getString(c.contentResolver, "device_name")?.takeIf { it.isNotBlank() } ?: Build.MODEL

    // --- Identity: a key that never leaves the device's keystore, with a self-signed certificate.

    private fun identity(): Pair<PrivateKey, X509Certificate> {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(ALIAS)) {
            val start = Calendar.getInstance()
            val end = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                        .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                        .setCertificateSubject(X500Principal("CN=Deskdash"))
                        .setCertificateNotBefore(start.time)
                        .setCertificateNotAfter(end.time)
                        .build()
                )
            }.generateKeyPair()
        }
        return ks.getKey(ALIAS, null) as PrivateKey to ks.getCertificate(ALIAS) as X509Certificate
    }

    val fingerprint: String by lazy { Protocol.fingerprint(identity().second) }

    private fun sslContext(): SSLContext {
        val (key, cert) = identity()
        val km = object : X509ExtendedKeyManager() {
            override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
            override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?) = ALIAS
            override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
            override fun getCertificateChain(alias: String?) = arrayOf(cert)
            override fun getPrivateKey(alias: String?) = key
            override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = null
            override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = null
        }
        return SSLContext.getInstance("TLS").apply { init(arrayOf(km), null, null) }
    }

    // --- Server

    @Synchronized private fun start() {
        _enabled.value = true
        if (server != null) return
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        thread(name = "remote-host", isDaemon = true) {
            val s = runCatching { sslContext().serverSocketFactory.createServerSocket(0) as SSLServerSocket }
                .onFailure { Log.w(TAG, "can't listen", it) }.getOrNull() ?: return@thread
            server = s
            register(s.localPort)
            while (!s.isClosed) {
                val socket = runCatching { s.accept() }.getOrNull() ?: break
                thread(name = "remote-client", isDaemon = true) { serve(socket) }
            }
        }
    }

    @Synchronized private fun stop() {
        _enabled.value = false
        _code.value = null
        server?.let { runCatching { it.close() } }
        server = null
        synchronized(clients) { clients.toList() }.forEach { runCatching { it.close() } }
        nsdListener?.let { runCatching { app.getSystemService(NsdManager::class.java).unregisterService(it) } }
        nsdListener = null
        scope.cancel()
    }

    private fun register(port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = deviceName()
            serviceType = Protocol.SERVICE_TYPE
            this.port = port
            setAttribute("id", Protocol.hostId(fingerprint))
            setAttribute("proto", Protocol.PROTOCOL.toString())
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) { Log.i(TAG, "announced as ${i.serviceName} on $port") }
            override fun onRegistrationFailed(i: NsdServiceInfo, code: Int) { Log.w(TAG, "announce failed: $code") }
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, code: Int) {}
        }
        nsdListener = l
        app.getSystemService(NsdManager::class.java).registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
    }

    private fun serve(socket: Socket) {
        synchronized(clients) { clients += socket }
        var name: String? = null
        var watch: Job? = null
        val out = socket.getOutputStream().bufferedWriter()
        fun send(o: JsonObject) = synchronized(out) { out.write(o.toString()); out.newLine(); out.flush() }
        try {
            socket.getInputStream().bufferedReader().lineSequence().forEach { line ->
                val msg = runCatching { Protocol.json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return@forEach
                val reply = when (val op = msg.str("op")) {
                    "hello" -> obj(
                        "proto" to Protocol.PROTOCOL, "minProto" to Protocol.MIN_PROTOCOL, "app" to BuildConfig.VERSION_NAME,
                        "name" to deviceName(), "features" to Protocol.FEATURES,
                    )
                    "pair" -> pair(msg)
                    "auth" -> {
                        val n = _paired.value[Protocol.sha256(msg.str("token").orEmpty())]
                        if (n == null) obj("error" to "unpaired") else {
                            name = n
                            _connected.value = _connected.value + n
                            // Tell the phone whenever the boards change (its own edits included).
                            // Undispatched: subscribe before replying, so no change after sign-in is missed.
                            watch = scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                                merge(
                                    BoardStore.boards.drop(1),
                                    DashDb.get(app).dates().upcoming(LocalDate.now()).drop(1),
                                    Alarms.config(app).drop(1),
                                ).collect { runCatching { send(obj("op" to "changed")) } }
                            }
                            obj("ok" to true)
                        }
                    }
                    else -> if (name == null) obj("error" to "unpaired") else handle(op, msg)
                }
                send(JsonObject(reply + ("re" to (msg["id"] ?: kotlinx.serialization.json.JsonNull))))
            }
        } catch (e: Exception) {
            Log.i(TAG, "connection closed: ${e.message}")
        } finally {
            watch?.cancel()
            name?.let { n -> _connected.value = _connected.value.toMutableList().apply { remove(n) } }
            synchronized(clients) { clients -= socket }
            runCatching { socket.close() }
        }
    }

    private fun pair(msg: JsonObject): JsonObject {
        val c = _code.value
        if (c == null || System.currentTimeMillis() > codeExpires) return obj("error" to "closed")
        if (Protocol.proof(c, fingerprint) != msg.str("proof")) {
            // A few wrong tries close pairing, so the code can't be guessed.
            if (++attempts >= MAX_ATTEMPTS) _code.value = null
            return obj("error" to "code")
        }
        val token = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val phone = msg.str("name")?.take(40)?.takeIf { it.isNotBlank() } ?: "Phone"
        savePaired(_paired.value + (Protocol.sha256(token) to phone))
        _code.value = null
        return obj("ok" to true, "token" to token)
    }

    private fun handle(op: String?, msg: JsonObject): JsonObject = runBlocking {
        withContext(Dispatchers.Main) {
            val b = BoardStore.boards.value ?: return@withContext obj("error" to "busy")
            val s = screen ?: return@withContext obj("error" to "noscreen")
            when (op) {
                "get" -> obj(
                    "presets" to JsonArray(b.presets.map { obj("id" to it.id, "name" to it.name) }),
                    "active" to b.activeId,
                    "layout" to Protocol.json.encodeToJsonElement(GridLayout.serializer(), BoardStore.layoutFor(b.active, s.cols, s.rows)),
                    "width" to s.widthDp, "height" to s.heightDp,
                )
                "layout" -> {
                    val l = runCatching { Protocol.json.decodeFromJsonElement(GridLayout.serializer(), msg["layout"]!!) }.getOrNull()
                    when {
                        l == null || !valid(l, s) -> obj("error" to "invalid")
                        msg.str("preset") != b.activeId -> obj("error" to "stale")
                        else -> { BoardStore.saveLayout(l); obj("ok" to true) }
                    }
                }
                "active" -> { msg.str("preset")?.let { BoardStore.setActive(it) }; obj("ok" to true) }
                "tile" -> {
                    val id = msg.str("tile") ?: return@withContext obj("error" to "invalid")
                    val config = msg["config"] as? JsonObject
                    val style = msg.str("style")
                    val update = { t: TileSpec -> t.copy(config = config ?: t.config, style = style ?: t.style) }
                    // Same as editing on the tablet: if this screen's size isn't saved yet, save it so the change sticks.
                    val current = BoardStore.layoutFor(b.active, s.cols, s.rows)
                    if (current.tiles.none { it.id == id }) return@withContext obj("error" to "stale")
                    if (b.active.layouts[current.key] == null) BoardStore.saveLayout(current.copy(tiles = current.tiles.map { if (it.id == id) update(it) else it }))
                    BoardStore.updateTileEverywhere(id, update)
                    obj("ok" to true)
                }
                "dates" -> {
                    val dates = withContext(Dispatchers.IO) { DashDb.get(app).dates().upcoming(LocalDate.now()).first() }
                    obj("dates" to JsonArray(dates.map {
                        obj("id" to it.id, "title" to it.title, "date" to it.date.toString(), "time" to it.time?.toString(), "category" to it.category.name)
                    }))
                }
                "date.save" -> {
                    val date = msg.str("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    val title = msg.str("title")?.trim()?.take(UpcomingDate.TITLE_MAX)
                    if (date == null || title.isNullOrEmpty()) return@withContext obj("error" to "invalid")
                    withContext(Dispatchers.IO) {
                        DashDb.get(app).dates().upsert(
                            UpcomingDate(
                                id = msg.str("id")?.toLongOrNull() ?: 0, title = title, date = date,
                                time = msg.str("time")?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                                category = runCatching { Category.valueOf(msg.str("category")!!) }.getOrDefault(Category.OTHER),
                            )
                        )
                    }
                    obj("ok" to true)
                }
                "date.delete" -> {
                    val id = msg.str("id")?.toLongOrNull() ?: return@withContext obj("error" to "invalid")
                    withContext(Dispatchers.IO) {
                        val dao = DashDb.get(app).dates()
                        dao.upcoming(LocalDate.of(1970, 1, 1)).first().firstOrNull { it.id == id }?.let { dao.delete(it) }
                    }
                    obj("ok" to true)
                }
                "alarm" -> {
                    val a = withContext(Dispatchers.IO) { Alarms.config(app).first() }
                    obj(
                        "enabled" to a.enabled, "time" to a.time.toString(), "days" to a.days.sorted().map { it.name },
                        "once" to a.once, "next" to a.next().toString(),
                    )
                }
                "alarm.set" -> {
                    withContext(Dispatchers.IO) {
                        msg.str("time")?.let { t -> runCatching { LocalTime.parse(t) }.getOrNull()?.let { Alarms.setTime(app, it) } }
                        (msg["days"] as? JsonArray)?.let { arr ->
                            Alarms.setDays(app, arr.mapNotNull { e -> runCatching { DayOfWeek.valueOf((e as JsonPrimitive).content) }.getOrNull() }.toSet())
                        }
                        msg.str("once")?.toBooleanStrictOrNull()?.let { Alarms.setOnce(app, it) }
                        // Last, so turning it on schedules the time and days just set.
                        msg.str("enabled")?.toBooleanStrictOrNull()?.let { Alarms.setEnabled(app, it) }
                    }
                    obj("ok" to true)
                }
                else -> obj("error" to "unknown", "op" to op)
            }
        }
    }

    internal fun valid(l: GridLayout, s: ScreenInfo) = l.cols == s.cols && l.rows == s.rows &&
        l.tiles.all { Grid.inBounds(it, l.cols, l.rows) } &&
        l.tiles.indices.none { i -> (i + 1 until l.tiles.size).any { j -> l.tiles[i].overlaps(l.tiles[j]) } } &&
        l.tiles.map { it.id }.toSet().size == l.tiles.size

}
