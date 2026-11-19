package dev.dreammusic.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Small on-device playlist store; playlist membership refers to MediaStore track IDs. */
class LocalPlaylistStore(context: Context) {
    private val preferences = context.getSharedPreferences("dream_music_playlists", Context.MODE_PRIVATE)

    fun all(): List<LocalPlaylist> = preferences.all.mapNotNull { (id, value) ->
        val json = (value as? String)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@mapNotNull null
        val ids = json.optJSONArray("trackIds") ?: JSONArray()
        LocalPlaylist(
            id = id,
            name = json.optString("name", "Untitled playlist"),
            trackIds = List(ids.length()) { index -> ids.optLong(index) },
        )
    }.sortedBy { it.name.lowercase() }

    fun create(name: String): LocalPlaylist? {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return null
        val playlist = LocalPlaylist(UUID.randomUUID().toString(), cleanName, emptyList())
        write(playlist)
        return playlist
    }

    fun rename(id: String, name: String): LocalPlaylist? {
        val cleanName = name.trim()
        val existing = all().firstOrNull { it.id == id } ?: return null
        if (cleanName.isEmpty()) return null
        return existing.copy(name = cleanName).also(::write)
    }

    fun addSongs(id: String, trackIds: List<Long>): LocalPlaylist? {
        val existing = all().firstOrNull { it.id == id } ?: return null
        return existing.copy(trackIds = mergeTrackIds(existing.trackIds, trackIds)).also(::write)
    }

    fun remove(id: String) {
        preferences.edit().remove(id).apply()
    }

    private fun write(playlist: LocalPlaylist) {
        val ids = JSONArray().apply { playlist.trackIds.forEach { put(it) } }
        val json = JSONObject().put("name", playlist.name).put("trackIds", ids).toString()
        preferences.edit().putString(playlist.id, json).apply()
    }
}
