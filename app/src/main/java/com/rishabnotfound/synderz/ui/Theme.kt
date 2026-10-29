package com.rishabnotfound.synderz.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rishabnotfound.synderz.R

object C {
    val Bg = Color(0xFF090B0F)
    val Surface = Color(0xFF0F1318)
    val Surface2 = Color(0xFF151A21)
    val Surface3 = Color(0xFF1C232C)
    val Line = Color(0xFF222A35)
    val Text = Color(0xFFE8EDF2)
    val Muted = Color(0xFF8A96A6)
    val Faint = Color(0xFF525D6B)
    val Green = Color(0xFF34E0A1)
    val Blue = Color(0xFF5AA9FF)
    val Amber = Color(0xFFF5B84B)
    val Red = Color(0xFFFF6B6B)
    val Purple = Color(0xFFB794FF)
    val Teal = Color(0xFF5EEAD4)
    val Pink = Color(0xFFF38BC8)
    val Accent = Brush.linearGradient(listOf(Green, Blue))

    /** Top edge catches light, bottom edge falls off: the cheap trick behind most "premium" dark UIs. */
    val Edge = Brush.verticalGradient(listOf(Color.White.copy(alpha = .10f), Color.White.copy(alpha = .03f)))
}

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, w: Int) = Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Sans = FontFamily(listOf(400, 500, 600, 700).map { variable(R.font.geist, it) })
val Mono = FontFamily(listOf(400, 500, 700).map { variable(R.font.jetbrains_mono, it) })

fun methodColor(m: String) = when (m.uppercase()) {
    "GET" -> C.Green
    "POST" -> C.Blue
    "PUT" -> C.Amber
    "PATCH" -> C.Purple
    "DELETE" -> C.Red
    "HEAD" -> C.Teal
    "OPTIONS" -> C.Pink
    else -> C.Muted
}

fun statusColor(code: Int) = when (code) {
    in 200..299 -> C.Green
    in 300..399 -> C.Blue
    in 400..499 -> C.Amber
    else -> C.Red
}

fun speedColor(ms: Long) = when {
    ms < 300 -> C.Green
    ms < 1000 -> C.Amber
    else -> C.Red
}

/** Layered surface: fill + lit hairline edge. */
fun Modifier.surface(shape: Shape = RoundedCornerShape(14.dp), color: Color = C.Surface) =
    clip(shape).background(color).border(1.dp, C.Edge, shape)

/** Click with a subtle spring "give" under the finger. */
fun Modifier.press(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) .95f else 1f, spring(dampingRatio = .6f, stiffness = 900f), label = "press")
    graphicsLayer { scaleX = s; scaleY = s }.clickable(src, ripple(color = C.Text), enabled, onClick = onClick)
}

private fun Typography.withFont(f: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = f)
    return copy(
        displayLarge.f(), displayMedium.f(), displaySmall.f(), headlineLarge.f(), headlineMedium.f(), headlineSmall.f(),
        titleLarge.f(), titleMedium.f(), titleSmall.f(), bodyLarge.f(), bodyMedium.f(), bodySmall.f(),
        labelLarge.f(), labelMedium.f(), labelSmall.f(),
    )
}

@Composable
fun SynderzTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary = C.Green, onPrimary = Color(0xFF00210F),
        secondary = C.Blue, onSecondary = Color(0xFF001A33),
        background = C.Bg, onBackground = C.Text,
        surface = C.Surface, onSurface = C.Text,
        surfaceVariant = C.Surface2, onSurfaceVariant = C.Muted,
        surfaceContainerLowest = C.Bg, surfaceContainerLow = C.Surface, surfaceContainer = C.Surface,
        surfaceContainerHigh = C.Surface2, surfaceContainerHighest = C.Surface3,
        outline = C.Line, outlineVariant = C.Line, error = C.Red,
    ),
    typography = Typography().withFont(Sans),
    content = content,
)
