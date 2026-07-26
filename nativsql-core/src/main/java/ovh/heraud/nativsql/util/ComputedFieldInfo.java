package ovh.heraud.nativsql.util;

/**
 * Holds the resolved information for an {@code @OnInsert}- or
 * {@code @OnUpdate}-annotated field: the field name and the resolved
 * {@link ComputedValueProvider} instance to invoke on every
 * {@code GenericRepository.insert(...)}/{@code update(...)} call.
 *
 * @param fieldName the annotated field's property name
 * @param provider  the resolved provider instance to invoke
 */
public record ComputedFieldInfo(String fieldName, ComputedValueProvider<?> provider) {
}
