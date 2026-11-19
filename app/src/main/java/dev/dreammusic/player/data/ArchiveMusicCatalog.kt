package dev.dreammusic.player.data

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ArchiveTrack(
    val identifier: String,
    val title: String,
    val artist: String,
)

/** Search and stream public audio files exposed by the Internet Archive APIs. */
class ArchiveMusicCatalog {
    fun search(query: String): List<ArchiveTrack> {
        val clean = query.trim().take(100)
        if (clean.isEmpty()) return emptyList()
        val escaped = clean.replace("\\", "\\\\").replace("\"", "\\\"")
        val lucene = "mediatype:audio AND (title:\"$escaped\" OR creator:\"$escaped\")"
        val url = "https://archive.org/advancedsearch.php?q=${encode(lucene)}&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=creator&rows=24&page=1&output=json"
        val response = JSONObject(get(url))
        val docs = response.getJSONObject("response").getJSONArray("docs")
        return buildList {
            for (index in 0 until docs.length()) {
                val doc = docs.getJSONObject(index)
                val identifier = doc.optString("identifier").takeIf(String::isNotBlank) ?: continue
                add(ArchiveTrack(
                    identifier = identifier,
                    title = doc.optString("title").ifBlank { "Untitled recording" },
                    artist = when (val creator = doc.opt("creator")) {
                        is org.json.JSONArray -> (0 until creator.length()).map { creator.optString(it) }.joinToString(", ")
                        is String -> creator
                        else -> "Internet Archive contributor"
                    }.ifBlank { "Internet Archive contributor" },
                ))
            }
        }
    }

    fun resolve(track: ArchiveTrack): MediaItem {
        val metadata = JSONObject(get("https://archive.org/metadata/${encodePath(track.identifier)}"))
        val files = metadata.optJSONArray("files") ?: error("No audio files were listed for this recording")
        val candidate = (0 until files.length()).map { files.getJSONObject(it) }
            .filter { file ->
                val name = file.optString("name")
                val format = file.optString("format")
                name.endsWith(".mp3", ignoreCase = true) &&
                    !name.startsWith("__") && !name.endsWith("_files.xml") &&
                    !file.has("private") && (format.contains("mp3", ignoreCase = true) || format.isBlank())
            }
            .sortedBy { it.optString("name").lowercase() }
            .firstOrNull()
            ?: error("This recording has no playable MP3 file")
        val fileName = candidate.getString("name")
        val displayedTitle = fileName.substringAfterLast('/').removeSuffix(".mp3").replace(Regex("^\\d+[ ._-]*"), "").ifBlank { track.title }
        val streamUrl = "https://archive.org/download/${encodePath(track.identifier)}/${encodePath(fileName)}"
        return MediaItem.Builder()
            .setMediaId("archive:${track.identifier}:$fileName")
            .setUri(streamUrl)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(displayedTitle).setArtist(track.artist).build())
            .build()
    }

    private fun get(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 18_000
            setRequestProperty("User-Agent", "DreamMusicPlayer/0.3 (Android music discovery)")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) error("Catalog request failed ($status)")
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun encodePath(value: String) = value.split('/').joinToString("/") { Uri.encode(it) }
}
