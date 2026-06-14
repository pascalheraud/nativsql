package ovh.heraud.nativsql.repository.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import ovh.heraud.nativsql.domain.postgres.Group;
import ovh.heraud.nativsql.domain.postgres.User;
import ovh.heraud.nativsql.domain.postgres.UserStatus;

/**
 * Integration tests for dot-notation WHERE filtering on joined table columns.
 * Verifies that whereAnd* methods accepting "assoc.column" paths produce correct SQL
 * and return the expected rows from a real PostgreSQL database.
 */
@Import({ PostgresUserRepository.class, PostgresGroupRepository.class })
class PostgresWhereJoinedTest extends PostgresRepositoryTest {

    @Autowired
    private PostgresUserRepository userRepository;

    @Autowired
    private PostgresGroupRepository groupRepository;

    // ---- helpers ----

    private Long insertGroup(String name) {
        Group group = Group.builder().name(name).build();
        groupRepository.insert(group, "name");
        return groupRepository.findAll("id", "name").stream()
                .filter(g -> name.equals(g.getName()))
                .map(Group::getId)
                .findFirst()
                .orElseThrow();
    }

    private void insertUser(String firstName, String email, UserStatus status, Long groupId) {
        User user = User.builder()
                .firstName(firstName)
                .email(email)
                .status(status)
                .groupId(groupId)
                .build();
        userRepository.insert(user, "firstName", "email", "status", "groupId");
    }

    // ---- tests ----

    @Test
    void whereAndEquals_dot_path_returns_matching_users() {
        Long adminsId = insertGroup("Admins");
        Long othersId = insertGroup("Others");

        insertUser("Alice", "alice-joined@example.com", UserStatus.ACTIVE, adminsId);
        insertUser("Bob",   "bob-joined@example.com",   UserStatus.ACTIVE, adminsId);
        insertUser("Carol", "carol-joined@example.com", UserStatus.ACTIVE, othersId);

        List<User> result = userRepository.findAllByGroupName("Admins", "id", "firstName", "email");

        List<String> emails = result.stream().map(User::getEmail).toList();
        assertThat(emails).contains("alice-joined@example.com", "bob-joined@example.com");
        assertThat(emails).doesNotContain("carol-joined@example.com");
    }

    @Test
    void whereAndEquals_dot_path_no_match_returns_empty() {
        Long othersId = insertGroup("Others");
        insertUser("Dave", "dave-joined@example.com", UserStatus.ACTIVE, othersId);

        List<User> result = userRepository.findAllByGroupName("Admins", "id", "firstName", "email");

        assertThat(result).isEmpty();
    }

    @Test
    void mixed_main_and_joined_condition_returns_intersection() {
        Long adminsId = insertGroup("Admins");

        insertUser("Eve",   "eve-joined@example.com",   UserStatus.ACTIVE,   adminsId);
        insertUser("Frank", "frank-joined@example.com", UserStatus.INACTIVE, adminsId);

        List<User> result = userRepository.findAllByStatusAndGroupName(
                UserStatus.ACTIVE, "Admins", "id", "firstName", "email", "status");

        List<String> emails = result.stream().map(User::getEmail).toList();
        assertThat(emails).contains("eve-joined@example.com");
        assertThat(emails).doesNotContain("frank-joined@example.com");
    }

    @Test
    void whereAndColumnOperator_is_null_on_joined_column_returns_users_without_group() {
        Long adminsId = insertGroup("Admins");

        insertUser("Grace", "grace-joined@example.com", UserStatus.ACTIVE, adminsId);
        insertUser("Henry", "henry-joined@example.com", UserStatus.ACTIVE, null);

        List<User> result = userRepository.findAllWithNullGroupName("id", "firstName", "email");

        List<String> emails = result.stream().map(User::getEmail).toList();
        assertThat(emails).contains("henry-joined@example.com");
        assertThat(emails).doesNotContain("grace-joined@example.com");
    }

    @Test
    void whereAndOperator_like_on_joined_column_filters_correctly() {
        Long adminsId = insertGroup("Admins");
        Long usersId  = insertGroup("Users");

        insertUser("Ivy",  "ivy-joined@example.com",  UserStatus.ACTIVE, adminsId);
        insertUser("Jack", "jack-joined@example.com", UserStatus.ACTIVE, usersId);

        List<User> result = userRepository.findAllByGroupNameLike("Admin%", "id", "firstName", "email");

        List<String> emails = result.stream().map(User::getEmail).toList();
        assertThat(emails).contains("ivy-joined@example.com");
        assertThat(emails).doesNotContain("jack-joined@example.com");
    }

    @Test
    void camelCase_column_in_dot_path_is_converted_to_snake_case() {
        // group.createdAt must produce "user_group.created_at" in the SQL (snake_case conversion)
        Long adminsId = insertGroup("Admins");

        insertUser("Kim", "kim-joined@example.com", UserStatus.ACTIVE, adminsId);
        insertUser("Leo", "leo-joined@example.com", UserStatus.ACTIVE, null);

        // IS_NOT_NULL on group.createdAt = users that have a group with a non-null createdAt
        List<User> result = userRepository.findAllByGroupCreatedAtIsNotNull("id", "firstName", "email");

        List<String> emails = result.stream().map(User::getEmail).toList();
        assertThat(emails).contains("kim-joined@example.com");
        assertThat(emails).doesNotContain("leo-joined@example.com");
    }
}
