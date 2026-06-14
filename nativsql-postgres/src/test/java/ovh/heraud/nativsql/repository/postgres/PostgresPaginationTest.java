package ovh.heraud.nativsql.repository.postgres;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import ovh.heraud.nativsql.domain.postgres.User;
import ovh.heraud.nativsql.domain.postgres.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for limit/offset pagination on FindQuery against a real PostgreSQL database.
 */
@Import({ PostgresUserRepository.class })
class PostgresPaginationTest extends PostgresRepositoryTest {

    @Autowired
    private PostgresUserRepository userRepository;

    @Test
    void find_with_limit_and_offset_returns_correct_page() {
        // Given: 10 users inserted with predictable emails for ordering
        for (int i = 1; i <= 10; i++) {
            insertUser("User" + i, "page-user-" + i + "@example.com", UserStatus.ACTIVE);
        }

        // When: fetching page 2 (rows 4–6) ordered by id, limit 3 offset 3
        List<Long> allIds = userRepository.findPageByOrderById(10, 0, "id")
                .stream().map(User::getId).toList();
        long expectedId4 = allIds.get(3);
        long expectedId5 = allIds.get(4);
        long expectedId6 = allIds.get(5);

        List<User> page = userRepository.findPageByOrderById(3, 3, "id");

        // Then: exactly 3 rows are returned, matching ids 4–6 in insertion order
        assertThat(page).hasSize(3);
        List<Long> pageIds = page.stream().map(User::getId).toList();
        assertThat(pageIds).containsExactly(expectedId4, expectedId5, expectedId6);
    }

    @Test
    void find_with_limit_only_returns_first_n_rows() {
        // Given: 5 users inserted
        for (int i = 1; i <= 5; i++) {
            insertUser("LimitUser" + i, "limit-user-" + i + "@example.com", UserStatus.ACTIVE);
        }

        // When: fetching with limit 2 and no offset
        List<User> result = userRepository.findPageByOrderById(2, 0, "id");

        // Then: exactly 2 rows are returned
        assertThat(result).hasSize(2);
    }

    private void insertUser(String firstName, String email, UserStatus status) {
        User user = User.builder()
                .firstName(firstName)
                .email(email)
                .status(status)
                .build();
        userRepository.insert(user, "firstName", "email", "status");
    }
}
