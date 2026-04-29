package ovh.heraud.nativsql.util;

import java.util.Collections;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.TypeParamKey;

/**
 * Contains information about a field type extracted from the @Type annotation.
 */
public class TypeInfo {

    private final @NonNull DbDataType dataType;
    private final Map<TypeParamKey, Object> params;

    /**
     * Creates a new TypeInfo without parameters (backward-compatible constructor).
     *
     * @param dataType the database data type for this field
     */
    public TypeInfo(@NonNull DbDataType dataType) {
        this.dataType = dataType;
        this.params = Collections.emptyMap();
    }

    /**
     * Creates a new TypeInfo with parameters.
     *
     * @param dataType the database data type for this field
     * @param params   the type parameters (e.g. ALGO, KEY_PROVIDER, PREFIX)
     */
    public TypeInfo(@NonNull DbDataType dataType, Map<TypeParamKey, Object> params) {
        this.dataType = dataType;
        this.params = (params != null) ? Collections.unmodifiableMap(params) : Collections.emptyMap();
    }

    /**
     * Gets the database data type.
     *
     * @return the database data type
     */
    @NonNull
    public DbDataType getDataType() {
        return dataType;
    }

    /**
     * Gets all type parameters.
     *
     * @return an unmodifiable map of parameters — empty if none
     */
    public Map<TypeParamKey, Object> getParams() {
        return params;
    }

    /**
     * Gets the value of a specific type parameter.
     *
     * @param key the parameter key
     * @return the parameter value, or null if not set
     */
    public Object getParam(TypeParamKey key) {
        return params.get(key);
    }
}
