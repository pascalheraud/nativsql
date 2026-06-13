# Plan: Missing operators + `whereAndOperator` + `whereAndColumnOperator` + `whereAndRange`

> Issue: [nativsql#78](https://github.com/heraud/nativsql/issues/78)

## Steps

### Phase 1 — AbstractWhereQuery refacto

| # | Step | Status |
|---|------|--------|
| 1 | Create `AbstractWhereQuery<T, ID, Self>` in `ovh.heraud.nativsql.util` — move shared WHERE code from `FindQuery` and `DeleteQuery` | ✅ done |
| 2 | `FindQuery` extends `AbstractWhereQuery<T, ID, FindQuery<T, ID>>` — remove duplicated methods | ✅ done |
| 3 | `DeleteQuery` extends `AbstractWhereQuery<T, ID, DeleteQuery<T, ID>>` — remove duplicated methods | ✅ done |
| 4 | Unit tests: `AbstractWhereQueryRefactoTest` — regression for both query types | ✅ done |

### Phase 2 — New operators

| # | Step | Status |
|---|------|--------|
| 5 | `Operator` enum: add `LESS_THAN`, `LESS_OR_EQUAL`, `GREATER_THAN`, `GREATER_OR_EQUAL`, `NOT_EQUALS`, `LIKE` | ✅ done |
| 6 | Create `ColumnWhereExpressionBuilder` + `ColumnOperator` (`IS_NULL`, `IS_NOT_NULL`) | ✅ done |
| 7 | Create `RangeWhereExpressionBuilder` + `RangeOperator` (`BETWEEN`) | ✅ done |
| 8 | Create `CustomCondition`; `WhereClause.custom()` accumulates (list) instead of overwriting a single field | ✅ done |
| 9 | Add `whereAndOperator`, `whereAndColumnOperator`, `whereAndRange` to `AbstractWhereQuery` | ✅ done |
| 10 | Unit tests: `OperatorTest`, `ColumnOperatorTest`, `AbstractWhereQueryTest` | ✅ done |
| 11 | Integration tests in `nativsql-postgres`: `PostgresOperatorsTest` — one test per new operator against a real DB | ✅ done |
| 12 | Documentation: `CHANGELOG.md` `[2.6.0]`, `ARCHITECTURE.md`, `USERGUIDE.md` new section, `spec.md` marked done | ✅ done |

## Files created

- `nativsql-core/src/main/java/ovh/heraud/nativsql/util/AbstractWhereQuery.java`
- `nativsql-core/src/main/java/ovh/heraud/nativsql/util/ColumnWhereExpressionBuilder.java`
- `nativsql-core/src/main/java/ovh/heraud/nativsql/util/ColumnOperator.java`
- `nativsql-core/src/main/java/ovh/heraud/nativsql/util/RangeWhereExpressionBuilder.java`
- `nativsql-core/src/main/java/ovh/heraud/nativsql/util/RangeOperator.java`
- `nativsql-core/src/main/java/ovh/heraud/nativsql/util/ColumnCondition.java`
- `nativsql-core/src/main/java/ovh/heraud/nativsql/util/RangeCondition.java`
- `nativsql-core/src/main/java/ovh/heraud/nativsql/util/CustomCondition.java`
- `nativsql-core/src/test/java/ovh/heraud/nativsql/util/AbstractWhereQueryRefactoTest.java`
- `nativsql-core/src/test/java/ovh/heraud/nativsql/util/OperatorTest.java`
- `nativsql-core/src/test/java/ovh/heraud/nativsql/util/ColumnOperatorTest.java`
- `nativsql-core/src/test/java/ovh/heraud/nativsql/util/AbstractWhereQueryTest.java`
- `nativsql-postgres/src/test/java/ovh/heraud/nativsql/repository/postgres/PostgresOperatorsTest.java`

## Files modified

- `nativsql-core/src/main/java/ovh/heraud/nativsql/util/Operator.java`
- `nativsql-core/src/main/java/ovh/heraud/nativsql/util/WhereClause.java`
- `nativsql-core/src/main/java/ovh/heraud/nativsql/util/FindQuery.java`
- `nativsql-core/src/main/java/ovh/heraud/nativsql/util/DeleteQuery.java`
- `nativsql-postgres/src/test/java/ovh/heraud/nativsql/repository/postgres/PostgresUserRepository.java`
- `CHANGELOG.md`
- `doc/ARCHITECTURE.md`
- `USERGUIDE.md`

## Verification

```bash
# Unit tests
./gradlew :nativsql-core:test

# Integration tests
./gradlew :nativsql-postgres:test --tests "ovh.heraud.nativsql.repository.postgres.PostgresOperatorsTest"

# Full build
./gradlew build
```
