package ovh.heraud.nativsql.util;

import java.util.HashMap;
import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;

/**
 * Contains type parameters for a mapped field (DB_DATA_TYPE, encryption params,
 * etc.).
 */
public class TypeInfo {

    private final Map<ParamKey, Object> params;

    public TypeInfo() {
        this.params = new HashMap<>();
    }

    public TypeInfo(Map<ParamKey, Object> params) {
        this.params = new HashMap<>(params);
    }

    public DbDataType getDataType() {
        Object value = params.get(TypeParamKey.DB_DATA_TYPE);
        return value instanceof DbDataType dt ? dt : DbDataType.IDENTITY;
    }

    public Map<ParamKey, Object> getParams() {
        return params;
    }

    public Object getParam(ParamKey key) {
        return params.get(key);
    }
}
