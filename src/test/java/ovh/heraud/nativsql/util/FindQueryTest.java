package ovh.heraud.nativsql.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import ovh.heraud.nativsql.annotation.AnnotationManager;
import ovh.heraud.nativsql.db.SnakeCaseIdentifierConverter;
import ovh.heraud.nativsql.domain.IEntity;
import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.repository.GenericRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for FindQuery builder class.
 * Tests the fluent builder API for SELECT query construction.
 */
class FindQueryTest {

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

    // ==================== Constructor and Factory ====================

    @Test
    void testOfCreatesNewInstance() {
        FindQuery<TestEntity, Long> query = FindQuery.of(mockRepository);
        assertThat(query).isNotNull();
    }

    @Test
    void testOfThrowsWhenRepositoryIsNull() {
        assertThatThrownBy(() -> FindQuery.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Repository cannot be null");
    }

    // ==================== Select Columns ====================

    @Nested
    class SelectTests {

        @Test
        void testSelectWithVarargs() {
            findQuery.select("id", "name", "email");

            String sql = findQuery.buildString(identifierConverter);
            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id",
                        test_entity.name AS "name",
                        test_entity.email AS "email"
                    FROM test_entity
                    """);
        }

        @Test
        void testSelectWithList() {
            List<String> columns = Arrays.asList("id", "firstName", "lastName");
            findQuery.select(columns);

            String sql = findQuery.buildString(identifierConverter);
            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id",
                        test_entity.first_name AS "firstName",
                        test_entity.last_name AS "lastName"
                    FROM test_entity
                    """);
        }

        @Test
        void testSelectAccumulatesColumns() {
            findQuery.select("id", "name")
                    .select("email", "status");

            String sql = findQuery.buildString(identifierConverter);
            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id",
                        test_entity.name AS "name",
                        test_entity.email AS "email",
                        test_entity.status AS "status"
                    FROM test_entity
                    """);
        }

        @Test
        void testSelectThrowsWhenColumnsEmptyVarargs() {
            assertThatThrownBy(() -> findQuery.select())
                    .isInstanceOf(NativSQLException.class)
                    .hasMessage("Column list cannot be empty");
        }

        @Test
        void testSelectThrowsWhenColumnsNullVarargs() {
            assertThatThrownBy(() -> findQuery.select((String[]) null))
                    .isInstanceOf(NativSQLException.class)
                    .hasMessage("Column list cannot be empty");
        }

        @Test
        void testSelectThrowsWhenListEmpty() {
            assertThatThrownBy(() -> findQuery.select(List.of()))
                    .isInstanceOf(NativSQLException.class)
                    .hasMessage("Column list cannot be empty");
        }

        @Test
        void testSelectThrowsWhenListNull() {
            assertThatThrownBy(() -> findQuery.select((List<String>) null))
                    .isInstanceOf(NativSQLException.class)
                    .hasMessage("Column list cannot be empty");
        }

        @Test
        void testGetColumnsArray() {
            findQuery.select("id", "name", "email");

            String sql = findQuery.buildString(identifierConverter);
            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id",
                        test_entity.name AS "name",
                        test_entity.email AS "email"
                    FROM test_entity
                    """);
        }
    }

    // ==================== WHERE Conditions ====================

    @Nested
    class WhereTests {

        @Test
        void testWhereAndEquals() {
            findQuery.select("id", "name")
                    .whereAndEquals("status", "ACTIVE");

            String sql = findQuery.buildString(identifierConverter);
            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id",
                        test_entity.name AS "name"
                    FROM test_entity
                    WHERE
                            status = :status
                    """);
        }

        @Test
        void testWhereAndEqualsAccumulates() {
            findQuery.select("id", "name")
                    .whereAndEquals("status", "ACTIVE")
                    .whereAndEquals("role", "ADMIN");

            String sql = findQuery.buildString(identifierConverter);
            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id",
                        test_entity.name AS "name"
                    FROM test_entity
                    WHERE
                            status = :status
                        AND
                            role = :role
                    """);
        }

        @Test
        void testWhereAndIn() {
            List<String> statuses = Arrays.asList("ACTIVE", "INACTIVE");
            findQuery.select("id")
                    .whereAndIn("status", statuses);

            String sql = findQuery.buildString(identifierConverter);
            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    WHERE
                            status IN (:status)
                    """);
        }

        @Test
        void testWhereExpression() {
            findQuery.select("id")
                    .whereExpression("(address).city", "city", "Paris");

            String sql = findQuery.buildString(identifierConverter);
            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    WHERE
                            (address).city = :city
                    """);
        }

        @Test
        void testHasWhereConditionsInitiallyFalse() {
            findQuery.select("id");

            String sql = findQuery.buildString(identifierConverter);
            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    """);

            assertThat(findQuery.hasWhereConditions()).isFalse();
        }
    }

    // ==================== ORDER BY ====================

    @Nested
    class OrderByTests {

        @Test
        void testOrderByAsc() {
            findQuery.select("id").orderByAsc("name");

            String sql = findQuery.buildString(identifierConverter);
            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    ORDER BY
                        name ASC
                    """);
        }

        @Test
        void testOrderByDesc() {
            findQuery.select("id").orderByDesc("createdAt");

            String sql = findQuery.buildString(identifierConverter);
            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    ORDER BY
                        created_at DESC
                    """);
        }

        @Test
        void testMultipleOrderByConditions() {
            findQuery.select("id")
                    .orderByAsc("name")
                    .orderByDesc("createdAt");

            String sql = findQuery.buildString(identifierConverter);
            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    ORDER BY
                        name ASC,
                        created_at DESC
                    """);
        }

        @Test
        void testOrderByMerge() {
            OrderBy orderBy = new OrderBy();
            orderBy.asc("firstName");
            orderBy.desc("lastName");

            findQuery.select("id").orderBy(orderBy);

            String sql = findQuery.buildString(identifierConverter);
            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    ORDER BY
                        first_name ASC,
                        last_name DESC
                    """);
        }
    }

    // ==================== Associations ====================

    @Nested
    class AssociationTests {

        @Test
        void testAssociateWithVarargs() {
            findQuery.select("id")
                    .associate("contacts", "id", "email");

            assertThat(findQuery.getAssociations())
                    .hasSize(1);
        }

        @Test
        void testAssociateWithList() {
            List<String> columns = Arrays.asList("id", "email", "phone");
            findQuery.select("id")
                    .associate("contacts", columns);

            assertThat(findQuery.getAssociations()).hasSize(1);
        }

        @Test
        void testMultipleAssociations() {
            findQuery.select("id")
                    .associate("contacts", "id", "email")
                    .associate("orders", "id", "amount");

            assertThat(findQuery.getAssociations()).hasSize(2);
        }

        @Test
        void testGetAssociationNames() {
            findQuery.select("id")
                    .associate("contacts", "id", "email")
                    .associate("orders", "id", "amount");

            String[] names = findQuery.getAssociationNames();
            assertThat(names)
                    .hasSize(2)
                    .containsExactly("contacts", "orders");
        }

        @Test
        void testHasAssociationsInitiallyFalse() {
            findQuery.select("id");
            assertThat(findQuery.hasAssociations()).isFalse();
        }
    }

    // ==================== Joins ====================

    @Nested
    class JoinTests {

        @Test
        void testHasJoinsInitiallyFalse() {
            findQuery.select("id");

            String sql = findQuery.buildString(identifierConverter);
            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    """);

            assertThat(findQuery.hasJoins()).isFalse();
        }

        @Test
        void testGetJoinsReturnsCopy() {
            findQuery.select("id");

            String sql = findQuery.buildString(identifierConverter);
            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    """);

            List<Join> joins1 = findQuery.getJoins();
            List<Join> joins2 = findQuery.getJoins();

            // Verify we get copies, not the same reference
            assertThat(joins1)
                    .isNotSameAs(joins2)
                    .isEmpty();
        }
    }

    // ==================== Table and Repository ====================

    @Nested
    class TableTests {

        @Test
        void testGetTableName() {
            assertThat(findQuery.getTableName()).isEqualTo("test_entity");
        }

        @Test
        void testRepositoryCannotBeNull() {
            assertThatThrownBy(() -> FindQuery.of(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Repository cannot be null");
        }
    }

    // ==================== Parameters ====================

    @Nested
    class ParametersTests {

        @Test
        void testGetParametersFromWhereConditions() {
            findQuery.select("id")
                    .whereAndEquals("status", "ACTIVE")
                    .whereAndEquals("role", "ADMIN");

            String sql = findQuery.buildString(identifierConverter);
            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    WHERE
                            status = :status
                        AND
                            role = :role
                    """);

            Map<String, Object> params = findQuery.getParameters();
            assertThat(params)
                    .hasSize(2)
                    .containsEntry("status", "ACTIVE")
                    .containsEntry("role", "ADMIN");
        }

        @Test
        void testGetParametersEmptyWhenNoWhere() {
            findQuery.select("id");

            String sql = findQuery.buildString(identifierConverter);
            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    """);

            Map<String, Object> params = findQuery.getParameters();
            assertThat(params).isEmpty();
        }

        @Test
        void testGetParametersWithNullValue() {
            findQuery.select("id")
                    .whereAndEquals("status", "INACTIVE");

            String sql = findQuery.buildString(identifierConverter);
            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    WHERE
                            status = :status
                    """);

            Map<String, Object> params = findQuery.getParameters();
            assertThat(params)
                    .hasSize(1)
                    .containsEntry("status", "INACTIVE");
        }
    }

    // ==================== Method Chaining ====================

    @Nested
    class ChainingTests {

        @Test
        void testFluentApiChaining() {
            FindQuery<TestEntity, Long> query = findQuery
                    .select("id", "name", "email")
                    .whereAndEquals("status", "ACTIVE")
                    .whereAndEquals("role", "ADMIN")
                    .orderByAsc("name")
                    .associate("contacts", "id", "email");

            String sql = query.buildString(identifierConverter);
            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id",
                        test_entity.name AS "name",
                        test_entity.email AS "email"
                    FROM test_entity
                    WHERE
                            status = :status
                        AND
                            role = :role
                    ORDER BY
                        name ASC
                    """);

            assertThat(query).isNotNull();
            assertThat(query.getColumns()).hasSize(3);
            assertThat(query.getWhereConditions()).hasSize(2);
            assertThat(query.getAssociations()).hasSize(1);
        }

        @Test
        void testReturnsSelfForChaining() {
            FindQuery<TestEntity, Long> result1 = findQuery.select("id");
            FindQuery<TestEntity, Long> result2 = result1.whereAndEquals("status", "ACTIVE");
            FindQuery<TestEntity, Long> result3 = result2.orderByAsc("name");

            String sql = result3.buildString(identifierConverter);
            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    WHERE
                            status = :status
                    ORDER BY
                        name ASC
                    """);

            assertThat(result1).isSameAs(findQuery);
            assertThat(result2).isSameAs(findQuery);
            assertThat(result3).isSameAs(findQuery);
        }
    }

    // ==================== SQL Building ====================

    @Nested
    class SqlBuildingTests {

        // ==================== Simple SELECT ====================

        @Test
        void testBuildStringSimpleSelectSingleColumn() {
            findQuery.select("id");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    """);
        }

        @Test
        void testBuildStringSimpleSelectTwoColumns() {
            findQuery.select("id", "name");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id",
                        test_entity.name AS "name"
                    FROM test_entity
                    """);
        }

        @Test
        void testBuildStringSimpleSelectThreeColumns() {
            findQuery.select("id", "name", "email");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id",
                        test_entity.name AS "name",
                        test_entity.email AS "email"
                    FROM test_entity
                    """);
        }

        // ==================== WHERE - Single Condition ====================

        @Test
        void testBuildStringWithSingleWhereEquals() {
            findQuery.select("id", "name")
                    .whereAndEquals("status", "ACTIVE");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id",
                        test_entity.name AS "name"
                    FROM test_entity
                    WHERE
                            status = :status
                    """);
        }

        // ==================== WHERE - Multiple Conditions ====================

        @Test
        void testBuildStringWithTwoWhereConditions() {
            findQuery.select("id")
                    .whereAndEquals("status", "ACTIVE")
                    .whereAndEquals("role", "ADMIN");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    WHERE
                            status = :status
                        AND
                            role = :role
                    """);
        }

        @Test
        void testBuildStringWithThreeWhereConditions() {
            findQuery.select("id")
                    .whereAndEquals("status", "ACTIVE")
                    .whereAndEquals("role", "ADMIN")
                    .whereAndEquals("department", "SALES");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    WHERE
                            status = :status
                        AND
                            role = :role
                        AND
                            department = :department
                    """);
        }

        // ==================== WHERE - IN Condition ====================

        @Test
        void testBuildStringWithWhereInCondition() {
            List<String> statuses = Arrays.asList("ACTIVE", "PENDING", "ARCHIVED");
            findQuery.select("id", "name")
                    .whereAndIn("status", statuses);

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id",
                        test_entity.name AS "name"
                    FROM test_entity
                    WHERE
                            status IN (:status)
                    """);
        }

        @Test
        void testBuildStringWithWhereInAndEqualsConditions() {
            List<String> statuses = Arrays.asList("ACTIVE", "PENDING");
            findQuery.select("id")
                    .whereAndEquals("role", "ADMIN")
                    .whereAndIn("status", statuses);

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    WHERE
                            role = :role
                        AND
                            status IN (:status)
                    """);
        }

        // ==================== ORDER BY - Ascending ====================

        @Test
        void testBuildStringWithOrderByAscendingSingleColumn() {
            findQuery.select("id", "name")
                    .orderByAsc("name");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id",
                        test_entity.name AS "name"
                    FROM test_entity
                    ORDER BY
                        name ASC
                    """);
        }

        @Test
        void testBuildStringWithOrderByAscendingMultipleColumns() {
            findQuery.select("id")
                    .orderByAsc("name")
                    .orderByAsc("email");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    ORDER BY
                        name ASC,
                        email ASC
                    """);
        }

        // ==================== ORDER BY - Descending ====================

        @Test
        void testBuildStringWithOrderByDescendingSingleColumn() {
            findQuery.select("id", "name")
                    .orderByDesc("createdAt");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id",
                        test_entity.name AS "name"
                    FROM test_entity
                    ORDER BY
                        created_at DESC
                    """);
        }

        @Test
        void testBuildStringWithOrderByDescendingMultipleColumns() {
            findQuery.select("id")
                    .orderByDesc("name")
                    .orderByDesc("email");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    ORDER BY
                        name DESC,
                        email DESC
                    """);
        }

        // ==================== ORDER BY - Mixed ASC and DESC ====================

        @Test
        void testBuildStringWithMixedAscDescOrder() {
            findQuery.select("id")
                    .orderByAsc("name")
                    .orderByDesc("createdAt");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    ORDER BY
                        name ASC,
                        created_at DESC
                    """);
        }

        @Test
        void testBuildStringWithMixedAscDescOrderThreeColumns() {
            findQuery.select("id")
                    .orderByAsc("firstName")
                    .orderByDesc("lastName")
                    .orderByAsc("email");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    ORDER BY
                        first_name ASC,
                        last_name DESC,
                        email ASC
                    """);
        }

        // ==================== Combined: WHERE + ORDER BY (ASC) ====================

        @Test
        void testBuildStringWithWhereAndOrderByAsc() {
            findQuery.select("id", "name", "email")
                    .whereAndEquals("status", "ACTIVE")
                    .orderByAsc("name");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id",
                        test_entity.name AS "name",
                        test_entity.email AS "email"
                    FROM test_entity
                    WHERE
                            status = :status
                    ORDER BY
                        name ASC
                    """);
        }

        // ==================== Combined: WHERE + ORDER BY (DESC) ====================

        @Test
        void testBuildStringWithWhereAndOrderByDesc() {
            findQuery.select("id", "name")
                    .whereAndEquals("status", "ACTIVE")
                    .orderByDesc("createdAt");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id",
                        test_entity.name AS "name"
                    FROM test_entity
                    WHERE
                            status = :status
                    ORDER BY
                        created_at DESC
                    """);
        }

        // ==================== Combined: Multiple WHERE + Multiple ORDER BY ====================

        @Test
        void testBuildStringWithMultipleWhereAndOrderByMixed() {
            findQuery.select("id")
                    .whereAndEquals("status", "ACTIVE")
                    .whereAndEquals("role", "ADMIN")
                    .orderByAsc("name")
                    .orderByDesc("createdAt");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    WHERE
                            status = :status
                        AND
                            role = :role
                    ORDER BY
                        name ASC,
                        created_at DESC
                    """);
        }

        // ==================== StringBuilder Build ====================

        @Test
        void testBuildAppendToStringBuilder() {
            findQuery.select("id", "name");
            StringBuilder sb = new StringBuilder();

            findQuery.build(sb, identifierConverter);

            assertThat(sb.toString()).isEqualTo("""
                    SELECT
                        test_entity.id AS "id",
                        test_entity.name AS "name"
                    FROM test_entity
                    """);
        }

        @Test
        void testBuildStringBuilderWithWhereAndOrderBy() {
            findQuery.select("id")
                    .whereAndEquals("status", "ACTIVE")
                    .orderByAsc("name");
            StringBuilder sb = new StringBuilder();

            findQuery.build(sb, identifierConverter);

            assertThat(sb.toString()).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    WHERE
                            status = :status
                    ORDER BY
                        name ASC
                    """);
        }

        // ==================== CamelCase to snake_case conversion ====================

        @Test
        void testBuildStringWithCamelCaseColumns() {
            findQuery.select("firstName", "lastName", "emailAddress");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.first_name AS "firstName",
                        test_entity.last_name AS "lastName",
                        test_entity.email_address AS "emailAddress"
                    FROM test_entity
                    """);
        }

        @Test
        void testBuildStringWithCamelCaseWhereAndOrderBy() {
            findQuery.select("firstName")
                    .whereAndEquals("createdAt", "2024-01-01")
                    .orderByDesc("lastModified");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.first_name AS "firstName"
                    FROM test_entity
                    WHERE
                            created_at = :createdAt
                    ORDER BY
                        last_modified DESC
                    """);
        }

        @Test
        void testBuildStringWithComplexCamelCase() {
            findQuery.select("id", "firstName", "lastName")
                    .whereAndEquals("userStatus", "ACTIVE")
                    .whereAndEquals("isDeleted", false)
                    .orderByAsc("firstName")
                    .orderByDesc("createdAt");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id",
                        test_entity.first_name AS "firstName",
                        test_entity.last_name AS "lastName"
                    FROM test_entity
                    WHERE
                            user_status = :userStatus
                        AND
                            is_deleted = :isDeleted
                    ORDER BY
                        first_name ASC,
                        created_at DESC
                    """);
        }

        // ==================== Custom WHERE Expression ====================

        @Test
        void testBuildStringWithCustomWhereExpression() {
            findQuery.select("id", "name")
                    .whereExpression("(address).city", "city", "Paris");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id",
                        test_entity.name AS "name"
                    FROM test_entity
                    WHERE
                            (address).city = :city
                    """);
        }

        @Test
        void testBuildStringWithCustomWhereExpressionAndOrderBy() {
            findQuery.select("id")
                    .whereExpression("(address).city", "city", "London")
                    .orderByAsc("name");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    WHERE
                            (address).city = :city
                    ORDER BY
                        name ASC
                    """);
        }

        // ==================== Edge Cases ====================

        @Test
        void testBuildStringWithManyColumns() {
            findQuery.select("id", "firstName", "lastName", "email", "phone", "status", "createdAt");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id",
                        test_entity.first_name AS "firstName",
                        test_entity.last_name AS "lastName",
                        test_entity.email AS "email",
                        test_entity.phone AS "phone",
                        test_entity.status AS "status",
                        test_entity.created_at AS "createdAt"
                    FROM test_entity
                    """);
        }

        @Test
        void testBuildStringWithManyWhereConditions() {
            findQuery.select("id")
                    .whereAndEquals("status", "ACTIVE")
                    .whereAndEquals("role", "ADMIN")
                    .whereAndEquals("department", "IT")
                    .whereAndEquals("location", "Paris");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    WHERE
                            status = :status
                        AND
                            role = :role
                        AND
                            department = :department
                        AND
                            location = :location
                    """);
        }

        @Test
        void testBuildStringWithManyOrderByConditions() {
            findQuery.select("id")
                    .orderByAsc("firstName")
                    .orderByDesc("lastName")
                    .orderByAsc("email")
                    .orderByDesc("createdAt");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id"
                    FROM test_entity
                    ORDER BY
                        first_name ASC,
                        last_name DESC,
                        email ASC,
                        created_at DESC
                    """);
        }

        @Test
        void testBuildStringComplexQueryAllTogether() {
            findQuery.select("id", "firstName", "lastName", "email")
                    .whereAndEquals("status", "ACTIVE")
                    .whereAndEquals("department", "SALES")
                    .orderByAsc("firstName")
                    .orderByDesc("createdAt");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id",
                        test_entity.first_name AS "firstName",
                        test_entity.last_name AS "lastName",
                        test_entity.email AS "email"
                    FROM test_entity
                    WHERE
                            status = :status
                        AND
                            department = :department
                    ORDER BY
                        first_name ASC,
                        created_at DESC
                    """);
        }

        @Test
        void testBuildStringWithWhereInMultipleConditions() {
            List<String> statuses = Arrays.asList("ACTIVE", "PENDING");
            List<String> departments = Arrays.asList("IT", "HR", "SALES");

            findQuery.select("id", "name")
                    .whereAndIn("status", statuses)
                    .whereAndIn("department", departments)
                    .orderByAsc("name");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id",
                        test_entity.name AS "name"
                    FROM test_entity
                    WHERE
                            status IN (:status)
                        AND
                            department IN (:department)
                    ORDER BY
                        name ASC
                    """);
        }

        @Test
        void testBuildStringWithMixedInAndEqualsConditions() {
            List<String> statuses = Arrays.asList("ACTIVE", "INACTIVE");

            findQuery.select("id", "name", "email")
                    .whereAndEquals("department", "IT")
                    .whereAndIn("status", statuses)
                    .whereAndEquals("verified", true)
                    .orderByAsc("name");

            String sql = findQuery.buildString(identifierConverter);

            assertThat(sql).isEqualTo("""
                    SELECT
                        test_entity.id AS "id",
                        test_entity.name AS "name",
                        test_entity.email AS "email"
                    FROM test_entity
                    WHERE
                            department = :department
                        AND
                            status IN (:status)
                        AND
                            verified = :verified
                    ORDER BY
                        name ASC
                    """);
        }
    }

    // ==================== Test Entities ====================

    /**
     * Test entity implementing IEntity
     */
    static class TestEntity implements IEntity<Long> {
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
}
