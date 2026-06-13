# Plan: DeleteQuery + delete / deleteAll in NativSQL

> Issue: [nativsql#77](https://github.com/heraud/nativsql/issues/77)

## Context

Add property-based deletion to NativSQL via a `DeleteQuery` builder (symmetric to the existing `FindQuery`). Two families of methods:
- `delete(DeleteQuery)` / `deleteByProperty(...)` — exactly 1 tuple, throws `NativSQLException` otherwise
- `deleteAll(DeleteQuery)` / `deleteAllByProperty(...)` — 0 or N tuples, no row count validation

## Files to create

### `nativsql-core/.../util/DeleteQuery.java`

Lightweight copy of `FindQuery`: **WHERE conditions only** — no select, orderBy, associate, or join.

```java
public class DeleteQuery<T extends IEntity<ID>, ID> implements SQLBuilder {
    private final GenericRepository<T, ID> repository;
    private final AnnotationManager annotationManager;
    private final WhereClause whereClause = new WhereClause();

    private DeleteQuery(GenericRepository<T, ID> repository) { ... }

    public static <T extends IEntity<ID>, ID> DeleteQuery<T, ID> of(GenericRepository<T, ID> repository)

    public DeleteQuery<T, ID> whereAndEquals(String column, Object value)
    public DeleteQuery<T, ID> whereAndEquals(Getter<T> getter, Object value)
    public DeleteQuery<T, ID> whereAndIn(String column, List<?> values)
    public DeleteQuery<T, ID> whereAndIn(Getter<T> getter, List<?> values)
    public DeleteQuery<T, ID> whereExpression(String expression, String paramName, Object value)

    private void guardEncryptedColumn(String column)   // exact copy from FindQuery

    @Override public void build(StringBuilder sb, IdentifierConverter ic)
    public String buildString(IdentifierConverter ic)
    public Map<String, Object> getParameters()
    public boolean hasWhereConditions()
}
```

SQL generated (same indentation style as `FindQuery`, constant `INDENT = "    "`):
```sql
DELETE FROM schema.table
WHERE
    col = :col
```

## Files to modify

### `nativsql-core/.../repository/GenericRepository.java`

Add after the existing delete methods:

**Factory:**
```java
protected DeleteQuery<T, ID> newDeleteQuery() {
    return DeleteQuery.of(this);
}
```

**delete — exactly 1 tuple:**
```java
/**
 * Deletes exactly 1 row matching the given query.
 * Throws NativSQLException if 0 or more than 1 row is deleted,
 * causing any active transaction to roll back.
 */
public void delete(DeleteQuery<T, ID> query) {
    String sql = query.buildString(identifierConverter);
    Map<String, Object> params = convertParamsToSqlValues(query.getParameters());
    dbOperationLogger.execute(getClass(), "delete", "DELETE", getTableName(), sql, params, () -> {
        int rowsDeleted = executeUpdate(sql, params);
        if (rowsDeleted != 1) {
            throw new NativSQLException(
                "delete failed: expected to delete exactly 1 row but deleted " + rowsDeleted);
        }
    });
}

/**
 * Deletes exactly 1 row where the given property equals the given value.
 * Throws NativSQLException if 0 or more than 1 row is deleted,
 * causing any active transaction to roll back.
 */
public final void deleteByProperty(Getter<T> getter, Object value) {
    delete(newDeleteQuery().whereAndEquals(getter, value));
}

/** @see #deleteByProperty(Getter, Object) */
public void deleteByProperty(String property, Object value) {
    delete(newDeleteQuery().whereAndEquals(property, value));
}
```

**deleteAll — 0 or N tuples:**
```java
public void deleteAll(DeleteQuery<T, ID> query) {
    String sql = query.buildString(identifierConverter);
    Map<String, Object> params = convertParamsToSqlValues(query.getParameters());
    dbOperationLogger.execute(getClass(), "deleteAll", "DELETE", getTableName(), sql, params,
        () -> executeUpdate(sql, params));
}

public final void deleteAllByProperty(Getter<T> getter, Object value) {
    deleteAll(newDeleteQuery().whereAndEquals(getter, value));
}

public void deleteAllByProperty(String property, Object value) {
    deleteAll(newDeleteQuery().whereAndEquals(property, value));
}
```

## Reused patterns

- `WhereClause` — unchanged, reused as-is
- `FindQuery#guardEncryptedColumn` — copied into `DeleteQuery`
- `newFindQuery()` (line ~1107 of `GenericRepository`) — `newDeleteQuery()` follows the same pattern
- `dbOperationLogger.execute(...)` — same signature as in `deleteById`
- `convertParamsToSqlValues` / `executeUpdate` — called identically

## Documentation — `USERGUIDE.md`

Replace the `### Delete` section (lines 175-180) with:

```markdown
### Delete

```java
// Delete by primary key
userRepository.deleteById(userId);

// Delete exactly 1 tuple by property — throws NativSQLException if 0 or more than 1 row deleted
userRepository.deleteByProperty("email", "john@example.com");
userRepository.deleteByProperty(User::getEmail, "john@example.com");

// Delete exactly 1 tuple via DeleteQuery (multiple conditions)
userRepository.delete(newDeleteQuery()
    .whereAndEquals(User::getTenantId, tenantId)
    .whereAndEquals(User::getEmail, email));

// Delete N tuples by property (no row count validation)
userRepository.deleteAllByProperty("status", UserStatus.INACTIVE);
userRepository.deleteAllByProperty(User::getStatus, UserStatus.INACTIVE);

// Delete N tuples via DeleteQuery
userRepository.deleteAll(newDeleteQuery()
    .whereAndEquals(User::getTenantId, tenantId)
    .whereAndIn(User::getStatus, List.of(UserStatus.INACTIVE, UserStatus.SUSPENDED)));
```
```

## Changelog — `CHANGELOG.md`

Add at the top of the file (before `## [2.4.0]`):

```markdown
## [2.5.0] - 2026-06-12

### Added

- **`DeleteQuery` builder** — new builder symmetric to `FindQuery` for constructing typed `DELETE` statements:
  - `whereAndEquals(String, Object)` / `whereAndEquals(Getter<T>, Object)`
  - `whereAndIn(String, List<?>)` / `whereAndIn(Getter<T>, List<?>)`
  - `whereExpression(String, String, Object)`
- **`delete(DeleteQuery)`** — deletes exactly 1 tuple; throws `NativSQLException` if 0 or more than 1 row is affected
- **`deleteByProperty(String, Object)`** / **`deleteByProperty(Getter<T>, Object)`** — convenience wrappers over `delete(DeleteQuery)`
- **`deleteAll(DeleteQuery)`** — deletes 0 or N tuples with no row count validation
- **`deleteAllByProperty(String, Object)`** / **`deleteAllByProperty(Getter<T>, Object)`** — convenience wrappers over `deleteAll(DeleteQuery)`
- **`newDeleteQuery()`** — protected factory on `GenericRepository` (symmetric to `newFindQuery()`)
```

## Step 3 — Update `ARCHITECTURE.md`

File: `doc/ARCHITECTURE.md`

Add `DeleteQuery` to the utility classes section alongside `FindQuery` — describe its role (WHERE-only builder for DELETE statements) and its relationship to `GenericRepository` (`newDeleteQuery()` factory, `delete()` / `deleteAll()` entry points).

## Tests — `nativsql-postgres` module

Add a new test class `PostgresDeleteQueryTest` extending `PostgresRepositoryTest`, importing `PostgresUserRepository` (the real repository used by all existing postgres tests).

```java
@Import({ PostgresUserRepository.class })
class PostgresDeleteQueryTest extends PostgresRepositoryTest {
    @Autowired
    private PostgresUserRepository userRepository;
    ...
}
```

Cases to cover (each test inserts its own fixture and cleans up):

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

## Verification

```bash
./gradlew :nativsql-postgres:test   # run new and existing postgres tests
./gradlew build                     # full build, all modules
```

Existing tests must not regress.
