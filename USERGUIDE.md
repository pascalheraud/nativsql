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
13. [FAQ](#faq)

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

// By IDs
List<User> users = userRepository.findAllByIds(List.of(1L, 2L, 3L), "id", "firstName");
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

---

## Querying with FindQuery

`FindQuery` is a type-safe builder for SELECT queries.

```java
// Simple query with conditions
List<User> users = userRepository.findAll(
    userRepository.newFindQuery()
        .select("id", "firstName", "email")
        .whereAndEquals("status", UserStatus.ACTIVE)
        .whereAndEquals("groupId", groupId)
        .orderBy("firstName", "ASC")
        .build()
);

// IN clause
List<User> users = userRepository.findAll(
    userRepository.newFindQuery()
        .select("id", "firstName")
        .whereAndIn("status", List.of(UserStatus.ACTIVE, UserStatus.SUSPENDED))
        .build()
);
```

### JOIN (many-to-one)

```java
User userWithGroup = userRepository.find(
    userRepository.newFindQuery()
        .select("id", "firstName", "email")
        .whereAndEquals("id", userId)
        .leftJoin("group", List.of("id", "name"))  // maps to user.group.id, user.group.name
        .build()
);
```

Requires `@MappedBy` on the domain class — see [Relationships](#relationships).

### Filtering on joined table columns

Use dot-notation in any `whereAnd*` method to filter on a column of the joined entity. The segment before the dot is the association name (matching the `leftJoin`/`innerJoin` call); the segment after is the Java field name on the joined entity.

```java
// Filter on joined column — simple equality
userRepository.findAll(
    userRepository.newFindQuery()
        .select("id", "firstName")
        .leftJoin("group", "name")
        .whereAndEquals("group.name", "Admins")  // WHERE user_group.name = :groupName
);

// Mix main-table and joined-table conditions
userRepository.findAll(
    userRepository.newFindQuery()
        .select("id", "firstName", "status")
        .leftJoin("group", "name")
        .whereAndEquals("status", UserStatus.ACTIVE)       // WHERE users.status = :status
        .whereAndEquals("group.name", "Admins")            //   AND user_group.name = :groupName
);

// Other operators work too
query.whereAndOperator("group.name", Operator.LIKE, "Admin%")         // user_group.name LIKE :groupName
query.whereAndColumnOperator("group.deletedAt", ColumnOperator.IS_NULL) // user_group.deleted_at IS NULL
query.whereAndIn("group.status", List.of("ACTIVE", "PENDING"))        // user_group.status IN (:groupStatus)
query.whereAndRange("group.age", RangeOperator.BETWEEN, 18, 65)       // user_group.age BETWEEN :groupAgeLow AND :groupAgeHigh
```

**Rules:**
- Exactly one dot is allowed — `"a.b.col"` throws `NativSQLException`.
- The association name must match a `leftJoin`/`innerJoin` call earlier in the chain.
- Column names are camelCase Java field names; `identifierConverter` converts them to snake_case automatically.
- Parameter names are derived by joining the two segments in camelCase: `"group.name"` → `:groupName`, `"group.deletedAt"` → `:groupDeletedAt`.

### Association loading (one-to-many)

```java
User userWithContacts = userRepository.find(
    userRepository.newFindQuery()
        .select("id", "firstName")
        .whereAndEquals("id", userId)
        .associate("contacts", List.of("id", "type", "value"))
        .build()
);
```

Executes 2 queries (1 for user + 1 batch for contacts) — no N+1 problem. Requires `@OneToMany` on the domain class.

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

`whereExpression` is designed for composite types or other non-standard column access. Multiple calls accumulate — each expression is AND-ed with the others:

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
