package ovh.heraud.nativsql.domain.oracle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ovh.heraud.nativsql.annotation.Json;

/**
 * User preferences, stored as JSON in Oracle.
 */
@Json
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Preferences {
    private String language;
    private String theme;
    private boolean notifications;
}
