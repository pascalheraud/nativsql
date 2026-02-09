package ovh.heraud.nativsql.repository.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import ovh.heraud.nativsql.domain.postgres.Address;
import ovh.heraud.nativsql.domain.postgres.ContactInfo;
import ovh.heraud.nativsql.domain.postgres.ContactType;
import ovh.heraud.nativsql.domain.postgres.Group;
import ovh.heraud.nativsql.domain.postgres.Preferences;
import ovh.heraud.nativsql.domain.postgres.User;
import ovh.heraud.nativsql.domain.postgres.UserReport;
import ovh.heraud.nativsql.domain.postgres.UserStatus;
import org.junit.jupiter.api.Test;
import org.postgis.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Integration tests for UserRepository using Testcontainers.
 */
@Import({ PostgresUserRepository.class, PostgresContactInfoRepository.class, PostgresGroupRepository.class })
class PostgresUserRepositoryTest extends PostgresRepositoryTest {
        @Autowired
        private PostgresUserRepository userRepository;

        @Autowired
        private PostgresContactInfoRepository contactInfoRepository;

        @Autowired
        private PostgresGroupRepository groupRepository;

    @Test
    void testInsertUser() throws SQLException {
        // Given
        User user = User.builder()
                .firstName("Alice")
                .lastName("Wonder")
                .email("alice@example.com")
                .status(UserStatus.ACTIVE)
                .address(new Address("123 Main St", "Paris", "75001", "France"))
                .preferences(Preferences.builder().language("fr").theme("dark").notifications(true).build())
                .position(new Point("POINT(4 45)"))
                .build();

        // When
        userRepository.insert(user, "firstName", "lastName", "email", "status", "address", "preferences", "position");

        // Then
        assertThat(user.getId()).isNotNull();

        User found = userRepository.findByEmail("alice@example.com", "id", "firstName", "lastName", "email", "status",
                "address", "preferences", "position");
        assertThat(found).isNotNull();
        assertThat(found.getFirstName()).isEqualTo("Alice");
        assertThat(found.getLastName()).isEqualTo("Wonder");
        assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(found.getAddress()).isNotNull();
        assertThat(found.getAddress().getCity()).isEqualTo("Paris");
        assertThat(found.getPreferences()).isNotNull();
        assertThat(found.getPreferences().getTheme()).isEqualTo("dark");
        assertThat(found.getPosition().toString()).isEqualTo("SRID=4326;POINT(4 45)");
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
                userRepository.insert(user, "firstName", "lastName", "email", "status");

                // Then
                assertThat(user.getId()).isNotNull();

                User found = userRepository.findByEmail("bob@example.com", "id", "firstName", "lastName", "email",
                                "status");
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

                User found = userRepository.findByEmail("charlie@example.com", "id", "firstName", "lastName", "email",
                                "status",
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

                User found = userRepository.findByEmail("dave@example.com", "id", "firstName", "lastName", "email",
                                "status");

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
                                .preferences(Preferences.builder().language("fr").theme("dark").notifications(true)
                                                .build())
                                .build();
                userRepository.insert(user1, "firstName", "email", "status", "preferences");

                User user2 = User.builder()
                                .firstName("User2")
                                .email("user2@example.com")
                                .status(UserStatus.ACTIVE)
                                .preferences(Preferences.builder().language("en").theme("light").notifications(false)
                                                .build())
                                .build();
                userRepository.insert(user2, "firstName", "email", "status", "preferences");

                User user3 = User.builder()
                                .firstName("User3")
                                .email("user3@example.com")
                                .status(UserStatus.INACTIVE)
                                .preferences(Preferences.builder().language("fr").theme("auto").notifications(true)
                                                .build())
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
                                .preferences(Preferences.builder().language("fr").theme("dark").notifications(true)
                                                .build())
                                .build();
                userRepository.insert(user1, "firstName", "lastName", "email", "status", "groupId", "preferences");

                User user2 = User.builder()
                                .firstName("Bob")
                                .lastName("Sales")
                                .email("bob@example.com")
                                .status(UserStatus.ACTIVE)
                                .groupId(salesGroupId)
                                .preferences(Preferences.builder().language("en").theme("light").notifications(false)
                                                .build())
                                .build();
                userRepository.insert(user2, "firstName", "lastName", "email", "status", "groupId", "preferences");

                User user3 = User.builder()
                                .firstName("Charlie")
                                .lastName("SalesMgr")
                                .email("charlie@example.com")
                                .status(UserStatus.ACTIVE)
                                .groupId(salesGroupId)
                                .preferences(Preferences.builder().language("fr").theme("auto").notifications(true)
                                                .build())
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
                                .address(new Address("123 Main St", "Paris", "75001", "France"))
                                .build();
                userRepository.insert(user1, "firstName", "lastName", "email", "status", "address");

                User user2 = User.builder()
                                .firstName("User2")
                                .lastName("Test2")
                                .email("user2@example.com")
                                .status(UserStatus.ACTIVE)
                                .address(new Address("456 Oak Ave", "Lyon", "69000", "France"))
                                .build();
                userRepository.insert(user2, "firstName", "lastName", "email", "status", "address");

                User user3 = User.builder()
                                .firstName("User3")
                                .lastName("Test3")
                                .email("user3@example.com")
                                .status(UserStatus.INACTIVE)
                                .address(new Address("789 Pine Rd", "Marseille", "13000", "France"))
                                .build();
                userRepository.insert(user3, "firstName", "lastName", "email", "status", "address");

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

        @Test
        void testFindByExternalId() {
                // Given - Create a user with external ID
                UUID externalId = UUID.randomUUID();
                User user = User.builder()
                                .firstName("UUIDTest")
                                .lastName("User")
                                .email("uuid@example.com")
                                .externalId(externalId)
                                .status(UserStatus.ACTIVE)
                                .build();
                userRepository.insert(user, "firstName", "lastName", "email", "externalId", "status");

                // When - Find by external ID
                User found = userRepository.findByExternalId(externalId, "id", "firstName", "lastName", "email",
                                "externalId", "status");

                // Then
                assertThat(found).isNotNull();
                assertThat(found.getExternalId()).isEqualTo(externalId);
                assertThat(found.getEmail()).isEqualTo("uuid@example.com");
                assertThat(found.getFirstName()).isEqualTo("UUIDTest");
                assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        void testInsertFieldWithNull() {
                // Given - Create a user with some fields explicitly set to null
                User user = User.builder()
                                .firstName("TestUser")
                                .lastName(null) // Explicitly null
                                .email("insertwithnull@example.com")
                                .status(UserStatus.ACTIVE)
                                .address(null) // Explicitly null
                                .build();

                // When - Insert with null fields
                userRepository.insert(user, "firstName", "lastName", "email", "status", "address");

                // Then - Verify the insert worked and null values are preserved
                assertThat(user.getId()).isNotNull();

                User found = userRepository.findByEmail("insertwithnull@example.com", "id", "firstName", "lastName", "email",
                                "status", "address");
                assertThat(found).isNotNull();
                assertThat(found.getFirstName()).isEqualTo("TestUser");
                assertThat(found.getLastName()).isNull();
                assertThat(found.getEmail()).isEqualTo("insertwithnull@example.com");
                assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
                assertThat(found.getAddress()).isNull();
        }

        @Test
        void testUpdatePointField() throws SQLException {
                // Given - Create a user with a position (Point)
                Point initialPoint = new Point("POINT(2.5 48.5)");
                User user = User.builder()
                                .firstName("PointUser")
                                .lastName("Geo")
                                .email("pointupdate@example.com")
                                .status(UserStatus.ACTIVE)
                                .position(initialPoint)
                                .build();
                userRepository.insert(user, "firstName", "lastName", "email", "status", "position");

                User found = userRepository.findByEmail("pointupdate@example.com", "id", "firstName", "position");
                assertThat(found).isNotNull();
                assertThat(found.getPosition()).isNotNull();
                assertThat(found.getPosition().x).isCloseTo(2.5, within(0.0001));
                assertThat(found.getPosition().y).isCloseTo(48.5, within(0.0001));

                // When - Update the position to a different location
                Point newPoint = new Point("POINT(4.8 45.7)");
                found.setPosition(newPoint);

                int rows = userRepository.update(found, "position");

                // Then - Verify the update worked
                assertThat(rows).isEqualTo(1);

                User updated = userRepository.findByEmail("pointupdate@example.com", "id", "firstName", "position");
                assertThat(updated).isNotNull();
                assertThat(updated.getPosition()).isNotNull();
                assertThat(updated.getPosition().x).isCloseTo(4.8, within(0.0001));
                assertThat(updated.getPosition().y).isCloseTo(45.7, within(0.0001));
        }

        @Test
        void testUpdateEnumField() {
                // Given - Create a user with ACTIVE status
                User user = User.builder()
                                .firstName("EnumTest")
                                .lastName("Status")
                                .email("enumupdate@example.com")
                                .status(UserStatus.ACTIVE)
                                .build();
                userRepository.insert(user, "firstName", "lastName", "email", "status");

                User found = userRepository.findByEmail("enumupdate@example.com", "id", "firstName", "lastName", "status");
                assertThat(found).isNotNull();
                assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);

                // When - Update status to SUSPENDED
                found.setStatus(UserStatus.SUSPENDED);

                int rows = userRepository.update(found, "status");

                // Then - Verify the enum update worked
                assertThat(rows).isEqualTo(1);

                User updated = userRepository.findByEmail("enumupdate@example.com", "id", "status");
                assertThat(updated).isNotNull();
                assertThat(updated.getStatus()).isEqualTo(UserStatus.SUSPENDED);

                // When - Update status again to INACTIVE
                updated.setStatus(UserStatus.INACTIVE);

                rows = userRepository.update(updated, "status");

                // Then - Verify the second enum update worked
                assertThat(rows).isEqualTo(1);

                User finalUser = userRepository.findByEmail("enumupdate@example.com", "id", "status");
                assertThat(finalUser).isNotNull();
                assertThat(finalUser.getStatus()).isEqualTo(UserStatus.INACTIVE);
        }

        @Test
        void testUpdateFieldToNull() {
                // Given - Create a user with firstName and address
                User user = User.builder()
                                .firstName("OriginalName")
                                .lastName("LastName")
                                .email("nulltest@example.com")
                                .status(UserStatus.ACTIVE)
                                .address(new Address("123 Main St", "Paris", "75001", "France"))
                                .build();
                userRepository.insert(user, "firstName", "lastName", "email", "status", "address");

                User found = userRepository.findByEmail("nulltest@example.com", "id", "firstName", "lastName", "email",
                                "status", "address");
                assertThat(found).isNotNull();
                assertThat(found.getFirstName()).isEqualTo("OriginalName");
                assertThat(found.getAddress()).isNotNull();

                // When - Update firstName and address to null
                found.setFirstName(null);
                found.setAddress(null);

                int rows = userRepository.update(found, "firstName", "address");

                // Then - Verify the update worked
                assertThat(rows).isEqualTo(1);

                User updated = userRepository.findByEmail("nulltest@example.com", "id", "firstName", "lastName", "email",
                                "status", "address");
                assertThat(updated).isNotNull();
                assertThat(updated.getFirstName()).isNull();
                assertThat(updated.getAddress()).isNull();
                assertThat(updated.getLastName()).isEqualTo("LastName"); // Unchanged
                assertThat(updated.getStatus()).isEqualTo(UserStatus.ACTIVE); // Unchanged
        }

        @Test
        void testGetUsersReportAroundPoint() throws SQLException {
                // Given - Create users at different geographic locations
                Point parisCenter = new Point("POINT(2.3522 48.8566)"); // Paris center

                User user1 = User.builder()
                                .firstName("NearUser1")
                                .email("near1@example.com")
                                .status(UserStatus.ACTIVE)
                                .position(new Point("POINT(2.3525 48.8570)")) // ~500m from Paris center
                                .preferences(Preferences.builder().language("fr").theme("dark").notifications(true)
                                                .build())
                                .build();
                userRepository.insert(user1, "firstName", "email", "status", "position", "preferences");

                User user2 = User.builder()
                                .firstName("NearUser2")
                                .email("near2@example.com")
                                .status(UserStatus.ACTIVE)
                                .position(new Point("POINT(2.3530 48.8575)")) // ~800m from Paris center
                                .preferences(Preferences.builder().language("fr").theme("light").notifications(false)
                                                .build())
                                .build();
                userRepository.insert(user2, "firstName", "email", "status", "position", "preferences");

                User user3 = User.builder()
                                .firstName("FarUser")
                                .email("far@example.com")
                                .status(UserStatus.ACTIVE)
                                .position(new Point("POINT(2.3880 48.8800)")) // ~5km from Paris center
                                .preferences(Preferences.builder().language("en").theme("auto").notifications(true)
                                                .build())
                                .build();
                userRepository.insert(user3, "firstName", "email", "status", "position", "preferences");

                // Add email contact for one of the near users
                User foundUser1 = userRepository.findByEmail("near1@example.com", "id");
                ContactInfo contact1 = ContactInfo.builder()
                                .userId(foundUser1.getId())
                                .contactType(ContactType.EMAIL)
                                .contactValue("near1@work.com")
                                .build();
                contactInfoRepository.insert(contact1, "userId", "contactType", "contactValue");

                // When - Get report for users within 10km of Paris center
                UserReport report = userRepository.getUsersReportAroundPoint(parisCenter);

                // Then - Verify all three users are within 10km
                assertThat(report).isNotNull();
                assertThat(report.getTotalUsers()).isEqualTo(3);
                assertThat(report.getUsersWithEmailContact()).isEqualTo(1);
                assertThat(report.getUsersWithFrenchPreference()).isEqualTo(2);
        }
}