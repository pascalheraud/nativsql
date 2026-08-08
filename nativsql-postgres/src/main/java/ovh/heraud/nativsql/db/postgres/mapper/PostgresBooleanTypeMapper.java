package ovh.heraud.nativsql.db.postgres.mapper;

import java.util.Map;

import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.db.generic.mapper.BooleanTypeMapper;

/**
 * PostgreSQL-specific Boolean mapper that casts named parameters to
 * ::boolean, so PostgreSQL can always determine the parameter's type even
 * when it is used without a typed comparison (e.g. "where :flag is null").
 */
public class PostgresBooleanTypeMapper extends BooleanTypeMapper {

    @Override
    public String formatParameter(String paramName, Map<ParamKey, Object> params) {
        return PostgresParameterCasts.castForType(paramName, params, "boolean");
    }
}
