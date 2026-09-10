# Android stream extraction authentication research

Updated 2026-09-10. Scope: evidence for the Android playback-resolution failure where tokenless InnerTube clients can stall behind YouTube bot/attestation gating. This document is research only; it does not authorize an extraction identity-chain change.

## 1. Current DHUN problem

The existing DHUN Android path uses `OwnClientStreamResolver` with tokenless InnerTube client identities. The preserved project evidence shows `AuthRequired(Sign in to confirm you're not a bot)` during stream resolution, while unrelated metadata/search paths continue to work. Windows has an additional yt-dlp fallback; Android deliberately does not. The resulting Android user symptom can therefore be a long wait rather than a quick typed failure.

The current resolver identity/parallelism design is governed by the accepted ADRs. Any change to that chain requires an ADR and real-data evidence before implementation.

## 2. What current yt-dlp documentation says

The current yt-dlp PO Token Guide describes a Proof of Origin token as an attestation value required by some YouTube clients. Without the required token, affected requests can return HTTP 403 or cause account/IP blocking. The guide identifies BotGuard (Web), DroidGuard (Android), and iOSGuard as token-generation sources and states that tokens are platform-specific. It also states that token requirements and affected clients are changing over time.

Source: https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide

Important current details from that guide:

- PO tokens can be required for GVS playback URLs and, for some clients, InnerTube `player` requests.
- Current enforcement differs by client; for example, the guide currently lists `android` as requiring PO tokens for GVS or Player, while `tv` and `web_embedded` have different restrictions.
- Tokens have content/session binding and limited lifetime, so a token service must refresh them rather than treating them as permanent credentials.
- The guide recommends provider/plugin mechanisms rather than assuming a single static token will remain valid.

This supports treating token generation as a first-class, replaceable subsystem rather than hard-coding one permanent client workaround.

## 3. Concrete reference: vivi-music, not ViMusic

The relevant project is `vivizzz007/vivi-music` (VIVI Music). It is distinct from the archived ViMusic project.

Repository: https://github.com/vivizzz007/vivi-music

The inspected VIVI source currently routes Android playback through `InnerTubeXPlayer`. Its code constructs a `com.metrolist.innertubex.extraction.InnerTubeExtractor`, supplies a `TokenProvider`, and maps `PoTokenResult` into both player-request and streaming-data token fields. It also uses `generateClientPlaybackNonce()` and disables SABR because the app's playback engine expects a directly playable extracted stream.

Source inspected:
`app/src/main/kotlin/com/music/vivi/utils/InnerTubeXPlayer.kt`

The important architectural observation is that the PO-token provider is injected into the extraction engine. The playback caller does not itself implement BotGuard; it asks the token provider for a token associated with the video and visitor/session identity.

## 4. How VIVI generates the token

VIVI's `PoTokenGenerator` uses an Android `WebView` implementation (`PoTokenWebView`) to execute BotGuard-related JavaScript and obtain an integrity token/minter. The generator:

1. Detects whether the device WebView implementation is usable.
2. Creates and caches a token-generator WebView per session.
3. Generates a streaming token once for the session and a player token for each requested video.
4. Recreates the WebView if it becomes invalid or its renderer dies.
5. Treats an unavailable WebView as a capability failure rather than crashing playback.
6. Applies an **8-second timeout** to token generation. If the WebView hangs, it clears the generator and returns no token so the extraction engine can try a fallback path instead of blocking playback indefinitely.

Source inspected:
`app/src/main/kotlin/com/music/vivi/utils/potoken/PoTokenGenerator.kt`
`app/src/main/kotlin/com/music/vivi/utils/potoken/PoTokenWebView.kt`

The WebView is deliberately loaded from local HTML and has network loads blocked. The page calls YouTube BotGuard endpoints through the app's networking layer, evaluates the BotGuard/minter JavaScript inside the WebView, receives the resulting integrity information, and exposes token-generation calls back to Kotlin through a JavaScript interface.

## 5. Why this changes the DHUN hypothesis

The VIVI implementation is strong evidence that anonymous Android playback does not inherently require a Google account login. Its extraction path can obtain attestation material for a visitor/session and use that material with InnerTubeX. Therefore:

- **PO-token/attestation is the primary research candidate for anonymous playback.**
- **Cookies/login should remain an optional authenticated layer** for personalization or content that genuinely requires an account.
- A cookies-first design should not be treated as the only explanation for why another Android client can play while DHUN's tokenless clients are gated.

This is evidence about an implementation strategy, not proof that VIVI's exact dependency/version remains valid for DHUN or that the same endpoint sequence is still accepted by YouTube at DHUN implementation time.

### 5.1 Stronger finding from the InnerTubeX library itself

Direct inspection of the upstream `MetrolistGroup/innertubex` source makes the boundary clearer than the VIVI app wrapper alone.

`TokenProvider` is a small injected interface with explicit capabilities (`providers`, `usesWebView`), `getPoToken(videoId, visitorData, cookie)`, optional `prewarm`, invalidation, and close hooks. Its `PoTokenResult` carries separate player-request and streaming-data token values plus the visitor data, and its `toString()` deliberately reports only token presence rather than token contents. Source: https://github.com/MetrolistGroup/innertubex/blob/13f8e4d36d249024cfe356c9cd67cd7fd8ee6493/src/commonMain/kotlin/com/metrolist/innertubex/extraction/TokenProvider.kt

`PlayerClientDirector` is the other critical boundary. It selects playback clients based on content/auth/token capabilities, uses an explicit **8-second player-request timeout**, and gives PO-token acquisition its own **18-second upper bound**. It can first try an un-tokenized request, then retry with a token when the selected client requires it. The resulting token is split into player and GVS/streaming-data bindings and is rejected if the visitor binding or required token fields do not match. Source: https://github.com/MetrolistGroup/innertubex/blob/13f8e4d36d249024cfe356c9cd67cd7fd8ee6493/src/commonMain/kotlin/com/metrolist/innertubex/extraction/PlayerClientDirector.kt

`InnerTubeExtractor` injects this director into a higher-level extraction API and also prewarms both player configuration and token generation when the provider advertises capabilities. Its default token provider is explicitly an unavailable provider, so token support is optional at the library boundary rather than baked into every caller. Source: https://github.com/MetrolistGroup/innertubex/blob/13f8e4d36d249024cfe356c9cd67cd7fd8ee6493/src/commonMain/kotlin/com/metrolist/innertubex/extraction/InnerTubeExtractor.kt

**DHUN implication:** the best architectural lesson is not “copy InnerTubeX”; it is **separate token acquisition from client selection and stream extraction, expose capability/timeout semantics, and bind returned tokens to the same visitor/client/request identity**. That boundary maps naturally to an ADR discussion.

## 6. DHUN integration options to compare in the ADR

### Option A — PO-token provider + compatible InnerTube extraction (primary)

Add a replaceable Android PO-token provider and connect it to a compatible extraction implementation. The provider should expose capability/timeout/failure semantics so a failed BotGuard/WebView operation cannot turn into an unbounded playback stall.

Advantages:
- preserves anonymous playback as the default goal;
- matches the current direction used by VIVI/InnerTubeX;
- avoids storing account cookies merely to obtain playback;
- makes token acquisition a replaceable boundary as YouTube changes enforcement.

Risks:
- BotGuard/PO-token behavior is an active arms race and can change;
- WebView/JavaScript execution has device-specific failure modes;
- token binding and expiration require correct cache keys and refresh behavior;
- a token/client/user-agent mismatch can produce 403s even when token generation itself succeeds;
- this is an extraction identity-chain change and therefore requires the project ADR/evidence gate.

### Option B — opt-in cookies/session authentication

Allow a user to supply an authenticated YouTube session and use it for extraction where supported.

Advantages:
- useful for account-specific/private/age-restricted content where authentication is genuinely required;
- can be a fallback when anonymous extraction is unavailable.

Risks:
- sensitive credential handling and revocation/expiry;
- greater account/ToS exposure;
- cookies do not remove the need to understand current PO-token requirements for clients that require attestation;
- Android UX is substantially more complex than anonymous playback.

Conclusion: useful as an optional capability, not the primary explanation for VIVI's anonymous playback.

### Option C — OAuth/device-code playback authentication

Keep demoted unless new evidence appears. Existing project research and current practical behavior do not justify making OAuth the primary stream-extraction mechanism.

## 7. Safe reliability improvements independent of identity choice

These should be evaluated separately because they do not require replacing the identity chain:

1. **Persist `DhunStreamCache` across app restarts.** The current in-memory cache cannot rescue the same track after process death/restart even when its resolved URL is still within the intended TTL. Any persistent design must preserve user-agent binding, TTL, and invalidation semantics.
2. **Add an Android outer resolve budget.** The desktop resolver already has a bounded primary/fallback resolve budget. Android should have an explicit whole-resolution ceiling so repeated per-identity timeouts and 429 parking cannot present as an apparently infinite buffer. The timeout must be chosen so offline cache-span replay and normal transient network recovery are not harmed.

Neither item should be described as fixing bot-gating; they only improve failure behavior and cache resilience.

## 8. Required validation before implementation

### Static/code validation

- Compare `OwnClientStreamResolver`, `InnerTubeClient`, `StreamInfo.userAgent`, and `DhunStreamCache` contracts against the proposed token provider.
- Verify which exact InnerTube client receives each token and that the resulting stream URL is fetched with the matching user-agent/client identity.
- Verify cancellation and timeout behavior, especially WebView renderer death and background/foreground transitions.
- Confirm no frozen playback engine contracts need to change.

### Live data validation

- Windows: compare anonymous yt-dlp, authenticated yt-dlp, and a controlled client selection such as `tv`, recording latency and final playability.
- Android: capture `adb logcat -s DHUN` for cold uncached playback, token generation, stream resolution, and first audio.
- Test the same video across at least several ordinary tracks, including one known to fail under the current tokenless chain.

### CI/hardware gates

- `build-and-test` must pass.
- `test-release` APK/MSI gates must pass where required by the project's pre-merge ritual.
- Hardware playback must be tested separately; CI cannot establish audible Android playback.
- A sustained playback/soak run remains separate from compilation success.

## 9. Current recommendation

Proceed to an ADR comparing **Option A (PO-token provider / InnerTubeX-like boundary)** against **Option B (optional cookies/session)**, with Option C demoted. Do not copy VIVI code wholesale. First determine the smallest compatible abstraction that fits DHUN's existing `MusicProvider` and resolver contracts and the accepted ADR constraints.

The safest likely architecture is a dedicated Android token-provider boundary with explicit timeout/capability semantics, paired with a resolver that preserves client/token/user-agent binding and has a hard whole-operation budget. The provider should be replaceable because YouTube's attestation behavior is not stable enough to treat today's exact BotGuard flow as permanent.

## Sources inspected

- yt-dlp PO Token Guide: https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide
- VIVI Music repository: https://github.com/vivizzz007/vivi-music
- VIVI `InnerTubeXPlayer.kt` at inspected revision `fcd89968f7b0002d4dcca2546ab7034cfce28f4f`
- VIVI `PoTokenGenerator.kt` at inspected revision `fcd89968f7b0002d4dcca2546ab7034cfce28f4f`
- VIVI `PoTokenWebView.kt` at inspected revision `fcd89968f7b0002d4dcca2546ab7034cfce28f4f`
- Metrolist InnerTubeX `TokenProvider.kt` at revision `13f8e4d36d249024cfe356c9cd67cd7fd8ee6493`
- Metrolist InnerTubeX `PlayerClientDirector.kt` at revision `13f8e4d36d249024cfe356c9cd67cd7fd8ee6493`
- Metrolist InnerTubeX `InnerTubeExtractor.kt` at revision `13f8e4d36d249024cfe356c9cd67cd7fd8ee6493`

No claim in this document means that the researched mechanism is currently implemented in DHUN, CI-verified, released, or hardware-verified.
