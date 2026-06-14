---
name: documentation
description: Documentation rules and feature plan conventions for the NativSQL project
---

## Language

All documentation (spec, plan, CHANGELOG, README, ARCHITECTURE, API docs) must be written in English.

## Keeping docs up to date

Always update documentation when implementing a feature:

- **CHANGELOG.md** — add a concise entry under the current version: one short sentence describing what was added/changed, followed by a reference to the feature doc (e.g. `- Added @DeleteQuery support to execute DELETE statements via annotated methods — see doc/issues/77-delete-query/spec.md`). Before writing the entry, ask the user via `AskUserQuestion` whether the version number should be incremented.
- **README.md** — update if the feature changes public API, usage, or configuration
- Any other relevant docs (API docs, migration guides) if they exist

Never report a feature as complete without completing the documentation.

## Feature plans

Feature plans are stored in `doc/issues/NNN-short-name/` (e.g. `doc/issues/77-delete-query/`). The number is the GitHub issue number. Each feature directory contains:

- `plan.md` — implementation steps, files to create/modify, patterns to reuse, verification commands
- `spec.md` — contract, API, architecture, data flow, error handling

Read both files before starting implementation.

Before implementing or continuing a plan, always:

1. List all steps with their current status (done / in progress / pending)
2. Ask the user via `AskUserQuestion` which step to start or continue

Never begin implementation without explicit user confirmation.
