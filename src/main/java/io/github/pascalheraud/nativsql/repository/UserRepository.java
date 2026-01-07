package io.github.pascalheraud.nativsql.repository;

import io.github.pascalheraud.nativsql.domain.User;
import io.github.pascalheraud.nativsql.mapper.RowMapperFactory;
import io.github.pascalheraud.nativsql.mapper.TypeMapperFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for User entities.
 */
@Repository
public class UserRepository extends GenericRepository<User> {
    
    @Autowired
    public UserRepository(NamedParameterJdbcTemplate jdbcTemplate,
                          RowMapperFactory rowMapperFactory,
                          TypeMapperFactory typeMapperFactory) {
        super(jdbcTemplate, rowMapperFactory, typeMapperFactory, User.class);
    }
    
    @Override
    @NonNull
    protected String getTableName() {
        return "users";
    }

    /**
     * Finds a user by email with specified columns.
     *
     * @param email the user email
     * @param columns the property names (camelCase) to retrieve
     * @return the user or null if not found
     */
    public User findByEmail(String email, String... columns) {
        return findByProperty("email", email, columns);
    }
    
    /**
     * Finds users by city in their address with specified columns.
     *
     * @param city the city to search for
     * @param columns the property names (camelCase) to retrieve
     * @return list of users in that city
     */
    public List<User> findByCity(String city, String... columns) {
        // Using (address).city to access composite type field
        return findAllByPropertyExpression("(address).city", "city", city, columns);
    }

}