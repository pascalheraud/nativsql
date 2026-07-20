# Plan: exists() on GenericRepository

> Issue: [nativsql#99](https://github.com/heraud/nativsql/issues/99)
> See [spec.md](spec.md) for the full design rationale (base class rename, `ExistsQuery` as its own sibling to `CountQuery`, Oracle-specific SQL).

## Step 1 — Rename `AbstractWhereQuery` → `WhereQuery`

File: rename `nativsql-core/src/main/java/ovh/heraud/nativsql/util/AbstractWhereQuery.java` → `WhereQuery.java`

- Rename class `AbstractWhereQuery<T, ID, Self>` → `WhereQuery<T, ID, Self>`, keep it `abstract`, keep `implements SQLBuilder`
- No other change — all methods, javadoc, encryption guard stay as-is
- Update `FindQuery`, `DeleteQuery`, `CountQuery` to extend `WhereQuery<T, ID, Self>` instead of `AbstractWhereQuery<T, ID, Self>` — mechanical, no behavior change

## Step 2 — Add `DatabaseDialect.buildExistsQuery` / `extractExistsResult`

File: `nativsql-core/src/main/java/ovh/heraud/nativsql/db/DatabaseDialect.java`

```java
/**
 * Wraps an inner "SELECT 1 FROM table [WHERE ...]" fragment into a statement
 * that returns a single row indicating whether any row matched.
 */
String buildExistsQuery(String innerSelectOne);

/**
 * Extracts the boolean result from the raw JDBC scalar returned by the
 * statement built by buildExistsQuery.
 */
boolean extractExistsResult(Object rawResult);
```

- `GenericDialect` (`nativsql-core/.../db/generic/GenericDialect.java`) — default implementation:
  - `buildExistsQuery` → `"SELECT EXISTS(" + innerSelectOne + ")"`
  - `extractExistsResult` → handles both `Boolean` (Postgres JDBC driver) and `Number` (MariaDB JDBC driver returns `Integer` 1/0 for `SELECT EXISTS(...)`, confirmed via integration test failure) — `instanceof Boolean` check first, else `((Number) rawResult).intValue() != 0`
- Check whether `PostgresDialect` / `MariaDBDialect` need to override (confirmed not needed — they chain to `GenericDialect` via `AbstractChainedDialect` and the generic SQL is valid on both; only `extractExistsResult`'s raw-type handling differs, now handled generically)
- `OracleDialect` (`nativsql-oracle/.../db/oracle/OracleDialect.java`) — override both:
  - `buildExistsQuery` → `"SELECT CASE WHEN EXISTS(" + innerSelectOne + ") THEN 1 ELSE 0 END FROM dual"`
  - `extractExistsResult` → `((Number) rawResult).intValue() != 0`

## Step 3 — `ExistsQuery.java` (new)

New file: `nativsql-core/src/main/java/ovh/heraud/nativsql/util/ExistsQuery.java`

- Extends `WhereQuery<T, ID, ExistsQuery<T, ID>>`, mirroring `CountQuery`
- Private constructor + static `of(GenericRepository)` factory
- `build(StringBuilder, IdentifierConverter)` generates only the inner fragment: `SELECT 1 FROM <table>` plus optional `\nWHERE\n...` — no dialect wrapping here (dialect wrapping happens in `GenericRepository.exists(...)`, per spec)
- `buildString(IdentifierConverter)` convenience wrapper
- `getParameters()` inherited as-is from `WhereQuery`

## Step 4 — `GenericRepository` additions

File: `nativsql-core/src/main/java/ovh/heraud/nativsql/repository/GenericRepository.java`

1. `newExistsQuery()` — protected factory, mirrors `newCountQuery()`, near line ~1207
2. `exists(ExistsQuery<T, ID> query)` — protected:
   ```java
   protected boolean exists(ExistsQuery<T, ID> query) {
       String innerSql = query.buildString(identifierConverter);
       String sql = databaseDialect.buildExistsQuery(innerSql);
       Map<String, Object> params = convertParamsToSqlValues(query.getParameters());
       Object raw = dbOperationLogger.execute(getClass(), "exists", "SELECT", getTableName(), sql, params,
               () -> jdbcTemplate.queryForObject(sql, params, Object.class));
       return databaseDialect.extractExistsResult(raw);
   }
   ```
3. `existsAny()` — `public`, pure convenience wrapper: `return exists(newExistsQuery());`
4. `existsByProperty(Getter<T>, Object)` / `existsByProperty(String, Object)` — `public`, mirroring `countByProperty`: `return exists(newExistsQuery().whereAndEquals(getter/property, value));`

## Step 5 — Integration tests

### Postgres / MariaDB

New file: `nativsql-postgres/src/test/java/ovh/heraud/nativsql/repository/postgres/PostgresExistsQueryTest.java`, `@Import({ PostgresUserRepository.class })`, extends `PostgresRepositoryTest`.

Add to `PostgresUserRepository`, mirroring `countByStatuses`/`countByEmailAndStatus` (no-builder-leak rule — `ExistsQuery` must not leak through a public passthrough):
- `existsByStatuses(List<UserStatus>)` — `whereAndIn`
- `existsByEmailAndStatus(String, UserStatus)` — two `whereAndEquals` conditions

| Test | Method | Expected |
|---|---|---|
| `existsAny()` on non-empty table | `existsAny()` | `true` |
| `existsByProperty` getter reference, matching row | `existsByProperty(User::getEmail, ...)` | `true` |
| `existsByProperty` string property, matching row | `existsByProperty("email", ...)` | `true` |
| `existsByProperty` no matching rows | `existsByProperty("email", "unknown@x.com")` | `false` |
| `existsByStatuses` (`whereAndIn`) | matching / non-matching fixture | `true` / `false` |
| `existsByEmailAndStatus` (two AND conditions) | matching / non-matching fixture | `true` / `false` |
| WHERE on one-way/non-deterministic encrypted column | `existsByProperty(encryptedColumn, value)` | `NativSQLException` (inherited guard) |

Repeat the equivalent scenarios in the `nativsql-mariadb` test module (mirrors how `nativsql-postgres`/`nativsql-mariadb` already duplicate the `FindQuery`/`CountQuery` test suites — confirm exact existing pattern before writing).

> **Deviation (confirmed during implementation):** there is no existing `MariaDBCountQueryTest` — MariaDB has no dedicated `CountQuery` test suite at all today (`countAll`/`countByProperty` are untested there), so the assumption that Postgres/MariaDB "already duplicate the CountQuery test suite" did not hold. `MariaDBExistsQueryTest` was still added, mirroring `PostgresExistsQueryTest`'s structure directly (it's the most direct fulfillment of "integration tests for Postgres, MariaDB, and Oracle" and establishes the pattern `CountQuery` never got). The one Postgres-only scenario — WHERE on a one-way/non-deterministic encrypted column throwing `NativSQLException` — was *not* mirrored in MariaDB or Oracle: neither module's test `User` entity has an encrypted field, so there is no fixture to exercise that guard there (same gap pre-existing for `CountQuery`/`FindQuery`).

### Oracle — run against every supported Oracle version

`OracleBaseRepositoryTest.getDatabaseVersion()` defaults to `"23"` (oracle-free, `SELECT CASE WHEN EXISTS(...) THEN 1 ELSE 0 END FROM dual` runs there) but the codebase separately exercises `"20"` (oracle-xe) via `Oracle20DataTypeTest` overriding `getDatabaseVersion()` — this is the established pattern for "must pass on every Oracle version we claim to support," since 20 and 23 differ enough (23c added native SQL `BOOLEAN`, JSON improvements, etc.) that a single version isn't sufficient proof. The `dual`/`CASE WHEN` shape is standard across both, but Oracle 23c makes it tempting to instead emit a native-boolean statement later — the version matrix is what would catch that regression.

New file: `nativsql-oracle/src/test/java/ovh/heraud/nativsql/repository/oracle/OracleExistsQueryTest.java` (path/package to confirm against existing `OracleUserRepositoryTest` location), with equivalent wrapper methods added to `OracleUserRepository`, extends `OracleRepositoryTest` (default → Oracle 23).

New file: `OracleExistsQueryTest20.java` (or equivalent override), same test bodies, overriding `getDatabaseVersion()` → `"20"`, mirroring how `Oracle20DataTypeTest` overrides it for the data-type suite — confirm the exact naming/sharing convention used there (e.g. shared abstract test base + two thin subclasses picking the version) before duplicating test bodies wholesale.

> **Confirmed pattern:** `Oracle20DataTypeTest`/`OracleDataTypeTest` share bodies via an `IDataTypeTests` interface with default methods, which didn't fit a plain WHERE-only test suite. Implemented instead as `OracleExistsQueryTestBase` (package-private `abstract class`, no `@Import`, holds all `@Test` methods) with two thin concrete subclasses `OracleExistsQueryTest` (default version 23) and `OracleExistsQueryTest20` (overrides `getDatabaseVersion()`/`getScriptPath()` → `"20"` / `test-schema-oracle-20-init.sql`), each carrying its own `@Import({ OracleUserRepository.class })` — matching the observation that `OracleDataTypeTest`/`Oracle20DataTypeTest` each redeclare `@Import` themselves rather than relying on inheritance of Spring annotations.
>
> **Bug found and fixed while running the suite:** the shared `@Autowired OracleUserRepository userRepository` field must live in each concrete subclass, not in `OracleExistsQueryTestBase`. `BaseRepositoryTest.injectDataSourceToRepositories()` uses `this.getClass().getDeclaredFields()`, which only returns fields declared directly on the runtime class — an `@Autowired` field declared on an abstract superclass never gets its `DataSource` injected, so `GenericRepository.jdbcTemplate` stays `null` and every DB call throws `NullPointerException`. Fixed by turning the base class's field into an abstract `getUserRepository()` accessor, with each subclass declaring its own `@Autowired OracleUserRepository userRepository` field and implementing the accessor — the same shape `IDataTypeTests` implementors already use for this reason. Any future shared Oracle (or other dialect) test base class using this abstract-base pattern must follow the same accessor convention.

Oracle-specific case to add on both versions:
- Confirm the generated `SELECT CASE WHEN EXISTS(...) THEN 1 ELSE 0 END FROM dual` round-trips correctly through `extractExistsResult` for both `true` and `false` — this is the one path with no Postgres/MariaDB equivalent, so it needs its own explicit assertion rather than relying on the shared test table.

### Postgres / MariaDB — supported versions

Check whether `PostgresBaseRepositoryTest`/`MariaDBBaseRepositoryTest` have any existing multi-version test classes (none found for `CountQuery`/`FindQuery` today — they currently run on each module's single default `getDatabaseVersion()`). If the project's supported-version matrix for Postgres/MariaDB is documented anywhere (README, CI matrix, or `CLAUDE.md`), match `ExistsQueryTest` coverage to whatever `CountQueryTest` already covers there — do not introduce a new multi-version pattern for `exists()` alone if `count()` doesn't already have one.

## Step 6 — Documentation

- **`USERGUIDE.md`** — add an `### Exists` section (near `### Count`) with `existsAny`, `existsByProperty`, and a custom `ExistsQuery` example; mention the Oracle SQL difference only if `USERGUIDE.md` already documents other Oracle-specific SQL shapes (check `FindQuery`'s `FETCH FIRST` section for precedent)
- **`.claude/skills/tests/SKILL.md`** — add `newExistsQuery()`/`ExistsQuery` to the no-builder-leak example list alongside `FindQuery`/`DeleteQuery`/`CountQuery`
- **`CHANGELOG.md`** — add an entry for `exists()` and the `AbstractWhereQuery` → `WhereQuery` rename. **Deviation:** the launching task explicitly directed staying on version `2.8.0` (entry added under the existing `## [2.8.0]` section's new `### Added` subsection) instead of asking via `AskUserQuestion`.

## Verification

- `./gradlew :nativsql-core:compileJava :nativsql-postgres:compileTestJava :nativsql-mariadb:compileTestJava :nativsql-oracle:compileTestJava` — confirms the `WhereQuery` rename and new dialect methods compile everywhere
- Ask before running the integration test suites (Postgres/MariaDB/Oracle `ExistsQueryTest` classes), per stored preference — Oracle tests in particular need a real/Testcontainers Oracle instance to catch `dual`/`CASE WHEN` mistakes that unit compilation won't
