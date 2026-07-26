package ovh.heraud.nativsql.db.generic.mapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.exception.ConversionException;
import ovh.heraud.nativsql.mapper.AbstractTypeMapper;
import ovh.heraud.nativsql.util.FieldAccessor;

/**
 * TypeMapper for {@link Instant}.
 *
 * <p>
 * JDBC drivers (e.g. PostgreSQL's) cannot infer a SQL type for a bare
 * {@code java.time.Instant} passed to {@code PreparedStatement.setObject(...)} —
 * unlike {@link java.time.LocalDateTime}, which drivers understand natively.
 * This mapper converts to/from {@link Timestamp} explicitly so the value can be
 * bound without relying on driver-specific {@code Instant} support.
 */
public class InstantTypeMapper extends AbstractTypeMapper<Instant> {

    @Override
    public Instant fromValue(Object raw, FieldAccessor<?> fieldAccessor,
            Map<ParamKey, Object> params) throws ConversionException {
        if (raw instanceof Instant instant)
            return instant;
        if (raw instanceof Timestamp ts)
            return ts.toInstant();
        if (raw instanceof java.util.Date date)
            return date.toInstant();
        if (raw instanceof String str) {
            try {
                return Instant.parse(str);
            } catch (DateTimeParseException e) {
                throw new ConversionException(Instant.class, e);
            }
        }
        throw new ConversionException(Instant.class);
    }

    @Override
    protected Object toDatabaseValue(Instant value, Map<ParamKey, Object> params) {
        return Timestamp.from(value);
    }
}
