# ADR-008: Reopen Web as a Browser-Player Target Through a Feasibility Gate

## Status

**ACCEPTED FOR B1 FEASIBILITY ONLY — 2026-10-08.** The user first selected
“play music inside the website” at W0, then separately approved the safe plan to
run one small browser test before any full player work.

Acceptance authorizes only the B1 deployed-browser feasibility spike below. It
does **not** authorize a production web player, browser stack adoption, hosted
backend/proxy, extraction change, or public Web-support claim. This ADR is
numbered 008 because ADR-007 is already reserved by the unmerged
extraction-contingency research on PR #54.

## Context

The project contract before this ADR deliberately excluded Web:

- MASTER_PROMPT §1 says Web is deferred and likely “no”;
- §3 says `Web: cut. No shims, no stubs, no dead code "for later."`;
- §4 locks Kotlin Multiplatform to Android + JVM and explicitly rejects a
  separate backend and Compose for Web / Kotlin-JS;
- §7 keeps Web/PWA outside stages S1–S6.

The repository matches that contract. `shared` has only `androidTarget()` and
`jvm()`. There is no browser source set, browser playback engine, JavaScript or
Wasm build, web application module, backend, or browser CI gate. Android uses
Media3; Desktop uses libVLC. Neither playback implementation can run in a web
browser.

The current GitHub Pages deployment is static. It can host HTML, CSS,
JavaScript/Wasm and static assets, but it cannot itself act as a server-side
stream proxy. A browser player must also satisfy browser-origin, CORS, media
range-request, codec, autoplay and background-lifecycle constraints. The fact
that the Android/JVM clients can resolve and play a URL does not prove a browser
origin can fetch or play it.

This is therefore an architectural reversal, not an extra page in the proposed
marketing site.

## Decision

Reopen Web only as a **separately gated browser-player feasibility track**.
This ADR supersedes the Web cut just enough to permit the isolated B1 spike
below. It does **not** approve a production web player or select Kotlin/Wasm,
Kotlin/JS, TypeScript, a server backend, or any extraction change.

The existing Android/Desktop application, ADR-001–006, S3/S6 gates and rolling
release remain unchanged. Web experiments must not redden application CI,
change production extraction/probe semantics, or delay user-only hardware
evidence.

## Non-negotiable boundaries

1. **B1 is the entire authorized implementation scope.** Do not add a product
   app module, shared browser source set, adopted framework, backend, or full
   player. Use only what the disposable deployed-origin test needs.
2. **No credentials or attestation expansion.** Do not add sign-in, cookies,
   visitor credentials, PO tokens, BotGuard or secrets to browser code. Do not
   implement ADR-007 through this work.
3. **No unapproved proxy.** A hosted stream/API proxy would create a new service
   with operating cost, abuse, privacy, security and legal responsibilities. It
   requires another explicit architecture decision; it is not a fallback an
   implementer may silently add.
4. **No claim before proof.** Until B1 proves browser playback from the deployed
   origin, public copy must not say “web player,” “open in browser,” or imply Web
   support.
5. **Static site and player are separate concerns.** The research in
   `.ai/WEBSITE_PLAN.md` remains useful for content, legal assets and status
   wording, but its Option-A three-route Astro recommendation does not choose
   the player architecture.
6. **Keep gates separate.** A successful browser spike cannot close Android or
   Windows S3/S6 acceptance, and a static Pages deployment cannot count as
   browser playback evidence.
7. **GPL-3.0 remains non-negotiable.** New dependencies and copied/reference
   code require licence review before adoption.

## Staged decision path

### B0 — ADR acceptance (COMPLETE)

The user explicitly accepted the small-browser-test plan on 2026-10-08.
Acceptance authorizes B1 only. The static Option-A site remains the fallback if
B1 fails or later browser architecture is rejected.

### B1 — Deployed browser feasibility spike

Build the smallest disposable test surface, isolated from production modules,
to answer these questions from the actual
`https://99ggprooo00-code.github.io/DHUN/` origin:

1. Can browser code fetch the minimum anonymous metadata needed for one known
   public test item without cookies, credentials, tokens or a proxy?
2. Can it obtain a playable media response without modifying the production
   resolver?
3. Can the browser media element fetch the stream under real CORS, redirect and
   byte-range behaviour and produce audible playback?
4. Which codecs work in the target browsers, and what is the failure mode when
   one is unsupported?
5. Does the proof work in at least current Chromium and Firefox, with Safari
   recorded as tested or explicitly unsupported?
6. Does Pages deployment preserve the repository `/DHUN/` base path with no
   cross-origin secrets or unexpected requests?

The spike must use project-owned/CC0 test presentation content and record its
network trace, browser versions, deployed commit and result. A local dev-server
pass is insufficient. Failure is a valid result and must not trigger extraction
changes.

#### B1 implementation status — deployed, run, and stopped as BLOCKED

PR #136 merged with explicit user authorization at 2026-10-08T13:19:49Z as
`2a20024d4b626b3f40ae95776cefb6b1e49cfdca`, so legacy Pages now publishes the
dependency-free probe at the canonical origin
`https://99ggprooo00-code.github.io/DHUN/web-spike/`. It is still deliberately
unlinked, `noindex`, credential-free, allow-listed and labelled as an
engineering probe, and is fixed at revision `b1-v1`.

The canonical manual run is recorded in
`docs/verification/19-browser-feasibility-spike.md`. In the one browser that was
available — **Brave 1.96.61 (Chromium 154.0.8037.98, Official Build, 64-bit)**
— the UI rendered and anonymous metadata passed, but the player request was
blocked before a readable response. Direct-URL, byte-range, codec/media,
`playing`-event and audible-playback stages were never reached. No sanitized
JSON, screenshot or network trace was supplied, so the failure is **not
narrowed** to a specific HTTP response, a preflight rejection, extension/Shield
behavior or a network policy. Firefox and Safari were unavailable, not inferred
failures.

**Recorded outcome: B1 BLOCKED for the available Brave/Chromium-family run;
cross-browser coverage unavailable; no Web-support claim.** Brave is
Chromium-family rather than stock Chrome/Chromium, so it cannot satisfy B1's
current-Chromium-and-Firefox requirement on its own. B1 ends
here. Nothing in this outcome authorizes a proxy/backend, an extraction or
client-profile change, an adopted browser stack, or the static Option-A
fallback; the next step is a separate B2 user decision.

### B2 — Architecture selection after evidence

Choose one only after B1. B1 has now reported **BLOCKED** from the canonical
origin in the only available browser (Brave/Chromium family), with Firefox and
Safari unavailable. That evidence is thinner than a cross-browser verdict, so
B2 must be decided explicitly by the user and may legitimately be “stop”. This
ADR does not preselect any B2 option, and a decision to continue would not by
itself authorize B2.3's service.

| Option | Shape | Benefit | Blocking risk |
|---|---|---|---|
| **B2.1 — Kotlin browser target** | Add Kotlin/Wasm or Kotlin/JS plus browser platform implementations | Potential domain/UI reuse | Reopens a stack explicitly rejected by MASTER_PROMPT; CIO, Android/JVM SQLDelight drivers, Media3, libVLC, filesystem downloads and native integrations do not transfer automatically |
| **B2.2 — Separate TypeScript client** | Browser-specific UI/player consuming only a deliberately small shared protocol/model contract | Uses the browser ecosystem directly and isolates platform code | Duplicates presentation/domain behavior; same upstream CORS/playback constraints; new dependency/toolchain surface |
| **B2.3 — Frontend plus hosted backend/proxy** | Browser UI calls a new DHUN-operated service | Could avoid some browser cross-origin limits | New service operations, abuse surface, bandwidth cost, privacy/security/legal burden; forbidden without a second accepted ADR |
| **B2.4 — Stop Web playback** | Keep only a static information/download site or no site | Preserves accepted architecture and avoids a false support claim | Does not deliver the selected browser-player goal |

B1 does not preselect B2.1 merely because the existing application uses Kotlin,
and it does not preselect B2.2 merely because Node is available locally.

### B3 — Product plan

Only after a B2 architecture decision: define player scope, source sharing,
playback lifecycle, persistence, accessibility, browser support, tests,
deployment and rollback. “Port the app” is not an acceptable plan. Native-only
features must be explicitly omitted, redesigned or replaced.

### B4 — Acceptance

A production browser target would need, at minimum, deployed-origin playback,
search-to-play, pause/seek/next, queue state, responsive and keyboard behavior,
supported-browser evidence, upstream-failure messaging, privacy/network audit,
licence review and user sign-off. These are Web gates only.

## Alternatives considered now

### Keep Option A: static marketing/download site

This remains the lowest-risk alternative and is compatible with ADR-001–006.
It can honestly advertise the Android and Windows test builds without proving
browser playback. The user did not select it at W0, so it is retained only as a
fallback.

### Start a complete web port immediately

Rejected. There is no evidence that the current anonymous stream path is
browser-playable, and no accepted browser stack. Starting with UI would risk a
polished shell whose core mission cannot run.

### Add a proxy immediately

Rejected. It would silently reverse the no-backend decision and create a new
public service before proving that one is required or acceptable.

### Treat a static audio mockup as the web player

Rejected. A simulated player is valid only as a clearly labelled design asset;
it cannot be presented as functional product evidence.

## Consequences

- **Positive:** The user's browser-player direction gets a measurable first
  step instead of an unbounded port.
- **Positive:** A failed B1 can stop the work cheaply and honestly.
- **Positive:** Android/Desktop release gates and extraction doctrine remain
  protected.
- **Negative:** Web remains unavailable until multiple new gates close.
- **Negative:** A successful proof still leads to a second architecture choice;
  it is not production approval.
- **Risk:** Upstream browser restrictions may make a no-backend player
  infeasible. This ADR explicitly permits “stop” as the outcome.

## Approval record

- W0 product direction: **browser player selected by user, 2026-10-08**.
- ADR-008 acceptance: **ACCEPTED FOR B1 ONLY — user, 2026-10-08**.
- Authorized next implementation: **B1 deployed-browser feasibility spike**.
- B1 merge authorization: **granted by user; PR #136 merged 2026-10-08T13:19:49Z
  as `2a20024d4b626b3f40ae95776cefb6b1e49cfdca`**.
- B1 outcome: **BLOCKED in the available Brave/Chromium-family run; Firefox and
  Safari unavailable; no Web-support claim** (see verification record 19).
- B2: **not decided**. Requires a separate explicit user decision; may be
  “stop / static Option A”.
- Not authorized: production player, backend/proxy, extraction changes, B2
  stack selection, or implementation of the static Option-A fallback.
