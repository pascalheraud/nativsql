package io.github.pascalheraud.nativsql.repository;

import io.github.pascalheraud.nativsql.domain.ContactInfo;
import io.github.pascalheraud.nativsql.domain.ContactType;
import io.github.pascalheraud.nativsql.mapper.RowMapperFactory;
import io.github.pascalheraud.nativsql.mapper.TypeMapperFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for ContactInfo entities.
 */
@Repository
public class ContactInfoRepository extends GenericRepository<ContactInfo, Long> {

    @Autowired
    public ContactInfoRepository(NamedParameterJdbcTemplate jdbcTemplate,
                                  RowMapperFactory rowMapperFactory,
                                  TypeMapperFactory typeMapperFactory) {
        super(jdbcTemplate, rowMapperFactory, typeMapperFactory, ContactInfo.class);
    }

    @Override
    @NonNull
    protected String getTableName() {
        return "contact_info";
    }

    /**
     * Finds all contacts for a given user ID.
     *
     * @param userId the user ID
     * @param columns the property names (camelCase) to retrieve
     * @return list of contact info for the user
     */
    public List<ContactInfo> findByUserId(Long userId, String... columns) {
        return findAllByProperty("userId", userId, columns);
    }

    /**
     * Finds contacts for a user by contact type.
     *
     * @param userId the user ID
     * @param contactType the contact type
     * @param columns the property names (camelCase) to retrieve
     * @return list of matching contacts
     */
    public List<ContactInfo> findByUserIdAndType(Long userId, ContactType contactType, String... columns) {
        // Filter by userId first, then filter in memory by contactType
        return findByUserId(userId, columns).stream()
                .filter(contact -> contact.getContactType() == contactType)
                .toList();
    }

    /**
     * Finds the primary contact for a user by contact type.
     *
     * @param userId the user ID
     * @param contactType the contact type
     * @param columns the property names (camelCase) to retrieve
     * @return the primary contact or null if not found
     */
    public ContactInfo findPrimaryByUserIdAndType(Long userId, ContactType contactType, String... columns) {
        return findByUserId(userId, columns).stream()
                .filter(contact -> contact.getContactType() == contactType)
                .filter(contact -> Boolean.TRUE.equals(contact.getIsPrimary()))
                .findFirst()
                .orElse(null);
    }
}
