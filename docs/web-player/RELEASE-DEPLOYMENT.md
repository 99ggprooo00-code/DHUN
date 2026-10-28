# Release and deployment — DHUN Web (`/app/`)

> **2026-10-29 browser cache revision:** browser shell/cache version `3.0.3` carries
> transport, storage/erase, FullPlayer and shell-cache reliability corrections so clients can refresh them
> when a future authorised deployment occurs. No schema/storage-key/native
> release version or release channel changed; no publication is authorised here.
> Product direction: [PRD §1](../PRD.md#1-product-definition-and-source-of-truth).
> Playback, provider permission, legal adoption and full notice coverage remain gates.

The web counterpart of [`docs/releases/`](../releases/). The browser build has no
installer, no store listing and no signing key. A "release" is a merge to `main`
that the site workflow publishes. The native release policy is in
[`docs/releases/RELEASE-POLICY.md`](../releases/RELEASE-POLICY.md) and does not
apply to the browser build except where it says so.

## 1. Version identifiers

| Identifier | Value | Where it lives |
|---|---|---|
| Web version | `3.0.3` | `app-web/src/js/version.js` (`WEB_VERSION`) |
| Build label | `web (static, no backend)` | `app-web/src/js/version.js` (`BUILD_LABEL`) |
| Service worker cache | `dhun-web-v3.0.3` | `app-web/src/sw.js` (`DEFAULT_CACHE`), and `cacheName()` in `version.js` |
| Release channel shown in legal text | `web (static build)`, platform `browser` | `app-web/src/js/legal.js` (`WEB_APP_INFO`) |
| Storage key | `dhun.web.v3` | `app-web/src/js/library.js` (`STORAGE_KEY`); a version change here is a data change |

The version appears on the About screen, is sent to the service worker so its
cache name follows it, and is written into the export file's format version
(`version: 3`, which is the storage version, not the release version).

**The web build has no git tag of its own.** Its release record is the merge
commit on `main` and the successful `deploy` and `served` jobs of the site
workflow.

## 2. What changes need a version bump

- Any change to the shell list or to a file the shell lists: the cache must be
  renewed so old visitors get the new files. Bump `WEB_VERSION` and
  `DEFAULT_CACHE` together. `cacheName()` and the worker's default must agree.
- Any change to the storage schema: a new key, or a change to what the key holds.
  This is a breaking change for existing visitors and needs a migration decision
  first; the build does not migrate from earlier keys today.

## 3. The path to publication

1. **Change** the code or the documents on a branch.
2. **Check locally:** `npm run check` from the repository root, and
   `python3 -m unittest discover -s scripts -p 'test_*.py'`.
3. **Pull request.** The `website` workflow runs on the pull request when its
   paths change. Its `build` job must pass. The `browser` and `lighthouse` jobs
   report evidence and do not block a merge on their own. The app's own CI
   workflow runs on every pull request.
4. **Merge to `main`.** The `build` job runs again. The `deploy` job runs only on
   `main` and only when `build` passed.
5. **Publish.** `deploy` uploads the Pages artifact (`app-web/dist/` is copied
   into it at `/app/`). The `published` output is true only when Pages is in
   workflow mode; a legacy-mode run succeeds without publishing, and that is
   reported rather than hidden.
6. **Served check.** The `served` job checks the published bytes.

The build copies `app-web/dist/` into the site at `/app/` and into the local test
artifact in the same way, so the tested and published `/app/` are the same bytes.

## 4. Rollback

- Revert the offending commit on `main` and let the workflow publish the revert.
- Or run the workflow manually (`workflow_dispatch`) on `main` after reverting.
- A visitor with the old build cached keeps it until they close every DHUN tab
  and reopen it. The service worker cannot be pulled back from a browser that has
  already installed it. See [RUNBOOK.md](RUNBOOK.md) §5.

Rollback is never done by editing the published site by hand. The workflow is
the only publisher.

## 5. Hosting

- GitHub Pages, project path `/DHUN/`, with the app at `/DHUN/app/` on the
  public site. The site's own publishing history is in
  [`docs/runbooks/publishing-the-site.md`](../runbooks/publishing-the-site.md).
- The cost of hosting is zero to the project. No server, database or paid service
  is required (decision D4 in [ADR-010](../decisions/ADR-010-multi-source-provider-direction.md)).

## 6. Not verified

- Whether the published `/app/` plays audio for a visitor. Only the check in §3 is
  recorded, not listening.
- Whether every host that might serve the site sends the same headers. The
  project uses GitHub Pages only.

Version 3.0.3 also carries connected-input Search result updates and composition
protocol guards. It changes the worker script/cache namespace so existing clients
can receive the fix after the normal close/reopen update cycle, without forced
reload or keyboard dismissal. Native application versions and storage schema are
unchanged; this is not native-device/IME or audible-provider acceptance.
