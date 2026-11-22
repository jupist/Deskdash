package com.desk.dashboard.system

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** One approximate fix for weather. Needs ACCESS_COARSE_LOCATION (checked by the caller). */
object DeviceLocation {
    fun enabled(c: Context) = c.getSystemService(LocationManager::class.java).isLocationEnabled

    @SuppressLint("MissingPermission")
    suspend fun current(c: Context): Location? {
        val lm = c.getSystemService(LocationManager::class.java)
        // Network (Wi-Fi) is fast and enough for weather; then the fused provider; then GPS.
        val providers = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) || it == "fused" && lm.allProviders.contains("fused") }
        for (p in providers) {
            val fix = withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val cancel = CancellationSignal()
                    cont.invokeOnCancellation { cancel.cancel() }
                    runCatching { lm.getCurrentLocation(p, cancel, c.mainExecutor) { cont.resume(it) } }
                        .onFailure { cont.resume(null) }
                }
            }
            if (fix != null) return fix
        }
        return lm.allProviders.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }
}
