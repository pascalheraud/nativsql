package io.github.pascalheraud.nativsql.repository.mariadb;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pascalheraud.nativsql.domain.mariadb.ContactInfo;
import io.github.pascalheraud.nativsql.domain.mariadb.ContactType;
import io.github.pascalheraud.nativsql.domain.mariadb.User;
import io.github.pascalheraud.nativsql.domain.mariadb.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Integration tests for MariaDBUserRepository using Testcontainers.
 */
@Import({ MariaDBUserRepository.class, MariaDBContactInfoRepository.class })
class MariaDBUserRepositoryTest extends MariaDBRepositoryTest {
    @Autowired
    private MariaDBUserRepository userRepository;

    @Autowired
    private MariaDBContactInfoRepository contactInfoRepository;

    @Test
    void testInsertUser() {
        // Given
        User user = new User();
        user.setFirstName("Alice");
        user.setLastName("Wonder");
        user.setEmail("alice@example.com");
        user.setStatus(UserStatus.ACTIVE);

        // When
        int rows = userRepository.insert(user, "firstName", "lastName", "email", "status");

        // Then
        assertThat(rows).isEqualTo(1);

        User found = userRepository.findByEmail("alice@example.com", "id", "firstName", "lastName", "email", "status");
        assertThat(found).isNotNull();
        assertThat(found.getFirstName()).isEqualTo("Alice");
        assertThat(found.getLastName()).isEqualTo("Wonder");
        assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void testInsertUserWithAllFields() {
        // Given
        User user = new User();
        user.setFirstName("Bob");
        user.setLastName("Builder");
        user.setEmail("bob@example.com");
        user.setStatus(UserStatus.INACTIVE);

        // When - insert specified fields
        int rows = userRepository.insert(user, "firstName", "lastName", "email", "status");

        // Then
        assertThat(rows).isEqualTo(1);

        User found = userRepository.findByEmail("bob@example.com", "id", "firstName", "lastName", "email", "status");
        assertThat(found).isNotNull();
        assertThat(found.getFirstName()).isEqualTo("Bob");
        assertThat(found.getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void testUpdateUser() {
        // Given - insert a user first
        User user = new User();
        user.setFirstName("Charlie");
        user.setLastName("Brown");
        user.setEmail("charlie@example.com");
        user.setStatus(UserStatus.ACTIVE);
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User found = userRepository.findByEmail("charlie@example.com", "id", "firstName", "lastName", "email", "status");
        assertThat(found).isNotNull();

        // When - update the user
        found.setFirstName("Charles");
        found.setStatus(UserStatus.SUSPENDED);

        int rows = userRepository.update(found, "firstName", "status");

        // Then
        assertThat(rows).isEqualTo(1);

        User updated = userRepository.findByEmail("charlie@example.com", "id", "firstName", "lastName", "email", "status");
        assertThat(updated.getFirstName()).isEqualTo("Charles");
        assertThat(updated.getLastName()).isEqualTo("Brown"); // Unchanged
        assertThat(updated.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    void testDeleteUser() {
        // Given
        User user = new User();
        user.setFirstName("Eve");
        user.setLastName("Everton");
        user.setEmail("eve@example.com");
        user.setStatus(UserStatus.ACTIVE);
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User found = userRepository.findByEmail("eve@example.com", "id", "email");
        assertThat(found).isNotNull();

        // When
        int rows = userRepository.delete(found);

        // Then
        assertThat(rows).isEqualTo(1);

        User deleted = userRepository.findByEmail("eve@example.com", "id", "email");
        assertThat(deleted).isNull();
    }

    @Test
    void testEnumMapping() {
        // Given
        User activeUser = new User();
        activeUser.setEmail("active@example.com");
        activeUser.setStatus(UserStatus.ACTIVE);
        userRepository.insert(activeUser, "email", "status");

        User suspendedUser = new User();
        suspendedUser.setEmail("suspended@example.com");
        suspendedUser.setStatus(UserStatus.SUSPENDED);
        userRepository.insert(suspendedUser, "email", "status");

        // When
        User found1 = userRepository.findByEmail("active@example.com", "id", "email", "status");
        User found2 = userRepository.findByEmail("suspended@example.com", "id", "email", "status");

        // Then
        assertThat(found1.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(found2.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    void testOneToManyAssociation() {
        // Given - Create a user
        User user = new User();
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail("john@example.com");
        user.setStatus(UserStatus.ACTIVE);
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User foundUser = userRepository.findByEmail("john@example.com", "id");
        Long userId = foundUser.getId();

        // Create contact information for this user
        ContactInfo email1 = new ContactInfo();
        email1.setUserId(userId);
        email1.setContactType(ContactType.EMAIL);
        email1.setContactValue("john@work.com");
        email1.setIsPrimary(true);

        ContactInfo phone = new ContactInfo();
        phone.setUserId(userId);
        phone.setContactType(ContactType.PHONE);
        phone.setContactValue("+33612345678");
        phone.setIsPrimary(false);

        ContactInfo linkedin = new ContactInfo();
        linkedin.setUserId(userId);
        linkedin.setContactType(ContactType.LINKEDIN);
        linkedin.setContactValue("linkedin.com/in/johndoe");
        linkedin.setIsPrimary(false);

        contactInfoRepository.insert(email1, "userId", "contactType", "contactValue", "isPrimary");
        contactInfoRepository.insert(phone, "userId", "contactType", "contactValue", "isPrimary");
        contactInfoRepository.insert(linkedin, "userId", "contactType", "contactValue", "isPrimary");

        // When
        ContactInfo found1 = contactInfoRepository.findPrimaryByUserIdAndType(userId, ContactType.EMAIL, "id", "contactType", "contactValue", "isPrimary");
        ContactInfo found2 = contactInfoRepository.findByUserIdAndType(userId, ContactType.PHONE, "id", "contactType", "contactValue").get(0);

        // Then
        assertThat(found1).isNotNull();
        assertThat(found1.getIsPrimary()).isTrue();
        assertThat(found1.getContactType()).isEqualTo(ContactType.EMAIL);

        assertThat(found2).isNotNull();
        assertThat(found2.getContactType()).isEqualTo(ContactType.PHONE);
    }
}
