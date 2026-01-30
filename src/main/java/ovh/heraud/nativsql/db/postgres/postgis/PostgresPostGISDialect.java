package ovh.heraud.nativsql.db.postgres.postgis;

import org.postgis.Point;

import ovh.heraud.nativsql.db.AbstractChainedDialect;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.postgres.PostgresDialect;
import ovh.heraud.nativsql.mapper.ITypeMapper;

/**
 * PostgreSQL dialect with PostGIS support using Chain of Responsibility
 * pattern.
 *
 * Chains to PostgresDialect for non-PostGIS types, allowing specialized
 * handling
 * of PostGIS geometry types (Point) while delegating standard PostgreSQL types
 * (UUID, enums, JSON, composites) to the parent dialect.
 *
 * Chain structure:
 * PostgresPostGISDialect (Point) -> PostgresDialect (UUID, enums, JSON,
 * composites) -> GenericDialect (basic types)
 *
 * Use this dialect when your PostgreSQL database uses PostGIS extensions
 * for spatial data types.
 */
public class PostgresPostGISDialect extends AbstractChainedDialect {

    /**
     * Create a PostGIS dialect that chains to the standard PostgreSQL dialect.
     *
     * @param postgresDialect the PostgreSQL dialect to delegate to
     */
    public PostgresPostGISDialect(DatabaseDialect postgresDialect) {
        super(postgresDialect);
    }

    /**
     * Create a PostGIS dialect with a default PostgreSQL dialect.
     */
    public PostgresPostGISDialect() {
        super(new PostgresDialect());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ITypeMapper<T> getMapper(Class<T> targetType) {
        // Handle PostGIS Point type
        if (targetType == Point.class) {
            return (ITypeMapper<T>) new PostgresPointTypeMapper();
        }

        // Delegate to the next dialect in the chain (PostgreSQL dialect)
        return super.getMapper(targetType);
    }
}
