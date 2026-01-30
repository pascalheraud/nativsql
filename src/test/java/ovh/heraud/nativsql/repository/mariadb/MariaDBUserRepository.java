package ovh.heraud.nativsql.repository.mariadb;

import java.util.List;
import java.util.UUID;

import ovh.heraud.nativsql.domain.mariadb.User;
import ovh.heraud.nativsql.domain.mariadb.UserReport;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Repository;

/**
 * Repository for User entities using MariaDB.
 */
@Repository
public class MariaDBUserRepository extends MariaDBRepository<User, Long> {

    @Override
    @NonNull
    protected String getTableName() {
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
                        WHERE ci.contact_type = 'EMAIL'
                    )
                            AS "usersWithEmailContact",
                    (
                        SELECT COUNT(*)
                        FROM users u
                        WHERE JSON_EXTRACT(u.preferences, '$.language') = 'fr'
                    )
                            AS "usersWithFrenchPreference"
                FROM DUAL
                """;
        return findExternal(sql, UserReport.class);
    }

    /**
     * Generates a hierarchical user statistics report with group details.
     * The report includes nested group statistics for the group with the most users.
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
                        WHERE ci.contact_type = 'EMAIL'
                    )
                            AS "usersWithEmailContact",
                    (
                        SELECT COUNT(*)
                        FROM users u
                        WHERE JSON_EXTRACT(u.preferences, '$.language') = 'fr'
                    )
                            AS "usersWithFrenchPreference",
                    g.id
                            AS "groupStats.groupId",
                    g.name
                            AS "groupStats.groupName",
                    COUNT(DISTINCT u.id)
                            AS "groupStats.userCount",
                    SUM(CASE WHEN u.status = 'ACTIVE' THEN 1 ELSE 0 END)
                            AS "groupStats.activeUserCount",
                    SUM(CASE WHEN ci.id IS NOT NULL THEN 1 ELSE 0 END)
                            AS "groupStats.emailContactCount"
                FROM users u
                    LEFT JOIN user_group g ON u.group_id = g.id
                    LEFT JOIN contact_info ci ON u.id = ci.user_id AND ci.contact_type = 'EMAIL'
                WHERE g.id IS NOT NULL
                GROUP BY g.id, g.name
                ORDER BY "groupStats.userCount" DESC
                LIMIT 1
                """;
        return findExternal(sql, UserReport.class);
    }

}
