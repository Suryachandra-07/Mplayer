# Mplayer — Dream Music Player

Mplayer is a custom online and offline music player, built for personal use and others with similar
interests. Online discovery is the primary direction; local playback and playlists are supplementary.

The Obsidian Music checkout available alongside this
project contains its README, screenshots, release metadata, and Apache 2.0 licence, but no Android
application source or build files. This project is therefore an independent clean foundation; it
does not contain implementation code from InterTune or SimpMusic.

## Current milestone

- AMOLED-black Compose interface with warm artwork-inspired accents.
- Home, online-first Discover, and Library destinations.
- Search the Internet Archive audio catalog and stream playable MP3 files through the Media3
  background player. Results are resolved against each item's file metadata; archive entries vary
  in rights and availability. For multi-track items, the first alphabetically named MP3 is played.
- Local audio discovery through Android MediaStore after runtime permission.
- Media3 ExoPlayer in a `MediaSessionService`, with queue, play/pause, previous/next, seek, and
  system media-session controls.
- On-device playlists with both a dedicated multi-select **Add Songs** screen and a song-level
  **Add to playlist** picker.

YouTube Music and other mainstream providers are not connected. Online results cannot yet be saved
to playlists or library. Provider sync, downloads, lyrics, and recommendations are also future work.
The Internet Archive catalog is an initial, documented source—not a substitute for YouTube Music's
mainstream catalog. Search and streaming require an internet connection.

## Build

Requires JDK 17 or newer and Android SDK platform 36. From this directory:

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Install on a connected device with `./gradlew installDebug`.
