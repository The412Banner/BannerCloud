package com.banner.cloud.xbox

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Controller state for the native cloud player, from Android key and motion events (no Gamepad API). Button
 * bits follow the xCloud input report (Greenlight's packet.ts): Nexus 2, Menu 4, View 8, A 16, B 32, X 64,
 * Y 128, D-pad 256..2048, shoulders 4096 / 8192, stick clicks 16384 / 32768.
 *
 * Up to four controllers (local co-op, slots 0..3, each Android input device gets its own slot when [coop] is on;
 * otherwise every device drives slot 0). The touch controls and the mouse/keyboard emulation are extra sources for
 * slot 0. [remap], [deadzone] and [invertRightY] are applied to every snapshot.
 */
internal class XboxNativePad {
    data class State(val buttons: Int, val lx: Float, val ly: Float, val rx: Float, val ry: Float, val lt: Float, val rt: Float) {
        /** Greenlight's rule: any button change, a stick moved past 0.08 or held past 0.15, a trigger past 0.01. */
        fun differsFrom(o: State): Boolean = buttons != o.buttons ||
            listOf(lx to o.lx, ly to o.ly, rx to o.rx, ry to o.ry).any { (a, b) -> Math.abs(a - b) > 0.08f || Math.abs(a) > 0.15f } ||
            Math.abs(lt - o.lt) > 0.01f || Math.abs(rt - o.rt) > 0.01f

        companion object { val IDLE = State(0, 0f, 0f, 0f, 0f, 0f, 0f) }
    }

    private class Slot {
        @Volatile var buttons = 0
        @Volatile var hat = 0
        @Volatile var lx = 0f; @Volatile var ly = 0f
        @Volatile var rx = 0f; @Volatile var ry = 0f
        @Volatile var lt = 0f; @Volatile var rt = 0f
        @Volatile var lastInput = 0L
        fun state() = State(buttons or hat, lx, ly, rx, ry, lt, rt)
    }

    private val slots = Array(4) { Slot() }
    private val deviceSlot = HashMap<Int, Int>()
    /** Called after any change (instant controller input). */
    @Volatile var onChanged: (() -> Unit)? = null
    /** Called when a co-op slot gains (true) or loses (false) its controller. */
    @Volatile var onSlotChanged: ((index: Int, added: Boolean) -> Unit)? = null

    /** Buttons pressed on behalf of the app (the menu's Xbox button), OR-ed into slot 0. */
    @Volatile var overrideButtons = 0
    /** The on-screen controller's state (slot 0), or null when it is hidden. */
    @Volatile var touchState: State? = null
    /** The mouse-and-keyboard emulation's state (slot 0), or null when it is off. */
    @Volatile var mkbState: State? = null

    @Volatile var coop = false
        set(value) { if (field != value) { synchronized(deviceSlot) { deviceSlot.clear() }; field = value } }
    /** remap[i] = the bit (0..15) that source bit i is sent as. */
    @Volatile var remap: IntArray = IntArray(16) { it }
    @Volatile var deadzone = 0.08f
    @Volatile var invertRightY = false

    /** The time of the last physical controller input (ms, uptime), for hiding the touch controls. */
    val lastPhysicalInput get() = slots.maxOf { it.lastInput }

    /** Slots that have a controller (slot 0 always counts). */
    fun activeSlots(): List<Int> = synchronized(deviceSlot) { (listOf(0) + deviceSlot.values).distinct().sorted() }

    /** The input device driving [index] (co-op), for per-controller rumble. */
    fun deviceFor(index: Int): Int? = synchronized(deviceSlot) { deviceSlot.entries.firstOrNull { it.value == index }?.key }

    private fun slotFor(deviceId: Int): Slot {
        if (!coop) return slots[0]
        var added = -1
        val index = synchronized(deviceSlot) {
            deviceSlot[deviceId] ?: run {
                val used = deviceSlot.values.toSet()
                val free = (0..3).firstOrNull { it !in used } ?: 0
                deviceSlot[deviceId] = free
                if (free > 0) added = free
                free
            }
        }
        if (added > 0) onSlotChanged?.invoke(added, true)
        return slots[index]
    }

    /** A controller went away: its co-op slot is freed. */
    fun onDeviceRemoved(deviceId: Int) {
        val index = synchronized(deviceSlot) { deviceSlot.remove(deviceId) } ?: return
        slots[index].apply { buttons = 0; hat = 0; lx = 0f; ly = 0f; rx = 0f; ry = 0f; lt = 0f; rt = 0f }
        if (index > 0) onSlotChanged?.invoke(index, false)
        onChanged?.invoke()
    }

    fun snapshot(index: Int = 0): State {
        val physical = slots[index.coerceIn(0, 3)].state()
        var merged = physical
        if (index == 0) {
            listOfNotNull(touchState, mkbState).forEach { v ->
                merged = State(merged.buttons or v.buttons, pick(merged.lx, v.lx), pick(merged.ly, v.ly), pick(merged.rx, v.rx),
                    pick(merged.ry, v.ry), maxOf(merged.lt, v.lt), maxOf(merged.rt, v.rt))
            }
            merged = merged.copy(buttons = merged.buttons or overrideButtons)
        }
        val map = remap
        var buttons = 0
        for (bit in 0..15) if (merged.buttons and (1 shl bit) != 0) buttons = buttons or (1 shl map[bit])
        return merged.copy(buttons = buttons, ry = if (invertRightY) -merged.ry else merged.ry)
    }

    private fun pick(a: Float, b: Float) = if (Math.abs(b) > Math.abs(a)) b else a

    /** True when the event was a controller button this pad tracks. */
    fun onKey(event: KeyEvent): Boolean {
        val bit = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> 16
            KeyEvent.KEYCODE_BUTTON_B -> 32
            KeyEvent.KEYCODE_BUTTON_X -> 64
            KeyEvent.KEYCODE_BUTTON_Y -> 128
            KeyEvent.KEYCODE_DPAD_UP -> 256
            KeyEvent.KEYCODE_DPAD_DOWN -> 512
            KeyEvent.KEYCODE_DPAD_LEFT -> 1024
            KeyEvent.KEYCODE_DPAD_RIGHT -> 2048
            KeyEvent.KEYCODE_BUTTON_L1 -> 4096
            KeyEvent.KEYCODE_BUTTON_R1 -> 8192
            KeyEvent.KEYCODE_BUTTON_THUMBL -> 16384
            KeyEvent.KEYCODE_BUTTON_THUMBR -> 32768
            KeyEvent.KEYCODE_BUTTON_START -> 4
            KeyEvent.KEYCODE_BUTTON_SELECT -> 8
            KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2 -> -1
            else -> return false
        }
        // D-pad keys from a keyboard are not a controller.
        if (bit in 256..2048 && event.source and InputDevice.SOURCE_GAMEPAD != InputDevice.SOURCE_GAMEPAD &&
            event.source and InputDevice.SOURCE_DPAD != InputDevice.SOURCE_DPAD) return false
        val slot = slotFor(event.deviceId)
        slot.lastInput = android.os.SystemClock.uptimeMillis()
        val down = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            // Digital triggers on pads without analog axes.
            KeyEvent.KEYCODE_BUTTON_L2 -> slot.lt = if (down) 1f else 0f
            KeyEvent.KEYCODE_BUTTON_R2 -> slot.rt = if (down) 1f else 0f
            else -> slot.buttons = if (down) slot.buttons or bit else slot.buttons and bit.inv()
        }
        onChanged?.invoke()
        return true
    }

    /** True when the event came from a joystick (sticks, triggers, hat). */
    fun onMotion(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK || event.action != MotionEvent.ACTION_MOVE) return false
        val device = event.device
        val slot = slotFor(event.deviceId)
        slot.lastInput = android.os.SystemClock.uptimeMillis()
        val dz = deadzone
        fun axis(code: Int): Float {
            val value = event.getAxisValue(code)
            val flat = device?.getMotionRange(code, event.source)?.flat ?: 0f
            return if (Math.abs(value) > maxOf(flat, dz)) value else 0f
        }
        slot.lx = axis(MotionEvent.AXIS_X); slot.ly = axis(MotionEvent.AXIS_Y)
        slot.rx = axis(MotionEvent.AXIS_Z); slot.ry = axis(MotionEvent.AXIS_RZ)
        slot.lt = maxOf(axis(MotionEvent.AXIS_LTRIGGER), axis(MotionEvent.AXIS_BRAKE))
        slot.rt = maxOf(axis(MotionEvent.AXIS_RTRIGGER), axis(MotionEvent.AXIS_GAS))
        val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X); val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        slot.hat = (if (hy < -0.5f) 256 else 0) or (if (hy > 0.5f) 512 else 0) or (if (hx < -0.5f) 1024 else 0) or (if (hx > 0.5f) 2048 else 0)
        onChanged?.invoke()
        return true
    }

    fun reset() {
        slots.forEach { it.apply { buttons = 0; hat = 0; lx = 0f; ly = 0f; rx = 0f; ry = 0f; lt = 0f; rt = 0f } }
        overrideButtons = 0; touchState = null; mkbState = null
    }

    companion object {
        /** The buttons in report-bit order (bit 0 and the unused bit are left out of remapping UIs). */
        val BUTTON_NAMES = listOf(1 to "Xbox", 2 to "Menu", 3 to "View", 4 to "A", 5 to "B", 6 to "X", 7 to "Y",
            8 to "D-pad up", 9 to "D-pad down", 10 to "D-pad left", 11 to "D-pad right", 12 to "LB", 13 to "RB", 14 to "Left stick click", 15 to "Right stick click")

        /** "4=5,5=4" (source bit = target bit) → table; swaps first, then the custom list. */
        fun remapTable(swapAB: Boolean, swapXY: Boolean, custom: String): IntArray {
            val table = IntArray(16) { it }
            if (swapAB) { table[4] = 5; table[5] = 4 }
            if (swapXY) { table[6] = 7; table[7] = 6 }
            custom.split(',').forEach { pair ->
                val (from, to) = pair.split('=').mapNotNull { it.trim().toIntOrNull() }.takeIf { it.size == 2 } ?: return@forEach
                if (from in 1..15 && to in 1..15) table[from] = to
            }
            return table
        }
    }
}
