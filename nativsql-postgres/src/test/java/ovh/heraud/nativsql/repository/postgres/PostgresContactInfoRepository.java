package ovh.heraud.nativsql.repository.postgres;

import java.util.List;

import ovh.heraud.nativsql.domain.postgres.ContactInfo;
import ovh.heraud.nativsql.domain.postgres.ContactType;

import org.springframework.stereotype.Repository;

/**
 * Repository for ContactInfo entities.
 */
@Repository
public class PostgresContactInfoRepository extends PostgresRepository<ContactInfo, Long> {

    @Override
  public String getTableName() {
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

    /**
     * Finds contacts for a user filtered by the {@code isPrimary} boolean
     * column, including {@code null} — exercises {@code PostgresBooleanTypeMapper}'s
     * generated {@code ::boolean} cast for a generated-query WHERE condition
     * (issue #118).
     *
     * @param userId    the user ID
     * @param isPrimary the value to compare {@code isPrimary} against, may be null
     * @param columns   the property names (camelCase) to retrieve
     * @return list of matching contacts
     */
    public List<ContactInfo> findByUserIdAndIsPrimary(Long userId, Boolean isPrimary, String... columns) {
        return findAll(
                newFindQuery()
                        .select(columns)
                        .whereAndEquals("userId", userId)
                        .whereAndEquals("isPrimary", isPrimary));
    }
}
