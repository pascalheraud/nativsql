package ovh.heraud.nativsql.domain.postgres;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Base user statistics report with common fields.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseUserReport {
    private Long usersWithEmailContact;
    private Long usersWithFrenchPreference;
    private GroupStats groupStats;
}
