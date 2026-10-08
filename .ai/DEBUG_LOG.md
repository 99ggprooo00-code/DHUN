# DEBUG_LOG — incidents, root causes, environment traps

## 2026-10-08 — seven defects found by asking who would actually see the failure (session `arena/37ec95ed-dhun`)

The session's own rule was "a rule that cannot be made to fail is not a rule", so
every new check was mutated before it was trusted. Five defects were in the
plumbing (three in code I had just written, one in a workflow filter, one in my
own hand-written numbers). The sixth was reported by the person who owns the
repository, looking at the public URL.

**1. `forced_colors_violations` read the union of all pages' CSS and passed a
per-page deletion.** `site_stylesheet()` joins the inlined CSS of every route for
rules that ask "does the site define X". Deleting the `@media (forced-colors:
active)` block from `index.html` alone left the other two copies in the union, so
the rule stayed green: a mutation that *should* have failed. Fix: the rule now
iterates `page_paths()` and reads `page_stylesheet(dist, route)` — each page's own
`<style>` — so a single route losing the block is a red test. Lesson: a rule over
a *union* of pages cannot see a per-page omission; ask which page you mean.

**2. `print_style_violations` only checked that the tokens were *mentioned*.**
`--text:#ffffff` on a white `--bg` passed — a sheet that prints blank. Fix: the
rule parses the print block's `--text`/`--bg` and computes the WCAG ratio with the
same maths the token rule uses (`1.00:1, below the 4.5:1 floor` when mutated).
Lesson: "the mechanism exists" and "the mechanism works" are two rules; the cheap
one belongs in Python, the expensive one in the browser job.

**3. `checkTabStops` passed already-computed signatures to `focusChanged`.**
`focusChanged(before, after)` computes `focusSignature()` itself, so handing it two
strings produced `"undefined|undefined|undefined…"` on both sides and the rule
could never fire. Caught while re-reading the new code, before any push. Fix: the
in-page probe returns a style *record* and the pure rule does the folding — the
page and the rule now share one definition instead of two.
Lesson: a pure function that takes the *input* of a comparison must not be fed the
comparison's output; the type names were the clue and the tests did not cover the
call site, only the function.

**4. `scripts/report_lighthouse.py` was used by `website.yml` but missing from
both `paths:` filters.** A change to the only reader of the Lighthouse reports
could not start the workflow that runs it. Fix: both filters list it *and* its
test, and `test_website_workflow.py` gained a derived rule — every
`scripts/*.py` the workflow runs must appear at least twice (push and
pull_request). Mutation: removing one path from the filter fails with "is used by
website.yml but does not trigger it on both push and pull_request".
Lesson: hand-maintained lists drift; derive the list from the thing it describes.

**7. The first real browser run died silently, and only CI could have shown it.**
Run **37814413312** (head `89834c0`) was the first execution of the new browser
checks. Result: `Browser measurements` **failure** — and in the only readable
channel, one annotation: "Process completed with exit code 1". No route, no
viewport, no message, no screenshots (the artifact step warned that
`website/tests/screenshots` did not exist). Cause, found by static analysis
rather than by a log — Actions log archives are unreadable here: the page-facing
function `forcedColorsReport`, serialized *into the page* by
`page.evaluate(forcedColorsReport)`, called `forcedColorsBoundaryMissing(...)` —
a function **imported from `./rules.mjs` in Node scope**, which does not exist in
the page. `ReferenceError` in the page → `evaluate` rejects → the script threw
before `emitAnnotations()` → exit 1, nothing said. The very architecture this
session adopted (gather in the page, decide in Node) was violated in one line,
and a crash was mistaken for a result. Two fixes, both mutation-proved: the page
function now returns the raw styles and Node applies the rule, and the runner
guards every check (`guard(name, run)` recording a crash as a failure) and emits
the summary and annotations from a `finally` block even when something throws. A
static rule in `scripts/test_website_workflow.py` now fails if any function
passed to `page.evaluate` by name references an import — the class of bug, not
just the instance.

**6. Everything knew the canonical URL was not the site; nobody was told.**
`README.md` line 2 advertised <https://99ggprooo00-code.github.io/DHUN/> as the
marketing site while Pages is configured `build_type: legacy` / `main:/`, so
Jekyll renders the repository README there (`status: errored`; the API answers
HTTP 403 to the token here). The plan and the limitations file said so; the
deploy job even printed a `::warning::` naming the fix — but that job only runs
on a push to `main`, so a pull request, a run summary and every visitor saw
nothing, and the README went on promising a site that is not there. **A correct
warning nobody reads is a defect of discoverability, not a pass.** Fix: the
`build` job now reports `build_type`, the URL and the exact setting on every
trigger (summary + warning), `docs/runbooks/publishing-the-site.md` records the
switch and its verification, the README says plainly what that URL serves today,
and six tests fail if any of that regresses — including one that fails if the
warning is ever upgraded to an error, because a setting an agent cannot change
must not redden a build that is otherwise green.

**5. Two commit messages carried test counts I had not read.** `6240fa6` claims
"178 → 194 (10 icon/inlining + 6 reporter naming + 1 derived trigger rule)" — the
breakdown adds to 15, and `48f5cd9` claims "215 → 231" while the suite reported
225 (`Ran 225 tests`). The totals in those messages came from the tool; the
parentheticals did not. Both are corrected here rather than rewritten: history is
append-only in this project, and a wrong number in a commit message is a
documented slip, not a silent one. Measured counts after this session's work:
**`Ran 232 tests in 0.672s` → OK** on the Python-only suite (178 at boot), plus
**`# pass 9` / `# fail 0`** for `website/tests/rules.test.mjs` under `node --test`.
Lesson: every digit in a commit message is evidence and follows the same rule as a
number in a doc — read it from a tool output or do not write it.

## 2026-10-08 — the site denied a feature the app ships, and the config silently dropped a stylesheet (session `arena/9b791057-dhun`)

Two defects that only a *product* site can have: one was the copy lying about the
app, the other was the build lying about success.

**1. "No Android equaliser yet" was false.** The features page listed it as an
open item while the tree contains
`shared/src/commonMain/kotlin/dev/dhun/player/equalizer/` — `EqualizerBands`
(`COUNT = 10`, libVLC's 60 Hz–16 kHz frequencies, ±20 dB gain), an Android engine
bound to `android.media.audiofx` in `app-android/.../equalizer/`, a desktop
command file, and a full UI section in `ui/settings/SettingsScreen.kt` with
presets, preamp and per-band sliders. A negative list is a claim like any other,
and nothing checked it against the tree — the claims rules only reject
*positive* over-claims. Fix: the feature catalogue now cites the file behind
every item, the equaliser is listed as shipped, and the negative list was
corrected to "no store channel and no signed release". Policy: before adding a
line to `notInDhun`, grep for the feature — an under-claim is a lie in the
opposite direction and it costs the same trust.

**2. `cssModules: ["...", "ui"]` produced a page with no stylesheet, and the
build stayed green.** The Eleventy filter was
`CSS_MODULES.filter((name) => modules.includes(name))`: an unknown module name
simply vanished, so `/ui/` shipped unstyled. The class-coverage rule caught it
*by accident* — because the page's classes were "used but not defined" — which
is a long way from an error message. Fix: the filter now iterates the page's
declared modules and `readModule` throws on an unknown name. Policy: when a
configuration list filters the *input* rather than validating it, a typo becomes
a silent omission; validate the input.

**3. Nested `<section>` broke the traceability scan.** `re.findall` with a
non-greedy `(.*?)</section>` closes the outer section at the *first* inner
`</section>`, so a wrapper section with five sections inside gave five misaligned
"section N" matches and one of them (the last surface) looked citation-free.
Fix: the surfaces are five top-level sections under a plain `<div class="wrap">`
with an `<h2>`. Policy: claim-bearing sections do not nest.


## 2026-10-08 — four tooling traps from rebuilding the site's own checks (session `arena/9b791057-dhun`)

Each of these cost real time in this session. They are recorded because each
one fails *quietly*: the tool that should have complained instead reported
success.

**1. A typo in a Nunjucks comment terminator swallows the template.** The
closing form is `-#}`. A `-*-}` typo makes the comment run to the end of the
file, so the rendered page is missing everything after it — and Eleventy still
prints `Wrote 5 files` and exits 0. A page that builds but has no `</html>` is a
template-syntax bug, not a content bug: grep the template for a mistyped
comment before debugging anything else.

**2. A regex written for a multi-line CSS block silently matches nothing** when
the rule sits on one line. `\.tag-list \{.*?\n\}` never matched because the
deleted rule was one line long; the "dead rule" scan reported a clean sheet for
a rule that was still there. Match on the closing brace, or edit line by line.

**3. A test helper that double-wrapped its own responses.** `served(**overrides)`
built `{route: (status, (status, body))}` and the failure surfaced two frames
away as `AttributeError: 'tuple' object has no attribute 'strip'` inside the
page checker. The helper now holds `(status, body)` and nothing else. A helper
whose value shape is wrong produces an error in the code that *reads* it, which
is the slowest possible place to find it.

**4. `assertNotIn("@axe-core/cli", workflow_text)` fails on a comment that
explains why the tool was retired.** The assertion now targets the invocation
(`assertNotRegex(r"npx[^\n]*@axe-core/cli")`) instead of the word, and the
comment stays: it is the record of why axe-cli went, and the reason the removal
can be verified at all.

**And one that is a trap for the workflow, not for the tests:** writing Python
with regex and emoji through a shell heredoc mangles raw strings — the escaping
survives into the file. Patch programmatically and then prove the file parses
(`python3 -c "import ast; ast.parse(open(path).read())"`).

## 2026-10-08 — the measurement job's first run: three real defects, and two checks that could not name their own findings (session `arena/9b791057-dhun`)

**What happened.** `website` run **37802245177** (head `0570377`) was the first
execution of `website/tests/browser.mjs` anywhere — the sandbox has no browser,
so every earlier responsive/a11y statement in this repository was structural.
It failed on three real defects and, in passing, showed that two of its own
diagnostics were unable to say what was wrong.

**Findings, each read from a check-run annotation:**

1. `a.wordmark “DHUN” is 90.9×28 (44 px floor)` on all three pages — the header
   logo link was under the target floor.
2. `a.no-class “a” is 7.1×14 …` — the superscript footnote links on the front
   page were 6–7 px wide. Inline in a sentence is WCAG 2.5.8's inline
   exception, but that size is not hittable even deliberately, so they now
   carry a 24×24 box.
3. `overflow /download/ @ 320x568: documentElement.scrollWidth 444 > innerWidth
   320` (and at 360). Real horizontal overflow — and `body { overflow-x: hidden }`
   meant no scrollbar, so it presented as content cut off at the right edge.

**Why the browser check could not name the offender.** `overflowReport` skipped
any element with an ancestor whose `overflow-x` was not `visible` — reasoning
that such a container clips or scrolls its own content. Every element on this
site has one such ancestor: `body { overflow-x: hidden }`. So the offenders
list was permanently empty while the document was measurably 444 px wide. The
walk now ignores `body` (its `overflow-x` is propagated to the viewport, so it
does not clip), and a second pass reports elements whose *content* is wider than
their own box — spill that no border-box scan can see.

**Why the annotations were incomplete.** GitHub returns roughly ten annotations
per level per check run. The script emitted one `::error` per route per
viewport, so the failures pushed the *measurements* out of the only channel this
environment can read. Findings are now grouped by category into at most eight
error annotations, with the full text still on stdout.

**Why the Lighthouse job went red for no stated reason.** It ran `/` at the
median of three samples (98, 100, 100 → **100** across all four categories,
FCP=LCP=SI 1106 ms, TBT 0 ms, CLS 0.000, 49.9 kB, 2 requests) and then exited 1
on the next route without printing any `::error`. Nothing in this environment
can read the step log, so the failure was invisible. Each sample is now retried
once, and a sample that fails twice reports an `::error` annotation carrying the
tail of its own log. The gate is unchanged.

**Also learned:** a `name: … (Playwright: …)` line in a workflow is invalid YAML
(an unquoted `: ` in a plain scalar). It parses as a block mapping and GitHub
rejects the run before any job starts. There is no YAML parser in the sandbox
(`pypi` is unreachable), so `scripts/test_website_workflow.py` now carries
`yaml_hygiene_violations` for exactly this class of mistake, and the *file* was
also validated by parsing it with the `yaml` package in a throwaway directory —
a local check, not a repository dependency.

## 2026-10-08 — CI found a contrast defect the site's own checks were written to miss, and axe cannot run (session `arena/fc918d37-dhun`)

**What happened.** The first `website` run on this branch (`37794857026`,
head `e496464`) was green in the build job — build, minification proof, drift
check, honesty contract, quality gates and `html-validate` all passed in CI —
and red in the a11y job with accessibility **95** on `/features/` and
`/download/` (`/` scored 100). The check-run annotations carried the numbers but
not the failing audit, and Actions log archives are unreachable here, so the
cause had to be reasoned from the numbers.

**Root cause.** `--text-4` is 0.45-alpha white: ~3.9:1 on the dark surfaces,
below the 4.5:1 body-text floor. It painted exactly two things — `.traceable`
(the "source: ADR-002, PR #68" line) and the `.missing` list markers — and both
appear on `/features/` and `/download/` but **not** on `/`, which is the shape
the scores showed. The site's own contrast gate had not caught it because it
asserts the *declared token pairs* (`--text-3` on `--bg` and friends), not every
use of every token; `--text-4` was never wired to a text role in that table.

**Fix.** That text moved to `--text-3` (7.5:1); `--text-4` stays defined for
non-text affordances only, and a new test
(`test_no_text_uses_the_faintest_token`) fails the build if any `color:`
declaration uses it again. The next run (`37795271256`, head `e6cbb42`) scored
100/100/100 on all three routes.

**Also learned, and now reported instead of hidden:**

- `@axe-core/cli` exited 1 on all three routes **without writing a report** —
  a driver/browser handshake failure, not a verdict. The workflow now emits an
  explicit `::warning::` naming that, so a future run cannot mistake silence for
  a pass, and `scripts/report_axe.py` reports real violations as annotations if
  the tool ever does run.
- The job now prints every failing Lighthouse audit id as its own annotation,
  because "accessibility=95" alone is not actionable without the log archive.
- `PUT /repos/99ggprooo00-code/DHUN/pages -f build_type=workflow` returns
  **HTTP 403 "Resource not accessible by integration"**: the agent token cannot
  switch the Pages source. The deploy job therefore detects `legacy` and skips
  the three publishing steps with a warning that names the one-line human fix,
  instead of failing red on every push to `main` for a setting it cannot change.

## 2026-10-08 — the site's own gates caught three of its claims while it was being built (session `arena/fc918d37-dhun`)

**This entry records defects the honesty/quality gates found, not app incidents.**
They are here because each one is a trap that a later session will hit again.

1. **A blanket `sync` rule flagged a real feature.** The first version of the
   "no cross-device sync" rule matched every occurrence of *sync*, which made
   the truthful line *"Synced lyrics from LRCLIB"* a build failure. The rule now
   reads `(?<!lyrics )(?<!lyric )\bsync(s|ed|ing)?\b(?!\s+lyrics?\b)`, so
   synced lyrics pass and "syncs your library across devices" fails. The
   Android permission constant `FOREGROUND_SERVICE_DATA_SYNC` deliberately does
   not match either — `_` is a word character — which is why the downloads page
   names the constant instead of paraphrasing it.
2. **Negation was allowed one clause too far.** The first rule accepted a
   negation anywhere in the previous clause, so *"DHUN has no accounts. Sync
   across devices is coming next year."* read as honest. A negation cue must now
   sit in the **same** clause as the mention.
3. **The quality script failed against the minified build.** String checks like
   `@media (min-width: 480px)` and `--target: 44px` do not survive
   minification (`@media(min-width:480px)`, `--target:44px`). The checks now
   compare a whitespace-free copy, so they pass before *and* after minification
   — otherwise the gate would only ever have proven something about the
   unminified tree nobody serves.
4. **A contrast assertion numerically passed while being inverted.** The ratio
   was computed as `fg / bg` instead of lighter-over-darker, so black-on-violet
   reported 0.13:1. Fixed by sorting the two luminances; the real numbers are
   18.10:1 down to 5.71:1.
5. **Two `data-caveat` mistakes were caught by the mutation proof**, which is
   exactly what it is for: removing the `borrowed-time` attribute (and
   rewording its heading) failed the required-caveat rule, and a pasted SHA-256
   failed the digest rule both in the sources and in the built page. Both were
   reverted; the transcripts are in `docs/verification/20-marketing-site.md`.

**Environment traps re-confirmed for the site work:** Eleventy exposes
shortcodes to Nunjucks as **tag** syntax (`{% icon "play" %}`), not as
expression calls (`{{ icon("play") }}` — that fails with "Unable to call
`icon`"), and Nunjucks `{% include %}` cannot concatenate its path, so the
mockup include names are data, not derived strings. There is still no browser
and no display in this sandbox, so Lighthouse/axe cannot run here at all and
no score may be quoted from this machine.

## 2026-10-08 — ADR-008 B1: the canonical run is BLOCKED at the player request, and the evidence is too thin to name a cause (session `arena/45db02aa-dhun`)

**What happened.** PR #136 merged with explicit authorization as `2a20024`, so
legacy Pages began serving the probe at
`https://99ggprooo00-code.github.io/DHUN/web-spike/` (revision `b1-v1`). All four
post-merge workflows succeeded (CI **37783500689**, Build APK **37783500620**,
test-release **37783500838**, Pages **37783499138**), and the Pages API reports
`legacy`, `main:/`, `built`, no error at the exact commit. The user then ran the
probe from that canonical origin. The UI rendered, and the **anonymous metadata
card passed** — a non-empty title/author response was read and its values were
discarded as designed. The **player-response card failed**: the browser blocked
the request before any readable response, so the byte-range, media and
audible-playback cards were never reached. Recorded in
`docs/verification/19-browser-feasibility-spike.md`.

**Why no root cause is recorded.** This log exists to record causes, and the
honest entry here is that the cause is *not* established. The run supplied no
sanitized JSON, no screenshot and no DevTools network trace — therefore there is
no status code, no `type: "opaque"`/`TypeError` distinction, no preflight result
and no request timing. A `TypeError: Failed to fetch` is consistent with several
different causes, and the probe's card-02 failure message is deliberately
generic. Naming CORS, an `OPTIONS` rejection, Brave Shields, an extension, a
DNS/VPN policy proxy or an upstream refusal would each be an invention.

**Trap 1 — collapsing distinct failures.** The verification record explicitly
separates a blocked/unreadable response from an HTTP or playability answer. Do
not merge those rows to make the result look diagnosed.

**Trap 2 — laundering deployment bookkeeping into browser evidence.** Pages
artifact **11552239018** exists, but its archive redirected to an external blob
host that was unavailable in the previous environment; it was never inspected.
A deployment artifact is not CORS, media or audio evidence, and the deployed
page's markup says nothing about playback.

**Trap 3 — browser substitution.** The only available browser was **Brave
`1.96.61`** based on Chromium `154.0.8037.98`, Official Build, 64-bit. Brave is
Chromium-family, not a stock Chrome/Chromium test, and Firefox and Safari were
**unavailable** rather than failing. `B1 PASS` requires audible playback in
current Chromium *and* Firefox, so this attempt cannot pass on any reading.

**Consequence.** B1 is classified **BLOCKED for the available
Brave/Chromium-family run, cross-browser coverage unavailable, no Web-support
claim**. B1 stops here: no proxy/backend, no extraction or client-profile change,
no browser-stack adoption (Kotlin/JS, Wasm, TypeScript), no static Option-A
implementation. The next step is a separate **ADR-008 B2 user decision**, which
may legitimately be “stop”. The independent S3 round-4/round-5 and S6 gates, and
the user-only AAB staging dispatch, are unaffected and stay open.

## 2026-10-08 — ADR-008 B1: a working static page is not deployed-origin evidence (session `arena/ae65f1a5-dhun`) — *superseded by the entry above; the probe is now deployed and the canonical run is recorded*

**Constraint exposed before implementation.** GitHub Pages is still legacy
`main:/`. A PR branch has no canonical preview URL, and changing Pages source to
the session branch would replace the project's live source before review. That
would be a consequential deployment mutation, not a harmless test. Therefore B1
was implemented as an unlinked `web-spike/` candidate and stopped before any
Pages setting change or merge.

**Smallest honest probe.** The candidate is three static files with no package
manager or framework. It asks one narrow sequence: anonymous oEmbed metadata →
one credential-omitting WEB_REMIX `/player` POST → allow-listed direct
`*.googlevideo.com` candidate → 32 KiB CORS range read → separately clicked
`<audio crossorigin=anonymous>` → explicit tester “I heard audio” confirmation.
Metadata, player response, range access, media events and human audibility stay
separate so one green row cannot launder another red row.

**Privacy/security boundary.** CSP allows only self, the two YouTube origins and
googlevideo media. There is no iframe, autoplay, cookie/credential request,
local storage, proxy, remote image/font, analytics or response-body export.
Ephemeral stream URLs/query strings remain in memory and are removed on reset;
the copied JSON carries only sanitized stage evidence. The default presentation
is synthetic and the upstream test item is the CC-licensed Big Buck Bunny work;
no artwork/title response is rendered or committed.

**What local checks prove.** `scripts/test_web_spike.py` pins that boundary and
the WEB_REMIX identity against `InnerTubeClient`; the full Python suite is now
55 tests. `node --check web-spike/probe.js` proves JavaScript syntax. Serving it
in Arena proves the three assets load. None proves CORS, byte ranges, codecs or
sound because Arena's preview origin is not
`https://99ggprooo00-code.github.io`.

**Remaining verdict.** PR #136 must be reviewed and explicitly authorized for
merge before legacy Pages can expose `/DHUN/web-spike/`. Then real Chromium and
Firefox runs (Safari tested or unavailable) populate
`docs/verification/19-browser-feasibility-spike.md`. A pass or failure both stop
at B2; neither permits a proxy or extraction change.

## 2026-10-08 — the “successful Pages deploy but advertised URL 404s” incident was a hostname mismatch, not an empty deployment (session `arena/ae65f1a5-dhun`)

**Symptom inherited.** README line 2 links
`https://99ggprooo00.github.io/DHUN/`, which returns GitHub's “There isn't a
GitHub Pages site here.” The auto-created `pages-build-deployment` workflow was
green, so the previous session left two hypotheses: branch-source with no index,
or an Actions source publishing an empty artifact.

**The missing check.** The repository is `99ggprooo00-code/DHUN`; the assumed
Pages hostname dropped the `-code` suffix from the owner. The Pages API is the
authority and reports:

```text
html_url = https://99ggprooo00-code.github.io/DHUN/
build_type = legacy
source = main:/
status = built
```

Fetching that canonical URL works and renders the root README through GitHub's
legacy Jekyll build. Post-PR-#135 Pages run **37772062338** succeeded on
`main@8a8d6c5`; Pages build **1269157028** is `built` with no error. Therefore
the successful workflow was honest: it deployed to the URL GitHub configured.
The 404 was the wrong hostname on the project's front door.

**Residual issue and later W0 resolution.** The live canonical page is an
engineering README, not the requested product site. The diagnosis did not guess
the link disposition. Later in this session the user chose “correct now,” so
README line 2 was changed to the canonical `-code` URL. No Pages setting,
workflow, site scaffold or browser-player code changed **at the time of this
diagnosis**. The link was fixed and the product surface remained absent. The
user later accepted ADR-008 B1; the top entry now supersedes this point-in-time
status with the implemented-but-not-deployed probe candidate.

**Workflow noise found while reading the actual annotation.** The legacy Pages
build uses `actions/upload-artifact@v4`, which GitHub now warns targets deprecated
Node 20 and is being forced onto Node 24. The deploy is still green. A future
approved site workflow should use current Pages actions rather than attempting
to edit the auto-created workflow.

## 2026-10-08 — the unread post-merge verdicts for PR #135 were all green; extraction-health was classified from its warning annotation

PR #135 merged as `8a8d6c5` at 11:44:32Z. Runs left queued/in progress by the
previous session all completed: CI **37772063324**, test-release
**37772063364**, Build APK **37772063380** and Pages **37772062338** succeeded.
The rolling publish kept all APK bytes/digests and produced MSI 2.210.1
(112,971,776 B,
`8a2383475947f8b9f5557d584cf0d6f0d34cec489aee3d3a40ab543d8618e298`).
The hosted upgrade annotation proves `2.202.1 → 2.210.1` and sentinel checks,
not playback.

Scheduled extraction-health **37769870656** was not re-labelled from its red
conclusion alone. Its job warning says exactly: `ENVIRONMENT_BLOCKED — inspect
the probe log and verify playback outside the GitHub runner.` The final
keep-non-zero step then failed deliberately. That is the evidence for the
runner classification, while the warning title — “Extraction health is not a
production pass” — prevents treating it as live playback acceptance.

## 2026-10-08 — the API-24 lint gate covered one of the two Android modules (session `arena/8be68e2c-dhun`, base `main@6f1e6ba`, PR #135)

**The gap.** PR #134 added `Android Lint — API 24 floor (NewApi)` running
`:app-android:lintDebug`, and mutation-proved it. Its `KNOWN_LIMITATIONS` entry
then hedged the obvious follow-up question: *"`shared/src/androidMain` is
analysed only through `:app-android` lint over its library classes. That is an
expectation, not a checked fact."*

**The expectation is false.** Android Lint analyses the module it runs in. AGP
does not lint a module's library dependencies unless `lint.checkDependencies` is
set, and this repo never sets it. So the 7 expect/actual Android files under
`shared/src/androidMain` — `AndroidConnectivityMonitor`, `BlurSupport`,
`DownloadHttpClient`, `DatabaseDriverFactory`, `StorageSpace`, `CurrentOffset`,
`Clock` — were entirely outside the floor gate. Second, independent gap:
`shared/build.gradle.kts` had `lint { abortOnError = false }`, so even if
something had run `:shared` lint, it could not have failed the build.

**Fix (`46583a4`).** `shared` now has `lint { checkOnly += setOf("NewApi");
abortOnError = true; checkReleaseBuilds = false }`, and `ci.yml` runs
`:shared:lintDebug` as its own named step after the app-module one (so a red
floor violation names the module that broke). `scripts/test_ci_workflow.py` grew
39 → 45 tests pinning both steps *and* both Gradle lint blocks, because a
workflow step naming a task whose gate is switched off is green theater.

**Why a green lint run is not evidence.** Lint that analyses nothing exits 0
too. The proof is the probe: commit `5f74af3` added
`shared/src/androidMain/kotlin/dev/dhun/LintMutationProbe.kt` with an unguarded
`android.app.NotificationChannel(...)` (API 26 vs floor 24). CI pull_request
**37765481344** failed **step 10 only** — steps 1–9 success, 11–14 skipped —
with, verbatim:

```
Lint found 1 errors, 0 warnings. First failure:
/home/runner/work/DHUN/DHUN/shared/src/androidMain/kotlin/dev/dhun/LintMutationProbe.kt:18:
Error: Call requires API level 26 (current min is 24): android.app.NotificationChannel() [NewApi]
Execution failed for task ':shared:lintDebug'.
```

One message, four facts: `:shared:lintDebug` exists on a KMP
`com.android.library` module under AGP 8.7.2 / Kotlin 2.1.20; it analyses
`shared/src/androidMain`; it reads this module's `minSdk` as 24; and
`abortOnError = true` really aborts. Build APK **37765481328** and test-release
**37765481508** stayed green, so the failure was the gate and nothing else.
Reverted in `a66b342`; `git diff --stat 46583a4 a66b342` is empty.

**Environment note that shaped the work.** There is no JDK, Gradle or Android SDK
in the maintenance sandbox (`java` is absent from PATH; the SDK host is outside
the network allowlist), so nothing Gradle-shaped can be checked locally. The one
locally runnable gate is `python3 -m unittest discover -s scripts -p
'test_*.py'` — CI step 1 — which is exactly why the contract tests were put
there. Actions **log archives** are still unreachable
(`results-receiver.actions.githubusercontent.com` → EOF), but check-run
**annotations** are reachable over REST and carried the lint message above.

## 2026-10-08 — the S6 App Bundle path had zero automated coverage (session `arena/8be68e2c-dhun`, part 2)

**How it was found.** Looking for the next agent-lane gap after the lint gate,
the ledger row that mattered was not a test result but an *absence*: on the main
push run **37759720804** the `aab` job is **skipped**, as it is on every PR.
Reading `test-release.yml` shows why —
`if: ${{ github.event_name == 'workflow_dispatch' && (inputs.build_release_candidate == true || inputs.publish_v010_draft == true) }}`.
S6 requires a clean-installed AAB, and nothing was building one.

**Turned the handoff's parenthetical into a fact.** Sessions had been carrying
"agents get 403 on dispatch" as folklore. Tested directly:

```
$ gh workflow run test-release.yml --ref arena/8be68e2c-dhun \
    -f build_only=true -f build_release_candidate=true -f publish_v010_draft=false
could not create workflow dispatch event: HTTP 403: Resource not accessible by
integration (…/actions/workflows/347450723/dispatches)
```

So the AAB path is unreachable from the agent lane by construction. The fix had
to move it into a workflow that *does* run automatically.

**The latent risk.** PR #131 added `splits { abi { include("arm64-v8a",
"armeabi-v7a"); isUniversalApk = true } }` to `:app-android` — after the `aab`
job had last executed. Whether AGP ignores, tolerates or rejects ABI splits when
building an App Bundle was therefore an unanswered question on the release path,
with the failure scheduled for release day.

**Fix + answer.** `ci.yml` gained
`Android App Bundle compiles (S6 AAB gate)` → `:app-android:bundleDebug`, after
`assembleDebug` so a shared compile break keeps its honest step name; two
contract tests pin it. CI pull_request **37769519510** on `59ac12f`:
18/18 steps, **step 9 success**. AGP 8.7.2 tolerates the splits block for a
debug bundle. Evidence in `docs/verification/18-aab-bundle-gate.md`.

**What it still is not.** Not an install (`bundletool` + device = S6), not the
`aab` job's staging path (`stage_artifact.py` over `app-android-debug.aab`, still
dispatch-only), not Play-readiness (debug keystore), and it asserts nothing about
the bundle's split contents.

## 2026-10-08 — lower-Android commits `5151774` / `b5c349a` were not recoverable (session `arena/688214aa-dhun`)

**What was claimed.** The previous session committed `minSdk` 26 → 24, legacy
`mipmap-anydpi` launcher icons, and ABI-split APKs locally (`5151774`,
`b5c349a`) and could not push before the session closed.

**What this clone actually had.** `git cat-file` on both SHAs fails. `git fetch`
does not find them. `origin/arena/b4449fdd-dhun` is `ff71b2c` — a docs commit
that *sets* the lower-Android task as not started and records PR #130's
post-merge evidence. Working tree at clone was clean at `main@4607e07`. The
"modified files persist in the workspace" recovery path was empty.

**What was done.** The specified result was reconstructed (not cherry-picked):
minSdk 24, non-adaptive `mipmap-anydpi` icons, three published APKs, digest
report that does not fail if the per-ABI APKs differ from the universal. CI on
this head is the compiler. Do not cite `5151774` as an ancestor.

## 2026-10-08 — the rail-layout mini-player floated over the tab content (session `arena/b4449fdd-dhun`, base `main@9f88b6e`, commit `6ef48e9`)

**The report.** On Android **landscape** and Windows **fullscreen**, the user
reported the mini-player "expands and covers the entire home/search/library."
Clarified directly: the **full-screen player is fine and stable**; collapsing it
returns to Home with the mini-player on the bottom — but the **mini-player itself
is so big it covers the whole Home/Search/Library content** (navigation still
works, the list behind is hidden).

**Root cause (source-traced).** Both reported configs have width ≥ 840dp
(`DhunSpacing.navigationRailBreakpoint`), so `DhunShellPolicy.layoutAt` returns
**TwoPane**. In `DhunAppShell.kt`'s `panes == null || detailRoute == null` branch
(the large-screen, no-detail-route case), the tab content (`ShellMasterPane`) and
the `MiniPlayer` were **siblings inside one `Box`**, the mini-player positioned
with `Modifier.align(Alignment.BottomCenter)` — a floating card painted over the
list with no reserved space. The other two placements already dock it: SinglePane
puts it in the `GlassDock` bottom bar (Scaffold `innerPadding` lifts the content),
and `ShellTwoPane` makes it a `Column` child of the master. This branch was the
lone float. The `MiniPlayer` composable is a bounded `miniPlayerHeight` row in an
`AcrylicSurface` (wrap-content height) — it never grows in place; the defect is
the placement, not the widget.

**Fix (`6ef48e9`).** That branch is now a `Column`: content in
`Box(Modifier.weight(1f))`, the mini-player a docked bottom bar (its `.align`
removed — a `Column` child), so it reserves its own height and cannot overlay the
list. Full-screen `FullPlayer` (ADR-002) untouched. Compile-gated by CI (no
Compose UI-test harness exists repo-wide); visual acceptance is the S3 device
retest. Note the same Compose-receiver-scope trap that reddened PR #128
(`38536d5`/`bcd43f3`) applies to moving a composable between layout containers —
the `.align` (BoxScope) removal is the load-bearing edit.

**Also reconciled this session (GitHub evidence, not local impression):** PR #129
is **merged** (`9f88b6e`, 2026-10-08T05:56:11Z); post-merge CI **37734902276**
(12/12), Build APK **37734902281**, test-release **37734902315** (`publish` ran);
rolling `test` republished at `9f88b6e` (2026-10-08T06:01:08Z) — APK `aa6d027a…`
(unchanged from the PR head, since app code did not change between the final head
and the merge) and MSI **2.185.1** `86b1184c…`. The scheduled `extraction-health`
drill is `ENVIRONMENT_BLOCKED` (runner datacenter gating) — the accepted steady
state, **not** a resolver regression.

## 2026-10-08 — a fix that shipped unwired, guarded by a test that could not fail (session `arena/19a284df-dhun`, base `main@ca6d006`, PR #129, commit `643298a`)

**Why this lane.** Boot found PR #128 already merged into `main@ca6d006`
(2026-10-08T05:12:57Z) and the rolling `test` release republished at it
(2026-10-08T05:19:05Z), so the S3 round-2 fixes are live — with no device
retest. The agent lane therefore went to the one thing a sandbox can still do
honestly: re-read the shipped diff of the reported defects instead of trusting
the summary. The Search-Enter fix did not survive that reading.

**The symptom that started it.** PR #128's file list contained both
`SearchInputPolicy.kt` (new) and `SearchInputPolicyTest.kt` (new) *and* an edit
to `SearchScreen.kt` — which should have been one rule and one test. Reading
`main@ca6d006`:

- `SearchScreen.kt:262` had its own inline copy of the rule:
  `if (event.key == Key.Enter && event.type == KeyEventType.KeyDown)`.
  `SearchInputPolicy` was **never referenced by production code** — `grep` for
  it outside its own file and its test returned nothing.
- `SearchInputPolicyTest.kt` did not touch the policy either:

  ```kotlin
  @Test fun shouldSubmitOnEnter() {
      assertTrue(shouldSubmitOnEnter(isKeyDown = true, isEnter = true))
      …
  }
  private fun shouldSubmitOnEnter(isKeyDown: Boolean, isEnter: Boolean) =
      isEnter && isKeyDown
  ```

  A private re-implementation, same name as the concept under test. It passes
  for any state of the policy and of `SearchScreen` — including both being
  deleted.

**Why it matters beyond this one file.** The rule "a test must execute the code
it claims to pin" is the only thing standing between a defect report and a
green build. Here the report was closed with a test that measured a copy, so
CI green meant nothing about the shipped predicate — and the shipped predicate
was reachable only by an inline expression no test could see. That is the exact
shape of a regression that returns silently.

**The fix (`643298a`).** `SearchScreen` calls
`SearchInputPolicy.shouldSubmitOnKeyEvent(event)` (one rule; the four dead
key-event imports removed); the policy names its submit keys
(`Key.Enter` + `Key.NumPadEnter`, the pair `isTransportActivationKey` already
accepts) and keeps the KeyDown-only guard; the test builds **real desktop
`KeyEvent`s** and calls the policy.

**Environment trap worth keeping: there is no public `KeyEvent` constructor on
JVM.** `androidx.compose.ui.input.key.KeyEvent` is a common
`@JvmInline value class KeyEvent(val nativeKeyEvent: NativeKeyEvent)`, and on
the skiko/desktop target `actual typealias NativeKeyEvent = Any` — so you cannot
wrap a `java.awt.event.KeyEvent` directly; `KeyEvent.desktop.kt` maps AWT →
Compose through an `internal data class InternalKeyEvent`
(`toComposeEvent()`). The supported way to build one in a test is the
`@InternalComposeUiApi` factory in `KeyEvent.skiko.kt`:

```kotlin
@OptIn(InternalComposeUiApi::class)
val e = KeyEvent(key = Key.Enter, type = KeyEventType.KeyDown)
```

Verified against the **pinned** Compose (`org.jetbrains.compose` 1.8.2) by
reading `compose-multiplatform-core` at tag `v1.8.2` through the GitHub API —
`compose/ui/ui/src/skikoMain/kotlin/androidx/compose/ui/input/key/KeyEvent.skiko.kt`
(factory, parameter names, `InternalKeyEvent`) and
`compose/ui/ui-util/src/commonMain/kotlin/androidx/compose/ui/InternalComposeUiApi.kt`
(it *is* a `@RequiresOptIn` marker, so the opt-in is required, not decorative).
`Key.kt` at the same tag confirms every constant the test uses
(`Unknown`, `DirectionDown`, `Zero`, `A`, `Z`, `Spacebar`, `Enter`, `Escape`,
`NumPadEnter`). Named arguments (`key =`, `type =`) also rule out the value
class's own constructor, whose single parameter is `nativeKeyEvent`.

**Audit — was this systemic?** Scanned all 65 `shared` test files plus
`app-android` and `app-desktop` for test-private functions whose names also
exist in production code (the fingerprint of this defect). Every other hit is
legitimate: fixture factories (`track()`, `downloaded()`, `data()`, `shelf()`),
thin wrappers that *do* call production (`touchTargetPx()` →
`DhunSpacing.touchTarget`, `actions()` → `TrackMenuPolicy.actionsFor`), or
`DhunThemeContrastTest`'s private WCAG luminance/contrast — which is an
**independent oracle for a spec**, the correct design for a numeric gate, not a
copy of product logic. `SearchInputPolicyTest` was the only real instance.

**Sandbox limits hit again (unchanged, re-confirmed not assumed).** No
`java`/`javac`/`gradle`/`pwsh` on PATH and no `/usr/lib/jvm`, so no Kotlin
compile; `scripts/restore-toolchain.sh` downloads from `api.adoptium.net`,
which is not reachable here, and the Kotlin compiler's own GitHub release
redirects to `release-assets.githubusercontent.com` — blocked, the same wall
that stops release-asset downloads (`SSL_ERROR_SYSCALL`). Maven Central is
unreachable too, so even with a JDK the Compose classpath could not resolve.
Consequence: `:shared:jvmTest` on GitHub Actions is the first and only compile
of this change. The local gates that do run — `python3 -m unittest discover -s
scripts` **31 OK**, `scripts/validate_fixtures.py` **PASS: 39 files** — do not
touch Kotlin and are not offered as evidence for it.

**Never do.** Do not "fix" a red `:shared:jvmTest` here by deleting the test or
reverting to a primitive-typed policy just to dodge the internal API: the point
is that the shipped rule is the tested rule. Do not quote the provenance
digests below as user-verified device builds — the `.sha256` sidecars were not
independently fetched in-sandbox.

**Evidence verified against GitHub this session (not inherited).**
`main@ca6d006` post-merge: CI **37731260148** 12/12 steps success; Build APK
**37731260114** success; test-release **37731260236** success — `apk`
(113160651719), `msi` (113160651868) with `MSI upgrade smoke PASS :: Hosted
Windows: 2.178.1 -> 2.182.1; per-user install and userdata/cache sentinels
preserved. Baseline SHA256=0c67d2bf…` (the PR #127 publish's MSI, so the
install-over upgraded the release's own build), future-upgrade guard PASS,
uninstall smoke PASS, **no `MSI install-over SKIPPED` warning** — and `publish`
(113162155556) republished `test`: `isDraft=false`, `isPrerelease=true`,
published 2026-10-08T05:19:05Z, target `ca6d006…`; provenance `dhun-test.apk
source=ca6d006… bytes=18383603
sha256=ff454398bbfb16139b64ef13c9339ef192461b41453eaa8d62a55e9490142b07
buildOnly=false`, `dhun-test.msi source=ca6d006… version=2.182.1
bytes=112967680
sha256=aa3ff19c2e3c1102cca17ecd7f0129c4e4a5b689af99f8a1761794d1fd454db7
buildOnly=false`. PR #128's own red history is recorded in the ROADMAP ledger
(`38536d5`, `bcd43f3` — Compose receiver-scope errors during the rail
restructure). PR #129 checks — **all four green on both heads of this session**: code head
`643298a` push CI **37732411763** (12/12 steps), PR CI **37732439056** (12/12),
Build APK **37732439037**, test-release **37732439049**; final docs head
`27fe90f` push CI **37732962973** (12/12), PR CI **37732965756** (12/12), Build
APK **37732965758**, test-release **37732965769**. Step 6 is `:shared:jvmTest`,
so the rewritten `SearchInputPolicyTest` — including the
`@InternalComposeUiApi` `KeyEvent` factory — compiled and executed on CI; step 8
compiled the rewired `SearchScreen` for Android. The PR-path MSI ran the full
install-over both times with no skip (`2.182.1 → 2.183.1`, then
`2.182.1 → 2.184.1`, baseline `aa3ff19c…` = the published release's MSI), and
the PR artifacts are `buildOnly=true`: APK 18,383,603 B sha256
`aa6d027ac1c6737563271ab37026ac36f79ac23ebb6ca66df4d2b1d91dc3e6a8`, MSI 2.184.1
112,967,680 B sha256
`513d81f682458390c68ad19e56b0c78a0751f5cd83b39b9c901ba3b79c080c27`. The
identical APK digest on both heads is itself a check: the docs commit changed no
app code. **Green CI is still not device acceptance** — the Enter key on a real
window, focus routing and IME precedence are the user's S3 retest.

## 2026-10-06 — the two recurring shared-test flakes, root-caused at last: a test waited for one state and read its sibling (session `arena/cf69112a-dhun`, base `main@a9204c59`, PR #125, commit `df0504f`)

**Why this lane, this session.** Boot found the S3 hardware round still
user-gated (no device in the sandbox) while two flakes kept turning unrelated
docs/service PRs red and burning whole runs. They had been carried as
"known flake class — load/timing" since 2026-09-18, with a 5 s → 15 s timeout
bump as the only mitigation. That bump made failures *slower*, not rarer,
which was the clue: the symptom was a **timeout that could never succeed**,
not a slow flow.

**The class.** Both flakes are the same defect: a test `eventually`-waits for
one piece of state, then immediately reads a *sibling* piece that the
production code publishes as a **separate step**. Neither is a product bug —
the end state is always reached; the tests just refused to wait for it.

**Flake 1 — `LibraryViewModelTest.historyPlaybackQueuesCorrectly`**
(runs **37320618809**, **37407043852**, and push CI **35309124090** on
2026-09-18):
- `LibraryViewModel.groupedHistory` is **derived asynchronously** — a
  `scope.launch { combine(historyEntries, _offsetMs).collect { _groupedHistory
  .value = it } }` (`LibraryViewModel.kt:281-285`). `eventually {
  vm.historyEntries.value.size == 3 }` therefore returned while the derived
  flow could still hold its **initial `emptyList()`**.
- The test then did `val day = vm.groupedHistory.value.firstOrNull()` and
  `if (day != null) { vm.playHistoryDay(...) ... }` — a **silent skip**. And
  because `playHistoryEntry` (below it) queues the whole visible history with
  exactly **one** `prepareQueue` call, the following
  `eventually { player.prepareCalls >= 2 }` could **never** be satisfied once
  the day-play was skipped.
- Net effect: a coin-flip scheduler race became a **guaranteed 15 s timeout**
  — which is exactly why it looked like a load flake and why raising the
  timeout only moved the failure later. Same-SHA green/red splits
  (`37407039477` push green vs `37407043852` PR red) are the expected shape.
- **Fix (`df0504f`):** wait for the derived state (labelled), then read
  `first()` unconditionally — the day-play path is now mandatory coverage, and
  the test additionally pins `day.entries.size == player.lastPrepared.size`
  (the single-entry path queues all history, so this distinguishes which path
  ran). The `if (day != null)` guard existed only to dodge the race.

**Flake 2 — `PlayerViewModelTest.endlessRadioDropsAPageFetchedForAQueueThatChangedMidFetch`**
(run **37320626452**):
- `ComparisonFailure: expected:<tok-2> but was:<tok-a>` at
  `PlayerViewModelTest.kt:517`. A refill publishes **two facts in sequence**:
  the swapped queue, then the consumption of the page's continuation token.
  The test polled the queue and read `radioSession.continuationToken` inside
  that gap, so it sometimes observed the *pre-advance* token.
- **Fix (`df0504f`):** `RadioFixture.awaitContinuationToken(token)` waits for
  the consumption itself — the same assertion, just not racing it. Applied
  also to the three siblings that read an advanced token after a queue wait
  (`endlessRadioReplacesTailWhenSongsRunLow`,
  `endlessRadioReseedsWhenTheTokenIsGone`,
  `endlessRadioLeavesQueueAloneWhenRefillFails`).

**Diagnostics — the durable half.** `LibraryViewModelTest` and
`PlayerViewModelTest` now have
`eventually(timeoutMs, label: () -> String, check)`, where the label is
evaluated **only on expiry** and the failure goes through `kotlin.test.fail`.
The check-run annotation (the only CI readout this sandbox can reach — job
logs/blobs are not downloadable, see the `build.gradle.kts` test listener)
now reads
`eventually(15000ms) timed out: playHistoryEntry to prepare a second queue
(calls=1, lastPrepared=0)` instead of a bare
`Timed out waiting for 15000 ms`. The stack frame still points at the helper,
which is precisely why the message has to carry the identity and the state.

**Verification (GitHub, not local).** Push CI **37408908111** on `df0504f`:
**12/12 steps success**, including `Unit tests — shared domain` (the edited
files compiled and executed) with the Android Robolectric and Desktop JVM
suites green beside it. PR CI **37408920918**, Build APK **37408921035**,
test-release **37408920959** — all green, same head. Local, JDK-free gates:
`python3 -m unittest discover -s scripts` **31 OK**,
`scripts/validate_fixtures.py` **PASS: 39 files**, plus a string/comment-aware
delimiter check on both edited files against untouched controls.

**Honesty boundary.** Green CI **cannot prove the absence of a race**. What is
proven: the known-bad interleaving is gone, a silently-skipped coverage path
is now mandatory, and a recurrence will name the exact wait and the state it
was stuck on. The other four `eventually` copies (`BrowseViewModelTest`,
`HomeViewModelTest`, `LibraryDownloadsViewModelTest`, `SearchViewModelTest`)
still time out with the bare message — deliberately untouched this session
(blast radius), recorded in `KNOWN_LIMITATIONS.md`.

## 2026-10-06 — the `msi` job wedged the rolling release: a Draft baseline + `needs: [apk, msi]` (session `arena/95fb0f92-dhun`, base `main@5c8bd67`, PR #122)

**Symptom.** Every test-release run after 2026-10-05T16:56:46Z is red: the `msi`
job fails at *Check install-over and userdata on disposable Windows* with the
annotation `MSI install-over :: Could not download the published MSI
baseline/checksum` (post-merge run **37403248318** on `5c8bd67`), and the
`publish` job — the only thing that replaces the rolling `test` release — never
runs. The user reports the rolling release has no installation files on the
Releases page.

**Two causes, one of them a design defect.**
1. *State:* the rolling `test` release is a **Draft** (`draft=true`, target
   `885a092`, all four assets present, `published_at` 2026-10-05T16:56:46Z).
   GitHub lists draft releases only to callers with push access, so the `msi`
   job's `contents: read` token cannot resolve `gh release download test`. The
   assets were uploaded by run **37343725414** (APK 18,367,219 B, sha256
   `c351341e…`; MSI 2.146.1, 112,934,912 B, sha256 `b569e769…`), whose
   install-over step **passed at 16:55:24Z** against the previous, published
   baseline (2.140.1 → 2.146.1, sentinels preserved). The same step has failed in
   every run since the 16:56:46Z (re)create: **10 failures, 1 concurrency cancel,
   0 successes**. The exact sequence that left `draft=true` is not reconstructible
   from the API (the record's `created_at` predates its `published_at`; the event
   feed holds one `ReleaseEvent published tag=test`), and it does not matter for
   the fix: the release must be readable again, job or not.
2. *Design (the real defect):* `publish` has `needs: [apk, msi]`, so a red `msi`
   job skipped the only job with `contents: write` — the only actor that could
   republish the release. The outage could not repair itself, and no branch run
   could fix it (`publish` is `main`-gated). This is the part worth remembering:
   the *symptom* was release state, the *defect* was the dependency shape.

**Fix.** `check_msi_upgrade.ps1` grades the baseline fetch: unreadable
(absent / draft / mid-replace) ⇒ `::warning title=MSI install-over SKIPPED::` +
notice + `installOver = "skipped: <reason>"` in `result.json` + `exit 0`, so the
MSI still uploads and `publish` runs; `publish` then re-asserts readability
(`gh release edit test --draft=false --prerelease --target "$GITHUB_SHA"`) and
fails the run unless `isDraft` is provably `false`. `msi` keeps `contents: read`
(deliberate; a contract test forbids widening it).

**Never do.** Do not describe a skipped run as an upgrade pass: on that path
nothing was installed — no in-place upgrade, no sentinel checks, no
future-upgrade guard, no uninstall. Do not "fix" the deadlock by granting the
`msi` job write access. Do not re-clone or `git clean` the sandbox worktree.

**Not verified at write time.** The PowerShell edit is parsed only by CI's
`check_powershell_syntax.ps1` (no pwsh in the sandbox; a local brace/paren depth
check matches the original's profile — a smoke check, not a parse). Release-asset
downloads still EOF in-sandbox (Azure blob), so digests come from run notices.

**Verified on CI afterwards.** Code head `6a6dd83`: all 9 steps green including
the PowerShell parse (push CI **37405077637**, PR CI **37405088698**, Build APK
**37405088832**). The skip path ran for real in `test-release` **37405088686**
(`msi` green with `MSI install-over SKIPPED`, candidate MSI 2.158.1 staged,
`buildOnly=true`) — the exact behaviour the fix intends, and the reason that run
must never be quoted as an upgrade pass.

**Post-merge outcome (merged as `5e664c1f`).** CI **37406381107**, Build APK
**37406381126**, test-release **37406381117** — all green. The `msi` job skipped
again (the baseline was still the draft at check time, announced on the job) and
**`publish` ran** for the first time since 2026-10-05T16:56Z, deleting and
recreating `test` as a **published pre-release**: `isDraft=false`,
`isPrerelease=true`, published 2026-10-06T02:58:13Z, target `5e664c1f…`, four
assets (APK 18,367,219 B `21a5fe86…`; MSI 2.160.1 112,947,200 B `74109a13…`).
That closes the outage. The **full** install-over path then ran for real on the
PR head `a7c4fb0` (test-release **37407043857**, `buildOnly=true`): **no skip**,
`MSI upgrade smoke PASS::Hosted Windows: 2.160.1 -> 2.161.1` with both sentinels
preserved, baseline SHA256 `74109a13…` — exactly the published release's MSI
digest — plus the future-upgrade guard and the uninstall smoke.

**One red to keep on record, and it is not this diff:** PR CI **37407043852** on
the same head failed `:shared:jvmTest` on
`LibraryViewModelTest.historyPlaybackQueuesCorrectly`
(`TimeoutCancellationException: Timed out waiting for 15000 ms`,
`LibraryViewModelTest.kt:82`) while **push CI `37407039477` was 9/9 green on the
identical SHA**. That is the known flaky-test class documented in
`KNOWN_LIMITATIONS.md` (fourth occurrence; `gh run rerun` is refused, so a
successor commit is the re-trigger). Merge basis: the identical-SHA green push
CI plus the documented flake, not a retry-until-green.

**Second `main` push — the release path re-verified end to end.** On `a9204c5`
(the docs merge), CI **37408148211** / Build APK **37408148184** / test-release
**37408148220** are green; the `msi` job ran the full install-over with
`buildOnly=false` (`2.160.1 -> 2.163.1`, sentinels preserved, baseline SHA256
`74109a13…` = the previous publish's MSI), `publish` replaced the release
(published 2026-10-06T03:20:40Z, target `a9204c5…`, four assets; MSI 2.163.1
`12745f81…`, APK still `21a5fe86…`). **Nothing is skipped on these runs**, so a
`MSI install-over SKIPPED` warning from now on means the release went dark
again — that is the signal to check `isDraft` first, not the app code.

## 2026-10-06 — sandbox trap: the checkout's `.git` is reverted between turns (`arena/cf4e91ba-dhun`)

**Symptom.** Twice in this session the workspace came back with `HEAD` at the
original shallow merge commit (`885a092`) and the branch refs as they were before
any of this session's commits — while the *worktree* files still held the full
edits. `git log` showed only `885a092`; `git cat-file -t <session commit>` said
"Not a valid object name".

**What it is.** The sandbox snapshots the worktree but not the local `.git`
(`.git/config` is excluded from snapshots by design, and this environment also
appears to restore the base clone). The remote branch was never affected —
every commit had been pushed.

**What to do.** Treat `origin/<branch>` as the source of truth. To re-attach:
`git fetch --depth=50 origin <branch>`, `git add -A`, confirm
`git diff --cached origin/<branch>` is **empty** (that is the proof the worktree
still matches the pushed head before the ref moves), then
`git checkout -B <branch> origin/<branch>`. Do **not** assume local commits
survive a turn boundary, and do not `git reset`/re-clone.

## 2026-10-06 — Full Player's two "old interface" surfaces: the playlist picker and the queue panel's missing swipe (session `arena/cf4e91ba-dhun`, base `main@885a092`, PR #121)

**Report** (user, on the digest-verified PR #120 candidate, Android 15
`SQ3A.240829.003`; the artwork parsing was confirmed working and is untouched):
1. Full Player → ⋮ → **Add to playlist** — "still shows the old Material 3
   interface", plus "an unusually long/oversized box".
2. Full Player → ≡♪ → **Queue/Related** — "still shows the old interface", and
   "swiping down to close/toggle the panel does not work as expected".

**Root causes — measured against the code, not guessed.**
- The picker was the last old-Material surface left in the app: a `GlassCard` at
  280–400dp with double padding, an M3 `titleLarge` header, a **fixed
  `dialogListHeight` (180dp) `LazyColumn`** — a mostly empty slab for one or two
  playlists — an M3 `OutlinedTextField` (56dp of Material chrome with a floating
  label), and an M3 Close/Cancel row.
- The panel had **no drag detector anywhere**: `QueueSheetHeader` had no
  `pointerInput`, so the grab pill was pure decoration; and while the panel is
  open the player's queue glyph is hidden with the action row it lives in
  (`actionRowVisible = false` → `alpha 0` + `disabled`), so the only exits were
  the ✕ and Back. Its surface was also the retired near-black `GlassBottomBar`
  recipe (`glassBarTop` → `glassStrong`, no artwork) — the one surface left
  outside the artwork-veiled family the dock, ⋮ menus, dialogs and page
  backdrops had moved to.

**Fixes.**
- `AddToPlaylistDialog` rebuilt on the shared menu pieces: `TrackMenuSurface` +
  `TrackMenuHeader` + `MenuActionRow` (name, "N tracks" supporting line, trailing
  "Open"), `AddToPlaylistPolicy.listMaxHeight` (content-sized, ≤4 rows, never
  above the old 180dp box) and `DhunTextField` instead of M3's outlined field.
  Callbacks, create-and-add flow, blank-name error and dismissal unchanged.
- `MenuActionRow` gained `supportingText` + `trailingLabel`/`onTrailingClick`; a
  plain menu row keeps the exact fixed 44dp height it had.
- New `design/components/DhunTextField.kt`; new `LyricsArtworkSheet` (opaque
  base + elevation + lyrics veil over the track's blurred artwork), which the
  panel now paints. `GlassBottomBar` had no call sites left and was deleted.
- Panel drag: `detectVerticalDragGestures` on the header strip →
  `panelDragOffsetPx` (follows the finger, clamped `[0, travel]`, a non-finite
  current offset collapses to rest) → `panelDragTranslations(motion, drag)` adds
  the same pixels to the sheet's **and** the player's offsets, so the measured
  seam stays closed; release commits past `panelDismissThresholdPx`
  (0.28 × travel, floored at one touch target, capped at travel) through
  `shouldDismissPanel`, otherwise a fast tween snaps the offset back to 0.
  `rememberUpdatedState` keeps the gesture coroutine's callbacks (and its frozen
  `travelPx`) current.
- Same defect class, fixed alongside: `CreatePlaylistDialog`, `RenameDialog`,
  `DeleteSelectedConfirmDialog`, the clear-downloads and clear-history dialogs
  drop `OutlinedTextField` for `DhunTextField`, and all six dialog `GlassCard`s
  pass `opaqueBase = true` — the rule GlassCard's own KDoc states for dialogs.

**First red, and what it was (real CI, not a guess).** PR CI **37388855101**
failed in `:shared:jvmTest` on three `PlayerPanelDragTest` assertions
("expected 30.0, got 5.0"; "expected 100.0, got 28.0"; "expected 0.0, got 50.0").
Two were wrong test expectations and one was a helper inconsistency: an upward
delta must follow the finger back toward rest (clamped at 0) rather than be
ignored; a dismissal threshold with no touch-target floor is governed by the
0.28 fraction; and a non-finite *current* offset must collapse to rest in
`panelDragOffsetPx` instead of being treated as 0 and then advanced by the
frame's delta (a NaN translation would blank the panel). Fixed in `85e73eb`.

**Push-CI flake on the last docs head (recorded, not hidden).** The final
docs-only commit `e837d30` touched `.ai/HANDOFF_NEXT_SESSION.md` and nothing else.
Its **push** CI **37391667937** went red in step 6 (`:shared:jvmTest`) on
`PlayerViewModelTest.kt:517`
`endlessRadioDropsAPageFetchedForAQueueThatChangedMidFetch` —
`expected:<tok-2> but was:<tok-a>` — while its **PR** CI **37391672515** passed
9/9 on the *same SHA*. This is the endless-radio probe race family (see the
2026-09-28 entry) and it is the second time this exact test has flaked on a
docs-only head (the first was run 37320626452). It is not attributable to this
diff — docs only, same-SHA PR CI green — and it is **not** claimed fixed here.
`gh run rerun` is refused for these runs in the sandbox ("cannot be rerun; its
workflow file may be broken"), so the successor docs commit is what re-triggers
CI. A future session that touches the radio probe logic should treat this test's
timing as unproven.

**Evidence / verification posture.** No JDK and no device in the sandbox, so CI
is the compiler and the user's device is the acceptance. Green after the fix:
PR CI **37389109897** and push CI **37389105839** (all 9 steps, including
`:shared:jvmTest` with the new tests), Build APK **37389110248**. Candidate APK
(build-only artifact, **not** published): test-release run **37389110912**,
`apk` job green — `dhun-test.apk` 18,367,219 B, SHA-256
`21a5fe862b0c948fbc038417e156310e9eaab74bf9ea2f59214b9807f8c9cc2c`, source =
PR merge ref `a9e8c9d7` of head `85e73eb`. The same run's `msi` job **failed at
"Check install-over and userdata on disposable Windows"**: `check_msi_upgrade.ps1`
could not download the rolling `test` baseline (`gh release download test …`
→ non-zero), so no `msi` artifact was uploaded even though the installer built
(ProductVersion 2.148.1, SHA-256
`4687747496a20eeb2efbdbdb08ef436bec9f694546588049e58921fb449efe32`).

**Diagnosed, not hand-waved, and it reproduced.** The docs head's test-release
run (`37390031054`) failed in exactly the same place, so this is deterministic,
not flaky. Root cause: the rolling `test` release is currently a **Draft**
(`draft=true`; `created 2026-10-05T16:49:03Z`, `published 2026-10-05T16:56:46Z`),
and the `msi` job runs with `contents: read` — a draft release is not readable
by a token without push access, so `gh release download test` fails before the
install-over check gets a baseline. This is the documented draft-release
behaviour, not a hunch: the REST releases docs state that *"Only users with push
access will receive listings for draft releases"*
(<https://docs.github.com/en/rest/releases/releases>), and the release-asset
endpoints likewise require push access for a draft
(<https://docs.github.com/en/rest/releases/assets>) — the `msi` job runs under
the workflow's `contents: read`, so `gh release download test` cannot resolve a
draft and exits non-zero. The public record matches that reading exactly: the
same install-over step **passed** at 16:55:24Z in push run `37343725414`, when
the tag still resolved to the previously *published* baseline (MSI 2.137.1), and
it has failed in every run since the 16:56:46Z (re)create. An analogous real
case — a `contents: read` verifier seeing "release not found" for a draft and
being fixed by granting write — is `lustoykov/hallvi#199`.

What is **verifiable**: the current state is `draft=true`; and no workflow in
this repo drafts `test` (`test-release.yml` at `885a092`, the commit that ran
it, is byte-identical to this branch's, creates the release with `--prerelease`,
and only the `v0.1.0` `release_draft` job passes `--draft`; the event feed holds
exactly one `published` event). What is **not** fully reconstructible is the
history: the record's `created_at` (16:49:03Z) predates the 16:56:46Z publish
event while that publish's assets were uploaded at 16:56:42Z, so the exact
sequence that left the flag set is unclear from the API alone. It does not
change the actionable fact: a read-scoped token cannot read a draft, a branch
run cannot repair it (`publish` is gated on `refs/heads/main`), and the
install-over gate cannot run until the rolling release is published again (or
the job is deliberately granted `contents: write`). This session did not touch
release state. Until then **no Windows candidate exists for this slice**, and
the Windows half of the retest must wait. No visual or gesture acceptance is claimed here —
see `docs/verification/15-test-build-gate.md` §3 "Fix 4".

## 2026-10-05 — S3 hardware report; build digest and UI symptoms unresolved

The user returned a partial Android + Windows report for the round-1 checklist.
Android marked steps 1, 6, 7 and 9 failed; Windows core steps 10–13 passed by
user report. Steps 5a–5c showed artwork but it was described as small; the
compact track menu passed but the user asked for it to open near its ⋮ anchor.
`Go to album` was not found and only five actions were exercised; step 16 was
not understood and step 18 was not reported. No device/OS/build metadata or computed hashes accompanied the report. Four
inline screenshots were subsequently reviewed, though their step mapping is
unknown. Both album views show generic `Album` metadata and placeholder art; one
shows `Nazm Nazm` playing behind the page. The latest user clarification says
single-release cover and track art both fail, while other album/playlist covers
fail and their per-track row art mostly loads. Exact step-6 size/shape result
remains unclear. Therefore there is no confirmed build identity or diagnosed
parser root cause yet.
See `docs/verification/14-release.md` for the verbatim-scope result ledger.

**Checksum provenance correction.** The post-#119 gate had repeated the
`16ad2e5` hashes as if verified for `73b88b6`; this was incorrect. The release
API confirms the `test` target/tag, asset names and sizes, but sandbox sidecar
fetches were empty/EOF and publish-job annotations contain no artifact hashes.
The gate, runbook and handoff now mark the `73b88b6` checksums unverified. Do
not reuse old digests or infer file identity from equal sizes.

**Album screenshot/code correlation (preliminary, not a confirmed root cause).**
`parseAlbumPage` uses the generic title `Album` when `pageHeader(root)` or its
`title.runs` is not found; album art comes from `thumbnailsLastUrl(header)`;
track-row art is the row thumbnail or that header cover; `AlbumScreen` passes
`detail.thumbnailUrl` to `PageArtworkBackdrop`. If the page header/art URL is
missing—or the image request fails—the UI shows the observed placeholders, and
with another track playing the shell's now-playing blur can remain visible
under the album page. The screenshot's `Album`/`Unknown artist` labels fit that
fallback path, but could also be genuine incomplete upstream data; no raw browse
response, album id or source route was supplied. Do not patch the blur layer
until a real album response proves that valid page art reaches the UI.

**Follow-up symptom report (2026-10-05).** The user confirms ordinary
Home/Search song thumbnails load. On single releases, both the page cover and
track art are missing, including when those tracks are opened in the full-screen
player. On other albums and playlists, only the page-level cover fails while
per-track thumbnails mostly load. The pages were opened from Home and Search.
This strongly favors missing/unrecognized browse-page header data (and the
single page's row fallback) over a global Coil failure, but is not a confirmed
root cause without one raw affected browse response. `parseAlbumPage` derives
the header cover with `thumbnailsLastUrl(header)` and passes it as a row
fallback; `parsePlaylistPage` derives its cover separately while row thumbnails
come from each track item. Read-only audit found common Coil
`ArtworkImage`/`AsyncImage`, `coil-network-ktor3`, and Android `INTERNET`
permission. Do not change the global image loader without evidence of a shared
transport failure.

**Candidate parser fix (2026-10-05; targeted device retest passed).** `pageHeader`
previously checked only `root.header`, while two-column browse responses can
place `musicResponsiveHeaderRenderer` in a tab's `sectionListRenderer.contents`.
It now falls back to those normalized tab sections and unwraps nested responsive
detail headers. Tests/fixtures cover a single release with no row art (cover
inherited into the track), a playlist cover with independent row art, and nested
detail-header fields. These are synthetic response shapes, not the user's raw
payload, so the exact upstream cause remains unconfirmed even though the
candidate fixes the reported device symptoms.

Parser commit `b7d0f01`, PR #120. Initial PR CI **37319510581** passed all steps,
including the new parser tests. Two intervening docs-only runs on head `5006595`
had unrelated failures: push CI **37320618809** timed out in
`LibraryViewModelTest.historyPlaybackQueuesCorrectly`; PR CI **37320626452**
reported `PlayerViewModelTest.endlessRadioDropsAPageFetchedForAQueueThatChangedMidFetch`
(expected `tok-2`, got `tok-a`). On head `1332005`, push CI **37321440694** and
PR CI **37321446706** passed. On current pre-merge head `edaf4b2`, push CI
**37322646142**, PR CI **37322658661**, Build APK **37322658940**, and test-release
**37322658878** passed. The APK/MSI and MSI install-over jobs passed; AAB/publish
were skipped. The `apk` artifact contains `dhun-test.apk`, `.sha256`, and
`.build-info.json`. It is 18,367,219 bytes, SHA-256
`c351341edbeaa7935c7a52ec096141d6d28dc18133000ff2bc00cf63473c5458`; artifact ZIP
is 17,552,363 bytes (expected compression). User reports downloading the
candidate, verifying its checksum, and testing on Android 15 build
`SQ3A.240829.003` (model not reported): single cover/rows/full player, other
album cover, playlist cover/rows, and Home/Search thumbnails all work; the
now-playing backdrop appears during playback only, as expected. Playlist-row
step 6 (64dp, borderless) and `Go to album` were reported as “seems working,”
without exact row measurement or album-track ID. Record targeted artwork
acceptance **PASS by user report**, not full S3/S6 sign-off. No JDK locally;
fixture validation passed and CI remains the Kotlin verifier.

`TrackRow` currently uses `DhunSpacing.artworkThumb = 64.dp`; `ArtworkImage`
draws no stroke. The latest report says per-track thumbnails mostly load on
other albums/playlists; the step-6 expected-versus-actual row size/shape mismatch
is still not described. Do not change row styling from a page-cover failure.

**Step-16 procedure defect.** Code review of `TrackMenuPolicy` and its test
showed that `Download` is shown when a host supplies a `DownloadManager`;
there is no per-track downloadable predicate. Android and Desktop wire a
manager. Thus a tester cannot choose a special “non-downloadable track” on
these builds to test omission. The runbook wording has been corrected; the
no-manager policy remains unit-tested. `Go to album` remains metadata-dependent
and should be checked on a known album-linked track.

Targeted artwork verification is now reported PASS on the digest-verified
candidate. The remaining pre-merge work is to commit/push this verification
record and let checks pass on that docs head, then merge PR #120 as the final
action and watch post-merge CI/test-release in the same turn. Do not claim all
S3/S6 accepted: full step 18, exact step-6 measurement, device model, and other
platform/soak evidence remain incomplete. If symptoms recur after merge, obtain
the exact affected browse ID and sanitized response before another parser edit.
Keep menu anchoring and larger/dynamic notification artwork as separate UX
choices.


## 2026-09-28 — Post-#118 reconciliation (`arena/01a0e81a-dhun`)

Boot inherited a clean checkout at `16ad2e5`, but the roadmap still said
#118 was open. GitHub confirmed it merged at 12:59:01Z; its post-merge runs
were in progress at boot, then all succeeded (CI 36425394319, APK 36425394304,
test-release 36425394409). Release/tag both target that SHA; published
13:04:52Z. Staging annotations supply digests (APK job 108937985529, MSI job
108937985866); publish-job annotations contain only the Ubuntu-26 notice.
Do not call staging annotations publisher annotations or infer bytes from size.
Hosted Windows 2.134.1 → 2.137.1 preserved sentinels; no audio/UI claim.

The old S3 runbook prescribed uninstalling existing apps and named a stale
build; corrected to upgrade first, disposable clean-install/uninstall tests,
and digest-based identity. Also separated recents dismissal, force-stop and
OS process death. Android EQ's carried-forward absence claim contradicts the
actual MainActivity → Koin session → SettingsScreen wiring; documented the
contradiction without changing code or claiming audible DSP proof.

Local checks: 29 Python helper tests passed; 35 JSON fixtures syntax-valid.
No JDK locally; Kotlin/PowerShell/build/package checks use GitHub CI.
Installed `gh run list` has no `--commit`; use `--branch main` and inspect
`headSha`. Deepening the shallow clone succeeded without changing the branch.
No extraction semantics, dependencies, workflows or application code changed.

## 2026-09-28 — Two standing second-look findings executed: the offline-broadcast verdict and the never-refunded retry budget (`arena/01a0e7ee-dhun`)

**Where they came from.** Boot found the agent lane officially empty (S1/S2/S4/S5 code merged; S3/S6 are device gates; the only watch is the Ubuntu-26 runner migration on 2026-10-19). The highest-value code left that CI can prove was in KNOWN_LIMITATIONS' 2026-09-16 "second-look code findings": `[NIT/LOW — S5] checkPlayability passes LIVE_STREAM_OFFLINE as OK` and `[NIT/LOW — S5] PlaybackGraph.retries never reset on success`. Neither was executed in the S5 merge (PR #74) twelve days earlier. A third finding in that list (`cancelCacheFill()` not cancelling its job) turned out to have been fixed on 2026-09-16 in `92383ab` without the entry being annotated — verified by reading the code at `main@5bbb16d` and by the GitHub commits API, and now marked resolved.

**Defect 1 — an offline broadcast was reported as a parse failure.** `checkPlayability` returned the response for `LIVE_STREAM_OFFLINE`, so `parseStreamInfo` met an empty `streamingData` and threw `Parse("no formats in player response (streamingData empty)")`. Every identity in the wave answers the same way, and `aggregateResolveFailures` prefers a non-Parse error and finds none — so the headline became *"The music service sent something DHUN couldn't read. Try again in a moment."* The service's own reason ("This live stream is offline") survived only inside the aggregate's per-client summary, and `Parse` reads as *our* bug **and** as something worth retrying. Fix (`57febe6`): no format entries at all → `Unavailable(detail)` with status + reason + subreason (the `UNPLAYABLE`/`ERROR` path); formats present → passed through untouched, because a finished broadcast still serving its VOD is playable and the status string alone is not the blocker. `hasAnyFormat` is a **presence** check only, so audio-capability and direct-URL judgement stay in the parser and a ciphered response cannot be mislabelled "unavailable".

**Proof.** Tests first (`017f4fc`). CI **36422015540** red in `:shared:jvmTest` on exactly the two new assertions: `AssertionError: Expected an exception of class dev.dhun.core.DhunException to be thrown, but was completed successfully` (`InnerTubeRequestTest.kt:179`) and `IllegalStateException: LIVE_STREAM_OFFLINE was treated as playable by identity visionos` (`OwnClientStreamResolverTest.kt:223`). Everything else green. The third new test pins the pass-through half and was green *before* the fix too — it is the guard against over-reach. Fix-only head `57febe6`: push CI **36422438830** green. Full head `614134b`: push CI **36422490521**, PR CI **36422590223** (12/12 steps), Build APK **36422590089**, test-release **36422590222** (`apk` + `msi`, hosted install-over 2.134.1 → 2.135.1, sentinels preserved, `buildOnly=true` on the PR merge-preview source). Only annotation anywhere: the standing Ubuntu-26 notice.

**Not observed live.** The runner is `ENVIRONMENT_BLOCKED` and the sandbox has no path to a real offline broadcast, so the status/reason/subreason placement is modelled on the `UNPLAYABLE`/`ERROR` responses the parser already handles — the same basis as every other parser test in this repo. If a real response puts its reason elsewhere, the *category* stays right and only the reason text degrades to `status=LIVE_STREAM_OFFLINE`.

**Defect 2 — the recovery budget was never refunded.** `PlaybackGraph`'s listener kept a `HashMap<String, Int>` for the lifetime of the playback service, so `MAX_RETRIES = 3` meant *3 per track per process*, not 3 per incident. A track that recovered at minute 2 arrived at minute 90 with one retry left and then none: an expired URL or a gated endpoint surfaced as the Error state instead of "Reconnecting…" — the engine stopped fixing by itself exactly the fault it had been fixing all session. This is the failure mode the S6 30-minute soaks are most likely to hit, on a carrier that gates periodically.

**Fix (`614134b`).** The counter moved into `StreamRetryBudget` — pure, `internal`, same pinned numbers (3 recoveries; 0/1500/3000 ms backoff), same exhaustion behaviour — and the listener refunds a track's full budget after `RESET_AFTER_PLAYING_MS` = 10 s of continuous audible playback, on the existing `mainHandler`. `StreamRetryBudgetTest` pins the cap, the backoff schedule, the refund, per-track isolation of the refund, and the constants themselves.

**The trap in the obvious fix.** Refunding on the first `isPlaying = true` is *worse than the bug*: a flapping track (two seconds of audio, error, two more seconds) would earn endless fresh re-resolves and never reach the user — an infinite retry loop with a network round-trip per cycle. Hence the delay, plus two details that make it safe: the track id is captured when the refund is **scheduled** (never read from the player when it fires, so a skip inside the window cannot refund the wrong track), and any error or pause inside the window **cancels** it (`cancelBudgetReset()` is the first statement in `onPlayerError` and in the `!isPlaying` branch), leaving the failures counted. `docs/verification/03-android-skeleton.md` also carried a stale "max 2 retries per track" against `MAX_RETRIES = 3`; corrected in the same pass.

**Environment traps this session.** (1) Log blobs still EOF in-sandbox (`gh run view --log` → `results-receiver.actions.githubusercontent.com … EOF`) **and so do release asset downloads** (`release-assets.githubusercontent.com … EOF`), so the `.sha256` sidecars could not be read — the **annotations API** is the readout for both CI verdicts and publish digests, and the release's `target_commitish` is the readout for what `test` points at. (2) `gh run list --jq 'select(…)'` silently returned nothing; parsing the JSON with python was the reliable path. (3) **The local clone is shallow** (`.git/shallow` at `5bbb16d`, 4 commits, `5bbb16d` has no parents locally) — `git log`/`git log -S` cannot answer history questions here; the GitHub commits API can. (4) No JDK — CI is the compiler, as always.

## 2026-09-28 — Endless-radio stale-probe race (the `67068a7` push-CI flake); drill "wedged" was cron latency (`arena/01a0e6c8-dhun`)

**Symptom.** PR #115's head `67068a7` went red on push CI **35979756610**. `:shared:jvmTest` failed with `AssertionError: no refill above the threshold` at `PlayerViewModelTest.kt:408` (`endlessRadioReplacesTailWhenSongsRunLow`). PR CI **35979761707** passed on the **same SHA**, and #115 merged over the red push check. The failure changes with timing, not with code, so it is a race.

**Root cause (in production code, not the test).** The refill monitor is `combine(queue, index, state).distinctUntilChanged().collect { maybeRefillRadio(it) }` running on a multi-threaded scope. `maybeRefillRadio` checks the probe's *snapshot* against the *live* `radioSession.isActive`. `startRadio` does `replaceQueueKeepingCurrent(rest)` and then `radioSession.start(...)`. The September 21 fix already ordered these so a live check can never see an active station over the old queue. But a probe that was **computed** before the swap and **evaluated** after the start is still possible when the collector lags behind the player. That probe is the one-song `[a]`, so remaining = 0, which is at or under the threshold, so the refill fires. `refillRadio`'s post-fetch guard compared only the head id, and `[a]` and `[a, r1..r5]` share head `a`. So in production the fetched page 2 would be applied over page 1. In the test, the fake's empty continuation page made it visible as an unexpected `continuationCalls = [tok-a]`.

**Fix (`cd9d4f3`).** (1) In the gate, after `isActive` (order matters, see the code comment), a probe whose queue or index differs from the live `player.queue` / `currentQueueIndex` is stale and gets dropped. Every change emits, so the up-to-date probe is always still on its way to the collector. (2) `refillRadio` skips the fetch if the queue changed since the gate. After the fetch it requires the **whole** queue to be unchanged, and a mismatched page is dropped **unconsumed**: the token stays, and the existing `finally` re-gate refetches it for the live queue. (3) Test fake: `advanceTo` now publishes track then index, which is `AndroidDhunPlayer.refresh`'s order. Index-first opened a separate window: the refill launched, bailed on the track check, and lost its trigger to `lastRefillProbe`.

**Proof.** Tests first (`c58572a`, with only the visibility of `RefillProbe` / `maybeRefillRadio` changed to `internal`). CI **36389023330** was red on exactly the two new tests: `a stale probe must not refill, got [tok-a]` and `expected:<[tok-a, tok-a]> but was:<[tok-a]>`. Every other test was green. On the fix head `fbf69bb`, push CI **36389230165** and PR CI **36389248363** were green on all 12 steps.

**Drill "wedged" (09-22 entry below) was cron latency.** Run 35709793101 fired at 2026-09-22T09:20:13Z, about 5h after `17 4 * * *` and after the previous session checked. It has fired daily since, with 09:20–10:02 UTC starts, all `ENVIRONMENT_BLOCKED`. Environment trap: GitHub's schedule lag on this repo is hours, not minutes. Wait ≥12h after a cron before diagnosing a non-fire.

**Node 20.** `download-artifact@v6`'s `action.yml` says `using: 'node20'` and v7's says `'node24'` (read via the contents API). The warning was still firing on publish job 108818910798 (`main@dcdd41b`). Bumped to v7 (`fbf69bb`). Proof has to come from the post-merge publish run, because the job is `main`-gated.

## 2026-09-22 — Album playback had no artwork; album/artist pages had no backdrop; every ⋮ was the old sheet (`arena/01a0c772-dhun`)

**Ask.** Three visual defects, fixed *separately* — explicitly not one polish
pass — with four standing constraints: do not regress the Android mini player
or the accepted Home / Search / Library cards, do not lift the dark surface
hexes, do not retune the accepted brightness/scrim, and do not discard the
uncommitted edits in `LyricsMaterial.kt` / `FullPlayer.kt`.

**Environment note first.** This checkout was **clean** at `03a27b1` (PR #113's
merge). There were no uncommitted edits in `LyricsMaterial.kt` or
`FullPlayer.kt` to preserve — that work is already on `main` — and neither file
is touched by this session. No JDK and no Maven egress, so CI stayed the
compiler; `scripts/validate_fixtures.py` (35 files) and the 29
packaging/CI-contract tests were the only local gates.

**Defect 1 — album playback artwork.** `parseBrowseSongRow` called
`thumbnailOf(item)` and nothing else. YTM ships many album pages with **no**
per-row `musicThumbnailRenderer`; the cover exists only on the header's
`croppedSquareThumbnailRenderer`, which `parseAlbumPage` already read *for the
page*. So the tracks `AlbumViewModel` queues carried `thumbnailUrl = null` and
the full player, mini player and shell blur — all of which read that field —
had nothing, while Home / Search rows (which do carry art) looked fine. Fix:
the row parser takes a `fallbackThumbnail`, the album page reads the header
cover once (`coverUrl`) and passes it to **both** row-collection paths (the
Songs shelf *and* the tree-wide `collectObjects` fallback). Row art still wins;
a page with neither yields `null` — no invented URL to fetch, fail and paint a
placeholder over the player. `AlbumTrackRow` now renders that art at the
playlist-row size (48dp `touchTarget`, `DhunShapes.medium`) beside the number.

**Defect 2 — flat album/artist pages.** Both screens painted an opaque
`DhunColors.background` over the whole page. That hid the shell's
`NowPlayingBackdrop` completely and left the album header an
`ArtworkColorExtractor` *seed* tint rather than blurred cover art — i.e. the
only two screens in the app that could not glow. Fix: `PageArtworkBackdrop`, a
**wrapper** on `NowPlayingBackdrop` so the recipe and every pinned number
(`NowPlayingBackdropPolicy`: list tier, 64dp blur, dim 0.40, scrim
0.50/0.32/0.44/0.62) are the shell's, pointed at the page's own artwork. Both
pages are transparent now and paint it, so they glow **while nothing is
playing** — exactly when the shell backdrop has nothing. The album header wash
fades to transparent (its opaque second stop was what hid the backdrop); the
artist parallax hero's fade ends on the existing lyrics-card veil (0.62) rather
than an opaque slab, so the sharp→blurred seam stays soft and the name stays
readable. Nothing was retuned to get there, and no fallback changed: no
artwork, or no real blur (`supportsRealtimeBlur`), draws nothing.

**Defect 3 — the ⋮ was still the old UI, and long.** One dialog
(`TrackOverflowDialog`, mounted once from `DhunAppShell`) serves the full
player, playlist, album, artist, Home, Search and Library. It was a centered
Material sheet on the old opaque `GlassCard`: 280–380dp, double padding, a 52dp
header, a divider, ~48dp rows and a separate Close button — ~470dp of slab on
both platforms. Fix: frosted artwork material (the track's own cover blurred
once under the lyrics-card veil, `LyricsMaterial`, on an opaque
`DhunColors.surface` base — the same rule `GlassCard(opaqueBase = true)` exists
for, because a menu floats over a dimmed scrim), new `DhunSpacing.menu*` tokens
(Material's 280dp ceiling, 220dp floor, 44dp rows, 40dp header thumbnail), no
divider, no Close button (tap outside / Back dismisses, unchanged). Tallest
possible menu ≈ 340dp. Actions, order, visibility rules and the
dismiss-then-navigate ordering are unchanged and are now pure data —
`TrackMenuAction` / `TrackMenuPolicy` — pinned by `TrackMenuPolicyTest`. Only
the labels lost their parentheticals ("Go to artist (Queen)" → "Go to artist",
"Download for offline" → "Download"): the header already names the track and
artist, and the long label was what stretched the sheet. The queue row's ⋮
stays an anchored Material3 `DropdownMenu` with its own actions, but its
container is turned off (transparent, zero tonal/shadow elevation) and it draws
into the same `TrackMenuSurface` / `MenuActionRow` — one menu system, not two.

**Traps worth remembering.**
- Material3's `DropdownMenu` sizes its popup column with
  `width(IntrinsicSize.Max)` (verified against the CMP **1.8.2** source, since
  the sandbox has no compiler). A child that fills max width would therefore
  stretch the menu to the pane; giving the frosted surface a *fixed* 220dp
  width short-circuits the intrinsic query and keeps it menu-sized.
- `DropdownMenu(shape=, containerColor=, tonalElevation=, shadowElevation=)`
  exists in the pinned material3 — but `border` is newer and was not used.
- `internal` commonMain declarations are visible to `:shared`'s `jvmTest`
  across packages (precedent: `PlayerSheetLayoutTest` uses `dev.dhun.design`'s
  internal layout functions), which is what lets a pure policy test pin a menu
  that lives in `ui/components`.
- Fixtures live in **two** roots (`tests/fixtures` and
  `shared/src/jvmTest/resources/fixtures`); `validate_fixtures.py` checks both,
  so a new regression fixture must be written twice and byte-identical.

**CI.** Head `80fe28b` watched green: push **35692075343**, PR **35692215784**
(all 12 steps, including `:shared:jvmTest`, the Robolectric suite,
`assembleDebug`, desktop compile + `jvmTest`), Build APK **35692215748**,
test-release **35692215751** (apk + msi; publish skipped — PR). Only
annotation: the standing `ubuntu-latest` → Ubuntu 26 notice. Log blobs EOF
(257 bytes) as usual; annotations + step conclusions are the readout.

**Incidental observation, not caused by this work and not fixed here.** The daily
`extraction-health` drill (`schedule: cron '17 4 * * *'` UTC) did **not** fire on
2026-09-22: the newest run of any event is still **35561269411**
(2026-09-21T04:30:45Z, `414cd79`, failing `ENVIRONMENT_BLOCKED` as the drill
always does), nothing queued at 04:17 UTC, and the workflow timing API reports no
`last_run_started_at`. PR #111's workflow edit reached `main` at `7fcadbe`
~02:58 UTC — between the two fires — so the wedged schedule registration the
previous handoff predicted is what the run list shows. Left alone deliberately:
no workflow file is touched by this PR, agents get 403 on `workflow_dispatch`,
and a re-registration cannot be verified until the next 04:17 UTC. It is the
next session's first task (attempt-5 fresh-file re-registration); see
`.ai/KNOWN_LIMITATIONS.md`.

## 2026-09-22 — Glass / scrim follow-up: surfaces were not the darkness (`arena/01a0c716-dhun`)

**Ask.** After the lyrics-veil / acrylic mini-player pass, the user asked to
decrease Home/Search/Library darkness toward the full player's background,
ease the full-player dim so blurred artwork shows through, keep WCAG-AA and
the `DhunAppearanceTest` pins, leave the light theme alone, and keep list
thumbs a little larger (~64dp) and borderless.

**What was already true, and easy to "fix" twice.** `artworkThumb` was
already 64dp and `ArtworkImage` already draws no stroke (PR #107). The dark
ladder was already the one-rung lift (`background` `#161616`). Another rung
was measured with the same WCAG math as `DhunThemeContrastTest`:
artwork-derived controls are **3.12:1** on `#1E1E1E` and **2.97:1** on
`#222222`. The second fails the 3:1 pin. The surfaces were not lifted.

**What was actually still dark.** The artwork overlays, not the hexes.
Player black dim was still 0.52/0.16 (the 2026-09-21 changelog had claimed
0.42/0.10; the code had not moved). Shell scrim stops were still
0.62/0.42/0.58/0.78. Those landed in `d2a9045`: dim **0.40/0.08**, ambient
bottom stop on the **≥0.85** floor (not under it — `PlayerSheetLayoutTest`
pins that), shell dim **0.40**, scrim **0.50/0.32/0.44/0.62**.

**CI.** No JDK. Code head `d2a9045` watched green: push **35685053236**, PR
**35685055740**, Build APK **35685055672**, test-release **35685055772**.
Publish skipped on the PR, so the rolling download at `7fcadbe` does not
contain this. Visual acceptance is the user's after the post-merge republish.

## 2026-09-21 — PR #107 merged; release republished and verified (connection survived)

Merged `44e1ffd` (2026-09-21T09:54:37Z) under the user's "it's good go
ahead". Pre-merge turbulence, recorded honestly: the docs head `b8b669d`
failed build-and-test in 30s with `403 Forbidden` from Maven Central for
`app.cash.sqldelight:sqlite-driver:2.1.0` — a dependency-resolution flake on
a docs-only diff (the identical tree had passed 30 minutes earlier). The
rerun API refused ("workflow file may be broken"); an empty retrigger commit
(`1def7ee`) produced all-green checks (build / build-and-test / apk / msi),
and the merge followed.

Post-merge main runs SUCCESS: CI 35585878593, Build APK 35585878634,
test-release 35585878637. Rolling `test` republished 2026-09-21T09:59:24Z
with tag AND target = `44e1ffd`; APK 18,334,451 B (same bytes-size as the
previous build — size alone no longer distinguishes them, use the target
SHA), MSI **112,885,760 B** (previous 112,889,856 — the new size
distinguishes the UI-polish build; old guidance that referenced
112,889,856 as "the new MSI" is superseded).

Environment trap encountered mid-session: a sandbox recreation restored
workspace files but NOT the local git commit chain, producing an apparent
divergence; reconciled by fetching the pushed head, hard-resetting to it,
and restoring only the new docs delta from a backup branch. No force-push.
Lesson: after any sandbox recreation, diff local HEAD against the pushed
branch before committing.

User verdicts in force: Android PASS + Windows PASS (user-tested, "its
great") on the `810bef1` build; thumbnail remark retracted as a load
hiccup. The UI-polish look itself awaits the user's re-download and
eyeball on both platforms. Endless radio: ask first.


## 2026-09-21 — Windows PASS reported; thumbnail remark retracted; PR #107 merge authorized

Sequence, verbatim where it matters: the user first messaged "it's good go
ahead also i tested the updated windows app its great but the blurry
thumbnail is gone i think it ll be improvised in this pr", then interrupted
and clarified: "sorry thumblail wasent loded well".

**Verdicts recorded:** (1) Windows is now user-tested on the updated app —
the `810bef1` rolling release (PR #106 scope): "its great". This closes the
stale-install episode; Gate-4 items were not itemized and no failure was
reported with any of them, so the Windows verdict is recorded as a general
PASS, not per-item confirmations (the `O3-6zB3kg8M` offline replay and
explicit-close itemization remain unconfirmed-but-unfailed). (2) The
"blurry thumbnail is gone" observation was retracted by the user as a
transient artwork-load hiccup — no code action. Cross-check done anyway:
`supportsRealtimeBlur` on desktop is unconditionally `true`
(`BlurSupport.jvm.kt`), so no platform gate could hide the dock/backdrop
blur on Windows; consistent with the retraction. (3) "it's good go ahead" is
the explicit user authorization to merge PR #107.

**Honesty note kept in the record:** the Windows build the user tested does
NOT contain #107 — the UI polish ships in the rolling `test` release
published right after the merge, and the user must re-download to see it.
The authorization covers merging #107 on the strength of green CI + the
user's go-ahead, not an itemized on-device review of the polish.

## 2026-09-21 — UI polish implemented: lighter dark ladder, 64dp thumbs, continuous glass dock (`arena/01a0c2c7-dhun`)

User picked UI polish over endless radio this session. All three sub-tasks
implemented on the session branch; **no visual verdict exists yet — CI first,
then the user eyeballs both platforms. Never merge without it.**

**(a) Lighter backgrounds.** Dark surface ladder lifted one rung in
`DhunTokens` (each neutral +0x0C): background 0A→16, surface 12→1E,
surfaceVariant 1A→26, surfaceCard 1E→2A, surfaceElevated 24→30,
surfaceHighest 2A→36; tonal ladder follows (lowest=bg … highest 30→3C);
placeholders/shimmer retuned to keep their relative steps. FullPlayer: dim
0.52/0.16 → 0.42/0.10; `playerAmbientScrimStops` every stop lowered (bottom
0.92→0.86 — the ≥0.85 floor pinned by `PlayerSheetLayoutTest` is respected).
Shell backdrop (`NowPlayingBackdropPolicy`): DIM_ALPHA 0.55→0.45 (inside the
0.4–0.75 pin), scrimStops 0.62/0.42/0.58/0.78 → 0.50/0.32/0.44/0.62. Light
theme untouched.

**Contrast was proven before pushing** by replicating `DhunThemeContrastTest`'s
exact WCAG math in Python over the new ladder (all 5 text steps × 11 surfaces,
4 semantic colors, 6 accents × 2 surfaces, 6 accents × 8 artwork primaries ×
2 surfaces): every gate passes. One real regression was found and fixed that
way: on the lighter surface the control-accent floor 0.42 yields only 2.85:1
for the darkest artwork — `DARK_LEGIBILITY_FLOOR` retuned 0.42→0.45 (measured
worst case 3.12:1, same-margin as before). `DhunAppearanceTest` hex pins
updated to the new baseline with a comment naming both intentional retunes.

**(b) Thumbnails.** `DhunSpacing.artworkThumb` 56→64dp. Every consumer
audited: `TrackRow` and both Library cards wrap content (no fixed heights);
queue/playlist reorder rows use 44/48dp tokens, not the thumb;
`LoadingShimmer` is size-only; `DhunShellLayout.detailPaneMinWidth` grows
8dp (a min, harmless). No clip risk found.

**(c) Continuous glass dock.** New `GlassDock` in
`design/components/GlassCard.kt`: one clipped bottomSheet surface = blurred
current-track artwork (LYRICS-card treatment: `ArtworkImage` blur
glassBlur×2, `key(BlurredArtworkCache.keyFor)` + `markPrepared`, list-tier
URL via `NowPlayingBackdropPolicy.resolveUrl` so Coil reuses the shell
backdrop's cached request; skipped entirely when no URL or no realtime
blur) under the `glassBarTop→glassStrong` veil + glassEdge hairline.
`MiniPlayer` gained `embedded = true` (content only, no own chrome); the
single-pane `BottomNavigationBar` now renders MiniPlayer(embedded) +
transparent NavigationBar inside one GlassDock, so mini + nav read as one
glass sheet over the artwork. Rail layouts keep the floating MiniPlayer
(default `embedded = false` path unchanged). No new dependencies.

**CI result (2026-09-21):** first push run 35572182843 FAILED on exactly
one assertion — `DhunAppearanceTest` line 89 still expected the old
placeholderStart hex: the placeholder/shimmer pin edit in 78743bf did not
land even though the tool reported success. Fixed in `fd053df`, with every
pinned neutral now mechanically cross-checked against `DhunTokens` defaults
(parser compares test literals to production values — zero mismatches).
Re-run **35572454014 GREEN** (shared jvmTest incl. updated pins, Android
Robolectric + assembleDebug, probe, Desktop JVM, packaging helpers). Work is
PR **#107**; awaiting its packaging checks, then the user's visual verdict on
both platforms — no merge without it.

**Verification state:** local Python packaging/helper suite 29 OK (nothing
in scripts/ affected); brace-balance diff vs HEAD clean on all ten touched
files; WCAG replication as above. No JDK in sandbox — compile/test gate is
CI (`:shared:jvmTest` with the updated pins, Android Robolectric, Desktop
JVM, packaging). Open after CI: user visual verdict on Android + Windows
(lighter screens readable? dock glassy and continuous? thumbnails not
clipped in any rail?); record the verdict here before any merge.

## 2026-09-21 — PR #106 merged; `main` docs were stale; sync + verdict record (`arena/01a0c2c7-dhun`)

**Merged state (verified via `gh` on boot):** PR #106 merged into `main` as
`810bef1` at 2026-09-21T05:41:01Z after full green CI (post-merge main runs:
CI 35565454488, Build APK 35565454483, test-release 35565454562). Rolling
`test` re-published 2026-09-21T05:47:48Z with tag AND target =
`810bef13f34227360282df88f57372d6b0feba7c`; APK 18,334,451 B, MSI 112,889,856 B
(previous MSI 112,873,472 B — the byte size distinguishes new vs old installer).

**Incident:** the previous session (`arena/01a0c24e-dhun`) lost GitHub access
right after the merge. Its post-merge doc commits (push runs 35566034664 /
35566013932) landed only on that session branch, which has since been deleted
from origin — so `main`'s `.ai` docs still described the pre-merge state.
`origin/main` was verified to be exactly `810bef1` (= this session's branch
base). This session re-recorded the merged state, hardware verdicts, the saved
Windows gate procedure and the new task queue in ROADMAP/HANDOFF/CHANGELOG
before any code work.

**Hardware verdicts recorded (exact, no extrapolation):** Android **PASS** on
the merged build — "android all working" (Home moods, pull-to-refresh,
close/swipe behavior). Windows **NOT hardware-verified**: the user's machine
was still running an OLD copy (old round header refresh icon visible), which
explains the earlier "nothing works" report; the user accepted the new build
as good-to-go but has not tested it ("I'm not testing windows now"). Downloaded
playback `O3-6zB3kg8M` is fixed in code but untested on device — the earlier
Windows download/playback FAIL is triaged to the stale install, not disproven;
Gate 4 of the saved procedure is its retest.

**New user-requested task (2026-09-21): UI polish.** (a) Home/Search/Library
sit on the near-black dark ladder (`DhunTokens` dark defaults: background
0xFF0A0A0A, surfaces 0A→2A) — decrease darkness toward the full player's
background brightness; the full player's scrim over blurred artwork
(`Color.Black.copy(alpha = if (lyricsDominant) 0.52f else 0.16f)` plus
`ambientScrimBrush()` gradient stops ~0.42–0.62) is also lowered so artwork
shows through; WCAG-AA + `DhunAppearanceTest` contrast pins must stay green;
light theme untouched unless trivially symmetric. (b) `DhunSpacing.artworkThumb`
56.dp → ~64.dp, verify row heights/rails don't clip. (c) MiniPlayer +
`BottomNavigationBar` (ui/shell/DhunAppShell.kt ~line 570) become a glassy
bottom dock using the LYRICS-style background: BlurredArtworkCache +
`DhunColors.glass/glassDeep/glassStrong/glassBarTop`; no new dependencies.
Visual changes cannot be verified in-sandbox: CI green first, then user
eyeballs both platforms; record "awaiting user visual verdict"; never merge
without it.

**Queued (not started): endless radio.** While a radio plays, when ≤3 songs
remain, auto-queue more via the `/next` continuation (YouTube "Up next" for
the current track): same song, same position, seamless, no gap, then continue
the queue; replace the tail when refilling — supersedes the #99 "different
song" semantic. Related-songs row keeps excluding the current track, but an
explicit row tap still plays that row; gapless shuffle must visibly reorder.
Reproduce in engine code, add regression tests, fix. Gated on asking the user
first (Android is PASS; Windows accepted but untested).

**Boot discipline:** `gh pr list` shows only stale research PR #54;
`gh run list` shows no live runs; single-agent doctrine holds.

## 2026-09-21 — PR #106 final code verification before authorized merge

Exact code head **35d76f6**: build 35564494454 ✓, build-and-test 35564494452 ✓,
APK/MSI 35564494458 ✓. All relevant suites executed: shared, Android Robolectric,
probe/classification, Desktop JVM; 18 new regression cases across Home, close,
URI/resolver and local error routing. Local Python packaging/helper suite:
29 passed; whole-PR diff check passed. Earlier push CI 35564267958 also green.

User now explicitly authorizes merge after verification. Record docs/verdicts
before merge, await final docs-head CI too, then merge only the session PR #106.
No hardware PASS claimed: topic relevance, pull gesture, explicit-close behavior,
Windows downloaded-file replay and #105 checklist still need device evidence.
Post-merge release verification is the exact next step; if GitHub disconnects
on merge, next session must verify target/tag/publish time/bytes first.

## 2026-09-21 — Home chips were cosmetic; pull-to-refresh requested

User: For you / Focus / Chill / Workout / Party do not change songs below;
replace header refresh with a long downward swipe at top. Verified root cause:
HomeScreen stored selectedMood locally and only reordered title-matching lower
shelves, leaving quick picks/history/recommendations unchanged. No mood request.

Fresh HomeMood enum defines explicit topic-song search queries; GetHomeFeedUseCase
routes For you to browse and moods to song search + search continuation. No guessed
YouTube mood endpoints. HomeViewModel stores mood with feed generation, cancels
old work, preserves mood on retry/refresh and rejects late responses. Category
views omit unrelated rows. Persistent controls survive loading/empty/error; reset
list on category change. Material3 PullToRefreshBox provides threshold/indicator
and nested-scroll boundary handling, replacing header icon. Keep footer/F5/
accessibility refresh for non-touch users. No dependency additions.

Seven regression tests: all category queries/For-you return; repeat selection;
coalesced refresh in same mood; stale Home page; late prior-mood search; search
pagination/dedup; empty/error retry. Local diff check only; CI is compile/test
gate, hardware gesture and topic relevance remain open. User subsequently
explicitly authorized completion → verify/test → merge; do all docs/verdicts
before merge on the same PR #106 and branch.

## 2026-09-21 — Downloaded Windows row fails from Downloads: malformed file URI

User confirmed **Downloaded** row and **Library → Downloads** playback for
`O3-6zB3kg8M`; cannot collect logs. Code-level defects are independently visible:
`OfflineFirstStreamResolver.toFileUri` was just `"file://$path"`. A stored
`C:\Program Files\...` path became `file://C:\Program Files\...`, not a valid
escaped drive file URI. Desktop's remote-recovery bookkeeping also accepted the
local URI, then checked only the separate bounded cache on failure, explaining
why the shown error could falsely blame CDN/User-Agent instead of local media.

Fresh portable URI formatter normalizes Windows drive/UNC separators, percent
encodes UTF-8 filename bytes (including literal %, space, # and ?), preserves
Unix literal backslashes and leaves raw paths unchanged for file existence checks.
Desktop records the actual MRL at startMedia (including cache paths), reports
local errors directly and reserves CDN fallback for HTTP(S). Six shared cases
(path vectors + offline resolver/network bypass) and two desktop routing cases.
No forks/vendor code; no transport change or requirement for user console logs.

CI is the compile gate (no local JDK/SDK). Existing downloaded bytes/integrity
and libVLC behavior on the user's machine remain unverified: do NOT mark the
hardware failure fixed until existing download replays offline on the new build.
PR #106 remains unmerged, rolling test unchanged. Close-fix head 16a6cd6 had
build/APK green and build-and-test/MSI pending at this pre-push check.

## 2026-09-21 — Explicit close left music playing (`arena/01a0c24e-dhun`)

User clarified Windows X must quit completely; Android close means swiping the
app from Recents. Code: desktop X defaulted to hiding the window, with player
still alive; Android MediaSessionService had no explicit onTaskRemoved policy.
Initial default-only desktop proposal was superseded by the user's unconditional
X requirement before committing. X now calls the existing quit path regardless
of stored close-to-tray; obsolete settings UI removed (legacy key/model retained).
Android task removal pauses/stops the engine before foreground/service shutdown,
without stopping playback on Activity background/rotation. Helper regression
cases cover order/no queue clear, absent session, and cleanup despite stop error.
ASCII installer-description hyphen addresses reported shell metadata mojibake.

No local JDK/SDK: diff checks only; CI and hardware are required. Prior docs
commit c6326f0 on PR #106 passed all required checks. PR scope expanded on the
same fixed session branch; nothing merged and no merge permission given.
Windows download/playback report remains independently open pending evidence.

## 2026-09-21 — #105 release verification and Windows failure triage (`arena/01a0c24e-dhun`)

Docs-only follow-up: release API + tag both target `414cd79`, published
2026-09-21T03:00:04Z, APK 18,334,451 B / MSI 112,873,472 B. Updated ROADMAP
and HANDOFF to the seamless-radio contract and current download diagnostics.
Boot: only older PR #54 open, no active runs in recent list. Build/release CI
passed; scheduled extraction-health 35561269411 is later red with
ENVIRONMENT_BLOCKED annotation (check-run 106214457210), not a hardware verdict.

User reports Windows downloads but playback fails on `O3-6zB3kg8M` with CDN /
no-local-copy details. Other supplied test criteria are not confirmed passes.
Read-only trace found that DesktopDhunPlayer sets streamingRemoteUrl even for
an offline-first local URI, so the error wording alone cannot identify the
failure stage. Need row completion state, playback entry point, installed build,
matching download/cache diagnostics and, if completed, file existence/size.
No root-cause claim or speculative code change; endless radio remains gated.

## 2026-09-21 — Seamless radio + gapless shuffle + Android download rework, learned from GPL peers (`arena/01a0c1c9-dhun`)

**User report (round 3).** (1) "Play radio" must keep the SAME song playing
without pause and replace the queue — never restart (supersedes the #99
"move to a different song" semantic). Related tab must not list the playing
song. (2) Audible micro-pause on every shuffle toggle. (3) Downloads still
dead on Android after #98; Windows untested. Plus: study how similar OSS
projects implement downloads/radio/shuffle — learn, don't fork.

**Reference study (read-only via GitHub API; no fork, no vendored copy —
provenance row in THIRD_PARTY.md).** InnerTune `dev`
`MusicService.startRadioSeamlessly`: trim the timeline to the sounding item
with `removeMediaItems` before/after, fetch radio, `addMediaItems(radio
.drop(1))` — head untouched, zero interruption; shuffle is engine
`shuffleModeEnabled` + a pinned `DefaultShuffleOrder` (no timeline rebuild,
no gap); downloads run on Media3 `DownloadManager` over `OkHttpDataSource`
with `&range=0-N` appended "to avoid YouTube's throttling",
`maxParallelDownloads = 3`. OuterTune `lite` QueueBoard seamless branch:
same trim-around-playing trick ("`replaceMediaItems` seems to stop playback")
applied to every queue switch, including shuffle. ViMusic `master`
`startRadio(justAdd=true)`: `addMediaItems(process().drop(1))` + auto-extend
when ≤3 items remain (`maybeProcessRadio`) — endless-radio follow-up, not
this fix. RiMusic `master` (also KMP): `MyDownloadService :
DownloadService` + `SimpleCache`/`NoOpCacheEvictor` + retry 2 +
`Requirements(NETWORK)`; radio = `/next` + continuation (endless follow-up).

**What DHUN changed (fresh implementations, attribution comments in code).**
Radio: new `DhunPlayer.replaceQueueKeepingCurrent` (default = rebuild for
fakes only; both engines override seamlessly) + `QueueManager.
replaceKeepingCurrent` (head object preserved, shuffle reset, repeat kept);
Android trims/appends around the sounding MediaItem, desktop only repoints
the bookkeeper (engine untouched); `PlayerViewModel.startRadio` keeps the
head, position and artwork still; Related-tab filter from #99 kept.
Shuffle/mutations: deleted `reloadTimelinePreservingPlayback`
(`setMediaItems` + `prepare()` was the pause); new
`syncTimelineAroundCurrent` + single-insert `addNext`/`addToQueue`.
Downloads: Android transport CIO → OkHttp (`ktor-client-okhttp` in
`shared/androidMain`); always-Range (`bytes=0-` fresh); restart `.part` on
200-to-resume; 416-on-complete-part = success; connect + socket-idle
timeouts (no request timeout); workers pinned to `Dispatchers.IO`
(Android scope is Main); every failure `println`d (`DHUN download …` in
logcat) and worker crashes mark FAILED instead of sticking. If Android
downloads STILL fail after this, the logcat line names the stage (resolve
vs bytes vs worker) — ask the user for it instead of guessing again.

**Tests.** `QueueManagerTest` +4 (head/tail, defensive filter, noops,
shuffle-reset/repeat-keep); `PlayerViewModelTest` startRadio rewritten to
the seamless contract (head+position+no-seek) + no-op cases;
`StreamDownloaderTest` Range-always + 200-restart + 416; `FileDownloadManagerTest`
awaits terminal states (IO hop) + worker-crash case. CI is the compile
gate (no JDK in sandbox); hardware re-test per HANDOFF script items 1–6
(radio expectation flips to "same song continues").

## 2026-09-21 — Two sessions raced on the same defects; reconciliation without history damage (`arena/01a0c174-dhun`)

**Incident.** This session booted per handoff to "continue from where it is
paused": PR #100 (defect 3, shuffle) open with `build-and-test` pending.
While this session reviewed it (finding three real holes: Android queue
cursor never re-synced after engine auto-advance; desktop consuming source
indices while publishing the shuffled list — wrong-track taps under shuffle;
every queue mutation rebuilding + re-shuffling the upcoming order), the
previous session `arena/01a0c154-dhun` came back alive, merged #100
(00:58:25Z) AND opened+merged #101 (lyrics, 01:07:26Z). Detection was pure
luck-of-the-query: an early `gh pr list` showed #100 OPEN; a later
`gh run list --workflow=build-apk.yml` showed "#100 … main … 00:58:25Z
success" — the Actions log, not PR state, is what proved the race.

**Reconciliation.** (1) Treat merged #100 as immutable main history; do not
revert it (defect-3's core direction was correct). (2) Rebase this session's
corrective commits onto the new main (`git rebase --onto origin/main
870d3bc`), resolving each conflict toward this session's file versions —
they are strict supersets of #100's (kept #100's `setShuffle`/`displayQueue`/
`displayCurrentIndex` API so its surface survives; replaced its untested
seed-lottery test with a provable `ZeroRandom`-stub test). (3) Push with
`--force-with-lease=<branch>:<exact-old-sha>` — lease-guarded to THIS
session's branch only; main never rewritten. (4) Merged as #102 at
01:20:08Z; #100's holes were live on main ~22 minutes. (5) Post-merge CI +
rolling-`test` verified on the exact merge SHA. **Lesson for the user:** the
"one agent" rule is procedural, not technical — two sessions CAN interleave
merges; check `gh run list` (not just `gh pr list`) at boot for signs of a
live sibling before starting work.

## 2026-09-21 — CI caught a missing field declaration; annotations API readout (`arena/01a0c174-dhun`)

**Shape.** First CI run of PR #102: `build` ✓, `apk` ✓, `msi` ✓, but
`build-and-test` failed in step **"Unit tests — Android (Robolectric)"** —
10 × `Unresolved reference 'queueManager'` / `'it'` annotations, all in
`AndroidDhunPlayer.kt`. The android commit had used `queueManager` without
declaring it (the declaration+import existed on the superseded #100 branch
this session replaced; the JVM test step in the SAME run had already passed,
proving the shared `QueueManager` + its 9 new tests were green).

**Method.** No JDK in the sandbox; diagnosis purely via
`gh run view <id> --json jobs` (failed step name) +
`gh api .../check-runs/<job-id>/annotations` (per-line messages). Fix =
declare `private val queueManager = QueueManager()` + import, commit
`4b8204c`, next run fully green (build-and-test 5m52s). **Lesson:** failed
*step names* alone are ambiguous; the annotations endpoint is the real
compiler-output channel.



## 2026-09-20 — S1 closed: separating "runner-blocked" from "broken" with a diff, not a hunch (`arena/01a0c11b-dhun`)

**The problem this entry solves.** For two weeks the only extraction signal
available was a GitHub runner saying `ENVIRONMENT_BLOCKED`. That verdict is
ambiguous on its own: it is equally consistent with (a) YouTube bot-gating
datacenter IPs and (b) DHUN's own-client chain having rotted. Choosing wrongly
in direction (b) would have triggered ADR-007 work (PO tokens / attestation) —
weeks of high-risk code for a problem that may not exist.

**The disambiguation.** The user played 4 songs successfully from the rolling
`test` build on home WiFi. The question is then whether that build contains the
code the drill judged. Method:

```
gh api repos/99ggprooo00-code/DHUN/compare/6f7fa48...d99060e --jq '.files[].filename'
```

`6f7fa48` = the SHA of scheduled runs 35421383687 / 35489268023
(`ENVIRONMENT_BLOCKED`). `d99060e` = `target_commitish` of the `test` release
the user downloaded. Result: **9 files, every one documentation**
(`.ai/*`, `CHANGELOG.md`, `docs/runbooks/*`, `docs/verification/14-release.md`,
`tools/playback-probe/README.md`); filtering the compare for source extensions
returns **0**. Same extraction code, two networks, two outcomes → the variable
is the network, so (a) is proven and (b) is excluded.

**Build-identity proof (the user cannot read a SHA off the release page).**
Three independent facts were matched against the release API instead:
publish time 2026-09-20T16:46:20Z = 22:16 IST ≈ the reported "10 pm, ~6 hrs
ago"; APK 17,948,508 B = 17.1 MiB ≈ "17 MB"; MSI 112,861,184 B = 107.6 MiB ≈
"108 MB". This is exactly the identity protocol `s1-residential-evidence.md`
prescribes, and it worked as designed on first use.

**Trap recorded for future sessions.** Do not read a green residential result
as "playback is verified". It verifies *resolution and audio output on a real
network*. Soaks, media-session controls, offline, and the Windows native
surface were not exercised and stay open in S3. Conversely, do not read the
next `ENVIRONMENT_BLOCKED` runner result as a regression — it is now the
expected runner behaviour with a documented cause.


## 2026-09-20 — Scheduled runs confirm post-merge boundary; annotation readout method (`arena/01a0bd98-dhun`)

**Run identity.** Scheduled `extraction-health` runs **35421383687**
(2026-09-19, artifact `rot-drill-35421383687`) and **35489268023**
(2026-09-20, job **106021243260**, artifact `rot-drill-35489268023`) both
tested `main@6f7fa48c2546575d9cfca44d5eafbfeabf295f31` (PR #91 merged). Both
completed with conclusion `failure` at the intentional final gate only.

**Classification evidence without log egress.** Artifact and raw-log blob
downloads still return `EOF` in this sandbox, but the check-run annotations
API is reachable and carries the verdict: both runs emit
`warning: Extraction health is not a production pass ::
ENVIRONMENT_BLOCKED — inspect the probe log and verify playback outside
the GitHub runner.` The final gate's `Process completed with exit code 2`
is the `ENVIRONMENT_BLOCKED` branch of `extraction-health.yml`
(`UNAVAILABLE` would exit 3, unclassified `FAIL` would exit 1), and the
`Open or update a rot-drill issue` step was skipped in both runs (it fires
on `FAIL` only) — three independent signals agreeing, no step-name
guessing. Method: `gh run view <id> --json jobs` → job `databaseId` →
`gh api .../check-runs/<job>/annotations`.

**Decision.** The post-merge boundary holds: Home no longer drives a
`FAIL`, the resolver is honestly runner-gated, and no audio bytes are
validated. S1 stays open pending residential/device playback evidence;
no code change is indicated and none was made. A Node.js 20 deprecation
warning (checkout/setup-python/upload-artifact forced onto Node 24)
appears on both runs — pre-existing action-runtime maintenance noise,
not a verdict.


## 2026-09-18 — Home continuation transport aligned with independent client (`c71d1bb`)

**Comparison.** The diagnostic `ytmusicapi` client on the same GitHub runner returned
`continuationContents.sectionListContinuation` for the Home token while DHUN
returned only the tab-navigation shell. Source comparison isolated the request
contract rather than a parser gap: the independent client sends `alt=json`, an
empty `context.user`, `browseId` in the JSON body, the opaque token as both
`ctoken` and `continuation` query parameters, and the anonymous visitor header
from the Music homepage. DHUN had been missing the `alt=json` form and visitor
header and had also put the continuation in the body.

**Change.** Commit `56bd9b1` matched the body/query/URL contract and kept the
Home `browseId`; commit `c71d1bb` forwards the cached Music-home
`X-Goog-Visitor-Id` and adds a MockEngine assertion. `HomeFeedParser.kt` is
unchanged. No opaque tab endpoint is followed and no tab-only response is
accepted as success.

**Validation.** Extraction-health run **35325690972** tested `c71d1bb` and
classified the overall result as `ENVIRONMENT_BLOCKED`; the classifier step
passed, the rot-drill issue step was skipped, and the final non-PASS gate was
the only failing step. This moves the result past the previous Home-driven
`FAIL`; the remaining resolver bot gate is separate and no live audio bytes
were validated. Raw job logs still return `EOF` in this sandbox, and no local
Gradle test could run because no JDK is installed.


## 2026-09-18 — Current diagnostic run shows a tab-only Home shell; no parser payload (`arena/01a0b224-dhun`)

**Run identity.** Owner-dispatched `extraction-health` run **35321898985**
(run #7, attempt 1), job **105526042204**, checked out current branch head
`257251c84b934a6e93a4f44ffb1de39548c74b6a`. It completed 07:59:20Z with
failure and uploaded artifact `rot-drill-35321898985` (id **10537362749**).
Raw job logs/artifact download still return `EOF` in this sandbox.

**Probe result.** Version/search, first Home page, related tracks, and
zero-network offline playback passed. `home-more` failed with the expanded
shape-only evidence:

```
PROBE|home-more|FAIL|Parse(detail=Home response contained no section list or Home continuation action; shape=top[contents,responseContext,trackingParams];continuation[-];contents[singleColumnBrowseResultsRenderer];contentsItems[-];rootItems[-];browse[tabs];browseItems[-];tabs[tabRenderer];tabRenderers[endpoint,icon,selected,tabIdentifier,title,trackingParams];tabContents[-];tabSections[-];actions[-];commands[-];items[-])
```

The response contains a `tabRenderer` navigation shell with an `endpoint`, but
no tab `content`, no section list, no browse items, no actions, and no
continuation items. There is therefore no confirmed track/cursor payload for a
safe parser branch to consume. The resolver correctly emitted
`ENVIRONMENT_BLOCKED`; NewPipe remained the separate short-JSON watch. The
overall verdict correctly stayed `FAIL` because the shared Home continuation
response is not parseable as a Home page.

**Decision.** `f36cc76`'s diagnostic objective is complete. Do not turn this
tab-only shell into an empty successful page or invent an endpoint follow-up:
that would silently hide a continuation contract failure. Keep S1 RED until a
raw/sanitized response or a later approved run provides a real section/cursor
shape; Android and Windows/Desktop production paths remain untouched.


## 2026-09-18 — Current-head extraction-health run proves classifier; Home remains RED (`arena/01a0b224-dhun`)

**Run identity.** Owner-triggered `extraction-health` run **35316993036**
(run #6, attempt 1), job **105510712498**, checked out
`arena/01a0b224-dhun@ad1b403f35b3373a20fa0f83ca1c21f1d510baaa`. It completed
06:57:27Z with failure and uploaded artifact `rot-drill-35316993036` (id
**10535238461**). Raw job logs and artifact download still return `EOF` in this
sandbox.

**Probe result.** Version/search, first Home page, related tracks, and
zero-network offline playback passed. `home-more` failed independently:

```
PROBE|home-more|FAIL|Parse(detail=Home response contained no section list or Home continuation action; shape=top[contents,responseContext,trackingParams];continuation[-];contents[singleColumnBrowseResultsRenderer];contentsItems[-];rootItems[-];browse[tabs];browseItems[-];actions[-];commands[-];items[-])
```

The resolver emitted `ENVIRONMENT_BLOCKED`, and own-client/yt-dlp watch lines
also emitted `ENVIRONMENT_BLOCKED`, proving the new classification boundary is
working. NewPipe remained a separate `BROKEN|Parse(detail=JSON response is too
short)` watch. The overall verdict correctly stayed `FAIL` because a shared
Home parser failure is still present; the workflow remained non-zero.

**Narrow next patch.** Because the live body is unavailable, commit **`f36cc76`**
adds only safe nested key diagnostics for browse tabs, tab renderers, tab
contents, and tab sections. Push CI **35317377585**, Build APK **35317382758**,
and test-release **35317382644** pass. PR CI **35317382642** separately timed
out in `LibraryViewModelTest.kt:82` before the changed parser/probe steps; the
full push CI passed and no Android/Desktop production source was reopened.
The next owner run must test `f36cc76` to reveal the confirmed nested keys
before any parser branch is added.


## 2026-09-18 — Owner reran stale candidate job; final head still untested (`arena/01a0b224-dhun`)

**Run identity.** The supplied job link is workflow run **35310771629**, attempt
**5**, job **105507779324**, not a new run for the final branch head. GitHub
reports `head_sha=dbb3c0872dac2e7d010883b4e5ff7482561bc62a` (the older candidate),
started 06:42:21Z and completed 06:44:39Z. Its refreshed artifact is
`rot-drill-35310771629` (id **10535400903**). The artifact and raw job-log
endpoints still return `EOF` in this sandbox.

**Observed result.** The rerun reproduced the earlier old-head result: version,
search, first Home page, related tracks, and deterministic offline playback
passed; `home-more` failed with the same shape-only parse diagnostic; own-client
and yt-dlp were separately YouTube bot-gated; NewPipe returned its short-JSON
watch result. The job's steps show the old workflow revision and do not include
the newer `Classify probe result` step, so its `PROBE|verdict|FAIL` is not a
classification result from the final probe code.

**Decision.** This is useful confirmation of the stale candidate's behavior but
is not live validation of current code (`6dd98fb` plus docs-only heads). Android
and Windows/Desktop production paths remain unaffected. S1 stays RED and S2
stays blocked; the owner must dispatch `extraction-health` from the current
`arena/01a0b224-dhun` branch head.


## 2026-09-18 — Final probe/classifier head is CI-green; Android/Desktop unchanged (`arena/01a0b224-dhun`)

**Compiler repair.** CI **35313043505** had failed before tests because `Main.kt`
contained an invalid trailing comma in a Kotlin `when` branch at lines 201–202.
Commit **6dd98fb** removed that comma. No Android or Windows/Desktop production
extraction code was involved in the failure or the repair.

**GitHub verification.** Code head `6dd98fb` passed push CI **35313596684**, PR CI
**35313601849**, Build APK **35313601854**, and test-release **35313601903**.
The docs-only sync commit **`ad129be`** is now the final pushed PR head; its
push CI **35314651766**, PR CI **35314654589**, Build APK **35314654604**, and
test-release **35314654669** also pass. The checks cover shared domain tests,
Android Robolectric/debug build, probe compilation and `ProbeStatusTest`,
extraction-health classification, Desktop JVM compile/tests, and packaging. PR
#91 is OPEN, unmerged, and `CLEAN`.

**Remaining gate.** No owner-triggered live run has tested final pushed head `ad129be`. The latest
live candidate run **35310771629** tested older `dbb3c08`: metadata/search, first
Home page, related, and offline passed; `home-more` failed; own-client/yt-dlp
were separately bot-gated; NewPipe reported its separate short-JSON watch. S1
therefore remains RED and S2 remains blocked. The owner must dispatch
`extraction-health` on `arena/01a0b224-dhun@ad129be`; this agent still receives
HTTP 403 for workflow dispatch. No credentials, cookies, PO tokens, BotGuard,
attestation, ADR-007, resolver replacement, or platform rewrite was added.


## 2026-09-18 — Extraction-health status classification and production-path comparison (`arena/01a0b224-dhun`)

**Architecture result.** Android and Desktop production both reach the shared
`InnerTubeClient`, `parseHomeFeedPage`, and `OwnClientStreamResolver`. Desktop
adds the shared JVM `YtDlpStreamResolver` fallback; Android deliberately does
not. The probe directly constructs those same shared classes and uses the same
Desktop own-client → yt-dlp chain. It does not use platform DI, offline/cache
orchestration, Media3, libVLC, or NewPipe in production. No duplicate extractor
was found, so no resolver rewrite or synthetic CI extractor was introduced.

**Probe changes.** Home first-page and continuation failures now set the live
probe to `FAIL` because they exercise the production parser; they are no longer
merely informational. Resolver failures are classified without changing
`DhunError`: explicit `AuthRequired` bot evidence (`LOGIN_REQUIRED`, “Sign in to
confirm you're not a bot”, etc.) emits `ENVIRONMENT_BLOCKED`; ordinary
network/rate-limit/auth/unavailable results emit `UNAVAILABLE`; parser/unknown
resolver failures remain `FAIL`. All non-PASS statuses still exit non-zero so
an unverified live stream cannot become a green health check. NewPipe remains a
separate non-fatal watch.

**Workflow changes.** `extraction-health.yml` now preserves both offline and live
exit codes, parses the probe verdict, writes a job summary, warns explicitly on
runner limitation, opens a rot-drill issue only for `FAIL`, and keeps the check
non-zero for `ENVIRONMENT_BLOCKED`/`UNAVAILABLE`. A blocked runner is therefore
not filed as a DHUN parser/resolver regression, but it is also not declared a
production pass.

**Verification boundary.** `ProbeStatusTest` and the changed probe/workflow have
not run locally because this sandbox has no JDK; CI remains the compiler. The
existing Home parser candidate still requires an owner-triggered live run at its
current head and `home-more` must pass before S1 can close. No credentials,
cookies, PO tokens, BotGuard, attestation, or ADR-007 were added.


## 2026-09-18 — Candidate run 35310771629 keeps Home RED; nested browse follow-up added (`arena/01a0b224-dhun`)

**Owner-triggered validation.** The repository owner dispatched
`extraction-health` **35310771629** on `arena/01a0b224-dhun@dbb3c0872dac2e7d010883b4e5ff7482561bc62a` (`workflow_dispatch`). Probe job **105492165940** completed the live steps, uploaded artifact `rot-drill-35310771629` (id **10533178016**, 4,432 bytes), updated issue #14 in comment **5725612380**, and failed at the intentional alert step. The artifact/log blob again returns `EOF` in this sandbox; no raw response body or opaque token is recorded.

**Probe result.** Version/search (20 songs), first Home page (2 sections plus continuation), related (50), and deterministic offline playback passed. `home-more` remained RED:

```
PROBE|home-more|FAIL|Parse(detail=Home response contained no section list or Home continuation action; shape=top[contents,responseContext,trackingParams];continuation[-];contents[singleColumnBrowseResultsRenderer];contentsItems[-];rootItems[-];actions[-];commands[-];items[-])
```

The failure is separate from playback: own-client and yt-dlp remained `AuthRequired` / `LOGIN_REQUIRED` bot-gated, so no audio bytes were validated; NewPipe remained `Parse(detail=JSON response is too short)`. Keep all three findings separate and do not add cookies, sign-in, PO tokens, BotGuard, attestation, or ADR-007.

**Narrow follow-up.** The new diagnostic identifies the known top-level `contents` → `singleColumnBrowseResultsRenderer` envelope, while the existing parser only accepted its selected-tab section path. Commit **8dc88a1** adds `homeBrowsePage`, which accepts a scoped `sectionListRenderer` or direct `contents` section array under the known single-/two-column browse renderer; it does not recursively flatten arbitrary response objects. Fixture `nested-browse-contents.json` and a regression test cover that contract.

**Verification boundary.** Push CI **35311036178**, PR CI **35311039453**, Build APK **35311039470**, and test-release **35311039459** pass on `8dc88a1`; PR #91 is `CLEAN`, open, and unmerged. No local Kotlin/Gradle test ran because the sandbox has no JDK. This is not live acceptance: the next owner-triggered run must test current head `8dc88a1`. S1 remains RED and S2 remains blocked.


## 2026-09-18 — S1 live handoff is RED with two independent signals (`arena/01a0b224-dhun`)

**Authoritative run.** The repository owner dispatched workflow
`extraction-health` **35306224822** on `main@33e94b06125b8ce1eefe9aab0a2faca116ca53fe`
(`workflow_dispatch`, created 04:14:42Z, completed 04:17:00Z). The probe job
**105478849067** failed at the intentional alert step after emitting
`PROBE|verdict|FAIL|extraction-pipeline-broken`. Artifact
`rot-drill-35306224822` exists (artifact id **10532130174**, 4,357 bytes), and
workflow output was posted to issue #14. The signed artifact blob could not be
downloaded in this sandbox (`EOF`); the issue comment tail and artifact metadata
are the preserved evidence. No cookies, credentials, PO tokens, or signed URLs
are recorded here.

**Observed probe outcomes.** Java 17, yt-dlp **2026.08.19**, and
NewPipeExtractor **0.26.5** were used. Version and search passed (20 songs),
the first Home page passed (2 sections plus a continuation token), and related
tracks passed (50). The deterministic offline file probe passed with zero
network calls. The Home continuation check independently failed:

```
home-more|FAIL|Parse(detail=Home response contained no section list or Home continuation action)
WATCH|newpipe-stream|BROKEN|Parse(detail=JSON response is too short)
```

The production resolver then failed with `AuthRequired`; own-client and yt-dlp
watch paths both reported YouTube `LOGIN_REQUIRED` / “Sign in to confirm you're
not a bot”, so stream bytes were not validated. The NewPipe watch line is
non-fatal in the workflow, but it remains a separate parse signal.

**Classification boundary.** The own-client/yt-dlp result is strong evidence of
GitHub-hosted runner bot-gating, not permission failure and not permission to
add cookies, sign-in, PO tokens, BotGuard, attestation, or ADR-007. It must be
re-tested from an approved residential/device network. The `home-more` parse
failure is not dismissed as bot-gating: `parseHomeFeedPage` currently accepts
only direct section-list shapes or recognized append/reload action groups, and
the live response reached none of those branches. The raw continuation body is
not available because the artifact blob download returned `EOF`; the issue
comment has only the probe tail. That is enough to identify a live parser
contract mismatch, not enough to name the response shape or safely patch it.

**NewPipe boundary.** `NewPipeStreamResolver` uses NewPipeExtractor v0.26.5's
`NPStreamInfo.getInfo` through the tokenless `SimpleDownloader`; its
`ParsingException` is deliberately mapped to `DhunError.Parse`. The short-JSON
message therefore proves the NewPipe parser received an unexpectedly short
response, but without the body it cannot distinguish upstream schema drift from
a challenge/error page. It remains a diagnostic watch and is not a reason to
rewrite the production resolver chain.

**Decision.** S1 is **RED / unresolved**; S2 must not begin. Do not merge PR #91.
The next technical action is a sanitized capture or fixture of the actual Home
continuation response, followed by a narrow parser regression/fix if that
shape is confirmed. Keep the runner bot-gating evidence and the Home parser
failure as separate findings. No application source changed in this evidence
reconciliation; no local Kotlin/Gradle test ran because the sandbox has no JDK.



## 2026-09-18 — Home continuation parser candidate added after S1 RED (`arena/01a0b224-dhun`)

**Why this is a candidate, not a live-fix claim.** Run **35306224822** exposed
`home-more|FAIL|Parse(detail=Home response contained no section list or Home continuation action)`,
but the uploaded blob could not be downloaded (`EOF`), so the exact response body
is unavailable. Existing parser coverage handled `sectionListContinuation` and
carousel/immersive append actions, but not the other standard YouTube Music
continuation family: `musicShelfContinuation` / `musicPlaylistShelfContinuation`
wrappers or action items carrying `musicShelfRenderer` / playlist shelf renderers.
That is the narrow contract gap addressed here; a future probe must confirm it
matches the live response rather than treating inference as evidence.

**Change.** `HomeFeedParser` normalizes shelf-specific continuation wrappers,
recognizes shelf renderers in append/reload action groups, and `parseHomeSections`
now reads vertical/playlist shelf rows plus direct shelf/header titles. An
unmatched response now reports only top-level/continuation/action/command/item
**keys**—never cursor values or tracking data—so a future live run can identify
its shape without logging opaque continuation material. Two synthetic fixtures and
parser tests cover the direct shelf continuation and append-action variants.

**Verification boundary.** JSON fixture validation and the Python helper suite
are the only local checks; Kotlin/Gradle remains unrun because no JDK exists.
CI is the compiler. The candidate is not live-validated, does not change the
resolver chain, and does not weaken byte checks. S1 stays RED until CI plus a
sanitized owner-triggered probe/fixture confirms the shape. Bot-gating remains a
separate residential/device investigation; no cookies, credentials, PO tokens,
BotGuard, attestation, or ADR-007.

**First CI feedback and correction.** CI run **35307551559** reached the shared JVM tests and
caught two candidate-test defects: the new shelf wrapper exposed the cursor one level deeper
than `homeListContinuation` inspected, and the old `wrong-shelf-continuation` fixture was now
an intentionally supported `musicShelfContinuation`. The fix scopes cursor extraction to the
normalized shelf object and changes that negative fixture to an unsupported grid continuation.
This was a genuine test failure, not a live verdict; APK/build packaging checks on the same
head passed, but later steps were skipped by the shared-test failure. The correction is now
CI-green at parser evidence head **005b526**: PR CI **35307916827**, push CI **35307913766**,
Build APK **35307916736**, and test-release **35307916668** all passed, including the shared
JVM parser suite. This proves compilation/tests for the candidate only; it does not prove the
live Home response uses either covered shape.


## 2026-09-18 — Candidate-branch run 35308796439 keeps Home RED (`arena/01a0b224-dhun`)

**Owner-triggered validation.** Workflow `extraction-health` run **35308796439**
used candidate branch `arena/01a0b224-dhun` at `c546d7b797157bfd5d2eb6954c45ceefe54bb686`.
The probe job **105486452033** completed the live steps and uploaded artifact
`rot-drill-35308796439` (id **10532443661**, 4,402 bytes), then failed at the
intentional alert step. The artifact blob again returned `EOF` from this
sandbox; issue #14 preserves the tail. No cookies, credentials, PO tokens, or
signed URLs are recorded.

**What the candidate run proved.** Version/search passed, Home first page passed
(2 sections and a token), related passed (50), and the offline probe passed.
The first shelf-continuation candidate did **not** clear `home-more`:

```
PROBE|home-more|FAIL|Parse(detail=Home response contained no section list or Home continuation action; shape=top[contents,responseContext,trackingParams];continuation[-];actions[-];commands[-];items[-])
```

The shape-only diagnostic is useful: the continuation response has a top-level
`contents` field, not `continuationContents` or an action array. The candidate
parser did not yet inspect the direct nested contents contract. Own-client/yt-dlp
still returned `AuthRequired` bot-gating, NewPipe still returned
`Parse(detail=JSON response is too short)`, and no audio bytes were validated.

**Follow-up.** The next narrow patch accepts a shelf object directly under
`contents` when it yields rows/cursor, and expands shape-only diagnostics to
report the direct contents/item keys. This is still a source/test hypothesis
until CI and another owner-triggered candidate run confirm it. S1 remains RED;
S2 remains blocked; no auth/attestation workaround is permitted.


## 2026-09-18 — Direct-contents follow-up is PR-green; one push check flaked (`arena/01a0b224-dhun`)

The follow-up parser candidate is `526e3904ec1d59c04a8a7ac595157dbf913841e6`.
PR CI **35309128614**, Build APK **35309128640**, and test-release
**35309128612** passed, including the shared JVM parser suite. The independent
push CI **35309124090** failed at the unrelated
`LibraryViewModelTest.eventually` 15-second timeout; its annotations contain no
Home/parser failure. This is a CI flake on the same SHA, not evidence against
the parser patch, but the branch was temporarily `UNSTABLE` until a later push
check. Push CI **35309533562** on docs-only successor `98843af` passed, and PR
#91 is now `CLEAN`; no parser failure was found. No local Kotlin test was run.

The direct-contents patch is still not live-validated. The next owner-triggered
probe must use current head `98843af32e8326c8329a5c33d2c06ddfc30a9485`; S1 remains
RED and S2 remains blocked.

## 2026-09-18 — S1 boot reconciliation: registration is healthy, live verdict is still absent (`arena/01a0b224-dhun`)

**Current gap.** The repository is at `main@33e94b0` after PR #90. Main CI **35246193151**, Build APK **35246193174**, and test-release **35246193097** all pass, and the rolling `test` release points at that SHA. The replacement workflow `extraction-health` (id **360655315**) is active with the declared name, but `gh run list --workflow extraction-health.yml` returns no runs.

**Last actual extraction error.** The latest real scheduled probe is **34083253658** (`main@d1e0408`, 2026-09-07), not a push-trigger artifact: metadata/search/related passed, the production own-client chain and yt-dlp were bot-gated with `AuthRequired`, NewPipe reported its known parse watch, and `PROBE|verdict|FAIL|extraction-pipeline-broken` was emitted. The many zero-job push failures are trigger noise and are not cited as extraction verdicts.

**Root cause/blocker.** The file-registration repair succeeded, but this agent's GitHub integration token still receives HTTP 403 for `workflow_dispatch` (and cannot write issue comments). A human must click **Actions → extraction-health → Run workflow** on `main`, or the 04:17 UTC schedule must fire. Until an artifact and live verdict exist, S1 cannot close and S2 must not start.

**Verification boundary.** No application code or extraction semantics changed in this handoff. The sandbox has no JDK/Android SDK/adb, so no local Gradle test ran; the only local check for this docs reconciliation is `git diff --check`. Hardware playback, live Home pagination, visuals, and soaks remain unverified.

**Verification.** PR #91 last verified head `f2dffea` is CI-verified: CI runs **35297881950** and **35297879208**, Build APK **35297881997**, and test-release **35297882070** all pass. The PR remains open and unmerged.

**Next action.** Obtain and record the live `extraction-health` result before doing S2 cleanup. PR #91 must not be merged without explicit user instruction.

## 2026-09-16 — the new desktop-test gate immediately finds 3 latent failures from PR #47 (`arena/01a0aa8e-dhun`)

**Symptom.** First CI on PR #71 (`build-and-test` run `35112656439`, 8m26s)
red on the brand-new step — and only there (`apk`/`msi`/`build` pass):

```
TaskExecutionException: Execution failed for task ':app-desktop:jvmTest'.
AssertionError: expected:<7> but was:<8>  @ JumpListModelTest.more than MAX_RECENT recents are capped(JumpListModelTest.kt:57)
AssertionError: expected:<3> but was:<4>  @ JumpListModelTest.entries with invalid track ids are dropped, others survive(JumpListModelTest.kt:48)
AssertionError: Expected value to be false.  @ JumpListArgsTest.id validation allows only plain id characters(JumpListArgsTest.kt:56)
```

**Root cause — two independent defects, both latent since candidate 27 and
both invisible because `:app-desktop:jvmTest` was never a CI step:**
1. **Two test assertions forgot the separator.** `buildTasks` returns
   recents + separator + 2 verbs (its KDoc order, and what the passing
   sibling test `recents come first…` asserts: 2+1+2=5). The capped test
   asserted `MAX_RECENT + 2` (= 7) while its own comment says "5 recents +
   separator + 2 verbs" (= 8); the invalid-id test asserted 3 for one
   surviving recent (1+1+2 = 4). Production matched the documented order in
   both cases — the assertions were wrong from the day they were written
   (`d33adc9`) and never executed anywhere.
2. **`isValidTrackId` used Unicode-aware `isLetterOrDigit()`.** Its own KDoc
   promises `[A-Za-z0-9_-]` ("plain id characters", YouTube video ids), and
   the test asserts `ä` is rejected — but `Char.isLetterOrDigit()` admits
   Unicode letters, so `ä` passed. The implementation violated its documented
   contract; classic Kotlin trap.

**Fix.** Test assertions corrected to 4 and `MAX_RECENT + 3`; the validator
is now an explicit ASCII range check (`a-z`, `A-Z`, `0-9`, `-`, `_`) with a
KDoc note recording *why* `isLetterOrDigit()` is wrong here. Zero real-world
behaviour change: track ids are YouTube video ids (ASCII), so no legitimate
jump entry is affected.

**Verification.** Re-push; CI is the compiler. Two honest caveats: (a) this
red run consumed all 10 check-run annotations (the per-run cap), so further
failing tests could be hiding behind it — the re-run is the confirmation,
not this diagnosis; (b) no separate mutation run is needed for the new step:
a step that merely compiled could not produce failing test *names* with
file:line, so execution is proven by this genuine red.

**Lesson, filed where it will be read:** PR #47's own fix commit (`6275beb`)
says "the pure-core tests referenced it too, but `:app-desktop:test` is not
a CI step — exactly the gap the disclosed CI limit describes". A test suite
that never executes does not merely fail to protect — it *rots*: the two
separator assertions were born wrong and sat for a week looking like
coverage. Unexecuted tests are documentation with a false uniform.

## 2026-09-16 — FullPlayer and LyricsCard lacked Android <12 blur fallback guard (`arena/01a0aa7a-dhun`)

**Symptom.** On Android versions below API 31 (minSdk is 26, so Android 8.0–11),
`Modifier.blur` is a RenderEffect no-op. While `NowPlayingBackdrop` guarded against
this via `supportsRealtimeBlur` to prevent sharp stretched album covers from being
drawn behind Home/Search/Library, `FullPlayer`'s `PlayerBackdrop` bleed layer and
`LyricsCard` lacked this guard and would draw unblurred sharp artwork behind controls
and lyrics text.

**Root cause.** `FullPlayer.kt` called `ArtworkImage` with `.blur(blurRadius)` directly
without checking `supportsRealtimeBlur`.

**Fix.** Added `shouldRenderPlayerBackdrop` in `FullPlayer.kt` and `shouldRenderBackdrop`
in `NowPlayingBackdropPolicy.kt` that check both non-blank artwork URL and
`supportsRealtimeBlur`. On devices lacking realtime blur, `PlayerBackdrop` and `LyricsCard`
suppress the sharp artwork layer, gracefully falling back to the clean dark surface,
ambient gradient scrim, and readable text.

**Verification.** Unit tests added in `NowPlayingBackdropPolicyTest` and `PlayerSheetLayoutTest`
passed in CI `build-and-test` run 35106060769. Packaging passed in `build` run 35106060727
and `apk`/`msi` in run 35106060559.

## 2026-09-16 — `viewConfiguration.minimumFlingVelocity` does not exist in CMP 1.8.2 (`arena/01a0aa5e-dhun`)

**Symptom.** First CI on PR #69 (`35102094935` / apk `35102094887`) red on
`:shared:compileKotlinJvm` and `:shared:compileDebugKotlinAndroid`:
`HorizontalRail.kt:217 Unresolved reference 'minimumFlingVelocity'`.

**Root cause.** Compose Multiplatform 1.8.2 `ViewConfiguration` (the receiver
on `PointerInputScope`) exposes `touchSlop` / timeout millis, not
`minimumFlingVelocity`. That property exists on Android `ViewConfiguration`
and on later Compose; it is not on the pinned 1.8.2 common interface.

**Fix.** Pin `MouseRailFling.MIN_FLING_VELOCITY_PX_PER_SEC = 50f` — Android's
unscaled `MINIMUM_FLING_VELOCITY` — and pass that into `shouldFling`. Tests
already used 50f.

**Verification.** Re-push; CI is the compiler. Hardware feel of the floor is
still the user's gate.

## 2026-09-10 — PR #55 merged with all three gates red; `main` could not compile for 6 CI runs (`arena/01a08976-dhun`)

**Symptom.** Four required-gate jobs red on `main`, and the pattern is what makes it
interesting — it is not one failure but two, stacked 6 minutes apart:

```
shared/src/commonMain/kotlin/dev/dhun/innertube/InnerTubeClient.kt:268
    Argument type mismatch: actual type is 'Function0<JsonElement?>', but 'JsonElement' was expected.
shared/src/commonMain/kotlin/dev/dhun/innertube/InnerTubeClient.kt:300
    Unresolved reference 'visitorData'.
> Task :shared:compileKotlinJvm            FAILED   (jvm + android target → CI, apk, msi)
> Task :shared:compileDebugKotlinAndroid   FAILED

Build APK · run 34434063405, step "Assemble debug APK"
    ProjectSelectionException: Cannot locate tasks that match ':app:assembleDebug' as
    project 'app' is ambiguous in root project 'dhun'. Candidates are: 'app-android', 'app-desktop'.
```

Red runs, all on `main`: CI `456`(PR head)/`457`/`458`, test-release `177`/`178`/`179`,
`Build APK` `1`/`2`, rot-drill `104`/`105`/`106`.

**Root cause (three, independent).**
1. **`put(key) { … }` does not exist on `JsonObjectBuilder`.** `altContext` nested an
   object with `put("contentPlaybackContext") { … }`; the lambda was matched against
   `put(key: String, value: JsonElement?)` and Kotlin reported the coercion, not the
   missing overload. Correct form is `putJsonObject`. Anyone who has only written
   Java-style builders will reach for the wrong one, and **the sandbox cannot compile**
   (no JDK; Maven/Gradle egress refused), so the author never saw it.
2. **A patch that spans layers must thread its parameters.** `visitorData`/`signatureTimestamp`
   were added to `altPlayerResponse` + `altContext`, but the header was added in
   `postAltJson`, whose signature was never changed. The fix threads
   `visitorData: String? = null` into `postAltJson` and sends `X-Goog-Visitor-Id`
   **only when non-null** — as merged, every alt `/player` request would have carried an
   *empty* visitor header, i.e. a different and strictly worse request than before.
3. **`build-apk.yml` was written from a generic Android template.** This repo's modules are
   `:app-android` / `:app-desktop` / `:shared` / `:tools:playback-probe`; there is no `:app`,
   and `app/build/outputs/apk/debug/app-debug.apk` has never existed. Now pinned by
   `scripts/test_apk_workflow.py`, which cross-checks every workflow's
   `*/build/outputs/...` path against `settings.gradle.kts` (proven to go red on the old file).

**The process defect underneath.** #55 was merged while `build-and-test`, `apk` and `msi`
were **all failing at its own head** (`gh pr checks 55` shows `fail` ×3 next to a
`MERGED` state). A red head merged into `main` is not a small thing here: the rolling
`test` pre-release republishes only on a green push to `main`, so it is frozen at
`cd97464` and **every PR merged after it — #51, #52, the docs pushes — ships no artifact at
all**. Second-order effect, and the reason "CI red" must be read as *unknown*: a compile
failure suppresses the suites behind it, so `:shared:jvmTest` and
`:app-android:testDebugUnitTest` have not executed on `main` since `cd97464`.

**Also recorded because it will be misread as a fix.** The `visitorData`/`signatureTimestamp`
parameters are **inert**: all six `altPlayerResponse` call sites in
`OwnClientStreamResolver.kt` pass nothing, so no session material reaches the wire and
issue #14's `AUTH_REQUIRED("Sign in to confirm you're not a bot")` is untouched. #55 also
shrank resolve Wave 1 from `[web_embedded, visionos]` to `[visionos]`, leaving
`STRATEGIES[0]` in the list but unreachable from any wave. **A knob nobody turns is not a
fix; and a merged commit that changes no bytes on the wire cannot change server behavior.**

**Fix + verification state.** `putJsonObject`, threaded parameter with a null-guarded
header, 2 `MockEngine` tests pinning the alt `/player` body/headers in both states,
corrected `build-apk.yml`, new workflow contract test, ROADMAP/limitations/docs.
**Merged as `be51d7d` (PR #56, `2026-09-10T04:21:16Z`) and green on `main`:**
`build-and-test` `34436859375` success — including the Python workflow-contract step,
`:shared:jvmTest` and the Android debug build with its coupled suite — `apk`/`msi`/`publish`
run `183` success, `Build APK` run `6` success. The masked suites turned out clean, so
`073083c` had no hidden second failure. **What this does NOT verify:** that the request
shape is what YouTube wants (nothing sent, no caller), and that audio is audible — the
user's device is still the only proof of that, and issue #14 stays open.

**Addendum found in the same verification pass — the drill's "red" was partly noise.**
`rot-drill.yml` triggers on `schedule` + `workflow_dispatch` only. Every
`event=push` rot-drill run (today's `104`–`113`, and the ones cited in past
snapshots) has **0 jobs** and a `failure` conclusion — GitHub's no-matching-trigger
artifact. `gh api …/actions/runs/<id>/jobs --jq .total_count` returns `0`, which is the
whole test. Filtering by `event=schedule` shows the honest picture: #10 @
2026-09-07T04:28Z failed (real, and it did auto-comment on issue #14 as designed),
#2/#3/#4 on 09-03…09-05 were **green**, and **no schedule has fired since 09-07** even
though the workflow reports `state=active`. Agents cannot force one —
`gh workflow run rot-drill.yml` → `HTTP 403 Resource not accessible by integration`.
Two rules follow: *never cite a workflow run whose job count is 0*, and *verify a
scheduled workflow by its `event=schedule` runs, not by the noise in the run list* — a
silent three-day gap in the very job that watches extraction is the doctrine failing
open.

**Lesson, filed where it will be read:** the two errors were one patch touching three
layers (`altPlayerResponse` → `altContext` → `postAltJson`) with the middle edited and the
bottom forgotten, in a sandbox that cannot compile. The countermeasure is not "be
careful": it is (a) never merge a PR whose `gh pr checks` are not green, and (b) give the
blind spots a static gate that CI actually runs — here, a Python workflow-contract test,
the same trick `scripts/test_build_workflow.py` uses for the release pipeline.

## 2026-09-07 — PR #41's head never compiled, and a second red hid under it (`arena/01a07ad8-dhun`)

**Symptom.** All three required gates red on `arena/01a07a6b-dhun` @ `7c24fde`
(`build-and-test` ×2, `apk`, `msi`; `publish` skipped). CI annotations named one thing,
twice:

```
shared/src/commonMain/kotlin/dev/dhun/ui/player/PlayerSeekBar.kt:L245  Unresolved reference 'width'.
shared/src/commonMain/kotlin/dev/dhun/ui/player/PlayerSeekBar.kt:L261  Unresolved reference 'width'.
> Task :shared:compileKotlinJvm          FAILED   (jvm + android target)
```

**Root cause.** `ff28f4a` replaced the thumb's inline offset math with the pure helper
`trackAlignedItemOffsetPx(itemWidthPx: Int, widthPx: Float, fraction: Float)` and passed
`width.toInt()` for `itemWidthPx`. Two errors: `width` is not a member of
`BoxWithConstraints` scope (only `constraints` / `maxWidth` are), and the helper's first
argument is the **item** width, not the track width. It reached the branch because the
sandbox cannot compile (no JDK; Maven/Gradle egress refused) and the commits were pushed
without waiting for a verdict on them.

**The defect it masked.** `:app-android:testDebugUnitTest` had been failing since
`32e38c5` (runs `34092576415` / `34092579729`) on
`NavStatePersistenceTest."corrupt or future route entries are dropped, valid ones
survive"` — expected 3 routes, got 4 including `ArtistPage(id=)`. `save`/`restore`
encode routes as `artist:<id>` / `playlist:<isLocal>:<id>`, and `parts.getOrNull(1)?.let(…)`
guards **null** only; `split(':', limit = 3)` yields `""`. A corrupt entry therefore
restored as a live, permanently unresolvable back-stack item.

**Fixes, and who owns them after the split.** This session fixed both (`f2d2359` thumb
`thumbSize.roundToPx()` + pill measured outside its padding; `bf4449a` require non-blank
ids). Mid-session, the a6b session revived and force-pushed `arena/01a07a6b-dhun` from
`7c24fde` to `cd40c1f`, **retargeting #41 to the player workstream only** and
then merged the player workstream as `dd0fe14`, independently fixing the same break in
`d685ddc`. So `#43` was re-cut to `origin/main` +
the 8 Android commits + the nav fix at new SHAs, and the `shared/**` fix was deliberately
**dropped** — otherwise the same file would be patched twice by two PRs. Net rule
recorded: when an inherited branch is *shared*, the fix follows the file owner, not the
session that found it.

**Process trap, costlier than either bug.** The handoff asserted (a) PR #41 was
"merged-or-closed" — it was OPEN; (b) "9 commits … safe at `32e38c5`" — 13 commits, and
`32e38c5` was red; (c) "`phase15-android-polish-status.md` written at the repo root" —
absent from every commit on every branch. All three were one `gh` call away. The pre-push
ritual says verify on GitHub, not locally; it applies to **inherited claims** too, or a
false baseline propagates and the next agent trusts a merge that never happened.

**Verification state.** The 15a half is on `main` (`dd0fe14`, all gates green at
`cd40c1f`) with its own fix for defect 1; #43's Android suite is green at `434ad92`
(`build-and-test` `34099826064`; `apk` + `msi` `34099826022`) after the re-cut dropped
`f2d2359` from it. Earlier heads were green *with* the player batch (`CI #339` at
`f2d2359`; `34098780631`/`34098780641` at `00a432c`) — kept to show both fixes were sound
before the split, not as separate achievement. `rot-drill` red on every branch including `main` is the
known issue #14, not this code.

## 2026-09-07 — Windows second-window report: investigated, no code change warranted

**Report:** on Windows, opening DHUN also opens a second small mini-player window
alongside the real app.

**Finding: already fixed, twice, and merged.** `ADR-004` records the identical
complaint against the `test` build of `2026-09-06T06:51:40Z`. PR #28 (`b8f148d`)
deleted `ui/MiniPlayerWindow.kt` and the second Compose `Window`; PR #34 (`d1e0408`)
removed the remaining `JOptionPane` startup/fatal surfaces.

**Static audit of `481b77b`** (exhaustive grep over `app-desktop/**/*.kt` for
`Window(`, `ComposeWindow`, `JOptionPane`, `JDialog`, `JWindow`, `JFrame`,
`java.awt.Window|Frame|Dialog`, `AlertDialog`, `Dialog(`, `Popup(`, `Tooltip(`,
`SetWindowPos`, `GetWindowRect`, `moveWindow`, `FindWindow`, `CreateWindow`,
`ShowWindow`, `HWND`):

| Path | Verdict |
|---|---|
| `Main.kt:228` `Window(` | startup-error window — gated by `initError != null && koinInstance == null`, terminated by `return@application` at line 245 |
| `Main.kt:420` `Window(` | the main window — normal path only |
| `Main.kt:290` `AtomicReference<ComposeWindow>()` | holds a reference; creates nothing |
| `Main.kt:293` `showMainWindow()` | `isVisible` / `toFront` / `requestFocus` on the existing window |
| `DhunTray.kt` | `TrayIcon` + `PopupMenu` — not a window |
| `Smct.kt:451/459` `FindWindowW` | **finds** the existing `SunAwtFrame` HWND for `GetForWindow`; movement calls deleted (`Smct.kt:442-443`) |
| `JOptionPane` | 0 imports; one comment at `Main.kt:112` |

**Conclusion:** two simultaneous windows are not reachable from DHUN's own code on
`481b77b`. Either the tested build is older, or Koin init failed — in which case the
error window *replaces* the main window, so the user would still see exactly one.

**Decision: no `app-desktop` change.** Re-fixing a fixed bug adds risk without
evidence. The open work is a hardware re-test of the rolling `test` MSI
(`481b77b`, published `2026-09-07T04:58:25Z`); if it reproduces, `dhun-startup.log`
distinguishes the Compose error window from a leftover AWT surface.

**Boundary:** this audit is static. No Windows machine or display exists here, so
one-window startup has never been verified on hardware for any build.


## 2026-09-07 — C1: Koin self-recursion in the Android `DownloadManager` decorator (coordinator `arena/01a07a07-dhun`)

**Symptom (predicted, never observed on hardware):** Android app would crash at
launch with a `StackOverflowError` out of Koin internals, before any user interaction
— while `build-and-test`, `apk`, and `msi` all reported **green** on the same commit
(`a4dc28d`, PR #35).

**Root cause.** `app-android/src/main/kotlin/dev/dhun/android/di/AppModule.kt`:

```kotlin
single<DownloadManager> {
    ForegroundServiceDownloadManager(
        context = androidContext(),
        delegate = get(),        // <-- inferred as get<DownloadManager>()
        controller = get(),
    )
}
```

`ForegroundServiceDownloadManager` declares `private val delegate: DownloadManager`, so
the unqualified `get()` type-infers to `get<DownloadManager>()` — **the very definition
being constructed**. Koin 4.0.2 (`app-android/build.gradle.kts:84`) stores a singleton
*after* its factory returns, so nothing memoises the in-progress instance and the
resolution recurses.

**Why it fires at launch, not on first download.** `MainActivity.kt:197` passes
`downloadManager = koin.get()` into `DhunAppShell`, whose parameter is
`downloadManager: DownloadManager? = null` (`DhunAppShell.kt:122`). That resolution
happens during activity composition.

**Why CI could not catch it.** `:app-android:assembleDebug` is a type-check gate, and
`:app-android` has **no test source set**, so no smoke test existed. A DI cycle is a
runtime property of the object graph, invisible to a compiler.

**Fix (agent 1, `ef69f82`):** `delegate = get<FileDownloadManager>()` — explicit type,
breaking the cycle and pointing at the concrete singleton registered immediately above.
Regression test `shared/src/jvmTest/kotlin/dev/dhun/di/KoinDownloadStackTest.kt`
(`705a946`).

**Boundary on that test:** it lives in `:shared:jvmTest` and mirrors the production
registration *shape* using minimal fakes, because `:app-android` has no test source
set. It pins the pattern so the unqualified-`get()` form cannot quietly return; it
does **not** verify the real `appModule`. A `checkModules()` call or an `:app-android`
smoke test remains open.

**Coordinator honesty note:** this diagnosis was **static analysis**. The coordinator
has no JDK/Gradle in its sandbox and works from git + gh only, so it never produced a
reproduced stack trace. It was posted as a review comment on PR #35 and recorded as a
blocker in `INTEGRATION.md`; the fix was routed to agent 1, which owns
`app-android/**`.

**Gate applied and outcome:** #35 was held while red. The regression test took three
commits to compile — `705a946` (test added; `koin-test` missing from the
`:shared:jvmTest` classpath → run `34083073966` **failure**), `4fd9636` (added
`koin-test`; **a wrong turn** — `KoinTest` still did not resolve, errors unchanged →
run `34083348460` **failure** at `:shared:compileTestKotlinJvm`), `fb32711` (dropped
`koin-test`, read Koin through `GlobalContext.get()` directly → **green**,
`build-and-test` pass in 5m19s, run `34083713576`). **The production fix compiled
throughout** — every annotation in both red runs was inside the test file, never
`AppModule.kt`. Merged via PR #35 as squash `40eff1d`; main `481b77b` fully green.

**Lesson worth keeping:** two consecutive red runs on a branch whose *production* code
was correct. Reading the failing **Gradle task** (`:shared:compileTestKotlinJvm`) and
the annotation **file paths** — rather than the PR's overall red/green — is what kept
the C1 fix from being reverted along with its broken test.

**Second finding from the same pass — C2, inert UI.** `DhunAppShell` accepted
`downloadManager` and forwarded it to `LibraryViewModel` (line 139) and the overflow
`onDownload` (line 364) but **not** to `HomeScreen` (line 535) or `SearchScreen`
(line 549). Agent 3's new badges therefore compiled and rendered nothing. Fixed by
agent 3 in `e987f64`; the coordinator's exemption to write the pass-through was not
exercised. Related hazard: the parameter was inserted mid-list (7th of 12 / 7th of 8),
which is safe only because both callsites use named arguments.

## 2026-09-07 — ADR-006 offline playback probe added (session `arena/01a079f6-dhun`)

**Change:** Added `tools/playback-probe:offlineProbe` and a valid WAV fixture.
The probe uses the real JVM SQLDelight `DownloadRepository`, inserts a
`COMPLETED` `DownloadedTrack`, resolves through `OfflineFirstStreamResolver`,
requires a `file://` URI pointing to the committed local path, opens the file,
checks the RIFF/WAVE header, and fails if the injected network resolver is
called. It is exposed as:

```text
./gradlew :tools:playback-probe:offlineProbe --offline --no-daemon
```

**Verification state:** `git diff --check` passes. Local execution is blocked
in this sandbox because neither `JAVA_HOME` nor a `java` executable exists.
`scripts/restore-toolchain.sh` also could not download Temurin/Gradle because
TLS egress is unavailable. GitHub CI run `34080947691` compiled the probe,
but the existing workflow does not run the runtime task; a JDK-equipped
environment or explicit CI execution step is still needed for runtime evidence.

**Important boundary:** this is deterministic shared/JVM repository-to-file
verification, not Android Media3 `FileDataSource` verification, Desktop vlcj
decoding verification, or audible playback. Real Android device and Desktop/PC
checks remain open and must stay open in the roadmap even after CI passes.

**Follow-up:** pushed commits `bf5376b`/`2706066`/`aeec1e6`/`20d8ddf` are covered by PR
CI run `34081374800`, which passed the existing `Probe compiles` step. The
workflow does not invoke the new runtime task, so no `offline-verdict|PASS`
claim is made yet.

## 2026-09-07 — ADR-006 foundation + download engine landed (PR #33)

**Implemented this session (PR #33 `arena/01a07989-dhun`):** ADR-006
persistent offline downloads — data layer (schema v3: `DownloadedTrack` table,
migration `2.sqm`, `SqlDelightDownloadRepository` wired into `DataLayer`),
download engine (`DownloadManager`, resumable `StreamDownloader` with Range
resume + `.part` atomic commit, `DownloadStorage`, `FileDownloadManager` with a
bounded worker pool), plus jvmTests. All CI green.

**Traps hit and fixed:**
- SQLDelight `INTEGER AS kotlin.Int`/`AS kotlin.Long` on `DownloadedTrack.sq`
  generated a required `DownloadedTrackAdapter` ctor param on `DhunDatabase`,
  breaking `DatabaseFactory.create(driver)`; the `AS` maps also produced an
  `Unresolved reference 'Downloaded_track'`. Fixed by reverting to plain
  `INTEGER` (→ `Long`) + mapper conversions and importing the generated row as
  `dev.dhun.database.DownloadedTrack as DownloadedTrackRow`.
- `RepositoriesTest.schemaVersionIsTwo` was hardcoded to 2 while the schema is
  v3 — fixed to `schemaVersionIsThree`.
- `FileDownloadManagerTest` used `runTest` + `backgroundScope`, so the worker
  never advanced; also created two separate bare in-memory repos sharing no
  state. Rewrote with `runBlocking` + `Dispatchers.Unconfined` and one repo per
  test; scope is cancelled in `finally`.

**Status:** offline-first playback routing, platform download services
(FGS/WorkManager), and the download UI (button, Library "Downloads" tab, track
badging) are the remaining ADR-006 steps; hardware verification still pending.

## 2026-09-07 — PR #32 merged & rolling `test` published; new session `arena/01a07989-dhun`

**Merged:** PR #32 (`arena/01a076f3-dhun`) merged into `main` at
**`862f0ac`** on 2026-09-07T01:24:20Z. Main CI **34072908037 PASS**;
test-release **34072908097 PASS**. Rolling `test` pre-release published at
**`862f0ac`** 2026-09-07T01:29:28Z: `dhun-test.msi` **112,136,192 B**,
`dhun-test.apk` **17,516,190 B**, both with `.sha256` assets. Stable URLs
unchanged.

**New session state:** `arena/01a07989-dhun` branched from `862f0ac`;
nothing local outstanding. PR #31 (`arena/01a0759b-dhun`, docs-only, `3c63dca`)
is OPEN but **CONFLICTING** — superseded by PR #32's docs reconciliation; do
not merge without user instruction. Issue #14 (rot-drill) still OPEN — the
red is GitHub-runner IP gating on the live probe (a known environment
limitation), not a user-impact defect.

**Root cause of stale docs (why the reconcile commit here):** the previous
session's CURRENT ACTIVE TASK still claimed "PR #32 OPEN" and its "exact next
step" was to merge + publish — both now done, so the roadmap was replaced with
the verified post-merge snapshot. Also resolved a long-standing doc
contradiction: `KNOWN_LIMITATIONS.md` said "ADR-003 remains PROPOSED" while the
ADR file and `OwnClientStreamResolver.kt` both say ACCEPTED (Option C staged
wave) — reconciled to ACCEPTED. `StreamResolver.kt`'s doc comment still said
"ADR-003 is unapproved" and `shared/build.gradle.kts` still said "Schema v1" —
both stale and corrected.

**Environment:** no local JDK/Android SDK/display; CI is the compile gate.
The next step is to implement ADR-006 (persistent offline downloads) as a
code-first, jvmTest-covered, CI-verified increment; hardware/device/soak and
green live-probe gates remain OPEN and are not closable from this sandbox.

## 2026-09-07 — ADR-006 foundation + download engine (PR #33, CI green)

Session `arena/01a07989-dhun`, branch at `a1064b7`, PR #33.

**Implemented (all in `shared`, jvmTest-green):**
1. `core/DownloadedTrack.kt` — entity + `DownloadState` lifecycle enum.
2. SQLDelight `DownloadedTrack.sq` + `migrations/2.sqm` (schema **v3**).
3. `download/DownloadRepository.kt` + `SqlDelightDownloadRepository`, wired
   as `DataLayer.downloads`.
4. `download/DownloadStorage.kt` (filesystem abstraction), `StreamDownloader.kt`
   (Ktor byte-downloader: Range-resume, progress, resolving User-Agent
   isolation, `CancellationException` rethrow so pause/cancel keep the `.part`),
   `DownloadManager.kt` (queue/progress contract),
   `FileDownloadManager.kt` (bounded 3-slot pool, atomic `.part`→final commit,
   best-effort artwork, PAUSED/FAILED), jvmMain `JvmDownloadStorage`.
5. Tests: `DownloadRepositoryTest`, `StreamDownloaderTest`,
   `FileDownloadManagerTest` (+ `TestSupport` fakes).

**Two real CI failures fixed from root cause, not retried:**
- **Schema `DownloadedTrackAdapter` compile error.** Declaring integer columns
  as `INTEGER AS kotlin.Int`/`AS kotlin.Long` made SQLDelight emit a required
  `DownloadedTrackAdapter` param on the `DhunDatabase` constructor, breaking
  `DatabaseFactory.create(driver)`. Fix: drop the `AS` maps; use plain
  `INTEGER` (→ `Long`) and convert in the repository mapper, matching the
  existing `Track.sq` convention.
- **Manager tests non-deterministic / a hardcoded schema version.** The
  manager's background worker did not advance under `runTest` +
  `backgroundScope`, and `RepositoriesTest.schemaVersionIsTwo` was stale.
  Fix: drive the manager on a `Dispatchers.Unconfined` scope so workers run
  inline (assert on the deterministic repository), use `runBlocking` for the
  Ktor `StreamDownloader` tests (no virtual-time channels), and update the
  schema-version expectation to `3`.

**CI evidence (PR #33):** `build-and-test` `34075307637` PASS (shared JVM
tests incl. download tests, Android debug build, probe + Desktop compile);
`apk` PASS; `msi` in-flight. `:shared:jvmTest` push run `34075305385` PASS.

**Not yet wired:** no UI to enqueue downloads, no offline-first playback
routing (the Android `file://`/`FileDataSource` and Desktop vlcj local-path
load), no platform download services, no storage UI. These are the next steps
and are device/PC-verified only after they land (see `.ai/KNOWN_LIMITATIONS.md`).

---

## 2026-09-07 — Library Liked Songs reorganization, Mini-Player revamp, Slider Hitbox expansion, & ADR-006 Offline Downloads

Session `arena/01a076f3-dhun`:
1. **Library Liked Songs Integration:** Reorganized Liked Songs into a dedicated pinned folder card inside the Playlists tab, removing the redundant top-level Favorites tab. Tapping the Liked Songs folder displays the full collection with quick "Play all", reordering, and swipe-to-remove actions.
2. **Mini-Player UI Overhaul:** Revamped the docked Mini-Player across Windows and Android with an ambient artwork gradient wash, 2dp smoothed top progress indicator, animated circular play/pause action button, marquee track title, and expanded responsive touch/click area.
3. **Windows Player Slider Hitbox Expansion:** Expanded `DhunSeekBar` interaction hitbox to 48dp (`DhunSpacing.touchTarget`), enabling seamless mouse clicks and horizontal drags anywhere across the slider area on Windows and Android without requiring pinpoint center alignment.
4. **Offline Music Downloads Architecture (ADR-006):** Researched open-source audio download implementations (ViMusic, InnerTune, Metrolist, SimpMusic) and created ADR-006 defining the SQLDelight schema, resumable chunked downloader, atomic promotion, metadata tagging, offline-first playback interceptor, and storage management.

---

## 2026-09-06 — PR #32 test suite expansion: LyricsRepository & cache persistence CI PASS

Commit `52c6aba` on `arena/01a076f3-dhun`:
- Added `LyricsRepositoryTest.kt` (6 unit tests covering cache hits, YTM-first resolution, LRCLIB fallback with MockEngine, NotAvailable negative-cache prevention, cache read exception tolerance, and cache clear/inspection helpers).
- Added `RepositoriesTest.kt` coverage for `SqlDelightLyricsCacheRepository` (round-trip of Synced/Unsynced lyrics, NotAvailable non-caching, observe flow, and clear).
- CI results on PR #32:
  - Code CI run **34037665009** PASS (job 101498587867: Python checks, PowerShell syntax, shared JVM domain tests including new lyrics tests, Android debug build, probe and Desktop compilation).
  - Native packaging run **34037665019** PASS:
    - MSI build job 101498588247 produced MSI **1.40.1** (112,091,136 B, SHA256 `332f6dec0ea91821aecf10afa451c80a14bf370d7779b18fb7acab1445ff216a`).
    - Hosted Windows upgrade smoke verified **1.36.1 → 1.40.1**, preserving userdata and cache sentinels.
    - Future upgrade-removal guard and explicit uninstall checks passed.
    - APK build job 101498588114 passed (17,499,806 B, SHA256 `1b256c5a42091921206e68afd63ab8d7768431ca121bd1f292ac989d1e910c86`).

Hardware/product gates remain open awaiting user device re-tests.

---

## 2026-09-06 — Session arena/01a076f3-dhun initialized; PR #32 CI & MSI verification PASS

Session branch `arena/01a076f3-dhun` established from `main@76c68eb`. Working
PR #32 opened to track session development and CI verification.
- Code CI run **34037019387** PASS (job 101496835724: Python tests, PowerShell
  syntax, shared JVM domain tests, Android debug build, probe and Desktop
  compilation).
- Packaging run **34037019382** PASS:
  - MSI build job 101496835501 built MSI **1.38.1**, 112,091,136 B, SHA256
    `324f7ece174bbb47dd70475675b881a90aea248320f04ab080200038cea8bf9b`.
  - Hosted Windows upgrade smoke verified **1.36.1 → 1.38.1** (baseline SHA256
    `164decc74292cb5bb58fa272570d63dbff1c6c34502db7b24c5e8bd3e5ed7008`),
    preserving userdata and cache sentinels.
  - Future upgrade-removal guard PASS (sentinels preserved under
    `UPGRADINGPRODUCTCODE`).
  - Reinstall + explicit uninstall PASS (userdata removed).
  - APK build job 101496835642 PASS: 17,499,806 B, SHA256
    `1b256c5a42091921206e68afd63ab8d7768431ca121bd1f292ac989d1e910c86`.
- Artifacts: MSI `9990543402`, MSI diagnostic `9990542277`, APK `9990511086`.

All CI and synthetic packaging checks green. Hardware gates (real audio
streaming, live Home pagination, visual acceptance, tray/SMTC, soaks)
remain OPEN pending user device verification.

---

## 2026-09-06 — PR #30 merged and repair-code test release verified

User-requested merge completed at **11:52:26Z**, PR #30 →
`76c68eb2b27da5341d146bda3d5aa6ea298d954a`; session branch preserved.
Main CI **34031477321 PASS**, packaging/publishing **34031477327 PASS**.
`test@76c68eb` published **11:58:17Z**: MSI **1.36.1**, 112,091,136 B,
SHA256 `164decc74292cb5bb58fa272570d63dbff1c6c34502db7b24c5e8bd3e5ed7008`;
APK 17,499,806 B, SHA256
`1b256c5a42091921206e68afd63ab8d7768431ca121bd1f292ac989d1e910c86`.
Checksum assets uploaded; producer notices and release/tag/asset APIs agree.
Main's native smoke preserved sentinels from 1.0.5 → 1.36.1 and during
upgrade-flag removal, then removed them on explicit uninstall after reinstall.
No app launch/audio/GUI test was performed. The publish job had an action
Node-20 deprecation warning for download-artifact v4; no full zero-warning
claim. The post-merge documentation checkpoint records this code-release
snapshot; later rolling builds may advance asset identities. Hardware and
v0.1.0 remain open; no unrelated finalization action or branch deletion.

---

## 2026-09-06 — MSI data-safety correction passes real Windows checks

PR #30 at `b6d47bd`: code CI 34030730736 and branch CI 34030728903 PASS.
Packaging run **34030730743** PASS, MSI 1.34.1, 112,091,136 B,
SHA256 `1a4d2fe5c9b0c8949995cbd62e5e01675a9fb8081d71eb3da74b5a834dde021c`.
Check annotations confirm **1.0.5 → 1.34.1** preserves both userdata/cache
sentinels, explicit UPGRADINGPRODUCTCODE removal preserves both, and
reinstall/ordinary uninstall removes test userdata. MSI artifact 9988585984;
diagnostic artifact 9988584693. PR publishing skipped. The failed unsafe
1.33.1 MSI was never exposed as a downloadable candidate or merged.

The fix applies only to unsigned packages before hashing/signing and uses
DHUN-owned MSI operations; no OpenJDK template was copied. Legacy preparation
only changes matching DHUN HKCU cleanup values, never moves/deletes userdata;
future versions use the session-property guard. A later cancelled legacy
transaction can require retry to restore normal cleanup. Native passing
sentinels do not prove app launch, user library integrity, audio, visuals,
SMTC/tray or soaks. PR can proceed to merge after final evidence checks;
no stable v0.1.0 is earned.

---

## 2026-09-06 — Real MSI upgrade deletes userdata; PR held before merge

PR #30 run **34029598179** built MSI **1.33.1**, 112,075,216 B,
SHA256 `57aaf0a53cf17319dc832398adbb4ab485e9e29f86dbf9633ca540c5eb5af576`,
UpgradeCode `31ddb86b-9666-4071-b11c-45f16fa4682d`. The install-over test then
failed **“In-place MSI upgrade removed/changed existing userdata.”** The MSI
artifact was not uploaded; publishing skipped. APK built; code CI 34029598196
passed. The PR must not merge on compilation alone.

Source review of OpenJDK jpackage's `WixAppImageFragmentBuilder` and
`resources/main.wxs` confirms a version-specific `HKCU\Software\DHUN\DHUN\<version>`
recursive-cleaner registration and early RemoveExistingProducts. The old MSI
removes the whole install tree, including userdata, during an upgrade.

Correction under native test: finalize the unsigned MSI with an upgrade-only
cleaner-property guard, preserving normal explicit-uninstall cleanup. An
upgrade-only built-in PowerShell action suppresses only matching legacy
DHUN HKCU cleanup values before old-product removal; already-safe packages
carry a marker and use the session-property guard instead. It never moves
or deletes userdata, uses no execution-policy bypass, and aborts preparation
on unexpected registrations. RemoveExistingProducts is placed in the supported
post-InstallValidate/pre-InstallInitialize slot so directory properties exist.
The bridge restores its edits if preparation itself fails; a later cancelled
legacy upgrade can leave the old cleanup registration suppressed (data-safe,
but cleanup may need a successful retry). Backups remain recommended.

The native smoke now also uninstalls the candidate with UPGRADINGPRODUCTCODE
set, verifies both sentinels survive, reinstalls, then explicitly uninstalls
and requires userdata removal. This does not test playback or GUI. No source
from OpenJDK was copied; these are DHUN-owned MSI table/PowerShell operations.
All native outcomes are still pending; Python helper tests: 21 pass.

---

## 2026-09-06 — User authorises PR/merge; require native package checks before merge

Latest instruction: “Ok complete this work then PR and merge.” The current
branch is clean and CI-green through `455743b`, but the native packaging
script has only been parsed so far. Add packaging/install-over as ordinary
pull_request checks on the existing workflow, using the same MSI version
counter and a publish guard that rejects PR refs. No manual dispatch retry,
credential change, extra working branch or stable release is involved.
The PR will be merged only after real checks; user playback/visual/soak
acceptance still cannot be inferred from CI.

---

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

## 2026-09-06 · Android User-Agent collision & Desktop seamless pre-buffering

**Android 403 & multi-item queue root cause:**
In `PlaybackGraph.kt`, a single `AtomicReference<String?> userAgentForNextOpen`
was shared across the entire `ResolvingDataSource`. When Media3 / ExoPlayer
pre-buffered upcoming items in the queue, `userAgentForNextOpen` was overwritten
with the upcoming track's User-Agent. Mid-stream chunk reads for the currently
playing track then opened HTTP connections with the wrong User-Agent, causing
Google Video to reject the signed stream URL with HTTP 403.
**Fix:** Refactored `PlaybackGraph.kt` to map User-Agents per `videoId` via
`ConcurrentHashMap<String, String>`, ensuring complete stream isolation. Also
fixed `TransferListener` registration in `UserAgentDataSource`.

**Extraction latency fix (ADR-003 Option C):**
Refactored `OwnClientStreamResolver.kt` from serial identity evaluation to
staged concurrent waves (Wave 1: web_embedded + visionos, Wave 2: TV cluster,
Wave 3: mweb + web_remix). Identical tokenless identities, but racing within
waves drops initial track resolution latency from 20-40s to 300-800ms.

**Desktop transition & pre-buffering (ADR-005):**
Added temporary pre-buffering (`.temp` files) and promotion lifecycle to
`AudioFileCache.kt`. Updated `DesktopDhunPlayer.kt` to immediately silence/stop
previous playback on track switch, schedule pre-buffering of the upcoming track
only after current track is playing, and promote pre-buffered files to permanent
cache for instant 0ms track transitions. Purged unplayed temp files on queue jumps.

**Queue cursor invariant:**
Fixed `QueueManager.setQueue` setting `currentIndexInItems` before `rebuildOrder()`
and added `peekNext()` helper.

## 2026-09-16 · Full Player / Related-sheet transition moved only the artwork (session `arena/01a0a7f3-dhun`, follow-up to PR #66)

**Symptom (device report, Android; Desktop reproduced it):** tapping the queue
glyph opened the Queue/Related panel while the *only* thing that moved was the
cover. Title, artist, timeline, prev/play/next and the desktop volume slider
stayed parked, the panel rose into the space above them, and opening looked like
a glass wash fading in over a stationary player. Closing looked fine, which is
how the asymmetry got shipped.

**Root cause, three layers, all inside `shared/src/commonMain/…/ui/player/FullPlayer.kt`:**
1. PR #66 translated the player Box by `playerOffsetY` and then handed
   `PlayerControlCluster` the exact inverse (`translationY = -playerOffsetY`) to
   "keep the chrome reachable as a footer". Two offsets on one subtree = the
   chrome is stationary by construction.
2. The sheet was placed at `padding(bottom = chromeHeight)` with
   `height = 0.68 · (available − chromeHeight)`. Its top therefore met neither
   the player's lower boundary nor the bottom edge — the "gap" was structural,
   not an animation defect.
3. `alpha = motion.progress` on the sheet made the first ~40% of the flight a
   fade rather than a rise, and the travel was re-derived from live geometry on
   every frame, so anything that re-measured mid-motion (chrome, window resize)
   moved the target underneath a panel that had already been mounted.

**Fix:** one progress, one travel, both halves derived from it. `relatedSheetTravel`
(dp, from safe-area height + `chromeHeightDp`-corrected measured chrome) is the
sheet's height *and* the travel; `relatedSheetMotion(progress, travelPx)` yields
`playerOffsetY = -travel·progress` and `sheetOffsetY = travel·(1-progress)`, so
`player bottom == sheet top` at every frame with the panel flush to the bottom of
the safe area. `relatedSheetTransition` owns the mount window (target opened →
progress lands on 0) and the mirrored `actionRowVisible`, so the queue / shuffle /
repeat / lyrics strip is faded out — space preserved, so the chrome measure cannot
change mid-motion — and returns exactly once. `rememberFrozenSheetTravel` captures
the travel while at rest and holds it for the flight, including reversals. Half
the rise is absorbed by the weighted artwork field (`relatedSheetLayoutRiseDp`) so
the cover re-fits into a thumbnail rather than being cropped, and the header's
swipe-down collapse stays inside the touch area. Fade removed; the panel slides,
opaque, clipped by the player's own `clipToBounds`. No `AnimatedVisibility` enter
on the sheet to fight the shared transition. Shared commonMain only — `Main.kt`
still passes `isDesktop = true`, and there is no platform fork.

**Environment trap (unchanged, still true):** this sandbox has no JDK and Maven
/ Gradle / dl.google.com are egress-blocked (`curl` to them returns `000`), so
`./gradlew :shared:jvmTest`, `:app-android:assembleDebug` and
`:app-desktop:compileKotlinJvm` cannot run locally; CI is the compile gate. The
Python gate is runnable: `python3 -m unittest discover -s scripts -p 'test_*.py'`
→ 24 OK.

**Verification state:** the first CI run (`35048954505` on `c0b27fc`) was red on
`:shared:compileTestKotlinJvm` only, and only for a test-side Kotlin detail:
`songCount * DhunSpacing.xs` — `Int.times(Dp)` is a top-level extension this
codebase does not import, where `Dp.times(Int)` is the member form the app uses
(`DhunSpacing.glassBlur * 4`). `commonMain` compiled as-is; the assertion now
reads `DhunSpacing.xs * songCount`. Re-run covers shared `jvmTest`, Android
`assembleDebug`, Desktop `compileKotlinJvm` and the probe compile: **green on `44d8a32`
(run `35049622474`, 5m17s)** — `:shared:jvmTest` runs all 24 `PlayerSheetLayoutTest`
cases on a real Kotlin/JVM + Compose build, `:app-android:assembleDebug` builds the APK
off the same `commonMain`, and the Desktop module compiles against it.
`python3 -m unittest discover -s scripts -p 'test_*.py'` = 24 OK locally.
`rot-drill` 0-job no-trigger noise is not a gate. **Open, and not inferable from
CI:** on-device / on-Windows eyeball of the animation curve, a rapid
open↔close mash on a 60Hz and a 120Hz panel, and a dragged Desktop window mid
transition. The seam, reversal, freeze, visibility-window and
restored-layout contracts are pinned by `PlayerSheetLayoutTest` instead.

## 2026-09-21 — Endless radio: implementation + 6 regression tests (session `arena/01a0c3b7-dhun`)

**Task:** the spec queued in this log under "Queued (not started): endless
radio" — while a radio plays and ≤3 songs remain, auto-queue the station's
next `/next` page behind the current track; same song, same position,
seamless, no gap; tail replaced on refill; supersedes the #99 "different
song" seed semantic; related row still excludes the current track.
Implemented, tested, and merged under the session's standing
merge-without-asking directive (PR #109, head `063040d`).

**What shipped:**
- `RadioQueuePage` entity + `Parsers.radioQueuePage` reading
  `nextRadioContinuationData` (carries the track list AND the next
  continuation token; token = `nextRadioContinuationData`) — pinned by
  `ParserFixtureTest` on a real wire fixture.
- `MusicProvider.radioQueuePage(videoId)` / `radioQueueContinuation(token)`
  (interface defaults keep every fake honest) → `InnerTubeClient` posts the
  `/next` endpoint; continuation goes as URL parameters `ctoken` +
  `continuation` in the POST's 3rd argument (NOT the body) — the exact
  contract `relatedTracks` already uses, so seed and continuation share
  one endpoint path.
- `RadioSession` (commonMain, Koin single, in-memory): per-station
  continuation token + page-duplicate signature (track-id list). A fetched
  page whose tracks are already queued is NOT swapped in (loop guard);
  `tokenConsumed` on a null next-token marks the chain exhausted → re-seed
  on the next trigger.
- `PlayerViewModel` refill monitor: `combine(queue, index, state)` → when
  Playing on a radio-started queue with ≤3 songs remaining from the
  current track, `maybeRefillRadio()` — continuation while a token exists,
  else re-seed `/next` from the CURRENT track (head filtered out of the
  page → no self-replay). Success → `replaceQueueKeepingCurrent(tail)`.
  Failure → queue untouched, token kept (next advance retries the same
  chain). Non-radio queues (user-built lists, search results) are ignored.
- `DhunPlayer.replaceQueueKeepingCurrent` (interface default = plain
  `prepareQueue`; Android engine override = Media3 `removeMediaItems`
  around the sounding item + `addMediaItems` behind it — the sounding
  MediaItem is never re-prepared; desktop uses the default path).
- UI wiring unchanged in look: the refill is silent; artist/track radio
  start seeds through the new session.

**CI-as-compiler, third consecutive session — and a new diagnostic route:**
sandbox still has no JDK and log/blob downloads still EOF. The working
readout is the **check-run annotations API**: `gh api
repos/…/commits/{sha}/check-runs` → `/check-runs/{id}/annotations` returns
the real compiler/test errors with file + line. It caught all three red
runs on this PR: (1) Kotlin smart-cast of a `var` property in a test Fake
(`val local = theVar` before the `when`); (2) a K2 parser cascade on
`args![0]` index expressions inside when-branches of a reflective
`Proxy`-based Player double — rewritten around a null-safe `arg(args, i)`
helper with explicit when blocks; (3) the 3-arg `assertEquals(a, b, msg)`
form does NOT resolve in this toolchain's Android test compile — repo
convention stands: 2-arg `assertEquals` and `assertTrue("msg", cond)`
(message FIRST). Test-design slips the suite caught: a 3-track fake refill
page left the queue exactly at the ≤3 threshold and re-triggered the
monitor (size fake pages past the threshold — real pages are ~25), and the
duplicate guard legitimately probes once more after a small page lands
(assert the guard, don't forbid it). One documented flake:
`LibraryViewModelTest` 15s timeout under CI load (in-memory driver +
polling; test untouched — repo's own comment flags the 5s→15s history).

**Sandbox recreation incident (mid-session):** the GitHub token expired
(user reconnected; the 2-minute unblock worked) and a sandbox
recreation reset the LOCAL branch ref to the session base `6317a1b`
while the remote kept the four pushed commits. Reconciled exactly per
the previous session's playbook: verified the working tree byte-identical
to the remote head for all feature files (the 20-file mishap commit
`1d26ce5` differed from the remote by exactly the 7 new files),
`git reset --hard` to the remote head, `git checkout 1d26ce5 -- <7 files>`
back, re-committed as two clean commits (`4cd3e41` code, `2c54a2a` docs)
— no force-push, no history rewrite.

**The premature-refill race (found after the compile fixes, by the test
suite itself):** the first two red runs after the compile fixes failed in
different endless-radio tests with the NEW diagnostic assertions naming
the state — the fixture's session token was `null` right after
`startRadio`, and the non-radio test saw a continuation call "that cannot
happen". Root cause: `startRadio`/`playRelatedAt` marked the station
ACTIVE (radioSession.start) BEFORE the async queue swap landed — the
station was active over the OLD one-song queue (remaining = 0, at the
threshold), so a monitor sample in that window fired a premature refill:
in production it would fetch and consume the station's first /next page
early; in the tests the fake's empty-chain fallback returned an empty
page whose null token went through `tokenConsumed(null)` — nulling the
chain, so the next refill re-seeded instead of continuing (exactly the
`[r3, a, r1, r2, r4, r5]` swap the fail-open test caught on `063040d`).
Fixed in three waves as the suite kept exposing the next layer: (1) the
session starts only AFTER the swap (both entry points); (2)
`radioRefillInFlight` is claimed BEFORE the launch (single-flight across
the launch gap), `lastRelatedPageToken`/`radioRefillInFlight` are
`@Volatile` (cross-thread), and a post-fetch re-check stops a late page
from clobbering a queue the user replaced mid round-trip; (3) probe
CONFLATION — a natural advance emits index AND state, so the combine
delivered two probes for one refill-relevant situation, and the second
fetched again once the first refill finished (the duplicate-page test's
"expected 1 but was 2"): the probe now carries a playing flag (not the
state object) + `distinctUntilChanged`, `lastRefillProbe` stops a
no-change re-check from spinning, and the refill's `finally` re-gates on
the latest snapshot so probes skipped while a fetch was in flight (rapid
advance sequences) still get their refill.

**Verification:** CI green on the final head across all four workflows
(CI / Build APK / test-release / publish chain) — see ROADMAP top block.
Open, user-gated: re-download of the rolling `test` build carrying endless
radio + an itemized hardware soak (leave a station running 30 min, watch
for a gap at the first refill; related row still excludes the current
track).
