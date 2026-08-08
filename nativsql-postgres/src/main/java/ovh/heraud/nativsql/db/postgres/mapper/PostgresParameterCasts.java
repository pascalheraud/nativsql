package ovh.heraud.nativsql.db.postgres.mapper;

import java.util.EnumMap;
import java.util.Map;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;

/**
 * Shared {@code formatParameter} helpers for PostgreSQL mappers that need to
 * wrap a named parameter in a {@code ::type} cast so PostgreSQL can
 * determine its type even when it is used without a typed comparison
 * (issue #118).
 *
 * <p>
 * The cast target is resolved from the parameter's declared
 * {@link DbDataType} (set via {@code @Type}, e.g. an {@code Integer} field
 * annotated {@code @Type(BIG_INTEGER)}) when present, falling back to the
 * mapper's own natural PostgreSQL type otherwise. This covers every
 * DbDataType a given mapper's {@code toDatabaseValue} can convert to (see
 * e.g. {@code BooleanTypeMapper}, {@code IntegerTypeMapper}), so a single
 * lookup table is enough to generalize casting across all supported types,
 * not just Boolean/UUID.
 */
public final class PostgresParameterCasts {

    private static final Map<DbDataType, String> SQL_TYPE_BY_DATA_TYPE = new EnumMap<>(DbDataType.class);

    static {
        SQL_TYPE_BY_DATA_TYPE.put(DbDataType.STRING, "text");
        SQL_TYPE_BY_DATA_TYPE.put(DbDataType.INTEGER, "integer");
        SQL_TYPE_BY_DATA_TYPE.put(DbDataType.LONG, "bigint");
        SQL_TYPE_BY_DATA_TYPE.put(DbDataType.SHORT, "smallint");
        SQL_TYPE_BY_DATA_TYPE.put(DbDataType.BYTE, "smallint");
        SQL_TYPE_BY_DATA_TYPE.put(DbDataType.FLOAT, "real");
        SQL_TYPE_BY_DATA_TYPE.put(DbDataType.DOUBLE, "double precision");
        SQL_TYPE_BY_DATA_TYPE.put(DbDataType.DECIMAL, "numeric");
        SQL_TYPE_BY_DATA_TYPE.put(DbDataType.BIG_INTEGER, "numeric");
        SQL_TYPE_BY_DATA_TYPE.put(DbDataType.BOOLEAN, "boolean");
        SQL_TYPE_BY_DATA_TYPE.put(DbDataType.DATE, "date");
        SQL_TYPE_BY_DATA_TYPE.put(DbDataType.DATE_TIME, "timestamp");
        SQL_TYPE_BY_DATA_TYPE.put(DbDataType.LOCAL_DATE_TIME, "timestamp");
        SQL_TYPE_BY_DATA_TYPE.put(DbDataType.UUID, "uuid");
        SQL_TYPE_BY_DATA_TYPE.put(DbDataType.BYTE_ARRAY, "bytea");
    }

    private PostgresParameterCasts() {
    }

    /** Unconditionally wraps {@code paramName} in a {@code ::sqlType} cast. */
    public static String cast(String paramName, String sqlType) {
        return "(:" + paramName + ")::" + sqlType;
    }

    /**
     * Casts {@code paramName} to the PostgreSQL type matching {@code params}'
     * declared DB_DATA_TYPE, or to {@code naturalSqlType} when no DB_DATA_TYPE is
     * declared (or it has no known PostgreSQL equivalent).
     */
    public static String castForType(String paramName, Map<ParamKey, Object> params, String naturalSqlType) {
        DbDataType dataType = (DbDataType) params.get(TypeParamKey.DB_DATA_TYPE);
        String sqlType = dataType == null ? naturalSqlType
                : SQL_TYPE_BY_DATA_TYPE.getOrDefault(dataType, naturalSqlType);
        return cast(paramName, sqlType);
    }
}
