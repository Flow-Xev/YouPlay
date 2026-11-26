package app.youplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import app.youplay.data.local.AppSettings
import app.youplay.data.local.SettingsDataStore
import app.youplay.ui.navigation.YouPlayNavHost
import app.youplay.ui.player.PlayerController
import app.youplay.ui.theme.AccentTheme
import app.youplay.ui.theme.YouPlayTheme
import app.youplay.ui.theme.setAccent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsStore: SettingsDataStore
    @Inject lateinit var player: PlayerController
    @Volatile private var backgroundPlay = true
    @Volatile private var autoPip = true
    private val pendingVideo = androidx.compose.runtime.mutableStateOf<String?>(null)

    private fun handleIntent(i: android.content.Intent?) {
        if (i?.action == app.youplay.data.notify.NewVideoNotifier.ACTION_OPEN_VIDEO) {
            pendingVideo.value = i.getStringExtra(app.youplay.data.notify.NewVideoNotifier.EXTRA_VIDEO_ID)
            i.action = null
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = dark, navigationBarStyle = dark)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
                .launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        // Read the saved theme once up front so the first frame is already themed.
        val initial = runBlocking { settingsStore.settings.first() }
        setAccent(AccentTheme.of(initial.accent))
        backgroundPlay = initial.backgroundPlay; autoPip = initial.autoPip; player.sponsorBlock = initial.sponsorBlock
        player.autoMax = initial.autoMax; player.mobileCap = initial.mobileCap; player.preloadEnabled = initial.preloadNext
        lifecycleScope.launch { settingsStore.settings.collect { backgroundPlay = it.backgroundPlay; autoPip = it.autoPip; player.sponsorBlock = it.sponsorBlock; player.autoMax = it.autoMax; player.mobileCap = it.mobileCap; player.preloadEnabled = it.preloadNext } }
        handleIntent(intent)
        setContent {
            val s by settingsStore.settings.collectAsState(initial = initial)
            YouPlayTheme(accent = s.accent, amoled = s.amoled) {
                Surface(modifier = Modifier.fillMaxSize()) { YouPlayNavHost(openVideoId = pendingVideo.value, onOpenVideoHandled = { pendingVideo.value = null }) }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (autoPip && player.watchVisible && player.isPlaying.value && android.os.Build.VERSION.SDK_INT >= 26) {
            runCatching {
                enterPictureInPictureMode(
                    android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(16, 9)).build(),
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        player.setVideoEnabled(true)
    }

    override fun onStop() {
        super.onStop()
        // Screen off / app in background: keep the audio but stop downloading video.
        if (backgroundPlay && !isChangingConfigurations && !isInPictureInPictureMode) player.setVideoEnabled(false)
        if (!backgroundPlay && !isChangingConfigurations && !isInPictureInPictureMode) player.pause()
    }
}
