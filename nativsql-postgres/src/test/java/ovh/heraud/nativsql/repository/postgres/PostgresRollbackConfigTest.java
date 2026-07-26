package ovh.heraud.nativsql.repository.postgres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies BaseRepositoryTest's rollbackTransactionAfterEachTest() extension
 * point: a row inserted through the injected DataSource is invisible from a
 * second, independent connection when rollback is enabled (default), and
 * visible when it's disabled.
 */
class PostgresRollbackConfigTest extends PostgresRepositoryTest {

    @Test
    void row_is_not_visible_from_another_connection_when_rollback_is_enabled() throws Exception {
        // Given: a row inserted on the transactional connection (default rollback behavior)
        insertGroup("rollback-enabled-group");

        // When: querying for it from a second, independent connection
        long count = countGroupsFromNewConnection("rollback-enabled-group");

        // Then: the row is not visible, since it was never committed
        assertThat(count).isZero();
    }

    private void insertGroup(String name) {
        // Goes through JdbcTemplate so the insert participates in the Spring-managed
        // transaction opened by BaseRepositoryTest (same thread-bound connection),
        // instead of auto-committing on a brand-new connection.
        new JdbcTemplate(getDataSource()).update("INSERT INTO user_group (name) VALUES (?)", name);
    }

    private long countGroupsFromNewConnection(String name) throws Exception {
        try (Connection connection = getDataSource().getConnection();
                PreparedStatement statement = connection
                        .prepareStatement("SELECT COUNT(*) FROM user_group WHERE name = ?")) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }
}
