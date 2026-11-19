package dev.dreammusic.player.data

data class LocalPlaylist(
    val id: String,
    val name: String,
    val trackIds: List<Long>,
)

/** Adds new tracks once while keeping the playlist's existing order. */
internal fun mergeTrackIds(existing: List<Long>, additions: List<Long>): List<Long> =
    (existing + additions).distinct()
