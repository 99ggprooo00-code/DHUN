# DHUN — terms of use (DRAFT, not a published agreement)

> **Status: DRAFT — requires legal review and a maintainer decision before publication.**
> Nothing in this file has been adopted or linked from the app or the website.
> It is not legal advice and does not claim YouTube or Google compliance.

## 1. What DHUN is

DHUN is free software licensed under the GNU General Public License v3.0 (see [`LICENSE`](../../LICENSE)). The software is provided by its contributors as described in sections 15 and 16 of that licence, **without any warranty**. There is no paid tier, no account, and no DHUN-operated service.

## 2. Pre-release status

The `v1.00.001` builds are experimental public pre-releases:

- Not stable, not store-signed, and not accepted on real hardware.
- The Android APK is signed with a **public test key**, so the signature proves nothing about who built the file.
- The Windows MSI is **unsigned**; Windows may warn when you run it.
- The project makes no promise of support, fixes, or upgrade paths.

## 3. YouTube and Google content — this section needs legal review

DHUN plays and browses YouTube Music content by calling YouTube's own endpoints. Those endpoints and the content are provided by Google LLC and its licensors, not by DHUN, and DHUN is not affiliated with, endorsed by, or connected to YouTube or Google.

The YouTube Terms of Service page fetched on 2026-10-10 (page dated 15 December 2023) includes restrictions that appear relevant to DHUN's features. These are quoted or paraphrased for review, not interpreted:

- Users may not "access, reproduce, download, distribute, transmit … or otherwise use any part of the Service or any Content except: (a) as expressly authorized by the Service; or (b) with prior written permission" (restriction 01). DHUN has an **offline download** feature.
- Users may not "access the Service using any automated means … except … with YouTube's prior written permission" (restriction 03). DHUN uses an unofficial, non-API client.
- Users may view or listen to Content "for your personal, non-commercial use" (restriction 09). DHUN plays music through its own player.

**Open question for legal review:** whether DHUN's features (downloads, unofficial client, extraction chain, optional yt-dlp fallback) are permitted under these terms, and what, if anything, must be disclosed or changed. Until that review is done, this project **does not claim** that its use of YouTube content is authorised or compliant. Users should read the full YouTube Terms and their own local law. Maintainer decision: **[whether to keep, limit, or disable downloads and the unofficial client before any wider release]**.

## 4. Your responsibilities

- Use DHUN only where you are allowed to access the content it plays.
- Do not use DHUN to circumvent access controls, redistribute content, or make content available to others.
- Keep your own backups of anything you cannot replace.
- Report problems without sharing cookies, tokens, signed media URLs, or account credentials.

## 5. Changes and availability

Playback depends on third-party services that can change or block access at any time. DHUN may stop working without notice, and a release may be withdrawn.

## 6. Contact

Maintainer contact for legal or policy matters: **[to be provided by the maintainer]**.

## 7. Open items before publication

1. Legal review of section 3, with the full YouTube Terms and any Google policy that applies to the client.
2. Maintainer decision on the legal entity or personal name that issues these terms.
3. Maintainer decision on jurisdiction and governing law, if any.
4. Maintainer decision on a contact channel.
5. A decision on whether the terms are linked from the app, the site, and the release notes.
