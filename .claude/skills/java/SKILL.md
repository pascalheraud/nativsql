---
name: java
description: Java coding conventions, architecture patterns, and verification for the NativSQL project
---

## Coding conventions

### fromValue

- Always `throws ConversionException` (never `NativSQLException`)
- On failure: `throw new ConversionException(TargetType.class)` or `new ConversionException(TargetType.class, cause)`
- **Never log or include a value in any message** — the superclass handles formatting and automatically masks encrypted/hashed values as `#######`
- Unused `FieldAccessor<?> fieldAccessor` → annotate `@SuppressWarnings("unused")`
- JSON/Enum mappers: use `fieldAccessor` to derive type at call time

### toDatabaseValue

- Always `throws ConversionException`
- Unsupported `dataType` → `throw new ConversionException(dataType.name())`
- Never throw `NativSQLException` here
- `null` is handled by `AbstractTypeMapper.toDatabase()` — never receives null
- **Never log or include the value in any message** — the superclass masks encrypted/hashed values as `#######`

### Error propagation

- `ConversionException` is caught once in `AbstractTypeMapper` and wrapped into a `NativSQLException` with full context (column name, index, value masked as `#######` for encrypted fields)
- Direct `ITypeMapper` implementors (not extending `AbstractTypeMapper`) must not throw `NativSQLException` from `fromValue`

### Stateless mappers

- Mappers should be stateless where possible
- JSON mappers (`GenericJSONTypeMapper`, `PostgreJSONTypeMapper`) are stateless: they use `fieldAccessor.getField().getGenericType()` at call time, cached in a static `ConcurrentHashMap<Field, JavaType>`
- Enum mapper (`EnumStringMapper`) stores `Class<E>` in constructor (enum class is fixed per field)

### `toDatabaseValue` for IDENTITY

Remove `IDENTITY` case — let it fall into `default → throw new ConversionException(dataType.name())`

### Catches

- Consolidate multiple catches into one outer try-catch per method
- `new BigDecimal(double/float)` can throw `NumberFormatException` for NaN/Infinity — catch it
- `LocalDate.parse()` / `LocalDateTime.parse()` can throw `DateTimeParseException` — catch it

## Adding a new mapper

1. Create `XxxTypeMapper extends AbstractTypeMapper<Xxx>` in `nativsql-core/.../db/generic/mapper/`
2. Implement `fromValue` (throws `ConversionException`)
3. Implement `toDatabaseValue` (throws `ConversionException`)
4. Add factory method `ITypeMapper<Xxx> getXxxMapper()` to `DatabaseDialect` (abstract)
5. Implement in `GenericDialect`: `return new XxxTypeMapper();`
6. Add delegation in `AbstractChainedDialect`: `return nextDialect.getXxxMapper();`
7. Register in `GenericDialect.getMapperForType()`: `if (targetType == Xxx.class) return (ITypeMapper<T>) getXxxMapper();`

## Adding a new annotation

1. Create annotation in `nativsql-core/.../annotation/`
2. If it contributes params to `TypeInfo`, add a `TypeParamKey` entry
3. Scan it in `AnnotationManager.scanCryptParams()` (or a dedicated scan method)
4. If it uses `@Inject`, the bean is resolved automatically via `AnnotationManager.resolveBean()`

## Imports

Never use fully-qualified class names (FQN) inline in code. Always add an `import` statement and use the short name:

```java
// Bad
if (mapper instanceof ovh.heraud.nativsql.mapper.AbstractTypeMapper<ID> m) { ... }

// Good
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;
...
if (mapper instanceof AbstractTypeMapper<ID> m) { ... }
```

- Do not annotate primitive types (they cannot be null)

## Repository query encapsulation

`FindQuery`, `DeleteQuery`, `CountQuery`, `ExistsQuery` (and their protected factories
`newFindQuery()`/`newDeleteQuery()`/`newCountQuery()`/`newExistsQuery()`, and the public static
`FindQuery.of(repository)`) are repository-internal implementation details, not application-facing
API. Building and executing one — the WHERE conditions, joins, associations, ordering, limit/offset
— is always done *inside* a named method on the repository subclass, never by calling code (a
service, a controller, another repository) that receives or assembles the query object itself.

The one exception is the **column list**: it is still a *parameter* of that named method
(`String... columns` / `Getter<T>... columns`, or `String[]` when a method needs more than one
column list — see the ordering rule below), supplied by the caller, exactly like
`findByProperty`/`findAllByProperty` already do — the repository builds the query around
caller-supplied columns, it does not decide them. This avoids fetching columns a given caller
doesn't need. Hard-coding the column list inside the repository is only reasonable when it's
intrinsic to the query's shape (e.g. `selectExpression(...)`-based statistics/report projections
where the computed column always accompanies a fixed base-column set).

Never use `List<String>` for a column-list *parameter* on a repository method — a `String[]`
argument is directly assignable to a `String... columns` parameter, so wrapping it in `List.of(...)`
before passing it to `FindQuery`/`.select(...)`/`.leftJoin(...)`/`.associate(...)` is unnecessary
(those `FindQuery` methods themselves only expose `String...`/`Getter<T>...` overloads — the
`List<String>` overloads were removed as dead API). `List<String>` remains the right type for
*internal* storage (the `columns` field on `FindQuery`, `Association`, `Join`, etc.) — this rule is
only about the public parameter shape callers see.

```java
// Wrong — the query object itself leaks into calling code
FindQuery<User, Long> query = FindQuery.of(userRepository)
        .select("id", "firstName", "email")
        .whereAndEquals("status", UserStatus.ACTIVE);
List<User> users = userRepository.findAll(query);

// Wrong — encapsulated, but the repository hard-codes the columns for every caller
public List<User> findActiveUsers() {
    return findAll(newFindQuery()
            .select("id", "firstName", "email")
            .whereAndEquals("status", UserStatus.ACTIVE));
}

// Right — query construction/execution stays inside the repository,
// but the caller still picks its own columns
public List<User> findActiveUsers(String... columns) {
    return findAll(newFindQuery()
            .select(columns)
            .whereAndEquals("status", UserStatus.ACTIVE));
}

List<User> users = userRepository.findActiveUsers("id", "firstName", "email");
```

When a method needs several column lists — the repository's own entity plus one or more
joined/associated entities — the **last parameter is always the `String... columns` varargs for
the repository's own entity**; every other column list (joined/associated entity) is an earlier
`String[]` parameter — only the trailing, entity-owning list is a vararg, matching the existing
`findByProperty(..., OrderBy orderBy, String... columns)`-style ordering:

```java
public User findByIdWithGroup(Long userId, String[] groupColumns, String... columns) {
    return find(newFindQuery()
            .select(columns)
            .whereAndEquals("id", userId)
            .leftJoin("group", List.of(groupColumns)));
}
```

**Why:** the join/association shape and WHERE conditions are part of the repository's internal
query-building logic and must not leak — that was explicitly rejected by the user when a generic
public passthrough appeared during the CountQuery feature (issue #87). The column list is
different: it doesn't leak SQL shape, and hard-coding it forces every caller to fetch the same
fixed set of columns, which can mean unnecessary columns are always loaded or the method has to be
duplicated per use case.

**How to apply:** add a narrowly-scoped public method named after what it returns/does (e.g.
`findActiveUsers(String... columns)`, `findUserActivityReports()`, `countByStatuses(...)`) that
builds and executes the query internally, taking the column list as a parameter unless the query
is a fixed-shape projection. This applies everywhere a query builder appears — production code,
docs, and tests alike (tests already codify the builder-encapsulation half in
`.claude/skills/tests/SKILL.md`).

## Interface design

Do not use `default` methods in interfaces unless explicitly requested. Abstract behavior belongs in abstract classes (`AbstractTypeMapper`, etc.), not in interface defaults.

## Code formatting

No automatic formatting is wired up yet. Two responsibilities:

**Agent (best-effort):** write Java that matches the VS Code Java formatter style:

- 4-space indentation
- Braces on the same line (`{` at end of statement)
- Imports ordered: `java`, `javax`, `jakarta`, then third-party, then `#` (statics) — as configured in `.vscode/settings.json`
- One blank line between methods

**Developer reminder:** after any file is modified, apply the VS Code formatter manually with **Shift+Alt+F** before committing.

## Architecture documentation

After any Java code change, update `ARCHITECTURE.md` if the change affects:

- the module structure
- the mapper hierarchy or key signatures
- the dialect chain
- the type detection logic

If work is guided by a feature plan, the last step of the plan must explicitly include: "Update ARCHITECTURE.md if the architecture has changed."

## Logging

- All queries are logged (at INFO level or equivalent).
- Query parameters are logged at DEBUG level.
- **Never log an encrypted parameter** — use `#######` as a placeholder, as the superclass already does for mapper errors.

## Encrypted fields

- `WHERE` clauses on encrypted fields are not supported and must never be generated.

## Security

- **Never build queries by concatenating user-supplied values** — always use parameterized queries (prepared statements / bind variables). Concatenating user input into SQL is a SQL injection vulnerability.

## Tests

Every new feature must be covered by tests added in a dedicated sub-module. Tests run against a real database via a container (not mocks). See the [[tests]] skill for test structure and naming conventions.

## Verification

Always run after changes:

```bash
./gradlew compileJava   # verify compilation
./gradlew test          # run tests (ask user first — see tests skill)
./gradlew build         # full build
```

Fix all compile errors before reporting the feature as complete.
