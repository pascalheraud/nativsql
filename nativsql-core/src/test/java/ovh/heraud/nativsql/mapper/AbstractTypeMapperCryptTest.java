package ovh.heraud.nativsql.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.crypt.CryptAlgorithm;
import ovh.heraud.nativsql.crypt.CryptErrorCode;
import ovh.heraud.nativsql.crypt.CryptException;
import ovh.heraud.nativsql.crypt.CryptKeyProvider;
import ovh.heraud.nativsql.crypt.CryptUtils;
import ovh.heraud.nativsql.db.generic.mapper.StringTypeMapper;
import ovh.heraud.nativsql.exception.NativSQLException;

class AbstractTypeMapperCryptTest {

    private static final byte[] KEY = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final byte[] WRONG_KEY = "fedcba9876543210".getBytes(StandardCharsets.UTF_8);
    private static final String PREFIX = "{ENC}";
    private static final String COLUMN = "email";

    private static final CryptKeyProvider KEY_PROVIDER = () -> KEY;

    private final StringTypeMapper mapper = new StringTypeMapper();

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

    private ResultSet mockRs(String columnName, Object value) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.findColumn(columnName)).thenReturn(1);
        when(rs.getObject(1)).thenReturn(value);
        return rs;
    }

    @Test
    void to_database_gcm_serializes_and_encrypts() {
        Map<ParamKey, Object> params = gcmParams();

        Object result = mapper.toDatabase("hello", params);

        assertThat(result).isInstanceOf(String.class);
        assertThat((String) result).startsWith(PREFIX);
    }

    @Test
    void to_database_gcm_round_trip() {
        String plain = "secret value";
        Map<ParamKey, Object> params = gcmParams();

        Object stored = mapper.toDatabase(plain, params);

        String encoded = ((String) stored).substring(PREFIX.length());
        byte[] cipherBytes = CryptUtils.fromBase64(encoded, COLUMN);
        CryptUtils utils = new CryptUtils(KEY);
        String decrypted = utils.decryptGcm(cipherBytes, COLUMN);
        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    void to_database_bcrypt_returns_hash_string() {
        Map<ParamKey, Object> params = bcryptParams();

        Object result = mapper.toDatabase("password", params);

        assertThat(result).isInstanceOf(String.class);
        assertThat((String) result).startsWith("$2a$");
    }

    @Test
    void to_database_non_encrypted_passes_through() {
        Map<ParamKey, Object> params = Map.of();

        Object result = mapper.toDatabase("hello", params);

        assertThat(result).isEqualTo("hello");
    }

    @Test
    void to_database_null_value_returns_null() {
        Map<ParamKey, Object> params = gcmParams();

        Object result = mapper.toDatabase(null, params);

        assertThat(result).isNull();
    }

    @Test
    void map_gcm_decrypts_to_plaintext() throws Exception {
        CryptUtils utils = new CryptUtils(KEY);
        byte[] cipher = utils.encryptGcm("hello");
        String stored = PREFIX + CryptUtils.toBase64(cipher);
        ResultSet rs = mockRs(COLUMN, stored);
        Map<ParamKey, Object> params = gcmParams();

        String result = mapper.map(rs, COLUMN, null, params);

        assertThat(result).isEqualTo("hello");
    }

    @Test
    void map_gcm_value_without_prefix_returned_as_is() throws Exception {
        ResultSet rs = mockRs(COLUMN, "plainvalue");
        Map<ParamKey, Object> params = gcmParams();

        String result = mapper.map(rs, COLUMN, null, params);

        assertThat(result).isEqualTo("plainvalue");
    }

    @Test
    void map_bcrypt_returns_raw_hash_as_is() throws Exception {
        String bcryptHash = "$2a$04$abcdefghijklmnopqrstuuVvWxYzAaBbCcDdEeFfGgHhIiJjKkLl";
        ResultSet rs = mockRs(COLUMN, bcryptHash);
        Map<ParamKey, Object> params = bcryptParams();

        String result = mapper.map(rs, COLUMN, null, params);

        assertThat(result).isEqualTo(bcryptHash);
    }

    @Test
    void map_non_encrypted_reads_raw_value() throws Exception {
        ResultSet rs = mockRs(COLUMN, "rawvalue");
        Map<ParamKey, Object> params = Map.of(TypeParamKey.DB_DATA_TYPE, DbDataType.STRING);

        String result = mapper.map(rs, COLUMN, null, params);

        assertThat(result).isEqualTo("rawvalue");
    }

    @Test
    void map_null_value_in_result_set_returns_null() throws Exception {
        ResultSet rs = mockRs(COLUMN, null);
        Map<ParamKey, Object> params = gcmParams();

        String result = mapper.map(rs, COLUMN, null, params);

        assertThat(result).isNull();
    }

    @Test
    void map_wrong_key_throws_nativ_sql_exception() throws Exception {
        CryptUtils utils = new CryptUtils(KEY);
        byte[] cipher = utils.encryptGcm("hello");
        String stored = PREFIX + CryptUtils.toBase64(cipher);
        ResultSet rs = mockRs(COLUMN, stored);

        Map<ParamKey, Object> wrongParams = new HashMap<>();
        wrongParams.put(TypeParamKey.ENCRYPTED, true);
        wrongParams.put(TypeParamKey.ALGO, new CryptAlgorithm[] { CryptAlgorithm.GCM });
        wrongParams.put(TypeParamKey.KEY_PROVIDER, (CryptKeyProvider) () -> WRONG_KEY);
        wrongParams.put(TypeParamKey.PREFIX, PREFIX);

        assertThatThrownBy(() -> mapper.map(rs, COLUMN, null, wrongParams))
                .isInstanceOf(NativSQLException.class);
    }

    @Test
    void binary_no_prefix_to_database_returns_byte_array() {
        Map<ParamKey, Object> params = new HashMap<>();
        params.put(TypeParamKey.ENCRYPTED, true);
        params.put(TypeParamKey.ALGO, new CryptAlgorithm[] { CryptAlgorithm.GCM });
        params.put(TypeParamKey.KEY_PROVIDER, KEY_PROVIDER);
        params.put(TypeParamKey.DB_DATA_TYPE, DbDataType.BYTE_ARRAY);

        Object result = mapper.toDatabase("secret", params);

        assertThat(result).isInstanceOf(byte[].class);
        assertThat(((byte[]) result).length).isGreaterThan(12);
    }

    @Test
    void binary_no_prefix_round_trip_decrypts_to_plaintext() throws Exception {
        Map<ParamKey, Object> params = new HashMap<>();
        params.put(TypeParamKey.ENCRYPTED, true);
        params.put(TypeParamKey.ALGO, new CryptAlgorithm[] { CryptAlgorithm.GCM });
        params.put(TypeParamKey.KEY_PROVIDER, KEY_PROVIDER);
        params.put(TypeParamKey.DB_DATA_TYPE, DbDataType.BYTE_ARRAY);

        byte[] storedBytes = (byte[]) mapper.toDatabase("hello binary", params);
        ResultSet rs = mock(ResultSet.class);
        when(rs.findColumn(COLUMN)).thenReturn(1);
        when(rs.getObject(1)).thenReturn(storedBytes);

        String result = mapper.map(rs, COLUMN, null, params);

        assertThat(result).isEqualTo("hello binary");
    }

    @Test
    void binary_no_prefix_wrong_type_in_result_set_throws() throws Exception {
        Map<ParamKey, Object> params = new HashMap<>();
        params.put(TypeParamKey.ENCRYPTED, true);
        params.put(TypeParamKey.ALGO, new CryptAlgorithm[] { CryptAlgorithm.GCM });
        params.put(TypeParamKey.KEY_PROVIDER, KEY_PROVIDER);
        params.put(TypeParamKey.DB_DATA_TYPE, DbDataType.BYTE_ARRAY);

        ResultSet rs = mock(ResultSet.class);
        when(rs.findColumn(COLUMN)).thenReturn(1);
        when(rs.getObject(1)).thenReturn("notAByteArray");

        assertThatThrownBy(() -> mapper.map(rs, COLUMN, null, params))
                .isInstanceOf(NativSQLException.class);
    }

    @Test
    void binary_with_prefix_to_database_returns_byte_array_starting_with_prefix() {
        Map<ParamKey, Object> params = new HashMap<>();
        params.put(TypeParamKey.ENCRYPTED, true);
        params.put(TypeParamKey.ALGO, new CryptAlgorithm[] { CryptAlgorithm.GCM });
        params.put(TypeParamKey.KEY_PROVIDER, KEY_PROVIDER);
        params.put(TypeParamKey.DB_DATA_TYPE, DbDataType.BYTE_ARRAY);
        params.put(TypeParamKey.PREFIX, PREFIX);

        Object result = mapper.toDatabase("secret", params);

        assertThat(result).isInstanceOf(byte[].class);
        byte[] resultArray = (byte[]) result;
        assertThat(resultArray.length).isGreaterThan(12 + PREFIX.getBytes().length);
        byte[] subPrefix = new byte[PREFIX.getBytes().length];
        System.arraycopy(resultArray, 0, subPrefix, 0, PREFIX.getBytes().length);
        assertThat(new String(subPrefix)).isEqualTo(PREFIX);
    }

    @Test
    void binary_with_prefix_round_trip_decrypts_to_plaintext() throws Exception {
        Map<ParamKey, Object> params = new HashMap<>();
        params.put(TypeParamKey.ENCRYPTED, true);
        params.put(TypeParamKey.ALGO, new CryptAlgorithm[] { CryptAlgorithm.GCM });
        params.put(TypeParamKey.KEY_PROVIDER, KEY_PROVIDER);
        params.put(TypeParamKey.DB_DATA_TYPE, DbDataType.BYTE_ARRAY);
        params.put(TypeParamKey.PREFIX, PREFIX);

        byte[] storedBytes = (byte[]) mapper.toDatabase("hello binary", params);
        ResultSet rs = mock(ResultSet.class);
        when(rs.findColumn(COLUMN)).thenReturn(1);
        when(rs.getObject(1)).thenReturn(storedBytes);

        String result = mapper.map(rs, COLUMN, null, params);

        assertThat(result).isEqualTo("hello binary");
    }

    @Test
    void binary_with_prefix_wrong_type_in_result_set_throws() throws Exception {
        Map<ParamKey, Object> params = new HashMap<>();
        params.put(TypeParamKey.ENCRYPTED, true);
        params.put(TypeParamKey.ALGO, new CryptAlgorithm[] { CryptAlgorithm.GCM });
        params.put(TypeParamKey.KEY_PROVIDER, KEY_PROVIDER);
        params.put(TypeParamKey.DB_DATA_TYPE, DbDataType.BYTE_ARRAY);
        params.put(TypeParamKey.PREFIX, PREFIX);

        ResultSet rs = mock(ResultSet.class);
        when(rs.findColumn(COLUMN)).thenReturn(1);
        when(rs.getObject(1)).thenReturn("notAByteArray");

        assertThatThrownBy(() -> mapper.map(rs, COLUMN, null, params))
                .isInstanceOf(NativSQLException.class);
    }

    @Test
    void cascade_decrypt_all_algos_fail_when_key_is_wrong() throws Exception {
        CryptUtils utils = new CryptUtils(KEY);
        byte[] cipher = utils.encryptGcm("hello");
        String stored = PREFIX + CryptUtils.toBase64(cipher);
        ResultSet rs = mockRs(COLUMN, stored);

        Map<ParamKey, Object> params = new HashMap<>();
        params.put(TypeParamKey.ENCRYPTED, true);
        params.put(TypeParamKey.ALGO, new CryptAlgorithm[] { CryptAlgorithm.GCM, CryptAlgorithm.GCM });
        params.put(TypeParamKey.KEY_PROVIDER, (CryptKeyProvider) () -> WRONG_KEY);
        params.put(TypeParamKey.PREFIX, PREFIX);

        assertThatThrownBy(() -> mapper.map(rs, COLUMN, null, params))
                .isInstanceOf(NativSQLException.class);
    }

    @Test
    void cascade_decrypt_all_algos_failed_error_code_is_propagated() throws Exception {
        CryptUtils utils = new CryptUtils(KEY);
        byte[] cipher = utils.encryptGcm("hello");
        String stored = PREFIX + CryptUtils.toBase64(cipher);
        ResultSet rs = mockRs(COLUMN, stored);

        Map<ParamKey, Object> params = new HashMap<>();
        params.put(TypeParamKey.ENCRYPTED, true);
        params.put(TypeParamKey.ALGO, new CryptAlgorithm[] { CryptAlgorithm.GCM });
        params.put(TypeParamKey.KEY_PROVIDER, (CryptKeyProvider) () -> WRONG_KEY);
        params.put(TypeParamKey.PREFIX, PREFIX);

        assertThatThrownBy(() -> mapper.map(rs, COLUMN, null, params))
                .isInstanceOf(CryptException.class)
                .satisfies(e -> {
                    assertThat(e).isInstanceOf(CryptException.class);
                    assertThat(((CryptException) e).getCode()).isEqualTo(CryptErrorCode.ALL_ALGOS_FAILED);
                });
    }
}
