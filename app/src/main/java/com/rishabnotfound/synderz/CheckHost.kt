package com.rishabnotfound.synderz

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** A check-host.net probe location, e.g. ("us1.node.check-host.net", "us", "USA", "Los Angeles"). */
data class GeoNode(val host: String, val cc: String, val country: String, val city: String)

/** One node's verdict. ok == null means the node hasn't reported yet. */
data class GeoResult(val ok: Boolean?, val sec: Double? = null, val code: String? = null, val ip: String? = null, val message: String? = null)

data class GeoRun(
    val id: String? = null,
    val nodes: List<GeoNode> = emptyList(),
    val results: Map<String, GeoResult> = emptyMap(),
    val running: Boolean = true,
    val error: String? = null,
    val at: Long = System.currentTimeMillis(),
) {
    val permalink get() = id?.let { "https://check-host.net/check-report/$it" }
}

/**
 * check-host.net HTTP check from ~48 locations. Uses the site's JSON responses (Accept: application/json):
 * the HTML page needs a CSRF/JS round-trip, the JSON one hands back request_id + node metadata directly.
 */
object CheckHost {
    private const val BASE = "https://check-host.net"
    private const val UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36"
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun get(url: String): JsonObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .header("Origin", BASE).header("Referer", "$BASE/").header("User-Agent", UA).header("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { r ->
            val body = r.body!!.string()
            if (!r.isSuccessful) throw IllegalStateException("check-host.net answered ${r.code}")
            runCatching { json.parseToJsonElement(body).jsonObject }.getOrElse { throw IllegalStateException("check-host.net sent an unexpected page") }
        }
    }

    /** Starts a check; returns the request id and the nodes that will report. */
    suspend fun start(target: String): Pair<String, List<GeoNode>> {
        val o = get("$BASE/check-http".toHttpUrl().newBuilder().addQueryParameter("host", target).build().toString())
        val id = (o["request_id"] as? JsonPrimitive)?.contentOrNull ?: throw IllegalStateException(
            when (val e = (o["error"] as? JsonPrimitive)?.contentOrNull) {
                null -> "check-host.net didn't start the check"
                "limit_exceeded" -> "check-host.net's free rate limit is used up for now. Try again in a few minutes."
                else -> "check-host.net: ${e.replace('_', ' ')}"
            },
        )
        return id to parseNodes(o["nodes"] as? JsonObject)
    }

    suspend fun results(id: String): Map<String, GeoResult> = parseResults(get("$BASE/check_result/$id"))

    fun parseNodes(o: JsonObject?): List<GeoNode> = o.orEmpty().map { (host, v) ->
        val a = (v as? JsonArray).orEmpty().map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }
        GeoNode(host, a.getOrElse(0) { host.take(2) }, a.getOrElse(1) { "" }, a.getOrElse(2) { "" })
    }

    /**
     * Per node: null → pending; [[1, sec, "OK", "200", ip]] → ok; [[0, sec, msg, code?, ip?]] → failed;
     * [null, {"message": "..."}] → failed before any HTTP exchange.
     */
    fun parseResults(o: JsonObject): Map<String, GeoResult> = o.mapValues { (_, v) ->
        if (v is JsonNull || v !is JsonArray || v.isEmpty()) return@mapValues GeoResult(null)
        val first = v[0]
        if (first is JsonArray) {
            fun s(i: Int) = (first.getOrNull(i) as? JsonPrimitive)?.contentOrNull
            val ok = (first.getOrNull(0) as? JsonPrimitive)?.intOrNull == 1
            GeoResult(ok, (first.getOrNull(1) as? JsonPrimitive)?.doubleOrNull, s(3), s(4), s(2))
        } else {
            val msg = (v.getOrNull(1) as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
            GeoResult(false, message = msg ?: "No response")
        }
    }

    /** "de" → 🇩🇪 via regional indicator symbols. */
    fun flag(cc: String): String {
        val c = cc.uppercase().let { if (it == "UK") "GB" else it }
        if (c.length != 2 || !c.all { it in 'A'..'Z' }) return "🏳"
        return String(Character.toChars(0x1F1E6 + (c[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (c[1] - 'A')))
    }
}

