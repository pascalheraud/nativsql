package ovh.heraud.nativsql.db.postgres.mapper;

import java.sql.ResultSet;
import java.util.Map;

import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import ovh.heraud.nativsql.util.FieldAccessor;

/**
 * Wraps a generic nativsql {@link ITypeMapper} to add PostgreSQL parameter
 * casting (issue #118), for the many supported types whose value conversion
 * doesn't need any PostgreSQL-specific behavior — only {@link #formatParameter}
 * differs from the generic mapper. Reading and writing values is delegated
 * unchanged to {@code delegate}.
 *
 * @param <T> the Java type the wrapped mapper handles
 */
public final class PostgresCastingTypeMapper<T> implements ITypeMapper<T> {

    private final ITypeMapper<T> delegate;
    private final String naturalSqlType;

    /**
     * @param delegate       the generic mapper to delegate value conversion to
     * @param naturalSqlType the PostgreSQL type to cast to when no DB_DATA_TYPE
     *                       is declared (see {@link PostgresParameterCasts})
     */
    public PostgresCastingTypeMapper(ITypeMapper<T> delegate, String naturalSqlType) {
        this.delegate = delegate;
        this.naturalSqlType = naturalSqlType;
    }

    @Override
    public T map(ResultSet rs, String columnName, FieldAccessor<?> fieldAccessor, Map<ParamKey, Object> params) {
        return delegate.map(rs, columnName, fieldAccessor, params);
    }

    @Override
    public T map(String description, FieldAccessor<?> fieldAccessor, Map<ParamKey, Object> params, Object value) {
        return delegate.map(description, fieldAccessor, params, value);
    }

    @Override
    public Object toDatabase(T value, Map<ParamKey, Object> params) {
        return delegate.toDatabase(value, params);
    }

    @Override
    public String formatParameter(String paramName, Map<ParamKey, Object> params) {
        return PostgresParameterCasts.castForType(paramName, params, naturalSqlType);
    }
}
