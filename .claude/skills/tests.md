---
name: tests
description: Conventions for writing and running tests in the NativSQL project
---

Always write tests for new features. Place tests alongside the module they cover (e.g. `nativsql-core/src/test/`). A feature without tests is not complete.

Never run tests without asking first via `AskUserQuestion`. The user decides whether and when to run them.

Organize test classes using `@Nested` inner classes without `@DisplayName` to group related scenarios clearly. All test code (class names, method names, display names, comments) must be written in English. The full path of the test class should read like a sentence describing the behavior under test. The name of @Nested and test methods should be descriptive in snake case.

Initialization shared across all tests in a `@Nested` class belongs in a `@BeforeEach` of that nested class, not in the outer class or duplicated in each test.

Structure each test method with Given / When / Then sections, each introduced by a short comment describing what it does:

```java
class StringTypeMapperTest {

    @Nested
    class fromValue {

        @Test
        void returns_string_when_value_is_string() {
            // Given: a raw string value
            Object raw = "hello";

            // When: mapping the value
            String result = mapper.fromValue(raw, DbDataType.VARCHAR, null, Map.of());

            // Then: the original string is returned unchanged
            assertThat(result).isEqualTo("hello");
        }

        @Test
        void throws_conversion_exception_when_value_is_not_a_string() {
            // Given: a non-string value
            Object raw = 42;

            // When / Then: mapping throws a ConversionException
            assertThatThrownBy(() -> mapper.fromValue(raw, DbDataType.VARCHAR, null, Map.of()))
                .isInstanceOf(ConversionException.class);
        }
    }

    @Nested
    class ToDatabaseValue { ... }
}
```
