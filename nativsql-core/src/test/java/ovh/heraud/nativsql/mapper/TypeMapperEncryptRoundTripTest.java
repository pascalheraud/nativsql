package ovh.heraud.nativsql.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.crypt.CryptAlgorithm;
import ovh.heraud.nativsql.crypt.CryptKeyProvider;
import ovh.heraud.nativsql.db.generic.mapper.BigDecimalTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.BigIntegerTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.BooleanTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.ByteTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.DoubleTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.FloatTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.IntegerTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.LocalDateTimeTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.LocalDateTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.LongTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.ShortTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.StringTypeMapper;
import ovh.heraud.nativsql.db.generic.mapper.UUIDTypeMapper;

/**
 * Per-Java-type round-trip tests for encryption through AbstractTypeMapper.
 *
 * Write path: {@code value → toDatabase(ENCRYPTED) → encrypted String}
 * Read path: {@code encryptedString → map(ENCRYPTED) → original value}
 */
class TypeMapperEncryptRoundTripTest {

    private static final byte[] KEY = "0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final String PREFIX = "{ENC}";
    private static final String COLUMN = "field";
    private static final CryptKeyProvider KEY_PROVIDER = () -> KEY;

    private Map<ParamKey, Object> gcmParams() {
        Map<ParamKey, Object> params = getParams();
        params.put(TypeParamKey.ALGO, new CryptAlgorithm[] { CryptAlgorithm.GCM });
        params.put(TypeParamKey.KEY_PROVIDER, KEY_PROVIDER);
        params.put(TypeParamKey.PREFIX, PREFIX);
        return params;
    }

    private Map<ParamKey, Object> getParams() {
        Map<ParamKey, Object> params = new HashMap<>();
        params.put(TypeParamKey.ENCRYPTED, true);
        return params;
    }

    /** Helper: toDatabase then map back via a mocked ResultSet. */
    private <T> T roundTrip(AbstractTypeMapper<T> mapper, T value) throws SQLException {
        Map<ParamKey, Object> params = gcmParams();

        // Write
        Object stored = mapper.toDatabase(value, params);

        // Read via mock ResultSet
        ResultSet rs = mock(ResultSet.class);
        when(rs.findColumn(COLUMN)).thenReturn(1);
        when(rs.getObject(1)).thenReturn(stored);

        return mapper.map(rs, COLUMN, null, params);

    }

    @Test
    void string_roundTrip() throws Exception {
        assertThat(roundTrip(new StringTypeMapper(), "hello")).isEqualTo("hello");
    }

    @Test
    void integer_roundTrip() throws Exception {
        assertThat(roundTrip(new IntegerTypeMapper(), 42)).isEqualTo(42);
    }

    @Test
    void long_roundTrip() throws Exception {
        assertThat(roundTrip(new LongTypeMapper(), 42L)).isEqualTo(42L);
    }

    @Test
    void short_roundTrip() throws Exception {
        assertThat(roundTrip(new ShortTypeMapper(), (short) 5)).isEqualTo((short) 5);
    }

    @Test
    void byte_roundTrip() throws Exception {
        assertThat(roundTrip(new ByteTypeMapper(), (byte) 1)).isEqualTo((byte) 1);
    }

    @Test
    void float_roundTrip() throws Exception {
        Float result = roundTrip(new FloatTypeMapper(), 3.14f);
        assertThat(result).isEqualTo(3.14f);
    }

    @Test
    void double_roundTrip() throws Exception {
        Double result = roundTrip(new DoubleTypeMapper(), 3.14);
        assertThat(result).isEqualTo(3.14);
    }

    @Test
    void bigDecimal_roundTrip() throws Exception {
        BigDecimal result = roundTrip(new BigDecimalTypeMapper(), new BigDecimal("1.23"));
        assertThat(result).isEqualByComparingTo(new BigDecimal("1.23"));
    }

    @Test
    void bigInteger_roundTrip() throws Exception {
        assertThat(roundTrip(new BigIntegerTypeMapper(), new BigInteger("123"))).isEqualTo(new BigInteger("123"));
    }

    @Test
    void boolean_roundTrip() throws Exception {
        assertThat(roundTrip(new BooleanTypeMapper(), true)).isTrue();
        assertThat(roundTrip(new BooleanTypeMapper(), false)).isFalse();
    }

    @Test
    void uuid_roundTrip() throws Exception {
        UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        UUID result = roundTrip(new UUIDTypeMapper(), uuid);
        assertThat(result).isEqualTo(uuid);
    }

    @Test
    void localDate_roundTrip() throws Exception {
        LocalDate date = LocalDate.of(2025, 1, 15);
        LocalDate result = roundTrip(new LocalDateTypeMapper(), date);
        assertThat(result).isEqualTo(date);
    }

    @Test
    void localDateTime_roundTrip() throws Exception {
        LocalDateTime dt = LocalDateTime.of(2025, 1, 15, 12, 30, 45);
        LocalDateTime result = roundTrip(new LocalDateTimeTypeMapper(), dt);
        assertThat(result).isEqualTo(dt);
    }

    /**
     * Verifies that the encrypted value actually contains no recognizable
     * plaintext and starts with the prefix.
     */
    @Test
    void storedValue_isOpaqueAndPrefixed() throws Exception {
        Map<ParamKey, Object> params = gcmParams();
        Object stored = new StringTypeMapper().toDatabase("secret", params);

        assertThat(stored).isInstanceOf(String.class);
        String storedStr = (String) stored;
        assertThat(storedStr).startsWith(PREFIX);
        assertThat(storedStr).doesNotContain("secret");
    }
}
