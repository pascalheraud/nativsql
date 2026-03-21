package ovh.heraud.nativsql.repository;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ovh.heraud.nativsql.exception.NativSQLException;
import ovh.heraud.nativsql.repository.DbOperationLogger.SqlCallable;

/**
 * Unit tests for DbOperationLogger.
 * Tests logging of database operations with BEGIN/END/ERROR format, timing, and parameters.
 */
class DbOperationLoggerTest {

    private DbOperationLogger dbOperationLogger;
    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;
    private TestExecutionMetrics executionMetrics;

    @BeforeEach
    void setUp() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        logger = loggerContext.getLogger(DbOperationLogger.class);

        // Create and attach list appender to capture logs
        listAppender = new ListAppender<>();
        listAppender.setContext(loggerContext);
        listAppender.start();
        logger.addAppender(listAppender);
        logger.setLevel(Level.DEBUG);

        // Create ExecutionMetrics with controllable values
        executionMetrics = new TestExecutionMetrics();
        dbOperationLogger = new DbOperationLogger(executionMetrics);
    }

    @AfterEach
    void tearDown() {
        listAppender.stop();
        logger.detachAppender(listAppender);
    }

    @Nested
    class DebugLoggingTests {
        /**
         * Test DEBUG level logging with parameters.
         */
        @Test
        void testExecuteWithCallableAndParams() {
            Map<String, Object> params = new HashMap<>();
            params.put("id", 123);
            params.put("name", "John");

            String result = dbOperationLogger.execute("SELECT", "users",
                    "SELECT * FROM users WHERE id = :id", params,
                    () -> "test-result");

            assertThat(result).isEqualTo("test-result");
            List<ILoggingEvent> logList = listAppender.list;
            assertThat(logList).hasSize(4); // BEGIN, SQL, PARAMS, END

            // Check all logs
            verifyLogEvent(logList, 0, Level.INFO, "DB.BEGIN DbOperationLoggerTest$DebugLoggingTests.testExecuteWithCallableAndParams - SELECT users [test-uuid-12345-1]");
            verifyLogEvent(logList, 1, Level.DEBUG, "DB.SQL DbOperationLoggerTest$DebugLoggingTests.testExecuteWithCallableAndParams - SELECT users [test-uuid-12345-1] - SELECT * FROM users WHERE id = :id");
            verifyLogEvent(logList, 2, Level.DEBUG, "DB.PARAMS DbOperationLoggerTest$DebugLoggingTests.testExecuteWithCallableAndParams - SELECT users [test-uuid-12345-1] - {name=John, id=123}");
            verifyLogEvent(logList, 3, Level.INFO, "DB.END DbOperationLoggerTest$DebugLoggingTests.testExecuteWithCallableAndParams - SELECT users [test-uuid-12345-1] - 42ms");
        }

        /**
         * Test DEBUG level logging with parameters on runnable operation.
         */
        @Test
        void testExecuteWithRunnableAndParams() {
            Map<String, Object> params = new HashMap<>();
            params.put("name", "Jane");
            params.put("email", "jane@example.com");

            dbOperationLogger.execute("INSERT", "users", "INSERT INTO users (name, email) VALUES (:name, :email)",
                    params,
                    () -> {
                        // Do nothing
                    });

            List<ILoggingEvent> logList = listAppender.list;
            assertThat(logList).hasSize(4); // BEGIN, SQL, PARAMS, END

            // Check all logs
            verifyLogEvent(logList, 0, Level.INFO, "DB.BEGIN DbOperationLoggerTest$DebugLoggingTests.testExecuteWithRunnableAndParams - INSERT users [test-uuid-12345-1]");
            verifyLogEvent(logList, 1, Level.DEBUG, "DB.SQL DbOperationLoggerTest$DebugLoggingTests.testExecuteWithRunnableAndParams - INSERT users [test-uuid-12345-1] - INSERT INTO users (name, email) VALUES (:name, :email)");
            verifyLogEvent(logList, 2, Level.DEBUG, "DB.PARAMS DbOperationLoggerTest$DebugLoggingTests.testExecuteWithRunnableAndParams - INSERT users [test-uuid-12345-1] - {name=Jane, email=jane@example.com}");
            verifyLogEvent(logList, 3, Level.INFO, "DB.END DbOperationLoggerTest$DebugLoggingTests.testExecuteWithRunnableAndParams - INSERT users [test-uuid-12345-1] - 42ms");
        }

        /**
         * Test that same UUID is used for all DEBUG logs of same operation.
         */
        @Test
        void testSameUUIDForAllLogs() {
            Map<String, Object> params = new HashMap<>();
            params.put("id", 1);

            dbOperationLogger.execute("SELECT", "users", "SELECT * FROM users WHERE id = :id", params,
                    () -> "result");

            List<ILoggingEvent> logList = listAppender.list;
            assertThat(logList).hasSize(4);

            // Verify all logs contain the same UUID
            verifyLogEvent(logList, 0, Level.INFO, "DB.BEGIN DbOperationLoggerTest$DebugLoggingTests.testSameUUIDForAllLogs - SELECT users [test-uuid-12345-1]");
            verifyLogEvent(logList, 1, Level.DEBUG, "DB.SQL DbOperationLoggerTest$DebugLoggingTests.testSameUUIDForAllLogs - SELECT users [test-uuid-12345-1] - SELECT * FROM users WHERE id = :id");
            verifyLogEvent(logList, 2, Level.DEBUG, "DB.PARAMS DbOperationLoggerTest$DebugLoggingTests.testSameUUIDForAllLogs - SELECT users [test-uuid-12345-1] - {id=1}");
            verifyLogEvent(logList, 3, Level.INFO, "DB.END DbOperationLoggerTest$DebugLoggingTests.testSameUUIDForAllLogs - SELECT users [test-uuid-12345-1] - 42ms");
        }

        /**
         * Test complete DEBUG logging in full chain.
         */
        @Test
        void testCompleteLoggingChainWithControlledValues() {
            executionMetrics.setFixedUUID("test-op-123");
            executionMetrics.setFixedTimes(1000L, 1050L); // 50ms duration

            Map<String, Object> params = new HashMap<>();
            params.put("email", "test@example.com");

            String result = dbOperationLogger.execute("INSERT", "users",
                    "INSERT INTO users (email) VALUES (:email)", params,
                    () -> "result-value");

            assertThat(result).isEqualTo("result-value");

            List<ILoggingEvent> logList = listAppender.list;
            assertThat(logList).hasSize(4); // BEGIN, SQL, PARAMS, END

            // Verify all logs with exact 50ms duration
            verifyLogEvent(logList, 0, Level.INFO, "DB.BEGIN DbOperationLoggerTest$DebugLoggingTests.testCompleteLoggingChainWithControlledValues - INSERT users [test-op-123-1]");
            verifyLogEvent(logList, 1, Level.DEBUG, "DB.SQL DbOperationLoggerTest$DebugLoggingTests.testCompleteLoggingChainWithControlledValues - INSERT users [test-op-123-1] - INSERT INTO users (email) VALUES (:email)");
            verifyLogEvent(logList, 2, Level.DEBUG, "DB.PARAMS DbOperationLoggerTest$DebugLoggingTests.testCompleteLoggingChainWithControlledValues - INSERT users [test-op-123-1] - {email=test@example.com}");
            verifyLogEvent(logList, 3, Level.INFO, "DB.END DbOperationLoggerTest$DebugLoggingTests.testCompleteLoggingChainWithControlledValues - INSERT users [test-op-123-1] - 50ms");
        }
    }

    @Nested
    class InfoLoggingTests {
        /**
         * Test INFO level logging with callable and no parameters.
         */
        @Test
        void testExecuteWithCallableNoParams() {
            String result = dbOperationLogger.execute("SELECT", "users", "SELECT * FROM users",
                    () -> "test-result");

            assertThat(result).isEqualTo("test-result");
            List<ILoggingEvent> logList = listAppender.list;
            assertThat(logList).hasSize(3);

            // Check all INFO and DEBUG logs
            verifyLogEvent(logList, 0, Level.INFO, "DB.BEGIN DbOperationLoggerTest$InfoLoggingTests.testExecuteWithCallableNoParams - SELECT users [test-uuid-12345-1]");
            verifyLogEvent(logList, 1, Level.DEBUG, "DB.SQL DbOperationLoggerTest$InfoLoggingTests.testExecuteWithCallableNoParams - SELECT users [test-uuid-12345-1] - SELECT * FROM users");
            verifyLogEvent(logList, 2, Level.INFO, "DB.END DbOperationLoggerTest$InfoLoggingTests.testExecuteWithCallableNoParams - SELECT users [test-uuid-12345-1] - 42ms");
        }

        /**
         * Test INFO level logging with runnable and no parameters.
         */
        @Test
        void testExecuteWithRunnableNoParams() {
            dbOperationLogger.execute("INSERT", "users", "INSERT INTO users VALUES (...)",
                    () -> {
                        // Do nothing
                    });

            List<ILoggingEvent> logList = listAppender.list;
            assertThat(logList).hasSize(3);

            // Check all INFO and DEBUG logs
            verifyLogEvent(logList, 0, Level.INFO, "DB.BEGIN DbOperationLoggerTest$InfoLoggingTests.testExecuteWithRunnableNoParams - INSERT users [test-uuid-12345-1]");
            verifyLogEvent(logList, 1, Level.DEBUG, "DB.SQL DbOperationLoggerTest$InfoLoggingTests.testExecuteWithRunnableNoParams - INSERT users [test-uuid-12345-1] - INSERT INTO users VALUES (...)");
            verifyLogEvent(logList, 2, Level.INFO, "DB.END DbOperationLoggerTest$InfoLoggingTests.testExecuteWithRunnableNoParams - INSERT users [test-uuid-12345-1] - 42ms");
        }

        /**
         * Test that operation duration is logged at INFO level.
         */
        @Test
        void testExecutionDurationLogged() throws Exception {
            executionMetrics.setFixedTimes(1000L, 1087L); // 87ms duration

            dbOperationLogger.execute("SELECT", "users", "SELECT * FROM users",
                    () -> {
                        return "result";
                    });

            List<ILoggingEvent> logList = listAppender.list;
            assertThat(logList).hasSize(3);

            // Check all INFO level logs with exact duration
            verifyLogEvent(logList, 0, Level.INFO, "DB.BEGIN DbOperationLoggerTest$InfoLoggingTests.testExecutionDurationLogged - SELECT users [test-uuid-12345-1]");
            verifyLogEvent(logList, 1, Level.DEBUG, "DB.SQL DbOperationLoggerTest$InfoLoggingTests.testExecutionDurationLogged - SELECT users [test-uuid-12345-1] - SELECT * FROM users");
            verifyLogEvent(logList, 2, Level.INFO, "DB.END DbOperationLoggerTest$InfoLoggingTests.testExecutionDurationLogged - SELECT users [test-uuid-12345-1] - 87ms");
        }

        /**
         * Test that each operation has a unique UUID at INFO level.
         */
        @Test
        void testUniqueUUIDs() {
            dbOperationLogger.execute("SELECT", "users", "SELECT * FROM users",
                    () -> "result1");

            List<ILoggingEvent> firstOpLogs = new ArrayList<>(listAppender.list);
            listAppender.list.clear();

            dbOperationLogger.execute("SELECT", "users", "SELECT * FROM users",
                    () -> "result2");

            // Each operation should have different UUID in all logs
            assertThat(firstOpLogs).hasSize(3);
            verifyLogEvent(firstOpLogs, 0, Level.INFO, "DB.BEGIN DbOperationLoggerTest$InfoLoggingTests.testUniqueUUIDs - SELECT users [test-uuid-12345-1]");
            verifyLogEvent(firstOpLogs, 1, Level.DEBUG, "DB.SQL DbOperationLoggerTest$InfoLoggingTests.testUniqueUUIDs - SELECT users [test-uuid-12345-1] - SELECT * FROM users");
            verifyLogEvent(firstOpLogs, 2, Level.INFO, "DB.END DbOperationLoggerTest$InfoLoggingTests.testUniqueUUIDs - SELECT users [test-uuid-12345-1] - 42ms");

            assertThat(listAppender.list).hasSize(3);
            verifyLogEvent(listAppender.list, 0, Level.INFO, "DB.BEGIN DbOperationLoggerTest$InfoLoggingTests.testUniqueUUIDs - SELECT users [test-uuid-12345-2]");
            verifyLogEvent(listAppender.list, 1, Level.DEBUG, "DB.SQL DbOperationLoggerTest$InfoLoggingTests.testUniqueUUIDs - SELECT users [test-uuid-12345-2] - SELECT * FROM users");
            verifyLogEvent(listAppender.list, 2, Level.INFO, "DB.END DbOperationLoggerTest$InfoLoggingTests.testUniqueUUIDs - SELECT users [test-uuid-12345-2] - 42ms");
        }

        /**
         * Test that no parameters are logged when params is empty.
         */
        @Test
        void testNoLoggingWhenParamsEmpty() {
            Map<String, Object> emptyParams = new HashMap<>();

            dbOperationLogger.execute("SELECT", "users", "SELECT * FROM users", emptyParams,
                    () -> "result");

            List<ILoggingEvent> logList = listAppender.list;
            assertThat(logList).hasSize(3); // BEGIN, SQL, END but no PARAMS log

            // Check all logs (no PARAMS when empty)
            verifyLogEvent(logList, 0, Level.INFO, "DB.BEGIN DbOperationLoggerTest$InfoLoggingTests.testNoLoggingWhenParamsEmpty - SELECT users [test-uuid-12345-1]");
            verifyLogEvent(logList, 1, Level.DEBUG, "DB.SQL DbOperationLoggerTest$InfoLoggingTests.testNoLoggingWhenParamsEmpty - SELECT users [test-uuid-12345-1] - SELECT * FROM users");
            verifyLogEvent(logList, 2, Level.INFO, "DB.END DbOperationLoggerTest$InfoLoggingTests.testNoLoggingWhenParamsEmpty - SELECT users [test-uuid-12345-1] - 42ms");
        }

        /**
         * Test that null parameters are handled gracefully.
         */
        @Test
        void testNullParametersHandled() {
            dbOperationLogger.execute("SELECT", "users", "SELECT * FROM users", null,
                    () -> "result");

            List<ILoggingEvent> logList = listAppender.list;
            assertThat(logList).hasSize(3); // BEGIN, SQL, END but no PARAMS log

            // Check all logs (no PARAMS when null)
            verifyLogEvent(logList, 0, Level.INFO, "DB.BEGIN DbOperationLoggerTest$InfoLoggingTests.testNullParametersHandled - SELECT users [test-uuid-12345-1]");
            verifyLogEvent(logList, 1, Level.DEBUG, "DB.SQL DbOperationLoggerTest$InfoLoggingTests.testNullParametersHandled - SELECT users [test-uuid-12345-1] - SELECT * FROM users");
            verifyLogEvent(logList, 2, Level.INFO, "DB.END DbOperationLoggerTest$InfoLoggingTests.testNullParametersHandled - SELECT users [test-uuid-12345-1] - 42ms");
        }

        /**
         * Test that correct method name is included in INFO logs.
         */
        @Test
        void testOperationNameInLogs() {
            Map<String, Object> params = new HashMap<>();
            dbOperationLogger.execute("INSERT", "users", "INSERT INTO users VALUES (...)", params,
                    () -> null);

            List<ILoggingEvent> logList = listAppender.list;
            assertThat(logList).hasSize(3); // BEGIN, SQL, END (no PARAMS since empty)

            // Verify all logs with correct method name
            verifyLogEvent(logList, 0, Level.INFO, "DB.BEGIN DbOperationLoggerTest$InfoLoggingTests.testOperationNameInLogs - INSERT users [test-uuid-12345-1]");
            verifyLogEvent(logList, 1, Level.DEBUG, "DB.SQL DbOperationLoggerTest$InfoLoggingTests.testOperationNameInLogs - INSERT users [test-uuid-12345-1] - INSERT INTO users VALUES (...)");
            verifyLogEvent(logList, 2, Level.INFO, "DB.END DbOperationLoggerTest$InfoLoggingTests.testOperationNameInLogs - INSERT users [test-uuid-12345-1] - 42ms");
        }
    }

    @Nested
    class ErrorLoggingTests {
        /**
         * Test ERROR level logging with NativSQLException.
         */
        @Test
        void testExecuteWithNativSQLException() {
            NativSQLException exception = null;
            try {
                dbOperationLogger.execute("DELETE", "users", "DELETE FROM users",
                        (SqlCallable<Void>) () -> {
                            throw new NativSQLException("Constraint violation");
                        });
            } catch (NativSQLException e) {
                exception = e;
            }

            // NativSQLException is re-thrown as-is
            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).isEqualTo("Constraint violation");

            List<ILoggingEvent> logList = listAppender.list;
            assertThat(logList).hasSize(3);

            // Check all logs with correct method name
            verifyLogEvent(logList, 0, Level.INFO, "DB.BEGIN DbOperationLoggerTest$ErrorLoggingTests.testExecuteWithNativSQLException - DELETE users [test-uuid-12345-1]");
            verifyLogEvent(logList, 1, Level.DEBUG, "DB.SQL DbOperationLoggerTest$ErrorLoggingTests.testExecuteWithNativSQLException - DELETE users [test-uuid-12345-1] - DELETE FROM users");
            verifyLogEvent(logList, 2, Level.ERROR, "DB.ERROR DbOperationLoggerTest$ErrorLoggingTests.testExecuteWithNativSQLException - DELETE users [test-uuid-12345-1] - Constraint violation");
        }

        /**
         * Test ERROR level logging with generic Exception (wrapped in NativSQLException).
         */
        @Test
        void testExecuteWithGenericException() {
            NativSQLException exception = null;
            try {
                dbOperationLogger.execute("UPDATE", "users", "UPDATE users SET name = ?",
                        (SqlCallable<Void>) () -> {
                            throw new RuntimeException("Database error");
                        });
            } catch (NativSQLException e) {
                exception = e;
            }

            assertThat(exception).isNotNull();
            assertThat(exception.getMessage()).contains("Error executing UPDATE on users");
            assertThat(exception.getMessage()).contains("Database error");

            List<ILoggingEvent> logList = listAppender.list;
            assertThat(logList).hasSize(3);

            // Check all logs with correct method name
            verifyLogEvent(logList, 0, Level.INFO, "DB.BEGIN DbOperationLoggerTest$ErrorLoggingTests.testExecuteWithGenericException - UPDATE users [test-uuid-12345-1]");
            verifyLogEvent(logList, 1, Level.DEBUG, "DB.SQL DbOperationLoggerTest$ErrorLoggingTests.testExecuteWithGenericException - UPDATE users [test-uuid-12345-1] - UPDATE users SET name = ?");
            verifyLogEvent(logList, 2, Level.ERROR, "DB.ERROR DbOperationLoggerTest$ErrorLoggingTests.testExecuteWithGenericException - UPDATE users [test-uuid-12345-1] - Database error");
        }
    }

    /**
     * Helper method to verify a log event level and exact message.
     */
    private void verifyLogEvent(List<ILoggingEvent> logList, int index, Level expectedLevel, String expectedMessage) {
        ILoggingEvent log = logList.get(index);
        assertThat(log.getLevel()).isEqualTo(expectedLevel);
        assertThat(log.getFormattedMessage()).isEqualTo(expectedMessage);
    }

    /**
     * Test ExecutionMetrics implementation that allows controlling timing and UUID generation.
     * Used for deterministic tests.
     * UUIDs are consumed from a pre-generated queue in FIFO order.
     */
    private static class TestExecutionMetrics extends ExecutionMetrics {
        private Queue<String> uuidQueue = new LinkedList<>();
        private long fixedStartTime = 1000L;
        private long fixedEndTime = 1042L;
        private int timeCallCount = 0;

        public TestExecutionMetrics() {
            // Initialize with default UUIDs
            setUUIDs("test-uuid-12345-1", "test-uuid-12345-2", "test-uuid-12345-3");
        }

        @Override
        public String generateOperationId() {
            timeCallCount = 0; // Reset time call counter for each operation
            return uuidQueue.poll();
        }

        @Override
        public long getCurrentTimeMillis() {
            // First call in an operation returns startTime, second call returns endTime
            timeCallCount++;
            return (timeCallCount % 2 == 1) ? fixedStartTime : fixedEndTime;
        }

        public void setUUIDs(String... uuids) {
            uuidQueue.clear();
            uuidQueue.addAll(Arrays.asList(uuids));
        }

        public void setFixedUUID(String uuid) {
            setUUIDs(uuid + "-1", uuid + "-2", uuid + "-3");
        }

        public void setFixedTimes(long startTime, long endTime) {
            this.fixedStartTime = startTime;
            this.fixedEndTime = endTime;
        }
    }

}
