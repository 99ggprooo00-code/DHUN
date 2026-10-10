# DHUN — data and privacy notes (DRAFT, not a published policy)

> **Status: DRAFT — requires maintainer decisions and legal review before publication.**
> This document describes behaviour verified from the repository source as of
> commit `6ebc45c` (release `v1.00.001`). It is not legal advice, it is not a
> complete compliance statement, and it is not linked from the app or the site.
> Where a fact could not be verified it says so.

## 1. Who runs what

DHUN is free software (GPL-3.0). The repository contains **no DHUN-operated server, account system, or telemetry endpoint**: the app talks directly to the third-party services listed in section 3. No DHUN contact address or legal entity is named here, because none has been provided. Maintainer contact details: **[to be provided by the maintainer]**.

## 2. Data stored only on your device

Nothing in this section leaves the device by DHUN's own design. The app stores, in app-private storage (Android) or the per-user install folder (Windows):

| Data | Where | Source |
|---|---|---|
| Liked songs, playlists, playlist entries | local SQLite database | `shared/src/commonMain/sqldelight/.../Favorite.sq`, `Playlist.sq` |
| Listening history | local SQLite database | `History.sq` |
| Recent searches | local SQLite database | `RecentSearch.sq` |
| Track metadata seen while browsing | local SQLite database | `Track.sq` |
| Queue and now-playing state | local SQLite database | `NowPlaying.sq` |
| Lyrics cache | local SQLite database | `LyricsCache.sq` |
| Settings (appearance, playback, cache size, country code) | local settings table | `Settings.sq`, `SettingsKeys.kt` |
| Downloaded audio and artwork you saved for offline play | Android: `filesDir/downloads`; Windows: `<install>\userdata\cache\audio` | `AndroidDownloadStorage.kt`, `DhunUserDirs.kt` |
| Playback audio segment cache (bounded) | Android: app cache directory | `DhunAudioSegmentCache.kt` |
| Windows startup log | `<install>\userdata\dhun-startup.log`, or `%TEMP%` if that is unavailable | `app-desktop/.../Main.kt` |

**Not verified:** the exact contents of the Windows startup log and the Android in-app "Playback details" text were not audited line by line for personal data. Review them before sharing a log.

## 3. Data sent to third parties when you use the app

Every item below is sent by the app to a third party. DHUN does not control how those parties process it.

| Recipient | Destination (from source) | What the app sends | When |
|---|---|---|---|
| YouTube / Google | `music.youtube.com`, `www.youtube.com` (InnerTube API) | Search terms, track/album/artist/playlist IDs, the InnerTube client context (client name and version, country code `gl` from settings), and a visitor identifier that YouTube returns and the app then reuses | Home, search, browsing, playback resolution |
| YouTube / Google | `googlevideo.com` stream hosts (URLs returned by YouTube) | Ordinary media requests, including your IP address | Playing or downloading a track |
| YouTube / Google | `i.ytimg.com`, `lh3.googleusercontent.com` | Requests for artwork, including your IP address | Showing artwork |
| LRCLIB (`lrclib.net`) | `https://lrclib.net/api/get` | Track title, artist, album and duration to fetch synced lyrics | Each time a track loads (`PlayerViewModel.loadLyrics`). There is **no working lyrics on/off control**: the `lyrics_enabled` key has no reader in the source. |

Also: every network request carries your IP address and a user-agent string, as with any internet request.

**Not sent:** the app does not send a DHUN account identifier, cookies, or sign-in tokens, and the HTTP clients in the source do not install a cookie plugin (`HttpCookies`). The project's extraction design uses no cookies or PO tokens (`OwnClientStreamResolver.kt`). **Not verified:** the exact headers the OkHttp engine sends on Android, and the exact contents of every response YouTube returns.

**Optional desktop fallback:** if you install `yt-dlp` yourself and DHUN uses it, that tool contacts YouTube under its own behaviour. Its configuration files are ignored by DHUN (per the release documentation); this was not verified end to end.

**Developer-only path:** the internal design catalogue screen can load sample images from `picsum.photos`. It is not in the nav bar, but it can be reached through restored state. This is a known, minor exception to the list above.

## 4. Analytics, advertising, crash reporting

A targeted search of the Gradle build files and Kotlin sources found **no analytics, advertising, or crash-reporting SDK** (for example Firebase, Crashlytics, Sentry). This is a source-level search only. The full resolved dependency tree was not inspected, because the Gradle build could not be run in the audit sandbox. Confirm before publication.

The public website (GitHub Pages) has no client-side script: the only `<script>` elements are JSON-LD data blocks. GitHub, as the host, processes requests under its own policies. The `/app/` web preview is a separate static page with its own notices; see its README.

## 5. Permissions

Android (`app-android/src/main/AndroidManifest.xml`): `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK`, and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (requests the system battery-exemption dialog; it does not grant the exemption). The manifest declares no storage, location, contacts, camera, microphone, or account permission, and sets `allowBackup="false"`.

Windows: no declared permission model. The per-user installer writes under `%LOCALAPPDATA%\DHUN`.

## 6. Retention, export and deletion

- **Android:** uninstalling the app removes its app-private database, downloads and caches. Clearing downloads in-app is implemented (`DownloadManager.clearAll`); clearing the lyrics cache is implemented (`LyricsRepository.clearCache`).
- **Windows:** the data lives in `<install>\userdata`, so an uninstall removes it. Upgrades are designed to preserve it; that behaviour is **not yet proven** on a user machine (see `docs/verification/` and the release notes).
- **Export:** no general data-export feature was found in a targeted search. Not verified exhaustively.
- **Account deletion:** not applicable, because there is no DHUN account.

## 7. Open questions for the maintainer and legal review

1. Name the data controller or state that the project is a personal, non-commercial open-source effort, and provide a contact channel.
2. Decide the lyrics policy. LRCLIB receives track metadata on every track load, and the `lyrics_enabled` setting does not gate it. Either wire the setting to the lookup or disclose the behaviour honestly.
3. Decide whether the desktop yt-dlp fallback needs a disclosure of its own.
4. Confirm the jurisdictions the project intends to serve, so that the correct notices can be drafted by a lawyer. **[maintainer decision]**
5. Decide whether the website needs a cookie or privacy notice. Today it sets no cookies, but GitHub Pages is a third-party host.
6. Have a qualified lawyer review this file, the YouTube/Google terms item in `TERMS-DRAFT.md`, and any GDPR/CCPA-style obligations that might apply.
