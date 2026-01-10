package io.github.pascalheraud.nativsql.repository;

import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import io.github.pascalheraud.nativsql.domain.User;

/**
 * Repository for User entities.
 */
@Repository
public class UserRepository extends GenericRepository<User, Long> {

    @Override
    @NonNull
    protected String getTableName() {
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
                .join("contacts", List.of(contactColumns))
        );
    }

}