# Native restoration and DHUN glass plan

## Status

Phase 0 source inventory only. This document records what the rollback removed,
what still exists, and the order of implementation. It is not a test result or a
claim that any stage below has shipped.

## Baselines

- Restoration baseline / current `main`: `1e2f7cb0893ecd42c5b1a811348cefb0a4cddd01` (merged #217).
- Pre-#196 native snapshot retained by #217: `12498caa02607edd74504161f388fc2e11d34a4c`.
- Working branch: `arena/8a70a040-dhun`.
- Existing branch commits after the baseline are beta-asset protection and
  persistent-update recovery work. They are prerequisites to retain, not code
  copied from the PRs below.
- All native stages must preserve the website trees and the four-installer
  beta-asset policy. No release publication or merge is implied by this plan.

## Requested PR inventory

| PR | Head / merge commit | Behaviour removed by rollback | Useful pieces still in current tree | Restoration decision |
| --- | --- | --- | --- | --- |
| #211 playlist deletion | `1ac6c1a9f0a4f490017118b3aea40834ca0b1753` / `1ec8c0650c2e66fa915e6dcf8ec51db78c9bea1d` | Synchronous pending gate, success-only navigation, visible failure/retry, deletion state tests, transaction/data-preservation regressions | Local detail screen, repository transaction, SQLDelight observers, playback queue | Restore the behaviour in its own stage. Do not take its website/release changes. |
| #212 mutation results | `9ef934b3bf722943915b9b07202fa707f8581d7f` / `5a7a1b6bc01b768090bdc78db5bc30ebc223aba9` | `MutationTracker`, scoped pending/errors, result-driven dialogs, banners, add-to-playlist duplicate reporting, mutation tests | Repository/use-case APIs, screen models, custom dialogs/buttons | Restore infrastructure and then wire controlled slices: Settings, Library/downloads, likes/playlists. Keep each operation's failure visible rather than hiding it behind shared state. |
| #214 settings persistence | `a95deb059b403ddc481e869ffc64ed5cd9fceb1e` / `3ce83ddfd0d1bd62402eb4f80776679ba1c866c9` | Atomic persisted snapshot load, rollback after failed writes, read-failure defaults/notice, full live reset including equalizer, restart/write-failure tests | Settings keys/defaults, repository observation, serialized write mutex, equalizer use case | Rebuild after the mutation foundation. Preserve the later country normalization and persistent-recovery changes on this branch. |
| #215 Library management + old glass | `ee998cb15c4c887a4ca0f603da79e4e61b6a7c42` / `067bd15d8288c81a63c1dd4ee03093b6d48735ea` | Playlist overflow actions, rename/delete confirmation state, detail synchronization and action tests; a large mixed visual rewrite | Playlist rows, local detail screen, rename/delete repository methods | Restore only Library playlist management now. The old glass UI is a research input, not a wholesale implementation source. |
| #216 background controls | `53fd061268078a1288d626a55a68de804a86a487` / `942d2dfcf49327db09f3177d292f674a0e3a8178` | Five-palette background model and many technical background controls | Artwork color extraction, blur support, artwork cache, existing backdrop controls | Research during design implementation. Prefer a small set of useful defaults/settings; do not restore the five-palette model or control count automatically. |

## Existing design foundation

The current shared Compose app is used by Android and desktop. It already has
DHUN appearance/color/spacing/shape/typography tokens, artwork utilities,
blur support and cache, glass cards, content-row surfaces, custom buttons and
text fields, page/now-playing backdrops, and the familiar navigation/player
structure. These remain reusable.

Thirty-seven files under `ui` and `design` currently import Compose `material3`.
That count is an audit signal, not a request to remove the dependency: technical
primitives may remain, but stock Material appearance must not define the product.

At the Phase 0 inventory, active guidance was inconsistent and some files
contained toolkit-as-identity language or unimplemented background models.
Stage 9 makes `docs/design/glass-system.md` the single authoritative DHUN glass
specification and aligns `docs/TRD.md`, `docs/ui-ux-design.md`,
`docs/implementation-plan.md`, `.ai/MASTER_PROMPT.md`, `.ai/ROADMAP.md`,
`.ai/README.md`, `.ai/KNOWN_LIMITATIONS.md`, `.ai/HANDOFF_NEXT_SESSION.md`, and
`CHANGELOG.md`. Dated reports keep old facts only as labelled history.

## Delivery stages and gates

| Stage | Product scope | Required evidence before stage acceptance |
| --- | --- | --- |
| 0 | This inventory | Exact baselines, removed/remaining behaviour, dependency order; separate pre-existing recovery work identified |
| 1 | Safe local-playlist deletion | Real SQLite rollback/reopen/data-preservation tests; pending, duplicate, success-only, failure/retry, uncertain-outcome and cancellation tests; Android/desktop compile via CI |
| 2 | Observable mutations | Tracker unit tests plus operation-specific Settings, Library/download and likes/playlists tests; no false success or swallowed write failure |
| 3 | Settings persistence | One-snapshot load, rollback, restart/read/write failures, shared defaults and stored+live equalizer reset tests |
| 4 | Library playlist management | Rename/delete confirmation, pending, success-only UI update/removal, retry and detail synchronization tests |
| 5 | Glass foundations | DHUN tokens for separate dark/light schemes, elevation/fallback policy, shared component tests; no navigation changes |
| 6 | Screen surfaces | Home/Search/Artist/Album/Playlist/Library with recognisable structure and artwork ambience; source and supported visual QA |
| 7 | Playback surfaces | Full player, queue and lyrics using the same DHUN material/typography rules; playback behaviour preserved |
| 8 | Settings and dialogs | Consistent controls/dialogs plus simplified background preferences informed by #216; settings persistence retained |
| 9 | Design documentation | One authoritative specification, corrected active guidance, accessibility and fallback rules, examples of correct/incorrect implementation |

Every implementation stage gets its own reviewable commit/PR scope, exact-head
CI, and a changed-file review. CI compilation/tests are necessary but are not
device acceptance. Physical-device interaction, audible playback and visual
screenshots remain explicitly unverified where no hardware/display is available.

## Visual and regression guardrails

- Preserve screen order, navigation, information architecture and established
  interactions; visual stages do not silently redesign flows.
- Design light mode independently for bright and dark artwork. Do not derive it
  by inverting the dark scheme.
- Use glass selectively; avoid per-row blur, excessive transparency, stacked
  gradients and expensive full-screen effects.
- Keep accessible text/control contrast and visible keyboard/touch states.
- Retain local artwork, queues, favourites, history, playlists, settings and
  downloads. A cleared-data/reinstall result is not update recovery.
- Do not add fake content, providers, paid services or unrelated web work.
