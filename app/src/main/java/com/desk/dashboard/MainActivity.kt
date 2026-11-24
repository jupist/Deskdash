package com.desk.dashboard

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.desk.dashboard.board.DashActions
import com.desk.dashboard.board.LocalDashActions
import com.desk.dashboard.board.LocalOverlay
import com.desk.dashboard.data.DashDb
import com.desk.dashboard.data.DisplaySettings
import com.desk.dashboard.data.WeatherRepo
import com.desk.dashboard.media.Ambient
import com.desk.dashboard.system.Device
import com.desk.dashboard.system.Lock
import com.desk.dashboard.system.rememberBattery
import com.desk.dashboard.system.rememberMinute
import com.desk.dashboard.system.rememberPixelShift
import com.desk.dashboard.system.pixelGrid
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp
import com.desk.dashboard.ui.AppsScreen
import com.desk.dashboard.ui.BatteryPrompt
import com.desk.dashboard.ui.Dash
import com.desk.dashboard.ui.DashboardScreen
import com.desk.dashboard.ui.NightScreen
import com.desk.dashboard.ui.OnboardingScreen
import com.desk.dashboard.ui.SettingsScreen
import com.desk.dashboard.ui.setWindowBrightness
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    /** Where the app is: the dashboard, settings (optionally at a section), or the app list. */
    private sealed interface Screen {
        data object Dashboard : Screen
        data class Settings(val section: String?) : Screen
        data object Apps : Screen
    }

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
        val cfg by remember { DisplaySettings.config(activity) }.collectAsStateWithLifecycle(DisplaySettings.cached)
        var screen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
        var editing by rememberSaveable { mutableStateOf(false) }
        var overlay by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
        LaunchedEffect(homePresses.intValue) { screen = Screen.Dashboard; editing = false; overlay = null }

        // Back: close a dialog, then leave edit mode; as the home app, the dashboard itself stays.
        BackHandler(enabled = overlay != null) { overlay = null }
        BackHandler(enabled = overlay == null && editing && screen == Screen.Dashboard) { editing = false }
        BackHandler(enabled = overlay == null && !editing && isHome.value && screen == Screen.Dashboard) {}

        // Orientation: follow the setting (landscape until changed, setup included).
        LaunchedEffect(cfg?.orientation) {
            requestedOrientation = when (cfg?.orientation) {
                "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                "auto" -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }

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
        LaunchedEffect(peeking, touches, editing) {
            if (peeking && !editing) { delay(30_000); peeking = false }
        }

        val actions = remember {
            DashActions(
                openSettings = { section -> overlay = null; editing = false; screen = Screen.Settings(section) },
                lock = { Lock.lockOrAsk(activity) },
                openApps = { screen = Screen.Apps },
                editLayout = { overlay = null; screen = Screen.Dashboard; editing = true },
            )
        }

        CompositionLocalProvider(
            LocalDashActions provides actions,
            LocalOverlay provides { overlay = it },
        ) {
            Box(
                Modifier.fillMaxSize().background(Dash.frame).pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            if (peeking) touches++
                        }
                    }
                }
            ) {
                val c = cfg
                val remoteTarget by com.desk.dashboard.remote.RemoteMode.target.collectAsStateWithLifecycle()
                val rt = remoteTarget
                if (rt != null) {
                    // This phone is a tablet's remote control: its screens show the tablet instead.
                    com.desk.dashboard.ui.RemoteShell(rt, onExit = { com.desk.dashboard.remote.RemoteMode.stop(activity) })
                } else if (c != null && !c.onboarded) {
                    OnboardingScreen(onDone = { lifecycleScope.launch { DisplaySettings.setOnboarded(activity) } })
                } else {
                    AnimatedContent(
                        screen, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "screen",
                    ) { s ->
                        when (s) {
                            is Screen.Settings -> SettingsScreen(s.section, onDone = { screen = Screen.Dashboard })
                            Screen.Apps -> AppsScreen(onDone = { screen = Screen.Dashboard })
                            Screen.Dashboard ->
                                if (night && !peeking && !editing) {
                                    // The night clock is the most static thing shown; it roams wider.
                                    val shift by rememberPixelShift(c?.pixelShift == true, radius = 14.dp, periodMs = 60_000)
                                    Box(Modifier.pixelGrid(c?.pixelGrid == true).offset(shift.x, shift.y)) { NightScreen(c, onTap = { peeking = true }) }
                                } else if (c != null) {
                                    val shift by rememberPixelShift(c.pixelShift && !editing)
                                    Box(Modifier.pixelGrid(c.pixelGrid && !editing).offset(shift.x, shift.y)) { DashboardScreen(c, editing, onEditing = { editing = it }) }
                                    BatteryPrompt(c)
                                }
                        }
                    }
                }
                overlay?.invoke()
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
        // Ambient sound stops when the app leaves the foreground (fades out, resets to Off).
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
