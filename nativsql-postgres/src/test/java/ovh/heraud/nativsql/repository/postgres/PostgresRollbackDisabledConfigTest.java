package ovh.heraud.nativsql.repository.postgres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that overriding rollbackTransactionAfterEachTest() to return false
 * makes inserted data actually committed and visible from a second,
 * independent connection — the behavior an e2e test needs.
 */
class PostgresRollbackDisabledConfigTest extends PostgresRepositoryTest {

    @Override
    protected boolean rollbackTransactionAfterEachTest() {
        return false;
    }

    @Test
    void row_is_visible_from_another_connection_when_rollback_is_disabled() throws Exception {
        // Given: a row inserted with rollback disabled
        new JdbcTemplate(getDataSource()).update("INSERT INTO user_group (name) VALUES (?)",
                "rollback-disabled-group");

        // When: querying for it from a second, independent connection
        long count = countGroupsFromNewConnection("rollback-disabled-group");

        // Then: the row is visible, since it was actually committed
        assertThat(count).isEqualTo(1);
    }

    @AfterEach
    void deleteInsertedRow() {
        // No transaction rolls this back automatically anymore — clean up manually
        // so the row doesn't leak into the shared, cached container for other tests.
        new JdbcTemplate(getDataSource()).update("DELETE FROM user_group WHERE name = ?",
                "rollback-disabled-group");
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
