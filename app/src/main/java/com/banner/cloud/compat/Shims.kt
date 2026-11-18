package com.banner.cloud.compat

import android.content.Context
import android.preference.PreferenceManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import java.io.File

/** Frame generation defaults, the same values Bannerlator uses for games (Container.java). */
object FgDefaults {
    const val FRAMEGEN_DEFAULT_FLOW_SCALE = 0.6f
    const val LSFG_DEFAULT_FLOW_SCALE = 0.80f
    /** win-fg model 3 = optical flow (device-proven best base-FPS retention in Bannerlator). */
    const val FRAMEGEN_DEFAULT_MODEL = 3
}

/**
 * LSFG needs the user's own Lossless.dll, which this app does not import, so it always reports
 * "not available" and the player uses Win-FG or WFG.
 */
object LsfgNative {
    const val STATUS_OK = 0
    private const val STATUS_NO_DLL = 1
    fun isDllAvailable(context: Context): Boolean = false
    fun ensureCache(context: Context, preferFp16: Boolean): Int = STATUS_NO_DLL
    fun explain(status: Int): String = if (status == STATUS_OK) "ok" else "Lossless.dll is not imported"
    fun cacheFile(context: Context): File = File(context.filesDir, "lsfg/shaders.cache")
}

/** The controller button that opens the player menu (0 = off), as in Bannerlator's controller settings. */
object ControllerPrefs {
    private const val KEY_MENU_BUTTON = "menu_button_keycode"
    @Suppress("DEPRECATION")
    fun getMenuButtonKeyCode(context: Context): Int =
        PreferenceManager.getDefaultSharedPreferences(context).getInt(KEY_MENU_BUTTON, 0)
    @Suppress("DEPRECATION")
    fun setMenuButtonKeyCode(context: Context, keyCode: Int) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(KEY_MENU_BUTTON, keyCode).apply()
    }
}

/** Material 3 alert dialog with the parameters of Bannerlator's OutlinedAlertDialog. */
@Composable
internal fun OutlinedAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) = AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier,
    dismissButton = dismissButton, icon = icon, title = title, text = text, shape = shape, containerColor = containerColor,
    iconContentColor = iconContentColor, titleContentColor = titleContentColor, textContentColor = textContentColor,
    tonalElevation = tonalElevation, properties = properties)

/** Bannerlator wraps text fields for its on-screen controller keyboard; here the system keyboard is used. */
@Composable
internal fun XmbKeyboardWindow(content: @Composable () -> Unit) = content()
