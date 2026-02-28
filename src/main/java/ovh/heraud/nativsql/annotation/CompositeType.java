package ovh.heraud.nativsql.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a database composite type.
 * This annotation indicates that a class represents a composite type in the database.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CompositeType {
    /**
     * The database type name for this composite type.
     *
     * @return the database type name
     */
    String typeName();
}
