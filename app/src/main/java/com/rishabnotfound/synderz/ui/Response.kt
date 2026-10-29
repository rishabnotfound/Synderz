package com.rishabnotfound.synderz.ui

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rishabnotfound.synderz.AppVM
import com.rishabnotfound.synderz.Phase
import com.rishabnotfound.synderz.RawType
import com.rishabnotfound.synderz.Req
import com.rishabnotfound.synderz.Resp
import com.rishabnotfound.synderz.prettyJson
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

fun fmtSize(b: Long) = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.1f KB".format(b / 1024.0)
    else -> "%.2f MB".format(b / 1048576.0)
}

private fun fmtMs(ms: Double) = if (ms >= 1000) "%.2f s".format(ms / 1000) else "%.0f ms".format(ms)

private fun friendly(e: Throwable) = when (e) {
    is UnknownHostException -> "Couldn't resolve the host. Check the URL and your connection."
    is ConnectException -> "Connection refused. Is the server running and reachable from this phone?"
    is SocketTimeoutException -> "The request timed out. You can raise the timeout in Settings."
    is SSLException -> "SSL handshake failed: ${e.message}. For self-signed certificates, turn off SSL verification in Settings."
    else -> e.message ?: e.javaClass.simpleName
}

private val PHASE_COLORS = mapOf("DNS" to C.Teal, "Connect" to C.Amber, "TLS" to C.Purple, "Waiting" to C.Blue, "Download" to C.Green)

@Composable
fun ResponsePanel(vm: AppVM, tab: Req, onSearch: () -> Unit = {}) {
    val res = vm.responses[tab.id]
    val loading = tab.id in vm.running
    AnimatedContent(
        loading to res, label = "response",
        transitionSpec = { (fadeIn(tween(220)) + slideInVertically(tween(280)) { it / 16 }) togetherWith fadeOut(tween(100)) },
    ) { (l, r) ->
        when {
            l -> Waiting()
            r == null -> Hints()
            r.isFailure -> Failure(r.exceptionOrNull()!!)
            else -> ResponseView(vm, r.getOrThrow(), onSearch)
        }
    }
}

@Composable
private fun Waiting() {
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val s = System.nanoTime()
        while (true) withFrameNanos { elapsed = (System.nanoTime() - s) / 1_000_000 }
    }
    Column(Modifier.fillMaxSize()) {
        LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = C.Green, trackColor = C.Bg)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%.2f".format(elapsed / 1000.0) + "s", color = C.Text, fontSize = 34.sp, fontFamily = Mono, fontWeight = FontWeight.Normal, letterSpacing = (-1).sp)
            Text("waiting for response", color = C.Faint, fontSize = 12.sp, letterSpacing = .5.sp)
        }
    }
}

@Composable
private fun Hints() = Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), Alignment.Center) {
    Column(Modifier.widthIn(max = 360.dp)) {
        Text("No response yet", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text("Send a request and it'll land here.", color = C.Muted, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        listOf(
            "curl" to "Paste a cURL command straight into the URL bar",
            "{{ }}" to "Use environment variables anywhere in a request",
            "grip" to "Drag the divider, or tap it to focus the response",
            "hold" to "Long-press a tab to rename it",
        ).forEach { (k, v) ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(52.dp).surface(RoundedCornerShape(6.dp), C.Surface2).padding(vertical = 3.dp), Alignment.Center) {
                    Text(k, color = C.Muted, fontSize = 11.sp, fontFamily = Mono, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.width(12.dp))
                Text(v, color = C.Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Failure(e: Throwable) = Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(C.Red.copy(alpha = .12f)), Alignment.Center) {
        Icon(Icons.Rounded.CloudOff, null, tint = C.Red, modifier = Modifier.size(22.dp))
    }
    Spacer(Modifier.height(14.dp))
    Text("Couldn't get a response", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    Text(friendly(e), color = C.Muted, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 19.sp)
    Spacer(Modifier.height(8.dp))
    Text(e.javaClass.simpleName, color = C.Faint, fontSize = 11.sp, fontFamily = Mono)
}

@Composable
private fun Stat(text: String, color: androidx.compose.ui.graphics.Color = C.Muted) =
    Text(text, color = color, fontSize = 12.sp, fontFamily = Mono, fontWeight = FontWeight.Medium)

/** Thin timing bar that fills in on arrival; tap it for the full DNS → download waterfall. */
@Composable
private fun Waterfall(phases: List<Phase>, totalMs: Long) {
    if (phases.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    val span = maxOf(totalMs.toDouble(), phases.maxOf { it.start + it.dur }, 1.0)
    Column(Modifier.fillMaxWidth().clickable(interactionSource = null, indication = null) { open = !open }.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(C.Surface2)
                .graphicsLayer { scaleX = grow.value; transformOrigin = TransformOrigin(0f, .5f) },
        ) {
            phases.filter { it.dur > 0 }.forEach { p -> Box(Modifier.weight(p.dur.toFloat()).fillMaxHeight().background(PHASE_COLORS[p.name] ?: C.Muted)) }
            val rest = span - phases.sumOf { it.dur }
            if (rest > 0) Spacer(Modifier.weight(rest.toFloat()))
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                phases.forEach { p ->
                    val c = PHASE_COLORS[p.name] ?: C.Muted
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(c))
                        Spacer(Modifier.width(8.dp))
                        Text(p.name, color = C.Muted, fontSize = 12.sp, modifier = Modifier.width(70.dp))
                        BoxWithConstraints(Modifier.weight(1f).height(8.dp)) {
                            val x = maxWidth * (p.start / span).toFloat()
                            val w = (maxWidth * (p.dur / span).toFloat()).coerceAtLeast(2.dp)
                            Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(c))
                        }
                        Text(fmtMs(p.dur), color = C.Text, fontSize = 11.sp, fontFamily = Mono, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
                    }
                }
                Text("Reused connections skip DNS, Connect and TLS.", color = C.Faint, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ResponseView(vm: AppVM, r: Resp, onSearch: () -> Unit) {
    val ctx = LocalContext.current
    var sec by rememberSaveable { mutableIntStateOf(0) }
    var pretty by rememberSaveable { mutableStateOf(true) }
    val ct = r.contentType.lowercase()
    val isImage = ct.startsWith("image/") && !ct.contains("svg")
    val lang = when {
        ct.contains("json") || r.text.trimStart().let { it.startsWith("{") || it.startsWith("[") } -> RawType.JSON
        ct.contains("xml") || ct.contains("svg") -> RawType.XML
        ct.contains("html") -> RawType.HTML
        else -> null
    }
    val shown = remember(r, pretty) { if (pretty && lang == RawType.JSON) prettyJson(r.text) ?: r.text else r.text }
    val cookies = remember(r) {
        val u = r.url.toHttpUrlOrNull()
        if (u == null) emptyList() else r.headers.filter { it.first.equals("set-cookie", true) }.mapNotNull { Cookie.parse(u, it.second) }
    }
    val clip = 200_000 // binder transaction limit for clipboard/share

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var cur by rememberSaveable { mutableIntStateOf(0) }
    val lines = remember(shown) { codeLines(shown) }
    val matches = remember(lines, query) { findMatches(lines, query) }
    val byLine = remember(matches) { matches.groupBy { it.line } }
    val list = rememberLazyListState()
    LaunchedEffect(cur, matches) { matches.getOrNull(cur)?.let { list.animateScrollToItem((it.line - 3).coerceAtLeast(0)) } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("${r.code} ${r.message}".trim(), statusColor(r.code))
            Spacer(Modifier.width(12.dp)); Stat(fmtMs(r.timeMs.toDouble()), speedColor(r.timeMs))
            Text("  ·  ", color = C.Faint, fontSize = 12.sp)
            Stat(fmtSize(r.size))
            Spacer(Modifier.weight(1f))
            IconBtn(Icons.Rounded.ContentCopy, "Copy") { copyText(ctx, shown.take(clip)); vm.toast(if (shown.length > clip) "Copied first 200K chars" else "Response copied") }
            IconBtn(Icons.Rounded.Share, "Share") {
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shown.take(clip)), "Share response"))
            }
        }
        Waterfall(r.phases, r.timeMs)
        if (r.truncated) Text("Body larger than 20 MB, showing the first 20 MB.", color = C.Amber, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        Segmented(listOf("Body", "Headers", "Cookies"), sec, { sec = it }, Modifier.padding(horizontal = 12.dp).fillMaxWidth(), listOf(0, r.headers.size, cookies.size))
        when (sec) {
            0 -> Column(Modifier.fillMaxSize()) {
                if (!isImage && r.body.isNotEmpty()) AnimatedContent(searching, label = "search", transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(90)) }) { on ->
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (on) {
                            Field(
                                query, { query = it; cur = 0 }, Modifier.weight(1f), "Find in response", mono = true, fontSize = 13, bg = C.Surface,
                                autoFocus = true, padV = 8, onGo = { if (matches.isNotEmpty()) cur = (cur + 1) % matches.size },
                            ) {
                                Text(
                                    if (query.isEmpty()) "" else if (matches.isEmpty()) "0/0" else "${cur + 1}/${matches.size}${if (matches.size >= MAX_MATCHES) "+" else ""}",
                                    color = if (query.isNotEmpty() && matches.isEmpty()) C.Red else C.Muted, fontSize = 11.sp, fontFamily = Mono,
                                )
                            }
                            IconBtn(Icons.Rounded.KeyboardArrowUp, "Previous match") { if (matches.isNotEmpty()) cur = (cur - 1 + matches.size) % matches.size }
                            IconBtn(Icons.Rounded.KeyboardArrowDown, "Next match") { if (matches.isNotEmpty()) cur = (cur + 1) % matches.size }
                            IconBtn(Icons.Rounded.Close, "Close search") { searching = false; query = "" }
                        } else {
                            Text(lang?.label ?: "Text", color = C.Faint, fontSize = 11.sp, fontFamily = Mono, modifier = Modifier.weight(1f))
                            IconBtn(Icons.Rounded.Search, "Search response") { searching = true; onSearch() } // give results room above the keyboard
                            if (lang != null) {
                                Chip("Pretty", pretty) { pretty = true }
                                Spacer(Modifier.width(6.dp))
                                Chip("Raw", !pretty) { pretty = false }
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                    }
                }
                val bmp = remember(r) { if (isImage) BitmapFactory.decodeByteArray(r.body, 0, r.body.size)?.asImageBitmap() else null }
                when {
                    bmp != null -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Image(bmp, null, Modifier.fillMaxWidth().surface(RoundedCornerShape(12.dp), C.Surface2), contentScale = ContentScale.FillWidth)
                    }
                    r.body.isEmpty() -> EmptyState(Icons.Rounded.DataUsage, "Empty body", "The server sent no content.")
                    else -> CodeLines(lines, if (pretty) lang else null, byLine, matches.getOrNull(cur), list)
                }
            }
            1 -> SelectionContainer {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(r.headers) { (k, v) ->
                        Column {
                            Text(k, color = C.Blue, fontSize = 12.sp, fontFamily = Mono, fontWeight = FontWeight.Medium)
                            Text(v, color = C.Text, fontSize = 12.sp, fontFamily = Mono, lineHeight = 17.sp)
                        }
                    }
                }
            }
            else -> if (cookies.isEmpty()) EmptyState(Icons.Rounded.DataUsage, "No cookies", "This response didn't set any cookies.")
            else SelectionContainer {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cookies) { c ->
                        Column(Modifier.fillMaxWidth().surface(RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Text(c.name, color = C.Green, fontSize = 13.sp, fontFamily = Mono, fontWeight = FontWeight.Medium)
                            Text(c.value, color = C.Text, fontSize = 12.sp, fontFamily = Mono)
                            Spacer(Modifier.height(4.dp))
                            val flags = listOfNotNull("HttpOnly".takeIf { c.httpOnly }, "Secure".takeIf { c.secure }).joinToString(" · ")
                            Text(
                                "${c.domain}${c.path}" + (if (c.persistent) " · expires ${java.util.Date(c.expiresAt)}" else " · session") + (if (flags.isEmpty()) "" else " · $flags"),
                                color = C.Faint, fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ponytail: very long lines are hard-wrapped into 4K chunks, keeps Text measurement cheap on minified payloads
/** (line number or 0 for a continuation chunk, text) */
fun codeLines(text: String): List<Pair<Int, String>> =
    text.lines().withIndex().flatMap { (i, l) -> if (l.length <= 4000) listOf(i + 1 to l) else l.chunked(4000).mapIndexed { j, c -> (if (j == 0) i + 1 else 0) to c } }

const val MAX_MATCHES = 5000

class Match(val line: Int, val start: Int, val end: Int)

/** Case-insensitive, non-overlapping, capped so a one-letter query on a huge body stays instant. */
fun findMatches(lines: List<Pair<Int, String>>, q: String): List<Match> {
    if (q.isEmpty()) return emptyList()
    val out = ArrayList<Match>()
    for ((i, p) in lines.withIndex()) {
        var at = p.second.indexOf(q, 0, ignoreCase = true)
        while (at >= 0) {
            out += Match(i, at, at + q.length)
            if (out.size >= MAX_MATCHES) return out
            at = p.second.indexOf(q, at + q.length, ignoreCase = true)
        }
    }
    return out
}

/** Line-virtualised code view so multi-MB bodies stay smooth. */
@Composable
private fun CodeLines(lines: List<Pair<Int, String>>, lang: RawType?, hits: Map<Int, List<Match>>, current: Match?, state: LazyListState) {
    val gutter = (lines.lastOrNull()?.first ?: 1).toString().length
    SelectionContainer {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
            items(lines.size) { i ->
                val (n, l) = lines[i]
                val lineHits = hits[i]
                val text = remember(l, lang, lineHits, current) {
                    val base = highlight(l, lang, null)
                    if (lineHits == null) base else AnnotatedString.Builder(base).apply {
                        lineHits.forEach { m ->
                            val on = m === current
                            addStyle(SpanStyle(background = if (on) C.Amber else C.Amber.copy(alpha = .28f), color = if (on) C.Bg else C.Text), m.start, m.end)
                        }
                    }.toAnnotatedString()
                }
                Row {
                    Text(if (n == 0) "".padStart(gutter) else n.toString().padStart(gutter), color = C.Faint.copy(alpha = .7f), fontSize = 12.sp, fontFamily = Mono, lineHeight = 19.sp)
                    Spacer(Modifier.width(14.dp))
                    Text(text, color = C.Text, fontSize = 12.sp, fontFamily = Mono, lineHeight = 19.sp)
                }
            }
        }
    }
}
