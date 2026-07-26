# End-to-End Testing with NativSQL

This guide shows a full e2e test setup built on `BaseRepositoryTest`'s
`rollbackTransactionAfterEachTest()` opt-out (see [User Guide](../USERGUIDE.md#testing) and
[doc/issues/100-e2e-container-transaction-config/spec.md](issues/100-e2e-container-transaction-config/spec.md)).
It's adapted from a real consumer project's setup, renamed to a generic `order`/`shop` domain.

## Why a dedicated setup

An e2e scenario drives a real application process — started separately from the test JVM, with
its **own** JDBC connection(s) — through a browser or HTTP client. `BaseRepositoryTest`'s default
per-test transaction rollback wraps everything the test does in a transaction on a single
connection, which is invisible to any other connection until it commits. Since the whole point of
rollback-after-each-test is that it *never* commits, an e2e scenario using the default behavior
would seed data the running application could never see.

Overriding `rollbackTransactionAfterEachTest()` to `false` removes that obstacle: the container
and schema-init logic already built into `BaseRepositoryTest` (image selection, schema script,
container caching across the JVM) can be reused as-is for e2e tests too, instead of a project
hand-rolling its own `PostgreSQLContainer` setup just to avoid the rollback problem.

## `E2EEnvironment`: one shared environment per test run

A JVM-wide singleton, started once by the first scenario test that needs it and torn down by a
shutdown hook — not per test class, since restarting the application under test for every
scenario would make the suite too slow for CI:

```java
final class E2EEnvironment {

    private static final Object LOCK = new Object();
    private static E2EEnvironment instance;

    private final ShopE2EContainer db;
    private final Process appProcess;
    private final int appPort;

    static E2EEnvironment ensureStarted() {
        synchronized (LOCK) {
            if (instance == null) {
                instance = new E2EEnvironment();
            }
            return instance;
        }
    }

    DataSource dataSource() {
        return db.getDataSource();
    }

    String baseUrl() {
        return "http://localhost:" + appPort;
    }

    private E2EEnvironment() {
        db = new ShopE2EContainer();
        db.initializeDatabase(); // starts/reuses the cached container, applies the schema
        appPort = freePort();
        appProcess = startApp();
        waitForHttp("http://localhost:" + appPort + "/health", 60);
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    private void stop() {
        appProcess.destroy();
    }

    /**
     * Launches the packaged application jar, built beforehand (e.g. `mvn package`),
     * pointed at the container's host-mapped JDBC URL.
     */
    private Process startApp() {
        File jar = findAppJar();
        try {
            return new ProcessBuilder("java",
                    "-Dspring.datasource.url=" + db.getJdbcUrl(),
                    "-Dspring.datasource.username=" + db.getUsername(),
                    "-Dspring.datasource.password=" + db.getPassword(),
                    "-Dserver.port=" + appPort,
                    "-jar", jar.getAbsolutePath())
                    .redirectOutput(new File("target/e2e-app.log"))
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start the E2E application process", e);
        }
    }

    // findAppJar(), freePort(), waitForHttp() omitted — process/port plumbing,
    // not specific to NativSQL.
}

/**
 * Thin wrapper exposing BaseRepositoryTest's container/schema setup to a
 * non-JUnit caller: E2EEnvironment is a JVM-wide singleton, not a JUnit test
 * instance, so it calls initializeDatabase() directly instead of relying on
 * @BeforeEach.
 */
class ShopE2EContainer extends PostgresBaseRepositoryTest {
    @Override
    protected String getScriptPath() {
        return "db/schema-test.sql";
    }

    @Override
    protected boolean rollbackTransactionAfterEachTest() {
        return false;
    }

    String getJdbcUrl() { ... }
    String getUsername() { ... }
    String getPassword() { ... }
}
```

## `E2ETestBase`: the JUnit base class scenario tests extend

```java
abstract class E2ETestBase {

    protected DataSource dataSource;
    protected String baseUrl;
    protected ShopTestDataBuilder testData;

    @BeforeAll
    void setUpEnvironment() {
        E2EEnvironment env = E2EEnvironment.ensureStarted();
        dataSource = env.dataSource();
        baseUrl = env.baseUrl();
        testData = new ShopTestDataBuilder(dataSource);
    }
}
```

Since nothing rolls back between tests anymore, **data isolation between scenarios is the test
suite's own responsibility** — typically a delete+reseed test-data builder (see the
`backend-java-test-data-builder` skill) called with `testData.apply()` at the start of each test,
the same seeding mechanism used by the project's own repository integration tests.

## A worked scenario

```java
class PlaceOrderScenarioTest extends E2ETestBase {

    @Test
    void customerCanPlaceAnOrderAndSeeItInHistory() {
        // Given: a registered customer and no prior orders
        Data customer = testData.newCustomer();
        testData.apply();

        // When: placing an order through the running application
        HttpResponse<String> response = placeOrder(baseUrl, customer, "SKU-123", 2);

        // Then: the order is visible in the customer's order history
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(fetchOrderHistory(baseUrl, customer)).hasSize(1);
    }
}
```

The order placed by the running application (over its own JDBC connection) and the assertion
made by the test (querying the application's own API) both see the same committed row — the
scenario would hang or fail with the default rollback behavior, since the application's insert
would never be visible outside its own transaction either.
