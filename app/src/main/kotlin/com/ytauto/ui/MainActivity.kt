package com.ytauto.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ytauto.data.SearchResult
import com.ytauto.ui.theme.YTAutoTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()

        setContent {
            YTAutoTheme {
                MediaControllerLifecycle(viewModel)
                YTAutoScreen(viewModel)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
private fun MediaControllerLifecycle(viewModel: MainViewModel) {
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

    var showFullScreenPlayer by remember { mutableStateOf(false) }
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
            if (nowPlaying != null) {
                NowPlayingBar(
                    state = nowPlaying!!,
                    isPlaying = isPlaying,
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onClick = { showFullScreenPlayer = true }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Text(
                text = "YT Auto",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

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
                        SearchResultItem(result = result, onClick = { viewModel.playItem(result) })
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
private fun SearchResultItem(result: SearchResult, onClick: () -> Unit) {
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
    }
}

@Composable
private fun NowPlayingBar(state: NowPlayingState, isPlaying: Boolean, onPlayPauseClick: () -> Unit, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = state.artworkUri.toString(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = state.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = state.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) {
                Icon(imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullScreenPlayer(
    state: NowPlayingState,
    isPlaying: Boolean,
    isVideoMode: Boolean,
    player: Player?,
    currentPosition: Long,
    duration: Long,
    queue: List<MediaItem>,
    dominantColor: Int?,
    onPlayPauseClick: () -> Unit,
    onVideoModeToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onQueueItemClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = dominantColor?.let { Color(it) } ?: MaterialTheme.colorScheme.surface,
        animationSpec = tween(1000)
    )

    Box(modifier = Modifier.fillMaxSize().drawBehind {
        drawRect(Brush.verticalGradient(colors = listOf(backgroundColor.copy(alpha = 0.9f), Color.Black)))
    }) {
        // Dynamic Glow Background
        Box(modifier = Modifier.fillMaxSize().blur(100.dp).drawBehind {
            drawCircle(color = backgroundColor.copy(alpha = 0.4f), radius = size.minDimension, center = Offset(size.width / 2, size.height / 3))
        })

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Header
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = null, tint = Color.White) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AUDIO", style = MaterialTheme.typography.labelMedium, color = if (!isVideoMode) Color.White else Color.White.copy(0.4f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(checked = isVideoMode, onCheckedChange = { onVideoModeToggle() }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = backgroundColor.copy(alpha = 0.8f)))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("VIDEO", style = MaterialTheme.typography.labelMedium, color = if (isVideoMode) Color.White else Color.White.copy(0.4f))
                }
                IconButton(onClick = {}) { Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White.copy(0.7f)) }
            }

            LazyColumn(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(modifier = Modifier.fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)) {
                        if (isVideoMode && player != null) {
                            AndroidView(factory = { context -> PlayerView(context).apply { this.player = player; useController = false; resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT; setBackgroundColor(android.graphics.Color.BLACK) } }, modifier = Modifier.fillMaxSize())
                        } else {
                            AsyncImage(model = state.artworkUri.toString(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(text = state.title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = Color.White, shadow = Shadow(blurRadius = 12f, color = Color.Black.copy(alpha = 0.6f))), maxLines = 1, modifier = Modifier.basicMarquee())
                        Text(text = state.artist, style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    GlowSlider(currentPosition = currentPosition, duration = duration, accentColor = backgroundColor, onSeek = onSeek)
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onSkipPrevious, modifier = Modifier.size(64.dp)) { Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp)) }
                        IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(88.dp).background(Color.White, CircleShape)) { Icon(imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(56.dp), tint = Color.Black) }
                        IconButton(onClick = onSkipNext, modifier = Modifier.size(64.dp)) { Icon(Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp)) }
                    }
                    Spacer(modifier = Modifier.height(40.dp))
                    if (queue.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(text = "UP NEXT", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                        }
                    }
                }
                itemsIndexed(queue) { index, item ->
                    QueueItem(item, onClick = { onQueueItemClick(index) })
                }
            }
        }
    }
}

@Composable
private fun GlowSlider(currentPosition: Long, duration: Long, accentColor: Color, onSeek: (Long) -> Unit) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf(0f) }

    // Animeer alleen wanneer de gebruiker NIET sleept — anders rubber-banding
    val animatedPosition by animateFloatAsState(
        targetValue = if (isDragging) dragPosition else currentPosition.toFloat(),
        animationSpec = if (isDragging) tween(0) else tween(500),
        label = "sliderPosition"
    )
    val displayPosition = if (isDragging) dragPosition else animatedPosition

    Column {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.fillMaxWidth(0.95f).height(4.dp).blur(8.dp).background(accentColor.copy(alpha = 0.3f), RoundedCornerShape(2.dp)))
            Slider(
                value = displayPosition,
                onValueChange = { pos ->
                    isDragging = true
                    dragPosition = pos
                },
                onValueChangeFinished = {
                    onSeek(dragPosition.toLong())
                    isDragging = false
                },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = formatTime(if (isDragging) dragPosition.toLong() else currentPosition), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
            Text(text = formatTime(duration), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun QueueItem(item: MediaItem, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = item.mediaMetadata.artworkUri.toString(), contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.mediaMetadata.title?.toString() ?: "", style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = item.mediaMetadata.artist?.toString() ?: "", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f), maxLines = 1)
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
