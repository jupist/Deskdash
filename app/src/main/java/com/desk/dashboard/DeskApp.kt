package com.desk.dashboard

import android.app.Application
import android.content.res.Configuration
import com.desk.dashboard.data.AppearanceStore
import com.desk.dashboard.board.BoardStore
import com.desk.dashboard.media.Ambient
import com.desk.dashboard.system.Apps
import com.desk.dashboard.ui.Look
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class DeskApp : Application() {
    /** Process-wide scope for work that outlives a screen (syncs, refreshes). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Before the first unlock after a reboot, only the alarm runs (direct boot) and normal app
        // storage is still encrypted: reading it would crash the alarm. Start the rest on unlock.
        if (getSystemService(android.os.UserManager::class.java).isUserUnlocked) {
            startUnlocked()
        } else {
            registerReceiver(object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                    unregisterReceiver(this)
                    startUnlocked()
                }
            }, android.content.IntentFilter(android.content.Intent.ACTION_USER_UNLOCKED), RECEIVER_NOT_EXPORTED)
        }
    }

    @Volatile var unlockedStarted = false
        private set

    private fun startUnlocked() {
        if (unlockedStarted) return
        unlockedStarted = true
        // Apply the saved look before the first frame (one small read), then follow changes.
        val appearance = AppearanceStore.flow(this)
        runBlocking { Look.apply(this@DeskApp, appearance.first()) }
        scope.launch { appearance.collect { Look.apply(this@DeskApp, it) } }
        BoardStore.init(this, scope)
        Ambient.init(this)
        Apps.refresh(this) // preload the app list so it opens instantly
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (unlockedStarted) Look.refresh(this) // System light/dark and wallpaper colours
    }

    companion object {
        lateinit var instance: DeskApp
            private set
    }
}
