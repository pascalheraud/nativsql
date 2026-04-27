package ovh.heraud.nativsql.annotation.type;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the dialect-specific SQL type name for a field.
 *
 * <p>
 * Used by dialect mappers (e.g. PostgreSQL) to produce the correct type cast
 * (e.g. {@code ::contact_type}) when reading or writing the field.
 * Replaces the need to put {@code @EnumMapping} or {@code @CompositeType} on
 * the type class when the type name needs to be declared at the field level.
 *
 * <pre>
 * {@literal @}SqlType("contact_type")
 * private ContactType contactType;
 *
 * {@literal @}SqlType("address")
 * private Address address;
 * </pre>
 */
@TypeParam(key = TypeParamKey.SQL_TYPE)
@Target({ ElementType.FIELD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SqlType {
    /**
     * The SQL type name as known by the database (e.g. {@code "contact_type"}).
     */
    String value();
}
