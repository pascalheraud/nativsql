# Changelog

All notable changes to NativSQL will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.2.0] - 2026-04-21

### Added
- **Getter-based filter property for `GenericRepository` byProperty methods** — All `findByProperty` / `findAllByProperty` / `findAllByPropertyIn` overloads now accept a getter method reference as the *filter* property in addition to a `String` property name, enabling full compile-time safety on both the WHERE column and the SELECT columns:
  - `findByProperty(Getter<T>, Object, String...)` — getter filter + string column selection
  - `findByProperty(Getter<T>, Object, Getter<T>...)` — getter filter + getter column selection
  - `findAllByProperty(Getter<T>, Object, String...)` — getter filter + string column selection
  - `findAllByProperty(Getter<T>, Object, Getter<T>...)` — getter filter + getter column selection
  - `findAllByProperty(Getter<T>, Object, OrderBy, String...)` — getter filter + order by + string column selection
  - `findAllByProperty(Getter<T>, Object, OrderBy, Getter<T>...)` — getter filter + order by + getter column selection
  - `findAllByProperty(Getter<T>, List<?>, String...)` — getter filter + IN clause + string column selection
  - `findAllByProperty(Getter<T>, List<?>, Getter<T>...)` — getter filter + IN clause + getter column selection
  - `findAllByPropertyIn(Getter<T>, List<?>, String...)` — getter filter + IN clause + string column selection
  - `findAllByPropertyIn(Getter<T>, List<?>, Getter<T>...)` — getter filter + IN clause + getter column selection

## [2.1.0] - 2026-04-21

### Added
- **Getter-based API for `FindQuery`** — All query builder methods now accept getter method references (e.g., `User::getStatus`) in addition to `String` column names, enabling compile-time safety and IDE autocomplete:
  - `select(Getter<T>...)` — type-safe column selection
  - `whereAndEquals(Getter<T>, Object)` — type-safe equality filter
  - `whereAndIn(Getter<T>, List<?>)` — type-safe IN filter
  - `orderByAsc(Getter<T>)` / `orderByDesc(Getter<T>)` — type-safe ordering
  - `leftJoin(Getter<T>, ...)` / `innerJoin(Getter<T>, ...)` — type-safe JOIN on `@MappedBy` associations
  - `associate(Getter<T>, ...)` — type-safe `@OneToMany` association loading

## [2.0.0] - 2026-03-29

### Changed
- **BREAKING: Multi-Module Monorepo Structure**
  - Split monolithic library into dedicated modules: `nativsql-core`, `nativsql-mysql`, `nativsql-mysql-commons`, `nativsql-mariadb`, `nativsql-postgres`, `nativsql-oracle`, and `nativsql-test-commons`
  - Consumers now import only the modules they need, reducing dependency bloat
  - Each database module publishes its own artifact and test-fixtures

### Added
- **New Module Structure**
  - `nativsql-core` - Core framework, type system, and utilities (no database-specific code)
  - `nativsql-mysql-commons` - Shared MySQL dialect and type mappers for MySQL/MariaDB
  - `nativsql-mysql` - MySQL-specific implementation with MySQL JDBC driver
  - `nativsql-mariadb` - MariaDB-specific implementation with MariaDB JDBC driver
  - `nativsql-postgres` - PostgreSQL-specific implementation with PostGIS support
  - `nativsql-oracle` - Oracle-specific implementation with Oracle JDBC driver
  - `nativsql-test-commons` - Shared test infrastructure (BaseRepositoryTest, test data types, testcontainers configuration)

### Migration Notes
- Dependency imports change from single `nativsql` to specific modules:
  - Core types: `nativsql-core`
  - MySQL: `nativsql-mysql` (includes nativsql-core, nativsql-mysql-commons)
  - MariaDB: `nativsql-mariadb` (includes nativsql-core, nativsql-mysql-commons)
  - PostgreSQL: `nativsql-postgres` (includes nativsql-core)
  - Oracle: `nativsql-oracle` (includes nativsql-core)
- Test fixtures available per module: `nativsql-{db}-test-fixtures`
- All APIs remain unchanged; migration is purely dependency-related

## [1.7.0] - 2026-03-15

### Added
- **Oracle Database Support**
  - Complete Oracle 20+ JDBC driver integration
  - Full type mapper support for Oracle-specific types
  - Oracle-specific dialect implementation with native type conversions
  - PostGIS Point type support for Oracle spatial queries
  - Comprehensive test suite with Oracle testcontainers integration
  - Oracle-specific repository implementations for all test scenarios

### Supported Databases
- MySQL 8.0+
- MariaDB 11.0+
- PostgreSQL 15+ (with PostGIS support)
- **Oracle 20+** (NEW)

## [1.6.0] - 2026-03-10

### Added
- **Comprehensive SQL Logging and Metrics**
  - `DbOperationLogger` - SQL query execution logging with parameter tracking
  - `ExecutionMetrics` - Performance metrics collection (execution time, row counts, query types)
  - Automatic parameter substitution in logs for easier debugging
  - Integration with Spring Boot logging framework (configurable via `logging.level.ovh.heraud.nativsql`)
  - Performance monitoring per operation type (INSERT, UPDATE, DELETE, SELECT)

### Changed
- Repository operations now emit detailed logs when logging is enabled
- Debug logs include full query text with bound parameters for easier troubleshooting
- Execution metrics tracked automatically for all CRUD operations

## [1.5.0]  - 2026-03-01

### Added
- **Type-Safe Column References** - Use `userRepository.insert(user, User::getEmail, User::getId)` instead of string-based column names for compile-time safety and IDE autocomplete
- **Column Validation** - All repository methods now validate that column lists are not empty, raising `NativSQLException` for better error reporting
- **Single Result Validation** - Methods expecting a single result now validate that queries return at most one element, raising `NativSQLException` if multiple results are found
- **Update Row Validation** - The `update()` method now validates that exactly one row is updated, raising `NativSQLException` if no rows or multiple rows are affected
- **Delete Row Validation** - The `delete()` and `deleteById()` methods now validate that exactly one row is deleted, raising `NativSQLException` if no rows or multiple rows are affected

## [1.4.1]  - 2026-02-2

### Fixed
- Bug with null parameter in native queries

## [1.4.0]  - 2026-02-28

### Changed
- Added type mappers to map any type from DB to java.

## [1.3.3] - 2026-02-09

### Fixed
- **Critical**: RowMapper now properly supports class inheritance - fields from superclasses are now correctly discovered and mapped (fixes `NativSQLException: Property metadata not found for column` when mapping to subclass types)

### Changed
- **BREAKING**: Removed deprecated `Entity<ID>` interface - use `IEntity<ID>` instead

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

GNU General Public License v3 (GPL-3.0) - see [LICENSE](LICENSE) file for details
