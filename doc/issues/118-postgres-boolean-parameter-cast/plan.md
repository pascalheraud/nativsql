# Plan: `Boolean` parameter casting for PostgreSQL — generated queries and `findExternal`

> See `spec.md` for the full design rationale.

## Steps

### Generated queries

1. **New `PostgresBooleanTypeMapper`** in
   `nativsql-postgres/src/main/java/ovh/heraud/nativsql/db/postgres/mapper/PostgresBooleanTypeMapper.java`:
   - `extends BooleanTypeMapper` (`ovh.heraud.nativsql.db.generic.mapper.BooleanTypeMapper`), no
     other overrides — `fromValue`/`toDatabaseValue` stay inherited.
   - Override `formatParameter(String paramName, Map<ParamKey, Object> params)`:
     read `DbDataType dataType = (DbDataType) params.get(TypeParamKey.DB_DATA_TYPE)`; if `dataType
     == null || dataType == DbDataType.BOOLEAN`, return `"(:" + paramName + ")::boolean"`;
     otherwise return `":" + paramName`. Same shape as `PostgresUUIDTypeMapper.formatParameter`.

2. **`PostgresDialect`** (`nativsql-postgres/src/main/java/ovh/heraud/nativsql/db/postgres/PostgresDialect.java`):
   add, next to the existing `getUUIDMapper()`/`getStringMapper()`/`getByteArrayMapper()`
   overrides:
   ```java
   @Override
   public ITypeMapper<Boolean> getBooleanMapper() {
       return new PostgresBooleanTypeMapper();
   }
   ```

3. **Unit test** — `nativsql-postgres/src/test/java/.../db/postgres/mapper/PostgresBooleanTypeMapperTest.java`:
   - no `DB_DATA_TYPE` in params → `formatParameter("p", Map.of())` returns `"(:p)::boolean"`
   - `DB_DATA_TYPE = BOOLEAN` → `"(:p)::boolean"`
   - `DB_DATA_TYPE = STRING` → `":p"`
   - `DB_DATA_TYPE = INTEGER` → `":p"`

4. **Integration test** (Testcontainers) — add a narrowly-scoped repository method to the existing
   test repository (same convention as `PostgresOperatorsTest`/`PostgresUserRepository`), e.g.:
   ```java
   public List<User> findByActiveFlagAmbiguous(Boolean active) {
       return findAll("where (:active is null or active = :active)", Map.of("active", active));
   }
   ```
   assert it executes for `active = null`, `true`, `false` — previously threw `PSQLException`.

### `findExternal`/`findAllExternal`

5. **New `NullableParam`** in `nativsql-core/src/main/java/ovh/heraud/nativsql/repository/NullableParam.java`:
   immutable, `private final Class<?> type`, static factory `of(Class<?> type)`, `getType()`. No
   value field — always represents a `null` bind value with a declared type.

6. **New `NamedParamSqlCaster`** in
   `nativsql-core/src/main/java/ovh/heraud/nativsql/repository/NamedParamSqlCaster.java`
   (package-visible, used only by `GenericRepository`):
   - `private final Map<String, String> cache = new ConcurrentHashMap<>();`
   - `String castNamedParameters(String sql, Map<String, Object> params, Fields entityFields,
     DatabaseDialect dialect, AnnotationManager annotationManager)` →
     `cache.computeIfAbsent(sql, s -> rewrite(...))`.
   - `rewrite(...)`: scan `sql` once for `:name` tokens, skipping single-quoted string literals,
     double-quoted identifiers, `--` line comments, and `/* */` block comments (minimal hand-written
     scanner; header comment attributes the algorithm to Spring Framework's `NamedParameterUtils`,
     Apache License 2.0, adapted rather than copied — see spec for the licensing rationale). For
     each distinct name found, resolve type in priority order: `NullableParam` value → matching
     `entityFields` field → non-null runtime value's class → skip. Build a `FieldAccessor` for the
     resolved type, call `dialect.getMapper(fieldAccessor, annotationManager)
     .formatParameter(name, typeInfo.getParams())`; if the result differs from `":" + name`, replace
     every occurrence of `:name` in `sql` with it. Return the original `sql` unchanged if nothing
     matched (avoid allocating a new string needlessly).

7. **`GenericRepository`**:
   - New field `private final NamedParamSqlCaster namedParamSqlCaster = new NamedParamSqlCaster();`
     (same instantiation style as other per-repository collaborators).
   - `findAllExternal(String sql, Map<String, Object> params, Class<EXT> resultClass)`
     (`GenericRepository.java:1882-1886`): call
     `String castSql = namedParamSqlCaster.castNamedParameters(sql, params, entityFields,
     databaseDialect, annotationManager);` before `convertParamsToSqlValues(params)`, and pass
     `castSql` (not `sql`) to `jdbcTemplate.query(...)`.
   - `convertParamsToSqlValues` (`GenericRepository.java:1640`): add a branch — if
     `entry.getValue() instanceof NullableParam`, put `null` in `converted` for that key (skip
     `convertToSqlValue`, since the DB bind value is always `null` regardless of declared type).

8. **Tests**:
   - **Unit** (`nativsql-core/src/test/java/ovh/heraud/nativsql/repository/`): new
     `NamedParamSqlCasterTest`:
     - entity-field match, `null` and non-null values → cast applied both times
     - no match, non-null value → cast applied from runtime class
     - no match, `NullableParam.of(Boolean.class)` → cast applied
     - no match, plain `null`, no wrapper → sql unchanged
     - `:name` inside a string literal → not rewritten
     - second call, same `sql` string → cached (assert first-call shape wins even if a later call's
       `params` differ)
   - **Integration** (`nativsql-postgres/src/test/java/.../PostgresFindExternalBooleanCastTest`):
     a hand-written query mirroring the real `filterServiceToilette`-style pattern
     (`(:filterActive)::boolean is null or ...`, using `NullableParam.of(Boolean.class)` for the
     `null` calls) — executes without `PSQLException` for `null`/`true`/`false`.

### Docs

9. **`CHANGELOG.md`** — add an entry under **[2.12.0]**, referencing
   `doc/issues/118-postgres-boolean-parameter-cast/spec.md`.
10. **`gradle.properties`** — bump `version` to `2.12.0` (per user instruction).

### Generalization beyond `Boolean` (post-original-scope)

The original scope (§ "Design decision" in spec.md, superseded) limited casting to `Boolean` only.
During implementation this was generalized to every PostgreSQL-mapped type, since the underlying
PostgreSQL limitation ("could not determine data type of parameter") is not Boolean-specific. See
spec.md's "Design decision: generated-query fix, generalized to every PostgreSQL-mapped type" for
the rationale.

11. **New `PostgresParameterCasts`** in
    `nativsql-postgres/src/main/java/ovh/heraud/nativsql/db/postgres/mapper/PostgresParameterCasts.java`:
    a `DbDataType → PostgreSQL type name` lookup table (`EnumMap`) plus `cast(paramName, sqlType)`
    (unconditional wrap) and `castForType(paramName, params, naturalSqlType)` (looks up the
    declared `DB_DATA_TYPE`, falls back to `naturalSqlType` when absent or unmapped).

12. **New `PostgresCastingTypeMapper<T>`** in
    `nativsql-postgres/src/main/java/ovh/heraud/nativsql/db/postgres/mapper/PostgresCastingTypeMapper.java`:
    `implements ITypeMapper<T>`, wraps a `delegate` mapper, delegates `map`/`toDatabase` unchanged,
    overrides only `formatParameter` to call `PostgresParameterCasts.castForType`.

13. **`PostgresBooleanTypeMapper`/`PostgresUUIDTypeMapper`**: `formatParameter` bodies simplified to
    one line delegating to `PostgresParameterCasts.castForType` (replaces the old per-mapper
    "cast only if `dataType` matches my own type, else `:paramName` unchanged" logic — now casts to
    whatever type is actually declared).

14. **`PostgresStringTypeMapper`/`PostgresByteArrayTypeMapper`**: add a `formatParameter` override
    (previously inherited the no-cast default) delegating to `PostgresParameterCasts.castForType`
    with natural types `"text"`/`"bytea"`.

15. **`PostgresEnumMapper`/`PostgresCompositeTypeMapper`/`PostgresPointTypeMapper`/
    `PostgreJSONTypeMapper`**: existing/new `formatParameter` bodies switched to call
    `PostgresParameterCasts.cast(paramName, sqlType)` instead of building the string inline.

16. **`PostgresDialect`**: wrap `super.getXxxMapper()` in `new PostgresCastingTypeMapper<>(..., natural)`
    for `Long→"bigint"`, `Integer→"integer"`, `Short`/`Byte→"smallint"`, `Float→"real"`,
    `Double→"double precision"`, `BigDecimal`/`BigInteger→"numeric"`, `LocalDate→"date"`,
    `LocalDateTime→"timestamp"`, `Instant→"timestamptz"`.

17. **`NamedParamSqlCaster.resolveReplacement`**: added a guard — a parameter whose value is a
    `Collection` or array (used in `WHERE col IN (:name)`) is never cast, on any dialect. Found as a
    real regression while generalizing to `Long`: `findAllByIds` builds `id IN (:ids)`, and once
    `Long` started casting, the JDBC template's own `?, ?, ...` list expansion ran on the
    already-rewritten `(:ids)::bigint` text, producing invalid SQL (`IN ((?, ?)::bigint)`).

18. **Tests**:
    - `PostgresDialectFormatParameterTest` (new, `nativsql-postgres`) — parameterized, real
      `PostgresDialect`/`PostgresPostGISDialect`, real `User`/`ContactInfo` entities: every mapped
      type casts to its expected PostgreSQL type name.
    - `MariaDBDialectNoPostgresCastTest` / `OracleDialectNoPostgresCastTest` (new, one per
      non-PostgreSQL dialect module) — same type surface, asserts `formatParameter` stays
      `":p"` (no cast) on those dialects.
    - `PostgresBooleanTypeMapperTest` updated: `STRING`/`INTEGER` `DB_DATA_TYPE` now expect a cast
      to that type (`::text`/`::integer`), not "no cast".
    - `NamedParamSqlCasterTest`: added `castsUuidParameterMatchingEntityField`/
      `castsEnumParameterMatchingEntityField` (genericity beyond Boolean) and
      `doesNotCastListValuedParameterUsedInInClause` (regression test for the `IN`-list bug above).
    - `NamedParamSqlCasterEngineQueriesTest` (new, `nativsql-core`, resource-file-driven
      `@ParameterizedTest`) — cases in `named-param-sql-caster-engine-queries.txt`: per-engine
      quoting/comments (PostgreSQL/MySQL/MariaDB/Oracle), standard SQL constructs (CTE, joins,
      window functions, `INSERT`/`UPDATE`/`DELETE`, `UNION`, `CASE`), and colon-bearing non-param
      constructs (array slices, an existing `::cast` — not doubled up — and a `CAST(...)` call —
      not detected, gets nested, documented as an accepted limitation).
    - `PostgresUserRepositoryLoggingTest`: two logged-SQL assertions updated to reflect casts now
      appearing on `String`/`Instant` columns in generated `INSERT`/`UPDATE` statements.

## Verification

```bash
./gradlew compileJava
./gradlew build
```
Ask before running `./gradlew test` (project convention).

## Log

- Generalized casting from `Boolean`-only to every PostgreSQL-mapped type (steps 11-18 above),
  after the original Boolean-only implementation (steps 1-10) was complete and tested. Found and
  fixed a real regression along the way: `NamedParamSqlCaster` must never cast a list-valued
  parameter (`IN (:ids)`), or JDBC's own named-parameter list expansion produces invalid SQL.
- Added the remaining integration tests (step 4, step 8's integration half) and the doc/version
  edits (steps 9-10). Step 4's `User` entity has no scalar `Boolean` field, so the generated-query
  test uses `ContactInfo.isPrimary` instead (`PostgresContactInfoRepository
  .findByUserIdAndIsPrimary`); the `WhereQuery` builder always emits `expr = :param`, so a truly
  ambiguous *generated* predicate isn't reachable through it today — the test instead confirms no
  `PSQLException` and standard SQL null semantics (`= NULL` matches nothing) for `null`/`true`/`false`.
  The genuinely ambiguous case from the original bug report is covered by the `findExternal` test
  (`PostgresUserRepository.findAllByActiveFlagAmbiguous`, `PostgresFindExternalBooleanCastTest`).
  `./gradlew compileJava compileTestJava` passes; tests not yet run (ask first, per project
  convention).
