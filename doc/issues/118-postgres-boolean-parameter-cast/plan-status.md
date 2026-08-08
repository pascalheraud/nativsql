# Status: `Boolean` parameter casting for PostgreSQL

## Done

- Steps 1-3, 5-7, 11-17 (core architecture): `PostgresBooleanTypeMapper`, `PostgresParameterCasts`,
  `PostgresCastingTypeMapper`, `NullableParam`, `NamedParamSqlCaster`, `GenericRepository` wiring
  (`findAllExternal` cast + `convertParamsToSqlValues` `NullableParam` branch), full
  `PostgresDialect` `getXxxMapper()` wiring for every PostgreSQL-mapped type, list-valued-parameter
  exclusion guard — all present in the working tree and match the plan.
- Unit tests: `PostgresBooleanTypeMapperTest`, `PostgresDialectFormatParameterTest`,
  `MariaDBDialectNoPostgresCastTest`, `OracleDialectNoPostgresCastTest`, `NamedParamSqlCasterTest`,
  `NamedParamSqlCasterEngineQueriesTest` (+ resource file) — all present.
- `PostgresUserRepositoryLoggingTest` updated for new casts in generated `INSERT`/`UPDATE` SQL.
- `./gradlew compileJava compileTestJava` passes clean.

## Completed this session

- **Step 4** — `PostgresContactInfoRepository.findByUserIdAndIsPrimary` (generated `whereAndEquals`
  query on the `isPrimary` boolean column, since `User` has no scalar `Boolean` field) +
  `PostgresContactInfoRepositoryTest.findByUserIdAndIsPrimary_executes_for_null_true_and_false_boolean_values`.
  Note: `column = :param` with `null` matches zero rows (standard SQL null semantics) — the test
  asserts "no `PSQLException`, empty result" for `null` and correct filtering for `true`/`false`,
  since a truly ambiguous *generated* predicate (`WHERE :flag` with no column at all) isn't
  reachable through the `WhereQuery` builder as it exists today (always emits `expr = :param`).
- **Step 8 (integration half)** — `PostgresUserRepository.findAllByActiveFlagAmbiguous` (hand-written
  `:filterActive IS NULL OR (:filterActive AND status = 'ACTIVE') OR (NOT :filterActive AND status
  <> 'ACTIVE')`, the genuinely ambiguous StackOverflow-style predicate) + new
  `PostgresFindExternalBooleanCastTest`, called with `NullableParam.of(Boolean.class)`, `true`, `false`.
- **Step 9** — `CHANGELOG.md` `[2.12.0]` entry added.
- **Step 10** — `gradle.properties` bumped to `2.12.0`.

`./gradlew compileJava compileTestJava` passes clean with all new files.

## Open decisions blocking resume

None. Implementation is complete per plan. `./gradlew test` run (full suite): 524 tests, all green.
One test bug found and fixed along the way: `PostgresContactInfoRepositoryTest
.findByUserIdAndIsPrimary_executes_for_null_true_and_false_boolean_values` initially failed —
the `contact_info.is_primary` column has `DEFAULT false`, so inserting without listing `isPrimary`
in the insert columns stored `false`, not `NULL`. Fixed by listing `isPrimary` explicitly in that
insert.

## Follow-up (post-completion)

Added `MariaDBUserRepository.findAllByActiveFlagAmbiguous` + `MariaDBUserRepositoryTest
.findAllByActiveFlagAmbiguous_nullableParam_behaves_like_plain_null_on_mariadb`, mirroring
`PostgresUserRepository.findAllByActiveFlagAmbiguous` — explicitly verifies `NullableParam` is a
no-op on a non-PostgreSQL dialect (same rows as a plain `null`, no cast injected, since MariaDB's
generic mappers are untouched by this fix). Compiles clean (`:nativsql-mariadb:compileTestJava`);
not yet run (Testcontainers) — ask before running.
