# Kultr for Android

A native Android client for [Navidrome](https://www.navidrome.org/) and other
Subsonic-compatible servers, and a music player for the files on your phone,
no server needed. It's the Android counterpart of the
[Kultr web client](https://github.com/evropiani/Kultr) and of
[Kultr for iOS](https://github.com/evropiani/Kultr_iOS). It keeps their
behaviour and settings (a settings file exported from one opens in the
others) and runs as a proper Android music app: background playback, a media
notification, lock-screen and Bluetooth controls, Android Auto, Cast, and
downloads for offline listening.

On first start a short welcome asks where your music is (a Navidrome server
or folders on this phone) and shows around the app.

## Features

- **Cream, with glass where things float.** Cream pages, warm black text and
  one orange for whatever you can tap; warm charcoal in dark mode, and Night
  mode turns it pure black for OLED screens. The tab bar, mini player and
  download progress are liquid glass floating over the page (Android 12 and
  later; on Android 13 the edges bend the light like a lens): the selected tab
  is a lens you can slide along the bar, and the search button grows into the
  search field. Four app icons to choose from.
- **Stations.** Endless mixes from your own library: Warm Up, Night Drive,
  Peak Hour, Deep Focus, Wind Down, Unplayed gems, Favourites and one per
  decade, or your own rules (tempo, energy, key, genres, years, your tags,
  rating, plays, when it was added). Turn the dial to choose; while one plays,
  turning it mixes into the next.
- **Energy planner.** Draw how the next hour should feel, calm to peak, and
  Kultr builds a set that follows the line, with tempos and keys that mix.
- **Search does things.** "play Night Drive", "shuffle" and an artist, "sleep
  in 30 minutes", "next", "mix now", "plan an hour", "open settings".
- **Crates, notes and tags.** Folders of albums, artists and tracks, filled by
  hand or by rules and kept as a playlist if you like; your own tags and a
  note on every album and artist.
- **Kultr Studio.** Five ready-made styles, the player's parts in the order
  you want, an info line you write yourself, and your look shared as a link
  or a file (the same links as Kultr for iOS).
- **Home** has widgets (now playing, this week's listening, the next mix, a
  station, a crate, a shortcut) above shelves you choose and reorder, among
  them Stations and Rediscover (loved albums you haven't played for a year).
- **Music on this phone.** Choose the folders your music is in (Music, a
  memory card, anything the system's folder picker offers) and Kultr plays
  your files without a server or an account. It reads their tags and covers
  (a `cover.jpg` in the folder or the picture inside the file), groups them
  into albums, artists and genres, and finds new and changed files on its
  own. Favourites, ratings, play counts, playlists, InjeKt, the widget and
  Android Auto all work; folders and scanning are in Settings → Music on this
  phone.
- **Library mirror.** A server's whole library is synced into a local
  database, so browsing and search are instant and work offline. Later syncs
  fetch only what changed; they can run at start-up and periodically in the
  background. The Library is a list: playlists, crates, artists, albums,
  songs, genres, tags, favourites, downloads and radio, each on its own page.
- **Two-deck playback.** Every track plays on one of two players, so Kultr can
  crossfade (with a choice of curves), play gapless albums seamlessly, or cut.
- **InjeKt transitions.** Tracks are analysed on the phone (tempo, beat grid,
  key, energy and intro/outro structure), and each transition is planned as
  soon as a track starts: tempo-matched blends that land on the downbeat, a
  bass swap and a filter sweep.
- **Karousel.** Tap shuffle twice: when the queue runs out, music like what
  was playing keeps coming — your server's similar songs, songs by similar
  artists and by the ones you're hearing (with Last.fm set up on Navidrome),
  and your own favourites, which also keep it going offline and for the music
  on your phone. With InjeKt on, the songs that mix best come first. Its songs
  show under their own heading in Up next and leave the queue when you turn it
  off.
- **Offline.** Download albums, playlists, favourites or the whole library, at
  a bitrate of your choosing, on Wi-Fi only if you like. A Downloads page shows
  what is coming down, what is queued or failed, and what is already on the
  phone. Downloads play first, before the network is tried, and a stream cache
  keeps recent tracks too.
- **Listening on your server.** Every play is sent to Navidrome with the time
  it happened (offline plays go later, exactly once), and plays from your
  other devices come back, so "Jump back in", "Played the most" and the
  Listening page are the same everywhere. A monthly recap shows how long you
  listened, your top tracks, albums and artists, and your longest day.
- **Cast** to a Chromecast or speaker group from the player.
- **Home screen widget.** Centred artwork, title and controls that scale as you
  resize it, with the artwork optional and the background opacity you choose.
  Media buttons, KWGT and other controllers work too.
- **Drag and drop** tracks, albums, artists and playlists onto Play next, Add to
  queue, Favourite, Sync offline or Delete downloads.
- **Several servers.** Sign in to more than one server, name them as you like,
  and switch between them and the music on the phone; change a server's
  password from its menu. Each has its own library, downloads and history.
  Passwords are sealed with a key held in the Android Keystore.
- **Audio:** ten-band equaliser with presets, ReplayGain (track or album),
  per-network streaming bitrate, sleep timer (after minutes or at the end of
  the track, winding down with calmer music in its last 15 minutes). Hold
  Next to mix into the next track instead of skipping.
- **Updates in the app.** Kultr checks for new releases (at most twice a day,
  or from Settings → About → Version → Check for updates) and shows a banner
  you can dismiss. The update page has the release notes and an Update button
  that downloads the new APK, checks its SHA-256, and hands it to Android's
  installer. After an update, What's new shows every change since the version
  you last opened, and Settings → About → Update log has them all.
- **In English and German.** Kultr follows the phone's language, or the one
  chosen for it in Android's settings (Settings → Apps → Kultr → Language, from
  Android 13); the command bar understands German too ("spiele Nachtfahrt",
  "schlaf in 30 Minuten").
- **Also:** synced and plain lyrics, ratings and favourites, playlist editing,
  internet radio, saving the session as a playlist, a glass lens with the
  beats when you drag the progress bar, a player you pull down to close, and
  settings backup and restore (stations, crates, notes and widgets included).

## Getting it

Download `Kultr-<version>.apk` from the
[latest release](https://github.com/evropiani/Kultr_Android/releases/latest)
and open it on a phone running Android 8.0 (API 26) or later. Releases are
signed with the Kultr release key, so each one installs over the last; its
certificate's SHA-256 fingerprint is
`3C:26:16:4A:FD:6F:C1:30:F5:7D:A5:2B:4F:04:AB:2B:E8:3C:89:25:50:89:6F:C9:43:57:D2:E0:B8:51:6F:E0`.

From 1.5.0 on, Kultr tells you itself when a new version is out and installs
it from inside the app. Android asks once to allow Kultr to install apps.

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
| `core` | Plain Kotlin, no Android: the Subsonic API client, the audio analysis (FFT, tempo, key, structure), the InjeKt transition planner and energy planner, the two-deck playback engine, Karousel and station rules, the library and listening sync, the catalogue that groups local files into albums and artists, the command bar's words, the monthly recap, version comparison and release notes, and the settings model (Studio styles, crates, notes, widgets) with its import/export. Unit-tested on the JVM. |
| `app` | The Android app: its German (`assets/l10n/de.json`, the same file Kultr for iOS translates from, plus Android's own lines), a Room database per library (each server, and the music on the phone), the folder scanner, WorkManager jobs for sync, downloads and analysis, the update checker, a Media3 `MediaLibraryService` whose player drives two ExoPlayer decks through the core engine (with a custom audio processor for fades, EQ and filters), the home screen widget, and the Jetpack Compose interface with its liquid glass. |

Libraries: Jetpack Compose with Material 3, Media3 (ExoPlayer, session and Cast),
Room, WorkManager, Navigation, Coil, OkHttp and kotlinx.serialization.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
