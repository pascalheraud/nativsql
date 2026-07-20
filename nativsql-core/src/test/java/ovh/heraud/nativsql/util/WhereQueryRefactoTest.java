package ovh.heraud.nativsql.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import lombok.Getter;
import lombok.Setter;
import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.SnakeCaseIdentifierConverter;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.repository.GenericRepository;

/**
 * Verifies that whereAndEquals and whereAndIn still work correctly on both
 * FindQuery and DeleteQuery after the WhereQuery refacto.
 */
class WhereQueryRefactoTest {

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
        identifierConverter = new SnakeCaseIdentifierConverter();
    }

    @Test
    void findQuery_whereAndEquals_still_works_after_refacto() {
        FindQuery<TestEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .whereAndEquals("status", "ACTIVE");

        String sql = query.buildString(identifierConverter);
        assertThat(sql).contains("status = :status");
        assertThat(query.getParameters()).containsEntry("status", "ACTIVE");
    }

    @Test
    void findQuery_whereAndIn_still_works_after_refacto() {
        FindQuery<TestEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .whereAndIn("status", List.of("ACTIVE", "PENDING"));

        String sql = query.buildString(identifierConverter);
        assertThat(sql).contains("status IN (:status)");
        assertThat(query.getParameters()).containsKey("status");
    }

    @Test
    void deleteQuery_whereAndEquals_still_works_after_refacto() {
        DeleteQuery<TestEntity, Long> query = DeleteQuery.of(mockRepository)
                .whereAndEquals("status", "INACTIVE");

        String sql = query.buildString(identifierConverter);
        assertThat(sql).contains("status = :status");
        assertThat(query.getParameters()).containsEntry("status", "INACTIVE");
    }

    @Test
    void deleteQuery_whereAndIn_still_works_after_refacto() {
        DeleteQuery<TestEntity, Long> query = DeleteQuery.of(mockRepository)
                .whereAndIn("status", List.of("INACTIVE", "ARCHIVED"));

        String sql = query.buildString(identifierConverter);
        assertThat(sql).contains("status IN (:status)");
        assertThat(query.getParameters()).containsKey("status");
    }

    @Getter
    @Setter
    static class TestEntity implements IEntity<Long> {
        private Long id;
        private String status;
    }
}
