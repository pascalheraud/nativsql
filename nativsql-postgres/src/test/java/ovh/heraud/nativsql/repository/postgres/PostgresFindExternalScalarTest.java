package ovh.heraud.nativsql.repository.postgres;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import ovh.heraud.nativsql.domain.postgres.User;
import ovh.heraud.nativsql.domain.postgres.UserStatus;
import ovh.heraud.nativsql.exception.NativSQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@code findExternal}/{@code findAllExternal} mapping
 * directly into a base/scalar result class ({@link Long}/{@link String}) when
 * the query returns exactly one column (issue #111).
 */
@Import(PostgresUserRepository.class)
class PostgresFindExternalScalarTest extends PostgresRepositoryTest {

    @Autowired
    private PostgresUserRepository userRepository;

    @Test
    void findMostRecentUserId_maps_single_column_query_to_long() {
        // Given: two users inserted, the second one created last
        userRepository.insert(
                User.builder().firstName("Alice").email("alice-scalar@example.com").status(UserStatus.ACTIVE)
                        .build(),
                "firstName", "email", "status");
        User bob = User.builder().firstName("Bob").email("bob-scalar@example.com").status(UserStatus.ACTIVE)
                .build();
        userRepository.insert(bob, "firstName", "email", "status");

        // When: findExternal maps a single-column aggregation query directly to Long
        Long mostRecentId = userRepository.findMostRecentUserId();

        // Then: the most recently created user's id is returned
        assertThat(mostRecentId).isEqualTo(bob.getId());
    }

    @Test
    void findDistinctEmailDomains_maps_single_column_rows_to_list_of_string() {
        // Given: users with two distinct email domains
        userRepository.insert(
                User.builder().firstName("Carol").email("carol-scalar@example.com").status(UserStatus.ACTIVE)
                        .build(),
                "firstName", "email", "status");
        userRepository.insert(
                User.builder().firstName("Dan").email("dan-scalar@other-domain.com").status(UserStatus.ACTIVE)
                        .build(),
                "firstName", "email", "status");

        // When: findAllExternal maps each single-column row directly to String
        List<String> domains = userRepository.findDistinctEmailDomains();

        // Then: both distinct domains are present
        assertThat(domains).contains("example.com", "other-domain.com");
    }

    @Test
    void findExternal_with_scalar_result_class_and_two_columns_throws() {
        // Given: a user inserted, so the 2-column query returns a row
        userRepository.insert(
                User.builder().firstName("Dave").email("dave-scalar@example.com").status(UserStatus.ACTIVE)
                        .build(),
                "firstName", "email", "status");

        // When / Then: a 2-column query with a scalar (Long) result class is ambiguous
        assertThatThrownBy(() -> userRepository.findFirstUserIdAndEmail())
                .isInstanceOf(NativSQLException.class);
    }
}
