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

## Notes for the reviewer

<!-- Anything that could not be tested here, and why. -->
