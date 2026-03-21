package ovh.heraud.nativsql.repository;

import java.util.UUID;

/**
 * Encapsulates timing and ID generation for database operations.
 * Separated for testability and to allow mocking/replacement in tests.
 */
public class ExecutionMetrics {

    /**
     * Generates a unique operation ID.
     */
    public String generateOperationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Gets the current system time in milliseconds.
     */
    public long getCurrentTimeMillis() {
        return System.currentTimeMillis();
    }

}
