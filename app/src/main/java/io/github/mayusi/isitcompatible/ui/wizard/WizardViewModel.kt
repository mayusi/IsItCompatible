package io.github.mayusi.isitcompatible.ui.wizard

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mayusi.isitcompatible.data.UserPreferences
import io.github.mayusi.isitcompatible.hardware.DeviceFingerprint
import io.github.mayusi.isitcompatible.hardware.HardwareFingerprinter
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class WizardViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val prefs: UserPreferences,
    private val fingerprinter: HardwareFingerprinter,
) : ViewModel() {

    private val _state = MutableStateFlow(WizardState())
    val state: StateFlow<WizardState> = _state.asStateFlow()

    init {
        runFingerprint()
    }

    fun next() {
        _state.update { it.copy(step = it.step.next()) }
    }

    fun back() {
        _state.update { it.copy(step = it.step.previous()) }
    }

    fun onRomFolderPicked(uri: Uri?) {
        uri?.let { persistGrant(it) }
        _state.update { it.copy(romFolderUri = uri?.toString()) }
    }

    fun onPcFolderPicked(uri: Uri?) {
        uri?.let { persistGrant(it) }
        _state.update { it.copy(pcFolderUri = uri?.toString()) }
    }

    fun onStagingFolderPicked(uri: Uri?) {
        uri?.let { persistGrant(it) }
        _state.update { it.copy(stagingFolderUri = uri?.toString()) }
    }

    fun runFingerprint() {
        viewModelScope.launch {
            _state.update { it.copy(fingerprintLoading = true) }
            val fp = withContext(Dispatchers.IO) { fingerprinter.fingerprint() }
            _state.update { it.copy(fingerprint = fp, fingerprintLoading = false) }
        }
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            prefs.setRomFolderUri(s.romFolderUri)
            prefs.setPcFolderUri(s.pcFolderUri)
            prefs.setStagingFolderUri(s.stagingFolderUri)
            if (s.fingerprint != null) {
                prefs.setFingerprint(s.fingerprint)
            } else {
                // BUGFIX 6c: log when fingerprint is null so silent degradation
                // (recommender has no device data) is at least diagnosable in logcat.
                Log.w(TAG, "finish() called with null fingerprint — device hardware not fingerprinted yet")
            }
            prefs.setWizardComplete(true)
            onDone()
        }
    }

    private fun persistGrant(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { appContext.contentResolver.takePersistableUriPermission(uri, flags) }
    }

    private companion object {
        private const val TAG = "WizardViewModel"
    }
}

data class WizardState(
    val step: WizardStep = WizardStep.WELCOME,
    val romFolderUri: String? = null,
    val pcFolderUri: String? = null,
    val stagingFolderUri: String? = null,
    val fingerprint: DeviceFingerprint? = null,
    val fingerprintLoading: Boolean = false,
)

enum class WizardStep {
    /**
     * Welcome → Permissions → Folders → Fingerprint.
     *
     * v0.10: added a Permissions step so everything the app needs later
     * (install-unknown-apps for "Get it", all-files for Auto-Detect,
     * notifications for download/sync progress) is requested ONCE up front,
     * instead of surprising the user mid-download. Every permission is opt-in
     * with a Skip — the app's core "search ANY game" works without any of them.
     *
     * v0.11: added a Folders step so users can set up ROM, PC, and staging
     * folders during onboarding instead of in Settings. Staging folder is
     * required; ROM and PC are optional.
     */
    WELCOME, PERMISSIONS, FOLDERS, FINGERPRINT;

    fun next(): WizardStep = when (this) {
        WELCOME -> PERMISSIONS
        PERMISSIONS -> FOLDERS
        FOLDERS -> FINGERPRINT
        FINGERPRINT -> FINGERPRINT
    }

    fun previous(): WizardStep = when (this) {
        WELCOME -> WELCOME
        PERMISSIONS -> WELCOME
        FOLDERS -> PERMISSIONS
        FINGERPRINT -> FOLDERS
    }
}
