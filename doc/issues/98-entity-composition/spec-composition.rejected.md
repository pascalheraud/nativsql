# Spec: Augment an entity by composition using FindQuery — REJECTED OPTION

> Issue: [nativsql#98](https://github.com/heraud/nativsql/issues/98)

> This is a discarded design option, not the spec for issue #98. The chosen approach
> (inheritance) was written up separately after this one was explored and set aside. Kept
> only in case this direction needs to be picked up later.

## Goal

Allow a `FindQuery<T, ID>` built against an entity's repository to populate a **composed
result class** — a plain DTO that embeds the entity under a named field, plus extra
"computed" fields backed by arbitrary SQL expressions (including subqueries) — instead of
being restricted to mapping rows back into `T`.

Example:

```java
public class Client implements IEntity<Long> {
    private Long id;
    private String name;
    private String email;
    // getters/setters
}

public class ClientReport {
    private Client client;
    private BigDecimal totalAmount;
    private Long orderCount;
    // getters/setters
}
```

```java
FindQuery<Client, Long> query = newAugmentedFindQuery(ClientReport::getClient, ClientReport.class)
        .selectAs("id", "name", "email")
        .selectExpressions(Map.of(
                "totalAmount", "(SELECT COALESCE(SUM(o.amount), 0) FROM orders o WHERE o.client_id = client.id)",
                "orderCount", "(SELECT COUNT(*) FROM orders o WHERE o.client_id = client.id)"));

List<ClientReport> reports = findAll(query, ClientReport.class);
```

```sql
SELECT
    client.id AS "client.id",
    client.name AS "client.name",
    client.email AS "client.email",
    (SELECT COALESCE(SUM(o.amount), 0) FROM orders o WHERE o.client_id = client.id) AS "totalAmount",
    (SELECT COUNT(*) FROM orders o WHERE o.client_id = client.id) AS "orderCount"
FROM client
```

---

## Why this works with minimal new machinery

Row-to-object mapping (`GenericRowMapper` / `RowMapperFactory`) is already generic and
recursive: any result-set column label containing a dot (`"client.id"`) is treated as a
path into a nested property, and any label without a dot (`"totalAmount"`) is mapped as a
plain scalar field — see `doc/issues/83-where-joined-columns` for how the dotted-alias
convention was introduced for JOINs. `GenericRowMapper` does not care whether the dotted
prefix came from a SQL `JOIN` or from the query's own table aliased under a property name.
It also does not require the target class to be `T`, or to be entity-annotated — any POJO
with a no-arg constructor and matching field names works, and `findExternal`/
`findAllExternal` on `GenericRepository` already map into an arbitrary `Class<EXT>`.

So the only real gap is on the `FindQuery` **builder** side:

1. A way to select the *main table's own* columns aliased under a property-name prefix
   (`"client.id"`) — reusing the exact alias mechanism `leftJoin`/`innerJoin` already use
   for joined tables, but without requiring a `@MappedBy` association or a SQL `JOIN`.
2. A way to add a raw SQL expression (optionally a subquery, optionally parameterized) as
   a SELECT column with a given alias.
3. A dedicated, self-documenting entry point that binds the composition alias and the
   target result class together, up front, and validates them immediately — instead of a
   generic `FindQuery` silently accepting any alias/class combination until row-mapping
   time.

No changes are needed in `GenericRowMapper` or `RowMapperFactory`.

---

## New API

### `GenericRepository<T, ID>.newAugmentedFindQuery(...)`

```java
protected <R> FindQuery<T, ID> newAugmentedFindQuery(String alias, Class<R> resultClass)
protected <R> FindQuery<T, ID> newAugmentedFindQuery(Getter<R> aliasGetter, Class<R> resultClass)
```

A dedicated factory, alongside `newFindQuery()`/`newDeleteQuery()`/`newCountQuery()`/
`newExistsQuery()`, distinct from plain `newFindQuery()` on purpose: a query returned by
`newAugmentedFindQuery` is understood to map into `resultClass`, not into `T`, and that intent
is fixed at creation instead of discovered later at the terminal call.

The `Getter<R>` overload derives `alias` via `ReflectionUtils.getColumnName(aliasGetter)` —
the same utility already used for `select(Getter<T>...)`/`leftJoin(Getter<T>, ...)` — and
delegates to the `String` overload. It is the preferred form: `ClientReport::getClient`
instead of the string literal `"client"` avoids a typo silently producing a
"no field named …" exception at runtime instead of a compile error. `ReflectionUtils.getColumnName`
is already generic over the getter's declaring type (`Getter<X>`), not hardcoded to `T`, so
no change is needed there — only the new overload on `newAugmentedFindQuery` is required.

Validates immediately, via `ReflectionUtils.getFields(resultClass)`:
- `resultClass` has a field named `alias`;
- that field's type is assignable from `T`.

On failure it throws `NativSQLException` right away — before any `selectAs`/`where`/`select`
call, before the query is even built. `FindQuery` stores `alias` and `resultClass`
internally (not part of its generic signature — `FindQuery<T, ID>` keeps its existing two
type parameters; adding a third would ripple into `WhereQuery`/`DeleteQuery`/`CountQuery`
for no benefit here).

Still returns the same `FindQuery<T, ID>` type as `newFindQuery()` — no parallel query
class. All existing builder methods (`whereAnd*`, `leftJoin`, `orderBy*`, `limit`/`offset`,
`select`) remain usable on it exactly as before.

### `FindQuery<T, ID>.selectAs(...)`

Aliases columns from the query's own table under a property-name prefix, exactly like a
joined table's columns are aliased under `associationName.column`.

```java
public FindQuery<T, ID> selectAs(String... columns)                          // uses the query's bound alias
public final FindQuery<T, ID> selectAs(Getter<T>... getters)                 // uses the query's bound alias
public FindQuery<T, ID> selectAs(String propertyName, String... columns)     // explicit alias
public final FindQuery<T, ID> selectAs(String propertyName, Getter<T>... getters) // explicit alias
```

- The no-`propertyName` overloads are only valid on a query created via
  `newAugmentedFindQuery(alias, resultClass)`; they delegate to the explicit-`propertyName`
  overload using the query's bound alias. Calling them on a plain `newFindQuery()` query
  (no bound alias) throws `NativSQLException`.
- The explicit-`propertyName` overloads remain available on any `FindQuery` (augmented or
  not) for secondary composition groups — e.g. aliasing a joined table under a different
  name than its association name, or building a composed query "by hand" without going
  through `newAugmentedFindQuery` (advanced/internal use; not the documented common path).
- `columns` must not be empty — same rule as `select(...)`. NativSQL has no `SELECT *`
  equivalent anywhere in the API; the caller always lists the columns explicitly, and
  `selectAs` is no exception.
- Reuses `buildColumnExpression(identifierConverter, tableName, col, propertyName, col)` —
  the same private helper already used for joined columns (`FindQuery.java:478`), just
  called with the *main* table name instead of a join's table name.
- The `Getter<T>...` overloads mirror `select(Getter<T>...)` (`FindQuery.java:110`): they
  resolve column names via `ReflectionUtils.getColumnNames(getters)` and delegate to the
  matching `String...` overload.

### `FindQuery<T, ID>.selectExpression(...)`

Adds a raw SQL expression to the SELECT list, rendered verbatim (no identifier conversion,
no table-prefixing) and aliased with `AS "alias"`.

```java
public FindQuery<T, ID> selectExpression(String alias, String sqlExpression)
public FindQuery<T, ID> selectExpression(String alias, String sqlExpression, Map<String, Object> params)
public FindQuery<T, ID> selectExpressions(Map<String, String> aliasToSqlExpression)
```

- `alias` must not be null/blank. It may itself contain a dot (e.g.
  `"aggregation.orderCount"`) to nest the computed value under a sub-object, following the
  same dotted-alias convention.
- `sqlExpression` must not be null/blank. It is emitted as-is — callers are responsible for
  correct, injection-safe SQL (no string-concatenated user input; use the `params` overload
  with named parameters instead, e.g. `":minAmount"`).
- The `params` overload merges its entries into the query's parameter map
  (`FindQuery.getParameters()`); a duplicate parameter name (colliding with a WHERE
  parameter or another expression's parameter) throws `NativSQLException`.
- Calling `selectExpression` twice with the same `alias` throws `NativSQLException`.
- `selectExpressions(Map<String, String>)` is a bulk convenience that calls
  `selectExpression(alias, sql)` (no params) for each map entry — for the common case of
  several unparameterized computed columns declared together. It does not accept params per
  entry; use repeated `selectExpression(alias, sql, params)` calls when parameters are
  needed.

Both `selectAs` and `selectExpression` get read-back accessors, matching the existing
`getColumns()`/`getJoins()`/`getAssociations()` pattern:

```java
public List<SelectAsColumn> getSelectAsColumns()   // record SelectAsColumn(String propertyName, String column)
public List<ExpressionColumn> getExpressionColumns() // record ExpressionColumn(String alias, String sql, Map<String, Object> params)
```

### `GenericRepository<T, ID>`: mapping into the bound result class

```java
protected <R> R find(FindQuery<T, ID> query, Class<R> resultClass)
protected <R> List<R> findAll(FindQuery<T, ID> query, Class<R> resultClass)
```

Overloads of the existing `protected T find(FindQuery<T, ID>)` /
`protected List<T> findAll(FindQuery<T, ID>)`, for queries created via
`newAugmentedFindQuery(alias, resultClass)`. Because of Java type erasure, `FindQuery<T, ID>`
cannot carry `R` in its compile-time type, so a `Class<R>` witness is still required here to
produce a type-safe `List<R>` — but all the *validation* already happened at
`newAugmentedFindQuery(...)` time. This second `resultClass` is checked for equality against the
one bound at construction:

```java
if (!resultClass.equals(query.getResultClass())) {
    throw new NativSQLException(
            "resultClass '" + resultClass.getSimpleName() + "' does not match the class '"
            + query.getResultClass().getSimpleName() + "' this query was created with via newAugmentedFindQuery(...)");
}
```

If `query.getResultClass()` is `null` (the query was built via plain `newFindQuery()`, never
augmented), `find`/`findAll(query, resultClass)` throw `NativSQLException` — this pair of
methods is only for augmented queries; a plain query keeps using `find(query)`/`findAll(query)`.

They then build the SQL and parameters the same way `find`/`findAll(query)` already do, and
call `findExternal`/`findAllExternal(sql, params, resultClass)` instead of hardcoding
`entityClass`.

**Limitation:** `query.hasAssociations()` (`associate(...)`) is not supported on an augmented
query — association batch-loading (`loadAssociationsInBatch`) only knows how to populate
fields on `T`. `associate(...)` called on a query created via `newAugmentedFindQuery(...)` throws
`NativSQLException` immediately (fails at the builder call, not at the terminal).

These methods stay `protected`, consistent with existing repository design: a `FindQuery` is
never exposed publicly. A concrete repository adds its own named method:

```java
@Repository
public class ClientRepository extends GenericRepository<Client, Long> {
    public List<ClientReport> findClientReports() {
        FindQuery<Client, Long> query = newAugmentedFindQuery(ClientReport::getClient, ClientReport.class)
                .selectAs("id", "name", "email")
                .selectExpressions(Map.of(
                        "totalAmount", "(SELECT COALESCE(SUM(o.amount), 0) FROM orders o WHERE o.client_id = client.id)",
                        "orderCount", "(SELECT COUNT(*) FROM orders o WHERE o.client_id = client.id)"));
        return findAll(query, ClientReport.class);
    }
}
```

---

## SQL output examples

```java
// selectAs only (composition, no computed fields)
newAugmentedFindQuery("client", ClientReport.class).selectAs("id", "name");
// → SELECT client.id AS "client.id", client.name AS "client.name" FROM client

// selectAs + selectExpression (computed subquery)
newAugmentedFindQuery("client", ClientReport.class)
    .selectAs("id", "name")
    .selectExpression("orderCount", "(SELECT COUNT(*) FROM orders o WHERE o.client_id = client.id)");
// → SELECT client.id AS "client.id", client.name AS "client.name",
//          (SELECT COUNT(*) FROM orders o WHERE o.client_id = client.id) AS "orderCount"
//   FROM client

// selectExpression with bound parameter
newAugmentedFindQuery("client", ClientReport.class)
    .selectAs("id")
    .selectExpression("recentOrderCount",
        "(SELECT COUNT(*) FROM orders o WHERE o.client_id = client.id AND o.created_at >= :since)",
        Map.of("since", someInstant));
// → getParameters() contains {"since": someInstant}

// composed with an existing JOIN (client + its group + a computed field)
newAugmentedFindQuery("client", ClientReport.class)
    .selectAs("id", "name")
    .leftJoin("group", "id", "name")
    .selectExpression("orderCount", "(SELECT COUNT(*) FROM orders o WHERE o.client_id = client.id)");
// → maps into a DTO with a `client` field, a `group` field (if ClientReport declares one),
//   and a top-level `orderCount`
```

---

## Error handling

| Situation | Behaviour |
|---|---|
| `newAugmentedFindQuery(alias, resultClass)` called with an `alias` that has no matching field on `resultClass` | `NativSQLException`: "Result class '<resultClass>' has no field named '<alias>'" |
| `newAugmentedFindQuery(alias, resultClass)` called with an `alias` field whose type is not assignable from `T` | `NativSQLException`: "Field '<alias>' on '<resultClass>' has type '<fieldType>', expected '<entityType>'" |
| No-`propertyName` `selectAs(...)` called on a query not created via `newAugmentedFindQuery(...)` | `NativSQLException`: "selectAs(columns...) requires a query created via newAugmentedFindQuery(alias, resultClass); use selectAs(propertyName, columns...) otherwise" |
| `selectAs` called with blank `propertyName` or empty `columns` | `NativSQLException` |
| `selectExpression` called with blank `alias` or blank `sqlExpression` | `NativSQLException` |
| `selectExpression` called twice with the same `alias` | `NativSQLException`: "Duplicate expression alias '<alias>'" |
| `selectExpression` params overload reuses a parameter name already used by a WHERE condition, a range, or another expression | `NativSQLException`: "Duplicate parameter name '<name>'" |
| `associate(...)` called on a query created via `newAugmentedFindQuery(...)` | `NativSQLException`: "associate(...) is not supported on an augmented query" |
| `find`/`findAll(query, resultClass)` called with a `resultClass` different from the one bound at `newAugmentedFindQuery(...)` time | `NativSQLException`: "resultClass '<resultClass>' does not match the class '<boundClass>' this query was created with via newAugmentedFindQuery(...)" |
| `find`/`findAll(query, resultClass)` called on a query never created via `newAugmentedFindQuery(...)` | `NativSQLException`: "query was not created via newAugmentedFindQuery(...)" |
| No column, `selectAs`, `selectExpression`, or join selected at all (`buildSql`) | `NativSQLException`: "At least one column must be selected" |

---

## Out of scope

- Nested computed expressions inside `selectAs` groups (each `selectAs` call only aliases
  plain columns of the main table — use `selectExpression` for anything computed).
- Association (`associate(...)`) batch-loading on augmented queries.
- Validating that `sqlExpression` is well-formed SQL — it is passed through to the database
  driver as-is, same trust boundary as `whereExpression`/raw predicates already in the
  codebase.
- Multiple `newAugmentedFindQuery`-style bindings on the same query (one alias/resultClass pair
  per query); extra `selectAs(propertyName, ...)` groups are allowed but are not separately
  validated against `resultClass` the way the primary alias is.
- Validating `leftJoin(...)`/`innerJoin(...)` association names against `resultClass` fields.
  A join still produces a `"associationName.column"` alias exactly as on a plain `FindQuery`,
  and it still requires `resultClass` to have a matching field to be mapped — but unlike the
  primary `alias`, this is **not** checked at `newAugmentedFindQuery(...)` time. Get it wrong
  and the join's columns are silently dropped by `GenericRowMapper` rather than raising an
  error (same behavior as today when mapping a join into any POJO with no matching field).

## Note: `select(...)` remains fully usable on an augmented query

`select(...)` (plain, unprefixed column selection — e.g. `select("status")`) is unrelated to
the `alias` bound by `newAugmentedFindQuery(...)`: it produces an ordinary top-level column
alias (`status.status AS "status"`), exactly as on any other `FindQuery`. There is no naming
collision with the composition `alias` — `alias` only controls the prefix used by `selectAs`.
`select(...)` and `selectExpression(...)` are the two ways to add a **top-level** scalar field
to `resultClass` (one straight from a column of `T`, one computed); `selectAs(...)` is the one
way to add the **nested composed** field. All three can be combined freely on the same
augmented query.
