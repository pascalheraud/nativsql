# Plan: Find methods may return an `Optional` as well

## Steps

**Step 1 — Add `import java.util.Optional;` to `GenericRepository`**

File: `nativsql-core/src/main/java/ovh/heraud/nativsql/repository/GenericRepository.java`

---

**Step 2 — Add `findOptionalById` overloads next to `findById`**

Right after `findById(Object, String...)` (`:632-640`):

```java
@SafeVarargs
public final Optional<T> findOptionalById(Object id, Getter<T>... getters) {
    return Optional.ofNullable(findById(id, getters));
}

public Optional<T> findOptionalById(Object id, String... columns) {
    return Optional.ofNullable(findById(id, columns));
}
```

---

**Step 3 — Add `findOptionalByProperty` overloads next to the 4 `findByProperty` overloads**

Right after `findByProperty(String, Object, String...)` (`:766-772`):

```java
protected final Optional<T> findOptionalByProperty(Getter<T> propertyGetter, Object value, String... columns) {
    return Optional.ofNullable(findByProperty(propertyGetter, value, columns));
}

@SafeVarargs
protected final Optional<T> findOptionalByProperty(Getter<T> propertyGetter, Object value, Getter<T>... getters) {
    return Optional.ofNullable(findByProperty(propertyGetter, value, getters));
}

@SafeVarargs
protected final Optional<T> findOptionalByProperty(String property, Object value, Getter<T>... getters) {
    return Optional.ofNullable(findByProperty(property, value, getters));
}

protected Optional<T> findOptionalByProperty(String property, Object value, String... columns) {
    return Optional.ofNullable(findByProperty(property, value, columns));
}
```

---

**Step 4 — Add `findOptionalByPropertyExpression` overloads next to the 2 `findByPropertyExpression` overloads**

Right after `findByPropertyExpression(String, String, Object, String...)` (`:1116-1121`):

```java
@SafeVarargs
protected final Optional<T> findOptionalByPropertyExpression(String propertyExpression, String paramName, Object value,
        Getter<T>... getters) {
    return Optional.ofNullable(findByPropertyExpression(propertyExpression, paramName, value, getters));
}

protected Optional<T> findOptionalByPropertyExpression(String propertyExpression, String paramName, Object value,
        String... columns) {
    return Optional.ofNullable(findByPropertyExpression(propertyExpression, paramName, value, columns));
}
```

---

**Step 5 — Add `findOptional(FindQuery)` and `findOptional(FindQuery, Class<R>)` next to `find(FindQuery)` / `find(FindQuery, Class<R>)`**

Right after `find(FindQuery<T, ID> query)` (`:1260-1282`) and right after `find(FindQuery<T, ID> query, Class<R> resultClass)` (`:1312-1334`):

```java
protected Optional<T> findOptional(FindQuery<T, ID> query) {
    return Optional.ofNullable(find(query));
}

protected <R extends T> Optional<R> findOptional(FindQuery<T, ID> query, Class<R> resultClass) {
    return Optional.ofNullable(find(query, resultClass));
}
```

---

**Step 6 — Tests**

Add a new test class `GenericRepositoryFindOptionalTest` (following the [[tests]] skill conventions,
real DB via Testcontainers) covering:
- `findOptionalById` — present and absent id
- `findOptionalByProperty` (via a subclass method, mirroring how `PostgresUserRepository.findByEmail`
  wraps `findByProperty`) — present and absent value
- `findOptional(FindQuery)` — present and absent
- Verify existing `findById`/`findByProperty`/`find(FindQuery)` behavior is unchanged (no regression)

---

**Step 7 — Documentation**

- Update `CHANGELOG.md`: new entry under version `2.11.0` referencing
  `doc/issues/105-find-optional/spec.md`.
- Bump `gradle.properties` `version` to `2.11.0`.

---

**Step 8 — Verification**

```bash
./gradlew compileJava
./gradlew test   # ask user first
./gradlew build
```

Update `ARCHITECTURE.md` only if these additive methods change any documented structure (unlikely —
no new classes/modules).
