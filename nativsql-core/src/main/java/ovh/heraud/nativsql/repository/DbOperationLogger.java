package ovh.heraud.nativsql.repository;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Named;
import ovh.heraud.nativsql.exception.NativSQLException;

/**
 * Spring bean for logging database operations with consistent BEGIN/END/ERROR
 * format.
 * Provides timing information and exception handling.
 */
@Named
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
        // Find the first caller in our package that is not GenericRepository or
        // DbOperationLogger
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
     * Gets the simple class name from a Class object, handling Spring CGLIB
     * proxies.
     */
    private String getSimpleClassName(Class<?> clazz) {
        String className = clazz.getName();
        // Remove Spring CGLIB proxy suffix
        if (className.contains("$$")) {
            className = className.substring(0, className.indexOf("$$"));
        }
        int lastDot = className.lastIndexOf('.');
        if (lastDot >= 0) {
            return className.substring(lastDot + 1);
        }
        return className;
    }

    /**
     * Executes a database operation with logging (methodName extracted from call
     * stack).
     * Logs DB.BEGIN, DB.END with duration, or DB.ERROR with exception.
     *
     * @param <T>             the return type
     * @param repositoryClass the repository class
     * @param operation       the operation type (INSERT, UPDATE, DELETE, SELECT,
     *                        etc.)
     * @param table           the table name
     * @param sql             the SQL query
     * @param callable        the operation to execute
     * @return the result of the operation
     * @throws NativSQLException if the operation fails
     */
    public <T> T execute(Class<?> repositoryClass, String operation, String table, String sql,
            SqlCallable<T> callable) {
        return execute(repositoryClass, getCallerMethodName(), operation, table, sql, callable);
    }

    /**
     * Executes a database operation with logging.
     * Logs DB.BEGIN, DB.END with duration, or DB.ERROR with exception.
     *
     * @param <T>             the return type
     * @param repositoryClass the repository class
     * @param methodName      the method name
     * @param operation       the operation type (INSERT, UPDATE, DELETE, SELECT,
     *                        etc.)
     * @param table           the table name
     * @param sql             the SQL query
     * @param callable        the operation to execute
     * @return the result of the operation
     * @throws NativSQLException if the operation fails
     */
    public <T> T execute(Class<?> repositoryClass, String methodName, String operation, String table, String sql,
            SqlCallable<T> callable) {
        String opId = executionMetrics.generateOperationId();
        String repositoryName = getSimpleClassName(repositoryClass);
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
     * Executes a database operation with logging and parameters (methodName
     * extracted from call stack).
     * Logs DB.BEGIN with SQL and params, DB.END with duration, or DB.ERROR with
     * exception.
     *
     * @param <T>             the return type
     * @param repositoryClass the repository class
     * @param operation       the operation type (INSERT, UPDATE, DELETE, SELECT,
     *                        etc.)
     * @param table           the table name
     * @param sql             the SQL query
     * @param params          the SQL parameters (logged at DEBUG level)
     * @param callable        the operation to execute
     * @return the result of the operation
     * @throws NativSQLException if the operation fails
     */
    public <T> T execute(Class<?> repositoryClass, String operation, String table, String sql,
            Map<String, Object> params, SqlCallable<T> callable) {
        return execute(repositoryClass, getCallerMethodName(), operation, table, sql, params, callable);
    }

    /**
     * Executes a database operation with logging and parameters.
     * Logs DB.BEGIN with SQL and params, DB.END with duration, or DB.ERROR with
     * exception.
     *
     * @param <T>             the return type
     * @param repositoryClass the repository class
     * @param methodName      the method name
     * @param operation       the operation type (INSERT, UPDATE, DELETE, SELECT,
     *                        etc.)
     * @param table           the table name
     * @param sql             the SQL query
     * @param params          the SQL parameters (logged at DEBUG level)
     * @param callable        the operation to execute
     * @return the result of the operation
     * @throws NativSQLException if the operation fails
     */
    public <T> T execute(Class<?> repositoryClass, String methodName, String operation, String table, String sql,
            Map<String, Object> params, SqlCallable<T> callable) {
        String opId = executionMetrics.generateOperationId();
        String repositoryName = getSimpleClassName(repositoryClass);
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
     * Executes a database operation without return value (methodName extracted from
     * call stack).
     * Logs DB.BEGIN, DB.END with duration, or DB.ERROR with exception.
     *
     * @param repositoryClass the repository class
     * @param operation       the operation type (INSERT, UPDATE, DELETE, SELECT,
     *                        etc.)
     * @param table           the table name
     * @param sql             the SQL query
     * @param runnable        the operation to execute
     * @throws NativSQLException if the operation fails
     */
    public void execute(Class<?> repositoryClass, String operation, String table, String sql, SqlRunnable runnable) {
        execute(repositoryClass, getCallerMethodName(), operation, table, sql, runnable);
    }

    /**
     * Executes a database operation without return value.
     * Logs DB.BEGIN, DB.END with duration, or DB.ERROR with exception.
     *
     * @param repositoryClass the repository class
     * @param methodName      the method name
     * @param operation       the operation type (INSERT, UPDATE, DELETE, SELECT,
     *                        etc.)
     * @param table           the table name
     * @param sql             the SQL query
     * @param runnable        the operation to execute
     * @throws NativSQLException if the operation fails
     */
    public void execute(Class<?> repositoryClass, String methodName, String operation, String table, String sql,
            SqlRunnable runnable) {
        String opId = executionMetrics.generateOperationId();
        String repositoryName = getSimpleClassName(repositoryClass);
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
     * Executes a database operation without return value with parameters logging
     * (methodName extracted from call stack).
     * Logs DB.BEGIN with SQL and params, DB.END with duration, or DB.ERROR with
     * exception.
     *
     * @param repositoryClass the repository class
     * @param operation       the operation type (INSERT, UPDATE, DELETE, SELECT,
     *                        etc.)
     * @param table           the table name
     * @param sql             the SQL query
     * @param params          the SQL parameters (logged at DEBUG level)
     * @param runnable        the operation to execute
     * @throws NativSQLException if the operation fails
     */
    public void execute(Class<?> repositoryClass, String operation, String table, String sql,
            Map<String, Object> params, SqlRunnable runnable) {
        execute(repositoryClass, getCallerMethodName(), operation, table, sql, params, runnable);
    }

    /**
     * Executes a database operation without return value with parameters logging.
     * Logs DB.BEGIN with SQL and params, DB.END with duration, or DB.ERROR with
     * exception.
     *
     * @param repositoryClass the repository class
     * @param methodName      the method name
     * @param operation       the operation type (INSERT, UPDATE, DELETE, SELECT,
     *                        etc.)
     * @param table           the table name
     * @param sql             the SQL query
     * @param params          the SQL parameters (logged at DEBUG level)
     * @param runnable        the operation to execute
     * @throws NativSQLException if the operation fails
     */
    public void execute(Class<?> repositoryClass, String methodName, String operation, String table, String sql,
            Map<String, Object> params, SqlRunnable runnable) {
        String opId = executionMetrics.generateOperationId();
        String repositoryName = getSimpleClassName(repositoryClass);
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
