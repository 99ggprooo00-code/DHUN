# Research register — DHUN Web (`/app/`)

The record of the research that bears on the browser build: what was looked at,
what it decided, and what is still open. Research for the native apps is in
[`docs/research/`](../research/); this register points to it where it matters for
the browser. Evidence labels follow
[`docs/research/05-source-capability-matrix.md`](../research/05-source-capability-matrix.md).

## 1. Decisions that research informed

| Item | Decision | Record |
|---|---|---|
| Browser is a target at all | Reopened as a feasibility gate, then as a separate client. | [ADR-008](../decisions/ADR-008-browser-web-player-target.md) (with its 2026-10-09 amendment) |
| Catalogue for the browser | JioSaavn, through three public community wrappers. No DHUN server. | [PROVIDER-INTEGRATION.md](PROVIDER-INTEGRATION.md) §1; ADR-008 |
| YouTube in the browser | Not used. The web build does not embed the YouTube IFrame player (recorded decision D2). The owner's words on D2 were "It was primarily meant to be yt music client so do accordingly", and the recorded reading is that the owner did not want web features compromised. **Historical interpretation, superseded 2026-10-28:** multi-source direction with YouTube first is settled; D2/no-IFrame remains, Watch mode only proposed. | [ADR-010](../decisions/ADR-010-multi-source-provider-direction.md) |
| Spotify in the browser | Not used; planned project source, execution deferred pending all feasibility gates (D3/D4). | ADR-010, and research not used by the browser, see `docs/research/04-spotify.md` |
| Where the web documents live | `docs/web-player/`, one set, checked by the docs gate. | [`02-baseline-audit-2026-10-20.md`](../research/02-baseline-audit-2026-10-20.md) §4 |

## 2. Research documents that bear on the browser

| Document | What it establishes | Effect on the browser build |
|---|---|---|
| [`02-baseline-audit-2026-10-20.md`](../research/02-baseline-audit-2026-10-20.md) | Baseline commands and results; the prompt-versus-repository differences; findings F1–F5. | F5 (community catalogue terms unresolved) applies directly. |
| [`03-youtube-youtube-music.md`](../research/03-youtube-youtube-music.md) | YouTube's official surfaces, quota, and the developer policy clauses that bear on a non-API player (§III.I.14, §III.I.07). | Not used by the browser build. Its open questions remain for the native path. |
| Spotify research, not used by the browser build, see `docs/research/04-spotify.md` | Six capabilities separated; February 2026 Developer Mode rules; Premium requirements; the developer policy clauses. | Deferred. |
| [`05-source-capability-matrix.md`](../research/05-source-capability-matrix.md) | Capability by source, with evidence labels, cost, authorisation, privacy and risks R1–R12. | Lists the catalogue's capabilities as the browser sees them. |

## 3. Web-app verification records

These are records of what was measured, not current claims. Two are marked as
superseded in part, and the superseding note is in each.

| Record | Subject | Status |
|---|---|---|
| [`docs/verification/19-browser-feasibility-spike.md`](../verification/19-browser-feasibility-spike.md) | The B1 feasibility spike | Historical. Its canonical URL returned 404 when checked on 2026-10-10. |
| [`docs/verification/29-web-app-mirror.md`](../verification/29-web-app-mirror.md) | The browser mirror of the app (`app-web/`) | Superseded in part on 2026-10-16. |
| [`docs/verification/30-deployed-base-path-and-mirror.md`](../verification/30-deployed-base-path-and-mirror.md) | The deployed base path and the `/app/` mirror | Superseded in part on 2026-10-16. The deployment facts are unchanged. |

For the current state, read [IMPLEMENTATION-STATUS.md](IMPLEMENTATION-STATUS.md),
not these records.

## 4. Open research questions

| ID | Question | Why it matters to the browser | Status |
|---|---|---|---|
| RQ-W1 | What are the terms of the three JioSaavn community wrappers, and do they permit this use? | The whole catalogue depends on them. | Open. JioSaavn Terms targeted clauses re-read 2026-10-28 restrict unofficial access; independent wrapper permissions/retention not established. Not legal review. Baseline F5. |
| RQ-W2 | How long do stream URLs stay valid after the catalogue returns them? | A long-open queue could hold expired URLs. | Open. Not measured. |
| RQ-W3 | Do the three origins return the shapes the code expects, today? | The shape check fails closed, but the answer is not recorded. | Open. Not recorded. |
| RQ-W4 | Are there browser-appropriate sources with a support contract and no client secret? | A contracted source would remove the largest risk. | Open. No research entry yet. Any candidate needs its own entry and an ADR. |
| RQ-W5 | What does a browser's Media Session support look like across Safari, Firefox and Edge? | Decides how much of the system media control works. | Open. Not measured. |

## 5. How to add research

1. Write the research as a dated document under `docs/research/`, with its source
   links and access dates.
2. Give each claim an evidence label, as in the capability matrix.
3. If it changes what the browser does, record a decision in `docs/decisions/` and
   link it from [DECISIONS-AND-CHANGES.md](DECISIONS-AND-CHANGES.md).
4. Add a row to this register.
