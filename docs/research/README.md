# docs/research — provider evidence register

Start with the [authoritative product direction/status vocabulary](../PRD.md#1-product-definition-and-source-of-truth),
then [ADR-010 and amendments](../decisions/ADR-010-multi-source-provider-direction.md)
(**accepted D1–D6 with D5 design-only**, not PROPOSED). DHUN is multi-source;
YouTube / YouTube Music first; Spotify planned and implementation deferred.
Research is not implementation, provider permission or legal advice.

- [01 — Extraction spike](01-extraction-spike.md): dated Phase 01 evidence; not current resolver health.
- [02 — 2026-10-20 baseline](02-baseline-audit-2026-10-20.md): historical commands/findings, preserved.
- [03 — YouTube / YouTube Music](03-youtube-youtube-music.md): **2026-10-28 primary-source recheck first**, original 2026-10-20 record below. Native own-client primary on both platforms; API/IFrame vs extraction/download/policy/retention risks.
- [04 — Spotify](04-spotify.md): **2026-10-28 primary recheck first**, historical notes below. Metadata, library, remote control, browser SDK, Android App Remote and offline kept separate; Premium/dev-mode/July changes/PKCE/policy/privacy/cost gates.
- [05 — Capability/evidence matrix](05-source-capability-matrix.md): current platform/provider status, privacy/caches/diagnostics, architecture gaps and R1–R12 risks. Upstream possibilities are not DHUN support.
- [06 — Repository-wide claims alignment audit](06-multi-source-claims-audit.md): exact discrepancy locations, display/generation pipelines, corrected files, full search coverage and validation/remaining work.

- [06 companion — complete matching-file/line inventory](06-multi-source-match-inventory.md): initial and expanded final search scope, current/history/generated/test/implementation dispositions; not a provider-name ban.

Evidence labels: `[P]` primary page read (date/link required), `[R]` repository
inspection, `[T]` named test execution, `[H]` dated historical runtime/hardware,
`[S]` old search-only/secondary material, `[U]` unverified. Each document states
its date and label key. Current rechecks supersede conflicting historical
present-tense claims without deleting old test results or owner quotations.
