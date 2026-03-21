package ovh.heraud.nativsql.repository;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ovh.heraud.nativsql.exception.NativSQLException;

/**
 * Spring bean for logging database operations with consistent BEGIN/END/ERROR format.
 * Provides timing information and exception handling.
 */
@Component
public class DbOperationLogger {

    private final Logger logger = LoggerFactory.getLogger(DbOperationLogger.class);
    private final ExecutionMetrics executionMetrics;

    public DbOperationLogger() {
        this(new ExecutionMetrics());
    }

    public DbOperationLogger(ExecutionMetrics executionMetrics) {
        this.executionMetrics = executionMetrics;
    }

    /**
     * Gets the name of the method that called the execute method.
     */
    private String getCallerMethodName() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // Find the first caller in our package that is not GenericRepository or DbOperationLogger
        for (int i = 3; i < stackTrace.length; i++) {
            String className = stackTrace[i].getClassName();
            if (className.startsWith("ovh.heraud") &&
                !className.contains("GenericRepository") &&
                !className.contains("DbOperationLogger")) {
                return stackTrace[i].getMethodName();
            }
        }
        // Fallback to position [3]
        if (stackTrace.length > 3) {
            return stackTrace[3].getMethodName();
        }
        return "unknown";
    }

    /**
     * Gets the simple class name of the caller of the execute method.
     * Looks for the first caller in our package that is not GenericRepository or DbOperationLogger.
     */
    private String getCallerClassName() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // Find the first caller in our package that is not GenericRepository or DbOperationLogger
        for (int i = 3; i < stackTrace.length; i++) {
            String className = stackTrace[i].getClassName();
            if (className.startsWith("ovh.heraud") &&
                !className.contains("GenericRepository") &&
                !className.contains("DbOperationLogger")) {
                // Extract simple class name from fully qualified name and remove Spring CGLIB proxy suffix
                String simpleName = className;
                if (className.contains("$$")) {
                    simpleName = className.substring(0, className.indexOf("$$"));
                }
                int lastDot = simpleName.lastIndexOf('.');
                if (lastDot >= 0) {
                    return simpleName.substring(lastDot + 1);
                }
                return simpleName;
            }
        }
        // Fallback to position [3]
        if (stackTrace.length > 3) {
            String className = stackTrace[3].getClassName();
            String simpleName = className;
            if (className.contains("$$")) {
                simpleName = className.substring(0, className.indexOf("$$"));
            }
            int lastDot = simpleName.lastIndexOf('.');
            if (lastDot >= 0) {
                return simpleName.substring(lastDot + 1);
            }
            return simpleName;
        }
        return "unknown";
    }

    /**
     * Executes a database operation with logging.
     * Logs DB.BEGIN, DB.END with duration, or DB.ERROR with exception.
     *
     * @param <T>              the return type
     * @param operation        the operation type (INSERT, UPDATE, DELETE, SELECT, etc.)
     * @param table            the table name
     * @param sql              the SQL query
     * @param callable         the operation to execute
     * @return the result of the operation
     * @throws NativSQLException if the operation fails
     */
    public <T> T execute(String operation, String table, String sql, SqlCallable<T> callable) {
        String opId = executionMetrics.generateOperationId();
        String repositoryName = getCallerClassName();
        String methodName = getCallerMethodName();
        String opLabel = repositoryName + "." + methodName + " - " + operation + " " + table + " [" + opId + "]";

        logger.info("DB.BEGIN {}", opLabel);
        if (logger.isDebugEnabled()) {
            logger.debug("DB.SQL {} - {}", opLabel, sql);
        }
        long startTime = executionMetrics.getCurrentTimeMillis();

        try {
            T result = callable.call();
            long duration = executionMetrics.getCurrentTimeMillis() - startTime;
            logger.info("DB.END {} - {}ms", opLabel, duration);
            return result;
        } catch (NativSQLException e) {
            logger.error("DB.ERROR {} - {}", opLabel, e.getMessage(), e);
            throw e;
        } catch (Throwable t) {
            logger.error("DB.ERROR {} - {}", opLabel, t.getMessage(), t);
            throw new NativSQLException("Error executing " + operation + " on " + table + ": " + t.getMessage(), t);
        }
    }

    /**
     * Executes a database operation with logging and parameters.
     * Logs DB.BEGIN with SQL and params, DB.END with duration, or DB.ERROR with exception.
     *
     * @param <T>              the return type
     * @param operation        the operation type (INSERT, UPDATE, DELETE, SELECT, etc.)
     * @param table            the table name
     * @param sql              the SQL query
     * @param params           the SQL parameters (logged at DEBUG level)
     * @param callable         the operation to execute
     * @return the result of the operation
     * @throws NativSQLException if the operation fails
     */
    public <T> T execute(String operation, String table, String sql, Map<String, Object> params, SqlCallable<T> callable) {
        String opId = executionMetrics.generateOperationId();
        String repositoryName = getCallerClassName();
        String methodName = getCallerMethodName();
        String opLabel = repositoryName + "." + methodName + " - " + operation + " " + table + " [" + opId + "]";

        logger.info("DB.BEGIN {}", opLabel);
        if (logger.isDebugEnabled()) {
            logger.debug("DB.SQL {} - {}", opLabel, sql);
            if (params != null && !params.isEmpty()) {
                logger.debug("DB.PARAMS {} - {}", opLabel, params);
            }
        }
        long startTime = executionMetrics.getCurrentTimeMillis();

        try {
            T result = callable.call();
            long duration = executionMetrics.getCurrentTimeMillis() - startTime;
            logger.info("DB.END {} - {}ms", opLabel, duration);
            return result;
        } catch (NativSQLException e) {
            logger.error("DB.ERROR {} - {}", opLabel, e.getMessage(), e);
            throw e;
        } catch (Throwable t) {
            logger.error("DB.ERROR {} - {}", opLabel, t.getMessage(), t);
            throw new NativSQLException("Error executing " + operation + " on " + table + ": " + t.getMessage(), t);
        }
    }

    /**
     * Executes a database operation without return value.
     * Logs DB.BEGIN, DB.END with duration, or DB.ERROR with exception.
     *
     * @param operation       the operation type (INSERT, UPDATE, DELETE, SELECT, etc.)
     * @param table           the table name
     * @param sql             the SQL query
     * @param runnable        the operation to execute
     * @throws NativSQLException if the operation fails
     */
    public void execute(String operation, String table, String sql, SqlRunnable runnable) {
        String opId = executionMetrics.generateOperationId();
        String repositoryName = getCallerClassName();
        String methodName = getCallerMethodName();
        String opLabel = repositoryName + "." + methodName + " - " + operation + " " + table + " [" + opId + "]";

        logger.info("DB.BEGIN {}", opLabel);
        if (logger.isDebugEnabled()) {
            logger.debug("DB.SQL {} - {}", opLabel, sql);
        }
        long startTime = executionMetrics.getCurrentTimeMillis();

        try {
            runnable.run();
            long duration = executionMetrics.getCurrentTimeMillis() - startTime;
            logger.info("DB.END {} - {}ms", opLabel, duration);
        } catch (NativSQLException e) {
            logger.error("DB.ERROR {} - {}", opLabel, e.getMessage(), e);
            throw e;
        } catch (Throwable t) {
            logger.error("DB.ERROR {} - {}", opLabel, t.getMessage(), t);
            throw new NativSQLException("Error executing " + operation + " on " + table + ": " + t.getMessage(), t);
        }
    }

    /**
     * Executes a database operation without return value with parameters logging.
     * Logs DB.BEGIN with SQL and params, DB.END with duration, or DB.ERROR with exception.
     *
     * @param operation       the operation type (INSERT, UPDATE, DELETE, SELECT, etc.)
     * @param table           the table name
     * @param sql             the SQL query
     * @param params          the SQL parameters (logged at DEBUG level)
     * @param runnable        the operation to execute
     * @throws NativSQLException if the operation fails
     */
    public void execute(String operation, String table, String sql, Map<String, Object> params, SqlRunnable runnable) {
        String opId = executionMetrics.generateOperationId();
        String repositoryName = getCallerClassName();
        String methodName = getCallerMethodName();
        String opLabel = repositoryName + "." + methodName + " - " + operation + " " + table + " [" + opId + "]";

        logger.info("DB.BEGIN {}", opLabel);
        if (logger.isDebugEnabled()) {
            logger.debug("DB.SQL {} - {}", opLabel, sql);
            if (params != null && !params.isEmpty()) {
                logger.debug("DB.PARAMS {} - {}", opLabel, params);
            }
        }
        long startTime = executionMetrics.getCurrentTimeMillis();

        try {
            runnable.run();
            long duration = executionMetrics.getCurrentTimeMillis() - startTime;
            logger.info("DB.END {} - {}ms", opLabel, duration);
        } catch (NativSQLException e) {
            logger.error("DB.ERROR {} - {}", opLabel, e.getMessage(), e);
            throw e;
        } catch (Throwable t) {
            logger.error("DB.ERROR {} - {}", opLabel, t.getMessage(), t);
            throw new NativSQLException("Error executing " + operation + " on " + table + ": " + t.getMessage(), t);
        }
    }

    /**
     * Functional interface for SQL operations that return a value.
     */
    @FunctionalInterface
    public interface SqlCallable<T> {
        T call() throws Throwable;
    }

    /**
     * Functional interface for SQL operations that don't return a value.
     */
    @FunctionalInterface
    public interface SqlRunnable {
        void run() throws Throwable;
    }
}
