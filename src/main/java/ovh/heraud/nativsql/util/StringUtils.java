package ovh.heraud.nativsql.util;

/**
 * Utility class for string operations.
 */
public final class StringUtils {

    private StringUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Converts camelCase to snake_case.
     *
     * @param camelCase the camelCase string to convert
     * @return the snake_case string
     */
    public static String camelToSnake(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
