package ovh.heraud.nativsql.crypt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.NativSQLException;

class EncryptionUtilsTest {

    private static final byte[] KEY = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final byte[] WRONG_KEY = "fedcba9876543210".getBytes(StandardCharsets.UTF_8);
    private static final String PREFIX = "{ENC}";
    private static final String COLUMN = "email";

    private static final CryptKeyProvider KEY_PROVIDER = () -> KEY;

    private Map<ParamKey, Object> gcmParams() {
        Map<ParamKey, Object> params = new HashMap<>();
        params.put(TypeParamKey.ENCRYPTED, true);
        params.put(TypeParamKey.ALGO, new CryptAlgorithm[] { CryptAlgorithm.GCM });
        params.put(TypeParamKey.KEY_PROVIDER, KEY_PROVIDER);
        params.put(TypeParamKey.PREFIX, PREFIX);
        return params;
    }

    private Map<ParamKey, Object> bcryptParams() {
        Map<ParamKey, Object> params = new HashMap<>();
        params.put(TypeParamKey.ENCRYPTED, true);
        params.put(TypeParamKey.ALGO, new CryptAlgorithm[] { CryptAlgorithm.BCRYPT });
        params.put(TypeParamKey.COST, 4);
        return params;
    }

    @Nested
    class encrypt {

        @Test
        void gcm_returns_prefixed_base64_string() {
            // Given: GCM params with a prefix
            Map<ParamKey, Object> params = gcmParams();

            // When: encrypting a value
            Object result = EncryptionUtils.encrypt("hello", params);

            // Then: result is a string starting with the configured prefix
            assertThat(result).isInstanceOf(String.class);
            assertThat((String) result).startsWith(PREFIX);
        }

        @Test
        void gcm_two_calls_produce_different_ciphertexts() {
            // Given: GCM uses a random IV
            Map<ParamKey, Object> params = gcmParams();

            // When: encrypting the same value twice
            Object r1 = EncryptionUtils.encrypt("hello", params);
            Object r2 = EncryptionUtils.encrypt("hello", params);

            // Then: ciphertexts differ
            assertThat(r1).isNotEqualTo(r2);
        }

        @Test
        void bcrypt_returns_hash_starting_with_dollar_2a() {
            // Given: BCrypt params
            // When
            Object result = EncryptionUtils.encrypt("password", bcryptParams());

            // Then
            assertThat(result).isInstanceOf(String.class);
            assertThat((String) result).startsWith("$2a$");
        }

        @Test
        void binary_format_returns_byte_array() {
            // Given: GCM params with BINARY format
            Map<ParamKey, Object> params = new HashMap<>();
            params.put(TypeParamKey.ENCRYPTED, true);
            params.put(TypeParamKey.ALGO, new CryptAlgorithm[] { CryptAlgorithm.GCM });
            params.put(TypeParamKey.KEY_PROVIDER, KEY_PROVIDER);
            params.put(TypeParamKey.DB_DATA_TYPE, DbDataType.BYTE_ARRAY);

            // When
            Object result = EncryptionUtils.encrypt("hello", params);

            // Then: result is a byte array containing at least the IV (12 bytes) +
            // ciphertext
            assertThat(result).isInstanceOf(byte[].class);
            assertThat(((byte[]) result).length).isGreaterThan(12);
        }

        @Test
        void binary_format_with_prefix_byte_array_starts_with_prefix_bytes() {
            // Given: GCM params with BINARY format and a prefix
            Map<ParamKey, Object> params = new HashMap<>();
            params.put(TypeParamKey.ENCRYPTED, true);
            params.put(TypeParamKey.ALGO, new CryptAlgorithm[] { CryptAlgorithm.GCM });
            params.put(TypeParamKey.KEY_PROVIDER, KEY_PROVIDER);
            params.put(TypeParamKey.DB_DATA_TYPE, DbDataType.BYTE_ARRAY);
            params.put(TypeParamKey.PREFIX, PREFIX);

            // When
            byte[] result = (byte[]) EncryptionUtils.encrypt("hello", params);

            // Then: the byte array starts with the prefix bytes
            byte[] prefixBytes = PREFIX.getBytes(StandardCharsets.UTF_8);
            assertThat(result.length).isGreaterThan(prefixBytes.length + 12);
            assertThat(new String(result, 0, prefixBytes.length)).isEqualTo(PREFIX);
        }

        @Test
        void missing_algo_throws_nativsql_exception() {
            // Given: params without ALGO
            Map<ParamKey, Object> params = new HashMap<>();
            params.put(TypeParamKey.ENCRYPTED, true);
            params.put(TypeParamKey.KEY_PROVIDER, KEY_PROVIDER);

            // When / Then
            assertThatThrownBy(() -> EncryptionUtils.encrypt("hello", params))
                    .isInstanceOf(NativSQLException.class);
        }
    }

    @Nested
    class decrypt {

        @Test
        void gcm_round_trip_returns_original_plaintext() {
            // Given: a GCM-encrypted value
            Map<ParamKey, Object> params = gcmParams();
            String stored = (String) EncryptionUtils.encrypt("hello", params);

            // When: decrypting
            String result = EncryptionUtils.decrypt(stored, COLUMN, params);

            // Then
            assertThat(result).isEqualTo("hello");
        }

        @Test
        void gcm_value_without_prefix_is_returned_as_is() {
            // Given: a plain value without prefix (migration path — not yet encrypted)
            Map<ParamKey, Object> params = gcmParams();

            // When
            String result = EncryptionUtils.decrypt("plainvalue", COLUMN, params);

            // Then
            assertThat(result).isEqualTo("plainvalue");
        }

        @Test
        void bcrypt_returns_stored_value_unchanged() {
            // Given: a BCrypt hash as stored value
            String hash = "$2a$04$abcdefghijklmnopqrstuuVvWxYzAaBbCcDdEeFfGgHhIiJjKkLl";

            // When
            String result = EncryptionUtils.decrypt(hash, COLUMN, bcryptParams());

            // Then: BCrypt is one-way, so the raw hash is returned as-is
            assertThat(result).isEqualTo(hash);
        }

        @Test
        void gcm_wrong_key_throws_crypt_exception_with_all_algos_failed() {
            // Given: value encrypted with correct key, but decryption uses wrong key
            String stored = (String) EncryptionUtils.encrypt("hello", gcmParams());

            Map<ParamKey, Object> wrongParams = new HashMap<>();
            wrongParams.put(TypeParamKey.ENCRYPTED, true);
            wrongParams.put(TypeParamKey.ALGO, new CryptAlgorithm[] { CryptAlgorithm.GCM });
            wrongParams.put(TypeParamKey.KEY_PROVIDER, (CryptKeyProvider) () -> WRONG_KEY);
            wrongParams.put(TypeParamKey.PREFIX, PREFIX);

            // When / Then
            assertThatThrownBy(() -> EncryptionUtils.decrypt(stored, COLUMN, wrongParams))
                    .isInstanceOf(CryptException.class)
                    .satisfies(e -> assertThat(((CryptException) e).getCode())
                            .isEqualTo(CryptErrorCode.ALL_ALGOS_FAILED));
        }

        @Test
        void binary_format_round_trip_returns_original_plaintext() {
            // Given: binary GCM params
            Map<ParamKey, Object> params = new HashMap<>();
            params.put(TypeParamKey.ENCRYPTED, true);
            params.put(TypeParamKey.ALGO, new CryptAlgorithm[] { CryptAlgorithm.GCM });
            params.put(TypeParamKey.KEY_PROVIDER, KEY_PROVIDER);
            params.put(TypeParamKey.DB_DATA_TYPE, DbDataType.BYTE_ARRAY);

            // When: encrypting then decrypting
            byte[] stored = (byte[]) EncryptionUtils.encrypt("hello binary", params);
            String result = EncryptionUtils.decrypt(stored, COLUMN, params);

            // Then
            assertThat(result).isEqualTo("hello binary");
        }

        @Test
        void binary_format_with_non_byte_array_throws_crypt_exception_with_invalid_format() {
            // Given: binary GCM params with a non-byte[] stored value
            Map<ParamKey, Object> params = new HashMap<>();
            params.put(TypeParamKey.ENCRYPTED, true);
            params.put(TypeParamKey.ALGO, new CryptAlgorithm[] { CryptAlgorithm.GCM });
            params.put(TypeParamKey.KEY_PROVIDER, KEY_PROVIDER);
            params.put(TypeParamKey.DB_DATA_TYPE, DbDataType.BYTE_ARRAY);

            // When / Then
            assertThatThrownBy(() -> EncryptionUtils.decrypt("notBytes", COLUMN, params))
                    .isInstanceOf(CryptException.class)
                    .satisfies(e -> assertThat(((CryptException) e).getCode())
                            .isEqualTo(CryptErrorCode.INVALID_FORMAT));
        }

        @Test
        void cascade_all_algos_fail_throws_crypt_exception() {
            // Given: value encrypted with correct key, cascade uses wrong key for all algos
            String stored = (String) EncryptionUtils.encrypt("hello", gcmParams());

            Map<ParamKey, Object> wrongParams = new HashMap<>();
            wrongParams.put(TypeParamKey.ENCRYPTED, true);
            wrongParams.put(TypeParamKey.ALGO, new CryptAlgorithm[] { CryptAlgorithm.GCM, CryptAlgorithm.GCM });
            wrongParams.put(TypeParamKey.KEY_PROVIDER, (CryptKeyProvider) () -> WRONG_KEY);
            wrongParams.put(TypeParamKey.PREFIX, PREFIX);

            // When / Then
            assertThatThrownBy(() -> EncryptionUtils.decrypt(stored, COLUMN, wrongParams))
                    .isInstanceOf(CryptException.class)
                    .satisfies(e -> assertThat(((CryptException) e).getCode())
                            .isEqualTo(CryptErrorCode.ALL_ALGOS_FAILED));
        }
    }

    @Nested
    class buildCryptConfig {

        @Test
        void reads_key_algorithms_prefix_and_binary_flag_from_params() {
            // Given
            Map<ParamKey, Object> params = gcmParams();

            // When
            CryptConfig cfg = EncryptionUtils.buildCryptConfig(params);

            // Then
            assertThat(cfg.getKey()).isEqualTo(KEY);
            assertThat(cfg.getAlgorithms()).containsExactly(CryptAlgorithm.GCM);
            assertThat(cfg.getPrefix()).isEqualTo(PREFIX);
            assertThat(cfg.isBinary()).isFalse();
        }

        @Test
        void binary_format_sets_binary_flag() {
            // Given
            Map<ParamKey, Object> params = new HashMap<>();
            params.put(TypeParamKey.ALGO, new CryptAlgorithm[] { CryptAlgorithm.GCM });
            params.put(TypeParamKey.KEY_PROVIDER, KEY_PROVIDER);
            params.put(TypeParamKey.DB_DATA_TYPE, DbDataType.BYTE_ARRAY);

            // When
            CryptConfig cfg = EncryptionUtils.buildCryptConfig(params);

            // Then
            assertThat(cfg.isBinary()).isTrue();
        }

        @Test
        void missing_prefix_defaults_to_empty_string() {
            // Given: params without PREFIX
            Map<ParamKey, Object> params = new HashMap<>();
            params.put(TypeParamKey.ALGO, new CryptAlgorithm[] { CryptAlgorithm.GCM });
            params.put(TypeParamKey.KEY_PROVIDER, KEY_PROVIDER);

            // When
            CryptConfig cfg = EncryptionUtils.buildCryptConfig(params);

            // Then
            assertThat(cfg.getPrefix()).isEmpty();
        }

        @Test
        void missing_algo_throws_nativsql_exception() {
            // Given
            Map<ParamKey, Object> params = new HashMap<>();
            params.put(TypeParamKey.KEY_PROVIDER, KEY_PROVIDER);

            // When / Then
            assertThatThrownBy(() -> EncryptionUtils.buildCryptConfig(params))
                    .isInstanceOf(NativSQLException.class);
        }

        @Test
        void empty_algo_array_throws_nativsql_exception() {
            // Given
            Map<ParamKey, Object> params = new HashMap<>();
            params.put(TypeParamKey.ALGO, new CryptAlgorithm[0]);
            params.put(TypeParamKey.KEY_PROVIDER, KEY_PROVIDER);

            // When / Then
            assertThatThrownBy(() -> EncryptionUtils.buildCryptConfig(params))
                    .isInstanceOf(NativSQLException.class);
        }
    }

    @Nested
    class castParam {

        @Test
        void absent_key_returns_null() {
            // Given: empty params map
            // When
            String result = EncryptionUtils.castParam(Map.of(), TypeParamKey.PREFIX, String.class, "String");

            // Then
            assertThat(result).isNull();
        }

        @Test
        void correct_type_returns_value() {
            // Given
            Map<ParamKey, Object> params = Map.of(TypeParamKey.PREFIX, "hello");

            // When
            String result = EncryptionUtils.castParam(params, TypeParamKey.PREFIX, String.class, "String");

            // Then
            assertThat(result).isEqualTo("hello");
        }

        @Test
        void wrong_type_throws_nativsql_exception() {
            // Given: param value is an Integer, not a String
            Map<ParamKey, Object> params = new HashMap<>();
            params.put(TypeParamKey.PREFIX, 42);

            // When / Then
            assertThatThrownBy(() -> EncryptionUtils.castParam(params, TypeParamKey.PREFIX, String.class, "String"))
                    .isInstanceOf(NativSQLException.class);
        }
    }

    @Nested
    class parseCost {

        @Test
        void absent_cost_defaults_to_12() {
            // Given / When / Then
            assertThat(EncryptionUtils.parseCost(Map.of())).isEqualTo(12);
        }

        @Test
        void explicit_cost_returns_provided_value() {
            // Given
            Map<ParamKey, Object> params = Map.of(TypeParamKey.COST, 8);

            // When / Then
            assertThat(EncryptionUtils.parseCost(params)).isEqualTo(8);
        }
    }

    @Nested
    class getCachedCryptUtils {

        @Test
        void same_key_and_cost_returns_same_instance() {
            // Given / When
            CryptUtils u1 = EncryptionUtils.getCachedCryptUtils(KEY, 12);
            CryptUtils u2 = EncryptionUtils.getCachedCryptUtils(KEY, 12);

            // Then
            assertThat(u1).isSameAs(u2);
        }

        @Test
        void different_cost_returns_different_instance() {
            // Given / When
            CryptUtils u1 = EncryptionUtils.getCachedCryptUtils(KEY, 10);
            CryptUtils u2 = EncryptionUtils.getCachedCryptUtils(KEY, 14);

            // Then
            assertThat(u1).isNotSameAs(u2);
        }

        @Test
        void null_key_returns_usable_instance_for_bcrypt() {
            // Given / When
            CryptUtils u = EncryptionUtils.getCachedCryptUtils(null, 4);

            // Then
            assertThat(u).isNotNull();
        }
    }
}
