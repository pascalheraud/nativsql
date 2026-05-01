package ovh.heraud.nativsql.domain.mariadb;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User statistics report with hierarchical data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserReport {
    private Long totalUsers;
    private Long usersWithEmailContact;
    private Long usersWithFrenchPreference;
    private GroupStats groupStats;
}
