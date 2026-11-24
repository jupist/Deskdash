package com.desk.dashboard.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Phone ↔ tablet remote control, over the home Wi-Fi with no server in between.
 *
 * The tablet announces itself with mDNS ([SERVICE_TYPE]) and listens on a TLS socket. Each side
 * sends one JSON object per line. Every connection starts with `hello`, where both sides learn
 * the other's [PROTOCOL] version and features; a phone only offers what both understand.
 *
 * Pairing: the tablet shows a 6-digit code. The phone answers with an HMAC of the tablet's
 * certificate fingerprint keyed by that code, which proves it knows the code and that it sees the
 * tablet's real certificate (not a stand-in's). The tablet replies with a token; from then on the
 * phone trusts only that certificate and signs in with the token.
 */
object Protocol {
    /** Bumped only when a change would confuse older versions. Features cover everything else. */
    const val PROTOCOL = 1
    /** The oldest protocol this build still speaks. */
    const val MIN_PROTOCOL = 1
    const val SERVICE_TYPE = "_deskdash._tcp."

    /** What this build can edit remotely. A newer build adds names; both sides use the overlap. */
    val FEATURES = listOf("layout", "presets", "tiles", "countdowns", "alarm", "prefs")

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun fingerprint(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }

    /** Short id announced over mDNS, so the phone recognises a paired tablet before connecting. */
    fun hostId(fingerprint: String) = fingerprint.take(16)

    fun proof(code: String, fingerprint: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(code.toByteArray(), "HmacSHA256"))
        return mac.doFinal(fingerprint.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Whether two builds can talk: each must be at least the other's minimum. */
    fun compatible(theirs: Int, theirMin: Int) = theirs >= MIN_PROTOCOL && PROTOCOL >= theirMin

    fun obj(vararg pairs: Pair<String, Any?>) = JsonObject(pairs.mapNotNull { (k, v) ->
        when (v) {
            null -> null
            is String -> k to JsonPrimitive(v)
            is Number -> k to JsonPrimitive(v)
            is Boolean -> k to JsonPrimitive(v)
            is kotlinx.serialization.json.JsonElement -> k to v
            is List<*> -> k to JsonArray(v.map { it as? kotlinx.serialization.json.JsonElement ?: JsonPrimitive(it.toString()) })
            else -> k to JsonPrimitive(v.toString())
        }
    }.toMap())

    fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
    fun JsonObject.strings(key: String) = (this[key] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
}
