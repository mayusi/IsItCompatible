package io.github.mayusi.isitcompatible.ui.wizard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mayusi.isitcompatible.R
import io.github.mayusi.isitcompatible.hardware.DeviceFingerprint
import io.github.mayusi.isitcompatible.ui.common.SectionCard
import io.github.mayusi.isitcompatible.ui.theme.AppColors
import io.github.mayusi.isitcompatible.ui.theme.AppShapes
import io.github.mayusi.isitcompatible.ui.theme.Spacing

/* ---------- Step 1: Welcome ---------- */

@Composable
fun WelcomeStep(onLetsGo: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xl, vertical = Spacing.screenV)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.xxl + Spacing.lg))

        // Hero app name — big Chakra Petch display style
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Normal,
        )

        Spacer(Modifier.height(Spacing.xl + Spacing.sm))

        Text(
            stringResource(R.string.wizard_welcome_blurb),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(Spacing.xl))

        // Feature bullets — styled SectionCard
        SectionCard(
            title = "What you get",
            accentColor = MaterialTheme.colorScheme.primary,
        ) {
            val features = listOf(
                "Best emulator + preset for your exact chip",
                "Per-game setup guide + BIOS checklist",
                "Community reports from people with the same hardware",
                "One-tap config apply for Windows games via GameNative",
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                features.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(Spacing.lg),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            item,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.md))

        // Credit card — token-styled with outline border
        Card(
            Modifier.fillMaxWidth(),
            shape = AppShapes.card,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            ),
        ) {
            Text(
                stringResource(R.string.wizard_welcome_credit),
                modifier = Modifier.padding(Spacing.cardPadding),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Spacing.xxl))

        // Full-width CTA button
        Button(
            onClick = onLetsGo,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = AppShapes.button,
        ) {
            Text(
                stringResource(R.string.wizard_lets_go),
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(Spacing.xl))
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
            .padding(horizontal = Spacing.xl, vertical = Spacing.screenV)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(Spacing.lg))
        Text(
            stringResource(R.string.wizard_libraries_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xl))

        FolderPickRow(
            label = stringResource(R.string.wizard_pick_rom_folder),
            uri = romFolderUri,
            onPick = { romLauncher.launch(null) },
        )
        Spacer(Modifier.height(Spacing.md))
        FolderPickRow(
            label = stringResource(R.string.wizard_pick_pc_folder),
            uri = pcFolderUri,
            onPick = { pcLauncher.launch(null) },
        )
        Spacer(Modifier.height(Spacing.md))
        FolderPickRow(
            label = stringResource(R.string.wizard_pick_staging_folder),
            uri = stagingFolderUri,
            onPick = { stagingLauncher.launch(null) },
            subtitle = stringResource(R.string.wizard_staging_blurb),
        )

        Spacer(Modifier.height(Spacing.xxl + Spacing.sm))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = AppShapes.button,
            ) {
                Text("Back")
            }
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .weight(2f)
                    .height(56.dp),
                enabled = stagingFolderUri != null,
                shape = AppShapes.button,
            ) {
                Text(
                    stringResource(R.string.wizard_continue),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (stagingFolderUri == null) {
            Spacer(Modifier.height(Spacing.sm))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(
                    onClick = onContinue,
                    shape = AppShapes.button,
                ) {
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
    val picked = uri != null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.card)
            .clickable(onClick = onPick),
        shape = AppShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (picked)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
        ),
    ) {
        Column(Modifier.padding(Spacing.cardPadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(AppShapes.badge)
                        .background(
                            if (picked)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (picked) Icons.Outlined.CheckCircle else Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = if (picked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        uri ?: stringResource(R.string.not_picked),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (subtitle != null) {
                Spacer(Modifier.height(Spacing.sm))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/* ---------- Step 3: Permissions ---------- */

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
            .padding(horizontal = Spacing.xl, vertical = Spacing.screenV)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(Spacing.lg))
        Text(
            "Permissions",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Grant these now so downloading emulators and scanning your games works smoothly later. All optional — searching games works without them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xl))

        PermissionRow(
            title = "Install apps",
            blurb = "Lets the \"Get it\" button install emulators for you. Without this, Android interrupts each download to ask.",
            granted = installGranted,
            onGrant = {
                io.github.mayusi.isitcompatible.getit.InstallPermission.settingsIntent(context)
                    ?.let { context.startActivity(it) }
            },
        )
        Spacer(Modifier.height(Spacing.md))
        PermissionRow(
            title = "Find my games & BIOS",
            blurb = "All-files access so the Auto-Detect tab can see the games, emulators and BIOS you already have.",
            granted = filesGranted,
            onGrant = {
                io.github.mayusi.isitcompatible.autodetect.AllFilesAccess.settingsIntent(context)
                    ?.let { context.startActivity(it) }
            },
        )
        Spacer(Modifier.height(Spacing.md))
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

        Spacer(Modifier.height(Spacing.xxl + Spacing.sm))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = AppShapes.button,
            ) {
                Text("Back")
            }
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .weight(2f)
                    .height(56.dp),
                shape = AppShapes.button,
            ) {
                // Always enabled — everything here is optional.
                Text(
                    stringResource(R.string.wizard_continue),
                    fontWeight = FontWeight.Bold,
                )
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
        shape = AppShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (granted)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            Modifier.padding(Spacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(AppShapes.badge)
                    .background(
                        if (granted)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (granted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    blurb,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            if (granted) {
                Text(
                    "Granted",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                TextButton(
                    onClick = onGrant,
                    shape = AppShapes.button,
                ) {
                    Text("Grant")
                }
            }
        }
    }
}

/* ---------- Step 4: Fingerprint confirm ---------- */

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
            .padding(horizontal = Spacing.xl, vertical = Spacing.screenV)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(Spacing.lg))
        Text(
            stringResource(R.string.wizard_fingerprint_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            stringResource(R.string.wizard_fingerprint_blurb),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xl))

        Card(
            Modifier.fillMaxWidth(),
            shape = AppShapes.card,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            ),
        ) {
            Column(Modifier.padding(Spacing.xl)) {
                when {
                    loading -> Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }

                    fingerprint == null -> {
                        Text(
                            "Couldn't read device info.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(Spacing.md))
                        TextButton(
                            onClick = onRetry,
                            shape = AppShapes.button,
                        ) {
                            Text("Retry")
                        }
                    }

                    else -> FingerprintReadout(fingerprint)
                }
            }
        }

        // "What you can do" moment — only shown when we have a fingerprint.
        if (fingerprint != null && !loading) {
            Spacer(Modifier.height(Spacing.lg))
            DeviceValueCard(fingerprint)
        }

        Spacer(Modifier.height(Spacing.xxl + Spacing.sm))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = AppShapes.button,
            ) {
                Text("Back")
            }
            Button(
                onClick = onDone,
                modifier = Modifier
                    .weight(2f)
                    .height(56.dp),
                enabled = fingerprint != null,
                shape = AppShapes.button,
            ) {
                Text(
                    stringResource(R.string.wizard_done),
                    fontWeight = FontWeight.Bold,
                )
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
    SectionCard(
        title = "Your ${fp.manufacturer} ${fp.model} is ready",
        accentColor = AppColors.success,
    ) {
        Text(
            deviceLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        val bullets = listOf(
            "Browse any game and see exactly what to expect on your chip",
            "Get the right emulator + preset — matched to your hardware",
            "See reports from people with the same SoC, not just anyone",
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            bullets.forEach { bullet ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(Spacing.lg),
                        tint = AppColors.success,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        bullet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun FingerprintReadout(fp: DeviceFingerprint) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        KeyValue(stringResource(R.string.fp_soc), "${fp.socFamily} (${fp.socModel})")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        KeyValue(stringResource(R.string.fp_gpu), fp.gpuModel)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        KeyValue(stringResource(R.string.fp_ram), "${fp.totalRamMb / 1024} GB")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        KeyValue(stringResource(R.string.fp_android), "${fp.androidRelease} (API ${fp.androidApi})")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        KeyValue(stringResource(R.string.fp_driver), fp.gpuDriver)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        KeyValue("Device", "${fp.manufacturer} ${fp.model}")
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            key,
            modifier = Modifier.width(120.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
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
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WizardStep.entries.forEach { step ->
            val completed = step.ordinal <= current.ordinal
            Icon(
                if (completed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (completed) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
