# Plan: findById/findAllByIds always set the entity id

## Steps

**Step 1 — `ensureIdColumnSelected` helper in `GenericRepository`** ✅

Add a small private helper that takes the caller-supplied `String[] columns` and
returns a new array guaranteed to contain `ID_COLUMN`, without duplicating it if
already present (case-insensitive-safe comparison against `ID_COLUMN`).

```java
private String[] ensureIdColumnSelected(String[] columns) {
    for (String column : columns) {
        if (ID_COLUMN.equalsIgnoreCase(column)) {
            return columns;
        }
    }
    String[] result = Arrays.copyOf(columns, columns.length + 1);
    result[columns.length] = ID_COLUMN;
    return result;
}
```

File: `nativsql-core/src/main/java/ovh/heraud/nativsql/repository/GenericRepository.java`

---

**Step 2 — Use the helper in `findById(Object, String...)`** ✅

Line ~523-531: keep the existing empty-array check on the *original* `columns`
argument (so `findById(id)` still throws as before), then call
`ensureIdColumnSelected(columns)` before passing to `.select(...)`.

```java
public T findById(Object id, String... columns) {
    if (columns == null || columns.length == 0) {
        throw new NativSQLException("Column list cannot be empty");
    }
    return find(
            newFindQuery()
                    .select(ensureIdColumnSelected(columns))
                    .whereAndEquals(ID_COLUMN, id));
}
```

File: `nativsql-core/src/main/java/ovh/heraud/nativsql/repository/GenericRepository.java`

---

**Step 3 — Use the helper in `findAllByIds(List<?>, String...)`** ✅

Line ~561-569: same change.

```java
public List<T> findAllByIds(List<?> ids, String... columns) {
    if (columns == null || columns.length == 0) {
        throw new NativSQLException("Column list cannot be empty");
    }
    return findAll(
            newFindQuery()
                    .select(ensureIdColumnSelected(columns))
                    .whereAndIn(ID_COLUMN, ids));
}
```

File: `nativsql-core/src/main/java/ovh/heraud/nativsql/repository/GenericRepository.java`

Note: the `Getter<T>...` overloads of both methods (lines ~508, ~546) already delegate
to these two `String...` methods — no change needed there.

---

**Step 4 — Unit/integration tests** ✅

- `nativsql-test-commons/src/main/java/ovh/heraud/nativsql/repository/IDataTypeTests.java`
  (~line 93): after `readRepo.findById(data.getId(), "data")`, add an assertion that
  `getId()` equals the expected id, in addition to the existing `getData()` assertion.
- Add an equivalent case for `findAllByIds` with a partial column list (id omitted),
  asserting every returned entity has the correct, matching id.
- Add a case where the id column *is* explicitly requested alongside other columns
  (e.g. `findById(id, "id", "name")`) to confirm no duplicate-column SQL error and
  correct mapping — regression guard for the dedup logic.

Files: `nativsql-test-commons/src/main/java/ovh/heraud/nativsql/repository/IDataTypeTests.java`
(modified), plus corresponding Postgres integration test file(s) under
`nativsql-postgres/src/test/.../repository/postgres/` if `findAllByIds` partial-column
coverage doesn't already exist there.

---

**Step 5 — Documentation** ✅

- `CHANGELOG.md` — new entry describing that `findById`/`findAllByIds` now always
  populate the entity id, with a reference to
  `doc/issues/92-findbyid-sets-id/spec.md`. Ask the user via `AskUserQuestion` whether
  to bump the version number before writing the entry.
- `README.md` — update only if it documents `findById`/`findAllByIds` column-selection
  behaviour explicitly; otherwise no change needed (behavioural fix, no API change).

---

## Verification

```bash
./gradlew :nativsql-core:compileJava          # must compile without errors
./gradlew :nativsql-core:test                 # unit tests must pass
./gradlew :nativsql-postgres:test             # integration tests must pass
```

(Ask for confirmation via `AskUserQuestion` before running any of the above, per project
convention.)
