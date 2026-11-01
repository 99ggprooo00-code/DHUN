# 43 — DHUN glass restoration, stages 5–9

Date: 2026-10-31. Branch: `arena/8a70a040-dhun`.

## Status and evidence boundary

The stage diffs are implemented and the local Python/source suite passes
**519/519** at `bc0913c`. Hosted Compose/Skia screenshots now exist and have
been inspected; this is **not physical-device, accessibility-service, audible-
playback, or performance acceptance**. This workspace has no JDK, Android SDK,
emulator, ADB/phone, or interactive desktop display. Kotlin compilation and
JVM/Android/desktop tests require GitHub CI.

Persistent-update phone recovery is a linked but independent acceptance gate.
Nothing in this visual-stage record proves its cause or fresh/update behavior.

## Reviewed evidence and corrections (exact heads)

| Head / run | Result and boundary |
| --- | --- |
| `55c6ff3` / CI `41221873380` | **1,018 executed tests**: 794 shared JVM, 169 Android Robolectric, 4 playback probes, 51 desktop JVM. All except one desktop playlist-dialog assertion passed. |
| `53d2388` / Visual `41222626645` | **FAIL, 60 images / 5 tests / 1 failure**. The real repository was requested once and emitted `Visual QA / 4 tracks`; Library held the same row while the popup stayed empty. This disproved repository/dispatcher starvation as the cause. |
| `dc0e506` | Reads the collected playlist value in the parent composition and passes a fresh snapshot into the separate `Dialog` composition. The flow remains remembered so that parent recomposition does not restart collection. |
| `dc0e506` / Visual `41223387654` | **PASS, 62 verified PNGs / 5 tests / 0 failures / 0 errors**; manifest `d5a9a46d5fa22f34861594b7910b420ed1b93bb3`. All 62 frames were inspected. Direct and in-app dialogs visibly contain `Visual QA / 4 tracks` in dark and light modes. |
| `dc0e506` / test-release `41223387670` | Install-over and userdata/cache preservation **PASS**. The launch check **FAILED** because it watched only the launcher PID, while safe metadata showed a responding child `DHUN` process with a normal main-window handle. No PNG was captured, so this is not an installed-startup PASS. Manifest `19434c619dd4dc6124bbf08b69c37f6baf718c60`. |
| `bc0913c` | Corrects the Windows harness to find the normal window-owning `DHUN.exe` process, exclude pre-launch and startup-error windows, and clean up child and launcher. A startup PASS still requires a new real window capture and successful upgrade/uninstall result. |

The reviewed `dc0e506` matrix covers compact and wide Home, Search, Library,
Artist, Album, Playlist, player, queue, lyrics, Related, Settings, navigation,
menus/dialogs, loading/empty/error/offline, brightness, no-blur, and controlled
black/white artwork in dark and light modes. It does not establish TalkBack or
another native screen reader, large-text/IME behavior, reduced motion, audible
playback, physical rendering, frame cost, or memory behavior.

## Separately reviewable stages

| Stage | Commit / scope | Local evidence | CI status known while writing Stage 9 |
| --- | --- | --- | --- |
| 5 — glass foundations | `50cda76`: independent material/opacity/contrast/blur policies | Policy calculations and Python suite; no rendered UI | **Success**, run `41204408896` |
| 6 — screen surfaces and controls | `b19b2dd`: shared indicators/fields/refresh; Home, Search, Artist, Library adoption | 5 design tests; 511 total Python tests | **Success**, run `41205984049` |
| 7 — player and lyrics | `c3a9ab4`: volume/error controls, lyrics menus and TTML input | 6 design tests; 512 total Python tests | **Failure**, run `41206286572`: two rows used `text =` instead of `MenuActionRow(label = …)` |
| 7 correction | `845e07f`: only the two parameter names | 9 design tests after the later documentation guardrails were added | Inspect this exact head; do not infer success from the fix description |
| 8 — Settings, dialogs, shell | `ef81333`: DHUN Settings controls, battery dialog, tinted banners, navigation bar/rail | 7 design tests; 513 total Python tests | **Failure**, run `41206590339`, reporting the inherited Stage 7 parameter errors; not labelled green |
| 9 — authoritative documentation | Stage 9 diff: actual glass layer model, simple backdrop preferences, active-guidance reconciliation, global menu/docs guardrails | 9 design tests; **515 total Python tests pass** | Exact Stage 9 head must be checked after commit |

The original red commits remain in history. Acceptance must use a corrected
cumulative head with its own successful CI; this note does not rewrite their
results.

## Functional preservation reviewed at source level

- Player transport, seeking, queue mutation/reordering, lyrics state machines,
  provider selection, romanization, TTML limits/preview/choice, and error retry
  callbacks were not replaced.
- Backdrop blur/brightness still call the existing `SettingsViewModel` setters
  and live appearance setters. Settings atomic load, persisted-value rollback,
  equalizer behavior, and reset logic were not rewritten by the visual stages.
- Tab order, selected-tab calculation, detail-stack behavior, and the distinct
  phone/two-pane `selectTab` rules remain in `DhunAppShell`.
- Playlist/download transactions, persistence schema, website trees, release
  assets, and provider behavior are outside the visual-stage diffs.

## Background decision (#216)

The current product keeps **Background blur** and **Background brightness**
(defaults 100% and 55%). Zero blur keeps sharp, dimmed artwork; unsupported
platforms hide only the blur slider. The five-palette and later four-surface
custom-colour/HSL models are historical research, not current requirements.
`docs/verification/35-background-visual-validation.md` is marked superseded so
its open checklist cannot be mistaken for acceptance of controls that do not
exist in this tree.

## Documentation and enforcement

`docs/design/glass-system.md` is the single native visual authority. It now
separates quiet rows, glass cards/dialogs, artwork-backed player/menu materials,
and navigation; records independent light/dark and accessibility rules; gives
correct/incorrect examples; and documents the real non-blur fallback.

`scripts/test_design_system.py` now:

- scans every Kotlin source under shared native UI, Android, and desktop for raw
  stock visual calls;
- requires transparent, zero-elevation menu anchors;
- preserves critical player, Settings, navigation, and battery callbacks;
- rejects active guidance that restores rejected background models or names
  unimplemented components.

These are source/policy guardrails, not proof of taste, contrast on a physical
screen, frame cost, or accessibility-service behavior.

## Acceptance boundary and remaining hardware work

1. The final PR report must name the exact head and its own CI, Android package,
   desktop package, and visual-evidence results. A historical green run is not
   evidence for a later head.
2. The independent persistent-update recovery matrix still requires actual
   phone acceptance: fresh install, one install-over, and several successive
   install-over runs without clearing playlists, favourites, settings,
   downloads, queue, or history.
3. Real Android and interactive desktop review remains required for large text,
   keyboard/IME/focus, native screen readers, reduced motion, dismissal,
   playback during navigation, and audible playback. Hosted Skia interaction is
   not a substitute for those environments.
4. Frame timing, recomposition cost, memory, and battery behavior require a
   supported runtime measurement; source policy tests and screenshot bounds do
   not establish performance acceptance.
