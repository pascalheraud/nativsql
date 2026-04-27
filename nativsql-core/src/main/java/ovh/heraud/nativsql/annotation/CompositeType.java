package ovh.heraud.nativsql.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a database composite type.
 * The SQL type name is declared separately via {@link ovh.heraud.nativsql.annotation.type.SqlType}.
 *
 * <pre>
 * {@literal @}CompositeType
 * {@literal @}SqlType("address_type")
 * public class Address { ... }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CompositeType {
}
