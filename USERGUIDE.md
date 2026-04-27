# NativSQL User Guide

## Table of Contents

1. [Installation](#installation)
2. [Configuration](#configuration)
3. [Domain classes](#domain-classes)
4. [Repository basics](#repository-basics)
5. [CRUD operations](#crud-operations)
6. [Querying with FindQuery](#querying-with-findquery)
7. [Relationships](#relationships)
8. [Type mapping reference](#type-mapping-reference)
9. [Encryption](#encryption)
10. [Multiple databases](#multiple-databases)
11. [Logging](#logging)
12. [FAQ](#faq)

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

```java
// All non-null fields
userRepository.insert(user);

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
userRepository.deleteById(userId);
userRepository.delete("email", "john@example.com");
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

NULL values are handled naturally: a `null` field is skipped in insert unless explicitly listed; a null value in a condition generates `IS NULL`.

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

In `insert()` / `update()` with no field list, null fields are skipped. To explicitly write NULL, pass the field name in the explicit list:

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
