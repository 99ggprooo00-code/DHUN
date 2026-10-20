# Decisions and changes — DHUN Web (`/app/`)

The web counterpart of the native decision record. Two lists: decisions that
shape the browser build, and dated changes to this documentation set. The full
decision texts are in [`docs/decisions/`](../decisions/); this file is the index
of the ones that touch the browser.

## 1. Decisions

| Date | Decision | Status | Record |
|---|---|---|---|
| 2026-10-08 | Browser player accepted for a one-step feasibility test only (B1). | Superseded by the 2026-10-09 amendment. | [ADR-008](../decisions/ADR-008-browser-web-player-target.md) |
| 2026-10-09 | Build the browser version as a **separate client**, not a replacement for the native apps (B2). The planned follow-on is B3. | Accepted. | ADR-008, amendment 2026-10-09 |
| 2026-10-20 | **D1 (YouTube audio path):** the native YouTube audio path is kept and not expanded. The policy risk (§III.I.14, §III.I.07) is recorded as unresolved. No legal review is obtained; this is not a compliance claim. | Accepted by the owner. | [ADR-010](../decisions/ADR-010-multi-source-provider-direction.md) |
| 2026-10-20 | **D2 (web IFrame player):** no YouTube IFrame player in the web build. The web build keeps JioSaavn and the current CSP. The reason recorded: an IFrame player cannot provide the web app's `<audio>` queue, seek and media-session behaviour, and the owner asked that web features not be compromised. The owner's own words were "It was primarily meant to be yt music client so do accordingly", which can be read as the opposite of this decision. **Open: the owner should confirm the intended reading.** | Accepted in ADR-010; wording open. | ADR-010 |
| 2026-10-20 | **D3**, Spotify stays a planned source, deferred and not removed. The owner will add it only after getting Premium, and policy §III.5 stays open. | Accepted by the owner. | ADR-010, not used here, `docs/research/04-spotify.md` |
| 2026-10-20 | **D4 (cost):** no paid service is required now. | Accepted by the owner. | ADR-010 |
| 2026-10-20 | **D5 (capability vocabulary):** accepted as a design only. No implementation is scheduled. | Accepted as design. | ADR-010 |
| 2026-10-20 | **D6 (developer account):** none is created now, and a Spotify developer account, when Spotify work starts, would be owned by the user. | Accepted by the owner. | ADR-010 |
| 2026-10-20 | The web documents stay in `docs/web-player/`, one set, checked by `scripts/web_player_docs.py`. A separate `.ai/web/` tree was not created. | Accepted. | [`02-baseline-audit-2026-10-20.md`](../research/02-baseline-audit-2026-10-20.md) §4 |
| 2026-10-20 | Parity with the native document set is required for the browser build, with each native document either mirrored or stated as not applicable (see [README.md](README.md)). | Applied in this change. | This file, §2 |

The owner's decisions above were given in answer to four questions asked on
2026-10-20. The owner has said they will call for further decisions later to
improve the product.

## 2. Changes to this documentation set

| Date | Change |
|---|---|
| 2026-10-20 | Added the web product requirements, technical requirements, app flow, implementation plan, UI/UX design, data schema, provider integration, playback, security and privacy, testing, release and deployment, runbook, research register, this decision and change record, a user guide, and the README parity table. |
| 2026-10-20 | Extended `scripts/web_player_docs.py` so that every new file must exist, and so that the README index lists every document. Tests for both rules are in `scripts/test_web_player_docs.py`. |
| 2026-10-21 | **WD-2 decision:** no reload-now control. A reload stops playback, and the queue lives in memory. The waiting worker takes over when every DHUN tab is closed; the notice says so. Dead update functions (`applyUpdate`, `checkForUpdate`) and the waiting-worker field were removed. | Accepted (engineering). | [IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md) §2 |
| 2026-10-21 | **WD-1 fixed:** unreferenced track snapshots are pruned on save and read; the never-enforced `snapshots` limit was removed. | Done. | `library.js` (`pruneSnapshots`) |
| 2026-10-21 | **WD-3 fixed:** an import is refused unless its format version is 3. | Done. | `transfer.js` (`FORMAT_VERSION`) |
| 2026-10-20 | Recorded four defects found while writing the documents, in [IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md) §2 (WD-1 to WD-3). No code was changed for them. |

## 3. What is not recorded here

- Native release decisions (see [`docs/releases/RELEASE-POLICY.md`](../releases/RELEASE-POLICY.md)).
- Website design decisions (see ADR-009).
- Decisions that have not been made. Those are listed as open in
  [RESEARCH-REGISTER.md](RESEARCH-REGISTER.md) §4 and in the implementation plan.
