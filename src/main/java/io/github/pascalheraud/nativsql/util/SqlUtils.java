package io.github.pascalheraud.nativsql.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.github.pascalheraud.nativsql.annotation.OneToMany;
import io.github.pascalheraud.nativsql.exception.SQLException;

/**
 * Utility class for SQL-related operations.
 */
public final class SqlUtils {
    private SqlUtils() {}

    /**
     * Converts an array of column names (camelCase) to a comma-separated list in snake_case.
     * @param columns the property names (camelCase) to convert
     * @return comma-separated list in snake_case
     * @throws SQLException if columns is null or empty
     */
    public static String getColumnsList(String... columns) {
        if (columns == null || columns.length == 0) {
            throw new SQLException("At least one column must be specified");
        }

        return Arrays.stream(columns)
                .map(StringUtils::camelToSnake)
                .collect(Collectors.joining(", "));
    }

    /**
     * Gets all column names for an entity class, excluding OneToMany associations.
     * @param entityClass the entity class
     * @return array of column names (property names in camelCase)
     */
    public static String[] getEntityColumns(Class<?> entityClass) {
        List<String> columns = new ArrayList<>();
        for (FieldAccessor fieldAccessor : ReflectionUtils.getFieldAccessors(entityClass)) {
            // Skip fields annotated with @OneToMany
            if (!fieldAccessor.hasAnnotation(OneToMany.class)) {
                columns.add(fieldAccessor.getName());
            }
        }
        return columns.toArray(new String[0]);
    }
}
