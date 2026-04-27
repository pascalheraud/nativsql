package ovh.heraud.nativsql.annotation.type;

/**
 * Core typed keys for field-level type parameters.
 * Used as keys in the {@code Map<ParamKey, Object>} carried by
 * {@link ovh.heraud.nativsql.util.TypeInfo}.
 */
public enum TypeParamKey implements ParamKey {

    /**
     * Set by {@link CryptAlgo} — {@code CryptAlgorithm[]}. Mandatory for ENCRYPTED.
     */
    ALGO,

    /**
     * Set by {@link CryptKeyProvider} — resolved {@code CryptKeyProvider} instance
     * (Spring bean or newInstance). Mandatory for reversible algorithms.
     */
    KEY_PROVIDER,

    /** Set by {@link CryptCost} — {@code int}. Optional, defaults to 12. */
    COST,

    /**
     * Set by {@link CryptPrefix} — {@code String}. Mandatory for reversible
     * algorithms.
     */
    PREFIX,

    /**
     * The declared {@link ovh.heraud.nativsql.annotation.DbDataType} for this field,
     * set from the {@code @Type} annotation value (or {@code DbDataType.IDENTITY} when absent).
     */
    DB_DATA_TYPE,

    /**
     * Set by {@link Encrypted} — {@code boolean}. Presence signals that the field
     * is stored encrypted in the database.
     * Use {@code @Encrypted} on the field instead of {@code @Type(DbDataType.ENCRYPTED)}.
     */
    ENCRYPTED,

    /**
     * Set by {@link SqlType} — {@code String}. The dialect-specific SQL type name
     * (e.g. {@code "contact_type"} for a PostgreSQL enum, {@code "address"} for a
     * composite type).
     */
    SQL_TYPE,

    /**
     * Set when the field should be stored as JSON. Presence signals JSON serialization.
     * Set by {@link ovh.heraud.nativsql.annotation.Json} on the field or its type class.
     */
    JSON,

    /**
     * Set when the field's type is a database composite type.
     * Set by {@link ovh.heraud.nativsql.annotation.CompositeType} on the type class,
     * or programmatically via {@code setCompositeTypeInfo}.
     */
    COMPOSITE
}
