package dev.dhun.android

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.dhun.android.playback.AndroidDhunPlayer
import dev.dhun.android.playback.DhunPlaybackService
import dev.dhun.android.ui.HarnessScreen
import dev.dhun.android.ui.ConnectingScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * PHASE 03 TEST HARNESS — throwaway verification screen (search -> play ->
 * transport controls). Explicitly scheduled for replacement by the real UI
 * phases (MASTER_PROMPT.md Phase 06+). Do not polish this.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: dev.dhun.android.ui.HarnessViewModel by viewModel()
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: AndroidDhunPlayer? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        connectToPlaybackService()
        setContent {
            val p = player
            if (p != null) {
                HarnessScreen(player = p, viewModel = viewModel)
            } else {
                ConnectingScreen()
            }
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        activityScope.cancel()
        super.onDestroy()
    }

    private fun connectToPlaybackService() {
        val token = SessionToken(this, ComponentName(this, DhunPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            try {
                val controller = future.get()
                player = AndroidDhunPlayer(controller, activityScope)
            } catch (t: Throwable) {
                android.util.Log.e("DHUN", "controller connect failed", t)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
