# ADR-007: Android stream attestation (PO-token / extraction-engine options)

## Status

**PROPOSED** — 2026-09-10. Drafted from source research only
(`docs/research/02-innertubex-potoken-comparison.md`). **Not accepted. No
implementation is authorized by this document.** Acceptance requires the
user's explicit approval plus the gates in "Acceptance gates" below.

Supersedes nothing. Extends ADR-001 (extraction engine) and ADR-003 (staged
identity chain): those locked a *tokenless* chain, and every option here
except D changes that posture.

## Context

DHUN Android resolves streams through `OwnClientStreamResolver` only: seven
tokenless InnerTube identities in staged waves, no fallback, no outer
budget (`app-android/.../di/AppModule.kt`). On bot-gated networks YouTube
answers every identity with `LOGIN_REQUIRED / AuthRequired("Sign in to
confirm you're not a bot")` (issue #14; rot-drill evidence). The failure
re-enters ExoPlayer retries and 403-recovery, so the user-facing symptom is
a minutes-long stall, not a quick typed error. Desktop is rescued by the
yt-dlp subprocess under a 45 s budget; Android has neither (ADR-001: no
yt-dlp on Android).

Separately, DHUN cannot play ciphered responses at all:
`parseStreamInfo` throws `Parse("...ciphered/protected response")` when no
format carries a direct URL.

The reference design is vivi-music (`vivizzz007/vivi-music` @ `fcd8996`),
an actively-developed Android YouTube Music client that plays anonymously
on gated networks. Source inspection shows its playback path is:
`InnerTubeXPlayer` → `com.metrolist.innertubex:innertubex-android v0.2.6`
(GPL-3.0, JitPack) with an injected WebView BotGuard minter (8 s cap,
session-cached, graceful `null` → tokenless fallback), a cipher service fed
by remotely-fetched player configs (ZemerTeam/zemer-cipher, GPL-3.0), and
SABR/HLS disabled. Login exists in that app for personalization, not for
playback. Full detail: `docs/research/02-innertubex-potoken-comparison.md`.

## Decision to be made

> How should DHUN Android obtain playable streams on bot-gated networks?

### Option A — adopt a maintained engine + on-device minter on Android (recommended)

Android gains an `androidMain`/`app-android` extraction path built on
`innertubex-android` (or equivalent) with a WebView PO-token provider:
token-first, bounded (≈8 s mint cap), falling back to the existing
tokenless staged waves. Cipher service included (adopt or reimplement).
Desktop keeps own-client + yt-dlp.

- **Pros:** follows a proven, in-production design; maintenance absorbed by
  the engine + remote player configs; anonymous playback preserved (no
  account needed); graceful degradation when minting fails.
- **Cons:** biggest change — Android-only extraction path splits the
  "shared core" story; new JitPack dependency; WebView required at runtime
  (needs a no-WebView fallback story); remote cipher-config URL is a
  supply-chain trust decision; BotGuard solving is against YouTube ToS and
  an arms race (re-armable design mandatory); SABR stays unsupported.

### Option B — minimal own minter, keep own resolver

DHUN implements only a WebView minter + PO-token request fields
(`serviceIntegrityDimensions`, visitorData, nonce) and tries token-bearing
identities (e.g. ANDROID) ahead of the tokenless waves. No new engine, no
cipher service.

- **Pros:** smallest dependency footprint; stays in DHUN's own code.
- **Cons:** DHUN absorbs the whole arms race (BotGuard JS, challenge flow,
  player-field drift) with no upstream; ciphered responses still unplayable
  unless a cipher layer is also built — at which point this converges on A
  with more own code and less maintenance help.

### Option C — opt-in authenticated route (cookies / login)

User-supplied session (paste-a-cookie or login flow) unlocks gated clients
and personalization. Opt-in, own account, personal use only.

- **Pros:** durable where it works; unlocks age/region-gated content and
  personal library.
- **Cons:** does not fix anonymous playback (the reported symptom);
  credential handling + storage risk; clearest ToS exposure; login UX +
  session-expiry maintenance. Best framed as a *later, optional layer* on
  top of A or D, not as the root fix.

### Option D — status quo + hardening (ship regardless)

Keep the tokenless chain; bound the failure: Android outer resolve budget
(mirror desktop's 45 s), persist `DhunStreamCache` across restarts (~6 h
TTL survives reboot), per-client short-TTL failure memory, keep fast-fail
behavior. No new engine, no tokens, no ToS change.

- **Pros:** strictly reduces harm; small, reviewable, CI-verifiable.
- **Cons:** fixes the *stall*, not the *gate* — gated networks still get no
  audio. Note: `DhunStreamCache`/engine-contract files are on the frozen
  list, so even D needs a scoped mandate before code is touched.

## Recommendation

**A as the root fix (after acceptance gates), D shipped regardless, C as a
later optional layer.** B is not recommended: it takes the arms race
without the maintenance help.

## Acceptance gates (all required before any implementation)

1. User explicitly accepts this ADR (option + ToS exposure acknowledged).
2. Hardware proof: audible anonymous playback on the user's gated network
   (Android), plus the no-WebView fallback demonstrated.
3. CI green on the implementing PR (`build-and-test` + `apk` + `msi`).
4. Rot-drill signal: drill extended to cover the new path; red stays
   issue-#14-class (environment), not implementation-class.
5. Maintenance plan recorded: engine version pin + update cadence, cipher
   config source decision (adopt vs self-host vs pin), JitPack fallback.
6. `THIRD_PARTY.md` attribution + `KNOWN_LIMITATIONS.md` updated (WebView
   requirement, SABR unsupported, ToS note, Desktop/Android path split).

## Consequences if accepted

- Android extraction gains a platform-specific path; `MusicProvider`
  remains the only music-source API surface (no caller changes).
- New dependency(s) pinned in `gradle/libs.versions.toml`; JitPack
  repository addition reviewed for supply-chain risk.
- `DhunStreamCache` TTL semantics revisited against token-bound URL
  lifetimes (tokens/URLs expire; cache must not serve stale bindings).
- The tokenless staged waves stay as the fallback chain; drill keeps
  watching them.
- "Permanent fix" claims are forbidden: this is an arms race, and the
  design must be re-armable (config-driven client/token selection where
  feasible).

## Open questions for the user

1. Is the Android/Desktop extraction split acceptable, or must any fix stay
   in shared code (which rules out A as framed)?
2. Is on-device BotGuard solving acceptable given the ToS exposure, with
   the alternative being "no audio on gated networks"?
3. Who hosts/pins the cipher player-configs: upstream URL, DHUN fork, or
   vendored snapshot + update process?
4. Should D's hardening (budget + cache persistence + failure memory) land
   first as its own PR while A is decided?
