package ovh.heraud.nativsql.repository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.generic.GenericDialect;
import ovh.heraud.nativsql.db.generic.mapper.EnumStringMapper;
import ovh.heraud.nativsql.db.generic.mapper.UUIDTypeMapper;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import ovh.heraud.nativsql.util.Fields;
import ovh.heraud.nativsql.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link NamedParamSqlCaster} (issue #118): named parameters
 * in hand-written findExternal/findAllExternal SQL must be cast when their
 * type can be determined, and left untouched otherwise.
 */
class NamedParamSqlCasterTest {

    /** Test entity with a Boolean field, used to exercise entity-field matching. */
    @Getter
    static class TestEntity {
        private Boolean active;
    }

    enum Status {
        OPEN, CLOSED
    }

    /** Test entity with UUID and enum fields, used to exercise non-Boolean type resolution. */
    @Getter
    static class UuidAndEnumEntity {
        private UUID id;
        private Status status;
    }

    /**
     * Test dialect that always casts Boolean parameters, like
     * PostgresBooleanTypeMapper.
     */
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

    /**
     * Test dialect that casts UUID parameters like PostgresUUIDTypeMapper
     * ("::uuid") and enum parameters like PostgresEnumMapper ("::status"), to
     * prove NamedParamSqlCaster is generic across mapper types, not just
     * Boolean.
     */
    static class CastingUuidAndEnumDialect extends GenericDialect {
        CastingUuidAndEnumDialect() {
            super();
        }

        @Override
        public ITypeMapper<UUID> getUUIDMapper() {
            return new UUIDTypeMapper() {
                @Override
                public String formatParameter(String paramName, Map<ParamKey, Object> params) {
                    return "(:" + paramName + ")::uuid";
                }
            };
        }

        @Override
        public <E extends Enum<E>> ITypeMapper<E> getEnumMapper() {
            return new EnumStringMapper<E>() {
                @Override
                public String formatParameter(String paramName, Map<ParamKey, Object> params) {
                    return "(:" + paramName + ")::status";
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

    @Test
    void castsParameterMatchingEntityFieldEvenWhenNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("active", null);

        String result = caster.castNamedParameters("select * from t where :active is null or active = :active",
                params, entityFields, dialect, annotationManager);

        assertThat(result).isEqualTo(
                "select * from t where (:active)::boolean is null or active = (:active)::boolean");
    }

    @Test
    void castsParameterMatchingEntityFieldWhenNonNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("active", true);

        String result = caster.castNamedParameters("select * from t where :active", params, entityFields, dialect,
                annotationManager);

        assertThat(result).isEqualTo("select * from t where (:active)::boolean");
    }

    @Test
    void castsUnmatchedParameterFromRuntimeClassWhenNonNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("filterFlag", true);

        String result = caster.castNamedParameters("select * from t where :filterFlag", params, entityFields,
                dialect, annotationManager);

        assertThat(result).isEqualTo("select * from t where (:filterFlag)::boolean");
    }

    @Test
    void doesNotCastListValuedParameterUsedInInClause() {
        // "col IN (:name)" is expanded into "?, ?, ..." by the JDBC template's own
        // named-param parsing; casting ":name" here would corrupt that expansion
        // into e.g. "IN ((?, ?)::boolean)".
        Map<String, Object> params = new HashMap<>();
        params.put("active", java.util.List.of(true, false));

        String sql = "select * from t where active in (:active)";
        String result = caster.castNamedParameters(sql, params, entityFields, dialect, annotationManager);

        assertThat(result).isEqualTo(sql);
    }

    @Test
    void castsUnmatchedNullParameterWrappedInNullableParam() {
        Map<String, Object> params = new HashMap<>();
        params.put("filterFlag", NullableParam.of(Boolean.class));

        String result = caster.castNamedParameters("select * from t where :filterFlag", params, entityFields,
                dialect, annotationManager);

        assertThat(result).isEqualTo("select * from t where (:filterFlag)::boolean");
    }

    @Test
    void doesNotCastUnmatchedPlainNullParameter() {
        Map<String, Object> params = new HashMap<>();
        params.put("filterFlag", null);

        String sql = "select * from t where :filterFlag is null";
        String result = caster.castNamedParameters(sql, params, entityFields, dialect, annotationManager);

        assertThat(result).isEqualTo(sql);
    }

    @Test
    void doesNotRewriteParameterLikeTokenInsideStringLiteral() {
        Map<String, Object> params = new HashMap<>();
        params.put("active", true);

        String sql = "select * from t where name = 'contains :active inside' and active = :active";
        String result = caster.castNamedParameters(sql, params, entityFields, dialect, annotationManager);

        assertThat(result).isEqualTo(
                "select * from t where name = 'contains :active inside' and active = (:active)::boolean");
    }

    @Test
    void castsUuidParameterMatchingEntityField() {
        Fields uuidAndEnumFields = ReflectionUtils.getFields(UuidAndEnumEntity.class);
        DatabaseDialect uuidAndEnumDialect = new CastingUuidAndEnumDialect();
        Map<String, Object> params = new HashMap<>();
        params.put("id", UUID.randomUUID());

        String result = caster.castNamedParameters("select * from t where id = :id", params, uuidAndEnumFields,
                uuidAndEnumDialect, annotationManager);

        assertThat(result).isEqualTo("select * from t where id = (:id)::uuid");
    }

    @Test
    void castsEnumParameterMatchingEntityField() {
        Fields uuidAndEnumFields = ReflectionUtils.getFields(UuidAndEnumEntity.class);
        DatabaseDialect uuidAndEnumDialect = new CastingUuidAndEnumDialect();
        Map<String, Object> params = new HashMap<>();
        params.put("status", Status.OPEN);

        String result = caster.castNamedParameters("select * from t where status = :status", params,
                uuidAndEnumFields, uuidAndEnumDialect, annotationManager);

        assertThat(result).isEqualTo("select * from t where status = (:status)::status");
    }

    @Test
    void secondCallWithSameSqlUsesCachedRewriteFromFirstCall() {
        String sql = "select * from t where :active";

        Map<String, Object> first = new HashMap<>();
        first.put("active", true);
        String firstResult = caster.castNamedParameters(sql, first, entityFields, dialect, annotationManager);

        // Second call has no value at all for "active" (not even a key) -- if the
        // cache were bypassed, resolution would find nothing and skip the cast.
        Map<String, Object> second = new HashMap<>();
        String secondResult = caster.castNamedParameters(sql, second, entityFields, dialect, annotationManager);

        assertThat(secondResult).isEqualTo(firstResult);
    }
}
