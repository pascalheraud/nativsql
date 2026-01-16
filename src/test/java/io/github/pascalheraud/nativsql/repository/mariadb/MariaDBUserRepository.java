package io.github.pascalheraud.nativsql.repository.mariadb;

import io.github.pascalheraud.nativsql.domain.mariadb.User;
import io.github.pascalheraud.nativsql.domain.mariadb.UserReport;
import org.springframework.lang.NonNull;
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
     * Finds a user by ID and loads their group information.
     * The group is loaded via a separate query using the groupId field.
     *
     * @param userId       the user ID
     * @param groupColumns the columns to load for the group
     * @param userColumns  the columns to load for the user
     * @return the user with group information, or null if not found
     */
    public User getUserWithGroup(Long userId, String[] groupColumns, String... userColumns) {
        // Load user first (must include groupId)
        java.util.List<String> userColsWithGroup = new java.util.ArrayList<>(java.util.Arrays.asList(userColumns));
        if (!userColsWithGroup.contains("groupId")) {
            userColsWithGroup.add("groupId");
        }

        User user = findById(userId, userColsWithGroup.toArray(new String[0]));
        if (user == null || user.getGroupId() == null) {
            return user;
        }

        // Load group using groupId
        MariaDBGroupRepository groupRepository = applicationContext.getBean(MariaDBGroupRepository.class);
        user.setGroup(groupRepository.findById(user.getGroupId(), groupColumns));

        return user;
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
                    (SELECT COUNT(*) FROM users) as total_users,
                    (SELECT COUNT(DISTINCT u.id) FROM users u
                     INNER JOIN contact_info ci ON u.id = ci.user_id
                     WHERE ci.contact_type = 'EMAIL') as users_with_email_contact,
                    (SELECT COUNT(*) FROM users u
                     WHERE JSON_EXTRACT(u.preferences, '$.language') = 'fr') as users_with_french_preference
                FROM DUAL
                """;
        return findExternal(sql, UserReport.class);
    }

}
