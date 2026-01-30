package ovh.heraud.nativsql.db.mysql.postgis;

import java.sql.ResultSet;

import org.postgis.Point;

import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.ITypeMapper;

/**
 * MySQL-specific Point mapper for spatial coordinates.
 * Handles both reading from and writing to MySQL spatial columns.
 */
public class MySQLPointTypeMapper implements ITypeMapper<Point> {

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
            if (value instanceof String) {
                return parsePointFromString((String) value);
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
