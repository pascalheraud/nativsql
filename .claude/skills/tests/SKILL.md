---
name: tests
description: Conventions for writing and running tests in the NativSQL project
---

Always write tests for new features. Place tests alongside the module they cover (e.g. `nativsql-core/src/test/`). A feature without tests is not complete.

## Two levels of tests

Every feature requires **both** levels:

**Unit tests** (`nativsql-core/src/test/`) — fast, no DB, verify SQL generation and parameter extraction. Mocks (`@Mock GenericRepository`) are acceptable here because the goal is to test the builder logic in isolation.

**Integration tests** (`nativsql-postgres/src/test/`) — run against a real PostgreSQL container (Testcontainers). Never use mocks for integration tests. Each test inserts known data, executes the query under test against the real DB, and asserts the returned rows. Cover at minimum one test per new WHERE operator or query capability.

Integration tests extend `PostgresRepositoryTest`. The query methods must be implemented inside `PostgresUserRepository` (protected `newFindQuery()` / `findAll()` / `find()` are only accessible from subclasses). Add a dedicated method to the repository for each integration test scenario.

Never run tests without asking first via `AskUserQuestion`. The user decides whether and when to run them.

All test code (class names, method names, comments) must be written in English. Test methods should be descriptive in snake case. Do not use `@Nested` inner classes — keep all test methods flat in the test class so they run easily in VS Code.

Structure each test method with Given / When / Then sections, each introduced by a short comment describing what it does:

```java
class StringTypeMapperTest {

    @Test
    void fromValue_returns_string_when_value_is_string() {
        // Given: a raw string value
        Object raw = "hello";

        // When: mapping the value
        String result = mapper.fromValue(raw, DbDataType.VARCHAR, null, Map.of());

        // Then: the original string is returned unchanged
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void fromValue_throws_conversion_exception_when_value_is_not_a_string() {
        // Given: a non-string value
        Object raw = 42;

        // When / Then: mapping throws a ConversionException
        assertThatThrownBy(() -> mapper.fromValue(raw, DbDataType.VARCHAR, null, Map.of()))
            .isInstanceOf(ConversionException.class);
    }
}
```
