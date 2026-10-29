package com.rishabnotfound.synderz.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.runtime.key
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalContext
import com.rishabnotfound.synderz.CheckHost
import com.rishabnotfound.synderz.GeoNode
import com.rishabnotfound.synderz.GeoResult
import com.rishabnotfound.synderz.GeoRun
import com.rishabnotfound.synderz.MonitorStats
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rishabnotfound.synderz.redact
import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rishabnotfound.synderz.AppData
import com.rishabnotfound.synderz.AppVM
import com.rishabnotfound.synderz.MAX_MONITORS
import com.rishabnotfound.synderz.METHODS
import com.rishabnotfound.synderz.Monitor
import com.rishabnotfound.synderz.Req
import com.rishabnotfound.synderz.Sample
import com.rishabnotfound.synderz.fmtDuration
import com.rishabnotfound.synderz.stats
import com.rishabnotfound.synderz.withUrl
import kotlinx.coroutines.delay
import java.util.Date

private val INTERVALS = listOf(5, 10, 30, 60, 300)

private fun intervalLabel(s: Int) = if (s < 60) "${s}s" else "${s / 60}m"

/** Colour of one check: status palette, always paired with a code/label in text elsewhere. */
private fun sampleColor(s: Sample) = when {
    s.code == 0 || s.code >= 500 -> C.Red
    s.code >= 400 -> C.Amber
    else -> C.Green
}

private fun stateColor(last: Sample?) = if (last == null) C.Faint else sampleColor(last)

private fun statusLabel(last: Sample?, live: Boolean) = when {
    last == null -> if (live) "CHECKING" else "PAUSED"
    last.code == 0 -> "DOWN"
    else -> last.code.toString()
}

@Composable
fun MonitorScreen(vm: AppVM, d: AppData, fullscreen: Boolean, onFullscreen: (Boolean) -> Unit, onOpen: (Req) -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000 - now % 1000) } }
    var editing by remember { mutableStateOf<Monitor?>(null) }
    var adding by remember { mutableStateOf(false) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var gate by remember { mutableStateOf<(() -> Unit)?>(null) }
    val requirePin = { action: () -> Unit -> gate = action }
    val conf = LocalConfiguration.current
    val wide = conf.screenWidthDp > conf.screenHeightDp
    val perPage = if (wide) 6 else 5 // 3x2 grid in landscape, a column of 5 in portrait
    val pages = (d.monitors.size + perPage - 1) / perPage
    val pager = rememberPagerState { maxOf(1, pages) }
    val scope = rememberCoroutineScope()
    val add = { if (d.monitors.size >= MAX_MONITORS) vm.toast("Up to $MAX_MONITORS monitors") else adding = true }
    val locked = fullscreen // kiosk: watch + swipe pages only, everything else needs the PIN
    BackHandler(detail != null) { detail = null }
    BackHandler(locked && detail == null) { requirePin { onFullscreen(false) } }
    val toggleFullscreen = { on: Boolean ->
        if (on) { if (d.pin == null) requirePin { onFullscreen(true) } else onFullscreen(true) } // a PIN must exist before locking in
        else requirePin { onFullscreen(false) }
    }

    AnimatedContent(detail?.let { id -> d.monitors.find { it.id == id } }, label = "detail",
        transitionSpec = { (fadeIn(tween(220)) + scaleIn(initialScale = .97f)) togetherWith fadeOut(tween(100)) }) { m ->
        if (m != null) Detail(
            vm, m, vm.samples[m.id].orEmpty(), m.id in vm.live, now, locked, requirePin,
            onBack = { detail = null }, onEdit = { editing = m }, onOpen = { onOpen(m.req) },
        )
        else Column(Modifier.fillMaxSize()) {
            GlanceBar(vm, d, now, locked, toggleFullscreen, add, pages, pager.currentPage) { p -> scope.launch { pager.animateScrollToPage(p) } }
            if (d.monitors.isEmpty()) {
                EmptyState(
                    Icons.Rounded.MonitorHeart, "Watch your endpoints live",
                    "Response time, status codes and uptime at a glance, $perPage per page. URLs stay hidden behind a PIN, and the screen stays on while monitoring.",
                ) { Chip("Add endpoint", true, onClick = add) }
            } else HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                val slice = d.monitors.drop(page * perPage).take(perPage)
                Grid(vm, slice, now, locked, perPage, if (wide) 3 else 1, showAdd = !locked && slice.size < perPage && d.monitors.size < MAX_MONITORS, add) { detail = it }
            }
        }
    }

    if (adding) MonitorDialog(null, onDismiss = { adding = false }) { name, method, url, interval ->
        if (vm.addMonitor(name, Req(name = name.ifBlank { "Monitor" }, method = method).withUrl(url), interval)) {
            adding = false
            scope.launch { pager.animateScrollToPage((vm.data.value.monitors.size - 1) / perPage) }
        }
    }
    editing?.let { m ->
        MonitorDialog(m, onDismiss = { editing = null }) { name, method, url, interval ->
            vm.updateMonitor(m.id) { it.copy(name = name.ifBlank { it.name }, intervalSec = interval, req = it.req.copy(method = method).withUrl(url)) }
            editing = null
        }
    }
    gate?.let { action -> PinGate(vm, d, onDismiss = { gate = null }) { gate = null; action() } }
    vm.fresh?.let { FreshDialog(it, now, onAgain = vm::checkNow, onDismiss = vm::dismissFresh) }
}

/** Results of a cache-busted check of every site: status, time, and what the edge cache said. */
@Composable
private fun FreshDialog(rows: List<AppVM.Fresh>, now: Long, onAgain: () -> Unit, onDismiss: () -> Unit) {
    val running = rows.any { it.running }
    val ok = rows.count { !it.running && it.code in 200..399 }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(20.dp).widthIn(max = 560.dp).fillMaxWidth().surface(RoundedCornerShape(24.dp), C.Surface2)
                .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fresh check", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (running) "Fetching ${rows.size} sites past any edge cache…" else "$ok/${rows.size} up · no-cache + unique query, so CDNs can't answer from cache",
                        color = C.Muted, fontSize = 12.sp, lineHeight = 17.sp,
                    )
                }
                if (running) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(18.dp), color = C.Green, strokeWidth = 2.dp)
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                rows.forEachIndexed { i, r ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line.copy(alpha = .6f)))
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when { r.running -> "checking…"; r.error != null -> failReason(r.error); else -> r.cache },
                                color = if (r.error != null) C.Red else if (r.cache.contains("HIT") || r.cache.startsWith("cached")) C.Amber else C.Faint,
                                fontSize = 11.sp, fontFamily = Mono,
                            )
                        }
                        if (r.running) CircularProgressIndicator(Modifier.size(12.dp), color = C.Faint, strokeWidth = 1.5.dp)
                        else {
                            if (r.error == null) Text("${r.ms} ms", color = C.Text, fontSize = 13.sp, fontFamily = Mono, modifier = Modifier.padding(end = 10.dp))
                            val c = when { r.error != null || r.code >= 500 -> C.Red; r.code >= 400 -> C.Amber; else -> C.Green }
                            StatusPill(if (r.error != null) "DOWN" else "${r.code}", c, live = false, solid = true)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onAgain, enabled = !running) { Text("Run again", color = if (running) C.Faint else C.Blue) }
                TextButton(onDismiss) { Text("Done", color = C.Green) }
            }
        }
    }
}

/** Fleet summary in one line: clock, how many are up, typical latency, uptime. */
@Composable
private fun GlanceBar(vm: AppVM, d: AppData, now: Long, locked: Boolean, onFullscreen: (Boolean) -> Unit, add: () -> Unit, pages: Int, page: Int, onPage: (Int) -> Unit) {
    val clock = rememberClock()
    val lasts = d.monitors.mapNotNull { vm.samples[it.id]?.lastOrNull() }
    val up = lasts.count { it.up }
    val down = lasts.size - up
    val all = d.monitors.flatMap { vm.samples[it.id].orEmpty() }
    val live = vm.live.isNotEmpty()
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(clock.format(Date(now)), color = C.Text, fontSize = 20.sp, fontFamily = Mono, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp)
                Spacer(Modifier.width(10.dp))
                if (lasts.isNotEmpty()) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (down > 0) C.Red else C.Green))
                    Spacer(Modifier.width(5.dp))
                    Text(if (down > 0) "$down DOWN" else "ALL UP", color = C.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                }
            }
            Text(
                buildString {
                    append("$up/${d.monitors.size} up")
                    if (lasts.any { it.up }) append(" · avg ${lasts.filter { it.up }.map { it.ms }.average().toLong()} ms")
                    if (all.isNotEmpty()) append(" · %.1f%% uptime".format(all.count { it.up } * 100.0 / all.size))
                    if (live) append(" · awake")
                },
                color = C.Faint, fontSize = 11.sp, fontFamily = Mono, maxLines = 1,
            )
        }
        if (pages > 1) Row(Modifier.padding(end = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(pages) { i ->
                val w by animateDpAsState(if (i == page) 16.dp else 6.dp, label = "dot")
                Box(Modifier.height(6.dp).width(w).clip(CircleShape).background(if (i == page) C.Green else C.Surface3).clickable { onPage(i) })
            }
        }
        if (locked) {
            if (d.monitors.isNotEmpty()) IconBtn(Icons.Rounded.NetworkCheck, "Check all now, bypassing caches", tint = C.Blue) { vm.checkNow() }
            IconBtn(Icons.Rounded.Lock, "Unlock to exit dashboard", tint = C.Muted) { onFullscreen(false) }
        } else {
            if (d.monitors.isNotEmpty()) {
                IconBtn(if (live) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (live) "Pause all" else "Start all", tint = if (live) C.Text else C.Green) {
                    if (live) vm.stopAll() else vm.startAll()
                }
                IconBtn(Icons.Rounded.NetworkCheck, "Check all now, bypassing caches", tint = C.Blue) { vm.checkNow() }
                IconBtn(Icons.Rounded.Fullscreen, "Locked dashboard") { onFullscreen(true) }
            }
            if (d.monitors.size < MAX_MONITORS) IconBtn(Icons.Rounded.Add, "Add monitor", tint = C.Green, onClick = add)
        }
    }
}

/**
 * Every page has the same slot geometry (a column of 5 in portrait, 3x2 in landscape), so tiles never jump size.
 * Long-press a tile and drag to reorder: tiles are placed by slot math under stable keys, so a tile keeps its
 * identity (and the drag) while it moves, and the others glide out of the way via animateBounds.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Grid(
    vm: AppVM, monitors: List<Monitor>, now: Long, locked: Boolean, perPage: Int, cols: Int, showAdd: Boolean,
    add: () -> Unit, onDetail: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var order by remember(monitors.map { it.id }) { mutableStateOf(monitors.map { it.id }) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    val byId = monitors.associateBy { it.id }
    val rows = (perPage + cols - 1) / cols

    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
        val density = LocalDensity.current
        val gap = with(density) { 8.dp.toPx() }
        val cw = (constraints.maxWidth - gap * (cols - 1)) / cols
        val ch = (constraints.maxHeight - gap * (rows - 1)) / rows
        val cwDp = with(density) { cw.toDp() }
        val chDp = with(density) { ch.toDp() }
        fun cell(i: Int) = Offset((i % cols) * (cw + gap), (i / cols) * (ch + gap))
        fun slotAt(p: Offset): Int? {
            val c = (p.x / (cw + gap)).toInt(); val r = (p.y / (ch + gap)).toInt()
            return (r * cols + c).takeIf { p.x >= 0 && p.y >= 0 && c < cols && r < rows && it < order.size }
        }
        LookaheadScope {
            order.forEachIndexed { i, id ->
                val m = byId[id] ?: return@forEachIndexed
                key(id) {
                    val lifted = dragging == id
                    val lift by animateFloatAsState(if (lifted) 1f else 0f, label = "lift")
                    val at = cell(i)
                    Tile(
                        vm, m, vm.samples[m.id].orEmpty(), m.id in vm.live, now, locked,
                        Modifier
                            .offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }
                            .size(cwDp, chDp)
                            .then(if (lifted) Modifier else Modifier.animateBounds(this@LookaheadScope))
                            .zIndex(if (lifted) 1f else 0f)
                            .graphicsLayer {
                                if (lifted) { translationX = drag.x; translationY = drag.y }
                                scaleX = 1f + .03f * lift; scaleY = 1f + .03f * lift
                                shadowElevation = 28f * lift
                                shape = RoundedCornerShape(18.dp); clip = false
                            }
                            .then(
                                if (locked) Modifier else Modifier.pointerInput(id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { dragging = id; drag = Offset.Zero; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                        onDrag = { change, d ->
                                            change.consume()
                                            drag += d
                                            val from = order.indexOf(id)
                                            val to = slotAt(cell(from) + drag + Offset(cw / 2, ch / 2))
                                            if (to != null && to != from) {
                                                drag += cell(from) - cell(to) // keep the tile under the finger as its slot changes
                                                order = order.toMutableList().apply { add(to, removeAt(from)) }
                                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            }
                                        },
                                        onDragEnd = {
                                            vm.reorderMonitors(order)
                                            scope.launch {
                                                Animatable(drag, Offset.VectorConverter).animateTo(Offset.Zero, spring(dampingRatio = .75f, stiffness = 500f)) { drag = value }
                                                dragging = null
                                            }
                                        },
                                        onDragCancel = { order = monitors.map { it.id }; drag = Offset.Zero; dragging = null },
                                    )
                                },
                            ),
                    ) { onDetail(id) }
                }
            }
            if (showAdd) {
                val at = cell(order.size)
                AddTile(Modifier.offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }.size(cwDp, chDp), add)
            }
        }
    }
}

@Composable
private fun AddTile(modifier: Modifier, onClick: () -> Unit) = Box(
    modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).drawBehind {
        drawRoundRect(
            C.Line, cornerRadius = CornerRadius(14.dp.toPx()),
            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
        )
    },
    Alignment.Center,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Add, null, tint = C.Faint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("ADD ENDPOINT", color = C.Faint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
    }
}

@Composable
private fun LiveDot(on: Boolean, color: Color, solid: Boolean = false) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val s by pulse.animateFloat(1f, 2.6f, infiniteRepeatable(tween(1500), RepeatMode.Restart), label = "ring")
    val a by pulse.animateFloat(.55f, 0f, infiniteRepeatable(tween(1500), RepeatMode.Restart), label = "ringa")
    Box(Modifier.size(16.dp), Alignment.Center) {
        if (on) Box(Modifier.size(7.dp).scale(s).clip(CircleShape).background(color.copy(alpha = a)))
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (on || solid) color else C.Faint))
    }
}

/**
 * System-monitor tile. Hierarchy: the latency number first, then status, then trend.
 * State is carried by an ambient corner glow + the status pill (with a text label), not a coloured stripe.
 */
@Composable
private fun Tile(vm: AppVM, m: Monitor, samples: List<Sample>, live: Boolean, now: Long, locked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val st = remember(samples) { stats(samples) }
    val last = samples.lastOrNull()
    val state = stateColor(last)
    val down = last != null && !last.up
    val pulse = rememberInfiniteTransition(label = "alarm").animateFloat(.3f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alarma")
    val glow by animateColorAsState(if (!live || last == null) Color.Transparent else state, tween(600), label = "glow")
    val shape = RoundedCornerShape(18.dp)
    val edge = if (down) SolidColor(C.Red.copy(alpha = .35f + .45f * pulse.value)) else C.Edge

    BoxWithConstraints(
        modifier.clip(shape).background(C.Surface)
            .drawBehind {
                val a = if (down) .10f + .08f * pulse.value else .07f
                drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = a), Color.Transparent), Offset(size.width, 0f), size.width * .75f), size.width * .75f, Offset(size.width, 0f))
            }
            .border(1.dp, edge, shape).clickable(onClick = onClick),
    ) {
        val compact = maxHeight < 112.dp
        Column(Modifier.fillMaxSize().padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.name, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            last == null -> if (live) "waiting for first check" else "paused · every ${intervalLabel(m.intervalSec)}"
                            !live -> "paused · last ${fmtAgo(now - last.at)}"
                            last.code == 0 -> "${failReason(last.error)} · ${fmtAgo(now - last.at)}"
                            else -> "every ${intervalLabel(m.intervalSec)} · ${fmtAgo(now - last.at)}"
                        },
                        color = C.Faint, fontSize = 11.sp, maxLines = 1,
                    )
                }
                StatusPill(statusLabel(last, live), state, live && last != null)
                if (!locked) Box(Modifier.size(30.dp).clip(CircleShape).clickable { if (live) vm.stopMonitor(m.id) else vm.startMonitor(m.id) }, Alignment.Center) {
                    Icon(if (live) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (live) "Pause" else "Start", tint = if (live) C.Faint else C.Green, modifier = Modifier.size(16.dp))
                }
            }
            // The number is sized from the row it actually gets (after name + footer), not the whole tile,
            // and divided by the user's font scale, so it can never be taller than its row and get clipped.
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val fs = LocalDensity.current.fontScale
                val big = (maxHeight.value * .8f / fs).coerceIn(14f, 46f)
                val roomy = maxHeight >= 30.dp
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (last == null || last.code == 0) "—" else last.ms.toString(), color = if (last == null || last.code == 0) C.Faint else C.Text,
                        fontSize = big.sp, fontFamily = Mono, fontWeight = FontWeight.Medium, letterSpacing = (-1.5).sp, lineHeight = big.sp, maxLines = 1,
                    )
                    if (last != null && last.code != 0) Column(Modifier.padding(start = 4.dp, bottom = 2.dp)) {
                        if (roomy && st != null && last.up && st.avg > 0 && samples.size >= 3) {
                            val pct = ((last.ms - st.avg) * 100 / st.avg).toInt()
                            Text(if (pct >= 0) "▲$pct%" else "▼${-pct}%", color = C.Muted, fontSize = 10.sp, fontFamily = Mono, maxLines = 1)
                        }
                        Text("ms", color = C.Muted, fontSize = 12.sp, fontFamily = Mono, maxLines = 1)
                    }
                    Spacer(Modifier.width(14.dp))
                    Sparkline(samples, Modifier.weight(1f).fillMaxHeight().padding(top = 6.dp, bottom = 4.dp, end = 4.dp))
                }
            }
            Spacer(Modifier.height(if (compact) 5.dp else 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (st == null) Text("no checks yet", color = C.Faint, fontSize = 11.sp)
                else {
                    Text("%.1f%%".format(st.uptime), color = C.Text, fontSize = 11.sp, fontFamily = Mono, fontWeight = FontWeight.Medium)
                    Text(" up", color = C.Faint, fontSize = 11.sp)
                }
                if (st != null && st.p95 > 0) {
                    Text("  ·  p95 ", color = C.Faint, fontSize = 11.sp)
                    Text("${st.p95}", color = C.Text, fontSize = 11.sp, fontFamily = Mono, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.width(12.dp))
                Heartbeat(samples, Modifier.weight(1f).padding(end = 4.dp).height(if (compact) 7.dp else 9.dp))
            }
        }
    }
}

/** Short, host-free reason for a failed check, safe to show on a locked dashboard. */
private fun failReason(err: String?): String {
    val e = err.orEmpty().lowercase()
    return when {
        "timeout" in e || "timed out" in e -> "timeout"
        "resolve" in e || "no address" in e -> "no host"
        "refused" in e -> "refused"
        "ssl" in e || "certificate" in e || "handshake" in e -> "tls error"
        "reset" in e -> "reset"
        "unreachable" in e || "no route" in e -> "unreachable"
        else -> "failed"
    }
}

private fun fmtAgo(ms: Long) = if (ms < 1500) "just now" else "${fmtDuration(ms)} ago"

@Composable
private fun StatusPill(label: String, color: Color, live: Boolean, solid: Boolean = false) {
    val bg by animateColorAsState(if (label == "DOWN") C.Red.copy(alpha = .16f) else C.Surface2, label = "pillbg")
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(bg).border(1.dp, C.Line, RoundedCornerShape(8.dp)).padding(start = 4.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveDot(live, color, solid)
        Text(label, color = C.Text, fontSize = 11.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
    }
}

/** Smooth curve through the points (horizontal-tangent cubic), so spikes read as a trend, not scribbles. */
private fun linePath(pts: List<Offset>) = Path().apply {
    pts.forEachIndexed { i, p ->
        if (i == 0) moveTo(p.x, p.y)
        else { val c = (pts[i - 1].x + p.x) / 2; cubicTo(c, pts[i - 1].y, c, p.y, p.x, p.y) }
    }
}

/** Axis top. Never below 100ms, so a few ms of jitter on a fast API stays visually flat. */
private fun niceMax(v: Long): Long {
    val steps = listOf(100L, 200, 250, 500, 1000, 2000, 2500, 5000, 10000, 20000, 30000, 60000)
    return steps.firstOrNull { it >= v * 1.1 } ?: (v * 1.2).toLong()
}

/** Only checks that got a response have a latency; failed checks (code 0) only know how long they took to fail. */
private fun axisMax(pts: List<Sample>) = niceMax(pts.filter { it.code > 0 }.maxOfOrNull { it.ms } ?: 0)

/**
 * Latency series: responded checks form the curve (4xx/5xx get a marker), checks with no response
 * are a flat red line on the baseline, so a dead endpoint reads as a straight line, never a fake wave.
 */
private fun DrawScope.latencySeries(pts: List<Sample>, x: (Int) -> Float, y: (Long) -> Float, base: Float, lineAlpha: Float, width: Float, fillAlpha: Float) {
    var i = 0
    while (i < pts.size) {
        val answered = pts[i].code > 0
        var j = i
        while (j + 1 < pts.size && (pts[j + 1].code > 0) == answered) j++
        if (answered) {
            val o = (i..j).map { Offset(x(it), y(pts[it].ms)) }
            if (o.size == 1) drawCircle(C.Blue.copy(alpha = lineAlpha), width * 1.4f, o[0])
            else {
                drawPath(linePath(o).apply { lineTo(o.last().x, base); lineTo(o.first().x, base); close() },
                    Brush.verticalGradient(listOf(C.Blue.copy(alpha = fillAlpha), Color.Transparent), endY = base))
                drawPath(linePath(o), C.Blue.copy(alpha = lineAlpha), style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            (i..j).forEach { k -> if (!pts[k].up) { drawCircle(C.Surface, width * 3f, o[k - i]); drawCircle(sampleColor(pts[k]), width * 2f, o[k - i]) } }
        } else {
            val y0 = base - width
            if (i == j) drawCircle(C.Red, width * 1.6f, Offset(x(i), y0))
            else drawLine(C.Red.copy(alpha = .85f), Offset(x(i), y0), Offset(x(j), y0), width * 1.4f, StrokeCap.Round)
        }
        i = j + 1
    }
}

/**
 * Latency sparkline in its own space beside the number. Only checks that got a response are plotted;
 * a failed check is a gap in the line (its colour lives in the status strip below), so there are no stray marks.
 */
@Composable
private fun Sparkline(samples: List<Sample>, modifier: Modifier) = Canvas(modifier) {
    val pts = samples.takeLast(40)
    val guide = C.Line.copy(alpha = .55f)
    drawLine(guide, Offset(0f, size.height), Offset(size.width, size.height), 1f)
    if (pts.size < 2) return@Canvas
    val max = axisMax(pts).toFloat()
    val dx = size.width / (pts.size - 1)
    fun y(ms: Long) = size.height - (ms / max).coerceAtMost(1f) * size.height
    var i = 0
    var lastPoint: Offset? = null
    while (i < pts.size) {
        if (pts[i].code == 0) { i++; continue }
        var j = i
        while (j + 1 < pts.size && pts[j + 1].code > 0) j++
        val o = (i..j).map { Offset(it * dx, y(pts[it].ms)) }
        if (o.size > 1) {
            drawPath(linePath(o).apply { lineTo(o.last().x, size.height); lineTo(o.first().x, size.height); close() },
                Brush.verticalGradient(listOf(C.Blue.copy(alpha = .22f), Color.Transparent)))
            drawPath(linePath(o), C.Blue, style = Stroke(1.6f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else drawCircle(C.Blue, 1.6f.dp.toPx(), o[0])
        lastPoint = o.last().takeIf { j == pts.lastIndex } ?: lastPoint
        i = j + 1
    }
    lastPoint?.let { drawCircle(C.Blue.copy(alpha = .25f), 5.dp.toPx(), it); drawCircle(C.Blue, 2.5f.dp.toPx(), it) }
}

/** Uptime-robot style strip: one bar per check, newest on the right. */
@Composable
private fun Heartbeat(samples: List<Sample>, modifier: Modifier) = Canvas(modifier) {
    val bar = 3.dp.toPx(); val gap = 2.dp.toPx()
    val n = ((size.width + gap) / (bar + gap)).toInt()
    val shown = samples.takeLast(n)
    for (i in 0 until n) {
        val s = shown.getOrNull(i - (n - shown.size))
        drawRoundRect(if (s == null) C.Surface3 else sampleColor(s), Offset(i * (bar + gap), 0f), Size(bar, size.height), CornerRadius(bar / 2))
    }
}

@Composable
private fun Detail(
    vm: AppVM, m: Monitor, samples: List<Sample>, live: Boolean, now: Long, locked: Boolean, requirePin: (() -> Unit) -> Unit,
    onBack: () -> Unit, onEdit: () -> Unit, onOpen: () -> Unit,
) {
    val ctx = LocalContext.current
    val clock = rememberClock()
    val st = remember(samples) { stats(samples) }
    var revealed by remember(m.id) { mutableStateOf(false) }
    val last = samples.lastOrNull()
    val geo = vm.geo[m.id]

    val hero = @Composable { Hero(m, last, st, live, now, revealed) { if (revealed) revealed = false else requirePin { revealed = true } } }
    val kpis = @Composable {
        KpiStrip(
            listOf(
                "avg" to (st?.avg?.takeIf { it > 0 }?.let { "$it" } ?: "—"), "p95" to (st?.p95?.takeIf { it > 0 }?.let { "$it" } ?: "—"),
                "min" to (st?.min?.takeIf { it > 0 }?.let { "$it" } ?: "—"), "max" to (st?.max?.takeIf { it > 0 }?.let { "$it" } ?: "—"),
            ), unit = "ms",
        )
        Spacer(Modifier.height(8.dp))
        KpiStrip(
            listOf(
                "uptime" to (st?.let { "%.1f%%".format(it.uptime) } ?: "—"), "checks" to "${samples.size}",
                "interval" to intervalLabel(m.intervalSec), "last" to (last?.let { (now - it.at).let { d -> if (d < 1500) "now" else fmtDuration(d) } } ?: "—"),
            ),
        )
    }
    val chart = @Composable {
        Section("Response time", "last ${minOf(samples.size, 120)} checks") { DetailChart(samples, Modifier.height(210.dp)) }
    }
    val beat = @Composable {
        Section("Availability", st?.let { "%.2f%% up".format(it.uptime) } ?: "no data") {
            Heartbeat(samples, Modifier.fillMaxWidth().height(20.dp))
            Spacer(Modifier.height(6.dp))
            Row {
                Text("older", color = C.Faint, fontSize = 10.sp, modifier = Modifier.weight(1f))
                Text("now", color = C.Faint, fontSize = 10.sp)
            }
        }
    }
    val global = @Composable {
        GlobalCheck(geo, revealed, onRun = { vm.runGeo(m) }, onReport = { url ->
            requirePin { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
        })
    }
    val settings = @Composable {
        if (!locked || samples.any { it.error != null }) Section("Settings", null) {
            samples.lastOrNull { it.error != null }?.let { e ->
                Text("Last error · ${clock.format(Date(e.at))}", color = C.Faint, fontSize = 11.sp)
                Text(if (revealed) e.error!! else redact(e.error!!, m.req.url), color = C.Red, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp, bottom = if (locked) 0.dp else 12.dp))
            }
            if (!locked) {
                Text("Check every", color = C.Faint, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    INTERVALS.forEach { s -> Chip(intervalLabel(s), m.intervalSec == s) { vm.updateMonitor(m.id) { it.copy(intervalSec = s) } } }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = C.Text, onClick = onBack)
                Text("Monitor", color = C.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (!locked) IconBtn(if (live) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (live) "Pause" else "Start", tint = if (live) C.Text else C.Green) {
                    if (live) vm.stopMonitor(m.id) else vm.startMonitor(m.id)
                }
                if (locked) Icon(Icons.Rounded.Lock, "Read-only", tint = C.Faint, modifier = Modifier.padding(end = 14.dp).size(16.dp))
                else MenuButton(
                    listOf(
                        MenuItem("Edit", Icons.Rounded.Edit) { requirePin(onEdit) },
                        MenuItem("Open as request", Icons.Rounded.OpenInNew) { requirePin(onOpen) },
                        MenuItem("Clear data", Icons.Rounded.DeleteSweep) { requirePin { vm.samples.remove(m.id) } },
                        MenuItem("Remove", Icons.Rounded.Delete, C.Red) { requirePin { onBack(); vm.deleteMonitor(m.id) } },
                    ),
                ) { o -> IconBtn(Icons.Rounded.MoreVert, "Monitor actions", onClick = o) }
            }
            val gap = Arrangement.spacedBy(12.dp)
            if (wide) Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = gap) {
                    hero(); chart(); beat()
                }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 16.dp), verticalArrangement = gap) {
                    Column { kpis() }; global(); settings()
                }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = gap) {
                hero(); Column { kpis() }; chart(); beat(); global(); settings()
            }
        }
    }
}

@Composable
private fun Hero(m: Monitor, last: Sample?, st: MonitorStats?, live: Boolean, now: Long, revealed: Boolean, onUrl: () -> Unit) {
    val state = stateColor(last)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Surface)
            .drawBehind {
                drawCircle(Brush.radialGradient(listOf(state.copy(alpha = if (live) .10f else .04f), Color.Transparent), Offset(size.width, 0f), size.width * .8f),
                    size.width * .8f, Offset(size.width, 0f))
            }
            .border(1.dp, C.Edge, RoundedCornerShape(20.dp)).padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.name, color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            StatusPill(statusLabel(last, live), state, live && last != null)
        }
        Row(
            Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onUrl).padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (revealed) Icons.Rounded.LockOpen else Icons.Rounded.Lock, null, tint = if (revealed) C.Amber else C.Faint, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (revealed) "${m.req.method} ${m.req.url}" else "${m.req.method}  ••••••••••••  tap to reveal",
                color = if (revealed) C.Muted else C.Faint, fontSize = 12.sp, fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            val failed = last != null && last.code == 0
            Text(if (last == null || failed) "—" else "${last.ms}", color = if (last == null || failed) C.Faint else C.Text,
                fontSize = 56.sp, fontFamily = Mono, fontWeight = FontWeight.Medium, letterSpacing = (-2.5).sp, lineHeight = 58.sp)
            if (last != null && !failed) Text(" ms", color = C.Muted, fontSize = 15.sp, fontFamily = Mono, modifier = Modifier.padding(bottom = 8.dp))
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 6.dp)) {
                Text(
                    when { st == null -> if (live) "waiting" else "paused"; st.up -> "up ${fmtDuration(now - st.since)}"; else -> "down ${fmtDuration(now - st.since)}" },
                    color = C.Text, fontSize = 14.sp, fontFamily = Mono, fontWeight = FontWeight.Medium,
                )
                Text(
                    when { last == null -> "every ${intervalLabel(m.intervalSec)}"; failed -> failReason(last.error); else -> "checked ${fmtAgo(now - last.at)}" },
                    color = C.Faint, fontSize = 11.sp,
                )
            }
        }
    }
}

/** Four numbers in one bar, hairline separated. */
@Composable
private fun KpiStrip(items: List<Pair<String, String>>, unit: String? = null) = Row(
    Modifier.fillMaxWidth().height(IntrinsicSize.Min).surface(RoundedCornerShape(14.dp)).padding(vertical = 12.dp),
) {
    items.forEachIndexed { i, (k, v) ->
        if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(k.uppercase(), color = C.Faint, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(v, color = C.Text, fontSize = 16.sp, fontFamily = Mono, fontWeight = FontWeight.Medium, maxLines = 1)
                if (unit != null && v != "—") Text(unit, color = C.Faint, fontSize = 10.sp, fontFamily = Mono, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String, trailing: String?, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().surface(RoundedCornerShape(18.dp)).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
) {
    Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = C.Faint, fontSize = 11.sp, fontFamily = Mono)
        action?.invoke()
    }
    content()
}

/** check-host.net fan-out: one row per region, problems first, then fastest to slowest. */
@Composable
private fun GlobalCheck(run: GeoRun?, revealed: Boolean, onRun: () -> Unit, onReport: (String) -> Unit) {
    val done = run?.results.orEmpty()
    val ok = run?.nodes?.count { done[it.host]?.ok == true } ?: 0
    val bad = run?.nodes?.count { done[it.host]?.ok == false } ?: 0
    val pending = (run?.nodes?.size ?: 0) - ok - bad
    val times = run?.nodes?.mapNotNull { n -> done[n.host]?.takeIf { it.ok == true }?.sec }?.sorted().orEmpty()
    Section(
        "Global check", if (run?.nodes?.isNotEmpty() == true) "${run.nodes.size} regions" else null,
        action = {
            Spacer(Modifier.width(10.dp))
            Chip(if (run?.running == true) "Checking…" else if (run == null) "Run" else "Run again", run?.running != true) { if (run?.running != true) onRun() }
        },
    ) {
        when {
            run == null -> Text(
                "See this endpoint from ~48 locations worldwide, with status and response time per country. Runs on check-host.net, which receives the URL.",
                color = C.Muted, fontSize = 13.sp, lineHeight = 19.sp,
            )
            run.error != null -> Text(run.error, color = C.Red, fontSize = 13.sp)
            run.nodes.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), color = C.Green, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp)); Text("Contacting check-host.net…", color = C.Muted, fontSize = 13.sp)
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    GeoCount(ok, "ok", C.Green); GeoCount(bad, "failed", C.Red); if (pending > 0) GeoCount(pending, "pending", C.Faint)
                    Spacer(Modifier.weight(1f))
                    if (times.isNotEmpty()) Text("median ${(times[times.size / 2] * 1000).toLong()} ms", color = C.Muted, fontSize = 11.sp, fontFamily = Mono)
                }
                if (times.size >= 2) {
                    val fast = run.nodes.first { done[it.host]?.sec == times.first() && done[it.host]?.ok == true }
                    val slow = run.nodes.first { done[it.host]?.sec == times.last() && done[it.host]?.ok == true }
                    Text(
                        "fastest ${CheckHost.flag(fast.cc)} ${fast.city.ifBlank { fast.country }} ${(times.first() * 1000).toLong()}ms · slowest ${CheckHost.flag(slow.cc)} ${slow.city.ifBlank { slow.country }} ${(times.last() * 1000).toLong()}ms",
                        color = C.Faint, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                val maxSec = maxOf(times.lastOrNull() ?: 1.0, 0.5)
                val order = run.nodes.sortedWith(compareBy<GeoNode>({ when (done[it.host]?.ok) { false -> 0; true -> 1; null -> 2 } }, { done[it.host]?.sec ?: 0.0 }))
                Column(Modifier.animateContentSize()) {
                    order.forEachIndexed { i, n ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line.copy(alpha = .6f)))
                        GeoRow(n, done[n.host], maxSec, revealed)
                    }
                }
                run.permalink?.let { link ->
                    if (!run.running) Text(
                        "Open full report on check-host.net", color = C.Blue, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(6.dp)).clickable { onReport(link) }.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GeoCount(n: Int, label: String, color: Color) = Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(7.dp).clip(CircleShape).background(color))
    Spacer(Modifier.width(5.dp))
    Text("$n", color = C.Text, fontSize = 13.sp, fontFamily = Mono, fontWeight = FontWeight.Medium)
    Text(" $label", color = C.Faint, fontSize = 12.sp)
}

@Composable
private fun GeoRow(n: GeoNode, r: GeoResult?, maxSec: Double, revealed: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(CheckHost.flag(n.cc), fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(n.country.ifBlank { n.cc.uppercase() }, color = C.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(n.city.ifBlank { null }, n.host.substringBefore('.'), r?.ip?.takeIf { revealed }).joinToString(" · "),
                color = C.Faint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        when (r?.ok) {
            null -> CircularProgressIndicator(Modifier.size(12.dp), color = C.Faint, strokeWidth = 1.5.dp)
            false -> Text(
                (r.code?.let { "$it " }.orEmpty() + (r.message ?: "failed")).take(28), color = C.Red, fontSize = 12.sp, fontFamily = Mono,
                maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.width(150.dp),
            )
            true -> Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(56.dp).height(4.dp).clip(CircleShape).background(C.Surface3)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(((r.sec ?: 0.0) / maxSec).toFloat().coerceIn(.04f, 1f)).clip(CircleShape)
                        .background(speedColor(((r.sec ?: 0.0) * 1000).toLong())))
                }
                Spacer(Modifier.width(10.dp))
                Text(r.code ?: "", color = C.Muted, fontSize = 11.sp, fontFamily = Mono)
                Text("${((r.sec ?: 0.0) * 1000).toLong()}", color = C.Text, fontSize = 13.sp, fontFamily = Mono, fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
                Text("ms", color = C.Faint, fontSize = 10.sp, fontFamily = Mono, modifier = Modifier.padding(start = 2.dp))
            }
        }
    }
}

/** Full chart: single axis, recessive grid, scrub to read any check. */
@Composable
private fun DetailChart(samples: List<Sample>, modifier: Modifier) {
    val clock = rememberClock()
    var sel by remember(samples.size) { mutableIntStateOf(-1) }
    val pts = samples.takeLast(120)
    val measurer = rememberTextMeasurer()
    val label = TextStyle(color = C.Faint, fontSize = 10.sp, fontFamily = Mono)
    val pick = pts.getOrNull(sel) ?: pts.lastOrNull()

    Column(modifier) {
        Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pick != null) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(sampleColor(pick)))
                Spacer(Modifier.width(6.dp))
                Text(
                    (if (pick.code == 0) "DOWN · ${failReason(pick.error)}" else "${pick.ms} ms · ${pick.code}") + " · ${clock.format(Date(pick.at))}" + (if (sel < 0) " · latest" else ""),
                    color = C.Text, fontSize = 12.sp, fontFamily = Mono,
                )
            } else Text("Waiting for the first check…", color = C.Faint, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier.fillMaxWidth().weight(1f)
                .pointerInput(pts.size) {
                    fun at(x: Float) { if (pts.size > 1) sel = ((x - 44.dp.toPx()) / ((size.width - 44.dp.toPx()) / (pts.size - 1))).toInt().coerceIn(0, pts.lastIndex) }
                    detectDragGestures(onDragEnd = { sel = -1 }, onDragCancel = { sel = -1 }) { c, _ -> at(c.position.x) }
                }
                .pointerInput(pts.size) {
                    detectTapGestures { o -> if (pts.size > 1) sel = ((o.x - 44.dp.toPx()) / ((size.width - 44.dp.toPx()) / (pts.size - 1))).toInt().coerceIn(0, pts.lastIndex) }
                },
        ) {
            val left = 44.dp.toPx()
            val w = size.width - left
            val h = size.height - 16.dp.toPx()
            val max = axisMax(pts)
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            listOf(0L, max / 2, max).forEach { v ->
                val y = h - v.toFloat() / max * h
                drawLine(C.Line, Offset(left, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = if (v == 0L) null else dash)
                val t = measurer.measure("${v}ms", label)
                drawText(t, topLeft = Offset(0f, (y - t.size.height / 2).coerceIn(0f, h)))
            }
            if (pts.size >= 2) {
                val dx = w / (pts.size - 1)
                val yOf = { ms: Long -> h - (ms.toFloat() / max).coerceAtMost(1f) * h }
                latencySeries(pts, x = { left + it * dx }, y = yOf, base = h, lineAlpha = 1f, width = 2.dp.toPx(), fillAlpha = .18f)
                val i = if (sel in pts.indices) sel else pts.lastIndex
                val p = Offset(left + i * dx, if (pts[i].code > 0) yOf(pts[i].ms) else h - 2.dp.toPx())
                drawLine(C.Muted.copy(alpha = .5f), Offset(p.x, 0f), Offset(p.x, h), 1.dp.toPx())
                drawCircle(C.Surface, 6.dp.toPx(), p); drawCircle(if (pts[i].code > 0) C.Blue else C.Red, 4.dp.toPx(), p)
                val first = measurer.measure(clock.format(Date(pts.first().at)), label)
                val lastT = measurer.measure(clock.format(Date(pts.last().at)), label)
                drawText(first, topLeft = Offset(left, h + 4.dp.toPx()))
                drawText(lastT, topLeft = Offset(size.width - lastT.size.width, h + 4.dp.toPx()))
            }
        }
    }
}

@Composable
private fun MonitorDialog(m: Monitor?, onDismiss: () -> Unit, onSave: (String, String, String, Int) -> Unit) {
    var name by remember { mutableStateOf(m?.name.orEmpty()) }
    var url by remember { mutableStateOf(m?.req?.url.orEmpty()) }
    var method by remember { mutableStateOf(m?.req?.method ?: "GET") }
    var interval by remember { mutableIntStateOf(m?.intervalSec ?: 10) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.Surface2, shape = RoundedCornerShape(22.dp),
        title = { Text(if (m == null) "Monitor an endpoint" else "Edit monitor", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Field(url, { url = it }, Modifier.fillMaxWidth(), "https://api.example.com/health", mono = true, bg = C.Surface, autoFocus = m == null, fontSize = 13)
                Spacer(Modifier.height(8.dp))
                Field(name, { name = it }, Modifier.fillMaxWidth(), "Name (optional)", bg = C.Surface)
                Label("Method")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    METHODS.forEach { x -> Chip(x, method == x, methodColor(x)) { method = x } }
                }
                Label("Check every")
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    INTERVALS.forEach { s -> Chip(intervalLabel(s), interval == s) { interval = s } }
                }
            }
        },
        confirmButton = {
            TextButton({ onSave(name.trim(), method, url.trim(), interval) }, enabled = url.isNotBlank()) {
                Text(if (m == null) "Start" else "Save", color = if (url.isNotBlank()) C.Green else C.Faint)
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = C.Muted) } },
    )
}

/** PIN pad that guards monitor URLs. First use creates the PIN (entered twice). */
@Composable
private fun PinGate(vm: AppVM, d: AppData, onDismiss: () -> Unit, onUnlocked: () -> Unit) {
    val lock = d.pin
    var stage by remember { mutableStateOf(if (lock == null) "create" else "enter") }
    var first by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    fun fail(text: String) {
        msg = text; input = ""
        haptic.performHapticFeedback(HapticFeedbackType.Reject)
        scope.launch { listOf(14f, -12f, 9f, -6f, 3f, 0f).forEach { shake.animateTo(it, tween(45)) } }
    }
    fun submit() {
        when (stage) {
            "create" -> if (input.length < 4) fail("Use at least 4 digits") else { first = input; input = ""; msg = null; stage = "confirm" }
            "confirm" -> if (input == first) { vm.setPin(input); haptic.performHapticFeedback(HapticFeedbackType.Confirm); onUnlocked() }
                else { stage = "create"; first = ""; fail("PINs didn't match, start again") }
            else -> when (val r = vm.checkPin(input)) {
                AppVM.PinCheck.Ok -> { haptic.performHapticFeedback(HapticFeedbackType.Confirm); onUnlocked() }
                is AppVM.PinCheck.Wrong -> fail("Wrong PIN · ${r.left} ${if (r.left == 1) "try" else "tries"} left")
                is AppVM.PinCheck.Locked -> fail("Too many attempts, wait ${r.sec}s")
            }
        }
    }
    fun press(k: String) {
        haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
        when (k) {
            "del" -> input = input.dropLast(1)
            "ok" -> submit()
            else -> if (input.length < 6) {
                input += k
                if (stage == "enter" && lock != null && input.length == lock.len) submit()
                if (stage == "confirm" && input.length == first.length) submit()
            }
        }
    }

    val conf = LocalConfiguration.current
    val wide = conf.screenWidthDp > conf.screenHeightDp
    val key = if (wide) 50.dp else 64.dp

    val header = @Composable {
        Box(Modifier.size(44.dp).clip(CircleShape).background(C.Green.copy(alpha = .12f)), Alignment.Center) {
            Icon(Icons.Rounded.Lock, null, tint = C.Green, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            when (stage) { "create" -> "Create a monitor PIN"; "confirm" -> "Confirm your PIN"; else -> "Enter PIN" },
            color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            msg ?: when (stage) { "create" -> "4–6 digits. Unlocks monitor URLs."; "confirm" -> "Type it once more"; else -> "Monitor URLs are protected" },
            color = if (msg != null) C.Red else C.Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        val slots = if (stage == "enter" && lock != null) lock.len else if (stage == "confirm") first.length else maxOf(4, input.length)
        Row(Modifier.offset(x = shake.value.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(slots) { i ->
                val on = i < input.length
                val c by animateColorAsState(if (on) C.Green else Color.Transparent, label = "pin")
                Box(Modifier.size(13.dp).clip(CircleShape).background(c).border(1.5.dp, if (on) C.Green else C.Faint, CircleShape))
            }
        }
    }
    val pad = @Composable {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("del", "0", "ok")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(if (wide) 12.dp else 14.dp)) {
                    row.forEach { k ->
                        val hidden = k == "ok" && stage != "create"
                        Box(
                            Modifier.padding(vertical = if (wide) 3.dp else 5.dp).size(key).clip(CircleShape)
                                .background(if (k.length == 1) C.Surface3 else Color.Transparent)
                                .then(if (hidden) Modifier else Modifier.press { press(k) }),
                            Alignment.Center,
                        ) {
                            when (k) {
                                "del" -> Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete", tint = C.Muted, modifier = Modifier.size(20.dp))
                                "ok" -> if (!hidden) Icon(Icons.Rounded.Check, "Done", tint = C.Green, modifier = Modifier.size(24.dp))
                                else -> Text(k, color = C.Text, fontSize = if (wide) 20.sp else 24.sp, fontFamily = Mono, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = !wide)) {
        val box = Modifier.surface(RoundedCornerShape(26.dp), C.Surface2)
        if (wide) Row(box.padding(horizontal = 28.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(240.dp), horizontalAlignment = Alignment.CenterHorizontally) { header() }
            Spacer(Modifier.width(28.dp))
            Box(Modifier.width(1.dp).height(200.dp).background(C.Line))
            Spacer(Modifier.width(28.dp))
            pad()
        } else Column(box.padding(horizontal = 24.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            header()
            Spacer(Modifier.height(20.dp))
            pad()
        }
    }
}
