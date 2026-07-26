# Plan: configurable transaction rollback in `BaseRepositoryTest`

> Issue: [nativsql#100](https://github.com/heraud/nativsql/issues/100)
> See [spec.md](spec.md) for the full design rationale.

## Step 1 — Add `rollbackTransactionAfterEachTest()` extension point

File: `nativsql-core/src/testFixtures/java/ovh/heraud/nativsql/repository/BaseRepositoryTest.java`

- Add a new `protected` method, placed near the other overridable extension points
  (`getScriptPath()`, `getDatabaseVersion()`, `getDatabaseVendor()`, `createContainer(String)`):

  ```java
  protected boolean rollbackTransactionAfterEachTest() {
      return true;
  }
  ```

## Step 2 — Make transaction opening conditional in `initializeDatabase()`

Same file, in `initializeDatabase()` (currently unconditionally does):

```java
transactionManager = new DataSourceTransactionManager(dataSource);
transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
```

Guard this behind `rollbackTransactionAfterEachTest()`:

```java
if (rollbackTransactionAfterEachTest()) {
    transactionManager = new DataSourceTransactionManager(dataSource);
    transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
}
```

`transactionManager`/`transaction` fields stay `null` when the flag is `false`.

## Step 3 — Make `cleanup()` a no-op when there's nothing to roll back

Same file, `cleanup()` already guards on `transaction != null && !transaction.isCompleted()` —
this already becomes a correct no-op once `transaction` stays `null` from Step 2, so **no code
change needed here**, just confirm by reading it (`transaction` is only ever non-null when a
transaction was actually opened).

## Step 4 — Unit/integration test for the new flag — DONE

Implemented as two dedicated test classes in
`nativsql-postgres/src/test/java/ovh/heraud/nativsql/repository/postgres/`, both extending
`PostgresRepositoryTest` (real Testcontainers-backed Postgres, no mocks):

- **`PostgresRollbackConfigTest`** — default behavior, no override. Inserts a row via
  `JdbcTemplate` (so the insert participates in the Spring-managed transaction bound to the
  current thread), then opens a **second, independent JDBC connection** directly from
  `getDataSource().getConnection()` (a plain `DriverManagerDataSource`, so this really is a new
  physical connection, not thread-bound) and asserts the row is **not** visible — proving the
  transaction is still open/uncommitted.
- **`PostgresRollbackDisabledConfigTest`** — overrides `rollbackTransactionAfterEachTest()` to
  return `false`. Same insert, then the same second-connection check asserts the row **is**
  visible — proving it was actually committed. Its `@AfterEach` deletes the row manually, since
  nothing rolls back automatically anymore and the container is cached/shared across tests.

This tests the same property as originally planned (visibility across connections) more directly
than the original "insert in test A, check absence in test B" design, which would have depended on
JUnit method execution order (not guaranteed) — a same-test, two-connection check is deterministic
and doesn't need that assumption.

Both tests run via `./gradlew :nativsql-postgres:test --tests "*PostgresRollback*"` — confirmed
passing, and the full `:nativsql-postgres:test` suite passes with no regressions.

Ran only after asking the user via `AskUserQuestion`, per project convention.

## Step 5 — Documentation

- **CHANGELOG.md**: add an entry under the current version — ask the user via
  `AskUserQuestion` whether to bump the version first, per the `documentation` skill.
  Suggested entry: "Added `rollbackTransactionAfterEachTest()` to `BaseRepositoryTest`, letting
  e2e test subclasses opt out of the per-test rollback so seeded data is visible to an
  application-under-test's own connections — see
  doc/issues/100-e2e-container-transaction-config/spec.md"
- **USERGUIDE.md**: add a new `## Testing` section (after `## Logging`, before `## FAQ`; add an
  entry to the Table of Contents too) covering both usages of `BaseRepositoryTest`:
  - Default (rollback) usage for repository integration tests — one dialect-specific base class
    per project, overriding `getScriptPath()`/`getDatabaseVersion()`/`getDatabaseVendor()`/
    `createContainer(String)`.
  - Opt-out usage for e2e tests (`rollbackTransactionAfterEachTest()` → `false`), with a short
    example, then a link to the new `doc/EndToEndTesting.md` (see below) for the full setup.

    ```java
    // Repository/unit-style integration tests: rollback after each test (default)
    public abstract class OrderRepositoryTest extends PostgresBaseRepositoryTest {
        @Override
        protected String getScriptPath() {
            return "db/schema-test.sql";
        }
    }

    // E2E tests: data must be committed so a separately-started application process
    // (its own JDBC connection) can see it — see doc/EndToEndTesting.md for the full
    // E2EEnvironment/E2ETestBase setup this enables
    public abstract class OrderE2ERepositoryTest extends PostgresBaseRepositoryTest {
        @Override
        protected String getScriptPath() {
            return "db/schema-test.sql";
        }

        @Override
        protected boolean rollbackTransactionAfterEachTest() {
            return false;
        }
    }
    ```

- **New file `doc/EndToEndTesting.md`**, referenced from `USERGUIDE.md`'s new `## Testing`
  section (`[full E2E setup guide](doc/EndToEndTesting.md)`, same convention as the existing
  `doc/ARCHITECTURE.md` link). Content: a complete, anonymized, end-to-end example of standing up
  a full e2e suite on top of `BaseRepositoryTest`'s rollback opt-out — adapted from a real
  consumer project's setup (renamed to a generic `order`/`shop` domain, no project-specific
  names):

  - **`E2EEnvironment`** — a JVM-wide singleton (`ensureStarted()`, one instance shared by every
    scenario test in the run, torn down via a shutdown hook, not per test class) that:
    - Starts the DB via a `PostgresBaseRepositoryTest` subclass with
      `rollbackTransactionAfterEachTest()` returning `false`, reusing its container-cache and
      schema-init logic instead of hand-rolling a `PostgreSQLContainer` — this is the piece the
      opt-out unlocks; before it, projects had to duplicate `BaseRepositoryTest`'s container setup
      because the default rollback made reuse impossible.
    - Starts the application under test as a plain OS process (packaged jar / built frontend
      `node` process — or a `GenericContainer` if the project has Docker packaging), pointed at
      the container's host-mapped JDBC URL.
    - Exposes `dataSource()` (for seeding test data) and `baseUrl()` (for Playwright/HTTP calls
      against the running app).
  - **`E2ETestBase`** — the JUnit base class scenario test classes extend: calls
    `E2EEnvironment.ensureStarted()` once in a `@BeforeAll`, exposes the shared `dataSource()`/
    `baseUrl()` to subclasses, and documents that per-scenario data isolation must come from a
    delete+reseed test-data builder (see the `backend-java-test-data-builder` skill) called at
    the start of each test — not from transaction rollback, since none happens anymore.
  - A short worked scenario test extending `E2ETestBase` illustrating data seeding + an HTTP/UI
    assertion, to show the two pieces wired together end to end.

## Verification

- `./gradlew :nativsql-core:test` (after confirming with the user per the ask-before-tests
  convention) — confirms both new scenarios in Step 4 pass and no existing `*RepositoryTest`
  regressions across `nativsql-postgres`/`nativsql-mysql`/`nativsql-mariadb`/`nativsql-oracle`.
