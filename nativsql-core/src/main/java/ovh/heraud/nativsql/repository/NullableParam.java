package ovh.heraud.nativsql.repository;

/**
 * Declares the type of a named parameter passed to {@code findExternal}/
 * {@code findAllExternal} whose value is {@code null} and has no matching
 * entity field, so its type cannot otherwise be inferred (needed for
 * PostgreSQL to determine the parameter's type when it is used without a
 * typed comparison, e.g. "where :flag is null").
 */
public final class NullableParam {

    private final Class<?> type;

    private NullableParam(Class<?> type) {
        this.type = type;
    }

    public static NullableParam of(Class<?> type) {
        return new NullableParam(type);
    }

    public Class<?> getType() {
        return type;
    }
}
