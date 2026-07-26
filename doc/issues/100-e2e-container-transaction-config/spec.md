# Spec: configurable transaction rollback in `BaseRepositoryTest`

> Issue: [nativsql#100](https://github.com/heraud/nativsql/issues/100)

## Goal

`BaseRepositoryTest` (and its per-dialect subclasses `PostgresBaseRepositoryTest`,
`MySQLBaseRepositoryTest`, `MariaDBBaseRepositoryTest`, `OracleBaseRepositoryTest`) already spin
up a cached Testcontainers database and, today, unconditionally wrap every test in a transaction
that is rolled back in `@AfterEach`. That rollback-per-test behavior is correct for the library's
own integration tests (`*RepositoryTest` classes under `src/test`), which run entirely against a
single JDBC connection borrowed by the test itself.

It breaks down for **e2e tests**: an e2e scenario drives a real application process (or
container) that opens its **own** JDBC connection(s) to the same database container. Data
inserted by the test through the wrapping transaction is invisible to the app-under-test's
connections until commit — so today, nothing seeded via `BaseRepositoryTest` in an e2e context
can ever be observed by the app.

This feature makes the rollback-per-test behavior **configurable per test class**, so a subclass
used for e2e testing can opt out and have its seeded data actually committed, while every
existing internal integration test keeps rolling back by default with no change required.

```java
// Existing internal integration test — unchanged, still rolls back after each test
public abstract class PostgresRepositoryTest extends PostgresBaseRepositoryTest {
}

// New: an e2e test base class opts out of rollback so the app-under-test can see committed data
public abstract class PostgresE2ERepositoryTest extends PostgresBaseRepositoryTest {
    @Override
    protected boolean rollbackTransactionAfterEachTest() {
        return false;
    }
}
```

## Design

### Extension point: one overridable method, default preserves current behavior

`BaseRepositoryTest` gains a single new overridable method:

```java
/**
 * Whether the transaction opened in initializeDatabase() should be rolled back
 * in cleanup(). Defaults to true (current behavior) — override to return false
 * when the test needs its data to actually be committed and visible to other
 * connections (e.g. an application process under test in an e2e scenario).
 */
protected boolean rollbackTransactionAfterEachTest() {
    return true;
}
```

No annotation, no Spring property, no constructor parameter — a plain Java method override,
consistent with the rest of `BaseRepositoryTest`'s extension points (`getScriptPath()`,
`getDatabaseVersion()`, `getDatabaseVendor()`, `createContainer(String)` are all overridden the
same way by subclasses).

### Behavior change in `initializeDatabase()` / `cleanup()`

- When `rollbackTransactionAfterEachTest()` is `true` (default): behavior is **unchanged** — a
  transaction is opened in `@BeforeEach` and rolled back in `@AfterEach`, exactly as today.
- When it is `false`: `initializeDatabase()` does **not** open a wrapping transaction at all (not
  "open and commit" — just skip it), so every statement the test issues through the injected
  `DataSource` commits immediately at the point it's executed, the same way it would for any
  other connection to that database. `cleanup()` becomes a no-op for that test (nothing to roll
  back).

This is a binary switch, not a third "commit at the end" mode: e2e scenarios seed data
incrementally throughout a test and expect the app-under-test to observe it as soon as it's
inserted, not only after the test method returns.

### No change to container lifecycle or caching

The existing `CONTAINER_CACHE` (keyed by `vendor:version:schemaHash`) and container reuse logic
are unaffected. Whether the same cached container ends up used by both rollback-mode tests and
non-rollback-mode tests in the same run is unchanged from today — this spec only touches the
per-test transaction, not container creation/reuse. Cleaning up data committed by a non-rollback
test between e2e test methods is the e2e project's own responsibility (e.g. a
`TestDataBuilder`-style delete+reseed, per the [[java-test-e2e]] convention) — outside the scope
of this feature.

### Key principles

- **Default-safe**: every existing subclass and its tests keep exactly their current behavior
  with zero code changes — the new method only needs to be overridden by test classes that want
  the new, opt-in behavior.
- **No new dependency, no new module**: this lives entirely inside the existing
  `nativsql-core` `testFixtures` source set (`BaseRepositoryTest.java`); no new Testcontainers
  dependency is added anywhere, and no new Gradle module is created.
- **Per-dialect subclasses need no change**: `PostgresBaseRepositoryTest` /
  `MySQLBaseRepositoryTest` / `MariaDBBaseRepositoryTest` / `OracleBaseRepositoryTest` don't
  override anything new — they only implement `createContainer`/`getScriptPath`/etc. already, and
  the new method is overridden further down the hierarchy, by whichever concrete test class wants
  non-rollback behavior.
- **Documented as a first-class capability, not a footnote**: the rollback opt-out is what makes
  `BaseRepositoryTest` usable as the DB layer of a full e2e suite (container + schema, shared with
  an externally-running app process) instead of only as an internal repository-test fixture. This
  is significant enough to warrant its own dedicated end-to-end-testing guide — not just a
  paragraph under the existing `## Logging`-adjacent sections of `USERGUIDE.md` — covering the
  full e2e setup this unlocks (a JVM-wide environment singleton starting the container/app once,
  a JUnit base class scenario tests extend, and per-scenario data isolation via a
  delete+reseed test-data builder instead of transaction rollback). See plan.md for where this
  guide lives and what it covers.
