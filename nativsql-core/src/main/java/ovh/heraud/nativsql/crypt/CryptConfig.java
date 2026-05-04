package ovh.heraud.nativsql.crypt;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Resolved encryption configuration for a single mapped field.
 * Pure data object — holds the key and algorithm settings.
 * Encryption and decryption logic lives in {@code AbstractTypeMapper}.
 *
 * <p>Built at mapper construction time from the resolved {@code TypeParamKey} params.
 */
@Getter
@RequiredArgsConstructor
public class CryptConfig {

    /** Raw AES key bytes. Null for one-way algorithms (e.g. BCRYPT). */
    private final byte[] key;

    /** algos[0] is used for write; all are tried in cascade on read. */
    private final CryptAlgorithm[] algorithms;

    /** Prefix prepended to the stored value (e.g. "{ENC}"). Null for one-way algorithms. */
    private final String prefix;

    /** True = VARBINARY storage, false = VARCHAR (default). */
    private final boolean binary;

}
