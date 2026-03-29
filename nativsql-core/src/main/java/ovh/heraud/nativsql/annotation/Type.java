package ovh.heraud.nativsql.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify the database data type for a field.
 *
 * This annotation can be used to explicitly declare the database type
 * of a field, allowing for type-specific database handling and validation.
 *
 * <p>Example usage:
 * <pre>
 * @Entity
 * public class User {
 *     @Type(DbDataType.LONG)
 *     private Long id;
 *
 *     @Type(DbDataType.STRING)
 *     private String name;
 * }
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Type {
    /**
     * The database data type for this field.
     *
     * @return the database data type
     */
    DbDataType value();
}
