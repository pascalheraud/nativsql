package ovh.heraud.nativsql.mapper;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import ovh.heraud.nativsql.db.generic.mapper.LongTypeMapper;
import ovh.heraud.nativsql.exception.NativSQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ScalarRowMapper}, used by
 * {@code findExternal}/{@code findAllExternal} to map a single-column query
 * result directly to a base/scalar type (issue #111).
 */
@ExtendWith(MockitoExtension.class)
class ScalarRowMapperTest {

    @Mock
    private ResultSet resultSet;

    @Mock
    private ResultSetMetaData metaData;

    private ScalarRowMapper<Long> mapper;

    @BeforeEach
    void setUp() throws Exception {
        mapper = new ScalarRowMapper<>(Long.class, new LongTypeMapper());
        when(resultSet.getMetaData()).thenReturn(metaData);
    }

    @Test
    void mapRow_maps_single_column_value_to_target_type() throws Exception {
        // Given: a ResultSet with exactly one column holding a non-null value
        when(metaData.getColumnCount()).thenReturn(1);
        when(metaData.getColumnLabel(1)).thenReturn("count");
        when(resultSet.findColumn("count")).thenReturn(1);
        when(resultSet.getObject(1)).thenReturn(42L);

        // When: mapping the row
        Long result = mapper.mapRow(resultSet, 0);

        // Then: the column value is mapped to the target type
        assertThat(result).isEqualTo(42L);
    }

    @Test
    void mapRow_returns_null_when_single_column_value_is_null() throws Exception {
        // Given: a ResultSet with exactly one column holding a null value
        when(metaData.getColumnCount()).thenReturn(1);
        when(metaData.getColumnLabel(1)).thenReturn("count");
        when(resultSet.findColumn("count")).thenReturn(1);
        when(resultSet.getObject(1)).thenReturn(null);

        // When: mapping the row
        Long result = mapper.mapRow(resultSet, 0);

        // Then: null is returned, no exception
        assertThat(result).isNull();
    }

    @Test
    void mapRow_throws_when_query_returns_two_columns() throws Exception {
        // Given: a ResultSet with two columns
        when(metaData.getColumnCount()).thenReturn(2);

        // When / Then: mapping throws, since a scalar type can't disambiguate columns
        assertThatThrownBy(() -> mapper.mapRow(resultSet, 0))
                .isInstanceOf(NativSQLException.class)
                .hasMessageContaining("Long")
                .hasMessageContaining("2");
    }

    @Test
    void mapRow_throws_when_query_returns_zero_columns() throws Exception {
        // Given: a ResultSet with zero columns
        when(metaData.getColumnCount()).thenReturn(0);

        // When / Then: mapping throws
        assertThatThrownBy(() -> mapper.mapRow(resultSet, 0))
                .isInstanceOf(NativSQLException.class)
                .hasMessageContaining("Long")
                .hasMessageContaining("0");
    }
}
