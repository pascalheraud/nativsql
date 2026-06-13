package ovh.heraud.nativsql.repository.postgres;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import ovh.heraud.nativsql.domain.postgres.User;
import ovh.heraud.nativsql.domain.postgres.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the new WHERE operator methods against a real PostgreSQL database.
 * Covers: LESS_THAN, NOT_EQUALS, LIKE, IS_NULL, BETWEEN.
 */
@Import({ PostgresUserRepository.class })
class PostgresOperatorsTest extends PostgresRepositoryTest {

    @Autowired
    private PostgresUserRepository userRepository;

    @Test
    void less_than_returns_only_users_below_threshold() {
        // Given: three users with different ages
        insertUser("Young", "young-lt@example.com", UserStatus.ACTIVE, 20);
        insertUser("Middle", "middle-lt@example.com", UserStatus.ACTIVE, 30);
        insertUser("Senior", "senior-lt@example.com", UserStatus.ACTIVE, 50);

        // When: querying age < 30
        List<User> result = userRepository.findAllByAgeLessThan(30, "id", "firstName", "age");

        // Then: only the youngest user is returned
        List<String> firstNames = result.stream().map(User::getFirstName).toList();
        assertThat(firstNames).contains("Young");
        assertThat(firstNames).doesNotContain("Middle", "Senior");
    }

    @Test
    void not_equals_returns_only_users_with_different_status() {
        // Given: one ACTIVE user and one INACTIVE user
        insertUser("ActiveUser", "active-ne@example.com", UserStatus.ACTIVE, null);
        insertUser("InactiveUser", "inactive-ne@example.com", UserStatus.INACTIVE, null);

        // When: querying status <> ACTIVE
        List<User> result = userRepository.findAllByStatusNotEquals(UserStatus.ACTIVE, "id", "firstName", "email", "status");

        // Then: only the INACTIVE user is returned
        List<String> emails = result.stream().map(User::getEmail).toList();
        assertThat(emails).contains("inactive-ne@example.com");
        assertThat(emails).doesNotContain("active-ne@example.com");
    }

    @Test
    void like_returns_only_users_matching_pattern() {
        // Given: users with different first names
        insertUser("Alice", "alice-like@example.com", UserStatus.ACTIVE, null);
        insertUser("Bob", "bob-like@example.com", UserStatus.ACTIVE, null);
        insertUser("Albert", "albert-like@example.com", UserStatus.ACTIVE, null);

        // When: querying firstName LIKE 'Al%'
        List<User> result = userRepository.findAllByFirstNameLike("Al%", "id", "firstName");

        // Then: only names starting with 'Al' are returned
        List<String> firstNames = result.stream().map(User::getFirstName).toList();
        assertThat(firstNames).contains("Alice", "Albert");
        assertThat(firstNames).doesNotContain("Bob");
    }

    @Test
    void is_null_returns_only_users_without_group() {
        // Given: one user without a group (groupId = null)
        insertUser("NoGroup", "nogroup-null@example.com", UserStatus.ACTIVE, null);

        // When: querying groupId IS NULL
        List<User> result = userRepository.findAllWithNullGroupId("id", "firstName", "email", "groupId");

        // Then: the user without a group is in the result
        List<String> emails = result.stream().map(User::getEmail).toList();
        assertThat(emails).contains("nogroup-null@example.com");
    }

    @Test
    void between_returns_only_users_within_age_range() {
        // Given: users with ages 18, 25, 40, 60
        insertUser("Teen", "teen-between@example.com", UserStatus.ACTIVE, 18);
        insertUser("Young", "young-between@example.com", UserStatus.ACTIVE, 25);
        insertUser("Adult", "adult-between@example.com", UserStatus.ACTIVE, 40);
        insertUser("Senior", "senior-between@example.com", UserStatus.ACTIVE, 60);

        // When: querying age BETWEEN 20 AND 45
        List<User> result = userRepository.findAllByAgeBetween(20, 45, "id", "firstName", "age");

        // Then: only users with age 25 and 40 are returned
        List<String> firstNames = result.stream().map(User::getFirstName).toList();
        assertThat(firstNames).contains("Young", "Adult");
        assertThat(firstNames).doesNotContain("Teen", "Senior");
    }

    private void insertUser(String firstName, String email, UserStatus status, Integer age) {
        User user = User.builder()
                .firstName(firstName)
                .email(email)
                .status(status)
                .age(age)
                .build();
        userRepository.insert(user, "firstName", "email", "status", "age");
    }
}
