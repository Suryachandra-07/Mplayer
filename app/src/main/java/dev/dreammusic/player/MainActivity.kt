package dev.dreammusic.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.dreammusic.player.data.LocalMusicLibrary
import dev.dreammusic.player.data.ArchiveMusicCatalog
import dev.dreammusic.player.data.ArchiveTrack
import dev.dreammusic.player.data.LocalPlaylist
import dev.dreammusic.player.data.LocalPlaylistStore
import dev.dreammusic.player.data.LocalTrack
import dev.dreammusic.player.playback.MusicPlaybackService
import dev.dreammusic.player.ui.AddSongsScreen
import dev.dreammusic.player.ui.PlaylistDetailScreen
import dev.dreammusic.player.ui.PlaylistEditorScreen
import dev.dreammusic.player.ui.PickPlaylistScreen
import dev.dreammusic.player.ui.PlaylistRoute
import dev.dreammusic.player.ui.PlaylistCard
import dev.dreammusic.player.ui.PlaylistTrackRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF000000)
private val SurfaceBlack = Color(0xFF111010)
private val SoftWhite = Color(0xFFF5F0ED)
private val Muted = Color(0xFFAAA19D)
private val Ember = Color(0xFFD89073)

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controllerState = mutableStateOf<MediaController?>(null)
    private lateinit var audioPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private var permissionCallback: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionCallback?.invoke(granted)
            permissionCallback = null
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        connectPlayback()
        setContent {
            DreamMusicApp(
                controller = controllerState.value,
                requestAudioPermission = ::requestAudioPermission,
            )
        }
    }

    private fun connectPlayback() {
        val token = SessionToken(this, android.content.ComponentName(this, MusicPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching { future.get() }.onSuccess { controllerState.value = it }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestAudioPermission(onResult: (Boolean) -> Unit) {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            onResult(true)
            return
        }
        permissionCallback = onResult
        audioPermissionLauncher.launch(permission)
    }

    override fun onDestroy() {
        controllerState.value?.release()
        controllerState.value = null
        controllerFuture?.cancel(false)
        controllerFuture = null
        super.onDestroy()
    }
}

private enum class Tab(val title: String) { HOME("Home"), SEARCH("Discover"), LIBRARY("Library") }

@Composable
private fun DreamMusicApp(controller: MediaController?, requestAudioPermission: ((Boolean) -> Unit) -> Unit) {
    var tab by remember { mutableStateOf(Tab.HOME) }
    var showPlayer by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf<List<LocalTrack>>(emptyList()) }
    var permissionReady by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var onlineSearch by remember { mutableStateOf(true) }
    var onlineTracks by remember { mutableStateOf<List<ArchiveTrack>>(emptyList()) }
    var onlineLoading by remember { mutableStateOf(false) }
    var onlineError by remember { mutableStateOf<String?>(null) }
    var resolvingId by remember { mutableStateOf<String?>(null) }
    var currentTitle by remember { mutableStateOf("Nothing playing") }
    var currentArtist by remember { mutableStateOf("Choose a song from your library") }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var playlistRoute by remember { mutableStateOf<PlaylistRoute?>(null) }
    var pickerReturnRoute by remember { mutableStateOf<PlaylistRoute?>(null) }
    var editorReturnRoute by remember { mutableStateOf<PlaylistRoute?>(null) }

    // Retain the activity context before doing the MediaStore query off the main thread.
    val context = androidx.compose.ui.platform.LocalContext.current
    val scanScope = rememberCoroutineScope()
    val playlistStore = remember(context) { LocalPlaylistStore(context) }
    val onlineCatalog = remember { ArchiveMusicCatalog() }
    val uiScope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf(playlistStore.all()) }
    val saveableStateHolder = rememberSaveableStateHolder()

    fun refreshPlaylists() { playlists = playlistStore.all() }
    fun openPlaylistPicker(track: LocalTrack) {
        pickerReturnRoute = playlistRoute
        playlistRoute = PlaylistRoute.PickPlaylist(track)
    }
    fun scanTracks() {
        requestAudioPermission { granted ->
            permissionReady = granted
            if (granted) scanScope.launch {
                val result = withContext(Dispatchers.IO) { LocalMusicLibrary(context).loadTracks() }
                tracks = result
            }
        }
    }

    LaunchedEffect(tab, onlineSearch, query) {
        if (tab == Tab.SEARCH && onlineSearch && query.isNotBlank()) {
            kotlinx.coroutines.delay(350)
            onlineLoading = true
            onlineError = null
            try {
                onlineTracks = withContext(Dispatchers.IO) { onlineCatalog.search(query) }
            } catch (failure: Exception) {
                onlineTracks = emptyList()
                onlineError = failure.message ?: "Could not reach the music catalog. Check your connection and try again."
            } finally {
                onlineLoading = false
            }
        } else if (tab == Tab.SEARCH && onlineSearch && query.isBlank()) {
            onlineTracks = emptyList()
            onlineError = null
        }
    }

    fun playOnline(track: ArchiveTrack) {
        uiScope.launch {
            resolvingId = track.identifier
            onlineError = null
            try {
                val item = withContext(Dispatchers.IO) { onlineCatalog.resolve(track) }
                controller?.setMediaItem(item)
                controller?.prepare()
                controller?.play()
                showPlayer = true
            } catch (failure: Exception) {
                onlineError = failure.message ?: "That recording could not be played. Try another result."
            } finally {
                resolvingId = null
            }
        }
    }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentTitle = mediaItem?.mediaMetadata?.title?.toString() ?: "Nothing playing"
                currentArtist = mediaItem?.mediaMetadata?.artist?.toString() ?: "Choose a song from your library"
                duration = controller?.duration?.coerceAtLeast(0L) ?: 0L
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        controller?.addListener(listener)
        controller?.let { player ->
            val item = player.currentMediaItem
            currentTitle = item?.mediaMetadata?.title?.toString() ?: "Nothing playing"
            currentArtist = item?.mediaMetadata?.artist?.toString() ?: "Choose a song from your library"
            isPlaying = player.isPlaying
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
        }
        onDispose { controller?.removeListener(listener) }
    }

    androidx.compose.runtime.LaunchedEffect(controller, isPlaying) {
        while (isPlaying) {
            position = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
            duration = controller?.duration?.coerceAtLeast(0L) ?: 0L
            kotlinx.coroutines.delay(500)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = SurfaceBlack, primary = Ember, onPrimary = Ink,
        onBackground = SoftWhite, onSurface = SoftWhite, secondary = Ember,
    )) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                if (showPlayer) {
                    FullPlayer(
                        title = currentTitle, artist = currentArtist, playing = isPlaying,
                        position = position, duration = duration,
                        onBack = { showPlayer = false },
                        onToggle = { if (isPlaying) controller?.pause() else controller?.play() },
                        onPrevious = { controller?.seekToPreviousMediaItem() },
                        onNext = { controller?.seekToNextMediaItem() },
                        onSeek = { controller?.seekTo(it.toLong()) },
                    )
                } else {
                    Box(Modifier.weight(1f)) {
                    val route = playlistRoute
                    if (route == null) saveableStateHolder.SaveableStateProvider("main") {
                    when (tab) {
                        Tab.HOME -> HomeScreen(
                            tracks = tracks.take(5), title = currentTitle,
                            playing = isPlaying, onLoad = ::scanTracks,
                            onPlay = { track ->
                                playTracks(controller, tracks, tracks.indexOf(track))
                                showPlayer = true
                            },
                            onAddToPlaylist = ::openPlaylistPicker,
                            onOpenPlayer = { showPlayer = true },
                            onLibrary = { tab = Tab.LIBRARY },
                            onDiscover = { tab = Tab.SEARCH; onlineSearch = true },
                        )
                        Tab.SEARCH -> SearchScreen(
                            query = query, onQuery = { query = it }, onlineSearch = onlineSearch,
                            onOnlineSearch = { onlineSearch = it }, onlineTracks = onlineTracks,
                            onlineLoading = onlineLoading, onlineError = onlineError, resolvingId = resolvingId,
                            onPlayOnline = ::playOnline,
                            tracks = tracks.filter {
                                query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true)
                            }, onLoad = ::scanTracks, onPlay = { track ->
                                playTracks(controller, tracks, tracks.indexOf(track))
                                showPlayer = true
                            }, onAddToPlaylist = ::openPlaylistPicker,
                        )
                        Tab.LIBRARY -> LibraryScreen(
                            tracks = tracks, playlists = playlists, permissionReady = permissionReady,
                            onLoad = ::scanTracks, onPlay = { track ->
                                playTracks(controller, tracks, tracks.indexOf(track))
                                showPlayer = true
                            }, onAddToPlaylist = ::openPlaylistPicker,
                            onOpenPlaylist = { playlistRoute = PlaylistRoute.Detail(it.id) },
                            onCreatePlaylist = {
                                editorReturnRoute = null
                                playlistRoute = PlaylistRoute.Create
                            },
                        )
                    }
                    } else saveableStateHolder.SaveableStateProvider(route.key) {
                        when (route) {
                            is PlaylistRoute.Detail -> {
                                val playlist = playlists.firstOrNull { it.id == route.playlistId }
                                if (playlist == null) playlistRoute = null
                                else PlaylistDetailScreen(
                                    playlist = playlist, tracks = tracks,
                                    onBack = { playlistRoute = null },
                                    onAddSongs = { playlistRoute = PlaylistRoute.AddSongs(playlist.id) },
                                    onEdit = { playlistRoute = PlaylistRoute.Edit(playlist) },
                                    onPlay = { track ->
                                        playPlaylist(controller, playlist, tracks, track)
                                        showPlayer = true
                                    },
                                    onAddTrackToPlaylist = ::openPlaylistPicker,
                                )
                            }
                            is PlaylistRoute.AddSongs -> {
                                val playlist = playlists.firstOrNull { it.id == route.playlistId }
                                if (playlist == null) playlistRoute = null
                                else AddSongsScreen(
                                    playlistName = playlist.name, tracks = tracks,
                                    onBack = { playlistRoute = PlaylistRoute.Detail(playlist.id) },
                                    onAdd = { ids ->
                                        playlistStore.addSongs(playlist.id, ids)
                                        refreshPlaylists()
                                        saveableStateHolder.removeState(route.key)
                                        playlistRoute = PlaylistRoute.Detail(playlist.id)
                                    },
                                )
                            }
                            is PlaylistRoute.PickPlaylist -> PickPlaylistScreen(
                                track = route.track, playlists = playlists,
                                onBack = { playlistRoute = pickerReturnRoute },
                                onCreatePlaylist = {
                                    editorReturnRoute = route
                                    playlistRoute = PlaylistRoute.Create
                                },
                                onSelect = { playlist ->
                                    playlistStore.addSongs(playlist.id, listOf(route.track.id))
                                    refreshPlaylists()
                                    android.widget.Toast.makeText(context, "Added to ${playlist.name}", android.widget.Toast.LENGTH_SHORT).show()
                                    playlistRoute = pickerReturnRoute
                                },
                            )
                            is PlaylistRoute.Edit -> PlaylistEditorScreen(
                                initialName = route.playlist.name, isNew = false,
                                onBack = { playlistRoute = PlaylistRoute.Detail(route.playlist.id) },
                                onSave = { name ->
                                    playlistStore.rename(route.playlist.id, name)
                                    refreshPlaylists()
                                    playlistRoute = PlaylistRoute.Detail(route.playlist.id)
                                },
                                onDelete = {
                                    playlistStore.remove(route.playlist.id)
                                    refreshPlaylists()
                                    playlistRoute = null
                                    tab = Tab.LIBRARY
                                },
                            )
                            PlaylistRoute.Create -> PlaylistEditorScreen(
                                initialName = "", isNew = true,
                                onBack = { playlistRoute = editorReturnRoute },
                                onSave = { name ->
                                    val created = playlistStore.create(name)
                                    refreshPlaylists()
                                    playlistRoute = if (editorReturnRoute is PlaylistRoute.PickPlaylist && created != null) {
                                        editorReturnRoute
                                    } else null
                                },
                                onDelete = null,
                            )
                        }
                    }
                    }
                    if (currentTitle != "Nothing playing") {
                        MiniPlayer(currentTitle, currentArtist, isPlaying,
                            onClick = { showPlayer = true },
                            onToggle = { if (isPlaying) controller?.pause() else controller?.play() })
                    }
                    if (playlistRoute == null) BottomNavigation(tab, onSelect = { tab = it })
                }
            }
        }
    }
}

private fun playTracks(controller: MediaController?, tracks: List<LocalTrack>, index: Int) {
    if (tracks.isEmpty() || controller == null) return
    controller.setMediaItems(tracks.map { it.toMediaItem() }, index, 0L)
    controller.prepare()
    controller.play()
}

private fun playPlaylist(controller: MediaController?, playlist: LocalPlaylist, library: List<LocalTrack>, selected: LocalTrack) {
    val queue = playlist.trackIds.mapNotNull { id -> library.firstOrNull { it.id == id } }
    val startIndex = queue.indexOfFirst { it.id == selected.id }
    if (controller == null || queue.isEmpty() || startIndex < 0) return
    controller.setMediaItems(queue.map { it.toMediaItem() }, startIndex, 0L)
    controller.prepare()
    controller.play()
}

@Composable
private fun HomeScreen(
    tracks: List<LocalTrack>, title: String, playing: Boolean,
    onLoad: () -> Unit, onPlay: (LocalTrack) -> Unit, onAddToPlaylist: (LocalTrack) -> Unit,
    onOpenPlayer: () -> Unit, onLibrary: () -> Unit, onDiscover: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("DREAM MUSIC", color = Ember, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text("Find your\nnext sound.", color = SoftWhite, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onLibrary, modifier = Modifier.size(50.dp).background(SurfaceBlack, CircleShape)) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = "Open library", tint = SoftWhite)
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF603A2F), Color(0xFF211513), Color(0xFF111010))))
                .clickable(onClick = onDiscover).padding(22.dp)) {
                Column(Modifier.align(Alignment.BottomStart)) {
                    Text("DISCOVER MUSIC ONLINE", color = Color(0xFFEAB49D), fontSize = 10.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Explore independent\nmusic and recordings", color = SoftWhite, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
                }
                Box(Modifier.align(Alignment.TopEnd).size(48.dp).background(Color.White.copy(alpha = .12f), CircleShape), Alignment.Center) {
                    Icon(Icons.Default.Cloud, null, tint = SoftWhite, modifier = Modifier.size(24.dp))
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("On this device", color = SoftWhite, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("SEE ALL", color = Ember, fontSize = 11.sp, letterSpacing = 1.5.sp, modifier = Modifier.clickable(onClick = onLibrary))
            }
        }
        if (tracks.isEmpty()) {
            item { EmptyState("Your local library is ready when you are", "Allow music access to find songs stored on this device.", "Scan music", onLoad) }
        } else {
            items(tracks) { track -> PlaylistTrackRow(track, onClick = { onPlay(track) }, onAddToPlaylist = { onAddToPlaylist(track) }) }
        }
        if (title != "Nothing playing") item {
            Text("NOW PLAYING  ·  ${if (playing) "PLAYING" else "PAUSED"}", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp,
                modifier = Modifier.clickable(onClick = onOpenPlayer))
        }
    }
}

@Composable
private fun SearchScreen(
    query: String, onQuery: (String) -> Unit, onlineSearch: Boolean, onOnlineSearch: (Boolean) -> Unit,
    onlineTracks: List<ArchiveTrack>, onlineLoading: Boolean, onlineError: String?, resolvingId: String?,
    onPlayOnline: (ArchiveTrack) -> Unit, tracks: List<LocalTrack>, onLoad: () -> Unit,
    onPlay: (LocalTrack) -> Unit, onAddToPlaylist: (LocalTrack) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).padding(top = 32.dp)) {
        Text(if (onlineSearch) "Discover\nmusic online." else "Search your\nlibrary.", color = SoftWhite, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchModeChip("Online", onlineSearch) { onOnlineSearch(true) }
            SearchModeChip("On device", !onlineSearch) { onOnlineSearch(false) }
        }
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.OutlinedTextField(
            value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(if (onlineSearch) "Search artists, songs, recordings" else "Search songs or artists", color = Muted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Ember) },
            singleLine = true, shape = RoundedCornerShape(18.dp),
        )
        Spacer(Modifier.height(14.dp))
        if (onlineSearch) {
            Text("Online audio from the Internet Archive · rights vary by item", color = Muted, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            when {
                onlineLoading -> Text("Searching the catalog…", color = Muted, modifier = Modifier.padding(vertical = 20.dp))
                onlineError != null -> Text(onlineError, color = Color(0xFFE6A28D), modifier = Modifier.padding(vertical = 20.dp))
                query.isBlank() -> Column(Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
                    Text("Search a song or artist", color = SoftWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("Find independent releases, live recordings, and other audio shared in the archive.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                }
                onlineTracks.isEmpty() -> Text("No recordings found. Try another search.", color = Muted, modifier = Modifier.padding(vertical = 20.dp))
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(onlineTracks, key = { it.identifier }) { track ->
                        ArchiveTrackRow(track, isLoading = resolvingId == track.identifier, onClick = { onPlayOnline(track) })
                    }
                }
            }
        } else if (tracks.isEmpty()) EmptyState("Search your music", "Local songs appear here after you scan your library.", "Scan music", onLoad)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(tracks) { track -> PlaylistTrackRow(track, onClick = { onPlay(track) }, onAddToPlaylist = { onAddToPlaylist(track) }) }
        }
    }
}

@Composable
private fun SearchModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, color = if (selected) Ink else SoftWhite, fontSize = 12.sp,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) Ember else SurfaceBlack)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp))
}

@Composable
private fun ArchiveTrackRow(track: ArchiveTrack, isLoading: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFF29201D)), Alignment.Center) {
            Icon(if (isLoading) Icons.Default.Cloud else Icons.Default.MusicNote, null, tint = Ember, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, color = SoftWhite, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(track.artist, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
        }
        Text(if (isLoading) "…" else "PLAY", color = Ember, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

@Composable
private fun LibraryScreen(
    tracks: List<LocalTrack>, playlists: List<LocalPlaylist>, permissionReady: Boolean,
    onLoad: () -> Unit, onPlay: (LocalTrack) -> Unit, onAddToPlaylist: (LocalTrack) -> Unit,
    onOpenPlaylist: (LocalPlaylist) -> Unit, onCreatePlaylist: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).padding(top = 24.dp)) {
        Text("Your library", color = SoftWhite, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(if (permissionReady) "${tracks.size} songs on this device" else "Music from every part of your life", color = Muted)
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Playlists", color = SoftWhite, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("+ New", color = Ember, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onCreatePlaylist))
                }
            }
            if (playlists.isEmpty()) item {
                EmptyState("Create your first playlist", "Keep a collection of songs together on this device.", "Create playlist", onCreatePlaylist)
            } else items(playlists, key = { "playlist-${it.id}" }) {
                PlaylistCard(it, onClick = { onOpenPlaylist(it) })
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Songs", color = SoftWhite, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (tracks.isEmpty()) Text("SCAN", color = Ember, fontSize = 11.sp, modifier = Modifier.clickable(onClick = onLoad))
                }
            }
            if (tracks.isEmpty()) item {
                EmptyState("Your collection starts here", "Scan this device to see songs stored locally.", "Scan this device", onLoad)
            } else items(tracks, key = { "track-${it.id}" }) { track ->
                PlaylistTrackRow(track, onClick = { onPlay(track) }, onAddToPlaylist = { onAddToPlaylist(track) })
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String, action: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(SurfaceBlack).padding(20.dp)) {
        Text(title, color = SoftWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(16.dp))
        Text(action.uppercase(), color = Ember, fontSize = 11.sp, letterSpacing = 1.5.sp, modifier = Modifier.clickable(onClick = onClick))
    }
}

@Composable
private fun MiniPlayer(title: String, artist: String, playing: Boolean, onClick: () -> Unit, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).clip(RoundedCornerShape(18.dp))
        .background(Color(0xFF211916)).clickable(onClick = onClick).padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF633F33)), Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, tint = Color(0xFFE7B5A1))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = SoftWhite, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(artist, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
        }
        IconButton(onClick = onToggle) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", tint = SoftWhite) }
    }
}

@Composable
private fun BottomNavigation(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceAround) {
        listOf(Tab.HOME to Icons.Default.Home, Tab.SEARCH to Icons.Default.Search, Tab.LIBRARY to Icons.Default.LibraryMusic).forEach { (tab, icon) ->
            Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable { onSelect(tab) }.padding(horizontal = 22.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, tab.title, tint = if (selected == tab) Ember else Muted, modifier = Modifier.size(21.dp))
                Text(tab.title, color = if (selected == tab) Ember else Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun FullPlayer(
    title: String, artist: String, playing: Boolean, position: Long, duration: Long,
    onBack: () -> Unit, onToggle: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onSeek: (Float) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Ink).padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = SoftWhite) }
            Text("NOW PLAYING", color = SoftWhite, fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.size(48.dp))
        }
        Spacer(Modifier.weight(.5f))
        Box(Modifier.fillMaxWidth().weight(3f).clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF8C5743), Color(0xFF3C2924), Color(0xFF161212)))), Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(82.dp))
        }
        Spacer(Modifier.height(30.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = SoftWhite, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist, color = Muted, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Slider(value = position.coerceAtMost(duration.coerceAtLeast(1L)).toFloat(), onValueChange = onSeek, valueRange = 0f..duration.coerceAtLeast(1L).toFloat(), colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = SoftWhite, activeTrackColor = SoftWhite, inactiveTrackColor = Muted.copy(alpha = .4f)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(position), color = Muted, fontSize = 12.sp)
            Text("-${formatDuration((duration - position).coerceAtLeast(0L))}", color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(54.dp)) { Icon(Icons.Default.SkipPrevious, "Previous", tint = SoftWhite, modifier = Modifier.size(34.dp)) }
            IconButton(onClick = onToggle, modifier = Modifier.size(78.dp).background(SoftWhite, CircleShape)) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", tint = Ink, modifier = Modifier.size(42.dp))
            }
            IconButton(onClick = onNext, modifier = Modifier.size(54.dp)) { Icon(Icons.Default.SkipNext, "Next", tint = SoftWhite, modifier = Modifier.size(34.dp)) }
        }
        Spacer(Modifier.weight(.8f))
    }
}

internal fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
