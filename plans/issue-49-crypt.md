# Plan: Encrypted Fields — `DbDataType.ENCRYPTED` + `AbstractTypeMapper`

> Issue: [nativsql#49](https://github.com/heraud/nativsql/issues/49)

## Goal

Allow automatic encryption/decryption of a field on read and write,
via `@Type(DbDataType.ENCRYPTED)` with `@TypeParam` to specify the algorithm
and the key provider class.

---

## Architecture

### Data flow

**Read (DB → Java):**

```
DB VARCHAR (encrypted "a3f9bc...")
  → AbstractTypeMapper.map()          → reads raw String via rs.findColumn + JdbcUtils
  → cryptConfig.decrypt("a3f9bc...")  → plain String "42"
  → IntegerTypeMapper.fromValue("42") → Integer 42
```

**Write (Java → DB):**

```
Integer 42
  → AbstractTypeMapper.toDatabase(42, ENCRYPTED)
  → IntegerTypeMapper.toDatabaseValue(42, STRING) → "42"
  → cryptConfig.encrypt("42")                      → "a3f9bc..."
  → DB VARCHAR
```

### Key principles

- `DbDataType.ENCRYPTED` is a new enum value — it drives the encrypt/decrypt
  path in `AbstractTypeMapper`, transparently to the concrete mapper.
- `@TypeParam` sub-annotation on `@Type` passes the algo and `keyProvider` class.
- `AbstractTypeMapper` is the new superclass of all concrete mappers.
  It centralizes `rs.findColumn + JdbcUtils.getResultSetValue`, decryption on read,
  and encryption on write. Concrete mappers only implement `doMap()` and `toDatabaseValue()`.
- The concrete mapper is never aware that a value is encrypted:
  on write it always receives `DbDataType.STRING` to serialize to;
  on read it always receives a plain value via `fromValue()`.

---

## Implementation steps

### 1. Encryption utilities

Copy from Auxiliaires and adapt into `ovh.heraud.nativsql.crypt`:

| File                  | Notes                                                                        |
| --------------------- | ---------------------------------------------------------------------------- |
| `CryptAlgorithm.java` | From `EncryptionAlgorithm`, without `LEGACY`. Add `abstract boolean isDeterministic()` and `abstract boolean isOneWay()`. GCM → `isDeterministic=false, isOneWay=false`. BCRYPT → `isDeterministic=false, isOneWay=true`. One-way algos hash on write and return the raw hash on read — decryption is not supported. |
| `CryptUtils.java`     | From `EncryptionUtils` — `key` passed via constructor (no Spring dependency). Adaptations vs Auxiliaires: (1) `SecureRandom` must be `static final` — do not create a new instance per `encrypt()` call; (2) encoding changed from hex to Base64 (33% overhead vs 100% for hex); (3) do not log any value in error messages or debug output. |
| `CryptException.java` | From `EncryptionException`. Enriched with a `CryptErrorCode code` field (see below) — extends `NativSQLException` so callers can catch either. |
| `CryptErrorCode.java` | New enum — error codes for all crypt failure cases (see below).              |

**`CryptErrorCode` enum:**

```java
public enum CryptErrorCode {
    /** Base64 or hex decoding of the stored value failed — value is malformed. */
    DECODE_FAILED,
    /** GCM authentication tag verification failed — wrong key or tampered data. */
    AUTH_FAILED,
    /** Ciphertext is too short to be valid (e.g. shorter than IV length). */
    INVALID_FORMAT,
    /** All algorithms in the cascade failed to decrypt the stored value. */
    ALL_ALGOS_FAILED,
    /** Attempted to decrypt a one-way hash (BCRYPT) — operation not supported. */
    ONE_WAY_DECRYPT_UNSUPPORTED,
    /** Encryption failed — underlying JCA error. */
    ENCODE_FAILED,
    /** Attempted to use PREFIX on a one-way algorithm. */
    PREFIX_NOT_APPLICABLE,
}
```

**`CryptException`:**

```java
public class CryptException extends NativSQLException {
    private final CryptErrorCode code;

    public CryptException(CryptErrorCode code, String message) { super(message); this.code = code; }
    public CryptException(CryptErrorCode code, String message, Throwable cause) { super(message, cause); this.code = code; }

    public CryptErrorCode getCode() { return code; }
}
```

Messages must include: field/column name, algorithm name, error code — **never** the raw or decrypted value.
Example: `new CryptException(AUTH_FAILED, "Decryption failed for column 'email' with algo GCM [AUTH_FAILED]", e)`

`CryptConfig` always throws `CryptException` (never bare `NativSQLException`) so callers can switch on `getCode()`.

### 2. `CryptKeyProvider` interface

```java
@FunctionalInterface
public interface CryptKeyProvider {
    byte[] getKey();
}
```

Resolved by `GenericDialect` via Spring context if available, otherwise `newInstance()`.
If `newInstance()` fails (no no-arg constructor), a clear `NativSQLException` is thrown.

> `KEY_PROVIDER` is **not required** for one-way algorithms (e.g. BCRYPT) since they do not
> use a symmetric key. `validateCryptParams()` skips the `KEY_PROVIDER` check when `isOneWay() == true`.

> **Key lifetime and memory:** `getKey()` is called **once** at `CryptConfig` construction time —
> the returned `byte[]` is held in `CryptUtils` for the lifetime of the mapper (application scope).
> The key therefore lives in the JVM heap indefinitely; the GC may copy it across memory regions and
> it will appear in a heap dump. Mitigations are the caller's responsibility:
> - Do not log, serialize, or expose `CryptUtils` or `CryptKeyProvider` instances.
> - For higher security requirements, delegate to a KMS/HSM that never exports the raw key
>   (the `CryptKeyProvider` interface can wrap such a client).
> - There is no built-in zeroing of the key array — Java does not guarantee memory erasure.

### 3. `DbDataType.ENCRYPTED`

Add a new value to the enum:

```java
/**
 * Encrypted type: field is stored as an encrypted VARCHAR in the database.
 * Requires @TypeParam(key="algo") and @TypeParam(key="keyProvider") on the @Type annotation.
 */
ENCRYPTED
```

### 4. `TypeParamKey` enum

Typed keys for all parameters that can be passed via `@TypeParam`.
Located in `ovh.heraud.nativsql.annotation`.

```java
public enum TypeParamKey {
    /**
     * Encryption algorithm(s). Use @TypeParam.algoValue() — accepts one or more CryptAlgorithm values.
     * Multiple values = cascade fallback on read (first succeeds → stops).
     * Example: algoValue = CryptAlgorithm.GCM  or  algoValue = {CryptAlgorithm.GCM, CryptAlgorithm.AES_CBC}
     * Mandatory when DbDataType.ENCRYPTED.
     */
    ALGO,

    /**
     * Class implementing CryptKeyProvider (fully qualified name as String,
     * or the Class<?> instance when registered programmatically).
     * Mandatory when DbDataType.ENCRYPTED, except for one-way algorithms (e.g. BCRYPT).
     */
    KEY_PROVIDER,

    /**
     * Cost factor for one-way algorithms (e.g. BCRYPT work factor).
     * Optional — defaults to 12 if absent.
     */
    COST,

    /**
     * Prefix prepended to the encrypted value before storing in DB (e.g. "{ENC}").
     * Mandatory for reversible algorithms. Serves as both a format marker and a migration discriminator:
     *   - on write: stored value = prefix + ciphertext
     *   - on read:  if value starts with prefix → strip + decrypt; otherwise → return as-is (not yet migrated)
     * The absence of the prefix unambiguously identifies unmigrated rows (plain or legacy-encoded),
     * enabling incremental batch migration via CryptMigrator.
     *
     * Key rotation: changing the encryption key requires changing the prefix at the same time
     * (e.g. "{ENC_V1}" → "{ENC_V2}"). The old prefix becomes the sourcePrefix in
     * CryptMigrator.forReEncrypt(). Keeping the same prefix with a different key makes
     * rows indistinguishable before and after re-encryption, breaking incremental migration.
     *
     * Not applicable for one-way algorithms (BCRYPT has its own built-in format marker).
     */
    PREFIX,

    /**
     * Storage format. Use value() = "STRING" (default) or "BINARY".
     *   STRING — ciphertext is Base64-encoded and stored in a VARCHAR column.
     *            Human-readable in DB dumps; ~33% size overhead from Base64 encoding.
     *   BINARY — ciphertext stored as raw bytes in a VARBINARY/BLOB column.
     *            More compact (no Base64 overhead); recommended for large volumes.
     * Optional — defaults to STRING if absent.
     * The DB column type must match: VARCHAR for STRING, VARBINARY for BINARY.
     * For column sizing, see the column size note in §12 Tests / API.md.
     */
    FORMAT
}
```

### 5. `@TypeParam` + `@Type` update

`@TypeParam` uses `TypeParamKey` as key (not a free String):

```java
@Target({})  // used only inside @Type
@Retention(RetentionPolicy.RUNTIME)
public @interface TypeParam {
    TypeParamKey key();
    /**
     * String value for the parameter (prefix, cost factor).
     * Use for PREFIX and COST. Not used for ALGO or KEY_PROVIDER.
     */
    String value() default "";
    /**
     * Algorithm(s) for ALGO. Using the enum directly enables compile-time safety and
     * IDE navigation. Supports multiple values for cascade fallback on read
     * (e.g. algoValue = {CryptAlgorithm.GCM, CryptAlgorithm.AES_CBC}).
     * Defaults to empty array (= not set).
     */
    CryptAlgorithm[] algoValue() default {};
    /**
     * Class value for KEY_PROVIDER. Using a Class reference instead of a String
     * allows IDE refactoring (rename, find usages) to track the provider class.
     * Defaults to Void.class (= not set). Takes precedence over value() when set.
     *
     * Example: @TypeParam(key = TypeParamKey.KEY_PROVIDER, classValue = MyCryptKeyProvider.class)
     */
    Class<?> classValue() default Void.class;
}
```

`TypeParamKey` javadoc documents which attribute to use per key:
- `ALGO` → use `algoValue()` (compile-time safe enum reference)
- `KEY_PROVIDER` → use `classValue()` (IDE refactoring-safe); `value()` (FQCN String) accepted as fallback
- `PREFIX`, `COST`, `FORMAT` → use `value()`

`@Type` updated:

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Type {
    DbDataType value();
    TypeParam[] params() default {};
}
```

Usage example:

```java
@Type(value = DbDataType.ENCRYPTED, params = {
    @TypeParam(key = TypeParamKey.ALGO,        algoValue = CryptAlgorithm.GCM),
    @TypeParam(key = TypeParamKey.KEY_PROVIDER, classValue = MyCryptKeyProvider.class),
    @TypeParam(key = TypeParamKey.PREFIX,       value = "{ENC}")
})
private String email;
```

The String-based form is also accepted for `KEY_PROVIDER` (e.g., when the class is in another module):

```java
@TypeParam(key = TypeParamKey.KEY_PROVIDER, value = "com.example.MyCryptKeyProvider")
```

For cascade fallback on read, multiple enum values:

```java
@TypeParam(key = TypeParamKey.ALGO, algoValue = {CryptAlgorithm.GCM, CryptAlgorithm.AES_CBC})
```

### 6. `TypeInfo` update

Replace `Map<String, String>` with `Map<TypeParamKey, Object>`.
Values are `String` when coming from annotations, but `Object` allows programmatic
registration to pass typed values (e.g., an actual `Class<?>` instance for `KEY_PROVIDER`):

```java
public class TypeInfo {
    private final @NonNull DbDataType dataType;
    private final Map<TypeParamKey, Object> params; // empty map if no params

    public Object getParam(TypeParamKey key) { return params.get(key); }
}
```

`AnnotationManager.getTypeInfo()` builds the map by iterating `@TypeParam[]` and using
`typeParam.key()` (already a `TypeParamKey`) as the map key, `typeParam.value()` as the value.

`setTypeInfo()` gains an overload:

```java
// Existing (no params — kept for backward compatibility)
public void setTypeInfo(Class<?> clazz, String fieldName, DbDataType dataType)

// New overload
public void setTypeInfo(Class<?> clazz, String fieldName, DbDataType dataType,
                        Map<TypeParamKey, Object> params)
```

### 7. `CryptConfig`

Holds the resolved crypt configuration for a mapper instance:

```java
public class CryptConfig {
    private final CryptUtils cryptUtils;          // null for one-way algos (no key needed)
    private final CryptAlgorithm[] algorithms;    // algos[0] for write, cascade for read
    private final String prefix;                  // always set for reversible algos (mandatory)
    private final boolean binary;                 // true = VARBINARY storage, false = VARCHAR (default)

    /** Returns true if algos[0] is one-way (e.g. BCRYPT). */
    public boolean isOneWay() { return algorithms[0].isOneWay(); }

    public boolean hasPrefix()  { return prefix != null; }
    public String getPrefix()   { return prefix; }
    public boolean isBinary()   { return binary; }

    /**
     * Hashes or encrypts plain, then prepends prefix for STRING format.
     * STRING format: returns Base64(prefix + ciphertext) as String.
     * BINARY format: returns raw ciphertext as byte[] (no prefix, no Base64).
     * For one-way algos, always returns a String (bcrypt hash — format flag ignored).
     * Throws CryptException(ENCODE_FAILED) on JCA error — never includes the plain value.
     */
    public Object encode(String plain) { ... }

    /**
     * Migration-aware decryption for reversible algos (PREFIX is always set):
     *   STRING format: value starts with prefix → strip, Base64-decode, decrypt.
     *                  value does NOT start with prefix → return as-is (not yet migrated).
     *   BINARY format: always decrypt the raw byte[] directly (no prefix check — migration
     *                  uses NULL or a separate migrated flag).
     *
     * Exception codes thrown (always CryptException, never a bare NativSQLException):
     *   ONE_WAY_DECRYPT_UNSUPPORTED — called on a one-way algo
     *   DECODE_FAILED               — Base64/hex decoding of stored value failed
     *   INVALID_FORMAT              — ciphertext shorter than IV length
     *   AUTH_FAILED                 — GCM tag verification failed (wrong key or tampered)
     *   ALL_ALGOS_FAILED            — cascade exhausted, no algo succeeded
     *
     * The exception message includes column name and algo — never the raw stored value.
     */
    public String decryptOrPassthrough(Object stored) { ... }
}
```

> For reversible algos: `decryptOrPassthrough()` handles the prefix-based migration fallback.
> For one-way algos (BCRYPT): `decryptOrPassthrough()` always throws — the raw hash is returned
> as-is by `map()` and verification is the caller's responsibility (`BCrypt.checkpw(input, storedHash)`).

### 8. `AbstractTypeMapper<T>`

New superclass for all concrete mappers. Holds both `params` (for extensibility and
forwarding to abstract methods) and `cryptConfig` (built by `GenericDialect` from params):

```java
public abstract class AbstractTypeMapper<T> implements ITypeMapper<T> {

    private final Map<TypeParamKey, Object> params; // empty map if none
    private final CryptConfig cryptConfig;           // null = no encryption

    // No-arg constructor: normal (non-encrypted) mapper
    protected AbstractTypeMapper() {
        this.params = Map.of();
        this.cryptConfig = null;
    }

    // Constructor for encrypted mappers — called by GenericDialect
    protected AbstractTypeMapper(Map<TypeParamKey, Object> params, CryptConfig cryptConfig) {
        this.params = params;
        this.cryptConfig = cryptConfig;
    }

    /** Template method: reads from RS, decrypts if needed, delegates to doMap() or fromValue(). */
    @Override
    public final T map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            int index = rs.findColumn(columnName);
            Object raw = JdbcUtils.getResultSetValue(rs, index);
            if (raw == null) return null;
            if (cryptConfig != null) {
                if (cryptConfig.isOneWay()) {
                    // One-way hash (e.g. BCRYPT): return raw hash as-is — no decryption possible
                    return fromValue(raw);
                }
                // Reversible: raw is String (STRING format) or byte[] (BINARY format)
                // decryptOrPassthrough handles both — returns plain String in both cases
                return fromValue(cryptConfig.decryptOrPassthrough(raw));
            }
            return doMap(raw, params);
        } catch (SQLException e) {
            throw new NativSQLException("Unable to map column " + columnName, e);
        }
    }

    /** Template method: hashes or encrypts if needed before delegating to toDatabaseValue(). */
    @Override
    public final Object toDatabase(T value, DbDataType dataType) {
        if (value == null) return null;
        if (dataType == DbDataType.ENCRYPTED) {
            String serialized = (String) toDatabaseValue(value, DbDataType.STRING, params);
            // encode() returns String (Base64, STRING format) or byte[] (BINARY format)
            return cryptConfig.encode(serialized);
        }
        return toDatabaseValue(value, dataType, params);
    }

    /**
     * Converts the raw ResultSet value to T.
     * Must not handle null (already guarded above).
     * params is forwarded for extensibility — most mappers will ignore it.
     */
    protected abstract T doMap(Object raw, Map<TypeParamKey, Object> params) throws NativSQLException;

    /**
     * Replaces toDatabase() in concrete mappers.
     * Must not handle null (already guarded above).
     * When called for an ENCRYPTED field, dataType is always STRING (serialise to String first).
     * params is forwarded for extensibility — most mappers will ignore it.
     */
    protected abstract Object toDatabaseValue(T value, DbDataType dataType, Map<TypeParamKey, Object> params);
}
```

### 9. Migrate all concrete mappers

Each existing mapper:
- Extends `AbstractTypeMapper<T>` instead of `implements ITypeMapper<T>`
- Removes the `rs.findColumn + JdbcUtils.getResultSetValue + null check` boilerplate from `map()`
  and renames the body to `doMap(Object raw, Map<TypeParamKey, Object> params)`
  (params can be ignored — `@SuppressWarnings("unused")` acceptable)
- Renames `toDatabase()` → `toDatabaseValue(T value, DbDataType dataType, Map<TypeParamKey, Object> params)`
  (remove the `value == null` guard — handled by superclass; params can be ignored)
- Keeps `fromValue()` as-is

Mappers concerned: `IntegerTypeMapper`, `LongTypeMapper`, `ShortTypeMapper`, `ByteTypeMapper`,
`FloatTypeMapper`, `DoubleTypeMapper`, `BigDecimalTypeMapper`, `BigIntegerTypeMapper`,
`BooleanTypeMapper`, `StringTypeMapper`, `UUIDTypeMapper`, `LocalDateTypeMapper`,
`LocalDateTimeTypeMapper`, `ByteArrayTypeMapper`, `DefaultTypeMapper`, `EnumStringMapper`,
`GenericJSONTypeMapper`.

> **`LocalDateTypeMapper` and `LocalDateTimeTypeMapper`** must implement `fromValue(Object)` to
> support `DbDataType.ENCRYPTED`. The encrypted value is stored and read back as an ISO-8601 string:
> - `LocalDateTypeMapper.fromValue(Object)` → `LocalDate.parse((String) value)`
> - `LocalDateTimeTypeMapper.fromValue(Object)` → `LocalDateTime.parse((String) value)`
>
> `toDatabaseValue()` already serializes to String (ISO-8601 via `toString()`), so no change is
> needed on the write path.

### 10. `GenericDialect` — wiring

Extract the existing `if (targetType == ...)` chain into a private `getMapperForType(Class<T>)` helper.
Then, before the existing dispatch, check for `ENCRYPTED`:

```java
TypeInfo typeInfo = annotationManager.getTypeInfo(fieldAccessor);
if (typeInfo != null && typeInfo.getDataType() == DbDataType.ENCRYPTED) {
    Map<TypeParamKey, Object> params = typeInfo.getParams();
    validateCryptParams(params); // throws NativSQLException if ALGO or KEY_PROVIDER is missing
    CryptConfig cryptConfig = buildCryptConfig(params); // resolves CryptKeyProvider + builds CryptUtils
    return getEncryptedMapper(targetType, params, cryptConfig);
}
```

`validateCryptParams()` enforces the following rules at annotation-read time (mapper instantiation):

| Condition                                  | Rule                                                                 |
| ------------------------------------------ | -------------------------------------------------------------------- |
| `ALGO` absent                              | `NativSQLException` — always mandatory                               |
| `algo.isOneWay() && KEY_PROVIDER present`  | `NativSQLException` — one-way algos do not use a symmetric key       |
| `!algo.isOneWay() && KEY_PROVIDER absent`  | `NativSQLException` — reversible algos require a key                 |
| `algo.isOneWay() && COST` present          | OK — optional cost factor for bcrypt                                 |
| `!algo.isOneWay() && PREFIX absent`        | `NativSQLException` — PREFIX mandatory for reversible algos          |
| `!algo.isOneWay() && PREFIX == ""`         | `NativSQLException` — PREFIX must not be empty (use e.g. "{ENC}")    |
| `algo.isOneWay() && PREFIX present`        | `NativSQLException` — PREFIX not applicable to one-way algos         |
| `FORMAT` present but not "STRING"/"BINARY" | `NativSQLException` — invalid FORMAT value                            |

`buildCryptConfig()` reads `ALGO` (the `CryptAlgorithm[]` stored from `algoValue()`) and,
for reversible algos only, `KEY_PROVIDER` (resolves via Spring context if available,
otherwise `newInstance()`; throws `NativSQLException` if no-arg constructor is absent),
then builds `CryptUtils` + `CryptConfig`.

`getEncryptedMapper()` calls `getMapperForType(targetType)` and re-instantiates with the
`(Map<TypeParamKey, Object>, CryptConfig)` constructor. Each concrete mapper exposes this
constructor delegating to `super(params, cryptConfig)`.

### 11. Read/write path

| Path                       | Status                                                                                                        |
| -------------------------- | ------------------------------------------------------------------------------------------------------------- |
| SELECT result              | ✅ `AbstractTypeMapper.map()` handles decryption (String or byte[] from RS)                                   |
| INSERT / UPDATE            | ✅ `AbstractTypeMapper.toDatabase()` handles encryption (returns String or byte[])                            |
| WHERE params (`FindQuery`) | ✅ Supported **only for deterministic algorithms** — `FindQuery` encrypts values at condition-add time. GCM (non-deterministic) throws `NativSQLException`. |
| `associate()` join params  | ❌ Unsupported regardless of algorithm — joining on an encrypted FK is semantically broken.                   |

> **DB column sizing:**
> The ciphertext is always longer than the plaintext. Before deploying, verify the column is large enough:
> - `STRING` format (VARCHAR): `Base64(IV + ciphertext + tag)` — for AES-GCM: `ceil((plaintext_len + 28) * 4 / 3)` characters
> - `BINARY` format (VARBINARY): `plaintext_len + 12 (IV) + 16 (tag)` bytes for AES-GCM
>
> Add this check to `API.md` and `FAQ.md`.

### 12. Tests

**Step 1 — `AbstractTypeMapper` refactoring:**
- `map()` delegates to `doMap()` with params
- `toDatabase()` delegates to `toDatabaseValue()` with params
- null returns null on both paths
- Verify boilerplate is no longer duplicated across concrete mappers (spot-check 3 mappers)

**Step 2 — `TypeParamKey` / `@TypeParam` / `TypeInfo` / `AnnotationManager`:**
- `AnnotationManager.getTypeInfo()` on a field with `@Type(ENCRYPTED, params = {...})` → correct `Map<TypeParamKey, Object>`
- `setTypeInfo()` overload stores params correctly and is retrieved via `getTypeInfo()`
- `TypeInfo.getParam(ALGO)` returns the expected value; unknown key returns null

**Step 3 — `CryptUtils` / `CryptConfig`:**
- `CryptUtils` round-trip: `encode → decode` = original plaintext (GCM)
- `CryptConfig.decryptOrPassthrough()` cascade: `algos[0]` fails → tries `algos[1]` → succeeds
- `CryptConfig.decryptOrPassthrough()` all fail → `NativSQLException`
- `CryptConfig.decryptOrPassthrough()` on a one-way algo → `NativSQLException`
- `CryptConfig.isOneWay()` returns `true` for BCRYPT
- `CryptConfig.encode()` with PREFIX: stored value starts with prefix
- `CryptConfig.decryptOrPassthrough()` with PREFIX + prefixed value: strips prefix and decrypts correctly
- `CryptConfig.decryptOrPassthrough()` with PREFIX + non-prefixed value (plain, not yet migrated): returns value as-is
- Null in → null out

**Step 4 — `AbstractTypeMapper` crypt path:**
- `map()` reversible: reads String from RS → decrypts → calls `fromValue()` → correct T
- `map()` one-way: reads raw hash from RS → returns as-is via `fromValue()` (no decryption)
- `toDatabase()` reversible: serializes via `toDatabaseValue(STRING)` → encrypts → opaque String
- `toDatabase()` one-way: serializes via `toDatabaseValue(STRING)` → hashes → hash String
- null → null on both paths

**Per Java type (unit — `AbstractTypeMapper` + concrete mapper combined):**

| Java type    | Write                                  | Read                                    |
| ------------ | -------------------------------------- | --------------------------------------- |
| `String`     | `"hello"` → encrypted VARCHAR          | decrypt → `"hello"`                     |
| `Integer`    | `42` → `"42"` → encrypted VARCHAR      | decrypt → `"42"` → `42`                 |
| `Long`       | `42L` → `"42"` → encrypted VARCHAR     | decrypt → `"42"` → `42L`               |
| `Short`      | `(short)5` → `"5"` → encrypted         | decrypt → `"5"` → `(short)5`           |
| `Byte`       | `(byte)1` → `"1"` → encrypted          | decrypt → `"1"` → `(byte)1`            |
| `Float`      | `3.14f` → `"3.14"` → encrypted         | decrypt → `"3.14"` → `3.14f`           |
| `Double`     | `3.14` → `"3.14"` → encrypted          | decrypt → `"3.14"` → `3.14`            |
| `BigDecimal` | `1.23` → `"1.23"` → encrypted          | decrypt → `"1.23"` → `BigDecimal(1.23)` |
| `BigInteger` | `123` → `"123"` → encrypted            | decrypt → `"123"` → `BigInteger(123)`   |
| `Boolean`    | `true` → `"true"` → encrypted          | decrypt → `"true"` → `true`            |
| `UUID`       | UUID → `toString()` → encrypted        | decrypt → `"uuid-str"` → UUID           |
| `LocalDate`      | `2024-01-15` → `"2024-01-15"` → encrypted | decrypt → `"2024-01-15"` → `LocalDate`  |
| `LocalDateTime`  | `2024-01-15T10:30:00` → ISO String → encrypted | decrypt → ISO String → `LocalDateTime` |

**Step 5 — `GenericDialect` wiring / `validateCryptParams()`:**
- `getMapper()` on a field annotated `@Type(ENCRYPTED, ...)` returns a mapper with non-null `cryptConfig`
- `validateCryptParams()` throws if `ALGO` is absent
- `validateCryptParams()` throws if reversible algo + `KEY_PROVIDER` absent
- `validateCryptParams()` throws if one-way algo + `KEY_PROVIDER` present
- `buildCryptConfig()` throws `NativSQLException` if `KEY_PROVIDER` class has no no-arg constructor
- BCRYPT field: `getMapper()` returns mapper with `cryptConfig.isOneWay() == true`, no `KEY_PROVIDER` needed

**Step 6 — `FindQuery` WHERE guards:**
- `whereAndEquals` on a one-way algo field → `NativSQLException`
- `whereAndEquals` on a non-deterministic (GCM) field → `NativSQLException`
- `whereAndEquals` on a deterministic algo field → encrypts value, stored in params map
- `whereAndIn` on a deterministic algo field → encrypts each value in the list
- Non-encrypted field: no change to existing behaviour

**Step 7 — Integration tests:**
- Insert + select a `@Type(ENCRYPTED, ALGO=GCM)` String field: stored value is opaque, read value is plain
- Insert + select an encrypted Integer field: round-trip preserves value
- Insert + select a `@Type(ENCRYPTED, ALGO=BCRYPT)` String field: stored value is a bcrypt hash, read returns hash
- Migration scenario: row inserted with plain value (no prefix) → read returns plain value unchanged; row inserted after migration → read returns decrypted value

> **Migration prerequisite:** before adding `PREFIX` to a column, verify that no existing
> plain values start with the chosen prefix:
> ```sql
> SELECT COUNT(*) FROM my_table WHERE my_column LIKE '{ENC}%'; -- must return 0
> ```
> If any row matches, the prefix collides with existing data — `decryptOrPassthrough()` would
> attempt to decrypt a plain value, producing a `NativSQLException` or corrupted output.
> Choose a prefix that cannot appear in the domain data, or run the check first.
- `FindQuery.whereAndEquals` on a deterministic-algo encrypted field: finds the correct row
- `FindQuery.whereAndEquals` on a GCM-encrypted field: throws `NativSQLException`
- `FindQuery.whereAndEquals` on a BCRYPT field: throws `NativSQLException`

---

## Usage example

```java
@Component
public class MyCryptKeyProvider implements CryptKeyProvider {
    @Value("${app.crypt.key}")
    private String base64Key;

    @Override
    public byte[] getKey() {
        return Base64.getDecoder().decode(base64Key);
    }
}

public class User {

    @Type(value = DbDataType.ENCRYPTED, params = {
        @TypeParam(key = TypeParamKey.ALGO,        algoValue = CryptAlgorithm.GCM),
        @TypeParam(key = TypeParamKey.KEY_PROVIDER, classValue = MyCryptKeyProvider.class),
        @TypeParam(key = TypeParamKey.PREFIX,       value = "{ENC}")
    })
    private String email;

    @Type(value = DbDataType.ENCRYPTED, params = {
        @TypeParam(key = TypeParamKey.ALGO,        algoValue = CryptAlgorithm.GCM),
        @TypeParam(key = TypeParamKey.KEY_PROVIDER, classValue = MyCryptKeyProvider.class),
        @TypeParam(key = TypeParamKey.PREFIX,       value = "{ENC}")
    })
    private Integer phoneCode;
}
```

---

## Implementation order

### Step 1 — Refactor: `AbstractTypeMapper` (independent of crypt)

Pure refactoring, no behaviour change, mergeable on its own.

- Create `AbstractTypeMapper<T> implements ITypeMapper<T>` with no-arg constructor and `cryptConfig = null`
- Implement `map()` as final template method: `rs.findColumn + JdbcUtils + null check`, then `doMap(raw)`
- Implement `toDatabase()` as final template method: null check, then `toDatabaseValue(value, dataType)`
- Declare `doMap(Object raw)` and `toDatabaseValue(T value, DbDataType dataType)` as `protected abstract`
- Migrate all 17 concrete mappers:
  - `extends AbstractTypeMapper<T>` (drop `implements ITypeMapper<T>`)
  - rename `map()` body → `doMap(Object raw, Map<TypeParamKey, Object> params)` (remove `rs.findColumn + JdbcUtils + null check`; params can be ignored)
  - rename `toDatabase()` → `toDatabaseValue(T value, DbDataType dataType, Map<TypeParamKey, Object> params)` (remove `value == null` guard; params can be ignored)
  - keep `fromValue()` untouched
- Unit tests: verify `map()` and `toDatabase()` still delegate correctly, null cases

### Step 2 — Annotation: `TypeParamKey` + `@TypeParam` + `@Type` + `TypeInfo`

- Create `TypeParamKey` enum with `ALGO` and `KEY_PROVIDER` (see §4)
- Create `@TypeParam(TypeParamKey key, String value)` with `@Target({})` + `@Retention(RUNTIME)`
- Add `TypeParam[] params() default {}` to `@Type`
- Add `Map<TypeParamKey, Object> params` to `TypeInfo`; update constructor and `getParam(TypeParamKey key)` accessor
- Update `AnnotationManager.getTypeInfo()` to populate params map from `@TypeParam[]`:
  for each `@TypeParam`, the map value is resolved with the following priority:
  1. `algoValue()` not empty → store `CryptAlgorithm[]`
  2. `classValue() != Void.class` → store `Class<?>`
  3. otherwise → store `String` from `value()`
- Add overload `setTypeInfo(Class, String, DbDataType, Map<TypeParamKey, Object>)` for programmatic registration

### Step 3 — Crypt infrastructure

- `CryptAlgorithm` (from Auxiliaires, drop `LEGACY` + `BCRYPT`, add `abstract boolean isDeterministic()` — GCM → `false`)
- `CryptErrorCode` enum (7 values — see §1)
- `CryptException extends NativSQLException` with `CryptErrorCode code` field
- `CryptUtils(byte[] key)` — no Spring dependency
- `CryptKeyProvider` interface (`byte[] getKey()`)
- `CryptConfig(CryptUtils, CryptAlgorithm[])` — `encrypt(String)` + `decrypt(String)` (cascade fallback, throws `NativSQLException` if all fail)
- Verified from Auxiliaires source: GCM IV is 12 bytes generated via `static final SecureRandom` per encrypt call — unique per operation, NIST-recommended length. This must be preserved as-is in the adapted `CryptUtils`.
- Unit tests: round-trip GCM, cascade fallback, all-fail case

### Step 4 — `DbDataType.ENCRYPTED` + `AbstractTypeMapper` crypt path

- Add `ENCRYPTED` to `DbDataType`
- Add `AbstractTypeMapper(Map<TypeParamKey, Object>, CryptConfig)` protected constructor
- Add crypt branch in `map()`: if `cryptConfig != null` → `fromValue(cryptConfig.decrypt((String) raw))`
- Add crypt branch in `toDatabase()`: if `dataType == ENCRYPTED` → `toDatabaseValue(value, STRING, params)` then `cryptConfig.encrypt(...)`
- Add a `(Map<TypeParamKey, Object>, CryptConfig)` constructor to each concrete mapper that delegates to `super(params, cryptConfig)`
- Unit tests for the crypt path in `AbstractTypeMapper`

### Step 5 — Wiring in `GenericDialect`

- Extract the `if (targetType == ...)` chain into a private `getMapperForType(Class<T>)` helper
- At the top of `getMapper()`, check `TypeInfo.getDataType() == ENCRYPTED`:
  - resolve `CryptKeyProvider` (Spring context → `newInstance()` → clear exception if both fail)
  - build `CryptConfig`
  - call `getMapperForType(targetType)` and re-instantiate with `CryptConfig` constructor

### Step 6 — WHERE encryption in `FindQuery`

`FindQuery` already has access to `annotationManager` and `repository.getEntityFields()`.
It can therefore transparently encrypt WHERE values at the point where conditions are added.

**Fundamental constraint:** AES-GCM uses a random IV, so `encrypt("foo") ≠ encrypt("foo")`.
A WHERE equality on a GCM-encrypted field is semantically impossible.
The feature is only feasible for **deterministic** algorithms (same plaintext → same ciphertext).

**`CryptAlgorithm` change:**
Add `boolean isDeterministic()` to the enum.
Example: `AES_SIV` → `true`, `GCM` → `false`.

**`FindQuery.whereAndEquals()` and `whereAndIn()` logic:**

```
column → FieldAccessor (via repository.getEntityFields())
       → TypeInfo (via annotationManager.getTypeInfo())
       → if ENCRYPTED:
           - if algo.isOneWay()        → throw NativSQLException("WHERE on one-way hashed field is not supported")
           - if !algo.isDeterministic() → throw NativSQLException("WHERE on non-deterministic encrypted field is not supported")
           - if algo.isDeterministic()  → build CryptConfig (via dialect) and encrypt value
       → add condition with (possibly encrypted) value
```

`FindQuery` already has `repository`, which holds the dialect.
The dialect provides `buildCryptConfig(TypeInfo)` (introduced in Step 5).

**`associate()` join parameters:** same guard — joining on an encrypted FK is unsupported
regardless of algorithm (join semantics require the same value in both tables, which is
incompatible with field-level encryption).

### Step 7 — `CryptMigrator<T, ID>`

A dedicated class (not in `GenericRepository`) responsible for migrating field values to the
encryption scheme defined by `@Type(ENCRYPTED)` on the entity.

---

### Scenarios

**Scenario 1 — Plain text**
Values in DB are unencoded. No prior encryption.
```
WHERE {column} NOT LIKE '{newPrefix}%'
plain = row.value
```

**Scenario 2 — Unsupported legacy algorithm**
Values were encoded with a custom algorithm NativSQL does not know. A `Function<String, String>`
decodes them. No prefix in DB since the old system did not use one.
```
WHERE {column} NOT LIKE '{newPrefix}%'
plain = legacyDecoder.apply(row.value)
```

**Scenario 3 — Supported algorithm, prefix change**
Values are already encrypted with a supported algorithm and carry an old prefix.
`sourceParams` (ALGO, KEY_PROVIDER, PREFIX) describe the old scheme; `CryptMigrator` builds
a `sourceCryptConfig` internally. `sourcePrefix` must differ from the annotation prefix —
otherwise rows cannot be distinguished after re-encryption.
This scenario covers both algorithm changes and key rotation: for key rotation, keep the same
ALGO but change KEY_PROVIDER and PREFIX (e.g. `{ENC_V1}` → `{ENC_V2}`).
```
WHERE {column} LIKE '{sourcePrefix}%'
plain = sourceCryptConfig.decryptOrPassthrough(row.value)  // strips sourcePrefix, decrypts
```

In all scenarios, after migration the row carries the new prefix and is excluded from subsequent runs. ✅

---

Three static factory methods are preferred over constructors for clarity:

```java
public class CryptMigrator<T extends IEntity<ID>, ID> {

    // internal fields — set by factory methods
    private final GenericRepository<T, ID> repository;
    private final String fieldName;
    private final String sourcePrefix;           // null for Scenarios 1 & 2
    private final CryptConfig sourceCryptConfig; // null for Scenarios 1 & 2
    private final Function<String, String> legacyDecoder; // null for Scenarios 1 & 3
    private final int batchSize;                 // default 500
    private final Consumer<Integer> progressCallback; // null = no progress reporting

    /** Scenario 1: values are plain text. */
    public static <T extends IEntity<ID>, ID> CryptMigrator<T, ID> forPlainText(
            GenericRepository<T, ID> repository, Getter<T> getter) { ... }

    /** Scenario 2: values encoded with an unsupported legacy algorithm. */
    public static <T extends IEntity<ID>, ID> CryptMigrator<T, ID> forLegacyAlgo(
            GenericRepository<T, ID> repository, Getter<T> getter,
            Function<String, String> legacyDecoder) { ... }

    /** Scenario 3: values encrypted with a supported algorithm carrying an old prefix. */
    public static <T extends IEntity<ID>, ID> CryptMigrator<T, ID> forReEncrypt(
            GenericRepository<T, ID> repository, Getter<T> getter,
            String sourcePrefix, Map<TypeParamKey, Object> sourceParams) { ... }

    /** Overrides the default batch size (500). */
    public CryptMigrator<T, ID> withBatchSize(int batchSize) { ... } // fluent

    /**
     * Registers a callback called after each batch is committed.
     * The argument is the cumulative number of migrated rows so far.
     * Example: {@code .withProgressCallback(count -> log.info("Migrated {} rows", count))}
     */
    public CryptMigrator<T, ID> withProgressCallback(Consumer<Integer> callback) { ... } // fluent

    /** Runs the migration. Returns the total number of migrated rows. */
    public int migrate() { ... }
}
```

**Guards (checked at factory-method call time — fail fast):**
- Target field not `ENCRYPTED` → `NativSQLException`
- Target algo `isOneWay()` → `NativSQLException`
- Scenario 3: `sourcePrefix` equals annotation prefix → `NativSQLException` (non-incremental)
- Scenario 3: `sourceParams` missing `ALGO` or `KEY_PROVIDER` → `NativSQLException`

**Logic (`migrate()`):**

The target `cryptConfig` (used for `encode()`) is the one configured by the `@Type(ENCRYPTED)`
annotation on the field. `CryptMigrator` retrieves it at factory-method call time via
`annotationManager.getTypeInfo(field)` → `dialect.buildCryptConfig(typeInfo)`.

```
1. (Guards and CryptConfigs resolved at factory-method call)
2. whereClause = sourcePrefix != null
                 ? "{column} LIKE '{sourcePrefix}%'"      // Scenario 3
                 : "{column} NOT LIKE '{newPrefix}%'"     // Scenarios 1 & 2
3. totalMigrated = 0
4. loop:
     BEGIN TRANSACTION
       rows = SELECT id, {column} FROM {table}
              WHERE {whereClause}
              LIMIT this.batchSize
       if rows is empty → COMMIT, break
       for each row:
         plain = sourceCryptConfig != null ? sourceCryptConfig.decryptOrPassthrough(row.value)  // Scenario 3
               : legacyDecoder    != null ? legacyDecoder.apply(row.value)                      // Scenario 2
               :                            row.value                                           // Scenario 1
         encrypted = cryptConfig.encode(plain)     → "{newPrefix}ciphertext"
         UPDATE {table} SET {column} = :encrypted WHERE id = :id
       totalMigrated += rows.size()
     COMMIT
     if progressCallback != null → progressCallback.accept(totalMigrated)
5. return totalMigrated
```

Each batch is an independent transaction via `TransactionTemplate`.
If a batch fails, only that batch is rolled back — previously committed batches are preserved.

**Usage examples:**
```java
// Scenario 1
CryptMigrator.forPlainText(userRepository, User::getEmail).migrate();

// Scenario 2
CryptMigrator.forLegacyAlgo(userRepository, User::getEmail,
    value -> myOldCrypt.decode(value)).migrate();

// Scenario 3
CryptMigrator.forReEncrypt(userRepository, User::getEmail,
    "{ENC_V1}",
    Map.of(TypeParamKey.ALGO, new CryptAlgorithm[]{CryptAlgorithm.AES_CBC},
           TypeParamKey.KEY_PROVIDER, OldKeyProvider.class,
           TypeParamKey.PREFIX, "{ENC_V1}"))
    .withBatchSize(200)
    .migrate();
```

### Step 7bis — Logging precautions

No plain or cipher value must ever appear in a log, exception message, or stack trace.

**Rules, enforced at implementation time:**

| Location | Rule |
| -------- | ---- |
| `AbstractTypeMapper.map()` | `NativSQLException("Unable to map column " + columnName, e)` — column name only, never the raw value |
| `CryptConfig.decryptOrPassthrough()` | Exception message must say "decryption failed for column X" — no ciphertext, no decoded bytes |
| `CryptUtils` | No `log.debug(...)` or `log.trace(...)` at any point in encrypt/decrypt methods |
| `CryptMigrator.migrate()` | The migrator itself logs nothing — progress and row counts only via `progressCallback`; IDs and values never logged internally |
| `NativSQLException` from validation | May include field name and algo name — never a key, prefix value, or data value |

> **Oracle risk:** `decryptOrPassthrough()` has two observable behaviours: silent passthrough (no
> prefix) vs `NativSQLException` (invalid ciphertext). An attacker with DB write access can write
> a value starting with the prefix to provoke a 500 at the API layer. Since AES-GCM verifies the
> authentication tag, no actual decryption oracle exists — but unhandled exceptions that propagate
> to the API response must not include the raw stored value. API-level error handling (e.g. a global
> `@ExceptionHandler`) must catch `NativSQLException` and return a generic error.

### Step 8 — Integration tests

- Insert + select a `@Type(ENCRYPTED)` field; verify raw DB value is not plain text
- Test all supported Java types end-to-end (see type table in §11 above)
- `CryptMigrator.forPlainText()` on a table with mixed plain/encrypted rows: migrates only plain rows, returns correct count, re-running returns 0
- `CryptMigrator` on a field without PREFIX → `NativSQLException`
- `CryptMigrator` on a non-ENCRYPTED field → `NativSQLException`

### Step 9 — Documentation

**`CHANGELOG.md`**

Add a new version entry (next minor version after 2.2.0) following the Keep a Changelog format:

```markdown
## [2.3.0] - ...

### Added
- **`DbDataType.ENCRYPTED`** — transparent field-level encryption via `@Type(DbDataType.ENCRYPTED)`
  with `@TypeParam` to configure the algorithm (`ALGO`), key provider (`KEY_PROVIDER`) and
  storage prefix (`PREFIX`). Supports reversible algorithms (AES-GCM, …) and one-way hashing (BCRYPT).
- **`AbstractTypeMapper`** — new superclass for all type mappers; centralises ResultSet reading,
  null handling, and the encrypt/decrypt path. Concrete mappers now only implement `doMap()` and
  `toDatabaseValue()`.
- **`TypeParamKey`** enum — typed parameter keys for `@TypeParam` (`ALGO`, `KEY_PROVIDER`,
  `PREFIX`, `COST`).
- **`CryptMigrator`** — utility class for incremental batch migration of existing columns to
  `DbDataType.ENCRYPTED`. Three factory methods: `forPlainText()`, `forLegacyAlgo()`,
  `forReEncrypt()`.
- **Transparent WHERE encryption** in `FindQuery` for deterministic algorithms — `whereAndEquals()`
  and `whereAndIn()` automatically encrypt the search value when targeting an `ENCRYPTED` field.

### Changed
- `@Type` now accepts an optional `params` array of `@TypeParam` annotations.
- `ITypeMapper.toDatabase()` and `map()` are now final in `AbstractTypeMapper`; concrete mappers
  override `doMap()` and `toDatabaseValue()` instead.
```

---

**`API.md`**

Add the following sections (after the existing `ITypeMapper` section):

- **`DbDataType.ENCRYPTED`** — description, supported algorithms table (`isDeterministic`,
  `isOneWay`), mandatory params (`ALGO`, `KEY_PROVIDER`, `PREFIX`), optional `FORMAT` param,
  annotated field example, and column sizing formula for both STRING and BINARY formats.
- **`TypeParamKey`** — enum values table with description and applicability per algo type.
- **`@TypeParam` / `@Type` with params** — usage example with `ENCRYPTED`.
- **`AbstractTypeMapper`** — class contract, how to extend it for custom mappers.
- **`CryptKeyProvider`** — interface contract, Spring example, key rotation note (change KEY_PROVIDER + PREFIX together).
- **`CryptMigrator`** — three scenarios with code examples (copy from plan §Step 7).

Update the existing `ITypeMapper` section to note that concrete implementations should now extend
`AbstractTypeMapper` rather than implement `ITypeMapper` directly.

---

**`FAQ.md`**

Add a new section **"Encryption"** with the following entries:

| Question | Summary |
| -------- | ------- |
| How do I encrypt a field? | `@Type(ENCRYPTED)` + `@TypeParam` for algo, key provider, prefix |
| Can I search on an encrypted field (`WHERE`)? | Only with deterministic algos; GCM throws `NativSQLException` |
| Can I use bcrypt for passwords? | Yes — field returns the raw hash; verify with `BCrypt.checkpw()` |
| How do I migrate an existing column to encryption? | Use `CryptMigrator` — three scenarios (plain, legacy, re-encrypt) |
| What happens if I read a value without the prefix? | Returned as-is (migration fallback via `decryptOrPassthrough`) |
| Why is PREFIX mandatory? | It is the migration discriminator — absence = not yet migrated |
| Can plain values appear in logs? | No — NativSQL never logs values on the crypt path. Callers must also avoid logging encrypted-field values. |
| How do I rotate the encryption key? | Change KEY_PROVIDER and PREFIX together (e.g. `{ENC_V1}` → `{ENC_V2}`), then run `CryptMigrator.forReEncrypt()`. Keeping the same prefix with a new key breaks incremental migration. |
| How large should the DB column be? | Ciphertext is longer than plaintext. For AES-GCM, STRING format: `ceil((len + 28) * 4 / 3)` chars; BINARY format: `len + 28` bytes. Always verify column size before deploying. |
| STRING vs BINARY format? | STRING (default) stores Base64 in VARCHAR — portable and debuggable. BINARY stores raw bytes in VARBINARY — ~25% more compact. Choose based on volume and tooling constraints. |

---

**`DOCS.md`**

Add to the "Common Scenarios" section:

```markdown
- **How do I encrypt a field?** → [API.md#encryption](API.md#encryption)
- **How do I migrate an existing column to encryption?** → [API.md#cryptmigrator](API.md#cryptmigrator)
```

---

**Primitive types — documentation note (all three docs)**

NativSQL does not support Java primitive types (`int`, `long`, `boolean`, `double`, etc.) — entity
fields must use boxed types (`Integer`, `Long`, `Boolean`, `Double`, …). This is an existing
constraint, not specific to encryption, but must be documented to prevent confusion when annotating
encrypted numeric fields.

Add the following to:
- `FAQ.md` — new entry in a "Known constraints" section: "Primitive types (`int`, `long`, …) are
  not supported. Use boxed types (`Integer`, `Long`, …) for all entity fields."
- `API.md` — note in the `@Type` / `DbDataType` section and in the encryption section.
- `DOCS.md` — note in the "Getting started" or "Entity mapping" section.
