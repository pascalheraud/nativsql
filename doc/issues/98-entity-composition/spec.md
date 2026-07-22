# Spec: Augment an entity using FindQuery

> Issue: [nativsql#98](https://github.com/heraud/nativsql/issues/98)

## Composition vs. inheritance — decision

Two shapes were considered for the "report" DTO (e.g. `ClientReport`, wrapping `Client` plus
extra computed fields):

| | Composition (`ClientReport` has a `client` field) | Inheritance (`ClientReport extends Client`) |
|---|---|---|
| New `FindQuery` API needed | `selectAs`, an `alias` concept, `newAugmentedFindQuery(alias, resultClass)`, reflection validation of `alias` against `resultClass` | Only `selectExpression` (chainable) — `select(...)` already works as-is |
| Terminal call | Needs `resultClass` re-checked against the `alias` bound at construction (type erasure workaround) | `find`/`findAll(query, resultClass)` bounded by `<R extends T>` — checked by the **compiler**, no runtime reflection needed |
| `associate(...)` / batch association loading | Not supported — `loadAssociationsInBatch` only knows `T`, has to be forbidden on composed queries | Works as-is once `loadAssociationsInBatch`'s parameter is widened from `List<T>` to `List<? extends T>` (private method, safe, trivial change) |
| Accidental persistence risk (`insert`/`update` with a report instance) | N/A | None in practice: `insert`/`update` take `Getter<T>...`, so only `T`'s own getters are selectable — a report's extra fields (`totalAmount`, …) can't be written even by mistake |
| Composing **multiple** entities in one DTO (e.g. `Client` + `Order` together) | Supported (`selectAs("client", …)` + `selectAs("order", …)`) | **Not possible** — Java has no multiple inheritance |
| Conceptual fit | DTO clearly distinct from the entity it wraps | DTO *is-a* entity (`ClientReport` passes an `instanceof Client` check, inherits `IEntity<ID>`) |

**Decision: inheritance.** It reuses far more of the existing `FindQuery`/`GenericRepository`
machinery, needs no new alias/validation subsystem, and gets `associate(...)` support for
free. The one real limitation — no composing multiple entities into a single DTO — is not a
current requirement.

Composition remains a valid path if that limitation becomes a real need later. The rest of
this document only describes the inheritance-based approach that was chosen.

---

## Goal

Allow a `FindQuery<T, ID>` built against an entity's repository to populate a **report class
that extends the entity** — inheriting all of its fields, plus extra "computed" fields backed
by arbitrary SQL expressions (including subqueries) — instead of being restricted to mapping
rows back into exactly `T`.

Example:

```java
public class Client implements IEntity<Long> {
    private Long id;
    private String name;
    private String email;
    // getters/setters
}

public class ClientReport extends Client {
    private BigDecimal totalAmount;
    private Long orderCount;
    // getters/setters
}
```

```java
FindQuery<Client, Long> query = newFindQuery()
        .select("id", "name", "email")
        .selectExpression("totalAmount", "(SELECT COALESCE(SUM(o.amount), 0) FROM orders o WHERE o.client_id = {{table}}.id)")
        .selectExpression("orderCount", "(SELECT COUNT(*) FROM orders o WHERE o.client_id = {{table}}.id)");

List<ClientReport> reports = findAll(query, ClientReport.class);
```

```sql
SELECT
    client.id AS "id",
    client.name AS "name",
    client.email AS "email",
    (SELECT COALESCE(SUM(o.amount), 0) FROM orders o WHERE o.client_id = client.id) AS "totalAmount",
    (SELECT COUNT(*) FROM orders o WHERE o.client_id = client.id) AS "orderCount"
FROM client
```

---

## Why this works with minimal new machinery

Row-to-object mapping (`GenericRowMapper` / `RowMapperFactory`) is already generic: field
discovery (`ReflectionUtils.getFields(clazz)`) walks the full class hierarchy, so a field
inherited from `Client` is found on `ClientReport` exactly like a field declared directly on
it. Nothing about the mapper cares whether `resultClass` equals the repository's own
`entityClass` — `findExternal`/`findAllExternal` on `GenericRepository` already map into an
arbitrary `Class<EXT>`. `select(...)` on `FindQuery` already produces plain, unprefixed
column aliases (`AS "id"`), which land straight on `ClientReport`'s inherited `id` field with
no change at all.

So the only real gaps are:

1. A way to add a raw SQL expression (optionally a subquery, optionally parameterized) as a
   SELECT column with a given alias — nothing today lets a `FindQuery` emit a computed
   column.
2. A way to run the resulting query and map it into a result class other than the
   repository's own `entityClass` — constrained, at compile time, to be a subtype of `T`.
3. `associate(...)` batch-loading currently only accepts `List<T>` — widening it to
   `List<? extends T>` lets it keep working when the mapped type is `R extends T`.

No changes are needed in `GenericRowMapper` or `RowMapperFactory`.

---

## New API

### `FindQuery<T, ID>.selectExpression(...)`

Adds a raw SQL expression to the SELECT list, rendered (mostly) verbatim and aliased with
`AS "alias"`.

```java
public FindQuery<T, ID> selectExpression(String alias, String sqlExpression)
public FindQuery<T, ID> selectExpression(String alias, String sqlExpression, Map<String, Object> params)
public <R> FindQuery<T, ID> selectExpression(Getter<R> aliasGetter, String sqlExpression)
public <R> FindQuery<T, ID> selectExpression(Getter<R> aliasGetter, String sqlExpression, Map<String, Object> params)
```

- Chainable like every other `FindQuery` builder method — call it as many times as needed
  for multiple computed columns, no separate bulk/`Map`-based form.
- `alias` must not be null/blank.
- The `Getter<R>` overloads derive `alias` via `ReflectionUtils.getColumnName(aliasGetter)` —
  the same utility already used for `select(Getter<T>...)` — and delegate to the matching
  `String` overload. `R` is a free method type parameter here (not tied to `T`): it lets
  `ClientReport::getOrderCount` be used directly, catching a typo'd field name at compile
  time instead of at row-mapping time.

  ```java
  .selectExpression(ClientReport::getOrderCount, "(SELECT COUNT(*) FROM orders o WHERE o.client_id = {{table}}.id)")
  ```
- `sqlExpression` must not be null/blank. It is emitted as-is — callers are responsible for
  correct, injection-safe SQL (no string-concatenated user input; use the `params` overload
  with named parameters instead, e.g. `":minAmount"`) — **except** for one substitution:
  the literal token `{{table}}`, if present anywhere in `sqlExpression`, is replaced with the
  query's own table name (`repository.getTableName()` — the exact same value already used to
  prefix every other column, e.g. `client`) when the SQL is built. This lets a correlated
  subquery reference the outer row without hardcoding the table name:

  ```java
  .selectExpression("orderCount", "(SELECT COUNT(*) FROM orders o WHERE o.client_id = {{table}}.id)")
  // → (SELECT COUNT(*) FROM orders o WHERE o.client_id = client.id) AS "orderCount"
  ```

  `{{table}}` is not a SQL alias — `FindQuery` never declares one (no `FROM client AS c`);
  every column in the generated SQL, including joins and WHERE conditions, is already
  prefixed with the raw table name, so the substitution stays consistent with the rest of the
  query. Using the token is optional; hardcoding the table name (or referencing a different
  table on purpose) still works exactly as before.
- The `params` overload merges its entries into the query's parameter map
  (`FindQuery.getParameters()`); a duplicate parameter name (colliding with a WHERE
  parameter or another expression's parameter) throws `NativSQLException`.
- Calling `selectExpression` twice with the same `alias` throws `NativSQLException`.

#### Overriding an inherited field

`selectExpression` does not require `alias` to be a *new* field, absent from `T`. Row mapping
only ever looks at the SQL column label, never at where the value came from — so
`selectExpression("status", "...")`, used **on its own** (no `select("status")` alongside it),
computes the value that lands in `R`'s inherited `status` field instead of passing the raw
`status` column through unchanged:

```java
newFindQuery()
    .select("id", "name")   // "status" deliberately NOT selected here
    .selectExpression("status", "CASE WHEN {{table}}.deleted_at IS NOT NULL THEN 'ARCHIVED' ELSE status END");
```

This is a supported, intentional technique — e.g. to mask a value, apply a fallback, or
compute an "effective" version of a field while keeping the same field name for API/DTO
stability. It only becomes a problem if the *same* column is also plainly `select`-ed (see
"Error handling" above).

#### Usable on a plain (non-augmented) query too — for overriding only

`selectExpression` is a method on `FindQuery<T, ID>` itself; nothing ties it to
`find`/`findAll(query, resultClass)`. It works just as well with the existing
`find(query)`/`findAll(query)`, which map into `T` — **as long as `alias` matches a field `T`
already has** (the override case above). `T`'s own status field can be overridden with a
computed value while still fetching a plain `List<T>`:

```java
List<Client> clients = findAll(newFindQuery()
        .select("id", "name")
        .selectExpression("status", "CASE WHEN {{table}}.deleted_at IS NOT NULL THEN 'ARCHIVED' ELSE status END"));
```

What does **not** work is using `selectExpression` to introduce an alias that has no matching
field at all on the class actually being mapped into. Row mapping
(`GenericRowMapper.mapColumn`) throws `NativSQLException` — "Property '<alias>' not found for
in class '<class>'" — when a SELECT column's alias has no corresponding field, and this check
happens for *any* target class, not just `resultClass` from an augmented query. So
`selectExpression("orderCount", ...)` followed by plain `findAll(query)` (mapping into
`Client`, which has no `orderCount` field) fails at row-mapping time, not at query-building
time — `Client` simply doesn't have that field. Introducing a genuinely new computed field
requires `find`/`findAll(query, resultClass)` with an `resultClass` that declares it.

Gets a read-back accessor, matching the existing `getColumns()`/`getJoins()`/
`getAssociations()` pattern:

```java
public List<ExpressionColumn> getExpressionColumns() // ExpressionColumn: @Data class with alias/sql/params fields, in its own file (matching sibling Join/Association)
```

### `GenericRepository<T, ID>`: mapping into a subtype of `T`

```java
protected <R extends T> R find(FindQuery<T, ID> query, Class<R> resultClass)
protected <R extends T> List<R> findAll(FindQuery<T, ID> query, Class<R> resultClass)
```

Overloads of the existing `protected T find(FindQuery<T, ID>)` /
`protected List<T> findAll(FindQuery<T, ID>)`. The `<R extends T>` bound is enforced by the
compiler — there is no runtime reflection check to write, unlike a composition-based design
would need. They build the SQL and parameters the same way `find`/`findAll(query)` already
do, and call `findExternal`/`findAllExternal(sql, params, resultClass)` instead of hardcoding
`entityClass`. `find(query, resultClass)` still runs `loadAssociationsInBatch` when
`query.hasAssociations()` is true, exactly like `find(query)` does today.

These methods stay `protected`, consistent with existing repository design: a `FindQuery` is
never exposed publicly. A concrete repository adds its own named method:

```java
@Repository
public class ClientRepository extends GenericRepository<Client, Long> {
    public List<ClientReport> findClientReports() {
        FindQuery<Client, Long> query = newFindQuery()
                .select("id", "name", "email")
                .selectExpression("totalAmount", "(SELECT COALESCE(SUM(o.amount), 0) FROM orders o WHERE o.client_id = {{table}}.id)")
                .selectExpression("orderCount", "(SELECT COUNT(*) FROM orders o WHERE o.client_id = {{table}}.id)");
        return findAll(query, ClientReport.class);
    }
}
```

### `GenericRepository<T, ID>.loadAssociationsInBatch`: widened bound

```java
// before:
private void loadAssociationsInBatch(List<T> entities, List<Association> associations)
// after:
private void loadAssociationsInBatch(List<? extends T> entities, List<Association> associations)
```

Private method, no external callers — the only change needed to let `associate(...)` keep
working when `find(query, resultClass)` maps into `R extends T` instead of `T`.

---

## SQL output examples

```java
// select + selectExpression (computed subquery), using {{table}} instead of hardcoding "client"
newFindQuery()
    .select("id", "name")
    .selectExpression("orderCount", "(SELECT COUNT(*) FROM orders o WHERE o.client_id = {{table}}.id)");
// → SELECT client.id AS "id", client.name AS "name",
//          (SELECT COUNT(*) FROM orders o WHERE o.client_id = client.id) AS "orderCount"
//   FROM client

// selectExpression with bound parameter
newFindQuery()
    .select("id")
    .selectExpression("recentOrderCount",
        "(SELECT COUNT(*) FROM orders o WHERE o.client_id = {{table}}.id AND o.created_at >= :since)",
        Map.of("since", someInstant));
// → SQL contains "o.client_id = client.id AND o.created_at >= :since"
//   getParameters() contains {"since": someInstant}

// several computed columns, chained
newFindQuery()
    .select("id", "name")
    .selectExpression("totalAmount", "(SELECT COALESCE(SUM(o.amount), 0) FROM orders o WHERE o.client_id = {{table}}.id)")
    .selectExpression("orderCount", "(SELECT COUNT(*) FROM orders o WHERE o.client_id = {{table}}.id)");
// → {{table}} is replaced with "client" in both, same output as hardcoding it

// combined with an existing JOIN and associate(...)
newFindQuery()
    .select("id", "name")
    .leftJoin("group", "id", "name")
    .selectExpression("orderCount", "(SELECT COUNT(*) FROM orders o WHERE o.client_id = {{table}}.id)")
    .associate("contacts", "id", "email");

List<ClientReport> reports = findAll(query, ClientReport.class);
// → maps into ClientReport (inherited Client fields, joined `group`, computed `orderCount`,
//   batch-loaded `contacts` — all working exactly as they would for a plain Client query)
```

---

## Error handling

| Situation | Behaviour |
|---|---|
| `selectExpression` called with blank `alias` or blank `sqlExpression` | `NativSQLException` |
| `selectExpression` called twice with the same `alias` | `NativSQLException`: "Duplicate expression alias '<alias>'" |
| `selectExpression(alias, ...)` called with an `alias` already present in this query's `select(...)` columns | `NativSQLException`: "Expression alias '<alias>' collides with a plain select(...) column of the same name" |
| `select(...)` called with a column name already used as a `selectExpression(...)` alias | `NativSQLException`: "Column '<column>' collides with a selectExpression(...) alias of the same name" |
| `selectExpression` params overload reuses a parameter name already used by a WHERE condition, a range, or another expression | `NativSQLException`: "Duplicate parameter name '<name>'" |
| `resultClass` passed to `find`/`findAll(query, resultClass)` is not a subtype of `T` | Does not compile — `<R extends T>` bound |
| No column, `selectExpression`, or join selected at all (`buildSql`) | `NativSQLException`: "At least one column must be selected" |
| `selectExpression(alias, ...)` used with plain `find(query)`/`findAll(query)` (mapping into `T`) and `alias` has no matching field on `T` | `NativSQLException` from `GenericRowMapper`, at row-mapping time (not at `buildSql` time): "Property '<alias>' not found for in class '<T>'" |

---

## Out of scope

- Composing multiple entities into a single DTO (see decision table above).
- Validating that `sqlExpression` is well-formed SQL — it is passed through to the database
  driver as-is, same trust boundary as `whereExpression`/raw predicates already in the
  codebase.

`select(...)` and `selectExpression(...)` **do** validate against each other for the same
alias/column name in the same query (see "Error handling" above) — calling both
`select("status")` and `selectExpression("status", ...)` together throws `NativSQLException`
at the builder call, rather than letting the database reject the resulting duplicate-alias
SQL. This is unrelated to using `selectExpression(alias, ...)` **alone**, with an `alias`
that happens to match a field `R` already inherits from `T` (e.g. `selectExpression("status",
"CASE WHEN ... END")` with no `select("status")` alongside it) — that remains a fully
supported, intentional way to override the value of an inherited field with a computed one.
See the "Overriding an inherited field" note under `selectExpression` below.
