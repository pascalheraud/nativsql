package ovh.heraud.nativsql.crypt;

/**
 * Error codes for all crypt failure cases, carried by {@link CryptException}.
 */
public enum CryptErrorCode {

    /** Base64 decoding of the stored value failed — value is malformed. */
    DECODE_FAILED,

    /** GCM authentication tag verification failed — wrong key or tampered data. */
    AUTH_FAILED,

    /** Ciphertext is too short to be valid (e.g. shorter than IV length). */
    INVALID_FORMAT,

    /** All algorithms in the cascade failed to decrypt the stored value. */
    ALL_ALGOS_FAILED,

    /** Attempted to decrypt a one-way hash (BCRYPT) — operation not supported. */
    ONE_WAY_DECRYPT_UNSUPPORTED,

    /** Encryption failed — underlying JCA error. */
    ENCODE_FAILED,

    /** Attempted to use PREFIX on a one-way algorithm. */
    PREFIX_NOT_APPLICABLE
}
