package ovh.heraud.nativsql.annotation.type;

/**
 * Marker interface for typed keys used in the {@code Map<TypeParamKey, Object>}
 * carried by {@link ovh.heraud.nativsql.util.TypeInfo}.
 *
 * <p>
 * Core crypt keys are defined in {@link TypeParamKey}.
 * Dialect-specific modules (e.g. nativsql-postgres) may define their own
 * {@code enum} implementing this interface to contribute additional keys
 * without requiring changes to this module.
 */
public interface ParamKey {
}
