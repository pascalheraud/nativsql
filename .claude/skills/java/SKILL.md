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
