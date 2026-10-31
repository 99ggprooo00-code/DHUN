# 43 — DHUN glass restoration, stages 5–9

Date: 2026-10-31. Branch: `arena/8a70a040-dhun`.

## Status and evidence boundary

The stage diffs are implemented and the available Python/source suite passes.
This is **not visual or device acceptance**. This workspace has no JDK, Android
SDK, emulator, ADB/phone, or interactive desktop display. Kotlin compilation and
JVM/Android/desktop tests require GitHub CI; screenshots, navigation interaction,
artwork review, large text/TalkBack, blur fallback, and audible playback remain
**NOT RUN** unless separately evidenced.

Persistent-update phone recovery is a linked but independent acceptance gate.
Nothing in this visual-stage record proves its cause or fresh/update behavior.

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

## Remaining acceptance

1. Successful CI on the corrected cumulative Stage 7/8 head and the exact Stage
   9 head, including Kotlin policy tests and Android/desktop compilation.
2. Real screenshots and interaction on Android and desktop where supported:
   dark, OLED, and light; bright/dark artwork; Home/Search/Library; player,
   queue/lyrics/Related; Settings/dialogs; navigation and non-blur fallback.
3. Large text, keyboard/focus, screen-reader labels/live regions, pending/error
   states, and reduced-motion review in actual environments.
4. The independent persistent-update recovery matrix and actual phone
   acceptance before any recovery merge claim.
