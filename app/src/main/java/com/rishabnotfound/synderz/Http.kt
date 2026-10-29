package com.rishabnotfound.synderz

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Cookie
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.coroutineContext

/** Records connection milestones so the response can show a DNS/TCP/TLS/TTFB/download waterfall. */
private class Timer : EventListener() {
    private val t = HashMap<String, Long>()
    private fun mark(k: String) { t[k] = System.nanoTime() }
    override fun callStart(call: Call) = mark("start")
    override fun dnsStart(call: Call, domainName: String) = mark("dns0")
    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) = mark("dns1")
    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) = mark("tcp0")
    override fun secureConnectStart(call: Call) = mark("tls0")
    override fun secureConnectEnd(call: Call, handshake: Handshake?) = mark("tls1")
    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) = mark("tcp1")
    override fun requestHeadersStart(call: Call) = mark("req")
    override fun responseHeadersStart(call: Call) = mark("resp")

    fun phases(end: Long): List<Phase> {
        val s = t["start"] ?: return emptyList()
        fun ms(n: Long) = (n - s) / 1e6
        fun p(name: String, a: String, b: String?, bEnd: Long? = null): Phase? {
            val x = t[a] ?: return null
            val y = bEnd ?: t[b] ?: return null
            return Phase(name, ms(x), ((y - x) / 1e6).coerceAtLeast(0.0))
        }
        return listOfNotNull(
            p("DNS", "dns0", "dns1"),
            p("Connect", "tcp0", if ("tls0" in t) "tls0" else "tcp1"),
            p("TLS", "tls0", "tls1"),
            p("Waiting", "req", "resp"),
            p("Download", "resp", null, end),
        )
    }
}

class FilePart(val name: String, val mime: String?, val bytes: ByteArray)

object Http {
    private const val MAX_BODY = 20L * 1024 * 1024

    // ponytail: session-only cookie jar, persist it if people ask for logged-in sessions across restarts
    private val cookies = mutableListOf<Cookie>()
    private val jar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = synchronized(this@Http.cookies) {
            cookies.forEach { c ->
                this@Http.cookies.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
                this@Http.cookies += c
            }
        }

        override fun loadForRequest(url: HttpUrl) = synchronized(cookies) {
            val now = System.currentTimeMillis()
            cookies.removeAll { it.expiresAt < now }
            cookies.filter { it.matches(url) }
        }
    }

    fun clearCookies() = synchronized(cookies) { cookies.clear() }

    private val base = OkHttpClient.Builder().cookieJar(jar).build()

    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
    }

    private fun client(s: Settings) = base.newBuilder()
        .connectTimeout(s.timeoutSec.toLong(), TimeUnit.SECONDS)
        .readTimeout(s.timeoutSec.toLong(), TimeUnit.SECONDS)
        .writeTimeout(s.timeoutSec.toLong(), TimeUnit.SECONDS)
        .followRedirects(s.followRedirects)
        .followSslRedirects(s.followRedirects)
        .apply {
            if (!s.verifySsl) { // opt-in from Settings, for self-signed dev servers
                val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trustAll), SecureRandom()) }
                sslSocketFactory(ctx.socketFactory, trustAll)
                hostnameVerifier { _, _ -> true }
            }
        }.build()

    private fun List<KV>.on() = filter { it.enabled && it.key.isNotBlank() }

    fun normalizeUrl(u: String): String {
        val t = u.trim()
        return if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(t)) t else "http://$t"
    }

    fun build(req: Req, vars: Map<String, String>, files: (String) -> FilePart?): Request {
        fun r(s: String) = resolve(s, vars)
        val method = req.method.trim().uppercase().ifEmpty { "GET" }

        var url = normalizeUrl(r(req.url)).toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid URL: ${r(req.url).ifBlank { "(empty)" }}")
        val headers = Headers.Builder()
        req.headers.on().forEach { headers.addUnsafeNonAscii(r(it.key).trim(), r(it.value)) }

        val a = req.auth
        when (a.type) {
            AuthType.BEARER -> headers["Authorization"] = "Bearer ${r(a.token)}"
            AuthType.BASIC -> headers["Authorization"] = okhttp3.Credentials.basic(r(a.username), r(a.password))
            AuthType.API_KEY -> if (a.key.isNotBlank()) {
                if (a.inQuery) url = url.newBuilder().addQueryParameter(r(a.key), r(a.value)).build()
                else headers[r(a.key)] = r(a.value)
            }
            AuthType.NONE -> {}
        }
        if (headers["User-Agent"] == null) headers["User-Agent"] = "Synderz/1.0"
        if (headers["Accept"] == null) headers["Accept"] = "*/*"

        val userType = headers["Content-Type"]?.toMediaTypeOrNull()
        val body: RequestBody? = when (req.bodyType) {
            BodyType.NONE -> null
            BodyType.RAW -> r(req.rawBody).toByteArray().toRequestBody(userType ?: req.rawType.mime.toMediaTypeOrNull())
            BodyType.FORM -> FormBody.Builder().apply { req.form.on().forEach { add(r(it.key), r(it.value)) } }.build()
            BodyType.MULTIPART -> {
                val parts = req.multipart.on()
                if (parts.isEmpty()) ByteArray(0).toRequestBody(null)
                else MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                    parts.forEach { p ->
                        if (p.isFile) {
                            val f = files(p.value) ?: throw IllegalArgumentException("Can't read file for form field \"${p.key}\"")
                            addFormDataPart(r(p.key), f.name, f.bytes.toRequestBody(f.mime?.toMediaTypeOrNull()))
                        } else addFormDataPart(r(p.key), r(p.value))
                    }
                }.build().also { headers.removeAll("Content-Type") } // boundary must come from the body
            }
            BodyType.BINARY -> {
                val f = if (req.binaryUri.isEmpty()) null else files(req.binaryUri) ?: throw IllegalArgumentException("Can't read selected file")
                (f?.bytes ?: ByteArray(0)).toRequestBody(userType ?: f?.mime?.toMediaTypeOrNull())
            }
            BodyType.GRAPHQL -> {
                val vs = r(req.gqlVariables).trim()
                val json = buildJsonObject {
                    put("query", r(req.gqlQuery))
                    put("variables", if (vs.isEmpty()) JsonObject(emptyMap()) else runCatching { Json.parseToJsonElement(vs) }
                        .getOrElse { throw IllegalArgumentException("GraphQL variables are not valid JSON") })
                }
                json.toString().toByteArray().toRequestBody(userType ?: "application/json".toMediaTypeOrNull())
            }
        }
        // OkHttp forbids bodies on GET/HEAD and requires one on POST/PUT/PATCH.
        val finalBody = when {
            method == "GET" || method == "HEAD" -> null
            body == null && method in setOf("POST", "PUT", "PATCH", "PROPPATCH", "REPORT") -> ByteArray(0).toRequestBody(null)
            else -> body
        }
        return Request.Builder().url(url).headers(headers.build()).method(method, finalBody).build()
    }

    suspend fun execute(req: Req, vars: Map<String, String>, settings: Settings, files: (String) -> FilePart?): Resp =
        withContext(Dispatchers.IO) {
            val timer = Timer()
            val call = client(settings).newBuilder().eventListener(timer).build().newCall(build(req, vars, files))
            val handle = coroutineContext.job.invokeOnCompletion { call.cancel() }
            val start = System.nanoTime()
            try {
                call.execute().use { r ->
                    val src = r.body!!.source()
                    src.request(MAX_BODY + 1)
                    val truncated = src.buffer.size > MAX_BODY
                    val bytes = src.buffer.readByteArray(minOf(src.buffer.size, MAX_BODY))
                    val end = System.nanoTime()
                    Resp(
                        code = r.code,
                        message = r.message.ifEmpty { statusText(r.code) },
                        headers = r.headers.map { it.first to it.second },
                        body = bytes,
                        timeMs = (end - start) / 1_000_000,
                        contentType = r.header("Content-Type").orEmpty(),
                        url = r.request.url.toString(),
                        truncated = truncated,
                        phases = timer.phases(end),
                    )
                }
            } finally {
                handle.dispose()
            }
        }

    fun statusText(code: Int) = when (code) {
        200 -> "OK"; 201 -> "Created"; 202 -> "Accepted"; 204 -> "No Content"
        301 -> "Moved Permanently"; 302 -> "Found"; 304 -> "Not Modified"; 307 -> "Temporary Redirect"; 308 -> "Permanent Redirect"
        400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"; 404 -> "Not Found"; 405 -> "Method Not Allowed"
        409 -> "Conflict"; 415 -> "Unsupported Media Type"; 422 -> "Unprocessable Entity"; 429 -> "Too Many Requests"
        500 -> "Internal Server Error"; 502 -> "Bad Gateway"; 503 -> "Service Unavailable"; 504 -> "Gateway Timeout"
        else -> ""
    }
}

/**
 * What the cache layer in front of the origin said about this response.
 * Covers Cloudflare, Fastly/Varnish, CloudFront, Vercel, Netlify-style RFC 9211 Cache-Status, and plain Age.
 */
fun cacheVerdict(headers: List<Pair<String, String>>): String {
    fun h(name: String) = headers.lastOrNull { it.first.equals(name, true) }?.second?.trim()
    h("cf-cache-status")?.let { return "Cloudflare · ${it.uppercase()}" }
    h("x-vercel-cache")?.let { return "Vercel · ${it.uppercase()}" }
    h("x-cache")?.let { v ->
        val via = (h("via").orEmpty() + " " + h("x-served-by").orEmpty()).lowercase()
        val who = when {
            "cloudfront" in via || h("x-amz-cf-id") != null -> "CloudFront"
            "varnish" in via || "cache-" in via || h("x-fastly-request-id") != null -> "Fastly"
            else -> "CDN"
        }
        return "$who · ${v.substringBefore(' ').substringBefore(',').uppercase()}"
    }
    h("cache-status")?.let { v -> return "Cache · " + if (";hit" in v.lowercase() || v.lowercase().endsWith("hit")) "HIT" else "MISS" }
    h("age")?.toLongOrNull()?.let { if (it > 0) return "cached ${it}s" }
    return "origin"
}
