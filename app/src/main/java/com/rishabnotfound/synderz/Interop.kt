package com.rishabnotfound.synderz

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// ---------- cURL ----------

private fun shq(s: String) = "'" + s.replace("'", "'\\''") + "'"

fun toCurl(req: Req, vars: Map<String, String>): String {
    fun r(s: String) = resolve(s, vars)
    val out = mutableListOf("curl", "-X", req.method, shq(Http.normalizeUrl(r(req.url))))
    fun h(k: String, v: String) { out += "-H"; out += shq("$k: $v") }
    req.headers.filter { it.enabled && it.key.isNotBlank() }.forEach { h(r(it.key), r(it.value)) }
    val a = req.auth
    when (a.type) {
        AuthType.BEARER -> h("Authorization", "Bearer ${r(a.token)}")
        AuthType.BASIC -> { out += "-u"; out += shq("${r(a.username)}:${r(a.password)}") }
        AuthType.API_KEY -> if (!a.inQuery && a.key.isNotBlank()) h(r(a.key), r(a.value))
        AuthType.NONE -> {}
    }
    val hasCt = req.headers.any { it.enabled && it.key.equals("content-type", true) }
    when (req.bodyType) {
        BodyType.RAW -> { if (!hasCt) h("Content-Type", req.rawType.mime); out += "--data-raw"; out += shq(r(req.rawBody)) }
        BodyType.FORM -> req.form.filter { it.enabled && it.key.isNotBlank() }.forEach { out += "--data-urlencode"; out += shq("${r(it.key)}=${r(it.value)}") }
        BodyType.MULTIPART -> req.multipart.filter { it.enabled && it.key.isNotBlank() }.forEach {
            out += "-F"; out += shq(if (it.isFile) "${r(it.key)}=@${it.value.substringAfterLast('/')}" else "${r(it.key)}=${r(it.value)}")
        }
        BodyType.BINARY -> { out += "--data-binary"; out += shq("@" + req.binaryUri.substringAfterLast('/')) }
        BodyType.GRAPHQL -> {
            if (!hasCt) h("Content-Type", "application/json")
            val vs = r(req.gqlVariables).trim()
            val body = buildJsonObject {
                put("query", r(req.gqlQuery))
                put("variables", runCatching { Json.parseToJsonElement(vs) }.getOrDefault(JsonObject(emptyMap())))
            }
            out += "--data-raw"; out += shq(body.toString())
        }
        BodyType.NONE -> {}
    }
    return out.joinToString(" ")
}

/** Shell-style word splitting: quotes, backslash escapes, line continuations. */
fun shellSplit(s: String): List<String> {
    val out = mutableListOf<String>()
    val cur = StringBuilder()
    var inWord = false
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c == '\\' && i + 1 < s.length && (s[i + 1] == '\n' || s[i + 1] == '\r') -> { i++; if (s[i] == '\r' && i + 1 < s.length && s[i + 1] == '\n') i++ }
            c.isWhitespace() -> if (inWord) { out += cur.toString(); cur.clear(); inWord = false }
            c == '\'' -> { inWord = true; val e = s.indexOf('\'', i + 1).let { if (it < 0) s.length else it }; cur.append(s, i + 1, e); i = e }
            c == '$' && i + 1 < s.length && s[i + 1] == '\'' -> { // ANSI-C quoting from Chrome's "copy as cURL"
                inWord = true; i += 2
                while (i < s.length && s[i] != '\'') {
                    if (s[i] == '\\' && i + 1 < s.length) {
                        i++; cur.append(when (s[i]) { 'n' -> '\n'; 't' -> '\t'; 'r' -> '\r'; else -> s[i] })
                    } else cur.append(s[i])
                    i++
                }
            }
            c == '"' -> {
                inWord = true; i++
                while (i < s.length && s[i] != '"') {
                    if (s[i] == '\\' && i + 1 < s.length && s[i + 1] in "\"\\$`") i++
                    cur.append(s[i]); i++
                }
            }
            c == '\\' && i + 1 < s.length -> { inWord = true; cur.append(s[i + 1]); i++ }
            else -> { inWord = true; cur.append(c) }
        }
        i++
    }
    if (inWord) out += cur.toString()
    return out
}

fun parseCurl(cmd: String): Req {
    val t = shellSplit(cmd.trim()).toMutableList()
    if (t.firstOrNull() == "curl") t.removeAt(0)
    require(t.isNotEmpty()) { "Not a cURL command" }
    var method: String? = null
    var url = ""
    var get = false
    val headers = mutableListOf<KV>()
    val data = mutableListOf<String>()
    val forms = mutableListOf<KV>()
    var auth = Auth()
    val ignoredWithArg = setOf("-o", "--output", "-m", "--max-time", "--connect-timeout", "-x", "--proxy", "-w", "--write-out", "--retry", "-c", "--cookie-jar", "--cacert", "--cert", "--key")
    var i = 0
    fun next() = t.getOrElse(++i) { "" }
    while (i < t.size) {
        val a = t[i]
        when {
            a == "-X" || a == "--request" -> method = next()
            a.startsWith("-X") && a.length > 2 -> method = a.substring(2)
            a == "-H" || a == "--header" -> next().let { headers += KV(it.substringBefore(':').trim(), it.substringAfter(':', "").trim()) }
            a in setOf("-d", "--data", "--data-raw", "--data-binary", "--data-ascii", "--data-urlencode", "--json") -> {
                data += next()
                if (a == "--json") headers += listOf(KV("Content-Type", "application/json"), KV("Accept", "application/json"))
            }
            a == "-F" || a == "--form" -> next().let {
                val v = it.substringAfter('=', "")
                forms += if (v.startsWith("@")) KV(it.substringBefore('='), "", isFile = true) else KV(it.substringBefore('='), v)
            }
            a == "-u" || a == "--user" -> next().let { auth = Auth(AuthType.BASIC, username = it.substringBefore(':'), password = it.substringAfter(':', "")) }
            a == "-A" || a == "--user-agent" -> headers += KV("User-Agent", next())
            a == "-b" || a == "--cookie" -> headers += KV("Cookie", next())
            a == "-e" || a == "--referer" -> headers += KV("Referer", next())
            a == "-G" || a == "--get" -> get = true
            a == "-I" || a == "--head" -> method = "HEAD"
            a == "--url" -> url = next()
            a in ignoredWithArg -> next()
            a.startsWith("-") -> {} // --compressed, -L, -k, -s, -v, ...
            url.isEmpty() -> url = a
        }
        i++
    }
    require(url.isNotEmpty()) { "No URL found in cURL command" }

    // Lift "Authorization: Bearer x" into the auth tab like Postman does.
    headers.find { it.key.equals("Authorization", true) && it.value.startsWith("Bearer ", true) }?.let {
        headers.remove(it); auth = Auth(AuthType.BEARER, token = it.value.substring(7).trim())
    }

    val joined = data.joinToString("&")
    if (get && data.isNotEmpty()) url += (if ('?' in url) "&" else "?") + joined
    val ct = headers.find { it.key.equals("Content-Type", true) }?.value.orEmpty()
    var req = Req(name = url.substringBefore('?').substringAfter("://").take(60), headers = headers, auth = auth)
    req = when {
        forms.isNotEmpty() -> req.copy(bodyType = BodyType.MULTIPART, multipart = forms)
        data.isEmpty() || get -> req
        ct.contains("json") || joined.trimStart().let { it.startsWith("{") || it.startsWith("[") } ->
            req.copy(bodyType = BodyType.RAW, rawType = RawType.JSON, rawBody = prettyJson(joined) ?: joined)
        ct.contains("x-www-form-urlencoded") || (ct.isEmpty() && Regex("^[^=&\\s]+=[^&]*(&[^=&\\s]+=[^&]*)*$").matches(joined)) ->
            req.copy(bodyType = BodyType.FORM, form = joined.split('&').map {
                KV(java.net.URLDecoder.decode(it.substringBefore('='), "UTF-8"), java.net.URLDecoder.decode(it.substringAfter('=', ""), "UTF-8"))
            })
        ct.contains("xml") -> req.copy(bodyType = BodyType.RAW, rawType = RawType.XML, rawBody = joined)
        else -> req.copy(bodyType = BodyType.RAW, rawType = RawType.TEXT, rawBody = joined)
    }
    return req.copy(method = method?.uppercase() ?: if (data.isNotEmpty() && !get || forms.isNotEmpty()) "POST" else "GET").withUrl(url)
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

fun prettyJson(s: String): String? = runCatching { pretty.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(s)) }.getOrNull()

// ---------- Postman v2.1 ----------

private fun JsonElement?.str() = (this as? JsonPrimitive)?.contentOrNull
private fun JsonObject.s(k: String) = this[k].str().orEmpty()

private fun kvs(e: JsonElement?) = (e as? JsonArray)?.mapNotNull { it as? JsonObject }?.map {
    KV(it.s("key"), it.s("value"), enabled = it["enabled"]?.jsonPrimitive?.booleanOrNull ?: (it["disabled"]?.jsonPrimitive?.booleanOrNull != true), isFile = it.s("type") == "file")
}.orEmpty()

sealed interface Imported {
    data class Col(val c: Collection) : Imported
    data class Environment(val e: Env) : Imported
}

fun importPostman(text: String): Imported {
    val root = Json.parseToJsonElement(text).jsonObject
    if ("values" in root && "item" !in root) {
        return Imported.Environment(Env(name = root.s("name").ifEmpty { "Imported Environment" }, vars = kvs(root["values"])))
    }
    val info = root["info"]?.jsonObject ?: throw IllegalArgumentException("Not a Postman collection or environment")
    val colId = newId()
    val reqs = mutableListOf<Req>()
    fun walk(items: JsonElement?, prefix: String) {
        (items as? JsonArray)?.forEach { el ->
            val it = el.jsonObject
            val name = it.s("name")
            if ("item" in it) { walk(it["item"], "$prefix$name / "); return@forEach }
            val r = it["request"] ?: return@forEach
            reqs += if (r is JsonPrimitive) Req(name = prefix + name, url = r.content, collectionId = colId)
            else parsePostmanRequest(r.jsonObject).copy(name = prefix + name, collectionId = colId)
        }
    }
    walk(root["item"], "")
    return Imported.Col(Collection(id = colId, name = info.s("name").ifEmpty { "Imported" }, requests = reqs, variables = kvs(root["variable"])))
}

private fun parsePostmanRequest(o: JsonObject): Req {
    val u = o["url"]
    val url = (u as? JsonObject)?.s("raw") ?: u.str().orEmpty()
    var req = Req(method = o.s("method").ifEmpty { "GET" }, headers = kvs(o["header"])).withUrl(url)
    // disabled query params only live in the url object
    (u as? JsonObject)?.get("query")?.let { q -> req = req.copy(params = kvs(q)) }

    o["auth"]?.jsonObject?.let { a ->
        fun p(type: String, key: String) = (a[type] as? JsonArray)?.map { it.jsonObject }?.find { it.s("key") == key }?.s("value").orEmpty()
        req = req.copy(auth = when (a.s("type")) {
            "bearer" -> Auth(AuthType.BEARER, token = p("bearer", "token"))
            "basic" -> Auth(AuthType.BASIC, username = p("basic", "username"), password = p("basic", "password"))
            "apikey" -> Auth(AuthType.API_KEY, key = p("apikey", "key"), value = p("apikey", "value"), inQuery = p("apikey", "in") == "query")
            else -> Auth()
        })
    }
    val b = o["body"] as? JsonObject ?: return req
    return when (b.s("mode")) {
        "raw" -> {
            val lang = b["options"]?.jsonObject?.get("raw")?.jsonObject?.s("language").orEmpty()
            val rt = RawType.entries.find { it.name.equals(lang, true) } ?: if (lang.isEmpty() && b.s("raw").trimStart().startsWith("{")) RawType.JSON else RawType.TEXT
            req.copy(bodyType = BodyType.RAW, rawType = rt, rawBody = b.s("raw"))
        }
        "urlencoded" -> req.copy(bodyType = BodyType.FORM, form = kvs(b["urlencoded"]))
        "formdata" -> req.copy(bodyType = BodyType.MULTIPART, multipart = kvs(b["formdata"]).map { if (it.isFile) it.copy(value = "") else it })
        "file" -> req.copy(bodyType = BodyType.BINARY)
        "graphql" -> b["graphql"]?.jsonObject.let { g -> req.copy(bodyType = BodyType.GRAPHQL, gqlQuery = g?.s("query").orEmpty(), gqlVariables = g?.s("variables").orEmpty()) }
        else -> req
    }
}

private fun kvJson(list: List<KV>) = buildJsonArray {
    list.forEach { addJsonObject { put("key", it.key); put("value", it.value); if (!it.enabled) put("disabled", true); if (it.isFile) put("type", "file") } }
}

fun exportCollection(c: Collection): String = pretty.encodeToString(JsonElement.serializer(), buildJsonObject {
    putJsonObject("info") {
        put("_postman_id", c.id); put("name", c.name)
        put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json")
    }
    putJsonArray("item") {
        c.requests.forEach { r ->
            addJsonObject {
                put("name", r.name)
                putJsonObject("request") {
                    put("method", r.method)
                    put("header", kvJson(r.headers))
                    putJsonObject("url") { put("raw", r.url); put("query", kvJson(r.params)) }
                    val a = r.auth
                    fun authOf(type: String, vararg kv: Pair<String, String>) = putJsonObject("auth") {
                        put("type", type)
                        putJsonArray(type) { kv.forEach { (k, v) -> addJsonObject { put("key", k); put("value", v); put("type", "string") } } }
                    }
                    when (a.type) {
                        AuthType.BEARER -> authOf("bearer", "token" to a.token)
                        AuthType.BASIC -> authOf("basic", "username" to a.username, "password" to a.password)
                        AuthType.API_KEY -> authOf("apikey", "key" to a.key, "value" to a.value, "in" to if (a.inQuery) "query" else "header")
                        AuthType.NONE -> {}
                    }
                    when (r.bodyType) {
                        BodyType.RAW -> putJsonObject("body") {
                            put("mode", "raw"); put("raw", r.rawBody)
                            putJsonObject("options") { putJsonObject("raw") { put("language", r.rawType.name.lowercase()) } }
                        }
                        BodyType.FORM -> putJsonObject("body") { put("mode", "urlencoded"); put("urlencoded", kvJson(r.form)) }
                        BodyType.MULTIPART -> putJsonObject("body") { put("mode", "formdata"); put("formdata", kvJson(r.multipart)) }
                        BodyType.BINARY -> putJsonObject("body") { put("mode", "file"); putJsonObject("file") {} }
                        BodyType.GRAPHQL -> putJsonObject("body") {
                            put("mode", "graphql"); putJsonObject("graphql") { put("query", r.gqlQuery); put("variables", r.gqlVariables) }
                        }
                        BodyType.NONE -> {}
                    }
                }
            }
        }
    }
    put("variable", kvJson(c.variables))
})

fun exportEnv(e: Env): String = pretty.encodeToString(JsonElement.serializer(), buildJsonObject {
    put("id", e.id); put("name", e.name); put("values", kvJson(e.vars))
    put("_postman_variable_scope", "environment")
})


// ---------- .synderz (native, lossless) ----------

const val SYNDERZ_EXT = "synderz"

/** Our own file: everything we model, nothing lost in translation. `synderz` is the format version. */
@kotlinx.serialization.Serializable
class SynderzFile(val synderz: Int = 1, val kind: String, val collection: Collection? = null, val environment: Env? = null)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
private val native = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true; prettyPrintIndent = "  " } // defaults on: the "synderz" marker is one

fun exportNative(c: Collection): String = native.encodeToString(SynderzFile.serializer(), SynderzFile(kind = "collection", collection = c))
fun exportNative(e: Env): String = native.encodeToString(SynderzFile.serializer(), SynderzFile(kind = "environment", environment = e))

/** Accepts .synderz or Postman v2.1 collection/environment JSON. Imports always get fresh ids so nothing collides. */
fun importAny(text: String): Imported {
    val root = Json.parseToJsonElement(text).jsonObject
    if ("synderz" !in root) return importPostman(text)
    val f = native.decodeFromJsonElement(SynderzFile.serializer(), root)
    require(f.synderz <= 1) { "Made by a newer Synderz (format ${f.synderz}), update the app" }
    return when {
        f.kind == "collection" && f.collection != null -> {
            val id = newId()
            Imported.Col(f.collection.copy(id = id, requests = f.collection.requests.map { it.copy(id = newId(), collectionId = id) }))
        }
        f.kind == "environment" && f.environment != null -> Imported.Environment(f.environment.copy(id = newId()))
        else -> throw IllegalArgumentException("Unknown .synderz content: ${f.kind}")
    }
}
