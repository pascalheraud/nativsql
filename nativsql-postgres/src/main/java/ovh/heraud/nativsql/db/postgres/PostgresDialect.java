package ovh.heraud.nativsql.db.postgres;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.generic.GenericDialect;
import ovh.heraud.nativsql.db.postgres.mapper.PostgreJSONTypeMapper;
import ovh.heraud.nativsql.db.postgres.mapper.PostgresBooleanTypeMapper;
import ovh.heraud.nativsql.db.postgres.mapper.PostgresByteArrayTypeMapper;
import ovh.heraud.nativsql.db.postgres.mapper.PostgresCastingTypeMapper;
import ovh.heraud.nativsql.db.postgres.mapper.PostgresCompositeTypeMapper;
import ovh.heraud.nativsql.db.postgres.mapper.PostgresEnumMapper;
import ovh.heraud.nativsql.db.postgres.mapper.PostgresStringTypeMapper;
import ovh.heraud.nativsql.db.postgres.mapper.PostgresUUIDTypeMapper;
import ovh.heraud.nativsql.mapper.ITypeMapper;

/**
 * PostgreSQL-specific implementation of DatabaseDialect.
 *
 * Handles PostgreSQL-specific SQL formatting and type conversions including:
 * - Enum types with :: casting syntax
 * - Composite types with (val1,val2,val3) format
 * - JSON/JSONB types
 * - UUID types
 *
 * Part of the Chain of Responsibility pattern, chains to DefaultDialect for
 * unmapped types.
 * Can be extended to create specialized PostgreSQL dialects (e.g., with PostGIS
 * support).
 */
public class PostgresDialect extends GenericDialect {

    /**
     * Create a PostgreSQL dialect that chains to the default dialect.
     *
     * @param nextDialect the next dialect to delegate to
     */
    public PostgresDialect(DatabaseDialect nextDialect) {
        super(nextDialect);
    }

    /**
     * Create a PostgreSQL dialect with a generic dialect as the next in chain.
     */
    public PostgresDialect() {
        this(new GenericDialect());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends Enum<E>> ITypeMapper<E> getEnumMapper() {
        return (ITypeMapper<E>) new PostgresEnumMapper<>();
    }

    @Override
    public <T> ITypeMapper<T> getJsonMapper() {
        // PostgreSQL uses the dedicated PostgreJSONTypeMapper
        return new PostgreJSONTypeMapper<>();
    }

    @Override
    public <T> ITypeMapper<T> getCompositeMapper() {
        return new PostgresCompositeTypeMapper<>();
    }

    @Override
    public ITypeMapper<UUID> getUUIDMapper() {
        return new PostgresUUIDTypeMapper();
    }

    @Override
    public ITypeMapper<String> getStringMapper() {
        return new PostgresStringTypeMapper();
    }

    @Override
    public ITypeMapper<byte[]> getByteArrayMapper() {
        return new PostgresByteArrayTypeMapper();
    }

    @Override
    public ITypeMapper<Boolean> getBooleanMapper() {
        return new PostgresBooleanTypeMapper();
    }

    @Override
    public ITypeMapper<Long> getLongMapper() {
        return new PostgresCastingTypeMapper<>(super.getLongMapper(), "bigint");
    }

    @Override
    public ITypeMapper<Integer> getIntegerMapper() {
        return new PostgresCastingTypeMapper<>(super.getIntegerMapper(), "integer");
    }

    @Override
    public ITypeMapper<Short> getShortMapper() {
        return new PostgresCastingTypeMapper<>(super.getShortMapper(), "smallint");
    }

    @Override
    public ITypeMapper<Byte> getByteMapper() {
        return new PostgresCastingTypeMapper<>(super.getByteMapper(), "smallint");
    }

    @Override
    public ITypeMapper<Float> getFloatMapper() {
        return new PostgresCastingTypeMapper<>(super.getFloatMapper(), "real");
    }

    @Override
    public ITypeMapper<Double> getDoubleMapper() {
        return new PostgresCastingTypeMapper<>(super.getDoubleMapper(), "double precision");
    }

    @Override
    public ITypeMapper<BigDecimal> getBigDecimalMapper() {
        return new PostgresCastingTypeMapper<>(super.getBigDecimalMapper(), "numeric");
    }

    @Override
    public ITypeMapper<BigInteger> getBigIntegerMapper() {
        return new PostgresCastingTypeMapper<>(super.getBigIntegerMapper(), "numeric");
    }

    @Override
    public ITypeMapper<LocalDate> getLocalDateMapper() {
        return new PostgresCastingTypeMapper<>(super.getLocalDateMapper(), "date");
    }

    @Override
    public ITypeMapper<LocalDateTime> getLocalDateTimeMapper() {
        return new PostgresCastingTypeMapper<>(super.getLocalDateTimeMapper(), "timestamp");
    }

    @Override
    public ITypeMapper<Instant> getInstantMapper() {
        return new PostgresCastingTypeMapper<>(super.getInstantMapper(), "timestamptz");
    }

}
