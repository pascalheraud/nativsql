package ovh.heraud.nativsql.util;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.DatabaseDialect;
import ovh.heraud.nativsql.db.SnakeCaseIdentifierConverter;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.repository.GenericRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for dot-notation WHERE filtering on joined columns in FindQuery.
 * Verifies SQL generation and parameter extraction without a real database.
 */
class FindQueryDotNotationTest {

    /**
     * Minimal concrete repository stub that can be instantiated via no-arg
     * constructor.
     * Used as the join repository with a known table name.
     */
    public static class GroupRepositoryStub extends GenericRepository<GroupEntity, Long> {
        @Override
        public String getTableName() {
            return "user_group";
        }

        @Override
        protected Class<GroupEntity> getEntityClass() {
            return GroupEntity.class;
        }

        @Override
        protected DataSource getDataSource() {
            return null;
        }

        @Override
        protected DatabaseDialect getDatabaseDialectInstance() {
            return null;
        }
    }

    /**
     * Minimal concrete repository stub for a second join association.
     */
    public static class ProfileRepositoryStub extends GenericRepository<ProfileEntity, Long> {
        @Override
        public String getTableName() {
            return "user_profile";
        }

        @Override
        protected Class<ProfileEntity> getEntityClass() {
            return ProfileEntity.class;
        }

        @Override
        protected DataSource getDataSource() {
            return null;
        }

        @Override
        protected DatabaseDialect getDatabaseDialectInstance() {
            return null;
        }
    }

    static class GroupEntity implements IEntity<Long> {
        private Long id;

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }
    }

    static class ProfileEntity implements IEntity<Long> {
        private Long id;

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }
    }

    static class MainEntity implements IEntity<Long> {
        private Long id;

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public void setId(Long id) {
            this.id = id;
        }

        public String getStatus() {
            return null;
        }
    }

    @Mock
    private GenericRepository<MainEntity, Long> mockRepository;

    @Mock
    private AnnotationManager mockAnnotationManager;

    @Mock
    private Fields mockFields;

    private SnakeCaseIdentifierConverter identifierConverter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockRepository.getAnnotationManager()).thenReturn(mockAnnotationManager);
        when(mockRepository.getTableName()).thenReturn("test_entity");
        when(mockRepository.getEntityFields()).thenReturn(mockFields);
        when(mockAnnotationManager.getTypeInfo(any())).thenReturn(new TypeInfo());
        identifierConverter = new SnakeCaseIdentifierConverter();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void stubGroupJoin() {
        FieldAccessor groupField = mock(FieldAccessor.class);
        when(mockFields.get("group")).thenReturn(groupField);
        when(mockAnnotationManager.getMappedByInfo(groupField))
                .thenReturn(new MappedByInfo("groupId", GroupRepositoryStub.class));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void stubProfileJoin() {
        FieldAccessor profileField = mock(FieldAccessor.class);
        when(mockFields.get("profile")).thenReturn(profileField);
        when(mockAnnotationManager.getMappedByInfo(profileField))
                .thenReturn(new MappedByInfo("profileId", ProfileRepositoryStub.class));
    }

    @Test
    void whereAndEquals_with_dot_path_uses_joined_table_prefix() {
        // Given: a query with a leftJoin on "group" (table name "user_group")
        stubGroupJoin();
        FindQuery<MainEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .leftJoin("group", "name")
                .whereAndEquals("group.name", "Admins");

        // When: building the SQL
        String sql = query.buildString(identifierConverter);

        // Then: WHERE clause uses the joined table prefix and camelCase param name
        assertThat(sql).contains("user_group.name = :groupName");

        Map<String, Object> params = query.getParameters();
        assertThat(params).containsEntry("groupName", "Admins");
    }

    @Test
    void whereAndEquals_with_dot_path_throws_when_association_not_joined() {
        // Given: a query with no joins
        FindQuery<MainEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .whereAndEquals("group.name", "Admins");

        // When: building the SQL
        // Then: NativSQLException is thrown mentioning the missing association
        assertThatThrownBy(() -> query.buildString(identifierConverter))
                .isInstanceOf(NativSQLException.class)
                .hasMessageContaining("group");
    }

    @Test
    void plain_column_still_uses_main_table_prefix_when_joins_present() {
        // Given: a query with a leftJoin on "group"
        stubGroupJoin();
        FindQuery<MainEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .leftJoin("group", "name")
                .whereAndEquals("status", "ACTIVE");

        // When: building the SQL
        String sql = query.buildString(identifierConverter);

        // Then: plain column is prefixed with the main table name
        assertThat(sql).contains("test_entity.status = :status");
    }

    @Test
    void whereAndEquals_with_dot_path_and_plain_column_produce_correct_prefixes() {
        // Given: a query with a leftJoin and two WHERE conditions
        stubGroupJoin();
        FindQuery<MainEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .leftJoin("group", "name")
                .whereAndEquals("status", "ACTIVE")
                .whereAndEquals("group.name", "Admins");

        // When: building the SQL
        String sql = query.buildString(identifierConverter);

        // Then: both conditions use the correct table prefix
        assertThat(sql).contains("test_entity.status = :status");
        assertThat(sql).contains("user_group.name = :groupName");

        Map<String, Object> params = query.getParameters();
        assertThat(params).containsEntry("status", "ACTIVE");
        assertThat(params).containsEntry("groupName", "Admins");
    }

    @Test
    void whereAndColumnOperator_is_null_with_dot_path_adds_no_parameter() {
        // Given: a query with a leftJoin and an IS_NULL condition on the joined column
        stubGroupJoin();
        FindQuery<MainEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .leftJoin("group", "name")
                .whereAndColumnOperator("group.deletedAt", ColumnOperator.IS_NULL);

        // When: building the SQL
        String sql = query.buildString(identifierConverter);

        // Then: SQL contains the IS NULL condition with joined table prefix and no
        // param
        assertThat(sql).contains("user_group.deleted_at IS NULL");

        Map<String, Object> params = query.getParameters();
        assertThat(params).doesNotContainKey("groupDeletedAt");
    }

    @Test
    void whereAndRange_with_dot_path_derives_param_names_correctly() {
        // Given: a query with an innerJoin on "profile" and a BETWEEN range condition
        stubProfileJoin();
        FindQuery<MainEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .innerJoin("profile", "age")
                .whereAndRange("profile.age", RangeOperator.BETWEEN, 18, 65);

        // When: building the SQL
        String sql = query.buildString(identifierConverter);

        // Then: SQL contains the BETWEEN clause with joined table prefix and correct
        // param names
        assertThat(sql).contains("user_profile.age BETWEEN :profileAgeLow AND :profileAgeHigh");

        Map<String, Object> params = query.getParameters();
        assertThat(params).containsEntry("profileAgeLow", 18);
        assertThat(params).containsEntry("profileAgeHigh", 65);
    }

    @Test
    void whereAndIn_with_dot_path_uses_joined_table_prefix() {
        // Given: a query with a leftJoin and an IN condition on the joined column
        stubGroupJoin();
        FindQuery<MainEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .leftJoin("group", "status")
                .whereAndIn("group.status", List.of("ACTIVE", "PENDING"));

        // When: building the SQL
        String sql = query.buildString(identifierConverter);

        // Then: SQL uses the joined table prefix
        assertThat(sql).contains("user_group.status IN (:groupStatus)");

        Map<String, Object> params = query.getParameters();
        assertThat(params).containsKey("groupStatus");
    }

    @Test
    void whereAndOperator_like_with_dot_path_uses_joined_table_prefix() {
        // Given: a query with a leftJoin and a LIKE condition on the joined column
        stubGroupJoin();
        FindQuery<MainEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .leftJoin("group", "name")
                .whereAndOperator("group.name", Operator.LIKE, "Admin%");

        // When: building the SQL
        String sql = query.buildString(identifierConverter);

        // Then: SQL uses the joined table prefix with LIKE
        assertThat(sql).contains("user_group.name LIKE :groupName");

        Map<String, Object> params = query.getParameters();
        assertThat(params).containsEntry("groupName", "Admin%");
    }

    @Test
    void camelCase_column_in_dot_path_is_converted_to_snake_case_in_sql() {
        // Given: a dot-path with a camelCase column ("deletedAt")
        stubGroupJoin();
        FindQuery<MainEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .leftJoin("group", "deletedAt")
                .whereAndColumnOperator("group.deletedAt", ColumnOperator.IS_NULL);

        // When: building the SQL
        String sql = query.buildString(identifierConverter);

        // Then: identifierConverter.toDB is applied → "deletedAt" becomes "deleted_at"
        assertThat(sql).contains("user_group.deleted_at IS NULL");
    }
}
