package dev.dreammusic.player.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.dreammusic.player.data.LocalPlaylist
import dev.dreammusic.player.data.LocalTrack

private val Ink = Color(0xFF000000)
private val Card = Color(0xFF111010)
private val White = Color(0xFFF5F0ED)
private val Muted = Color(0xFFAAA19D)
private val Ember = Color(0xFFD89073)

sealed interface PlaylistRoute {
    val key: String
    data class Detail(val playlistId: String) : PlaylistRoute { override val key = "playlist:$playlistId" }
    data class AddSongs(val playlistId: String) : PlaylistRoute { override val key = "add-songs:$playlistId" }
    data class PickPlaylist(val track: LocalTrack) : PlaylistRoute { override val key = "pick-playlist:${track.id}" }
    data class Edit(val playlist: LocalPlaylist) : PlaylistRoute { override val key = "edit-playlist:${playlist.id}" }
    data object Create : PlaylistRoute { override val key = "create-playlist" }
}

@Composable
fun PlaylistDetailScreen(
    playlist: LocalPlaylist,
    tracks: List<LocalTrack>,
    onBack: () -> Unit,
    onAddSongs: () -> Unit,
    onEdit: () -> Unit,
    onPlay: (LocalTrack) -> Unit,
    onAddTrackToPlaylist: (LocalTrack) -> Unit,
) {
    val listState = rememberLazyListState()
    val playlistTracks = remember(playlist.trackIds, tracks) {
        playlist.trackIds.mapNotNull { id -> tracks.firstOrNull { it.id == id } }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Ink),
        state = listState,
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = White) }
                Text("PLAYLIST", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
            }
        }
        item {
            Box(Modifier.fillMaxWidth().height(192.dp).clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF70483D), Color(0xFF201715), Card))).padding(22.dp)) {
                Column(Modifier.align(Alignment.BottomStart)) {
                    Text("YOUR COLLECTION", color = Color(0xFFEAB49D), fontSize = 10.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(playlist.name, color = White, fontSize = 27.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${playlistTracks.size} songs", color = White.copy(alpha = .72f), fontSize = 13.sp)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onAddSongs, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add Songs")
                }
                Surface(color = Card, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f).clickable(onClick = onEdit)) {
                    Row(Modifier.padding(vertical = 13.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, null, tint = White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Edit playlist", color = White, fontSize = 14.sp)
                    }
                }
            }
        }
        if (playlistTracks.isEmpty()) {
            item { EmptyPlaylist(onAddSongs) }
        } else {
            item { Text("TRACKS", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 10.dp)) }
            items(playlistTracks, key = { it.id }) { track ->
                PlaylistTrackRow(track, onClick = { onPlay(track) }, onAddToPlaylist = { onAddTrackToPlaylist(track) })
            }
        }
    }
}

@Composable
fun AddSongsScreen(
    playlistName: String,
    tracks: List<LocalTrack>,
    onBack: () -> Unit,
    onAdd: (List<Long>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val filteredTracks = remember(query, tracks) {
        tracks.filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
    }
    Column(Modifier.fillMaxSize().background(Ink).padding(horizontal = 20.dp)) {
        ScreenHeader("Add songs", onBack)
        Text("TO  $playlistName", color = Ember, fontSize = 10.sp, letterSpacing = 1.7.sp)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search songs on this device", color = Muted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Ember) },
            singleLine = true, shape = RoundedCornerShape(16.dp),
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("On this device", color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${selectedIds.size} selected", color = Muted, fontSize = 13.sp)
        }
        if (tracks.isEmpty()) {
            EmptyPlaylistMessage("No local songs found", "Scan your music library first, then return here.")
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(filteredTracks, key = { it.id }) { track ->
                    val selected = track.id in selectedIds
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                            selectedIds = if (selected) selectedIds - track.id else selectedIds + track.id
                        }.padding(horizontal = 4.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TrackArtwork()
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(track.title, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                            Text(track.artist, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                        }
                        Checkbox(checked = selected, onCheckedChange = { checked ->
                            selectedIds = if (checked) selectedIds + track.id else selectedIds - track.id
                        })
                    }
                }
            }
        }
        Button(
            onClick = { onAdd(selectedIds.toList()) },
            enabled = selectedIds.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).height(52.dp),
            shape = RoundedCornerShape(17.dp),
        ) {
            Icon(Icons.Default.Check, null)
            Spacer(Modifier.width(8.dp))
            Text("Add ${selectedIds.size} selected")
        }
    }
}

@Composable
fun PickPlaylistScreen(
    track: LocalTrack,
    playlists: List<LocalPlaylist>,
    onBack: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onSelect: (LocalPlaylist) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, playlists) { playlists.filter { it.name.contains(query, true) } }
    Column(Modifier.fillMaxSize().background(Ink).padding(horizontal = 20.dp)) {
        ScreenHeader("Add to playlist", onBack)
        Surface(Modifier.fillMaxWidth(), color = Card, shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TrackArtwork()
                Column(Modifier.padding(start = 10.dp)) {
                    Text(track.title, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search playlists", color = Muted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Ember) },
            singleLine = true, shape = RoundedCornerShape(16.dp),
        )
        TextButton(onClick = onCreatePlaylist, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(7.dp))
            Text("Create a playlist")
        }
        if (filtered.isEmpty()) {
            EmptyPlaylistMessage("No matching playlists", "Create a playlist to keep this song in your library.")
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(filtered, key = { it.id }) { playlist ->
                PlaylistCard(playlist, onClick = { onSelect(playlist) })
            }
        }
    }
}

@Composable
fun PlaylistEditorScreen(
    initialName: String,
    isNew: Boolean,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    Column(Modifier.fillMaxSize().background(Ink).padding(horizontal = 20.dp)) {
        ScreenHeader(if (isNew) "New playlist" else "Edit playlist", onBack)
        Text("Give this collection a name", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Playlist name") }, singleLine = true, shape = RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
            Text(if (isNew) "Create playlist" else "Save changes")
        }
        if (onDelete != null) {
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.DeleteOutline, null)
                Spacer(Modifier.width(6.dp))
                Text("Delete playlist")
            }
        }
    }
}

@Composable
fun PlaylistCard(playlist: LocalPlaylist, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackArtwork()
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(playlist.name, color = White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${playlist.trackIds.size} songs", color = Muted, fontSize = 12.sp)
        }
        Icon(Icons.Default.PlayArrow, null, tint = Ember)
    }
}

@Composable
fun PlaylistTrackRow(track: LocalTrack, onClick: () -> Unit, onAddToPlaylist: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card).clickable(onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        TrackArtwork()
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(track.title, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(track.artist, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
        }
        androidx.compose.material3.IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, "Song options", tint = Muted)
        }
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Add to playlist") },
                onClick = { expanded = false; onAddToPlaylist() },
                leadingIcon = { Icon(Icons.Default.Add, null) },
            )
        }
    }
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = White) }
        Text(title, color = White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TrackArtwork() {
    Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFF70483D), Color(0xFF26201F)))), Alignment.Center) {
        Icon(Icons.Default.MusicNote, null, tint = Color(0xFFE7B5A1))
    }
}

@Composable
private fun EmptyPlaylist(onAddSongs: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(20.dp)) {
        Text("This playlist is empty", color = White, fontWeight = FontWeight.SemiBold)
        Text("Choose songs from your local library to get started.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
        TextButton(onClick = onAddSongs, contentPadding = PaddingValues(0.dp)) { Text("ADD SONGS", color = Ember, letterSpacing = 1.sp) }
    }
}

@Composable
private fun EmptyPlaylistMessage(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(top = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = White, fontWeight = FontWeight.SemiBold)
        Text(description, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
    }
}
