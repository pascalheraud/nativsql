package ovh.heraud.nativsql.db.postgres;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.annotation.type.ParamKey;
import ovh.heraud.nativsql.db.postgres.postgis.PostgresPostGISDialect;
import ovh.heraud.nativsql.domain.postgres.Address;
import ovh.heraud.nativsql.domain.postgres.ContactInfo;
import ovh.heraud.nativsql.domain.postgres.User;
import ovh.heraud.nativsql.domain.postgres.UserStatus;
import ovh.heraud.nativsql.mapper.ITypeMapper;
import ovh.heraud.nativsql.util.FieldAccessor;
import ovh.heraud.nativsql.util.Fields;
import ovh.heraud.nativsql.util.ReflectionUtils;
import ovh.heraud.nativsql.util.TypeInfo;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generalizes the "does this type get a ::cast in formatParameter" question
 * (issue #118) across every type the real {@link PostgresDialect} /
 * {@link PostgresPostGISDialect} know how to map — not just Boolean —
 * covering both PostgreSQL-specific mappers with bespoke value conversion
 * (Boolean, UUID, Enum, Composite, Point) and plain nativsql types that get
 * their cast from the generic {@code PostgresCastingTypeMapper} decorator
 * (Long, Integer, String, LocalDateTime, Instant...), using real fields of
 * the {@link User} and {@link ContactInfo} test entities rather than
 * synthetic doubles. Every mapped type is expected to cast: PostgreSQL can
 * fail to infer a bind parameter's type from context alone (issue #118 was
 * this exact failure for Boolean), so casting is applied uniformly rather
 * than only for the types where the bug was first reported.
 */
class PostgresDialectFormatParameterTest {

    private static final Fields USER_FIELDS = ReflectionUtils.getFields(User.class);
    private static final Fields CONTACT_INFO_FIELDS = ReflectionUtils.getFields(ContactInfo.class);

    private static final AnnotationManager ANNOTATION_MANAGER = new AnnotationManager();
    private static final PostgresPostGISDialect DIALECT;

    static {
        ANNOTATION_MANAGER.setCompositeTypeInfo(Address.class, "address_type");
        ANNOTATION_MANAGER.setEnumSqlType(UserStatus.class, "user_status");
        DIALECT = new PostgresPostGISDialect(new PostgresDialect());
    }

    static Stream<Arguments> types() {
        return Stream.of(
                // PostgreSQL-specific mappers with bespoke value conversion.
                Arguments.of("ContactInfo.isPrimary (Boolean)", CONTACT_INFO_FIELDS, "isPrimary", "boolean"),
                Arguments.of("User.externalId (UUID)", USER_FIELDS, "externalId", "uuid"),
                Arguments.of("User.status (enum)", USER_FIELDS, "status", "user_status"),
                Arguments.of("User.address (composite)", USER_FIELDS, "address", "address_type"),
                Arguments.of("User.position (postgis Point)", USER_FIELDS, "position", "geometry"),

                // Plain nativsql types, cast via the generic PostgresCastingTypeMapper.
                Arguments.of("User.id (Long)", USER_FIELDS, "id", "bigint"),
                Arguments.of("User.age (Integer, @Type BIG_INTEGER)", USER_FIELDS, "age", "numeric"),
                Arguments.of("User.firstName (String)", USER_FIELDS, "firstName", "text"),
                Arguments.of("User.createdAt (LocalDateTime)", USER_FIELDS, "createdAt", "timestamp"),
                Arguments.of("User.updateDate (Instant)", USER_FIELDS, "updateDate", "timestamptz"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("types")
    void formatParameterCastsToExpectedPostgresType(String description, Fields fields, String fieldName,
            String expectedSqlType) {
        FieldAccessor<?> fieldAccessor = fields.getOrNull(fieldName);
        assertThat(fieldAccessor).as("field " + fieldName + " should exist").isNotNull();

        ITypeMapper<?> mapper = DIALECT.getMapper(fieldAccessor, ANNOTATION_MANAGER);
        assertThat(mapper).as("mapper for " + fieldName).isNotNull();

        TypeInfo typeInfo = ANNOTATION_MANAGER.getTypeInfo(fieldAccessor);
        @SuppressWarnings({ "unchecked", "rawtypes" })
        String formatted = ((ITypeMapper) mapper).formatParameter("p", (Map<ParamKey, Object>) typeInfo.getParams());

        assertThat(formatted).isEqualTo("(:p)::" + expectedSqlType);
    }
}
