package ovh.heraud.nativsql.crypt;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static ovh.heraud.nativsql.crypt.CryptErrorCode.*;

/**
 * Low-level encryption/decryption utilities for NativSQL field-level encryption.
 * No Spring dependency — the key is passed via constructor.
 *
 * <p>GCM format: IV (12 bytes) || ciphertext+tag. The IV is generated fresh for
 * each encrypt call using a {@code static final SecureRandom} instance (thread-safe).
 *
 * <p>Never logs any value — neither plain, cipher, nor key bytes.
 */
public class CryptUtils {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final byte[] key;
    private final int bcryptStrength;

    /**
     * Creates a CryptUtils with the given AES key and default bcrypt strength (12).
     *
     * @param key AES key bytes — 16, 24, or 32 bytes
     */
    public CryptUtils(byte[] key) {
        this(key, 12);
    }

    /**
     * Creates a CryptUtils with the given AES key and bcrypt strength.
     *
     * @param key            AES key bytes — 16, 24, or 32 bytes (may be null for one-way-only use)
     * @param bcryptStrength bcrypt work factor (4–31, default 12)
     */
    public CryptUtils(byte[] key, int bcryptStrength) {
        this.key = key;
        this.bcryptStrength = bcryptStrength;
    }

    /**
     * Encrypts {@code plain} using AES/GCM/NoPadding with a fresh random IV.
     * Returns raw bytes: IV (12) || ciphertext+tag.
     *
     * @param plain the plaintext to encrypt — never null
     * @return the ciphertext bytes
     * @throws CryptException(ENCODE_FAILED) on JCA error
     */
    public byte[] encryptGcm(String plain) {
        try {
            byte[] input = plain.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance(CryptAlgorithm.GCM.getTransformation());

            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] cipherText = cipher.doFinal(input);
            byte[] output = new byte[GCM_IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, output, 0, GCM_IV_LENGTH);
            System.arraycopy(cipherText, 0, output, GCM_IV_LENGTH, cipherText.length);
            return output;
        } catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException
                | IllegalBlockSizeException | BadPaddingException
                | InvalidAlgorithmParameterException e) {
            throw new CryptException(ENCODE_FAILED, "Encryption failed [ENCODE_FAILED]", e);
        }
    }

    /**
     * Decrypts AES/GCM ciphertext bytes (IV || ciphertext+tag) to plaintext.
     *
     * @param cipherBytes the ciphertext bytes — never null
     * @param columnName  used in exception messages — never the value itself
     * @return the decrypted plaintext
     * @throws CryptException(INVALID_FORMAT) if ciphertext is too short
     * @throws CryptException(AUTH_FAILED)    if GCM tag verification fails
     * @throws CryptException(DECODE_FAILED)  on other JCA errors
     */
    public String decryptGcm(byte[] cipherBytes, String columnName) {
        if (cipherBytes.length <= GCM_IV_LENGTH) {
            throw new CryptException(INVALID_FORMAT,
                    "Decryption failed for column '" + columnName + "' with algo GCM [INVALID_FORMAT]");
        }
        try {
            byte[] iv = Arrays.copyOfRange(cipherBytes, 0, GCM_IV_LENGTH);
            byte[] cipherText = Arrays.copyOfRange(cipherBytes, GCM_IV_LENGTH, cipherBytes.length);

            SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance(CryptAlgorithm.GCM.getTransformation());
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return new String(cipher.doFinal(cipherText), java.nio.charset.StandardCharsets.UTF_8);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            throw new CryptException(AUTH_FAILED,
                    "Decryption failed for column '" + columnName + "' with algo GCM [AUTH_FAILED]", e);
        } catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException
                | InvalidAlgorithmParameterException e) {
            throw new CryptException(DECODE_FAILED,
                    "Decryption failed for column '" + columnName + "' with algo GCM [DECODE_FAILED]", e);
        }
    }

    /**
     * Hashes {@code plain} using bcrypt with the configured strength.
     *
     * @param plain the value to hash — never null
     * @return the bcrypt hash string
     * @throws CryptException(ENCODE_FAILED) on error
     */
    public String hashBcrypt(String plain) {
        try {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(bcryptStrength);
            return encoder.encode(plain);
        } catch (Exception e) {
            throw new CryptException(ENCODE_FAILED, "BCrypt hashing failed [ENCODE_FAILED]", e);
        }
    }

    /**
     * Encodes raw ciphertext bytes to a Base64 string (standard encoding, no padding stripped).
     */
    public static String toBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Decodes a Base64 string to raw bytes.
     *
     * @throws CryptException(DECODE_FAILED) if the string is not valid Base64
     */
    public static byte[] fromBase64(String base64, String columnName) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new CryptException(DECODE_FAILED,
                    "Decryption failed for column '" + columnName + "': Base64 decoding failed [DECODE_FAILED]", e);
        }
    }
}
