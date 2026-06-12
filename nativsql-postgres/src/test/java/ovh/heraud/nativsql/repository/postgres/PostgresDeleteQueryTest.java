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
class PostgresDeleteQueryTest extends PostgresRepositoryTest {

    @Autowired
    private PostgresUserRepository userRepository;

    @Test
    void deleteByProperty_deletesExactlyOneRow_byColumnString() {
        // Given
        User user = User.builder()
                .firstName("Alice")
                .email("alice-del@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.insert(user, "firstName", "email", "status");

        // When
        userRepository.deleteByProperty("email", "alice-del@example.com");

        // Then
        User found = userRepository.findByEmail("alice-del@example.com", "id");
        assertThat(found).isNull();
    }

    @Test
    void deleteByProperty_deletesExactlyOneRow_byGetterReference() {
        // Given
        User user = User.builder()
                .firstName("Bob")
                .email("bob-del@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.insert(user, "firstName", "email", "status");

        // When
        userRepository.deleteByProperty(User::getEmail, "bob-del@example.com");

        // Then
        User found = userRepository.findByEmail("bob-del@example.com", "id");
        assertThat(found).isNull();
    }

    @Test
    void deleteByProperty_throwsNativSQLException_whenZeroRowsMatch() {
        // When / Then
        assertThatThrownBy(() -> userRepository.deleteByProperty("email", "nobody@example.com"))
                .isInstanceOf(NativSQLException.class);
    }

    @Test
    void deleteByProperty_throwsNativSQLException_whenMultipleRowsMatch() {
        // Given — two users with same status
        User user1 = User.builder()
                .firstName("User1")
                .email("multi-del-1@example.com")
                .status(UserStatus.SUSPENDED)
                .build();
        userRepository.insert(user1, "firstName", "email", "status");

        User user2 = User.builder()
                .firstName("User2")
                .email("multi-del-2@example.com")
                .status(UserStatus.SUSPENDED)
                .build();
        userRepository.insert(user2, "firstName", "email", "status");

        // When / Then
        assertThatThrownBy(() -> userRepository.deleteByProperty("status", UserStatus.SUSPENDED))
                .isInstanceOf(NativSQLException.class);
    }

    @Test
    void delete_withDeleteQuery_twoConditions_deletesMatchingRow() {
        // Given
        User user = User.builder()
                .firstName("Charlie")
                .email("charlie-del@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.insert(user, "firstName", "email", "status");

        // When
        userRepository.deleteByEmailAndStatus("charlie-del@example.com", UserStatus.ACTIVE);

        // Then
        User found = userRepository.findByEmail("charlie-del@example.com", "id");
        assertThat(found).isNull();
    }

    @Test
    void deleteAllByProperty_zeroRows_noException() {
        // When / Then — must not throw
        userRepository.deleteAllByProperty("email", "ghost@example.com");
    }

    @Test
    void deleteAllByProperty_deletesAllMatchingRows() {
        // Given — insert 3 users with INACTIVE status
        for (int i = 1; i <= 3; i++) {
            User user = User.builder()
                    .firstName("InactiveUser" + i)
                    .email("inactive-del-" + i + "@example.com")
                    .status(UserStatus.INACTIVE)
                    .build();
            userRepository.insert(user, "firstName", "email", "status");
        }

        // When
        userRepository.deleteAllByProperty(User::getStatus, UserStatus.INACTIVE);

        // Then
        for (int i = 1; i <= 3; i++) {
            User found = userRepository.findByEmail("inactive-del-" + i + "@example.com", "id");
            assertThat(found).isNull();
        }
    }

    @Test
    void deleteAll_withDeleteQuery_whereAndIn_deletesMatchingRows() {
        // Given — insert users with different statuses
        User active = User.builder()
                .firstName("Active")
                .email("active-in@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.insert(active, "firstName", "email", "status");

        User suspended = User.builder()
                .firstName("Suspended")
                .email("suspended-in@example.com")
                .status(UserStatus.SUSPENDED)
                .build();
        userRepository.insert(suspended, "firstName", "email", "status");

        User inactive = User.builder()
                .firstName("Inactive")
                .email("inactive-in@example.com")
                .status(UserStatus.INACTIVE)
                .build();
        userRepository.insert(inactive, "firstName", "email", "status");

        // When — delete ACTIVE and SUSPENDED but not INACTIVE
        userRepository.deleteAllByStatuses(List.of(UserStatus.ACTIVE, UserStatus.SUSPENDED));

        // Then
        assertThat(userRepository.findByEmail("active-in@example.com", "id")).isNull();
        assertThat(userRepository.findByEmail("suspended-in@example.com", "id")).isNull();
        assertThat(userRepository.findByEmail("inactive-in@example.com", "id")).isNotNull();
    }
}
