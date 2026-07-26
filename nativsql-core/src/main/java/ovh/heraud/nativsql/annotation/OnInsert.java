package ovh.heraud.nativsql.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import ovh.heraud.nativsql.util.ComputedValueProvider;

/**
 * Marks a field whose value is computed by the framework every time
 * {@code GenericRepository.insert(...)} runs on the entity, provided the
 * caller does not already include that column in the requested
 * {@code columns}/{@code getters} list.
 *
 * <p>
 * The field type is not constrained by the framework (timestamp, id of the
 * user performing the change, ...), so {@link #value()} has no default: the
 * provider must always be specified explicitly, matching the annotated
 * field's type. For a {@code creationDate} case, implement
 * {@code ComputedValueProvider<Instant>} returning {@code Instant.now()}.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OnInsert {

    Class<? extends ComputedValueProvider<?>> value();
}
