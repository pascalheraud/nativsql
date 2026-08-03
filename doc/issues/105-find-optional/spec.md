# Spec: Find methods may return an `Optional` as well

> Issue: [nativsql#105](https://github.com/pascalheraud/nativsql/issues/105)

## Goal

Every single-result "find" method in `GenericRepository` currently returns `T` and uses
`null` to signal "not found". Add an `Optional<T>`-returning twin for each of these
methods so repository authors can opt into `Optional`-based null handling instead of
manual null checks, without changing the behaviour or signature of any existing method.

## Problem

`GenericRepository` has a family of single-result lookup methods that all follow the
same "return the entity, or `null` if not found" contract:

- `findById(Object, Getter<T>...)` / `findById(Object, String...)` —
  `nativsql-core/src/main/java/ovh/heraud/nativsql/repository/GenericRepository.java:617,632`
- `findByProperty(...)` (4 overloads, getter/property × getters/columns) —
  `GenericRepository.java:710,731,750,766`
- `findByPropertyExpression(...)` (2 overloads) — `GenericRepository.java:1096,1116`
- `find(FindQuery<T, ID>)` and `find(FindQuery<T, ID>, Class<R>)` —
  `GenericRepository.java:1260,1312`

Concrete repositories built on top of these (e.g.
`nativsql-postgres/src/test/java/ovh/heraud/nativsql/repository/postgres/PostgresUserRepository.java`)
inherit this same "or null" contract in their own public finder methods (e.g.
`findByEmail`), forcing every caller to null-check. There is currently no `Optional`
anywhere on the find surface (confirmed: no occurrence of `java.util.Optional` in
`nativsql-core`'s main sources).

## Behaviour after fix

- For each existing nullable single-result find method, an additional method exists
  with `find` replaced by `findOptional` in its name (e.g. `findById` →
  `findOptionalById`, `find(FindQuery)` → `findOptional(FindQuery)`), same parameters,
  same visibility, returning `Optional<T>` (or `Optional<R>` for the `Class<R>`
  variant) instead of `T`.
- The new methods wrap the existing ones: `Optional.ofNullable(existingMethod(...))`.
  No query-building or mapping logic is duplicated or changed.
- Existing methods (`findById`, `findByProperty`, `findByPropertyExpression`, `find`)
  are **not modified** — same signature, same nullable return, same behaviour. This is
  a purely additive change; no existing caller is affected.
- Validation errors already thrown by the wrapped method (e.g. `NativSQLException` on
  an empty `columns`/`getters` array) propagate unchanged through the `Optional`
  variant — they are not converted to `Optional.empty()`.
- A caller who wants `Optional` semantics for `findById` calls `findOptionalById`
  instead; a caller who wants the existing behaviour keeps calling `findById`. Both
  hit the same SQL/mapping code path.

## Architecture

### Data flow

```
findOptionalById(42, "name")
  → findById(42, "name")            (unchanged: builds FindQuery, executes, maps)
  → Optional.ofNullable(result)     (new: only wraps the outcome)
```

No new query-building, SQL, or mapping code — `Optional` variants are thin wrappers
added next to (not inside) the methods listed under "Problem".

### Key principles

- **One new method per existing nullable single-result method**, same visibility
  (`public`/`protected`), same `final`-ness, same varargs shape, same
  `@SafeVarargs` where applicable.
- **Delegation only**: the body of every `*Optional` method is
  `return Optional.ofNullable(<same-name-without-Optional>(<same args>));` — never a
  reimplementation.
- **No change to list-returning methods** (`findAll`, `findAllByIds`,
  `findAllByProperty`, etc.) — `Optional` only applies where "not found" currently
  means a single `null`, not an empty list (an empty `List` is already an unambiguous
  "not found" signal, no `Optional` needed).
- **No change to `find(FindQuery, Class<R>)`'s association-loading or subtype-mapping
  behaviour** — the `Optional` variant loads associations exactly when the wrapped
  method would have.

## API

New methods added to `GenericRepository<T extends IEntity<ID>, ID>`, each mirroring
an existing method's signature and visibility (`Optional` return instead of `T`/`R`):

```java
// mirrors findById(Object, Getter<T>...)
public final Optional<T> findOptionalById(Object id, Getter<T>... getters)

// mirrors findById(Object, String...)
public Optional<T> findOptionalById(Object id, String... columns)

// mirrors the 4 findByProperty overloads
protected final Optional<T> findOptionalByProperty(Getter<T> propertyGetter, Object value, String... columns)
protected final Optional<T> findOptionalByProperty(Getter<T> propertyGetter, Object value, Getter<T>... getters)
protected final Optional<T> findOptionalByProperty(String property, Object value, Getter<T>... getters)
protected Optional<T> findOptionalByProperty(String property, Object value, String... columns)

// mirrors the 2 findByPropertyExpression overloads
protected final Optional<T> findOptionalByPropertyExpression(String propertyExpression, String paramName, Object value, Getter<T>... getters)
protected Optional<T> findOptionalByPropertyExpression(String propertyExpression, String paramName, Object value, String... columns)

// mirrors find(FindQuery<T, ID>)
protected Optional<T> findOptional(FindQuery<T, ID> query)

// mirrors find(FindQuery<T, ID>, Class<R>)
protected <R extends T> Optional<R> findOptional(FindQuery<T, ID> query, Class<R> resultClass)
```

No existing method signature changes. This is additive only — no breaking change for
any code compiled against the current `GenericRepository` API.

## Error handling

No new error cases. Each `*Optional` method surfaces exactly the exceptions the
wrapped method already throws (e.g. `NativSQLException("Column list cannot be empty")`
from `findById`/`findByProperty`/`findByPropertyExpression` when `columns`/`getters`
is `null` or empty) — validation happens before the `Optional.ofNullable(...)` wrap,
so it is not swallowed into an empty `Optional`.

## Patterns reused

| Pattern | Source |
|---|---|
| Thin delegating overload next to an existing method (no logic duplication) | `findById(Object, Getter<T>...)` delegating to `findById(Object, String...)`, `GenericRepository.java:617-620` |
| `@SafeVarargs` on `final` varargs-of-generic-type overloads | Existing `Getter<T>...` overloads throughout `GenericRepository.java` |
| Public vs protected split (public for id/direct lookups, protected for building blocks subclasses expose their own way) | Existing `findById` (public) vs `findByProperty`/`find(FindQuery)` (protected), same file |
