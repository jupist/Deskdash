package com.desk.dashboard

import android.service.dreams.DreamService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.desk.dashboard.board.DashActions
import com.desk.dashboard.board.LocalDashActions
import com.desk.dashboard.board.LocalOverlay
import com.desk.dashboard.data.DisplaySettings
import com.desk.dashboard.data.WeatherRepo
import com.desk.dashboard.system.pixelGrid
import com.desk.dashboard.system.rememberMinute
import com.desk.dashboard.system.rememberPixelShift
import com.desk.dashboard.ui.Dash
import com.desk.dashboard.ui.DashboardScreen
import com.desk.dashboard.ui.NightScreen

/**
 * Deskdash as Android's screensaver ("Daydream"): the dashboard, view-only, while the device
 * charges or docks. The first touch ends it, as with any screensaver. Follows night mode and
 * burn-in protection like the app does.
 */
class DeskDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@DeskDream)
            setViewTreeSavedStateRegistryOwner(this@DeskDream)
            // Not "Content()": inside apply {} that would resolve to ComposeView.Content() and recurse forever.
            setContent { DreamContent() }
        }
        setContentView(view)
        registry.currentState = Lifecycle.State.STARTED
    }

    @androidx.compose.runtime.Composable
    private fun DreamContent() {
        val context = this
        val cfg by remember { DisplaySettings.config(context) }.collectAsStateWithLifecycle(DisplaySettings.cached)
        val now by rememberMinute()
        val c = cfg ?: return
        val night = c.isNight(now.toLocalTime())
        // Tiles' buttons are inert here (the dream isn't interactive), so actions do nothing.
        val actions = remember { DashActions({}, {}, {}, {}) }
        CompositionLocalProvider(
            LocalDashActions provides actions,
            LocalOverlay provides {},
            // Tiles register for activity results (permission prompts, pickers); a screensaver has no
            // activity to launch them from, so they get a registry that launches nothing.
            androidx.activity.compose.LocalActivityResultRegistryOwner provides inertResults,
        ) {
            Box(Modifier.fillMaxSize().background(Dash.frame)) {
                val shift by rememberPixelShift(c.pixelShift, radius = if (night) 14.dp else 3.dp, periodMs = if (night) 60_000 else 120_000)
                Box(Modifier.pixelGrid(c.pixelGrid).offset(shift.x, shift.y)) {
                    // No top bar: its buttons can't be used in a screensaver.
                    if (night) NightScreen(c, onTap = {}) else DashboardScreen(c.copy(topBar = false), editing = false, onEditing = {}, gridAsWithBar = true)
                }
            }
        }
    }

    private val inertResults = object : androidx.activity.result.ActivityResultRegistryOwner {
        override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: androidx.activity.result.contract.ActivityResultContract<I, O>,
                input: I,
                options: androidx.core.app.ActivityOptionsCompat?,
            ) {}
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        registry.currentState = Lifecycle.State.RESUMED
        // Show fresh weather if the cache is old (the app's own refresh loop isn't running).
        lifecycleScopeLaunch { WeatherRepo.get(this).run { loadCache(); refresh(maxAgeMs = 15 * 60_000L) } }
    }

    override fun onDreamingStopped() {
        registry.currentState = Lifecycle.State.STARTED
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        registry.currentState = Lifecycle.State.DESTROYED
        super.onDetachedFromWindow()
    }

    private fun lifecycleScopeLaunch(block: suspend () -> Unit) {
        (application as DeskApp).scope.launch { runCatching { block() } }
    }
}
