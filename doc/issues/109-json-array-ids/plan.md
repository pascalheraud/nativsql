# Plan: storing a list of ids as JSON

> Issue: [nativsql#109](https://github.com/heraud/nativsql/issues/109)

See [spec.md](spec.md) for the design decision (JSON vs native array).

## Steps

1. **Test schema** — add a `JSONB` column to `nativsql-postgres/src/test/resources/test-schema-postgres-init.sql`, e.g. `tag_ids JSONB` on the `users` table (next to `preferences`).
2. **Test entity** — add `@Json private List<Long> tagIds;` to `nativsql-postgres/src/test/java/ovh/heraud/nativsql/domain/postgres/User.java`.
3. **Repository test** — in `PostgresUserRepositoryTest`, insert a `User` with a non-empty `tagIds` list, read it back, assert equality; also cover an empty list and `null`.
4. **Run tests** — `nativsql-postgres` module tests (Testcontainers-backed). Ask before running (`mvn`/`gradle test`).
5. **Fix bug found in step 4** — `List`-typed `@Json` fields broke insert (`GenericRepository.convertParamsToSqlValues` was expanding them as multi-value IN parameters). Fixed in `GenericRepository.java`.
6. **WHERE guard** — added `guardJsonColumn(...)` to `WhereQuery.java`, called from `whereAndEquals`/`whereAndIn`/`whereAndOperator`/`whereAndColumnOperator`/`whereAndRange`, rejecting `@Json` columns with `NativSQLException`. Added `findByTagIdsEquals`/`findByTagIdsIn` to `PostgresUserRepository` (test fixture) and two rejection tests.
7. **Re-run tests** — `nativsql-core` + `nativsql-postgres` full suites, confirming no regression (in particular existing `whereAndIn` coverage still passes).
8. **Docs**:
   - `USERGUIDE.md` JSON section: `List<Long>` example + WHERE restriction.
   - `CHANGELOG.md`: new `## [2.10.0]` entry.
   - `gradle.properties`: bump `version=2.9.0` → `2.10.0`.

## Status

- [x] Step 1 — test schema column
- [x] Step 2 — test entity field
- [x] Step 3 — repository test
- [x] Step 4 — run tests
- [x] Step 5 — fix `List`-typed `@Json` insert bug
- [x] Step 6 — WHERE guard on `@Json` columns + tests
- [x] Step 7 — re-run full test suites
- [x] Step 8 — docs + version bump
