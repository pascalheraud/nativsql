# Spec: WHERE conditions on joined table columns — dot-notation path

> Issue: [nativsql#83](https://github.com/heraud/nativsql/issues/83)

## Goal

Allow callers to filter on columns of joined tables using a dot-notation path in the existing `whereAnd*` signatures:

```java
query.leftJoin("group", "id", "name")
     .whereAndEquals("group.name", "Admins");
// → WHERE groups.name = :groupName
```

No new method names. The same `whereAndEquals`, `whereAndOperator`, `whereAndColumnOperator`, `whereAndRange`, `whereAndIn` accept either a plain column (`"status"`) or a dot path (`"group.name"`).

---

## Column path format

```
"column"         → main table column  (current behaviour, unchanged)
"assoc.column"   → join one level deep (only supported form)
```

Exactly one dot is allowed. Nested paths (`"a.b.column"`) are rejected with `NativSQLException`. The segment before the dot is the association name; the segment after is the column name on the joined entity.

---

## Parameter name derivation

Named parameters in Spring JDBC cannot contain dots. The parameter name is derived by joining all path segments in camelCase:

| Column path      | SQL column (example)   | Parameter name |
|------------------|------------------------|----------------|
| `"status"`       | `users.status`         | `status`       |
| `"group.name"`   | `groups.name`          | `groupName`    |
| `"group.active"` | `groups.is_active`     | `groupActive`  |

Rule: `assocName + capitalize(columnName)` — two segments only.

A shared utility method `columnPathToParamName(String column)` returns:
- the column unchanged if it contains no dot
- the camelCase-joined segments otherwise

This method is called in **two places** that must stay consistent:
1. `WhereClause.buildConditionStrings()` — to produce the `:paramName` in the SQL string
2. `AbstractWhereQuery.getParameters()` — to produce the map key

---

## Architecture: where resolution happens

The `whereAnd*` methods on `AbstractWhereQuery` are **not overridden** in `FindQuery`. They store the raw column path as-is in `WhereClause` (e.g. `"group.name"`) without any special handling.

The resolution is deferred to SQL build time. `FindQuery.buildSql()` registers a **`JoinResolver`** on the `WhereClause` just before rendering:

```java
whereClause.withJoinResolver(this::resolveJoinColumn);
```

`WhereClause.toDbCol(column, identifierConverter)` calls the resolver only when the column contains a dot:
- **No dot** → current logic unchanged (optional global table prefix when `hasJoins`)
- **Has dot** → delegate to resolver → returns the fully qualified DB column string (e.g. `"groups.name"`)

`DeleteQuery` never calls `withJoinResolver`. If a dot path somehow reaches `WhereClause.toDbCol()` without a resolver set, `WhereClause` throws `NativSQLException`. The `whereAnd*` methods themselves remain identical in `AbstractWhereQuery` for both `FindQuery` and `DeleteQuery`.

### `JoinResolver` functional interface

```java
@FunctionalInterface
public interface JoinResolver {
    /**
     * Resolves a dot-notation column path to a fully qualified DB column string.
     * e.g. "group.name" → "groups.name"  (using the identifier converter)
     *
     * @param path                the dot-notation path (at least one dot)
     * @param identifierConverter the active identifier converter
     * @return the fully qualified SQL column expression (table.col)
     * @throws NativSQLException if any segment of the path cannot be resolved
     */
    String resolve(String path, IdentifierConverter identifierConverter);
}
```

### Resolution logic in `FindQuery`

```java
private String resolveJoinColumn(String path, IdentifierConverter converter) {
    // columnPathToParamName already validated exactly one dot
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

---

## Encrypted-column guard

`AbstractWhereQuery.guardEncryptedColumn(String column)` currently looks up the column in `repository.getEntityFields()`. When the column contains a dot, the field belongs to a joined entity — skip the guard entirely:

```java
private void guardEncryptedColumn(String column) {
    if (column.contains(".")) {
        return; // joined table column — guard does not apply
    }
    // existing logic...
}
```

---

## Range conditions: parameter names

`whereAndRange` currently derives param names as `camelBase + "Low"` / `camelBase + "High"`:

```java
String camelBase = repository.getIdentifierConverter().fromDB(column);
whereClause.addRangeOperator(column, operator, camelBase + "Low", low, camelBase + "High", high);
```

For a dot path `"group.age"`, `camelBase` must be `columnPathToParamName("group.age")` = `"groupAge"`, so the params become `groupAgeLow` / `groupAgeHigh`. Replace the `fromDB(column)` call with `columnPathToParamName(column)`:

```java
String camelBase = columnPathToParamName(column);
```

---

## SQL output examples

```java
// Simple join filter
query.leftJoin("group", "id", "name")
     .whereAndEquals("group.name", "Admins");
// → WHERE
//       groups.name = :groupName

// Mixed: main table + joined table
query.leftJoin("group", "id", "name")
     .whereAndEquals("status", "ACTIVE")
     .whereAndEquals("group.name", "Admins");
// → WHERE
//       users.status = :status
//   AND
//       groups.name = :groupName

// Joined IS NULL
query.leftJoin("group", "id")
     .whereAndColumnOperator("group.deletedAt", ColumnOperator.IS_NULL);
// → WHERE
//       groups.deleted_at IS NULL
// (no parameter added)

// Joined BETWEEN
query.innerJoin("profile", "age")
     .whereAndRange("profile.age", RangeOperator.BETWEEN, 18, 65);
// → WHERE
//       profiles.age BETWEEN :profileAgeLow AND :profileAgeHigh

// Joined LIKE
query.leftJoin("group", "name")
     .whereAndOperator("group.name", Operator.LIKE, "Admin%");
// → WHERE
//       groups.name LIKE :groupName
```

---

## Error handling

| Situation | Behaviour |
|---|---|
| Dot path used with no `JoinResolver` set (e.g. in `DeleteQuery`) | `NativSQLException`: "Dot-notation column paths are not supported in this query type" |
| More than one dot (e.g. `"a.b.col"`) | `NativSQLException`: "Nested column paths are not supported: '<path>'. Only one level of join is allowed (e.g. 'assoc.column')" |
| Association segment not found in `joins` | `NativSQLException`: "No join found for association '<name>' in dot-notation column '<path>'…" |
| Column segment is empty (e.g. `"group."`) | `NativSQLException`: "Invalid column path '<path>': column name cannot be empty" |

---

## Implementation steps

### Step 1 — `columnPathToParamName` utility

File: `ReflectionUtils.java` (or a new `SqlUtils` method)

```java
public static String columnPathToParamName(String column) {
    if (!column.contains(".")) return column;
    String[] parts = column.split("\\.", -1);
    if (parts.length != 2) {
        throw new NativSQLException(
            "Nested column paths are not supported: '" + column
            + "'. Only one level of join is allowed (e.g. 'assoc.column')");
    }
    String assoc = parts[0];
    String col = parts[1];
    if (col.isEmpty()) {
        throw new NativSQLException("Invalid column path '" + column + "': column name cannot be empty");
    }
    return assoc + Character.toUpperCase(col.charAt(0)) + col.substring(1);
}
```

Used in:
- `WhereClause.buildConditionStrings()` for the `:paramName` in SQL
- `AbstractWhereQuery.getParameters()` for the map key
- `AbstractWhereQuery.whereAndRange()` for the `camelBase` derivation

### Step 2 — `JoinResolver` interface

New file: `ovh.heraud.nativsql.util.JoinResolver`

Functional interface as defined above.

### Step 3 — `WhereClause`: resolver support

File: `WhereClause.java`

- Add `private JoinResolver joinResolver = null` field.
- Add `withJoinResolver(JoinResolver resolver)` method (returns `this`).
- Update `toDbCol`:
  - If column has no dot: existing logic unchanged.
  - If column has a dot: call `joinResolver.resolve(column, identifierConverter)`, throw if resolver is null.
- Update `buildConditionStrings` to use `columnPathToParamName(condition.getColumn())` as `paramName` instead of `condition.getColumn()` directly.

### Step 4 — `AbstractWhereQuery`: guard + range fix

File: `AbstractWhereQuery.java`

- `guardEncryptedColumn`: early return when `column.contains(".")`.
- `whereAndRange`: use `columnPathToParamName(column)` instead of `repository.getIdentifierConverter().fromDB(column)` for the `camelBase`.
- `getParameters()`: use `columnPathToParamName(condition.getColumn())` as map key instead of `condition.getColumn()`.

### Step 5 — `FindQuery`: register resolver + implement `resolveJoinColumn`

File: `FindQuery.java`

- Add private `resolveJoinColumn(String path, IdentifierConverter converter)` as described above.
- In `buildSql()`, before calling `whereClause.buildFormatted(...)`, register the resolver:
  ```java
  whereClause.withJoinResolver(this::resolveJoinColumn);
  ```

### Step 6 — Tests

#### Unit — `ColumnPathParamNameTest`

```java
@Test void plain_column_unchanged() { assertThat(columnPathToParamName("status")).isEqualTo("status"); }
@Test void one_level_becomes_camelCase() { assertThat(columnPathToParamName("group.name")).isEqualTo("groupName"); }
@Test void two_levels_throws() {
    assertThatThrownBy(() -> columnPathToParamName("a.b.value"))
        .isInstanceOf(NativSQLException.class)
        .hasMessageContaining("Nested column paths are not supported");
}
@Test void empty_column_segment_throws() {
    assertThatThrownBy(() -> columnPathToParamName("group."))
        .isInstanceOf(NativSQLException.class)
        .hasMessageContaining("column name cannot be empty");
}
```

#### Unit — `FindQueryDotNotationTest`

```java
@Test
void whereAndEquals_with_dot_path_uses_joined_table_prefix() {
    // Given: query with leftJoin("group")
    // When: whereAndEquals("group.name", "Admins")
    // Then: SQL contains "groups.name = :groupName"
    //       getParameters() contains {"groupName": "Admins"}
}

@Test
void whereAndEquals_with_dot_path_throws_when_association_not_joined() {
    // Given: query with no joins
    // When: whereAndEquals("group.name", "Admins")
    // Then: NativSQLException mentioning 'group'
}

@Test
void plain_column_still_uses_main_table_prefix_when_joins_present() {
    // Given: query with leftJoin("group")
    // When: whereAndEquals("status", "ACTIVE")
    // Then: SQL contains "users.status = :status"
}

@Test
void whereAndRange_with_dot_path_derives_param_names_correctly() {
    // When: whereAndRange("profile.age", BETWEEN, 18, 65)
    // Then: SQL contains "profiles.age BETWEEN :profileAgeLow AND :profileAgeHigh"
    //       getParameters() contains {"profileAgeLow": 18, "profileAgeHigh": 65}
}

@Test
void whereAndColumnOperator_is_null_with_dot_path_adds_no_parameter() {
    // When: whereAndColumnOperator("group.deletedAt", IS_NULL)
    // Then: SQL contains "groups.deleted_at IS NULL", no parameter added
}
```

#### Integration — `PostgresWhereJoinedDotNotationTest`

| Test | Expected |
|---|---|
| `whereAndEquals` dot path — matching rows | only rows where joined column matches |
| `whereAndEquals` dot path — no match | empty result |
| mixed main-table + dot-path conditions | intersection of both filters |
| `whereAndColumnOperator` IS NULL on joined column | rows where joined column is null |
| `whereAndRange` BETWEEN on joined column | rows within range |

### Step 7 — Documentation

- **CHANGELOG.md** — dot-notation column paths in `whereAnd*` methods.
- **ARCHITECTURE.md** — document `JoinResolver`, `columnPathToParamName`, and how `WhereClause` resolves paths at build time.
- **USERGUIDE.md** — add a "Filtering on joined table columns" subsection showing dot-notation examples.
