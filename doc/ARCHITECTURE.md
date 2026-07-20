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

**`FindQuery<T, ID>`** (`newFindQuery()`) — builds `SELECT` statements. Adds `.select(...)`, `.leftJoin(...)`, `.innerJoin(...)`, `.associate(...)`, `.orderBy(...)` on top of the WHERE methods inherited from `WhereQuery`. Passed directly to `find()` (single result) or `findAll()` (list).

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

---

## Logging

All operations are logged via `DbOperationLogger` (SLF4J, `INFO`):

```
DB.BEGIN  UserRepository.insert - INSERT users [request-id]
DB.PARAMS {firstName=John, email=john@example.com}    ← plain values, never encrypted
DB.END    UserRepository.insert - INSERT users [request-id] - 12ms
```

Encrypted field values are excluded from `DB.PARAMS`. The actual SQL parameters passed to JDBC contain the encrypted form.
