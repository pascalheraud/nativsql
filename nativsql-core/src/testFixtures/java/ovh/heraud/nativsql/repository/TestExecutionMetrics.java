package ovh.heraud.nativsql.repository;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Test ExecutionMetrics implementation that allows controlling timing and UUID generation.
 * Used for deterministic tests across the test suite.
 * Operation IDs are consumed from a pre-generated queue in FIFO order.
 */
public class TestExecutionMetrics extends ExecutionMetrics {
    private Queue<String> operationIdQueue = new LinkedList<>();
    private long fixedStartTime = 1000L;
    private long fixedEndTime = 1042L;
    private int timeCallCount = 0;

    public TestExecutionMetrics() {
        // Initialize with default IDs
        setOperationIds("test-uuid-12345-1", "test-uuid-12345-2", "test-uuid-12345-3");
    }

    @Override
    public String generateOperationId() {
        timeCallCount = 0; // Reset time call counter for each operation
        return operationIdQueue.isEmpty() ? "test-default-id" : operationIdQueue.poll();
    }

    @Override
    public long getCurrentTimeMillis() {
        // First call in an operation returns startTime, second call returns endTime
        timeCallCount++;
        return (timeCallCount % 2 == 1) ? fixedStartTime : fixedEndTime;
    }

    public void setOperationIds(String... ids) {
        operationIdQueue.clear();
        operationIdQueue.addAll(Arrays.asList(ids));
    }

    public void setFixedOperationId(String id) {
        setOperationIds(id + "-1", id + "-2", id + "-3");
    }

    public void setFixedTimes(long startTime, long endTime) {
        this.fixedStartTime = startTime;
        this.fixedEndTime = endTime;
    }
}
