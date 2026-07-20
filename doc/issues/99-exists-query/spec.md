# Spec: exists() on GenericRepository

> Issue: [nativsql#99](https://github.com/heraud/nativsql/issues/99)

## Goal

Add an `exists(...)` method on `GenericRepository` to test whether at least one row matches a set of WHERE conditions, using a real SQL `EXISTS` (not a `COUNT(*)`) for efficiency:

```java
public boolean hasValidatedUser() {
    return exists(newExistsQuery()
        .whereAndEquals("validated", true));
}
```

---

## Design decision: `AbstractWhereQuery` renamed to `WhereQuery`, `CountQuery` and `ExistsQuery` stay separate

Earlier drafts of this spec considered merging `CountQuery` into a single shared concrete builder reused by both `count()` and `exists()`. That was reverted: a `COUNT(*)` and a `SELECT EXISTS(SELECT 1 ...)` are genuinely different SQL statements with different performance characteristics — `EXISTS` short-circuits on the first match, `COUNT(*)` must scan/count every matching row. Implementing `exists()` as `count(query) > 0` would make every existence check pay the cost of a full count on large tables.

Instead, the existing shape is kept — one concrete builder class per terminal SQL statement (`FindQuery`, `DeleteQuery`, `CountQuery`, and now `ExistsQuery`) — and only the **already select-less abstract base class** is renamed for clarity:

- `AbstractWhereQuery<T, ID, Self>` → **`WhereQuery<T, ID, Self>`**. It already contains only WHERE-clause building (`whereAnd*`/`whereExpression`, encryption guard, parameter collection) and no SELECT/COUNT/EXISTS semantics of its own — the name `AbstractWhereQuery` was accurate but the `Abstract` prefix is dropped since `Where` already conveys "conditions only, no terminal statement".
- `FindQuery`, `DeleteQuery`, `CountQuery` keep their names; they now extend `WhereQuery<T, ID, Self>` instead of `AbstractWhereQuery<T, ID, Self>`.
- New `ExistsQuery<T, ID>` extends `WhereQuery<T, ID, ExistsQuery<T, ID>>`, sibling to `CountQuery`, generating its own `SELECT EXISTS(...)` SQL.

This is a pure rename of the abstract base (mechanical, no behavior change) plus one new sibling class — not a merge.

---

## Architecture

### Dialect problem: Oracle is in the target list

`nativsql-oracle` is a supported dialect module. `SELECT EXISTS(SELECT 1 FROM ...)` as a top-level statement is **not valid Oracle SQL**: Oracle has no boolean-returning scalar `EXISTS` outside a `WHERE`/`CASE` expression, and every Oracle `SELECT` requires a `FROM` clause (no bare `SELECT <expr>`). Postgres and MariaDB both accept the bare `SELECT EXISTS(...)` form directly.

This is the **first dialect-specific SQL generation** needed by the query builders — `FindQuery`'s `FETCH FIRST ... ROWS ONLY` / `OFFSET ... ROWS` (SQL:2008) happens to already be common syntax across Postgres, MariaDB, and Oracle 12c+, so no builder so far has needed to branch on dialect.

Chosen approach: `DatabaseDialect` gets one new method to produce the dialect-correct EXISTS wrapper:

```java
// DatabaseDialect.java
/**
 * Wraps an inner "SELECT 1 FROM table [WHERE ...]" fragment into a statement
 * that returns a single row indicating whether any row matched.
 */
String buildExistsQuery(String innerSelectOne);

/**
 * Extracts the boolean result from the raw JDBC scalar returned by
 * the statement built by buildExistsQuery (Boolean on Postgres/MariaDB,
 * Integer 1/0 on Oracle).
 */
boolean extractExistsResult(Object rawResult);
```

- **Generic / Postgres / MariaDB** (`GenericDialect`, inherited by `PostgresDialect`/`MariaDBDialect` via `AbstractChainedDialect` unless overridden): `buildExistsQuery` → `"SELECT EXISTS(" + innerSelectOne + ")"`; `extractExistsResult` → `(Boolean) rawResult`
- **Oracle** (`OracleDialect`, overrides both): `buildExistsQuery` → `"SELECT CASE WHEN EXISTS(" + innerSelectOne + ") THEN 1 ELSE 0 END FROM dual"`; `extractExistsResult` → `((Number) rawResult).intValue() != 0`

Keeping both dialect-specific pieces (SQL shape *and* result extraction) behind the same `DatabaseDialect` methods keeps `GenericRepository.exists(...)` dialect-agnostic — it just calls `queryForObject(sql, params, Object.class)` and hands the raw result to `extractExistsResult`.

`ExistsQuery.build()` generates only the inner fragment (`SELECT 1 FROM table [WHERE ...]`, no dialect wrapping — same shape as `CountQuery`'s `SELECT COUNT(*) FROM ...`); `GenericRepository.exists(...)` calls `databaseDialect.buildExistsQuery(...)` to get the final statement, mirroring how `getGeneratedKey`/`getMapper` are already dialect calls made from the repository layer, not from the query builders themselves.

### Data flow

```
newExistsQuery().whereAndEquals("validated", true)
  → ExistsQuery.buildString(identifierConverter)
      → "SELECT 1 FROM schema.users\nWHERE\n    validated = :validated\n"
  → databaseDialect.buildExistsQuery(innerSql)
      → Postgres/MariaDB: "SELECT EXISTS(SELECT 1 FROM schema.users WHERE validated = :validated)"
      → Oracle:            "SELECT CASE WHEN EXISTS(SELECT 1 FROM schema.users WHERE validated = :validated) THEN 1 ELSE 0 END FROM dual"
  → convertParamsToSqlValues({validated: true})
  → jdbcTemplate.queryForObject(sql, params, Object.class)  → raw Boolean (PG/MariaDB) or Number (Oracle)
  → databaseDialect.extractExistsResult(raw)  → boolean
```

### Key principles

- `ExistsQuery` holds only a `WhereClause` — no SELECT columns, no ORDER BY, no JOINs, no LIMIT/OFFSET — same shape as `CountQuery`, both siblings under `WhereQuery`. It stays dialect-agnostic; it only ever emits the inner `SELECT 1 FROM ... WHERE ...` fragment.
- `exists(ExistsQuery)` executes a genuine `EXISTS`-based statement (dialect-wrapped), not `count(...) > 0` — it short-circuits on the first match instead of counting every matching row.
- `newExistsQuery()` and `exists(ExistsQuery)` are `protected` — same visibility as `newCountQuery()`/`count(CountQuery)`; only convenience wrappers (`existsAny`, `existsByProperty`) are `public`.
- Result mapping (`Boolean` vs `Integer != 0`) is a repository-layer concern driven by dialect, exactly like `getGeneratedKey`'s Oracle-specific uppercase-column handling already is.

---

## API

### `WhereQuery<T, ID, Self>` (renamed from `AbstractWhereQuery`)

No API change — same methods, same behavior, only the class name changes. All existing subclasses (`FindQuery`, `DeleteQuery`, `CountQuery`) are updated to extend it.

### `ExistsQuery<T, ID>` (new)

```java
// Factory
ExistsQuery.of(repository)

// WHERE conditions (inherited from WhereQuery)
.whereAndEquals(String column, Object value)
.whereAndEquals(Getter<T> getter, Object value)
.whereAndIn(String column, List<?> values)
.whereAndIn(Getter<T> getter, List<?> values)
.whereAndOperator(String column, Operator operator, Object value)
.whereAndColumnOperator(String column, ColumnOperator operator)
.whereAndRange(String column, RangeOperator operator, Object low, Object high)
.whereExpression(String expression, String paramName, Object value)

// SQLBuilder
.buildString(IdentifierConverter)
.build(StringBuilder, IdentifierConverter)

// Parameters
.getParameters()         → Map<String, Object>
.hasWhereConditions()    → boolean
```

SQL generated:
```sql
SELECT EXISTS(SELECT 1 FROM schema.table
WHERE
    col = :col
)
```

(no `WHERE` clause when there are no conditions — `SELECT EXISTS(SELECT 1 FROM schema.table)`)

### `GenericRepository` additions

```java
// Factory
protected ExistsQuery<T, ID> newExistsQuery()

/**
 * Tests whether at least one row matches the given query's WHERE conditions.
 * Uses a dialect-specific EXISTS statement — short-circuits on the first
 * match, unlike count().
 */
protected boolean exists(ExistsQuery<T, ID> query)

/**
 * Tests whether at least one row exists in the table (no WHERE conditions).
 */
public boolean existsAny()

/**
 * Tests whether at least one row exists where the given property equals the given value.
 */
public final boolean existsByProperty(Getter<T> getter, Object value)
public boolean existsByProperty(String property, Object value)
```

---

## Error handling

| Situation | Method | Behaviour |
|---|---|---|
| 0 rows match | `exists(ExistsQuery)` | returns `false`, no exception |
| ≥1 row matches | `exists(ExistsQuery)` | returns `true` |
| WHERE on one-way/non-deterministic encrypted column | any `whereAnd*` call | `NativSQLException` from inherited `guardEncryptedColumn` |

---

## Patterns reused

| Pattern | Source |
|---|---|
| `WhereQuery` (WHERE conditions, encryption guard, parameter collection) | renamed from `AbstractWhereQuery`, reused as-is by `FindQuery`/`DeleteQuery`/`CountQuery`/`ExistsQuery` |
| `newCountQuery()` / `count(CountQuery)` | mirrored for `newExistsQuery()` / `exists(ExistsQuery)` |
| `dbOperationLogger.execute(...)` | same signature as in `count`, operation label `"SELECT"` |
| `convertParamsToSqlValues` | called identically to `count` |
| `jdbcTemplate.queryForObject(sql, params, Object.class)` | new usage, mirrors `queryForObject(sql, params, Long.class)` in `count()` but untyped since the raw JDBC type differs by dialect |
| `DatabaseDialect` (per-module `PostgresDialect`/`MariaDBDialect`/`OracleDialect`, `AbstractChainedDialect`) | extended with `buildExistsQuery`/`extractExistsResult`; same mechanism already used for `getGeneratedKey` (Oracle uppercase-column quirk) and `getMapper` |
| No-builder-leak rule | `ExistsQuery` (like `FindQuery`/`DeleteQuery`/`CountQuery`) must never be exposed via a public repository passthrough — test repositories need named wrapper methods |
