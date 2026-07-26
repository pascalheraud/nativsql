# Plan: ORDER BY on joined-table columns — explicit disambiguation

> Issue: [nativsql#104](https://github.com/heraud/nativsql/issues/104) — see `spec.md` for the full contract.

Every `orderByAsc`/`orderByDesc`/`whereAnd*`/`leftJoin`/`innerJoin` overload
touched below (existing or new) must have its javadoc first line updated to
use the exact phrase from spec.md's "Javadoc labelling convention" table —
`"on root entity"`, `"on root or joined entity"`, or `"on joined entity"` —
so this must not be skipped as a "just add the new overloads" pass; the
existing single-`Getter<T>`/single-`String` overloads get their javadoc
first line rewritten too, with no behaviour change.

## Step 1 — `ReflectionUtils.AssociationGetter<T, R>`

File: `nativsql-core/src/main/java/ovh/heraud/nativsql/util/ReflectionUtils.java`

- Add:
  ```java
  @FunctionalInterface
  public interface AssociationGetter<T, R> extends Serializable {
      R get(T obj);
  }
  ```
  next to the existing `Getter<T>` interface.
- `ReflectionUtils.getColumnName`/`extractMethodName` are currently typed to
  `Getter<T>`. Add an overload (or generalize the `writeReplace`/
  `SerializedLambda` extraction to accept any `Serializable`) so
  `ReflectionUtils.getColumnName(AssociationGetter<T, R> getter)` works the
  same way — same method-name extraction logic, just a different declared
  parameter type. Prefer a small private shared helper
  `extractMethodName(Serializable lambda)` called by both the `Getter<T>` and
  `AssociationGetter<T,R>` public overloads, to avoid duplicating the
  reflection code.

## Step 2 — `OrderBy`: join-aware column resolution

File: `nativsql-core/src/main/java/ovh/heraud/nativsql/util/OrderBy.java`

- Add fields `tablePrefix`, `hasJoins`, `joinResolver` and fluent setters
  `withTablePrefix(String)`, `withJoins(boolean)`, `withJoinResolver(JoinResolver)`
  — copy the shape from `WhereClause` (`WhereClause.java:23,88-116`).
- Add a `private String toDbCol(IdentifierConverter converter, String column)`
  helper mirroring `WhereClause.toDbCol` (`WhereClause.java:232-245`): dot →
  resolver (or throw if none registered); no dot + `hasJoins` + prefix set →
  qualify with `tablePrefix`; else unchanged.
- Route `Order.build(StringBuilder, IdentifierConverter)` through this
  resolution instead of calling `converter.toDB(column)` directly. `Order` is
  currently a `private static class` — either drop `static` so it can reach
  the outer `OrderBy` instance, or resolve the column string before
  constructing/calling `Order.build` and pass the resolved string in. Pick
  whichever is less invasive once inside the file.
- `copyFrom(OrderBy other)` is unaffected — it copies raw column strings; the
  copy target's own resolver/prefix (set later by `FindQuery.buildSql()`)
  applies at build time.

## Step 3 — `FindQuery`: register the resolver for ORDER BY

File: `nativsql-core/src/main/java/ovh/heraud/nativsql/util/FindQuery.java`

- In `buildSql()`, before the `orderBy.isEmpty()` check (around line 549),
  add:
  ```java
  orderBy.withJoinResolver(this::resolveJoinColumn).withTablePrefix(tableName).withJoins(hasJoins());
  ```
- Reuse the existing private `resolveJoinColumn` (`FindQuery.java:340-352`) —
  no duplication.

## Step 4 — `FindQuery`: new `orderByAsc`/`orderByDesc` overloads

File: `nativsql-core/src/main/java/ovh/heraud/nativsql/util/FindQuery.java`

- Add, alongside the existing `orderByAsc`/`orderByDesc` overloads (leave the
  `String column` and `Getter<T> getter` ones untouched):
  ```java
  public <R> FindQuery<T, ID> orderByAsc(AssociationGetter<T, R> assocGetter, Getter<R> getter) {
      return orderByAsc(ReflectionUtils.getColumnName(assocGetter), ReflectionUtils.getColumnName(getter));
  }

  public <R> FindQuery<T, ID> orderByDesc(AssociationGetter<T, R> assocGetter, Getter<R> getter) {
      return orderByDesc(ReflectionUtils.getColumnName(assocGetter), ReflectionUtils.getColumnName(getter));
  }

  public FindQuery<T, ID> orderByAsc(String joinName, String column) {
      return orderByAsc(joinName + "." + column);
  }

  public FindQuery<T, ID> orderByDesc(String joinName, String column) {
      return orderByDesc(joinName + "." + column);
  }
  ```
- Import `ovh.heraud.nativsql.util.ReflectionUtils.AssociationGetter` next to
  the existing `ReflectionUtils.Getter` import.
- No validation needed for blank `joinName`/`column` beyond what
  `resolveJoinColumn` already does (it throws if the association isn't
  registered); keep this consistent with how `whereAndEquals("group.name", ...)`
  behaves today rather than adding new blank-string guards.

## Step 5 — `FindQuery`: fully-typed `leftJoin`/`innerJoin` overloads

File: `nativsql-core/src/main/java/ovh/heraud/nativsql/util/FindQuery.java`

- Add, alongside the existing `leftJoin(String, String...)` and
  `leftJoin(Getter<T>, String...)` (leave both untouched):
  ```java
  @SafeVarargs
  public final <R> FindQuery<T, ID> leftJoin(AssociationGetter<T, R> assocGetter, Getter<R>... columnGetters) {
      return leftJoin(ReflectionUtils.getColumnName(assocGetter), ReflectionUtils.getColumnNames(columnGetters));
  }

  @SafeVarargs
  public final <R> FindQuery<T, ID> innerJoin(AssociationGetter<T, R> assocGetter, Getter<R>... columnGetters) {
      return innerJoin(ReflectionUtils.getColumnName(assocGetter), ReflectionUtils.getColumnNames(columnGetters));
  }
  ```
- `ReflectionUtils.getColumnNames(Getter<T>... getters)` is already generic;
  confirm it accepts `Getter<R>[]` without changes (it should — its type
  parameter is independent of `FindQuery`'s `T`).

## Step 6 — `FindQuery`: fully-typed `whereAnd*` overloads

File: `nativsql-core/src/main/java/ovh/heraud/nativsql/util/FindQuery.java`

- Add, on `FindQuery` (not `WhereQuery`/`AbstractWhereQuery` — `DeleteQuery`
  has no joins), one overload per existing `whereAnd*(Getter<T>, ...)` method
  inherited from `WhereQuery`:
  ```java
  public <R> FindQuery<T, ID> whereAndEquals(AssociationGetter<T, R> assocGetter, Getter<R> getter, Object value) {
      return whereAndEquals(joinColumnPath(assocGetter, getter), value);
  }

  public <R> FindQuery<T, ID> whereAndIn(AssociationGetter<T, R> assocGetter, Getter<R> getter, List<?> values) {
      return whereAndIn(joinColumnPath(assocGetter, getter), values);
  }

  public <R> FindQuery<T, ID> whereAndOperator(AssociationGetter<T, R> assocGetter, Getter<R> getter, Operator operator, Object value) {
      return whereAndOperator(joinColumnPath(assocGetter, getter), operator, value);
  }

  public <R> FindQuery<T, ID> whereAndColumnOperator(AssociationGetter<T, R> assocGetter, Getter<R> getter, ColumnOperator operator) {
      return whereAndColumnOperator(joinColumnPath(assocGetter, getter), operator);
  }

  public <R> FindQuery<T, ID> whereAndRange(AssociationGetter<T, R> assocGetter, Getter<R> getter, RangeOperator operator, Object low, Object high) {
      return whereAndRange(joinColumnPath(assocGetter, getter), operator, low, high);
  }

  private <R> String joinColumnPath(AssociationGetter<T, R> assocGetter, Getter<R> getter) {
      return ReflectionUtils.getColumnName(assocGetter) + "." + ReflectionUtils.getColumnName(getter);
  }
  ```
  Note the return type is `FindQuery<T, ID>`, not `Self` — these overloads
  are declared directly on `FindQuery`, they don't need the `Self`-typed
  fluent-chaining trick from the shared base class.
- No changes needed in `WhereClause`/`JoinResolver` — the dot-path resolution
  these delegate into already exists (#83) and is already wired up via
  `FindQuery.buildSql()`'s `whereClause.withJoinResolver(this::resolveJoinColumn)`.
- Double-check `guardEncryptedColumn` (called by the delegate `String`
  overloads) already skips dotted columns — confirmed in #83's spec, no
  change needed here.

## Step 7 — Tests

Follow the `tests` skill conventions. Add to
`nativsql-core/src/test/java/ovh/heraud/nativsql/util/FindQueryTest.java`:

1. `orderByAsc(assocGetter, getter)` on a single join → `ORDER BY <joinTable>.<col> ASC`.
2. `orderByDesc(assocGetter, getter)` — same, descending.
3. `orderByAsc(joinName, column)` string form → same SQL as (1).
4. Two joins of the same entity type, each ordered independently by its own
   explicit join name (both forms) → correct table qualification per join.
5. Unregistered join name/association → `NativSQLException` (reuses
   `resolveJoinColumn`'s existing error, assert message content).
6. Root entity `orderByAsc(Getter<T>)` / `orderByAsc(String)` — regression
   check, unchanged output, including when the query also has joins (root
   column still resolves correctly alongside the new join-based ordering).
7. `orderBy(OrderBy)` merge case with a `FindQuery` that has joins — build an
   `OrderBy` with the association+column path added via a merged instance,
   confirm the resolver from the target `FindQuery` applies at build time.
8. `leftJoin(assocGetter, columnGetters...)` — SQL matches the equivalent
   `leftJoin(String, String...)` call (same joined columns, same alias/prefix
   handling in `buildPrefixedColumns`).
9. `innerJoin(assocGetter, columnGetters...)` — same, for `INNER JOIN`.
10. `leftJoin(assocGetter, columnGetters...)` combined with
    `orderByAsc(assocGetter, getter)` end-to-end, asserting the full
    generated SQL (SELECT + JOIN + ORDER BY).
11. Each typed `whereAnd*(assocGetter, getter, ...)` overload (`whereAndEquals`,
    `whereAndIn`, `whereAndOperator`, `whereAndColumnOperator`, `whereAndRange`)
    → SQL matches the equivalent existing `whereAnd*("assoc.column", ...)`
    string-dot-path call.
12. Typed `whereAndEquals` combined with typed `leftJoin` and typed
    `orderByAsc` in a single query, asserting the full generated SQL.

Run via the `tests` skill's prescribed command (ask before running, per
existing feedback memory) — likely `mvn -pl nativsql-core test -Dtest=FindQueryTest`.

## Step 8 — Documentation

Per the `documentation` skill:
- Update `CHANGELOG.md`: one entry, e.g. "Added typed `leftJoin`/`innerJoin`,
  `orderByAsc`/`orderByDesc`, and `whereAnd*` overloads for joined entities —
  association getter + target-entity getter(s), or an explicit join name +
  column string(s) — see `doc/issues/104-orderby-joined-columns/spec.md`."
  Ask via `AskUserQuestion` whether to bump the version first.
- Update `README.md` if it documents `leftJoin`/`innerJoin`/`orderByAsc`/`orderByDesc`/`whereAnd*`
  usage — grep for existing mentions and extend with a joined example if present.

## Verification

- `mvn -pl nativsql-core test` (ask before running).
- Confirm `nativsql-postgres`/`nativsql-mariadb` modules still compile — this
  feature only adds overloads, no existing signature changes, so this should
  be a formality.
