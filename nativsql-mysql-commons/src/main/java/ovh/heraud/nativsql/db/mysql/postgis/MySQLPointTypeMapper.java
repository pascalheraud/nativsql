package ovh.heraud.nativsql.db.mysql.postgis;

import java.util.Map;

import org.postgis.Point;
import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;
import ovh.heraud.nativsql.util.FieldAccessor;

/**
 * MySQL-specific Point mapper for spatial coordinates.
 * Handles both reading from and writing to MySQL spatial columns.
 */
public class MySQLPointTypeMapper extends AbstractTypeMapper<Point> {

    @Override
    public Point fromValue(Object value, FieldAccessor<?> fieldAccessor,
            Map<ParamKey, Object> params) {
        if (value instanceof Point p)
            return p;
        if (value instanceof String str)
            return parsePointFromString(str);
        throw new NativSQLException("Cannot parse Point from value: " + value);
    }

    @Override
    protected Object toDatabaseValue(Point value, Map<ParamKey, Object> params) {
        DbDataType dataType = (DbDataType) params.get(TypeParamKey.DB_DATA_TYPE);
        // For IDENTITY type, return as-is
        if (dataType == DbDataType.IDENTITY) {
            return value;
        }

        // Point types must be converted to geometry, no other conversion is allowed
        if (dataType != null) {
            throw new NativSQLException(
                    "Cannot convert Point to " + dataType);
        }
        return value.toString();
    }

    @Override
    public String formatParameter(String paramName, Map<ParamKey, Object> params) {
        // MySQL geometry type
        return "ST_GeomFromText(:" + paramName + ")";
    }

    private Point parsePointFromString(String value) {
        try {
            return new Point(value);
        } catch (Exception e) {
            throw new NativSQLException("Failed to parse Point: " + value, e);
        }
    }
}
