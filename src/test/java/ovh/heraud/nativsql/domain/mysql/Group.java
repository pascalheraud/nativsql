package ovh.heraud.nativsql.domain.mysql;

import java.time.LocalDateTime;

import ovh.heraud.nativsql.domain.IEntity;
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
public class Group implements IEntity<Long> {
    private Long id;
    private String name;
    private LocalDateTime createdAt;
}
