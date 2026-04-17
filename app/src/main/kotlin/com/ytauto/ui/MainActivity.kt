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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ytauto.data.SearchResult
import com.ytauto.ui.theme.YTAutoTheme

/**
 * MainActivity — Mobiele Compose UI
 *
 * Biedt een eenvoudige interface om:
 * 1. Te zoeken op YouTube
 * 2. Zoekresultaten te bekijken
 * 3. Een nummer af te spelen
 * 4. De huidige track te zien/pauzeren
 *
 * De Activity communiceert met de PlaybackService via de ViewModel's
 * MediaController.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // ── Permissie-launcher voor POST_NOTIFICATIONS (Android 13+) ──
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* We gaan door ongeacht het resultaat — het beïnvloedt alleen
           of de notificatie zichtbaar is, niet de functionaliteit. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Vraag notificatiepermissie aan op Android 13+
        requestNotificationPermission()

        setContent {
            YTAutoTheme {
                // Beheer de levenscyclus van de MediaController-verbinding
                MediaControllerLifecycle(viewModel)

                YTAutoScreen(viewModel)
            }
        }
    }

    /**
     * Vraagt de POST_NOTIFICATIONS permissie aan als die nog niet verleend is.
     * Vereist vanaf Android 13 (API 33) om notificaties te tonen.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// LIFECYCLE MANAGEMENT
// ═══════════════════════════════════════════════════════════════

/**
 * Beheert de MediaController-verbinding op basis van de Activity lifecycle.
 * Verbindt bij onStart, verbreekt bij onStop.
 */
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

// ═══════════════════════════════════════════════════════════════
// HOOFDSCHERM
// ═══════════════════════════════════════════════════════════════

@Composable
private fun YTAutoScreen(viewModel: MainViewModel) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Now Playing bar onderaan het scherm
        bottomBar = {
            AnimatedVisibility(
                visible = nowPlaying != null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                nowPlaying?.let { state ->
                    NowPlayingBar(
                        state = state,
                        isPlaying = isPlaying,
                        onPlayPauseClick = { viewModel.togglePlayPause() }
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
            // ── App titel ──
            Text(
                text = "YT Auto",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            // ── Zoekbalk ──
            SearchBar(
                query = query,
                onQueryChanged = viewModel::onQueryChanged,
                onSearch = viewModel::search,
                isSearching = isSearching,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Error bericht ──
            errorMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            // ── Zoekresultaten ──
            if (isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(results, key = { it.videoUrl }) { result ->
                        SearchResultItem(
                            result = result,
                            onClick = { viewModel.playItem(result) }
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// ZOEKBALK
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChanged,
        placeholder = {
            Text(
                "Zoek op YouTube…",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = "Zoek",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
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

// ═══════════════════════════════════════════════════════════════
// ZOEKRESULTAAT ITEM
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SearchResultItem(
    result: SearchResult,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Thumbnail ──
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(result.thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = result.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Spacer(modifier = Modifier.width(14.dp))

            // ── Titel en artiest ──
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${result.artist} · ${result.formattedDuration}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// NOW PLAYING BAR
// ═══════════════════════════════════════════════════════════════

@Composable
private fun NowPlayingBar(
    state: NowPlayingState,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Artwork ──
            if (state.artworkUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(state.artworkUri.toString())
                        .crossfade(true)
                        .build(),
                    contentDescription = "Cover art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // ── Track info ──
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = state.artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Play/Pause knop ──
            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pauzeer" else "Afspelen",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
