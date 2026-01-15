package io.github.pascalheraud.nativsql.repository.mysql;

import java.util.List;

import jakarta.annotation.Nonnull;

import io.github.pascalheraud.nativsql.domain.mysql.ContactInfo;
import io.github.pascalheraud.nativsql.domain.mysql.ContactType;
import org.springframework.stereotype.Repository;

/**
 * Repository for ContactInfo entities using MySQL.
 */
@Repository
public class MySQLContactInfoRepository extends MySQLRepository<ContactInfo, Long> {

    @Override
    @Nonnull
    protected String getTableName() {
        return "contact_info";
    }

    @Override
    protected Class<ContactInfo> getEntityClass() {
        return ContactInfo.class;
    }

    /**
     * Finds all contacts for a given user ID.
     *
     * @param userId  the user ID
     * @param columns the property names (camelCase) to retrieve
     * @return list of contact info for the user
     */
    public List<ContactInfo> findByUserId(Long userId, String... columns) {
        return findAllByProperty("userId", userId, columns);
    }

    /**
     * Finds contacts for a user by contact type using SQL.
     *
     * @param userId      the user ID
     * @param contactType the contact type
     * @param columns     the property names (camelCase) to retrieve
     * @return list of matching contacts
     */
    public List<ContactInfo> findByUserIdAndType(Long userId, ContactType contactType, String... columns) {
        return findAll(
                newFindQuery()
                        .select(columns)
                        .whereAndEquals("userId", userId)
                        .whereAndEquals("contactType", contactType));
    }

    /**
     * Finds the primary contact for a user by contact type using SQL.
     *
     * @param userId      the user ID
     * @param contactType the contact type
     * @param columns     the property names (camelCase) to retrieve
     * @return the primary contact or null if not found
     */
    public ContactInfo findPrimaryByUserIdAndType(Long userId, ContactType contactType, String... columns) {
        return find(
                newFindQuery()
                        .select(columns)
                        .whereAndEquals("userId", userId)
                        .whereAndEquals("contactType", contactType)
                        .whereAndEquals("isPrimary", true));
    }
}
