package ovh.heraud.nativsql.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.Getter;
import lombok.Setter;
import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.mapper.RowMapperFactory;
import ovh.heraud.nativsql.util.FindQuery;
import ovh.heraud.nativsql.util.TypeInfo;

/**
 * Unit tests for the {@code findOptional*} methods added to
 * {@link GenericRepository}: {@link GenericRepository#findOptionalById},
 * {@link GenericRepository#findOptionalByProperty}, and
 * {@link GenericRepository#findOptional(FindQuery)}.
 */
@ExtendWith(MockitoExtension.class)
class GenericRepositoryFindOptionalTest {

    // ==================== Test fixtures ====================

    @Getter
    @Setter
    static class TestEntity implements IEntity<Long> {
        private Long id;
        private String name;
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

        Optional<TestEntity> exposedFindOptionalByProperty(String property, Object value, String... columns) {
            return findOptionalByProperty(property, value, columns);
        }

        Optional<TestEntity> exposedFindOptional(FindQuery<TestEntity, Long> query) {
            return findOptional(query);
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
        lenient().doReturn(new TypeInfo()).when(annotationManager).getTypeInfo(any());
    }

    @Test
    void findOptionalById_returns_present_optional_when_entity_found() {
        // Given: a row matching the requested id
        TestEntity entity = new TestEntity();
        entity.setId(1L);
        entity.setName("Alice");
        doReturn(List.of(entity)).when(repository)
                .findAllExternal(anyString(), anyMap(), eq(TestEntity.class));

        // When: findOptionalById is called
        Optional<TestEntity> result = repository.findOptionalById(1L, "name");

        // Then: the optional contains the entity
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Alice");
    }

    @Test
    void findOptionalById_returns_empty_optional_when_no_rows() {
        // Given: no matching row
        doReturn(List.of()).when(repository)
                .findAllExternal(anyString(), anyMap(), eq(TestEntity.class));

        // When: findOptionalById is called
        Optional<TestEntity> result = repository.findOptionalById(1L, "name");

        // Then: the optional is empty, not null
        assertThat(result).isEmpty();
    }

    @Test
    void findOptionalByProperty_returns_present_optional_when_entity_found() {
        // Given: a row matching the requested property value
        TestEntity entity = new TestEntity();
        entity.setId(1L);
        entity.setName("Alice");
        doReturn(List.of(entity)).when(repository)
                .findAllExternal(anyString(), anyMap(), eq(TestEntity.class));

        // When: findOptionalByProperty is called
        Optional<TestEntity> result = repository.exposedFindOptionalByProperty("name", "Alice", "id", "name");

        // Then: the optional contains the entity
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Alice");
    }

    @Test
    void findOptionalByProperty_returns_empty_optional_when_no_rows() {
        // Given: no matching row
        doReturn(List.of()).when(repository)
                .findAllExternal(anyString(), anyMap(), eq(TestEntity.class));

        // When: findOptionalByProperty is called
        Optional<TestEntity> result = repository.exposedFindOptionalByProperty("name", "Bob", "id", "name");

        // Then: the optional is empty, not null
        assertThat(result).isEmpty();
    }

    @Test
    void findOptional_query_returns_present_optional_when_entity_found() {
        // Given: a query with one matching row
        FindQuery<TestEntity, Long> query = repository.newQuery().select("id", "name");

        TestEntity entity = new TestEntity();
        entity.setId(1L);
        entity.setName("Alice");
        doReturn(List.of(entity)).when(repository)
                .findAllExternal(anyString(), anyMap(), eq(TestEntity.class));

        // When: findOptional(query) is called
        Optional<TestEntity> result = repository.exposedFindOptional(query);

        // Then: the optional contains the entity
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Alice");
    }

    @Test
    void findOptional_query_returns_empty_optional_when_no_rows() {
        // Given: a query with no matching rows
        FindQuery<TestEntity, Long> query = repository.newQuery().select("id");

        doReturn(List.of()).when(repository)
                .findAllExternal(anyString(), anyMap(), eq(TestEntity.class));

        // When: findOptional(query) is called
        Optional<TestEntity> result = repository.exposedFindOptional(query);

        // Then: the optional is empty, not null
        assertThat(result).isEmpty();
    }
}
