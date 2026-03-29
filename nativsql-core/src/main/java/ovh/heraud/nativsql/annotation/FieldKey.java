package ovh.heraud.nativsql.annotation;

/**
 * Represents a unique field identifier using the declaring class and field name.
 * Used as a key in caches for annotation metadata.
 *
 * @param clazz the class that declares the field
 * @param fieldName the name of the field
 */
public record FieldKey(Class<?> clazz, String fieldName) {
}
