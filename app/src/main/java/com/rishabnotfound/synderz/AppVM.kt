package com.rishabnotfound.synderz

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

fun fileName(ctx: Context, uri: String): String = runCatching {
    ctx.contentResolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull() ?: uri.substringAfterLast('/')

private const val MAX_SAMPLES = 360 // 1h at 10s

class AppVM(private val app: Application) : AndroidViewModel(app) {
    private val file = File(app.filesDir, "synderz.json")
    private val json = Json { ignoreUnknownKeys = true }

    val data = MutableStateFlow(load())
    val responses = mutableStateMapOf<String, Result<Resp>>()
    val running = mutableStateMapOf<String, Job>()
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val finished = MutableSharedFlow<Boolean>(extraBufferCapacity = 4) // true = 2xx/3xx, drives haptics

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch { data.drop(1).debounce(400).collect { write(it) } }
    }

    private fun load(): AppData = runCatching { json.decodeFromString<AppData>(file.readText()) }.getOrNull()
        ?.let { if (it.tabs.isEmpty()) it.copy(tabs = listOf(Req())) else it } ?: AppData()

    private suspend fun write(d: AppData) = withContext(Dispatchers.IO) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(AppData.serializer(), d))
        tmp.renameTo(file)
    }

    fun setKiosk(on: Boolean) {
        data.update { it.copy(kiosk = on) }
        flush() // write now, don't wait for the debounce
    }

    fun flush() = viewModelScope.launch { write(data.value) }

    fun toast(msg: String) { messages.tryEmit(msg) }

    // ---------- tabs ----------
    fun activeTab(d: AppData) = d.tabs.find { it.id == d.activeTabId } ?: d.tabs.first()

    fun updateTab(id: String, f: (Req) -> Req) = data.update { d -> d.copy(tabs = d.tabs.map { if (it.id == id) f(it) else it }) }

    fun selectTab(id: String) = data.update { it.copy(activeTabId = id) }

    fun newTab(req: Req = Req()) = data.update { it.copy(tabs = it.tabs + req, activeTabId = req.id) }

    /** Saved requests keep their id in the tab, so re-opening focuses the existing tab. */
    fun open(req: Req) = data.update { d ->
        if (d.tabs.any { it.id == req.id }) d.copy(activeTabId = req.id) else d.copy(tabs = d.tabs + req, activeTabId = req.id)
    }

    fun closeTab(id: String) {
        running.remove(id)?.cancel()
        responses.remove(id)
        data.update { d ->
            val i = d.tabs.indexOfFirst { it.id == id }
            val rest = d.tabs.filter { it.id != id }.ifEmpty { listOf(Req()) }
            d.copy(tabs = rest, activeTabId = if (d.activeTabId == id) rest[(i - 1).coerceIn(0, rest.lastIndex)].id else d.activeTabId)
        }
    }

    fun savedVersion(d: AppData, r: Req) = d.collections.find { it.id == r.collectionId }?.requests?.find { it.id == r.id }

    // ---------- send ----------
    fun send(id: String) {
        val d = data.value
        val req = d.tabs.find { it.id == id } ?: return
        running.remove(id)?.cancel()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val r = Http.execute(req, scope(d, req), d.settings, ::readFile)
                responses[id] = Result.success(r)
                finished.tryEmit(r.code < 400)
                record(req, r.code, r.timeMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                responses[id] = Result.failure(e)
                finished.tryEmit(false)
                record(req, 0, 0)
            } finally {
                if (running[id] === coroutineContext[Job]) running.remove(id)
            }
        }
        running[id] = job
        job.start()
    }

    fun cancel(id: String) { running.remove(id)?.cancel() }

    private fun record(req: Req, status: Int, ms: Long) = data.update { d ->
        if (!d.settings.saveHistory) d
        else d.copy(history = (listOf(HistoryEntry(req.copy(id = newId()), status, ms, System.currentTimeMillis())) + d.history).take(300))
    }

    // ---------- save ----------
    /** Returns false when the request has no home yet and the caller should ask where to save it. */
    fun save(id: String): Boolean {
        val d = data.value
        val r = d.tabs.find { it.id == id } ?: return true
        if (savedVersion(d, r) == null) return false
        data.update { it.copy(collections = it.collections.map { c -> if (c.id == r.collectionId) c.copy(requests = c.requests.map { x -> if (x.id == r.id) r else x }) else c }) }
        toast("Saved")
        return true
    }

    fun saveAs(tabId: String, name: String, collectionId: String?, newCollectionName: String?) {
        val src = data.value.tabs.find { it.id == tabId } ?: return
        val col = collectionId ?: Collection(name = newCollectionName!!.ifBlank { "New Collection" }).also { c -> data.update { it.copy(collections = it.collections + c) } }.id
        val saved = src.copy(id = newId(), name = name, collectionId = col)
        data.update { d ->
            d.copy(
                collections = d.collections.map { if (it.id == col) it.copy(requests = it.requests + saved) else it },
                tabs = d.tabs.map { if (it.id == tabId) saved else it },
                activeTabId = if (d.activeTabId == tabId) saved.id else d.activeTabId,
            )
        }
        responses.remove(tabId)?.let { responses[saved.id] = it }
        toast("Saved to collection")
    }

    // ---------- collections ----------
    fun updateCollection(id: String, f: (Collection) -> Collection) = data.update { d -> d.copy(collections = d.collections.map { if (it.id == id) f(it) else it }) }

    fun addCollection(name: String) = data.update { it.copy(collections = it.collections + Collection(name = name)) }

    fun deleteCollection(id: String) = data.update { d ->
        d.copy(collections = d.collections.filter { it.id != id }, tabs = d.tabs.map { if (it.collectionId == id) it.copy(collectionId = null) else it })
    }

    fun addRequestTo(colId: String) {
        val r = Req(collectionId = colId)
        updateCollection(colId) { it.copy(requests = it.requests + r) }
        open(r)
    }

    fun renameRequest(colId: String, reqId: String, name: String) {
        updateCollection(colId) { c -> c.copy(requests = c.requests.map { if (it.id == reqId) it.copy(name = name) else it }) }
        updateTab(reqId) { it.copy(name = name) }
    }

    fun duplicateRequest(colId: String, req: Req) = updateCollection(colId) { c ->
        val i = c.requests.indexOfFirst { it.id == req.id }
        c.copy(requests = c.requests.toMutableList().apply { add(i + 1, req.copy(id = newId(), name = req.name + " Copy")) })
    }

    fun deleteRequest(colId: String, reqId: String) {
        updateCollection(colId) { c -> c.copy(requests = c.requests.filter { it.id != reqId }) }
        updateTab(reqId) { it.copy(collectionId = null) }
    }

    // ---------- environments ----------
    fun updateEnv(id: String, f: (Env) -> Env) = data.update { d -> d.copy(envs = d.envs.map { if (it.id == id) f(it) else it }) }

    fun addEnv(name: String) = data.update { it.copy(envs = it.envs + Env(name = name)) }

    fun deleteEnv(id: String) = data.update { d -> d.copy(envs = d.envs.filter { it.id != id }, activeEnvId = d.activeEnvId.takeIf { it != id }) }

    fun setActiveEnv(id: String?) = data.update { it.copy(activeEnvId = id) }

    // ---------- monitors ----------
    val samples = mutableStateMapOf<String, List<Sample>>()
    val live = mutableStateMapOf<String, Job>()

    init {
        if (data.value.kiosk) startAll() // came back into a locked dashboard: resume watching
    }

    fun addMonitor(name: String, req: Req, intervalSec: Int): Boolean {
        if (data.value.monitors.size >= MAX_MONITORS) { toast("Up to $MAX_MONITORS monitors"); return false }
        // never default the name to the host: the URL is meant to stay behind the PIN
        val m = Monitor(name = name.ifBlank { "Site ${data.value.monitors.size + 1}" }, req = req.copy(id = newId()), intervalSec = intervalSec)
        data.update { it.copy(monitors = it.monitors + m) }
        startMonitor(m.id)
        return true
    }

    fun updateMonitor(id: String, f: (Monitor) -> Monitor) = data.update { d -> d.copy(monitors = d.monitors.map { if (it.id == id) f(it) else it }) }

    fun deleteMonitor(id: String) {
        stopMonitor(id)
        samples.remove(id)
        data.update { d -> d.copy(monitors = d.monitors.filter { it.id != id }) }
    }

    fun startMonitor(id: String) {
        if (id in live) return
        live[id] = viewModelScope.launch {
            while (true) {
                val d = data.value
                val m = d.monitors.find { it.id == id } ?: break
                val t0 = System.currentTimeMillis()
                val s = try {
                    // a check must never outlive its interval, or probes pile up
                    val r = Http.execute(m.req, scope(d, m.req), d.settings.copy(timeoutSec = m.intervalSec.coerceIn(3, 30)), ::readFile)
                    Sample(t0, r.code, r.timeMs)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Sample(t0, 0, System.currentTimeMillis() - t0, e.message ?: e.javaClass.simpleName)
                }
                addSample(id, s)
                delay((m.intervalSec * 1000L - (System.currentTimeMillis() - t0)).coerceAtLeast(250))
            }
            live.remove(id)
        }
    }

    // ---------- monitor PIN ----------
    sealed interface PinCheck { data object Ok : PinCheck; data class Wrong(val left: Int) : PinCheck; data class Locked(val sec: Long) : PinCheck }

    private var pinFails = 0
    private var lockouts = 0
    private var pinLockedUntil = 0L

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        data.update { it.copy(pin = PinLock(hashPin(pin, salt), salt, pin.length)) }
    }

    /** 5 misses per round; each lockout doubles (30s, 1m, 2m … capped at 1h). There is deliberately no reset path. */
    fun checkPin(pin: String): PinCheck {
        val now = System.currentTimeMillis()
        if (now < pinLockedUntil) return PinCheck.Locked((pinLockedUntil - now + 999) / 1000)
        val lock = data.value.pin ?: return PinCheck.Ok
        if (hashPin(pin, lock.salt) == lock.hash) { pinFails = 0; lockouts = 0; return PinCheck.Ok }
        pinFails++
        if (pinFails >= 5) {
            pinFails = 0
            val sec = lockoutSeconds(lockouts)
            lockouts++
            pinLockedUntil = now + sec * 1000
            return PinCheck.Locked(sec)
        }
        return PinCheck.Wrong(5 - pinFails)
    }

    // ---------- global check (check-host.net) ----------
    val geo = mutableStateMapOf<String, GeoRun>()

    fun runGeo(m: Monitor) {
        if (geo[m.id]?.running == true) return
        geo[m.id] = GeoRun()
        viewModelScope.launch {
            try {
                val target = Http.normalizeUrl(resolve(m.req.url, scope(data.value, m.req)))
                val (id, nodes) = CheckHost.start(target)
                geo[m.id] = GeoRun(id, nodes)
                // nodes report over a few seconds; poll until none are pending (or ~40s)
                for (attempt in 0 until 26) {
                    delay(1500)
                    val r = CheckHost.results(id)
                    geo[m.id] = geo[m.id]!!.copy(results = r)
                    if (nodes.all { r[it.host]?.ok != null }) break
                }
                geo[m.id] = geo[m.id]!!.copy(running = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                geo[m.id] = (geo[m.id] ?: GeoRun()).copy(running = false, error = redact(e.message ?: e.javaClass.simpleName, m.req.url))
            }
        }
    }

    private fun addSample(id: String, s: Sample) {
        val prev = samples[id]?.lastOrNull()
        samples[id] = (samples[id].orEmpty() + s).takeLast(MAX_SAMPLES)
        if (prev != null && prev.up != s.up) finished.tryEmit(s.up) // buzz on going down / recovering
    }

    fun reorderMonitors(ids: List<String>) = data.update { it.copy(monitors = reorderSlice(it.monitors, ids)) }

    // ---------- fresh check: every site at once, past any CDN cache ----------
    data class Fresh(val id: String, val name: String, val running: Boolean = true, val code: Int = 0, val ms: Long = 0, val cache: String = "", val error: String? = null)

    var fresh by mutableStateOf<List<Fresh>?>(null)
        private set

    fun dismissFresh() { fresh = null }

    fun checkNow() {
        val d = data.value
        if (fresh?.any { it.running } == true) return
        fresh = d.monitors.map { Fresh(it.id, it.name) }
        d.monitors.forEach { m ->
            viewModelScope.launch {
                val bust = System.currentTimeMillis().toString()
                // Edges like Cloudflare ignore client no-cache; a unique query string changes the cache key, which they can't ignore.
                val req = m.req.copy(
                    headers = m.req.headers.filterNot { it.key.equals("Cache-Control", true) || it.key.equals("Pragma", true) } +
                        listOf(KV("Cache-Control", "no-cache, no-store, max-age=0"), KV("Pragma", "no-cache")),
                ).withParams(m.req.params + KV("_synderz", bust))
                val t0 = System.currentTimeMillis()
                val result = try {
                    val r = Http.execute(req, scope(d, m.req), d.settings.copy(timeoutSec = 20), ::readFile)
                    addSample(m.id, Sample(t0, r.code, r.timeMs))
                    Fresh(m.id, m.name, false, r.code, r.timeMs, cacheVerdict(r.headers))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val err = e.message ?: e.javaClass.simpleName
                    addSample(m.id, Sample(t0, 0, System.currentTimeMillis() - t0, err))
                    Fresh(m.id, m.name, false, 0, System.currentTimeMillis() - t0, error = err)
                }
                fresh = fresh?.map { if (it.id == m.id) result else it }
            }
        }
    }

    fun stopMonitor(id: String) { live.remove(id)?.cancel() }

    fun startAll() = data.value.monitors.forEach { startMonitor(it.id) }
    fun stopAll() = live.keys.toList().forEach(::stopMonitor)

    // ---------- import / files ----------
    fun importText(text: String) {
        runCatching { importAny(text) }.onSuccess { r ->
            when (r) {
                is Imported.Col -> { data.update { it.copy(collections = it.collections + r.c) }; toast("Imported \"${r.c.name}\" · ${r.c.requests.size} requests") }
                is Imported.Environment -> { data.update { it.copy(envs = it.envs + r.e) }; toast("Imported environment \"${r.e.name}\"") }
            }
        }.onFailure { toast("Import failed: ${it.message}") }
    }

    fun readText(uri: Uri): String? = runCatching { app.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()

    fun writeText(uri: Uri, text: String) = runCatching {
        app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
        toast("Exported")
    }.onFailure { toast("Export failed: ${it.message}") }

    fun keepAccess(uri: Uri) { runCatching { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }

    private fun readFile(uri: String): FilePart? = runCatching {
        val u = Uri.parse(uri)
        val bytes = app.contentResolver.openInputStream(u)!!.use { it.readBytes() }
        FilePart(fileName(app, uri), app.contentResolver.getType(u), bytes)
    }.getOrNull()
}
