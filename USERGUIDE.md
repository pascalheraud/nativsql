# NativSQL User Guide

## Table of Contents

1. [Installation](#installation)
2. [Configuration](#configuration)
3. [Domain classes](#domain-classes)
4. [Repository basics](#repository-basics)
5. [CRUD operations](#crud-operations)
6. [Querying with FindQuery](#querying-with-findquery)
7. [WHERE operators reference](#where-operators-reference)
8. [Relationships](#relationships)
9. [Type mapping reference](#type-mapping-reference)
10. [Encryption](#encryption)
11. [Multiple databases](#multiple-databases)
12. [Logging](#logging)
13. [Testing](#testing)
14. [FAQ](#faq)

---

## Installation

Choose the module for your database:

**Gradle:**

```gradle
// MySQL
implementation 'ovh.heraud:nativsql-mysql:2.0.0'

// MariaDB
implementation 'ovh.heraud:nativsql-mariadb:2.0.0'

// PostgreSQL
implementation 'ovh.heraud:nativsql-postgres:2.0.0'

// Oracle
implementation 'ovh.heraud:nativsql-oracle:2.0.0'

// Always needed
implementation 'org.springframework.boot:spring-boot-starter-jdbc'
implementation 'com.fasterxml.jackson.core:jackson-databind'
implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'
```

**Maven:**

```xml
<dependency>
    <groupId>ovh.heraud</groupId>
    <artifactId>nativsql-postgres</artifactId>
    <version>2.0.0</version>
</dependency>
```

### Module structure

| Module | Contents |
|--------|----------|
| `nativsql-core` | Framework core, type system, generic dialect — no DB-specific code |
| `nativsql-mysql` | MySQL dialect (includes `nativsql-mysql-commons`) |
| `nativsql-mysql-commons` | Shared MySQL/MariaDB dialect |
| `nativsql-mariadb` | MariaDB dialect |
| `nativsql-postgres` | PostgreSQL dialect, PostGIS support |
| `nativsql-oracle` | Oracle dialect |
| `nativsql-test-commons` | Shared test infrastructure |

---

## Configuration

```java
@Configuration
public class NativSqlConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
```

`application.properties`:

```properties
# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/mydb
spring.datasource.username=postgres
spring.datasource.password=secret

# MySQL
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=secret
```

---

## Domain classes

NativSQL works with plain POJOs — no annotations required on domain classes. Field names map to columns by camelCase ↔ snake_case convention (`firstName` → `first_name`).

```java
public class User implements IEntity<Long> {
    private Long id;
    private String firstName;
    private String email;
    private UserStatus status;  // enum
    private Address address;    // JSON column
    private LocalDateTime createdAt;

    // getters and setters
}
```

Lombok works fine:

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class User implements IEntity<Long> { ... }
```

---

## Repository basics

Extend `GenericRepository<T, ID>`:

```java
@Repository
public class UserRepository extends GenericRepository<User, Long> {

    @Override
    protected String getTableName() { return "users"; }

    @Override
    protected Class<User> getEntityClass() { return User.class; }
}
```

For PostgreSQL-specific features (composite types, JSONB, PostGIS), extend `PostgresRepository` instead:

```java
@Repository
public class UserRepository extends PostgresRepository<User, Long> { ... }
```

---

## CRUD operations

### Insert

The property list is **mandatory** — passing no properties throws `NativSQLException`.

```java
// Explicit field list (null fields are included as NULL)
userRepository.insert(user, "firstName", "email", "status");
```

### Update

```java
// All non-null fields, WHERE on id
userRepository.update(user, "id");

// Explicit fields to update, WHERE on id
userRepository.update(user, "id", "firstName", "email");

// Multiple WHERE columns
userRepository.update(user, new String[]{"tenantId", "id"}, "firstName", "email");
```

### Delete

```java
// Delete by primary key
userRepository.deleteById(userId);

// Delete exactly 1 tuple by property — throws NativSQLException if 0 or more than 1 row deleted
userRepository.deleteByProperty("email", "john@example.com");
userRepository.deleteByProperty(User::getEmail, "john@example.com");

// Delete exactly 1 tuple via DeleteQuery (multiple conditions)
userRepository.delete(newDeleteQuery()
    .whereAndEquals(User::getTenantId, tenantId)
    .whereAndEquals(User::getEmail, email));

// Delete N tuples by property (no row count validation)
userRepository.deleteAllByProperty("status", UserStatus.INACTIVE);
userRepository.deleteAllByProperty(User::getStatus, UserStatus.INACTIVE);

// Delete N tuples via DeleteQuery
userRepository.deleteAll(newDeleteQuery()
    .whereAndEquals(User::getTenantId, tenantId)
    .whereAndIn(User::getStatus, List.of(UserStatus.INACTIVE, UserStatus.SUSPENDED)));
```

### Find

```java
// By a single property
User user = userRepository.findByProperty("email", email, "id", "firstName", "email");

// Multiple values (IN clause)
List<User> users = userRepository.findAllByProperty("status",
    List.of(UserStatus.ACTIVE, UserStatus.SUSPENDED), "id", "firstName");

// By ID(s) — the id is always set on the returned entity/entities,
// even if "id" is omitted from the requested columns
User user2 = userRepository.findById(1L, "firstName");
List<User> users = userRepository.findAllByIds(List.of(1L, 2L, 3L), "firstName");
```

### Count

```java
// Count every row in the table
long total = userRepository.countAll();

// Count rows by property
long activeCount = userRepository.countByProperty("status", UserStatus.ACTIVE);
long activeCount2 = userRepository.countByProperty(User::getStatus, UserStatus.ACTIVE);
```

**The property list is mandatory for all `find*`, `insert`, and `update` methods** — passing an empty list throws `NativSQLException`. A `null` field value included in the list is written as `NULL`; a null value in a condition generates `IS NULL`.

> **Best practice — keep column lists in the caller, not in the repository.** Repository methods that hard-code a column list decide for every caller which fields are loaded, which can force unnecessary columns to be fetched or require duplicating the method for different use cases. Prefer passing the column list as a parameter so callers can select only what they need:
>
> ```java
> // Recommended: caller decides which columns to load
> public User findByEmail(String email, String... columns) {
>     return findByProperty("email", email, columns);
> }
>
> // Called with only the columns needed
> User user = userRepository.findByEmail("john@example.com", "id", "firstName");
> ```
>
> Hard-coding column lists inside the repository is not forbidden — it is reasonable for queries where the column set is tightly coupled to the query's purpose (e.g. a statistics projection or a join with a fixed shape).
>
> When a method needs several column lists — the repository's own entity plus one or more joined/associated entities — the **last parameter is always the `String... columns` varargs for the repository's own entity**; every other column list (joined/associated entity) is an earlier `String[]` parameter (only the trailing, entity-owning list is a vararg):
>
> ```java
> public User findByIdWithGroup(Long userId, String[] groupColumns, String... columns) {
>     return find(newFindQuery()
>         .select(columns)
>         .whereAndEquals("id", userId)
>         .leftJoin("group", List.of(groupColumns)));
> }
> ```

### Exists

```java
// Test whether the table has any row at all
boolean anyUser = userRepository.existsAny();

// Test existence by property
boolean hasActive = userRepository.existsByProperty("status", UserStatus.ACTIVE);
boolean hasActive2 = userRepository.existsByProperty(User::getStatus, UserStatus.ACTIVE);
```

`exists()` uses a real SQL `EXISTS` (`SELECT EXISTS(SELECT 1 FROM ... WHERE ...)`), not `count(...) > 0` — it short-circuits on the first matching row instead of counting every match, which matters on large tables. For a custom set of conditions, build an `ExistsQuery` inside the repository (like `FindQuery`/`CountQuery`, `ExistsQuery` must not be exposed publicly — add a named wrapper method):

```java
public boolean hasValidatedUser() {
    return exists(newExistsQuery()
        .whereAndEquals("validated", true));
}
```

---

## Querying with FindQuery

`FindQuery` is a type-safe builder for SELECT queries. Like `ExistsQuery`/`DeleteQuery`/`CountQuery`,
it must never be exposed publicly — `newFindQuery()`, `find(...)`, and `findAll(...)` are `protected`
and only usable from inside the repository subclass. Build and execute the query behind a named
wrapper method; the caller only ever sees the method name and its result, never the builder. The
column list, however, stays a caller-supplied parameter of that method (see the best practice
above) — it's the query shape (WHERE/JOIN/associations/ordering) that must be encapsulated, not the
columns:

```java
// Simple query with conditions
public List<User> findActiveUsersInGroup(Long groupId, String... columns) {
    return findAll(newFindQuery()
        .select(columns)
        .whereAndEquals("status", UserStatus.ACTIVE)
        .whereAndEquals("groupId", groupId)
        .orderBy("firstName", "ASC"));
}

// IN clause
public List<User> findUsersByStatuses(List<UserStatus> statuses, String... columns) {
    return findAll(newFindQuery()
        .select(columns)
        .whereAndIn("status", statuses));
}
```

```java
// Call sites never build the query themselves — they just pick the columns they need
List<User> users = userRepository.findActiveUsersInGroup(groupId, "id", "firstName", "email");
List<User> suspended = userRepository.findUsersByStatuses(
    List.of(UserStatus.ACTIVE, UserStatus.SUSPENDED), "id", "firstName");
```

### JOIN (many-to-one)

The joined entity's columns are a separate `String[]` parameter, with the repository's own
`String... columns` trailing last (see the multi-column-list ordering rule above):

```java
public User findByIdWithGroup(Long userId, String[] groupColumns, String... columns) {
    return find(newFindQuery()
        .select(columns)
        .whereAndEquals("id", userId)
        .leftJoin("group", List.of(groupColumns)));  // maps to user.group.<col> for each groupColumns entry
}
```

Requires `@MappedBy` on the domain class — see [Relationships](#relationships).

`leftJoin`/`innerJoin` also accept a fully-typed form: an association getter (pins the joined entity type `R`) plus target-entity getters, so a getter from an unrelated entity does not compile:

```java
.leftJoin(User::getGroup, Group::getId, Group::getName, Group::getCreationDate)
```

### Filtering on joined table columns

Use dot-notation in any `whereAnd*` method to filter on a column of the joined entity. The segment before the dot is the association name (matching the `leftJoin`/`innerJoin` call); the segment after is the Java field name on the joined entity.

```java
// Filter on joined column — simple equality
public List<User> findByGroupName(String groupName, String... columns) {
    return findAll(newFindQuery()
        .select(columns)
        .leftJoin("group", "name")
        .whereAndEquals("group.name", groupName));  // WHERE user_group.name = :groupName
}

// Mix main-table and joined-table conditions
public List<User> findActiveByGroupName(String groupName, String... columns) {
    return findAll(newFindQuery()
        .select(columns)
        .leftJoin("group", "name")
        .whereAndEquals("status", UserStatus.ACTIVE)       // WHERE users.status = :status
        .whereAndEquals("group.name", groupName));         //   AND user_group.name = :groupName
}

// Other operators work too, inside a repository method
query.whereAndOperator("group.name", Operator.LIKE, "Admin%")         // user_group.name LIKE :groupName
query.whereAndColumnOperator("group.deletedAt", ColumnOperator.IS_NULL) // user_group.deleted_at IS NULL
query.whereAndIn("group.status", List.of("ACTIVE", "PENDING"))        // user_group.status IN (:groupStatus)
query.whereAndRange("group.age", RangeOperator.BETWEEN, 18, 65)       // user_group.age BETWEEN :groupAgeLow AND :groupAgeHigh
```

Each `whereAnd*` method also accepts a fully-typed form — an association getter plus a target-entity getter — instead of the dot-path string:

```java
query.leftJoin(User::getGroup, Group::getId, Group::getName)
     .whereAndEquals(User::getGroup, Group::getName, "Admins");  // WHERE user_group.name = :groupName
```

**Rules:**
- Exactly one dot is allowed — `"a.b.col"` throws `NativSQLException`.
- The association name must match a `leftJoin`/`innerJoin` call earlier in the chain.
- Column names are camelCase Java field names; `identifierConverter` converts them to snake_case automatically.
- Parameter names are derived by joining the two segments in camelCase: `"group.name"` → `:groupName`, `"group.deletedAt"` → `:groupDeletedAt`.

### Ordering on joined table columns

`orderByAsc`/`orderByDesc` accept an explicit association reference to target a joined entity's column — disambiguating same-named properties across entities (e.g. `creationDate` on both `User` and `Group`). Two equivalent forms, both requiring the association to be joined earlier in the chain:

```java
// Fully typed: association getter + target-entity getter (R inferred from the first)
query.leftJoin(User::getGroup, Group::getId, Group::getName, Group::getCreationDate)
     .orderByAsc(User::getGroup, Group::getCreationDate);
// → ORDER BY user_groups.creation_date ASC

// String join name + string column
query.leftJoin("group", "id", "name", "creationDate")
     .orderByAsc("group", "creationDate");
// → ORDER BY user_groups.creation_date ASC
```

The existing `orderByAsc(String column)` overload also accepts a dot path directly (e.g. `"group.creationDate"`), equivalent to the two-argument string form. `orderByAsc(Getter<T>)` always targets the root entity. An unregistered association name/getter throws `NativSQLException`.

### Association loading (one-to-many)

```java
public User findByIdWithContacts(Long userId, String[] contactColumns, String... columns) {
    return find(newFindQuery()
        .select(columns)
        .whereAndEquals("id", userId)
        .associate("contacts", List.of(contactColumns)));
}
```

Executes 2 queries (1 for user + 1 batch for contacts) — no N+1 problem. Requires `@OneToMany` on the domain class.

### Report classes (entity + computed fields)

`selectExpression(alias, sql[, params])` adds a raw SQL expression (optionally a subquery, optionally parameterized) as a SELECT column. Combined with `find(query, resultClass)` / `findAll(query, resultClass)`, this lets a query built against an entity's repository be mapped into a "report" class that **extends the entity**, inheriting all of its fields plus extra computed ones — instead of being restricted to mapping rows back into exactly the entity type.

```java
public class UserActivityReport extends User {
    private Long contactCount;
    // getter/setter
}
```

```java
@Repository
public class UserRepository extends GenericRepository<User, Long> {

    public List<UserActivityReport> findUserActivityReports(String... columns) {
        FindQuery<User, Long> query = newFindQuery()
                .select(columns)
                .selectExpression("contactCount",
                        "(SELECT COUNT(*) FROM contact_info c WHERE c.user_id = {{table}}.id)");
        return findAll(query, UserActivityReport.class);
    }
}
```

```java
List<UserActivityReport> reports = userRepository.findUserActivityReports("id", "firstName", "email");
```

```sql
SELECT
    users.id AS "id",
    users.first_name AS "firstName",
    users.email AS "email",
    (SELECT COUNT(*) FROM contact_info c WHERE c.user_id = users.id) AS "contactCount"
FROM users
```

- The literal token `{{table}}`, if present in the SQL expression, is substituted with the query's own table name — the same value already used to prefix every other column — so a correlated subquery can reference the outer row without hardcoding the table name.
- A `Getter<R>` overload derives the alias from a method reference (e.g. `.selectExpression(UserActivityReport::getContactCount, "...")`), catching a typo'd field name at compile time.
- An optional `Map<String, Object>` of named parameters merges into the query's parameter map; a name colliding with a WHERE parameter or another expression throws `NativSQLException`.
- `find`/`findAll(query, resultClass)` are `protected` overloads of `find(query)`/`findAll(query)`, bounded by `<R extends T>` — checked by the compiler, no runtime reflection. `find(query, resultClass)` still batch-loads `associate(...)` associations; `findAll(query, resultClass)` does not, for the same N+1-avoidance reason as plain `findAll(query)`.
- `selectExpression` can also be used **alone**, with an alias matching a field the entity already has, to override that field's computed value on a plain `find(query)`/`findAll(query)` (no `resultClass` needed) — e.g. masking a value or computing an "effective" version of a field while keeping the same field name.
- Calling `select(...)` and `selectExpression(...)` with the same alias/column name throws `NativSQLException` — this only applies when *both* target the same name; using `selectExpression` alone to override an inherited field (previous bullet) is unaffected.

See [doc/issues/98-entity-composition/spec.md](doc/issues/98-entity-composition/spec.md) for the full design rationale.

---

## WHERE operators reference

All WHERE methods are available on both `FindQuery` and `DeleteQuery`.

### Equality and membership

```java
query.whereAndEquals("status", UserStatus.ACTIVE)          // status = :status
query.whereAndEquals(User::getStatus, UserStatus.ACTIVE)   // same, type-safe

query.whereAndIn("status", List.of(ACTIVE, SUSPENDED))     // status IN (:status)
query.whereAndIn(User::getStatus, List.of(ACTIVE, SUSPENDED))
```

### Comparison operators

Use `whereAndOperator` with any `Operator` constant for single-value comparisons:

```java
query.whereAndOperator("age", Operator.GREATER_OR_EQUAL, 18)   // age >= :age
query.whereAndOperator("age", Operator.LESS_THAN, 65)          // age < :age
query.whereAndOperator("score", Operator.NOT_EQUALS, 0)        // score <> :score
query.whereAndOperator("name", Operator.LIKE, "Dup%")          // name LIKE :name
```

Available `Operator` constants: `EQUALS`, `IN`, `LESS_THAN` (`<`), `LESS_OR_EQUAL` (`<=`), `GREATER_THAN` (`>`), `GREATER_OR_EQUAL` (`>=`), `NOT_EQUALS` (`<>`), `LIKE`.

> The caller is responsible for adding `%` wildcards when using `LIKE`.

### NULL checks

```java
query.whereAndColumnOperator("deletedAt", ColumnOperator.IS_NULL)      // deleted_at IS NULL
query.whereAndColumnOperator("deletedAt", ColumnOperator.IS_NOT_NULL)  // deleted_at IS NOT NULL
query.whereAndColumnOperator(User::getDeletedAt, ColumnOperator.IS_NULL)
```

No parameter is bound — `getParameters()` contains no entry for these conditions.

### Range (BETWEEN)

```java
query.whereAndRange("birthDate", RangeOperator.BETWEEN,
    LocalDate.of(1980, 1, 1), LocalDate.of(2000, 12, 31));
// → birth_date BETWEEN :birthDateLow AND :birthDateHigh
```

Both bounds are required — passing `null` throws `NativSQLException`. Parameter names are derived from the camelCase column name with `Low` / `High` suffixes.

### Custom SQL expressions

`whereExpression` is designed for composite types or other non-standard column access. Multiple calls accumulate — each expression is AND-ed with the others.

> **Important:** `whereExpression` always generates an equality condition (`expression = :paramName`). It does **not** support custom operators. For `<`, `<=`, `>`, `>=`, `<>`, `LIKE` — use `whereAndOperator` instead.

```java
// PostgreSQL composite type
query.whereExpression("(address).city", "city", "Paris");   // (address).city = :city
query.whereExpression("(address).zip", "zip", "75001");     // AND (address).zip = :zip
```

---

## Relationships

### Many-to-one — `@MappedBy`

```java
public class User implements IEntity<Long> {
    private Long id;
    private Long groupId;

    @MappedBy(value = "groupId", repository = GroupRepository.class)
    private Group group;  // populated by LEFT JOIN via .leftJoin("group", ...)
}
```

### One-to-many — `@OneToMany`

```java
public class User implements IEntity<Long> {
    private Long id;

    @OneToMany(mappedBy = "userId", repository = ContactInfoRepository.class)
    private List<ContactInfo> contacts;  // loaded via .associate("contacts", ...)
}
```

---

## Type mapping reference

### Standard types

The following types are mapped automatically — no annotation needed.

| Java type | DB column type | Scope | Parameters |
|-----------|---------------|-------|------------|
| `String` | VARCHAR / TEXT | Generic (all DBs) | — |
| `String` | TEXT (with cast) | PostgreSQL | — |
| `Long` / `long` | BIGINT | Generic | — |
| `Integer` / `int` | INTEGER | Generic | — |
| `Short` / `short` | SMALLINT | Generic | — |
| `Byte` / `byte` | TINYINT | Generic | — |
| `Double` / `double` | DOUBLE | Generic | — |
| `Float` / `float` | FLOAT | Generic | — |
| `BigDecimal` | DECIMAL / NUMERIC | Generic | — |
| `BigInteger` | BIGINT | Generic | — |
| `Boolean` / `boolean` | BOOLEAN / BIT | Generic | — |
| `LocalDate` | DATE | Generic | — |
| `LocalDateTime` | TIMESTAMP | Generic | — |
| `byte[]` | BLOB / BINARY | Generic (MySQL, Oracle) | — |
| `byte[]` | BYTEA | PostgreSQL | — |
| `UUID` | CHAR / VARCHAR | Generic (MySQL, Oracle) | `@Type(DbDataType.UUID)` to force UUID type |
| `UUID` | UUID (with `::uuid` cast) | PostgreSQL | — |

The `@Type(DbDataType.xxx)` annotation on a field overrides the default DB type used at write time.

### Enum types

Enums are mapped by name (`.name()` → `VARCHAR` on MySQL/Oracle, native `ENUM` type on PostgreSQL).

| Behavior | Scope | Configuration |
|----------|-------|---------------|
| Enum stored as VARCHAR / string | Generic (all DBs) | No configuration needed |
| Enum stored as native PostgreSQL ENUM with `::type_name` cast | PostgreSQL | SQL type name required (see below) |

**Declare the PostgreSQL type name — three equivalent options:**

Option 1 — annotation on the enum class:
```java
@SqlType("user_status")
public enum UserStatus { ACTIVE, INACTIVE }
```

Option 2 — annotation on the field:
```java
@SqlType("user_status")
private UserStatus status;
```

Option 3 — programmatic registration (e.g. in Spring config):
```java
annotationManager.setEnumSqlType(UserStatus.class, "user_status");
```

If both a field-level and a class-level `@SqlType` are present with different values, NativSQL throws a configuration error at startup.

### JSON types

Any POJO can be stored as JSON. On PostgreSQL it uses JSONB; on MySQL/MariaDB it uses the JSON column type.

| Behavior | Scope | Configuration |
|----------|-------|---------------|
| Object serialized/deserialized via Jackson | Generic (all DBs) | JSON type registration required |
| Object stored as JSONB (PostgreSQL native) | PostgreSQL | Same registration |

**Register a JSON type — three equivalent options:**

Option 1 — annotation on the class:
```java
@Json
public class Address { ... }
```

Option 2 — annotation on the field:
```java
@Json
private Address address;
```

Option 3 — programmatic registration:
```java
annotationManager.setJsonInfo(Address.class);
```

### Composite types (PostgreSQL only)

A composite type maps a Java POJO to a PostgreSQL composite type (`CREATE TYPE address_type AS (...)`). The value is serialized as `(field1,field2,...)` and cast with `::type_name`.

| Behavior | Scope | Configuration |
|----------|-------|---------------|
| POJO ↔ PostgreSQL composite type with `(v1,v2)::type_name` | PostgreSQL only | Composite type registration required |

**Register a composite type — three equivalent options:**

Option 1 — annotations on the class:
```java
@CompositeType
@SqlType("address_type")
public class Address { ... }
```

Option 2 — annotation on the field:
```java
@SqlType("address_type")
private Address address;
```

Option 3 — programmatic registration:
```java
annotationManager.setCompositeTypeInfo(Address.class, "address_type");
```

### PostGIS types (PostgreSQL + PostGIS)

Use `PostgresPostGISDialect` and the `PostgresPointTypeMapper`. Point fields are mapped to/from `geometry(Point, 4326)`.

```java
// Activate PostGIS dialect
@Override
protected DatabaseDialect getDatabaseDialectInstance() {
    return new PostgresPostGISDialect();
}

// Field in domain class — automatic mapping
private Point location;
```

---

## Encryption

Fields can be stored encrypted in the database. The plain value is logged (as-is); the encrypted value is what gets written to the DB.

```java
@Encrypted
@CryptAlgo(CryptAlgorithm.GCM)
@CryptKeyProvider(MyKeyProvider.class)
@CryptPrefix("{enc}")
private String email;
```

| Annotation | Required | Description |
|-----------|----------|-------------|
| `@Encrypted` | Yes | Marks the field as encrypted |
| `@CryptAlgo` | Yes | Encryption algorithm (`GCM`, `BCRYPT`, etc.) |
| `@CryptKeyProvider` | Yes | Class that provides the encryption key |
| `@CryptPrefix` | For reversible algos | Prefix stored with the ciphertext (e.g. `{enc}`) |
| `@CryptCost` | Optional | Work factor for bcrypt |

Implement `ICryptKeyProvider` to supply the key:

```java
public class MyKeyProvider implements ICryptKeyProvider {
    @Override
    public byte[] getKey() { return loadKeyFromVault(); }
}
```

For binary storage (e.g. AES GCM output), add `@Type(DbDataType.BYTE_ARRAY)`.

---

## Multiple databases

Override `getDatabaseDialectInstance()` in your repository:

```java
@Repository
public class UserRepository extends GenericRepository<User, Long> {
    @Override
    protected DatabaseDialect getDatabaseDialectInstance() {
        return new PostgresDialect();  // or MySQLDialect, OracleDialect, etc.
    }
}
```

For multi-datasource Spring setups, inject the `JdbcTemplate` for each datasource explicitly.

---

## Pagination

Use `limit(int)` and `offset(int)` on `FindQuery` to paginate results:

```java
// inside UserRepository
public List<User> findPage(int page, int pageSize, String... columns) {
    return findAll(newFindQuery()
        .select(columns)
        .orderByAsc("lastName")
        .limit(pageSize)
        .offset(page * pageSize));
}
```

```java
List<User> page = userRepository.findPage(2, 10, "id", "firstName", "email");
```

Generated SQL (SQL:2008 standard, works on PostgreSQL, H2, Oracle 12c+, SQL Server 2012+):

```sql
SELECT …
FROM users
ORDER BY last_name ASC
OFFSET 20 ROWS
FETCH NEXT 10 ROWS ONLY
```

Rules:
- `limit(n)` — `n` must be > 0, otherwise throws `NativSQLException`.
- `offset(n)` — `n` must be >= 0. `offset(0)` is a no-op (no SQL output).
- When only `limit` is set, generates `FETCH FIRST n ROWS ONLY`.
- Always combine with `ORDER BY` for deterministic results.

---

## Logging

NativSQL logs all DB operations via SLF4J at INFO level under `ovh.heraud.nativsql.repository.DbOperationLogger`:

```
DB.BEGIN UserRepository.insert - INSERT users [request-id]
DB.PARAMS {firstName=John, email=john@example.com, status=ACTIVE}
DB.END UserRepository.insert - INSERT users [request-id] - 12ms
```

Encrypted field values are **not** included in `DB.PARAMS` logs.

Enable in `application.properties`:

```properties
logging.level.ovh.heraud.nativsql=DEBUG
```

---

## Testing

Repository integration tests and e2e tests both build on `BaseRepositoryTest` (published in each
dialect module's `testFixtures`), which spins up a cached Testcontainers database container and
applies a schema script — see `PostgresBaseRepositoryTest`, `MySQLBaseRepositoryTest`,
`MariaDBBaseRepositoryTest`, `OracleBaseRepositoryTest`.

### Repository integration tests (default: rollback after each test)

Extend the dialect-specific base class and provide the schema script; every test method runs
inside a transaction that's rolled back automatically, so tests never leak data into each other:

```java
public abstract class OrderRepositoryTest extends PostgresBaseRepositoryTest {
    @Override
    protected String getScriptPath() {
        return "db/schema-test.sql";
    }
}
```

### E2E tests (opt out of rollback)

An e2e scenario drives a real application process (or container) with its **own** JDBC
connection(s) to the same database. Data inserted through `BaseRepositoryTest`'s wrapping
transaction is invisible to those connections until commit — so by default, nothing seeded this
way can ever be observed by the app under test. Override `rollbackTransactionAfterEachTest()` to
`false` to have seeded data actually committed:

```java
public abstract class OrderE2ERepositoryTest extends PostgresBaseRepositoryTest {
    @Override
    protected String getScriptPath() {
        return "db/schema-test.sql";
    }

    @Override
    protected boolean rollbackTransactionAfterEachTest() {
        return false;
    }
}
```

When rollback is disabled, nothing cleans up data between tests automatically anymore — that
becomes the project's own responsibility (typically a delete+reseed test-data builder called at
the start of each scenario). See [doc/EndToEndTesting.md](doc/EndToEndTesting.md) for a full,
worked e2e setup built on this opt-out (a shared environment singleton starting the container and
the application under test, and a JUnit base class scenario tests extend).

---

## FAQ

### When should I use NativSQL instead of JPA/Hibernate?

Use NativSQL when you want full control over SQL — complex joins, window functions, stored procedures, performance-sensitive queries. Hibernate is a better fit when you want automatic schema management or are doing simple CRUD on a greenfield project.

### Do I need to annotate my domain classes?

No. Plain POJOs with getters/setters work out of the box. Annotations (`@Json`, `@CompositeType`, `@SqlType`, `@Encrypted`, etc.) are opt-in for non-default behaviour.

### How do I handle NULL values?

All `find*`, `insert`, and `update` methods require a non-empty property list — passing none throws `NativSQLException`. A field included in the list with a `null` value is written as `NULL`:

```java
userRepository.insert(user, "firstName", "email");  // email=null → NULL in DB
```

### How do I prevent N+1 queries?

Use `.associate()` in FindQuery for one-to-many loading — it runs 2 queries (1 + 1 batch), regardless of how many entities are returned. Use `.leftJoin()` for many-to-one.

### Can I use NativSQL with Lombok?

Yes. `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` all work fine.

### Can I use it without Spring Boot?

NativSQL relies on `NamedParameterJdbcTemplate` from Spring JDBC. It can work with Spring Framework without Spring Boot, but Spring JDBC is required.

### What happens if a primitive type is used instead of a boxed type?

NativSQL throws a `NativSQLException` at mapping time with a clear message telling you to use the boxed type (e.g. `int` → `Integer`).

### How do I debug which SQL is executed?

Enable Spring JDBC debug logging:

```properties
logging.level.org.springframework.jdbc.core=DEBUG
logging.level.org.springframework.jdbc.core.namedparam=DEBUG
```

### How do I migrate from Hibernate?

Keep Hibernate for schema validation (`ddl-auto=validate`) and create NativSQL repositories alongside. Migrate feature by feature, then remove Hibernate once fully replaced.

### Can I use NativSQL with JPA entities?

It works but is not recommended — JPA annotations on domain classes create framework coupling that NativSQL avoids by design. Prefer separate POJO classes.

### What license is NativSQL under?

GNU General Public License v3 (GPL-3.0). Commercial use is allowed; source code must be available under the same license.
