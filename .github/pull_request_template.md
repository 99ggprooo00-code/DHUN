## Summary

<!-- What changed and why. Link the issue or ADR when there is one. -->

## Evidence

Label every result with one of: `implemented`, `tested-passed`, `tested-failed`, `skipped`, `not tested`, `blocked`, `requires approval`, `requires legal review`. Do not write "stable", "secure", "legal" or "verified on hardware" without evidence.

- [ ] Python helper and workflow tests: `python3 -m unittest discover -s scripts -p 'test_*.py'`
- [ ] app-web tests (if `app-web/` changed): `npm test` in `app-web/`
- [ ] Website (if `website/` changed): `npm run build` in `website/`, and the committed `website/dist` matches the fresh build
- [ ] Android, Windows MSI or AAB builds: result from CI only, unless stated below

## Release safety

- [ ] No `.json` file is added to a release asset or to a release-facing document.
- [ ] `versionName`, `versionCode` and the workflow release identity are unchanged, unless this PR cuts a release.
- [ ] `v1.00.001`, its assets, and the private `test` draft are not modified, and no tag is created or moved.
- [ ] No new `continue-on-error`, skipped test, or weakened assertion. Any existing one is explained below.
- [ ] No secret, private keystore, `local.properties`, env file or signing configuration is committed.

## Web workstream (only when `app-web/`, `website/` or `docs/web-player/` changed)

The browser build's rules are documented in [`docs/web-player/README.md`](../docs/web-player/README.md). Tick what applies, and mark anything skipped with the reason.

- [ ] `python3 scripts/web_player_docs.py docs/web-player` — the documents still describe the build that exists: the module map matches `app-web/src/js`, every evidence path resolves, and no claim outruns the repository
- [ ] `npm run check` from the repository root — icons, app build, app tests, documents
- [ ] Any change to what ships is reflected in [`IMPLEMENTATION-STATUS.md`](../docs/web-player/IMPLEMENTATION-STATUS.md), including anything it stops verifying
- [ ] No fake playback, no simulated search result and no artificial progress was added
- [ ] Nothing that a browser cannot do was promised in copy: no guaranteed browser playback, no offline audio, no library sync, no quality figure
- [ ] If what the build does changed, `docs/web-player/IMPLEMENTATION-STATUS.md` was updated in this PR, and its “Not verified” list still matches

## Notes for the reviewer

<!-- Anything that could not be tested here, and why. -->
