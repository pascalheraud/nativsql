# Spec: storing a list of ids as JSON

> Issue: [nativsql#109](https://github.com/heraud/nativsql/issues/109)

## Goal

Confirm and validate that NativSQL can map a field of type `List<Long>` (a list of ids) to a JSON/JSONB column, using the existing `@Json` mechanism — and document it. No new production code is expected; this is a verification + test + documentation feature.

```java
@Json
private List<Long> tagIds;
```

## Why `@Json`, not a native SQL array

Postgres has a native array column type (`BIGINT[]`), read/written through `java.sql.Array` / `Connection.createArrayOf(...)`. It was considered as an alternative to JSON for this use case and rejected for this issue:

| | `@Json` (JSONB/JSON) | Native array (`BIGINT[]`) |
|---|---|---|
| Dialect support | Postgres (`PostgreJSONTypeMapper`), MySQL/MariaDB/Oracle (`GenericJSONTypeMapper`) — already implemented, works today | Postgres only. MySQL, MariaDB and Oracle have no equivalent array column type, so a generic mapper cannot exist — every entity using it would become non-portable across dialects |
| New code required | None — the existing `@Json` annotation + type mappers already handle any Jackson-serializable generic type, including `List<Long>` (`PostgreJSONTypeMapper` caches `fieldAccessor.getField().getGenericType()` as a Jackson `JavaType`, which resolves list element types correctly) | A new `DbDataType` value, a new `TypeMapper` using `java.sql.Array`/`PGobject`, a new `DatabaseDialect` method to render the `BIGINT[]` column type, and dialect-specific handling since only Postgres could implement it |
| Query support | Cannot be queried/filtered element-wise via `whereAnd*` (opaque JSON blob to SQL) | Postgres array operators (`ANY`, `@>`, etc.) could be exposed later, but that is itself new query-builder surface, not just a type mapper |

Given NativSQL's multi-dialect goal (generic/Postgres/MySQL/MariaDB/Oracle all currently supported), and that `@Json` already works without any new code, **native array support is out of scope for this issue**. It remains a candidate for a future, separate issue if element-wise array querying is later needed.

## Behavior

- `@Json` on a field of a generic collection type (e.g. `List<Long>`, `List<UUID>`) works like `@Json` on a POJO field: the column is `JSONB` (Postgres) / `JSON` (generic dialects), and Jackson serializes/deserializes using the field's full generic type. `Json.java`, `PostgreJSONTypeMapper`, and `GenericJSONTypeMapper` needed no change.

### Bug found and fixed: `List`-typed `@Json` fields broke insert/update

`GenericRepository.convertParamsToSqlValues(...)` special-cased any `Map` entry whose *value* was a `java.util.List`: it always expanded it into a multi-value SQL parameter (the mechanism used to support `whereAndIn(...)`), regardless of what the target column actually was. Since a `List<Long> tagIds` field's value is itself a `List`, this path hijacked it — the entity's own list-of-ids was expanded into N `?` placeholders instead of going through `PostgreJSONTypeMapper`/`GenericJSONTypeMapper` as one JSON value, producing malformed SQL (`INSERT has more expressions than target columns`, or a syntax error for an empty list).

Fixed by checking, before expansion, whether the parameter key resolves to a real entity field whose *declared type* is itself a `Collection` (as opposed to a query parameter key matching a scalar column, e.g. `id`, being compared against a caller-supplied `List` for an `IN` clause). Only in that case is the value passed through untouched to the normal single-value conversion path. See `GenericRepository.convertParamsToSqlValues` / `convertListParams`.

Only `List`-typed fields were actually broken: the pre-fix code special-cased `entry.getValue() instanceof List<?>`, and a `Set`/array value never matches that check, so `@Json private Set<Long> tagIdSet;` and `@Json private Long[] tagIdArray;` were already serialized correctly as a single JSON value even before this fix. The guard was still widened from `List` to `Collection` (rather than left `List`-only) for robustness/consistency, and both shapes now have regression tests locking in the behavior.

### Design decision: `@Json` columns are rejected in standard WHERE conditions

A `@Json` column holds an opaque blob to plain SQL — `=`, `IN`, range comparisons don't have a meaningful, portable semantics against a serialized JSON value (and the bug above shows how easily "value happens to be a List" gets confused with "value is meant for an IN clause"). Rather than let `whereAndEquals("tagIds", ...)` silently produce a technically-valid-but-meaningless query (comparing a JSONB blob to another JSONB blob), `WhereQuery` now explicitly rejects any `@Json` column passed to `whereAndEquals`, `whereAndIn`, `whereAndOperator`, `whereAndColumnOperator`, or `whereAndRange`, throwing `NativSQLException`. `whereExpression(...)` remains available as the escape hatch for dialect-specific JSON operators (e.g. Postgres `->`/`@>`), mirroring how composite types are queried via `(address).city`.

## Verification

- Added `tag_ids`, `tag_id_set`, `tag_id_array`, `external_tag_ids`, `tag_labels`, `tag_scores`, `tag_flags`, `preferences_history` (all `JSONB`) to the Postgres test schema, and matching `@Json`-annotated fields to the `User` test entity: `List<Long> tagIds`, `Set<Long> tagIdSet`, `Long[] tagIdArray`, `List<UUID> externalTagIds`, `List<String> tagLabels`, `List<Double> tagScores`, `List<Boolean> tagFlags`, `List<Preferences> preferencesHistory` (nested POJO element type).
- Repository tests (Postgres only — see design decision below on scope): insert/read `tagIds` with a non-empty list, an empty list, and `null`; insert/read `tagIdSet`/`tagIdArray` with non-empty values; insert/read `externalTagIds`/`preferencesHistory` (UUID, nested POJO) and `tagLabels`/`tagScores`/`tagFlags` (String, Double, Boolean) to confirm the fix is element-type-agnostic; two tests asserting `whereAndEquals`/`whereAndIn` on `tagIds` throw `NativSQLException`.
- Full `nativsql-core` + `nativsql-postgres` test suites pass, including existing `whereAndIn` coverage (confirms the fix didn't regress genuine IN-clause queries).
- `USERGUIDE.md` JSON section updated with the `List<Long>` example and the WHERE restriction.
- `CHANGELOG.md` entry added under `2.10.0`.

### Scope: Postgres only

MySQL/MariaDB/Oracle already have generic `@Json` coverage (POJO fields, via `GenericJSONTypeMapper`) but no dedicated `List<Long>` test. The `convertParamsToSqlValues` fix and the WHERE guard are both in `nativsql-core`, so all dialects benefit from them regardless. Adding matching `List<Long>` tests to the other three dialect modules was considered and deferred — out of scope for this issue, kept as a candidate for a future issue if needed.
