package com.ytauto.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.palette.graphics.Palette
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ytauto.db.RecentTrack
import com.ytauto.ui.theme.YTAutoTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            YTAutoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                viewModel.handleSharedText(sharedText)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.connectToService(context)
                Lifecycle.Event.ON_STOP -> viewModel.disconnectFromService()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    YTAutoScreen(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YTAutoScreen(viewModel: MainViewModel) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val dominantColor by viewModel.dominantColor.collectAsState()
    val isVideoMode by viewModel.isVideoMode.collectAsState()
    val downloadedTracks by viewModel.downloadedTracks.collectAsState()
    val downloadedUrls by viewModel.downloadedUrls.collectAsState()
    val recentTracks by viewModel.recentTracks.collectAsState()
    val isShizukuAvailable by viewModel.isShizukuAvailable.collectAsState()
    val hasShizukuPermission by viewModel.hasShizukuPermission.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val bassBoostStrength by viewModel.bassBoostStrength.collectAsState()
    val loudnessGain by viewModel.loudnessGain.collectAsState()
    val eqBands by viewModel.eqBands.collectAsState()

    var showFullScreenPlayer by remember { mutableStateOf(false) }
    var currentTab by remember { mutableIntStateOf(0) } // 0 = Recent, 1 = Search, 2 = Downloads, 3 = Settings

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    // Palette API Kleurextractie
    LaunchedEffect(nowPlaying?.artworkUri) {
        nowPlaying?.artworkUri?.let { uri ->
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context).data(uri.toString()).allowHardware(false).build()
            val result = (loader.execute(request) as? SuccessResult)?.drawable
            result?.let { drawable ->
                val bitmap = drawable.toBitmap()
                Palette.from(bitmap).generate { palette ->
                    val color = palette?.vibrantSwatch?.rgb ?: palette?.mutedSwatch?.rgb
                    viewModel.updateDominantColor(color)
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                if (nowPlaying != null) {
                    NowPlayingBar(
                        state = nowPlaying!!,
                        isPlaying = isPlaying,
                        isVideoMode = isVideoMode,
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onVideoModeToggle = { viewModel.toggleVideoMode() },
                        onClick = { showFullScreenPlayer = true }
                    )
                }
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.History, null) },
                        label = { Text("Recent") },
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Search, null) },
                        label = { Text("Zoeken") },
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.LibraryMusic, null) },
                        label = { Text("Bibliotheek") },
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text("Instellingen") },
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            val title = when (currentTab) {
                0 -> "Recent Gespeeld"
                1 -> "Zoeken"
                2 -> "Bibliotheek"
                else -> "Instellingen"
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "TabTransition"
            ) { targetTab ->
                when (targetTab) {
                    0 -> RecentTab(recentTracks, viewModel::playRecentTrack)
                    1 -> {
                        Column {
                            SearchBar(
                                query = query,
                                onQueryChanged = viewModel::onQueryChanged,
                                onSearch = viewModel::search,
                                isSearching = isSearching,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            if (isSearching) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(results, key = { it.videoUrl }) { result ->
                                        SearchResultItem(
                                            result = result,
                                            isDownloaded = downloadedUrls.contains(result.videoUrl),
                                            downloadProgress = downloadProgress[result.videoUrl],
                                            onClick = { viewModel.playItem(result) },
                                            onDownloadClick = { viewModel.downloadTrack(context, result) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(downloadedTracks, key = { it.videoUrl }) { track ->
                                OfflineTrackItem(
                                    track = track,
                                    onClick = { viewModel.playOfflineTrack(track) }
                                )
                            }
                        }
                    }
                    3 -> {
                        SettingsScreen(
                            isShizukuAvailable = isShizukuAvailable,
                            hasShizukuPermission = hasShizukuPermission,
                            onRequestShizukuPermission = viewModel::requestShizukuPermission,
                            bassBoostStrength = bassBoostStrength,
                            onBassBoostChange = viewModel::setBassBoost,
                            loudnessGain = loudnessGain,
                            onLoudnessChange = viewModel::setLoudness,
                            eqBands = eqBands,
                            onEqBandChange = viewModel::setEqBand,
                            onApplyShizukuHacks = viewModel::applyShizukuHacks,
                            onRefreshShizuku = viewModel::refreshShizuku,
                            currentPreset = viewModel.currentPreset.collectAsState().value,
                            presets = viewModel.presets.keys.toList(),
                            onApplyPreset = viewModel::applyPreset,
                            onClearAnalytics = { viewModel.clearAnalytics(context) }
                        )
                    }
                }
            }
        }
    }

    if (showFullScreenPlayer && nowPlaying != null) {
        ModalBottomSheet(
            onDismissRequest = { showFullScreenPlayer = false },
            sheetState = sheetState,
            dragHandle = null,
            containerColor = Color.Transparent,
            scrimColor = Color.Black.copy(alpha = 0.8f),
            modifier = Modifier.fillMaxSize()
        ) {
            FullScreenPlayer(
                state = nowPlaying!!,
                isPlaying = isPlaying,
                isVideoMode = isVideoMode,
                player = viewModel.getController(),
                currentPosition = currentPosition,
                duration = duration,
                queue = queue,
                dominantColor = dominantColor,
                onPlayPauseClick = { viewModel.togglePlayPause() },
                onVideoModeToggle = { viewModel.toggleVideoMode() },
                onSeek = { viewModel.seekTo(it) },
                onSkipNext = { viewModel.skipNext() },
                onSkipPrevious = { viewModel.skipPrevious() },
                onQueueItemClick = { viewModel.skipToQueueItem(it) },
                onDismiss = { showFullScreenPlayer = false }
            )
        }
    }
}

@Composable
private fun RecentTab(tracks: List<RecentTrack>, onTrackClick: (RecentTrack) -> Unit) {
    if (tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nog geen nummers afgespeeld", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(tracks, key = { it.videoUrl }) { track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTrackClick(track) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(track.thumbnailUrl).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChanged: (String) -> Unit, onSearch: () -> Unit, isSearching: Boolean, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQueryChanged,
        placeholder = { Text("Zoek op YouTube…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Zoek", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun SearchResultItem(result: com.ytauto.data.SearchResult, isDownloaded: Boolean, downloadProgress: Float?, onClick: () -> Unit, onDownloadClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(result.thumbnailUrl).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = result.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(text = result.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(contentAlignment = Alignment.Center) {
            if (downloadProgress != null && !isDownloaded) {
                CircularProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            IconButton(onClick = onDownloadClick) {
                Icon(
                    imageVector = if (isDownloaded) Icons.Default.CloudDone else Icons.Default.Download,
                    contentDescription = null,
                    tint = if (isDownloaded) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OfflineTrackItem(track: com.ytauto.db.OfflineTrack, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(track.title, style = MaterialTheme.typography.bodyLarge)
            Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NowPlayingBar(
    state: com.ytauto.ui.NowPlayingState,
    isPlaying: Boolean,
    isVideoMode: Boolean,
    onPlayPauseClick: () -> Unit,
    onVideoModeToggle: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = state.artworkUri,
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(state.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onVideoModeToggle) {
                Icon(
                    imageVector = if (isVideoMode) Icons.Default.MusicNote else Icons.Default.Videocam,
                    contentDescription = "Toggle Video",
                    tint = if (isVideoMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onPlayPauseClick) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun FullScreenPlayer(
    state: com.ytauto.ui.NowPlayingState,
    isPlaying: Boolean,
    isVideoMode: Boolean,
    player: androidx.media3.common.Player?,
    currentPosition: Long,
    duration: Long,
    queue: List<androidx.media3.common.MediaItem>,
    dominantColor: Int?,
    onPlayPauseClick: () -> Unit,
    onVideoModeToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onQueueItemClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Color(dominantColor ?: Color.Black.toArgb()), Color.Black))
    )) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White) }
                IconButton(onClick = onVideoModeToggle) { 
                    Icon(if (isVideoMode) Icons.Default.MusicNote else Icons.Default.Videocam, null, tint = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            if (isVideoMode && player != null) {
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply {
                            this.player = player
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    update = { view ->
                        view.player = player
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                )
            } else {
                AsyncImage(
                    model = state.artworkUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(state.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Text(state.artist, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f))
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Slider(
                value = currentPosition.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
            )
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(currentPosition), color = Color.White.copy(alpha = 0.5f))
                Text(formatTime(duration), color = Color.White.copy(alpha = 0.5f))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSkipPrevious) { Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(48.dp), tint = Color.White) }
                Spacer(modifier = Modifier.width(32.dp))
                FloatingActionButton(onClick = onPlayPauseClick, shape = CircleShape, containerColor = Color.White) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black)
                }
                Spacer(modifier = Modifier.width(32.dp))
                IconButton(onClick = onSkipNext) { Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(48.dp), tint = Color.White) }
            }
            
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

private fun formatTime(ms: Long): String {
    val sec = (ms / 1000) % 60
    val min = (ms / (1000 * 60)) % 60
    return "%d:%02d".format(min, sec)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isShizukuAvailable: Boolean,
    hasShizukuPermission: Boolean,
    onRequestShizukuPermission: () -> Unit,
    bassBoostStrength: Int,
    onBassBoostChange: (Int) -> Unit,
    loudnessGain: Int,
    onLoudnessChange: (Int) -> Unit,
    eqBands: List<Int>,
    onEqBandChange: (Int, Int) -> Unit,
    onApplyShizukuHacks: () -> Unit,
    onRefreshShizuku: () -> Unit,
    currentPreset: String,
    presets: List<String>,
    onApplyPreset: (String) -> Unit,
    onClearAnalytics: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Audio Tuning", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("Presets")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = currentPreset == preset,
                        onClick = { onApplyPreset(preset) },
                        label = { Text(preset) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("Bass Boost: $bassBoostStrength")
            Slider(value = bassBoostStrength.toFloat(), onValueChange = { onBassBoostChange(it.toInt()) }, valueRange = 0f..1000f)
            
            Text("Loudness Enhancer: $loudnessGain")
            Slider(value = loudnessGain.toFloat(), onValueChange = { onLoudnessChange(it.toInt()) }, valueRange = 0f..1000f)
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("5-Band Equalizer")
            eqBands.forEachIndexed { index, level ->
                Text("Band $index: $level dB")
                Slider(value = level.toFloat(), onValueChange = { onEqBandChange(index, it.toInt()) }, valueRange = -1500f..1500f)
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))
            
            Text("Privacy & Analytics", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onClearAnalytics,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Wis Afspeelgeschiedenis (Analytics)")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Systeem Hacks (Shizuku)", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onRefreshShizuku) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Shizuku")
                }
            }
            if (!isShizukuAvailable) {
                Text("Shizuku is niet geïnstalleerd.", color = MaterialTheme.colorScheme.error)
            } else if (!hasShizukuPermission) {
                Button(onClick = onRequestShizukuPermission) { Text("Geef Shizuku Toestemming") }
            } else {
                Button(onClick = onApplyShizukuHacks) { Text("Deactiveer Rij-restricties (ADB)") }
            }
        }
    }
}
