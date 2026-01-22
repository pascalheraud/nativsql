package ovh.heraud.nativsql.domain.mariadb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User preferences, stored as JSON in MariaDB.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Preferences {
    private String language;
    private String theme;
    private boolean notifications;
}