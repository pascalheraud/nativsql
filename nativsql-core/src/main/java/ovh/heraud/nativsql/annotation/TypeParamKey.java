package ovh.heraud.nativsql.annotation;

/**
 * Typed keys for parameters passed via {@code @TypeParam} on a {@code @Type} annotation.
 */
public enum TypeParamKey {

    /**
     * Encryption algorithm(s). Use {@code @TypeParam.algoValue()} — accepts one or more
     * {@code CryptAlgorithm} values. Multiple values = cascade fallback on read (first succeeds → stops).
     * Mandatory when {@code DbDataType.ENCRYPTED}.
     */
    ALGO,

    /**
     * Class implementing {@code CryptKeyProvider}.
     * Use {@code @TypeParam.classValue()} (IDE refactoring-safe) or {@code value()} (FQCN String) as fallback.
     * Mandatory when {@code DbDataType.ENCRYPTED} unless the algorithm is one-way (e.g. BCRYPT).
     */
    KEY_PROVIDER,

    /**
     * Cost factor for one-way algorithms (e.g. BCRYPT work factor).
     * Optional — defaults to 12 if absent.
     */
    COST,

    /**
     * Prefix prepended to the encrypted value before storing in DB (e.g. "{ENC}").
     * Mandatory for reversible algorithms. Serves as both a format marker and a migration discriminator:
     * on write: stored value = prefix + Base64(ciphertext); on read: if value starts with prefix → strip
     * and decrypt; otherwise → return as-is (not yet migrated).
     * Not applicable for one-way algorithms.
     */
    PREFIX,

    /**
     * Storage format. Use {@code value()} = "STRING" (default) or "BINARY".
     * STRING stores Base64-encoded ciphertext in a VARCHAR column.
     * BINARY stores raw bytes in a VARBINARY/BLOB column.
     * Optional — defaults to STRING if absent.
     */
    FORMAT,

    /**
     * Resolved AES key bytes ({@code byte[]}).
     * Populated automatically by {@code AnnotationManager} at startup by calling
     * the {@code CryptKeyProvider} referenced by {@code KEY_PROVIDER}.
     * Never set manually via {@code @TypeParam} — internal use only.
     */
    KEY
}
