package ovh.heraud.nativsql.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import org.springframework.jdbc.support.JdbcUtils;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.TypeParamKey;
import ovh.heraud.nativsql.crypt.CryptAlgorithm;
import ovh.heraud.nativsql.crypt.CryptConfig;
import ovh.heraud.nativsql.crypt.CryptErrorCode;
import ovh.heraud.nativsql.crypt.CryptException;
import ovh.heraud.nativsql.crypt.CryptUtils;
import ovh.heraud.nativsql.exception.NativSQLException;

/**
 * Abstract superclass for all NativSQL type mappers.
 * Centralises ResultSet reading, null handling, and the encrypt/decrypt path.
 * Concrete mappers only implement {@link #doMap(Object, Map)},
 * {@link #toDatabaseValue(Object, DbDataType, Map)}, and {@link #fromValue(Object)}.
 *
 * <p>When a field is annotated with {@code @Type(DbDataType.ENCRYPTED)}, the dialect
 * constructs a mapper via the {@link #AbstractTypeMapper(Map)} constructor with params
 * containing the resolved {@code TypeParamKey.KEY}. The crypt path is then transparent:
 * <ul>
 *   <li>on read: the stored value is decrypted, then passed to {@link #fromValue(Object)}</li>
 *   <li>on write: the value is serialised to String via {@code toDatabaseValue(..., STRING)},
 *       then encrypted</li>
 * </ul>
 *
 * @param <T> the Java type this mapper handles
 */
public abstract class AbstractTypeMapper<T> implements ITypeMapper<T> {

    private final Map<TypeParamKey, Object> params;
    private final CryptConfig cryptConfig;
    private final CryptUtils cryptUtils;

    /**
     * No-arg constructor for normal (non-encrypted) mappers.
     */
    protected AbstractTypeMapper() {
        this.params = Map.of();
        this.cryptConfig = null;
        this.cryptUtils = null;
    }

    /**
     * Constructor for encrypted mappers.
     * Builds {@link CryptConfig} and {@link CryptUtils} eagerly from the resolved params.
     * Expects {@code TypeParamKey.KEY} to be present when the field is ENCRYPTED.
     *
     * @param params the resolved type parameters — must contain KEY for encrypted fields
     */
    protected AbstractTypeMapper(Map<TypeParamKey, Object> params) {
        this.params = params;
        if (params.containsKey(TypeParamKey.KEY)) {
            this.cryptConfig = buildCryptConfig(params);
            this.cryptUtils = new CryptUtils(cryptConfig.getKey(), parseCost(params));
        } else {
            this.cryptConfig = null;
            this.cryptUtils = null;
        }
    }

    /**
     * Template method: reads from RS, decrypts if needed, delegates to
     * {@link #doMap(Object, Map)} (normal path) or {@link #fromValue(Object)} (encrypted path).
     */
    @Override
    public final T map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            int index = rs.findColumn(columnName);
            Object raw = JdbcUtils.getResultSetValue(rs, index);
            if (raw == null) return null;
            if (cryptConfig != null) {
                if (cryptConfig.getAlgorithms()[0].isOneWay()) {
                    // One-way (BCRYPT): return raw hash as-is — decryption not possible
                    return fromValue(raw);
                }
                return fromValue(doDecrypt(raw, columnName));
            }
            return doMap(raw, params);
        } catch (SQLException e) {
            throw new NativSQLException("Unable to map column " + columnName, e);
        }
    }

    /**
     * Template method: serialises, encrypts if needed, then delegates to
     * {@link #toDatabaseValue(Object, DbDataType, Map)}.
     */
    @Override
    public final Object toDatabase(T value, DbDataType dataType) {
        if (value == null) return null;
        if (dataType == DbDataType.ENCRYPTED) {
            String serialized = (String) toDatabaseValue(value, DbDataType.STRING, params);
            return doEncrypt(serialized);
        }
        return toDatabaseValue(value, dataType, params);
    }

    /**
     * Converts the raw ResultSet value to T.
     * Called on the normal (non-encrypted) read path only.
     * Null is never passed — handled by {@link #map(ResultSet, String)}.
     */
    protected abstract T doMap(Object raw, Map<TypeParamKey, Object> params) throws NativSQLException;

    /**
     * Converts a Java value to its database representation.
     * For ENCRYPTED fields, always called with {@code dataType = STRING} (superclass serialises
     * to String first, then encrypts).
     * Null is never passed — handled by {@link #toDatabase(Object, DbDataType)}.
     */
    protected abstract Object toDatabaseValue(T value, DbDataType dataType, Map<TypeParamKey, Object> params);

    /**
     * Converts a value (possibly a decrypted String) to T.
     * Called on the encrypted read path. The value is the decrypted plaintext String
     * (or the raw stored hash for one-way algorithms).
     * Must handle String input for all encrypted fields.
     */
    public abstract T fromValue(Object value);

    // --- private crypt helpers ---

    private Object doEncrypt(String plain) {
        if (cryptConfig.getAlgorithms()[0].isOneWay()) {
            return cryptUtils.hashBcrypt(plain);
        }
        byte[] cipherBytes = cryptUtils.encryptGcm(plain);
        if (cryptConfig.isBinary()) {
            return cipherBytes;
        }
        return cryptConfig.getPrefix() + CryptUtils.toBase64(cipherBytes);
    }

    private String doDecrypt(Object stored, String columnName) {
        if (cryptConfig.isBinary()) {
            if (!(stored instanceof byte[] bytes)) {
                throw new CryptException(CryptErrorCode.INVALID_FORMAT,
                        "Decryption failed for column '" + columnName + "': expected byte[] for BINARY format [INVALID_FORMAT]");
            }
            return cascadeDecrypt(bytes, columnName);
        }
        // STRING format
        String storedStr = stored.toString();
        String prefix = cryptConfig.getPrefix();
        if (prefix != null && !storedStr.startsWith(prefix)) {
            // Not yet migrated — return as-is
            return storedStr;
        }
        String base64Part = (prefix != null) ? storedStr.substring(prefix.length()) : storedStr;
        byte[] cipherBytes = CryptUtils.fromBase64(base64Part, columnName);
        return cascadeDecrypt(cipherBytes, columnName);
    }

    private String cascadeDecrypt(byte[] cipherBytes, String columnName) {
        CryptException lastException = null;
        for (CryptAlgorithm algo : cryptConfig.getAlgorithms()) {
            try {
                return switch (algo) {
                    case GCM -> cryptUtils.decryptGcm(cipherBytes, columnName);
                    default -> throw new CryptException(CryptErrorCode.DECODE_FAILED,
                            "Unsupported algorithm for decryption: " + algo + " [DECODE_FAILED]");
                };
            } catch (CryptException e) {
                lastException = e;
            }
        }
        throw new CryptException(CryptErrorCode.ALL_ALGOS_FAILED,
                "Decryption failed for column '" + columnName + "': all algorithms failed [ALL_ALGOS_FAILED]",
                lastException);
    }

    private static CryptConfig buildCryptConfig(Map<TypeParamKey, Object> params) {
        byte[] key = castParam(params, TypeParamKey.KEY, byte[].class, "byte[]");
        CryptAlgorithm[] algorithms = castParam(params, TypeParamKey.ALGO, CryptAlgorithm[].class, "CryptAlgorithm[]");
        if (algorithms == null || algorithms.length == 0) {
            throw new NativSQLException("Encrypted field: ALGO param is missing or empty");
        }
        String prefix = castParam(params, TypeParamKey.PREFIX, String.class, "String");
        Object formatRaw = params.get(TypeParamKey.FORMAT);
        String formatStr = (formatRaw == null) ? null : castParam(params, TypeParamKey.FORMAT, String.class, "String");
        boolean binary = "BINARY".equalsIgnoreCase(formatStr);
        return new CryptConfig(key, algorithms, prefix, binary);
    }

    private static <V> V castParam(Map<TypeParamKey, Object> params, TypeParamKey key, Class<V> expected, String expectedLabel) {
        Object value = params.get(key);
        if (value == null) return null;
        try {
            return (V) expected.cast(value);
        } catch (ClassCastException e) {
            throw new NativSQLException(
                    "Encrypted field: param " + key + " expected " + expectedLabel
                    + " but got " + value.getClass().getSimpleName() + " [" + value + "]", e);
        }
    }

    private static int parseCost(Map<TypeParamKey, Object> params) {
        String costStr = (String) params.get(TypeParamKey.COST);
        if (costStr == null || costStr.isEmpty()) return 12;
        try {
            return Integer.parseInt(costStr);
        } catch (NumberFormatException e) {
            throw new NativSQLException("Invalid COST value: '" + costStr + "' — must be an integer");
        }
    }
}
