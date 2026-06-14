package ovh.heraud.nativsql.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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

class FindQueryLimitOffsetTest {

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
    void limit_appends_fetch_first_clause() {
        // Given: a FindQuery with limit(10)
        findQuery.select("id").limit(10);

        // When: building the SQL
        String sql = findQuery.buildString(identifierConverter);

        // Then: SQL ends with FETCH FIRST 10 ROWS ONLY
        assertThat(sql).isEqualTo("""
                SELECT
                    test_entity.id AS "id"
                FROM test_entity
                FETCH FIRST 10 ROWS ONLY
                """);
    }

    @Test
    void offset_appends_offset_rows_clause() {
        // Given: a FindQuery with offset(20)
        findQuery.select("id").offset(20);

        // When: building the SQL
        String sql = findQuery.buildString(identifierConverter);

        // Then: SQL contains OFFSET 20 ROWS
        assertThat(sql).isEqualTo("""
                SELECT
                    test_entity.id AS "id"
                FROM test_entity
                OFFSET 20 ROWS
                """);
    }

    @Test
    void limit_and_offset_use_fetch_next_and_correct_order() {
        // Given: limit(10).offset(20)
        findQuery.select("id").limit(10).offset(20);

        // When: building the SQL
        String sql = findQuery.buildString(identifierConverter);

        // Then: OFFSET appears before FETCH NEXT
        assertThat(sql).isEqualTo("""
                SELECT
                    test_entity.id AS "id"
                FROM test_entity
                OFFSET 20 ROWS
                FETCH NEXT 10 ROWS ONLY
                """);
    }

    @Test
    void offset_zero_produces_no_offset_clause() {
        // Given: offset(0)
        findQuery.select("id").limit(5).offset(0);

        // When: building the SQL
        String sql = findQuery.buildString(identifierConverter);

        // Then: SQL does NOT contain OFFSET and uses FETCH FIRST
        assertThat(sql).doesNotContain("OFFSET");
        assertThat(sql).contains("FETCH FIRST 5 ROWS ONLY");
    }

    @Test
    void limit_zero_throws_nativsql_exception() {
        // Given / When: limit(0)
        // Then: NativSQLException with correct message
        assertThatThrownBy(() -> findQuery.limit(0))
                .isInstanceOf(NativSQLException.class)
                .hasMessage("limit must be greater than 0");
    }

    @Test
    void limit_negative_throws_nativsql_exception() {
        // Given / When: limit(-5)
        // Then: NativSQLException with correct message
        assertThatThrownBy(() -> findQuery.limit(-5))
                .isInstanceOf(NativSQLException.class)
                .hasMessage("limit must be greater than 0");
    }

    @Test
    void offset_negative_throws_nativsql_exception() {
        // Given / When: offset(-1)
        // Then: NativSQLException with correct message
        assertThatThrownBy(() -> findQuery.offset(-1))
                .isInstanceOf(NativSQLException.class)
                .hasMessage("offset must be greater than or equal to 0");
    }

    @Test
    void limit_combined_with_where_and_order_by_produces_correct_clause_order() {
        // Given: a FindQuery with WHERE, ORDER BY, limit and offset
        findQuery.select("id", "name")
                .whereAndEquals("name", "Alice")
                .orderByAsc("name")
                .limit(5)
                .offset(10);

        // When: building the SQL
        String sql = findQuery.buildString(identifierConverter);

        // Then: clause order is SELECT … FROM … WHERE … ORDER BY … OFFSET … FETCH NEXT …
        int wherePos = sql.indexOf("WHERE");
        int orderPos = sql.indexOf("ORDER BY");
        int offsetPos = sql.indexOf("OFFSET");
        int fetchPos = sql.indexOf("FETCH");
        assertThat(wherePos).isLessThan(orderPos);
        assertThat(orderPos).isLessThan(offsetPos);
        assertThat(offsetPos).isLessThan(fetchPos);
        assertThat(sql).contains("OFFSET 10 ROWS");
        assertThat(sql).contains("FETCH NEXT 5 ROWS ONLY");
    }

    @Getter
    @Setter
    static class TestEntity implements IEntity<Long> {
        private Long id;
        private String name;
    }
}
