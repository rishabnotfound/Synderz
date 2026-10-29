package com.rishabnotfound.synderz.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rishabnotfound.synderz.AppData
import com.rishabnotfound.synderz.AppVM
import com.rishabnotfound.synderz.Collection
import com.rishabnotfound.synderz.Http
import com.rishabnotfound.synderz.KV
import com.rishabnotfound.synderz.Req
import com.rishabnotfound.synderz.SYNDERZ_EXT
import com.rishabnotfound.synderz.exportCollection
import com.rishabnotfound.synderz.exportNative
import com.rishabnotfound.synderz.exportEnv
import com.rishabnotfound.synderz.newId
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Shared plumbing: one dialog slot + Postman JSON import/export via the system file picker. */
private class Io(val vm: AppVM) {
    var dialog by mutableStateOf<(@Composable () -> Unit)?>(null)
    var pending = ""
}

@Composable
private fun rememberIo(vm: AppVM): Triple<Io, (String, String) -> Unit, () -> Unit> {
    val io = remember { Io(vm) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? -> uri?.let { vm.writeText(it, io.pending) } }
    // octet-stream, or the system picker "helpfully" turns name.synderz into name.synderz.json
    val nativeExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? -> uri?.let { vm.writeText(it, io.pending) } }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { u -> vm.readText(u)?.let(vm::importText) ?: vm.toast("Couldn't read file") }
    }
    io.dialog?.invoke()
    return Triple(
        io,
        { name, text -> io.pending = text; (if (name.endsWith(".$SYNDERZ_EXT")) nativeExporter else exporter).launch(name) },
        { importer.launch(arrayOf("*/*")) }, // .synderz has no registered MIME type, so don't filter it out
    )
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) = Column(
    modifier.fillMaxWidth().surface(RoundedCornerShape(16.dp)).animateContentSize(spring(dampingRatio = .85f, stiffness = 500f)),
) { content() }

@Composable
private fun Chevron(open: Boolean) {
    val r by animateFloatAsState(if (open) 90f else 0f, label = "chev")
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.Faint, modifier = Modifier.size(20.dp).rotate(r))
}

@Composable
fun KvDialog(title: String, items: List<KV>, onDismiss: () -> Unit, onSave: (List<KV>) -> Unit) {
    var v by remember { mutableStateOf(items) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.Surface2, shape = RoundedCornerShape(20.dp),
        title = { Text(title, fontSize = 18.sp) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { KvEditor(v, { v = it }, null, "Variable") } },
        confirmButton = { TextButton({ onSave(v.filter { it.key.isNotBlank() }) }) { Text("Save", color = C.Green) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = C.Muted) } },
    )
}

// ---------- Collections ----------

@Composable
fun CollectionsScreen(vm: AppVM, d: AppData, onOpen: (Req) -> Unit) {
    val (io, export, import) = rememberIo(vm)
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    val close = { io.dialog = null }
    val q = query.trim()
    val shown = if (q.isEmpty()) d.collections else d.collections.mapNotNull { c ->
        val hits = c.requests.filter { it.name.contains(q, true) || it.url.contains(q, true) }
        if (c.name.contains(q, true)) c else if (hits.isNotEmpty()) c.copy(requests = hits) else null
    }
    val newCollection = { io.dialog = { TextDialog("New collection", confirm = "Create", onDismiss = close) { vm.addCollection(it); close() } } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Collections", "${d.collections.size} collections · ${d.collections.sumOf { it.requests.size }} requests") {
            IconBtn(Icons.Rounded.FileDownload, "Import", onClick = import)
            IconBtn(Icons.Rounded.CreateNewFolder, "New collection", tint = C.Green, onClick = newCollection)
        }
        if (d.collections.isEmpty()) {
            EmptyState(Icons.Rounded.FolderOpen, "No collections yet", "Group requests, share {{variables}}, or import a Postman v2.1 collection.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("New collection", true, onClick = newCollection)
                    Chip("Import", true, C.Blue, onClick = import)
                }
            }
            return@Column
        }
        Field(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), "Search requests", bg = C.Surface) {
            Icon(Icons.Rounded.Search, null, tint = C.Faint, modifier = Modifier.size(18.dp))
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { it.id }) { c ->
                val open = c.id in expanded || q.isNotEmpty()
                Card(Modifier.animateItem()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { expanded = if (c.id in expanded) expanded - c.id else expanded + c.id }.padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Chevron(open)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Folder, null, tint = C.Blue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${c.requests.size} requests · ${c.variables.size} variables", color = C.Faint, fontSize = 12.sp)
                        }
                        CollectionMenu(vm, d.collections.first { it.id == c.id }, io, export)
                    }
                    if (open) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
                        c.requests.forEach { r -> RequestRow(vm, c, r, io) { onOpen(r) } }
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.addRequestTo(c.id); onOpen(vm.data.value.tabs.last()) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = C.Green, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp)); Text("Add request", color = C.Green, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionMenu(vm: AppVM, c: Collection, io: Io, export: (String, String) -> Unit) {
    val close = { io.dialog = null }
    MenuButton(
        listOf(
            MenuItem("Add request", Icons.Rounded.Add) { vm.addRequestTo(c.id) },
            MenuItem("Variables", Icons.Rounded.Tune) {
                io.dialog = { KvDialog("${c.name} variables", c.variables, close) { v -> vm.updateCollection(c.id) { it.copy(variables = v) }; close() } }
            },
            MenuItem("Rename", Icons.Rounded.DriveFileRenameOutline) {
                io.dialog = { TextDialog("Rename collection", c.name, onDismiss = close) { n -> vm.updateCollection(c.id) { it.copy(name = n) }; close() } }
            },
            MenuItem("Duplicate", Icons.Rounded.ContentCopy) {
                val id = newId()
                vm.data.update { d -> d.copy(collections = d.collections + c.copy(id = id, name = c.name + " Copy", requests = c.requests.map { it.copy(id = newId(), collectionId = id) })) }
            },
            MenuItem("Export .synderz", Icons.Rounded.FileUpload) { export("${c.name}.$SYNDERZ_EXT", exportNative(c)) },
            MenuItem("Export for Postman", Icons.Rounded.FileUpload) { export("${c.name}.postman_collection.json", exportCollection(c)) },
            MenuItem("Delete", Icons.Rounded.Delete, C.Red) {
                io.dialog = { ConfirmDialog("Delete \"${c.name}\"?", "${c.requests.size} requests will be removed. This can't be undone.", onDismiss = close) { vm.deleteCollection(c.id) } }
            },
        ),
    ) { open -> IconBtn(Icons.Rounded.MoreVert, "Collection actions", onClick = open) }
}

@Composable
private fun RequestRow(vm: AppVM, c: Collection, r: Req, io: Io, onClick: () -> Unit) {
    val close = { io.dialog = null }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        MethodTag(r.method)
        Column(Modifier.weight(1f)) {
            Text(r.name, color = C.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (r.url.isNotEmpty()) Text(r.url, color = C.Faint, fontSize = 11.sp, fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MenuButton(
            listOf(
                MenuItem("Rename", Icons.Rounded.DriveFileRenameOutline) {
                    io.dialog = { TextDialog("Rename request", r.name, onDismiss = close) { n -> vm.renameRequest(c.id, r.id, n); close() } }
                },
                MenuItem("Duplicate", Icons.Rounded.ContentCopy) { vm.duplicateRequest(c.id, r) },
                MenuItem("Delete", Icons.Rounded.Delete, C.Red) { vm.deleteRequest(c.id, r.id) },
            ),
        ) { open -> IconBtn(Icons.Rounded.MoreVert, "Request actions", tint = C.Faint, onClick = open) }
    }
}

// ---------- History ----------

private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

@Composable
fun HistoryScreen(vm: AppVM, d: AppData, onOpen: (Req) -> Unit) {
    val (io) = rememberIo(vm)
    val clock = rememberClock(seconds = false)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("History", "${d.history.size} requests sent") {
            if (d.history.isNotEmpty()) IconBtn(Icons.Rounded.DeleteSweep, "Clear history") {
                io.dialog = { ConfirmDialog("Clear history?", "All ${d.history.size} entries will be removed.", "Clear", { io.dialog = null }) { vm.data.update { it.copy(history = emptyList()) } } }
            }
        }
        if (d.history.isEmpty()) {
            EmptyState(Icons.Rounded.History, "No history yet", "Every request you send lands here, tap one to reopen it.")
            return@Column
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val groups = d.history.groupBy { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            groups.forEach { (day, list) ->
                item(key = day.toString()) {
                    Label(when (day) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> day.format(dayFmt) })
                }
                items(list, key = { it.req.id }) { e ->
                    Row(
                        Modifier.animateItem().fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(e.req.copy(id = newId())) }.padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MethodTag(e.req.method)
                        Column(Modifier.weight(1f)) {
                            Text(e.req.url.ifEmpty { e.req.name }, color = C.Text, fontSize = 13.sp, fontFamily = Mono, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                clock.format(java.util.Date(e.at)) + (if (e.status > 0) " · ${e.timeMs} ms" else ""),
                                color = C.Faint, fontSize = 11.sp,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Pill(if (e.status > 0) e.status.toString() else "ERR", statusColor(e.status))
                    }
                }
            }
        }
    }
}

// ---------- Environments ----------

@Composable
fun EnvironmentsScreen(vm: AppVM, d: AppData) {
    val (io, export, import) = rememberIo(vm)
    var expanded by remember { mutableStateOf(setOf<String>()) }
    val close = { io.dialog = null }
    fun toggle(id: String) { expanded = if (id in expanded) expanded - id else expanded + id }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Environments", d.envs.find { it.id == d.activeEnvId }?.let { "Active: ${it.name}" } ?: "None active") {
            IconBtn(Icons.Rounded.FileDownload, "Import", onClick = import)
            IconBtn(Icons.Rounded.Add, "New environment", tint = C.Green) {
                io.dialog = { TextDialog("New environment", confirm = "Create", onDismiss = close) { vm.addEnv(it); close() } }
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "globals") {
                EnvCard(Icons.Rounded.Public, C.Blue, "Globals", "${d.globals.size} variables · lowest precedence", "globals" in expanded, { toggle("globals") }, null) {
                    KvEditor(d.globals, { v -> vm.data.update { it.copy(globals = v) } }, null, "Variable")
                }
            }
            if (d.envs.isEmpty()) item(key = "hint") {
                Text(
                    "Create an environment (Local, Staging, Prod…) and reference its values anywhere as {{name}}. Switch the active one from the top bar.",
                    color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(8.dp),
                )
            }
            items(d.envs, key = { it.id }) { e ->
                val active = e.id == d.activeEnvId
                EnvCard(
                    if (active) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, if (active) C.Green else C.Faint,
                    e.name, if (active) "Active · ${e.vars.size} variables" else "${e.vars.size} variables", e.id in expanded, { toggle(e.id) },
                    onIcon = { vm.setActiveEnv(if (active) null else e.id) },
                    menu = {
                        MenuButton(
                            listOf(
                                MenuItem(if (active) "Deactivate" else "Set active", Icons.Rounded.CheckCircle) { vm.setActiveEnv(if (active) null else e.id) },
                                MenuItem("Rename", Icons.Rounded.DriveFileRenameOutline) {
                                    io.dialog = { TextDialog("Rename environment", e.name, onDismiss = close) { n -> vm.updateEnv(e.id) { it.copy(name = n) }; close() } }
                                },
                                MenuItem("Duplicate", Icons.Rounded.ContentCopy) { vm.data.update { it.copy(envs = it.envs + e.copy(id = newId(), name = e.name + " Copy")) } },
                                MenuItem("Export .synderz", Icons.Rounded.FileUpload) { export("${e.name}.$SYNDERZ_EXT", exportNative(e)) },
                                MenuItem("Export for Postman", Icons.Rounded.FileUpload) { export("${e.name}.postman_environment.json", exportEnv(e)) },
                                MenuItem("Delete", Icons.Rounded.Delete, C.Red) {
                                    io.dialog = { ConfirmDialog("Delete \"${e.name}\"?", "Its ${e.vars.size} variables will be removed.", onDismiss = close) { vm.deleteEnv(e.id) } }
                                },
                            ),
                        ) { open -> IconBtn(Icons.Rounded.MoreVert, "Environment actions", onClick = open) }
                    },
                ) {
                    KvEditor(e.vars, { v -> vm.updateEnv(e.id) { it.copy(vars = v) } }, null, "Variable")
                }
            }
        }
    }
}

@Composable
private fun EnvCard(
    icon: ImageVector, tint: androidx.compose.ui.graphics.Color, title: String, sub: String, open: Boolean, onToggle: () -> Unit,
    menu: (@Composable () -> Unit)?, onIcon: (() -> Unit)? = null, content: @Composable () -> Unit,
) = Card {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = if (menu == null) 12.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).clickable(enabled = onIcon != null) { onIcon?.invoke() }, Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = C.Faint, fontSize = 12.sp)
        }
        menu?.invoke()
        Chevron(open)
        Spacer(Modifier.width(8.dp))
    }
    if (open) Column(Modifier.padding(start = 16.dp, end = 12.dp, bottom = 12.dp)) { content() }
}

// ---------- Settings ----------

@Composable
fun SettingsDialog(vm: AppVM, d: AppData, onDismiss: () -> Unit) {
    val s = d.settings
    fun set(f: (com.rishabnotfound.synderz.Settings) -> com.rishabnotfound.synderz.Settings) = vm.data.update { it.copy(settings = f(it.settings)) }
    @Composable
    fun Toggle(title: String, sub: String, on: Boolean, change: (Boolean) -> Unit) = Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 14.sp)
            Text(sub, color = C.Faint, fontSize = 12.sp)
        }
        Switch(on, change, colors = SwitchDefaults.colors(checkedThumbColor = C.Bg, checkedTrackColor = C.Green, uncheckedTrackColor = C.Surface3, uncheckedBorderColor = C.Line))
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.Surface2, shape = RoundedCornerShape(20.dp),
        title = { Text("Settings", fontSize = 18.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Request timeout", color = C.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("${s.timeoutSec}s", color = C.Green, fontSize = 14.sp, fontFamily = Mono)
                }
                Slider(
                    s.timeoutSec.toFloat(), { v -> set { it.copy(timeoutSec = v.toInt()) } }, valueRange = 1f..300f,
                    colors = SliderDefaults.colors(thumbColor = C.Green, activeTrackColor = C.Green, inactiveTrackColor = C.Surface3),
                )
                Toggle("Follow redirects", "Automatically follow 3xx responses", s.followRedirects) { v -> set { it.copy(followRedirects = v) } }
                Toggle("SSL certificate verification", "Turn off only for self-signed dev servers", s.verifySsl) { v -> set { it.copy(verifySsl = v) } }
                Toggle("Save history", "Keep the last 300 sent requests", s.saveHistory) { v -> set { it.copy(saveHistory = v) } }
                Toggle("Dim locked dashboard", "Saves battery while it sits on a desk", s.dimDashboard) { v -> set { it.copy(dimDashboard = v) } }
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Clock", color = C.Text, fontSize = 14.sp)
                        Text("Dashboard, charts and history", color = C.Faint, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(null to "System", true to "24h", false to "12h").forEach { (v, label) ->
                            Chip(label, s.clock24 == v) { set { it.copy(clock24 = v) } }
                        }
                    }
                }
                TextButton({ Http.clearCookies(); vm.toast("Cookies cleared") }) { Text("Clear cookie jar", color = C.Blue) }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Layers, null, tint = C.Faint, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Variables resolve: environment › collection › globals", color = C.Faint, fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done", color = C.Green) } },
    )
}
