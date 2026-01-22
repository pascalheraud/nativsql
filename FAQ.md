# Frequently Asked Questions (FAQ)

## General Questions

### Q: What is NativSQL?

**A:** NativSQL is a lightweight, reflection-based SQL mapping library for Java that bridges the gap between raw SQL queries and object mapping. Unlike ORMs like Hibernate, NativSQL:
- You write explicit SQL queries
- Automatic ResultSet to Java object mapping using reflection
- No annotations required on domain classes
- Supports complex nested objects and relationships
- Works great with Spring Boot JDBC

### Q: When should I use NativSQL vs Hibernate/JPA?

**A:** Use NativSQL when you want:
- **Full control over SQL** - You write the exact queries you need
- **Better performance** - No query generation overhead
- **Simpler codebase** - Less magic, fewer annotations
- **Complex queries** - JOINs, aggregations, window functions
- **Direct database access** - Stored procedures, custom SQL

Use Hibernate/JPA when you want:
- **Automatic query generation** - Less SQL to write
- **Database independence** - Switch databases without code changes
- **Object relationships** - Automatic lazy loading, cascading
- **Simpler CRUD** - High-level ORM API

### Q: Does NativSQL support all databases?

**A:** Currently, NativSQL supports:
- ✅ MySQL 8.0+
- ✅ MariaDB 11.0+
- ✅ PostgreSQL 15+

Other databases can be supported by implementing the `DatabaseDialect` interface.

### Q: Is NativSQL production-ready?

**A:** Yes, NativSQL is production-ready. It has:
- Comprehensive test coverage across all supported databases
- GitHub Actions CI/CD for continuous testing
- Used in real-world applications
- Semantic versioning for stability

## Setup & Configuration

### Q: How do I configure NativSQL for multiple databases?

**A:** Create separate repositories for each database with different dialect implementations:

```java
@Configuration
public class DataSourceConfig {

    @Bean(name = "mysqlDataSource")
    public DataSource mysqlDataSource() { ... }

    @Bean(name = "postgresDataSource")
    public DataSource postgresDataSource() { ... }
}

@Repository
public class UserRepositoryMySQL extends GenericRepository<User, Long> {
    @Override
    protected DatabaseDialect getDatabaseDialectInstance() {
        return new DefaultDialect();  // MySQL
    }
}

@Repository
public class UserRepositoryPostgres extends GenericRepository<User, Long> {
    @Override
    protected DatabaseDialect getDatabaseDialectInstance() {
        return new PostgresDialect();
    }
}
```

### Q: Do I need Lombok?

**A:** No, Lombok is optional. It just makes writing domain classes easier:

```java
// With Lombok (clean)
@Data
@Builder
public class User implements Entity<Long> { ... }

// Without Lombok (verbose but works fine)
public class User implements Entity<Long> {
    private Long id;
    private String firstName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public static UserBuilder builder() { ... }
    // etc.
}
```

## Usage Questions

### Q: How do I handle NULL values?

**A:** NativSQL uses standard Java NULL handling:

```java
// In insert/update - null fields are skipped
User user = User.builder()
    .firstName("John")
    .email(null)  // This field is NOT inserted
    .build();
userRepository.insert(user);  // Only firstName is inserted

// For explicit NULL in SQL - use explicit field list
userRepository.insert(user, "firstName", "email");  // email IS set to NULL

// In queries - NULL is a valid value
List<User> usersWithoutEmail = userRepository.findByProperty("email", null);
```

### Q: How do I handle custom types (enums, value objects)?

**A:** Register custom type mappers:

```java
// Custom enum stored as string
public class UserStatusMapper implements ITypeMapper<UserStatus> {
    @Override
    public UserStatus toJava(Object dbValue) {
        if (dbValue == null) return null;
        return UserStatus.valueOf((String) dbValue);
    }

    @Override
    public Object toDatabase(UserStatus javaValue) {
        return javaValue == null ? null : javaValue.name();
    }
}

// Register in your dialect
dialect.registerMapper(UserStatus.class, new UserStatusMapper());
```

### Q: How do I handle JSON/JSONB fields (PostgreSQL)?

**A:** Use automatic JSON mapping:

```java
public class Address {
    public String street;
    public String city;
    public String country;
}

public class User implements Entity<Long> {
    private Long id;
    private String email;
    private Address address;  // Automatically maps to JSONB
}

// Automatically handles serialization/deserialization via Jackson
User user = User.builder()
    .email("john@example.com")
    .address(new Address("123 Main St", "Paris", "France"))
    .build();
userRepository.insert(user);  // address is serialized to JSON
```

### Q: How do I handle relationships?

**A:** NativSQL supports two types of relationships:

#### Many-to-One via @MappedBy

```java
public class User implements Entity<Long> {
    private Long id;
    private String email;
    private Long groupId;

    @MappedBy(value = "groupId", repository = GroupRepository.class)
    private Group group;  // Loaded via LEFT JOIN
}

// Use with JOINs in FindQuery
User userWithGroup = userRepository.find(
    userRepository.newFindQuery()
        .select("id", "email")
        .whereAndEquals("id", userId)
        .leftJoin("group", List.of("id", "name"))
        .build()
);
```

#### One-to-Many via @OneToMany

```java
public class User implements Entity<Long> {
    private Long id;
    private String email;

    @OneToMany(mappedBy = "userId", repository = ContactInfoRepository.class)
    private List<ContactInfo> contacts;  // Loaded via batch query
}

// Use with associations in FindQuery
User userWithContacts = userRepository.find(
    userRepository.newFindQuery()
        .select("id", "email")
        .whereAndEquals("id", userId)
        .associate("contacts", List.of("id", "type", "value"))
        .build()
);
```

### Q: How do I prevent N+1 query problems?

**A:** Use batch operations:

```java
// ❌ N+1 Problem: 1 + N queries
List<User> users = userRepository.findAll();
for (User user : users) {
    List<ContactInfo> contacts = contactRepository.findAllByProperty("userId",
        List.of(user.getId()), "id", "type");  // N separate queries
}

// ✅ Solution 1: Use batch method
List<Long> userIds = users.stream().map(User::getId).collect(toList());
List<ContactInfo> allContacts = contactRepository.findAllByPropertyIn("userId",
    userIds, "id", "type");  // 1 query with IN clause

// ✅ Solution 2: Use JOIN
users = userRepository.findAll(
    userRepository.newFindQuery()
        .select("id", "email")
        .leftJoin("contacts", List.of("id", "type"))
        .build()
);

// ✅ Solution 3: Use association loading
users = userRepository.findAll(
    userRepository.newFindQuery()
        .select("id", "email")
        .associate("contacts", List.of("id", "type"))
        .build()
);
```

## Performance Questions

### Q: How can I optimize queries?

**A:** Best practices:

1. **Select only needed columns**
   ```java
   .select("id", "firstName", "email")  // Not SELECT *
   ```

2. **Use batch operations for multiple records**
   ```java
   List<User> users = userRepository.findAllByIds(userIds, "id", "firstName");
   ```

3. **Use indexes on frequently queried columns**
   ```sql
   CREATE INDEX idx_user_email ON users(email);
   CREATE INDEX idx_user_status ON users(status);
   ```

4. **Cache row mappers** - RowMapperFactory does this automatically

5. **Use transactions for multiple operations**
   ```java
   @Transactional
   public void createUsers(List<User> users) {
       users.forEach(userRepository::insert);
   }
   ```

### Q: Will NativSQL be slower than writing raw JDBC?

**A:** No, NativSQL has minimal overhead:
- Reflection is cached per class type
- No query generation, you write SQL
- Direct ResultSet mapping
- Comparable performance to hand-written JDBC

### Q: How do I debug generated SQL?

**A:** Enable Spring JDBC logging:

```properties
logging.level.org.springframework.jdbc.core=DEBUG
logging.level.org.springframework.jdbc.core.namedparam=DEBUG
```

## Migration Questions

### Q: How do I migrate from Hibernate to NativSQL?

**A:** Step by step:

1. **Keep Hibernate for schema generation**
   ```properties
   spring.jpa.hibernate.ddl-auto=validate
   ```

2. **Create NativSQL repositories alongside Hibernate**
   ```java
   // Old Hibernate way
   @Repository
   public interface UserHibernateRepository extends JpaRepository<User, Long> { }

   // New NativSQL way
   @Repository
   public class UserNativSqlRepository extends GenericRepository<User, Long> { }
   ```

3. **Gradually migrate features** - Migrate piece by piece

4. **Run both in parallel** - Ensure data consistency during migration

5. **Remove Hibernate** - Once fully migrated

### Q: Can I use NativSQL with JPA entities?

**A:** Yes, but not recommended. NativSQL works with plain POJOs:

```java
// ❌ Mixing - can cause issues
@Entity
public class User implements Entity<Long> { }

// ✅ Better - use separate domain classes
@Entity
public class UserEntity { }

public class User implements Entity<Long> { }
```

## Contributing & Support

### Q: How do I report a bug?

**A:** Please use the [Bug Report Template](../.github/ISSUE_TEMPLATE/bug_report.md) on GitHub issues.

Include:
- Java version and operating system
- Database and version
- Minimal code to reproduce
- Full error stack trace

### Q: How do I request a feature?

**A:** Use the [Feature Request Template](../.github/ISSUE_TEMPLATE/feature_request.md) on GitHub issues.

Include:
- Use case and motivation
- Proposed solution
- Example code if applicable
- Database compatibility concerns

### Q: Can I contribute to NativSQL?

**A:** Yes! See [CONTRIBUTING.md](../.github/CONTRIBUTING.md) for guidelines.

Areas we welcome contributions:
- Additional database dialects
- Performance improvements
- Documentation and examples
- Bug fixes and tests

## License & Legal

### Q: What license is NativSQL under?

**A:** MIT License - See [LICENSE](../LICENSE) file for details.

### Q: Can I use NativSQL in commercial projects?

**A:** Yes, MIT License allows commercial use. You just need to include the LICENSE file.

## More Questions?

- 📖 [API Documentation](API.md)
- 📚 [Installation Guide](INSTALLATION.md)
- 💬 [GitHub Discussions](https://github.com/pascalheraud/nativsql/discussions)
- 🐛 [GitHub Issues](https://github.com/pascalheraud/nativsql/issues)
