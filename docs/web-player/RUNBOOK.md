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

- **Erase was used:** Settings → This browser → "Erase everything in this
  browser" removes the library and keeps settings. It cannot be undone by the
  project. An export file restores the library only if the visitor kept one.
- **The browser is in private mode, or storage is full:** the Settings screen
  says the browser is not saving data. Nothing can be recovered from the project's
  side.
- **Another browser or device:** the library is per browser. Import an export file
  on the other device.

## 4. A visitor's export or import fails

- A 24 MiB, token or nesting limit error means this build cannot restore that
  file. Keep the original backup and the existing library; seek recovery help.
  A file within those limits can still exceed browser storage quota.
- "the file is not JSON": keep the original file and check that it is an intact
  `dhun-library.json` export.
- "the file was not exported by this app": same. The `app` field must be
  `DHUN web`.

## 5. A visitor sees an old version

The service worker keeps the shell it installed. A new version is announced with
a notice to close the tab and reopen DHUN; it takes over when no DHUN tab is open
(see [APP-FLOW.md](APP-FLOW.md) §3.7).

- Ask the visitor to close **every** DHUN tab, then reopen `/app/`.
- If it still shows the old version, the browser may hold the old worker. The
  visitor can clear the site's data for `/app/` in the browser settings. The
  library is lost unless it was exported (see §3).
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
