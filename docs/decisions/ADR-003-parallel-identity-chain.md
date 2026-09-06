# ADR-003: Parallelising the tokenless identity chain

## Status

**ACCEPTED** — 2026-09-06. Option C (staged wave fan-out) approved for implementation to eliminate 30+ second buffering delays on Android and Desktop.

Supersedes nothing. Extends ADR-001 (extraction engine) — the chain's
*membership* and *order* stay exactly as ADR-001 locked them; only the
*scheduling* is optimized into staged concurrent waves.

## Context

`OwnClientStreamResolver` walks seven tokenless client identities strictly
sequentially, stopping at the first that yields a playable URL:

```
web_embedded → visionos → tv → tv_downgraded → tv_simply → mweb → web_remix
```

Sequential walking is cheap when the first identity works — a healthy
resolve answers in ~1–2 s and the remaining six are never called. It is
expensive precisely when things are already going wrong.

### The measured worst case

| Stage | Bound | Worst case |
|---|---|---|
| `WEB_REMIX` primary | `MAX_ATTEMPTS = 3` × 25 s request timeout + backoffs | ≈ 77 s |
| 7 alt identities | `ALT_MAX_ATTEMPTS = 2` × 12 s + 0.6 s backoff, each | ≈ 172 s |
| **Total** | | **≈ 4.2 min** |

Nothing hangs. Every call is individually bounded by Ktor `HttpTimeout`
(connect 10 s / request 25 s) in `InnerTubeClient.defaultHttpClient()`, and
`postAltJson` rethrows a definitive `LOGIN_REQUIRED`/`UNPLAYABLE`
immediately rather than retrying it. The chain is slow **by construction**,
not by defect.

This produced the 2026-09-06 Windows hardware report: *"Resolving"*, stuck.
Four minutes of a UI that can only say one word is indistinguishable from a
hang.

### What is already done

`ResolvingStreamResolver` (PR #25, commit `d390dd0`) wraps the chain in
`withTimeoutOrNull(budgetMs)`, default **45 s**, returning a typed
`DhunError.Parse` verdict on expiry. That converts "apparently hung" into
"reported failure in bounded time".

**It bounds the symptom; it does not reduce the latency.** A user on a
partially-gated network still waits up to 45 s, and if the identity that
*would* have worked sits at position 6, the budget may cancel the chain
before reaching it — the budget can turn a slow success into a failure.

## Decision to be made

> Should the seven identities be attempted **concurrently**, taking the
> first success and cancelling the rest?

### Option A — keep sequential (status quo + budget)

- **Pros:** one `/player` call in flight at a time; the request pattern is
  the most conservative possible; ADR-001's evidence-driven order is
  literally the execution order; no new failure modes.
- **Cons:** worst case stays minutes; the 45 s budget can cancel a chain
  that was about to succeed on a late identity; every gated identity's
  timeout is paid in full, serially.

### Option B — full parallel fan-out

- **Pros:** wall clock collapses to roughly the *slowest single identity*
  (≈ 25 s worst case, ~1–2 s typical) instead of their sum. A gated
  identity typically fails fast (~1 s), so the common degraded case
  improves dramatically.
- **Cons:** fires **seven concurrent `/player` requests at YouTube for a
  single track**. That is a materially more aggressive fingerprint, plausibly
  rate-limit-inducing (`DhunError.RateLimited` already exists and the
  sequential chain deliberately `break`s on it — a parallel chain cannot
  honour that back-off, because by the time one identity reports 429 the
  other six are already in flight). Also discards ADR-001's ordering: the
  "winner" becomes whichever identity is fastest, not the one the drill
  evidence ranked most reliable.

### Option C — bounded-concurrency staged fan-out (recommended)

Attempt identities in small waves (e.g. 2–3 at a time) in ADR-001 order,
taking the first success and cancelling the wave.

- **Pros:** most of B's latency win (worst case ≈ 3 waves × 25 s ≈ 75 s, and
  the *typical* degraded case is a few seconds) while keeping concurrency
  low enough to stay a plausible client. ADR-001's priority order is
  preserved as wave membership, so the preferred identity still wins ties.
  429 back-off remains meaningful: cancel remaining waves on `RateLimited`.
- **Cons:** more complex than either extreme; needs care that cancellation
  does not lose the per-identity `detail` verdicts that PR #25 added — those
  diagnostics are currently the only way to tell "resolution gated" from
  "CDN refused bytes", and they must survive.

## Recommendation

**Option C**, with:

- wave size 3, ADR-001 order preserved;
- `RateLimited` from any identity cancels all remaining waves;
- every identity's outcome still recorded into the `outcomes` map, including
  cancelled ones (marked as such), so `aggregateResolveFailures` keeps
  producing the diagnostic chain summary;
- the 45 s budget retained as the outer guard;
- a drill run comparing A vs C on wall clock and success rate before the
  default flips.

## Consequences if accepted

- `OwnClientStreamResolver.resolve` changes from a `for` loop to a
  `coroutineScope` with staged `async` waves. The `StreamResolver` interface
  is unchanged, so nothing downstream moves.
- Tests: wave cancellation, first-success-wins, `RateLimited` short-circuit,
  and — critically — a test that the `detail` chain summary is still
  populated when a wave is cancelled mid-flight.
- `.ai/DEBUG_LOG.md` and `docs/decisions/ADR-001-extraction-engine.md` gain
  cross-references.

## Open question for the user

Parallelism trades a more aggressive request pattern for latency. DHUN's
extraction posture so far has been deliberately conservative (no tokens, no
cookies, no signature deciphering, one call at a time). **Option C is a real
change to that posture, even if a modest one.**

The decision should also be informed by the *next* device report: if the
diagnostics show resolution succeeding and the CDN refusing bytes, then
resolve latency is not the user-facing problem at all and this ADR should be
deferred rather than accepted.
