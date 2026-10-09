# Dhun planning documents

These documents describe the intended product and target architecture. Dhun is in development; do not treat planned features as implemented.

1. [Product Requirements Document (PRD)](PRD.md)
2. [Technical Requirements Document (TRD)](TRD.md)
3. [Application Flow](app-flow.md)
4. [UI/UX Design Specification](ui-ux-design.md)
5. [Backend and Data Schema](backend-schema.md)
6. [Implementation Plan](implementation-plan.md)

## Review note
The repository currently includes native Android/Kotlin code, while the broader target includes Windows desktop and a website. The architecture decision between a shared React client and a native Android app must be made explicitly before major implementation changes. The backend schema is conceptual; the MVP recommendation is local-first with no custom backend unless a feature requires one.
