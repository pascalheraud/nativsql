package ovh.heraud.nativsql.db.postgres.type;

import ovh.heraud.nativsql.annotation.type.ParamKey;

/**
 * Typed keys for PostgreSQL-specific type parameters.
 * Used as keys in the {@code Map<TypeParamKey, Object>} carried by
 * {@link ovh.heraud.nativsql.util.TypeInfo}.
 */
public enum PostgresParamKey implements ParamKey {

    /**
     * The PostgreSQL database type name (e.g. {@code "my_enum_type"},
     * {@code "my_composite_type"}).
     */
    DB_TYPE_NAME
}
