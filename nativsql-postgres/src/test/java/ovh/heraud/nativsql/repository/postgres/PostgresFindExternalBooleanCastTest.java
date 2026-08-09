package ovh.heraud.nativsql.repository.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import ovh.heraud.nativsql.domain.postgres.User;
import ovh.heraud.nativsql.domain.postgres.UserStatus;
import ovh.heraud.nativsql.repository.NullableParam;

/**
 * Integration test reproducing the original StackOverflow bug via
 * {@code findExternal}/{@code findAllExternal}: a hand-written query with an
 * ambiguous boolean predicate ({@code :filterActive IS NULL OR ...}) fails
 * with {@code PSQLException: could not determine data type of parameter}
 * unless {@code NamedParamSqlCaster} injects an explicit cast (issue #118).
 */
@Import({ PostgresUserRepository.class })
class PostgresFindExternalBooleanCastTest extends PostgresRepositoryTest {

    @Autowired
    private PostgresUserRepository userRepository;

    @Test
    void findAllByActiveFlagAmbiguous_executes_and_matches_for_null_true_and_false() {
        // Given: one ACTIVE and one INACTIVE user
        insertUser("Active", "active-flag@example.com", UserStatus.ACTIVE);
        insertUser("Inactive", "inactive-flag@example.com", UserStatus.INACTIVE);

        // When: filtering with a null (via NullableParam), true, and false value —
        // each exercises the ambiguous ":filterActive IS NULL"/"NOT :filterActive" predicate
        List<User> nullResult = userRepository.findAllByActiveFlagAmbiguous(NullableParam.of(Boolean.class, null));
        List<User> trueResult = userRepository.findAllByActiveFlagAmbiguous(true);
        List<User> falseResult = userRepository.findAllByActiveFlagAmbiguous(false);

        // Then: no PSQLException, and each call returns the expected users
        assertThat(nullResult).extracting(User::getEmail)
                .contains("active-flag@example.com", "inactive-flag@example.com");
        assertThat(trueResult).extracting(User::getEmail)
                .contains("active-flag@example.com")
                .doesNotContain("inactive-flag@example.com");
        assertThat(falseResult).extracting(User::getEmail)
                .contains("inactive-flag@example.com")
                .doesNotContain("active-flag@example.com");
    }

    @Test
    void findAllByActiveFlagAmbiguous_nullableParam_with_non_null_value_uses_the_value() {
        // Given: one ACTIVE and one INACTIVE user
        insertUser("Active", "active-flag-nonnull@example.com", UserStatus.ACTIVE);
        insertUser("Inactive", "inactive-flag-nonnull@example.com", UserStatus.INACTIVE);

        // When: wrapping a non-null boolean in NullableParam — the type must still be
        // caught for the cast, and the actual (non-null) value must be used (issue #120)
        List<User> trueResult = userRepository.findAllByActiveFlagAmbiguous(NullableParam.of(Boolean.class, true));
        List<User> falseResult = userRepository.findAllByActiveFlagAmbiguous(NullableParam.of(Boolean.class, false));

        // Then: no PSQLException, and each call filters as if the plain boolean was passed
        assertThat(trueResult).extracting(User::getEmail)
                .contains("active-flag-nonnull@example.com")
                .doesNotContain("inactive-flag-nonnull@example.com");
        assertThat(falseResult).extracting(User::getEmail)
                .contains("inactive-flag-nonnull@example.com")
                .doesNotContain("active-flag-nonnull@example.com");
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
