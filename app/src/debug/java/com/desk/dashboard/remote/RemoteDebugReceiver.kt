package com.desk.dashboard.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Debug builds only: lets tests drive the tablet side over adb, for when nobody can reach the
 * screen. `adb shell am broadcast -n com.desk.dashboard/.remote.RemoteDebugReceiver --es do pair`
 * opens pairing and logs the code; `--es do status` logs paired and connected phones.
 */
class RemoteDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getStringExtra("do")) {
            "pair" -> Log.i("RemoteDebug", "code ${RemoteHost.openPairing()} fingerprint ${RemoteHost.fingerprint}")
            "status" -> Log.i("RemoteDebug", "enabled ${RemoteHost.enabled.value} paired ${RemoteHost.paired.value.values} connected ${RemoteHost.connected.value} screen ${RemoteHost.screen}")
        }
    }
}
