package io.github.pascalheraud.nativsql.domain.mysql;

import java.time.LocalDateTime;

import io.github.pascalheraud.nativsql.domain.Entity;
import lombok.Data;

/**
 * Contact information entity.
 */
@Data
public class ContactInfo implements Entity<Long> {
    private Long id;
    private Long userId;
    private ContactType contactType;
    private String contactValue;
    private Boolean isPrimary;
    private LocalDateTime createdAt;
}
