package dev.dreammusic.player

import org.junit.Assert.assertEquals
import org.junit.Test
import dev.dreammusic.player.data.mergeTrackIds

class PlaybackTextTest {
    @Test
    fun formatsPlaybackTimesAsMinutesAndSeconds() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("1:05", formatDuration(65_000))
        assertEquals("61:02", formatDuration(3_662_000))
    }

    @Test
    fun playlistAdditionKeepsExistingOrderAndSkipsDuplicates() {
        assertEquals(listOf(4L, 8L, 3L), mergeTrackIds(listOf(4L, 8L), listOf(8L, 3L, 3L)))
    }
}
