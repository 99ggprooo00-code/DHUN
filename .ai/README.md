# .ai/ — agent operating files

Everything an AI agent (or a returning human) needs to operate this repo
that the app build itself does not need. Product code lives in
`shared/`, `app-android/`, `app-desktop/`, `tools/`; user-facing product
documentation lives in `docs/` and the root `README.md`.

These files are deliberately OUTSIDE the main project tree so the product
stays clean while the agent's instructions, plan state and incident
history persist across sessions (user directive, 2026-09-05). If a
separate `ai`-named git branch is ever wanted for them, split this
directory — the content is branch-ready as-is.

## Boot protocol (do this BEFORE touching code)

1. `.ai/ROADMAP.md` — the **CURRENT ACTIVE TASK** section at the very top:
   which file, last error, exact next step.
2. `.ai/DEBUG_LOG.md` — open incidents, root causes, environment traps
   (CI mechanics, sandbox limits). Do not re-diagnose something logged here.
3. `git log --oneline -15` + `git status` — what actually landed since the
   docs were last written. Docs lag code; code + CI is the truth.
4. `.ai/MASTER_PROMPT.md` — the 14-phase plan, the locked stack, and the
   AI behavior rules. `.ai/PROMPT_SEQUENCE.md` — the original 30-phase
   prompt audit (where "Phase 15–30" ambitions come from).

## Permanent maintenance contract

- **Every phase / every session:** update `.ai/ROADMAP.md` (CURRENT ACTIVE
  TASK at the top; mark EXACTLY which steps are done) and
  `.ai/KNOWN_LIMITATIONS.md` (honest > complete).
- **Done = pushed + CI green + (where specified) on-hardware verified.**
  Unpushed or CI-unverified work is marked NOT done.
- **Incidents:** every significant crash / CI red / environment trap gets
  an entry in `.ai/DEBUG_LOG.md` (symptom with stack → root cause → fix →
  verification state).
- **Recurring maintenance (the Doctrine):** stream extraction is a
  maintenance problem. The daily rot-drill (`tools/playback-probe`) watches
  YouTube breakage; on red, pin last-good / adopt upstream patch / ship a
  patch release ≤72h. See `.ai/RISK_REGISTER.md` for the pre-agreed
  responses. Do not hand-roll extraction; do not "fix" YouTube breakage by
  silently changing the locked stack — write an ADR in `docs/decisions/`
  and get the user's OK.
- **Repo sanitization:** no secrets, no device identifiers, no captured
  credentials or personal data anywhere in the repo; test fixtures are
  sanitized JSON; every dependency (name, license, version) is listed in
  root `THIRD_PARTY.md`; build output never committed.
- **Commit style:** small, meaningful commits; a phase ends with its
  verification log in `docs/verification/NN-*.md`.
