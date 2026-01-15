package io.github.pascalheraud.nativsql.repository.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.github.pascalheraud.nativsql.domain.postgres.Address;
import io.github.pascalheraud.nativsql.domain.postgres.ContactInfo;
import io.github.pascalheraud.nativsql.domain.postgres.ContactType;
import io.github.pascalheraud.nativsql.domain.postgres.Preferences;
import io.github.pascalheraud.nativsql.domain.postgres.User;
import io.github.pascalheraud.nativsql.domain.postgres.UserReport;
import io.github.pascalheraud.nativsql.domain.postgres.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Integration tests for UserRepository using Testcontainers.
 */
@Import({ PGUserRepository.class, PGContactInfoRepository.class })
class PGUserRepositoryTest extends PGRepositoryTest {
    @Autowired
    private PGUserRepository userRepository;

    @Autowired
    private PGContactInfoRepository contactInfoRepository;

    @Test
    void testInsertUser() {
        // Given
        User user = User.builder()
                .firstName("Alice")
                .lastName("Wonder")
                .email("alice@example.com")
                .status(UserStatus.ACTIVE)
                .address(new Address("123 Main St", "Paris", "75001", "France"))
                .preferences(Preferences.builder().language("fr").theme("dark").notifications(true).build())
                .build();

        // When
        int rows = userRepository.insert(user, "firstName", "lastName", "email", "status", "address", "preferences");

        // Then
        assertThat(rows).isEqualTo(1);

        User found = userRepository.findByEmail("alice@example.com", "id", "firstName", "lastName", "email", "status",
                "address", "preferences");
        assertThat(found).isNotNull();
        assertThat(found.getFirstName()).isEqualTo("Alice");
        assertThat(found.getLastName()).isEqualTo("Wonder");
        assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(found.getAddress()).isNotNull();
        assertThat(found.getAddress().getCity()).isEqualTo("Paris");
        assertThat(found.getPreferences()).isNotNull();
        assertThat(found.getPreferences().getTheme()).isEqualTo("dark");
    }

    @Test
    void testInsertUserWithAllFields() {
        // Given
        User user = User.builder()
                .firstName("Bob")
                .lastName("Builder")
                .email("bob@example.com")
                .status(UserStatus.INACTIVE)
                .build();

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
        User user = User.builder()
                .firstName("Charlie")
                .lastName("Brown")
                .email("charlie@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User found = userRepository.findByEmail("charlie@example.com", "id", "firstName", "lastName", "email", "status",
                "address");
        assertThat(found).isNotNull();

        // When - update the user
        found.setFirstName("Charles");
        found.setStatus(UserStatus.SUSPENDED);

        Address newAddress = new Address("456 Oak Ave", "Lyon", "69001", "France");
        found.setAddress(newAddress);

        int rows = userRepository.update(found, "firstName", "status", "address");

        // Then
        assertThat(rows).isEqualTo(1);

        User updated = userRepository.findByEmail("charlie@example.com", "id", "firstName", "lastName", "email",
                "status", "address");
        assertThat(updated.getFirstName()).isEqualTo("Charles");
        assertThat(updated.getLastName()).isEqualTo("Brown"); // Unchanged
        assertThat(updated.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(updated.getAddress()).isNotNull();
        assertThat(updated.getAddress().getCity()).isEqualTo("Lyon");
    }

    @Test
    void testUpdateUserAllFields() {
        // Given
        User user = User.builder()
                .firstName("Dave")
                .lastName("Davidson")
                .email("dave@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User found = userRepository.findByEmail("dave@example.com", "id", "firstName", "lastName", "email", "status");

        // When - update specified fields
        found.setLastName("Davies");
        found.setStatus(UserStatus.INACTIVE);

        int rows = userRepository.update(found, "lastName", "status");

        // Then
        assertThat(rows).isEqualTo(1);

        User updated = userRepository.findByEmail("dave@example.com", "id", "lastName", "email", "status");
        assertThat(updated.getLastName()).isEqualTo("Davies");
        assertThat(updated.getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void testDeleteUser() {
        // Given
        User user = User.builder()
                .firstName("Eve")
                .lastName("Everton")
                .email("eve@example.com")
                .status(UserStatus.ACTIVE)
                .build();
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
    void testFindByCity() {
        // Given
        User user1 = User.builder()
                .firstName("Frank")
                .email("frank@example.com")
                .address(new Address("123 St", "Paris", "75001", "France"))
                .build();
        userRepository.insert(user1, "firstName", "email", "address");

        User user2 = User.builder()
                .firstName("Grace")
                .email("grace@example.com")
                .address(new Address("456 Ave", "Lyon", "69001", "France"))
                .build();
        userRepository.insert(user2, "firstName", "email", "address");

        User user3 = User.builder()
                .firstName("Henry")
                .email("henry@example.com")
                .address(new Address("789 Blvd", "Paris", "75002", "France"))
                .build();
        userRepository.insert(user3, "firstName", "email", "address");

        // When
        List<User> parisUsers = userRepository.findByCity("Paris", "id", "firstName", "email", "address");

        // Then
        assertThat(parisUsers).hasSize(2);
        assertThat(parisUsers).extracting(User::getFirstName)
                .containsExactlyInAnyOrder("Frank", "Henry");
    }

    @Test
    void testEnumMapping() {
        // Given
        User activeUser = User.builder()
                .email("active@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.insert(activeUser, "email", "status");

        User suspendedUser = User.builder()
                .email("suspended@example.com")
                .status(UserStatus.SUSPENDED)
                .build();
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
        User user = User.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User foundUser = userRepository.findByEmail("john@example.com", "id");
        Long userId = foundUser.getId();

        // Create contact information for this user
        ContactInfo email1 = ContactInfo.builder()
                .userId(userId)
                .contactType(ContactType.EMAIL)
                .contactValue("john@work.com")
                .isPrimary(true)
                .build();

        ContactInfo phone = ContactInfo.builder()
                .userId(userId)
                .contactType(ContactType.PHONE)
                .contactValue("+33612345678")
                .isPrimary(false)
                .build();

        ContactInfo linkedin = ContactInfo.builder()
                .userId(userId)
                .contactType(ContactType.LINKEDIN)
                .contactValue("linkedin.com/in/johndoe")
                .isPrimary(false)
                .build();

        contactInfoRepository.insert(email1, "userId", "contactType", "contactValue", "isPrimary");
        contactInfoRepository.insert(phone, "userId", "contactType", "contactValue", "isPrimary");
        contactInfoRepository.insert(linkedin, "userId", "contactType", "contactValue", "isPrimary");

        // When - Load user with contact information
        User userWithContacts = userRepository.findByIdWithContactInfos(
                userId,
                new String[] { "id", "contactType", "contactValue", "isPrimary" },
                "id", "firstName", "lastName", "email", "status");

        // Then
        assertThat(userWithContacts).isNotNull();
        assertThat(userWithContacts.getContacts()).isNotNull();
        assertThat(userWithContacts.getContacts()).hasSize(3);
        assertThat(userWithContacts.getContacts())
                .extracting(ContactInfo::getContactType)
                .containsExactlyInAnyOrder(
                        ContactType.EMAIL,
                        ContactType.PHONE,
                        ContactType.LINKEDIN);
    }

    @Test
    void testGetUserReport() {
        // Given - Create users with different preferences
        User user1 = User.builder()
                .firstName("User1")
                .email("user1@example.com")
                .status(UserStatus.ACTIVE)
                .preferences(Preferences.builder().language("fr").theme("dark").notifications(true).build())
                .build();
        userRepository.insert(user1, "firstName", "email", "status", "preferences");

        User user2 = User.builder()
                .firstName("User2")
                .email("user2@example.com")
                .status(UserStatus.ACTIVE)
                .preferences(Preferences.builder().language("en").theme("light").notifications(false).build())
                .build();
        userRepository.insert(user2, "firstName", "email", "status", "preferences");

        User user3 = User.builder()
                .firstName("User3")
                .email("user3@example.com")
                .status(UserStatus.INACTIVE)
                .preferences(Preferences.builder().language("fr").theme("auto").notifications(true).build())
                .build();
        userRepository.insert(user3, "firstName", "email", "status", "preferences");

        // Add contact info for users
        User foundUser1 = userRepository.findByEmail("user1@example.com", "id");
        ContactInfo contact1 = ContactInfo.builder()
                .userId(foundUser1.getId())
                .contactType(ContactType.EMAIL)
                .contactValue("user1@work.com")
                .build();
        contactInfoRepository.insert(contact1, "userId", "contactType", "contactValue");

        User foundUser2 = userRepository.findByEmail("user2@example.com", "id");
        ContactInfo contact2 = ContactInfo.builder()
                .userId(foundUser2.getId())
                .contactType(ContactType.PHONE)
                .contactValue("+33612345678")
                .build();
        contactInfoRepository.insert(contact2, "userId", "contactType", "contactValue");

        // When
        UserReport report = userRepository.getUsersReport();

        // Then
        assertThat(report).isNotNull();
        assertThat(report.getTotalUsers()).isEqualTo(3);
        assertThat(report.getUsersWithEmailContact()).isEqualTo(1);
        assertThat(report.getUsersWithFrenchPreference()).isEqualTo(2);
    }
}