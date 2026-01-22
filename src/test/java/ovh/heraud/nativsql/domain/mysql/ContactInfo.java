package ovh.heraud.nativsql.domain.mysql;

import java.time.LocalDateTime;

import ovh.heraud.nativsql.domain.Entity;
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
public class ContactInfo implements Entity<Long> {
    private Long id;
    private Long userId;
    private ContactType contactType;
    private String contactValue;
    private Boolean isPrimary;
    private LocalDateTime createdAt;
}
