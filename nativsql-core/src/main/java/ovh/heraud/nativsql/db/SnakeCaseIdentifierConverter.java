package ovh.heraud.nativsql.db;

import org.springframework.jdbc.support.JdbcUtils;

/**
 * Default identifier converter that uses snake_case for database identifiers.
 *
 * Converts:
 * - Java: firstName → Database: first_name
 * - Database: first_name → Java: firstName
 */
public class SnakeCaseIdentifierConverter implements IdentifierConverter {

    @Override
    public String toDB(String javaIdentifier) {
        return JdbcUtils.convertPropertyNameToUnderscoreName(javaIdentifier);
    }

    @Override
    public String fromDB(String dbIdentifier) {
        // Convert snake_case to camelCase
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;

        for (char c : dbIdentifier.toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }
}
