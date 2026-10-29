package com.rishabnotfound.synderz.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rishabnotfound.synderz.KV
import com.rishabnotfound.synderz.RawType
import com.rishabnotfound.synderz.VAR_REGEX
import com.rishabnotfound.synderz.fileName
import com.rishabnotfound.synderz.isResolvable

// ---------- syntax + variable highlighting ----------

private val JSON_TOKEN = Regex("(\"(?:\\\\.|[^\"\\\\])*\")(\\s*:)?|(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)|\\b(true|false|null)\\b")
private val XML_TOKEN = Regex("(</?[\\w:.-]+|/?>|<\\?|\\?>)|([\\w:-]+)(?==)|(\"[^\"]*\"|'[^']*')|(<!--[\\s\\S]*?-->)")

fun highlight(s: String, lang: RawType?, vars: Map<String, String>?): AnnotatedString = buildAnnotatedString {
    append(s)
    if (s.length > 200_000) return@buildAnnotatedString
    when (lang) {
        RawType.JSON -> JSON_TOKEN.findAll(s).forEach { m ->
            val g = m.groups
            when {
                g[1] != null -> addStyle(SpanStyle(if (g[2] != null) C.Blue else C.Green), g[1]!!.range.first, g[1]!!.range.last + 1)
                g[3] != null -> addStyle(SpanStyle(C.Amber), m.range.first, m.range.last + 1)
                else -> addStyle(SpanStyle(C.Purple), m.range.first, m.range.last + 1)
            }
        }
        RawType.XML, RawType.HTML -> XML_TOKEN.findAll(s).forEach { m ->
            val g = m.groups
            val color = when { g[1] != null -> C.Blue; g[2] != null -> C.Purple; g[3] != null -> C.Green; else -> C.Faint }
            addStyle(SpanStyle(color), m.range.first, m.range.last + 1)
        }
        else -> {}
    }
    if (vars != null) VAR_REGEX.findAll(s).forEach { m ->
        val c = if (isResolvable(m.groupValues[1], vars)) C.Green else C.Red
        addStyle(SpanStyle(color = c, background = c.copy(alpha = .14f), fontWeight = FontWeight.Medium), m.range.first, m.range.last + 1)
    }
}

data class Highlight(val vars: Map<String, String>?, val lang: RawType? = null) : VisualTransformation {
    override fun filter(text: AnnotatedString) = TransformedText(highlight(text.text, lang, vars), OffsetMapping.Identity)
}

// ---------- inputs ----------

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    mono: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    vars: Map<String, String>? = null,
    lang: RawType? = null,
    password: Boolean = false,
    fontSize: Int = 14,
    bg: Color = C.Surface2,
    border: Boolean = true,
    autoFocus: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    onGo: (() -> Unit)? = null,
    onFocus: ((Boolean) -> Unit)? = null,
    padH: Int = 12,
    padV: Int = 10,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val line by animateColorAsState(if (!border) Color.Transparent else if (focused) C.Green.copy(alpha = .55f) else C.Line, label = "focus")
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    val style = TextStyle(color = C.Text, fontSize = fontSize.sp, fontFamily = if (mono) Mono else Sans, lineHeight = (fontSize + 7).sp)
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.focusRequester(focus).onFocusChanged { focused = it.isFocused; onFocus?.invoke(it.isFocused) },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = if (singleLine) 1 else maxLines,
        textStyle = style,
        cursorBrush = SolidColor(C.Green),
        keyboardActions = KeyboardActions(onGo = { onGo?.invoke() }),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
            imeAction = if (onGo != null) ImeAction.Go else if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        visualTransformation = when {
            password -> PasswordVisualTransformation()
            vars != null || lang != null -> Highlight(vars, lang)
            else -> VisualTransformation.None
        },
        decorationBox = { inner ->
            Row(
                Modifier.clip(shape).background(bg).border(1.dp, line, shape).padding(horizontal = padH.dp, vertical = padV.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = style.copy(color = C.Faint), maxLines = if (singleLine) 1 else Int.MAX_VALUE)
                    inner()
                }
                trailing?.invoke(this)
            }
        },
    )
}

@Composable
fun Check(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(if (checked) C.Green else Color.Transparent, label = "check")
    val edge by animateColorAsState(if (checked) C.Green else C.Faint, label = "checkedge")
    Box(
        modifier.size(18.dp).clip(RoundedCornerShape(5.dp)).background(bg).border(1.5.dp, edge, RoundedCornerShape(5.dp))
            .clickable { onChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Rounded.Check, null, tint = C.Bg, modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun HLine() = Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))

@Composable
private fun VLine() = Box(Modifier.width(1.dp).fillMaxHeight().background(C.Line))

/** Postman-style key/value table: trailing ghost row turns real once you type in it. */
@Composable
fun KvEditor(
    items: List<KV>,
    onChange: (List<KV>) -> Unit,
    vars: Map<String, String>?,
    keyHint: String = "Key",
    valueHint: String = "Value",
    files: Boolean = false,
    presets: List<String> = emptyList(),
) {
    var bulk by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    var pickFor by remember { mutableIntStateOf(-1) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && pickFor >= 0) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val kv = items.getOrElse(pickFor) { KV() }.copy(isFile = true, value = uri.toString())
            onChange(if (pickFor >= items.size) items + kv else items.toMutableList().also { it[pickFor] = kv })
        }
    }
    fun set(i: Int, kv: KV) = onChange(if (i >= items.size) items + kv else items.toMutableList().also { it[i] = kv })

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (bulk) {
            var text by remember { mutableStateOf(items.joinToString("\n") { (if (it.enabled) "" else "//") + it.key + ":" + it.value }) }
            Field(text, { t ->
                text = t
                onChange(t.lines().filter { it.isNotBlank() }.map { l ->
                    val off = l.trimStart().startsWith("//")
                    val body = if (off) l.trimStart().removePrefix("//") else l
                    KV(body.substringBefore(':').trim(), body.substringAfter(':', "").trim(), enabled = !off)
                })
            }, Modifier.fillMaxWidth(), placeholder = "key:value\n//disabled:row", mono = true, singleLine = false, minLines = 6, vars = vars, fontSize = 13, bg = C.Surface)
        } else Column(Modifier.fillMaxWidth().surface(RoundedCornerShape(12.dp))) {
            Row(Modifier.fillMaxWidth().background(C.Surface2).padding(start = 40.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("KEY", color = C.Faint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                Text("VALUE", color = C.Faint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.weight(1.25f).padding(start = 10.dp))
                Spacer(Modifier.width(if (files) 80.dp else 36.dp))
            }
            (items + KV()).forEachIndexed { i, kv ->
                val ghost = i == items.size
                val dim = if (!kv.enabled && !ghost) .4f else 1f
                HLine()
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(40.dp), Alignment.Center) {
                        Check(kv.enabled, { set(i, kv.copy(enabled = it)) }, Modifier.alpha(if (ghost) 0f else 1f))
                    }
                    Field(kv.key, { set(i, kv.copy(key = it)) }, Modifier.weight(1f).alpha(dim), if (ghost) keyHint else "", mono = true, vars = vars,
                        fontSize = 13, bg = Color.Transparent, border = false, padH = 4, padV = 11)
                    VLine()
                    if (kv.isFile) {
                        Row(
                            Modifier.weight(1.25f).fillMaxHeight().clickable { pickFor = i; picker.launch(arrayOf("*/*")) }.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.AttachFile, null, tint = C.Blue, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (kv.value.isEmpty()) "Select file" else remember(kv.value) { fileName(ctx, kv.value) },
                                color = if (kv.value.isEmpty()) C.Faint else C.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Field(kv.value, { set(i, kv.copy(value = it)) }, Modifier.weight(1.25f).alpha(dim), if (ghost) valueHint else "", mono = true, vars = vars,
                            fontSize = 13, bg = Color.Transparent, border = false, padH = 10, padV = 11)
                    }
                    if (files) Box(Modifier.width(44.dp), Alignment.Center) {
                        Text(
                            if (kv.isFile) "File" else "Text", color = if (kv.isFile) C.Blue else C.Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(C.Surface3)
                                .clickable { set(i, kv.copy(isFile = !kv.isFile, value = "")) }.padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                    Box(Modifier.size(36.dp).clip(CircleShape).clickable(enabled = !ghost) { onChange(items.filterIndexed { j, _ -> j != i }) }, Alignment.Center) {
                        if (!ghost) Icon(Icons.Rounded.Close, "Remove", tint = C.Faint, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${items.count { it.enabled && it.key.isNotBlank() }} active", color = C.Faint, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(if (bulk) "Table view" else "Bulk edit", color = C.Blue, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { bulk = !bulk }.padding(horizontal = 8.dp, vertical = 4.dp))
        }
        if (presets.isNotEmpty() && !bulk) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                presets.forEach { p ->
                    Chip("+ ${p.substringBefore(':')}") {
                        val (k, v) = p.substringBefore(':') to p.substringAfter(": ", "")
                        onChange(items.filter { !it.key.equals(k, true) } + KV(k, v))
                    }
                }
            }
        }
    }
}

// ---------- selection ----------

/** Equal-width segments with a thumb that springs between them. */
@Composable
fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, counts: List<Int> = emptyList()) {
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(modifier.height(38.dp).surface(RoundedCornerShape(11.dp), C.Surface).padding(3.dp)) {
        val w = maxWidth / labels.size
        val x by animateDpAsState(w * selected, spring(dampingRatio = .78f, stiffness = 520f), label = "thumb")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().surface(RoundedCornerShape(8.dp), C.Surface3))
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, l ->
                val fg by animateColorAsState(if (i == selected) C.Text else C.Muted, label = "segfg")
                Row(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                        .clickable(interactionSource = null, indication = null) {
                            if (i != selected) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(i)
                        },
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(l, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    val n = counts.getOrElse(i) { 0 }
                    if (n > 0) {
                        Spacer(Modifier.width(5.dp))
                        Text("$n", color = C.Green, fontSize = 10.sp, fontFamily = Mono, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(CircleShape).background(C.Green.copy(alpha = .12f)).padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean = false, color: Color = C.Green, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) color.copy(alpha = .13f) else C.Surface, label = "chip")
    val fg by animateColorAsState(if (selected) color else C.Muted, label = "chipfg")
    val edge by animateColorAsState(if (selected) color.copy(alpha = .35f) else C.Line, label = "chipedge")
    Text(
        text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = Modifier.press(onClick = onClick).clip(RoundedCornerShape(8.dp)).background(bg)
            .border(1.dp, edge, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
fun MethodTag(method: String, modifier: Modifier = Modifier.width(56.dp), size: Int = 11) = Text(
    method.take(7), color = methodColor(method), fontSize = size.sp, fontWeight = FontWeight.Bold, fontFamily = Mono,
    maxLines = 1, modifier = modifier,
)

@Composable
fun Pill(text: String, color: Color) = Row(
    Modifier.clip(RoundedCornerShape(7.dp)).background(color.copy(alpha = .12f)).border(1.dp, color.copy(alpha = .25f), RoundedCornerShape(7.dp))
        .padding(horizontal = 8.dp, vertical = 3.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
    Spacer(Modifier.width(6.dp))
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, maxLines = 1)
}

class MenuItem(val label: String, val icon: ImageVector? = null, val color: Color = C.Text, val onClick: () -> Unit)

@Composable
fun MenuButton(items: List<MenuItem>, anchor: @Composable (open: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        anchor { open = true }
        DropdownMenu(open, { open = false }, containerColor = C.Surface2, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, C.Edge)) {
            items.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.label, color = m.color, fontSize = 14.sp) },
                    leadingIcon = m.icon?.let { { Icon(it, null, tint = if (m.color == C.Text) C.Muted else m.color, modifier = Modifier.size(18.dp)) } },
                    onClick = { open = false; m.onClick() },
                )
            }
        }
    }
}

@Composable
fun IconBtn(icon: ImageVector, desc: String, tint: Color = C.Muted, onClick: () -> Unit) =
    Box(Modifier.size(40.dp).clip(CircleShape).press(onClick = onClick), Alignment.Center) { Icon(icon, desc, tint = tint, modifier = Modifier.size(20.dp)) }

@Composable
fun ScreenHeader(title: String, sub: String? = null, actions: @Composable RowScope.() -> Unit = {}) =
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = C.Text, letterSpacing = (-0.6).sp)
            if (sub != null) Text(sub, fontSize = 13.sp, color = C.Faint)
        }
        actions()
    }

@Composable
fun EmptyState(icon: ImageVector, title: String, sub: String, action: (@Composable () -> Unit)? = null) =
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = C.Faint, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(sub, color = C.Muted, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 19.sp)
        if (action != null) { Spacer(Modifier.height(18.dp)); action() }
    }

@Composable
fun TextDialog(title: String, initial: String = "", label: String = "Name", confirm: String = "Save", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.Surface2, shape = RoundedCornerShape(22.dp),
        title = { Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = { Field(v, { v = it }, Modifier.fillMaxWidth(), label, bg = C.Surface, autoFocus = true) },
        confirmButton = { TextButton({ onConfirm(v.trim()) }, enabled = v.isNotBlank()) { Text(confirm, color = if (v.isNotBlank()) C.Green else C.Faint) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = C.Muted) } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Delete", onDismiss: () -> Unit, onConfirm: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss, containerColor = C.Surface2, shape = RoundedCornerShape(22.dp),
    title = { Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
    text = { Text(text, color = C.Muted) },
    confirmButton = { TextButton({ onConfirm(); onDismiss() }) { Text(confirm, color = C.Red) } },
    dismissButton = { TextButton(onDismiss) { Text("Cancel", color = C.Muted) } },
)

fun copyText(ctx: android.content.Context, text: String) =
    ctx.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("Synderz", text))

@Composable
fun Label(text: String) = Text(text.uppercase(), color = C.Faint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))

@Composable
fun DropField(value: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = C.Text) =
    Row(
        modifier.press(onClick = onClick).surface(RoundedCornerShape(10.dp), C.Surface2).padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Rounded.ExpandMore, null, tint = C.Muted, modifier = Modifier.size(18.dp))
    }

/** Resolved 12/24-hour choice, provided once at the app root. */
val LocalClock24 = staticCompositionLocalOf { true }

@Composable
fun rememberClock(seconds: Boolean = true): java.text.SimpleDateFormat {
    val h24 = LocalClock24.current
    return remember(h24, seconds) {
        java.text.SimpleDateFormat(if (h24) (if (seconds) "HH:mm:ss" else "HH:mm") else (if (seconds) "h:mm:ss a" else "h:mm a"), java.util.Locale.getDefault())
    }
}

/** True while the locked dashboard is up: endless animations stop so the screen only redraws when data changes. */
val LocalCalm = staticCompositionLocalOf { false }

class Battery(val pct: Int, val charging: Boolean)

/** Live battery level from the sticky ACTION_BATTERY_CHANGED broadcast; updates only when the level changes. */
@Composable
fun rememberBattery(): Battery? {
    val ctx = LocalContext.current
    var b by remember { mutableStateOf<Battery?>(null) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val rx = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                val level = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                val status = i.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                if (level >= 0) b = Battery(level * 100 / scale, status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL)
            }
        }
        ctx.registerReceiver(rx, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))?.let { rx.onReceive(ctx, it) }
        onDispose { ctx.unregisterReceiver(rx) }
    }
    return b
}
