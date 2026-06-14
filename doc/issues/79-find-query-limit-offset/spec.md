# Spec: `limit` and `offset` on `FindQuery`

> Issue: [nativsql#79](https://github.com/heraud/nativsql/issues/79)
> **Status: PENDING**

## Goal

Add `limit(int)` and `offset(int)` fluent methods to `FindQuery` so callers can
paginate results without writing raw SQL.

---

## SQL syntax decision — `FETCH FIRST` vs `LIMIT`

| Syntax | Standard | PostgreSQL | H2 | MySQL | Oracle | SQL Server |
|---|---|---|---|---|---|---|
| `OFFSET m ROWS FETCH NEXT n ROWS ONLY` | SQL:2008 ✓ | ✓ | ✓ | ✗ | 12c+ ✓ | 2012+ ✓ |
| `LIMIT n OFFSET m` | Non-standard | ✓ | ✓ | ✓ | ✗ | ✗ |

**Decision: use `FETCH FIRST` / `FETCH NEXT` (SQL:2008 standard).**

Rationale: NativSQL targets multiple databases through `DatabaseDialect`. The
standard syntax works on every database NativSQL currently supports (PostgreSQL,
H2). It is forward-compatible with any future SQL-standard-compliant target.
`LIMIT` would require a dialect-specific override for Oracle and SQL Server.

Full standard form used in generated SQL:

```sql
-- offset only
OFFSET 20 ROWS

-- limit only
FETCH FIRST 10 ROWS ONLY

-- both (offset must appear before FETCH)
OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY
```

`FETCH NEXT` (vs `FETCH FIRST`) is used when an offset is present, to be
semantically accurate (fetch the *next* N rows after skipping M). Both are
equivalent in practice and both are standard.

---

## API

Two new fluent methods added to `FindQuery<T, ID>`:

```java
/**
 * Limits the number of rows returned.
 * Generates FETCH FIRST n ROWS ONLY (or FETCH NEXT n ROWS ONLY when combined with offset).
 *
 * @param n the maximum number of rows (must be > 0)
 * @throws NativSQLException if n <= 0
 */
public FindQuery<T, ID> limit(int n)

/**
 * Skips the first n rows before returning results.
 * Generates OFFSET n ROWS.
 * Must be combined with an ORDER BY for deterministic results.
 *
 * @param n the number of rows to skip (must be >= 0)
 * @throws NativSQLException if n < 0
 */
public FindQuery<T, ID> offset(int n)
```

Both methods return `this` for fluent chaining.

### Validation

| Call | Rule | Exception message |
|---|---|---|
| `limit(0)` | invalid | `"limit must be greater than 0"` |
| `limit(-1)` | invalid | `"limit must be greater than 0"` |
| `offset(-1)` | invalid | `"offset must be greater than or equal to 0"` |
| `offset(0)` | valid (no-op equivalent) | — |

---

## SQL output

`limit` and `offset` are appended **after** `ORDER BY` in the SQL clause order:

```
SELECT …
FROM …
[WHERE …]
[ORDER BY …]
[OFFSET m ROWS]
[FETCH FIRST|NEXT n ROWS ONLY]
```

### Examples

```java
query.limit(10);
// → FETCH FIRST 10 ROWS ONLY

query.offset(20);
// → OFFSET 20 ROWS

query.limit(10).offset(20);
// → OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY

query.orderByAsc("name").limit(5).offset(0);
// ORDER BY name ASC
// FETCH FIRST 5 ROWS ONLY
// (offset 0 is suppressed)
```

`offset(0)` produces no SQL output (same as no offset).

---

## Implementation

### State added to `FindQuery`

```java
private Integer limit = null;   // null = no limit
private Integer offset = null;  // null = no offset
```

### `buildSql` changes

After the `ORDER BY` block and before the trailing `\n`:

```java
if (offset != null && offset > 0) {
    sb.append("\nOFFSET ").append(offset).append(" ROWS");
}
if (limit != null) {
    String fetchKeyword = (offset != null && offset > 0) ? "NEXT" : "FIRST";
    sb.append("\nFETCH ").append(fetchKeyword).append(" ").append(limit).append(" ROWS ONLY");
}
```

No named parameters are involved — the values are inlined directly into the SQL
string (they are integers controlled by the application, not user input, so
there is no SQL injection risk).

---

## Tests

### Unit — `FindQueryLimitOffsetTest`

```java
@Test
void limit_appends_fetch_first_clause() {
    // Given: a FindQuery with limit(10)
    // When: building the SQL
    // Then: SQL ends with FETCH FIRST 10 ROWS ONLY
}

@Test
void offset_appends_offset_rows_clause() {
    // Given: a FindQuery with offset(20)
    // When: building the SQL
    // Then: SQL contains OFFSET 20 ROWS
}

@Test
void limit_and_offset_use_fetch_next_and_correct_order() {
    // Given: limit(10).offset(20)
    // When: building the SQL
    // Then: SQL contains OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY (in that order)
}

@Test
void offset_zero_produces_no_offset_clause() {
    // Given: offset(0)
    // When: building the SQL
    // Then: SQL does NOT contain OFFSET
}

@Test
void limit_zero_throws_nativsql_exception() {
    // Given / When: limit(0)
    // Then: NativSQLException with message "limit must be greater than 0"
}

@Test
void limit_negative_throws_nativsql_exception() {
    // Given / When: limit(-5)
    // Then: NativSQLException with message "limit must be greater than 0"
}

@Test
void offset_negative_throws_nativsql_exception() {
    // Given / When: offset(-1)
    // Then: NativSQLException with message "offset must be greater than or equal to 0"
}

@Test
void limit_combined_with_where_and_order_by_produces_correct_clause_order() {
    // Given: whereAndEquals + orderByAsc + limit + offset
    // When: building the SQL
    // Then: clause order is SELECT … FROM … WHERE … ORDER BY … OFFSET … FETCH NEXT …
}
```

### Integration — `PostgresUserRepositoryTest`

```java
@Test
void find_with_limit_and_offset_returns_correct_page() {
    // Given: 10 rows inserted into the test table
    // When: FindQuery with orderByAsc("id").limit(3).offset(3)
    // Then: exactly 3 rows returned, corresponding to rows 4–6 by id order
}
```

---

## Documentation

- **CHANGELOG.md** — add `limit(int)` and `offset(int)` under version 2.6.0.
- **README.md** — add a Pagination section showing the fluent API and the generated SQL.

---

## Steps

**Step 1** — Add `limit` and `offset` fields and methods to `FindQuery` with validation.

**Step 2** — Update `buildSql` to append `OFFSET … ROWS` and `FETCH FIRST|NEXT … ROWS ONLY`.

**Step 3** — Write `FindQueryLimitOffsetTest` (unit tests).

**Step 4** — Write the integration test in `PostgresUserRepositoryTest`.

**Step 5** — Update CHANGELOG.md and README.md.
