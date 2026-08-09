package ovh.heraud.nativsql.repository;

/**
 * Declares the type of a named parameter passed to {@code findExternal}/
 * {@code findAllExternal} that has no matching entity field, so its type
 * cannot otherwise be inferred (needed for PostgreSQL to determine the
 * parameter's type when it is used without a typed comparison, e.g.
 * "where :flag is null"). The wrapped value may be {@code null} (type-only
 * hint) or a real value (its type is inferred from the value and the value
 * itself is still bound and used).
 */
public final class NullableParam {

    private final Class<?> type;
    private final Object value;
    private final boolean hasValue;

    private NullableParam(Class<?> type, Object value, boolean hasValue) {
        this.type = type;
        this.value = value;
        this.hasValue = hasValue;
    }

    public static NullableParam of(Class<?> type) {
        return new NullableParam(type, null, false);
    }

    public static NullableParam of(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null, use NullableParam.of(Class) instead");
        }
        return new NullableParam(value.getClass(), value, true);
    }

    public Class<?> getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

    public boolean hasValue() {
        return hasValue;
    }
}
