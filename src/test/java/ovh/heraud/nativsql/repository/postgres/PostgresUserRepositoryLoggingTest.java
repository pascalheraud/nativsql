package ovh.heraud.nativsql.repository.postgres;

import java.util.List;
import java.util.function.Supplier;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.annotation.Testable;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import ovh.heraud.nativsql.domain.postgres.Address;
import ovh.heraud.nativsql.domain.postgres.User;
import ovh.heraud.nativsql.domain.postgres.UserReport;
import ovh.heraud.nativsql.domain.postgres.UserStatus;
import ovh.heraud.nativsql.repository.DbOperationLogger;
import ovh.heraud.nativsql.repository.TestExecutionMetrics;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test logging behavior of DbOperationLogger with PostgresUserRepository.
 * Validates that logs are generated correctly for:
 * - CRUD operations (insert, update, delete)
 * - findBy methods that call find()
 * - Native SQL queries via findExternal()
 *
 * Tests are run at both DEBUG and INFO log levels to verify:
 * - DEBUG level captures SQL statements and parameters
 * - INFO level captures only BEGIN/END messages
 *
 * Uses mocked ExecutionMetrics to generate predictable UUIDs and timing.
 */
@Import({ PostgresUserRepository.class, PostgresContactInfoRepository.class, PostgresGroupRepository.class })
public class PostgresUserRepositoryLoggingTest extends PostgresRepositoryTest {

    @Autowired
    private PostgresUserRepository userRepository;

    @Autowired
    private DbOperationLogger dbOperationLogger;

    protected ListAppender<ILoggingEvent> logAppender;
    protected TestExecutionMetrics testMetrics;

    protected void setupLogCapture(Level level) {
        // Setup log capture
        Logger logger = (Logger) LoggerFactory.getLogger("ovh.heraud.nativsql.repository.DbOperationLogger");
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        logger.setLevel(level);

        // Setup mock ExecutionMetrics for predictable UUIDs
        testMetrics = new TestExecutionMetrics();
        testMetrics.setFixedTimes(1000L, 1042L);

        // Inject test metrics into the logger
        ReflectionTestUtils.setField(dbOperationLogger, "executionMetrics", testMetrics);
    }

    /**
     * Helper method to verify a log event level and exact message.
     */
    protected void verifyLogEvent(List<ILoggingEvent> logList, int index, Level expectedLevel, String expectedMessage) {
        ILoggingEvent log = logList.get(index);
        assertThat(log.getLevel()).isEqualTo(expectedLevel);
        assertThat(log.getFormattedMessage()).isEqualTo(expectedMessage);
    }

    /**
     * Helper method to execute given setup and clear logs before the test.
     * This allows capturing only the logs from the actual test (When/Then sections).
     * Use this when the setup doesn't need to return a value.
     */
    protected void executeGiven(Runnable given) {
        given.run();
        logAppender.list.clear();
    }

    /**
     * Helper method to execute given setup and clear logs before the test.
     * This allows capturing only the logs from the actual test (When/Then sections).
     * Use this when the setup needs to return a value.
     */
    protected <T> T executeGiven(Supplier<T> given) {
        T result = given.get();
        logAppender.list.clear();
        return result;
    }

    @Test 
    void emptyTest() {
        // This test exists only to ensure the test class is recognized and the @BeforeEach setup runs.
        // Actual tests are in nested classes.
    }

    @Nested
    @Testable
    class DebugLoggingTests {

        @BeforeEach
        void setUp() {
            setupLogCapture(Level.DEBUG);
        }

        @Test
        void testInsertLogging() {
            // Given
            User user = User.builder()
                    .firstName("Alice")
                    .lastName("Wonder")
                    .email("alice@example.com")
                    .status(UserStatus.ACTIVE)
                    .address(new Address("123 Main St", "Paris", "75001", "France"))
                    .build();

            // When
            testMetrics.setOperationIds("test-uuid-insert-1");
            userRepository.insert(user, "firstName", "lastName", "email", "status", "address");

            // Then
            List<ILoggingEvent> logs = logAppender.list;
            assertThat(logs).hasSize(4); // BEGIN (INFO), SQL (DEBUG), PARAMS (DEBUG), END (INFO)

            verifyLogEvent(logs, 0, Level.INFO, "DB.BEGIN PostgresUserRepository.insert - INSERT users [test-uuid-insert-1]");
            verifyLogEvent(logs, 1, Level.DEBUG, "DB.SQL PostgresUserRepository.insert - INSERT users [test-uuid-insert-1] - INSERT INTO users (first_name, last_name, email, status, address) VALUES (:firstName, :lastName, :email, (:status)::user_status, (:address)::address_type)");
            verifyLogEvent(logs, 2, Level.DEBUG, "DB.PARAMS PostgresUserRepository.insert - INSERT users [test-uuid-insert-1] - {firstName=Alice, lastName=Wonder, address=(\"123 Main St\",\"Paris\",\"75001\",\"France\"), email=alice@example.com, status=ACTIVE}");
            verifyLogEvent(logs, 3, Level.INFO, "DB.END PostgresUserRepository.insert - INSERT users [test-uuid-insert-1] - 42ms");
        }

        @Test
        void testUpdateLogging() {
            // Given
            User user = executeGiven(() -> {
                User newUser = User.builder()
                        .firstName("Bob")
                        .lastName("Builder")
                        .email("bob@example.com")
                        .status(UserStatus.ACTIVE)
                        .build();
                userRepository.insert(newUser, "firstName", "lastName", "email", "status");
                return newUser;
            });

            // When
            testMetrics.setOperationIds("test-uuid-update-1");
            user.setFirstName("Robert");
            userRepository.update(user, "firstName");

            // Then
            List<ILoggingEvent> logs = logAppender.list;
            assertThat(logs).hasSize(4); // BEGIN (INFO), SQL (DEBUG), PARAMS (DEBUG), END (INFO)

            verifyLogEvent(logs, 0, Level.INFO, "DB.BEGIN PostgresUserRepository.update - UPDATE users [test-uuid-update-1]");
            verifyLogEvent(logs, 1, Level.DEBUG, "DB.SQL PostgresUserRepository.update - UPDATE users [test-uuid-update-1] - UPDATE users SET first_name = :firstName WHERE id = :id");
            verifyLogEvent(logs, 2, Level.DEBUG, "DB.PARAMS PostgresUserRepository.update - UPDATE users [test-uuid-update-1] - {firstName=Robert, id=" + user.getId() + "}");
            verifyLogEvent(logs, 3, Level.INFO, "DB.END PostgresUserRepository.update - UPDATE users [test-uuid-update-1] - 42ms");
        }

        @Test
        void testDeleteLogging() {
            // Given
            Long userId = executeGiven(() -> {
                User user = User.builder()
                        .firstName("Charlie")
                        .lastName("Brown")
                        .email("charlie@example.com")
                        .status(UserStatus.ACTIVE)
                        .build();
                userRepository.insert(user, "firstName", "lastName", "email", "status");
                return user.getId();
            });

            // When
            testMetrics.setOperationIds("test-uuid-delete-1");
            userRepository.deleteById(userId);

            // Then
            List<ILoggingEvent> logs = logAppender.list;
            assertThat(logs).hasSize(4); // BEGIN (INFO), SQL (DEBUG), PARAMS (DEBUG), END (INFO)

            verifyLogEvent(logs, 0, Level.INFO, "DB.BEGIN PostgresUserRepository.deleteById - DELETE users [test-uuid-delete-1]");
            verifyLogEvent(logs, 1, Level.DEBUG, "DB.SQL PostgresUserRepository.deleteById - DELETE users [test-uuid-delete-1] - DELETE FROM users WHERE id = :id");
            verifyLogEvent(logs, 2, Level.DEBUG, "DB.PARAMS PostgresUserRepository.deleteById - DELETE users [test-uuid-delete-1] - {id=" + userId + "}");
            verifyLogEvent(logs, 3, Level.INFO, "DB.END PostgresUserRepository.deleteById - DELETE users [test-uuid-delete-1] - 42ms");
        }

        @Test
        void testFindByEmailLogging() {
            // Given
            executeGiven(() -> {
                User user = User.builder()
                        .firstName("Dave")
                        .lastName("Davidson")
                        .email("dave@example.com")
                        .status(UserStatus.ACTIVE)
                        .build();
                userRepository.insert(user, "firstName", "lastName", "email", "status");
            });

            // When
            testMetrics.setOperationIds("test-uuid-find-1");
            User found = userRepository.findByEmail("dave@example.com", "id", "firstName", "lastName", "email", "status");

            // Then
            assertThat(found).isNotNull();
            List<ILoggingEvent> logs = logAppender.list;
            assertThat(logs).hasSize(4); // BEGIN (INFO), SQL (DEBUG), PARAMS (DEBUG), END (INFO)

            verifyLogEvent(logs, 0, Level.INFO, "DB.BEGIN PostgresUserRepository.findByEmail - SELECT users [test-uuid-find-1]");
            verifyLogEvent(logs, 1, Level.DEBUG, """
                DB.SQL PostgresUserRepository.findByEmail - SELECT users [test-uuid-find-1] - SELECT
                    users.id AS "id",
                    users.first_name AS "firstName",
                    users.last_name AS "lastName",
                    users.email AS "email",
                    users.status AS "status"
                FROM users
                WHERE
                        email = :email
                """);
            verifyLogEvent(logs, 2, Level.DEBUG, "DB.PARAMS PostgresUserRepository.findByEmail - SELECT users [test-uuid-find-1] - {email=dave@example.com}");
            verifyLogEvent(logs, 3, Level.INFO, "DB.END PostgresUserRepository.findByEmail - SELECT users [test-uuid-find-1] - 42ms");
        }

        @Test
        void testFindByCityLogging() {
            // Given
            executeGiven(() -> {
                User user = User.builder()
                        .firstName("Eve")
                        .lastName("Evans")
                        .email("eve@example.com")
                        .status(UserStatus.ACTIVE)
                        .address(new Address("456 Oak Ave", "Lyon", "69000", "France"))
                        .build();
                userRepository.insert(user, "firstName", "lastName", "email", "status", "address");
            });

            // When
            testMetrics.setOperationIds("test-uuid-findall-1");
            List<User> found = userRepository.findByCity("Lyon", "id", "firstName", "address");

            // Then
            assertThat(found).isNotEmpty();
            List<ILoggingEvent> logs = logAppender.list;
            assertThat(logs).hasSize(4); // BEGIN (INFO), SQL (DEBUG), PARAMS (DEBUG), END (INFO)

            verifyLogEvent(logs, 0, Level.INFO, "DB.BEGIN PostgresUserRepository.findByCity - SELECT users [test-uuid-findall-1]");
            verifyLogEvent(logs, 1, Level.DEBUG, """
                DB.SQL PostgresUserRepository.findByCity - SELECT users [test-uuid-findall-1] - SELECT
                    users.id AS "id",
                    users.first_name AS "firstName",
                    users.address AS "address"
                FROM users
                WHERE
                        (address).city = :city
                """);
            verifyLogEvent(logs, 2, Level.DEBUG, "DB.PARAMS PostgresUserRepository.findByCity - SELECT users [test-uuid-findall-1] - {city=Lyon}");
            verifyLogEvent(logs, 3, Level.INFO, "DB.END PostgresUserRepository.findByCity - SELECT users [test-uuid-findall-1] - 42ms");
        }

        @Test
        void testGetUsersReportLogging() {
            // Given
            executeGiven(() -> {
                User user = User.builder()
                        .firstName("Frank")
                        .lastName("Franklin")
                        .email("frank@example.com")
                        .status(UserStatus.ACTIVE)
                        .build();
                userRepository.insert(user, "firstName", "lastName", "email", "status");
            });

            // When
            testMetrics.setOperationIds("test-uuid-report-1");
            UserReport report = userRepository.getUsersReport();

            // Then
            assertThat(report).isNotNull();
            List<ILoggingEvent> logs = logAppender.list;
            assertThat(logs).hasSize(3); // BEGIN (INFO), SQL (DEBUG), END (INFO)

            verifyLogEvent(logs, 0, Level.INFO, "DB.BEGIN PostgresUserRepository.getUsersReport - SELECT users [test-uuid-report-1]");
            verifyLogEvent(logs, 1, Level.DEBUG, """
                DB.SQL PostgresUserRepository.getUsersReport - SELECT users [test-uuid-report-1] - SELECT
                    (
                        SELECT COUNT(*)
                        FROM users
                    )
                            AS "totalUsers",
                    (
                        SELECT COUNT(DISTINCT u.id)
                        FROM users u
                        INNER JOIN contact_info ci ON u.id = ci.user_id
                        WHERE ci.contact_type = 'EMAIL'::contact_type
                    )
                            AS "usersWithEmailContact",
                    (
                        SELECT COUNT(*)
                        FROM users u
                        WHERE u.preferences->>'language' = 'fr'
                    )
                            AS "usersWithFrenchPreference"
                """);
            verifyLogEvent(logs, 2, Level.INFO, "DB.END PostgresUserRepository.getUsersReport - SELECT users [test-uuid-report-1] - 42ms");
        }

        @Test
        void testGetUsersReportWithParamsLogging() throws Exception {
            // Given
            executeGiven(() -> {
                User user = User.builder()
                        .firstName("Grace")
                        .lastName("Green")
                        .email("grace@example.com")
                        .status(UserStatus.ACTIVE)
                        .build();
                userRepository.insert(user, "firstName", "lastName", "email", "status");
            });

            // When
            testMetrics.setOperationIds("test-uuid-report-params-1");
            org.postgis.Point point = new org.postgis.Point("POINT(2.3522 48.8566)");
            UserReport report = userRepository.getUsersReportAroundPoint(point);

            // Then
            assertThat(report).isNotNull();
            List<ILoggingEvent> logs = logAppender.list;
            assertThat(logs).hasSize(4); // BEGIN (INFO), SQL (DEBUG), PARAMS (DEBUG), END (INFO)

            verifyLogEvent(logs, 0, Level.INFO, "DB.BEGIN PostgresUserRepository.getUsersReportAroundPoint - SELECT users [test-uuid-report-params-1]");
            verifyLogEvent(logs, 1, Level.DEBUG, """
                DB.SQL PostgresUserRepository.getUsersReportAroundPoint - SELECT users [test-uuid-report-params-1] - SELECT
                    (
                        SELECT COUNT(*)
                        FROM users
                        WHERE ST_DWithin(position, :point::geography, 10000)
                    )
                            AS "totalUsers",
                    (
                        SELECT COUNT(DISTINCT u.id)
                        FROM users u
                        INNER JOIN contact_info ci ON u.id = ci.user_id
                        WHERE ci.contact_type = 'EMAIL'::contact_type
                        AND ST_DWithin(u.position, :point::geography, 10000)
                    )
                            AS "usersWithEmailContact",
                    (
                        SELECT COUNT(*)
                        FROM users u
                        WHERE u.preferences->>'language' = 'fr'
                        AND ST_DWithin(u.position, :point::geography, 10000)
                    )
                            AS "usersWithFrenchPreference"
                """);
            verifyLogEvent(logs, 2, Level.DEBUG, "DB.PARAMS PostgresUserRepository.getUsersReportAroundPoint - SELECT users [test-uuid-report-params-1] - {point=POINT(2.3522 48.8566)}");
            verifyLogEvent(logs, 3, Level.INFO, "DB.END PostgresUserRepository.getUsersReportAroundPoint - SELECT users [test-uuid-report-params-1] - 42ms");
        }
    }

    @Nested
    @Testable
    class InfoLoggingTests {

        @BeforeEach
        void setUp() {
            setupLogCapture(Level.INFO);
            logAppender.list.clear();
        }

        @Test
        void testInsertLogging() {
            // Given
            User user = User.builder()
                    .firstName("Alice")
                    .lastName("Wonder")
                    .email("alice@example.com")
                    .status(UserStatus.ACTIVE)
                    .address(new Address("123 Main St", "Paris", "75001", "France"))
                    .build();

            // When
            testMetrics.setOperationIds("test-uuid-insert-1");
            userRepository.insert(user, "firstName", "lastName", "email", "status", "address");

            // Then
            List<ILoggingEvent> logs = logAppender.list;
            assertThat(logs).hasSize(2); // BEGIN (INFO), END (INFO)

            verifyLogEvent(logs, 0, Level.INFO, "DB.BEGIN PostgresUserRepository.insert - INSERT users [test-uuid-insert-1]");
            verifyLogEvent(logs, 1, Level.INFO, "DB.END PostgresUserRepository.insert - INSERT users [test-uuid-insert-1] - 42ms");
        }

        @Test
        void testUpdateLogging() {
            // Given
            User user = executeGiven(() -> {
                User newUser = User.builder()
                        .firstName("Bob")
                        .lastName("Builder")
                        .email("bob@example.com")
                        .status(UserStatus.ACTIVE)
                        .build();
                userRepository.insert(newUser, "firstName", "lastName", "email", "status");
                return newUser;
            });

            // When
            testMetrics.setOperationIds("test-uuid-update-1");
            user.setFirstName("Robert");
            userRepository.update(user, "firstName");

            // Then
            List<ILoggingEvent> logs = logAppender.list;
            assertThat(logs).hasSize(2); // BEGIN (INFO), END (INFO)

            verifyLogEvent(logs, 0, Level.INFO, "DB.BEGIN PostgresUserRepository.update - UPDATE users [test-uuid-update-1]");
            verifyLogEvent(logs, 1, Level.INFO, "DB.END PostgresUserRepository.update - UPDATE users [test-uuid-update-1] - 42ms");
        }

        @Test
        void testDeleteLogging() {
            // Given
            Long[] userId = new Long[1];
            executeGiven(() -> {
                User user = User.builder()
                        .firstName("Charlie")
                        .lastName("Brown")
                        .email("charlie@example.com")
                        .status(UserStatus.ACTIVE)
                        .build();
                userRepository.insert(user, "firstName", "lastName", "email", "status");
                userId[0] = user.getId();
            });

            // When
            testMetrics.setOperationIds("test-uuid-delete-1");
            userRepository.deleteById(userId[0]);

            // Then
            List<ILoggingEvent> logs = logAppender.list;
            assertThat(logs).hasSize(2); // BEGIN (INFO), END (INFO)

            verifyLogEvent(logs, 0, Level.INFO, "DB.BEGIN PostgresUserRepository.deleteById - DELETE users [test-uuid-delete-1]");
            verifyLogEvent(logs, 1, Level.INFO, "DB.END PostgresUserRepository.deleteById - DELETE users [test-uuid-delete-1] - 42ms");
        }

        @Test
        void testFindByEmailLogging() {
            // Given
            executeGiven(() -> {
                User user = User.builder()
                        .firstName("Dave")
                        .lastName("Davidson")
                        .email("dave@example.com")
                        .status(UserStatus.ACTIVE)
                        .build();
                userRepository.insert(user, "firstName", "lastName", "email", "status");
            });

            // When
            testMetrics.setOperationIds("test-uuid-find-1");
            User found = userRepository.findByEmail("dave@example.com", "id", "firstName", "lastName", "email", "status");

            // Then
            assertThat(found).isNotNull();
            List<ILoggingEvent> logs = logAppender.list;
            assertThat(logs).hasSize(2); // BEGIN (INFO), END (INFO)

            verifyLogEvent(logs, 0, Level.INFO, "DB.BEGIN PostgresUserRepository.findByEmail - SELECT users [test-uuid-find-1]");
            verifyLogEvent(logs, 1, Level.INFO, "DB.END PostgresUserRepository.findByEmail - SELECT users [test-uuid-find-1] - 42ms");
        }

        @Test
        void testFindByCityLogging() {
            // Given
            executeGiven(() -> {
                User user = User.builder()
                        .firstName("Eve")
                        .lastName("Evans")
                        .email("eve@example.com")
                        .status(UserStatus.ACTIVE)
                        .address(new Address("456 Oak Ave", "Lyon", "69000", "France"))
                        .build();
                userRepository.insert(user, "firstName", "lastName", "email", "status", "address");
            });

            // When
            testMetrics.setOperationIds("test-uuid-findall-1");
            List<User> found = userRepository.findByCity("Lyon", "id", "firstName", "address");

            // Then
            assertThat(found).isNotEmpty();
            List<ILoggingEvent> logs = logAppender.list;
            assertThat(logs).hasSize(2); // BEGIN (INFO), END (INFO)

            verifyLogEvent(logs, 0, Level.INFO, "DB.BEGIN PostgresUserRepository.findByCity - SELECT users [test-uuid-findall-1]");
            verifyLogEvent(logs, 1, Level.INFO, "DB.END PostgresUserRepository.findByCity - SELECT users [test-uuid-findall-1] - 42ms");
        }

        @Test
        void testGetUsersReportLogging() {
            // Given
            executeGiven(() -> {
                User user = User.builder()
                        .firstName("Frank")
                        .lastName("Franklin")
                        .email("frank@example.com")
                        .status(UserStatus.ACTIVE)
                        .build();
                userRepository.insert(user, "firstName", "lastName", "email", "status");
            });

            // When
            testMetrics.setOperationIds("test-uuid-report-1");
            UserReport report = userRepository.getUsersReport();

            // Then
            assertThat(report).isNotNull();
            List<ILoggingEvent> logs = logAppender.list;
            assertThat(logs).hasSize(2); // BEGIN (INFO), END (INFO)

            verifyLogEvent(logs, 0, Level.INFO, "DB.BEGIN PostgresUserRepository.getUsersReport - SELECT users [test-uuid-report-1]");
            verifyLogEvent(logs, 1, Level.INFO, "DB.END PostgresUserRepository.getUsersReport - SELECT users [test-uuid-report-1] - 42ms");
        }

        @Test
        void testGetUsersReportWithParamsLogging() throws Exception {
            // Given
            executeGiven(() -> {
                User user = User.builder()
                        .firstName("Grace")
                        .lastName("Green")
                        .email("grace@example.com")
                        .status(UserStatus.ACTIVE)
                        .build();
                userRepository.insert(user, "firstName", "lastName", "email", "status");
            });

            // When
            testMetrics.setOperationIds("test-uuid-report-params-1");
            org.postgis.Point point = new org.postgis.Point("POINT(2.3522 48.8566)");
            UserReport report = userRepository.getUsersReportAroundPoint(point);

            // Then
            assertThat(report).isNotNull();
            List<ILoggingEvent> logs = logAppender.list;
            assertThat(logs).hasSize(2); // BEGIN (INFO), END (INFO) - no DEBUG logs

            verifyLogEvent(logs, 0, Level.INFO, "DB.BEGIN PostgresUserRepository.getUsersReportAroundPoint - SELECT users [test-uuid-report-params-1]");
            verifyLogEvent(logs, 1, Level.INFO, "DB.END PostgresUserRepository.getUsersReportAroundPoint - SELECT users [test-uuid-report-params-1] - 42ms");
        }
    }

}
