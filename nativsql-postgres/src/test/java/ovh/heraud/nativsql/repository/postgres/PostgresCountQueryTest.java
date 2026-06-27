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

@Import({ PostgresUserRepository.class })
class PostgresCountQueryTest extends PostgresRepositoryTest {

    @Autowired
    private PostgresUserRepository userRepository;

    @Test
    void countAll_returnsTotalRowCount() {
        // Given
        long before = userRepository.countAll();
        User user = User.builder()
                .firstName("Alice")
                .email("alice-count@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.insert(user, "firstName", "email", "status");

        // When
        long after = userRepository.countAll();

        // Then
        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    void countByProperty_withGetterReference_returnsMatchingRowCount() {
        // Given
        for (int i = 1; i <= 3; i++) {
            User user = User.builder()
                    .firstName("ActiveUser" + i)
                    .email("active-count-" + i + "@example.com")
                    .status(UserStatus.ACTIVE)
                    .build();
            userRepository.insert(user, "firstName", "email", "status");
        }

        // When
        long count = userRepository.countByProperty(User::getEmail, "active-count-1@example.com");

        // Then
        assertThat(count).isEqualTo(1);
    }

    @Test
    void countByProperty_withStringProperty_returnsMatchingRowCount() {
        // Given
        for (int i = 1; i <= 3; i++) {
            User user = User.builder()
                    .firstName("StatusUser" + i)
                    .email("status-count-" + i + "@example.com")
                    .status(UserStatus.SUSPENDED)
                    .build();
            userRepository.insert(user, "firstName", "email", "status");
        }

        // When
        long count = userRepository.countByProperty("email", "status-count-2@example.com");

        // Then
        assertThat(count).isEqualTo(1);
    }

    @Test
    void countByProperty_noMatchingRows_returnsZero() {
        // When
        long count = userRepository.countByProperty("email", "unknown@x.com");

        // Then
        assertThat(count).isZero();
    }

    @Test
    void count_withCountQuery_whereAndIn_returnsMatchingRowCount() {
        // Given
        User active = User.builder()
                .firstName("Active")
                .email("active-in-count@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.insert(active, "firstName", "email", "status");

        User suspended = User.builder()
                .firstName("Suspended")
                .email("suspended-in-count@example.com")
                .status(UserStatus.SUSPENDED)
                .build();
        userRepository.insert(suspended, "firstName", "email", "status");

        User inactive = User.builder()
                .firstName("Inactive")
                .email("inactive-in-count@example.com")
                .status(UserStatus.INACTIVE)
                .build();
        userRepository.insert(inactive, "firstName", "email", "status");

        // When
        long count = userRepository.countByStatuses(List.of(UserStatus.ACTIVE, UserStatus.SUSPENDED));

        // Then
        assertThat(count).isEqualTo(2);
    }

    @Test
    void count_withCountQuery_twoConditions_returnsMatchingRowCount() {
        // Given
        User user = User.builder()
                .firstName("Charlie")
                .email("charlie-count@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.insert(user, "firstName", "email", "status");

        // When
        long count = userRepository.countByEmailAndStatus("charlie-count@example.com", UserStatus.ACTIVE);

        // Then
        assertThat(count).isEqualTo(1);
    }

    @Test
    void countByProperty_onNonDeterministicEncryptedColumn_throwsNativSQLException() {
        // When / Then
        assertThatThrownBy(() -> userRepository.countByProperty("lastName", "Doe"))
                .isInstanceOf(NativSQLException.class);
    }
}
