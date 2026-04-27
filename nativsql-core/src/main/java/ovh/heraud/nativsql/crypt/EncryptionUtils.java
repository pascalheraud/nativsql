package ovh.heraud.nativsql.crypt;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.NativSQLException;

/**
 * Stateless utility class for field-level encryption and decryption.
 * All methods are static and operate solely on {@link CryptConfig},
 * {@link CryptUtils}
 * and the type param map — no mapper state is involved.
 *
 * <p>
 * {@link CryptUtils} instances are cached per {@code (key, cost)} pair so that
 * at
 * most one instance exists per unique encryption key across the JVM.
 */
public final class EncryptionUtils {

    /** Cache of CryptUtils instances keyed by {@code Base64(key) + ":" + cost}. */
    private static final ConcurrentHashMap<String, CryptUtils> CRYPT_UTILS_CACHE = new ConcurrentHashMap<>();

    private EncryptionUtils() {
    }

    // ---- encrypt path ----

    /**
     * Encrypts a plain-text string using the algorithm and key defined in
     * {@code params}.
     *
     * @param plain  the serialised value to encrypt
     * @param params the type parameters (ALGO, KEY_PROVIDER, PREFIX, DB_DATA_TYPE,
     *               COST)
     * @return the encrypted representation (String for STRING storage, byte[] for
     *         BYTE_ARRAY)
     */
    public static Object encrypt(String plain, Map<ParamKey, Object> params) {
        CryptConfig cfg = buildCryptConfig(params);
        CryptUtils utils = getCachedCryptUtils(cfg.getKey(), parseCost(params));
        if (cfg.getAlgorithms()[0].isOneWay()) {
            return utils.hashBcrypt(plain);
        }
        byte[] cipherBytes = utils.encryptGcm(plain);
        return prefixEncryptedValue(cipherBytes, cfg);
    }

    private static Object prefixEncryptedValue(byte[] cipherBytes, CryptConfig cfg) {
        if (!cfg.isBinary()) {
            return cfg.getPrefix() + CryptUtils.toBase64(cipherBytes);
        } else {
            byte[] prefixBytes = cfg.getPrefix().getBytes();
            byte[] prefixedBytes = new byte[prefixBytes.length + cipherBytes.length];
            System.arraycopy(prefixBytes, 0, prefixedBytes, 0, prefixBytes.length);
            System.arraycopy(cipherBytes, 0, prefixedBytes, prefixBytes.length, cipherBytes.length);
            return prefixedBytes;
        }
    }

    // ---- decrypt path ----

    /**
     * Decrypts a stored value using the algorithm and key defined in
     * {@code params}.
     * Returns the raw value as-is for one-way algorithms (e.g. BCRYPT).
     *
     * @param stored     the stored database value (String or byte[])
     * @param columnName used in error messages
     * @param params     the type parameters (ALGO, KEY_PROVIDER, PREFIX,
     *                   DB_DATA_TYPE, COST)
     * @return the decrypted plain-text string, or the raw value for one-way
     *         algorithms
     */
    public static String decrypt(Object stored, String columnName, Map<ParamKey, Object> params) {
        CryptConfig cfg = buildCryptConfig(params);
        if (cfg.getAlgorithms()[0].isOneWay()) {
            return stored.toString();
        }
        CryptUtils utils = getCachedCryptUtils(cfg.getKey(), parseCost(params));
        return decryptValue(stored, columnName, cfg, utils);
    }

    private static String decryptValue(Object stored, String columnName, CryptConfig cfg, CryptUtils utils) {
        if (cfg.isBinary()) {
            if (!(stored instanceof byte[] bytes)) {
                throw new CryptException(CryptErrorCode.INVALID_FORMAT,
                        "Decryption failed for column '" + columnName
                                + "': expected byte[] for BYTE_ARRAY storage [INVALID_FORMAT]");
            }
            String prefix = cfg.getPrefix();
            byte[] cipherBytes;
            if (prefix != null && !prefix.isEmpty()) {
                byte[] prefixBytes = prefix.getBytes();
                cipherBytes = new byte[bytes.length - prefixBytes.length];
                System.arraycopy(bytes, prefixBytes.length, cipherBytes, 0, cipherBytes.length);
            } else {
                cipherBytes = bytes;
            }
            return cascadeDecrypt(cipherBytes, columnName, utils, cfg.getAlgorithms());
        }
        // STRING storage
        String storedStr = stored.toString();
        String prefix = cfg.getPrefix();
        if (prefix != null && !storedStr.startsWith(prefix)) {
            // Not yet migrated — return as-is
            return storedStr;
        }
        String base64Part = (prefix != null) ? storedStr.substring(prefix.length()) : storedStr;
        byte[] cipherBytes = CryptUtils.fromBase64(base64Part, columnName);
        return cascadeDecrypt(cipherBytes, columnName, utils, cfg.getAlgorithms());
    }

    private static String cascadeDecrypt(byte[] cipherBytes, String columnName,
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

    // ---- param helpers ----

    /**
     * Builds a {@link CryptConfig} from the type param map.
     *
     * @param params the type parameters
     * @return the resolved CryptConfig
     */
    public static CryptConfig buildCryptConfig(Map<ParamKey, Object> params) {
        CryptKeyProvider provider = castParam(params, TypeParamKey.KEY_PROVIDER,
                CryptKeyProvider.class, "CryptKeyProvider");
        byte[] key = provider != null ? provider.getKey() : null;
        CryptAlgorithm[] algorithms = castParam(params, TypeParamKey.ALGO, CryptAlgorithm[].class, "CryptAlgorithm[]");
        if (algorithms == null || algorithms.length == 0) {
            throw new NativSQLException("Encrypted field: ALGO param is missing or empty");
        }
        String prefix = castParam(params, TypeParamKey.PREFIX, String.class, "String");
        if (prefix == null) {
            prefix = "";
        }
        DbDataType dbDataType = castParam(params, TypeParamKey.DB_DATA_TYPE, DbDataType.class, "DbDataType");
        boolean binary;
        if (dbDataType == null || dbDataType == DbDataType.STRING) {
            binary = false;
        } else if (dbDataType == DbDataType.BYTE_ARRAY) {
            binary = true;
        } else {
            throw new NativSQLException(
                    "Encrypted field: DB_DATA_TYPE must be STRING or BYTE_ARRAY, got " + dbDataType);
        }
        return new CryptConfig(key, algorithms, prefix, binary);
    }

    /**
     * Reads a typed param from the map, throwing {@link NativSQLException} on type
     * mismatch.
     */

    public static <V> V castParam(Map<ParamKey, Object> params, ParamKey key, Class<V> expected,
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

    /**
     * Reads the COST param, defaulting to 12 if absent.
     */
    public static int parseCost(Map<ParamKey, Object> params) {
        Object costRaw = params.get(TypeParamKey.COST);
        if (costRaw == null)
            return 12;
        return (Integer) costRaw;
    }

    // ---- CryptUtils cache ----

    public static CryptUtils getCachedCryptUtils(byte[] key, int cost) {
        String cacheKey = (key != null ? CryptUtils.toBase64(key) : "") + ":" + cost;
        return CRYPT_UTILS_CACHE.computeIfAbsent(cacheKey, k -> new CryptUtils(key, cost));
    }
}
