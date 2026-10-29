package com.rishabnotfound.synderz.ui

import androidx.compose.ui.platform.LocalConfiguration
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rishabnotfound.synderz.AppData
import com.rishabnotfound.synderz.AppVM
import com.rishabnotfound.synderz.Auth
import com.rishabnotfound.synderz.AuthType
import com.rishabnotfound.synderz.BodyType
import com.rishabnotfound.synderz.METHODS
import com.rishabnotfound.synderz.RawType
import com.rishabnotfound.synderz.Req
import com.rishabnotfound.synderz.fileName
import com.rishabnotfound.synderz.newId
import com.rishabnotfound.synderz.parseCurl
import com.rishabnotfound.synderz.prettyJson
import com.rishabnotfound.synderz.sameContent
import com.rishabnotfound.synderz.scope
import com.rishabnotfound.synderz.toCurl
import com.rishabnotfound.synderz.withParams
import com.rishabnotfound.synderz.withUrl
import kotlinx.coroutines.launch

@Composable
fun WorkspaceScreen(vm: AppVM, d: AppData) {
    val ctx = LocalContext.current
    val tab = vm.activeTab(d)
    val saved = vm.savedVersion(d, tab)
    var dialog by remember { mutableStateOf<String?>(null) }
    fun set(f: (Req) -> Req) = vm.updateTab(tab.id, f)

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TabStrip(vm, d, tab.id, Modifier.weight(1f)) { dialog = "rename" }
            Box {
                IconBtn(Icons.Rounded.Save, "Save", tint = if (saved == null || !saved.sameContent(tab)) C.Text else C.Faint) {
                    if (!vm.save(tab.id)) dialog = "saveas"
                }
                val dot by animateFloatAsState(if (saved != null && !saved.sameContent(tab)) 1f else 0f, spring(dampingRatio = .5f), label = "dirty")
                Box(Modifier.align(Alignment.TopEnd).offset((-8).dp, 8.dp).size(7.dp).graphicsLayer { scaleX = dot; scaleY = dot }.clip(CircleShape).background(C.Amber))
            }
            MenuButton(
                listOf(
                    MenuItem("Save as…", Icons.Rounded.SaveAs) { dialog = "saveas" },
                    MenuItem("Rename", Icons.Rounded.DriveFileRenameOutline) { dialog = "rename" },
                    MenuItem("Duplicate tab", Icons.Rounded.Tab) { vm.newTab(tab.copy(id = newId(), name = tab.name + " Copy")) },
                    MenuItem("Monitor this endpoint", Icons.Rounded.MonitorHeart) { if (vm.addMonitor(tab.name, tab, 10)) vm.toast("Monitoring \"${tab.name}\" every 10s") },
                    MenuItem("Import cURL", Icons.Rounded.Input) { dialog = "curl" },
                    MenuItem("Copy as cURL", Icons.Rounded.ContentCopy) { copyText(ctx, toCurl(tab, scope(d, tab))); vm.toast("cURL copied") },
                    MenuItem("Close tab", Icons.Rounded.Close) { vm.closeTab(tab.id) },
                ),
            ) { open -> IconBtn(Icons.Rounded.MoreVert, "More", onClick = open) }
            Spacer(Modifier.width(4.dp))
        }
        Crossfade(tab.id, animationSpec = tween(160), label = "tab") { id ->
            val t = d.tabs.find { it.id == id } ?: return@Crossfade
            RequestPane(vm, d, t)
        }
    }

    when (dialog) {
        "rename" -> TextDialog("Rename request", tab.name, onDismiss = { dialog = null }) { n ->
            if (saved != null) vm.renameRequest(tab.collectionId!!, tab.id, n) else set { it.copy(name = n) }
            dialog = null
        }
        "saveas" -> SaveDialog(d, tab, onDismiss = { dialog = null }) { name, colId, newCol -> vm.saveAs(tab.id, name, colId, newCol); dialog = null }
        "curl" -> CurlDialog(onDismiss = { dialog = null }) { cmd ->
            runCatching { parseCurl(cmd) }
                .onSuccess { p -> set { r -> p.copy(id = r.id, name = r.name, collectionId = r.collectionId) }; vm.toast("cURL imported"); dialog = null }
                .onFailure { vm.toast(it.message ?: "Invalid cURL") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabStrip(vm: AppVM, d: AppData, activeId: String, modifier: Modifier, onRename: () -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(activeId, d.tabs.size) {
        val i = d.tabs.indexOfFirst { it.id == activeId }
        if (i >= 0) state.animateScrollToItem(i)
    }
    LazyRow(
        modifier, state = state, contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        items(d.tabs, key = { it.id }) { t ->
            val on = t.id == activeId
            val bg by animateColorAsState(if (on) C.Surface2 else Color.Transparent, label = "tabbg")
            val edge by animateColorAsState(if (on) C.Line else Color.Transparent, label = "tabedge")
            Row(
                Modifier.animateItem().clip(RoundedCornerShape(10.dp)).background(bg).border(1.dp, edge, RoundedCornerShape(10.dp))
                    .combinedClickable(onLongClick = { vm.selectTab(t.id); onRename() }) { vm.selectTab(t.id) }
                    .padding(start = 10.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MethodTag(t.method, Modifier, size = 10)
                Spacer(Modifier.width(6.dp))
                Text(
                    t.name, color = if (on) C.Text else C.Muted, fontSize = 13.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp),
                )
                Box(Modifier.size(28.dp).clip(CircleShape).clickable { vm.closeTab(t.id) }, Alignment.Center) {
                    if (t.id in vm.running) CircularProgressIndicator(Modifier.size(11.dp), color = C.Green, strokeWidth = 1.5.dp)
                    else Icon(Icons.Rounded.Close, "Close tab", tint = C.Faint, modifier = Modifier.size(13.dp))
                }
            }
        }
        item {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).press { vm.newTab() }, Alignment.Center) {
                Icon(Icons.Rounded.Add, "New tab", tint = C.Muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun RequestPane(vm: AppVM, d: AppData, tab: Req) {
    val vars = scope(d, tab)
    fun set(f: (Req) -> Req) = vm.updateTab(tab.id, f)
    Column(Modifier.fillMaxSize()) {
        UrlBar(vm, tab, vars, ::set)
        Spacer(Modifier.height(10.dp))
        Split(top = { RequestEditor(vm, tab, vars, ::set) }, bottom = { focus -> ResponsePanel(vm, tab, onSearch = focus) })
    }
}

@Composable
private fun UrlBar(vm: AppVM, tab: Req, vars: Map<String, String>, set: ((Req) -> Req) -> Unit) {
    var custom by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val loading = tab.id in vm.running
    val focus = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val send = { focus.clearFocus(); haptic.performHapticFeedback(HapticFeedbackType.ContextClick); vm.send(tab.id) }
    val mc by animateColorAsState(methodColor(tab.method), label = "mc")
    val shape = RoundedCornerShape(16.dp)

    Row(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(shape).background(C.Surface)
            .border(1.dp, if (focused) SolidColor(mc.copy(alpha = .45f)) else C.Edge, shape).padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuButton(METHODS.map { m -> MenuItem(m, color = methodColor(m)) { set { it.copy(method = m) } } } + MenuItem("Custom…") { custom = true }) { open ->
            Row(
                Modifier.clip(RoundedCornerShape(11.dp)).background(mc.copy(alpha = .12f)).press(onClick = open).padding(start = 10.dp, end = 4.dp, top = 11.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(tab.method, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "method") { m ->
                    Text(m, color = methodColor(m), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, maxLines = 1)
                }
                Icon(Icons.Rounded.ExpandMore, null, tint = mc.copy(alpha = .7f), modifier = Modifier.size(16.dp))
            }
        }
        Field(
            tab.url,
            { v ->
                if (v.trimStart().startsWith("curl ")) {
                    runCatching { parseCurl(v) }.onSuccess { p -> set { r -> p.copy(id = r.id, name = r.name, collectionId = r.collectionId) }; vm.toast("cURL imported") }
                        .onFailure { set { r -> r.withUrl(v) } }
                } else set { r -> r.withUrl(v) }
            },
            Modifier.weight(1f), "Enter URL or paste cURL", mono = true, singleLine = false, maxLines = 4, vars = vars,
            bg = Color.Transparent, border = false, fontSize = 13, onGo = send, onFocus = { focused = it }, padH = 10,
        )
        val bg by animateColorAsState(if (loading) C.Surface3 else C.Green, label = "sendbg")
        Box(
            Modifier.height(40.dp).clip(RoundedCornerShape(11.dp)).background(bg)
                .press { if (loading) vm.cancel(tab.id) else send() }.padding(horizontal = 14.dp),
            Alignment.Center,
        ) {
            AnimatedContent(loading, transitionSpec = { (fadeIn() + scaleIn(initialScale = .85f)) togetherWith (fadeOut() + scaleOut(targetScale = .85f)) }, label = "send") { l ->
                if (l) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(13.dp), color = C.Green, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp)); Text("Stop", color = C.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Send", color = C.Bg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Rounded.Send, null, tint = C.Bg, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
    if (custom) TextDialog("Custom method", tab.method, "e.g. PROPFIND", "Use", onDismiss = { custom = false }) { m ->
        set { it.copy(method = m.uppercase().filter { c -> !c.isWhitespace() }) }; custom = false
    }
}

/**
 * Request editor and response share the screen: stacked in portrait, side by side in landscape.
 * Drag the grip to resize, tap it to hand the space to the response.
 */
@Composable
private fun Split(top: @Composable () -> Unit, bottom: @Composable (focusBottom: () -> Unit) -> Unit) {
    val frac = remember { Animatable(.46f) }
    val scope = rememberCoroutineScope()
    val focusBottom: () -> Unit = { scope.launch { frac.animateTo(.12f, spring(dampingRatio = .82f, stiffness = 280f)) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val conf = LocalConfiguration.current
        val side = conf.screenWidthDp > conf.screenHeightDp // not constraints: the keyboard must not flip the layout
        val span = (if (side) constraints.maxWidth else constraints.maxHeight).toFloat()
        val grip = Modifier
            .pointerInput(span, side) {
                if (side) detectHorizontalDragGestures { _, d -> scope.launch { frac.snapTo((frac.value + d / span).coerceIn(.2f, .8f)) } }
                else detectVerticalDragGestures { _, d -> scope.launch { frac.snapTo((frac.value + d / span).coerceIn(.1f, .9f)) } }
            }
            .clickable(interactionSource = null, indication = null) {
                scope.launch { frac.animateTo(if (frac.value > .25f) (if (side) .2f else .12f) else .46f, spring(dampingRatio = .82f, stiffness = 280f)) }
            }
        if (side) Row(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxHeight().weight(frac.value)) { top() }
            Box(grip.fillMaxHeight().width(20.dp), Alignment.Center) {
                Box(Modifier.fillMaxHeight().width(1.dp).background(C.Line))
                Box(Modifier.width(4.dp).height(36.dp).clip(CircleShape).background(C.Faint))
            }
            Box(Modifier.fillMaxHeight().weight(1f - frac.value)) { bottom {} } // side by side: already full height
        } else Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(frac.value)) { top() }
            Box(grip.fillMaxWidth().height(22.dp), Alignment.Center) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
                Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(C.Faint))
            }
            Box(Modifier.fillMaxWidth().weight(1f - frac.value)) { bottom(focusBottom) }
        }
    }
}

@Composable
private fun RequestEditor(vm: AppVM, tab: Req, vars: Map<String, String>, set: ((Req) -> Req) -> Unit) {
    var sec by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Segmented(
            listOf("Params", "Auth", "Headers", "Body"), sec, { sec = it }, Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
            counts = listOf(tab.params.count { it.enabled && it.key.isNotBlank() }, 0, tab.headers.count { it.enabled && it.key.isNotBlank() }, 0),
        )
        AnimatedContent(
            sec, label = "section",
            transitionSpec = { (fadeIn(tween(200)) + slideInHorizontally(tween(240)) { w -> if (targetState > initialState) w / 12 else -w / 12 }) togetherWith fadeOut(tween(100)) },
        ) { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 12.dp)) {
                when (s) {
                    0 -> KvEditor(tab.params, { p -> set { it.withParams(p) } }, vars, "parameter")
                    1 -> AuthEditor(tab.auth, vars) { a -> set { it.copy(auth = a) } }
                    2 -> KvEditor(
                        tab.headers, { h -> set { it.copy(headers = h) } }, vars, "header",
                        presets = listOf("Content-Type: application/json", "Accept: application/json", "Cache-Control: no-cache", "User-Agent: Synderz/1.0"),
                    )
                    else -> BodyEditor(vm, tab, vars, set)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AuthEditor(a: Auth, vars: Map<String, String>, set: (Auth) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Type", color = C.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        MenuButton(AuthType.entries.map { t -> MenuItem(t.label) { set(a.copy(type = t)) } }) { open -> DropField(a.type.label, open) }
    }
    AnimatedContent(a.type, label = "auth", transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(100)) }) { t ->
        Column {
            when (t) {
                AuthType.NONE -> Text("This request doesn't send any credentials.", color = C.Faint, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
                AuthType.BEARER -> { Label("Token"); Field(a.token, { set(a.copy(token = it)) }, Modifier.fillMaxWidth(), "{{token}}", mono = true, vars = vars, bg = C.Surface) }
                AuthType.BASIC -> {
                    var show by remember { mutableStateOf(false) }
                    Label("Username"); Field(a.username, { set(a.copy(username = it)) }, Modifier.fillMaxWidth(), "username", mono = true, vars = vars, bg = C.Surface)
                    Label("Password")
                    Field(a.password, { set(a.copy(password = it)) }, Modifier.fillMaxWidth(), "password", mono = true, vars = vars, password = !show, bg = C.Surface) {
                        Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Toggle", tint = C.Faint,
                            modifier = Modifier.size(18.dp).clickable { show = !show })
                    }
                }
                AuthType.API_KEY -> {
                    Label("Key"); Field(a.key, { set(a.copy(key = it)) }, Modifier.fillMaxWidth(), "X-API-Key", mono = true, vars = vars, bg = C.Surface)
                    Label("Value"); Field(a.value, { set(a.copy(value = it)) }, Modifier.fillMaxWidth(), "{{apiKey}}", mono = true, vars = vars, bg = C.Surface)
                    Label("Add to")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Header", !a.inQuery) { set(a.copy(inQuery = false)) }
                        Chip("Query params", a.inQuery) { set(a.copy(inQuery = true)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BodyEditor(vm: AppVM, tab: Req, vars: Map<String, String>, set: ((Req) -> Req) -> Unit) {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            set { it.copy(binaryUri = uri.toString()) }
        }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        BodyType.entries.forEach { b -> Chip(b.label, tab.bodyType == b, C.Blue) { set { it.copy(bodyType = b) } } }
    }
    if (tab.bodyType != BodyType.NONE && tab.method.uppercase() in setOf("GET", "HEAD")) {
        Row(
            Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(C.Amber.copy(alpha = .08f)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.WarningAmber, null, tint = C.Amber, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("${tab.method} can't carry a body on Android's HTTP stack, so it won't be sent.", color = C.Amber, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(12.dp))
    AnimatedContent(tab.bodyType, label = "body", transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(100)) }) { b ->
        Column {
            when (b) {
                BodyType.NONE -> Text("This request has no body.", color = C.Faint, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                BodyType.RAW -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MenuButton(RawType.entries.map { r -> MenuItem(r.label) { set { it.copy(rawType = r) } } }) { open -> DropField(tab.rawType.label, open, color = C.Blue) }
                        Spacer(Modifier.weight(1f))
                        if (tab.rawType == RawType.JSON) Chip("Beautify") {
                            prettyJson(tab.rawBody)?.let { p -> set { it.copy(rawBody = p) } } ?: vm.toast("Body isn't valid JSON")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Field(tab.rawBody, { v -> set { it.copy(rawBody = v) } }, Modifier.fillMaxWidth().heightIn(min = 200.dp),
                        if (tab.rawType == RawType.JSON) "{\n  \"key\": \"{{value}}\"\n}" else "", mono = true, singleLine = false, minLines = 10,
                        vars = vars, lang = tab.rawType, fontSize = 13, bg = C.Surface)
                }
                BodyType.FORM -> KvEditor(tab.form, { f -> set { it.copy(form = f) } }, vars)
                BodyType.MULTIPART -> KvEditor(tab.multipart, { f -> set { it.copy(multipart = f) } }, vars, files = true)
                BodyType.BINARY -> Row(
                    Modifier.fillMaxWidth().press { picker.launch(arrayOf("*/*")) }.surface(RoundedCornerShape(12.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.FileUpload, null, tint = C.Blue)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (tab.binaryUri.isEmpty()) "Select file" else remember(tab.binaryUri) { fileName(ctx, tab.binaryUri) }, color = C.Text, fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Sent as the raw request body", color = C.Faint, fontSize = 12.sp)
                    }
                    if (tab.binaryUri.isNotEmpty()) IconBtn(Icons.Rounded.Close, "Clear") { set { it.copy(binaryUri = "") } }
                }
                BodyType.GRAPHQL -> {
                    Label("Query")
                    Field(tab.gqlQuery, { v -> set { it.copy(gqlQuery = v) } }, Modifier.fillMaxWidth(), "query {\n  viewer { id }\n}",
                        mono = true, singleLine = false, minLines = 8, vars = vars, fontSize = 13, bg = C.Surface)
                    Label("Variables")
                    Field(tab.gqlVariables, { v -> set { it.copy(gqlVariables = v) } }, Modifier.fillMaxWidth(), "{ }",
                        mono = true, singleLine = false, minLines = 4, vars = vars, lang = RawType.JSON, fontSize = 13, bg = C.Surface)
                }
            }
        }
    }
}

@Composable
private fun SaveDialog(d: AppData, tab: Req, onDismiss: () -> Unit, onSave: (String, String?, String?) -> Unit) {
    var name by remember { mutableStateOf(tab.name) }
    var target by remember { mutableStateOf(tab.collectionId ?: d.collections.firstOrNull()?.id) } // null = new collection
    var newName by remember { mutableStateOf("") }
    val ok = name.isNotBlank() && (target != null || newName.isNotBlank())
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.Surface2, shape = RoundedCornerShape(22.dp),
        title = { Text("Save request", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Field(name, { name = it }, Modifier.fillMaxWidth(), "Request name", bg = C.Surface)
                Label("Save to")
                val radio = RadioButtonDefaults.colors(selectedColor = C.Green, unselectedColor = C.Faint)
                d.collections.forEach { c ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { target = c.id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(target == c.id, { target = c.id }, colors = radio)
                        Text(c.name, color = C.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { target = null }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(target == null, { target = null }, colors = radio)
                    Text("New collection", color = C.Blue, fontSize = 14.sp)
                }
                if (target == null) Field(newName, { newName = it }, Modifier.fillMaxWidth().padding(top = 4.dp), "Collection name", bg = C.Surface, autoFocus = true)
            }
        },
        confirmButton = { TextButton({ onSave(name.trim(), target, newName.trim()) }, enabled = ok) { Text("Save", color = if (ok) C.Green else C.Faint) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = C.Muted) } },
    )
}

@Composable
private fun CurlDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var v by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.Surface2, shape = RoundedCornerShape(22.dp),
        title = { Text("Import cURL", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Field(v, { v = it }, Modifier.fillMaxWidth(), "curl https://api.example.com -H 'Accept: application/json'",
                mono = true, singleLine = false, minLines = 6, maxLines = 12, bg = C.Surface, fontSize = 12, autoFocus = true)
        },
        confirmButton = { TextButton({ onImport(v) }, enabled = v.isNotBlank()) { Text("Import", color = C.Green) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = C.Muted) } },
    )
}
