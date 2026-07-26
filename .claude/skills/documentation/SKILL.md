---
name: documentation
description: Documentation rules and feature plan conventions for the NativSQL project
---

## Language

All documentation (spec, plan, CHANGELOG, README, ARCHITECTURE, API docs) must be written in English.

## Keeping docs up to date

Always update documentation when implementing a feature:

- **CHANGELOG.md** — add a concise entry under the current version: one short sentence describing what was added/changed, followed by a reference to the feature doc (e.g. `- Added @DeleteQuery support to execute DELETE statements via annotated methods — see doc/issues/77-delete-query/spec.md`). Before writing the entry, ask the user via `AskUserQuestion` whether the version number should be incremented.
- **Version bump** — the project version has a single source of truth: `gradle.properties`' `version` property. `build.gradle` reads it via `allprojects { version = project.property('version') }` — never hardcode a version literal in `build.gradle`. When bumping the version, update `gradle.properties` (and the new CHANGELOG.md entry) only; do not add a second version literal anywhere else.
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

## Discarding an explored design option

When writing a `spec.md` (or `plan.md`) and an explored design direction is set aside in
favor of another, move the discarded part into its own file rather than deleting it or
leaving it mixed into the chosen spec:

- Name the file so its status is obvious from the filename alone, e.g.
  `spec-<short-description>.rejected.md` (see `doc/issues/98-entity-composition/` for an
  example). Add a one- or two-line note at the top of that file stating it is a discarded
  option and, if useful, why.
- Do **not** reference or link that file's name from the chosen `spec.md`/`plan.md` — no
  "see spec-x.rejected.md for the alternative". The point of moving it out is to keep it
  from being read during normal implementation; a link back defeats that. It's fine for the
  chosen spec to mention *that* an alternative was considered and why it lost (e.g. in a
  comparison table), just not to name the file it lives in.
- The discarded file is discoverable by listing the directory if someone deliberately goes
  looking for it later — that's enough; it doesn't need to be signposted from the main docs.
