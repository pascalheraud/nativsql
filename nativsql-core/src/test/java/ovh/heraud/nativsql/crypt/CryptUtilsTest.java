package ovh.heraud.nativsql.crypt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CryptUtils}.
 * Covers GCM encrypt/decrypt round-trips, cascade decryption, and error cases.
 */
class CryptUtilsTest {

    private static final byte[] KEY_16 = "0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final byte[] KEY_16_ALT = "fedcba9876543210".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Nested
    class GcmRoundTripTests {

        @Test
        void encryptThenDecrypt_returnsOriginalPlaintext() {
            CryptUtils utils = new CryptUtils(KEY_16);
            String plain = "hello world";

            byte[] cipher = utils.encryptGcm(plain);
            String decrypted = utils.decryptGcm(cipher, "col");

            assertThat(decrypted).isEqualTo(plain);
        }

        @Test
        void encryptIsNonDeterministic_samePlaintextProducesDifferentCipher() {
            CryptUtils utils = new CryptUtils(KEY_16);
            String plain = "hello";

            byte[] cipher1 = utils.encryptGcm(plain);
            byte[] cipher2 = utils.encryptGcm(plain);

            assertThat(cipher1).isNotEqualTo(cipher2);
        }

        @Test
        void encryptThenDecrypt_emptyString() {
            CryptUtils utils = new CryptUtils(KEY_16);

            byte[] cipher = utils.encryptGcm("");
            String decrypted = utils.decryptGcm(cipher, "col");

            assertThat(decrypted).isEmpty();
        }

        @Test
        void encryptThenDecrypt_unicodeChars() {
            CryptUtils utils = new CryptUtils(KEY_16);
            String plain = "café 中文";

            byte[] cipher = utils.encryptGcm(plain);
            String decrypted = utils.decryptGcm(cipher, "col");

            assertThat(decrypted).isEqualTo(plain);
        }
    }

    @Nested
    class GcmDecryptErrorTests {

        @Test
        void decryptWithWrongKey_throwsAuthFailed() {
            CryptUtils encryptor = new CryptUtils(KEY_16);
            CryptUtils decryptor = new CryptUtils(KEY_16_ALT);
            byte[] cipher = encryptor.encryptGcm("secret");

            assertThatThrownBy(() -> decryptor.decryptGcm(cipher, "col"))
                    .isInstanceOf(CryptException.class)
                    .satisfies(e -> assertThat(((CryptException) e).getCode())
                            .isIn(CryptErrorCode.AUTH_FAILED, CryptErrorCode.DECODE_FAILED));
        }

        @Test
        void decryptTooShortBytes_throwsInvalidFormat() {
            CryptUtils utils = new CryptUtils(KEY_16);
            byte[] tooShort = new byte[5];

            assertThatThrownBy(() -> utils.decryptGcm(tooShort, "col"))
                    .isInstanceOf(CryptException.class)
                    .satisfies(e -> assertThat(((CryptException) e).getCode())
                            .isEqualTo(CryptErrorCode.INVALID_FORMAT));
        }

        @Test
        void decryptExactlyIvLength_throwsInvalidFormat() {
            // Boundary: exactly 12 bytes (= IV length) must also be rejected
            CryptUtils utils = new CryptUtils(KEY_16);
            byte[] exactlyIv = new byte[12];

            assertThatThrownBy(() -> utils.decryptGcm(exactlyIv, "col"))
                    .isInstanceOf(CryptException.class)
                    .satisfies(e -> assertThat(((CryptException) e).getCode())
                            .isEqualTo(CryptErrorCode.INVALID_FORMAT));
        }
    }

    @Nested
    class Base64Tests {

        @Test
        void toBase64ThenFromBase64_roundTrip() {
            byte[] original = new byte[] { 1, 2, 3, 4, 5 };

            String encoded = CryptUtils.toBase64(original);
            byte[] decoded = CryptUtils.fromBase64(encoded, "col");

            assertThat(decoded).isEqualTo(original);
        }

        @Test
        void fromBase64InvalidInput_throwsDecodeFailedCryptException() {
            assertThatThrownBy(() -> CryptUtils.fromBase64("!!!notBase64!!!", "col"))
                    .isInstanceOf(CryptException.class)
                    .satisfies(e -> assertThat(((CryptException) e).getCode())
                            .isEqualTo(CryptErrorCode.DECODE_FAILED));
        }
    }

    @Nested
    class BcryptTests {

        @Test
        void hashBcrypt_produces_NonEmptyHash() {
            CryptUtils utils = new CryptUtils(null, 4);
            String hash = utils.hashBcrypt("password");

            assertThat(hash).isNotNull().isNotEmpty().startsWith("$2a$");
        }

        @Test
        void hashBcrypt_differentCallsProduceDifferentHashes() {
            CryptUtils utils = new CryptUtils(null, 4);
            String hash1 = utils.hashBcrypt("password");
            String hash2 = utils.hashBcrypt("password");

            assertThat(hash1).isNotEqualTo(hash2);
        }
    }

    @Nested
    class CascadeDecryptTests {

        /**
         * We test the cascade behaviour indirectly through AbstractTypeMapper, but here
         * we verify the basic GCM round-trip with two separate CryptUtils instances
         * (simulating key rotation: ciphertext encrypted with key1 can only be
         * decrypted
         * with key1, not key2).
         */
        @Test
        void decryptWithCorrectKeySucceeds_withWrongKeyFails() {
            CryptUtils key1Utils = new CryptUtils(KEY_16);
            CryptUtils key2Utils = new CryptUtils(KEY_16_ALT);

            byte[] cipher = key1Utils.encryptGcm("hello");

            // Correct key succeeds
            assertThat(key1Utils.decryptGcm(cipher, "col")).isEqualTo("hello");

            // Wrong key fails
            assertThatThrownBy(() -> key2Utils.decryptGcm(cipher, "col"))
                    .isInstanceOf(CryptException.class);
        }
    }
}
