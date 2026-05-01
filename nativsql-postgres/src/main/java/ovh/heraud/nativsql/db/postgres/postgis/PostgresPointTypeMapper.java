package ovh.heraud.nativsql.db.postgres.postgis;

import java.sql.ResultSet;
import ovh.heraud.nativsql.util.FieldAccessor;
import java.util.Map;

import org.postgis.PGgeometry;
import org.postgis.Point;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;

/**
 * PostgreSQL-specific Point mapper for PostGIS geometry types.
 * Handles both reading from and writing to PostgreSQL geometry columns.
 */
public class PostgresPointTypeMapper implements ITypeMapper<Point> {

    @Override
    public Point map(ResultSet rs, String columnName, DbDataType dataType, FieldAccessor<?> fieldAccessor,
            Map<TypeParamKey, Object> params)
            throws NativSQLException {
        try {
            Object value = rs.getObject(columnName);
            if (value == null) {
                return null;
            }
            if (value instanceof Point p) {
                return p;
            }
            if (value instanceof PGgeometry pgGeom) {
                return (Point) pgGeom.getGeometry();
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
        if (value instanceof PGgeometry pgGeom)
            return (Point) pgGeom.getGeometry();
        if (value instanceof String str) {
            try {
                return (Point) new PGgeometry(str).getGeometry();
            } catch (Exception e) {
                throw new NativSQLException("Failed to parse Point: " + str, e);
            }
        }
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

        // Use PGgeometry to wrap the Point
        return new PGgeometry(value);
    }

    @Override
    public String formatParameter(String paramName) {
        // PostGIS geometry type needs explicit casting
        return "(:" + paramName + ")::geometry";
    }
}
