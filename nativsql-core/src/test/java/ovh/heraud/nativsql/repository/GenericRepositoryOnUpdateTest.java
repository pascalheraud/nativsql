package ovh.heraud.nativsql.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.annotation.OnUpdate;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.generic.GenericDialect;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.util.SystemComputedValueProvider;
import ovh.heraud.nativsql.util.ComputedValueProvider;

/**
 * Unit tests for the {@code @OnUpdate} mechanism wired into
 * {@link GenericRepository#update(Object, String...)}.
 *
 * <p>
 * Uses a real {@link AnnotationManager} (annotation scanning matters here) and
 * stubs the low-level {@code executeUpdate} call so no real database is
 * needed — the goal is to verify the SQL-parameter and in-memory field
 * behaviour, not SQL generation in isolation (a repository-level Testcontainers
 * test also covers this feature, see PostgresUserRepositoryTest).
 */
class GenericRepositoryOnUpdateTest {

    // ==================== Test fixtures ====================

    static class TestEntity implements IEntity<Long> {
        private Long id;
        private String name;
        @OnUpdate(SystemComputedValueProvider.class)
        private Instant updateDate;
        @OnUpdate(FixedVersionProvider.class)
        private Integer version;

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Instant getUpdateDate() {
            return updateDate;
        }

        public void setUpdateDate(Instant updateDate) {
            this.updateDate = updateDate;
        }

        public Integer getVersion() {
            return version;
        }

        public void setVersion(Integer version) {
            this.version = version;
        }
    }

    static class PlainEntity implements IEntity<Long> {
        private Long id;
        private String name;

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    static class NullReturningProvider implements ComputedValueProvider<Instant> {
        @Override
        public Instant getValue() {
            return null;
        }
    }

    public static class FixedVersionProvider implements ComputedValueProvider<Integer> {
        static final AtomicInteger CALL_COUNT = new AtomicInteger(0);

        @Override
        public Integer getValue() {
            CALL_COUNT.incrementAndGet();
            return 42;
        }
    }

    static class TestEntityRepository extends GenericRepository<TestEntity, Long> {

        TestEntityRepository(AnnotationManager annotationManager, DatabaseDialect dialect) {
            super(TestEntity.class, "test_entity", null, annotationManager, new DbOperationLogger());
            this.dialect = dialect;
            initJdbcTemplate(); // no DataSource in unit tests, but this also sets databaseDialect
        }

        private final DatabaseDialect dialect;

        @Override
        protected DataSource getDataSource() {
            return null;
        }

        @Override
        protected Class<TestEntity> getEntityClass() {
            return TestEntity.class;
        }

        @Override
        protected DatabaseDialect getDatabaseDialectInstance() {
            return dialect;
        }

        @Override
        public DatabaseDialect getDatabaseDialect() {
            return dialect;
        }

        @Override
        protected int executeUpdate(String sql, Map<String, Object> params) {
            return 1;
        }
    }

    static class PlainEntityRepository extends GenericRepository<PlainEntity, Long> {

        PlainEntityRepository(AnnotationManager annotationManager, DatabaseDialect dialect) {
            super(PlainEntity.class, "plain_entity", null, annotationManager, new DbOperationLogger());
            this.dialect = dialect;
            initJdbcTemplate(); // no DataSource in unit tests, but this also sets databaseDialect
        }

        private final DatabaseDialect dialect;

        @Override
        protected DataSource getDataSource() {
            return null;
        }

        @Override
        protected Class<PlainEntity> getEntityClass() {
            return PlainEntity.class;
        }

        @Override
        protected DatabaseDialect getDatabaseDialectInstance() {
            return dialect;
        }

        @Override
        public DatabaseDialect getDatabaseDialect() {
            return dialect;
        }

        @Override
        protected int executeUpdate(String sql, Map<String, Object> params) {
            return 1;
        }
    }

    private AnnotationManager annotationManager;
    private DatabaseDialect dialect;

    @BeforeEach
    void setUp() {
        annotationManager = new AnnotationManager();
        dialect = new GenericDialect();
    }

    @AfterEach
    void tearDown() {
        annotationManager.clearCache();
        FixedVersionProvider.CALL_COUNT.set(0);
    }

    // ==================== Auto-applied @OnUpdate ====================

    @Test
    void update_autoAppliesOnUpdateColumn_whenNotRequestedByCaller() {
        // Given: an entity with an @OnUpdate updateDate field, caller only updates "name"
        TestEntityRepository repository = spy(new TestEntityRepository(annotationManager, dialect));
        TestEntity entity = new TestEntity();
        entity.setId(1L);
        entity.setName("Alice");

        // When
        repository.update(entity, "name");

        // Then: the updateDate field was populated in-memory by the provider
        assertThat(entity.getUpdateDate()).isNotNull();
        assertThat(entity.getUpdateDate()).isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(5,
                java.time.temporal.ChronoUnit.SECONDS));
    }

    // ==================== Explicit override ====================

    @Test
    void update_keepsCallerValue_whenOnUpdateColumnExplicitlyRequested() {
        // Given: caller explicitly passes "version" (an @OnUpdate column) with its own value
        TestEntityRepository repository = spy(new TestEntityRepository(annotationManager, dialect));
        TestEntity entity = new TestEntity();
        entity.setId(1L);
        entity.setName("Bob");
        entity.setVersion(7);

        // When: version is explicitly requested alongside name
        repository.update(entity, "name", "version");

        // Then: the caller's own value is kept, the provider is never invoked for it
        assertThat(entity.getVersion()).isEqualTo(7);
        assertThat(FixedVersionProvider.CALL_COUNT.get()).isZero();
    }

    // ==================== Two independent @OnUpdate fields ====================

    @Test
    void update_appliesBothOnUpdateFields_whenNeitherRequested() {
        // Given: an entity with two independent @OnUpdate fields (updateDate, version)
        TestEntityRepository repository = spy(new TestEntityRepository(annotationManager, dialect));
        TestEntity entity = new TestEntity();
        entity.setId(1L);
        entity.setName("Carol");

        // When: only "name" is requested
        repository.update(entity, "name");

        // Then: both @OnUpdate fields were auto-applied
        assertThat(entity.getUpdateDate()).isNotNull();
        assertThat(entity.getVersion()).isEqualTo(42);
    }

    // ==================== Provider returning null ====================

    @Test
    void update_throws_whenProviderReturnsNull() {
        // Given: the updateDate field's provider is programmatically overridden to return null
        TestEntityRepository repository = spy(new TestEntityRepository(annotationManager, dialect));
        annotationManager.setComputedFieldInfo(TestEntity.class, "updateDate", new NullReturningProvider());
        TestEntity entity = new TestEntity();
        entity.setId(1L);
        entity.setName("Dave");

        // When / Then
        assertThatThrownBy(() -> repository.update(entity, "name"))
                .isInstanceOf(NativSQLException.class)
                .hasMessageContaining("updateDate");
    }

    // ==================== No @OnUpdate field (regression) ====================

    @Test
    void update_behavesUnchanged_whenEntityHasNoOnUpdateField() {
        // Given: an entity with no @OnUpdate field at all
        PlainEntityRepository repository = spy(new PlainEntityRepository(annotationManager, dialect));
        PlainEntity entity = new PlainEntity();
        entity.setId(1L);
        entity.setName("Eve");

        // When
        repository.update(entity, "name");

        // Then: no extra behaviour, update executed normally
        verify(repository, times(1)).executeUpdate(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap());
        assertThat(entity.getName()).isEqualTo("Eve");
    }

    // ==================== Non-date use case ====================

    @Test
    void update_appliesNonDateOnUpdateField_versionCounter() {
        // Given: the version field uses an int-typed, non-date provider
        TestEntityRepository repository = spy(new TestEntityRepository(annotationManager, dialect));
        TestEntity entity = new TestEntity();
        entity.setId(1L);
        entity.setName("Frank");

        // When
        repository.update(entity, "name");

        // Then: the version counter was set by its own provider, independent of the date field
        assertThat(entity.getVersion()).isEqualTo(42);
        assertThat(FixedVersionProvider.CALL_COUNT.get()).isEqualTo(1);
    }
}
