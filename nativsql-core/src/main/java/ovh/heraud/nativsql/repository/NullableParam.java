package ovh.heraud.nativsql.repository;

/**
 * Declares the type of a named parameter passed to {@code findExternal}/
 * {@code findAllExternal} that has no matching entity field, so its type
 * cannot otherwise be inferred (needed for PostgreSQL to determine the
 * parameter's type when it is used without a typed comparison, e.g.
 * "where :flag is null"). The wrapped value may be {@code null} (type-only
 * hint) or a real value (the value is still bound and used, with its type
 * caught for the cast).
 */
public final class NullableParam {

    private final Class<?> type;
    private final Object value;

    private NullableParam(Class<?> type, Object value) {
        this.type = type;
        this.value = value;
    }

    public static NullableParam of(Class<?> type, Object value) {
        return new NullableParam(type, value);
    }

    public Class<?> getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }
}
