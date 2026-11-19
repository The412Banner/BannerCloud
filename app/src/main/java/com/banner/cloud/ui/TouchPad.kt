package com.banner.cloud.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.banner.cloud.xbox.XboxNativePad
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The on-screen controller for the native player (no Microsoft touch layouts: a standard pad). Multi-touch; each
 * finger keeps the control it landed on. Sticks are fixed rings with a knob; buttons light while held. The state
 * goes to [onState] (null when no finger is down). When [visible] is false, a touch only calls [onWake].
 */
@Composable
internal fun TouchPad(visible: Boolean, opacity: Float, onState: (XboxNativePad.State?) -> Unit, onWake: () -> Unit) {
    val density = LocalDensity.current.density
    val pressed = remember { mutableStateMapOf<String, Boolean>() }
    var leftKnob by remember { mutableStateOf(Offset.Zero) }
    var rightKnob by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().pointerInput(visible) {
        if (!visible) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    if (e.changes.any { it.pressed && !it.previousPressed }) onWake()
                }
            }
        }
        awaitPointerEventScope {
            val owners = HashMap<PointerId, Control>()
            while (true) {
                val event = awaitPointerEvent()
                val layout = layout(Size(size.width.toFloat(), size.height.toFloat()), density)
                for (c in event.changes) {
                    if (c.pressed && !c.previousPressed) layout.firstOrNull { it.hit(c.position) }?.let { owners[c.id] = it }
                    if (!c.pressed) owners.remove(c.id)
                    if (owners.containsKey(c.id)) c.consume()
                }
                var buttons = 0; var lt = 0f; var rt = 0f
                var left = Offset.Zero; var right = Offset.Zero
                val held = HashSet<String>()
                for (c in event.changes) {
                    val control = owners[c.id] ?: continue
                    held += control.name
                    when (control.kind) {
                        Kind.LEFT_STICK -> left = control.stick(c.position)
                        Kind.RIGHT_STICK -> right = control.stick(c.position)
                        Kind.LT -> lt = 1f
                        Kind.RT -> rt = 1f
                        Kind.BUTTON -> buttons = buttons or control.bit
                    }
                }
                leftKnob = left; rightKnob = right
                pressed.keys.retainAll(held); held.forEach { pressed[it] = true }
                onState(if (owners.isEmpty()) null else XboxNativePad.State(buttons, left.x, left.y, right.x, right.y, lt, rt))
            }
        }
    }) {
        if (visible) Canvas(Modifier.fillMaxSize()) {
            val controls = layout(size, density)
            val alpha = opacity.coerceIn(0.1f, 1f)
            for (control in controls) drawControl(control, pressed[control.name] == true, alpha,
                when (control.kind) { Kind.LEFT_STICK -> leftKnob; Kind.RIGHT_STICK -> rightKnob; else -> Offset.Zero }, density)
        }
    }
}

private enum class Kind { BUTTON, LEFT_STICK, RIGHT_STICK, LT, RT }

private class Control(val name: String, val label: String, val kind: Kind, val bit: Int, val center: Offset, val radius: Float) {
    fun hit(p: Offset): Boolean {
        val d = p - center
        val reach = if (kind == Kind.LEFT_STICK || kind == Kind.RIGHT_STICK) radius * 1.6f else radius * 1.25f
        return d.x * d.x + d.y * d.y <= reach * reach
    }
    /** Stick deflection -1..1 (y down = positive, as Android's AXIS_Y). */
    fun stick(p: Offset): Offset {
        val d = (p - center) / radius
        val len = sqrt(d.x * d.x + d.y * d.y)
        return if (len > 1f) d / len else d
    }
}

/** The pad's controls for a screen of [size] px (landscape-friendly; scales with the short side). */
@Suppress("UNUSED_PARAMETER")
private fun layout(size: Size, density: Float): List<Control> {
    // One design unit = 1/411 of the short side (the layout is drawn for a 411-unit-tall landscape screen).
    val s = min(size.width, size.height) / 411f
    val w = size.width; val h = size.height
    val button = 26f * s
    return listOf(
        Control("ls", "", Kind.LEFT_STICK, 0, Offset(118f * s, h - 120f * s), 58f * s),
        Control("rs", "", Kind.RIGHT_STICK, 0, Offset(w - 250f * s, h - 78f * s), 48f * s),
        Control("a", "A", Kind.BUTTON, 16, Offset(w - 100f * s, h - 78f * s), button),
        Control("b", "B", Kind.BUTTON, 32, Offset(w - 46f * s, h - 132f * s), button),
        Control("x", "X", Kind.BUTTON, 64, Offset(w - 154f * s, h - 132f * s), button),
        Control("y", "Y", Kind.BUTTON, 128, Offset(w - 100f * s, h - 186f * s), button),
        Control("up", "▲", Kind.BUTTON, 256, Offset(250f * s, h - 112f * s), 20f * s),
        Control("down", "▼", Kind.BUTTON, 512, Offset(250f * s, h - 40f * s), 20f * s),
        Control("left", "◀", Kind.BUTTON, 1024, Offset(214f * s, h - 76f * s), 20f * s),
        Control("right", "▶", Kind.BUTTON, 2048, Offset(286f * s, h - 76f * s), 20f * s),
        Control("lb", "LB", Kind.BUTTON, 4096, Offset(70f * s, 92f * s), 24f * s),
        Control("rb", "RB", Kind.BUTTON, 8192, Offset(w - 70f * s, 92f * s), 24f * s),
        Control("lt", "LT", Kind.LT, 0, Offset(70f * s, 36f * s), 24f * s),
        Control("rt", "RT", Kind.RT, 0, Offset(w - 70f * s, 36f * s), 24f * s),
        Control("l3", "L3", Kind.BUTTON, 16384, Offset(40f * s, h - 210f * s), 18f * s),
        Control("r3", "R3", Kind.BUTTON, 32768, Offset(w - 320f * s, h - 150f * s), 18f * s),
        Control("view", "VIEW", Kind.BUTTON, 8, Offset(w / 2f - 56f * s, 30f * s), 22f * s),
        Control("menu", "MENU", Kind.BUTTON, 4, Offset(w / 2f + 56f * s, 30f * s), 22f * s),
    )
}

private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }

private fun DrawScope.drawControl(c: Control, down: Boolean, alpha: Float, knob: Offset, density: Float) {
    val fill = Color.White.copy(alpha = (if (down) 0.45f else 0.16f) * alpha)
    val edge = Color.White.copy(alpha = 0.55f * alpha)
    when (c.kind) {
        Kind.LEFT_STICK, Kind.RIGHT_STICK -> {
            drawCircle(Color.White.copy(alpha = 0.10f * alpha), c.radius, c.center)
            drawCircle(edge, c.radius, c.center, style = Stroke(1.5f * density))
            drawCircle(Color.White.copy(alpha = (if (down) 0.6f else 0.35f) * alpha), c.radius * 0.45f, c.center + knob * c.radius)
        }
        else -> {
            val tint = when (c.name) { "a" -> Color(0xFF4CC24C); "b" -> Color(0xFFE5484D); "x" -> Color(0xFF3E8EF7); "y" -> Color(0xFFF5C518); else -> Color.White }
            drawCircle(if (c.name in setOf("a", "b", "x", "y")) tint.copy(alpha = (if (down) 0.6f else 0.25f) * alpha) else fill, c.radius, c.center)
            drawCircle(edge, c.radius, c.center, style = Stroke(1.2f * density))
            labelPaint.color = android.graphics.Color.argb((235 * alpha).toInt().coerceIn(40, 255), 255, 255, 255)
            labelPaint.textSize = c.radius * (if (c.label.length > 2) 0.5f else 0.8f)
            drawContext.canvas.nativeCanvas.drawText(c.label, c.center.x, c.center.y + labelPaint.textSize * 0.35f, labelPaint)
        }
    }
}
