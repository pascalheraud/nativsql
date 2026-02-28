package ovh.heraud.nativsql.db.postgres.postgis;

import java.sql.ResultSet;

import org.postgis.PGgeometry;
import org.postgis.Point;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;

/**
 * PostgreSQL-specific Point mapper for PostGIS geometry types.
 * Handles both reading from and writing to PostgreSQL geometry columns.
 */
public class PostgresPointTypeMapper implements ITypeMapper<Point> {

    @Override
    public Point map(ResultSet rs, String columnName) throws NativSQLException {
        try {
            Object value = rs.getObject(columnName);
            if (value == null) {
                return null;
            }
            if (value instanceof Point) {
                return (Point) value;
            }
            if (value instanceof PGgeometry) {
                PGgeometry pgGeom = (PGgeometry) value;
                return (Point) pgGeom.getGeometry();
            }
            throw new NativSQLException("Cannot parse Point from value: " + value);
        } catch (java.sql.SQLException e) {
            throw new NativSQLException("Failed to map Point from column: " + columnName, e);
        }
    }

    @Override
    public Object toDatabase(Point value, DbDataType dataType) {
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
