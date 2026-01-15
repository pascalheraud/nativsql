package io.github.pascalheraud.nativsql.repository.mysql;

import io.github.pascalheraud.nativsql.domain.mysql.User;
import io.github.pascalheraud.nativsql.domain.mysql.UserReport;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

/**
 * Repository for User entities using MySQL.
 */
@Repository
public class MySQLUserRepository extends MySQLRepository<User, Long> {

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
