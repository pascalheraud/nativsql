package ovh.heraud.nativsql.util;

/**
 * Supplies a value to assign to an {@code @OnInsert}- or
 * {@code @OnUpdate}-annotated field every time
 * {@code GenericRepository.insert(...)} or {@code GenericRepository.update(...)}
 * runs on the entity, unless the caller already supplies that column
 * explicitly.
 *
 * @param <T> the type of value produced
 */
@FunctionalInterface
public interface ComputedValueProvider<T> {

    /**
     * Computes the value to write to the annotated field.
     *
     * @return the computed value; must not be null
     */
    T getValue();
}
