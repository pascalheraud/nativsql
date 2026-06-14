package ovh.heraud.nativsql.util;

import ovh.heraud.nativsql.db.IdentifierConverter;

/**
 * Resolves a dot-notation column path (e.g. "group.name") to its fully-qualified
 * SQL column reference (e.g. "user_group.name").
 * Registered on a {@link WhereClause} by {@code FindQuery.buildSql()} when joins are present.
 */
@FunctionalInterface
public interface JoinResolver {
    String resolve(String path, IdentifierConverter identifierConverter);
}
