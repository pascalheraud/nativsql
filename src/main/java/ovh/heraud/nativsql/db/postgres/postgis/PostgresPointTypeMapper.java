package ovh.heraud.nativsql.db.postgres.postgis;

import java.sql.ResultSet;

import org.postgis.PGgeometry;
import org.postgis.Point;

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
    public Object toDatabase(Point value) {
        if (value == null) {
            return null;
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
