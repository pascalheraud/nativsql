package ovh.heraud.nativsql.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a class as a JSON type.
 * This enables automatic registration of the class as a JSON type
 * in the database dialect, without requiring programmatic registration.
 *
 * <p>Example usage:</p>
 * <pre>
 * {@code
 * @Json
 * public class Preferences {
 *     private String theme;
 *     private boolean notifications;
 * }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Json {
}
