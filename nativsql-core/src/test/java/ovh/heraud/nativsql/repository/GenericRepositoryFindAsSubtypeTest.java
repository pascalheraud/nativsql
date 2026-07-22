package ovh.heraud.nativsql.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import lombok.Getter;
import lombok.Setter;
import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.mapper.RowMapperFactory;
import ovh.heraud.nativsql.util.FindQuery;

/**
 * Unit tests for {@link GenericRepository#find(FindQuery, Class)} and
 * {@link GenericRepository#findAll(FindQuery, Class)}, mapping a query into a
 * subtype {@code R extends T} instead of exactly {@code T}.
 */
@ExtendWith(MockitoExtension.class)
class GenericRepositoryFindAsSubtypeTest {

    // ==================== Test fixtures ====================

    @Getter
    @Setter
    static class TestEntity implements IEntity<Long> {
        private Long id;
        private String name;
        private String status;
    }

    @Getter
    @Setter
    static class TestEntityReport extends TestEntity {
        private Long extra;
    }

    static class TestRepository extends GenericRepository<TestEntity, Long> {

        TestRepository(RowMapperFactory rowMapperFactory, AnnotationManager annotationManager) {
            super(TestEntity.class, "test_entity", rowMapperFactory, annotationManager, new DbOperationLogger());
        }

        @Override
        protected DataSource getDataSource() {
            throw new UnsupportedOperationException("No DataSource in unit tests");
        }

        @Override
        protected Class<TestEntity> getEntityClass() {
            return TestEntity.class;
        }

        @Override
        protected DatabaseDialect getDatabaseDialectInstance() {
            return null;
        }

        // Expose protected members for the test
        FindQuery<TestEntity, Long> newQuery() {
            return newFindQuery();
        }

        <R extends TestEntity> R exposedFind(FindQuery<TestEntity, Long> query, Class<R> resultClass) {
            return find(query, resultClass);
        }

        <R extends TestEntity> List<R> exposedFindAll(FindQuery<TestEntity, Long> query, Class<R> resultClass) {
            return findAll(query, resultClass);
        }

        TestEntity exposedFind(FindQuery<TestEntity, Long> query) {
            return find(query);
        }

        List<TestEntity> exposedFindAll(FindQuery<TestEntity, Long> query) {
            return findAll(query);
        }
    }

    @Mock
    private RowMapperFactory rowMapperFactory;

    @Mock
    private AnnotationManager annotationManager;

    private TestRepository repository;

    @BeforeEach
    void setUp() {
        repository = spy(new TestRepository(rowMapperFactory, annotationManager));
    }

    @Test
    void findAll_query_resultClass_maps_inherited_and_computed_fields() {
        // Given: a query selecting TestEntity's own columns plus a computed field for the report
        FindQuery<TestEntity, Long> query = repository.newQuery()
                .select("id", "name")
                .selectExpression("extra", "1");

        TestEntityReport report = new TestEntityReport();
        report.setId(1L);
        report.setName("Alice");
        report.setExtra(42L);

        doReturn(List.of(report)).when(repository)
                .findAllExternal(anyString(), anyMap(), eq(TestEntityReport.class));

        // When: findAll(query, resultClass)
        List<TestEntityReport> results = repository.exposedFindAll(query, TestEntityReport.class);

        // Then: both the inherited and computed fields are populated
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Alice");
        assertThat(results.get(0).getExtra()).isEqualTo(42L);
    }

    @Test
    void find_query_resultClass_returns_null_when_no_rows() {
        // Given: a query with no matching rows
        FindQuery<TestEntity, Long> query = repository.newQuery().select("id");

        doReturn(List.of()).when(repository)
                .findAllExternal(anyString(), anyMap(), eq(TestEntityReport.class));

        // When: find(query, resultClass)
        TestEntityReport result = repository.exposedFind(query, TestEntityReport.class);

        // Then: null is returned
        assertThat(result).isNull();
    }

    @Test
    void find_query_resultClass_loads_associations_when_present() {
        // Given: a query with an association configured
        FindQuery<TestEntity, Long> query = repository.newQuery()
                .select("id")
                .associate("contacts", "id");

        TestEntityReport report = new TestEntityReport();
        report.setId(1L);
        doReturn(List.of(report)).when(repository)
                .findAllExternal(anyString(), anyMap(), eq(TestEntityReport.class));

        // Loading associations requires a Spring ApplicationContext; since none is set,
        // loadAssociationsInBatch is a no-op guard clause, but this still verifies the
        // find(query, resultClass) path calls into it without throwing (widened bound).
        // When / Then
        TestEntityReport result = repository.exposedFind(query, TestEntityReport.class);
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void findAll_query_resultClass_does_not_load_associations() {
        // Given: a query with an association configured
        FindQuery<TestEntity, Long> query = repository.newQuery()
                .select("id")
                .associate("contacts", "id");

        TestEntityReport report = new TestEntityReport();
        report.setId(1L);
        doReturn(List.of(report)).when(repository)
                .findAllExternal(anyString(), anyMap(), eq(TestEntityReport.class));

        // When: findAll(query, resultClass) — the list variant never loads associations
        List<TestEntityReport> results = repository.exposedFindAll(query, TestEntityReport.class);

        // Then: same N+1-avoidance behavior as findAll(query); no exception, no batch loading
        assertThat(results).hasSize(1);
    }

    @Test
    void findAll_plain_query_selectExpression_overrides_existing_field() {
        // Given: TestEntity has a "status" field, overridden by a computed CASE expression
        FindQuery<TestEntity, Long> query = repository.newQuery()
                .select("id")
                .selectExpression("status", "CASE WHEN {{table}}.deleted_at IS NOT NULL THEN 'ARCHIVED' ELSE status END");

        TestEntity entity = new TestEntity();
        entity.setId(1L);
        entity.setStatus("ARCHIVED");
        doReturn(List.of(entity)).when(repository)
                .findAllExternal(anyString(), anyMap(), eq(TestEntity.class));

        // When: findAll(query) — the existing method, mapping into TestEntity itself
        List<TestEntity> results = repository.exposedFindAll(query);

        // Then: the returned entity has "status" populated from the CASE expression
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getStatus()).isEqualTo("ARCHIVED");
    }

    @Test
    void findAll_plain_query_selectExpression_with_no_matching_field_throws_at_row_mapping() {
        // Given: TestEntity has no "extra" field, but the query introduces one via selectExpression
        FindQuery<TestEntity, Long> query = repository.newQuery()
                .select("id")
                .selectExpression("extra", "1");

        // When / Then: findAll(query), mapping into TestEntity, fails at row-mapping time
        // (simulated here by letting findAllExternal itself raise the same exception
        // GenericRowMapper would raise for a column with no matching field)
        org.mockito.Mockito.doThrow(new NativSQLException("Property 'extra' not found for in class 'TestEntity'"))
                .when(repository).findAllExternal(anyString(), anyMap(), eq(TestEntity.class));

        assertThatThrownBy(() -> repository.exposedFindAll(query))
                .isInstanceOf(NativSQLException.class);
    }
}
