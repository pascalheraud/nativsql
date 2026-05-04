package ovh.heraud.nativsql.db.mysql.postgis;

import java.sql.ResultSet;
import java.util.Map;

import org.postgis.Point;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import ovh.heraud.nativsql.util.FieldAccessor;

/**
 * MySQL-specific Point mapper for spatial coordinates.
 * Handles both reading from and writing to MySQL spatial columns.
 */
public class MySQLPointTypeMapper implements ITypeMapper<Point> {

    @Override
    public Point map(ResultSet rs, String columnName, DbDataType dataType, FieldAccessor<?> fieldAccessor,
            Map<TypeParamKey, Object> params)
            throws NativSQLException {
        try {
            Object value = rs.getObject(columnName);
            if (value == null) {
                return null;
            }
            if (value instanceof Point) {
                return (Point) value;
            }
            if (value instanceof String) {
                return parsePointFromString((String) value);
            }
            throw new NativSQLException("Cannot parse Point from value: " + value);
        } catch (java.sql.SQLException e) {
            throw new NativSQLException("Failed to map Point from column: " + columnName, e);
        }
    }

    @Override
    public Point fromValue(Object value, DbDataType dataType, FieldAccessor<?> fieldAccessor,
            Map<TypeParamKey, Object> params) {
        if (value == null)
            return null;
        if (value instanceof Point p)
            return p;
        if (value instanceof String str)
            return parsePointFromString(str);
        throw new NativSQLException("Cannot parse Point from value: " + value);
    }

    @Override
    public Object toDatabase(Point value, DbDataType dataType, Map<TypeParamKey, Object> params) {
        if (value == null) {
            return null;
        }

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
    public String formatParameter(String paramName) {
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
