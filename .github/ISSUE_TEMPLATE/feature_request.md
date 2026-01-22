---
name: Feature Request
about: Suggest an idea for NativSQL
title: '[FEATURE] '
labels: enhancement
assignees: ''

---

## Description

A clear and concise description of the feature you'd like to see.

## Use Case

Describe the problem or use case this feature would solve:
- What are you trying to do?
- Why is this important?
- How would this improve your experience?

## Proposed Solution

Describe how you'd like this feature to work:

```java
// Example usage
FindQuery<User, Long> query = FindQuery.of(userRepository)
    .select("id", "name", "email")
    .where(/* your idea here */)
    .build();
```

## Alternatives Considered

Describe alternative solutions or features you've considered:

- Alternative 1
- Alternative 2

## Database Compatibility

Will this feature need to work across:
- ✅ MySQL 8.0
- ✅ MariaDB 11
- ✅ PostgreSQL 15

Or specific databases only?

## Additional Context

Add any other context or screenshots about the feature request:
- Related issues
- Similar features in other libraries
- Performance considerations
