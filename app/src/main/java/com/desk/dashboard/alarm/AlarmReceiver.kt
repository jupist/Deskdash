package com.desk.dashboard.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Runs [block] off the main thread, keeping the receiver alive until it finishes. */
private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try { block() } finally { pending.finish() }
    }
}

/** Fired by AlarmManager: start ringing at once, then book the next occurrence. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val snooze = intent.getBooleanExtra(Alarms.EXTRA_SNOOZE, false)
        AlarmService.ring(context)
        async { Alarms.onFired(context, snooze) }
    }
}

/** AlarmManager forgets alarms on reboot, and times shift with clock/zone changes. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        async { Alarms.reschedule(context) }
        // Timers live in normal (credential) storage: only readable once the user has unlocked.
        if (context.getSystemService(android.os.UserManager::class.java).isUserUnlocked) Timers.rescheduleAll(context)
    }
}
