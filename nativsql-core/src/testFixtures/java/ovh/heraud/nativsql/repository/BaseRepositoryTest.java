package ovh.heraud.nativsql.repository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptStatementFailedException;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.testcontainers.containers.JdbcDatabaseContainer;

/**
 * Base class for repository integration tests using Testcontainers.
 * Subclasses create containers in @BeforeEach and set the DataSource.
 */
@ExtendWith(SpringExtension.class)
@TestExecutionListeners(listeners = {
        DependencyInjectionTestExecutionListener.class }, mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
public abstract class BaseRepositoryTest {
    private static final Map<String, JdbcDatabaseContainer<?>> CONTAINER_CACHE = new HashMap<>();

    protected DataSource dataSource;
    private PlatformTransactionManager transactionManager;
    private TransactionStatus transaction;

    protected abstract String getScriptPath();

    protected abstract String getDatabaseVersion();

    protected abstract String getDatabaseVendor();

    protected abstract JdbcDatabaseContainer<?> createContainer(String schemaHash);

    /**
     * Initialize database container and DataSource.
     * Called automatically by JUnit @BeforeEach.
     */
    @BeforeEach
    protected void initializeDatabase() throws Exception {
        String schemaHash = computeSchemaHash();
        String cacheKey = getDatabaseVendor() + ":" + getDatabaseVersion() + ":" + schemaHash;

        JdbcDatabaseContainer<?> container = CONTAINER_CACHE.computeIfAbsent(cacheKey, key -> {
            try {
                JdbcDatabaseContainer<?> newContainer = createContainer(schemaHash);
                newContainer.start();
                initializeSchema(newContainer);
                return newContainer;
            } catch (Exception e) {
                throw new RuntimeException("Failed to create and initialize container", e);
            }
        });

        // Ensure container is running
        if (!container.isRunning()) {
            container.start();
        }

        setDataSource(createDataSourceFromContainer(container));

        // Create TransactionManager and start transaction for test rollback
        transactionManager = new DataSourceTransactionManager(dataSource);
        transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
    }

    /**
     * Rollback transaction after test.
     * Called automatically by JUnit @AfterEach.
     */
    @AfterEach
    protected void cleanup() {
        if (transaction != null && !transaction.isCompleted()) {
            transactionManager.rollback(transaction);
        }
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Set the DataSource for this test. Called from @BeforeEach.
     */
    protected void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
        injectDataSourceToRepositories(dataSource);
    }

    /**
     * Inject DataSource to all repository fields via reflection.
     * Looks for setDataSourceForTest() or setDataSource() methods.
     */
    private void injectDataSourceToRepositories(DataSource dataSource) {
        for (Field field : this.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object repo = field.get(this);
                if (repo != null) {
                    // Try setDataSourceForTest first, then setDataSource
                    Method setter = findSetterMethod(repo.getClass(), "setDataSource", DataSource.class);
                    if (setter != null) {
                        setter.invoke(repo, dataSource);

                        // Reinitialize JdbcTemplate if it's a GenericRepository
                        if (repo instanceof GenericRepository) {
                            ((GenericRepository<?, ?>) repo).initializeJdbcTemplate();
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore fields that can't be accessed
            }
        }
    }

    private Method findSetterMethod(Class<?> clazz, String methodName, Class<?> paramType) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredMethod(methodName, paramType);
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Initialize the database schema by executing the SQL script.
     * Ignores "already exists" errors when container is reused.
     */
    protected void initializeSchema(JdbcDatabaseContainer<?> container) throws SQLException, IOException {
        try (Connection conn = DriverManager.getConnection(container.getJdbcUrl(),
                container.getUsername(),
                container.getPassword())) {

            String sql = readSqlScript();
            try {
                ScriptUtils.executeSqlScript(conn,
                        new ByteArrayResource(sql.getBytes()));
            } catch (ScriptStatementFailedException e) {
                // Ignore "already exists" errors when reusing container
                // Error codes: Oracle=955, MySQL/MariaDB=1050, PostgreSQL=42710
                if (!isIgnorableSchemaError(e)) {
                    throw e;
                }
            }
        }
    }

    /**
     * Check if the exception is an ignorable schema error (already exists).
     * Recursively searches the cause chain for SQLException with ignorable error
     * codes.
     */
    private boolean isIgnorableSchemaError(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof SQLException) {
                int errorCode = ((SQLException) cause).getErrorCode();
                String sqlState = ((SQLException) cause).getSQLState();
                String message = cause.getMessage();

                // Check error codes: Oracle=955, MySQL/MariaDB=1050, PostgreSQL=42710
                if (errorCode == 955 || errorCode == 1050 || errorCode == 42710) {
                    return true;
                }
                // Also check SQL state for PostgreSQL (42710 is DUPLICATE_OBJECT)
                if ("42710".equals(sqlState)) {
                    return true;
                }
                // Check message for "already exists" keywords
                if (message != null && (message.contains("already exists") || message.contains("duplicate"))) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    private String readSqlScript() throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        BaseRepositoryTest.class.getClassLoader().getResourceAsStream(getScriptPath())))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * Create a DataSource from a JdbcDatabaseContainer.
     */
    protected DataSource createDataSourceFromContainer(JdbcDatabaseContainer<?> container) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName(container.getDriverClassName());
        ds.setUrl(container.getJdbcUrl());
        ds.setUsername(container.getUsername());
        ds.setPassword(container.getPassword());
        return ds;
    }

    /**
     * Compute SHA-256 hash of the schema script for container reuse decision.
     */
    protected String computeSchemaHash() {
        try {
            String scriptPath = getScriptPath();
            return computeSchemaHash(scriptPath);
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Compute SHA-256 hash of the schema script.
     */
    protected static String computeSchemaHash(String scriptPath) throws IOException, NoSuchAlgorithmException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        BaseRepositoryTest.class.getClassLoader().getResourceAsStream(scriptPath)))) {
            String sql = reader.lines().collect(Collectors.joining("\n"));
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(sql.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 12);
        }
    }
}
