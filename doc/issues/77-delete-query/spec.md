# Spec: DeleteQuery + delete / deleteAll

> Issue: [nativsql#77](https://github.com/heraud/nativsql/issues/77)

## Goal

Introduce a `DeleteQuery` builder — symmetric to `FindQuery` — to construct typed `DELETE` statements with WHERE conditions. Expose two families of methods on `GenericRepository`:

- **`delete(DeleteQuery)`** / **`deleteByProperty(...)`** — exactly 1 tuple; throws `NativSQLException` otherwise
- **`deleteAll(DeleteQuery)`** / **`deleteAllByProperty(...)`** — 0 or N tuples; no row count validation

---

## Architecture

### Data flow

```
deleteByProperty(User::getEmail, "john@example.com")
  → newDeleteQuery().whereAndEquals(User::getEmail, "john@example.com")
  → DeleteQuery.buildString(identifierConverter)
      → "DELETE FROM schema.users\nWHERE\n    email = :email\n"
  → convertParamsToSqlValues({email: "john@example.com"})
      → TypeMapper pipeline (handles enums, composites, etc.)
  → executeUpdate(sql, params)  → int rowsDeleted
  → assert rowsDeleted == 1     → NativSQLException if not
```

### Key principles

- `DeleteQuery` holds only a `WhereClause` — no SELECT columns, no ORDER BY, no JOINs, no associations.
- The guard against one-way / non-deterministic encrypted columns (`guardEncryptedColumn`) is copied from `FindQuery` and applied identically.
- `delete(DeleteQuery)` enforces exactly 1 deleted row; `deleteAll(DeleteQuery)` does not.
- `deleteByProperty` / `deleteAllByProperty` are pure convenience wrappers; they do not duplicate logic.

---

## API

### `DeleteQuery<T, ID>`

```java
// Factory
DeleteQuery.of(repository)

// WHERE conditions (same as FindQuery)
.whereAndEquals(String column, Object value)
.whereAndEquals(Getter<T> getter, Object value)
.whereAndIn(String column, List<?> values)
.whereAndIn(Getter<T> getter, List<?> values)
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
DELETE FROM schema.table
WHERE
    col = :col
```

### `GenericRepository` additions

```java
// Factory
protected DeleteQuery<T, ID> newDeleteQuery()

/**
 * Deletes exactly 1 row matching the given query.
 * Throws NativSQLException if 0 or more than 1 row is deleted,
 * causing any active transaction to roll back.
 */
public void delete(DeleteQuery<T, ID> query)

/**
 * Deletes exactly 1 row where the given property equals the given value.
 * Throws NativSQLException if 0 or more than 1 row is deleted,
 * causing any active transaction to roll back.
 */
public final void deleteByProperty(Getter<T> getter, Object value)
public void deleteByProperty(String property, Object value)

// 0 or N tuples — no row count validation, never throws for count
public void deleteAll(DeleteQuery<T, ID> query)
public final void deleteAllByProperty(Getter<T> getter, Object value)
public void deleteAllByProperty(String property, Object value)
```

---

## Implementation steps

### ⏳ Step 1 — `DeleteQuery.java`

New file: `nativsql-core/src/main/java/ovh/heraud/nativsql/util/DeleteQuery.java`

- Implements `SQLBuilder`
- Private constructor + static `of(GenericRepository)` factory
- Delegates WHERE conditions to the existing `WhereClause`
- `guardEncryptedColumn(String column)` — exact copy from `FindQuery`
- `buildString` / `build` generate `DELETE FROM … WHERE …` with same indentation style as `FindQuery` (`INDENT = "    "`)
- `getParameters()` collects values from `WhereClause` conditions

### ⏳ Step 2 — `GenericRepository` additions

File: `nativsql-core/src/main/java/ovh/heraud/nativsql/repository/GenericRepository.java`

Add after existing delete methods:

1. `newDeleteQuery()` — protected factory, mirrors `newFindQuery()`
2. `delete(DeleteQuery)` — builds SQL, converts params, executes, asserts `rowsDeleted == 1`
3. `deleteByProperty(String, Object)` / `deleteByProperty(Getter<T>, Object)`
4. `deleteAll(DeleteQuery)` — same as `delete` without row count assertion
5. `deleteAllByProperty(String, Object)` / `deleteAllByProperty(Getter<T>, Object)`

### ⏳ Step 3 — Integration tests (`nativsql-postgres` module)

New class: `nativsql-postgres/src/test/.../repository/postgres/PostgresDeleteQueryTest.java`

Extends `PostgresRepositoryTest`, imports `PostgresUserRepository` (the real repository backed by the Testcontainers PostgreSQL instance used by all existing postgres tests).

```java
@Import({ PostgresUserRepository.class })
class PostgresDeleteQueryTest extends PostgresRepositoryTest { ... }
```

Cases:

| Test | Method | Expected |
|---|---|---|
| `deleteByProperty` deletes exactly 1 matching row | `deleteByProperty("email", value)` | row gone, no exception |
| `deleteByProperty` with getter reference | `deleteByProperty(User::getEmail, value)` | row gone, no exception |
| `deleteByProperty` — 0 rows → exception | `deleteByProperty("email", "unknown@x.com")` | `NativSQLException` |
| `deleteByProperty` — N rows → exception | insert 2 users with same status, `deleteByProperty("status", ACTIVE)` | `NativSQLException` |
| `delete(DeleteQuery)` with two conditions | `delete(newDeleteQuery().whereAndEquals(...).whereAndEquals(...))` | row gone |
| `deleteAllByProperty` — 0 rows, no exception | `deleteAllByProperty("email", "unknown@x.com")` | no exception |
| `deleteAllByProperty` — N rows deleted | insert 3 users with same status, `deleteAllByProperty(User::getStatus, INACTIVE)` | all 3 gone |
| `deleteAll(DeleteQuery)` with `whereAndIn` | `deleteAll(newDeleteQuery().whereAndIn(User::getStatus, List.of(...)))` | matching rows gone |

### ⏳ Step 4 — Documentation

- **`USERGUIDE.md`** — replace `### Delete` section (lines 175-180) with full examples covering `deleteById`, `deleteByProperty`, `delete(DeleteQuery)`, `deleteAllByProperty`, `deleteAll(DeleteQuery)`
- **`CHANGELOG.md`** — add `## [2.5.0]` entry before `## [2.4.0]`

---

## Error handling

| Situation | Method | Behaviour |
|---|---|---|
| 0 rows deleted | `delete(DeleteQuery)` | `NativSQLException` — rolls back any active transaction |
| N > 1 rows deleted | `delete(DeleteQuery)` | `NativSQLException` — rolls back any active transaction |
| WHERE on one-way encrypted column | any | `NativSQLException` from `guardEncryptedColumn` |
| WHERE on non-deterministic encrypted column | any | `NativSQLException` from `guardEncryptedColumn` |

`NativSQLException` is a `RuntimeException` — Spring rolls back any active `@Transactional` context automatically on both the 0-row and the N-row cases.

`deleteAll(DeleteQuery)` never throws for row count (0 or N are both valid).

---

## Patterns reused

| Pattern | Source |
|---|---|
| `WhereClause` + `Operator` | unchanged, reused as-is |
| `guardEncryptedColumn` | copied from `FindQuery` |
| `newFindQuery()` factory | mirrored for `newDeleteQuery()` |
| `dbOperationLogger.execute(...)` | same signature as in `deleteById` |
| `convertParamsToSqlValues` / `executeUpdate` | called identically |
