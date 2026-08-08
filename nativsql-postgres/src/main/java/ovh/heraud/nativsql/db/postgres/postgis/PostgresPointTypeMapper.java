package ovh.heraud.nativsql.db.postgres.postgis;

import ovh.heraud.nativsql.util.FieldAccessor;
import java.util.Map;

import org.postgis.PGgeometry;
import org.postgis.Point;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.db.postgres.mapper.PostgresParameterCasts;
import ovh.heraud.nativsql.exception.ConversionException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;

/**
 * PostgreSQL-specific Point mapper for PostGIS geometry types.
 * Handles both reading from and writing to PostgreSQL geometry columns.
 */
public class PostgresPointTypeMapper extends AbstractTypeMapper<Point> {

    @Override
    public Point fromValue(Object value, FieldAccessor<?> fieldAccessor,
            Map<ParamKey, Object> params) throws ConversionException {
        if (value instanceof Point p)
            return p;
        if (value instanceof PGgeometry pgGeom)
            return (Point) pgGeom.getGeometry();
        if (value instanceof String str) {
            try {
                return (Point) new PGgeometry(str).getGeometry();
            } catch (Exception e) {
                throw new ConversionException(Point.class, e);
            }
        }
        throw new ConversionException(Point.class);
    }

    @Override
    protected Object toDatabaseValue(Point value, Map<ParamKey, Object> params)
            throws ConversionException {
        DbDataType dataType = (DbDataType) params.get(TypeParamKey.DB_DATA_TYPE);
        if (dataType != null) {
            throw new ConversionException(dataType.name());
        }
        return new PGgeometry(value);
    }

    @Override
    public String formatParameter(String paramName, Map<ParamKey, Object> params) {
        return PostgresParameterCasts.cast(paramName, "geometry");
    }
}
