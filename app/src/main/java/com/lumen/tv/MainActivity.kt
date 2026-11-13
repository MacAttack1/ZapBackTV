package com.lumen.tv

import android.app.PictureInPictureParams
import android.app.UiModeManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.tv.ui.AppViewModel
import com.lumen.tv.ui.LumenRoot
import com.lumen.tv.ui.Screen
import com.lumen.tv.ui.theme.LumenTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    val isTv: Boolean by lazy {
        val ui = getSystemService(UI_MODE_SERVICE) as UiModeManager
        ui.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val settings by vm.store.settings.collectAsStateWithLifecycle()
            LumenTheme(accentIndex = settings.accent, isTv = isTv) {
                LumenRoot(vm = vm, onExit = { finish() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from the "install unknown apps" screen: let a waiting update continue.
        vm.updater.recheckPermission()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** "Open with Lumen TV" on an .m3u file or link pre-fills the add-playlist screen. */
    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW) return
        if (data.scheme == "content") {
            runCatching { contentResolver.takePersistableUriPermission(data, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        vm.navigate(Screen.EditSource(null, prefillUrl = data.toString()))
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isTv && vm.current is Screen.Player && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            runCatching {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        vm.inPip = isInPictureInPictureMode
    }
}
