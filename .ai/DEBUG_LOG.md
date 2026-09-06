# DEBUG_LOG — incidents, root causes, environment traps

## 2026-09-06 — Packaging helpers CI-green; dispatch still requires owner action

[CI 34028225356](https://github.com/99ggprooo00-code/DHUN/actions/runs/34028225356) on `77f9c96` **PASSED**;
job 101472922356 completed 10:46:45Z. GitHub APIs confirm Python helper
tests, PowerShell AST parsing, shared JVM tests, Android debug build and
probe/Desktop compilation passed, with no check annotations. Earlier
CI 34028039448 at `9317050` passed too. There is still **no packaging run**
on this branch after the 403 dispatch denial, hence no MSI/package/sentinel
result or new downloadable Windows artifact. Main/test remain `0920148`,
public release timestamp 07:22:29Z, PR list empty.

Owner action is now the minimal unblock: reconnect GitHub in Arena, or use
GitHub Actions → test-release → Run workflow, choose the existing session
branch (not main), leave build-only on. No credential should be shared in
chat. Do not call PR/merge/finalization actions or bypass the denied dispatch.

---

## 2026-09-06 — Manual packaging dispatch denied by integration permissions

Build-only automation was pushed at `9317050` (implementation `6fedf8a`).
Automatic branch CI 34028039448 started normally. Manual dispatch did not:

- `gh workflow run ... --json` rejected boolean-valued JSON locally; this
  installed gh version expects strings. No event was sent by that attempt.
- Retrying with the supported `-f build_only=true` on the same session ref
  reached GitHub and returned **HTTP 403: Resource not accessible by
  integration** for the workflow dispatch endpoint. No packaging run exists
  on the branch, confirmed via the runs API. No MSI artifact was produced.

This is a GitHub integration permission boundary, not a Kotlin/JDK failure.
Do not work around the denial with a different credential/trigger. Ask for
GitHub reconnection in Arena, or have the owner use GitHub Actions →
`test-release` → Run workflow → **arena/01a0759b-dhun** → build-only. Selecting
main would use the old published workflow, so the ref matters. The new
branch's publish guard prevents release changes regardless of build-only's
value. Add a PowerShell AST syntax check to normal CI while dispatch is
blocked; parsing scripts is not native MSI execution. No PR/merge/release or
session-finalizing action occurred.

---

## 2026-09-06 — Safe branch packaging route prepared; no PR/session finalization

The user clarified that normal work should continue without the one-time
Arena session-ending action. Reuse `test-release.yml` with a default-on
`build_only` input. Its job-level guard disallows publishing from non-main
refs regardless of the input, build tokens are contents:read, and branch
concurrency cannot cancel main's existing release group. Reuse of the SAME
workflow counter is deliberate: using the general CI run number for a
candidate MSI could make a later release MSI a numeric downgrade.

Added revision-bound binary manifests/checksums, read-only Windows Installer
COM property validation, and a disposable-runner-only install-over sentinel
check before making the MSI artifact downloadable. The script does not
launch DHUN or claim audio/visual acceptance. PowerShell/packaging execution
is still pending. Local Python regressions: **19 PASS**; JSON fixtures: **29
syntax-valid**. No new release, tag, PR, branch or repository setting changed.

Node runtimes were checked from the upstream action manifests: checkout v5,
setup-python v6 and upload-artifact v6 use Node 24. The guarded, unexecuted
publish job's existing download-artifact v4 is unchanged.

---

## 2026-09-06 — Corrected branch CI GREEN; release deliberately unchanged

**Verified:** [CI 34025807972](https://github.com/99ggprooo00-code/DHUN/actions/runs/34025807972) on
`75c4a8b9e6b3a030d24a360b0cb98923a4de5a0f` succeeded; job 101466441642
completed **09:57:34Z**, duration **6m13s**. GitHub run/job/check APIs confirm
Python checks, shared JVM tests, Android debug build and probe/Desktop
compilation all passed. Check annotations: **0** (not a full log-warning audit).
The `$index_` compiler errors are resolved and the Quick-picks predicate is
now connected to the actual screen projection rather than only a test.

This removes the Kotlin/build-verification blocker through the user-approved
CI path, despite the local sandbox still lacking a JDK. It does NOT prove
YouTube playback, Windows installer upgrade/data preservation, live Home
scrolling, visual/native acceptance or soaks. No test-release/rot-drill dispatch,
PR, merge, tag or release was performed. Main/test remain `0920148` and the
published installer remains the 07:22:29Z build. This evidence is followed by
a same-branch documentation-only push; application/test inputs stay unchanged.

---

## 2026-09-06 — First authorised branch CI reaches Kotlin; Home key interpolation fails

Push `f914050` started CI **34025629231**. JDK setup and Python checks passed;
`:shared:compileKotlinJvm` failed at HomeScreen lines 393/407/420/439 with
`Unresolved reference 'index_'`. Kotlin reads `$index_` as one identifier;
keys must use `${index}_`. Four occurrences corrected. Kotlin tests and the
later Android/probe/Desktop steps were skipped, not passed.

Review also found that `quickPickShelfAlreadyShown` had a test but was not
used by the actual screen projection, whose category filter discarded all
Quick picks. Wire `remainingHomeSections` into the screen and test that a
later same-title shelf with new music survives. The run's Node-20 checkout
warning is addressed with `actions/checkout@v5` (manifest verified node24).
All changes remain branch-CI-only; main/test, installer and PR list unchanged.

---

## 2026-09-06 — CI-only checkpoint explicitly approved

After the local JDK/download blockers, the user approved the offered scope:
**commit/push only to `arena/01a0759b-dhun`, run CI and fix failures; no PR,
merge or release**. This supersedes the earlier keep-local restriction for
branch verification only. No workflow dispatch of test-release or rot-drill
is included. The published `test@0920148` / 07:22:29Z build is unchanged.

Pre-push GitHub audit: main unchanged, no open PRs, no remote session branch;
previous baseline CI 34018809911 is green but does not cover the repairs.
Ten local Python helper tests, 29 JSON fixture syntax checks and diff checks
pass. Candidate Kotlin/Android/Desktop checks are pending. Record real run
IDs and failures below when available; do not close hardware acceptance.

---

## 2026-09-06 — Local continuation: target ownership, gesture cancellation, subprocess deadline

The user asked to continue from the saved state. The earlier **Keep everything
local** decision remains in force; no commit, push, PR or publication occurred.

**Additional source defects corrected locally:**

- `HomeFeedParser` initially flattened all append/reload actions. A horizontal
  shelf command could therefore supply the vertical feed's next cursor even
  after fixing recursive token lookup. A full initial page could also be
  overridden by an unrelated action. Prefer the actual section list, group
  incremental updates only by matching non-null target IDs, select an
  unambiguous Home group, and reject ambiguous targets instead of guessing.
  Split shelf/cursor commands for the same named target are supported.
- Replaced interpolated Home test JSON with **17 synthetic fixture files**
  consumed by `HomeFeedParserTest`, covering mixed/anonymous/ambiguous targets,
  append/reload aliases, split commands, empty advancing pages and exhaustion.
  `scripts/validate_fixtures.py` strictly checks the actual files (duplicate
  keys and non-JSON numeric constants are errors). This does not run Kotlin.
- Previous/Next treated `waitForUpOrCancellation()` returning null as a
  successful release and could skip on a cancelled press. Hold cleanup was
  also skipped if the pointer coroutine was disposed/cancelled after starting
  seek. `TransportPress` distinguishes release/cancel/deadline, captures a
  matching callback set per press, and gets idempotent cleanup in `finally`.
  Seek-bar handlers now refresh when duration/callback changes; changing
  tracks recreates the scrub state, including equal-duration tracks.
- yt-dlp's process-exit wait alone did not bound awaiting pipe EOF. Its own
  timeout now wraps exit **and output drain**; inner/outer cleanup share one
  disposal. Process start denial is typed. HTTP 429 classification no longer
  mistakes a video ID containing `429` for a rate limit. New Kotlin cases cover
  an exited child with still-open output, cleanup count, start denial and
  classification. The extractor identity chain remains sequential/unchanged.

**Verification actually run:** ten Python tests PASS (5 installer + 5 fixture
validator); all **29 JSON fixture files** pass strict syntax validation;
`git diff --check` passes. Kotlin tests remain **UNRUN**: Gradle again stops
with `JAVA_HOME is not set and no 'java' command could be found in your PATH.`
The official Temurin 17.0.20.1+1 Linux JDK asset was identified through GitHub,
but its download failed with EOF at `release-assets.githubusercontent.com`;
no binary/checksum/install succeeded. Direct Gradle, Maven, Android SDK and
Debian endpoints also failed. Do not retry these routes indefinitely or use
unauthorised CI publication to evade the local-only decision.

No new app build, live extraction, audible playback, Windows upgrade, UI
acceptance or soak evidence exists for these local changes. See ROADMAP for
exact next validation tasks and the still-open gates.

---

## 2026-09-06 — Fresh Windows failures; local repair batch (arena/01a0759b-dhun)

**Evidence, not a success claim:** after the 07:22:29Z `test@0920148`
recommendation, the user reports install-over blocked by **“Another version
of this product is already installed…”**. Manual uninstall/reinstall opens
**one window**, so do not redo ADR-004. Audio still fails with the generic
unavailable banner; Home still does not page; glyph placement, shuffle shape
and transport colours remain wrong despite somewhat better styling.
Screenshot/report: **Ko Cha Ra (Official Audio) — John Rai, 0:00 / 4:49**,
no useful error detail. Original image was not copied into the repository;
no verified checksum, track ID, Windows/VLC/tool versions or sanitized current
logs were supplied. This is user-facing failure, not evidence of CI-only gating.

**Source defects and LOCAL corrections:**

1. `app-desktop/build.gradle.kts` rebuilt every MSI as **1.0.5**. Add an
   explicit internal version property; the rolling workflow supplies a
   numeric run/attempt sequence from `scripts/installer_version.py`, keeping
   the stable upgrade UUID/public asset. Fresh reruns advance too; an old-ref
   publishing guard prevents replacing the slot with a superseded build.
   Startup logs record the version. Actual in-place upgrade/data preservation
   is still untested; future stable packaging must not reset the sequence.
2. Desktop `YtDlpStreamResolver` invoked Unix **`which`**, then assumed
   `python3`. The standalone `yt-dlp.exe` can be installed on Windows yet
   missed. New locator handles Windows Path casing, quoted paths, executable
   override, real Python/`py` fallback and Store-alias avoidance. Tool absence
   is explicit evidence, not Network. The user's installation state remains
   unknown; this defect alone does not explain every rejected player request.
3. The process waited before draining either pipe and was not cancellation
   safe. Move the resolver to its own JVM file, drain both pipes concurrently
   with bounded retained output, interrupt `waitFor`, dispose process/children
   before joining readers and also on early cancellation. This is pipe I/O
   concurrency, **not** parallel InnerTube identities. `--ignore-config`
   preserves the anonymous/no-cookie contract.
4. UNPLAYABLE/ERROR reasons disappeared into a detail-less Unavailable,
   aggregation dropped those details, and double failure discarded the
   fallback. Preserve status/reason/subreason, bounded per-identity and both
   engine outcomes (including missing dependency), sanitized URL-free text;
   429 keeps retry semantics. Timeout identifies the active engine and any
   completed primary failure. Full and docked players share a complete,
   scrollable/selectable Details dialog instead of ellipsis or a giant banner.
5. Home chose the first continuation recursively (often a horizontal shelf's
   token), deduplicated by title, and stopped even if a duplicate/empty page
   supplied a new token. Add a scoped Home parser plus action/command response
   handling, content-aware deduplication, advancing-token following with a
   three-no-growth-page pause, token-cycle checks, visible retry/end UI and
   indexed shelf keys. Later Quick picks with new IDs must remain visible.
   VM generation/feed/flags now update atomically; old completion cannot
   overwrite refresh even on Desktop's Default dispatcher. Failed pages are
   not retried by every recomposition. Home's original fixture has no token,
   so the new parser cases are explicitly **synthetic**, not live evidence.
6. The first metadata context could contain a fallback version while the
   header used the newly discovered version. Normalise the request body to
   the same value; MockEngine regression covers first Home and continuation.
7. SVG scaling pivoted around the canvas centre instead of coordinate origin,
   shifting/clipping non-24px glyphs (including display scaling). Fix the
   actual draw helper and use canonical Material shuffle/repeat/repeat-one
   paths; record upstream SHA/license in THIRD_PARTY. Fit player artwork to
   width **and height**, centre bounded transport/volume and use consistent
   inactive colours/sizes and active toggle treatment. No separate window
   was reintroduced. Raster/layout regressions are written, not executed.

**Validation/publication boundary:** five Python version regressions PASS;
`git diff --check` PASS; literal request-fixture JSON syntax checked.
`./gradlew :shared:jvmTest :app-desktop:compileKotlinJvm --no-daemon` stops
before Gradle with **`JAVA_HOME is not set and no 'java' command could be
found in your PATH.`** No checked JDK/cache exists; Maven/Gradle/Adoptium
requests failed with `SSL_ERROR_SYSCALL`. No Kotlin compile/test, Windows
installer, current live playback or UI result was produced for this batch.
No commit/push/PR has been made. When asked about making this the next
checkpoint, the user explicitly selected **Keep everything local**: do not
commit, push, open a PR or publish now. The prior green GitHub runs are only
the baseline; wait for a later authorised checkpoint or an available local
build environment. ADR-003 is still proposed; seven identities remain sequential.
See verification/12 and /14 and ROADMAP for exact next actions/open gates.

---

## 2026-09-06 — UI restyle: the "terrible UI" was one bad colour function (session arena/01a07563-dhun)

**Trigger:** the user supplied 18 screenshots (APK under MEmu, MSI on
Windows 11) that had been outstanding since the previous session. Task C of
the handoff was unblocked.

**What the screenshots actually showed.** Nine of them are tinted a
different colour — Home brown, Search green, FullPlayer maroon, the desktop
window magenta. The Now Playing screen has a **fire-engine-red play disc and
a full-width red volume slider**, which is pixel-for-pixel what an error
state should look like. This was not a "styling taste" problem; it was one
function.

**Root cause.** `ArtworkColorExtractor.extractFromSeed` hashed the artwork
URL and mapped the hash across the **entire hue wheel** at 0.62–0.92
saturation / 0.78–0.98 value. That colour then went to three places at once:

| Consumer | What it did with it |
|---|---|
| `DhunAppShell` ambient wash | painted the whole app at alpha 0.42 → 0.18 |
| `FullPlayer` backdrop | alpha 0.28 |
| `FullPlayer` **controls** | used it *raw* for the play disc, seek bar, slider, active tab, "NOW" label |

So the UI was a different loud colour every track, and roughly one seed in
six landed on red — indistinguishable from the error affordance sitting
directly above it.

**Fix.** Split "ambient colour" from "control colour", which had been
conflated:

- hue clamped to −40°..+30° of the brand hue (deliberately asymmetric — it
  stops short of 300° so it can never cross into magenta/red), saturation
  0.34–0.48, value 0.62–0.74;
- ambient alpha 0.22 → 0.10, desaturated 55%; shell wash 0.42 → 0.30;
- new `ArtworkColors.controlAccent`: blends 45% brand accent in, then floors
  luminance at 0.42 so dark artwork cannot produce an invisible disc. All
  transport chrome reads this. **Raw `primary` is now backdrop-only.**

**Other genuine defects the screenshots exposed** (each was invisible from
the code alone):

1. `SkipPrevious` and `SkipNext` **glyph paths were swapped** — the left
   button drew a right-pointing arrow with a trailing bar. Present in every
   player screenshot; nobody caught it because the *handlers* were correct.
2. Missing artwork rendered as a flat grey rectangle, and a *failed* Coil
   load fell through to the same bare box. Both now get a note glyph.
3. The mini-player "Playback error" dialog was a stock `AlertDialog` — an
   opaque grey slab that ignored the design system entirely. It also
   discarded `PlaybackState.Error.detail`, the very diagnostics the previous
   session added, because only `FullPlayer` rendered it.
4. Dialogs used `GlassCard` while floating over a `Dialog` scrim. Glass needs
   something behind it; there was nothing, so the page bled through the text
   and "New playlist" was unreadable. Added `opaqueBase`.
5. Glass tokens were 55–82% **opaque** near-black composited onto a near-black
   background — arithmetically that is just flat grey, which is exactly how
   it rendered. Re-tuned genuinely translucent (the user's standing ask).
6. The developer component catalogue shipped as a **fourth user-facing nav
   tab**. Hidden behind `AppTab.userTabs`; the enum entry stays because
   `MainActivity` restores tab state by `valueOf(name)`.

**Trap worth recording.** `ArtworkColorExtractorTest` asserted
`backgroundTint.alpha in 0.15f..0.35f` — a test that *locked in* the alpha
that was causing the problem. Retuned to 0.05–0.15. A test asserting a
design token is only as good as the token.

**Task B (assigned corrective) closed.** `ci.yml` push trigger extended to
`branches: [main, "arena/**"]`. Verified live: pushing this branch with no
PR open produced run `34016873567`, and its `headSha` was confirmed equal to
the pushed commit before trusting the green.

**Status: CI-green, NOT hardware-verified.** Playback untouched by this work.

## 2026-09-06 — hardware verdict: both builds LAUNCH, but no audio at all (session arena/01a0750c-dhun)

**Report (user, real hardware, builds from the rolling `test` release):**
`dhun-test.msi` and `dhun-test.apk` both install and **both launch** — the
"Failed to launch JVM" fix from PR #22 is confirmed on hardware. But:

1. **No audio plays on either platform.** Not one track, on either app.
2. UI reads as "bad / terrible", and the two apps look much alike.
3. **No endless scroll** anywhere.

**Root cause found for (1) — User-Agent mismatch on the byte fetch:**

A googlevideo stream URL is bound to the InnerTube client identity that
resolved it. `OwnClientStreamResolver` tries seven identities
(`web_embedded` → `visionos` → `tv` → `tv_downgraded` → `tv_simply` →
`mweb` → `web_remix`), each with a *different* User-Agent, then returned
**only the URL**. `StreamInfo` had no field for the identity.

Every byte-reading layer then used its own hardcoded agent:

| Layer | Agent it sent | File |
|---|---|---|
| ExoPlayer HTTP source | `Mozilla/5.0 (Linux; Android 14) … Chrome/126` | `PlaybackGraph.kt` |
| Desktop audio-file cache | `Mozilla/5.0 (Windows NT 10.0; …) Chrome/126` | `AudioFileCache.kt` |
| libVLC itself | whatever libVLC sends — **not overridable via vlcj** | `DesktopDhunPlayer.kt` |

So resolution succeeded and the CDN refused the bytes. This is the same
failure the drill saw once and mis-attributed to IP gating — 2026-09-05
entry: *"expanded chain got googlevideo URL, CDN 403 on bytes"*.

**Fix (this session):** `StreamInfo.userAgent` added and populated by every
resolver (own-client stamps the winning strategy; yt-dlp pinned with
`--user-agent`; NewPipe from `SimpleDownloader.USER_AGENT`).
`AltInnertubeClient.userAgent` made public. Android: `DhunStreamCache`
returns url+agent, `PlaybackGraph` wraps the HTTP source in
`UserAgentDataSource` which stamps the agent on the live instance per open.
Desktop: cache downloader gets the agent, and because libVLC cannot send
one, a fallback waits for that download and replays from the local file
when libVLC rejects the URL (one attempt per track, `Recovering` state).

**Dead end worth recording:** the obvious fix — `httpFactory.setUserAgent(…)`
inside the `ResolvingDataSource.Resolver` — does **nothing**. `ResolvingDataSource`
constructs its upstream data source once, in its own constructor, so the
agent is baked in before the first resolve. It has to be applied to the
live instance per `open()`.

**Root cause for (3):** `MusicProvider` had `searchContinuation` but **no
home continuation at all** — `homeFeed()` returned a bare `List<HomeSection>`
and `HomeScreen` had no list state, so Home could never scroll past page one.
Added `HomeFeedPage` (sections + token), `homeFeedPage()` /
`homeFeedContinuation()` through client → provider → use case → ViewModel,
and a near-bottom trigger + spinner in `HomeScreen`. Search already had
load-more wiring and is unchanged.

**Four CI rounds to compile the Android half — media3 1.5.1 API traps.**
Worth recording because each one is a plausible-looking API that does not
exist or behaves differently here:

1. `httpFactory.setUserAgent(…)` inside the `ResolvingDataSource.Resolver`
   compiles but does **nothing** — `ResolvingDataSource` builds its upstream
   data source once, in its own constructor.
2. `DefaultHttpDataSource.Builder()` — **does not exist** in 1.5.1
   (`Unresolved reference 'Builder'`). It is `DefaultHttpDataSource.Factory`.
3. `DefaultHttpDataSource.setUserAgent(…)` on an **instance** — does not
   exist; only `Factory.setUserAgent(@Nullable String)` does. Verified
   against the source at tag `1.5.1`.
4. `override val uri` on a `DataSource` — *"'uri' overrides nothing"*. The
   interface declares `@Nullable Uri getUri()`, so Kotlin wants
   `override fun getUri()`.
5. The member that kept producing the truncated *"does not implement
   abstract members"* error: **`addTransferListener(TransferListener)` is
   abstract**, not default. Only `getResponseHeaders()` has a default.

GitHub annotations truncate multi-line compiler messages and
`gh run view --log` returned nothing, so the interface was read directly
via `api.github.com/repos/androidx/media/contents/...?ref=1.5.1`
(`raw.githubusercontent.com` is blocked in this sandbox, `api.github.com`
is not). **Lesson: when an "unimplemented member" error is truncated, fetch
the interface instead of guessing — four CI rounds cost ~10 minutes each.**

Final shape that compiles: the resolver publishes the agent into an
`AtomicReference`; `UserAgentDataSource.open()` restamps the
`DefaultHttpDataSource.Factory`, builds a fresh source for that one
request, and implements `addTransferListener` / `getUri` /
`getResponseHeaders` by delegation.

**Device answers (same session) — they confirm the diagnosis and rule out two alternatives:**

| Question | Answer | What it settles |
|---|---|---|
| APK symptom | **"Buffering, then Reconnecting"** | Resolution **succeeded** (Buffering means a URL arrived and ExoPlayer opened it), then the byte fetch failed → `PlaybackGraph`'s recovery listener set `Recovering` → "Reconnecting…". That is *exactly* the User-Agent-mismatch signature, not a resolution failure. |
| Windows symptom | **"Resolving"** (stuck) | Different symptom — stuck in `PlaybackState.Resolving`, i.e. `provider.getStreamInfo` had not returned. VLC is installed, so this is not a missing dependency. Note: `postAltJson` does **not** retry a definitive `LOGIN_REQUIRED`/`UNPLAYABLE` verdict (`catch (DhunException) { throw e }` exits immediately) — only 429/5xx/timeout get the 2 attempts — so "stuck" means slow/timing-out requests, not a retry storm. Re-check with the fixed build before treating it as a second bug. |
| Metadata | **Partial** — some content loads | Their network reaches YouTube for at least some calls, so this is **not** blanket IP gating. Consistent with a stream-layer (not metadata-layer) failure. |
| VLC | **Installed** | Rules out the "no libVLC ⇒ no desktop audio path" explanation. |
| UI | Screenshots to follow | No restyle attempted yet. |

The APK answer is the strongest evidence in this file: "Buffering → Reconnecting"
cannot be produced by a resolver that never returns a URL. Something resolved,
and the CDN then refused the bytes.

**(2) is unresolved — no screenshots were provided**, so no restyle was
attempted rather than guess at the wrong thing.

**Verification status:** no JDK and no egress in this sandbox (only
`github.com` resolves; `api.adoptium.net`, `services.gradle.org`,
`repo1.maven.org`, `dl.google.com` all `000`), so this is code-read +
CI-compiled. **Audio is NOT yet verified** — needs the next APK/MSI on a
device. `AudioFileCacheTest` now asserts the agent reaches the network
layer; `UseCasesTest` has three new home-pagination tests.

## 2026-09-06 — desktop "stuck on Resolving" is a 4-minute chain, not a hang (session arena/01a0750c-dhun)

**Symptom (user, Windows MSI):** the player sits on "Resolving" and never
advances. The APK on the same network showed "Buffering → Reconnecting",
i.e. resolution *succeeded* there — so this looked like a second, separate
bug.

**Investigation — it is not a hang.** Two things rule that out:

- `InnerTubeClient.defaultHttpClient()` installs `HttpTimeout`
  (`connectTimeoutMillis = 10_000`, `requestTimeoutMillis = 25_000`), and the
  per-request `timeout { requestTimeoutMillis = 12_000 }` in `postAltJson`
  is honoured because the plugin is installed. No request can block forever.
- `postAltJson` does **not** retry a definitive verdict: `LOGIN_REQUIRED` →
  `DhunError.AuthRequired` and `UNPLAYABLE`/`ERROR` → `Unavailable` are
  thrown as `DhunException`, and `catch (e: DhunException) { throw e }`
  exits the `repeat` loop immediately. Only 429 / 5xx / timeout get the
  second attempt. So there is no retry storm either.

**Actual cause: the chain is slow by construction.**

| Stage | Worst case |
|---|---|
| `WEB_REMIX` primary, `MAX_ATTEMPTS = 3` × 25 s + backoffs | ≈ 77 s |
| 7 alt identities, `ALT_MAX_ATTEMPTS = 2` × 12 s + 0.6 s backoff | ≈ 172 s |
| **Total** | **≈ 4.2 min** |

For those ~4 minutes the only thing the UI can render is
`PlaybackState.Resolving` — indistinguishable from a dead player. The
desktop just has less going on than Android (no notification/lock-screen
state), so it reads as "stuck".

**Fix (`d390dd0`):** `ResolvingStreamResolver` wraps the chain in
`withTimeoutOrNull(budgetMs)` (default **45 s**) and returns a typed verdict
on expiry. Typed as `DhunError.Parse`, not `Network`, because `Network` has
no `detail` slot and `toUserMessage()` renders `Parse.detail` — Parse is the
only member that can explain itself. Two tests: the budget returns a verdict
near the window and cancels the slow chain; a fast chain is unaffected.

**Deliberately deferred:** running the identity chain in parallel would cut
wall clock far more (a gated identity fails in ~1 s, so parallel ≈ a few
seconds total), but it changes extraction behaviour and fires concurrent
`/player` calls at YouTube. Under MASTER_PROMPT AI rule 8 that needs an ADR
and real data first.

## 2026-09-06 — CI blind spot: branch pushes with no open PR are never checked

`.github/workflows/ci.yml` is `on: push: branches: [main]` +
`pull_request:`. A commit pushed to a session branch **while no PR is open
gets no CI run at all** — no failure, no warning, just silence. Three
commits (`37f04e8`, `3591e70`, `d390dd0`) sat unverified until PR #25 was
opened, and `gh run list --branch <branch>` simply showed the older runs,
which reads like "CI is fine" if you do not check the SHA.

**Rule: open the PR before trusting a green mark, and always confirm the run
`headSha` matches the commit you think you verified.**

## 2026-09-06 — device screenshots: splash rawness, total APK stream failure, sheets (session arena/01a0740a-dhun)

**Report:** splash shows raw attempt/log lines; APK streams nothing
(persistent mini-player Error + never-resolving skeletons); dialog sheets
have hard boundaries on dark glass. (Screenshots referenced but not
viewable in sandbox — fixes from descriptions + code audit.)

**Audit results:**
- Manifest/FGS/audio-focus all correct (mediaPlayback type + permission,
  exported MediaSessionService, handleAudioFocus/noisy, WAKE_LOCK).
  Koin starts in Application.onCreate before any service access. No
  cleartext anywhere (all endpoints https) — `usesCleartextTraffic=false`
  is not the blocker.
- Real total-failure cliff found: `SimpleCache` throws on a corrupt cache
  dir, and BOTH engine paths (service + session-less fallback) built the
  cache unconditionally → dead app, zero audio. Now both degrade to
  direct streaming (`audioCache = null` path in `PlaybackGraph`).
- Throttled/stall-y carrier reads got more per-segment retries
  (`DefaultLoadErrorHandlingPolicy(5)`) before the error reaches the
  recovery listener from the previous entry.
- `ArtworkImage` pulsed its placeholder forever on failed loads (read as
  never-resolving skeletons) → now settles static on error.
- Splash rewritten (indicator + static line + corner version, logs to
  Logcat only); dialogs to 28dp + `GlassCard.borderColor` (default keeps
  old look elsewhere).
- Still NOT done: if the device's network gates the /player endpoint or
  googlevideo bytes per-region, no Android engine exists (ADR-001: no
  yt-dlp on Android) — the error dialog now surfaces the exact chain for
  the next report. Hardware verification OPEN.

## 2026-09-06 — APK "Error — tap to see" stuck-error + blurry Now Playing art (session arena/01a0740a-dhun)

**Symptom:** Android build latched `PlaybackState.Error` on any ExoPlayer
failure (mini-player "Error — tap to see"); tapping expanded a FullPlayer
that showed no error, and play did nothing. Now Playing art visibly blurry.

**Root causes (code-read, sandbox has no JDK/egress — CI compiles only):**
1. `PlaybackGraph` recovered ONLY `ERROR_CODE_IO_BAD_HTTP_STATUS`; every
   other failure (resolve IOException, timeout, dropped connection) went
   straight to permanent Error — and `AndroidDhunPlayer.refresh()` latches
   any `playerError` into Error.
2. After `onPlayerError` ExoPlayer sits in error-idle where `play()` is a
   no-op until `prepare()` — so the play button appeared dead. No `retry()`
   path existed on `DhunPlayer`, and the 403 path never restored
   `playWhenReady` (re-prepare could land paused).
3. Art: `Parsers.thumbnailOf` kept the FIRST (smallest, w60) thumbnail;
   `parseRelatedTracks` same with no upscale; only literal w60/w120
   rewrites (missed w176+); FullPlayer rendered that at ~0.82 screen width.

**Fix:** bounded auto-recovery for all transient IO errors (invalidate →
seek → prepare + playWhenReady, backoff, max 3/track) in `PlaybackGraph`;
`DhunPlayer.retry()` (Android re-prepare, desktop re-resolve) wired to a
mini-player error dialog, a FullPlayer error banner, and `togglePlay()`;
`ArtworkUrls` tiers (lists 544, Now Playing 1024, proxy-only rewrites) +
largest-entry parsers. Home quick-actions Row→LazyRow (trailing inset);
FullPlayer transport 88→72dp, edges at xxl.

**Verification:** `ArtworkUrlsTest` added (7); hardware play + error-path
soak still OPEN (needs device + residential network).

## 2026-09-05 — Desktop audio cache (session arena/01a07287-dhun)

**Environment trap:** sandbox has no JDK and *no egress* (adoptium,
services.gradle.org, repo1.maven.org all return 000) — `scripts/
restore-toolchain.sh` cannot run. Code on PR #17 is CI-compiled only.

**Design note:** libVLC has no Media3-style data-source layer, so the desktop
cache is whole-file (`AudioFileCache`), not segment-level. First play
streams + fills in parallel; hit plays the local path. `.part` files are
swept on open; `touch()` is made monotonic so LRU order is stable on
coarse-`lastModified` filesystems (the test relies on it).

**CI gap closed:** `app-desktop` was never compiled on PRs (only in the
`test-release` MSI job on main) — added `:app-desktop:compileKotlinJvm`
to `ci.yml`.

## 2026-09-05 — PR #16 merged to main

`gh pr merge 16 --merge` succeeded. Bundle: Phase 14 robustness + M3 glass UI.
Residential stream / soaks / v0.1.0 still human gates — merge ≠ product done.

## 2026-09-05 — PR #16 ready for pull

Title/body refreshed for full scope (Phase 14 + M3 glass UI). CI green,
mergeable CLEAN @ `1df07b3`. Human gates: residential stream, HW soaks, v0.1.0.

## 2026-09-05 — Browse + queue glass rows

Artist toolbar / Album+Playlist frosted track rows / floating back chips /
FullPlayer Queue+Related glass cells. Same glass-morphism language app-wide.

## 2026-09-05 — M3 glass lists + lyrics motion

Search frosted field, Library pill tabs, TrackRow glass cells, airier spacing,
synced lyrics active-line emphasis (ADR-002 P8 lightweight). Still no Liquid Glass.

## 2026-09-05 — M3 glass-morphism chrome (not Liquid Glass)

User: want translucent blurry glass-morphism, still lightweight, not boring.

**Shipped:** GlassCard frosted fill (no content blur), GlassBottomBar dock,
frosted chips/home/player panels, richer ambient wash, FullPlayer sheet handle.

## 2026-09-05 — M3 UI overhaul (Home depth + sans type)

**User brief:** Expert M3 overhaul — readable sans, deep Home, M3 surfaces,
ambient art wash, immersive player (already ADR-002), quick-action chips.
Lightweight but not boring. No Liquid Glass.

**Shipped:** typography lock, Home chips + classified shelves, M3 shapes/
surfaces/nav, shell ambient, sleep timer.

## 2026-09-05 — Phase 14 audio-segment cache (Android)

**Shipped:** Media3 SimpleCache LRU (`DhunAudioSegmentCache`), wired in
`PlaybackGraph` with stable video-id keys; offline span replay when
resolve fails; budget `CACHE_SIZE_MB` default 1 GiB.

**Not claimed:** HW offline proof, desktop cache, soaks, v0.1.0, residential
extraction (still CDN 403 on Actions).

## 2026-09-05 — ADR-002 M3 polish + Recovering (no Liquid Glass)

**User:** No Liquid Glass; Material 3 OK; plan + execute.

**Shipped:**
- `PlaybackState.Recovering` / `StreamRecoverySignal` / PlaybackGraph 403 → chip
- FullPlayer lyrics-dominant (Lyrics tab) + BlurredArtworkCache
- ADR-002 hardened M3-only

**Not claimed done:** residential stream, hardware 08/11, soaks, v0.1.0.
Live drill still 33970045379 URL→CDN 403.

Format: date · title · symptom (with stack where available) · root cause ·
fix · verification state. Newest first. If you hit one of these again,
read this entry before re-diagnosing.

---

## 2026-09-05 · rot-drill 33970045379 — expanded chain got googlevideo URL, CDN 403 on bytes

**Ref:** `d9f4083` on `arena/01a07170-dhun`.  
**Evidence:** `PROBE|resolve+stream|FAIL|IOException: ... 403 ... googlevideo.com/videoplayback...itag=251`.  
**Meaning:** client_client list produced a URL; Actions IP cannot fetch media bytes (category 8 CDN gate). Own-client WATCH `Unavailable`. Still not a green drill; still not a reason to skip byte checks or add cookies without ADR.

**Next:** residential smoke; optional nsig research; ADR-002 player polish only after one real play.

---

## 2026-09-05 · proper fix after 33968950214 — expand tokenless client chain (no probe mask)

**Trigger:** User confirmed the job diagnosis: all three playback paths
broken with AuthRequired / NewPipe Parse; asked for a proper fix on a
branch, not masking the failing probe. Session is pinned to
`arena/01a07170-dhun` (no new branch).

**What we will NOT do:** cookies, PO tokens, attestation spoofing,
skipping stream-byte checks, converting CI red into a synthetic pass.

**What we will do (code):**
1. `OwnClientStreamResolver` — 7-identity chain from yt-dlp master
   INNERTUBE_CLIENTS: web_embedded (thirdParty.embedUrl) → visionos → tv →
   tv_downgraded → tv_simply → mweb → web_remix. ANDROID/IOS still out.
2. `YtDlpStreamResolver` — explicit
   `youtube:player_client=web_embedded,tv,tv_downgraded,tv_simply,mweb,web_safari,android`
   instead of default-only path that 33968950214 showed gated.
3. ADR-001 addendum 2026-09-05; KNOWN_LIMITATIONS; setup-java@v5 bump
   (Node 20 deprecation noise only).

**Verification:** CI compile/tests on PR #16; live rot-drill re-dispatch
on this branch (agent cannot dispatch). PASS only if real audio bytes
verify. FAIL with fuller per-client detail is still an honest category-8
result.

---

## 2026-09-05 · rot-drill run 33968950214 — fixed branch LIVE; both engines CI-IP gated

**Run:** https://github.com/99ggprooo00-code/DHUN/actions/runs/33968950214  
**Ref:** `arena/01a07170-dhun` @ `10ad025` (correct branch — first time)  
**Conclusion:** failure via intentional kill switch after alert.

**Probe evidence (from issue #14 comment, artifact `rot-drill-33968950214`):**
```
yt-dlp 2026.08.19
PROBE|version|PASS|WEB_REMIX 1.20260901.12.00
PROBE|search|PASS|20 music-song results
WATCH|own-client|BROKEN|AuthRequired(web_remix/visionos/tv all AUTH_REQUIRED
  Sign in to confirm you're not a bot)
WATCH|ytdlp|BROKEN|AuthRequired(ERROR: [youtube] utwMHfDZ6SA: Sign in to
  confirm you're not a bot. Use --cookies-from-browser or --cookies ...)
PROBE|resolve+stream|FAIL|resolve via resolving(own-innertube-player -> yt-dlp)
PROBE|related|PASS|50 related tracks
WATCH|newpipe-stream|BROKEN|Parse(JSON response is too short)
PROBE|verdict|FAIL|extraction-pipeline-broken
```

**Root cause:** Category **8 — YouTube datacenter-IP bot gating** of the
player endpoint from the Actions runner. Now confirmed against the
**production** chain: Android's only engine (own-client, all three
strategies) and desktop primary+fallback (own-client + yt-dlp) are all
gated. Metadata endpoints still work from the same IP. Not extractor-shape
rot; not a workflow bug; kill switch correct.

**Fixes verified live (vs main@a554594 runs):** production chain gate,
per-engine WATCH lines, AuthRequired.detail populated, artifact name in
issue body, yt-dlp version in artifact.

**Minor defect found:** WATCH/resolve used Kotlin `"$r.error"` which prints
`Failure(...).error` (receiver + literal). Fix: `"${r.error}"`.

**Policy (do not violate):**
- Do not weaken stream-byte verification or tolerate resolve failures to
  get a green CI drill.
- Do not add cookie/sign-in flows without ADR + user sign-off.
- Residential hardware is the next evidence gate for user-facing impact.
- CI red + residential green ⇒ record here; keep kill switch.

**Next:** push string-template fix; continue Phase 14 taxonomy/audio-cache
work; keep soaks/v0.1.0 open.

---

## 2026-09-05 · rot-drill run 33968612285 FAILED on main@a554594 — wrong ref, not a fix regression

**Symptom:** User saw step **"Fail the workflow after alerting" → `exit 1`**
and concluded nothing updated. Run:
https://github.com/99ggprooo00-code/DHUN/actions/runs/33968612285

**Root cause:** `workflow_dispatch` targeted **`main` @ `a554594`** (PR #13
merge), which still has the pre-fix probe (yt-dlp alone, `AuthRequired()`
with null detail, issue-body backtick bug). Probe output is a byte-for-byte
repeat of run 33961533965. The `exit 1` step is the intentional kill switch
after `steps.probe.outcome == failure` — not a new defect.

**Not the cause:** PR #16 / branch `arena/01a07170-dhun` code was never
checked out. Agent still cannot dispatch (`HTTP 403` on
`actions/workflows/.../dispatches`).

**Response:** Comment on issue #14 with the wrong-ref diagnosis. ROADMAP
CURRENT ACTIVE TASK updated. Next human action: dispatch rot-drill with
branch **`arena/01a07170-dhun`**, or merge PR #16 then re-run on main.

**Verification state:** PR #16 CI remains GREEN (`33967339900`). Live green
verdict still does not exist.

---

## 2026-09-05 · CI red on PR #16 (run 33967027211): NowPlayingPersistenceTest 15s timeout

**Symptom** (CI step "Unit tests — shared domain"):
```
kotlinx.coroutines.TimeoutCancellationException: Timed out waiting for 15000 ms
  @ NowPlayingPersistenceTest$eventually$2.invokeSuspend(NowPlayingPersistenceTest.kt:110)
Test failed: NowPlayingPersistenceTest.queueAndProgressArePersistedThenRestoredPaused
```
Same code tree was GREEN on PR #15 run `33963828155` (identical persistence
+ JDBC path) → load/timing flake, not a rot-drill regression.

**Root cause (two cooperating defects):**
1. **JDBC single-connection concurrency.** `JdbcSqliteDriver.IN_MEMORY` is
   one shared connection; every `SqlDelight*Repository` defaulted its `io`
   dispatcher to `Dispatchers.Default` (multi-threaded). On track start,
   `NowPlayingPersistence.onTrackChanged` does `recordPlay` (history write)
   then `snapshot` (nowPlaying write) while the queue collector also fires
   `snapshot`, and the progress loop may fire `updateProgress` — concurrent
   JDBC access can hang or drop the position row so
   `load()?.positionMs == 30_000` never becomes true.
2. **Test ordering.** The test set `positionMs = 30_000` *after*
   `prepareQueue`, so the first snapshots could persist `positionMs=0`; the
   test then depended solely on a later progress tick winning against the
   concurrent history write. `updateProgress` was also a pure `UPDATE … WHERE
   id = 1` — a no-op if the state row was not yet committed.

**Fix (this session, branch `arena/01a07170-dhun`):**
- `DataLayer` now builds one `Dispatchers.Default.limitedParallelism(1)` and
  hands it to every repository — one-connection SQLite is single-threaded
  at the app boundary (correct for JVM file DB and in-memory tests).
- `SqlDelightNowPlayingRepository.updateProgress` upserts state when the
  row is missing (late tick still lands the heard position).
- Test sets duration+position *before* `prepareQueue` and waits for the
  queue ids first, then the position — clearer failure mode.

**Verification:** push → require CI green on PR #16 (run after this commit).
Sandbox has no JDK so CI is the compile/test gate.

---

## 2026-09-05 · rot-drill run 33961533965 FAILED — first live drill red (yt-dlp bot-gated from CI IP)

**Symptom** (job 101295458477, step "Fail the workflow after alerting"):
`Process completed with exit code 1.` — that step is the INTENTIONAL
kill switch (`exit 1` when `steps.probe.outcome == 'failure'`), so it is
the alert, not the cause. Real failure output, from issue #14 (tail of the
run's `rot-drill.log` artifact):

```
PROBE|version|PASS|WEB_REMIX 1.20260901.12.00 (scraped from homepage HTML)
PROBE|search|PASS|20 music-song results
PROBE|resolve+stream|FAIL|IllegalStateException: resolve: AuthRequired(detail=null)
PROBE|related|PASS|50 related tracks
WATCH|newpipe-stream|BROKEN|Parse(detail=JSON response is too short)
PROBE|verdict|FAIL|extraction-pipeline-broken
```

**Root cause** (chain of evidence, no guessing):
1. `PROBE|resolve+stream` failed inside `YtDlpStreamResolver.resolve`:
   that mapping fires ONLY when yt-dlp's last non-blank stderr line contains
   "Sign in to confirm" — YouTube's bot-gate text
   (`JvmStreamResolvers.kt: message.contains("Sign in to confirm") →
   DhunError.AuthRequired()`).
2. CI installed yt-dlp **2026.08.19** (latest on PyPI today — verified from
   this sandbox), the exact version ADR-001 measured tokenless-working from
   a hostile datacenter IP on 2026-09-01 ⇒ not a version regression;
   YouTube tightened player-endpoint gating for the Actions runner IP class
   between 09-01 and 09-05.
3. Metadata endpoints kept working from the SAME runner in the SAME run
   (version scrape, 20 search results, 50 related) ⇒ player-endpoint
   gating, not a blanket IP block, not an InnerTube-shape break.
4. Classification: **YouTube/datacenter-IP bot blocking (category 8)**.
   Residential impact unproven either way — this is CI-network evidence.

**Aggravating defects found while diagnosing (all real, all to fix):**
- Probe misalignment: fatal step drove `YtDlpStreamResolver` ALONE (the
  desktop FALLBACK per ADR-001), never the production primary
  (`OwnClientStreamResolver`, the ONLY engine Android ships). The drill
  gated the verdict on an engine production uses second.
- Diagnostics loss: yt-dlp's stderr line was discarded when typing
  `AuthRequired` → printed `detail=null`, violating ADR-001's
  "detail carries the per-attempt evidence" contract. We had to infer the
  trigger text from the code path instead of reading it in the log.
- Workflow quoting bug: `` `rot-drill-${GITHUB_RUN_ID}` `` inside the
  double-quoted bash issue body executed as command substitution → issue
  #14 shows "The attached  artifact" with the name swallowed.
- Evidence gap: `yt-dlp --version` printed only to the step log; the
  `rot-drill.log` artifact starts at Gradle, so the artifact cannot prove
  which engine version ran.

**Environment traps re-confirmed this session:** this sandbox has no JDK
(CI is the compile gate) and YouTube egress is TLS-blocked here
(`yt-dlp` fails `TLS/SSL connection has been closed (EOF)`) — live
reproduction must happen on GitHub Actions, not locally.

**Fix (this session, small commits):**
1. `YtDlpStreamResolver`: carry yt-dlp's last stderr line into
   `AuthRequired(detail=…)` / `Unknown(causeMessage=…)` — typed errors and
   fail-loud behavior unchanged.
2. Probe `Main.kt`: fatal `resolve+stream` step now drives the REAL
   production chain (`ResolvingStreamResolver(OwnClientStreamResolver →
   YtDlpStreamResolver)`, identical to `forDesktop`), with new per-engine
   `WATCH` lines (`WATCH|own-client`, `WATCH|ytdlp`) alongside the existing
   NewPipe watch. NOT a weakening: audio bytes still HTTP-fetched and
   magic-byte-verified; both engines gated ⇒ verdict still FAIL.
3. `rot-drill.yml`: quote the issue body safely (no backtick execution),
   append `yt-dlp --version` into `rot-drill.log`.

**Verification state:** code fixes are CI-verified — PR #15, run
`33963002355` GREEN 2026-09-05 (shared unit tests, Android debug build,
probe compile; sandbox has no JDK so CI is the compile gate). Live rerun
still PENDING: the sandbox token cannot dispatch workflows
(`HTTP 403: Resource not accessible by integration`, re-confirmed) — the
drill must be re-dispatched from the GitHub UI on ref
`arena/01a07141-dhun` (exactly how run 33961533965 was dispatched) or left
to the 04:17 UTC cron after PR #15 merges. Green requires a real
`PROBE|verdict|PASS`; if the own-client tier is ALSO gated from CI, the
drill stays red (CI-network gating evidence — see the
"CI-network vs residential" note in KNOWN_LIMITATIONS) and residential
verification moves to real hardware.

---

## 2026-09-05 · Android FATAL: `MediaController method is called from a wrong thread`

**Symptom** (user-reported crash, artist page → shuffle play):

```
IllegalStateException: MediaController method is called from a wrong thread
    at androidx.media3.session.MediaController.verifyApplicationThread(MediaController.java:…)
    at androidx.media3.session.MediaController.setMediaItems(MediaController.java:…)
    at dev.dhun.android.playback.AndroidDhunPlayer.prepareQueue(AndroidDhunPlayer.kt:91)
    at dev.dhun.presentation.browse.ArtistViewModel$playTopSongsShuffled$1.invokeSuspend(ArtistViewModel.kt:70)
    … on Dispatchers.Default (StandaloneCoroutine Cancelling)
```

**Root cause:** the shared `DhunPlayer` interface is implemented on Android by
`AndroidDhunPlayer`, which holds the connected `MediaController`. Media3
enforces application-thread (main) access on **every** `MediaController`
method — setters, getters, `prepare()`, even `release()`. All ViewModels
(`ArtistViewModel`, `PlayerViewModel` play paths, `NowPlayingPersistence`)
run on `Dispatchers.Default` and call the player directly → any of them
crashes. The reported stack is only the first call to hit the check
(`setMediaItems` in `prepareQueue`); the 500 ms position poll and every
transport button are the same class of bug.

**Fix:** `AndroidDhunPlayer` now owns the threading invariant — callers are
untouched:
- `private val mainHandler = Handler(Looper.getMainLooper())` +
  `onMain { }` (inline when already on main, preserves FIFO order),
- every controller call (setMediaItems/addMediaItem/seekTo/play/pause/
  removeMediaItem/moveMediaItem/stop/prepare/getters/repeat/shuffle/volume)
  runs inside `onMain`,
- `prepareQueue` (suspend) uses `withContext(Dispatchers.Main)` so callers
  that chain calls (restore → seekTo) keep ordering,
- the 500 ms position poll is pinned `scope.launch(Dispatchers.Main)`,
- `refresh()` (StateFlow projection reading controller getters) and
  `release()` are marshalled the same way.

**Verification:** compile gate = CI (`:app-android:assembleDebug`); on-device
retest on the user's hardware (artist shuffle play no longer crashes;
background playback checklist `docs/verification/03-android-skeleton.md`).

**Rule for the future:** the `DhunPlayer` implementation for a process-bound
engine (ExoPlayer) does NOT need this — only `MediaController` (cross-process)
does. If a new Android player implementation wraps a controller, it MUST
marshal to main.

---

## 2026-09-05 · CI red #4 (run 33938679193, head a20165b): shared unit test — NOT the desktop code

**Symptom:** `:shared:jvmTest` failed; steps "Android debug build" and
"Probe compiles" skipped. Failing assertion
(`NowPlayingPersistenceTest.queueAndProgressArePersistedThenRestoredPaused`,
line 129): `expected:<[T1, T2, T3]> but was:<[T1, T2, …truncated…]>` — the
queue persisted right after `prepareQueue` didn't round-trip.

**Root cause:** race in `NowPlayingPersistence`. `prepareQueue` sets
`queue` and then `currentTrack`; both collectors fire `snapshot()` almost
simultaneously. Each `snapshot()` hops to `Dispatchers.Default` inside
`withContext(io)`, so two `saveQueue` transactions (clearQueue → insert 3
rows → upsertState) interleave on the DB. The test's in-memory driver is
`JdbcSqliteDriver(IN_MEMORY)` = ONE shared connection, so the interleaving
corrupts the queue rows. Same latent race exists in production (Android
activityScope is Main, but the same `withContext(Default)` hops make two
snapshots concurrent on the DB layer). The test was green on main and on
earlier PR commits — it's a load/timing flake, which is why CI runs 1–3
(red on the DESKTOP compile at step 6, see below) never surfaced it: step 4
never got reached as a failure.

**Fix:** `NowPlayingPersistence` serializes all now-playing writes with a
`Mutex` (`writeMutex.withLock` around `save(...)` and `save.progress(...)`).
Each `saveQueue` remains a single atomic `db.transaction`; serialization
removes the only hazardous interleaving (two queue-rewriting transactions).

**Verification:** CI run for the fix push (step 4 must pass; then step 6
finally compile-checks the desktop round-3 code — first real signal for it).

---

## 2026-09-05 · CI reds #1–#3 (runs 33887658349 / 33928843140 / 33930616806): Compose Desktop 1.8.2 API

All three failed at CI step 6 "Probe compiles" — `:tools:playback-probe`
**chains the desktop compile** in CI only (`if (System.getenv("GITHUB_ACTIONS") == "true")` →
`dependsOn(":app-desktop:compileKotlinJvm")` in its build.gradle.kts; ci.yml
has no desktop step by policy of the time). So "desktop compile errors"
appear under a step named "Probe compiles". Remember that mapping.

The 1.8.2 desktop Window API is NOT what 1.9+/2.x docs show. Verified from
source: `JetBrains/compose-multiplatform-core` tag `v1.8.2`
(`compose/ui/ui/src/desktopMain/kotlin/androidx/compose/ui/window/…`).

| Do NOT use (1.8.2) | Use instead (1.8.2) |
|---|---|
| `windowScope` parameter | content lambda receiver is implicit `FrameWindowScope`; `window` = `ComposeWindow : JFrame` (public AWT) |
| `LocalWindow` / `LocalComposeWindow` | **internal** in 1.8.2 — use `FrameWindowScope.window` or AWT `Frame.getFrames()` title lookup |
| `rememberWindowState(position = Offset)` | `position: WindowPosition` (`WindowPosition(x.dp, y.dp)` = Absolute) |
| `Float.px` | does not exist — convert via density where needed |
| `skipTaskbar` param | absent in 1.8.2 (mini-player shows in taskbar — KNOWN_LIMITATIONS) |
| `Key.LEFT` / `Key.RIGHT` | `Key.DirectionLeft` / `Key.DirectionRight` |
| `Key.Space` | `Key.Spacebar` |
| `KeyEvent.type` "unresolved" (round 3) | cascade of the unresolved `Window(...)` call — `type`/`isCtrlPressed` DO exist |

Round-3 fix `a20165b` was source-verified against the above; its CI
verification was blocked by the test race (previous entry) — confirmed only
when a CI run reaches step 6 green.

Round 4 (run 33943041377 on `4602d9d`) finally reached the desktop module
and exposed 8 more — all small API/type mixups:
- `Main.kt` `rememberWindowState(width/height/position=…)`: **`Long.dp`
  does not exist** (Int/Float/Double do) — the persisted geometry is `Long`
  (px) → convert with `.toFloat()` before `.dp`. (miniState with Int args
  compiled fine — the failing args were exactly the Long-derived ones.)
- `TrayIcons.kt`: `0xFF161616` / `0xFFBB86FC` are **Long literals** in
  Kotlin (> Int.MAX) → `Color(Int)` mismatch → `.toInt()`.
  `(s * 0.12f).coerceAtLeast(1)` — Float receiver got an Int →
  `.toInt().coerceAtLeast(1)`.
- `DhunTray.kt`: `java.awt.MenuItem` has **`label`**, not `text`
  (getLabel/setLabel); and an `inline` lambda passed to
  `SwingUtilities.invokeLater` needs **`noinline`**.

Also settled from the v1.8.2 source (use as reference): `application { }`
**IS a composable context** (KDoc: `fun main() = application { Window … }`),
and the `Window` overload with `undecorated: Boolean = false` has defaults
for every parameter (state/title/resizable/alwaysOnTop/onKeyEvent/content
all named-safe).

Round 5 (run 33944244828 on `21201fc`): Main/DhunTray/TrayIcons all
compiled; errors moved to `Smct.kt` (JNA):
- `com.sun.jna.platform.win32.GUID` **does not exist** (the platform GUID
  is nested in `WinNT`) — the stable home is base-jna
  `com.sun.jna.win32.Guid.GUID` (Data1 int / Data2 short / Data3 short /
  Data4 byte[8]) → added a `guidFromIid()` byte converter (Win32 GUID =
  first 3 fields LE, last 8 bytes as-is; verified against the
  ddb0472d-… bytes by hand).
- jna-platform (User32) was only on the classpath **transitively** (via
  vlcj) — declared `jna-platform:5.17.0` explicitly + THIRD_PARTY line.
- JNA `Memory` constructor takes **long** — `Memory(Native.POINTER_SIZE)`
  (Int const) rejected → `.toLong()` / `4L`.
- RUNTIME caveat (not a compile issue, machine-verify): raw
  `Function.invoke` marshals Structure args **by reference**, so the
  REFIID inside `vtableCall` may need by-value marshaling on a real
  machine. The probe is failure-isolated (logs HRESULT, never throws;
  documented fallback = tray path) so this can't break the app.

Round 6 (run 33944782718 on `a47fd84`): `com.sun.jna.win32.Guid` STILL
unresolved (import line!) and `User32.FindWindowW` / `User32.RECT`
unresolved too — while the User32 *import* resolved and base-jna
Function/Memory/Native all resolved. Conclusion: **the exact win32
helper classes of the JNA artifacts are version/artifact-split fragile;
do not build DHUN interop on them.** Rewrote `Smct.kt` fully
self-contained on base JNA: own `WinGuid`/`WinRect` `Structure`s
(explicit `getFieldOrder`), own `Native.load("user32")` interface with
`Pointer`-typed HWND params, SWP_* as local consts. Dropped the
jna-platform direct dep again (nothing references it; vlcj still pulls
it transitively for itself).

Rounds 7–9 (runs 33945300702 → 33945909159/33946130860 → 33946527454
GREEN): the JNA rewrite held; the rest was small 1.8.2/JNA-5.17 API
graining — **and the first lesson is process, not code:**
- **Check-run annotations are capped at 10 per run** (8 are consumed by
  Gradle's own build-failure annotations). In run 33945300702 the
  visible 10 hid 12+ real errors: `DesktopHarnessScreen.kt` had 12
  `Color(0xFF…)` literals — every 0xFFxx value > Int.MAX = Long →
  `Color(Int)` mismatch. Rule: when a desktop file is untested,
  pre-scan it for the known hazard classes (Long hex literals,
  missing dp extensions) instead of waiting for the capped list.
- JNA 5.17: `Pointer.getPointer(offset)` takes a **long** offset;
  `Function.invokeInt(Object[])` is NOT vararg (no `*all` spread);
  `Pointer` has **no `toLong()`** — log via `toString()`, pass the
  `Pointer` itself as the HWND arg.
- Compose 1.8.2: `awaitFirstDown()` takes **no parameters**
  (`requireCapture` is a later version); `PointerInputChange` has no
  `press()` — use `consume()`.
- vlcj 4.8.2 (javadoc-verified): the track-ended event is
  `MediaPlayerEventAdapter.finished(MediaPlayer)` — NOT `ended`.
- MiniPlayerWindow.kt: `ArtworkImage` lives in
  `dev.dhun.design.components`, and **`edit_file` can silently not
  persist in this sandbox** — verify edits with grep after applying;
  sed/perl/python are the reliable hammers.
- Also fixed while in the file (runtime correctness, machine-verify):
  `RoGetActivationFactory` takes an **HSTRING handle** — built with
  `WindowsCreateString`/freed with `WindowsDeleteString`; `FindWindowW`
  takes `WString` (wide).

Final state: run `33946527454` on `3cd4bf8` = **first fully green CI**
(shared tests, android assembleDebug, probe + desktop compile). PR #9
merged @ `697cf54`.

---

## 2026-09-05 · Background playback killed by OEM battery savers (MIUI/HyperOS/OneUI)

**Symptom (class of report):** music stops when the phone is locked / app
swiped away; OEM devices kill the playback service within minutes.

**Root cause (code):** `DhunPlaybackService` (MediaSessionService) never
called `MediaSession.startForeground(...)` — no foreground status, no
media notification. A background-only service is exactly what MIUI
cleaners, OneUI "battery saver" and HyperOS kill first. The manifest
already had `FOREGROUND_SERVICE(_MEDIA_PLAYBACK)` + `mediaPlayback` type —
the runtime call was missing.

**Fix:**
- `DhunPlaybackService.onCreate` → plain `Service.startForeground(1,
  notification)` — with `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`
  on API 29+ (targetSdk 35 REQUIRES the type on Android 14+). The
  notification uses `NotificationCompat` +
  `MediaStyleNotificationHelper.MediaStyle(session)` (the media3 1.5.x
  helper's NESTED MediaStyle bound to the session — the system drives the
  transport state from it) + `setShowActionsInCompactView(0,1,2)`; a
  Player.Listener re-posts on track transition / play-state change so
  title/artwork stay live. Channel `dhun_playback`, `ic_notification`
  vector (minSdk 26 → vectors are fine).

**API correction (learned the hard way, do NOT repeat):** in media3 **1.5.1**
there is NO `MediaSession.startForeground(...)`, NO `MediaSession.sessionId`,
and NO static `MediaStyleNotificationHelper.createNotification(session,
icon, intent)` — first attempt failed CI compile on exactly those (run
33941799559). The real pattern is Service.startForeground + the nested
`MediaStyleNotificationHelper.MediaStyle(session)`. Second compile round
(run 33942371150) — smaller mixups: `NotificationCompat.CATEGORY_MEDIA`
does not exist, `MediaMetadata.artworkData` is a `ByteArray` (no
`toBitmap()` — use `BitmapFactory.decodeByteArray(data, 0, data.size)`).
Third round (run 33942622916): `android.app.Notification.CATEGORY_MEDIA`
**also does not exist** — the framework has no media category constant at
all (setCategory removed; MediaStyle + the mediaPlayback FGS type carry
the semantics). Lesson, repeated twice: **verify small API surfaces
against the compiler, not memory.**
- `MainActivity.attach()` → one-shot (per process) system dialog via
  `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + `package:` URI
  (needs `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission, added to
  manifest). Asked only while the app is in use.
- ExoPlayer already holds a partial wake lock (`C.WAKE_MODE_LOCAL`) and
  handles audio-focus / becoming-noisy — unchanged.

**Still manual (cannot be requested programmatically — put on the user's
checklist, `docs/verification/03-android-skeleton.md`):** MIUI/HyperOS
"auto-start" + "lock in recent apps" per-app switches; OneUI battery
settings "unrestricted".

**Verification:** on-device — play → lock → 30 min → audio continues;
`adb shell dumpsys activity services dev.dhun.android | grep fg` shows
foreground + mediaPlayback.

---

## 2026-09-05 · SMTC status (desktop media keys / now-playing tile)

Phase 1 (spike) code is in: `desktop/Smct.kt` — WinRT activation via JNA/
combase from the unpackaged JVM → `ISystemMediaTransportControlsInterop` →
`GetForWindow` → `IsTransportControlsButtonVisible` live check; HRESULTs
logged at startup (`SMTC probe PASS/FAIL — …` console line), off-switch
`-Ddhun.smct=false`.

Phase 2 (`UpdateMetadata` + `ButtonPressed` events, i.e. the actual tile +
media-key handling) needs two interface IIDs pulled **on a Windows
machine** from the system winmd — procedure in
`docs/verification/12-desktop-native.md`. Until then hardware media keys do
NOT drive DHUN; the shipping fallback is tray menu + keyboard shortcuts +
mini-player (documented in `.ai/KNOWN_LIMITATIONS.md`).

---

## Environment traps (this sandbox + CI mechanics) — recurring

- **No JDK / Android SDK / adb / display** in the sandbox. CI is the
  compile gate. Never claim "compiles" locally — claim "source-verified
  against <source of the API>", and let CI confirm.
- **Sandbox resets the repo between user turns** (re-clone to the merge
  base `d27eb37`; working tree survives, local commits/objects can be
  wiped). After every turn start: `git log --oneline` + `git fetch origin`
  + compare local vs remote head; unpushed commits must be re-pushed or
  they are gone (commit `04f00eb` was lost once this way and recreated as
  `c808819`).
- **GitHub token flaps mid-session.** `git push` / `gh` fail with
  "could not read Username" / 401 intermittently, then work again. Retry
  a few times; if persistently failing, the user must reconnect GitHub in
  Arena.
- **CI logs are unreachable** from this sandbox (`gh run view --log-failed`
  → results-receiver EOF). Use the annotations API instead:
  `gh api repos/99ggprooo00-code/DHUN/check-runs/<job-id>/annotations`
  (job id from `gh run view <run> --json jobs`). Annotation messages are
  first-line / truncated — enough for compile errors, too short for full
  test output.
- **Egress:** direct `curl` mostly blocked (lrclib 35, maven 000);
  `fetch_page` works; `raw.githubusercontent.com` works for some repos
  (compose-multiplatform-core: yes; androidx/media3 tag paths: 404 —
  don't retry). `git` egress works only for the DHUN repo (session token).
- **Stack is KMP/Gradle, not npm/Tauri.** Desktop packaging = jpackage
  (`:app-desktop:createMsi`, Windows-only host). Any "npm run build /
  Tauri installer" instruction adapts to that — do NOT create npm tooling.
- **Desktop compile in CI runs under the "Probe compiles" step** (probe
  module chains it, GITHUB_ACTIONS-guarded) — see reds #1–#3 above.
- **Rolling test release policy (user decision 2026-09-01):** exactly one
  release tagged `test`, replaced on every push to main; assets
  `dhun-test.apk` (+ `.sha256`) and — since 2026-09-05 — `dhun-test.msi`
  (+ `.sha256`). No version numbers, no history for unfinished builds.
  Stable URLs: `/releases/download/test/dhun-test.apk`, `…/dhun-test.msi`.

## 2026-09-05 · Phase 13 Android native polish compile gate

**Implementation:** `8669e09` adds edge-to-edge setup and safe-drawing insets,
static launcher shortcuts, saved navigation state, battery-exemption rationale,
and an 840dp shared navigation rail. `c2a86df` was an intermediate attempt to
reuse the bottom navigation item helper.

**CI failure:** run `33958722933` reached `:shared:compileKotlinJvm` and
reported `Unresolved reference 'NavigationBarItem'` plus two composable-context
errors at `DhunAppShell.kt:394-398`. Material3's `NavigationBarItem` is a
`RowScope` extension; the helper had been moved outside the `NavigationBar`
content scope. An import alias did not fix receiver resolution (`33958802810`
reproduced the same error).

**Fix:** `4de9795` splits the shared rendering into `RowScope.AppBottomNavigationItem`
and `ColumnScope.AppRailNavigationItem`, leaving each Material3 item in its
required layout scope. CI run `33958894084` passed shared tests, Android debug
build, and the Desktop/probe compilation.

**Environment:** local `./gradlew` remains blocked by the sandbox's missing
`JAVA_HOME`/`java`; CI is the compile gate. Device rotation, gesture-nav,
shortcut launcher, battery/OEM, and 30-minute playback soak evidence remain
open and must not be inferred from CI.

## 2026-09-05 · Phase 14 rot-drill dispatch gate

**Attempt:** After pushing `5897c5c` + `5573f9a`, a manual dispatch was
requested with `gh workflow run rot-drill.yml --ref arena/01a070b3-dhun`.

**Result:** GitHub returned `HTTP 403: Resource not accessible by integration`.
The authenticated GitHub bot is valid and PR CI is green, but the workflow is
not on the default branch yet; `gh workflow view rot-drill.yml` still shows
the old placeholder definition from `main`. No live probe verdict or issue
alert/recovery exercise can be claimed from this attempt.

**Next action:** Keep the workflow code staged and run it once the workflow is
available on the default branch or Actions dispatch permission is restored.
Until then, the Phase 14 rot-drill step remains open; do not mark it green
because the YAML has not been live-executed.

**Workflow review fix:** `29326cc` removes `cache: pip` from
`actions/setup-python@v5`; this repository has no requirements file, so the
rot-drill must install `yt-dlp` directly without asking the action to resolve a
missing cache dependency. The live workflow remains unexecuted because the
manual dispatch is still blocked by the GitHub 403 above.
