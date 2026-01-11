package io.github.pascalheraud.nativsql.repository.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import io.github.pascalheraud.nativsql.domain.ContactInfo;
import io.github.pascalheraud.nativsql.domain.ContactType;
import io.github.pascalheraud.nativsql.domain.User;
import io.github.pascalheraud.nativsql.domain.UserStatus;

/**
 * Integration tests for ContactInfoRepository using Testcontainers.
 */
@Import({ PGUserRepository.class, PGContactInfoRepository.class })
class PGContactInfoRepositoryTest  extends PGRepositoryTest{
    @Autowired
    private PGContactInfoRepository contactInfoRepository;

    @Autowired
    private PGUserRepository userRepository;

    private Long createTestUser() {
        User user = new User();
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail("john@example.com");
        user.setStatus(UserStatus.ACTIVE);
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User found = userRepository.findByEmail("john@example.com", "id");
        return found.getId();
    }

    @Test
    void testInsertContactInfo() {
        // Given
        Long testUserId = createTestUser();

        ContactInfo contact = new ContactInfo();
        contact.setUserId(testUserId);
        contact.setContactType(ContactType.EMAIL);
        contact.setContactValue("john@work.com");
        contact.setIsPrimary(true);

        // When
        int rows = contactInfoRepository.insert(contact, "userId", "contactType", "contactValue", "isPrimary");

        // Then
        assertThat(rows).isEqualTo(1);

        List<ContactInfo> contacts = contactInfoRepository.findByUserId(testUserId, "id", "userId", "contactType",
                "contactValue", "isPrimary");
        assertThat(contacts).hasSize(1);
        assertThat(contacts.get(0).getContactType()).isEqualTo(ContactType.EMAIL);
        assertThat(contacts.get(0).getContactValue()).isEqualTo("john@work.com");
        assertThat(contacts.get(0).getIsPrimary()).isTrue();
    }

    @Test
    void testInsertMultipleContacts() {
        // Given
        Long testUserId = createTestUser();

        ContactInfo email = new ContactInfo();
        email.setUserId(testUserId);
        email.setContactType(ContactType.EMAIL);
        email.setContactValue("john@work.com");
        email.setIsPrimary(true);

        ContactInfo phone = new ContactInfo();
        phone.setUserId(testUserId);
        phone.setContactType(ContactType.PHONE);
        phone.setContactValue("+33612345678");
        phone.setIsPrimary(false);

        ContactInfo linkedin = new ContactInfo();
        linkedin.setUserId(testUserId);
        linkedin.setContactType(ContactType.LINKEDIN);
        linkedin.setContactValue("linkedin.com/in/johndoe");
        linkedin.setIsPrimary(false);

        // When
        contactInfoRepository.insert(email, "userId", "contactType", "contactValue", "isPrimary");
        contactInfoRepository.insert(phone, "userId", "contactType", "contactValue", "isPrimary");
        contactInfoRepository.insert(linkedin, "userId", "contactType", "contactValue", "isPrimary");

        // Then
        List<ContactInfo> allContacts = contactInfoRepository.findByUserId(testUserId, "id", "contactType",
                "contactValue", "isPrimary");
        assertThat(allContacts).hasSize(3);
    }

    @Test
    void testFindByUserIdAndType() {
        // Given
        Long testUserId = createTestUser();

        ContactInfo email1 = new ContactInfo();
        email1.setUserId(testUserId);
        email1.setContactType(ContactType.EMAIL);
        email1.setContactValue("john@work.com");
        email1.setIsPrimary(true);

        ContactInfo email2 = new ContactInfo();
        email2.setUserId(testUserId);
        email2.setContactType(ContactType.EMAIL);
        email2.setContactValue("john@personal.com");
        email2.setIsPrimary(false);

        ContactInfo phone = new ContactInfo();
        phone.setUserId(testUserId);
        phone.setContactType(ContactType.PHONE);
        phone.setContactValue("+33612345678");
        phone.setIsPrimary(false);

        contactInfoRepository.insert(email1, "userId", "contactType", "contactValue", "isPrimary");
        contactInfoRepository.insert(email2, "userId", "contactType", "contactValue", "isPrimary");
        contactInfoRepository.insert(phone, "userId", "contactType", "contactValue", "isPrimary");

        // When
        List<ContactInfo> emails = contactInfoRepository.findByUserIdAndType(testUserId, ContactType.EMAIL, "id",
                "contactType", "contactValue", "isPrimary");

        // Then
        assertThat(emails).hasSize(2);
        assertThat(emails).allMatch(c -> c.getContactType() == ContactType.EMAIL);
    }

    @Test
    void testFindPrimaryByUserIdAndType() {
        // Given
        Long testUserId = createTestUser();

        ContactInfo email1 = new ContactInfo();
        email1.setUserId(testUserId);
        email1.setContactType(ContactType.EMAIL);
        email1.setContactValue("john@work.com");
        email1.setIsPrimary(true);

        ContactInfo email2 = new ContactInfo();
        email2.setUserId(testUserId);
        email2.setContactType(ContactType.EMAIL);
        email2.setContactValue("john@personal.com");
        email2.setIsPrimary(false);

        contactInfoRepository.insert(email1, "userId", "contactType", "contactValue", "isPrimary");
        contactInfoRepository.insert(email2, "userId", "contactType", "contactValue", "isPrimary");

        // When
        ContactInfo primaryEmail = contactInfoRepository.findPrimaryByUserIdAndType(testUserId, ContactType.EMAIL, "id",
                "contactType", "contactValue", "isPrimary");

        // Then
        assertThat(primaryEmail).isNotNull();
        assertThat(primaryEmail.getContactValue()).isEqualTo("john@work.com");
        assertThat(primaryEmail.getIsPrimary()).isTrue();
    }

    @Test
    void testDeleteContact() {
        // Given
        Long testUserId = createTestUser();

        ContactInfo contact = new ContactInfo();
        contact.setUserId(testUserId);
        contact.setContactType(ContactType.EMAIL);
        contact.setContactValue("john@work.com");
        contactInfoRepository.insert(contact, "userId", "contactType", "contactValue");

        List<ContactInfo> contacts = contactInfoRepository.findByUserId(testUserId, "id");
        assertThat(contacts).hasSize(1);

        // When
        int rows = contactInfoRepository.delete(contacts.get(0));

        // Then
        assertThat(rows).isEqualTo(1);
        List<ContactInfo> afterDelete = contactInfoRepository.findByUserId(testUserId, "id");
        assertThat(afterDelete).isEmpty();
    }
}
