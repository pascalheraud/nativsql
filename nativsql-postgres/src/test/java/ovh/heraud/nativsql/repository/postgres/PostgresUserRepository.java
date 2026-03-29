package ovh.heraud.nativsql.repository.postgres;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.postgis.Point;
import ovh.heraud.nativsql.domain.data.IData;
import ovh.heraud.nativsql.domain.postgres.User;
import ovh.heraud.nativsql.domain.postgres.UserReport;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Repository;

/**
 * Repository for User entities.
 */
@Repository
public class PostgresUserRepository extends PostgresRepository<User, Long> {

    @Override
    @NonNull
    public String getTableName() {
        return "users";
    }

    @Override
    @NonNull
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
                        .associate("contacts", List.of(contactColumns)));
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
                        .leftJoin("group", List.of(groupColumns)));
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
    public List<User> findByIdWithNullParam(Long userId, Object nullParam) {
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

}