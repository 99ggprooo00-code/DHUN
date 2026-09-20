# S1 residential / device playback evidence — step-by-step guide

Filed 2026-09-20 (session `arena/01a0bd98-dhun`). This is the procedure the
user follows to supply the one evidence item Stage S1 cannot produce from
CI: an honest playback result from a **residential (non-datacenter)
network**. Supplied to the user in chat the same day; results come back via
chat (or an issue #14 comment — the agent token cannot write issue
comments) and are recorded in `.ai/ROADMAP.md` + `KNOWN_LIMITATIONS.md` +
`docs/verification/14-release.md` by the next session.

## Why this evidence is needed

GitHub runners use datacenter IPs that YouTube bot-gates, so the drill
verdict there (`ENVIRONMENT_BLOCKED`) can never prove real-user playback.
S1 closes when we have **one honest result from a home network** — either
audible playback (S1 green path) or the exact failure text (which becomes
contingency-trigger T1 evidence — equally valuable, not a wasted run).

## Build under test

The rolling `test` release is **replaced on every push to `main`**, so the page
always shows the newest build — use whatever is there today. You do not need to
match it against this guide: the page's publish timestamp being recent *is* the
proof you are on the current build. (For reference, this paragraph was written
when `test` was published 2026-09-20T15:19:07Z from `7304abb` — that pair is
already superseded by any later merge, and downloading the newest release is
still correct.)

- Release page: `https://github.com/99ggprooo00-code/DHUN/releases/tag/test`
- Android: `dhun-test.apk` (last measured **17,948,508 bytes**) · Windows:
  `dhun-test.msi` (last measured **112,861,184 bytes**); each has a `.sha256`
  sidecar next to it. Sizes change only when a build actually changes —
  docs-only merges leave them identical.

**How to say which build you tested:** the release page does *not* print a
commit SHA. So report the **date+time shown beside the `test` release title**,
the **file size your device downloaded**, and — if handy — the **`.sha256`
sidecar contents** (gold standard). The agent maps that back to a commit via
the release's `target_commitish`.

Do **Path A** (phone) or **Path B** (PC) — or both. Path C is an advanced
alternative that also satisfies S1.

## Path A — Android phone (~15 min)

1. On the phone (home WiFi or mobile data — **no VPN**, note which one),
   open the release page and download `dhun-test.apk`.
2. Tap the downloaded file → allow **"Install unknown apps"** for the
   browser when prompted → Install.
3. Open DHUN → **Search** → any well-known song. Wait for results (this
   confirms metadata works on the network).
4. Tap a track → watch the states: `Resolving` → `Buffering` → playing
   with **advancing position + audible sound**. Confirm sound from the
   phone speaker (disconnect Bluetooth if unsure).
5. Let it play **at least 2 minutes** (a full track is ideal). Note:
   does the position advance smoothly? Any `Reconnecting…` or error?
6. **Lock the phone mid-play** for 30 seconds: does audio continue?
   (record yes/no — valuable background-playback signal).
7. On failure: tap the mini-player (its action is labelled **Show playback
   details** when an error is active), or open the full player and use
   **Details** in the red error band. The **Playback details** dialog holds the
   full text and it is selectable — long-press → Copy.
8. Send back the evidence bundle below.

## Path B — Windows PC (~15 min)

1. **Install VLC first** (64-bit) if not present — DHUN Desktop needs
   system libVLC for audio.
2. Download `dhun-test.msi` from the release page (same home network,
   no VPN).
3. Run the MSI. SmartScreen warns about an unknown publisher → **More
   info → Run anyway** (expected: the installer is unsigned). Per-user,
   no admin/UAC prompt.
4. On "Another version of this product is already installed": uninstall
   the old DHUN via Settings → Apps first (note: uninstall removes
   DHUN's data), then re-run.
5. Launch DHUN → Search → play a track → same observations as Path A
   steps 4–5 (audible? position advancing? how long?).
6. Optional but helpful: in a terminal run `yt-dlp --version` (or
   `python -m yt_dlp --version`) → report the version or "not
   installed". Desktop has an optional yt-dlp fallback; knowing its
   presence helps interpret results.
7. On failure: open **Details** in the player, copy the exact
   diagnostic text.

## Path C — home-computer probe, advanced alternative (~20 min)

Needs git + JDK 17 + Python 3 on a home-network computer:

1. `git clone https://github.com/99ggprooo00-code/DHUN.git` →
   `git checkout main`
2. `pip install yt-dlp` (same engine version family the drill uses)
3. Run `./gradlew :tools:playback-probe:run --no-daemon` (Linux/macOS)
   or `gradlew.bat :tools:playback-probe:run --no-daemon` (Windows)
4. Paste every line starting with `PROBE|` / `WATCH|` / `YTAPI|` plus
   the final verdict line.

## Evidence bundle to send back (copy-paste template)

```
Build tested: (the date+time shown beside the `test` release title, and the
  file size your device downloaded — e.g. "2026-09-20 13:34 UTC, APK
  17,948,508 bytes"; the page shows no commit SHA, and that pairing pins it)
sha256 (optional, best): (contents of the matching `.sha256` sidecar)
File tested: (APK / MSI)
Device/OS: (e.g. Galaxy A54 / Android 14; Win11 + VLC 3.0.x)
Network: (home WiFi / mobile data; ISP if known; VPN off confirmed?)
Track tested: (exact title — artist as shown in the app)
Result: AUDIBLE YES/NO; position advanced YES/NO; played ~X min
Background (phone): audio continued with screen locked YES/NO
Errors (exact text from the "Playback details" dialog, if any):
Screenshots/recordings attached: (yes/no)
yt-dlp (PC): (version or "not installed")
```

## Privacy and failure notes

- No logins or cookies are needed — DHUN is guest-first. Never send any.
- Don't paste long `googlevideo.com` URLs if encountered (they expire
  and are noise); the Details text alone is enough.
- **A failed run is still a successful evidence run** — report exactly
  how far it got (`Resolving` stuck? `Buffering` → `Reconnecting`?
  instant typed error?) and whether Search/Home content loaded. That
  distinction separates "needs ADR-007" from "needs a wave bump".

## Troubleshooting quick table

| Symptom | Action |
|---|---|
| Android install blocked | Allow "Install unknown apps" for the browser |
| Windows SmartScreen | More info → Run anyway (unsigned, expected) |
| "Another version installed" | Uninstall old DHUN via Settings → Apps first |
| No sound but position moves | Check volume/Bluetooth output; report exactly |
| Works on mobile data but not home WiFi (or vice versa) | Report both — that is signal, not noise |
