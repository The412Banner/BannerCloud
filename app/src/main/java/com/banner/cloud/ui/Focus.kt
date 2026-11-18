package com.banner.cloud.ui

import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Controller commands the screens act on (the activity maps the buttons). */
enum class PadCommand { PREV_TAB, NEXT_TAB, REFRESH, SEARCH, SETTINGS }

/** Controller A and B as select and back (D-pad centre / Back), so every screen handles them the same way. */
fun mapFaceButtons(event: KeyEvent): KeyEvent? {
    val mapped = when (event.keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
        KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
        else -> return null
    }
    return KeyEvent(event.downTime, event.eventTime, event.action, mapped, event.repeatCount, event.metaState,
        event.deviceId, event.scanCode, event.flags, event.source)
}

/** True when a game controller is connected. */
fun hasGamepad(): Boolean = InputDevice.getDeviceIds().any { id ->
    val sources = InputDevice.getDevice(id)?.sources ?: 0
    (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
        (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
}

/**
 * The focus look for controller navigation: a springy zoom and an accent outline that fades in. Put it
 * before the clickable/focusable modifier so it sees that element's focus.
 */
fun Modifier.focusHighlight(shape: Shape, zoom: Float = 1.05f, width: Dp = 2.dp): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) zoom else 1f,
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "focus-zoom")
    val edge by animateColorAsState(if (focused) Accent else Color.Transparent, tween(160), label = "focus-edge")
    this
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .border(width, edge, shape)
}
