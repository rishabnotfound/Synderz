package com.rishabnotfound.synderz

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoreTest {
    @Test fun curlChromeStyle() {
        val r = parseCurl(
            """curl 'https://api.x.io/v1/users?page=2' \
              -H 'authorization: Bearer abc' -H "Content-Type: application/json" \
              --data-raw $'{"name":"it\'s"}' --compressed""",
        )
        assertEquals("POST", r.method)
        assertEquals(AuthType.BEARER, r.auth.type)
        assertEquals("abc", r.auth.token)
        assertEquals(listOf(KV("page", "2")), r.params)
        assertEquals(BodyType.RAW, r.bodyType)
        assertEquals(RawType.JSON, r.rawType)
        assertEquals("it's", kotlinx.serialization.json.Json.parseToJsonElement(r.rawBody).let { (it as kotlinx.serialization.json.JsonObject)["name"].toString().trim('"') })
    }

    @Test fun curlFormAndBasic() {
        val r = parseCurl("curl -XPUT -u bob:s3cr:et -d 'a=1&b=x%20y' http://h/p")
        assertEquals("PUT", r.method)
        assertEquals("s3cr:et", r.auth.password)
        assertEquals(listOf(KV("a", "1"), KV("b", "x y")), r.form)
        assertEquals(BodyType.MULTIPART, parseCurl("curl -F f=@a.png -F n=1 h").bodyType)
    }

    @Test fun curlRoundTrip() {
        val req = Req(method = "PATCH", url = "https://h/{{p}}", headers = listOf(KV("X-A", "it's")), bodyType = BodyType.RAW, rawBody = "{\"a\":1}")
        val back = parseCurl(toCurl(req, mapOf("p" to "items")))
        assertEquals("PATCH", back.method)
        assertEquals("https://h/items", back.url)
        assertEquals("it's", back.headers.first { it.key == "X-A" }.value)
    }

    @Test fun querySync() {
        val r = Req().withUrl("http://h/p?a=1&b={{v}}")
        assertEquals(listOf(KV("a", "1"), KV("b", "{{v}}")), r.params)
        val r2 = r.withParams(r.params.map { if (it.key == "a") it.copy(enabled = false) else it })
        assertEquals("http://h/p?b={{v}}", r2.url)
        assertEquals(3, r2.withUrl("http://h/p?b={{v}}&c").params.size) // disabled "a" survives, "c" added
    }

    @Test fun variables() {
        assertEquals("http://x/1/{{nope}}", resolve("http://{{ host }}/{{id}}/{{nope}}", mapOf("host" to "x", "id" to "1")))
        assertEquals(36, resolve("{{\$guid}}", emptyMap()).length)
        val d = AppData(
            globals = listOf(KV("a", "g"), KV("b", "g")),
            collections = listOf(Collection("c", "C", variables = listOf(KV("b", "c"), KV("c", "c")))),
            envs = listOf(Env("e", "E", listOf(KV("c", "e")))), activeEnvId = "e",
        )
        assertEquals(mapOf("a" to "g", "b" to "c", "c" to "e"), scope(d, Req(collectionId = "c")))
    }

    @Test fun postmanRoundTrip() {
        val c = Collection(
            name = "Demo", variables = listOf(KV("base", "http://h")),
            requests = listOf(
                Req(name = "Create", method = "POST", url = "{{base}}/u?x=1", params = listOf(KV("x", "1"), KV("off", "0", false)),
                    headers = listOf(KV("H", "v")), bodyType = BodyType.RAW, rawBody = "{}", auth = Auth(AuthType.API_KEY, key = "k", value = "v", inQuery = true)),
                Req(name = "Gql", method = "POST", url = "{{base}}/g", bodyType = BodyType.GRAPHQL, gqlQuery = "{ me { id } }", gqlVariables = "{\"a\":1}"),
            ),
        )
        val back = (importPostman(exportCollection(c)) as Imported.Col).c
        assertEquals(c.name, back.name)
        assertEquals(c.variables, back.variables)
        c.requests.zip(back.requests).forEach { (a, b) -> assertEquals(a.copy(id = "", collectionId = null), b.copy(id = "", collectionId = null)) }
    }

    @Test fun buildRequest() {
        val vars = mapOf("host" to "h.io", "t" to "tok")
        val get = Http.build(Req(url = "{{host}}/a", bodyType = BodyType.RAW, rawBody = "x", auth = Auth(AuthType.BEARER, token = "{{t}}")), vars) { null }
        assertEquals("http://h.io/a", get.url.toString())
        assertEquals("Bearer tok", get.header("Authorization"))
        assertNull(get.body) // GET can't carry a body

        val post = Http.build(Req(method = "POST", url = "h", bodyType = BodyType.GRAPHQL, gqlQuery = "{a}"), vars) { null }
        assertEquals("application/json", post.body!!.contentType().toString())
        assertEquals("""{"query":"{a}","variables":{}}""", Buffer().also { post.body!!.writeTo(it) }.readUtf8())

        val del = Http.build(Req(method = "DELETE", url = "h", headers = listOf(KV("Content-Type", "text/csv")), bodyType = BodyType.RAW, rawBody = "1,2"), vars) { null }
        assertEquals("text/csv", del.body!!.contentType().toString())
        assertEquals("PROPFIND", Http.build(Req(method = "propfind", url = "h"), vars) { null }.method)
    }
}

class FeatureTest {
    @Test fun responseSearch() {
        val lines = com.rishabnotfound.synderz.ui.codeLines("{\n  \"Name\": \"name\"\n}")
        val m = com.rishabnotfound.synderz.ui.findMatches(lines, "NAME")
        assertEquals(2, m.size) // case-insensitive, both hits on line index 1
        assertEquals(listOf(3, 11), m.map { it.start })
        assertEquals(0, com.rishabnotfound.synderz.ui.findMatches(lines, "").size)
        assertEquals(0, com.rishabnotfound.synderz.ui.findMatches(lines, "zz").size) // no false hits
    }

    @Test fun monitorStats() {
        val s = listOf(Sample(0, 200, 100), Sample(1000, 500, 900), Sample(2000, 200, 300), Sample(3000, 204, 200))
        val st = stats(s)!!
        assertEquals(75.0, st.uptime, 0.001)
        assertEquals(200, st.avg) // only successful checks count toward latency
        assertEquals(100, st.min); assertEquals(300, st.max)
        assertEquals(300, st.p95) // nearest-rank: never below what was actually observed at the top
        assertEquals(642, stats(listOf(Sample(0, 200, 171), Sample(1, 200, 642)))!!.p95)
        assertEquals(true, st.up); assertEquals(2000, st.since) // up-streak began after the 500
        assertEquals("2m 5s", fmtDuration(125_000))
    }
}

class PinTest {
    @Test fun hashingAndRedaction() {
        val h = hashPin("1234", "salt")
        assertEquals(h, hashPin("1234", "salt"))
        assertEquals(false, h == hashPin("1234", "other")) // salt matters
        assertEquals(false, h.contains("1234"))
        assertEquals(listOf(30L, 60, 120, 240, 480, 960, 1920, 3600, 3600), (0..8).map(::lockoutSeconds))
        assertEquals("Unable to resolve host \"•••\"", redact("Unable to resolve host \"API.Secret.io\"", "https://api.secret.io/health"))
    }
}

class CheckHostTest {
    @Test fun parsesResultShapes() {
        val raw = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"at1.node.check-host.net":[[1,0.0218920707702637,"OK","200","172.67.187.201"]],
                "in1.node.check-host.net":[null,{"message":"Connect timeout"}],
                "us1.node.check-host.net":null,
                "de1.node.check-host.net":[[0,0.5,"Not Found","404","1.2.3.4"]]}""",
        ) as kotlinx.serialization.json.JsonObject
        val r = CheckHost.parseResults(raw)
        assertEquals(GeoResult(true, 0.0218920707702637, "200", "172.67.187.201", "OK"), r["at1.node.check-host.net"])
        assertEquals(GeoResult(false, message = "Connect timeout"), r["in1.node.check-host.net"])
        assertEquals(null, r["us1.node.check-host.net"]!!.ok) // still pending
        assertEquals("404", r["de1.node.check-host.net"]!!.code)
        assertEquals(false, r["de1.node.check-host.net"]!!.ok)
    }

    @Test fun nodesAndFlags() {
        val n = CheckHost.parseNodes(kotlinx.serialization.json.Json.parseToJsonElement(
            """{"es1.node.check-host.net":["es","Spain","Madrid","78.40.111.151","AS215691"]}""") as kotlinx.serialization.json.JsonObject)
        assertEquals(GeoNode("es1.node.check-host.net", "es", "Spain", "Madrid"), n.single())
        assertEquals("🇩🇪", CheckHost.flag("de"))
        assertEquals("🇬🇧", CheckHost.flag("uk"))
        assertEquals("🏳", CheckHost.flag("??"))
    }
}

class NativeFormatTest {
    @Test fun synderzRoundTripIsLosslessWithFreshIds() {
        val c = Collection(name = "API", variables = listOf(KV("base", "https://x")), requests = listOf(
            Req(name = "Up", method = "PUT", url = "{{base}}/a", bodyType = BodyType.RAW, rawType = RawType.XML, rawBody = "<a/>", collectionId = "old"),
        ))
        val back = (importAny(exportNative(c)) as Imported.Col).c
        assertEquals(false, back.id == c.id)
        assertEquals(back.id, back.requests.single().collectionId)
        assertEquals(c.requests.single().copy(id = "", collectionId = null), back.requests.single().copy(id = "", collectionId = null))
        assertEquals(c.variables, back.variables)
        assertEquals("Env", (importAny(exportNative(Env(name = "Env", vars = listOf(KV("k", "v"))))) as Imported.Environment).e.name)
        assertEquals("Demo", (importAny(exportCollection(Collection(name = "Demo"))) as Imported.Col).c.name) // Postman still accepted
    }

    @Test fun cacheVerdicts() {
        assertEquals("Cloudflare · MISS", cacheVerdict(listOf("CF-Cache-Status" to "miss")))
        assertEquals("CloudFront · HIT", cacheVerdict(listOf("X-Cache" to "Hit from cloudfront", "Via" to "1.1 abc.cloudfront.net (CloudFront)")))
        assertEquals("Fastly · MISS", cacheVerdict(listOf("X-Cache" to "MISS, MISS", "X-Served-By" to "cache-fra-1")))
        assertEquals("cached 42s", cacheVerdict(listOf("Age" to "42")))
        assertEquals("origin", cacheVerdict(listOf("Content-Type" to "text/html")))
    }
}

class ReorderTest {
    @Test fun reordersOnlyThePage() {
        val all = "abcdefg".map { Monitor(id = "$it", name = "$it", req = Req()) }
        // page 2 (f, g) untouched; page 1 slots get the new order
        assertEquals("cadbefg", reorderSlice(all, "cadbe".map { "$it" }).joinToString("") { it.id })
        assertEquals("abcdegf", reorderSlice(all, listOf("g", "f")).joinToString("") { it.id })
    }
}
