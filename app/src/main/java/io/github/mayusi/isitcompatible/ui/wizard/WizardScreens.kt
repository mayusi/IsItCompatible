package io.github.mayusi.isitcompatible.ui.wizard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mayusi.isitcompatible.R
import io.github.mayusi.isitcompatible.hardware.DeviceFingerprint

/* ---------- Step 1: Welcome ---------- */

@Composable
fun WelcomeStep(onLetsGo: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))
        Text(
            stringResource(R.string.wizard_welcome_blurb),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "What you get",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(8.dp))
                listOf(
                    "Best emulator + preset for your exact chip",
                    "Per-game setup guide + BIOS checklist",
                    "Community reports from people with the same hardware",
                    "One-tap config apply for Windows games via GameNative",
                ).forEach { item ->
                    Row(
                        Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            item,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
        ) {
            Text(
                stringResource(R.string.wizard_welcome_credit),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onLetsGo,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(stringResource(R.string.wizard_lets_go))
        }
    }
}

/* ---------- Step 2: Library roots ---------- */

@Composable
fun LibrariesStep(
    romFolderUri: String?,
    pcFolderUri: String?,
    stagingFolderUri: String?,
    onRomPicked: (Uri?) -> Unit,
    onPcPicked: (Uri?) -> Unit,
    onStagingPicked: (Uri?) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val romLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(), onRomPicked
    )
    val pcLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(), onPcPicked
    )
    val stagingLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(), onStagingPicked
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.wizard_libraries_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(24.dp))

        FolderPickRow(
            label = stringResource(R.string.wizard_pick_rom_folder),
            uri = romFolderUri,
            onPick = { romLauncher.launch(null) },
        )
        Spacer(Modifier.height(12.dp))
        FolderPickRow(
            label = stringResource(R.string.wizard_pick_pc_folder),
            uri = pcFolderUri,
            onPick = { pcLauncher.launch(null) },
        )
        Spacer(Modifier.height(12.dp))
        FolderPickRow(
            label = stringResource(R.string.wizard_pick_staging_folder),
            uri = stagingFolderUri,
            onPick = { stagingLauncher.launch(null) },
            subtitle = stringResource(R.string.wizard_staging_blurb),
        )

        Spacer(Modifier.height(40.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("Back")
            }
            Button(
                onClick = onContinue,
                modifier = Modifier.weight(2f),
                enabled = stagingFolderUri != null,
            ) {
                Text(stringResource(R.string.wizard_continue))
            }
        }
        if (stagingFolderUri == null) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onContinue) {
                    Text("Skip for now")
                }
            }
        }
    }
}

@Composable
private fun FolderPickRow(
    label: String,
    uri: String?,
    onPick: () -> Unit,
    subtitle: String? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (uri != null) Icons.Outlined.CheckCircle else Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    tint = if (uri != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        uri ?: stringResource(R.string.not_picked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (subtitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/* ---------- Step 2: Permissions ---------- */

@Composable
fun PermissionsStep(
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Re-read granted state every time the screen resumes (user returns from a
    // system settings page). A bump counter forces recomposition on RESUME.
    var refreshTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val installGranted = remember(refreshTick) {
        io.github.mayusi.isitcompatible.getit.InstallPermission.canInstall(context)
    }
    val filesGranted = remember(refreshTick) {
        io.github.mayusi.isitcompatible.autodetect.AllFilesAccess.isGranted(context)
    }

    // Notifications (Android 13+) — runtime dialog.
    var notifGranted by remember {
        mutableStateOf(
            android.os.Build.VERSION.SDK_INT < 33 ||
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notifGranted = granted }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Permissions",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Grant these now so downloading emulators and scanning your games works smoothly later. All optional — searching games works without them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        PermissionRow(
            title = "Install apps",
            blurb = "Lets the \"Get it\" button install emulators for you. Without this, Android interrupts each download to ask.",
            granted = installGranted,
            onGrant = {
                io.github.mayusi.isitcompatible.getit.InstallPermission.settingsIntent(context)
                    ?.let { context.startActivity(it) }
            },
        )
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            title = "Find my games & BIOS",
            blurb = "All-files access so the Auto-Detect tab can see the games, emulators and BIOS you already have.",
            granted = filesGranted,
            onGrant = {
                io.github.mayusi.isitcompatible.autodetect.AllFilesAccess.settingsIntent(context)
                    ?.let { context.startActivity(it) }
            },
        )
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            title = "Notifications",
            blurb = "Show download and database-sync progress.",
            granted = notifGranted,
            onGrant = {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )

        Spacer(Modifier.height(40.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(onClick = onContinue, modifier = Modifier.weight(2f)) {
                // Always enabled — everything here is optional.
                Text(stringResource(R.string.wizard_continue))
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    blurb: String,
    granted: Boolean,
    onGrant: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (granted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    blurb,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (granted) {
                Text(
                    "Granted",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                TextButton(onClick = onGrant) { Text("Grant") }
            }
        }
    }
}

/* ---------- Step 3: Fingerprint confirm ---------- */

@Composable
fun FingerprintStep(
    fingerprint: DeviceFingerprint?,
    loading: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.wizard_fingerprint_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.wizard_fingerprint_blurb),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
        ) {
            Column(Modifier.padding(20.dp)) {
                when {
                    loading -> Box(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    fingerprint == null -> {
                        Text("Couldn't read device info.")
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }

                    else -> FingerprintReadout(fingerprint)
                }
            }
        }

        // "What you can do" moment — only shown when we have a fingerprint.
        // Gives the user an immediate, concrete sense of value before they hit Done.
        if (fingerprint != null && !loading) {
            Spacer(Modifier.height(16.dp))
            DeviceValueCard(fingerprint)
        }

        Spacer(Modifier.height(40.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("Back")
            }
            Button(
                onClick = onDone,
                modifier = Modifier.weight(2f),
                enabled = fingerprint != null,
            ) {
                Text(stringResource(R.string.wizard_done))
            }
        }
    }
}

/**
 * Post-detection value card: shown on the FingerprintStep after we have a
 * fingerprint. Gives the user a concrete "here's what the app does for YOU"
 * moment so landing on Browse after the wizard isn't a blank search page.
 * Stays tight — 3 bullets max, no friction.
 */
@Composable
private fun DeviceValueCard(fp: DeviceFingerprint) {
    val deviceLabel = "${fp.socFamily} · ${fp.gpuModel} · ${fp.totalRamMb / 1024} GB"
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Your ${fp.manufacturer} ${fp.model} is ready",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                deviceLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(10.dp))
            listOf(
                "Browse any game and see exactly what to expect on your chip",
                "Get the right emulator + preset — matched to your hardware",
                "See reports from people with the same SoC, not just anyone",
            ).forEach { bullet ->
                Row(
                    Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        bullet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun FingerprintReadout(fp: DeviceFingerprint) {
    KeyValue(stringResource(R.string.fp_soc), "${fp.socFamily} (${fp.socModel})")
    KeyValue(stringResource(R.string.fp_gpu), fp.gpuModel)
    KeyValue(stringResource(R.string.fp_ram), "${fp.totalRamMb / 1024} GB")
    KeyValue(stringResource(R.string.fp_android), "${fp.androidRelease} (API ${fp.androidApi})")
    KeyValue(stringResource(R.string.fp_driver), fp.gpuDriver)
    KeyValue("Device", "${fp.manufacturer} ${fp.model}")
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            key,
            modifier = Modifier.width(120.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/* ---------- Wizard host ---------- */

@Composable
fun WizardHost(
    state: WizardState,
    onLetsGo: () -> Unit,
    onFingerprintRetry: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onRomPicked: (Uri?) -> Unit,
    onPcPicked: (Uri?) -> Unit,
    onStagingPicked: (Uri?) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        StepIndicator(state.step)
        when (state.step) {
            WizardStep.WELCOME -> WelcomeStep(onLetsGo = onLetsGo)
            WizardStep.PERMISSIONS -> PermissionsStep(
                onBack = onBack,
                onContinue = onLetsGo, // onLetsGo == vm::next → advances to folders
            )
            WizardStep.FOLDERS -> LibrariesStep(
                romFolderUri = state.romFolderUri,
                pcFolderUri = state.pcFolderUri,
                stagingFolderUri = state.stagingFolderUri,
                onRomPicked = onRomPicked,
                onPcPicked = onPcPicked,
                onStagingPicked = onStagingPicked,
                onBack = onBack,
                onContinue = onLetsGo, // onLetsGo == vm::next → advances to fingerprint
            )
            WizardStep.FINGERPRINT -> FingerprintStep(
                fingerprint = state.fingerprint,
                loading = state.fingerprintLoading,
                onRetry = onFingerprintRetry,
                onBack = onBack,
                onDone = onFinish,
            )
        }
    }
}

@Composable
private fun StepIndicator(current: WizardStep) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WizardStep.entries.forEach { step ->
            val active = step.ordinal <= current.ordinal
            Icon(
                if (active) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
