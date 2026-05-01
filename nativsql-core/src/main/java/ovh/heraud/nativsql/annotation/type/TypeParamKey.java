package ovh.heraud.nativsql.annotation.type;

/**
 * Typed keys for crypt parameters collected from field-level annotations
 * ({@link CryptAlgo}, {@link CryptKeyProvider}, {@link CryptPrefix}, {@link CryptCost}, {@link CryptFormat}).
 * Used as keys in the {@code Map<TypeParamKey, Object>} carried by {@link ovh.heraud.nativsql.util.TypeInfo}.
 */
public enum TypeParamKey {

    /** Set by {@link CryptAlgo} — {@code CryptAlgorithm[]}. Mandatory for ENCRYPTED. */
    ALGO,

    /** Set by {@link CryptKeyProvider} — resolved {@code CryptKeyProvider} instance (Spring bean or newInstance). Mandatory for reversible algorithms. */
    KEY_PROVIDER,

    /** Set by {@link CryptCost} — {@code int}. Optional, defaults to 12. */
    COST,

    /** Set by {@link CryptPrefix} — {@code String}. Mandatory for reversible algorithms. */
    PREFIX,

    /** Set by {@link CryptFormat} — {@code CryptFormat.Format}. Optional, defaults to STRING. */
    FORMAT
}
