# NativSQL Architecture

## Module structure

| Module | Role |
|--------|------|
| `nativsql-core` | Interfaces, abstractions, generic implementations — no DB-specific code |
| `nativsql-mysql-commons` | Shared MySQL/MariaDB dialect and mappers |
| `nativsql-mysql` | MySQL module (includes `nativsql-mysql-commons`) |
| `nativsql-mariadb` | MariaDB module (includes `nativsql-mysql-commons`) |
| `nativsql-postgres` | PostgreSQL dialect, PostGIS support |
| `nativsql-oracle` | Oracle dialect |
| `nativsql-test-commons` | Shared test infrastructure |

Build: Gradle — `./gradlew compileJava`, `./gradlew test`, `./gradlew build`.

---

## Dialect chain (Chain of Responsibility)

```
DatabaseDialect  (interface)
  └── AbstractChainedDialect  (delegates everything to nextDialect)
        └── GenericDialect    (end of chain, all base implementations)
              ├── PostgresDialect   (overrides enum, JSON, composite, UUID, String, byte[])
              │     └── PostgresPostGISDialect  (overrides Point mapper)
              ├── MySQLDialect      (overrides getGeneratedKey)
              └── OracleDialect     (overrides UUID, getGeneratedKey)
```

`AbstractChainedDialect` holds a `nextDialect` reference and delegates every method to it. Concrete dialects extend `GenericDialect` (or `AbstractChainedDialect` when chaining to a specific dialect) and override only what they need.

### Mapper factory methods

Each type has a dedicated factory method on `DatabaseDialect`, implemented in `GenericDialect`, delegated in `AbstractChainedDialect`. Subclasses override to inject DB-specific implementations:

```java
ITypeMapper<String>      getStringMapper();
ITypeMapper<Long>        getLongMapper();
ITypeMapper<UUID>        getUUIDMapper();
ITypeMapper<byte[]>      getByteArrayMapper();
// ... one per standard type
<E extends Enum<E>> ITypeMapper<E> getEnumMapper();
<T> ITypeMapper<T>       getJsonMapper();
<T> ITypeMapper<T>       getCompositeMapper();
```

`GenericDialect.getMapper(FieldAccessor, AnnotationManager)` dispatches to the right factory by inspecting the field type and its `TypeInfo` params:

1. Primitive → throw `NativSQLException` (use boxed type)
2. Enum → `getEnumMapper()`
3. Field/class carries `TypeParamKey.JSON` → `getJsonMapper()`
4. Field/class carries `TypeParamKey.COMPOSITE` → `getCompositeMapper()`
5. Known type → `getMapperForType()` (delegates to individual `getXxxMapper()` methods)
6. Unknown type → `getDefaultMapper()` (passes through as JDBC object)

---

## Type system — TypeInfo and TypeParamKey

Every field has a `TypeInfo` object cached in `AnnotationManager`. `TypeInfo` carries a single `Map<ParamKey, Object>` that holds **all** field-level parameters — both annotation-derived and programmatically registered.

```java
enum TypeParamKey implements ParamKey {
    DB_DATA_TYPE,   // DbDataType — from @Type
    ENCRYPTED,      // Boolean    — from @Encrypted
    ALGO,           // CryptAlgorithm — from @CryptAlgo
    KEY_PROVIDER,   // Class<ICryptKeyProvider> — from @CryptKeyProvider
    COST,           // Integer    — from @CryptCost
    PREFIX,         // String     — from @CryptPrefix
    SQL_TYPE,       // String     — DB type name for enum/composite (@SqlType or programmatic)
    JSON,           // Boolean    — field/class is a JSON type
    COMPOSITE       // Boolean    — field/class is a composite type
}
```

This map is passed through the entire call chain — from `getMapper()` selection down to `ITypeMapper.toDatabaseValue()` and `ITypeMapper.formatParameter()`. No field-level context is lost at any step.

### AnnotationManager public API

```java
// Type detection
TypeInfo getTypeInfo(FieldAccessor<?> fieldAccessor);

// Programmatic registration
void setEnumSqlType(Class<?> enumClass, String sqlType);
void setJsonInfo(Class<?> clazz);
void setCompositeTypeInfo(Class<?> clazz, String sqlType);
void setDbDataType(Class<?> clazz, String fieldName, DbDataType dataType);

// Relationship metadata
MappedByInfo   getMappedByInfo(FieldAccessor<?> fieldAccessor);
OneToManyAssociation getOneToManyInfo(FieldAccessor<?> fieldAccessor);

// @OnUpdate / @OnInsert metadata (framework-managed computed values, see doc/issues/76-on-update)
List<ComputedFieldInfo> getComputedFieldInfos(Class<?> entityClass);
void setComputedFieldInfo(Class<?> clazz, String fieldName, ComputedValueProvider<?> provider);
List<ComputedFieldInfo> getOnInsertFieldInfos(Class<?> entityClass);
void setOnInsertFieldInfo(Class<?> clazz, String fieldName, ComputedValueProvider<?> provider);
```

---

## Mapper hierarchy

```
ITypeMapper<T>                     (interface)
  └── AbstractTypeMapper<T>        (handles encryption, error formatting)
        ├── StringTypeMapper       (generic)
        ├── LongTypeMapper         (generic)
        ├── UUIDTypeMapper         (generic)
        │     └── PostgresUUIDTypeMapper   (::uuid cast)
        ├── ByteArrayTypeMapper    (generic)
        │     └── PostgresByteArrayTypeMapper  (bytea)
        ├── EnumStringMapper       (generic — enum.name())
        │     └── PostgresEnumMapper          (PGobject with SQL type name)
        ├── GenericJSONTypeMapper  (Jackson serialization)
        │     └── PostgreJSONTypeMapper        (JSONB)
        └── PostgresCompositeTypeMapper         (PGobject (v1,v2)::type_name)
```

### Key mapper signatures

```java
// Reading from DB
T fromValue(Object raw, Map<ParamKey, Object> params) throws ConversionException;

// Writing to DB
Object toDatabaseValue(T value, Map<ParamKey, Object> params) throws ConversionException;

// SQL parameter formatting (e.g. ::uuid, ::type_name)
String formatParameter(String paramName, Map<ParamKey, Object> params);
```

---

## Query builders — FindQuery and DeleteQuery

`FindQuery` and `DeleteQuery` are WHERE-clause builders used to construct typed SQL queries without raw string concatenation. Both are created via protected factory methods on `GenericRepository` and accept either string column names or type-safe getter references. `CountQuery` and `ExistsQuery` are two further sibling builders under the same base class, generating `SELECT COUNT(*) FROM ...` and `SELECT 1 FROM ...` (dialect-wrapped into `EXISTS` by `GenericRepository.exists(...)`) respectively.

### Class hierarchy

```
WhereQuery<T, ID, Self>   (ovh.heraud.nativsql.util)
  ├── FindQuery<T, ID>              extends WhereQuery<T,ID,FindQuery<T,ID>>
  ├── DeleteQuery<T, ID>            extends WhereQuery<T,ID,DeleteQuery<T,ID>>
  ├── CountQuery<T, ID>             extends WhereQuery<T,ID,CountQuery<T,ID>>
  └── ExistsQuery<T, ID>            extends WhereQuery<T,ID,ExistsQuery<T,ID>>
```

`WhereQuery` uses the CRTP pattern (`Self` parameter) so that all fluent WHERE methods return the correct concrete type without casting. It holds the `WhereClause`, `GenericRepository`, and `AnnotationManager` references, and provides all WHERE methods and `getParameters()`.

**`FindQuery<T, ID>`** (`newFindQuery()`) — builds `SELECT` statements. Adds `.select(...)`, `.selectExpression(...)`, `.leftJoin(...)`, `.innerJoin(...)`, `.associate(...)`, `.orderBy(...)` on top of the WHERE methods inherited from `WhereQuery`. Passed directly to `find()` (single result) or `findAll()` (list) — or to `find(query, resultClass)` / `findAll(query, resultClass)` to map into a subtype `R extends T` (see "Mapping into a subtype" below).

### `selectExpression` — computed SQL-expression columns

`FindQuery.selectExpression(alias, sql[, params])` adds a raw SQL expression to the SELECT clause, stored as an `ExpressionColumn` (`ovh.heraud.nativsql.util`, a `@Data` class with `alias`/`sql`/`params` fields — same one-type-per-file, Lombok-`@Data` convention as sibling `Join`/`Association`) in a dedicated `expressionColumns` list, kept separate from the plain `columns` list. `Getter<R>` overloads derive `alias` via `ReflectionUtils.getColumnName` — `R` is a free type parameter, not tied to `T`, so a getter from a report subtype can be used directly.

- `buildPrefixedColumns` emits each expression as `<resolved-sql> AS "<alias>"`, substituting the literal token `{{table}}` in `sql` with the query's own table name (the same value used to prefix every other column) — this lets a correlated subquery reference the outer row without hardcoding the table name.
- `select(...)` and `selectExpression(...)` validate against each other: an alias already used by the other throws `NativSQLException`, in both directions (`columns` and `expressionColumns` are checked mutually on every call).
- `buildSql` throws `NativSQLException` ("At least one column must be selected") if `columns`, `expressionColumns`, and `joins` are all empty.
- `FindQuery.getParameters()` overrides `WhereQuery.getParameters()` to merge each `ExpressionColumn`'s params on top of the WHERE-based map, throwing `NativSQLException` on a name collision.
- Row mapping never distinguishes a computed column from a plain one — `GenericRowMapper` only looks at the column label — so `selectExpression(alias, ...)` can be used alone, with `alias` matching a field the mapped class already has, to override that field's value with a computed one (works on a plain `find(query)`/`findAll(query)` too, not just the `resultClass` overloads).

### Mapping into a subtype — `find`/`findAll(query, resultClass)`

`GenericRepository<T, ID>` exposes `protected <R extends T> R find(FindQuery<T, ID> query, Class<R> resultClass)` and `protected <R extends T> List<R> findAll(FindQuery<T, ID> query, Class<R> resultClass)`, mirroring `find(query)`/`findAll(query)` but calling `findAllExternal(sql, params, resultClass)` instead of hardcoding `entityClass`. The `<R extends T>` bound is enforced by the compiler — no runtime reflection check needed, unlike a composition-based design would require. `find(query, resultClass)` still batch-loads associations via `loadAssociationsInBatch` when `query.hasAssociations()` is true; `findAll(query, resultClass)` intentionally does not, for N+1-avoidance parity with `findAll(query)`. Both methods stay `protected`: a `FindQuery` is never exposed publicly — concrete repositories add their own named wrapper method (e.g. `findUserActivityReports()`) that builds the query and calls `findAll(query, SomeReport.class)` internally.

This lets a "report" class that `extends T` (inheriting all of `T`'s fields via `ReflectionUtils.getFields`'s hierarchy walk) be populated with `T`'s own columns plus extra computed fields from `selectExpression(...)`, while still supporting joins and `associate(...)` batch-loading as-is — `loadAssociationsInBatch`'s parameter type is `List<? extends T>` (widened from `List<T>`) specifically to allow this. See [doc/issues/98-entity-composition/spec.md](../doc/issues/98-entity-composition/spec.md) for the full design rationale, including why inheritance was chosen over a composition-based alternative.

**`DeleteQuery<T, ID>`** (`newDeleteQuery()`) — builds `DELETE` statements. Inherits all WHERE methods from `WhereQuery`. Entry points:
- `delete(DeleteQuery)` — expects exactly 1 deleted row; throws `NativSQLException` otherwise
- `deleteAll(DeleteQuery)` — deletes 0 or N rows with no row count validation

### WHERE condition types

All WHERE methods are available on both `FindQuery` and `DeleteQuery` and guard against encrypted columns that use non-deterministic or one-way algorithms.

| Method | SQL produced | Notes |
|--------|-------------|-------|
| `whereAndEquals(col, val)` | `col = :col` | — |
| `whereAndIn(col, list)` | `col IN (:col)` | — |
| `whereAndOperator(col, Operator, val)` | `col <op> :col` | generic single-value operator |
| `whereAndColumnOperator(col, ColumnOperator)` | `col IS NULL` / `col IS NOT NULL` | no parameter bound |
| `whereAndRange(col, RangeOperator, low, high)` | `col BETWEEN :colLow AND :colHigh` | null bound → `NativSQLException` |
| `whereExpression(expr, param, val)` | `expr = :param` | multiple calls accumulate |

### Dot-notation column paths (FindQuery only)

Any `whereAnd*` method accepts a dot-notation path `"assoc.column"` to filter on a joined table's column. The raw path is stored as-is in `WhereClause`; resolution is deferred to SQL build time via a `JoinResolver` registered by `FindQuery.buildSql()`.

**`JoinResolver`** — functional interface in `ovh.heraud.nativsql.util`:

```java
@FunctionalInterface
public interface JoinResolver {
    String resolve(String path, IdentifierConverter identifierConverter);
}
```

**Resolution flow:**

1. `FindQuery.buildSql()` registers `this::resolveJoinColumn` on `whereClause.withJoinResolver(...)`.
2. `WhereClause.toDbCol(column, converter)` detects a dot: delegates to `joinResolver.resolve(column, converter)`.
3. `FindQuery.resolveJoinColumn(path, converter)` splits on `.`, finds the matching `Join` by association name, returns `join.getRepository().getTableName() + "." + converter.toDB(column)`.
4. If no resolver is set (e.g. `DeleteQuery`), `WhereClause` throws `NativSQLException`.

**`SqlUtils.columnPathToParamName(String column)`** — converts a dot path to a valid named-parameter key (no dots allowed in Spring JDBC parameter names):

```
"status"       → "status"       (unchanged)
"group.name"   → "groupName"    (camelCase join)
"group.createdAt" → "groupCreatedAt"
"a.b.col"      → NativSQLException (only one dot supported)
"group."       → NativSQLException (empty column segment)
```

Called in three places to keep SQL and parameter map consistent:
- `WhereClause.buildConditionStrings()` — produces `:groupName` in SQL
- `WhereQuery.getParameters()` — produces `{"groupName": value}` map key
- `WhereQuery.whereAndRange()` — derives `camelBase` for `Low`/`High` suffix params

### Operator enums

**`Operator`** — single-value operators: `EQUALS`, `IN`, `LESS_THAN`, `LESS_OR_EQUAL`, `GREATER_THAN`, `GREATER_OR_EQUAL`, `NOT_EQUALS`, `LIKE`. Each constant holds a `WhereExpressionBuilder` strategy `(dbCol, paramName) → SQL fragment`.

**`ColumnOperator`** — parameter-less operators: `IS_NULL`, `IS_NOT_NULL`. Each constant holds a `ColumnWhereExpressionBuilder` strategy `(dbCol) → SQL fragment`.

**`RangeOperator`** — two-parameter operators: `BETWEEN`. Each constant holds a `RangeWhereExpressionBuilder` strategy `(dbCol, paramLow, paramHigh) → SQL fragment`. Parameter names are derived from the camelCase column name with `Low` / `High` suffixes.

---

## Repository flow

`GenericRepository` orchestrates the full read/write cycle:

**Write path (insert / update):**
1. `extractValues(entity, fields)` — reflect on the entity, return raw Java values
2. `convertParamsForLogging(rawParams)` — convert everything except encrypted fields → logged as `DB.PARAMS`
3. `convertParamsToSqlValues(rawParams)` — convert all fields (enum→PGobject, composite→PGobject, encrypt, etc.) → passed to `NamedParameterJdbcTemplate`

**Read path:**
1. `GenericRowMapper` iterates over `ResultSet` columns
2. For each column, looks up the `TypeInfo` via `AnnotationManager`
3. Calls `ITypeMapper.fromValue(raw, params)` to convert to Java

`RowMapperFactory.getRowMapper(resultClass, dialect, identifierConverter)` — used by
`findExternal`/`findAllExternal` and the `find`/`findAll(query, resultClass)` overloads — picks
between two `RowMapper` implementations based on `dialect.getMapperForType(resultClass)`:
- non-null (a base/JDBC scalar type like `Long`, `String`, `UUID`, ...) → `ScalarRowMapper`, which
  requires the query to return exactly one column and maps it directly via the dialect's scalar
  `ITypeMapper`, throwing a `NativSQLException` otherwise
- `null` (an entity/bean type) → `GenericRowMapper`, the existing bean-introspection path

---

## Logging

All operations are logged via `DbOperationLogger` (SLF4J, `INFO`):

```
DB.BEGIN  UserRepository.insert - INSERT users [request-id]
DB.PARAMS {firstName=John, email=john@example.com}    ← plain values, never encrypted
DB.END    UserRepository.insert - INSERT users [request-id] - 12ms
```

Encrypted field values are excluded from `DB.PARAMS`. The actual SQL parameters passed to JDBC contain the encrypted form.
