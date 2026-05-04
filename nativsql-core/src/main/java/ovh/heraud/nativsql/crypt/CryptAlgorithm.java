package ovh.heraud.nativsql.crypt;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Encryption algorithms supported by NativSQL field-level encryption.
 *
 * GCM   : AES/GCM/NoPadding with a random 12-byte IV and 128-bit auth tag.
 *         Non-deterministic (IV is random) — WHERE equality on GCM-encrypted fields is not supported.
 * BCRYPT: one-way bcrypt hash — irreversible, used for passwords.
 *         Verification is the caller's responsibility ({@code BCrypt.checkpw(input, storedHash)}).
 */
@Getter
@RequiredArgsConstructor
public enum CryptAlgorithm {

    /**
     * AES/GCM/NoPadding — reversible, non-deterministic.
     * Provides confidentiality and integrity (AEAD). Recommended for all new reversible encryption.
     */
    GCM("AES/GCM/NoPadding", false, false),

    /**
     * BCrypt one-way hash — irreversible.
     * No symmetric key required. Use {@code COST} param to override the default work factor (12).
     */
    BCRYPT(null, false, true);

    /**
     * JCA transformation string to pass to {@code Cipher.getInstance()}.
     * Null for one-way algorithms.
     */
    private final String transformation;

    /**
     * Returns true if the same plaintext always produces the same ciphertext,
     * allowing WHERE equality checks on encrypted columns.
     */
    private final boolean deterministic;

    /**
     * Returns true if the algorithm is irreversible (hash-based).
     * One-way algorithms cannot be decrypted.
     */
    private final boolean oneWay;
}
