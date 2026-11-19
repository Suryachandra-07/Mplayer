# Mplayer — Dream Music Player

Mplayer is a custom online and offline music player, built for personal use and others with similar
interests. This Android-first foundation starts with local music and a cohesive black-and-warm UI.

The Obsidian Music checkout available alongside this
project contains its README, screenshots, release metadata, and Apache 2.0 licence, but no Android
application source or build files. This project is therefore an independent clean foundation; it
does not contain implementation code from InterTune or SimpMusic.

## Current milestone

- AMOLED-black Compose interface with warm artwork-inspired accents.
- Home, Search, and Library destinations.
- Local audio discovery through Android MediaStore after runtime permission.
- Media3 ExoPlayer in a `MediaSessionService`, with queue, play/pause, previous/next, seek, and
  system media-session controls.
- On-device playlists with both a dedicated multi-select **Add Songs** screen and a song-level
  **Add to playlist** picker.

Online catalogues, provider sync, downloads, lyrics, and recommendations are not implemented yet.
Playlist contents currently reference local songs on this device.

## Build

Requires JDK 17 or newer and Android SDK platform 36. From this directory:

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

Install on a connected device with `./gradlew installDebug`.
