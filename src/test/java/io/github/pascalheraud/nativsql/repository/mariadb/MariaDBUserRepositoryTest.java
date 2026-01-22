package io.github.pascalheraud.nativsql.repository.mariadb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.github.pascalheraud.nativsql.domain.mariadb.ContactInfo;
import io.github.pascalheraud.nativsql.domain.mariadb.ContactType;
import io.github.pascalheraud.nativsql.domain.mariadb.Group;
import io.github.pascalheraud.nativsql.domain.mariadb.Preferences;
import io.github.pascalheraud.nativsql.domain.mariadb.User;
import io.github.pascalheraud.nativsql.domain.mariadb.UserReport;
import io.github.pascalheraud.nativsql.domain.mariadb.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Integration tests for MariaDBUserRepository using Testcontainers.
 */
@Import({ MariaDBUserRepository.class, MariaDBContactInfoRepository.class, MariaDBGroupRepository.class })
class MariaDBUserRepositoryTest extends MariaDBRepositoryTest {
    @Autowired
    private MariaDBUserRepository userRepository;

    @Autowired
    private MariaDBContactInfoRepository contactInfoRepository;

    @Autowired
    private MariaDBGroupRepository groupRepository;

    @Test
    void testInsertUser() {
        // Given
        User user = new User();
        user.setFirstName("Alice");
        user.setLastName("Wonder");
        user.setEmail("alice@example.com");
        user.setStatus(UserStatus.ACTIVE);

        // When
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        // Then
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
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        // Then
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

    @Test
    void testGetUserWithGroup() {
        // Given - Create a group
        Group group = Group.builder()
                .name("Engineering")
                .build();
        groupRepository.insert(group, "name");
        Long groupId = group.getId();

        // Create a user in the group
        User user = User.builder()
                .firstName("Alice")
                .lastName("Engineer")
                .email("alice@example.com")
                .status(UserStatus.ACTIVE)
                .groupId(groupId)
                .build();
        userRepository.insert(user, "firstName", "lastName", "email", "status", "groupId");
        Long userId = user.getId();

        // When - Load user with group information
        User userWithGroup = userRepository.getUserWithGroup(
                userId,
                new String[] { "id", "name" },
                "id", "firstName", "lastName", "email", "groupId");

        // Then
        assertThat(userWithGroup).isNotNull();
        assertThat(userWithGroup.getId()).isEqualTo(userId);
        assertThat(userWithGroup.getFirstName()).isEqualTo("Alice");
        assertThat(userWithGroup.getLastName()).isEqualTo("Engineer");
        assertThat(userWithGroup.getGroup()).isNotNull();
        assertThat(userWithGroup.getGroup().getId()).isEqualTo(groupId);
        assertThat(userWithGroup.getGroup().getName()).isEqualTo("Engineering");
    }

    @Test
    void testGetUserReportWithGroupStats() {
        // Given - Create groups
        Group engGroup = Group.builder()
                .name("Engineering")
                .build();
        groupRepository.insert(engGroup, "name");
        Long engGroupId = engGroup.getId();

        Group salesGroup = Group.builder()
                .name("Sales")
                .build();
        groupRepository.insert(salesGroup, "name");
        Long salesGroupId = salesGroup.getId();

        // Create users in groups
        User user1 = User.builder()
                .firstName("Alice")
                .lastName("Dev")
                .email("alice@example.com")
                .status(UserStatus.ACTIVE)
                .groupId(engGroupId)
                .preferences(Preferences.builder().language("fr").theme("dark").notifications(true).build())
                .build();
        userRepository.insert(user1, "firstName", "lastName", "email", "status", "groupId", "preferences");

        User user2 = User.builder()
                .firstName("Bob")
                .lastName("Sales")
                .email("bob@example.com")
                .status(UserStatus.ACTIVE)
                .groupId(salesGroupId)
                .preferences(Preferences.builder().language("en").theme("light").notifications(false).build())
                .build();
        userRepository.insert(user2, "firstName", "lastName", "email", "status", "groupId", "preferences");

        User user3 = User.builder()
                .firstName("Charlie")
                .lastName("SalesMgr")
                .email("charlie@example.com")
                .status(UserStatus.ACTIVE)
                .groupId(salesGroupId)
                .preferences(Preferences.builder().language("fr").theme("auto").notifications(true).build())
                .build();
        userRepository.insert(user3, "firstName", "lastName", "email", "status", "groupId", "preferences");

        // Add contact info for some users
        User foundUser1 = userRepository.findByEmail("alice@example.com", "id");
        ContactInfo contact1 = ContactInfo.builder()
                .userId(foundUser1.getId())
                .contactType(ContactType.EMAIL)
                .contactValue("alice@work.com")
                .build();
        contactInfoRepository.insert(contact1, "userId", "contactType", "contactValue");

        User foundUser2 = userRepository.findByEmail("bob@example.com", "id");
        ContactInfo contact2 = ContactInfo.builder()
                .userId(foundUser2.getId())
                .contactType(ContactType.EMAIL)
                .contactValue("bob@work.com")
                .build();
        contactInfoRepository.insert(contact2, "userId", "contactType", "contactValue");

        // When
        UserReport report = userRepository.getUsersReportWithGroupStats();

        // Then - Verify hierarchical structure
        assertThat(report).isNotNull();
        assertThat(report.getTotalUsers()).isEqualTo(3);
        assertThat(report.getUsersWithEmailContact()).isEqualTo(2);
        assertThat(report.getUsersWithFrenchPreference()).isEqualTo(2);

        // Verify nested group stats
        assertThat(report.getGroupStats()).isNotNull();
        assertThat(report.getGroupStats().getGroupId()).isNotNull();
        assertThat(report.getGroupStats().getGroupName()).isNotNull();
        assertThat(report.getGroupStats().getUserCount()).isGreaterThan(0);
        assertThat(report.getGroupStats().getActiveUserCount()).isGreaterThan(0);
        assertThat(report.getGroupStats().getEmailContactCount()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void testFindAllByIds() {
        // Given - Create multiple users
        User user1 = User.builder()
                .firstName("User1")
                .lastName("Test1")
                .email("user1@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.insert(user1, "firstName", "lastName", "email", "status");

        User user2 = User.builder()
                .firstName("User2")
                .lastName("Test2")
                .email("user2@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.insert(user2, "firstName", "lastName", "email", "status");

        User user3 = User.builder()
                .firstName("User3")
                .lastName("Test3")
                .email("user3@example.com")
                .status(UserStatus.INACTIVE)
                .build();
        userRepository.insert(user3, "firstName", "lastName", "email", "status");

        Long userId1 = user1.getId();
        Long userId3 = user3.getId();

        // When - Find multiple users by their IDs
        List<User> foundUsers = userRepository.findAllByIds(
                List.of(userId1, userId3),
                "id", "firstName", "lastName", "email", "status");

        // Then
        assertThat(foundUsers).hasSize(2);
        assertThat(foundUsers)
                .extracting(User::getId)
                .containsExactlyInAnyOrder(userId1, userId3);
        assertThat(foundUsers)
                .extracting(User::getFirstName)
                .containsExactlyInAnyOrder("User1", "User3");
        assertThat(foundUsers)
                .extracting(User::getStatus)
                .containsExactlyInAnyOrder(UserStatus.ACTIVE, UserStatus.INACTIVE);
    }
}
