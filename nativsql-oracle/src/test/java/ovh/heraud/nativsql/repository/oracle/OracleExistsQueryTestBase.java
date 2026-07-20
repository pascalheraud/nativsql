package ovh.heraud.nativsql.repository.oracle;

import java.util.List;

import org.junit.jupiter.api.Test;
import ovh.heraud.nativsql.domain.oracle.User;
import ovh.heraud.nativsql.domain.oracle.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared test bodies for exists() / ExistsQuery against Oracle, run against every
 * supported Oracle version (see {@link OracleExistsQueryTest} for 23 and
 * {@link OracleExistsQueryTest20} for 20). Confirms the generated
 * "SELECT CASE WHEN EXISTS(...) THEN 1 ELSE 0 END FROM dual" statement round-trips
 * correctly through {@code extractExistsResult} for both true and false.
 */
abstract class OracleExistsQueryTestBase extends OracleRepositoryTest {

    /**
     * Subclasses must declare their own {@code @Autowired} field for this
     * repository (not inherit one from this base class): the test harness's
     * {@code injectDataSourceToRepositories} only scans fields declared
     * directly on the runtime test class, mirroring the pattern used by
     * {@link ovh.heraud.nativsql.repository.IDataTypeTests} implementors.
     */
    protected abstract OracleUserRepository getUserRepository();

    @Test
    void existsAny_onNonEmptyTable_returnsTrue() {
        // Given
        User user = User.builder()
                .firstName("Alice")
                .email("alice-exists@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        getUserRepository().insert(user, "firstName", "email", "status");

        // When
        boolean exists = getUserRepository().existsAny();

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void existsByProperty_withGetterReference_matchingRow_returnsTrue() {
        // Given
        User user = User.builder()
                .firstName("ExistsUser")
                .email("exists-getter@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        getUserRepository().insert(user, "firstName", "email", "status");

        // When
        boolean exists = getUserRepository().existsByProperty(User::getEmail, "exists-getter@example.com");

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void existsByProperty_withStringProperty_matchingRow_returnsTrue() {
        // Given
        User user = User.builder()
                .firstName("ExistsUser2")
                .email("exists-string@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        getUserRepository().insert(user, "firstName", "email", "status");

        // When
        boolean exists = getUserRepository().existsByProperty("email", "exists-string@example.com");

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void existsByProperty_noMatchingRows_returnsFalse() {
        // When
        boolean exists = getUserRepository().existsByProperty("email", "unknown@x.com");

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void existsByStatuses_matchingFixture_returnsTrue() {
        // Given
        User active = User.builder()
                .firstName("Active")
                .email("active-in-exists@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        getUserRepository().insert(active, "firstName", "email", "status");

        // When
        boolean exists = getUserRepository().existsByStatuses(List.of(UserStatus.ACTIVE, UserStatus.SUSPENDED));

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void existsByStatuses_nonMatchingFixture_returnsFalse() {
        // When
        boolean exists = getUserRepository().existsByStatuses(List.of(UserStatus.INACTIVE));

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void existsByEmailAndStatus_twoConditions_matchingFixture_returnsTrue() {
        // Given
        User user = User.builder()
                .firstName("Charlie")
                .email("charlie-exists@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        getUserRepository().insert(user, "firstName", "email", "status");

        // When
        boolean exists = getUserRepository().existsByEmailAndStatus("charlie-exists@example.com", UserStatus.ACTIVE);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void existsByEmailAndStatus_twoConditions_nonMatchingFixture_returnsFalse() {
        // When
        boolean exists = getUserRepository().existsByEmailAndStatus("nobody-exists@example.com", UserStatus.ACTIVE);

        // Then
        assertThat(exists).isFalse();
    }
}
