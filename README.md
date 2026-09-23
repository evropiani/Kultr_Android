# Kultr for Android

A native Android client for [Navidrome](https://www.navidrome.org/) and other
Subsonic-compatible servers — the Android counterpart of the
[Kultr web client](https://github.com/evropiani/Kultr). It keeps the web
client's behaviour and settings (a settings file exported from one opens in
the other) while running as a proper Android music app: background playback,
a media notification, lock-screen and Bluetooth controls, Android Auto, and
downloads for offline listening.

## Features

- **Library mirror.** The whole library is synced into a local database, so
  browsing and search are instant and work offline. Later syncs fetch only
  what changed; they can run at start-up and periodically in the background.
- **Two-deck playback.** Every track plays on one of two players, so Kultr can
  crossfade (with a choice of curves), play gapless albums seamlessly, or cut.
- **InjeKt transitions.** Tracks are analysed on the phone (tempo, beat grid,
  key, energy and intro/outro structure) and transitions are planned from
  that: tempo-matched blends that land on the downbeat, a bass swap, a
  filter sweep, and key-aware ordering for an endless automatic queue.
- **Offline.** Download albums, playlists, favourites or the whole library, at
  a bitrate of your choosing, on Wi-Fi only if you like. A Downloads page shows
  what is coming down, what is queued or failed, and what is already on the
  phone. Downloads play first, before the network is tried, and a stream cache
  keeps recent tracks too.
- **Cast** to a Chromecast or speaker group from the player.
- **Home screen widget** with artwork and playback controls, with the background
  opacity you choose; media buttons, KWGT and other controllers work too.
- **Drag and drop** tracks, albums, artists and playlists onto Play next, Add to
  queue, Favourite, Sync offline or Delete downloads.
- **Several servers.** Sign in to more than one server and switch between them;
  each has its own library, downloads and history. Passwords are sealed with a
  key held in the Android Keystore.
- **Home shelves** you choose and reorder: jump back in, recently added, most
  played, albums at random, favourites, playlists on repeat, internet radio
  and more.
- **Audio:** ten-band equaliser with presets, ReplayGain (track or album),
  per-network streaming bitrate, sleep timer (after minutes or at the end of
  the track).
- **Also:** synced and plain lyrics, ratings and favourites, playlist editing,
  scrobbling with an offline queue, listening stats, internet radio, a
  now-playing screen tinted by the artwork, and settings backup and restore.

## Getting it

Download `Kultr-<version>.apk` from the
[latest release](https://github.com/evropiani/Kultr_Android/releases/latest)
and open it on a phone running Android 8.0 (API 26) or later. Releases are
signed with the Kultr release key, so each one installs over the last; its
certificate's SHA-256 fingerprint is
`3C:26:16:4A:FD:6F:C1:30:F5:7D:A5:2B:4F:04:AB:2B:E8:3C:89:25:50:89:6F:C9:43:57:D2:E0:B8:51:6F:E0`.

Every push to `main` is also built by GitHub Actions, with a debug APK attached
to the run as the `kultr-debug-apk` artifact. Debug builds are signed with a
different key, so uninstall a release before installing one (and back).

## Building

Requirements: JDK 17 or newer and the Android SDK (compile SDK 37). Then:

```sh
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :core:test :app:testDebugUnitTest
./gradlew :app:assembleRelease        # R8-shrunk; sign it with your own key
```

Open the project in a current Android Studio to run it on a device.

### Releasing

Bump `versionCode` and `versionName` in `app/build.gradle.kts`, add notes as
`.github/release-notes/v<version>.md`, then either push a matching tag
(`git tag v1.0.1 && git push origin v1.0.1`) or run the **Release** workflow
from the Actions tab on `main` with the tag `v1.0.1`, which creates it.

The release workflow builds and signs the APK and publishes it as a GitHub
release. The signing key is kept in the repository encrypted
(`signing/kultr-release.p12.enc`); the workflow unlocks it with the
`KULTR_SIGNING_PASSPHRASE` repository secret. Keep that passphrase somewhere
safe: without it, no update can be signed to install over existing copies.

## How it is put together

| Module | What it holds |
| --- | --- |
| `core` | Plain Kotlin, no Android: the Subsonic API client, the audio analysis (FFT, tempo, key, structure), the InjeKt transition planner, the two-deck playback engine, the library sync, and the settings model with its import/export. Unit-tested on the JVM. |
| `app` | The Android app: a Room database per server, WorkManager jobs for sync, downloads and analysis, a Media3 `MediaLibraryService` whose player drives two ExoPlayer decks through the core engine (with a custom audio processor for fades, EQ and filters), and the Jetpack Compose interface. |

Libraries: Jetpack Compose with Material 3, Media3 (ExoPlayer and session),
Room, WorkManager, Navigation, Coil, OkHttp and kotlinx.serialization.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
