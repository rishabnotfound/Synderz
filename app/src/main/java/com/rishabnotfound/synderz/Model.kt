package com.rishabnotfound.synderz

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Serializable
data class KV(
    val key: String = "",
    val value: String = "",
    val enabled: Boolean = true,
    val isFile: Boolean = false, // multipart only: value holds a content:// uri
)

enum class BodyType(val label: String) { NONE("none"), RAW("raw"), FORM("x-www-form-urlencoded"), MULTIPART("form-data"), BINARY("binary"), GRAPHQL("GraphQL") }

enum class RawType(val label: String, val mime: String) {
    JSON("JSON", "application/json"), TEXT("Text", "text/plain"), XML("XML", "application/xml"),
    HTML("HTML", "text/html"), JAVASCRIPT("JavaScript", "application/javascript"),
}

enum class AuthType(val label: String) { NONE("No Auth"), BEARER("Bearer Token"), BASIC("Basic Auth"), API_KEY("API Key") }

@Serializable
data class Auth(
    val type: AuthType = AuthType.NONE,
    val token: String = "",
    val username: String = "",
    val password: String = "",
    val key: String = "",
    val value: String = "",
    val inQuery: Boolean = false,
)

@Serializable
data class Req(
    val id: String = newId(),
    val name: String = "Untitled Request",
    val method: String = "GET",
    val url: String = "",
    val params: List<KV> = emptyList(),
    val headers: List<KV> = emptyList(),
    val bodyType: BodyType = BodyType.NONE,
    val rawType: RawType = RawType.JSON,
    val rawBody: String = "",
    val form: List<KV> = emptyList(),
    val multipart: List<KV> = emptyList(),
    val binaryUri: String = "",
    val gqlQuery: String = "",
    val gqlVariables: String = "",
    val auth: Auth = Auth(),
    val collectionId: String? = null,
)

@Serializable
data class Collection(
    val id: String = newId(),
    val name: String,
    val requests: List<Req> = emptyList(),
    val variables: List<KV> = emptyList(),
)

@Serializable
data class Env(val id: String = newId(), val name: String, val vars: List<KV> = emptyList())

@Serializable
data class HistoryEntry(val req: Req, val status: Int, val timeMs: Long, val at: Long)

/** Monitor URLs are hidden behind this. Only a salted hash is stored. */
@Serializable
data class PinLock(val hash: String, val salt: String, val len: Int)

fun hashPin(pin: String, salt: String): String =
    java.security.MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }

/** n-th PIN lockout (0-based): 30s, 1m, 2m, 4m … capped at 1h. */
fun lockoutSeconds(n: Int): Long = (30L shl n.coerceIn(0, 7)).coerceAtMost(3600)

/** Strips a monitor's URL and host out of free text (e.g. "Unable to resolve host x.com"). */
fun redact(text: String, url: String): String {
    val host = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':')
    var out = text.replace(url, "•••")
    if (host.isNotBlank()) out = out.replace(host, "•••", ignoreCase = true)
    return out
}

@Serializable
data class Monitor(val id: String = newId(), val name: String, val req: Req, val intervalSec: Int = 10)

/** Applies a new order to just the monitors named in [ids] (one dashboard page), leaving the rest where they are. */
fun reorderSlice(all: List<Monitor>, ids: List<String>): List<Monitor> {
    val set = ids.toSet()
    val q = ids.mapNotNull { id -> all.find { it.id == id } }.iterator()
    return all.map { if (it.id in set && q.hasNext()) q.next() else it }
}

/** One monitor check. code 0 = network failure. */
class Sample(val at: Long, val code: Int, val ms: Long, val error: String? = null) {
    val up get() = code in 200..399
}

const val MAX_MONITORS = 20

@Serializable
data class Settings(
    val timeoutSec: Int = 30,
    val followRedirects: Boolean = true,
    val verifySsl: Boolean = true,
    val saveHistory: Boolean = true,
    val clock24: Boolean? = null, // null = follow the phone's 12/24-hour setting
    val dimDashboard: Boolean = true, // lower brightness while the locked dashboard is up
)

@Serializable
data class AppData(
    val collections: List<Collection> = emptyList(),
    val envs: List<Env> = emptyList(),
    val globals: List<KV> = emptyList(),
    val activeEnvId: String? = null,
    val history: List<HistoryEntry> = emptyList(),
    val monitors: List<Monitor> = emptyList(),
    val pin: PinLock? = null,
    val kiosk: Boolean = false, // locked dashboard survives process death, so killing the app doesn't unlock it
    val tabs: List<Req> = listOf(Req()),
    val activeTabId: String? = null,
    val settings: Settings = Settings(),
)

class Resp(
    val code: Int,
    val message: String,
    val headers: List<Pair<String, String>>,
    val body: ByteArray,
    val timeMs: Long,
    val contentType: String,
    val url: String,
    val truncated: Boolean = false,
    val phases: List<Phase> = emptyList(),
) {
    val text: String by lazy { body.toString(Charsets.UTF_8) }
    val size: Long get() = body.size + headers.sumOf { it.first.length + it.second.length + 4L }
}

/** One slice of the request waterfall, in ms from call start. */
class Phase(val name: String, val start: Double, val dur: Double)

val METHODS = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

/** Query string <-> params table, kept in sync like Postman. Values stay unencoded so {{vars}} survive. */
fun parseQuery(url: String): List<KV> {
    val q = url.substringAfter('?', "").substringBefore('#')
    if (q.isEmpty()) return emptyList()
    return q.split('&').filter { it.isNotEmpty() }.map { KV(it.substringBefore('='), it.substringAfter('=', "")) }
}

fun withQuery(url: String, params: List<KV>): String {
    val base = url.substringBefore('?')
    val q = params.filter { it.enabled && it.key.isNotEmpty() }
        .joinToString("&") { if (it.value.isEmpty()) it.key else "${it.key}=${it.value}" }
    return if (q.isEmpty()) base else "$base?$q"
}

fun Req.withUrl(newUrl: String) = copy(url = newUrl, params = parseQuery(newUrl) + params.filter { !it.enabled })
fun Req.withParams(newParams: List<KV>) = copy(params = newParams, url = withQuery(url, newParams))

/** Content equality ignoring identity/location, used for the unsaved-changes dot. */
fun Req.sameContent(o: Req) = copy(id = "", collectionId = null) == o.copy(id = "", collectionId = null)

class MonitorStats(val avg: Long, val p95: Long, val min: Long, val max: Long, val uptime: Double, val up: Boolean, val since: Long)

/** Latency stats over successful checks; uptime and the current up/down streak over all checks. */
fun stats(s: List<Sample>): MonitorStats? {
    if (s.isEmpty()) return null
    val ok = s.filter { it.up }.map { it.ms }.sorted()
    val last = s.last().up
    val since = s.indexOfLast { it.up != last }.let { s[it + 1].at }
    return MonitorStats(
        avg = if (ok.isEmpty()) 0 else ok.average().toLong(),
        p95 = if (ok.isEmpty()) 0 else ok[(Math.ceil(ok.size * .95).toInt() - 1).coerceIn(0, ok.lastIndex)], // nearest-rank
        min = ok.firstOrNull() ?: 0, max = ok.lastOrNull() ?: 0,
        uptime = s.count { it.up } * 100.0 / s.size, up = last, since = since,
    )
}

fun fmtDuration(ms: Long): String {
    val sec = ms / 1000
    return when {
        sec < 60 -> "${sec}s"
        sec < 3600 -> "${sec / 60}m ${sec % 60}s"
        else -> "${sec / 3600}h ${sec % 3600 / 60}m"
    }
}
