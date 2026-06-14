package io.github.mayusi.isitcompatible.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mayusi.isitcompatible.BuildConfig
import io.github.mayusi.isitcompatible.data.UserPreferences
import io.github.mayusi.isitcompatible.hardware.HardwareFingerprinter
import io.github.mayusi.isitcompatible.ui.appupdate.AppUpdateViewModel
import io.github.mayusi.isitcompatible.ui.common.AppUpdateBanner
import io.github.mayusi.isitcompatible.ui.common.AppUpdateDialog
import io.github.mayusi.isitcompatible.ui.submit.SubmitLinks
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val prefs: UserPreferences,
    private val fingerprinter: HardwareFingerprinter,
) : ViewModel() {

    fun setRom(uri: Uri?) = viewModelScope.launch { prefs.setRomFolderUri(uri?.toString()) }
    fun setPc(uri: Uri?) = viewModelScope.launch { prefs.setPcFolderUri(uri?.toString()) }
    fun setStaging(uri: Uri?) = viewModelScope.launch { prefs.setStagingFolderUri(uri?.toString()) }
    fun refingerprint() = viewModelScope.launch {
        val fp = fingerprinter.fingerprint()
        prefs.setFingerprint(fp)
    }
}

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    vm: SettingsViewModel = hiltViewModel(),
    updateVm: AppUpdateViewModel = hiltViewModel(),
) {
    val snapshot by vm.prefs.data.collectAsState(initial = null)
    val ctx = LocalContext.current
    val updateState by updateVm.state.collectAsState()
    var showUpdateDialog by remember { mutableStateOf(false) }

    val romLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) ctx.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        vm.setRom(uri)
    }
    val pcLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) ctx.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        vm.setPc(uri)
    }
    val stagingLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) ctx.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        vm.setStaging(uri)
    }

    // Show the update dialog if requested.
    if (showUpdateDialog && updateState.pendingUpdate != null) {
        AppUpdateDialog(
            update = updateState.pendingUpdate!!,
            installState = updateState.installState,
            onInstall = updateVm::installUpdate,
            onDismiss = { showUpdateDialog = false },
        )
    }

    Column(
        Modifier.fillMaxSize().padding(contentPadding).padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        // Update banner — shown when an update is pending.
        updateState.pendingUpdate?.let { pending ->
            AppUpdateBanner(
                update = pending,
                onSeeWhatsNew = { showUpdateDialog = true },
                onDismiss = updateVm::dismiss,
            )
        }

        snapshot?.fingerprint?.let { fp ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Device", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(fp.displayLine, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(2.dp))
                    // Many OEMs put the brand name into both MANUFACTURER and MODEL
                    // (e.g. Thor → "AYN" + "AYN Thor" = "AYN AYN Thor"). Strip the
                    // duplicate prefix so the line reads naturally.
                    val brand = fp.manufacturer
                    val model = if (fp.model.startsWith(brand, ignoreCase = true) && brand.isNotBlank()) {
                        fp.model.removePrefix(brand).removePrefix(" ").ifBlank { fp.model }
                    } else {
                        fp.model
                    }
                    Text("$brand $model · API ${fp.androidApi}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = vm::refingerprint) { Text("Re-detect hardware") }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Folders", style = MaterialTheme.typography.titleMedium)
                FolderRow("ROMs",     snapshot?.romFolderUri)     { romLauncher.launch(null) }
                FolderRow("Windows games", snapshot?.pcFolderUri) { pcLauncher.launch(null) }
                FolderRow("Staging",  snapshot?.stagingFolderUri) { stagingLauncher.launch(null) }
                Text(
                    "The staging folder is where we put recommended drivers and config files for you to import.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Submit a report", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (SubmitLinks.COMMUNITY_DB_ENABLED)
                        "Console games → EmuReady. Windows-translator games → our GitHub."
                    else
                        "Console games → EmuReady.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { SubmitLinks.openEmuReady(ctx) }) {
                    Text("Open EmuReady (console games)")
                }
                // The GitHub Issues button targets the (non-existent) community DB repo,
                // so it's hidden until that repo exists (mirrors SubmitLinks gating).
                if (SubmitLinks.COMMUNITY_DB_ENABLED) {
                    TextButton(onClick = { SubmitLinks.openGithubIssues(ctx) }) {
                        Text("Open GitHub Issues (Windows / GameNative)")
                    }
                }
            }
        }

        AboutCard(
            autoCheckEnabled = snapshot?.updateAutoCheckEnabled ?: true,
            checkInProgress = updateState.checkInProgress,
            lastCheckMessage = updateState.lastCheckMessage,
            onCheckNow = updateVm::checkNow,
            onAutoCheckToggle = updateVm::setAutoCheck,
            onShowDialog = { showUpdateDialog = true },
            hasPendingUpdate = updateState.pendingUpdate != null,
        )
    }
}

@Composable
private fun FolderRow(label: String, uri: String?, onPick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            uri ?: "(not set)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onPick) { Text(if (uri == null) "Pick" else "Change") }
        HorizontalDivider()
    }
}

@Composable
private fun AboutCard(
    autoCheckEnabled: Boolean,
    checkInProgress: Boolean,
    lastCheckMessage: String?,
    onCheckNow: () -> Unit,
    onAutoCheckToggle: (Boolean) -> Unit,
    onShowDialog: () -> Unit,
    hasPendingUpdate: Boolean,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("About", style = MaterialTheme.typography.titleMedium)
            Text("Is It Compatible? v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Pick the right emulator + driver + preset for any game on YOUR device. Standalone, MIT-licensed, no accounts, no telemetry.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))

            // App update controls
            HorizontalDivider()
            Text("App updates", style = MaterialTheme.typography.titleSmall)

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Check automatically", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = autoCheckEnabled,
                    onCheckedChange = onAutoCheckToggle,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onCheckNow,
                    enabled = !checkInProgress,
                ) {
                    Text(if (checkInProgress) "Checking…" else "Check for updates")
                }
                if (hasPendingUpdate) {
                    TextButton(onClick = onShowDialog) {
                        Text("See what's new")
                    }
                }
            }

            lastCheckMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(6.dp))
            HorizontalDivider()
            Spacer(Modifier.height(6.dp))

            Text("Credits", style = MaterialTheme.typography.titleSmall)
            Text(
                "• EmuReady (emuready.com) — console compatibility data.\n" +
                "• K11MCH1/AdrenoToolsDrivers — Adreno/Turnip GPU drivers.\n" +
                "• Obtainium Emulation Pack (RJNY) — emulator metadata reference.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Developed and tested on AYN Odin 3 and AYN Thor. Works on any Android gaming handheld — coverage grows as more devices report results.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
