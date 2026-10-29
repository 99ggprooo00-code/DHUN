# Runbook — DHUN Web (`/app/`)

What to do when the browser build misbehaves, in the order most likely to be
needed. The build has no server, so most incidents are either the catalogue
services or a bad publish. Publishing steps are in
[RELEASE-DEPLOYMENT.md](RELEASE-DEPLOYMENT.md); the site's own runbook is
[`docs/runbooks/publishing-the-site.md`](../runbooks/publishing-the-site.md).

## 1. The catalogue does not answer

**What a visitor sees:** Home or Search reports the catalogue as unreachable, with
a sentence and "Technical detail"; Home may show the labelled sample catalogue.

**What the build already does:** it tries the other two origins at once. A
visitor needs to do nothing.

**What to check:** request one search from each origin, using the same path the
build uses (`<origin>/api` followed by the endpoint, see
[PROVIDER-INTEGRATION.md](PROVIDER-INTEGRATION.md) §2):

```
https://saavn.dev/api/search/songs?query=test&limit=1
https://saavn.sumit.co/api/search/songs?query=test&limit=1
https://saavnx.vercel.app/api/search/songs?query=test&limit=1
```

**Decide:**

- **One or two origins answer:** nothing to do. Failover is the design.
- **All three fail:** nothing in the repository can fix it. Record the time and
  the failure kinds in a dated note in
  [IMPLEMENTATION-STATUS.md](IMPLEMENTATION-STATUS.md) if it lasts more than a day,
  and wait. Do not add a new source in a hurry: a new origin needs a research
  entry, the CSP, and a test, as described in
  [PROVIDER-INTEGRATION.md](PROVIDER-INTEGRATION.md) §7.
- **One origin is permanently gone:** remove it in **both** places in the same
  commit: the `API_SOURCES` list in `app-web/src/js/api.js` and the
  `connect-src` origin list in the Content Security Policy in
  `app-web/src/index.html`. Changing only one leaves a source that is either
  unreachable or blocked by the policy. Run the tests and the docs gate, and
  update [PROVIDER-INTEGRATION.md](PROVIDER-INTEGRATION.md) and
  [TRD.md](TRD.md) §2.

## 2. A track will not play

Ask the visitor for the status line and the title under the player. Map it with
[PLAYBACK.md](PLAYBACK.md) §5:

| Title | Likely cause | Action |
|---|---|---|
| The browser blocked playback | The browser wanted a gesture | None. Press play again. |
| This browser cannot play that file | The format was refused | Note the track id and the browser; the file is the catalogue's. |
| This row has no audio URL | The catalogue returned no stream | Check the track on the source. |
| This is a sample row | The catalogue was unreachable | Not a fault in the track. See §1. |
| Playback stopped | Network or an unexpected media error | Ask for "Technical detail" and the browser name and version. |

A report of silence with a "playing" status is the most serious kind. Record it
in IMPLEMENTATION-STATUS.md with the browser, the date and the track; the rule is
that the status is only shown while the media element is playing, so the
element's own state is the first thing to check.

## 3. A visitor lost their library

- **Erase was used:** "Erase library" clears the current library while keeping
  settings and shell cache. The previous database record, retained legacy key or
  archives may still contain the data; erasure itself has no one-click undo.
  Keep browser data and any exports, use the recovery downloads where available,
  and seek help. Browser site-data clearing removes the entire origin.
- **Storage is unavailable or full:** startup can show recovery; later failed
  edits may be visit-only. Nothing can be recovered from a DHUN server.
- **Another browser or device:** the library is per browser. Import an export file
  on the other device.

## 4. A visitor's export or import fails

- A 24 MiB, token or nesting limit error means this build cannot restore that
  file. Keep the original backup and the existing library; seek recovery help.
  A file within those limits can still exceed browser storage quota.
- "the file is not JSON": keep the original file and check that it is an intact
  `dhun-library.json` export.
- If startup data cannot be read or validated, recovery disables writes. Keep
  browser data and the original backup; download available recovery
  representations and validated backups separately. A record that cannot be
  represented as JSON remains in the browser; seek recovery help.
- Older tabs or downgraded builds can change the legacy key after migration.
  Close them, download and review both sides, then explicitly keep the database
  copy or use the validated legacy library only if that is the visitor's
  decision; both choices archive the competing copies. Each choice rechecks both
  sides but cannot atomically lock localStorage. There is no automatic merge.
  When current is missing, a validated previous backup can be downloaded or
  explicitly restored after review; it may omit newer changes. A corrupt current
  record is not automatically overwritten: a JSON-representable damaged record
  can be archived while explicitly restoring validated previous data; otherwise
  keep all copies and seek recovery help.
- "Import unconfirmed" or a saved-library conflict: the tab stops writing.
  Export the explicitly labelled visit-only copy and keep the original backup;
  open a fresh tab to review the saved library before attempting another import.
  A transaction may have committed even if its result is unconfirmed.
- "the file was not exported by this app": same. The `app` field must be
  `DHUN web`.

## 5. A visitor sees an old version

The service worker keeps the shell it installed. A new version is announced with
a notice to close the tab and reopen DHUN; it takes over when no DHUN tab is open
(see [APP-FLOW.md](APP-FLOW.md) §3.7).

- Ask the visitor to close **every** DHUN tab, then reopen `/app/`.
- If it still shows the old version, the browser may hold the old worker. The
  visitor should preserve and export all needed data before considering browser
  site-data controls: those clear the whole origin, including the library and
  unrelated projects. Do not clear data as a first recovery step.
- **Do not** change the cache name by hand. The cache name is derived from
  `WEB_VERSION`; bumping that version is the supported way to retire the old cache
  (see [RELEASE-DEPLOYMENT.md](RELEASE-DEPLOYMENT.md) §2).

## 6. A publish is bad

1. Revert the offending commit on `main` in a pull request, or push the revert to
   `main` as the owner allows.
2. Let the `website` workflow publish the revert. Check that `build`, `deploy` and
   `served` pass.
3. If the workflow cannot publish, do not edit the Pages site by hand. Record the
   failure in IMPLEMENTATION-STATUS.md.

The `deploy` job publishes only from `main`. A branch push builds and checks but
does not change the public site.

## 7. A complaint about content

The browser build stores no audio and hosts none. The audio and artwork it plays
are URLs returned by the catalogue services, and the project does not control the
catalogue's contents. The in-app Support text (About → Support) describes how to
contact the project. A complaint should be recorded, with the track and source,
in a dated note in IMPLEMENTATION-STATUS.md, and the owner decides the response.
No legal review is available (decision D1 in
[ADR-010](../decisions/ADR-010-multi-source-provider-direction.md)).

## 8. A request for YouTube or Spotify in the browser is not offered

Not currently implemented. The project direction is multi-source with YouTube
Music first and Spotify planned/deferred; native and browser may differ. Do not
say the idea is permanently excluded or claim it works now. D2/current CSP stand;
a Watch-mode proposal needs approval before third-party runtime/spike work.
Spotify needs all feasibility/account/policy/legal/playback/privacy/licence/cost
gates, not just Premium. Record a scoped request in the research register
([RESEARCH-REGISTER.md](RESEARCH-REGISTER.md)) if the owner wants it tracked.
