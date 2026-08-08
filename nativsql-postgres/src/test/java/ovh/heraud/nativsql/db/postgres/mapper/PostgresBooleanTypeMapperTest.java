package ovh.heraud.nativsql.db.postgres.mapper;

import java.util.Map;

import org.junit.jupiter.api.Test;

import ovh.heraud.nativsql.annotation.DbDataType;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.annotation.type.TypeParamKey;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PostgresBooleanTypeMapper} (issue #118): named
 * parameters must be cast to ::boolean so PostgreSQL can determine their
 * type even when used without a typed comparison.
 */
class PostgresBooleanTypeMapperTest {

    private final PostgresBooleanTypeMapper mapper = new PostgresBooleanTypeMapper();

    @Test
    void formatParameterCastsToBooleanWhenNoDbDataType() {
        assertThat(mapper.formatParameter("p", Map.of())).isEqualTo("(:p)::boolean");
    }

    @Test
    void formatParameterCastsToBooleanWhenDbDataTypeIsBoolean() {
        Map<ParamKey, Object> params = Map.of(TypeParamKey.DB_DATA_TYPE, DbDataType.BOOLEAN);
        assertThat(mapper.formatParameter("p", params)).isEqualTo("(:p)::boolean");
    }

    @Test
    void formatParameterCastsToTextWhenDbDataTypeIsString() {
        // A Boolean field written to a STRING column (@Type(STRING)) is sent as
        // "true"/"false" text, so the cast follows the declared column type, not
        // the Java field's own natural boolean type.
        Map<ParamKey, Object> params = Map.of(TypeParamKey.DB_DATA_TYPE, DbDataType.STRING);
        assertThat(mapper.formatParameter("p", params)).isEqualTo("(:p)::text");
    }

    @Test
    void formatParameterCastsToIntegerWhenDbDataTypeIsInteger() {
        Map<ParamKey, Object> params = Map.of(TypeParamKey.DB_DATA_TYPE, DbDataType.INTEGER);
        assertThat(mapper.formatParameter("p", params)).isEqualTo("(:p)::integer");
    }
}
