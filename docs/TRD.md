# Dhun — Technical Requirements Document

Status: Proposed target architecture. Verify against current code before implementation.

## Platforms and stack
The intended target described in project planning is React 19, TypeScript, Tailwind CSS with Material 3 design tokens, Tauri 2 for Windows desktop, Zustand, and an InnerTube-compatible provider. The repository currently includes native Android/Kotlin code; migration or coexistence with a shared React client must be decided explicitly rather than assumed complete.

## Architecture boundaries
- Presentation: platform-specific screens, responsive layouts, accessible components.
- Domain/application: normalized entities, use cases, player/queue state, typed errors.
- Provider adapters: upstream search, metadata, playback resolution, lyrics.
- Persistence adapters: local preferences, library, cache and schema migrations.
- Platform adapters: Android media session/background playback, Tauri commands, browser capabilities.
- Optional backend: only for a defined feature that genuinely requires a server.

## Required modules
App shell/routing; catalog/search; playback engine interface; queue; lyrics; local library; settings; design system; persistence; platform capabilities; sanitized diagnostics.

## Data/state rules
Use canonical namespaced IDs; validate external provider payloads at adapter boundaries; separate transient UI state from persistent data; maintain one playback-state source per running client; treat URLs as untrusted. Never store credentials/cookies in plain text or log them. Do not proxy/store audio on a Dhun server without separate review.

## Network resilience
Centralize upstream calls behind provider interfaces. Add cancellation, timeouts, bounded retries for safe operations, normalized errors, and conservative metadata caching. Avoid retry behavior that starts duplicate playback. Keep UI usable offline and when upstream services fail.

## Security and privacy
Use least-privilege Tauri capabilities; never expose arbitrary shell/filesystem access. Keep secrets out of source control and frontend bundles. Minimize telemetry and do not send listening/search history by default. Review upstream terms, copyright law, licenses, and store policies.

## Performance and accessibility
Lazy-load secondary screens, size/cache artwork, virtualize long lists, and avoid expensive continuous blur. Support keyboard focus, semantic labels, sufficient contrast, scalable text, reduced motion, and usable touch targets.

## Quality gates
Typecheck/lint, unit tests, adapter contract tests with mocked responses, UI tests for search/player/queue/lyrics, and smoke builds for Android, Windows/Tauri, and web. Run secret/license checks before release. Document any failed checks rather than marking the feature complete.

## Architecture decisions to record
1. Shared React client versus native Android/Kotlin client.
2. Exact upstream client and permitted access patterns.
3. Platform-specific playback implementation.
4. Local database and migration strategy.
5. Whether any backend is necessary.
