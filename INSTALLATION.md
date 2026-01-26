# NativSQL Installation Guide

This guide walks you through installing and setting up NativSQL in your project.

## Prerequisites

- Java 17 or higher
- Gradle 8.0 or Maven (project uses Gradle)
- Spring Boot 3.2+
- One of the supported databases:
  - MySQL 8.0+
  - MariaDB 11.0+
  - PostgreSQL 15+

## Installation Steps

### 1. Add NativSQL to Your Project

#### Gradle (Recommended)

Add to your `build.gradle`:

```gradle
repositories {
    mavenCentral()
    // If using snapshot builds:
    maven { url 'https://oss.sonatype.org/content/repositories/snapshots' }
}

dependencies {
    // NativSQL (adjust version as needed)
    implementation 'ovh.heraud:nativsql:1.0.0'

    // Required Spring Boot dependencies
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'

    // Database drivers
    implementation 'mysql:mysql-connector-java:8.0.33'
    // OR for PostgreSQL:
    implementation 'org.postgresql:postgresql:42.7.1'

    // JSON processing
    implementation 'com.fasterxml.jackson.core:jackson-databind'
    implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'

    // Optional: For PostGIS support
    implementation 'net.postgis:postgis-jdbc:2023.1.0'

    // Testing
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.testcontainers:testcontainers:1.19.3'
    testImplementation 'org.testcontainers:mysql:1.19.3'
    testImplementation 'org.testcontainers:postgresql:1.19.3'
}
```

#### Maven

Add to your `pom.xml`:

```xml
<dependencies>
    <!-- NativSQL -->
    <dependency>
        <groupId>ovh.heraud</groupId>
        <artifactId>nativsql</artifactId>
        <version>1.0.0</version>
    </dependency>

    <!-- Spring Boot JDBC -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>

    <!-- Database Driver (choose one) -->
    <dependency>
        <groupId>mysql</groupId>
        <artifactId>mysql-connector-java</artifactId>
        <version>8.0.33</version>
    </dependency>

    <!-- JSON Processing -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.datatype</groupId>
        <artifactId>jackson-datatype-jsr310</artifactId>
    </dependency>
</dependencies>
```

### 2. Configure Your Database Connection

Add to `application.properties` or `application.yml`:

#### MySQL

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

#### PostgreSQL

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/mydb
spring.datasource.username=postgres
spring.datasource.password=password
spring.datasource.driver-class-name=org.postgresql.Driver
```

#### Or with YAML:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mydb
    username: root
    password: password
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate  # Don't let JPA manage schema
```

### 3. Create Spring Boot Configuration

Create a configuration class to set up NativSQL components:

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import ovh.heraud.nativsql.mapper.RowMapperFactory;

@Configuration
public class NativSqlConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public RowMapperFactory rowMapperFactory() {
        return new RowMapperFactory();
    }
}
```

### 4. Create Your Domain Classes

```java
package myapp.domain;

import ovh.heraud.nativsql.domain.Entity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements Entity<Long> {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private LocalDateTime createdAt;

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }
}
```

### 5. Create Your Repository

```java
package myapp.repository;

import org.springframework.stereotype.Repository;
import ovh.heraud.nativsql.repository.GenericRepository;
import myapp.domain.User;

@Repository
public class UserRepository extends GenericRepository<User, Long> {

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
}
```

### 6. Create Database Schema

#### MySQL

```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    email VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### PostgreSQL

```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    email VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 7. Use Your Repository

```java
package myapp.service;

import org.springframework.stereotype.Service;
import myapp.domain.User;
import myapp.repository.UserRepository;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void createUser(String firstName, String lastName, String email) {
        User user = User.builder()
            .firstName(firstName)
            .lastName(lastName)
            .email(email)
            .build();

        userRepository.insert(user);
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public void updateUserName(Long userId, String newFirstName) {
        User user = User.builder()
            .id(userId)
            .firstName(newFirstName)
            .build();

        userRepository.update(user, "id", "firstName");
    }

    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }
}
```

## Development Setup

### Using Docker for Database

Create a `docker-compose.yml` for local development:

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: mydb
    ports:
      - "3306:3306"

  postgres:
    image: postgres:15
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: mydb
    ports:
      - "5432:5432"
```

Start services:

```bash
docker-compose up -d
```

### Running Tests

Add test dependencies to `build.gradle`:

```gradle
testImplementation 'org.springframework.boot:spring-boot-starter-test'
testImplementation 'org.testcontainers:testcontainers:1.19.3'
testImplementation 'org.testcontainers:mysql:1.19.3'
testImplementation 'org.testcontainers:postgresql:1.19.3'
```

Create a test configuration:

```java
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UserRepositoryTest {

    private final UserRepository userRepository;

    public UserRepositoryTest(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Test
    void testCreateAndFindUser() {
        // Create
        User user = User.builder()
            .firstName("John")
            .lastName("Doe")
            .email("john@example.com")
            .build();
        userRepository.insert(user);

        // Find
        User found = userRepository.findByEmail("john@example.com");
        assertNotNull(found);
        assertEquals("John", found.getFirstName());
    }
}
```

## Troubleshooting

### Issue: `Package not found: ovh.heraud.nativsql`

**Solution**: Make sure the dependency is correctly added to your build file and you've imported from `ovh.heraud.nativsql.*`

### Issue: `SQLException: Table not found`

**Solution**: Ensure your database schema is created before running your application.

### Issue: `ClassNotFoundException: com.mysql.cj.jdbc.Driver`

**Solution**: Add the MySQL JDBC driver to your dependencies:

```gradle
implementation 'mysql:mysql-connector-java:8.0.33'
```

### Issue: NativSQL logs not showing

**Solution**: Add logging configuration to `application.properties`:

```properties
logging.level.ovh.heraud.nativsql=DEBUG
logging.level.org.springframework.jdbc=DEBUG
```

## Next Steps

1. Read the [API Documentation](API.md) for detailed method reference
2. Check [README.md](../README.md) for examples and features
3. Review [CONTRIBUTING.md](../.github/CONTRIBUTING.md) if you want to contribute
4. Look at test examples in `src/test/java/ovh/heraud/nativsql/repository/`

## Support

- 📖 [API Documentation](API.md)
- 💬 [GitHub Discussions](https://github.com/pascalheraud/nativsql/discussions)
- 🐛 [Report Issues](https://github.com/pascalheraud/nativsql/issues)

## License

MIT License - See [LICENSE](../LICENSE) file
