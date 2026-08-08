---
name: nativ-sql
description: Used for all database repository development
---

<!-- Source: https://github.com/pascalheraud/nativsql -->

# NativSQL

This skill applies to all work on database repositories: creation, modification, adding methods, queries, etc.

## Library

**NativSQL** (in-house Java library) for PostgreSQL. Repositories extend `PostgresRepository<Entity, Long>` from package `ovh.heraud.nativsql.repository.postgres`.

## Integrating the library

**Maven:**
```xml
<dependency>
    <groupId>ovh.heraud</groupId>
    <artifactId>nativsql-postgres</artifactId>
    <version>2.0.0</version>
</dependency>
```

**Gradle:**
```gradle
implementation 'ovh.heraud:nativsql-postgres:2.0.0'
implementation 'org.springframework.boot:spring-boot-starter-jdbc'
implementation 'com.fasterxml.jackson.core:jackson-databind'
implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'
```

Spring config bean (Jackson must have `JavaTimeModule` registered — required for `LocalDate`/`LocalDateTime` mapping):
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
spring.datasource.url=jdbc:postgresql://localhost:5432/mydb
spring.datasource.username=postgres
spring.datasource.password=secret
```

Domain classes are plain POJOs — no annotation required — implementing `IEntity<ID>` (package `ovh.heraud.nativsql.domain`); fields map camelCase ↔ snake_case automatically.

## Repository structure

```java
@Repository
public class XxxRepository extends PostgresRepository<Xxx, Long> {

    @Override
    @NonNull
    public String getTableName() {
        return "schema.xxx"; // PostgreSQL schema + table name
    }

    @Override
    @NonNull
    protected Class<Xxx> getEntityClass() {
        return Xxx.class;
    }
}
```

## Available NativSQL API

### Basic CRUD

**The property list is mandatory** for all `find*`, `insert`, and `update` methods — passing an empty list throws `NativSQLException`.

```java
insert(entity, "field1", "field2")              // explicit fields (required)

// Updates — always entity-based, NO newUpdateQuery() builder exists
// WHERE is always `id = :id`, read from the @Id-annotated field of the entity — never pass it in the field list
update(entity, Xxx::getField1, Xxx::getField2)  // the listed fields are what gets SET, not the WHERE clause
// For partial updates: create a minimal entity, set its @Id field + changed fields, then call update()
deleteById(id)
deleteByProperty(Xxx::getEmail, value)          // exactly 1 row, throws if 0 or >1
deleteAllByProperty(Xxx::getStatus, value)      // N rows, no count validation
deleteAll(newDeleteQuery()                       // typed DELETE builder
    .whereAndEquals(Xxx::getTenantId, tenantId)
    .whereAndIn(Xxx::getStatus, List.of(...)))
```

### Count / exists

```java
countAll()                                       // count every row in the table
countByProperty(Xxx::getStatus, value)
existsAny()                                       // any row at all
existsByProperty(Xxx::getStatus, value)           // real SQL EXISTS(...), not count() > 0
```

### `Optional<T>` variants

Every nullable single-result `find*` method has an `Optional<T>`-returning twin — same lookup, same query/mapping code path: `findOptionalById`, `findOptionalByProperty`, `findOptionalByPropertyExpression`, and `find(FindQuery, ...)`'s twin `findOptional(FindQuery, ...)`. Use whichever fits the caller's null-handling style.

### General principle: properties to load

All `find*` methods accept as their last parameter a varargs list of getters (`Getter<Xxx>...`) that defines which columns to load. The varargs parameter is named **`properties`**:

```java
@SafeVarargs
public final Xxx findByEmail(String email, Getter<Xxx>... properties) {
    return findByProperty(Xxx::getEmail, email, properties);
}

@SafeVarargs
public final List<Xxx> findAllActive(Getter<Xxx>... properties) {
    return findAll(newFindQuery()
        .select(properties)
        .whereAndEquals(Xxx::getActive, true)
    );
}
```

Import `ovh.heraud.nativsql.util.ReflectionUtils.Getter` for these signatures.

### Simple lookup

```java
findByProperty(Xxx::getEmail, email, properties...)
findAllByProperty(Xxx::getStatus, value, properties...)
findAllByIds(List<Long> ids, properties...)
```

### Query builder (type-safe)

```java
// Single result
find(newFindQuery()
    .select(properties)
    .whereAndEquals(Xxx::getId, id)
);

// List
findAll(newFindQuery()
    .select(properties)                              // pass through caller's properties
    .select(Xxx::getId, Xxx::getName, Xxx::getEmail) // or explicit list if no properties
    .whereAndEquals(Xxx::getStatus, UserStatus.ACTIVE)
    .whereAndIn(Xxx::getStatus, List.of(...))
    .whereAndOperator(Xxx::getAge, Operator.GREATER_OR_EQUAL, 18)  // age >= :age
    .whereAndOperator(Xxx::getScore, Operator.LESS_OR_EQUAL, 100)  // score <= :score
    .whereAndColumnOperator(Xxx::getDeletedAt, ColumnOperator.IS_NULL) // deleted_at IS NULL
    .whereAndRange(Xxx::getBirthDate, RangeOperator.BETWEEN, dateFrom, dateTo)
    .orderBy("createdAt", "DESC")
);
```

> Do not call `.build()` — `find()` and `findAll()` accept the builder directly.

### Ordering

`.orderBy(column, "ASC"|"DESC")` works with a string column and direction. Typed alternatives: `.orderByAsc(Xxx::getField)` / `.orderByDesc(Xxx::getField)`; for a joined entity's column, `.orderByAsc(Xxx::getAssociation, Assoc::getField)` (or the string two-arg form `.orderByAsc("assocName", "field")`).

### Pagination

```java
.limit(pageSize)   // must be > 0
.offset(page * pageSize)  // must be >= 0; offset(0) is a no-op
```
Always combine with `.orderBy*` for deterministic results. Generates SQL:2008 `OFFSET ... FETCH NEXT ... ROWS ONLY`.

### Filtering / ordering on joined columns

After `.leftJoin(...)`/`.innerJoin(...)`, filter on the joined entity with dot-notation or the typed form:

```java
.whereAndEquals("group.name", groupName)                        // string dot-path
.whereAndEquals(Xxx::getGroup, Group::getName, groupName)        // typed, association getter first
```
Same typed/dot-path duality applies to `orderByAsc`/`orderByDesc` on a joined column.

### Report classes (entity + computed SELECT expressions)

`selectExpression(alias, sql[, params])` adds a raw SQL expression (e.g. a correlated subquery) as a SELECT column. Use with `find(query, ResultClass.class)` / `findAll(query, ResultClass.class)` to map into a class that **extends the entity**, adding the computed fields:

```java
public class XxxReport extends Xxx {
    private Long relatedCount;
}

FindQuery<Xxx, Long> query = newFindQuery()
    .select(properties)
    .selectExpression("relatedCount", "(SELECT COUNT(*) FROM related r WHERE r.xxx_id = {{table}}.id)");
return findAll(query, XxxReport.class);
```
`{{table}}` is substituted with the query's own table name. `selectExpression` can also be used alone (no `resultClass`) with an alias matching an existing entity field, to override that field's computed value.

### Framework-managed computed values: `@OnInsert` / `@OnUpdate`

A field annotated `@OnInsert`/`@OnUpdate` is recomputed automatically on every `insert()`/`update()` call, unless the caller already passes that column explicitly — via an injectable `ComputedValueProvider<T>`:

```java
public class DateProvider implements ComputedValueProvider<Instant> {
    @Override public Instant getValue() { return Instant.now(); }
}

@OnInsert(DateProvider.class)
private Instant creationDate;   // set automatically on every insert(...)
@OnUpdate(DateProvider.class)
private Instant updateDate;     // recomputed on every update(...)
```
No default provider exists for either annotation — always specify one matching the field's type (timestamp, version counter, audit user id, ...).

### Comparison operators

Use `whereAndOperator` (import `ovh.heraud.nativsql.util.Operator`) for single-value comparisons with an explicit operator:

```java
.whereAndOperator(Xxx::getAge, Operator.GREATER_OR_EQUAL, 18)  // age >= :age
.whereAndOperator(Xxx::getAge, Operator.LESS_THAN, 65)         // age < :age
.whereAndOperator(Xxx::getScore, Operator.LESS_OR_EQUAL, 100)  // score <= :score
.whereAndOperator(Xxx::getName, Operator.LIKE, "Dup%")         // name LIKE :name
```

Available constants: `EQUALS`, `IN`, `LESS_THAN`, `LESS_OR_EQUAL`, `GREATER_THAN`, `GREATER_OR_EQUAL`, `NOT_EQUALS`, `LIKE`.

### NULL checks

```java
.whereAndColumnOperator(Xxx::getDeletedAt, ColumnOperator.IS_NULL)     // deleted_at IS NULL
.whereAndColumnOperator(Xxx::getDeletedAt, ColumnOperator.IS_NOT_NULL) // deleted_at IS NOT NULL
```

Import `ovh.heraud.nativsql.util.ColumnOperator`.

### Range (BETWEEN)

```java
.whereAndRange(Xxx::getBirthDate, RangeOperator.BETWEEN, dateFrom, dateTo)
// → birth_date BETWEEN :birthDateLow AND :birthDateHigh
```

Import `ovh.heraud.nativsql.util.RangeOperator`.

### Custom expressions (composite types only)

`whereExpression(expression, paramName, value)` is reserved for PostgreSQL composite types — it always generates `expression = :paramName`. **Do not use it for operators like `<=` or `>`**, use `whereAndOperator` instead.

```java
.whereExpression("(address).city", "city", "Paris")  // (address).city = :city
```

### JOIN for @MappedBy associations (ToOne)

Use `.innerJoin()` (NOT NULL foreign key) or `.leftJoin()` (nullable foreign key) on a `FindQuery`. The field on the main entity must be annotated with `@MappedBy`.

When the repository method must also load the joined association with caller-configurable columns, Java does not allow two varargs parameters. Convention: the joined association columns are passed as a **`String[]`** placed before the main varargs `Getter<T>... properties` (which stays last). Column names follow the camelCase convention (e.g. `"externalId"`, `"name"`).

```java
// Entity with @MappedBy
public class Order implements IEntity<Long> {
    @MappedBy(value = "customerId", repository = CustomerRepository.class)
    private Customer customer;
    // ...
}

// Repository
@SafeVarargs
public final List<Order> findByStatus(OrderStatus status,
        String[] customerColumns, Getter<Order>... properties) {
    return findAll(newFindQuery()
        .select(properties)
        .innerJoin(Order::getCustomer, customerColumns)
        .whereAndEquals(Order::getStatus, status));
}

// Call site — String array for the association columns, varargs for the main entity
repository.findByStatus(OrderStatus.PENDING,
    new String[]{"name", "email"},
    Order::getId, Order::getCreatedAt);
```

### Native SQL queries (for complex cases)

```java
// Single result, different type (columns fixed by SQL)
findExternal(sql, params, ExternalClass.class)

// List of a different type with named parameters
Map<String, Object> params = new HashMap<>();
params.put("myParam", value);
findAllExternal(SQL_QUERY, params, ExternalClass.class)
```

Native SQL parameters use `:paramName` syntax.

On PostgreSQL, a bind parameter used without being compared to a typed column (e.g. `WHERE :flag IS NULL OR NOT :flag`) can fail with `could not determine data type of parameter` unless NativSQL can infer its type — from a matching entity field, or from the runtime class of a non-null value. For a `null` value with no matching entity field, wrap it with `NullableParam.of(SomeType.class)` (package `ovh.heraud.nativsql.repository`) instead of passing `null` directly, so NativSQL can inject the cast.

### Formatting raw SQL strings

Applies everywhere a SQL string literal is written in Java — repository classes under `src/main`,
test-only repositories/helpers (`mocktest`), and `AuxiliairesTestDataBuilder`/`jdbcTemplate()` calls in
tests alike:

- **Multi-line SQL is always a text block** (`""" ... """`), never string concatenation
  (`"SELECT ... " + "FROM ... "`) — a text block reads as one contiguous statement instead of a chain
  of `+`-joined fragments, and needs no manual trailing-space bookkeeping between lines.
- **SQL keywords are written in uppercase** (`SELECT`, `FROM`, `WHERE`, `JOIN`, `ORDER BY`, `LIMIT`,
  `INSERT INTO`, `UPDATE`, `GROUP BY`, etc.), including in single-line queries — keeps keywords visually
  distinct from table/column identifiers at a glance.

### Optional filter parameters in raw SQL: `:param IS NULL OR ...`

When a raw SQL query (`findAllExternal`/`findExternal`) takes an **optional** filter parameter — apply
the condition only if the caller passed a non-null value, otherwise match everything — use a single
consistent idiom regardless of the parameter's type:

```sql
AND (:filterParam::bigint IS NULL OR column != :filterParam::bigint)      -- non-boolean (id, string, ...)
AND (:filterFlag::boolean IS NULL OR NOT :filterFlag::boolean OR column)  -- boolean "requirement" flag
```

Don't reach for `COALESCE(:filterFlag, false)` for the boolean case — `NOT COALESCE(:filterFlag, false)
OR (column AND :filterFlag)` is logically equivalent but mixes two different idioms in the same query
for no benefit. `:filterFlag IS NULL OR NOT :filterFlag OR column` reads the same way as the
non-boolean form and says directly "skip this condition when the caller didn't ask for it" — one idiom
to recognize regardless of the parameter's type. Don't write `:filterFlag = false` either — `:filterFlag`
is already a `Boolean` on the Java side; testing it directly (`NOT :filterFlag`) is what "the SGBD
supports booleans natively" actually buys you, a literal `= false` comparison is redundant.

**Always cast the parameter explicitly (`::bigint`, `::boolean`, ...) when it appears only in an
`IS NULL`/`NOT` position with no other already-typed operand next to it.** With prepared statements,
PostgreSQL determines each `$n` placeholder's type from the query text alone, before it sees any bound
values — a parameter that only ever appears in `:param IS NULL` or `NOT :param` gives the planner
nothing to infer a type from, and the query fails at execution with `could not determine data type of
parameter $n`. The old `COALESCE(:filterFlag, false)` form accidentally avoided this because the
literal `false` gave Postgres a type hint; switching to the `IS NULL`/`NOT` form must add the cast back
explicitly. The cast is a binding-time annotation for Postgres, not a runtime conversion — the value
sent by the JDBC driver is already the correct Java type either way.

## Optional entity annotations

```java
@Json                                    // POJO serialized as JSON/JSONB
@MappedBy(value = "groupId", repository = GroupRepository.class)
@OneToMany(mappedBy = "userId", repository = ContactInfoRepository.class)
@SqlType("my_enum_type")                 // PostgreSQL cast for enums — import ovh.heraud.nativsql.annotation.type.SqlType
@Encrypted @CryptAlgo(...) @CryptKeyProvider(...)
```

Entities must implement `IEntity<ID>` (package `ovh.heraud.nativsql.domain`).

## Automatic mapping

- camelCase Java ↔ snake_case SQL automatic (e.g. `addressCity` ↔ `address_city`)
- Supported types: `String`, `Long`, `Integer`, `Boolean`, `LocalDate`, `LocalDateTime`, `UUID`, `BigDecimal`, `byte[]`, enums, PostGIS `Point`
- A `Boolean`-typed field mapped from a `NOT NULL` SQL column is always populated (never `null`) once loaded via `find*`/`select(properties)` — safe to use directly in a boolean condition (`if (entity.getFlag() && ...)`) without a defensive `Boolean.TRUE.equals(...)` wrapper. Reserve `Boolean.TRUE.equals(...)` for `Boolean` fields backed by a nullable column, where `null` is a real, distinct state.

## Testing (Testcontainers)

Repository/integration tests extend the dialect-specific base class published in the dialect
module's `testFixtures` — `PostgresBaseRepositoryTest` for PostgreSQL. It spins up a cached
Testcontainers PostgreSQL container and applies a schema script; each test method runs inside a
transaction rolled back automatically after the test, so no manual cleanup and no cross-test data
leakage:

```java
public abstract class XxxRepositoryTest extends PostgresBaseRepositoryTest {
    @Override
    protected String getScriptPath() {
        return "db/schema-test.sql"; // classpath-relative schema script
    }
}

class XxxRepositoryIT extends XxxRepositoryTest {
    XxxRepository repository = new XxxRepository();

    @Test
    void findByEmail_returnsMatchingEntity() {
        repository.insert(new Xxx(...), Xxx::getEmail, ...);
        Xxx found = repository.findByEmail("a@b.com", Xxx::getEmail);
        assertThat(found).isNotNull();
    }
}
```

### DataSource configuration — no Spring context involved

`PostgresBaseRepositoryTest` is **plain JUnit 5** (`@ExtendWith(SpringExtension.class)` only for
`@Autowired`-free field injection support — there is no `@SpringBootTest`/`ApplicationContext`).
Before each test it:

1. Starts (or reuses) a cached `postgis/postgis:<version>-3.3` Testcontainers container, keyed by
   `vendor:version:schemaHash` — so tests sharing the same schema script share one container
   across the whole run instead of paying startup cost per test.
2. Wraps the container in a plain `DriverManagerDataSource` (host/port/db/user/pass all taken from
   the running container — no manual `application.properties`/datasource config needed in tests).
3. Reflectively scans the test class's own fields for repository instances and calls
   `setDataSource(...)` on each — this is why the repository is a plain `new XxxRepository()`
   field on the test class, not something pulled from a Spring context.

Override `getDatabaseVersion()` to pin a different PostGIS/Postgres image tag; override
`createContainer(String schemaHash)` to change the container setup entirely (extra init scripts, a
non-default image, ...).

### Dialect configuration — a repository concern, not a test concern

The `DatabaseDialect` is **not** selected by the test base class at all — it comes from
`getDatabaseDialectInstance()`, the same method every repository already implements/inherits
(`PostgresRepository` defaults it to `PostgresDialect`; override it in the repository class itself
to use `PostgresPostGISDialect` or another dialect). Tests exercise whatever dialect the repository
under test is wired for; there is no separate "test dialect" to configure.

### Unit-style (rollback) vs e2e (no rollback)

- **Default — unit/integration style:** each test runs inside a transaction that's rolled back
  automatically afterward (`rollbackTransactionAfterEachTest()` returns `true` by default) — no
  manual cleanup, no data leaking between tests, use this for ordinary repository tests.
- **E2E style:** when a scenario drives a real app process/container with its **own** JDBC
  connection(s) to the same database, that connection can't see data seeded inside the test's
  wrapping transaction until it commits — so the default rollback makes seeded data invisible to
  the app under test. Override `rollbackTransactionAfterEachTest()` to return `false` to commit
  seeded data instead; in that mode nothing cleans up automatically, so reseeding/cleanup between
  scenarios becomes the project's own responsibility (typically a delete+reseed test-data builder
  called at the start of each scenario). See `doc/EndToEndTesting.md` in the NativSQL repo for a
  fully worked setup.

Add the test-fixtures dependency (Gradle — published as a separate `-test-fixtures` artifact, not a Maven classifier):
```gradle
testImplementation 'ovh.heraud:nativsql-postgres-test-fixtures:2.0.0'
testImplementation 'org.testcontainers:postgresql'
testImplementation 'org.testcontainers:junit-jupiter'
```

## Rules

1. Always annotate `@NonNull` on `getTableName()` and `getEntityClass()`
2. Always annotate `@SafeVarargs` on varargs `Getter<T>...` methods
3. All public `find*` methods expose a `Getter<T>... properties` parameter as the last argument
4. **Do not add `insert` or `update` wrapper methods in repositories** — callers call `repository.insert(entity, Xxx::getField1, ...)` and `repository.updateById(id, entity, Xxx::getField1, ...)` directly
5. **Column lists belong in the caller, not in the repository.** Repository methods must never hard-code which columns to load — always expose a `@SafeVarargs Getter<T>... properties` varargs parameter and pass it to `.select(properties)`. Each caller decides which fields it needs. Exception: queries whose column set is fixed by their nature (e.g. GROUP BY aggregate, JOIN projection returning a dedicated result class).
6. Prefer method references (`Xxx::getField`) over strings for the query builder
7. For complex SQL queries (geo, UNION, aggregates, JOIN with WHERE on joined table), use `findAllExternal` with a `private static final String` constant
8. **All public repository methods must have a Javadoc comment** that describes the contract: what it queries/does, parameters, and return value. One sentence is enough for simple lookups; more detail for complex queries (filters, ordering, native SQL).
