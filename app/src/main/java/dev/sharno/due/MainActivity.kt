package dev.sharno.due

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.sharno.due.ui.DueApp
import dev.sharno.due.ui.theme.DueTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: TodoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        holdFirstFrameUntilThemeLoads()
        setContent {
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            DueTheme(settings = theme ?: ThemeSettings()) {
                DueApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            TaskScheduler.synchronize(this@MainActivity)
        }
    }

    /**
     * Theme preferences are read asynchronously from DataStore, so drawing immediately would show
     * the default colours for a frame and then jump. Holding the first frame is the same trick
     * `installSplashScreen().setKeepOnScreenCondition { }` uses, without the extra dependency.
     * The deadline stops a pathological read from hanging launch.
     */
    private fun holdFirstFrameUntilThemeLoads() {
        val content = findViewById<View>(android.R.id.content)
        val deadline = SystemClock.uptimeMillis() + MAX_THEME_WAIT_MILLIS
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val ready = viewModel.theme.value != null || SystemClock.uptimeMillis() > deadline
                    if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                    return ready
                }
            },
        )
    }

    private companion object {
        const val MAX_THEME_WAIT_MILLIS = 700L
    }
}
