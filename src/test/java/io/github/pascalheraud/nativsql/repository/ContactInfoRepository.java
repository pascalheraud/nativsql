package io.github.pascalheraud.nativsql.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import jakarta.annotation.Nonnull;

import io.github.pascalheraud.nativsql.domain.ContactInfo;
import io.github.pascalheraud.nativsql.domain.ContactType;

/**
 * Repository for ContactInfo entities.
 */
@Repository
public class ContactInfoRepository extends GenericRepository<ContactInfo, Long> {

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
     * Finds contacts for a user by contact type.
     *
     * @param userId      the user ID
     * @param contactType the contact type
     * @param columns     the property names (camelCase) to retrieve
     * @return list of matching contacts
     */
    // TODO Pascal à refactorer pour ne pas avoir de recherche java mais une req SQL
    public List<ContactInfo> findByUserIdAndType(Long userId, ContactType contactType, String... columns) {
        // Filter by userId first, then filter in memory by contactType
        return findByUserId(userId, columns).stream()
                .filter(contact -> contact.getContactType() == contactType)
                .toList();
    }

    /**
     * Finds the primary contact for a user by contact type.
     *
     * @param userId      the user ID
     * @param contactType the contact type
     * @param columns     the property names (camelCase) to retrieve
     * @return the primary contact or null if not found
     */
    // TODO Pascal à refactorer pour ne pas avoir de recherche java mais une req SQL
    public ContactInfo findPrimaryByUserIdAndType(Long userId, ContactType contactType, String... columns) {
        return findByUserId(userId, columns).stream()
                .filter(contact -> contact.getContactType() == contactType)
                .filter(contact -> Boolean.TRUE.equals(contact.getIsPrimary()))
                .findFirst()
                .orElse(null);
    }
}
