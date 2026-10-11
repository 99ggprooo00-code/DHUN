# Screenshot Provenance & Metadata Verification (`2026-10-11`)

## 1. Staging Source

- **Staging repository:** `https://github.com/99ggprooo00-code/dhun-screenshot-staging`
- **Directory:** `dhun-screenshot/`
- **Raw file count:** 18 files (9 Android portrait JPEGs + 9 Windows desktop PNGs)
- **App version:** DHUN `v1.00.001` (`versionName = "1.00.001"` in `app-android/build.gradle.kts`, `packageVersion = "1.0.0"` in `app-desktop/build.gradle.kts`)
- **Capture date:** `2026-10-10` (`20:09–20:12` local time on Windows; `20:51–20:54` local time on Android)
- **Devices:** Maintainer Android phone (`1080×2278` to `1080×2304` portrait, status/navigation bars cropped) and Windows desktop (`1050×1199` window)
- **Content-safety verification:** All third-party album artwork thumbnails and lyrics lines in the captures were pre-blurred by the maintainer prior to staging.

## 2. Shipped WebP Screenshots (`website/src/assets/screenshots/`)

All 10 shipped screenshots were converted from the staging originals via:

```bash
convert <input> -strip -define webp:method=6 -quality 82 <output.webp>
```

Every output file is well below `ASSET_BUDGET = 150 KB` (`153,600 B`) in `scripts/website_quality.py:43` (largest is `76,116 B` / `74.3 KB`, total across all 10 files is `479,042 B` / `467.8 KB`):

| Backlog ID | Shipped File (`website/src/assets/screenshots/`) | Staging Source (`dhun-screenshot/`) | Platform / Device | App Version | Capture Date | Dimensions | Raw Bytes | WebP Bytes | Screen & Live `/app/` Deep Link |
|---|---|---|---|---|---|---|---:|---:|---|
| `mock-home-phone` | `android-home-portrait.webp` | `IMG_20261010_205129.jpg` | Android phone | `v1.00.001` | `2026-10-10` | `1080×2288` | 233,460 B | 76,116 B (74.3 KB) | Home tab (`Good afternoon`, mood chips, Recommended songs, Listen again, mini-player, bottom nav) → `/app/` |
| `mock-player-phone` | `android-full-player.webp` | `IMG_20261010_205204.jpg` | Android phone | `v1.00.001` | `2026-10-10` | `1080×2278` | 141,275 B | 31,888 B (31.1 KB) | Full player sheet (`Dhun - Rockheads`, blurred artwork, `0:00 / 4:18` seek bar, transport controls, action row) → `/app/#/player` |
| `mock-lyrics` | `android-player-lyrics.webp` | `IMG_20261010_205218.jpg` | Android phone | `v1.00.001` | `2026-10-10` | `1080×2288` | 164,296 B | 41,210 B (40.2 KB) | Full player with synced Devanagari lyrics overlay (`CC` button active) → `/app/#/player/lyrics` |
| `mock-queue-phone` | `android-player-queue.webp` | `IMG_20261010_205232.jpg` | Android phone | `v1.00.001` | `2026-10-10` | `1080×2282` | 189,893 B | 53,880 B (52.6 KB) | Full player with `Up next` (`1 of 24`, `Drag to reorder`) and `Related` tabs → `/app/#/player/queue` |
| `mock-search-phone` | `android-search.webp` | `IMG_20261010_205246.jpg` | Android phone | `v1.00.001` | `2026-10-10` | `1080×2283` | 154,567 B | 32,462 B (31.7 KB) | Search tab with query field and `All` / `Songs` / `Artists` / `Albums` / `Playlists` / `Videos` filter chips → `/app/#/search` |
| `mock-playlists-phone` | `android-library-playlists.webp` | `IMG_20261010_205302.jpg` | Android phone | `v1.00.001` | `2026-10-10` | `1080×2288` | 183,665 B | 46,716 B (45.6 KB) | Library → Playlists (`Liked Songs` auto-folder, custom playlists, `+ New playlist`) → `/app/#/library/playlists` |
| `mock-downloads-phone` | `android-library-downloads.webp` | `IMG_20261010_205315.jpg` | Android phone | `v1.00.001` | `2026-10-10` | `1080×2286` | 181,152 B | 45,818 B (44.7 KB) | Library → Downloads (`1 item • 4.2 MB`, `Clear all`, storage usage bar, downloaded track row) → `/app/#/library/downloads` |
| `mock-history-phone` | `android-library-history.webp` | `IMG_20261010_205330.jpg` | Android phone | `v1.00.001` | `2026-10-10` | `1080×2300` | 157,936 B | 35,418 B (34.6 KB) | Library → History (`1 play`, `Clear all`, `Today` chronological play row) → `/app/#/library/history` |
| `mock-settings-phone` | `android-equalizer.webp` | `IMG_20261010_205400.jpg` | Android phone | `v1.00.001` | `2026-10-10` | `1080×2304` | 238,173 B | 70,702 B (69.0 KB) | Settings & 5-band Equalizer (Dark/Light, Violet accent, cache cap, `About & Legal`, presets, preamp `+12 dB`, 5 bands) → `/app/#/settings` |
| `mock-desktop-window` | `windows-desktop-window.webp` | `Screenshot 2026-10-10 200909.png` | Windows desktop | `v1.00.001` | `2026-10-10` | `1050×1199` | 724,653 B | 44,832 B (43.8 KB) | Windows desktop window (native titlebar, Home view, 4-column card grids, bottom mini-player with volume slider) → `/app/#/home` |

## 3. Additional Staging Captures Inventoried (Not Shipped to Avoid Unreferenced Assets)

The remaining 8 Windows staging PNGs (`1050×1199`, captured `2026-10-10` on Windows desktop running DHUN `v1.00.001`) depict the desktop equivalents of the same screens shown in the Android series above:

- `Screenshot 2026-10-10 200950.png` (595,731 B raw → 21,268 B WebP): Windows Full Player (`Dhun - Rockheads`)
- `Screenshot 2026-10-10 201009.png` (545,748 B raw → 23,452 B WebP): Windows Full Player with synced Devanagari lyrics overlay
- `Screenshot 2026-10-10 201018.png` (507,576 B raw → 24,978 B WebP): Windows Full Player with `Up next` queue sheet
- `Screenshot 2026-10-10 201044.png` (337,020 B raw → 17,794 B WebP): Windows Search tab
- `Screenshot 2026-10-10 201136.png` (348,449 B raw → 22,814 B WebP): Windows Library → Playlists
- `Screenshot 2026-10-10 201139.png` (363,755 B raw → 23,932 B WebP): Windows Library → History
- `Screenshot 2026-10-10 201156.png` (345,798 B raw → 19,282 B WebP): Windows Library → Downloads
- `Screenshot 2026-10-10 201205.png` (424,496 B raw → 38,772 B WebP): Windows Settings & 10-band Equalizer

## 4. Metadata Strip Verification

Each shipped `.webp` file was inspected via both `identify -verbose` and binary RIFF chunk parsing:

- Every file contains **only** a single `VP8 ` bitstream chunk inside the `RIFF....WEBP` container.
- Zero `EXIF`, `XMP `, `ICCP`, or comment chunks exist in any shipped image (`profiles=[]`).
