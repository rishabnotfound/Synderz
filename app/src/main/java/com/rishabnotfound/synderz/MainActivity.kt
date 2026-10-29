package com.rishabnotfound.synderz

import com.rishabnotfound.synderz.ui.LocalClock24
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.ui.platform.LocalConfiguration
import android.app.Activity
import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rishabnotfound.synderz.ui.C
import com.rishabnotfound.synderz.ui.CollectionsScreen
import com.rishabnotfound.synderz.ui.EnvironmentsScreen
import com.rishabnotfound.synderz.ui.HistoryScreen
import com.rishabnotfound.synderz.ui.IconBtn
import com.rishabnotfound.synderz.ui.MenuButton
import com.rishabnotfound.synderz.ui.MenuItem
import com.rishabnotfound.synderz.ui.MonitorScreen
import com.rishabnotfound.synderz.ui.SettingsDialog
import com.rishabnotfound.synderz.ui.SynderzTheme
import com.rishabnotfound.synderz.ui.WorkspaceScreen
import com.rishabnotfound.synderz.ui.press
import com.rishabnotfound.synderz.ui.surface
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    private val vm: AppVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(TRANSPARENT), SystemBarStyle.dark(TRANSPARENT))
        setContent { SynderzTheme { App(vm) } }
    }

    override fun onPause() {
        super.onPause()
        vm.flush()
    }
}

private val NAV = listOf(
    "Request" to Icons.Rounded.Bolt, "Collections" to Icons.Rounded.Folder, "Monitor" to Icons.Rounded.MonitorHeart,
    "History" to Icons.Rounded.History, "Envs" to Icons.Rounded.Layers,
)
private const val MONITOR = 2

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun App(vm: AppVM) {
    val d by vm.data.collectAsState()
    var screen by rememberSaveable { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) { vm.messages.collectLatest { snack.showSnackbar(it) } }
    LaunchedEffect(Unit) { vm.finished.collect { ok -> haptic.performHapticFeedback(if (ok) HapticFeedbackType.Confirm else HapticFeedbackType.Reject) } }
    val full = d.kiosk
    LaunchedEffect(full) { if (full) screen = MONITOR }
    // Back in the dashboard is handled (PIN-gated) by MonitorScreen.
    BackHandler(!full && screen != 0) { screen = 0 }

    // Monitoring is a dashboard you glance at: keep the display on while any probe is live.
    val view = LocalView.current
    val monitoring = vm.live.isNotEmpty()
    DisposableEffect(monitoring) {
        view.keepScreenOn = monitoring
        onDispose { view.keepScreenOn = false }
    }
    // Locked dashboard: nothing on screen but the monitors, and the app is pinned so Home/Recents can't leave it.
    DisposableEffect(full) {
        val activity = view.context as Activity
        val c = WindowCompat.getInsetsController(activity.window, view)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (full) {
            c.hide(WindowInsetsCompat.Type.systemBars())
            runCatching { activity.startLockTask() }
        } else c.show(WindowInsetsCompat.Type.systemBars())
        onDispose {
            // rotation recreates the activity; keep the pin rather than re-prompting
            if (!activity.isChangingConfigurations) {
                c.show(WindowInsetsCompat.Type.systemBars())
                if (full) runCatching { activity.stopLockTask() }
            }
        }
    }

    val ctx = LocalContext.current
    CompositionLocalProvider(LocalClock24 provides (d.settings.clock24 ?: android.text.format.DateFormat.is24HourFormat(ctx))) {
    BoxWithConstraints(Modifier.fillMaxSize().background(C.Bg)) {
        // Orientation from the screen, not from our constraints: the keyboard shrinks constraints, and flipping
        // layouts while it opens would rebuild the focused field and close it again.
        val conf = LocalConfiguration.current
        val wide = conf.screenWidthDp > conf.screenHeightDp && conf.screenWidthDp >= 560
        val ime = WindowInsets.isImeVisible
        val showChrome = !full
        val content = @Composable { modifier: Modifier ->
            AnimatedContent(
                screen, modifier, label = "screen",
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    if (wide) (fadeIn(tween(220)) + slideInVertically(tween(280)) { dir * it / 14 }) togetherWith fadeOut(tween(100))
                    else (fadeIn(tween(220)) + slideInHorizontally(tween(280)) { dir * it / 14 }) togetherWith fadeOut(tween(100))
                },
            ) { s ->
                when (s) {
                    0 -> WorkspaceScreen(vm, d)
                    1 -> CollectionsScreen(vm, d) { vm.open(it); screen = 0 }
                    MONITOR -> MonitorScreen(vm, d, full, vm::setKiosk) { vm.newTab(it.copy(id = newId())); screen = 0 }
                    3 -> HistoryScreen(vm, d) { vm.newTab(it); screen = 0 }
                    else -> EnvironmentsScreen(vm, d)
                }
            }
        }
        Scaffold(
            containerColor = C.Bg,
            contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
            topBar = { if (showChrome && !wide) TopBar(vm, d) { settings = true } },
            bottomBar = {
                AnimatedVisibility(showChrome && !wide && !WindowInsets.isImeVisible, enter = expandVertically(), exit = shrinkVertically()) {
                    NavBar(screen, monitoring) { screen = it }
                }
            },
            snackbarHost = {
                SnackbarHost(snack) {
                    Snackbar(it, Modifier.border(1.dp, C.Edge, RoundedCornerShape(12.dp)), containerColor = C.Surface3, contentColor = C.Text, shape = RoundedCornerShape(12.dp))
                }
            },
        ) { pad ->
            val m = Modifier.padding(pad).consumeWindowInsets(pad).imePadding()
            if (wide && showChrome) Row(m) {
                AnimatedVisibility(!ime, enter = expandHorizontally(), exit = shrinkHorizontally()) {
                    Rail(vm, d, screen, monitoring, { screen = it }) { settings = true }
                }
                content(Modifier.weight(1f).fillMaxHeight())
            } else content(m.fillMaxSize())
        }
    }
    if (settings) SettingsDialog(vm, d) { settings = false }
    }
}

/** One indicator that glides between items rather than each item fading its own. */
@Composable
private fun NavBar(selected: Int, monitoring: Boolean, onSelect: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(Modifier.background(C.Surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        BoxWithConstraints(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom)).height(62.dp)) {
            val w = maxWidth / NAV.size
            val x by animateDpAsState(w * selected, spring(dampingRatio = .72f, stiffness = 420f), label = "nav")
            Box(Modifier.offset(x = x + (w - 52.dp) / 2, y = 8.dp).width(52.dp).height(28.dp).clip(CircleShape).background(C.Green.copy(alpha = .13f)))
            Row(Modifier.fillMaxSize()) {
                NAV.forEachIndexed { i, (label, icon) ->
                    NavItem(i == selected, label, icon, i == MONITOR && monitoring, Modifier.weight(1f).fillMaxHeight().padding(top = 11.dp)) {
                        if (i != selected) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(i)
                    }
                }
            }
        }
    }
}

@Composable
private fun NavItem(on: Boolean, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, live: Boolean, modifier: Modifier, showLabel: Boolean = true, onClick: () -> Unit) {
    val tint by animateColorAsState(if (on) C.Green else C.Faint, label = "navtint")
    val text by animateColorAsState(if (on) C.Text else C.Faint, label = "navtext")
    Column(modifier.clickable(interactionSource = null, indication = null, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Icon(icon, label, tint = tint, modifier = Modifier.size(21.dp))
            if (live) Box(
                Modifier.align(Alignment.TopEnd).offset(3.dp, (-2).dp).size(8.dp).clip(CircleShape)
                    .background(C.Surface).padding(1.5.dp).clip(CircleShape).background(C.Green),
            )
        }
        if (showLabel) Spacer(Modifier.height(7.dp))
        if (showLabel) Text(label, color = text, fontSize = 11.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}

/** Landscape: navigation moves to a side rail so the content keeps every pixel of height. */
@Composable
private fun Rail(vm: AppVM, d: AppData, selected: Int, monitoring: Boolean, onSelect: (Int) -> Unit, onSettings: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(Modifier.fillMaxHeight()) {
        Column(Modifier.width(76.dp).fillMaxHeight().background(C.Surface).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.logo_mark), "Synderz", Modifier.size(36.dp))
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val h = minOf(maxHeight / NAV.size, 64.dp)
                val labels = h >= 58.dp // short landscape phones get an icon-only rail
                val y by animateDpAsState(h * selected, spring(dampingRatio = .72f, stiffness = 420f), label = "rail")
                val iconTop = ((if (labels) h - 49.dp else h - 21.dp) / 2).coerceAtLeast(0.dp)
                Box(Modifier.offset(x = 12.dp, y = y + iconTop - 4.dp).width(52.dp).height(29.dp).clip(CircleShape).background(C.Green.copy(alpha = .13f)))
                Column {
                    NAV.forEachIndexed { i, (label, icon) ->
                        NavItem(i == selected, label, icon, i == MONITOR && monitoring, Modifier.fillMaxWidth().height(h).padding(top = iconTop), showLabel = labels) {
                            if (i != selected) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(i)
                        }
                    }
                }
            }
            EnvMenu(vm, d, compact = true)
            IconBtn(Icons.Rounded.Settings, "Settings", onClick = onSettings)
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))
    }
}

@Composable
private fun EnvMenu(vm: AppVM, d: AppData, compact: Boolean) {
    val env = d.envs.find { it.id == d.activeEnvId }
    MenuButton(
        listOf(MenuItem("No environment", if (env == null) Icons.Rounded.Check else null) { vm.setActiveEnv(null) }) +
            d.envs.map { e -> MenuItem(e.name, if (e.id == env?.id) Icons.Rounded.Check else null, if (e.id == env?.id) C.Green else C.Text) { vm.setActiveEnv(e.id) } },
    ) { open ->
        val dot by animateColorAsState(if (env != null) C.Green else C.Faint, label = "envdot")
        if (compact) Box(Modifier.size(40.dp).clip(CircleShape).press(onClick = open), Alignment.Center) {
            Icon(Icons.Rounded.Layers, "Environment", tint = if (env != null) C.Green else C.Muted, modifier = Modifier.size(20.dp))
            Box(Modifier.align(Alignment.TopEnd).offset((-8).dp, 8.dp).size(7.dp).clip(CircleShape).background(dot))
        } else Row(
            Modifier.press(onClick = open).surface(RoundedCornerShape(10.dp), C.Surface).padding(start = 10.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(7.dp))
            Text(env?.name ?: "No env", color = if (env != null) C.Text else C.Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 110.dp))
            Icon(Icons.Rounded.ExpandMore, null, tint = C.Faint, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TopBar(vm: AppVM, d: AppData, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(C.Bg).windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top))
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.logo_mark), "Synderz", Modifier.size(30.dp))
        Spacer(Modifier.width(9.dp))
        Text("synderz", style = TextStyle(brush = C.Accent, fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp))
        Spacer(Modifier.weight(1f))
        EnvMenu(vm, d, compact = false)
        IconBtn(Icons.Rounded.Settings, "Settings", onClick = onSettings)
    }
}
