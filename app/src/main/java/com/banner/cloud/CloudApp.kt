package com.banner.cloud

import android.app.Application
import com.banner.cloud.xbox.XboxDiag

class CloudApp : Application() {
    override fun onCreate() {
        super.onCreate()
        XboxDiag.dir = getExternalFilesDir(null)
    }
}
