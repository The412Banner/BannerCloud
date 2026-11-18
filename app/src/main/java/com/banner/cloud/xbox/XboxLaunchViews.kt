package com.banner.cloud.xbox

import java.io.File

/** This app never starts installed games, so no launch views exist; kept for Xodus.Child's cleanup. */
internal object XboxLaunchViews {
    fun remove(directory: File) { directory.deleteRecursively() }
}
