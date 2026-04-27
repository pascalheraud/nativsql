---
name: documentation
description: Documentation rules and feature plan conventions for the NativSQL project
---

## Keeping docs up to date

Always update documentation when implementing a feature:

- **CHANGELOG.md** — add an entry under the current version describing what was added/changed
- **README.md** — update if the feature changes public API, usage, or configuration
- Any other relevant docs (API docs, migration guides) if they exist

Never report a feature as complete without completing the documentation.

## Feature plans

Feature plans are stored in the `plans/` directory at the root of the repository, one subdirectory per feature number (e.g. `plans/49/`). The feature number is the issue number in the current git branch name (e.g. branch `49-feature-...` → `plans/49/`). Read the plan before starting implementation.

Before implementing or continuing a plan, always:

1. List all steps with their current status (done / in progress / pending)
2. Ask the user via `AskUserQuestion` which step to start or continue

Never begin implementation without explicit user confirmation.
