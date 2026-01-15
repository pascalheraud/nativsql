package io.github.pascalheraud.nativsql.repository.postgres;

import java.util.List;

import io.github.pascalheraud.nativsql.domain.postgres.User;
import io.github.pascalheraud.nativsql.domain.postgres.UserReport;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

/**
 * Repository for User entities.
 */
@Repository
public class PGUserRepository extends PGRepository<User, Long> {

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
                        .join("contacts", List.of(contactColumns)));
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
                     WHERE ci.contact_type = 'EMAIL'::contact_type) as users_with_email_contact,
                    (SELECT COUNT(*) FROM users u
                     WHERE u.preferences->>'language' = 'fr') as users_with_french_preference
                """;
        return findExternal(sql, UserReport.class);
    }

}