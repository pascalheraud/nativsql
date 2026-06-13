# Spec: Missing operators + `whereAndOperator` + `whereAndColumnOperator` + `whereAndRange`

> Issue: [nativsql#78](https://github.com/heraud/nativsql/issues/78)
> **Status: DONE** — implemented 2026-06-13, released in `[2.5.0]`

## Goal

This issue is split into two sequential phases.

**Phase 1 — Prerequisite (refacto, no new feature)**

Extract `AbstractWhereQuery<T, ID, Self>` to deduplicate the WHERE methods shared between `FindQuery` and `DeleteQuery`. No new public methods — behaviour is identical to the current state.

**Phase 2 — New operators**

Once Phase 1 tests are green:

1. New single-parameter operators in `Operator`: `<`, `>`, `<=`, `>=`, `<>`, `LIKE`.
2. New `whereAndOperator(column, Operator, value)` — generic escape hatch for any `Operator`.
3. New enum `ColumnOperator` (`IS_NULL`, `IS_NOT_NULL`) + `whereAndColumnOperator(column, ColumnOperator)` — parameter-less conditions.
4. New enum `RangeOperator` (`BETWEEN`) + `whereAndRange(column, RangeOperator, low, high)` — two-parameter conditions.

---

## Phase 1 — `AbstractWhereQuery<T, ID, Self>`

### Architecture

```
AbstractWhereQuery<T, ID, Self>   (new — ovh.heraud.nativsql.util)
  ├── FindQuery<T, ID>             (existing — extends AbstractWhereQuery<T,ID,FindQuery<T,ID>>)
  └── DeleteQuery<T, ID>           (existing — extends AbstractWhereQuery<T,ID,DeleteQuery<T,ID>>)
```

The CRTP parameter `Self` ensures fluent calls return the correct concrete type without casting.

### Contents of `AbstractWhereQuery`

Moved from `FindQuery` and `DeleteQuery` (identical code in both classes):

```java
public abstract class AbstractWhereQuery<T extends IEntity<ID>, ID, Self extends AbstractWhereQuery<T, ID, Self>> {

    protected final WhereClause whereClause = new WhereClause();
    protected final GenericRepository<T, ID> repository;

    public Self whereAndEquals(String column, Object value) { ... }
    public Self whereAndEquals(Getter<T> getter, Object value) { ... }
    public Self whereAndIn(String column, List<?> values) { ... }
    public Self whereAndIn(Getter<T> getter, List<?> values) { ... }
    public Self whereExpression(String expression, String paramName, Object value) { ... }

    public Map<String, Object> getParameters() { ... }
    public boolean hasWhereConditions() { ... }

    @SuppressWarnings("unchecked")
    protected Self self() { return (Self) this; }
}
```

### Steps Phase 1

**Step 1** — Create `AbstractWhereQuery` in `ovh.heraud.nativsql.util`. Move the shared code from `FindQuery` and `DeleteQuery` into it.

**Step 2** — `FindQuery` extends `AbstractWhereQuery<T, ID, FindQuery<T, ID>>`; remove duplicated WHERE methods.

**Step 3** — `DeleteQuery` extends `AbstractWhereQuery<T, ID, DeleteQuery<T, ID>>`; remove duplicated WHERE methods.

**Step 4** — Compile and verify all existing tests pass unchanged. Tests must be green before starting Phase 2.

---

## Phase 2 — New operators

### 1 — `Operator` additions

Current values: `EQUALS`, `IN`.

New values to add:

| Constant           | SQL fragment produced | Notes                                   |
| ------------------ | --------------------- | --------------------------------------- |
| `LESS_THAN`        | `col < :param`        |                                         |
| `LESS_OR_EQUAL`    | `col <= :param`       |                                         |
| `GREATER_THAN`     | `col > :param`        |                                         |
| `GREATER_OR_EQUAL` | `col >= :param`       |                                         |
| `NOT_EQUALS`       | `col <> :param`       | Standard SQL                            |
| `LIKE`             | `col LIKE :param`     | Caller is responsible for `%` wildcards |

### 2 — `ColumnOperator` enum (new)

Parameter-less operators — the `ColumnWhereExpressionBuilder` only receives the column name.

```java
public enum ColumnOperator {
    IS_NULL    ((col) -> col + " IS NULL"),
    IS_NOT_NULL((col) -> col + " IS NOT NULL");
    ...
}
```

Associated interface:

```java
@FunctionalInterface
public interface ColumnWhereExpressionBuilder {
    String buildExpression(String column);
}
```

### 3 — `RangeOperator` enum (new)

Two-parameter operators — the builder receives `(col, paramLow, paramHigh)`.

```java
public enum RangeOperator {
    BETWEEN((col, low, high) -> col + " BETWEEN :" + low + " AND :" + high);
    ...
}
```

Associated interface:

```java
@FunctionalInterface
public interface RangeWhereExpressionBuilder {
    String buildExpression(String column, String paramLow, String paramHigh);
}
```

### 4 — New methods in `AbstractWhereQuery`

```java
// Operator (1 parameter) — generic
public Self whereAndOperator(String column, Operator operator, Object value)
public Self whereAndOperator(Getter<T> getter, Operator operator, Object value)

// ColumnOperator (0 parameters)
public Self whereAndColumnOperator(String column, ColumnOperator operator)
public Self whereAndColumnOperator(Getter<T> getter, ColumnOperator operator)

// RangeOperator (2 parameters)
public Self whereAndRange(String column, RangeOperator operator, Object low, Object high)
public Self whereAndRange(Getter<T> getter, RangeOperator operator, Object low, Object high)
```

`whereAndRange`: `low` and `high` must both be non-null, otherwise throws `NativSQLException`. Parameter names: `<camelCaseColumn>Low` / `<camelCaseColumn>High`.

### 5 — Encrypted field guard

Apply the same guard as on `whereAndEquals` / `whereAndIn` to all new methods: if the field has `@Type(DbDataType.ENCRYPTED)`, throw `NativSQLException` before adding the condition.

### 6 — SQL output examples

```java
query.whereAndOperator("age", Operator.GREATER_OR_EQUAL, 18);
// → WHERE age >= :age

query.whereAndColumnOperator("deleted_at", ColumnOperator.IS_NULL);
// → WHERE deleted_at IS NULL   (no parameter added)

query.whereAndColumnOperator("deleted_at", ColumnOperator.IS_NOT_NULL);
// → WHERE deleted_at IS NOT NULL   (no parameter added)

query.whereAndRange("birth_date", RangeOperator.BETWEEN,
    LocalDate.of(1980,1,1), LocalDate.of(2000,12,31));
// → WHERE birth_date BETWEEN :birthDateLow AND :birthDateHigh
// params: {birthDateLow: 1980-01-01, birthDateHigh: 2000-12-31}

query.whereAndOperator("name", Operator.LIKE, "Dup%");
// → WHERE name LIKE :name
```

### 7 — Steps Phase 2

**Step 1** — `Operator` enum: add the six new constants.

**Step 2** — Create `ColumnWhereExpressionBuilder` and `ColumnOperator` in `ovh.heraud.nativsql.util`.

**Step 3** — Create `RangeWhereExpressionBuilder` and `RangeOperator` in `ovh.heraud.nativsql.util`.

**Step 4** — Add `whereAndOperator`, `whereAndColumnOperator`, `whereAndRange` to `AbstractWhereQuery` with encrypted field guards.

**Step 5** — Documentation:
- **CHANGELOG.md** — new enums `ColumnOperator`, `RangeOperator`, new `Operator` constants, new methods `whereAndOperator`, `whereAndColumnOperator`, `whereAndRange`.
- **ARCHITECTURE.md** — document `AbstractWhereQuery`, `ColumnOperator`, `RangeOperator`, and the `FindQuery` / `DeleteQuery` hierarchy.

---

## Tests

### Phase 1 — `AbstractWhereQueryRefactoTest`

```java
@Test
void find_query_whereAndEquals_still_works_after_refacto() {
    // Given: a FindQuery after the AbstractWhereQuery refacto
    FindQuery<...> query = FindQuery.of(...);
    // When: using the existing whereAndEquals
    query.whereAndEquals("status", "ACTIVE");
    // Then: SQL and parameters are unchanged
    assertThat(query.buildString(...)).contains("status = :status");
    assertThat(query.getParameters()).containsEntry("status", "ACTIVE");
}

@Test
void delete_query_whereAndEquals_still_works_after_refacto() {
    // Given / When / Then: symmetric for DeleteQuery
}
```

### Phase 2 — `OperatorTest`

```java
@Test
void less_than_produces_expected_sql_fragment() {
    // Given: the LESS_THAN operator
    // When: building the expression
    String result = Operator.LESS_THAN.getExpressionBuilder().buildExpression("age", "age");
    // Then: the correct SQL fragment is returned
    assertThat(result).isEqualTo("age < :age");
}

// (repeat pattern for LESS_OR_EQUAL, GREATER_THAN, GREATER_OR_EQUAL, NOT_EQUALS, LIKE)
```

### Phase 2 — `ColumnOperatorTest`

```java
@Test
void is_null_produces_expected_sql_fragment() {
    // Given: the IS_NULL column operator
    // When: building the expression
    String result = ColumnOperator.IS_NULL.getExpressionBuilder().buildExpression("deleted_at");
    // Then: the correct SQL fragment is returned
    assertThat(result).isEqualTo("deleted_at IS NULL");
}

@Test
void is_not_null_produces_expected_sql_fragment() {
    // Given / When / Then: symmetric for IS_NOT_NULL
}
```

### Phase 2 — `AbstractWhereQueryTest`

Tests written against `FindQuery` (concrete subclass) but cover the behaviour of `AbstractWhereQuery`.

```java
@Test
void whereAndOperator_greater_than_adds_condition_and_parameter() {
    // Given: a FindQuery
    FindQuery<...> query = FindQuery.of(...);
    // When: adding a GREATER_THAN condition
    query.whereAndOperator("age", Operator.GREATER_THAN, 18);
    // Then: SQL contains the correct fragment and parameter is bound
    assertThat(query.buildString(...)).contains("age > :age");
    assertThat(query.getParameters()).containsEntry("age", 18);
}

@Test
void whereAndColumnOperator_is_null_adds_expression_with_no_parameter() {
    // Given: a FindQuery
    FindQuery<...> query = FindQuery.of(...);
    // When: adding an IS NULL condition
    query.whereAndColumnOperator("deleted_at", ColumnOperator.IS_NULL);
    // Then: SQL contains the fragment and no parameter is bound
    assertThat(query.buildString(...)).contains("deleted_at IS NULL");
    assertThat(query.getParameters()).doesNotContainKey("deletedAt");
}

@Test
void whereAndColumnOperator_is_not_null_adds_expression_with_no_parameter() {
    // Given / When / Then: symmetric to IS_NULL
}

@Test
void whereAndRange_between_adds_expression_and_both_parameters() {
    // Given: a FindQuery
    FindQuery<...> query = FindQuery.of(...);
    // When: adding a BETWEEN condition
    query.whereAndRange("birth_date", RangeOperator.BETWEEN,
        LocalDate.of(1980,1,1), LocalDate.of(2000,12,31));
    // Then: SQL contains the BETWEEN fragment and both parameters are bound
    assertThat(query.buildString(...)).contains("birth_date BETWEEN :birthDateLow AND :birthDateHigh");
    assertThat(query.getParameters()).containsEntry("birthDateLow", LocalDate.of(1980,1,1));
    assertThat(query.getParameters()).containsEntry("birthDateHigh", LocalDate.of(2000,12,31));
}

@Test
void whereAndRange_between_throws_when_low_is_null() {
    // Given: a FindQuery
    FindQuery<...> query = FindQuery.of(...);
    // When / Then: passing null as low throws
    assertThatThrownBy(() -> query.whereAndRange("age", RangeOperator.BETWEEN, null, 40))
        .isInstanceOf(NativSQLException.class);
}

@Test
void whereAndRange_between_throws_when_high_is_null() {
    // Given / When / Then: symmetric to low-is-null case
}
```

### Integration — one test per new capability

Each integration test (using existing DB containers) verifies that a query with the new condition returns the correct rows from a real table. Cover at minimum: `LESS_THAN`, `NOT_EQUALS`, `LIKE`, `IS_NULL`, `BETWEEN`.
