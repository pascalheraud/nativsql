package ovh.heraud.nativsql.repository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.generic.GenericDialect;
import ovh.heraud.nativsql.util.Fields;
import ovh.heraud.nativsql.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parameterized regression test for {@link NamedParamSqlCaster} (issue
 * #118): the scanner must correctly handle syntax specific to each
 * supported engine (PostgreSQL, MySQL/MariaDB, Oracle) — quoted
 * identifiers, comment styles, cast operators, string-literal escaping —
 * casting only the bound ":active" token and leaving everything else in
 * the query byte-for-byte unchanged.
 *
 * <p>
 * Cases live in {@code named-param-sql-caster-engine-queries.txt} on the
 * test classpath, one block per case, so new engine quirks can be added
 * without touching this class.
 */
class NamedParamSqlCasterEngineQueriesTest {

    private static final String CASES_RESOURCE = "/ovh/heraud/nativsql/repository/named-param-sql-caster-engine-queries.txt";

    /** Test entity with a Boolean field, matched by name to the ":active" token. */
    static class TestEntity {
        private Boolean active;
    }

    /** Test dialect that always casts Boolean parameters, like PostgresBooleanTypeMapper. */
    static class CastingBooleanDialect extends GenericDialect {
        CastingBooleanDialect() {
            super();
        }

        @Override
        public ovh.heraud.nativsql.mapper.ITypeMapper<Boolean> getBooleanMapper() {
            return new ovh.heraud.nativsql.db.generic.mapper.BooleanTypeMapper() {
                @Override
                public String formatParameter(String paramName,
                        Map<ovh.heraud.nativsql.annotation.type.ParamKey, Object> params) {
                    return "(:" + paramName + ")::boolean";
                }
            };
        }
    }

    private NamedParamSqlCaster caster;
    private Fields entityFields;
    private DatabaseDialect dialect;
    private AnnotationManager annotationManager;

    @BeforeEach
    void setUp() {
        caster = new NamedParamSqlCaster();
        entityFields = ReflectionUtils.getFields(TestEntity.class);
        dialect = new CastingBooleanDialect();
        annotationManager = new AnnotationManager();
    }

    static Stream<Arguments> cases() {
        List<Arguments> cases = new ArrayList<>();
        String desc = null;
        String in = null;
        try (InputStream stream = NamedParamSqlCasterEngineQueriesTest.class.getResourceAsStream(CASES_RESOURCE)) {
            for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.startsWith("DESC: ")) {
                    desc = line.substring("DESC: ".length()).trim();
                } else if (line.startsWith("IN: ")) {
                    in = line.substring("IN: ".length());
                } else if (line.startsWith("OUT: ")) {
                    cases.add(Arguments.of(desc, in, line.substring("OUT: ".length())));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void castsBoundParamAndLeavesEngineSpecificSyntaxUnchanged(String description, String in, String out) {
        Map<String, Object> params = new HashMap<>();
        params.put("active", true);

        String result = caster.castNamedParameters(in, params, entityFields, dialect, annotationManager);

        assertThat(result).isEqualTo(out);
    }
}
