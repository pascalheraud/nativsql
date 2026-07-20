package ovh.heraud.nativsql.repository.mariadb;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import ovh.heraud.nativsql.domain.mariadb.User;
import ovh.heraud.nativsql.domain.mariadb.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for exists() / ExistsQuery on MariaDBUserRepository.
 */
@Import({ MariaDBUserRepository.class })
class MariaDBExistsQueryTest extends MariaDBRepositoryTest {

    @Autowired
    private MariaDBUserRepository userRepository;

    @Test
    void existsAny_onNonEmptyTable_returnsTrue() {
        // Given
        User user = User.builder()
                .firstName("Alice")
                .email("alice-exists@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.insert(user, "firstName", "email", "status");

        // When
        boolean exists = userRepository.existsAny();

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
        userRepository.insert(user, "firstName", "email", "status");

        // When
        boolean exists = userRepository.existsByProperty(User::getEmail, "exists-getter@example.com");

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
        userRepository.insert(user, "firstName", "email", "status");

        // When
        boolean exists = userRepository.existsByProperty("email", "exists-string@example.com");

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void existsByProperty_noMatchingRows_returnsFalse() {
        // When
        boolean exists = userRepository.existsByProperty("email", "unknown@x.com");

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
        userRepository.insert(active, "firstName", "email", "status");

        // When
        boolean exists = userRepository.existsByStatuses(List.of(UserStatus.ACTIVE, UserStatus.SUSPENDED));

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void existsByStatuses_nonMatchingFixture_returnsFalse() {
        // When
        boolean exists = userRepository.existsByStatuses(List.of(UserStatus.INACTIVE));

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
        userRepository.insert(user, "firstName", "email", "status");

        // When
        boolean exists = userRepository.existsByEmailAndStatus("charlie-exists@example.com", UserStatus.ACTIVE);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void existsByEmailAndStatus_twoConditions_nonMatchingFixture_returnsFalse() {
        // When
        boolean exists = userRepository.existsByEmailAndStatus("nobody-exists@example.com", UserStatus.ACTIVE);

        // Then
        assertThat(exists).isFalse();
    }
}
