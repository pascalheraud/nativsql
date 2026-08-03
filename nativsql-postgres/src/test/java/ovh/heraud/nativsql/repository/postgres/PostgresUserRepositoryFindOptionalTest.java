package ovh.heraud.nativsql.repository.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import ovh.heraud.nativsql.domain.postgres.User;
import ovh.heraud.nativsql.domain.postgres.UserStatus;

/**
 * Integration tests for {@code findOptional*} methods added to
 * {@link ovh.heraud.nativsql.repository.GenericRepository}, exercised through
 * {@link PostgresUserRepository}.
 */
@Import(PostgresUserRepository.class)
class PostgresUserRepositoryFindOptionalTest extends PostgresRepositoryTest {

    @Autowired
    private PostgresUserRepository userRepository;

    @Test
    void findOptionalById_returns_present_optional_when_user_exists() {
        // Given: an inserted user
        User user = User.builder()
                .firstName("Olivia")
                .lastName("Optional")
                .email("olivia.optional@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        // When: looking it up via findOptionalById
        Optional<User> found = userRepository.findOptionalById(user.getId(), "email");

        // Then: the optional is present and contains the expected user
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("olivia.optional@example.com");
    }

    @Test
    void findOptionalById_returns_empty_optional_when_user_does_not_exist() {
        // When: looking up an id that was never inserted
        Optional<User> found = userRepository.findOptionalById(-1L, "email");

        // Then: the optional is empty, not null
        assertThat(found).isEmpty();
    }

    @Test
    void findOptionalByEmail_returns_present_optional_when_user_exists() {
        // Given: an inserted user
        User user = User.builder()
                .firstName("Oscar")
                .lastName("Optional")
                .email("oscar.optional@example.com")
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        // When: looking it up via findOptionalByEmail
        Optional<User> found = userRepository.findOptionalByEmail("oscar.optional@example.com", "id", "firstName");

        // Then: the optional is present and contains the expected user
        assertThat(found).isPresent();
        assertThat(found.get().getFirstName()).isEqualTo("Oscar");
    }

    @Test
    void findOptionalByEmail_returns_empty_optional_when_email_does_not_exist() {
        // When: looking up an email that was never inserted
        Optional<User> found = userRepository.findOptionalByEmail("nobody.optional@example.com", "id");

        // Then: the optional is empty, not null
        assertThat(found).isEmpty();
    }
}
