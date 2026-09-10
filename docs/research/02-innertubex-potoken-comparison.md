# Source comparison: vivi-music / InnerTubeX token path vs DHUN extraction

Research only. No extraction code is changed by this document, and nothing
here authorizes an identity-chain change — that needs ADR-007 to be accepted
first (AI rule 8 / ADR-001..006).

## Sources inspected (pinned)

| Source | Pin | How read |
|---|---|---|
| `vivizzz007/vivi-music`, `main` | `fcd8996` (2026-09-09) | `api.github.com` contents (sandbox blocks `raw.githubusercontent.com`) |
| `MetrolistGroup/innertubex` | `v0.2.6` (released 2026-08-27), JitPack `com.github.MetrolistGroup.innertubex:innertubex-android` | vivi `gradle/libs.versions.toml` + `app/build.gradle.kts` |
| `ZemerTeam/zemer-cipher` | remote config URL only (below) | referenced by vivi source, not vendored |
| DHUN | `main` @ `cd97464` | local tree |

Licences (all GPL-3.0, compatible with DHUN; attribution to `THIRD_PARTY.md`
required if anything is used): innertubex `GPL-3.0` (API-verified);
zemer-cipher `GPL-3.0` (API-verified); vivi-music GPL-3.0 with a proprietary
`musixmatch` exception (LICENSE-file verified, see
`.ai/ui-research-vivi-music.md` — nothing from that module was read or used).

**Correction to the earlier handoff framing:** InnerTubeX is *not* vivi's own
engine. It is an external dependency (`com.metrolist.innertubex`, by
MetrolistGroup). vivi-music's own code is the WebView PO-token minter, the
wiring (`InnerTubeXPlayer`), and the cipher-config source choice.

## 1. DHUN's current Android path (verified in-tree)

`app-android/.../di/AppModule.kt`: `InnerTubeClient` →
`OwnClientStreamResolver` → `OfflineFirstStreamResolver` →
`YouTubeMusicProvider` → `ResolveObservingMusicProvider` → `DhunStreamCache`.
No yt-dlp (ADR-001), no fallback resolver, no outer resolve budget.

`shared/.../extraction/OwnClientStreamResolver.kt`:

- 7 **tokenless** identities in 3 staged waves (ADR-003 Option C):
  `web_embedded + visionos` → `tv + tv_downgraded + tv_simply` →
  `mweb + web_remix`. First direct-audio win cancels its wave.
- Explicitly: *"Never signs URLs, never deciphers challenges, never uses
  cookies / PO tokens."* No ANDROID/IOS clients (*"GVS PO-token required"*).
- `parseStreamInfo` **fails** responses whose formats all lack direct URLs
  (`Parse("...ciphered/protected response")`) — DHUN cannot play ciphered
  responses at all today.

`shared/.../innertube/InnerTubeClient.kt`:

- `/player` bodies carry `context.client` (name/version/hl/gl) +
  `videoId/contentCheckOk/racyCheckOk` only. No `visitorData` persistence,
  no `serviceIntegrityDimensions`, no PO-token fields, no playback nonce.
- Per-request 12 s (alt, ×2 attempts) / 20 s (primary, ×3) timeouts;
  process-wide 429 gate (15 s default cooldown).

Failure mode on a bot-gated network: every wave returns
`AuthRequired("Sign in to confirm you're not a bot")` (issue #14), the
failure re-enters ExoPlayer retries + 403-recovery, and the UI sits in
`Buffering`/`Recovering` for minutes. Desktop is rescued by the yt-dlp
subprocess under a 45 s budget; Android has neither.

## 2. vivi-music's Android path (verified from source)

Single entry point: `app/.../utils/InnerTubeXPlayer.kt` (object, 384 lines).

```
InnerTubeXPlayer.playerResponseForPlayback()
  → InnerTubeExtractor.extract(videoId, hints, excludedClients, quality, nonce)
      ├─ TokenProvider (capabilities: WEB_BOTGUARD, usesWebView=true)
      │    └─ PoTokenGenerator.getWebClientPoToken(videoId, visitorData)
      │         └─ PoTokenWebView (headless Android WebView + po_token.html BotGuard client)
      ├─ YouTubeCipherService + RemotePlayerConfigStore
      │    └─ player config cached in SharedPreferences, remote source:
      │       ZemerTeam/zemer-cipher …/player_configs.json
      └─ ContentHints(allowHls=false, allowSabr=false)
```

What each layer does:

- **Token provider injection.** The extractor does not implement BotGuard;
  it asks the injected `TokenProvider` for a token bound to
  `(videoId, visitorData, cookie?)` and maps the result into both
  player-request and streaming-data token fields. The `cookie` parameter
  exists but vivi passes session identity only — **login is not what makes
  its anonymous playback work**.
- **`PoTokenGenerator`** (vivi's own): session-scoped caching (the streaming
  token is minted once per visitor session, the player token per video),
  proactive recreate on WebView death/expiry/session change, and a hard
  **`POTOKEN_TIMEOUT_MS = 8_000`** cap: *"Healthy cold-start … is ~2–5s in
  practice; 8s leaves slack … without making the user wait too long before
  the fallback chain (ANDROID_VR, etc.) takes over."* A missing/bad WebView
  returns `null` instead of throwing — tokenless fallback clients still run.
- **`PoTokenWebView`** (vivi's own): headless `WebView`, JS enabled,
  `blockNetworkLoads = true` (the WebView itself needs no internet — the
  BotGuard challenge is fetched over the app's OkHttp and injected), a
  `@JavascriptInterface` bridge, renderer-death handling that degrades to
  recreate rather than permanently disabling tokens. The 8.6 KB
  `assets/po_token.html` is the BotGuard client (BgUtils v3.2.0 patterns):
  load challenge → run VM → integrity token → create the minter **once**,
  reuse for all tokens.
- **Cipher service.** `YouTubeCipherService` + remotely-fetched player
  configs decipher `signatureCipher`/n-param responses, with
  `refreshAfterStreamRejection()` re-arming after a rejection. This closes
  the hole DHUN's `parseStreamInfo` fails open on.
- **Client hygiene.** Per-video WEB_REMIX failure cache (5-min TTL),
  runtime `excludedClients` set, `prewarm()` (extractor + token provider
  with a warmup video id), `generateClientPlaybackNonce()` per request, and
  SABR explicitly rejected (`check(stream.sabrBootstrap == null)`).

## 3. Gap table

| Capability | DHUN today | vivi / InnerTubeX |
|---|---|---|
| PO-token minting | none | on-device WebView BotGuard, 8 s cap, session-cached |
| `/player` attestation fields | none (client/version/hl/gl only) | player-request + streaming-data tokens, visitorData, playback nonce |
| Ciphered responses | hard `Parse` failure | deciphered via cipher service + remote player configs |
| Tokenless fallback when minting fails | n/a (only mode) | yes (`null` token → ANDROID_VR-class clients) |
| Per-client failure memory | none (retries everything every time) | 5-min WEB_REMIX failure cache + excluded set |
| Outer resolve budget (Android) | none | 8 s token cap inside a bounded extract |
| Maintenance absorption | DHUN pins identities by hand | innertubex lib + ZemerTeam remote configs |
| Login requirement | none, and none needed by this design | optional (personalization), not playback |

## 4. Integration seams if DHUN ever adopts this (design notes, not a plan)

- **Seam 1 — token provider interface.** Mirror the split: a narrow
  `PoTokenProvider(videoId, visitorData): PoToken?` interface in shared
  code, with the WebView minter as an Android `actual`/app-layer impl.
  `innertubex-android` is **Android-only**, so it cannot live in
  `shared/commonMain` — Android would gain an `androidMain` (or
  `app-android`) extraction path while Desktop keeps own-client + yt-dlp.
  That platform split is the biggest architectural cost and must be
  accepted explicitly.
- **Seam 2 — resolver ordering.** vivi's lesson is *token-first with
  tokenless fallback*, not token-only: keep the staged tokenless waves as
  the fallback behind a bounded token attempt, and never let minting hang
  the path (their 8 s cap exists because WebView processes get culled).
- **Seam 3 — cipher.** Any client set that returns ciphered responses needs
  either a cipher service (adopt or reimplement) or client selection that
  avoids ciphered responses. DHUN currently has neither.
- **Seam 4 — failure memory.** Cheap and ADR-free-adjacent: per-client
  short-TTL failure cache so one gated resolve does not re-pay every
  timeout on the next track. (Still needs the ADR's blessing if it touches
  the identity chain's scheduling.)

## 5. Honest limits of this comparison

- vivi's `/player` request shape (exact token fields,
  `serviceIntegrityDimensions`, nonce wiring) lives inside the innertubex
  binary/source, which was **not** source-inspected here — only its API
  surface as used by vivi. A follow-up can read
  `MetrolistGroup/innertubex` source before any integration design.
- Remote cipher configs are a supply-chain trust decision (a third party's
  `raw.githubusercontent.com` URL fetched at runtime), not just a
  dependency line. Self-hosting/pinning is an open question for the ADR.
- JitPack (`com.github.…`) is a availability risk vs Maven Central; pinning
  and a fallback plan belong in the ADR.
- Nothing here is proven on DHUN's side: no hardware run, no drill signal,
  no compile. The ADR acceptance gates must include all three plus the
  user's on-network verdict.
- BotGuard solving sits against YouTube's ToS like any anti-bot
  circumvention; that exposure must be named in the ADR, not buried.
