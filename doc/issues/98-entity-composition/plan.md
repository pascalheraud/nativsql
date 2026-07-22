# Plan: Augment an entity using FindQuery

> Issue: [nativsql#98](https://github.com/heraud/nativsql/issues/98)

## Context

Read `spec.md` before implementing — it documents the comparison between the composition and
inheritance shapes considered, and why inheritance was chosen (`R extends T`). This plan only
implements the inheritance-based design.

`select(...)` on `FindQuery` already produces plain, unprefixed column aliases that land
correctly on any subtype of `T` because field discovery (`ReflectionUtils.getFields`) walks
the full class hierarchy. The actual gaps are: (1) a way to add a computed SQL-expression
SELECT column, (2) a way to map a query into `R extends T` instead of exactly `T`, and (3)
letting `associate(...)` batch-loading keep working when the mapped type is `R` instead of
`T`.

`select(...)` and `selectExpression(...)` validate against each other for the same
alias/column name: calling both `select("status")` and `selectExpression("status", ...)` on
the same query throws `NativSQLException` at the builder call, instead of letting the
database reject the resulting duplicate-alias SQL later. This does not affect
`selectExpression(alias, ...)` used *alone*, with an `alias` matching a field `R` already
inherits from `T` (e.g. `selectExpression("status", "CASE WHEN ... END")` with no
`select("status")` alongside it) — that remains a supported, intentional way to override an
inherited field's value with a computed one (see "Overriding an inherited field" in
`spec.md`); the collision check only fires when the *same* alias is used by both methods.

---

## Files to modify

### 1 — `FindQuery.java`

**Add a field for expression columns:**

```java
private final List<ExpressionColumn> expressionColumns = new ArrayList<>();
```

New small type in its own file, `util/ExpressionColumn.java` — sibling `Join.java`/`Association.java`
are one-type-per-file, Lombok `@Data`/`@NoArgsConstructor` classes (not records) with a matching
explicit constructor, so `ExpressionColumn` follows the same shape rather than a `record`:

```java
@Data
@NoArgsConstructor
public class ExpressionColumn {
    private String alias;
    private String sql;
    private Map<String, Object> params;

    public ExpressionColumn(String alias, String sql, Map<String, Object> params) {
        this.alias = alias;
        this.sql = sql;
        this.params = new HashMap<>(params);
    }
}
```

**Add `selectExpression` overloads:**

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
    if (columns.contains(alias)) {
        throw new NativSQLException(
                "Expression alias '" + alias + "' collides with a plain select(...) column of the same name");
    }
    expressionColumns.add(new ExpressionColumn(alias, sqlExpression, params));
    return this;
}

public <R> FindQuery<T, ID> selectExpression(Getter<R> aliasGetter, String sqlExpression) {
    return selectExpression(ReflectionUtils.getColumnName(aliasGetter), sqlExpression);
}

public <R> FindQuery<T, ID> selectExpression(Getter<R> aliasGetter, String sqlExpression, Map<String, Object> params) {
    return selectExpression(ReflectionUtils.getColumnName(aliasGetter), sqlExpression, params);
}
```

`ReflectionUtils.getColumnName` is already generic over the getter's declaring type
(`Getter<X>`, used today for `Getter<T>`) — no change needed there, it accepts `Getter<R>`
as-is.

**Update `select(String... cols)`** (`FindQuery.java:97`) with the same check, in the other
direction:

```java
public FindQuery<T, ID> select(String... cols) {
    if (cols == null || cols.length == 0) {
        throw new NativSQLException("Column list cannot be empty");
    }
    for (String col : cols) {
        boolean collidesWithExpression = expressionColumns.stream()
                .anyMatch(e -> e.getAlias().equals(col));
        if (collidesWithExpression) {
            throw new NativSQLException(
                    "Column '" + col + "' collides with a selectExpression(...) alias of the same name");
        }
    }
    columns.addAll(Arrays.asList(cols));
    return this;
}
```

> **Post-implementation correction:** the plan originally had `select(String...)` delegate to a
> parallel `select(List<String> cols)` overload holding the collision check, on the assumption
> that both entry points needed to share it. In review, that `List<String>` overload turned out to
> be dead API — no caller (production or test) ever invoked it with a genuine `List`, since a
> `String[]`/vararg argument is directly assignable to a `String...` parameter with no conversion
> needed. It was removed, and the collision check now lives directly in `select(String... cols)`.
> The same reasoning applied to `associate`/`leftJoin`/`innerJoin` (both `String` and `Getter<T>`
> variants): their `List<String>` overloads were also unused outside self-tests and were removed,
> leaving only the `String...` forms. See "Repository query encapsulation" in
> `.claude/skills/java/SKILL.md` for the resulting convention: `List<String>` is for a query
> builder's *internal* storage only (`FindQuery.columns`, `Association`, `Join`), never for a
> public column-list parameter.

**No read-back accessor was added for `expressionColumns`** (nor kept for `columns`/`joins`/`whereConditions`/association names): a later cleanup pass found `getColumns()`, `getJoins()`,
`getWhereConditions()`, and `getAssociationNames()` were exercised by no code path except a test
written solely to call the getter itself, and removed all of them along with those tests.
`getExpressionColumns()` was likewise never added to production code, so it was left out entirely.
`getTableName()`, `getAssociations()`, and `getParameters()` remain — each has a real caller in
`GenericRepository`/`CountQuery`/`DeleteQuery`/`ExistsQuery`.

**Update `buildPrefixedColumns`** (`FindQuery.java:497-522`) to also emit
`expressionColumns`, substituting the `{{table}}` token with the query's own table name:

```java
for (ExpressionColumn ec : expressionColumns) {
    String resolvedSql = ec.sql().replace("{{table}}", tableName);
    prefixedColumns.add(String.format("%s AS \"%s\"", resolvedSql, ec.alias()));
}
```

`buildPrefixedColumns` already receives `tableName` as a parameter — no new parameter
needed, just extend the loop.

**Guard against an entirely empty SELECT list** in `buildSql` (`FindQuery.java:532`), before
building `prefixedColumns`:

```java
if (columns.isEmpty() && expressionColumns.isEmpty() && joins.isEmpty()) {
    throw new NativSQLException("At least one column must be selected");
}
```

**Override `getParameters()`** (inherited from `WhereQuery`, `WhereQuery.java:163`) to merge
expression params on top of the WHERE-based params, with duplicate detection:

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

**Add `find`/`findAll` overloads bounded by `<R extends T>`**, next to the existing
`protected T find(FindQuery<T, ID> query)` (`GenericRepository.java:1185`) and
`protected List<T> findAll(FindQuery<T, ID> query)` (`GenericRepository.java:1213`):

```java
protected <R extends T> R find(FindQuery<T, ID> query, Class<R> resultClass) {
    String sql = query.buildString(identifierConverter);
    Map<String, Object> params = query.getParameters();
    List<R> results = dbOperationLogger.execute(getClass(), "SELECT", getTableName(), sql, params,
            () -> findAllExternal(sql, params, resultClass));
    R entity = getFirstOrNull(results);
    if (entity != null && query.hasAssociations()) {
        loadAssociationsInBatch(List.of(entity), query.getAssociations());
    }
    return entity;
}

protected <R extends T> List<R> findAll(FindQuery<T, ID> query, Class<R> resultClass) {
    String sql = query.buildString(identifierConverter);
    Map<String, Object> params = query.getParameters();
    return dbOperationLogger.execute(getClass(), "SELECT", getTableName(), sql, params,
            () -> findAllExternal(sql, params, resultClass));
}
```

Mirrors `find(query)`/`findAll(query)` exactly (including `findAll` intentionally *not*
batch-loading associations, same as today, to avoid N+1) — only `entityClass` is replaced
with the caller-supplied `resultClass`. No new validation logic: the `<R extends T>` bound is
enforced by the compiler.

Both stay `protected` — no public passthrough of `FindQuery`. A concrete repository adds its
own named method (see integration test fixtures below for a worked example).

**Widen `loadAssociationsInBatch`'s parameter** (`GenericRepository.java:1427`):

```java
// before:
private void loadAssociationsInBatch(List<T> entities, List<Association> associations)
// after:
private void loadAssociationsInBatch(List<? extends T> entities, List<Association> associations)
```

Private method, no external callers, body unchanged (already only does field-reflection
work) — this is what lets `find(query, resultClass)` reuse it for `List<R>` where
`R extends T`.

---

## Tests

### Unit — `nativsql-core`

#### `FindQuerySelectExpressionTest`

File: `nativsql-core/src/test/java/ovh/heraud/nativsql/util/FindQuerySelectExpressionTest.java`

Same mock-repository setup pattern as `FindQueryTest`.

```java
@Test
void selectExpression_adds_raw_sql_with_alias() {
    // When: .selectExpression("orderCount", "(SELECT COUNT(*) FROM orders WHERE client_id = {{table}}.id)")
    // Then: SQL contains '(SELECT COUNT(*) FROM orders WHERE client_id = client.id) AS "orderCount"'
    //       (assuming the mock repository's table name is "client")
}

@Test
void selectExpression_table_token_substituted_with_actual_table_name() {
    // Given: repository table name "test_entity"
    // When: .selectExpression("x", "{{table}}.status")
    // Then: SQL contains 'test_entity.status AS "x"'
}

@Test
void selectExpression_getter_overload_derives_alias() {
    // When: .selectExpression(SomeReport::getOrderCount, "1+1")
    // Then: SQL contains '1+1 AS "orderCount"'
}

@Test
void selectExpression_with_params_merges_into_getParameters() {
    // When: .selectExpression("recent", "{{table}}.created_at >= :since", Map.of("since", someInstant))
    // Then: getParameters() contains {"since": someInstant}
}

@Test
void selectExpression_blank_alias_throws() { ... }

@Test
void selectExpression_blank_sql_throws() { ... }

@Test
void selectExpression_duplicate_alias_throws() { ... }

@Test
void selectExpression_alias_colliding_with_existing_select_column_throws() {
    // Given: .select("id", "status")
    // When: .selectExpression("status", "...")
    // Then: NativSQLException: "Expression alias 'status' collides with a plain select(...) column of the same name"
}

@Test
void select_column_colliding_with_existing_selectExpression_alias_throws() {
    // Given: .selectExpression("status", "...")
    // When: .select("id", "status")
    // Then: NativSQLException: "Column 'status' collides with a selectExpression(...) alias of the same name"
    //       — same collision caught regardless of call order
}

@Test
void selectExpression_duplicate_param_name_with_where_condition_throws() {
    // Given: .whereAndEquals("status", "ACTIVE") uses param "status"
    // When: .selectExpression("x", "... :status", Map.of("status", "x"))
    // Then: NativSQLException on getParameters()
}

@Test
void buildSql_with_no_columns_expressions_or_joins_throws() { ... }

@Test
void select_and_selectExpression_combine_in_generated_sql() {
    // Given: .select("id", "name") + .selectExpression("total", "...") (no name collision)
    // Then: SQL contains all three columns, in declaration order
}

@Test
void selectExpression_alone_overrides_an_inherited_field_alias() {
    // Given: .select("id") + .selectExpression("status", "CASE WHEN {{table}}.deleted_at IS NOT NULL THEN 'ARCHIVED' ELSE status END")
    //        ("status" NOT passed to select(...) — no collision, so this must NOT throw)
    // Then: SQL contains exactly one 'AS "status"' column, holding the CASE expression —
    //       confirms the override case (selectExpression alone) is distinct from, and does
    //       not trip, the select(...)/selectExpression(...) collision guard above, which only
    //       fires when both target the same alias
}
```

#### `GenericRepositoryFindAsSubtypeTest`

File: `nativsql-core/src/test/java/ovh/heraud/nativsql/repository/GenericRepositoryFindAsSubtypeTest.java`

Use a throwaway `TestEntity implements IEntity<Long>` and a `TestEntityReport extends
TestEntity` with one extra field, plus a `TestRepository extends GenericRepository<TestEntity,
Long>` exposing `find`/`findAll` for the test (same pattern as existing
`GenericRepositoryTest` for accessing protected members).

```java
@Test
void findAll_query_resultClass_maps_inherited_and_computed_fields() {
    // Given: FindQuery with select(...) covering TestEntity's own columns
    //        + selectExpression("extra", "...") for TestEntityReport's own field
    // When: findAll(query, TestEntityReport.class)
    // Then: returned reports have both the inherited fields and "extra" populated
}

@Test
void find_query_resultClass_returns_null_when_no_rows() { ... }

@Test
void find_query_resultClass_loads_associations_when_present() {
    // Given: query.associate(...) on a TestEntity association
    // When: find(query, TestEntityReport.class)
    // Then: the association is populated on the returned TestEntityReport
    //       (verifies the loadAssociationsInBatch(List<? extends T>, ...) widening)
}

@Test
void findAll_query_resultClass_does_not_load_associations() {
    // Given: query.associate(...)
    // When: findAll(query, TestEntityReport.class)
    // Then: association field stays unpopulated — same N+1-avoidance behavior as findAll(query)
}

@Test
void findAll_plain_query_selectExpression_overrides_existing_field() {
    // Given: TestEntity has a "status" field.
    //        FindQuery: .select("id").selectExpression("status", "CASE WHEN {{table}}.deleted_at IS NOT NULL THEN 'ARCHIVED' ELSE status END")
    //        ("status" NOT passed to select(...) — no builder-time collision)
    // When: findAll(query) — the existing method, mapping into TestEntity itself, no resultClass argument
    // Then: returned TestEntity instances have "status" populated from the CASE expression —
    //       confirms selectExpression works on a plain, non-augmented find/findAll as long as
    //       the alias matches a field the mapped class (T here) already has
}

@Test
void findAll_plain_query_selectExpression_with_no_matching_field_throws_at_row_mapping() {
    // Given: TestEntity has no "extra" field.
    //        FindQuery: .select("id").selectExpression("extra", "1")
    // When: findAll(query) — mapping into TestEntity, which has no "extra" field
    // Then: NativSQLException thrown by GenericRowMapper at row-mapping time
    //       ("Property 'extra' not found for in class ..."), not at buildSql/query-construction
    //       time — confirms introducing a genuinely new computed field requires
    //       findAll(query, resultClass) with a resultClass that actually declares it
}
```

---

## Integration tests — every supported DBMS

This feature touches SQL generation (`{{table}}` substitution, expression columns) and row
mapping into a class hierarchy, both of which can behave differently per dialect
(`identifierConverter`, quoting, subquery support). It must be verified against **all four**
supported database modules, not just Postgres: `nativsql-postgres`, `nativsql-mysql`,
`nativsql-mariadb`, `nativsql-oracle`. Follow the existing one-class-per-dialect convention
(see `PostgresExistsQueryTest`/`MariaDBExistsQueryTest`/`OracleExistsQueryTest` for the
pattern) rather than a shared `test-commons` interface — this keeps each dialect's SQL
assertions and Testcontainers/dialect config explicit, consistent with how `ExistsQuery` was
tested for #99.

Each dialect module already has a `User`/`Group`/`ContactInfo` fixture set
(`domain/<dialect>/User.java` has `@MappedBy` `group` and `@OneToMany` `contacts`) and a
`<Dialect>UserRepository`. Reuse them:

### New fixture per dialect: `UserActivityReport extends User`

File: `domain/<dialect>/UserActivityReport.java` (one per module, package-adjusted)

```java
public class UserActivityReport extends User {
    private Long contactCount;
    // getter/setter (or Lombok @Data, matching how the dialect's User.java is written)
}
```

### New repository method per dialect: `<Dialect>UserRepository.findUserActivityReports()`

```java
public List<UserActivityReport> findUserActivityReports() {
    FindQuery<User, Long> query = newFindQuery()
            .select("id", "firstName", "email")
            .selectExpression("contactCount",
                    "(SELECT COUNT(*) FROM contact_info c WHERE c.user_id = {{table}}.id)");
    return findAll(query, UserActivityReport.class);
}

public List<UserActivityReport> findUserActivityReportsWithGroupAndContacts() {
    FindQuery<User, Long> query = newFindQuery()
            .select("id", "firstName", "email")
            .leftJoin("group", "id", "name")
            .selectExpression("contactCount",
                    "(SELECT COUNT(*) FROM contact_info c WHERE c.user_id = {{table}}.id)")
            .associate("contacts", "id", "type");
    return findAll(query, UserActivityReport.class);
}
```

Verify the exact `contact_info` table/column names against the existing
`ContactInfo`/`<Dialect>ContactInfoRepository` fixtures per module before writing the raw
SQL (naming may differ slightly per dialect's `identifierConverter`/table setup — check
`PostgresContactInfoRepository.getTableName()` and equivalents).

### Test classes (one per dialect)

- `nativsql-postgres/src/test/java/ovh/heraud/nativsql/repository/postgres/PostgresUserActivityReportTest.java`
- `nativsql-mysql/src/test/java/ovh/heraud/nativsql/repository/mysql/MySQLUserActivityReportTest.java`
- `nativsql-mariadb/src/test/java/ovh/heraud/nativsql/repository/mariadb/MariaDBUserActivityReportTest.java`
- `nativsql-oracle/src/test/java/ovh/heraud/nativsql/repository/oracle/OracleUserActivityReportTest.java`

Each extends that module's existing repository-test base (Testcontainers-backed, matching
`<Dialect>UserRepositoryTest`'s setup) and runs the same table of scenarios:

| Test | Setup | Expected |
|---|---|---|
| inherited fields + computed subquery | 1 user, 3 contacts | `UserActivityReport` has `id`/`firstName`/`email` from `User` and `contactCount == 3` |
| computed subquery with zero related rows | 1 user, 0 contacts | `contactCount == 0` (query's `COUNT(*)` naturally returns 0, no `COALESCE` needed) |
| `{{table}}` resolves to this dialect's actual table name | — | assert on `findUserActivityReports()`'s generated SQL (via a logging/capture hook if available, or indirectly by confirming correct results — check how `PostgresUserRepositoryLoggingTest` captures SQL and reuse that mechanism if present) |
| combined with `leftJoin` + `associate` | user with a group, 2 contacts | `findUserActivityReportsWithGroupAndContacts()` returns a report with `group` populated (join), `contacts` populated (association), and `contactCount` correct (computed) |
| `find(query, resultClass)` singular variant loads associations | same as above, single expected row | non-list variant also populates `contacts` |
| `findAll(query, resultClass)` does not load associations | same setup | `contacts` stays empty/null on the list variant (N+1-avoidance parity with plain `findAll(query)`) |
| Oracle-specific: subquery + `{{table}}` under the identifier-quoting rules for this dialect | — | same scenarios as above pass unmodified — this is the main risk area for Oracle given its distinct quoting/case-folding behavior; call this out explicitly if `OracleExistsQueryTestBase`/`OracleExistsQueryTest20` split (seen in the existing `nativsql-oracle` tests) hints at version-specific SQL differences that also apply here |

If any dialect's `identifierConverter` or SQL dialect handling makes `{{table}}` substitution
behave differently (e.g. quoting), adjust `FindQuery`'s substitution to use the *converted*
table name consistently with how the rest of `buildSql` already prefixes columns — no
dialect-specific code should be needed since `{{table}}` is replaced with
`repository.getTableName()`, the same value already used everywhere else in the generated
SQL for that table.

**Deviation from this plan as implemented:** `OracleUserActivityReportTest` is a single test
class extending `OracleRepositoryTest` directly, not split into an abstract
`OracleUserActivityReportTestBase` + `OracleUserActivityReportTest`/`OracleUserActivityReportTest20`
pair the way `OracleExistsQueryTest`/`OracleExistsQueryTest20` are. `selectExpression`/`{{table}}`
substitution is plain string substitution done entirely in `FindQuery` before the SQL reaches the
driver — it does not depend on Oracle-version-specific behavior (unlike `EXISTS` — Oracle
23c's native handling vs 20's), so a single-version test class is sufficient coverage here. If a
future Oracle version changes identifier quoting or dual-table SELECT syntax, revisit this.

---

## Documentation

- **CHANGELOG.md** — add entry under the existing `[2.8.0]` heading (no version bump for this
  feature — already confirmed): `selectExpression(...)` (String and `Getter<R>` forms, with
  optional named parameters and a `{{table}}` token) on `FindQuery`, plus
  `find`/`findAll(query, resultClass)` on `GenericRepository` (bounded by `<R extends T>`), to
  map a query into a report class that extends the entity with extra computed SQL-expression
  fields — see `doc/issues/98-entity-composition/spec.md`.
- **ARCHITECTURE.md** — document `FindQuery.selectExpression`, the `{{table}}` substitution,
  and the `<R extends T>` `find`/`findAll` overloads.
- **USERGUIDE.md** — add a "Report classes (entity + computed fields)" section under the
  SELECT/FindQuery documentation, using the `User`/`UserActivityReport`-style example.

---

## Verification

```bash
./gradlew :nativsql-core:test        # FindQuerySelectExpressionTest, GenericRepositoryFindAsSubtypeTest
./gradlew :nativsql-postgres:test    # PostgresUserActivityReportTest + full regression
./gradlew :nativsql-mysql:test       # MySQLUserActivityReportTest + full regression
./gradlew :nativsql-mariadb:test     # MariaDBUserActivityReportTest + full regression
./gradlew :nativsql-oracle:test      # OracleUserActivityReportTest + full regression
./gradlew build                      # full build, all modules
```

All four dialect modules must pass, not just Postgres — this feature's main risk (SQL
generation correctness across dialects) is only actually verified by running the integration
suite in each of them. Existing tests must not regress, in particular `FindQueryTest`,
`FindQueryDotNotationTest` (from #83), and each dialect's `<Dialect>UserRepositoryTest` —
`columns`, `joins`, and `expressionColumns` are independent lists so `select(...)`'s existing
behavior is unchanged.
