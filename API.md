# NativSQL API Documentation

Complete API reference for NativSQL query builder and repository framework.

## Table of Contents

- [GenericRepository](#genericrepository)
- [FindQuery](#findquery)
- [Condition](#condition)
- [Operator](#operator)
- [RowMapperFactory](#rowmapperfactory)
- [Type Mappers](#type-mappers)

## GenericRepository

Base class for all repository implementations. Provides CRUD operations and query building.

### Type Parameters

```java
public abstract class GenericRepository<T extends Entity<ID>, ID>
```

- `T`: The entity type
- `ID`: The type of the entity's ID field

### Key Methods

#### Insert Operations

```java
/**
 * Inserts an entity with all non-null fields.
 */
void insert(T entity);

/**
 * Inserts an entity with specified fields only.
 *
 * @param entity the entity to insert
 * @param columns the column names to insert (in camelCase)
 */
void insert(T entity, String... columns);
```

#### Update Operations

```java
/**
 * Updates an entity by ID with all non-null fields.
 */
void update(T entity, String idFieldName);

/**
 * Updates an entity by ID with specified fields.
 *
 * @param entity the entity to update
 * @param idFieldName the name of the ID field (used in WHERE clause)
 * @param columns the column names to update
 */
void update(T entity, String idFieldName, String... columns);
```

#### Delete Operations

```java
/**
 * Deletes an entity by a specific field value.
 *
 * @param fieldName the field name to match (in camelCase)
 * @param value the value to match
 */
void delete(String fieldName, Object value);

/**
 * Deletes an entity by ID.
 *
 * @param id the entity ID
 */
void deleteById(ID id);
```

#### Find Operations

```java
/**
 * Finds a single entity by property value.
 *
 * @param property the property name (in camelCase)
 * @param value the value to match
 * @param columns the columns to retrieve
 * @return the entity or null if not found
 */
T findByProperty(String property, Object value, String... columns);

/**
 * Finds all entities matching a property value.
 * Uses IN clause for batch efficiency.
 *
 * @param property the property name
 * @param values list of values to match
 * @param columns the columns to retrieve
 * @return list of matching entities
 */
List<T> findAllByProperty(String property, List<?> values, String... columns);

/**
 * Finds all entities by their IDs.
 *
 * @param ids list of entity IDs
 * @param columns the columns to retrieve
 * @return list of matching entities
 */
List<T> findAllByIds(List<?> ids, String... columns);

/**
 * Finds a single entity using a FindQuery.
 *
 * @param query the FindQuery built with the builder
 * @return the entity or null if not found
 */
T find(FindQuery<T, ID> query);

/**
 * Finds all entities using a FindQuery.
 *
 * @param query the FindQuery built with the builder
 * @return list of matching entities
 */
List<T> findAll(FindQuery<T, ID> query);
```

#### Abstract Methods (Must Implement)

```java
/**
 * Returns the database table name.
 */
protected abstract String getTableName();

/**
 * Returns the entity class type.
 */
protected abstract Class<T> getEntityClass();

/**
 * Returns the database dialect for this repository.
 */
protected abstract DatabaseDialect getDatabaseDialectInstance();
```

## FindQuery

Type-safe query builder for constructing SELECT queries.

### Creating a FindQuery

```java
FindQuery<User, Long> query = repository.newFindQuery()
    .select("id", "firstName", "email", "status")
    .whereAndEquals("status", UserStatus.ACTIVE)
    .orderBy("firstName", "ASC")
    .build();

List<User> users = repository.findAll(query);
```

### Builder Methods

#### select(String... columns)

Specifies which columns to retrieve.

```java
.select("id", "firstName", "email")
```

#### whereAndEquals(String column, Object value)

Adds an equality condition (WHERE column = value).

```java
.whereAndEquals("status", UserStatus.ACTIVE)
.whereAndEquals("email", "john@example.com")
```

Multiple conditions are combined with AND.

#### whereAndIn(String column, List<?> values)

Adds an IN condition (WHERE column IN (...)).

```java
.whereAndIn("status", List.of(UserStatus.ACTIVE, UserStatus.SUSPENDED))
.whereAndIn("id", List.of(1L, 2L, 3L))
```

#### leftJoin(String property, List<String> columns)

Adds a LEFT JOIN for a @MappedBy associated entity.

```java
.leftJoin("group", List.of("id", "name", "description"))
```

#### innerJoin(String property, List<String> columns)

Adds an INNER JOIN for a @MappedBy associated entity.

```java
.innerJoin("group", List.of("id", "name"))
```

#### associate(String property, List<String> columns)

Loads a @OneToMany association for the entity.

```java
.associate("contacts", List.of("id", "type", "value"))
```

#### orderBy(String column, String direction)

Adds an ORDER BY clause. Direction is "ASC" or "DESC".

```java
.orderBy("firstName", "ASC")
.orderBy("createdAt", "DESC")
```

Multiple orderBy calls will create compound sorting.

#### build()

Finalizes the query. Returns a FindQuery ready for execution.

```java
FindQuery<User, Long> query = builder.build();
```

## Condition

Represents a single WHERE clause condition with column, operator, and value.

### Constructor

```java
public Condition(String column, Operator operator, Object value)
```

### Methods

```java
String getColumn()
Operator getOperator()
Object getValue()
```

## Operator

Enum of SQL operators for WHERE conditions. Each operator includes a strategy for generating SQL expressions.

### Defined Operators

#### EQUALS

```java
Operator.EQUALS  // Generates: "column = :paramName"
```

#### IN

```java
Operator.IN  // Generates: "column IN (:paramName)"
```

### Adding Custom Operators

To add a new operator (e.g., LIKE):

```java
public enum Operator {
    EQUALS("=", (col, param) -> col + " = :" + param),
    IN("IN", (col, param) -> col + " IN (:" + param + ")"),
    LIKE("LIKE", (col, param) -> col + " LIKE :" + param);  // New operator

    private final String sql;
    private final WhereExpressionBuilder expressionBuilder;

    // Constructor and methods...
}
```

## RowMapperFactory

Factory for creating and caching `GenericRowMapper` instances.

### Methods

```java
/**
 * Gets or creates a row mapper for the specified class.
 * Results are cached per class to improve performance.
 */
<T> GenericRowMapper<T> getRowMapper(Class<T> clazz, DatabaseDialect dialect);
```

## Type Mappers

### ITypeMapper Interface

```java
public interface ITypeMapper<T> {
    /**
     * Converts a database value to a Java value.
     *
     * @param dbValue the database value (e.g., String, Integer, custom type)
     * @return the Java value (e.g., enum, custom object)
     */
    T toJava(Object dbValue);

    /**
     * Converts a Java value to a database value.
     *
     * @param javaValue the Java value
     * @return the database value
     */
    Object toDatabase(T javaValue);
}
```

### Built-in Mappers

#### EnumStringMapper

Maps Java enums to/from database string values.

```java
// For enum stored as VARCHAR
public enum UserStatus {
    ACTIVE,      // -> "ACTIVE" in database
    INACTIVE,    // -> "INACTIVE" in database
    SUSPENDED    // -> "SUSPENDED" in database
}
```

#### DefaultTypeMapper

Handles standard Java types (String, Integer, Long, LocalDateTime, etc.) with database conversions.

### Custom Type Mappers

Implement `ITypeMapper` to handle custom types:

```java
public class EmailMapper implements ITypeMapper<Email> {
    @Override
    public Email toJava(Object dbValue) {
        if (dbValue == null) return null;
        return new Email((String) dbValue);
    }

    @Override
    public Object toDatabase(Email javaValue) {
        return javaValue == null ? null : javaValue.getValue();
    }
}
```

Register in your configuration:

```java
databaseDialect.registerMapper(Email.class, new EmailMapper());
```

## Database Dialects

NativSQL supports multiple database dialects with dialect-specific type mapping and SQL generation.

### Supported Dialects

- **DefaultDialect**: MySQL 8.0+, MariaDB 11+
- **PostgresDialect**: PostgreSQL 15+

### DatabaseDialect Interface

```java
public interface DatabaseDialect {
    /**
     * Converts a Java identifier (camelCase) to database identifier (snake_case).
     */
    String javaToDBIdentifier(String javaName);

    /**
     * Gets or creates a type mapper for the specified class.
     */
    <T> ITypeMapper<T> getMapper(Class<T> type);

    /**
     * Registers a custom type mapper for a specific class.
     */
    <T> void registerMapper(Class<T> type, ITypeMapper<T> mapper);
}
```

## Entity Interface

Base interface for all entities.

```java
public interface Entity<ID> {
    /**
     * Gets the entity's identifier.
     */
    ID getId();

    /**
     * Sets the entity's identifier.
     */
    void setId(ID id);
}
```

Example implementation:

```java
public class User implements Entity<Long> {
    private Long id;

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    // Other fields and methods...
}
```

## Annotations

### @MappedBy

Maps a field to a related entity loaded via JOIN.

```java
@MappedBy(value = "groupId", repository = GroupRepository.class)
private Group group;  // Loaded via LEFT/INNER JOIN
```

Parameters:
- `value`: The foreign key column name (in camelCase)
- `repository`: The repository class for the related entity

### @OneToMany

Maps a field to a collection of related entities loaded via batch query.

```java
@OneToMany(mappedBy = "userId", repository = ContactInfoRepository.class)
private List<ContactInfo> contacts;  // Loaded separately with IN clause
```

Parameters:
- `mappedBy`: The foreign key field name in the related entity
- `repository`: The repository class for related entities

## Examples

### Complete User Repository

```java
import ovh.heraud.nativsql.repository.GenericRepository;

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

    public List<User> findActiveUsers(String... columns) {
        return findAll(
            newFindQuery()
                .select(columns)
                .whereAndEquals("status", UserStatus.ACTIVE)
                .orderBy("firstName", "ASC")
                .build()
        );
    }

    public User findByEmail(String email, String... columns) {
        return findByProperty("email", email, columns);
    }

    public List<User> findByIds(List<Long> ids, String... columns) {
        return findAllByIds(ids, columns);
    }

    public User getUserWithGroup(Long userId) {
        return find(
            newFindQuery()
                .select("id", "firstName", "email")
                .whereAndEquals("id", userId)
                .leftJoin("group", List.of("id", "name"))
                .build()
        );
    }
}
```

## Performance Considerations

1. **Specify Columns** - Only select columns you need, not `SELECT *`
2. **Use Batch Methods** - Use `findAllByIds()` or `findAllByProperty()` for multiple entities
3. **Avoid N+1 Queries** - Use JOINs or batch association loading
4. **Cache Row Mappers** - RowMapperFactory caches mappers per class
5. **Use Transactions** - Wrap multiple operations in `@Transactional`

## Error Handling

Common exceptions:

```java
try {
    User user = userRepository.find(query);
} catch (NativSQLException e) {
    // NativSQL-specific errors
    log.error("Query failed: {}", e.getMessage());
}
```

## See Also

- [README.md](README.md) - Quick start guide
- [CONTRIBUTING.md](.github/CONTRIBUTING.md) - Contributing guidelines
- [CHANGELOG.md](CHANGELOG.md) - Version history
