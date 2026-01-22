# NativSQL Documentation Index

Welcome to NativSQL documentation! This page provides a complete guide to all available resources.

## 📚 Core Documentation

### Quick Access

| Document | Purpose | Audience |
|----------|---------|----------|
| [README.md](README.md) | Overview, features, quick start | Everyone |
| [INSTALLATION.md](INSTALLATION.md) | Step-by-step setup guide | New users |
| [API.md](API.md) | Complete API reference | Developers |
| [FAQ.md](FAQ.md) | Common questions and answers | Everyone |
| [CHANGELOG.md](CHANGELOG.md) | Version history and changes | All users |

## 🚀 Getting Started

### For New Users

1. **[Start Here: README.md](README.md)**
   - What is NativSQL?
   - Why use NativSQL?
   - Basic features overview
   - Quick start example

2. **[Installation Guide: INSTALLATION.md](INSTALLATION.md)**
   - Prerequisites and setup
   - Dependency configuration
   - Database configuration
   - Creating your first repository
   - Testing setup

3. **[API Reference: API.md](API.md)**
   - Complete method reference
   - Class documentation
   - Code examples
   - Best practices

### Common Scenarios

- **How do I set up NativSQL?** → [INSTALLATION.md](INSTALLATION.md)
- **How do I create a repository?** → [README.md](README.md#quick-start) / [API.md](API.md#genericrepository)
- **How do I write queries?** → [README.md](README.md#advanced-features) / [API.md](API.md#findquery)
- **How do I handle relationships?** → [API.md](API.md#annotations) / [FAQ.md](FAQ.md#how-do-i-handle-relationships)
- **I have a problem!** → [FAQ.md](FAQ.md) / [Troubleshooting](INSTALLATION.md#troubleshooting)

## 🔧 Developer Guides

### GitHub & CI/CD

- **[.github/WORKFLOWS.md](.github/WORKFLOWS.md)** - GitHub Actions CI/CD setup
- **[CONTRIBUTING.md](CONTRIBUTING.md)** - How to contribute
- **[docker-compose.yml](docker-compose.yml)** - Local development setup

### Code Examples

- **[Test Examples](src/test/java/ovh/heraud/nativsql/repository/)** - Real test cases for all databases:
  - MySQL tests: `src/test/java/ovh/heraud/nativsql/repository/mysql/`
  - MariaDB tests: `src/test/java/ovh/heraud/nativsql/repository/mariadb/`
  - PostgreSQL tests: `src/test/java/ovh/heraud/nativsql/repository/postgres/`

## 📖 API Documentation

### Core Classes

#### [GenericRepository](API.md#genericrepository)
Base class for all repositories. Provides:
- `insert()` - Create records
- `update()` - Modify records
- `delete()` - Remove records
- `findByProperty()` - Query by field
- `findAllByIds()` - Batch query by IDs
- `find()` / `findAll()` - Query with FindQuery builder

#### [FindQuery](API.md#findquery)
Type-safe query builder. Supports:
- `select()` - Specify columns
- `whereAndEquals()` - Equality conditions
- `whereAndIn()` - IN clause conditions
- `leftJoin()` / `innerJoin()` - Relationships via JOIN
- `associate()` - One-to-many loading
- `orderBy()` - Sorting

#### [Condition](API.md#condition)
Represents a single WHERE clause condition.

#### [Operator](API.md#operator)
SQL operators with extensible strategy pattern:
- `EQUALS` - Equality comparison
- `IN` - Set membership
- Extensible for custom operators (LIKE, >, <, BETWEEN, etc.)

### Type System

#### [ITypeMapper](API.md#type-mappers)
Custom type mapping interface for:
- Enums (EnumStringMapper)
- Value objects
- Custom types
- Database-specific types

#### [RowMapperFactory](API.md#rowmapperfactory)
Creates and caches row mappers for automatic ResultSet mapping.

#### [DatabaseDialect](API.md#database-dialects)
Database-specific implementations:
- DefaultDialect (MySQL, MariaDB)
- PostgresDialect (PostgreSQL)

### Annotations

#### [@Entity](API.md#entity-interface)
Mark your domain classes as entities.

#### [@MappedBy](API.md#mappedbyannotation)
Define many-to-one relationships via JOIN.

#### [@OneToMany](API.md#onetomanyannotation)
Define one-to-many relationships via batch loading.

## 🎓 Tutorials & Examples

### Basic Operations

```java
// Insert
userRepository.insert(user, "firstName", "email");

// Query single
User user = userRepository.findByProperty("email", email, "id", "firstName");

// Query multiple
List<User> users = userRepository.findAllByProperty("status",
    List.of(ACTIVE, SUSPENDED), "id", "firstName");

// Update
userRepository.update(user, "id", "firstName");

// Delete
userRepository.deleteById(userId);
```

### Advanced Queries

```java
// With conditions
List<User> users = userRepository.findAll(
    userRepository.newFindQuery()
        .select("id", "firstName", "email")
        .whereAndEquals("status", ACTIVE)
        .whereAndEquals("groupId", groupId)
        .orderBy("firstName", "ASC")
        .build()
);

// With JOIN
User userWithGroup = userRepository.find(
    userRepository.newFindQuery()
        .select("id", "firstName")
        .whereAndEquals("id", userId)
        .leftJoin("group", List.of("id", "name"))
        .build()
);

// With associations
User userWithContacts = userRepository.find(
    userRepository.newFindQuery()
        .select("id", "firstName")
        .whereAndEquals("id", userId)
        .associate("contacts", List.of("id", "type", "value"))
        .build()
);
```

### Handling Special Types

```java
// Enums
repository.insert(user, "status");  // Auto-converted

// JSON fields (PostgreSQL)
Address address = new Address("123 Main", "Paris", "France");
user.setAddress(address);
repository.insert(user, "address");  // Auto-serialized to JSONB

// LocalDateTime
user.setCreatedAt(LocalDateTime.now());
repository.insert(user, "createdAt");  // Auto-converted to TIMESTAMP
```

## ❓ FAQ & Troubleshooting

### Quick Answers

- **Q: How do I handle NULL values?** → [FAQ.md](FAQ.md#q-how-do-i-handle-null-values)
- **Q: How do I prevent N+1 queries?** → [FAQ.md](FAQ.md#q-how-do-i-prevent-n1-query-problems)
- **Q: Can I use multiple databases?** → [FAQ.md](FAQ.md#q-how-do-i-configure-nativsql-for-multiple-databases)
- **Q: How do I optimize queries?** → [FAQ.md](FAQ.md#q-how-can-i-optimize-queries)
- **Q: Can I migrate from Hibernate?** → [FAQ.md](FAQ.md#q-how-do-i-migrate-from-hibernate-to-nativsql)

### Common Issues

- **Package not found** → [INSTALLATION.md](INSTALLATION.md#troubleshooting)
- **Table not found** → [INSTALLATION.md](INSTALLATION.md#troubleshooting)
- **ClassNotFoundException** → [INSTALLATION.md](INSTALLATION.md#troubleshooting)

## 🤝 Contributing & Support

### Want to Contribute?

1. **[CONTRIBUTING.md](CONTRIBUTING.md)** - Contribution guidelines
2. **[.github/WORKFLOWS.md](.github/WORKFLOWS.md)** - Development workflow
3. **[.github/ISSUE_TEMPLATE/](.github/ISSUE_TEMPLATE/)** - How to report issues

### Getting Help

- 💬 **[GitHub Discussions](https://github.com/pascalheraud/nativsql/discussions)** - Ask questions
- 🐛 **[GitHub Issues](https://github.com/pascalheraud/nativsql/issues)** - Report bugs
- 📧 **[Contributing Guide](CONTRIBUTING.md)** - Get involved

## 📋 Project Structure

```
NativSQL/
├── src/
│   ├── main/java/ovh/heraud/nativsql/
│   │   ├── annotation/      - @MappedBy, @OneToMany, etc.
│   │   ├── db/              - DatabaseDialect implementations
│   │   ├── domain/          - Entity interface
│   │   ├── exception/       - NativSQLException
│   │   ├── mapper/          - Row mapping and type conversion
│   │   ├── repository/      - GenericRepository base class
│   │   └── util/            - Condition, FindQuery, Operator, etc.
│   └── test/java/           - Comprehensive tests for all databases
├── .github/
│   ├── workflows/           - GitHub Actions CI/CD
│   └── ISSUE_TEMPLATE/      - Bug reports, feature requests
├── INSTALLATION.md          - Setup guide
├── API.md                   - API reference
├── FAQ.md                   - Frequently asked questions
├── CHANGELOG.md             - Version history
└── README.md                - Overview and quick start
```

## 🔗 Useful Links

### External Resources

- [Spring Boot JDBC Documentation](https://spring.io/projects/spring-framework)
- [Jackson Documentation](https://github.com/FasterXML/jackson)
- [Testcontainers Guide](https://www.testcontainers.org/)

### Related Tools

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Gradle Build Tool](https://gradle.org/)
- [Docker](https://www.docker.com/)

## 📈 Documentation Status

Last updated: January 23, 2026

- ✅ API Documentation (Complete)
- ✅ Installation Guide (Complete)
- ✅ FAQ (Complete)
- ✅ Contribution Guidelines (Complete)
- ✅ CI/CD Workflows (Complete)
- ✅ Code Examples (Complete)

## 📞 Quick Reference

### Getting Started

```bash
# Clone repository
git clone https://github.com/pascalheraud/nativsql.git

# Start database services
docker-compose up -d

# Run tests
./gradlew test

# Build project
./gradlew build
```

### Create Repository

```java
@Repository
public class UserRepository extends GenericRepository<User, Long> {
    @Override
    protected String getTableName() { return "users"; }

    @Override
    protected Class<User> getEntityClass() { return User.class; }
}
```

### Basic Operations

```java
// Insert
repository.insert(entity);

// Find
User user = repository.findByProperty("email", "john@example.com", "id", "email");

// Update
repository.update(user, "id", "firstName");

// Delete
repository.deleteById(userId);

// Complex query
List<User> users = repository.findAll(
    repository.newFindQuery()
        .select("id", "firstName")
        .whereAndEquals("status", ACTIVE)
        .orderBy("firstName", "ASC")
        .build()
);
```

---

## Need Help?

- **New to NativSQL?** → Start with [INSTALLATION.md](INSTALLATION.md)
- **Looking for examples?** → Check [README.md](README.md#advanced-features)
- **Have a question?** → See [FAQ.md](FAQ.md)
- **Want to contribute?** → Read [CONTRIBUTING.md](CONTRIBUTING.md)
- **Found a bug?** → [Report an issue](https://github.com/pascalheraud/nativsql/issues)

**Happy coding! 🚀**
