# Plan: WHERE conditions on joined table columns — dot-notation path

> Issue: [nativsql#83](https://github.com/heraud/nativsql/issues/83)

## Context

Allow filtering on joined table columns via dot-notation in the existing `whereAnd*` signatures:
`whereAndEquals("group.name", "Admins")` → `WHERE groups.name = :groupName`.

Exactly one dot is allowed. Resolution is deferred to SQL build time via a `JoinResolver` registered by `FindQuery.buildSql()`. No changes to condition classes. No new `whereAnd*` method overloads.

Read `spec.md` before implementing.

---

## Files to create

### `nativsql-core/.../util/JoinResolver.java`

```java
@FunctionalInterface
public interface JoinResolver {
    String resolve(String path, IdentifierConverter identifierConverter);
}
```

---

## Files to modify

### 1 — `SqlUtils.java`

Add `columnPathToParamName(String column)`. Called from three places: `WhereClause`, `AbstractWhereQuery.getParameters()`, `AbstractWhereQuery.whereAndRange()`.

```java
public static String columnPathToParamName(String column) {
    if (!column.contains(".")) return column;
    String[] parts = column.split("\\.", -1);
    if (parts.length != 2) {
        throw new NativSQLException(
            "Nested column paths are not supported: '" + column
            + "'. Only one level of join is allowed (e.g. 'assoc.column')");
    }
    String col = parts[1];
    if (col.isEmpty()) {
        throw new NativSQLException(
            "Invalid column path '" + column + "': column name cannot be empty");
    }
    return parts[0] + Character.toUpperCase(col.charAt(0)) + col.substring(1);
}
```

---

### 2 — `WhereClause.java`

**Add field:**
```java
private JoinResolver joinResolver = null;
```

**Add method:**
```java
public WhereClause withJoinResolver(JoinResolver resolver) {
    this.joinResolver = resolver;
    return this;
}
```

**Replace `toDbCol`:**

Current:
```java
private String toDbCol(IdentifierConverter identifierConverter, String column) {
    String dbCol = identifierConverter.toDB(column);
    if (hasJoins && !tablePrefix.isEmpty()) {
        dbCol = tablePrefix + "." + dbCol;
    }
    return dbCol;
}
```

New:
```java
private String toDbCol(IdentifierConverter identifierConverter, String column) {
    if (column.contains(".")) {
        if (joinResolver == null) {
            throw new NativSQLException(
                "Dot-notation column paths are not supported in this query type: '" + column + "'");
        }
        return joinResolver.resolve(column, identifierConverter);
    }
    String dbCol = identifierConverter.toDB(column);
    if (hasJoins && !tablePrefix.isEmpty()) {
        dbCol = tablePrefix + "." + dbCol;
    }
    return dbCol;
}
```

**Update `buildConditionStrings`:**

Replace every occurrence of `condition.getColumn()` used as `paramName` with `SqlUtils.columnPathToParamName(condition.getColumn())`. The `dbCol` call already goes through `toDbCol` which handles the dot — only the `paramName` variable needs the fix.

Concretely, in the `for (Condition condition : conditions)` loop:
```java
// before:
String paramName = condition.getColumn();
// after:
String paramName = SqlUtils.columnPathToParamName(condition.getColumn());
```

`ColumnCondition` and `RangeCondition` loops use `toDbCol` for the column (OK) but do not emit a param name themselves — no change needed there.

---

### 3 — `AbstractWhereQuery.java`

**`guardEncryptedColumn` — skip dot paths:**

Add at the top of the method, before the field lookup:
```java
if (column.contains(".")) {
    return; // joined entity column — encryption guard does not apply
}
```

**`whereAndRange` — use `columnPathToParamName` for camelBase:**

Current:
```java
String camelBase = repository.getIdentifierConverter().fromDB(column);
```

New:
```java
String camelBase = SqlUtils.columnPathToParamName(column);
```

Note: for a plain column `"age"`, `columnPathToParamName` returns `"age"` unchanged — same result as before. No regression.

**`getParameters()` — use `columnPathToParamName` for map keys:**

Current:
```java
for (Condition condition : whereClause.getConditions()) {
    params.put(condition.getColumn(), condition.getValue());
}
```

New:
```java
for (Condition condition : whereClause.getConditions()) {
    params.put(SqlUtils.columnPathToParamName(condition.getColumn()), condition.getValue());
}
```

`RangeCondition` already stores its own `paramLow` / `paramHigh` strings (set by `whereAndRange`), so they are already derived correctly after the `whereAndRange` fix above — no change needed in `getParameters()` for range conditions.

---

### 4 — `FindQuery.java`

**Add private helper `resolveJoinColumn`:**

```java
private String resolveJoinColumn(String path, IdentifierConverter converter) {
    String[] segments = path.split("\\.", 2);
    String associationName = segments[0];
    String column = segments[1];
    Join join = joins.stream()
            .filter(j -> j.getName().equals(associationName))
            .findFirst()
            .orElseThrow(() -> new NativSQLException(
                    "No join found for association '" + associationName
                    + "' in dot-notation column '" + path + "'. "
                    + "Add leftJoin/innerJoin before using this column in a WHERE condition."));
    return join.getRepository().getTableName() + "." + converter.toDB(column);
}
```

**Register the resolver in `buildSql`:**

In `buildSql`, just before the `if (hasWhereConditions())` block:
```java
whereClause.withJoinResolver(this::resolveJoinColumn);
```

The resolver is safe to register even when there are no dot-path conditions — it will simply never be called.

---

## Tests

### Unit — `SqlUtilsColumnPathTest`

File: `nativsql-core/src/test/java/ovh/heraud/nativsql/util/SqlUtilsColumnPathTest.java`

```java
@Test
void plain_column_is_returned_unchanged() {
    assertThat(SqlUtils.columnPathToParamName("status")).isEqualTo("status");
}

@Test
void one_dot_produces_camelCase_param_name() {
    assertThat(SqlUtils.columnPathToParamName("group.name")).isEqualTo("groupName");
}

@Test
void one_dot_with_multi_char_column_capitalises_first_letter_only() {
    assertThat(SqlUtils.columnPathToParamName("group.active")).isEqualTo("groupActive");
}

@Test
void two_dots_throws_NativSQLException() {
    assertThatThrownBy(() -> SqlUtils.columnPathToParamName("a.b.value"))
            .isInstanceOf(NativSQLException.class)
            .hasMessageContaining("Nested column paths are not supported");
}

@Test
void empty_column_segment_throws_NativSQLException() {
    assertThatThrownBy(() -> SqlUtils.columnPathToParamName("group."))
            .isInstanceOf(NativSQLException.class)
            .hasMessageContaining("column name cannot be empty");
}
```

---

### Unit — `FindQueryDotNotationTest`

File: `nativsql-core/src/test/java/ovh/heraud/nativsql/util/FindQueryDotNotationTest.java`

Follows the same setup pattern as `FindQueryTest` (mock repository, `SnakeCaseIdentifierConverter`). Also needs a mock join repository with a known table name (e.g. `"groups"`).

```java
@Test
void whereAndEquals_with_dot_path_uses_joined_table_prefix() {
    // Given: query with a leftJoin on "group" (table name "groups")
    // When: whereAndEquals("group.name", "Admins")
    // Then: SQL contains "groups.name = :groupName"
    //       getParameters() contains {"groupName": "Admins"}
}

@Test
void whereAndEquals_with_dot_path_throws_when_association_not_joined() {
    // Given: query with no joins
    // When: whereAndEquals("group.name", "Admins")
    // Then: NativSQLException mentioning "group"
}

@Test
void plain_column_still_uses_main_table_prefix_when_joins_present() {
    // Given: query with a leftJoin
    // When: whereAndEquals("status", "ACTIVE")
    // Then: SQL contains "test_entity.status = :status"
}

@Test
void whereAndEquals_with_dot_path_and_plain_column_produce_correct_prefixes() {
    // Given: query with leftJoin("group")
    // When: whereAndEquals("status", "ACTIVE") + whereAndEquals("group.name", "Admins")
    // Then: SQL contains both "test_entity.status = :status" and "groups.name = :groupName"
    //       getParameters() has both entries
}

@Test
void whereAndColumnOperator_is_null_with_dot_path_adds_no_parameter() {
    // Given: query with leftJoin("group")
    // When: whereAndColumnOperator("group.deletedAt", ColumnOperator.IS_NULL)
    // Then: SQL contains "groups.deleted_at IS NULL", getParameters() has no entry for it
}

@Test
void whereAndRange_with_dot_path_derives_param_names_correctly() {
    // Given: query with innerJoin("profile")
    // When: whereAndRange("profile.age", RangeOperator.BETWEEN, 18, 65)
    // Then: SQL contains "profiles.age BETWEEN :profileAgeLow AND :profileAgeHigh"
    //       getParameters() contains {"profileAgeLow": 18, "profileAgeHigh": 65}
}

@Test
void whereAndIn_with_dot_path_uses_joined_table_prefix() {
    // Given: query with leftJoin("group")
    // When: whereAndIn("group.status", List.of("ACTIVE", "PENDING"))
    // Then: SQL contains "groups.status IN (:groupStatus)"
}

@Test
void whereAndOperator_like_with_dot_path_uses_joined_table_prefix() {
    // Given: query with leftJoin("group")
    // When: whereAndOperator("group.name", Operator.LIKE, "Admin%")
    // Then: SQL contains "groups.name LIKE :groupName"
}
```

---

### Integration — `PostgresWhereJoinedTest`

File: `nativsql-postgres/src/test/java/ovh/heraud/nativsql/repository/postgres/PostgresWhereJoinedTest.java`

Uses the existing `PostgresUserRepository` and `Group` entity. Verify with the existing `@MappedBy` join between `User.group` and `Group`.

```java
@Import({ PostgresUserRepository.class })
class PostgresWhereJoinedTest extends PostgresRepositoryTest {

    @Autowired
    private PostgresUserRepository userRepository;
```

| Test | Setup | Query | Expected |
|---|---|---|---|
| `whereAndEquals` dot path — matching rows | insert group "Admins", 2 users in it, 1 other | `whereAndEquals("group.name","Admins")` | 2 users returned |
| `whereAndEquals` dot path — no match | insert group "Users" | `whereAndEquals("group.name","Admins")` | empty list |
| mixed main + joined condition | users A and B in group "Admins", A is ACTIVE, B is INACTIVE | `whereAndEquals("status",ACTIVE).whereAndEquals("group.name","Admins")` | only A |
| `whereAndColumnOperator` IS NULL on joined column | 1 user with group, 1 without | `whereAndColumnOperator("group.name", IS_NULL)` | only the one without group |
| `whereAndOperator` LIKE on joined column | groups "Admins" and "Users" | `whereAndOperator("group.name", LIKE, "Admin%")` | only users in "Admins" |

---

## Documentation

- **CHANGELOG.md** — add entry: dot-notation column paths in `whereAnd*` methods of `FindQuery` to filter on joined table columns.
- **ARCHITECTURE.md** — document `JoinResolver`, `SqlUtils.columnPathToParamName`, and the deferred-resolution pattern in `WhereClause`.
- **USERGUIDE.md** — add "Filtering on joined table columns" subsection under the JOIN section with dot-notation examples.

---

## Verification

```bash
./gradlew :nativsql-core:test        # unit tests — SqlUtilsColumnPathTest, FindQueryDotNotationTest
./gradlew :nativsql-postgres:test    # integration — PostgresWhereJoinedTest + full regression
./gradlew build                      # full build, all modules
```

Existing tests must not regress. In particular, `FindQueryTest`, `FindQueryCryptTest`, and `PostgresOperatorsTest` must remain green without modification.
