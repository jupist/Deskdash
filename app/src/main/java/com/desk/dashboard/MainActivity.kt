package com.desk.dashboard

import android.os.Bundle
import android.widget.Toast
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

        setContent {
            var showSettings by rememberSaveable { mutableStateOf(false) }
            if (showSettings) {
                SettingsScreen(onDone = { showSettings = false })
            } else {
                Dashboard(
                    DashActions(
                        openSettings = { showSettings = true },
                        lock = { todo("Lock arrives in milestone 7") },
                        openApps = {},
                    )
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Past entries are already hidden by the queries; this just drops them from storage.
        lifecycleScope.launch { DashDb.get(this@MainActivity).dates().purgeBefore(LocalDate.now()) }
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

    private fun todo(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
