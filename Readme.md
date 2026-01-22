# NativSQL

A lightweight, reflection-based SQL mapping library for Java with Spring Boot and PostgreSQL.

[![Build and Test](https://github.com/pascalheraud/nativsql/actions/workflows/build.yml/badge.svg)](https://github.com/pascalheraud/nativsql/actions/workflows/build.yml)
[![Pull Request Checks](https://github.com/pascalheraud/nativsql/actions/workflows/pr-checks.yml/badge.svg)](https://github.com/pascalheraud/nativsql/actions/workflows/pr-checks.yml)

## Features

- **Generic RowMapper**: Automatic mapping from ResultSet to Java objects using reflection
- **Nested Objects**: Support for nested objects using dot notation (`address.street`)
- **Generic Repository**: Base repository with insert/update operations
- **Type Conversions**: 
  - Automatic enum mapping (Java ↔ PostgreSQL ENUM)
  - JSON/JSONB support for complex objects
  - Custom type mappers for value objects
  - Geographic types (PostGIS)
- **Convention over Configuration**: 
  - Automatic camelCase ↔ snake_case conversion
  - No annotations required (pure POJOs)
- **Lazy Instantiation**: Objects created only when data exists

## Quick Start

### 1. Setup

Add dependencies to your `build.gradle`:

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
    implementation 'org.postgresql:postgresql:42.7.1'
    implementation 'net.postgis:postgis-jdbc:2023.1.0'
    implementation 'com.fasterxml.jackson.core:jackson-databind'
}
```

### 2. Configuration

```java
@Configuration
public class NativSqlConfig {
    
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    @Bean
    public TypeMapperFactory typeMapperFactory(ObjectMapper objectMapper) {
        TypeMapperFactory factory = new TypeMapperFactory(objectMapper);
        
        // Register JSON types
        factory.registerJsonType(Address.class);
        factory.registerJsonType(Preferences.class);
        
        // Register value objects
        factory.registerCompositeMapper(Email.class, String.class, Email::new);
        
        return factory;
    }
}
```

### 3. Define Your Domain

```java
public class User {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private UserStatus status;      // Enum
    private Address address;        // JSON
    private Preferences preferences; // JSON
    private LocalDateTime createdAt;
    
    // Getters and setters
}

public enum UserStatus {
    ACTIVE, INACTIVE, SUSPENDED
}
```

### 4. Create Repository

```java
import ovh.heraud.nativsql.repository.GenericRepository;

@Repository
public class UserRepository extends GenericRepository<User, Long> {

    @Autowired
    private RowMapperFactory rowMapperFactory;

    @Override
    protected String getTableName() {
        return "users";
    }

    @Override
    protected Class<User> getEntityClass() {
        return User.class;
    }

    public User findByEmail(String email) {
        return findByProperty("email", email, "id", "firstName", "lastName", "email");
    }

    public List<User> findByCity(String city) {
        return findAllByPropertyExpression("(address)->>'city'", "city", city,
            "id", "firstName", "lastName", "email", "address");
    }
}
```

### 5. Use the Repository

```java
// Insert
User user = new User();
user.setFirstName("John");
user.setEmail("john@example.com");
user.setAddress(new Address("123 Main St", "Paris", "75001", "France"));

userRepository.insert(user);  // All non-null fields
// or
userRepository.insert(user, "firstName", "email", "address");  // Specific fields

// Update
user.setFirstName("Jane");
userRepository.update(user, "id");  // All non-null fields
// or
userRepository.update(user, "id", "firstName");  // Specific fields

// Delete
userRepository.delete("id", userId);
```

## Nested Objects

Map joined tables to nested objects using dot notation:

```java
public class UserWithAddress {
    private Long id;
    private String email;
    private AddressEntity address;  // Nested object
}

// In repository
String sql = """
    SELECT 
        u.id, 
        u.email,
        a.id AS "address.id",
        a.street AS "address.street",
        a.city AS "address.city"
    FROM users u
    LEFT JOIN addresses a ON u.id = a.user_id
    """;

List<UserWithAddress> users = jdbcTemplate.query(sql,
    rowMapperFactory.getRowMapper(UserWithAddress.class));
```

## Custom Type Mappers

### Value Objects

```java
// Register a value object mapper
factory.registerCompositeMapper(Email.class, String.class, Email::new);

// In your entity
private Email email;  // Stored as VARCHAR, mapped to Email value object
```

### Geographic Types

```java
// Register PostGIS Point mapper
factory.register(Point.class, (rs, col) -> {
    PGobject pgObj = (PGobject) rs.getObject(col);
    if (pgObj == null) return null;
    return parsePoint(pgObj.getValue());
});
```

## Advanced Features

### FindQuery Builder

Build type-safe, reusable queries:

```java
// Simple query
List<User> users = repository.findAll(
    repository.newFindQuery()
        .select("id", "firstName", "email")
        .whereAndEquals("status", UserStatus.ACTIVE)
        .orderBy("firstName")
        .build(),
    "id", "firstName", "email"
);

// Query with IN clause
List<User> activeUsers = repository.findAllByIds(
    List.of(1L, 2L, 3L),
    "id", "firstName", "email"
);

// Query with JOINs
User userWithGroup = repository.find(
    repository.newFindQuery()
        .select("id", "firstName", "email")
        .whereAndEquals("id", userId)
        .leftJoin("group", List.of("id", "name"))
        .build()
);
```

### Extensible WHERE Expressions

The WHERE clause builder supports extensible operators:

```java
public enum Operator {
    EQUALS("=", (col, param) -> col + " = :" + param),
    IN("IN", (col, param) -> col + " IN (:" + param + ")");
    // Easy to add: LIKE, >, <, BETWEEN, etc.
}

// Use in queries
Condition condition = new Condition("email", Operator.EQUALS, "john@example.com");
```

### Batch Loading with Associations

Load one-to-many relationships efficiently:

```java
User user = repository.find(
    repository.newFindQuery()
        .select("id", "firstName", "email")
        .whereAndEquals("id", userId)
        .associate("contacts", List.of("id", "type", "value"))
        .build()
);
// Loads user and all their contacts in 2 queries (N+1 problem solved)
```

## Running Tests

Start PostgreSQL with Docker:

```bash
docker-compose up -d
```

Run tests:

```bash
./gradlew test
```

Tests use Testcontainers to spin up a PostgreSQL instance automatically.

## Database Schema

```sql
CREATE TYPE user_status AS ENUM ('ACTIVE', 'INACTIVE', 'SUSPENDED');

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    email VARCHAR(255) UNIQUE NOT NULL,
    status user_status DEFAULT 'ACTIVE',
    address JSONB,
    preferences JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Project Structure

```
src/
├── main/
│   └── java/
│       └── ovh/heraud/nativsql/
│           ├── annotation/
│           │   ├── EnumMapping.java
│           │   ├── MappedBy.java
│           │   └── OneToMany.java
│           ├── db/
│           │   ├── DatabaseDialect.java
│           │   ├── DefaultDialect.java
│           │   └── (dialect implementations)
│           ├── domain/
│           │   └── Entity.java
│           ├── exception/
│           │   └── NativSQLException.java
│           ├── mapper/
│           │   ├── GenericRowMapper.java
│           │   ├── ITypeMapper.java
│           │   ├── PropertyMetadata.java
│           │   ├── RowMapperFactory.java
│           │   └── (type mappers)
│           ├── repository/
│           │   └── GenericRepository.java
│           └── util/
│               ├── Condition.java
│               ├── FindQuery.java
│               ├── Operator.java
│               ├── WhereExpressionBuilder.java
│               └── (utilities)
├── test/
│   └── java/
│       └── ovh/heraud/nativsql/
│           ├── domain/(mariadb|mysql|postgres)/
│           └── repository/(mariadb|mysql|postgres)/
└── testFixtures/
    └── java/
        └── ovh/heraud/nativsql/
            └── repository/(mariadb|mysql|postgres)/
```

## Design Principles

1. **Pure POJOs**: No annotations required, just getters/setters
2. **Convention over Configuration**: Automatic camelCase ↔ snake_case
3. **Explicit SQL**: You write the SQL, we handle the mapping
4. **Type Safety**: Compile-time type checking for custom mappers
5. **Performance**: Reflection metadata cached per class

## Documentation

For more detailed information, see:
- **[DOCS.md](DOCS.md)** - Complete documentation index
- **[API.md](API.md)** - API reference
- **[INSTALLATION.md](INSTALLATION.md)** - Installation guide
- **[FAQ.md](FAQ.md)** - Frequently asked questions
- **[CONTRIBUTING.md](CONTRIBUTING.md)** - How to contribute
- **[CHANGELOG.md](CHANGELOG.md)** - Version history

## License

MIT License