package io.github.pascalheraud.nativsql.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark enum types that should be automatically registered
 * as PostgreSQL ENUM types.
 *
 * <p>Example usage:</p>
 * <pre>
 * {@code
 * @EnumPGMapping(pgTypeName = "contact_type")
 * public enum ContactType {
 *     EMAIL, PHONE, FACEBOOK, TWITTER, LINKEDIN, WEBSITE
 * }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnumPGMapping {

    /**
     * The PostgreSQL enum type name.
     *
     * @return the PostgreSQL type name (e.g., "contact_type")
     */
    String pgTypeName();
}
