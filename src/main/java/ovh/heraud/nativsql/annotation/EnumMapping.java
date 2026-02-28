package ovh.heraud.nativsql.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark enum types that should be automatically registered
 * as database enum types.
 *
 * <p>Example usage:</p>
 * <pre>
 * {@code
 * @EnumMapping(typeName = "contact_type")
 * public enum ContactType {
 *     EMAIL, PHONE, FACEBOOK, TWITTER, LINKEDIN, WEBSITE
 * }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnumMapping {

    /**
     * The database enum type name.
     *
     * @return the database type name (e.g., "contact_type")
     */
    String typeName();
}
