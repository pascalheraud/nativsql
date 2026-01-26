# Changelog

All notable changes to NativSQL will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- **BREAKING**: Package renamed from `io.github.pascalheraud.nativsql` to `ovh.heraud.nativsql`
  - Update all imports in your project
  - All classes maintain the same functionality

### Added
- **Extensible WHERE Expression Builder Pattern**
  - New `WhereExpressionBuilder` interface for custom operators
  - Each `Operator` enum value includes its SQL expression generator
  - Easy to extend with new operators (LIKE, >, <, BETWEEN, etc.)
  - Eliminates if/switch logic from query building

- **Batch Query Methods**
  - `findAllByIds(List<?> ids, String... columns)` - Query by multiple IDs
  - `findAllByPropertyIn(String property, List<?> values, String... columns)` - Query by property values
  - Improved `findAllByProperty()` to use IN clause for batch operations
  - Significantly better performance for loading related entities

- **GitHub Actions CI/CD**
  - `build.yml` - Automated build and test on Java 17+ with MySQL, MariaDB, PostgreSQL
  - `pr-checks.yml` - Code quality, security scanning, coverage reporting
  - `release.yml` - Automated GitHub releases and artifact publishing
  - `dependabot.yml` - Automatic dependency updates

- **Docker Compose Setup**
  - Easy local development with MySQL 8.0, MariaDB 11, PostgreSQL 15
  - Health checks included for reliable service startup

- **Improved Documentation**
  - Contributing guidelines
  - Issue templates (bug reports, feature requests)
  - Workflow documentation
  - Advanced features guide

### Fixed
- **RowMapperFactory Recursion Issue**
  - Replaced `computeIfAbsent()` with explicit cache get/put pattern
  - Fixes `IllegalStateException: Recursive update` in ConcurrentHashMap
  - Allows proper nested type mapper creation

### Improved
- **Code Quality**
  - Refactored `loadAssociationInBatch()` to work directly with List<String>
  - Removed `ensureForeignKeyInColumns()` helper method
  - Simplified WHERE condition generation with strategy pattern
  - Better separation of concerns in FindQuery builder

- **Test Coverage**
  - Added `testFindAllByIds()` tests for all databases (MySQL, MariaDB, PostgreSQL)
  - All tests passing consistently across all supported databases

## [1.0.0] - 2026-01-23

### Initial Release

#### Added
- Generic `RowMapper` with reflection-based ResultSet mapping
- Support for nested objects with dot notation
- `GenericRepository<T, ID>` base class for common CRUD operations
- Automatic enum mapping (Java ↔ PostgreSQL ENUM)
- JSON/JSONB support for complex objects
- Custom type mappers for value objects
- Geographic types support (PostGIS)
- Convention over configuration with automatic camelCase ↔ snake_case conversion
- `FindQuery` builder for type-safe query construction
- Batch loading with JOIN support
- OneToMany association loading

#### Supported Databases
- MySQL 8.0+
- MariaDB 11.0+
- PostgreSQL 15+ (with PostGIS support)

#### Supported Java Versions
- Java 17+

---

## Migration Guide

### From `io.github.pascalheraud.nativsql` to `ovh.heraud.nativsql`

Update all imports in your code:

**Before:**
```java
import io.github.pascalheraud.nativsql.repository.GenericRepository;
import io.github.pascalheraud.nativsql.util.FindQuery;
import io.github.pascalheraud.nativsql.mapper.RowMapperFactory;
```

**After:**
```java
import ovh.heraud.nativsql.repository.GenericRepository;
import ovh.heraud.nativsql.util.FindQuery;
import ovh.heraud.nativsql.mapper.RowMapperFactory;
```

No functional changes - just update the package names and your code will work as before.

---

## Future Roadmap

### Planned Features
- [ ] Query result caching
- [ ] Pagination support in FindQuery
- [ ] SQL logging and debugging tools
- [ ] Integration with Spring Data JPA (optional)
- [ ] GraphQL query generation
- [ ] Additional database support (Oracle, SQLServer)
- [ ] Native queries with named result mappings

### Under Consideration
- Custom aggregation functions
- Window functions support
- Full-text search integration
- Computed/generated columns support

---

## Contributors

See [CONTRIBUTORS.md](CONTRIBUTORS.md) for the list of contributors.

## License

MIT License - see [LICENSE](LICENSE) file for details
