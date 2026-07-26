# Plan: `@OnUpdate` — framework-managed computed value on update

## Steps

**Step 1 — `UpdateValueProvider<T>` functional interface**

```java
package ovh.heraud.nativsql.util;

@FunctionalInterface
public interface UpdateValueProvider<T> {
    T getValue();
}
```

File: `nativsql-core/src/main/java/ovh/heraud/nativsql/util/UpdateValueProvider.java`

---

**Step 2 — `Instant.now()` test-fixture provider**

Not a framework-shipped class — since only test code (this project's own tests, the
`User` test entity) references a timestamp provider, it lives in `nativsql-core`'s
`testFixtures` source set instead of `src/main`, so it's not part of the published
`nativsql-core` artifact:

```java
package ovh.heraud.nativsql.util;

import java.time.Instant;

public class SystemUpdateValueProvider implements UpdateValueProvider<Instant> {
    @Override
    public Instant getValue() {
        return Instant.now();
    }
}
```

File: `nativsql-core/src/testFixtures/java/ovh/heraud/nativsql/util/SystemUpdateValueProvider.java`

---

**Step 3 — `@OnUpdate` annotation**

```java
package ovh.heraud.nativsql.annotation;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OnUpdate {
    Class<? extends UpdateValueProvider<?>> value();
}
```

File: `nativsql-core/src/main/java/ovh/heraud/nativsql/annotation/OnUpdate.java`

No default: the field type is not constrained by the framework, so defaulting to a
date-typed provider would be wrong for a non-`Instant` field. Callers always specify
a provider class explicitly — `SystemUpdateValueProvider.class` for the common
`updateDate` case, a custom class for other use cases (version counters, audit fields,
a mocked clock in tests, ...).

---

**Step 4 — `OnUpdateInfo` holder + cache + accessor in `AnnotationManager`**

Unlike `MappedByInfo` (one association per field, one field looked up at a time), an
entity can have **several** independent `@OnUpdate` fields (e.g. `updateDate` +
`version`), so the cache holds a list per class rather than a single value per field:

```java
private final Map<Class<?>, List<OnUpdateInfo>> onUpdateCache = new ConcurrentHashMap<>();

public List<OnUpdateInfo> getOnUpdateInfos(Class<?> entityClass) {
    return onUpdateCache.computeIfAbsent(entityClass, cls -> {
        List<OnUpdateInfo> infos = new ArrayList<>();
        for (FieldAccessor<?> fa : ReflectionUtils.getFields(cls)) {
            OnUpdate ann = fa.getAnnotation(OnUpdate.class);
            if (ann != null) {
                UpdateValueProvider<?> provider = (UpdateValueProvider<?>) resolveBean(ann.value(), fa.getName());
                infos.add(new OnUpdateInfo(fa.getName(), provider));
            }
        }
        return infos; // empty list cached fine, no null-sentinel needed
    });
}
```

Notes:
- `resolveBean` is currently `private` (`AnnotationManager.java:272-281`) — no visibility
  change needed since `getOnUpdateInfos` lives in `AnnotationManager` itself (recommended,
  keeps the pattern consistent with `getMappedByInfo`).
- `OnUpdateInfo` is a small record: `record OnUpdateInfo(String fieldName, UpdateValueProvider<?> provider)`.
- An empty `List` is a perfectly cacheable "no `@OnUpdate` field" result — no
  null-sentinel trick needed here (unlike a single-value cache).

Also add, mirroring `setMappedByInfo` (`AnnotationManager.java:291-295`), replacing
whatever list was previously registered/discovered for that class:

```java
public void setOnUpdateInfo(Class<?> clazz, String fieldName, UpdateValueProvider<?> provider) {
    onUpdateCache.compute(clazz, (c, existing) -> {
        List<OnUpdateInfo> infos = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        infos.removeIf(i -> i.fieldName().equals(fieldName));
        infos.add(new OnUpdateInfo(fieldName, provider));
        return infos;
    });
}
```

File: `nativsql-core/src/main/java/ovh/heraud/nativsql/annotation/AnnotationManager.java`
New file: `nativsql-core/src/main/java/ovh/heraud/nativsql/util/OnUpdateInfo.java` (follow
whichever convention `MappedByInfo`/`OneToManyAssociation` already use; they're standalone
classes in `util/`, so match that).

---

**Step 5 — Wire into `GenericRepository.update(T, String...)`**

Current code (`GenericRepository.java:342-373`):

```java
public void update(T entity, String... columns) {
    if (columns == null || columns.length == 0) {
        throw new NativSQLException("Column list cannot be empty");
    }
    Map<String, Object> rawParams = extractValues(entity, columns);
    ...
```

Change to inject each `@OnUpdate` column not already requested by the caller:

```java
public void update(T entity, String... columns) {
    if (columns == null || columns.length == 0) {
        throw new NativSQLException("Column list cannot be empty");
    }

    List<OnUpdateInfo> onUpdateInfos = annotationManager.getOnUpdateInfos(entityClass);
    List<OnUpdateInfo> autoApplied = new ArrayList<>();
    List<String> effectiveColumnsList = new ArrayList<>(Arrays.asList(columns));
    Map<String, Object> autoValues = new HashMap<>();
    for (OnUpdateInfo info : onUpdateInfos) {
        if (Arrays.stream(columns).noneMatch(c -> info.fieldName().equalsIgnoreCase(c))) {
            Object value = info.provider().getValue();
            if (value == null) {
                throw new NativSQLException("@OnUpdate provider must not return null (field '"
                        + info.fieldName() + "')");
            }
            autoValues.put(info.fieldName(), value);
            effectiveColumnsList.add(info.fieldName());
            autoApplied.add(info);
        }
    }
    String[] effectiveColumns = effectiveColumnsList.toArray(new String[0]);

    Map<String, Object> rawParams = extractValues(entity, columns); // still only caller's columns
    rawParams.putAll(autoValues);
    FieldAccessor<ID> idField = entityFields.get(ID_COLUMN);
    Object id = idField != null ? idField.getValue(entity) : null;
    rawParams.put(ID_COLUMN, id);

    String setClause = Arrays.stream(effectiveColumns)
            .map(col -> {
                if (col == null || col.isEmpty()) {
                    throw new NativSQLException("Column name cannot be null or empty");
                }
                FieldAccessor<ID> field = entityFields.get(col);
                return identifierConverter.toDB(col) + " = " + formatParameter(col, field);
            })
            .collect(Collectors.joining(", "));

    String idColumnSnake = identifierConverter.toDB(ID_COLUMN);
    String sql = "UPDATE " + getTableName() + " SET " + setClause + " WHERE " + idColumnSnake + " = :" + ID_COLUMN;

    Map<String, Object> sqlParams = convertParamsToSqlValues(rawParams);
    Map<String, Object> logParams = convertParamsForLogging(rawParams);
    dbOperationLogger.execute(getClass(), "update", "UPDATE", getTableName(), sql, logParams, () -> {
        int rowsUpdated = executeUpdate(sql, sqlParams);
        if (rowsUpdated != 1) {
            throw new NativSQLException(
                    "Update failed: expected to update exactly 1 row but updated " + rowsUpdated);
        }
    });

    for (OnUpdateInfo info : autoApplied) {
        @SuppressWarnings("unchecked")
        FieldAccessor<Object> field = (FieldAccessor<Object>) entityFields.get(info.fieldName());
        field.setValue(entity, autoValues.get(info.fieldName()));
    }
}
```

`update(T entity, Getter<T>... getters)` (`GenericRepository.java:327-331`) needs no
change — it already converts getters to column names and delegates here, so it picks up
the new behaviour automatically, including the "explicit override" path if the caller's
getter list already includes an `@OnUpdate` field.

Superseded by the final design: rather than a standalone `logOnUpdate(...)` call before
`execute(...)`, `DbOperationLogger` gained a single `executeUpdate(Class<?>
repositoryClass, String table, String sql, Map<String,Object> params,
Map<String,Object> onUpdateValues, SqlRunnable runnable)` method that logs the
`DB.ONUPDATE` line (if `onUpdateValues` is non-empty) and then delegates to `execute(...)`
internally — so `GenericRepository.update()` has a single log-related call site, and no
direct SLF4J `Logger` is used in `GenericRepository` (all DB-operation logging goes
through `DbOperationLogger`, per the `nativ-sql` skill). `insert()` got the symmetric
`executeInsert(...)` when `@OnInsert` was added (see addendum below).

Also superseded: rather than a separate `autoValues` map plus a post-success write-back
loop, the accepted (simpler) design writes each `@OnInsert`/`@OnUpdate` computed value
directly onto the entity **before** the SQL runs, via a private
`applyComputedFields(T entity, String[] columns, List<ComputedFieldInfo> infos, String
annotationName, List<String> appliedFieldNames)` helper shared by `insert()` and
`update()`. Tradeoff accepted by Pascal: if the subsequent INSERT/UPDATE fails, the
entity keeps the new, uncommitted value(s) rather than reverting — see spec.md
"Behaviour" for the full rationale.

File: `nativsql-core/src/main/java/ovh/heraud/nativsql/repository/GenericRepository.java`

---

**Step 6 — Unit tests**

Follow `backend-java-test` conventions (deterministic test data, no wall-clock
dependency). Add a test entity + a fixed-value `ComputedValueProvider` (or reuse
`AnnotationManager.setComputedFieldInfo` to inject a lambda per-test) to assert:

- `update(entity, "name")` also writes the `@OnUpdate` column with the provider's value.
- `update(entity, "name", "updateDate")` (explicit) keeps the caller's value, provider
  not invoked for that column.
- Two independent `@OnUpdate` fields on one entity (e.g. `updateDate` + `version`) are
  both applied on a single `update()` call.
- Provider returning `null` → `NativSQLException`.
- Entity without `@OnUpdate` → `update()` behaves exactly as before (regression check).
- Non-date use case: an `Integer`/`Long` `version` field with an incrementing provider, to
  confirm the mechanism isn't accidentally date-typed anywhere in the implementation.
  (Note: the field must be the boxed type, not a Java primitive — `GenericDialect.getMapper`
  rejects primitive-typed fields framework-wide, this isn't specific to `@OnUpdate`/`@OnInsert`.)

Cross-dialect: check `nativsql-test-commons` for a shared test class pattern
(`IDataTypeTests`-style) if this needs to run against postgres/mysql/mariadb/oracle test
schemas, or if a single core-level test (in-memory / one dialect) is enough — decide at
implementation time based on whether SQL generation differs per dialect here (it
shouldn't, since this only affects the `SET` clause the same way any other column would).

---

**Step 7 — Documentation**

- `CHANGELOG.md`: add entry under 2.9.0 referencing `doc/issues/76-on-update/spec.md`.
- `README.md`: add a short section/example under wherever `@Encrypted`/`@MappedBy` are
  documented, showing `@OnUpdate` usage for the `updateDate` case and mentioning it's
  generic (not date-specific).

---

## Addendum — `@OnInsert` extension + `ComputedValueProvider`/`ComputedFieldInfo` rename

After the `@OnUpdate` implementation above landed, the same "caller-overridable,
framework-computed column" need was identified for `insert()` (a `creationDate` case),
leading to a symmetric `@OnInsert` annotation. This addendum captures what changed
relative to the steps above, rather than rewriting them:

1. **Rename `UpdateValueProvider` → `ComputedValueProvider`, `OnUpdateInfo` →
   `ComputedFieldInfo`.** Both were update-specific names; once `@OnInsert` reuses the
   same provider interface and cache-entry shape, the names had to stop implying
   "update". `AnnotationManager.getComputedFieldInfos`/`setComputedFieldInfo` (renamed
   from `getOnUpdateInfos`/`setOnUpdateInfo`) remain the `@OnUpdate`-specific cache
   accessors; a parallel `onInsertCache`/`getOnInsertFieldInfos`/`setOnInsertFieldInfo`
   was added for `@OnInsert`.
2. **`@OnInsert` annotation** (`nativsql-core/src/main/java/ovh/heraud/nativsql/annotation/OnInsert.java`) —
   identical shape to `@OnUpdate`: `Class<? extends ComputedValueProvider<?>> value();`,
   no default.
3. **`GenericRepository.insert()`** — same auto-apply/explicit-override logic as
   `update()`, factored into a shared private `applyComputedFields(...)` helper (see
   Step 5 note above) so the two methods don't duplicate the loop. `insert()`'s
   `dbOperationLogger.execute(...)` call became `dbOperationLogger.executeInsert(...)`,
   the `@OnInsert` counterpart to `executeUpdate(...)`.
4. **`DbOperationLogger.executeInsert(Class<?> repositoryClass, String table, String sql,
   Map<String,Object> params, Map<String,Object> onInsertValues, SqlCallable<T>
   callable)`** — symmetric to `executeUpdate(...)` but generic over the return type
   (the generated id), logging `DB.ONINSERT` instead of `DB.ONUPDATE`.
5. **Test-fixture providers moved out of `nativsql-core`'s main sources.** Since only
   test code referenced a concrete timestamp provider, `SystemComputedValueProvider`
   (`Instant`) and `SystemLocalDateTimeComputedValueProvider` (`LocalDateTime`, needed
   because the `User` test entity's `createdAt` is `LocalDateTime` not `Instant`) live in
   `nativsql-core/src/testFixtures/java/ovh/heraud/nativsql/util/` — not part of the
   published `nativsql-core` artifact. `nativsql-core`'s main sources ship no concrete
   provider at all; users write their own `ComputedValueProvider<T>`.
6. **Tests**: `GenericRepositoryOnInsertTest` (new, mirrors
   `GenericRepositoryOnUpdateTest`) covers auto-apply, explicit override, two independent
   `@OnInsert` fields, null-provider exception, no-annotation regression, and a non-date
   (`String`) use case. `PostgresUserRepositoryTest` gained
   `testOnInsertAutoAppliesCreatedAt`/`testOnInsertExplicitOverrideKeepsCallerValue`,
   mirroring the existing `@OnUpdate` repository-level tests, using `User.createdAt`
   (`@OnInsert(SystemLocalDateTimeComputedValueProvider.class)`).
7. **`@OnUpdate` fields are not seeded on `insert()`, by design** — `@OnInsert` is a
   separate annotation rather than making `@OnUpdate` fields also apply at insert time,
   so `updateDate` and `creationDate` stay independently controlled per field.
