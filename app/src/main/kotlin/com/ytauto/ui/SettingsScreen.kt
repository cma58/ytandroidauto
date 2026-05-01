package com.ytauto.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ytauto.shizuku.ShizukuManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    
    // Audio States van ViewModel
    val bassBoostStrength by viewModel.bassBoostStrength.collectAsState()
    val loudnessGain by viewModel.loudnessGain.collectAsState()
    val eqBands by viewModel.eqBands.collectAsState()
    val currentPreset by viewModel.currentPreset.collectAsState()
    val presets = viewModel.presets.keys.toList()

    // Shizuku States
    val isShizukuAvailable by viewModel.isShizukuAvailable.collectAsState()
    val hasShizukuPermission by viewModel.hasShizukuPermission.collectAsState()

    val sponsorBlockEnabled by viewModel.isSponsorBlockEnabled.collectAsState()
    val autoSyncEnabled by viewModel.isAutoSyncEnabled.collectAsState()

    val partyUrl by viewModel.partyModeUrl.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadPartyModeUrl() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("God-Mode Control Center") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // --- Sectie 1: Superpowers (SponsorBlock & Sync) ---
            item {
                Text("Superkrachten & Automatisering", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                
                SettingSwitchItem(
                    icon = Icons.Default.SkipNext,
                    title = "SponsorBlock (Auto-Skip)",
                    subtitle = "Sla intro's en gepraat in muziekvideo's automatisch over",
                    checked = sponsorBlockEnabled,
                    onCheckedChange = { viewModel.setSponsorBlock(it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingSwitchItem(
                    icon = Icons.Default.Sync,
                    title = "Smart Auto-Sync",
                    subtitle = "Download 's nachts nieuwe nummers via Wi-Fi",
                    checked = autoSyncEnabled,
                    onCheckedChange = { viewModel.setAutoSync(context, it) }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // --- Sectie 2: Audio Engine Tuning ---
            item {
                Text("Audio Engine Tuning", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(12.dp))
                
                Text("Presets", style = MaterialTheme.typography.bodyMedium)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = currentPreset == preset,
                            onClick = { viewModel.applyPreset(preset) },
                            label = { Text(preset) }
                        )
                    }
                }
                
                AudioSliderItem(label = "Bass Boost", value = bassBoostStrength, range = 0f..1000f, onValueChange = viewModel::setBassBoost)
                AudioSliderItem(label = "Loudness Enhancer", value = loudnessGain, range = 0f..2000f, onValueChange = viewModel::setLoudness)
                
                Spacer(modifier = Modifier.height(8.dp))
                Text("5-Band Equalizer", style = MaterialTheme.typography.bodyMedium)
                eqBands.forEachIndexed { index, level ->
                    AudioSliderItem(label = "Band $index", value = level, range = -1500f..1500f, onValueChange = { viewModel.setEqBand(index, it) })
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // --- Sectie 3: Shizuku & Android Auto Hacks ---
            item {
                Text("Android Auto Systeem Hacks", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(12.dp))
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DeveloperMode, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Shizuku Whitelist Injectie", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Forceer deze app in de officiële Google Play Services database zodat Android Auto hem altijd accepteert.", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (!isShizukuAvailable) {
                            Text("Shizuku is niet gedetecteerd. Start de Shizuku app!", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = viewModel::refreshShizuku) {
                                Icon(Icons.Default.Refresh, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Check Shizuku Status")
                            }
                        } else if (!hasShizukuPermission) {
                            Button(onClick = viewModel::requestShizukuPermission, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                                Icon(Icons.Default.GppGood, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Geef Shizuku Toestemming")
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        try {
                                            viewModel.applyShizukuHacks()
                                            Toast.makeText(context, "Succes! Whitelist geüpdatet.", Toast.LENGTH_LONG).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Hack mislukt: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.FlashOn, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Injecteer App")
                                }
                                
                                Button(
                                    onClick = { viewModel.runCommand("am force-stop com.google.android.projection.gearhead") },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onErrorContainer)
                                ) {
                                    Icon(Icons.Default.RestartAlt, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Restart AA")
                                }
                            }
                        }
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // --- Sectie 4: Party Mode ---
            item {
                Text("Party Mode", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gasten kunnen nummers toevoegen", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Open dit adres op een telefoon in hetzelfde Wi-Fi netwerk:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = partyUrl,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Party Mode URL", partyUrl))
                                Toast.makeText(context, "URL gekopieerd!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Kopieer URL")
                        }
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // --- Sectie 5: App Updates ---
            item {
                val updateState by viewModel.updateState.collectAsState()

                Text(
                    "App Updates",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Automatische updates via GitHub", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        when (val state = updateState) {
                            is MainViewModel.UpdateState.Idle -> {
                                Button(onClick = { viewModel.checkForUpdate() }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Controleer op updates")
                                }
                            }
                            is MainViewModel.UpdateState.Checking -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Controleren...", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            is MainViewModel.UpdateState.UpToDate -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Je hebt de nieuwste versie!", style = MaterialTheme.typography.bodyMedium)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(onClick = { viewModel.checkForUpdate() }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Opnieuw controleren")
                                }
                            }
                            is MainViewModel.UpdateState.Available -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Nieuwe versie beschikbaar: ${state.info.tagName}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { viewModel.downloadAndInstall(context, state.info) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Download & Installeer")
                                }
                            }
                            is MainViewModel.UpdateState.Downloading -> {
                                Text("Downloaden: ${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            is MainViewModel.UpdateState.ReadyToInstall -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Download klaar!", style = MaterialTheme.typography.bodyMedium)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.installApk(context) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.InstallMobile, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Installeer nu")
                                }
                            }
                            is MainViewModel.UpdateState.Error -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(onClick = { viewModel.checkForUpdate() }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Opnieuw proberen")
                                }
                            }
                        }
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // --- Sectie 6: Privacy ---
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.clearAnalytics(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Wis Afspeelgeschiedenis")
                }
            }
        }
    }
}

@Composable
fun SettingSwitchItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun AudioSliderItem(label: String, value: Int, range: ClosedFloatingPointRange<Float>, onValueChange: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(value.toString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range,
            modifier = Modifier.height(32.dp)
        )
    }
}
