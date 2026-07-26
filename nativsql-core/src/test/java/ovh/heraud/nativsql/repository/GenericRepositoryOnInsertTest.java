package ovh.heraud.nativsql.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.annotation.OnInsert;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.generic.GenericDialect;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.util.ComputedValueProvider;
import ovh.heraud.nativsql.util.SystemComputedValueProvider;

/**
 * Unit tests for the {@code @OnInsert} mechanism wired into
 * {@link GenericRepository#insert(Object, String...)}.
 *
 * <p>
 * Uses a real {@link AnnotationManager} (annotation scanning matters here) and
 * stubs the low-level {@code insertWithGeneratedKey} call so no real database
 * is needed — the goal is to verify the SQL-parameter and in-memory field
 * behaviour, not SQL generation in isolation.
 */
class GenericRepositoryOnInsertTest {

    // ==================== Test fixtures ====================

    static class TestEntity implements IEntity<Long> {
        private Long id;
        private String name;
        @OnInsert(SystemComputedValueProvider.class)
        private Instant creationDate;
        @OnInsert(FixedAuthorProvider.class)
        private String createdBy;

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

        public Instant getCreationDate() {
            return creationDate;
        }

        public void setCreationDate(Instant creationDate) {
            this.creationDate = creationDate;
        }

        public String getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(String createdBy) {
            this.createdBy = createdBy;
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

    public static class FixedAuthorProvider implements ComputedValueProvider<String> {
        static final AtomicInteger CALL_COUNT = new AtomicInteger(0);

        @Override
        public String getValue() {
            CALL_COUNT.incrementAndGet();
            return "system";
        }
    }

    static class TestEntityRepository extends GenericRepository<TestEntity, Long> {

        private final AtomicLong nextId;

        TestEntityRepository(AnnotationManager annotationManager, DatabaseDialect dialect) {
            super(TestEntity.class, "test_entity", null, annotationManager, new DbOperationLogger());
            this.dialect = dialect;
            this.nextId = new AtomicLong(1L);
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
        protected Long insertWithGeneratedKey(String sql, Map<String, Object> params) {
            return nextId.getAndIncrement();
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
        protected Long insertWithGeneratedKey(String sql, Map<String, Object> params) {
            return 1L;
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
        FixedAuthorProvider.CALL_COUNT.set(0);
    }

    // ==================== Auto-applied @OnInsert ====================

    @Test
    void insert_autoAppliesOnInsertColumn_whenNotRequestedByCaller() {
        // Given: an entity with an @OnInsert creationDate field, caller only inserts "name"
        TestEntityRepository repository = spy(new TestEntityRepository(annotationManager, dialect));
        TestEntity entity = new TestEntity();
        entity.setName("Alice");

        // When
        repository.insert(entity, "name");

        // Then: the creationDate field was populated in-memory by the provider
        assertThat(entity.getCreationDate()).isNotNull();
        assertThat(entity.getCreationDate()).isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(5,
                java.time.temporal.ChronoUnit.SECONDS));
    }

    // ==================== Explicit override ====================

    @Test
    void insert_keepsCallerValue_whenOnInsertColumnExplicitlyRequested() {
        // Given: caller explicitly passes "createdBy" (an @OnInsert column) with its own value
        TestEntityRepository repository = spy(new TestEntityRepository(annotationManager, dialect));
        TestEntity entity = new TestEntity();
        entity.setName("Bob");
        entity.setCreatedBy("manual-import");

        // When: createdBy is explicitly requested alongside name
        repository.insert(entity, "name", "createdBy");

        // Then: the caller's own value is kept, the provider is never invoked for it
        assertThat(entity.getCreatedBy()).isEqualTo("manual-import");
        assertThat(FixedAuthorProvider.CALL_COUNT.get()).isZero();
    }

    // ==================== Two independent @OnInsert fields ====================

    @Test
    void insert_appliesBothOnInsertFields_whenNeitherRequested() {
        // Given: an entity with two independent @OnInsert fields (creationDate, createdBy)
        TestEntityRepository repository = spy(new TestEntityRepository(annotationManager, dialect));
        TestEntity entity = new TestEntity();
        entity.setName("Carol");

        // When: only "name" is requested
        repository.insert(entity, "name");

        // Then: both @OnInsert fields were auto-applied
        assertThat(entity.getCreationDate()).isNotNull();
        assertThat(entity.getCreatedBy()).isEqualTo("system");
    }

    // ==================== Provider returning null ====================

    @Test
    void insert_throws_whenProviderReturnsNull() {
        // Given: the creationDate field's provider is programmatically overridden to return null
        TestEntityRepository repository = spy(new TestEntityRepository(annotationManager, dialect));
        annotationManager.setOnInsertFieldInfo(TestEntity.class, "creationDate", new NullReturningProvider());
        TestEntity entity = new TestEntity();
        entity.setName("Dave");

        // When / Then
        assertThatThrownBy(() -> repository.insert(entity, "name"))
                .isInstanceOf(NativSQLException.class)
                .hasMessageContaining("creationDate");
    }

    // ==================== No @OnInsert field (regression) ====================

    @Test
    void insert_behavesUnchanged_whenEntityHasNoOnInsertField() {
        // Given: an entity with no @OnInsert field at all
        PlainEntityRepository repository = spy(new PlainEntityRepository(annotationManager, dialect));
        PlainEntity entity = new PlainEntity();
        entity.setName("Eve");

        // When
        repository.insert(entity, "name");

        // Then: no extra behaviour, insert executed normally
        verify(repository, times(1)).insertWithGeneratedKey(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyMap());
        assertThat(entity.getName()).isEqualTo("Eve");
        assertThat(entity.getId()).isEqualTo(1L);
    }

    // ==================== Non-date use case ====================

    @Test
    void insert_appliesNonDateOnInsertField_createdByAuthor() {
        // Given: the createdBy field uses a String-typed, non-date provider
        TestEntityRepository repository = spy(new TestEntityRepository(annotationManager, dialect));
        TestEntity entity = new TestEntity();
        entity.setName("Frank");

        // When
        repository.insert(entity, "name");

        // Then: createdBy was set by its own provider, independent of the date field
        assertThat(entity.getCreatedBy()).isEqualTo("system");
        assertThat(FixedAuthorProvider.CALL_COUNT.get()).isEqualTo(1);
    }
}
