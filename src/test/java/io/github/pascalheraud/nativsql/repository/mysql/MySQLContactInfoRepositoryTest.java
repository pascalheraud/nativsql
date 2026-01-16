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
        User user = User.builder()
            .firstName("Alice")
            .lastName("Wonder")
            .email("alice@example.com")
            .status(UserStatus.ACTIVE)
            .build();
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User foundUser = userRepository.findByEmail("alice@example.com", "id");
        Long userId = foundUser.getId();

        // Create contact info
        ContactInfo contact = ContactInfo.builder()
            .userId(userId)
            .contactType(ContactType.EMAIL)
            .contactValue("alice@work.com")
            .isPrimary(true)
            .build();

        // When
        contactInfoRepository.insert(contact, "userId", "contactType", "contactValue", "isPrimary");

        // Then
        ContactInfo found = contactInfoRepository.findPrimaryByUserIdAndType(userId, ContactType.EMAIL,
            "id", "userId", "contactType", "contactValue", "isPrimary");
        assertThat(found).isNotNull();
        assertThat(found.getContactValue()).isEqualTo("alice@work.com");
        assertThat(found.getIsPrimary()).isTrue();
    }

    @Test
    void testFindByUserId() {
        // Given - Create a user with multiple contacts
        User user = User.builder()
            .firstName("Bob")
            .lastName("Builder")
            .email("bob@example.com")
            .status(UserStatus.ACTIVE)
            .build();
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User foundUser = userRepository.findByEmail("bob@example.com", "id");
        Long userId = foundUser.getId();

        // Add multiple contacts
        ContactInfo email = ContactInfo.builder()
            .userId(userId)
            .contactType(ContactType.EMAIL)
            .contactValue("bob@work.com")
            .isPrimary(true)
            .build();
        contactInfoRepository.insert(email, "userId", "contactType", "contactValue", "isPrimary");

        ContactInfo phone = ContactInfo.builder()
            .userId(userId)
            .contactType(ContactType.PHONE)
            .contactValue("+33612345678")
            .isPrimary(false)
            .build();
        contactInfoRepository.insert(phone, "userId", "contactType", "contactValue", "isPrimary");

        ContactInfo linkedin = ContactInfo.builder()
            .userId(userId)
            .contactType(ContactType.LINKEDIN)
            .contactValue("linkedin.com/in/bobbuilder")
            .isPrimary(false)
            .build();
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
        User user = User.builder()
            .firstName("Charlie")
            .lastName("Brown")
            .email("charlie@example.com")
            .status(UserStatus.ACTIVE)
            .build();
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User foundUser = userRepository.findByEmail("charlie@example.com", "id");
        Long userId = foundUser.getId();

        // Add multiple email contacts
        ContactInfo email1 = ContactInfo.builder()
            .userId(userId)
            .contactType(ContactType.EMAIL)
            .contactValue("charlie@work.com")
            .isPrimary(true)
            .build();
        contactInfoRepository.insert(email1, "userId", "contactType", "contactValue", "isPrimary");

        ContactInfo email2 = ContactInfo.builder()
            .userId(userId)
            .contactType(ContactType.EMAIL)
            .contactValue("charlie@personal.com")
            .isPrimary(false)
            .build();
        contactInfoRepository.insert(email2, "userId", "contactType", "contactValue", "isPrimary");

        ContactInfo phone = ContactInfo.builder()
            .userId(userId)
            .contactType(ContactType.PHONE)
            .contactValue("+33987654321")
            .isPrimary(false)
            .build();
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
        User user = User.builder()
            .firstName("Dave")
            .lastName("Davidson")
            .email("dave@example.com")
            .status(UserStatus.ACTIVE)
            .build();
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User foundUser = userRepository.findByEmail("dave@example.com", "id");
        Long userId = foundUser.getId();

        // Add multiple phone contacts, only one is primary
        ContactInfo phone1 = ContactInfo.builder()
            .userId(userId)
            .contactType(ContactType.PHONE)
            .contactValue("+33612345678")
            .isPrimary(true)
            .build();
        contactInfoRepository.insert(phone1, "userId", "contactType", "contactValue", "isPrimary");

        ContactInfo phone2 = ContactInfo.builder()
            .userId(userId)
            .contactType(ContactType.PHONE)
            .contactValue("+33687654321")
            .isPrimary(false)
            .build();
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
        User user = User.builder()
            .firstName("Eve")
            .lastName("Everton")
            .email("eve@example.com")
            .status(UserStatus.ACTIVE)
            .build();
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User foundUser = userRepository.findByEmail("eve@example.com", "id");
        Long userId = foundUser.getId();

        ContactInfo contact = ContactInfo.builder()
            .userId(userId)
            .contactType(ContactType.EMAIL)
            .contactValue("eve@old.com")
            .isPrimary(false)
            .build();
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
        User user = User.builder()
            .firstName("Frank")
            .lastName("Franklin")
            .email("frank@example.com")
            .status(UserStatus.ACTIVE)
            .build();
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User foundUser = userRepository.findByEmail("frank@example.com", "id");
        Long userId = foundUser.getId();

        ContactInfo contact = ContactInfo.builder()
            .userId(userId)
            .contactType(ContactType.TWITTER)
            .contactValue("@frank")
            .isPrimary(false)
            .build();
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
