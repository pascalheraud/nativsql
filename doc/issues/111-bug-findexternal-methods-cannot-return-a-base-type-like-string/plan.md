# Plan: `findExternal`/`findAllExternal` support for scalar result types

> See `spec.md` for the full design rationale.

## Steps

1. **`DatabaseDialect`** — promote `GenericDialect.getMapperForType(Class<T>)` to the interface:
   ```java
   <T> ITypeMapper<T> getMapperForType(Class<T> targetType);
   ```
   Add javadoc explaining it returns the scalar mapper for base/JDBC types, `null` for
   entity/bean types.

2. **`GenericDialect`** — change `getMapperForType` from `protected` to `public`, add
   `@Override`. No logic change.

3. **`AbstractChainedDialect`** — add a delegating implementation, same pattern as
   `buildExistsQuery`/`extractExistsResult`:
   ```java
   @Override
   public <T> ITypeMapper<T> getMapperForType(Class<T> targetType) {
       if (nextDialect != null) {
           return nextDialect.getMapperForType(targetType);
       }
       throw new NativSQLException("No dialect found in chain to get mapper for type");
   }
   ```

4. **New `ScalarRowMapper<T>`** in `nativsql-core/.../mapper/ScalarRowMapper.java`:
   - Implements `RowMapper<T>` (Spring).
   - Constructor: `(Class<T> resultClass, ITypeMapper<T> typeMapper)`.
   - `mapRow`: reads `rs.getMetaData().getColumnCount()`; if `!= 1`, throws `NativSQLException`
     naming `resultClass.getSimpleName()` and the actual column count; otherwise reads column 1's
     label and calls `typeMapper.map(rs, columnLabel, null, Collections.emptyMap())`.

5. **`RowMapperFactory`**:
   - Cache field type: `Map<Class<?>, GenericRowMapper<?>>` → `Map<Class<?>, RowMapper<?>>`.
   - Public `getRowMapper(...)` return type: `GenericRowMapper<T>` → `RowMapper<T>`.
   - In `getRowMapper`, after the cache miss: call `dialect.getMapperForType(clazz)`; if non-null,
     build/cache/return a `ScalarRowMapper<>(clazz, scalarMapper)`; otherwise delegate to the
     existing bean path.
   - Existing `createRowMapper(...)` (bean introspection) stays private, unchanged internally,
     but its recursive call for joined sub-properties (line ~82, `getRowMapper(fieldAccessor
     .getType(), ...)`) must keep resolving to a `GenericRowMapper` specifically (needed for
     `.mapColumn(...)`/`.createNewInstance(...)` used by `JoinedPropertyMetadata`). Since a
     joined-property field's declared type is by construction never a base type (it only reaches
     this branch when `dialect.getMapper(fieldAccessor, ...)` already returned `null` for that
     field), add a small private helper `getBeanRowMapper(Class<T>, ...)` that shares the same
     cache but always goes through `createRowMapper` (skips the scalar check), and call that from
     the recursive site instead of the public `getRowMapper`.

6. **`GenericRepository`** — no change. `findExternal`/`findAllExternal` already call
   `rowMapperFactory.getRowMapper(...)`; the new scalar behavior is transparent to them.

7. **Tests**:
   - **Unit** (`nativsql-core/src/test/java/ovh/heraud/nativsql/mapper/`): new
     `ScalarRowMapperTest` — mock `ResultSet`/`ResultSetMetaData` directly (no DB, no Spring
     context, matches the mapper-package unit-test style used for e.g.
     `LongTypeMapper`/`StringTypeMapper` tests):
     - single column, non-null value → mapped correctly
     - single column, null value → returns null
     - two columns → throws `NativSQLException` mentioning the type name and `2`
     - zero columns → throws `NativSQLException` mentioning the type name and `0`
   - **Integration** (`nativsql-postgres/src/test/java/.../PostgresUserRepository*`): add two
     narrowly-scoped repository methods (per the query-encapsulation convention — no generic
     passthrough):
     ```java
     public Long countAllUsers() {
         return findExternal("select count(*) from users", Long.class);
     }

     public List<Long> findAllUserIds() {
         return findAllExternal("select id from users", Long.class);
     }

     public Long findFirstUserIdAndEmail() {
         // deliberately 2 columns, to exercise the ambiguous-column error path
         return findExternal("select id, email from users limit 1", Long.class);
     }
     ```
     New test class `PostgresFindExternalScalarTest extends PostgresRepositoryTest`:
     - `countAllUsers()` on a table with N rows → returns `N` as `Long`
     - `findAllUserIds()` → returns the list of inserted user IDs
     - `findFirstUserIdAndEmail()` (2-column query, `Long` result class) →
       `assertThatThrownBy(...).isInstanceOf(NativSQLException.class)`

8. **Docs**:
   - `CHANGELOG.md` — add an entry under **[2.11.0]**, referencing
     `doc/issues/111-bug-findexternal-methods-cannot-return-a-base-type-like-string/spec.md`.
   - `gradle.properties` — bump `version` to `2.11.0`.
   - `ARCHITECTURE.md` — update the mapper-hierarchy section: `RowMapperFactory` now produces
     either a `GenericRowMapper` (bean) or a `ScalarRowMapper` (single-column scalar), selected via
     `DatabaseDialect.getMapperForType`.

## Verification

```bash
./gradlew compileJava
./gradlew build
```
Ask before running `./gradlew test` (project convention).

## Log

- Implemented as designed: `DatabaseDialect.getMapperForType` promoted from `GenericDialect`
  (protected) to the interface; `AbstractChainedDialect` delegates it down the chain like
  `buildExistsQuery`/`extractExistsResult`; new `ScalarRowMapper<T>`; `RowMapperFactory` branches
  on `dialect.getMapperForType(clazz) != null`, with a new private `getBeanRowMapper(...)` helper
  used only by the recursive joined-sub-property call site (which must stay a `GenericRowMapper`
  for `JoinedPropertyMetadata`).
- All modules (`compileJava`, `compileTestJava`) compile clean across core + all dialects
  (postgres, oracle, mysql/mariadb).
- `./gradlew test` run in full — all tests pass, including new `ScalarRowMapperTest` (unit) and
  `PostgresFindExternalScalarTest` (integration, Testcontainers).
- Version bumped to 2.11.0 per user instruction (this is the target release version, not a
  further bump beyond it).
