package com.neighborly.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

@HiltAndroidApp
class NeighborlyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // OpenStreetMap tile servers require an identifying user agent.
        Configuration.getInstance().apply {
            userAgentValue = "$packageName/${BuildConfig.VERSION_NAME}"
            osmdroidBasePath = cacheDir.resolve("osmdroid")
            osmdroidTileCache = cacheDir.resolve("osmdroid/tiles")
        }
    }
}
