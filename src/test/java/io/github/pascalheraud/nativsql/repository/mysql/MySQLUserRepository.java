package io.github.pascalheraud.nativsql.repository.mysql;

import jakarta.annotation.Nonnull;

import io.github.pascalheraud.nativsql.domain.mysql.User;
import org.springframework.stereotype.Repository;

/**
 * Repository for User entities using MySQL.
 */
@Repository
public class MySQLUserRepository extends MySQLRepository<User, Long> {

    @Override
    @Nonnull
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

}
