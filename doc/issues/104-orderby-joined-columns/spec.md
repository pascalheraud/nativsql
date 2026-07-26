# Spec: ORDER BY on joined-table columns — explicit disambiguation

> Issue: [nativsql#104](https://github.com/heraud/nativsql/issues/104)

## Goal

Allow `orderByAsc`/`orderByDesc` to target a column that lives on a **joined**
entity, even when the same property name (e.g. `creationDate`) exists on
several entities in the query. The join is always designated **explicitly**
by the caller. Two forms are added:

```java
// 1. Fully typed: association getter + target getter (R inferred from the first)
query.leftJoin(User::getGroup, Group::getId, Group::getName, Group::getCreationDate)
     .orderByAsc(User::getGroup, Group::getCreationDate);
// → ORDER BY user_groups.creation_date ASC

// 2. String join name + string column
query.leftJoin("group", "id", "name", "creationDate")
     .orderByAsc("group", "creationDate");
// → ORDER BY user_groups.creation_date ASC
```

Both forms take the association name as an explicit, separate argument —
consistent with how `leftJoin`/`innerJoin` already take the association name
before the joined column list. `leftJoin`/`innerJoin` themselves gain the same
fully-typed form (association getter + target-entity column getters),
alongside the existing `Getter<T> + String...` and `String + String...`
forms, which are unchanged.

The existing `orderByAsc(String column)` and `orderByAsc(Getter<T> getter)`
overloads are unchanged and keep meaning "root entity column".

---

## Javadoc labelling convention

Every `orderByAsc`/`orderByDesc`/`whereAnd*` overload's javadoc first line
states which entity the targeted column resolves against, using one of three
fixed phrases (verbatim, so they're greppable):

| Phrase | When |
|---|---|
| `"on root entity"` | single `Getter<T>` overload — always the root entity |
| `"on root or joined entity"` | single `String column` overload — root by default, joined if the caller passes a dot path |
| `"on joined entity"` | two-argument overloads (`AssociationGetter<T,R>` + `Getter<R>`, or `String joinName` + `String column`) — always a joined entity |

`leftJoin`/`innerJoin` follow the same three phrases but apply them per
argument (association reference vs. columns) rather than to the method as a
whole, since one call always spans both entities — see the `leftJoin`/`innerJoin`
section below for the exact wording.

This is a javadoc-only convention — it changes no behaviour on any existing
overload, only documents intent explicitly so `on joined entity`/`on root
entity` calls aren't confused for one another when reading the API.

---

## New APIs

### `ReflectionUtils.AssociationGetter<T, R>`

```java
@FunctionalInterface
public interface AssociationGetter<T, R> extends Serializable {
    R get(T obj);
}
```

Unlike `Getter<T>` (`Object get(T obj)`), this is typed to return `R`.
`User::getGroup` is assignable to `AssociationGetter<User, Group>`, which
pins `R = Group` at the call site, so the compiler requires the second
argument of `orderByAsc(assocGetter, getter)` to be a `Getter<Group>` —
`Order::getCreationDate` would not compile there. The association name
itself is still extracted at runtime via `ReflectionUtils.getColumnName(assocGetter)`,
exactly like `leftJoin(Getter<T> getter, ...)` already does today.

### `FindQuery<T, ID>`

Every `orderByAsc`/`orderByDesc` overload's javadoc states, in its first
line, which entity the column resolves against — `"on root entity"`, `"on
root or joined entity"` (string form: caller-controlled, works for either
depending on whether a dot path is passed), or `"on joined entity"`
(explicit two-argument forms). See "Javadoc labelling convention" below for
the exact wording to use on every overload touched by this feature (existing
and new).

```java
/**
 * Adds an ascending ORDER BY condition on root entity, using a getter method reference.
 */
public FindQuery<T, ID> orderByAsc(Getter<T> getter)

/**
 * Adds an ascending ORDER BY condition on root or joined entity, using a raw column name.
 * Prefix with "assoc." to target a joined entity's column (e.g. "group.name").
 */
public FindQuery<T, ID> orderByAsc(String column)

/**
 * Adds an ascending ORDER BY condition on joined entity, using an association getter
 * and a target-entity getter method reference.
 */
public <R> FindQuery<T, ID> orderByAsc(AssociationGetter<T, R> assocGetter, Getter<R> getter)

/**
 * Adds an ascending ORDER BY condition on joined entity, using an explicit join name
 * and column name.
 */
public FindQuery<T, ID> orderByAsc(String joinName, String column)
```
(same four, mirrored, for `orderByDesc`.)

Resolution for the two new forms:
1. `associationName` — from `assocGetter` via `ReflectionUtils.getColumnName`, or the `joinName` string directly.
2. `columnName` — from `getter` via `ReflectionUtils.getColumnName`, or the `column` string directly.
3. Look up the join by `associationName` in the registered joins; `NativSQLException` if not found.
4. Order by `"<joinTableName>.<columnName>"`.

### `FindQuery<T, ID>` — `leftJoin`/`innerJoin` fully-typed overload

For `leftJoin`/`innerJoin`, the label describes the two arguments separately
(association reference vs. joined columns), since a single call always spans
both entities:

```java
/**
 * Adds a LEFT JOIN for a @MappedBy association, using the association's raw name
 * (on root entity) and raw column names (on joined entity).
 */
public FindQuery<T, ID> leftJoin(String associationName, String... columns)

/**
 * Adds a LEFT JOIN for a @MappedBy association, using an association getter method
 * reference (on root entity) and raw column names (on joined entity).
 */
public FindQuery<T, ID> leftJoin(Getter<T> getter, String... columns)

/**
 * Adds a LEFT JOIN for a @MappedBy association, using an association getter method
 * reference (on root entity) and target-entity getter method references (on joined entity).
 */
@SafeVarargs
public final <R> FindQuery<T, ID> leftJoin(AssociationGetter<T, R> assocGetter, Getter<R>... columnGetters)
```
(same three, mirrored, for `innerJoin`.)

Same `AssociationGetter<T, R>` typing as the new `orderByAsc`/`orderByDesc`
overload: `assocGetter` pins `R` to the joined entity type, so
`columnGetters` must be getters of that same entity — `Group::getId,
Group::getName` type-checks after `User::getGroup`, a getter from an
unrelated entity does not. Resolution: `associationName =
ReflectionUtils.getColumnName(assocGetter)`, `columns =
ReflectionUtils.getColumnNames(columnGetters)`, then delegate to the existing
`leftJoin(String associationName, String... columns)` /
`innerJoin(String associationName, String... columns)`.

The existing `leftJoin(String, String...)` and `leftJoin(Getter<T>,
String...)` overloads are unchanged — the typed form is a third overload, not
a replacement. Both existing overloads gain the same `"(on root entity)"` /
`"(on joined entity)"` javadoc clarification for their two arguments (see
above), without any behaviour change.

### `FindQuery<T, ID>` — `whereAnd*` fully-typed overload

Same principle applied to the `whereAnd*` methods inherited from
`WhereQuery`/`AbstractWhereQuery`. Unlike the join/orderBy cases, the
dot-notation join path (`"assoc.column"`) already exists for WHERE since #83
(`WhereClause`'s `JoinResolver`), so no new resolution plumbing is needed —
only the typed overloads, added on `FindQuery` (not on the shared
`WhereQuery` base, since joins — and therefore associations — only exist on
`FindQuery`, not on `DeleteQuery`):

```java
/**
 * Adds a WHERE condition with EQUALS operator on joined entity, using an association
 * getter and a target-entity getter method reference.
 */
public <R> FindQuery<T, ID> whereAndEquals(AssociationGetter<T, R> assocGetter, Getter<R> getter, Object value)

/**
 * Adds a WHERE condition with IN operator on joined entity, using an association
 * getter and a target-entity getter method reference.
 */
public <R> FindQuery<T, ID> whereAndIn(AssociationGetter<T, R> assocGetter, Getter<R> getter, List<?> values)

/**
 * Adds a WHERE condition with an explicit operator on joined entity, using an association
 * getter and a target-entity getter method reference.
 */
public <R> FindQuery<T, ID> whereAndOperator(AssociationGetter<T, R> assocGetter, Getter<R> getter, Operator operator, Object value)

/**
 * Adds a column-only WHERE condition (e.g. IS NULL) on joined entity, using an association
 * getter and a target-entity getter method reference.
 */
public <R> FindQuery<T, ID> whereAndColumnOperator(AssociationGetter<T, R> assocGetter, Getter<R> getter, ColumnOperator operator)

/**
 * Adds a BETWEEN range WHERE condition on joined entity, using an association
 * getter and a target-entity getter method reference.
 */
public <R> FindQuery<T, ID> whereAndRange(AssociationGetter<T, R> assocGetter, Getter<R> getter, RangeOperator operator, Object low, Object high)
```

The inherited `whereAnd*(String column, ...)` overloads (declared on
`WhereQuery`) are relabelled `"on root or joined entity"` (dot-path optional,
same rule as `orderByAsc(String)`); the inherited `whereAnd*(Getter<T>
getter, ...)` overloads are relabelled `"on root entity"`. Both keep their
current behaviour — only the javadoc first line changes, to stay consistent
with the new typed overloads declared on `FindQuery`.

Each new overload builds `associationName + "." + columnName` (same extraction as the
`orderByAsc`/`orderByDesc` typed overload) and delegates to the existing
`String`-based `whereAnd*` method inherited from `WhereQuery`, which already
understands the dot path via `WhereClause`. The existing `whereAnd*(String,
...)` and `whereAnd*(Getter<T>, ...)` overloads (root entity only) are
unchanged.

```java
query.leftJoin(User::getGroup, Group::getId, Group::getName)
     .whereAndEquals(User::getGroup, Group::getName, "Admins");
// → WHERE user_groups.name = :groupName
```

### `OrderBy`

```java
public OrderBy withTablePrefix(String tablePrefix)
public OrderBy withJoins(boolean hasJoins)
public OrderBy withJoinResolver(JoinResolver resolver)
```

Mirrors `WhereClause` (`WhereClause.java:88-116`). `FindQuery.buildSql()`
calls `orderBy.withJoinResolver(this::resolveJoinColumn).withTablePrefix(tableName).withJoins(hasJoins())`
before rendering, reusing the existing `resolveJoinColumn` (`FindQuery.java:340-352`,
from #83) rather than duplicating join lookup logic.

Internally, the four new `orderByAsc`/`orderByDesc` overloads build the path
`associationName + "." + columnName` and forward to the existing
`orderByAsc(String column)` / `orderByDesc(String column)`. Resolution inside
`OrderBy.Order.build()`:
- contains a dot → delegate to the `JoinResolver` (throws if none registered)
- no dot, `hasJoins` true, `tablePrefix` set → prefix with the root table name
- no dot, no joins → unchanged (unqualified)

Standalone `OrderBy` (used outside `FindQuery`) is unaffected when no
resolver is registered.

---

## Error handling

| Situation | Behaviour |
|---|---|
| `joinName`/`associationName` not found in registered joins | `NativSQLException`: "No join found for association '<name>' in dot-notation column '<path>'…" (reuses `resolveJoinColumn`, same message as #83) |
| Dot-notation order path used with no `JoinResolver` set (defensive — unreachable via this feature's public API, inherited from the shared `OrderBy`/`WhereClause` plumbing) | `NativSQLException`: "Dot-notation column paths are not supported in this query type: '<path>'" |

---

## SQL output examples

```java
// Root entity getter, no joins — unchanged behaviour
FindQuery.of(userRepo).select("id").orderByAsc(User::getFirstName);
// → ORDER BY first_name ASC

// Joined entity column via typed association + target getters
FindQuery.of(userRepo).select("id")
    .leftJoin(User::getGroup, "id", "name", "creationDate")
    .orderByAsc(User::getGroup, Group::getCreationDate);
// → ORDER BY user_groups.creation_date ASC

// Joined entity column via string join name + column
FindQuery.of(userRepo).select("id")
    .leftJoin("group", "id", "creationDate")
    .orderByAsc("group", "creationDate");
// → ORDER BY user_groups.creation_date ASC

// Two joins of the same entity type — each ordered by its own explicit join name
FindQuery.of(userRepo).select("id")
    .leftJoin("primaryGroup", "id", "creationDate")
    .leftJoin("secondaryGroup", "id", "creationDate")
    .orderByAsc("secondaryGroup", "creationDate");
// → ORDER BY secondary_groups.creation_date ASC
```

---

## Out of scope

- Ordering by an expression column (`selectExpression`).
- Two-level join paths (`"a.b.column"`) — same one-level restriction as #83.
- Changing `WhereClause`'s behaviour — this feature only touches `OrderBy` and adds `FindQuery` overloads.
