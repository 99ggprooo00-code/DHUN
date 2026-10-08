# AUTONOMY_LOG — session `arena/967513fd-dhun` (2026-10-09)

One line per checkpoint. `T0` is the first command of the session.

| Checkpoint | Landed | In flight | Next |
|---|---|---|---|
| T+0:00 | boot: 272 python tests (1 failure), main CI red at 37831998519, Pages `workflow`/`built` | — | fix the red test, then build `app-web/` |
| T+0:30 | runbook fix committed (272 green), PR #145 open, PR #144 retitled SUPERSEDED | CI burning | `app-web/` module, tokens first |
| T+1:30 | `app-web/`: tokens, icons generated from Kotlin, shell + 3 screens + settings, both players, queue, LRC, EQ; 60 node tests green | python contract test | boot smoke test against a DOM stub |
| T+2:00 | 23 Python contract assertions in `scripts/test_app_web.py` (295 green); dev server up on 0.0.0.0:4173 | CI | website improvement: the annotation-carry bug |
| T+2:30 | `website/tests/annotation-report.mjs` + 8 tests; `browser.mjs` packs to a budget and mirrors the report to the log and `$GITHUB_STEP_SUMMARY` | CI | ADR-008 amendment, verification record 29, `.ai/` docs |
| T+3:00 | ADR-008 B2/B3 amendment, `docs/verification/29-web-app-mirror.md`, ROADMAP + KNOWN_LIMITATIONS + this prompt updated | CI final read | merge |

Notes kept for the next session:

- `app-web` is **not deployed**: the marketing workflow owns the single Pages
  artifact and a second workflow publishing to the same environment would race
  it. Deployment is the first open item.
- No audio plays and no live catalogue is reachable; both are labelled on
  screen and asserted by tests.
