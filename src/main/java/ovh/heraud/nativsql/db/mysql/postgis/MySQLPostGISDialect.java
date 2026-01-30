package ovh.heraud.nativsql.db.mysql.postgis;

import org.postgis.Point;

import ovh.heraud.nativsql.db.mysql.MySQLDialect;
import ovh.heraud.nativsql.mapper.ITypeMapper;

/**
 * MySQL dialect with PostGIS (Geographic Information System) support.
 *
 * Extends MySQLDialect to add support for spatial coordinates using
 * org.postgis.Point mapping.
 *
 * Use this dialect when your MySQL database uses spatial columns
 * for geographic or coordinate data.
 */
public class MySQLPostGISDialect extends MySQLDialect {

    @Override
    @SuppressWarnings("unchecked")
    public <T> ITypeMapper<T> getMapper(Class<T> targetType) {
        // Use MySQL-specific Point mapper for spatial coordinates
        if (targetType == Point.class) {
            return (ITypeMapper<T>) new MySQLPointTypeMapper();
        }

        // Fall back to parent MySQLDialect implementation
        return super.getMapper(targetType);
    }
}
