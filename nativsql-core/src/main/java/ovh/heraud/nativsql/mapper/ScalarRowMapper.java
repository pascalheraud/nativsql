package ovh.heraud.nativsql.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;

import org.springframework.jdbc.core.RowMapper;

import ovh.heraud.nativsql.exception.NativSQLException;

/**
 * RowMapper for base/JDBC scalar result types (String, Long, Integer, ...)
 * used by {@code findExternal}/{@code findAllExternal} when the query
 * returns exactly one column.
 *
 * @param <T> the scalar type to map
 */
public class ScalarRowMapper<T> implements RowMapper<T> {

    private final Class<T> resultClass;
    private final ITypeMapper<T> typeMapper;

    public ScalarRowMapper(Class<T> resultClass, ITypeMapper<T> typeMapper) {
        this.resultClass = resultClass;
        this.typeMapper = typeMapper;
    }

    @Override
    public T mapRow(ResultSet rs, int rowNum) throws SQLException {
        int columnCount = rs.getMetaData().getColumnCount();
        if (columnCount != 1) {
            throw new NativSQLException(
                    "findExternal/findAllExternal with result type '" + resultClass.getSimpleName()
                            + "' requires the query to return exactly one column, but it returned "
                            + columnCount + ". Use an entity/bean result class for multi-column queries.");
        }
        String columnLabel = rs.getMetaData().getColumnLabel(1);
        return typeMapper.map(rs, columnLabel, null, Collections.emptyMap());
    }
}
