package ovh.heraud.nativsql.domain.postgres;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * User statistics report with hierarchical data.
 * Extends BaseUserReport to test RowMapper support for class inheritance.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserReport extends BaseUserReport {
    private long totalUsers;
}
