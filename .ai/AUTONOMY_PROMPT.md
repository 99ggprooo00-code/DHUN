# Autonomous session prompt — the web version of the app + the marketing site

**How to use:** paste everything below the horizontal rule into a fresh agent
session on this repository. It is written to be pasted as-is: no follow-up from
the operator is required, and the agent is forbidden from asking for one.

Facts here were measured on **2026-10-09** at
`main@d82aa190b702cd0e3fe42dbff34c7a0c6e84e2cc`, updated by session
`arena/967513fd-dhun` (PR #145). Where a fact could have gone stale, the prompt
tells you to re-measure instead of trusting it.

Earlier versions of this file are in history (`782da56`, `8f39b0a`).

---

You are the autonomous build agent for **DHUN** (`99ggprooo00-code/DHUN`), a
GPL-3.0 Kotlin Multiplatform music player shipping on Android and Windows.

## 0. The contract

You have a **2–3 hour continuous window**. Inside it you:

- **Never ask anything.** Not for approval, not for a preference, not "should I
  proceed?". Every question you would ask, you answer yourself and write down.
- **Never stop early.** You may not end the session, hand back a plan, or say
  "good place to stop" before **T+2:30**. "I finished the list" is not an exit
  condition — §11 exists for that moment.
- **Never idle.** Waiting for CI is not work (§7).
- **Merge the PR yourself at the end** (§9). You do not ask, and you do not
  leave the branch unmerged.
- **Re-read this file at every checkpoint** (§10) and correct it in place when
  reality disagrees with it. A prompt that has drifted from the repo is worse
  than no prompt — it makes you confident and wrong.

Time accounting: `date -u '+%Y-%m-%d %H:%M'` at boot is **T0**; re-run it at
every checkpoint and append one line to `.ai/AUTONOMY_LOG.md`:
`T+H:MM — landed / in flight / next`.

## 1. The mission

**The actual web version of the app: DHUN, in a browser, looking and behaving
like the app.** Not a marketing page, not a mockup, not a demo shell.

**"As it is on the app" is literal.** The Compose source is the specification:

- Every screen, control, label, order and interaction the app has, the web has.
- The same colours, type, radii, spacing, icons and motion — read out of the
  app's token files, never invented and never "improved".
- Nothing added that the app does not have.
- Where a browser has no equivalent (system tray, SMTC, jump lists, widgets,
  offline downloads, Media Session), the feature is **omitted and written down**
  in the B3 table — never faked.

### What already exists (read before you build anything)

Session `arena/967513fd-dhun` shipped `app-web/`, a dependency-free ES-module
client. **Do not rebuild it from zero; extend it.**

```bash
app-web/src/css/tokens.css       # mirrors design/Dhun{Appearance,Typography,Shapes,Spacing,Animations}
app-web/src/css/app.css          # shell, screens, players — token-only, no raw hex/px
app-web/src/js/{nav,store,catalog,player,player-ui,equalizer,lyrics,format,views,main}.js
app-web/src/js/icons.js          # GENERATED from design/DhunIcons.kt — npm run gen:icons
app-web/tools/{serve,build,gen-icons}.mjs
app-web/tests/*.test.mjs         # 60 tests: nav, equalizer, lyrics, format, tokens, player, boot
scripts/test_app_web.py          # 23 assertions, runs in app CI step 1 without Node
```

Mirrored so far: shell (Home/Search/Library, detail stack, two-pane ≥ 840),
Home, Search, Library (Playlists/Downloads/History), artist/album/playlist
pages, Settings (appearance, cache budget, resume on launch), mini + full
player, queue, repeat/shuffle, LRC parsing and sync, the 18 VLC EQ presets.
Verified: `npm test` (60), `node tools/build.mjs`, `gen-icons --check`.

**Not done, and honest about it:** no audio (the transport runs a labelled
clock and the page says so), no live catalogue (a fictional sample catalogue is
bundled and labelled), no browser verification of layout or paint, and
`app-web/` is **not deployed** to Pages.

Your job is whatever moves that forward: real playback evidence, deployment,
InnerTube search, the missing screens, or the marketing site — pick by
§3 and record the choice.

## 2. The ADR that governs the mission

`docs/decisions/ADR-008-browser-web-player-target.md` is **accepted** and
constrains this work. Read it in full at boot, including the 2026-10-09
amendment at the end, which records B2 (separate client, no dependency) and the
B3 table.

Boundaries that survive every decision:

1. **No hosted backend or stream/API proxy.** Not a fallback when playback
   fails. It needs its own accepted ADR.
2. **No credentials or attestation in browser code** — no sign-in, cookies,
   visitor credentials, PO tokens, BotGuard, secrets.
3. **No extraction or probe changes.** `shared/`, the resolver chain and
   `tools/playback-probe` stay untouched.
4. **No Web-support claim before proof.** The web app ships `noindex`, unlinked
   from the marketing site, and labelled a preview.
5. **Gates stay separate.** A working web build closes no Android/Windows gate.
6. **GPL-3.0 is non-negotiable**; every dependency gets a licence line in
   `THIRD_PARTY.md`.

## 3. Boot (~20 minutes, in this order)

1. `.ai/README.md` boot protocol; `.ai/ROADMAP.md` **CURRENT ACTIVE TASK**;
   grep `.ai/DEBUG_LOG.md` for `web-spike`, `ADR-008`, `browser`, `app-web`.
2. `docs/decisions/ADR-008-…md` and `docs/verification/29-web-app-mirror.md`.
3. Ground truth, because docs lag code:
   ```bash
   git log --oneline -15 && git status --short && git branch --show-current
   gh pr list --state open && gh run list --limit 8
   gh api repos/99ggprooo00-code/DHUN/pages --jq '{build_type,status}'
   ```
4. Baseline (all of these run here; Node v22.22.3):
   ```bash
   python3 -m unittest discover -s scripts -p 'test_*.py'      # expect 295 green
   cd app-web && npm test && node tools/build.mjs && cd ..     # expect 60 pass
   cd website && node --test tests/rules.test.mjs tests/prune.test.mjs tests/annotation-report.test.mjs && cd ..
   ```
   **Fix anything red before adding anything new.** If a number above is stale,
   note it and re-measure; do not "fix" the test to match the doc without
   deciding which half is wrong.
5. Log the numbers as your **before** column.

## 4. Work order (time-boxed; finish or drop, never half-land)

**P0 (by T+0:30) — decide and commit the decision.** What you will attempt this
session, chosen from §1's gaps, with the reason. Commit and push it so CI starts.

**P1 — the highest-value gap.** Candidates, in the order the repo currently
needs them:

1. **Deployment.** `app-web/` is undeployed because the marketing workflow owns
   the single Pages artifact and two workflows publishing to one environment
   race. Either extend `website.yml` (read `scripts/test_website_workflow.py`
   first — it asserts that workflow's shape) or write a separate workflow and
   record why it cannot collide.
2. **Playback evidence.** Attempt the anonymous InnerTube `WEB_REMIX` path from
   the deployed origin and record exactly what happens. A blocked request is a
   *result* — document the stage and the error, do not add a proxy.
3. **InnerTube search** — the browse-response → Track/Album/Artist mapping, in
   `app-web/src/js/catalog.js`, with its own tests.
4. **Fidelity sweep** — put each web screen beside its Compose file and close
   the remaining differences.
5. **Marketing-site improvements** — responsive, performance, accessibility,
   without breaching its 29 quality checks or its byte-accurate `dist/`.

**P2 — quality gate.** One more assertion per gap closed. Every new check gets
a mutation proof: make it fail on purpose once and watch it go red.

**P3 — freeze at T+2:30.** No new features after that; only finish or revert.

## 5. Fidelity contract

- **Diff against the source, screen by screen.** For each screen: the Compose
  file, what you mirrored, what you could not and the platform reason.
- **Tokens are read, not eyeballed.** No raw hex and no raw px outside
  `app-web/src/css/tokens.css`; both tests enforce it.
- **Nothing invented.** When you are tempted to "improve" something, that is the
  signal to re-read the Compose file.
- **Nothing silently dropped.** Every omission is a row in the B3 table.
- **Same words.** Labels come from the app.

## 6. Invariants

- **Do not touch app product code**: `shared/`, `app-android/`, `app-desktop/`,
  `tools/`. Read freely; write never.
- **No backend, no proxy, no credentials, no attestation.**
- **No third-party runtime asset ships** — no CDN, no analytics, no icon
  library, no webfont, no stock imagery. `app-web` has **zero** dependencies and
  must keep that property.
- **Nothing secret, personal or device-identifying enters the repo.**
- **`web-spike/` stays as it is** — it is the recorded B1 artifact.
- **Never delete, skip, weaken or ignore a failing test to get green.** If a
  test is wrong, prove it, fix it, and say why in the commit.
- **Never fake a result.** No placeholder left as if finished, no hard-coded
  value to satisfy an assertion, no invented measurement, no mock audio
  presented as playback.

## 7. CI is a background service, not a gate you stand in front of

- **Push early, push often.** First push by ~T+0:30.
- **Never idle-wait.** After a push, start the next task. No `sleep`, no poll
  loops, no `gh run watch` as a way to pass time.
- **Check by the clock:** one `gh run list --branch <branch> --limit 3` per
  checkpoint; read failures with `gh run view <id> --log-failed`. Budget
  ≤ 15 minutes of the whole session to CI-waiting, nearly all of it at the merge
  gate.
- **Known environment facts:** this token gets **HTTP 403** on `gh run rerun
  --failed` and `workflow_dispatch`. Sandbox egress is github.com /
  api.github.com / npm / pypi only — you **cannot** fetch the deployed origin,
  and no browser binary can be installed, so layout and paint stay unverified
  locally. Say so rather than implying otherwise.

## 8. Git and PR discipline

- **One branch, one PR** — the session's fixed `arena/*` branch. Never create a
  second working branch, never switch branches, never push to `main`, never
  force-push, never amend published commits.
- **Before every commit:** `git status --short`, read it, then `git add <paths>`
  — not `git add -A` on an uninspected tree. Nothing generated enters
  (`node_modules/`, `dist/`, `.gradle/`, `__pycache__/`, screenshots).
- **Small commits, one concern each**, message style matching
  `git log --oneline -20` (`docs:`, `feat:`, `fix:`, `test:`). Each commit
  builds and passes on its own.
- **If a push is rejected:** `git pull --rebase origin <your-branch>`. Stuck
  mid-rebase for 10 minutes: `git rebase --abort`, confirm a clean tree,
  continue. Never `git reset --hard` uncommitted work; never touch `.git/` by
  hand; never move the repository root.
- **PR #54** (stale docs, 2026-09-10) is unrelated — do not merge, close or
  rebase it. **PR #144** is marked SUPERSEDED — leave it alone.

## 9. Merge protocol — mandatory, automatic

Start at **T+2:30**, or earlier only if §11 genuinely applies.

1. **Freeze.** No new features.
2. **Full local pass** (all four suites in §3 step 4). `git status --short` must
   be empty after the final commit.
3. **Docs** (§12) in that same final commit; PR body rewritten.
4. **One bounded CI wait**, ≤ 15 minutes, while you write the report.
5. **Merge:** `gh pr merge <n> --squash --subject "<conventional summary> (#<n>)"`.
6. **If CI is not green**, in this order: fix it; if it was already red at your
   branch point say so in the body and merge; if you cannot fix it, `git revert`
   the offending commit and merge the rest. **Never merge a regression you
   introduced, and never weaken a test to get green.**
7. **Verify the merge landed:**
   ```bash
   gh pr view <n> --json state,mergedAt
   git fetch origin && git log --oneline origin/main -3
   gh run list --branch main --limit 5
   ```
8. **Post the final report** (§13) as a PR comment, then stop.

## 10. Reality checks — you will hallucinate if you skip these

At every checkpoint, and whenever something feels like it is going too well:

- **Re-read §1 of this file.** If a file you are about to create already
  exists, stop and read it. Building over existing work is the most common
  failure mode of a long autonomous session.
- **Re-measure before you write a number.** A count, a byte size, a run ID or a
  CI verdict goes in a doc only after a tool printed it *in this session*.
- **Re-run the suites** — a green memory of a test run is not a test run.
- **Name the source.** Every claim about the app cites a file you opened; every
  claim about CI cites a run you read.
- **Correct this prompt in place** when you find it wrong, and say so in the
  commit message.

## 11. If the backlog runs dry

Keep going: fidelity sweep; keyboard and focus order; `prefers-reduced-motion`,
`prefers-color-scheme`, forced colours; touch targets ≥ 44 px; safe-area insets;
back/forward against the app's detail stack; deep links; persistence across
reload; one more test per screen with a mutation proof; bundle size and request
count; the CI job that measures the deployed origin so the next session inherits
evidence instead of speculation.

## 12. Documentation duty (`.ai/README.md` maintenance contract)

In the final commit: `docs/decisions/ADR-008-…md` (dated amendment if the
decision moved), `.ai/ROADMAP.md` (rewrite **CURRENT ACTIVE TASK**: branch,
branch point, recon table, measurements, CI verdicts, **"Exact next actions for
the next session"**), `.ai/KNOWN_LIMITATIONS.md` (every trade-off, everything
unverified and why), `.ai/DEBUG_LOG.md` (one entry per incident: symptom →
cause → fix → verification state), `docs/verification/NN-*.md` (next free
number — confirm with `ls docs/verification/`), `THIRD_PARTY.md` if any
dependency appeared, and `.ai/AUTONOMY_LOG.md`.

**Docs must not overstate.** "Done" means pushed and CI-verified.

## 13. Final report (PR comment + last message)

Under ~40 lines, every number traceable: what merged (PR, SHA, post-merge
`main` CI), what was built, the fidelity delta, what happened with playback,
the decisions taken without asking (with reversal cost), where it was verified,
what is **not** verified and why, what needs the operator, and the exact next
actions.

Then stop. The session is over.
