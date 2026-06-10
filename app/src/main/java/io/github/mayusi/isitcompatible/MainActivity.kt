package io.github.mayusi.isitcompatible

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.mayusi.isitcompatible.data.UserPreferences
import io.github.mayusi.isitcompatible.ui.MainScaffold
import io.github.mayusi.isitcompatible.ui.theme.IsItCompatibleTheme
import io.github.mayusi.isitcompatible.ui.wizard.WizardHost
import io.github.mayusi.isitcompatible.ui.wizard.WizardViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            IsItCompatibleTheme {
                Surface(Modifier.fillMaxSize()) {
                    AppRoot(prefs)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(prefs: UserPreferences) {
    // Flow of (wizardComplete) — drives whether we show the wizard or the main app.
    val snapshot by prefs.data.collectAsState(initial = UserPreferences.Snapshot(
        wizardComplete = false,
        romFolderUri = null,
        pcFolderUri = null,
        stagingFolderUri = null,
        fingerprint = null,
    ))

    if (!snapshot.wizardComplete) {
        WizardRoute()
    } else {
        MainScaffold()
    }
}

@Composable
private fun WizardRoute(vm: WizardViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    WizardHost(
        state = state,
        onLetsGo = vm::next,
        onFingerprintRetry = vm::runFingerprint,
        onBack = vm::back,
        onFinish = { vm.finish(onDone = {}) },
        onRomPicked = vm::onRomFolderPicked,
        onPcPicked = vm::onPcFolderPicked,
        onStagingPicked = vm::onStagingFolderPicked,
    )
}
