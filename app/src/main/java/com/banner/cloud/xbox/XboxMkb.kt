package com.banner.cloud.xbox

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.sqrt

/**
 * Mouse and keyboard for the native player, in two modes:
 * - "controller": an emulated controller on slot 0, with Better xCloud's "Standard" layout (redphx/better-xcloud,
 *   MIT, src/utils/local-db/mkb-mapping-presets-table.ts): WASD left stick, mouse right stick, Space/E = A,
 *   C/Backspace = B, R = X, V = Y, Q = LB, F = RB, left click = RT, right click = LT, arrows/1-4 = D-pad, Enter =
 *   Menu, Tab = View, ` = Xbox, X = L3, Z = R3, H/U/J/K = right stick. Mouse to stick: Better xCloud's formula.
 * - "native": real mouse (report 32) and keyboard (report 64) input for games that support them.
 * Mouse movement needs pointer capture (the player asks for it on the first click).
 */
internal class XboxMkb(private val pad: XboxNativePad, private val protocol: () -> XboxNativeProtocol?) {
    @Volatile var mode = "off"
        set(value) { field = value; held.clear(); mouseButtons = 0; mouseX = 0f; mouseY = 0f; pad.mkbState = null; pad.onChanged?.invoke() }
    @Volatile var sensitivity = 1f

    private val held = HashSet<Int>()
    private var mouseButtons = 0
    private var mouseX = 0f
    private var mouseY = 0f
    private var mouseAt = 0L

    /** True when the event is from a keyboard and was used. */
    fun onKey(event: KeyEvent): Boolean {
        if (mode == "off") return false
        if (event.source and InputDevice.SOURCE_KEYBOARD != InputDevice.SOURCE_KEYBOARD) return false
        if (event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) return false
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return false
        val down = event.action == KeyEvent.ACTION_DOWN
        if (mode == "native") {
            val vk = virtualKey(event.keyCode)
            if (vk == 0) return false
            if (event.repeatCount == 0 || !down) protocol()?.sendKey(vk, down)
            return true
        }
        if (KEY_MAP[event.keyCode] == null) return false
        synchronized(held) { if (down) held += event.keyCode else held -= event.keyCode }
        publish()
        return true
    }

    /** A captured (relative) mouse event. */
    fun onCapturedPointer(event: MotionEvent): Boolean {
        if (mode == "off") return false
        val buttons = (if (event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)) 1 else 0) or
            (if (event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)) 2 else 0) or
            (if (event.isButtonPressed(MotionEvent.BUTTON_TERTIARY)) 4 else 0)
        val dx = event.x; val dy = event.y
        val wheel = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
        val hwheel = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
        if (mode == "native") {
            protocol()?.sendMouse((dx * 2 * sensitivity).toInt(), (dy * 2 * sensitivity).toInt(), (hwheel * 120).toInt(), (wheel * 120).toInt(), buttons)
            return true
        }
        mouseButtons = buttons
        if (dx != 0f || dy != 0f) {
            // Better xCloud: x = movement × sensitivity(0.1 at 100 %), length kept between 0.2 and 1.1.
            var x = dx * 0.1f * sensitivity; var y = dy * 0.1f * sensitivity
            val len = sqrt(x * x + y * y)
            if (len > 0f && len < 0.2f) { x *= 0.2f / len; y *= 0.2f / len } else if (len > 1.1f) { x *= 1.1f / len; y *= 1.1f / len }
            mouseX = x.coerceIn(-1f, 1f); mouseY = y.coerceIn(-1f, 1f); mouseAt = SystemClock.uptimeMillis()
        }
        publish()
        return true
    }

    /** The stick returns to centre 50 ms after the mouse stops (call from the input heartbeat). */
    fun tick() {
        if (mode != "controller" || (mouseX == 0f && mouseY == 0f)) return
        if (SystemClock.uptimeMillis() - mouseAt > 50) { mouseX = 0f; mouseY = 0f; publish() }
    }

    private fun publish() {
        if (mode != "controller") { pad.mkbState = null; return }
        val keys = synchronized(held) { held.toSet() }
        var buttons = 0; var lt = 0f; var rt = 0f
        var lx = 0f; var ly = 0f; var rx = mouseX; var ry = mouseY
        for (key in keys) {
            when (val target = KEY_MAP[key] ?: continue) {
                LS_UP -> ly -= 1f; LS_DOWN -> ly += 1f; LS_LEFT -> lx -= 1f; LS_RIGHT -> lx += 1f
                RS_UP -> ry -= 1f; RS_DOWN -> ry += 1f; RS_LEFT -> rx -= 1f; RS_RIGHT -> rx += 1f
                else -> buttons = buttons or target
            }
        }
        if (mouseButtons and 1 != 0) rt = 1f
        if (mouseButtons and 2 != 0) lt = 1f
        fun norm(a: Float, b: Float): Pair<Float, Float> { val l = sqrt(a * a + b * b); return if (l > 1f) (a / l) to (b / l) else a to b }
        val (nlx, nly) = norm(lx, ly); val (nrx, nry) = norm(rx.coerceIn(-1f, 1f), ry.coerceIn(-1f, 1f))
        pad.mkbState = XboxNativePad.State(buttons, nlx, nly, nrx, nry, lt, rt)
        pad.onChanged?.invoke()
    }

    companion object {
        private const val LS_UP = -1; private const val LS_DOWN = -2; private const val LS_LEFT = -3; private const val LS_RIGHT = -4
        private const val RS_UP = -5; private const val RS_DOWN = -6; private const val RS_LEFT = -7; private const val RS_RIGHT = -8

        private val KEY_MAP: Map<Int, Int> = mapOf(
            KeyEvent.KEYCODE_W to LS_UP, KeyEvent.KEYCODE_S to LS_DOWN, KeyEvent.KEYCODE_A to LS_LEFT, KeyEvent.KEYCODE_D to LS_RIGHT,
            KeyEvent.KEYCODE_U to RS_UP, KeyEvent.KEYCODE_J to RS_DOWN, KeyEvent.KEYCODE_H to RS_LEFT, KeyEvent.KEYCODE_K to RS_RIGHT,
            KeyEvent.KEYCODE_SPACE to 16, KeyEvent.KEYCODE_E to 16,
            KeyEvent.KEYCODE_C to 32, KeyEvent.KEYCODE_DEL to 32,
            KeyEvent.KEYCODE_R to 64, KeyEvent.KEYCODE_V to 128,
            KeyEvent.KEYCODE_DPAD_UP to 256, KeyEvent.KEYCODE_DPAD_DOWN to 512, KeyEvent.KEYCODE_DPAD_LEFT to 1024, KeyEvent.KEYCODE_DPAD_RIGHT to 2048,
            KeyEvent.KEYCODE_1 to 256, KeyEvent.KEYCODE_2 to 512, KeyEvent.KEYCODE_3 to 1024, KeyEvent.KEYCODE_4 to 2048,
            KeyEvent.KEYCODE_ENTER to 4, KeyEvent.KEYCODE_TAB to 8, KeyEvent.KEYCODE_GRAVE to 2,
            KeyEvent.KEYCODE_Q to 4096, KeyEvent.KEYCODE_F to 8192,
            KeyEvent.KEYCODE_X to 16384, KeyEvent.KEYCODE_Z to 32768,
        )

        /** Android key code → the browser's keyCode (what the stream's keyboard report carries); 0 = none. */
        fun virtualKey(code: Int): Int = when (code) {
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> 65 + (code - KeyEvent.KEYCODE_A)
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> 48 + (code - KeyEvent.KEYCODE_0)
            in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> 112 + (code - KeyEvent.KEYCODE_F1)
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> 96 + (code - KeyEvent.KEYCODE_NUMPAD_0)
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> 13
            KeyEvent.KEYCODE_ESCAPE -> 27
            KeyEvent.KEYCODE_SPACE -> 32
            KeyEvent.KEYCODE_TAB -> 9
            KeyEvent.KEYCODE_DEL -> 8
            KeyEvent.KEYCODE_FORWARD_DEL -> 46
            KeyEvent.KEYCODE_INSERT -> 45
            KeyEvent.KEYCODE_MOVE_HOME -> 36
            KeyEvent.KEYCODE_MOVE_END -> 35
            KeyEvent.KEYCODE_PAGE_UP -> 33
            KeyEvent.KEYCODE_PAGE_DOWN -> 34
            KeyEvent.KEYCODE_DPAD_LEFT -> 37
            KeyEvent.KEYCODE_DPAD_UP -> 38
            KeyEvent.KEYCODE_DPAD_RIGHT -> 39
            KeyEvent.KEYCODE_DPAD_DOWN -> 40
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> 16
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> 17
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> 18
            KeyEvent.KEYCODE_CAPS_LOCK -> 20
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> 91
            KeyEvent.KEYCODE_MINUS -> 189
            KeyEvent.KEYCODE_EQUALS -> 187
            KeyEvent.KEYCODE_LEFT_BRACKET -> 219
            KeyEvent.KEYCODE_RIGHT_BRACKET -> 221
            KeyEvent.KEYCODE_BACKSLASH -> 220
            KeyEvent.KEYCODE_SEMICOLON -> 186
            KeyEvent.KEYCODE_APOSTROPHE -> 222
            KeyEvent.KEYCODE_GRAVE -> 192
            KeyEvent.KEYCODE_COMMA -> 188
            KeyEvent.KEYCODE_PERIOD -> 190
            KeyEvent.KEYCODE_SLASH -> 191
            else -> 0
        }
    }
}
