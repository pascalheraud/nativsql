package ovh.heraud.nativsql.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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
 * Tests for the new WHERE operator methods added to AbstractWhereQuery.
 * Uses FindQuery as the concrete subclass.
 */
class AbstractWhereQueryTest {

    @Mock
    private GenericRepository<TestEntity, Long> mockRepository;

    @Mock
    private AnnotationManager mockAnnotationManager;

    private SnakeCaseIdentifierConverter identifierConverter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockRepository.getAnnotationManager()).thenReturn(mockAnnotationManager);
        when(mockRepository.getTableName()).thenReturn("test_entity");
        when(mockRepository.getEntityFields()).thenReturn(null);
        when(mockRepository.getIdentifierConverter()).thenReturn(new SnakeCaseIdentifierConverter());
        identifierConverter = new SnakeCaseIdentifierConverter();
    }

    @Test
    void whereAndOperator_greater_than_produces_correct_sql_and_params() {
        FindQuery<TestEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .whereAndOperator("age", Operator.GREATER_THAN, 18);

        String sql = query.buildString(identifierConverter);
        assertThat(sql).contains("age > :age");

        Map<String, Object> params = query.getParameters();
        assertThat(params).containsEntry("age", 18);
    }

    @Test
    void whereAndColumnOperator_is_null_produces_correct_sql_and_no_param() {
        FindQuery<TestEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .whereAndColumnOperator("deletedAt", ColumnOperator.IS_NULL);

        String sql = query.buildString(identifierConverter);
        assertThat(sql).contains("deleted_at IS NULL");

        Map<String, Object> params = query.getParameters();
        assertThat(params).doesNotContainKey("deletedAt");
    }

    @Test
    void whereAndColumnOperator_is_not_null_produces_correct_sql() {
        FindQuery<TestEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .whereAndColumnOperator("deletedAt", ColumnOperator.IS_NOT_NULL);

        String sql = query.buildString(identifierConverter);
        assertThat(sql).contains("deleted_at IS NOT NULL");
    }

    @Test
    void whereAndRange_between_produces_correct_sql_and_params() {
        LocalDate low = LocalDate.of(1980, 1, 1);
        LocalDate high = LocalDate.of(2000, 12, 31);

        FindQuery<TestEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .whereAndRange("birthDate", RangeOperator.BETWEEN, low, high);

        String sql = query.buildString(identifierConverter);
        assertThat(sql).contains("birth_date BETWEEN :birthDateLow AND :birthDateHigh");

        Map<String, Object> params = query.getParameters();
        assertThat(params).containsEntry("birthDateLow", low);
        assertThat(params).containsEntry("birthDateHigh", high);
    }

    @Test
    void whereAndRange_with_null_low_throws_nativsql_exception() {
        assertThatThrownBy(() ->
                FindQuery.of(mockRepository)
                        .select("id")
                        .whereAndRange("birthDate", RangeOperator.BETWEEN, null, LocalDate.of(2000, 12, 31)))
                .isInstanceOf(NativSQLException.class)
                .hasMessageContaining("birthDate");
    }

    @Test
    void whereAndRange_with_null_high_throws_nativsql_exception() {
        assertThatThrownBy(() ->
                FindQuery.of(mockRepository)
                        .select("id")
                        .whereAndRange("birthDate", RangeOperator.BETWEEN, LocalDate.of(1980, 1, 1), null))
                .isInstanceOf(NativSQLException.class)
                .hasMessageContaining("birthDate");
    }

    @Getter
    @Setter
    static class TestEntity implements IEntity<Long> {
        private Long id;
        private int age;
        private LocalDate birthDate;
        private LocalDate deletedAt;
    }
}
