# Changelog

All notable changes to NativSQL will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.3.2] - 2026-02-09

### Fixed
- **Critical**: `findExternal()` and `findAllExternal()` now properly convert parameters using TypeMappers (fixes `PSQLException: Cannot infer SQL type` for PostGIS Point and custom types)


## [1.3.1] - 2026-02-09

### Fixed
- Fixed `NullPointerException` when inserting/updating fields with null values
- Improved error messages for missing TypeMappers with clear `IllegalArgumentException`

## [1.2.0] - 2026-01-27

### Added
- **PostGIS Support (org.postgis.Point)**
  - `PostgresPointTypeMapper` for PostgreSQL geometry columns using PGgeometry
  - `MySQLPointTypeMapper` for MySQL spatial coordinates
  - `MariaDBPointTypeMapper` for MariaDB spatial columns
  - Point field support in User entity across all databases
  - PostGIS JDBC and Geometry library dependencies (version 2.5.1)

- **Spatial Type Mappers**
  - `PostgresPostGISDialect` - PostgreSQL dialect with PostGIS support
  - `MySQLPointDialect` - MySQL dialect with spatial Point support
  - `MariaDBPointDialect` - MariaDB dialect with spatial Point support
  - Base dialect classes are now non-component classes that can be extended

### Changed
- **Dialect Architecture**
  - `PostgresDialect`, `MySQLDialect`, `MariaDBDialect` are now non-component base classes
  - Concrete dialect implementations can extend these base classes
  - Removes @Component annotation from base classes to prevent Spring conflicts
  - Allows for specialized dialect implementations (e.g., with PostGIS support)

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
