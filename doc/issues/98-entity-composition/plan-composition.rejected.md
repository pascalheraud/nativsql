# Plan: Augment an entity by composition using FindQuery

> Issue: [nativsql#98](https://github.com/heraud/nativsql/issues/98)

## Context

`FindQuery<T, ID>` today can only map rows back into `T`. Row mapping itself
(`GenericRowMapper`/`RowMapperFactory`) is already generic and dot-alias-driven (see
`doc/issues/83-where-joined-columns`), so it already supports mapping a dotted alias like
`"client.id"` into a nested field of any POJO, and a plain alias like `"totalAmount"` into a
top-level scalar field of any POJO. The gap is entirely in the `FindQuery` builder: no way to
(a) alias the *main* table's own columns under a property prefix, (b) add a raw SQL
expression column, or (c) run the query against a result class other than `T`.

Read `spec.md` before implementing.

---

## Files to modify

### 1 — `FindQuery.java`

**Add field for expression columns:**

```java
private final List<ExpressionColumn> expressionColumns = new ArrayList<>();
```

New small private record in the same file (or a new file `util/ExpressionColumn.java` if the
project convention is one-type-per-file — check sibling `Join.java` for the pattern and match
it):

```java
record ExpressionColumn(String alias, String sql, Map<String, Object> params) {}
```

**Add `selectAs`:**

```java
public FindQuery<T, ID> selectAs(String propertyName, String... columns) {
    if (propertyName == null || propertyName.isBlank()) {
        throw new NativSQLException("Property name cannot be null or blank");
    }
    if (columns == null || columns.length == 0) {
        throw new NativSQLException("Column list cannot be empty");
    }
    for (String col : columns) {
        selectAsColumns.add(new SelectAsColumn(propertyName, col));
    }
    return this;
}

@SafeVarargs
public final FindQuery<T, ID> selectAs(String propertyName, Getter<T>... getters) {
    return selectAs(propertyName, ReflectionUtils.getColumnNames(getters));
}
```

Mirrors the existing `select(Getter<T>... getters)` overload (`FindQuery.java:110`), which
delegates the same way to `select(String...)` via `ReflectionUtils.getColumnNames`.

Store these in a new `private final List<SelectAsColumn> selectAsColumns = new ArrayList<>();`
(`record SelectAsColumn(String propertyName, String column) {}`), separate from the existing
`columns` list — `columns` keeps its current unprefixed-alias behaviour untouched.

**Add `selectExpression`:**

```java
public FindQuery<T, ID> selectExpression(String alias, String sqlExpression) {
    return selectExpression(alias, sqlExpression, Map.of());
}

public FindQuery<T, ID> selectExpression(String alias, String sqlExpression, Map<String, Object> params) {
    if (alias == null || alias.isBlank()) {
        throw new NativSQLException("Expression alias cannot be null or blank");
    }
    if (sqlExpression == null || sqlExpression.isBlank()) {
        throw new NativSQLException("Expression SQL cannot be null or blank");
    }
    boolean duplicateAlias = expressionColumns.stream().anyMatch(e -> e.alias().equals(alias));
    if (duplicateAlias) {
        throw new NativSQLException("Duplicate expression alias '" + alias + "'");
    }
    expressionColumns.add(new ExpressionColumn(alias, sqlExpression, params));
    return this;
}

public FindQuery<T, ID> selectExpressions(Map<String, String> aliasToSqlExpression) {
    for (Map.Entry<String, String> entry : aliasToSqlExpression.entrySet()) {
        selectExpression(entry.getKey(), entry.getValue());
    }
    return this;
}
```

`selectExpressions` is a bulk convenience only (no per-entry params) — it just loops over
`selectExpression(alias, sql)`, so it inherits the same blank/duplicate-alias validation with
no extra logic.

**Update `buildPrefixedColumns`** (`FindQuery.java:497-522`) to also emit `selectAs` and
`expressionColumns` entries:

```java
for (SelectAsColumn sac : selectAsColumns) {
    if (sac.column() == null || sac.column().isEmpty()) {
        throw new NativSQLException("Column name cannot be null or empty");
    }
    String columnWithAlias = buildColumnExpression(identifierConverter, tableName, sac.column(),
            sac.propertyName(), sac.column());
    prefixedColumns.add(columnWithAlias);
}

for (ExpressionColumn ec : expressionColumns) {
    prefixedColumns.add(String.format("%s AS \"%s\"", ec.sql(), ec.alias()));
}
```

Note this reuses `buildColumnExpression` for `selectAs` exactly as joined columns do — same
call shape as `buildColumnExpression(identifierConverter, joinTableName, col, propertyName, col)`
at `FindQuery.java:515`, just with `tableName` (the main table) instead of `joinTableName`.

**Guard against an entirely empty SELECT list** in `buildSql` (`FindQuery.java:532`), before
building `prefixedColumns`:

```java
if (columns.isEmpty() && selectAsColumns.isEmpty() && expressionColumns.isEmpty() && joins.isEmpty()) {
    throw new NativSQLException("At least one column must be selected");
}
```

**Override `getParameters()`** to merge expression params on top of `WhereQuery`'s WHERE-based
params, with duplicate detection:

```java
@Override
public Map<String, Object> getParameters() {
    Map<String, Object> params = super.getParameters();
    for (ExpressionColumn ec : expressionColumns) {
        for (Map.Entry<String, Object> entry : ec.params().entrySet()) {
            if (params.containsKey(entry.getKey())) {
                throw new NativSQLException("Duplicate parameter name '" + entry.getKey() + "'");
            }
            params.put(entry.getKey(), entry.getValue());
        }
    }
    return params;
}
```

---

### 2 — `GenericRepository.java`

**Add `find`/`findAll` overloads targeting an arbitrary result class**, next to the existing
`protected T find(FindQuery<T, ID> query)` (`GenericRepository.java:1185`) and
`protected List<T> findAll(FindQuery<T, ID> query)` (`GenericRepository.java:1213`):

```java
protected <R> R find(FindQuery<T, ID> query, String alias, Class<R> resultClass) {
    guardComposedResultClass(query, alias, resultClass);
    String sql = query.buildString(identifierConverter);
    Map<String, Object> params = query.getParameters();
    List<R> results = dbOperationLogger.execute(getClass(), "SELECT", getTableName(), sql, params,
            () -> findAllExternal(sql, params, resultClass));
    return getFirstOrNull(results);
}

protected <R> List<R> findAll(FindQuery<T, ID> query, String alias, Class<R> resultClass) {
    guardComposedResultClass(query, alias, resultClass);
    String sql = query.buildString(identifierConverter);
    Map<String, Object> params = query.getParameters();
    return dbOperationLogger.execute(getClass(), "SELECT", getTableName(), sql, params,
            () -> findAllExternal(sql, params, resultClass));
}

private <R> void guardComposedResultClass(FindQuery<T, ID> query, String alias, Class<R> resultClass) {
    if (query.hasAssociations()) {
        throw new NativSQLException(
                "associate(...) is not supported when mapping into a result class other than "
                + "the repository's entity type");
    }
    FieldAccessor<?> field = ReflectionUtils.getFields(resultClass).get(alias);
    if (field == null) {
        throw new NativSQLException(
                "Result class '" + resultClass.getSimpleName() + "' has no field named '" + alias + "'");
    }
    if (!field.getType().isAssignableFrom(entityClass)) {
        throw new NativSQLException(
                "Field '" + alias + "' on '" + resultClass.getSimpleName() + "' has type '"
                + field.getType().getSimpleName() + "', expected '" + entityClass.getSimpleName() + "'");
    }
}
```

Both stay `protected` — no public passthrough of `FindQuery`/result-class mapping. Concrete
repositories add their own named methods (e.g. `findClientReports()`), matching how
`findExternal`/`findAllExternal` are already used today.

`alias` must match the property name used in the query's `selectAs(alias, ...)` call — it is
not derived automatically from the query (a `FindQuery` can have zero or several `selectAs`
groups; the caller states which one is the "primary" composed field being validated).
`guardComposedResultClass` only validates the single `alias` passed in; it does not attempt
to cross-check every `selectAs`/`selectExpression` alias against `resultClass` fields — that
would require exposing the query's internal alias lists, which `FindQuery` does not do
publicly.

`getFirstOrNull` already exists (used by the current `find(FindQuery<T,ID>)` at
`GenericRepository.java:1196`) — reuse it, no change needed there. `ReflectionUtils.getFields`
and `FieldAccessor` already exist and are used the same way by `RowMapperFactory` — no new
reflection helper needed.

---

## Tests

### Unit — `FindQuerySelectAsTest`

File: `nativsql-core/src/test/java/ovh/heraud/nativsql/util/FindQuerySelectAsTest.java`

Same mock-repository setup pattern as `FindQueryTest`.

```java
@Test
void selectAs_aliases_main_table_columns_under_property_prefix() {
    // When: newFindQuery().selectAs("client", "id", "name")
    // Then: SQL contains 'client.id AS "client.id"' and 'client.name AS "client.name"'
}

@Test
void selectAs_with_blank_property_name_throws() { ... }

@Test
void selectAs_with_empty_columns_throws() { ... }

@Test
void selectExpressions_bulk_adds_each_entry_without_params() {
    // When: .selectExpressions(Map.of("a", "1+1", "b", "2+2"))
    // Then: SQL contains both '1+1 AS "a"' and '2+2 AS "b"'
}

@Test
void selectExpression_adds_raw_sql_with_alias() {
    // When: .selectExpression("orderCount", "(SELECT COUNT(*) FROM orders WHERE client_id = client.id)")
    // Then: SQL contains '(SELECT COUNT(*) FROM orders WHERE client_id = client.id) AS "orderCount"'
}

@Test
void selectExpression_with_params_merges_into_getParameters() {
    // When: .selectExpression("recent", "... :since", Map.of("since", someInstant))
    // Then: getParameters() contains {"since": someInstant}
}

@Test
void selectExpression_duplicate_alias_throws() { ... }

@Test
void selectExpression_duplicate_param_name_with_where_condition_throws() {
    // Given: .whereAndEquals("status", "ACTIVE") uses param "status"
    // When: .selectExpression("x", "... :status", Map.of("status", "x"))
    // Then: NativSQLException on getParameters()
}

@Test
void buildSql_with_no_columns_selectAs_expressions_or_joins_throws() { ... }

@Test
void selectAs_combined_with_selectExpression_and_leftJoin_produces_all_three_alias_kinds() {
    // covers the "composed with an existing JOIN" example from spec.md
}
```

### Unit — `GenericRepositoryFindAsResultClassTest`

File: `nativsql-core/src/test/java/ovh/heraud/nativsql/repository/GenericRepositoryFindAsResultClassTest.java`

Use a throwaway `TestRepository extends GenericRepository<TestEntity, Long>` that exposes
`find`/`findAll` for the test (protected methods called from a subclass in the same test, or
via a small public wrapper repository — follow the existing `FindQueryTest`/
`GenericRepositoryTest` pattern for accessing protected members).

```java
@Test
void findAll_query_alias_resultClass_maps_selectAs_and_selectExpression_into_dto() {
    // Given: FindQuery with selectAs("entity", ...) + selectExpression("computed", ...)
    // When: findAll(query, "entity", TestReportDto.class)
    // Then: returned DTOs have populated nested entity field + computed field
}

@Test
void find_query_alias_resultClass_returns_null_when_no_rows() { ... }

@Test
void findAll_query_alias_resultClass_throws_when_query_has_associations() {
    // Given: query.associate(...)
    // When: findAll(query, "entity", SomeDto.class)
    // Then: NativSQLException, query never executed
}

@Test
void findAll_query_alias_resultClass_throws_when_alias_field_missing() {
    // Given: resultClass has no field named "entity"
    // When: findAll(query, "entity", SomeDto.class)
    // Then: NativSQLException mentioning the missing field, query never executed
}

@Test
void findAll_query_alias_resultClass_throws_when_alias_field_type_mismatches_entity() {
    // Given: resultClass has a field "entity" of the wrong type
    // When: findAll(query, "entity", SomeDto.class)
    // Then: NativSQLException mentioning the type mismatch, query never executed
}
```

### Integration — `PostgresCompositionReportTest`

File: `nativsql-postgres/src/test/java/ovh/heraud/nativsql/repository/postgres/PostgresCompositionReportTest.java`

Add a `Client`/`Order` fixture (or reuse an existing entity pair with a one-to-many
relationship, e.g. `User`/whatever order-like table already exists in the Postgres test
fixtures — check `nativsql-postgres/src/test/.../fixtures` first before introducing new
tables/migrations) and a `ClientReport`-shaped DTO.

| Test | Setup | Query | Expected |
|---|---|---|---|
| composition with computed subquery aggregate | 1 client, 3 orders (sum known) | `selectAs("client", "id","name") + selectExpression("totalAmount", subquery)` | DTO has nested `client` populated and correct `totalAmount` |
| composition with zero related rows | 1 client, 0 orders | same query | `totalAmount` reflects `COALESCE(...,0)` / count is `0` |
| composition + existing leftJoin together | client with a group, some orders | `selectAs("client",...) + leftJoin("group",...) + selectExpression(...)` | DTO has `client`, `group`, and computed field all populated |
| selectExpression with bound parameter | orders at various dates | `selectExpression("recentOrderCount", "... :since", params)` | only orders after `:since` counted |

---

## Documentation

- **CHANGELOG.md** — add entry: `selectAs`/`selectExpression`/`selectExpressions` on
  `FindQuery`, plus `find`/`findAll(query, alias, resultClass)` on `GenericRepository`, to map
  a query into a composed DTO embedding the entity with extra computed SQL-expression fields
  — see `doc/issues/98-entity-composition/spec.md`. Ask the user via `AskUserQuestion` whether
  to bump the version number before adding the entry.
- **ARCHITECTURE.md** — document that `GenericRowMapper`'s dotted-alias mapping is
  join-agnostic, and that `selectAs`/`selectExpression` reuse the same alias convention
  without a SQL `JOIN`.
- **USERGUIDE.md** — add a "Composing a result DTO" section under the SELECT/FindQuery
  documentation, using the `Client`/`ClientReport` example from `spec.md`.

---

## Verification

```bash
./gradlew :nativsql-core:test        # FindQuerySelectAsTest, GenericRepositoryFindAsResultClassTest
./gradlew :nativsql-postgres:test    # PostgresCompositionReportTest + full regression
./gradlew build                      # full build, all modules
```

Existing tests must not regress, in particular `FindQueryTest`, `FindQueryDotNotationTest`
(from #83), and any existing `GenericRepository` find/findAll tests — `columns`,
`selectAsColumns`, and `expressionColumns` are independent lists so unprefixed `select(...)`
behaviour is unchanged.
