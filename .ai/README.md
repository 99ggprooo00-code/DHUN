# .ai/ — agent operating files

Everything an AI agent (or a returning human) needs to operate this repo
that the app build itself does not need. Product code lives in
`shared/`, `app-android/`, `app-desktop/`, `tools/`; user-facing product
documentation lives in `docs/` and the root `README.md`.

These operating files are deliberately separate from application modules so the product
stays clean while the agent's instructions, plan state and incident
history persist across sessions (user directive, 2026-09-05). If a
separate `ai`-named git branch is ever wanted for them, split this
directory — the content is branch-ready as-is.

## Boot protocol (before edits)

1. Read the user's current scope/permissions and [PRD §1](../docs/PRD.md#1-product-definition-and-source-of-truth), the single authoritative multi-source vision/status vocabulary.
2. Inspect `git status`, branch, current source/build/workflows; do not switch the session branch or assume old CI/hardware results are current.
3. Read `.ai/ROADMAP.md` **CURRENT ACTIVE TASK**, this master prompt, current provider matrix and latest accepted ADR amendments. Native YouTube Music and browser JioSaavn differ; Spotify is planned/deferred, not supported.
4. Read relevant DEBUG_LOG/KNOWN_LIMITATIONS and verification records with their dates. PROMPT_SEQUENCE, old handoffs and archived autonomy prompts are historical, not orders.

## Current binding UI/navigation correction (2026-10-09)

Before acting on old session notes, read the current active task at the top of
`ROADMAP.md`, the binding correction in `MASTER_PROMPT.md`, and the authoritative
[DHUN glass specification](../docs/design/glass-system.md). Old handoff
restrictions, visual models, and branch/commit snapshots are historical unless
the current active task explicitly adopts them. The native KMP app and the `app-web/`
browser client are one product with one design system; the static `website/` is
that product's front door and renders the same shell and tokens (ADR-008
amendment 2026-10-16). Current browser requests go to unofficial JioSaavn wrappers/CDN, not YouTube.
Live catalogue and audible deployed-origin playback remain unverified.

Destructive operations must expose pending/success/failure and must not dismiss
confirmation or clear selection before success. GitHub Pages internal links must
be generated for the `/DHUN/` project base; root-hosted local browser tests are
not proof of deployed-path correctness. Keep source, tests, CI, hardware, and
canonical-origin verification states separate.

## Permanent maintenance contract

- **Every phase / every session:** update `.ai/ROADMAP.md` (CURRENT ACTIVE
  TASK at the top; mark EXACTLY which steps are done) and
  `.ai/KNOWN_LIMITATIONS.md` (honest > complete).
- **Done is scope- and evidence-specific.** Report edited/tested/untested separately. Do not claim pushed, CI-green, hardware or deployed-origin results without actually obtaining them. Commit/push/merge/release only with current authorisation.
- **Before every push / PR merge:** run the pre-push ritual at the top of
  `.ai/ROADMAP.md` — verify on GitHub (not locally) what is pushed and
  green, rewrite CURRENT ACTIVE TASK (file · last error · exact next
  step), mark steps from that evidence, commit everything and push
  (including any stranded unpushed commits), then re-check CI and fix
  the marks in a follow-up commit if they changed.
- **Incidents:** every significant crash / CI red / environment trap gets
  an entry in `.ai/DEBUG_LOG.md` (symptom with stack → root cause → fix →
  verification state).
- **Recurring maintenance (the Doctrine):** stream extraction is a
  maintenance problem. The daily rot-drill (`tools/playback-probe`) watches
  YouTube breakage; on red, pin last-good / adopt upstream patch / ship a
  patch release ≤72h. See `.ai/RISK_REGISTER.md` for the pre-agreed
  responses. The existing own-client resolver is deliberate (ADR-001); do not expand extraction features or "fix" YouTube breakage by
  silently changing the locked stack — write an ADR in `docs/decisions/`
  and get the user's OK.
- **Repo sanitization:** no secrets, no device identifiers, no captured
  credentials or personal data anywhere in the repo; test fixtures are
  sanitized JSON; every dependency (name, license, version) is listed in
  root `THIRD_PARTY.md`; follow existing generated-output conventions (`website/dist` and legal/token/icon bundles are deliberate tracked exceptions).
- **Commit style:** small, meaningful commits; a phase ends with its
  verification log in `docs/verification/NN-*.md`.
