package ovh.heraud.nativsql.repository.postgres;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.postgis.Point;
import ovh.heraud.nativsql.domain.data.IData;
import ovh.heraud.nativsql.domain.postgres.Group;
import ovh.heraud.nativsql.domain.postgres.User;
import ovh.heraud.nativsql.domain.postgres.UserActivityReport;
import ovh.heraud.nativsql.domain.postgres.UserReport;
import ovh.heraud.nativsql.domain.postgres.UserStatus;
import ovh.heraud.nativsql.util.ColumnOperator;
import ovh.heraud.nativsql.util.FindQuery;
import ovh.heraud.nativsql.util.Operator;
import ovh.heraud.nativsql.util.RangeOperator;
import org.springframework.stereotype.Repository;

/**
 * Repository for User entities.
 */
@Repository
public class PostgresUserRepository extends PostgresRepository<User, Long> {

    @Override
    public String getTableName() {
        return "users";
    }

    @Override
    protected Class<User> getEntityClass() {
        return User.class;
    }

    /**
     * Finds a user by email with specified columns.
     *
     * @param email   the user email
     * @param columns the property names (camelCase) to retrieve
     * @return the user or null if not found
     */
    public User findByEmail(String email, String... columns) {
        return findByProperty("email", email, columns);
    }

    /**
     * Finds a user by email with specified columns.
     *
     * @param email   the user email
     * @param columns the property names (camelCase) to retrieve
     * @return an {@link Optional} containing the user, or empty if not found
     */
    public Optional<User> findOptionalByEmail(String email, String... columns) {
        return findOptionalByProperty("email", email, columns);
    }

    /**
     * Finds a user by external ID (UUID) with specified columns.
     *
     * @param externalId the user external ID
     * @param columns    the property names (camelCase) to retrieve
     * @return the user or null if not found
     */
    public User findByExternalId(UUID externalId, String... columns) {
        return findByProperty("externalId", externalId, columns);
    }

    /**
     * Finds users by city in their address with specified columns.
     *
     * @param city    the city to search for
     * @param columns the property names (camelCase) to retrieve
     * @return list of users in that city
     */
    public List<User> findByCity(String city, String... columns) {
        // Using (address).city to access composite type field
        return findAllByPropertyExpression("(address).city", "city", city, columns);
    }

    /**
     * Attempts to find users by an equality condition on the JSON-mapped
     * {@code tagIds} column — used to verify that standard WHERE conditions on a
     * {@code @Json} column are rejected.
     *
     * @param value   the value to compare {@code tagIds} against
     * @param columns the property names (camelCase) to retrieve
     * @return list of matching users (never actually reached; the query builder
     *         throws before execution)
     */
    public List<User> findByTagIdsEquals(Object value, String... columns) {
        return findAll(
                newFindQuery()
                        .select(columns)
                        .whereAndEquals("tagIds", value));
    }

    /**
     * Attempts to find users by an IN condition on the JSON-mapped
     * {@code tagIds} column — used to verify that standard WHERE conditions on a
     * {@code @Json} column are rejected.
     *
     * @param values  the values to compare {@code tagIds} against
     * @param columns the property names (camelCase) to retrieve
     * @return list of matching users (never actually reached; the query builder
     *         throws before execution)
     */
    public List<User> findByTagIdsIn(List<?> values, String... columns) {
        return findAll(
                newFindQuery()
                        .select(columns)
                        .whereAndIn("tagIds", values));
    }

    /**
     * Finds a user by ID and loads their contact information.
     *
     * @param userId         the user ID
     * @param contactColumns the columns to load for contact information
     * @param userColumns    the columns to load for the user
     * @return the user with contact information, or null if not found
     */
    public User findByIdWithContactInfos(Long userId, String[] contactColumns, String... userColumns) {
        return find(
                newFindQuery()
                        .select(userColumns)
                        .whereAndEquals("id", userId)
                        .associate("contacts", contactColumns));
    }

    /**
     * Finds a user by ID and loads their group information via JOIN.
     *
     * @param userId       the user ID
     * @param groupColumns the columns to load for the group
     * @param userColumns  the columns to load for the user
     * @return the user with group information, or null if not found
     */
    public User getUserWithGroup(Long userId, String[] groupColumns, String... userColumns) {
        return find(
                newFindQuery()
                        .select(userColumns)
                        .whereAndEquals("id", userId)
                        .leftJoin("group", groupColumns));
    }

    /**
     * Generates a user statistics report.
     *
     * @return the user report with stats on total users, users with email contacts,
     *         and users with French preferences
     */
    public UserReport getUsersReport() {
        String sql = """
                SELECT
                    (
                        SELECT COUNT(*)
                        FROM users
                    )
                            AS "totalUsers",
                    (
                        SELECT COUNT(DISTINCT u.id)
                        FROM users u
                        INNER JOIN contact_info ci ON u.id = ci.user_id
                        WHERE ci.contact_type = 'EMAIL'::contact_type
                    )
                            AS "usersWithEmailContact",
                    (
                        SELECT COUNT(*)
                        FROM users u
                        WHERE u.preferences->>'language' = 'fr'
                    )
                            AS "usersWithFrenchPreference"
                """;
        return findExternal(sql, UserReport.class);
    }

    /**
     * Generates a user statistics report for users within 10km of a given point.
     *
     * @param point the geographic point to search around
     * @return the user report with stats on total users within 10km, users with
     *         email contacts,
     *         and users with French preferences
     */
    public UserReport getUsersReportAroundPoint(Point point) {
        String sql = """
                SELECT
                    (
                        SELECT COUNT(*)
                        FROM users
                        WHERE ST_DWithin(position, :point::geography, 10000)
                    )
                            AS "totalUsers",
                    (
                        SELECT COUNT(DISTINCT u.id)
                        FROM users u
                        INNER JOIN contact_info ci ON u.id = ci.user_id
                        WHERE ci.contact_type = 'EMAIL'::contact_type
                        AND ST_DWithin(u.position, :point::geography, 10000)
                    )
                            AS "usersWithEmailContact",
                    (
                        SELECT COUNT(*)
                        FROM users u
                        WHERE u.preferences->>'language' = 'fr'
                        AND ST_DWithin(u.position, :point::geography, 10000)
                    )
                            AS "usersWithFrenchPreference"
                """;
        Map<String, Object> params = new HashMap<>();
        params.put("point", point);
        return findExternal(sql, params, UserReport.class);
    }

    /**
     * Generates a hierarchical user statistics report with group details.
     * The report includes nested group statistics for the group with the most
     * users.
     *
     * @return the user report with group statistics
     */
    public UserReport getUsersReportWithGroupStats() {
        String sql = """
                SELECT
                    (
                        SELECT COUNT(*)
                        FROM users
                    )
                            AS "totalUsers",
                    (
                        SELECT COUNT(DISTINCT u.id)
                        FROM users u
                        INNER JOIN contact_info ci ON u.id = ci.user_id
                        WHERE ci.contact_type = 'EMAIL'::contact_type
                    )
                            AS "usersWithEmailContact",
                    (
                        SELECT COUNT(*)
                        FROM users u
                        WHERE u.preferences->>'language' = 'fr'
                    )
                            AS "usersWithFrenchPreference",
                    g.id
                            AS "groupStats.groupId",
                    g.name
                            AS "groupStats.groupName",
                    COUNT(DISTINCT u.id)
                            AS "groupStats.userCount",
                    CAST(SUM(CASE WHEN u.status = 'ACTIVE'::user_status THEN 1 ELSE 0 END) AS BIGINT)
                            AS "groupStats.activeUserCount",
                    CAST(COUNT(DISTINCT ci.id) AS BIGINT)
                            AS "groupStats.emailContactCount"
                FROM users u
                    LEFT JOIN user_group g ON u.group_id = g.id
                    LEFT JOIN contact_info ci ON u.id = ci.user_id AND ci.contact_type = 'EMAIL'::contact_type
                WHERE g.id IS NOT NULL
                GROUP BY g.id, g.name
                ORDER BY "groupStats.userCount" DESC
                LIMIT 1
                """;
        return findExternal(sql, UserReport.class);
    }

    public <T> IData<T> getValue(Class<? extends IData<T>> clazz) {
        String tableName = getTableNameForDataType(clazz);
        return findExternal("select data from " + tableName + " limit 1", clazz);
    }

    public void insertValue(Class<?> dataTypeClass, Object value) {
        String tableName = getTableNameForDataType(dataTypeClass);
        executeUpdate("insert into " + tableName + "(data) values (:v)", Map.of("v", value));
    }

    public void deleteValue(Class<?> dataTypeClass) {
        String tableName = getTableNameForDataType(dataTypeClass);
        executeUpdate("delete from " + tableName, Map.of());
    }

    private String getTableNameForDataType(Class<?> clazz) {
        String simpleName = clazz.getSimpleName();
        // Convert DataTypeLong -> data_type_long
        return "data_type_" + simpleName
                .substring("DataType".length())
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toLowerCase();
    }

    /**
     * Finds users by ID with a custom query that includes a null parameter.
     * This tests how the repository handles null values in custom findExternal
     * queries. PostgreSQL requires a cast for NULL type inference.
     *
     * @param userId    the user ID
     * @param nullParam a null parameter to test null handling
     * @return list of users found
     */
    public List<User> findAllByIdWithNullParam(Long userId, Object nullParam) {
        String sql = """
                SELECT id, first_name as "firstName", email
                FROM users
                WHERE id = :userId OR :nullParam::varchar IS NULL
                    """;
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("nullParam", nullParam); // This is null - tests the bug
        return findAllExternal(sql, params, User.class);
    }

    public void deleteByEmailAndStatus(String email, UserStatus status) {
        delete(newDeleteQuery()
                .whereAndEquals(User::getEmail, email)
                .whereAndEquals(User::getStatus, status));
    }

    public void deleteAllByStatuses(List<UserStatus> statuses) {
        deleteAll(newDeleteQuery()
                .whereAndIn(User::getStatus, statuses));
    }

    public List<User> findAllByAgeLessThan(int threshold, String... columns) {
        return findAll(newFindQuery()
                .select(columns)
                .whereAndOperator(User::getAge, Operator.LESS_THAN, threshold));
    }

    public List<User> findAllByStatusNotEquals(UserStatus status, String... columns) {
        return findAll(newFindQuery()
                .select(columns)
                .whereAndOperator(User::getStatus, Operator.NOT_EQUALS, status));
    }

    public List<User> findAllByFirstNameLike(String pattern, String... columns) {
        return findAll(newFindQuery()
                .select(columns)
                .whereAndOperator(User::getFirstName, Operator.LIKE, pattern));
    }

    public List<User> findAllWithNullGroupId(String... columns) {
        return findAll(newFindQuery()
                .select(columns)
                .whereAndColumnOperator(User::getGroupId, ColumnOperator.IS_NULL));
    }

    public List<User> findAllByAgeBetween(int low, int high, String... columns) {
        return findAll(newFindQuery()
                .select(columns)
                .whereAndRange(User::getAge, RangeOperator.BETWEEN, low, high));
    }

    public List<User> findAllByGroupName(String groupName, String... columns) {
        return findAll(newFindQuery()
                .select(columns)
                .leftJoin("group", "name")
                .whereAndEquals("group.name", groupName));
    }

    public List<User> findAllByStatusAndGroupName(UserStatus status, String groupName, String... columns) {
        return findAll(newFindQuery()
                .select(columns)
                .leftJoin("group", "name")
                .whereAndEquals("status", status)
                .whereAndEquals("group.name", groupName));
    }

    public List<User> findAllWithNullGroupName(String... columns) {
        return findAll(newFindQuery()
                .select(columns)
                .leftJoin("group", "name")
                .whereAndColumnOperator("group.name", ColumnOperator.IS_NULL));
    }

    public List<User> findAllByGroupNameLike(String pattern, String... columns) {
        return findAll(newFindQuery()
                .select(columns)
                .leftJoin("group", "name")
                .whereAndOperator("group.name", Operator.LIKE, pattern));
    }

    public List<User> findAllByGroupCreatedAtIsNotNull(String... columns) {
        return findAll(newFindQuery()
                .select(columns)
                .leftJoin("group", "createdAt")
                .whereAndColumnOperator("group.createdAt", ColumnOperator.IS_NOT_NULL));
    }

    /**
     * Finds all users ordered ascending/descending by their joined group's name,
     * using the typed AssociationGetter overloads (issue #104).
     *
     * @param ascending true for ascending order, false for descending
     * @param columns   the user columns to retrieve
     */
    public List<User> findAllOrderByGroupName(boolean ascending, String... columns) {
        FindQuery<User, Long> query = newFindQuery()
                .select(columns)
                .leftJoin(User::getGroup, Group::getName);
        query = ascending
                ? query.orderByAsc(User::getGroup, Group::getName)
                : query.orderByDesc(User::getGroup, Group::getName);
        return findAll(query);
    }

    /**
     * Finds users whose joined group name equals the given value, using the
     * typed AssociationGetter overload of whereAndEquals (issue #104).
     *
     * @param groupName the group name to filter on
     * @param columns   the user columns to retrieve
     */
    public List<User> findAllByGroupNameTyped(String groupName, String... columns) {
        return findAll(newFindQuery()
                .select(columns)
                .leftJoin(User::getGroup, Group::getName)
                .whereAndEquals(User::getGroup, Group::getName, groupName));
    }

    public List<User> findPageByOrderById(int limit, int offset, String... columns) {
        return findAll(newFindQuery()
                .select(columns)
                .orderByAsc("id")
                .limit(limit)
                .offset(offset));
    }

    public long countByStatuses(List<UserStatus> statuses) {
        return count(newCountQuery()
                .whereAndIn(User::getStatus, statuses));
    }

    public long countByEmailAndStatus(String email, UserStatus status) {
        return count(newCountQuery()
                .whereAndEquals(User::getEmail, email)
                .whereAndEquals(User::getStatus, status));
    }

    public boolean existsByStatuses(List<UserStatus> statuses) {
        return exists(newExistsQuery()
                .whereAndIn(User::getStatus, statuses));
    }

    public boolean existsByEmailAndStatus(String email, UserStatus status) {
        return exists(newExistsQuery()
                .whereAndEquals(User::getEmail, email)
                .whereAndEquals(User::getStatus, status));
    }

    /**
     * Finds user activity reports: each user's own fields plus a computed
     * contact count, mapped into {@link UserActivityReport} (issue #98).
     *
     * @param columns the user columns to retrieve
     */
    public List<UserActivityReport> findUserActivityReports(String... columns) {
        FindQuery<User, Long> query = newFindQuery()
                .select(columns)
                .selectExpression("contactCount",
                        "(SELECT COUNT(*) FROM contact_info c WHERE c.user_id = {{table}}.id)");
        return findAll(query, UserActivityReport.class);
    }

    /**
     * Same as {@link #findUserActivityReports(String...)}, combined with a JOIN on the
     * user's group and batch-loaded contacts (issue #98).
     *
     * @param groupColumns   the columns to load for the group
     * @param contactColumns the columns to load for the contacts association
     * @param columns        the user columns to retrieve
     */
    public List<UserActivityReport> findUserActivityReportsWithGroupAndContacts(
            String[] groupColumns, String[] contactColumns, String... columns) {
        FindQuery<User, Long> query = newFindQuery()
                .select(columns)
                .leftJoin("group", groupColumns)
                .selectExpression("contactCount",
                        "(SELECT COUNT(*) FROM contact_info c WHERE c.user_id = {{table}}.id)")
                .associate("contacts", contactColumns);
        return findAll(query, UserActivityReport.class);
    }

    /**
     * Singular variant of {@link #findUserActivityReportsWithGroupAndContacts(String[], String[], String...)},
     * which loads associations (issue #98).
     *
     * @param userId         the user ID
     * @param groupColumns   the columns to load for the group
     * @param contactColumns the columns to load for the contacts association
     * @param columns        the user columns to retrieve
     */
    public UserActivityReport findUserActivityReportWithGroupAndContacts(
            Long userId, String[] groupColumns, String[] contactColumns, String... columns) {
        FindQuery<User, Long> query = newFindQuery()
                .select(columns)
                .whereAndEquals("id", userId)
                .leftJoin("group", groupColumns)
                .selectExpression("contactCount",
                        "(SELECT COUNT(*) FROM contact_info c WHERE c.user_id = {{table}}.id)")
                .associate("contacts", contactColumns);
        return find(query, UserActivityReport.class);
    }

}