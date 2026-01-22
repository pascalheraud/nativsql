package ovh.heraud.nativsql.domain.mariadb;

import java.time.LocalDateTime;

import ovh.heraud.nativsql.domain.Entity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Group entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Group implements Entity<Long> {
    private Long id;
    private String name;
    private LocalDateTime createdAt;
}
