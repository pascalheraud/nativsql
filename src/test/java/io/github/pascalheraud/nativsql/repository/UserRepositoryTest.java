package io.github.pascalheraud.nativsql.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import io.github.pascalheraud.nativsql.config.NativSqlConfig;
import io.github.pascalheraud.nativsql.domain.Address;
import io.github.pascalheraud.nativsql.domain.Preferences;
import io.github.pascalheraud.nativsql.domain.User;
import io.github.pascalheraud.nativsql.domain.UserStatus;
import io.github.pascalheraud.nativsql.mapper.RowMapperFactory;

/**
 * Integration tests for UserRepository using Testcontainers.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers
@Import({ NativSqlConfig.class, RowMapperFactory.class, UserRepository.class })
class UserRepositoryTest {
    // Déclare l'image comme compatible avec postgres
    static DockerImageName postgisImage = DockerImageName
            .parse("postgis/postgis:15-3.3")
            .asCompatibleSubstituteFor("postgres");

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer(postgisImage)
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("test-schema.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // Clean up before each test
        jdbcTemplate.getJdbcTemplate().execute("TRUNCATE users CASCADE");
    }

    @Test
    void testInsertUser() {
        // Given
        User user = new User();
        user.setFirstName("Alice");
        user.setLastName("Wonder");
        user.setEmail("alice@example.com");
        user.setStatus(UserStatus.ACTIVE);

        Address address = new Address("123 Main St", "Paris", "75001", "France");
        user.setAddress(address);

        Preferences prefs = new Preferences("fr", "dark", true);
        user.setPreferences(prefs);

        // When
        int rows = userRepository.insert(user, "firstName", "lastName", "email", "status", "address", "preferences");

        // Then
        assertThat(rows).isEqualTo(1);

        User found = userRepository.findByEmail("alice@example.com", "id", "firstName", "lastName", "email", "status", "address", "preferences");
        assertThat(found).isNotNull();
        assertThat(found.getFirstName()).isEqualTo("Alice");
        assertThat(found.getLastName()).isEqualTo("Wonder");
        assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(found.getAddress()).isNotNull();
        assertThat(found.getAddress().getCity()).isEqualTo("Paris");
        assertThat(found.getPreferences()).isNotNull();
        assertThat(found.getPreferences().getTheme()).isEqualTo("dark");
    }

    @Test
    void testInsertUserWithAllFields() {
        // Given
        User user = new User();
        user.setFirstName("Bob");
        user.setLastName("Builder");
        user.setEmail("bob@example.com");
        user.setStatus(UserStatus.INACTIVE);

        // When - insert specified fields
        int rows = userRepository.insert(user, "firstName", "lastName", "email", "status");

        // Then
        assertThat(rows).isEqualTo(1);

        User found = userRepository.findByEmail("bob@example.com", "id", "firstName", "lastName", "email", "status");
        assertThat(found).isNotNull();
        assertThat(found.getFirstName()).isEqualTo("Bob");
        assertThat(found.getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void testUpdateUser() {
        // Given - insert a user first
        User user = new User();
        user.setFirstName("Charlie");
        user.setLastName("Brown");
        user.setEmail("charlie@example.com");
        user.setStatus(UserStatus.ACTIVE);
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User found = userRepository.findByEmail("charlie@example.com", "id", "firstName", "lastName", "email", "status", "address");
        assertThat(found).isNotNull();

        // When - update the user
        found.setFirstName("Charles");
        found.setStatus(UserStatus.SUSPENDED);

        Address newAddress = new Address("456 Oak Ave", "Lyon", "69001", "France");
        found.setAddress(newAddress);

        int rows = userRepository.update(found, "id", "firstName", "status", "address");

        // Then
        assertThat(rows).isEqualTo(1);

        User updated = userRepository.findByEmail("charlie@example.com", "id", "firstName", "lastName", "email", "status", "address");
        assertThat(updated.getFirstName()).isEqualTo("Charles");
        assertThat(updated.getLastName()).isEqualTo("Brown"); // Unchanged
        assertThat(updated.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(updated.getAddress()).isNotNull();
        assertThat(updated.getAddress().getCity()).isEqualTo("Lyon");
    }

    @Test
    void testUpdateUserAllFields() {
        // Given
        User user = new User();
        user.setFirstName("Dave");
        user.setLastName("Davidson");
        user.setEmail("dave@example.com");
        user.setStatus(UserStatus.ACTIVE);
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User found = userRepository.findByEmail("dave@example.com", "id", "firstName", "lastName", "email", "status");

        // When - update specified fields
        found.setLastName("Davies");
        found.setStatus(UserStatus.INACTIVE);

        int rows = userRepository.update(found, "id", "lastName", "status");

        // Then
        assertThat(rows).isEqualTo(1);

        User updated = userRepository.findByEmail("dave@example.com", "id", "lastName", "email", "status");
        assertThat(updated.getLastName()).isEqualTo("Davies");
        assertThat(updated.getStatus()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void testDeleteUser() {
        // Given
        User user = new User();
        user.setFirstName("Eve");
        user.setLastName("Everton");
        user.setEmail("eve@example.com");
        user.setStatus(UserStatus.ACTIVE);
        userRepository.insert(user, "firstName", "lastName", "email", "status");

        User found = userRepository.findByEmail("eve@example.com", "id", "email");
        assertThat(found).isNotNull();

        // When
        int rows = userRepository.delete(found.getId());

        // Then
        assertThat(rows).isEqualTo(1);

        User deleted = userRepository.findByEmail("eve@example.com", "id", "email");
        assertThat(deleted).isNull();
    }

    @Test
    void testFindByCity() {
        // Given
        User user1 = new User();
        user1.setFirstName("Frank");
        user1.setEmail("frank@example.com");
        user1.setAddress(new Address("123 St", "Paris", "75001", "France"));
        userRepository.insert(user1, "firstName", "email", "address");

        User user2 = new User();
        user2.setFirstName("Grace");
        user2.setEmail("grace@example.com");
        user2.setAddress(new Address("456 Ave", "Lyon", "69001", "France"));
        userRepository.insert(user2, "firstName", "email", "address");

        User user3 = new User();
        user3.setFirstName("Henry");
        user3.setEmail("henry@example.com");
        user3.setAddress(new Address("789 Blvd", "Paris", "75002", "France"));
        userRepository.insert(user3, "firstName", "email", "address");

        // When
        List<User> parisUsers = userRepository.findByCity("Paris", "id", "firstName", "email", "address");

        // Then
        assertThat(parisUsers).hasSize(2);
        assertThat(parisUsers).extracting(User::getFirstName)
                .containsExactlyInAnyOrder("Frank", "Henry");
    }

    @Test
    void testEnumMapping() {
        // Given
        User activeUser = new User();
        activeUser.setEmail("active@example.com");
        activeUser.setStatus(UserStatus.ACTIVE);
        userRepository.insert(activeUser, "email", "status");

        User suspendedUser = new User();
        suspendedUser.setEmail("suspended@example.com");
        suspendedUser.setStatus(UserStatus.SUSPENDED);
        userRepository.insert(suspendedUser, "email", "status");

        // When
        User found1 = userRepository.findByEmail("active@example.com", "id", "email", "status");
        User found2 = userRepository.findByEmail("suspended@example.com", "id", "email", "status");

        // Then
        assertThat(found1.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(found2.getStatus()).isEqualTo(UserStatus.SUSPENDED);
    }
}