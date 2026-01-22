# Contributing to NativSQL

We welcome contributions to NativSQL! This document provides guidelines for contributing to the project.

## Getting Started

### Prerequisites

- Java 21+
- Gradle 8.0+
- Docker (for running services locally)
- Git

### Setup Development Environment

1. Clone the repository:
   ```bash
   git clone https://github.com/pascalheraud/nativsql.git
   cd nativsql
   ```

2. Start the database services:
   ```bash
   docker-compose up -d
   ```

3. Build the project:
   ```bash
   ./gradlew build
   ```

## Development Workflow

### Creating a Feature Branch

```bash
git checkout -b feature/my-feature
# or
git checkout -b fix/my-bug
```

### Running Tests Locally

```bash
# All tests
./gradlew test

# Specific test class
./gradlew test --tests MySQLUserRepositoryTest

# Specific test method
./gradlew test --tests MySQLUserRepositoryTest.testInsertUser
```

### Code Quality

Before submitting a PR, ensure:

```bash
# Format code
./gradlew spotlessApply

# Run full build
./gradlew build

# Check test coverage
./gradlew jacocoTestReport
```

## Commit Guidelines

### Commit Message Format

Follow the conventional commits format:

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types:**
- `feat`: A new feature
- `fix`: A bug fix
- `refactor`: Code refactoring without feature changes
- `perf`: Performance improvements
- `test`: Adding or updating tests
- `docs`: Documentation changes
- `chore`: Build process, dependencies, etc.

**Examples:**
```
feat(query): add LIKE operator support to WHERE clause

Add WhereExpressionBuilder interface for extensible WHERE expression generation.
Supports LIKE operator with pattern matching.

Closes #123
```

```
fix(mapper): handle recursive type mapping in RowMapperFactory

Replace computeIfAbsent with direct cache get/put to avoid recursive
update exceptions in ConcurrentHashMap.
```

## Pull Request Process

1. **Create a meaningful PR title** following the commit message format

2. **Write a clear description** including:
   - What problem does this solve?
   - How does it solve the problem?
   - Are there any breaking changes?

3. **Ensure CI passes**:
   - All tests pass (MySQL, MariaDB, PostgreSQL)
   - Code quality checks pass
   - No new warnings introduced

4. **Link related issues**:
   ```markdown
   Closes #123
   Resolves #456
   ```

5. **Request reviews** from maintainers

## Testing Requirements

### Adding New Features

1. Add unit tests for the new functionality
2. Add integration tests using all supported databases
3. Ensure test coverage doesn't decrease
4. Update documentation

### Database Support

Tests must pass on:
- MySQL 8.0
- MariaDB 11
- PostgreSQL 15 (with PostGIS)

### Example Test Structure

```java
@Test
void testMyNewFeature() {
    // Given - Setup test data
    User user = User.builder()
        .email("test@example.com")
        .build();

    // When - Execute the feature
    repository.insert(user, "email");

    // Then - Verify the result
    User found = repository.findByEmail("test@example.com", "id", "email");
    assertThat(found).isNotNull();
    assertThat(found.getEmail()).isEqualTo("test@example.com");
}
```

## Code Style

### Naming Conventions

- Classes: `PascalCase`
- Methods/variables: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- Database columns: `snake_case`
- Java properties: `camelCase`

### Formatting

The project uses Spotless for code formatting. Format your code with:

```bash
./gradlew spotlessApply
```

## Documentation

### Code Comments

- Document **why**, not **what**
- Use JavaDoc for public APIs
- Keep comments up-to-date

### Example:

```java
/**
 * Finds all entities matching the given IDs using a single batch query.
 * This is more efficient than individual queries per ID.
 *
 * @param ids the list of entity IDs to find
 * @param columns the properties to retrieve
 * @return list of matching entities
 */
public List<T> findAllByIds(List<?> ids, String... columns) {
    // Implementation
}
```

## Reporting Bugs

When reporting a bug, include:

1. **Environment:**
   - Java version
   - Gradle version
   - Operating system

2. **Steps to reproduce:**
   - Minimal code example
   - Expected vs actual behavior

3. **Logs/Stack traces:**
   - Full error output
   - Relevant application logs

## Feature Requests

1. Describe the use case
2. Explain the expected behavior
3. Discuss potential implementation approaches
4. Consider database compatibility

## Code Review

### What We Look For

✅ **Do:**
- Follow the project's code style
- Write clear commit messages
- Add tests for new code
- Update documentation
- Keep changes focused and atomic

❌ **Don't:**
- Submit large, unfocused PRs
- Add unrelated changes
- Break existing tests
- Remove test coverage
- Ignore code review comments

## Recognition

Contributors are recognized in:
- CONTRIBUTORS.md file
- GitHub acknowledgments
- Release notes

## Questions?

- Open an issue for questions
- Check existing issues/discussions first
- Join our community discussions

Thank you for contributing! 🙏
