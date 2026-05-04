package ovh.heraud.nativsql.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.support.JdbcUtils;
import ovh.heraud.nativsql.util.FieldAccessor;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.CryptFormat;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.crypt.CryptAlgorithm;
import ovh.heraud.nativsql.crypt.CryptConfig;
import ovh.heraud.nativsql.crypt.CryptErrorCode;
import ovh.heraud.nativsql.crypt.CryptException;
import ovh.heraud.nativsql.crypt.CryptUtils;
import ovh.heraud.nativsql.exception.ConversionException;
import ovh.heraud.nativsql.exception.NativSQLException;

/**
 * Abstract superclass for all NativSQL type mappers.
 * Stateless with respect to encryption — no crypt state is held in the
 * instance.
 * The encrypt/decrypt path is driven entirely by the {@code params} passed at
 * call time
 * ({@link #map(ResultSet, String, DbDataType, Map)} and
 * {@link #toDatabase(Object, DbDataType, Map)}).
 *
 * <p>
 * Concrete mappers implement {@link #fromValue(Object, DbDataType, Map)} and
 * {@link #toDatabaseValue(Object, DbDataType, Map)}.
 *
 * <p>
 * {@link CryptUtils} instances are cached per (key, cost) pair — at most one
 * instance
 * is created per unique encryption key across the JVM.
 *
 * @param <T> the Java type this mapper handles
 */
public abstract class AbstractTypeMapper<T> implements ITypeMapper<T> {

    /**
     * Cache of CryptUtils instances keyed by {@code Base64(key) + ":" + cost}.
     * Ensures at most one CryptUtils per unique (key, cost) pair across all mapper
     * instances.
     */
    private static final ConcurrentHashMap<String, CryptUtils> CRYPT_UTILS_CACHE = new ConcurrentHashMap<>();

    // ---- map path ----

    /**
     * Template method: reads from RS, then either:
     * <ul>
     * <li>decrypts and calls {@link #fromValue(Object, DbDataType, Map)} when
     * {@code dataType == DbDataType.ENCRYPTED}</li>
     * <li>calls {@link #fromValue(Object, DbDataType, Map)} with the provided
     * params
     * (normal path)</li>
     * </ul>
     */
    @Override
    // fieldAccessor should not be nullable
    public final T map(ResultSet rs, String columnName, @Nullable DbDataType dataType,
            @Nullable FieldAccessor<?> fieldAccessor, Map<TypeParamKey, Object> params) throws NativSQLException {
        Integer index = null;
        Object raw = null;
        try {
            index = rs.findColumn(columnName);
            raw = JdbcUtils.getResultSetValue(rs, index);
        } catch (SQLException e) {
            throw new NativSQLException("Unable to map column " + columnName + "index(" + index + ")", e);
        }
        if (raw == null)
            return null;
        boolean encrypted = dataType == DbDataType.ENCRYPTED;
        if (encrypted) {
            CryptConfig cfg = buildCryptConfig(params);
            if (cfg.getAlgorithms()[0].isOneWay()) {
                // One-way (BCRYPT): return raw hash as-is — decryption not possible
                return fromValueWithLog(columnName, index, raw, dataType, fieldAccessor, params);
            }
            CryptUtils utils = getCachedCryptUtils(cfg.getKey(), parseCost(params));
            return fromValueWithLog(columnName, index, decryptValue(raw, columnName, cfg, utils), dataType,
                    fieldAccessor, params);
        }
        return fromValueWithLog(columnName, index, raw, dataType, fieldAccessor, params);
    }

    public T fromValueWithLog(String columnName, Integer index, Object value, DbDataType dataType,
            @Nullable FieldAccessor<?> fieldAccessor, Map<TypeParamKey, Object> params) {
        try {
            return fromValue(value, dataType, fieldAccessor, params);
        } catch (ConversionException e) {
            boolean encrypted = dataType == DbDataType.ENCRYPTED;
            String valueStr = encrypted ? "#######" : String.valueOf(value);
            throw new NativSQLException("Unable to map column " + columnName + " (index " + index + ")"
                    + " with value " + valueStr
                    + " from class " + (value != null ? value.getClass() : "null")
                    + " to " + e.getTargetName(), e);
        }
    }

    @Override
    public final T fromValueWithLog(Object value, DbDataType dataType, Map<TypeParamKey, Object> params) {
        try {
            return fromValue(value, dataType, null, params);
        } catch (ConversionException e) {
            boolean encrypted = dataType == DbDataType.ENCRYPTED;
            String valueStr = encrypted ? "#######" : String.valueOf(value);
            throw new NativSQLException("Unable to convert value " + valueStr
                    + " from class " + (value != null ? value.getClass() : "null")
                    + " to " + e.getTargetName(), e);
        }
    }

    // ---- toDatabase path ----

    /**
     * Template method: serialises and optionally encrypts using the provided
     * params.
     * When {@code dataType == ENCRYPTED}, serialises to String via
     * {@link #toDatabaseValue(Object, DbDataType, Map)} then encrypts.
     */
    @Override
    public final Object toDatabase(T value, DbDataType dataType, Map<TypeParamKey, Object> params) {
        if (value == null)
            return null;
        boolean encrypted = dataType == DbDataType.ENCRYPTED;
        try {
            if (encrypted) {
                String serialized = (String) toDatabaseValue(value, DbDataType.STRING, params);
                return encryptWithParams(serialized, params);
            }
            return toDatabaseValue(value, dataType, params);
        } catch (ConversionException e) {
            String valueStr = encrypted ? "#######" : String.valueOf(value);
            throw new NativSQLException("Unable to convert value " + valueStr
                    + " from class " + value.getClass()
                    + " to " + e.getTargetName(), e);
        }
    }

    // ---- abstract hooks ----

    /**
     * Converts a Java value to its database representation.
     * For ENCRYPTED fields always called with {@code dataType = STRING}.
     * Null is never passed — handled by
     * {@link #toDatabase(Object, DbDataType, Map)}.
     */
    protected abstract Object toDatabaseValue(T value, DbDataType dataType, Map<TypeParamKey, Object> params)
            throws ConversionException;

    // ---- private helpers ----

    private static CryptUtils getCachedCryptUtils(byte[] key, int cost) {
        String cacheKey = (key != null ? CryptUtils.toBase64(key) : "") + ":" + cost;
        return CRYPT_UTILS_CACHE.computeIfAbsent(cacheKey, k -> new CryptUtils(key, cost));
    }

    private static Object encryptWithParams(String plain, Map<TypeParamKey, Object> params) {
        CryptConfig cfg = buildCryptConfig(params);
        CryptUtils utils = getCachedCryptUtils(cfg.getKey(), parseCost(params));
        if (cfg.getAlgorithms()[0].isOneWay()) {
            return utils.hashBcrypt(plain);
        }
        byte[] cipherBytes = utils.encryptGcm(plain);
        if (cfg.isBinary()) {
            return cipherBytes;
        }
        return (cfg.getPrefix() != null ? cfg.getPrefix() : "") + CryptUtils.toBase64(cipherBytes);
    }

    private static String decryptValue(Object stored, String columnName, CryptConfig cfg, CryptUtils utils) {
        if (cfg.isBinary()) {
            if (!(stored instanceof byte[] bytes)) {
                throw new CryptException(CryptErrorCode.INVALID_FORMAT,
                        "Decryption failed for column '" + columnName
                                + "': expected byte[] for BINARY format [INVALID_FORMAT]");
            }
            return cascadeDecryptStatic(bytes, columnName, utils, cfg.getAlgorithms());
        }
        // STRING format
        String storedStr = stored.toString();
        String prefix = cfg.getPrefix();
        if (prefix != null && !storedStr.startsWith(prefix)) {
            // Not yet migrated — return as-is
            return storedStr;
        }
        String base64Part = (prefix != null) ? storedStr.substring(prefix.length()) : storedStr;
        byte[] cipherBytes = CryptUtils.fromBase64(base64Part, columnName);
        return cascadeDecryptStatic(cipherBytes, columnName, utils, cfg.getAlgorithms());
    }

    private static String cascadeDecryptStatic(byte[] cipherBytes, String columnName,
            CryptUtils utils, CryptAlgorithm[] algorithms) {
        CryptException lastException = null;
        for (CryptAlgorithm algo : algorithms) {
            try {
                return switch (algo) {
                    case GCM -> utils.decryptGcm(cipherBytes, columnName);
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
        ovh.heraud.nativsql.crypt.CryptKeyProvider provider = castParam(params, TypeParamKey.KEY_PROVIDER,
                ovh.heraud.nativsql.crypt.CryptKeyProvider.class, "CryptKeyProvider");
        byte[] key = provider != null ? provider.getKey() : null;
        CryptAlgorithm[] algorithms = castParam(params, TypeParamKey.ALGO, CryptAlgorithm[].class, "CryptAlgorithm[]");
        if (algorithms == null || algorithms.length == 0) {
            throw new NativSQLException("Encrypted field: ALGO param is missing or empty");
        }
        String prefix = castParam(params, TypeParamKey.PREFIX, String.class, "String");
        Object formatRaw = params.get(TypeParamKey.FORMAT);
        boolean binary = formatRaw instanceof CryptFormat.Format f
                ? f == CryptFormat.Format.BINARY
                : "BINARY".equalsIgnoreCase(formatRaw != null ? formatRaw.toString() : null);
        return new CryptConfig(key, algorithms, prefix, binary);
    }

    private static <V> V castParam(Map<TypeParamKey, Object> params, TypeParamKey key, Class<V> expected,
            String expectedLabel) {
        Object value = params.get(key);
        if (value == null)
            return null;
        try {
            return expected.cast(value);
        } catch (ClassCastException e) {
            throw new NativSQLException(
                    "Encrypted field: param " + key + " expected " + expectedLabel
                            + " but got " + value.getClass().getSimpleName() + " [" + value + "]",
                    e);
        }
    }

    private static int parseCost(Map<TypeParamKey, Object> params) {
        Object costRaw = params.get(TypeParamKey.COST);
        if (costRaw == null)
            return 12;
        return (Integer) costRaw;
    }
}
