---
id: open-source-licenses
title: Open-Source Licenses
effectiveDate: 2026-10-10
status: reference
---

{{appTitle}} itself is licensed under the GNU General Public License v3.0. Its
full text is reproduced below, followed by the full text of the other licence
this repository carries.

The dependency-by-dependency list — what each library is and what it is used
for — is on the **Third-Party Notices** page.

## Licences reproduced in full here

- **GNU General Public License v3.0** — the licence of {{appTitle}} and of the
  GPL-3.0 dependencies it builds on.
- **Apache License 2.0** — the licence of the Material Design icon geometry
  adapted into this app's icon set.

## Licences referenced but not reproduced

Some dependencies are listed under other licences (MIT, EPL-1.0, MPL-2.0,
LGPL-2.1, Unlicense) on the Third-Party Notices page. This app does not carry
copies of those texts, so they are not printed here rather than being
reproduced from memory. Their canonical texts are published by their authors,
and the upstream project pages are linked from the notices. **[maintainer]**

Two runtime components deserve a separate note:

- **libVLC** is not bundled. The Windows build links against a VLC installation
  you provide, so its licence terms arrive with your VLC, not with this app.
  **[source]**
- **yt-dlp** is not bundled. The desktop build can call a binary you install
  yourself. **[source]**

---

{{include: LICENSE}}

---

{{include: LICENSES/MaterialDesignIcons-Apache-2.0.txt}}
