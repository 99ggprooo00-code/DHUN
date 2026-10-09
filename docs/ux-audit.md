# DHUN — UI/UX and Navigation Audit

**Audit date:** 2026-10-09  
**Repository:** `99ggprooo00-code/DHUN`  
**Scope:** source-level inspection of the current UI, website templates, AI operating documents and design ADRs. This is not a claim of a complete hands-on visual test: a source inspection cannot establish how every screen renders on real devices.

## Remediation status on `fix/ui-download-feedback-website-links`

- **Clear all downloads:** the ViewModel now exposes clearing/success/error state, prevents concurrent clears, and rethrows coroutine cancellation. The dialog stays open while work is pending, reports a retryable failure, and dismisses/clears selection only after success. A JVM failure-and-retry test was added. **Not yet CI-verified.**
- **Website base path:** templates now route internal links through the `sitePath` filter. The website workflow builds a root artifact for existing local browser/quality tests and a second `/DHUN/`-prefixed artifact for Pages, with a prefix smoke check. **Not yet workflow-verified or confirmed at the deployed origin.**
- **AI instructions:** current visual target and active task are reconciled in `.ai/MASTER_PROMPT.md`, `.ai/ROADMAP.md`, `.ai/KNOWN_LIMITATIONS.md`, `.ai/DEBUG_LOG.md`, `.ai/WEBSITE_PLAN.md`, `.ai/HANDOFF_NEXT_SESSION.md` and `.ai/README.md`. Older notes are retained as history, not current restrictions.
- No claim is made that the native build, full website workflow, Android/desktop hardware checks, or post-deployment route checks have passed. Those require their actual evidence.

### Latest verification evidence (2026-10-09)

On code-equivalent commit `650a65e`, Build APK succeeded and the website build/prefix smoke and browser-measurement jobs passed. On the preceding code-equivalent documentation head `90bee52` (before this status-note commit), run `37869983686` (Build APK) was green; run `37869983672` has the build/quality/prefix-smoke and browser jobs green while Lighthouse remains in progress; run `37869983670` has shared-domain and Robolectric steps green while overall CI remains in progress. Run `37869983669` has the APK job green and MSI still running. PR deploy and served-origin checks are skipped; post-merge canonical-origin verification and native hardware checks remain open. This status note advances the documentation head, so fresh final-head workflows are expected; the earlier results are evidence for unchanged code, not a green verdict for the new head. These are workflow snapshots, not claims that every job has passed.

## Severity definitions

- **P0 — data loss / trust:** user action can silently fail or destroy the wrong scope.
- **P1 — release-blocking navigation / accessibility:** core routes, controls or information are unreachable or misleading.
- **P2 — major UX inconsistency:** visible behavior, design or feedback contradicts the product contract.
- **P3 — polish:** non-blocking visual/interaction refinement.

## Findings

### P0 — Clear all downloads hides failure

**Evidence**
- `shared/src/commonMain/kotlin/dev/dhun/ui/library/LibraryScreen.kt`: the confirmation's `onConfirm` calls `onClearAll()`, clears selection and sets `showClearConfirm = false` immediately.
- `shared/src/commonMain/kotlin/dev/dhun/presentation/library/LibraryViewModel.kt`: `clearDownloads()` launches `dm.clearAll()` and wraps it in `runCatching` without exposing success/failure to the UI.

**Impact:** the dialog can close and selection can clear even if the operation fails; the exception is not surfaced. The user cannot distinguish success from a failed or partial delete. Whether active/partial downloads and files are consistently removed also needs an explicit tested contract.

**Remediation in branch:** observable pending/success/failure state; duplicate-submit guard; retryable failure in the dialog; selection and dialog reset only on success. Remaining verification: test all states on CI and hardware, including active/queued/paused/failed/partial downloads and filesystem/database divergence.

**Regression tests:** cancel is a no-op; confirm calls manager once; repeated tap is blocked; success refreshes list and storage; failure keeps remaining data visible; active job and partial-file cleanup; DB/file divergence; retry; restart recovery.

### P1 — Website internal links can escape the GitHub Pages project path

**Evidence**
- The public project URL is under `https://99ggprooo00-code.github.io/DHUN/`.
- `website/src/_includes/base.njk` uses root-absolute internal links including `href="/"`, `/features/` and `/ui/`.
- `website/src/ui.njk` and `website/src/404.njk` also contain root-absolute internal links.
- A repository search did not find a `pathPrefix` / `basePath` helper. The static templates therefore need explicit base-path handling unless the actual build output proves an equivalent prefixing step exists.

**Impact:** a page may work in local root hosting but navigation from the deployed project site can send users to `github.io/features/` rather than `github.io/DHUN/features/`. This matches the symptom “the main page is not linked with the other pages” more closely than a missing nav component: the source already has a shared header/footer, but emitted URLs may be wrong.

**Remediation in branch:** an Eleventy `sitePath` filter is used for internal wordmark, header, footer, CTA, interface and 404 links; the Pages workflow builds a separate `/DHUN/` artifact and smoke-checks internal paths while preserving external links. Remaining verification: workflow result and canonical-origin click-through.

**Regression tests:** Home → Features → Interface → Home; every route's footer/header; direct route load and refresh under `/DHUN/`; Back/Forward; 404 recovery; no root-escaping links; exactly one correct current-page marker.

### P1 — Public site deployment state and source tree can disagree

**Evidence:** repository notes have contained conflicting/historical Pages status claims: the current active roadmap reports Pages as workflow-built, while older README/known-limitations sections describe legacy Pages serving the README. This audit does not infer which bytes a visitor currently receives from prose alone.

**Impact:** even correct templates and links do not help if the canonical URL serves a stale artifact or a different source. Visitors may see an unlinked README rather than the intended website.

**Required fix:** query Pages/workflow status and fetch the canonical origin after deployment; compare served route/content with the commit. Update README and `.ai/KNOWN_LIMITATIONS.md` from the same evidence. Record a served-site smoke test; “workflow succeeded” is not equivalent to “canonical origin serves the intended bytes.”

### P1 — Design decision contradicts the user's current visual direction

**Evidence:** `docs/decisions/ADR-002-fullscreen-player-design.md` said “Material 3 only” and “Liquid Glass forbidden,” while `.ai/MASTER_PROMPT.md` describes a premium glassy, artwork-driven design. The user's updated instruction is translucent frosted glass rather than Material 3 as the visual identity.

**Impact:** AI agents can follow the old ADR and repeatedly rebuild the wrong appearance even if the PRD/UI brief says “glassy.”

**Required fix:** update ADR-002 and UI/UX spec together. Frostered glass is the target; Compose/Material components may be implementation primitives. Keep blur lightweight/cached, use tint/scrim, and avoid a heavy 3D renderer.

### P2 — Interaction tests can pass while real controls are broken

**Evidence:** `.ai/DEBUG_LOG.md`, `app-web/README.md` and `.ai/KNOWN_LIMITATIONS.md` state that DOM-stub/markup tests prove wiring but do not prove rendered pixels, real clicks/touch or browser behavior. The log also records an “Add to playlist” action-name collision that reopened its own sheet.

**Impact:** a label/button can exist in markup while its handler repeats the wrong action, overlays block clicks, focus gets lost, or the layout clips the target.

**Required fix:** each visible control must have one unique semantic action and a state-transition test. Add browser interaction/visual checks when an engine is available; test open vs confirm separately; verify overlay hit-testing, focus and Escape/back behavior.

### P2 — Dialogs and frosted forms need a consistent material policy

**Evidence:** `.ai/DEBUG_LOG.md` records that clear-downloads/clear-history and other dialogs were changed from default `OutlinedTextField` to `DhunTextField`, and their `GlassCard` surfaces were given `opaqueBase = true` to satisfy the component's own dialog rule.

**Impact:** mixing default component styles or insufficiently opaque glass can produce unreadable inputs, bleed-through, inconsistent controls and fragile dialogs.

**Required fix:** use shared Dhun form components and shared glass/dialog policies; add checks for bright/dark backdrop contrast, dialog focus, text entry, small-window overflow and cancel/confirm.

### P2 — App, marketing website and browser mirror are easy to conflate

**Evidence:** the repo has native Android/Desktop product code, a static `website/`, and separate `app-web/` engineering mirror. The browser mirror README says it has no audible playback, sample catalogue fallback and is not deployed as the product.

**Impact:** plans can promise a React/Tauri app or working web player when neither is the current production architecture; website CTAs can mislead users toward an engineering preview.

**Required fix:** all product documents distinguish native app, marketing site and browser mirror. No production web claim or link to a fake player until ADR-008 gates are passed and the deployed-origin result is verified.

### P2 — Documentation is not synchronized to the current repository

**Evidence:** earlier PR #147 planning drafts proposed React/Tauri and treated Web as a product platform, while the current repo is Kotlin Multiplatform/Compose and has separate marketing/browser surfaces. Current .ai files also preserve historical decisions and old status snapshots.

**Impact:** future AI-generated work can implement a second architecture, create speculative backend tables, or mark unverified features complete.

**Required fix:** treat current source + accepted ADRs + current CI/hardware evidence as truth; label historical plans as history; use a single docs index; maintain explicit “present in source / unit-tested / CI-green / hardware verified / deployed-site verified” status.

## Verification checklist

- [ ] Confirm Clear all downloads cancel, pending, success, failure, duplicate tap and partial-file semantics with interaction tests.
- [ ] Audit all destructive actions: clear history/cache, batch delete, playlist delete, remove download, reset preferences.
- [ ] Add project-base-aware URL generation to website templates and sitemap.
- [ ] Crawl every generated route/anchor and assert every internal link resolves.
- [ ] Test all site routes from the actual deployed `/DHUN/` origin and verify the served commit.
- [ ] Test native navigation/back stack and overlay hit targets on Android and desktop.
- [ ] Visually inspect dialogs, FullPlayer, lyrics mode, list rows, short screens and wide two-pane layout on hardware.
- [ ] Verify glass contrast, fallback, reduced motion and high contrast.
- [ ] Keep `app-web/` visibly labelled as an engineering preview until its limitations are removed and approved.
- [ ] Update `.ai/ROADMAP.md`, `.ai/KNOWN_LIMITATIONS.md` and relevant verification record with actual evidence.

## Limits of this audit

This is a source/documentation audit. It does not claim that all app screens have been manually rendered or tested on hardware, nor that the canonical website currently serves a specific byte sequence. Those are explicit follow-up verification gates, not assumptions.
