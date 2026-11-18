package com.banner.cloud.xbox

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostics: logcat (tag "BannerXbox") and a small rolling file in the app's external files folder
 * (Android/data/<package>/files/xbox_diag.txt), to hand over when something fails. Only step names, error classes,
 * exit codes and the backend's own allow-listed diagnostic lines go here; never account names, tokens or
 * game data.
 */
object XboxDiag {
    private const val TAG = "BannerXbox"
    private const val MAX_BYTES = 512 * 1024L
    private val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /** Set by the Application at start-up (its external files folder; no storage permission needed). */
    @Volatile var dir: File? = null

    fun file(): File = File(dir ?: File(Environment.getExternalStorageDirectory(), "Android/data/com.banner.cloud/files"), "xbox_diag.txt")

    @Synchronized fun log(app: Context?, message: String) {
        // Lines are allow-listed where they are made; the shared scrubber is the backstop (account names, emails, tokens).
        val line = com.banner.cloud.compat.LogRedactor.redact(message)
        Log.i(TAG, line)
        runCatching {
            val f = file()
            f.parentFile?.mkdirs()
            if (f.length() > MAX_BYTES) {
                val old = File(f.path + ".1"); old.delete(); f.renameTo(old)
            }
            f.appendText("${time.format(Date())} [${android.os.Process.myPid()}] $line\n")
        }
    }
}
