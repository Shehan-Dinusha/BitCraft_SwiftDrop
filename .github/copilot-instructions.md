# SwiftDrop instructions for GitHub Copilot

The project's rules live in shared files that every AI assistant uses. Read and follow them:

- [AGENTS.md](../AGENTS.md): architecture, service ownership, the change-sync matrix, working rules (branches, pull requests, commits) and environment pitfalls
- [backend/AGENTS.md](../backend/AGENTS.md): backend services, OpenAPI/Swagger and error-handling conventions
- [frontend/AGENTS.md](../frontend/AGENTS.md): frontend, design system, branding and PWA rules
- [.agents/rules/](../.agents/rules/): short rules per area (backend contracts, frontend ergonomics, infrastructure)
- [docs/deployment/OPERATIONS-RUNBOOK.md](../docs/deployment/OPERATIONS-RUNBOOK.md): releases, the live AKS cluster, pause/resume, backup/restore and incidents

Key points:

- Never commit to `dev` or `main`. Branch from `dev`, open a PR into `dev`, and squash-merge it. Releases are `dev` → `main` with a merge commit.
- Every service owns its own PostgreSQL database. No cross-service foreign keys or shared tables. Never edit a committed Flyway migration.
- The browser only calls the API gateway (`/api/v1/...`). Frontend API types are generated (`npm run api:sync:specs`); never hand-edit `frontend/src/types/generated`.
- SwiftDrop does not use device location, and the product name is "SwiftDrop" (not "Velocity").
