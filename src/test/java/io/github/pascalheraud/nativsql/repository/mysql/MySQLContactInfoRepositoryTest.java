package io.github.pascalheraud.nativsql.repository.mysql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.github.pascalheraud.nativsql.domain.mysql.ContactInfo;
import io.github.pascalheraud.nativsql.domain.mysql.ContactType;
import io.github.pascalheraud.nativsql.domain.mysql.User;
import io.github.pascalheraud.nativsql.domain.mysql.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Integration tests for MySQLContactInfoRepository using Testcontainers.
 */
@Import({ MySQLUserRepository.class, MySQLContactInfoRepository.class })
class MySQLContactInfoRepositoryTest extends MySQLRepositoryTest {
    @Autowired
    private MySQLUserRepository userRepository;

    @Autowired
    private MySQLContactInfoRepository contactInfoRepository;

    @Test
    void testInsertContactInfo() {
        // Given - Create a user first
        User user = new User();
        user.setFirstName("Alice");
        user.setLastName("Wonder");
        user.setEmail("alice@example.com");
        user.setStatus(UserStatus.ACTIVE);
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User foundUser = userRepository.findByEmail("alice@example.com", "id");
        Long userId = foundUser.getId();

        // Create contact info
        ContactInfo contact = new ContactInfo();
        contact.setUserId(userId);
        contact.setContactType(ContactType.EMAIL);
        contact.setContactValue("alice@work.com");
        contact.setIsPrimary(true);

        // When
        int rows = contactInfoRepository.insert(contact, "userId", "contactType", "contactValue", "isPrimary");

        // Then
        assertThat(rows).isEqualTo(1);

        ContactInfo found = contactInfoRepository.findPrimaryByUserIdAndType(userId, ContactType.EMAIL,
            "id", "userId", "contactType", "contactValue", "isPrimary");
        assertThat(found).isNotNull();
        assertThat(found.getContactValue()).isEqualTo("alice@work.com");
        assertThat(found.getIsPrimary()).isTrue();
    }

    @Test
    void testFindByUserId() {
        // Given - Create a user with multiple contacts
        User user = new User();
        user.setFirstName("Bob");
        user.setLastName("Builder");
        user.setEmail("bob@example.com");
        user.setStatus(UserStatus.ACTIVE);
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User foundUser = userRepository.findByEmail("bob@example.com", "id");
        Long userId = foundUser.getId();

        // Add multiple contacts
        ContactInfo email = new ContactInfo();
        email.setUserId(userId);
        email.setContactType(ContactType.EMAIL);
        email.setContactValue("bob@work.com");
        email.setIsPrimary(true);
        contactInfoRepository.insert(email, "userId", "contactType", "contactValue", "isPrimary");

        ContactInfo phone = new ContactInfo();
        phone.setUserId(userId);
        phone.setContactType(ContactType.PHONE);
        phone.setContactValue("+33612345678");
        phone.setIsPrimary(false);
        contactInfoRepository.insert(phone, "userId", "contactType", "contactValue", "isPrimary");

        ContactInfo linkedin = new ContactInfo();
        linkedin.setUserId(userId);
        linkedin.setContactType(ContactType.LINKEDIN);
        linkedin.setContactValue("linkedin.com/in/bobbuilder");
        linkedin.setIsPrimary(false);
        contactInfoRepository.insert(linkedin, "userId", "contactType", "contactValue", "isPrimary");

        // When
        List<ContactInfo> contacts = contactInfoRepository.findByUserId(userId,
            "id", "userId", "contactType", "contactValue", "isPrimary");

        // Then
        assertThat(contacts).hasSize(3);
        assertThat(contacts).extracting(ContactInfo::getContactType)
            .containsExactlyInAnyOrder(ContactType.EMAIL, ContactType.PHONE, ContactType.LINKEDIN);
    }

    @Test
    void testFindByUserIdAndType() {
        // Given - Create a user with contacts of different types
        User user = new User();
        user.setFirstName("Charlie");
        user.setLastName("Brown");
        user.setEmail("charlie@example.com");
        user.setStatus(UserStatus.ACTIVE);
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User foundUser = userRepository.findByEmail("charlie@example.com", "id");
        Long userId = foundUser.getId();

        // Add multiple email contacts
        ContactInfo email1 = new ContactInfo();
        email1.setUserId(userId);
        email1.setContactType(ContactType.EMAIL);
        email1.setContactValue("charlie@work.com");
        email1.setIsPrimary(true);
        contactInfoRepository.insert(email1, "userId", "contactType", "contactValue", "isPrimary");

        ContactInfo email2 = new ContactInfo();
        email2.setUserId(userId);
        email2.setContactType(ContactType.EMAIL);
        email2.setContactValue("charlie@personal.com");
        email2.setIsPrimary(false);
        contactInfoRepository.insert(email2, "userId", "contactType", "contactValue", "isPrimary");

        ContactInfo phone = new ContactInfo();
        phone.setUserId(userId);
        phone.setContactType(ContactType.PHONE);
        phone.setContactValue("+33987654321");
        phone.setIsPrimary(false);
        contactInfoRepository.insert(phone, "userId", "contactType", "contactValue", "isPrimary");

        // When
        List<ContactInfo> emails = contactInfoRepository.findByUserIdAndType(userId, ContactType.EMAIL,
            "id", "userId", "contactType", "contactValue", "isPrimary");

        // Then
        assertThat(emails).hasSize(2);
        assertThat(emails).extracting(ContactInfo::getContactType)
            .containsOnly(ContactType.EMAIL);
    }

    @Test
    void testFindPrimaryByUserIdAndType() {
        // Given - Create a user with multiple contacts of same type
        User user = new User();
        user.setFirstName("Dave");
        user.setLastName("Davidson");
        user.setEmail("dave@example.com");
        user.setStatus(UserStatus.ACTIVE);
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User foundUser = userRepository.findByEmail("dave@example.com", "id");
        Long userId = foundUser.getId();

        // Add multiple phone contacts, only one is primary
        ContactInfo phone1 = new ContactInfo();
        phone1.setUserId(userId);
        phone1.setContactType(ContactType.PHONE);
        phone1.setContactValue("+33612345678");
        phone1.setIsPrimary(true);
        contactInfoRepository.insert(phone1, "userId", "contactType", "contactValue", "isPrimary");

        ContactInfo phone2 = new ContactInfo();
        phone2.setUserId(userId);
        phone2.setContactType(ContactType.PHONE);
        phone2.setContactValue("+33687654321");
        phone2.setIsPrimary(false);
        contactInfoRepository.insert(phone2, "userId", "contactType", "contactValue", "isPrimary");

        // When
        ContactInfo primaryPhone = contactInfoRepository.findPrimaryByUserIdAndType(userId, ContactType.PHONE,
            "id", "userId", "contactType", "contactValue", "isPrimary");

        // Then
        assertThat(primaryPhone).isNotNull();
        assertThat(primaryPhone.getIsPrimary()).isTrue();
        assertThat(primaryPhone.getContactValue()).isEqualTo("+33612345678");
    }

    @Test
    void testUpdateContactInfo() {
        // Given - Create a user and contact
        User user = new User();
        user.setFirstName("Eve");
        user.setLastName("Everton");
        user.setEmail("eve@example.com");
        user.setStatus(UserStatus.ACTIVE);
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User foundUser = userRepository.findByEmail("eve@example.com", "id");
        Long userId = foundUser.getId();

        ContactInfo contact = new ContactInfo();
        contact.setUserId(userId);
        contact.setContactType(ContactType.EMAIL);
        contact.setContactValue("eve@old.com");
        contact.setIsPrimary(false);
        contactInfoRepository.insert(contact, "userId", "contactType", "contactValue", "isPrimary");

        ContactInfo found = contactInfoRepository.findByUserIdAndType(userId, ContactType.EMAIL, "id").get(0);

        // When - Update the contact
        found.setContactValue("eve@new.com");
        found.setIsPrimary(true);
        int rows = contactInfoRepository.update(found, "contactValue", "isPrimary");

        // Then
        assertThat(rows).isEqualTo(1);

        ContactInfo updated = contactInfoRepository.findPrimaryByUserIdAndType(userId, ContactType.EMAIL,
            "id", "contactValue", "isPrimary");
        assertThat(updated.getContactValue()).isEqualTo("eve@new.com");
        assertThat(updated.getIsPrimary()).isTrue();
    }

    @Test
    void testDeleteContactInfo() {
        // Given - Create a user and contact
        User user = new User();
        user.setFirstName("Frank");
        user.setLastName("Franklin");
        user.setEmail("frank@example.com");
        user.setStatus(UserStatus.ACTIVE);
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User foundUser = userRepository.findByEmail("frank@example.com", "id");
        Long userId = foundUser.getId();

        ContactInfo contact = new ContactInfo();
        contact.setUserId(userId);
        contact.setContactType(ContactType.TWITTER);
        contact.setContactValue("@frank");
        contact.setIsPrimary(false);
        contactInfoRepository.insert(contact, "userId", "contactType", "contactValue", "isPrimary");

        ContactInfo found = contactInfoRepository.findByUserIdAndType(userId, ContactType.TWITTER, "id").get(0);
        assertThat(found).isNotNull();

        // When
        int rows = contactInfoRepository.delete(found);

        // Then
        assertThat(rows).isEqualTo(1);

        List<ContactInfo> deleted = contactInfoRepository.findByUserIdAndType(userId, ContactType.TWITTER, "id");
        assertThat(deleted).isEmpty();
    }
}
