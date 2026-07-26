# NativSQL

**Write SQL. Get objects. Nothing in between.**

NativSQL is a lightweight Java library that bridges Spring Boot JDBC and your domain model — without the magic, the surprises, or the hidden queries of a full ORM.

[![Build and Test](https://github.com/pascalheraud/nativsql/actions/workflows/build.yml/badge.svg)](https://github.com/pascalheraud/nativsql/actions/workflows/build.yml)
[![Pull Request Checks](https://github.com/pascalheraud/nativsql/actions/workflows/pr-checks.yml/badge.svg)](https://github.com/pascalheraud/nativsql/actions/workflows/pr-checks.yml)

## Why NativSQL?

Most Java data access layers force a choice between two extremes: the full magic of JPA/Hibernate (invisible queries, lazy-loading surprises, schema ownership fights) or the full ceremony of raw JDBC (boilerplate `ResultSet` mapping, manual parameter binding for every query).

NativSQL occupies the middle ground:

- **You write the SQL** — no query generation, no surprises, full control over execution plans
- **Automatic object mapping** — reflection-based `ResultSet` → POJO mapping, camelCase ↔ snake_case by convention
- **Zero annotations required** on domain classes — plain POJOs, no framework coupling
- **Rich type system** — enums, JSON/JSONB, composite types (PostgreSQL), UUID, encryption, PostGIS
- **Relationship support** — `@MappedBy` (JOIN-based), `@OneToMany` (batch loading, no N+1)
- **Pagination** — `limit` / `offset` on `FindQuery`, standard SQL:2008 syntax ([see User Guide](USERGUIDE.md#pagination))
- **Multi-database** — MySQL, MariaDB, PostgreSQL, Oracle, each with a dedicated dialect

## Quick Start

```java
// 1. Extend GenericRepository
@Repository
public class UserRepository extends GenericRepository<User, Long> {
    @Override protected String getTableName() { return "users"; }
    @Override protected Class<User> getEntityClass() { return User.class; }
}

// 2. Use it
userRepository.insert(user);
userRepository.update(user, "id", "firstName", "email");
userRepository.deleteById(userId);

User found = userRepository.findByProperty("email", email, "id", "firstName", "email");

// Custom queries live behind a named method on the repository — never build
// FindQuery in calling code (see USERGUIDE.md "Querying with FindQuery")
List<User> active = userRepository.findActiveUsers();
```

## Supported Databases

| Database   | Module              | Notes                             |
|------------|---------------------|-----------------------------------|
| MySQL 8.0+ | `nativsql-mysql`    |                                   |
| MariaDB 11+| `nativsql-mariadb`  |                                   |
| PostgreSQL 15+ | `nativsql-postgres` | + PostGIS, composite types, JSONB |
| Oracle 20+ | `nativsql-oracle`   |                                   |

## Documentation

- **[USERGUIDE.md](USERGUIDE.md)** — complete user guide: installation, all features, type mapping reference, FAQ
- **[doc/ARCHITECTURE.md](doc/ARCHITECTURE.md)** — internals, dialect chain, type system design
- **[doc/EndToEndTesting.md](doc/EndToEndTesting.md)** — full e2e test setup on top of `BaseRepositoryTest`
- **[CHANGELOG.md](CHANGELOG.md)** — version history

## License

GNU General Public License v3 (GPL-3.0)
