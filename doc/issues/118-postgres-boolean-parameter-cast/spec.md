# Spec: PostgreSQL cannot determine the type of a `Boolean` parameter used without a typed comparison

> Issue: [nativsql#118](https://github.com/heraud/nativsql/issues/118)

## Goal

PostgreSQL's extended query protocol infers a bind parameter's type from the syntactic context
it appears in. When a `Boolean`-typed named parameter is used **without** being compared to a
typed column — e.g. as the predicate itself, or combined with `IS NULL` / `NOT` — PG has nothing
to infer the type from and the query fails at execution time:

```sql
where :estValide
where :filter is null or not :filter
```

```
org.postgresql.util.PSQLException: ERROR: could not determine data type of parameter $1
```

This is a known PostgreSQL/JDBC limitation (see
https://stackoverflow.com/questions/56089400/postgres-sql-could-not-determine-data-type-of-parameter-by-hibernate),
not specific to NativSQL, but NativSQL can avoid it structurally: emit an explicit `::boolean`
cast around every `Boolean` parameter placeholder in generated SQL, so PG always knows the type
regardless of the surrounding syntax.

## Design decision: also cover `findExternal`/`findAllExternal`, via a cached SQL rewrite

An earlier draft scoped this fix to queries **generated** by NativSQL (`WhereBuilder`, `findBy...`
methods), where the placeholder text is produced by `ITypeMapper.formatParameter` and casting is
just a matter of changing what that method returns. That leaves out `findExternal`/
`findAllExternal`, where the caller writes the SQL text by hand — and in practice, most
of the ambiguous-parameter bug reports (including the original StackOverflow issue this spec
started from) come from hand-written queries, not generated ones. Without covering this path, the
fix has limited real-world value.

**The problem:** for a hand-written query, there is no `FieldAccessor` for the placeholder text —
NativSQL doesn't control the SQL string, only the parameter values. Two building blocks make it
possible anyway:

1. **Type source**, tried in order for each parameter name found in the SQL text:
   1. **Explicit `NullableParam`** (see below) — the caller declares the type up front. This is
      the only reliable source when the value is `null`, since `null` carries no Java class to
      inspect and matching real-world filter/exclude parameter names against entity field names
      does not hold in practice (see rejected alternatives below).
   2. **Matching entity field** — reusing the same mechanism `convertParamsToSqlValues` already
      uses (`GenericRepository.java:1650`, `entityFields.getOrNull(paramName)`): when a parameter
      name matches a field of the repository's entity type `T`, that field's static Java type
      drives the cast, exactly as for generated queries.
   3. **Non-null runtime value** — reusing the _other_ existing fallback in the same method
      (`GenericRepository.java:1653-1654`, `new FieldAccessor<Object>(entry.getValue().getClass())`):
      when the parameter isn't `null` and has no matching entity field, its own runtime class is a
      safe, already-precedented source of type info (this is what lets a raw enum value convert
      correctly today even without a field mapping).
   4. **None of the above** (a `null` value, no matching field, no `NullableParam`) — no cast is
      added, `:paramName` unchanged. Documented limitation, not a silent failure: this is exactly
      today's behavior, so nothing regresses.

   Each source ultimately feeds the same `dialect.getMapper(...).formatParameter(...)` call used
   for generated queries — only how the `FieldAccessor`/type is obtained differs.

2. **Rewrite mechanism:** locating `:paramName` occurrences in raw SQL text requires skipping
   string literals and comments, or a `('active')` literal would be corrupted into
   `('(:active)::boolean')`. Spring's `NamedParameterUtils`/`ParsedSql` already implements this
   scanning, but every accessor needed (`getParameterNames`, `getParameterIndexes`, even the
   `ParsedSql` constructor) is package-private to `org.springframework.jdbc.core.namedparam` —
   unusable from NativSQL code even though `parseSqlStatement(sql)` itself is `public`. Spring
   Framework is Apache License 2.0, which permits copying/adapting source into another project
   (with attribution and a "file modified" notice — no copyleft, no obligation to relicense
   NativSQL); since the useful parts can't be consumed as a binary dependency, this is done as a
   **new, minimal** scanner in NativSQL — attributed to Spring Framework as the algorithmic
   source, not a verbatim copy of `NamedParameterUtils` (most of that class handles positional
   `?`/`SqlParameterSource` substitution, which isn't needed here).

**Cost control — cache keyed by the raw SQL string:** the caller's SQL text is a source-level
constant per call site (a `String` literal embedded in a repository method), so the
scan-and-rewrite only needs to happen once per distinct SQL string, not once per call. A
`Map<String, String>` cache (SQL text → rewritten SQL text, or the same string when nothing needs
casting) makes every call after the first a plain map lookup — same asymptotic cost as today. The
rewrite for a given `sql` string is computed once, from whichever `params` map happens to be passed
on the _first_ call for that `sql` — so the cast decision for a given parameter name is only as
good as what that first call reveals (entity field match and `NullableParam` are call-independent
and therefore always safe; the non-null-runtime-value fallback is only as reliable as the first
call happening to pass a non-null value for that parameter). **Contract for callers:** a parameter
that can be `null` in an ambiguous position, and has no matching entity field, must be wrapped in
`NullableParam` on **every** call for that query — not just the first — since only the first
call's shape is actually used to build the cached rewrite; an inconsistently-wrapped parameter
would non-deterministically win or lose its cast depending on call order.

## Design decision: generated-query fix, generalized to every PostgreSQL-mapped type

NativSQL already solves this exact class of problem for some types via `ITypeMapper
.formatParameter`, which lets a mapper emit `(:paramName)::pgtype` instead of the bare
`:paramName` placeholder — used before this issue by `PostgresUUIDTypeMapper`
(`(:paramName)::uuid`), `PostgresEnumMapper` (`(:paramName)::enum_type`),
`PostgresCompositeTypeMapper`, and `PostgresPointTypeMapper`. `formatParameter` is called at
SQL-generation time on the mapper resolved for the field/parameter's **static Java type**
(`GenericDialect.getMapperForType`/`getBooleanMapper()`), not on the runtime value — so the cast
applies uniformly whether the bound value is `true`, `false`, or `null`.

The issue was first reported for `Boolean` (`WHERE :estValide`, `WHERE :filter IS NULL OR NOT
:filter`), but PostgreSQL's "could not determine data type of parameter" failure is not
Boolean-specific — it can happen for any bind parameter used without a typed comparison,
regardless of its declared type. Rather than scope the fix to `Boolean` and wait for the same bug
to be reported per-type, casting is applied **uniformly to every type PostgreSQL maps** —
`String`, all numeric types, `LocalDate`/`LocalDateTime`/`Instant`, `byte[]`, `JSON`, in addition
to the original `Boolean`/`UUID`/enum/composite/PostGIS `Point`. A value cast to its own resolved
type is always a semantic no-op, so applying it unconditionally is safe.

**Cast target resolution** (`PostgresParameterCasts`, new): a parameter's declared `DB_DATA_TYPE`
(set via `@Type`, e.g. an `Integer` field annotated `@Type(BIG_INTEGER)`) is looked up in a fixed
`DbDataType → PostgreSQL type name` table (`STRING→text`, `LONG→bigint`, `BIG_INTEGER→numeric`,
`LOCAL_DATE_TIME→timestamp`, `UUID→uuid`, ...); when no `DB_DATA_TYPE` is declared, the mapper's
own natural PostgreSQL type is used instead (e.g. `Instant`'s natural type is `timestamptz`, even
though `DbDataType.DATE_TIME`/`LOCAL_DATE_TIME` both map to plain `timestamp`). This one lookup
table is enough for every mapper, because it mirrors the `DbDataType` branches those mappers'
`toDatabaseValue` already switch on (see `BooleanTypeMapper`, `IntegerTypeMapper`, etc.) — no
per-mapper cast logic is needed beyond picking the natural type.

**Two ways a mapper gets its cast**, depending on whether it needs PostgreSQL-specific value
conversion beyond the generic mapper:

- **Dedicated subclass** — for mappers that already override `toDatabaseValue`/`fromValue` for
  PostgreSQL (`PostgresBooleanTypeMapper`, `PostgresUUIDTypeMapper`, `PostgresStringTypeMapper`,
  `PostgresByteArrayTypeMapper`, plus the unconditional-cast `PostgresEnumMapper`/
  `PostgresCompositeTypeMapper`/`PostgresPointTypeMapper`/`PostgreJSONTypeMapper`), `formatParameter`
  is one line delegating to `PostgresParameterCasts`.
- **Generic decorator** — for types PostgreSQL doesn't otherwise specialize (`Long`, `Integer`,
  `Short`, `Byte`, `Float`, `Double`, `BigDecimal`, `BigInteger`, `LocalDate`, `LocalDateTime`,
  `Instant`), writing eleven near-identical subclasses just to add one `formatParameter` line each
  would be pure duplication. `PostgresCastingTypeMapper<T>` (new) wraps the generic mapper
  (`ITypeMapper<T>` delegate) and only overrides `formatParameter`; `PostgresDialect` wires it in by
  wrapping `super.getXxxMapper()` per type, without touching `nativsql-core`.

Other dialects (MariaDB, Oracle) are unaffected: the generic mappers in `nativsql-core` are
unchanged, so their `formatParameter` still returns the bare `:paramName` unless/until those
dialects get their own casting decorator — they are not known to share this bug and casting syntax
is PostgreSQL-specific (`::type`).

**List-valued parameters are excluded.** A parameter bound to a `List`/array (`WHERE id IN
(:ids)`) is expanded into `?, ?, ...` by the JDBC template's own named-parameter parsing, which
looks for the literal `:name` token — if `NamedParamSqlCaster` (see below) had already rewritten
it into `(:name)::bigint`, the expansion would corrupt the SQL into `IN ((?, ?)::bigint)`. Both
`NamedParamSqlCaster.resolveReplacement` and (implicitly, since it never reaches a formatting
mapper) the generated-query path treat any `Collection`/array-valued parameter as never castable.

## Architecture

### New `PostgresParameterCasts` — shared cast-target resolution

`nativsql-postgres/src/main/java/ovh/heraud/nativsql/db/postgres/mapper/PostgresParameterCasts.java`,
a stateless helper used by every casting mapper:

```java
public final class PostgresParameterCasts {

    private static final Map<DbDataType, String> SQL_TYPE_BY_DATA_TYPE = new EnumMap<>(DbDataType.class);
    static {
        SQL_TYPE_BY_DATA_TYPE.put(DbDataType.STRING, "text");
        SQL_TYPE_BY_DATA_TYPE.put(DbDataType.LONG, "bigint");
        SQL_TYPE_BY_DATA_TYPE.put(DbDataType.BIG_INTEGER, "numeric");
        SQL_TYPE_BY_DATA_TYPE.put(DbDataType.BOOLEAN, "boolean");
        SQL_TYPE_BY_DATA_TYPE.put(DbDataType.UUID, "uuid");
        // ... one entry per DbDataType with a PostgreSQL equivalent
    }

    public static String cast(String paramName, String sqlType) {
        return "(:" + paramName + ")::" + sqlType;
    }

    public static String castForType(String paramName, Map<ParamKey, Object> params, String naturalSqlType) {
        DbDataType dataType = (DbDataType) params.get(TypeParamKey.DB_DATA_TYPE);
        String sqlType = dataType == null ? naturalSqlType
                : SQL_TYPE_BY_DATA_TYPE.getOrDefault(dataType, naturalSqlType);
        return cast(paramName, sqlType);
    }
}
```

`castForType` replaces the old per-mapper "cast only if `dataType` matches my own type, else leave
unchanged" logic: instead of skipping the cast when the declared `DB_DATA_TYPE` doesn't match the
mapper's own type, it casts to *that* type instead — e.g. a `Boolean` field annotated
`@Type(STRING)` now formats as `(:paramName)::text`, not `:paramName`, since the value is really
being sent as text.

### `PostgresBooleanTypeMapper`/`PostgresUUIDTypeMapper`/`PostgresStringTypeMapper`/`PostgresByteArrayTypeMapper`

Each keeps its existing PostgreSQL-specific `toDatabaseValue`/`fromValue` overrides and adds a
one-line `formatParameter`:

```java
public class PostgresBooleanTypeMapper extends BooleanTypeMapper {
    @Override
    public String formatParameter(String paramName, Map<ParamKey, Object> params) {
        return PostgresParameterCasts.castForType(paramName, params, "boolean");
    }
}
```

`PostgresEnumMapper`/`PostgresCompositeTypeMapper` (cast to their `SQL_TYPE` param, unconditional)
and `PostgresPointTypeMapper`/`PostgreJSONTypeMapper` (fixed `::geometry`/`::jsonb`, unconditional)
call `PostgresParameterCasts.cast(paramName, sqlType)` directly instead — they don't vary by
`DB_DATA_TYPE`.

### New `PostgresCastingTypeMapper<T>` — generic decorator for types with no PostgreSQL-specific conversion

`nativsql-postgres/src/main/java/ovh/heraud/nativsql/db/postgres/mapper/PostgresCastingTypeMapper.java`
wraps any `ITypeMapper<T>`, delegating `map`/`toDatabase` unchanged and overriding only
`formatParameter`:

```java
public final class PostgresCastingTypeMapper<T> implements ITypeMapper<T> {
    private final ITypeMapper<T> delegate;
    private final String naturalSqlType;

    public PostgresCastingTypeMapper(ITypeMapper<T> delegate, String naturalSqlType) {
        this.delegate = delegate;
        this.naturalSqlType = naturalSqlType;
    }

    @Override public T map(...) { return delegate.map(...); }
    @Override public T map(...) { return delegate.map(...); }
    @Override public Object toDatabase(T value, Map<ParamKey, Object> params) { return delegate.toDatabase(value, params); }

    @Override
    public String formatParameter(String paramName, Map<ParamKey, Object> params) {
        return PostgresParameterCasts.castForType(paramName, params, naturalSqlType);
    }
}
```

### `PostgresDialect` registers every mapper

```java
@Override
public ITypeMapper<Boolean> getBooleanMapper() {
    return new PostgresBooleanTypeMapper();
}

@Override
public ITypeMapper<Long> getLongMapper() {
    return new PostgresCastingTypeMapper<>(super.getLongMapper(), "bigint");
}
// ... same pattern for Integer→"integer", Short/Byte→"smallint", Float→"real",
// Double→"double precision", BigDecimal/BigInteger→"numeric", LocalDate→"date",
// LocalDateTime→"timestamp", Instant→"timestamptz"
```

`getBooleanMapper`/`getUUIDMapper`/`getStringMapper`/`getByteArrayMapper`/`getEnumMapper`/
`getCompositeMapper`/`getJsonMapper` return the dedicated subclasses (unchanged pattern); every
other `getXxxMapper()` wraps `super.getXxxMapper()` in `PostgresCastingTypeMapper` with that type's
natural PostgreSQL name.

### Data flow

```
repository method with a Boolean parameter "estValide"
  → GenericDialect.getMapperForType(Boolean.class) → PostgresDialect.getBooleanMapper()
      → PostgresBooleanTypeMapper
  → GenericRepository.formatParameter("estValide", params)
      → mapper.formatParameter(...) → "(:estValide)::boolean"
  → generated SQL: "where (:estValide)::boolean"
  → PG can determine $1's type from the cast → no more
    "could not determine data type of parameter" error
```

The same flow applies unchanged for any other type, e.g. a `Long id` parameter now generates
`"where id = (:id)::bigint"` via `PostgresCastingTypeMapper` — except when `id`'s value is a
`List` (`WHERE id IN (:ids)`), which is never cast (see list-valued-parameter exclusion above).

### New `NullableParam`

`nativsql-core/src/main/java/ovh/heraud/nativsql/repository/NullableParam.java` — a minimal,
immutable holder for "this named parameter is `null`, but here is its type":

```java
public final class NullableParam {

    private final Class<?> type;

    private NullableParam(Class<?> type) {
        this.type = type;
    }

    public static NullableParam of(Class<?> type) {
        return new NullableParam(type);
    }

    public Class<?> getType() {
        return type;
    }
}
```

Used as a `params` map value in place of a plain (possibly-null) value, only for parameters that
are: not backed by an entity field, and can legitimately be `null` in an ambiguous SQL position.
Non-null values and entity-field-matched parameters never need it.

### `NamedParamSqlCaster` — scanner + cached rewrite for `findExternal`/`findAllExternal`

New class in `nativsql-core/src/main/java/ovh/heraud/nativsql/repository/NamedParamSqlCaster.java`.
Header comment attributes the scanning algorithm (skip string literals `'...'`, double-quoted
identifiers `"..."`, and `--`/`/* */` comments while scanning for `:name` tokens) as adapted from
Spring Framework's `NamedParameterUtils` (Apache License 2.0) — a fresh implementation, not a
copy, since only the scanning behavior is needed (not positional-`?`/`SqlParameterSource`
handling).

```java
public class NamedParamSqlCaster {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Returns sql with an explicit dialect cast injected around every named
     * parameter placeholder whose type can be determined — via a NullableParam
     * value, a matching entityClass field, or (for non-null values) the
     * value's own runtime class — and whose mapper requests a cast
     * (formatParameter differs from ":name"). Result is cached by the raw sql
     * string, computed from the first params map seen for that sql.
     */
    public String castNamedParameters(String sql, Map<String, Object> params, Fields entityFields,
            DatabaseDialect dialect, AnnotationManager annotationManager) {
        return cache.computeIfAbsent(sql,
                s -> rewrite(s, params, entityFields, dialect, annotationManager));
    }

    private String rewrite(String sql, Map<String, Object> params, Fields entityFields,
            DatabaseDialect dialect, AnnotationManager annotationManager) {
        // scan sql once, collecting each top-level ":name" token (skipping
        // '...' / "..." / -- / * comments). For each distinct name found:
        //   0. params.get(name) is a Collection/array (IN-list) -> skip, never cast
        //   1. params.get(name) instanceof NullableParam np -> np.getType()
        //   2. entityFields.getOrNull(name) -> that field's declared type
        //   3. params.get(name) is non-null -> params.get(name).getClass()
        //   4. otherwise -> skip, no cast for this name
        // then call dialect.getMapper(fieldAccessor, annotationManager)
        //   .formatParameter(name, typeInfo.getParams()); replace every
        //   occurrence of ":name" with the result if it differs from ":name".
        // return sql unchanged if nothing matched.
    }
}
```

- One instance per `GenericRepository` (same lifetime/scope as `rowMapperFactory`), so the cache
  is naturally bounded by the number of distinct `findExternal`/`findAllExternal` call sites for
  that repository — no cross-repository sharing needed, consistent with `RowMapperFactory.cache`
  being an instance field.
- `GenericRepository.findAllExternal` (`GenericRepository.java:1882-1886`) calls
  `namedParamSqlCaster.castNamedParameters(sql, params, entityFields, databaseDialect,
annotationManager)` — using the **raw** `params` (before `convertParamsToSqlValues` unwraps
  `NullableParam`/converts values) — before passing the rewritten `sql` to
  `convertParamsToSqlValues` and `jdbcTemplate.query(...)`; `findExternal` delegates to
  `findAllExternal` already (`GenericRepository.java:1850-1853`), so both are covered by one
  change.
- `convertParamsToSqlValues` (`GenericRepository.java:1640`) gets one new branch: when a `params`
  entry's value is a `NullableParam`, put `null` in the converted map under that key (the DB bind
  value is always `null` regardless of declared type) instead of attempting `convertToSqlValue` on
  the wrapper itself.

### Data flow (findExternal)

```
findAllExternal("select * from users where :active is null or active = :active",
                 Map.of("active", null), User.class)
  → namedParamSqlCaster.castNamedParameters(sql, params, ...)
      cache miss → scan sql → "active" has no NullableParam, but matches
                   User.active (Boolean field) → static field type used
      → dialect.getMapper(activeFieldAccessor, ...) → PostgresBooleanTypeMapper
      → formatParameter("active", ...) → "(:active)::boolean" (!= ":active")
      → rewritten = "select * from users where (:active)::boolean is null
                     or active = (:active)::boolean"
      cache.put(sql, rewritten)
  → jdbcTemplate.query(rewritten, convertedParams, rowMapper)
  → no more "could not determine data type of parameter" for active = null
```

```
findAllExternal(sql, Map.of("filterServiceToilette", NullableParam.of(Boolean.class)), User.class)
  → "filterServiceToilette" has no matching User field, but is a NullableParam
    → type = Boolean.class → PostgresBooleanTypeMapper → "(:filterServiceToilette)::boolean"
  → works whether the actual filter value passed on this or later calls is null, true, or false
```

Second call with the same `sql` string: cache hit, no re-scan — provided every call wraps the same
ambiguous nullable parameters consistently (see caching contract above).

## API

### `PostgresParameterCasts` (new, `ovh.heraud.nativsql.db.postgres.mapper` package, public — used across mapper and postgis packages)

```java
public final class PostgresParameterCasts {
    public static String cast(String paramName, String sqlType)
    public static String castForType(String paramName, Map<ParamKey, Object> params, String naturalSqlType)
}
```

### `PostgresCastingTypeMapper<T>` (new, `ovh.heraud.nativsql.db.postgres.mapper` package)

```java
public final class PostgresCastingTypeMapper<T> implements ITypeMapper<T> {
    public PostgresCastingTypeMapper(ITypeMapper<T> delegate, String naturalSqlType)
    // map/toDatabase delegate to `delegate`; formatParameter casts via PostgresParameterCasts
}
```

### `PostgresBooleanTypeMapper` (`ovh.heraud.nativsql.db.postgres.mapper` package)

```java
public class PostgresBooleanTypeMapper extends BooleanTypeMapper {
    @Override
    public String formatParameter(String paramName, Map<ParamKey, Object> params)
}
```

### `PostgresDialect` — every `getXxxMapper()` now casts

```java
@Override
public ITypeMapper<Boolean> getBooleanMapper()      // dedicated subclass
@Override
public ITypeMapper<UUID> getUUIDMapper()             // dedicated subclass
@Override
public ITypeMapper<String> getStringMapper()         // dedicated subclass
@Override
public ITypeMapper<byte[]> getByteArrayMapper()      // dedicated subclass
@Override
public ITypeMapper<Long> getLongMapper()             // PostgresCastingTypeMapper wrapping super
@Override
public ITypeMapper<Integer> getIntegerMapper()       // PostgresCastingTypeMapper wrapping super
@Override
public ITypeMapper<Short> getShortMapper()           // PostgresCastingTypeMapper wrapping super
@Override
public ITypeMapper<Byte> getByteMapper()             // PostgresCastingTypeMapper wrapping super
@Override
public ITypeMapper<Float> getFloatMapper()           // PostgresCastingTypeMapper wrapping super
@Override
public ITypeMapper<Double> getDoubleMapper()         // PostgresCastingTypeMapper wrapping super
@Override
public ITypeMapper<BigDecimal> getBigDecimalMapper() // PostgresCastingTypeMapper wrapping super
@Override
public ITypeMapper<BigInteger> getBigIntegerMapper() // PostgresCastingTypeMapper wrapping super
@Override
public ITypeMapper<LocalDate> getLocalDateMapper()   // PostgresCastingTypeMapper wrapping super
@Override
public ITypeMapper<LocalDateTime> getLocalDateTimeMapper() // PostgresCastingTypeMapper wrapping super
@Override
public ITypeMapper<Instant> getInstantMapper()       // PostgresCastingTypeMapper wrapping super
```

`getEnumMapper`/`getCompositeMapper`/`getJsonMapper` were already overridden pre-issue and now also
cast (via `PostgresParameterCasts.cast`, unconditional).

### `NullableParam` (new, `ovh.heraud.nativsql.repository` package)

```java
public final class NullableParam {
    public static NullableParam of(Class<?> type)
    public Class<?> getType()
}
```

### `NamedParamSqlCaster` (new, `ovh.heraud.nativsql.repository` package, package-visible — internal to `GenericRepository`)

```java
class NamedParamSqlCaster {
    String castNamedParameters(String sql, Map<String, Object> params, Fields entityFields,
            DatabaseDialect dialect, AnnotationManager annotationManager)
}
```

### `GenericRepository` — no public signature change

`findExternal`/`findAllExternal` signatures are unchanged; callers opt into the fix by using
`NullableParam.of(type)` as a params-map value where needed. `convertParamsToSqlValues` gets one
new internal branch to unwrap `NullableParam` into a plain `null` bind value.

## Error handling

| Situation                                                                                                     | Behaviour                                                                                                                   |
| ------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| Any PostgreSQL-mapped scalar parameter, `DB_DATA_TYPE` null (default), generated query                        | SQL placeholder emitted as `(:paramName)::<natural type>` — PG always knows the type, `null` and any value bind without error |
| Same parameter, `DB_DATA_TYPE` explicitly set to a *different* type (e.g. `Boolean` field `@Type(STRING)`)     | cast follows the **declared** type instead (`(:paramName)::text`) — the value is genuinely sent as that type                |
| Parameter bound to a `List`/array (`WHERE col IN (:name)`)                                                     | never cast, on any dialect — casting would corrupt the JDBC template's own `?, ?, ...` list expansion                        |
| Any scalar parameter, other dialects (MariaDB, Oracle)                                                        | unchanged, no cast added — this fix is PostgreSQL-only (see `MariaDBDialectNoPostgresCastTest`/`OracleDialectNoPostgresCastTest`) |
| `findExternal`/`findAllExternal` parameter name matches an entity field of `T`                                | cast applied automatically, no `NullableParam` needed, works for `null` and non-null values                                 |
| `findExternal`/`findAllExternal` parameter has no matching field, non-null value                              | cast applied automatically from the value's runtime class (reuses the existing `convertParamsToSqlValues` fallback)         |
| `findExternal`/`findAllExternal` parameter has no matching field, `null`, wrapped in `NullableParam.of(type)` | cast applied from the declared type — must be used consistently on every call for that SQL text (see caching contract)      |
| `findExternal`/`findAllExternal` parameter has no matching field, `null`, **not** wrapped                     | no cast added — unchanged, documented limitation                                                                            |
| `:name` already followed by an explicit `::type` cast in the SQL text (any type)                              | left unchanged — not doubled up (checked via `sql.startsWith("::", token[1])`)                                              |
| `:name` already wrapped in a standard `CAST(:name AS type)`                                                   | **not** detected (unlike bare `::`) — gets nested, e.g. `CAST((:name)::boolean AS boolean)`; harmless but redundant         |

## Patterns reused

| Pattern                                                                                     | Source                                                                                                                                    |
| ------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| Dialect-specific `::type` cast via `formatParameter` override                               | `PostgresUUIDTypeMapper.formatParameter` (`(:paramName)::uuid`), pre-issue                                                                |
| Shared cast-target resolution across all mappers                                            | `PostgresParameterCasts.castForType` — one `DbDataType → pg type` table reused by every mapper's `formatParameter`                        |
| Decorator to add PostgreSQL casting to a generic mapper without a dedicated subclass         | `PostgresCastingTypeMapper<T>` wrapping `ITypeMapper<T>`, used for `Long`/`Integer`/.../`Instant` in `PostgresDialect`                     |
| Per-dialect mapper registration via `getXxxMapper()` override                               | `PostgresDialect.getUUIDMapper()`/`getStringMapper()`/`getByteArrayMapper()`/... (now every `getXxxMapper()`)                              |
| Subclassing the generic mapper to reuse `fromValue`/`toDatabaseValue` unchanged             | `PostgresUUIDTypeMapper extends UUIDTypeMapper`                                                                                           |
| Static field-type resolution for a raw-query parameter name                                 | `convertParamsToSqlValues`'s `entityFields.getOrNull(paramName)` (`GenericRepository.java:1650`)                                          |
| Runtime-class fallback for a non-null, unmapped parameter                                   | `convertParamsToSqlValues`'s `new FieldAccessor<Object>(entry.getValue().getClass())` (`GenericRepository.java:1653-1654`)                |
| Instance-level `ConcurrentHashMap` cache scoped to the repository                           | `RowMapperFactory.cache` (`RowMapperFactory.java:25`)                                                                                     |

## Tests

- `PostgresBooleanTypeMapperTest` — `params` without `DB_DATA_TYPE` or with `BOOLEAN` →
  `"(:p)::boolean"`; `STRING`/`INTEGER` → cast to `"(:p)::text"`/`"(:p)::integer"` (follows the
  declared type, not "no cast").
- `PostgresDialectFormatParameterTest` (parameterized, real `PostgresDialect`/`PostgresPostGISDialect`,
  real `User`/`ContactInfo` test entities) — every mapped type casts to its expected PostgreSQL
  type: `Boolean→boolean`, `UUID→uuid`, enum→its `SQL_TYPE`, composite→its `SQL_TYPE`,
  `Point→geometry`, `Long→bigint`, `Integer` with `@Type(BIG_INTEGER)`→`numeric`, `String→text`,
  `LocalDateTime→timestamp`, `Instant→timestamptz`.
- `MariaDBDialectNoPostgresCastTest` / `OracleDialectNoPostgresCastTest` (parameterized, same type
  surface) — every type's `formatParameter` stays `":p"` unchanged on non-PostgreSQL dialects.
- Integration test (Testcontainers, `nativsql-postgres`) reproducing the original bug for generated
  queries: a repository method with a query shaped like `where (:active is null or not :active)`
  (or equivalent boolean-only predicate) on a `Boolean` field/parameter, called with `null`,
  `true`, and `false` — must execute without `PSQLException: could not determine data type of
parameter`, matching the expected rows for each case.
- `NamedParamSqlCasterTest` (unit, no DB):
  - parameter name matches an entity field → cast applied, `null` and non-null both work
  - parameter name has no match, non-null value → cast applied from runtime class
  - parameter name has no match, `NullableParam.of(Boolean.class)` → cast applied
  - parameter name has no match, plain `null`, no `NullableParam` → no cast, sql unchanged
  - `:name` occurring inside a string literal (`'text with :notaparam inside'`) → not touched
  - a `List`-valued parameter used in `IN (:name)` → never cast (regression test for the
    `id IN (?, ?)` corruption bug found while generalizing casting to `Long`)
  - `UUID`/enum parameters via a synthetic casting dialect → cast, proving genericity beyond `Boolean`
  - second call with the same `sql` string → cached result returned, no re-scan
- `NamedParamSqlCasterEngineQueriesTest` (parameterized, cases loaded from
  `named-param-sql-caster-engine-queries.txt`) — ~30 cases covering per-engine quoting/comment
  syntax (PostgreSQL/MySQL/MariaDB/Oracle), standard SQL constructs (CTE, joins, window functions,
  subqueries, `INSERT`/`UPDATE`/`DELETE`, `UNION`, `CASE`), and colon-bearing constructs that are
  not named parameters (array slices, JSON string literals, an existing `::cast`, a `CAST(...)`
  call) — the caster must cast only the real bound token and leave everything else byte-identical.
- Integration test (Testcontainers, `nativsql-postgres`) reproducing the original StackOverflow bug
  via `findExternal`: a hand-written query with a `NullableParam`-wrapped boolean filter (mirroring
  the real `filterServiceToilette`-style pattern), called with `null`, `true`, and `false` — must
  execute without `PSQLException`, matching expected rows for each case.
