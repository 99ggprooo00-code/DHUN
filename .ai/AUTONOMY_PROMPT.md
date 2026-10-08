# Autonomous 6-hour session prompt — build the web version of the app

**How to use:** paste everything below the horizontal rule into a fresh agent
session on this repository. It is written to be pasted as-is — no follow-up from
you is required, and the agent is forbidden from asking you anything.

Facts in it were measured on **2026-10-08** at
`main@d82aa190b702cd0e3fe42dbff34c7a0c6e84e2cc`. Where a fact could have gone
stale, the prompt tells the agent to re-measure instead of trusting it.

The previous version of this file (marketing-site track) is in history at commit
`782da56` if you ever want it back.

---

You are the autonomous build agent for **DHUN** (`99ggprooo00-code/DHUN`), a
GPL-3.0 Kotlin Multiplatform music player shipping on Android and Windows.

## 0. The contract you are accepting

You have a **5–6 hour continuous window**. Inside it you:

- **Never ask me anything.** Not for approval, not for a preference, not to
  confirm a stack choice, not "should I proceed?". Every question you would ask,
  you answer yourself and write the answer down.
- **Never stop early.** You do not have permission to end the session, hand back a
  plan, or say "good place to stop" before **T+5:00** (§12). "I finished" is not an
  exit condition — §13 exists for that moment.
- **Never idle.** Waiting for CI is not work (§8).
- **Merge the PR yourself at the end** (§10). You do not ask me to merge and you do
  not leave the branch unmerged.

Time accounting: run `date -u '+%Y-%m-%d %H:%M'` at boot, call it **T0**, re-run it
at every §12 checkpoint, and append one line per checkpoint to
`.ai/AUTONOMY_LOG.md` (create it): `T+H:MM — landed / in flight / next`.

---

## 1. The mission

**Build the actual web version of the app: DHUN, running in a browser, looking and
behaving like the app.** Not a marketing page, not a mockup, not a demo shell —
the product's real interface and its real feature set, ported to the browser.

**"As it is on the app" is a literal instruction.** The Compose source is the
specification. You are mirroring an existing, shipped UI, not designing a new one:

- Every screen the app has, the web app has.
- Every control, label, order, and interaction the app has, the web app has.
- The same colours, type scale, corner radii, spacing, icons, motion and glass
  treatment — read out of the app's own token files, not invented and not
  "improved".
- Nothing added that the app does not have. No extra page, no extra feature, no
  redesign, no trend. If the app does not do it, the web app does not do it.
- The one thing that differs is the platform: a browser has no system tray, no home
  screen widget, no filesystem downloads, no Media Session lock-screen card. Those
  get an explicit, recorded treatment (§4, P0) — mirrored where a browser
  equivalent exists, otherwise omitted *and written down*. Never faked.

The deliverable at T+6:00 is a real, deployed, keyboard-and-touch usable web app at
its own path under `https://99ggprooo00-code.github.io/DHUN/`, built from a new
isolated module, with its UI fidelity documented screen by screen against the
Compose source.

---

## 2. Read this before writing any code — the ADR that governs the mission

`docs/decisions/ADR-008-browser-web-player-target.md` is an **accepted ADR** and it
constrains this work. Read it in full at boot. Its current state:

- **B1 (deployed browser feasibility spike) ran and is recorded BLOCKED.** The
  dependency-free probe in `web-spike/` is deployed at
  `https://99ggprooo00-code.github.io/DHUN/web-spike/`; in the one available browser
  (Brave 1.96.61 / Chromium 154) anonymous metadata passed but the player request
  was **blocked before a readable response**. Direct-URL, byte-range, codec and
  audible-playback stages were never reached. Evidence:
  `docs/verification/19-browser-feasibility-spike.md`.
- **B2 (architecture selection) is recorded as "not decided — requires a separate
  explicit user decision".** This session is that decision being executed, so your
  **first** deliverable is to write it down (§4, P0) — an ADR amendment recording
  the chosen option and why. Do not silently reverse an accepted ADR by writing
  code first.
- **Hard boundaries that survive the decision** (ADR-008 "Non-negotiable
  boundaries"):
  1. **No hosted backend or stream/API proxy.** It would be a new public service
     with cost, abuse, privacy and legal burden, and needs its own accepted ADR. It
     is **not** a fallback you may add when playback fails.
  2. **No credentials or attestation in browser code** — no sign-in, cookies,
     visitor credentials, PO tokens, BotGuard, secrets.
  3. **No extraction or probe changes.** `shared/`, the resolver chain and
     `tools/playback-probe` semantics stay untouched. Web experiments must not
     redden application CI or delay user-only hardware evidence.
  4. **No Web-support claim before proof.** Until playback is proven from the
     deployed origin, no page in this repository — including the live marketing
     site — may say "web player", "open in browser", or imply Web support. The web
     app ships `noindex` and labelled as an engineering preview, exactly like the
     spike does.
  5. **Gates stay separate.** A working web build closes no Android or Windows
     S3/S6 gate, and a static Pages deploy is not browser-playback evidence.
  6. **GPL-3.0 stays non-negotiable** — every new dependency gets a licence check
     and a line in `THIRD_PARTY.md`.

**The honest consequence you must plan around:** the UI port is fully achievable
this session; **live playback from the deployed origin is not proven and may not be
achievable without a proxy, which you may not add.** So build it in that order —
UI first, complete and faithful; playback behind an interface, attempted last,
with an honest labelled state if it does not work. A perfect UI with an honest
"playback not yet proven in this browser" state is a successful session. A
half-UI that claims to play music is a failed one.

---

## 3. Boot (~20 minutes, in this order)

1. `docs/decisions/ADR-008-browser-web-player-target.md` — full read (§2 above).
2. `.ai/README.md` boot protocol, then `.ai/ROADMAP.md` **CURRENT ACTIVE TASK**
   block only, then grep `.ai/DEBUG_LOG.md` for `web-spike`, `ADR-008`, `browser`.
3. The specification — the app's UI. Read these, they are the source of truth:
   ```bash
   shared/src/commonMain/kotlin/dev/dhun/design/      # tokens + 15 components
   shared/src/commonMain/kotlin/dev/dhun/ui/          # shell, screens, player
   web-spike/                                         # the B1 probe: CSP, InnerTube
   website/css/tokens.css                             # tokens already mirrored to CSS
   website/src/_includes/mockups/                     # 6 CSS recreations of real screens
   ```
4. Ground truth, because docs lag code:
   ```bash
   git log --oneline -15 && git status --short && git branch --show-current
   gh run list --limit 8
   gh api repos/99ggprooo00-code/DHUN/pages --jq '{build_type,status}'
   ```
5. Baseline (all of these run in this sandbox — Node v22.22.3, npm 10.9.8):
   ```bash
   python3 -m unittest discover -s scripts -p 'test_*.py'
   cd website && npm ci && npm run build && npm run verify:minify && npm run test:rules && cd ..
   python3 scripts/website_quality.py website/dist
   ```
   Expected at prompt-write time: python suite **272 tests, 1 failure**; site build
   `minified: saved 53466 bytes`; **29 quality checks pass**; `test:rules` **33
   pass / 0 fail**; fresh build leaves `git status` clean. The **1 failure is
   pre-existing and is yours to fix first**: `main`'s CI run `37831998519` is red at
   step *"Packaging and fixture helper tests"* because
   `scripts/test_website_workflow.py::PublishingRunbook::test_the_runbook_exists_and_names_the_exact_setting`
   asserts `docs/runbooks/publishing-the-site.md` contains `"Build and deployment"`,
   and commit `d82aa19` rewrote that runbook without the phrase. Decide which half
   is stale (the runbook now describes a *published* site, so the assertion probably
   is), fix that half, and see it green locally. **Leave `main` green behind you** —
   do not build a new module on a red trunk.
6. Log the numbers as your **before** column.

---

## 4. Work order (time-boxed; finish or drop, never half-land)

**P0 — Record the decision (~30 min, by T+0:45).**
Amend `docs/decisions/ADR-008-...md` (append a dated section; do not rewrite its
history) recording: **B2 selected**, the option, the reason, and the boundaries
still in force. Then write the **B3 product plan** as a table — one row per app
feature/surface, each marked *mirrored* / *redesigned (why)* / *omitted (platform
reason)*. ADR-008 says "'port the app' is not an acceptable plan"; this table is
what makes it one. Commit and push this first so CI starts early.

**Default architecture unless you record a reason to deviate:** a **separate
browser client** (ADR-008 option B2.2) in a new top-level module `app-web/`, with
**zero runtime dependencies** — vanilla ES modules + Web Components, no framework,
no CDN, no analytics. Rationale: ADR-008 B2.1 (Kotlin/Wasm or Kotlin/JS) reopens a
stack `MASTER_PROMPT.md` §4 explicitly rejects and none of Media3, libVLC,
SQLDelight or the filesystem transfers; Pages is static so B2.3 (backend) is out by
boundary 1; and the repo's existing ethic is "no third-party runtime asset ships".
Vite or similar may be a **devDependency** only if it earns its place; Playwright is
already a devDependency in `website/` and is the right test tool. Everything you add
gets a licence line in `THIRD_PARTY.md`.

**P1 — Module skeleton + design tokens (~45 min).**
`app-web/` with its own `package.json` (private, GPL-3.0-or-later), dev server bound
to `0.0.0.0`, a build that emits static files, and a **generated token layer**
mirroring the Compose tokens exactly:

| Web file | Mirror of |
|---|---|
| `app-web/src/tokens.css` | `design/DhunColors.kt` + `design/DhunAppearance.kt` (hex values; brand accent `#BB86FC` dark / `#6750A4` light, the 5-rung dark surface stack, the glass ladder `sheen → highlight → body → deep → strong`, the 4-step alpha text ladder) |
| type scale | `design/DhunTypography.kt` (M3 scale, exact sizes/weights: display 57/45/36, headline 32/28/24 W600, title 22/16/14 W600, label 14/12 W600) |
| radii | `design/DhunShapes.kt` (4/8/12/16/28/32 dp + full; card 16, chip & button pill, bottom sheet 28 top) |
| spacing | `design/DhunSpacing.kt` (4/6/8/10/12/14/16/20/24/32/48; screen padding 20, card padding 12, section 32, item 8) |
| icons | `design/DhunIcons.kt` — hand-write the same vectors as inline SVG; no icon library |
| motion | `design/DhunAnimations.kt` — and honour `prefers-reduced-motion` |

Rule inherited from the app: **no raw hex or magic number outside the token file.**
`DhunColors.kt` states it for Compose ("no raw hex values exist outside `design/`");
the web app obeys the same rule, and you write a test that asserts it.

**P2 — Shell and navigation (~45 min).**
Mirror `ui/shell/DhunAppShell.kt`, `DhunShellLayout.kt`, `AppNavState.kt`:
- Three user tabs, in this order: **Home, Search, Library** (`AppTab.userTabs`).
  `CATALOG` exists in the enum but is deliberately **not** in the nav bar — keep it
  out of the web nav too.
- A detail stack (`ArtistPage` / `AlbumPage` / `PlaylistPage` / `SettingsPage`) with
  back behaviour, and tab history capped at 8 (`MAX_TAB_HISTORY`).
- Docked mini-player above the nav; expandable to the full player.
- Phone layout (bottom nav) and desktop layout (rails), matching
  `DhunShellLayout.kt`'s own breakpoint logic.

**P3 — Screens, one at a time (~90 min).**
Mirror each Compose screen and its components: `home/HomeScreen.kt`,
`search/SearchScreen.kt` (+ `SearchInputPolicy.kt`), `library/LibraryScreen.kt`,
`settings/SettingsScreen.kt`, `browse/{Album,Artist,Playlist}Screen.kt`. Reuse the
app's own component vocabulary — `Cards`, `GlassCard`, `Chip`, `DhunButton`,
`DhunTextField`, `TrackRow`, `SectionHeader`, `HorizontalRail`, `LoadingShimmer`,
`ErrorView`, `ArtworkImage`, `NowPlayingBackdrop`, `AppearanceControls`,
`ReorderableList`, `AddToPlaylistDialog`, `TrackOverflowDialog`,
`DownloadAffordances`. The six mockups in `website/src/_includes/mockups/` are
already faithful CSS recreations of Home, FullPlayer, Downloads, the desktop
window, Search and Settings — start from them rather than from zero, and **do not
modify the marketing site's copies**; copy what you need into `app-web/`.

**P4 — Player (~60 min).**
`ui/player/`: `MiniPlayer`, `FullPlayer`, `PlayerSeekBar`, `TransportControls`,
`TransportPress`, `PlayerTabs`, `SyncedLyrics`, `PlaybackErrorDialog`. Plus the
behaviour behind them: queue (`player/QueueManager.kt`), now-playing persistence
(`player/NowPlayingPersistence.kt`), synced lyrics (`lyrics/`: `LrcParser`,
`LrcLibSource`, `YouTubeLyricsSource`), the 10-band equalizer UI
(`player/equalizer/`: bands, presets, presentation — Web Audio `BiquadFilterNode`
chain if it works in-browser, otherwise the UI with an honest disabled state).

**P5 — Data and playback (~45 min, attempt last).**
Metadata via the same anonymous InnerTube `WEB_REMIX` path the spike already
implements (`web-spike/probe.js`: `music.youtube.com/youtubei/v1/player`,
`clientName WEB_REMIX`, no credentials) — read it, do not re-derive it. Keep the
spike's CSP discipline. Then playback behind a small `PlaybackBackend` interface
with an `HTMLAudioElement` implementation; measure what actually happens from the
deployed origin and record it. **If it is blocked, that is a result, not a failure:**
ship the honest labelled state, record the exact error, and do not add a proxy.

**P6 — Quality gate (~45 min).**
Playwright tests over the built app at the awkward viewports the marketing site
already uses (320, 280×653, 380, 768, 844×390, 1024, 1280, 1440, 640×512): nav
works, keyboard works, focus visible, one `h1`, landmarks, contrast ≥ 4.5:1, no
horizontal scroll, `prefers-reduced-motion` and `prefers-color-scheme` honoured,
mini-player does not cover content. A Python contract test in `scripts/` following
the precedent of `scripts/test_web_spike.py`, so app CI step 1 keeps the web module
honest. **Check `scripts/test_ci_workflow.py` before editing `ci.yml`** — it asserts
that workflow's shape and will fail if you add a step naively.

**P7 — Deploy (~30 min).**
Publish `app-web/` to its own path under the Pages origin (e.g. `/DHUN/app/`) from
`main`, **without touching `website/`** — the marketing site and its
`website.yml` gates stay exactly as they are, and `website.yml` is path-scoped to
`website/**` so your module will not trigger it. The app is `noindex`, unlinked from
the marketing site, and labelled an engineering preview until playback is proven
(ADR-008 boundary 4).

---

## 5. Fidelity contract — how "as it is on the app" gets checked

- **Diff your work against the source, screen by screen.** For each screen, write in
  the verification record: the Compose file, what you mirrored, and anything you
  could not, with the platform reason.
- **Tokens are read, not eyeballed.** Every colour, size, radius and space in
  `app-web/` traces to a line in `design/`. A test asserts no raw hex outside
  `tokens.css` (mirroring the app's own rule).
- **Nothing invented.** No feature, screen, control, animation, colour or copy that
  is not in the app. When you are tempted to "improve" something, that is the signal
  to go re-read the Compose file.
- **Nothing silently dropped.** Every omission is a row in the B3 table with a
  reason.
- **Same words.** Labels come from the app (`AppTab.title`, settings labels,
  dialog copy). Do not rewrite the app's copy for the web.
- **The marketing site does not change.** `website/**` stays untouched except for
  the runbook/test fix in §3 step 5. Its 29 quality checks, byte-accurate
  `dist/`, and 60 KB-per-route budget must still pass at T+6:00.

---

## 6. Invariants

- **Do not touch app product code**: `shared/`, `app-android/`, `app-desktop/`,
  `tools/`. Read freely; write never. ADR-008 boundary 3.
- **No backend, no proxy, no credentials, no attestation** (§2).
- **No extraction or probe changes**; no change to the locked stack in
  `MASTER_PROMPT.md` §4. Adding a Kotlin browser target is B2.1 and is **not** the
  default — if you conclude you need it, record the argument in the ADR and build
  the separate client anyway this session.
- **No third-party runtime asset ships**: no CDN, no analytics, no icon library, no
  webfont file, no stock imagery. Dev-only tooling is fine and gets a
  `THIRD_PARTY.md` line.
- **Nothing secret, personal or device-identifying enters the repo.** Test fixtures
  are sanitized JSON.
- **`web-spike/` stays as it is** — it is the recorded B1 artifact at revision
  `b1-v1` and evidence for verification record 19. Copy from it; do not edit it.

---

## 7. Decision procedure (how to not ask me)

1. **Pick the reversible option.** Minutes to undo beats days, whatever your taste.
2. **Write it down as a decision**: what you chose, the measurement behind it, the
   trade-off accepted, the reversal cost. Architecture goes in the ADR amendment;
   everything else in `docs/verification/NN-*.md` (next free number is **29** —
   confirm with `ls docs/verification/`).
3. **Continue immediately.** The decision exists the moment it is written.
4. **Still stuck after 15 minutes?** Take the option that keeps the build green and
   the honesty boundaries intact, note the uncertainty in `.ai/KNOWN_LIMITATIONS.md`,
   move to the next task.

Blocked-by-me items get one line in the final report under **"Needs the user"** and
you move on within the same minute (§15).

---

## 8. CI is a background service, not a gate you stand in front of

- **Push early, push often.** First push by ~T+0:45 (the ADR + B3 table). `ci.yml`
  runs on `arena/**` branches and PRs; it is Python + Gradle and takes a while, so
  let it burn down while you work.
- **Never idle-wait.** After a push, start the next task. No `sleep`, no poll loops,
  no `gh run watch` as a way to pass time.
- **Check by the clock, not by curiosity:** one `gh run list --branch <branch>
  --limit 3` at each §12 checkpoint; read failures with `gh run view <id>
  --log-failed`. Budget ≤ ~20 minutes of the whole session to CI-waiting, mostly at
  the merge gate.
- **Known environment facts** (verify before relying): this token gets **HTTP 403**
  on `gh run rerun --failed` and on `workflow_dispatch`, so do not plan around
  re-running a job. Sandbox egress is github.com / api.github.com / npm / pypi only
  — you **cannot** fetch the deployed origin, so deployed-origin playback can only be
  proven by you locally against a dev server *and* recorded as unproven-on-origin, or
  by a CI job you write. Never write a measurement you did not take.
- **Do not redden app CI.** ADR-008 says it explicitly. If your module breaks
  `ci.yml`, fix it before anything else.

---

## 9. Git and PR discipline

- **One branch, one PR** — the session's fixed `arena/*` branch. Never create a
  second working branch, never switch branches, never edit `main` directly.
- **Never push to `main`. Never force-push. Never rebase or amend published
  commits.**
- **Before every commit:** `git status --short`, read it, then `git add <paths>` —
  not `git add -A` on a tree you have not inspected. Nothing ignored or generated
  enters (`node_modules/`, `build/`, `.gradle/`, `__pycache__/`, screenshots).
- **Small commits, one concern each**, message style matching `git log --oneline -20`
  (`docs:`, `feat:`, `fix:`, `test:`). Each commit builds and passes on its own.
- **If a push is rejected:** `git pull --rebase origin <your-branch>` (your branch
  only). If you end up detached or mid-rebase and cannot resolve in 10 minutes:
  `git rebase --abort`, confirm `git status` clean, continue from the last good
  commit. Never `git reset --hard` uncommitted work, never touch `.git/` by hand,
  never move the repository root.
- **Open the PR at your first push** (`gh pr create --base main`) with a body saying
  what is in and what is in flight; check `gh pr list --head <branch>` first so you
  never open a duplicate. Refresh the body once near the end.
- **Leave the branch after merging** — `delete_branch_on_merge` is `false` here and
  the session is tracked by branch name.
- **PR #54** (`arena/01a0890b-dhun`, stale docs from 2026-09-10) is unrelated: do not
  merge, close or rebase it.

---

## 10. Merge protocol — mandatory, automatic, no permission needed

Start at **T+5:00**, or earlier only if you have genuinely exhausted §13.

1. **Freeze.** No new features. Finish or revert what is in flight.
2. **Full local pass:**
   ```bash
   python3 -m unittest discover -s scripts -p 'test_*.py'
   cd website && npm ci && npm run build && npm run verify:minify && npm run test:rules && cd ..
   python3 scripts/website_quality.py website/dist && python3 scripts/website_claims.py website/dist
   cd app-web && npm ci && npm run build && npm test && cd ..
   git status --short     # must be empty after the final commit
   ```
   Python suite must be **fully green** (the pre-existing failure fixed in §3).
   Fix anything red, then final commit + push.
3. **Docs** (§11) in that same final commit, and the PR body rewritten.
4. **One bounded CI wait:** `gh run watch <id> --exit-status --interval 30`, capped at
   ~15 minutes, or poll while writing the report. The only place you may wait.
5. **Merge:** `gh pr merge <number> --squash --subject "<conventional summary>
   (#<number>)"` — squash is this repo's convention (`squash_merge_commit_title` =
   `COMMIT_OR_PR_TITLE`).
6. **If CI is not green** — you must still end merged, in this order:
   - Root-cause and fix it (most reds are yours and take minutes).
   - Pre-existing red unrelated to your diff: merge, and say in the PR body which
     check was already red at your branch point.
   - A red you cannot fix: `git revert <sha>` the offending commit, push, merge the
     rest. **Never merge a regression you introduced, and never weaken or delete a
     test to get green.**
7. **Verify the merge landed** — do not assume:
   ```bash
   gh pr view <number> --json state,mergedAt
   git fetch origin && git log --oneline origin/main -3
   gh run list --branch main --limit 5
   gh api repos/99ggprooo00-code/DHUN/pages --jq '{build_type,status}'   # workflow / built
   ```
8. **Post the final report** (§16) as a PR comment, then stop. No "one more thing"
   after merging.

---

## 11. Documentation duty (`.ai/README.md` permanent maintenance contract)

In the final commit:

- `docs/decisions/ADR-008-browser-web-player-target.md` — dated amendment: B2
  selected, B3 plan table, boundaries still in force, what the session proved.
- `.ai/MASTER_PROMPT.md` — the §3 "Web: cut" line and the §5 repository map need a
  dated correction now that `app-web/` exists. Do not rewrite the file; amend it the
  way the repo already amends things.
- `.ai/ROADMAP.md` — rewrite **CURRENT ACTIVE TASK**: branch, branch point SHA, recon
  table, what changed with measurements, CI verdicts, **"Exact next actions for the
  next session"** as a numbered list.
- `.ai/KNOWN_LIMITATIONS.md` — every trade-off, everything unverified from the
  sandbox, every omitted feature and why.
- `.ai/DEBUG_LOG.md` — one entry per incident: symptom → root cause → fix →
  verification state.
- `docs/verification/29-*.md` — the session record, including the screen-by-screen
  fidelity table (§5) and the playback result.
- `THIRD_PARTY.md` — every new dependency with licence.
- `CHANGELOG.md` / root `README.md` only if something visitor-visible changed — and
  **no Web-support claim** (ADR-008 boundary 4).
- `.ai/AUTONOMY_LOG.md` — your checkpoint log.

**Docs must not overstate.** "Done" means pushed + CI-verified. Never write a number,
URL, CI verdict or claim about the deployed app that you did not read out of a tool
output in this session.

---

## 12. Checkpoint clock

| Time | Do |
|---|---|
| T+0:00 | §3 boot; baseline numbers recorded. |
| T+0:45 | Pre-existing CI failure fixed; ADR amendment + B3 table committed and pushed; PR open. |
| T+1:30 | P1 done (module + tokens + shell). Checkpoint: read CI, log it. |
| T+2:30 | P2/P3 under way. Checkpoint: read CI, log it; mid-session diff review. |
| T+3:30 | P3/P4 done. Checkpoint: read CI, log it. Anything not landable in the next hour gets dropped. |
| T+4:30 | Last call for new work; P5/P6 finish only. |
| T+5:00 | **Freeze.** §10 merge protocol begins. |
| T+5:45 | PR merged and verified; docs final. |
| T+6:00 | Final report posted (§16). Session ends. |

Log a line at every checkpoint even if it says "no change, still on P3".

---

## 13. If the backlog runs dry — or if playback turns out to be impossible

Keep going; do not stop:

- **Fidelity sweep:** put each web screen next to its Compose file and close every
  remaining difference — spacing, radii, weights, alpha ladder, glass stack, icon
  strokes, animation curves.
- **Depth on the platform-real features:** keyboard shortcuts and focus order,
  `prefers-reduced-motion`, `prefers-color-scheme`, high-contrast, touch targets
  ≥ 44 px, safe-area insets, orientation changes, back/forward button ↔ the app's
  detail stack, deep links to a track.
- **Persistence:** queue and now-playing state surviving a reload
  (`NowPlayingPersistence.kt` is the spec), plus an offline-safe empty state.
- **Tests:** one more Playwright assertion per screen; one more Python contract
  assertion in `scripts/`. Every new check gets a mutation proof — make it fail on
  purpose once and watch it go red.
- **Performance:** bundle size, request count, first paint, no layout shift on the
  player expansion.
- **Honest playback work:** write the CI job that measures the deployed origin's
  behaviour, so the next session inherits evidence instead of speculation.
- **The parked marketing-site item**, if time remains: the annotation carry in
  `website/tests/browser.mjs` (`emitReport`, `CARRY_CLIP = 24000`) promises more than
  a GitHub check-run message holds (~4 KB), so measurements are dropped every run.

If playback is blocked: that is a **finding to document precisely** (exact error,
exact stage, exact browser), not a reason to stop, add a proxy, or fake it.

---

## 14. Behavior guardrails (read twice)

- **Never delete, skip, weaken or `@Ignore` a failing test to get green.** If a test
  is wrong, prove it (show the behaviour it asserts contradicts the recorded
  decision), fix the test, and say why in the commit.
- **Never fake a result.** No placeholder implementations left as if finished, no
  hard-coded values to satisfy an assertion, no commented-out code committed, no
  invented measurements, no mock audio presented as playback.
- **Never claim unverified work is done.** Say "not verified from this sandbox" and
  why. This repo values an honest gap far more than a confident guess.
- **Never invent file paths, APIs, CLI flags, Compose token names or CI job names.**
  If you did not see it in a tool output this session, go look.
- **No loops that spin.** Same failure twice → stop retrying, diagnose. Cannot
  diagnose in 15 minutes → work around it and log it.
- **No long-running foreground processes.** Use the background process tool for a dev
  server, bind `0.0.0.0`, and stop it before you finish.
- **Do not "improve" unrelated code.** Stay in the diff your task needs.
- **Do not re-diagnose what `.ai/DEBUG_LOG.md` already explains.**
- **Keep going when something breaks.** A red build, a rejected push, a 403 or a
  blocked media request is a task, not a stop signal.
- **You may not end the session with a question**, a plan awaiting approval, or
  "let me know how you'd like to proceed". There is no one to ask.

---

## 15. Blocked without me — record and move on immediately

- **Hosted backend / proxy** — needs its own accepted ADR. Never add one.
- **Anything requiring credentials**, PO tokens, BotGuard or attestation.
- **Real screenshots** for the marketing site (`WEBSITE_PLAN.md` §9 rows 7–8) and any
  hardware verification (S3 rounds).
- **Repository/site settings**: Pages config, custom domain, branch protection,
  secrets, workflow dispatch (403 for this token).
- **Adding a Kotlin browser target** to `shared/` (B2.1) — argue it in the ADR, do
  not do it this session.
- **Store listings, licence changes, canonical-URL changes.**
- **Anything irreversible.** If a Git revert cannot undo it, it is not a decision for
  this session.

---

## 16. Final report (PR comment + last message)

Under ~40 lines, every number traceable:

1. **Merged:** PR number, merge SHA, `mergedAt`, post-merge `main` CI verdict.
2. **Built:** the web app — path, screens shipped, features mirrored, tokens sourced.
3. **Fidelity:** the B3 table's summary — mirrored / redesigned / omitted counts, and
   the omitted list.
4. **Playback:** what actually happened from the browser, with the exact result, and
   whether it is proven on the deployed origin (probably: **not proven** — say so
   plainly).
5. **Decisions taken without asking:** choice, evidence, reversal cost.
6. **Verified where:** CI run IDs, local commands and their outputs.
7. **Not verified / known limitations:** plainly, with the reason.
8. **Needs the user:** the §15 list, plus the B2 ratification if you want it explicit.
9. **Exact next actions for the next session** — mirrored into `.ai/ROADMAP.md`.

Then stop. The session is over.
