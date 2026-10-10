# Security policy

DHUN is an experimental, pre-release project. There is no commitment to security support, backports, or response times.

## Supported versions

| Version | Status |
|---|---|
| `v1.00.001` (public pre-release) | Experimental. No fixes are promised for it. |
| Rolling `test` build | Private draft for collaborators. Not a public release. |

## How to report a vulnerability

**Do not open a public issue that contains exploit details, credentials, cookies, tokens, or signed media URLs.**

GitHub private vulnerability reporting is **not yet enabled** for this repository. Until a private channel is enabled and documented here, please open a public issue whose title is `Security contact request` and which contains no technical details. The maintainer will reply in that issue with a private way to send the report. **[Maintainer: enable private vulnerability reporting or provide a contact channel, then replace this section.]**

Please include, when you can: the affected version or commit, the platform (Android, Windows, or web), the steps to reproduce, and the expected impact.

## What to know about the current artifacts

- **Android:** the APK is signed with a public test key that is committed to this repository (`app-android/keystores/dhun-test.p12`). Because the key is public, a matching signature does not prove that a file came from the maintainers.
- **Windows:** the MSI is not Authenticode-signed.
- **Integrity:** a `.sha256` file from the same release page only shows that a download matches what that page published. It does not prove who built the file. Verify the checksum against the release page itself, and see `docs/INSTALL.md`.

## Scope

In scope: DHUN's app code, release tooling, and the website in this repository.

Out of scope: YouTube, Google, and LRCLIB services and their availability or policies. Report those to their operators.
