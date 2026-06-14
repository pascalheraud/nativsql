# Plan: limit/offset on FindQuery — version 2.6.0

## Steps

**Step 1 — Fields and fluent methods in `FindQuery`** ✅

Add `private Integer limit` and `private Integer offset` fields to `FindQuery`.
Add `limit(int n)` and `offset(int n)` fluent methods with validation:
- `limit(n <= 0)` → `NativSQLException("limit must be greater than 0")`
- `offset(n < 0)` → `NativSQLException("offset must be greater than or equal to 0")`

File: `nativsql-core/src/main/java/ovh/heraud/nativsql/util/FindQuery.java`

---

**Step 2 — `buildSql`: append OFFSET/FETCH after ORDER BY** ✅

After the ORDER BY block and before the trailing `\n`:

```java
if (offset != null && offset > 0) {
    sb.append("\nOFFSET ").append(offset).append(" ROWS");
}
if (limit != null) {
    String fetchKeyword = (offset != null && offset > 0) ? "NEXT" : "FIRST";
    sb.append("\nFETCH ").append(fetchKeyword).append(" ").append(limit).append(" ROWS ONLY");
}
```

`offset(0)` is suppressed (no SQL output). `FETCH NEXT` is used only when offset > 0.

File: `nativsql-core/src/main/java/ovh/heraud/nativsql/util/FindQuery.java`

---

**Step 3 — Unit tests** ✅

8 flat test methods covering: limit only, offset only, limit+offset, offset=0, limit=0 exception,
limit<0 exception, offset<0 exception, combined WHERE+ORDER BY+limit+offset clause order.

File: `nativsql-core/src/test/java/ovh/heraud/nativsql/util/FindQueryLimitOffsetTest.java` (new)

---

**Step 4 — Integration tests** ✅

Add `findPageByOrderById(int limit, int offset, String... columns)` to the test repository helper.
Create `PostgresPaginationTest` with two tests against a real PostgreSQL container:
- `find_with_limit_and_offset_returns_correct_page` — 10 rows, limit 3 offset 3 → rows 4–6
- `find_with_limit_only_returns_first_n_rows` — 5 rows, limit 2 → 2 rows

Files:
- `nativsql-postgres/src/test/.../PostgresUserRepository.java` (modified)
- `nativsql-postgres/src/test/.../PostgresPaginationTest.java` (new)

---

**Step 5 — Documentation** ✅

- `CHANGELOG.md` — new `[2.6.0]` section.
- `README.md` — new "Pagination" section with fluent API example and generated SQL.

---

## Verification

```bash
./gradlew :nativsql-core:compileJava          # must compile without errors
./gradlew :nativsql-core:test                 # unit tests must pass
./gradlew :nativsql-postgres:test             # integration tests must pass
```
