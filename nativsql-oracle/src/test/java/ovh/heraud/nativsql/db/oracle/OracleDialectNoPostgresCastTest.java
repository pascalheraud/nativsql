package ovh.heraud.nativsql.db.oracle;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import ovh.heraud.nativsql.util.FieldAccessor;
import ovh.heraud.nativsql.util.TypeInfo;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL's "::type" parameter casting (issue #118) is dialect-specific —
 * it must not leak into Oracle, which doesn't support that syntax.
 * {@link OracleDialect} only special-cases UUID (its own
 * {@code OracleUUIDTypeMapper}, itself uncast) and otherwise chains to
 * {@code GenericDialect} for every type, so {@code formatParameter} stays
 * uncast across the board — this test asserts that stays true across the
 * same type surface exercised for PostgreSQL.
 */
class OracleDialectNoPostgresCastTest {

    private static final AnnotationManager ANNOTATION_MANAGER = new AnnotationManager();
    private static final OracleDialect DIALECT = new OracleDialect();

    static Stream<Arguments> types() {
        return Stream.of(
                Arguments.of("Boolean", Boolean.class),
                Arguments.of("UUID", UUID.class),
                Arguments.of("Long", Long.class),
                Arguments.of("Integer", Integer.class),
                Arguments.of("String", String.class),
                Arguments.of("BigDecimal", BigDecimal.class),
                Arguments.of("LocalDateTime", LocalDateTime.class),
                Arguments.of("Instant", Instant.class),
                Arguments.of("byte[]", byte[].class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("types")
    void formatParameterNeverCastsOnOracle(String description, Class<?> type) {
        FieldAccessor<?> fieldAccessor = new FieldAccessor<>(type);

        ITypeMapper<?> mapper = DIALECT.getMapper(fieldAccessor, ANNOTATION_MANAGER);
        assertThat(mapper).as("mapper for " + description).isNotNull();

        TypeInfo typeInfo = ANNOTATION_MANAGER.getTypeInfo(fieldAccessor);
        @SuppressWarnings({ "unchecked", "rawtypes" })
        String formatted = ((ITypeMapper) mapper).formatParameter("p", (Map<ParamKey, Object>) typeInfo.getParams());

        assertThat(formatted).as(description).isEqualTo(":p");
    }
}
