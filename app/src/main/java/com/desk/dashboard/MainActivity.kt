package com.desk.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.dashboard.data.DisplaySettings
import com.desk.dashboard.system.Device
import com.desk.dashboard.system.rememberBattery
import com.desk.dashboard.system.rememberMinute
import com.desk.dashboard.ui.AppsScreen
import com.desk.dashboard.ui.BatteryPrompt
import com.desk.dashboard.ui.NightScreen
import com.desk.dashboard.ui.setWindowBrightness
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.desk.dashboard.data.DashDb
import com.desk.dashboard.data.WeatherRepo
import com.desk.dashboard.media.Ambient
import com.desk.dashboard.system.Lock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import com.desk.dashboard.ui.DashActions
import com.desk.dashboard.ui.Dashboard
import com.desk.dashboard.ui.SettingsScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ambient.init(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        // Weather: show the cache at once, refresh on every start (if older than 5 min), then every 30 min.
        val weather = WeatherRepo.get(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                weather.loadCache()
                weather.refresh(maxAgeMs = 5 * 60 * 1000L)
                while (true) {
                    delay(WeatherRepo.REFRESH_MS)
                    weather.refresh()
                }
            }
        }

        setContent { Root() }
    }

    private enum class Screen { Dashboard, Settings, Apps }

    /** Bumped when Home is pressed while we're the home app: go back to the dashboard. */
    private val homePresses = mutableIntStateOf(0)
    private val isHome = mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses.intValue++
    }

    @Composable
    private fun Root() {
        val activity = this
        val cfg by remember { DisplaySettings.config(activity) }.collectAsStateWithLifecycle(null)
        var screen by rememberSaveable { mutableStateOf(Screen.Dashboard) }
        LaunchedEffect(homePresses.intValue) { screen = Screen.Dashboard }
        // As the home app, Back on the dashboard has nowhere to go.
        BackHandler(enabled = isHome.value && screen == Screen.Dashboard) {}

        // Keep awake: only while plugged in (charging or full) and the setting is on.
        val battery by rememberBattery()
        val keepAwake = cfg?.keepAwakeCharging == true && battery.let { it != null && (it.charging || it.full) }
        LaunchedEffect(keepAwake) {
            if (keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // Night mode: dim the window for the whole night; the dashboard gives way to a muted clock.
        val now by rememberMinute()
        val night = cfg?.isNight(now.toLocalTime()) == true
        LaunchedEffect(night, cfg?.nightBrightness) {
            setWindowBrightness(activity, if (night) cfg?.nightBrightness else null)
        }
        // A tap on the night clock shows the dashboard for 30 s; touching it restarts the 30 s.
        var peeking by remember { mutableStateOf(false) }
        var touches by remember { mutableIntStateOf(0) }
        LaunchedEffect(night) { if (!night) peeking = false }
        LaunchedEffect(peeking, touches) {
            if (peeking) { delay(30_000); peeking = false }
        }

        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        if (peeking) touches++
                    }
                }
            }
        ) {
            when (screen) {
                Screen.Settings -> SettingsScreen(onDone = { screen = Screen.Dashboard })
                Screen.Apps -> AppsScreen(onDone = { screen = Screen.Dashboard })
                Screen.Dashboard ->
                    if (night && !peeking) {
                        NightScreen(onTap = { peeking = true })
                    } else {
                        Dashboard(
                            DashActions(
                                openSettings = { screen = Screen.Settings },
                                lock = { Lock.lockOrAsk(activity) },
                                openApps = { screen = Screen.Apps },
                            )
                        )
                        cfg?.let { BatteryPrompt(it) }
                    }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isHome.value = Device.isHomeApp(this)
        // Past entries are already hidden by the queries; this just drops them from storage.
        lifecycleScope.launch { DashDb.get(this@MainActivity).dates().purgeBefore(LocalDate.now()) }
    }

    override fun onStop() {
        super.onStop()
        // Spec: ambient sound stops when the app leaves the foreground (fades out, resets to Off).
        Ambient.stop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Bars come back after dialogs/permission screens; hide them again when we regain focus.
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

}
