package ovh.heraud.nativsql.repository.postgres;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import ovh.heraud.nativsql.domain.postgres.ContactInfo;
import ovh.heraud.nativsql.domain.postgres.ContactType;
import ovh.heraud.nativsql.domain.postgres.Group;
import ovh.heraud.nativsql.domain.postgres.User;
import ovh.heraud.nativsql.domain.postgres.UserActivityReport;
import ovh.heraud.nativsql.domain.postgres.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@code FindQuery.selectExpression(...)} and
 * {@code GenericRepository.find/findAll(query, resultClass)} against
 * PostgreSQL, mapping a query into {@link UserActivityReport}, a class that
 * extends {@link User} with extra computed fields (issue #98).
 */
@Import({ PostgresUserRepository.class, PostgresContactInfoRepository.class, PostgresGroupRepository.class })
class PostgresUserActivityReportTest extends PostgresRepositoryTest {

    @Autowired
    private PostgresUserRepository userRepository;

    @Autowired
    private PostgresContactInfoRepository contactInfoRepository;

    @Autowired
    private PostgresGroupRepository groupRepository;

    @Test
    void findUserActivityReports_maps_inherited_fields_and_computed_subquery() {
        // Given: a user with 3 contacts
        User user = User.builder()
                .firstName("Alice")
                .email("alice-activity@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.insert(user, "firstName", "email", "status");

        contactInfoRepository.insert(
                ContactInfo.builder().userId(user.getId()).contactType(ContactType.EMAIL)
                        .contactValue("a@example.com").build(),
                "userId", "contactType", "contactValue");
        contactInfoRepository.insert(
                ContactInfo.builder().userId(user.getId()).contactType(ContactType.PHONE)
                        .contactValue("0102030405").build(),
                "userId", "contactType", "contactValue");
        contactInfoRepository.insert(
                ContactInfo.builder().userId(user.getId()).contactType(ContactType.LINKEDIN)
                        .contactValue("linkedin/alice").build(),
                "userId", "contactType", "contactValue");

        // When
        List<UserActivityReport> reports = userRepository.findUserActivityReports("id", "firstName", "email");

        // Then: inherited fields and the computed contact count are both populated
        UserActivityReport report = reports.stream()
                .filter(r -> r.getId().equals(user.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(report.getFirstName()).isEqualTo("Alice");
        assertThat(report.getEmail()).isEqualTo("alice-activity@example.com");
        assertThat(report.getContactCount()).isEqualTo(3L);
    }

    @Test
    void findUserActivityReports_computed_subquery_returns_zero_for_no_related_rows() {
        // Given: a user with zero contacts
        User user = User.builder()
                .firstName("Bob")
                .email("bob-activity@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.insert(user, "firstName", "email", "status");

        // When
        List<UserActivityReport> reports = userRepository.findUserActivityReports("id", "firstName", "email");

        // Then: COUNT(*) naturally returns 0, no COALESCE needed
        UserActivityReport report = reports.stream()
                .filter(r -> r.getId().equals(user.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(report.getContactCount()).isEqualTo(0L);
    }

    @Test
    void findUserActivityReportsWithGroupAndContacts_combines_join_association_and_expression() {
        // Given: a user with a group and 2 contacts
        Group group = Group.builder().name("Engineering").build();
        groupRepository.insert(group, "name");

        User user = User.builder()
                .firstName("Carol")
                .email("carol-activity@example.com")
                .status(UserStatus.ACTIVE)
                .groupId(group.getId())
                .build();
        userRepository.insert(user, "firstName", "email", "status", "groupId");

        contactInfoRepository.insert(
                ContactInfo.builder().userId(user.getId()).contactType(ContactType.EMAIL)
                        .contactValue("carol@example.com").build(),
                "userId", "contactType", "contactValue");
        contactInfoRepository.insert(
                ContactInfo.builder().userId(user.getId()).contactType(ContactType.PHONE)
                        .contactValue("0102030406").build(),
                "userId", "contactType", "contactValue");

        // When
        List<UserActivityReport> reports = userRepository.findUserActivityReportsWithGroupAndContacts(
                new String[] { "id", "name" }, new String[] { "id", "contactType" }, "id", "firstName", "email");

        // Then: group populated (join), contacts populated (association), contactCount correct
        UserActivityReport report = reports.stream()
                .filter(r -> r.getId().equals(user.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(report.getGroup()).isNotNull();
        assertThat(report.getGroup().getName()).isEqualTo("Engineering");
        assertThat(report.getContacts()).isNullOrEmpty();
        assertThat(report.getContactCount()).isEqualTo(2L);
    }

    @Test
    void findUserActivityReportWithGroupAndContacts_singular_variant_loads_associations() {
        // Given: same setup as above
        Group group = Group.builder().name("Sales").build();
        groupRepository.insert(group, "name");

        User user = User.builder()
                .firstName("Dave")
                .email("dave-activity@example.com")
                .status(UserStatus.ACTIVE)
                .groupId(group.getId())
                .build();
        userRepository.insert(user, "firstName", "email", "status", "groupId");

        contactInfoRepository.insert(
                ContactInfo.builder().userId(user.getId()).contactType(ContactType.EMAIL)
                        .contactValue("dave@example.com").build(),
                "userId", "contactType", "contactValue");
        contactInfoRepository.insert(
                ContactInfo.builder().userId(user.getId()).contactType(ContactType.PHONE)
                        .contactValue("0102030407").build(),
                "userId", "contactType", "contactValue");

        // When: the non-list variant, which DOES batch-load associations
        UserActivityReport report = userRepository.findUserActivityReportWithGroupAndContacts(
                user.getId(), new String[] { "id", "name" }, new String[] { "id", "contactType" }, "id", "firstName", "email");

        // Then
        assertThat(report).isNotNull();
        assertThat(report.getGroup().getName()).isEqualTo("Sales");
        assertThat(report.getContacts()).hasSize(2);
        assertThat(report.getContactCount()).isEqualTo(2L);
    }
}
