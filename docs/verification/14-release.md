# Phase 14 verification — Robustness, Rot-Drill, Release

> **Current status (2026-10-05 — S3 hardware round 1).** PR #119 is merged as
> `73b88b60b647120662812a5d33b233876acad283`; post-merge CI **36467187148**,
> Build APK **36467187128**, and test-release **36467187181** passed. Rolling
> `test` metadata points to `73b88b6`, published **2026-09-28T18:48:23Z**
> (APK 18,367,219 B; MSI 112,934,912 B; ProductVersion 2.137.1). The #119
> SHA-256 values have **not** been independently verified in this sandbox; the
> hashes previously repeated from #118 were not established for this build.
> Do not treat the user's reported install as verified `73b88b6` until its
> computed hashes are compared with the matching release sidecars.
>
> **User-reported S3 results (report received 2026-10-05; execution date not
> supplied):** Android marked 1, 6, 7, 9 fail; 2–5, 8, 10–12, 14, 17 were
> reported pass, with qualifications; 13/15 are partial because `Go to album`
> was not found and only five actions were tried; 16 was not understood; 18
> was not reported. Windows core loop 10–13 was reported pass. This is partial
> device evidence, **not S3 acceptance**. Device models, OS versions, prior
> builds and test hashes were not provided. Four Android screenshots were subsequently embedded and visually reviewed;
> the exact failed-step mapping remains unknown. Album views show generic metadata
> and placeholder artwork; playlist views show row art but do not explain the
> reported row failure. Details and limits are in the report ledger below.
> S3/S6 soaks, native Windows checks and other release gates remain open.

> **Historical status snapshot (2026-09-28, session `arena/01a0e7ee-dhun` — PR #118 pre-merge).**
> The release baseline is **`main@5bbb16d`** (PR #117, merged
> 2026-09-28T10:50:31Z). Post-merge CI green: CI **36412080952**, Build APK
> **36412080763**, test-release **36412080887** (`apk` + `msi` + `publish`).
> Rolling `test` targets exactly `5bbb16da5d8cee3dce3d6874425e3e4caa433766`
> (`target_commitish` read from the release API this session), published
> **2026-09-28T10:56:10Z**: `dhun-test.apk` **18,367,219 B** (sha256
> `864d41c4bb8cb02b283a02c716af3da5460786b74b9c646d64872a3c8c92d0de`),
> `dhun-test.msi` **112,934,912 B**, ProductVersion **2.134.1** (sha256
> `d0c74087a290a72507c9579c129cbcf8e1bfe88600673bf3446d388034ac0c12`) — both
> from the publisher annotations on run 36412080887, re-read independently of
> the PR #117 comment. `.sha256` sidecars exist but the asset host EOFs
> in-sandbox, so they could not be fetched here. The publish job carries only
> the standing Ubuntu-26 notice: PR #116's `download-artifact@v7` bump still
> holds (zero Node-20 annotations). Intermediate baselines: `935e068`
> (PR #116, merged 2026-09-28T07:16:51Z; APK sha256 `bef638ca…f7a3f`, MSI
> 2.131.1 sha256 `8c435936…ce00`), `dcdd41b` (PR #115), `edf19e0` (PR #114).
> **PR #118** (two S5 leftovers: the offline-broadcast verdict + the retry-budget
> refund) code head `614134b` is green: push CI **36422490521**, PR CI
> **36422590223** (12/12 steps), Build APK **36422590089**, test-release
> **36422590222** (`apk` + `msi` incl. the hosted Windows install-over
> 2.134.1 → 2.135.1 with the userdata/cache sentinels preserved,
> `buildOnly=true`; `aab` / `publish` / `release_draft` skipped because they are
> `main`-gated). Its merge republishes again — quote the post-merge release API
> or the PR #118 comment as the download identity, not this paragraph. Drill:
> **the 2026-09-28 run fired** — 36412874929 at 10:58:41Z on `5bbb16d`,
> `ENVIRONMENT_BLOCKED` (see the live evidence log); daily since 09-22, all
> `ENVIRONMENT_BLOCKED`. This is compile, unit and packaging evidence only. It
> closes no S3 checklist and signs no soak.
>
> **Previous status (2026-09-28, session `arena/01a0e6c8-dhun` — PR #116 pre-merge; retained).**
> The release baseline is **`main@dcdd41b`** (PR #115, merged
> 2026-09-28T06:46:14Z). Post-merge CI green: CI **36388115963**, Build APK
> **36388115954**, test-release **36388116013**. Rolling `test` targets exactly
> `dcdd41b`, published **2026-09-28T06:51:17Z**: `dhun-test.apk` **18,367,219 B**
> (sha256 `9857a3fe071cb3b2a2e2408046342eba05b5eebb79c041fc8c94681d03eab7ef`),
> `dhun-test.msi` **112,934,912 B**, ProductVersion **2.128.1** (sha256
> `69e42993fdfb263a55f11988173699c233318243f23652235cf3906e5cb5d2ab`), from the
> publisher annotations. Intermediate baseline: `edf19e0` (PR #114, merged
> 2026-09-22T06:10:44Z; post-merge CI 35693686731 / 35693686689 / 35693686796
> green; APK sha256 `75ed9fbb…04e7`, MSI 2.124.1 sha256 `c95b004f…f5a3`).
> **PR #116** (endless-radio stale-probe fix + `download-artifact@v7`) code head
> `fbf69bb` is green: push CI **36389230165**, PR CI **36389248363**, Build APK
> **36389248349**, test-release **36389248359** (apk + msi incl. hosted
> install-over; `aab` / `publish` / `release_draft` skipped because they are
> `main`-gated). Its merge republishes again. Quote the post-merge release API
> or the PR #116 comment as the download identity, not this paragraph. Drill:
> daily runs 09-22 → 09-27, all `ENVIRONMENT_BLOCKED` (see the live evidence
> log). The previous session's "did not fire" note was cron latency. This is
> compile, unit and packaging evidence only. It closes no S3 checklist and
> signs no soak.
>
> **Previous status (2026-09-22, session `arena/01a0c716-dhun` — PR #113 pre-merge; retained).**
> The release baseline is **`main@7fcadbe`** (PR #111, S2 CI hygiene, merged).
> Post-merge CI on that SHA is green: CI **35681131215**, Build APK
> **35681131195**, test-release **35681131229**. Rolling `test` targets exactly
> `7fcadbe`, published **2026-09-22T02:58:26Z**: `dhun-test.apk` **18,350,835 B**,
> `dhun-test.msi` **112,914,432 B**, both `.sha256` sidecars present (hashes not
> re-read; asset blobs EOF here). **That download does not contain PR #113.**
> PR #113's merge will republish again — quote the post-merge release API, not
> this paragraph, as the download identity. The 2026-09-22 04:17 UTC drill had
> not appeared at this writing; the latest schedule is still **35561269411**
> (2026-09-21, `414cd79`, `ENVIRONMENT_BLOCKED`). Earlier baselines: `0d83216`
> (PR #110), `500b6a8` (PR #109), `d99060e` (PR #96 — **the S1 user-evidence
> build**), `7304abb`, `fabeb5f`, `39b8748`, `6f7fa48`.
>
> **Pre-merge verification — PR #113, code head `d2a9045` (this session).**
> Glass restyle `4166633` + scrim follow-up `d2a9045`, based on `main@7fcadbe`.
> Watched to completion, all success: push CI **35685053236** (5m2s), PR CI
> **35685055740** (5m44s), Build APK **35685055672** (2m33s), test-release
> **35685055772** (apk 2m49s, msi 3m40s including hosted install-over/userdata).
> `aab` / `publish` / `release_draft` skipped — `main`-gated. Suites that ran:
> shared domain, Android Robolectric, Android debug, probes, extraction-health
> classification, desktop compile, desktop JVM. No local JDK. This is compile,
> unit and packaging evidence only — not a visual or playback acceptance.
> Merge records the glass/scrim change and that CI; it does not close S3, sign
> a soak, or certify the look on a device.
>
> **Stage S1 closed GREEN on 2026-09-20 (retained record below).** The S1 evidence
> paragraphs that follow describe the `d99060e` build and remain the residential
> playback record; Gates 2–7 accounting below still stands (S3 checklists, soaks,
> clean installs, signing, final docs review, user go-ahead).
>
> **S1 acceptance is MET — the first live user playback evidence in this ledger.**
> On 2026-09-20 the user downloaded this exact rolling build over **home WiFi (no
> VPN)** and exercised it on **both** platforms: install and uninstall easy on
> Android and Windows; searched a song; **4 songs played with audible sound and
> advancing position; zero failures**; on Android **audio continued with the screen
> locked**. No `Playback details` text exists because nothing failed. Build identity
> was confirmed against the release API rather than a SHA on the page (the page shows
> none): reported "≈6 h ago, 10 pm" = published 16:46:20Z = **22:16 IST**; reported
> "17 MB"/"108 MB" = **17,948,508 B** (17.1 MiB) / **112,861,184 B** (107.6 MiB).
>
> **Interpretation of the drill, now settled.** Scheduled runs **35421383687**
> (09-19) and **35489268023** (09-20) classified **`ENVIRONMENT_BLOCKED`** on
> `main@6f7fa48`. `gh api compare/6f7fa48...d99060e` returns **9 changed files, all
> documentation, 0 source files** — so the runner block and the user's audible
> playback are the *same extraction code* on two different networks. The block is
> datacenter IP gating, not extraction rot; contingency trigger **T1 is disproven**
> and ADR-007 stays PROPOSED/unimplemented. The next scheduled run on `d99060e`
> (2026-09-21 04:17 UTC) is expected to remain `ENVIRONMENT_BLOCKED` — a known-correct
> runner result.
>
> **Release-gate accounting.** Gate 1 (S1 live verdict) is **satisfied**; re-confirm
> only if extraction code changes before the tag — docs-only merges do not invalidate
> it. Gates 2–7 remain open: S3 device checklists, 30-minute soaks, clean-target
> installs of APK + AAB + MSI, signing decisions, final CHANGELOG/README review, and
> the user's go-ahead. Four ~2-minute tracks are **not** a soak, and the following
> were not exercised: media-notification/lock-screen **controls**, rotation/process
> death, downloads + offline, lyrics, Settings/theme persistence, Android EQ, and the
> Windows native column (single-instance, tray, close-to-tray, jump lists, media
> keys/SMTC). Windows is confirmed to install and play only.
>
> The Home continuation request-contract repair is on `main`; Android and
> Windows/Desktop production paths are unchanged. All non-PASS drill statuses remain
> non-zero and only `FAIL` opens a rot-drill issue. Raw GitHub log/artifact blobs
> still return `EOF` in the agent sandbox (annotations API is the readout), and local
> Gradle cannot run without a JDK.

## S3 hardware round 1 — user report ledger (received 2026-10-05)

**Evidence qualification.** The report did not include the device-run date/time
in text, Android model/version, Windows build, previously installed build, or
APK/MSI SHA-256 values. Four Android PNG screenshots were embedded in the
follow-up message and visually reviewed:
`Screenshot_2026-10-05-18-13-30-669_dev.dhun.android.png`,
`Screenshot_2026-10-05-18-14-46-908_dev.dhun.android.png`,
`Screenshot_2026-10-05-18-10-32-018_dev.dhun.android.png`, and
`Screenshot_2026-10-05-18-16-02-116_dev.dhun.android.png`. Their filenames
suggest capture times on 2026-10-05, but timezone and step mapping were not
specified. Initial screenshot review interpreted two playlist views as showing
per-track cover thumbnails (one on a plain dark page, one with a blurred backdrop
while `Parvati` is playing). The user's latest clarification distinguishes the
failures: single-release pages lose both cover and track art, while other album
and playlist pages lose only the page-level cover and their row thumbnails mostly
load. The images alone cannot confirm the loaded state of each tile, explain the
step-6 size/shape mismatch, or establish which artwork supplies the second
backdrop. Two album views show the header title/subtitle falling back to `Album`,
placeholder cover art, and an `Unknown artist` track row. One has no
active player and a flat dark page; the other has `Nazm Nazm` by Arko playing
while a blurred background is visible behind the generic album page. These
images support the reported missing album-page metadata/art symptoms, but do
not identify the album, route or browse response. None shows steps 5, 10 or the
⋮ menu. They still do not establish the exact artifact/device configuration.

### Android — 18-step walkthrough

| Step | Reported result | Detail / limitation |
|---|---|---|
| 1 | **FAIL** | Album track-row artwork check failed. The album screenshots show placeholder art on the row(s), but the album/route is unidentified and the exact source response is unavailable. |
| 2 | PASS | Full player artwork reported present. |
| 3 | PASS | Mini-player artwork reported present. |
| 4 | PASS | Home/Search/Library shell backdrop reported correct. |
| 5a | Art present; **small** | Notification shade; attachment-to-step mapping unavailable. Procedure asks for a screenshot. |
| 5b | Art present; **small** | Lock-screen media surface; attachment-to-step mapping unavailable. |
| 5c | Art present; **small** | Home-screen widget; attachment-to-step mapping unavailable. User suggested larger thumbnails or a dynamically blurred current-song backdrop; this is UX feedback, not yet a scoped change. |
| 6 | **FAIL** | Playlist-row appearance check failed, but the row mismatch is still unspecified. The latest clarification says playlist and other-album page covers fail while their per-track row thumbnails mostly load; single-release rows may also lack art. A missing page cover alone does not explain step 6. The expected-versus-actual size/shape result remains missing. |
| 7 | **FAIL** | Idle album screenshot shows generic `Album` title/subtitle, placeholder cover and row art, and a flat dark page. The album image is absent or failed to load; generic labels are consistent with parser fallbacks when header fields are missing. Exact album/route and response are unknown, so the cause is unconfirmed. |
| 8 | PASS | Artist-page backdrop/scroll check reported pass. |
| 9 | **FAIL** | Screenshot shows a generic `Album` page with placeholder art while `Nazm Nazm` by Arko is playing and a blurred image remains behind the page. This is consistent with the page's own artwork being absent/unloaded and the shell's now-playing backdrop showing through; the exact album/route and cause are unknown. |
| 10 | PASS (readable) | Text on a white/pale-cover case was reported readable; album name and exact location were not supplied. The parenthetical “album cover not working” in the report is ambiguous and is not recorded as a separate confirmed failure. |
| 11 | PASS (user-reported) | One playlist image is flat/dark; another has a blurred background while `Parvati` is playing. The images do not identify local vs remote or prove whether the blur is the playlist's own art versus the shell fallback. |
| 12 | PASS with UX request | Compact menu reported present, but centered. User would prefer it anchored near the originating ⋮, toward the lower right. Current centered placement is intentional; anchoring is a separate design change. |
| 13 | **PARTIAL** | Labels otherwise marked pass, but `Go to album` was not found. Selected track/surface and album metadata were not specified, so absence is not yet classified as a defect or expected policy. |
| 14 | PASS | Tap-outside and Back dismissal both reported working. |
| 15 | **PARTIAL** | User reports all five actions they found worked; the sixth was not exercised/located. Exact five-action list was not supplied. |
| 16 | Not tested | User did not understand the check. The old wording was misleading: `Download` visibility is host-capability based (`DownloadManager` supplied), not per-track “downloadability.” The current Android and Windows builds provide a manager, so the hide case is not selectable in ordinary device testing; the policy is unit-tested. For album policy, check `Go to album` on a known album track (present/navigates) and on a track with no album metadata (absent). See corrected step 16 in `15-test-build-gate.md`. |
| 17 | PASS | Queue-row anchored dropdown reported pass. |
| 18 | Not reported | The seven-surface check was left blank; no conclusion recorded. |

**Menu feel / media presentation feedback:** the centered compact menu passed as
compact, but user requested a lower-right contextual placement. Notification,
lock-screen and widget artwork were present but considered too small; user
suggested increasing art size or using a blurred background that follows the
current song. The four reviewed images do not show notification/lock-screen/widget surfaces, so they cannot assess OS scaling or contrast.

### Windows — four core checks

| Step | Reported result | Detail / limitation |
|---|---|---|
| 10 | PASS | Shuffle queue check. |
| 11 | PASS | Radio check. |
| 12 | PASS | Playback controls and 10-minute tray check. |
| 13 | PASS | Download, disconnect network, and offline playback check. |

These are user-reported core-loop passes only; Windows build/device metadata
is absent and they do not close the separate Windows-native S3/S6 checklist.

**Disposition:** partial S3 evidence; do not mark the gate green. Follow up with
artifact hashes, device/OS/build details, screenshots and exact actual results
for Android 1, 6, 7, 9; verify `Go to album` with a known album-linked track;
and complete step 18. After symptom clarification, triage only the confirmed
S3 visual defects and re-test on a digest-verified build. The rolling release
hashes and S3 acceptance remain open.

### S3 round-2 remediation plan

**Follow-up symptom report (2026-10-05):** ordinary song thumbnails on
Home/Search load. The user reports that on “singles” both the page cover and
track art fail; tracks played from those pages also lack art in the full-screen
player. On other album and playlist pages, only the page-level cover fails while
per-track thumbnails mostly load. Album/playlist pages were opened from Home and
Search. This distribution points more strongly to missing or unrecognized
browse-page/track thumbnail data than to a global Coil/network failure, but a
single representative item and its raw response are not yet available. The
#119 tested artifact/device identity also remains unknown.

1. **Reproducibility/provenance.** The user installed the PR #120 candidate
   artifact from run **37322658878** and reports verifying its APK checksum.
   Device: Android 15 build `SQ3A.240829.003`; exact model and affected titles
   were not supplied. The older #119 rolling release is not the tested build.
2. **Research.** `parseAlbumPage` derives the cover with
   `thumbnailsLastUrl(header)` and passes it to rows as a fallback;
   `parsePlaylistPage` derives its cover from the header while preserving row
   art separately. Two-column browse responses can put
   `musicResponsiveHeaderRenderer` under the tab's section-list contents, while
   `pageHeader` originally checked only the top-level `header`.
3. **Candidate fix and retest.** `pageHeader` checks normalized tab sections and
   unwraps a nested `musicDetailHeaderRenderer.musicResponsiveHeaderRenderer`.
   Synthetic fixtures cover single-track fallback, playlist cover with
   independent row art, and nested headers. The user reports the single's page
   cover/rows/full player, another album cover, playlist cover/rows, and
   Home/Search thumbnails all work on the candidate. This is targeted device
   acceptance, not proof of the exact upstream payload/root cause: no user raw
   browse response was captured. If the symptom returns, inspect that sanitized
   payload before another parser change. Do not alter Coil or invent fallback
   art when upstream data is genuinely absent.
4. **Separate checks.** Step 6's expected playlist rows are 64dp borderless; the
   user says the row shape seems correct but supplied no measurement. `Go to
   album` also “seems working,” with no exact track ID/navigation result. Keep
   their broader checklist entries open/qualified, and keep menu anchoring plus
   notification/lock-screen/widget art-size preferences as separate UX scope.
5. **Merge-last.** The target artwork retest and CI/build workflows pass. Commit
   and push this verification record, wait for all PR checks on that head, then
   merge PR #120 last and verify post-merge CI/test-release in the same turn.
   This targeted pass does not close the full S3/S6 hardware checklist.

**PR #120 candidate verification and device retest (2026-10-05):** parser
commit `b7d0f01` is pushed on `arena/01a0eb87-dhun`; PR
[#120](https://github.com/99ggprooo00-code/DHUN/pull/120) is open. Initial PR CI
**37319510581** passed, including `:shared:jvmTest`, Android Robolectric/build,
probe and Desktop tests. Two intervening docs-only runs on head `5006595` had
unrelated test failures: push CI **37320618809** timed out in
`LibraryViewModelTest.historyPlaybackQueuesCorrectly`; PR CI **37320626452**
failed in `PlayerViewModelTest.endlessRadioDropsAPageFetchedForAQueueThatChangedMidFetch`
(expected `tok-2`, got `tok-a`). Later runs passed on heads `1332005` and
`edaf4b2`; app-code head `edaf4b2` passed push CI **37322646142**, PR CI
**37322658661**, Build APK **37322658940**, and test-release **37322658878**.
The candidate test-release APK/MSI and Windows install-over passed; AAB/publish
were skipped.

The `apk` artifact from
[test-release run 37322658878](https://github.com/99ggprooo00-code/DHUN/actions/runs/37322658878)
contains `dhun-test.apk`, `.sha256` and `.build-info.json`. APK: 18,367,219 bytes,
SHA-256 `c351341edbeaa7935c7a52ec096141d6d28dc18133000ff2bc00cf63473c5458`.
The downloaded ZIP is 17,552,363 bytes; this is expected compression, not a
size mismatch. User reports verifying the APK checksum and installing/testing
it on Android 15 build `SQ3A.240829.003` (device model not provided). Reported
PASS: single-release cover, track rows and full-screen player; another album's
cover; playlist cover/rows; Home/Search thumbnails. User clarified Home/Search
thumbnails were fine and the now-playing backdrop appears only during playback
as intended. Playlist step-6 row shape (expected 64dp, borderless) and `Go to
album` on an album-linked track are reported as “seems working”; exact row
measurement and album-track ID were not supplied. Record the targeted artwork
retest as **PASS by user report**; do not call all S3/S6 acceptance complete.
No user-captured raw browse response exists, so the precise upstream response
shape/root cause remains unconfirmed even though the candidate fixes the
observed symptoms. No rolling release was published before merge.

**Remaining verification boundary:** broader 18-step S3/S6 acceptance, Windows
native/soak results, step 18, exact device model, and other platform evidence
remain open. This targeted pass and the green PR checks are sufficient to merge
PR #120 under the user's request; do not claim all S3/S6 gates are closed.

## S1 evidence log (residential / on-device)

```text
- S1 2026-09-20: Android (user device) + Windows on main@d99060e — PASS — rolling `test`
  published 2026-09-20T16:46:20Z, APK 17,948,508 B / MSI 112,861,184 B, home WiFi no VPN;
  install+uninstall easy both platforms; 4 songs searched and played, audible, position
  advancing, no failures; Android audio continued with screen locked; no error text to
  capture. Scope not covered: soaks, media controls, offline, lyrics, EQ, Windows native.
```

## Pre-merge verification — 2026-09-18

- Candidate: PR **#91**, `arena/01a0b224-dhun@79d052b`, based on `main@33e94b0`.
- Code verification: CI **35326114136** passed all shared, Android, Desktop, probe-compilation, classifier, and packaging checks. Build APK **35326114122** passed. Test-release APK/MSI jobs in **35326114116** passed; publication was correctly skipped for the unmerged PR.
- Live-check boundary: extraction-health **35326110278** completed non-zero because the resolver remained `ENVIRONMENT_BLOCKED`. Its classifier passed and the rot-drill issue step was skipped; this is not evidence of a new DHUN/Home failure, but it is also not live playback acceptance.
- Merge disposition: the user explicitly authorized merging after this record. Merging records the request-contract repair and CI verification only; it does **not** close S1, certify live audio, or unblock S2.


## Phase 14 status snapshot — 2026-09-07 (retained history)

> Superseded by the current-status header at the top of this file: the merge
> chain has since continued through PR #109 → `500b6a8` (2026-09-22). This
> section is the 2026-09-07 snapshot, kept verbatim as evidence.

Status then: 🟨 **REPAIR CODE MERGED / TEST RELEASE PUBLISHED; HARDWARE AND STABLE
RELEASE ACCEPTANCE OPEN.** The merge chain then ended at **PR #32 → `862f0ac`**
(2026-09-07T01:24:20Z), stacking on PR #30 (`76c68eb`) and the earlier repair
batch. Main CI **34072908037** and test-release **34072908097** passed. Rolling
`test` pre-release published at **`862f0ac`** **2026-09-07T01:29:28Z**.
Rolling tags are replaced on every push to main — always inspect the current
asset identity, not a historical one.

| Published asset | Size | CI-produced SHA256 |
|---|---|---|
| `dhun-test.msi` | 112,136,192 B | — (download-and-hash blocked in sandbox; see asset `.sha256`) |
| `dhun-test.apk` | 17,516,190 B | — (download-and-hash blocked in sandbox; see asset `.sha256`) |

Both `.sha256` assets are uploaded alongside each binary. Main's Windows job
verified MSI identity, **1.0.5 → 1.36.1** with userdata/cache sentinels
retained, future-upgrade flag removal retaining them, reinstall and
explicit-uninstall cleanup. These are real hosted Windows Installer tests,
**not app launch, sound, visual, media-key or soak tests**. Published sizes/tag
were verified via GitHub APIs; the publisher passed with a Node-20 deprecation
warning for `actions/download-artifact@v4`; no zero-warning audit is claimed.

The user's last audio/Home/UI verdict was negative on the old 07:22 build.
Only one-window startup was reported successful after manual reinstall.
The newer `862f0ac` build needs the user's re-test. No v0.1.0 tag/release was
created.

## Pre-merge evidence — PR #30, b6d47bd (retained history)

Code CI **34030728903** (branch) and **34030730736** (PR) pass. Native
packaging **34030730743** passes with MSI **1.34.1** after fixing the initial
userdata-deletion failure; PR synthetic merge source **61bf548b62b689f67687afcd7e6b76c0a56739a4**.
Verified from job/check annotations and artifact APIs:

- MSI: **112,091,136 B**, SHA256
  `1a4d2fe5c9b0c8949995cbd62e5e01675a9fb8081d71eb3da74b5a834dde021c`;
  stable UpgradeCode and MSI database ProductVersion verified.
- Hosted Windows **1.0.5 → 1.34.1** install-over: userdata/cache sentinels
  preserved. Baseline hash:
  `0683538542af97c7505cd35610e03cfb42ce92e01e07ea09c28a7364b1825add`.
- Candidate uninstall with explicit UPGRADINGPRODUCTCODE: both sentinels
  preserved; candidate reinstalled and ordinary uninstall removed userdata.
- Artifacts: `msi` **9988585984**, diagnostic `msi-install-check`
  **9988584693**, `apk` **9988526777**. APK **17,499,806 B**, SHA256
  `1b256c5a42091921206e68afd63ab8d7768431ca121bd1f292ac989d1e910c86`.
- `publish` **SKIPPED** on PR. Main/test remain `0920148` / 07:22:29Z until
  the separately verified merge/publishing step.

These native checks execute real Windows Installer operations, not just
PowerShell parsing. They still do not launch DHUN, play audio or inspect its
UI. User-machine library preservation, sound, Home, visuals and native/soak
gates remain open. A later cancelled legacy transaction can leave cleanup
suppressed until retry; backups are still recommended. No v0.1.0.

## Phase 14 implementation status

| Step | Current status | Evidence / remaining gate |
|---|---|---|
| Typed error taxonomy and actionable user messages | 🟨 Typed `DhunResult`/`DhunError` + `toUserMessage` paths, per-request retry, 429 global backoff gate (`2932d57`, with unit tests), and offline banner (`fed1d54`) are merged with recovery UX; baseline CI `34018809911` is green. Local reason-preserving diagnostics changes await CI; offline/429/403 hardware checks and db-path review remain | `shared/.../core/RateLimitGate.kt`, `shared/.../core/ConnectivityMonitor.kt`, `DhunAppShell.kt`, hosts' Koin modules |
| Bounded audio cache and offline replay | 🟨 Android + Desktop code | Android: Media3 `SimpleCache` LRU via `DhunAudioSegmentCache` + `CacheDataSource` (stable video-id keys). Desktop: `AudioFileCache` whole-track LRU files under `<data dir>/cache/audio`, background fill during first play, local-file playback on hit (no resolve → offline). Both use `SettingsKeys.CACHE_SIZE_MB` default 1024 MB (`AudioCacheBudget`). URL TTL cache still `DhunStreamCache`. Unit tests: `AudioFileCacheTest` (9: hit/LRU victim/over-budget/short-read/cancel/unsafe id/partial sweep/shrink+clear). Hardware offline-replay check OPEN on both |
| Daily live extraction-health | ✅ **S1 GREEN (2026-09-20).** Schedule restored and firing daily: runs **35421383687** (09-19) + **35489268023** (09-20) on `main@6f7fa48` both classified `ENVIRONMENT_BLOCKED` — probe + classifier steps green, only the intentional non-PASS gate failed (exit 2). The runner's datacenter IP is bot-gated; **residential evidence on `main@d99060e` (docs-only diff from `6f7fa48`) played 4 songs audibly on Android + Windows**, so the block is network-shaped, not rot. | Gate 1 satisfied. Keep `ENVIRONMENT_BLOCKED`/`UNAVAILABLE` separate from `FAIL`; expect the runner to stay blocked. Re-run the residential guide if extraction code changes before the tag. First run on `main@d99060e`: 2026-09-21 04:17 UTC. |
| Android 30-minute soak | ⬜ Open | Requires a physical device with unrestricted battery mode, lock-screen playback, and zero-crash/leak evidence |
| Desktop 30-minute soak | ⬜ Open | Requires a desktop with libVLC and tray/SMTC-capable runtime |
| Release v0.1.0 artifacts | ⬜ Open | Rolling `test` APK/MSI is not the signed/stable v0.1.0 release; clean-target installation and release evidence are required |

## Rot-drill procedure

The scheduled workflow runs daily at `04:17 UTC` and can be started manually
from GitHub Actions. It performs the real Phase 01 path:

1. Install JDK 17 and `yt-dlp` on a fresh Ubuntu runner.
2. Run `:tools:playback-probe:run` using the shared InnerTube client.
3. Search for a known song, resolve an audio URL, fetch and validate audio
   bytes, and fetch related tracks.
4. Record the full log as a 14-day workflow artifact.
5. Open or update one `[rot-drill]` issue when the probe fails; automatically
   close that issue after a later green run.

The NewPipe Extractor watch line is intentionally non-fatal. The production
Desktop path uses the ADR-001 yt-dlp fallback while NewPipe remains monitored
for upstream recovery.

## Live evidence log

### Rot-drill / extraction-health

- [x] **Scheduled run — 36412874929 (2026-09-28 10:58:41 UTC, `main@5bbb16d`, job 108896993696): `ENVIRONMENT_BLOCKED`.** The first fire on the post-#117 baseline, and the one the PR #117 session had not yet seen when it wrote "had not fired" (checked 10:40/10:50 UTC). Read via the job-steps + `check-runs/<job>/annotations` APIs (log blob download EOFs in-sandbox): steps 1–10 `success` — including the offline + live playback probes, the independent Home-continuation comparison and the classification — both issue steps `skipped`, and `Keep the check non-zero when live health is unverified` failed with **exit 2 by design**. Annotation: `ENVIRONMENT_BLOCKED — inspect the probe log and verify playback outside the GitHub runner`. Steady state; issue #14 correctly untouched. Fire windows observed so far: 09:20–10:58 UTC, i.e. ~5–7h after the `17 4 * * *` cron. Next run executes on whichever `main` exists then (PR #118's merge if it has landed).
- [x] **Scheduled runs 2026-09-22 → 09-27, all on `main@edf19e0`, all `ENVIRONMENT_BLOCKED`** (annotation `Extraction health is not a production pass — ENVIRONMENT_BLOCKED — inspect the probe log and verify playback outside the GitHub runner`): 35709793101 (09-22 09:20:13Z), 35842385136 (09-23 09:20:47Z), 35980652608 (09-24 09:20:40Z), 36119584587 (09-25 09:38:37Z), 36232608074 (09-26 09:23:04Z), 36311265246 (09-27 10:02:34Z). This is the steady state; issue #14 was correctly left untouched. The runs **retract** the 2026-09-22 "schedule wedged" note: the `17 4 * * *` cron fires ~5h late on this repo, and the 09-22 run appeared after that check. The PR #111 in-place edit did not wedge the registration. First run on `dcdd41b` or later: 2026-09-28's fire.
- [x] **Scheduled run — 35489268023 (2026-09-20 04:29:25 UTC, `main@6f7fa48`, job 106021243260): `ENVIRONMENT_BLOCKED`.** Read via `check-runs/<job>/annotations` + the job-steps API (log/blob download returns `EOF` in the sandbox): steps 1–10 `success` — offline probe, live probe (under `continue-on-error`), the independent `ytmusicapi` Home comparison, artifact upload, classification — issue steps `skipped`, and step 13 `Keep the check non-zero when live health is unverified` failed with **exit 2 by design**. Annotation: `Extraction health is not a production pass — ENVIRONMENT_BLOCKED — inspect the probe log and verify playback outside the GitHub runner`. Artifact `rot-drill-35489268023` retained 14 days. No live audio bytes were validated on the runner and issue #14 was correctly left untouched. First scheduled run on `main@7304abb`: 2026-09-21 04:17 UTC.
- [x] **Scheduled run — 35421383687 (2026-09-19 04:28:36 UTC, `main@6f7fa48`): `ENVIRONMENT_BLOCKED`.** Same classification and same failure shape as the 09-20 run; this is the first pair of runs proving the daily cadence is restored on the repaired Home request contract (`6f7fa48`).

- [x] **Current candidate live run — run 35321898985 (2026-09-18, `workflow_dispatch`, `arena/01a0b224-dhun@257251c`, job 105526042204): RED / correctly classified result.** Version/search, first Home page, related, and deterministic offline playback passed. `home-more` failed with `tabs[tabRenderer]`, `tabRenderers[endpoint,icon,selected,tabIdentifier,title,trackingParams]`, and empty `tabContents`/`tabSections`; no browse items/actions/commands/continuations were present. Own-client/yt-dlp and their watch lines were `ENVIRONMENT_BLOCKED`; NewPipe remained the separate short-JSON watch. The overall verdict was correctly `FAIL` because the Home response had no section/cursor payload. Artifact `rot-drill-35321898985` id **10537362749** exists; blob download returned `EOF` in the sandbox.
- [ ] **Raw Home response / real section-cursor contract pending:** the shape-diagnostic objective is complete in code head `f36cc76`. Do not add an empty-page success or opaque-endpoint follow-up. A raw/sanitized response or later approved run with a real section/cursor payload is required before another parser branch is justified. Android and Windows/Desktop production paths are not being reopened.
- [x] **Stale-candidate rerun — run 35310771629 attempt 5, job 105507779324:** GitHub reran older head `dbb3c08`, not the final branch head. Metadata/search, first Home page, related, and offline passed; `home-more` failed again; resolver bot-gating and NewPipe short-JSON remained separate. The old workflow revision had no classifier step, so this does not validate the final probe status changes. Refreshed artifact id **10535400903**; artifact download returned `EOF` in the sandbox.
- [x] **Current S1 live verdict — run 35306224822 (2026-09-18, `workflow_dispatch`, `main@33e94b0`, job 105478849067): RED / mixed.** Artifact `rot-drill-35306224822` (id **10532130174**, 4,357 B) exists and issue #14 received the workflow comment. Version/search passed (20 songs), Home first page passed (2 sections + continuation token), related passed (50 tracks), and the deterministic offline probe passed with zero network calls. `home-more` independently failed with `Parse(detail=Home response contained no section list or Home continuation action)`. Own-client and yt-dlp returned `AuthRequired` / `LOGIN_REQUIRED` bot-gating, so stream bytes were skipped; NewPipe returned `Parse(detail=JSON response is too short)`. The artifact blob download returned `EOF` in the sandbox, so the raw Home body is not available. Keep bot-gating and parser findings separate; no cookies, sign-in, PO tokens, BotGuard, attestation, or ADR-007.
- [x] **Candidate-branch probe — run 35308796439 (2026-09-18, `arena/01a0b224-dhun@c546d7b`):** offline/metadata/search/related passed, but `home-more` remained RED with shape-only detail `top[contents,responseContext,trackingParams]`; own-client/yt-dlp remained bot-gated, NewPipe remained short-JSON parse, and no audio bytes were validated. Artifact `rot-drill-35308796439` id **10532443661** exists; blob download returned `EOF` in the sandbox.
- [x] **Home continuation request-contract follow-up — run `35325690972` on `c71d1bb`:** the independent anonymous client supplied the real `continuationContents.sectionListContinuation` contract, so DHUN was repaired at the request layer (`alt=json`, body/query token placement, and anonymous visitor header). `HomeFeedParser.kt` remained unchanged; the candidate no longer produced a Home-driven `FAIL` and was classified `ENVIRONMENT_BLOCKED` solely because resolver playback remains runner-gated. The earlier tab-only shell remains invalid if encountered; no parser fallback or opaque-endpoint follow-up is accepted.


- [x] **Failure path exercised for real — run 33961533965 (2026-09-05,
      workflow_dispatch on `a554594`, job 101295458477): FAILED as
      designed.** Verdict line: `PROBE|verdict|FAIL|extraction-pipeline-broken`.
      Root cause: yt-dlp's default player path was bot-gated
      ("Sign in to confirm" → `AuthRequired(detail=null)`) from the Actions
      runner's datacenter IP, while InnerTube metadata (version/search/
      related) passed in the same run — i.e. YouTube player-endpoint
      datacenter-IP gating, not extractor-shape rot. Issue [#14](https://github.com/99ggprooo00-code/DHUN/issues/14)
      auto-opened with the log tail; artifact `rot-drill-33961533965`
      uploaded; kill-switch step fired. Secondary defects found and logged
      in `.ai/DEBUG_LOG.md`: probe gated the verdict on the desktop-fallback
      engine only (not the production own-client→yt-dlp chain), yt-dlp
      stderr evidence was dropped, and the issue body swallowed the artifact
      name through a bash backtick bug.

- [x] **Second live dispatch on wrong ref — run 33968612285 (2026-09-05,
      workflow_dispatch on `main` @ `a554594`): FAILED, identical pattern.**
      Same `AuthRequired(detail=null)` / no `WATCH|own-client`. Confirms the
      user UI defaulted to main (pre-fix). Kill switch step "Fail the
      workflow after alerting" is intentional. Issue #14 updated with
      diagnosis comment. **Not a regression of PR #16** — that code was not
      checked out.


- [x] **First live run on the FIXED branch — run 33968950214 (2026-09-05,
      workflow_dispatch on `arena/01a07170-dhun` @ `10ad025`): FAILED as
      designed (kill switch).** Production chain + per-engine WATCH lines
      fired. Evidence:
      - `WATCH|own-client|BROKEN|AuthRequired(web_remix/visionos/tv all
        AUTH_REQUIRED Sign in to confirm you're not a bot)`
      - `WATCH|ytdlp|BROKEN|AuthRequired(...Sign in to confirm you're not a
        bot... --cookies...)` with yt-dlp **2026.08.19** in the artifact
      - `PROBE|related|PASS|50` + search/version PASS ⇒ metadata healthy
      - Artifact name `rot-drill-33968950214` correctly present in issue #14
      - Classification: **category 8 CI/datacenter-IP bot gating of BOTH
        production engines** — not shape rot. Residential verification OPEN.
      - Do **not** convert this red into a pass.


- [x] **Expanded-chain live run — 33970045379 (2026-09-05, `d9f4083`):** FAIL.
      `WATCH|ytdlp` still AuthRequired on default messaging, but
      `resolve+stream` reached a **real googlevideo URL (itag 251)** then
      **HTTP 403** on the range byte-fetch from the Actions IP. Own-client
      WATCH reported `Unavailable`. Metadata PASS. Progress: no-URL →
      URL-then-CDN-403. Kill switch OK. Do not drop byte verification.

- [x] **Scheduled default-branch execution verified — run 34011539225,
      2026-09-06, `schedule`, `dd1ab31`: FAILED.** Artifact
      `rot-drill-34011539225` (3,241 B) exists. Issue #14's
      [04:29:14Z comment](https://github.com/99ggprooo00-code/DHUN/issues/14#issuecomment-5556894160)
      preserves the output: version/search/related PASS; own-client WATCH
      Unavailable; yt-dlp WATCH bot-gate AuthRequired; production resolve+
      stream FAIL / Unavailable. Do not relabel the aggregate as AuthRequired.
      Run: https://github.com/99ggprooo00-code/DHUN/actions/runs/34011539225
- [x] Failure path creates/updates one issue and uploads the log artifact.
- [ ] A later live run completes with `PROBE|verdict|PASS`, including
      validated audio bytes, after the current mixed RED is reconciled.
- [ ] Approved residential/device verification distinguishes runner bot-gating
      from user-network playback.
- [ ] Recovery path comments on and closes issue #14 (still OPEN).

**CI vs user-network evidence:** a red Actions run establishes failure on
that runner, not its unique cause or residential success. The user now
also reports failed Windows audio; do not dismiss it as CI-only gating.
Keep byte validation and the kill switch intact. Authentication/cookie or
identity-scheduling changes require the appropriate approved decision;
ADR-003 remains proposed and the own-client chain remains sequential.

### Android soak

- Device / Android version: ____________________
- Battery mode / OEM settings: ____________________
- APK / commit: ____________________
- Start and end timestamps (30 minutes): ____________________
- Lock-screen and notification controls: ____________________
- Rotation/back stack/shortcut result: ____________________
- Crash / leak result: ____________________
- Screenshots or logcat location: ____________________

### Desktop soak and clean install

- OS / version / libVLC version: ____________________
- MSI / commit: ____________________
- Start and end timestamps (30 minutes): ____________________
- Tray / keyboard / SMTC result (separate mini-player window removed — ADR-004): ____________________
- Clean-install result: ____________________
- Crash / zombie-process result: ____________________
- Screenshots or logs: ____________________

### Windows MSI startup — \"Failed to launch JVM\" (2026-09-06)

**History:** `dhun-test.msi` built from `main@8310383` (PR #20, `34001706159`) installed per-user to `%LOCALAPPDATA%\DHUN` but opening the installed app showed `Failed to launch JVM` — a desktop startup failure, not a packaging failure. No startup fix was published before the handoff; the exception was uncaptured.

**Investigation leads from code review (not yet confirmed then):** `app-desktop/build.gradle.kts` omitted `java.sql` (needed by SQLDelight/JDBC), `DesktopDhunPlayer` init before window (VLC), missing exception capture.

**Fix (PR #22, merged to `main@e90dba6`):**

- `app-desktop/build.gradle.kts` — adds explicit `modules("java.sql", "java.sql.rowset", "java.naming", "jdk.unsupported", "java.management", "java.instrument", "java.desktop", "java.logging", "java.net.http")` plus `includeAllModules = true` (112 MB MSI, `test-release` `34011563630` windows-latest `5m13s`). Bumped `packageVersion` 1.0.4 → **1.0.5** (same `upgradeUuid`, per-user, SmartScreen unsigned — unchanged). This is the documented Compose Desktop fix for sqlite/H2 \"Failed to launch JVM\" (docs: `kotlinlang.org/.../compose-native-distribution.html#including-jdk-modules`; StackOverflow 77675565, 78374398).

- `app-desktop/.../player/DesktopDhunPlayer.kt` — `MediaPlayerFactory` now try/caught; `vlcAvailable` gates all ops; missing VLC degrades to `PlaybackState.Error("VLC not found — install VLC…")` instead of crashing before window.

- `app-desktop/.../desktop/Main.kt` — captures every startup exception: `Thread.setDefaultUncaughtExceptionHandler`, early probes for `java.sql.Driver`/`org.sqlite.JDBC`/`vlcj`, log file `<installDir>/userdata/dhun-startup.log` (fallback `%TEMP%`) with OS/Java/jpackage.app-path + stacktrace, AWT `JOptionPane` dialog + minimal error `Window` if Koin/DataLayer fails before main window, `DataLayer` file-DB → in-memory fallback with logging.

**CI evidence (GitHub-verified, not yet hardware-verified):**

- PR CI `34011326728` — **passed** shared JVM tests, Android debug build, probe compile, Desktop compile (`:app-desktop:compileKotlinJvm`).
- Main CI `34011563632` — **passed** (6m10s) on `e90dba6`.
- Rolling test-release `34011563630` — **passed** `msi` `5m13s` + `apk` `4m33s` + `publish` `19s`; published the `1.0.5` JVM-fix binaries to the `test` pre-release at `2026-09-06T04:33:36Z`.
- Rolling test-release `34012157287` on `main@9294520` (PR #23, docs-only) — **passed**; re-published the same `1.0.5` binaries at `2026-09-06T04:45:40Z`: `dhun-test.msi` 112,001,488 bytes + `dhun-test.msi.sha256`, `dhun-test.apk` 17,467,038 bytes + `dhun-test.apk.sha256` (verified with `gh release view test`). Main CI on the same commit: `34012157207` — **passed**.
- **The `test` tag now points at `9294520`, not `e90dba6`** — the binaries are unchanged, so either checksum set matches the JVM-fix build. Verify before testing; any further push to `main` replaces these assets.

**Hardware gate still OPEN — to verify on a Windows machine:**

1. Download the current `dhun-test.msi` + `dhun-test.msi.sha256` from `https://github.com/99ggprooo00-code/DHUN/releases/tag/test`; verify the checksum, record the actual release SHA and published time (last verified `0920148` / `07:22:29Z`; the rolling asset can change).
2. Install per-user (no admin) — accept SmartScreen **Run anyway** / **More info → Run anyway** — confirm install completes without admin UAC.
3. Launch DHUN from Start menu / installed shortcut — **no** `Failed to launch JVM`; exactly one window opens: the main window (1200×780) with the docked mini-player above the bottom nav, plus the tray icon. (The separate mini-player window was removed — ADR-004, 2026-09-06.)
4. Check `dhun-startup.log` (packaged: `<installDir>/userdata/dhun-startup.log`; fallback: `%TEMP%\dhun-startup.log`) —
   - contains `DHUN main starting` + `java.sql.Driver available` + `org.sqlite.JDBC available` + `VLC initialized` (or `VLC init failed` → graceful Error state, not crash).
   - no `ClassNotFoundException: java.sql` or `UnsatisfiedLinkError: libvlc`.
5. If VLC is installed: play an uncached search result → audible audio; tray icon switches; if VLC is **not** installed: player shows `VLC not found — install VLC…` Error but app stays responsive (tray/close-to-tray still work).
6. Clean uninstall: Settings → Apps → DHUN → Uninstall → confirm `<installDir>/userdata` is removed; VLC remains (not ours).

Record here: Windows version/build, VLC version (or \"not installed\"), MSI size/sha256, `dhun-startup.log` excerpts (sanitized), and whether launch succeeded. **Successful CI packaging is not launch verification.**

### Hardware reports — 2026-09-06

**Earlier report:** APK and MSI installed/launched, confirming the previous
JVM-launch recovery on the user's machines; audio failed on both. PR #24
subsequently propagated the resolving User-Agent and added Home continuation,
PR #25 added a resolve budget/diagnostics, and PR #26 restyled the UI. Those
changes are on GitHub and in the 07:22:29Z release, but are **not proof of
successful audio or accepted UI**. The earlier attribution of all no-audio
to User-Agent mismatch was too strong without device stream evidence.

**Fresh Windows report, in response to the 07:22:29Z build recommendation:**

| Check | User-observed result |
|---|---|
| Install over existing DHUN | **FAIL** — “Another version of this product is already installed. Installation of this version cannot continue. To configure or remove the existing version of the product, use Add/Remove Programs on the Control Panel.” |
| Manual uninstall, then reinstall / launch | **PASS as reported** — one window opens. This is not a clean-VM or successful in-place upgrade test |
| Playback | **FAIL** — “This track is not available right now.” Screenshot: **Ko Cha Ra (Official Audio) — John Rai**, **0:00 / 4:49**, Retry, no useful diagnostic detail |
| Home | **FAIL** — endless/further-page scrolling still unavailable |
| Player appearance | **NOT ACCEPTED** — styling somewhat better; glyph positions, shuffle shape and shuffle/next/previous/repeat colours wrong. Artwork/controls appear oversized/spread across the window |

Screenshot evidence was supplied in the conversation (`Screenshot 2026-09-06
132629.png`), not copied into this checkout. No verified installer SHA256,
track ID, Windows/VLC versions or sanitized current logs were supplied. No
fresh Android result accompanied this Windows report. Only the reported
one-window launch check is closed; tray/SMTC/shortcuts remain unverified.

### Branch repair candidate — arena/01a0759b-dhun (CI ONLY, NOT RELEASED)

**First branch CI result:** [34025629231](https://github.com/99ggprooo00-code/DHUN/actions/runs/34025629231),
`f914050`, push event, **FAIL**. JDK setup and Python checks passed; shared
Kotlin compilation failed with four `Unresolved reference 'index_'` errors
in `HomeScreen.kt`. Kotlin tests did not execute; Android/probe/Desktop steps
were skipped. Fix: delimit `${index}` in the shelf keys. Follow-up also wires
the Quick-picks visibility predicate into the actual category projection and
adds a regression, and upgrades CI checkout to its Node-24 v5 runtime after
the run reported the v4 Node-20 warning. The rerun below is green; no release/PR.

**Verified corrected branch run:** [34025807972](https://github.com/99ggprooo00-code/DHUN/actions/runs/34025807972) at
**`75c4a8b9e6b3a030d24a360b0cb98923a4de5a0f`**, completed **09:57:34Z on
2026-09-06**, job **101466441642** (6m13s), **SUCCESS**. Run, job steps and
check annotations were verified via GitHub REST APIs:

| Check | Actual result |
|---|---|
| JDK 17 / checkout v5 setup | PASS |
| Installer + fixture Python tests | PASS |
| `:shared:jvmTest` | PASS — includes new parser/paging, resolver lifecycle, diagnostics, transport and raster/layout regressions |
| `:app-android:assembleDebug` | PASS — built, not device-tested or published |
| `:tools:playback-probe:compileKotlin` | PASS — compilation only, not a live extraction run |
| `:app-desktop:compileKotlinJvm` | PASS — no MSI/native runtime test |
| Check annotations | 0 — not a separate full-log compiler-warning audit |

This proves the automated **branch code/test checkpoint**, not actual audio,
Windows upgrades or visual acceptance. `main` / `test` remain `0920148`, the
public download remains the 07:22:29Z build, and there are no open PRs.

| Area | Source repair / regression coverage added |
|---|---|
| MSI identity | Replace constant 1.0.5 with `dhunInstallerVersion`; CI uses `(1 + run/256).(run%256).attempt`, bounded to MSI numeric limits; local default 1.0.6. Run 33/attempt 1 would be 1.33.1. Keep upgrade UUID `31ddb86b-9666-4071-b11c-45f16fa4682d` and `dhun-test.msi`; reject superseded-ref publishing; log installer version. A future stable packager must continue the internal sequence, not reset it to app semver |
| Desktop extraction | Locate `yt-dlp.exe` on Windows Path / explicit `DHUN_YTDLP`; discover real Python/`py` fallback without Unix `which` or launching Store aliases. Missing tool gets an actionable diagnostic, not Network. Drain both pipes while waiting, retain bounded output, interrupt waits and dispose child processes on cancellation; ignore user yt-dlp config to preserve anonymous/no-cookie operation |
| Failure evidence | Network/Unavailable/429 can retain details; preserve playability reason/subreason, every completed own-client outcome and both engine failures. Bound/sanitize external text and redact URLs. Timeout names the active engine and any completed primary failure. No scheduling fan-out |
| Home parsing / transport | Select only feed-level section-list continuation tokens; support list continuations and append/reload actions/commands. Keep the request body's client version aligned with its header on the first request too |
| Home data / state / UI | Same title plus fresh item IDs is new content. Follow advancing empty/duplicate pages, but stop token cycles and pause after three no-growth pages. Claim loading before dispatch; refresh invalidates stale pages. Keep failed feed visible with explicit retry; show honest end state. Indexed keys and later quick-picks shelves do not hide fresh tracks |
| Player graphics / diagnostics | Scale SVG paths about the origin; canonical Apache-2.0 Material shuffle/repeat paths. Fit artwork to both available axes; centre bounded transport/volume; consistent inactive tint and active toggle treatment. Same full, scrollable/selectable Playback details dialog from either player; docked MiniPlayer retained |

**Local checks and the original environment blocker (distinct from CI above):**

- **PASS:** `python3 -m unittest discover -s scripts -p 'test_*.py' -v` —
  **10 tests**: five installer tests (legacy 1.0.5 upgrade ordering, reruns,
  run ordering, numeric rollover/limits and invalid inputs) and five strict
  fixture-validator tests (valid/malformed/duplicate-key/non-JSON-number
  handling plus validation of checked-in inputs).
- **PASS:** `git diff --check`; `python3 scripts/validate_fixtures.py` —
  **29 JSON fixture files**, including **17 new synthetic Home response
  cases** consumed by the Kotlin tests. These are static/helper checks,
  not Kotlin parser tests or application execution.
- **BLOCKED before Gradle started:**
  `./gradlew :shared:jvmTest :app-desktop:compileKotlinJvm --no-daemon` →
  `JAVA_HOME is not set and no 'java' command could be found in your PATH.`
  Maven, Gradle distribution and Adoptium requests also failed with
  `SSL_ERROR_SYSCALL`. The follow-up official Temurin JDK-17 download through
  GitHub also failed at `release-assets.githubusercontent.com` with EOF.
  No usable JDK or Gradle dependencies were downloaded/installed.
- **Now PASS in GitHub CI 34025807972, not run locally:** Windows tool lookup/missing module/Store aliases,
  pipe back-pressure and cancellation tests; error aggregation and fallback
  evidence; scoped continuation/response-shape and first-request tests;
  repeated-title/empty-page/cycle/retry/stale-refresh tests; headless
  icon raster bounds at 18–64 px and artwork dimension tests.
- Candidate CI, shared tests and Android/probe/Desktop builds passed as
  recorded above. There is **no new MSI package, published APK/MSI, live
  extraction, Windows install or audible playback proof**. Publication is deferred until the
  user separately authorises it after verified CI. The earlier **Keep
  everything local** decision was superseded by explicit permission for
  **commit/push and CI only**. Do not open a PR, merge or publish a release.

**Local follow-up review:** Home now keeps different action targets separate,
prefers full-page section lists over unrelated actions, accepts same-target
split updates and rejects ambiguous targets. Previous/Next cancellation no
longer routes through a tap; hold cleanup is in `finally`, and scrubbing is
reset on track/duration changes. yt-dlp's own deadline includes pipe EOF,
cleanup runs once, start denial is typed, and a track ID containing `429`
is not a rate limit. Corresponding Kotlin regressions now **PASS in the
verified branch run**. None of this closes playback, UI or installation acceptance.

### Initial native PR packaging failure — 34029598179 (historical; corrected above)

PR #30 on `8b2da25` / synthetic merge `1fb74d9` built APK and MSI. Code CI
34029598196 passed. MSI property verification confirmed **1.33.1**, stable
UpgradeCode, **112,075,216 bytes**, SHA256
`57aaf0a53cf17319dc832398adbb4ab485e9e29f86dbf9633ca540c5eb5af576`.
The real install-over step then **FAILED: existing userdata was removed**.
The MSI artifact was withheld, diagnostic artifact `msi-install-check`
(9988255378) was uploaded, and `publish` skipped. Nothing was merged/released.

Do not weaken this test. The in-progress installer finalization adds an
upgrade-only property guard plus a narrow legacy-HKCU cleanup bridge, before
checksums/signing. A second native path test verifies future upgrade removal
preserves data and explicit uninstall still removes it. Preparation errors
abort before old-product removal; a later cancelled legacy transaction may
leave cleanup suppressed until a successful retry, rather than deleting data.
The corrected native run 34030730743 above passed these sentinel checks; full user-machine acceptance remains open.

### Packaging verification and newly authorised PR/merge

The latest user instruction explicitly requests completing the work, then
PR and merge. The earlier CI-only/no-PR restriction is superseded for this
batch. Windows packaging and install-over checks are being made normal PR
checks before merge; PR refs have contents:read and cannot publish. The
manual workflow-dispatch API remains denied; it is not retried. Actual
Windows execution is still pending and must not be confused with the
already-green PowerShell syntax step.

### Build-only packaging history

The user clarified that ordinary development is allowed; only the one-time
Arena session-ending action must be avoided. No PR/merge is being used.
The existing packaging workflow now supports `build_only=true` dispatches
on this session branch, with read-only build permissions, isolated branch
concurrency and a job-level `publish` guard requiring main and publishing
mode. Branches cannot publish. The same workflow counter supplies versions,
avoiding a separate artifact workflow's larger counter blocking later MSI
upgrades.

New staging adds exact file checksums and source/run manifests (only
whitelisted non-secret metadata), queries the actual MSI ProductVersion and
stable UpgradeCode, and includes a short Windows guide. A Windows-only CI
script is restricted to disposable Actions runners: download/checksum the
existing release, silently install it, seed userdata/cache sentinels,
install over it, verify those files, and check uninstall cleanup. Full logs
are retained; this does not launch the app or validate sound/visuals.

**Local results:** 19 Python tests pass (including publishing-guard truth
table and artifact provenance tests); 29 JSON fixtures pass syntax checks.
**Packaging dispatch:** pushed at `9317050`, then denied by GitHub with
**HTTP 403: Resource not accessible by integration**. The branch has no
packaging run; no MSI or install-over test ran. Automatic CI 34028039448
passed separately. The follow-up [CI 34028225356](https://github.com/99ggprooo00-code/DHUN/actions/runs/34028225356) at **`77f9c96`**
completed **10:46:45Z** (job 101472922356): **PASS** for 19 Python helper
tests, PowerShell syntax, shared JVM tests, Android debug build and
probe/Desktop compilation. Check annotations: 0. The PowerShell check
parses the scripts only; it does not execute Windows Installer/COM or
install/uninstall products.

Owner action: reconnect GitHub in Arena, or manually run `test-release` from
GitHub Actions using **arena/01a0759b-dhun**, not main, with build-only on.
No new artifact or hardware pass may be claimed until an actual run is
recorded here.

**Next Windows acceptance, only with a CI-green candidate artifact:**

1. Record build SHA, published time, internal MSI version and SHA256.
   Quit all DHUN/tray processes. Back up test userdata before the upgrade
   check; test on a disposable user/VM where possible.
2. Install **over** the previous MSI without manual uninstall. Verify
   library/queue preservation, one-window startup, and no “Another version”
   error. Manual uninstall/reinstall does not pass this test.
3. Play an uncached track and verify actual sound plus advancing position.
   If it fails, open **Details**, select/copy the diagnostic (no signed URLs,
   cookies or tokens), and record track ID, elapsed resolving time,
   Windows/VLC/yt-dlp versions and sanitized `dhun-startup.log` excerpts.
4. Scroll Home through multiple server pages, including repeated shelf
   labels with new music; verify manual retry after a network failure and
   refresh while paging. An upstream null continuation is a real end, not
   an excuse to fabricate an infinite feed.
5. Inspect shuffle/previous/play/next/repeat at common Windows display
   scaling and in a short/wide window. Artwork must not cover controls.
   Then re-check Android using the matching candidate APK.

### v0.1.0 release gate

- [ ] `extraction-health` has a current green live run on the release candidate (scheduled runs **35421383687**/**35489268023** on `main@6f7fa48` classify `ENVIRONMENT_BLOCKED`; first run on `main@7304abb` due 2026-09-21 04:17 UTC — Home no longer fails, resolver runner-gated, no audio bytes validated — residential/device evidence still open, and a runner-only green would not be sufficient anyway: S1 closes on device/residential playback, not on CI).
- [ ] Android APK and AAB build and install on a clean target.
- [ ] Windows MSI installs and launches on a clean Windows VM/user — **published baseline `0920148` launches on the user’s machine; install-over failed and clean-target hygiene is still OPEN**.
- [ ] Android and Desktop soak evidence is attached above (both still OPEN; use an identified candidate that first passes real playback).
- [ ] `KNOWN_LIMITATIONS.md`, `THIRD_PARTY.md`, `RISK_REGISTER.md`, README,
      and CHANGELOG are current (current-report reconciliation is local; final hardware/risk/license/release review still OPEN).
- [ ] Release is tagged `v0.1.0` only after all required evidence is real.

## PR #16 merge (2026-09-05)

Merged to `main` (session `arena/01a07170-dhun`). Code + CI complete for:
taxonomy, Recovering UX, audio-segment cache (Android), M3 glass UI, ADR-002 player polish.

**Still OPEN:** residential rot-drill/stream, Android/Desktop soaks, v0.1.0 artifacts.

## Install / uninstall hygiene (2026-09-06, session arena/01a073c3-dhun)

Code-level audit of the rolling `test` artifacts. Not a malware scan of
the bytes (sandbox cannot fetch GitHub release assets); provenance is
`test-release.yml` on `main`.

| Surface | On install | On uninstall |
|---|---|---|
| Android `dev.dhun.android` | Sideload debug APK, public test key, permissions listed in README. Data only in app-private storage. `allowBackup=false`, `hasFragileUserData=false`, no cleartext HTTP. | Settings / launcher Uninstall deletes `/data/data/dev.dhun.android` (DB + `cache/audio-segments` + Coil). No shared-storage writes exist in the code. |
| Windows MSI | Per-user (`%LOCALAPPDATA%\DHUN`), no UAC. Unsigned → SmartScreen. Needs a preinstalled VLC. | Settings → Apps → DHUN removes the install dir including `userdata/` (DB + `cache/audio`). VLC is left installed (it is not ours). |

Hardware confirmation of the Windows row still OPEN (needs a real MSI
install/uninstall). Android uninstall cleanliness is platform-guaranteed
for private storage.

## Desktop audio cache (2026-09-05, session arena/01a07287-dhun)

Code: `shared/src/jvmMain/kotlin/dev/dhun/player/AudioFileCache.kt`,
`DesktopDhunPlayer` (cache-hit → local file; miss → stream + background
fill; fill cancelled on skip/stop), Koin wiring in `Main.kt`. Test:
`shared/src/jvmTest/.../AudioFileCacheTest.kt`. CI gains a
`:app-desktop:compileKotlinJvm` step (previously desktop only compiled on
main's MSI job).

Desktop offline check to run on a machine with libVLC:
- [ ] Play a track fully → log `DHUN cache: cached <id>`; file exists under
      `<data dir>/cache/audio/<id>.audio`.
- [ ] Disconnect network → play the same track → log `cache hit` and audio
      plays; a non-cached track shows the typed network error.
- [ ] Set `cache_size_mb` small, play several tracks → oldest evicted,
      total stays under budget.
