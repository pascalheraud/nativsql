package ovh.heraud.nativsql.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import lombok.Getter;
import lombok.Setter;
import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.SnakeCaseIdentifierConverter;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.repository.GenericRepository;

/**
 * Unit tests for {@link FindQuery#selectExpression}, covering computed
 * SQL-expression SELECT columns, {@code {{table}}} substitution, and
 * collision/validation rules against plain select(...) columns.
 */
class FindQuerySelectExpressionTest {

    @Mock
    private GenericRepository<TestEntity, Long> mockRepository;

    @Mock
    private AnnotationManager mockAnnotationManager;

    private FindQuery<TestEntity, Long> findQuery;
    private SnakeCaseIdentifierConverter identifierConverter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockRepository.getAnnotationManager()).thenReturn(mockAnnotationManager);
        when(mockRepository.getTableName()).thenReturn("test_entity");

        findQuery = FindQuery.of(mockRepository);
        identifierConverter = new SnakeCaseIdentifierConverter();
    }

    @Test
    void selectExpression_adds_raw_sql_with_alias() {
        // Given: a query selecting a plain column plus a computed subquery
        findQuery.select("id")
                .selectExpression("orderCount", "(SELECT COUNT(*) FROM orders WHERE client_id = {{table}}.id)");

        // When: building the SQL
        String sql = findQuery.buildString(identifierConverter);

        // Then: the expression is emitted with {{table}} resolved to the repository's table name
        assertThat(sql).contains(
                "(SELECT COUNT(*) FROM orders WHERE client_id = test_entity.id) AS \"orderCount\"");
    }

    @Test
    void selectExpression_table_token_substituted_with_actual_table_name() {
        // Given: an expression using only the {{table}} token
        findQuery.selectExpression("x", "{{table}}.status");

        // When: building the SQL
        String sql = findQuery.buildString(identifierConverter);

        // Then: {{table}} is substituted with "test_entity"
        assertThat(sql).contains("test_entity.status AS \"x\"");
    }

    @Test
    void selectExpression_getter_overload_derives_alias() {
        // Given: an expression added via a Getter<R> reference for the alias
        findQuery.selectExpression((ReflectionUtils.Getter<SomeReport>) SomeReport::getOrderCount, "1+1");

        // When: building the SQL
        String sql = findQuery.buildString(identifierConverter);

        // Then: the alias is derived from the getter's property name
        assertThat(sql).contains("1+1 AS \"orderCount\"");
    }

    @Test
    void selectExpression_with_params_merges_into_getParameters() {
        // Given: an expression with a bound named parameter
        Instant since = Instant.parse("2024-01-01T00:00:00Z");
        findQuery.selectExpression("recent", "{{table}}.created_at >= :since", Map.of("since", since));

        // When: reading back the parameters
        Map<String, Object> params = findQuery.getParameters();

        // Then: the expression's parameter is present
        assertThat(params).containsEntry("since", since);
    }

    @Test
    void selectExpression_blank_alias_throws() {
        // Given / When / Then: a blank alias is rejected
        assertThatThrownBy(() -> findQuery.selectExpression("  ", "1"))
                .isInstanceOf(NativSQLException.class);
    }

    @Test
    void selectExpression_blank_sql_throws() {
        // Given / When / Then: a blank SQL expression is rejected
        assertThatThrownBy(() -> findQuery.selectExpression("x", "  "))
                .isInstanceOf(NativSQLException.class);
    }

    @Test
    void selectExpression_duplicate_alias_throws() {
        // Given: an expression already registered under "x"
        findQuery.selectExpression("x", "1");

        // When / Then: registering the same alias again throws
        assertThatThrownBy(() -> findQuery.selectExpression("x", "2"))
                .isInstanceOf(NativSQLException.class)
                .hasMessage("Duplicate expression alias 'x'");
    }

    @Test
    void selectExpression_alias_colliding_with_existing_select_column_throws() {
        // Given: "status" already selected as a plain column
        findQuery.select("id", "status");

        // When / Then: selectExpression with the same alias throws
        assertThatThrownBy(() -> findQuery.selectExpression("status", "1"))
                .isInstanceOf(NativSQLException.class)
                .hasMessage("Expression alias 'status' collides with a plain select(...) column of the same name");
    }

    @Test
    void select_column_colliding_with_existing_selectExpression_alias_throws() {
        // Given: "status" already registered as a selectExpression alias
        findQuery.selectExpression("status", "1");

        // When / Then: select(...) with the same column name throws, regardless of call order
        assertThatThrownBy(() -> findQuery.select("id", "status"))
                .isInstanceOf(NativSQLException.class)
                .hasMessage("Column 'status' collides with a selectExpression(...) alias of the same name");
    }

    @Test
    void selectExpression_duplicate_param_name_with_where_condition_throws() {
        // Given: a WHERE condition using param name "status" and an expression reusing it
        findQuery.select("id")
                .whereAndEquals("status", "ACTIVE")
                .selectExpression("x", "... :status", Map.of("status", "x"));

        // When / Then: reading back parameters detects the collision
        assertThatThrownBy(() -> findQuery.getParameters())
                .isInstanceOf(NativSQLException.class);
    }

    @Test
    void buildSql_with_no_columns_expressions_or_joins_throws() {
        // Given / When / Then: an empty query cannot be built
        assertThatThrownBy(() -> findQuery.buildString(identifierConverter))
                .isInstanceOf(NativSQLException.class)
                .hasMessage("At least one column must be selected");
    }

    @Test
    void select_and_selectExpression_combine_in_generated_sql() {
        // Given: a mix of plain columns and a non-colliding computed expression
        findQuery.select("id", "name")
                .selectExpression("total", "(SELECT 1)");

        // When: building the SQL
        String sql = findQuery.buildString(identifierConverter);

        // Then: all three columns are present, in declaration order
        assertThat(sql).isEqualTo("""
                SELECT
                    test_entity.id AS "id",
                    test_entity.name AS "name",
                    (SELECT 1) AS "total"
                FROM test_entity
                """);
    }

    @Test
    void selectExpression_alone_overrides_an_inherited_field_alias() {
        // Given: "status" is NOT passed to select(...) — only computed via selectExpression
        findQuery.select("id")
                .selectExpression("status",
                        "CASE WHEN {{table}}.deleted_at IS NOT NULL THEN 'ARCHIVED' ELSE status END");

        // When: building the SQL
        String sql = findQuery.buildString(identifierConverter);

        // Then: exactly one "status" column is emitted, holding the CASE expression
        assertThat(sql).contains(
                "CASE WHEN test_entity.deleted_at IS NOT NULL THEN 'ARCHIVED' ELSE status END AS \"status\"");
        assertThat(sql.split("AS \"status\"", -1)).hasSize(2);
    }

    // ==================== Test Entities ====================

    @Getter
    @Setter
    static class TestEntity implements IEntity<Long> {
        private Long id;
        private String name;
        private String status;
    }

    @Getter
    @Setter
    static class SomeReport extends TestEntity {
        private Long orderCount;
    }
}
