package ovh.heraud.nativsql.domain.postgres;

import java.time.LocalDateTime;

import ovh.heraud.nativsql.domain.IEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contact information entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactInfo implements IEntity<Long> {
    private Long id;
    private Long userId;
    private ContactType contactType;
    private String contactValue;
    private Boolean isPrimary;
    private LocalDateTime createdAt;
}
