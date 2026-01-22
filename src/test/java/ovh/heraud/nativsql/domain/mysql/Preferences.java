package ovh.heraud.nativsql.domain.mysql;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User preferences, stored as JSON in MySQL.
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