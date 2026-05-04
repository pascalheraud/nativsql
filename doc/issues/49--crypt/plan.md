# Plan: Encrypted Fields — `DbDataType.ENCRYPTED` + `AbstractTypeMapper`

> Issue: [nativsql#49](https://github.com/heraud/nativsql/issues/49)

## Goal

Allow automatic encryption/decryption of a field on read and write,
via `@Type(DbDataType.ENCRYPTED)` with typed field-level annotations to specify the algorithm
and the key provider class.

---

## Architecture

### Data flow

**Read (DB → Java):**

```
DB VARCHAR (encrypted "a3f9bc...")
  → AbstractTypeMapper.map(rs, col, ENCRYPTED, params)  → reads raw String via rs.findColumn + JdbcUtils
  → decryptValue(...)                                   → plain String "42"
  → IntegerTypeMapper.fromValue("42", ENCRYPTED, params) → Integer 42
```

**Write (Java → DB):**

```
Integer 42
  → AbstractTypeMapper.toDatabase(42, ENCRYPTED, params)
  → IntegerTypeMapper.toDatabaseValue(42, STRING, params) → "42"
  → encryptWithParams("42", params)                       → "a3f9bc..."
  → DB VARCHAR
```

### Key principles

- **Stateless mappers** — no crypt state is held in mapper instances.
  Encryption params flow through call-time arguments: `map(rs, col, DbDataType, Map)` and
  `toDatabase(value, DbDataType, Map)`.
- `@Type(DbDataType.ENCRYPTED)` + field-level annotations (`@CryptAlgo`, `@CryptKeyProvider`, …)
  drive the crypt path in `AbstractTypeMapper` transparently to the concrete mapper.
- `AbstractTypeMapper` centralises `rs.findColumn + JdbcUtils.getResultSetValue`, decryption on
  read, and encryption on write. Concrete mappers implement only `fromValue(Object, DbDataType, Map)`
  and `toDatabaseValue(T, DbDataType, Map)`.
- The concrete mapper is never aware that a value is encrypted:
  on write it always receives `DbDataType.STRING`; on read it always receives a plain value.

---

## Annotation structure

### `annotation.type` package

All type-annotation classes live in `ovh.heraud.nativsql.annotation.type`:

| Class | Role |
|---|---|
| `@Type` | Field annotation — declares `DbDataType value()` only (no `params`) |
| `@CryptParam` | Meta-annotation on other annotations — carries `TypeParamKey key()` |
| `@CryptAlgo` | `CryptAlgorithm[] value()` — mandatory for ENCRYPTED |
| `@CryptKeyProvider` | `Class<? extends CryptKeyProvider> value()` — mandatory for reversible algos |
| `@CryptPrefix` | `String value()` — mandatory for reversible algos |
| `@CryptCost` | `int value() default 12` — optional, for one-way algos |
| `@CryptFormat` | `Format value() default Format.STRING` — nested enum `STRING`/`BINARY` |
| `TypeParamKey` | Internal enum — keys for `Map<TypeParamKey, Object>` in `TypeInfo` |

`AnnotationManager.getTypeInfo()` scans field annotations via reflection:
for each annotation carrying `@CryptParam`, reads `value()` and stores the result in the
`TypeInfo` params map under `@CryptParam.key()`. No hardcoded list — adding a new param
annotation requires no change to `AnnotationManager`.

**Usage example:**

```java
@Type(DbDataType.ENCRYPTED)
@CryptAlgo(CryptAlgorithm.GCM)
@CryptKeyProvider(MyCryptKeyProvider.class)
@CryptPrefix("{ENC}")
private String email;

// Cascade fallback (migration from AES_CBC to GCM):
@Type(DbDataType.ENCRYPTED)
@CryptAlgo({CryptAlgorithm.GCM, CryptAlgorithm.AES_CBC})
@CryptKeyProvider(MyCryptKeyProvider.class)
@CryptPrefix("{ENC}")
private String legacy;

// One-way hash:
@Type(DbDataType.ENCRYPTED)
@CryptAlgo(CryptAlgorithm.BCRYPT)
@CryptCost(14)
private String password;

// Binary storage:
@Type(DbDataType.ENCRYPTED)
@CryptAlgo(CryptAlgorithm.GCM)
@CryptKeyProvider(MyCryptKeyProvider.class)
@CryptPrefix("{ENC}")
@CryptFormat(CryptFormat.Format.BINARY)
private String secret;
```

---

## Implementation steps

### ✅ Step 1 — Crypt infrastructure

`ovh.heraud.nativsql.crypt`:

| Class | Status |
|---|---|
| `CryptAlgorithm` | ✅ `isOneWay()`, `isDeterministic()` per value |
| `CryptErrorCode` | ✅ 7 values: `DECODE_FAILED`, `AUTH_FAILED`, `INVALID_FORMAT`, `ALL_ALGOS_FAILED`, `ONE_WAY_DECRYPT_UNSUPPORTED`, `ENCODE_FAILED`, `PREFIX_NOT_APPLICABLE` |
| `CryptException` | ✅ extends `NativSQLException`, carries `CryptErrorCode` |
| `CryptUtils` | ✅ AES-GCM + BCrypt, `static final SecureRandom`, Base64 encoding, no value logging |
| `CryptKeyProvider` | ✅ `@FunctionalInterface byte[] getKey()` |
| `CryptConfig` | ✅ Pure data: `byte[] key`, `CryptAlgorithm[] algorithms`, `String prefix`, `boolean binary` |

### ✅ Step 2 — `DbDataType.ENCRYPTED`

Added to `ovh.heraud.nativsql.annotation.DbDataType` (stays in `annotation` package, not `annotation.type`).

### ✅ Step 3 — Annotation structure

- `TypeParamKey` enum: `ALGO`, `KEY_PROVIDER`, `COST`, `PREFIX`, `FORMAT`, `KEY` (internal)
- `@CryptParam` meta-annotation
- `@CryptAlgo`, `@CryptKeyProvider`, `@CryptPrefix`, `@CryptCost`, `@CryptFormat`
- `@Type` simplified — `params()` removed
- `TypeParam.java` deleted

All in package `ovh.heraud.nativsql.annotation.type`.

### ✅ Step 4 — `TypeInfo` + `AnnotationManager`

- `TypeInfo` carries `DbDataType` + `Map<TypeParamKey, Object>` params
- `AnnotationManager.getTypeInfo()` scans field annotations via `@CryptParam` reflection
- `setTypeInfo()` overloads for programmatic registration (with and without params)
- `KEY_PROVIDER` resolved at annotation-read time via Spring context → `newInstance()` fallback;
  result stored as `TypeParamKey.KEY → byte[]`

### ✅ Step 5 — `ITypeMapper` interface + `AbstractTypeMapper`

**`ITypeMapper<T>` interface** — three abstract methods:

```java
T map(ResultSet rs, String columnName, @Nullable DbDataType dataType, Map<TypeParamKey, Object> params);
T fromValue(Object value, DbDataType dataType, Map<TypeParamKey, Object> params);
Object toDatabase(T value, DbDataType dataType, Map<TypeParamKey, Object> params);
default String formatParameter(String paramName) { return ":" + paramName; }
```

**`AbstractTypeMapper<T>`** — stateless superclass:

- `map()` is `final`: reads from RS, decrypts if `dataType == ENCRYPTED`, calls `fromValue(raw, dataType, params)`
- `toDatabase()` is `final`: encrypts if `dataType == ENCRYPTED`, calls `toDatabaseValue(value, dataType, params)`
- `fromValue(Object, DbDataType, Map)` — abstract hook (replaces former `doMap`)
- `toDatabaseValue(T, DbDataType, Map)` — abstract hook
- Static `ConcurrentHashMap<String, CryptUtils>` cache keyed by `Base64(key) + ":" + cost`
- Static `encryptForWhere(Object, Map)` helper for WHERE clause encryption

All 17 concrete mappers migrated: `IntegerTypeMapper`, `LongTypeMapper`, `ShortTypeMapper`,
`ByteTypeMapper`, `FloatTypeMapper`, `DoubleTypeMapper`, `BigDecimalTypeMapper`,
`BigIntegerTypeMapper`, `BooleanTypeMapper`, `StringTypeMapper`, `UUIDTypeMapper`,
`LocalDateTypeMapper`, `LocalDateTimeTypeMapper`, `ByteArrayTypeMapper`, `DefaultTypeMapper`,
`EnumStringMapper`, `GenericJSONTypeMapper`.

Direct `ITypeMapper` implementors also updated (no `AbstractTypeMapper`):
`PostgresCompositeTypeMapper`, `PostgresEnumMapper`, `PostgreJSONTypeMapper`,
`MySQLPointTypeMapper`, `PostgresPointTypeMapper`.

### ✅ Step 6 — `TypeInfo` wiring through read/write paths

- `PropertyMetadata` carries `TypeInfo typeInfo`
- `RowMapperFactory` fetches `TypeInfo` via `annotationManager.getTypeInfo()` and passes to `PropertyMetadata`
- `GenericRowMapper.mapColumn()` passes `typeInfo.getDataType()` + `typeInfo.getParams()` to `map()`
- `GenericRepository.convertToSqlValue()` passes `TypeInfo` to `toDatabase()`
- `GenericRepository.insertWithGeneratedKey()`: `idMapper.fromValue(idValue, null, Map.of())`

### ✅ Step 7 — `GenericDialect` simplified

No ENCRYPTED special path — `getMapper()` returns the plain type mapper.
Encryption is handled transparently by `AbstractTypeMapper` based on the `dataType` param.
`validateCryptParams`, `getEncryptedMapper`, `buildCryptConfig` removed from `GenericDialect`.

---

## Remaining steps

### ⏳ Step 8 — WHERE encryption in `FindQuery`

`FindQuery` already has access to `annotationManager` and `repository.getEntityFields()`.

**Constraint:** AES-GCM uses a random IV — `encrypt("foo") ≠ encrypt("foo")`. WHERE equality
on a non-deterministic field is semantically impossible.

Logic in `whereAndEquals()` and `whereAndIn()`:

```
column → FieldAccessor → TypeInfo
if ENCRYPTED:
  if algo.isOneWay()         → throw NativSQLException("WHERE on one-way hashed field not supported")
  if !algo.isDeterministic() → throw NativSQLException("WHERE on non-deterministic encrypted field not supported")
  if algo.isDeterministic()  → AbstractTypeMapper.encryptForWhere(value, params)
add condition with (possibly encrypted) value
```

`associate()` join parameters: same guard — joining on an encrypted FK is unsupported.

### ⏳ Step 9 — Tests

**Unit tests:**

- `AnnotationManager.getTypeInfo()` on a field with `@Type(ENCRYPTED)` + crypt annotations
  → correct `Map<TypeParamKey, Object>` (ALGO typed, KEY resolved)
- `CryptUtils` round-trip: `encrypt → decrypt` = original plaintext (GCM)
- Cascade decrypt: `algos[0]` fails → tries `algos[1]` → succeeds
- All algos fail → `CryptException(ALL_ALGOS_FAILED)`
- Decrypt one-way algo → `CryptException(ONE_WAY_DECRYPT_UNSUPPORTED)`
- `AbstractTypeMapper.map()` reversible: reads String → decrypts → `fromValue()` → correct T
- `AbstractTypeMapper.map()` one-way: reads raw hash → returns as-is
- `AbstractTypeMapper.toDatabase()` reversible: serializes → encrypts → opaque String/bytes
- `AbstractTypeMapper.toDatabase()` one-way: serializes → hashes → hash String

**Per Java type (round-trip):**

| Java type | Write | Read |
|---|---|---|
| `String` | `"hello"` → encrypted VARCHAR | decrypt → `"hello"` |
| `Integer` | `42` → `"42"` → encrypted | decrypt → `"42"` → `42` |
| `Long` | `42L` → `"42"` → encrypted | decrypt → `"42"` → `42L` |
| `Short` | `(short)5` → `"5"` → encrypted | decrypt → `"5"` → `(short)5` |
| `Byte` | `(byte)1` → `"1"` → encrypted | decrypt → `"1"` → `(byte)1` |
| `Float` | `3.14f` → `"3.14"` → encrypted | decrypt → `"3.14"` → `3.14f` |
| `Double` | `3.14` → `"3.14"` → encrypted | decrypt → `"3.14"` → `3.14` |
| `BigDecimal` | `1.23` → `"1.23"` → encrypted | decrypt → `"1.23"` → `BigDecimal` |
| `BigInteger` | `123` → `"123"` → encrypted | decrypt → `"123"` → `BigInteger` |
| `Boolean` | `true` → `"true"` → encrypted | decrypt → `"true"` → `true` |
| `UUID` | UUID → `toString()` → encrypted | decrypt → UUID string → `UUID` |
| `LocalDate` | ISO string → encrypted | decrypt → ISO string → `LocalDate` |
| `LocalDateTime` | ISO string → encrypted | decrypt → ISO string → `LocalDateTime` |

**Integration tests** (one per dialect, using existing DB containers):

- Insert + select a `@Type(ENCRYPTED) @CryptAlgo(GCM)` String field:
  raw DB value is opaque, read value matches original
- Insert + select an encrypted `Integer` field: round-trip preserves value
- Insert + select a `@CryptAlgo(BCRYPT)` field: stored value is a bcrypt hash, read returns hash
- Migration passthrough: row with plain value (no prefix) → read returns plain value unchanged
- `FindQuery.whereAndEquals` on a GCM-encrypted field → `NativSQLException`
- `FindQuery.whereAndEquals` on a BCRYPT field → `NativSQLException`

**`AbstractTypeMapper.encryptForWhere()` tests** (once Step 8 is done):

- Deterministic algo: encrypted value is equal to the stored cipher
- Non-deterministic (GCM): throws
- One-way (BCRYPT): throws

### ⏳ Step 10 — `CryptMigrator<T, ID>`

Dedicated class (not in `GenericRepository`) for incremental batch migration.
Three factory methods: `forPlainText()`, `forLegacyAlgo()`, `forReEncrypt()`.

**Guards (at factory-method call time):**
- Target field not `ENCRYPTED` → `NativSQLException`
- Target algo `isOneWay()` → `NativSQLException`
- Scenario 3: `sourcePrefix == annotation prefix` → `NativSQLException`

**Logic (`migrate()`):**

```
whereClause = sourcePrefix != null
              ? "{column} LIKE '{sourcePrefix}%'"      (Scenario 3)
              : "{column} NOT LIKE '{newPrefix}%'"     (Scenarios 1 & 2)
loop (batched transactions):
  rows = SELECT id, {column} FROM {table} WHERE {whereClause} LIMIT batchSize
  if empty → break
  for each row:
    plain = sourceCryptConfig.decryptOrPassthrough(row.value) | legacyDecoder(row.value) | row.value
    encrypted = AbstractTypeMapper.encryptForWhere(plain, targetParams)
    UPDATE {table} SET {column} = :encrypted WHERE id = :id
  progressCallback?.accept(totalMigrated)
```

**Usage:**

```java
// Scenario 1 — plain text
CryptMigrator.forPlainText(userRepository, User::getEmail).migrate();

// Scenario 3 — key rotation
CryptMigrator.forReEncrypt(userRepository, User::getEmail, "{ENC_V1}", oldParams)
    .withBatchSize(200)
    .migrate();
```

### ⏳ Step 11 — Documentation

- `CHANGELOG.md` — new version entry
- `API.md` — `@Type(ENCRYPTED)`, annotation table, `CryptKeyProvider`, `CryptMigrator`, column sizing
- `FAQ.md` — encryption section, migration, key rotation, WHERE constraints, primitive types
- `DOCS.md` — links to API.md

---

## Logging precautions (all steps)

| Location | Rule |
|---|---|
| `AbstractTypeMapper.map()` | Column name only in exception — never the raw value |
| `AbstractTypeMapper` crypt helpers | No `log.debug/trace` on encrypt/decrypt paths |
| `CryptMigrator.migrate()` | Row counts only via `progressCallback`; no IDs or values logged internally |
| `NativSQLException` from validation | Field name + algo name allowed — never key, prefix value, or data value |
