package ovh.heraud.nativsql.util;

import java.time.LocalDateTime;
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
 * Unit tests for ORDER BY on joined-table columns in FindQuery (issue #104).
 * Verifies SQL generation for the typed (AssociationGetter + Getter) and
 * string (joinName + column) forms of orderByAsc/orderByDesc, the typed
 * leftJoin/innerJoin overloads, and the typed whereAnd* overloads, without a
 * real database.
 */
class FindQueryJoinedOrderByTest {

    /**
     * Minimal concrete repository stub for the "group" join association.
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
     * Minimal concrete repository stub for the "secondaryGroup" join association
     * (same entity type as GroupRepositoryStub, different table).
     */
    public static class SecondaryGroupRepositoryStub extends GenericRepository<GroupEntity, Long> {
        @Override
        public String getTableName() {
            return "secondary_groups";
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

        public String getName() {
            return null;
        }

        public LocalDateTime getCreationDate() {
            return null;
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

        public GroupEntity getGroup() {
            return null;
        }

        public GroupEntity getSecondaryGroup() {
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
    private void stubSecondaryGroupJoin() {
        FieldAccessor secondaryGroupField = mock(FieldAccessor.class);
        when(mockFields.get("secondaryGroup")).thenReturn(secondaryGroupField);
        when(mockAnnotationManager.getMappedByInfo(secondaryGroupField))
                .thenReturn(new MappedByInfo("secondaryGroupId", SecondaryGroupRepositoryStub.class));
    }

    @Test
    void orderByAsc_with_association_getter_and_target_getter_orders_by_joined_table_column() {
        // Given: a query with a leftJoin on "group" ordered ascending by a typed
        // association getter + target-entity getter
        stubGroupJoin();
        FindQuery<MainEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .leftJoin("group", "id", "name", "creationDate")
                .orderByAsc(MainEntity::getGroup, GroupEntity::getCreationDate);

        // When: building the SQL
        String sql = query.buildString(identifierConverter);

        // Then: ORDER BY uses the joined table prefix, ascending
        assertThat(sql).contains("ORDER BY").contains("user_group.creation_date ASC");
    }

    @Test
    void orderByDesc_with_association_getter_and_target_getter_orders_by_joined_table_column() {
        // Given: a query with a leftJoin on "group" ordered descending by a typed
        // association getter + target-entity getter
        stubGroupJoin();
        FindQuery<MainEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .leftJoin("group", "id", "name", "creationDate")
                .orderByDesc(MainEntity::getGroup, GroupEntity::getCreationDate);

        // When: building the SQL
        String sql = query.buildString(identifierConverter);

        // Then: ORDER BY uses the joined table prefix, descending
        assertThat(sql).contains("ORDER BY").contains("user_group.creation_date DESC");
    }

    @Test
    void orderByAsc_with_string_join_name_and_column_produces_same_sql_as_typed_form() {
        // Given: the same ordering expressed with the string join name + column form
        stubGroupJoin();
        FindQuery<MainEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .leftJoin("group", "id", "creationDate")
                .orderByAsc("group", "creationDate");

        // When: building the SQL
        String sql = query.buildString(identifierConverter);

        // Then: same table-qualified column as the typed form
        assertThat(sql).contains("user_group.creation_date ASC");
    }

    @Test
    void orderByAsc_with_two_joins_of_same_entity_type_orders_by_the_explicit_join_name() {
        // Given: two joins of the same entity type (GroupEntity), ordered by the
        // second join's explicit name — string form
        stubGroupJoin();
        stubSecondaryGroupJoin();
        FindQuery<MainEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .leftJoin("group", "id", "creationDate")
                .leftJoin("secondaryGroup", "id", "creationDate")
                .orderByAsc("secondaryGroup", "creationDate");

        // When: building the SQL
        String sql = query.buildString(identifierConverter);

        // Then: the correct join's table qualifies the ORDER BY column
        assertThat(sql).contains("secondary_groups.creation_date ASC");
        assertThat(sql).doesNotContain("user_group.creation_date ASC");
    }

    @Test
    void orderByAsc_with_two_joins_of_same_entity_type_orders_by_the_explicit_typed_association() {
        // Given: two joins of the same entity type (GroupEntity), ordered by the
        // second join's explicit association getter — typed form
        stubGroupJoin();
        stubSecondaryGroupJoin();
        FindQuery<MainEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .leftJoin(MainEntity::getGroup, GroupEntity::getId, GroupEntity::getCreationDate)
                .leftJoin(MainEntity::getSecondaryGroup, GroupEntity::getId, GroupEntity::getCreationDate)
                .orderByAsc(MainEntity::getSecondaryGroup, GroupEntity::getCreationDate);

        // When: building the SQL
        String sql = query.buildString(identifierConverter);

        // Then: the correct join's table qualifies the ORDER BY column
        assertThat(sql).contains("secondary_groups.creation_date ASC");
        assertThat(sql).doesNotContain("user_group.creation_date ASC");
    }

    @Test
    void orderByAsc_with_unregistered_join_name_throws_native_sql_exception() {
        // Given: a query ordering by an association name that was never joined
        FindQuery<MainEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .orderByAsc("group", "creationDate");

        // When: building the SQL
        // Then: NativSQLException is thrown mentioning the missing association
        assertThatThrownBy(() -> query.buildString(identifierConverter))
                .isInstanceOf(NativSQLException.class)
                .hasMessageContaining("group");
    }

    @Test
    void orderByAsc_on_root_entity_still_works_when_query_also_has_joins() {
        // Given: a root-entity ordering (getter and string forms), on a query that
        // also has joins registered — regression check for unchanged behavior
        stubGroupJoin();
        FindQuery<MainEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .leftJoin("group", "id", "name")
                .orderByAsc(MainEntity::getStatus)
                .orderByAsc("status");

        // When: building the SQL
        String sql = query.buildString(identifierConverter);

        // Then: the root column is still qualified with the main table prefix
        assertThat(sql).contains("test_entity.status ASC");
    }

    @Test
    void orderBy_merge_applies_the_resolver_from_the_target_find_query() {
        // Given: an OrderBy built standalone with a dot-notation path, merged into a
        // FindQuery that has the matching join registered
        stubGroupJoin();
        OrderBy standalone = new OrderBy().asc("group.creationDate");
        FindQuery<MainEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .leftJoin("group", "id", "creationDate")
                .orderBy(standalone);

        // When: building the SQL
        String sql = query.buildString(identifierConverter);

        // Then: the merged order resolves against the target query's registered join
        assertThat(sql).contains("user_group.creation_date ASC");
    }

    @Test
    void leftJoin_with_association_getter_and_column_getters_matches_string_form_sql() {
        // Given: two equivalent queries, one using the typed leftJoin overload, one
        // using the existing string overload
        stubGroupJoin();
        FindQuery<MainEntity, Long> typedQuery = FindQuery.of(mockRepository)
                .select("id")
                .leftJoin(MainEntity::getGroup, GroupEntity::getId, GroupEntity::getName);

        FindQuery<MainEntity, Long> stringQuery = FindQuery.of(mockRepository)
                .select("id")
                .leftJoin("group", "id", "name");

        // When: building the SQL for both
        String typedSql = typedQuery.buildString(identifierConverter);
        String stringSql = stringQuery.buildString(identifierConverter);

        // Then: both produce the same SQL
        assertThat(typedSql).isEqualTo(stringSql);
        assertThat(typedSql).contains("LEFT JOIN user_group");
    }

    @Test
    void innerJoin_with_association_getter_and_column_getters_matches_string_form_sql() {
        // Given: two equivalent queries, one using the typed innerJoin overload, one
        // using the existing string overload
        stubGroupJoin();
        FindQuery<MainEntity, Long> typedQuery = FindQuery.of(mockRepository)
                .select("id")
                .innerJoin(MainEntity::getGroup, GroupEntity::getId, GroupEntity::getName);

        FindQuery<MainEntity, Long> stringQuery = FindQuery.of(mockRepository)
                .select("id")
                .innerJoin("group", "id", "name");

        // When: building the SQL for both
        String typedSql = typedQuery.buildString(identifierConverter);
        String stringSql = stringQuery.buildString(identifierConverter);

        // Then: both produce the same SQL
        assertThat(typedSql).isEqualTo(stringSql);
        assertThat(typedSql).contains("INNER JOIN user_group");
    }

    @Test
    void typed_left_join_combined_with_typed_order_by_produces_full_expected_sql() {
        // Given: a query combining the typed leftJoin overload with the typed
        // orderByAsc overload
        stubGroupJoin();
        FindQuery<MainEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .leftJoin(MainEntity::getGroup, GroupEntity::getId, GroupEntity::getName, GroupEntity::getCreationDate)
                .orderByAsc(MainEntity::getGroup, GroupEntity::getCreationDate);

        // When: building the SQL
        String sql = query.buildString(identifierConverter);

        // Then: SELECT, JOIN and ORDER BY are all present and correctly qualified
        assertThat(sql).contains("SELECT");
        assertThat(sql).contains("LEFT JOIN user_group");
        assertThat(sql).contains("ORDER BY").contains("user_group.creation_date ASC");
    }

    @Test
    void typed_where_and_equals_matches_dot_path_string_form_sql() {
        // Given: two equivalent queries, one using the typed whereAndEquals overload,
        // one using the existing dot-path string overload
        stubGroupJoin();
        FindQuery<MainEntity, Long> typedQuery = FindQuery.of(mockRepository)
                .select("id")
                .leftJoin(MainEntity::getGroup, GroupEntity::getId, GroupEntity::getName)
                .whereAndEquals(MainEntity::getGroup, GroupEntity::getName, "Admins");

        FindQuery<MainEntity, Long> stringQuery = FindQuery.of(mockRepository)
                .select("id")
                .leftJoin("group", "id", "name")
                .whereAndEquals("group.name", "Admins");

        // When: building the SQL for both
        String typedSql = typedQuery.buildString(identifierConverter);
        String stringSql = stringQuery.buildString(identifierConverter);

        // Then: both produce the same SQL and parameters
        assertThat(typedSql).isEqualTo(stringSql);
        assertThat(typedSql).contains("user_group.name = :groupName");

        Map<String, Object> params = typedQuery.getParameters();
        assertThat(params).containsEntry("groupName", "Admins");
    }

    @Test
    void typed_where_and_equals_combined_with_typed_join_and_typed_order_by_produces_full_expected_sql() {
        // Given: a query combining the typed whereAndEquals, typed leftJoin and typed
        // orderByAsc overloads
        stubGroupJoin();
        FindQuery<MainEntity, Long> query = FindQuery.of(mockRepository)
                .select("id")
                .leftJoin(MainEntity::getGroup, GroupEntity::getId, GroupEntity::getName, GroupEntity::getCreationDate)
                .whereAndEquals(MainEntity::getGroup, GroupEntity::getName, "Admins")
                .orderByAsc(MainEntity::getGroup, GroupEntity::getCreationDate);

        // When: building the SQL
        String sql = query.buildString(identifierConverter);

        // Then: SELECT, JOIN, WHERE and ORDER BY are all present and correctly
        // qualified
        assertThat(sql).contains("LEFT JOIN user_group");
        assertThat(sql).contains("user_group.name = :groupName");
        assertThat(sql).contains("ORDER BY").contains("user_group.creation_date ASC");

        Map<String, Object> params = query.getParameters();
        assertThat(params).containsEntry("groupName", "Admins");
    }
}
